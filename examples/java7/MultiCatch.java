// Multi-catch (Java 7, JLS 14.20). Desugared at parse time into a chain
// of single-type catches sharing the body Term.

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
