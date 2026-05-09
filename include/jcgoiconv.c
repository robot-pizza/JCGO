/*
 * @(#) $(JCGO)/include/jcgoiconv.c --
 * a part of the JCGO runtime subsystem.
 **
 * TODO #12: charset conversion via Win32 MultiByteToWideChar /
 * WideCharToMultiByte. Maps charset names (e.g. "Shift_JIS",
 * "GBK", "GB18030", "Big5", "EUC-KR", "KOI8-R") to Windows code
 * pages and routes the conversion through the Win32 API. Linux /
 * macOS (iconv-based) is a separate slice.
 **
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 */

/**
 * This file is compiled together with the files produced by the JCGO
 * translator (do not include and/or compile this file directly).
 */

/*
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

#ifdef JCGO_WIN32

#include <windows.h>

#ifndef JCGO_ICONV_DEFINED
#define JCGO_ICONV_DEFINED 1

/* Equality check for charset name (jstring) against a literal C
   ASCII string, case-insensitive. The Java String's underlying
   char array is read directly (no UTF-8 conversion needed because
   the names we test are pure ASCII). */
/* Read the i-th Java char of a String, transparently handling both
   storage modes: 16-bit char-array (`value` is a jcharArr) and the
   8-bit compact representation (`value` is a jbyteArr where each
   byte is one Latin-1 char). The caller has already stashed
   `valueObj` and `byteMode` flag. */
JCGO_NOSEP_INLINE jchar CFASTCALL
jcgo_iconv_charAt( jObject valueObj, int byteMode, jint idx )
{
 if (byteMode)
  return (jchar)(unsigned char)
          JCGO_ARR_INTERNALACC(jbyte, (jbyteArr)valueObj, idx);
 return JCGO_ARR_INTERNALACC(jchar, (jcharArr)valueObj, idx);
}

JCGO_NOSEP_INLINE int CFASTCALL
jcgo_iconv_nameEq( java_lang_String name, CONST char *cstr )
{
 jObject value;
 int byteMode;
 jint count, offset, i;
 if (name == jnull) return 0;
 value = (jObject)JCGO_FIELD_NZACCESS(name, value);
 if (value == jnull) return 0;
 byteMode = JCGO_METHODS_OF(value)->jcgo_typeid
         == OBJT_jarray + OBJT_jbyte;
 count = JCGO_FIELD_NZACCESS(name, count);
 offset = JCGO_FIELD_NZACCESS(name, offset);
 for (i = 0; i < count; i++)
 {
  char a = (char)jcgo_iconv_charAt(value, byteMode, offset + i);
  char b = cstr[i];
  if (b == '\0') return 0;
  if (a >= 'A' && a <= 'Z') a = (char)(a + ('a' - 'A'));
  if (b >= 'A' && b <= 'Z') b = (char)(b + ('a' - 'A'));
  /* Treat '_' and '-' as equivalent so callers like "Shift_JIS"
     vs Win32's "Shift-JIS" / IANA's "shift_jis" all match. */
  if (a == '-') a = '_';
  if (b == '-') b = '_';
  if (a != b) return 0;
 }
 return cstr[count] == '\0';
}

/* Map a Java charset name (jstring) to a Windows code page. Returns
   0 when no mapping. The set covers common multi-byte CJK encodings
   that classpath-0.93 doesn't ship. Single-byte encodings already
   covered by Classpath aren't here. */
JCGO_NOSEP_INLINE UINT CFASTCALL
jcgo_iconv_codePage( java_lang_String name )
{
 if (name == jnull) return 0;
 if (jcgo_iconv_nameEq(name, "Shift_JIS")
     || jcgo_iconv_nameEq(name, "MS932")
     || jcgo_iconv_nameEq(name, "Windows-31J")
     || jcgo_iconv_nameEq(name, "x-MS932_0213"))
  return 932;
 if (jcgo_iconv_nameEq(name, "GBK")
     || jcgo_iconv_nameEq(name, "MS936")
     || jcgo_iconv_nameEq(name, "Windows-936")
     || jcgo_iconv_nameEq(name, "GB2312")
     || jcgo_iconv_nameEq(name, "EUC-CN"))
  return 936;
 if (jcgo_iconv_nameEq(name, "GB18030"))
  return 54936;
 if (jcgo_iconv_nameEq(name, "Big5")
     || jcgo_iconv_nameEq(name, "MS950")
     || jcgo_iconv_nameEq(name, "Windows-950")
     || jcgo_iconv_nameEq(name, "x-MS950"))
  return 950;
 if (jcgo_iconv_nameEq(name, "EUC-KR")
     || jcgo_iconv_nameEq(name, "MS949")
     || jcgo_iconv_nameEq(name, "x-windows-949")
     || jcgo_iconv_nameEq(name, "KSC_5601"))
  return 949;
 if (jcgo_iconv_nameEq(name, "EUC-JP")
     || jcgo_iconv_nameEq(name, "x-EUC-JP"))
  return 20932;
 if (jcgo_iconv_nameEq(name, "KOI8-R") || jcgo_iconv_nameEq(name, "KOI8R"))
  return 20866;
 if (jcgo_iconv_nameEq(name, "ISO-2022-JP")
     || jcgo_iconv_nameEq(name, "ISO2022JP"))
  return 50220;
 return 0;
}

