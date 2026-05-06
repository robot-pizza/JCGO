// Autoboxing / unboxing (Java 5, JLS 5.1.7-5.1.8). Slice 18 + 18b
// covers all four sites: variable initializer, `=` assignment, return
// statement, and method-call argument (overload-resolution-driven).

public final class Autobox
{

 static int unboxArg(Integer i)
 {
  int x = i;          // initializer unbox
  return x;
 }

 static Integer boxIt(int i)
 {
  return i;           // return-context box
 }

 static int twoBoxes(Integer x, Integer y)
 {
  return x + y;        // arithmetic-on-wrappers: unbox both operands
 }

 static boolean compare(Integer x, Integer y)
 {
  return x < y;        // ordering on wrappers: unbox both
 }

 static int firstObject(Object... xs)
 {
  Integer first = (Integer) xs[0];
  return first;
 }

 public static void main(String[] args)
 {
  Integer a = 42;     // initializer box
  int    b = a;       // initializer unbox
  Long   l = 100L;    // box long
  Boolean t = true;   // box boolean
  Double d = 3.14;    // box double
  Integer e;
  e = 99;             // assignment box
  int    f;
  f = e;              // assignment unbox
  Integer wrapped = boxIt(7);   // arg autobox: int -> Integer
  int unwrapped = unboxArg(33); // arg autobox: int -> Integer (formal)
  int paired = twoBoxes(10, 20);// arg autobox at both formal positions
  boolean ord = compare(3, 9);  // arg autobox + ordering unbox
  int firstOf = firstObject(1, 2, 3);  // varargs trailing autobox
  System.out.println(b);
  System.out.println(f);
  System.out.println(wrapped);
  System.out.println(unwrapped);
  System.out.println(paired);
  System.out.println(ord);
  System.out.println(firstOf);
 }
}
