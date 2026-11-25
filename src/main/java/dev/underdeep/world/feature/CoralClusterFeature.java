package dev.underdeep.world.feature;

import dev.underdeep.registry.ModPlants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * A reef mound of glowing lanterncoral topped by fans, spirecoral and tidecaps. Only replaces water,
 * so reefs grow over the existing submerged terrain.
 */
public final class CoralClusterFeature extends Feature<DefaultFeatureConfig> {
    public CoralClusterFeature() {
        super(DefaultFeatureConfig.CODEC);
    }

    private static boolean water(StructureWorldAccess w, BlockPos p) {
        BlockState s = w.getBlockState(p);
        return s.isOf(Blocks.WATER) && s.getFluidState().isStill();
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
        StructureWorldAccess w = ctx.getWorld();
        BlockPos o = ctx.getOrigin();
        Random r = ctx.getRandom();
        if (!water(w, o)) {
            return false;
        }
        double rad = 1.5 + r.nextDouble() * 1.5;
        int h = 1 + r.nextInt(3);
        int placed = 0;
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > rad) {
                    continue;
                }
                int top = (int) Math.round(h * (1 - d / (rad + 0.5)));
                for (int y = 0; y <= top; y++) {
                    BlockPos p = o.add(x, y, z);
                    if (water(w, p) && (y > 0 || !water(w, p.down()))) {
                        w.setBlockState(p, ModPlants.LANTERNCORAL.getDefaultState(), Block.NOTIFY_LISTENERS);
                        placed++;
                    }
                }
                BlockPos cap = o.add(x, top + 1, z);
                if (water(w, cap) && r.nextFloat() < 0.6f) {
                    float f = r.nextFloat();
                    Block deco = f < 0.45f ? ModPlants.LANTERNCORAL_FAN : f < 0.8f ? ModPlants.SPIRECORAL : ModPlants.TIDECAP;
                    w.setBlockState(cap, deco.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        return placed > 0;
    }
}
