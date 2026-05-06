/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/SwitchArgHoister.java --
 * a part of JCGO translator.
 **
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 */

/*
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

package com.ivmaisoft.jcgo;

/**
 * Slice 36 (Java 14): hoist switch-expression arguments out of method
 * calls (and other expression positions) into preamble statements at
 * the surrounding statement level.
 *
 * Input:  a parsed expression-Term tree that may contain a
 *         SwitchExpression somewhere as a sub-term (typically inside
 *         an Argument of a MethodInvocation).
 * Output: a Result with
 *           - rewrittenRoot: the same tree, with each SwitchExpression
 *             replaced in place by a `$jcgoSwArg$N` reference;
 *           - preambles: a Seq of (LocalVariableDecl + SwitchStatement)
 *             pairs to emit before the surrounding statement.
 *
 * Type heuristic for the synthesized temp: the first non-throw arm's
 * body Term decides. Recognized: integer/float/boolean/string literals.
 * Anything else falls back to java.lang.Object — works fine for
 * reference returns; primitive returns may need an explicit cast at
 * the use site, which JCGO already does for Object→primitive
 * conversions via autobox.
 *
 * Walks LexNode.terms[] in place. Skips descent into nested scopes:
 * LambdaExpression bodies, InstanceCreation class bodies, nested class
 * declarations.
 */
final class SwitchArgHoister {

    static final class Result {
        final Term rewrittenRoot;
        final Term preambles;
        final boolean hoisted;

        Result(Term rewrittenRoot, Term preambles, boolean hoisted) {
            this.rewrittenRoot = rewrittenRoot;
            this.preambles = preambles;
            this.hoisted = hoisted;
        }
    }

    private static int nextTempId = 0;

    private SwitchArgHoister() {
    }

    /**
     * Returns a Result with rewritten root + preamble statements.
     * Empty/no-hoist case: preambles is Empty, hoisted is false.
     */
    static Result hoist(Term root) {
        if (root == null || !root.notEmpty()) {
            return new Result(root, Empty.newTerm(), false);
        }
        ObjVector preambles = new ObjVector();
        walk(root, preambles);
        if (preambles.size() == 0) {
            return new Result(root, Empty.newTerm(), false);
        }
        Term seq = Empty.newTerm();
        for (int i = preambles.size() - 1; i >= 0; i--) {
            Term stmt = (Term) preambles.elementAt(i);
            seq = seq.notEmpty() ? new Seq(stmt, seq) : stmt;
        }
        return new Result(root, seq, true);
    }

    private static void walk(Term node, ObjVector preambles) {
        if (!node.notEmpty() || !(node instanceof LexNode)) return;
        // Don't recurse into structures with their own scope/lift.
        if (node instanceof LambdaExpression
                || node instanceof MethodReference
                || node instanceof InstanceCreation
                || node instanceof ClassDeclaration
                || node instanceof IfaceDeclaration
                || node instanceof SwitchExpression) {
            return;
        }
        LexNode ln = (LexNode) node;
        Term[] children = ln.terms;
        for (int i = 0; i < children.length; i++) {
            Term child = children[i];
            // Direct SwitchExpression child — hoist.
            if (child instanceof SwitchExpression
                    && !SwitchExpressionLifter.anyPatternCases(
                            (SwitchExpression) child)) {
                children[i] = hoistOne((SwitchExpression) child, preambles);
                continue;
            }
            walk(child, preambles);
        }
    }

    private static Term hoistOne(SwitchExpression se, ObjVector preambles) {
        String tempName = "$jcgoSwArg$" + (nextTempId++);
        Term tempType = inferTempType(se);
        Term decl = new ExprStatement(new LocalVariableDecl(
                tempType,
                new VariableDeclarator(
                        new VariableIdentifier(
                                new LexTerm(LexTerm.ID, tempName)),
                        Empty.newTerm(), Empty.newTerm())));
        Term switchStmt = SwitchExpressionLifter.buildSwitchStmt(se,
                tempName);
        preambles.addElement(decl);
        preambles.addElement(switchStmt);
        return new Expression(new QualifiedName(
                new LexTerm(LexTerm.ID, tempName), Empty.newTerm()));
    }

