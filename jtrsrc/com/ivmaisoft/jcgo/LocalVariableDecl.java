/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/LocalVariableDecl.java --
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
 * Grammar production for a local variable definition.
 ** 
 * Formats: PrimitiveType/TypeWithDims/Expression(QualifiedName)
 * VariableDeclarators AccModifier/Empty PrimitiveType/ClassOrIfaceType [Dims]
 * VariableDeclarators
 */

final class LocalVariableDecl extends LexNode {

    LocalVariableDecl(Term a, Term b) {
        super(Empty.newTerm(), a, Empty.newTerm(), b);
    }

    LocalVariableDecl(Term a, Term b, Term c, Term d) {
        super(a, b, c, d);
    }

    boolean isBlock() {
        return true;
    }

    void processPass1(Context c) {
        if (isVarType(terms[1]) && terms[2] == Empty.term) {
            processVarPass1(c);
            return;
        }
        int oldModifiers = c.modifiers;
        c.modifiers = AccModifier.LOCALVAR;
        terms[0].processPass1(c);
        c.typeDims = 0;
        terms[2].processPass1(c);
        if (terms[1].isName()) {
            c.typeClassDefinition = c.resolveClass(terms[1].dottedName(), true,
                    false);
        } else if (terms[1].isType()) {
            terms[1].processPass1(c);
        } else {
            fatalError(c, "Type is expected");
        }
        terms[3].processPass1(c);
        // Quirk #2: thread the declared type's parser-captured generic
        // args onto each local-var's VariableDefinition. The same
        // fieldTypeCapturedArgs slot is reused (the name is a slice-50
        // misnomer — storage is generic). Read at MethodInvocation
        // chained-call retry to substitute T → concrete type.
        if (terms[1] instanceof ClassOrIfaceType) {
            Term name = ((ClassOrIfaceType) terms[1]).getNameTerm();
            String captured = Parser.getCapturedGenericArgs(name);
            if (captured != null) {
                attachCapturedArgs(terms[3], captured);
            }
        }
        c.modifiers = oldModifiers;
    }

    private static void attachCapturedArgs(Term t, String args) {
        if (!t.notEmpty()) return;
        if (t instanceof VariableDeclareList) {
            VariableDeclareList list = (VariableDeclareList) t;
            attachCapturedArgs(list.terms[0], args);
            attachCapturedArgs(list.terms[1], args);
            return;
        }
        if (t instanceof VariableDeclarator) {
            VariableDeclarator vd = (VariableDeclarator) t;
            VariableDefinition v = vd.terms[0].getVariable(false);
            if (v != null) v.setFieldTypeCapturedArgs(args);
        }
    }

    private static boolean isVarType(Term t) {
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

    private void processVarPass1(Context c) {
        if (!c.versionAtLeast(JavaVersion.JLS_100)) {
            fatalError(c,
                    "var local-variable type inference requires -source 10 or higher (got "
                            + JavaVersion.format(Main.dict.javaVersion) + ")");
        }
        if (!(terms[3] instanceof VariableDeclarator)) {
            fatalError(c, "var requires a single declarator with an initializer");
        }
        VariableDeclarator vd = (VariableDeclarator) terms[3];
        Term init = vd.terms[2];
        if (!init.notEmpty()) {
            fatalError(c, "var requires an initializer");
        }
        int oldModifiers = c.modifiers;
        c.modifiers = AccModifier.LOCALVAR;
        terms[0].processPass1(c);
        init.processPass1(c);
        ExpressionType inferred = init.exprType();
        if (inferred == null || inferred.signatureClass() == null) {
            fatalError(c, "var: cannot infer type from initializer");
        }
        c.typeClassDefinition = inferred.signatureClass();
        c.typeDims = inferred.signatureDimensions();
        terms[3].processPass1(c);
        c.modifiers = oldModifiers;
    }

    void processOutput(OutputContext oc) {
        terms[3].processOutput(oc);
    }

    ExpressionType traceClassInit() {
        terms[3].traceClassInit();
        return null;
    }
}
