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
| Mob population study | `scripts/mob_study.sh <run-dir>` (real client joins the dedicated server; uses `<run-dir>/selftest/ecosystem_sites.txt` from a self-test) → `<run-dir>/mobstudy/mobstudy.md` |
| Stage 1.5 matrix | `scripts/test_matrix.sh` (also runs the Stage 1.5 checks; production JAR on the official Fabric server launcher) |

## Results — Stage 1.5 final runs (2026-11-25)

Same hardware and toolchain as Stage 1 (below). Terrain generator version 2. All numbers below come from
the final matrix (`scripts/test_matrix.sh`, run directories `run-tests/m_*`) on the final code, except where
a row says otherwise.

### Build and unit tests — PASS
`./gradlew build --offline` succeeds; JAR `build/libs/underdeep-0.2.0-stage1.5.jar`. JUnit: 9 tests PASSED
(`TerrainModelTest` 8 incl. all 15 ecosystems near the origin and floor continuity < 4 blocks per block step,
which allows steep shorelines; `DistributionReportTest` 1).

### Dedicated-server self-tests — all PASS
| Run | Seed | Result |
|---|---|---|
| create, forward | 12345 | 86 passed, 0 failed |
| reload (restart on the saved world) | 12345 | PASS, 0 failed (25 spawn chunk hashes + benchmark area identical) |
| create, reverse chunk order | 12345 | 84 passed, 0 failed |
| create | −987654321 | 84 passed, 0 failed |
| create | 42 | 83 passed, 0 failed (The Abyss light probe found no dry floor near its site: info only) |
| **production JAR** on the official Fabric server launcher | 2024 | 84 passed, 0 failed |
| vanilla regression (default world type, mod installed) | 12345 | 4 passed, 0 failed |
| memory re-run after adding the heap line (create) | 12345 | 84 passed, 0 failed |

Log scan: no generation exceptions; only the expected offline-mode authentication errors.

### Region distribution (terrain model, 4000×4000 blocks per layer)
| Seed | upper median / p90 / max eq. diameter | size classes <60 / 60–120 / 120–250 / 250–400 / 400–700 / >700 | ecosystems within 500 / 1000 / 2500 |
|---|---|---|---|
| 12345 | 102 / ~250 / ~700 | 306 / 278 / 285 / 66 / 25 / 1 | 7 / 11 / 15 |
| −987654321 | 95 / ~250 / ~700 | 329 / 262 / 256 / 76 / 27 / 1 | 7 / 13 / 15 |
| 42 | 96 / ~250 / ~700 | 329 / 267 / 265 / 71 / 30 / 0 | 6 / 12 / 15 |
| 2024 (prod) | 93 | 383 / 269 / 288 / 84 / 21 / 0 | 8 / 14 / 15 |
Counts within 500/1000 are upper-layer ecosystems with a meaningful area; "2500" counts all 15 including
the deep-only Abyss. A walk along a straight line crosses an ecosystem border every 166–168 blocks
(8.1–8.3 changes per 1000 blocks). Upper shares 3.5–13.6 % per ecosystem; deep layer: The Abyss 55.7–58.5 %,
Crystal Deep / Stormstone / Ashen the rest. Cells < 60 blocks are mostly slivers along borders.

### Survival accessibility (walking/swimming BFS on the terrain model, radius 1500, 4-block grid)
- 84–90 % of the upper layer's open area is reachable from spawn on foot or swimming; 14/14 upper
  ecosystems reachable on every seed; 80–83 % of sampled ecosystem border crossings are walkable.
- Walking vs straight distance (seed 12345): Fungal 84→92, Golden 43→64, Emerald 118→152, Petrified 231→264,
  Flooded 241→328, Everfrost 524→644, Crystal 545→732, Hollow Peaks 652→744, Ashen 722→892,
  Stormstone 740→936, Crimson 1018→1376, Sunken 1145→1596 (≈1.1–1.4×).
- Not measured: climbing between the two layers (shafts); reaching The Abyss requires going down a shaft.

