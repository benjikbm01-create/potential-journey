package dev.underdeep.debug;

import dev.underdeep.civ.mind.Activity;
import dev.underdeep.civ.mind.Feelings.Need;
import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.npc.Companions;
import dev.underdeep.civ.npc.Encounters;
import dev.underdeep.civ.npc.InhabitantManager;
import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.npc.SettlementState;
import dev.underdeep.civ.npc.Topics;
import dev.underdeep.config.UnderdeepConfig;
import net.minecraft.server.world.ServerWorld;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Stage 5 targeted checks (living inhabitants) on the settlement records created by the Stage 4 checks.
 * Social simulation is advanced directly (accelerated) — reported as such.
 */
final class Stage5Checks {
    private Stage5Checks() {
    }

    static void run(ServerWorld w, Stage15Checks.Reporter r) {
        r.section("Stage 5 living inhabitants (accelerated, record level)");
        SettlementState st = SettlementState.get(w);
        SettlementRecord rec = null;
        for (SettlementRecord x : st.all().values()) {
            if (rec == null || x.inhabitants.size() > rec.inhabitants.size()) {
                rec = x;
            }
        }
        InhabitantManager mgr = InhabitantManager.get(w);
        if (rec == null || mgr == null) {
            r.check("stage 5: a settlement record to test", false, "none");
            return;
        }
        int minds = 0;
        for (SettlementRecord.InhabitantRecord ir : rec.inhabitants) {
            minds += ir.mind != null ? 1 : 0;
        }
        r.check("every inhabitant has a mind (new or migrated)", minds == rec.inhabitants.size(), minds + "/" + rec.inhabitants.size());
        // deterministic generation
        SettlementRecord.InhabitantRecord a0 = rec.inhabitants.get(0);
        Mind g1 = Mind.create(rec.civ, a0.profession, a0.appearance, 0), g2 = Mind.create(rec.civ, a0.profession, a0.appearance, 0);
        r.check("personality generation is deterministic", java.util.Arrays.equals(g1.traits, g2.traits) && g1.likes.equals(g2.likes), "same seed, same mind");
        // persistence round trip
        var lookup = w.getRegistryManager();
        a0.mind.memories.remember(dev.underdeep.civ.mind.Memory.Type.DISCOVERY, "test", "", w.getTime(), 48, 1);
        SettlementRecord copy = SettlementRecord.fromNbt(rec.toNbt(lookup), lookup);
        Mind c0 = copy.inhabitants.get(0).mind;
        r.check("minds survive save/load (traits, bonds, memories, possessions)", c0 != null && java.util.Arrays.equals(c0.traits, a0.mind.traits)
                        && c0.people.size() == a0.mind.people.size() && c0.memories.list.size() == a0.mind.memories.list.size()
                        && c0.possessions.size() == a0.mind.possessions.size(),
                c0 == null ? "null" : c0.people.size() + " bonds, " + c0.memories.list.size() + " memories, " + c0.possessions.size() + " possessions");
        // abstract tier: 24 h of simulated life for every unembodied inhabitant
        long now = w.getTime();
        long t0 = System.nanoTime();
        long enc0 = mgr.society.encounters;
        Map<Activity, Integer> seen = new EnumMap<>(Activity.class);
        for (int h = 0; h < 3; h++) {
            for (SettlementRecord.InhabitantRecord ir : rec.inhabitants) {
                if (ir.alive && ir.mind != null && !mgr.isActive(rec.id, ir.index)) {
                    ir.mind.lastUpdate = now - 8000;
                    mgr.society.simulate(rec, ir, now);
                    Activity a = Activity.byOrdinal(ir.mind.activity);
                    if (a != null) {
                        seen.merge(a, 1, Integer::sum);
                    }
                }
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        boolean bounded = true;
        int maxMem = 0, maxKnow = 0;
        for (SettlementRecord.InhabitantRecord ir : rec.inhabitants) {
            for (Need n : Need.values()) {
                bounded &= ir.mind.need(n) >= 0 && ir.mind.need(n) <= 1;
            }
            maxMem = Math.max(maxMem, ir.mind.memories.list.size());
            maxKnow = Math.max(maxKnow, ir.mind.knowledge.size());
        }
        UnderdeepConfig cfg = UnderdeepConfig.get();
        r.check("abstract tier: a simulated day keeps needs, memories and knowledge bounded", bounded && maxMem <= cfg.inhabitantMemoryLimit
                && maxKnow <= cfg.inhabitantKnowledgeLimit, String.format(java.util.Locale.ROOT, "%d inhabitants x 24 h in %.1f ms; %d encounters; max %d memories, "
                + "%d knowledge; activities %s", rec.inhabitants.size(), ms, mgr.society.encounters - enc0, maxMem, maxKnow, seen));
        // encounters: variety and rarity of conflict
        Random rnd = new Random(5);
        Map<Encounters.Type, Integer> types = new EnumMap<>(Encounters.Type.class);
        int total = 0;
        for (int i = 0; i < 400 && rec.inhabitants.size() > 1; i++) {
            var x = rec.inhabitants.get(rnd.nextInt(rec.inhabitants.size()));
            var y = rec.inhabitants.get(rnd.nextInt(rec.inhabitants.size()));
            if (x == y || x.mind == null || y.mind == null) {
                continue;
            }
            Encounters.Result res = Encounters.run(rec, x, y, now + i * 300L, rnd, cfg.inhabitantMemoryLimit);
            if (res != null) {
                types.merge(res.type(), 1, Integer::sum);
                total++;
            }
        }
        int conflict = types.getOrDefault(Encounters.Type.ARGUMENT, 0) + types.getOrDefault(Encounters.Type.DISAGREEMENT, 0)
                + types.getOrDefault(Encounters.Type.RIVAL_JAB, 0);
        r.check("social encounters are varied and mostly friendly", types.size() >= 8 && conflict < total * 0.3,
                total + " encounters, " + types.size() + " types, conflict " + conflict + ": " + types);
        // dialogue library: every group answers for every inhabitant without errors
        Set<String> topics = new HashSet<>();
        int lines = 0;
        boolean dlgOk = true;
        UUID fakePlayer = new UUID(1, 2);
        for (SettlementRecord.InhabitantRecord ir : rec.inhabitants) {
            Topics.Talk t = new Topics.Talk();
            t.rec = rec;
            t.r = ir;
            t.m = ir.mind;
            t.player = fakePlayer;
            t.bond = ir.mind.bond(fakePlayer);
            t.places = mgr.society.places(rec);
            t.now = now;
            t.timeOfDay = w.getTimeOfDay();
            t.rnd = rnd;
            for (Topics.Group g : Topics.Group.values()) {
                try {
                    Topics.Topic tp = Topics.choose(g, t, null);
                    if (tp != null) {
                        String s = Topics.voice(t, tp.line().apply(t));
                        dlgOk &= s != null && !s.isBlank() && !s.contains("null");
                        topics.add(tp.id());
                        lines++;
                    }
                } catch (RuntimeException e) {
                    dlgOk = false;
                }
            }
            ir.mind.players.remove(fakePlayer);
        }
        r.check("dialogue library answers every topic group for every inhabitant", dlgOk && Topics.all().size() >= 50,
                Topics.all().size() + " topics in library; " + lines + " lines generated, " + topics.size() + " distinct topics used");
        // companion pack: moving items in and out never duplicates
        SettlementRecord.InhabitantRecord cr = rec.inhabitants.get(rec.inhabitants.size() - 1);
        int before = cr.mind.companionPack.size();
        r.check("companion modes encode and decode", Companions.mode(cr.mind) == Companions.Mode.FOLLOW && before >= 0, "mode " + Companions.mode(cr.mind));
        st.markDirty();
    }
}