#endif /* JCGO_ICONV_DEFINED */

JCGO_NOSEP_STATIC jboolean CFASTCALL
gnu_java_nio_charset_VMIconvCharset__supports__Ls( java_lang_String name )
{
 return (jboolean)(jcgo_iconv_codePage(name) != 0);
}

JCGO_NOSEP_STATIC jcharArr CFASTCALL
gnu_java_nio_charset_VMIconvCharset__decode__BAIILs( jbyteArr bytes,
 java_lang_String name, jint off, jint len )
{
 UINT cp = jcgo_iconv_codePage(name);
 jcharArr arr;
 int wlen;
 char *src;
 if (!cp || bytes == jnull || len < 0)
  return jnull;
 src = (char *)&JCGO_ARR_INTERNALACC(jbyte, bytes, off);
 if (len == 0)
  return (jcharArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jchar), 0, 0);
 wlen = MultiByteToWideChar(cp, 0, src, len, NULL, 0);
 if (wlen <= 0)
  return jnull;
 arr = (jcharArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jchar), 0,
         (jint)wlen);
 if (arr == jnull)
  return jnull;
 (void)MultiByteToWideChar(cp, 0, src, len,
         (LPWSTR)&JCGO_ARR_INTERNALACC(jchar, arr, 0), wlen);
 return arr;
}

JCGO_NOSEP_STATIC jbyteArr CFASTCALL
gnu_java_nio_charset_VMIconvCharset__encode__CAIILs( jcharArr chars,
 java_lang_String name, jint off, jint len )
{
 UINT cp = jcgo_iconv_codePage(name);
 jbyteArr arr;
 int blen;
 LPCWSTR src;
 if (!cp || chars == jnull || len < 0)
  return jnull;
 src = (LPCWSTR)&JCGO_ARR_INTERNALACC(jchar, chars, off);
 if (len == 0)
  return (jbyteArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jbyte), 0, 0);
 blen = WideCharToMultiByte(cp, 0, src, len, NULL, 0, NULL, NULL);
 if (blen <= 0)
  return jnull;
 arr = (jbyteArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jbyte), 0,
         (jint)blen);
 if (arr == jnull)
  return jnull;
 (void)WideCharToMultiByte(cp, 0, src, len,
         (char *)&JCGO_ARR_INTERNALACC(jbyte, arr, 0), blen, NULL, NULL);
 return arr;
}

#else /* JCGO_WIN32 */

/* Linux/macOS: route through POSIX iconv. The native UTF-16 byte
   order matches JCGO's jchar[] in-memory layout on every platform
   we currently target (x86 / x86_64 / ARM / ARM64 -- all little
   endian). For a hypothetical BE platform we'd need to bracket
   the toUTF16 / fromUTF16 strings here. */

#include <iconv.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>

#define JCGO_ICONV_UTF16 "UTF-16LE"

/* Copy a Java String's chars into `buf` as ASCII bytes, NUL-terminated.
   Returns 0 on overflow or non-ASCII; 1 otherwise. The conversion is
   one-to-one (Java char to byte) so any 8-bit-clean charset name works. */
JCGO_NOSEP_INLINE int CFASTCALL
jcgo_iconv_nameToC( java_lang_String name, char *buf, int bufsize )
{
 jObject value;
 int byteMode;
 jint count, offset, i;
 if (name == jnull || bufsize <= 0) return 0;
 value = (jObject)JCGO_FIELD_NZACCESS(name, value);
 if (value == jnull) return 0;
 byteMode = JCGO_METHODS_OF(value)->jcgo_typeid
         == OBJT_jarray + OBJT_jbyte;
 count = JCGO_FIELD_NZACCESS(name, count);
 offset = JCGO_FIELD_NZACCESS(name, offset);
 if (count >= bufsize) return 0;
 for (i = 0; i < count; i++)
 {
  jchar c = byteMode
          ? (jchar)(unsigned char) JCGO_ARR_INTERNALACC(jbyte,
            (jbyteArr)value, offset + i)
          : JCGO_ARR_INTERNALACC(jchar, (jcharArr)value, offset + i);
  if (c > 0x7f) return 0;
  buf[i] = (char)c;
 }
 buf[count] = '\0';
 return 1;
}

/* Run iconv against a freshly-opened conversion descriptor and copy
   the output into a malloc'd buffer (caller frees). Grows the
   buffer on E2BIG, bails on illegal / incomplete sequences. Returns
   0 on success and writes *outBuf and *outLen. */
