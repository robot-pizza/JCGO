#!/bin/sh
# End-to-end runtime verification: translate, compile, and run
# examples/java8/E2EVerify.java; compare its stdout to the expected
# golden output. This is the only test in the tree that actually
# executes JCGO-translated code -- the source-levels smoke harness
# stops at translation.
#
# Prereqs (verified at startup):
#   * jcgo.jar built (mkjcgo/build-java.sh)
#   * rflg_out/ generated (auxbin/jre/GenRefl.jar -d rflg_out reflgen/*.dat)
#   * classpath-0.93/ unpacked at repo root
#   * x86_64-w64-mingw32-gcc on PATH (mingw-w64)
#   * libs/amd64/mingw64/libgc.a present (mkjcgo/build-win64-mingw32.sh
#     builds this from contrib/bdwgc; or copy a prebuilt one in)

set -e

cd $(dirname "$0")/..

if [ ! -f jcgo.jar ]; then
    echo "skip: jcgo.jar not built -- run mkjcgo/build-java.sh first"
    exit 0
fi
if [ ! -d classpath-0.93 ]; then
    echo "skip: classpath-0.93/ not present"
    exit 0
fi
if [ ! -d rflg_out ]; then
    echo "skip: rflg_out/ not generated"
    exit 0
fi
if ! command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1; then
    echo "skip: x86_64-w64-mingw32-gcc not on PATH"
    exit 0
fi

LIBGC=libs/amd64/mingw64/libgc.a
if [ ! -f "$LIBGC" ]; then
    echo "skip: $LIBGC not present (build BDWGC via build-win64-mingw32.sh"
    echo "      or copy a prebuilt one from a v1.16 install)"
    exit 0
fi

OUT=.build_tmp/e2e
rm -rf "$OUT"
mkdir -p "$OUT"

SRCS="-src goclsp/clsp_asc -src goclsp/clsp_fix -src goclsp/clsp_ldr"
SRCS="$SRCS -src goclsp/clsp_res -src goclsp/vm -src goclsp/noopmain"
SRCS="$SRCS -src goclsp/vm_str -src miscsrc/jpropjav -src rflg_out"
SRCS="$SRCS -src classpath-0.93"
SRCS="$SRCS -src classpath-0.93/external/relaxngDatatype"
SRCS="$SRCS -src classpath-0.93/external/sax"
SRCS="$SRCS -src classpath-0.93/external/w3c_dom"

echo "Translating E2EVerify..."
java -Xss1M -jar jcgo.jar -source 17 -d "$OUT" -src examples/java17 $SRCS \
    E2EVerify >"$OUT/translate.log" 2>&1

echo "Compiling..."
x86_64-w64-mingw32-gcc -m64 \
    -I include -I include/boehmgc -I native \
    -O2 -fwrapv -fno-strict-aliasing \
    -DJCGO_FFDATA -DJCGO_LARGEFILE -DJCGO_WIN32 \
    -DJCGO_USEGCJ -DGCSTATICDATA= \
    -DGC_INITIAL_HEAP_SIZE=4*1024*1024 \
    -Wl,--export-all-symbols \
    -o "$OUT/verify.exe" "$OUT/Main.c" "$LIBGC" -lws2_32

echo "Running..."
"$OUT/verify.exe" >"$OUT/actual.txt" 2>&1
actual_exit=$?

EXPECTED='bridge.produce returned: produced-string
bridge.return is String? true
pickFirst.getGenericReturnType class = gnu.java.lang.reflect.TypeVariableImpl
pickFirst.getGenericReturnType name = T
pickFirst.getGenericParameterTypes.length = 2
  param[0] = gnu.java.lang.reflect.TypeVariableImpl(name=T)
  param[1] = gnu.java.lang.reflect.TypeVariableImpl(name=T)
Holder.pass.params[0] = TV(U)
Holder.value.genericType = TV(U)
smaller(1,2) = 1
smaller(7,3) = 3
tripleBound = tagged:42
fieldAccessCrossBound = tagged:99
smaller.typeParams.length = 1
smaller.X.bounds.length = 2
  bound[0] = class java.lang.Number
  bound[1] = interface java.lang.Comparable
