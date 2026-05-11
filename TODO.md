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
  (which doesn't re-enter the bean). **Arbitrary-thread walk
  shipped 2026-05-11:** `gnu_java_lang_management_VMThreadMXBeanImpl
  __captureThreadStackTrace0__Lo` in `jcgogmt.c` drives both
  platforms:
   - **Win32 x64**: `SuspendThread` on the target's HANDLE,
     `GetThreadContext`, walk via `RtlLookupFunctionEntry` +
     `RtlVirtualUnwind` in the same loop `native/jcgocrash.c` uses,
     `ResumeThread`. Verified manually: a spinner worker's trace
     surfaces its user method in `ThreadDump2.spin`.
   - **POSIX**: `pthread_kill(target, SIGUSR2)` + a sigaction
     handler that runs on the target, captures via `backtrace()`,
     posts a semaphore the caller is waiting on. Mutex-guarded
     single-capture-at-a-time. Untested on POSIX (no test rig)
     but structurally complete.
  Java bridge: `VMThread.getVmdata()` + `VMAccessorJavaLang
  .getVmdataVMThread(Thread)` exposes the per-thread TCB pointer
  across the package boundary; `VMThrowable.buildStackTrace(Object
  vmdata)` (refactored from the existing private path) renders the
  captured PCs through the same demangling + line-resolution chain
  Throwable uses.

  Win32 x86 still returns null from the native (no
  `RtlLookupFunctionEntry`). A `StackWalk64` fallback would close
  that gap.
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

### In-code `TODO #N` comment cleanup (done)

Earlier scan: `grep -rn "TODO\b"` across `jtrsrc/`, `goclsp/vm/`,
`include/jcgo*.c`, `native/`, `examples/java17/`,
`mkjcgo/test-e2e.sh` found 34 hits. All described shipped work
keyed by slice number — not pending tasks. Bulk-rewrote the prefix
to `Slice #N` so future greps for unfinished work return clean.

The lone `MethodDefinition.java:858` "TODO #10 partial" was stale:
`appendBoundSegments` actually handles both single-bound and
multi-bound (`<T extends A & B>`) forms via the `&`-split walk.
Updated to `Slice #10` and added a note in the comment that the
"partial" label was leftover from before multi-bound support.

Final state: zero `TODO` matches in production code.

### User-reported quirks (2026-05-10 round)

A user trying to run a real app through JCGO 21 reported seven quirks
(originally six, then a switch-on-enum one came in alongside). State
as of this round:

- [x] **#1 default -source 1.4.** `JavaVersion.DEFAULT = JLS_210`.
  Smoke harness still asserts each fixture under its own explicit
  `-source` so the change is invisible there.

- [x] **#3 generic / wildcard casts.** `(List<String>) x`,
  `(Map<?, ?>) y`, `(Foo<X>[]) z`. `UnaryWithPara` now peeks for the
  cast shape `Identifier(.Identifier)* <...> ([])* )` followed by a
  unary-starter and routes through `SimpleType` (which already
  handles wildcards via `captureGenericArgsToJls`). Fixture:
  `examples/java5/GenericCast.java`.

- [x] **#4 for-each iteration variable type as qualified name.**
  `for (com.foo.Window w : it)`. `looksLikeForeach` extended to walk
  dotted-id chains and optional `<...>` generic args so any
  well-formed type works in the foreach header. Fixture:
  `examples/java5/ForeachQualified.java`.

- [x] **#5 lambda inference into constructor args.**
  `new Action(label, () -> doX())` errored with "lambda needs an
  explicit functional-interface target type." InstanceCreation now
  mirrors `MethodInvocation.preProcessLambdaArgs`: resolves the type
  early, picks the unique constructor by arity, plumbs the matching
  formal's ExpressionType into each lambda / method-ref arg's
  processPass1 via `c.currentVarType`. Fixture:
  `examples/java8/LambdaCtorArg.java`.

- [x] **#6 lambda → parameterized SAM crash.** The reported
  AssertException — `field.setOnChange(value -> ...)` for
  `interface ChangeListener<T> { void onChange(T value); }` and
  `setOnChange(ChangeListener<String>)`. Two-part fix:
  (a) `Context.currentVarTypeArgsJls` plumbs the call site's
  parser-captured generic args alongside currentVarType;
  `MethodInvocation` + `InstanceCreation` look up the corresponding
  formal parameter's captured-args side channel and set both fields
  together. (b) `LambdaSynthesis.buildClassBody` parses the args
  string, looks up the iface's type-parameter names from
  `genericSignatureData`, and substitutes T → matching arg in the
  SAM's formal parameter types. When any substitution fires it runs
  `BridgeSynthesis.wrap` on the synthesized body so the typed
  `onChange(String)` gets an `onChange(Object)` bridge — preserving
  the SAM-dispatch override. Fixture:
  `examples/java8/LambdaGenericSam.java`.

- [x] **#2 synthetic CHECKCAST after `List<T>.get(i)`.**
  `attrs.get(i).serialize()` errored as
  `Undefined: Object.serialize(...)`. `LocalVariableDecl` and
  `FormalParameter` now thread the declared type's parser-captured
  generic args onto the variable's `VariableDefinition` (same
  `fieldTypeCapturedArgs` slot that `FieldDeclaration` was already
  using). `MethodInvocation.processPass1` then checks the chained
  receiver: when the receiver is a `MethodInvocation` whose return
  erases to Object and whose own receiver variable has captured
  args, wrap the inner call in a synthesized `CastExpression`
  before resolving the outer call. Two paths: when the inner
  method's slice-50 `returnTypeVarName` is set (user-source generic
  methods) the substitution looks up the type-var index in the
  defining class's `genericSignatureData`; the pre-generics
  classpath fallback fires when the receiver's captured args have
  exactly one type-arg (Collection<E>, List<E>, Iterator<E> and
  friends). Map<K, V> deliberately doesn't fire — picking K vs V
  would be a guess, and a wrong silent substitution beats the
  explicit-cast workaround there. Fixture:
  `examples/java5/GenericChainedGet.java`.

