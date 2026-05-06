// Lambdas (Java 8, JEP 126). Slices 23/23b/24d cover variable
// initializer + multi-arg parens + return position. Lambda in
// method-arg / cast / assignment-only positions is still deferred.

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

 // Slice 24d: lambda at the head of a return expression — target type
 // comes from the method's declared return type via md.exprType().
 // Captures don't reach into the synthesized anonymous class yet, so
 // these examples don't reference enclosing locals.
 static IntOp constOp()
 {
  return x -> x + 1;
 }

 static Runnable greeter()
 {
  return () -> System.out.println("hello");
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

  IntOp adder = constOp();
  System.out.println(adder.apply(5));

  greeter().run();
 }
}
