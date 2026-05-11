/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/MethodInvocation.java --
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
 * Grammar production for normal and primary method calls.
 ** 
 * Formats: QualifiedName LPAREN [ArgumentList] RPAREN Primary DOT ID LPAREN
 * [ArgumentList] RPAREN Super DOT ID LPAREN [ArgumentList] RPAREN
 */

final class MethodInvocation extends LexNode {

    private ClassDefinition resultClass;

    private ClassDefinition actualClass;

    private boolean isExact;

    private MethodDefinition md;

    private MethodDefinition actualMethod;

    private String resultString;

    private ExpressionType classLiteralValue;

    private int rcvr;

    private ExpressionType reflectedClass;

    private boolean reflectInSuper;

    private String reflectedMethodId;

    private ObjVector reflectedParmSig;

    private boolean forceCheck;

    private LeftBrace noLeaksScope;

    private boolean noStackObjRet;

    private boolean isConditional;

    private String stackObjCode;

    private boolean needsLocalVolatile;

    private boolean isVoidExpr;

    MethodInvocation(Term a, Term c) {
        super(a, Empty.newTerm(), c);
    }

    MethodInvocation(Term a, Term c, Term e) {
        super(a, c, e);
    }

    MethodInvocation(Context c, ClassDefinition resultClass, String id,
            Term exprTerm) {
        super(Empty.newTerm(), Empty.newTerm(), (new Argument(exprTerm, true))
                .setLineInfoFrom(exprTerm));
        assertCond(c.currentClass != null);
        setLineInfoFrom(exprTerm);
        this.resultClass = resultClass;
        processPassOneInner(c, null, resultClass, 0, id);
        if (md != null && !md.isClassMethod()) {
            undefinedMethod(resultClass, md.methodSignature(), c);
        }
    }

    MethodInvocation(Context c, Term t, String id) {
        super(t, Empty.newTerm(), Empty.newTerm());
        assertCond(c.currentClass != null);
        setLineInfoFrom(t);
        resultClass = t.exprType().receiverClass();
        processPassOneInner(c, null, t.actualExprType(), 0, id);
        if (md != null && md.exprType().objectSize() != Type.CLASSINTERFACE) {
            undefinedMethod(resultClass, md.methodSignature(), c);
        }
    }

    void processPass1(Context c) {
        if (resultClass == null) {
            assertCond(c.currentClass != null);
            if ((c.forceVmExc & ClassDefinition.NULL_PTR_EXC) != 0) {
                forceCheck = true;
            }
            ClassDefinition aclass;
            int vecSize;
            String id;
            BranchContext oldBranch = c.saveBranch();
            if (terms[1].notEmpty()) {
                terms[0].processPass1(c);
                // Quirk #2: javac's implicit-cast-at-generic-call.
                // If the receiver is a chained generic-method call
                // whose erased return is Object but whose declared
                // return was a type-var (`E get(int)` on List<E>),
                // substitute and wrap the receiver in a synthesized
                // cast. Without this `attrs.get(0).serialize()`
                // can't resolve `serialize` (because the receiver's
                // static type stays Object).
                Term substituted = trySubstituteChainedGenericReturn(
                        terms[0], c);
                if (substituted != null) {
                    terms[0] = substituted;
                }
                resultClass = terms[0].exprType().receiverClass();
                aclass = null;
                vecSize = 0;
                id = terms[1].dottedName();
            } else {
                ObjVector vec = new ObjVector();
                terms[0].storeDottedName(vec);
                aclass = terms[0].defineClass(c, vec);
                vecSize = vec.size();
                id = (String) vec.elementAt(vecSize - 1);
            }
            // Slice 33: pre-pass1 lambda / method-ref args with their
            // target type set in c.currentVarType. Pre-resolves the
            // method by name+arity on the receiver class; if exactly
            // one candidate matches, formal types from that signature
            // are threaded into each lambda arg's processPass1.
            preProcessLambdaArgs(c, aclass, id);
            if (terms[0].isSafeExpr()) {
                oldBranch = c.swapBranch(oldBranch);
                terms[2].processPass1(c);
                c.unionBranch(oldBranch);
            } else {
                terms[2].processPass1(c);
            }
            processPassOneInner(c, aclass, terms[0].actualExprType(), vecSize,
                    id);
        }
    }

