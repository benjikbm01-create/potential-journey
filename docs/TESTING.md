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
| Stage 2 targeted | `SUITE=stage2 scripts/selftest.sh <seed> <dir> [create\|reload]`; `scripts/stage2_matrix.sh` (3 seeds, reload, vanilla, production JAR); tour views of Stage 2 flora in `<dir>/selftest/tour_stage2.txt` |

## Results — Stage 2 (2026-11-26)

Same hardware/toolchain as before (4 vCPU container, OpenJDK 21, `-Xmx6G` dev server, Xvfb + software
OpenGL client). Targeted suite per the Stage 2 brief (D-032): `SUITE=stage2` self-tests and
`scripts/stage2_matrix.sh`; the slow Stage 1.5 terrain analyses were **not** re-run because terrain
output is unchanged (terrain-stage checksum for seed 12345 `f6fb2cde5f4f2a93`, identical to Stage 1.5).

### Build and unit tests — PASS
`./gradlew build` succeeds; JAR `build/libs/underdeep-0.3.0-stage2.jar`. JUnit 13 tests PASSED: the 9
Stage 1.5 terrain tests plus `EcologyRegistryTest` (every ecosystem has ≥ 3 biological species and a
regional mineral (Golden Grove: renewable resin instead); species/block ids unique; content counts in
the target ranges; `RegionEcology` deterministic and bounded; Rootwild more productive than the Abyss).

### Dedicated-server self-tests (stage 2 suite) — final runs
| Run | Seed | Result |
|---|---|---|
| create (`run-tests/s2m_12345`) | 12345 | 161 passed, 0 failed |
| reload (server restart on the saved world) | 12345 | PASS, 0 failed (spawn + benchmark chunk hashes identical; Stage 2 plots persisted) |
| create (`s2f_-987654321`, re-run after moving the reactive-plant test plot into the spawn chunks) | −987654321 | 161 passed, 0 failed |
| create (`s2f_42`, same re-run) | 42 | 161 passed, 0 failed |
| **production JAR** on the official Fabric server launcher (loader 0.17.3, Fabric API 0.116.18) | 2024 | 161 passed, 0 failed |
| vanilla world type with the mod installed | 12345 | 5 passed, 0 failed (incl. "Underdeep spreading is inactive in vanilla worlds") |
Log scan of all matrix runs: 0 unexpected error/exception lines (only the offline-mode authentication
messages). Failures found and fixed during development (recorded honestly): wrong configured-feature
reference format for tree selectors (registry load failure), missing names for 29 plants, the golden
sapling pointing at a non-existent tree feature (`tree/goldwood`), test plots without computed light,
test assertions on dropped items/creatures in chunks without entity tracking (moved to spawn chunks),
a dark-blooming flower that could flicker because of its own light (fixed + new check), and the
glowpad spreading bound (first threshold 12 was arbitrary; a run produced 15 on a 49-cell pool, the
check now uses 20 — the actual guarantee is the per-neighbourhood density cap).

### Registries, resources and recipes (every run)
- 272 Underdeep blocks, 286 items registered; every block has a blockstate and a loot table, every item
  a model, every referenced texture exists (781 models checked), every block/item has an English name.
- 95 species in `EcologyRegistry`, all mapped to registered blocks; all 15 ecosystems have species.
- 154 Underdeep recipes loaded; verified through the real recipe manager: planks from all 7 logs,
  vanilla tag recipes (crafting table, sticks) from Underdeep planks, door, sign, gunpowder (sulfur +
  niter + charcoal), glowgrain bread, emberwort tonic, galvanite → redstone; smelting duskroot, inkcap,
  raw starsilver, malachite → copper. All 25 foods have food components.

