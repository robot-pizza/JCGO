/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/YieldStatement.java --
 * a part of JCGO translator.
 **
 * Project: JCGO (http://www.ivmaisoft.com/jcgo/)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 */

/*
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

package com.ivmaisoft.jcgo;

/**
 * `yield expr;` inside a switch-expression block body (Java 14, JLS
 * 14.21). Placeholder: the SwitchExpression lifter walks the block
 * body, finds these, and rewrites each into `targetVar = expr; break;`.
 * If a YieldStatement is reached in pass1 it means the lifter missed
 * it — emit a clear error.
 *
 * terms[0] = the yielded expression
 */
final class YieldStatement extends LexNode {

    YieldStatement(Term expr) {
        super(expr);
    }

    Term getExpression() {
        return terms[0];
    }

    void processPass1(Context c) {
        fatalError(c,
                "`yield` is only supported inside a switch-expression "
                + "block body in this fork");
    }
}
