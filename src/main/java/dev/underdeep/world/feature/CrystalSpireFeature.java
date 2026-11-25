package dev.underdeep.world.feature;

import com.mojang.serialization.Codec;
import dev.underdeep.registry.ModBlocks;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * A glowing crystal spire growing from a cavern floor (or hanging from a ceiling): a tapering cone
 * with a luminite core, a calcite/amethyst sheath and a crystal cluster at the tip. Radius is kept
 * small (&lt;= 3) so the feature never writes outside the 3x3 chunk generation window.
 */
public final class CrystalSpireFeature extends Feature<DefaultFeatureConfig> {
    public CrystalSpireFeature(Codec<DefaultFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
        StructureWorldAccess world = ctx.getWorld();
        BlockPos origin = ctx.getOrigin();
        Random random = ctx.getRandom();
        Direction dir;
        if (world.getBlockState(origin.down()).isSolidBlock(world, origin.down())) {
            dir = Direction.UP;
        } else if (world.getBlockState(origin.up()).isSolidBlock(world, origin.up())) {
            dir = Direction.DOWN;
        } else {
            return false;
        }
        int height = 5 + random.nextInt(dir == Direction.UP ? 12 : 8);
        double baseRadius = 1.2 + random.nextDouble() * 1.8;
        BlockState core = ModBlocks.LUMINITE_BLOCK.getDefaultState();
        BlockState shell = random.nextBoolean() ? Blocks.CALCITE.getDefaultState() : Blocks.AMETHYST_BLOCK.getDefaultState();
        BlockPos.Mutable m = new BlockPos.Mutable();
        int placed = 0;
        // Bury the base a little so spires on slopes do not float.
        for (int i = -2; i < height; i++) {
            double t = Math.max(0, i) / (double) height;
            double r = baseRadius * (1 - t) + 0.3;
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > r) {
                        continue;
                    }
                    m.set(origin.getX() + dx, origin.getY() + i * dir.getOffsetY(), origin.getZ() + dz);
                    BlockState existing = world.getBlockState(m);
                    if (!(existing.isAir() || existing.isReplaceable() || i < 0)) {
                        continue;
                    }
                    if (i < 0 && existing.isAir()) {
                        continue;
                    }
                    world.setBlockState(m, d < r - 1.0 || r < 1.2 ? core : shell, 2);
                    placed++;
                }
            }
        }
        m.set(origin.getX(), origin.getY() + height * dir.getOffsetY(), origin.getZ());
        if (world.getBlockState(m).isAir()) {
            world.setBlockState(m, ModBlocks.LUMINITE_CLUSTER.getDefaultState().with(AmethystClusterBlock.FACING, dir), 2);
        }
        return placed > 0;
    }
}
