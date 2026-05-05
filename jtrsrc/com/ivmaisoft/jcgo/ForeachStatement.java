/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/ForeachStatement.java --
 * a part of JCGO translator.
 **
 * Project: JCGO (http://www.ivmaisoft.com/jcgo/)
 * Copyright (C) 2001-2026 Ivan Maidanski <ivmai@mail.ru>
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
 * Slice 1: parse + version gate only. Desugaring to a classic for-loop is a
 * follow-up slice — until then, processPass1 emits a clear "not yet
 * implemented" error at -source 5+. The negative case (-source 1.4) is fully
 * exercised: foreach in 1.4 sources fails with a version error.
 */

final class ForeachStatement extends LexNode {

    ForeachStatement(Term type, Term varIdent, Term iterableExpr, Term body) {
        super(type, varIdent, iterableExpr, body);
    }

    void processPass1(Context c) {
        if (Main.dict.javaVersion < JavaVersion.JLS_50) {
            fatalError(c, "enhanced for loop requires -source 5 or higher (got "
                    + JavaVersion.format(Main.dict.javaVersion) + ")");
        }
        fatalError(c,
                "enhanced for loop is not yet implemented in this fork "
                + "(parser accepts the syntax; desugaring is a follow-up slice)");
    }

    void processOutput(OutputContext oc) {
        oc.cPrint("/* foreach: not yet implemented */");
    }

    ExpressionType traceClassInit() {
        return null;
    }
}
