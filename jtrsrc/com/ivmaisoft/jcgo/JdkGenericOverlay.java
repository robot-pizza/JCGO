/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/JdkGenericOverlay.java --
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
 * Standards-pass P2 overlay for the JDK collection / iterator / map
 * interfaces that classpath-0.93 declares without generics.
 *
 * classpath-0.93 predates JLS-5 generics, so its java.util.List etc.
 * declare `Object get(int)` rather than `E get(int)`. JCGO's
 * slice-50 retention picks up generic type-vars only when the
 * declaration AST carries them, so without modernizing the source
 * itself the type-var return information is missing at use sites.
 *
 * This overlay registers the type-parameter list and the
 * generic-return-type-var name for each method of interest. It's
 * consulted via:
 *   - ClassDefinition.getGenericTypeParamNames — falls back to
 *     overlay when the class's own genericSignatureData is empty
 *     (parsed pre-generics classpath).
 *   - MethodDefinition.getReturnTypeVarName — falls back to overlay
 *     when slice-50 didn't capture a returnTypeVarName.
 *
 * Coverage prioritizes the methods whose return type drives the
 * #2 chained-call substitution: Map<K,V>.get → V is the most
 * visible win since it's a 2-type-parameter case the receiver-side
 * heuristic deliberately punts on. Single-type-param classes
 * (Collection, List, Iterator, Set, Queue, Deque, Stack) also
 * benefit from a precise lookup (vs. the heuristic's
 * single-captured-arg fallback) by allowing methods like
 * `Iterator.next()` to substitute even when the receiver
 * variable's slice-50 captured-args weren't visible (e.g. a
 * method returning Iterator without an explicit captured-args
 * declaration in the caller).
 *
 * Lookup walks the class chain (`definingClass` → its superClass
 * → declared interfaces) so subclasses like ArrayList that
 * override List.get pick up the parent's overlay entry.
 */
final class JdkGenericOverlay {

    private static final ObjHashtable typeParams = new ObjHashtable();
    private static final ObjHashtable returnTypeVar = new ObjHashtable();

    static {
        // Single-E classes: Iterator family.
        registerSingleE("java.util.Iterator");
        registerReturn("java.util.Iterator", "next()", "E");
        registerSingleE("java.util.ListIterator");
        registerReturn("java.util.ListIterator", "previous()", "E");

        // Iterable<T> uses T. Marked so subclasses inherit; no
        // method returns T directly (iterator() returns
        // Iterator<T> which JCGO sees as Iterator at erasure).
        typeParams.put("java.util.Iterable", new String[] { "T" });
        typeParams.put("java.lang.Iterable", new String[] { "T" });

        // Collection<E> and its hierarchy.
        registerSingleE("java.util.Collection");
        registerSingleE("java.util.AbstractCollection");
        registerSingleE("java.util.AbstractList");
        registerSingleE("java.util.AbstractSequentialList");
        registerSingleE("java.util.AbstractSet");
        registerSingleE("java.util.List");
        registerSingleE("java.util.ArrayList");
        registerSingleE("java.util.LinkedList");
        registerSingleE("java.util.Vector");
        registerSingleE("java.util.Stack");
        registerSingleE("java.util.Set");
        registerSingleE("java.util.HashSet");
        registerSingleE("java.util.LinkedHashSet");
        registerSingleE("java.util.TreeSet");
        registerSingleE("java.util.SortedSet");
        registerSingleE("java.util.NavigableSet");
        registerSingleE("java.util.Queue");
        registerSingleE("java.util.Deque");
        registerSingleE("java.util.ArrayDeque");
        registerSingleE("java.util.PriorityQueue");

        // List<E>: index-keyed accessors return E.
        registerReturn("java.util.List", "get(I)", "E");
        registerReturn("java.util.List", "set(ILjava/lang/Object;)", "E");
        registerReturn("java.util.List", "remove(I)", "E");
        registerReturn("java.util.AbstractList", "get(I)", "E");
        registerReturn("java.util.AbstractList", "set(ILjava/lang/Object;)", "E");
        registerReturn("java.util.AbstractList", "remove(I)", "E");
        registerReturn("java.util.ArrayList", "get(I)", "E");
        registerReturn("java.util.ArrayList", "set(ILjava/lang/Object;)", "E");
        registerReturn("java.util.ArrayList", "remove(I)", "E");
        registerReturn("java.util.LinkedList", "get(I)", "E");
        registerReturn("java.util.LinkedList", "set(ILjava/lang/Object;)", "E");
        registerReturn("java.util.LinkedList", "remove(I)", "E");
        registerReturn("java.util.LinkedList", "peek()", "E");
        registerReturn("java.util.LinkedList", "poll()", "E");
        registerReturn("java.util.LinkedList", "element()", "E");
        registerReturn("java.util.LinkedList", "getFirst()", "E");
        registerReturn("java.util.LinkedList", "getLast()", "E");
        registerReturn("java.util.LinkedList", "removeFirst()", "E");
        registerReturn("java.util.LinkedList", "removeLast()", "E");
        registerReturn("java.util.LinkedList", "peekFirst()", "E");
        registerReturn("java.util.LinkedList", "peekLast()", "E");
        registerReturn("java.util.LinkedList", "pollFirst()", "E");
        registerReturn("java.util.LinkedList", "pollLast()", "E");
        registerReturn("java.util.Vector", "get(I)", "E");
        registerReturn("java.util.Vector", "set(ILjava/lang/Object;)", "E");
        registerReturn("java.util.Vector", "remove(I)", "E");
        registerReturn("java.util.Vector", "firstElement()", "E");
        registerReturn("java.util.Vector", "lastElement()", "E");
        registerReturn("java.util.Vector", "elementAt(I)", "E");
        registerReturn("java.util.Stack", "peek()", "E");
        registerReturn("java.util.Stack", "pop()", "E");

        // Queue<E> / Deque<E>: head accessors return E.
        registerReturn("java.util.Queue", "peek()", "E");
        registerReturn("java.util.Queue", "poll()", "E");
        registerReturn("java.util.Queue", "element()", "E");
        registerReturn("java.util.Queue", "remove()", "E");
        registerReturn("java.util.Deque", "peek()", "E");
        registerReturn("java.util.Deque", "poll()", "E");
        registerReturn("java.util.Deque", "element()", "E");
        registerReturn("java.util.Deque", "remove()", "E");
        registerReturn("java.util.Deque", "peekFirst()", "E");
        registerReturn("java.util.Deque", "peekLast()", "E");
        registerReturn("java.util.Deque", "pollFirst()", "E");
        registerReturn("java.util.Deque", "pollLast()", "E");
        registerReturn("java.util.Deque", "getFirst()", "E");
        registerReturn("java.util.Deque", "getLast()", "E");
        registerReturn("java.util.Deque", "removeFirst()", "E");
        registerReturn("java.util.Deque", "removeLast()", "E");
        registerReturn("java.util.ArrayDeque", "peek()", "E");
        registerReturn("java.util.ArrayDeque", "poll()", "E");
        registerReturn("java.util.ArrayDeque", "peekFirst()", "E");
        registerReturn("java.util.ArrayDeque", "peekLast()", "E");
        registerReturn("java.util.ArrayDeque", "pollFirst()", "E");
        registerReturn("java.util.ArrayDeque", "pollLast()", "E");
        registerReturn("java.util.ArrayDeque", "getFirst()", "E");
        registerReturn("java.util.ArrayDeque", "getLast()", "E");
        registerReturn("java.util.PriorityQueue", "peek()", "E");
        registerReturn("java.util.PriorityQueue", "poll()", "E");

        // Map<K, V>: V-returning accessors. Two type-parameters so
        // the receiver-side heuristic in MethodInvocation skips
        // these — overlay precision matters.
        registerMapKV("java.util.Map");
        registerMapKV("java.util.AbstractMap");
        registerMapKV("java.util.HashMap");
        registerMapKV("java.util.LinkedHashMap");
        registerMapKV("java.util.IdentityHashMap");
        registerMapKV("java.util.WeakHashMap");
        registerMapKV("java.util.TreeMap");
        registerMapKV("java.util.SortedMap");
        registerMapKV("java.util.NavigableMap");
        registerMapKV("java.util.Hashtable");
        registerMapReturns("java.util.Map");
        registerMapReturns("java.util.AbstractMap");
        registerMapReturns("java.util.HashMap");
        registerMapReturns("java.util.LinkedHashMap");
        registerMapReturns("java.util.IdentityHashMap");
        registerMapReturns("java.util.WeakHashMap");
        registerMapReturns("java.util.TreeMap");
        registerMapReturns("java.util.Hashtable");

        // Map.Entry<K, V>: key/value accessors.
        typeParams.put("java.util.Map$Entry", new String[] { "K", "V" });
        registerReturn("java.util.Map$Entry", "getKey()", "K");
        registerReturn("java.util.Map$Entry", "getValue()", "V");
        registerReturn("java.util.Map$Entry",
                "setValue(Ljava/lang/Object;)", "V");

        // Reference family — small overlay, not chained-call-driven
        // but added for completeness so getGenericTypeParamNames
        // returns sensible info on the iface.
        typeParams.put("java.lang.ref.Reference", new String[] { "T" });
        registerReturn("java.lang.ref.Reference", "get()", "T");
    }

