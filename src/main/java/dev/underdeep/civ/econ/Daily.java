package dev.underdeep.civ.econ;

import dev.underdeep.civ.Civilization;
import dev.underdeep.civ.arch.BuildingKind;
import dev.underdeep.civ.mind.Bond;
import dev.underdeep.civ.mind.Feelings.Emotion;
import dev.underdeep.civ.mind.Memory;
import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.npc.InhabitantManager;
import dev.underdeep.civ.npc.Places;
import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.settlement.SettlementCategory;
import dev.underdeep.civ.settlement.SettlementPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Daily settlement processes (Stage 6): leadership, the settlement's own decisions (bounded utility rules over
 * real state, with reasons kept for diagnostics and dialogue), causal events and the news they create in
 * inhabitants' minds. Trade, construction, population and player participation are delegated.
 */
public final class Daily {
    private Daily() {
    }

    public static void run(EconomyManager mgr, EconomySim.Ctx c) {
        c.now = c.world.getTime();
        Economy e = c.e;
        if (e.leaders.isEmpty() || c.rnd.nextInt(20) == 0 || !leadersPresent(c)) {
            leadership(c);
        }
        observe(c);
        decide(mgr, c);
        Trade.daily(mgr, c);
        Construction.daily(mgr, c);
        Population.daily(mgr, c);
        Participation.daily(mgr, c);
        e.disruption *= 0.8;
        grow(c);
        // expire old events and gatherings
        e.events.removeIf(ev -> ev.until() < c.now - 24000L * 6);
        Places pl = places(c);
        if (pl != null) {
            pl.celebrationUntil = e.gatheringUntil;
        }
    }

    static Places places(EconomySim.Ctx c) {
        InhabitantManager im = InhabitantManager.get(c.world);
        return im == null ? null : im.society.places(c.rec);
    }

    // ================================================================== leadership

