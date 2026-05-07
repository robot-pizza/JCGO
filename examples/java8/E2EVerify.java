// E2E runtime-verification fixture covering features added by the
// modernization slices: bridge methods (covariant override), generic-
// signature retention (Method.getGenericReturnType returning a
// TypeVariable instead of the erased Number bound), and annotation
// reflection through the Proxy machinery (getDeclaredAnnotations on
// @Deprecated).
//
// Running this fixture (translate -> compile -> run) is the only way
// to catch runtime regressions in those code paths -- the source-
// levels smoke harness validates only that translation succeeds.
//
// PROXY_FORCING / the never-true Proxy.newProxyInstance call is a
// sentinel: JCGO's Proxy implementation requires a proxy class to be
// statically known at codegen time. Without the sentinel, codegen
// won't emit a proxy class for Deprecated, and runtime
// Proxy.newProxyInstance throws OutOfMemoryError. A future slice
// could walk the methodsAnnos / classAnnos / fieldsAnnos tables at
// codegen time and force proxy emission automatically; until then,
// programs that want runtime annotation reflection need this kind of
// sentinel for each annotation type they reflect over.

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

public class E2EVerify {

  interface Producer<T> {
    T produce();
  }

  public static class StringProducer implements Producer<String> {
    public String produce() { return "produced-string"; }
  }

  public <T extends Number> T pickFirst(T a, T b) { return a; }

  @Deprecated
  public void oldMethod() {}

  static final Class[] PROXY_FORCING = new Class[] { Deprecated.class };

  public static void main(String[] args) throws Exception {
    if (args.length > 100000) {
      java.lang.reflect.Proxy.newProxyInstance(null, PROXY_FORCING, null);
    }

    Producer p = new StringProducer();
    Object out = p.produce();
    System.out.println("bridge.produce returned: " + out);
    System.out.println("bridge.return is String? " + (out instanceof String));

    Method pf = E2EVerify.class.getMethod("pickFirst",
        new Class[]{ Number.class, Number.class });
    Type rt = pf.getGenericReturnType();
    System.out.println("pickFirst.getGenericReturnType class = "
        + rt.getClass().getName());
    if (rt instanceof TypeVariable) {
      System.out.println("pickFirst.getGenericReturnType name = "
          + ((TypeVariable) rt).getName());
    } else {
      System.out.println("pickFirst.getGenericReturnType (not a TypeVariable): "
          + rt);
    }

    Method old = E2EVerify.class.getMethod("oldMethod", new Class[0]);
    System.out.println("oldMethod isAnnotationPresent(Deprecated) = "
        + old.isAnnotationPresent(Deprecated.class));
    Annotation[] anns = old.getDeclaredAnnotations();
    System.out.println("oldMethod getDeclaredAnnotations.length = "
        + anns.length);
    if (anns.length > 0) {
      System.out.println("oldMethod ann[0].annotationType = "
          + anns[0].annotationType().getName());
    }
  }
}
