// Enums (Java 5, JLS 8.9). Slice 19 MVP: zero-arg constants only.

public final class Enums
{

 enum Color { RED, GREEN, BLUE }

 static String describe(Color c)
 {
  if (c == Color.RED)   return "red";
  if (c == Color.GREEN) return "green";
  return "other";
 }

 public static void main(String[] args)
 {
  Color a = Color.RED;
  System.out.println(a.name());
  System.out.println(a.ordinal());
  System.out.println(describe(a));
  System.out.println(describe(Color.GREEN));
  System.out.println(describe(Color.BLUE));
  Color[] all = Color.values();
  System.out.println(all.length);
  Color found = Color.valueOf("BLUE");
  System.out.println(found.ordinal());
 }
}
