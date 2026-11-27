# Underdeep — Survival, farming, cooking, medicine and homesteading (Stage 7)

Stage 7 gives the Stage 2–3 resources uses and adds cooking, preserving, medicine, hazards, camping and homes, on
top of vanilla hunger (unchanged). Package `dev.underdeep.survival`; content data
`data/underdeep/survival/content.json`, generated with everything else by `tools/assets/stage7.py` (run through
`tools/assets/stage2_assets.py` with `PYTHONHASHSEED=0`). Settings: the `survival` object in `config/underdeep.json`.

## 1. Content in numbers

| Kind | Count | Where |
|---|---|---|
| Foods (dishes, preserved foods, snacks, regional fish) | 60 | `stage7.py` `F` (kind `bowl`/`plain`) |
| Drinks (teas, juices, broths, ferments) | 19 | `F` kind `bottle` (no thirst meter) |
| Medicines | 18 new + 5 Stage 2 = 23 | `M` |
| Materials (flour, dough, salt, butter, vinegar, spice blend, compost, 5 animal feeds, bait) | 13 | `MAT` |
| Station recipes | 131 (98 food, drink and feed; 33 medicine and herb cuttings) | `R` |
| Vanilla-type roasts (furnace/smoker/campfire) | 7 inputs × 3 methods | `ROASTS` |
| Workstations | 7 cooking/medicine + compost bin, fish trap, feeding trough | `SurvivalBlocks` |
| Homestead and camping blocks | 12 | chairs, bench, table, 5 storage kinds, planter, bedroll, awning, rich soil |
| Status effects | 14 (7 protective/comfort, 7 hazard/injury) | `SurvivalEffects` |
| Hazards | 13 | `Hazards.Hazard` |

## 2. Food rules
- **Vanilla hunger and saturation stay.** Every food keeps a vanilla food component.
- **Food groups** (grain, vegetable, fruit, fungi, meat, fish, sweet) are listed for every Underdeep and common
  vanilla food (tooltips show them). Eating 3 different groups within 1.5 in-game days gives **Well Fed I**
  (hunger drains 12 % slower); 5 groups gives **Well Fed II** (20 % slower and hazard exposure builds 25 % slower).
  A plain diet is never penalised.
- **Meal buffs** (warmed, cooled, spore ward, water breathing, haste, night vision, regeneration, rested, …) follow
  bounded rules (`Meals.apply`): durations never add up (the longer wins), food amplifiers are at most I, food buffs
  last at most 10 minutes, warmed and cooled cancel each other.
- **Containers**: bowl dishes return the bowl, drinks return the glass bottle (vanilla `usingConvertsTo`).
- **Spoilage** is not implemented (config flag `spoilage` reserved, off). Preservation instead gives *better*
  food for travel: dried, pickled, fermented, aged and baked rations are compact and very filling.

## 3. Cooking progression and stations
1. Campfire / furnace / smoker: simple roasts (sunbeans, emberpepper, puffcap, rimecap, regional fish).
2. **Cooking pot** (needs heat below: lit campfire, fire, lava, magma, a lit furnace or `#underdeep:heat_sources`),
   **preparation table** (no heat), **kettle** (heat), **clay oven** (burns fuel).
3. **Fermentation crock** (slow, no heat), **drying rack**, **herbalist's table**.

Station rules (`StationBlockEntity`):
- Slots: up to 6 inputs, a container slot (bowls/bottles), a fuel slot (oven), an output slot.
- A recipe matches when every non-empty input is used by one of its ingredients (specific items first, then
  ingredient groups such as "any fruit"); the most specific match wins. Unrelated items block every recipe.
- **Batches**: one load makes up to 4 batches (8 at the preparation and herbal tables), limited by ingredients,
  containers and output room. A batch takes the same time as a single serving (group cooking).
- Work progresses only with heat (pot, kettle) or burning fuel (oven); without them progress pauses, nothing is
  lost. Inputs are consumed **once, at completion**; taking things out early loses nothing.
