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

 // Slice 22b: assignment-form lift — `lhs = switch (...);`.
 static int kind(int day)
 {
  int k;
  k = switch (day)
  {
   case 0, 6 -> 0;
   case 1, 2, 3, 4, 5 -> 1;
   default -> 2;
  };
  return k;
 }

 // Slice 31: `throw switch(...) {...};`. Each arm becomes a throw
 // statement directly — no temp needed.
 static int validate(int day) throws Throwable
 {
  if (day < 0 || day > 6)
   throw switch (day)
   {
    case -1 -> new IllegalArgumentException("negative-1");
    default -> new RuntimeException("out-of-range:" + day);
   };
  return day;
 }

 public static void main(String[] args) throws Throwable
 {
  System.out.println(rank(0));
  System.out.println(rank(3));
  System.out.println(rank(7));
  System.out.println(label(1));
  System.out.println(label(5));
  System.out.println(kind(0));
  System.out.println(kind(2));
  System.out.println(kind(8));
  System.out.println(validate(3));
 }
}
