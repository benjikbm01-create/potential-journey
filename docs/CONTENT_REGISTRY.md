# Underdeep — Content Registry

Implemented content only (planned content lives in `MASTER_DESIGN.md`; secret content in `docs/private/`).
Persistent ids and enum ordinals are append-only.

## World generation types

| Id | Kind | Notes |
|---|---|---|
| `underdeep:underdeep` | world preset | listed under World Type; `level-type=underdeep\:underdeep` |
| `underdeep:underdeep` | dimension type | y −64..319, no skylight, ceiling, ambient light 0.1 |
| `underdeep:underdeep` | dimension effects (client) | no sky/clouds, biome fog colour |
| `underdeep:underdeep` | chunk generator | field `generator_version` (int); current terrain version **2** |
| `underdeep:underdeep` | biome source | no fields; resolves biomes by key |
| `underdeep:cave_surface` | placement modifier | `surface` floor/ceiling/water_floor/water_surface/wall, `min_y`, `max_y`, `max_scan` |
| `underdeep:start_zone` | placement modifier | `radius` around origin |
| `underdeep:variant` | placement modifier | `variants`: list of sub-regional variants (0–3) allowed |
| `underdeep:crystal_spire`, `underdeep:hanging_root`, `underdeep:ore_outcrop` | features | small decoration features |
| biome tags `underdeep:is_underdeep`, `underdeep:danger_{low,medium,high,extreme}` | tags | for future systems |

## Cavern layers (`CavernLayer`, ordinal = persistence id)

| # | Id | Floor base | Fluid level | Region cell | Graph cell | Ecosystems |
|---|---|---|---|---|---|---|
| 0 | upper | 142 | 112 (water; lava in Ashen/Crimson) | 112 | 176 | 14 (all except The Abyss) |
| 1 | deep | −8 | −44 | 160 | 224 | The Abyss (~56 %), Crystal Deep, Ashen Hollows, Stormstone Caverns |

## Ecosystems (`RegionType`, ordinal = persistence id)

Danger drives hostile spawning (see ARCHITECTURE §5); lighting drives glowing ground cover density.
Sub-regional variants: 0 = open/plain, 1 = typical, 2 = dense/rugged, 3 = lake/wet (hills ×0.35/×1/×1.5/×0.8;
variant 3 adds ponds and enlarges lakes; features filter by variant where noted).

