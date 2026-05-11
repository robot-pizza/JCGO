// Issue #148 (named-class variant): named (non-anonymous) class that
// directly `implements` a parameterized SAM
// (`class Holder implements Listener<Boolean>`). The first #148 fix
// covered the anonymous-class form (BridgeSynthesis was threaded
// through QualIdentNewInstanceTail); a real-world report against
// v1.20.0 surfaced that named classes implementing a parameterized
// interface ALSO bypass BridgeSynthesis, because
// ClassDeclaration only checked `lastExtendsHadTypeArgs`. Fix tracks
// `lastImplementsHadTypeArgs` in parallel.
//
// The two-impl shape pins this — devirtualization would mask a
// regression where the bridge isn't synthesized, so the call site
// has to flow through interface dispatch.
import java.util.ArrayList;
import java.util.List;

public final class NamedGenericSAM
{

 interface Listener<T>
 {
  void onValue(T v);
 }

 static final class Widget
 {
  Listener<Boolean> ear;
  void setEar(Listener<Boolean> l) { ear = l; }
  void fire(Boolean v) { if (ear != null) ear.onValue(v); }
 }

 static final class HolderA implements Listener<Boolean>
 {
  public void onValue(Boolean v) { System.out.println("A " + v); }
 }

 static final class HolderB implements Listener<Boolean>
 {
  public void onValue(Boolean v) { System.out.println("B " + v); }
 }

 public static void main(String[] args)
 {
  List<Widget> list = new ArrayList<Widget>();

  Widget w1 = new Widget();
  w1.setEar(new HolderA());
  list.add(w1);

  Widget w2 = new Widget();
  w2.setEar(new HolderB());
  list.add(w2);

  for (int i = 0; i < list.size(); i++)
  {
   list.get(i).fire(Boolean.TRUE);
  }
 }
}
