/*
 * @(#) $(JCGO)/goclsp/vm/java/lang/reflect/VMReflectAnnotations.java --
 * Slice 86 helper: shared annotation-proxy construction used by
 * Class / Method / Field / Constructor's getDeclaredAnnotations.
 **
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 */

/*
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

package java.lang.reflect;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import sun.reflect.annotation.AnnotationInvocationHandler;

/**
 * Slice 86: build Annotation[] objects from JCGO codegen-emitted
 * type-name tables. Proxy construction goes through the standard
 * AnnotationInvocationHandler with an empty memberValues map —
 * marker annotations work fully; valued annotations raise
 * IncompleteAnnotationException when those members are queried,
 * because annotation argument values aren't yet captured by the
 * parser.
 *
 * Annotation type names captured at parse time may be unqualified
 * (e.g. `@MyTag` records "MyTag" rather than "pkg.MyTag" because
 * the parser doesn't resolve imports). The lookup tries the name
 * as-is, then with the declaring class's package prefix, then
 * java.lang.* — null when none resolves to an annotation type.
 */
public final class VMReflectAnnotations
{
 private VMReflectAnnotations() {}

 public static Annotation[] build(String[] names, Class declaring)
 {
  return build(names, null, declaring);
 }

 // Java 8 getAnnotationsByType / getDeclaredAnnotationsByType
 // helper. Filters the given direct annotations by type. If none
 // match directly, looks at each annotation for an @Repeatable
 // container whose value() returns an array of the requested type;
 // when found, unwraps via reflection. Returns a zero-length array
 // when nothing matches.
 public static Annotation[] byType(Annotation[] direct,
   Class annotationClass)
 {
  if (direct == null || direct.length == 0)
   return (Annotation[]) java.lang.reflect.Array.newInstance(
            annotationClass, 0);
  // Direct hits first.
  Annotation[] tmp = (Annotation[]) java.lang.reflect.Array
          .newInstance(annotationClass, direct.length);
  int count = 0;
  for (int i = 0; i < direct.length; i++)
  {
   if (annotationClass.isInstance(direct[i]))
    tmp[count++] = direct[i];
  }
  if (count > 0)
   return shrink(tmp, count, annotationClass);
  // No direct hits — look for an @Repeatable container.
  Class containerClass = findRepeatableContainer(annotationClass);
  if (containerClass == null)
   return (Annotation[]) java.lang.reflect.Array.newInstance(
            annotationClass, 0);
  for (int i = 0; i < direct.length; i++)
  {
   if (containerClass.isInstance(direct[i]))
   {
    Object unwrapped = invokeValueMember(direct[i]);
    if (unwrapped instanceof Annotation[])
     return (Annotation[]) unwrapped;
   }
  }
  return (Annotation[]) java.lang.reflect.Array.newInstance(
          annotationClass, 0);
 }

 private static Annotation[] shrink(Annotation[] arr, int count,
   Class annotationClass)
 {
  if (count == arr.length)
   return arr;
  Annotation[] r = (Annotation[]) java.lang.reflect.Array
          .newInstance(annotationClass, count);
  System.arraycopy(arr, 0, r, 0, count);
  return r;
 }

 private static Class findRepeatableContainer(Class annotationClass)
 {
  try
  {
   java.lang.annotation.Repeatable r = (java.lang.annotation.Repeatable)
            annotationClass.getAnnotation(
              java.lang.annotation.Repeatable.class);
   return r != null ? r.value() : null;
  }
  catch (Throwable t)
  {
   return null;
  }
 }

 private static Object invokeValueMember(Annotation anno)
 {
  try
  {
   Method m = anno.annotationType().getMethod("value", new Class[0]);
   return m.invoke(anno, new Object[0]);
  }
  catch (Throwable t)
  {
   return null;
  }
 }

