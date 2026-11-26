package dev.underdeep.civ.econ;

import dev.underdeep.civ.Civilization;
import dev.underdeep.civ.arch.Blueprint;
import dev.underdeep.civ.arch.BuildingKind;
import dev.underdeep.civ.mind.Activity;
import dev.underdeep.civ.mind.Feelings.Emotion;
import dev.underdeep.civ.mind.Memory;
import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.settlement.SettlementPlan;
import dev.underdeep.civ.settlement.SettlementSite;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.ecology.EcologyRegistry;
import dev.underdeep.ecology.RegionEcology;
import dev.underdeep.ecology.Species;
import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.WildlifeRegistry;
import dev.underdeep.wildlife.population.RegionWildlife;
import dev.underdeep.wildlife.population.WildlifeManager;
import dev.underdeep.world.region.RegionType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The settlement economy simulation (Stage 6). The same rules run for every tier: hourly steps (coarser far from
 * players, bounded catch-up) of production by actual workers at their workplaces, gathering limited by the
 * region's ecology and sustainability pools, household consumption, wages, merchant restocking, prices, money
 * stabilisation and prosperity. Daily processes (decisions, trade, construction, population, events) are in
 * {@link Daily}.
 */
public final class EconomySim {
    private EconomySim() {
    }

    /** Everything a step needs about a settlement (rebuilt per step; never stored). */
    public static final class Ctx {
        public ServerWorld world;
        public EconomyState state;
        public Economy e;
        public SettlementRecord rec;
        public SettlementPlan plan;
        public SettlementSite site;
        public RegionType region;
        public long regionId;
        public RegionEcology eco;
        public long now;
        public UnderdeepConfig cfg;
        public Random rnd;
        public int population;
        public double nutritionNeedPerDay;

        public BuildingKind kindOf(int building) {
            if (building >= Economy.EXTRA_BASE) {
                for (Economy.Extra x : e.extras) {
                    if (Economy.EXTRA_BASE + x.id == building) {
                        return BuildingKind.valueOf(x.kind);
                    }
                }
                return BuildingKind.HOUSE;
            }
            return building >= 0 && building < plan.buildings().size() ? plan.buildings().get(building).kind() : null;
        }
    }

    // ================================================================== creation

    private static final Map<String, Double> MIN_DESIRED = new HashMap<>();

    static {
        for (Goods.Category c : Goods.Category.values()) {
            MIN_DESIRED.put(c.id(), switch (c) {
                case GRAIN, PRODUCE, MEAT, FISH -> 6.0;
                case PREPARED_FOOD -> 8.0;
                case STONE -> 32.0;
                case LUMBER -> 24.0;
                case TIMBER, BUILDING, FUEL -> 12.0;
                case METAL, ORE, FIBER -> 6.0;
                case MEDICINE, ANIMAL_PRODUCT, HERB -> 3.0;
                case TOOLS, GEM, SPECIALTY -> 2.0;
                default -> 1.0;
            });
        }
    }

    public static String civCrop(Civilization c, int v) {
        return switch (c) {
            case ROOTFOLK -> v % 2 == 0 ? "underdeep:duskroot" : "underdeep:glowgrain";
            case FORGEKIN -> "underdeep:emberpepper";
            case TIDE_DWELLERS -> "underdeep:mire_rice";
            case SPOREFOLK -> "underdeep:cave_truffle";
            case GLIMMERKIN -> "underdeep:glowgrain";
            case STONEBORN -> "underdeep:frost_turnip";
            case AMBERFOLK -> v % 2 == 0 ? "underdeep:sunbean" : "underdeep:glowgrain";
            case ASTRALS -> v % 2 == 0 ? "underdeep:glowgrain" : "underdeep:sunbean";
        };
    }

