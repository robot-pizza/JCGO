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
   Map values = parseArgText(argText);
   Annotation a = buildOne(names[i], declaring, loader, values);
   if (a != null)
    tmp[count++] = a;
  }
  if (count == names.length)
   return tmp;
  Annotation[] r = new Annotation[count];
  System.arraycopy(tmp, 0, r, 0, count);
  return r;
 }

 // Slice 86: parse the raw arg-text captured by the parser into a
 // memberValues Map. Recognized forms:
 //   <empty>           → empty map (marker annotation).
 //   "string"          → {"value": "string"}.
 //   value="string"    → {"value": "string"}.
 //   key="x"           → {"key": "x"}.
 // Anything else (numbers, classes, enums, arrays, nested
 // annotations, multi-arg) → empty map. The proxy then raises
 // IncompleteAnnotationException for those members. Capturing the
 // full set of value kinds is a deferred follow-up.
 private static Map parseArgText(String argText)
 {
  Map m = new HashMap();
  if (argText == null || argText.length() == 0)
   return m;
  String trimmed = argText.trim();
  // value=... form?
  int eq = trimmed.indexOf('=');
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
  if (rest.length() >= 2
      && rest.charAt(0) == '"'
      && rest.charAt(rest.length() - 1) == '"')
  {
   String s = unescapeStringLiteral(
            rest.substring(1, rest.length() - 1));
   m.put(key, s);
  }
  return m;
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
