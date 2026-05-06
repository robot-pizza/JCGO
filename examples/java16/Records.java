// Records (Java 16, JEP 395). Slice 11 covered the basic synthesis;
// slice 29 lets users declare body members — including a custom
// canonical constructor that replaces the synthesized default.

public final class Records
{

 record Point(int x, int y) {}

 // Slice 29: record with user-supplied body members.
 record Range(int lo, int hi)
 {
  // Custom canonical ctor — JCGO uses this instead of synthesizing.
  // (User is responsible for assigning the fields.)
  public Range(int lo, int hi)
  {
   if (lo > hi) throw new IllegalArgumentException("lo > hi");
   this.lo = lo;
   this.hi = hi;
  }

  // Extra method on the record.
  int span() { return hi - lo; }
 }

 // Slice 40: compact canonical constructor — no parens, parameters
 // come from the record header, implicit `this.x = x` assignments
 // are appended automatically.
 record Pair(int a, int b)
 {
  Pair
  {
   if (a > b) throw new IllegalArgumentException("a > b");
  }
  int sum() { return a + b; }
 }

 public static void main(String[] args)
 {
  Point p = new Point(3, 4);
  System.out.println(p.x() + p.y());

  Range r = new Range(2, 9);
  System.out.println(r.lo() + " .. " + r.hi() + " span=" + r.span());

  Pair pp = new Pair(3, 7);
  System.out.println(pp.a() + "+" + pp.b() + "=" + pp.sum());
 }
}
