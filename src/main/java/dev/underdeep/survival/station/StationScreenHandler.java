package dev.underdeep.survival.station;

import dev.underdeep.survival.SurvivalBlocks;
import dev.underdeep.survival.SurvivalContent.Station;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/** Container menu for every Stage 7 workstation. Slot layout depends on the station (inputs, container, fuel, output). */
public class StationScreenHandler extends ScreenHandler {
    public record Data(String station, BlockPos pos) {
        public static final PacketCodec<RegistryByteBuf, Data> CODEC = PacketCodec.of((d, buf) -> {
            buf.writeString(d.station);
            buf.writeBlockPos(d.pos);
        }, buf -> new Data(buf.readString(), buf.readBlockPos()));
    }

    public final Inventory inventory;
    public final PropertyDelegate props;
    public final Station station;
    public final BlockPos pos;
    /** Index in {@link #slots} of the first player-inventory slot. */
    public final int playerStart;

    /** Client constructor. */
    public StationScreenHandler(int syncId, PlayerInventory inv, Data data) {
        this(syncId, inv, new SimpleInventory(StationBlockEntity.SIZE), new ArrayPropertyDelegate(6), Station.of(data.station()), data.pos());
    }

    public StationScreenHandler(int syncId, PlayerInventory inv, Inventory inventory, PropertyDelegate props, Station station, BlockPos pos) {
        super(SurvivalBlocks.STATION_SCREEN, syncId);
        this.inventory = inventory;
        this.props = props;
        this.station = station;
        this.pos = pos;
        checkSize(inventory, StationBlockEntity.SIZE);
        inventory.onOpen(inv.player);
        int cols = station.inputs == 4 ? 2 : 3;
        for (int i = 0; i < station.inputs; i++) {
            addSlot(new Slot(inventory, i, 26 + (i % cols) * 18, 17 + (i / cols) * 18));
        }
        addSlot(new Slot(inventory, StationBlockEntity.CONTAINER, 26, 62) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return inventory.isValid(StationBlockEntity.CONTAINER, stack);
            }
        });
        if (station.fuel) {
            addSlot(new Slot(inventory, StationBlockEntity.FUEL, 98, 53) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return inventory.isValid(StationBlockEntity.FUEL, stack);
                }
            });
        }
        addSlot(new Slot(inventory, StationBlockEntity.OUTPUT, 134, 35) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }
        });
        playerStart = slots.size();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, 8 + c * 18, 142));
        }
        addProperties(props);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack s = slot.getStack();
        ItemStack copy = s.copy();
        if (index < playerStart) {
            if (!insertItem(s, playerStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            // containers and fuel to their slots first, then the inputs
            for (int i = 0; i < playerStart && !moved; i++) {
                Slot t = slots.get(i);
                int idx = t.getIndex();
                if ((idx == StationBlockEntity.CONTAINER || idx == StationBlockEntity.FUEL) && t.canInsert(s)) {
                    moved = insertItem(s, i, i + 1, false);
                }
            }
            if (!moved) {
                int inEnd = station.inputs;
                if (!insertItem(s, 0, inEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (s.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        if (s.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTakeItem(player, s);
        return copy;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return inventory.canPlayerUse(player);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        inventory.onClose(player);
    }
}
