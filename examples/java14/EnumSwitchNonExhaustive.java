// JCGO-SKIP — invalid-by-design fixture; the harness's inv_14
// registry runs it via run_invalid and asserts the version-positive
// run is gated out so the positive runner doesn't fail it.
//
// Standards-pass P6 negative — non-exhaustive enum-switch-expression
// without a default arm must be rejected.

public final class EnumSwitchNonExhaustive
{

 public enum Dir { N, S }

 public static int incomplete(Dir d)
 {
  return switch (d) {
   case N -> 1;     // S not covered, no default → reject
  };
 }

 public static void main(String[] args)
 {
  System.out.println(incomplete(Dir.N));
 }
}
