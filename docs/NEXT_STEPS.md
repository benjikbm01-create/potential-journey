# Underdeep — Next Steps

## Immediate: user inspection of Stage 2, then Stage 3

Do not start Stage 3 until the user assigns it. The user will first play/inspect Stage 2.

### Prerequisites (verify first)
1. `./gradlew build` succeeds (unit tests included).
2. `SUITE=stage2 scripts/selftest.sh 12345 run-tests/check create` → report shows 0 FAIL.
3. Read `docs/ARCHITECTURE.md` §5c (living ecosystems + Stage 3 integration), §6 (contracts),
   `ecology/*`, `block/plant/*`, `tools/assets/stage2_assets.py`, `tools/worldgen/generate_data.py` (Stage 2 block).

### Stage 3 scope suggestions (Wildlife and ecological simulation)
- Replace `InitialPopulation` vanilla herds with Underdeep creatures chosen from
  `RegionEcology.wildlife()` guilds per region; abstract populations per `RegionId` in `regionData`.
- Creature goals use `Habitats.findNearest` with `Species.Role` (food, nests, shelter, light).
- Herbivores eating bushes should use the harvest path (age reset) so plants regrow, and record
  pressure in `EcologyState`.
- Replace vanilla hostile mobs per danger class with ecosystem predators.

### Known issues / follow-ups from Stage 2
- Procedural textures: an art pass could replace individual PNGs (no code changes needed).
- Vanilla stand-ins remain in some ecosystems (huge vanilla mushrooms, crimson fungi, mangroves, vanilla
  flowers/berries) alongside native species; thin them further if desired.
- Ambient particles use vanilla particle types; custom pollen/leaf particles and sounds are Stage 11.
- Existing Stage 1.5 worlds: old chunks have no Stage 2 flora (terrain matches, decoration differs);
  a fresh world is recommended.
- Stage 1.5 known issues still apply (Sunken Gardens air pockets, shaft-only layer links).

## Code locations
- Ecology data: `src/main/java/dev/underdeep/ecology/`
- Plant framework: `block/plant/`; registrations: `registry/ModWood`, `WoodSet`, `ModPlants`, `ModItems`, `ModTags`
- Trees & big organisms: `world/feature/UnderdeepTreeFeature`, `GiantFungusFeature`, `FallenLogFeature`, `CoralClusterFeature`, `PlantBuilder`
- Terrain model: `world/terrain/*`; regions `world/region/*`; generator `world/gen/*`
- Assets: `tools/assets/stage2_assets.py` + `art.py` (runs `generate_assets.py` first); worldgen data `tools/worldgen/generate_data.py`
- Tests: `src/test/java/...`, `debug/SelfTest`, `Stage15Checks`, `Stage2Checks`, `scripts/stage2_matrix.sh`, `scripts/test_matrix.sh`
