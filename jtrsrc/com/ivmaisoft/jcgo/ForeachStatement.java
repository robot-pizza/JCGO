/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/ForeachStatement.java --
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
 * Grammar production for the enhanced for statement (Java 5+).
 *
 * Format: FOR LPAREN SimpleType Identifier COLON Expression RPAREN Statement
 *
 * Two desugars, picked at pass1 from the iter expression's static
 * type — matches javac's lowering:
 *
 *   Array iter:
 *     for (T x : arr) body
 *       desugars to
 *     { T[] $arr = arr;
 *       int  $i = 0;
 *       for (; $i &lt; $arr.length; $i++) {
 *         T x = $arr[$i];
 *         body;
 *       }
 *     }
 *
 *   Iterable iter:
 *     for (T x : iter) body
 *       desugars to
 *     { java.util.Iterator $it = iter.iterator();
 *       while ($it.hasNext()) {
 *         T x = (T) $it.next();
 *         body;
 *       }
 *     }
 *
 * The synthesized temp names embed a per-instance counter to avoid
 * collision across nested foreaches and user code.
 *
 * D6 history: an earlier version (slice 1) emitted only the
 * array-style desugar, on the assumption that all iters would be
 * arrays. Real `for (T x : list)` then emitted a `(jObjectArr) list`
 * cast — silently miscompiled, and crashed at runtime on a real
 * List. Now picks at pass1 by inspecting iter's exprType: arrays
 * keep the original lowering, Iterable-implementing types route
 * through the iterator desugar.
 */

final class ForeachStatement extends LexNode {

    private static int nextId;

    private final Term userType;
    private final Term userVarIdent;
    private final Term userIter;
    private final Term userBody;
    private final boolean isVarForeach;
    private boolean desugared;

    ForeachStatement(Term type, Term varIdent, Term iter, Term body) {
        super(Empty.newTerm());
        this.userType = type;
        this.userVarIdent = varIdent;
        this.userIter = iter;
        this.userBody = body;
        this.isVarForeach = isVarLikeType(type);
    }

    private static boolean isVarLikeType(Term t) {
        if (!t.notEmpty()) {
            return false;
        }
        if (t.isName()) {
            return "var".equals(t.dottedName());
        }
        if (t instanceof ClassOrIfaceType) {
            Term inner = ((ClassOrIfaceType) t).getNameTerm();
            return inner != null && inner.isName()
                    && "var".equals(inner.dottedName());
        }
        return false;
    }

    void processPass1(Context c) {
        if (desugared) {
            terms[0].processPass1(c);
            return;
        }
        if (!c.versionAtLeast(JavaVersion.JLS_50)) {
            fatalError(c, "enhanced for loop requires -source 5 or higher (got "
                    + JavaVersion.format(Main.dict.javaVersion) + ")");
            return;
        }
        if (isVarForeach
                && !c.versionAtLeast(JavaVersion.JLS_100)) {
            fatalError(c,
                    "var in foreach requires -source 10 or higher (got "
                            + JavaVersion.format(Main.dict.javaVersion)
                            + ")");
            return;
        }

        // D6: pre-pass1 the iter to learn its static type so we can
        // pick the right desugar shape. Pass1 of expression terms is
        // idempotent (each guards on a per-instance "done" flag),
        // so the second pass1 inside the desugared LocalVariableDecl
        // is a no-op.
        userIter.processPass1(c);
        ExpressionType iterType = userIter.exprType();
        boolean isArray = iterType != null
                && iterType.signatureDimensions() > 0;
        boolean isIterable = !isArray && isIterableType(iterType, c);

        if (!isArray && !isIterable) {
            String got = iterType == null ? "?"
                    : iterType.signatureClass() == null ? "?"
                            : iterType.signatureClass().name();
            fatalError(c,
                    "for-each requires an array or java.lang.Iterable "
                            + "instance (got " + got + ")");
            return;
        }

        // For `for (var x : iter)` over an Iterable, var-inference
        // would pick Object from Iterator.next()'s erased return.
        // Resolve the element type from iter's slice-50 captured
        // generic args so var picks up the actual element type.
        Term effectiveUserType = userType;
        if (!isArray && isVarForeach) {
            Term resolved = resolveIterableElementType(userIter, c);
            if (resolved != null) {
                effectiveUserType = resolved;
            }
        }
        Term built = isArray
                ? buildArrayDesugar(userType, userVarIdent, userIter, userBody)
                : buildIterableDesugar(effectiveUserType, userVarIdent,
                        userIter, userBody);
        terms[0] = built;
        desugared = true;
        terms[0].processPass1(c);
    }

