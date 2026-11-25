#!/usr/bin/env bash
# Runs the Underdeep dedicated-server self-test.
# Usage: [SUITE=full|stage2] scripts/selftest.sh <seed> <run-dir> [phase=create|reload] [order=forward|reverse]
# SUITE=stage2 skips the slow Stage 1.5 terrain analyses (connectivity, accessibility, landmarks, sites).
# Creates <run-dir> with server.properties (level-type underdeep:underdeep) on first use; the report
# is written to <run-dir>/selftest/. The server log is <run-dir>/logs/latest.log.
set -euo pipefail
SEED="$1"; RUN_DIR="$2"; PHASE="${3:-create}"; ORDER="${4:-forward}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/$RUN_DIR"
cd "$ROOT/$RUN_DIR"
echo "eula=true" > eula.txt
if [ "$PHASE" = "create" ] && [ ! -f server.properties ]; then
cat > server.properties <<PROPS
level-type=underdeep\:underdeep
level-seed=$SEED
level-name=world
online-mode=false
max-tick-time=-1
spawn-protection=0
view-distance=8
simulation-distance=6
sync-chunk-writes=false
PROPS
fi
cd "$ROOT"
./gradlew --no-daemon --offline runServer -PrunDir="$RUN_DIR" -Pselftest="$ROOT/$RUN_DIR/selftest" \
  -PselftestPhase="$PHASE" -PselftestOrder="$ORDER" -PselftestSuite="${SUITE:-full}" --args="nogui" 2>&1 | tail -n 5
echo "--- report: $RUN_DIR/selftest/report.md"
grep -E "Summary|FAIL" "$RUN_DIR/selftest/report.md" || true
