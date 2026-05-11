// Quirk #7 (switch on enum rejected) — arrow / switch-expression
// half. Both forms ride the same SwitchStatement.processPass1 enum
// detection as the colon form in examples/java5/EnumSwitch.java:
// switch-expression lowers through SwitchExpressionLifter to a
// SwitchStatement first, then the enum desugar fires there.

public final class EnumSwitchArrow
{

 public enum Direction { LEFT, RIGHT, UP, DOWN }

 // Arrow form.
 public static int arrow(Direction d)
 {
  int code = 0;
  switch (d) {
   case LEFT  -> code = 1;
   case RIGHT -> code = 2;
   case UP    -> code = 3;
   case DOWN  -> code = 4;
  }
  return code;
 }

 // Switch-expression with multi-label arrow cases.
 public static int expr(Direction d)
 {
  return switch (d) {
   case LEFT, RIGHT -> 1;
   case UP, DOWN    -> 2;
  };
 }

 public static void main(String[] args)
 {
  System.out.println(arrow(Direction.RIGHT));    // 2
  System.out.println(arrow(Direction.DOWN));     // 4
  System.out.println(expr(Direction.RIGHT));     // 1
  System.out.println(expr(Direction.DOWN));      // 2
 }
}
