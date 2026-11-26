package dev.underdeep.civ.econ;

import dev.underdeep.civ.Names;
import dev.underdeep.civ.arch.Blueprint;
import dev.underdeep.civ.arch.BuildingKind;
import dev.underdeep.civ.mind.Bond;
import dev.underdeep.civ.mind.Feelings.Emotion;
import dev.underdeep.civ.mind.Memory;
import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.mind.Trait;
import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.npc.SettlementState;
import dev.underdeep.civ.settlement.Professions;
import dev.underdeep.civ.settlement.SettlementPlan;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Population development (Stage 6): employment and job changes for real reasons, vacancies, migration of actual
 * individuals between settlements (same identity, memories, possessions; old friends are remembered), newcomers
 * from beyond the simulated region when a prosperous settlement has work and beds, partnerships, new family
 * members (conservative, housing- and food-limited), belongings of the departed kept by their household.
 */
public final class Population {
    private Population() {
    }

    /** A job slot: building index, position and profession. */
    public record Vacancy(int building, BlockPos pos, String profession) {
    }

    // ================================================================== employment

    public static boolean unemployed(EconomySim.Ctx c, SettlementRecord.InhabitantRecord ir) {
        if (!ir.present() || ir.mind == null || ir.mind.age == Mind.Age.YOUNG || ir.mind.companionOf != null || ir.mind.employer != null
                || ir.mind.journey != 0) {
            return false;
        }
        if (ir.workplace >= 0 || Market.sells(ir.profession) || ir.profession.equals("guard")) {
            return false;
        }
        for (Recipes.Recipe r : Recipes.forProfession(ir.profession)) {
            if (EconomySim.allowed(c, r, "")) {
                return false;
            }
        }
        return true;
    }

