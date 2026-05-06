// try-with-resources on existing variable (Java 9, JEP 213). Slice 27.

import java.io.StringReader;

public final class TwrExisting
{

 public static void main(String[] args) throws Exception
 {
  StringReader r = new StringReader("hi");
  try (r)
  {
   System.out.println(r.read());
   System.out.println(r.read());
  }
 }
}
