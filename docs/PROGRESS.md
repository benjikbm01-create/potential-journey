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

## Stages 6–12
Not started.
