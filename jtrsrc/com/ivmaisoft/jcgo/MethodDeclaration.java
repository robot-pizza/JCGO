/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/MethodDeclaration.java --
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
 * Grammar production for a method definition.
 ** 
 * Formats: VoidType ID LPAREN [FormalParamList] RPAREN [Throws] MethodBody
 * PrimitiveType/ClassOrIfaceType [Dims] ID LPAREN [FormalParamList] RPAREN
 * [Dims] [Throws] MethodBody
 */

final class MethodDeclaration extends LexNode {

    private MethodDefinition md;

    MethodDeclaration(Term a, Term b, Term d, Term f, Term g) {
        super(a, Empty.newTerm(), b, d, Empty.newTerm(), f, g);
    }

    MethodDeclaration(Term a, Term b, Term c, Term e, Term g, Term h, Term i) {
        super(a, b, c, e, g, h, i);
    }

    void processPass0(Context c) {
        assertCond(c.currentClass != null);
        c.passZeroMethodDefnTerm = this;
        terms[6].processPass0(c);
        c.passZeroMethodDefnTerm = null;
    }

    void processPass1(Context c) {
        c.typeDims = 0;
        terms[1].processPass1(c);
        terms[4].processPass1(c);
        terms[0].processPass1(c);
        assertCond(c.typeDims == 0
                || c.typeClassDefinition.objectSize() != Type.VOID);
        String id = terms[2].dottedName();
        if ((c.modifiers & (AccModifier.VOLATILE | AccModifier.TRANSIENT)) != 0) {
            fatalError(c, "Illegal modifier specified for method: " + id);
        }
        boolean isInterfaceConcrete = c.currentClass.isInterface()
                && (c.modifiers & (AccModifier.DEFAULT | AccModifier.STATIC
                        | AccModifier.PRIVATE)) != 0;
        if (isInterfaceConcrete) {
            if ((c.modifiers & (AccModifier.STATIC | AccModifier.DEFAULT))
                    != 0
                    && Main.dict.javaVersion < JavaVersion.JLS_80) {
                fatalError(c,
                        "default/static interface method requires -source 8 or higher (got "
                                + JavaVersion.format(Main.dict.javaVersion)
                                + ")");
            }
            if ((c.modifiers & AccModifier.PRIVATE) != 0
                    && Main.dict.javaVersion < JavaVersion.JLS_90) {
                fatalError(c,
                        "private interface method requires -source 9 or higher (got "
                                + JavaVersion.format(Main.dict.javaVersion)
                                + ")");
            }
        }
        if (((c.modifiers & AccModifier.ABSTRACT) != 0
                || (c.currentClass.isInterface() && !isInterfaceConcrete))
                && (c.modifiers & (AccModifier.PRIVATE | AccModifier.STATIC
                        | AccModifier.SYNCHRONIZED | AccModifier.NATIVE
                        | AccModifier.FINAL | AccModifier.STRICT
                        | AccModifier.DEFAULT)) != 0) {
            fatalError(c, "Illegal modifier found for an abstract method: "
                    + id);
        }
        if (c.currentClass.isInterface()
                && (c.modifiers & AccModifier.PROTECTED) != 0) {
            fatalError(c, "An interface method cannot be protected: " + id);
        }
        if ((c.modifiers & AccModifier.STATIC) != 0
                && !c.currentClass.isStaticClass()) {
            fatalError(c, "An inner class cannot have a static method: " + id);
        }
        MethodDefinition md2 = c.currentClass
                .addMethod(md = new MethodDefinition(c, id, c.modifiers,
                        c.typeClassDefinition.asExprType(c.typeDims), terms[3],
                        terms[5], terms[6]));
        // Slice 50: thread the parser-captured `<T, U extends X>`
        // type-param list onto the MethodDefinition so codegen can
        // serialize it as a JLS method-signature string for
        // reflection.
        ObjVector genericSig = Parser.getGenericSignature(this);
        if (genericSig != null) {
            md.setGenericSignatureData(genericSig);
        }
        // Slice 49: thread the parser-captured declaration-annotation
        // names onto the MethodDefinition so codegen can emit them
        // into the per-method reflection metadata.
        ObjVector annos = Parser.getDeclarationAnnotations(this);
        if (annos != null) {
            md.setAnnotationTypeNames(annos);
        }
        // Slice 86: parallel arg-text list.
        ObjVector annoArgs = Parser.getDeclarationAnnotationArgs(this);
        if (annoArgs != null) {
            md.setAnnotationArgs(annoArgs);
        }
        // TODO #1: when this MethodDeclaration was synthesized for an
        // @interface element with `default V`, pick up the captured V
        // text and thread it onto the MethodDefinition. The string is
        // emitted into the methodsDefault[] reflection table for the
        // annotation type and parsed back into a typed value at runtime.
        String defText = Parser.getAnnotationDefault(this);
        if (defText != null) {
            md.setAnnotationDefaultText(defText);
        }
        // Slice 49 ext: collect per-parameter annotation lists by
        // walking the FormalParamList AST. Each entry is either an
        // ObjVector<String> of annotation type names or null.
        ObjVector paramAnnos = collectParamAnnotations(terms[3]);
        if (paramAnnos != null) {
            md.setParameterAnnotationLists(paramAnnos);
        }
        // TODO #3: parallel walk for arg-text strings.
        ObjVector paramAnnoArgs = collectParamAnnotationArgs(terms[3]);
        if (paramAnnoArgs != null) {
            md.setParameterAnnotationArgsLists(paramAnnoArgs);
        }
        // Slice 50 (pre-erasure retention): if the return type was a
        // single-id type-param erased by slice 45, thread the
        // original name through so the JLS signature can render it
        // as `TT;`. terms[0] is the return-type AST. Slice 50 (inner
        // generic-arg retention): also propagate any captured
        // parameterized args (e.g. `List<T>` → `<TT;>`) for the
        // return-type slot.
        Term returnTypeAst = terms[0];
        if (returnTypeAst instanceof ClassOrIfaceType) {
            Term n = ((ClassOrIfaceType) returnTypeAst).getNameTerm();
            String tvar = Parser.getErasedTypeVarName(n);
            if (tvar != null) {
                md.setReturnTypeVarName(tvar);
            }
            String capturedArgs = Parser.getCapturedGenericArgs(n);
            if (capturedArgs != null) {
                md.setReturnTypeCapturedArgs(capturedArgs);
            }
        }
        if (md2 != null && !md2.isAbstract()) {
            fatalError(c, "Duplicate method definition: " + id);
        }
    }

