#!/usr/bin/env bash
# Full verification matrix (Stage 1.5). Results: run-tests/*/selftest/report.md and the log of this script.
set -uo pipefail
cd "$(dirname "$0")/.."
rm -rf build/libs
./gradlew --no-daemon --offline build -Dunderdeep.previews=true 2>&1 | grep -E "Test.*(PASSED|FAILED)|BUILD"
rm -rf run-tests/m_*
scripts/selftest.sh 12345 run-tests/m_12345 create forward
scripts/selftest.sh 12345 run-tests/m_12345 reload forward
scripts/selftest.sh 12345 run-tests/m_12345_rev create reverse
scripts/selftest.sh -987654321 run-tests/m_-987654321 create forward
scripts/selftest.sh 42 run-tests/m_42 create forward
mkdir -p run-tests/m_vanilla && printf 'level-seed=12345\nonline-mode=false\nmax-tick-time=-1\nview-distance=8\n' > run-tests/m_vanilla/server.properties
echo "eula=true" > run-tests/m_vanilla/eula.txt
./gradlew --no-daemon --offline runServer -PrunDir=run-tests/m_vanilla -Pselftest="$PWD/run-tests/m_vanilla/selftest" \
  -PexpectVanilla=true --args="nogui" 2>&1 | tail -n 1
grep -E "Summary|FAIL" run-tests/m_vanilla/selftest/report.md
echo "--- determinism (forward vs reverse chunk order, same seed)"
python3 tools/analysis/world_diff.py run-tests/m_12345/world run-tests/m_12345_rev/world 31 31 40 40 | grep -E "blocks compared|terrain-level"
# Production JAR on the official Fabric server launcher
P=run-tests/m_prod; mkdir -p $P/mods
cp build/libs/underdeep-*-stage*.jar $P/mods/ 2>/dev/null; rm -f $P/mods/*-sources.jar
find /root/.gradle/caches/modules-2 -name "fabric-api-0.116.18+1.21.1.jar" -exec cp {} $P/mods/ \;
cp run-tests/prod/fabric-server-launch.jar $P/ 2>/dev/null || curl -sS -o $P/fabric-server-launch.jar "https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.17.3/1.1.0/server/jar"
cp -r run-tests/prod/libraries run-tests/prod/server.jar run-tests/prod/.fabric $P/ 2>/dev/null
echo "eula=true" > $P/eula.txt
printf 'level-type=underdeep\\:underdeep\nlevel-seed=2024\nonline-mode=false\nmax-tick-time=-1\nview-distance=8\nsync-chunk-writes=false\n' > $P/server.properties
(cd $P && timeout 2400 java -Xmx8G -Dunderdeep.selftest=$PWD/selftest -jar fabric-server-launch.jar nogui > console.log 2>&1)
grep -E "Summary|FAIL" $P/selftest/report.md
for d in run-tests/m_*/logs/latest.log; do echo "--- $d: $(grep -E '/ERROR\]|ERROR\]|Exception' $d | grep -vcE 'yggdrasil|Yggdrasil|minecraftservices|UnknownHost|sessionserver') unexpected error/exception lines"; done
echo MATRIX DONE
