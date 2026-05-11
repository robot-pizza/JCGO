// D6 + slice 8 follow-up: `for (var x : iter)` over an Iterable
// resolves the element type from the iter's slice-50 captured args
// (e.g. `List<String>` → x has type String). Without this,
// var-inference would pick Iterator.next()'s erased Object return.

import java.util.ArrayList;
import java.util.List;

public final class VarForeachIterable
{

 static int sumLens(List<String> xs)
 {
  int n = 0;
  for (var x : xs) n += x.length();
  return n;
 }

 public static void main(String[] args)
 {
  List<String> xs = new ArrayList<String>();
  xs.add("hi"); xs.add("there");
  System.out.println(sumLens(xs));
 }
}
