// D4 (JLS 21): `case null` in enum switch. SwitchStatement's
// buildEnumEq detects null labels and emits `tmp == null` rather
// than `tmp == EnumType.null` (which would be a parse-level
// "undefined qualified variable" error). The exhaustiveness check
// skips null labels — they're orthogonal to enum-constant coverage.

public final class EnumSwitchNullCase
{

 public enum D { L, R }

 public static int code(D d)
 {
  return switch (d) {
   case null -> 0;
   case L    -> 1;
   case R    -> 2;
  };
 }

 public static void main(String[] args)
 {
  System.out.println(code(null));
  System.out.println(code(D.L));
  System.out.println(code(D.R));
 }
}
