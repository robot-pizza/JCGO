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
    private static int captureCounter;

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

        boolean unbound = !isCtor && detectUnboundInstance(sam, c);
        // Once-eval semantics for real-expression receivers (JLS 15.13.3).
        // For `(getThing())::handle` and similar, the receiver expression
        // must evaluate exactly once at lambda-creation time. The current
        // call-site path embeds the receiver Term directly into the SAM
        // body, which re-runs it on every invocation. Capture it into a
        // field of the synthesized class so the field's initializer (run
        // once at construction) holds the result.
        Term captureField = null;
        String captureName = null;
        if (!isCtor && !unbound && !receiverIsQualifiedName(terms[0])) {
            Object[] cap = buildReceiverCapture(c);
            if (cap != null) {
                captureField = (Term) cap[0];
                captureName = (String) cap[1];
            }
        }
        Term body = buildBody(paramNames, sam, unbound, captureName);
        Term classBody = LambdaSynthesis.buildClassBody(sam, lambdaParams,
                body, false);
        if (captureField != null) {
            classBody = new Seq(captureField, classBody);
        }

        Term typeTerm = new ClassOrIfaceType(qualifiedName(iface.name()));
        lifted = new InstanceCreation(typeTerm, Empty.newTerm(), classBody);
        lifted.processPass0(c);
        lifted.processPass1(c);
    }

    /**
     * For receiver shapes whose static type is syntactically derivable
     * without running pass1 (currently: explicit casts, possibly nested
     * in parens), capture the receiver into a final field on the
     * synthesized anonymous class. The field's initializer evaluates
     * once at construction — JLS 15.13.3 once-eval semantics — and the
     * SAM body reads from the field instead of re-evaluating the
     * expression.
     *
     * Pre-running pass1 to discover the type for a non-cast receiver
     * (e.g. `(getThing())::handle`) would bind any free names against
     * the outer scope, blocking JCGO's anon-class-capture mechanism
     * when those names are subsequently re-encountered as part of the
     * field initializer in the inner-class context. So for receivers
     * whose type is not syntactically apparent, this returns null and
     * the caller falls back to inline embedding (re-eval per call).
     */
    private Object[] buildReceiverCapture(Context c) {
        Term castType = extractCastType(terms[0]);
        if (castType == null) return null;
        String name = "$mref$rcv$" + (captureCounter++);
        Term varDeclr = new VariableDeclarator(
                new VariableIdentifier(new LexTerm(LexTerm.ID, name)),
                Empty.newTerm(), terms[0]);
        Term field = new FieldDeclaration(castType, Empty.newTerm(),
                varDeclr);
        Term modifiers = new Seq(new AccModifier(AccModifier.PRIVATE),
                new AccModifier(AccModifier.FINAL));
        Term decl = new TypeDeclaration(modifiers, field);
        return new Object[] { decl, name };
    }

    /**
     * Walk through paren / Expression wrappers; return the cast type
     * Term if the receiver's outermost shape is a CastExpression.
     * Otherwise null. The returned Term is fresh enough to be reused
     * as a field-declaration type.
     */
    private static Term extractCastType(Term receiver) {
        Term inner = receiver;
        while (inner instanceof Expression || inner instanceof ParenExpression) {
            inner = ((LexNode) inner).terms[0];
        }
        if (!(inner instanceof CastExpression)) return null;
        Term castTypeExpr = ((LexNode) inner).terms[0];
        // CastExpression's terms[0] is an Expression wrapping the type
        // (typically a QualifiedName like `Comparable` or `java.util.List`,
        // or a PrimitiveType, or a TypeWithDims). For field-decl use we
        // need a ClassOrIfaceType / PrimitiveType / TypeWithDims at the
        // outer layer. Primitive-typed receivers don't reach this point
        // (method refs need an object receiver), so wrap qualified-name
        // shapes as ClassOrIfaceType.
        Term ct = castTypeExpr;
        while (ct instanceof Expression || ct instanceof ParenExpression) {
            ct = ((LexNode) ct).terms[0];
        }
        if (ct instanceof QualifiedName) {
            return new ClassOrIfaceType(ct);
        }
        if (ct instanceof ClassOrIfaceType
                || ct instanceof PrimitiveType
                || ct instanceof TypeWithDims) {
            return ct;
        }
        return null;
    }

    private Term buildBody(ObjVector paramNames, MethodDefinition sam,
            boolean unbound, String captureName) {
        int n = paramNames.size();
        if (unbound) {
            // `Class::instanceMethod` — first SAM param becomes the
            // receiver, the rest pass straight through. SAM has at
            // least one param (caller validated).
            Term receiverExpr = new Expression(new QualifiedName(
                    new LexTerm(LexTerm.ID, "$mr$0"), Empty.newTerm()));
            Term args = Empty.newTerm();
            for (int i = n - 1; i >= 1; i--) {
                Term argRef = new Argument(new Expression(new QualifiedName(
                        new LexTerm(LexTerm.ID, "$mr$" + i),
                        Empty.newTerm())));
                args = args.notEmpty()
                        ? new ParameterList(argRef, args) : argRef;
            }
            return new MethodInvocation(receiverExpr,
                    new LexTerm(LexTerm.ID, methodName), args);
        }
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
        // `Receiver::method`. Two shapes:
        //
        // (a) Receiver is a qualified-name path (e.g. Integer,
        //     System.out, someVar). Build a combined path
        //     `<receiver>.<method>` and pass it as terms[0] of a 2-arg
        //     MethodInvocation so JCGO resolves it like a regular
        //     `Foo.bar(...)` call.
        //
        // (b) Receiver is an arbitrary expression (e.g.
        //     `((Comparable) x)::compareTo`, `(getThing())::handle`).
        //     Build a 3-arg MethodInvocation(receiver, methodName,
        //     args) -- the receiver expression itself is the call's
        //     receiver. JCGO's inner-class-captures-outer-locals
        //     handles capturing any locals referenced inside the
        //     expression. Note: the expression re-evaluates on each
        //     SAM invocation rather than once at lambda-creation
        //     (Java spec says once); for typical receivers (cast,
        //     field access) this is observably equivalent. Side-
        //     effecting receivers like `(getStream())::onNext` would
        //     differ.
        if (receiverIsQualifiedName(terms[0])) {
            Term receiverPath = unwrapToQualifiedName(terms[0]);
            Term combined = appendQualifiedSegment(receiverPath, methodName);
            return new MethodInvocation(combined, Empty.newTerm(), args);
        }
        if (captureName != null) {
            // Receiver was captured into the synthesized class as a
            // final field — the SAM body reads from the field instead
            // of re-evaluating the receiver expression.
            Term fieldRef = new Expression(new QualifiedName(
                    new LexTerm(LexTerm.ID, captureName), Empty.newTerm()));
            return new MethodInvocation(fieldRef,
                    new LexTerm(LexTerm.ID, methodName), args);
        }
        return new MethodInvocation(terms[0],
                new LexTerm(LexTerm.ID, methodName), args);
    }

    private static boolean receiverIsQualifiedName(Term receiver) {
        Term inner = receiver instanceof Expression
                ? ((Expression) receiver).terms[0] : receiver;
        return inner instanceof QualifiedName;
    }

    /**
     * Slice 24f: an unbound-instance reference (`String::length` for
     * `Function<String, Integer>`) is detected by:
     *   - receiver dotted name resolves to a class
     *   - that class declares an INSTANCE method named `methodName`
     *     whose arity equals SAM arity minus one (the missing param
     *     is the receiver, supplied by SAM's first arg)
     * Returns false if any of those checks fail — caller falls back
     * to the bound/static `Receiver.method(args)` body shape.
     */
    private boolean detectUnboundInstance(MethodDefinition sam, Context c) {
        int samArity = sam.methodSignature().paramCount();
        if (samArity < 1) return false;
        Term qn = unwrapToQualifiedName(terms[0]);
        String dotted = qn.dottedName();
        if (dotted == null) return false;
        // Restrict to single-identifier receivers — `System.out::println`
        // and friends are almost always bound-instance refs through a
        // field access, and JCGO's resolveClass actively tries to LOAD
        // a `System.out` class file for dotted names, which crashes the
        // build. Single-id receivers like `Box`, `String`, `Integer`
        // are the cases that actually want unbound-instance handling.
        if (dotted.indexOf('.') >= 0) return false;
        // If the receiver name is a local variable (introduced by the
        // user OR by MethodRefHoister's `var $mref$rcv$h$N = ...`
        // lift), skip the resolveClass attempt — that path
        // System.exits when it can't load a source file for the
        // name. Local-var-typed receivers are bound instance refs;
        // detectUnbound doesn't apply.
        if (c.currentMethod != null
                && c.currentMethod.getLocalVar(dotted) != null) {
            return false;
        }
        ClassDefinition rcvCls;
        try {
            rcvCls = c.resolveClass(dotted, true, false);
        } catch (RuntimeException e) {
            return false;
        }
        if (rcvCls == null) return false;
        rcvCls.define(c.forClass);
        java.util.Enumeration en = rcvCls.enumerateMethodSignatures();
        while (en.hasMoreElements()) {
            String sig = (String) en.nextElement();
            MethodDefinition md = rcvCls.getMethodNoInheritance(sig);
            if (md == null) continue;
            if (md.isClassMethod()) continue;
            if (!methodName.equals(md.id())) continue;
            if (md.methodSignature().paramCount() == samArity - 1) {
                return true;
            }
        }
        return false;
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
