#!/usr/bin/env bash
# Stage 12: real client (dev run, Xvfb, software GL) with a group of third-party mods, on a copy of a test world.
# Usage: scripts/client_compat.sh <name> <world-dir> <tour.txt> [mod jar patterns...]   (JFR=1 records a flight recording)
# Output: run-tests/c12_<name>.log (fps / server tick per viewpoint, errors), screenshots tour_* in run-client/screenshots.
set -uo pipefail
NAME="$1"; WORLD="$2"; TOUR="$3"; shift 3
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "$ROOT/run-client/mods"; mkdir -p "$ROOT/run-client/mods"
for pat in "$@"; do cp "$ROOT"/run-tests/mods-cache/$pat "$ROOT/run-client/mods/"; done
LOG="$ROOT/run-tests/c12_$NAME.log"
if [ "${JFR:-0}" = 1 ]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -XX:StartFlightRecording=filename=$ROOT/run-tests/c12_$NAME-%p.jfr,settings=profile,dumponexit=true"
fi
timeout 1800 "$ROOT/scripts/visual_tour.sh" "$WORLD" "$TOUR" > "$LOG.full" 2>&1
rm -rf "$ROOT/run-client/mods"
{
  echo "mods: $(grep -oE 'Loading [0-9]+ mods' "$LOG.full" | head -1)"
  grep -E "Tour '.*average tick" "$LOG.full" | sed -E 's/.*Tour (.*)/\1/'
  echo "errors: $(grep -E 'ERROR\]|Exception' "$LOG.full" | grep -vcE 'yggdrasil|Yggdrasil|minecraftservices|UnknownHost|sessionserver|OpenAL|realms|Realms|telemetry')"
  grep -E 'ERROR\]|Exception' "$LOG.full" | grep -vE 'yggdrasil|Yggdrasil|minecraftservices|UnknownHost|sessionserver|OpenAL|realms|Realms|telemetry' | head -5
} > "$LOG"
cat "$LOG"
