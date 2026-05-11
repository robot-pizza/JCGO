// Issue #147: Map<K, Inner>.get(...) erases to Object, and javac would
// insert an implicit cast when assigning to a Inner-typed local. JCGO
// erases the call but didn't synthesize the cast, leaving the local's
// tracked actualType as Object. The subsequent call on that local then
// traces with curActualClass=Object and trips the
// methodTraceClassInit ourClass.isAssignableFrom(curActualClass)
// assertion when reached through a class-init group (static-final
// initializer or method called from one). Fix clamps actualType to
// the declared LHS when the RHS's actualType is plain Object.

import java.util.HashMap;
import java.util.Map;

public final class MapGetAssignThenCall
{

 static final class Rules
 {
  boolean accepts(String s) { return s != null && s.length() > 0; }
 }

 static final Map<String, Rules> RULES = new HashMap<String, Rules>();

 static
 {
  RULES.put("k", new Rules());
 }

 // Helper invoked from a static-final initializer below — class-init
 // tracing walks this body, so the assertion fires here when the bug
 // is present.
 public static boolean accepts(String parent, String child)
 {
  if (parent == null || child == null) return false;
  Rules r = RULES.get(parent);
  return r != null && r.accepts(child);
 }

 public static final boolean OK = accepts("k", "v");

 public static void main(String[] args)
 {
  System.out.println(OK);
 }
}
