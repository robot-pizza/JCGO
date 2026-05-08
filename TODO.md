# JCGO Fork — TODO

Local working notes (gitignored). Track modernization work here; promote
to upstream-quality commit messages / PR descriptions when ready.

## Status

Everything in the original modernization-goal block (generics, enums,
annotations, autoboxing, foreach, varargs, twr, diamond, multi-catch,
string switch, switch expressions, records, sealed) has shipped. Java
version selector flag is in. Last verified: `master` at 88a95bc — smoke
positive 88/88, negative 33/33, skipped 2; `mkjcgo/test-e2e.sh`
runtime-validates bridges, generic signatures, built-in @Deprecated
reflection, and custom @MyTag reflection.

## Unfinished / deferred work

### Annotation infrastructure


### Verification gaps (claimed at codegen, not at runtime)


### Parser gaps


### Runtime / native — Android parity

The bar is "works at least as well as Android's ART runtime", not
OpenJDK parity. Code that already runs on Android shouldn't have to
change to run through JCGO.

- [ ] **Charset support on non-Win32.** Win32 path routes through
  MultiByteToWideChar / WideCharToMultiByte (8d9df1f). Linux / macOS
  / Android need an iconv (or ICU) implementation in
  `include/jcgoiconv.c` to provide the same charsets. The Java side
  works as-is; only the C side's `#ifdef JCGO_WIN32` block needs a
  Linux counterpart calling iconv_open / iconv / iconv_close.
- [ ] **`java.lang.management` Android subset.** Android stubs
  `ManagementFactory.getRuntimeMXBean()` (returns "<pid>@<host>"-style
  ID), `getThreadMXBean()` (basic `getThreadCount`, `getAllThreadIds`,
  `getThreadInfo`, `dumpAllThreads`), and `getMemoryMXBean()` (heap
  usage). JCGO either returns nulls or doesn't implement the natives.
  Wire enough that user code calling these gets sensible answers
  rather than NPE — most data is already accessible through BDWGC
  stats and the existing thread table.

### Won't do

- **JDWP debugger natives.** Substantial native-runtime project
  (porting OpenJDK's JDWP agent on top of a translated runtime with
  no real JVM data structures). Android doesn't ship this either —
  ART has its own JDWP-like protocol. Attach a native debugger to the
  generated C instead.

