// var in lambda parameters (Java 11, JEP 323). Slice 26.

public final class VarLambda
{

 interface IntBinOp
 {
  int apply(int a, int b);
 }

 public static void main(String[] args)
 {
  IntBinOp add = (var a, var b) -> a + b;
  System.out.println(add.apply(11, 22));

  IntBinOp mul = (var a, var b) -> a * b;
  System.out.println(mul.apply(3, 4));
 }
}
