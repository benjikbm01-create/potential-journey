# Underdeep — Next Steps

## Immediate: user inspection of Stage 3, then Stage 4

Do not start Stage 4 until the user assigns it. The user will first play/inspect Stage 3.

### Prerequisites (verify first)
1. `./gradlew build` succeeds (unit tests included).
2. `SUITE=stage2 scripts/selftest.sh 42 run-tests/check create` → report shows 0 FAIL (Stage 2 + Stage 3 sections).
3. Read `docs/ARCHITECTURE.md` §5c (ecosystems), §5d (wildlife), §6 (contracts); `wildlife/*`,
   `wildlife/population/*`, `tools/assets/creatures.py`.

### Stage 4 hooks prepared by Stage 3 (Civilizations and settlement architecture)
- Settlement placement can weigh game availability: `WildlifeManager.get(world).region(id)` → counts
  and `disturbance` per region, without loading chunks.
- Livestock: villagers can own animals through `UnderdeepCreature.leaveWildAsTamed(owner)` (non-wild
  animals breed freely and are saved). Pack animals (`Mount.PACK`) suit caravans (Stage 6).
- Hunting by NPCs should go through the normal death path so `RegionWildlife` counts and disturbance
  stay correct.
- Replace the remaining vanilla monsters (still at `vanillaMonsterShare` = 0.6 of the Stage 1.5 rate)
  with ecosystem dangers or civilisation threats.

### Known issues / follow-ups from Stage 3
- Ridden-mount movement and the mount controls were not exercised by a real player (server-side tests
  cover harness, ownership and control assignment; movement is client-side like vanilla horses).
- Creature animations were inspected only as static poses in screenshots (frozen exhibits); motion was
  not visually inspected.
- Sounds are pitched vanilla sounds (`wildlife/Voices`); custom sounds belong to Stage 11.
- Loaded herds do not perform long seasonal migrations; migration happens in the aggregate model and by
  animals settling in a neighbouring region (home-region transfer).
- Rare/exceptional creatures have their distinct size, stats and resources but no unique set-piece
  behaviour yet (see private design material for the planned follow-ups).
- Vanilla animals already saved in Stage 2 worlds' explored chunks remain.
- Stage 2 known issues still apply (procedural art, vanilla stand-in vegetation, Stage 1.5 terrain notes).

## Code locations
- Wildlife: `src/main/java/dev/underdeep/wildlife/` (species, entity, goals in `ai/`, populations in `population/`)
- Client creature rendering: `src/client/java/dev/underdeep/client/creature/`
- Ecology data: `src/main/java/dev/underdeep/ecology/`
- Plant framework: `block/plant/`; registrations: `registry/ModWood`, `WoodSet`, `ModPlants`, `ModItems`, `ModTags`
- Trees & big organisms: `world/feature/UnderdeepTreeFeature`, `GiantFungusFeature`, `FallenLogFeature`, `CoralClusterFeature`, `PlantBuilder`
- Terrain model: `world/terrain/*`; regions `world/region/*`; generator `world/gen/*`
- Assets: `tools/assets/stage2_assets.py` + `art.py` + `creatures.py` (runs `generate_assets.py` first); worldgen data `tools/worldgen/generate_data.py`
- Tests: `src/test/java/...`, `debug/SelfTest`, `Stage15Checks`, `Stage2Checks`, `Stage3Checks`, `WildlifeZoo`, `MobStudy`,
  `scripts/stage2_matrix.sh`, `scripts/test_matrix.sh`
