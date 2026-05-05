// var in foreach (Java 10).

public final class VarForeach
{

 public static void main(String[] args)
 {
  int[] arr = new int[3];
  arr[0] = 10;
  arr[1] = 20;
  arr[2] = 30;
  int sum = 0;
  for (var v : arr)
   sum += v;
  System.out.println(sum);
 }
}
