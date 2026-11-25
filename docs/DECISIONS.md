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