### Landmarks and settlement-compatible terrain
- Upper landmarks per 16 km²: 98–113 (12–23 titan-scale); deep 41–56. All six kinds present on every seed.
- Settlement-compatible areas around the 324 upper chambers (seed 12345): outpost-size 54, village 90,
  town 29, city-size 45; every size class present on every seed.

### Natural lighting (generated chunks; ground-level block light; 5×5 chunks per ecosystem, inner 3×3 measured)
% of walkable ground with block light ≥ 8 (mean light) per seed 12345 / −987654321 / 42 / 2024:
| Ecosystem | Class | 12345 | −987654321 | 42 | 2024 |
|---|---|---|---|---|---|
| Rootwild | bright | 95 % (10.5) | 90 % (10.1) | 88 % (10.1) | 83 % (9.4) |
| Fungal Expanse | bright | 100 % (10.3) | 93 % (9.7) | 97 % (10.1) | 97 % (10.1) |
| Golden Grove | bright | 76 % (9.2) | 99 % (11.2) | 96 % (10.8) | 100 % (11.4) |
| Emerald Wetlands | bright | 65 % (8.3) | 86 % (9.7) | 98 % (11.0) | 97 % (11.1) |
| Starlight Caverns | bright | 100 % (11.4) | 100 % (11.3) | 100 % (11.4) | 65 % (8.4) |
| Flooded Depths | moderate | 80 % (9.1) | 64 % (8.0) | 42 % (5.9) | 68 % (8.0) |
| Crystal Deep | moderate | 74 % (8.6) | 63 % (7.9) | 72 % (8.4) | 66 % (8.3) |
| Everfrost | moderate | 67 % (8.1) | 56 % (7.3) | 54 % (7.1) | 67 % (8.1) |
| Hollow Peaks | moderate | 62 % (7.8) | 75 % (8.8) | 71 % (8.3) | 50 % (7.1) |
| Petrified Forest | moderate | 20 % (5.5) | 26 % (5.6) | 24 % (5.8) | 29 % (6.4) |
| Sunken Gardens (lake bed) | moderate | 95 % (11.0) | 93 % (10.9) | 97 % (11.4) | 94 % (10.9) |
| Ashen Hollows | dark | 24 % (3.8) | 4 % (1.5) | 24 % (4.5) | 21 % (4.2) |
| Crimson Basin | dark | 35 % (6.5) | 26 % (5.3) | 40 % (6.4) | 38 % (6.2) |
| Stormstone Caverns | dark | 7 % (3.7) | 9 % (4.3) | 7 % (3.5) | 12 % (4.8) |
| The Abyss | pitch | 1 % (0.5) | 1 % (1.1) | — | 0 % (1.0) |
Block light at spawn: 10–12. Sky light is 0 everywhere. A single 3×3-chunk window per ecosystem is a small
sample: one window can fall on a darker sub-area (e.g. Starlight on seed 2024, Emerald on seed 12345).

### Survival growth without sunlight — PASS (every Underdeep run)
- Oak sapling grows into a tree under natural block light (light 10–11, sky light 0) within 14 random ticks.
- Wheat can be planted on hoed ground near spawn and matures in 23–24 random ticks at light 10–11.
- Core early-game recipes present (crafting table, furnace, torches, wooden/stone tools, bread, bed).
- Starting resources within 48 blocks: logs 1 412–9 625, coal 2 365–5 201, iron 974–1 880 ore blocks,
  water ≥ 821, food plants ≥ 700, animals 0–43 (entity-loading timing, see Stage 1 known issues).

