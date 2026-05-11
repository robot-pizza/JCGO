// Quirk #7 (switch on enum rejected) — colon-form half. Arrow form
// and switch-expression form are gated at JLS_140; they live in
// examples/java14/EnumSwitchArrow.java.
//
// `switch (Direction d)` errored at the integer-discriminant gate
// regardless of switch form. SwitchStatement.processPass1 now
// detects an enum-typed discriminant (superClass == java.lang.Enum)
// and desugars to a temp + if/else chain — same shape as the
// existing string-switch desugar (slice 7b). Each `case CONST`
// becomes `tmp == EnumType.CONST`; multi-label / fall-through cases
// ride the existing pending-labels mechanism.

public final class EnumSwitch
{

 public enum Direction { LEFT, RIGHT, UP, DOWN }

 // Colon form with default.
 public static int colon(Direction d)
 {
  int code = 0;
  switch (d) {
   case LEFT:  code = 1; break;
   case RIGHT: code = 2; break;
   case UP:    code = 3; break;
   default:    code = 99; break;
  }
  return code;
 }

 // Colon form with label fall-through (`case A: case B: body;`).
 public static int colonFallThrough(Direction d)
 {
  int code = 0;
  switch (d) {
   case LEFT:
   case RIGHT: code = 10; break;
   case UP:
   case DOWN:  code = 20; break;
  }
  return code;
 }

 public static void main(String[] args)
 {
  System.out.println(colon(Direction.LEFT));            // 1
  System.out.println(colon(Direction.DOWN));            // 99 (default)
  System.out.println(colonFallThrough(Direction.RIGHT)); // 10
  System.out.println(colonFallThrough(Direction.UP));    // 20
 }
}