| # | Id | Danger | Light | Terrain identity | Formations & landmarks | Flora / features |
|---|---|---|---|---|---|---|
| 0 | `rootwild` | low | bright | tall forest caverns, rolling hills, rivers, ponds | wooden pillar-trees with foliage rings; colossal trees; root arches | oak/fancy/dark oak/birch/azalea trees (dense in v2, none in v0 meadows), hanging roots, glow berries, glowcaps, glowmoss pools, berries, pumpkins, flowers & flower meadows (v0), sugar cane |
| 1 | `fungal_expanse` | low | bright | mounded terraced floors | mushroom-stem pillars with cap shelves; giant mushrooms with glowing undersides | huge mushrooms, warped fungi (v2/3), glowcaps (many), sporeglow soil pools, vines |
| 2 | `flooded_depths` | medium | moderate | lakes below the water table, islands, waterfalls (exceptional size) | dripstone pillars; sea arches; fossils | dripstone, many springs, seagrass, lumen seagrass, kelp, sea pickles, lily pads, glowreeds on shores |
| 3 | `ashen_hollows` | high | dark | ridged terraced mountains, lava rivers/pools | basalt pillars; volcanoes with lava craters; fossils | basalt columns, deltas, glowstone, magma, gilded blackstone, lava falls |
| 4 | `crystal_deep` | medium | moderate | very tall caverns, bridges | calcite/luminite pillars; giant crystal clusters; arches | luminite spires & clusters, amethyst geodes/clusters, calcite veins |
| 5 | `the_abyss` | extreme | pitch | colossal deep chambers, chasms to the lava sea | deepslate pillars; giant fossils; black arches | sculk, dripstone, rare luminite |
| 6 | `everfrost` | medium | moderate | frozen lakes (ice surface), icy caverns, giant icicles | packed/blue ice pillars & bridges; ice crystal clusters; ice arches | snow cover, ice spikes (v0/v2), spruce/pine (v1/v2), blue ice, rime crystals |
| 7 | `golden_grove` | low | bright | gentle hills, clearings, small rivers, bigger ponds | golden-leaved colossal trees; root arches | golden trees & bushes (custom golden leaves, glowing), amberblooms, flower meadows (v0), berries, pumpkins |
| 8 | `hollow_peaks` | medium | moderate | mountain ranges (72-block ridged mountains), plateaus (12-block terraces), passes, tallest caverns | many natural bridges; giant arches; fossils | sparse spruce/oak on plateaus, emerald ore, many springs |
| 9 | `emerald_wetlands` | low | bright | floor at the water table: shallow marsh, channels, islets | mangrove-wood pillar-trees; root arches | mangroves, glowreeds, dripleaf, lily pads (many), seagrass, lumen seagrass |
| 10 | `petrified_forest` | medium | moderate | stepped terraces; dense stone trunks | petrified-wood pillars; petrified colossal trees; fossil skeletons | embedded fossils, amber resin veins/pools, dead bushes |
| 11 | `crimson_basin` | high | dark | bowl-shaped basins (−30 at centres), lava pools/rivers | crimson-hyphae pillar trunks; giant crimson fungi; volcanoes | crimson fungi & roots, weeping vines, emberblooms, ember nylium, nether quartz/gold |
| 12 | `sunken_gardens` | medium | moderate | floors ~60 blocks below the water table: submerged valleys & cliffs | prismarine pillars; coral spire clusters (sea lanterns); arches | coral gardens, kelp forests, lumen seagrass, sea pickles |
| 13 | `stormstone_caverns` | high | dark | jagged fractured terrain, strong roughness, terraces | stormstone pillars with charged veins; storm crystal clusters | storm crystals, charged veins, amethyst geodes |
| 14 | `starlight_caverns` | low | bright | enormous open caverns, flat meadows, large lakes | calcite/starstone pillars; crystal clusters; arches | starstone-studded ceilings, starbloom meadows, starmoss pools, lily pads, lumen seagrass |

## Landmarks (terrain model, `world/terrain/Landmarks`)
Colossal tree, giant mushroom/fungus, crystal cluster, arch (single or crossing), fossil skeleton, volcano.
One landmark cell per 230 blocks (upper) / 320 (deep), ~75 % / 50 % occupied when a tall enough chamber
exists; ~13 % are titan-scale (≈1.8×). A colossal tree always stands near the spawn.

## Blocks

| Id | Light | Notes |
|---|---|---|
| `underdeep:luminite_block` | 12 | crystal rock; pickaxe |
| `underdeep:luminite_cluster` | 9 | directional cluster |
| `underdeep:glowcap` | 10 | small glowing fungus on any sturdy floor |
| `underdeep:glowmoss` | 13 | glowing moss ground cover (passive animals spawn on it; not dirt, so moss patches leave it intact) |
| `underdeep:amber_moss` | 13 | Golden Grove ground cover |
| `underdeep:starmoss` | 13 | Starlight ground cover |
| `underdeep:sporeglow_soil` | 12 | Fungal ground cover (mushrooms grow on it) |
| `underdeep:ember_nylium` | 9 | Crimson ground cover |
| `underdeep:golden_leaves` | 9 | leaves (decay normally when generated by small trees; persistent in landmarks) |
| `underdeep:starbloom` / `emberbloom` / `amberbloom` | 12 / 10 / 11 | glowing flowers on any sturdy floor |
| `underdeep:glowreed` | 11 | tall marsh reed (two blocks) |
| `underdeep:lumen_seagrass` | 10 | glowing underwater grass (shears) |
| `underdeep:starstone` | 15 | star-like ceiling stone |
| `underdeep:stormstone` | 0 | Stormstone rock (ores generate in it) |
| `underdeep:charged_stormstone` | 8 | glowing mineral veins |
| `underdeep:storm_crystal` / `rime_crystal` | 10 / 10 | directional clusters |
| `underdeep:petrified_wood` | 0 | pillar block, pickaxe |
| `underdeep:amber_resin` | 10 | fossil resin |

