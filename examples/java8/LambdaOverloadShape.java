// D1: lambda overload narrowing by body return-type shape.
// `classifyLambdaShape` pins:
//   - block-with-`return EXPR;` → SHAPE_VALUE → only non-void SAMs
//   - block-no-return → SHAPE_VOID → only void SAMs
//   - expression body w/ unambiguous value shape (literal,
//     arithmetic, new) → SHAPE_VALUE
//   - expression body that's a MethodInvocation → looks up the
//     called method's return type. SHAPE_VOID when every method
//     of that name on the receiver class returns void
//     (`System.out.println` → Runnable), SHAPE_VALUE when every
//     returns non-void (`Integer.valueOf` → `Sup<Integer>`),
//     SHAPE_ANY when the set is mixed or the receiver isn't
//     statically resolvable.
//   - Assignment / Postfix++/-- → SHAPE_ANY (both target shapes
//     are legal per JLS 15.27; we don't over-filter).

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

  // MethodInvocation expression bodies whose receiver class is
  // statically resolvable:
  //   Integer.valueOf — all overloads return Integer → VALUE → Sup
  new X("k", () -> Integer.valueOf(7));
  //   System.out.println — all overloads return void → VOID → Runnable
  new X("k", () -> System.out.println("v"));
 }
}
