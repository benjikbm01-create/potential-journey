#!/usr/bin/env bash
# Runs a real Minecraft client under Xvfb (software OpenGL) on a saved world and captures screenshots
# of every viewpoint in a tour file (produced by the self-test). Development only.
# Usage: scripts/visual_tour.sh <world-dir> <tour.txt>
# Screenshots: run-client/screenshots/tour_<name>_{natural,nightvision}.png
set -euo pipefail
WORLD="$1"; TOUR="$2"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NAME="tourworld"
mkdir -p "$ROOT/run-client/saves"
rm -rf "$ROOT/run-client/saves/$NAME"
cp -r "$WORLD" "$ROOT/run-client/saves/$NAME"
rm -f "$ROOT/run-client/saves/$NAME/session.lock"
cp "$TOUR" "$ROOT/run-client/tour.txt"
if [ ! -f "$ROOT/run-client/options.txt" ]; then
cat > "$ROOT/run-client/options.txt" <<OPTS
version:3955
onboardAccessibility:false
renderDistance:12
simulationDistance:8
pauseOnLostFocus:false
gamma:0.5
tutorialStep:none
soundCategory_master:0.0
OPTS
fi
cd "$ROOT"
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew --no-daemon --offline runClient \
  -PclientRunDir=run-client -Ptour="$ROOT/run-client/tour.txt" -PtourWorld="$NAME"
ls -1 "$ROOT/run-client/screenshots" | grep tour_ || true
