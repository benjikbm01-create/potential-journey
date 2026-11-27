package dev.underdeep.survival;

import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Regional fishing (Stage 7). In the Underdeep each region's waters hold their own fish; a fishing rod catches a
 * regional fish most of the time (vanilla loot otherwise), bait held in the other hand improves the odds, and every
 * catch adds fishing pressure to that stretch of water: heavily fished water yields less until it recovers (a few
 * days). Fish traps use the same tables and pressure.
 */
public final class Fishing {
    private Fishing() {
    }

    record Entry(String item, int weight) {
    }

    private static final Map<RegionType, List<Entry>> TABLES = new EnumMap<>(RegionType.class);

    static {
        t(RegionType.ROOTWILD, "fish_fillet", 5, "minecraft:cod", 3, "minecraft:salmon", 2);
        t(RegionType.FUNGAL_EXPANSE, "fish_fillet", 4, "glimmerfin", 2, "mire_eel", 1);
        t(RegionType.FLOODED_DEPTHS, "fish_fillet", 4, "glimmerfin", 3, "shellmeat", 2, "minecraft:salmon", 2);
        t(RegionType.ASHEN_HOLLOWS, "fish_fillet", 2, "shellmeat", 1);
        t(RegionType.CRYSTAL_DEEP, "glimmerfin", 5, "fish_fillet", 2);
        t(RegionType.THE_ABYSS, "glimmerfin", 2, "fish_fillet", 2);
        t(RegionType.EVERFROST, "minecraft:salmon", 4, "minecraft:cod", 3, "fish_fillet", 2);
        t(RegionType.GOLDEN_GROVE, "fish_fillet", 4, "minecraft:cod", 2);
        t(RegionType.HOLLOW_PEAKS, "minecraft:salmon", 3, "fish_fillet", 2);
        t(RegionType.EMERALD_WETLANDS, "mire_eel", 5, "fish_fillet", 3, "shellmeat", 1);
        t(RegionType.PETRIFIED_FOREST, "fish_fillet", 3, "mire_eel", 1);
        t(RegionType.CRIMSON_BASIN, "fish_fillet", 2, "shellmeat", 1);
        t(RegionType.SUNKEN_GARDENS, "reef_darter", 5, "shellmeat", 3, "fish_fillet", 2, "minecraft:tropical_fish", 1);
        t(RegionType.STORMSTONE_CAVERNS, "glimmerfin", 2, "fish_fillet", 2);
        t(RegionType.STARLIGHT_CAVERNS, "glimmerfin", 4, "fish_fillet", 2);
    }

    private static void t(RegionType r, Object... kv) {
        List<Entry> l = new java.util.ArrayList<>();
        for (int i = 0; i < kv.length; i += 2) {
            l.add(new Entry((String) kv[i], (Integer) kv[i + 1]));
        }
        TABLES.put(r, List.copyOf(l));
    }

    /** Pressure above which catches thin out, and the floor of the catch chance. */
    public static final float SOFT_LIMIT = 24f, HARD_RANGE = 48f;

    public static String areaKey(RegionType r, BlockPos pos) {
        return r.id() + ":" + (pos.getX() >> 6) + ":" + (pos.getZ() >> 6);
    }

    public static RegionType region(ServerWorld world, BlockPos pos) {
        UnderdeepTerrain t = UnderdeepWorlds.terrain(world);
        return t == null ? null : t.regionAt(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Catch chance at this pressure (1 when lightly fished, 0.25 when heavily fished). */
    public static double sustain(float pressure) {
        return pressure <= SOFT_LIMIT ? 1.0 : Math.max(0.25, 1.0 - (pressure - SOFT_LIMIT) / HARD_RANGE);
    }

    /** A regional catch at {@code pos}, or EMPTY when nothing bites (pressure) or outside the Underdeep. */
    public static ItemStack catchAt(ServerWorld world, BlockPos pos, Random random, boolean trap) {
        RegionType r = region(world, pos);
        if (r == null) {
            // vanilla worlds: traps still work with plain fish
            return random.nextInt(3) == 0 ? ItemStack.EMPTY : new ItemStack(random.nextBoolean() ? Items.COD : Items.SALMON);
        }
        SurvivalState st = SurvivalState.get(world.getServer());
        st.decay(world.getTimeOfDay() / 24000L);
        String key = areaKey(r, pos);
        float p = st.pressure.getOrDefault(key, 0f);
        if (random.nextDouble() > sustain(p)) {
            return ItemStack.EMPTY;
        }
        List<Entry> table = TABLES.getOrDefault(r, List.of(new Entry("fish_fillet", 1)));
        int total = table.stream().mapToInt(Entry::weight).sum();
        int roll = random.nextInt(total);
        Item item = Items.COD;
        for (Entry e : table) {
            roll -= e.weight();
            if (roll < 0) {
                item = Registries.ITEM.get(e.item().contains(":") ? Identifier.of(e.item()) : dev.underdeep.registry.Reg.id(e.item()));
                break;
            }
        }
        st.pressure.put(key, p + (trap ? 1.5f : 1f));
        st.markDirty();
        return new ItemStack(item);
    }

    /** Fishing-rod loot in the Underdeep (called by the bobber mixin in place of the vanilla loot roll). */
    public static ObjectArrayList<ItemStack> rodLoot(ServerWorld world, BlockPos pos, PlayerEntity angler, ObjectArrayList<ItemStack> vanilla) {
        if (!UnderdeepConfig.get().survival.regionalFishing || !UnderdeepWorlds.isUnderdeep(world) || region(world, pos) == null) {
            return vanilla;
        }
        ItemStack off = angler.getStackInHand(Hand.OFF_HAND);
        boolean bait = off.isOf(SurvivalItems.get("fishing_bait"));
        double regional = (bait ? 0.85 : 0.65) + (dev.underdeep.equipment.Accessories.flags(angler).contains("angler") ? 0.05 : 0);
        if (world.random.nextDouble() >= regional) {
            return vanilla;
        }
        ItemStack c = catchAt(world, pos, world.random, false);
        if (c.isEmpty()) {
            return vanilla;
        }
        boolean anglerCharm = dev.underdeep.equipment.Accessories.flags(angler).contains("angler");
        if (bait && !angler.getAbilities().creativeMode && !(anglerCharm && world.random.nextBoolean())) {
            off.decrement(1);
        }
        if (angler instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
            dev.underdeep.guide.Advancements.event(sp, "fished_regional", net.minecraft.registry.Registries.ITEM.getId(c.getItem()).getPath());
        }
        ObjectArrayList<ItemStack> out = new ObjectArrayList<>();
        out.add(c);
        return out;
    }

    public static List<String> tableFor(RegionType r) {
        return TABLES.getOrDefault(r, List.of()).stream().map(Entry::item).toList();
    }
}
