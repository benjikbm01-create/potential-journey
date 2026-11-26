package dev.underdeep.civ.npc;

import dev.underdeep.civ.mind.Activity;
import dev.underdeep.civ.mind.Bond;
import dev.underdeep.civ.mind.Brain;
import dev.underdeep.civ.mind.Feelings.Emotion;
import dev.underdeep.civ.mind.Feelings.Need;
import dev.underdeep.civ.mind.Goals;
import dev.underdeep.civ.mind.Memory;
import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.mind.Trait;
import dev.underdeep.civ.settlement.SettlementMap;
import dev.underdeep.civ.settlement.SettlementSite;
import dev.underdeep.civ.settlement.Settlements;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.UnderdeepCreature;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The social simulation of a world's inhabitants (Stage 5), shared by both tiers:
 * <ul>
 * <li><b>Active</b> — embodied inhabitants think once per second (staggered): sense danger, wildlife and
 * neighbours, advance their mind, choose activities with {@link Brain}, talk to each other (speech bubbles).</li>
 * <li><b>Abstract</b> — every other inhabitant is updated round-robin from the persistent records with a bounded
 * budget per tick: hourly steps (at most 24 per update) of needs, decisions, goals and encounters with other
 * unembodied inhabitants. Settlements far from every player update less often.</li>
 * </ul>
 * The record's mind is the single authority in both tiers; tier transitions only change who advances it.
 */
public final class Society {
    public static final String BUBBLE_TAG = "underdeep_bubble";
    final ServerWorld world;
    final InhabitantManager manager;
    private final Map<Long, Places> places = new HashMap<>();
    private final Map<Long, Long> pairCooldown = new HashMap<>();
    private final List<Bubble> bubbles = new ArrayList<>();
    private final List<Pending> pending = new ArrayList<>();
    private final List<long[]> queue = new ArrayList<>();
    private final Set<Long> nearSettlements = new HashSet<>();
    private int cursor;
    final Random rnd = new Random();
    public long abstractUpdates, encounters, activeThinks, catchUpHours;

    private record Bubble(DisplayEntity.TextDisplayEntity display, long until) {
    }

    private record Pending(InhabitantEntity speaker, String text, long at) {
    }

    Society(ServerWorld world, InhabitantManager manager) {
        this.world = world;
        this.manager = manager;
    }

    @Nullable
    public Places places(SettlementRecord rec) {
        Places p = places.get(rec.id);
        if (p == null) {
            SettlementMap map = Settlements.of(world);
            SettlementSite site = map == null ? null : map.byId(rec.id);
            if (site == null) {
                return null;
            }
            p = new Places(map.plan(site));
            places.put(rec.id, p);
        }
        return p;
    }

    static int memLimit() {
        return UnderdeepConfig.get().inhabitantMemoryLimit;
    }

    static int knowLimit() {
        return UnderdeepConfig.get().inhabitantKnowledgeLimit;
    }

    // ================================================================== context

