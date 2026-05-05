// JCGO-SKIP: sealed/non-sealed/permits are Java 17 (JEP 409). JCGO
// doesn't yet recognize them as contextual keywords in class/interface
// declaration position.

public final class Sealed
{

 sealed interface Shape permits Circle, Square {}
 record Circle(double r) implements Shape {}
 record Square(double s) implements Shape {}

 public static void main(String[] args)
 {
  Shape s = new Circle(1.0);
  System.out.println(s);
 }
}
