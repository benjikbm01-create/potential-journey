# Underdeep — Decision Log

Each entry: decision, reasons, trade-offs. Newest last. Reference IDs from code comments and docs.

## D-001 Toolchain versions (Stage 1)
Minecraft 1.21.1, Yarn 1.21.1+build.3, Fabric Loader 0.17.3, Fabric API 0.116.18+1.21.1, Loom 1.11.8,
Gradle 8.14.3, Java 21. Verified against maven.fabricmc.net / meta.fabricmc.net during Stage 1.
Loom 1.11 is the newest line that runs on Gradle 8.14 without a Gradle 9 migration. Yarn chosen as
Fabric's default mapping; decompiled sources (`genSourcesWithVineflower`) were used to verify APIs.

## D-002 Custom `ChunkGenerator` instead of noise-settings JSON
`NoiseChunkGenerator` is `final` and density-function JSON cannot express guaranteed graph
connectivity, layer-specific fluid rules or region profile blending. A custom generator gives full
control and lets the same pure terrain model serve analysis tools and future simulation. Trade-off: we
re-implement fill/surface and lose vanilla aquifers, ore veins and noise caves (all intentionally
replaced). Vanilla features, structures plumbing, biomes and spawning still work.

## D-003 Terrain model as a pure function, separate from Minecraft classes
`world/noise`, `world/region`, `world/terrain` have no Minecraft dependencies. Benefits: chunk-order
independence, unit testability (JUnit without a game), PNG previews without a server, cheap queries of
unloaded areas for settlements/ecology. Trade-off: some duplication of vanilla utilities (Perlin noise,
hashing).

