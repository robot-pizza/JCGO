// D3: subclass with fixed type-args. `class SL extends
// ArrayList<String>` then `SL sl; sl.get(0).length()` — at use,
// the variable `sl` has no captured generic args (SL is itself
// unparameterized), so the slice-50 receiver-side heuristic
// couldn't substitute. Parser's ExtendsType now retains the
// captured `<String>` on the extends QualifiedName, and
// MethodInvocation.findInheritedCapturedArgs walks the receiver
// class's superclass chain to find it.

import java.util.ArrayList;
import java.util.HashMap;

public final class SubclassFixedTypeArgs
{

 // Single-level extends with one fixed type-arg.
 static final class StringList extends ArrayList<String> {}

 // Single-level extends with two fixed type-args (Map<K, V> →
 // both K and V fixed).
 static final class IntsByName extends HashMap<String, Integer> {}

 public static void main(String[] args)
 {
  StringList sl = new StringList();
  sl.add("hello");
  System.out.println(sl.get(0).length());   // 5

  IntsByName m = new IntsByName();
  m.put("k", Integer.valueOf(42));
  System.out.println(m.get("k").intValue()); // 42
 }
}
