
public final class Annotations
{

 @SuppressWarnings({ "unchecked", "rawtypes" })
 public static void main(String[] args)
 {
  legacy();
 }

 @Deprecated
 @SuppressWarnings(value = "deprecation")
 private static void legacy()
 {
  System.out.println("legacy");
 }
}