## D-004 Seed binding through `createStructurePlacementCalculator`
`BiomeSource#getBiome` receives only a `MultiNoiseSampler`. The generator binds the terrain model to the
world's sampler (weak identity map) when vanilla calls `createStructurePlacementCalculator(…, noiseConfig,
seed)` during chunk-manager construction. Avoids mixins and mutable global "current seed" state; works
with several worlds/seeds at once. Fallback: biome source returns Rootwild and logs a warning if called
with an unknown sampler.

## D-005 Vertical layout: −64..319, two cavern layers
Same height as the vanilla overworld (known memory/perf profile, vanilla ore height ranges remain
meaningful: coal/iron in the upper layer, gold/redstone/diamond/lapis in the deep layer). Upper floors
~110–180 with ceilings up to ~300; deep floors ~−55–20 with ceilings up to ~130. Regions span large
horizontal areas in each layer rather than thin depth bands; Crystal Deep and Ashen Hollows occur in both
layers. A third stratum can be added later via `CavernLayer` (append-only).

## D-006 Connectivity by construction (cavern graph)
Rather than hoping noise caves connect, each layer is a grid graph of chambers with tunnels to E/S
neighbours (+ random diagonals), heavily warped/perturbed so the grid is not visible. Layers connect via
shafts. Verified by ground-truth flood fill (TESTING.md). Trade-off: inter-layer links are sparser
(hundreds of blocks apart) than horizontal links.

## D-007 Fluid domains
Each layer has one fluid level (upper 112, deep −44); perched ponds have their own constant level and a
raised rim. Winding tunnels are excluded from the band between the two layers' fluid domains, ravines
never cut below the water table, shafts only open on dry floors, and noise is suppressed around ponds.
Result: no fluid sits next to air at the same height at generation time (measured 0 floating sources in
spawn-area tests). Waterfalls come from spring features and flow naturally. Trade-off: lakes are flat at
fixed levels per layer; different-level lakes require ponds.

## D-008 No vanilla carvers, no vanilla structures in Stage 1
Carvers would produce ordinary Minecraft caves (explicitly undesired). Underdeep biomes are not in vanilla
structure biome tags, so strongholds, villages, mineshafts etc. do not generate. Monster rooms (features)
are kept for early loot. Stage 9 adds Underdeep structures.

## D-009 Nether/End disabled by default
Nether portals cannot be lit in Underdeep worlds (config `netherPortalsInUnderdeep`). No strongholds means
no End portal. Dimensions remain in the preset so the config can re-enable access later. Progression
resources from those dimensions need underground alternatives (planned Stages 7–8, see MASTER_DESIGN §5).

## D-010 Day/night cycle kept
Time still advances (beds, future NPC routines rely on it). The custom client `DimensionEffects` ignore
time for fog colour so caverns look the same at all hours. Biomes disable precipitation.

## D-011 Data generation with a Python script
Biome feature lists must preserve a global relative order across biomes. `tools/worldgen/generate_data.py`
declares one ordered list per generation step and emits all JSON, preventing feature-cycle crashes and
keeping content tables readable. Trade-off: contributors must re-run the script after edits.

## D-012 Placement via `underdeep:cave_surface`
Vanilla heightmap placement is meaningless under a roof, and vanilla `environment_scan` is limited to 32
steps. The custom modifier scans from a random height to the first real floor/ceiling/wall/lake bed/water
surface, so tall caverns get proportionally more decoration.

## D-013 Initial herds placed by Underdeep
Vanilla `SpawnHelper.populateEntities` uses the surface heightmap (the bedrock roof). `InitialPopulation`
places a few vanilla herds on real floors as Stage 1 food; to be replaced by Stage 3 wildlife.

## D-014 Lit spawn
Hostile mobs spawn at block light 0. The spawn finder plants glowcaps (light 10) around the spawn so new
players arrive in a lit, mob-free pocket. The start zone also gets extra glowcaps, glow berries, trees,
berries and an ore outcrop.

## D-015 Self-test inside the dedicated server
Automated verification runs inside a real dedicated server (Loom `runServer`), gated by a system property,
and writes a Markdown/JSON report. This tests the real registries, preset, spawn, chunk pipeline, saving
and reloading — more trustworthy than mocks. Screenshot tour does the same for the client under Xvfb.

## D-016 Visual tuning from real-client inspection (Stage 1)
Screenshots from a real client (Xvfb, software GL) drove these changes: stalactites/stalagmites made
rarer and stubbier (thin dense spikes read as a "pole forest"); rock strata bands thickened to 11 blocks
with lower-contrast palettes (5-block bands looked like zebra stripes); Rootwild tree density halved and
spawn selection rejects covered spots (the spawn was buried in canopy); vanilla basalt pillars removed from
the Abyss and made rare in Ashen Hollows (floor-to-ceiling pillars in 100-block caverns); ambient light
raised from 0.06 to 0.10 (Nether level) so unlit cavern geometry stays faintly readable. Ambient light should
be revisited with real players and real GPUs in Stage 11.

## D-017 Determinism scope
Guarantee: terrain (rock/air/water/lava) is a pure function of seed and position — verified identical across
chunk orders and JVM runs. Not guaranteed: decoration features that cross chunk borders (trees, patches,
some ores) may differ in a small fraction of blocks depending on scheduling, as in vanilla. Systems that need
reproducibility (settlement placement, simulation) must use the terrain model, not decorated blocks.

## D-018 Stage 1.5 region system: small warped cells + calibrated 3D climate (GENERATOR_VERSION 2)
Stage 1 regions (700–830-block Voronoi cells, 6 types) made players cross ~500 blocks of one ecosystem.
Regions are now ~112-block (upper) / 160-block (deep) Voronoi cells, domain-warped at two scales (75 and 18
blocks), each choosing an ecosystem from a 3-axis climate (temperature, moisture, mineral) sampled at the
cell centre. Low-frequency climate makes neighbouring cells agree, so cells merge into regions of naturally
varied size (measured median ≈100 blocks, p90 ≈250, max ≈700). Climate axes are normalised to a uniform
distribution (normal CDF of the measured spread) and per-ecosystem biases are solved on a fixed sample so
each ecosystem reaches a target share; prototype positions give believable neighbours. Dangerous ecosystems
are penalised within 850 blocks of the origin. Trade-offs: region identity (`RegionId`) now refers to the
small cells; worlds created with v1 must be regenerated (terrain does not match at chunk borders).

## D-019 Formations and landmarks in the terrain model
Pillars, bridges and new landmarks (colossal trees, giant mushrooms, crystal clusters, arches, fossils,
volcanoes) are evaluated in the pure terrain model as signed-distance primitives and only fill open space.
Reasons: they can be much larger than the vanilla 3x3-chunk feature window, stay chunk-order independent and
are visible to analysis tools. Materials come from per-region formation palettes (wood, ice, crystal,
petrified wood, coral, bone...). Pillars lean and have lobed cross-sections; in forest ecosystems every pillar
is a colossal tree with a foliage ring. Cost: a few distance evaluations per open block near formations
(early-out keeps fill time unchanged within measurement noise).

## D-020 Natural light from ground cover
Block light spreads only ~15 blocks, so ceiling lights cannot light cavern floors. Ground-level light comes from
pools of light-emitting ground cover (glowmoss, amber moss, starmoss, sporeglow soil, ember nylium, charged
stormstone, amber resin, luminite) painted by the surface painter with density set by the ecosystem's lighting
class, plus glowing plants, leaves, crystals and formations. Measured with real block light in generated chunks
(TESTING.md). Global brightness is unchanged.

## D-021 Hostile spawning by ecosystem danger
Vanilla spawns monsters wherever block light is 0 — everywhere underground. Low-danger ecosystems have no
monster spawn entries; other ecosystems keep vanilla monsters but the chunk generator's spawn list passes only
a configurable fraction of natural attempts (medium 12 %, high 45 %, extreme 100 %), the starter cavern
(150 blocks, above y90) is a hard no-spawn zone, and biome spawn costs cap local density. Only natural
spawning is affected (spawners, commands, eggs, future encounters are not). Stage 3 replaces vanilla monsters
with ecosystem creatures using the same danger contract.

## D-022 Sub-regional variants
A separate noise field assigns each column a variant 0–3 (open, typical, dense/rugged, lake/wet) that scales
hills, adds lakes and filters features (`underdeep:variant`), so one ecosystem contains meadows, dense forests,
lake districts, ice-spike fields and so on without separate biome registrations.

## D-023 Peaceful buffer zones
The first mob study showed 1–3 hostiles near low-danger regions: with ~100-block regions, mobs spawned in
neighbouring medium regions wander in. Natural hostile spawns are now also refused wherever a low-danger
ecosystem has blend weight (≈34 blocks around peaceful regions). The generator keeps a reference to its
world's terrain (set once in `createStructurePlacementCalculator`) for this check.

## D-024 Measurement methodology (Stage 1.5)
- Distribution is measured on explorable cavern columns of the terrain model (the generator uses the same
  functions; terrain-level identity with generated chunks is verified separately). "Meaningful" regions are
  contiguous areas ≥ 60 blocks across.
- Lighting is measured as real block light in generated chunks at the standing position above ground (plants
  and snow skipped, canopies skipped), or on the lake bed for Sunken Gardens.
- Mob populations require a real player (vanilla spawning ignores fake players), so a real client joins the
  dedicated server and the server-side study teleports it between sites in creative mode.

## D-025 Stage 2 species metadata in pure Java (`EcologyRegistry`)
Species data (habitats, roles, rarity, needs) is Java, not data packs: it is consumed by Java systems
(Stage 3 AI, regional simulation), must be unit-testable without a Minecraft bootstrap, and changes
together with the block classes. Block ids are cross-checked by the self-test. Ids are append-only.

## D-026 Configurable block classes instead of one class per plant
Most flora are instances of a handful of reusable classes configured fluently (`EcoPlantBlock`,
`HarvestablePlantBlock`, `BloomingPlantBlock`, …) with a `GrowthRule`. Keeps ~100 organisms
maintainable and their behaviour consistent; special behaviours (snapjaw, spore puff, lanterncap,
resin, crystals) get small dedicated classes. Codecs return `MapCodec.unit(this)` (block codecs are
not used for serialisation in 1.21.1).

## D-027 Bounded spreading rules
Spreading only fills empty space next to the parent, is density-capped, never within 2 blocks of
player-made blocks (`#underdeep:growth_blockers`), runs only in Underdeep worlds, is ¼ rate outside the
species' native ecosystems, and fungal soil only creeps inside the Fungal Expanse. Growth is linear and
local; it cannot overrun regions or bases. Rates and toggles are in `config/underdeep.json`.

