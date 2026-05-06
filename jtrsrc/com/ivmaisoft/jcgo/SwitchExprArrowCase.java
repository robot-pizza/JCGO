/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/SwitchExprArrowCase.java --
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
 * Holds one arrow-form case clause of a switch expression before the
 * lifter converts it into ordinary CaseStatement nodes that assign to
 * the result temp.
 *
 * terms[0] = labels Term: a Seq chain of label expressions, or
 *            Empty.term for `default ->`.
 * terms[1] = body Term: an expression (BODY_EXPR), a throw statement
 *            (BODY_THROW), or a JavaBlock that may contain Yield
 *            statements (BODY_BLOCK).
 */
final class SwitchExprArrowCase extends LexNode {

    static final int BODY_EXPR = 0;
    static final int BODY_THROW = 1;
    static final int BODY_BLOCK = 2;

    private final int bodyKind;

    SwitchExprArrowCase(Term labels, Term body, int bodyKind) {
        super(labels, body);
        this.bodyKind = bodyKind;
    }

    Term getLabels() {
        return terms[0];
    }

    Term getBody() {
        return terms[1];
    }

    int getBodyKind() {
        return bodyKind;
    }
}
