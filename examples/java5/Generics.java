// Generics (Java 5, JLS 4.5). Slice 24 MVP — erasure model: parser
// accepts and discards `<TypeArgs>` everywhere they appear in type
// positions and on class/interface declarations, so user code can use
// generic syntax without JCGO needing to track parameter bindings.
//
// Out of scope:
//   - Bounded type-parameter checking (`<T extends Number>` parses
//     but the bound isn't enforced)
//   - Generic methods `<T> T foo(T x)`
//   - Diamond operator `new ArrayList<>()` — needs separate parser hook
//   - Type-argument-aware overload resolution

public final class Generics
{

 // Generic class — `<T>` parses and erases.
 static final class Box<T>
 {
  Object value;
  Box(Object v) { this.value = v; }
  Object get() { return value; }
 }

 // Generic interface — type-param list also accepted on interfaces.
 interface Holder<T>
 {
  Object held();
 }

 public static void main(String[] args)
 {
  Box<Integer> b = new Box<Integer>(Integer.valueOf(42));
  System.out.println(b.get());

  Box<String> s = new Box<String>("hello");
  System.out.println(s.get());

  // Nested generics — `>>` closes both layers in one token.
  Box<Box<Integer>> nested = new Box<Box<Integer>>(b);
  System.out.println(((Box) nested.get()).get());
 }
}