## D-028 No offline catch-up growth
Plants grow only through random ticks in loaded chunks. There is deliberately no "time since last
visit" growth: it would duplicate resources across restarts and complicate determinism. Abstract
regional state (`EcologyState`, `RegionEcology`) is the hook for Stage 3+ aggregate simulation.

## D-029 Custom tree feature instead of vanilla tree configs
Vanilla trunk/foliage placers cannot express the wanted silhouettes (wind-bent, umbrella, weeping
curtains, twisted). `UnderdeepTreeFeature` implements nine growth algorithms with a common safe
commit (`PlantBuilder`). The same feature serves world generation (young/normal/ancient) and sapling
growth (`underdeep:tree/<species>` configured features). Stage 1.5 colossal trees stay terrain landmarks.

## D-030 Procedural original pixel art
All Stage 2 textures are generated by `tools/assets/art.py` (deterministic, original, Minecraft-style
16×16). This guarantees every block/item has a complete texture set and coherent per-species palettes;
a hand-painted art pass can replace individual PNGs later without code changes.

## D-031 Terrain unchanged in Stage 2 (GENERATOR_VERSION stays 2)
Stage 2 only adds decoration features, so terrain output is identical (terrain-stage checksum for seed
12345 unchanged: `f6fb2cde5f4f2a93`). Existing Stage 1.5 worlds load, but already generated chunks have no
Stage 2 flora; new chunks do, so a fresh world is recommended. Adding placed features shifts vanilla
decoration random sequences, so decoration in new chunks differs from Stage 1.5 even where nothing new
was placed.

