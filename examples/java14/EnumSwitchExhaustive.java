// Standards-pass P6: enum-switch-expression must cover every enum
// constant OR include a `default`. Positive case — fully-covered
// switch-expression compiles. The corresponding negative case is
// inv_14_EnumSwitchNonExhaustive_needle below; non-exhaustive form
// is rejected at pass1.

public final class EnumSwitchExhaustive
{

 public enum Dir { N, S, E, W }

 // Every enum constant covered → exhaustive.
 public static int allCovered(Dir d)
 {
  return switch (d) {
   case N -> 1;
   case S -> 2;
   case E -> 3;
   case W -> 4;
  };
 }

 // Default arm covers any uncovered enum constant.
 public static int withDefault(Dir d)
 {
  return switch (d) {
   case N -> 1;
   default -> 0;
  };
 }

 public static void main(String[] args)
 {
  System.out.println(allCovered(Dir.E));      // 3
  System.out.println(withDefault(Dir.S));     // 0
 }
}
