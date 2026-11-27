package dev.underdeep.survival.home;

import com.mojang.serialization.MapCodec;
import dev.underdeep.survival.SurvivalBlocks;
import dev.underdeep.survival.SurvivalContent;
import dev.underdeep.survival.SurvivalItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

/** Storage furniture, fish trap and feeding trough. */
public class ContainerBlock extends BlockWithEntity implements Waterloggable {
    public enum Kind {
        CABINET(27), PANTRY(27), MEDICINE(18), CRATE(18), COUNTER(9), TRAP(9), TROUGH(9);

        public final int size;

        Kind(int size) {
            this.size = size;
        }

        public boolean filtered() {
            return this == PANTRY || this == MEDICINE || this == TRAP || this == TROUGH;
        }

        public boolean accepts(ItemStack s) {
            return switch (this) {
                case PANTRY -> s.contains(DataComponentTypes.FOOD) || SurvivalContent.groupsOf(s.getItem()).size() > 0
                        || ingredient(s);
                case MEDICINE -> s.getItem() instanceof SurvivalItems.MedicineItem || s.getItem() instanceof dev.underdeep.item.RemedyItem
                        || s.isIn(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.ITEM, dev.underdeep.registry.Reg.id("herb")))
                        || s.isOf(net.minecraft.item.Items.GLASS_BOTTLE);
                case TRAP -> true; // bait in, catch out (hoppers take from below)
                case TROUGH -> ContainerBlockEntity.isFeed(s) || s.isOf(SurvivalItems.get("fishing_bait"));
                default -> true;
            };
        }

        private static boolean ingredient(ItemStack s) {
            String id = Registries.ITEM.getId(s.getItem()).getPath();
            return SurvivalContent.MATERIALS.contains(id) || s.isOf(net.minecraft.item.Items.SUGAR) || s.isOf(net.minecraft.item.Items.WHEAT)
                    || s.isOf(net.minecraft.item.Items.EGG) || s.isOf(net.minecraft.item.Items.MILK_BUCKET);
        }
    }

    public final Kind kind;
    private final VoxelShape shape;

    public ContainerBlock(Settings settings, Kind kind, VoxelShape shape) {
        super(settings);
        this.kind = kind;
        this.shape = shape;
        setDefaultState(getStateManager().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH).with(Properties.WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(s -> new ContainerBlock(s, kind, shape));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HorizontalFacingBlock.FACING, Properties.WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        FluidState f = ctx.getWorld().getFluidState(ctx.getBlockPos());
        return getDefaultState().with(HorizontalFacingBlock.FACING, ctx.getHorizontalPlayerFacing().getOpposite())
                .with(Properties.WATERLOGGED, f.getFluid() == Fluids.WATER);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.get(Properties.WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world,
                                                   BlockPos pos, BlockPos neighborPos) {
        if (state.get(Properties.WATERLOGGED)) {
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shape;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ContainerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient || (kind != Kind.TRAP && kind != Kind.TROUGH) ? null
                : validateTicker(type, SurvivalBlocks.CONTAINER_BE, ContainerBlockEntity::tick);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof ContainerBlockEntity be) {
            player.openHandledScreen(be);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        // the crate keeps its contents in the dropped item (loot table copies the container component)
        if (!state.isOf(newState.getBlock()) && kind != Kind.CRATE && world.getBlockEntity(pos) instanceof ContainerBlockEntity be) {
            ItemScatterer.spawn(world, pos, be);
            world.updateComparators(pos, this);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    protected boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
    }
}
