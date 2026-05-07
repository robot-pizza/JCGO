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

 // Force virtual dispatch — JCGO can't devirtualize past this
 // boundary.
 static void putThroughParent(Box b, Object value)
 {
  b.put(value);
 }

 public static void main(String[] args)
 {
  StringBox sb = new StringBox();
  putThroughParent(sb, "hello");
  // Expected: "STR:hello" — the override should be invoked even when
  // dispatched through a Box reference. Without a bridge, the parent's
  // put(Object) runs and stored becomes just "hello".
  System.out.println(sb.peek());
 }
}
