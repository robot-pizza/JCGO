// Standards-pass P5: lambda targeting a SAM whose RETURN type is a
// type-var. `interface Sup<T> { T get(); }` targeted as
// `Sup<Integer>` — the synthesized method's return type must be
// substituted from T to Integer so the user's typed body (which
// returns Integer) type-checks against the SAM contract.
//
// Existing #6 fix already handled parameter substitution; this is
// the symmetric return-type case via LambdaSynthesis.resolveTypeVarReturn.

public final class LambdaSupplier
{

 interface Sup<T> { T get(); }

 static int callGet(Sup<Integer> s)
 {
  return s.get().intValue();
 }

 // Single-type-arg case (Sup<Integer>) — return type substitutes T → Integer.
 static String callGetStr(Sup<String> s)
 {
  return s.get();
 }

 public static void main(String[] args)
 {
  System.out.println(callGet(() -> Integer.valueOf(42)));
  System.out.println(callGetStr(() -> "hi"));
 }
}
