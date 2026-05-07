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
  if (names == null || names.length == 0)
   return new Annotation[0];
  ClassLoader loader = declaring != null
          ? declaring.getClassLoader() : null;
  Map empty = new HashMap();
  Annotation[] tmp = new Annotation[names.length];
  int count = 0;
  for (int i = 0; i < names.length; i++)
  {
   Annotation a = buildOne(names[i], declaring, loader, empty);
   if (a != null)
    tmp[count++] = a;
  }
  if (count == names.length)
   return tmp;
  Annotation[] r = new Annotation[count];
  System.arraycopy(tmp, 0, r, 0, count);
  return r;
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
