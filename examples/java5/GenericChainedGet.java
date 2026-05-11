// Quirk #2 (synthetic checkcast after List<T>.get etc.).
// `attrs.get(0).serialize()` errored as
// `Undefined: java.lang.Object.serialize(...)` because classpath-0.93
// declares `Object get(int)` rather than `E get(int)`.
// MethodInvocation.processPass1 wraps the inner generic-method call in
// a CastExpression based on the receiver's slice-50 captured args.
//
// Standards-pass P2: the substitution drives off real generic
// signatures via JdkGenericOverlay — Map<K,V>.get → V (the
// historically-deferred two-type-param case) now resolves correctly.

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GenericChainedGet
{

 static final class Attribute
 {
  String name;
  Attribute(String n) { this.name = n; }
  String serialize() { return "<" + name + "/>"; }
 }

 // The reported user pattern: chained generic-method return + typed
 // method call. Previously had to be written as
 // `((Attribute) attrs.get(0)).serialize()`.
 static String firstSerialized(List<Attribute> attrs)
 {
  return attrs.get(0).serialize();
 }

 // Field-access receiver to verify the substitution works on a
 // field-declared generic list, not just a local.
 static final class Holder
 {
  List<Attribute> items = new ArrayList<Attribute>();
 }

 static String holderHead(Holder h)
 {
  return h.items.get(0).serialize();
 }

 // Map<K,V>.get → V (the historically-deferred two-type-param case).
 static int mapValueLength(Map<String, Integer> m, String k)
 {
  return m.get(k).intValue();
 }

 public static void main(String[] args)
 {
  List<Attribute> attrs = new ArrayList<Attribute>();
  attrs.add(new Attribute("hi"));
  System.out.println(firstSerialized(attrs));

  Holder h = new Holder();
  h.items.add(new Attribute("hello"));
  System.out.println(holderHead(h));

  Map<String, Integer> m = new HashMap<String, Integer>();
  m.put("k", Integer.valueOf(42));
  System.out.println(mapValueLength(m, "k"));
 }
}
