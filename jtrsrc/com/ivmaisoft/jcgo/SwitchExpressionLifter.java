/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/SwitchExpressionLifter.java --
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
    private static int nextMatchedId = 0;

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

        Term casesChain = se.getCases();
        ObjVector arrowCases = new ObjVector();
        flattenCases(casesChain, arrowCases);

        if (anyPatternCase(arrowCases)) {
            return liftPatternSwitch(declStmt, se.getDiscriminant(),
                    arrowCases, varName);
        }

        ObjVector emittedCases = new ObjVector();
        for (int i = 0; i < arrowCases.size(); i++) {
            SwitchExprArrowCase arrow = (SwitchExprArrowCase) arrowCases
                    .elementAt(i);
            buildCaseStatements(arrow, varName, emittedCases);
        }
        Term caseSeq = seqOf(emittedCases);
        SwitchStatement switchStmt = new SwitchStatement(se.getDiscriminant(),
                caseSeq);
        // P6: mark so the enum-switch path can enforce exhaustiveness.
        switchStmt.markFromSwitchExpression();

        // Return a Seq (not a Block) so the synthesized declaration
        // shares the enclosing block's scope — the user expects the
        // variable to be visible after the switch.
        return new Seq(declStmt, switchStmt);
    }

    private static boolean anyPatternCase(ObjVector cases) {
        for (int i = 0; i < cases.size(); i++) {
            if (((SwitchExprArrowCase) cases.elementAt(i)).isPattern()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pattern-switch desugar (slice 15). Uses a synthetic boolean
     * $matched flag to enforce first-match-wins across cases that may
     * overlap (e.g. two `case Square sq` cases differing only in their
     * `when` guards).
     *
     *   T target;
     *   boolean $matched_N = false;
     *   if (!$matched_N) { if (discr instanceof T1 v1) {
     *      [if (guard1)] { target = body1; $matched_N = true; } } }
     *   if (!$matched_N) { if (discr instanceof T2 v2) {
     *      [if (guard2)] { target = body2; $matched_N = true; } } }
     *   if (!$matched_N) { target = defaultBody; }
     */
    private static Term liftPatternSwitch(Term declStmt, Term discriminant,
            ObjVector arrowCases, String varName) {
        String matchedName = "$jcgoMatched$" + (nextMatchedId++);
        Term matchedDecl = new ExprStatement(new LocalVariableDecl(
                new PrimitiveType(Type.BOOLEAN),
                new VariableDeclarator(
                        new VariableIdentifier(new LexTerm(LexTerm.ID,
                                matchedName)),
                        Empty.newTerm(),
                        new LexTerm(LexTerm.FALSE, "false"))));
        Term out = new Seq(declStmt, matchedDecl);
        for (int i = 0; i < arrowCases.size(); i++) {
            SwitchExprArrowCase arrow = (SwitchExprArrowCase) arrowCases
                    .elementAt(i);
            Term guarded = buildPatternCaseStmt(arrow, varName, matchedName,
                    discriminant);
            out = new Seq(out, guarded);
        }
        return out;
    }

    private static Term buildPatternCaseStmt(SwitchExprArrowCase arrow,
            String varName, String matchedName, Term discriminant) {
        Term notMatched = new UnaryExpression(
                new LexTerm(LexTerm.NOT, "!"),
                new Expression(new QualifiedName(
                        new LexTerm(LexTerm.ID, matchedName),
                        Empty.newTerm())));
        Term coreBody = buildPatternCoreBody(arrow, varName, matchedName,
                discriminant);
        return new IfThenElse(new Expression(notMatched), coreBody,
                Empty.newTerm());
    }

    /**
     * For a pattern case, produces the inner body that runs when
     * !$matched. For a default case (no pattern, empty labels), the
     * body unconditionally assigns to target and sets $matched.
     */
    private static Term buildPatternCoreBody(SwitchExprArrowCase arrow,
            String varName, String matchedName, Term discriminant) {
        Term setMatched = new ExprStatement(new Assignment(
                new Expression(new QualifiedName(
                        new LexTerm(LexTerm.ID, matchedName),
                        Empty.newTerm())),
                new LexTerm(LexTerm.EQUALS, "="),
                new LexTerm(LexTerm.TRUE, "true")));

        if (!arrow.isPattern()) {
            // default case
            return assignBodySeq(arrow, varName, setMatched);
        }

        Term assigningBody = assignBodySeq(arrow, varName, setMatched);

        if (arrow.getGuard() != null) {
            assigningBody = new IfThenElse(
                    new Expression(arrow.getGuard()),
                    assigningBody, Empty.newTerm());
        }

        InstanceOf iof;
        if (arrow.getRecordPattern() != null) {
            // Slice 16: record-pattern case — let IfThenElse.prependPatternBinding
            // do the destructuring work for us by attaching the record pattern.
            RecordPattern rp = arrow.getRecordPattern();
            iof = new InstanceOf(discriminant, rp.getType(),
                    Empty.newTerm());
            iof.setRecordPattern(rp);
        } else {
            iof = new InstanceOf(discriminant,
                    arrow.getPatternType(), Empty.newTerm());
            iof.setBindingName(arrow.getPatternBinding());
        }
        return new IfThenElse(new Expression(iof), assigningBody,
                Empty.newTerm());
    }

    /**
     * Translates the case body and appends the matched-flag assignment.
     */
    private static Term assignBodySeq(SwitchExprArrowCase arrow,
            String varName, Term setMatched) {
        int kind = arrow.getBodyKind();
        Term body = arrow.getBody();
        if (kind == SwitchExprArrowCase.BODY_THROW) {
            // Throw exits — no assignment, no flag set.
            return body;
        }
        if (kind == SwitchExprArrowCase.BODY_EXPR) {
            return new Seq(
                    new ExprStatement(buildAssign(varName, body)),
                    setMatched);
        }
        // BODY_BLOCK: rewrite Yield → assign + setMatched.
        return rewriteYieldsForPattern(body, varName, setMatched);
    }

    private static Term rewriteYieldsForPattern(Term node, String varName,
            Term setMatched) {
        if (!node.notEmpty()) {
            return node;
        }
        if (node instanceof YieldStatement) {
            Term expr = ((YieldStatement) node).getExpression();
            return new Seq(
                    new ExprStatement(buildAssign(varName, expr)),
                    setMatched);
        }
        if (node instanceof Block) {
            Block b = (Block) node;
            if (b.terms.length >= 2) {
                Term newBody = rewriteYieldsForPattern(b.terms[1], varName,
                        setMatched);
                return new Block(newBody);
            }
            return node;
        }
        if (node instanceof Seq) {
            Seq s = (Seq) node;
            return new Seq(
                    rewriteYieldsForPattern(s.terms[0], varName, setMatched),
                    rewriteYieldsForPattern(s.terms[1], varName, setMatched));
        }
        return node;
    }

    /**
     * Slice 32: pass1-time helper for pattern-switch in non-init
     * contexts (e.g. `return switch (...) { case Type id -> ...; }`).
     * Builds the synthesized $matched-flag chain with assignments to
     * the caller-supplied varName. Caller wraps the result with the
     * appropriate prelude (decl statement) and trailing use of
     * varName (e.g. return $tmp).
     */
    static Term buildPatternSwitchStmts(SwitchExpression se, String varName,
            Term declStmt) {
        ObjVector arrowCases = new ObjVector();
        flattenCases(se.getCases(), arrowCases);
        return liftPatternSwitch(declStmt, se.getDiscriminant(),
                arrowCases, varName);
    }

    /**
     * Slice 22: pass1-time helper for non-init-context switch-expression
     * lifts (e.g. inside `return switch (...) {...}`). The caller has
     * already declared a temp `varName` of the right type; we just emit
     * the rewritten switch statement that assigns to it.
     *
     * Pattern-switch arms aren't handled here — see
     * buildPatternSwitchStmts for the pattern path.
     */
    static Term buildSwitchStmt(SwitchExpression se, String varName) {
        ObjVector arrowCases = new ObjVector();
        flattenCases(se.getCases(), arrowCases);
        ObjVector emittedCases = new ObjVector();
        for (int i = 0; i < arrowCases.size(); i++) {
            SwitchExprArrowCase arrow = (SwitchExprArrowCase) arrowCases
                    .elementAt(i);
            buildCaseStatements(arrow, varName, emittedCases);
        }
        Term caseSeq = seqOf(emittedCases);
        SwitchStatement ss = new SwitchStatement(se.getDiscriminant(),
                caseSeq);
        ss.markFromSwitchExpression();
        return ss;
    }

    static boolean anyPatternCases(SwitchExpression se) {
        ObjVector arrowCases = new ObjVector();
        flattenCases(se.getCases(), arrowCases);
        return anyPatternCase(arrowCases);
    }

    /**
     * Slice 31: lift `throw switch (...) {...};`. Each arm becomes a
     * throw statement directly — no temp variable, no method-return-
     * type plumbing. Yield arms (block bodies) get their yields
     * rewritten into throws of the yielded expression.
     */
    static Term liftThrowSwitch(SwitchExpression se) {
        ObjVector arrowCases = new ObjVector();
        flattenCases(se.getCases(), arrowCases);
        ObjVector emittedCases = new ObjVector();
        for (int i = 0; i < arrowCases.size(); i++) {
            SwitchExprArrowCase arrow = (SwitchExprArrowCase) arrowCases
                    .elementAt(i);
            buildThrowingCase(arrow, emittedCases);
        }
        Term caseSeq = seqOf(emittedCases);
        SwitchStatement ss = new SwitchStatement(se.getDiscriminant(),
                caseSeq);
        ss.markFromSwitchExpression();
        return ss;
    }

    /**
     * Slice 37: pattern-switch in throw position. Builds the same
     * $matched-flag chain as liftPatternSwitch but each arm THROWS
     * its body expression instead of assigning into a target temp.
     * The flag is technically unused (throws exit) but it's needed
     * for the case where a pattern matches but its `when` guard
     * fails — we want to fall through to the next arm rather than
     * throw nothing, and the $matched-style chain handles that
     * naturally (the inner if-guard short-circuits, control returns
     * to the outer if(!$matched), and the next arm gets a chance).
     */
    static Term liftPatternThrowSwitch(SwitchExpression se) {
        ObjVector arrowCases = new ObjVector();
        flattenCases(se.getCases(), arrowCases);
        String matchedName = "$jcgoMatched$" + (nextMatchedId++);
        Term matchedDecl = new ExprStatement(new LocalVariableDecl(
                new PrimitiveType(Type.BOOLEAN),
                new VariableDeclarator(
                        new VariableIdentifier(new LexTerm(LexTerm.ID,
                                matchedName)),
                        Empty.newTerm(),
                        new LexTerm(LexTerm.FALSE, "false"))));
        Term out = matchedDecl;
        for (int i = 0; i < arrowCases.size(); i++) {
            SwitchExprArrowCase arrow = (SwitchExprArrowCase) arrowCases
                    .elementAt(i);
            Term guarded = buildPatternThrowCaseStmt(arrow, matchedName,
                    se.getDiscriminant());
            out = new Seq(out, guarded);
        }
        return out;
    }

    private static Term buildPatternThrowCaseStmt(SwitchExprArrowCase arrow,
            String matchedName, Term discriminant) {
        Term notMatched = new UnaryExpression(
                new LexTerm(LexTerm.NOT, "!"),
                new Expression(new QualifiedName(
                        new LexTerm(LexTerm.ID, matchedName),
                        Empty.newTerm())));
        Term coreBody = buildPatternThrowCoreBody(arrow, discriminant);
        return new IfThenElse(new Expression(notMatched), coreBody,
                Empty.newTerm());
    }

    private static Term buildPatternThrowCoreBody(SwitchExprArrowCase arrow,
            Term discriminant) {
        Term throwingBody = throwArmBody(arrow);
        if (!arrow.isPattern()) {
            return throwingBody;
        }
        if (arrow.getGuard() != null) {
            throwingBody = new IfThenElse(
                    new Expression(arrow.getGuard()),
                    throwingBody, Empty.newTerm());
        }
        InstanceOf iof;
        if (arrow.getRecordPattern() != null) {
            RecordPattern rp = arrow.getRecordPattern();
            iof = new InstanceOf(discriminant, rp.getType(),
                    Empty.newTerm());
            iof.setRecordPattern(rp);
        } else {
            iof = new InstanceOf(discriminant,
                    arrow.getPatternType(), Empty.newTerm());
            iof.setBindingName(arrow.getPatternBinding());
        }
        return new IfThenElse(new Expression(iof), throwingBody,
                Empty.newTerm());
    }

    private static Term throwArmBody(SwitchExprArrowCase arrow) {
        Term body = arrow.getBody();
        int kind = arrow.getBodyKind();
        if (kind == SwitchExprArrowCase.BODY_THROW) return body;
        if (kind == SwitchExprArrowCase.BODY_EXPR) {
            return new ThrowStatement(body);
        }
        return rewriteYieldsToThrow(body);
    }

    private static void buildThrowingCase(SwitchExprArrowCase arrow,
            ObjVector out) {
        Term body = arrow.getBody();
        int kind = arrow.getBodyKind();
        Term effectiveBody;
        if (kind == SwitchExprArrowCase.BODY_THROW) {
            effectiveBody = body;
        } else if (kind == SwitchExprArrowCase.BODY_EXPR) {
            effectiveBody = new ThrowStatement(body);
        } else {
            // BODY_BLOCK: rewrite each Yield to a Throw of the yielded
            // expression.
            effectiveBody = rewriteYieldsToThrow(body);
        }
        ObjVector labels = new ObjVector();
        flattenLabels(arrow.getLabels(), labels);
        if (labels.size() == 0) {
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

    private static Term rewriteYieldsToThrow(Term node) {
        if (!node.notEmpty()) return node;
        if (node instanceof YieldStatement) {
            return new ThrowStatement(
                    ((YieldStatement) node).getExpression());
        }
        if (node instanceof Block) {
            Block b = (Block) node;
            if (b.terms.length >= 2) {
                Term newBody = rewriteYieldsToThrow(b.terms[1]);
                return new Block(newBody);
            }
            return node;
        }
        if (node instanceof Seq) {
            Seq s = (Seq) node;
            return new Seq(rewriteYieldsToThrow(s.terms[0]),
                    rewriteYieldsToThrow(s.terms[1]));
        }
        return node;
    }

    /**
     * Slice 22b: parse-time lift for `lhs = switch (...) {...};` where
     * lhs is a plain identifier already in scope. The LHS gives us
     * everything we need (no temp); each arm becomes a `lhs = val;
     * break;` and the whole assignment is replaced by the resulting
     * SwitchStatement. Returns null when the input isn't a simple
     * identifier-LHS Assignment of a SwitchExpression, or when the
     * switch contains pattern cases (those need the $matched flag
     * setup that the assignment-form lifter doesn't reach).
     */
    static Term tryLiftAssign(Term node) {
        if (!(node instanceof Assignment)) return null;
        Assignment a = (Assignment) node;
        if (a.terms[1].getSym() != LexTerm.EQUALS) return null;
        if (!(a.terms[2] instanceof SwitchExpression)) return null;
        SwitchExpression se = (SwitchExpression) a.terms[2];
        String lhsName = a.terms[0].dottedName();
        if (lhsName == null || lhsName.indexOf('.') >= 0) return null;
        // Slice 42: pattern arms route through the $matched-flag chain
        // assigning to lhsName. The lhs is already declared so no
        // decl-stmt is needed — pass Empty as the first chain element.
        if (anyPatternCases(se)) {
            return buildPatternSwitchStmts(se, lhsName, Empty.newTerm());
        }
        return buildSwitchStmt(se, lhsName);
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
