/*
 * @(#) $(JCGO)/include/jcgothrw.c --
 * a part of the JCGO runtime subsystem.
 **
 * Project: JCGO (http://www.ivmaisoft.com/jcgo/)
 * Copyright (C) 2001-2012 Ivan Maidanski <ivmai@ivmaisoft.com>
 * All rights reserved.
 */

/**
 * This file is compiled together with the files produced by the JCGO
 * translator (do not include and/or compile this file directly).
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

#ifdef JCGO_VER

/*
 * Stack-trace machinery. Bridges JCGO's VMThrowable Java side to the
 * host platform's stack-walk + symbol resolution. Two concerns:
 *
 *   1. fillInStackTrace0 captures return addresses into a long[] which
 *      VMThrowable stores as opaque vmdata.
 *   2. lookupSymbol0(vmdata, i) returns the i-th frame's mangled C
 *      function name as bytes. The Java side splits on `__` to
 *      recover (className, methodName) -- imperfect for inner-class
 *      and underscore-containing names but useful enough for crash
 *      dumps and ThreadInfo.
 *
 * Win32 path: CaptureStackBackTrace + a self-contained walker over
 * our own PE export directory. Requires linking with
 * `-Wl,--export-all-symbols` so the export table contains every
 * function (mingw doesn't export by default for executables).
 *
 * POSIX path: backtrace() + dladdr(). Requires building with
 * `-rdynamic` (or `-Wl,--export-dynamic`) so dladdr can see static
 * functions.
 *
 * If symbols can't be resolved on the host, lookupSymbol0 returns an
 * empty array and the Java side renders the frame as "<unknown>".
 */

#define JCGO_TRACE_MAX_FRAMES 64

#ifdef JCGO_WIN32

#include <windows.h>
#include <dbghelp.h>

/* Two parallel resolution paths on Win32:
 *
 *   1. PE export table walk (jcgo_thrw_pe_lookup): recovers the
 *      mangled C function name from any captured return address.
 *      Self-contained, works for mingw and MSVC builds. Names land
 *      in the export table when the binary is linked with
 *      --export-all-symbols (mingw) or /OPT:NOREF + dllexport
 *      (MSVC). The Java side demangles to (className, methodName).
 *
 *   2. DbgHelp SymGetLineFromAddr64 (jcgo_thrw_dbg_line): reads the
 *      PDB the linker dropped next to the .exe and returns the
 *      Java source file + line number for the address. Only works
 *      on PDB-bearing builds (MSVC + /Zi). mingw's DWARF is opaque
 *      to DbgHelp; on those builds the line lookup silently fails
 *      and lookupLine0 returns 0.
 *
 * DbgHelp is loaded lazily via LoadLibrary so the runtime doesn't
 * impose a hard link dependency on dbghelp.dll for builds that
 * don't care about line resolution.
 */

static HMODULE jcgo_thrw_self;
static int jcgo_thrw_selfInited;

typedef BOOL (WINAPI *jcgo_thrw_SymInitialize_fn)(HANDLE, PCSTR, BOOL);
typedef BOOL (WINAPI *jcgo_thrw_SymGetLineFromAddr64_fn)(HANDLE, DWORD64,
        PDWORD, PIMAGEHLP_LINE64);
typedef BOOL (WINAPI *jcgo_thrw_SymFromAddr_fn)(HANDLE, DWORD64, PDWORD64,
        PSYMBOL_INFO);
typedef DWORD (WINAPI *jcgo_thrw_SymSetOptions_fn)(DWORD);

static jcgo_thrw_SymInitialize_fn pSymInitialize;
static jcgo_thrw_SymGetLineFromAddr64_fn pSymGetLineFromAddr64;
static jcgo_thrw_SymFromAddr_fn pSymFromAddr;
static jcgo_thrw_SymSetOptions_fn pSymSetOptions;
static int jcgo_thrw_dbgInited;

static void jcgo_thrw_initSelf(void)
{
 if (jcgo_thrw_selfInited) return;
 jcgo_thrw_selfInited = 1;
 jcgo_thrw_self = GetModuleHandleA(NULL);
}

