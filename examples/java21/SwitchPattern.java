// JCGO-SKIP: pattern matching for switch is Java 21 (JEP 441). JCGO
// doesn't yet parse type patterns or "when" guards in case labels.

public final class SwitchPattern
{

 sealed interface Shape permits Circle, Square {}
 record Circle(double r) implements Shape {}
 record Square(double s) implements Shape {}

 static double area(Shape sh)
 {
  return switch (sh)
  {
   case Circle c -> Math.PI * c.r() * c.r();
   case Square sq when sq.s() > 0 -> sq.s() * sq.s();
   case Square sq -> 0.0;
  };
 }

 public static void main(String[] args)
 {
  System.out.println(area(new Circle(2.0)));
 }
}