via paren-wrapped method-ref
eq42(42) = true
eq42(99) = false
is42(42) = true
is42(99) = false
rcv-evals after two SAM calls = 1
is42mc(42) = true
is42mc(99) = false
mc rcv-evals after two SAM calls = 1
Shift_JIS encoded length = 5
Shift_JIS byte[2] = 0x82
Shift_JIS byte[3] = 0xa0
Shift_JIS round-trip codepoint[2] = 0x3042
Shift_JIS round-trip == original = true
rmx.getName != null = true
rmx.getUptime >= 0 = true
tm.getThreadCount > 0 = true
tm.getThreadInfo non-null = true
tm.getThreadInfo.id matches = true
tm.getThreadInfo.name non-null = true
tm.getThreadInfo.stack non-empty = true
Throwable.getStackTrace non-empty = true
Throwable.getStackTrace contains main = true
Throwable.getStackTrace[0].class = E2EVerify
Throwable.getStackTrace[0].method = main
mm.heap.used > 0 = true
mm.heap.max > 0 = true
WithConsts.LIMIT = 42
WithConsts.LABEL = anno-const
WithConsts.LIMIT (via reflection) = 42
WithConsts.KIND = java.lang.Integer
WithConsts.LIMITS.length = 3
WithConsts.LIMITS[1] = 2
oldMethod isAnnotationPresent(Deprecated) = true
oldMethod getDeclaredAnnotations.length = 1
oldMethod ann[0].annotationType = java.lang.Deprecated
taggedMethod isAnnotationPresent(MyTag) = true
taggedMethod getDeclaredAnnotations.length = 1
taggedMethod ann[0].annotationType = MyTag
partialDefaults @WithDefaults.value = ok
partialDefaults @WithDefaults.level = 99
allDefaults @WithDefaults.value = ok
allDefaults @WithDefaults.level = 5
WithDefaults.value default = ok
WithDefaults.level default = 5
allDefaults @WithDefaults.kind = java.lang.Integer
allDefaults @WithDefaults.severity = MEDIUM
allDefaults @WithDefaults.tags.length = 3
allDefaults @WithDefaults.tags[1] = b
allDefaults @WithDefaults.inner.text = inner-default
MyTag.getDeclaredAnnotations.length = 2
  myTagMeta[0] = java.lang.annotation.Retention
  myTagMeta[1] = java.lang.annotation.Target
MyTag.isAnnotation = true
WithDefaults.isAnnotation = true
E2EVerify.isAnnotation = false
Circle.describe = circle
Square.describe = square
Circle isInstance Shape = true
Child.isAnnotationPresent(Family) = true
Child.isAnnotationPresent(NotInherited) = false
Parent.isAnnotationPresent(Family) = true
Child3.isAnnotationPresent(Family) = true
HasFamilyIface.isAnnotationPresent(Family) = false
multiTagged getAnnotationsByType(Tag).length = 2
  tag[0] = alpha
  tag[1] = beta
multiScored getAnnotationsByType(Score).length = 2
  score[0] = x:3
  score[1] = y:4
receivesAnnotated paramCount = 2
  param[0].length = 1
    [0][0] = MyTag
  param[1].length = 1
    [1][0] = WithDefaults
      value=ok level=7
ParamAnnoCtor ctorCount = 1
ParamAnnoCtor paramCount = 2
  ctorParam[0].length = 1
    ctor[0][0] = MyTag
  ctorParam[1].length = 1
    ctor[1][0] = WithDefaults
      ctor value=ok level=7'

# Strip CRs because Win32 stdio writes \r\n and shell here-doc is \n.
# The "--- full trace ---" / "--- end trace ---" block is optimization-
# sensitive (-O2 inlines helpers, -O0 -fno-inline preserves frames),
# so excise it before the diff and assert separately that the trace
# was captured with >= 1 frame.
actual_full=$(tr -d '\r' < "$OUT/actual.txt")
actual=$(printf '%s\n' "$actual_full" | sed '/^--- full trace ---$/,/^--- end trace ---$/d')
trace_frames=$(printf '%s\n' "$actual_full" | sed -n '/^--- full trace ---$/,/^--- end trace ---$/{//!p}' | grep -c '^  at ' || true)

if [ "$actual_exit" -ne 0 ]; then
    echo "FAIL: verify.exe exited $actual_exit"
    echo "--- actual output:"
    echo "$actual"
    exit 1
fi

