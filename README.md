# Underdeep

A cooperative underground survival sandbox mod for **Minecraft Java Edition 1.21.1 (Fabric)**.
The whole world is underground: vast interconnected caverns, underground forests, fungal expanses,
flooded depths, volcanic hollows, crystal halls and abysmal depths.

Status: **Stage 4 — civilizations, settlements and architecture** (on top of Stages 1–3). See `docs/PROGRESS.md`.

**Worlds created with Stage 1 (terrain generator v1) must be recreated** — the terrain changed completely.
Stage 1.5 worlds load with Stage 2 (terrain is identical) but already explored chunks contain no Stage 2
plants, trees or minerals; **a fresh world is recommended**. Stage 2 worlds load with Stage 3 unchanged (same
blocks); native wildlife appears around players everywhere, but vanilla animals already saved in explored
chunks stay. **Settlements (Stage 4) only generate in worlds created with Stage 4 (terrain generator v3):
create a new world to meet the civilizations.** Older worlds keep generating without settlements unless a
server sets `settlementsInExistingWorlds` (they would then appear only in unexplored chunks). The mod never
deletes or modifies existing worlds.

## Requirements
- Java 21
- Minecraft 1.21.1 with Fabric Loader ≥ 0.17.3 and Fabric API 0.116.18+1.21.1 (or newer 1.21.1 build)

## Building
```bash
./gradlew build
```
The mod JAR is written to `build/libs/underdeep-0.5.0-stage4.jar` (`-sources.jar` alongside). Prebuilt copies are committed in `releases/`.

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
   - Stage 3 wildlife: `wildlifeEnabled` (true), `wildlifeDensity` (1.0), `wildlifeDensityByEcosystem` (per-ecosystem
     multipliers), `predatorAbundance` (1.0), `wildlifeReproduction` (1.0), `wildlifeMigration` (true),
     `wildlifeSimInterval` (1200 ticks), `wildlifeSimRegionsPerUpdate` (24), `maxWildlifePerPlayer` (28),
     `maxWildlifeTotal` (160), `wildlifeDetailDistance` (48), `wildlifeDespawnDistance` (96),
     `wildlifeVegetationConsumption` (1.0), `rareCreatureFrequency` (1.0), `tamingEnabled` (true),
     `vanillaMobReplacement` (true), `vanillaMonsterShare` (0.6), `huntingDisturbance` (1.0)
   - Stage 4 civilizations: `settlementsEnabled` (true), `settlementsInExistingWorlds` (false),
     `maxActiveInhabitantsPerSettlement` (24), `maxActiveInhabitantsTotal` (96), `inhabitantActivationDistance` (64),
     `settlementMonsterExclusion` (24)

## Diagnostics (operators)
`/underdeep info | region [pos] | locate <region> | spawncheck | cavern [pos] | sites <type> [radius] |
perf [chunks] | render [radius] | connectivity [radiusChunks] | ecology [pos] | settlements [radius] | settlement |
gallery confirm` (the gallery builds every building design next to you — use a test world)

## Development
- `./gradlew test` — fast unit tests of the pure terrain model (`-Dunderdeep.previews=true` writes PNGs to `build/previews/`).
- `scripts/selftest.sh <seed> <run-dir> [create|reload] [forward|reverse]` — dedicated-server self-test.
- `scripts/visual_tour.sh <world-dir> <tour.txt>` — real client under Xvfb, saves screenshots.
- `scripts/mob_study.sh <run-dir>` — real client joins the dedicated server; measures natural mob populations.
- `scripts/test_matrix.sh` — full verification matrix (seeds, reload, determinism, vanilla, production JAR).
- `SUITE=stage2 scripts/selftest.sh …` / `scripts/stage2_matrix.sh` — targeted Stage 2+3 tests (ecology, growth, recipes,
  wildlife behaviour, taming, populations, persistence). `ZOO=true` also builds a close-up exhibit of every creature
  for `scripts/visual_tour.sh`.
- `SUITE=stage4 scripts/selftest.sh …` — quick settlement/inhabitant suite; `GALLERY=true` also builds the architecture
  gallery (tour in `<dir>/selftest/tour_gallery.txt`, settlement views in `tour_settlements.txt`).
- `python3 tools/assets/stage2_assets.py` — regenerate all block/item/creature assets, loot, recipes, tags and names
  (creatures come from `tools/assets/creatures.py`, inhabitant skins from `tools/assets/inhabitants.py`).
- `python3 tools/worldgen/generate_data.py` — regenerate biome/feature JSON after editing the tables.
- Read `CLAUDE.md` and `docs/` before contributing.
