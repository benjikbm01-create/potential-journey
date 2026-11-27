# Underdeep — Progress

Factual record of what exists. Test evidence: `TESTING.md`.

## Stage 1 — Foundation and underground world generation: COMPLETE (2026-11-25)

(Stage 1 table kept for history; region sizes and counts were superseded by Stage 1.5.)

### Completed
| System | State | Notes |
|---|---|---|
| Fabric 1.21.1 project (Gradle wrapper, Loom, Yarn, Fabric API, Java 21, split client source set) | done | builds; dev JAR `build/libs/underdeep-0.1.0-stage1.jar` |
| Common/client entry points, logging, `config/underdeep.json` | done | |
| JUnit test infrastructure (pure terrain model) | done | 8 tests |
| Dedicated-server self-test, test matrix, client screenshot tour, GUI world-creation test, world diff tool | done | dev-only, property-gated |
| World preset `underdeep:underdeep` + dimension type + client dimension effects | done | appears under World Type; `level-type=underdeep\:underdeep` |
| No "experimental settings" warning for Underdeep worlds | done | mixin marks the Underdeep overworld as stable |
| Deterministic pure terrain model (layers, regions, cavern graph, columns, density) | done | chunk-order independent at terrain level (verified) |
| Two cavern layers with fluid domains (water table y112, deep lava/water y−44) | done | no fluid leaks measured |
| Six foundational regions with distinct terrain profiles, biomes, palettes, surfaces | done | Rootwild, Fungal Expanse, Flooded Depths, Ashen Hollows, Crystal Deep, The Abyss |
| Terrain features: chambers (incl. colossal), tunnels, winding tunnels, shafts (spiral ledges), ravines, rivers & river tunnels, ponds, lakes & islands, pits, pillars, bridges/arches, stalactites/stalagmites, ceiling ridges, terraces, strata | done | |
| Vegetation/decoration via 64 placed features with `underdeep:cave_surface` placement | done | mostly vanilla configured features (Stage 2 adds custom flora) |
| Foundational blocks: luminite block, luminite cluster, glowcap | done | procedural placeholder textures |
| Custom features: crystal spire, hanging root, ore outcrop | done | |
| Safe, lit spawn in a guaranteed Rootwild start cavern with resources | done | verified on 5 worlds |
| Initial animal herds on cavern floors | done (placeholder) | replaced by Stage 3 wildlife |
| Nether portals disabled by default (config), no strongholds/End access | done | |
| Region identifiers and persistent world state (`data/underdeep.dat`) | done | region data map reserved for later stages |
| Analysis contracts: `RegionReport`, `RegionEnvironment`, `SiteAnalyzer`/`SiteType`, cavern graph as road network, `ConnectivityAnalyzer` | done | |
| Diagnostics: `/underdeep info/region/locate/spawncheck/cavern/sites/perf/render/connectivity`, F3 lines | done | permission-gated |
| Documentation set (CLAUDE.md, docs/*, private spoiler folder) | done | |

### Partially complete / placeholders
- Textures for the three custom blocks are simple procedural placeholders.
- Initial herds use vanilla animals (Stage 3 replaces).
- Flora relies mostly on vanilla features; regional custom flora/resources are Stage 2.
- Ashen Hollows and Crystal Deep reuse one biome each for both layers (deep variants could become
  sub-biomes in Stage 2).

### Not implemented (by design, later stages)
Wildlife simulation, civilizations/NPCs, world simulation/economy, survival/farming systems, equipment,
structures/ruins, bosses, custom sounds/animations, underground alternatives to Nether/End resources.

## Stage 1.5 — World expansion and refinement: see status below (2026-11-25/26)

| System | State | Notes |
|---|---|---|
| 15 ecosystems registered, generated and reachable | done | all within ~2,500 blocks of spawn on every tested seed |
| Small organic regions (median ≈100 blocks, p90 ≈250, max ≈700), climate-coherent neighbours, calibrated shares | done | `RegionMap` v2; GENERATOR_VERSION 2 |
| Sub-regional variants (open / typical / dense / lake) driving terrain and features | done | `underdeep:variant` placement |
| Distinct terrain per ecosystem (basins, marsh flats, mountain ranges, submerged valleys, frozen lakes, jagged fields…) | done | `RegionType` profiles |
| Organic pillars (lean, lobes) with region materials; forest pillars are colossal trees with canopies | done | |
| Landmarks: colossal trees, giant mushrooms/fungi, crystal clusters, arches, fossils, volcanoes; titan versions | done | terrain model |
| 18 new blocks (glowing ground covers, leaves, flowers, reed, seagrass, starstone, stormstone family, crystals, petrified wood, amber) with textures, models, loot, tags | done | procedural placeholder-quality textures |
| Natural lighting overhaul (ground cover pools, plants, canopies, ceilings, underwater) | done | measured per ecosystem |
| Hostile spawning by danger class, starter no-spawn zone, density caps, config | done | measured with a real client |
| Water: large lakes/seas (Flooded, Sunken), marshes, starlight lakes, rivers, waterfalls | done | |
| Diagnostics: distribution, accessibility, lighting, landmarks, survival growth, mob study | done | self-test + `scripts/mob_study.sh` |
| Settlement-compatible terrain | done (analysis) | chamber-centred usable-area report per size class (outpost/village/town/city); `SiteAnalyzer` gained ecosystem affinities; no settlements are built yet (later stages) |
| Memory / performance measurement | done | full generation 24–26 ms/chunk; ~550 MB heap after GC with ~10 000 chunks loaded |

Not done in Stage 1.5: custom creatures (Stage 3), custom sounds, ecosystem-specific resources beyond the
foundational blocks (Stage 2), underwater air pockets in Sunken Gardens.

## Stage 2 — Living ecosystems, vegetation and natural resources: COMPLETE (2026-11-26)

| System | State | Notes |
|---|---|---|
| Ecological metadata (`EcologyRegistry`, 95 entries) and regional summaries (`RegionEcology`) | done | unit-tested; Stage 3 contract in ARCHITECTURE §5c |
| Persistent regional ecology state (harvest pressure per region) | done | verified across save/restart |
| Reusable plant framework (growth rules, bounded spreading, protection of builds, particles gate, config) | done | random ticks only, no plant block entities |
| 9 tree species with distinct growth algorithms; young/normal/ancient sizes; renewable saplings | done | all 9 saplings verified growing on block light only |
| 7 complete wood sets incl. signs and hanging signs | done | recipes verified (planks, door, sign; vanilla tag recipes) |
| 35 land plants (bushes, herbs, flowers, grasses, ferns, vines) | done | |
| 16 fungi (13 small, 3 giant forms), fungal soil creep, temporary blooms, spore releases, decaying logs | done | |
| 10 aquatic species + coral reefs, floating, submerged, kelp-like and harvestable forms | done | |
| 9 crops with distinct needs (8 public + 1 exceptional), wild patches | done | growth and drops verified for 8 |
| Foods (25 edible items), 5 medicines | done | effects verified with a fake player |
| 15 regional minerals (14 ores + renewable glimmer quartz), 7 storage blocks, uses via recipes | done | all ores verified mineable with drops |
| Environmental processes (12) | done | see CONTENT_REGISTRY / ARCHITECTURE §5c |
| World generation in all 15 ecosystems | done | ≥ 3 native species measured in every ecosystem on 3 seeds |
| Textures, models, blockstates, loot, names, hints for every block/item | done | procedurally generated original pixel art (D-030); validated by the self-test |
| Targeted test suite (`SUITE=stage2`, `scripts/stage2_matrix.sh`) | done | results in TESTING.md |

Partially complete / limitations: art is procedural (coherent but simpler than hand-painted vanilla
art); no custom sounds (Stage 11) or custom particles (vanilla particle types are used); environmental
effects are not simulated in unloaded chunks (by design, D-028); vanilla stand-in vegetation from
Stage 1.5 remains alongside the new species in some ecosystems.

## Stage 3 — Wildlife and living ecological simulation: COMPLETE (2026-11-26)

| System | State | Notes |
|---|---|---|
| Species framework (`CreatureSpecies`, one `UnderdeepCreature` entity class, D-033) | done | locomotion, diet, social, temperament, hunting, activity, taming, mount, nest, products |
| 60 original species across all 15 ecosystems | done | 12 tamable, 5 rideable, 11 aquatic/amphibious, 11 flying/gliding/hovering; 5 rare/exceptional (private) |
| Behaviour library (> 25 behaviours) | done | forage, graze, drink, hunt (5 styles), flee + herd alarm, rest/sleep, territory with warning, herding, packs, schools/swarms, following, socialising, nesting, reproduction, scavenging, climbing, swimming, flying/gliding, curiosity, play, protective companions, avoiding hazards, seeking shelter, migration |
| Needs and individuality | done | hunger, tiredness, loneliness, reproductive readiness, territory; per-individual personality |
| Food chains on Stage 2 vegetation | done | bounded consumption, harvestables regrow, never crops or built areas |
| Reproduction, juveniles, nests, eggs | done | bounded by regional carrying capacity |
| Regional populations, tiers, reconciliation (D-034, D-035) | done | wild animals owned by regions; no duplication through chunk loading |
| Aggregate simulation (predation, growth, decline, migration, hunting disturbance) | done | accelerated tests only |
| Taming (4 methods), ownership, companions, riding (harness), pack inventory | done | ridden movement not tested by a real player |
| Animal resources (14 items, renewable products, recipes to vanilla materials) | done | |
| Vanilla passive mob replacement in Underdeep worlds (D-036) | done | vanilla worlds unaffected (tested) |
| Data-driven models, textures, procedural animation, spawn eggs | done | static poses inspected in the real client for all 60 species |
| Sounds | partial | pitched vanilla sounds; custom sounds are Stage 11 |
| Wildlife field journal (per player, no locations) | done | |
| Configuration (18 options) | done | README |
| Tests (`Stage3Checks`, `WildlifeModelTest`, `stage3_matrix.sh`, zoo tour, real-client wildlife study) | done | all PASS; TESTING.md |

Partially complete / limitations: see `NEXT_STEPS.md` (known issues). Terrain unchanged (D-038).

## Stage 4 — Civilizations, settlements and architecture: COMPLETE (2026-11-26)

| System | State | Notes |
|---|---|---|
| 8 civilizations (public identity; full lore private) | done | home and secondary ecosystems, palettes, names, trades |
| Architectural styles: 8 culture shells + palettes | done | recognizable silhouettes; inspected in real-client screenshots |
| Building library: 25 kinds × 8 cultures (200 designs, 8 unique landmarks), 4 variants each, furnished interiors | done | validator: entrances, reachable beds/workstations/storage |
| Settlement categories: encampment, outpost, village, town, city | done | sizes per brief; cities in mega chambers |
| Deterministic placement and spawn-access guarantees | done | unit-tested on 3 seeds; chunk-order determinism checked |
| Settlement planner: layouts, districts, roads (grade-limited), plots, terrain fit, yards, driveways, docks, gates, lamps, bridges | done | rejection diagnostics in the self-test |
| Chunk-by-chunk construction, decoration suppression, hostile exclusion, v3 gating for new worlds only | done | D-042/D-043 |
| Inhabitants: records (names, homes, professions, deaths, trade uses), embodiment budgets, daily routines, guards | done | accelerated routine test; never saved as entities |
| 30 professions; culture/profession trade tables (emeralds, daily restock) | done | |
| Discovery titles and per-player discovery records | done | |
| NPC appearance: 6 skins per culture, 6 tinted outfit groups, culture proportions | done | procedural pixel art |
| Commands: settlements, settlement, gallery | done | operators |
| Tests: SettlementMapTest, Stage4Checks, gallery and settlement tours | done | TESTING.md |

Partially complete / limitations: see `NEXT_STEPS.md`.

## Stage 5 — Living inhabitants, personalities, relationships and social simulation: IMPLEMENTED (2026-11-26)

See `docs/NPC_MINDS.md` for the full description.

| System | State | Notes |
|---|---|---|
| Minds in inhabitant records, deterministic generation, Stage 4 migration | done | schema 1; save/load round trip checked |
| 24 personality traits with documented effects; civilization/profession tendencies | done | |
| 15 emotions, 10 needs, mood | done | bounded, decaying, event driven |
| 50 activities, utility decisions with commitment/interrupts, daily cycle with shifts and jitter | done | unit-tested |
| Places from the settlement plan, shared-spot reservations | done | |
| 28 memory categories, consolidation, bounded storage | done | unit-tested |
| Relationships: multi-dimensional, asymmetric, sparse; 18 labels; households, families, mentors, rivals | done | |
| 32 NPC-to-NPC encounter types (both tiers), speech bubbles | done | |
| Information spread with source/confidence, local and bounded | done | |
| Per-player reputation, first meetings, witnessed attacks | done | |
| Dialogue screen (server-authoritative), 92-topic library, preference discovery | done | free-text input not implemented |
| Gifts (validated, consume one, cooldown), requests for help from real needs | done | |
| Personal goals (10 implemented, 3 reserved for Stage 6) | done | |
| Companions: eligibility, 12 commands, following with safe relocation, stances, retreat, incapacitation, pack, disconnect handling, shared memories | done | `/companion` |
| Abstract tier (bounded round-robin, near/distant intervals, 24 h catch-up) | done | |
| Journal: people met, preferences learned, companion | done | |
| Diagnostics `/underdeep npc …`, config options | done | |
| Tests: MindLogicTest (unit), Stage5Checks (self-test) | see TESTING.md | in-game behaviour to be verified by the user |

Limitations: see `NEXT_STEPS.md`.

## Stage 6 — Economies, trade, autonomous civilizations and living world simulation: IMPLEMENTED (2026-11-26)

See `docs/ECONOMY.md`.

| System | State | Notes |
|---|---|---|
| Goods (156 real items, 22 categories), emerald currency, ledgers with ownership rules | done | data-generated |
| Production registry (102 recipes: 27 gathering, 75 processing; 28 producing professions + merchants, guards) | done | conserving, unit/self-tested |
| Gathering from regional species yields, sustainability pools, hunting/fishing remove real wildlife (40 % floor) | done | |
| Households (food, fuel, fibre, medicine, luxuries), food security → minds, wages, support, levy | done | |
| Monetary stabilisation (issuance/upkeep), world money audit | done | audit exact in self-test |
| Dynamic bounded prices, merchant shelves (wholesale), real stock offers, relationship discounts, no loops | done | |
| Skills (18 kinds) growing with work, apprentices, job changes | done | |
| Prosperity (11 components) driving decisions | done | |
| Trade network (cavern-graph routes), imports/exports, agreements, shipments with escrow, exactly-once delivery | done | self-tested end to end |
| Disruptions (delays, attacks, blocked routes), news carried by traders | done | |
| Physical caravans (trader, guards, pack animals) near players | implemented | not observed in a real client |
| Water transport | abstract only | documented |
| Leadership (8 cultural styles), 18 decision categories | done | |
| Construction (20 kinds), plots, materials, labour, physical bottom-up placement, repairs, protection | done | placement verified in the self-test world |
| Population: vacancies, migration (same identity), newcomers, partnerships, children, new homes, growth | done | |
| Diplomacy (28 civ pairs, disputes, reconciliation), local reputation | done | |
| ~40 causal event types + variations, celebrations | done | |
| Contracts (supply, construction materials, escort; escrowed), property (buy/rent, permissions), player shops with NPC customers, hiring | done | courier contracts not posted |
| Dialogue: stock, noticeboard, contracts, property, shop, hiring, economy topics; journal trade notes | done | |
| Diagnostics `/underdeep econ …`, config | done | |

## Stage 7 — Survival, farming, cooking, medicine and homesteading: IMPLEMENTED (2026-11-27)

See `docs/SURVIVAL.md`.

| System | State | Notes |
|---|---|---|
| Content data (79 foods/drinks, 18 + 5 medicines, 13 materials, 131 station recipes) generated by `stage7.py`, validated at load | done | self-tested |
| Food groups, variety bonus (Well Fed I/II, no penalties), bounded meal buffs, container return | done | self-tested |
| Stations: cooking pot, kettle (heat), clay oven (fuel), preparation table, fermentation crock, herbalist's table, drying rack | done | ticked directly in the self-test |
| Matching with groups, rejection, batches (≤4/≤8), consume-once, remainders, hoppers/comparators, persistence | done | self-tested |
| Station screen (progress, heat/fuel, known recipes with present/missing ingredients, effects) | implemented | not seen in a real client |
| Recipe discovery (basics, cooking, eating, cooks teach regional dishes), client sync | done | learning self-tested; dialogue not hands-on |
| 20 regional recipes over 8 civilizations; cultural food preferences for new minds | done | |
| Farming: rich soil, planter box, sporeglow soil recipe, compost bin, gentle compost, herb cuttings, right-click harvest | done | self-tested |
| Husbandry: feeding trough (diet feeds, productivity, bounded breeding, tamed only) | done | feeding self-tested; breeding not observed |
| Regional fishing (rod mixin, bait, persistent pressure), fish trap (bait, bounded catch-up) | done | tables/trap/pressure self-tested; rod not hands-on |
| Medicine: cooldowns, cures, use on others, healer service, lightweight injuries | done | cures and injuries self-tested |
| 13 hazards with warnings, counterplay, journal; none in starting regions | done | exposure/counterplay self-tested with a fake player |
| Camping: bedroll (no spawn change unless sneaking), awning shelter, portable crate | done | spawn rule + crate self-tested |
| Homes: chairs/bench (sitting), table, 5 storage kinds with filters, comfort score → Rested, inn bed and meal | done | comfort/filters self-tested |
| Economy: 110 new goods, 105 settlement recipes, service payments audited | done | audit in Stage 6 checks |
| Journal survival notes, `/underdeep survival …`, config section | done | |
| Spoilage | not implemented (config reserved, off) | by design, see SURVIVAL.md §2 |

## Stage 8 — Equipment, crafting, material progression, Handbook and advancements: IMPLEMENTED (2026-11-27)

See `docs/EQUIPMENT.md`, `docs/RESOURCES.md`, `docs/HANDBOOK.md`.

| System | State | Notes |
|---|---|---|
| Resource census (320 natural blocks over 15 regions) + obtainability/usefulness audit of every item | done | all 666 Underdeep items and 64 vanilla essentials reachable; report in each self-test |
| Gap fixes (strip/planting/obsidian rules, starlily placement, 5 underground alternative recipes) | done | |
| 17 material families; alloys, crushing, gem cutting, leatherworking (160 station recipes) | done | stations self-tested |
| 41 melee weapons (7 families) + 3 bows + 4 arrows; material specials, coatings | done | attributes/specials self-tested; combat feel not hands-on |
| 13 armour sets with hazard protection feeding the Stage 7 hazard meters | done | protection self-tested |
| 26 tools incl. hammer, excavator, lumber axe, sickle, prospector's pick; tier rules; smelting pickaxe | done | 3×3, felling, smelting self-tested |
| Exploration gear: grappling hook, cave glider, depth gauge, delver's compass, waymarker, rope ladder, lantern, backpack/pouch/satchel | implemented | packs' filters self-tested; the rest not hands-on |
| 27 accessories in 4 slots (K key), no accumulation, kept on death | done | self-tested |
| 34 upgrades (2 slots, one per group), kits, artisan's bench, repair (+NPC smith), salvage ≤ 50 % | done | self-tested incl. save/load |
| Economy: 217 goods, 58 settlement recipes; companion gear | done | money audit exact |
| Handbook (item + H key, 13 categories, 99 pages + generated item/species pages, search, recipes, bookmarks, discovery) | done | data self-tested; screen not seen in a real client |
| 134 advancements in 13 tabs, custom event criterion, counters, quiet migration | done | triggers self-tested with a test player; toasts not seen |
| Spoilage, combat AI changes, new bosses | not in scope | Stage 9+ |

## Stage 9 — Exploration, dungeons, ruins, artifacts and secret discoveries: IMPLEMENTED (2026-11-27)

See `docs/EXPLORATION.md` (public) and private design material. Needs a new world (generator v4).

| System | State | Notes |
|---|---|---|
| Deterministic site placement on both layers (pure model, overlap resolution, settlement/spawn exclusion) | done | ~780 sites within 2000 blocks (seed 42), all 15 ecosystems; determinism self-tested |
| 30 ruin, 16 landmark, 20 dungeon, 10 standalone secret designs + 17 hidden rooms, 7 culture palettes | done | static walkability audit 228 layouts / 3 seeds; generated chunks match layouts 99.8 % |
| Puzzle framework, 16 mechanic types + key locks, seals, break protection | done | every puzzle of all 20 dungeons solved in-world through real interactions; 180/180 seals |
| 11 trap mechanics, guards, disarming, severity config | done | self-tested; damage feel not hands-on |
| Reward policies shared / personal / unique / key; claims persisted; unique artifacts once per world | done | self-tested incl. save/load |
| 38 artifacts/relics + 3 valuables; 54 records; journal in the Handbook; delver's compass targets | done | journal screen not seen in a real client |
| Inhabitant rumours, surveyor's charts, identification, findings reports (audited treasury payments) | done | self-tested |
| 18 travel encounters, warding bell | done | encounters run in-world by the self-test; frequency not play-tested |
| 37 advancements (new Expedition tab), +115 Handbook pages, audio context hooks for Stage 11 | done | |
| `/underdeep explore …` operator commands, config section `exploration` | done | |
| Combat encounters / bosses in reserved rooms | not in scope | Stage 10 |

## Stages 10–12
Not started.
