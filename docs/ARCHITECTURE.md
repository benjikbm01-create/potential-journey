# Underdeep — Technical Architecture

Status: Stage 1 (foundation and world generation). See `PROGRESS.md` for what is implemented.

## 1. Toolchain

| Component | Version | Notes |
|---|---|---|
| Minecraft | 1.21.1 | |
| Fabric Loader | 0.17.3 | `gradle.properties` |
| Fabric API | 0.116.18+1.21.1 | latest 1.21.1 build at time of Stage 1 |
| Mappings | Yarn 1.21.1+build.3 | |
| Fabric Loom | 1.11.8 | compatible with Gradle 8.14 |
| Gradle | 8.14.3 (wrapper) | |
| Java | 21 (toolchain) | |
| Tests | JUnit 5.11.3 (pure model tests) | |

Source sets are split (`loom.splitEnvironmentSourceSets()`): `src/main` (common/server) and
`src/client` (rendering, dev screenshot tour). No dependencies beyond Fabric API.

## 2. Package map

```
dev.underdeep
├── Underdeep                     common entry point (config, registries, commands, self-test, world-state init)
├── config/UnderdeepConfig        config/underdeep.json (Gson)
├── registry/ModBlocks            foundational blocks (+ block items, creative tab entries)
├── registry/ModWood, WoodSet     Stage 2 wood sets (19 blocks each) and extra tree species
├── registry/ModPlants, ModItems  Stage 2 flora, fungi, aquatic life, crops, minerals; foods, herbs, remedies, minerals
├── registry/ModTags, Reg         soil/habitat/protection tags; registration helpers + render-layer lists
├── ecology/                      Species, EcologyRegistry (metadata of every species), RegionEcology (regional
│                                 summary), EcologyState (persistent harvest pressure), Habitats (Stage 3 query API)
├── block/plant/                  reusable plant framework: Env, GrowthRule, Spreading, ParticleGate and block
│                                 classes (EcoPlant, Blooming, Harvestable, Submerged, Floating, Hanging*, AquaticStem,
│                                 EcoCrop, AquaticCrop, EcoSapling, SporePuff, Snapjaw, EphemeralFungus, ShelfFungus,
│                                 FungalSoil, RottingLog, ResinKnot, CrystalGrower)
├── item/RemedyItem               simple medicines
├── wildlife/                     Stage 3: CreatureSpecies (species data), WildlifeRegistry (60 species, entity types,
│                                 attributes), UnderdeepCreature (single TameableEntity for all species), Personality,
│                                 Rhythms (activity cycles), Voices (pitched vanilla sounds), WildlifeItems, WildlifeBlocks
│                                 (nests, burrows, eggs), WildlifeJournal (per-player field journal)
├── wildlife/ai/CreatureGoals     shared goal library composed per species (forage, hunt, flee, herd, territory, ...)
├── wildlife/population/          RegionWildlife (pure abstract population + aggregate model), WildlifeManager
│                                 (per-world ledger, spawner, tiers, reconciliation, taming, herds), Companions
├── registry/ModWorldgen          chunk generator, biome source, features, placement modifiers
├── block/GlowcapBlock
├── command/UnderdeepCommands     /underdeep diagnostics (permission-gated)
├── debug/SelfTest                dedicated-server automated test (system property gated)
├── debug/GenerationBenchmark     detached terrain-stage benchmark + chunk hashing
├── mixin/MinecraftServerMixin    spawn selection for Underdeep worlds
├── mixin/NetherPortalMixin       Nether portal toggle
├── mixin/DimensionOptionsRegistryHolderMixin  Underdeep overworld counts as a stable (non-experimental) setup
└── world
    ├── UnderdeepWorlds           binds seed → terrain model per world
    ├── noise/                    Hashing (SplitMix), PerlinNoise, FractalNoise — pure Java
    ├── region/                   RegionType, TerrainProfile, RegionEnvironment, RegionMap, RegionSample, RegionId
    ├── terrain/                  CavernLayer, CavernGraph, TerrainColumn, UnderdeepTerrain — pure Java
    ├── gen/                      UnderdeepChunkGenerator, UnderdeepBiomeSource, UnderdeepBiomes,
    │                             BlockPalette, SurfacePainter, InitialPopulation
    ├── feature/                  CaveSurfacePlacement, StartZonePlacement, CrystalSpire, HangingRoot, OreOutcrop,
    │                             Stage 2: UnderdeepTreeFeature (9 species), GiantFungusFeature, FallenLogFeature,
    │                             CoralClusterFeature, PlantBuilder (safe commit + leaf distances), SpeciesFeatureConfig
    ├── analysis/                 RegionReport, SiteType, SiteAnalyzer, SiteCandidate, ConnectivityAnalyzer
    ├── spawn/SpawnFinder
    ├── data/UnderdeepWorldState  persistent per-world data (data/underdeep.dat)
    └── debug/TerrainPreview      PNG renders straight from the terrain model
client: UnderdeepClient (render layers, dimension effects), ScreenshotTour and CreateWorldTest (dev only),
        creature/CreatureModel (JSON-built model + role-based procedural animation), CreatureRenderers
tools/analysis/world_diff.py      block-level diff of two saved worlds (determinism checks)
tools/worldgen/generate_data.py   generates biome / feature / preset JSON
tools/assets/generate_textures.py generates Stage 1 textures and icon
tools/assets/generate_assets.py   Stage 1.5 block assets
tools/assets/stage2_assets.py     Stage 2 textures (art.py), models, blockstates, loot, recipes, tags, names, hints
tools/assets/creatures.py         Stage 3 creature models (JSON), entity textures, loot, items, recipes, tags, names
scripts/selftest.sh, scripts/visual_tour.sh
```

