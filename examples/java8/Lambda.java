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

 static IntOp constOp()
 {
  return x -> x + 1;
 }

 static Runnable greeter()
 {
  return () -> System.out.println("hello");
 }

 // Slice 24e: lambda capturing an enclosing-method `final` local.
 static IntOp makeAdder(final int k)
 {
  return x -> x + k;
 }

 // Slice 24g: effectively-final capture — `m` isn't declared final
 // but isn't reassigned either. JCGO's outerLocals filter now
 // accepts any initialized local/param.
 static IntOp makeMultiplier(int m)
 {
  return x -> x * m;
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

  IntOp plus100 = makeAdder(100);
  System.out.println(plus100.apply(5));

  IntOp times7 = makeMultiplier(7);
  System.out.println(times7.apply(6));
 }
}
