package dev.underdeep.debug;

import dev.underdeep.civ.econ.Goods;
import dev.underdeep.survival.Comfort;
import dev.underdeep.survival.Fishing;
import dev.underdeep.survival.Hazards;
import dev.underdeep.survival.Meals;
import dev.underdeep.survival.Recipes;
import dev.underdeep.survival.Survival;
import dev.underdeep.survival.SurvivalBlocks;
import dev.underdeep.survival.SurvivalContent;
import dev.underdeep.survival.SurvivalData;
import dev.underdeep.survival.SurvivalEffects;
import dev.underdeep.survival.SurvivalItems;
import dev.underdeep.survival.home.CompostBinBlock;
import dev.underdeep.survival.home.ContainerBlockEntity;
import dev.underdeep.survival.home.RichSoilBlock;
import dev.underdeep.survival.station.DryingRackBlockEntity;
import dev.underdeep.survival.station.StationBlockEntity;
import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.UnderdeepCreature;
import dev.underdeep.wildlife.WildlifeRegistry;
import dev.underdeep.world.region.RegionType;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.ComposterBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Stage 7 targeted checks: content validation, workstations (matching, rejection, batching, consumption, heat and
 * fuel, persistence, hopper sides), drying, compost, rich soil, right-click harvest, fishing and traps, troughs,
 * meal-buff rules and nutrition, medicine, hazards and their counterplay, bedroll spawn rules, comfort, storage
 * filters and the crate. Built in a small test site above the spawn; block entities are ticked directly
 * (accelerated) and a Fabric fake player stands in for a real one. Reported as such.
 */
final class Stage7Checks {
    private Stage7Checks() {
    }

    static Item item(String id) {
        return Registries.ITEM.get(id.contains(":") ? Identifier.of(id) : Identifier.of("underdeep", id));
    }

    static void run(ServerWorld w, Stage15Checks.Reporter r) {
        r.section("Stage 7 survival, farming, cooking, medicine and homesteading (block entities ticked directly; fake player)");
        // ---------------- content
        List<String> problems = SurvivalContent.resolve();
        long cookRecipes = SurvivalContent.recipes().stream().filter(x -> !x.result().getPath().endsWith("_feed")
                && !x.result().getPath().equals("fruit_mash") && !x.result().getPath().equals("meat_scraps") && x.station() != SurvivalContent.Station.HERBAL)
                .count();
        long drinks = SurvivalContent.FOODS.values().stream().filter(SurvivalContent.FoodDef::drink).count();
        int medicines = SurvivalContent.MEDICINES.size() + 5;
        r.check("content data loads and every ingredient, result and effect resolves", problems.isEmpty(),
                SurvivalContent.FOODS.size() + " foods/drinks, " + medicines + " medicines (incl. 5 Stage 2), " + SurvivalContent.recipes().size()
                        + " station recipes; problems: " + (problems.isEmpty() ? "none" : problems.subList(0, Math.min(3, problems.size()))));
        r.check("60-90 cooking recipes, 15-25 beverages, 20-30 medicines", cookRecipes >= 60 && drinks >= 15 && drinks <= 25 && medicines >= 20
                && medicines <= 30, cookRecipes + " cooking/food recipes, " + drinks + " drinks, " + medicines + " medicines");
        boolean effectsOk = true;
        for (SurvivalContent.FoodDef d : SurvivalContent.FOODS.values()) {
            for (SurvivalContent.Buff b : d.buffs()) {
                effectsOk &= SurvivalEffects.byId(b.effect()) != null && b.seconds() <= Meals.MAX_FOOD_BUFF_SECONDS && b.amplifier() <= 1;
            }
        }
        r.check("meal buffs reference real effects and stay within bounds (<= 10 min, amplifier <= I)", effectsOk, "");

        BlockPos site = w.getSpawnPos().add(20, 40, 20);
        clear(w, site);
        FakePlayer fake = FakePlayer.get(w);
        fake.refreshPositionAndAngles(site.getX() + 0.5, site.getY() + 1, site.getZ() + 0.5, 0, 0);
        fake.clearStatusEffects();
        try {
            stations(w, r, site, fake);
            farming(w, r, site.add(8, 0, 0), fake);
            fishing(w, r, site.add(0, 0, 8));
            people(w, r, site.add(8, 0, 8), fake);
            homes(w, r, site.add(-8, 0, 0), fake);
        } finally {
            clear(w, site);
        }
        int econ = 0;
        for (String id : List.of("underdeep:puffcap_soup", "underdeep:bandage", "underdeep:travel_ration", "underdeep:flour", "underdeep:mendleaf_tea")) {
            econ += Goods.get(id) != null ? 1 : 0;
        }
        r.check("new foods and medicine are economy goods", econ == 5, econ + "/5 sampled items are goods");
    }