static void jcgo_thrw_initDbg(void)
{
 HMODULE h;
 if (jcgo_thrw_dbgInited) return;
 jcgo_thrw_dbgInited = 1;
 h = LoadLibraryA("dbghelp.dll");
 if (h == NULL) return;
 pSymInitialize = (jcgo_thrw_SymInitialize_fn)
         GetProcAddress(h, "SymInitialize");
 pSymGetLineFromAddr64 = (jcgo_thrw_SymGetLineFromAddr64_fn)
         GetProcAddress(h, "SymGetLineFromAddr64");
 pSymFromAddr = (jcgo_thrw_SymFromAddr_fn)
         GetProcAddress(h, "SymFromAddr");
 pSymSetOptions = (jcgo_thrw_SymSetOptions_fn)
         GetProcAddress(h, "SymSetOptions");
 if (pSymSetOptions != NULL)
  pSymSetOptions(0x10 | 0x100); /* LOAD_LINES | DEFERRED_LOADS */
 if (pSymInitialize != NULL)
  pSymInitialize(GetCurrentProcess(), NULL, TRUE);
}

static int jcgo_thrw_dbg_line(void *addr)
{
 IMAGEHLP_LINE64 line;
 DWORD disp;
 jcgo_thrw_initDbg();
 if (pSymGetLineFromAddr64 == NULL) return 0;
 memset(&line, 0, sizeof(line));
 line.SizeOfStruct = sizeof(line);
 if (!pSymGetLineFromAddr64(GetCurrentProcess(),
         (DWORD64)(size_t)addr, &disp, &line)) {
  return 0;
 }
 return (int)line.LineNumber;
}

/* DbgHelp populates an IMAGEHLP_LINE64 with both `FileName` and
 * `LineNumber`. We expose the filename via a separate native so the
 * Java side can render whichever the build actually produced — Java
 * file when JCGO emitted #line pragmas, C file when run with
 * `-no-line-info`. */
static int jcgo_thrw_dbg_file(void *addr, char *outBuf, int outLen)
{
 IMAGEHLP_LINE64 line;
 DWORD disp;
 int n;
 jcgo_thrw_initDbg();
 if (pSymGetLineFromAddr64 == NULL) return 0;
 memset(&line, 0, sizeof(line));
 line.SizeOfStruct = sizeof(line);
 if (!pSymGetLineFromAddr64(GetCurrentProcess(),
         (DWORD64)(size_t)addr, &disp, &line)) {
  return 0;
 }
 if (line.FileName == NULL) return 0;
 n = 0;
 while (n < outLen - 1 && line.FileName[n] != 0) {
  outBuf[n] = line.FileName[n];
  n++;
 }
 outBuf[n] = 0;
 return n;
}

static int jcgo_thrw_dbg_symbol(void *addr, char *outBuf, int outLen)
{
 char symBuf[sizeof(SYMBOL_INFO) + 512];
 SYMBOL_INFO *sym;
 DWORD64 disp;
 int n;
 jcgo_thrw_initDbg();
 if (pSymFromAddr == NULL) return 0;
 sym = (SYMBOL_INFO *)symBuf;
 memset(sym, 0, sizeof(symBuf));
 sym->SizeOfStruct = sizeof(SYMBOL_INFO);
 sym->MaxNameLen = 511;
 if (!pSymFromAddr(GetCurrentProcess(), (DWORD64)(size_t)addr,
         &disp, sym)) {
  return 0;
 }
 n = 0;
 while (n < outLen - 1 && sym->Name[n] != 0) {
  outBuf[n] = sym->Name[n];
  n++;
 }
 outBuf[n] = 0;
 return n;
}

/* Resolve `addr` to the export-table symbol nearest below it.
 * Returns symbol name length (excluding NUL) into outBuf, or 0 if
 * unresolved. outBuf is unmodified on miss. */