    // For `var x : iter` over Iterable<T>, build a ClassOrIfaceType
    // term for T from iter's variable's slice-50 captured args. Only
    // single-type-arg captures resolve (Iterable<X>, Collection<X>,
    // List<X>, ...); 2+-arg captures (Map<K,V>) aren't Iterable
    // directly so they don't reach this path.
    private static Term resolveIterableElementType(Term iter, Context c) {
        VariableDefinition v = iter.getVariable(true);
        if (v == null) return null;
        String captured = v.getFieldTypeCapturedArgs();
        if (captured == null) return null;
        ClassDefinition first = pickFirstJlsArgClass(captured, c);
        if (first == null) return null;
        return new ClassOrIfaceType(qualifiedName(first.name()));
    }

    private static ClassDefinition pickFirstJlsArgClass(String jls,
            Context c) {
        if (jls == null || jls.length() < 2 || jls.charAt(0) != '<'
                || jls.charAt(jls.length() - 1) != '>') {
            return null;
        }
        int i = 1;
        int end = jls.length() - 1;
        if (i >= end || jls.charAt(i) != 'L') return null;
        int depth = 0;
        int j = i + 1;
        int semi = -1;
        while (j < end) {
            char ch = jls.charAt(j);
            if (ch == '<') depth++;
            else if (ch == '>') depth--;
            else if (ch == ';' && depth == 0) { semi = j; break; }
            j++;
        }
        if (semi < 0) return null;
        int lt = jls.indexOf('<', i + 1);
        String dotted = (lt >= 0 && lt < semi)
                ? jls.substring(i + 1, lt).replace('/', '.')
                : jls.substring(i + 1, semi).replace('/', '.');
        ClassDefinition cd = c == null ? null
                : c.resolveClass(dotted, false, false);
        if (cd == null && Main.dict.exists(dotted)) {
            cd = Main.dict.get(dotted);
        }
        return cd;
    }

    private static boolean isIterableType(ExpressionType et, Context c) {
        if (et == null) return false;
        ClassDefinition cls = et.signatureClass();
        if (cls == null) return false;
        ClassDefinition iterable = c.resolveClass("java.lang.Iterable", false,
                false);
        if (iterable == null) return false;
        return ClassDefinition.isAssignableFrom(iterable, et, c.forClass);
    }

    private static String fresh(String prefix) {
        return prefix + (nextId++);
    }

    private static Term identName(String name) {
        return new QualifiedName(new LexTerm(LexTerm.ID, name), Empty.newTerm());
    }

    private static Term identRef(String name) {
        return new Expression(identName(name));
    }

