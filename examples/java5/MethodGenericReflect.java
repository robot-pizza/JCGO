// Slice 50 (pre-erasure retention): Method.getGenericReturnType /
// getGenericParameterTypes should return the original type
// variable, not Object after slice-45 erasure. This fixture
// exercises the API by calling getDeclaredMethods and inspecting
// the JLS signature emitted for `<T> T identity(T)`.
//
// The smoke driver verifies translation succeeds; the runtime
// behavior needs a real C build and run to observe.

import java.lang.reflect.Method;
import java.lang.reflect.Type;

public final class MethodGenericReflect
{

 static <T extends Number> T pick(T a, T b)
 {
  return a;
 }

 public static void main(String[] args) throws Exception
 {
  Class self = MethodGenericReflect.class;
  Method[] ms = self.getDeclaredMethods();
  for (int i = 0; i < ms.length; i++)
  {
   Method m = ms[i];
   if (!m.getName().equals("pick"))
    continue;
   Type ret = m.getGenericReturnType();
   Type[] params = m.getGenericParameterTypes();
   System.out.println("returnType=" + ret);
   for (int j = 0; j < params.length; j++)
    System.out.println("param" + j + "=" + params[j]);
  }
 }
}
