#!/usr/bin/env bash
# Self-test + visual tour + mob study for one seed. Usage: scripts/stage15_pipeline.sh <seed> [mob-seconds]
set -uo pipefail
SEED="$1"; SECS="${2:-60}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
DIR="run-tests/s15_$SEED"
rm -rf "$DIR"
scripts/selftest.sh "$SEED" "$DIR" create forward
rm -rf run-client/screenshots
timeout 3600 scripts/visual_tour.sh "$DIR/world" "$DIR/selftest/tour.txt" > "$DIR/tour.out" 2>&1
mkdir -p "$DIR/screenshots" && cp run-client/screenshots/*.png "$DIR/screenshots/" 2>/dev/null
timeout 3600 scripts/mob_study.sh "$DIR" "$SECS"
echo PIPELINE DONE
