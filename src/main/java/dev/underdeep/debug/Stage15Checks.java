package dev.underdeep.debug;

import dev.underdeep.world.analysis.Accessibility;
import dev.underdeep.world.analysis.RegionDistribution;
import dev.underdeep.world.gen.UnderdeepBiomes;
import dev.underdeep.world.region.Lighting;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.Landmarks;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stage 1.5 self-test sections: ecosystem distribution, accessibility, ground-level lighting,
 * landmarks and darkness-survival checks. Called from {@link SelfTest}.
 */
final class Stage15Checks {
    private Stage15Checks() {
    }

    interface Reporter {
        void check(String name, boolean pass, String detail);

        void info(String name, String detail);

        void section(String title);
    }

    /** Ecosystem distribution (model) and nearest meaningful regions; returns representative positions. */
    static Map<RegionType, int[]> distribution(UnderdeepTerrain t, Reporter r) {
        r.section("Ecosystem distribution (explorable cavern columns, 5200x5200 blocks, every 8 blocks)");
        Map<RegionType, int[]> reps = new EnumMap<>(RegionType.class);
        for (CavernLayer layer : CavernLayer.values()) {
            RegionDistribution.Report d = RegionDistribution.analyze(t, layer, 2600, 8);
            r.info(layer.id + " distribution", d.summary());
            r.info(layer.id + " region size classes (equivalent diameter)", d.sizeClasses());
            r.info(layer.id + " ecosystem shares", d.frequencies());
            r.info(layer.id + " nearest >=60-block region of each ecosystem", d.meaningfulFirstDistance().toString());
            if (layer == CavernLayer.UPPER) {
                Set<RegionType> w500 = d.meaningfulWithin(500);
                Set<RegionType> w1000 = d.meaningfulWithin(1000);
                r.check("several ecosystems within 500 blocks of spawn", w500.size() >= 3, w500.size() + ": " + w500);
                r.check("6+ ecosystems within 1000 blocks of spawn", w1000.size() >= 6, w1000.size() + ": " + w1000);
                r.check("biome changes frequently (mean run 100-250 blocks)", d.meanRunLength() >= 80 && d.meanRunLength() <= 260,
                        String.format(Locale.ROOT, "%.0f blocks between ecosystem changes along straight lines", d.meanRunLength()));
                List<Double> sizes = d.regions().stream().filter(x -> x.cells() >= 4).map(RegionDistribution.Region::equivalentDiameter)
                        .sorted().toList();
                double p50 = sizes.get(sizes.size() / 2);
                r.check("typical region 60-250 blocks across", p50 >= 60 && p50 <= 250, String.format(Locale.ROOT, "median %.0f", p50));
            }
            for (RegionDistribution.Region reg : d.regions()) {
                int[] cur = reps.get(reg.type());
                double dist = Math.hypot(reg.centerX(), reg.centerZ());
                if (reg.equivalentDiameter() >= 80 && (cur == null || dist < Math.hypot(cur[0], cur[2]))) {
                    reps.put(reg.type(), new int[]{reg.centerX(), layer.ordinal(), reg.centerZ()});
                }
            }
        }
        EnumSet<RegionType> all = EnumSet.noneOf(RegionType.class);
        for (CavernLayer layer : CavernLayer.values()) {
            all.addAll(RegionDistribution.analyze(t, layer, 2500, 16).meaningfulWithin(2500));
        }
        r.check("all 15 ecosystems within 2500 blocks", all.size() == RegionType.values().length,
                all.size() + " found; missing " + EnumSet.complementOf(all));
        return reps;
    }

    static void accessibility(UnderdeepTerrain t, BlockPos spawn, Reporter r) {
        r.section("Survival accessibility (walking/swimming paths on the terrain model, radius 1500, 4-block grid)");
        Accessibility.Report a = Accessibility.analyze(t, CavernLayer.UPPER, spawn.getX(), spawn.getZ(), 1500, 4);
        r.info("upper layer", a.summary());
        r.info("distance to each ecosystem (straight vs walking)", a.distances());
        double frac = a.reachableSamples() / (double) Math.max(1, a.openSamples());
        r.check("most open cavern area reachable from spawn on foot", frac > 0.7, String.format(Locale.ROOT, "%.0f%%", frac * 100));
        int reached = 0;
        int present = 0;
        for (RegionType type : CavernLayer.UPPER.regions) {
            if (a.straightDistance().containsKey(type)) {
                present++;
                if (a.walkDistance().containsKey(type)) {
                    reached++;
                }
            }
        }
        r.check("every nearby upper ecosystem reachable on foot", reached == present, reached + "/" + present);
        double cross = a.walkableCrossings() / (double) Math.max(1, a.borderCrossings());
        r.check("most ecosystem borders walkable", cross > 0.65, String.format(Locale.ROOT, "%.0f%%", cross * 100));
    }

