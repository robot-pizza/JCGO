// JCGO-SKIP: switch expressions with arrow + yield are Java 14 (JEP 361).
// JCGO doesn't yet parse "case X ->" or "yield" inside switch blocks.

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
