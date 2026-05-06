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

    private EnumSynthesis() {
    }

    /**
     * Returns a synthesized class body for the named enum.
     *
     * @param enumName  simple name of the enum class
     * @param constants ObjVector of String — the constant identifiers
     *                  in declaration order
     */
    static Term buildBody(String enumName, ObjVector constants) {
        ObjVector members = new ObjVector();

        for (int i = 0; i < constants.size(); i++) {
            String constName = (String) constants.elementAt(i);
            members.addElement(buildConstantField(enumName, constName, i));
        }
        members.addElement(buildValuesArrayField(enumName, constants));
        members.addElement(buildCtor(enumName));
        members.addElement(buildValuesMethod(enumName));
        members.addElement(buildValueOfMethod(enumName));

        return new Seq(seqOf(members), Empty.newTerm());
    }

    /**
     * `public static final NAME T = new T("NAME", ordinal);`
     */
    private static Term buildConstantField(String enumName, String constName,
            int ordinal) {
        Term modifiers = new Seq(new AccModifier(AccModifier.PUBLIC),
                new Seq(new AccModifier(AccModifier.STATIC),
                        new AccModifier(AccModifier.FINAL)));
        Term enumType = simpleType(enumName);
        Term args = new ParameterList(
                new Argument(new StringLiteral("\"" + constName + "\"")),
                new Argument(new IntLiteral(Integer.toString(ordinal))));
        Term init = new InstanceCreation(simpleType(enumName), args,
                Empty.newTerm());
        Term varDeclr = new VariableDeclarator(
                new VariableIdentifier(new LexTerm(LexTerm.ID, constName)),
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
            String constName = (String) constants.elementAt(i);
            Term ref = new Expression(new QualifiedName(
                    new LexTerm(LexTerm.ID, constName), Empty.newTerm()));
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
