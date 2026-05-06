// Lambdas (Java 8, JEP 126). Slices 23 + 23b: single-arg `id ->`,
// zero-arg `() ->`, and multi-arg `(a, b, ...) ->`. Lambda still has
// to sit in a variable initializer with an explicit target interface.

public final class Lambda
{

 interface IntOp
 {
  int apply(int x);
 }

 interface IntBinaryOp
 {
  int apply(int a, int b);
 }

 public static void main(String[] args)
 {
  Runnable r = () -> System.out.println("ran");
  r.run();

  IntOp inc = x -> x + 1;
  System.out.println(inc.apply(5));
  System.out.println(inc.apply(10));

  IntOp twice = x -> { return x * 2; };
  System.out.println(twice.apply(7));

  IntBinaryOp add = (a, b) -> a + b;
  IntBinaryOp mul = (a, b) -> a * b;
  System.out.println(add.apply(2, 3));
  System.out.println(mul.apply(4, 5));
 }
}
