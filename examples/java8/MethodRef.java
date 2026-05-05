// JCGO-SKIP: method references (ClassName::method, instance::method,
// ClassName::new) are Java 8 (JLS 15.13). JCGO doesn't yet recognize "::".

import java.util.Arrays;

public final class MethodRef
{

 public static void main(String[] args)
 {
  Arrays.stream(args).map(String::trim).forEach(System.out::println);
 }
}
