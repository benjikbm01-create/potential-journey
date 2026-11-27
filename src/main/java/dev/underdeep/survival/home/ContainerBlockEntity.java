package dev.underdeep.survival.home;

import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.survival.Fishing;
import dev.underdeep.survival.SurvivalBlocks;
import dev.underdeep.survival.SurvivalItems;
import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.UnderdeepCreature;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

/**
 * Storage furniture (cabinet, pantry, medicine cabinet, crate, counter), the fish trap and the feeding trough.
 * Uses the vanilla chest screen; filtered kinds (pantry: food; medicine cabinet: medicine and herbs; trap: bait and
 * catch; trough: animal feed) reject other items in the screen and from hoppers.
 */
public class ContainerBlockEntity extends LootableContainerBlockEntity {
    private DefaultedList<ItemStack> items;
    public final ContainerBlock.Kind kind;
    private long lastTick = -1;
    private double trapTime;
    /** Catches made / feedings given (diagnostics, tests). */
    public int produced;

    public ContainerBlockEntity(BlockPos pos, BlockState state) {
        super(SurvivalBlocks.CONTAINER_BE, pos, state);
        this.kind = state.getBlock() instanceof ContainerBlock cb ? cb.kind : ContainerBlock.Kind.CABINET;
        this.items = DefaultedList.ofSize(kind.size, ItemStack.EMPTY);
    }

    @Override
    protected DefaultedList<ItemStack> getHeldStacks() {
        return items;
    }

    @Override
    protected void setHeldStacks(DefaultedList<ItemStack> list) {
        this.items = list;
    }

    @Override
    protected Text getContainerName() {
        return getCachedState().getBlock().getName();
    }

    @Override
    public int size() {
        return kind.size;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return kind.accepts(stack);
    }

    @Override
    protected ScreenHandler createScreenHandler(int syncId, PlayerInventory inv) {
        int rows = kind.size / 9;
        ScreenHandlerType<GenericContainerScreenHandler> type = rows == 1 ? ScreenHandlerType.GENERIC_9X1
                : rows == 2 ? ScreenHandlerType.GENERIC_9X2 : ScreenHandlerType.GENERIC_9X3;
        GenericContainerScreenHandler h = new GenericContainerScreenHandler(type, syncId, inv, this, rows);
        if (kind.filtered()) {
            for (int i = 0; i < kind.size; i++) {
                Slot old = h.slots.get(i);
                Inventory me = this;
                Slot ns = new Slot(me, old.getIndex(), old.x, old.y) {
                    @Override
                    public boolean canInsert(ItemStack stack) {
                        return me.isValid(getIndex(), stack);
                    }
                };
                ns.id = old.id;
                h.slots.set(i, ns);
            }
        }
        return h;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup reg) {
        super.writeNbt(nbt, reg);
        if (!writeLootTable(nbt)) {
            net.minecraft.inventory.Inventories.writeNbt(nbt, items, reg);
        }
        nbt.putDouble("trapTime", trapTime);
        nbt.putLong("lastTick", lastTick);
        nbt.putInt("produced", produced);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup reg) {
        super.readNbt(nbt, reg);
        items = DefaultedList.ofSize(kind.size, ItemStack.EMPTY);
        if (!readLootTable(nbt)) {
            net.minecraft.inventory.Inventories.readNbt(nbt, items, reg);
        }
        trapTime = nbt.getDouble("trapTime");
        lastTick = nbt.contains("lastTick") ? nbt.getLong("lastTick") : -1;
        produced = nbt.getInt("produced");
    }

    // ------------------------------------------------------------------ ticking (trap, trough)

    public static void tick(World world, BlockPos pos, BlockState state, ContainerBlockEntity be) {
        if (world.isClient || world.getTime() % 20 != (pos.asLong() & 15)) {
            return;
        }
        long now = world.getTime();
        long delta = be.lastTick < 0 ? 20 : Math.max(0, now - be.lastTick);
        be.lastTick = now;
        if (be.kind == ContainerBlock.Kind.TRAP) {
            be.trapTick((ServerWorld) world, pos, state, delta);
        } else if (be.kind == ContainerBlock.Kind.TROUGH && world.getTime() % 100 < 20) {
            be.troughTick((ServerWorld) world, pos);
        }
    }