## D-032 Targeted Stage 2 test suite
Per the Stage 2 brief, exhaustive multi-hour terrain analyses are not repeated: `SUITE=stage2` keeps the
lighting probe and survival checks, skips connectivity/accessibility/landmark/site analyses (terrain
unchanged), and adds functional tests in sealed test plots. Full terrain matrix remains
`scripts/test_matrix.sh` for terrain changes and Stage 12.

## D-033 One creature class, data-driven species
All Stage 3 species are `UnderdeepCreature` (a `TameableEntity`) with a `CreatureSpecies` record
selecting locomotion (navigation + move control), diet, social structure, temperament, hunting style,
activity cycle, taming method, mount type and nest type. Behaviour is composed from shared goals in
`wildlife/ai/CreatureGoals`. Sixty separate entity classes would duplicate code and make tuning and
later civilisation integration (Stage 4–6) much harder; species-specific quirks are small hooks.

## D-034 Wild animals are owned by their region, not by chunks
A wild creature (not tamed, not named, not persistent) is never written to chunk storage
(`shouldSave() == false`). Its region's abstract population (`RegionWildlife`, `regionData` key
`wildlife`) is the single source of truth; entities are materialised near players only while the
number of active entities of that species/region is below the abstract count, and return to the pool
when they unload or move > `wildlifeDespawnDistance` from every player. Counts change only through
births, deaths, migration and taming/naming. This makes duplication through chunk loading impossible,
keeps kills permanent, needs no force-loading, and lets distant regions be simulated cheaply.
Tamed and named creatures are ordinary saved entities and leave the pool.

## D-035 Three simulation tiers
Tier 1: creatures within `wildlifeDetailDistance` (48) of a player run full AI. Tier 2: loaded but
farther creatures run reduced AI (needs updated every 20 ticks, goals throttled). Tier 3: regions with no
active creatures run the aggregate model (`RegionWildlife.step`: predation, logistic growth, decline
above capacity, emigration to compatible neighbouring regions with exact conservation) every
`wildlifeSimInterval` ticks, at most `wildlifeSimRegionsPerUpdate` regions per update, at most 3
in-game days of catch-up per step. Regions that contain active creatures are never simulated
abstractly, so an individual is never in two tiers at once.

