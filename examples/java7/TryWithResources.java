// JCGO-SKIP: try-with-resources is Java 7 (JLS 14.20.3); JCGO doesn't yet
// parse the resource list inside try, nor desugar to try-finally + close().

import java.io.FileReader;
import java.io.IOException;

public final class TryWithResources
{

 public static void main(String[] args) throws IOException
 {
  try (FileReader r = new FileReader("/etc/hostname"))
  {
   int c;
   while ((c = r.read()) != -1)
    System.out.print((char) c);
  }
 }
}
