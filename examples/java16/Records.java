// Records (Java 16, JEP 395). Slice 11 MVP: synthesizes private final
// fields, canonical ctor, and accessors.

public final class Records
{

 record Point(int x, int y) {}

 public static void main(String[] args)
 {
  Point p = new Point(3, 4);
  System.out.println(p.x() + p.y());
 }
}
