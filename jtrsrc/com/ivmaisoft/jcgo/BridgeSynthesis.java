/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/BridgeSynthesis.java --
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
 * Slice 51: synthesizes JLS-style bridge methods for classes that
 * extend a parameterized supertype.
 *
 * Java's generics rely on type erasure: a generic supertype
 * `Box<T> { void put(T x) }` has erased signature `void put(Object)`.
 * When a subclass declares `void put(String x)` (intending to override
 * after `extends Box<String>`), the JVM emits a synthetic bridge
 * `void put(Object x) { put((String) x); }` so virtual dispatch
 * through a Box-typed reference still hits the subclass override.
 *
 * JCGO's analyzer detects overrides by exact-string signature match
 * (parameter types as stored in MethodSignature). After slice-45
 * erasure, parent and subclass have non-matching signatures
 * (`(Object)` vs `(String)`), so the override isn't recognized and
 * the parent's method gets devirtualized away. This class restores
 * correctness by appending bridge MethodDeclaration AST nodes to the
 * subclass body at parse time.
 *
 * Detection heuristic: any class with `extends X<TypeArgs>` is a
 * candidate; for each declared method whose parameter list contains
 * at least one non-primitive non-Object reference type, build a
 * bridge with those types replaced by Object. The bridge body
 * delegates to the original method with `(OriginalType)` casts on
 * the arguments. Skips synthesis when an existing method already has
 * the bridge's erased signature.
 */
final class BridgeSynthesis {

    private BridgeSynthesis() {
    }

    /**
     * Walk the class body, find methods that need bridges, append the
     * synthesized bridges to the body. Returns the (possibly extended)
     * body Term. No-op when there are no candidates.
     */
    static Term wrap(Term body) {
        if (body == null || !body.notEmpty()) return body;
        ObjVector existingSigs = new ObjVector();
        ObjVector bridges = new ObjVector();
        collectExistingSigs(body, existingSigs);
        collectBridges(body, existingSigs, bridges);
        if (bridges.size() == 0) return body;
        Term out = body;
        for (int i = 0; i < bridges.size(); i++) {
            out = appendDeclaration(out, (Term) bridges.elementAt(i));
        }
        return out;
    }

    /**
     * Build a key string `name(P1Erased,P2Erased,...)` for each
     * declared method/constructor in the body. Used for collision
     * avoidance — we don't synthesize a bridge whose erased signature
     * already exists.
     */
    private static void collectExistingSigs(Term body, ObjVector out) {
        if (body == null || !body.notEmpty()) return;
        if (body instanceof Seq && ((Seq) body).terms.length >= 2) {
            collectExistingSigs(((Seq) body).terms[0], out);
            collectExistingSigs(((Seq) body).terms[1], out);
            return;
        }
        if (body instanceof TypeDeclaration) {
            Term member = ((TypeDeclaration) body).getDeclTerm();
            if (member instanceof MethodDeclaration) {
                String sig = methodErasedSig((MethodDeclaration) member);
                if (sig != null) out.addElement(sig);
            }
        }
    }

    private static void collectBridges(Term body, ObjVector existingSigs,
            ObjVector bridges) {
        if (body == null || !body.notEmpty()) return;
        if (body instanceof Seq && ((Seq) body).terms.length >= 2) {
            collectBridges(((Seq) body).terms[0], existingSigs, bridges);
            collectBridges(((Seq) body).terms[1], existingSigs, bridges);
            return;
        }
        if (body instanceof TypeDeclaration) {
            TypeDeclaration td = (TypeDeclaration) body;
            Term member = td.getDeclTerm();
            if (member instanceof MethodDeclaration) {
                Term bridge = tryBuildBridge(td.terms[0],
                        (MethodDeclaration) member, existingSigs);
                if (bridge != null) bridges.addElement(bridge);
            }
        }
    }

