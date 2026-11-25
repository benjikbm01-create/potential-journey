#!/usr/bin/env bash
# Full Stage 1 verification matrix. Results: run-tests/*/selftest/report.md, logs in run-tests/*/logs.
set -uo pipefail
cd "$(dirname "$0")/.."
./gradlew --no-daemon --offline test -Dunderdeep.previews=true 2>&1 | grep -E "Test.*(PASSED|FAILED)|BUILD" 
rm -rf run-tests/seed12345 run-tests/seed12345_rev run-tests/seed-987654321 run-tests/seed42 run-tests/vanilla
scripts/selftest.sh 12345 run-tests/seed12345 create forward
scripts/selftest.sh 12345 run-tests/seed12345 reload forward
scripts/selftest.sh 12345 run-tests/seed12345_rev create reverse
scripts/selftest.sh -987654321 run-tests/seed-987654321 create forward
scripts/selftest.sh 42 run-tests/seed42 create forward
# Vanilla regression: default world type with the mod installed.
mkdir -p run-tests/vanilla && printf 'level-seed=12345\nonline-mode=false\nmax-tick-time=-1\nview-distance=8\n' > run-tests/vanilla/server.properties
echo "eula=true" > run-tests/vanilla/eula.txt
./gradlew --no-daemon --offline runServer -PrunDir=run-tests/vanilla -Pselftest="$PWD/run-tests/vanilla/selftest" \
  -PexpectVanilla=true --args="nogui" 2>&1 | tail -n 2
grep -E "Summary|FAIL" run-tests/vanilla/selftest/report.md
echo "--- determinism across chunk orders"
if cmp -s run-tests/seed12345/selftest/chunk_hashes_create_forward.txt run-tests/seed12345_rev/selftest/chunk_hashes_create_reverse.txt; then
  echo "DETERMINISM PASS: forward vs reverse chunk hashes identical ($(wc -l < run-tests/seed12345/selftest/chunk_hashes_create_forward.txt) chunks)"
else
  echo "DETERMINISM DIFF:"; diff run-tests/seed12345/selftest/chunk_hashes_create_forward.txt run-tests/seed12345_rev/selftest/chunk_hashes_create_reverse.txt | head -20
fi
for d in run-tests/*/logs/latest.log; do echo "--- $d: $(grep -cE '/ERROR\]|Exception' $d) error/exception lines"; grep -E '/ERROR\]|Exception' $d | grep -v -E "yggdrasil|Yggdrasil|minecraftservices|UnknownHost|sessionserver" | head -5; done
echo MATRIX DONE
