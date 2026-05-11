// D2: nested chained generic calls.
// `outer.get(0).get(0).method()` for `List<List<X>>` — P3's
// synthesized CastExpression (Object → List) didn't carry the
// inner `<X>` args, so the next chained call lost the
// substitution context. MethodInvocation.synthCastArgs now stashes
// the inner generic-args slot on the synthesized cast, and the
// chained substitution reads it via getSynthCastArgs.

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NestedGenericChained
{

 static int firstLen(List<List<String>> outer)
 {
  return outer.get(0).get(0).length();
 }

 // Triple-nested.
 static int tripleLen(List<List<List<String>>> outer)
 {
  return outer.get(0).get(0).get(0).length();
 }

 // Map<K, List<V>>: m.get(k) returns List<V>, then .get(0) → V.
 static int mapOfListsHead(Map<String, List<String>> m, String k)
 {
  return m.get(k).get(0).length();
 }

 public static void main(String[] args)
 {
  List<List<String>> o = new ArrayList<List<String>>();
  List<String> inner = new ArrayList<String>();
  inner.add("hello");
  o.add(inner);
  System.out.println(firstLen(o));

  List<List<List<String>>> oo = new ArrayList<List<List<String>>>();
  oo.add(o);
  System.out.println(tripleLen(oo));

  Map<String, List<String>> m = new HashMap<String, List<String>>();
  m.put("k", inner);
  System.out.println(mapOfListsHead(m, "k"));
 }
}
