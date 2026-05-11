// Quirk #3 (cast position): `(List<String>) x`, `(Map<?, ?>) y`,
// `(List<?>) z`. JCGO previously rejected these at parse time
// because the cast was routed through the expression parser, where
// `<` reads as a relational operator and `?`/`extends` aren't valid.
// UnaryWithPara now peeks ahead for the cast shape and routes the
// type through SimpleType (which already accepts wildcards / nested
// args via captureGenericArgsToJls).

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GenericCast
{

 // (List<String>) cast.
 static int firstLen(Object o)
 {
  List<String> xs = (List<String>) o;
  return xs.size();
 }

 // (Map<?, ?>) wildcard cast — the form the user reported.
 static int wildSize(Object root)
 {
  Map<?, ?> n = (Map<?, ?>) root;
  return n.size();
 }

 // (List<?>) — single wildcard.
 static int singleWildSize(Object o)
 {
  List<?> li = (List<?>) o;
  return li.size();
 }

 public static void main(String[] args)
 {
  List<String> xs = new ArrayList<String>();
  xs.add("a"); xs.add("b");
  System.out.println(firstLen(xs));        // 2

  Map<String, Integer> m = new HashMap<String, Integer>();
  m.put("x", Integer.valueOf(7));
  System.out.println(wildSize(m));         // 1

  System.out.println(singleWildSize(xs));  // 2
 }
}
