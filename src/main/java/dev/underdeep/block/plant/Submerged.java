package dev.underdeep.block.plant;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

/** Shared rules for plants that live fully submerged (seagrass-like: the block itself holds water). */
public final class Submerged {
    private Submerged() {
    }

    public static boolean inWater(WorldView world, BlockPos pos) {
        FluidState f = world.getFluidState(pos);
        return f.isIn(FluidTags.WATER) && f.getLevel() == 8;
    }

    public static FluidState water() {
        return Fluids.WATER.getStill(false);
    }

    public static BlockState waterBlock() {
        return Blocks.WATER.getDefaultState();
    }
}