    private void processPassOneInner(Context c, ClassDefinition aclass,
            ExpressionType actualType0, int vecSize, String id) {
        if (vecSize == 0) {
            MethodSignature msig = new MethodSignature(id,
                    terms[2].getSignature());
            if (resultClass.objectSize() < Type.CLASSINTERFACE) {
                fatalError(c, "Primitive type cannot be dereferenced: "
                        + resultClass.name());
                return;
            }
            md = resultClass.matchMethod(msig, c.forClass);
            if (md == null) {
                ClassDefinition altClass = trySecondaryBoundsForName(
                        terms[0].dottedName(), c, msig);
                if (altClass != null) {
                    Term original = terms[0];
                    terms[0] = new CastExpression(
                            new ClassOrIfaceType(altClass), original);
                    terms[0].processPass1(c);
                    resultClass = terms[0].exprType().receiverClass();
                    actualType0 = terms[0].actualExprType();
                    md = resultClass.matchMethod(msig, c.forClass);
                }
            }
            if (md == null) {
                undefinedMethod(resultClass, msig, c);
                return;
            }
            applyArgAutobox(c);
            handleVarargsBundling(c, msig);
            if (terms[0].isSuper(false)) {
                if (md.isAbstract()) {
                    fatalError(c, "Abstract method is called: " + md.id());
                }
                isExact = true;
            }
            actualClass = resultClass;
            actualMethod = md;
            if (!md.isClassMethod() && actualType0.objectSize() != Type.NULLREF) {
                actualClass = actualType0.receiverClass();
                if (actualType0 != actualClass
                        && actualType0.objectSize() == Type.CLASSINTERFACE) {
                    isExact = true;
                }
                if (actualClass != resultClass && md.allowOverride()) {
                    actualClass.define(c.forClass);
                    actualMethod = actualClass.getSameMethod(md);
                    if (actualMethod == null) {
                        actualClass = resultClass;
                        actualMethod = md;
                    }
                }
            }
        } else if (vecSize > 1) {
            if (aclass == null) {
                fatalError(c,
                        "Undefined qualified name: " + terms[0].dottedName());
                resultString = VariableDefinition.UNKNOWN_NAME;
                resultClass = c.currentClass;
                return;
            }
            actualClass = actualType0.receiverClass();
            if (actualType0 != actualClass
                    && actualType0.objectSize() == Type.CLASSINTERFACE) {
                isExact = true;
            }
            MethodSignature msig = new MethodSignature(id,
                    terms[2].getSignature());
            resultClass = aclass;
            md = aclass.matchMethod(msig, c.forClass);
            if (md == null && vecSize >= 2 && terms[0] instanceof QualifiedName) {
                // Slice #10: path-style `a.method(args)` where `a` (or a
                // dotted-field chain like `a.b.c`) is a multi-bound
                // type-var-typed variable / field. Rewrite to
                // `((Bound) <receiverPath>).method(args)`: replace
                // terms[0] with a CastExpression wrapping the receiver
                // path (everything except the trailing method segment)
                // and let the rest of the branch2 + common post-match
                // logic carry on. The state changes (aclass, resultClass,
                // actualType0, actualClass) match what the equivalent
                // 3-arg form would produce, including the
                // actualClass.getSameMethod(md) fallback that updates
                // actualClass back to the interface bound -- this is
                // what causes the secondary bound to be marked used and
                // the VFUNC dispatch path to fire. For vecSize == 2 the
                // receiver path is a single segment (a local variable);
                // for vecSize >= 3 the receiver path is a dotted field
                // chain (e.g. `holder.val`).
                QualifiedName qn = (QualifiedName) terms[0];
                // QualifiedName chain layout: terms[0] is the head path
                // (recursive QualifiedName for multi-segment, or LexTerm
                // for a single segment), terms[1] is the last-segment
                // LexTerm. Drop the last by using terms[0] directly.
                Term receiverPath = qn.terms[0];
                String rcvName = receiverPath.dottedName();
                ClassDefinition altClass = rcvName == null ? null
                        : trySecondaryBoundsForName(rcvName, c, msig);
                if (altClass != null) {
                    Term receiverExpr = new Expression(receiverPath);
                    Term castReceiver = new CastExpression(
                            new ClassOrIfaceType(altClass), receiverExpr);
                    terms[0] = castReceiver;
                    terms[1] = new LexTerm(LexTerm.ID, id);
                    terms[0].processPass1(c);
                    resultClass = terms[0].exprType().receiverClass();
                    actualType0 = terms[0].actualExprType();
                    actualClass = actualType0.receiverClass();
                    aclass = resultClass;
                    md = aclass.matchMethod(msig, c.forClass);
                }
            }
            if (md == null) {
                undefinedMethod(aclass, msig, c);
                return;
            }
            applyArgAutobox(c);
            handleVarargsBundling(c, msig);
            assertCond(actualClass != null);
            actualMethod = md;
            if (actualType0.objectSize() != Type.NULLREF) {
                actualClass.define(c.forClass);
                if (md.allowOverride()
                        && (actualMethod = actualClass.getSameMethod(md)) == null) {
                    actualClass = resultClass;
                    actualMethod = md;
                }
            } else {
                actualClass = aclass;
            }
        } else {
            MethodSignature msig = new MethodSignature(id,
                    terms[2].getSignature());
            resultClass = c.currentClass;
            resultString = This.CNAME;
            while ((md = resultClass.matchMethod(msig, c.forClass)) == null) {
                VariableDefinition outerV = resultClass.outerThisRef();
                if (outerV != null) {
                    outerV.markUsed();
                    resultString = outerV.stringOutput(resultString, 1, false);
                }
                resultClass = resultClass.outerClass();
                if (resultClass == null) {
                    // Slice 3b: try static-imported owners for unqualified
                    // method calls.
                    ClassDefinition siClass = c
                            .resolveStaticImportMethodOwner(id);
                    if (siClass != null) {
                        siClass.define(c.forClass);
                        md = siClass.matchMethod(msig, c.forClass);
                        if (md != null && md.isClassMethod()) {
                            siClass.markUsed();
                            resultClass = siClass;
                            resultString = This.CNAME;
                            break;
                        }
                        md = null;
                    }
                    undefinedMethod(c.currentClass, msig, c);
                    resultClass = c.currentClass;
                    return;
                }
            }
            applyArgAutobox(c);
            handleVarargsBundling(c, msig);
            if (!md.isClassMethod() && c.currentMethod != null
                    && c.currentMethod.isClassMethod()) {
                fatalError(c,
                        "Instance method used in a static context: " + md.id());
            }
            actualClass = resultClass;
            actualMethod = md;
        }
        boolean useMethodBranch = false;
        if (c.currentClass.superClass() != null
                || md.used()
                || !md.definingClass().name().equals(Names.JAVA_LANG_VMCLASS)
                || !md.isExactMatch(true, Names.JAVA_LANG_CLASS, 0,
                        Names.SIGN_ARRAYCLASSOF0X)) {
            if (isExact && !md.isClassMethod()) {
                actualMethod.markUsedThisOnly();
            } else {
                actualMethod.markUsed(actualClass,
                        c.containsAccessedClass(actualMethod.definingClass()));
            }
            if (!c.currentClass.name().equals(
                    Names.JAVAX_SWING_UIDEFAULTS_PROXYLAZYVALUE)) {
                processReflection(c.currentClass, c.forClass);
            }
            VariableDefinition v = md.isClassMethod() ? null : terms[0]
                    .getVariable(false);
            if (!actualMethod.isNative()
                    || !Names
                            .isVMCoreClass(actualMethod.definingClass().name())) {
                if (md.isClassMethod()) {
                    c.addAccessedClass(md.definingClass());
                } else if (!actualClass.isInterface()) {
                    c.addAccessedClass(actualClass);
                } else if (!resultClass.isInterface()) {
                    c.addAccessedClass(resultClass);
                }
                if (isExact || !actualMethod.allowOverride()) {
                    actualMethod.processBranch(c,
                            v == VariableDefinition.THIS_VAR);
                    useMethodBranch = true;
                }
            }
            if (v != null) {
                c.setVarNotNull(v);
            }
            noLeaksScope = c.localScope;
            isConditional = c.isConditional;
        }
        actualMethod.incCallsCount(c.currentMethod);
        actualMethod.setArgsFormalType(terms[2], useMethodBranch ? c : null);
    }

    ExpressionType exprType() {
        assertCond(resultClass != null);
        return md != null ? md.exprType() : Main.dict.classTable[Type.VOID];
    }

    ExpressionType actualExprType() {
        assertCond(resultClass != null);
        return actualMethod != null
                && (isExact || !actualMethod.allowOverride()) ? actualMethod
                .actualExprType() : exprType();
    }

    boolean isNotNull() {
        assertCond(resultClass != null);
        return actualMethod != null
                && (isExact || !actualMethod.allowOverride())
                && actualMethod.isNotNull();
    }

    boolean isSwitchMapAssign(boolean isMethodCall) {
        assertCond(resultClass != null);
        VariableDefinition v = terms[0].getVariable(false);
        return v != null
                && isMethodCall
                && actualMethod != null
                && actualMethod.definingClass().name()
                        .equals(Names.JAVA_LANG_ENUM)
                && !actualMethod.isClassMethod()
                && v.isClassVariable()
                && actualMethod.exprType().objectSize() == Type.INT
                && actualMethod.methodSignature().signatureString()
                        .equals(Names.SIGN_ORDINAL) && v.isFinalVariable();
    }

    MethodDefinition superMethodCall() {
        return terms[0].isSuper(true) ? actualMethod : null;
    }

    String strLiteralValueGuess() {
        assertCond(resultClass != null);
        if (md != null) {
            if (resultClass.name().equals(Names.JAVA_LANG_CLASS)) {
                if (!md.isExactMatch(false, Names.JAVA_LANG_STRING, 0,
                        Names.SIGN_GETNAME))
                    return null;
                ExpressionType exprType0 = terms[0].classLiteralValGuess();
                if (exprType0 == null)
                    return null;
                if (exprType0.signatureDimensions() > 0)
                    return exprType0.getJavaSignature();
                ClassDefinition cd = exprType0.receiverClass();
                return cd != exprType0 || cd.isFinal() ? cd.name() : null;
            }
            if (resultClass.name().equals(Names.JAVA_LANG_STRING)
                    && md.isClassMethod()
                    && actualMethod.id().equals(Names.VALUEOF))
                return decodeFirstArgAsString();
        }
        return null;
    }

    ExpressionType classLiteralValGuess() {
        assertCond(resultClass != null);
        return classLiteralValue != null ? (classLiteralValue.receiverClass()
                .isProxyClass() ? null : classLiteralValue)
                : actualMethod != null ? actualMethod.classLiteralValGuess()
                        : null;
    }

    MethodInvocation getClassNewInstanceCall() {
        assertCond(resultClass != null);
        MethodInvocation mcall = null;
        if (md != null
                && (mcall = actualMethod.getClassNewInstanceCall()) == null
                && (md.definingClass().name().equals(Names.JAVA_LANG_CLASS) ? md
                        .isExactMatch(false, Names.JAVA_LANG_OBJECT, 0,
                                Names.SIGN_NEWINSTANCE)
                        : actualMethod
                                .definingClass()
                                .name()
                                .equals(Names.GNU_CLASSPATH_SERVICEFACTORY_SERVICEITERATOR)
                                && md.isExactMatch(false,
                                        Names.JAVA_LANG_OBJECT, 0,
                                        Names.SIGN_NEXT))) {
            mcall = this;
        }
        return mcall;
    }

    MethodSignature getConstructorInstanceSign() {
        assertCond(resultClass != null);
        if (md == null)
            return null;
        MethodSignature msig = actualMethod.getConstructorInstanceSign();
        if (msig != null)
            return msig;
        Term t = terms[2].getArgumentTerm(0);
        if (t == null)
            return null;
        ObjVector parmSig;
        String className = md.definingClass().name();
        if (className.equals(Names.JAVA_LANG_CLASS)
                && (md.isExactMatch(false, Names.JAVA_LANG_REFLECT_CONSTRUCTOR,
                        0, Names.SIGN_GETCONSTRUCTOR) || md.isExactMatch(false,
                        Names.JAVA_LANG_REFLECT_CONSTRUCTOR, 0,
                        Names.SIGN_GETDECLAREDCONSTRUCTOR))) {
            parmSig = new ObjVector();
            if (!t.storeClassLiteralsGuess(parmSig, false)
                    && t.actualExprType().objectSize() != Type.NULLREF)
                return null;
        } else {
            if (!className.equals(Names.JAVA_LANG_REFLECT_CONSTRUCTOR)
                    || !md.isExactMatch(false, Names.JAVA_LANG_OBJECT, 0,
                            Names.SIGN_NEWINSTANCE_CTOR))
                return null;
            if ((msig = terms[0].getConstructorInstanceSign()) != null)
                return msig;
            parmSig = new ObjVector();
            parmSig.addElement(Main.dict.classTable[Type.NULLREF]);
        }
        return new MethodSignature("<init>", parmSig);
    }

