/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/SwitchStatement.java --
 * a part of JCGO translator.
 **
 * Project: JCGO (http://www.ivmaisoft.com/jcgo/)
 * Copyright (C) 2001-2012 Ivan Maidanski <ivmai@mail.ru>
 * All rights reserved.
 */

/*
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 *
 * Modifications are licensed under the same terms as JCGO above:
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

/*
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 **
 * This software is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License (GPL) for more details.
 **
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 **
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module. An independent module is a module which is not derived from
 * or based on this library. If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 */

package com.ivmaisoft.jcgo;

/**
 * Grammar production for 'switch'.
 **
 * Format: SWITCH LPAREN Expression RPAREN LBRACE
 * SwitchBlockStatementGroups/Empty RBRACE
 *
 * String discriminants (Java 7, JLS 14.11) are supported via a parse-time
 * desugar at processPass1: the case chain is rewritten into a Block that
 * holds a temp local for the discriminant plus an IfThenElse chain
 * comparing the temp against each case label via String.equals(). Breaks
 * inside case bodies still target this SwitchStatement's break label
 * because BreakableStmt's processPassOneBegin/End wraps the desugared
 * subtree just like the original case chain.
 */

final class SwitchStatement extends BreakableStmt {

    private static int nextStringSwitchId;

    private static int nextEnumSwitchId;

    private boolean isStringSwitch;

    private boolean isEnumSwitch;

    // P6: set by SwitchExpressionLifter when this SwitchStatement was
    // synthesized from a switch *expression* (vs a switch statement).
    // Enables javac-style exhaustiveness checking on enum
    // discriminators — non-exhaustive switch-expression must be
    // rejected because the result variable would otherwise be
    // unassigned for an uncovered enum value.
    private boolean isFromSwitchExpression;

    void markFromSwitchExpression() {
        isFromSwitchExpression = true;
    }

    SwitchStatement(Term c, Term f) {
        super(c, new LeftBrace(), f, new RightBrace());
    }

    void processPass1(Context c) {
        TryStatement oldLastBreakableTry = c.lastBreakableTry;
        c.lastBreakableTry = c.currentTry;
        terms[0].processPass1(c);
        ExpressionType discrType = terms[0].exprType();
        ClassDefinition discrClass = discrType.signatureClass();
        boolean isStringDiscr = discrClass != null
                && Names.JAVA_LANG_STRING.equals(discrClass.name())
                && discrType.signatureDimensions() == 0;
        if (isStringDiscr) {
            if (!c.versionAtLeast(JavaVersion.JLS_70)) {
                fatalError(c,
                        "string switch requires -source 7 or higher (got "
                                + JavaVersion.format(Main.dict.javaVersion)
                                + ")");
            }
            terms[2] = buildStringSwitchDesugar(terms[0], terms[2]);
            isStringSwitch = true;
        } else if (isEnumClass(discrClass)
                && discrType.signatureDimensions() == 0) {
            if (!c.versionAtLeast(JavaVersion.JLS_50)) {
                fatalError(c,
                        "enum switch requires -source 5 or higher (got "
                                + JavaVersion.format(Main.dict.javaVersion)
                                + ")");
            }
            if (isFromSwitchExpression) {
                checkEnumSwitchExpressionExhaustive(c, discrClass, terms[2]);
            }
            terms[2] = buildEnumSwitchDesugar(terms[0], discrClass, terms[2]);
            isEnumSwitch = true;
        } else {
            int s0 = discrType.objectSize();
            if (s0 < Type.BYTE || s0 > Type.INT) {
                fatalError(c, "Illegal type of switch expression");
            }
        }
        terms[1].processPass1(c);
        boolean oldBreakableHidden = c.breakableHidden;
        c.breakableHidden = false;
        processPassOneBegin(c);
        terms[2].processPass1(c);
        processPassOneEnd(c);
        c.breakableHidden = oldBreakableHidden;
        terms[3].processPass1(c);
        c.lastBreakableTry = oldLastBreakableTry;
    }

    void writeStackObjs(OutputContext oc, Term scopeTerm) {
        terms[0].writeStackObjs(oc, scopeTerm);
        terms[2].writeStackObjs(oc, scopeTerm);
    }

    void processOutput(OutputContext oc) {
        if (isStringSwitch || isEnumSwitch) {
            // Wrap the desugared block in `do { ... } while (0);` so the
            // unlabeled `break` statements that BreakStatement.processOutput
            // emits inside case bodies have an enclosing C construct to
            // exit. labeled-break paths bypass this by going to the
            // outputBreakLabel emitted below.
            oc.cPrint("do ");
            terms[2].processOutput(oc);
            oc.cPrint("while (0);");
            outputBreakLabel(oc);
            return;
        }
        oc.cPrint("switch (");
        terms[0].processOutput(oc);
        oc.cPrint(")");
        terms[1].processOutput(oc);
        if (terms[2].notEmpty()) {
            terms[2].writeStackObjs(oc, terms[1]);
            terms[2].processOutput(oc);
            oc.cPrint(";");
        }
        terms[3].processOutput(oc);
        outputBreakLabel(oc);
    }

    // === String-switch desugar helpers ===========================

    private static Term buildStringSwitchDesugar(Term discriminant,
            Term caseChain) {
        ObjVector cases = new ObjVector();
        flattenCases(caseChain, cases);

        ObjVector groups = new ObjVector();
        ObjVector pendingLabels = new ObjVector();
        Term defaultBody = null;

        for (int i = 0; i < cases.size(); i++) {
            CaseStatement cs = (CaseStatement) cases.elementAt(i);
            Term label = cs.terms[0];
            Term body = cs.terms[1];
            if (!label.notEmpty()) {
                if (body.notEmpty()) {
                    defaultBody = body;
                }
                continue;
            }
            pendingLabels.addElement(label);
            if (body.notEmpty()) {
                Object[] g = new Object[2];
                g[0] = pendingLabels;
                g[1] = body;
                groups.addElement(g);
                pendingLabels = new ObjVector();
            }
        }

        String tmpName = "$jcgoSwitchStr$" + (nextStringSwitchId++);

        Term result = defaultBody != null ? defaultBody : Empty.newTerm();
        for (int i = groups.size() - 1; i >= 0; i--) {
            Object[] g = (Object[]) groups.elementAt(i);
            ObjVector labels = (ObjVector) g[0];
            Term body = (Term) g[1];
            Term cond = buildEqualsCall(tmpName,
                    (Term) labels.elementAt(0));
            for (int j = 1; j < labels.size(); j++) {
                cond = new CondOrAndOperation(cond,
                        new LexTerm(LexTerm.OR, "||"),
                        buildEqualsCall(tmpName,
                                (Term) labels.elementAt(j)));
            }
            result = new IfThenElse(cond, body, result);
        }

        Term strType = new ClassOrIfaceType(stringQualifiedName());
        Term tmpDecl = new ExprStatement(new LocalVariableDecl(strType,
                new VariableDeclarator(
                        new VariableIdentifier(new LexTerm(LexTerm.ID,
                                tmpName)),
                        Empty.newTerm(), discriminant)));

        return new Block(new Seq(tmpDecl, result));
    }

    private static void flattenCases(Term t, ObjVector out) {
        if (!t.notEmpty()) {
            return;
        }
        if (t instanceof Seq) {
            Seq s = (Seq) t;
            flattenCases(s.terms[0], out);
            flattenCases(s.terms[1], out);
        } else if (t instanceof CaseStatement) {
            out.addElement(t);
        }
    }

    private static Term stringQualifiedName() {
        return new QualifiedName(new LexTerm(LexTerm.ID, "java"),
                new QualifiedName(new LexTerm(LexTerm.ID, "lang"),
                        new QualifiedName(new LexTerm(LexTerm.ID, "String"),
                                Empty.newTerm())));
    }

    private static Term buildEqualsCall(String tmpName, Term labelExpr) {
        Term receiver = new Expression(new QualifiedName(new LexTerm(
                LexTerm.ID, tmpName), Empty.newTerm()));
        return new MethodInvocation(receiver,
                new LexTerm(LexTerm.ID, "equals"),
                new Argument(labelExpr));
    }

    // === Enum-switch desugar helpers (quirk #7) ==================

    // True when `cd` is a real enum class (declared via `enum Foo
    // {...}` or by extending java.lang.Enum directly). Detected via
    // the superclass name to avoid touching the private isEnum()
    // accessor.
    private static boolean isEnumClass(ClassDefinition cd) {
        if (cd == null) return false;
        ClassDefinition sc = cd.superClass();
        return sc != null && Names.JAVA_LANG_ENUM.equals(sc.name());
    }

    // P6 (enum switch-expression exhaustiveness): every enum constant
    // must be matched by a `case CONST` label OR there must be a
    // `default`. Matches javac's "switch expression does not cover
    // all possible input values" check.
    private void checkEnumSwitchExpressionExhaustive(Context c,
            ClassDefinition enumCls, Term caseChain) {
        ObjVector cases = new ObjVector();
        flattenCases(caseChain, cases);
        boolean hasDefault = false;
        ObjHashSet covered = new ObjHashSet();
        for (int i = 0; i < cases.size(); i++) {
            CaseStatement cs = (CaseStatement) cases.elementAt(i);
            Term label = cs.terms[0];
            if (!label.notEmpty()) {
                hasDefault = true;
                continue;
            }
            String name = label.dottedName();
            if (name == null) continue;
            int dot = name.lastIndexOf('.');
            covered.add(dot >= 0 ? name.substring(dot + 1) : name);
        }
        if (hasDefault) return;
        ObjVector constants = enumConstantNames(enumCls);
        if (constants == null) return;
        StringBuffer missing = null;
        for (int i = 0; i < constants.size(); i++) {
            String name = (String) constants.elementAt(i);
            if (!covered.contains(name)) {
                if (missing == null) missing = new StringBuffer();
                else missing.append(", ");
                missing.append(name);
            }
        }
        if (missing != null) {
            fatalError(c,
                    "switch expression does not cover all possible input "
                            + "values (missing: " + missing
                            + ") — add a `default` arm or label every enum "
                            + "constant");
        }
    }

    // Enumerate the enum constants of `enumCls` (its public static
    // final fields of the enum's own type). Returns null if the
    // class doesn't look like a properly-defined enum.
    private static ObjVector enumConstantNames(ClassDefinition enumCls) {
        if (enumCls == null) return null;
        ObjVector out = new ObjVector();
        java.util.Enumeration en = enumCls.enumerateFieldNames();
        if (en == null) return null;
        while (en.hasMoreElements()) {
            String fieldName = (String) en.nextElement();
            VariableDefinition v = enumCls.getField(fieldName, null);
            if (v == null) continue;
            if (!v.isClassVariable() || !v.isFinalVariable()
                    || v.isPrivate()) continue;
            ExpressionType fieldType = v.exprType();
            if (fieldType == null) continue;
            if (fieldType.signatureClass() != enumCls) continue;
            if (fieldType.signatureDimensions() != 0) continue;
            out.addElement(fieldName);
        }
        return out;
    }

    // Build `tmp { if (tmp == Enum.LABEL0) body0; else if ... else
    // default }`. Same group / pending-labels shape as the
    // string-switch desugar — multi-label arrow cases and label
    // fall-through (`case A: case B: body;`) both work through it.
    private static Term buildEnumSwitchDesugar(Term discriminant,
            ClassDefinition enumCls, Term caseChain) {
        ObjVector cases = new ObjVector();
        flattenCases(caseChain, cases);

        ObjVector groups = new ObjVector();
        ObjVector pendingLabels = new ObjVector();
        Term defaultBody = null;

        for (int i = 0; i < cases.size(); i++) {
            CaseStatement cs = (CaseStatement) cases.elementAt(i);
            Term label = cs.terms[0];
            Term body = cs.terms[1];
            if (!label.notEmpty()) {
                if (body.notEmpty()) {
                    defaultBody = body;
                }
                continue;
            }
            pendingLabels.addElement(label);
            if (body.notEmpty()) {
                Object[] g = new Object[2];
                g[0] = pendingLabels;
                g[1] = body;
                groups.addElement(g);
                pendingLabels = new ObjVector();
            }
        }

        String tmpName = "$jcgoSwitchEnum$" + (nextEnumSwitchId++);

        Term result = defaultBody != null ? defaultBody : Empty.newTerm();
        for (int i = groups.size() - 1; i >= 0; i--) {
            Object[] g = (Object[]) groups.elementAt(i);
            ObjVector labels = (ObjVector) g[0];
            Term body = (Term) g[1];
            Term cond = buildEnumEq(tmpName, enumCls,
                    (Term) labels.elementAt(0));
            for (int j = 1; j < labels.size(); j++) {
                cond = new CondOrAndOperation(cond,
                        new LexTerm(LexTerm.OR, "||"),
                        buildEnumEq(tmpName, enumCls,
                                (Term) labels.elementAt(j)));
            }
            result = new IfThenElse(cond, body, result);
        }

        Term enumType = new ClassOrIfaceType(dottedQualifiedName(
                enumCls.name()));
        Term tmpDecl = new ExprStatement(new LocalVariableDecl(enumType,
                new VariableDeclarator(
                        new VariableIdentifier(new LexTerm(LexTerm.ID,
                                tmpName)),
                        Empty.newTerm(), discriminant)));

        return new Block(new Seq(tmpDecl, result));
    }

    // `tmp == EnumType.LABEL` — extract the simple-name label from
    // whatever wrapper Expression / QualifiedName the parser built
    // and re-qualify it under the discriminator's enum class.
    private static Term buildEnumEq(String tmpName,
            ClassDefinition enumCls, Term labelExpr) {
        String simple = labelExpr.dottedName();
        // For an already-qualified label (`case EnumType.LABEL`) keep
        // only the last segment — re-qualification happens below.
        if (simple != null) {
            int dot = simple.lastIndexOf('.');
            if (dot >= 0) simple = simple.substring(dot + 1);
        }
        if (simple == null) simple = "?";
        Term qualified = enumConstantRef(enumCls, simple);
        Term lhs = new Expression(new QualifiedName(new LexTerm(
                LexTerm.ID, tmpName), Empty.newTerm()));
        return new RelationalOp(lhs, new LexTerm(LexTerm.EQ, "=="),
                qualified);
    }

    // Build `EnumType.LABEL` as a single dotted QualifiedName chain
    // (e.g. `QuirksEnum.Direction.LEFT` for an inner enum). The
    // resolver walks segment-by-segment: outer class → inner class →
    // static enum field.
    private static Term enumConstantRef(ClassDefinition enumCls,
            String label) {
        return new Expression(
                dottedQualifiedName(enumCls.name() + "." + label));
    }

    private static Term dottedQualifiedName(String runtimeName) {
        // JCGO's runtime class names use `$` for inner-class
        // boundaries; source-level references want `.`.
        String dotted = runtimeName.replace('$', '.');
        Term qn = null;
        int idx = dotted.length();
        while (idx > 0) {
            int prev = dotted.lastIndexOf('.', idx - 1);
            String part = dotted.substring(prev + 1, idx);
            Term lt = new LexTerm(LexTerm.ID, part);
            qn = qn == null ? new QualifiedName(lt, Empty.newTerm())
                    : new QualifiedName(lt, qn);
            idx = prev;
        }
        return qn;
    }
}