## Initial animals (placeholder until Stage 3)
Rootwild/Golden: sheep, cows, pigs, chickens, rabbits · Fungal: mooshrooms, pigs · Flooded/Wetlands: frogs,
chickens · Starlight: sheep, rabbits, cows · Hollow Peaks: goats, sheep · Everfrost: foxes, rabbits.

## Stage 2 content (spoiler-free; exceptional discoveries are listed only in `docs/private/`)

### Trees and wood
| Species | Wood set | Ecosystem | Shape | Sapling needs |
|---|---|---|---|---|
| Duskoak | `duskoak_*` (full set) | Rootwild | broad branching forest tree; ancient trees with 2×2 trunks and roots | living soil, light ≥ 7 |
| Spirebark | `spirebark_*` | Rootwild, Hollow Peaks | very tall, narrow, stacked foliage rings | soil or stone, light ≥ 7 |
| Goldbough | `goldwood_*` (leaves: `golden_leaves`) | Golden Grove | arms under a wide golden dome, resin knots | living soil, light ≥ 10 |
| Mirewillow | `mirewillow_*` | Emerald Wetlands, Flooded shores | buttress roots, weeping curtains | mud/clay/wet soil |
| Frostfir | `frostfir_*` | Everfrost | tiered conical conifer | snow/ice, cold, no heat nearby |
| Bloodthorn | `bloodthorn_*` (fireproof) | Crimson Basin | twisted wandering trunk, crooked limbs | volcanic ground, hot climate |
| Lumenwood | `lumenwood_*` (glowing leaves, light 10) | Starlight Caverns | slender curved trunk under a flat glowing umbrella | living soil, light ≥ 7 |
| Cragpine | spirebark logs, `cragpine_leaves` | Hollow Peaks | wind-bent, flat foliage pads | soil or stone |
| Cinderbark | `cinderbark_log` (+stripped), `cinder_leaves` (light 6) | Ashen Hollows | short gnarled volcanic tree | volcanic ground near heat |

Each full wood set: log, wood, stripped log/wood, planks, stairs, slab, fence, fence gate, door,
trapdoor, button, pressure plate, sign, hanging sign, leaves, sapling, potted sapling (vanilla tags,
recipes, stripping, flammability, fuel). Signs use Fabric `WoodTypeBuilder`; textures in
`textures/entity/signs/`.

### Land plants (by ecosystem)
Rootwild: duskberry bush (fruit), glowfern (light 6, spreads), mendleaf (medicinal herb), glowivy
(climbing, light 8), fallen duskoak logs with rotting wood. Golden Grove: honeyfruit bush, goldgrass
(may drop glowgrain seeds), sunleaf (rare herb), resin knots. Ashen Hollows: ashgrass, pyrelily (opens
near heat), emberwort (heat herb). Crystal Deep: shardgrass, prism lily (rare), ghostfern. Abyss:
voidroot (hanging, fibres), ghostfern. Everfrost: frostbell (blooms in the cold), snowberry bush,
steamfern (near heat only). Hollow Peaks: cliffberry bush, peakstar (rare), ledgevine (hanging).
Emerald Wetlands: bogbloom, marsh fern (may drop mire rice), bogwort (herb). Petrified Forest:
stoneroot, petrolichen. Crimson Basin: thornbriar (thorny fruit bush), plaguebloom (toxic), snapjaw
(snaps; drops fangs). Stormstone: static grass, voltbloom (reacts to energy), storm lichen. Starlight:
silvergrass, moonpetal (rare herb), starstrand (hanging, light 12).

### Fungi
puffcap (edible), inkcap (toxic raw, edible roasted), deathbell (poisonous), lanterncap (temporary
glowing blooms), sporepuff (spore releases), sagecap (rare, medicinal), bracket fungus (on logs),
embercap (volcanic), rimecap (frozen), gloomcap (darkness), calcicap (mineral), bloodcap (crimson),
tidecap (underwater); giant sporestalk, bellcap and shelfcap (`fungal_stem`, caps, `glowgill` light 13).

