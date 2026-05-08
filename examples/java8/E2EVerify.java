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

  public void receivesAnnotated(@MyTag String first,
                                 @WithDefaults(level = 7) String second) {}

  @Tag("alpha")
  @Tag("beta")
  public void multiTagged() {}

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

    Annotation[] myTagMeta = MyTag.class.getDeclaredAnnotations();
    System.out.println("MyTag.getDeclaredAnnotations.length = " + myTagMeta.length);
    for (int i = 0; i < myTagMeta.length; i++) {
      System.out.println("  myTagMeta[" + i + "] = " + myTagMeta[i].annotationType().getName());
    }
    System.out.println("MyTag.isAnnotation = " + MyTag.class.isAnnotation());
    System.out.println("WithDefaults.isAnnotation = "
        + WithDefaults.class.isAnnotation());
    System.out.println("E2EVerify.isAnnotation = "
        + E2EVerify.class.isAnnotation());

    System.out.println("Child.isAnnotationPresent(Family) = "
        + Child.class.isAnnotationPresent(Family.class));
    System.out.println("Child.isAnnotationPresent(NotInherited) = "
        + Child.class.isAnnotationPresent(NotInherited.class));
    System.out.println("Parent.isAnnotationPresent(Family) = "
        + Parent.class.isAnnotationPresent(Family.class));

    Method mt = E2EVerify.class.getMethod("multiTagged", new Class[0]);
    Annotation[] tagsByType = mt.getAnnotationsByType(Tag.class);
    System.out.println("multiTagged getAnnotationsByType(Tag).length = "
        + tagsByType.length);
    for (int i = 0; i < tagsByType.length; i++) {
      System.out.println("  tag[" + i + "] = " + ((Tag) tagsByType[i]).value());
    }

    Method ra = E2EVerify.class.getMethod("receivesAnnotated",
        new Class[]{ String.class, String.class });
    Annotation[][] params = ra.getParameterAnnotations();
    System.out.println("receivesAnnotated paramCount = " + params.length);
    for (int i = 0; i < params.length; i++) {
      System.out.println("  param[" + i + "].length = " + params[i].length);
      for (int j = 0; j < params[i].length; j++) {
        System.out.println("    [" + i + "][" + j + "] = "
            + params[i][j].annotationType().getName());
        if (params[i][j] instanceof WithDefaults) {
          WithDefaults wd = (WithDefaults) params[i][j];
          System.out.println("      value=" + wd.value() + " level=" + wd.level());
        }
      }
    }
  }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface MyTag {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@java.lang.annotation.Repeatable(Tags.class)
@interface Tag { String value(); }

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Tags { Tag[] value(); }

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@java.lang.annotation.Inherited
@interface Family {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface NotInherited {}

@Family
@NotInherited
class Parent {}

class Child extends Parent {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface WithDefaults {
  String value() default "ok";
  int level() default 5;
}
