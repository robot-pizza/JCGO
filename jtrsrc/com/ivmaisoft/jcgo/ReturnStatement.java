/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/ReturnStatement.java --
 * a part of JCGO translator.
 **
 * Project: JCGO (http://www.ivmaisoft.com/jcgo/)
 * Copyright (C) 2001-2012 Ivan Maidanski <ivmai@mail.ru>
 * All rights reserved.
 */

/*
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 *
 * Modifications are licensed under the same terms as JCGO above:
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
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
 * Grammar production for 'return'.
 ** 
 * Format: RETURN [Expression] SEMI
 */

final class ReturnStatement extends LexNode {

    private TryStatement curTry;

    private MethodDefinition md;

    private ExpressionType actualType0;

    // Slice 22: synthesized preamble emitted before the actual return
    // when the source was `return switch (...) {...};`. Contains a
    // local-var declaration of the return type plus a switch-statement
    // that assigns into it. Set during processPass1; read at output.
    private Term liftedPreamble;

    private static int nextSwRetId = 0;

    ReturnStatement(Term b) {
        super(b);
    }

    void processPass1(Context c) {
        curTry = c.currentTry;
        md = c.currentMethod;
        assertCond(md != null);
        if (terms[0].notEmpty()) {
            if (md.isConstructor()) {
                fatalError(c,
                        "Return expression is not allowed for constructors");
            }
            // Slice 22 / 32: lift `return switch (...) {...};` into a
            // temp declaration + switch-stmt + return-of-temp. The
            // temp's type comes from md.exprType() — only available at
            // pass1.
            if (terms[0] instanceof SwitchExpression) {
                if (SwitchExpressionLifter.anyPatternCases(
                        (SwitchExpression) terms[0])) {
                    liftPatternSwitchExprReturn(c);
                } else {
                    liftSwitchExprReturn(c);
                }
            }
            // Slice 24d: a lambda or method reference at the head of
            // the return expression resolves its target type from the
            // method's declared return type. Thread it through
            // c.currentVarType the same way VariableDefinition does
            // for an initializer expression.
            ExpressionType oldVarType = c.currentVarType;
            if (terms[0] instanceof LambdaExpression
                    || terms[0] instanceof MethodReference) {
                c.currentVarType = md.exprType();
            }
            terms[0].processPass1(c);
            c.currentVarType = oldVarType;
            int s0 = terms[0].exprType().objectSize();
            int s1 = md.exprType().objectSize();
            // Slice 18 (Java 5): autobox/unbox return value to method's
            // declared return type.
            Term coerced = Autobox.coerce(c, terms[0], s1);
            if (coerced != terms[0]) {
                terms[0] = coerced;
                s0 = terms[0].exprType().objectSize();
            }
            if (s0 == Type.BOOLEAN ? s1 != Type.BOOLEAN
                    : s0 >= Type.CLASSINTERFACE ? s1 < Type.CLASSINTERFACE
                            : s0 >= Type.LONG && s1 < s0) {
                fatalError(c, "Incompatible type of return expression");
            }
            actualType0 = terms[0].actualExprType();
            md.setActualType(actualType0, terms[0]);
        } else if (!md.isConstructor()
                && md.exprType().objectSize() != Type.VOID) {
            fatalError(c, "Missing return expression");
        }
        md.setMethodBranchFrom(c);
    }

    private void liftSwitchExprReturn(Context c) {
        SwitchExpression se = (SwitchExpression) terms[0];
        String tempName = "$jcgoSwRet$" + (nextSwRetId++);
        Term retTypeTerm = exprTypeToTypeTerm(md.exprType());
        if (retTypeTerm == null) {
            // Method returns void or a type we can't reify — fall through;
            // the existing pass1 will report the mismatch.
            return;
        }
        Term decl = new ExprStatement(new LocalVariableDecl(retTypeTerm,
                new VariableDeclarator(
                        new VariableIdentifier(
                                new LexTerm(LexTerm.ID, tempName)),
                        Empty.newTerm(), Empty.newTerm())));
        Term switchStmt = SwitchExpressionLifter.buildSwitchStmt(se, tempName);
        liftedPreamble = new Seq(decl, switchStmt);
        liftedPreamble.processPass1(c);
        terms[0] = new Expression(new QualifiedName(
                new LexTerm(LexTerm.ID, tempName), Empty.newTerm()));
    }

    private void liftPatternSwitchExprReturn(Context c) {
        SwitchExpression se = (SwitchExpression) terms[0];
        String tempName = "$jcgoSwRet$" + (nextSwRetId++);
        Term retTypeTerm = exprTypeToTypeTerm(md.exprType());
        if (retTypeTerm == null) return;
        Term decl = new ExprStatement(new LocalVariableDecl(retTypeTerm,
                new VariableDeclarator(
                        new VariableIdentifier(
                                new LexTerm(LexTerm.ID, tempName)),
                        Empty.newTerm(), Empty.newTerm())));
        // The pattern-switch lift produces:
        //   { decl; matchedDecl; if(!$matched) {...}; ... }
        // We then return $tmp after.
        liftedPreamble = SwitchExpressionLifter.buildPatternSwitchStmts(
                se, tempName, decl);
        liftedPreamble.processPass1(c);
        terms[0] = new Expression(new QualifiedName(
                new LexTerm(LexTerm.ID, tempName), Empty.newTerm()));
    }

    private static Term exprTypeToTypeTerm(ExpressionType et) {
        int dims = et.signatureDimensions();
        ClassDefinition cd = et.signatureClass();
        if (cd == null) return null;
        int sz = cd.objectSize();
        Term base;
        if (sz < Type.CLASSINTERFACE && sz != Type.NULLREF) {
            base = new PrimitiveType(sz);
        } else if (sz == Type.CLASSINTERFACE) {
            base = new ClassOrIfaceType(qualifiedNameTerm(cd.name()));
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

    private static Term qualifiedNameTerm(String name) {
        Term qn = null;
        int idx = name.length();
        while (idx > 0) {
            int prev = name.lastIndexOf('.', idx - 1);
            String part = name.substring(prev + 1, idx);
            Term lt = new LexTerm(LexTerm.ID, part);
            qn = qn == null ? new QualifiedName(lt, Empty.newTerm())
                    : new QualifiedName(lt, qn);
            idx = prev;
        }
        return qn;
    }

    boolean hasTailReturnOrThrow() {
        return true;
    }

    boolean isReturnAtEnd(boolean allowBreakThrow) {
        return true;
    }

    boolean allowInline(int tokenLimit) {
        return terms[0].tokenCount() <= tokenLimit;
    }

    MethodDefinition superMethodCall() {
        return terms[0].superMethodCall();
    }

    void discoverObjLeaks() {
        assertCond(md != null);
        if (liftedPreamble != null) {
            liftedPreamble.discoverObjLeaks();
        }
        terms[0].discoverObjLeaks();
        if (md.exprType().objectSize() >= Type.CLASSINTERFACE) {
            terms[0].setObjLeaks(VariableDefinition.RETURN_VAR);
        }
    }

    void processOutput(OutputContext oc) {
        // Slice 22: emit the synthesized decl + switch-stmt before the
        // return when we lifted a `return switch (...) {...}` form.
        if (liftedPreamble != null) {
            liftedPreamble.processOutput(oc);
        }
        assertCond(md != null);
        boolean useVar = false;
        boolean needsEnd = false;
        if (curTry != null) {
            if (!terms[0].isSafeExpr() || terms[0].isFieldAccessed(null)) {
                oc.cPrint("{");
                oc.cPrint(md.exprType().castName());
                oc.cPrint(" jcgo_retval=");
                writeReturnExpression(oc);
                oc.cPrint(";");
                useVar = true;
            }
            if (curTry.outputFinallyGroupInner(oc, null, useVar)) {
                if (useVar) {
                    if (!oc.insideNotSehTry) {
                        oc.cPrint("\n#endif\010");
                    }
                } else {
                    if (oc.insideNotSehTry)
                        return;
                    oc.cPrint("\n#else\010");
                    needsEnd = true;
                }
            }
        }
        oc.cPrint("return");
        if (useVar) {
            oc.cPrint(" jcgo_retval;}");
        } else {
            writeReturnExpression(oc);
            oc.cPrint(";");
            if (needsEnd) {
                oc.cPrint("\n#endif\010");
            }
        }
    }

    private void writeReturnExpression(OutputContext oc) {
        assertCond(md != null);
        if (terms[0].notEmpty()) {
            oc.cPrint(" ");
            ExpressionType exprType = md.exprType();
            if (exprType.objectSize() == Type.BOOLEAN
                    || exprType != terms[0].exprType()) {
                oc.cPrint("(");
                oc.cPrint(exprType.castName());
                oc.cPrint(")");
                terms[0].atomaryOutput(oc);
            } else {
                terms[0].processOutput(oc);
            }
        } else if (md.isConstructor()) {
            oc.cPrint(" ");
            oc.cPrint(This.CNAME);
        }
    }

    ExpressionType traceClassInit() {
        assertCond(md != null);
        if (terms[0].notEmpty()) {
            ExpressionType curTraceType0 = terms[0].traceClassInit();
            md.setTraceExprType(curTraceType0 != null ? curTraceType0
                    : actualType0);
        }
        return null;
    }
}
