package dev.underdeep.debug;

import dev.underdeep.world.gen.SurfacePainter;
import dev.underdeep.world.gen.UnderdeepChunkGenerator;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Measures the Underdeep terrain stages (noise fill + surface painting) on detached ProtoChunks far
 * from players, so the measurement does not disturb the live world. Feature decoration, lighting and
 * saving are NOT included; use full-world measurements (docs/TESTING.md) for end-to-end numbers.
 */
public final class GenerationBenchmark {
    private GenerationBenchmark() {
    }

    public record Result(int chunks, int threads, double millisPerChunkSingle, double chunksPerSecondParallel, long checksum) {
        public String summary() {
            return String.format(Locale.ROOT,
                    "Terrain fill+surface: %d chunks, %.2f ms/chunk single-threaded, %.1f chunks/s with %d threads (checksum %x)",
                    chunks, millisPerChunkSingle, chunksPerSecondParallel, threads, checksum);
        }
    }

    public static void runAsync(ServerWorld world, UnderdeepTerrain t, int chunks, Consumer<Result> done) {
        Thread th = new Thread(() -> done.accept(run(world, t, chunks)), "underdeep-benchmark");
        th.setDaemon(true);
        th.start();
    }

    public static Result run(ServerWorld world, UnderdeepTerrain baseTerrain, int chunks) {
        // Fresh terrain instance so caches start cold, like a real first visit.
        UnderdeepTerrain t = new UnderdeepTerrain(baseTerrain.seed());
        var biomes = world.getRegistryManager().get(RegistryKeys.BIOME);
        int side = (int) Math.ceil(Math.sqrt(chunks));
        int offset = 40000 >> 4;
        long start = System.nanoTime();
        long checksum = 0;
        int singleCount = Math.min(chunks, 64);
        for (int i = 0; i < singleCount; i++) {
            checksum ^= fillOne(world, t, biomes, new ChunkPos(offset + i % side, offset + i / side));
        }
        double singleMs = (System.nanoTime() - start) / 1e6 / singleCount;
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        UnderdeepTerrain t2 = new UnderdeepTerrain(baseTerrain.seed());
        List<Future<Long>> futures = new ArrayList<>();
        long pStart = System.nanoTime();
        for (int i = 0; i < chunks; i++) {
            ChunkPos cp = new ChunkPos(-offset - i % side, offset + i / side);
            futures.add(pool.submit(() -> fillOne(world, t2, biomes, cp)));
        }
        for (Future<Long> f : futures) {
            try {
                checksum ^= f.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        double secs = (System.nanoTime() - pStart) / 1e9;
        pool.shutdown();
        return new Result(chunks, threads, singleMs, chunks / secs, checksum);
    }

    /** Fills one detached chunk and returns a content hash. */
    public static long fillOne(ServerWorld world, UnderdeepTerrain t, net.minecraft.registry.Registry<net.minecraft.world.biome.Biome> biomes, ChunkPos cp) {
        ProtoChunk chunk = new ProtoChunk(cp, UpgradeData.NO_UPGRADE_DATA, world, biomes, null);
        ChunkSection[] sections = chunk.getSectionArray();
        UnderdeepChunkGenerator.fill(t, chunk, sections, chunk.getBottomSectionCoord());
        SurfacePainter.paint(t, chunk);
        return hash(chunk);
    }

    public static long hash(net.minecraft.world.chunk.Chunk chunk) {
        long h = 1469598103934665603L;
        net.minecraft.util.math.BlockPos.Mutable m = new net.minecraft.util.math.BlockPos.Mutable();
        int x0 = chunk.getPos().getStartX();
        int z0 = chunk.getPos().getStartZ();
        for (int y = chunk.getBottomY(); y < chunk.getTopY(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    h ^= net.minecraft.block.Block.getRawIdFromState(chunk.getBlockState(m.set(x0 + x, y, z0 + z)));
                    h *= 1099511628211L;
                }
            }
        }
        return h;
    }
}