## D-036 Vanilla passive mobs replaced in Underdeep worlds only
With `vanillaMobReplacement` (default on) the Underdeep generator spawns no vanilla passive/water
animals and skips vanilla initial herds; vanilla resources remain available through creature drops
(leather from hide, wool from fur, feathers from plumes, string from silk, milk, eggs, bone meal).
Vanilla monsters remain at `vanillaMonsterShare` (0.6) of their Stage 1.5 rate until Stage 4+
replaces them. Vanilla worlds are unaffected.

## D-037 Data-driven creature models
Models are JSON (`assets/underdeep/creatures/<id>.json`, generated by `tools/assets/creatures.py`) with
parts tagged by role (body, head, leg, wing, tail, fin, ...). The client builds `TexturedModelData`
from the JSON and animates by role (gait, wing flap, tail sway, sleep pose, warning pose). This keeps 60
distinct body plans maintainable; Blockbench-made models can replace individual JSON files later.
Sounds use pitched vanilla sounds (`Voices`) until Stage 11.

## D-038 Terrain unchanged in Stage 3 (GENERATOR_VERSION stays 2)
Stage 3 adds no world-generation features: blocks generated for a seed are identical to Stage 2. Only
entity population changed (no vanilla passive animals or initial herds in new Underdeep chunks; native
wildlife is materialised at runtime). Stage 2 worlds therefore load unchanged; vanilla animals already
saved in explored chunks remain until killed.

## D-039 Accelerated behaviour tests
Per the Stage 3 brief (no multi-hour runs), behaviour is verified in sealed test plots by ticking the real
entities with their real goals directly on the server thread (world clock advanced per tick), with
`UnderdeepCreature.testObserver` standing in for a nearby player. Population dynamics are verified by
calling the real aggregate model for simulated days. These are accelerated tests, not long-running
multiplayer sessions; TESTING.md labels them as such.

## D-040 Hybrid procedural architecture
Buildings are generated from code, not from hand-authored structure files. Each design combines:
- a shared massing and furnished interior per building kind (`civ/arch/Architect`)
- a culture's shell (`Shells`): plinth, wall rhythm, windows, entrance, roof and facade articulation
- a palette of the culture's materials (`Palette`)
- one hand-designed landmark per culture (`Landmarks`)

Designs are cached per (culture, kind, variant, profession) and validated offline (`BlueprintValidator`:
entrance present, beds/workstations/storage/merchant spots reachable on foot). This keeps around 25 kinds
× 4 variants × 8 cultures maintainable and guarantees complete interiors. Authored templates can replace
individual designs later without changing the planner.

## D-041 Settlements at cavern chambers
`SettlementMap` places settlements only at cavern-graph chamber centres. Chambers are open and
tunnel-connected by construction, so every settlement is reachable through the cave network. Each
chamber's decision is a pure function of the seed:
1. A hashed roll gives a tentative size category.
2. A terrain fit check downgrades the category until it fits the chamber (dry share, headroom, relief;
   water is acceptable for the Tide Dwellers).
3. A local spacing rule against neighbouring chambers' tentative results removes overlaps.

Two guarantees are added near spawn: a welcoming settlement 140–320 blocks away and a town 600–1100
blocks away. The starting cavern stays wild. The decision is independent of chunk generation order.

## D-042 Pure plans, chunk-by-chunk construction
`SettlementPlanner` turns a site into a complete `SettlementPlan`. The plan contains the layout type,
plazas, roads with smoothed height profiles, plots with buildings, rotations and floor levels,
driveways, docks, gates and lamps. It uses only the terrain model, and is cached.

The chunk generator writes each chunk's share of every intersecting plan after natural decoration. The
order is plazas, yards, roads, buildings, lamps, boats. Every step reads only the plan and that chunk's
own terrain blocks.

Natural decoration whose origin lies within 12 blocks of a planned building, road or plaza is
suppressed (`CaveSurfacePlacement`). Features of neighbouring chunks therefore cannot grow into
buildings, whatever order the chunks generate in.

