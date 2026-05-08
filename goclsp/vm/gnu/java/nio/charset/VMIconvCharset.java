/*
 * @(#) $(JCGO)/goclsp/vm/gnu/java/nio/charset/VMIconvCharset.java --
 * VM-side native bridge for charset conversion through OS facilities.
 *
 * On Win32: routes via MultiByteToWideChar / WideCharToMultiByte
 * with a code-page table mapping common charset names (Shift_JIS,
 * GBK, GB18030, Big5, EUC-KR, KOI8-R, etc.) to Windows code pages.
 * Linux/macOS via iconv is a future slice.
 *
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 *
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

package gnu.java.nio.charset;

final class VMIconvCharset
{
 private VMIconvCharset() {}

 // True if the runtime can convert the given charset name. On Win32
 // this maps to whether lookupCodePage finds a non-zero code page.
 static native boolean supports(String name); /* JVM-core */

 // Decode bytes[off..off+len] as `name` into a freshly allocated
 // char[]. Returns null when the name isn't supported or the
 // conversion fails irrecoverably.
 static native char[] decode(byte[] bytes, int off, int len,
   String name); /* JVM-core */

 // Encode chars[off..off+len] as `name` into a freshly allocated
 // byte[]. Returns null when the name isn't supported or the
 // conversion fails irrecoverably.
 static native byte[] encode(char[] chars, int off, int len,
   String name); /* JVM-core */
}
