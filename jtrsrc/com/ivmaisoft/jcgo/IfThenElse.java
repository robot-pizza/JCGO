/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/IfThenElse.java --
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

import java.util.Enumeration;

/**
 * Grammar production for the if-then-else constructions.
 ** 
 * Formats: IF LPAREN Expression RPAREN Statement IF LPAREN Expression RPAREN
 * StatementNoShortIf ELSE Statement
 */

final class IfThenElse extends LexNode {

    private ConstValue constVal0;

    private boolean isAssertion;

    IfThenElse(Term c, Term e, Term g) {
        super(c, e.isBlock() ? e : new Block(e),
                !g.notEmpty() || g.isBlock() ? g : new Block(g));
    }

    void processPass1(Context c) {
        assertCond(c.currentClass != null);
        isAssertion = terms[0].handleAssertionsDisabled(c.currentClass);
        terms[0].processPass1(c);
        if (terms[0].exprType().objectSize() != Type.BOOLEAN) {
            fatalError(c, "The condition expression must be of boolean type");
        }
        InstanceOf patternIof = unwrapPatternInstanceOf(terms[0]);
        if (patternIof != null) {
            prependPatternBinding(patternIof);
        }
        constVal0 = terms[0].evaluateConstValue();
        if (constVal0 != null) {
            terms[constVal0.isNonZero() ? 1 : 2].processPass1(c);
        } else {
            MethodDefinition md = c.currentMethod;
            assertCond(md != null);
            ObjQueue unsetVars = new ObjQueue();
            Enumeration en = md.getLocalsNames();
            while (en.hasMoreElements()) {
                VariableDefinition v = md
                        .getLocalVar((String) en.nextElement());
                if (v.isUnassigned()) {
                    unsetVars.addLast(v);
                }
            }
            BranchContext oldBranch = c.saveBranch();
            terms[0].updateCondBranch(c, true);
            boolean oldHasBreakSimple = c.hasBreakSimple;
            c.hasBreakSimple = false;
            boolean oldHasBreakDeep = c.hasBreakDeep;
            c.hasBreakDeep = false;
            boolean oldHasContinueSimple = c.hasContinueSimple;
            c.hasContinueSimple = false;
            boolean oldHasContinueDeep = c.hasContinueDeep;
            c.hasContinueDeep = false;
            c.isConditional = true;
            terms[1].processPass1(c);
            Enumeration en2 = unsetVars.elements();
            while (en2.hasMoreElements()) {
                ((VariableDefinition) en2.nextElement()).setUnassigned(true);
            }
            oldBranch = c.swapBranch(oldBranch);
            terms[0].updateCondBranch(c, false);
            if (terms[2].notEmpty()) {
                boolean hasBreakOrContinue1 = c.hasBreakSimple
                        || c.hasBreakDeep || c.hasContinueSimple
                        || c.hasContinueDeep;
                oldHasContinueSimple |= c.hasContinueSimple;
                c.hasContinueSimple = false;
                oldHasContinueDeep |= c.hasContinueDeep;
                c.hasContinueDeep = false;
                oldHasBreakSimple |= c.hasBreakSimple;
                c.hasBreakSimple = false;
                oldHasBreakDeep |= c.hasBreakDeep;
                c.hasBreakDeep = false;
                c.isConditional = true;
                terms[2].processPass1(c);
                if (!c.hasBreakSimple && !c.hasBreakDeep
                        && !c.hasContinueSimple && !c.hasContinueDeep
                        && terms[2].hasTailReturnOrThrow()) {
                    c.swapBranch(oldBranch);
                } else if (hasBreakOrContinue1
                        || !terms[1].hasTailReturnOrThrow()) {
                    c.intersectBranch(oldBranch);
                }
            } else if (c.hasBreakSimple || c.hasBreakDeep
                    || c.hasContinueSimple || c.hasContinueDeep
                    || !terms[1].hasTailReturnOrThrow()) {
                c.intersectBranch(oldBranch);
            }
            c.hasContinueSimple |= oldHasContinueSimple;
            c.hasContinueDeep |= oldHasContinueDeep;
            c.hasBreakSimple |= oldHasBreakSimple;
            c.hasBreakDeep |= oldHasBreakDeep;
        }
    }

    private static InstanceOf unwrapPatternInstanceOf(Term t) {
        Term cur = t;
        while (cur instanceof ParenExpression) {
            cur = ((ParenExpression) cur).terms[0];
        }
        if (cur instanceof InstanceOf) {
            InstanceOf iof = (InstanceOf) cur;
            if (iof.getBindingName() != null
                    || iof.getRecordPattern() != null) {
                return iof;
            }
        }
        return null;
    }

    private static int nextRecordPatternTmpId = 0;

    private static String recordTypeKey(Term type) {
        if (type instanceof ClassOrIfaceType) {
            Term nameTerm = ((ClassOrIfaceType) type).getNameTerm();
            String full = nameTerm != null ? nameTerm.dottedName() : null;
            if (full == null) return null;
            int dot = full.lastIndexOf('.');
            return dot < 0 ? full : full.substring(dot + 1);
        }
        return null;
    }

    private void prependPatternBinding(InstanceOf iof) {
        if (iof.getRecordPattern() != null) {
            // Slice 16: destructure record pattern bindings into the
            // then-branch. Component types come from the record's
            // declared accessors via RecordSynthesis.componentsByName.
            Term destructure = expandRecordPattern(iof.getOperand(),
                    iof.getRecordPattern(), terms[1]);
            terms[1] = new Block(destructure);
            return;
        }
        String name = iof.getBindingName();
        Term typeTerm = iof.getTypeTerm();
        Term dimsTerm = iof.getDimsTerm();
        Term operand = iof.getOperand();
        Term castType = dimsTerm.notEmpty()
                ? new TypeWithDims(typeTerm, dimsTerm) : typeTerm;
        Term cast = new CastExpression(castType, operand);
        Term varDeclr = new VariableDeclarator(
                new VariableIdentifier(new LexTerm(LexTerm.ID, name)),
                Empty.newTerm(), cast);
        Term decl = new ExprStatement(new LocalVariableDecl(castType,
                varDeclr));
        terms[1] = new Block(new Seq(decl, terms[1]));
    }

