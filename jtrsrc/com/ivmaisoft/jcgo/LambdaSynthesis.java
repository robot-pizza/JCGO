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
     *
     * Quirk #6: when the target functional interface is parameterized
     * (`ChangeListener<String>`) and the SAM declares a parameter as a
     * type variable (`void onChange(T value)`), substitute T with the
     * captured arg so the synthesized formal parameter is `String value`,
     * not `Object value`. Otherwise the lambda body fails to type-check
     * any operation that needs the substituted type.
     */
    static Term buildClassBody(MethodDefinition sam, Term lambdaParams,
            Term lambdaBody, boolean bodyIsBlock,
            ClassDefinition iface, String varTypeArgsJls,
            Context c) {
        ObjVector userParamNames = new ObjVector();
        flattenParamNames(lambdaParams, userParamNames);

        MethodSignature msig = sam.methodSignature();
        int n = msig.paramCount();
        if (userParamNames.size() != n) {
            // Caller already validated, but guard defensively.
            return Empty.newTerm();
        }

        ExpressionType[] resolvedParams = resolveSamParamTypes(sam, msig,
                iface, varTypeArgsJls, c);

        Term formalParams = Empty.newTerm();
        for (int i = n - 1; i >= 0; i--) {
            ExpressionType pt = resolvedParams != null ? resolvedParams[i]
                    : msig.paramAt(i);
            String name = (String) userParamNames.elementAt(i);
            Term fp = buildFormalParam(pt, name);
            formalParams = formalParams.notEmpty()
                    ? new FormalParamList(fp, formalParams) : fp;
        }

        Term methodBody = buildMethodBody(sam, lambdaBody, bodyIsBlock);
        ExpressionType retEt = sam.exprType();
        if (resolvedParams != null) {
            ExpressionType subst = resolveTypeVarReturn(sam, iface,
                    varTypeArgsJls, c);
            if (subst != null) retEt = subst;
        }
        Term retType = exprTypeToTypeTerm(retEt);
        if (retType == null) {
            // Return type can't be reified — punt.
            return Empty.newTerm();
        }

        Term method = new MethodDeclaration(retType, Empty.newTerm(),
                new LexTerm(LexTerm.ID, sam.id()),
                formalParams, Empty.newTerm(), Empty.newTerm(), methodBody);
        Term modifiers = new AccModifier(AccModifier.PUBLIC);
        Term member = new TypeDeclaration(modifiers, method);
        Term body = new Seq(member, Empty.newTerm());
        // Quirk #6: when a SAM parameter was substituted from a
        // type-var, the synthesized method's signature differs from
        // the parent SAM's erased signature. BridgeSynthesis (slice
        // 51) emits an `(Object) -> downcast -> delegate` bridge so
        // the SAM dispatch still finds the typed method. No-op when
        // every formal stays at the erased type.
        if (resolvedParams != null) {
            body = BridgeSynthesis.wrap(body);
        }
        return body;
    }

    // Build per-parameter substituted ExpressionTypes for a
    // generic SAM whose parent interface has type-parameters and
    // whose call site supplied captured generic args. Returns null
    // when there's nothing to substitute (no args, no T-typed
    // parameter, etc.) — caller falls back to msig.paramAt.
    private static ExpressionType[] resolveSamParamTypes(
            MethodDefinition sam, MethodSignature msig,
            ClassDefinition iface, String varTypeArgsJls, Context c) {
        if (varTypeArgsJls == null || iface == null) return null;
        String[] typeParamNames = iface.getGenericTypeParamNames();
        if (typeParamNames == null || typeParamNames.length == 0) return null;
        ExpressionType[] argEts = parseTopLevelArgs(varTypeArgsJls, c);
        if (argEts == null || argEts.length != typeParamNames.length) {
            return null;
        }
        Term paramList = sam.getParamList();
        ObjVector params = new ObjVector();
        collectFormalParams(paramList, params);
        int n = msig.paramCount();
        if (params.size() != n) return null;
        ExpressionType[] out = new ExpressionType[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            out[i] = msig.paramAt(i);
            Term fp = (Term) params.elementAt(i);
            if (!(fp instanceof FormalParameter)) continue;
            Term type = ((FormalParameter) fp).terms[1];
            if (!(type instanceof ClassOrIfaceType)) continue;
            Term name = ((ClassOrIfaceType) type).getNameTerm();
            String tvar = Parser.getErasedTypeVarName(name);
            if (tvar == null) continue;
            for (int j = 0; j < typeParamNames.length; j++) {
                if (typeParamNames[j].equals(tvar) && argEts[j] != null) {
                    out[i] = argEts[j];
                    any = true;
                    break;
                }
            }
        }
        return any ? out : null;
    }

    // Standards-pass P5: substitute T → matching captured arg for
    // SAMs whose return type is a type-var (e.g.
    // `interface Supplier<T> { T get(); }` targeted as
    // `Supplier<Integer>`). Reads the type-var name via
    // MethodDefinition.getReturnTypeVarName, which falls back to
    // JdkGenericOverlay for pre-generics classpath methods.
    private static ExpressionType resolveTypeVarReturn(MethodDefinition sam,
            ClassDefinition iface, String varTypeArgsJls, Context c) {
        if (varTypeArgsJls == null || iface == null) return null;
        String tvar = sam.getReturnTypeVarName();
        if (tvar == null) return null;
        String[] typeParamNames = iface.getGenericTypeParamNames();
        if (typeParamNames == null) return null;
        int tvarIndex = -1;
        for (int i = 0; i < typeParamNames.length; i++) {
            if (tvar.equals(typeParamNames[i])) {
                tvarIndex = i; break;
            }
        }
        if (tvarIndex < 0) return null;
        ExpressionType[] argEts = parseTopLevelArgs(varTypeArgsJls, c);
        if (argEts == null || tvarIndex >= argEts.length) return null;
        return argEts[tvarIndex];
    }

    private static void collectFormalParams(Term t, ObjVector out) {
        if (t == null || !t.notEmpty()) return;
        if (t instanceof FormalParamList) {
            FormalParamList list = (FormalParamList) t;
            collectFormalParams(list.terms[0], out);
            collectFormalParams(list.terms[1], out);
        } else if (t instanceof FormalParameter) {
            out.addElement(t);
        }
    }

    // Parse a JLS-form generic-args string like `<Ljava/lang/String;>`
    // or `<Ljava/lang/String;Ljava/lang/Integer;>` into an array of
    // ExpressionType. Returns null if any arg is a wildcard / nested
    // generic / type-var reference / array type — substitution is only
    // attempted when each arg resolves cleanly to an erased class.
    private static ExpressionType[] parseTopLevelArgs(String jls,
            Context c) {
        if (jls == null || jls.length() < 2 || jls.charAt(0) != '<'
                || jls.charAt(jls.length() - 1) != '>') {
            return null;
        }
        ObjVector out = new ObjVector();
        int i = 1;
        int end = jls.length() - 1;
        while (i < end) {
            char ch = jls.charAt(i);
            if (ch != 'L') return null;
            int depth = 0;
            int j = i + 1;
            int semi = -1;
            while (j < end) {
                char c2 = jls.charAt(j);
                if (c2 == '<') depth++;
                else if (c2 == '>') depth--;
                else if (c2 == ';' && depth == 0) { semi = j; break; }
                j++;
            }
            if (semi < 0) return null;
            int lt = jls.indexOf('<', i + 1);
            String dotted;
            if (lt >= 0 && lt < semi) {
                dotted = jls.substring(i + 1, lt).replace('/', '.');
            } else {
                dotted = jls.substring(i + 1, semi).replace('/', '.');
            }
            // Captured args store the type name AS WRITTEN by the user
            // (Slice 50). For an imported single-id name like "String"
            // we resolve via Context to "java.lang.String"; for an
            // already-qualified name like "java.lang.String" the
            // resolver passes it through.
            ClassDefinition cd = c == null ? null
                    : c.resolveClass(dotted, false, false);
            if (cd == null) {
                if (!Main.dict.exists(dotted)) return null;
                cd = Main.dict.get(dotted);
            }
            if (cd == null) return null;
            out.addElement(cd);
            i = semi + 1;
        }
        ExpressionType[] arr = new ExpressionType[out.size()];
        for (int k = 0; k < out.size(); k++) {
            arr[k] = (ExpressionType) out.elementAt(k);
        }
        return arr;
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

    private static Term qualifiedName(String runtimeName) {
        // JCGO's runtime class names use `$` for inner-class
        // boundaries; source-level QualifiedName chains want `.`
        // between every segment. See MethodInvocation.qualifiedNameOf
        // for the failure mode this guards against (Issue #147).
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