### Aquatic
glowpad (floating, light 9), starlily (floating, light 12), lakebloom, ribbonweed and glowkelp
(kelp-like columns), tidebloom (light 12), lanterncoral (block + fan, light 10/7), spirecoral, sea
grape bush (underwater fruit), siltwort (rare herb), lake chestnut (crop). Coral reefs via
`underdeep:coral_cluster`.

### Crops (seed item → crop block)
duskroot → `duskroots` (staple), glowgrain seeds → `glowgrain_crop` (bright light), cave truffle →
`cave_truffles` (darkness), mire rice → `mire_rice_crop` (water beside), lake chestnut →
`lake_chestnuts` (underwater), emberpepper → `emberpeppers` (faster near heat), frost turnip →
`frost_turnips` (cold, slow), sunbean → `sunbeans` (bright light). Wild patches grow in their
ecosystems; all grow on farmland at home when their conditions are met. One further exceptional crop
is documented privately.

### Foods and medicines
Fruits/berries: duskberries, honeyfruit, snowberries, cliffberries, briar fruit, sea grapes. Cooked:
baked duskroot, roasted truffle, roasted chestnut, baked turnip, roasted inkcap, glowgrain bread, rice
bowl, sunbean stew, cavern stew. Remedies: mendleaf poultice (regeneration), emberwort tonic (fire
resistance), antidote tincture (cures harmful effects), moonpetal draught (night vision), sunleaf salve
(absorption + regeneration).

### Minerals (ore → item)
cinnabar (Ashen, Crimson → red dye), sulfur (Ashen, Crimson, Abyss → gunpowder with niter), niter
(Fungal, Rootwild, Petrified), emberite (Ashen; long-burning fuel, blaze powder), cryolite (Everfrost;
freezes water into ice), malachite (Hollow Peaks; smelts to copper, green dye), bog iron (Wetlands,
Flooded; raw iron), galvanite (Stormstone; redstone), prismatic opal (Crystal Deep, rare), jasper
(Petrified), fossil (Petrified, Abyss; bone meal), umbralite (Abyss, rare deep metal), nacre (Sunken,
Flooded; pearls), starsilver (Starlight metal), glimmer quartz (Crystal Deep; renewable via budding
glimmer). Storage blocks for opal, jasper, malachite, cryolite, galvanite, starsilver, umbralite.
Vanilla coal, iron, copper, gold, redstone, lapis, diamond (and emerald in Hollow Peaks) still generate
in every ecosystem (`COMMON_ORES`).

### Stage 2 tags
`underdeep:plant_soil`, `fungal_soil`, `volcanic_soil`, `frozen_soil`, `mineral_soil`, `wet_soil`,
`aquatic_bed`, `fungal_convertible`, `shelf_fungus_hosts`, `growth_blockers`, `ore_hosts`,
`<wood>_logs`; item tag `underdeep:herbs`.

### Stage 2 world-generation features
`underdeep:tree` (`species`, `size` young/normal/ancient), `underdeep:giant_fungus`,
`underdeep:fallen_log`, `underdeep:coral_cluster`; configured features `underdeep:tree/<species>`
(saplings), `p_<plant>` patches, `ore_<mineral>`.

## Stage 3 — Wildlife

Entity ids `underdeep:<species>`; one spawn egg per species (`underdeep:<species>_spawn_egg`, creative
"Underdeep" tab). Species metadata: `wildlife/WildlifeRegistry` (ids append-only; they are stored in
region NBT). Models `assets/underdeep/creatures/<id>.json`, textures
`textures/entity/creature/<id>.png`, loot `data/underdeep/loot_table/entities/<id>.json`, all generated by
`tools/assets/creatures.py`.

60 species. The 55 listed below are the regular wildlife; five further rare/exceptional creatures are
documented only in private design material.

