// Text blocks (Java 15, JEP 378). Slice 20 + 20b: indentation strip,
// `\s` for explicit space, and `\<line terminator>` for line continuation.

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

  String spaced = """
                  end-with-spaces:\s\s\s
                  """;
  System.out.println(spaced);

  String continued = """
                     one \
                     line""";
  System.out.println(continued);
 }
}
