# Underdeep — Next Steps

## Immediate: Stage 2 — Ecosystems, vegetation and regional resources

Do not start until the user assigns it. When assigned:

### Prerequisites (verify first)
1. `./gradlew build` succeeds; `./gradlew test` passes.
2. `scripts/selftest.sh 12345 run-tests/s2check create` → report shows 0 FAIL.
3. Read `docs/ARCHITECTURE.md` §4 (terrain), §6 (contracts) and `tools/worldgen/generate_data.py`.

### Scope suggestions
- Region-specific resources: custom ores/minerals per region (`BlockPalette` rock bands are where
  custom ores should be targeted; vanilla ore features only replace stone/deepslate families).
- Vegetation depth: custom plants/fungi/trees per region (many Stage 1 regions use vanilla features).
  Add sub-biomes where useful (lake shores, deep variants of Crystal Deep / Ashen Hollows) by mapping
  region + local terrain in `UnderdeepBiomeSource#biomeAtBlock` (keep biomes cheap to compute).
- Additional ecosystems toward the 12–15 target: append `RegionType` entries (see ARCHITECTURE §6
  "Extending regions") — bump `UnderdeepTerrain.GENERATOR_VERSION` and record it in DECISIONS.
- Use `RegionEnvironment.habitatTags/resourceTags` as the vocabulary future species will reference.
- Replace remaining placeholder visuals (procedural 16×16 textures in `tools/assets`).

### Known issues / follow-ups from Stage 1 (see TESTING.md "Known issues")
- Vertical connections between layers are only shafts (≈ one per 130–160k m² outside Flooded Depths, which has none). Consider
  additional sloped descents (ramps) so the deep layer is reachable without building.
- Rivers exist only in lowlands below y≈146; most river channels appear in Rootwild/Ashen lowlands.
- `getColumnSample` uses exact noise while the chunk fill interpolates 3D noise; harmless now (no
  structures use it) but structure placement in Stage 9 should sample real chunks or tolerate this.
- Distant views end in a flat fog colour at render distance (vanilla fog); a custom fog curve could
  improve vistas in colossal caverns (Stage 11).
- Ambient light 0.1 is a first guess; tune with players (Stage 11).
- Not yet tested: a second client joining the dedicated server over the network; long sessions.
- The start-resource check's animal count can read 0 because entity sections may not be loaded yet.

## Code locations
- Terrain model: `src/main/java/dev/underdeep/world/terrain/UnderdeepTerrain.java`
- Regions: `world/region/RegionType.java`, `RegionMap.java`
- Generator: `world/gen/UnderdeepChunkGenerator.java`, `SurfacePainter.java`, `BlockPalette.java`
- Worldgen data: `tools/worldgen/generate_data.py` → `src/main/resources/data/underdeep/...`
- Analysis contracts: `world/analysis/*`
- Tests: `src/test/java/.../TerrainModelTest.java`, `debug/SelfTest.java`, `scripts/*.sh`