static int jcgo_thrw_pe_lookup(void *addr, char *outBuf, int outLen)
{
 BYTE *base;
 IMAGE_DOS_HEADER *dos;
 IMAGE_NT_HEADERS *nt;
 IMAGE_DATA_DIRECTORY *expDir;
 IMAGE_EXPORT_DIRECTORY *exp;
 DWORD *funcRvas;
 DWORD *nameRvas;
 WORD *ordinals;
 DWORD i;
 DWORD bestRva;
 const char *bestName;
 DWORD targetRva;

 if (jcgo_thrw_self == NULL) return 0;
 base = (BYTE *)jcgo_thrw_self;
 dos = (IMAGE_DOS_HEADER *)base;
 if (dos->e_magic != IMAGE_DOS_SIGNATURE) return 0;
 nt = (IMAGE_NT_HEADERS *)(base + dos->e_lfanew);
 if (nt->Signature != IMAGE_NT_SIGNATURE) return 0;
 expDir = &nt->OptionalHeader
          .DataDirectory[IMAGE_DIRECTORY_ENTRY_EXPORT];
 if (expDir->VirtualAddress == 0 || expDir->Size == 0) return 0;
 exp = (IMAGE_EXPORT_DIRECTORY *)(base + expDir->VirtualAddress);
 funcRvas = (DWORD *)(base + exp->AddressOfFunctions);
 nameRvas = (DWORD *)(base + exp->AddressOfNames);
 ordinals = (WORD *)(base + exp->AddressOfNameOrdinals);

 if ((BYTE *)addr < base) return 0;
 targetRva = (DWORD)((BYTE *)addr - base);
 bestRva = 0;
 bestName = NULL;
 for (i = 0; i < exp->NumberOfNames; i++)
 {
  WORD ord = ordinals[i];
  DWORD funcRva = funcRvas[ord];
  if (funcRva <= targetRva && funcRva > bestRva)
  {
   bestRva = funcRva;
   bestName = (const char *)(base + nameRvas[i]);
  }
 }
 if (bestName == NULL) return 0;
 {
  int n = 0;
  while (n < outLen - 1 && bestName[n] != 0)
  {
   outBuf[n] = bestName[n];
   n++;
  }
  outBuf[n] = 0;
  return n;
 }
}

#else /* !JCGO_WIN32 */

#include <execinfo.h>
#include <dlfcn.h>
#include <string.h>

static int jcgo_thrw_dl_lookup(void *addr, char *outBuf, int outLen)
{
 Dl_info info;
 int n;
 if (dladdr(addr, &info) == 0 || info.dli_sname == NULL) return 0;
 n = 0;
 while (n < outLen - 1 && info.dli_sname[n] != 0)
 {
  outBuf[n] = info.dli_sname[n];
  n++;
 }
 outBuf[n] = 0;
 return n;
}

#endif /* JCGO_WIN32 */

JCGO_NOSEP_STATIC jint CFASTCALL
java_lang_VMThrowable__setNullException0__Lo( java_lang_Object throwable )
{
 jcgo_globData.nullExc = (jObject)throwable;
 return 0;
}

JCGO_NOSEP_STATIC java_lang_Object CFASTCALL
java_lang_VMThrowable__fillInStackTrace0__( void )
{
 void *frames[JCGO_TRACE_MAX_FRAMES];
 int count;
 jlongArr arr;
 int i;

#ifdef JCGO_WIN32
 /* Skip 1: this very function. */
 count = (int)CaptureStackBackTrace(1, JCGO_TRACE_MAX_FRAMES,
          frames, NULL);
#else
 count = backtrace(frames, JCGO_TRACE_MAX_FRAMES);
 /* Drop the deepest frame (this function). */
 if (count > 0)
 {
  int j;
  for (j = 0; j < count - 1; j++) frames[j] = frames[j + 1];
  count--;
 }
#endif
 if (count < 0) count = 0;
 arr = (jlongArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jlong), 0,
          (jint)count);
 if (arr == jnull) return jnull;
 for (i = 0; i < count; i++)
 {
  JCGO_ARR_INTERNALACC(jlong, arr, i) =
           (jlong)(size_t)frames[i];
 }
 return (java_lang_Object)arr;
}

JCGO_NOSEP_STATIC jint CFASTCALL
java_lang_VMThrowable__getStackTraceLen0__Lo( java_lang_Object vmdata )
{
 if (vmdata == jnull) return 0;
 return (jint)JCGO_ARRAY_NZLENGTH(((jlongArr)vmdata));
}

