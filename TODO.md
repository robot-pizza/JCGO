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

- [x] **Cast-expression receivers** (`((SomeType) expr)::method`).
  `MethodReference.processPass1` now extracts the cast type
  syntactically (no early pass1 of the receiver — that would bind
  free names against the outer scope and block JCGO's anon-class
  capture), synthesizes a `private final SomeType $mref$rcv =
  <receiver>;` field on the lifted class, and rewrites the SAM
  body to read the field instead of re-evaluating. Field
  initializer runs once at construction, JLS 15.13.3 compliant.
  E2EVerify exercises observably: a counter-bumping side-effecting
  receiver wrapped in a cast, two SAM calls, counter = 1.
- [x] **Method-call receivers** (`(getStream())::onNext`). New
  `MethodRefHoister` (parse-time, modeled on `SwitchArgHoister`)
  walks expression trees at the same three statement-level hoist
  points and, for every `MethodReference` whose receiver isn't
  already a `QualifiedName` or a `CastExpression` (those have
  their own working paths), lifts the receiver to a synthesized
  `var $mref$rcv$h$N = <receiver>;` local declaration as a
  preamble before the surrounding statement. The receiver
  expression then becomes a single-name reference that hits the
  existing `QualifiedName`-receiver path, evaluating the original
  expression exactly once. JLS 15.13.3 compliant.

  Type inference for the synthesized local rides on JCGO's
  existing `var` (Slice 24a / JLS 10) handling — pass1 sees the
  init expr and propagates its `exprType` to the local in the
  outer scope, where the receiver naturally lives. No
  inner-vs-outer capture conflict.

  Surfaced fix: `MethodReference.detectUnboundInstance` was
  unconditionally calling `c.resolveClass(dotted, true, false)`
  on a single-id receiver, which `System.exit`s when the name
  isn't a class (parseJavaFile fails to find the source).
  Pre-checking `c.currentMethod.getLocalVar(dotted)` skips the
  resolveClass attempt when the receiver is a local var — covers
  both the new hoist case and any existing user-written
  `localVar::method` form (which had been latently broken).

  E2EVerify covers it: `is42mc = (bumpAndGet42())::equals`,
  two SAM calls, `mc rcv-evals after two SAM calls = 1`.

### `java.lang.management` Android subset

The skeleton is populated; baseline calls work end-to-end (E2EVerify
probes RuntimeMXBean.getName / getUptime, ThreadMXBean.getThreadCount,
MemoryMXBean.getHeapMemoryUsage). The remaining gaps are per-method,
with stubs returning null/0/empty in place of real data.

**Crashing user code (must fix):**

- [x] `ThreadMXBean.getThreadInfo(long)` —
  `VMThreadMXBeanImpl.getThreadInfoForId` now finds the live
  Thread by id and reflects ThreadInfo's private 13-arg
  constructor to populate id/name/state. Native stub
  `getThreadInfoForId0` in `jcgogmt.c` is now unreferenced.
