# Java Versions: Source-Language Changes (1.4 → 21)

This document tracks **source-language** changes per Java version, scoped to
what a parser/AST has to handle. It exists to inform the JCGO grammar
modernization effort: each `-source` level we support corresponds to a slice
of these features.

**Final-only.** Preview features are excluded — they're noted only on the
release where they went final, with the trail back through earlier previews
so the long-tail stays auditable. JVM, library, GC, and tooling changes are
out of scope.

**Out of scope:** library additions (`java.util.stream`, `java.time`, etc.),
JVM/bytecode (`invokedynamic`, value classes, Loom), GC algorithms, and CLI
tooling. JCGO is a *Java→C source translator*, so we care about what changes
between `.java` files at level N and level N+1.

**Android note (2026-05).** Android Gradle Plugin documents a maximum
`sourceCompatibility` of **Java 17**; Android 14 / API 34 supports Java 17,
Android 13 / API 33 supports Java 11. Some Java 17+ features compile to
older bytecode (compiler-only) but others require library support not
present on Android. Java 21 is not currently a supported source level on
Android. See <https://developer.android.com/build/jdks>.

---

## 1.4 (J2SE 1.4, 2002)

- **New reserved keyword:** `assert`. Breaking change for code that used
  `assert` as an identifier under 1.3 — javac accepts `-source 1.3` to
  suppress.
- **New construct:** `assert Expression ;` and `assert Expression : Expression ;`
  statements.
- No other source-language additions (regex, NIO, logging are libraries).

Source: <https://docs.oracle.com/javase/6/docs/technotes/guides/language/assert.html>

JCGO status (1.16): `assert` accepted; gated on `-source 1.4` as of phase 1
of the version-flag plumbing.

---

## 5 (J2SE 5.0, 2004) — JSR 14, JSR 175, JSR 201, JSR 202

- **New reserved keyword:** `enum`.
- **New constructs:**
  - **Generics**: parameterized types `List<E>`, type parameters
    `<T>` on classes/methods, bounded wildcards `? extends T` / `? super T`,
    explicit method type arguments `Collections.<String>emptyList()`.
  - **Enhanced `for` (foreach)**: `for (Type x : iterableOrArray) { ... }`.
  - **Enum declarations**: `enum Color { RED, GREEN, BLUE }` with bodies and
    abstract methods per constant.
  - **Annotations**: declaration syntax `@interface`, application syntax
    `@Foo`, `@Foo(name=val)`, `@Foo(val)` shorthand, `@Foo({a,b})` array
    shorthand.
  - **Varargs**: `void f(String... args)`; trailing args become an array at
    the call site.
  - **Static import**: `import static pkg.Type.MEMBER;` and
    `import static pkg.Type.*;`.
  - **Hex floating-point literals**: `0x1.8p1` etc. (lexer change).
- **Parsing-relevant semantics:**
  - **Autoboxing/unboxing** between primitive and wrapper types — affects
    overload resolution, not grammar directly, but every type-aware AST
    pass cares.
  - **Generic type erasure**; raw-type warnings.
  - **Covariant return types** at language level.

Sources: <https://docs.oracle.com/cd/E19253-01/817-7970/features-enums/index.html>,
<https://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html>

JCGO status (1.16): grammar partially recognizes `@Foo` / `@Foo(arg)` form
(`jcgo.atg:175-182`) but not the full `name=val` form, no enums, no
generics, no foreach, no varargs, no static import.

---

## 6 (Java SE 6, 2006)

No source-language additions. Maintenance release at the language level;
all changes were libraries and JVM.

Source: <https://docs.oracle.com/javase/specs/jls/se6/html/j3TOC.html>

---

## 7 (Java SE 7, 2011) — Project Coin (JSR 334)

