package dev.underdeep.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.gen.feature.FeatureConfig;

/**
 * Config for species-driven features: {@code {"species": "duskoak", "size": "normal"}}.
 * Sizes: {@code young}, {@code normal}, {@code ancient}.
 */
public record SpeciesFeatureConfig(String species, String size) implements FeatureConfig {
    public static final Codec<SpeciesFeatureConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("species").forGetter(SpeciesFeatureConfig::species),
            Codec.STRING.optionalFieldOf("size", "normal").forGetter(SpeciesFeatureConfig::size)
    ).apply(i, SpeciesFeatureConfig::new));

    public double scale() {
        return switch (size) {
            case "young" -> 0.62;
            case "ancient" -> 1.65;
            default -> 1.0;
        };
    }
}