 public static Annotation[] build(String[] names, String[] argTexts,
   Class declaring)
 {
  if (names == null || names.length == 0)
   return new Annotation[0];
  ClassLoader loader = declaring != null
          ? declaring.getClassLoader() : null;
  Annotation[] tmp = new Annotation[names.length];
  int count = 0;
  for (int i = 0; i < names.length; i++)
  {
   String argText = argTexts != null && i < argTexts.length
            ? argTexts[i] : null;
   Class annoClass = resolveAnnotationClass(names[i], declaring, loader);
   // We don't check Class.isAnnotation() because the built-in
   // annotations supplied by JCGO (java.lang.Deprecated,
   // java.lang.annotation.Repeatable, etc.) are plain interfaces
   // that extend Annotation without the ANNOTATION modifier bit.
   // User-declared @interface types DO set the bit (TODO #2,
   // markAsAnnotationType in ClassDefinition); keeping the gate
   // relaxed makes both kinds work uniformly. Names in the
   // methodsAnnos / classAnnos / fieldsAnnos tables come from real
   // @Foo source-level usages, so any class we can resolve from
   // those names is an annotation in the source-language sense.
   if (annoClass == null)
    continue;
   Map values = parseArgText(argText, annoClass, loader, declaring);
   fillInDefaults(values, annoClass);
   try
   {
    tmp[count++] = (Annotation) Proxy.newProxyInstance(loader,
             new Class[]{annoClass},
             new AnnotationInvocationHandler(annoClass, values));
   }
   catch (RuntimeException e)
   {
    // skip
   }
  }
  if (count == names.length)
   return tmp;
  Annotation[] r = new Annotation[count];
  System.arraycopy(tmp, 0, r, 0, count);
  return r;
 }

 // TODO #1: for any annotation member not present in `values`, look
 // up the member's declared default (via Method.getDefaultValue) and
 // populate the map. Without this, AnnotationInvocationHandler raises
 // IncompleteAnnotationException for unspecified members that have a
 // default declared on the annotation type.
 private static void fillInDefaults(Map values, Class annoClass)
 {
  if (annoClass == null) return;
  try
  {
   Method[] methods = annoClass.getDeclaredMethods();
   for (int i = 0; i < methods.length; i++)
   {
    String key = methods[i].getName();
    if (values.containsKey(key)) continue;
    Object def = methods[i].getDefaultValue();
    if (def != null) values.put(key, def);
   }
  }
  catch (Throwable t)
  {
   // proceed without defaults
  }
 }

 // Slice 86: parse the raw arg-text captured by the parser into a
 // memberValues Map. Recognized value kinds:
 //   "string"             → java.lang.String
 //   42 / 42L             → Integer / Long
 //   true / false         → Boolean
 //   X.class              → Class (resolved like the annotation
 //                          class, with package fallbacks).
 // Multi-arg `key1=v1, key2=v2` is split at top-level commas
 // (commas inside strings / braces / parens are not splits).
 // Single-positional `42` becomes `value=42`.
 //
 // Unrecognized or unsupported value kinds (enum constants,
 // nested annotations, brace-array literals beyond a single
 // element) are skipped, leaving the proxy to raise
 // IncompleteAnnotationException for the affected member.
 //
 // Coercion: array-typed annotation members whose value parsed as
 // a singular get wrapped to a single-element array of the right
 // component type (e.g. `@SuppressWarnings("unused")` with `value`
 // member returning String[] gets a String[]{"unused"}).
 private static Map parseArgText(String argText, Class annoClass,
   ClassLoader loader, Class declaring)
 {
  Map m = new HashMap();
  if (argText == null || argText.length() == 0)
   return m;
  // Build a key→return-type map so parsing can be type-directed
  // for enum constants, classes, and arrays. If annoClass can't be
  // loaded the map stays empty and parsing falls back to type-
  // agnostic literal recognition.
  Map memberTypes = new HashMap();
  if (annoClass != null)
  {
   try
   {
    Method[] methods = annoClass.getDeclaredMethods();
    for (int i = 0; i < methods.length; i++)
     memberTypes.put(methods[i].getName(),
              methods[i].getReturnType());
   }
   catch (Throwable t)
   {
    // proceed with empty map
   }
  }
  String[] segments = splitTopLevelCommas(argText);
  for (int i = 0; i < segments.length; i++)
  {
   String trimmed = segments[i].trim();
   if (trimmed.length() == 0)
    continue;
   int eq = findTopLevelEquals(trimmed);
   String key;
   String rest;
   if (eq > 0)
   {
    key = trimmed.substring(0, eq).trim();
    rest = trimmed.substring(eq + 1).trim();
   }
   else
   {
    key = "value";
    rest = trimmed;
   }
   Class targetType = (Class) memberTypes.get(key);
   Object val = parseValueWithType(rest, targetType, declaring, loader);
   if (val != null)
    m.put(key, val);
  }
  if (annoClass != null && !m.isEmpty())
   coerceArrayMembers(annoClass, m);
  return m;
 }

