package dev.underdeep.civ.mind;

import dev.underdeep.civ.Civilization;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Everything that makes an inhabitant an individual (Stage 5): personality, emotions, needs, memories,
 * relationships (with inhabitants of the same settlement and with players), knowledge, goals, preferences,
 * open requests for help, possessions, health, household and companion affiliation. Stored inside the
 * inhabitant's settlement record (one authoritative copy, NBT key {@code mind}, schema {@link #SCHEMA}).
 */
public final class Mind {
    public static final int SCHEMA = 1;

    public enum Age { YOUNG, ADULT, ELDER }

    public enum Status { ACTIVE, AWAY, RECOVERING }

    /** Kinds of knowledge (ordinals persisted). */
    public enum InfoType { PREDATOR_SEEN, PERSON_INJURED, PERSON_LOST, PLAYER_HELPED, PLAYER_ATTACKED, DISCOVERY, GIFT_FROM_PLAYER }

    /** Where knowledge came from. */
    public enum Source { WITNESSED, TOLD, CULTURE }

    public static final class Info {
        public InfoType type;
        /** Subject key: "p:uuid", "i:index", "s:species" or a region/place id. */
        public String subject;
        public String detail;
        public long time;
        public Source source;
        /** 0–100; decreases each time the information is retold. */
        public int confidence;
        public int retold;

        NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            n.putInt("t", type.ordinal());
            n.putString("s", subject);
            n.putString("d", detail);
            n.putLong("at", time);
            n.putInt("src", source.ordinal());
            n.putInt("c", confidence);
            n.putInt("r", retold);
            return n;
        }

