// Lambdas (Java 8, JEP 126). Slice 23 MVP: lambda only as the
// initializer of a variable declaration, where the declared type
// names the target functional interface explicitly. Single-arg
// `id ->` and zero-arg `() ->` forms only — multi-param `(a, b) ->`
// is deferred until peek-depth or backtracking is added.

public final class Lambda
{

 interface IntOp
 {
  int apply(int x);
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
 }
}
