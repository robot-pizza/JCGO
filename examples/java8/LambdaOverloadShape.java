// D1: lambda overload narrowing by body return-type shape.
// `classifyLambdaShape` pins:
//   - block-with-`return EXPR;` → SHAPE_VALUE → only non-void SAMs
//   - block-no-return → SHAPE_VOID → only void SAMs
//   - expression body w/ unambiguous value shape (literal,
//     arithmetic, new) → SHAPE_VALUE
// Residual: expression body whose terminal is a MethodInvocation /
// Assignment / Postfix ++/-- can't be classified syntactically
// (could be void OR value depending on the called method's
// return) — SHAPE_ANY. javac resolves those via speculative pass1
// inference; JCGO doesn't and surfaces the existing explicit-target
// error there.

public final class LambdaOverloadShape
{

 interface Sup<T> { T get(); }

 static final class X
 {
  X(String a, Runnable r) { System.out.println("R"); r.run(); }
  X(String a, Sup<Integer> s) { System.out.println("S=" + s.get()); }
 }

 public static void main(String[] args)
 {
  // Block-with-return → VALUE → Sup<Integer>.
  new X("k", () -> { return Integer.valueOf(7); });

  // Block-no-return → VOID → Runnable.
  int[] sink = new int[]{ 0 };
  new X("k", () -> { sink[0]++; });

  // Arithmetic expression → VALUE → Sup<Integer>.
  new X("k", () -> 9 + 1);

  // Constructor-call expression → VALUE → Sup<Integer>.
  new X("k", () -> new Integer(3));
 }
}