    private void trapTick(ServerWorld world, BlockPos pos, BlockState state, long delta) {
        UnderdeepConfig.Survival cfg = UnderdeepConfig.get().survival;
        if (!state.contains(Properties.WATERLOGGED) || !state.get(Properties.WATERLOGGED) || baitSlot() < 0) {
            return;
        }
        // catch-up after the area was unloaded is bounded
        trapTime += Math.min(delta, (long) cfg.fishTrapInterval * cfg.fishTrapMaxCatchUp);
        int guard = cfg.fishTrapMaxCatchUp;
        while (trapTime >= cfg.fishTrapInterval && guard-- > 0) {
            trapTime -= cfg.fishTrapInterval;
            if (!catchOne(world, pos)) {
                trapTime = 0;
                break;
            }
        }
        trapTime = Math.min(trapTime, cfg.fishTrapInterval);
        markDirty();
    }

    /** One catch attempt: uses one bait if it catches anything. Returns false when out of bait or full. */
    public boolean catchOne(ServerWorld world, BlockPos pos) {
        int bait = baitSlot();
        if (bait < 0) {
            return false;
        }
        int crowd = 0;
        for (BlockPos p : BlockPos.iterate(pos.add(-4, -2, -4), pos.add(4, 2, 4))) {
            if (!p.equals(pos) && world.getBlockState(p).isOf(SurvivalBlocks.FISH_TRAP)) {
                crowd++;
            }
        }
        // crowded traps share the same fish
        if (crowd > 2 && world.random.nextInt(crowd) > 1) {
            return true;
        }
        ItemStack c = Fishing.catchAt(world, pos, world.random, true);
        if (c.isEmpty()) {
            return true;
        }
        if (!insert(c)) {
            return false;
        }
        items.get(bait).decrement(1);
        produced++;
        return true;
    }

    private int baitSlot() {
        Item bait = SurvivalItems.get("fishing_bait");
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isOf(bait)) {
                return i;
            }
        }
        return -1;
    }

    private boolean insert(ItemStack c) {
        for (int i = 0; i < items.size() && !c.isEmpty(); i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) {
                items.set(i, c.copy());
                c.setCount(0);
            } else if (ItemStack.areItemsAndComponentsEqual(s, c) && s.getCount() < s.getMaxCount()) {
                int m = Math.min(c.getCount(), s.getMaxCount() - s.getCount());
                s.increment(m);
                c.decrement(m);
            }
        }
        return c.isEmpty();
    }

    /** Feeds hungry tamed animals around the trough with matching feed; may encourage well-fed pairs to breed. */
    public int troughTick(ServerWorld world, BlockPos pos) {
        UnderdeepConfig.Survival cfg = UnderdeepConfig.get().survival;
        int r = cfg.troughRadius;
        List<UnderdeepCreature> near = world.getEntitiesByClass(UnderdeepCreature.class, new Box(pos).expand(r, 4, r),
                c -> c.isTamed() && c.isAlive());
        int fed = 0;
        for (UnderdeepCreature c : near) {
            int slot = feedFor(c);
            if (slot >= 0 && c.troughFed()) {
                items.get(slot).decrement(1);
                fed++;
                produced++;
                // breeding: well-fed adults, a population cap near the trough, normal vanilla cooldowns
                if (!c.isBaby() && c.getBreedingAge() == 0 && !c.isInLove() && near.size() < cfg.breedingCap && world.random.nextInt(6) == 0) {
                    c.lovePlayer(null);
                }
            }
        }
        if (fed > 0) {
            markDirty();
        }
        return fed;
    }

    private int feedFor(UnderdeepCreature c) {
        CreatureSpecies sp = c.species();
        String feed = switch (sp.diet()) {
            case GRAZER, BROWSER -> "grain_feed";
            case FUNGIVORE -> "fungal_feed";
            case NECTAR, OMNIVORE -> "fruit_mash";
            case CARNIVORE, SCAVENGER -> "meat_scraps";
            case LITHOVORE -> "mineral_lick";
            case AQUATIC_GRAZER, FILTER -> "fishing_bait";
        };
        Item f = SurvivalItems.get(feed);
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (s.isOf(f) || (!s.isEmpty() && sp.diet() == CreatureSpecies.Diet.OMNIVORE && isFeed(s)) || (!s.isEmpty() && c.isBreedingItem(s))) {
                return i;
            }
        }
        return -1;
    }

    static boolean isFeed(ItemStack s) {
        for (String f : new String[]{"grain_feed", "fungal_feed", "fruit_mash", "meat_scraps", "mineral_lick"}) {
            if (s.isOf(SurvivalItems.get(f))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return super.canPlayerUse(player);
    }
}
