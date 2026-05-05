
public final class Foreach
{

 public static void main(String[] args)
 {
  int[] arr = new int[3];
  arr[0] = 1;
  arr[1] = 2;
  arr[2] = 3;
  int sum = 0;
  for (int x : arr)
   sum += x;
  System.out.println(sum);
 }
}
