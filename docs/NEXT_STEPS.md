# Underdeep — Next Steps

## Immediate: user inspection of Stage 4, then Stage 5

Do not start Stage 5 until the user assigns it. The user will first play/inspect Stage 4 in a **new world**
(settlements generate only in worlds created with terrain generator v3).

### Prerequisites (verify first)
1. `./gradlew build` succeeds (unit tests included).
2. `SUITE=stage4 scripts/selftest.sh 42 run-tests/check create` → 0 FAIL (quick); `SUITE=stage2 …` for everything.
3. Read `docs/ARCHITECTURE.md` §5e (civilizations), §6 (contracts); `civ/*`; private `docs/private/CIVILIZATIONS.md`
   (spoilers — never quote publicly).

### Stage 5 hooks prepared by Stage 4 (individual NPC intelligence)
- `SettlementRecord.InhabitantRecord.memory` (NBT compound per inhabitant) for relationships and memories.
- `InhabitantManager.onTalk` for conversations (currently a greeting line).
- Routine scaffold in `InhabitantEntity.ScheduleGoal` / `activity()` to replace with personal schedules.
- Inhabitant identities (name, appearance, home, workplace) are stable across sessions.

### Stage 6 hooks (world simulation)
- Unembodied inhabitants are pure records: abstract production/consumption can run on them.
- Settlement records count deaths by players (reputation), trades per inhabitant (demand).
- Plans expose districts, storehouses, gates and docks (caravan endpoints); cavern-graph tunnels are routes.

### Known issues / follow-ups from Stage 4
- Architecture is a procedural grammar (D-040): recognizable per culture and furnished, but simpler than
  hand-authored builds by expert builders; several kinds share layouts across cultures. Worth an authored pass
  on landmarks and the most common houses.
- Roads are straight segments with grade-limited height profiles; very rough caverns still produce steep
  cuttings and some long foundations. Cities on extremely uneven floors end up smaller than planned
  (plot rejections are reported by the self-test).
- Buildings are axis-aligned; along diagonal roads frontages are stepped.
- Inhabitants walk to their spots with vanilla navigation; complex terrain can leave individuals stuck (they
  quietly reposition when no player is within 24 blocks). Only the 24 nearest per settlement are embodied.
- Basic trading uses emeralds (D-045); regional prices and currencies are Stage 6.
- Boats at docks are only spawned when the mooring cell is water.
- Creature textures were regenerated once when the Stage 3 asset script was made deterministic (same
  palettes and models, different noise patterns).
- Stage 3 known issues still apply (ridden movement untested by a real player, etc.).

## Code locations
- Civilizations: `src/main/java/dev/underdeep/civ/` (`arch/` designs, `settlement/` placement and planning, `npc/` inhabitants)
- Client inhabitants: `src/client/java/dev/underdeep/client/civ/`
- Wildlife: `src/main/java/dev/underdeep/wildlife/`
- Ecology data: `src/main/java/dev/underdeep/ecology/`; plants `block/plant/`; registrations `registry/*`
- Terrain model: `world/terrain/*`; regions `world/region/*`; generator `world/gen/*`
- Assets: `tools/assets/stage2_assets.py` (+ `art.py`, `creatures.py`, `inhabitants.py`); worldgen data `tools/worldgen/generate_data.py`
- Tests: `src/test/java/...`, `debug/SelfTest`, `Stage15Checks`, `Stage2Checks`, `Stage3Checks`, `Stage4Checks`,
  `ArchitectureGallery`, `WildlifeZoo`, `MobStudy`, `scripts/stage3_matrix.sh`, `scripts/test_matrix.sh`