    /** Decision context from the record alone (relationships, knowledge, settlement facilities). */
    Brain.Context context(SettlementRecord rec, SettlementRecord.InhabitantRecord r, Places pl, long now, long timeOfDay) {
        Brain.Context c = new Brain.Context();
        pl.fill(c, r);
        c.now = now;
        c.timeOfDay = timeOfDay;
        Mind m = r.mind;
        long day = now / 24000;
        double fw = -9, famw = -9;
        for (Map.Entry<Integer, Bond> e : m.people.entrySet()) {
            int idx = e.getKey();
            if (!alive(rec, idx)) {
                continue;
            }
            Bond b = e.getValue();
            double w = b.warmth() + Math.floorMod(idx * 31L + day * 17 + r.index, 13L) / 40.0; // varies day to day
            if (b.kin != Bond.Kin.NONE) {
                if (!b.household && w > famw) {
                    famw = w;
                    c.family = idx;
                }
            } else if (b.affection >= 20 && b.familiarity >= 25 && w > fw) {
                fw = w;
                c.friend = idx;
            }
            if (b.apprentice) {
                c.apprentice = idx;
            }
            if (b.resentment >= 25) {
                c.estranged = idx;
            }
        }
        Mind.Goal visit = m.goal(Mind.GoalType.VISIT_FRIEND);
        int vt = index(visit == null ? "" : visit.target);
        if (vt >= 0 && alive(rec, vt)) {
            c.friend = vt;
        }
        Mind.Goal rec2 = m.goal(Mind.GoalType.RECONCILE);
        int rt = index(rec2 == null ? "" : rec2.target);
        if (rt >= 0 && alive(rec, rt)) {
            c.estranged = rt;
        }
        for (Mind.Info i : m.knowledge) {
            int idx = index(i.subject);
            if (i.type == Mind.InfoType.PERSON_INJURED && now - i.time < 12000 && idx >= 0 && alive(rec, idx)
                    && rec.inhabitants.get(idx).mind.health < 0.7f && m.bond(idx).affection > 10) {
                c.injuredNeighbor = true;
                c.friend = idx;
            }
        }
        // someone they barely know: a chance to introduce themselves (sociable and curious people do)
        if (rec.inhabitants.size() > 1) {
            int cand = (int) Math.floorMod(r.index + day * 7 + now / 3000, (long) rec.inhabitants.size());
            Bond b = m.people.get(cand);
            if (cand != r.index && alive(rec, cand) && (b == null || b.familiarity < 8)) {
                c.newcomer = cand;
            }
        }
        return c;
    }

