package dev.underdeep.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.underdeep.registry.ModWorldgen;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.feature.FeaturePlacementContext;
import net.minecraft.world.gen.placementmodifier.AbstractConditionalPlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifierType;

import java.util.List;

/**
 * Keeps only positions whose ecosystem sub-regional variant (0 open, 1 typical, 2 dense/rugged,
 * 3 lake/wet; see {@code TerrainColumn#variant}) is in the given list. Lets one ecosystem contain
 * meadows, dense forests, lake districts and so on. Outside Underdeep worlds it passes everything.
 * <p>
 * JSON: {@code {"type": "underdeep:variant", "variants": [0, 3]}}
 */
public final class VariantPlacement extends AbstractConditionalPlacementModifier {
    public static final MapCodec<VariantPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.listOf().fieldOf("variants").forGetter(p -> p.variants)
    ).apply(i, VariantPlacement::new));

    private final List<Integer> variants;

    public VariantPlacement(List<Integer> variants) {
        this.variants = variants;
    }

    @Override
    protected boolean shouldPlace(FeaturePlacementContext context, Random random, BlockPos pos) {
        UnderdeepTerrain t = UnderdeepWorlds.terrain(context.getWorld().toServerWorld());
        if (t == null) {
            return true;
        }
        return variants.contains(t.variantAt(pos.getX(), pos.getZ()));
    }

    @Override
    public PlacementModifierType<?> getType() {
        return ModWorldgen.VARIANT;
    }
}