    /**
     * Type heuristic for the synthesized temp. Walks the switch's
     * arrow cases looking for the first non-throw arm; the type of
     * that arm's body expression is used. Falls back to
     * java.lang.Object when the body is non-trivial.
     */
    private static Term inferTempType(SwitchExpression se) {
        Term firstArm = firstNonThrowArmExpr(se);
        Term inferred = inferTypeFromExpr(firstArm);
        if (inferred != null) return inferred;
        return new ClassOrIfaceType(qualifiedName(Names.JAVA_LANG_OBJECT));
    }

    private static Term firstNonThrowArmExpr(SwitchExpression se) {
        ObjVector cases = new ObjVector();
        flattenCases(se.getCases(), cases);
        for (int i = 0; i < cases.size(); i++) {
            SwitchExprArrowCase arm = (SwitchExprArrowCase) cases
                    .elementAt(i);
            int kind = arm.getBodyKind();
            if (kind == SwitchExprArrowCase.BODY_THROW) continue;
            if (kind == SwitchExprArrowCase.BODY_EXPR) {
                return arm.getBody();
            }
            // BODY_BLOCK: scan for first yield statement.
            Term yieldExpr = firstYieldExpr(arm.getBody());
            if (yieldExpr != null) return yieldExpr;
        }
        return null;
    }

    private static Term firstYieldExpr(Term node) {
        if (!node.notEmpty()) return null;
        if (node instanceof YieldStatement) {
            return ((YieldStatement) node).getExpression();
        }
        if (node instanceof Block || node instanceof Seq) {
            LexNode ln = (LexNode) node;
            for (int i = 0; i < ln.terms.length; i++) {
                Term r = firstYieldExpr(ln.terms[i]);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static void flattenCases(Term t, ObjVector out) {
        if (!t.notEmpty()) return;
        if (t instanceof Seq) {
            Seq s = (Seq) t;
            flattenCases(s.terms[0], out);
            flattenCases(s.terms[1], out);
        } else if (t instanceof SwitchExprArrowCase) {
            out.addElement(t);
        }
    }

    private static Term inferTypeFromExpr(Term expr) {
        if (expr == null || !expr.notEmpty()) return null;
        // Unwrap Expression / ParenExpression layers.
        if (expr instanceof Expression) {
            return inferTypeFromExpr(((Expression) expr).terms[0]);
        }
        if (expr instanceof ParenExpression) {
            return inferTypeFromExpr(((ParenExpression) expr).terms[0]);
        }
        if (expr instanceof IntLiteral) {
            String s = expr.dottedName();
            if (s != null && (s.endsWith("L") || s.endsWith("l"))) {
                return new PrimitiveType(Type.LONG);
            }
            return new PrimitiveType(Type.INT);
        }
        if (expr instanceof FloatLiteral) {
            String s = expr.dottedName();
            if (s != null && (s.endsWith("F") || s.endsWith("f"))) {
                return new PrimitiveType(Type.FLOAT);
            }
            return new PrimitiveType(Type.DOUBLE);
        }
        if (expr instanceof StringLiteral) {
            return new ClassOrIfaceType(qualifiedName(
                    Names.JAVA_LANG_STRING));
        }
        if (expr instanceof CharacterLiteral) {
            return new PrimitiveType(Type.CHAR);
        }
        if (expr instanceof LexTerm) {
            int sym = ((LexTerm) expr).getSym();
            if (sym == LexTerm.TRUE || sym == LexTerm.FALSE) {
                return new PrimitiveType(Type.BOOLEAN);
            }
        }
        return null;
    }

    private static Term qualifiedName(String dotted) {
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
