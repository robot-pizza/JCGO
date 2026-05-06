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

 // Slice 24h/24i: lambda inside an instance method capturing
 // enclosing instance state via either `OuterClass.this.field` or
 // bare `this.field`. Slice 24i added a bare-`this` rewrite during
 // lambda synthesis so JLS 15.27.2 semantics hold (lambda inherits
 // the enclosing `this` rather than introducing a new one).
 static final class Counter
 {
  final int seed;
  Counter(int seed) { this.seed = seed; }
  void run()
  {
   IntOp shiftQualified = x -> x + Counter.this.seed;
   System.out.println(shiftQualified.apply(3));
   IntOp shiftBare = x -> x + this.seed;
   System.out.println(shiftBare.apply(7));
  }
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

  // Slice 25: typed lambda parameters. The declared types are
  // consumed and the SAM's formal types win — but the parser
  // accepts the user's annotation.
  IntBinaryOp sub = (int a, int b) -> a - b;
  System.out.println(sub.apply(10, 3));

  IntOp adder = constOp();
  System.out.println(adder.apply(5));

  greeter().run();

  IntOp plus100 = makeAdder(100);
  System.out.println(plus100.apply(5));

  IntOp times7 = makeMultiplier(7);
  System.out.println(times7.apply(6));

  // Slice 24h: lambda inside an instance method capturing `this`.
  new Counter(10).run();

  // Slice 34: lambda / method-ref in cast position — cast type names
  // the target functional interface explicitly.
  Runnable castR = (Runnable)() -> System.out.println("cast-lambda");
  castR.run();
  IntOp castInc = (IntOp) x -> x + 100;
  System.out.println(castInc.apply(1));
 }
}
