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

- [ ] **Multi-bound type parameter method resolution.** Parser
  accepts `<T extends A & B>`, captures only the first bound, and
  erases per JLS to the first bound -- this is correct for the type
  signature itself. The remaining gap is on the resolver: when user
  code calls a method that lives on the *second* bound (e.g.
  `a.compareTo(b)` for `<X extends Number & Comparable>`), JCGO
  reports "Undefined" because the resolver only walks the first
  bound. Standard javac inserts an implicit cast; JCGO needs the
  same handling. Requires teaching method-dispatch resolution about
  secondary bounds (today they're parsed-and-discarded by
  consumeTypeParamList's `& X` skip).
- [ ] **Method-reference parenthesized-expression receivers.** Comment
  in `MethodReference.java:27` notes these are deferred.

### Runtime / native — Android parity

The bar is "works at least as well as Android's ART runtime", not
OpenJDK parity. Code that already runs on Android shouldn't have to
change to run through JCGO.

- [ ] **iconv multi-byte charsets.** JCGO ships only the pure-Java
  charsets bundled with Classpath (US-ASCII, ISO-8859-1, UTF-8, UTF-16
  variants). Android exposes a much broader set via ICU — Shift-JIS,
  GB18030, GBK, GB2312, Big5, EUC-JP, EUC-KR, etc. — so user code
  reasonably expects `Charset.forName("Shift_JIS")` to work. Wire
  iconv into the runtime as a JNI shim and route the unsupported
  Charset providers through it.
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

