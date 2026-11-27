# Underdeep — Next Steps

## Immediate: user play-testing of Stage 7, then Stage 8

Do not start Stage 8 until the user assigns it. Stage 7 needs no new world (no terrain change; generator still v3):
all items, blocks and stations work in existing worlds, hazards and regional fishing in Underdeep worlds.

### Prerequisites (verify first)
1. `./gradlew build` succeeds (unit tests included).
2. `SUITE=stage4 scripts/selftest.sh 42 runs/check create` → 0 FAIL (includes the Stage 5, 6 and 7 checks).
3. Read `docs/SURVIVAL.md`, then `docs/ECONOMY.md` §13 and `docs/ARCHITECTURE.md` §5h.

### Manual test checklist
- Cooking: campfire + cooking pot on top; put 2 puffcaps + a vegetable + bowls in; watch the arrow, steam and the
  known-recipes panel (click a recipe: + / − ingredients). Try the oven with fuel, the kettle, the crock (slow),
  the preparation table (flour → dough with a water bucket; the bucket comes back), the drying rack.
- Hoppers into/out of a station; comparator on a station.
- Eat three food groups → Well Fed (tooltip shows groups); eat a warming dish then a cooling one (they cancel).
- Ask a cook "Could you teach me one of your dishes?" (after talking to them a couple of times).
- Farming: rich soil, compost bin (fill with crops), compost on a crop, right-click a mature crop, herb cuttings.
- Feeding trough with feed next to tamed animals; fish with bait in the off hand in different regions; a fish
  trap with bait in water.
- Hazards: Everfrost without warm clothes, then by a campfire; Stormstone with an iron pickaxe; Ashen Hollows.
  Journal (sneak-use for pages) → survival notes. `/underdeep survival hazards` shows exposure.
- Bedroll at night (spawn must not move; sneak-use sets it), chairs/bench, pantry/medicine-cabinet filters,
  supply crate keeps contents, comfort message on waking, inn meal/bed, healer treatment.

### Stage 8 hooks
See `docs/SURVIVAL.md` §13 (reserved materials, hazard protection contracts, data formats).

### Known issues / limitations of Stage 7
- Not hands-on tested: every screen and interaction in a real client (station screen layout, drying-rack item
  rendering, sitting, bedroll sleeping and wake position, fishing rod catches, dialogue services); see TESTING.md.
- Spoilage is not implemented (by design, flag reserved). Station "filled" visuals are particles only.
- Creative-mode breaking of a supply crate loses its contents (survival breaking keeps them).
- Breeding at troughs uses vanilla love mode; it was not observed producing young in the self-test.
- Hazard checks use the region at the player's position; region borders can switch hazards abruptly.
- Earlier known issues of Stages 4–6 still apply.
