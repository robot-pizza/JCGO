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

  public static class Holder<U> {
    public U value;
    public U pass(U x) { return x; }
  }

  // Multi-bound type parameter (`<X extends A & B>`). JCGO erases X
  // to the first bound (Number) but the resolver retries against
  // the secondaries when matchMethod fails on the leftmost bound,
  // injecting a synthetic cast (Slice #10).
  public <X extends Number & Comparable> X smaller(X a, X b) {
    return a.compareTo(b) <= 0 ? a : b;
  }

  interface Describable { String describe(); }

  static class Tagged extends Number
      implements Comparable<Tagged>, Describable {
    final int v;
    Tagged(int v) { this.v = v; }
    public int intValue() { return v; }
    public long longValue() { return v; }
    public float floatValue() { return v; }
    public double doubleValue() { return v; }
    public int compareTo(Tagged other) { return v - other.v; }
    public String describe() { return "tagged:" + v; }
  }

  // Three-bound type parameter — method dispatch on the third bound.
  public <X extends Number & Comparable & Describable> String tripleBound(X x) {
    return x.describe();
  }

  // Field-access receiver for cross-bound dispatch — `holder.val.describe()`
  // where `val`'s static type is the type-var X.
  static class GenHolder<X extends Number & Comparable & Describable> {
    X val;
  }
  public <X extends Number & Comparable & Describable> String fieldAccessCrossBound(
      GenHolder<X> h) {
    return h.val.describe();
  }

  // Once-eval observation: a side-effecting receiver behind a cast.
  // The method-ref `((Comparable) bumpAndGet42())::equals` should
  // evaluate `bumpAndGet42()` exactly once at lambda-creation time
  // (JLS 15.13.3). Calling the SAM repeatedly must not re-bump the
  // counter -- if `rcvCounter` ends at 1 after two SAM calls, we got
  // once-eval; 3 means the receiver re-evaluates per call.
  static int rcvCounter;
  static Object bumpAndGet42() {
    rcvCounter++;
    return Integer.valueOf(42);
  }

  static StackTraceElement[] traceLevel1() { return traceLevel2(); }
  static StackTraceElement[] traceLevel2() { return traceLevel3(); }
  static StackTraceElement[] traceLevel3() {
    return new Throwable().getStackTrace();
  }
  interface Booler { boolean test(Object o); }

  interface StringSink { void accept(String s); }

  static StringSink mkSink() {
    // Paren-wrapped qualified-name receiver. The redundant parens
    // around `System.out` would normally fail the method-ref parser;
    // the partial #11 fix strips them at parse time.
    return (System.out)::println;
  }

  interface IntChecker { boolean check(Integer n); }

  static IntChecker mkChecker(Integer threshold) {
    // Real-expression receiver: `((Comparable) threshold)::equals`.
    // The receiver isn't a plain qualified-name path -- it's a cast
    // expression wrapped in parens. The lifted lambda captures
    // `threshold` from the outer scope and dispatches on the cast.
    return ((Comparable) threshold)::equals;
  }

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

  // Repeating annotation with multiple members — exercises auto-wrap
  // into the @Repeatable container's value() and per-instance member
  // value retention through the proxy.
  @Score(value = "x", level = 3)
  @Score(value = "y", level = 4)
  public void multiScored() {}

  sealed interface Shape permits Circle, Square {}
  static final class Circle implements Shape { public String describe() { return "circle"; } }
  static final class Square implements Shape { public String describe() { return "square"; } }

  enum Severity { LOW, MEDIUM, HIGH }

  // Constructor parameter annotations — symmetric to
  // Method.getParameterAnnotations but goes through Constructor.java's
  // path, which is wired but wasn't e2e-tested before.
  static class ParamAnnoCtor {
    public ParamAnnoCtor(@MyTag String first,
                         @WithDefaults(level = 7) Integer second) {}
  }

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
    Type[] genParams = pf.getGenericParameterTypes();
    System.out.println("pickFirst.getGenericParameterTypes.length = "
        + genParams.length);
    for (int i = 0; i < genParams.length; i++) {
      Type pt = genParams[i];
      String desc = pt.getClass().getName();
      if (pt instanceof TypeVariable) {
        desc += "(name=" + ((TypeVariable) pt).getName() + ")";
      }
      System.out.println("  param[" + i + "] = " + desc);
    }

    Method hp = Holder.class.getMethod("pass", new Class[]{ Object.class });
    Type[] hpParams = hp.getGenericParameterTypes();
    System.out.println("Holder.pass.params[0] = "
        + (hpParams[0] instanceof TypeVariable
            ? "TV(" + ((TypeVariable) hpParams[0]).getName() + ")"
            : hpParams[0].toString()));

    java.lang.reflect.Field hv = Holder.class.getField("value");
    Type ft = hv.getGenericType();
    System.out.println("Holder.value.genericType = "
        + (ft instanceof TypeVariable
            ? "TV(" + ((TypeVariable) ft).getName() + ")"
            : ft.toString()));

    E2EVerify ev = new E2EVerify();
    System.out.println("smaller(1,2) = " + ev.smaller(
        Integer.valueOf(1), Integer.valueOf(2)));
    System.out.println("smaller(7,3) = " + ev.smaller(
        Integer.valueOf(7), Integer.valueOf(3)));

    Tagged taggedX = new Tagged(42);
    System.out.println("tripleBound = " + ev.tripleBound(taggedX));
    GenHolder<Tagged> gh = new GenHolder<Tagged>();
    gh.val = new Tagged(99);
    System.out.println("fieldAccessCrossBound = "
        + ev.fieldAccessCrossBound(gh));

    Method fn = E2EVerify.class.getMethod("smaller",
        new Class[]{ Number.class, Number.class });
    TypeVariable[] fnVars = fn.getTypeParameters();
    System.out.println("smaller.typeParams.length = " + fnVars.length);
    if (fnVars.length > 0) {
      Type[] bounds = fnVars[0].getBounds();
      System.out.println("smaller.X.bounds.length = " + bounds.length);
      for (int i = 0; i < bounds.length; i++) {
        System.out.println("  bound[" + i + "] = " + bounds[i]);
      }
    }

    StringSink paren = mkSink();
    paren.accept("via paren-wrapped method-ref");

    IntChecker eq42 = mkChecker(Integer.valueOf(42));
    System.out.println("eq42(42) = " + eq42.check(Integer.valueOf(42)));
    System.out.println("eq42(99) = " + eq42.check(Integer.valueOf(99)));

    rcvCounter = 0;
    Booler is42 = ((Comparable) bumpAndGet42())::equals;
    System.out.println("is42(42) = " + is42.test(Integer.valueOf(42)));
    System.out.println("is42(99) = " + is42.test(Integer.valueOf(99)));
    System.out.println("rcv-evals after two SAM calls = " + rcvCounter);

    // Method-call-shape receiver: `(bumpAndGet42())::equals` (no cast).
    // Receiver's type isn't syntactically derivable; the parse-time
    // MethodRefHoister lifts it to a `var $mref$rcv$h$N = ...;` local
    // declaration before the surrounding statement so the receiver
    // becomes a single-name reference, evaluating exactly once.
    rcvCounter = 0;
    Booler is42mc = (bumpAndGet42())::equals;
    System.out.println("is42mc(42) = " + is42mc.test(Integer.valueOf(42)));
    System.out.println("is42mc(99) = " + is42mc.test(Integer.valueOf(99)));
    System.out.println("mc rcv-evals after two SAM calls = " + rcvCounter);

    // Slice #12: charset round-trip via OS code page. Shift_JIS isn't
    // in classpath-0.93's pure-Java set; the iconv shim routes the
    // call through Win32 MultiByteToWideChar / WideCharToMultiByte.
    // Tests both ASCII pass-through and non-ASCII multi-byte (the
    // 'a' hiragana is 2 bytes in Shift_JIS: 0x82 0xa0).
    String original = "ABあC";
    byte[] sjisBytes = original.getBytes("Shift_JIS");
    System.out.println("Shift_JIS encoded length = " + sjisBytes.length);
    System.out.println("Shift_JIS byte[2] = 0x"
        + Integer.toHexString(sjisBytes[2] & 0xff));
    System.out.println("Shift_JIS byte[3] = 0x"
        + Integer.toHexString(sjisBytes[3] & 0xff));
    String roundTrip = new String(sjisBytes, "Shift_JIS");
    System.out.println("Shift_JIS round-trip codepoint[2] = 0x"
        + Integer.toHexString(roundTrip.charAt(2)));
    System.out.println("Shift_JIS round-trip == original = "
        + original.equals(roundTrip));

    // Probe java.lang.management baseline.
    try {
      java.lang.management.RuntimeMXBean rmx =
          java.lang.management.ManagementFactory.getRuntimeMXBean();
      System.out.println("rmx.getName != null = "
          + (rmx.getName() != null));
      System.out.println("rmx.getUptime >= 0 = "
          + (rmx.getUptime() >= 0));
    } catch (Throwable t) {
      System.out.println("RuntimeMXBean: " + t.getClass().getName());
    }
    try {
      java.lang.management.ThreadMXBean tm =
          java.lang.management.ManagementFactory.getThreadMXBean();
      System.out.println("tm.getThreadCount > 0 = "
          + (tm.getThreadCount() > 0));
      long selfId = Thread.currentThread().getId();
      java.lang.management.ThreadInfo info = tm.getThreadInfo(selfId);
      System.out.println("tm.getThreadInfo non-null = "
          + (info != null));
      System.out.println("tm.getThreadInfo.id matches = "
          + (info != null && info.getThreadId() == selfId));
      System.out.println("tm.getThreadInfo.name non-null = "
          + (info != null && info.getThreadName() != null));
      // tm.getThreadInfo(id) defaults maxDepth=0 (no frames); use the
      // 2-arg form to ask for the actual trace.
      java.lang.management.ThreadInfo deepInfo =
          tm.getThreadInfo(selfId, Integer.MAX_VALUE);
      java.lang.StackTraceElement[] trace = deepInfo != null
          ? deepInfo.getStackTrace() : null;
      System.out.println("tm.getThreadInfo.stack non-empty = "
          + (trace != null && trace.length > 0));
    } catch (Throwable t) {
      System.out.println("ThreadMXBean: " + t.getClass().getName());
    }
    StackTraceElement[] selfTrace = traceLevel1();
    System.out.println("Throwable.getStackTrace non-empty = "
        + (selfTrace.length > 0));
    boolean hasMain = false;
    for (int i = 0; i < selfTrace.length; i++) {
      if ("main".equals(selfTrace[i].getMethodName())) {
        hasMain = true;
        break;
      }
    }
    System.out.println("Throwable.getStackTrace contains main = "
        + hasMain);
    if (selfTrace.length > 0) {
      System.out.println("Throwable.getStackTrace[0].class = "
          + selfTrace[0].getClassName());
      System.out.println("Throwable.getStackTrace[0].method = "
          + selfTrace[0].getMethodName());
    }
    System.out.println("--- full trace ---");
    for (int i = 0; i < selfTrace.length; i++) {
      StackTraceElement e = selfTrace[i];
      String loc = e.getFileName() != null
          ? "(" + e.getFileName() + ":" + e.getLineNumber() + ")"
          : "(<no file>)";
      System.out.println("  at " + e.getClassName()
          + "." + e.getMethodName() + " " + loc);
    }
    System.out.println("--- end trace ---");
    try {
      java.lang.management.MemoryMXBean mm =
          java.lang.management.ManagementFactory.getMemoryMXBean();
      java.lang.management.MemoryUsage h = mm.getHeapMemoryUsage();
      System.out.println("mm.heap.used > 0 = " + (h.getUsed() > 0));
      System.out.println("mm.heap.max > 0 = " + (h.getMax() > 0));
    } catch (Throwable t) {
      System.out.println("MemoryMXBean: " + t.getClass().getName());
    }

    System.out.println("WithConsts.LIMIT = " + WithConsts.LIMIT);
    System.out.println("WithConsts.LABEL = " + WithConsts.LABEL);
    java.lang.reflect.Field limitField =
        WithConsts.class.getField("LIMIT");
    System.out.println("WithConsts.LIMIT (via reflection) = "
        + limitField.get(null));
    java.lang.reflect.Field kindField =
        WithConsts.class.getField("KIND");
    System.out.println("WithConsts.KIND = "
        + ((Class) kindField.get(null)).getName());
    java.lang.reflect.Field limitsField =
        WithConsts.class.getField("LIMITS");
    int[] limitsArr = (int[]) limitsField.get(null);
    System.out.println("WithConsts.LIMITS.length = " + limitsArr.length);
    System.out.println("WithConsts.LIMITS[1] = " + limitsArr[1]);

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
    System.out.println("allDefaults @WithDefaults.kind = "
        + ((Class) ad.kind()).getName());
    System.out.println("allDefaults @WithDefaults.severity = "
        + ((E2EVerify.Severity) ad.severity()).name());
    String[] tags = ad.tags();
    System.out.println("allDefaults @WithDefaults.tags.length = "
        + tags.length);
    System.out.println("allDefaults @WithDefaults.tags[1] = "
        + tags[1]);
    Inner inner = ad.inner();
    System.out.println("allDefaults @WithDefaults.inner.text = "
        + inner.text());

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

    Shape c = new Circle();
    Shape s = new Square();
    System.out.println("Circle.describe = " + ((Circle) c).describe());
    System.out.println("Square.describe = " + ((Square) s).describe());
    System.out.println("Circle isInstance Shape = "
        + Shape.class.isInstance(c));

    System.out.println("Child.isAnnotationPresent(Family) = "
        + Child.class.isAnnotationPresent(Family.class));
    System.out.println("Child.isAnnotationPresent(NotInherited) = "
        + Child.class.isAnnotationPresent(NotInherited.class));
    System.out.println("Parent.isAnnotationPresent(Family) = "
        + Parent.class.isAnnotationPresent(Family.class));
    System.out.println("Child3.isAnnotationPresent(Family) = "
        + Child3.class.isAnnotationPresent(Family.class));
    System.out.println("HasFamilyIface.isAnnotationPresent(Family) = "
        + HasFamilyIface.class.isAnnotationPresent(Family.class));

    Method mt = E2EVerify.class.getMethod("multiTagged", new Class[0]);
    Annotation[] tagsByType = mt.getAnnotationsByType(Tag.class);
    System.out.println("multiTagged getAnnotationsByType(Tag).length = "
        + tagsByType.length);
    for (int i = 0; i < tagsByType.length; i++) {
      System.out.println("  tag[" + i + "] = " + ((Tag) tagsByType[i]).value());
    }

    Method ms = E2EVerify.class.getMethod("multiScored", new Class[0]);
    Annotation[] scoresByType = ms.getAnnotationsByType(Score.class);
    System.out.println("multiScored getAnnotationsByType(Score).length = "
        + scoresByType.length);
    for (int i = 0; i < scoresByType.length; i++) {
      Score sc = (Score) scoresByType[i];
      System.out.println("  score[" + i + "] = " + sc.value()
          + ":" + sc.level());
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

    java.lang.reflect.Constructor[] ctors =
        ParamAnnoCtor.class.getDeclaredConstructors();
    System.out.println("ParamAnnoCtor ctorCount = " + ctors.length);
    Annotation[][] ctorParams = ctors[0].getParameterAnnotations();
    System.out.println("ParamAnnoCtor paramCount = " + ctorParams.length);
    for (int i = 0; i < ctorParams.length; i++) {
      System.out.println("  ctorParam[" + i + "].length = "
          + ctorParams[i].length);
      for (int j = 0; j < ctorParams[i].length; j++) {
        System.out.println("    ctor[" + i + "][" + j + "] = "
            + ctorParams[i][j].annotationType().getName());
        if (ctorParams[i][j] instanceof WithDefaults) {
          WithDefaults wd = (WithDefaults) ctorParams[i][j];
          System.out.println("      ctor value=" + wd.value()
              + " level=" + wd.level());
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
@Target(ElementType.METHOD)
@java.lang.annotation.Repeatable(Scores.class)
@interface Score { String value(); int level(); }

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Scores { Score[] value(); }

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

// 3-level chain to exercise @Inherited's transitive walk.
@Family
class Grandparent3 {}
class Parent3 extends Grandparent3 {}
class Child3 extends Parent3 {}

// @Inherited only walks the superclass chain per JLS 9.6.4.3 — it
// should NOT propagate through interface implementations.
@Family
interface IFamily {}
class HasFamilyIface implements IFamily {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Inner { String text() default "inner-default"; }

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface WithDefaults {
  String value() default "ok";
  int level() default 5;
  Class kind() default Integer.class;
  E2EVerify.Severity severity() default E2EVerify.Severity.MEDIUM;
  String[] tags() default { "a", "b", "c" };
  Inner inner() default @Inner;
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface WithConsts {
  int LIMIT = 42;
  String LABEL = "anno-const";
  Class KIND = Integer.class;
  int[] LIMITS = {1, 2, 3};
  String name();
}
