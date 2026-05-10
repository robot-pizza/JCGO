# Fetch third-party tarballs the build chain expects.
# Idempotent -- skips download/extract if the destination already has
# the marker file we expect.
#
# What this fetches:
#   contrib/bdwgc/                  <- gc-8.2.8 from ivmai/bdwgc
#   contrib/bdwgc/libatomic_ops/    <- libatomic_ops-7.8.4
#   classpath-0.93/                 <- GNU Classpath sources, needed by
#                                      JPropJav (rflg_out generation)
#
# What this does NOT fetch:
#   contrib/tinygc/                 <- no public release URL; drop the
#                                      tinygc-2_6.tar.bz2 in contrib/
#                                      manually if you want the TinyGC
#                                      DLL built. The win32 .bat skips
#                                      that step gracefully if the
#                                      sources aren't present.

$ErrorActionPreference = "Stop"

$BDWGC_URL     = "https://github.com/ivmai/bdwgc/releases/download/v8.2.8/gc-8.2.8.tar.gz"
$LAO_URL       = "https://github.com/ivmai/libatomic_ops/releases/download/v7.8.4/libatomic_ops-7.8.4.tar.gz"
$CLASSPATH_URL = "https://ftp.gnu.org/gnu/classpath/classpath-0.93.tar.gz"

$ContribDir    = "contrib"
$BdwgcDir      = Join-Path $ContribDir "bdwgc"
$LaoDir        = Join-Path $BdwgcDir "libatomic_ops"
$ClasspathDir  = "classpath-0.93"

# Marker files: cheap "is this already extracted?" check.
$BdwgcMarker     = Join-Path $BdwgcDir     "include\gc.h"
$LaoMarker       = Join-Path $LaoDir       "src\atomic_ops.h"
$ClasspathMarker = Join-Path $ClasspathDir "configure"

function Fetch-And-Extract {
    param(
        [Parameter(Mandatory)] [string]$Url,
        [Parameter(Mandatory)] [string]$ExpectedDirAfterExtract,  # e.g. "gc-8.2.8"
        [Parameter(Mandatory)] [string]$FinalDir,                 # e.g. "contrib/bdwgc"
        [Parameter(Mandatory)] [string]$ExtractParent             # e.g. "contrib"
    )
    $tmp = Join-Path $env:TEMP ("jcgo-dep-" + [Guid]::NewGuid().ToString("N") + ".tar.gz")
    try {
        Write-Host "Downloading $Url"
        # ProgressPreference=SilentlyContinue speeds Invoke-WebRequest up
        # dramatically (~10x) on PS 5.1 by skipping the progress bar.
        $prev = $ProgressPreference
        $ProgressPreference = "SilentlyContinue"
        try {
            Invoke-WebRequest -Uri $Url -OutFile $tmp -UseBasicParsing
        } finally {
            $ProgressPreference = $prev
        }

        Write-Host "Extracting into $ExtractParent/"
        New-Item -ItemType Directory -Force -Path $ExtractParent | Out-Null
        # Windows 10+ ships tar.exe in System32; handles .tar.gz natively.
        & tar -xzf $tmp -C $ExtractParent
        if ($LASTEXITCODE -ne 0) { throw "tar extraction failed" }

        $extracted = Join-Path $ExtractParent $ExpectedDirAfterExtract
        if (-not (Test-Path $extracted)) {
            throw "expected $extracted after extraction, but it doesn't exist"
        }

        # When the tarball already extracts to the desired final dir
        # (e.g. classpath-0.93.tar.gz -> classpath-0.93/), the
        # Remove+Move sequence below would destroy the just-extracted
        # tree. Resolve canonical paths and skip if they match.
        $extractedFull = (Resolve-Path -LiteralPath $extracted).Path
        if (Test-Path $FinalDir) {
            $finalFull = (Resolve-Path -LiteralPath $FinalDir).Path
            if ($finalFull -eq $extractedFull) { return }
            Write-Host "Removing existing $FinalDir to make room for fresh extract"
            Remove-Item -Recurse -Force $FinalDir
        }
        Move-Item -Path $extracted -Destination $FinalDir
    } finally {
        if (Test-Path $tmp) { Remove-Item -Force $tmp }
    }
}

if (Test-Path $BdwgcMarker) {
    Write-Host "BDWGC already present at $BdwgcDir (marker: $BdwgcMarker) -- skipping"
} else {
    Fetch-And-Extract `
        -Url                     $BDWGC_URL `
        -ExpectedDirAfterExtract "gc-8.2.8" `
        -FinalDir                $BdwgcDir `
        -ExtractParent           $ContribDir
}

if (Test-Path $LaoMarker) {
    Write-Host "libatomic_ops already present at $LaoDir (marker: $LaoMarker) -- skipping"
} else {
    Fetch-And-Extract `
        -Url                     $LAO_URL `
        -ExpectedDirAfterExtract "libatomic_ops-7.8.4" `
        -FinalDir                $LaoDir `
        -ExtractParent           $BdwgcDir
}

# classpath-0.93 lands at the repo root, not in contrib/. The tarball
# already extracts to a "classpath-0.93/" dir, so ExtractParent="."
# and ExpectedDirAfterExtract == FinalDir. Fetch-And-Extract handles
# that fine -- the trailing Move-Item is a no-op rename when source
# and destination resolve to the same path, but we Remove-Item the
# existing dir first to keep the helper's "fresh extract" semantics
# uniform; the marker check above prevents that destructive path on
# subsequent runs.
if (Test-Path $ClasspathMarker) {
    Write-Host "classpath-0.93 already present at $ClasspathDir (marker: $ClasspathMarker) -- skipping"
} else {
    Fetch-And-Extract `
        -Url                     $CLASSPATH_URL `
        -ExpectedDirAfterExtract "classpath-0.93" `
        -FinalDir                $ClasspathDir `
        -ExtractParent           "."
}

Write-Host ""
Write-Host "Dependencies ready (contrib/bdwgc/, contrib/bdwgc/libatomic_ops/, classpath-0.93/)."
if (-not (Test-Path "contrib/tinygc/tinygc.c")) {
    Write-Host "Note: contrib/tinygc/ not present. Drop tinygc-2_6.tar.bz2 in"
    Write-Host "      contrib/ and unpack it if you want the TinyGC DLL built."
    Write-Host "      The build will skip TinyGC otherwise."
}