JCGO_NOSEP_STATIC jbyteArr CFASTCALL
java_lang_VMThrowable__lookupSymbol0__LoI( java_lang_Object vmdata,
 jint index )
{
 jlongArr addrs;
 jint len;
 void *addr;
 char buf[256];
 int n;
 jbyteArr out;
 int i;

 if (vmdata == jnull)
  return (jbyteArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jbyte), 0, 0);
 addrs = (jlongArr)vmdata;
 len = (jint)JCGO_ARRAY_NZLENGTH(addrs);
 if (index < 0 || index >= len)
  return (jbyteArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jbyte), 0, 0);
 addr = (void *)(size_t)JCGO_ARR_INTERNALACC(jlong, addrs,
          index);
 buf[0] = 0;
#ifdef JCGO_WIN32
 /* Try DbgHelp first (works for MSVC + PDB and any binary whose
  * symbols ended up in the COFF symtab); fall back to walking our
  * own PE export table for mingw + --export-all-symbols builds. */
 n = jcgo_thrw_dbg_symbol(addr, buf, sizeof(buf));
 if (n <= 0) {
  jcgo_thrw_initSelf();
  n = jcgo_thrw_pe_lookup(addr, buf, sizeof(buf));
 }
#else
 n = jcgo_thrw_dl_lookup(addr, buf, sizeof(buf));
#endif
 if (n <= 0)
  return (jbyteArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jbyte), 0, 0);
 out = (jbyteArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jbyte), 0,
          (jint)n);
 if (out == jnull) return jnull;
 for (i = 0; i < n; i++)
  JCGO_ARR_INTERNALACC(jbyte, out, i) = (jbyte)buf[i];
 return out;
}

JCGO_NOSEP_STATIC jbyteArr CFASTCALL
java_lang_VMThrowable__lookupFile0__LoI( java_lang_Object vmdata,
 jint index )
{
 jlongArr addrs;
 jint len;
 void *addr;
 char buf[260];
 int n;
 jbyteArr out;
 int i;

 if (vmdata == jnull)
  return (jbyteArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jbyte), 0, 0);
 addrs = (jlongArr)vmdata;
 len = (jint)JCGO_ARRAY_NZLENGTH(addrs);
 if (index < 0 || index >= len)
  return (jbyteArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jbyte), 0, 0);
 addr = (void *)(size_t)JCGO_ARR_INTERNALACC(jlong, addrs, index);
 buf[0] = 0;
#ifdef JCGO_WIN32
 n = jcgo_thrw_dbg_file(addr, buf, sizeof(buf));
#else
 (void)addr;
 n = 0;
#endif
 if (n <= 0)
  return (jbyteArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jbyte), 0, 0);
 out = (jbyteArr)jcgo_newArray(JCGO_CORECLASS_FOR(OBJT_jbyte), 0,
          (jint)n);
 if (out == jnull) return jnull;
 for (i = 0; i < n; i++)
  JCGO_ARR_INTERNALACC(jbyte, out, i) = (jbyte)buf[i];
 return out;
}

JCGO_NOSEP_STATIC jint CFASTCALL
java_lang_VMThrowable__lookupLine0__LoI( java_lang_Object vmdata,
 jint index )
{
 jlongArr addrs;
 jint len;
 void *addr;

 if (vmdata == jnull) return 0;
 addrs = (jlongArr)vmdata;
 len = (jint)JCGO_ARRAY_NZLENGTH(addrs);
 if (index < 0 || index >= len) return 0;
 addr = (void *)(size_t)JCGO_ARR_INTERNALACC(jlong, addrs, index);
#ifdef JCGO_WIN32
 return (jint)jcgo_thrw_dbg_line(addr);
#else
 (void)addr;
 return 0;
#endif
}

JCGO_NOSEP_STATIC void CFASTCALL
java_lang_VMThrowable__exit0__I( jint status )
{
 jcgo_initialized = -2;
 exit((int)status);
}

#endif
