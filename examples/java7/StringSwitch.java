// JCGO-SKIP: parser accepts the String discriminant under -source 7+,
// but slice 7a stops at the gate — desugaring is slice 7b. The negative
// test (under -source 6) still verifies the version gate path.

public final class StringSwitch
{

 public static void main(String[] args)
 {
  String s = args.length > 0 ? args[0] : "default";
  switch (s)
  {
   case "alpha":
    System.out.println("a");
    break;
   case "beta":
    System.out.println("b");
    break;
   default:
    System.out.println("?");
  }
 }
}