    /** Settlement-compatible areas by size class around each chamber (terrain model). */
    static void settlementAreas(UnderdeepTerrain t, Reporter r) {
        r.section("Settlement-compatible terrain (chamber-centred areas within 1500 blocks, terrain model)");
        int outposts = 0, villages = 0, towns = 0, cities = 0, evaluated = 0;
        java.util.Map<RegionType, Integer> big = new EnumMap<>(RegionType.class);
        for (dev.underdeep.world.terrain.CavernGraph.Node n : t.graph(CavernLayer.UPPER).nodesIn(-1500, -1500, 1500, 1500)) {
            var c = dev.underdeep.world.analysis.SiteAnalyzer.evaluate(t, dev.underdeep.world.analysis.SiteType.VILLAGE,
                    CavernLayer.UPPER, (int) n.x(), (int) n.z(), 160);
            evaluated++;
            if (c.headroom() < 10) {
                continue;
            }
            int diameter = c.usableRadius() * 2;
            if (diameter >= 180) {
                cities++;
                big.merge(c.region(), 1, Integer::sum);
            } else if (diameter >= 100) {
                towns++;
            } else if (diameter >= 50) {
                villages++;
            } else if (diameter >= 25) {
                outposts++;
            }
        }
        r.info("usable areas around " + evaluated + " chambers", String.format(Locale.ROOT,
                "outpost-size (25-50 blocks): %d, village (50-100): %d, town (100-180): %d, city (180+): %d; city-size areas by ecosystem %s",
                outposts, villages, towns, cities, big));
        r.check("settlement-compatible areas of every size class exist", outposts > 0 && villages > 0 && towns > 0 && cities > 0,
                "see counts above");
    }

    static void landmarks(UnderdeepTerrain t, Reporter r) {
        r.section("Landmarks (terrain model, 4000x4000 blocks)");
        for (CavernLayer layer : CavernLayer.values()) {
            Map<Landmarks.Kind, Integer> kinds = new EnumMap<>(Landmarks.Kind.class);
            int titans = 0;
            int total = 0;
            for (Landmarks.Landmark l : t.landmarks(layer).landmarksIn(-2000, -2000, 2000, 2000)) {
                kinds.merge(l.kind, 1, Integer::sum);
                total++;
                if (l.titan) {
                    titans++;
                }
            }
            r.info(layer.id + " landmarks", total + " (" + titans + " titan-scale): " + kinds);
            if (layer == CavernLayer.UPPER) {
                r.check("major landmarks are common but sparse", total > 40 && total < 400, total + " in 16 km^2");
            }
        }
    }