    /** Culturally appropriate leadership from actual inhabitants (adults ranked by standing in their community). */
    public static void leadership(EconomySim.Ctx c) {
        Economy e = c.e;
        String title = switch (e.civ) {
            case ROOTFOLK -> "the council of elders";
            case FORGEKIN -> "the masters of the forge";
            case TIDE_DWELLERS -> "the harbour master";
            case SPOREFOLK -> "the circle of keepers";
            case GLIMMERKIN -> "the artisans' guild";
            case STONEBORN -> "the chieftain";
            case AMBERFOLK -> "the merchant guild";
            case ASTRALS -> "the observatory council";
        };
        int seats = e.civ == Civilization.TIDE_DWELLERS || e.civ == Civilization.STONEBORN ? 1 : c.rec.category.ordinal() >= SettlementCategory.TOWN.ordinal() ? 3 : 2;
        List<Object[]> ranked = new ArrayList<>();
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (!ir.present() || ir.mind == null || ir.mind.age == Mind.Age.YOUNG || ir.mind.companionOf != null) {
                continue;
            }
            Mind m = ir.mind;
            double standing = 0;
            for (SettlementRecord.InhabitantRecord o : c.rec.inhabitants) {
                Bond b = o.mind == null ? null : o.mind.people.get(ir.index);
                if (b != null) {
                    standing += b.respect * 0.5 + b.trust * 0.3;
                }
            }
            double score = standing / 10 + m.skill * 0.3 + m.trait(dev.underdeep.civ.mind.Trait.AMBITION) * 0.2
                    + m.trait(dev.underdeep.civ.mind.Trait.DISCIPLINE) * 0.1 + switch (e.civ) {
                case ROOTFOLK -> m.age == Mind.Age.ELDER ? 40 : 0;
                case FORGEKIN -> ir.profession.equals("blacksmith") || ir.profession.equals("smelter") ? 30 : 0;
                case TIDE_DWELLERS -> ir.profession.equals("navigator") || ir.profession.equals("boatwright") || ir.profession.equals("fisher") ? 30 : 0;
                case SPOREFOLK -> ir.profession.equals("mycologist") || ir.profession.equals("healer") || ir.profession.equals("alchemist") ? 30 : 0;
                case GLIMMERKIN -> ir.profession.equals("gemcutter") || ir.profession.equals("artisan") ? 30 : 0;
                case STONEBORN -> (m.age == Mind.Age.ELDER ? 20 : 0) + (ir.profession.equals("guard") || ir.profession.equals("stonemason") ? 20 : 0);
                case AMBERFOLK -> ir.profession.equals("merchant") || ir.profession.equals("innkeeper") ? 30 : 0;
                case ASTRALS -> ir.profession.equals("astronomer") || ir.profession.equals("scholar") ? 30 : 0;
            };
            ranked.add(new Object[]{score, ir.index});
        }
        ranked.sort((a, b) -> Double.compare((Double) b[0], (Double) a[0]));
        List<Integer> before = new ArrayList<>(e.leaders);
        e.leaders.clear();
        for (int i = 0; i < Math.min(seats, ranked.size()); i++) {
            e.leaders.add((Integer) ranked.get(i)[1]);
        }
        e.leadership = title;
        if (!before.isEmpty() && !before.equals(e.leaders) && !e.leaders.isEmpty()) {
            event(c, "LEADER_CHOSEN", "i:" + e.leaders.get(0), first(c, e.leaders.get(0)) + " now speaks for " + title, 24000L * 3);
        }
    }

    static boolean leadersPresent(EconomySim.Ctx c) {
        for (int i : c.e.leaders) {
            if (i < 0 || i >= c.rec.inhabitants.size() || !c.rec.inhabitants.get(i).present()) {
                return false;
            }
        }
        return true;
    }

    static String first(EconomySim.Ctx c, int idx) {
        if (idx < 0 || idx >= c.rec.inhabitants.size()) {
            return "someone";
        }
        String n = c.rec.inhabitants.get(idx).name;
        return n.contains(" ") ? n.substring(0, n.indexOf(' ')) : n;
    }

    // ================================================================== observation → events

    static double foodDays(EconomySim.Ctx c) {
        double points = 0;
        for (Goods.Good g : Goods.all()) {
            if (g.food()) {
                points += c.e.free(g.id()) * g.nutrition() * (g.category() == Goods.Category.PREPARED_FOOD ? 1 : 0.75);
            }
        }
        return points / Math.max(1, c.nutritionNeedPerDay);
    }

    static double foodProducedPerDay(EconomySim.Ctx c) {
        double p = 0;
        for (Goods.Good g : Goods.all()) {
            Economy.Stock s = c.e.stock.get(g.id());
            if (g.food() && s != null) {
                p += s.rP * g.nutrition() * 0.75;
            }
        }
        return p;
    }

    /** Events that arise from the actual state of the settlement today. */
    static void observe(EconomySim.Ctx c) {
        Economy e = c.e;
        double days = foodDays(c);
        double produced = foodProducedPerDay(c);
        if (days < 1.5 && !e.recent("FOOD_SHORTAGE", c.now)) {
            event(c, "FOOD_SHORTAGE", "", "food is running short in " + e.name, 24000L * 2);
            e.disruption = Math.min(1, e.disruption + 0.3);
        } else if (days > 4 && e.recent("FOOD_SHORTAGE", c.now - 24000L * 2) && !e.recent("FOOD_RECOVERED", c.now)) {
            event(c, "FOOD_RECOVERED", "", e.name + " has enough food again", 24000L * 2);
            moodAll(c, Emotion.RELIEF, 0.2);
        }
        if (produced > c.nutritionNeedPerDay * 1.6 && c.rnd.nextInt(3) == 0 && !e.recent("GOOD_HARVEST", c.now)) {
            event(c, "GOOD_HARVEST", "", "the harvest in " + e.name + " has been very good", 24000L * 3);
            moodAll(c, Emotion.HAPPINESS, 0.1);
        }
        for (var en : e.pools.entrySet()) {
            Economy.Pool p = en.getValue();
            if (p.capacity > 0 && p.level < p.capacity * 0.25 && !e.recent("DEPLETED_" + en.getKey(), c.now)) {
                event(c, "DEPLETED_" + en.getKey(), en.getKey(), switch (en.getKey()) {
                    case "game" -> "the hunters say game is getting scarce around " + e.name;
                    case "fish" -> "the fishers say the catch is poor around " + e.name;
                    case "wood" -> "the foresters around " + e.name + " are letting the woods rest";
                    case "herbs" -> "herbs are hard to find near " + e.name + " this week";
                    default -> en.getKey() + " are getting scarce around " + e.name;
                }, 24000L * 3);
            }
        }
        for (Economy.Workplace w : e.workplaces.values()) {
            if (w.idleHours > 24 && !w.missing.isEmpty() && !e.recent("IDLE_" + w.building, c.now)) {
                Goods.Good g = w.missing.startsWith("@") ? null : Goods.get(w.missing);
                String what = g != null ? g.name() : w.missing.equals("@FUEL") ? "fuel" : w.missing.substring(1).toLowerCase(Locale.ROOT);
                event(c, "IDLE_" + w.building, w.missing, "the " + w.kind.toLowerCase(Locale.ROOT).replace('_', ' ') + " in " + e.name
                        + " has stopped for lack of " + what, 24000L * 2);
            }
        }
        // prices that jumped well above normal
        for (Goods.Good g : Goods.all()) {
            Economy.Stock s = e.stock.get(g.id());
            if (s != null && (g.food() || g.category() == Goods.Category.FUEL) && s.price > g.value() * 1.6 && !e.recent("PRICE_" + g.id(), c.now)) {
                event(c, "PRICE_" + g.id(), g.id(), g.name() + " has become expensive in " + e.name, 24000L * 2);
                break;
            }
        }
        if (e.score < 0.35 && !e.recent("HARD_TIMES", c.now)) {
            event(c, "HARD_TIMES", "", "times are hard in " + e.name, 24000L * 3);
        }
        // occasional new vein: miners find more of a regional mineral (bounded, only where minerals exist)
        Economy.Pool min = e.pools.get("minerals");
        if (min != null && min.capacity > 0 && c.rnd.nextDouble() < 0.03 * c.cfg.eventFrequency) {
            min.capacity *= 1.15;
            min.level = Math.min(min.capacity, min.level + min.capacity * 0.2);
            event(c, "NEW_DEPOSIT", "", "miners near " + e.name + " found a new mineral vein", 24000L * 3);
        }
    }

    static void moodAll(EconomySim.Ctx c, Emotion em, double v) {
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (ir.present() && ir.mind != null) {
                ir.mind.feel(em, v);
            }
        }
    }

    /**
     * Records an event and lets a few people who would know learn it (the leaders and one or two others); from there
     * it spreads through ordinary conversations (Stage 5 information system). {@code sentence} is spoken as is.
     */
    public static void event(EconomySim.Ctx c, String type, String subject, String sentence, long duration) {
        Economy e = c.e;
        e.event(type, c.now, subject, sentence, duration);
        c.state.note(c.now, e.name + ": " + sentence);
        List<SettlementRecord.InhabitantRecord> knowers = new ArrayList<>();
        for (int i : e.leaders) {
            if (i >= 0 && i < c.rec.inhabitants.size()) {
                knowers.add(c.rec.inhabitants.get(i));
            }
        }
        for (int k = 0; k < 2 && !c.rec.inhabitants.isEmpty(); k++) {
            knowers.add(c.rec.inhabitants.get(c.rnd.nextInt(c.rec.inhabitants.size())));
        }
        for (SettlementRecord.InhabitantRecord ir : knowers) {
            if (ir.present() && ir.mind != null) {
                ir.mind.learn(Mind.InfoType.ECON, "e:" + type + ":" + e.id, sentence, c.now, Mind.Source.WITNESSED, 90,
                        c.cfg.inhabitantKnowledgeLimit);
            }
        }
    }

    // ================================================================== decisions

    /** A candidate decision: category, utility and a reason. */
    record Option(String category, double utility, String reason, Runnable act) {
    }

    static void decide(EconomyManager mgr, EconomySim.Ctx c) {
        Economy e = c.e;
        List<Option> opts = new ArrayList<>();
        double days = foodDays(c);
        double produced = foodProducedPerDay(c);
        int free = freeBeds(c);
        int adults = 0, idle = 0;
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (ir.present() && ir.mind != null && ir.mind.age != Mind.Age.YOUNG) {
                adults++;
                if (Population.unemployed(c, ir)) {
                    idle++;
                }
            }
        }
        int vacancies = Population.vacancies(c).size();
        boolean building = e.projects.stream().anyMatch(Project::active);
        double treasury = e.treasuryBalance;
        // 1 food security
        if (days < 3) {
            opts.add(new Option("import_food", 3 - days + (produced < c.nutritionNeedPerDay ? 1 : 0), String.format(Locale.ROOT,
                    "%.1f days of food left", days), () -> Trade.requestImport(mgr, c, Goods.Category.PREPARED_FOOD, c.nutritionNeedPerDay * 4 / 5)));
        }
        if (produced < c.nutritionNeedPerDay * 0.9) {
            opts.add(new Option("increase_food_production", 1.5 + (c.nutritionNeedPerDay - produced) / Math.max(1, c.nutritionNeedPerDay),
                    "local food production covers only " + Math.round(produced / Math.max(1, c.nutritionNeedPerDay) * 100) + "% of needs",
                    () -> {
                        if (!Population.reassign(c, "farmer", "the settlement needs more food") && !building) {
                            Construction.propose(mgr, c, Project.Kind.FARM, "more food is needed", "farmer");
                        }
                    }));
        }
        // 2 housing and growth
        if (free <= 1 && e.score > 0.45 && !building) {
            opts.add(new Option("build_housing", 1.2 + e.score + (free <= 0 ? 0.6 : 0), free + " free beds", () -> Construction.propose(mgr, c,
                    c.rec.category.ordinal() >= SettlementCategory.TOWN.ordinal() && c.rnd.nextBoolean() ? Project.Kind.LARGE_HOUSE : Project.Kind.HOUSE,
                    "homes are full", "")));
        }
        if (e.totalUnits() > e.storageCapacity * 0.8 && !building) {
            opts.add(new Option("expand_storage", 1.3, "stores are " + Math.round(e.totalUnits() / e.storageCapacity * 100) + "% full",
                    () -> Construction.propose(mgr, c, Project.Kind.STOREHOUSE, "the stores are overflowing", "merchant")));
        }
        if (idle >= 2 && !building && e.score > 0.4) {
            String prof = Population.neededProfession(c);
            opts.add(new Option("build_workshop", 1.0 + idle * 0.3, idle + " people without work", () -> Construction.propose(mgr, c,
                    Project.Kind.WORKSHOP, "people need work", prof)));
        }
        if (vacancies > 0 && free > 0) {
            opts.add(new Option("open_positions", 0.9 + vacancies * 0.2, vacancies + " open positions", () -> Population.attract(mgr, c)));
        }
        // 3 trade
        if (c.state.connectionsOf(e.id).size() < 2) {
            opts.add(new Option("establish_trade", 1.1, "only " + c.state.connectionsOf(e.id).size() + " trade partners",
                    () -> Trade.connect(mgr, c.state, e)));
        }
        opts.add(new Option("export_surplus", 0.6 + Trade.surplusValue(c) / 20.0, "surplus worth " + Math.round(Trade.surplusValue(c)) + " emeralds",
                () -> Trade.planExports(mgr, c)));
        // 4 safety and infrastructure
        double safety = e.prosperity(Prosperity.SAFETY);
        if (safety < 0.6) {
            opts.add(new Option("improve_defenses", 1.4 - safety, "recent dangers", () -> {
                if (!Population.reassign(c, "guard", "the settlement needs more guards") && !building) {
                    Construction.propose(mgr, c, Project.Kind.GUARD_POST, "the settlement feels unsafe", "guard");
                }
            }));
        }
        if (Construction.damaged(mgr, c) && !building) {
            opts.add(new Option("repair_infrastructure", 1.6, "damaged public buildings", () -> Construction.proposeRepair(mgr, c)));
        }
        if (c.site.waterNearby() && e.prosperity(Prosperity.TRADE) > 0.5 && countKind(c, BuildingKind.DOCK) < 2 && !building && e.score > 0.6) {
            opts.add(new Option("expand_waterfront", 0.9, "busy water trade", () -> Construction.propose(mgr, c, Project.Kind.DOCK, "more boats call here",
                    "fisher")));
        }
        // 5 community
        if (e.score > 0.62 && treasury > c.cfg.moneyPerCapita * 4 && c.now > e.gatheringUntil + 24000L * 4) {
            opts.add(new Option("fund_gathering", 0.8 + e.score, "prosperous times", () -> celebrate(c, "times are good")));
        }
        double hungry = 0;
        for (Economy.Household h : e.households.values()) {
            hungry += h.foodSecurity < 0.6 && h.purse < 3 ? 1 : 0;
        }
        if (hungry > 0 && treasury > 10) {
            opts.add(new Option("support_households", 1.0 + hungry * 0.3, (int) hungry + " households struggling", () -> {
                for (Economy.Household h : e.households.values()) {
                    if (h.foodSecurity < 0.6 && h.purse < 3) {
                        e.supportPaid += Tx.payUpTo(e.treasury, h, 4, c.now, "relief");
                    }
                }
            }));
        }
        if (Trade.shortage(c) != null && treasury > 15) {
            Goods.Good g = Trade.shortage(c);
            opts.add(new Option("post_contract", 0.7, g.name() + " is short", () -> Participation.postSupply(c, g, "we are short of " + g.name())));
        }
        if (e.score > 0.7 && !building && c.rec.alive() >= 12 && countKind(c, BuildingKind.INN) == 0) {
            opts.add(new Option("build_inn", 1.0, "travellers and no inn", () -> Construction.propose(mgr, c, Project.Kind.INN, "travellers need a place to stay",
                    "innkeeper")));
        }
        if (e.score > 0.7 && !building && e.prosperity(Prosperity.TRADE) > 0.4 && countKind(c, BuildingKind.STALL) + countKind(c, BuildingKind.SHOP) < 3) {
            opts.add(new Option("expand_market", 0.9, "trade is growing", () -> Construction.propose(mgr, c, Project.Kind.STALL, "the market is busy",
                    "merchant")));
        }
        if (e.prosperity(Prosperity.HEALTH) < 0.7 && !building && countKind(c, BuildingKind.CLINIC) == 0 && c.rec.alive() >= 10) {
            opts.add(new Option("build_clinic", 1.0, "many are unwell", () -> Construction.propose(mgr, c, Project.Kind.CLINIC, "people need care", "healer")));
        }
        if (e.score > 0.78 && !building && c.rnd.nextInt(4) == 0) {
            Project.Kind k = switch (c.rnd.nextInt(4)) {
                case 0 -> Project.Kind.HALL;
                case 1 -> Project.Kind.WELL;
                case 2 -> Project.Kind.LIBRARY;
                default -> Project.Kind.TOWER;
            };
            opts.add(new Option("civic_improvement", 0.7, "prosperity allows it", () -> Construction.propose(mgr, c, k, "the community can afford it", "")));
        }
        Participation.propertyOptions(c, opts);
        opts.sort((a, b) -> Double.compare(b.utility(), a.utility()));
        int done = 0;
        for (Option o : opts) {
            if (done >= 3 || o.utility() < 0.75) {
                break;
            }
            o.act().run();
            e.decide(c.now, o.category() + " (" + o.reason() + String.format(Locale.ROOT, ", utility %.2f)", o.utility()));
            done++;
        }
    }

    static int countKind(EconomySim.Ctx c, BuildingKind k) {
        int n = 0;
        for (SettlementPlan.Building b : c.plan.buildings()) {
            n += b.kind() == k ? 1 : 0;
        }
        for (Economy.Extra x : c.e.extras) {
            n += x.kind.equals(k.name()) ? 1 : 0;
        }
        return n;
    }

    static int freeBeds(EconomySim.Ctx c) {
        int used = 0;
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            used += ir.present() ? 1 : 0;
        }
        return EconomySim.beds(c) - used;
    }

    /** A community celebration: schedules a gathering, uses prepared food and luxuries, lifts spirits. */
    public static void celebrate(EconomySim.Ctx c, String why) {
        Economy e = c.e;
        e.gatheringUntil = c.now + 9000;
        e.gatheringReason = why;
        double food = c.population * 0.5;
        java.util.Map<String, Double> used = new java.util.HashMap<>();
        e.takeFrom(Goods.Category.PREPARED_FOOD, food, used);
        e.takeFrom(Goods.Category.LUXURY, 1, used);
        used.forEach((g, v) -> e.stock(g).consumed += v);
        double spend = Math.min(e.treasuryBalance, c.population * 0.3); // musicians, food and lamps: a counted money sink
        e.treasuryBalance -= spend;
        e.sunk += spend;
        Tx.log(c.now, "celebration", "treasury", "world", spend, e.name);
        String what = switch (e.civ) {
            case ROOTFOLK -> "a root festival";
            case FORGEKIN -> "a forge-fire feast";
            case TIDE_DWELLERS -> "a tide night on the docks";
            case SPOREFOLK -> "a spore-bloom gathering";
            case GLIMMERKIN -> "a night of lights";
            case STONEBORN -> "a stone moot";
            case AMBERFOLK -> "a market feast";
            case ASTRALS -> "a starwatch";
        };
        event(c, "CELEBRATION", why, e.name + " is holding " + what + " because " + why, 9000);
        moodAll(c, Emotion.HAPPINESS, 0.15);
        Places pl = places(c);
        if (pl != null) {
            pl.celebrationUntil = e.gatheringUntil;
        }
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (ir.present() && ir.mind != null && c.rnd.nextInt(3) == 0) {
                ir.mind.memories.remember(Memory.Type.GOAL_DONE, "", "celebration", c.now, c.cfg.inhabitantMemoryLimit, 0.5);
            }
        }
    }

    /** A settlement that has grown in people and buildings is recognised as the next category (rare, bounded). */
    static void grow(EconomySim.Ctx c) {
        SettlementCategory cat = c.rec.category;
        if (cat.ordinal() + 1 >= SettlementCategory.values().length || c.e.extras.size() < 3 + cat.ordinal()) {
            return;
        }
        SettlementCategory next = SettlementCategory.values()[cat.ordinal() + 1];
        if (c.rec.alive() >= next.minPeople && c.e.score > 0.6) {
            c.rec.category = next;
            event(c, "SETTLEMENT_GREW", "", c.e.name + " has grown into a " + next.id(), 24000L * 5);
            celebrate(c, c.e.name + " has become a " + next.id());
            dev.underdeep.civ.npc.SettlementState.get(c.world).markDirty();
        }
    }

    /** True when a player owns or rents the building (settlements then leave it alone). */
    public static boolean ownedByPlayer(EconomySim.Ctx c, int building) {
        for (EconomyState.Property p : c.state.properties.values()) {
            if (p.settlement == c.e.id && p.building == building && p.owner != null) {
                return true;
            }
        }
        return false;
    }
}
