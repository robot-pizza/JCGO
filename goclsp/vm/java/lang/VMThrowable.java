/*
 * @(#) $(JCGO)/goclsp/vm/java/lang/VMThrowable.java --
 * VM specific methods for Java "Throwable" class.
 **
 * Project: JCGO (http://www.ivmaisoft.com/jcgo/)
 * Copyright (C) 2001-2009 Ivan Maidanski <ivmai@ivmaisoft.com>
 * All rights reserved.
 **
 * Class specification origin: GNU Classpath v0.93 vm/reference
 */

/*
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 **
 * This software is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License (GPL) for more details.
 **
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 **
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module. An independent module is a module which is not derived from
 * or based on this library. If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 */

package java.lang;

final class VMThrowable /* hard-coded class name */
{

 private static OutOfMemoryError outOfMemoryError =
  new OutOfMemoryError(); /* hack */

 private final transient Object vmdata;

 static
 {
  setNullException0(createNullPointerException0X()); /* hack */
  if (outOfMemoryError == null) /* hack */
  {
   throwOutOfMemoryError0X(); /* hack */
   throwArithmeticException0X(); /* hack */
   throwArrayIndexOutOfBoundsException0X(); /* hack */
   throwArrayStoreException0X(); /* hack */
   throwClassCastException0X(); /* hack */
   throwNegativeArraySizeException0X(); /* hack */
   throwStringIndexOutOfBoundsException0X(); /* hack */
   throwUnsatisfiedLinkError0X(); /* hack */
   throwExceptionInInitializer0X(null, null); /* hack */
   createInstantiationException0X(null); /* hack */
   createNoClassDefFoundError0X(null, 0); /* hack */
   createNoSuchFieldError0X(null, null, null, 0); /* hack */
   createNoSuchMethodError0X(null, null, null, 0); /* hack */
  }
 }

 private VMThrowable(Object vmdata)
 {
  this.vmdata = vmdata;
 }

 static VMThrowable fillInStackTrace(Throwable throwable)
 {
  return new VMThrowable(fillInStackTrace0());
 }

 StackTraceElement[] getStackTrace(Throwable throwable)
 {
  Object vmdata = this.vmdata;
  int count = vmdata != null ? getStackTraceLen0(vmdata) : 0;
  StackTraceElement[] elements = new StackTraceElement[count];
  for (int i = 0; i < count; i++)
  {
   byte[] symBytes = lookupSymbol0(vmdata, i);
   String mangled = symBytes != null && symBytes.length > 0
        ? new String(symBytes, 0, symBytes.length) : "";
   String[] decoded = decodeMangledName(mangled);
   int lineNum = lookupLine0(vmdata, i);
   if (lineNum <= 0) lineNum = -1;
   // Prefer DbgHelp's actual source filename — that's the .java
   // path when JCGO emitted #line pragmas (the default), and the
   // generated .c path when run with -no-line-info. Falls back
   // to a synthesized `<class>.java` only when the resolver had
   // no debug info at all (e.g. POSIX builds with no DWARF
   // reader).
   byte[] fileBytes = lookupFile0(vmdata, i);
   String fileName;
   if (fileBytes != null && fileBytes.length > 0)
   {
    fileName = new String(fileBytes, 0, fileBytes.length);
    int slash = fileName.lastIndexOf('\\');
    if (slash < 0) slash = fileName.lastIndexOf('/');
    if (slash >= 0) fileName = fileName.substring(slash + 1);
   }
   else
   {
    fileName = "<unknown>".equals(decoded[0]) ? null
         : (decoded[0] + ".java");
   }
   elements[i] = new StackTraceElement(decoded[0], decoded[1],
        fileName, lineNum);
  }
  return elements;
 }

