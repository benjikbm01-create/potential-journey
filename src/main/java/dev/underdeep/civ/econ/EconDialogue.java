package dev.underdeep.civ.econ;

import dev.underdeep.civ.npc.Dialogue;
import dev.underdeep.civ.npc.InhabitantEntity;
import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.npc.SettlementState;
import dev.underdeep.civ.npc.Topics;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Stage 6 conversation options: what a seller has in stock (with availability labels and the reason a good is
 * expensive), the noticeboard (open contracts, construction, gatherings, property for sale, openings), contracts
 * (accept / hand in), buying or renting property, registering a shop, hiring a shopkeeper, and what the town needs.
 * Everything shown reflects the actual ledger and records.
 */
public final class EconDialogue {
    private EconDialogue() {
    }

    @Nullable
    static Economy econ(Topics.Talk t) {
        return EconomyManager.economy(t.rec.id);
    }

    static boolean speaksForTown(Economy e, Topics.Talk t) {
        return e.leaders.contains(t.r.index) || t.r.profession.equals("innkeeper") || t.r.profession.equals("merchant") && e.leaders.isEmpty();
    }

    public static void opened(ServerPlayerEntity p, SettlementRecord rec, SettlementRecord.InhabitantRecord r) {
        Economy e = EconomyManager.economy(rec.id);
        if (e != null && (e.leaders.contains(r.index) || r.profession.equals("innkeeper"))) {
            Participation.pending(p, rec.id); // finished escorts get their pay when they next speak to the town
        }
    }

    public static void options(ServerPlayerEntity p, InhabitantEntity ent, Topics.Talk t, List<String> ids, List<String> labels) {
        Economy e = econ(t);
        if (e == null) {
            return;
        }
        Economy.Merchant m = e.merchants.get(t.r.index);
        if (m != null && !m.shelf.isEmpty()) {
            Dialogue.opt(ids, labels, "e_stock", "What do you have in stock?");
        }
        if (speaksForTown(e, t)) {
            Dialogue.opt(ids, labels, "e_notice", "What's on the noticeboard?");
        }
        EconomyState st = EconomyState.get((ServerWorld) ent.getWorld());
        for (EconomyState.Contract k : st.contracts.values()) {
            Goods.Good g = Goods.get(k.good);
            if (k.settlement == e.id && k.status == EconomyState.Contract.Status.TAKEN && p.getUuid().equals(k.taker) && g != null
                    && Tx.count(p, g.item()) >= k.qty && (speaksForTown(e, t) || k.issuer == t.r.index)) {
                Dialogue.opt(ids, labels, "e_hand:" + k.id, "Hand in " + k.qty + " " + g.name());
            }
        }
        EconomyState.Business b = Participation.businessOf(st, p.getUuid(), e.id);
        if (b != null && b.employee < 0 && t.m.age == dev.underdeep.civ.mind.Mind.Age.ADULT && t.m.employer == null && t.m.companionOf == null
                && t.familiar()) {
            Dialogue.opt(ids, labels, "e_hire", "Would you keep my shop?");
        }
        if (p.getUuid().equals(t.m.employer)) {
            Dialogue.opt(ids, labels, "e_fire", "You can stop working at my shop");
        }
    }

