# Varargs call-site bundling — algorithm reference

This document captures the JLS algorithm and the concrete plan for slice 2b
(varargs call-site bundling). Plumbing is already done (commit `94465aa`):
`MethodSignature.isVarArgs` is set when `MethodDefinition.methodSignature()`
constructs the signature from a varargs `paramList`. The AST carries the
right info; the resolution and bundling steps still need to be wired.

## JLS reference

JLS Java SE 21 §15.12.2 specifies a **three-phase** method-invocation
resolution. At each phase, the compiler computes the set of *applicable*
methods. As soon as a phase produces a non-empty set, it picks the most
specific (§15.12.2.5) and stops.

- **Phase 1 — Strict invocation (§15.12.2.2).** Arity must match exactly,
  no boxing/unboxing, only widening reference and primitive conversions.
- **Phase 2 — Loose invocation (§15.12.2.3).** Arity must match exactly,
  boxing/unboxing allowed.
- **Phase 3 — Variable arity invocation (§15.12.2.4).** A method `f(T₁, …,
  T_{n-1}, F_n[])` declared `f(T₁, …, T_{n-1}, F_n... )` is considered
  applicable to a call with `k` actuals if `k ≥ n - 1` and:
  - For `i ∈ [0, n-1)`: actual[i] is loose-compatible with `T_{i+1}`.
  - For `i ∈ [n-1, k)`: actual[i] is loose-compatible with the **element
    type** `F_n` (i.e. `F_n[]` with one less dim).

Because Phase 1 always wins over Phase 3 when both could match, fixed-arity
overloads automatically beat varargs ones — we don't need an explicit
priority rule.

### Bundling decision (§15.12.4.2)

After resolution chooses a variable-arity method `m`, the source-level
behaviour is *as if* the trailing actuals were wrapped in a synthetic
array. Concretely, given `n` formals and `k` actuals:

> Bundle iff `k != n` OR (`k == n` AND the last actual's type is **not**
> assignment-compatible with `F_n[]`).

Equivalently, the only no-bundle case is `k == n && actuals[n-1].type <:
F_n[]` — i.e. the caller already passed an array of the right type.

## Concrete cases (verified against the spec)

| Call | Decision |
|------|----------|
| `f()` for `void f(int... v)`         | bundle → `new int[0]` |
| `f(1,2,3)` for `void f(int... v)`    | bundle → `new int[]{1,2,3}` |
| `f(myArr)` for `void f(int... v)` (`myArr: int[]`) | **no bundle**, pass directly |
| `f((int[]) null)` for `void f(int... v)` | **no bundle** (cast forces array type) |
| `f(null)` for `void f(int... v)` | **no bundle** — Phase 1 already absorbs this since `null <: int[]` |
| `f("a","b")` for `void f(Object... v)` | bundle → `new Object[]{"a","b"}` |
| `f(1)` with both `f(int)` and `f(int...)` declared | `f(int)` wins (Phase 1 > Phase 3) |

## Algorithm to implement

### Phase 3 applicability test (new code)

```
function isApplicableByVarargs(candidate, actuals):
    if not candidate.isVarArgs: return false
    n = candidate.formals.length
    k = actuals.length
    if k < n - 1: return false
    elementType = candidate.formals[n-1].componentType   # strip [] off F_n

    # Check fixed prefix.
    for i in [0, n-1):
        if not looseCompatible(actuals[i].type, candidate.formals[i]): return false
    # Check trailing actuals against ELEMENT type.
    for i in [n-1, k):
        if not looseCompatible(actuals[i].type, elementType): return false
    return true
```

### Bundling decision (new code)

```
function shouldBundle(candidate, actuals):
    if not candidate.isVarArgs: return false
    n = candidate.formals.length
    k = actuals.length
    if k != n: return true
    return not assignmentCompatible(actuals[n-1].type, candidate.formals[n-1])
```

### Top-level resolution flow

```
function resolveMethod(name, actuals, candidates):
    set1 = [m for m in candidates if isApplicableStrict(m, actuals)]
    if set1 nonempty: return mostSpecific(set1, actuals)
    set2 = [m for m in candidates if isApplicableLoose(m, actuals)]
    if set2 nonempty: return mostSpecific(set2, actuals)
    set3 = [m for m in candidates if isApplicableByVarargs(m, actuals)]
    if set3 nonempty: return mostSpecific(set3, actuals)
    error("no applicable method")
```