    static int index(String subject) {
        if (subject == null || !subject.startsWith("i:")) {
            return -1;
        }
        try {
            return Integer.parseInt(subject.substring(2));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static boolean alive(SettlementRecord rec, int idx) {
        return idx >= 0 && idx < rec.inhabitants.size() && rec.inhabitants.get(idx).alive && rec.inhabitants.get(idx).mind != null;
    }

    // ================================================================== abstract tier

    void abstractStep(UnderdeepConfig cfg, List<ServerPlayerEntity> players) {
        SettlementState state = SettlementState.get(world);
        if (cursor >= queue.size()) {
            queue.clear();
            nearSettlements.clear();
            SettlementMap map = Settlements.of(world);
            for (SettlementRecord rec : state.all().values()) {
                SettlementSite site = map == null ? null : map.byId(rec.id);
                for (ServerPlayerEntity p : players) {
                    if (site != null && site.contains(p.getBlockX(), p.getBlockZ(), cfg.distantSimulationDistance)) {
                        nearSettlements.add(rec.id);
                    }
                }
                for (SettlementRecord.InhabitantRecord r : rec.inhabitants) {
                    if (r.alive && r.mind != null) {
                        queue.add(new long[]{rec.id, r.index});
                    }
                }
            }
            cursor = 0;
        }
        long now = world.getTime();
        int budget = Math.max(1, cfg.mindUpdatesPerTick);
        boolean dirty = false;
        while (budget > 0 && cursor < queue.size()) {
            long[] q = queue.get(cursor++);
            SettlementRecord rec = state.get(q[0]);
            if (rec == null || q[1] >= rec.inhabitants.size()) {
                continue;
            }
            SettlementRecord.InhabitantRecord r = rec.inhabitants.get((int) q[1]);
            if (!r.alive || r.mind == null || manager.isActive(rec.id, r.index)) {
                continue;
            }
            long interval = nearSettlements.contains(rec.id) ? cfg.abstractUpdateInterval : cfg.distantUpdateInterval;
            if (r.mind.lastUpdate < 0) {
                r.mind.lastUpdate = now;
            }
            if (now - r.mind.lastUpdate < interval) {
                continue;
            }
            budget--;
            simulate(rec, r, now);
            dirty = true;
        }
        if (dirty) {
            state.markDirty();
        }
    }

    /**
     * Brings an unembodied inhabitant up to date: hourly steps of needs, decisions, goals and encounters. Catch-up
     * is bounded to the last 24 in-game hours (older time only matters as "they lived their routine").
     */
    public void simulate(SettlementRecord rec, SettlementRecord.InhabitantRecord r, long now) {
        Mind m = r.mind;
        Places pl = places(rec);
        if (pl == null) {
            m.lastUpdate = now;
            return;
        }
        long elapsed = Math.max(0, now - m.lastUpdate);
        if (elapsed > 24000) {
            elapsed = 24000;
        }
        int steps = (int) (elapsed / 1000);
        long t = now - elapsed;
        long tod = world.getTimeOfDay();
        for (int k = 0; k < steps; k++) {
            t += 1000;
            step(rec, r, pl, t, tod - (now - t), 1000, true);
        }
        long rest = now - t;
        if (rest > 0) {
            step(rec, r, pl, now, tod, rest, steps == 0);
        }
        catchUpHours += steps;
        abstractUpdates++;
        m.lastUpdate = now;
        noticeAbsences(rec, r, now);
    }

    private void step(SettlementRecord rec, SettlementRecord.InhabitantRecord r, Places pl, long t, long tod, long len, boolean social) {
        Mind m = r.mind;
        Brain.advance(m, len);
        Brain.Context c = context(rec, r, pl, t, tod);
        Brain.Choice ch = Brain.decide(m, c, rnd);
        Brain.begin(m, ch, t);
        Activity a = Activity.byOrdinal(m.activity);
        Goals.update(m, a, len / 1000.0, t, rnd, memLimit());
        if (!social || a == null || a == Activity.SLEEP || a == Activity.NAP || a == Activity.RECOVER) {
            return;
        }
        double chance = (a.cat == Activity.Cat.SOCIAL || a == Activity.TAVERN_EVENING || a == Activity.PLAY_GAME || a == Activity.EAT_TAVERN
                || a == Activity.BROWSE_MARKET ? 0.45 : a.cat == Activity.Cat.WORK ? 0.12 : 0.2) * UnderdeepConfig.get().npcConversationFrequency
                * (len / 1000.0);
        if (rnd.nextDouble() >= chance) {
            return;
        }
        SettlementRecord.InhabitantRecord partner = abstractPartner(rec, r, a);
        if (partner != null) {
            Encounters.Result res = Encounters.run(rec, r, partner, t, rnd, memLimit());
            if (res != null) {
                encounters++;
                afterEncounter(rec, r, partner, res, t);
            }
        }
    }

    @Nullable
    private SettlementRecord.InhabitantRecord abstractPartner(SettlementRecord rec, SettlementRecord.InhabitantRecord r, Activity a) {
        int target = r.mind.activityTarget;
        if (alive(rec, target) && !manager.isActive(rec.id, target)) {
            return rec.inhabitants.get(target);
        }
        // someone doing something at the same kind of place, or a housemate/coworker
        for (int tries = 0; tries < 8; tries++) {
            SettlementRecord.InhabitantRecord o = rec.inhabitants.get(rnd.nextInt(rec.inhabitants.size()));
            if (o == r || !o.alive || o.mind == null || manager.isActive(rec.id, o.index)) {
                continue;
            }
            Activity oa = Activity.byOrdinal(o.mind.activity);
            if (oa == null || oa == Activity.SLEEP || oa == Activity.NAP) {
                continue;
            }
            Bond b = r.mind.people.get(o.index);
            if (oa.place == a.place || b != null && (b.household || b.coworker && a.cat == Activity.Cat.WORK)) {
                return o;
            }
        }
        return null;
    }

    /** Goal completion and news reactions after any encounter (both tiers). */
    void afterEncounter(SettlementRecord rec, SettlementRecord.InhabitantRecord a, SettlementRecord.InhabitantRecord b, Encounters.Result res, long now) {
        Goals.completeIf(a.mind, Mind.GoalType.VISIT_FRIEND, "i:" + b.index, now, memLimit());
        if (res.type() == Encounters.Type.INTRODUCTION || a.mind.memories.has(Memory.Type.BECAME_FRIENDS, "i:" + b.index)) {
            if (a.mind.bond(b.index).label().ordinal() >= Bond.Label.FRIEND.ordinal()
                    && a.mind.bond(b.index).label().ordinal() <= Bond.Label.BEST_FRIEND.ordinal()) {
                Goals.completeIf(a.mind, Mind.GoalType.MAKE_FRIEND, null, now, memLimit());
            }
        }
        for (Mind.Info i : b.mind.knowledge) {
            if (i.time == now && i.source == Mind.Source.TOLD) {
                react(rec, b, i, now);
                Goals.completeIf(b.mind, Mind.GoalType.LEARN_NEWS, null, now, memLimit());
            }
        }
    }

    /** Emotional reaction to learning something (witnessed or told). */
    static void react(SettlementRecord rec, SettlementRecord.InhabitantRecord who, Mind.Info i, long now) {
        Mind m = who.mind;
        int idx = index(i.subject);
        switch (i.type) {
            case PERSON_LOST -> {
                if (idx < 0 || idx == who.index) {
                    return;
                }
                Bond b = m.bond(idx);
                if (b.kin != Bond.Kin.NONE || b.household && b.affection > 20) {
                    m.memories.remember(Memory.Type.LOST_FAMILY, i.subject, "", now, memLimit(), 1.2);
                    m.feel(Emotion.SADNESS, 0.6);
                    m.addGoal(Mind.GoalType.SPEND_TIME_FAMILY, "", now, 24000L * 3);
                } else if (b.affection >= 20) {
                    m.memories.remember(Memory.Type.LOST_FRIEND, i.subject, "", now, memLimit(), 1);
                    m.feel(Emotion.SADNESS, 0.4);
                } else {
                    m.feel(Emotion.CONCERN, 0.1);
                }
            }
            case PERSON_INJURED -> {
                if (idx >= 0 && m.bond(idx).affection > 15) {
                    m.feel(Emotion.CONCERN, 0.2 + m.t(Trait.COMPASSION) * 0.1);
                }
            }
            case PREDATOR_SEEN -> m.satisfy(Need.SAFETY, -0.1 - m.t(Trait.CAUTION) * 0.05);
            case DISCOVERY -> m.feel(Emotion.CURIOSITY, 0.2);
            default -> {
            }
        }
    }

    /** Households notice when a member is gone (no omniscience: only those who share a home or kin). */
    private void noticeAbsences(SettlementRecord rec, SettlementRecord.InhabitantRecord r, long now) {
        for (Map.Entry<Integer, Bond> e : r.mind.people.entrySet()) {
            int idx = e.getKey();
            if (idx < 0 || idx >= rec.inhabitants.size() || rec.inhabitants.get(idx).alive) {
                continue;
            }
            Bond b = e.getValue();
            if ((b.household || b.kin != Bond.Kin.NONE) && r.mind.learn(Mind.InfoType.PERSON_LOST, "i:" + idx, "", now, Mind.Source.WITNESSED, 80, knowLimit())) {
                react(rec, r, r.mind.knowledge.get(r.mind.knowledge.size() - 1), now);
            }
        }
    }

    // ================================================================== active tier

    /** What an embodied inhabitant perceives around itself. */
    static final class Sense {
        @Nullable LivingEntity danger;
        @Nullable UnderdeepCreature wildlife;
        @Nullable InhabitantEntity nearest;
        boolean guardNear;
        int othersNear;
    }

    Sense sense(InhabitantEntity e) {
        Sense s = new Sense();
        double best = Double.MAX_VALUE, bestOther = Double.MAX_VALUE;
        for (Entity x : world.getOtherEntities(e, e.getBoundingBox().expand(14, 6, 14), x -> x instanceof LivingEntity && x.isAlive())) {
            double d2 = e.squaredDistanceTo(x);
            if (x instanceof HostileEntity h) {
                if (d2 < 12 * 12 && d2 < best && e.canSee(h)) {
                    best = d2;
                    s.danger = h;
                }
            } else if (x instanceof UnderdeepCreature c) {
                CreatureSpecies.Temperament t = c.species().temperament();
                boolean threat = !c.isTamed() && (t == CreatureSpecies.Temperament.AMBUSH && d2 < 12 * 12
                        || t == CreatureSpecies.Temperament.PREDATOR && d2 < 6 * 6);
                if (threat && d2 < best && e.canSee(c)) {
                    best = d2;
                    s.danger = c;
                } else if (!threat && s.wildlife == null && d2 < 10 * 10) {
                    s.wildlife = c;
                }
            } else if (x instanceof InhabitantEntity o && o.settlementId == e.settlementId) {
                if (o.isGuard()) {
                    s.guardNear = true;
                }
                if (!o.isSleeping() && d2 < 6 * 6) {
                    s.othersNear++;
                    if (d2 < bestOther && o.getCustomer() == null) {
                        bestOther = d2;
                        s.nearest = o;
                    }
                }
            }
        }
        return s;
    }

    /** One "second" of thought for an embodied inhabitant. */
    void think(InhabitantEntity e, SettlementRecord rec, SettlementRecord.InhabitantRecord r) {
        Places pl = places(rec);
        if (pl == null || r.mind == null) {
            return;
        }
        activeThinks++;
        Mind m = r.mind;
        long now = world.getTime();
        m.health = Math.max(0.05f, e.getHealth() / e.getMaxHealth());
        Sense s = sense(e);
        long elapsed = m.lastUpdate < 0 ? 20 : now - m.lastUpdate;
        if (elapsed > 1000) {
            simulate(rec, r, now); // returning from a long absence of updates: catch up first
        } else {
            Brain.advance(m, elapsed);
            m.lastUpdate = now;
        }
        if (s.danger != null) {
            perceiveDanger(e, r, s.danger, now);
        } else if (s.wildlife != null && rnd.nextInt(30) == 0) {
            m.memories.remember(Memory.Type.WILDLIFE_SEEN, "s:" + s.wildlife.species().id, "", now, memLimit(), 0.5 + m.t(Trait.CURIOSITY) * 0.3);
            m.satisfy(Need.CURIOSITY, 0.05);
        }
        Brain.Context c = context(rec, r, pl, now, world.getTimeOfDay());
        c.danger = s.danger != null;
        c.attacked = e.getAttacker() != null && e.age - e.getLastAttackedTime() < 200;
        c.guardNearby = s.guardNear;
        c.othersNear = s.othersNear > 0;
        Activity before = Activity.byOrdinal(m.activity);
        Brain.Choice ch = Brain.decide(m, c, rnd);
        Brain.begin(m, ch, now);
        Activity a = Activity.byOrdinal(m.activity);
        boolean changed = a != before;
        if (changed || e.goalPos == null || a.place == Activity.Place.PATROL && now % 200 < 20) {
            BlockPos p = pl.resolve(rec, r, a, m.activityTarget, now);
            e.goalPos = p != null ? p : a.place == Activity.Place.NEAR ? e.getBlockPos() : e.goalPos;
        }
        e.goalActivity = a;
        e.danger = s.danger;
        e.defending = a == Activity.DEFEND;
        if (e.defending && s.danger != null && e.getTarget() == null) {
            e.setTarget(s.danger);
        }
        if (changed && a == Activity.WARN_OTHERS && s.danger != null) {
            say(e, warning(s.danger), 0);
            for (InhabitantEntity o : world.getEntitiesByClass(InhabitantEntity.class, e.getBoundingBox().expand(16), o -> o != e)) {
                SettlementRecord.InhabitantRecord or = InhabitantManager.record(o);
                if (or != null && or.mind != null) {
                    or.mind.learn(Mind.InfoType.PREDATOR_SEEN, subjectOf(s.danger), s.danger.getName().getString(), now, Mind.Source.TOLD, 85, knowLimit());
                    or.mind.feel(Emotion.FEAR, 0.15);
                }
            }
        }
        Goals.update(m, a, elapsed / 1000.0, now, rnd, memLimit());
        if (!e.isSleeping() && s.nearest != null && e.getCustomer() == null && !a.emergency()) {
            maybeConverse(e, rec, r, s.nearest, a, now);
        }
    }

    private static String subjectOf(LivingEntity d) {
        if (d instanceof UnderdeepCreature c) {
            return "s:" + c.species().id;
        }
        return "s:" + net.minecraft.registry.Registries.ENTITY_TYPE.getId(d.getType()).getPath();
    }

    private String warning(LivingEntity d) {
        String n = d.getName().getString().toLowerCase(java.util.Locale.ROOT);
        String[] lines = {"Watch out! A " + n + "!", "Everyone inside! There's a " + n + "!", "Careful, a " + n + " out there!"};
        return lines[rnd.nextInt(lines.length)];
    }

    private void perceiveDanger(InhabitantEntity e, SettlementRecord.InhabitantRecord r, LivingEntity d, long now) {
        Mind m = r.mind;
        double courage = m.trait(Trait.COURAGE) / 100.0;
        m.feel(Emotion.FEAR, 0.08 * (1.2 - courage));
        m.satisfy(Need.SAFETY, -0.15 * (1.2 - courage));
        if (m.learn(Mind.InfoType.PREDATOR_SEEN, subjectOf(d), d.getName().getString(), now, Mind.Source.WITNESSED, 100, knowLimit())) {
            m.memories.remember(Memory.Type.DANGER, subjectOf(d), "", now, memLimit(), 0.8 + m.t(Trait.CAUTION) * 0.3);
        }
        if (e.isGuard() && e.getTarget() == null && (d instanceof HostileEntity || d instanceof UnderdeepCreature c
                && c.species().temperament() == CreatureSpecies.Temperament.AMBUSH)) {
            e.setTarget(d);
        }
    }

    // ================================================================== conversations

    private static long pairKey(long settlement, int a, int b) {
        return settlement * 1_000_003L + Math.min(a, b) * 4099L + Math.max(a, b);
    }

    private void maybeConverse(InhabitantEntity e, SettlementRecord rec, SettlementRecord.InhabitantRecord r, InhabitantEntity o,
                               Activity a, long now) {
        UnderdeepConfig cfg = UnderdeepConfig.get();
        if (cfg.npcConversationFrequency <= 0 || now - r.mind.lastSocial < 300 || o.isSleeping() || e.talkUntil > e.age) {
            return;
        }
        SettlementRecord.InhabitantRecord or = InhabitantManager.record(o);
        if (or == null || or.mind == null || now - or.mind.lastSocial < 200) {
            return;
        }
        double chance = a.cat == Activity.Cat.SOCIAL || a == Activity.TAVERN_EVENING || a == Activity.PLAY_GAME || a == Activity.ATTEND_GATHERING
                ? 0.12 : a.cat == Activity.Cat.WORK ? 0.006 : 0.025;
        if (r.mind.activityTarget == or.index) {
            chance = 0.3;
        }
        chance *= cfg.npcConversationFrequency * (0.6 + r.mind.trait(Trait.SOCIABILITY) / 100.0);
        Long until = pairCooldown.get(pairKey(rec.id, r.index, or.index));
        if (until != null && until > now || rnd.nextDouble() >= chance || e.squaredDistanceTo(o) > 4.5 * 4.5) {
            return;
        }
        Encounters.Result res = Encounters.run(rec, r, or, now, rnd, memLimit());
        if (res == null) {
            return;
        }
        encounters++;
        pairCooldown.put(pairKey(rec.id, r.index, or.index), now + 1800 + rnd.nextInt(1200));
        afterEncounter(rec, r, or, res, now);
        e.talkTo(o, 70);
        o.talkTo(e, 70);
        say(e, res.lineA(), 0);
        say(o, res.lineB(), 45);
        if (pairCooldown.size() > 4096) {
            pairCooldown.values().removeIf(v -> v < now);
        }
    }

    // ================================================================== speech bubbles

    /** Shows a short line above an inhabitant's head after a delay (rate-limited by callers). */
    public void say(InhabitantEntity speaker, String text, int delayTicks) {
        if (!UnderdeepConfig.get().speechBubbles || text == null || text.isEmpty()) {
            return;
        }
        pending.add(new Pending(speaker, text, world.getTime() + delayTicks));
    }

    void tickBubbles() {
        long now = world.getTime();
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (p.at <= now) {
                it.remove();
                if (p.speaker.isAlive() && !p.speaker.isSleeping() && !p.speaker.isRemoved()) {
                    spawnBubble(p.speaker, p.text);
                }
            }
        }
        for (Iterator<Bubble> it = bubbles.iterator(); it.hasNext(); ) {
            Bubble b = it.next();
            Entity v = b.display.getVehicle();
            if (b.until <= now || v == null || !v.isAlive() || v instanceof InhabitantEntity ie && ie.isSleeping()) {
                b.display.discard();
                it.remove();
            }
        }
    }

    private void spawnBubble(InhabitantEntity e, String text) {
        // one bubble per speaker
        for (Iterator<Bubble> it = bubbles.iterator(); it.hasNext(); ) {
            Bubble b = it.next();
            if (b.display.getVehicle() == e) {
                b.display.discard();
                it.remove();
            }
        }
        DisplayEntity.TextDisplayEntity d = EntityType.TEXT_DISPLAY.create(world);
        if (d == null) {
            return;
        }
        NbtCompound n = new NbtCompound();
        n.putString("text", Text.Serialization.toJsonString(Text.literal(text), world.getRegistryManager()));
        n.putString("billboard", "center");
        n.putInt("line_width", 140);
        n.putInt("background", 0x80000000);
        n.putFloat("view_range", 0.3f);
        NbtCompound tr = new NbtCompound();
        tr.put("translation", floats(0f, 0.15f, 0f));
        tr.put("left_rotation", floats(0f, 0f, 0f, 1f));
        tr.put("scale", floats(0.55f, 0.55f, 0.55f));
        tr.put("right_rotation", floats(0f, 0f, 0f, 1f));
        n.put("transformation", tr);
        d.readNbt(n);
        d.addCommandTag(BUBBLE_TAG);
        d.refreshPositionAndAngles(e.getX(), e.getY() + e.getHeight(), e.getZ(), 0, 0);
        InhabitantManager.spawningBubble = true;
        boolean ok;
        try {
            ok = world.spawnEntity(d);
        } finally {
            InhabitantManager.spawningBubble = false;
        }
        if (ok) {
            d.startRiding(e, true);
            bubbles.add(new Bubble(d, world.getTime() + 20L * Math.max(1, UnderdeepConfig.get().speechBubbleSeconds)));
        }
    }

    private static NbtList floats(float... v) {
        NbtList l = new NbtList();
        for (float f : v) {
            l.add(NbtFloat.of(f));
        }
        return l;
    }

    void clearBubbles() {
        for (Bubble b : bubbles) {
            b.display.discard();
        }
        bubbles.clear();
        pending.clear();
    }

    // ================================================================== events

    /** An embodied inhabitant was hurt: victim and witnesses form memories, opinions and knowledge. */
    void attacked(InhabitantEntity victim, LivingEntity attacker) {
        SettlementRecord rec = SettlementState.get(world).get(victim.settlementId);
        SettlementRecord.InhabitantRecord r = InhabitantManager.record(victim);
        if (rec == null || r == null || r.mind == null) {
            return;
        }
        long now = world.getTime();
        Mind m = r.mind;
        m.health = Math.max(0.05f, victim.getHealth() / victim.getMaxHealth());
        m.feel(Emotion.FEAR, 0.35 * (1.2 - m.trait(Trait.COURAGE) / 100.0));
        if (attacker instanceof PlayerEntity p) {
            if (p.isCreative() || p.isSpectator()) {
                return;
            }
            UUID u = p.getUuid();
            m.feel(Emotion.ANGER, 0.3 + m.t(Trait.PRIDE) * 0.1);
            m.bond(u).adjust(now, 5, -22, -18, 0, 25, 18, false);
            m.memories.remember(Memory.Type.ATTACKED_BY, "p:" + u, "", now, memLimit(), 1.2);
            m.learn(Mind.InfoType.PLAYER_ATTACKED, "p:" + u, p.getName().getString(), now, Mind.Source.WITNESSED, 100, knowLimit());
            say(victim, pick("Hey! Stop that!", "Ow! What are you doing?", "Help! Guards!"), 0);
            for (InhabitantEntity w : world.getEntitiesByClass(InhabitantEntity.class, victim.getBoundingBox().expand(16), w -> w != victim)) {
                SettlementRecord.InhabitantRecord wr = InhabitantManager.record(w);
                if (wr == null || wr.mind == null || w.isSleeping() || !w.canSee(p)) {
                    continue; // only those who actually saw it
                }
                wr.mind.bond(u).adjust(now, 3, -12, -8, 0, 10, 10, false);
                wr.mind.memories.remember(Memory.Type.WITNESSED_ATTACK, "p:" + u, "i:" + r.index, now, memLimit(), 1);
                wr.mind.learn(Mind.InfoType.PLAYER_ATTACKED, "p:" + u, p.getName().getString(), now, Mind.Source.WITNESSED, 90, knowLimit());
                wr.mind.feel(Emotion.FEAR, 0.2);
                wr.mind.feel(Emotion.ANGER, 0.15);
            }
        } else {
            m.memories.remember(Memory.Type.DANGER, subjectOf(attacker), "", now, memLimit(), 1);
            m.learn(Mind.InfoType.PREDATOR_SEEN, subjectOf(attacker), attacker.getName().getString(), now, Mind.Source.WITNESSED, 100, knowLimit());
        }
        if (m.health < 0.6f) {
            m.learn(Mind.InfoType.PERSON_INJURED, "i:" + r.index, "", now, Mind.Source.WITNESSED, 100, knowLimit());
        }
        SettlementState.get(world).markDirty();
    }

    /** An inhabitant died: witnesses learn of it; the rest learn through households and news. */
    void died(SettlementRecord rec, InhabitantEntity e, SettlementRecord.InhabitantRecord r, @Nullable Entity attacker) {
        long now = world.getTime();
        places(rec);
        Places pl = places.get(rec.id);
        if (pl != null) {
            pl.release(r.index);
        }
        for (InhabitantEntity w : world.getEntitiesByClass(InhabitantEntity.class, e.getBoundingBox().expand(24), w -> w != e)) {
            SettlementRecord.InhabitantRecord wr = InhabitantManager.record(w);
            if (wr == null || wr.mind == null || w.isSleeping() || !w.canSee(e)) {
                continue;
            }
            if (wr.mind.learn(Mind.InfoType.PERSON_LOST, "i:" + r.index, r.name, now, Mind.Source.WITNESSED, 100, knowLimit())) {
                react(rec, wr, wr.mind.knowledge.get(wr.mind.knowledge.size() - 1), now);
            }
            if (attacker instanceof PlayerEntity p && !p.isCreative()) {
                wr.mind.bond(p.getUuid()).adjust(now, 5, -40, -35, 0, 45, 30, false);
                wr.mind.learn(Mind.InfoType.PLAYER_ATTACKED, "p:" + p.getUuid(), p.getName().getString(), now, Mind.Source.WITNESSED, 100, knowLimit());
            }
        }
    }

    private String pick(String... s) {
        return s[rnd.nextInt(s.length)];
    }

    /** Starting belongings: a little food and something of their trade (moved, never duplicated, afterwards). */
    static void startingPossessions(Mind m, String profession, long seed) {
        if (!m.possessions.isEmpty()) {
            return;
        }
        Random r = new Random(seed ^ 0xF00DL);
        m.possessions.add(new ItemStack(switch (profession) {
            case "fisher" -> Items.COOKED_COD;
            case "farmer", "baker" -> Items.BREAD;
            case "rancher", "hunter" -> Items.COOKED_MUTTON;
            default -> r.nextBoolean() ? Items.BREAD : Items.BAKED_POTATO;
        }, 1 + r.nextInt(3)));
        if (r.nextInt(3) == 0) {
            m.possessions.add(new ItemStack(r.nextBoolean() ? Items.APPLE : Items.COOKIE, 1 + r.nextInt(2)));
        }
    }
}