- **No new reserved keywords.**
- **New constructs:**
  - **try-with-resources**: `try (R r = ...; R2 r2 = ...) { ... }`. Requires
    `AutoCloseable`.
  - **Multi-catch**: `catch (A | B e) { ... }`.
  - **Diamond `<>`**: `new ArrayList<>()` — type arguments inferred from
    context.
  - **Strings in `switch`**: `switch (s) { case "x": ... }`.
  - **More precise rethrow**: `throws` clause checking treats a `final` (or
    effectively-final) caught exception variable as the union of types
    actually thrown in the `try`.
  - **`@SafeVarargs`** annotation recognized.
- **New lexical features:**
  - **Binary integer literals**: `0b1010_1010`, `0B...`.
  - **Underscores in numeric literals**: `1_000_000`, `0xFFFF_FFFFL`,
    `3.14_15F`. Underscores allowed only between digits — not adjacent to
    `.`, the `0x`/`0b` prefix, the type suffix, or sign.

Sources: <https://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html>,
<https://jcp.org/en/jsr/detail?id=334>

---

## 8 (Java SE 8, 2014) — JSR 335, JSR 308

- **No new reserved keywords.** `default` reused for interface default
  methods.
- **New constructs:**
  - **Lambda expressions**: `(x, y) -> x + y`, `x -> x.foo()`,
    `() -> {...}`, with optional explicit parameter types
    `(int x, int y) -> ...`. Target type comes from a functional interface.
  - **Method references**: `ClassName::method`, `instance::method`,
    `ClassName::new`, `super::method`.
  - **Default methods on interfaces**: `interface I { default int f() { ... } }`.
  - **Static methods on interfaces**: `interface I { static int g() { ... } }`.
  - **Type annotations (JSR 308)**: annotations on any *use* of a type —
    `List<@NonNull String>`, `(@NonNull String) o`, `new @Foo Bar()`,
    `extends @Bar Object`.
  - **Repeating annotations**: `@Foo @Foo` allowed when `@Foo` is
    `@Repeatable(Foos.class)`.
- **Parsing-relevant semantics:**
  - **"Effectively final"**: lambdas and inner classes may capture local
    variables that are not declared `final` if they're never reassigned
    after initialization. Compiler must track this.
  - **Improved generic target-type inference** (JEP 101) — affects nested
    generic call type-checking.
- **Restricted at language level:**
  - **`_` (single underscore) as identifier**: now produces a *warning*;
    reserved for future use.

Sources: <https://openjdk.org/jeps/126>,
<https://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html>

---

## 9 (Java SE 9, 2017) — JEP 213, JEP 261

- **New restricted (contextual) keywords** — only inside `module-info.java`:
  `module`, `open`, `requires`, `transitive`, `exports`, `opens`, `to`,
  `uses`, `provides`, `with`. Remain valid identifiers everywhere else.
- **New constructs:**
  - **Module declarations**: `module-info.java` with
    `module M { requires X; exports p; opens p to M2; uses S; provides S with Impl; }`.
    A new compilation unit form.
  - **Private interface methods**: `private` and `private static` methods
    on interfaces (JEP 213).
- **Parsing-relevant semantics:**
  - **Try-with-resources on effectively-final variables**:
    `try (existingFinalVar) { ... }` — resource expression no longer
    required to be a fresh declaration.
  - **`@SafeVarargs` allowed on private instance methods.**
  - **Diamond on anonymous classes**: `new Foo<>() {...}` legal where the
    inferred type is denotable.
- **Removed at language level:**
  - **`_` (single underscore) as identifier**: now a **hard compile error**.
    Reserved for future language use (later filled by unnamed
    variables/patterns in JDK 22).

Sources: <https://openjdk.org/jeps/213>,
<https://docs.oracle.com/javase/specs/jls/se9/html/jls-3.html>

---

## 10 (March 2018) — JEP 286

- **New restricted (contextual) keyword:** `var` — recognized only in
  `LocalVariableType` position. Remains a legal identifier elsewhere;
  **cannot** be used as a class/interface/type name (`TypeIdentifier`
  excludes `var`).
- **New construct:**
  - **Local-variable type inference** (`var`): in local variable
    declarations with initializer, `for` loop indexes, enhanced-for
    variables, try-with-resources resources. Requires an initializer; not
    allowed for fields, method parameters, or returns.