- Container items of ingredients come back (water/milk bucket → bucket, honey bottle → bottle).
- Automation: hoppers insert inputs from above, bowls/bottles/fuel from the sides, and extract output from below;
  comparators read the fill level.
- Persistence: contents, progress and fuel are saved with the block entity.

**Drying rack**: four hanging spots, each item on its own timer (meat → jerky, fish → dried fish, fruit, fungi,
kelp, hide → leather). Rain slows it. Right-click to hang, empty hand to take down. Hoppers: in from above, finished
items out from below.

**Recipe discovery** (`Recipes`): basic recipes are known from the start (50 of 131). Others are learned by
cooking them (any valid combination works even when unknown — experimenting is allowed), by eating the dish, or by
asking a settlement's cook, baker, brewer or innkeeper ("Could you teach me one of your dishes?"), who teaches
their civilization's regional recipes first. Knowledge only affects what the station screen and journal show.

**Station screen** (`StationScreen`): slots, a progress arrow, heat or fuel state, what the current inputs make
(with batch count) or why nothing happens, and a side panel with the recipes you know for this station (green =
can be made now). Click one to see each ingredient marked present (+) or missing (−) and the meal's effects.

**Regional cuisine**: each civilization has 2–4 dishes (`civ` field in the recipe data; e.g. Sporefolk fungal hotpot,
spore paste and kombucha; Tide-dweller tide soup and tide rolls; Forgekin goulash and pickled peppers; Stoneborn
marrow broth and pickled turnips; Amberfolk hearth stew, tarts and fizz; Rootfolk forest stew and kvass;
Glimmerkin glimmer broth; Astral starlit porridge and starlily nectar; 20 regional recipes). Inhabitants of each civilization may like
their dishes as gifts (new minds only).

## 4. Farming
- All Stage 2 crops, bushes, herbs, fungi and aquatic plants remain the cultivatable set (≈30 kinds): crops planted
  from their produce or seeds, bushes from their fruit, fungi on fungal soil, aquatic plants in water.
- **Herb cuttings**: 3 sprigs + 1 compost at the herbalist's table give a new herb plant (mendleaf, sunleaf,
  emberwort, bogwort, moonpetal, siltwort), so herbs are renewable without uncontrolled spreading.
- **Rich soil** (4 dirt + compost) and **planter boxes**: accepted by every soil tag; crops grow ×1.5
  (`richSoilGrowth`) and never dry out; herbs and bushes also grow faster. **Sporeglow soil** is now craftable
  (2 dirt + puffcap + compost) for fungal farming.
- **Compost bin**: same items and chances as the vanilla composter (all crops, herbs and dishes are compostable),
  yields compost; hoppers fill it from above and empty it from below. **Compost** used on a crop advances exactly
  one stage (bone meal still jumps several); on other plants it has an even chance to grow them once.
- **Right-click harvest**: with an empty hand, a mature crop drops its harvest and replants itself using one seed
  from the drops (nothing happens if the drops hold no seed). Bushes and herbs already re-grow when picked.
- Growth runs on vanilla random ticks only: unloaded farms do not grow (no force loading).

## 5. Animal husbandry
- Uses the Stage 3 species and products (shed products, milk, shearing). **Feeding trough**: holds feed; every five
  seconds it feeds hungry **tamed** animals within 12 blocks with the feed their diet wants (grain feed for grazers
  and browsers, fungal feed, fruit mash, meat scraps, mineral lick, bait for aquatic feeders), one feed per feeding.
  Fed animals stay productive: shedding products come up to 30 s sooner per feeding and sheared coats regrow sooner,
  never more than one product per feeding.
- Breeding: well-fed adults near a trough may come into breeding mood (vanilla cooldowns apply) while fewer than
  `breedingCap` (24) tamed animals are around; offspring of tamed parents are tamed. Wild animals are never fed or
  bred by troughs, so the wild/domestic split of Stage 3 is kept (tamed animals are not part of regional counts).