| Ecosystem | Species (★ tamable, ▲ rideable) |
|---|---|
| Rootwild | mossbuck, thumperkit, rootglider ★, lumenmoth, tuskbriar, shadecat ★, snufflet ★ |
| Fungal Expanse | capback ★▲ (pack animal, milk), sporemite, lanternbeetle, capstalker, puffling ★ |
| Flooded Depths | glimmerfin, newtling, lakehorn ★▲ (swimming mount), ribbon_eel |
| Ashen Hollows | emberskink, ashshell, scorchjaw |
| Crystal Deep | prismhorn, shardfly, geode_crab, facet_mantis |
| The Abyss | echo_crawler, lure_angler, gloomwing |
| Everfrost | frostmane (shearable), rimefox, glacier_skimmer |
| Golden Grove | goldfleece (shearable), amberwing ★, bumblewisp, glade_runner ★▲ (fast runner) |
| Hollow Peaks | cliffstrider ★▲ (climbing mount), ledgewing ★▲ (gliding mount), rockpip |
| Emerald Wetlands | bogcroaker, stiltwader, rillpup ★, glassdart |
| Petrified Forest | stonehide, dustbeak, petrisloth |
| Crimson Basin | bloodhorn, thornstalker, toxitoad |
| Sunken Gardens | reef_darter, coral_crab, veilray, stoneface |
| Stormstone Caverns | static_beetle, arcback |
| Starlight Caverns | starwisp, moonhorn, lumen_kit ★ |

Several species also live in a second ecosystem. Aquatic/amphibious: 12 (incl. private species);
flying, hovering or gliding: 13. How each companion is tamed is for players to discover (the field journal
records hints once a behaviour has been observed).

### Wildlife items
Foods: game meat, fish fillet, shellmeat (+ cooked versions), creature egg. Materials: hide (→ leather),
fur tuft (→ white wool), plume (→ feather), shell fragment and horn (→ bone meal), scale, silk strand
(→ string), venom gland (→ poison tipped arrows), creature oil (→ torches), glow gland (→ glowstone dust),
crystal husk (→ glimmer shard). Tools: harness (owner equips a rideable companion), field journal.
Renewable sources: shearing (fur tufts), milking (capback), shed items dropped by living animals near
players (horn, plume, scale, shell fragment, glow gland, crystal husk, venom gland), eggs.

### Wildlife blocks
`underdeep:twig_nest`, `underdeep:burrow_mound` (built by nesting species on natural ground only),
`underdeep:creature_egg_block` (hatches; tracks species, home region and owner).

### Entity tags
`minecraft:can_breathe_under_water`, `minecraft:aquatic`, `minecraft:fall_damage_immune`,
`minecraft:arthropod`, `minecraft:freeze_immune_entity_types` (Underdeep species added).

## Stage 4 — Civilizations and settlements

Ids are persistence contracts (append-only): civilization ids (`civ/Civilization`), settlement categories
(`civ/settlement/SettlementCategory`), profession ids (`civ/settlement/Professions`), building kind ids
(`civ/arch/BuildingKind`). Histories, customs and secrets are private design material.

### Civilizations (public identity)
| Id | Name | Home ecosystems (also) | Building style | Trades |
|---|---|---|---|---|
| `rootfolk` | Rootfolk | Rootwild (Wetlands, Golden Grove, Fungal fringe) | duskoak timber on mossy plinths, leaf-thatched roofs, trunk houses, Elder Tree | wood, herbs, remedies, brewing, tailoring |
| `forgekin` | Forgekin | Ashen Hollows (Crimson, Stormstone, Petrified) | blackstone halls, basalt buttresses, copper cornices, smoking chimneys, Great Furnace | tools, armour, metals, engineering |
| `tide_dwellers` | Tide Dwellers | Flooded Depths, Sunken Gardens (Wetlands) | decked stilt houses, prismarine hip roofs, piers and boats, Lighthouse | fish, boats, maps, pearls |
| `sporefolk` | Sporefolk | Fungal Expanse (Wetlands, Rootwild) | round stem houses with glowing caps, Great Cap | remedies, fungi, alchemy |
| `glimmerkin` | Glimmerkin | Crystal Deep (Stormstone, Starlight) | calcite with deepslate ribs, pointed roofs, crystal spires, Crystal Spire | gems, crystal, lights, books |
| `stoneborn` | Stoneborn | Hollow Peaks (Everfrost, Petrified) | heavy stone, shutters, deep eaves, terraced on slopes, Hall of Steps | building stone, tools, hides |
| `amberfolk` | Amberfolk | Golden Grove (Rootwild, Wetlands) | goldwood half-timbering, brick roofs, awnings, Fountain Court | food, textiles, commerce |
| `astrals` | Astrals | Starlight Caverns (Crystal Deep) | calcite and quartz colonnades, domes with spires, Observatory | maps, books, starsilver, lights |

