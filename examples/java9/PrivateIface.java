// Private interface methods (Java 9, JEP 213). Slice 21.
// Lets default methods share a private helper without exposing it
// on the interface's public API.

public final class PrivateIface
{

 interface Greeter
 {
  default String hi()  { return prefix() + "hi"; }
  default String bye() { return prefix() + "bye"; }
  private String prefix() { return "[" + this + "] "; }
 }

 static final class Lefty implements Greeter
 {
  public String toString() { return "L"; }
 }

 public static void main(String[] args)
 {
  Greeter g = new Lefty();
  System.out.println(g.hi());
  System.out.println(g.bye());
 }
}
