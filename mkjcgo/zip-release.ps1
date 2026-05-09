# Build dist/jcgo-binaries-windows.zip — a self-contained release artifact.
#
# Run from the JCGO repo root, after the build artifacts already exist:
#   - jcgo.jar              (from `bash mkjcgo/build-java.sh`)
#   - libs/{x86,amd64}/msvc (from `make all`)
#   - dlls/{x86,amd64}/win32
#
# The script stages the contents into dist/jcgo-binaries-windows/ and then
# zips that folder, so unzipping the result on a consumer machine yields a
# top-level jcgo-binaries-windows/ directory rather than dumping files at
# the cwd. Existing dist/ contents are wiped first for idempotency.

param(
    [string]$Name = "jcgo-binaries-windows"
)

$ErrorActionPreference = "Stop"

$DistDir = "dist"
$Stage = Join-Path $DistDir $Name
$ZipPath = Join-Path $DistDir "$Name.zip"

# Required artifacts — fail fast with a clear message if the prerequisite
# build steps haven't been run yet.
$Required = @(
    "jcgo.jar",
    "auxbin/jre/GenRefl.jar",
    "auxbin/jre/JPropJav.jar",
    "libs/x86/msvc/gc.lib",
    "libs/amd64/msvc/gc.lib",
    "dlls/x86/win32/gc.dll",
    "dlls/amd64/win32/gc64.dll"
)
$Missing = $Required | Where-Object { -not (Test-Path $_) }
if ($Missing) {
    Write-Error ("Missing build artifacts:`n  " + ($Missing -join "`n  ") + `
        "`n`nRun 'make jcgo-jar' for jcgo.jar/auxbin," + `
        "`nand 'make all' for the libs/ + dlls/ contents." + `
        "`n(Or 'make release' to do all of the above + zip in one shot.)")
    exit 1
}

# Idempotent stage
if (Test-Path $Stage) { Remove-Item -Recurse -Force $Stage }
if (Test-Path $ZipPath) { Remove-Item -Force $ZipPath }
New-Item -ItemType Directory -Force -Path $Stage | Out-Null

# What goes in: built artifacts + consumer-facing source dirs (headers,
# native runtime sources, classpath overrides) + license/readme.
$Items = @(
    "jcgo.jar",
    "auxbin",
    "goclsp",
    "include",
    "native",
    "libs",
    "dlls",
    "COPYING",
    "LICENSE",
    "README",
    "README_QUICK.txt"
)
foreach ($item in $Items) {
    if (Test-Path $item) {
        Copy-Item -Recurse -Force -Path $item -Destination $Stage
    } else {
        Write-Warning "Skipping $item (not found)"
    }
}

Compress-Archive -Path $Stage -DestinationPath $ZipPath -Force

$size = (Get-Item $ZipPath).Length
$mb = [math]::Round($size / 1MB, 2)
Write-Host ("Created {0} ({1} MB)" -f $ZipPath, $mb)
