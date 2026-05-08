// E2E runtime-verification fixture covering features added by the
// modernization slices: bridge methods (covariant override), generic-
// signature retention (Method.getGenericReturnType returning a
// TypeVariable instead of the erased Number bound), built-in
// annotation reflection (@Deprecated round-trip through the Proxy
// machinery), and custom @interface annotation types resolving as
// real classes that survive Class.forName / Proxy construction at
// runtime.
//
// Running this fixture (translate -> compile -> run) is the only way
// to catch runtime regressions in those code paths -- the source-
// levels smoke harness validates only that translation succeeds.

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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

  @MyTag
  public void taggedMethod() {}

  @WithDefaults(level = 99)
  public void partialDefaults() {}

  @WithDefaults
  public void allDefaults() {}

  public static void main(String[] args) throws Exception {
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

    Method tagged = E2EVerify.class.getMethod("taggedMethod", new Class[0]);
    System.out.println("taggedMethod isAnnotationPresent(MyTag) = "
        + tagged.isAnnotationPresent(MyTag.class));
    Annotation[] tagAnns = tagged.getDeclaredAnnotations();
    System.out.println("taggedMethod getDeclaredAnnotations.length = "
        + tagAnns.length);
    if (tagAnns.length > 0) {
      System.out.println("taggedMethod ann[0].annotationType = "
          + tagAnns[0].annotationType().getName());
    }

    Method partial = E2EVerify.class.getMethod("partialDefaults", new Class[0]);
    WithDefaults pd = (WithDefaults) partial.getAnnotation(WithDefaults.class);
    System.out.println("partialDefaults @WithDefaults.value = " + pd.value());
    System.out.println("partialDefaults @WithDefaults.level = " + pd.level());

    Method all = E2EVerify.class.getMethod("allDefaults", new Class[0]);
    WithDefaults ad = (WithDefaults) all.getAnnotation(WithDefaults.class);
    System.out.println("allDefaults @WithDefaults.value = " + ad.value());
    System.out.println("allDefaults @WithDefaults.level = " + ad.level());

    Method valueMember = WithDefaults.class.getMethod("value", new Class[0]);
    System.out.println("WithDefaults.value default = "
        + valueMember.getDefaultValue());
    Method levelMember = WithDefaults.class.getMethod("level", new Class[0]);
    System.out.println("WithDefaults.level default = "
        + levelMember.getDefaultValue());
  }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface MyTag {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface WithDefaults {
  String value() default "ok";
  int level() default 5;
}
