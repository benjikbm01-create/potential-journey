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
