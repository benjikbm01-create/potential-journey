#!/usr/bin/env bash
# Natural mob population study with a real client connected to the dedicated server (multiplayer).
# Usage: scripts/mob_study.sh <server-run-dir-with-existing-world> [seconds-per-site]
# Needs <run-dir>/selftest/ecosystem_sites.txt from a prior self-test. Report: <run-dir>/mobstudy/mobstudy.md
set -uo pipefail
RUN_DIR="$1"; SECS="${2:-60}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
grep -q '^simulation-distance' "$RUN_DIR/server.properties" && sed -i 's/^simulation-distance=.*/simulation-distance=8/' "$RUN_DIR/server.properties"
./gradlew --no-daemon --offline runServer -PrunDir="$RUN_DIR" -Pmobstudy="$ROOT/$RUN_DIR/mobstudy" \
  -PmobstudySites="$ROOT/$RUN_DIR/selftest/ecosystem_sites.txt" -PmobstudySeconds="$SECS" --args="nogui" > "$RUN_DIR/mobstudy-server.out" 2>&1 &
SERVER=$!
until grep -q "Done (" "$RUN_DIR/logs/latest.log" 2>/dev/null; do sleep 3; done
mkdir -p run-client-mp
printf 'version:3955\nonboardAccessibility:false\nrenderDistance:3\nsimulationDistance:5\npauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\njoinedFirstServer:true\n' > run-client-mp/options.txt
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 854x480x24" ./gradlew --no-daemon --offline runClient \
  -PclientRunDir=run-client-mp -PjoinServer=localhost:25565 > "$RUN_DIR/mobstudy-client.out" 2>&1 &
CLIENT=$!
wait $SERVER
pkill -f "fabric.dli.env=client" || true
wait $CLIENT 2>/dev/null
cat "$RUN_DIR/mobstudy/mobstudy.md"