    static void clear(ServerWorld w, BlockPos site) {
        for (BlockPos p : BlockPos.iterate(site.add(-12, -1, -4), site.add(12, 4, 14))) {
            w.setBlockState(p, p.getY() == site.getY() - 1 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        for (ItemEntity e : w.getEntitiesByClass(ItemEntity.class, new Box(site).expand(16), e -> true)) {
            e.discard();
        }
    }

    static StationBlockEntity place(ServerWorld w, BlockPos p, Block b) {
        w.setBlockState(p, b.getDefaultState(), Block.NOTIFY_ALL);
        return (StationBlockEntity) w.getBlockEntity(p);
    }

    static void tick(ServerWorld w, StationBlockEntity be, int n) {
        BlockState s = w.getBlockState(be.getPos());
        for (int i = 0; i < n; i++) {
            StationBlockEntity.tick(w, be.getPos(), s, be);
        }
    }

    // ------------------------------------------------------------------------------------------------ stations
    static void stations(ServerWorld w, Stage15Checks.Reporter r, BlockPos site, FakePlayer fake) {
        // cooking pot on a lit campfire
        BlockPos fire = site;
        w.setBlockState(fire, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true), Block.NOTIFY_ALL);
        StationBlockEntity pot = place(w, fire.up(), SurvivalBlocks.COOKING_POT);
        pot.setStack(0, new ItemStack(item("puffcap"), 8));
        pot.setStack(1, new ItemStack(item("duskroot"), 4));
        pot.setStack(StationBlockEntity.CONTAINER, new ItemStack(Items.BOWL, 6));
        String cur = pot.currentForTest();
        int made = pot.finishNowForTest();
        ItemStack out = pot.getStack(StationBlockEntity.OUTPUT);
        r.check("cooking pot: ingredients in any slot order match the recipe, batches limited by the station (4)",
                "puffcap_soup".equals(cur) && made == 4 && out.isOf(item("puffcap_soup")) && out.getCount() == 4,
                "matched " + cur + ", made " + made + " -> " + out.getCount() + " " + Registries.ITEM.getId(out.getItem()).getPath());
        r.check("inputs and containers are consumed exactly once", pot.getStack(0).isEmpty() && pot.getStack(1).isEmpty()
                && pot.getStack(StationBlockEntity.CONTAINER).getCount() == 2, "puffcap " + pot.getStack(0).getCount() + ", duskroot "
                + pot.getStack(1).getCount() + ", bowls " + pot.getStack(StationBlockEntity.CONTAINER).getCount());
        // rejection
        pot.setStack(StationBlockEntity.OUTPUT, ItemStack.EMPTY);
        pot.setStack(0, new ItemStack(item("puffcap"), 2));
        pot.setStack(1, new ItemStack(item("duskroot"), 1));
        pot.setStack(2, new ItemStack(Items.COBBLESTONE, 1));
        r.check("an unrelated item in the inputs blocks every recipe", pot.currentForTest() == null, "");
        pot.setStack(2, ItemStack.EMPTY);
        pot.setStack(StationBlockEntity.CONTAINER, ItemStack.EMPTY);
        r.check("a missing bowl blocks a soup", pot.currentForTest() == null, "");
        // real ticking with heat
        pot.setStack(StationBlockEntity.CONTAINER, new ItemStack(Items.BOWL, 1));
        tick(w, pot, 10 * 20 + 25);
        r.check("with heat below, the pot cooks in its recipe time (ticked)", pot.getStack(StationBlockEntity.OUTPUT).isOf(item("puffcap_soup")),
                "output " + pot.getStack(StationBlockEntity.OUTPUT));
        // no heat
        w.setBlockState(fire, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        pot.setStack(StationBlockEntity.OUTPUT, ItemStack.EMPTY);
        pot.setStack(0, new ItemStack(item("puffcap"), 2));
        pot.setStack(1, new ItemStack(item("duskroot"), 1));
        pot.setStack(StationBlockEntity.CONTAINER, new ItemStack(Items.BOWL, 1));
        tick(w, pot, 400);
        r.check("without heat the pot does not cook and loses nothing", pot.getStack(StationBlockEntity.OUTPUT).isEmpty()
                && pot.getStack(0).getCount() == 2 && pot.progressForTest() == 0, "progress " + pot.progressForTest());
        // persistence
        NbtCompound nbt = pot.createNbtWithIdentifyingData(w.getRegistryManager());
        BlockEntity copy = BlockEntity.createFromNbt(pot.getPos(), pot.getCachedState(), nbt, w.getRegistryManager());
        r.check("station contents persist through save/load", copy instanceof StationBlockEntity c && c.getStack(0).getCount() == 2
                && c.getStack(1).isOf(item("duskroot")) && c.getStack(StationBlockEntity.CONTAINER).isOf(Items.BOWL), "");
        // hopper sides
        r.check("hopper sides: inputs from above, containers from the side, output from below only",
                pot.getAvailableSlots(net.minecraft.util.math.Direction.UP).length == 6
                        && pot.canInsert(StationBlockEntity.CONTAINER, new ItemStack(Items.BOWL), net.minecraft.util.math.Direction.NORTH)
                        && !pot.canInsert(StationBlockEntity.OUTPUT, new ItemStack(Items.BOWL), net.minecraft.util.math.Direction.UP)
                        && pot.canExtract(StationBlockEntity.OUTPUT, new ItemStack(Items.BOWL), net.minecraft.util.math.Direction.DOWN)
                        && !pot.canExtract(0, new ItemStack(Items.BOWL), net.minecraft.util.math.Direction.DOWN), "");
        // remainders: dough at the prep table returns the bucket
        StationBlockEntity prep = place(w, site.add(2, 0, 0), SurvivalBlocks.PREPARATION_TABLE);
        prep.setStack(0, new ItemStack(item("flour"), 2));
        prep.setStack(1, new ItemStack(Items.WATER_BUCKET));
        int d = prep.finishNowForTest();
        r.check("container items come back (water bucket -> bucket)", d == 1 && prep.getStack(1).isOf(Items.BUCKET)
                && prep.getStack(StationBlockEntity.OUTPUT).isOf(item("dough")) && prep.getStack(StationBlockEntity.OUTPUT).getCount() == 3,
                "slot " + prep.getStack(1) + ", output " + prep.getStack(StationBlockEntity.OUTPUT));
        // group ingredients
        prep.setStack(1, ItemStack.EMPTY);
        prep.setStack(StationBlockEntity.OUTPUT, ItemStack.EMPTY);
        prep.setStack(0, new ItemStack(item("duskberries"), 1));
        prep.setStack(1, new ItemStack(item("snowberries"), 1));
        prep.setStack(2, new ItemStack(Items.APPLE, 1));
        prep.setStack(StationBlockEntity.CONTAINER, new ItemStack(Items.BOWL, 1));
        String salad = prep.currentForTest();
        r.check("ingredient groups accept any member (three different fruits make a fruit salad)", "fruit_salad".equals(salad), "matched " + salad);
        // oven fuel
        StationBlockEntity oven = place(w, site.add(4, 0, 0), SurvivalBlocks.CLAY_OVEN);
        oven.setStack(0, new ItemStack(item("dough"), 1));
        tick(w, oven, 300);
        boolean noFuel = oven.getStack(StationBlockEntity.OUTPUT).isEmpty();
        oven.setStack(StationBlockEntity.FUEL, new ItemStack(Items.COAL, 1));
        tick(w, oven, 260);
        r.check("the oven needs fuel and burns it", noFuel && oven.getStack(StationBlockEntity.OUTPUT).isOf(item("flatbread"))
                && oven.getStack(StationBlockEntity.FUEL).isEmpty(), "output " + oven.getStack(StationBlockEntity.OUTPUT));
        // crock (long, no heat)
        StationBlockEntity crock = place(w, site.add(6, 0, 0), SurvivalBlocks.FERMENTATION_CROCK);
        crock.setStack(0, new ItemStack(Items.MILK_BUCKET, 1));
        crock.setStack(1, new ItemStack(item("salt"), 1));
        String cheese = crock.currentForTest();
        tick(w, crock, 240 * 20 + 25);
        r.check("fermentation takes its long time without heat (cheese, 4 min)", "capback_cheese".equals(cheese)
                && crock.getStack(StationBlockEntity.OUTPUT).isOf(item("capback_cheese")) && crock.getStack(0).isOf(Items.BUCKET), "");
        // learning by cooking
        SurvivalContent.Recipe chili = SurvivalContent.recipe("ember_chili");
        SurvivalData.of(fake).known.clear();
        boolean before = Recipes.knows(fake, chili);
        boolean learned = Recipes.learn(fake, chili, "test");
        boolean again = Recipes.learn(fake, chili, "test");
        r.check("recipe discovery: basics known, others learned once", !before && learned && !again && Recipes.knows(fake, chili)
                && Recipes.knows(fake, SurvivalContent.recipe("puffcap_soup")), "");
        // drying rack
        w.setBlockState(site.add(-2, 0, 0), SurvivalBlocks.DRYING_RACK.getDefaultState(), Block.NOTIFY_ALL);
        DryingRackBlockEntity rack = (DryingRackBlockEntity) w.getBlockEntity(site.add(-2, 0, 0));
        boolean hung = rack.hang(new ItemStack(item("game_meat"))) && rack.hang(new ItemStack(Items.KELP));
        boolean refused = !rack.hang(new ItemStack(Items.STONE));
        rack.finishNowForTest();
        r.check("drying rack hangs only dryable items and preserves them", hung && refused && rack.items().get(0).isOf(item("jerky"))
                && rack.items().get(1).isOf(Items.DRIED_KELP), rack.items().toString());
    }

    // ------------------------------------------------------------------------------------------------ farming
    static void farming(ServerWorld w, Stage15Checks.Reporter r, BlockPos site, FakePlayer fake) {
        w.setBlockState(site.down(), SurvivalBlocks.RICH_SOIL.getDefaultState(), Block.NOTIFY_ALL);
        Block crop = Registries.BLOCK.get(Identifier.of("underdeep", "duskroots"));
        boolean plantable = crop.getDefaultState().canPlaceAt(w, site);
        double boost = RichSoilBlock.boost(w, site), plain = RichSoilBlock.boost(w, site.add(5, 0, 5));
        r.check("crops can be planted on rich soil and grow faster there", plantable && boost > 1 && plain == 1, "boost x" + boost);
        if (crop instanceof CropBlock cb) {
            w.setBlockState(site, cb.withAge(cb.getMaxAge()), Block.NOTIFY_ALL);
            List<ItemStack> drops = Survival.harvest(w, site, w.getBlockState(site), cb, fake);
            BlockState after = w.getBlockState(site);
            r.check("right-click harvest gives the crop and replants it", drops != null && after.isOf(crop) && cb.getAge(after) == 0,
                    drops == null ? "no seed" : drops.toString());
            // compost advances exactly one stage
            ItemStack compost = new ItemStack(item("compost"), 1);
            fake.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, compost);
            compost.useOnBlock(new net.minecraft.item.ItemUsageContext(fake, net.minecraft.util.Hand.MAIN_HAND,
                    new net.minecraft.util.hit.BlockHitResult(site.toCenterPos(), net.minecraft.util.math.Direction.UP, site, false)));
            r.check("compost fertilises gently: one growth stage, one compost", cb.getAge(w.getBlockState(site)) == 1 && compost.isEmpty(), "");
            fake.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, ItemStack.EMPTY);
        }
        // compost bin
        BlockPos bin = site.add(2, 0, 0);
        w.setBlockState(bin, SurvivalBlocks.COMPOST_BIN.getDefaultState().with(ComposterBlock.LEVEL, 8), Block.NOTIFY_ALL);
        var inv = ((CompostBinBlock) SurvivalBlocks.COMPOST_BIN).getInventory(w.getBlockState(bin), w, bin);
        ItemStack got = inv.getStack(0);
        boolean compostable = ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.getFloat(item("duskroot")) > 0
                && ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.getFloat(item("puffcap_soup")) > 0;
        r.check("compost bin yields compost; crops and dishes are compostable", got.isOf(item("compost")) && compostable, got.toString());
        // feeding trough with a tamed grazer
        CreatureSpecies sp = WildlifeRegistry.all().stream().filter(s -> s.diet() == CreatureSpecies.Diet.GRAZER).findFirst().orElse(null);
        BlockPos tp = site.add(4, 0, 0);
        w.setBlockState(tp, SurvivalBlocks.FEEDING_TROUGH.getDefaultState(), Block.NOTIFY_ALL);
        ContainerBlockEntity trough = (ContainerBlockEntity) w.getBlockEntity(tp);
        if (sp != null && trough != null) {
            UnderdeepCreature c = WildlifeRegistry.type(sp.id).create(w);
            c.refreshPositionAndAngles(tp.getX() + 1.5, tp.getY(), tp.getZ() + 0.5, 0, 0);
            c.setTamed(true, true);
            c.setPersistent();
            w.spawnEntity(c);
            c.hunger = 0.8f;
            trough.setStack(0, new ItemStack(item("grain_feed"), 3));
            boolean rejects = !trough.isValid(1, new ItemStack(Items.STONE));
            int fed = trough.troughTick(w, tp);
            int again = trough.troughTick(w, tp);
            r.check("feeding trough feeds hungry tamed animals once with the right feed", fed == 1 && again == 0 && c.hunger < 0.4f
                    && trough.getStack(0).getCount() == 2 && rejects, sp.id + " hunger " + String.format(Locale.ROOT, "%.2f", c.hunger));
            c.discard();
        }
    }