- [x] **#7 switch on enum rejected.** `SwitchStatement.processPass1`
  now detects an enum-typed discriminant
  (`superClass.name == java.lang.Enum`) and desugars to a temp
  local + if/else chain, same shape as the existing string-switch
  desugar (slice 7b). Each `case CONST` becomes
  `tmp == EnumType.CONST` with the label re-qualified to
  `EnumType.LABEL` so it resolves outside the switch's special
  scope. Multi-label / fall-through cases ride the existing
  pending-labels mechanism; `break` in case bodies works through
  the same do-while-0 output wrap. Switch-expression form rides
  for free because `SwitchExpressionLifter` lowers it to a
  `SwitchStatement` before this path runs. Fixtures:
  `examples/java5/EnumSwitch.java` (colon form, fall-through,
  default) and `examples/java14/EnumSwitchArrow.java` (arrow form,
  switch-expression form).

### Standards-conformance pass (2026-05-10)

The first round of fixes for the seven reported quirks landed but
deviated from javac in several spots: receiver-side heuristic
instead of real generic signature, no exhaustiveness check on
switch-expression, etc. A second pass tightens these toward javac
semantics. Smoke harness: positive 99/99, negative 43/43, skipped 3.

- [x] **P1: gate-suppress generics for toolchain files.**
  `Main.parseJavaFile` sets `Parser.inToolFile` /
  `Context.fileIsToolchain` when the file lives under
  `classpath-0.93/` or `goclsp/`. Every `JLS_xx` gate routes
  through `versionAtLeast` / `c.versionAtLeast`, which short-circuits
  for toolchain files. Lets the JDK overlays use generics / lambdas
  / etc. independently of the user-supplied `-source` level so
  older source levels stay backward-compatible.

- [x] **P2: JdkGenericOverlay.** classpath-0.93 predates JLS-5
  generics, so its `Map.get` etc. declare `Object` returns rather
  than `V`. Rather than modernize the classpath sources (which
  would have been a 2000+-line edit across the collection
  interfaces), `JdkGenericOverlay` registers the type-parameter
  list and per-method return-type-var for the JDK collection /
  iterator / map hierarchy. ClassDefinition.getGenericTypeParamNames
  and MethodDefinition.getReturnTypeVarName fall back to the overlay
  when slice-50 retention didn't capture the info.

