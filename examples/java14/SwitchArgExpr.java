// Switch expression in method-argument / return-of-call positions
// (Java 14, JEP 361). Slice 36 hoists each SwitchExpression sub-term
// out of the call into a synthesized `T $tmp; switch(...) { $tmp = ...; }`
// preamble. Type of $tmp comes from the first non-throw arm body —
// integer/string/char/bool literals recognized; everything else falls
// back to Object.

public final class SwitchArgExpr
{

 static int max(int a, int b) { return a > b ? a : b; }

 static String describe(int s) { return "size=" + s; }

 public static void main(String[] args)
 {
  int day = 3;

  // Switch expression as a method-arg (println(int) overload).
  System.out.println(switch (day)
  {
   case 0, 6 -> -1;
   case 1, 2, 3, 4, 5 -> day;
   default -> 99;
  });

  // Switch expression returning String, used as a method-arg.
  System.out.println(describe(switch (day)
  {
   case 0, 6 -> 0;
   default -> day * 10;
  }));

  // Two switch-expr args in the same call.
  System.out.println(max(
   switch (day) { case 0 -> 100; default -> 1; },
   switch (day) { case 0 -> 0; default -> 50; }));

  // Slice 38: switch expression in cast position. Inner parens
  // around the switch aren't needed — `(long) switch(...)` parses
  // directly through UnaryWithParaTail.
  long big = (long) switch (day) { case 0 -> 100; default -> 99; };
  System.out.println(big);

  // Slice 43: switch expression as a ternary arm.
  boolean weekend = day == 0 || day == 6;
  int rank = weekend
   ? switch (day) { case 0 -> 70; default -> 71; }
   : day * 10;
  System.out.println(rank);
 }
}
