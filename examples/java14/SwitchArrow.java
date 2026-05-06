// Arrow-case switch statements (Java 14). Slice 14a — the simpler
// half of switch expressions: arrow cases as statements, no yield,
// no switch in expression position.

public final class SwitchArrow
{

 public static void main(String[] args)
 {
  int n = args.length;
  switch (n)
  {
   case 0, 1 -> System.out.println("few");
   case 2, 3, 4 -> System.out.println("some");
   default -> System.out.println("many");
  }
 }
}
