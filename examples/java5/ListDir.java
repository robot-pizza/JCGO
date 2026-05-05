import java.io.File;

public class ListDir
{
 public static void main(String args[])
  throws java.io.IOException
 {
  File dir = new File(args.length > 0 ? args[0] : ".");
  String list[] = dir.list();
  if (list != null)
  {
   for (String name : list)
   {
    File f = new File(dir, name);
    System.out.println("[" + f.getName() + "] " +
     (f.exists() ? "E" : "-") + (f.isFile() ? "F" : "-") +
     (f.isDirectory() ? "D" : "-") + (f.isHidden() ? "H" : "-") +
     (f.canRead() ? "R" : "-") + (f.canWrite() ? "W" : "-") + " " +
     f.length() + " bytes, " +
     new java.util.Date(f.lastModified()).toGMTString() + ", " +
     f.getCanonicalPath());
   }
  }
   else System.out.println("<<failed>>");
 }
}
