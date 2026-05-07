// JCGO Scanner read source as UTF-8 (was platform default, which
// breaks emoji on Windows). Each surrogate of an emoji code unit
// must arrive in the AST + generated C as the correct \uXXXX value.

public final class Emoji
{

 public static void main(String[] args)
 {
  // 🎉 = U+1F389 = surrogate pair D83C DF89
  String s = "🎉ok";
  // Should print 5 — two surrogate chars + 'o' + 'k' + (well, 4).
  // Actually: 🎉 = 2 chars, o = 1, k = 1, total = 4.
  System.out.println(s.length());
  System.out.println((int) s.charAt(0));  // 0xD83C = 55356
  System.out.println((int) s.charAt(1));  // 0xDF89 = 57225
 }
}
