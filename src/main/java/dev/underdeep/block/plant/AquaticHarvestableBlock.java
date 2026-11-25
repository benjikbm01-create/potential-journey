package dev.underdeep.block.plant;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidFillable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** Submerged harvestable plant (sea grapes, siltwort). */
public class AquaticHarvestableBlock extends HarvestablePlantBlock implements FluidFillable {
    public AquaticHarvestableBlock(TagKey<Block> bed, Supplier<Item> produce, int min, int max, Settings settings) {
        super(bed, produce, min, max, settings);
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return Submerged.inWater(world, pos) && super.canPlaceAt(state, world, pos);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return Submerged.inWater(ctx.getWorld(), ctx.getBlockPos()) ? super.getPlacementState(ctx) : null;
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (!state.canPlaceAt(world, pos)) {
            return Submerged.waterBlock();
        }
        world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        return state;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Submerged.water();
    }

    @Override
    public boolean canFillWithFluid(@Nullable PlayerEntity player, BlockView world, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean tryFillWithFluid(WorldAccess world, BlockPos pos, BlockState state, FluidState fluidState) {
        return false;
    }

    @Override
    protected boolean spread(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        BlockState child = getDefaultState();
        return Spreading.trySpread(world, pos, child, spreadRadius, spreadCap, random, Spreading.water(child));
    }
}