        static Info fromNbt(NbtCompound n) {
            Info i = new Info();
            i.type = InfoType.values()[Math.min(n.getInt("t"), InfoType.values().length - 1)];
            i.subject = n.getString("s");
            i.detail = n.getString("d");
            i.time = n.getLong("at");
            i.source = Source.values()[Math.min(n.getInt("src"), Source.values().length - 1)];
            i.confidence = n.getInt("c");
            i.retold = n.getInt("r");
            return i;
        }
    }

    /**
     * Personal goals (ordinals persisted). {@code implemented} goals can be completed in Stage 5; the others are
     * held as Stage 6 extension points (economy, construction) and are never generated yet.
     */
    public enum GoalType {
        EAT_FAVORITE(true), VISIT_FRIEND(true), IMPROVE_SKILL(true), OBTAIN_ITEM(true), SPEND_TIME_FAMILY(true), EXPLORE_NEARBY(true),
        RECOVER(true), RECONCILE(true), LEARN_NEWS(true), MAKE_FRIEND(true), SAVE_UP(false), IMPROVE_HOME(false), FIND_SAFER_WORK(false);

        public final boolean implemented;

        GoalType(boolean implemented) {
            this.implemented = implemented;
        }
    }

    public static final class Goal {
        public GoalType type;
        public String target = "";
        public double progress;
        public long created, expires;

        NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            n.putInt("t", type.ordinal());
            n.putString("g", target);
            n.putDouble("p", progress);
            n.putLong("c", created);
            n.putLong("e", expires);
            return n;
        }

        static Goal fromNbt(NbtCompound n) {
            Goal g = new Goal();
            g.type = GoalType.values()[Math.min(n.getInt("t"), GoalType.values().length - 1)];
            g.target = n.getString("g");
            g.progress = n.getDouble("p");
            g.created = n.getLong("c");
            g.expires = n.getLong("e");
            return g;
        }
    }

    /** An open request for help that arose from the inhabitant's real situation. */
    public static final class Request {
        public enum Kind { FOOD, MEDICINE, MATERIAL }

        public Kind kind;
        public String item = "";
        public int count;
        public long created, expires;
        @Nullable public UUID helper;

        NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            n.putInt("k", kind.ordinal());
            n.putString("i", item);
            n.putInt("c", count);
            n.putLong("cr", created);
            n.putLong("e", expires);
            if (helper != null) {
                n.putUuid("h", helper);
            }
            return n;
        }

        static Request fromNbt(NbtCompound n) {
            Request r = new Request();
            r.kind = Kind.values()[Math.min(n.getInt("k"), Kind.values().length - 1)];
            r.item = n.getString("i");
            r.count = n.getInt("c");
            r.created = n.getLong("cr");
            r.expires = n.getLong("e");
            r.helper = n.containsUuid("h") ? n.getUuid("h") : null;
            return r;
        }
    }

    // ------------------------------------------------------------------ state

    public final byte[] traits = new byte[Trait.COUNT];
    public final float[] emotions = new float[Feelings.Emotion.COUNT];
    public final float[] needs = new float[Feelings.Need.COUNT];
    public final Memory.Book memories = new Memory.Book();
    /** Relationships with inhabitants of the same settlement, keyed by record index. */
    public final Map<Integer, Bond> people = new HashMap<>();
    /** Relationships with players, keyed by UUID (individual reputation). */
    public final Map<UUID, Bond> players = new HashMap<>();
    public final List<Info> knowledge = new ArrayList<>();
    public final List<Goal> goals = new ArrayList<>();
    /** Liked item ids (gift preferences), one disliked item id, a favourite place kind and topic. */
    public final List<String> likes = new ArrayList<>();
    public String dislike = "";
    public String favoritePlace = "";
    public String favoriteTopic = "";
    /** Which preferences each player has learned (player → bitmask: 1 likes[0], 2 likes[1], 4 likes[2], 8 dislike, 16 place). */
    public final Map<UUID, Integer> revealed = new HashMap<>();
    @Nullable public Request request;
    public final List<ItemStack> possessions = new ArrayList<>();
    public Age age = Age.ADULT;
    public Status status = Status.ACTIVE;
    public float health = 1.0f;
    public long recoverUntil;
    public int household = -1;
    public String origin = "";
    public int skill = 40;
    /** Current activity (ordinal into the activity library) and when it began; persisted for tier continuity. */
    public int activity = -1;
    public long activitySince;
    public long lastUpdate = -1;
    public long lastSocial;
    /** Companion affiliation. */
    @Nullable public UUID companionOf;
    public int companionMode;
    public final List<ItemStack> companionPack = new ArrayList<>();
    /** Development diagnostics: why the current activity was chosen (not persisted). */
    public transient String reason = "";

    public int trait(Trait t) {
        return traits[t.ordinal()];
    }

    /** Trait as −1..1 around the average. */
    public double t(Trait t) {
        return (traits[t.ordinal()] - 50) / 50.0;
    }

    public float emotion(Feelings.Emotion e) {
        return emotions[e.ordinal()];
    }

    public void feel(Feelings.Emotion e, double amount) {
        int i = e.ordinal();
        emotions[i] = (float) Math.max(0, Math.min(1, emotions[i] + amount));
    }

    public float need(Feelings.Need n) {
        return needs[n.ordinal()];
    }

    public void satisfy(Feelings.Need n, double amount) {
        int i = n.ordinal();
        needs[i] = (float) Math.max(0, Math.min(1, needs[i] + amount));
    }

    /** The strongest current emotion (contentment if nothing stands out). */
    public Feelings.Emotion mood() {
        Feelings.Emotion best = Feelings.Emotion.CONTENTMENT;
        float v = 0.25f;
        for (Feelings.Emotion e : Feelings.Emotion.values()) {
            if (e != Feelings.Emotion.CONTENTMENT && emotions[e.ordinal()] > v) {
                v = emotions[e.ordinal()];
                best = e;
            }
        }
        return best;
    }

    public Bond bond(int index) {
        return people.computeIfAbsent(index, k -> new Bond());
    }

    public Bond bond(UUID player) {
        return players.computeIfAbsent(player, k -> {
            Bond b = new Bond();
            b.trust = (int) Math.round(t(Trait.TRUSTFULNESS) * 15);
            return b;
        });
    }

    @Nullable
    public Bond bondIfAny(UUID player) {
        return players.get(player);
    }

    /** Adds or refreshes knowledge (bounded); returns true if it was new. */
    public boolean learn(InfoType type, String subject, String detail, long now, Source source, int confidence, int limit) {
        for (Info i : knowledge) {
            if (i.type == type && i.subject.equals(subject)) {
                if (confidence > i.confidence || now - i.time > 12000) {
                    i.time = now;
                    i.confidence = Math.max(i.confidence, confidence);
                }
                return false;
            }
        }
        Info i = new Info();
        i.type = type;
        i.subject = subject;
        i.detail = detail == null ? "" : detail;
        i.time = now;
        i.source = source;
        i.confidence = confidence;
        knowledge.add(i);
        if (knowledge.size() > limit) {
            knowledge.sort((a, b) -> Long.compare(a.time, b.time));
            knowledge.remove(0);
        }
        return true;
    }

    @Nullable
    public Goal goal(GoalType type) {
        for (Goal g : goals) {
            if (g.type == type) {
                return g;
            }
        }
        return null;
    }

    public void addGoal(GoalType type, String target, long now, long duration) {
        if (!type.implemented || goal(type) != null || goals.size() >= 3) {
            return;
        }
        Goal g = new Goal();
        g.type = type;
        g.target = target == null ? "" : target;
        g.created = now;
        g.expires = now + duration;
        goals.add(g);
    }

    // ------------------------------------------------------------------ generation

    private static final Map<Civilization, int[][]> CIV_BIAS = Map.of(
            Civilization.ROOTFOLK, new int[][]{{Trait.COMPASSION.ordinal(), 8}, {Trait.SOCIABILITY.ordinal(), 6}, {Trait.PATIENCE.ordinal(), 5}, {Trait.HOMEBODY.ordinal(), 5}},
            Civilization.FORGEKIN, new int[][]{{Trait.DISCIPLINE.ordinal(), 9}, {Trait.PRIDE.ordinal(), 7}, {Trait.HUMOR.ordinal(), 4}, {Trait.TRUSTFULNESS.ordinal(), -6}},
            Civilization.TIDE_DWELLERS, new int[][]{{Trait.ADVENTUROUSNESS.ordinal(), 8}, {Trait.SOCIABILITY.ordinal(), 6}, {Trait.OPTIMISM.ordinal(), 6}},
            Civilization.SPOREFOLK, new int[][]{{Trait.CURIOSITY.ordinal(), 7}, {Trait.TOLERANCE.ordinal(), 9}, {Trait.IMPULSIVENESS.ordinal(), 4}, {Trait.HUMOR.ordinal(), 4}},
            Civilization.GLIMMERKIN, new int[][]{{Trait.PATIENCE.ordinal(), 7}, {Trait.PRIDE.ordinal(), 6}, {Trait.DISCIPLINE.ordinal(), 6}, {Trait.HONESTY.ordinal(), 4}},
            Civilization.STONEBORN, new int[][]{{Trait.LOYALTY.ordinal(), 9}, {Trait.CAUTION.ordinal(), 6}, {Trait.TRUSTFULNESS.ordinal(), -8}, {Trait.RESILIENCE.ordinal(), 7}},
            Civilization.AMBERFOLK, new int[][]{{Trait.GENEROSITY.ordinal(), 7}, {Trait.SOCIABILITY.ordinal(), 9}, {Trait.AMBITION.ordinal(), 5}, {Trait.HUMOR.ordinal(), 4}},
            Civilization.ASTRALS, new int[][]{{Trait.CURIOSITY.ordinal(), 10}, {Trait.INDEPENDENCE.ordinal(), 7}, {Trait.SOCIABILITY.ordinal(), -6}, {Trait.HONESTY.ordinal(), 5}});

    private static int[][] professionBias(String p) {
        return switch (p) {
            case "guard" -> new int[][]{{Trait.COURAGE.ordinal(), 16}, {Trait.DISCIPLINE.ordinal(), 9}, {Trait.LOYALTY.ordinal(), 6}};
            case "merchant", "innkeeper" -> new int[][]{{Trait.SOCIABILITY.ordinal(), 10}, {Trait.AMBITION.ordinal(), 6}, {Trait.HUMOR.ordinal(), 4}};
            case "scholar", "astronomer", "cartographer" -> new int[][]{{Trait.CURIOSITY.ordinal(), 13}, {Trait.PATIENCE.ordinal(), 6}};
            case "hunter", "navigator", "fisher" -> new int[][]{{Trait.ADVENTUROUSNESS.ordinal(), 12}, {Trait.COURAGE.ordinal(), 8}, {Trait.INDEPENDENCE.ordinal(), 6}};
            case "healer", "herbalist" -> new int[][]{{Trait.COMPASSION.ordinal(), 13}, {Trait.PATIENCE.ordinal(), 6}};
            case "farmer", "rancher" -> new int[][]{{Trait.PATIENCE.ordinal(), 8}, {Trait.HOMEBODY.ordinal(), 7}};
            case "blacksmith", "smelter", "stonemason", "engineer" -> new int[][]{{Trait.DISCIPLINE.ordinal(), 6}, {Trait.PRIDE.ordinal(), 6}};
            case "miner" -> new int[][]{{Trait.COURAGE.ordinal(), 8}, {Trait.RESILIENCE.ordinal(), 6}};
            default -> new int[0][];
        };
    }

    /** Deterministic initial mind for an inhabitant (new records and migration of Stage 4 records). */
    public static Mind create(Civilization civ, String profession, long seed, long now) {
        Mind m = new Mind();
        Random r = new Random(seed ^ 0x5EED5EEDL);
        int[] v = new int[Trait.COUNT];
        for (int i = 0; i < Trait.COUNT; i++) {
            v[i] = 50 + (int) Math.round(r.nextGaussian() * 17);
        }
        for (int[] b : CIV_BIAS.getOrDefault(civ, new int[0][])) {
            v[b[0]] += b[1];
        }
        for (int[] b : professionBias(profession)) {
            v[b[0]] += b[1];
        }
        for (int i = 0; i < Trait.COUNT; i++) {
            m.traits[i] = (byte) Math.max(2, Math.min(98, v[i]));
        }
        for (Feelings.Need n : Feelings.Need.values()) {
            m.needs[n.ordinal()] = 0.65f + r.nextFloat() * 0.35f;
        }
        m.needs[Feelings.Need.SAFETY.ordinal()] = 1f;
        m.needs[Feelings.Need.HEALTH.ordinal()] = 1f;
        m.emotions[Feelings.Emotion.CONTENTMENT.ordinal()] = (float) (0.4 + 0.2 * m.t(Trait.OPTIMISM));
        m.skill = 30 + r.nextInt(50);
        double ageRoll = r.nextDouble();
        m.age = ageRoll < 0.18 ? Age.YOUNG : ageRoll > 0.85 ? Age.ELDER : Age.ADULT;
        Preferences.assign(m, civ, profession, r);
        m.lastUpdate = now;
        return m;
    }

    // ------------------------------------------------------------------ NBT

    public NbtCompound toNbt(RegistryWrapper.WrapperLookup lookup) {
        NbtCompound n = new NbtCompound();
        n.putInt("schema", SCHEMA);
        n.putByteArray("traits", traits);
        NbtCompound e = new NbtCompound();
        for (Feelings.Emotion em : Feelings.Emotion.values()) {
            if (emotions[em.ordinal()] > 0.01f) {
                e.putFloat(Integer.toString(em.ordinal()), emotions[em.ordinal()]);
            }
        }
        n.put("emotions", e);
        NbtCompound nd = new NbtCompound();
        for (Feelings.Need nn : Feelings.Need.values()) {
            nd.putFloat(Integer.toString(nn.ordinal()), needs[nn.ordinal()]);
        }
        n.put("needs", nd);
        n.put("memories", memories.toNbt());
        NbtCompound pp = new NbtCompound();
        people.forEach((k, b) -> pp.put(Integer.toString(k), b.toNbt()));
        n.put("people", pp);
        NbtCompound pl = new NbtCompound();
        players.forEach((k, b) -> pl.put(k.toString(), b.toNbt()));
        n.put("players", pl);
        NbtList kn = new NbtList();
        knowledge.forEach(i -> kn.add(i.toNbt()));
        n.put("knowledge", kn);
        NbtList gl = new NbtList();
        goals.forEach(g -> gl.add(g.toNbt()));
        n.put("goals", gl);
        NbtList lk = new NbtList();
        likes.forEach(s -> lk.add(net.minecraft.nbt.NbtString.of(s)));
        n.put("likes", lk);
        n.putString("dislike", dislike);
        n.putString("place", favoritePlace);
        n.putString("topic", favoriteTopic);
        NbtCompound rv = new NbtCompound();
        revealed.forEach((k, v) -> rv.putInt(k.toString(), v));
        n.put("revealed", rv);
        if (request != null) {
            n.put("request", request.toNbt());
        }
        n.put("possessions", stacks(possessions, lookup));
        n.putInt("age", age.ordinal());
        n.putInt("status", status.ordinal());
        n.putFloat("health", health);
        n.putLong("recover", recoverUntil);
        n.putInt("household", household);
        n.putString("origin", origin);
        n.putInt("skill", skill);
        n.putInt("activity", activity);
        n.putLong("activitySince", activitySince);
        n.putLong("updated", lastUpdate);
        n.putLong("social", lastSocial);
        if (companionOf != null) {
            n.putUuid("companionOf", companionOf);
        }
        n.putInt("companionMode", companionMode);
        n.put("pack", stacks(companionPack, lookup));
        return n;
    }

    private static NbtList stacks(List<ItemStack> list, RegistryWrapper.WrapperLookup lookup) {
        NbtList l = new NbtList();
        for (ItemStack s : list) {
            if (!s.isEmpty()) {
                l.add(s.encode(lookup));
            }
        }
        return l;
    }

    private static void readStacks(NbtList l, List<ItemStack> out, RegistryWrapper.WrapperLookup lookup) {
        out.clear();
        for (NbtElement e : l) {
            ItemStack.fromNbt(lookup, e).ifPresent(out::add);
        }
    }

    public static Mind fromNbt(NbtCompound n, RegistryWrapper.WrapperLookup lookup) {
        Mind m = new Mind();
        byte[] t = n.getByteArray("traits");
        System.arraycopy(t, 0, m.traits, 0, Math.min(t.length, m.traits.length));
        for (int i = t.length; i < m.traits.length; i++) {
            m.traits[i] = 50; // traits added later default to average
        }
        NbtCompound e = n.getCompound("emotions");
        for (String k : e.getKeys()) {
            int i = Integer.parseInt(k);
            if (i < m.emotions.length) {
                m.emotions[i] = e.getFloat(k);
            }
        }
        NbtCompound nd = n.getCompound("needs");
        for (int i = 0; i < m.needs.length; i++) {
            m.needs[i] = nd.contains(Integer.toString(i)) ? nd.getFloat(Integer.toString(i)) : 0.8f;
        }
        m.memories.fromNbt(n.getList("memories", NbtElement.COMPOUND_TYPE));
        NbtCompound pp = n.getCompound("people");
        for (String k : pp.getKeys()) {
            m.people.put(Integer.parseInt(k), Bond.fromNbt(pp.getCompound(k)));
        }
        NbtCompound pl = n.getCompound("players");
        for (String k : pl.getKeys()) {
            try {
                m.players.put(UUID.fromString(k), Bond.fromNbt(pl.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
                // malformed key
            }
        }
        for (NbtElement el : n.getList("knowledge", NbtElement.COMPOUND_TYPE)) {
            m.knowledge.add(Info.fromNbt((NbtCompound) el));
        }
        for (NbtElement el : n.getList("goals", NbtElement.COMPOUND_TYPE)) {
            m.goals.add(Goal.fromNbt((NbtCompound) el));
        }
        for (NbtElement el : n.getList("likes", NbtElement.STRING_TYPE)) {
            m.likes.add(el.asString());
        }
        m.dislike = n.getString("dislike");
        m.favoritePlace = n.getString("place");
        m.favoriteTopic = n.getString("topic");
        NbtCompound rv = n.getCompound("revealed");
        for (String k : rv.getKeys()) {
            try {
                m.revealed.put(UUID.fromString(k), rv.getInt(k));
            } catch (IllegalArgumentException ignored) {
                // malformed key
            }
        }
        if (n.contains("request")) {
            m.request = Request.fromNbt(n.getCompound("request"));
        }
        readStacks(n.getList("possessions", NbtElement.COMPOUND_TYPE), m.possessions, lookup);
        m.age = Age.values()[Math.min(n.getInt("age"), Age.values().length - 1)];
        m.status = Status.values()[Math.min(n.getInt("status"), Status.values().length - 1)];
        m.health = n.contains("health") ? n.getFloat("health") : 1f;
        m.recoverUntil = n.getLong("recover");
        m.household = n.contains("household") ? n.getInt("household") : -1;
        m.origin = n.getString("origin");
        m.skill = n.getInt("skill");
        m.activity = n.contains("activity") ? n.getInt("activity") : -1;
        m.activitySince = n.getLong("activitySince");
        m.lastUpdate = n.getLong("updated");
        m.lastSocial = n.getLong("social");
        m.companionOf = n.containsUuid("companionOf") ? n.getUuid("companionOf") : null;
        m.companionMode = n.getInt("companionMode");
        readStacks(n.getList("pack", NbtElement.COMPOUND_TYPE), m.companionPack, lookup);
        return m;
    }

    /** Position helper for knowledge details. */
    public static String pos(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }
}