    /** Creates a settlement's economy with initial world reserves (a documented, one-time money source). */
    public static Economy create(Ctx c) {
        Economy e = new Economy();
        c.e = e;
        e.id = c.rec.id;
        e.civ = c.rec.civ;
        e.name = c.rec.name;
        e.created = c.now;
        e.lastStep = c.now;
        e.day = c.now / 24000;
        Random r = new Random(c.rec.id * 31 + 7);
        int pop = 0, farms = 0, pens = 0, stores = 0;
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (!ir.present()) {
                continue;
            }
            pop++;
            e.households.computeIfAbsent(ir.home, h -> {
                Economy.Household hh = new Economy.Household();
                hh.home = h;
                return hh;
            });
            if (ir.workplace >= 0) {
                Economy.Workplace w = e.workplaces.computeIfAbsent(ir.workplace, b -> {
                    Economy.Workplace ww = new Economy.Workplace();
                    ww.building = b;
                    BuildingKind k = c.kindOf(b);
                    ww.kind = k == null ? "" : k.name();
                    return ww;
                });
                w.positions++;
                w.workers.add(ir.index);
            }
            if (Market.sells(ir.profession)) {
                Economy.Merchant m = new Economy.Merchant();
                m.index = ir.index;
                e.merchants.put(ir.index, m);
            }
            if (ir.mind != null) {
                seedSkills(ir);
            }
        }
        for (SettlementPlan.Building b : c.plan.buildings()) {
            switch (b.kind()) {
                case FARM -> farms++;
                case PEN, BARN -> pens++;
                case STOREHOUSE -> stores++;
                default -> {
                }
            }
        }
        e.storageCapacity = 250 + 30.0 * c.plan.buildings().size() + 400.0 * stores;
        // money: target per capita split between treasury, households and merchants
        double target = c.cfg.moneyPerCapita * Math.max(1, pop);
        e.treasuryBalance = target * 0.5;
        double perHousehold = target * 0.35 / Math.max(1, e.households.size());
        for (Economy.Household h : e.households.values()) {
            h.purse = perHousehold * (0.8 + r.nextDouble() * 0.4);
        }
        for (Economy.Merchant m : e.merchants.values()) {
            m.till = target * 0.15 / Math.max(1, e.merchants.size());
        }
        e.initialMoney = e.money();
        // sustainability pools from the region's ecology and the plan's facilities
        pools(c, farms, pens);
        // initial reserves: a few days of food and the settlement's staples (world economic reserves)
        String crop = civCrop(e.civ, (int) (c.rec.id & 1));
        e.stock(crop).qty = 30 + pop * 3;
        e.stock("underdeep:glowgrain").qty += 10 + pop;
        e.stock("minecraft:bread").qty = pop * 2;
        e.stock("minecraft:cobblestone").qty = 96;
        e.stock("minecraft:coal").qty = 16 + pop;
        e.stock("minecraft:torch").qty = 16;
        e.stock("minecraft:iron_pickaxe").qty = 1;
        e.stock("minecraft:iron_axe").qty = 1;
        e.stock("underdeep:mendleaf_poultice").qty = 2;
        for (Species s : EcologyRegistry.inRegion(c.region)) {
            Goods.Good g = s.yield() == null ? null : Goods.get(id(s.yield()));
            if (g != null && s.rarity() != Species.Rarity.EXCEPTIONAL && s.rarity() != Species.Rarity.RARE) {
                e.stock(g.id()).qty += 6 + r.nextInt(10);
                if (s.kind() == Species.Kind.TREE) {
                    String planks = g.id().replace("_log", "_planks");
                    if (Goods.get(planks) != null) {
                        e.stock(planks).qty += 40;
                    }
                }
            }
        }
        c.population = pop;
        c.nutritionNeedPerDay = pop * 13.0;
        updatePrices(c, 24); // desired levels first: shelves never take the households' share
        for (Economy.Merchant m : e.merchants.values()) {
            Market.restock(c, m, 24);
        }
        prosperity(c);
        return e;
    }

    static String id(String yield) {
        return yield.contains(":") ? yield : "underdeep:" + yield;
    }

    /** Initial professional skills from the Stage 5 general skill (deterministic). */
    static void seedSkills(SettlementRecord.InhabitantRecord ir) {
        Mind m = ir.mind;
        boolean empty = true;
        for (byte b : m.skills) {
            empty &= b == 0;
        }
        if (!empty) {
            return;
        }
        Random r = new Random(ir.appearance ^ 0x5C111L);
        for (int i = 0; i < Recipes.Skill.values().length; i++) {
            m.skills[i] = (byte) (5 + r.nextInt(20));
        }
        m.skills[Recipes.skillOf(ir.profession).ordinal()] = (byte) Math.max(20, Math.min(95, m.skill));
    }

    static void pools(Ctx c, int farms, int pens) {
        Economy e = c.e;
        double trees = 0, herbs = 0, shrubs = 0, minerals = 0, crystals = 0;
        for (Species s : EcologyRegistry.inRegion(c.region)) {
            switch (s.kind()) {
                case TREE -> trees += s.abundance();
                case HERB, FLOWER -> herbs += Math.max(0.02, s.abundance());
                case SHRUB, VINE -> shrubs += Math.max(0.02, s.abundance());
                case MINERAL -> minerals += 1;
                case CRYSTAL -> crystals += 1;
                default -> {
                }
            }
        }
        boolean water = c.site.waterNearby() || c.site.overWater() || c.eco.habitats().contains(RegionEcology.Habitat.OPEN_WATER);
        pool(e, "crops", 30 + 60.0 * farms, 0.6);
        pool(e, "forage", 40 + 500 * shrubs * c.eco.renewableAbundance(), 0.35);
        pool(e, "fungi", 60 + 80 * (c.eco.habitats().contains(RegionEcology.Habitat.FUNGAL_GROVE) ? 3 : 1), 0.4);
        pool(e, "herbs", 20 + 400 * herbs, 0.3);
        pool(e, "wood", 600 * trees, 0.08);
        pool(e, "fish", water ? 80 + 160 * c.eco.waterAvailability() : 0, 0.25);
        pool(e, "game", 40 + 120 * c.eco.foodAvailability() * c.eco.wildlife().size() / 6.0, 0.12);
        pool(e, "ranch", 20 + 50.0 * pens, 0.5);
        pool(e, "minerals", 80 * (minerals + crystals), 0.03);
        pool(e, "ore", 260, 0.05);
        pool(e, "stone", 3000, 0.5);
    }

    private static void pool(Economy e, String id, double cap, double regen) {
        Economy.Pool p = new Economy.Pool();
        p.capacity = Math.max(0, cap);
        p.level = p.capacity;
        p.regen = regen;
        e.pools.put(id, p);
    }

    // ================================================================== step

    /** Advances one settlement economy by {@code hours} (bounded by the caller). */
    public static void step(Ctx c, double hours) {
        Economy e = c.e;
        c.population = 0;
        c.nutritionNeedPerDay = 0;
        Map<Integer, List<SettlementRecord.InhabitantRecord>> homes = new HashMap<>();
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (!ir.present() || ir.mind == null) {
                continue;
            }
            c.population++;
            if (ir.mind.journey == 0 && !dev.underdeep.civ.npc.Companions.travelling(ir.mind)) {
                homes.computeIfAbsent(ir.home, h -> new ArrayList<>()).add(ir);
                c.nutritionNeedPerDay += need(ir.mind);
            }
        }
        for (Integer h : homes.keySet()) {
            e.households.computeIfAbsent(h, k -> {
                Economy.Household hh = new Economy.Household();
                hh.home = k;
                return hh;
            });
        }
        production(c, hours);
        Construction.labor(c, hours);
        consumption(c, homes, hours);
        for (Economy.Merchant m : new ArrayList<>(e.merchants.values())) {
            Market.restock(c, m, hours);
        }
        regenerate(c, hours);
        updatePrices(c, hours);
        stabilise(c, hours);
        prosperity(c);
    }

    static double need(Mind m) {
        return m.age == Mind.Age.YOUNG ? 9 : m.age == Mind.Age.ELDER ? 11 : 14;
    }

    // ------------------------------------------------------------------ production

    /** Share of the step this inhabitant spent working (actual activity when steps are short). */
    static double workHours(SettlementRecord.InhabitantRecord ir, double hours) {
        Mind m = ir.mind;
        if (m.status != Mind.Status.ACTIVE || m.health < 0.4f || m.journey != 0 || dev.underdeep.civ.npc.Companions.travelling(m)
                || m.employer != null) {
            return 0;
        }
        if (hours <= 1.5) {
            Activity a = Activity.byOrdinal(m.activity);
            return a != null && a.cat == Activity.Cat.WORK && a != Activity.PATROL && a != Activity.WATCH_POST && a != Activity.CONSTRUCT
                    ? hours : 0;
        }
        return hours / 3.0; // long steps (distant tier, catch-up): a working third of the day
    }

    static double efficiency(SettlementRecord.InhabitantRecord ir, Recipes.Skill skill) {
        Mind m = ir.mind;
        double s = m.skills[skill.ordinal()] & 0xFF;
        double rest = 0.7 + 0.3 * m.need(dev.underdeep.civ.mind.Feelings.Need.REST);
        return (0.6 + Math.min(100, s) / 100.0 * 0.8) * Math.min(1, m.health + 0.2) * rest * (0.6 + 0.4 * m.food);
    }

    static void production(Ctx c, double hours) {
        Economy e = c.e;
        for (Economy.Workplace w : e.workplaces.values()) {
            w.output = w.output * Math.pow(0.9, hours);
        }
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (!ir.present() || ir.mind == null) {
                continue;
            }
            double work = workHours(ir, hours);
            if (work <= 0) {
                continue;
            }
            Economy.Workplace wp = ir.workplace >= 0 ? e.workplaces.get(ir.workplace) : null;
            String wpKind = wp == null ? "" : wp.kind;
            Recipes.Recipe best = null;
            double bestScore = 0;
            String missing = "";
            for (Recipes.Recipe rcp : Recipes.forProfession(ir.profession)) {
                if (!allowed(c, rcp, wpKind)) {
                    continue;
                }
                String lack = lacking(c, rcp);
                if (!lack.isEmpty()) {
                    missing = lack;
                    continue;
                }
                double score = Math.max(0.01, rcp.baseGain() / Math.max(0.1, rcp.hours())) * demand(c, rcp) * (0.9 + c.rnd.nextDouble() * 0.2);
                if (score > bestScore) {
                    bestScore = score;
                    best = rcp;
                }
            }
            if (best == null) {
                if (wp != null) {
                    wp.status = Economy.WorkStatus.IDLE;
                    wp.idleHours += work;
                    wp.missing = missing;
                }
                continue;
            }
            double eff = efficiency(ir, best.skill()) * c.cfg.productionRate;
            double batches = run(c, best, work / best.hours() * eff, Math.max(1, best.maxPerDay()) * work / 8.0);
            if (batches <= 0) {
                continue;
            }
            if (wp != null) {
                wp.status = Economy.WorkStatus.OPEN;
                wp.recipe = best.id();
                wp.missing = "";
                wp.idleHours = Math.max(0, wp.idleHours - work);
                wp.output += batches;
            }
            // skill grows slowly with real work; the worker is paid for the hours worked
            int si = best.skill().ordinal();
            if (c.rnd.nextDouble() < 0.02 * work) {
                ir.mind.skills[si] = (byte) Math.min(100, (ir.mind.skills[si] & 0xFF) + 1);
            }
            ir.mind.satisfy(dev.underdeep.civ.mind.Feelings.Need.PURPOSE, 0.02 * work);
            payWage(c, ir, work, best.skill());
        }
    }

    static boolean allowed(Ctx c, Recipes.Recipe r, String wpKind) {
        if (!r.workplace().isEmpty() && !r.workplace().contains(wpKind)) {
            // workplace-bound recipes need that kind of building (farms, pens, workshops)
            return false;
        }
        if (!r.regions().isEmpty() && !r.regions().contains(c.region.name())) {
            return false;
        }
        if (!r.habitats().isEmpty()) {
            boolean any = false;
            for (RegionEcology.Habitat h : c.eco.habitats()) {
                any |= r.habitats().contains(h.name());
            }
            if (!any) {
                return false;
            }
        }
        if (!r.source().isEmpty() && yields(c.region, r.source()).isEmpty()) {
            return false;
        }
        if (!r.pool().isEmpty()) {
            Economy.Pool p = c.e.pools.get(r.pool());
            return p != null && p.capacity > 0;
        }
        return true;
    }

    /** Empty when all inputs (and fuel) are available, otherwise the first missing good. */
    static String lacking(Ctx c, Recipes.Recipe r) {
        for (var in : r.inputs().entrySet()) {
            if (available(c.e, in.getKey()) + 1e-6 < in.getValue() * 0.25) {
                return in.getKey();
            }
        }
        if (r.fuel() > 0 && available(c.e, "@FUEL") < r.fuel()) {
            return "@FUEL";
        }
        return "";
    }

    static double available(Economy e, String key) {
        if (key.equals("@FUEL")) {
            return e.freeIn(Goods.Category.FUEL);
        }
        if (key.equals("@FUELSOLID")) {
            return e.free("minecraft:coal") + e.free("minecraft:charcoal");
        }
        if (key.startsWith("@")) {
            try {
                return e.freeIn(Goods.Category.valueOf(key.substring(1)));
            } catch (IllegalArgumentException ex) {
                return 0;
            }
        }
        return e.free(key);
    }

    /** How much the settlement wants this recipe's outputs (low stock → high). */
    static double demand(Ctx c, Recipes.Recipe r) {
        double d = 0;
        int n = 0;
        for (String out : r.outputs().keySet()) {
            if (out.startsWith("@")) {
                d += 1.2;
                n++;
                continue;
            }
            Economy.Stock s = c.e.stock(out);
            d += Math.max(0.05, Math.min(3, (s.desired + 1) / (s.free() + 1)));
            n++;
        }
        return n == 0 ? 1 : d / n;
    }

    /** Runs up to {@code wanted} batches (fractional allowed), limited by inputs, pools, storage and a cap. Returns batches run. */
    static double run(Ctx c, Recipes.Recipe r, double wanted, double cap) {
        Economy e = c.e;
        double b = Math.min(wanted, cap);
        for (var in : r.inputs().entrySet()) {
            b = Math.min(b, available(e, in.getKey()) / in.getValue());
        }
        if (r.fuel() > 0) {
            b = Math.min(b, available(e, "@FUEL") / r.fuel());
        }
        double outPer = 0;
        for (double v : r.outputs().values()) {
            outPer += v;
        }
        Economy.Pool pool = r.pool().isEmpty() ? null : e.pools.get(r.pool());
        double yield = 1;
        if (pool != null) {
            yield = pool.yieldFactor();
            b = Math.min(b, pool.level / Math.max(0.01, outPer));
        }
        double room = Math.max(0, e.storageCapacity * 1.5 - e.totalUnits());
        b = Math.min(b, room / Math.max(0.01, outPer));
        // the settlement stops making what it cannot use or sell
        for (String out : r.outputs().keySet()) {
            if (!out.startsWith("@") && e.free(out) > e.stock(out).desired * 4 + 16) {
                b = Math.min(b, 0);
            }
        }
        if (b <= 1e-6) {
            return 0;
        }
        // consume inputs exactly as defined
        for (var in : r.inputs().entrySet()) {
            consume(c, in.getKey(), in.getValue() * b);
        }
        if (r.fuel() > 0) {
            consume(c, "@FUEL", r.fuel() * b);
        }
        if (!r.tool().isEmpty() && available(e, r.tool()) > 0) {
            consume(c, r.tool(), 0.01 * b); // wear
        } else if (!r.tool().isEmpty()) {
            yield *= 0.6; // working without proper tools
        }
        for (var out : r.outputs().entrySet()) {
            produce(c, r, out.getKey(), out.getValue() * b * yield);
        }
        for (var by : r.byproducts().entrySet()) {
            produce(c, r, by.getKey(), by.getValue() * b * yield);
        }
        if (pool != null) {
            pool.level = Math.max(0, pool.level - outPer * b * yield);
            pool.pressure += outPer * b * yield;
            if (r.pool().equals("game") || r.pool().equals("fish")) {
                wildlife(c, r.pool(), outPer * b * yield);
            }
        }
        return b;
    }

    static void consume(Ctx c, String key, double qty) {
        Economy e = c.e;
        Map<String, Double> taken = new HashMap<>();
        if (key.equals("@FUEL")) {
            e.takeFrom(Goods.Category.FUEL, qty, taken);
        } else if (key.equals("@FUELSOLID")) {
            double t = e.take("minecraft:charcoal", qty);
            taken.put("minecraft:charcoal", t);
            taken.merge("minecraft:coal", e.take("minecraft:coal", qty - t), Double::sum);
        } else if (key.startsWith("@")) {
            try {
                e.takeFrom(Goods.Category.valueOf(key.substring(1)), qty, taken);
            } catch (IllegalArgumentException ignored) {
                // unknown category: nothing taken
            }
        } else {
            taken.put(key, e.take(key, qty));
        }
        taken.forEach((g, v) -> e.stock(g).consumed += v);
    }

    static void produce(Ctx c, Recipes.Recipe r, String key, double qty) {
        if (qty <= 0) {
            return;
        }
        if (key.equals("@CIVCROP")) {
            key = civCrop(c.e.civ, (int) (c.now / 24000 & 1));
        }
        if (key.equals("@SPECIES")) {
            List<Object[]> ys = yields(c.region, r.source());
            double total = 0;
            for (Object[] y : ys) {
                total += (Double) y[1];
            }
            for (Object[] y : ys) {
                put(c, (String) y[0], qty * (Double) y[1] / total);
            }
            return;
        }
        put(c, key, qty);
    }

    private static void put(Ctx c, String good, double qty) {
        double in = c.e.put(good, qty);
        c.e.stock(good).produced += in;
    }

    private static final Map<String, List<Object[]>> YIELDS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Native species yields of the given kinds that are economic goods: [good id, weight]. Exceptional species never. */
    static List<Object[]> yields(RegionType region, java.util.Set<String> kinds) {
        return YIELDS.computeIfAbsent(region.name() + kinds, k -> {
            List<Object[]> out = new ArrayList<>();
            for (Species s : EcologyRegistry.inRegion(region)) {
                if (s.yield() == null || !kinds.contains(s.kind().name()) || s.rarity() == Species.Rarity.EXCEPTIONAL) {
                    continue;
                }
                Goods.Good g = Goods.get(id(s.yield()));
                if (g != null) {
                    double w = Math.max(0.02, s.abundance()) * (s.rarity() == Species.Rarity.RARE ? 0.2 : s.rarity() == Species.Rarity.UNCOMMON ? 0.6 : 1);
                    out.add(new Object[]{g.id(), w});
                }
            }
            return out;
        });
    }

    /** Hunting and fishing take actual animals from the region's abstract wildlife population (never below 40 % of capacity). */
    static void wildlife(Ctx c, String pool, double units) {
        WildlifeManager wm = WildlifeManager.get(c.world);
        if (wm == null) {
            return;
        }
        RegionWildlife rw = wm.region(c.regionId);
        if (rw == null || !rw.initialized) {
            return;
        }
        int animals = (int) Math.floor(units / 5 + c.rnd.nextDouble());
        UnderdeepConfig cfg = c.cfg;
        for (int i = 0; i < animals; i++) {
            CreatureSpecies pick = null;
            int bestSurplus = 0;
            for (var en : rw.counts.entrySet()) {
                CreatureSpecies s = WildlifeRegistry.byId(en.getKey());
                if (s == null || s.isPredator() || s.rarity() != Species.Rarity.COMMON || pool.equals("fish") != s.isAquatic()) {
                    continue;
                }
                int cap = RegionWildlife.capacity(s, c.region, c.regionId, cfg.wildlifeDensity, cfg.rareCreatureFrequency, rw.disturbance);
                int surplus = en.getValue() - (int) Math.ceil(cap * 0.4);
                if (surplus > bestSurplus) {
                    bestSurplus = surplus;
                    pick = s;
                }
            }
            if (pick == null) {
                return; // populations are at their safeguard floor: the pool will deplete instead
            }
            rw.add(pick.id, -1);
        }
    }

    // ------------------------------------------------------------------ wages and consumption

    static void payWage(Ctx c, SettlementRecord.InhabitantRecord ir, double hours, Recipes.Skill skill) {
        Economy e = c.e;
        Economy.Household h = e.households.get(ir.home);
        if (h == null) {
            return;
        }
        double skillF = 0.7 + (ir.mind.skills[skill.ordinal()] & 0xFF) / 100.0;
        double wage = 0.1 * skillF * c.cfg.wageRate * (0.8 + 0.4 * e.score) * hours;
        Economy.Workplace w = ir.workplace >= 0 ? e.workplaces.get(ir.workplace) : null;
        if (w != null) {
            w.wage = 0.1 * skillF * c.cfg.wageRate;
        }
        double paid = Tx.payUpTo(e.treasury, h, wage, c.now, "wage");
        e.wagesPaid += paid;
        if (paid < wage * 0.5) {
            ir.mind.feel(Emotion.FRUSTRATION, 0.02);
        }
    }

    static void consumption(Ctx c, Map<Integer, List<SettlementRecord.InhabitantRecord>> homes, double hours) {
        Economy e = c.e;
        double day = hours / 24.0 * c.cfg.consumptionRate;
        boolean cold = c.region == RegionType.EVERFROST, warm = c.region == RegionType.ASHEN_HOLLOWS;
        for (var en : homes.entrySet()) {
            Economy.Household h = e.households.get(en.getKey());
            List<SettlementRecord.InhabitantRecord> members = en.getValue();
            double need = 0;
            for (SettlementRecord.InhabitantRecord ir : members) {
                need += need(ir.mind);
            }
            need *= day;
            double got = buyFood(c, h, need);
            double met = need <= 0 ? 1 : Math.min(1, got / need);
            double k = Math.min(1, day * 1.5);
            h.foodSecurity = h.foodSecurity * (1 - k) + met * k;
            // fuel for warmth and cooking, clothing and repairs at low rates
            buy(c, h, Goods.Category.FUEL, (cold ? 0.8 : warm ? 0 : 0.35) * day);
            buy(c, h, Goods.Category.FIBER, 0.04 * members.size() * day);
            if (h.purse > 25 && e.score > 0.6) {
                buy(c, h, c.rnd.nextBoolean() ? Goods.Category.LUXURY : Goods.Category.DECORATIVE, 0.03 * day);
            }
            for (SettlementRecord.InhabitantRecord ir : members) {
                Mind m = ir.mind;
                m.food = (float) h.foodSecurity;
                if (h.foodSecurity < 0.25 && m.health > 0.5f) {
                    m.health = (float) Math.max(0.5, m.health - 0.02 * hours); // hunger weakens, never kills
                }
                if (m.health < 0.6f) {
                    double got1 = buy(c, h, Goods.Category.MEDICINE, Math.min(1, day * 2));
                    if (got1 <= 0) {
                        got1 = buy(c, h, Goods.Category.HERB, Math.min(2, day * 4)) / 2;
                    }
                    m.health = (float) Math.min(1, m.health + 0.3 * got1);
                }
            }
            // community levy on large savings (redistribution, documented): 5 %/day above 25 per member
            double cap = 25.0 * Math.max(1, members.size());
            if (h.purse > cap) {
                Tx.pay(h, e.treasury, (h.purse - cap) * 0.05 * day, c.now, "levy");
            }
        }
    }

    /** Buys food for {@code nutrition} points: prepared first, cheapest per point. Returns points bought. */
    static double buyFood(Ctx c, Economy.Household h, double nutrition) {
        Economy e = c.e;
        double left = nutrition;
        List<Goods.Good> foods = new ArrayList<>();
        for (Goods.Good g : Goods.all()) {
            if (g.food() && e.free(g.id()) > 0.01) {
                foods.add(g);
            }
        }
        foods.sort((a, b) -> Double.compare(costPerPoint(e, a), costPerPoint(e, b)));
        for (Goods.Good g : foods) {
            if (left <= 1e-6) {
                break;
            }
            double eff = g.nutrition() * (g.category() == Goods.Category.PREPARED_FOOD ? 1.0 : 0.75);
            double units = Math.min(e.free(g.id()), left / eff);
            double price = e.stock(g.id()).price;
            double cost = units * price;
            if (h.purse < cost) {
                // community support covers the shortfall when the household cannot pay (documented safeguard)
                double gap = cost - h.purse;
                double sup = Tx.payUpTo(e.treasury, h, gap, c.now, "support");
                e.supportPaid += sup;
                h.support += sup;
                if (h.purse < cost) {
                    units = h.purse / Math.max(1e-6, price);
                    cost = units * price;
                }
            }
            if (units <= 0) {
                continue;
            }
            Tx.pay(h, e.treasury, cost, c.now, "food");
            double t = e.take(g.id(), units);
            e.stock(g.id()).consumed += t;
            left -= t * eff;
        }
        // then from the sellers' shelves (whole items, paid into their tills)
        for (Economy.Merchant m : e.merchants.values()) {
            for (var en : new ArrayList<>(m.shelf.entrySet())) {
                if (left <= 1e-6) {
                    return nutrition - left;
                }
                Goods.Good g = Goods.get(en.getKey());
                if (g == null || !g.food() || en.getValue() <= 0) {
                    continue;
                }
                double eff = g.nutrition() * (g.category() == Goods.Category.PREPARED_FOOD ? 1.0 : 0.75);
                int units = (int) Math.min(en.getValue(), Math.ceil(left / eff));
                double price = e.stock(g.id()).price * 1.05;
                units = (int) Math.min(units, Math.floor(h.purse / Math.max(1e-6, price)));
                if (units <= 0 || !Tx.pay(h, m, units * price, c.now, "food")) {
                    continue;
                }
                m.shelf.put(en.getKey(), en.getValue() - units);
                e.stock(g.id()).consumed += units;
                left -= units * eff;
            }
        }
        return nutrition - left;
    }

    private static double costPerPoint(Economy e, Goods.Good g) {
        double eff = g.nutrition() * (g.category() == Goods.Category.PREPARED_FOOD ? 1.0 : 0.75);
        return e.stock(g.id()).price / Math.max(0.5, eff) * (g.category() == Goods.Category.PREPARED_FOOD ? 0.85 : 1);
    }

    /** Buys units of a category from the ledger (cheapest first). Returns units bought. */
    static double buy(Ctx c, Economy.Household h, Goods.Category cat, double units) {
        if (units <= 1e-6) {
            return 0;
        }
        Economy e = c.e;
        double got = 0;
        List<Goods.Good> gs = new ArrayList<>(Goods.in(cat));
        gs.sort((a, b) -> Double.compare(e.stock(a.id()).price, e.stock(b.id()).price));
        for (Goods.Good g : gs) {
            double want = units - got;
            if (want <= 1e-6) {
                break;
            }
            double u = Math.min(want, e.free(g.id()));
            double price = e.stock(g.id()).price;
            u = Math.min(u, h.purse / Math.max(1e-6, price));
            if (u <= 1e-6) {
                continue;
            }
            Tx.pay(h, e.treasury, u * price, c.now, "goods");
            double t = e.take(g.id(), u);
            e.stock(g.id()).consumed += t;
            got += t;
        }
        return got;
    }

    // ------------------------------------------------------------------ regeneration, prices, money, prosperity

    static void regenerate(Ctx c, double hours) {
        for (Economy.Pool p : c.e.pools.values()) {
            // logistic regrowth toward capacity; pressure (recent extraction) fades
            double r = p.regen * hours / 24.0;
            p.level = Math.min(p.capacity, p.level + p.capacity * r * Math.max(0.15, 1 - p.level / Math.max(1, p.capacity)) * 1.4);
            p.pressure *= Math.pow(0.9, hours);
        }
    }

    static void updatePrices(Ctx c, double hours) {
        Economy e = c.e;
        double k = Math.min(1, hours / 24.0 * 0.6);
        double transport = 0.15 + nearestSupplierDistance(c) / 5000.0;
        double foodNeedPerGood = c.nutritionNeedPerDay * 3 / 4.0 / 12.0;
        for (Goods.Good g : Goods.all()) {
            Economy.Stock s = e.stock.get(g.id());
            if (s == null) {
                continue;
            }
            // smoothed daily rates
            double perDay = 24.0 / Math.max(0.01, hours);
            s.roll(k, perDay);
            double min = MIN_DESIRED.getOrDefault(g.category().id(), 1.0);
            double want = Math.max(min, s.rC * 3 + (g.food() ? foodNeedPerGood : 0));
            s.desired = want + projectNeed(e, g);
            boolean local = s.rP > 0.05;
            double target = Pricing.target(g.value(), s.free(), s.desired, local, transport, e.score, g.food(), c.cfg.priceVariation);
            if (s.price <= 0) {
                s.price = target;
            }
            s.price = Pricing.approach(s.price, target, hours);
        }
    }

    private static double projectNeed(Economy e, Goods.Good g) {
        double t = 0;
        for (Project p : e.projects) {
            if (p.active()) {
                Double need = p.required.get(g.category().id());
                if (need != null) {
                    t += Math.max(0, need - p.delivered.getOrDefault(g.category().id(), 0.0)) / Math.max(1, Goods.in(g.category()).size());
                }
            }
        }
        return t;
    }

    static double nearestSupplierDistance(Ctx c) {
        double best = 1500;
        for (EconomyState.Connection k : c.state.connectionsOf(c.e.id)) {
            best = Math.min(best, k.distance);
        }
        return best;
    }

    /** Issuance and upkeep keep money per inhabitant within 60–160 % of the target (explicit, counted sources and sinks). */
    static void stabilise(Ctx c, double hours) {
        Economy e = c.e;
        double target = c.cfg.moneyPerCapita * Math.max(1, c.population);
        double m = e.money();
        double rate = target * 0.05 * hours / 24.0;
        if (m < 0.6 * target) {
            double add = Math.min(0.6 * target - m, rate);
            e.treasuryBalance += add;
            e.issued += add;
            Tx.log(c.now, "issue", "world", "treasury", add, e.name);
        } else if (m > 1.6 * target) {
            double sink = Math.min(Math.min(m - 1.6 * target, rate), e.treasuryBalance);
            if (sink > 0) {
                e.treasuryBalance -= sink;
                e.sunk += sink;
                Tx.log(c.now, "upkeep", "treasury", "world", sink, e.name);
            }
        }
    }

    static void prosperity(Ctx c) {
        Economy e = c.e;
        double[] p = e.prosperity;
        double food = 0;
        int hh = 0;
        for (Economy.Household h : e.households.values()) {
            food += h.foodSecurity;
            hh++;
        }
        p[Prosperity.FOOD.ordinal()] = hh == 0 ? 1 : food / hh;
        int beds = beds(c), pop = Math.max(1, c.population);
        p[Prosperity.HOUSING.ordinal()] = Math.min(1, beds / (double) pop);
        int adults = 0, employed = 0;
        double health = 0;
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (!ir.present() || ir.mind == null) {
                continue;
            }
            health += ir.mind.health;
            if (ir.mind.age != Mind.Age.YOUNG) {
                adults++;
                employed += ir.workplace >= 0 || !Recipes.forProfession(ir.profession).isEmpty() || Market.sells(ir.profession)
                        || ir.profession.equals("guard") ? 1 : 0;
            }
        }
        p[Prosperity.EMPLOYMENT.ordinal()] = adults == 0 ? 1 : employed / (double) adults;
        p[Prosperity.HEALTH.ordinal()] = health / pop;
        double res = 0;
        Goods.Category[] key = {Goods.Category.PREPARED_FOOD, Goods.Category.FUEL, Goods.Category.LUMBER, Goods.Category.STONE, Goods.Category.TOOLS};
        for (Goods.Category k : key) {
            double free = e.freeIn(k), desired = 0;
            for (Goods.Good g : Goods.in(k)) {
                Economy.Stock s = e.stock.get(g.id());
                desired += s == null ? 0 : s.desired / Math.max(1, Goods.in(k).size()) * 2;
            }
            res += Math.min(1, free / Math.max(1, desired));
        }
        p[Prosperity.RESERVES.ordinal()] = res / key.length;
        double trade = 0, produced = 0;
        for (var en : e.stock.entrySet()) {
            Goods.Good g = Goods.get(en.getKey());
            if (g != null) {
                trade += (en.getValue().rI + en.getValue().rE) * g.value();
                produced += en.getValue().rP * g.value();
            }
        }
        p[Prosperity.TRADE.ordinal()] = Math.min(1, trade / (pop * 0.3));
        p[Prosperity.PRODUCTION.ordinal()] = Math.min(1, produced / (pop * 0.8));
        p[Prosperity.CONNECTIVITY.ordinal()] = Math.min(1, c.state.connectionsOf(e.id).size() / 3.0);
        p[Prosperity.SAFETY.ordinal()] = Math.max(0, 1 - e.disruption);
        int built = 0;
        for (Project pr : e.projects) {
            built += pr.status == Project.Status.COMPLETE ? 1 : 0;
        }
        p[Prosperity.INFRASTRUCTURE.ordinal()] = Math.min(1, 0.55 + 0.1 * built);
        p[Prosperity.MONEY.ordinal()] = Math.min(1, e.money() / (c.cfg.moneyPerCapita * pop));
        e.score = Prosperity.score(p, e.disruption);
    }

    /** Beds in the plan's and constructed homes (people's records point at them). */
    public static int beds(Ctx c) {
        int n = 0;
        for (SettlementPlan.Building b : c.plan.buildings()) {
            for (Blueprint.Marker m : b.blueprint().markers()) {
                n += m.type() == Blueprint.MarkerType.BED ? 1 : 0;
            }
        }
        for (Economy.Extra x : c.e.extras) {
            n += x.beds.size();
        }
        return n;
    }

    /** A free bed (no present inhabitant sleeps there), or null. */
    @Nullable
    public static Object[] freeBed(Ctx c) {
        java.util.Set<Long> used = new java.util.HashSet<>();
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (ir.present() && ir.bed != null) {
                used.add(ir.bed.asLong());
            }
        }
        for (Economy.Extra x : c.e.extras) {
            for (BlockPos b : x.beds) {
                if (!used.contains(b.asLong()) && !Daily.ownedByPlayer(c, Economy.EXTRA_BASE + x.id)) {
                    return new Object[]{Economy.EXTRA_BASE + x.id, b};
                }
            }
        }
        for (SettlementPlan.Building b : c.plan.buildings()) {
            if (Daily.ownedByPlayer(c, b.index())) {
                continue;
            }
            for (Blueprint.Marker m : b.blueprint().markers()) {
                if (m.type() == Blueprint.MarkerType.BED) {
                    BlockPos wp = b.world(m.pos());
                    if (!used.contains(wp.asLong())) {
                        return new Object[]{b.index(), wp};
                    }
                }
            }
        }
        return null;
    }

    /** Test access: runs a recipe directly (same code path as production). */
    public static double runForTest(Ctx c, Recipes.Recipe r, double wanted, double cap) {
        return run(c, r, wanted, cap);
    }

    /** Memories for inhabitants: helper used by daily processes. */
    static void remember(SettlementRecord.InhabitantRecord ir, Memory.Type t, String who, String detail, long now) {
        if (ir.mind != null) {
            ir.mind.memories.remember(t, who, detail, now, UnderdeepConfig.get().inhabitantMemoryLimit, 1);
        }
    }
}
