# Build JCGO's .jar artifacts in pure PowerShell — no bash required.
#
# Mirrors the .jar-producing portion of mkjcgo/build-java.sh (jcgo.jar
# plus auxbin/jre/{GenRefl,JPropJav,TraceJni}.jar). The longer .sh
# script also generates rflg_out/ and translated C output under
# .build_tmp/; those steps depend on classpath-0.93/ and contrib/swt/
# being unpacked, and aren't required for the release zip, so they're
# omitted here. Use mkjcgo/build-java.sh (under bash) for the full
# chain.
#
# Run from the JCGO repo root. Requires javac and jar on PATH.

$ErrorActionPreference = "Stop"

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    throw "javac not found on PATH. Install a JDK and ensure javac/jar are reachable."
}
if (-not (Get-Command jar -ErrorAction SilentlyContinue)) {
    throw "jar not found on PATH. Install a JDK and ensure javac/jar are reachable."
}

function Build-Jar {
    param(
        [Parameter(Mandatory)] [string]$JarPath,
        [Parameter(Mandatory)] [string]$ManifestPath,
        [Parameter(Mandatory)] [string]$ClassDir,
        [Parameter(Mandatory)] [string]$MainClass,
        [Parameter(Mandatory)] [string[]]$Sources
    )
    Write-Host "==> $JarPath"

    New-Item -ItemType Directory -Force -Path $ClassDir | Out-Null
    New-Item -ItemType Directory -Force -Path (Split-Path $ManifestPath -Parent) | Out-Null
    $jarParent = Split-Path $JarPath -Parent
    if ($jarParent) { New-Item -ItemType Directory -Force -Path $jarParent | Out-Null }

    # Manifest: ASCII, single line, LF terminator (matches build-java.sh's
    # `echo > MANIFEST.MF`). [IO.File]::WriteAllText avoids PowerShell's
    # default UTF-16/BOM and CRLF tendencies.
    [IO.File]::WriteAllText(
        (Resolve-Path -LiteralPath (Split-Path $ManifestPath -Parent)).Path + `
            "\" + (Split-Path $ManifestPath -Leaf),
        "Main-Class: $MainClass`n",
        [Text.Encoding]::ASCII)

    & javac -d $ClassDir $Sources
    if ($LASTEXITCODE -ne 0) { throw "javac failed for $JarPath" }

    & jar cfm $JarPath $ManifestPath -C $ClassDir com
    if ($LASTEXITCODE -ne 0) { throw "jar failed for $JarPath" }
}

function Get-JavaSources([string]$Dir) {
    Get-ChildItem -Path $Dir -Filter "*.java" -File | ForEach-Object { $_.FullName }
}

# jcgo.jar
Build-Jar `
    -JarPath      "jcgo.jar" `
    -ManifestPath ".build_tmp/jtr/MANIFEST.MF" `
    -ClassDir     ".build_tmp/jtr/bin" `
    -MainClass    "com.ivmaisoft.jcgo.Main" `
    -Sources      (Get-JavaSources "jtrsrc/com/ivmaisoft/jcgo")

# auxbin/jre/GenRefl.jar
Build-Jar `
    -JarPath      "auxbin/jre/GenRefl.jar" `
    -ManifestPath ".build_tmp/genrefl/MANIFEST.MF" `
    -ClassDir     ".build_tmp/genrefl/bin" `
    -MainClass    "com.ivmaisoft.jcgorefl.GenRefl" `
    -Sources      @("reflgen/com/ivmaisoft/jcgorefl/GenRefl.java")

# auxbin/jre/JPropJav.jar
Build-Jar `
    -JarPath      "auxbin/jre/JPropJav.jar" `
    -ManifestPath ".build_tmp/jpropjav/MANIFEST.MF" `
    -ClassDir     ".build_tmp/jpropjav/bin" `
    -MainClass    "com.ivmaisoft.jpropjav.Main" `
    -Sources      (Get-JavaSources "miscsrc/jpropjav/com/ivmaisoft/jpropjav")

# auxbin/jre/TraceJni.jar
Build-Jar `
    -JarPath      "auxbin/jre/TraceJni.jar" `
    -ManifestPath ".build_tmp/tracejni/MANIFEST.MF" `
    -ClassDir     ".build_tmp/tracejni/bin" `
    -MainClass    "com.ivmaisoft.jcgorefl.TraceJni" `
    -Sources      @("reflgen/com/ivmaisoft/jcgorefl/TraceJni.java")

Write-Host ""
Write-Host "Built: jcgo.jar, auxbin/jre/{GenRefl,JPropJav,TraceJni}.jar"
