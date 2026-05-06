// Method references (Java 8, JLS 15.13). Slice 23c MVP: identifier
// receiver only (e.g. Integer::parseInt, Foo::new). Dotted-receiver
// forms like System.out::println are deferred — they need fuller
// receiver-expression parsing.

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
 }
}
