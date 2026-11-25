package dev.underdeep.block.plant;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidFillable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/** A fully submerged plant, coral fan or fungus. Spreads only into still water. */
public class SubmergedPlantBlock extends EcoPlantBlock implements FluidFillable {
    public SubmergedPlantBlock(TagKey<Block> bed, Settings settings) {
        super(bed, settings);
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

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (ambient == null && random.nextInt(24) == 0 && ParticleGate.allow()) {
            world.addParticle(ParticleTypes.BUBBLE, pos.getX() + random.nextDouble(), pos.getY() + 0.5, pos.getZ() + random.nextDouble(), 0, 0.04, 0);
        }
        super.randomDisplayTick(state, world, pos, random);
    }
}