## D-043 Generator v3: settlements only in new worlds
Stage 4 bumps `UnderdeepTerrain.GENERATOR_VERSION` to 3; terrain blocks are unchanged, but settlements
add blocks. Settlements generate only in worlds created with v3 or later. The world state records the
version a world was created with, so Stage 1–3 worlds keep generating exactly as before and never get
half-built settlements cut off at the borders of explored chunks. Servers can opt in with
`settlementsInExistingWorlds`.

## D-044 Inhabitants are records first, entities second
Every registered inhabitant is a persistent record (`SettlementState`). A record holds name, profession,
bed, workplace, appearance, alive flag, trade uses and a memory compound reserved for Stage 5.
- **Embodiment.** `InhabitantManager` embodies the records nearest to players as entities, up to 24 per
  settlement and 96 per world.
- **Never saved.** The entities are never written to chunks; when players leave they return to their
  records. This gives stable identities, permanent deaths, no duplicates and no chunk-load exploits.
- **Stage 6 hook.** Abstract simulation of the unembodied majority starts in Stage 6.
- **Daily routine.** Work by day, gather in the evening, sleep in their own bed at night; guards patrol
  in shifts.

## D-045 Emeralds as the Stage 4 trade currency
Basic trading uses vanilla merchant offers priced in emeralds, with per-inhabitant offers that restock
daily. A regional currency and price dynamics belong to the Stage 6 economy.

## D-046 Quick settlement suite
`SUITE=stage4` runs the Stage 4 self-test without the Stage 1.5–3 measurements, which have not changed,
for fast iteration. Full verification still runs the `stage2` suite, which includes Stage 3 and Stage 4,
on several seeds.

## D-047 Minds live inside the inhabitant records
Stage 5 personality, emotions, needs, memories, relationships, knowledge, goals, preferences, possessions and
companion state are a `Mind` stored in each `InhabitantRecord` (NBT key `mind`, schema 1). There is exactly one
authoritative copy, whichever tier advances it; Stage 4 records are migrated deterministically from their
appearance seed. The Stage 4 `memory` compound stays unused (kept for compatibility).

## D-048 One decision model for both tiers
`Brain` (utility + commitment) drives embodied inhabitants once per second and unembodied ones in hourly steps,
so an individual behaves the same whether or not a player is watching. Abstract catch-up is capped at the last
24 in-game hours; earlier absence only means "they lived their routine".

## D-049 Authored, composable dialogue — no language model
Conversations come from an authored topic library with eligibility predicates (only true statements) and
variation by personality, emotion, culture, familiarity and memories. No network service, no generated text.
The optional free-text keyword input was not implemented (menu choices cover every topic group).

## D-050 Companions are incapacitated, not killed (default)
Defeat sends a companion home to recover (configurable `companionPermanentDeath`). Ordinary inhabitants keep the
Stage 4 rule: deaths are permanent. Companions never target players, to keep cooperative play safe.

## D-051 Speech bubbles as riding text displays
NPC-to-NPC lines are shown by short-lived text display entities riding the speaker (tagged; cleaned up on load,
never saved because their vehicle is never saved). Configurable (`speechBubbles`, `speechBubbleSeconds`).

## D-052 Relationship growth has diminishing returns
Trivial interactions (small talk, greetings) count fully only twice per day per bond, then 35 %, then not at all;
significant events are never diminished. Gifts have a cooldown. Reputation cannot be ground by spamming talk.

## D-053 One economy record per settlement, one file for the world economy
Ledgers, treasuries, households, merchants, workplaces, pools, projects and events of every settlement live in
`EconomyState` (`data/underdeep_economy.dat`) together with shipments, contracts, property and businesses, so a
shipment's cargo and the ledgers it left/enters are saved atomically. Tiers differ only in step cadence.

## D-054 Settlement stores are abstract
The settlement ledger has no physical chests mirroring it (no double ownership); players reach goods through
merchants' shelves (owned by the seller) and contracts. Stage 4 building loot is independent of the economy.

## D-055 Emeralds and a stabilised community money supply
Emeralds stay the currency. Wages come from the settlement treasury (community workplaces); money supply per
inhabitant is kept within 60–160 % of `moneyPerCapita` by explicit, counted issuance and upkeep; a world money
audit identity is checked by the self-test. Private profit exists for merchants (tills) and player shops.

