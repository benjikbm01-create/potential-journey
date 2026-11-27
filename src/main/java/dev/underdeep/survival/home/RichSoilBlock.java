package dev.underdeep.survival.home;

import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.survival.SurvivalBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/** Rich soil and planter boxes: crops, herbs, bushes and fungi grow faster on them and they never dry out. */
public class RichSoilBlock extends Block {
    public RichSoilBlock(Settings settings) {
        super(settings);
    }

    /** Growth multiplier for a plant at {@code plantPos} (1 unless it stands on rich soil). */
    public static double boost(BlockView world, BlockPos plantPos) {
        BlockState below = world.getBlockState(plantPos.down());
        return below.isOf(SurvivalBlocks.RICH_SOIL) || below.isOf(SurvivalBlocks.PLANTER_BOX) ? UnderdeepConfig.get().survival.richSoilGrowth : 1.0;
    }

    public static boolean rich(BlockView world, BlockPos plantPos) {
        BlockState below = world.getBlockState(plantPos.down());
        return below.isOf(SurvivalBlocks.RICH_SOIL) || below.isOf(SurvivalBlocks.PLANTER_BOX);
    }
}
