// Bounded type-parameter erasure (Java 5 / slice 46). `<T extends X>`
// erases to X, not Object — so a method that calls X-typed methods on
// the type-param actually compiles. The first bound class wins on
// `& X` multi-bounds.

public final class BoundedGenerics
{

 // Bounded type-param: T extends Number, so .intValue() works.
 static final class NumBox<T extends Number>
 {
  T n;
  NumBox(T n) { this.n = n; }
  int asInt() { return n.intValue(); }
  T raw() { return n; }
 }

 // Generic method with bounded type-param.
 static <T extends CharSequence> int totalLength(T a, T b)
 {
  return a.length() + b.length();
 }

 // Multi-bound: erases to first bound class (Number).
 static <T extends Number & java.io.Serializable> double doubled(T x)
 {
  return x.doubleValue() * 2.0;
 }

 // Recursive bound: <T extends Comparable<T>> erases to Comparable.
 static <T extends Comparable<T>> T pickMax(T a, T b)
 {
  return a.compareTo(b) >= 0 ? a : b;
 }

 public static void main(String[] args)
 {
  NumBox<Integer> ib = new NumBox<Integer>(Integer.valueOf(42));
  System.out.println(ib.asInt());
  System.out.println(ib.raw());

  NumBox<Long> lb = new NumBox<Long>(Long.valueOf(1000L));
  System.out.println(lb.asInt());

  System.out.println(totalLength("hello", "world!"));

  System.out.println(doubled(Integer.valueOf(21)));

  System.out.println(pickMax("alpha", "beta"));
 }
}
