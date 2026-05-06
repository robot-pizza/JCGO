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

 // Slice 37: pattern-switch in throw position. Routes through the
 // $matched-flag chain so the `when` guard can fall through to the
 // next arm if it's false.
 static int strictArea(Shape sh) throws Throwable
 {
  if (sh == null)
   throw switch (sh)
   {
    case Circle c when c.r() > 100.0 -> new RuntimeException("big-circle");
    case Circle c -> new IllegalArgumentException("circle-null?");
    case Square sq -> new IllegalArgumentException("square-null?");
   };
  return (int) area(sh);
 }

 public static void main(String[] args) throws Throwable
 {
  System.out.println(area(new Circle(2.0)));
  System.out.println(describe(new Circle(3.0)));
  System.out.println(describe(new Square(4.0)));
  System.out.println(strictArea(new Circle(2.0)));
 }
}