- [x] **`ThreadInfo.getStackTrace()`** populated for the
  current thread via a synthesized `new Throwable().getStackTrace()`
  (which doesn't re-enter the bean). Arbitrary-thread walk is
  still empty — JCGO's runtime can't suspend-and-walk other
  threads yet; `pthread_kill(SIGUSR2)` + sigaction handler on
  POSIX or `SuspendThread` + `StackWalk64` on Win32 would do
  it. Punted as separate work.
- ~~`ThreadMXBean.dumpAllThreads()`~~ — n/a; doesn't exist in
  classpath-0.93's `ThreadMXBean` interface (Java-6 addition that
  postdates the classpath release we're built against).

**Misleading values (lies about CPU time):**

- ~~`ThreadMXBean.getThreadCpuTime(long)` /
  `getThreadUserTime(long)`~~ — n/a; classpath's `ThreadMXBeanImpl`
  already gates on `isThreadCpuTimeSupported()` which checks the
  `gnu.java.lang.management.ThreadTimeSupport` system property
  (unset by JCGO), so both methods throw
  `UnsupportedOperationException` rather than returning 0. The
  underlying `getThreadCpuUserTime0` stub is unreachable.

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

### Stack traces + Java-level line info

End-to-end `Throwable.getStackTrace()` produces frames with real
`className`, `methodName`, `fileName`, and `lineNumber` populated
on MSVC builds. A separate crash dumper writes the same shape to
`<exe>.stk` when the binary fails before reaching user code.

- [x] **`Throwable.getStackTrace()`** — `include/jcgothrw.c` was
  three stubs; now wired:
  - **Win32 capture**: `CaptureStackBackTrace` returns frames into
    a `long[]` JCGO holds as opaque `vmdata`.
  - **Win32 symbol resolution**: DbgHelp `SymFromAddr` is the
    primary path (works for both MSVC + PDB and any binary whose
    symbols ended up in the COFF symtab); a self-contained PE
    export-table walk is the fallback for mingw + `-Wl,--export-all-symbols`
    builds.
  - **Win32 line resolution**: `SymGetLineFromAddr64` returns
    Java file + Java line, decoded by Java side into `fileName`
    + `lineNumber` on `StackTraceElement`. POSIX stays at line
    0 — no DWARF reader bundled, MSVC declared the canonical
    line-info path.
  - **Java demangle**: `VMThrowable.decodeMangledName` splits
    the mangled C name on `__`, strips the `package_` prefix,
    and replaces `_` with `.` to recover `(className,
    methodName)`. Best-effort; underscore-bearing or inner-class
    names decode imperfectly but a slightly garbled identifier
    pair is still vastly more useful than a hex address.
  - E2EVerify asserts on the trace: non-empty, contains `main`,
    top-frame demangles to `E2EVerify.main`. The fixture also
    dumps the full trace; sample on MSVC:
    ```
    at E2EVerify.traceLevel3 (E2EVerify.java:96)
    at E2EVerify.traceLevel2 (E2EVerify.java:96)
    at E2EVerify.traceLevel1 (E2EVerify.java:95)
    at E2EVerify.main      (E2EVerify.java:290)
    ```

- [x] **`#line` pragmas in generated C** (default on; opt-out
  with `-no-line-info`). Each method body opens with
  `#line N "Foo.java"` anchored to the body's first statement
  (Block.lineNum captures the closing `}` per parser quirk, so
  drilling into `Block.terms[1]` is needed). Per-statement
  directives fire from `ExprStatement` and `ReturnStatement`
  so DWARF/PDB carry Java-level positions throughout the
  function body, not just at entry. Sample E2EVrf.c gets ~180
  directives. The pragma must precede the C function
  declaration, not follow it — MSVC's PDB stores each
  function's anchor as `<last #line> + (C-line delta)` and a
  pragma after `{` attributes the entry to the previous
  method's tail. Test-e2e.sh asserts both the C-output
  presence and the round-trip via MSVC PDB strings-grep.

- [x] **Crash dumper** — `native/jcgocrash.c`, adapted from
  mobileui's editor crash_dump (AIBridge socket bits stripped).
  Auto-installs at process start (`.CRT$XCU` initializer on MSVC,
  `__attribute__((constructor))` on gcc); catches SEH +
  SIGSEGV/SIGABRT; walks the faulting thread's stack via
  `RtlVirtualUnwind` + `RtlLookupFunctionEntry`; resolves each
  frame via the same DbgHelp pair (`SymFromAddr` +
  `SymGetLineFromAddr64`); writes `<exe-basename>.stk` next to
  the binary AND mirrors to stderr. Useful both for actual
  crashes and as a sanity check that the `#line` pragma chain
  is end-to-end functional (a deliberately-failing test binary
  still produces a fully symbolized trace before dying).

- [x] **Win64 dirRead handle truncation** (incidentally surfaced
  while debugging the MSVC build's JNI-bootstrap segfault).
  `struct jcgo_tfind_s.handle` in `native/jcgofile.h` was typed
  `long` (32-bit on Win64). MSVC's `_findfirst` returns 64-bit
  `intptr_t`; the cast truncated the upper 32 bits, leaving a
  junk handle that crashed `_findnext` inside
  `RtlEnterCriticalSection`. Switched to `intptr_t` for MSVC.
  mingw was masking the bug because its UCRT happened to hand
  out small 32-bit-clean handle values; MSVC's UCRT hands out
  full pointer-shaped handles, so the truncation was
  immediately fatal. Standalone fix; not specific to the
  trace work, but blocked the MSVC verify binary from running
  the trace probe.

- [x] **Permanent MSVC build path in test-e2e.sh** — detects VS
  2022, stages a `.bat` driver that `call`s vcvars64.bat then
  invokes `cl.exe` with `/Zi /Od /MT /DJCGO_FFDATA
  /DJCGO_LARGEFILE /DJCGO_WIN32`, links `dbghelp.lib` +
  `legacy_stdio_definitions.lib` + standard system libs,
  produces verify_msvc.exe + verify_msvc.pdb. BDWGC linkage:
  prefers `libs/amd64/msvc/gc.lib` if present (gitignored;
  user runs `mkjcgo/build-win64-msvc.bat` once to produce it),
  falls back to `native/jcgogcstub.c` (linker-satisfying
  no-op stubs) for PDB-inspection-only builds. The asserts:
  `verify_msvc.pdb` strings-greps to `E2EVerify.java`; if
  the real lib was used, the binary actually runs through the
  full e2e fixture and produces a populated trace.

### Reachability for nested-annotation defaults

- [x] `ClassDefinition.registerAnnotationList` now follows
  member-method return types: when registering a Proxy for
  `@interface A`, walks A's methods and registers a Proxy for
  every return type that's also `@interface`. This fills B's
  `reflectedMethods` table so `fillInDefaults` can discover B's
  own defaults at runtime. Removed the `@Inner` workaround on
  `E2EVerify.allDefaults()` and confirmed the test still passes.

### Test coverage gaps

These features are claimed-done but the e2e fixture exercises only
narrow shapes. Real users will hit edge cases. Widen the fixture
before declaring full confidence.

- [x] **Annotation member defaults** — `int`, `String`,
  `Class`, enum, brace-array (`String[] default {"a","b","c"}`),
  and nested-annotation (`Inner default @Inner`) defaults all
  exercised in `E2EVerify`. Two fixes shipped along the way:
  (a) `@interface ElemType[] name()` form — Parser now threads
  the pre-name dim into the synthesized MethodDeclaration so the
  return type is the array type, not the component type;
  (b) nested-annotation default — `parseValueWithType` now
  calls `fillInDefaults` on the nested values map before
  building the proxy, otherwise members of the nested
  annotation IncompleteAnnotation-throw on access.
- [x] **`Constructor.getParameterAnnotations()`** — added
  `ParamAnnoCtor` fixture in `E2EVerify`. Found and fixed: the
  parser dropped per-parameter annotation ARG TEXT for
  constructors (only types were captured), so
  `@WithDefaults(level = 7)` on a ctor param reflected as default.
  `ConstrDeclaration.processPass1` now also calls
  `MethodDeclaration.collectParamAnnotationArgs` and threads it
  via `setParameterAnnotationArgsLists`.
- [x] **`@interface` const declarations** — added `Class KIND`
  and `int[] LIMITS` to `WithConsts`. Found and fixed: the
  `@interface` element parser captured the pre-name array dim
  for the constant-declaration branch but didn't thread it into
  the synthesized `FieldDeclaration` (only into the method-decl
  branch, fixed earlier). `int[] LIMITS = {1, 2, 3}` was being
  parsed as `int LIMITS = {…}`. Pre-name dim now flows to
  `FieldDeclaration.terms[1]`.
- [x] **`@Inherited` walk** — multi-level (Grandparent3 →
  Parent3 → Child3, all asserted) plus interface-chain (an
  annotation on an interface I, where C implements I; per JLS
  9.6.4.3 must NOT propagate, asserted false). Found and fixed:
  `Class.internalGetAnnotations` was walking
  `klass.getInterfaces()` and pulling their declared annotations
  into the inherited set, so `HasFamilyIface.isAnnotationPresent
  (Family)` returned true. JLS 9.6.4.3 says `@Inherited` walks
  the direct superclass chain only — interface walk removed.
- [x] **Repeating annotations** — added `@Score(value, level)`
  with `@Repeatable(Scores.class)` and asserted `multiScored
  getAnnotationsByType(Score.class)` returns both members with
  per-instance values. Worked first-shot — no fix needed.
- [x] **Cross-bound method dispatch** — added 3-bound type-var
  (`<X extends Number & Comparable & Describable>`) with
  dispatch on the third bound, plus field-access receiver
  (`h.val.describe()` where `val` is a generic field of a
  multi-bound type-var). Found and fixed: the path-style
  retry in `MethodInvocation.processPass1` was gated on
  `vecSize == 2` and looked up only single-name local vars.
  Extended to `vecSize >= 2` with a `resolvePathToVarDef`
  helper that walks dotted paths to find the deepest field's
  multi-bound secondaries; receiver-path extraction now uses
  `qn.terms[0]` directly (the head chain) since the QualifiedName
  layout is `terms[0]=head, terms[1]=last segment`.

### In-code `TODO #N` comment audit

A `grep -rn "TODO\b"` across `jtrsrc/`, `goclsp/vm/`, `include/jcgo*.c`,
`native/`, `examples/java17/`, `mkjcgo/test-e2e.sh` finds 34 hits.
Cataloged here so we can decide per-comment whether each is genuine
unfinished work or stale labeling on now-shipped code. Numbers (`#1`,
`#2`, ...) correspond to JCGO modernization slices; many of those
slices are now CHECKED IN this TODO.md. The convention so far has
been to keep the slice number as a permanent doc tag, not a "to do"
marker — but the literal text "TODO" reads as if work is pending.

`#1` (annotation member defaults — shipped):
  - `jtrsrc/com/ivmaisoft/jcgo/ClassDefinition.java:5411`
  - `jtrsrc/com/ivmaisoft/jcgo/MethodDeclaration.java:149`
  - `jtrsrc/com/ivmaisoft/jcgo/MethodDefinition.java:189`
  - `goclsp/vm/java/lang/reflect/Method.java:363`
  - `goclsp/vm/java/lang/reflect/VMMethod.java:952`
  - `goclsp/vm/java/lang/reflect/VMReflectAnnotations.java:172`
  - `goclsp/vm/java/lang/reflect/VMReflectAnnotations.java:271`
  - `include/jcgormet.c:154`

`#2` (annotation modifier bit — shipped):
  - `jtrsrc/com/ivmaisoft/jcgo/ClassDeclaration.java:104`
  - `jtrsrc/com/ivmaisoft/jcgo/ClassDefinition.java:707`
  - `jtrsrc/com/ivmaisoft/jcgo/Parser.java:4818`
  - `goclsp/vm/java/lang/reflect/VMReflectAnnotations.java:144`

`#3` (parameter annotation arg text — shipped):
  - `jtrsrc/com/ivmaisoft/jcgo/ClassDefinition.java:5345`
  - `jtrsrc/com/ivmaisoft/jcgo/MethodDeclaration.java:165`
  - `jtrsrc/com/ivmaisoft/jcgo/MethodDefinition.java:183`
  - `jtrsrc/com/ivmaisoft/jcgo/Parser.java:168`
  - `goclsp/vm/java/lang/reflect/Constructor.java:327`
  - `goclsp/vm/java/lang/reflect/Method.java:376`
  - `goclsp/vm/java/lang/reflect/VMMethod.java:946`
  - `include/jcgormet.c:142`

`#4` (built-in annotation resolution — shipped):
  - `jtrsrc/com/ivmaisoft/jcgo/ClassDictionary.java:602`
  - `goclsp/vm/java/lang/reflect/VMReflectAnnotations.java:701`

`#10` (cross-bound dispatch — shipped):
  - `jtrsrc/com/ivmaisoft/jcgo/Context.java:82`
  - `jtrsrc/com/ivmaisoft/jcgo/MethodDefinition.java:858`
  - `jtrsrc/com/ivmaisoft/jcgo/MethodInvocation.java:241`
  - `jtrsrc/com/ivmaisoft/jcgo/Parser.java:3359`
  - `jtrsrc/com/ivmaisoft/jcgo/Parser.java:3383`
  - `jtrsrc/com/ivmaisoft/jcgo/Parser.java:3565`
  - `jtrsrc/com/ivmaisoft/jcgo/VariableDefinition.java:124`
  - `jtrsrc/com/ivmaisoft/jcgo/VariableIdentifier.java:71`
  - `examples/java17/E2EVerify.java:43`

`#12` (iconv multi-byte charsets — shipped):
  - `jtrsrc/com/ivmaisoft/jcgo/Names.java:1435`
  - `include/jcgoiconv.c:5`
  - `examples/java17/E2EVerify.java:237`

Suspected status: every entry above is descriptive doc on shipped
work, not pending work — verified against TODO.md's `[x]` items and
against the surrounding code, which in each case implements what the
comment describes. Recommendation: rewrite each comment to drop
"TODO" (e.g. "Slice #N: ..." or just descriptive prose), so future
greps for unfinished work return clean. One comment to keep tagged
as actually-partial: `MethodDefinition.java:858` reads "TODO #10
partial:" — verify whether that's still partial or now full.

### Won't do

- **JDWP debugger natives.** Substantial native-runtime project
  (porting OpenJDK's JDWP agent on top of a translated runtime with
  no real JVM data structures). Android doesn't ship this either —
  ART has its own JDWP-like protocol. Attach a native debugger to the
  generated C instead.

