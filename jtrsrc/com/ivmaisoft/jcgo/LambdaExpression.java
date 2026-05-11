/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/LambdaExpression.java --
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
 * Slice 23 (Java 8, JEP 126). A lambda expression — placeholder that
 * lifts to an anonymous-class instance creation at pass1 time, when
 * the target functional-interface type is available via
 * c.currentVarType (set by the enclosing variable declaration).
 *
 * MVP scope:
 *   - `id -> body` (single untyped parameter, no parens)
 *   - `() -> body`
 *   - `(id, id, ...) -> body` (multiple untyped params)
 *   - body is either an expression (becomes `return body;`) or a block
 *
 * Out of scope:
 *   - Typed parameters `(int x) -> ...`
 *   - Method references `String::length`
 *   - Target inference from method-arg / cast / ternary context
 *
 * terms[0] = ParamList (Seq of LexTerm IDs) or single LexTerm ID
 * terms[1] = body Term (expression or Block)
 */
final class LambdaExpression extends LexNode {

    private InstanceCreation lifted;
    private boolean bodyIsBlock;

    LambdaExpression(Term params, Term body, boolean bodyIsBlock) {
        super(params, body);
        this.bodyIsBlock = bodyIsBlock;
    }

    Term getParams() {
        return terms[0];
    }

    Term getBody() {
        return terms[1];
    }

    boolean isBodyBlock() {
        return bodyIsBlock;
    }

    void processPass1(Context c) {
        if (lifted != null) return;
        if (!c.versionAtLeast(JavaVersion.JLS_80)) {
            fatalError(c,
                    "lambda expression requires -source 8 or higher (got "
                    + JavaVersion.format(Main.dict.javaVersion) + ")");
            return;
        }
        ExpressionType target = c.currentVarType;
        if (target == null
                || target.objectSize() != Type.CLASSINTERFACE) {
            fatalError(c,
                    "lambda needs an explicit functional-interface target "
                    + "type (e.g. `Runnable r = () -> ...`); pure-context "
                    + "inference is not supported in this fork");
            return;
        }
        ClassDefinition iface = target.receiverClass();
        if (iface == null) {
            fatalError(c, "lambda target type has no class definition");
            return;
        }
        iface.define(c.forClass);
        MethodDefinition sam = findSam(iface, c.forClass);
        if (sam == null) {
            fatalError(c,
                    "lambda target " + iface.name() + " is not a functional "
                    + "interface (need exactly one abstract method)");
            return;
        }

        // Slice 24i: rewrite bare `this` in the body so it binds to
        // the enclosing class instance (JLS 15.27.2), not the
        // synthesized anonymous lambda class.
        if (c.currentClass != null) {
            LambdaSynthesis.rewriteBareThis(terms[1], c.currentClass);
        }
        Term classBody = LambdaSynthesis.buildClassBody(sam, terms[0],
                terms[1], bodyIsBlock, iface, c.currentVarTypeArgsJls, c);
        Term typeTerm = new ClassOrIfaceType(qualifiedName(iface.name()));
        lifted = new InstanceCreation(typeTerm, Empty.newTerm(), classBody);
        lifted.processPass0(c);
        lifted.processPass1(c);
        // Quirk #8: after lift, the original params/body Terms are
        // co-owned by the synthesized class body. Leaving them in
        // this.terms makes tree walks (discoverObjLeaks, allocRcvr,
        // isAnyLocalVarChanged, ...) descend into nodes that were
        // never pass1'd if the SAM is never actually invoked (e.g.
        // lambda stored in a field and never called). Replacing with
        // Empty.term routes every subsequent walk exclusively through
        // `lifted`.
        terms[0] = Empty.newTerm();
        terms[1] = Empty.newTerm();
    }

    ExpressionType exprType() {
        assertCond(lifted != null);
        return lifted.exprType();
    }

    ExpressionType actualExprType() {
        assertCond(lifted != null);
        return lifted.actualExprType();
    }

    void processOutput(OutputContext oc) {
        assertCond(lifted != null);
        lifted.processOutput(oc);
    }

    // Quirk #8: after lifting, the original terms[0] (params) and
    // terms[1] (body) terms are co-owned by the synthesized class
    // body — the LambdaExpression placeholder should NOT participate
    // in tree walks independently of `lifted`, or we'd visit nodes
    // that the synth class's own walk skipped (e.g. when the SAM is
    // never invoked anywhere so its body's nodes never got pass1).
    void discoverObjLeaks() {
        assertCond(lifted != null);
        lifted.discoverObjLeaks();
    }

    // Issue #150: also forward escape-analysis callbacks that flow
    // top-down. Without the setObjLeaks override, when an enclosing
    // Argument decides this arg escapes and calls setObjLeaks(null)
    // on the LambdaExpression placeholder, Term's default no-op
    // swallows it — `lifted` (the synthesized anon-class
    // InstanceCreation) never gets told. Stack-eligibility flag stays
    // set, the lambda gets JCGO_STACKOBJ_NEW'd, and storing it via
    // a setter (`textField.setOnChange(value -> ...)`) lands a stack
    // pointer in a field that outlives the creating function. When
    // fireChange later dereferences `onChange`, the stack memory has
    // been reused and we crash (PC=0 if zeroed, fault inside
    // fireChange if not).
    void setObjLeaks(VariableDefinition v) {
        assertCond(lifted != null);
        lifted.setObjLeaks(v);
    }

    void setStackObjVolatile() {
        assertCond(lifted != null);
        lifted.setStackObjVolatile();
    }

    void writeStackObjs(OutputContext oc, Term scopeTerm) {
        assertCond(lifted != null);
        lifted.writeStackObjs(oc, scopeTerm);
    }

    int tokenCount() {
        return lifted != null ? lifted.tokenCount() : 4;
    }

    boolean isAtomary() {
        return lifted != null && lifted.isAtomary();
    }

    static MethodDefinition findSam(ClassDefinition iface,
            ClassDefinition forClass) {
        if (!iface.isInterface()) return null;
        java.util.Enumeration en = iface.enumerateMethodSignatures();
        MethodDefinition sole = null;
        while (en.hasMoreElements()) {
            String sig = (String) en.nextElement();
            MethodDefinition md = iface.getMethodNoInheritance(sig);
            if (md == null || !md.isAbstract()) continue;
            if (sole != null && sole != md) return null;
            sole = md;
        }
        return sole;
    }

    private static Term qualifiedName(String runtimeName) {
        // `$` → `.` so inner-class iface names (`Outer$Inner`)
        // resolve at pass1. Issue #147.
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
