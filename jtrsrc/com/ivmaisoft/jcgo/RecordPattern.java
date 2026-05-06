/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/RecordPattern.java --
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
 * Slice 16 (Java 21, JEP 440). A record pattern, e.g. `Point(int x,
 * int y)` or `Box(Point(var a, var b), Point lo)`.
 *
 * terms[0] = the record type Term (a SimpleType node).
 *
 * components is a positional list of sub-patterns; each is either a
 * simple binding (type + name) or a nested RecordPattern. The mapping
 * to record component accessors is by index — name lookup happens at
 * desugar time via RecordSynthesis.componentsForType.
 */
final class RecordPattern extends LexNode {

    private final ObjVector components;

    RecordPattern(Term type, ObjVector components) {
        super(type);
        this.components = components;
    }

    Term getType() {
        return terms[0];
    }

    ObjVector getComponents() {
        return components;
    }

    /**
     * One element of a record pattern. Exactly one of binding+bindingType
     * or nested is set. bindingType may be null when the source spelled
     * `var` (target type is filled in by the lifter from the record's
     * declared component type).
     */
    static final class Component {
        final Term bindingType;
        final String binding;
        final RecordPattern nested;

        Component(Term bindingType, String binding) {
            this.bindingType = bindingType;
            this.binding = binding;
            this.nested = null;
        }

        Component(RecordPattern nested) {
            this.bindingType = null;
            this.binding = null;
            this.nested = nested;
        }

        boolean isNested() {
            return nested != null;
        }
    }
}
