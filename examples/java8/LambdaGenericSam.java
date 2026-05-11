// Quirk #6 (lambda targeting a parameterized functional interface).
// Reported as a hard translator crash (AssertException) when a lambda
// targeted a SAM whose parameter type was a generic type-variable —
// e.g. `field.setOnChange(value -> ...)` for
// `interface ChangeListener<T> { void onChange(T value); }` and
// `setOnChange(ChangeListener<String> cb)`.
//
// Two-part fix:
//   1. preProcessLambdaArgs (MethodInvocation + InstanceCreation) now
//      threads the formal parameter's parser-captured generic args
//      (slice 50) into c.currentVarTypeArgsJls alongside
//      c.currentVarType.
//   2. LambdaSynthesis.buildClassBody substitutes T → matching arg in
//      the SAM's formal parameter types. When any substitution
//      happens, it runs BridgeSynthesis.wrap on the synthesized class
//      body so the typed onChange(String) gets an
//      onChange(Object) bridge — preserving the SAM-dispatch
//      override that the parent's erased signature requires.

public final class LambdaGenericSam
{

 interface ChangeListener<T>
 {
  void onChange(T value);
 }

 static final class TextField
 {
  ChangeListener<String> cb;
  void setOnChange(ChangeListener<String> cb) { this.cb = cb; }
  void fire(String v) { if (cb != null) cb.onChange(v); }
 }

 static int countChars(String s) { return s.length(); }

 public static void main(String[] args)
 {
  TextField f = new TextField();

  // The minimal repro from the user's report. Body uses `value` as
  // String — countChars(value) won't resolve unless the synthesized
  // formal parameter type was substituted from T to String.
  f.setOnChange(value -> { System.out.println(countChars(value)); });
  f.fire("hello");

  // Reassign so the bridge gets exercised twice with different
  // bodies.
  f.setOnChange(value -> System.out.println("got: " + value));
  f.fire("world");
 }
}