    void discoverObjLeaks() {
        assertCond(resultClass != null);
        terms[0].discoverObjLeaks();
        if (actualMethod != null && actualMethod.used()) {
            if (!isExact) {
                while (actualMethod.allowOverride()) {
                    ClassDefinition cd = actualClass.getRealOurClass();
                    MethodDefinition md2;
                    if (cd != actualClass
                            && (md2 = cd.getSameMethod(actualMethod)) != null) {
                        assertCond(md2.used());
                        actualClass = cd;
                        actualMethod = md2;
                        if (!actualMethod.allowOverride())
                            break;
                    }
                    if (!actualMethod.isAbstract())
                        break;
                    ClassDefinition[] cdArr = new ClassDefinition[1];
                    md2 = actualClass.getSingleRealMethodInSubclasses(
                            actualMethod, cdArr);
                    if (md2 == null)
                        break;
                    actualMethod = md2;
                    actualClass = cdArr[0];
                    assertCond(actualClass != null);
                }
            }
            String sigString = actualMethod.methodSignature().signatureString();
            if (!actualMethod.copyObjLeaksTo(terms[2]) && !isExact
                    && actualMethod.allowOverride()) {
                actualClass.copyObjLeaksInSubclasses(sigString, terms[2]);
            }
            if (actualMethod.hasThisObjLeak(false)
                    || (!isExact && actualMethod.allowOverride() && actualClass
                            .hasThisObjLeakInSubclasses(sigString, false))) {
                terms[0].setObjLeaks(null);
            } else if (actualMethod.isThisStackObjVolatile()
                    || (!isExact && actualMethod.allowOverride() && actualClass
                            .isThisStackObjVltInSubclasses(sigString))) {
                terms[0].setStackObjVolatile();
            }
            if (!actualMethod.allowStackObjRet()
                    || (!isExact && actualMethod.allowOverride() && actualClass
                            .subclassHasMethod(sigString, null))) {
                noStackObjRet = true;
            } else if (!actualMethod.stackObjRetRequired()) {
                Main.dict.stackObjRetCalls.addLast(this);
            }
        }
        terms[2].discoverObjLeaks();
    }

    void setStackObjVolatile() {
        assertCond(resultClass != null);
        if (actualMethod != null && actualMethod.used() && !needsLocalVolatile) {
            needsLocalVolatile = true;
            if (actualMethod.hasThisObjLeak(true)
                    || (!isExact && actualMethod.allowOverride() && actualClass
                            .hasThisObjLeakInSubclasses(actualMethod
                                    .methodSignature().signatureString(), true))) {
                terms[0].setStackObjVolatile();
            }
            terms[2].setStackObjVolatile();
        }
    }

    void setObjLeaks(VariableDefinition v) {
        assertCond(resultClass != null);
        if (actualMethod != null && actualMethod.used()) {
            if (v != VariableDefinition.WRITABLE_ARRAY_VAR
                    && (actualMethod.hasThisObjLeak(true) || (!isExact
                            && actualMethod.allowOverride() && actualClass
                            .hasThisObjLeakInSubclasses(actualMethod
                                    .methodSignature().signatureString(), true)))) {
                terms[0].setObjLeaks(v);
            }
            terms[2].setObjLeaks(v);
            noLeaksScope = VariableDefinition.addSetObjLeaksTerm(noLeaksScope,
                    v, this, isConditional || noStackObjRet);
            if (noLeaksScope == null
                    || v == VariableDefinition.WRITABLE_ARRAY_VAR) {
                actualMethod.setWritableArray();
                if (!isExact && actualMethod.allowOverride()) {
                    actualClass.setWritableArrayRetInSubclasses(actualMethod
                            .methodSignature().signatureString());
                }
            }
        }
    }

    MethodDefinition getNoLeaksScopeMethod() {
        return noLeaksScope != null && !noStackObjRet ? actualMethod : null;
    }

    MethodDefinition stackObjRetMethodCall() {
        assertCond(actualMethod != null
                && (noLeaksScope == null || noStackObjRet));
        return actualMethod;
    }

    int tokenCount() {
        return terms[0].tokenCount() + terms[2].tokenCount()
                + (terms[0].isSafeExpr() ? 1 : 2);
    }

    void allocRcvr(int[] curRcvrs) {
        if (actualMethod != null && actualMethod.used()) {
            Term t0 = terms[0];
            Term t2 = terms[2];
            int[] curRcvrs1 = OutputContext.copyRcvrs(curRcvrs);
            t0.allocRcvr(curRcvrs);
            VariableDefinition v;
            if (!actualMethod.isClassMethod()
                    && ((t0.isSafeExpr() ? !t0.isNotNull()
                            && ((v = t0.getVariable(true)) == null || !v
                                    .isLocalOrParam()) && !t0.isImmutable()
                            : actualMethod.isAbstract()
                                    || !t2.isSafeExpr()
                                    || !t0.isNotNull()
                                    || t2.isFieldAccessed(null)
                                    || (!t2.isImmutable() && t0
                                            .isAnyLocalVarChanged(null))
                                    || (!isExact
                                            && actualMethod.allowOverride() && actualClass
                                            .subclassHasMethod(actualMethod
                                                    .methodSignature()
                                                    .signatureString(), null))) || (!t2
                            .isSafeWithThrow() && !t0.isImmutable() && (t0
                            .isFieldAccessed(null) || t2
                            .isAnyLocalVarChanged(t0))))) {
                rcvr = ++curRcvrs1[Type.NULLREF];
            }
            if (!md.isClassMethod() && t2.notEmpty() && !t0.isNotNull()) {
                int[] curRcvrs2 = OutputContext.copyRcvrs(curRcvrs1);
                t2.markParamRcvr(-1, curRcvrs2);
                t2.allocParamRcvr(curRcvrs1,
                        OutputContext.copyRcvrs(curRcvrs1), curRcvrs2);
            } else {
                t2.allocRcvr(curRcvrs1);
            }
            OutputContext.joinRcvrs(curRcvrs, curRcvrs1);
        }
    }

    String writeStackObjDefn(OutputContext oc, boolean needsLocalVolatile) {
        assertCond(actualMethod != null);
        return actualMethod.writeStackObjDefn(oc, needsLocalVolatile);
    }

    void writeStackObjs(OutputContext oc, Term scopeTerm) {
        if (actualMethod != null && actualMethod.used()) {
            terms[0].writeStackObjs(oc, scopeTerm);
            terms[2].writeStackObjs(oc, scopeTerm);
            if (noLeaksScope == scopeTerm && !noStackObjRet) {
                assertCond(scopeTerm != null);
                stackObjCode = actualMethod.writeStackObjDefn(oc,
                        needsLocalVolatile);
            }
        }
    }

    void writeStackObjTrigClinit(OutputContext oc) {
        assertCond(actualMethod != null);
        actualMethod.writeStackObjTrigClinit(oc);
    }

    ExpressionType writeStackObjRetCode(OutputContext oc) {
        assertCond(actualMethod != null
                && (noLeaksScope == null || noStackObjRet));
        stackObjCode = MethodDefinition.STACKOBJ_RETNAME;
        return actualMethod.writeStackObjRetCode(oc);
    }

    boolean isAtomary() {
        return true;
    }

    void setVoidExpression() {
        isVoidExpr = true;
    }

