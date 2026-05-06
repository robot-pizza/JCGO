// Method references (Java 8, JLS 15.13). Covered: bound-instance and
// static (`Integer::parseInt`), constructor (`Foo::new`), dotted-id
// receivers (`System.out::println`), and unbound-instance refs where
// the SAM's first param becomes the receiver (`Box::get` for
// `Function<Box, Integer>`-shape SAMs).

public final class MethodRef
{

 interface Parser
 {
  int parse(String s);
 }

 interface Maker
 {
  Box make(int v);
 }

 interface Sink
 {
  void accept(java.lang.Object x);
 }

 // Slice 24f: SAM where the first param is the receiver target.
 interface BoxGetter
 {
  int get(Box b);
 }

 static final class Box
 {
  final int v;
  Box(int v) { this.v = v; }
  int get() { return v; }
 }

 public static void main(String[] args)
 {
  Parser p = Integer::parseInt;
  System.out.println(p.parse("42"));
  System.out.println(p.parse("100"));

  Maker m = Box::new;
  Box b = m.make(7);
  System.out.println(b.get());

  Sink s = System.out::println;
  s.accept("dotted-receiver");

  // Unbound-instance method ref — `b` is supplied as the receiver
  // by the SAM's first argument at call time.
  BoxGetter getter = Box::get;
  System.out.println(getter.get(b));
 }
}
