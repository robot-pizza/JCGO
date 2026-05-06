// Autoboxing / unboxing (Java 5, JLS 5.1.7-5.1.8). Slice 18 MVP:
// covers assignment and return contexts (variable initializer,
// `=` assignment, return statement). Method-argument autoboxing is
// a follow-up slice — simpler fixture avoids it.

public final class Autobox
{

 static int unboxIt(Integer i)
 {
  int x = i;          // unbox Integer -> int
  return x;
 }

 static Integer boxIt(int i)
 {
  return i;           // return-context box
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
  Integer wrapped = boxIt(7);   // return-context box
  int unwrapped = unboxIt(e);    // return-context unbox
  System.out.println(b);
  System.out.println(f);
  System.out.println(wrapped);
  System.out.println(unwrapped);
 }
}
