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
smaller.typeParams.length = 1
smaller.X.bounds.length = 2
  bound[0] = class java.lang.Number
  bound[1] = interface java.lang.Comparable
via paren-wrapped method-ref
eq42(42) = true
eq42(99) = false
Shift_JIS encoded length = 5
Shift_JIS byte[2] = 0x82
Shift_JIS byte[3] = 0xa0
Shift_JIS round-trip codepoint[2] = 0x3042
Shift_JIS round-trip == original = true
WithConsts.LIMIT = 42
WithConsts.LABEL = anno-const
WithConsts.LIMIT (via reflection) = 42
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
multiTagged getAnnotationsByType(Tag).length = 2
  tag[0] = alpha
  tag[1] = beta
receivesAnnotated paramCount = 2
  param[0].length = 1
    [0][0] = MyTag
  param[1].length = 1
    [1][0] = WithDefaults
      value=ok level=7'

# Strip CRs because Win32 stdio writes \r\n and shell here-doc is \n.
actual=$(tr -d '\r' < "$OUT/actual.txt")

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

echo "pass: E2EVerify (bridges, generic signatures, builtin + custom annotation Proxy, member defaults, isAnnotation, parameter annotations, meta-annotations, repeating, @Inherited, type-var params/fields, sealed runtime, @interface consts, multi-bound type-vars, paren method-ref, cross-bound dispatch, complex-receiver method-ref, iconv multi-byte)"
exit 0
