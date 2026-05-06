// Numeric underscores and binary literals (Java 7, JEP 213). Slice 17.

public final class NumericUnderscores
{

 public static void main(String[] args)
 {
  int million = 1_000_000;
  int hexMask = 0xFF_FF_00_00;
  int bits    = 0b1010_1010;
  long big    = 1_234_567_890_123L;
  System.out.println(million);
  System.out.println(hexMask);
  System.out.println(bits);
  System.out.println(big);
 }
}