    private static void registerSingleE(String className) {
        typeParams.put(className, new String[] { "E" });
    }

    private static void registerMapKV(String className) {
        typeParams.put(className, new String[] { "K", "V" });
    }

    private static void registerMapReturns(String className) {
        registerReturn(className, "get(Ljava/lang/Object;)", "V");
        registerReturn(className,
                "put(Ljava/lang/Object;Ljava/lang/Object;)", "V");
        registerReturn(className, "remove(Ljava/lang/Object;)", "V");
        registerReturn(className,
                "getOrDefault(Ljava/lang/Object;Ljava/lang/Object;)", "V");
        registerReturn(className,
                "putIfAbsent(Ljava/lang/Object;Ljava/lang/Object;)", "V");
        registerReturn(className,
                "replace(Ljava/lang/Object;Ljava/lang/Object;)", "V");
    }

    private static void registerReturn(String className, String methodSig,
            String typeVar) {
        returnTypeVar.put(className + "#" + methodSig, typeVar);
    }

    static String[] getTypeParamsFor(String className) {
        return (String[]) typeParams.get(className);
    }

    /**
     * Walk the class's declaration chain (super + declared interfaces)
     * for an overlay entry. The method's `definingClass` is checked
     * first, then superclasses, then interfaces. Returns null when
     * nothing in the chain has a registered entry.
     */
    static String getReturnTypeVarFor(ClassDefinition definingCls,
            String methodSig) {
        if (definingCls == null || methodSig == null) return null;
        return walkChainForReturn(definingCls, methodSig,
                new ObjHashSet());
    }

    private static String walkChainForReturn(ClassDefinition cls,
            String methodSig, ObjHashSet visited) {
        if (cls == null || visited.contains(cls)) return null;
        visited.add(cls);
        String direct = (String) returnTypeVar.get(cls.name() + "#"
                + methodSig);
        if (direct != null) return direct;
        ClassDefinition sc = cls.superClass();
        if (sc != null) {
            String v = walkChainForReturn(sc, methodSig, visited);
            if (v != null) return v;
        }
        return null;
    }

    private JdkGenericOverlay() {
    }
}