## D-056 Production follows minds; gathering follows ecology
Workers produce only while their Stage 5 activity is work. Gathering yields come from the region's actual species
and are limited by sustainability pools; hunting and fishing also remove animals from Stage 3 populations above a
40 % safeguard. NPCs never extract blocks; physical crop harvesting is cosmetic (no double counting).

## D-057 Caravans are embodied shipments
A shipment is the only authority; caravan entities are a temporary body near players (never saved) and write
their progress back. Water routes are abstract (no autonomous boat navigation). Killing a trader loses the
shipment exactly once and spills half its cargo.

## D-058 Construction never overwrites player work
Plots avoid plan buildings, roads, plazas, projects and player property; any non-natural block in a loaded plot
rejects it, and a block built later stops the project (refunding unused materials). Placement happens only in
loaded chunks, bounded per tick; repairs only fill air.

## D-059 Migration moves people, not copies
A migrant's record (with the same mind object) is appended to the destination; the old record becomes a pointer
(`movedTo`), never embodied or simulated. Old relationships are kept as `elsewhere` bonds keyed by settlement and
index. Mind schema 2 adds food security, skills, journey, employer and elsewhere bonds (defaults on load).

## D-060 Stage 7 content is data generated from one definition
Foods, drinks, medicines, materials, station recipes, regional cuisine and the derived economy goods/recipes all come
from `tools/assets/stage7.py` (assets + `data/underdeep/survival/content.json`); `generate_economy.py` imports it,
so content, art and economy can never disagree. Java registers items from the data at init and validates every id
once all items exist (bad recipes are disabled and reported, never crashing).

## D-061 Vanilla hunger stays; variety is a bonus only
No thirst, no nutrient meters, no penalties for a plain diet. Food groups give Well Fed I/II (less exhaustion,
slower hazard exposure). Meal buffs are bounded (no duration stacking, amplifier ≤ I, ≤ 10 min, opposites cancel).

## D-062 Stations consume inputs once at completion
Generic `StationBlockEntity` for six stations: exact-use matching (every non-empty input must be used), most specific
recipe wins, batches limited by station/ingredients/containers/output room, heat or fuel gates progress without
loss, remainders returned. Recipe knowledge is informational: unknown recipes can still be cooked (discovery by
experiment), so knowledge can never block progress.

## D-063 Hazards are exposure meters with warnings, never instant
Hazards only in dangerous regions (never the starting regions), warning at 40 %, effect only at 100 %, mild bounded
penalties, damage optional, counterplay through effects/gear/heat/shelter. Meters are transient (not saved).

## D-064 Fishing pressure is persistent per water area
`SurvivalState` stores pressure per 64×64 area; it thins catches (floor 25 %) and recovers daily. Traps share the
tables and pressure, catch-up after unloading is capped, catches never exceed bait.

## D-065 Bedrolls never set spawn implicitly
Sleeping uses Fabric sleep events (valid bed, facing, no spawn); sneak-use sets a forced spawn deliberately.

## D-066 Services keep the money audit exact
Healer and inn payments are physical emeralds into the treasury recorded as `playerIn`; goods used are taken from
the ledger as consumption. Troughs and traps never interact with wild populations.

## D-067 Obtainability is proven by a fixpoint over real game data
`guide/Obtainability` combines an in-world census of natural blocks with simulated loot tables, the live recipe
manager, station recipes and settlement trade. Items reachable only through themselves are unreachable. The audit
runs in every self-test and its result feeds the Handbook, so documentation and the game cannot disagree. Vanilla
items with no underground route are listed as outside the premise rather than given artificial recipes, except where
an essential route needed one (5 alternative recipes from regional materials).

## D-068 Equipment is data; derived attributes are recomputed, never accumulated
Materials, families, armour, accessories and upgrades are generated by `stage8.py`. An upgraded item stores only the
upgrade id list; attributes and max durability are recomputed from the base item each time. Accessory modifiers use
stable ids and are removed before being re-added. This makes duplication/stacking exploits structurally impossible.

