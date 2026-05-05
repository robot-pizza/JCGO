
public final class Varargs
{

 public static void main(String[] args)
 {
  System.out.println(sum(1, 2, 3));
  System.out.println(sum());
  int[] explicit = new int[2];
  explicit[0] = 10;
  explicit[1] = 20;
  System.out.println(sum(explicit));
 }

 private static int sum(int... vals)
 {
  int total = 0;
  for (int v : vals)
   total += v;
  return total;
 }
}
