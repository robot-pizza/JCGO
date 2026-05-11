// Issue #150: a lambda stored in a long-lived field via a setter was
// being stack-allocated by JCGO's escape analysis. The
// LambdaExpression placeholder forwarded `discoverObjLeaks` to its
// lifted InstanceCreation, but NOT `setObjLeaks` — so when the
// enclosing Argument decided the arg escaped (assigned to a heap
// field) and called setObjLeaks(null), Term's default no-op
// swallowed it and the lifted InstanceCreation's stack-eligibility
// flag stayed set. The C output then JCGO_STACKOBJ_NEW'd the lambda
// in the caller's frame, the setter stored a stack pointer in the
// receiver's field, the creating function returned, the stack
// memory was reused, and any subsequent invocation through the
// field's vtable dereferenced freed stack memory.
//
// In editor-side reproductions this surfaced as ACCESS_VIOLATION at
// WinTextField.fireChange+0x47 (reading methods->onChange__Lo on a
// dangling stack pointer); in a stripped-down case the field reads
// as a zeroed function pointer and the call faults with PC=0.
//
// Fix delegates setObjLeaks (and the parallel setStackObjVolatile)
// from LambdaExpression to lifted. This fixture exercises the
// pattern: a helper builds a Widget, installs a lambda via a
// setter, then returns the Widget. Main fires it after the helper
// returned — verifying the lambda survives.

public final class LambdaStackEscapeViaSetter
{

 interface Listener
 {
  void onValue(String v);
 }

 static class Widget
 {
  Listener onChange;
  void setOnChange(Listener l) { onChange = l; }
  void fire(String v) { if (onChange != null) onChange.onValue(v); }
 }

 /** Helper returns a Widget whose onChange field holds a lambda.
  *  The lambda must outlive this helper's return — if it's
  *  stack-allocated, the stored pointer dangles. */
 static Widget makeWidget(String tag)
 {
  Widget w = new Widget();
  w.setOnChange(v -> System.out.println(tag + ": " + v));
  return w;
 }

 public static void main(String[] args)
 {
  Widget a = makeWidget("alpha");
  Widget b = makeWidget("beta");
  // Force the stack memory used by makeWidget to be reused before
  // we fire — otherwise the stack-allocated lambda might still be
  // sitting at the same address by luck.
  String pad = "";
  for (int i = 0; i < 64; i++)
  {
   pad = pad + "x";
  }
  a.fire("one");
  b.fire("two");
  System.out.println(pad.length());
 }
}
