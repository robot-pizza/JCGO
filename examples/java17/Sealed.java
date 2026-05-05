// Sealed classes/interfaces (Java 17). Slice 12: parse-and-discard —
// JCGO doesn't enforce the sealed boundary, but the syntax accepts.

public final class Sealed
{

 sealed interface Shape permits Circle, Square {}

 record Circle(double r) implements Shape {}
 record Square(double s) implements Shape {}

 public static void main(String[] args)
 {
  Shape s = new Circle(2.0);
  System.out.println(s);
 }
}
