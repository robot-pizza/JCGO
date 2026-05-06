/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/SwitchExpression.java --
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
 * Marker AST node for a switch expression (Java 14+, JEP 361). This is a
 * placeholder used between Parser.SwitchExpressionParse and the call-site
 * lifter that rewrites the enclosing LocalVariableDecl into a Block:
 *   T name = switch(d) { ... }
 *      ==>
 *   { T name; switch(d) { ... assigns to name + break ... } }
 *
 * Slice 14b only supports switch expressions as the init of a
 * single-declarator local variable. processPass1 fires fatalError if the
 * lifter didn't reach this node — that happens when the switch expression
 * was used in any other context (method arg, ternary, etc.).
 *
 * terms[0] = discriminant Term (already wrapped as Expression by parser)
 * terms[1] = body chain (Seq of CaseStatementArrow nodes)
 */
final class SwitchExpression extends LexNode {

    SwitchExpression(Term discriminant, Term cases) {
        super(discriminant, cases);
    }

    Term getDiscriminant() {
        return terms[0];
    }

    Term getCases() {
        return terms[1];
    }

    void processPass1(Context c) {
        fatalError(c,
                "switch expression is only supported as the initializer of "
                + "a local variable declaration in this fork");
    }
}
