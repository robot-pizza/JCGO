/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/FieldDeclaration.java --
 * a part of JCGO translator.
 **
 * Project: JCGO (http://www.ivmaisoft.com/jcgo/)
 * Copyright (C) 2001-2012 Ivan Maidanski <ivmai@mail.ru>
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
 * Grammar production for the start of a class field definition.
 ** 
 * Format: PrimitiveType/ClassOrIfaceType [Dims] VariableDeclarators
 */

final class FieldDeclaration extends LexNode {

    FieldDeclaration(Term a, Term b, Term c) {
        super(a, b, c);
    }

    void processPass0(Context c) {
        assertCond(c.currentClass != null);
        if ((c.modifiers & AccModifier.STATIC) != 0) {
            c.currentClass.setMayContainClinit();
        }
        terms[0].processPass0(c);
        terms[2].processPass0(c);
    }

    void processPass1(Context c) {
        if ((c.modifiers & (AccModifier.SYNCHRONIZED | AccModifier.NATIVE
                | AccModifier.ABSTRACT | AccModifier.STRICT)) != 0) {
            fatalError(c, "Illegal modifier specified for a field");
        }
        if (c.currentClass.isInterface()
                && (c.modifiers & (AccModifier.PRIVATE | AccModifier.PROTECTED
                        | AccModifier.VOLATILE | AccModifier.TRANSIENT)) != 0) {
            fatalError(c, "Illegal modifier found for an interface constant");
        }
        if ((c.modifiers & AccModifier.STATIC) != 0
                && (c.modifiers & AccModifier.FINAL) == 0
                && !c.currentClass.isStaticClass()) {
            fatalError(c, "An inner class cannot have static non-final fields");
        }
        c.typeDims = 0;
        terms[1].processPass1(c);
        terms[0].processPass1(c);
        terms[2].processPass1(c);
        // Slice 49: thread the parser-captured declaration-annotation
        // names onto every VariableDefinition produced by this decl.
        // For `@Anno T a, b, c;` the annotation applies to all of a,
        // b, c per JLS.
        ObjVector annos = Parser.getDeclarationAnnotations(this);
        if (annos != null) {
            attachAnnotationsToDeclarators(terms[2], annos);
        }
        // Slice 86: parallel arg-text list.
        ObjVector annoArgs = Parser.getDeclarationAnnotationArgs(this);
        if (annoArgs != null) {
            attachAnnoArgsToDeclarators(terms[2], annoArgs);
        }
        // Slice 50 (pre-erasure retention): if the declared type was
        // a single-id type-var that slice 45 erased (e.g. `T value`
        // becomes `Object value`), thread the original name onto the
        // VariableDefinitions so the field's JLS signature can render
        // it as `TT;`. terms[0] is the field-type AST. Slice 50
        // (inner generic-arg retention): also propagate parameterized
        // args (e.g. `List<T>` → `<TT;>`).
        Term fieldTypeAst = terms[0];
        if (fieldTypeAst instanceof ClassOrIfaceType) {
            Term n = ((ClassOrIfaceType) fieldTypeAst).getNameTerm();
            String tvar = Parser.getErasedTypeVarName(n);
            if (tvar != null) {
                attachTypeVarNameToDeclarators(terms[2], tvar);
            }
            String capturedArgs = Parser.getCapturedGenericArgs(n);
            if (capturedArgs != null) {
                attachCapturedArgsToDeclarators(terms[2], capturedArgs);
            }
        }
    }

    private static void attachAnnotationsToDeclarators(Term t,
            ObjVector annos) {
        if (!t.notEmpty()) return;
        if (t instanceof VariableDeclareList) {
            VariableDeclareList list = (VariableDeclareList) t;
            attachAnnotationsToDeclarators(list.terms[0], annos);
            attachAnnotationsToDeclarators(list.terms[1], annos);
            return;
        }
        if (t instanceof VariableDeclarator) {
            VariableDeclarator vd = (VariableDeclarator) t;
            VariableDefinition v = vd.terms[0].getVariable(false);
            if (v != null) v.setAnnotationTypeNames(annos);
        }
    }

    private static void attachAnnoArgsToDeclarators(Term t,
            ObjVector args) {
        if (!t.notEmpty()) return;
        if (t instanceof VariableDeclareList) {
            VariableDeclareList list = (VariableDeclareList) t;
            attachAnnoArgsToDeclarators(list.terms[0], args);
            attachAnnoArgsToDeclarators(list.terms[1], args);
            return;
        }
        if (t instanceof VariableDeclarator) {
            VariableDeclarator vd = (VariableDeclarator) t;
            VariableDefinition v = vd.terms[0].getVariable(false);
            if (v != null) v.setAnnotationArgs(args);
        }
    }

    private static void attachTypeVarNameToDeclarators(Term t, String tvar) {
        if (!t.notEmpty()) return;
        if (t instanceof VariableDeclareList) {
            VariableDeclareList list = (VariableDeclareList) t;
            attachTypeVarNameToDeclarators(list.terms[0], tvar);
            attachTypeVarNameToDeclarators(list.terms[1], tvar);
            return;
        }
        if (t instanceof VariableDeclarator) {
            VariableDeclarator vd = (VariableDeclarator) t;
            VariableDefinition v = vd.terms[0].getVariable(false);
            if (v != null) v.setFieldTypeVarName(tvar);
        }
    }

    private static void attachCapturedArgsToDeclarators(Term t, String args) {
        if (!t.notEmpty()) return;
        if (t instanceof VariableDeclareList) {
            VariableDeclareList list = (VariableDeclareList) t;
            attachCapturedArgsToDeclarators(list.terms[0], args);
            attachCapturedArgsToDeclarators(list.terms[1], args);
            return;
        }
        if (t instanceof VariableDeclarator) {
            VariableDeclarator vd = (VariableDeclarator) t;
            VariableDefinition v = vd.terms[0].getVariable(false);
            if (v != null) v.setFieldTypeCapturedArgs(args);
        }
    }
}
