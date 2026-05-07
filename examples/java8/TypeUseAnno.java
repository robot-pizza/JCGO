// Type-USE annotations (Java 8, JSR 308). Slice 44: parser accepts
// annotations at type-use positions and discards them. JCGO already
// parses-and-ignores annotations on declarators; this slice extends
// the same to types embedded inside generics, throws, and casts.

public final class TypeUseAnno
{

 static final class Box
 {
  Object v;
  Box(Object v) { this.v = v; }
  Object get() { return v; }
 }

 // Type-use annotation on a throws-clause type.
 public static void main(String[] args) throws @SuppressWarnings("x") Exception
 {
  // Type-use annotation on a local-var type.
  @SuppressWarnings("unused") String s = "hello";
  System.out.println(s);

  // Type-use annotation inside a generic argument (left-hand and
  // right-hand type-arg lists both go through consumeGenericArgs).
  Box<@SuppressWarnings("x") String> b =
      new Box<@SuppressWarnings("y") String>("world");
  System.out.println(b.get());

  // Type-use annotation in a cast position.
  Object o = (@SuppressWarnings("z") String) "cast-target";
  System.out.println(o);
 }
}
