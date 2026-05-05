
import static java.lang.Math.PI;

public final class StaticImport
{

 public static void main(String[] args)
 {
  // Slice 3a accepts the syntax but does not yet resolve unqualified
  // statically-imported names — qualified Math.PI works regardless.
  System.out.println(Math.PI);
 }
}
