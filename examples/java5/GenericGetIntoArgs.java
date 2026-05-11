// Issue #149: javac inserts an implicit checkcast at every use-site
// of a generic-method-return whose static type is a type-var
// (`List<T>.get(i) → T`). After erasure JCGO sees the call returning
// Object, and the value flows into positions that won't accept
// Object:
//   * as a METHOD ARGUMENT — `serialize(list.get(i))` looks up
//     `serialize(Object)` and fails when only `serialize(Control)`
//     exists.
//   * as a FIELD-ACCESS RECEIVER — `lines.get(0).indent` resolves
//     `indent` on Object and fails.
// Both must apply the same substitution that
// `trySubstituteChainedGenericReturn` does for the chained-call case
// (`list.get(i).foo()`). The fix calls it from
// MethodInvocation.substituteChainedGenericArgs (arg walk) and from
// PrimaryFieldAccess.processPass1 (field receiver) too.

import java.util.ArrayList;
import java.util.List;

public final class GenericGetIntoArgs
{

 static final class Holder
 {
  final int v;
  Holder(int v) { this.v = v; }
 }

 // Argument-position case: this overload doesn't exist for Object,
 // so without the cast the lookup fails.
 static int describe(Holder h)
 {
  return h.v;
 }

 public static void main(String[] args)
 {
  List<Holder> hs = new ArrayList<Holder>();
  hs.add(new Holder(7));
  hs.add(new Holder(42));

  int sum = 0;
  for (int i = 0; i < hs.size(); i++)
  {
   // Argument position — `hs.get(i)` erases to Object, but
   // `describe(Object)` doesn't exist. Without the implicit cast
   // the call fails to resolve.
   sum += describe(hs.get(i));
   // Field-access-receiver position — `hs.get(i).v` resolves `v`
   // on Object without the implicit cast.
   sum += hs.get(i).v;
  }

  System.out.println(sum);
 }
}