 // TODO #1 entry point for Method.getDefaultValue. Same parser as the
 // per-call arg-text path; the input is the raw text JCGO captured at
 // parse time for an annotation member's `default V` clause.
 public static Object parseDefaultValue(String text, Class targetType,
   Class declaring)
 {
  ClassLoader loader = declaring != null
          ? declaring.getClassLoader() : null;
  return parseValueWithType(text, targetType, declaring, loader);
 }

 private static Object parseValueWithType(String s, Class targetType,
   Class declaring, ClassLoader loader)
 {
  if (s == null)
   return null;
  s = s.trim();
  if (s.length() == 0)
   return null;
  // Nested annotation `@TypeName(args)` — recursively build a
  // proxy for it. The captured arg-text preserves the leading `@`
  // and parens; we strip them and recurse via parseArgText for the
  // nested annotation's member values.
  if (s.charAt(0) == '@')
  {
   String body = s.substring(1).trim();
   int parenStart = body.indexOf('(');
   String typeName;
   String nestedArgs;
   if (parenStart >= 0 && body.charAt(body.length() - 1) == ')')
   {
    typeName = body.substring(0, parenStart).trim();
    nestedArgs = body.substring(parenStart + 1,
             body.length() - 1).trim();
   }
   else
   {
    typeName = body.trim();
    nestedArgs = "";
   }
   Class nestedClass = resolveAnnotationClass(typeName, declaring,
            loader);
   if (nestedClass == null || !nestedClass.isAnnotation())
    return null;
   Map nestedValues = parseArgText(nestedArgs, nestedClass, loader,
            declaring);
   try
   {
    return Proxy.newProxyInstance(loader,
             new Class[]{nestedClass},
             new AnnotationInvocationHandler(nestedClass,
              nestedValues));
   }
   catch (RuntimeException e)
   {
    return null;
   }
  }
  // Brace-array literal `{a, b, c}` — produces an array of
  // targetType's component type, recursing into parseValueWithType
  // for each element.
  if (s.charAt(0) == '{' && s.charAt(s.length() - 1) == '}')
  {
   String inner = s.substring(1, s.length() - 1).trim();
   Class comp = targetType != null && targetType.isArray()
            ? targetType.getComponentType() : Object.class;
   if (inner.length() == 0)
   {
    try
    {
     return java.lang.reflect.Array.newInstance(comp, 0);
    }
    catch (Throwable t)
    {
     return null;
    }
   }
   String[] elems = splitTopLevelCommas(inner);
   try
   {
    Object arr = java.lang.reflect.Array.newInstance(comp, elems.length);
    for (int i = 0; i < elems.length; i++)
    {
     Object e = parseValueWithType(elems[i].trim(), comp, declaring,
              loader);
     if (e == null)
      return null;
     java.lang.reflect.Array.set(arr, i, e);
    }
    return arr;
   }
   catch (Throwable t)
   {
    return null;
   }
  }
  // Enum constant — `MEMBER` or `Enum.MEMBER`. Type-directed:
  // resolve via Enum.valueOf when the target is an enum.
  if (targetType != null && targetType.isEnum())
  {
   String enumName = s;
   int dot = s.lastIndexOf('.');
   if (dot >= 0)
    enumName = s.substring(dot + 1).trim();
   try
   {
    return Enum.valueOf(targetType, enumName);
   }
   catch (RuntimeException e)
   {
    return null;
   }
  }
  // String / boolean / class / number literals — type-agnostic.
  Object val = parseValue(s, declaring, loader);
  // Type-directed numeric coercion: an integer literal `5` for a
  // byte / short / char member needs to be re-boxed to the right
  // primitive wrapper class so AnnotationInvocationHandler's
  // isInstance check passes.
  if (val instanceof Integer && targetType != null)
  {
   int v = ((Integer) val).intValue();
   if (targetType == Byte.TYPE || targetType == Byte.class)
    return Byte.valueOf((byte) v);
   if (targetType == Short.TYPE || targetType == Short.class)
    return Short.valueOf((short) v);
   if (targetType == Character.TYPE || targetType == Character.class)
    return Character.valueOf((char) v);
   if (targetType == Long.TYPE || targetType == Long.class)
    return Long.valueOf(v);
   if (targetType == Float.TYPE || targetType == Float.class)
    return Float.valueOf(v);
   if (targetType == Double.TYPE || targetType == Double.class)
    return Double.valueOf(v);
  }
  return val;
 }

