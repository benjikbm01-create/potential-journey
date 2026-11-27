package dev.underdeep.world;

import com.google.common.collect.MapMaker;
import dev.underdeep.Underdeep;
import dev.underdeep.world.gen.UnderdeepChunkGenerator;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Binds the seed-dependent {@link UnderdeepTerrain} to the Minecraft objects that represent a world.
 * <p>
 * Vanilla does not pass the world seed to {@code BiomeSource#getBiome}; it passes the world's
 * {@link MultiNoiseUtil.MultiNoiseSampler}, which is unique per world (owned by its
 * {@link NoiseConfig}). {@link UnderdeepChunkGenerator#createStructurePlacementCalculator} receives the
 * seed when the world's chunk manager is constructed and registers the terrain here, keyed by that
 * sampler's identity (weak keys, so unloaded worlds are collected).
 */
public final class UnderdeepWorlds {
    private static final ConcurrentMap<MultiNoiseUtil.MultiNoiseSampler, UnderdeepTerrain> BY_SAMPLER =
            new MapMaker().weakKeys().makeMap();
    private static final ConcurrentMap<Long, UnderdeepTerrain> BY_SEED = new ConcurrentHashMap<>();

    private UnderdeepWorlds() {
    }

    /** Shared terrain model for a seed (terrain objects are immutable, so sharing is safe). */
    public static UnderdeepTerrain terrainForSeed(long seed) {
        return terrainForSeed(seed, UnderdeepTerrain.GENERATOR_VERSION);
    }

    /** Shared terrain model for a seed and generator version (old worlds keep their original terrain). */
    public static UnderdeepTerrain terrainForSeed(long seed, int version) {
        if (BY_SEED.size() > 8) {
            BY_SEED.clear();
        }
        long key = seed * 31 + version;
        UnderdeepTerrain t = BY_SEED.get(key);
        if (t == null || t.seed() != seed || t.version() != version) {
            t = new UnderdeepTerrain(seed, version);
            BY_SEED.put(key, t);
        }
        return t;
    }

    public static void bind(NoiseConfig noiseConfig, long seed, int version) {
        BY_SAMPLER.put(noiseConfig.getMultiNoiseSampler(), terrainForSeed(seed, version));
    }

    @Nullable
    public static UnderdeepTerrain terrain(MultiNoiseUtil.MultiNoiseSampler sampler) {
        return BY_SAMPLER.get(sampler);
    }

    public static UnderdeepTerrain terrain(NoiseConfig noiseConfig) {
        UnderdeepTerrain t = BY_SAMPLER.get(noiseConfig.getMultiNoiseSampler());
        if (t == null) {
            throw new IllegalStateException("Underdeep terrain requested for an unbound NoiseConfig");
        }
        return t;
    }

    /** Whether a world uses the Underdeep generator. */
    public static boolean isUnderdeep(WorldAccess world) {
        return world instanceof ServerWorld sw && sw.getChunkManager().getChunkGenerator() instanceof UnderdeepChunkGenerator;
    }

    @Nullable
    public static UnderdeepTerrain terrain(ServerWorld world) {
        ChunkGenerator gen = world.getChunkManager().getChunkGenerator();
        if (!(gen instanceof UnderdeepChunkGenerator)) {
            return null;
        }
        UnderdeepTerrain t = BY_SAMPLER.get(world.getChunkManager().getNoiseConfig().getMultiNoiseSampler());
        if (t == null) {
            Underdeep.LOGGER.warn("Underdeep world {} had no bound terrain; binding from seed", world.getRegistryKey().getValue());
            bind(world.getChunkManager().getNoiseConfig(), world.getSeed(), ((UnderdeepChunkGenerator) gen).generatorVersion());
            t = BY_SAMPLER.get(world.getChunkManager().getNoiseConfig().getMultiNoiseSampler());
        }
        return t;
    }
}