## 6. Fishing and traps
- Each region has its own catch table (`Fishing`): glimmerfin in the crystal and starlit waters, reef darters in the
  Sunken Gardens, mire eels in the wetlands, salmon and cod in the cold north, shellfish where they live.
- Rod: in the Underdeep 65 % of catches are regional (85 % with **fishing bait** in the other hand, which uses one
  bait); the rest is vanilla loot.
- **Sustainability**: every catch adds pressure to a 64×64 stretch of water (`SurvivalState`, persistent); above 24
  the chance of a catch falls, to at most a 75 % reduction, and pressure recovers by 10 per day.
- **Fish trap**: place in water with bait inside; one catch attempt every 5 minutes (`fishTrapInterval`), one bait
  per catch, at most 3 catches caught up after the area was unloaded, crowded traps share fish. Hoppers can empty
  it. Catches can never exceed the bait used.

## 7. Medicine
- 23 preparations (bandage, field medkit, cooling/warming/burn/glow salves, calming balm, grounding paste, spore
  ward / clear-air / breathing / venom tinctures, restorative tonic, fortifying elixir, vigor draught, purging
  draught, bloodcap tonic, smelling salts + the five Stage 2 remedies). Most are made at the herbalist's table.
- Each has a cooldown (3 s for a bandage up to 5 min for the purging draught) so strong medicine cannot be chained.
- **Cooperative care**: applied medicine (salves, bandages, kits, balms) can be used on another player, a tamed
  animal or an inhabitant by right-clicking them.
- **Healers** (healer, herbalist, alchemist) treat you for 2 emeralds, using one dressing or remedy from their
  settlement's stock; with none in stock they ask for herbs.
- **Lightweight injuries** (`injuries`, on): a single hit of 7+ damage leaves you Wounded (−8 % speed) for 5
  minutes or until bandaged.

## 8. Hazards
Checked once per second per player; exposure meters (0–100) build in the hazard and fall outside it. A warning at
40 % names the hazard and its counterplay (once per minute at most) and records it in the journal. Only at 100 %
does the hazard effect apply (mild attribute penalties; optional 1 damage every 4–6 s with `hazardDamage`). There
are **no hazards in the gentle starting regions** (Rootwild, Golden Grove, Emerald Wetlands, Starlight Caverns).

| Hazard | Where | Counterplay |
|---|---|---|
| Searing heat | near lava/magma in the Ashen Hollows and Crimson Basin | back away; Cooled (cooling salve, rimecap cooler, cryolite chill, frost ration, berry shrub); awning shade |
| Geothermal heat | Ashen Hollows, Crimson Basin | Cooled; standing in water; shade |
| Bitter cold | Everfrost | a fire/campfire/furnace within 4 blocks; leather armour (−20 % per piece); Warmed (warming salve, ember chili, emberwort tea, ember ration …); awning |
| Icy water | water in the Everfrost | get out; Warmed |
| Biting wind | Hollow Peaks above y 60 | as cold |
| Spore clouds | among dense fungus in the Fungal Expanse | Spore Ward (tincture, marsh tea, kombucha, fungal hotpot, spore paste) |
| Choking fumes | the Abyss below y −40; the Crimson Basin depths | climb; Clear Lungs (clear-air tincture) |
| Static charge | Stormstone Caverns, only with metal tools or armour | Grounded (grounding paste); put metal away |
| Deep pressure | submerged deep in the Abyss | water breathing (breathing draught, shellfish broth, tide soup) |
| Abyssal gloom | total darkness in the Abyss | hold a light; night vision |
| Wounds | heavy single hits (anywhere) | bandage, field medkit; heals in time |
| Weariness | 3 days without rest (off by default) | sleep, or sit 30 s on a chair or bench |
| Toxic flora | Stage 2 plants (unchanged) | antidote, venom antiserum |

## 9. Camping, homes and comfort
- **Bedroll**: a single-block bed for expeditions (night only, no nearby monsters, like a bed). It never moves your
  respawn point; sneak-use sets it deliberately (`bedrollSetsSpawnWhenSneaking`).
