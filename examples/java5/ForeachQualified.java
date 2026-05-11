// Quirk #4 (foreach iteration variable type as qualified name).
// `for (java.util.Map.Entry<...> e : ...)` and similar — the slice-1
// looksLikeForeach lookahead originally accepted only a single-id
// type, so `.` in the qualified name aborted detection and the
// classic-for path took over with a "; expected" parse error. The
// extended lookahead now walks dotted-id chains and optional `<...>`
// generic args so any well-formed type works in foreach-header
// position.

import java.util.ArrayList;
import java.util.List;

public final class ForeachQualified
{

 static int sumLengths(List<java.lang.String> xs)
 {
  int n = 0;
  for (java.lang.String s : xs) {
   n += s.length();
  }
  return n;
 }

 // Parameterized type as iteration variable type. Avoids
 // chained .get(i).method() — that's quirk #2, a separate gap.
 static int sumOfSizes(List<List<java.lang.String>> outer)
 {
  int n = 0;
  for (List<java.lang.String> inner : outer) {
   n += inner.size();
  }
  return n;
 }

 public static void main(String[] args)
 {
  List<java.lang.String> xs = new ArrayList<java.lang.String>();
  xs.add("hello");
  xs.add("ab");
  System.out.println(sumLengths(xs));

  List<List<java.lang.String>> outer = new ArrayList<List<java.lang.String>>();
  outer.add(xs);
  outer.add(new ArrayList<java.lang.String>());
  System.out.println(sumOfSizes(outer));
 }
}
