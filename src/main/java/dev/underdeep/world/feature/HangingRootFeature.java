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
 * Great roots descending from a Rootwild ceiling: a wandering, tapering strand of wood and mangrove
 * roots ending in hanging roots. Provides wood high up and gives forest caverns their silhouette.
 * Horizontal wander is clamped to 3 blocks so the feature stays within its generation window.
 */
public final class HangingRootFeature extends Feature<DefaultFeatureConfig> {
    public HangingRootFeature(Codec<DefaultFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
        StructureWorldAccess world = ctx.getWorld();
        BlockPos origin = ctx.getOrigin();
        Random random = ctx.getRandom();
        if (!world.getBlockState(origin.up()).isSolidBlock(world, origin.up())) {
            return false;
        }
        int length = 6 + random.nextInt(22);
        double x = 0;
        double z = 0;
        double vx = random.nextGaussian() * 0.25;
        double vz = random.nextGaussian() * 0.25;
        BlockPos.Mutable m = new BlockPos.Mutable();
        BlockState wood = Blocks.DARK_OAK_WOOD.getDefaultState();
        BlockState roots = Blocks.MANGROVE_ROOTS.getDefaultState();
        int placed = 0;
        for (int i = 0; i < length; i++) {
            double t = i / (double) length;
            double r = 1.6 * (1 - t) + 0.4;
            vx += random.nextGaussian() * 0.12;
            vz += random.nextGaussian() * 0.12;
            x = Math.max(-3, Math.min(3, x + vx * 0.5));
            z = Math.max(-3, Math.min(3, z + vz * 0.5));
            int ri = (int) Math.ceil(r);
            boolean blocked = false;
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > r * r) {
                        continue;
                    }
                    m.set(origin.getX() + (int) Math.round(x) + dx, origin.getY() - i, origin.getZ() + (int) Math.round(z) + dz);
                    BlockState s = world.getBlockState(m);
                    if (s.isAir()) {
                        boolean edge = dx * dx + dz * dz > (r - 1) * (r - 1);
                        world.setBlockState(m, edge && random.nextFloat() < 0.6f ? roots : wood, 2);
                        placed++;
                    } else if (dx == 0 && dz == 0 && i > 2 && !s.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) {
                        blocked = true;
                    }
                }
            }
            if (blocked) {
                break;
            }
        }
        // Dangling tips
        m.set(origin.getX() + (int) Math.round(x), origin.getY() - length, origin.getZ() + (int) Math.round(z));
        for (int i = 0; i < 3 && world.getBlockState(m).isAir(); i++) {
            if (i == 0) {
                world.setBlockState(m, Blocks.HANGING_ROOTS.getDefaultState(), 2);
            }
            m.move(0, -1, 0);
        }
        return placed > 0;
    }
}