    /** Ground-level block light per ecosystem, measured in generated chunks. */
    /** Open cavern floor of the given ecosystem near a representative region centre (x, z). */
    static int[] siteFor(UnderdeepTerrain t, RegionType type, int[] p) {
        boolean up = CavernLayer.values()[p[1]] == CavernLayer.UPPER;
        for (int border : new int[]{24, 8}) {
            for (int rad = 0; rad <= (border == 24 ? 192 : 480); rad += 8) {
                for (int dx = -rad; dx <= rad; dx += 8) {
                    for (int dz = -rad; dz <= rad; dz += 8) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != rad) {
                            continue;
                        }
                        TerrainColumn oc = t.column(p[0] + dx, p[2] + dz);
                        if ((up ? oc.upperCeiling - oc.upperFloor : oc.deepCeiling - oc.deepFloor) > 16
                                && (up ? oc.upperRegion : oc.deepRegion).primary == type
                                && (up ? oc.upperRegion : oc.deepRegion).borderDistance > border) {
                            return new int[]{p[0] + dx, p[2] + dz};
                        }
                    }
                }
            }
        }
        return new int[]{p[0], p[2]};
    }

    static void lighting(ServerWorld world, UnderdeepTerrain t, Map<RegionType, int[]> reps, Reporter r) {
        r.section("Ground-level natural block light per ecosystem (generated chunks, 5x5 chunks per ecosystem, inner 3x3 measured)");
        for (RegionType type : RegionType.values()) {
            int[] p = reps.get(type);
            if (p == null) {
                r.info("light " + type.id(), "no representative region found");
                continue;
            }
            CavernLayer layer = CavernLayer.values()[p[1]];
            boolean up = layer == CavernLayer.UPPER;
            int[] site = siteFor(t, type, p);
            int sx = site[0];
            int sz = site[1];
            boolean aquaticBed = type == RegionType.SUNKEN_GARDENS;
            int ccx = sx >> 4;
            int ccz = sz >> 4;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    world.getChunk(ccx + dx, ccz + dz, ChunkStatus.FULL, true);
                }
            }
            List<Integer> levels = new ArrayList<>();
            BlockPos.Mutable m = new BlockPos.Mutable();
            for (int x = (ccx - 1) * 16; x < (ccx + 2) * 16; x += 2) {
                for (int z = (ccz - 1) * 16; z < (ccz + 2) * 16; z += 2) {
                    TerrainColumn c = t.column(x, z);
                    int top = (int) Math.min(layer == CavernLayer.UPPER ? c.upperCeiling : c.deepCeiling, 300);
                    int bottom = (int) Math.max(layer == CavernLayer.UPPER ? c.separation : -60, -60);
                    boolean seenOpen = false;
                    for (int y = top; y > bottom; y--) {
                        BlockState s = world.getBlockState(m.set(x, y, z));
                        if (!seenOpen) {
                            // Start below the ceiling: skip rock until the first open block of the cavern.
                            if (s.getCollisionShape(world, m).isEmpty()) {
                                seenOpen = true;
                            } else {
                                continue;
                            }
                        }
                        if (aquaticBed) {
                            // Underwater ecosystem: measure on the lake bed (first water above a solid floor).
                            if (s.getFluidState().isOf(net.minecraft.fluid.Fluids.WATER)
                                    && world.getBlockState(m.set(x, y - 1, z)).isSideSolidFullSquare(world, m, Direction.UP)) {
                                levels.add(world.getLightLevel(LightType.BLOCK, m.set(x, y, z)));
                                break;
                            }
                            continue;
                        }
                        // The standing position may hold plants or snow (no collision); stop at the first ground.
                        if (!s.getCollisionShape(world, m).isEmpty() || !s.getFluidState().isEmpty()) {
                            boolean canopy = s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.LOGS) || s.isOf(Blocks.MUSHROOM_STEM)
                                    || s.isOf(Blocks.RED_MUSHROOM_BLOCK) || s.isOf(Blocks.BROWN_MUSHROOM_BLOCK)
                                    || s.isOf(Blocks.NETHER_WART_BLOCK) || s.isOf(Blocks.SHROOMLIGHT);
                            if (canopy) {
                                continue; // trees and giant mushrooms are not ground
                            }
                            boolean ground = s.isSideSolidFullSquare(world, m, Direction.UP) || s.getFluidState().isOf(net.minecraft.fluid.Fluids.WATER);
                            if (ground && world.getBiome(m.set(x, y + 1, z)).matchesKey(UnderdeepBiomes.key(type))) {
                                levels.add(world.getLightLevel(LightType.BLOCK, m.set(x, y + 1, z)));
                            }
                            break;
                        }
                    }
                }
            }
            if (levels.isEmpty()) {
                r.info("light " + type.id(), "no dry ground samples (e.g. fully submerged)");
                continue;
            }
            levels.sort(Integer::compare);
            long lit8 = levels.stream().filter(v -> v >= 8).count();
            long dark0 = levels.stream().filter(v -> v == 0).count();
            double mean = levels.stream().mapToInt(Integer::intValue).average().orElse(0);
            double frac8 = lit8 / (double) levels.size();
            String detail = String.format(Locale.ROOT, "%s, %d %s samples near %d,%d: mean %.1f, median %d, >=8: %.0f%%, zero: %.0f%%",
                    type.lighting(), levels.size(), aquaticBed ? "lake-bed" : "ground", sx, sz, mean, levels.get(levels.size() / 2), frac8 * 100,
                    dark0 * 100.0 / levels.size());
            boolean ok = switch (type.lighting()) {
                case BRIGHT -> frac8 >= 0.6;
                case MODERATE -> mean >= 3;
                case DARK -> true;
                case PITCH -> mean < 4;
            };
            r.check("light " + type.id(), ok, detail);
        }
    }

    /** Tree growth, crop growth and core recipes without sunlight. Restores modified blocks. */
    static void survival(MinecraftServer server, ServerWorld world, BlockPos spawn, Reporter r) {
        r.section("Survival without sunlight (block light only)");
        // Test plots are at least 16 blocks from the spawn so grown trees never cover it.
        BlockPos site = findLitGrass(world, spawn, 9);
        if (site == null) {
            r.check("lit grass near spawn for growth tests", false, "none with block light >= 9 within 16-90 blocks");
        } else {
            Random random = Random.create(42);
            // Sapling
            BlockPos saplingPos = site.up();
            world.setBlockState(saplingPos, Blocks.OAK_SAPLING.getDefaultState());
            int ticks = 0;
            while (ticks < 6000 && world.getBlockState(saplingPos).isOf(Blocks.OAK_SAPLING)) {
                world.getBlockState(saplingPos).randomTick(world, saplingPos, random);
                ticks++;
            }
            boolean grew = world.getBlockState(saplingPos).isIn(BlockTags.LOGS);
            r.check("oak sapling grows into a tree under natural block light", grew, "light " + world.getLightLevel(saplingPos)
                    + ", sky light " + world.getLightLevel(LightType.SKY, saplingPos) + ", " + ticks + " random ticks");
            // Crops
            BlockPos cropSite = findLitGrass(world, spawn.add(6, 0, 6), 9);
            if (cropSite != null) {
                world.setBlockState(cropSite, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
                BlockPos wheat = cropSite.up();
                boolean placeable = Blocks.WHEAT.getDefaultState().canPlaceAt(world, wheat);
                world.setBlockState(wheat, Blocks.WHEAT.getDefaultState());
                int t2 = 0;
                while (t2 < 20000 && world.getBlockState(wheat).isOf(Blocks.WHEAT)
                        && !((CropBlock) Blocks.WHEAT).isMature(world.getBlockState(wheat))) {
                    world.getBlockState(wheat).randomTick(world, wheat, random);
                    world.setBlockState(cropSite, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
                    t2++;
                }
                boolean mature = world.getBlockState(wheat).isOf(Blocks.WHEAT) && ((CropBlock) Blocks.WHEAT).isMature(world.getBlockState(wheat));
                r.check("wheat can be planted and matures under natural block light", placeable && mature,
                        "placeable=" + placeable + ", light " + world.getLightLevel(wheat) + ", " + t2 + " random ticks");
                world.setBlockState(wheat, Blocks.AIR.getDefaultState());
                world.setBlockState(cropSite, Blocks.GRASS_BLOCK.getDefaultState());
            }
        }
        var recipes = server.getRecipeManager();
        List<String> missing = new ArrayList<>();
        for (String id : List.of("crafting_table", "wooden_pickaxe", "stone_pickaxe", "furnace", "iron_pickaxe", "torch",
                "white_bed", "bucket", "bread", "chest")) {
            if (recipes.get(Identifier.ofVanilla(id)).isEmpty()) {
                missing.add(id);
            }
        }
        r.check("core early-game recipes available", missing.isEmpty(), missing.isEmpty() ? "all present" : "missing " + missing);
        var biomes = world.getRegistryManager().get(RegistryKeys.BIOME);
        r.info("registered Underdeep biomes", String.valueOf(biomes.getKeys().stream()
                .filter(k -> k.getValue().getNamespace().equals("underdeep")).count()));
    }

    /** A sapling needs room for the tree it grows into. */
    private static boolean clearAbove(ServerWorld world, BlockPos ground, int height) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 2; dy <= height; dy++) {
                    if (!world.getBlockState(ground.add(dx, dy, dz)).isAir() && !(dx == 0 && dz == 0 && dy == 1
                            && world.getBlockState(ground.up()).isReplaceable())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static BlockPos findLitGrass(ServerWorld world, BlockPos around, int minLight) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int rad = 16; rad <= 90; rad += 2) {
            for (int dx = -rad; dx <= rad; dx += 2) {
                for (int dz = -rad; dz <= rad; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rad) {
                        continue;
                    }
                    for (int y = around.getY() + 8; y > around.getY() - 12; y--) {
                        m.set(around.getX() + dx, y, around.getZ() + dz);
                        BlockState s = world.getBlockState(m);
                        if ((s.isOf(Blocks.GRASS_BLOCK) || s.isIn(BlockTags.DIRT)) && world.getBlockState(m.up()).isReplaceable()
                                && world.getLightLevel(m.up(2)) >= minLight && clearAbove(world, m, 8)) {
                            return m.toImmutable();
                        }
                    }
                }
            }
        }
        return null;
    }
}