Sources: <https://docs.oracle.com/en/java/javase/11/language/local-variable-type-inference.html>,
<https://openjdk.org/jeps/286>

---

## 11 (Sep 2018) — JEP 323

- **New construct:**
  - **`var` in lambda formal parameters**: `(var x, var y) -> x + y`. Allows
    annotations on inferred lambda params: `(@NonNull var x) -> ...`. All
    parameters in one lambda must use `var` or none.
- No new keywords. No other final language changes.

Source: <https://openjdk.org/jeps/323>

---

## 12 (March 2019)

No final source-language additions. (Switch expressions arrived as preview
only — JEP 325.)

---

## 13 (Sep 2019)

No final source-language additions. (Switch expressions still preview —
JEP 354. Text blocks first preview — JEP 355.)

---

## 14 (March 2020) — JEP 361

- **New restricted (contextual) keyword:** `yield` — statement keyword in
  switch expression bodies. Remains a valid identifier (incl. method names)
  outside that context.
- **New construct:**
  - **Switch expressions** (final): `switch (x) { case A, B -> expr; case C -> { ...; yield v; } }`.
    - Arrow form `case L ->` (no fall-through, RHS is expression / block /
      throw).
    - Multiple labels per case via comma: `case A, B, C ->`.
    - `yield Expression;` to produce the value of a block-form case in an
      expression context.
    - Exhaustiveness required for switch *expressions* (compiler inserts
      `default` for total enum switches).
- **Preview history:** JEP 325 (12) → JEP 354 (13) → **JEP 361 (14 final)**.

Sources: <https://docs.oracle.com/en/java/javase/14/language/switch-expressions.html>,
<https://openjdk.org/jeps/361>

---

## 15 (Sep 2020) — JEP 378

- **New construct (lexical):**
  - **Text blocks** (final): triple-quoted multi-line string literals
    `""" ... """`. Opening `"""` must be followed by a line terminator;
    common leading whitespace is stripped per the spec's algorithm.
  - **New text-block escapes** (added during preview, kept in final): `\s`
    (single space, prevents trailing-whitespace stripping) and
    `\<line-terminator>` (line continuation — suppresses the newline).
- **Preview history:** JEP 355 (13) → JEP 368 (14) → **JEP 378 (15 final)**.

Sources: <https://docs.oracle.com/en/java/javase/15/language/text-blocks.html>,
<https://openjdk.org/jeps/378>

---

## 16 (March 2021) — JEP 394, JEP 395

- **New restricted (contextual) keyword:** `record` (in `RecordDeclaration`
  position).
- **New constructs:**
  - **Pattern matching for `instanceof`** (final, JEP 394):
    `if (obj instanceof Type t) { ...t... }`. `t` is a *flow-scoped binding*
    — in scope only where the test must be true. Composes with `&&` (in
    scope on RHS) but not `||`.
  - **Records** (final, JEP 395): `record Point(int x, int y) {}` — header
    is the canonical-constructor parameter list; auto-generates
    `private final` fields, accessors of the same name (`p.x()`), `equals`,
    `hashCode`, `toString`. Records are implicitly `final`, implicitly
    extend `java.lang.Record`, cannot declare instance fields outside the
    header. Compact constructor: `public Point { ... }` (no parameter list,
    no explicit assignments).
- **Other:**
  - **Local interfaces, enums, and records** allowed inside methods (along
    with previously-allowed local classes). Local records/enums are
    implicitly `static`.
- **Preview history (instanceof):** JEP 305 (14) → JEP 375 (15) →
  **JEP 394 (16 final)**. **(Records):** JEP 359 (14) → JEP 384 (15) →
  **JEP 395 (16 final)**.

Sources: <https://docs.oracle.com/en/java/javase/16/language/pattern-matching-instanceof-operator.html>,
<https://docs.oracle.com/en/java/javase/16/language/records.html>

---

