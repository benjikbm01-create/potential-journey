# Underdeep — Testing Record

Only tests that were actually executed are recorded as passing. Dates are UTC.

## Test environment (Stage 1, 2026-11-25)
- Linux container, 4 vCPU Intel Xeon @ 2.10 GHz, 15 GB RAM, no GPU.
- OpenJDK 21.0.13; Gradle 8.14.3; Loom 1.11.8; Fabric Loader 0.17.3; Fabric API 0.116.18+1.21.1.
- Dedicated server: Loom `runServer` (real `net.minecraft.server.Main`, dev mappings), `-Xmx6G`,
  offline mode (no Mojang auth/network, so authentication errors in logs are expected).
- Client: Loom `runClient` under Xvfb (1280×720) with Mesa software OpenGL (`LIBGL_ALWAYS_SOFTWARE=1`),
  sound disabled (no audio device). Frame rates measured here say nothing about real hardware.

## How to run
| What | Command |
|---|---|
| Build + unit tests | `./gradlew build` (`./gradlew test -Dunderdeep.previews=true` also writes PNG previews to `build/previews/`) |
| Server self-test | `scripts/selftest.sh <seed> <run-dir> [create\|reload] [forward\|reverse]` → `<run-dir>/selftest/report.md`, `checks.json`, chunk hashes, previews, `tour.txt` |
| Whole Stage 1 matrix | `scripts/test_matrix.sh` (unit tests, 3 seeds, reload, reverse order, vanilla regression, determinism compare, log scan) |
| Visual tour | `scripts/visual_tour.sh <world-dir> <tour.txt>` → `run-client/screenshots/tour_*.png` |
| GUI world creation | `LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient -PcreateWorldTest=<name>` |
| Block-level world diff | `python3 tools/analysis/world_diff.py <worldA> <worldB> cx0 cz0 cx1 cz1` (needs `pip install nbtlib`) |

## Results — Stage 1 final runs

### Build and unit tests — PASS
`./gradlew build` succeeds (no compiler errors). JUnit (`TerrainModelTest`, 8 tests, seeds 12345,
−987654321, 42): determinism across instances and query order; different seeds differ; bedrock floor and
roof sealed and no open space above y302 over 4000×4000 blocks sampled; all 6 regions within 2600 blocks
of the origin, origin is Rootwild; RegionId round trip; start cavern open and dry; base floor continuous
across region borders (< 2 blocks per block step); preview rendering. All 8 PASSED.

### Dedicated-server self-tests — all PASS
| Run | Seed | Result |
|---|---|---|
| create, forward order | 12345 | 47 passed, 0 failed (final run, includes 11 command checks) |
| reload (server restart on the saved world) | 12345 | 38 passed, 0 failed (run before command checks existed) |
| create, reverse chunk order | 12345 | 36 passed, 0 failed |
| create | −987654321 | 36 passed, 0 failed |
| create | 42 | 36 passed, 0 failed |
| vanilla regression (default world type, mod installed) | 12345 | 4 passed, 0 failed |
| **production JAR** on the official standalone Fabric server launcher (loader 0.17.3, Fabric API 0.116.18 jar, `level-type=underdeep\:underdeep`) | 2024 | 47 passed, 0 failed |

Verified by these runs:
- **Registry initialization / preset**: Underdeep generator active; dimension has no skylight, has a
  ceiling, y −64..319; 6/6 biomes registered.
- **Safe spawning**: spawn validated (solid 3×3 floor, 3 blocks headroom, no fluid, no lava/magma within
  3 blocks, not covered overhead), player hitbox fits; spawn in Rootwild; sky light 0; block light 5–6
  at spawn (glowcaps). Spawns: (−8,147,−4), (−12,146,−12) [same seed, other run], (0,147,0), (−12,138,8), (2,139,−2) [seed 2024].
- **Starting resources within 48 blocks** (ranges over the 4 Underdeep runs): logs 1727–5328, food plants
  461–1492, animals 0–39*, water 440–821 blocks, stone > 80 000, coal 2672–4988, iron 1100–1554,
  copper 538–863 ore blocks, light sources 405–1823, walkable floor columns 3026–3150.
  *Animal count reads 0 in one run although herds are generated near spawn in others; entity sections may
  not be loaded when the check runs — treat as a measurement limitation (see Known issues).
- **All six regions present** with matching biomes: within 0–1267 blocks of the origin for every seed.
- **Cavern size variation** (terrain model, 3000×3000 blocks): upper layer 66–70 % of columns open,
  open height median 49–54 / p90 102–111 / max 175–185 blocks; chamber radius 34 → 225–237 blocks with
  40–49 colossal chambers of 324. Deep layer 74 % open, median ~60 / p90 ~104 / max 172; radius 44 → 287–311.
