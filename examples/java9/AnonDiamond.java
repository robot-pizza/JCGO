// Anonymous-class diamond `new T<>() { ... }` (Java 9, JEP 213). Slice 28.

public final class AnonDiamond
{

 static abstract class Box<T>
 {
  Object value;
  Box(Object v) { this.value = v; }
  abstract Object describe();
 }

 public static void main(String[] args)
 {
  // Diamond on anonymous class — `<>` placeholder.
  Box<Integer> b = new Box<>(Integer.valueOf(42))
  {
   Object describe() { return "wrapped " + this.value; }
  };
  System.out.println(b.describe());
 }
}
