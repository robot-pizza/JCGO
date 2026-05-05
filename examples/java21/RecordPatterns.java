// JCGO-SKIP: record patterns (deconstruction) are Java 21 (JEP 440).

public final class RecordPatterns
{

 record Point(int x, int y) {}
 record Box(Point lo, Point hi) {}

 static int area(Box b)
 {
  if (b instanceof Box(Point(var x1, var y1), Point(var x2, var y2)))
   return (x2 - x1) * (y2 - y1);
  return 0;
 }

 public static void main(String[] args)
 {
  System.out.println(area(new Box(new Point(0, 0), new Point(3, 4))));
 }
}
