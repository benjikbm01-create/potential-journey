package dev.underdeep.survival.station;

import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.survival.SurvivalBlocks;
import dev.underdeep.survival.SurvivalContent;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Drying rack: four hanging spots. Each item dries on its own timer into its preserved form (jerky, dried fish,
 * fruit, mushrooms, kelp, leather). Right-click to hang, right-click with an empty hand to take one down. Hoppers
 * may hang items from above and take finished items from below.
 */
public class DryingRackBlockEntity extends BlockEntity implements SidedInventory {
    public static final int SIZE = 4;
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
    private final double[] time = new double[SIZE];

    public DryingRackBlockEntity(BlockPos pos, BlockState state) {
        super(SurvivalBlocks.RACK_BE, pos, state);
    }

    @Nullable
    public static SurvivalContent.Recipe recipeFor(ItemStack s) {
        for (SurvivalContent.Recipe r : SurvivalContent.recipes(SurvivalContent.Station.RACK)) {
            if (SurvivalContent.matches(r.ingredients().get(0), s)) {
                return r;
            }
        }
        return null;
    }

    public List<ItemStack> items() {
        return items;
    }

    /** Hangs one item; returns true on success. */
    public boolean hang(ItemStack s) {
        if (recipeFor(s) == null) {
            return false;
        }
        for (int i = 0; i < SIZE; i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, s.copyWithCount(1));
                time[i] = 0;
                changed();
                return true;
            }
        }
        return false;
    }

    /** Takes down the last hung item (finished or not). */
    public ItemStack take() {
        for (int i = SIZE - 1; i >= 0; i--) {
            if (!items.get(i).isEmpty()) {
                ItemStack s = items.get(i);
                items.set(i, ItemStack.EMPTY);
                time[i] = 0;
                changed();
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    public static void tick(World world, BlockPos pos, BlockState state, DryingRackBlockEntity be) {
        if (world.isClient) {
            return;
        }
        boolean ch = false;
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = be.items.get(i);
            SurvivalContent.Recipe r = s.isEmpty() ? null : recipeFor(s);
            if (r == null) {
                continue;
            }
            // dries faster in dry heat, slower in rain or water
            double rate = UnderdeepConfig.get().survival.stationSpeed * (world.hasRain(pos) ? 0.5 : 1.0);
            be.time[i] += rate;
            if (be.time[i] >= r.seconds() * 20) {
                be.items.set(i, r.output(1));
                be.time[i] = 0;
                ch = true;
            }
        }
        if (ch) {
            be.changed();
        }
    }

    /** Dries everything now (tests). */
    public void finishNowForTest() {
        for (int i = 0; i < SIZE; i++) {
            SurvivalContent.Recipe r = items.get(i).isEmpty() ? null : recipeFor(items.get(i));
            if (r != null) {
                items.set(i, r.output(1));
            }
        }
        changed();
    }

    private void changed() {
        markDirty();
        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup reg) {
        super.writeNbt(nbt, reg);
        Inventories.writeNbt(nbt, items, true, reg);
        for (int i = 0; i < SIZE; i++) {
            nbt.putDouble("t" + i, time[i]);
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup reg) {
        super.readNbt(nbt, reg);
        items.clear();
        Inventories.readNbt(nbt, items, reg);
        for (int i = 0; i < SIZE; i++) {
            time[i] = nbt.getDouble("t" + i);
        }
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup reg) {
        return createNbt(reg);
    }

    // inventory (hoppers)
    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getStack(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack s = Inventories.splitStack(items, slot, amount);
        time[slot] = 0;
        changed();
        return s;
    }

    @Override
    public ItemStack removeStack(int slot) {
        time[slot] = 0;
        return Inventories.removeStack(items, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack.copyWithCount(Math.min(1, stack.getCount())));
        time[slot] = 0;
        changed();
    }

    @Override
    public int getMaxCountPerStack() {
        return 1;
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void clear() {
        items.clear();
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return new int[]{0, 1, 2, 3};
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return dir != Direction.DOWN && items.get(slot).isEmpty() && recipeFor(stack) != null;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return dir == Direction.DOWN && recipeFor(stack) == null;
    }
}