    private static Term buildArrayDesugar(Term userType, Term userVarIdent,
            Term iter, Term body) {
        String aName = fresh("$jcgoArr$");
        String iName = fresh("$jcgoIdx$");

        // When userType is `var`, leave the array temp's type as `var` too —
        // slice 8's LocalVariableDecl var-inference will pick it up from iter
        // (the array initializer) and the loop variable's later inference
        // picks up the element type from $jcgoArr[$jcgoIdx].
        Term arrType = isVarLikeType(userType) ? userType
                : new TypeWithDims(userType,
                        new DimSpec(Empty.newTerm()));
        Term arrDeclr = new VariableDeclarator(
                new VariableIdentifier(new LexTerm(LexTerm.ID, aName)),
                Empty.newTerm(), iter);
        Term arrLocal = new ExprStatement(new LocalVariableDecl(arrType,
                arrDeclr));

        Term idxType = new PrimitiveType(Type.INT);
        Term idxDeclr = new VariableDeclarator(
                new VariableIdentifier(new LexTerm(LexTerm.ID, iName)),
                Empty.newTerm(), new IntLiteral("0"));
        Term idxLocal = new ExprStatement(new LocalVariableDecl(idxType,
                idxDeclr));

        Term lengthAccess = new PrimaryFieldAccess(identRef(aName),
                identName("length"));
        Term cond = new RelationalOp(identRef(iName),
                new LexTerm(LexTerm.LT, "<"), lengthAccess);

        Term update = new PostfixOp(identRef(iName),
                new LexTerm(LexTerm.INCREMENT, "++"));

        Term arrAccess = new ArrayAccess(identRef(aName), identRef(iName));
        Term userVarDeclr = new VariableDeclarator(userVarIdent,
                Empty.newTerm(), arrAccess);
        Term userVarLocal = new ExprStatement(new LocalVariableDecl(userType,
                userVarDeclr));

        Term innerBlock = new Block(new Seq(userVarLocal, body));
        Term forStmt = new ForStatement(Empty.newTerm(), cond, update,
                innerBlock);

        return new Block(new Seq(arrLocal, new Seq(idxLocal, forStmt)));
    }

    private static Term buildIterableDesugar(Term userType, Term userVarIdent,
            Term iter, Term body) {
        String itName = fresh("$jcgoIt$");

        // `Iterator $it = iter.iterator();`
        Term iteratorType = new ClassOrIfaceType(qualifiedName(
                "java.util.Iterator"));
        Term iteratorCall = new MethodInvocation(iter,
                new LexTerm(LexTerm.ID, "iterator"), Empty.newTerm());
        Term itDeclr = new VariableDeclarator(
                new VariableIdentifier(new LexTerm(LexTerm.ID, itName)),
                Empty.newTerm(), iteratorCall);
        Term itLocal = new ExprStatement(new LocalVariableDecl(iteratorType,
                itDeclr));

        // `$it.hasNext()` — loop condition.
        Term hasNextCall = new MethodInvocation(identRef(itName),
                new LexTerm(LexTerm.ID, "hasNext"), Empty.newTerm());

        // `T x = (T) $it.next();` — javac inserts the CHECKCAST at the
        // bytecode level because Iterator.next() erases to
        // `Object next()`. Skip the cast when the loop var is `var`
        // (slice 8 var-inference handles it) or when the loop var is
        // declared Object (no narrowing needed).
        Term nextCall = new MethodInvocation(identRef(itName),
                new LexTerm(LexTerm.ID, "next"), Empty.newTerm());
        Term initExpr;
        if (isVarLikeType(userType) || isObjectType(userType)) {
            initExpr = nextCall;
        } else {
            initExpr = new CastExpression(cloneTypeForCast(userType),
                    nextCall);
        }
        Term userVarDeclr = new VariableDeclarator(userVarIdent,
                Empty.newTerm(), initExpr);
        Term userVarLocal = new ExprStatement(new LocalVariableDecl(userType,
                userVarDeclr));

        Term innerBlock = new Block(new Seq(userVarLocal, body));
        Term whileStmt = new WhileStatement(hasNextCall, innerBlock);

        return new Block(new Seq(itLocal, whileStmt));
    }

    private static boolean isObjectType(Term t) {
        if (!(t instanceof ClassOrIfaceType)) return false;
        Term name = ((ClassOrIfaceType) t).getNameTerm();
        if (name == null) return false;
        String dotted = name.dottedName();
        return "Object".equals(dotted)
                || "java.lang.Object".equals(dotted);
    }

    private static Term cloneTypeForCast(Term origType) {
        if (origType instanceof ClassOrIfaceType) {
            Term name = ((ClassOrIfaceType) origType).getNameTerm();
            String dotted = name != null ? name.dottedName() : null;
            if (dotted != null) {
                return new ClassOrIfaceType(qualifiedName(dotted));
            }
        }
        return origType;
    }

    private static Term qualifiedName(String runtimeName) {
        // `$` → `.` so inner-class element types resolve at pass1.
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
}
