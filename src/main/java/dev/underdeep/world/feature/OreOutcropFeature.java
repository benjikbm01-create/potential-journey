package dev.underdeep.world.feature;

import com.mojang.serialization.Codec;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * A small boulder on a cavern floor with visible coal, iron and copper ore. Used to guarantee that the
 * starting cavern offers basic metals without requiring blind tunnelling.
 */
public final class OreOutcropFeature extends Feature<DefaultFeatureConfig> {
    public OreOutcropFeature(Codec<DefaultFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
        StructureWorldAccess world = ctx.getWorld();
        BlockPos origin = ctx.getOrigin();
        Random random = ctx.getRandom();
        if (!world.getBlockState(origin.down()).isSolidBlock(world, origin.down())) {
            return false;
        }
        double r = 1.8 + random.nextDouble() * 1.2;
        int ri = (int) Math.ceil(r);
        BlockState[] ores = {Blocks.COAL_ORE.getDefaultState(), Blocks.COAL_ORE.getDefaultState(),
                Blocks.IRON_ORE.getDefaultState(), Blocks.IRON_ORE.getDefaultState(), Blocks.COPPER_ORE.getDefaultState()};
        BlockPos.Mutable m = new BlockPos.Mutable();
        int placed = 0;
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -1; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
                    if (d > r) {
                        continue;
                    }
                    m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState s = world.getBlockState(m);
                    if (!(s.isAir() || s.isReplaceable() || s.isOf(Blocks.STONE) || s.isIn(net.minecraft.registry.tag.BlockTags.DIRT))) {
                        continue;
                    }
                    BlockState place = random.nextFloat() < 0.38f ? ores[random.nextInt(ores.length)] : Blocks.STONE.getDefaultState();
                    world.setBlockState(m, place, 2);
                    placed++;
                }
            }
        }
        return placed > 0;
    }
}
