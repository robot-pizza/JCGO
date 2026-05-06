/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/ForeachStatement.java --
 * a part of JCGO translator.
 **
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 */

/*
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 **
 * This software is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License (GPL) for more details.
 **
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 **
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module. An independent module is a module which is not derived from
 * or based on this library. If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 */

package com.ivmaisoft.jcgo;

/**
 * Grammar production for the enhanced for statement (Java 5+).
 **
 * Format: FOR LPAREN SimpleType Identifier COLON Expression RPAREN Statement
 *
 * Slice 1 covers array-typed iterables only. The iterable expression is
 * desugared at construction time into a classic for-loop:
 *
 *   for (T x : iter) body
 *      desugars to
 *   { T[] $jcgoArr$N = iter;
 *     int $jcgoIdx$N = 0;
 *     for (; $jcgoIdx$N &lt; $jcgoArr$N.length; $jcgoIdx$N++) {
 *       T x = $jcgoArr$N[$jcgoIdx$N];
 *       body;
 *     }
 *   }
 *
 * The synthetic temps' names embed a per-instance counter to avoid collision
 * across nested foreaches and across foreach + user code.
 */

final class ForeachStatement extends LexNode {

    private static int nextId;

    private boolean isVarForeach;

    ForeachStatement(Term type, Term varIdent, Term iter, Term body) {
        super(buildDesugar(type, varIdent, iter, body));
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
        if (Main.dict.javaVersion < JavaVersion.JLS_50) {
            fatalError(c, "enhanced for loop requires -source 5 or higher (got "
                    + JavaVersion.format(Main.dict.javaVersion) + ")");
        }
        if (isVarForeach
                && Main.dict.javaVersion < JavaVersion.JLS_100) {
            fatalError(c,
                    "var in foreach requires -source 10 or higher (got "
                            + JavaVersion.format(Main.dict.javaVersion)
                            + ")");
        }
        terms[0].processPass1(c);
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

    private static Term buildDesugar(Term userType, Term userVarIdent,
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
}
