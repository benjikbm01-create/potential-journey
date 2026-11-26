package dev.underdeep.civ.npc;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.underdeep.civ.mind.Activity;
import dev.underdeep.civ.mind.Bond;
import dev.underdeep.civ.mind.Feelings.Emotion;
import dev.underdeep.civ.mind.Feelings.Need;
import dev.underdeep.civ.mind.Memory;
import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.mind.Trait;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Development diagnostics for inhabitants ({@code /underdeep npc ...}, permission-gated with the rest of
 * {@code /underdeep}). Target: the nearest inhabitant within 8 blocks of the caller.
 */
public final class NpcCommands {
    private NpcCommands() {
    }

    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return CommandManager.literal("npc")
                .executes(c -> profile(c))
                .then(CommandManager.literal("memories").executes(NpcCommands::memories))
                .then(CommandManager.literal("relationships").executes(NpcCommands::relationships))
                .then(CommandManager.literal("knowledge").executes(NpcCommands::knowledge))
                .then(CommandManager.literal("household").executes(NpcCommands::household))
                .then(CommandManager.literal("stats").executes(NpcCommands::stats))
                .then(CommandManager.literal("encounter").executes(NpcCommands::encounter))
                .then(CommandManager.literal("befriend").executes(NpcCommands::befriend))
                .then(CommandManager.literal("advance").then(CommandManager.argument("hours", IntegerArgumentType.integer(1, 72))
                        .executes(c -> advance(c, IntegerArgumentType.getInteger(c, "hours")))));
    }

    private record Target(InhabitantEntity e, SettlementRecord rec, SettlementRecord.InhabitantRecord r) {
    }

    @Nullable
    private static Target target(CommandContext<ServerCommandSource> c) {
        ServerPlayerEntity p = c.getSource().getPlayer();
        if (p == null) {
            c.getSource().sendError(Text.literal("Run this as a player near an inhabitant."));
            return null;
        }
        InhabitantEntity best = null;
        for (InhabitantEntity e : p.getServerWorld().getEntitiesByClass(InhabitantEntity.class, p.getBoundingBox().expand(8), e -> !e.exhibit)) {
            if (best == null || e.squaredDistanceTo(p) < best.squaredDistanceTo(p)) {
                best = e;
            }
        }
        SettlementRecord.InhabitantRecord r = best == null ? null : InhabitantManager.record(best);
        if (r == null || r.mind == null) {
            c.getSource().sendError(Text.literal("No inhabitant within 8 blocks."));
            return null;
        }
        return new Target(best, SettlementState.get(p.getServerWorld()).get(best.settlementId), r);
    }

    private static void say(CommandContext<ServerCommandSource> c, String s) {
        c.getSource().sendFeedback(() -> Text.literal(s), false);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static int profile(CommandContext<ServerCommandSource> c) {
        Target t = target(c);
        if (t == null) {
            return 0;
        }
        Mind m = t.r.mind;
        say(c, "§6" + t.r.name + "§r #" + t.r.index + " — " + t.r.profession + ", " + t.rec.civ.id() + " of " + t.rec.name + ", " + m.age.name().toLowerCase(Locale.ROOT)
                + ", skill " + m.skill + ", status " + m.status + ", health " + f(m.health) + ", tier ACTIVE");
        List<Trait> ts = new ArrayList<>(List.of(Trait.values()));
        ts.sort((a, b) -> Integer.compare(m.trait(b), m.trait(a)));
        StringBuilder hi = new StringBuilder(), lo = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            hi.append(ts.get(i).name().toLowerCase(Locale.ROOT)).append(' ').append(m.trait(ts.get(i))).append("  ");
            Trait l = ts.get(ts.size() - 1 - i);
            lo.append(l.name().toLowerCase(Locale.ROOT)).append(' ').append(m.trait(l)).append("  ");
        }
        say(c, "§7high:§r " + hi + "\n§7low:§r " + lo);
        StringBuilder em = new StringBuilder();
        for (Emotion e : Emotion.values()) {
            if (m.emotion(e) >= 0.05) {
                em.append(e.name().toLowerCase(Locale.ROOT)).append(' ').append(f(m.emotion(e))).append("  ");
            }
        }
        say(c, "§7mood:§r " + m.mood().name().toLowerCase(Locale.ROOT) + "  §7emotions:§r " + em);
        StringBuilder nd = new StringBuilder();
        for (Need n : Need.values()) {
            nd.append(n.name().toLowerCase(Locale.ROOT)).append(' ').append(f(m.need(n))).append("  ");
        }
        say(c, "§7needs:§r " + nd);
        Activity a = Activity.byOrdinal(m.activity);
        say(c, "§7activity:§r " + (a == null ? "-" : a.id()) + " for " + (c.getSource().getWorld().getTime() - m.activitySince) / 1000.0 + " h, target "
                + m.activityTarget + " — " + m.reason + (t.e.goalPos == null ? "" : " @ " + t.e.goalPos.toShortString()));
        StringBuilder gs = new StringBuilder();
        for (Mind.Goal g : m.goals) {
            gs.append(g.type.name().toLowerCase(Locale.ROOT)).append(g.target.isEmpty() ? "" : "(" + g.target + ")").append(' ').append(f(g.progress)).append("  ");
        }
        say(c, "§7goals:§r " + (gs.length() == 0 ? "none" : gs) + "  §7request:§r " + (m.request == null ? "none" : m.request.kind + " " + m.request.item
                + " x" + m.request.count) + "  §7possessions:§r " + m.possessions.size() + "  §7likes:§r " + m.likes + " §7dislike:§r " + m.dislike
                + " §7place:§r " + m.favoritePlace);
        if (m.companionOf != null) {
            say(c, "§7companion of§r " + m.companionOf + " — " + Companions.mode(m) + ", " + Companions.stance(m) + ", pack "
                    + m.companionPack.stream().filter(s -> !s.isEmpty()).count() + " stacks");
        }
        return 1;
    }

    private static int memories(CommandContext<ServerCommandSource> c) {
        Target t = target(c);
        if (t == null) {
            return 0;
        }
        long now = c.getSource().getWorld().getTime();
        List<Memory> list = new ArrayList<>(t.r.mind.memories.list);
        list.sort((a, b) -> Double.compare(b.weight(now), a.weight(now)));
        say(c, t.r.name + ": " + list.size() + " memories (limit " + Society.memLimit() + ")");
        for (Memory m : list.subList(0, Math.min(20, list.size()))) {
            say(c, " " + m.type.name().toLowerCase(Locale.ROOT) + " " + m.who + (m.detail.isEmpty() ? "" : " [" + m.detail + "]") + " x" + m.count
                    + " imp " + f(m.importance) + " w " + f(m.weight(now)) + " age " + (now - m.time) / 1000 + " h");
        }
        return 1;
    }

    private static int relationships(CommandContext<ServerCommandSource> c) {
        Target t = target(c);
        if (t == null) {
            return 0;
        }
        List<Map.Entry<Integer, Bond>> list = new ArrayList<>(t.r.mind.people.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue().warmth(), a.getValue().warmth()));
        say(c, t.r.name + ": " + list.size() + " relationships, " + t.r.mind.players.size() + " with players");
        for (Map.Entry<Integer, Bond> e : list.subList(0, Math.min(14, list.size()))) {
            Bond b = e.getValue();
            String who = e.getKey() < t.rec.inhabitants.size() ? t.rec.inhabitants.get(e.getKey()).name : "?";
            say(c, " " + who + ": " + b.label().name().toLowerCase(Locale.ROOT) + (b.kin != Bond.Kin.NONE ? " (" + b.kin + ")" : "") + " fam " + b.familiarity
                    + " trust " + b.trust + " aff " + b.affection + " resp " + b.respect + " res " + b.resentment + " fear " + b.fear);
        }
        for (var e : t.r.mind.players.entrySet()) {
            Bond b = e.getValue();
            say(c, " player " + e.getKey() + ": " + b.playerWord() + " fam " + b.familiarity + " trust " + b.trust + " aff " + b.affection + " res "
                    + b.resentment + " fear " + b.fear);
        }
        return 1;
    }

    private static int knowledge(CommandContext<ServerCommandSource> c) {
        Target t = target(c);
        if (t == null) {
            return 0;
        }
        long now = c.getSource().getWorld().getTime();
        say(c, t.r.name + " knows " + t.r.mind.knowledge.size() + " things");
        for (Mind.Info i : t.r.mind.knowledge) {
            say(c, " " + i.type + " " + i.subject + " [" + i.detail + "] " + i.source + " conf " + i.confidence + " retold " + i.retold + " age "
                    + (now - i.time) / 1000 + " h");
        }
        return 1;
    }

    private static int household(CommandContext<ServerCommandSource> c) {
        Target t = target(c);
        if (t == null) {
            return 0;
        }
        say(c, t.r.name + " household (building " + t.r.home + "):");
        for (SettlementRecord.InhabitantRecord o : t.rec.inhabitants) {
            if (o.home == t.r.home && o.home >= 0) {
                Bond b = t.r.mind.people.get(o.index);
                say(c, " " + o.name + " (" + o.profession + ", " + (o.alive ? "alive" : "gone") + ")" + (b == null || o == t.r ? "" : " — "
                        + b.label().name().toLowerCase(Locale.ROOT) + (b.kin != Bond.Kin.NONE ? " " + b.kin : "")));
            }
        }
        return 1;
    }

    private static int stats(CommandContext<ServerCommandSource> c) {
        ServerWorld w = c.getSource().getWorld();
        InhabitantManager m = InhabitantManager.get(w);
        if (m == null) {
            c.getSource().sendError(Text.literal("No settlements in this world."));
            return 0;
        }
        Society s = m.society;
        int records = 0, minds = 0, companions = 0;
        for (SettlementRecord rec : SettlementState.get(w).all().values()) {
            for (SettlementRecord.InhabitantRecord r : rec.inhabitants) {
                records += r.alive ? 1 : 0;
                minds += r.alive && r.mind != null ? 1 : 0;
                companions += r.alive && r.mind != null && r.mind.companionOf != null ? 1 : 0;
            }
        }
        long t0 = System.nanoTime();
        m.thinkAll();
        long thinkUs = (System.nanoTime() - t0) / 1000;
        int act = m.activeCount();
        say(c, "settlement records " + SettlementState.get(w).all().size() + ", living inhabitants " + records + " (" + minds + " with minds), companions "
                + companions + "; active " + act + "; abstract updates " + s.abstractUpdates + " (catch-up hours " + s.catchUpHours + "), active thoughts "
                + s.activeThinks + ", encounters " + s.encounters + "; one think pass over all active: " + thinkUs + " µs");
        return 1;
    }

    private static int encounter(CommandContext<ServerCommandSource> c) {
        Target t = target(c);
        if (t == null) {
            return 0;
        }
        InhabitantEntity other = null;
        for (InhabitantEntity e : c.getSource().getWorld().getEntitiesByClass(InhabitantEntity.class, t.e.getBoundingBox().expand(16), e -> e != t.e && !e.exhibit)) {
            if (other == null || e.squaredDistanceTo(t.e) < other.squaredDistanceTo(t.e)) {
                other = e;
            }
        }
        SettlementRecord.InhabitantRecord o = other == null ? null : InhabitantManager.record(other);
        if (o == null || o.mind == null || other.settlementId != t.e.settlementId) {
            c.getSource().sendError(Text.literal("No second inhabitant of the same settlement within 16 blocks."));
            return 0;
        }
        long now = c.getSource().getWorld().getTime();
        Encounters.Result res = Encounters.run(t.rec, t.r, o, now, new Random(), Society.memLimit());
        if (res == null) {
            say(c, "No encounter was eligible.");
            return 0;
        }
        InhabitantManager mgr = InhabitantManager.get(c.getSource().getWorld());
        mgr.society.afterEncounter(t.rec, t.r, o, res, now);
        mgr.society.say(t.e, res.lineA(), 0);
        mgr.society.say(other, res.lineB(), 45);
        say(c, res.type() + ": " + t.r.name + " \"" + res.lineA() + "\" — " + o.name + " \"" + res.lineB() + "\"; now "
                + t.r.mind.bond(o.index).label().name().toLowerCase(Locale.ROOT));
        SettlementState.get(c.getSource().getWorld()).markDirty();
        return 1;
    }

    /** Testing aid: makes the nearest inhabitant regard the caller as a trusted friend (for companion tests). */
    private static int befriend(CommandContext<ServerCommandSource> c) {
        Target t = target(c);
        if (t == null || c.getSource().getPlayer() == null) {
            return 0;
        }
        Bond b = t.r.mind.bond(c.getSource().getPlayer().getUuid());
        b.familiarity = Math.max(b.familiarity, 60);
        b.trust = Math.max(b.trust, 50);
        b.affection = Math.max(b.affection, 50);
        b.resentment = 0;
        b.fear = 0;
        SettlementState.get(c.getSource().getWorld()).markDirty();
        say(c, t.r.name + " now regards you as: " + b.playerWord());
        return 1;
    }

    /** Controlled test: advances every unembodied inhabitant of the nearest settlement by some hours (bounded catch-up). */
    private static int advance(CommandContext<ServerCommandSource> c, int hours) {
        Target t = target(c);
        if (t == null) {
            return 0;
        }
        InhabitantManager mgr = InhabitantManager.get(c.getSource().getWorld());
        long now = c.getSource().getWorld().getTime();
        int n = 0;
        long enc = mgr.society.encounters;
        long t0 = System.nanoTime();
        for (SettlementRecord.InhabitantRecord r : t.rec.inhabitants) {
            if (r.alive && r.mind != null && !mgr.isActive(t.rec.id, r.index) && !Companions.travelling(r.mind)) {
                r.mind.lastUpdate = now - hours * 1000L;
                mgr.society.simulate(t.rec, r, now);
                n++;
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        SettlementState.get(c.getSource().getWorld()).markDirty();
        say(c, "Advanced " + n + " unembodied inhabitants of " + t.rec.name + " by " + hours + " h (catch-up capped at 24 h) in " + ms + " ms; "
                + (mgr.society.encounters - enc) + " encounters.");
        return 1;
    }
}