### Natural mob populations (real client joined the dedicated server; EASY; 60 s per site; hostiles within 64 blocks horizontal / 40 vertical, sampled every 10 s)
Seed 12345, final spawning rules (`run-results/mobstudy_12345/mobstudy.md`, local, not committed):
| Site | Danger | Hostile samples | Max |
|---|---|---|---|
| starter cavern (2 sites) | low (starter zone) | 0 at every sample | 0 |
| Rootwild, Fungal, Golden, Emerald, Starlight | low | 0 at every sample | 0 |
| Crystal Deep, Everfrost, Hollow Peaks, Petrified | medium | 0 at every sample | 0 |
| Sunken Gardens | medium | 0–1 | 1 |
| Flooded Depths | medium | 3–5 | 5 |
| Crimson Basin | high | 2–6 | 6 |
| Stormstone Caverns | high | 5–7 | 7 |
| Ashen Hollows | high | 5–8 | 8 |
| The Abyss | extreme | 12–16 | 16 |
Seed 42 (run before the peaceful border buffer was added): low-danger ecosystems 0 except Golden Grove max 2
and Emerald max 3 (mobs from bordering regions; the buffer added afterwards removed these on seed 12345);
Ashen max 8, Stormstone max 9, Crimson max 4, Everfrost 1, Hollow Peaks 1. The seed 42 study was not repeated
with the final rules. Medium ecosystems often read 0 in one minute because only 12 % of attempts are allowed.

### Determinism, persistence, compatibility
- Forward vs reverse chunk order, seed 12345: terrain level **0 differences in 9 436 125 compared blocks**;
  decoration differs in 0.36 % of blocks (cross-chunk features, vanilla behaviour).
- Save + restart: 25 spawn chunk hashes and the benchmark area identical; world state persisted.
- Vanilla world type unaffected (NoiseChunkGenerator, skylight, surface spawn).

### Performance (this hardware)
| Measurement | Result |
|---|---|
| Terrain fill + surface, single thread | 11.1–12.6 ms/chunk |
| Same, 3 worker threads | 212–242 chunks/s |
| Full generation incl. decoration, 144 chunks sequential | 24.2–26.1 ms/chunk (Stage 1: 27.5–29.0) |
| Memory after self-test (seed 12345, 10 173 chunks loaded, `-Xmx6G`) | 956 MB used before GC, 551 MB after GC |
| Integrated-server tick during client tours | 2.5–15 ms |
| `saveAll` after self-test | ~2.8 s |
Not measured: 4–6 real players, long sessions, real GPU frame rates.

### Graphical inspection — executed
Real client (Xvfb, software OpenGL) toured all 15 ecosystems on seeds 12345 and 42, elevated and
ground-level views, natural lighting (night vision only for The Abyss overview). Defects found and fixed
during the stage: whole glowing canopy blobs, repetitive painted squares on stepped walls, a giant trunk
filling the starter pond, bright ecosystems too dark at ground level. A final short tour confirmed the
brighter floors read as natural glowing ground cover rather than uniform light. Samples: `docs/images/s15_*.jpg`.
Some tour cameras sit inside formations or behind foliage; the screenshots are representative, not curated.

### GUI world creation
PASS (re-run on the final build): the real Create World screen offered "Default, Superflat, Large Biomes,
AMPLIFIED, Single Biome, Underdeep"; selecting Underdeep and pressing Create produced an Underdeep world
(generator `UnderdeepChunkGenerator`, random seed, spawn (−4,119,8), skylight false, block light 10 at the player).

### Not tested in Stage 1.5
Hands-on survival play, multiplayer with several real clients, long sessions, real GPU frame rates,
the seed 42 mob study with the final buffer rules, travel between layers on foot.

## Results — Stage 1 final runs (history; superseded where Stage 1.5 changed the world)

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
- Stage 1.5: Sunken Gardens have no underwater air pockets; Petrified Forest is the darkest "moderate"
  ecosystem (20–29 % of ground ≥ 8); some screenshots/probes land in darker sub-areas; vanilla hostile mobs
  are placeholders; textures of the 21 custom blocks are procedural placeholders.
- Decoration is not bit-identical across generation orders (Stage 1.5: 0.36 % of blocks; terrain is identical).
- Inter-layer connections are shafts only; some 336×336 windows contain none (layers still connect within
  a few hundred blocks). Players may need to build to climb shafts without spiral ledges.
- Animal counts in the start-resource check can read 0 (entity loading timing); the food check passes via
  plants regardless.
- Distant views end in flat fog colour at the render-distance edge (vanilla fog behaviour).
- Not tested: a separate client joining the dedicated server over the network (multiplayer with real
  players), long play sessions, memory profiling, real GPU frame rates, manual hands-on play.
