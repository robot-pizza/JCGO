// Switch expression with yield (Java 14, JEP 361). Slice 14b: lifted
// into a local variable's initializer.

public final class SwitchExpr
{

 public static void main(String[] args)
 {
  int day = args.length;
  String name = switch (day)
  {
   case 0, 6 -> "weekend";
   case 1, 2, 3, 4, 5 -> "weekday";
   default -> {
    String s = "out-of-range:" + day;
    yield s;
   }
  };
  System.out.println(name);
 }
}