if [ "$actual" != "$EXPECTED" ]; then
    echo "FAIL: output mismatch"
    echo "--- expected:"
    echo "$EXPECTED"
    echo "--- actual:"
    echo "$actual"
    exit 1
fi

if [ "$trace_frames" -lt 1 ]; then
    echo "FAIL: stack trace was empty (expected >= 1 frame)"
    exit 1
fi

# Verify -line-info default emits #line pragmas into generated C and
# -no-line-info suppresses them. Asserts both that pragmas exist and
# that they reference E2EVerify.java specifically -- otherwise a
# regression in the pragma-emit path (e.g. mis-derived source filename)
# could go unnoticed.
line_count=$(grep -c '^#line' "$OUT/E2EVrf.c")
if [ "$line_count" -lt 10 ]; then
    echo "FAIL: expected #line pragmas in $OUT/E2EVrf.c, got $line_count"
    exit 1
fi
if ! grep -q '^#line .* "E2EVerify.java"' "$OUT/E2EVrf.c"; then
    echo "FAIL: #line pragmas exist but none reference E2EVerify.java"
    exit 1
fi

# MSVC build helper. Stages a .bat that calls vcvars + cl on the
# E2EVerify Main.c in $1, links the JCGO crash dumper + a real
# BDWGC if one's been built (libs/amd64/msvc/gc.lib) or the no-op
# stub otherwise, and produces verify_msvc.{exe,pdb} in $1. Sets
# MSVC_RUN_OK=1 if the binary should actually run; 0 if linked
# against the stub (PDB inspection only).
INCW_MSVC=$(cygpath -w include)
INCBOEHMW_MSVC=$(cygpath -w include/boehmgc)
INCNATIVEW_MSVC=$(cygpath -w native)
NATIVEW_MSVC=$(cygpath -w native)
if [ -f libs/amd64/msvc/gc.lib ]; then
    LIB_INPUT_MSVC="\"$(cygpath -w libs/amd64/msvc/gc.lib)\""
    MSVC_RUN_OK=1
else
    LIB_INPUT_MSVC="\"$NATIVEW_MSVC\\jcgogcstub.c\""
    MSVC_RUN_OK=0
fi
build_msvc_at() {
    local dir="$1"
    local outw=$(cygpath -w "$dir")
    cat >"$dir/run_msvc.bat" <<EOF
@call "C:\\Program Files\\Microsoft Visual Studio\\2022\\Community\\VC\\Auxiliary\\Build\\vcvars64.bat" >nul
@if errorlevel 1 exit /b %errorlevel%
cl.exe /nologo /Zi /Od /MT ^
  /D_CRT_SECURE_NO_WARNINGS /DJCGO_FFDATA /DJCGO_LARGEFILE /DJCGO_WIN32 ^
  /I"$INCW_MSVC" /I"$INCBOEHMW_MSVC" /I"$INCNATIVEW_MSVC" ^
  /Fo"$outw\\\\" /Fd"$outw\\Main_msvc.pdb" ^
  /Fe"$outw\\verify_msvc.exe" ^
  "$outw\\Main.c" ^
  "$NATIVEW_MSVC\\jcgocrash.c" ^
  $LIB_INPUT_MSVC ^
  /link /nologo /DEBUG /PDB:"$outw\\verify_msvc.pdb" ^
    legacy_stdio_definitions.lib user32.lib ws2_32.lib dbghelp.lib ^
    advapi32.lib ole32.lib
exit /b %errorlevel%
EOF
    local batw=$(cygpath -w "$dir/run_msvc.bat")
    MSYS_NO_PATHCONV=1 cmd.exe /c call "$batw" \
        >"$dir/msvc.log" 2>&1
}

# Default-on (with #line pragmas): the C output must have pragmas
# referencing E2EVerify.java, the PDB must carry that filename, and
# (when a real BDWGC is linked) the running binary's stack trace
# must show .java:<line> references.
line_count=$(grep -c '^#line' "$OUT/E2EVrf.c")
if [ "$line_count" -lt 10 ]; then
    echo "FAIL: expected #line pragmas in $OUT/E2EVrf.c, got $line_count"
    exit 1
fi
if ! grep -q '^#line .* "E2EVerify.java"' "$OUT/E2EVrf.c"; then
    echo "FAIL: #line pragmas exist but none reference E2EVerify.java"
    exit 1
fi

