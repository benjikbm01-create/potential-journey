package dev.underdeep.block.plant;

import com.mojang.serialization.MapCodec;
import dev.underdeep.registry.ModTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/** Bracket (shelf) fungus growing out of the side of logs, rotting wood and some stone. FACING points away from the host. */
public class ShelfFungusBlock extends HorizontalFacingBlock {
    private static final VoxelShape NORTH = Block.createCuboidShape(2, 6, 9, 14, 10, 16);
    private static final VoxelShape SOUTH = Block.createCuboidShape(2, 6, 0, 14, 10, 7);
    private static final VoxelShape WEST = Block.createCuboidShape(9, 6, 2, 16, 10, 14);
    private static final VoxelShape EAST = Block.createCuboidShape(0, 6, 2, 7, 10, 14);

    public ShelfFungusBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return MapCodec.unit(this);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        Direction f = state.get(FACING);
        return world.getBlockState(pos.offset(f.getOpposite())).isIn(ModTags.SHELF_FUNGUS_HOSTS);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        for (Direction d : ctx.getPlacementDirections()) {
            if (d.getAxis().isHorizontal()) {
                BlockState s = getDefaultState().with(FACING, d.getOpposite());
                if (s.canPlaceAt(ctx.getWorld(), ctx.getBlockPos())) {
                    return s;
                }
            }
        }
        return null;
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return direction == state.get(FACING).getOpposite() && !state.canPlaceAt(world, pos)
                ? net.minecraft.block.Blocks.AIR.getDefaultState() : state;
    }
}
