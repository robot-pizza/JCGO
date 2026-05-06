// Enums (Java 5, JLS 8.9). Slices 19 + 19b + 19c: zero-arg constants,
// constants with constructor args, and constants with anonymous bodies.

public final class Enums
{

 enum Color { RED, GREEN, BLUE }

 enum Light
 {
  GREEN(0x00ff00), AMBER(0xffaa00), RED(0xff0000);

  private final int rgb;

  Light(int rgb)
  {
   this.rgb = rgb;
  }

  public int rgb()
  {
   return rgb;
  }
 }

 static String describe(Color c)
 {
  if (c == Color.RED)   return "red";
  if (c == Color.GREEN) return "green";
  return "other";
 }

 enum Op
 {
  PLUS
  {
   public int apply(int a, int b) { return a + b; }
  },
  TIMES
  {
   public int apply(int a, int b) { return a * b; }
  };

  public abstract int apply(int a, int b);
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

  Light amber = Light.AMBER;
  System.out.println(amber.name());
  System.out.println(amber.rgb());
  System.out.println(Light.RED.rgb());

  System.out.println(Op.PLUS.apply(3, 4));
  System.out.println(Op.TIMES.apply(3, 4));
 }
}