### Settlement categories
| Id | Planned radius | Buildings | Inhabitants | Centre |
|---|---|---|---|---|
| `encampment` | 9–16 | 2–5 tents/stalls | 2–6 | campfire |
| `outpost` | 16–32 | 4–10 | 5–15 | well |
| `village` | 32–55 | 10–25 | 12–35 | well, hall, inn |
| `town` | 52–100 | 25–55 | 35–80 | landmark, gates |
| `city` | 90–175 | 50–100 | 80–200 | landmark, districts, gates |

### Building kinds (25, each designed per culture in 4 variants)
Residential: `tent`, `house_small`, `house`, `house_large`, `shared_house`. Commercial: `stall`, `shop`,
`inn`, `trade_hall`. Production: `workshop` (per profession), `kitchen`, `storehouse`. Agricultural: `farm`,
`barn`, `pen`. Civic: `hall`, `library`, `tower`, `guard_post`, `clinic`, `landmark`, `campfire`.
Infrastructure: `well` (or fountain), `gate`, `dock`. Roads, plazas, bridges, lamps and boats come from the
planner. 8 cultures × 25 kinds give 200 culture-specific designs, 8 of which are unique landmarks. Each
comes in 4 variants (size, storeys, roof or extension), and workshops are fitted per profession. The designs
are generated from a shared grammar (D-040), so variants of one kind share their interior layout.

### Professions (30)
farmer, herbalist, forester, carpenter, cook, baker, brewer, blacksmith, miner, smelter, engineer, fisher,
boatwright, navigator, alchemist, mycologist, healer, gemcutter, scholar, cartographer, stonemason, hunter,
guard, merchant, tailor, innkeeper, rancher, artisan, astronomer, lampwright.

### Entity
`underdeep:inhabitant`: a record-backed NPC. Six skin variants per culture
(`textures/entity/inhabitant/<civ>_<n>.png`) plus six tinted outfit layers (`outfit/<group>.png`: apron,
robe, armor, farmer, vest, coat).

### Commands (operators)
`/underdeep settlements [radius]`, `/underdeep settlement` (details of the one you stand in),
`/underdeep gallery confirm` (builds every design next to you; test worlds only).

## Stage 5 — Living inhabitants (ids are append-only ordinals; see docs/NPC_MINDS.md)

- **Traits (24)**: sociability, courage, patience, generosity, trustfulness, ambition, curiosity, loyalty, optimism,
  honesty, independence, discipline, humor, competitiveness, compassion, caution, adventurousness, impulsiveness,
  pride, forgiveness, resilience, frugality, homebody, tolerance.
- **Emotions (15)**: happiness, contentment, excitement, curiosity, affection, concern, fear, anger, frustration,
  sadness, loneliness, embarrassment, stress, pride, relief.
- **Needs (10)**: hunger, rest, safety, social, recreation, comfort, purpose, belonging, health, curiosity.
- **Activities (50)**: sleep, nap, eat_home, eat_tavern, tidy_home, morning_routine, recover, work_station,
  tend_fields, fish, smithing, crafting, cooking, study, serve_customers, stock_shelves, patrol, watch_post,
  heal_patients, animal_care, maintain_tools, gather_herbs, run_inn, chat, visit_friend, visit_family, share_meal,
  attend_gathering, help_neighbor, give_gift, make_amends, greet_newcomer, teach_apprentice, stroll, sit_and_rest,
  watch_wildlife, visit_garden, browse_market, tavern_evening, explore_nearby, favorite_spot, play_game, read,
  flee, take_shelter, warn_others, defend, seek_guard, help_injured, see_healer.