    void processOutput(OutputContext oc) {
        assertCond(resultClass != null);
        if (isVoidExpr
                && md != null
                && !md.isClassMethod()
                && md.exprType().objectSize() != Type.VOID
                && !terms[0].isSuper(false)
                && (!actualMethod.used() || (!isExact && !actualClass
                        .hasRealInstances()))) {
            oc.cPrint("(");
            oc.cPrint(Type.cName[Type.VOID]);
            oc.cPrint(")");
        }
        oc.cPrint("(");
        String rcvrStr = null;
        if (rcvr > 0) {
            rcvrStr = OutputContext.getRcvrName(rcvr, Type.CLASSINTERFACE);
            oc.cPrint(rcvrStr);
            oc.cPrint("= (");
            oc.cPrint(Type.cName[Type.CLASSINTERFACE]);
            oc.cPrint(")");
            ExpressionType oldAssignmentRightType = oc.assignmentRightType;
            oc.assignmentRightType = null;
            terms[0].atomaryOutput(oc);
            oc.assignmentRightType = oldAssignmentRightType;
            oc.cPrint(", ");
        }
        boolean isSpec = false;
        boolean rightParenNeeded = false;
        String primaryStr = null;
        if (md == null || md.isClassMethod() || terms[0].isSuper(false)) {
            if (md != null && md.used()) {
                terms[2].produceRcvr(oc);
            }
            if (!terms[0].isSafeExpr()) {
                oc.cPrint("(");
                oc.cPrint(Type.cName[Type.VOID]);
                oc.cPrint(")");
                if (rcvrStr != null) {
                    oc.cPrint(rcvrStr);
                } else {
                    terms[0].atomaryOutput(oc);
                }
                oc.cPrint(", ");
            }
            if (md != null) {
                if (md.used()) {
                    oc.cPrint(stackObjCode != null ? md
                            .stackObjRetRoutineCName() : md.routineCName());
                } else {
                    assertCond(md.definingClass().name()
                            .equals(Names.JAVA_LANG_VMCLASS)
                            && md.isExactMatch(true, Names.JAVA_LANG_CLASS, 0,
                                    Names.SIGN_ARRAYCLASSOF0X));
                }
            } else {
                oc.cPrint(MethodDefinition.UNKNOWN_NAME);
            }
            Main.dict.normalCalls++;
            isSpec = true;
        } else {
            if (actualMethod.used() && actualClass.hasRealInstances()) {
                terms[2].produceRcvr(oc);
            }
            rightParenNeeded = actualMethod
                    .writeMethodCall(
                            oc,
                            isExact ? null : actualClass,
                            (rcvrStr != null || actualClass != resultClass ? "("
                                    + actualClass.castName() + ")"
                                    : "")
                                    + (rcvrStr != null ? rcvrStr
                                            : resultString != null ? resultString
                                                    : actualClass == resultClass
                                                            || terms[0]
                                                                    .isAtomary() ? (primaryStr = terms[0]
                                                            .stringOutput())
                                                            : "("
                                                                    + (primaryStr = terms[0]
                                                                            .stringOutput())
                                                                    + ")"),
                            terms[0].isNotNull() ? 1 : forceCheck ? -1 : 0,
                            stackObjCode != null, md.exprType());
        }
        if (isSpec || (actualMethod.used() && actualClass.hasRealInstances())) {
            oc.cPrint("(");
            if (md != null && !md.isClassMethod()) {
                oc.cPrint("\010 ");
                ClassDefinition cd = actualMethod.definingClass();
                if (isSpec || rcvrStr != null || cd != resultClass) {
                    oc.cPrint("(");
                    oc.cPrint(cd.castName());
                    oc.cPrint(")");
                }
                if (resultString != null) {
                    oc.cPrint(resultString);
                } else if (rcvrStr != null) {
                    oc.cPrint(rcvrStr);
                } else if (primaryStr != null) {
                    if (cd == resultClass || terms[0].isAtomary()) {
                        oc.cPrint(primaryStr);
                    } else {
                        oc.cPrint("(");
                        oc.cPrint(primaryStr);
                        oc.cPrint(")");
                    }
                } else if (cd != resultClass) {
                    terms[0].atomaryOutput(oc);
                } else {
                    terms[0].processOutput(oc);
                }
                oc.parameterOutputAsArg(terms[2]);
            } else if (md == null || md.used()) {
                if (terms[2].notEmpty()) {
                    oc.cPrint("\010 ");
                }
                oc.cPrint(OutputContext
                        .paramStringOutputNoComma(terms[2], true));
            } else {
                oc.cPrint("(");
                oc.cPrint(Type.cName[Type.VOID]);
                oc.cPrint(")0");
                terms[2].getTermAt(0).parameterOutput(oc, true, Type.NULLREF);
            }
            if (stackObjCode != null) {
                assertCond(md != null);
                if (!md.isClassMethod() || terms[2].notEmpty()) {
                    oc.cPrint(", ");
                }
                oc.cPrint(stackObjCode);
            }
            oc.cPrint(")");
        }
        if (rightParenNeeded) {
            oc.cPrint(")");
        }
        oc.cPrint(")");
    }

    private void processReflection(ClassDefinition currentClass,
            ClassDefinition forClass) {
        if (!md.isPublic())
            return;
        String className = md.definingClass().name();
        if (className.equals(Names.JAVA_LANG_REFLECT_PROXY)
                && (md.isExactMatch(true, Names.JAVA_LANG_CLASS, 0,
                        Names.SIGN_GETPROXYCLASS) || md.isExactMatch(true,
                        Names.JAVA_LANG_OBJECT, 0, Names.SIGN_NEWPROXYINSTANCE))) {
            Term t = terms[2].getArgumentTerm(1);
            if (t != null) {
                ObjVector parmSig = new ObjVector();
                if (t.storeClassLiteralsGuess(parmSig, false)) {
                    classLiteralValue = Main.dict.addProxyClass(parmSig,
                            forClass);
                }
            }
            return;
        }
        if (md.isExactMatch(false, Names.JAVA_LANG_CLASS, 0,
                Names.SIGN_GETCLASS)) {
            ExpressionType actualType0 = terms[0].actualExprType();
            if (isExact || actualType0.receiverClass().superClass() != null) {
                classLiteralValue = actualType0;
            }
            if (actualType0.signatureClass().objectSize() == Type.CLASSINTERFACE
                    && (actualType0.objectSize() == Type.OBJECTARRAY || (!isExact && actualType0
                            .receiverClass().superClass() == null))) {
                Main.dict.get(Names.JAVA_LANG_VMCLASS).markUsed();
            }
            return;
        }
        if (!className.equals(Names.JAVA_LANG_CLASS))
            return;
        if (md.isExactMatch(false, Names.JAVA_LANG_STRING, 0,
                Names.SIGN_GETNAME)) {
            Main.dict
                    .addGetNameClass(terms[0].classLiteralValGuess(), forClass);
            return;
        }
        boolean isForName2 = md.isExactMatch(true, Names.JAVA_LANG_CLASS, 0,
                Names.SIGN_FORNAME_2);
        if (isForName2
                || md.isExactMatch(true, Names.JAVA_LANG_CLASS, 0,
                        Names.SIGN_FORNAME)) {
            ExpressionType exprType = decodeClassForNameArg(
                    decodeFirstArgAsString(), currentClass);
            if (exprType != null) {
                classLiteralValue = exprType;
                ClassDefinition cd = exprType.signatureClass();
                cd.predefineClass(forClass);
                cd.markUsed();
                Term t;
                ConstValue constVal1;
                if (exprType.signatureDimensions() == 0
                        && (!isForName2 || ((t = terms[2].getArgumentTerm(1)) != null && ((constVal1 = t
                                .evaluateConstValue()) == null || constVal1
                                .isNonZero())))) {
                    reflectedClass = exprType;
                }
            }
            return;
        }
        ExpressionType exprType = terms[0].classLiteralValGuess();
        ClassDefinition literalClass;
        boolean isExactType = false;
        if (exprType != null) {
            literalClass = null;
            if (exprType.objectSize() == Type.CLASSINTERFACE) {
                literalClass = exprType.receiverClass();
                literalClass.define(forClass);
            }
            if (literalClass != exprType) {
                isExactType = true;
            }
            if (literalClass != null) {
                if (md.isExactMatch(false, Names.JAVA_LANG_REFLECT_FIELD, 1,
                        Names.SIGN_GETDECLAREDFIELDS)) {
                    reflectedClass = literalClass.reflectAllFields(true);
                    return;
                }
                if (md.isExactMatch(false, Names.JAVA_LANG_REFLECT_FIELD, 1,
                        Names.SIGN_GETFIELDS)) {
                    reflectedClass = literalClass.reflectAllFields(false);
                    reflectInSuper = true;
                    return;
                }
                if (md.isExactMatch(false, Names.JAVA_LANG_REFLECT_CONSTRUCTOR,
                        1, Names.SIGN_GETDECLAREDCONSTRUCTORS)) {
                    reflectConstructors(literalClass, true, null, isExactType);
                    return;
                }
                if (md.isExactMatch(false, Names.JAVA_LANG_REFLECT_CONSTRUCTOR,
                        1, Names.SIGN_GETCONSTRUCTORS)) {
                    reflectConstructors(literalClass, false, null, isExactType);
                    return;
                }
                if (md.isExactMatch(false, Names.JAVA_LANG_REFLECT_CONSTRUCTOR,
                        0, Names.SIGN_GETDECLAREDCONSTRUCTOR)) {
                    reflectConstructors(literalClass, true,
                            decodeArgAsClassArray(0), isExactType);
                    return;
                }
                if (md.isExactMatch(false, Names.JAVA_LANG_REFLECT_CONSTRUCTOR,
                        0, Names.SIGN_GETCONSTRUCTOR)) {
                    reflectConstructors(literalClass, false,
                            decodeArgAsClassArray(0), isExactType);
                    return;
                }
                if (md.isExactMatch(false, Names.JAVA_LANG_REFLECT_METHOD, 1,
                        Names.SIGN_GETDECLAREDMETHODS)) {
                    reflectMethods(literalClass, true, null, null, isExactType);
                    return;
                }
                if (isExactType
                        && md.isExactMatch(false, Names.JAVA_LANG_OBJECT, 0,
                                Names.SIGN_NEWINSTANCE)) {
                    reflectConstructors(literalClass, true, new ObjVector(),
                            true);
                    return;
                }
            } else {
                literalClass = Main.dict.get(Names.JAVA_LANG_OBJECT);
            }
            if (md.isExactMatch(false, Names.JAVA_LANG_REFLECT_METHOD, 1,
                    Names.SIGN_GETMETHODS)) {
                reflectMethods(literalClass, false, null, null, isExactType);
                return;
            }
        } else {
            literalClass = Main.dict.get(Names.JAVA_LANG_OBJECT);
        }
        if (md.isExactMatch(false, Names.JAVA_LANG_REFLECT_FIELD, 0,
                Names.SIGN_GETDECLAREDFIELD)) {
            String name = decodeFirstArgAsString();
            reflectedClass = name != null ? literalClass.reflectField(name,
                    true, isExactType) : literalClass.reflectAllFields(true);
            return;
        }
        if (md.isExactMatch(false, Names.JAVA_LANG_REFLECT_FIELD, 0,
                Names.SIGN_GETFIELD)) {
            String name = decodeFirstArgAsString();
            if (name != null) {
                reflectedClass = literalClass.reflectField(name, false,
                        isExactType);
            } else {
                reflectedClass = literalClass.reflectAllFields(false);
                reflectInSuper = true;
            }
            return;
        }
        if (md.isExactMatch(false, Names.JAVA_LANG_REFLECT_METHOD, 0,
                Names.SIGN_GETDECLAREDMETHOD)) {
            reflectMethods(literalClass, true, decodeFirstArgAsString(),
                    decodeArgAsClassArray(1), isExactType);
            return;
        }
        if (md.isExactMatch(false, Names.JAVA_LANG_REFLECT_METHOD, 0,
                Names.SIGN_GETMETHOD)) {
            reflectMethods(literalClass, false, decodeFirstArgAsString(),
                    decodeArgAsClassArray(1), isExactType);
        }
    }

