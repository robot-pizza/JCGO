// non-sealed (Java 17). Extends slice 12 with the hyphenated keyword.

public final class NonSealed
{

 sealed interface Shape permits Circle, Square, Other {}

 static final class Circle implements Shape {}
 static final class Square implements Shape {}
 non-sealed static class Other implements Shape {}

 public static void main(String[] args)
 {
  Shape s = new Circle();
  System.out.println(s);
 }
}