## D-069 Equipment reuses the Stage 7 station engine
The five processing stations are `StationBlockEntity` kinds (appended `Station` enum values); only the artisan's
bench (upgrade/repair/salvage) has its own handler. Salvage ≤ 50 % of the main material scaled by condition.

## D-070 Area tools break through the player's own block breaking
Hammer/excavator/lumber axe call `tryBreakBlock` for each extra block (with a re-entrancy guard), so drops,
durability, protection mods and claims behave like normal mining. Felling needs leaves (natural trees only), max 48.

## D-071 Advancements count real events through one criterion
`underdeep:event` with `{event, subject, min, distinct}` plus persistent per-player counters; gameplay reports
completed actions only. Migration from older worlds is one-time, quiet and evidence-based.

## D-072 Handbook data is server-authoritative and discovery-gated
The server sends each player only item data and discovery keys they are entitled to; sensitive items appear only
after being obtained; technical/deferred items never. Bookmarks are client-side.

## D-073 Exploration sites are a pure function of seed and terrain model
Sites sit on a jittered per-layer grid, fit-checked against the terrain model only, with overlaps resolved by
priority across neighbouring cells, so placement never depends on chunk order. Generator bumped to v4; older
worlds get no sites unless `inOlderWorlds` is set (sites at the border of generated land would be cut).

## D-074 Authored layouts in Java with a static audit
Designs are code (`explore/design`) over a small vocabulary rather than NBT structure files: they vary with the
seed, rotate, adapt to the culture palette and are audited automatically (walkability, puzzle data, light paths)
for several seeds in every Stage 9 self-test. Fixtures (ladders, doors, mechanisms) lock their positions so
later room geometry cannot overwrite them.

## D-075 Mechanisms are indexed by the layout, not stored as block entities
The layout is recomputed deterministically, so `SiteLayout` maps world positions to puzzle elements, seals, traps,
caches and inscriptions. Only player-caused state (solved puzzles, claims, jammed traps, woken guards) is stored
in `ExplorationState`.

## D-076 Reward policies for multiplayer fairness
Shared containers use vanilla loot; expedition coffers give each player one share; relic pedestals and unique
artifacts are awarded once per site / once per world; key pedestals give each player one key. Claims are recorded
before items are given.

## D-077 Spoiler-safe discovery
Places, hidden rooms and records become visible (Handbook pages, journal, compass targets, advancements) only after
the player has been there or read them. Operator commands that reveal positions are permission-gated.

## D-078 Version-aware terrain
`UnderdeepTerrain(seed, version)`: the chunk generator's stored version selects the terrain, so worlds created before
generator v5 keep producing identical terrain (no seams). New worldgen features are gated by version.

## D-079 Connectivity through passages and descents, not a global graph
Secondary passages on a finer grid and helical descent caves are pure functions of (seed, cell) evaluated per column,
with bounded shared caches; physical walkability is validated with a voxel survival walk, not graph links.

## D-080 Combat is a server-authoritative layer over vanilla
Stamina, dodge invulnerability, parry windows, heavy-attack timing and hit detection, counters and stagger are decided
on the server; clients send intents; vanilla shields, armour, enchantments and cooldowns keep working.

## D-081 One reusable fighting goal with archetypes
Fighting creatures share `CombatGoal` (approach → telegraph → strike-at-the-moment → recovery) parameterised by 15
archetypes from ecology data, instead of per-species code; peaceful species are untouched.

## D-082 Guardians are encounter-owned, never saved
Boss entities exist only while their encounter is active; a restart or unload resets the fight and restores arena
blocks recorded in persistent state. Victories, credits, Signs set into the door and completion are world facts.

## D-083 Shared progression through a world door
Sign-stones are personal rewards, but the door records Signs per world (keystone + any 3 of 6), so absent players never
force a repeat and late joiners can take part. Lost Signs are reclaimable at the guardian's altar until set.

## D-084 Data-driven articulated boss rigs
Boss models are JSON rigs (parts, pivots, materials, glow, per-move poses) generated by tools/assets/stage10.py and
animated from synced move stages; Stage 11 can rebuild visuals without changing mechanics.