    /** Options of the noticeboard sub-menu. */
    public static void menu(ServerPlayerEntity p, InhabitantEntity ent, Topics.Talk t, String menu, List<String> ids, List<String> labels) {
        Economy e = econ(t);
        if (e == null || !menu.equals("notice")) {
            return;
        }
        EconomyState st = EconomyState.get((ServerWorld) ent.getWorld());
        int n = 0;
        for (EconomyState.Contract k : Participation.open(st, e.id)) {
            if (n++ >= 3) {
                break;
            }
            Dialogue.opt(ids, labels, "e_accept:" + k.id, "Take on: " + Participation.describe(st, k));
        }
        int props = 0;
        for (EconomyState.Property pr : st.properties.values()) {
            if (pr.settlement == e.id && pr.owner == null && props++ < 1) {
                Dialogue.opt(ids, labels, "e_buy:" + pr.id, "Buy the " + pr.type + " (" + (int) Math.ceil(pr.price) + " emeralds)");
                Dialogue.opt(ids, labels, "e_rent:" + pr.id, "Rent the " + pr.type + " for a week (" + (int) Math.ceil(pr.price / 5) + ")");
            }
        }
        boolean owns = st.properties.values().stream().anyMatch(pr -> pr.settlement == e.id && p.getUuid().equals(pr.owner));
        if (owns && Participation.businessOf(st, p.getUuid(), e.id) == null) {
            Dialogue.opt(ids, labels, "e_open", "Register my property as a shop");
        }
        Dialogue.opt(ids, labels, "e_needs", "What does " + e.name + " need?");
    }

    @Nullable
    public static String choose(ServerPlayerEntity p, InhabitantEntity ent, Topics.Talk t, Dialogue.Session s, String option) {
        if (!option.startsWith("e_")) {
            return null;
        }
        Economy e = econ(t);
        if (e == null) {
            return "Hm?";
        }
        ServerWorld w = (ServerWorld) ent.getWorld();
        EconomyState st = EconomyState.get(w);
        EconomyManager mgr = EconomyManager.get(w);
        EconomySim.Ctx c = mgr == null ? null : mgr.ctx(e);
        String[] parts = option.split(":", 2);
        long id = parts.length > 1 ? Long.parseLong(parts[1]) : -1;
        st.markDirty();
        switch (parts[0]) {
            case "e_stock" -> {
                return stock(e, t);
            }
            case "e_notice" -> {
                if (!speaksForTown(e, t)) {
                    return null;
                }
                s.menu = "notice";
                return noticeboard(st, e, c, w.getTime());
            }
            case "e_needs" -> {
                return c == null ? "Hard to say." : needs(c);
            }
            case "e_accept" -> {
                EconomyState.Contract k = st.contracts.get(id);
                return k == null || k.settlement != e.id ? "That's no longer needed." : Participation.accept(p, k);
            }
            case "e_hand" -> {
                EconomyState.Contract k = st.contracts.get(id);
                if (k == null || c == null || k.settlement != e.id) {
                    return "That's no longer needed.";
                }
                String res = Participation.handIn(p, k, c);
                if (k.status == EconomyState.Contract.Status.DONE) {
                    dev.underdeep.guide.Advancements.event(p, "contract_completed", k.good);
                }
                return res;
            }
            case "e_buy", "e_rent" -> {
                EconomyState.Property pr = st.properties.get(id);
                if (pr == null || pr.settlement != e.id || !speaksForTown(e, t)) {
                    return "That's not for sale.";
                }
                String res = Participation.buy(p, pr, parts[0].equals("e_rent"));
                if (p.getUuid().equals(pr.owner)) {
                    dev.underdeep.guide.Advancements.event(p, "property_bought", String.valueOf(pr.id));
                }
                return res;
            }
            case "e_open" -> {
                String res = Participation.openBusiness(p, e);
                if (Participation.businessOf(st, p.getUuid(), e.id) != null) {
                    dev.underdeep.guide.Advancements.event(p, "business_opened", String.valueOf(e.id));
                }
                return res;
            }
            case "e_hire" -> {
                String res = Participation.hire(p, t.rec, t.r);
                if (p.getUuid().equals(t.m.employer)) {
                    dev.underdeep.guide.Advancements.event(p, "hired", t.r.name);
                }
                return res;
            }
            case "e_fire" -> {
                if (p.getUuid().equals(t.m.employer)) {
                    t.m.employer = null;
                    EconomyState.Business b = Participation.businessOf(st, p.getUuid(), e.id);
                    if (b != null && b.employee == t.r.index) {
                        b.employee = -1;
                    }
                    SettlementState.get(w).markDirty();
                    return "Alright. It was good work while it lasted.";
                }
                return "I don't work for you.";
            }
            default -> {
                return null;
            }
        }
    }

