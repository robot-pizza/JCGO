// Issue #148: anonymous inner class implementing a parameterized SAM
// (`new Listener<String>() {...}`). The user method declares the
// narrowed parameter type (`onValue(String)`), but the erased SAM is
// `onValue(Object)`. javac synthesizes a bridge `onValue(Object) →
// onValue(String)` automatically; JCGO's BridgeSynthesis used to do
// this only for top-level class declarations, so anon-class bodies
// ended up with a null vtable slot for the SAM. Interface dispatch
// through Listener then crashed at runtime (ACCESS_VIOLATION) when
// JCGO couldn't devirtualize — which happens whenever there are
// multiple impls flowing through the same field. The two-impl
// fire(boolean) below pins this: any regression where the bridge
// isn't synthesized will leave one (or both) lifted classes with a
// null onValue slot and the runtime call will fault.

import java.util.ArrayList;
import java.util.List;

public final class AnonGenericSAM
{

 interface Listener<T>
 {
  void onValue(T v);
 }

 static final class Widget
 {
  Listener<String> ear;
  void setEar(Listener<String> l) { ear = l; }
  void fire(String v) { if (ear != null) ear.onValue(v); }
 }

 public static void main(String[] args)
 {
  List<Widget> list = new ArrayList<Widget>();
  final String tag1 = "h1";
  final String tag2 = "h2";

  Widget w1 = new Widget();
  w1.setEar(new Listener<String>()
  {
   public void onValue(String v) { System.out.println(tag1 + " " + v); }
  });
  list.add(w1);

  Widget w2 = new Widget();
  w2.setEar(new Listener<String>()
  {
   public void onValue(String v) { System.out.println(tag2 + " " + v); }
  });
  list.add(w2);

  for (int i = 0; i < list.size(); i++)
  {
   list.get(i).fire("x");
  }
 }
}