    // ------------------------------------------------------------------------------------------------ fishing
    static void fishing(ServerWorld w, Stage15Checks.Reporter r, BlockPos site) {
        r.check("fishing pressure thins catches but never empties the waters", Fishing.sustain(0) == 1 && Fishing.sustain(50) < 1
                && Fishing.sustain(10000) >= 0.25, String.format(Locale.ROOT, "%.2f / %.2f", Fishing.sustain(50), Fishing.sustain(10000)));
        r.check("every region has its own fish", Fishing.tableFor(RegionType.SUNKEN_GARDENS).contains("reef_darter")
                && Fishing.tableFor(RegionType.EMERALD_WETLANDS).contains("mire_eel") && Fishing.tableFor(RegionType.CRYSTAL_DEEP).contains("glimmerfin"), "");
        w.setBlockState(site, SurvivalBlocks.FISH_TRAP.getDefaultState().with(Properties.WATERLOGGED, true), Block.NOTIFY_ALL);
        ContainerBlockEntity trap = (ContainerBlockEntity) w.getBlockEntity(site);
        trap.setStack(0, new ItemStack(item("fishing_bait"), 3));
        int ok = 0;
        for (int i = 0; i < 6; i++) {
            ok += trap.catchOne(w, site) ? 1 : 0;
        }
        int fish = 0;
        for (int i = 1; i < trap.size(); i++) {
            fish += trap.getStack(i).getCount();
        }
        int bait = trap.getStack(0).getCount();
        r.check("fish trap: each catch uses one bait, never more catches than bait", fish == 3 - bait && fish <= 3 && trap.produced == fish,
                fish + " fish, " + bait + " bait left");
    }

