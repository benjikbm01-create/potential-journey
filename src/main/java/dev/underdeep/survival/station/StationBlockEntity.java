package dev.underdeep.survival.station;

import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.survival.Recipes;
import dev.underdeep.survival.SurvivalBlocks;
import dev.underdeep.survival.SurvivalContent;
import dev.underdeep.survival.SurvivalContent.Ingredient;
import dev.underdeep.survival.SurvivalContent.Recipe;
import dev.underdeep.survival.SurvivalContent.Station;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Stage 7 workstation (cooking pot, oven, preparation table, kettle, fermentation crock, herbalist's table).
 * <p>
 * Slots: inputs 0-5 (a station uses its first {@link Station#inputs}), 6 = container (bowls / bottles), 7 = fuel
 * (oven), 8 = output. A recipe matches when every non-empty input slot is used by one of its ingredients; the number
 * of batches is limited by ingredients, containers, output room and the station's maximum. Work progresses only
 * while the station has heat (pot, kettle) or fuel (oven); inputs are consumed once, when the work completes, so
 * taking ingredients out early never loses anything. Hoppers: inputs from above, containers and fuel from the sides,
 * output from below.
 */
public class StationBlockEntity extends BlockEntity implements SidedInventory, ExtendedScreenHandlerFactory<StationScreenHandler.Data> {
    public static final int INPUTS = 6, CONTAINER = 6, FUEL = 7, OUTPUT = 8, SIZE = 9;
    private static final int[] TOP = {0, 1, 2, 3, 4, 5}, SIDE = {CONTAINER, FUEL}, BOTTOM = {OUTPUT};

    public final Station station;
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
    private double progress;
    private int burn, burnMax;
    @Nullable
    private String current;
    private int batches;
    private boolean dirty = true;
    private boolean warm;
    @Nullable
    private UUID lastUser;

    /** Synced to the screen: progress, total, heat/fuel state, fuel max, matched recipe index, batches. */
    public final PropertyDelegate props = new PropertyDelegate() {
        @Override
        public int get(int i) {
            Recipe r = current == null ? null : SurvivalContent.recipe(current);
            return switch (i) {
                case 0 -> (int) progress;
                case 1 -> r == null ? 0 : total(r);
                case 2 -> station.fuel ? burn : warm ? 1 : 0;
                case 3 -> burnMax;
                case 4 -> r == null ? -1 : SurvivalContent.recipes(station).indexOf(r);
                case 5 -> batches;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
        }

        @Override
        public int size() {
            return 6;
        }
    };

    public StationBlockEntity(BlockPos pos, BlockState state) {
        super(SurvivalBlocks.STATION_BE, pos, state);
        this.station = state.getBlock() instanceof StationBlock sb ? sb.station : Station.POT;
    }

    static int total(Recipe r) {
        return Math.max(20, r.seconds() * 20);
    }

    // ------------------------------------------------------------------ matching

    /** Result of matching the inputs against a recipe. */
    public record Match(Recipe recipe, int batches, int[] claim) {
    }

    /**
     * Matches inputs against one recipe. {@code claim[slot]} = ingredient index using that slot (-1 = unused).
     * Returns null if any non-empty input is unused or an ingredient is missing; batches 0 if only room is lacking.
     */
    @Nullable
    public static Match match(Recipe r, List<ItemStack> inputs, ItemStack container, ItemStack output, int maxBatch) {
        int n = inputs.size();
        int[] claim = new int[n];
        java.util.Arrays.fill(claim, -1);
        List<Ingredient> ins = r.ingredients();
        int[] have = new int[ins.size()];
        // specific items first, then groups, so a group never steals the slot a specific ingredient needs
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < ins.size(); k++) {
                Ingredient ing = ins.get(k);
                if (ing.group() != (pass == 1)) {
                    continue;
                }
                for (int s = 0; s < n; s++) {
                    if (claim[s] < 0 && SurvivalContent.matches(ing, inputs.get(s))) {
                        claim[s] = k;
                        have[k] += inputs.get(s).getCount();
                    }
                }
            }
        }
        for (int s = 0; s < n; s++) {
            if (!inputs.get(s).isEmpty() && claim[s] < 0) {
                return null;
            }
        }
        int b = maxBatch;
        for (int k = 0; k < ins.size(); k++) {
            b = Math.min(b, have[k] / ins.get(k).count());
        }
        if (b <= 0) {
            return null;
        }
        Item cont = r.containerItem();
        if (cont != null) {
            b = container.isOf(cont) ? Math.min(b, container.getCount() / r.count()) : 0;
        }
        Item res = r.resultItem();
        int room = output.isEmpty() ? res.getMaxCount() : ItemStack.areItemsAndComponentsEqual(output, new ItemStack(res))
                ? output.getMaxCount() - output.getCount() : 0;
        b = Math.min(b, room / Math.max(1, r.count()));
        return new Match(r, Math.max(0, b), claim);
    }

    /** Best matching recipe for these inputs (most ingredient kinds first), or null. */
    @Nullable
    public static Match best(Station st, List<ItemStack> inputs, ItemStack container, ItemStack output) {
        Match best = null;
        for (Recipe r : SurvivalContent.recipes(st)) {
            Match m = match(r, inputs, container, output, st.maxBatch);
            if (m != null && (best == null || r.ingredients().size() > best.recipe().ingredients().size()
                    || (r.ingredients().size() == best.recipe().ingredients().size() && m.batches() > best.batches()))) {
                best = m;
            }
        }
        return best;
    }

    private List<ItemStack> inputs() {
        return items.subList(0, station.inputs);
    }

    // ------------------------------------------------------------------ ticking

    public static void tick(World world, BlockPos pos, BlockState state, StationBlockEntity be) {
        if (world.isClient) {
            return;
        }
        be.serverTick((ServerWorld) world, pos);
    }

    private void serverTick(ServerWorld world, BlockPos pos) {
        warm = station.heat && hasHeat(world, pos);
        if (dirty || world.getTime() % 20 == 0) {
            dirty = false;
            Match m = best(station, inputs(), items.get(CONTAINER), items.get(OUTPUT));
            String id = m == null || m.batches() == 0 ? null : m.recipe().id();
            if (!java.util.Objects.equals(id, current)) {
                current = id;
                progress = 0;
            }
            batches = m == null ? 0 : m.batches();
        }
        Recipe r = current == null ? null : SurvivalContent.recipe(current);
        if (station.fuel && burn > 0) {
            burn--;
        }
        if (r == null) {
            return;
        }
        boolean can = true;
        if (station.heat && !warm) {
            can = false;
        }
        if (station.fuel && burn <= 0) {
            ItemStack f = items.get(FUEL);
            Integer t = f.isEmpty() ? null : FuelRegistry.INSTANCE.get(f.getItem());
            if (t != null && t > 0) {
                burn = burnMax = t;
                Item rem = f.getItem().getRecipeRemainder();
                f.decrement(1);
                if (f.isEmpty() && rem != null) {
                    items.set(FUEL, new ItemStack(rem));
                }
                markDirty();
            } else {
                can = false;
            }
        }
        if (!can) {
            return;
        }
        progress += UnderdeepConfig.get().survival.stationSpeed;
        if (world.getTime() % 20 == 0) {
            effects(world, pos);
        }
        if (progress >= total(r)) {
            Match m = match(r, inputs(), items.get(CONTAINER), items.get(OUTPUT), station.maxBatch);
            progress = 0;
            if (m != null && m.batches() > 0) {
                craft(world, pos, m);
            }
            dirty = true;
        }
    }

    private void effects(ServerWorld world, BlockPos pos) {
        double x = pos.getX() + 0.5, y = pos.getY() + (station == Station.OVEN ? 1.3 : 0.8), z = pos.getZ() + 0.5;
        switch (station) {
            case POT, KETTLE -> world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 2, 0.15, 0.05, 0.15, 0.01);
            case OVEN -> world.spawnParticles(ParticleTypes.SMOKE, x, y, z, 2, 0.1, 0.05, 0.1, 0.01);
            default -> {
            }
        }
        if (world.random.nextInt(4) == 0) {
            world.playSound(null, pos, station == Station.POT || station == Station.KETTLE ? SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP
                    : station == Station.OVEN ? SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE : SoundEvents.BLOCK_WOOD_HIT, SoundCategory.BLOCKS, 0.4f, 1f);
        }
    }

    /** Consumes ingredients and containers for {@code m.batches()} batches and adds the output. Exactly once. */
    void craft(World world, BlockPos pos, Match m) {
        Recipe r = m.recipe();
        List<ItemStack> leftovers = new ArrayList<>();
        for (int k = 0; k < r.ingredients().size(); k++) {
            int need = r.ingredients().get(k).count() * m.batches();
            for (int s = 0; s < station.inputs && need > 0; s++) {
                if (m.claim()[s] != k) {
                    continue;
                }
                ItemStack st = items.get(s);
                int take = Math.min(need, st.getCount());
                Item rem = st.getItem().getRecipeRemainder();
                st.decrement(take);
                need -= take;
                if (rem != null) {
                    ItemStack back = new ItemStack(rem, take);
                    if (st.isEmpty() && take <= back.getMaxCount()) {
                        items.set(s, back);
                    } else {
                        leftovers.add(back);
                    }
                }
            }
        }
        if (r.containerItem() != null) {
            items.get(CONTAINER).decrement(r.count() * m.batches());
        }
        ItemStack out = r.output(m.batches());
        ItemStack cur = items.get(OUTPUT);
        if (cur.isEmpty()) {
            items.set(OUTPUT, out);
        } else {
            cur.increment(out.getCount());
        }
        for (ItemStack l : leftovers) {
            ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, l);
        }
        world.playSound(null, pos, SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.5f, 1.3f);
        if (lastUser != null && world.getPlayerByUuid(lastUser) instanceof ServerPlayerEntity sp && sp.squaredDistanceTo(pos.toCenterPos()) < 64 * 64) {
            Recipes.learn(sp, r, "cooking it");
            String res = net.minecraft.registry.Registries.ITEM.getId(r.resultItem()).getPath();
            String ev = switch (station) {
                case CRUSH -> "crushed";
                case ALLOY -> "alloyed";
                case FORGE -> "forged";
                case GEMS -> "cut_gems";
                case LEATHER -> "leatherwork";
                case HERBAL -> "medicine_made";
                case CROCK, RACK -> "preserved";
                default -> "cooked";
            };
            dev.underdeep.guide.Advancements.event(sp, ev, res);
            dev.underdeep.guide.Advancements.event(sp, "station_used", station.id);
            if (m.batches() >= 4) {
                dev.underdeep.guide.Advancements.event(sp, "batch_cooked", res);
            }
            if (!r.civ().isEmpty()) {
                dev.underdeep.guide.Advancements.event(sp, "regional_dish", r.civ());
            }
        }
        crafted += m.batches();
        markDirty();
    }

    /** Total batches completed (diagnostics, tests). */
    public int crafted;

    public static boolean hasHeat(World world, BlockPos pos) {
        BlockState b = world.getBlockState(pos.down());
        if (b.isIn(BlockTags.CAMPFIRES)) {
            return b.contains(CampfireBlock.LIT) && b.get(CampfireBlock.LIT);
        }
        if (b.getBlock() instanceof AbstractFurnaceBlock) {
            return b.get(AbstractFurnaceBlock.LIT);
        }
        return b.isIn(BlockTags.FIRE) || b.isOf(Blocks.LAVA) || b.isOf(Blocks.MAGMA_BLOCK) || b.isIn(SurvivalBlocks.HEAT_SOURCES);
    }

    public void setUser(PlayerEntity p) {
        lastUser = p.getUuid();
    }

    // ------------------------------------------------------------------ test hooks

    /** Runs the work instantly as if the time had passed (tests). Returns batches made. */
    public int finishNowForTest() {
        Match m = best(station, inputs(), items.get(CONTAINER), items.get(OUTPUT));
        if (m == null || m.batches() == 0) {
            return 0;
        }
        craft(world, pos, m);
        return m.batches();
    }

    @Nullable
    public String currentForTest() {
        Match m = best(station, inputs(), items.get(CONTAINER), items.get(OUTPUT));
        return m == null || m.batches() == 0 ? null : m.recipe().id();
    }

    public double progressForTest() {
        return progress;
    }

    // ------------------------------------------------------------------ inventory

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
        if (!s.isEmpty()) {
            dirty = true;
            markDirty();
        }
        return s;
    }

    @Override
    public ItemStack removeStack(int slot) {
        dirty = true;
        return Inventories.removeStack(items, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.capCount(getMaxCount(stack));
        dirty = true;
        markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return world != null && world.getBlockEntity(pos) == this && player.squaredDistanceTo(pos.toCenterPos()) <= 64;
    }

    @Override
    public void clear() {
        items.clear();
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (slot == OUTPUT) {
            return false;
        }
        if (slot == FUEL) {
            return station.fuel && FuelRegistry.INSTANCE.get(stack.getItem()) != null && FuelRegistry.INSTANCE.get(stack.getItem()) > 0;
        }
        if (slot == CONTAINER) {
            return stack.isOf(Items.BOWL) || stack.isOf(Items.GLASS_BOTTLE);
        }
        return slot < station.inputs;
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return side == Direction.UP ? java.util.Arrays.copyOf(TOP, station.inputs) : side == Direction.DOWN ? BOTTOM : SIDE;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return isValid(slot, stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return slot == OUTPUT && dir == Direction.DOWN || slot == FUEL && stack.isOf(Items.BUCKET);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup reg) {
        super.writeNbt(nbt, reg);
        Inventories.writeNbt(nbt, items, reg);
        nbt.putDouble("progress", progress);
        nbt.putInt("burn", burn);
        nbt.putInt("burnMax", burnMax);
        nbt.putInt("crafted", crafted);
        if (current != null) {
            nbt.putString("recipe", current);
        }
        if (lastUser != null) {
            nbt.putUuid("user", lastUser);
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup reg) {
        super.readNbt(nbt, reg);
        items.clear();
        Inventories.readNbt(nbt, items, reg);
        progress = nbt.getDouble("progress");
        burn = nbt.getInt("burn");
        burnMax = nbt.getInt("burnMax");
        crafted = nbt.getInt("crafted");
        current = nbt.contains("recipe") ? nbt.getString("recipe") : null;
        lastUser = nbt.containsUuid("user") ? nbt.getUuid("user") : null;
        dirty = true;
    }

    // ------------------------------------------------------------------ screen

    @Override
    public StationScreenHandler.Data getScreenOpeningData(ServerPlayerEntity player) {
        return new StationScreenHandler.Data(station.id, pos);
    }

    @Override
    public Text getDisplayName() {
        return getCachedState().getBlock().getName();
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        setUser(player);
        return new StationScreenHandler(syncId, inv, this, props, station, pos);
    }
}
