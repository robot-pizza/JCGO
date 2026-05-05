// JCGO-SKIP: text blocks (triple-quoted multi-line strings) are Java 15
// (JEP 378). JCGO's lexer treats """..."""" as three string literals.

public final class TextBlock
{

 public static void main(String[] args)
 {
  String json = """
                {
                  "greeting": "hello",
                  "count": 42
                }
                """;
  System.out.println(json);
 }
}