    /**
     * Compute `name(P1,P2,...)` using the declared parameter types
     * (NOT erased). Used to detect when a method we'd otherwise
     * synthesize a bridge for already exists in the same class.
     *
     * MethodDeclaration term layout: terms[2] is the name LexTerm,
     * terms[3] is the FormalParamList (or single FormalParameter).
     */
    private static String methodErasedSig(MethodDeclaration md) {
        Term nameTerm = md.terms[2];
        Term paramsTerm = md.terms[3];
        if (!(nameTerm instanceof LexTerm)) return null;
        StringBuffer sb = new StringBuffer();
        sb.append(((LexTerm) nameTerm).dottedName()).append('(');
        appendDeclaredParamSigs(paramsTerm, sb);
        sb.append(')');
        return sb.toString();
    }

    private static void appendDeclaredParamSigs(Term params, StringBuffer sb) {
        if (!params.notEmpty()) return;
        if (params instanceof FormalParamList) {
            appendDeclaredParamSigs(((FormalParamList) params).terms[0], sb);
            appendDeclaredParamSigs(((FormalParamList) params).terms[1], sb);
            return;
        }
        if (params instanceof FormalParameter) {
            FormalParameter fp = (FormalParameter) params;
            // FormalParameter terms: [0]=mods, [1]=type, [2]=dims,
            //                        [3]=ident, [4]=dims2.
            sb.append(declaredTypeName(fp.terms[1])).append(',');
        }
    }

    /**
     * Sig key for an EXISTING method param: the type as declared.
     * Used to detect collision with a bridge we'd synthesize — only
     * a real `(Object x)` method should make us skip a bridge.
     */
    private static String declaredTypeName(Term type) {
        if (type instanceof PrimitiveType) {
            return type.dottedName();
        }
        if (type instanceof ClassOrIfaceType) {
            Term name = ((ClassOrIfaceType) type).getNameTerm();
            if (name != null) {
                String dotted = name.dottedName();
                if (dotted != null) return dotted;
            }
            return "?";
        }
        return "?";
    }

    /**
     * If `md` declares at least one non-Object reference parameter,
     * build a bridge whose params are all Object and whose body
     * delegates to the original with downcasts. Returns null if no
     * bridge is needed or one already exists.
     */
    private static Term tryBuildBridge(Term modifiers, MethodDeclaration md,
            ObjVector existingSigs) {
        Term nameTerm = md.terms[2];
        if (!(nameTerm instanceof LexTerm)) return null;
        Term paramsTerm = md.terms[3];
        ObjVector params = new ObjVector();
        flattenParams(paramsTerm, params);
        if (params.size() == 0) return null;
        boolean anyRef = false;
        for (int i = 0; i < params.size(); i++) {
            FormalParameter fp = (FormalParameter) params.elementAt(i);
            if (isNonObjectReferenceType(fp.terms[1])) {
                anyRef = true;
                break;
            }
        }
        if (!anyRef) return null;
        // Bridge erased sig already exists? skip.
        StringBuffer sigSb = new StringBuffer();
        sigSb.append(((LexTerm) nameTerm).dottedName()).append('(');
        for (int i = 0; i < params.size(); i++) {
            sigSb.append("Object,");
        }
        sigSb.append(')');
        String bridgeSig = sigSb.toString();
        for (int i = 0; i < existingSigs.size(); i++) {
            if (bridgeSig.equals(existingSigs.elementAt(i))) {
                return null;
            }
        }
        // Build bridge param list: each param becomes Object x_i.
        // Build delegate args: each arg is `(OrigType) x_i` cast.
        Term newParams = Empty.newTerm();
        Term newArgs = Empty.newTerm();
        for (int i = params.size() - 1; i >= 0; i--) {
            FormalParameter origFp = (FormalParameter) params.elementAt(i);
            String paramName = "$bridge$p" + i;
            Term origType = origFp.terms[1];
            Term newFp = buildObjectFormalParam(paramName);
            newParams = newParams.notEmpty()
                    ? new FormalParamList(newFp, newParams) : newFp;
            Term cast = isNonObjectReferenceType(origType)
                    ? new CastExpression(cloneTypeForCast(origType),
                            new QualifiedName(
                                    new LexTerm(LexTerm.ID, paramName),
                                    Empty.newTerm()))
                    : (Term) new QualifiedName(
                            new LexTerm(LexTerm.ID, paramName),
                            Empty.newTerm());
            Term arg = new Argument(cast);
            newArgs = newArgs.notEmpty()
                    ? new ParameterList(arg, newArgs) : arg;
        }
        // Body: optionally `return` + invoke originalName(args)
        Term call = new MethodInvocation(
                new QualifiedName((LexTerm) nameTerm, Empty.newTerm()),
                newArgs);
        Term stmt;
        if (isVoidReturn(md.terms[0])) {
            stmt = new ExprStatement(call);
        } else {
            stmt = new ReturnStatement(call);
        }
        Term body = new Block(stmt);
        // Build new MethodDeclaration with same return type, name,
        // throws — params replaced.
        Term retType = md.terms[0];
        Term throwsTerm = md.terms[5];
        Term bridge = new MethodDeclaration(retType, Empty.newTerm(),
                nameTerm, newParams, Empty.newTerm(), throwsTerm, body);
        // Wrap with same modifiers as original (synthetic flag added).
        Term bridgeMods = new Seq(modifiers,
                new AccModifier(AccModifier.SYNTHETIC));
        return new TypeDeclaration(bridgeMods, bridge);
    }

