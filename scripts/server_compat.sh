#!/usr/bin/env bash
# Stage 12: production-JAR dedicated server with an optional group of third-party mods.
# Usage: scripts/server_compat.sh <name> <mode: audit|perf> [mod jar patterns...]
#   audit: runs the Underdeep self-test (SUITE=${SUITE:-audit}) on the official Fabric server launcher, then stops.
#   perf : starts the server without the self-test and feeds console commands from $CMDS (a file, one command per line,
#          "sleep N" lines pause) — used for Chunky pregeneration and Spark measurements.
# Mod jars come from run-tests/mods-cache (downloaded from Modrinth, see docs/MODPACK.md).
set -euo pipefail
NAME="$1"; MODE="$2"; shift 2
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
P="$ROOT/run-tests/s12_$NAME"
rm -rf "$P"; mkdir -p "$P/mods"
JAR=$(ls "$ROOT"/build/libs/underdeep-*.jar | grep -v sources | sort -V | tail -1)
cp "$JAR" "$P/mods/"
find /root/.gradle/caches/modules-2 -name "fabric-api-0.116.18+1.21.1.jar" -exec cp {} "$P/mods/" \; -quit
for pat in "$@"; do cp "$ROOT"/run-tests/mods-cache/$pat "$P/mods/"; done
cp "$ROOT/run-tests/prod/fabric-server-launch.jar" "$P/"
cp -r "$ROOT/run-tests/prod/libraries" "$ROOT/run-tests/prod/server.jar" "$ROOT/run-tests/prod/.fabric" "$P/" 2>/dev/null || true
echo "eula=true" > "$P/eula.txt"
printf 'level-type=underdeep\\:underdeep\nlevel-seed=%s\nonline-mode=false\nmax-tick-time=-1\nview-distance=8\nsimulation-distance=5\nsync-chunk-writes=false\nspawn-protection=0\n' "${SEED:-2024}" > "$P/server.properties"
cd "$P"
if [ "$MODE" = audit ]; then
  timeout 2400 java -Xmx6G -Dunderdeep.selftest="$P/selftest" -Dunderdeep.selftest.suite="${SUITE:-audit}" -jar fabric-server-launch.jar nogui > console.log 2>&1 || true
  grep -E "Summary" "$P/selftest/report.md" || echo "NO REPORT"
else
  ( while IFS= read -r line; do
      case "$line" in sleep*) sleep "${line#sleep }";; *) echo "$line";; esac
    done < "$CMDS"; echo stop ) | timeout 7200 java -Xms6G -Xmx6G -XX:+UseG1GC -Xlog:gc:file=gc.log -jar fabric-server-launch.jar nogui > console.log 2>&1 || true
fi
echo "mods loaded: $(grep -oE 'Loading [0-9]+ mods' console.log | head -1)"
echo "errors: $(grep -E 'ERROR\]|Exception' console.log | grep -vcE 'yggdrasil|Yggdrasil|minecraftservices|UnknownHost|sessionserver') unexpected lines"
