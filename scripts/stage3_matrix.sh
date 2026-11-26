#!/usr/bin/env bash
# Targeted Stage 3 verification (Stage 2 + Stage 3 checks; terrain unchanged): build + unit tests, self-test on
# three seeds, save/reload on one, vanilla regression and the production JAR on the official Fabric
# server launcher. Terrain-only Stage 1.5 analyses are skipped (SUITE=stage2); run test_matrix.sh when
# the terrain generator changes.
set -uo pipefail
cd "$(dirname "$0")/.."
export SUITE=stage2
rm -rf build/libs run-tests/s3m_*
./gradlew --offline build -q 2>&1 | tail -n 3
scripts/selftest.sh 12345 run-tests/s3m_12345 create forward
cp run-tests/s3m_12345/selftest/report.md run-tests/s3m_12345/selftest/report_create.md
scripts/selftest.sh 12345 run-tests/s3m_12345 reload forward
scripts/selftest.sh -987654321 run-tests/s3m_-987654321 create forward
scripts/selftest.sh 42 run-tests/s3m_42 create forward
mkdir -p run-tests/s3m_vanilla && printf 'level-seed=12345\nonline-mode=false\nmax-tick-time=-1\nview-distance=8\n' > run-tests/s3m_vanilla/server.properties
echo "eula=true" > run-tests/s3m_vanilla/eula.txt
./gradlew --no-daemon --offline runServer -PrunDir=run-tests/s3m_vanilla -Pselftest="$PWD/run-tests/s3m_vanilla/selftest" \
  -PexpectVanilla=true --args="nogui" 2>&1 | tail -n 1
grep -E "Summary|FAIL" run-tests/s3m_vanilla/selftest/report.md
P=run-tests/s3m_prod; mkdir -p $P/mods
cp build/libs/underdeep-*-stage*.jar $P/mods/ 2>/dev/null; rm -f $P/mods/*-sources.jar
find /root/.gradle/caches/modules-2 -name "fabric-api-0.116.18+1.21.1.jar" -exec cp {} $P/mods/ \;
cp run-tests/prod/fabric-server-launch.jar $P/ 2>/dev/null || cp run-tests/m_prod/fabric-server-launch.jar $P/
cp -r run-tests/prod/libraries run-tests/prod/server.jar run-tests/prod/.fabric $P/ 2>/dev/null || cp -r run-tests/m_prod/libraries run-tests/m_prod/server.jar run-tests/m_prod/.fabric $P/
echo "eula=true" > $P/eula.txt
printf 'level-type=underdeep\\:underdeep\nlevel-seed=2024\nonline-mode=false\nmax-tick-time=-1\nview-distance=8\nsync-chunk-writes=false\n' > $P/server.properties
(cd $P && timeout 2400 java -Xmx8G -Dunderdeep.selftest=$PWD/selftest -Dunderdeep.selftest.suite=stage2 -jar fabric-server-launch.jar nogui > console.log 2>&1)
grep -E "Summary|FAIL" $P/selftest/report.md
for d in run-tests/s3m_*/logs/latest.log; do echo "--- $d: $(grep -E 'ERROR\]|Exception' $d | grep -vcE 'yggdrasil|Yggdrasil|minecraftservices|UnknownHost|sessionserver') unexpected error/exception lines"; done
echo MATRIX DONE