    private static void flattenParams(Term params, ObjVector out) {
        if (!params.notEmpty()) return;
        if (params instanceof FormalParamList) {
            flattenParams(((FormalParamList) params).terms[0], out);
            flattenParams(((FormalParamList) params).terms[1], out);
            return;
        }
        if (params instanceof FormalParameter) {
            out.addElement(params);
        }
    }

    private static boolean isNonObjectReferenceType(Term type) {
        if (!(type instanceof ClassOrIfaceType)) return false;
        // ClassOrIfaceType stashes the name in a private field, not
        // in terms[]. Reach through getNameTerm() to read it.
        Term name = ((ClassOrIfaceType) type).getNameTerm();
        if (name == null) return false;
        String dotted = name.dottedName();
        return dotted != null
                && !"Object".equals(dotted)
                && !"java.lang.Object".equals(dotted);
    }

    private static boolean isVoidReturn(Term returnType) {
        if (returnType instanceof PrimitiveType) {
            String dn = returnType.dottedName();
            return "void".equals(dn);
        }
        return false;
    }

    private static FormalParameter buildObjectFormalParam(String name) {
        Term type = new ClassOrIfaceType(qualifiedNameOf("java.lang.Object"));
        return new FormalParameter(Empty.term, type, Empty.newTerm(),
                new VariableIdentifier(new LexTerm(LexTerm.ID, name)),
                Empty.newTerm());
    }

    /**
     * Clone a type Term well enough for use in a CastExpression.
     * ClassOrIfaceType wraps a QualifiedName; we rebuild the QName
     * from its dottedName to avoid sharing AST nodes between the
     * original parameter declaration and the bridge cast (which
     * would let analyzer state leak between them).
     */
    private static Term cloneTypeForCast(Term origType) {
        if (origType instanceof ClassOrIfaceType) {
            Term name = ((ClassOrIfaceType) origType).getNameTerm();
            String dotted = name != null ? name.dottedName() : null;
            if (dotted != null) {
                return new ClassOrIfaceType(qualifiedNameOf(dotted));
            }
        }
        return origType;
    }

    private static Term qualifiedNameOf(String runtimeName) {
        // `$` → `.` so inner-class names resolve at pass1.
        // Issue #147.
        String dotted = runtimeName.replace('$', '.');
        Term tail = Empty.newTerm();
        int idx = dotted.length();
        while (idx > 0) {
            int prev = dotted.lastIndexOf('.', idx - 1);
            String part = dotted.substring(prev + 1, idx);
            tail = new QualifiedName(new LexTerm(LexTerm.ID, part), tail);
            idx = prev;
        }
        return tail;
    }

    /**
     * Append `decl` to the end of the Seq tree representing the class
     * body. Walks down the right children of nested Seq nodes and
     * replaces the trailing empty terminator with `Seq(decl, Empty)`.
     */
    private static Term appendDeclaration(Term body, Term decl) {
        if (!body.notEmpty()) return new Seq(decl, Empty.newTerm());
        if (body instanceof Seq) {
            Term left = ((Seq) body).terms[0];
            Term right = ((Seq) body).terms[1];
            return new Seq(left, appendDeclaration(right, decl));
        }
        return new Seq(body, new Seq(decl, Empty.newTerm()));
    }
}