- **Boundaries**: bedrock floor (y−64) and roof (y319) complete in 81 chunks around spawn; 0 air blocks in
  y305–318 (no surface access); 0 of ~25 000 sampled air blocks sky-lit.
- **Fluid stability**: 0 fluid source blocks directly above air in 81 chunks around spawn (no leaks).
- **Connectivity (ground truth flood fill of generated blocks, 336×336 areas, y −60..310)**: in every
  area every sampled chamber is in a main network and each layer's chambers form a single network. Where a
  shaft lies in the window the whole volume is one network (98.8–99.5 % of open blocks); where none does
  (seed 42 spawn area, seed −987654321 remote area) the two layers are separate networks (upper/deep),
  together holding 98.5–99.0 %. Remaining ~1 % are small sealed pockets (largest ~30 000 blocks).
- **Save and restart**: after `saveAll` and a full server restart, 25 spawn-area chunk hashes and 100
  benchmark-area chunk hashes were identical; world state (`data/underdeep.dat`) persisted.
- **Determinism / chunk order**: same seed generated forward vs reverse: terrain level (rock/air/water/lava)
  **0 differences in 9 377 308 compared blocks**; decoration differs in 0.77 % of blocks (leaves, moss
  patches, vines, grass, ores, hanging roots) — features that cross chunk borders are applied in
  scheduling order, the same behaviour as vanilla. Terrain-stage benchmark checksums are identical across
  JVM runs (seed 12345: `804ee67ea4ab7cf9` in both runs).
- **Diagnostic commands** (seed 12345 final run): `/underdeep info, region, locate crystal_deep,
  locate the_abyss, spawncheck, cavern, sites village 800, sites floating 1500, connectivity 2, perf 32,
  render 256` all executed through the real dispatcher with success results; outputs in the server log.
  (`perf`/`render` run asynchronously; the server stopped before their completion messages.)
- **Vanilla regression**: with the mod installed, a default world uses `NoiseChunkGenerator`, has skylight
  and a surface spawn (96,144,−32).
- **Log scan**: every run's `latest.log` contains only the 3 expected offline-mode authentication errors
  (Yggdrasil key / session server unreachable); no world generation exceptions.

### Performance (measured; this hardware)
| Measurement | Result |
|---|---|
| Terrain fill + surface only, detached chunks, single thread | 12.0–15.1 ms/chunk |
| Same, 3 worker threads | 185–234 chunks/s |
| Full generation incl. decoration, 144 chunks requested sequentially from the main thread | 27.5–29.0 ms/chunk |
| Vanilla default world, same method (64 chunks, different area) | 122 ms/chunk (for reference only) |
| Integrated-server average tick while a spectator teleported across the world generating chunks (client tour, software rendering competing for the 4 CPUs) | 2.5–15 ms (20 TPS needs ≤ 50 ms) |
| `saveAll` after self-test (~1 300 chunks) | 2.4–2.9 s |
Not measured: TPS with 4–6 real players on a dedicated server, memory over a long session.

### Graphical inspection — executed
Real client under Xvfb with software OpenGL, screenshots of spawn and one large cavern per region
(seeds 12345 and 42), natural lighting and night vision. Several defects found and fixed this session:
overly thin/dense stalactites, zebra-striped rock strata, over-dense spawn forest, basalt-pillar
"forests" in Ashen Hollows/Abyss, too-dark ambient light, the "experimental settings" warning on load.
After fixes: spawn is an open lit grove; Rootwild giant forest cavern with waterfalls and root columns;
Fungal Expanse giant mushrooms and fungi; Flooded Depths sand shores, islands, lakes, waterfalls; Ashen
Hollows dark basalt halls with lava; Crystal Deep spires, clusters and a natural bridge; the Abyss colossal
pillars and chasm floors; shafts with spiral ledges. Sample images: `docs/images/`.

### GUI world creation — PASS
Driving the real Create World screen: offered world types "Default, Superflat, Large Biomes, AMPLIFIED,
Single Biome, Underdeep"; selecting Underdeep and pressing Create produced an Underdeep world (generator
`UnderdeepChunkGenerator`, spawn (−10,123,10), skylight false, block light 8 at the player) with no
experimental-settings warning.

## Known issues and limitations
- Decoration is not bit-identical across generation orders (0.77 % of blocks; terrain is identical).
- Inter-layer connections are shafts only; some 336×336 windows contain none (layers still connect within
  a few hundred blocks). Players may need to build to climb shafts without spiral ledges.
- Animal counts in the start-resource check can read 0 (entity loading timing); the food check passes via
  plants regardless.
- Distant views end in flat fog colour at the render-distance edge (vanilla fog behaviour).
- Not tested: a separate client joining the dedicated server over the network (multiplayer with real
  players), long play sessions, memory profiling, real GPU frame rates, manual hands-on play.
