# Underdeep

A cooperative underground survival sandbox mod for **Minecraft Java Edition 1.21.1 (Fabric)**.
The whole world is underground: vast interconnected caverns, underground forests, fungal expanses,
flooded depths, volcanic hollows, crystal halls and abysmal depths.

Status: **Stage 2 — living ecosystems: trees, wood sets, plants, fungi, aquatic life, crops, foods, medicines, minerals.** See `docs/PROGRESS.md`.

**Worlds created with Stage 1 (terrain generator v1) must be recreated** — the terrain changed completely.
Stage 1.5 worlds load with Stage 2 (terrain is identical) but already explored chunks contain no Stage 2
plants, trees or minerals; **a fresh world is recommended**. The mod never deletes or modifies existing worlds.

## Requirements
- Java 21
- Minecraft 1.21.1 with Fabric Loader ≥ 0.17.3 and Fabric API 0.116.18+1.21.1 (or newer 1.21.1 build)

## Building
```bash
./gradlew build
```
The mod JAR is written to `build/libs/underdeep-0.3.0-stage2.jar` (`-sources.jar` alongside). Prebuilt copies are committed in `releases/`.

## Playing in singleplayer
1. Install Fabric Loader for 1.21.1 and put Fabric API and the Underdeep JAR in `mods/`.
2. Create a new world → **More World Options… → World Type: Underdeep**.
3. You start in a lit forest cavern at the world origin. Vanilla world types are unaffected.

## Dedicated server
1. Install the Fabric server launcher for 1.21.1; put Fabric API and the Underdeep JAR in `mods/`.
2. In `server.properties` **before the world is first created**:
   ```properties
   level-type=underdeep\:underdeep
   ```
   (Optional: `level-seed=...`.) The colon must be escaped as shown in `.properties` files.
3. Recommended for 4–6 players: `-Xmx8G`, `view-distance=10`, `simulation-distance=8`.
4. Server options live in `config/underdeep.json` (created on first start):
   - `netherPortalsInUnderdeep` (default `false`)
   - `endAccessInUnderdeep` (reserved; no strongholds generate, so the End is unreachable)
   - `diagnosticsPermissionLevel` (default `2`, operators)
   - `spawnSearchRadius` (default `96`)
   - `starterSafeRadius` (default `150`): no natural hostile spawns around the starter cavern
   - `monsterSpawnChanceLow/Medium/High/Extreme` (default `0 / 0.12 / 0.45 / 1.0`): share of natural hostile
     spawn attempts allowed per ecosystem danger class
   - Stage 2 ecology: `naturalSpreading` (true), `spreadRateMultiplier` (1.0; 0 disables), `spreadDensityLimit` (5),
     `sporeReleases` (true), `sporeEffects` (true), `crystalRegrowth` (true), `reactivePlants` (true),
     `ambientParticles` (true; client side, also respects Particles: Minimal)

## Diagnostics (operators)
`/underdeep info | region [pos] | locate <region> | spawncheck | cavern [pos] | sites <type> [radius] |
perf [chunks] | render [radius] | connectivity [radiusChunks] | ecology [pos]`

## Development
- `./gradlew test` — fast unit tests of the pure terrain model (`-Dunderdeep.previews=true` writes PNGs to `build/previews/`).
- `scripts/selftest.sh <seed> <run-dir> [create|reload] [forward|reverse]` — dedicated-server self-test.
- `scripts/visual_tour.sh <world-dir> <tour.txt>` — real client under Xvfb, saves screenshots.
- `scripts/mob_study.sh <run-dir>` — real client joins the dedicated server; measures natural mob populations.
- `scripts/test_matrix.sh` — full verification matrix (seeds, reload, determinism, vanilla, production JAR).
- `SUITE=stage2 scripts/selftest.sh …` / `scripts/stage2_matrix.sh` — targeted Stage 2 tests (ecology, growth, recipes).
- `python3 tools/assets/stage2_assets.py` — regenerate all block/item assets, loot, recipes, tags and names.
- `python3 tools/worldgen/generate_data.py` — regenerate biome/feature JSON after editing the tables.
- Read `CLAUDE.md` and `docs/` before contributing.
