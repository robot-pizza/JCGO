// Text blocks (Java 15, JEP 378). Slice 20.

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
