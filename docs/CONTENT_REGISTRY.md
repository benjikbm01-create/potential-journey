# Underdeep — Content Registry

Implemented content only (planned content lives in `MASTER_DESIGN.md`; secret content in `docs/private/`).
Persistent ids are append-only.

## World generation types

| Id | Kind | Notes |
|---|---|---|
| `underdeep:underdeep` | world preset | listed under World Type; `level-type=underdeep:underdeep` |
| `underdeep:underdeep` | dimension type | y −64..319, no skylight, ceiling, ambient light 0.06 |
| `underdeep:underdeep` | dimension effects (client) | no sky/clouds, biome fog colour |
| `underdeep:underdeep` | chunk generator | field `generator_version` (int, default current) |
| `underdeep:underdeep` | biome source | no fields; resolves biomes by key |
| `underdeep:cave_surface` | placement modifier | `surface` floor/ceiling/water_floor/water_surface/wall, `min_y`, `max_y`, `max_scan` |
| `underdeep:start_zone` | placement modifier | `radius` around origin |
| `underdeep:crystal_spire` | feature | glowing spire from floor or ceiling |
| `underdeep:hanging_root` | feature | wood/root strands from Rootwild ceilings |
| `underdeep:ore_outcrop` | feature | small ore boulder (start cavern) |

## Cavern layers (`CavernLayer`, ordinal = persistence id)

| # | Id | Floor base | Fluid level | Region cell | Graph cell |
|---|---|---|---|---|---|
| 0 | upper | 142 | 112 (water; lava in Ashen) | 704 | 176 |
| 1 | deep | −8 | −44 (lava in Abyss/Ashen, water in Crystal) | 832 | 224 |

## Regions / biomes (`RegionType`, ordinal = persistence id)

| # | Region id / biome | Layers | Terrain signature | Fluid | Stage 1 flora & features |
|---|---|---|---|---|---|
| 0 | `rootwild` | upper (spawn) | tall chambers, rolling hills, root columns, rivers, ponds | water | oak/fancy oak/dark oak/birch/azalea trees, hanging roots, glow berries, moss, grass, ferns, berries, flowers, sugar cane, mushrooms, spore blossoms, glowcaps |
| 1 | `fungal_expanse` | upper | mounded terraced floors, lower domes | water | huge mushrooms, warped & crimson huge fungi (shroomlight), nether roots/sprouts, twisting & weeping vines, glowcaps, mycelium/nylium |
| 2 | `flooded_depths` | upper | lakes below the water table, islands, waterfalls | water | seagrass, kelp, sea pickles, lily pads, dripstone, many springs, axolotls/glow squid/fish spawns |
| 3 | `ashen_hollows` | upper + deep | terraced cliffs, ridged mountains, lava rivers/pools, basalt columns | lava | basalt columns & pillars, lava deltas, glowstone, magma veins, gilded blackstone, lava falls |
| 4 | `crystal_deep` | upper + deep | very tall caverns, bridges, spires | water | luminite spires and clusters, amethyst geodes & clusters, calcite veins, dripstone |
| 5 | `the_abyss` | deep | colossal chambers, giant pillars, chasms to the lava sea | lava | sculk floors and veins, dripstone, basalt pillars, rare luminite |

All biomes: no precipitation; ambient particles / music per region; common vanilla ores at vanilla
height ranges (coal & iron & extra copper in the upper layer; gold, redstone, lapis, diamond in the
deep layer); monster rooms.

## Blocks (Stage 1 foundational)

| Id | Light | Notes |
|---|---|---|
| `underdeep:luminite_block` | 12 | crystal rock of the Crystal Deep; requires pickaxe |
| `underdeep:luminite_cluster` | 9 | directional cluster (like amethyst); drops itself |
| `underdeep:glowcap` | 10 | small glowing fungus on any sturdy floor; lights the spawn |

## Terrain structures produced by the generator (not registry content)
Chambers (incl. colossal ~13 %), tunnels/highways, winding tunnels, vertical shafts (spiral ledges in
~65 %), ravines, rivers and river tunnels, perched ponds, lakes and islands, abyssal pits, pillars,
natural bridges/arches, stalactites/stalagmites, hanging ceiling ridges, terraces, rock strata.

## Initial animals (placeholder until Stage 3)
Rootwild: sheep, cows, pigs, chickens, rabbits · Fungal Expanse: mooshrooms, pigs · Flooded Depths:
frogs, chickens.
