// var local-variable type inference (Java 10, JEP 286). Detected at
// LocalVariableDecl.processPass1 by walking past the type term and
// matching dottedName "var"; pre-processes the initializer and binds
// c.typeClassDefinition / c.typeDims from its inferred ExpressionType.

public final class VarLocal
{

 public static void main(String[] args)
 {
  var greeting = "Hello, world";
  var count = 42;
  System.out.println(greeting + " (" + count + ")");
 }
}
