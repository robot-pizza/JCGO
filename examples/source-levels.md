# Source-level test fixtures

`examples/javaN/` directories hold copies of a small subset of
`examples/simple/` — `Hello.java`, `QSort.java`, `ListDir.java`,
`ShowProp.java` — progressively rewritten to use the newest language
features available at version N.

Each directory is the input to a translation test:

- **Positive:** `jcgo -source N` should translate the directory cleanly.
- **Negative:** `jcgo -source N-1` should reject the new constructs with a
  clear version error (the same gating mechanism that backs `assert` for
  `-source 1.4`).

Cumulative: a `javaN` fixture inherits all features available through
version N (e.g. `java8` keeps the `java7` idioms and adds lambdas).
For language details per version see `docs/java-versions.md`.

## Layout

| Dir            | Adds vs. previous                                  | Status (2026-05) |
|----------------|----------------------------------------------------|------------------|
| `java5/`       | foreach, generics, enums, annotations, varargs, autoboxing, static import, hex FP literals | Foreach, Varargs, StaticImport, Annotations, AnnotationType all translate end-to-end. ListDir.java rewritten with foreach. Other JLS-5 features (generics, enums, autoboxing, varargs call-site bundling, hex FP) await their slices. |
| `java6/`       | (no language additions)                            | mirrors java5 (no new features) |
| `java7/`       | try-with-resources, multi-catch, diamond, strings-in-switch, numeric underscores, binary literals | TryWithResources.java, MultiCatch.java added with `JCGO-SKIP` markers — design/intent only |
| `java8/`       | lambdas, method references, default + static interface methods, type annotations | Lambda.java, MethodRef.java added with `JCGO-SKIP` markers |
| `java9/`       | private interface methods, t-w-r on effectively-final, diamond on anon classes; modules (separate fixture) | base copies only — slice candidates are small, not yet written |
| `java10/`      | `var` local-variable type inference                | VarLocal.java added with `JCGO-SKIP` marker |
| `java11/`      | `var` in lambda parameters                         | base copies only |
| `java12/`      | (no language additions)                            | mirrors java11 |
| `java13/`      | (no language additions)                            | mirrors java11 |
| `java14/`      | switch expressions + `yield`                       | SwitchExpr.java added with `JCGO-SKIP` marker |
| `java15/`      | text blocks                                        | TextBlock.java added with `JCGO-SKIP` marker |
| `java16/`      | pattern-match `instanceof`, records, local records/enums/interfaces | Records.java, PatternInstanceof.java added with `JCGO-SKIP` markers |
| `java17/`      | `sealed`/`non-sealed`/`permits`                    | Sealed.java added with `JCGO-SKIP` marker |
| `java18/`      | (no language additions)                            | mirrors java17 |
| `java19/`      | (no language additions)                            | mirrors java17 |
| `java20/`      | (no language additions)                            | mirrors java17 |
| `java21/`      | pattern-match `switch` + `when`, record patterns   | SwitchPattern.java, RecordPatterns.java added with `JCGO-SKIP` markers |

## Test runner

`mkjcgo/test-source-levels.sh` runs the suite end-to-end. For every
`*.java` in each `examples/javaN/`:
- **Positive:** translate at `-source N`. Expected to succeed unless the
  file carries a `// JCGO-SKIP` marker in the first three lines (in which
  case it is documentation-only — features it uses aren't translated yet).
- **Negative:** for files in a version's known-fixture allowlist, also
  translate at the previous level and expect a `requires -source ...`
  version-gate error.

Skipped fixtures sit in the tree as design/intent documents — when the
underlying grammar slice lands, the marker comes off and the fixture
becomes a real positive test.

## Note on parser coverage

The fixtures with `// JCGO-SKIP` markers exercise features JCGO doesn't
yet parse (try-with-resources, lambdas, switch expressions, records,
sealed types, pattern matching, etc.). They live in the repo to document
what each version's idiomatic rewrite should look like; their tests are
skipped by the runner until the matching grammar slice lands. The
unmarked fixtures (Foreach, Varargs, StaticImport, Annotations,
AnnotationType in `java5/`) all translate end-to-end today.