## 17 (Sep 2021) — JEP 409

- **New restricted (contextual) keywords:** `sealed`, `non-sealed`,
  `permits` (in class/interface declaration position). `non-sealed` is the
  only *hyphenated* contextual keyword in Java's grammar.
- **New construct:**
  - **Sealed classes & interfaces** (final, JEP 409):
    `sealed class S permits A, B, C {}`. Every direct subtype of a sealed
    type must be declared `final`, `sealed`, or `non-sealed`. The `permits`
    clause is optional iff all permitted subtypes are in the same
    compilation unit. Records, being implicitly `final`, are eligible
    permitted subtypes.
- **Removed at language level:**
  - **`strictfp` modifier behavior**: still a keyword, but now a no-op (all
    FP is strict). Doesn't change parsing.
- **Preview history:** JEP 360 (15) → JEP 397 (16) → **JEP 409 (17 final)**.

Sources: <https://docs.oracle.com/en/java/javase/17/language/sealed-classes-and-interfaces.html>,
<https://openjdk.org/jeps/409>

---

## 18 (March 2022)

No final source-language additions. (Pattern matching for switch still
preview — JEP 420.)

---

## 19 (Sep 2022)

No final source-language additions. (Pattern matching for switch — JEP 427
preview; record patterns first preview — JEP 405.)

---

## 20 (March 2023)

No final source-language additions. (Pattern matching for switch — JEP 433
preview; record patterns — JEP 432 preview.)

---

## 21 (Sep 2023, LTS) — JEP 440, JEP 441

- **New restricted (contextual) keyword:** `when` — guard introducer inside
  `switch` case labels. Remains a valid identifier elsewhere.
- **New constructs:**
  - **Pattern matching for `switch`** (final, JEP 441): type patterns and
    the `null` pattern in case labels; guarded patterns via
    `case P when Cond ->`. Selector type extended to any reference type or
    `int`-family primitives (not `long`/`float`/`double`/`boolean`).
    Exhaustiveness required for switch expressions and for switch
    statements that use any non-constant case label; uses sealed-hierarchy
    info. Pattern dominance is checked statically. `case null` (and
    `case null, default`) are now legal labels — without them, a `null`
    selector still throws NPE.
  - **Record patterns** (final, JEP 440): deconstruction patterns
    `Point(int x, int y)` usable in `instanceof` and `switch` case labels,
    nestable (`Box(Point(var x, var y))`), and may use `var` per component.
    `null` does not match a record pattern. Generic record patterns infer
    type arguments.
- **Preview history (switch):** JEP 406 (17) → JEP 420 (18) → JEP 427 (19)
  → JEP 433 (20) → **JEP 441 (21 final)**. **(Record patterns):** JEP 405
  (19) → JEP 432 (20) → **JEP 440 (21 final)**.
- **Still preview in 21 (excluded):** Unnamed patterns and variables (`_`)
  — JEP 443 in 21, finalized as JEP 456 in JDK 22.

Sources: <https://docs.oracle.com/en/java/javase/21/language/pattern-matching-switch.html>,
<https://docs.oracle.com/en/java/javase/21/language/record-patterns.html>,
<https://docs.oracle.com/javase/specs/jls/se21/html/jls-3.html>

---

## Keyword timeline (cheat sheet)

| Token | Added | Reserved or contextual? |
|---|---|---|
| `assert` | 1.4 | reserved |
| `enum` | 5 | reserved |
| `module`, `requires`, `exports`, `opens`, `to`, `uses`, `provides`, `with`, `transitive`, `open` | 9 | contextual (module-info only) |
| `var` | 10 | contextual (`LocalVariableType`, lambda param) |
| `yield` | 14 | contextual (switch-expression block) |
| `record` | 16 | contextual (class-decl position) |
| `sealed`, `non-sealed`, `permits` | 17 | contextual (class/interface decl) |
| `when` | 21 | contextual (switch case guard) |
| `_` | 9 | reserved (illegal identifier; later given meaning in 22) |
