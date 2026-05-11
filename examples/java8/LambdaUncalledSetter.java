// Quirk #8 (AssertException on lambda-via-setter into non-generic SAM
// field). The minimal-repro case the user reported: a non-generic
// SAM (`Listener` with String parameter), a setter that just stores
// the lambda into a field, and a caller that never invokes the
// SAM. The lambda's body Term (a MethodInvocation) ends up
// co-owned by both LambdaExpression.terms[1] and the synthesized
// class's method body; when the synth method's body is never
// pass1'd (because the SAM is never reached), tree walks reaching
// the body through the LambdaExpression placeholder hit
// unprocessed nodes and assert.
//
// Fix: after lift, LambdaExpression nulls its own terms[0]/[1] to
// Empty.term and overrides discoverObjLeaks / writeStackObjs to
// delegate to lifted. All subsequent walks route exclusively
// through the lifted InstanceCreation.

public final class LambdaUncalledSetter
{

 public interface Listener { void onValue(String v); }

 public static class WinWidget
 {
  private Listener onChange;
  public void setOnChange(Listener l) { onChange = l; }
 }

 public static void main(String[] args)
 {
  // Lambda stored via setter, never invoked.
  new WinWidget().setOnChange(v -> System.out.println(v));

  // Local-var receiver + block body — same shape that earlier
  // QuirksLam tests covered but with non-generic Listener and
  // never-invoked SAM.
  WinWidget w = new WinWidget();
  w.setOnChange(v -> { System.out.println(v); });
 }
}
