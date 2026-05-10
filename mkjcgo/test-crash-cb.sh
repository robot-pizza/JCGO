#!/bin/sh
# Smoke test for jcgo_crash_set_callback.
#
# Builds mkjcgo/test_crash_cb.c + native/jcgocrash.c under MSVC, runs
# the resulting exe (which deliberately null-derefs after registering
# a callback), and asserts the callback wrote a sentinel file with
# the right shape -- proving:
#   * the callback fires,
#   * `info->code_name` / `code` reflect the actual exception,
#   * the linked frame list is populated,
#   * the user `vpcb` round-trips,
#   * `info->stk_path` is set and the .stk file actually got written.
#
# Skips silently when MSVC tooling isn't present.

set -e
cd $(dirname "$0")/..

VCVARS_PATH="/c/Program Files/Microsoft Visual Studio/2022/Community/VC/Auxiliary/Build/vcvars64.bat"
if [ ! -f "$VCVARS_PATH" ]; then
    echo "skip: $VCVARS_PATH not present (need MSVC 2022 Community)"
    exit 0
fi

OUT=.build_tmp/test-crash-cb
rm -rf "$OUT"
mkdir -p "$OUT"

INCW=$(cygpath -w include)
INCNATIVEW=$(cygpath -w native)
SRCW=$(cygpath -w mkjcgo/test_crash_cb.c)
CRASHW=$(cygpath -w native/jcgocrash.c)
OUTW=$(cygpath -w "$OUT")
VCVARSW=$(cygpath -w "$VCVARS_PATH")

cat >"$OUT/run_msvc.bat" <<EOF
@call "$VCVARSW" >nul
@if errorlevel 1 exit /b %errorlevel%
cl.exe /nologo /Zi /Od /MT ^
  /D_CRT_SECURE_NO_WARNINGS ^
  /I"$INCW" /I"$INCNATIVEW" ^
  /Fo"$OUTW\\\\" /Fd"$OUTW\\test_crash_cb.pdb" ^
  /Fe"$OUTW\\test_crash_cb.exe" ^
  "$SRCW" "$CRASHW" ^
  /link /nologo /DEBUG /PDB:"$OUTW\\test_crash_cb_linked.pdb" ^
    legacy_stdio_definitions.lib dbghelp.lib advapi32.lib
exit /b %errorlevel%
EOF

BATW=$(cygpath -w "$OUT/run_msvc.bat")
echo "Building test_crash_cb.exe..."
MSYS_NO_PATHCONV=1 cmd.exe /c call "$BATW" >"$OUT/build.log" 2>&1 || {
    echo "FAIL: build of test_crash_cb failed; see $OUT/build.log"
    tail -20 "$OUT/build.log" || true
    exit 1
}

# Run from $OUT so the sentinel + .stk land beside the exe (the
# crash dumper writes <exe>.stk relative to the exe basename in the
# current working directory).
echo "Running test_crash_cb.exe (expected to crash)..."
(
    cd "$OUT"
    rm -f test_crash_cb_sentinel.txt test_crash_cb.stk
    ./test_crash_cb.exe >stdout.txt 2>stderr.txt
) && {
    echo "FAIL: test_crash_cb.exe exited 0 -- expected non-zero (crash)"
    exit 1
} || true   # any non-zero exit is the success case here

SENTINEL="$OUT/test_crash_cb_sentinel.txt"
STK="$OUT/test_crash_cb.stk"

if [ ! -f "$SENTINEL" ]; then
    echo "FAIL: callback did not write $SENTINEL"
    echo "----- stderr -----"
    cat "$OUT/stderr.txt" 2>/dev/null || true
    exit 1
fi
if [ ! -f "$STK" ]; then
    echo "FAIL: built-in handler did not write $STK"
    exit 1
fi

fail=0
expect_grep() {
    local pat="$1"
    if ! grep -q -E "$pat" "$SENTINEL"; then
        echo "FAIL: sentinel missing pattern: $pat"
        fail=1
    fi
}
# Null deref on Windows = STATUS_ACCESS_VIOLATION = 0xC0000005.
expect_grep '^code=0xC0000005$'
expect_grep '^code_name=ACCESS_VIOLATION$'
# At least one frame collected.
expect_grep '^frame_count=[1-9][0-9]*$'
# vpcb round-tripped and counter got incremented exactly once.
expect_grep '^vpcb_counter=1$'
# stk_path matches the file we just verified exists.
expect_grep '^stk_path=test_crash_cb\.stk$'
# Faulting frame is in main (the crash site).
expect_grep '^first_frame_symbol=main$'

if [ "$fail" -ne 0 ]; then
    echo "----- $SENTINEL -----"
    cat "$SENTINEL"
    exit 1
fi

echo "PASS: jcgo_crash_set_callback fired with expected info"
