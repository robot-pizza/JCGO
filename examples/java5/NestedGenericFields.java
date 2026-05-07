// Slice 50 (inner generic-arg retention): `List<T> field` should
// emit with parameterized form, not raw `Ljava/util/List;`. Same
// for return types and parameters.
//
// Smoke check: ensure translation succeeds. Runtime correctness of
// getGenericReturnType / getGenericType needs a real C build.

import java.util.List;
import java.util.ArrayList;

public final class NestedGenericFields
{

 static class Holder<T>
 {
  List<T> items;

  Holder() { items = new ArrayList<T>(); }

  List<T> getAll() { return items; }
 }

 public static void main(String[] args)
 {
  Class self = NestedGenericFields.class;
  System.out.println(self.getName());
 }
}
