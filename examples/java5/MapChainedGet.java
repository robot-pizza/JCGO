// Standards-pass P2+P3: Map<K, V>.get → V resolves correctly via
// JdkGenericOverlay. classpath-0.93's Map.get is declared as
// `Object get(Object)` (pre-generics), so slice-50 retention can't
// pick up the return-type-var. The overlay registers
// `java.util.Map#get(Ljava/lang/Object;)` → "V" so
// MethodInvocation.processPass1 substitutes V from the receiver's
// captured generic args.

import java.util.HashMap;
import java.util.Map;

public final class MapChainedGet
{

 static int valueLength(Map<String, Integer> m, String k)
 {
  // Chained generic-method call — `m.get(k)` static return is
  // Object (erased), specialized return is Integer.
  return m.get(k).intValue();
 }

 // Map<String, String>.get → String — also two-arg, but value type
 // is reference. Pins that the substitution picks index 1 (V) not
 // index 0 (K) — a wrong substitution would emit (String) on a
 // get-keyed call and silently miscompile, so this fixture is the
 // safety net.
 static int firstChar(Map<String, String> m, String k)
 {
  return m.get(k).length();
 }

 public static void main(String[] args)
 {
  Map<String, Integer> mi = new HashMap<String, Integer>();
  mi.put("k", Integer.valueOf(42));
  System.out.println(valueLength(mi, "k"));

  Map<String, String> ms = new HashMap<String, String>();
  ms.put("k", "hello");
  System.out.println(firstChar(ms, "k"));
 }
}