- **Memory types (28)**, **relationship labels (18)**, **kin (6)**, **encounter types (32)**, **knowledge kinds (7)**,
  **goal types (13; 10 implemented)**, **request kinds (3)**, **companion modes (6) and stances (3)**.
- **Dialogue topics (92)** in `civ/npc/Topics.java` (ids such as `greet_stranger`, `work_proud`, `news_predator`).
- **Network payloads**: `underdeep:dialogue_open` (S2C), `underdeep:dialogue_choose` (C2S).
- **Commands**: `/companion <cmd>` (players), `/underdeep npc …` (operators).
- **Lang keys**: `message.underdeep.companion.incapacitated`, `message.underdeep.companion.none`,
  `message.underdeep.people.header`, `message.underdeep.people.none`, `screen.underdeep.dialogue`.

## Stage 6 — Economy (ids are data/strings; see docs/ECONOMY.md)
- Goods: 156 (`data/underdeep/economy/goods.json`), categories (22): grain, produce, meat, fish, prepared_food,
  timber, lumber, stone, building, ore, metal, fuel, fiber, animal_product, herb, medicine, gem, decorative,
  specialty, tools, equipment, luxury.
- Production recipes: 102 (`production.json`; ids such as `smelt_iron`, `fell_trees`, `bake_glowgrain_bread`).
- Skills (append-only ordinals in `Mind.skills`): agriculture, forestry, mining, smithing, carpentry, fishing,
  cooking, trading, construction, animal_care, medicine, transport, research, guarding, hunting, masonry,
  tailoring, crafting.
- Sustainability pools: crops, forage, fungi, herbs, wood, fish, game, ranch, minerals, ore, stone.
- Project kinds (20): house, large_house, small_house, shared_house, workshop, farm, pen, storehouse, stall, shop,
  inn, kitchen, guard_post, dock, hall, library, clinic, well, tower, barn, repair.
- Shipment methods: porter, pack_train, boat. Contract types: supply, courier (reserved), escort, build.
- Activities appended: construct, travel, celebrate. Knowledge kinds appended: econ, person_moved.
- Commands: `/property`, `/business` (players); `/underdeep econ …` (operators).
- Lang keys: `message.underdeep.property.*`, `message.underdeep.business.*`, `message.underdeep.contract.paid`,
  `message.underdeep.journal.trade`.

## Stage 7 — Survival (ids are data/strings; see docs/SURVIVAL.md)
- Items: 79 foods/drinks, 18 medicines, 13 materials (`data/underdeep/survival/content.json`, generated by
  `tools/assets/stage7.py`); regional raw fish `glimmerfin`, `reef_darter`, `mire_eel`.
- Blocks (22): `cooking_pot`, `clay_oven`, `preparation_table`, `kettle`, `fermentation_crock`, `herbalist_table`,
  `drying_rack`, `compost_bin`, `fish_trap`, `feeding_trough`, `rich_soil`, `bedroll`, `camp_awning`, `wooden_chair`,
  `wooden_bench`, `wooden_table`, `storage_cabinet`, `pantry`, `medicine_cabinet`, `supply_crate`, `kitchen_counter`,
  `planter_box`. Block entity types: `underdeep:station`, `underdeep:drying_rack`, `underdeep:container`. Entity:
  `underdeep:seat` (never saved). Screen handler: `underdeep:station`.
- Status effects (14): warmed, cooled, spore_ward, clear_lungs, grounded, well_fed, rested, heat_stress,
  cold_stress, spore_irritation, choking_fumes, static_shock, wounded, weary.
- Station ids (strings in block entities/data): pot, oven, prep, kettle, crock, rack, herbal. Food groups (ordinals
  in `SurvivalData`, append-only): grain, vegetable, fruit, fungi, meat, fish, sweet.
- Hazard ids (journal): lava_heat, geothermal, extreme_cold, icy_water, biting_wind, spore_clouds, choking_fumes,
  static_charge, deep_pressure, abyssal_gloom, wounds, weariness, toxic_flora.