    private String decodeFirstArgAsString() {
        Term t = terms[2].getArgumentTerm(0);
        return t != null ? t.strLiteralValueGuess() : null;
    }

    private ObjVector decodeArgAsClassArray(int index) {
        Term t = terms[2].getArgumentTerm(index);
        if (t == null)
            return null;
        ObjVector parmSig = new ObjVector();
        return t.storeClassLiteralsGuess(parmSig, false)
                || t.actualExprType().objectSize() == Type.NULLREF ? parmSig
                : null;
    }

    void reflectConstructors(ClassDefinition literalClass) {
        reflectConstructors(literalClass, false, new ObjVector(), false);
    }

    private void reflectConstructors(ClassDefinition literalClass,
            boolean declaredOnly, ObjVector parmSig, boolean isExactType) {
        if (reflectedMethodId == null) {
            literalClass.reflectConstructors(
                    declaredOnly,
                    parmSig != null ? (new MethodSignature("<init>", parmSig))
                            .signatureString() : null, isExactType);
            reflectedClass = isExactType ? literalClass.asExactClassType()
                    : literalClass;
            reflectedMethodId = "<init>";
            reflectedParmSig = parmSig;
            reflectInSuper = !declaredOnly;
        }
    }

    private void reflectMethods(ClassDefinition literalClass,
            boolean declaredOnly, String id, ObjVector parmSig,
            boolean isExactType) {
        if (reflectedMethodId == null) {
            if (id != null && id.length() == 0) {
                id = null;
            }
            literalClass.reflectMethods(id, declaredOnly, parmSig, isExactType);
            reflectedClass = literalClass;
            reflectedMethodId = id != null ? id : "";
            reflectedParmSig = parmSig;
            reflectInSuper = !declaredOnly;
        }
    }

    static ExpressionType decodeClassForNameArg(String str,
            ClassDefinition curClass) {
        if (str == null)
            return null;
        int dims = 0;
        char ch;
        int len = str.length();
        do {
            if (dims >= len)
                return null;
            ch = str.charAt(dims);
            if (ch != '[')
                break;
            dims++;
        } while (true);
        if (dims > 0) {
            if (ch != 'L') {
                if (len - 1 == dims) {
                    for (int type = Type.BOOLEAN; type < Type.VOID; type++) {
                        if (Type.sig[type].charAt(0) == ch)
                            return Main.dict.classTable[type].asExprType(dims);
                    }
                }
                return null;
            }
            if (len - 1 <= dims || str.charAt(len - 1) != ';')
                return null;
            str = str.substring(dims + 1, len - 1);
        }
        if ((curClass != null && !Main.dict.alreadyKnown(str) && (str.indexOf(
                '.', 0) < 0 ? curClass.name().indexOf('.', 0) >= 0 : !str
                .startsWith(curClass.getPackageName() + ".")))
                || !Main.dict.existsOrInner(str))
            return null;
        ClassDefinition cd = Main.dict.get(str);
        return dims > 0 ? cd.asExprType(dims) : cd.asExactClassType();
    }

    private void traceReflected() {
        if (classLiteralValue != null) {
            Main.dict.addDynClassToTrace(classLiteralValue.signatureClass());
        }
        if (reflectedClass != null) {
            ClassDefinition cd = reflectedClass.signatureClass();
            MethodDefinition md2;
            if ((md2 = Main.dict.curHelperForMethod) != null
                    && md2.isClassMethod() && md2.id().equals("initIDs")) {
                cd.classTraceClassInit(true);
            } else if (reflectedMethodId != null) {
                if (reflectedMethodId.equals("<init>")) {
                    cd.traceReflectedConstructor(
                            !reflectInSuper,
                            reflectedParmSig != null ? (new MethodSignature(
                                    "<init>", reflectedParmSig))
                                    .signatureString() : null,
                            reflectedClass != cd);
                } else {
                    cd.traceReflectedMethod(
                            reflectedMethodId.length() > 0 ? reflectedMethodId
                                    : null, !reflectInSuper, reflectedParmSig);
                }
            } else {
                cd.classTraceClassInit(Main.dict.classInitWeakDepend);
                if (reflectInSuper) {
                    cd.classTraceForSupers();
                }
            }
        } else if (md != null
                && md.definingClass().name().equals(Names.JAVA_LANG_CLASS)
                && md.isExactMatch(false, Names.JAVA_LANG_OBJECT, 0,
                        Names.SIGN_NEWINSTANCE)) {
            String ourClassName = Main.dict.curTraceInfo.getDefiningClassName();
            if (!ourClassName
                    .equals(Names.GNU_CLASSPATH_SERVICEPROVIDERLOADINGACTION)
                    && !ourClassName.equals(Names.JAVA_UTIL_LOGGING_LOGMANAGER)) {
                Main.dict.curTraceInfo.setUsesDynClasses();
            }
        }
    }