    static String stock(Economy e, Topics.Talk t) {
        Economy.Merchant m = e.merchants.get(t.r.index);
        if (m == null || m.shelf.isEmpty()) {
            return "My shelves are bare right now.";
        }
        List<String> lines = new ArrayList<>();
        String dear = null;
        double dearRatio = 1.35;
        for (var en : m.shelf.entrySet()) {
            Goods.Good g = Goods.get(en.getKey());
            if (g == null || en.getValue() <= 0) {
                continue;
            }
            double price = e.stock(g.id()).price;
            if (lines.size() < 6) {
                lines.add(g.name() + " (" + Market.label(e, g) + ")");
            }
            if (price / g.value() > dearRatio) {
                dearRatio = price / g.value();
                dear = g.id();
            }
        }
        String s = "I have " + String.join(", ", lines) + ".";
        if (dear != null) {
            Goods.Good g = Goods.get(dear);
            Economy.Stock k = e.stock(dear);
            String why = k.rI > k.rP + 0.05 ? "it all comes from elsewhere" : e.recent("ROUTE_BLOCKED", t.now) ? "the road is blocked"
                    : k.free() < k.desired * 0.5 ? "we're short of it" : "everyone wants it";
            s += " " + cap(g.name()) + " is dear at the moment: " + why + ".";
        }
        return s;
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String noticeboard(EconomyState st, Economy e, @Nullable EconomySim.Ctx c, long now) {
        List<String> lines = new ArrayList<>();
        for (EconomyState.Contract k : Participation.open(st, e.id)) {
            if (lines.size() < 3) {
                lines.add("Wanted — " + Participation.describe(st, k) + ": " + k.reason + ".");
            }
        }
        for (Project p : e.projects) {
            if (p.active() && p.kind != Project.Kind.REPAIR) {
                lines.add("Planned: a new " + Construction.label(p.kind) + " (" + p.status.name().toLowerCase(Locale.ROOT) + ").");
                break;
            }
        }
        if (e.gatheringUntil > now) {
            lines.add("Gathering at the square: " + e.gatheringReason + ".");
        }
        for (EconomyState.Property pr : st.properties.values()) {
            if (pr.settlement == e.id && pr.owner == null) {
                lines.add("For sale: a " + pr.type + ", " + (int) Math.ceil(pr.price) + " emeralds.");
                break;
            }
        }
        if (c != null && !Population.vacancies(c).isEmpty()) {
            lines.add("We are looking for a " + Population.vacancies(c).get(0).profession() + ".");
        }
        for (Economy.Event ev : e.events) {
            if (ev.until() > now && (ev.type().startsWith("DEPLETED") || ev.type().equals("ROUTE_BLOCKED") || ev.type().equals("FOOD_SHORTAGE"))) {
                lines.add("Notice: " + ev.detail() + ".");
                break;
            }
        }
        return lines.isEmpty() ? "Nothing posted right now. Things are quiet." : String.join(" ", lines);
    }

    static String needs(EconomySim.Ctx c) {
        Goods.Good g = Trade.shortage(c);
        List<String> out = new ArrayList<>();
        if (g != null) {
            out.add("We are short of " + g.name() + ".");
        }
        for (Project p : c.e.projects) {
            if (p.status == Project.Status.MATERIALS) {
                for (var en : p.required.entrySet()) {
                    if (p.delivered.getOrDefault(en.getKey(), 0.0) < en.getValue()) {
                        out.add("The new " + Construction.label(p.kind) + " still needs " + en.getKey() + ".");
                        break;
                    }
                }
            }
        }
        if (Daily.freeBeds(c) <= 0) {
            out.add("Every bed in town is taken.");
        }
        if (out.isEmpty()) {
            return c.e.score > 0.65 ? "Honestly? We're doing well. Trade is good." : "We get by. Nothing urgent.";
        }
        return String.join(" ", out);
    }
}
