// Strings in switch (Java 7, JLS 14.11). Desugars at parse time to a
// do { ... } while(0) wrapping a chain of String.equals() comparisons.
// Slice 7b — fall-through case grouping is supported.

public final class StringSwitch
{

 public static void main(String[] args)
 {
  String s = args.length > 0 ? args[0] : "default";
  switch (s)
  {
   case "alpha":
   case "first":
    System.out.println("a/first");
    break;
   case "beta":
    System.out.println("b");
    break;
   default:
    System.out.println("?");
  }
 }
}
