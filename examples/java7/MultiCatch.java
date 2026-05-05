// JCGO-SKIP: multi-catch (catch (A | B e)) is Java 7 (JLS 14.20).
// JCGO's catch grammar accepts only a single exception type today.

public final class MultiCatch
{

 public static void main(String[] args)
 {
  try
  {
   if (args.length == 0)
    throw new IllegalStateException("no args");
   Integer.parseInt(args[0]);
  }
  catch (IllegalStateException | NumberFormatException e)
  {
   System.out.println("caught: " + e.getMessage());
  }
 }
}
