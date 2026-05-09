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

**Tier 1 — frequently used, JCGO returns broken values:**

- [ ] `ThreadMXBean.getThreadInfo(long)` — `jcgogmt.c`
  `getThreadInfoForId0` is a stub returning null. User code calling
  `info.getThreadId()` / `info.getStackTrace()` NPEs. Fix needs a
  populated `ThreadInfo` with name/id/state/stack trace.
- [ ] `ThreadMXBean.dumpAllThreads()` — depends on getThreadInfo;
  same gap.
- [ ] `ThreadMXBean.getThreadCpuTime(long)` /
  `getThreadUserTime(long)` — always 0 (`jcgogmt.c`
  `getThreadCpuUserTime0` stub). On Linux a real impl reads
  `/proc/self/task/<tid>/stat`; on Win32, `GetThreadTimes`.
- [ ] `ThreadMXBean.findDeadlockedThreads()` /
  `findMonitorDeadlockedThreads()` — empty array.
- [ ] `RuntimeMXBean.getInputArguments()` — empty `String[]`
  (`jcgogmr.c` stub). Apps reading `-D` properties or VM flags see
  nothing.

**Tier 2 — less common, currently misleading:**

- [ ] `MemoryMXBean.getNonHeapMemoryUsage()` — returns
  `MemoryUsage(0, 0, 0, 0)` (`jcgogmm.c` `getNonHeapMemoryUsage0`
  stub returns 0-fill).
- [ ] `MemoryMXBean.getObjectPendingFinalizationCount()` — 0 stub.
- [ ] `OperatingSystemMXBean.getSystemLoadAverage()` — 0.0 stub
  (`jcgogms.c`). Should return -1.0 per JLS to mean "unknown".
- [ ] `GarbageCollectorMXBean.getCollectionTime(name)` — stub
  (`jcgogmg.c`). Count works (`getCollectionCount` via
  `JCGO_MEM_GCGETCOUNT`) but cumulative time doesn't.

**Tier 3 — niche, OK to leave stubbed:**

- `ClassLoadingMXBean.getUnloadedClassCount` — 0 (JCGO doesn't
  unload). Correct for whole-program codegen.
- `CompilationMXBean.getTotalCompilationTime` — 0 (no JIT).
  Correct.
- `MemoryPoolMXBean.*` — most are stubs. Pools are
  JIT/generational concepts that don't map well to BDWGC; Android
  has them but the values are mostly placeholders too.

### Won't do

- **JDWP debugger natives.** Substantial native-runtime project
  (porting OpenJDK's JDWP agent on top of a translated runtime with
  no real JVM data structures). Android doesn't ship this either —
  ART has its own JDWP-like protocol. Attach a native debugger to the
  generated C instead.