    /**
     * Slice 49 ext: walk the FormalParamList AST in declaration
     * order, returning an ObjVector indexed by parameter position
     * where each entry is either the parameter's
     * Parser.getParamAnnotations side-channel value or null. Returns
     * null when no parameter on this method has annotations, so the
     * caller can skip the per-method emission entirely.
     */
    private static ObjVector collectParamAnnotations(Term paramList) {
        if (paramList == null || !paramList.notEmpty()) return null;
        ObjVector out = new ObjVector();
        boolean anyAnnos = collectParamAnnotationsInto(paramList, out);
        return anyAnnos ? out : null;
    }

    private static boolean collectParamAnnotationsInto(Term t,
            ObjVector out) {
        if (!t.notEmpty()) return false;
        if (t instanceof FormalParamList) {
            FormalParamList list = (FormalParamList) t;
            boolean a = collectParamAnnotationsInto(list.terms[0], out);
            boolean b = collectParamAnnotationsInto(list.terms[1], out);
            return a || b;
        }
        if (t instanceof FormalParameter) {
            ObjVector annos = Parser.getParamAnnotations(t);
            out.addElement(annos);
            return annos != null && annos.size() > 0;
        }
        return false;
    }

    private static ObjVector collectParamAnnotationArgs(Term paramList) {
        if (paramList == null || !paramList.notEmpty()) return null;
        ObjVector out = new ObjVector();
        boolean anyArgs = collectParamAnnotationArgsInto(paramList, out);
        return anyArgs ? out : null;
    }

    private static boolean collectParamAnnotationArgsInto(Term t,
            ObjVector out) {
        if (!t.notEmpty()) return false;
        if (t instanceof FormalParamList) {
            FormalParamList list = (FormalParamList) t;
            boolean a = collectParamAnnotationArgsInto(list.terms[0], out);
            boolean b = collectParamAnnotationArgsInto(list.terms[1], out);
            return a || b;
        }
        if (t instanceof FormalParameter) {
            ObjVector args = Parser.getParamAnnotationArgs(t);
            out.addElement(args);
            return args != null && args.size() > 0;
        }
        return false;
    }

    MethodDefinition superMethodCall() {
        return md;
    }
}
