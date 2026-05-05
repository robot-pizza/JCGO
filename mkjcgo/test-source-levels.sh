#!/bin/sh
# Source-level smoke test driver for examples/javaN/ fixtures.
#
# For each fixture:
#   * Positive translate at -source N  -> expect success
#   * Negative translate at -source N-1 -> expect "requires -source ..." error
#
# Skeleton only. Does NOT run yet — flips to "todo" for every fixture
# until both prerequisites are met:
#   1. classpath-0.93/ is unpacked locally (see CLAUDE.md for the URL).
#   2. jcgo.jar is rebuilt against this branch.
#
# Wiring will land as task #13 once those prereqs are in place.

set -e

# Set current working directory to JCGO root:
cd $(dirname "$0")/..

if [ ! -d classpath-0.93 ]; then
    echo "skip: classpath-0.93/ not present (download per CLAUDE.md)"
    echo "skip: source-level fixtures require an end-to-end translate path"
    exit 0
fi

if [ ! -f jcgo.jar ]; then
    echo "skip: jcgo.jar not built — run mkjcgo/build-java.sh first"
    exit 0
fi

# Versions whose dirs are real fixtures (skip pure-mirror dirs for now):
#   5 7 8 9 10 11 14 15 16 17 21
#
# Pure-mirror dirs (6, 12, 13, 18, 19, 20) are intentionally identical to
# their predecessor; their tests can be added once the runner stabilises.
versions="5 7 8 9 10 11 14 15 16 17 21"

# TODO once prereqs are met:
#   for v in $versions; do
#       prev=$(($v - 1))
#       outdir=".build_tmp/source-levels/java$v"
#       mkdir -p "$outdir"
#       # Positive: translate at -source $v
#       java -jar jcgo.jar -source "$v" -d "$outdir" \
#            -src "examples/java$v" -src goclsp/clsp_asc Hello \
#            @stdpaths.in
#       # Negative: same fixture at -source $prev should fail
#       if java -jar jcgo.jar -source "$prev" -d "$outdir" \
#               -src "examples/java$v" -src goclsp/clsp_asc Hello \
#               @stdpaths.in 2>/dev/null; then
#           echo "FAIL: java$v parsed under -source $prev (gate missing)"
#           exit 1
#       fi
#   done

echo "todo: per-version positive+negative translate tests not yet wired"
exit 0
