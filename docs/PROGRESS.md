# Underdeep — Progress

Factual record of what exists. Test evidence: `TESTING.md`.

## Stage 1 — Foundation and underground world generation: COMPLETE (2026-11-25)

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

## Stages 2–12
Not started.
