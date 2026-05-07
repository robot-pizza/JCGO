// Slice 50 (pre-erasure retention): Method.getGenericReturnType /
// getGenericParameterTypes should return the original type
// variable, not Object after slice-45 erasure. This fixture
// exercises the API by calling getDeclaredMethods and inspecting
// the JLS signature emitted for `<T> T identity(T)`.
//
// Slice 50 (inner generic-arg retention): also exercises a method
// returning List<T> so the emitted signature contains the
// parameterized `Ljava/util/List<TT;>;` form rather than raw
// `Ljava/util/List;`.
//
// The smoke driver verifies translation succeeds; the runtime
// behavior needs a real C build and run to observe.

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.ArrayList;

public final class MethodGenericReflect
{

 static <T extends Number> T pick(T a, T b)
 {
  return a;
 }

 // Slice 50 inner generic-arg retention: exercises List<T> return.
 static <T> List<T> wrap(T x)
 {
  List<T> out = new ArrayList<T>();
  out.add(x);
  return out;
 }

 public static void main(String[] args) throws Exception
 {
  Class self = MethodGenericReflect.class;
  Method[] ms = self.getDeclaredMethods();
  for (int i = 0; i < ms.length; i++)
  {
   Method m = ms[i];
   String name = m.getName();
   if (!name.equals("pick") && !name.equals("wrap"))
    continue;
   Type ret = m.getGenericReturnType();
   Type[] params = m.getGenericParameterTypes();
   System.out.println(name + ".returnType=" + ret);
   for (int j = 0; j < params.length; j++)
    System.out.println(name + ".param" + j + "=" + params[j]);
  }
 }
}
