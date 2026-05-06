/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/LambdaSynthesis.java --
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
 * Slice 23. Builds the anonymous-class body Term for a LambdaExpression
 * once the SAM (single-abstract method) of the target functional
 * interface has been resolved.
 *
 * The synthesized method:
 *   - has the same name and return type as the SAM
 *   - takes the SAM's parameter types but the user's parameter names
 *     (untyped lambda params just contribute names — types come from
 *     the SAM)
 *   - body is `return userBody;` for an expression-bodied lambda
 *     (or just `userBody;` if the SAM is void), or the user's block
 *     wrapped as-is when the lambda used a block body
 */
final class LambdaSynthesis {

    private LambdaSynthesis() {
    }

    /**
     * Slice 24i: rewrites bare `this` in the lambda body to refer to
     * the enclosing class instance (`OuterClass.this`). JLS 15.27.2
     * says lambdas don't introduce a new `this` — they inherit the
     * enclosing one. Without this rewrite, JCGO's anonymous-class
     * lift makes bare `this` resolve to the synthesized lambda class
     * itself, which doesn't have the user's fields.
     *
     * Mutates the body tree in place. Skips anonymous inner classes
     * (any InstanceCreation with a classBody) and nested
     * ClassDeclarations — they introduce their own `this` scope.
     */
    static void rewriteBareThis(Term node, ClassDefinition outerClass) {
        if (!node.notEmpty() || !(node instanceof LexNode)) return;
        if (node instanceof InstanceCreation
                || node instanceof ClassDeclaration
                || node instanceof IfaceDeclaration) {
            return;
        }
        LexNode ln = (LexNode) node;
        for (int i = 0; i < ln.terms.length; i++) {
            Term child = ln.terms[i];
            if (child instanceof This) {
                This t = (This) child;
                if (!t.terms[0].notEmpty()) {
                    ln.terms[i] = new This(
                            new ClassOrIfaceType(outerClass));
                    continue;
                }
            }
            rewriteBareThis(child, outerClass);
        }
    }

    /**
     * Returns a class-body Term suitable for use as the third argument
     * of a 3-arg `new InstanceCreation(type, args, classBody)` call.
     */
    static Term buildClassBody(MethodDefinition sam, Term lambdaParams,
            Term lambdaBody, boolean bodyIsBlock) {
        ObjVector userParamNames = new ObjVector();
        flattenParamNames(lambdaParams, userParamNames);

        MethodSignature msig = sam.methodSignature();
        int n = msig.paramCount();
        if (userParamNames.size() != n) {
            // Caller already validated, but guard defensively.
            return Empty.newTerm();
        }

        Term formalParams = Empty.newTerm();
        for (int i = n - 1; i >= 0; i--) {
            ExpressionType pt = msig.paramAt(i);
            String name = (String) userParamNames.elementAt(i);
            Term fp = buildFormalParam(pt, name);
            formalParams = formalParams.notEmpty()
                    ? new FormalParamList(fp, formalParams) : fp;
        }

        Term methodBody = buildMethodBody(sam, lambdaBody, bodyIsBlock);
        Term retType = exprTypeToTypeTerm(sam.exprType());
        if (retType == null) {
            // Return type can't be reified — punt.
            return Empty.newTerm();
        }

        Term method = new MethodDeclaration(retType, Empty.newTerm(),
                new LexTerm(LexTerm.ID, sam.id()),
                formalParams, Empty.newTerm(), Empty.newTerm(), methodBody);
        Term modifiers = new AccModifier(AccModifier.PUBLIC);
        Term member = new TypeDeclaration(modifiers, method);
        return new Seq(member, Empty.newTerm());
    }

    private static void flattenParamNames(Term t, ObjVector out) {
        if (!t.notEmpty()) return;
        if (t instanceof Seq) {
            Seq s = (Seq) t;
            flattenParamNames(s.terms[0], out);
            flattenParamNames(s.terms[1], out);
        } else {
            String name = t.dottedName();
            if (name != null) out.addElement(name);
        }
    }

    private static Term buildFormalParam(ExpressionType type, String name) {
        Term typeTerm = exprTypeToTypeTerm(type);
        if (typeTerm == null) {
            // Fallback: use Object so the build doesn't crash; the
            // user's lambda body will fail type-check if it really
            // matters.
            typeTerm = new ClassOrIfaceType(qualifiedName(
                    Names.JAVA_LANG_OBJECT));
        }
        return new FormalParameter(Empty.newTerm(), typeTerm,
                Empty.newTerm(),
                new VariableIdentifier(new LexTerm(LexTerm.ID, name)),
                Empty.newTerm());
    }

    private static Term buildMethodBody(MethodDefinition sam, Term userBody,
            boolean bodyIsBlock) {
        if (bodyIsBlock) {
            // User-supplied block — pass through. The block already
            // contains explicit return/yield as needed.
            return userBody;
        }
        // Expression body: wrap as `return userBody;` (or just the
        // expression statement if SAM returns void).
        if (sam.exprType().objectSize() == Type.VOID) {
            return new ExprStatement(userBody);
        }
        return new ReturnStatement(userBody);
    }

    private static Term exprTypeToTypeTerm(ExpressionType et) {
        int dims = et.signatureDimensions();
        ClassDefinition cd = et.signatureClass();
        if (cd == null) return null;
        int sz = cd.objectSize();
        Term base;
        if (sz == Type.VOID) {
            base = new PrimitiveType(Type.VOID);
        } else if (sz < Type.CLASSINTERFACE && sz != Type.NULLREF) {
            base = new PrimitiveType(sz);
        } else if (sz == Type.CLASSINTERFACE) {
            base = new ClassOrIfaceType(qualifiedName(cd.name()));
        } else {
            return null;
        }
        if (dims > 0) {
            Term ds = Empty.newTerm();
            for (int i = 0; i < dims; i++) {
                ds = new DimSpec(ds);
            }
            base = new TypeWithDims(base, ds);
        }
        return base;
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
