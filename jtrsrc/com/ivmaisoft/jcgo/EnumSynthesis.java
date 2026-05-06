/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/EnumSynthesis.java --
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
 * Slice 19 (Java 5, JLS 8.9). Synthesizes the class body for an
 * `enum` declaration:
 *
 *   enum Color { RED, GREEN, BLUE }
 *
 * lowers to a final class extending java.lang.Enum, with:
 *   - one `public static final Color FOO = new Color("FOO", N);`
 *     field per declared constant;
 *   - a synthetic `private static final Color[] $VALUES` array;
 *   - a private 2-arg constructor that calls super(name, ordinal);
 *   - public static `values()` and `valueOf(String)` methods.
 *
 * Java 5 / MVP scope: zero-arg constants only. Constants declared
 * with `(args)` and user-supplied custom constructors are a
 * follow-up — they require rewriting the user's constructor to
 * prepend (String, int) params.
 */
final class EnumSynthesis {

    /**
     * One declared enum constant — slice 19b. `args` is the parsed
     * Argument-chain (or Empty.term) from `RED(0xff0000, ...)`. For
     * zero-arg constants `args` is Empty.term and the constant call
     * just receives the synthesized (name, ordinal) pair.
     */
    static final class EnumConstant {
        final String name;
        final Term args;

        EnumConstant(String name, Term args) {
            this.name = name;
            this.args = args;
        }
    }

    private EnumSynthesis() {
    }

    /**
     * Returns a synthesized class body for the named enum.
     *
     * @param enumName  simple name of the enum class
     * @param constants ObjVector of EnumConstant in declaration order
     * @param userBody  user-supplied class body members (after the `;`),
     *                  or Empty.term if none. Constructors found here
     *                  are rewritten to prepend (String, int) params
     *                  and call super(name, ordinal); otherwise members
     *                  pass through verbatim.
     */
    static Term buildBody(String enumName, ObjVector constants,
            Term userBody) {
        ObjVector members = new ObjVector();

        for (int i = 0; i < constants.size(); i++) {
            EnumConstant ec = (EnumConstant) constants.elementAt(i);
            members.addElement(buildConstantField(enumName, ec, i));
        }
        members.addElement(buildValuesArrayField(enumName, constants));

        boolean userHasCtor = userBody != null && userBody.notEmpty()
                && containsConstructor(userBody);
        if (userHasCtor) {
            members.addElement(rewriteUserBody(userBody, enumName));
        } else {
            members.addElement(buildCtor(enumName));
            if (userBody != null && userBody.notEmpty()) {
                members.addElement(userBody);
            }
        }

        members.addElement(buildValuesMethod(enumName));
        members.addElement(buildValueOfMethod(enumName));

        return new Seq(seqOf(members), Empty.newTerm());
    }

    private static boolean containsConstructor(Term node) {
        if (!node.notEmpty()) return false;
        if (node instanceof Seq) {
            Seq s = (Seq) node;
            return containsConstructor(s.terms[0])
                    || containsConstructor(s.terms[1]);
        }
        if (node instanceof TypeDeclaration) {
            return ((TypeDeclaration) node).getDeclTerm()
                    instanceof ConstrDeclaration;
        }
        return false;
    }

    /**
     * Walks the user body Seq and rewrites every ConstrDeclaration to
     * prepend (String $n, int $o) params plus a super($n, $o) call at
     * the start of its body. Non-ctor members pass through.
     */
    private static Term rewriteUserBody(Term node, String enumName) {
        if (!node.notEmpty()) return node;
        if (node instanceof Seq) {
            Seq s = (Seq) node;
            return new Seq(rewriteUserBody(s.terms[0], enumName),
                    rewriteUserBody(s.terms[1], enumName));
        }
        if (node instanceof TypeDeclaration) {
            TypeDeclaration td = (TypeDeclaration) node;
            Term inner = td.getDeclTerm();
            if (inner instanceof ConstrDeclaration) {
                Term newCtor = rewriteCtor((ConstrDeclaration) inner,
                        enumName);
                return new TypeDeclaration(td.terms[0], newCtor);
            }
        }
        return node;
    }