## 3. World preset and dimension

- World preset `underdeep:underdeep` (`data/underdeep/worldgen/world_preset/underdeep.json`), listed in
  `#minecraft:normal` so it appears in the "World Type" button. Dedicated servers use
  `level-type=underdeep:underdeep` in `server.properties`.
- The overworld uses dimension type `underdeep:underdeep`: y −64..319 (384 high, same memory profile
  as vanilla overworld), `has_skylight=false`, `has_ceiling=true`, `ambient_light=0.1`, beds work,
  monsters spawn at block light 0 only. Client effects `underdeep:underdeep` (registered with Fabric
  `DimensionRenderingRegistry`): no sky, no clouds, fog colour = biome fog colour (per-region haze).
- Nether and End dimensions are kept in the preset (vanilla generators) so they can be re-enabled.
  Nether portals cannot be lit in Underdeep worlds unless `netherPortalsInUnderdeep` is true. Underdeep
  biomes are not in any vanilla structure biome tag, so no strongholds (and no End portals) generate.
- Vanilla worlds are untouched: everything is keyed on the generator type.

## 4. Terrain model (`world/terrain`)

The terrain is a **pure, deterministic function** of (seed, x, y, z). Nothing reads generated chunks,
so output is independent of chunk generation order, identical across restarts, and can be queried for
unloaded areas by analysis tools and future simulation systems.

### 4.1 Layers
`CavernLayer.UPPER` (floor base y142, fluid level y112 = sea level) and `CavernLayer.DEEP` (floor base
y−8, lava/water level y−44). Each layer has its own region map and cavern graph. A per-column
*separation* height (upper floor − 8) splits the column into the two layers for biomes, palettes and
fluids. Deep ceilings are softly capped 6 blocks below the separation, leaving a ≥14-block crust.

### 4.2 Regions (`RegionMap`, Stage 1.5)
15 ecosystems (`RegionType`, with `Danger` and `Lighting` classes). Each layer is tiled by small Voronoi cells
(112 blocks upper, 160 deep) domain-warped at two scales (amplitudes 75 and 18). Each cell takes the ecosystem
whose prototype is nearest to the cell's climate (temperature, moisture, mineral — each normalised to a uniform
[-1, 1] via the normal CDF of its measured spread, plus per-cell jitter) minus a per-ecosystem bias solved at
construction so each ecosystem reaches a target share. Neighbouring cells usually agree, giving contiguous
regions of varied size. Guarantees: origin cell and neighbours within 95 blocks are Rootwild; high/extreme-danger
ecosystems are penalised within 850 blocks of the origin; every ecosystem of the layer occurs within ~2300
blocks (a missing one is planted as a 5-cell cluster at the cell whose climate suits it best). `RegionSample`
gives the primary type, `RegionId`, blend weights (blend radius 34 blocks) and the distance to the nearest
border with a *different* ecosystem. `neighbourTypes(cell)` lists adjacent ecosystems.