### Natural generation — native Stage 2 species per ecosystem
Distinct native species found in 5×5 generated chunks around an open cavern floor of each ecosystem
(that cavern layer only); check requires ≥ 3:
| Ecosystem | 12345 | −987654321 | 42 | 2024 (prod) |
|---|---|---|---|---|
| Rootwild | 12 | 12 | 13 | 13 |
| Fungal Expanse | 11 | 11 | 10 | 10 |
| Flooded Depths | 4 | 7 | 8 | 5 |
| Ashen Hollows | 9 | 9 | 9 | 9 |
| Crystal Deep | 6 | 6 | 6 | 5 |
| The Abyss | 7 | 7 | 7 | 6 |
| Everfrost | 7 | 5 | 7 | 6 |
| Golden Grove | 7 | 8 | 8 | 8 |
| Hollow Peaks | 6 | 6 | 6 | 6 |
| Emerald Wetlands | 9 | 9 | 8 | 10 |
| Petrified Forest | 6 | 6 | 6 | 6 |
| Crimson Basin | 8 | 8 | 8 | 8 |
| Sunken Gardens | 7 | 7 | 7 | 7 |
| Stormstone Caverns | 5 | 5 | 5 | 5 |
| Starlight Caverns | 6 | 5 | 6 | 5 |
(Per-species block counts are in each `report.md`; e.g. seed 12345 Crimson Basin: bloodthorn 1562
blocks, thornbriar, plaguebloom, snapjaw, bloodcap, emberwort, cinnabar, sulfur.)

### Functional tests (sealed test plots in the target ecosystem, real block code, every create run)
- All 9 saplings grow into their tree on block light only (light 10, sky light 0) on their own soil;
  frostfir stays dormant next to heat (1500 random ticks).
- A growing duskoak surrounded by a plank roof and chests: 30/30 player blocks intact.
- Crops on farmland with water beside: duskroots, glowgrain, cave truffles (in darkness, light 0),
  mire rice, emberpeppers (with heat), frost turnips (Everfrost), sunbeans mature in 25–72 random ticks
  and drop their produce; lake chestnuts mature underwater; breaking a submerged plant leaves water.
- Fake-player harvest of a mature duskberry bush drops fruit, resets to age 1 and regrows; the harvest
  is recorded in the regional ecology state; a full resin knot yields amber.
- Foods/medicines: raw inkcap causes nausea, antidote cures it, emberwort tonic → fire resistance (bottle
  returned), moonpetal draught → night vision, baked duskroot restores 5 hunger.
- All 14 ores are mineable with a pickaxe and drop their mineral; a mature herb drops itself + leaves.
- Snapjaw damages a creature (10 → 7 health) and closes; a spore puff bursts on contact and weakens it.
- Pyrelily opens (light 12) beside magma and stays closed 5+ blocks away; voltbloom opens on a
  redstone-powered block; a prism lily opens in darkness and does not flicker afterwards (0 changes).
- Bounded spreading: one puffcap on a 15×15 fungal floor → 31 after 4000 rounds of random ticks on
  every cap (a forced, accelerated rate; real growth is far slower); fungal soil converted 6 dirt blocks, farmland intact; no fungi within
  2 blocks of a crafting table; glowpads on a 7×7 pool 6–15.
- Budding glimmer grows 6 buds/clusters in 3000 random ticks in darkness.
- Persistence (reload): 9/9 grown trees, 7/7 mature crops, regrown crystals and the ecology harvest
  record survived save + restart.

