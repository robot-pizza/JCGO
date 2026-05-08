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

- [ ] **Multi-bound method dispatch via secondary bounds.** Parser
  captures all bounds (3b9e76b) and TypeVariable.getBounds() reflects
  them. Cross-bound dispatch (`a.compareTo(b)` for `<X extends Number
  & Comparable>`) still requires an explicit user-side cast
  (`((Comparable) a).compareTo(b)`). Resolver retry was prototyped
  (Stage 1 + 2 + 3 wiring) but the C codegen still emits
  JCGO_CALL_VFUNC against the leftmost-bound struct -- routing the
  call through interface dispatch needs cast injection at codegen
  time (rewrite the receiver Term to a CastExpression on retry
  success, with care around path-style `a.b()` vs expression-style
  receivers). Reverted in bacd875.
- [ ] **Method-reference real-expression receivers.** Paren-wrapped
  qualified-name receivers (`(System.out)::println`) now parse
  (03f8a74). Real expression receivers like `(getThing())::method`
  or `((Cast) x)::method` still don't, because the receiver value
  has to be evaluated once at lambda-creation time and captured in
  a synthetic field on the lifted anonymous class. The existing
  user workaround is to write a regular lambda
  (`() -> getThing().method()`).

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