    ExpressionType traceClassInit() {
        assertCond(resultClass != null);
        if (actualMethod == null)
            return null;
        ExpressionType curTraceType0 = terms[0].traceClassInit();
        ClassDefinition curClass = actualClass;
        MethodDefinition curMethod = actualMethod;
        if (curTraceType0 != null && !actualMethod.isClassMethod()) {
            if (curTraceType0.objectSize() == Type.NULLREF)
                return actualMethod.exprType().objectSize() >= Type.CLASSINTERFACE ? curTraceType0
                        : null;
            curClass = curTraceType0.receiverClass();
            if (curClass != actualClass) {
                if (actualClass.isAssignableFrom(curClass, 0, null)) {
                    if (actualMethod.allowOverride()
                            && ((curMethod = curClass
                                    .getSameMethod(actualMethod)) == null || !curMethod
                                    .used())) {
                        curClass = actualClass;
                        curMethod = actualMethod;
                    }
                } else {
                    curClass = actualClass;
                }
            }
        }
        terms[2].traceClassInit();
        ObjVector parmTraceSig = null;
        if (terms[2].notEmpty()) {
            parmTraceSig = new ObjVector();
            terms[2].getTraceSignature(parmTraceSig);
        }
        curTraceType0 = curMethod
                .methodTraceClassInit(
                        false,
                        isExact
                                || (curTraceType0 != null
                                        && curTraceType0 != curClass
                                        && curTraceType0.signatureClass() == curClass && curTraceType0
                                        .signatureDimensions() == 0) ? curClass
                                .asExactClassType() : curClass, parmTraceSig);
        if (actualMethod.exprType() == curTraceType0) {
            curTraceType0 = null;
        }
        traceReflected();
        return curTraceType0;
    }

    // === Varargs call-site bundling (slice 2b, JLS 15.12.4.2) =========

    /**
     * Slice 33: pre-pass1 each lambda / method-reference argument with
     * its target functional-interface type plumbed through
     * c.currentVarType. The target type comes from the unique method
     * on the receiver class with matching name and arity. If the
     * lookup is ambiguous (overloads with the same name+arity) the
     * lambda gets no target and pass1 will fall back to its existing
     * "lambda needs an explicit functional-interface target" path.
     */
    private void preProcessLambdaArgs(Context c, ClassDefinition aclass,
            String id) {
        if (id == null || !hasLambdaArg()) return;
        ClassDefinition rcv = aclass != null ? aclass : resultClass;
        if (rcv == null) {
            // Unqualified call inside the same class — try currentClass.
            rcv = c.currentClass;
        }
        if (rcv == null) return;
        rcv.define(c.forClass);
        ObjVector args = new ObjVector();
        flattenArgsInto(terms[2], args);
        int argCount = args.size();
        MethodDefinition uniqueMd = findUniqueMdByNameArity(rcv, id, argCount);
        if (uniqueMd == null) {
            // P4: try narrowing by lambda-shape (FI param at each
            // lambda arg's position).
            boolean[] isLam = new boolean[argCount];
            int[] paramCt = new int[argCount];
            for (int i = 0; i < argCount; i++) {
                Term head = (Term) args.elementAt(i);
                if (!(head instanceof Argument)) continue;
                Term inner = ((Argument) head).terms[0];
                if (inner instanceof LambdaExpression) {
                    isLam[i] = true;
                    paramCt[i] = countLambdaParams(
                            ((LambdaExpression) inner).getParams());
                } else if (inner instanceof MethodReference) {
                    isLam[i] = true;
                    // Method-ref arity isn't statically known here;
                    // any arity matches.
                    paramCt[i] = -1;
                }
            }
            uniqueMd = narrowByLambdaShapeRespectingArity(rcv, id,
                    argCount, isLam, paramCt, c.forClass);
            if (uniqueMd == null) return;
        }
        MethodSignature uniqueFormals = uniqueMd.methodSignature();
        for (int i = 0; i < argCount; i++) {
            Term head = (Term) args.elementAt(i);
            if (!(head instanceof Argument)) continue;
            Term inner = ((Argument) head).terms[0];
            if (!(inner instanceof LambdaExpression)
                    && !(inner instanceof MethodReference)) continue;
            ExpressionType oldVar = c.currentVarType;
            String oldArgs = c.currentVarTypeArgsJls;
            c.currentVarType = uniqueFormals.paramAt(i);
            c.currentVarTypeArgsJls =
                    capturedArgsForFormal(uniqueMd.getParamList(), i);
            inner.processPass1(c);
            c.currentVarType = oldVar;
            c.currentVarTypeArgsJls = oldArgs;
        }
    }

    // Quirk #6: walks the FormalParamList AST to find the i-th
    // parameter's type and reads its parser-captured generic args
    // (slice 50). Returns null when the parameter has no generic args
    // or the AST shape isn't recognized.
    static String capturedArgsForFormal(Term paramList, int index) {
        if (paramList == null) return null;
        ObjVector params = new ObjVector();
        collectFormalParams(paramList, params);
        if (index < 0 || index >= params.size()) return null;
        Term fp = (Term) params.elementAt(index);
        if (!(fp instanceof FormalParameter)) return null;
        Term type = ((FormalParameter) fp).terms[1];
        if (!(type instanceof ClassOrIfaceType)) return null;
        Term name = ((ClassOrIfaceType) type).getNameTerm();
        return Parser.getCapturedGenericArgs(name);
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

    // Quirk #2: when `receiver` is a chained MethodInvocation whose
    // resolved method returns Object and the receiver's variable was
    // declared with a single-arg parser-captured generic type (e.g.
    // `attrs` declared as `List<Attribute>`), wrap `receiver` in a
    // CastExpression to that arg. Returns the wrapped term, or null
    // when no substitution applies.
    //
    // classpath-0.93 predates generics, so List.get / Iterator.next
    // are declared as `Object get(int)` without a type-var return.
    // The heuristic is therefore receiver-side: if the receiver's
    // declaration captured exactly one generic arg, the chained
    // generic-method return is taken to be that arg. Covers
    // Collection<E>, List<E>, Set<E>, Iterator<E>, Iterable<E>,
    // Queue<E>, Deque<E>, Stack<E>. Map<K, V> (two captured args)
    // deliberately doesn't fire — picking K vs V would be a guess,
    // and a wrong silent substitution is worse than the explicit-cast
    // workaround. When the slice-50 returnTypeVarName side channel
    // *is* set (`<T> T foo()` in user code), the type-var-aware path
    // takes precedence and Map-style two-arg cases also work
    // correctly.
    private static Term trySubstituteChainedGenericReturn(Term receiver,
            Context c) {
        if (!(receiver instanceof MethodInvocation)) return null;
        MethodInvocation inner = (MethodInvocation) receiver;
        MethodDefinition innerMd = inner.actualMethod;
        if (innerMd == null) return null;
        // Inner's return must currently erase to java.lang.Object.
        ExpressionType retEt = innerMd.exprType();
        if (retEt == null) return null;
        ClassDefinition retCls = retEt.signatureClass();
        if (retCls == null
                || !Names.JAVA_LANG_OBJECT.equals(retCls.name())
                || retEt.signatureDimensions() != 0) {
            return null;
        }
        // Receiver of the inner call. Local var, parameter, or
        // field — `allowInstance=true` lets PrimaryFieldAccess return
        // a non-static field's VariableDefinition (we only need the
        // captured-args side channel, not the runtime value).
        VariableDefinition rcvVar = inner.terms[0].getVariable(true);
        if (rcvVar == null) return null;
        String capturedJls = rcvVar.getFieldTypeCapturedArgs();
        if (capturedJls == null) return null;
        int argCount = countTopLevelArgs(capturedJls);
        ClassDefinition substCls = null;
        String tvar = innerMd.getReturnTypeVarName();
        if (tvar != null) {
            // Slice-50-retained return type-var (`<T> T foo()` user
            // code). Look up T's index in the defining class's
            // type-param list; substitute the matching captured arg.
            ClassDefinition definingCls = innerMd.definingClass();
            if (definingCls == null) return null;
            String[] typeParamNames = definingCls.getGenericTypeParamNames();
            if (typeParamNames == null
                    || typeParamNames.length != argCount) {
                return null;
            }
            int tvarIndex = -1;
            for (int i = 0; i < typeParamNames.length; i++) {
                if (tvar.equals(typeParamNames[i])) {
                    tvarIndex = i; break;
                }
            }
            if (tvarIndex < 0) return null;
            substCls = pickJlsArgClass(capturedJls, tvarIndex, c);
        } else if (argCount == 1) {
            // Pre-generics classpath fallback. Single captured arg
            // is unambiguous — substitute it.
            substCls = pickJlsArgClass(capturedJls, 0, c);
        }
        if (substCls == null) return null;
        // Same-class cast (Object → Object) is meaningless.
        if (Names.JAVA_LANG_OBJECT.equals(substCls.name())) return null;
        Term castType = new ClassOrIfaceType(qualifiedNameOf(substCls.name()));
        Term cast = new CastExpression(castType, inner);
        cast.setLineInfoFrom(inner);
        cast.processPass1(c);
        return cast;
    }

    private static int countTopLevelArgs(String jls) {
        if (jls == null || jls.length() < 2 || jls.charAt(0) != '<'
                || jls.charAt(jls.length() - 1) != '>') {
            return 0;
        }
        int i = 1;
        int end = jls.length() - 1;
        int count = 0;
        while (i < end) {
            char ch = jls.charAt(i);
            if (ch != 'L') return 0;
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
            if (semi < 0) return 0;
            count++;
            i = semi + 1;
        }
        return count;
    }

    // Extract the index-th top-level arg from a JLS-form string like
    // `<Ljava/lang/String;Ljava/util/List<TT;>;>` and resolve it to a
    // ClassDefinition. Wildcards (`*`), type-var refs (`T...;`) and
    // primitive/array args yield null — substitution only fires for
    // an erased class arg, matching the conservative LambdaSynthesis
    // counterpart.
    private static ClassDefinition pickJlsArgClass(String jls, int index,
            Context c) {
        if (jls == null || jls.length() < 2 || jls.charAt(0) != '<'
                || jls.charAt(jls.length() - 1) != '>') {
            return null;
        }
        int i = 1;
        int end = jls.length() - 1;
        int pos = 0;
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
            if (pos == index) {
                int lt = jls.indexOf('<', i + 1);
                String dotted;
                if (lt >= 0 && lt < semi) {
                    dotted = jls.substring(i + 1, lt).replace('/', '.');
                } else {
                    dotted = jls.substring(i + 1, semi).replace('/', '.');
                }
                ClassDefinition cd = c == null ? null
                        : c.resolveClass(dotted, false, false);
                if (cd == null) {
                    if (!Main.dict.exists(dotted)) return null;
                    cd = Main.dict.get(dotted);
                }
                return cd;
            }
            pos++;
            i = semi + 1;
        }
        return null;
    }