    private static Term rewriteCtor(ConstrDeclaration ctor, String enumName) {
        Term originalParams = ctor.terms[1];
        Term originalBody = ctor.terms[3];

        Term nameParam = new FormalParameter(Empty.newTerm(),
                new ClassOrIfaceType(qualifiedName(Names.JAVA_LANG_STRING)),
                Empty.newTerm(),
                new VariableIdentifier(new LexTerm(LexTerm.ID, "$n")),
                Empty.newTerm());
        Term ordinalParam = new FormalParameter(Empty.newTerm(),
                new PrimitiveType(Type.INT),
                Empty.newTerm(),
                new VariableIdentifier(new LexTerm(LexTerm.ID, "$o")),
                Empty.newTerm());
        Term newParams = FormalParamList.prepend(nameParam,
                FormalParamList.prepend(ordinalParam, originalParams));

        Term superArgs = new ParameterList(
                new Argument(new Expression(new QualifiedName(
                        new LexTerm(LexTerm.ID, "$n"), Empty.newTerm()))),
                new Argument(new Expression(new QualifiedName(
                        new LexTerm(LexTerm.ID, "$o"), Empty.newTerm()))));
        Term superCall = new ExprStatement(new ConstructorCall(
                Empty.newTerm(), new Super(), superArgs));
        Term newBody = originalBody.notEmpty()
                ? new Seq(superCall, originalBody) : superCall;

        return new ConstrDeclaration(ctor.terms[0], newParams,
                ctor.terms[2], newBody);
    }

    /**
     * `public static final T NAME = new T("NAME", ordinal[, userArgs]);`
     */
    private static Term buildConstantField(String enumName, EnumConstant ec,
            int ordinal) {
        Term modifiers = new Seq(new AccModifier(AccModifier.PUBLIC),
                new Seq(new AccModifier(AccModifier.STATIC),
                        new AccModifier(AccModifier.FINAL)));
        Term enumType = simpleType(enumName);

        Term nameArg = new Argument(new StringLiteral(
                "\"" + ec.name + "\""));
        Term ordinalArg = new Argument(
                new IntLiteral(Integer.toString(ordinal)));
        Term args;
        if (ec.args != null && ec.args.notEmpty()) {
            args = new ParameterList(nameArg,
                    new ParameterList(ordinalArg, ec.args));
        } else {
            args = new ParameterList(nameArg, ordinalArg);
        }

        Term init = new InstanceCreation(simpleType(enumName), args,
                Empty.newTerm());
        Term varDeclr = new VariableDeclarator(
                new VariableIdentifier(new LexTerm(LexTerm.ID, ec.name)),
                Empty.newTerm(), init);
        Term field = new FieldDeclaration(enumType, Empty.newTerm(),
                varDeclr);
        return new TypeDeclaration(modifiers, field);
    }

    /**
     * `private static final NAME[] $VALUES = { CONST1, CONST2, ... };`
     */
    private static Term buildValuesArrayField(String enumName,
            ObjVector constants) {
        Term modifiers = new Seq(new AccModifier(AccModifier.PRIVATE),
                new Seq(new AccModifier(AccModifier.STATIC),
                        new AccModifier(AccModifier.FINAL)));
        Term arrType = new TypeWithDims(simpleType(enumName),
                new DimSpec(Empty.newTerm()));
        Term elements = Empty.newTerm();
        for (int i = constants.size() - 1; i >= 0; i--) {
            EnumConstant ec = (EnumConstant) constants.elementAt(i);
            Term ref = new Expression(new QualifiedName(
                    new LexTerm(LexTerm.ID, ec.name), Empty.newTerm()));
            Term elem = new ArrElementInit(ref);
            elements = elements.notEmpty()
                    ? new VarInitializers(elem, elements) : elem;
        }
        Term init = new ArrayInitializer(elements);
        Term varDeclr = new VariableDeclarator(
                new VariableIdentifier(new LexTerm(LexTerm.ID, "$VALUES")),
                Empty.newTerm(), init);
        Term field = new FieldDeclaration(arrType, Empty.newTerm(), varDeclr);
        return new TypeDeclaration(modifiers, field);
    }

