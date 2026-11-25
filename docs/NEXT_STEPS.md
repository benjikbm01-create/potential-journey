# Underdeep — Next Steps

## Immediate: user inspection of Stage 1.5, then Stage 2

Do not start Stage 2 until the user assigns it. The user will first inspect the Stage 1.5 world manually.

### Prerequisites (verify first)
1. `./gradlew build` succeeds (unit tests included).
2. `scripts/selftest.sh 12345 run-tests/check create` → report shows 0 FAIL.
3. Read `docs/ARCHITECTURE.md` §4 (terrain, regions v2, landmarks), §5a/5b (spawning, lighting), §6 (contracts),
   `tools/worldgen/generate_data.py` and `tools/assets/generate_assets.py`.

### Stage 2 scope suggestions (Ecosystems, vegetation and regional resources)
- Custom flora per ecosystem replacing remaining vanilla stand-ins (fungal identity, crimson identity, frost flora).
- Region-specific resources (ores/minerals per ecosystem; stormstone, amber, crystals as materials with uses).
- Proper art for the 21 custom blocks (current textures are procedural placeholders).
- Sub-biome registrations only where gameplay needs them (e.g. deep variants), mapped in `UnderdeepBiomeSource`.
- Use `RegionEnvironment` habitat/resource tags and `Danger`/`Lighting` as the vocabulary for later species.

### Known issues / follow-ups from Stage 1.5 (see TESTING.md)
- Lighting in bright ecosystems: see TESTING.md for the measured fraction of ground at block light ≥ 8; pools
  between glowing patches remain darker by design.
- Sunken Gardens have no underwater air pockets yet.
- Inter-layer connections remain shafts only.
- `getColumnSample` uses exact noise while chunk fill interpolates 3D noise (harmless until structures).
- Decoration (not terrain) differs slightly between chunk-generation orders (vanilla-like behaviour).
- Vanilla hostile mobs are placeholders until Stage 3 ecosystem creatures.
- Existing Stage 1 worlds (generator v1) must be regenerated; v2 terrain does not match them.

## Code locations
- Terrain model: `src/main/java/dev/underdeep/world/terrain/UnderdeepTerrain.java`, `Landmarks.java`, `CavernGraph.java`
- Regions: `world/region/RegionType.java`, `RegionMap.java`, `Danger.java`, `Lighting.java`
- Generator: `world/gen/UnderdeepChunkGenerator.java`, `SurfacePainter.java`, `BlockPalette.java`
- Spawning: `world/spawn/SpawnRules.java`, `config/UnderdeepConfig.java`
- Worldgen data: `tools/worldgen/generate_data.py`; block assets: `tools/assets/generate_assets.py`
- Analysis: `world/analysis/*` (RegionDistribution, Accessibility, SiteAnalyzer, ConnectivityAnalyzer)
- Tests: `src/test/java/...`, `debug/SelfTest.java`, `debug/Stage15Checks.java`, `debug/MobStudy.java`, `scripts/*.sh`