    private static Term expandRecordPattern(Term operand, RecordPattern rp,
            Term innerBody) {
        String tmpName = "$jcgoRP$" + (nextRecordPatternTmpId++);
        Term castExpr = new CastExpression(rp.getType(), operand);
        Term tmpDecl = new ExprStatement(new LocalVariableDecl(rp.getType(),
                new VariableDeclarator(
                        new VariableIdentifier(
                                new LexTerm(LexTerm.ID, tmpName)),
                        Empty.newTerm(), castExpr)));

        String typeName = recordTypeKey(rp.getType());
        Object[] info = typeName != null
                ? (Object[]) RecordSynthesis.componentsByName.get(typeName)
                : null;
        if (info == null) {
            // Defensive: should have been populated by record decl.
            return new Seq(tmpDecl, innerBody);
        }

        Term result = innerBody;
        ObjVector comps = rp.getComponents();
        for (int i = comps.size() - 1; i >= 0; i--) {
            RecordPattern.Component comp = (RecordPattern.Component) comps
                    .elementAt(i);
            String compName = (String) info[i * 2];
            Term compType = (Term) info[i * 2 + 1];
            Term receiver = new Expression(new QualifiedName(
                    new LexTerm(LexTerm.ID, tmpName), Empty.newTerm()));
            Term accessor = new MethodInvocation(receiver,
                    new LexTerm(LexTerm.ID, compName), Empty.newTerm());
            if (comp.isNested()) {
                result = expandRecordPattern(accessor, comp.nested, result);
            } else {
                Term bindingType = comp.bindingType != null
                        ? comp.bindingType : compType;
                Term bindingDeclr = new VariableDeclarator(
                        new VariableIdentifier(
                                new LexTerm(LexTerm.ID, comp.binding)),
                        Empty.newTerm(), accessor);
                Term bindingDecl = new ExprStatement(new LocalVariableDecl(
                        bindingType, bindingDeclr));
                result = new Seq(bindingDecl, result);
            }
        }
        return new Seq(tmpDecl, result);
    }

    int tokenCount() {
        return isAssertion ? 1 : constVal0 != null ? terms[constVal0
                .isNonZero() ? 1 : 2].tokenCount() : terms[0].tokenCount()
                + terms[1].tokenCount() + terms[2].tokenCount() + 1;
    }

    boolean hasTailReturnOrThrow() {
        return constVal0 != null ? terms[constVal0.isNonZero() ? 1 : 2]
                .hasTailReturnOrThrow() : terms[1].hasTailReturnOrThrow()
                && terms[2].hasTailReturnOrThrow();
    }

    boolean isReturnAtEnd(boolean allowBreakThrow) {
        return constVal0 != null ? terms[constVal0.isNonZero() ? 1 : 2]
                .isReturnAtEnd(allowBreakThrow) : terms[1]
                .isReturnAtEnd(allowBreakThrow)
                && terms[2].isReturnAtEnd(allowBreakThrow);
    }

    void allocRcvr(int[] curRcvrs) {
        if (constVal0 == null) {
            terms[0].allocRcvr(curRcvrs);
        }
    }

    void discoverObjLeaks() {
        if (constVal0 != null) {
            terms[constVal0.isNonZero() ? 1 : 2].discoverObjLeaks();
        } else {
            terms[0].discoverObjLeaks();
            terms[1].discoverObjLeaks();
            terms[2].discoverObjLeaks();
        }
    }

    void writeStackObjs(OutputContext oc, Term scopeTerm) {
        if (constVal0 != null) {
            terms[constVal0.isNonZero() ? 1 : 2].writeStackObjs(oc, scopeTerm);
        } else {
            terms[0].writeStackObjs(oc, scopeTerm);
            terms[1].writeStackObjs(oc, scopeTerm);
            terms[2].writeStackObjs(oc, scopeTerm);
        }
    }

    boolean allowInline(int tokenLimit) {
        return isAssertion
                || (constVal0 != null ? terms[constVal0.isNonZero() ? 1 : 2]
                        .allowInline(tokenLimit) : !terms[2].notEmpty()
                        && (tokenLimit -= terms[0].tokenCount() + 1) >= 0
                        && terms[1].allowInline(tokenLimit));
    }

    void processOutput(OutputContext oc) {
        if (isAssertion) {
            oc.cPrint("\n#ifdef JCGO_ASSERTION\010");
        }
        if (constVal0 != null) {
            if (constVal0.isNonZero()) {
                terms[1].processOutput(oc);
            }
        } else {
            oc.cPrint("if (");
            terms[0].processOutput(oc);
            oc.cPrint(")");
            terms[1].processOutput(oc);
            if (terms[2].notEmpty()) {
                oc.cPrint("else");
            }
        }
        if (isAssertion) {
            oc.cPrint("\n#endif\010");
        }
        if (constVal0 == null || !constVal0.isNonZero()) {
            terms[2].processOutput(oc);
        }
    }

    ExpressionType traceClassInit() {
        if (constVal0 != null) {
            terms[constVal0.isNonZero() ? 1 : 2].traceClassInit();
        } else {
            terms[0].traceClassInit();
            terms[1].traceClassInit();
            terms[2].traceClassInit();
        }
        return null;
    }
}
