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
        if (Main.dict.javaVersion < JavaVersion.JLS_80) {
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
                terms[1], bodyIsBlock);
        Term typeTerm = new ClassOrIfaceType(qualifiedName(iface.name()));
        lifted = new InstanceCreation(typeTerm, Empty.newTerm(), classBody);
        lifted.processPass0(c);
        lifted.processPass1(c);
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
