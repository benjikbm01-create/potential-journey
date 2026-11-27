package dev.underdeep.block.plant;

import com.mojang.serialization.MapCodec;
import dev.underdeep.ecology.EcologyState;
import dev.underdeep.ecology.Habitats;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A resin knot on a log: slowly fills with amber resin (age 0-2); right-click a full knot to collect
 * resin globs. Renewable, never spreads.
 */
public class ResinKnotBlock extends HorizontalFacingBlock {
    public static final IntProperty AGE = Properties.AGE_2;
    private static final VoxelShape SHAPE = Block.createCuboidShape(4, 4, 4, 12, 12, 12);
    private final Supplier<Item> resin;

    public Item resin() {
        return resin.get();
    }

    public ResinKnotBlock(Supplier<Item> resin, Settings settings) {
        super(settings);
        this.resin = resin;
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(AGE, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return MapCodec.unit(this);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, AGE);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return world.getBlockState(pos.offset(state.get(FACING).getOpposite())).isIn(BlockTags.LOGS);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction side = ctx.getSide();
        if (!side.getAxis().isHorizontal()) {
            return null;
        }
        BlockState s = getDefaultState().with(FACING, side);
        return s.canPlaceAt(ctx.getWorld(), ctx.getBlockPos()) ? s : null;
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return !state.canPlaceAt(world, pos) ? net.minecraft.block.Blocks.AIR.getDefaultState() : state;
    }

    @Override
    protected boolean hasRandomTicks(BlockState state) {
        return state.get(AGE) < 2;
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (random.nextInt(8) == 0) {
            world.setBlockState(pos, state.with(AGE, state.get(AGE) + 1), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (state.get(AGE) < 2) {
            return ActionResult.PASS;
        }
        if (!world.isClient) {
            Block.dropStack(world, pos, state.get(FACING), new ItemStack(resin.get(), 1 + world.random.nextInt(2)));
            world.playSound(null, pos, SoundEvents.BLOCK_HONEY_BLOCK_SLIDE, SoundCategory.BLOCKS, 1.0f, 1.0f);
            world.setBlockState(pos, state.with(AGE, 0), Block.NOTIFY_LISTENERS);
            EcologyState.recordHarvest((ServerWorld) world, pos, Habitats.speciesOf(state));
        }
        return ActionResult.success(world.isClient);
    }
}