    // ------------------------------------------------------------------------------------------------ meals, medicine, hazards
    static void people(ServerWorld w, Stage15Checks.Reporter r, BlockPos site, FakePlayer p) {
        p.clearStatusEffects();
        p.refreshPositionAndAngles(site.getX() + 0.5, site.getY(), site.getZ() + 0.5, 0, 0);
        SurvivalData d = SurvivalData.of(p);
        java.util.Arrays.fill(d.lastAte, 0);
        for (String f : List.of("glowgrain_bread", "duskberries", "cooked_game_meat")) {
            Meals.onEat(p, new ItemStack(item(f)));
        }
        StatusEffectInstance wf0 = p.getStatusEffect(SurvivalEffects.WELL_FED);
        int amp1 = wf0 == null ? -1 : wf0.getAmplifier(); // (the instance is upgraded in place later)
        Meals.onEat(p, new ItemStack(item("puffcap")));
        Meals.onEat(p, new ItemStack(item("cooked_fish_fillet")));
        StatusEffectInstance wf2 = p.getStatusEffect(SurvivalEffects.WELL_FED);
        r.check("variety: 3 food groups give Well Fed I, 5 give Well Fed II; never a penalty", amp1 == 0 && wf2 != null
                && wf2.getAmplifier() == 1 && Meals.exhaustionFactor(p) < 1, "exhaustion x" + String.format(Locale.ROOT, "%.2f", Meals.exhaustionFactor(p)));
        Meals.apply(p, SurvivalEffects.WARMED, 2000, 0, true);
        Meals.apply(p, SurvivalEffects.WARMED, 600, 0, true);
        int dur = p.getStatusEffect(SurvivalEffects.WARMED).getDuration();
        Meals.apply(p, SurvivalEffects.COOLED, 600, 3, true);
        StatusEffectInstance cool = p.getStatusEffect(SurvivalEffects.COOLED);
        r.check("meal buffs: durations do not stack, opposites cancel, food amplifier capped", dur == 2000 && !p.hasStatusEffect(SurvivalEffects.WARMED)
                && cool != null && cool.getAmplifier() == 1, "");
        // medicine
        p.addStatusEffect(new StatusEffectInstance(SurvivalEffects.HEAT_STRESS, 400));
        p.addStatusEffect(new StatusEffectInstance(SurvivalEffects.WOUNDED, 400));
        ((SurvivalItems.MedicineItem) item("cooling_salve")).treat(p);
        ((SurvivalItems.MedicineItem) item("bandage")).treat(p);
        r.check("medicine cures what it says (cooling salve -> heat stress, bandage -> wound)", !p.hasStatusEffect(SurvivalEffects.HEAT_STRESS)
                && !p.hasStatusEffect(SurvivalEffects.WOUNDED) && p.hasStatusEffect(SurvivalEffects.COOLED), "");
        // hazards
        p.clearStatusEffects();
        EnumMap<Hazards.Hazard, Float> m = new EnumMap<>(Hazards.Hazard.class);
        Hazards.exposures(p, w, RegionType.ROOTWILD, m);
        boolean starter = m.isEmpty();
        m.clear();
        Hazards.exposures(p, w, RegionType.GOLDEN_GROVE, m);
        starter &= m.isEmpty();
        m.clear();
        Hazards.exposures(p, w, RegionType.EVERFROST, m);
        float cold = m.getOrDefault(Hazards.Hazard.EXTREME_COLD, 0f);
        p.addStatusEffect(new StatusEffectInstance(SurvivalEffects.WARMED, 200));
        m.clear();
        Hazards.exposures(p, w, RegionType.EVERFROST, m);
        float coldWarm = m.getOrDefault(Hazards.Hazard.EXTREME_COLD, 0f);
        p.clearStatusEffects();
        w.setBlockState(site.add(2, 0, 0), Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true), Block.NOTIFY_ALL);
        m.clear();
        Hazards.exposures(p, w, RegionType.EVERFROST, m);
        float coldFire = m.getOrDefault(Hazards.Hazard.EXTREME_COLD, 0f);
        w.setBlockState(site.add(2, 0, 0), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        r.check("no hazards in the gentle starting regions", starter, "");
        r.check("cold builds in the Everfrost; warming effects and a fire stop it", cold > 0 && coldWarm == 0 && coldFire == 0,
                String.format(Locale.ROOT, "%.1f/s bare", cold));
        m.clear();
        Hazards.exposures(p, w, RegionType.STORMSTONE_CAVERNS, m);
        float noMetal = m.getOrDefault(Hazards.Hazard.STATIC_CHARGE, 0f);
        p.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        m.clear();
        Hazards.exposures(p, w, RegionType.STORMSTONE_CAVERNS, m);
        float metal = m.getOrDefault(Hazards.Hazard.STATIC_CHARGE, 0f);
        p.addStatusEffect(new StatusEffectInstance(SurvivalEffects.GROUNDED, 200));
        m.clear();
        Hazards.exposures(p, w, RegionType.STORMSTONE_CAVERNS, m);
        float grounded = m.getOrDefault(Hazards.Hazard.STATIC_CHARGE, 0f);
        p.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        p.clearStatusEffects();
        m.clear();
        Hazards.exposures(p, w, RegionType.ASHEN_HOLLOWS, m);
        float heat = m.getOrDefault(Hazards.Hazard.GEOTHERMAL, 0f);
        p.addStatusEffect(new StatusEffectInstance(SurvivalEffects.COOLED, 200));
        m.clear();
        Hazards.exposures(p, w, RegionType.ASHEN_HOLLOWS, m);
        float heatCool = m.getOrDefault(Hazards.Hazard.GEOTHERMAL, 0f);
        p.clearStatusEffects();
        r.check("static charge needs metal and is stopped by grounding; geothermal heat is stopped by cooling",
                noMetal == 0 && metal > 0 && grounded == 0 && heat > 0 && heatCool == 0, "");
        Hazards.afterDamage(p, 8f);
        boolean wounded = p.hasStatusEffect(SurvivalEffects.WOUNDED);
        p.clearStatusEffects();
        Hazards.afterDamage(p, 3f);
        r.check("lightweight injuries: only a heavy blow wounds", wounded && !p.hasStatusEffect(SurvivalEffects.WOUNDED), "");
        p.clearStatusEffects();
    }

