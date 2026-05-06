// Generics (Java 5, JLS 4.5) + diamond + generic methods. Slice 24
// erasure-mode parser support, slice 24b adds generic-method prefix
// and exercises the diamond operator (which already works because
// consumeGenericArgs accepts an empty `<>`).

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

 // Generic method — `<T>` prefix is consumed and discarded.
 static <T> Object identity(Object x)
 {
  return x;
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

  // Diamond operator — `<>` is empty type args, parser accepts.
  Box<String> d = new Box<>("diamond");
  System.out.println(d.get());

  // Generic method invocation — type args inferred (not declared).
  System.out.println(identity("via-generic-method"));

  // Slice 30: explicit type-witness invocation `.<TypeArgs>method(...)`
  // — type args parse and erase.
  System.out.println(Generics.<String>identity("via-witness"));
 }
}
