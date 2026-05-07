// Generic type-parameter erasure (Java 5 / slice 45). Type-parameter
// references in field types, method return types, parameter types,
// throws clauses, and local-var types erase to java.lang.Object so
// the generic class actually translates instead of failing pass1
// with "Cannot find class: T".

public final class GenericFields
{

 // Generic class with type-param used in field and method signatures.
 static final class Box<T>
 {
  T v;
  Box(T v) { this.v = v; }
  T get() { return v; }
  void set(T v) { this.v = v; }
 }

 // Generic interface — same erasure rules apply.
 interface Holder<T>
 {
  T held();
 }

 // Generic method — local var, return, and param types all erase.
 static <U> U identity(U x)
 {
  U y = x;
  return y;
 }

 // Two type params, one used in nested generic type-arg position.
 static <K, V> V lookup(K key, V fallback)
 {
  if (key == null) return fallback;
  return fallback;
 }

 public static void main(String[] args)
 {
  Box<String> sb = new Box<String>("hello");
  System.out.println(sb.get());
  sb.set("world");
  System.out.println(sb.get());

  // Anonymous inner class implementing the generic interface.
  Holder<Integer> h = new Holder<Integer>() {
    public Integer held() { return Integer.valueOf(42); }
  };
  System.out.println(h.held());

  // Generic method invocation.
  System.out.println(identity("via-generic-method"));
  System.out.println(lookup(null, "fallback"));
 }
}