 private static String[] splitTopLevelCommas(String s)
 {
  int n = s.length();
  int parts = 1;
  for (int i = 0; i < n; i++)
   if (s.charAt(i) == ',')
    parts++;
  String[] out = new String[parts];
  int outIdx = 0;
  int start = 0;
  int depth = 0;
  boolean inString = false;
  boolean escape = false;
  for (int i = 0; i < n; i++)
  {
   char c = s.charAt(i);
   if (escape)
   {
    escape = false;
    continue;
   }
   if (c == '\\' && inString)
   {
    escape = true;
    continue;
   }
   if (c == '"')
   {
    inString = !inString;
    continue;
   }
   if (inString)
    continue;
   if (c == '{' || c == '(' || c == '[')
    depth++;
   else if (c == '}' || c == ')' || c == ']')
    depth--;
   else if (c == ',' && depth == 0)
   {
    out[outIdx++] = s.substring(start, i);
    start = i + 1;
   }
  }
  out[outIdx++] = s.substring(start);
  if (outIdx < out.length)
  {
   String[] r = new String[outIdx];
   System.arraycopy(out, 0, r, 0, outIdx);
   return r;
  }
  return out;
 }

 private static int findTopLevelEquals(String s)
 {
  int n = s.length();
  int depth = 0;
  boolean inString = false;
  boolean escape = false;
  for (int i = 0; i < n; i++)
  {
   char c = s.charAt(i);
   if (escape)
   {
    escape = false;
    continue;
   }
   if (c == '\\' && inString)
   {
    escape = true;
    continue;
   }
   if (c == '"')
   {
    inString = !inString;
    continue;
   }
   if (inString)
    continue;
   if (c == '{' || c == '(' || c == '[')
    depth++;
   else if (c == '}' || c == ')' || c == ']')
    depth--;
   else if (c == '=' && depth == 0)
    return i;
  }
  return -1;
 }