# Off-mode (`-no-line-info`): retranslate into a sibling dir, assert
# zero pragmas, build with MSVC the same way, run, and assert the
# trace's file:line references point at JCGO's emitted .c files.
NOLINE=.build_tmp/e2e_noline
rm -rf "$NOLINE"
mkdir -p "$NOLINE"
java -Xss1M -jar jcgo.jar -source 17 -no-line-info \
    -d "$NOLINE" -src examples/java17 $SRCS \
    E2EVerify >"$NOLINE/translate.log" 2>&1
if [ ! -f "$NOLINE/E2EVrf.c" ]; then
    echo "FAIL: -no-line-info translate produced no E2EVrf.c"
    exit 1
fi
# `grep -c` exits 1 when count is 0; `set -e` would trip on that.
nl_count=$(grep -c '^#line' "$NOLINE/E2EVrf.c" || true)
if [ "$nl_count" -ne 0 ]; then
    echo "FAIL: -no-line-info should suppress #line pragmas, got $nl_count"
    exit 1
fi

VCVARS_PATH="/c/Program Files/Microsoft Visual Studio/2022/Community/VC/Auxiliary/Build/vcvars64.bat"
if [ -f "$VCVARS_PATH" ]; then
    # Default-on PDB inspection: build, confirm PDB carries the .java
    # source filename (round-trip via cl /Zi + linker).
    rm -f "$OUT/Main_msvc.obj" "$OUT/Main_msvc.pdb"
    if ! build_msvc_at "$OUT"; then
        echo "FAIL: MSVC compile (default-on) errored"
        tail -20 "$OUT/msvc.log"
        exit 1
    fi
    if [ ! -f "$OUT/verify_msvc.pdb" ]; then
        echo "FAIL: MSVC linker produced no PDB at $OUT/verify_msvc.pdb"
        exit 1
    fi
    pdb_hits=$(strings "$OUT/verify_msvc.pdb" \
        | grep -c "E2EVerify\\.java" || true)
    if [ "$pdb_hits" -lt 1 ]; then
        echo "FAIL: MSVC PDB contains no E2EVerify.java references"
        echo "       (the #line pragma chain didn't survive the host C compiler)"
        exit 1
    fi

    # Off-mode build.
    if ! build_msvc_at "$NOLINE"; then
        echo "FAIL: MSVC compile (-no-line-info) errored"
        tail -20 "$NOLINE/msvc.log"
        exit 1
    fi

    if [ "$MSVC_RUN_OK" = "1" ]; then
        # Default-on runtime trace: at least one frame must render
        # as `.java:<line>`. Proves the live SymGetLineFromAddr64
        # round-trip resolves Java source positions.
        on_trace=$("$OUT/verify_msvc.exe" 2>&1 \
            | tr -d '\r' \
            | sed -n '/^--- full trace ---$/,/^--- end trace ---$/p')
        if ! printf '%s\n' "$on_trace" | grep -qE '\.java:[0-9]+\)'; then
            echo "FAIL: default-on trace has no .java:N references"
            printf '%s\n' "$on_trace"
            exit 1
        fi

        # Off-mode runtime trace: same machinery, no #line pragmas;
        # DbgHelp surfaces the JCGO-emitted C filename (E2EVrf.c,
        # Main.c, etc.) instead. Asserts at least one .c:N frame
        # AND zero .java:N frames.
        off_trace=$("$NOLINE/verify_msvc.exe" 2>&1 \
            | tr -d '\r' \
            | sed -n '/^--- full trace ---$/,/^--- end trace ---$/p')
        if ! printf '%s\n' "$off_trace" | grep -qE '\.c:[0-9]+\)'; then
            echo "FAIL: -no-line-info trace has no .c:N references"
            printf '%s\n' "$off_trace"
            exit 1
        fi
        if printf '%s\n' "$off_trace" | grep -qE '\.java:[0-9]+\)'; then
            echo "FAIL: -no-line-info trace leaked .java:N references"
            printf '%s\n' "$off_trace"
            exit 1
        fi
    fi
fi

echo "pass: E2EVerify (bridges, generic signatures, builtin + custom annotation Proxy, member defaults, isAnnotation, parameter annotations, meta-annotations, repeating, @Inherited, type-var params/fields, sealed runtime, @interface consts, multi-bound type-vars, paren method-ref, cross-bound dispatch, complex-receiver method-ref, iconv multi-byte, management baseline)"
exit 0
