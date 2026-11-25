package dev.underdeep.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.underdeep.registry.ModWorldgen;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.feature.FeaturePlacementContext;
import net.minecraft.world.gen.placementmodifier.AbstractConditionalPlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifierType;

/**
 * Keeps only positions within {@code radius} blocks of the world origin (the guaranteed starting
 * cavern). Used for features that guarantee early-game resources near spawn.
 */
public final class StartZonePlacement extends AbstractConditionalPlacementModifier {
    public static final MapCodec<StartZonePlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 512).fieldOf("radius").forGetter(p -> p.radius)
    ).apply(i, StartZonePlacement::new));

    private final int radius;

    public StartZonePlacement(int radius) {
        this.radius = radius;
    }

    @Override
    protected boolean shouldPlace(FeaturePlacementContext context, Random random, BlockPos pos) {
        long dx = pos.getX();
        long dz = pos.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    @Override
    public PlacementModifierType<?> getType() {
        return ModWorldgen.START_ZONE;
    }
}
