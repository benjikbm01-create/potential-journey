# Underdeep — Equipment, crafting and material progression (Stage 8)

Stage 8 turns the Stage 2–7 resources into equipment with real trade-offs. Package `dev.underdeep.equipment`;
content data `data/underdeep/equipment/content.json`, generated (with models, textures, lang, tags and recipes) by
`tools/assets/stage8.py` + `tools/assets/stage8_art.py`, run through `tools/assets/stage2_assets.py` with
`PYTHONHASHSEED=0`. Station recipes for the new stations are part of the same data and are loaded by the Stage 7
station engine (`SurvivalContent.readRecipes`). Nothing here changes terrain (generator stays v3).

Spoiler note: a few discovery-sensitive items exist; they are listed only in `docs/private/`.

## 1. Content in numbers

| Kind | Count | Notes |
|---|---|---|
| Material families | 17 | 13 new (copper, steel, embersteel, rimesteel, galvanic, starsilver, umbralite, nacre, fossil, malachite, fur, scale, shell, silk, glimmer) + iron and diamond extended with new weapon families |
| Melee weapons | 41 | 7 families: sword, dagger, greatsword, battleaxe, spear, warhammer, mace (+ cutlass, harpoon, glaive, club, maul, stiletto variants) |
| Bows / arrows | 3 / 4 | short (fast, weak), long (slow, strong), horn recurve (durable); heavy, frost, venom, ember arrows |
| Tools | 26 | 20 material tools + 6 specialised (2 hammers, excavator, lumber axe, sickle, prospector's pick) |
| Utility gear | 8 + 4 blocks | grappling hook, cave glider, depth gauge, delver's compass, backpack, herb pouch, ore satchel, repair kit; rope coil/rope ladder, waymarker, glimmer lantern |
| Armour sets | 13 (52 pieces) | metal, diving, cold, spore/fume, explorer and cheap sets |
| Accessories | 27 | 11 rings, 6 amulets, 10 charms; 4 slots (ring, ring, amulet, charm) |
| Upgrades | 34 | applied with kits at the artisan's bench; 2 slots per item, one per group |
| Components | 22 | alloys, crushed ores, cut gems, linings, plates, straps, cloth, grips |
| New stations | 6 | ore crusher, alloy furnace, forge, gem cutter, leatherworker's bench, artisan's bench |
| Processing / forging recipes | 160 | crush 10, alloy 7, gem 6, leather 12, forge 125 |

## 2. Material progression (no circular dependencies)

The obtainability audit (`docs/RESOURCES.md`) proves every item is reachable from the natural world. The intended
path is:

1. **Stone / copper** (copper ore is common in the upper layers): copper tools mine iron but not diamond.
2. **Iron** (vanilla) and **steel** (alloy furnace: 2 iron + coal/charcoal → 2 steel). Steel is the structural
   metal: tougher and longer-lasting than iron, used by most specialised tools and kits.
3. **Regional alloys** (alloy furnace, steel + a regional mineral): embersteel (Ashen Hollows emberite, heat),
   rimesteel (Everfrost cryolite, cold), galvanic (copper + Stormstone galvanite, static, very fast).
   Steel mines emberite-tier ores; embersteel/rimesteel are diamond tier.
4. **Rare metals**: starsilver (light, best enchantability, radiant), umbralite (netherite tier, heaviest).
5. **Biological materials** (leatherworker's bench): fur lining, scale plate, shell plate, silk cloth, horn grip,
   leather strap, rope — from shed/sheared/hunted wildlife products (Stage 3) and plants.
6. **Gems** (gem cutter): cut opal, polished jasper/malachite/pearl, cut cryolite, glimmer lens — used for inlays,
   rings and amulets.

The **ore crusher** turns 2 raw ore into 3 crushed ore (smelts like raw ore), so processing pays back +50 %.

## 3. Weapons

| Family | Damage (vs sword) | Speed | Reach | Knockback | Durability | Role |
|---|---|---|---|---|---|---|
| sword | base | 1.6 | — | — | ×1.0 | all-rounder, sweeping |
| dagger | −1.5 | 2.4 | −0.25 | — | ×0.8 | many quick hits |
| greatsword | +2 | 0.9 | +0.5 | +0.3 | ×1.2 | slow, heavy, sweeping |
| battleaxe | +2.5 | 0.9 | — | +0.2 | ×1.1 | disables shields (vanilla axe rule) |
| spear | +0.5 | 1.3 | +1.0 | — | ×0.9 | keeps enemies at distance |
| warhammer | +3 | 0.75 | +0.25 | +1.0 | ×1.3 | crushing, huge knockback |
| mace | +1.5 | 1.1 | — | +0.5 | ×1.2 | blunt middle ground |

Material specials (`EquipmentEffects.bonusDamage/onHit`): embersteel sets targets alight, rimesteel slows,
galvanic shocks wet targets (+damage in water/rain), starsilver +3 vs undead, umbralite pierces armour (bonus
scaled by the target's armour), nacre +damage in water, glimmer marks targets (glowing). Coatings and inlays add
poison/fire/slow/glow. Bows: draw time and power are per bow (`EquipBowItem`), upgrades change them.

## 4. Armour

Armour is the vanilla `ArmorMaterial` system (real defence/toughness/knockback resistance/enchantability) plus
hazard protection that the Stage 7 hazard meters read (`EquipmentEffects.protection(player, group)`):

| Set | Strength | Hazard protection (per piece) | Extras |
|---|---|---|---|
| copper | 10 | — (makes static **worse**: −15 % per piece) | cheap |
| steel | 15, toughness 1 | — | durable |
| embersteel | 16 | heat 20 % | burn time −15 % |
| rimesteel | 16 | cold 20 % | |
| galvanic | 13 | static 25 % | |
| starsilver | 13 | — | +2 % speed per piece, best enchanting |
| umbralite | 20, toughness 3, KB 0.1 | — | strongest |
| nacre (diving) | 13 | pressure 25 % | helmet: +2 breath, underwater mining; legs/boots: swim speed |
| fossil | 13, KB 0.1 | — | cheap, heavy |
| fur (furlined) | 7 | cold 25 % | |
| scale | 10 | pressure 10 % | swim speed |
| shell (shellplate) | 13 | spores 20 %, fumes 20 % | |
| silk (silkweave) | 7 | — | +3 % speed per piece; boots: +3 safe fall |

Protection from all sources adds up and is capped at 90 %; negative protection (copper) is floored at −60 %.

## 5. Tools and exploration gear

- Material tools follow vanilla tiers via `ToolMaterial` (`getInverseTag`): copper = stone tier, steel/galvanic/
  malachite/nacre = iron tier (steel also mines emberite-tier ores), embersteel/rimesteel/starsilver = diamond,
  umbralite = netherite. Malachite tools are very durable; galvanic very fast; the embersteel pickaxe smelts what
  it mines (`BlockDropsMixin`); the nacre pickaxe mines quickly underwater.
- **Hammer / excavator**: mine a 3×3 face (stone / soil), each extra block broken through the player's own
  `tryBreakBlock` (so protection, claims and drops behave normally); sneak mines a single block.
- **Lumber axe**: fells a connected natural tree (logs with leaves nearby), up to 48 logs; sneak for one log.
- **Sickle**: harvests and replants mature crops in a 3×3 area; cuts plants/leaves.
- **Prospector's pick**: right-click to sense ores within 8 blocks: kinds, counts and the general direction (no x-ray).
- **Grappling hook**: pulls the player to a block up to 16 blocks away; no fall damage for the swing; cooldown.
- **Cave glider**: chest slot, glides like an elytra, cannot be rocket-boosted. (Renders without wings — Stage 11.)
- **Depth gauge / delver's compass / waymarker**: depth, region and nearest known settlement; the compass points to
  a waymarker, bed or known settlement (right-click cycles).
- **Rope coil / rope ladder**: places up to 8 ladder rungs downward from where it is used.
- **Glimmer lantern**: bright light block.
- **Backpack** (18 slots), **herb pouch** and **ore satchel** (9 filtered slots): stored in the item
  (`ContainerComponent`); packs cannot contain packs or shulker boxes; the open pack's slot is locked while open.
- **Diving**: nacre set + pearl ring + nacre amulet + sealed seams; **climbing**: climbing claws (charm) and rope;
  **mobility**: silk set, stride/feather/runner's charms, glider; **lighting**: miner's lamp, lamp mount, lantern.

## 6. Accessories

Opened with the **K** key (rebindable) or by using an accessory item (it equips into the first fitting slot).
Slots: ring, ring, amulet, charm. Accessories are stored in a per-player attachment (`Accessories.Data`, persistent
and **kept on death**, so a death in lava or a chasm never loses them). Effects are applied as temporary attribute modifiers with stable ids
(`underdeep:accessory.<slot>.<n>`), removed and re-added on every change so they can never accumulate; hazard
protection and flags (lamp, climb, runner, healer, trader, forager, wayfinder, angler) are read where used.
Inhabitant traders give the trader's token holder a 5 % better price (cap 17 % total discount with relationships).

## 7. Upgrades, repair and salvage

- **Kits** (34, one per upgrade) are crafted from components and applied at the **artisan's bench** (item + kit →
  result). An item has **2 upgrade slots**; at most one upgrade per group (edge, grip, coating, head, brace, lining,
  plating, gem, string, limbs); upgrades only fit their item category. Rejections show the reason.
- Upgrades are a data component (`underdeep:upgrades`, list of ids). Derived attributes and max durability are
  **recomputed from the base item + upgrade list** every time (`Upgrades.recompute`), so nothing stacks; enchantments
  and damage are preserved; the component survives save/load.
- **Repair** (artisan's bench: item + its material, or a repair kit) restores 25 % of max durability per material,
  keeping upgrades and enchantments. Blacksmith inhabitants also repair for emeralds (price scales with damage, uses
  steel/iron from the settlement's stock).
- **Salvage** (artisan's bench button): returns at most 50 % of the main material of the item's own recipe, scaled
  by remaining durability; upgrades are lost. Because crafting always costs ≥ 2× what salvage returns, there is no
  profitable loop.

## 8. Stations

| Station | Inputs | Heat/fuel | Purpose |
|---|---|---|---|
| Ore crusher | 2 | — | raw ore → crushed (+50 %), stone → gravel/sand, bones → bone meal |
| Alloy furnace | 4 | fuel | steel, embersteel, rimesteel, galvanic, glass, scrap |
| Forge | 4 | fuel | regional and rare weapons, tools, armour |
| Gem cutter | 4 | — | cut/polished gems, lenses |
| Leatherworker's bench | 4 | — | leather, linings, plates, cloth, grips, straps, rope, books |
| Artisan's bench | item + 1 | — | upgrades, repair, salvage |

The five processing stations reuse the Stage 7 `StationBlockEntity` (hoppers, comparators, consume-once at
completion, persistence, known-recipes panel). Basic equipment (copper, steel, fur, silk, …) uses the crafting
table; every recipe is listed in the Handbook's recipe browser.

## 9. Economy and inhabitants

- 217 goods were added (components, equipment, accessories, kits, gear) and 58 settlement recipes, so smiths,
  armourers, leatherworkers, gem cutters and engineers produce and stock regional equipment (Ashen settlements sell
  embersteel, Everfrost rimesteel, …). Totals: 483 goods, 265 recipes. All movement goes through validated
  transfers; the money audit stays exact.
- Players can run equipment businesses with the Stage 6 business system (NPC customers buy stocked goods).
- **Companions** wear gear from their companion pack (best armour piece per slot, best weapon) — display and real
  attributes; gear is cleared when you part ways.

## 10. Configuration
`config/underdeep.json`: `handbookOnJoin`, `advancementMigration` (see `docs/HANDBOOK.md`). Equipment has no
balance toggles; numbers live in `stage8.py`.

## 11. Contracts for Stages 9–10
- Combat (Stage 9) may read `EquipmentEffects.bonusDamage/onHit`, family stats from `content.json`, `Upgrades.of`.
  New weapon effects must be added as upgrade/material flags, not ad-hoc item checks.
- Hazard protection groups are `heat, cold, spores, fumes, static, pressure`; new hazards must add a group to
  `Hazards.group` and to the data.
- Accessory slots are ordinal-stable (ring, ring, amulet, charm); add new slot kinds only by appending.
- New items must pass the obtainability audit and get handbook data automatically (see `docs/RESOURCES.md`).

## 12. Art (Stage 11 backlog)
All 300+ textures are procedural (`stage8_art.py`): readable, consistent per material, but simple. Backlog: weapon
silhouettes per family, armour layer detail, glider wings render, bow pull frames polish, station block models
(currently cube-based), GUI textures for accessories/artisan bench.