    /**
     * `private NAME(String name, int ordinal) { super(name, ordinal); }`
     */
    private static Term buildCtor(String enumName) {
        Term nameParam = new FormalParameter(Empty.newTerm(),
                new ClassOrIfaceType(qualifiedName(Names.JAVA_LANG_STRING)),
                Empty.newTerm(),
                new VariableIdentifier(new LexTerm(LexTerm.ID, "$n")),
                Empty.newTerm());
        Term ordinalParam = new FormalParameter(Empty.newTerm(),
                new PrimitiveType(Type.INT),
                Empty.newTerm(),
                new VariableIdentifier(new LexTerm(LexTerm.ID, "$o")),
                Empty.newTerm());
        Term params = new FormalParamList(nameParam, ordinalParam);

        Term superArgs = new ParameterList(
                new Argument(new Expression(new QualifiedName(
                        new LexTerm(LexTerm.ID, "$n"), Empty.newTerm()))),
                new Argument(new Expression(new QualifiedName(
                        new LexTerm(LexTerm.ID, "$o"), Empty.newTerm()))));
        // ConstrDeclaration wraps the body in a ConstructorBlock itself,
        // so we pass the bare statement here (not a Block) — otherwise
        // the super() call lands inside a regular block and triggers
        // "Constructor invocation is not allowed here".
        Term superCall = new ExprStatement(new ConstructorCall(
                Empty.newTerm(), new Super(), superArgs));

        Term ctor = new ConstrDeclaration(
                new LexTerm(LexTerm.ID, enumName),
                params, Empty.newTerm(), superCall);
        Term modifiers = new AccModifier(AccModifier.PRIVATE);
        return new TypeDeclaration(modifiers, ctor);
    }

    /**
     * `public static T[] values() { return (T[]) $VALUES.clone(); }`
     */
    private static Term buildValuesMethod(String enumName) {
        Term retType = new TypeWithDims(simpleType(enumName),
                new DimSpec(Empty.newTerm()));
        Term valuesRef = new Expression(new QualifiedName(
                new LexTerm(LexTerm.ID, "$VALUES"), Empty.newTerm()));
        Term cloneCall = new MethodInvocation(valuesRef,
                new LexTerm(LexTerm.ID, "clone"), Empty.newTerm());
        Term cast = new CastExpression(retType, cloneCall);
        Term body = new Block(new ReturnStatement(cast));
        Term method = new MethodDeclaration(retType, Empty.newTerm(),
                new LexTerm(LexTerm.ID, "values"),
                Empty.newTerm(), Empty.newTerm(), Empty.newTerm(), body);
        Term modifiers = new Seq(new AccModifier(AccModifier.PUBLIC),
                new AccModifier(AccModifier.STATIC));
        return new TypeDeclaration(modifiers, method);
    }

