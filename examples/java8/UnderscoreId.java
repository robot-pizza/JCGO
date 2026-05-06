// `_` as a regular identifier — legal up through Java 8, reserved as
// of Java 9 (JLS 3.8 / JEP 213). Slice 35 enforces the reverse gate:
// fixture is shipped under java8 and should fail at -source 9+.

public final class UnderscoreId
{

 public static void main(String[] args)
 {
  int _ = 5;
  System.out.println(_);
 }
}
