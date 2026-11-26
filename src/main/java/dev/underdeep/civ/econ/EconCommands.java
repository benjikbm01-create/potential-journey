package dev.underdeep.civ.econ;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.settlement.SettlementMap;
import dev.underdeep.civ.settlement.SettlementSite;
import dev.underdeep.civ.settlement.Settlements;
import dev.underdeep.config.UnderdeepConfig;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Development diagnostics for the Stage 6 economy ({@code /underdeep econ ...}, permission-gated with the rest of
 * {@code /underdeep}). The target is the settlement nearest to the caller.
 */
public final class EconCommands {
    private EconCommands() {
    }

    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        LiteralArgumentBuilder<ServerCommandSource> n = CommandManager.literal("econ").executes(c -> summary(c));
        for (String s : new String[]{"ledger", "prices", "workforce", "households", "merchants", "decisions", "projects", "shipments", "network",
                "relations", "events", "properties", "businesses", "contracts", "audit", "world"}) {
            n.then(CommandManager.literal(s).executes(c -> section(c, s)));
        }
        n.then(CommandManager.literal("advance").then(CommandManager.argument("hours", IntegerArgumentType.integer(1, 240))
                .executes(c -> advance(c, IntegerArgumentType.getInteger(c, "hours")))));
        n.then(CommandManager.literal("build").then(CommandManager.argument("kind", StringArgumentType.word())
                .executes(c -> build(c, StringArgumentType.getString(c, "kind")))));
        n.then(CommandManager.literal("shortage").executes(EconCommands::shortage));
        n.then(CommandManager.literal("daily").executes(EconCommands::daily));
        return n;
    }

    private static void say(CommandContext<ServerCommandSource> c, String s) {
        c.getSource().sendFeedback(() -> Text.literal(s), false);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    @Nullable
    static Economy target(CommandContext<ServerCommandSource> c) {
        ServerWorld w = c.getSource().getServer().getOverworld();
        SettlementMap map = Settlements.of(w);
        EconomyState st = EconomyState.get(w);
        if (map == null || st.economies.isEmpty()) {
            c.getSource().sendError(Text.literal("No settlement economies yet (they appear near players within " + UnderdeepConfig.get().economyRadius
                    + " blocks)."));
            return null;
        }
        var pos = c.getSource().getPosition();
        Economy best = null;
        double bd = Double.MAX_VALUE;
        for (Economy e : st.economies.values()) {
            SettlementSite s = map.byId(e.id);
            if (s != null) {
                double d = Math.hypot(s.x() - pos.x, s.z() - pos.z);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
        }
        return best;
    }

    private static EconomyManager mgr(CommandContext<ServerCommandSource> c) {
        return EconomyManager.get(c.getSource().getServer().getOverworld());
    }

    private static int summary(CommandContext<ServerCommandSource> c) {
        Economy e = target(c);
        if (e == null) {
            return 0;
        }
        EconomySim.Ctx x = mgr(c).ctx(e);
        say(c, "§6" + e.name + "§r (" + e.civ.id() + (x == null ? "" : ", " + x.rec.category.id() + ", " + x.rec.alive() + " people, " + x.region.name())
                + ") prosperity " + f(e.score * 100) + "%, treasury " + f(e.treasuryBalance) + ", money " + f(e.money()) + ", goods " + f(e.totalUnits())
                + "/" + f(e.storageCapacity) + ", leaders " + e.leadership + " " + e.leaders);
        StringBuilder p = new StringBuilder();
        for (Prosperity pr : Prosperity.values()) {
            p.append(pr.name().toLowerCase(Locale.ROOT)).append(' ').append(f(e.prosperity(pr) * 100)).append("  ");
        }
        say(c, "§7" + p);
        if (x != null) {
            say(c, "food " + f(Daily.foodDays(x)) + " days, produced " + f(Daily.foodProducedPerDay(x)) + "/day vs need " + f(x.nutritionNeedPerDay)
                    + "; free beds " + Daily.freeBeds(x) + "; vacancies " + Population.vacancies(x).size() + "; partners " + x.state.connectionsOf(e.id).size());
        }
        return 1;
    }

    private static int section(CommandContext<ServerCommandSource> c, String s) {
        ServerWorld w = c.getSource().getServer().getOverworld();
        EconomyState st = EconomyState.get(w);
        Economy e = s.equals("world") || s.equals("relations") ? null : target(c);
        if (e == null && !s.equals("world") && !s.equals("relations")) {
            return 0;
        }
        switch (s) {
            case "ledger", "prices" -> {
                List<Map.Entry<String, Economy.Stock>> l = new ArrayList<>(e.stock.entrySet());
                l.removeIf(en -> en.getValue().qty < 0.5 && en.getValue().rC < 0.1);
                l.sort((a, b) -> Double.compare(b.getValue().qty * value(b.getKey()), a.getValue().qty * value(a.getKey())));
                for (var en : l.subList(0, Math.min(24, l.size()))) {
                    Economy.Stock k = en.getValue();
                    Goods.Good g = Goods.get(en.getKey());
                    say(c, " " + en.getKey().replace("minecraft:", "").replace("underdeep:", "") + ": " + f(k.qty) + (k.reserved > 0 ? " (res " + f(k.reserved)
                            + ")" : "") + " want " + f(k.desired) + " | price " + String.format(Locale.ROOT, "%.3f", k.price) + " (base "
                            + (g == null ? "?" : g.value()) + ") | /day +" + f(k.rP) + " -" + f(k.rC) + " in " + f(k.rI) + " out " + f(k.rE)
                            + (g == null ? "" : " [" + Market.label(e, g) + "]"));
                }
            }
            case "workforce" -> {
                for (Economy.Workplace wp : e.workplaces.values()) {
                    say(c, " #" + wp.building + " " + wp.kind + ": " + wp.workers.size() + "/" + wp.positions + " " + wp.status + " recipe " + wp.recipe
                            + (wp.missing.isEmpty() ? "" : " missing " + wp.missing) + " output " + f(wp.output) + " wage " + String.format(Locale.ROOT,
                            "%.2f/h", wp.wage) + " idle " + f(wp.idleHours) + "h");
                }
                EconomySim.Ctx x = mgr(c).ctx(e);
                if (x != null) {
                    for (Population.Vacancy v : Population.vacancies(x)) {
                        say(c, " vacancy: " + v.profession() + " at #" + v.building());
                    }
                }
                e.pools.forEach((k, p) -> say(c, " pool " + k + ": " + f(p.level) + "/" + f(p.capacity) + " regen " + p.regen + "/day pressure " + f(p.pressure)));
            }
            case "households" -> e.households.values().forEach(h -> say(c, " home " + h.home + ": purse " + f(h.purse) + " food security "
                    + f(h.foodSecurity * 100) + "% support " + f(h.support) + " storage " + h.storage.size()));
            case "merchants" -> e.merchants.values().forEach(m -> say(c, " #" + m.index + " till " + f(m.till) + " shelf " + m.shelf));
            case "decisions" -> e.decisions.forEach(d -> say(c, " " + d));
            case "events" -> e.events.forEach(ev -> say(c, " " + ev.type() + " (day " + ev.time() / 24000 + "): " + ev.detail()));
            case "projects" -> e.projects.forEach(p -> say(c, " #" + p.id + " " + p.kind + " " + p.status + " at " + p.origin.toShortString() + " progress "
                    + f(p.progress() * 100) + "% placed " + p.placed + "/" + p.total + " materials " + f(p.materialShare() * 100) + "% " + p.required
                    + " budget " + f(p.budget) + " — " + p.reason));
            case "shipments" -> st.shipments.values().forEach(sh -> {
                if (e == null || sh.origin == e.id || sh.dest == e.id) {
                    say(c, " #" + sh.id + " " + name(st, sh.origin) + " → " + name(st, sh.dest) + " " + sh.method + " " + sh.status + " " + sh.cargo
                            + " progress " + f(sh.progress) + "/" + f(sh.length) + " escrow " + f(sh.escrow) + (Caravans.embodied(sh.id) ? " [physical]" : "")
                            + (sh.incident.isEmpty() ? "" : " — " + sh.incident));
                }
            });
            case "network" -> st.connectionsOf(e.id).forEach(k -> say(c, " ↔ " + name(st, k.other(e.id)) + " " + f(k.distance) + " blocks, danger "
                    + f(k.danger * 100) + "%, trust " + f(k.trust) + ", reliability " + f(k.reliability) + (k.water ? ", water" : "") + ", "
                    + k.shipments + " shipments, " + k.agreements.size() + " agreements" + (k.blockedUntil > w.getTime() ? ", BLOCKED" : "")));
            case "relations" -> {
                var cv = dev.underdeep.civ.Civilization.values();
                for (int a = 0; a < cv.length; a++) {
                    for (int b = a + 1; b < cv.length; b++) {
                        EconomyState.Relation r = st.relations[a][b];
                        say(c, " " + cv[a].id() + "/" + cv[b].id() + ": trust " + f(r.trust) + " coop " + f(r.cooperation) + " tension " + f(r.tension)
                                + " competition " + f(r.competition) + " reliability " + f(r.reliability) + " dependence " + f(r.dependence)
                                + (r.restrictedUntil > w.getTime() ? " RESTRICTED" : ""));
                    }
                }
            }
            case "properties" -> st.properties.values().forEach(p -> say(c, " #" + p.id + " " + p.type + " in " + name(st, p.settlement) + " price " + f(p.price)
                    + " owner " + p.owner + (p.rentUntil > 0 ? " (rented)" : "")));
            case "businesses" -> st.businesses.values().forEach(b -> say(c, " #" + b.id + " " + b.name + " in " + name(st, b.settlement) + " till " + f(b.till)
                    + " sales " + b.sales + " employee " + b.employee));
            case "contracts" -> st.contracts.values().forEach(k -> say(c, " #" + k.id + " " + k.type + " " + k.status + " in " + name(st, k.settlement) + ": "
                    + Participation.describe(st, k) + " escrow " + f(k.escrow) + " taker " + k.taker));
            case "audit" -> {
                double expected = e.initialMoney + e.issued - e.sunk + e.playerIn - e.playerOut + e.tradeIn - e.tradeOut;
                say(c, e.name + ": money now " + f(e.money()) + " | initial " + f(e.initialMoney) + " + issued " + f(e.issued) + " − sunk " + f(e.sunk)
                        + " + players in " + f(e.playerIn) + " − out " + f(e.playerOut) + " + trade in " + f(e.tradeIn) + " − out " + f(e.tradeOut)
                        + " (− project budgets " + f(e.projects.stream().mapToDouble(p -> p.budget).sum()) + ") = " + f(expected) + "; wages "
                        + f(e.wagesPaid) + ", support " + f(e.supportPaid) + "; rejected transactions " + Tx.rejected);
            }
            case "world" -> {
                EconomyManager m = mgr(c);
                long moving = st.shipments.values().stream().filter(Shipment::moving).count();
                say(c, st.economies.size() + " economies, " + st.connections.size() + " connections, " + moving + " shipments moving ("
                        + Caravans.active() + " physical), " + st.delivered + " delivered, " + st.lost + " lost; world money " + f(st.totalMoney())
                        + "; steps " + m.steps + ", daily runs " + m.dailyRuns + ", last step " + f(m.lastStepNanos / 1e6) + " ms, max "
                        + f(m.maxStepNanos / 1e6) + " ms");
                st.log.stream().limit(10).forEach(l -> say(c, " " + l));
            }
            default -> {
            }
        }
        return 1;
    }

    static double value(String id) {
        Goods.Good g = Goods.get(id);
        return g == null ? 0 : g.value();
    }

    static String name(EconomyState st, long id) {
        Economy e = st.economies.get(id);
        return e == null ? "?" : e.name;
    }

    private static int advance(CommandContext<ServerCommandSource> c, int hours) {
        Economy e = target(c);
        if (e == null) {
            return 0;
        }
        EconomyManager m = mgr(c);
        long t0 = System.nanoTime();
        EconomySim.Ctx x = m.ctx(e);
        if (x == null) {
            return 0;
        }
        double left = hours;
        while (left > 0) {
            double h = Math.min(6, left);
            EconomySim.step(x, h);
            left -= h;
        }
        for (int d = 0; d < hours / 24; d++) {
            Daily.run(m, x);
        }
        say(c, "Advanced " + e.name + " by " + hours + " h (" + hours / 24 + " daily runs) in " + f((System.nanoTime() - t0) / 1e6) + " ms.");
        EconomyState.get(c.getSource().getServer().getOverworld()).markDirty();
        return 1;
    }

    private static int build(CommandContext<ServerCommandSource> c, String kind) {
        Economy e = target(c);
        if (e == null) {
            return 0;
        }
        try {
            Project.Kind k = Project.Kind.valueOf(kind.toUpperCase(Locale.ROOT));
            EconomySim.Ctx x = mgr(c).ctx(e);
            boolean ok = x != null && Construction.propose(mgr(c), x, k, "an operator asked for it", "");
            say(c, ok ? "Project proposed." : "Could not propose (limit reached or no plot).");
            return ok ? 1 : 0;
        } catch (IllegalArgumentException ex) {
            c.getSource().sendError(Text.literal("Kinds: " + java.util.Arrays.toString(Project.Kind.values())));
            return 0;
        }
    }

    /** Controlled test: removes most food from the ledger (a disruption) so the settlement's own responses can be observed. */
    private static int shortage(CommandContext<ServerCommandSource> c) {
        Economy e = target(c);
        if (e == null) {
            return 0;
        }
        double removed = 0;
        for (Goods.Good g : Goods.all()) {
            if (g.food()) {
                removed += e.take(g.id(), e.free(g.id()) * 0.9);
            }
        }
        e.sunk += 0; // goods only; counted as a test disruption
        say(c, "Removed " + f(removed) + " units of food from " + e.name + " (test disruption). Watch /underdeep econ decisions, shipments, events.");
        return 1;
    }

    private static int daily(CommandContext<ServerCommandSource> c) {
        Economy e = target(c);
        EconomySim.Ctx x = e == null ? null : mgr(c).ctx(e);
        if (x == null) {
            return 0;
        }
        Daily.run(mgr(c), x);
        e.decisions.stream().limit(3).forEach(d -> say(c, " " + d));
        return 1;
    }

    static List<SettlementRecord.InhabitantRecord> unused() {
        return List.of();
    }
}
