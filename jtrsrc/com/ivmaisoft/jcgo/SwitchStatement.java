/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/SwitchStatement.java --
 * a part of JCGO translator.
 **
 * Project: JCGO (http://www.ivmaisoft.com/jcgo/)
 * Copyright (C) 2001-2026 Ivan Maidanski <ivmai@mail.ru>
 * All rights reserved.
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

    private boolean isStringSwitch;

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
            if (Main.dict.javaVersion < JavaVersion.JLS_70) {
                fatalError(c,
                        "string switch requires -source 7 or higher (got "
                                + JavaVersion.format(Main.dict.javaVersion)
                                + ")");
            }
            terms[2] = buildStringSwitchDesugar(terms[0], terms[2]);
            isStringSwitch = true;
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
        if (isStringSwitch) {
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
}
