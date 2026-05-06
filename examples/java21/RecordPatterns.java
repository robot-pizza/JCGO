// Record patterns (Java 21, JEP 440). Slice 16: deconstruction in
// instanceof and case labels.

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

 static String describe(Object o)
 {
  String result = switch (o)
  {
   case Point(int x, int y) -> "point " + x + "," + y;
   case Box(Point lo, Point hi) -> "box of points";
   default -> "?";
  };
  return result;
 }

 public static void main(String[] args)
 {
  System.out.println(area(new Box(new Point(0, 0), new Point(3, 4))));
  System.out.println(describe(new Point(5, 6)));
 }
}
