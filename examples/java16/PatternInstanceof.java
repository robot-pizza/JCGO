// JCGO-SKIP: pattern matching for instanceof is Java 16 (JEP 394).
// JCGO doesn't yet parse the binding identifier after the type.

public final class PatternInstanceof
{

 public static void main(String[] args)
 {
  Object o = args.length > 0 ? (Object) args[0] : Integer.valueOf(42);
  if (o instanceof String s)
   System.out.println("string of length " + s.length());
  else if (o instanceof Integer i)
   System.out.println("int " + i);
 }
}