    public static List<Vacancy> vacancies(EconomySim.Ctx c) {
        List<Vacancy> out = new ArrayList<>();
        java.util.Set<Long> taken = new java.util.HashSet<>();
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (ir.present() && ir.work != null) {
                taken.add(ir.work.asLong());
            }
        }
        for (SettlementPlan.Building b : c.plan.buildings()) {
            if (Daily.ownedByPlayer(c, b.index())) {
                continue;
            }
            for (Blueprint.Marker m : b.blueprint().markers()) {
                String prof = switch (m.type()) {
                    case WORKSTATION, MERCHANT -> Professions.valid(m.tag()) ? m.tag() : b.profession().isEmpty() ? "merchant" : b.profession();
                    case FARM -> b.kind() == BuildingKind.FARM ? "farmer" : "rancher";
                    case GUARD -> "guard";
                    case DOCK -> "fisher";
                    default -> null;
                };
                if (prof != null) {
                    BlockPos wp = b.world(m.pos());
                    if (!taken.contains(wp.asLong())) {
                        out.add(new Vacancy(b.index(), wp, prof));
                    }
                }
            }
        }
        for (Economy.Extra x : c.e.extras) {
            for (int i = 0; i < x.jobs.size(); i++) {
                if (!taken.contains(x.jobs.get(i).asLong())) {
                    out.add(new Vacancy(Economy.EXTRA_BASE + x.id, x.jobs.get(i), x.jobProfessions.get(i)));
                }
            }
        }
        return out;
    }

    /** Assigns a job (profession, workplace, station); updates memories, skills and the workplace register. */
    static void assign(EconomySim.Ctx c, SettlementRecord.InhabitantRecord ir, Vacancy v, String why) {
        String before = ir.profession;
        Economy.Workplace old = c.e.workplaces.get(ir.workplace);
        if (old != null) {
            old.workers.remove((Integer) ir.index);
        }
        ir.profession = v.profession();
        ir.workplace = v.building();
        ir.work = v.pos();
        Economy.Workplace w = c.e.workplaces.computeIfAbsent(v.building(), b -> {
            Economy.Workplace ww = new Economy.Workplace();
            ww.building = b;
            BuildingKind k = c.kindOf(b);
            ww.kind = k == null ? "" : k.name();
            return ww;
        });
        if (!w.workers.contains(ir.index)) {
            w.workers.add(ir.index);
        }
        w.positions = Math.max(w.positions, w.workers.size());
        if (ir.mind != null) {
            ir.mind.skill = ir.mind.skills[Recipes.skillOf(ir.profession).ordinal()] & 0xFF;
            if (!before.equals(ir.profession)) {
                EconomySim.remember(ir, Memory.Type.CHANGED_JOB, "", ir.profession, c.now);
                ir.mind.feel(Emotion.EXCITEMENT, 0.2);
            }
        }
        if (Market.sells(ir.profession) && !c.e.merchants.containsKey(ir.index)) {
            Economy.Merchant m = new Economy.Merchant();
            m.index = ir.index;
            c.e.merchants.put(ir.index, m);
        }
        // an experienced colleague takes the newcomer on as an apprentice
        for (int other : w.workers) {
            if (other != ir.index && other < c.rec.inhabitants.size()) {
                SettlementRecord.InhabitantRecord o = c.rec.inhabitants.get(other);
                if (o.mind != null && ir.mind != null && o.profession.equals(ir.profession) && o.mind.skill >= ir.mind.skill + 25) {
                    ir.mind.bond(o.index).mentor = true;
                    o.mind.bond(ir.index).apprentice = true;
                    Daily.event(c, "NEW_APPRENTICE", "i:" + ir.index, Daily.first(c, ir.index) + " is learning the " + ir.profession + "'s trade from "
                            + Daily.first(c, o.index), 24000L * 2);
                    break;
                }
            }
        }
        if (!before.equals(ir.profession)) {
            Daily.event(c, "JOB_CHANGE", "i:" + ir.index, Daily.first(c, ir.index) + " now works as a " + ir.profession + " (" + why + ")", 24000L * 2);
        }
        SettlementState.get(c.world).markDirty();
    }

    /** Moves someone into a needed profession: an unemployed adult first, then a worker whose workshop stands idle. */
    public static boolean reassign(EconomySim.Ctx c, String profession, String why) {
        Vacancy slot = null;
        for (Vacancy v : vacancies(c)) {
            if (v.profession().equals(profession)) {
                slot = v;
                break;
            }
        }
        SettlementRecord.InhabitantRecord who = null;
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (unemployed(c, ir)) {
                who = ir;
                break;
            }
        }
        if (who == null) {
            for (Economy.Workplace w : c.e.workplaces.values()) {
                if (w.status == Economy.WorkStatus.IDLE && w.idleHours > 48 && !w.workers.isEmpty()) {
                    int idx = w.workers.get(w.workers.size() - 1);
                    if (idx < c.rec.inhabitants.size() && c.rec.inhabitants.get(idx).present()) {
                        who = c.rec.inhabitants.get(idx);
                        w.idleHours = 0;
                        break;
                    }
                }
            }
        }
        if (who == null) {
            return false;
        }
        if (slot == null) {
            if (!Professions.ROAMING.contains(profession)) {
                return false;
            }
            slot = new Vacancy(-1, null, profession);
        }
        if (slot.building() < 0) {
            String before = who.profession;
            Economy.Workplace old = c.e.workplaces.get(who.workplace);
            if (old != null) {
                old.workers.remove((Integer) who.index);
            }
            who.profession = profession;
            who.workplace = -1;
            who.work = null;
            if (!before.equals(profession)) {
                EconomySim.remember(who, Memory.Type.CHANGED_JOB, "", profession, c.now);
                Daily.event(c, "JOB_CHANGE", "i:" + who.index, Daily.first(c, who.index) + " now works as a " + profession + " (" + why + ")", 24000L * 2);
            }
            return true;
        }
        assign(c, who, slot, why);
        return true;
    }

    /** What the settlement lacks most: a processor for raw surpluses, else a staple producer. */
    public static String neededProfession(EconomySim.Ctx c) {
        if (c.e.free("minecraft:raw_iron") > 16) {
            return "smelter";
        }
        if (c.e.freeIn(Goods.Category.TIMBER) > 24) {
            return "carpenter";
        }
        if (c.e.freeIn(Goods.Category.HERB) > 12) {
            return "healer";
        }
        if (c.e.freeIn(Goods.Category.GEM) > 12) {
            return "gemcutter";
        }
        if (c.e.freeIn(Goods.Category.GRAIN) + c.e.freeIn(Goods.Category.MEAT) + c.e.freeIn(Goods.Category.FISH) > 30) {
            return "cook";
        }
        return Professions.workshopFor(c.e.civ, c.rnd.nextInt(5));
    }

    // ================================================================== daily population processes

    public static void daily(EconomyManager mgr, EconomySim.Ctx c) {
        arrivals(c);
        belongings(c);
        // the unemployed look for work at home first
        for (Vacancy v : vacancies(c)) {
            for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
                if (unemployed(c, ir)) {
                    assign(c, ir, v, "there was an opening");
                    break;
                }
            }
        }
        double f = c.cfg.migrationFrequency;
        if (c.rnd.nextDouble() < 0.2 * f) {
            emigrate(mgr, c);
        }
        if (c.rnd.nextDouble() < 0.25 * f && !vacancies(c).isEmpty() && EconomySim.freeBed(c) != null && c.e.score > 0.55) {
            attract(mgr, c);
        }
        if (c.rnd.nextDouble() < 0.3 * c.cfg.populationGrowth) {
            partnerships(c);
        }
        if (c.rnd.nextDouble() < 0.035 * c.cfg.populationGrowth) {
            newFamilyMember(c);
        }
    }

    /** Travellers who have arrived start their new lives. */
    static void arrivals(EconomySim.Ctx c) {
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (ir.present() && ir.mind != null && ir.mind.status == Mind.Status.AWAY && ir.mind.companionOf == null && c.now >= ir.mind.recoverUntil) {
                ir.mind.status = Mind.Status.ACTIVE;
                ir.mind.lastUpdate = c.now;
                Daily.event(c, "MIGRANT_ARRIVED", "i:" + ir.index, Daily.first(c, ir.index) + " has arrived to live in " + c.e.name
                        + (ir.cameFrom.isEmpty() ? "" : " from " + origin(c, ir.cameFrom)), 24000L * 3);
            }
        }
    }

    static String origin(EconomySim.Ctx c, String cameFrom) {
        try {
            long id = Long.parseLong(cameFrom.substring(0, cameFrom.indexOf(':')));
            Economy o = c.state.economies.get(id);
            return o == null ? "far away" : o.name;
        } catch (RuntimeException e) {
            return "far away";
        }
    }

    /** Possessions of people who died stay with their household (never deleted). */
    static void belongings(EconomySim.Ctx c) {
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (!ir.alive && ir.mind != null && !ir.mind.possessions.isEmpty()) {
                Economy.Household h = c.e.households.get(ir.home);
                if (h != null) {
                    for (ItemStack s : ir.mind.possessions) {
                        if (!s.isEmpty() && h.storage.size() < 27) {
                            h.storage.add(s);
                        }
                    }
                    ir.mind.possessions.clear();
                }
            }
        }
    }

    /** Someone with a real reason moves to a partner settlement that has work and a bed for them. */
    static void emigrate(EconomyManager mgr, EconomySim.Ctx c) {
        SettlementRecord.InhabitantRecord who = null;
        String why = "";
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (!ir.present() || ir.mind == null || ir.mind.age != Mind.Age.ADULT || c.e.leaders.contains(ir.index) || ir.mind.companionOf != null
                    || ir.mind.employer != null || ir.mind.journey != 0 || ir.mind.status != Mind.Status.ACTIVE || hasKinHere(c, ir)) {
                continue;
            }
            Economy.Household h = c.e.households.get(ir.home);
            if (unemployed(c, ir)) {
                who = ir;
                why = "looking for work";
            } else if (h != null && h.foodSecurity < 0.45) {
                who = ir;
                why = "hard times at home";
            } else if (c.e.disruption > 0.6 && ir.mind.trait(Trait.CAUTION) > 65) {
                who = ir;
                why = "looking for a safer home";
            } else if (ir.mind.trait(Trait.ADVENTUROUSNESS) > 80 && ir.mind.trait(Trait.HOMEBODY) < 30 && c.rnd.nextInt(12) == 0) {
                who = ir;
                why = "wanting to see more of the world";
            }
            if (who != null) {
                break;
            }
        }
        if (who == null) {
            return;
        }
        // destination: a partner with a bed, preferably with a matching job and better prospects
        EconomySim.Ctx best = null;
        Vacancy bestJob = null;
        double bestScore = -1;
        for (EconomyState.Connection k : c.state.connectionsOf(c.e.id)) {
            Economy o = c.state.economies.get(k.other(c.e.id));
            EconomySim.Ctx oc = o == null ? null : mgr.ctx(o);
            if (oc == null || EconomySim.freeBed(oc) == null) {
                continue;
            }
            Vacancy job = null;
            for (Vacancy v : vacancies(oc)) {
                if (v.profession().equals(who.profession) || job == null) {
                    job = v;
                }
            }
            double score = o.score - c.e.score + (job != null ? 0.3 : 0) + (job != null && job.profession().equals(who.profession) ? 0.2 : 0)
                    + (o.civ == c.e.civ ? 0.15 : 0) - k.distance / 6000.0;
            if (score > bestScore) {
                bestScore = score;
                best = oc;
                bestJob = job;
            }
        }
        if (best == null || bestScore < 0.05) {
            return;
        }
        move(mgr, c, who, best, bestJob, why, (long) (best.state.connection(c.e.id, best.e.id).distance / 150.0 * 1000));
    }

    static boolean hasKinHere(EconomySim.Ctx c, SettlementRecord.InhabitantRecord ir) {
        for (var en : ir.mind.people.entrySet()) {
            Bond b = en.getValue();
            if (b.kin != Bond.Kin.NONE && en.getKey() < c.rec.inhabitants.size() && c.rec.inhabitants.get(en.getKey()).present()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves an individual to another settlement: the same person (name, appearance, mind, memories, skills,
     * possessions) gets a record there; old relationships are kept as "elsewhere" bonds; the old record points to the
     * new one. They travel for a while before arriving.
     */
    static void move(EconomyManager mgr, EconomySim.Ctx from, SettlementRecord.InhabitantRecord ir, EconomySim.Ctx to, @Nullable Vacancy job,
                     String why, long travelTicks) {
        Object[] bed = EconomySim.freeBed(to);
        if (bed == null || ir.mind == null) {
            return;
        }
        SettlementRecord.InhabitantRecord n = new SettlementRecord.InhabitantRecord();
        n.index = to.rec.inhabitants.size();
        n.name = ir.name;
        n.appearance = ir.appearance;
        n.profession = ir.profession;
        n.home = (Integer) bed[0];
        n.bed = (BlockPos) bed[1];
        n.workplace = -1;
        n.cameFrom = from.e.id + ":" + ir.index;
        Mind m = ir.mind;
        for (Map.Entry<Integer, Bond> en : m.people.entrySet()) {
            m.elsewhere.put(from.e.id + ":" + en.getKey(), en.getValue());
        }
        m.people.clear();
        while (m.elsewhere.size() > 24) {
            String weakest = null;
            double w = Double.MAX_VALUE;
            for (var en : m.elsewhere.entrySet()) {
                if (en.getValue().warmth() < w) {
                    w = en.getValue().warmth();
                    weakest = en.getKey();
                }
            }
            m.elsewhere.remove(weakest);
        }
        m.household = n.home;
        m.origin = from.e.name;
        m.status = Mind.Status.AWAY;
        m.recoverUntil = from.now + Math.max(1000, travelTicks);
        m.activity = -1;
        m.memories.remember(Memory.Type.MOVED_HOME, "e:" + to.e.id, to.e.name, from.now, from.cfg.inhabitantMemoryLimit, 1.2);
        n.mind = m;
        ir.mind = null;
        ir.movedTo = to.e.id;
        ir.movedIndex = n.index;
        Economy.Workplace old = from.e.workplaces.get(ir.workplace);
        if (old != null) {
            old.workers.remove((Integer) ir.index);
        }
        Economy.Merchant oldShop = from.e.merchants.remove(ir.index);
        if (oldShop != null) {
            from.e.treasuryBalance += oldShop.till; // the stall's working capital stays in town (shelf goods return to the stores)
            oldShop.shelf.forEach((g, units) -> from.e.put(g, units));
        }
        to.rec.inhabitants.add(n);
        if (job != null) {
            assign(to, n, job, "a new start");
        }
        // friends at home know where they went
        for (SettlementRecord.InhabitantRecord o : from.rec.inhabitants) {
            if (o.present() && o.mind != null) {
                Bond b = o.mind.people.get(ir.index);
                if (b != null && (b.familiarity > 30 || b.household)) {
                    o.mind.learn(Mind.InfoType.PERSON_MOVED, "i:" + ir.index, Daily.first(from, ir.index) + " moved to " + to.e.name, from.now,
                            Mind.Source.WITNESSED, 100, from.cfg.inhabitantKnowledgeLimit);
                    if (b.affection > 30) {
                        o.mind.feel(Emotion.SADNESS, 0.1);
                    }
                }
            }
        }
        Daily.event(from, "RESIDENT_LEFT", "i:" + ir.index, Daily.first(from, ir.index) + " left " + from.e.name + " for " + to.e.name + ", " + why,
                24000L * 3);
        SettlementState.get(from.world).markDirty();
    }

    public static void moveForTest(EconomyManager mgr, EconomySim.Ctx from, SettlementRecord.InhabitantRecord ir, EconomySim.Ctx to) {
        move(mgr, from, ir, to, null, "test", 1000);
    }

    /** Fills openings: a real person from a partner settlement if someone there wants to move, else a newcomer from afar. */
    public static void attract(EconomyManager mgr, EconomySim.Ctx c) {
        List<Vacancy> vs = vacancies(c);
        if (vs.isEmpty() || EconomySim.freeBed(c) == null) {
            return;
        }
        Vacancy v = vs.get(c.rnd.nextInt(vs.size()));
        for (EconomyState.Connection k : c.state.connectionsOf(c.e.id)) {
            Economy o = c.state.economies.get(k.other(c.e.id));
            EconomySim.Ctx oc = o == null ? null : mgr.ctx(o);
            if (oc == null) {
                continue;
            }
            for (SettlementRecord.InhabitantRecord ir : oc.rec.inhabitants) {
                if (unemployed(oc, ir) && !hasKinHere(oc, ir) && !oc.e.leaders.contains(ir.index)) {
                    move(mgr, oc, ir, c, v, "work was offered in " + c.e.name, (long) (k.distance / 150.0 * 1000));
                    return;
                }
            }
        }
        if (c.rnd.nextDouble() < 0.5) {
            newcomer(c, v);
        }
    }

    /** A traveller from beyond the simulated region (a new identity; an explicit, rate-limited population source). */
    static void newcomer(EconomySim.Ctx c, Vacancy v) {
        Object[] bed = EconomySim.freeBed(c);
        if (bed == null) {
            return;
        }
        SettlementRecord.InhabitantRecord n = new SettlementRecord.InhabitantRecord();
        n.index = c.rec.inhabitants.size();
        n.appearance = c.rnd.nextLong();
        n.name = Names.person(c.e.civ, n.appearance);
        n.profession = v.profession();
        n.home = (Integer) bed[0];
        n.bed = (BlockPos) bed[1];
        n.workplace = v.building();
        n.work = v.pos();
        n.cameFrom = "afar";
        c.rec.inhabitants.add(n);
        SettlementRecord.ensureMinds(c.rec, c.now);
        if (n.mind != null) {
            EconomySim.seedSkills(n);
            n.mind.status = Mind.Status.AWAY;
            n.mind.recoverUntil = c.now + 3000;
        }
        Economy.Workplace w = c.e.workplaces.computeIfAbsent(v.building(), b -> {
            Economy.Workplace ww = new Economy.Workplace();
            ww.building = b;
            BuildingKind k = c.kindOf(b);
            ww.kind = k == null ? "" : k.name();
            return ww;
        });
        w.workers.add(n.index);
        if (Market.sells(n.profession)) {
            Economy.Merchant m = new Economy.Merchant();
            m.index = n.index;
            double capital = Math.min(8, c.e.treasuryBalance); // the settlement lends a new trader working capital
            c.e.treasuryBalance -= capital;
            m.till = capital;
            c.e.merchants.put(n.index, m);
            Daily.event(c, "NEW_MERCHANT", "i:" + n.index, "a new " + n.profession + " is opening up in " + c.e.name, 24000L * 3);
        }
        SettlementState.get(c.world).markDirty();
    }

    /** Close, trusting single adults may become partners and move in together if one of their homes has a free bed. */
    static void partnerships(EconomySim.Ctx c) {
        for (SettlementRecord.InhabitantRecord a : c.rec.inhabitants) {
            if (!eligibleSingle(a)) {
                continue;
            }
            for (var en : a.mind.people.entrySet()) {
                int bi = en.getKey();
                if (bi >= c.rec.inhabitants.size()) {
                    continue;
                }
                SettlementRecord.InhabitantRecord b = c.rec.inhabitants.get(bi);
                Bond ab = en.getValue(), ba = b.mind == null ? null : b.mind.people.get(a.index);
                if (!eligibleSingle(b) || ba == null || ab.kin != Bond.Kin.NONE || ab.affection < 55 || ba.affection < 55 || ab.trust < 40
                        || ab.familiarity < 50) {
                    continue;
                }
                ab.kin = Bond.Kin.PARTNER;
                ba.kin = Bond.Kin.PARTNER;
                a.mind.feel(Emotion.HAPPINESS, 0.4);
                b.mind.feel(Emotion.HAPPINESS, 0.4);
                if (a.home != b.home) {
                    BlockPos free = freeBedIn(c, a.home);
                    if (free != null) {
                        b.home = a.home;
                        b.bed = free;
                        b.mind.household = a.home;
                        EconomySim.remember(b, Memory.Type.MOVED_HOME, "i:" + a.index, "", c.now);
                    }
                }
                Daily.event(c, "PARTNERSHIP", "i:" + a.index, Daily.first(c, a.index) + " and " + Daily.first(c, b.index) + " are now partners",
                        24000L * 3);
                return;
            }
        }
    }

    static boolean eligibleSingle(SettlementRecord.InhabitantRecord r) {
        if (!r.present() || r.mind == null || r.mind.age != Mind.Age.ADULT) {
            return false;
        }
        for (Bond b : r.mind.people.values()) {
            if (b.kin == Bond.Kin.PARTNER) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    static BlockPos freeBedIn(EconomySim.Ctx c, int home) {
        java.util.Set<Long> used = new java.util.HashSet<>();
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (ir.present() && ir.bed != null) {
                used.add(ir.bed.asLong());
            }
        }
        if (home >= Economy.EXTRA_BASE) {
            for (Economy.Extra x : c.e.extras) {
                if (Economy.EXTRA_BASE + x.id == home) {
                    for (BlockPos b : x.beds) {
                        if (!used.contains(b.asLong())) {
                            return b;
                        }
                    }
                }
            }
            return null;
        }
        if (home < 0 || home >= c.plan.buildings().size()) {
            return null;
        }
        SettlementPlan.Building b = c.plan.buildings().get(home);
        for (Blueprint.Marker m : b.blueprint().markers()) {
            if (m.type() == Blueprint.MarkerType.BED && !used.contains(b.world(m.pos()).asLong())) {
                return b.world(m.pos());
            }
        }
        return null;
    }

    /** Partners with food security and a free bed at home may welcome a child (rare, conservative). */
    static void newFamilyMember(EconomySim.Ctx c) {
        for (SettlementRecord.InhabitantRecord a : c.rec.inhabitants) {
            if (!a.present() || a.mind == null || a.mind.age != Mind.Age.ADULT) {
                continue;
            }
            Economy.Household h = c.e.households.get(a.home);
            if (h == null || h.foodSecurity < 0.8) {
                continue;
            }
            for (var en : a.mind.people.entrySet()) {
                if (en.getValue().kin != Bond.Kin.PARTNER || en.getKey() >= c.rec.inhabitants.size()) {
                    continue;
                }
                SettlementRecord.InhabitantRecord b = c.rec.inhabitants.get(en.getKey());
                BlockPos bed = freeBedIn(c, a.home);
                if (!b.present() || b.home != a.home || bed == null) {
                    continue;
                }
                SettlementRecord.InhabitantRecord n = new SettlementRecord.InhabitantRecord();
                n.index = c.rec.inhabitants.size();
                n.appearance = new Random(a.appearance ^ b.appearance ^ c.now).nextLong();
                String family = a.name.contains(" ") ? a.name.substring(a.name.indexOf(' ') + 1) : "";
                String given = Names.person(c.e.civ, n.appearance);
                n.name = (given.contains(" ") ? given.substring(0, given.indexOf(' ')) : given) + (family.isEmpty() ? "" : " " + family);
                n.profession = a.profession;
                n.home = a.home;
                n.bed = bed;
                n.workplace = -1;
                c.rec.inhabitants.add(n);
                SettlementRecord.ensureMinds(c.rec, c.now);
                if (n.mind == null) {
                    return;
                }
                n.mind.age = Mind.Age.YOUNG;
                for (SettlementRecord.InhabitantRecord parent : new SettlementRecord.InhabitantRecord[]{a, b}) {
                    Bond pb = parent.mind.bond(n.index), cb = n.mind.bond(parent.index);
                    pb.kin = Bond.Kin.CHILD;
                    cb.kin = Bond.Kin.PARENT;
                    pb.affection = cb.affection = 70;
                    pb.trust = cb.trust = 60;
                    pb.familiarity = cb.familiarity = 90;
                    pb.household = cb.household = true;
                    parent.mind.feel(Emotion.HAPPINESS, 0.5);
                }
                Daily.event(c, "NEW_FAMILY_MEMBER", "i:" + n.index, "the " + (family.isEmpty() ? Daily.first(c, a.index) : family)
                        + " family welcomed little " + Daily.first(c, n.index), 24000L * 4);
                SettlementState.get(c.world).markDirty();
                return;
            }
        }
    }

    /** Residents of crowded homes move into newly built ones (and newcomers may follow). */
    static void settleNewHomes(EconomyManager mgr, EconomySim.Ctx c) {
        Economy.Extra newest = c.e.extras.isEmpty() ? null : c.e.extras.get(c.e.extras.size() - 1);
        if (newest == null || newest.beds.isEmpty()) {
            return;
        }
        Map<Integer, Integer> sizes = new java.util.HashMap<>();
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (ir.present()) {
                sizes.merge(ir.home, 1, Integer::sum);
            }
        }
        int moved = 0;
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (moved >= Math.max(1, newest.beds.size() - 1)) {
                break;
            }
            if (!ir.present() || ir.mind == null || ir.mind.age != Mind.Age.ADULT || sizes.getOrDefault(ir.home, 0) < 3 || hasKinHere(c, ir)) {
                continue;
            }
            BlockPos bed = freeBedIn(c, Economy.EXTRA_BASE + newest.id);
            if (bed == null) {
                break;
            }
            sizes.merge(ir.home, -1, Integer::sum);
            ir.home = Economy.EXTRA_BASE + newest.id;
            ir.bed = bed;
            ir.mind.household = ir.home;
            EconomySim.remember(ir, Memory.Type.MOVED_HOME, "", "new house", c.now);
            ir.mind.feel(Emotion.HAPPINESS, 0.3);
            Daily.event(c, "NEW_HOUSEHOLD", "i:" + ir.index, Daily.first(c, ir.index) + " moved into the new house", 24000L * 3);
            moved++;
        }
        SettlementState.get(c.world).markDirty();
    }
}