- [x] **P3: real generic resolution for chained generic-method
  calls (#2 follow-up).** `MethodInvocation.trySubstituteChainedGenericReturn`
  now drives off the inner method's `getReturnTypeVarName` and the
  defining class's `getGenericTypeParamNames`, looking up the
  type-var's index and pulling that index from the receiver's
  captured args. `Map<K, V>.get → V` resolves correctly. The
  single-captured-arg receiver-side heuristic stays as a fallback
  for user-defined generic classes that pre-date their own slice-50
  retention.

- [x] **#8: AssertException on lambda-via-setter into non-generic
  SAM field.** Pre-existing JCGO bug surfaced by the user's third
  report — even on clean master, a lambda stored in a field and
  never invoked crashed at the Output pass because the
  LambdaExpression placeholder's tree walks (discoverObjLeaks,
  allocRcvr, isAnyLocalVarChanged) descended into nodes inside the
  synth class body that were never pass1'd (the SAM's method body
  never got marked-used). Fix: after lift, `LambdaExpression`
  replaces its own `terms[0]` / `terms[1]` with `Empty.term` and
  overrides `discoverObjLeaks` / `writeStackObjs` to delegate to
  `lifted`. All subsequent walks route exclusively through the
  lifted InstanceCreation. Fixture:
  `examples/java8/LambdaUncalledSetter.java`.

- [x] **P4: same-arity ctor / method overload narrowing
  (partial).** `narrowByLambdaShapeRespectingArity` narrows
  same-arity candidates to those whose param at each lambda /
  method-ref arg's position is a functional interface with matching
  SAM arity. Handles the common case of mixed overloads where the
  non-FI variants are present (e.g.
  `X(String, String)` vs `X(String, Runnable)` — pick Runnable).
  Genuinely-ambiguous all-FI overloads
  (`X(String, Runnable)` vs `X(String, Supplier<Integer>)`) still
  need full lambda-body return-type inference and surface the
  existing "lambda needs an explicit functional-interface target
  type" error.

- [x] **P5: SAM return-type substitution.** Wired
  `LambdaSynthesis.resolveTypeVarReturn` — looks up the SAM's
  `getReturnTypeVarName` (slice-50 retention or overlay fallback),
  finds the type-var's index in the iface's
  `getGenericTypeParamNames`, and pulls the substituted ExpressionType
  from the call site's captured args. The synthesized method's
  return type now matches the substituted SAM return rather than
  the erased Object. Fixture: `examples/java8/LambdaSupplier.java`.

- [x] **P6: switch-expression exhaustiveness on enum.**
  `SwitchStatement.markFromSwitchExpression` set by
  SwitchExpressionLifter when it lifts an expression-shaped switch.
  `checkEnumSwitchExpressionExhaustive` runs at pass1: walks the
  case labels, collects covered enum constants, compares against
  the enum class's static-final-self-typed fields, rejects with a
  javac-style "switch expression does not cover all possible input
  values" message when a constant is unmatched and no default arm
  is present. Fixtures:
  `examples/java14/EnumSwitchExhaustive.java` (positive),
  `examples/java14/EnumSwitchNonExhaustive.java` (inv_14 negative
  asserts the error).

- [x] **P7: unchecked-cast diagnostic.** Shipped in the round-2
  follow-up — see the "Deviations from javac — round-2 closes"
  section below for the final implementation.

### Deviations from javac — round-2 closes (audited)

After the first standards-pass shipped, a second round closed each
of the documented deviations:

