// Slice 51: bridge methods for generic covariant override.
//
// `Box<T>` has `void put(T x)` which slice 45 erases to
// `void put(Object x)`. `StringBox extends Box<String>` overrides
// with `void put(String x)`. Java says these are an override (after
// erasure) and the JVM emits a synthetic bridge method
// `void put(Object x)` in StringBox that downcasts to String and
// calls `put(String)`. Without the bridge, dispatch through a Box
// reference invokes the parent's empty `put(Object)` and the child's
// override is silently lost.
//
// Return-type covariance (Object → String) doesn't actually need a
// bridge in JCGO because vtable slots use ABI-compatible function
// pointers; parameter-type covariance is the real gap.
//
// This fixture exercises:
//   * single-param bridge (`put(T)` → `put(String)`),
//   * multi-param bridge (`pair(K, V)` → `pair(String, Integer)`),
//   * a non-generic helper that proves the bridge is used at the
//     vtable boundary (JCGO can't devirtualize past it).

public final class BridgeOverride
{

 static class Box<T>
 {
  Object stored = "(empty)";
  void put(T x) { stored = x; }
  Object peek() { return stored; }
 }

 static class StringBox extends Box<String>
 {
  void put(String x) { stored = "STR:" + x; }
 }

 static class Pair<K, V>
 {
  Object stored = "(empty)";
  void pair(K k, V v) { stored = "raw:" + k + ":" + v; }
 }

 static class StringIntPair extends Pair<String, Integer>
 {
  void pair(String k, Integer v) { stored = "SI:" + k + ":" + v; }
 }

 // Force virtual dispatch — JCGO can't devirtualize past this
 // boundary.
 static void putThroughParent(Box b, Object value)
 {
  b.put(value);
 }

 static void pairThroughParent(Pair p, Object k, Object v)
 {
  p.pair(k, v);
 }

 public static void main(String[] args)
 {
  StringBox sb = new StringBox();
  putThroughParent(sb, "hello");
  System.out.println(sb.peek());

  StringIntPair sip = new StringIntPair();
  pairThroughParent(sip, "n", Integer.valueOf(42));
  System.out.println(sip.stored);
 }
}