    // ------------------------------------------------------------------------------------------------ camping and homes
    static void homes(ServerWorld w, Stage15Checks.Reporter r, BlockPos site, FakePlayer p) {
        w.setBlockState(site, SurvivalBlocks.BEDROLL.getDefaultState(), Block.NOTIFY_ALL);
        boolean spawn = EntitySleepEvents.ALLOW_SETTING_SPAWN.invoker().allowSettingSpawn(p, site);
        boolean bed = EntitySleepEvents.ALLOW_BED.invoker().allowBed(p, site, w.getBlockState(site), false) == net.minecraft.util.ActionResult.SUCCESS;
        r.check("bedrolls are valid beds but never move the respawn point on their own", bed && !spawn, "");
        int bare = Comfort.score(w, site, p);
        w.setBlockState(site.add(2, 0, 0), SurvivalBlocks.WOODEN_CHAIR.getDefaultState(), Block.NOTIFY_ALL);
        w.setBlockState(site.add(2, 0, 1), SurvivalBlocks.WOODEN_TABLE.getDefaultState(), Block.NOTIFY_ALL);
        w.setBlockState(site.add(-2, 0, 0), SurvivalBlocks.STORAGE_CABINET.getDefaultState(), Block.NOTIFY_ALL);
        w.setBlockState(site.add(-2, 0, 2), SurvivalBlocks.COOKING_POT.getDefaultState(), Block.NOTIFY_ALL);
        w.setBlockState(site.add(0, 0, 3), Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true), Block.NOTIFY_ALL);
        int furnished = Comfort.score(w, site, p);
        r.check("comfort rises with furniture, a kitchen and warmth (positive only)", furnished > bare && bare >= 0, bare + " -> " + furnished);
        // storage filters
        w.setBlockState(site.add(4, 0, 0), SurvivalBlocks.PANTRY.getDefaultState(), Block.NOTIFY_ALL);
        ContainerBlockEntity pantry = (ContainerBlockEntity) w.getBlockEntity(site.add(4, 0, 0));
        w.setBlockState(site.add(4, 0, 2), SurvivalBlocks.MEDICINE_CABINET.getDefaultState(), Block.NOTIFY_ALL);
        ContainerBlockEntity med = (ContainerBlockEntity) w.getBlockEntity(site.add(4, 0, 2));
        r.check("pantry takes food and ingredients only; medicine cabinet takes medicine and herbs", pantry.isValid(0, new ItemStack(Items.BREAD))
                && pantry.isValid(0, new ItemStack(item("flour"))) && !pantry.isValid(0, new ItemStack(Items.COBBLESTONE))
                && med.isValid(0, new ItemStack(item("bandage"))) && med.isValid(0, new ItemStack(item("mendleaf_sprig")))
                && !med.isValid(0, new ItemStack(Items.BREAD)), "");
        // crate keeps its contents
        BlockPos cp = site.add(6, 0, 0);
        w.setBlockState(cp, SurvivalBlocks.SUPPLY_CRATE.getDefaultState(), Block.NOTIFY_ALL);
        ContainerBlockEntity crate = (ContainerBlockEntity) w.getBlockEntity(cp);
        crate.setStack(0, new ItemStack(Items.IRON_INGOT, 5));
        List<ItemStack> drops = Block.getDroppedStacks(w.getBlockState(cp), w, cp, crate);
        w.setBlockState(cp, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        int loose = w.getEntitiesByClass(ItemEntity.class, new Box(cp).expand(2), e -> e.getStack().isOf(Items.IRON_INGOT)).size();
        boolean kept = drops.size() == 1 && drops.get(0).contains(DataComponentTypes.CONTAINER)
                && drops.get(0).get(DataComponentTypes.CONTAINER).stream().anyMatch(s -> s.isOf(Items.IRON_INGOT) && s.getCount() == 5);
        r.check("the supply crate keeps its contents when broken (no duplicate spill)", kept && loose == 0, drops + ", loose " + loose);
    }
}