JCGO_NOSEP_INLINE int CFASTCALL
jcgo_iconv_run( iconv_t cd, char *in, size_t inLen,
 char **outBuf, size_t *outLen )
{
 size_t outAlloc = inLen * 4 + 16;
 char *out = (char *)malloc(outAlloc);
 char *outPtr = out;
 size_t outLeft = outAlloc;
 size_t inLeft = inLen;
 if (out == NULL) return -1;
 while (inLeft > 0)
 {
  size_t r = iconv(cd, &in, &inLeft, &outPtr, &outLeft);
  if (r == (size_t)-1)
  {
   if (errno == E2BIG)
   {
    size_t used = (size_t)(outPtr - out);
    size_t newAlloc = outAlloc * 2;
    char *newOut = (char *)realloc(out, newAlloc);
    if (newOut == NULL) { free(out); return -1; }
    out = newOut;
    outPtr = out + used;
    outLeft = newAlloc - used;
    outAlloc = newAlloc;
   }
   else
   {
    free(out);
    return -1;
   }
  }
 }
 *outBuf = out;
 *outLen = (size_t)(outPtr - out);
 return 0;
}

JCGO_NOSEP_STATIC jboolean CFASTCALL
gnu_java_nio_charset_VMIconvCharset__supports__Ls( java_lang_String name )
{
 char buf[64];
 iconv_t cd;
 if (!jcgo_iconv_nameToC(name, buf, sizeof(buf))) return (jboolean)0;
 cd = iconv_open(JCGO_ICONV_UTF16, buf);
 if (cd == (iconv_t)-1) return (jboolean)0;
 iconv_close(cd);
 return (jboolean)1;
}

JCGO_NOSEP_STATIC jcharArr CFASTCALL
gnu_java_nio_charset_VMIconvCharset__decode__BAIILs( jbyteArr bytes,
 java_lang_String name, jint off, jint len )
{
 char nameBuf[64];
 iconv_t cd;
 char *src;
 char *outBuf = NULL;
 size_t outLen = 0;
 jcharArr arr;
 jint chCount, j;
 if (bytes == jnull || len < 0) return jnull;
 if (!jcgo_iconv_nameToC(name, nameBuf, sizeof(nameBuf))) return jnull;
 if (len == 0)
  return (jcharArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jchar), 0, 0);
 cd = iconv_open(JCGO_ICONV_UTF16, nameBuf);
 if (cd == (iconv_t)-1) return jnull;
 src = (char *)&JCGO_ARR_INTERNALACC(jbyte, bytes, off);
 if (jcgo_iconv_run(cd, src, (size_t)len, &outBuf, &outLen) != 0)
 {
  iconv_close(cd);
  return jnull;
 }
 iconv_close(cd);
 chCount = (jint)(outLen / 2);
 arr = (jcharArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jchar), 0,
         chCount);
 if (arr == jnull) { free(outBuf); return jnull; }
 /* outBuf holds UTF-16LE bytes; on LE platforms a memcpy into the
    jcharArr is correct. */
 for (j = 0; j < chCount; j++)
 {
  unsigned char lo = (unsigned char)outBuf[j * 2];
  unsigned char hi = (unsigned char)outBuf[j * 2 + 1];
  JCGO_ARR_INTERNALACC(jchar, arr, j) = (jchar)((hi << 8) | lo);
 }
 free(outBuf);
 return arr;
}

JCGO_NOSEP_STATIC jbyteArr CFASTCALL
gnu_java_nio_charset_VMIconvCharset__encode__CAIILs( jcharArr chars,
 java_lang_String name, jint off, jint len )
{
 char nameBuf[64];
 iconv_t cd;
 char *src = NULL;
 char *outBuf = NULL;
 size_t outLen = 0;
 jbyteArr arr;
 jint i;
 if (chars == jnull || len < 0) return jnull;
 if (!jcgo_iconv_nameToC(name, nameBuf, sizeof(nameBuf))) return jnull;
 if (len == 0)
  return (jbyteArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jbyte), 0, 0);
 cd = iconv_open(nameBuf, JCGO_ICONV_UTF16);
 if (cd == (iconv_t)-1) return jnull;
 /* Pack chars into a UTF-16LE byte buffer for iconv. */
 src = (char *)malloc((size_t)len * 2);
 if (src == NULL) { iconv_close(cd); return jnull; }
 for (i = 0; i < len; i++)
 {
  jchar c = JCGO_ARR_INTERNALACC(jchar, chars, off + i);
  src[i * 2] = (char)(c & 0xff);
  src[i * 2 + 1] = (char)((c >> 8) & 0xff);
 }
 if (jcgo_iconv_run(cd, src, (size_t)len * 2, &outBuf, &outLen) != 0)
 {
  free(src);
  iconv_close(cd);
  return jnull;
 }
 free(src);
 iconv_close(cd);
 arr = (jbyteArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jbyte), 0,
         (jint)outLen);
 if (arr == jnull) { free(outBuf); return jnull; }
 memcpy(&JCGO_ARR_INTERNALACC(jbyte, arr, 0), outBuf, outLen);
 free(outBuf);
 return arr;
}

#endif /* !JCGO_WIN32 */