### Natural lighting after Stage 2 (% of ground with block light ≥ 8, mean)
| Ecosystem | 12345 | −987654321 | 42 | 2024 (prod) |
|---|---|---|---|---|
| Rootwild (bright) | 95 % (10.5) | 89 % (9.9) | 88 % (10.2) | 79 % (9.1) |
| Fungal Expanse (bright) | 93 % (10.0) | 84 % (9.1) | 90 % (9.7) | 87 % (9.5) |
| Golden Grove (bright) | 74 % (8.9) | 99 % (11.2) | 95 % (10.7) | 99 % (11.3) |
| Emerald Wetlands (bright) | 78 % (8.6) | 90 % (9.6) | 93 % (10.3) | 95 % (10.9) |
| Starlight Caverns (bright) | 100 % (11.4) | 99 % (11.3) | 99 % (11.3) | 65 % (8.5) |
| Flooded Depths | 79 % (9.1) | 68 % (8.4) | 62 % (7.7) | 73 % (8.5) |
| Crystal Deep | 75 % (8.6) | 60 % (7.8) | 71 % (8.4) | 64 % (8.1) |
| Everfrost | 64 % (8.0) | 58 % (7.4) | 55 % (7.1) | 65 % (8.1) |
| Hollow Peaks | 64 % (8.0) | 75 % (8.7) | 69 % (8.3) | 51 % (7.3) |
| Petrified Forest | 19 % (5.4) | 21 % (5.5) | 27 % (5.8) | 29 % (6.4) |
| Sunken Gardens (lake bed) | 94 % (11.0) | 95 % (10.8) | 97 % (11.4) | 93 % (10.9) |
| Ashen Hollows (dark) | 6 % (1.9) | 17 % (3.4) | 22 % (4.1) | 17 % (3.3) |
| Crimson Basin (dark) | 33 % (5.9) | 33 % (5.9) | 38 % (6.3) | 45 % (6.6) |
| Stormstone Caverns (dark) | 9 % (4.2) | 8 % (4.4) | 6 % (3.6) | 8 % (4.2) |
| The Abyss (pitch) | 1 % (0.8) | 0 % (1.3) | 1 % (1.6) | 0 % (1.0) |
The welcoming/moderate/dark/pitch distinction is preserved. Compared with Stage 1.5 (same sites),
values moved within a few points except Ashen Hollows on seed 12345 (24 % → 6 %): new features shift
vanilla decoration random sequences, so that sample window received fewer glowstone/lava features.
The Abyss stays dark (gloomcaps light 4, voidroot 2).

### Performance (this hardware)
| Measurement | Stage 2 | Stage 1.5 |
|---|---|---|
| Terrain fill + surface, single thread | 11.8–12.6 ms/chunk | 11.1–12.6 |
| Full generation incl. decoration, 144 chunks sequential | 22.6–25.8 ms/chunk | 24.2–26.1 |
| Stage 2 random tick (mixed plants, fungi, bush, sapling, crop incl. spreading checks) | 0.79–0.80 µs per call | — |
| Spreading test: 4000 rounds over a 15×15 plot | 243 ms | — |
| Heap after GC with ~8 900 chunks loaded (`-Xmx6G`) | 344–500 MB | 551 MB |
For scale: vanilla performs 3 random ticks per 16³ section per game tick; even if every random tick hit
a Stage 2 plant, ~20 000 random ticks per player per tick (simulation distance 8) would cost ~16 ms —
in practice only a small fraction of random ticks land on plants. Not measured: TPS with 4–6 real
players over a long session.

### Graphical inspection — executed (real client, software OpenGL, seed 42)
Ground-level tours of the census sites of all 15 ecosystems (`tour_stage2.txt`, 23–27 views). Checked:
goldbough domes and goldgrass meadows, mirewillow curtains and marsh ferns in the wetlands, giant
bellcaps/sporestalks/shelfcaps in the Fungal Expanse, glowpads covering a Flooded Depths lake, frostfir
tiers on Everfrost ice, duskoak/spirebark forest in Rootwild, cinderbark and ashgrass by lava, bloodthorn
in the Crimson Basin, ghostferns/gloomcaps in the dark Abyss. No missing textures/models in the client
log. Defects found and fixed: test plots obstructing views (moved away), young cragpines reading as a
"+" shape (wider pads; re-checked in a short re-shoot of Hollow Peaks/Rootwild/Golden Grove on a fresh seed 42
world, `run-tests/s2z_42`: cragpines now show flat layered pads). The final code passed the stage 2
self-test once more in that run (seed 42: 161 passed, 0 failed). Samples: `docs/images/s2_*.jpg`. Not inspected: every plant close-up, item icons in the
inventory screen (item textures were reviewed as an image sheet), shaders (Iris/Sodium not installed here).

### GUI world creation
Not re-run in Stage 2 (world preset unchanged; Stage 1.5 result stands).

### Not tested in Stage 2
Hands-on survival play, multiplayer with real clients, long sessions/TPS under load, shaders
(Sodium/Iris), chunk-order determinism of decoration (terrain unchanged; decoration is known to vary
slightly with generation order, D-017), the exceptional crop's full growth cycle (code path shared with
the tested crops).

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
