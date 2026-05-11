// Quirk #5 (lambda target inference into constructor args).
// `new Action("ping", () -> doX())` previously errored with "lambda
// needs an explicit functional-interface target type" because the
// pre-pass1 lambda-arg target lookup only ran for MethodInvocation,
// not InstanceCreation. InstanceCreation now mirrors the
// preProcessLambdaArgs path: resolves the type early, picks the
// unique constructor by arity, and threads each formal param's
// ExpressionType into the matching lambda / method-ref arg's
// processPass1 via c.currentVarType.

public final class LambdaCtorArg
{

 static final class Action
 {
  final String label;
  final Runnable cb;
  Action(String label, Runnable cb)
  {
   this.label = label;
   this.cb = cb;
  }
  void run()
  {
   System.out.println(label);
   cb.run();
  }
 }

 // Constructor with a method-reference arg — same dispatch path.
 static final class Greeter
 {
  final Runnable r;
  Greeter(Runnable r) { this.r = r; }
  void go() { r.run(); }
 }

 static void hello() { System.out.println("hi"); }

 public static void main(String[] args)
 {
  // Lambda in constructor arg position — target inferred from the
  // unique Action(String, Runnable) ctor.
  Action a = new Action("ping", () -> System.out.println("pong"));
  a.run();

  // Method-reference in constructor arg position.
  Greeter g = new Greeter(LambdaCtorArg::hello);
  g.go();
 }
}
