package dev.underdeep.world.gen;

import dev.underdeep.Underdeep;
import dev.underdeep.world.region.RegionType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.EnumMap;
import java.util.Map;

/**
 * Biome registry keys. Stage 1 has exactly one biome per {@link RegionType}; later stages may add
 * sub-biomes (e.g. lake shores, deep variants) and map them from region + local terrain in
 * {@link UnderdeepBiomeSource}.
 */
public final class UnderdeepBiomes {
    private static final Map<RegionType, RegistryKey<Biome>> KEYS = new EnumMap<>(RegionType.class);

    static {
        for (RegionType type : RegionType.values()) {
            KEYS.put(type, RegistryKey.of(RegistryKeys.BIOME, Identifier.of(Underdeep.MOD_ID, type.id())));
        }
    }

    private UnderdeepBiomes() {
    }

    public static RegistryKey<Biome> key(RegionType type) {
        return KEYS.get(type);
    }
}
