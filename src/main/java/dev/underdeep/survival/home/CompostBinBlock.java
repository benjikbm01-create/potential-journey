package dev.underdeep.survival.home;

import dev.underdeep.survival.SurvivalItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ComposterBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

/**
 * Compost bin: fills exactly like the vanilla composter (same compostable items and chances, hopper input from
 * above) but yields compost instead of bone meal (hoppers below take it out).
 */
public class CompostBinBlock extends ComposterBlock {
    public static final int YIELD = 1;

    public CompostBinBlock(Settings settings) {
        super(settings);
    }

    static ItemStack output() {
        return new ItemStack(SurvivalItems.get("compost"), YIELD);
    }

    public static void empty(World world, BlockPos pos, BlockState state) {
        if (!world.isClient) {
            ItemEntity e = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 1.01, pos.getZ() + 0.5, output());
            e.setToDefaultPickupDelay();
            world.spawnEntity(e);
        }
        world.setBlockState(pos, state.with(LEVEL, 0), Block.NOTIFY_ALL);
        world.playSound(null, pos, SoundEvents.BLOCK_COMPOSTER_EMPTY, SoundCategory.BLOCKS, 1f, 1f);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                                             BlockHitResult hit) {
        if (state.get(LEVEL) == 8) {
            empty(world, pos, state);
            return ItemActionResult.success(world.isClient);
        }
        return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (state.get(LEVEL) == 8) {
            empty(world, pos, state);
            return ActionResult.success(world.isClient);
        }
        return ActionResult.PASS;
    }

    @Override
    public SidedInventory getInventory(BlockState state, WorldAccess world, BlockPos pos) {
        if (state.get(LEVEL) == 8) {
            return new Full(state, world, pos);
        }
        return super.getInventory(state, world, pos);
    }

    static final class Full extends SimpleInventory implements SidedInventory {
        private final BlockState state;
        private final WorldAccess world;
        private final BlockPos pos;
        private boolean done;

        Full(BlockState state, WorldAccess world, BlockPos pos) {
            super(output());
            this.state = state;
            this.world = world;
            this.pos = pos;
        }

        @Override
        public int[] getAvailableSlots(Direction side) {
            return side == Direction.DOWN ? new int[]{0} : new int[0];
        }

        @Override
        public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
            return false;
        }

        @Override
        public boolean canExtract(int slot, ItemStack stack, Direction dir) {
            return !done && dir == Direction.DOWN;
        }

        @Override
        public void markDirty() {
            // the extraction empties the bin
            if (!done && getStack(0).getCount() < YIELD) {
                done = true;
                world.setBlockState(pos, state.with(LEVEL, 0), Block.NOTIFY_ALL);
            }
        }
    }
}
