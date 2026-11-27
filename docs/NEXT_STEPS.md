# Underdeep — Next Steps

## Immediate: user play-testing of Stage 8, then Stage 9

Do not start Stage 9 until the user assigns it. Stage 8 needs no new world (generator still v3): all items, blocks,
stations, the Handbook and advancements work in existing worlds; existing players get the Handbook and quiet
credit for earlier accomplishments on first join. (Only freshly generated chunks get the starlily placement fix.)

### Prerequisites (verify first)
1. `./gradlew build` succeeds (43 unit tests).
2. `SUITE=audit scripts/selftest.sh 42 runs/a create` → 0 FAIL (~1.5 min); `SUITE=stage8 …` for the full set.
3. Read `docs/EQUIPMENT.md`, `docs/RESOURCES.md`, `docs/HANDBOOK.md`, then `docs/ARCHITECTURE.md` §5i.

### Manual test checklist
- Handbook: open with H and with the item; search ("steel", "cold", "taming"); open an item page and check How to
  get / recipes / Used to make; follow see-also links, Back, Mark/Saved; region pages unlocking as you explore.
- Advancements: open the advancement screen, check the 13 tabs; harvest a crop, cook a dish, discover a settlement
  and watch the toasts. In a Stage 7 world: join and check there is no toast spam but earlier progress is credited.
- Metallurgy: alloy furnace (2 iron + coal → steel), ore crusher, forge (embersteel/rimesteel gear), gem cutter,
  leatherworker's bench; hoppers on these stations.
- Weapons: dagger vs greatsword vs spear vs warhammer in real fights; bows (draw time), special arrows.
- Armour: rimesteel or fur in the Everfrost, galvanic in Stormstone, copper in Stormstone (worse!), nacre diving.
- Tools: hammer 3×3 (sneak = one block), lumber axe on a tree, sickle on crops, prospector's pick, embersteel
  pickaxe smelting, malachite durability.
- Exploration: grappling hook, cave glider (no wings drawn yet), climbing claws, rope coil, depth gauge, delver's
  compass + waymarkers, backpack/pouch/satchel, miner's lamp.
- Accessories (K): rings/amulet/charm, effects on and off, death keeps them.
- Artisan's bench: apply two upgrades, try a third (rejected), repair with material and with a repair kit, salvage.
- Blacksmith repair dialogue; companion wearing armour from the companion pack; smiths selling regional gear.

### Stage 9 hooks
See `docs/EQUIPMENT.md` §11 and `docs/HANDBOOK.md` §3 (combat may read weapon specials/upgrades; new hazards add a
protection group; every new item must pass the audit and get Handbook pages and advancements).

### Known issues / limitations of Stage 8
- Not hands-on tested: every Stage 8 screen and interaction in a real client (see TESTING.md).
- The cave glider renders without wings; textures are procedural placeholders (Stage 11 art backlog in
  `docs/EQUIPMENT.md` §12).
- Breaking rope ladder rungs drops nothing (the rope is consumed).
- Area tools ignore the 3×3 extension when sneaking (by design) and fell at most 48 logs.
- Prospector's pick reports direction and counts only (no exact positions, by design).
- Handbook bookmarks are per client, not per world.
- Advancement balance (thresholds) was not play-tested.
- Earlier known issues of Stages 4–7 still apply (creative-mode crate loss, no spoilage, …).
