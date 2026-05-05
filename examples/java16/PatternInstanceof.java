// Pattern matching for instanceof (Java 16, JEP 394).

public final class PatternInstanceof
{

 public static void main(String[] args)
 {
  Object o = "hello world";
  if (o instanceof String s)
   System.out.println("got string of length " + s.length());
  else
   System.out.println("not a string");
 }
}
