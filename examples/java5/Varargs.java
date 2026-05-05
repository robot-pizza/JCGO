
public final class Varargs
{

 public static void main(String[] args)
 {
  int[] arr = new int[3];
  arr[0] = 1;
  arr[1] = 2;
  arr[2] = 3;
  System.out.println(sum(arr));
 }

 private static int sum(int... vals)
 {
  int total = 0;
  for (int v : vals)
   total += v;
  return total;
 }
}
