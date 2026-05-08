# JCGO Fork — Project Instructions

## What this is
This is a fork of [ivmai/JCGO](https://github.com/ivmai/JCGO) (the
GPL+Classpath-exception Java→C translator by Ivan Maidanski), held at
[robot-pizza/JCGO](https://github.com/robot-pizza/JCGO).

Upstream is `master` at v1.16 (2014-04-29). The fork was taken on
2026-05-03 to lift JCGO's source-language ceiling so user code can be
written in modern Java.

## Why we forked
Stock JCGO accepts JLS 1+2 only — no generics, no enums, no
annotations, no autoboxing, no foreach, no lambdas, no records, no
sealed types, etc. The fork reworks the parser, type resolver, and
runtime support so JCGO can accept and translate sources up through
JLS 21 (with `-source N` selecting the level).

Last verified state: smoke harness positive 88/88, negative 33/33,
skipped 2; runtime E2E (`mkjcgo/test-e2e.sh`) covers bridge methods,
generic-signature retention, and built-in + custom annotation
reflection. See `TODO.md` for the remaining gaps.

## Layout
- `jtrsrc/` — the translator itself (Java source). This is where the
  bulk of modernization work lives.
- `jcgo.atg` — the AntLR grammar. Touched by any syntax-level change
  (generics tokens, enum keyword, `@` annotations, foreach, etc.).
- `goclsp/` — the JCGO-specific overrides on top of GNU Classpath.
- `native/`, `include/`, `minihdr/` — runtime / native glue (C side).
- `reflgen/`, `sunawt/` — auxiliary generators / AWT impl.
- `examples/`, `contrib/` — example apps and contributed pieces.
- `mkjcgo/` — build scripts that produce `jcgo.exe` / `jcgo.jar`,
  plus `test-source-levels.sh` (smoke) and `test-e2e.sh` (runtime).

GNU Classpath 0.93 is **not** vendored here — it's downloaded and
unpacked into `classpath-0.93/` at build time (and is in
`.gitignore`).

## Build artifacts (all gitignored)
`jcgo.exe`, `jcgo.jar`, `bin/`, `auxbin/`, `dlls/`, `libs/`,
`rflg_out/`, `sawt_out/`, and `classpath-0.93/` are all build
output — see `.gitignore`.

## Working with this fork
- `origin` → `robot-pizza/JCGO` (the fork).
- `upstream` → `ivmai/JCGO` (read-only; pull from here to track
  upstream releases). Last known upstream is v1.16 from 2014; the
  project has been dormant since.

## License
GPL v2 with the Classpath exception (see `COPYING` and `LICENSE`).
Any changes here inherit the same terms. Don't strip headers.

## Notes for Claude
- Don't commit upstream-license-bearing files with edits unless the
  edit is genuinely needed; surgical changes are easier to keep in
  sync with `ivmai/JCGO`.
- If you touch `jcgo.atg` or anything generated, regenerate the
  parser before claiming a change works.