- [x] **D4: `case null` in enum switch** (JLS 21). `buildEnumEq`
  detects null labels and emits `tmp == null` rather than
  `tmp == EnumType.null`. Exhaustiveness check skips null labels
  (they're orthogonal to constant coverage). Parity verified
  against `java NullCase` — both emit `0 / 1 / 2`.

- [x] **D3: subclass-fixed type-args via superclass walk.** Parser's
  `ExtendsType` now uses `captureGenericArgsToJls` so the extends
  clause's generic args are retained alongside the type-param list.
  `ClassDeclaration.processPass0` threads them onto the
  ClassDefinition via `setSuperClassCapturedArgs`. MethodInvocation's
  chained-call substitution walks the receiver class's superclass
  chain via `findInheritedCapturedArgs` when the use-site variable
  itself has no captured args. `class SL extends ArrayList<String>`
  then `sl.get(0).length()` resolves to String.

- [x] **D2: propagate captured args through synthesized cast.**
  When the chained-call substitution wraps the inner call in a
  `CastExpression`, extract the inner generic-args slot from the
  outer captured-args JLS form (e.g. for outer
  `<Ljava/util/List<Lpkg/X;>;>` index 0 → `<Lpkg/X;>`) and stash
  on the cast via `MethodInvocation.synthCastArgs`. The next
  chained call's substitution reads the cast's stashed args before
  giving up. `outer.get(0).get(0).length()` for
  `List<List<String>>`, triple-nested chains, and
  `Map<K, List<V>>.get(k).get(0)` all resolve.

- [~] **D1: lambda overload by body return-type shape** — partial.
  `classifyLambdaShape` distinguishes block-with-return (VALUE),
  block-no-return (VOID), and expression bodies whose terminal
  shape is a literal / arithmetic / new-instance (VALUE).
  `narrowByLambdaShapeRespectingArity` filters same-arity FI
  candidates by SAM-return-type vs the classified shape. Pins
  `new X("k", () -> 9 + 1)` to `Sup<Integer>`,
  `new X("k", () -> { return 7; })` to `Sup<Integer>`,
  `new X("k", () -> { sink[0]++; })` to `Runnable`.
  Residual: expression-body lambdas whose terminal is a
  MethodInvocation / Assignment / Postfix++/-- can't be
  disambiguated syntactically (they could be void OR value
  depending on the called method's return) — these still surface
  the existing "lambda needs explicit target type" error. javac
  resolves these via speculative pass1 against each candidate;
  JCGO's pass1 isn't easily reversible, so this case stays
  user-disambiguated.

- [x] **P7: unchecked-cast lint warnings.** Per-cast `warning:`
  notice with `file (line, col)` location, matching the existing
  `SemErr` format. `ErrorStream.Warn` doesn't increment the
  error count — translation succeeds, the user just gets a
  stderr message. `Parser.Warning` / `Parser.WarningAt` skip
  toolchain files (classpath-0.93 / goclsp) so internal casts
  don't generate noise. Suppressed globally via `-nowarn`.
  Wildcard-only casts (`(Map<?, ?>) x`) don't warn — they verify
  fully at runtime against the erased class plus an unconstrained
  parameter slot. The warnings infra is intentionally minimal but
  extensible — next lint diagnostic (deprecation, rawtypes, etc.)
  can ride the same path.

### D6: for-each over Iterable (closed)

- [x] **For-each over a real Iterable** silently miscompiled with the
  slice-1 array-walk desugar — `(jObjectArr) list` was a runtime
  crash. `ForeachStatement.processPass1` now pre-pass1's the iter to
  inspect its exprType, and routes to either the original array
  desugar OR a new iterator desugar
  `Iterator $it = iter.iterator(); while ($it.hasNext()) { T x = (T)
  $it.next(); body }` matching javac's bytecode lowering (verified
  by `javap -c`). var-foreach over Iterable resolves the element
  type from iter's slice-50 captured args so `for (var x : list)`
  picks up String rather than the erased Object. Fixtures:
  `examples/java5/ForeachIterable.java`,
  `examples/java10/VarForeachIterable.java`. Parity verified by
  running the translated binary alongside `java` on the same source
  — both emit `7` for the IterFE probe.

### Won't do

- **JDWP debugger natives.** Substantial native-runtime project
  (porting OpenJDK's JDWP agent on top of a translated runtime with
  no real JVM data structures). Android doesn't ship this either —
  ART has its own JDWP-like protocol. Attach a native debugger to the
  generated C instead.

