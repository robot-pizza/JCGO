// Method references (Java 8, JLS 15.13). Slice 23c handles
// `Integer::parseInt` and `Foo::new`; slice 24c grew the parser to
// also accept dotted-id receivers like `System.out::println`.

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

  // Dotted-id receiver — `System.out` resolves at pass1 the same way
  // a qualified static field reference does.
  Sink s = System.out::println;
  s.accept("dotted-receiver");
 }
}
