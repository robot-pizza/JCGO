/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/MethodRefHoister.java --
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
 * Once-eval semantics for method-call-shaped method-ref receivers
 * (JLS 15.13.3). For `(getStream())::onNext` and similar, the receiver
 * expression must evaluate exactly once at lambda-creation time. The
 * cast-shape sibling fix in MethodReference.buildReceiverCapture
 * handles `((Type) expr)::method` by capturing the receiver into a
 * field of the lifted anon class — but only because the cast type is
 * syntactically derivable. For shapes whose type isn't determinable
 * without pass1 resolution, we lift the receiver to a `var local at
 * parse time, before the surrounding statement, so the receiver
 * becomes a single-name `QualifiedName` that the existing working
 * path handles trivially.
 *
 * Input:  a parsed expression-Term tree that may contain a
 *         MethodReference whose receiver is a real expression
 *         (e.g. method call, cast we couldn't extract, etc).
 * Output: a Result with
 *           - rewrittenRoot: the same tree, with each lifted
 *             method-ref's receiver replaced by a `$mref$rcv$h$N`
 *             reference;
 *           - preambles: a Seq of `var $mref$rcv$h$N = <receiver>;`
 *             local-var declarations to emit before the surrounding
 *             statement.
 *
 * `var` (Slice 24a, JLS 10) does the type-inference work at pass1 in
 * the OUTER scope where the local naturally lives — no inner-vs-outer
 * capture conflict, no manual receiver-type discovery.
 *
 * Walks LexNode.terms[] in place. Skips descent into nested scopes:
 * lambda bodies, anon-class instance creations, nested class
 * declarations. For MethodReference itself we visit the receiver but
 * don't recurse further (after lifting it's a single-name reference).
 */
final class MethodRefHoister {

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

    private MethodRefHoister() {
    }

    /**
     * Returns a Result with the rewritten root and any preamble
     * statements. Empty/no-hoist case: preambles is Empty, hoisted
     * is false.
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
                || node instanceof InstanceCreation
                || node instanceof ClassDeclaration
                || node instanceof IfaceDeclaration) {
            return;
        }
        if (node instanceof MethodReference) {
            hoistOne((MethodReference) node, preambles);
            return;
        }
        LexNode ln = (LexNode) node;
        Term[] children = ln.terms;
        for (int i = 0; i < children.length; i++) {
            walk(children[i], preambles);
        }
    }

    private static void hoistOne(MethodReference mref, ObjVector preambles) {
        Term receiver = mref.terms[0];
        if (!needsHoist(receiver)) return;
        String tempName = "$mref$rcv$h$" + (nextTempId++);
        Term decl = buildVarDecl(tempName, receiver);
        preambles.addElement(decl);
        // Replace the receiver in-place with a single-name path. The
        // wrapping Expression matches the shape MethodReference's
        // receiverIsQualifiedName check expects (an Expression around
        // a QualifiedName).
        mref.terms[0] = new Expression(new QualifiedName(
                new LexTerm(LexTerm.ID, tempName), Empty.newTerm()));
    }

    /**
     * Cast-shape receivers (`((Type) expr)::method`) get handled by
     * the field-on-lifted-class path inside MethodReference; leave
     * them alone here. QualifiedName receivers (`Integer::parseInt`,
     * `(System.out)::println`, single-var refs) hit the existing
     * working path. Anything else needs hoisting.
     */
    private static boolean needsHoist(Term receiver) {
        if (receiver == null || !receiver.notEmpty()) return false;
        Term inner = receiver;
        while (inner instanceof Expression
                || inner instanceof ParenExpression) {
            inner = ((LexNode) inner).terms[0];
        }
        if (inner instanceof QualifiedName) return false;
        if (inner instanceof CastExpression) return false;
        return true;
    }

    /**
     * Builds `var $mref$rcv$h$N = <receiver>;` as a statement Term.
     * The `var` keyword routes through LocalVariableDecl.processVarPass1
     * which infers the local's static type from the initializer's
     * exprType after pass1. The local lives in the outer scope, where
     * the receiver expression naturally evaluates once.
     */
    private static Term buildVarDecl(String name, Term initExpr) {
        Term varType = new ClassOrIfaceType(new QualifiedName(
                new LexTerm(LexTerm.ID, "var"), Empty.newTerm()));
        Term varDeclr = new VariableDeclarator(
                new VariableIdentifier(new LexTerm(LexTerm.ID, name)),
                Empty.newTerm(),
                initExpr);
        return new ExprStatement(
                new LocalVariableDecl(varType, varDeclr));
    }
}
