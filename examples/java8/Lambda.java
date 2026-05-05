// JCGO-SKIP: lambdas + functional interfaces are Java 8 (JLS 15.27).
// JCGO doesn't yet parse "->" lambda forms or desugar to inner classes.

public final class Lambda
{

 interface IntBinaryOp
 {
  int apply(int a, int b);
 }

 public static void main(String[] args)
 {
  IntBinaryOp add = (a, b) -> a + b;
  IntBinaryOp mul = (a, b) -> a * b;
  System.out.println(add.apply(2, 3));
  System.out.println(mul.apply(4, 5));
 }
}
