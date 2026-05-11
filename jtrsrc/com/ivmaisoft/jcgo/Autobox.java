/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/Autobox.java --
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
 * Slice 18 (Java 5, JLS 5.1.7-5.1.8). Inserts implicit
 * `WrapperType.valueOf(x)` and `x.primitiveValue()` calls at
 * coercion sites (assignment, method args, return, ternary).
 *
 * Usage: at a coercion site where the target type and source type
 * have already been computed, call `coerce(c, expr, targetSize)`.
 * The returned Term is either the original expr (no conversion
 * needed or possible) or a wrapped MethodInvocation. Callers should
 * re-read exprType() afterwards.
 *
 * The version gate is enforced by the call sites — they should only
 * invoke Autobox when Main.dict.javaVersion >= JLS_50.
 */
final class Autobox {

    private Autobox() {
    }

    /**
     * Returns expr, possibly wrapped to convert it toward targetSize.
     * Targets:
     *   - targetSize is a primitive (BOOLEAN..DOUBLE), expr static type
     *     is a wrapper class → unbox via x.primitiveValue().
     *   - targetSize is reference (CLASSINTERFACE+), expr is a primitive
     *     (BOOLEAN..DOUBLE) → box via WrapperType.valueOf(x).
     *   - Anything else → returns expr unchanged.
     */
    static Term coerce(Context c, Term expr, int targetSize) {
        int srcSize = expr.exprType().objectSize();
        boolean wouldBox = targetSize >= Type.CLASSINTERFACE
                && isPrimitive(srcSize);
        int wrapperPrim = -1;
        if (isPrimitive(targetSize) && srcSize >= Type.CLASSINTERFACE) {
            wrapperPrim = wrapperPrimitive(expr);
        }
        boolean wouldUnbox = wrapperPrim >= 0;
        if (!wouldBox && !wouldUnbox) return expr;
        if (!c.versionAtLeast(JavaVersion.JLS_50)) {
            expr.fatalError(c,
                    "autoboxing requires -source 5 or higher (got "
                    + JavaVersion.format(Main.dict.javaVersion) + ")");
            return expr;
        }
        if (wouldBox) return box(c, expr, srcSize);
        return unbox(c, expr, wrapperPrim);
    }

    /**
     * Same as coerce, but the caller already knows the source must be
     * unboxed to a particular primitive (e.g. arithmetic operands).
     * Returns null if the receiver class is not a known wrapper.
     */
    static Term forceUnbox(Context c, Term expr) {
        int srcSize = expr.exprType().objectSize();
        if (srcSize < Type.CLASSINTERFACE) return null;
        int wrapperPrim = wrapperPrimitive(expr);
        if (wrapperPrim < 0) return null;
        if (!c.versionAtLeast(JavaVersion.JLS_50)) {
            expr.fatalError(c,
                    "autoboxing requires -source 5 or higher (got "
                    + JavaVersion.format(Main.dict.javaVersion) + ")");
            return null;
        }
        return unbox(c, expr, wrapperPrim);
    }

    static boolean isPrimitive(int size) {
        return size >= Type.BOOLEAN && size <= Type.DOUBLE;
    }

    /**
     * Slice 18b: returns the primitive size that the given wrapper class
     * unboxes to, or -1 if it isn't a known wrapper. Used by overload
     * resolution to recognize primitive ↔ wrapper compatibility.
     */
    static int wrapperPrimitiveFor(ClassDefinition cd) {
        if (cd == null) return -1;
        String name = cd.name();
        if (Names.JAVA_LANG_BOOLEAN.equals(name)) return Type.BOOLEAN;
        if (Names.JAVA_LANG_BYTE.equals(name)) return Type.BYTE;
        if (Names.JAVA_LANG_CHARACTER.equals(name)) return Type.CHAR;
        if (Names.JAVA_LANG_SHORT.equals(name)) return Type.SHORT;
        if (Names.JAVA_LANG_INTEGER.equals(name)) return Type.INT;
        if (Names.JAVA_LANG_LONG.equals(name)) return Type.LONG;
        if (Names.JAVA_LANG_FLOAT.equals(name)) return Type.FLOAT;
        if (Names.JAVA_LANG_DOUBLE.equals(name)) return Type.DOUBLE;
        return -1;
    }

    /**
     * Slice 18b: returns the wrapper ClassDefinition for a primitive
     * size, or null if size is not a primitive.
     */
    static ClassDefinition wrapperClassFor(int primSize) {
        String name = wrapperNameForPrimitive(primSize);
        return name != null ? Main.dict.get(name) : null;
    }

    private static Term box(Context c, Term primitive, int primSize) {
        String wrapperName = wrapperNameForPrimitive(primSize);
        if (wrapperName == null) return primitive;
        ClassDefinition cd = Main.dict.get(wrapperName);
        return new MethodInvocation(c, cd, Names.VALUEOF, primitive)
                .setLineInfoFrom(primitive);
    }

    private static Term unbox(Context c, Term ref, int primSize) {
        String methodName = unboxMethodName(primSize);
        if (methodName == null) return ref;
        // Use the parser-style constructor + processPass1: the
        // (Context, Term, String) constructor rejects primitive-return
        // methods up front, but unboxing IS exactly that.
        Term mi = new MethodInvocation(ref,
                new LexTerm(LexTerm.ID, methodName), Empty.newTerm());
        mi.setLineInfoFrom(ref);
        mi.processPass1(c);
        return mi;
    }

    private static int wrapperPrimitive(Term expr) {
        ClassDefinition cd = expr.exprType().receiverClass();
        if (cd == null) return -1;
        String name = cd.name();
        if (Names.JAVA_LANG_BOOLEAN.equals(name)) return Type.BOOLEAN;
        if (Names.JAVA_LANG_BYTE.equals(name)) return Type.BYTE;
        if (Names.JAVA_LANG_CHARACTER.equals(name)) return Type.CHAR;
        if (Names.JAVA_LANG_SHORT.equals(name)) return Type.SHORT;
        if (Names.JAVA_LANG_INTEGER.equals(name)) return Type.INT;
        if (Names.JAVA_LANG_LONG.equals(name)) return Type.LONG;
        if (Names.JAVA_LANG_FLOAT.equals(name)) return Type.FLOAT;
        if (Names.JAVA_LANG_DOUBLE.equals(name)) return Type.DOUBLE;
        return -1;
    }

    private static String wrapperNameForPrimitive(int size) {
        switch (size) {
        case Type.BOOLEAN: return Names.JAVA_LANG_BOOLEAN;
        case Type.BYTE: return Names.JAVA_LANG_BYTE;
        case Type.CHAR: return Names.JAVA_LANG_CHARACTER;
        case Type.SHORT: return Names.JAVA_LANG_SHORT;
        case Type.INT: return Names.JAVA_LANG_INTEGER;
        case Type.LONG: return Names.JAVA_LANG_LONG;
        case Type.FLOAT: return Names.JAVA_LANG_FLOAT;
        case Type.DOUBLE: return Names.JAVA_LANG_DOUBLE;
        default: return null;
        }
    }

    private static String unboxMethodName(int primSize) {
        switch (primSize) {
        case Type.BOOLEAN: return "booleanValue";
        case Type.BYTE: return "byteValue";
        case Type.CHAR: return "charValue";
        case Type.SHORT: return "shortValue";
        case Type.INT: return "intValue";
        case Type.LONG: return "longValue";
        case Type.FLOAT: return "floatValue";
        case Type.DOUBLE: return "doubleValue";
        default: return null;
        }
    }
}