- Tags: `#underdeep:heat_sources` (block), item groups `#underdeep:fruit|vegetable|grain|mushroom|meat|cooked_meat|fish|
  cooked_fish|herb|sweet|egg|fiber|bread|dried_fruit_src|raw_meats`; rich soil and planter box added to every soil tag.
- Persistent state: player attachment `underdeep:survival` (schema 1: ate, known, hazards, rest, comfort); world
  state `underdeep_survival` (pressure, decayDay).
- Commands: `/underdeep survival [content|me|hazards|learnall|forget]` (operators).
- Economy: 110 goods, 105 recipes prefixed `s7_`.

## Stage 8 — Equipment, Handbook, advancements (ids are data/strings; see docs/EQUIPMENT.md, docs/HANDBOOK.md)
- Items (from `data/underdeep/equipment/content.json`, generated by `tools/assets/stage8.py`): 41 melee weapons
  (`<material>_<family>`, e.g. `steel_greatsword`, plus `nacre_cutlass`, `nacre_harpoon`, `starsilver_glaive`,
  `fossil_club`, `malachite_maul`, `glimmer_stiletto`), bows `lumenwood_shortbow`, `spirebark_longbow`,
  `horn_recurve_bow`, arrows `heavy_arrow`, `frost_arrow`, `venom_arrow`, `ember_arrow`; 26 tools
  (`<material>_<pickaxe|axe|shovel|hoe>`, `steel_hammer`, `umbralite_hammer`, `steel_excavator`, `steel_lumber_axe`,
  `steel_sickle`, `prospectors_pick`); 52 armour pieces (`<set>_<helmet|chestplate|leggings|boots>` for 13 sets);
  27 accessories; 34 upgrade kits (`<upgrade>_kit`); 22 components (`steel_ingot`, `embersteel_ingot`,
  `rimesteel_ingot`, `galvanic_ingot`, `crushed_*`, cut/polished gems, `glimmer_lens`, `fur_lining`, `scale_plate`,
  `shell_plate`, `silk_cloth`, `horn_grip`, `leather_strap`, `rope_coil`); utility `grappling_hook`, `cave_glider`,
  `depth_gauge`, `delvers_compass`, `backpack`, `herb_pouch`, `ore_satchel`, `repair_kit`; `underdeep_handbook`.
- Blocks (9): `ore_crusher`, `alloy_furnace`, `forge`, `gem_cutter`, `leatherworker_bench`, `artisan_bench`,
  `waymarker`, `glimmer_lantern`, `rope_ladder`. Station ids (append-only `Station` enum): crush, alloy, forge, gems,
  leather. Screen handlers: `underdeep:accessories`, `underdeep:artisan`, pack screens.
- Armour materials: one per set (`underdeep:<set>`, textures `textures/models/armor/<set>_layer_1/2.png`).
- Data components: `underdeep:upgrades` (list of upgrade ids). Attribute modifier ids: `underdeep:accessory.<slot>.<n>`,
  `underdeep:weapon_reach`, `underdeep:weapon_knockback`, `underdeep:tool_aquatic` (+ per-upgrade ids).
- Player attachments: `underdeep:accessories` (4 stacks, kept on death), `underdeep:progress` (advancement counters,
  schema 1), `underdeep:places` (waymarkers).
- Criterion: `underdeep:event`. Advancements: `data/underdeep/advancement/<tab>/<id>.json`, 13 tabs (start,
  exploration, wildlife, civilizations, relationships, economy, agriculture, cooking, medicine, homesteading,
  equipment, adventure, mastery), generated by `tools/advancements/generate.py`.
- Payloads: `underdeep:open_accessories` (C2S), `underdeep:accessory_flags`, `underdeep:handbook_data`,
  `underdeep:handbook_discover` (S2C). Keybinds: H (Handbook), K (accessories).
- Data: `data/underdeep/guide/natural_blocks.json` (census), `data/underdeep/guide/visibility.json`,
  `assets/underdeep/handbook/pages.json` (`tools/handbook/handbook.py`).
- Economy: 217 goods, 58 recipes prefixed `s8_` (totals 483 goods, 265 recipes).
- Config: `handbookOnJoin`, `advancementMigration`.
