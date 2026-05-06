// Switch expression in non-init return context (Java 14, JEP 361).
// Slice 22 extends slice 14b's lifter to fire from `return switch ...`
// at pass1 — using the method's declared return type to size the temp.

public final class SwitchExprReturn
{

 static int rank(int day)
 {
  return switch (day)
  {
   case 0, 6 -> -1;
   case 1, 2, 3, 4, 5 -> day;
   default -> 99;
  };
 }

 static String label(int n)
 {
  return switch (n)
  {
   case 1 -> "one";
   case 2 -> "two";
   default -> "many";
  };
 }

 public static void main(String[] args)
 {
  System.out.println(rank(0));
  System.out.println(rank(3));
  System.out.println(rank(7));
  System.out.println(label(1));
  System.out.println(label(5));
 }
}