    /**
     * `public static T valueOf(String name) {
     *      for (int i = 0; i < $VALUES.length; i++)
     *          if ($VALUES[i].name().equals(name)) return $VALUES[i];
     *      throw new IllegalArgumentException(name);
     *  }`
     *
     * Avoids reflection so it works without Modifier.ENUM bits being
     * tracked on the synthesized fields.
     */
    private static Term buildValueOfMethod(String enumName) {
        Term retType = simpleType(enumName);
        Term param = new FormalParameter(Empty.newTerm(),
                new ClassOrIfaceType(qualifiedName(Names.JAVA_LANG_STRING)),
                Empty.newTerm(),
                new VariableIdentifier(new LexTerm(LexTerm.ID, "$s")),
                Empty.newTerm());

        // Linear search over $VALUES via index-based for loop.
        // for (int $i = 0; $i < $VALUES.length; $i++) { ... }
        // ForStatement expects the init/update slots in their bare form
        // (LocalVariableDecl, PostfixOp) — no ExprStatement wrapper.
        Term iVar = new VariableIdentifier(new LexTerm(LexTerm.ID, "$i"));
        Term forInitDeclr = new VariableDeclarator(iVar, Empty.newTerm(),
                new IntLiteral("0"));
        Term forInit = new LocalVariableDecl(new PrimitiveType(Type.INT),
                forInitDeclr);

        Term iRef = new Expression(new QualifiedName(
                new LexTerm(LexTerm.ID, "$i"), Empty.newTerm()));
        Term valuesLengthAccess = new PrimaryFieldAccess(
                new Expression(new QualifiedName(
                        new LexTerm(LexTerm.ID, "$VALUES"), Empty.newTerm())),
                new QualifiedName(new LexTerm(LexTerm.ID, "length"),
                        Empty.newTerm()));
        Term forCond = new RelationalOp(iRef,
                new LexTerm(LexTerm.LT, "<"), valuesLengthAccess);

        Term iIncr = new PostfixOp(iRef,
                new LexTerm(LexTerm.INCREMENT, "++"));

        // $VALUES[$i].name().equals($s)
        Term valuesArr = new Expression(new QualifiedName(
                new LexTerm(LexTerm.ID, "$VALUES"), Empty.newTerm()));
        Term arrIndex = new Expression(new QualifiedName(
                new LexTerm(LexTerm.ID, "$i"), Empty.newTerm()));
        Term arrAccess = new ArrayAccess(valuesArr, arrIndex);
        Term nameCall = new MethodInvocation(arrAccess,
                new LexTerm(LexTerm.ID, "name"), Empty.newTerm());
        Term equalsArg = new Argument(new Expression(new QualifiedName(
                new LexTerm(LexTerm.ID, "$s"), Empty.newTerm())));
        Term equalsCall = new MethodInvocation(nameCall,
                new LexTerm(LexTerm.ID, "equals"), equalsArg);

        Term valuesArr2 = new Expression(new QualifiedName(
                new LexTerm(LexTerm.ID, "$VALUES"), Empty.newTerm()));
        Term arrIndex2 = new Expression(new QualifiedName(
                new LexTerm(LexTerm.ID, "$i"), Empty.newTerm()));
        Term arrAccess2 = new ArrayAccess(valuesArr2, arrIndex2);
        Term returnFound = new ReturnStatement(arrAccess2);

        Term ifMatch = new IfThenElse(new Expression(equalsCall),
                returnFound, Empty.newTerm());
        Term forBody = new Block(ifMatch);

        Term forStmt = new ForStatement(forInit, forCond, iIncr, forBody);

        // throw new IllegalArgumentException($s)
        Term iaeArgs = new Argument(new Expression(new QualifiedName(
                new LexTerm(LexTerm.ID, "$s"), Empty.newTerm())));
        Term iaeNew = new InstanceCreation(
                new ClassOrIfaceType(qualifiedName(
                        "java.lang.IllegalArgumentException")),
                iaeArgs, Empty.newTerm());
        Term throwStmt = new ThrowStatement(iaeNew);

        Term body = new Block(new Seq(forStmt, throwStmt));
        Term method = new MethodDeclaration(retType, Empty.newTerm(),
                new LexTerm(LexTerm.ID, "valueOf"),
                param, Empty.newTerm(), Empty.newTerm(), body);
        Term modifiers = new Seq(new AccModifier(AccModifier.PUBLIC),
                new AccModifier(AccModifier.STATIC));
        return new TypeDeclaration(modifiers, method);
    }

    private static Term simpleType(String name) {
        return new ClassOrIfaceType(qualifiedName(name));
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
