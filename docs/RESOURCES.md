# Underdeep — Resource obtainability audit (Stage 8)

The world is entirely underground, so vanilla's surface routes (villages, desert temples, the End, …) do not exist.
Stage 8 adds a machine-checked audit proving that every survival material can actually be obtained, has a use, and
does not depend on itself.

## 1. How the audit works (`guide/Obtainability`)

A fixpoint over **rules**, each saying "with these items you can obtain that item":

| Rule kind | Source of truth |
|---|---|
| mine / harvest | in-world census of natural blocks (`data/underdeep/guide/natural_blocks.json`, 320 blocks over 15 regions, from 7×7 chunks per region) + blocks declared by worldgen (`EcologyRegistry`) + real loot tables (`Block.getDroppedStacks` with every tool tier) |
| planting / stripping / special | planting BlockItems, axe stripping (`AxeItem.STRIPPED_BLOCKS`), obsidian from water + lava, nests |
| creatures | biome spawn entries + `WildlifeRegistry` species; real loot simulation; shed, shear, milk products; creature eggs |
| fishing / traps / compost | regional fishing tables, vanilla fishing loot, fish trap, compost bin and composter |
| recipes | every server recipe (crafting — a crafting table gates >4-input recipes —, smelting, blasting, smoking, campfire, stonecutting, smithing) |
| stations | Stage 7 + Stage 8 station recipes (gated by the station item; heat/fuel inputs) |
| trade | settlement goods (gated by emeralds, which are mined) |

The result for every item: reachable or not, **depth** (number of processing steps from the natural world),
sources, uses and what it is used in. An item reachable only through itself is never reachable (no circular
dependencies). The same data drives the Handbook's "How to get" / "Used to make" sections, so documentation can
never disagree with the game.

## 2. Results (seed 42, `SUITE=audit`, 2026-11-27)

- All **666** Underdeep items are reachable and every ordinary one has at least one use.
- All **64** essential vanilla materials (tools, torches, furnace, enchanting table, anvil, redstone, bows, books,
  glass, wool, netherite route, …) are reachable underground.
- 189 items have a natural (mined/harvested) source; 3 rare plants are only declared by worldgen (not in the census
  sample); worldgen places them, but they are rare enough to be missed by the sampled chunks.
- Gaps found and fixed in Stage 8: stripped logs and hanging signs (strip rule), rare plants grown from their own seeds or cuttings (planting rule),
  obsidian (special rule), the never-generating starlily (placement fixed), blaze rod / nether wart / name tag /
  honey bottle / netherite upgrade template (underground alternative recipes from regional materials).
- **Outside the premise by design** (no underground route): saddle, elytra, turtle helmet/scute, trident, nautilus
  shell, heart of the sea, bottle o' enchanting, shulker shell, phantom membrane, dragon's breath, chorus fruit.
  Underdeep gear replaces their roles (riding needs no saddle; cave glider; nacre diving set; backpacks).
- Deferred secret items: listed privately (`docs/private/`), 0 at the end of Stage 8.

The self-test writes the full report to `<run>/selftest/obtainability.md` and `.json` (one row per item).

## 3. Visibility (`guide/Sensitivity`, `data/underdeep/guide/visibility.json`)
- `technical` items (spawn eggs, internal blocks) never appear in the Handbook.
- `sensitive` items get Handbook pages only after the player has obtained them.
- `deferred` items (future secrets) are skipped by the audit's "must be reachable" check and never shown.

## 4. Rules for future stages
Every new item must: be reachable in the audit (or be explicitly `deferred`/`technical`), have a use (or an entry in
`guide/Uses`), and not make a vanilla essential unreachable. Run `SUITE=audit scripts/selftest.sh <seed> <dir>`
(about 1.5 minutes) after adding content. If new natural blocks are added, regenerate the census (see
`docs/TESTING.md`).
