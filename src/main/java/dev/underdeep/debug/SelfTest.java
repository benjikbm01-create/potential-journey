package dev.underdeep.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.underdeep.Underdeep;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.analysis.ConnectivityAnalyzer;
import dev.underdeep.world.analysis.SiteAnalyzer;
import dev.underdeep.world.analysis.SiteCandidate;
import dev.underdeep.world.analysis.SiteType;
import dev.underdeep.world.data.UnderdeepWorldState;
import dev.underdeep.world.debug.TerrainPreview;
import dev.underdeep.world.gen.UnderdeepBiomes;
import dev.underdeep.world.gen.UnderdeepChunkGenerator;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.spawn.SpawnFinder;
import dev.underdeep.world.terrain.CavernGraph;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CaveVines;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Automated dedicated-server self-test for world generation (development only).
 * <p>
 * Enabled with {@code -Dunderdeep.selftest=<output dir>}. Optional properties:
 * {@code underdeep.selftest.phase=create|reload} and {@code underdeep.selftest.order=forward|reverse}.
 * After the server starts it runs every check, writes {@code report.md}, {@code checks.json},
 * {@code chunk_hashes_<phase>.txt}, terrain previews and a client screenshot tour file, then stops the server.
 * See docs/TESTING.md for how the scripted runs use this.
 */
public final class SelfTest {
    private SelfTest() {
    }

    private record Check(String name, String status, String detail) {
    }

    private static final List<Check> CHECKS = new ArrayList<>();
    private static final StringBuilder REPORT = new StringBuilder();

