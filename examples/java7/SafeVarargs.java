// @SafeVarargs (Java 7, JEP for varargs warning suppression).
// JCGO doesn't emit unchecked-conversion warnings, so this is purely
// parse-and-discard — the annotation's only job is to suppress
// warnings javac would otherwise generate. Slice 47 just adds the
// version gate and a fixture; the annotation itself rides through
// the existing AnnotationGroup parser.

import java.util.ArrayList;
import java.util.List;

public final class SafeVarargs
{

 @java.lang.SafeVarargs
 static <T> List<T> asList(T... items)
 {
  List<T> out = new ArrayList<T>();
  for (int i = 0; i < items.length; i++)
   out.add(items[i]);
  return out;
 }

 public static void main(String[] args)
 {
  List<String> xs = asList("a", "b", "c");
  System.out.println(xs.size());
  for (int i = 0; i < xs.size(); i++)
   System.out.println(xs.get(i));
 }
}
