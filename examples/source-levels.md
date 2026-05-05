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
| `java5/`       | foreach, generics, enums, annotations, varargs, autoboxing, static import, hex FP literals | foreach landed (ListDir uses it; QSort/ShowProp loops aren't natural foreach fits; other JLS-5 features pending grammar slices) |
| `java6/`       | (no language additions)                            | base copies (will mirror `java5/` after Stage 1) |
| `java7/`       | try-with-resources, multi-catch, diamond, strings-in-switch, numeric underscores, binary literals | base copies (Stage 2 pending) |
| `java8/`       | lambdas, method references, default + static interface methods, type annotations | base copies (Stage 3 pending) |
| `java9/`       | private interface methods, t-w-r on effectively-final, diamond on anon classes; modules (separate fixture) | base copies (Stage 4 pending) |
| `java10/`      | `var` local-variable type inference                | base copies (Stage 4 pending) |
| `java11/`      | `var` in lambda parameters                         | base copies (Stage 4 pending) |
| `java12/`      | (no language additions)                            | base copies (will mirror `java11/`) |
| `java13/`      | (no language additions)                            | base copies (will mirror `java11/`) |
| `java14/`      | switch expressions + `yield`                       | base copies (Stage 5 pending) |
| `java15/`      | text blocks                                        | base copies (Stage 5 pending) |
| `java16/`      | pattern-match `instanceof`, records, local records/enums/interfaces | base copies (Stage 5 pending) |
| `java17/`      | `sealed`/`non-sealed`/`permits`                    | base copies (Stage 5 pending) |
| `java18/`      | (no language additions)                            | base copies (will mirror `java17/`) |
| `java19/`      | (no language additions)                            | base copies (will mirror `java17/`) |
| `java20/`      | (no language additions)                            | base copies (will mirror `java17/`) |
| `java21/`      | pattern-match `switch` + `when`, record patterns   | base copies (Stage 6 pending) |

## Test runner

`mkjcgo/test-source-levels.sh` is the driver. It is currently a skeleton —
it documents what each per-version translate+gate test will do but does not
run anything until `classpath-0.93/` is set up locally and JCGO is rebuilt.

## Note on parser coverage

JCGO's grammar today (1.16 + this fork's `-source` plumbing) does not yet
parse most of the constructs above. As features are added to `jcgo.atg`
and the corresponding gates are wired, the matching version's directory
becomes a real positive test. Until then, every fixture from `java5/`
upward will fail at parse time — that's expected.
