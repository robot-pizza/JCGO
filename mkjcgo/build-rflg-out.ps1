# Generate rflg_out/ -- the directory of generated Java source files
# JCGO's translator consumes alongside user app sources.
#
# Two tools populate rflg_out:
#
#   GenRefl   reads reflgen/*.dat (binary descriptors) and emits Java
#             source for runtime reflection support.
#
#   JPropJav  reads .properties files (locales, JPEG messages,
#             security callbacks, etc.) referenced in
#             goclsp/clsp_res/jreslist.in and converts them to Java
#             source so they compile into the C output.
#
# Mirrors lines 48-65 of mkjcgo/build-java.sh, minus the SWT path:
# we filter SWT-tagged entries out of jreslist.in at build time
# because (a) most JCGO consumers don't translate SWT GUIs, (b) the
# SWT source tarball isn't publicly hosted at a stable URL, and (c)
# the jcgo-binaries-windows.zip is a headless-runtime convenience
# drop, not a full GUI distribution. SWT-using consumers regenerate
# rflg_out locally with full sources via mkjcgo/build-java.sh.
#
# Run from the JCGO repo root. Requires:
#   - jcgo.jar + auxbin/jre/{GenRefl,JPropJav}.jar (make jcgo-jar)
#   - classpath-0.93/ unpacked at repo root        (make dependencies)

$ErrorActionPreference = "Stop"

$RflgOut    = "rflg_out"
$JreslistIn = "goclsp/clsp_res/jreslist.in"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "java not found on PATH"
}

$required = @(
    "auxbin/jre/GenRefl.jar",
    "auxbin/jre/JPropJav.jar",
    "classpath-0.93",
    $JreslistIn
)
foreach ($p in $required) {
    if (-not (Test-Path $p)) {
        throw "missing prerequisite: $p (run 'make dependencies' and 'make jcgo-jar' first)"
    }
}

# Wipe + recreate so a stale partial run doesn't poison the output.
if (Test-Path $RflgOut) { Remove-Item -Recurse -Force $RflgOut }
New-Item -ItemType Directory -Force -Path $RflgOut | Out-Null

# Force LF line endings in generated source. Mirrors build-java.sh's
# `-Dline.separator=$'\n'` so the bundled rflg_out has consistent
# endings regardless of build host.
$lf = [char]10

# --- GenRefl --------------------------------------------------------
Write-Host "==> GenRefl -d $RflgOut reflgen/*.dat"
$datFiles = @(Get-ChildItem -Path "reflgen" -Filter "*.dat" -File | ForEach-Object { $_.FullName })
if ($datFiles.Count -eq 0) {
    throw "reflgen/*.dat not found"
}
& java "-Dline.separator=$lf" -jar "auxbin/jre/GenRefl.jar" -d $RflgOut $datFiles
if ($LASTEXITCODE -ne 0) { throw "GenRefl failed (exit $LASTEXITCODE)" }

# --- JPropJav -------------------------------------------------------
# Strip SWT entries from a build-time copy of jreslist.in. With the
# entries gone we don't need the SWT source path at all.
$tmpJreslist = Join-Path $env:TEMP ("jcgo-jreslist-" + [Guid]::NewGuid().ToString("N") + ".in")
try {
    (Get-Content $JreslistIn) `
        | Where-Object { $_ -notmatch 'eclipse\.swt' } `
        | Set-Content -Encoding ASCII -Path $tmpJreslist
    Write-Host "==> JPropJav -d $RflgOut (SWT entries stripped from jreslist.in)"
    & java "-Dline.separator=$lf" -jar "auxbin/jre/JPropJav.jar" `
        -d $RflgOut `
        -sourcepath "goclsp/clsp_fix/resource" `
        -sourcepath "classpath-0.93/resource" `
        "@$tmpJreslist"
    if ($LASTEXITCODE -ne 0) { throw "JPropJav failed (exit $LASTEXITCODE)" }
} finally {
    if (Test-Path $tmpJreslist) { Remove-Item -Force $tmpJreslist }
}

$count = (Get-ChildItem -Recurse -File -Path $RflgOut | Measure-Object).Count
Write-Host ""
Write-Host "rflg_out/ generated ($count files)"
