// Slice 49: class-level annotation retention. The @MyTag annotation
// on AnnotationReflect should be captured by the parser and emitted
// into the static __class struct so VMClass / Class.isAnnotationPresent
// can later look it up by name.
//
// We exercise codegen only: the test asserts that the .c output
// contains a non-NULL annotation array on the class struct. Runtime
// isAnnotationPresent verification needs a real C build + run; the
// piece we can test in the smoke suite is that translation
// completes without resolving the annotation type as a class.

@MyTag
public final class AnnotationReflect
{

 public static void main(String[] args)
 {
  // Force emission of the static __class struct so codegen runs
  // through the slice-49 annotation array path. The println uses
  // getName() which goes through the Class object.
  System.out.println(AnnotationReflect.class.getName());
 }
}

@interface MyTag { }
