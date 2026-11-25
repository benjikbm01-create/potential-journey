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