- **Camp awning**: shelter that reduces heat and cold exposure nearby and counts toward comfort. Portable cooking:
  a campfire with a cooking pot or kettle on top. Expedition supplies: rations, hardtack, jerky, dried fish, trail
  mix, bandages, the supply crate (keeps its contents when broken, so it can be carried).
- **Furniture**: chairs and benches you can sit on (an unsaved invisible seat), table, storage cabinet (27),
  pantry (27, food and ingredients only), medicine cabinet (18, medicine and herbs only), supply crate (18,
  portable), kitchen counter (9), planter box. Storage works with hoppers and comparators and keeps the Stage 6
  property protection (they are ordinary containers).
- **Comfort** (`Comfort.score`, 0–10, positive only): up to 4 for different furniture kinds, +1 kitchen station, +2
  a real bed, +1 awning, +1 light, +1 warmth, +1 company (a player, tamed animal or inhabitant nearby). Waking gives
  **Rested** for 2 + 2×comfort minutes (max 20): slower hunger and a little speed. Sitting a while also rests.
  Innkeepers rent a bed (2 emeralds, full Rested) and serve a hot meal from the settlement's own food (1 emerald).
- Shared bases use the Stage 6 property permissions unchanged.

## 10. Economy integration
- 110 new goods (every Stage 7 food, drink, medicine and material) and 105 settlement production recipes derived from
  the station recipes (`tools/economy/generate_economy.py` imports `stage7.py`; groups map to economy categories,
  buckets are not modelled for settlements). Cooks, bakers, brewers, innkeepers, hunters and healers produce them.
- Service payments (healer, inn) are physical emeralds into the treasury, recorded as player inflow, so the world
  money audit stays exact; goods used leave the ledger as consumption. NPCs never harvest player farms.

## 11. Multiplayer, persistence, performance
- Server-authoritative: stations, traps, troughs, hazards and buffs run only on the server; the client gets
  recipe knowledge (one small payload on join and when it changes) and vanilla container sync.
- Persistent: block entities (stations, racks, containers), `SurvivalData` attachment per player (copied on death:
  recent food groups, known recipes, hazards met, last rest), `SurvivalState` (fishing pressure). Hazard meters are
  transient. NBT keys are append-only.
- Cost: hazards ~1 block-scan per player per second (≤ 9×5×9 lookups); troughs every 5 s; traps once a second with
  an early exit; stations tick only while loaded. Nothing force-loads chunks.
- Vanilla worlds: all items, blocks and stations work; hazards and regional fishing are Underdeep-only.

## 12. Visual backlog (Stage 11)
Placeholder-quality but consistent 16×16 pixel art was generated for all items; blocks have simple multi-element
models. To improve in Stage 11: animated steam/bubbling on active stations (currently particles only), filled-pot
model state, oven glow, drying-rack item orientation, chair/bench variants per wood type, awning cloth colours, crate
lid, trough fill levels, proper GUI textures for the station screen (currently drawn with fills), dedicated effect
icons beyond the generated discs.

## 13. Reserved for Stage 8 (integration contracts)
- Materials reserved for advanced equipment: `cryolite` (cooling gear), `galvanite` / `storm_lichen` (grounding
  gear), `fur_tuft` / `hide` (cold gear — leather already counts), `glow_gland` (light/gloom gear), `amber_glob`,
  `bloodcap`, `deathbell` (advanced medicine).
- Contracts Stage 8 may rely on: `Hazards.Hazard` ids and `Hazards.exposures(...)` (protection hooks via effects:
  warmed, cooled, spore_ward, clear_lungs, grounded); `Meals.apply` bounded-buff rules; `SurvivalData` attachment
  (append keys only); station recipe data format (`station`, `ingredients` with `#group` keys, `container`, `civ`);
  `SurvivalBlocks.HEAT_SOURCES` tag; fish tables per region.

## 14. Testing
`debug/Stage7Checks` runs in every self-test suite (block entities ticked directly; a Fabric fake player stands in
for a player). See `docs/TESTING.md` for results and what was not tested by hand.
