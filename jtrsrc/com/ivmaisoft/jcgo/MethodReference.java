/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/MethodReference.java --
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
 * Slice 23c (Java 8). A method reference of the form
 *   Receiver :: methodName
 *   Receiver :: new
 *
 * Lifted at pass1 to a synthetic anonymous class implementing the
 * target functional interface — same machinery as LambdaExpression,
 * just with a body shaped by the reference instead of by the user.
 *
 * MVP scope: simple identifier receiver only (e.g. `Integer::parseInt`,
 * `Foo::new`). Dotted receivers like `System.out::println` and
 * parenthesized-expression receivers are deferred — they need fuller
 * receiver-expression parsing.
 *
 * Resolution rules:
 *   - For `Receiver::method`, the lifter looks up `method` on the
 *     receiver's class. If the resolved method is static, the body
 *     is `Receiver.method(p1..pN)`. If it's an instance method with
 *     arity = SAM arity (bound), same shape — we trust JCGO's normal
 *     MethodInvocation resolution to bind correctly. Unbound-instance
 *     references like `String::length` (where p1 becomes the receiver)
 *     are NOT yet auto-detected — the user can write the equivalent
 *     `s -> s.length()` lambda.
 *   - For `Receiver::new`, the body is `new Receiver(p1..pN)`.
 *
 * terms[0] = receiver Term (a QualifiedName for the class/var name)
 */
final class MethodReference extends LexNode {

    private final String methodName;
    private final boolean isCtor;
    private InstanceCreation lifted;

    MethodReference(Term receiver, String methodName, boolean isCtor) {
        super(receiver);
        this.methodName = methodName;
        this.isCtor = isCtor;
    }

    void processPass1(Context c) {
        if (lifted != null) return;
        if (Main.dict.javaVersion < JavaVersion.JLS_80) {
            fatalError(c,
                    "method reference requires -source 8 or higher (got "
                    + JavaVersion.format(Main.dict.javaVersion) + ")");
            return;
        }
        ExpressionType target = c.currentVarType;
        if (target == null
                || target.objectSize() != Type.CLASSINTERFACE) {
            fatalError(c,
                    "method reference needs an explicit functional-interface "
                    + "target type (e.g. `Function f = Integer::parseInt`)");
            return;
        }
        ClassDefinition iface = target.receiverClass();
        if (iface == null) {
            fatalError(c, "method-reference target type has no class definition");
            return;
        }
        iface.define(c.forClass);
        MethodDefinition sam = LambdaExpression.findSam(iface, c.forClass);
        if (sam == null) {
            fatalError(c,
                    "method-reference target " + iface.name()
                    + " is not a functional interface");
            return;
        }

        int n = sam.methodSignature().paramCount();
        ObjVector paramNames = new ObjVector();
        Term lambdaParams = Empty.newTerm();
        for (int i = n - 1; i >= 0; i--) {
            String pname = "$mr$" + i;
            paramNames.addElement(pname);
            Term lt = new LexTerm(LexTerm.ID, pname);
            lambdaParams = lambdaParams.notEmpty()
                    ? new Seq(lt, lambdaParams) : lt;
        }

        Term body = buildBody(paramNames, sam);
        Term classBody = LambdaSynthesis.buildClassBody(sam, lambdaParams,
                body, false);

        Term typeTerm = new ClassOrIfaceType(qualifiedName(iface.name()));
        lifted = new InstanceCreation(typeTerm, Empty.newTerm(), classBody);
        lifted.processPass0(c);
        lifted.processPass1(c);
    }

    private Term buildBody(ObjVector paramNames, MethodDefinition sam) {
        Term args = Empty.newTerm();
        for (int i = paramNames.size() - 1; i >= 0; i--) {
            String pname = (String) paramNames.elementAt(i);
            Term argRef = new Argument(new Expression(new QualifiedName(
                    new LexTerm(LexTerm.ID, pname), Empty.newTerm())));
            args = args.notEmpty() ? new ParameterList(argRef, args) : argRef;
        }
        if (isCtor) {
            // `Receiver::new` → `new Receiver(p1..pN)`. The receiver
            // term names the type to instantiate; we wrap the bare
            // QualifiedName as a ClassOrIfaceType so InstanceCreation
            // resolves it as a class rather than an expression.
            Term typeTerm = unwrapToTypeTerm(terms[0]);
            return new InstanceCreation(typeTerm, args, Empty.newTerm());
        }
        // `Receiver::method`. Build a qualified-name path
        // `<receiver>.<method>` and pass it as terms[0] of a
        // MethodInvocation with terms[1]=Empty, so JCGO resolves the
        // path the same way it does a regular `Foo.bar(...)` call —
        // working for both static (Integer::parseInt) and instance-
        // bound (someVar::method) references.
        Term receiverPath = unwrapToQualifiedName(terms[0]);
        Term combined = appendQualifiedSegment(receiverPath, methodName);
        return new MethodInvocation(combined, Empty.newTerm(), args);
    }

    private static Term unwrapToTypeTerm(Term receiver) {
        Term qn = unwrapToQualifiedName(receiver);
        return new ClassOrIfaceType(qn);
    }

    private static Term unwrapToQualifiedName(Term receiver) {
        if (receiver instanceof Expression) {
            Term inner = ((Expression) receiver).terms[0];
            if (inner instanceof QualifiedName) return inner;
        }
        if (receiver instanceof QualifiedName) return receiver;
        return receiver;
    }

    private static Term appendQualifiedSegment(Term qn, String segment) {
        // QualifiedName chain stored head-first: outer.terms[0] is the
        // current segment LexTerm, outer.terms[1] is the rest. We want
        // to append at the END so the method name becomes the deepest
        // (rightmost) segment.
        if (qn instanceof QualifiedName) {
            QualifiedName q = (QualifiedName) qn;
            Term tail = q.terms[1];
            if (!tail.notEmpty()) {
                Term newTail = new QualifiedName(
                        new LexTerm(LexTerm.ID, segment), Empty.newTerm());
                return new QualifiedName(q.terms[0], newTail);
            }
            return new QualifiedName(q.terms[0],
                    appendQualifiedSegment(tail, segment));
        }
        // Fallback — shouldn't happen for the MVP receiver shapes.
        return new QualifiedName(new LexTerm(LexTerm.ID, segment),
                Empty.newTerm());
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