 // Best-effort reverse of JCGO's name mangling. JCGO emits each
 // method as a C function named `<classCName>__<methodCSign>` where
 // classCName has dots (`.`) and dollar signs (`$`) replaced by
 // underscores, and an unpackaged top-level class gets a synthetic
 // `package_` prefix. Heuristic:
 //   - split on the first `__`
 //   - left side: strip leading `package_`, replace `_` -> `.`
 //   - right side, up to next `__`: method name (or "<unknown>" if
 //     the chunk is empty)
 // Edge cases (inner classes, names that contain `_`) decode imperfectly,
 // but a slightly garbled className.methodName pair is still vastly more
 // useful than an opaque address. Returns {className, methodName}.
 private static String[] decodeMangledName(String mangled)
 {
  String[] r = new String[]{ "<unknown>", "<unknown>" };
  if (mangled == null || mangled.length() == 0) return r;
  int dd1 = mangled.indexOf("__");
  if (dd1 <= 0)
  {
   r[1] = mangled;
   return r;
  }
  String mc = mangled.substring(0, dd1);
  if (mc.startsWith("package_")) mc = mc.substring(8);
  r[0] = mc.replace('_', '.');
  String rest = mangled.substring(dd1 + 2);
  int dd2 = rest.indexOf("__");
  String method = dd2 >= 0 ? rest.substring(0, dd2) : rest;
  if (method.length() == 0) method = "<unknown>";
  r[1] = method;
  return r;
 }

 static final void exit(int status)
 { /* used by VM classes only */
  if (status < 0 || status >= 255)
   status = 255;
  exit0(status);
 }

 static final Object createInstantiationException0X(Class aclass)
 { /* called from native code */
  return new InstantiationException(aclass != null ?
          "cannot instantiate class: ".concat(getClassName(aclass)) : null);
 }

 static final Object createNoClassDefFoundError0X(String name,
   int isErroneousState)
 { /* called from native code */
  NoClassDefFoundError error = new NoClassDefFoundError(name);
  if (isErroneousState != 0)
   error.initCause(new ExceptionInInitializerError());
  return error;
 }

 static final Object createNoSuchFieldError0X(Class aclass, String name,
   String sig, int isStatic)
 { /* called from native code */
  return new NoSuchFieldError(aclass != null && name != null ?
          getClassName(aclass).concat(".").concat(name) : null);
 }

 static final Object createNoSuchMethodError0X(Class aclass, String name,
   String sig, int isStatic)
 { /* called from native code */
  return new NoSuchMethodError(aclass != null && name != null ?
          getClassName(aclass).concat(".").concat(name).concat(sig != null &&
          sig.length() > 0 ? (sig.startsWith("(") ? sig : " ".concat(sig)) :
          "") : null);
 }

 static final Object createNullPointerException0X()
 { /* called from native code */
  return new NullPointerException();
 }

 static final void throwArithmeticException0X()
 { /* called from native code */
  throw new ArithmeticException("/ by zero");
 }

 static final void throwArrayIndexOutOfBoundsException0X()
 { /* called from native code */
  throw new ArrayIndexOutOfBoundsException();
 }

 static final void throwArrayStoreException0X()
 { /* called from native code */
  throw new ArrayStoreException();
 }

 static final void throwClassCastException0X()
 { /* called from native code */
  throw new ClassCastException();
 }

 static final void throwNegativeArraySizeException0X()
 { /* called from native code */
  throw new NegativeArraySizeException();
 }

 static final void throwStringIndexOutOfBoundsException0X()
 { /* called from native code */
  throw new StringIndexOutOfBoundsException();
 }

 static final void throwOutOfMemoryError0X()
 { /* called from native code */
  if (outOfMemoryError == null)
   exit0(255);
  throw outOfMemoryError;
 }

 static final void throwUnsatisfiedLinkError0X()
 { /* called from native code */
  throw new UnsatisfiedLinkError("Missing native function called!");
 }

 static final void throwExceptionInInitializer0X(Object throwableObj,
   Class aclass)
 { /* called from native code */
  throw throwableObj instanceof Error ? (Error) throwableObj :
         new ExceptionInInitializerError((Throwable) throwableObj);
 }

 private static String getClassName(Class aclass)
 {
  String name;
  if ((name = aclass.name) == null) /* hack */
   name = "<UnknownClass>";
  return name;
 }

 private static native int setNullException0(Object throwable); /* JVM-core */

 private static native Object fillInStackTrace0(); /* JVM-core */

 private static native int getStackTraceLen0(Object vmdata); /* JVM-core */

 private static native byte[] lookupSymbol0(Object vmdata, int index); /* JVM-core */

 private static native int lookupLine0(Object vmdata, int index); /* JVM-core */

 private static native byte[] lookupFile0(Object vmdata, int index); /* JVM-core */

 private static native void exit0(
   int status); /* JVM-core */ /* never return */
}