    private static Term qualifiedNameOf(String dotted) {
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

    static MethodDefinition findUniqueMdByNameArity(
            ClassDefinition cls, String name, int arity) {
        java.util.Enumeration en = cls.enumerateMethodSignatures();
        MethodDefinition unique = null;
        while (en.hasMoreElements()) {
            String sig = (String) en.nextElement();
            MethodDefinition md = cls.getMethodNoInheritance(sig);
            if (md == null) continue;
            if (!name.equals(md.id())) continue;
            if (md.methodSignature().paramCount() != arity) continue;
            if (unique != null) return null;
            unique = md;
        }
        return unique;
    }

    // Standards-pass P4: when the simple name+arity lookup is
    // ambiguous, narrow by "for each lambda / method-ref arg, the
    // formal at that arg's position is a functional interface".
    // This picks the right ctor / method for the common case of
    // overloads where the non-FI variants exist (e.g.
    // `X(String, String)` vs `X(String, Runnable)`) — the lambda
    // can only legally target the FI-typed slot.
    //
    // Doesn't help genuinely-ambiguous overloads where two same-arity
    // candidates both have FI-typed slots at the lambda's position
    // (e.g. `X(String, Runnable)` vs `X(String, Supplier<Integer>)`)
    // — those require full lambda-body return-type inference, which
    // remains a deferred deviation noted in TODO.md.
    private static int countLambdaParams(Term paramsTerm) {
        if (paramsTerm == null || !paramsTerm.notEmpty()) return 0;
        if (paramsTerm instanceof Seq) {
            return countLambdaParams(((Seq) paramsTerm).terms[0])
                    + countLambdaParams(((Seq) paramsTerm).terms[1]);
        }
        return 1;
    }

    // Variant of narrowByLambdaShape that uses -1 as "any arity"
    // for method-references, where the static SAM arity isn't
    // syntactically known without resolution. Falls through to the
    // standard FI check otherwise.
    static MethodDefinition narrowByLambdaShapeRespectingArity(
            ClassDefinition cls, String name, int arity,
            boolean[] argIsLambda, int[] argLambdaParamCount,
            ClassDefinition forClass) {
        java.util.Enumeration en = cls.enumerateMethodSignatures();
        MethodDefinition match = null;
        while (en.hasMoreElements()) {
            String sig = (String) en.nextElement();
            MethodDefinition md = cls.getMethodNoInheritance(sig);
            if (md == null) continue;
            if (!name.equals(md.id())) continue;
            MethodSignature msig = md.methodSignature();
            if (msig.paramCount() != arity) continue;
            boolean ok = true;
            for (int i = 0; i < arity && ok; i++) {
                if (!argIsLambda[i]) continue;
                ExpressionType pt = msig.paramAt(i);
                ClassDefinition pc = pt.signatureClass();
                if (pc == null || !pc.isInterface()
                        || pt.signatureDimensions() != 0) {
                    ok = false;
                    break;
                }
                MethodDefinition sam = LambdaExpression.findSam(pc,
                        forClass);
                if (sam == null) { ok = false; break; }
                if (argLambdaParamCount[i] >= 0
                        && sam.methodSignature().paramCount()
                                != argLambdaParamCount[i]) {
                    ok = false; break;
                }
            }
            if (!ok) continue;
            if (match != null) return null;
            match = md;
        }
        return match;
    }

    static MethodDefinition narrowByLambdaShape(ClassDefinition cls,
            String name, int arity, boolean[] argIsLambda,
            int[] argLambdaParamCount, ClassDefinition forClass) {
        java.util.Enumeration en = cls.enumerateMethodSignatures();
        MethodDefinition match = null;
        while (en.hasMoreElements()) {
            String sig = (String) en.nextElement();
            MethodDefinition md = cls.getMethodNoInheritance(sig);
            if (md == null) continue;
            if (!name.equals(md.id())) continue;
            MethodSignature msig = md.methodSignature();
            if (msig.paramCount() != arity) continue;
            boolean ok = true;
            for (int i = 0; i < arity && ok; i++) {
                if (!argIsLambda[i]) continue;
                ExpressionType pt = msig.paramAt(i);
                ClassDefinition pc = pt.signatureClass();
                if (pc == null || !pc.isInterface()
                        || pt.signatureDimensions() != 0) {
                    ok = false;
                    break;
                }
                MethodDefinition sam = LambdaExpression.findSam(pc,
                        forClass);
                if (sam == null) { ok = false; break; }
                if (sam.methodSignature().paramCount()
                        != argLambdaParamCount[i]) {
                    ok = false; break;
                }
            }
            if (!ok) continue;
            if (match != null) return null;
            match = md;
        }
        return match;
    }

    private boolean hasLambdaArg() {
        ObjVector args = new ObjVector();
        flattenArgsInto(terms[2], args);
        for (int i = 0; i < args.size(); i++) {
            Term head = (Term) args.elementAt(i);
            if (!(head instanceof Argument)) continue;
            Term inner = ((Argument) head).terms[0];
            if (inner instanceof LambdaExpression
                    || inner instanceof MethodReference) {
                return true;
            }
        }
        return false;
    }

    static MethodSignature findUniqueByNameArity(
            ClassDefinition cls, String name, int arity) {
        java.util.Enumeration en = cls.enumerateMethodSignatures();
        MethodSignature unique = null;
        while (en.hasMoreElements()) {
            String sig = (String) en.nextElement();
            MethodDefinition md = cls.getMethodNoInheritance(sig);
            if (md == null) continue;
            if (!name.equals(md.id())) continue;
            MethodSignature msig = md.methodSignature();
            if (msig.paramCount() != arity) continue;
            if (unique != null) return null;  // ambiguous
            unique = msig;
        }
        return unique;
    }

    /**
     * Slice 18b (Java 5): after a successful matchMethod, walk the
     * argument chain and insert autobox/unbox conversions wherever a
     * primitive arg matched a wrapper formal (or vice versa). The
     * relaxed matchOneParam in MethodSignature lets such matches succeed
     * with cost 0x4000; this method makes the conversion concrete.
     *
     * For varargs methods we autobox the fixed-arity prefix only —
     * conversion of the varargs trailing args (or pre-bundled array
     * elements) is left to a follow-up; common JDK calls like
     * println(Object) fall in the fixed-arity prefix.
     */
    private static ClassDefinition trySecondaryBoundsForName(
            String varName, Context c, MethodSignature msig) {
        if (varName == null || c.currentMethod == null) return null;
        VariableDefinition rcv = resolvePathToVarDef(varName, c);
        if (rcv == null) return null;
        String secs = rcv.getMultiBoundSecondaries();
        if (secs == null || secs.length() == 0) return null;
        int start = 0;
        while (start <= secs.length()) {
            int amp = secs.indexOf('&', start);
            String boundName = amp < 0 ? secs.substring(start)
                    : secs.substring(start, amp);
            if (boundName.length() > 0) {
                ClassDefinition cd = resolveSecondaryBound(boundName, c);
                if (cd != null && cd.matchMethod(msig, c.forClass) != null) {
                    return cd;
                }
            }
            if (amp < 0) break;
            start = amp + 1;
        }
        return null;
    }

    /**
     * Resolve a dotted path to the VariableDefinition of its last segment.
     * For a single-id path returns the local variable directly; for a
     * dotted path walks each segment as a field access, threading the
     * receiver type forward. Used so cross-bound retry can find
     * secondaries on a field-access receiver like `h.val.method()`,
     * not just on a single-name local.
     */
    private static VariableDefinition resolvePathToVarDef(String dotted,
            Context c) {
        if (dotted == null || dotted.length() == 0) return null;
        if (dotted.indexOf('.') < 0) {
            return c.currentMethod.getLocalVar(dotted);
        }
        int dot = dotted.indexOf('.');
        String head = dotted.substring(0, dot);
        VariableDefinition v = c.currentMethod.getLocalVar(head);
        if (v == null) return null;
        ExpressionType type = v.exprType();
        int start = dot + 1;
        while (start <= dotted.length()) {
            int next = dotted.indexOf('.', start);
            String seg = next < 0 ? dotted.substring(start)
                    : dotted.substring(start, next);
            if (type == null) return null;
            ClassDefinition cd = type.signatureClass();
            if (cd == null) return null;
            VariableDefinition fld = cd.getField(seg, c.forClass);
            if (fld == null) return null;
            v = fld;
            type = fld.exprType();
            if (next < 0) break;
            start = next + 1;
        }
        return v;
    }

    private static ClassDefinition resolveSecondaryBound(String name,
            Context c) {
        try {
            ClassDefinition cd = c.resolveClass(name, true, false);
            if (cd != null) return cd;
        } catch (RuntimeException e) {
            // ignore
        }
        if (name.indexOf('.') < 0) {
            try {
                return c.resolveClass("java.lang." + name, true, false);
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private void applyArgAutobox(Context c) {
        if (md == null) return;
        if (!c.versionAtLeast(JavaVersion.JLS_50)) return;
        MethodSignature formalMsig = md.methodSignature();
        int n = formalMsig.paramCount();
        boolean isVarArgs = formalMsig.isVarArgs();
        int fixedCount = isVarArgs ? n - 1 : n;
        for (int i = 0; i < fixedCount; i++) {
            ExpressionType formal = formalMsig.paramAt(i);
            Argument arg = findArgumentNode(terms[2], i);
            if (arg == null) continue;
            Term inner = arg.terms[0];
            int srcSize = inner.exprType().objectSize();
            int dstSize = formal.objectSize();
            // Only convert if a primitive↔reference bridge is needed.
            if ((dstSize >= Type.CLASSINTERFACE && Autobox.isPrimitive(srcSize))
                    || (Autobox.isPrimitive(dstSize)
                            && srcSize >= Type.CLASSINTERFACE
                            && Autobox.wrapperPrimitiveFor(
                                    inner.exprType().receiverClass()) >= 0)) {
                Term coerced = Autobox.coerce(c, inner, dstSize);
                if (coerced != inner) {
                    arg.replaceArgTermAndRefresh(coerced);
                }
            }
        }
    }

    private static Argument findArgumentNode(Term t, int index) {
        Term cur = t;
        int i = 0;
        while (cur instanceof ParameterList) {
            if (i == index) {
                Term head = ((ParameterList) cur).terms[0];
                return head instanceof Argument ? (Argument) head : null;
            }
            cur = ((ParameterList) cur).terms[1];
            i++;
        }
        if (cur instanceof Argument && i == index) return (Argument) cur;
        return null;
    }

    private void handleVarargsBundling(Context c, MethodSignature actualMsig) {
        if (md == null) {
            return;
        }
        MethodSignature formalMsig = md.methodSignature();
        if (!formalMsig.isVarArgs()) {
            return;
        }
        int n = formalMsig.paramCount();
        int k = actualMsig.paramCount();
        if (k < n - 1) {
            return;
        }
        boolean needBundle;
        if (k != n) {
            needBundle = true;
        } else {
            ExpressionType lastFormal = formalMsig.paramAt(n - 1);
            ExpressionType lastActual = actualMsig.paramAt(n - 1);
            int costAsArray = MethodSignature.matchOneParam(lastFormal,
                    lastActual, c.forClass);
            needBundle = (costAsArray < 0);
        }
        if (!needBundle) {
            return;
        }
        ExpressionType varargsFormal = formalMsig.paramAt(n - 1);
        ExpressionType elementType = varargsFormal.indirectedType();
        if (elementType == null) {
            return;
        }
        ObjVector args = new ObjVector();
        flattenArgsInto(terms[2], args);
        if (args.size() != k) {
            return;
        }
        // Slice 18c: autobox each bundle element to the varargs element
        // type. Without this, calling `f(Object... xs)` with `(1, 2, 3)`
        // builds `Object[] {1, 2, 3}` whose elements fail the int → Object
        // assignability check at the array initializer.
        if (Main.dict.javaVersion >= JavaVersion.JLS_50) {
            int dstSize = elementType.objectSize();
            for (int i = n - 1; i < k; i++) {
                Term head = (Term) args.elementAt(i);
                if (!(head instanceof Argument)) continue;
                Argument arg = (Argument) head;
                Term inner = arg.terms[0];
                int srcSize = inner.exprType().objectSize();
                if (dstSize >= Type.CLASSINTERFACE
                        && Autobox.isPrimitive(srcSize)) {
                    Term coerced = Autobox.coerce(c, inner, dstSize);
                    if (coerced != inner) {
                        arg.replaceArgTermAndRefresh(coerced);
                    }
                }
            }
        }
        Term elementTypeTerm = exprTypeToTypeTerm(elementType);
        if (elementTypeTerm == null) {
            return;
        }
        Term init = buildArrInit(args, n - 1, k);
        Term anonArr = new AnonymousArray(elementTypeTerm,
                new DimSpec(Empty.newTerm()),
                new ArrayInitializer(init));
        anonArr.processPass1(c);
        Term newArg = new Argument(anonArr);
        newArg.processPass1(c);
        Term tail = newArg;
        for (int i = n - 2; i >= 0; i--) {
            tail = new ParameterList((Term) args.elementAt(i), tail);
        }
        terms[2] = tail;
    }

    static void flattenArgsInto(Term t, ObjVector out) {
        if (!t.notEmpty()) {
            return;
        }
        if (t instanceof ParameterList) {
            ParameterList pl = (ParameterList) t;
            flattenArgsInto(pl.terms[0], out);
            flattenArgsInto(pl.terms[1], out);
        } else if (t instanceof Argument) {
            out.addElement(t);
        }
    }

    private static Term buildArrInit(ObjVector args, int start, int end) {
        if (start >= end) {
            return Empty.newTerm();
        }
        Term first = (Term) args.elementAt(start);
        Term firstInner = ((Argument) first).terms[0];
        Term elem = new ArrElementInit(firstInner);
        if (start == end - 1) {
            return elem;
        }
        Term rest = buildArrInit(args, start + 1, end);
        return new VarInitializers(elem, rest);
    }

    private static Term exprTypeToTypeTerm(ExpressionType et) {
        int dims = et.signatureDimensions();
        ClassDefinition cd = et.signatureClass();
        if (cd == null) {
            return null;
        }
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
}