    public static void init() {
        String out = System.getProperty("underdeep.selftest");
        if (out == null) {
            return;
        }
        Underdeep.LOGGER.info("Underdeep self-test enabled, output: {}", out);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> {
            Path dir = Path.of(out);
            try {
                Files.createDirectories(dir);
                run(server, dir);
            } catch (Throwable t) {
                Underdeep.LOGGER.error("Self-test crashed", t);
                check("selftest completed", false, "exception: " + t);
            } finally {
                try {
                    writeReports(dir);
                } catch (IOException e) {
                    Underdeep.LOGGER.error("Could not write self-test report", e);
                }
                Underdeep.LOGGER.info("UNDERDEEP SELFTEST FINISHED: {} checks, {} failed", CHECKS.size(),
                        CHECKS.stream().filter(c -> c.status.equals("FAIL")).count());
                server.stop(false);
            }
        }));
    }

    private static void check(String name, boolean pass, String detail) {
        CHECKS.add(new Check(name, pass ? "PASS" : "FAIL", detail));
        REPORT.append("- **").append(pass ? "PASS" : "FAIL").append("** ").append(name).append(": ").append(detail).append('\n');
        Underdeep.LOGGER.info("[selftest] {} {}: {}", pass ? "PASS" : "FAIL", name, detail);
    }

    private static void info(String name, String detail) {
        CHECKS.add(new Check(name, "INFO", detail));
        REPORT.append("- INFO ").append(name).append(": ").append(detail).append('\n');
        Underdeep.LOGGER.info("[selftest] INFO {}: {}", name, detail);
    }

    private static void section(String title) {
        REPORT.append("\n## ").append(title).append("\n\n");
    }

    private static void run(MinecraftServer server, Path dir) throws IOException {
        String phase = System.getProperty("underdeep.selftest.phase", "create");
        String order = System.getProperty("underdeep.selftest.order", "forward");
        ServerWorld world = server.getOverworld();
        long seed = world.getSeed();
        REPORT.append("# Underdeep self-test report\n\n");
        REPORT.append(String.format(Locale.ROOT, "- seed: %d\n- phase: %s\n- chunk order: %s\n- java: %s\n- cpus: %d\n- max heap: %d MB\n",
                seed, phase, order, System.getProperty("java.version"), Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / (1024 * 1024)));

        // ------------------------------------------------------------------ preset & registries
        section("Preset and registries");
        boolean isUd = world.getChunkManager().getChunkGenerator() instanceof UnderdeepChunkGenerator;
        check("underdeep world preset active", isUd, world.getChunkManager().getChunkGenerator().getClass().getName());
        if (!isUd) {
            return;
        }
        UnderdeepTerrain t = UnderdeepWorlds.terrain(world);
        check("dimension has no skylight and has a ceiling", !world.getDimension().hasSkyLight() && world.getDimension().hasCeiling(),
                "hasSkyLight=" + world.getDimension().hasSkyLight() + " hasCeiling=" + world.getDimension().hasCeiling());
        check("vertical range -64..319", world.getBottomY() == -64 && world.getTopY() == 320,
                world.getBottomY() + ".." + (world.getTopY() - 1));
        var biomeReg = world.getRegistryManager().get(RegistryKeys.BIOME);
        int found = 0;
        for (RegionType r : RegionType.values()) {
            if (biomeReg.contains(UnderdeepBiomes.key(r))) {
                found++;
            }
        }
        check("all region biomes registered", found == RegionType.values().length, found + "/" + RegionType.values().length);
        UnderdeepWorldState state = UnderdeepWorldState.get(world);
        info("world state", "created with generator v" + state.createdWithGeneratorVersion() + " at game time " + state.createdAtGameTime());

        // ------------------------------------------------------------------ spawn
        section("Spawn");
        BlockPos spawn = world.getSpawnPos();
        SpawnFinder.Validation v = SpawnFinder.validate(world, spawn);
        check("world spawn is safe", v.safe(), spawn.toShortString() + (v.safe() ? "" : " problems: " + v.problems()));
        Box box = EntityType.PLAYER.getDimensions().getBoxAt(spawn.toBottomCenterPos());
        check("player hitbox fits at spawn", world.isSpaceEmpty(box), box.toString());
        check("spawn is in Rootwild", t.regionAt(spawn.getX(), spawn.getY(), spawn.getZ()) == RegionType.ROOTWILD,
                t.regionAt(spawn.getX(), spawn.getY(), spawn.getZ()).id());
        int skyLight = world.getLightLevel(LightType.SKY, spawn);
        check("no skylight at spawn", skyLight == 0, "sky light level " + skyLight);
        info("block light at spawn", String.valueOf(world.getLightLevel(LightType.BLOCK, spawn)));

        // ------------------------------------------------------------------ starting resources
        section("Starting area resources (within 48 blocks of spawn)");
        Map<String, Integer> counts = startResources(world, spawn, 48);
        counts.forEach((k, n) -> info("count " + k, String.valueOf(n)));
        check("wood available", counts.get("logs") >= 20, counts.get("logs") + " log/wood blocks");
        check("food available", counts.get("food_plants") + counts.get("animals") * 4 >= 6,
                counts.get("food_plants") + " food plants, " + counts.get("animals") + " animals");
        check("water available", counts.get("water") >= 10, counts.get("water") + " water blocks");
        check("stone available", counts.get("stone") >= 1000, counts.get("stone") + " exposed-or-near stone blocks");
        check("basic ores available", counts.get("coal") >= 3 && counts.get("iron") >= 3,
                "coal=" + counts.get("coal") + " iron=" + counts.get("iron") + " copper=" + counts.get("copper"));
        check("light sources near spawn", counts.get("light_sources") >= 3, counts.get("light_sources") + " light-emitting blocks");
        int open = counts.get("open_floor");
        check("space for base construction", open >= 600, open + " walkable floor columns within 48 blocks");

        // ------------------------------------------------------------------ regions
        section("Regions");
        Map<RegionType, int[]> regionPos = new EnumMap<>(RegionType.class);
        for (CavernLayer layer : CavernLayer.values()) {
            for (int r = 0; r <= 3200; r += 64) {
                for (int dx = -r; dx <= r; dx += 64) {
                    for (int dz = -r; dz <= r; dz += 64) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        RegionType rt = t.regions(layer).typeAt(dx, dz);
                        if (!regionPos.containsKey(rt)) {
                            TerrainColumn c = t.column(dx, dz);
                            int y = (int) (layer == CavernLayer.UPPER ? c.upperFloor + 2 : Math.min(c.deepFloor + 2, c.separation - 10));
                            regionPos.put(rt, new int[]{dx, y, dz, layer.ordinal()});
                        }
                    }
                }
            }
        }
        for (RegionType rt : RegionType.values()) {
            int[] p = regionPos.get(rt);
            if (p == null) {
                check("region present: " + rt.id(), false, "not found within 3200 blocks");
                continue;
            }
            var biome = world.getBiome(new BlockPos(p[0], p[1], p[2]));
            boolean match = biome.matchesKey(UnderdeepBiomes.key(rt));
            check("region present: " + rt.id(), match, String.format(Locale.ROOT, "at %d %d %d (%s layer, %.0f blocks from origin), biome %s",
                    p[0], p[1], p[2], CavernLayer.values()[p[3]].id, Math.sqrt((double) p[0] * p[0] + (double) p[2] * p[2]),
                    biome.getKey().map(k -> k.getValue().toString()).orElse("?")));
        }

        // ------------------------------------------------------------------ cavern statistics (terrain model)
        section("Cavern size statistics (terrain model, 3000x3000 blocks, every 8 blocks)");
        cavernStats(t);

        // ------------------------------------------------------------------ chunk generation & determinism
        section("Generation performance");
        BenchmarkHolder bench = new BenchmarkHolder();
        bench.result = GenerationBenchmark.run(world, t, 256);
        info("terrain-stage benchmark", bench.result.summary());
        long h1 = GenerationBenchmark.fillOne(world, new UnderdeepTerrain(seed), biomeReg, new ChunkPos(123, -77));
        long h2 = GenerationBenchmark.fillOne(world, new UnderdeepTerrain(seed), biomeReg, new ChunkPos(123, -77));
        check("terrain stage deterministic (fresh instances)", h1 == h2, Long.toHexString(h1) + " vs " + Long.toHexString(h2));

        int area = 12;
        int baseX = 30;
        int baseZ = 30;
        long genStart = System.nanoTime();
        List<ChunkPos> genOrder = new ArrayList<>();
        for (int i = 0; i < area; i++) {
            for (int j = 0; j < area; j++) {
                genOrder.add(new ChunkPos(baseX + i, baseZ + j));
            }
        }
        if (order.equals("reverse")) {
            java.util.Collections.reverse(genOrder);
        }
        if (phase.equals("create")) {
            for (ChunkPos cp : genOrder) {
                world.getChunk(cp.x, cp.z, ChunkStatus.FULL, true);
            }
            double ms = (System.nanoTime() - genStart) / 1e6;
            info("full generation, sequential main-thread requests", String.format(Locale.ROOT,
                    "%d chunks in %.0f ms (%.1f ms/chunk incl. neighbour decoration); order=%s", genOrder.size(), ms, ms / genOrder.size(), order));
        }
        // Hash inner chunks (their neighbours were all generated, so decoration is complete).
        StringBuilder hashes = new StringBuilder();
        for (int i = 1; i < area - 1; i++) {
            for (int j = 1; j < area - 1; j++) {
                Chunk c = world.getChunk(baseX + i, baseZ + j, ChunkStatus.FULL, true);
                hashes.append(baseX + i).append(',').append(baseZ + j).append(' ').append(Long.toHexString(GenerationBenchmark.hash(c))).append('\n');
            }
        }
        // Spawn-area chunks for the save/reload comparison.
        StringBuilder spawnHashes = new StringBuilder();
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                Chunk c = world.getChunk((spawn.getX() >> 4) + i, (spawn.getZ() >> 4) + j, ChunkStatus.FULL, true);
                spawnHashes.append((spawn.getX() >> 4) + i).append(',').append((spawn.getZ() >> 4) + j).append(' ')
                        .append(Long.toHexString(GenerationBenchmark.hash(c))).append('\n');
            }
        }
        Files.writeString(dir.resolve("chunk_hashes_" + phase + "_" + order + ".txt"), hashes.toString());
        Files.writeString(dir.resolve("spawn_hashes_" + phase + ".txt"), spawnHashes.toString());
        if (phase.equals("reload")) {
            Path prev = dir.resolve("spawn_hashes_create.txt");
            if (Files.exists(prev)) {
                boolean same = Files.readString(prev).equals(spawnHashes.toString());
                check("spawn chunks unchanged after save + restart", same, same ? "25 chunk hashes identical" : "hashes differ");
            } else {
                info("reload comparison", "no create-phase hashes found in " + dir);
            }
            Path prevArea = dir.resolve("chunk_hashes_create_forward.txt");
            if (Files.exists(prevArea)) {
                boolean same = Files.readString(prevArea).equals(hashes.toString());
                check("benchmark-area chunks unchanged after save + restart", same, same ? "identical" : "differ");
            }
        }

        // ------------------------------------------------------------------ boundaries & fluids
        section("Boundaries, skylight and fluid stability (spawn area, 9x9 chunks)");
        boundaries(world, spawn);

        // ------------------------------------------------------------------ connectivity
        section("Connectivity (ground truth flood fill of generated blocks)");
        connectivity(world, t, spawn.getX() >> 4, spawn.getZ() >> 4, 10, "spawn area");
        connectivity(world, t, 140, -90, 10, "remote area (2240,-1440)");

        // ------------------------------------------------------------------ settlement sites
        section("Settlement site analysis near spawn (radius 1200)");
        for (SiteType st : SiteType.values()) {
            for (CavernLayer layer : CavernLayer.values()) {
                List<SiteCandidate> sites = SiteAnalyzer.findSites(t, st, layer, 0, 0, 1200, 1);
                if (sites.isEmpty()) {
                    info("site " + st.id() + " (" + layer.id + ")", "none");
                } else {
                    SiteCandidate s = sites.get(0);
                    info("site " + st.id() + " (" + layer.id + ")", String.format(Locale.ROOT,
                            "best score %.2f at %d %d %d in %s, usable radius %d, headroom %.0f, water=%s lava=%s tunnels=%d",
                            s.score(), s.x(), s.y(), s.z(), s.region().id(), s.usableRadius(), s.headroom(),
                            s.waterNearby(), s.lavaNearby(), s.connections()));
                }
            }
        }

        // ------------------------------------------------------------------ previews and tour
        section("Artifacts");
        TerrainPreview.write(TerrainPreview.regionMap(t, CavernLayer.UPPER, 0, 0, 512, 12), dir.resolve("regions_upper.png").toFile());
        TerrainPreview.write(TerrainPreview.regionMap(t, CavernLayer.DEEP, 0, 0, 512, 12), dir.resolve("regions_deep.png").toFile());
        TerrainPreview.write(TerrainPreview.layerMap(t, CavernLayer.UPPER, 0, 0, 768, 2), dir.resolve("layer_upper.png").toFile());
        TerrainPreview.write(TerrainPreview.layerMap(t, CavernLayer.DEEP, 0, 0, 768, 2), dir.resolve("layer_deep.png").toFile());
        TerrainPreview.write(TerrainPreview.crossSection(t, spawn.getZ(), spawn.getX() - 400, 800, 1), dir.resolve("section_spawn.png").toFile());
        writeTour(world, t, spawn, regionPos, dir);
        info("artifacts", "previews and tour.txt written to " + dir.toAbsolutePath());

        if (phase.equals("create")) {
            long saveStart = System.nanoTime();
            server.saveAll(true, true, true);
            info("save", String.format(Locale.ROOT, "saveAll took %.0f ms", (System.nanoTime() - saveStart) / 1e6));
        }
        info("average tick time (ms)", String.format(Locale.ROOT, "%.2f", server.getAverageTickTime()));
        check("selftest completed", true, "all sections executed");
    }

    private static final class BenchmarkHolder {
        GenerationBenchmark.Result result;
    }

    private static Map<String, Integer> startResources(ServerWorld world, BlockPos spawn, int radius) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String k : List.of("logs", "leaves", "food_plants", "animals", "water", "stone", "coal", "iron", "copper",
                "light_sources", "open_floor", "grass_or_moss")) {
            m.put(k, 0);
        }
        BlockPos.Mutable p = new BlockPos.Mutable();
        int minY = spawn.getY() - 40;
        int maxY = spawn.getY() + 60;
        for (int x = spawn.getX() - radius; x <= spawn.getX() + radius; x++) {
            for (int z = spawn.getZ() - radius; z <= spawn.getZ() + radius; z++) {
                boolean floorCounted = false;
                for (int y = maxY; y >= minY; y--) {
                    BlockState s = world.getBlockState(p.set(x, y, z));
                    if (s.isIn(BlockTags.LOGS)) {
                        m.merge("logs", 1, Integer::sum);
                    } else if (s.isIn(BlockTags.LEAVES)) {
                        m.merge("leaves", 1, Integer::sum);
                    } else if (s.isOf(Blocks.SWEET_BERRY_BUSH) || s.isOf(Blocks.BROWN_MUSHROOM) || s.isOf(Blocks.RED_MUSHROOM)
                            || (s.getBlock() instanceof CaveVines && CaveVines.hasBerries(s))) {
                        m.merge("food_plants", 1, Integer::sum);
                    } else if (s.getFluidState().isOf(Fluids.WATER)) {
                        m.merge("water", 1, Integer::sum);
                    } else if (s.isOf(Blocks.STONE) || s.isOf(Blocks.ANDESITE) || s.isOf(Blocks.GRANITE) || s.isOf(Blocks.DIORITE)) {
                        m.merge("stone", 1, Integer::sum);
                    } else if (s.isOf(Blocks.COAL_ORE) || s.isOf(Blocks.DEEPSLATE_COAL_ORE)) {
                        m.merge("coal", 1, Integer::sum);
                    } else if (s.isOf(Blocks.IRON_ORE) || s.isOf(Blocks.DEEPSLATE_IRON_ORE)) {
                        m.merge("iron", 1, Integer::sum);
                    } else if (s.isOf(Blocks.COPPER_ORE) || s.isOf(Blocks.DEEPSLATE_COPPER_ORE)) {
                        m.merge("copper", 1, Integer::sum);
                    } else if (s.isOf(Blocks.GRASS_BLOCK) || s.isOf(Blocks.MOSS_BLOCK)) {
                        m.merge("grass_or_moss", 1, Integer::sum);
                    }
                    if (s.getLuminance() > 0 && !s.isOf(Blocks.LAVA)) {
                        m.merge("light_sources", 1, Integer::sum);
                    }
                    if (!floorCounted && s.isAir() && world.getBlockState(p.down()).isSideSolidFullSquare(world, p.down(), Direction.UP)
                            && world.getBlockState(p.up()).isAir()) {
                        m.merge("open_floor", 1, Integer::sum);
                        floorCounted = true;
                    }
                }
            }
        }
        Box area = new Box(spawn).expand(radius, 60, radius);
        m.put("animals", world.getEntitiesByClass(AnimalEntity.class, area, e -> true).size());
        return m;
    }

    private static void cavernStats(UnderdeepTerrain t) {
        for (CavernLayer layer : CavernLayer.values()) {
            List<Double> gaps = new ArrayList<>();
            int total = 0;
            boolean upper = layer == CavernLayer.UPPER;
            for (int x = -1500; x <= 1500; x += 8) {
                for (int z = -1500; z <= 1500; z += 8) {
                    TerrainColumn c = t.column(x, z);
                    double gap = upper ? c.upperCeiling - c.upperFloor : c.deepCeiling - c.deepFloor;
                    total++;
                    if (gap > 3) {
                        gaps.add(gap);
                    }
                }
            }
            gaps.sort(Double::compare);
            if (gaps.isEmpty()) {
                check(layer.id + " layer has open space", false, "no open columns");
                continue;
            }
            double p50 = gaps.get(gaps.size() / 2);
            double p90 = gaps.get((int) (gaps.size() * 0.9));
            double max = gaps.get(gaps.size() - 1);
            info(layer.id + " open-column fraction", String.format(Locale.ROOT, "%.1f%%", gaps.size() * 100.0 / total));
            info(layer.id + " open height (median / p90 / max)", String.format(Locale.ROOT, "%.0f / %.0f / %.0f blocks", p50, p90, max));
            List<Double> radii = new ArrayList<>();
            int mega = 0;
            for (CavernGraph.Node n : t.graph(layer).nodesIn(-1500, -1500, 1500, 1500)) {
                radii.add(n.radius());
                if (n.mega()) {
                    mega++;
                }
            }
            radii.sort(Double::compare);
            info(layer.id + " chamber radius (min / median / max)", String.format(Locale.ROOT, "%.0f / %.0f / %.0f blocks; %d colossal of %d chambers",
                    radii.get(0), radii.get(radii.size() / 2), radii.get(radii.size() - 1), mega, radii.size()));
            check(layer.id + " cavern size variation", max - p50 > 40 && radii.get(radii.size() - 1) > 150,
                    "colossal caverns exist and heights vary");
        }
    }

    private static void boundaries(ServerWorld world, BlockPos spawn) {
        int scx = spawn.getX() >> 4;
        int scz = spawn.getZ() >> 4;
        int floorHoles = 0;
        int roofHoles = 0;
        int highOpen = 0;
        int skyLit = 0;
        int floatingWater = 0;
        int floatingLava = 0;
        int sampledOpen = 0;
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int cx = scx - 4; cx <= scx + 4; cx++) {
            for (int cz = scz - 4; cz <= scz + 4; cz++) {
                Chunk chunk = world.getChunk(cx, cz, ChunkStatus.FULL, true);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int x = cx * 16 + lx;
                        int z = cz * 16 + lz;
                        if (!chunk.getBlockState(p.set(x, -64, z)).isOf(Blocks.BEDROCK)) {
                            floorHoles++;
                        }
                        if (!chunk.getBlockState(p.set(x, 319, z)).isOf(Blocks.BEDROCK)) {
                            roofHoles++;
                        }
                        for (int y = 305; y < 319; y++) {
                            if (chunk.getBlockState(p.set(x, y, z)).isAir()) {
                                highOpen++;
                            }
                        }
                        for (int y = -63; y < 305; y++) {
                            BlockState s = chunk.getBlockState(p.set(x, y, z));
                            if (s.isAir() && (lx % 4 == 0) && (lz % 4 == 0) && (y % 8 == 0)) {
                                sampledOpen++;
                                if (world.getLightLevel(LightType.SKY, p) > 0) {
                                    skyLit++;
                                }
                            }
                            if (s.getFluidState().isStill() && lx > 0 && lx < 15 && lz > 0 && lz < 15) {
                                BlockState below = chunk.getBlockState(p.set(x, y - 1, z));
                                if (below.isAir()) {
                                    if (s.getFluidState().isOf(Fluids.WATER)) {
                                        floatingWater++;
                                    } else {
                                        floatingLava++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        check("bedrock floor complete at y=-64", floorHoles == 0, floorHoles + " holes in 81 chunks");
        check("bedrock roof complete at y=319", roofHoles == 0, roofHoles + " holes");
        check("no open space above y=304 (no surface access)", highOpen == 0, highOpen + " air blocks in y 305..318");
        check("no skylight anywhere sampled", skyLit == 0, skyLit + " of " + sampledOpen + " sampled air blocks sky-lit");
        info("fluid sources directly above air (waterfall/leak indicators)", "water=" + floatingWater + " lava=" + floatingLava);
        check("no large fluid leaks", floatingWater + floatingLava < 400, "source blocks over air: " + (floatingWater + floatingLava));
    }

    private static void connectivity(ServerWorld world, UnderdeepTerrain t, int ccx, int ccz, int r, String label) {
        long start = System.nanoTime();
        ConnectivityAnalyzer.Result res = ConnectivityAnalyzer.analyze(world, ccx, ccz, r, -60, 310);
        info(label + " connectivity", res.summary() + String.format(Locale.ROOT, " [%.1f s]", (System.nanoTime() - start) / 1e9));
        info(label + " largest components", res.topSizes().toString());
        int minX = (ccx - r) * 16;
        int minZ = (ccz - r) * 16;
        int maxX = (ccx + r + 1) * 16 - 1;
        int maxZ = (ccz + r + 1) * 16 - 1;
        int nodes = 0;
        int connected = 0;
        List<String> isolated = new ArrayList<>();
        List<String> layerNetworks = new ArrayList<>();
        boolean eachLayerSingle = true;
        for (CavernLayer layer : CavernLayer.values()) {
            CavernGraph g = t.graph(layer);
            java.util.Set<Integer> nets = new java.util.TreeSet<>();
            for (CavernGraph.Node n : g.nodesIn(minX + 40, minZ + 40, maxX - 40, maxZ - 40)) {
                if (n.x() < minX + 40 || n.x() > maxX - 40 || n.z() < minZ + 40 || n.z() > maxZ - 40) {
                    continue;
                }
                // Find an open block in this chamber (search a small area around the warped node centre).
                boolean anyOpen = false;
                int net = -1;
                for (int dx = -24; dx <= 24 && net < 0; dx += 4) {
                    for (int dz = -24; dz <= 24 && net < 0; dz += 4) {
                        int x = (int) n.x() + dx;
                        int z = (int) n.z() + dz;
                        TerrainColumn c = t.column(x, z);
                        double f = layer == CavernLayer.UPPER ? c.upperFloor : c.deepFloor;
                        double ce = layer == CavernLayer.UPPER ? c.upperCeiling : c.deepCeiling;
                        if (ce - f < 6) {
                            continue;
                        }
                        anyOpen = true;
                        int y = (int) (f + Math.min(6, (ce - f) / 2));
                        net = res.mainIndex(x, y, z);
                        if (net < 0) {
                            net = res.mainIndex(x, y + 2, z);
                        }
                    }
                }
                if (!anyOpen) {
                    continue;
                }
                nodes++;
                if (net >= 0) {
                    connected++;
                    nets.add(net);
                } else {
                    isolated.add(layer.id + "@" + (int) n.x() + "," + (int) n.z());
                }
            }
            layerNetworks.add(layer.id + " chambers -> networks " + nets);
            if (nets.size() > 1) {
                eachLayerSingle = false;
            }
        }
        double frac = nodes > 0 ? connected / (double) nodes : 0;
        check(label + ": chambers in a main connected network", nodes > 0 && frac >= 0.85,
                String.format(Locale.ROOT, "%d/%d (%.0f%%)%s", connected, nodes, frac * 100,
                        isolated.isEmpty() ? "" : " not joined within area: " + isolated));
        check(label + ": each layer's chambers form one network", eachLayerSingle, String.join("; ", layerNetworks));
        check(label + ": main networks hold nearly all open space", res.mainFraction() > 0.9,
                String.format(Locale.ROOT, "%d main network(s) (>=10%% of open volume each) hold %.1f%%; largest alone %.1f%%",
                        res.mainNetworks(), res.mainFraction() * 100, res.largestFraction() * 100));
        int shafts = 0;
        for (int sx = Math.floorDiv(minX, CavernLayer.DEEP.graphCell); sx <= Math.floorDiv(maxX, CavernLayer.DEEP.graphCell); sx++) {
            for (int sz = Math.floorDiv(minZ, CavernLayer.DEEP.graphCell); sz <= Math.floorDiv(maxZ, CavernLayer.DEEP.graphCell); sz++) {
                if (t.shaft(sx, sz) != null) {
                    shafts++;
                }
            }
        }
        info(label + " inter-layer shafts (graph cells overlapping area)", String.valueOf(shafts));
    }

    private static void writeTour(ServerWorld world, UnderdeepTerrain t, BlockPos spawn, Map<RegionType, int[]> regionPos, Path dir)
            throws IOException {
        StringBuilder sb = new StringBuilder("# name x y z yaw pitch\n");
        sb.append(String.format(Locale.ROOT, "spawn_n %d %.1f %d 180 0\n", spawn.getX(), spawn.getY() + 1.6, spawn.getZ()));
        sb.append(String.format(Locale.ROOT, "spawn_e %d %.1f %d -90 5\n", spawn.getX(), spawn.getY() + 1.6, spawn.getZ()));
        sb.append(String.format(Locale.ROOT, "spawn_overview %d %.1f %d 30 25\n", spawn.getX() - 30, spawn.getY() + 30.0, spawn.getZ() - 30));
        Set<RegionType> done = EnumSet.noneOf(RegionType.class);
        for (CavernLayer layer : CavernLayer.values()) {
            CavernGraph g = t.graph(layer);
            Map<RegionType, CavernGraph.Node> best = new EnumMap<>(RegionType.class);
            for (CavernGraph.Node n : g.nodesIn(-1800, -1800, 1800, 1800)) {
                TerrainColumn c = t.column((int) n.x(), (int) n.z());
                RegionType r = layer == CavernLayer.UPPER ? c.upperRegion.primary : c.deepRegion.primary;
                double gap = layer == CavernLayer.UPPER ? c.upperCeiling - c.upperFloor : c.deepCeiling - c.deepFloor;
                if (gap < 30 || done.contains(r)) {
                    continue;
                }
                CavernGraph.Node b = best.get(r);
                if (b == null || n.radius() > b.radius()) {
                    best.put(r, n);
                }
            }
            for (Map.Entry<RegionType, CavernGraph.Node> e : best.entrySet()) {
                CavernGraph.Node n = e.getValue();
                TerrainColumn c = t.column((int) n.x(), (int) n.z());
                double f = layer == CavernLayer.UPPER ? c.upperFloor : c.deepFloor;
                double ce = layer == CavernLayer.UPPER ? c.upperCeiling : c.deepCeiling;
                double fluid = layer == CavernLayer.UPPER ? UnderdeepTerrain.SEA_LEVEL : CavernLayer.DEEP.fluidLevel;
                double y = Math.max(f, fluid) + Math.min(28, (ce - Math.max(f, fluid)) * 0.35);
                sb.append(String.format(Locale.ROOT, "%s_%s %d %.1f %d %d 8\n", e.getKey().id(), layer.id, (int) n.x(), y, (int) n.z(),
                        (int) (Math.atan2(-1, 0.3) * 57) + (e.getKey().ordinal() * 60)));
                done.add(e.getKey());
            }
        }
        // A shaft near spawn, looking down.
        outer:
        for (int r = 0; r < 1500; r += 32) {
            for (int dx = -r; dx <= r; dx += 32) {
                for (int dz = -r; dz <= r; dz += 32) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    CavernGraph.Shaft s = t.shaft(Math.floorDiv(dx, CavernLayer.DEEP.graphCell), Math.floorDiv(dz, CavernLayer.DEEP.graphCell));
                    if (s != null) {
                        TerrainColumn c = t.column((int) s.x(), (int) s.z());
                        if (c.shaftDist < c.shaftRadius) {
                            sb.append(String.format(Locale.ROOT, "shaft %d %.1f %d 0 80\n", (int) s.x(), c.shaftTop + 4, (int) s.z()));
                            break outer;
                        }
                    }
                }
            }
        }
        Files.writeString(dir.resolve("tour.txt"), sb.toString());
    }

    private static void writeReports(Path dir) throws IOException {
        long fails = CHECKS.stream().filter(c -> c.status.equals("FAIL")).count();
        long passes = CHECKS.stream().filter(c -> c.status.equals("PASS")).count();
        String header = String.format(Locale.ROOT, "\n**Summary: %d passed, %d failed, %d informational.**\n", passes, fails,
                CHECKS.size() - passes - fails);
        Files.writeString(dir.resolve("report.md"), REPORT + header);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(dir.resolve("checks.json"), gson.toJson(CHECKS));
    }
}
