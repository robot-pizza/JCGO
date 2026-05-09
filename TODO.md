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

The bar is "works at least as well as Android's ART runtime", not
OpenJDK parity. Code that already runs on Android shouldn't have to
change to run through JCGO.

### Method-reference once-eval semantics

- [ ] `(expr)::method` re-evaluates the receiver expression on each
  SAM invocation. Java spec says evaluate once at lambda-creation
  time. Side-effecting receivers like `(getStream())::onNext`
  observably differ from javac. Fix: capture-via-constructor — give
  the synthesized anonymous class a final field of the receiver's
  type, populate via a constructor arg at creation, and have the
  SAM body read the field instead of re-running the expression.
  Most user code uses pure receivers (casts, field accesses) and
  doesn't notice; this is for spec compliance.

### `java.lang.management` Android subset

The skeleton is populated; baseline calls work end-to-end (E2EVerify
probes RuntimeMXBean.getName / getUptime, ThreadMXBean.getThreadCount,
MemoryMXBean.getHeapMemoryUsage). The remaining gaps are per-method,
with stubs returning null/0/empty in place of real data.

**Crashing user code (must fix):**

- [ ] `ThreadMXBean.getThreadInfo(long)` — `jcgogmt.c`
  `getThreadInfoForId0` is a stub returning null. User code calling
  `info.getThreadId()` / `info.getStackTrace()` NPEs. Fix needs a
  populated `ThreadInfo` with name/id/state/stack trace.
- [ ] `ThreadMXBean.dumpAllThreads()` — depends on getThreadInfo;
  same gap.

**Misleading values (lies about CPU time):**

- [ ] `ThreadMXBean.getThreadCpuTime(long)` /
  `getThreadUserTime(long)` — always 0 (`jcgogmt.c`
  `getThreadCpuUserTime0` stub). Either implement (Win32
  `GetThreadTimes`, Linux `/proc/self/task/<tid>/stat`) or wire
  `isThreadCpuTimeSupported()` to return `false` so callers know.

**Honest answers (return value already conveys "not supported"):**

These return -1 / 0 / empty in a way that's a legitimate "not
supported / unknown" response. No fix needed unless we plumb through
real values:

- `OperatingSystemMXBean.getSystemLoadAverage()` returns -1.0 (JLS
  "unknown") — already correct.
- `RuntimeMXBean.getInputArguments()` returns empty `String[]` —
  honest: JCGO has no JVM args, just main's argv.
- `MemoryMXBean.getNonHeapMemoryUsage()` returns
  `MemoryUsage(0, 0, 0, 0)` — valid per JLS.
- `MemoryMXBean.getObjectPendingFinalizationCount()` returns 0 —
  honest: BDWGC runs finalizers eagerly, no queue.
- `ThreadMXBean.findDeadlockedThreads` /
  `findMonitorDeadlockedThreads()` returns empty array — honest:
  no deadlocks detected (since JCGO doesn't track).
- `GarbageCollectorMXBean.getCollectionTime` returns 0 — honest:
  BDWGC doesn't track cumulative pause time.
- `ClassLoadingMXBean.getUnloadedClassCount` returns 0 — correct:
  whole-program codegen, no unloading.
- `CompilationMXBean.getTotalCompilationTime` returns 0 —
  correct: no JIT.
- `MemoryPoolMXBean.*` mostly stubbed — pools are
  JIT/generational concepts that don't map to BDWGC.

### Test coverage gaps

These features are claimed-done but the e2e fixture exercises only
narrow shapes. Real users will hit edge cases. Widen the fixture
before declaring full confidence.

- [ ] **Annotation member defaults** — only `int` + `String`
  defaults tested. Untested: `Class<X> default Foo.class`,
  `enum default X.RED`, brace-array defaults like
  `int[] default {1, 2, 3}`, nested-annotation defaults like
  `Spec default @Spec(value="x")`.
- [ ] **`Constructor.getParameterAnnotations()`** — code is
  symmetric to `Method.getParameterAnnotations` and is wired in
  `Constructor.java`, but the e2e fixture only invokes the Method
  variant. Add a fixture with a constructor that has annotated
  parameters and reflect over it.
- [ ] **`@interface` const declarations** — `int` + `String`
  constants tested. Untested: `Class<?>` constants
  (`Class<? extends Foo> KIND = Foo.class;`) and array constants
  (`int[] LIMITS = {1, 2, 3};`).
- [ ] **`@Inherited` walk** — only 2-level (Parent → Child).
  Multi-level (Grandparent → Parent → Child) and inheritance via
  interface chain (an annotation on an interface I, where C extends
  another class that implements I) untested.
- [ ] **Repeating annotations** — only `String value()` shape
  tested via `@Tag("alpha") @Tag("beta")`. Untested: repeating
  annotations with multiple members
  (`@Score(value="x", level=3) @Score(value="y", level=4)`),
  and the auto-wrap into a `@Repeatable` container's `value()`.
- [ ] **Cross-bound method dispatch** — 2-bound case
  (`<X extends Number & Comparable>`) tested. Untested: 3+ bounds
  (`<X extends A & B & C>` with method on C), cross-bound dispatch
  through a receiver that's a field access rather than a local
  parameter. Receiver-separated form (vecSize==0 retry path in
  MethodInvocation) is wired but not e2e-tested -- normal `a.x()`
  parses path-style, so this branch only fires in less-common
  expression-receiver shapes.

### Won't do

- **JDWP debugger natives.** Substantial native-runtime project
  (porting OpenJDK's JDWP agent on top of a translated runtime with
  no real JVM data structures). Android doesn't ship this either —
  ART has its own JDWP-like protocol. Attach a native debugger to the
  generated C instead.

