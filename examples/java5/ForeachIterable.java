// D6: for-each over a real `Iterable` now translates via the
// iterator desugar (`for (Iterator $it = iter.iterator(); $it.hasNext();)
// { T x = (T) $it.next(); body }`), matching javac's bytecode-level
// lowering. The earlier slice-1 ForeachStatement only emitted an
// array-walk desugar with an unsafe `(jObjectArr) iter` cast, which
// silently miscompiled — translation succeeded but the generated C
// crashed at runtime trying to access .length on a non-array.
//
// ForeachStatement.processPass1 now inspects iter's exprType:
// arrays keep the original lowering, Iterable-implementing types
// route through the iterator desugar with a synthesized CHECKCAST
// on the element.

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ForeachIterable
{

 // Most common shape: List<T> + typed element variable.
 static int sumLens(List<String> xs)
 {
  int n = 0;
  for (String x : xs) n += x.length();
  return n;
 }

 // Set<T> — different Iterable implementer.
 static int sumLensSet(Set<String> xs)
 {
  int n = 0;
  for (String x : xs) n += x.length();
  return n;
 }

 public static void main(String[] args)
 {
  List<String> xs = new ArrayList<String>();
  xs.add("hello"); xs.add("ab");
  System.out.println(sumLens(xs));

  Set<String> ys = new HashSet<String>();
  ys.add("xyz");
  System.out.println(sumLensSet(ys));
 }
}
