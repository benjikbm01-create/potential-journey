# Underdeep — Next Steps

## Immediate: user play-testing of Stage 5, then Stage 6

Do not start Stage 6 until the user assigns it. Stage 5 needs no new world (existing Stage 4 settlement
records are migrated), but settlements themselves still only exist in worlds created with generator v3.

### Prerequisites (verify first)
1. `./gradlew build` succeeds (unit tests included).
2. `SUITE=stage4 scripts/selftest.sh 42 runs/check create` → 0 FAIL (quick, includes Stage 5 checks).
3. Read `docs/NPC_MINDS.md`, `docs/ARCHITECTURE.md` §5e–5f and §6; private `docs/private/` notes (spoilers).

### Manual test checklist for Stage 5 (in a v3 world)
- Walk into a settlement by day and evening: people work, eat, chat (speech bubbles), visit, gather.
- Right-click an inhabitant: dialogue screen; try every option; sneak + right-click trades directly.
- Give liked/disliked items; check the journal (sneak-use cycles wildlife → people pages).
- `/underdeep npc befriend` near someone (operator testing aid), then ask them to travel with you;
  try `/companion` commands, the pack, stairs/shafts (relocation), a fight, logging out and back in.
- `/underdeep npc`, `npc relationships`, `npc memories`, `npc stats`, `npc advance 12` to inspect minds.

### Stage 6 hooks prepared by Stage 5
- Possessions and companion packs are real inventories; goal types SAVE_UP / IMPROVE_HOME / FIND_SAFER_WORK are
  reserved; knowledge items can travel between settlements (caravans); per-player bonds are reputation for prices.
- `Brain.Context` flags are the place to add new facilities (stocked shops, construction sites).

### Known issues / limitations of Stage 5
- Not hands-on tested: dialogue UI, gifts, companions, bubbles (see TESTING.md).
- Dialogue is English text generated on the server (not localised); optional free-text input not implemented.
- Inhabitants do not physically open containers, use workstations' GUIs or carry visible items; food is
  abstract (eating restores hunger; possessions are consumed only when shared or eaten by companions).
- Companions use bare hands (4 damage) unless they are guards; they do not equip pack items.
- Encounters between embodied inhabitants need them to be within 4.5 blocks; abstract encounters pair people at
  the same kind of place.
- Families are created only for settlements first visited after Stage 5; migrated Stage 4 settlements have
  households but no kin bonds.
- Stage 4 known issues still apply (procedural architecture, straight roads, stuck pathing in rough terrain).