### 4.3 Cavern graph (`CavernGraph`) — connectivity by construction
Each layer is divided into graph cells (176 / 224 blocks). Every cell owns one chamber node (jittered,
rotated ellipse, region-scaled radius/height; ~13 % are "colossal" ×2.1 radius). Every node has
tunnels to its east and south neighbours (plus random diagonals), so each layer is a connected grid by
construction. Chamber outlines are perturbed by lobe noise and coordinates are domain-warped so the
graph never looks like a grid. Openness ("how much open height the skeleton requires here") is the
max over chambers (domes), tunnels and noise-driven lobes adjacent to chambers.

Vertical shafts (per deep graph cell, probability = 3.2 × the upper region's `shafts` value ≈ 32–38 %, 0 in Flooded Depths) connect a deep
chamber to the nearest upper chamber via a shaft chamber and feeder tunnel; ~65 % have a walkable
spiral ledge (pitch 22). Shafts open only where the upper floor is dry.

### 4.4 Column model (`TerrainColumn`, `UnderdeepTerrain#column`)
Per block column (cached thread-locally): region samples and blended profiles; per layer floor and
ceiling (base undulation + hills/mountains scaled by chamber-ness and cavern height, terracing,
stalagmites/stalactites, hanging ceiling ridges); rivers (lowland valleys and channels that carve
their own tunnels at the water table); ravines (dry floors only, never below the water table);
abyssal pits down to the deep lava sea; perched ponds (constant level per pond, guaranteed rim,
noise suppressed nearby); shafts; pillar and bridge parameters.

### 4.4b Sub-regional variants
`UnderdeepTerrain#variantAt` (noise, ~120-block features) assigns 0 open / 1 typical / 2 dense-rugged /
3 lake-wet. Variants scale hills, add/enlarge ponds and filter features through `underdeep:variant`.

### 4.4c Landmarks and formations (`Landmarks`, `UnderdeepTerrain#formation`)
Landmark cells (230 upper / 320 deep); a landmark is placed only inside a chamber at least 32 blocks tall.
Kinds by ecosystem: colossal tree, giant mushroom/fungus, crystal cluster, arch (single/crossing), fossil
skeleton, volcano (lava crater). Built from tapered capsules, ellipsoids and tori; ~13 % titan-scale. Pillars
(leaning, lobed), bridges and landmarks produce a `FormationHit` (density + material code + region); they only
fill open space. `BlockPalette.formation` maps code × region to blocks.

### 4.5 Density and materials
`openDensity = max(min(y-floor, ceiling-y) per layer, shaft) + roughness·amp`, then winding tunnels
(two-noise "spaghetti", only in bands that cannot connect differing fluid domains). Formations
(pillars, bridges/arches, landmarks) are evaluated separately; `Material.FORMATION` where they fill open
space. Basins (Crimson) sink chamber floors towards their centres; Everfrost fluid surfaces become ice
(`Material.ICE`). `> 0` is
open. `classify` adds bedrock (floor y−64..−60, roof y315..319), sealed rock above y302 and below
y−58, and fluids: pond fluid; upper-layer water/lava (Ashen) at or below y112; deep water/lava
(Abyss/Ashen) at or below y−44. Fluid rules are designed so fluid never sits beside air at the same
height (no leaks at generation): see DECISIONS D-007.

### 4.6 Chunk generation (`UnderdeepChunkGenerator`)
- `populateNoise`: 256 exact columns + 3D noise on a 4-block grid trilinearly interpolated
  (roughness only near surfaces, tunnels only where allowed). Rock comes from `BlockPalette`
  (region strata bands). Runs on the worldgen worker pool; sections locked like vanilla.
- `buildSurface`: `SurfacePainter` paints floors (grass, moss, mycelium, nylium, sand/clay under water,
  ash, sculk...) and ceilings per region inside the chunk only.
- `carve`: intentionally empty (caves are in the model).
- Features: the vanilla biome feature pipeline. Placement uses `underdeep:cave_surface` (scans a column
  from a random height to a real floor / ceiling / wall / lake bed / water surface) and
  `underdeep:start_zone` (start cavern guarantees).
- `populateEntities`: `InitialPopulation` places herds on real cavern floors (vanilla uses the
  heightmap, which is the roof). Stage 3 replaces this with the wildlife system.
- `getHeight` returns the roof; `getColumnSample` returns exact (non-interpolated) unpainted blocks.

### 4.7 Seed binding (`UnderdeepWorlds`)
Vanilla never gives `BiomeSource#getBiome` the seed. `UnderdeepChunkGenerator#createStructurePlacementCalculator`
(called once when a world's chunk manager is built, with the seed and NoiseConfig) registers the terrain
model in a weak identity map keyed by that world's `MultiNoiseSampler`; the biome source and generator
look it up. Terrain objects are shared per seed (immutable). No mixin is needed for this.

## 5a. Natural hostile spawning (`world/spawn/SpawnRules`)
`UnderdeepChunkGenerator#getEntitySpawnList` returns an empty monster pool for attempts that fail
`SpawnRules.allowMonster`: always inside the starter zone (radius `starterSafeRadius`=150 around the origin,
above y90); otherwise with probability `monsterSpawnChance{Low,Medium,High,Extreme}` (0 / 0.12 / 0.45 / 1.0,
config). Low-danger biomes also have empty monster lists; medium+ biomes carry spawn costs (energy budget
0.08 / 0.12 / 0.16, charge 0.7) to cap local density. Only natural spawning is affected.

## 5b. Natural lighting
Surface painter places pools of light-emitting ground cover with density by `Lighting` class (bright
≈10–36 %, moderate 2–12 %, dark ≈1–3 % of dry floor tops, modulated by a pool noise); plus glowing plants
(features), glowing leaves and formation materials, star-stone ceilings (Starlight).

## 5. Spawn
`MinecraftServerMixin` replaces `setupSpawn` for Underdeep worlds: `SpawnFinder` spirals out from the
origin over generated chunks and accepts the first position whose 3×3 footprint is solid, dry,
lava/magma-free with 3 blocks headroom, then plants glowcaps around it so the arrival point is lit.
Because the dimension has no skylight, vanilla places every (re)spawning player exactly at the world
spawn and moves them up until free — all players spawn safely. Bonus chest is supported.

## 5c. Living ecosystems (Stage 2)

**Data.** `ecology/EcologyRegistry` lists every species and regional resource (95 entries) with habitats
(`RegionType`s), rarity, edibility, light/water/climate preference, ecological roles (`FORAGE`, `FRUIT`,
`SEEDS`, `NECTAR`, `FUNGAL_FOOD`, `AQUATIC_FORAGE`, `SHELTER`, `NEST_SITE`, `LIGHT_SOURCE`, `HAZARD`,
`MINERAL_FOOD`), reproduction mode, renewability, typical abundance, block ids and yield. Pure Java,
unit-tested. Species ids are persistent identifiers (append-only).

**Plant framework (`block/plant`).** Behaviour is configured per block at registration:
- `GrowthRule` = light band + climate (cold/temperate/hot from the ecosystem, or the biome temperature
  outside Underdeep) + at most one need (water beside, heat nearby, faster near heat, no heat). `factor()`
  returns 0 (dormant) … 1 (ideal). Tooltips describe the conditions.
- Growth uses vanilla random ticks only; no block entities for plants. Every environmental scan is a
  small box evaluated after the random chance roll (`Env`).
- `Spreading.trySpread`: places only into free air/still water accepted by `canPlaceAt`, never replaces
  blocks, stops at a per-species density cap (config `spreadDensityLimit`), never within 2 blocks of
  `#underdeep:growth_blockers` (player-made blocks, farmland, paths, storage, redstone), only in loaded
  chunks, radius ≤ 4. Outside its native ecosystem a species spreads at ¼ rate; spreading and
  environmental processes run only in Underdeep worlds (`Env.spreadingEnabled`) and can be disabled or
  scaled in `config/underdeep.json`.
- Trees (`UnderdeepTreeFeature`) are planned in a `PlantBuilder` and committed only if the trunk core is
  free; wood and leaves replace only air/plants/water, so a growing sapling never overwrites player
  blocks. Leaf distances are computed like vanilla (decay works).

**Processes** (all random-tick driven, bounded): fungal soil creep (Fungal Expanse only), mushroom
colony spreading, temporary lanterncap blooms that sprout/brighten/fade, puffball spore releases,
flowers that open/close by light/heat/water/cold/energy (state-dependent real block light), fruit and
herb regrowth after harvest, aquatic spreading (glowpads, submerged plants), decay of fallen logs into
fungi, heat-dependent growth, crystal regrowth on budding glimmer and charged stormstone, renewable resin.

**Unloaded areas.** Nothing grows while chunks are unloaded or the server is off; there is no catch-up
growth (no duplication). Abstract regional state lives in `EcologyState` (per `RegionId` compound
`ecology`: harvest counts `h.<species>`, `last` game time) and the deterministic `RegionEcology`
summary (dominant species, diversity, habitats, food/water/renewable/productivity scores, danger,
supported wildlife guilds, minerals) computed from data — never stored per block.

**Stage 3 integration (how wildlife should use this).**
1. Choose which species a region can hold from `RegionEcology.of(type, regionId).wildlife()` (guilds)
   and scale abstract population capacity by `productivity()` / `foodAvailability()`.
2. Lower capacity by recorded harvest pressure (`EcologyState.harvests`) and later by predation.
3. Creature AI finds food/shelter/nests with `Habitats.findNearest(world, pos, r, v, Habitats.role(Role.X))`
   (bounded ≤16 blocks; call when a goal starts, not every tick) and `Habitats.speciesOf(state)`.
4. Herbivores eat `FORAGE`/`FRUIT` species (bushes reset to age 1 like a harvest), pollinators visit
   `NECTAR`, fungivores `FUNGAL_FOOD`, aquatic grazers `AQUATIC_FORAGE`; nesting creatures use
   `NEST_SITE`; light-seeking creatures gather at `LIGHT_SOURCE`; `HAZARD` species should be avoided.
5. Settlements (Stage 6) use the same summary for agricultural productivity and regional trade goods
   (`minerals()`, renewable yields).

## 5d. Wildlife and ecological simulation (Stage 3)

**Species.** `CreatureSpecies` holds everything that differs between species: ecosystems, size, stats,
locomotion (land, climber, aquatic, amphibious, flyer, glider, hover), diet (mapped to Stage 2
`Species.Role` food roles), social structure (solitary … herd, pack, school, swarm, colony), temperament,
hunting style (pursuit, stalk, ambush, pack, aquatic, swoop) and prey list, activity cycle, taming method
and food, mount type, nest type, density, rarity and renewable product. `WildlifeRegistry` defines 60
species and registers one `EntityType<UnderdeepCreature>` per species (D-033).

**Entity.** `UnderdeepCreature extends TameableEntity`. Navigation and move control follow the locomotion
(swim/amphibious/bird/spider/ground navigation; aquatic and flight move controls). Needs (hunger, tiredness,
loneliness) update every 20 ticks; a deterministic `Personality` from the UUID (curiosity, timidity,
aggression, sociability, playfulness, boldness, loyalty) modulates flee distance, play, curiosity and
taming speed. Tracked state (idle, eat, sleep, warn, attack, play, drink, alert) drives client animation.
NBT keys `UdHunger`, `UdTired`, `UdHomeRegion`, `UdHome`, `UdHarness`, `UdSheared`, `UdVariant`, `UdPack`
(append-only).

**Behaviour.** `CreatureGoals.install` composes goals by species: swim, sit, flee (predators of the species,
players by temperament, herd alarms; stalkers/ambushers noticed only close up), escape danger, territory
(warning posture and call first; a short charge only if the intruder presses closer; never far from home),
melee attack (predators sprint), hunt (style-specific approach, hands over to the attack; kills satiate and
start a long cooldown), ambush players in dangerous ecosystems (never in Peaceful), follow owner, mate,
follow parent, tempt, forage (Stage 2 food plants by diet; harvestables reset to an earlier age so they
regrow, nectar feeders pollinate, grass/ferns rarely removed; never crops, never inside protected/built
areas), rest/sleep (shelter, home; hunger overrides rest), herd cohesion around a leader, socialising,
nesting, scavenging dropped meat (not equipment), curiosity toward players, play, drinking, and
locomotion-specific wandering. Chases end after ~12 s without closing in (`limitChase`).

**Populations and tiers (D-034, D-035).** `RegionWildlife` (NBT key `wildlife` in `regionData`) stores
species → count, disturbance, last update and counters. `capacity()` is deterministic per region
(density × food availability from `RegionEcology` × disturbance, ±25 % regional variation; rare species
0 or 1). `WildlifeManager` (one per Underdeep world) keeps the ledger of active wild entities per region
and species. Invariant: active ≤ count. The spawner (every 20 ticks, round-robin over players) materialises
groups near players from their current region's pool while under the per-player and global caps; wild
entities are not saved and return to the pool when unloaded or > 96 blocks from every player; deaths,
births, taming and naming adjust counts. Regions without active entities are stepped by the aggregate
model (predation, logistic growth, decline, emigration to compatible neighbouring regions with exact
conservation). Loaded animals that settle in another region transfer their home region (migration on foot).

**Taming and riding.** Four methods (feeding, patience, rescue, offering; details private). Taming
removes the animal from the wild pool. Ownership is by UUID (`ownedBy`), so it survives the owner being
offline, in another dimension or dead. Owner-only commands (sit/follow), owner friendly-fire immunity
unless sneaking, rider immunity, companions follow their owner (teleporting when > 20 blocks behind, like vanilla pets) unless told to stay. Mounts need a harness; only
the owner controls a ridden mount (movement is client-side as for vanilla horses). Pack animals carry an
18-slot inventory (saved, dropped on death). Companions have small helper behaviours (`Companions`).

**Resources.** Loot tables (looting-aware, cooked when on fire), renewable products (milk, shearing, shed
items dropped near players), eggs that hatch into juveniles (egg block entity remembers species, home
region, wildness and owner). Nests/burrows are placed only on natural support outside protected areas.

**Client.** Models are JSON (`assets/underdeep/creatures/<id>.json`, D-037) built into `TexturedModelData`
at startup; `CreatureModel` animates by part role (gaits for quadruped/hexapod/crab/biped, wings, fins,
tails, sleep and warning poses, juvenile scale) and emissive parts use a glow layer.

**Vanilla mobs (D-036).** In Underdeep worlds vanilla passive/water creatures no longer spawn and
`InitialPopulation` is disabled while `vanillaMobReplacement` is on; vanilla monsters spawn at
`vanillaMonsterShare` of their previous rate. Vanilla worlds are untouched.

**Configuration** (`config/underdeep.json`): `wildlifeEnabled`, `wildlifeDensity`,
`wildlifeDensityByEcosystem`, `predatorAbundance`, `wildlifeReproduction`, `wildlifeMigration`,
`wildlifeSimInterval`, `wildlifeSimRegionsPerUpdate`, `maxWildlifePerPlayer`, `maxWildlifeTotal`,
`wildlifeDetailDistance`, `wildlifeDespawnDistance`, `wildlifeVegetationConsumption`,
`rareCreatureFrequency`, `tamingEnabled`, `vanillaMobReplacement`, `vanillaMonsterShare`, `huntingDisturbance`.

**Interfaces for Stages 4–6.** Settlements can read `RegionWildlife` counts (game availability, hunting
pressure via `disturbance`), call `WildlifeManager.region(id)` for any region without loading chunks, use
`CreatureSpecies` diet/temperament for herding/livestock, treat tamed creatures as livestock (non-wild
creatures breed freely, `allowBirth`), and add villager-owned animals via `leaveWildAsTamed`. Caravans
can use pack animals (`Mount.PACK`). Predator pressure on routes = predator counts in the regions crossed.

## 6. Contracts for future stages

| Need | Contract | Notes |
|---|---|---|
| Region identity / persistence | `RegionId` (packed long), `RegionType` ids | append-only |
| Environment & habitat | `RegionType#environment()` → `RegionEnvironment` (temperature, humidity, light, water, danger, fertility, habitat tags, resource tags) | species definitions (Stage 3) match on habitat tags |
| Point queries | `RegionReport.at(terrain, x, y, z)` | layer, floor/ceiling, fluid, border distance; no chunk loads |
| Settlement placement | `SiteAnalyzer.evaluate/findSites`, `SiteType` requirements, `RegionType#settlementAffinity` | pure model; bounded cost; does not see features |
| Roads / trade routes | `CavernGraph` nodes and `edgesOf` (tunnels) | natural road network; caravans can store progress along edges |
| Regional simulation storage | `UnderdeepWorldState#regionData(regionId)` | one small NBT compound per region, never per block |
| Terrain inspection | `UnderdeepTerrain#column`, `materialAt`, `regionAt` | thread-safe |
| Connectivity validation | `ConnectivityAnalyzer` | diagnostics/tests only (heavy) |
| Ecosystem distribution | `RegionDistribution.analyze` | region sizes, shares, counts within radii, transition frequency |
| Survival traversal | `Accessibility.analyze` | walk/swim reachability, path vs straight distances, walkable borders |
| Danger / lighting | `RegionType#danger()`, `#lighting()`, biome tags `underdeep:danger_*` | spawning, future ecology |
| Neighbouring ecosystems | `RegionMap#neighbourTypes` | for migration / trade-route planning |
| Landmarks | `UnderdeepTerrain#landmarks(layer)` | positions & kinds for future discovery/settlement logic |
| Species metadata | `EcologyRegistry` (`byId`, `byBlock`, `inRegion`), `Species` | append-only ids |
| Regional ecology | `RegionEcology.of(type, regionId)` | deterministic, no storage |
| Harvest pressure | `EcologyState.recordHarvest / harvests` | NBT `ecology` per region |
| Habitat search | `Habitats.speciesOf`, `findNearest`, `role` | bounded block scans |
| Wildlife species | `WildlifeRegistry` (`all`, `byId`, `of(type)`, `type(id)`), `CreatureSpecies` | append-only ids (stored in NBT) |
| Regional wildlife | `WildlifeManager.get(world).region(regionId)` → `RegionWildlife` (counts, disturbance) | NBT `wildlife` per region; no chunk loads |
| Ownership | `UnderdeepCreature.isWild()`, `ownedBy(player)`, `leaveWildAsTamed(owner)` | wild ⇒ region-owned, never saved as entity |

**Extending regions:** add a `RegionType` entry at the end with a `TerrainProfile` and environment,
add it to a layer's region list and climate prototypes in `RegionMap`, add a biome in
`tools/worldgen/generate_data.py`, palettes in `BlockPalette`/`SurfacePainter`, regenerate data, bump
`GENERATOR_VERSION` (existing worlds will show seams at newly generated chunk borders).

**Wildlife population contract:** implemented in Stage 3, see §5d.

## 7. Performance design
- Column values are computed once per block column; 3D noise on a 4-block grid; roughness evaluated
  only near surfaces. Thread-local caches (columns 512, nodes 1024, edges 512, shafts 256, region types 128).
- No cross-chunk writes outside the vanilla 3×3 feature window; feature radii are small.
- No forced chunk loading; analysis tools work on the pure model.
- Measured numbers are in `TESTING.md`.

## 8. Diagnostics
`/underdeep info | region [pos] | locate <region> | spawncheck | cavern [pos] | sites <type> [radius] |
perf [chunks] | render [radius] | connectivity [radiusChunks] | ecology [pos]` — permission level from config (default 2).
Self-test suite `SUITE=stage2 scripts/selftest.sh …` (property `underdeep.selftest.suite=stage2`) skips the slow
Stage 1.5 terrain analyses and runs `Stage2Checks` (resources, recipes, generation census, growth,
harvest, spreading bounds, protection, persistence); `scripts/stage2_matrix.sh` runs it across seeds.
`Stage3Checks` adds wildlife registration, distribution, behaviour (sealed test plots, entities ticked
directly = accelerated), taming, products, population integrity, aggregate simulation and persistence
checks; `-Dunderdeep.zoo=true` (env `ZOO=true`) builds a close-up exhibit of every species for the
screenshot tour. `UnderdeepCreature.testObserver` (self-test only) makes creatures behave as if a player
were adjacent.
Mob study (`-Dunderdeep.mobstudy=<dir>`, `scripts/mob_study.sh`) measures natural spawning with a real
client connected to the dedicated server.
`F3` debug screen shows layer, region and cavern heights. Self-test (`-Dunderdeep.selftest=<dir>`) and
screenshot tour (`-Dunderdeep.tour=<file>`) are inert unless those properties are set.
