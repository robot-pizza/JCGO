# Cut a JCGO release: build the zip, tag the source repo, attach the
# zip to a GitHub release, then bump the minor version and push the
# bump. Mirrors the flow of aisend's scripts/release.py.
#
# Reads the current version from VERSION at the repo root (single
# source of truth). Tag is "v<version>".
#
# Prereqs:
#   - gh CLI installed and authenticated against the github.com user
#     that has push to robot-pizza/JCGO
#   - Working tree clean, on master, in sync with origin
#   - VERSION at repo root containing major.minor.patch

$ErrorActionPreference = "Stop"

$REPO         = "robot-pizza/JCGO"
$VERSION_FILE = "VERSION"
$ZIP_NAME     = "jcgo-binaries-windows"

function Fail([string]$msg) {
    Write-Error "release: $msg"
    exit 1
}

function ExitOnFail([string]$what) {
    if ($LASTEXITCODE -ne 0) { Fail "$what failed (exit $LASTEXITCODE)" }
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Fail "gh CLI not on PATH -- install from https://cli.github.com/"
}

# Refuse to release with a dirty tree. The bump commit at the end
# would otherwise sweep up unrelated changes.
$status = (& git status --porcelain) | Out-String
if ($status.Trim()) {
    Fail "working tree dirty; commit or stash first:`n$status"
}

$branch = (& git rev-parse --abbrev-ref HEAD).Trim()
ExitOnFail "git rev-parse"
if ($branch -ne "master") { Fail "not on master (on $branch)" }

if (-not (Test-Path $VERSION_FILE)) {
    Fail "$VERSION_FILE not found at repo root"
}
$version = (Get-Content $VERSION_FILE -Raw).Trim()
if ($version -notmatch '^\d+\.\d+\.\d+$') {
    Fail "VERSION not in major.minor.patch form: '$version'"
}
$tag = "v$version"

# Tag must not exist yet -- guards against accidental re-publish at
# the same version.
& git rev-parse --verify --quiet $tag *> $null
if ($LASTEXITCODE -eq 0) {
    Fail "tag $tag already exists locally; bump VERSION and try again"
}

Write-Host "release: cutting $tag"

# Build the zip. The Makefile's zip target depends on dependencies +
# jcgo-jar + all, so this also drives the full build. Idempotent.
& make zip
ExitOnFail "make zip"
$zipPath = Join-Path "dist" "$ZIP_NAME.zip"
if (-not (Test-Path $zipPath)) {
    Fail "make zip did not produce $zipPath"
}

# Build release notes from commits since the previous tag. First
# release in the project will have no previous tag -- fall back to
# a placeholder.
$prevTag = & git describe --tags --abbrev=0 --match "v*" 2>$null
if ($LASTEXITCODE -eq 0 -and $prevTag) {
    $prevTag = $prevTag.Trim()
    $log = (& git log --pretty="format:- %s" "$prevTag..HEAD") | Out-String
    if ($log.Trim()) {
        $notes = $log.Trim()
    } else {
        $notes = "(no commits between $prevTag and HEAD)"
    }
} else {
    $notes = "Initial release of the JCGO modernization fork."
}

# Tag the source repo at the released commit and push the tag. Use
# an annotated tag so the release has authorship/date metadata.
& git tag -a $tag -m "JCGO $tag"
ExitOnFail "git tag"
& git push origin $tag
ExitOnFail "git push tag"

# Create the GitHub release with the zip attached.
& gh release create $tag $zipPath `
    --repo $REPO `
    --title "JCGO $tag" `
    --notes $notes
ExitOnFail "gh release create"

# Bump minor for the next dev cycle and push the bump commit.
# (Same scheme aisend uses: each release bumps minor, zeros patch.)
$parts = $version.Split('.')
$parts[1] = ([int]$parts[1] + 1).ToString()
$parts[2] = "0"
$nextVersion = $parts -join '.'
[IO.File]::WriteAllText(
    (Resolve-Path $VERSION_FILE).Path,
    "$nextVersion`n",
    [Text.Encoding]::ASCII)
& git add $VERSION_FILE
ExitOnFail "git add VERSION"
& git commit -m "Bump version to $nextVersion"
ExitOnFail "git commit"
& git push origin master
ExitOnFail "git push master"

Write-Host "release: published $tag -> $REPO; local version bumped to $nextVersion"
