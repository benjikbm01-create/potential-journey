#!/usr/bin/env bash
# Stage 12: PRODUCTION Fabric client (portablemc-installed Minecraft 1.21.1 + Fabric Loader 0.17.3, intermediary
# runtime like players use) with the production Underdeep JAR and a mod group, running the screenshot tour on a copy
# of a test world under Xvfb (software GL). Usage: scripts/prod_client.sh <name> <world-dir> <tour.txt> [mod patterns]
set -uo pipefail
NAME="$1"; WORLD="$2"; TOUR="$3"; shift 3
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
P="$ROOT/run-tests/pmc"
G="$P/game_$NAME"
rm -rf "$G"; mkdir -p "$G/mods" "$G/saves"
JAR=$(ls "$ROOT"/build/libs/underdeep-*.jar | grep -v sources | sort -V | tail -1)
cp "$JAR" "$G/mods/"
find /root/.gradle/caches/modules-2 -name "fabric-api-0.116.18+1.21.1.jar" -exec cp {} "$G/mods/" \; -quit
for pat in "$@"; do cp "$ROOT"/run-tests/mods-cache/$pat "$G/mods/"; done
cp -r "$WORLD" "$G/saves/tourworld"; rm -f "$G/saves/tourworld/session.lock"
cp "$ROOT/run-client/options.txt" "$G/options.txt" 2>/dev/null || true
cp "$TOUR" "$G/tour.txt"
cd "$P"
LIBGL_ALWAYS_SOFTWARE=1 timeout 1500 xvfb-run -a -s "-screen 0 1280x720x24" portablemc --main-dir ./main start --mc-dir "$G" \
  --join-world tourworld --jvm-arg=-Xmx3G --jvm-arg=-Dunderdeep.tour="$G/tour.txt" fabric:1.21.1:0.17.3 > "$G/console.log" 2>&1
LOG="$ROOT/run-tests/pc12_$NAME.log"
{
  echo "mods: $(grep -oE 'Loading [0-9]+ mods' "$G/logs/latest.log" "$G/console.log" 2>/dev/null | head -1)"
  grep -hE "Tour '.*average tick" "$G/logs/latest.log" 2>/dev/null | sed -E 's/.*Tour (.*)/\1/'
  echo "screenshots: $(ls "$G/screenshots" 2>/dev/null | wc -l)"
  echo "errors: $(grep -hE 'ERROR\]|Exception' "$G/logs/latest.log" 2>/dev/null | grep -vcE 'yggdrasil|Yggdrasil|minecraftservices|UnknownHost|sessionserver|OpenAL|SoundSystem|realms|Realms|telemetry')"
  grep -hE 'ERROR\]' "$G/logs/latest.log" 2>/dev/null | grep -vE 'yggdrasil|Yggdrasil|minecraftservices|UnknownHost|sessionserver|OpenAL|SoundSystem|realms|Realms|telemetry' | head -5
  grep -hE "crash|Crash" "$G/console.log" | head -3
} > "$LOG"
cat "$LOG"
