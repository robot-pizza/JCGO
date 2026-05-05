// JCGO-SKIP: record declarations are Java 16 (JEP 395). JCGO doesn't yet
// parse "record" as a contextual keyword in class-decl position, nor
// synthesize the canonical constructor / accessors / equals / hashCode /
// toString implied by a record header.

public final class Records
{

 record Point(int x, int y) {}

 public static void main(String[] args)
 {
  Point p = new Point(3, 4);
  System.out.println(p);
  System.out.println(p.x() + p.y());
 }
}