 private static Object parseValue(String s, Class declaring,
   ClassLoader loader)
 {
  if (s == null)
   return null;
  s = s.trim();
  if (s.length() == 0)
   return null;
  if (s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"')
  {
   return unescapeStringLiteral(s.substring(1, s.length() - 1));
  }
  if (s.length() >= 3 && s.charAt(0) == '\''
      && s.charAt(s.length() - 1) == '\'')
  {
   String inside = unescapeStringLiteral(
            s.substring(1, s.length() - 1));
   if (inside.length() == 1)
    return Character.valueOf(inside.charAt(0));
   return null;
  }
  if ("true".equals(s))
   return Boolean.TRUE;
  if ("false".equals(s))
   return Boolean.FALSE;
  if (s.endsWith(".class"))
  {
   String cname = s.substring(0, s.length() - ".class".length()).trim();
   Class c = resolveAnnotationClass(cname, declaring, loader);
   if (c == null)
    c = tryLoad(cname, loader);
   return c;
  }
  Object n = parseNumber(s);
  if (n != null)
   return n;
  return null;
 }

 private static Object parseNumber(String s)
 {
  int len = s.length();
  if (len == 0)
   return null;
  char last = s.charAt(len - 1);
  boolean isLong = last == 'L' || last == 'l';
  boolean isFloat = last == 'F' || last == 'f';
  boolean isDouble = last == 'D' || last == 'd';
  String body = (isLong || isFloat || isDouble)
          ? s.substring(0, len - 1) : s;
  // Float / double — look for explicit suffix or fractional point /
  // exponent.
  if (isFloat || isDouble || body.indexOf('.') >= 0
      || body.indexOf('e') >= 0 || body.indexOf('E') >= 0)
  {
   try
   {
    if (isFloat)
     return Float.valueOf(body);
    return Double.valueOf(body);
   }
   catch (NumberFormatException e)
   {
    return null;
   }
  }
  try
  {
   if (isLong)
    return Long.valueOf(body);
   return Integer.valueOf(body);
  }
  catch (NumberFormatException e)
  {
   return null;
  }
 }

 private static void coerceArrayMembers(Class annoClass, Map values)
 {
  Method[] methods;
  try
  {
   methods = annoClass.getDeclaredMethods();
  }
  catch (Throwable t)
  {
   return;
  }
  for (int i = 0; i < methods.length; i++)
  {
   Method method = methods[i];
   String name = method.getName();
   Object val = values.get(name);
   if (val == null)
    continue;
   Class rt = method.getReturnType();
   if (!rt.isArray() || rt.isInstance(val))
    continue;
   Class comp = rt.getComponentType();
   if (!isCompatibleComponent(comp, val))
    continue;
   try
   {
    Object arr = java.lang.reflect.Array.newInstance(comp, 1);
    java.lang.reflect.Array.set(arr, 0, val);
    values.put(name, arr);
   }
   catch (Throwable t)
   {
    // skip
   }
  }
 }

 private static boolean isCompatibleComponent(Class comp, Object val)
 {
  if (comp.isPrimitive())
  {
   if (comp == Integer.TYPE) return val instanceof Integer;
   if (comp == Long.TYPE) return val instanceof Long;
   if (comp == Boolean.TYPE) return val instanceof Boolean;
   if (comp == Byte.TYPE) return val instanceof Byte;
   if (comp == Short.TYPE) return val instanceof Short;
   if (comp == Character.TYPE) return val instanceof Character;
   if (comp == Float.TYPE) return val instanceof Float;
   if (comp == Double.TYPE) return val instanceof Double;
   return false;
  }
  return comp.isInstance(val);
 }

 private static String unescapeStringLiteral(String s)
 {
  if (s.indexOf('\\') < 0)
   return s;
  StringBuffer sb = new StringBuffer(s.length());
  int n = s.length();
  int i = 0;
  while (i < n)
  {
   char c = s.charAt(i++);
   if (c != '\\' || i >= n)
   {
    sb.append(c);
    continue;
   }
   char esc = s.charAt(i++);
   switch (esc)
   {
    case 'n': sb.append('\n'); break;
    case 'r': sb.append('\r'); break;
    case 't': sb.append('\t'); break;
    case '"': sb.append('"'); break;
    case '\'': sb.append('\''); break;
    case '\\': sb.append('\\'); break;
    default: sb.append(esc); break;
   }
  }
  return sb.toString();
 }

 public static Annotation buildOne(String name, Class declaring,
   ClassLoader loader, Map values)
 {
  Class annoClass = resolveAnnotationClass(name, declaring, loader);
  if (annoClass == null || !annoClass.isAnnotation())
   return null;
  try
  {
   return (Annotation) Proxy.newProxyInstance(loader,
            new Class[]{annoClass},
            new AnnotationInvocationHandler(annoClass, values));
  }
  catch (RuntimeException e)
  {
   return null;
  }
 }

 private static Class resolveAnnotationClass(String name,
   Class declaring, ClassLoader loader)
 {
  Class c = tryLoad(name, loader);
  if (c != null)
   return c;
  if (name.indexOf('.') < 0)
  {
   String declName = declaring != null ? declaring.getName() : null;
   if (declName != null)
   {
    int dot = declName.lastIndexOf('.');
    if (dot > 0)
    {
     c = tryLoad(declName.substring(0, dot) + "." + name, loader);
     if (c != null)
      return c;
    }
   }
   c = tryLoad("java.lang." + name, loader);
   if (c != null)
    return c;
  }
  return null;
 }

 private static Class tryLoad(String name, ClassLoader loader)
 {
  try
  {
   return Class.forName(name, true, loader);
  }
  catch (ClassNotFoundException ex)
  {
   return null;
  }
  catch (NoClassDefFoundError er)
  {
   return null;
  }
 }
}
