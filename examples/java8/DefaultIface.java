public final class DefaultIface {
 interface Greeter {
  default String hello() { return "hi"; }
 }
 static class English implements Greeter {}
 public static void main(String[] args) {
  System.out.println(new English().hello());
 }
}
