// try-with-resources (Java 7, JLS 14.20.3). Slice 13 MVP.

import java.io.FileReader;
import java.io.IOException;

public final class TryWithResources
{

 public static void main(String[] args) throws IOException
 {
  if (args.length == 0)
   return;
  try (FileReader r = new FileReader(args[0]))
  {
   int c = r.read();
   System.out.println(c);
  }
 }
}
