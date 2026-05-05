// JCGO-SKIP: var local-variable type inference is Java 10 (JEP 286).
// JCGO doesn't yet recognize var as a contextual keyword in
// LocalVariableType position.

public final class VarLocal
{

 public static void main(String[] args)
 {
  var greeting = "Hello, world";
  var count = 42;
  System.out.println(greeting + " (" + count + ")");
 }
}
