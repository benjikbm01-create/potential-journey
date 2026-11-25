package dev.underdeep.world.gen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.underdeep.Underdeep;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Assigns biomes from the Underdeep region maps: a position above a column's layer separation uses
 * the upper layer's region, below it the deep layer's region.
 * <p>
 * The serialized form has no fields; all biomes are resolved from the biome registry by key.
 */
public final class UnderdeepBiomeSource extends BiomeSource {
    public static final MapCodec<UnderdeepBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RegistryOps.getEntryLookupCodec(RegistryKeys.BIOME)
    ).apply(instance, instance.stable(UnderdeepBiomeSource::new)));

    private final RegistryEntryLookup<Biome> lookup;
    private final Map<RegionType, RegistryEntry<Biome>> biomes = new EnumMap<>(RegionType.class);
    private boolean warnedUnbound;

    public UnderdeepBiomeSource(RegistryEntryLookup<Biome> lookup) {
        this.lookup = lookup;
        for (RegionType type : RegionType.values()) {
            biomes.put(type, lookup.getOrThrow(UnderdeepBiomes.key(type)));
        }
    }

    @Override
    protected MapCodec<? extends BiomeSource> getCodec() {
        return CODEC;
    }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return List.copyOf(biomes.values()).stream();
    }

    public RegistryEntry<Biome> biome(RegionType type) {
        return biomes.get(type);
    }

    /** Biome at block coordinates, using a known terrain model. */
    public RegistryEntry<Biome> biomeAtBlock(UnderdeepTerrain terrain, int x, int y, int z) {
        return biomes.get(terrain.regionAt(x, y, z));
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        UnderdeepTerrain terrain = UnderdeepWorlds.terrain(noise);
        if (terrain == null) {
            if (!warnedUnbound) {
                warnedUnbound = true;
                Underdeep.LOGGER.warn("Underdeep biome source queried with an unknown noise sampler; returning Rootwild");
            }
            return biomes.get(RegionType.ROOTWILD);
        }
        // x, y, z are biome (quart) coordinates; sample the centre of the 4x4x4 cell.
        int bx = BiomeCoords.toBlock(x) + 2;
        int by = BiomeCoords.toBlock(y) + 2;
        int bz = BiomeCoords.toBlock(z) + 2;
        return biomeAtBlock(terrain, bx, by, bz);
    }

    @Override
    public void addDebugInfo(List<String> info, BlockPos pos, MultiNoiseUtil.MultiNoiseSampler noiseSampler) {
        UnderdeepTerrain terrain = UnderdeepWorlds.terrain(noiseSampler);
        if (terrain != null) {
            info.add("Underdeep region: " + terrain.regionAt(pos.getX(), pos.getY(), pos.getZ()).id());
        }
    }
}
