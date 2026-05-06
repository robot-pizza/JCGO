/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/SwitchExpressionLifter.java --
 * a part of JCGO translator.
 **
 * Project: JCGO (http://www.ivmaisoft.com/jcgo/)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 */

/*
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

package com.ivmaisoft.jcgo;

/**
 * Lifts a `T name = switch(...) {...}` declaration into:
 *   { T name; switch(...) { ...rewritten cases... } }
 * Each arrow-case body becomes:
 *   case L: name = body; break;            // BODY_EXPR
 *   case L: throw expr;                    // BODY_THROW
 *   case L: { ...; name = yieldExpr; break; }   // BODY_BLOCK
 */
final class SwitchExpressionLifter {

    private SwitchExpressionLifter() {
    }

    /**
     * Returns null if the input isn't a single-declarator local with
     * a SwitchExpression initializer; otherwise returns the lifted
     * Block. Caller should substitute that Block in place of the
     * original ExprStatement(LocalVariableDecl).
     */
    static Term tryLift(Term localVarDecl) {
        if (!(localVarDecl instanceof LocalVariableDecl)) {
            return null;
        }
        LocalVariableDecl lvd = (LocalVariableDecl) localVarDecl;
        if (!(lvd.terms[3] instanceof VariableDeclarator)) {
            return null;
        }
        VariableDeclarator vd = (VariableDeclarator) lvd.terms[3];
        if (!(vd.terms[2] instanceof SwitchExpression)) {
            return null;
        }
        SwitchExpression se = (SwitchExpression) vd.terms[2];
        String varName = vd.terms[0].dottedName();
        if (varName == null) {
            return null;
        }

        // Strip the initializer from the declaration.
        Term plainDeclr = new VariableDeclarator(vd.terms[0], vd.terms[1],
                Empty.newTerm());
        Term plainDecl;
        if (lvd.terms[0].notEmpty()) {
            plainDecl = new LocalVariableDecl(lvd.terms[0], lvd.terms[1],
                    lvd.terms[2], plainDeclr);
        } else {
            plainDecl = new LocalVariableDecl(lvd.terms[1], plainDeclr);
        }
        Term declStmt = new ExprStatement(plainDecl);

        // Build the case chain for the switch statement.
        Term casesChain = se.getCases();
        ObjVector arrowCases = new ObjVector();
        flattenCases(casesChain, arrowCases);
        ObjVector emittedCases = new ObjVector();
        for (int i = 0; i < arrowCases.size(); i++) {
            SwitchExprArrowCase arrow = (SwitchExprArrowCase) arrowCases
                    .elementAt(i);
            buildCaseStatements(arrow, varName, emittedCases);
        }
        Term caseSeq = seqOf(emittedCases);
        Term switchStmt = new SwitchStatement(se.getDiscriminant(), caseSeq);

        // Return a Seq (not a Block) so the synthesized declaration
        // shares the enclosing block's scope — the user expects the
        // variable to be visible after the switch.
        return new Seq(declStmt, switchStmt);
    }

    private static void flattenCases(Term t, ObjVector out) {
        if (!t.notEmpty()) {
            return;
        }
        if (t instanceof Seq) {
            Seq s = (Seq) t;
            flattenCases(s.terms[0], out);
            flattenCases(s.terms[1], out);
        } else if (t instanceof SwitchExprArrowCase) {
            out.addElement(t);
        }
    }

    private static void buildCaseStatements(SwitchExprArrowCase arrow,
            String varName, ObjVector out) {
        Term body = arrow.getBody();
        int kind = arrow.getBodyKind();
        Term effectiveBody;
        if (kind == SwitchExprArrowCase.BODY_THROW) {
            effectiveBody = body;
        } else if (kind == SwitchExprArrowCase.BODY_EXPR) {
            effectiveBody = new Seq(
                    new ExprStatement(buildAssign(varName, body)),
                    new BreakStatement(Empty.newTerm()));
        } else {
            // BODY_BLOCK: rewrite Yield → assign+break.
            effectiveBody = rewriteYields(body, varName);
        }
        ObjVector labels = new ObjVector();
        flattenLabels(arrow.getLabels(), labels);
        if (labels.size() == 0) {
            // default
            out.addElement(new CaseStatement(effectiveBody));
            return;
        }
        int n = labels.size();
        for (int i = 0; i < n - 1; i++) {
            out.addElement(new CaseStatement(
                    new Expression((Term) labels.elementAt(i)),
                    Empty.newTerm()));
        }
        out.addElement(new CaseStatement(
                new Expression((Term) labels.elementAt(n - 1)),
                effectiveBody));
    }

    private static void flattenLabels(Term t, ObjVector out) {
        if (!t.notEmpty()) {
            return;
        }
        if (t instanceof Seq) {
            Seq s = (Seq) t;
            flattenLabels(s.terms[0], out);
            flattenLabels(s.terms[1], out);
        } else {
            out.addElement(t);
        }
    }

    private static Term buildAssign(String varName, Term rhs) {
        Term lhs = new Expression(new QualifiedName(
                new LexTerm(LexTerm.ID, varName), Empty.newTerm()));
        return new Assignment(lhs,
                new LexTerm(LexTerm.EQUALS, "="), rhs);
    }

    /**
     * Walk a Block (or Seq under it) and replace each YieldStatement
     * with `target = expr; break;`. Other statements pass through.
     */
    private static Term rewriteYields(Term node, String varName) {
        if (!node.notEmpty()) {
            return node;
        }
        if (node instanceof YieldStatement) {
            Term expr = ((YieldStatement) node).getExpression();
            return new Seq(
                    new ExprStatement(buildAssign(varName, expr)),
                    new BreakStatement(Empty.newTerm()));
        }
        if (node instanceof Block) {
            Block b = (Block) node;
            // Block.terms = [LeftBrace, body, RightBrace] for the 1-arg
            // ctor, or [body] for the 0-arg fallback. The 1-arg form is
            // what JavaBlock produces.
            if (b.terms.length >= 2) {
                Term newBody = rewriteYields(b.terms[1], varName);
                return new Block(newBody);
            }
            return node;
        }
        if (node instanceof Seq) {
            Seq s = (Seq) node;
            Term left = rewriteYields(s.terms[0], varName);
            Term right = rewriteYields(s.terms[1], varName);
            return new Seq(left, right);
        }
        return node;
    }

    private static Term seqOf(ObjVector items) {
        if (items.size() == 0) {
            return Empty.newTerm();
        }
        Term result = (Term) items.elementAt(items.size() - 1);
        for (int i = items.size() - 2; i >= 0; i--) {
            result = new Seq((Term) items.elementAt(i), result);
        }
        return result;
    }
}
