// Pattern matching for switch (Java 21, JEP 441). Slice 15 +
// slice 32: pattern-switch in return position is now lifted via
// the same $matched-flag chain as the variable-init form.

public final class SwitchPattern
{

 sealed interface Shape permits Circle, Square {}
 record Circle(double r) implements Shape {}
 record Square(double s) implements Shape {}

 static double area(Shape sh)
 {
  double a = switch (sh)
  {
   case Circle c -> Math.PI * c.r() * c.r();
   case Square sq when sq.s() > 0 -> sq.s() * sq.s();
   case Square sq -> 0.0;
  };
  return a;
 }

 // Slice 32: pattern-switch in return position.
 static String describe(Shape sh)
 {
  return switch (sh)
  {
   case Circle c -> "circle r=" + c.r();
   case Square sq -> "square s=" + sq.s();
  };
 }

 public static void main(String[] args)
 {
  System.out.println(area(new Circle(2.0)));
  System.out.println(describe(new Circle(3.0)));
  System.out.println(describe(new Square(4.0)));
 }
}
