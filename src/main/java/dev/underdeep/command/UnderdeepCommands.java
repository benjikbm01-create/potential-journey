package dev.underdeep.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.debug.GenerationBenchmark;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.analysis.ConnectivityAnalyzer;
import dev.underdeep.world.analysis.RegionReport;
import dev.underdeep.world.analysis.SiteAnalyzer;
import dev.underdeep.world.analysis.SiteCandidate;
import dev.underdeep.world.analysis.SiteType;
import dev.underdeep.world.data.UnderdeepWorldState;
import dev.underdeep.world.debug.TerrainPreview;
import dev.underdeep.world.gen.UnderdeepChunkGenerator;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.spawn.SpawnFinder;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /underdeep ...} diagnostic commands. All require the permission level configured in
 * {@code config/underdeep.json} (default 2, i.e. operators) and are intended for development and
 * server administration, not ordinary gameplay.
 */
public final class UnderdeepCommands {
    private UnderdeepCommands() {
    }

    private static final SuggestionProvider<ServerCommandSource> REGIONS = (c, b) ->
            CommandSource.suggestMatching(Arrays.stream(RegionType.values()).map(RegionType::id), b);
    private static final SuggestionProvider<ServerCommandSource> SITES = (c, b) ->
            CommandSource.suggestMatching(Arrays.stream(SiteType.values()).map(SiteType::id), b);

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("underdeep")
                .requires(s -> s.hasPermissionLevel(UnderdeepConfig.get().diagnosticsPermissionLevel))
                .then(CommandManager.literal("info").executes(UnderdeepCommands::info))
                .then(CommandManager.literal("region")
                        .executes(c -> region(c, BlockPos.ofFloored(c.getSource().getPosition())))
                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                .executes(c -> region(c, BlockPosArgumentType.getBlockPos(c, "pos")))))
                .then(CommandManager.literal("locate")
                        .then(CommandManager.argument("region", StringArgumentType.word()).suggests(REGIONS)
                                .executes(UnderdeepCommands::locate)))
                .then(CommandManager.literal("spawncheck").executes(UnderdeepCommands::spawnCheck))
                .then(CommandManager.literal("ecology")
                        .executes(c -> ecology(c, BlockPos.ofFloored(c.getSource().getPosition())))
                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                .executes(c -> ecology(c, BlockPosArgumentType.getBlockPos(c, "pos")))))
                .then(CommandManager.literal("cavern")
                        .executes(c -> cavern(c, BlockPos.ofFloored(c.getSource().getPosition())))
                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                .executes(c -> cavern(c, BlockPosArgumentType.getBlockPos(c, "pos")))))
                .then(CommandManager.literal("sites")
                        .then(CommandManager.argument("type", StringArgumentType.word()).suggests(SITES)
                                .executes(c -> sites(c, 512))
                                .then(CommandManager.argument("radius", IntegerArgumentType.integer(32, 4096))
                                        .executes(c -> sites(c, IntegerArgumentType.getInteger(c, "radius"))))))
                .then(CommandManager.literal("perf")
                        .executes(c -> perf(c, 256))
                        .then(CommandManager.argument("chunks", IntegerArgumentType.integer(16, 4096))
                                .executes(c -> perf(c, IntegerArgumentType.getInteger(c, "chunks")))))
                .then(CommandManager.literal("render")
                        .executes(c -> render(c, 1024))
                        .then(CommandManager.argument("radius", IntegerArgumentType.integer(64, 4096))
                                .executes(c -> render(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(CommandManager.literal("connectivity")
                        .executes(c -> connectivity(c, 4))
                        .then(CommandManager.argument("radiusChunks", IntegerArgumentType.integer(1, 12))
                                .executes(c -> connectivity(c, IntegerArgumentType.getInteger(c, "radiusChunks"))))));
    }

    private static UnderdeepTerrain requireTerrain(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerWorld world = c.getSource().getWorld();
        UnderdeepTerrain t = UnderdeepWorlds.terrain(world);
        if (t == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Text.literal("This dimension does not use the Underdeep generator.")).create();
        }
        return t;
    }

    private static void send(CommandContext<ServerCommandSource> c, String msg) {
        c.getSource().sendFeedback(() -> Text.literal(msg), false);
    }

    private static int info(CommandContext<ServerCommandSource> c) {
        ServerWorld world = c.getSource().getWorld();
        boolean ud = world.getChunkManager().getChunkGenerator() instanceof UnderdeepChunkGenerator;
        send(c, "Dimension " + world.getRegistryKey().getValue() + " generator: "
                + world.getChunkManager().getChunkGenerator().getClass().getSimpleName()
                + (ud ? " (Underdeep preset active)" : " (not Underdeep)"));
        if (ud) {
            UnderdeepWorldState state = UnderdeepWorldState.get(world);
            send(c, "Terrain generator v" + UnderdeepTerrain.GENERATOR_VERSION + ", world created with v"
                    + state.createdWithGeneratorVersion() + "; height " + world.getBottomY() + ".." + (world.getTopY() - 1)
                    + "; skylight=" + world.getDimension().hasSkyLight() + "; ceiling=" + world.getDimension().hasCeiling());
            send(c, "World spawn: " + world.getSpawnPos().toShortString() + "; Nether portals "
                    + (UnderdeepConfig.get().netherPortalsInUnderdeep ? "enabled" : "disabled"));
        }
        return 1;
    }

    private static int region(CommandContext<ServerCommandSource> c, BlockPos pos) throws CommandSyntaxException {
        UnderdeepTerrain t = requireTerrain(c);
        RegionReport r = RegionReport.at(t, pos.getX(), pos.getY(), pos.getZ());
        send(c, String.format(Locale.ROOT, "%s (%s layer) id=%s border=%.0fm biome=%s", r.region().displayName(),
                r.layer().id, r.regionId(), r.borderDistance(),
                c.getSource().getWorld().getBiome(pos).getKey().map(k -> k.getValue().toString()).orElse("?")));
        send(c, String.format(Locale.ROOT, "cavern floor %.0f ceiling %.0f (open %.0f), fluid %s",
                r.floorY(), r.ceilingY(), r.openHeight(),
                Double.isNaN(r.fluidLevel()) ? "none" : (r.lavaFluid() ? "lava @" : "water @") + (int) r.fluidLevel()));
        var e = r.environment();
        send(c, String.format(Locale.ROOT, "env: temp %.2f humidity %.2f light %.2f water %.2f danger %.2f fertility %.2f habitat %s",
                e.temperature(), e.humidity(), e.ambientLight(), e.waterAvailability(), e.danger(), e.fertility(), e.habitatTags()));
        StringBuilder sb = new StringBuilder("settlement affinity:");
        for (SiteType s : SiteType.values()) {
            sb.append(' ').append(s.id()).append('=').append(String.format(Locale.ROOT, "%.1f", r.region().settlementAffinity(s)));
        }
        send(c, sb.toString());
        return 1;
    }

    /** Regional ecological summary (Stage 2 contract for wildlife and settlements) plus recorded harvests. */
    private static int ecology(CommandContext<ServerCommandSource> c, BlockPos pos) throws CommandSyntaxException {
        UnderdeepTerrain t = requireTerrain(c);
        RegionReport r = RegionReport.at(t, pos.getX(), pos.getY(), pos.getZ());
        var eco = dev.underdeep.ecology.RegionEcology.of(r.region(), r.regionId().asLong());
        send(c, eco.describe());
        var tag = dev.underdeep.world.data.UnderdeepWorldState.get(c.getSource().getWorld())
                .regionData(r.regionId().asLong()).getCompound(dev.underdeep.ecology.EcologyState.KEY);
        send(c, "recorded harvests in region " + r.regionId() + ": " + (tag.isEmpty() ? "none" : tag.toString()));
        return 1;
    }

    private static int locate(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        UnderdeepTerrain t = requireTerrain(c);
        RegionType type = RegionType.byId(StringArgumentType.getString(c, "region"));
        if (type == null) {
            send(c, "Unknown region. Options: " + Arrays.toString(RegionType.values()));
            return 0;
        }
        BlockPos from = BlockPos.ofFloored(c.getSource().getPosition());
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        CavernLayer bestLayer = null;
        for (CavernLayer layer : CavernLayer.values()) {
            if (!layer.regions.contains(type)) {
                continue;
            }
            var map = t.regions(layer);
            int step = 64;
            for (int r = 0; r <= 6000 && best == null || r <= 6000 && r < Math.sqrt(bestD) + step; r += step) {
                for (int dx = -r; dx <= r; dx += step) {
                    for (int dz = -r; dz <= r; dz += step) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        int x = from.getX() + dx;
                        int z = from.getZ() + dz;
                        if (map.typeAt(x, z) == type) {
                            double d = (double) dx * dx + (double) dz * dz;
                            if (d < bestD) {
                                bestD = d;
                                best = new int[]{x, z};
                                bestLayer = layer;
                            }
                        }
                    }
                }
            }
        }
        if (best == null) {
            send(c, "No " + type.displayName() + " found within 6000 blocks.");
            return 0;
        }
        TerrainColumn col = t.column(best[0], best[1]);
        int y = (int) (bestLayer == CavernLayer.UPPER ? col.upperFloor + 2 : col.deepFloor + 2);
        send(c, String.format(Locale.ROOT, "Nearest %s (%s layer) at %d %d %d, ~%.0f blocks away",
                type.displayName(), bestLayer.id, best[0], y, best[1], Math.sqrt(bestD)));
        return 1;
    }

    private static int spawnCheck(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        requireTerrain(c);
        ServerWorld world = c.getSource().getWorld();
        BlockPos spawn = world.getSpawnPos();
        SpawnFinder.Validation v = SpawnFinder.validate(world, spawn);
        send(c, "World spawn " + spawn.toShortString() + ": " + (v.safe() ? "SAFE" : "UNSAFE"));
        v.problems().stream().limit(8).forEach(p -> send(c, " - " + p));
        return v.safe() ? 1 : 0;
    }

    private static int cavern(CommandContext<ServerCommandSource> c, BlockPos pos) throws CommandSyntaxException {
        UnderdeepTerrain t = requireTerrain(c);
        TerrainColumn col = t.column(pos.getX(), pos.getZ());
        send(c, String.format(Locale.ROOT, "upper: floor %.1f ceiling %.1f open %.0f chamber %.2f | deep: floor %.1f ceiling %.1f open %.0f chamber %.2f | separation %.1f",
                col.upperFloor, col.upperCeiling, col.upperCeiling - col.upperFloor, col.upperChamber,
                col.deepFloor, col.deepCeiling, col.deepCeiling - col.deepFloor, col.deepChamber, col.separation));
        // Sample the surrounding 128x128 area of this layer.
        boolean upper = pos.getY() >= col.separation;
        int open = 0;
        int total = 0;
        double maxGap = 0;
        for (int dx = -64; dx <= 64; dx += 4) {
            for (int dz = -64; dz <= 64; dz += 4) {
                TerrainColumn o = t.column(pos.getX() + dx, pos.getZ() + dz);
                double gap = upper ? o.upperCeiling - o.upperFloor : o.deepCeiling - o.deepFloor;
                total++;
                if (gap > 3) {
                    open++;
                    maxGap = Math.max(maxGap, gap);
                }
            }
        }
        send(c, String.format(Locale.ROOT, "within 64 blocks (%s layer): %.0f%% of columns open, tallest open space %.0f blocks",
                upper ? "upper" : "deep", open * 100.0 / total, maxGap));
        if (col.shaftDist < col.shaftRadius) {
            send(c, String.format(Locale.ROOT, "inside a vertical shaft (%.0f..%.0f)%s", col.shaftBottom, col.shaftTop,
                    col.shaftSpiral ? " with spiral ledge" : ""));
        }
        return 1;
    }

    private static int sites(CommandContext<ServerCommandSource> c, int radius) throws CommandSyntaxException {
        UnderdeepTerrain t = requireTerrain(c);
        String id = StringArgumentType.getString(c, "type");
        SiteType type = Arrays.stream(SiteType.values()).filter(s -> s.id().equals(id)).findFirst().orElse(null);
        if (type == null) {
            send(c, "Unknown site type. Options: " + Arrays.toString(SiteType.values()));
            return 0;
        }
        BlockPos from = BlockPos.ofFloored(c.getSource().getPosition());
        CavernLayer layer = t.column(from.getX(), from.getZ()).layerAt(from.getY());
        List<SiteCandidate> list = SiteAnalyzer.findSites(t, type, layer, from.getX(), from.getZ(), radius, 6);
        if (list.isEmpty()) {
            send(c, "No suitable " + type.id() + " sites within " + radius + " blocks in the " + layer.id + " layer.");
            return 0;
        }
        for (SiteCandidate s : list) {
            send(c, String.format(Locale.ROOT, "%.2f %s at %d %d %d (%s) radius %d headroom %.0f slope %.2f water=%s lava=%s tunnels=%d",
                    s.score(), s.type().id(), s.x(), s.y(), s.z(), s.region().id(), s.usableRadius(), s.headroom(),
                    s.slope(), s.waterNearby(), s.lavaNearby(), s.connections()));
        }
        return list.size();
    }

    private static int perf(CommandContext<ServerCommandSource> c, int chunks) throws CommandSyntaxException {
        UnderdeepTerrain t = requireTerrain(c);
        ServerCommandSource src = c.getSource();
        send(c, "Benchmarking terrain fill of " + chunks + " chunks on background threads...");
        GenerationBenchmark.runAsync(src.getWorld(), t, chunks, result ->
                src.getServer().execute(() -> src.sendFeedback(() -> Text.literal(result.summary()), false)));
        return 1;
    }

    private static int render(CommandContext<ServerCommandSource> c, int radius) throws CommandSyntaxException {
        UnderdeepTerrain t = requireTerrain(c);
        ServerCommandSource src = c.getSource();
        BlockPos from = BlockPos.ofFloored(src.getPosition());
        File dir = src.getServer().getSavePath(WorldSavePath.ROOT).resolve("underdeep-debug").toFile();
        send(c, "Rendering terrain previews to " + dir + " ...");
        Thread th = new Thread(() -> {
            try {
                int size = 512;
                int scale = Math.max(1, radius * 2 / size);
                TerrainPreview.write(TerrainPreview.regionMap(t, CavernLayer.UPPER, from.getX(), from.getZ(), size, scale * 4), new File(dir, "regions_upper.png"));
                TerrainPreview.write(TerrainPreview.regionMap(t, CavernLayer.DEEP, from.getX(), from.getZ(), size, scale * 4), new File(dir, "regions_deep.png"));
                TerrainPreview.write(TerrainPreview.layerMap(t, CavernLayer.UPPER, from.getX(), from.getZ(), size, scale), new File(dir, "layer_upper.png"));
                TerrainPreview.write(TerrainPreview.layerMap(t, CavernLayer.DEEP, from.getX(), from.getZ(), size, scale), new File(dir, "layer_deep.png"));
                TerrainPreview.write(TerrainPreview.crossSection(t, from.getZ(), from.getX() - 256, 512, 1), new File(dir, "section_x.png"));
                src.getServer().execute(() -> src.sendFeedback(() -> Text.literal("Previews written to " + dir), false));
            } catch (Exception e) {
                src.getServer().execute(() -> src.sendError(Text.literal("Render failed: " + e)));
            }
        }, "underdeep-render");
        th.setDaemon(true);
        th.start();
        return 1;
    }

    private static int connectivity(CommandContext<ServerCommandSource> c, int radiusChunks) throws CommandSyntaxException {
        requireTerrain(c);
        BlockPos from = BlockPos.ofFloored(c.getSource().getPosition());
        send(c, "Analysing connectivity (generates up to " + (2 * radiusChunks + 1) * (2 * radiusChunks + 1) + " chunks; the server will pause)...");
        ConnectivityAnalyzer.Result r = ConnectivityAnalyzer.analyze(c.getSource().getWorld(), from.getX() >> 4, from.getZ() >> 4,
                radiusChunks, -60, 310);
        send(c, r.summary());
        return 1;
    }
}