JCGO's current `ClassDefinition.matchMethod` collapses Phases 1 and 2
into a single cost-based pass via `MethodSignature.match()` (which already
allows widening but not boxing — JCGO doesn't do boxing yet). For slice 2b,
add a Phase 3 fallback that runs if `match()` returns no candidate.

## Where to plug in

- **`ClassDefinition.matchMethod` (`ClassDefinition.java:3122`)** — after
  the existing exact + iter pass returns null, run a varargs scan: iterate
  the method dictionary by name, filter to `isVarArgs()` methods, for each
  call a new `matchVarargs(actualMsig, forClass)` on its `MethodSignature`,
  return the lowest-cost candidate.
- **`MethodSignature.matchVarargs` (new method)** — mirrors the existing
  `match` but uses the element type of the trailing formal for positions
  ≥ n-1, and returns MAX_INT if `k < n-1`.
- **`MethodInvocation.processPass1` (`MethodInvocation.java:120`)** — after
  `md = resultClass.matchMethod(...)` returns a varargs method, decide
  bundle/no-bundle per §15.12.4.2 and rewrite `terms[2]` if bundling is
  needed.

### AST mutation for bundling

`terms[2]` is the call-site argument list — a chain of `ParameterList(arg,
rest)` nodes terminating in a single `Argument`. To bundle:

1. Walk to position `n-1` in the chain.
2. Collect all trailing actuals (their wrapped expressions).
3. Build a synthetic `AnonymousArray(elementTypeTerm, new
   DimSpec(Empty.newTerm()), arrayInitializer)` where `arrayInitializer`
   is a right-leaning chain of `VarInitializers(ArrElementInit(expr),
   ...)` (or a single `ArrElementInit` for one element).
4. Wrap the new array in a single `Argument`.
5. Splice the new chain so it ends with the bundled arg.
6. Re-run `processPass1` on the spliced subtree so `AnonymousArray` and
   the new `Argument` get their cached `resType` / `exprType0` set. The
   already-pass1'd inner expressions can be reused.

### Open subproblem: elementTypeTerm

To construct `AnonymousArray`, we need a **Term** representing the
element type. JCGO's pass1 only retains an `ExpressionType`. Three
options:
- **Pass through:** `MethodDefinition` keeps a reference to the original
  `FormalParameter` (or its type Term) and exposes it. Cleanest.
- **Reconstruct from ExpressionType:** look up the class definition,
  build a fresh `ClassOrIfaceType(name)` or `PrimitiveType(typeCode)`.
  Works but loses any annotation/dim info — fine for our use.
- **Helper on `ExpressionType.toTypeTerm()`:** centralise option 2.

Recommend option 1 long-term, option 2 for slice 2b expediency.

## javac source pointer

OpenJDK reference implementation:

- Phase enum (`MethodResolutionPhase`):
  <https://github.com/openjdk/jdk/blob/master/src/jdk.compiler/share/classes/com/sun/tools/javac/comp/Resolve.java>
  (search for `enum MethodResolutionPhase` — three values: `BASIC`, `BOX`,
  `VARARITY`, plus `methodResolutionSteps = List.of(BASIC, BOX, VARARITY)`).
- Applicability iteration (`AbstractMethodCheck.argumentsAcceptable`): the
  hot 50-line function that walks formals + actuals. Pulls `useVarargs`
  from the phase, treats the last formal as element type once the fixed
  prefix is consumed.

The bundling decision lives separately in `Lower.java` / `Attr.java` —
javac's resolver only answers yes/no on applicability; codegen is where
the synthetic `new T[]{...}` gets generated.

## Sources

- [JLS SE 21 §15.12.2](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12.2)
- [JLS SE 21 §15.12.4.2](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12.4.2)
- [JLS SE 8 §15.12.2.4](https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.12.2.4) (text identical for the varargs rule)
- [OpenJDK `Resolve.java`](https://github.com/openjdk/jdk/blob/master/src/jdk.compiler/share/classes/com/sun/tools/javac/comp/Resolve.java)
