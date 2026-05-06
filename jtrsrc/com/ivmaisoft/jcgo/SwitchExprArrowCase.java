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

    // Pattern-case fields (slice 15). Null/0 for constant cases.
    private Term patternType;
    private String patternBinding;
    private Term guard;

    // Record-pattern field (slice 16). Set instead of patternType+binding
    // when the case label spells `case Type(...)` rather than `case Type id`.
    private RecordPattern recordPattern;

    SwitchExprArrowCase(Term labels, Term body, int bodyKind) {
        super(labels, body);
        this.bodyKind = bodyKind;
    }

    void setPattern(Term type, String binding, Term guard) {
        this.patternType = type;
        this.patternBinding = binding;
        this.guard = guard;
    }

    void setRecordPattern(RecordPattern rp, Term guard) {
        this.recordPattern = rp;
        this.guard = guard;
    }

    boolean isPattern() {
        return patternType != null || recordPattern != null;
    }

    RecordPattern getRecordPattern() {
        return recordPattern;
    }

    Term getPatternType() {
        return patternType;
    }

    String getPatternBinding() {
        return patternBinding;
    }

    Term getGuard() {
        return guard;
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
