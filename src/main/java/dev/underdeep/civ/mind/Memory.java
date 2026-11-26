package dev.underdeep.civ.mind;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One structured memory. Repeated similar experiences consolidate into one record (count grows,
 * importance rises slowly) instead of filling storage. {@link Book} keeps a bounded, prioritized set.
 */
public final class Memory {
    /** Memory categories (ordinals persisted, append-only). Base importance 0–1 and emotional valence −1..1. */
    public enum Type {
        FIRST_MEETING(0.35, 0.2), GOOD_TALK(0.15, 0.4), BAD_TALK(0.3, -0.5), GIFT_RECEIVED(0.45, 0.8), GIFT_GIVEN(0.3, 0.5),
        HELPED_BY(0.6, 0.8), HELPED_SOMEONE(0.4, 0.6), ATTACKED_BY(0.85, -1), WITNESSED_ATTACK(0.6, -0.7), RESCUED(0.8, 0.9),
        LOST_FRIEND(0.9, -1), LOST_FAMILY(1.0, -1), BECAME_FRIENDS(0.5, 0.7), ARGUMENT(0.45, -0.6), RECONCILED(0.5, 0.6),
        WORKED_TOGETHER(0.15, 0.4), TRUST_BROKEN(0.75, -0.9), DISCOVERY(0.55, 0.7), NEW_PLACE(0.35, 0.5), CHANGED_JOB(0.5, 0.2),
        MOVED_HOME(0.45, 0.1), GOAL_DONE(0.45, 0.8), DANGER(0.6, -0.8), JOINED_COMPANY(0.55, 0.6), TRADED(0.08, 0.3),
        SHARED_MEAL(0.15, 0.5), DEFENDED_BY(0.65, 0.8), WILDLIFE_SEEN(0.2, 0.4);

        public final double importance, valence;

        Type(double importance, double valence) {
            this.importance = importance;
            this.valence = valence;
        }
    }

    public Type type;
    /** Who: "p:<uuid>" for players, "i:<index>" for inhabitants of the same settlement, "s:<species>", or "". */
    public String who;
    /** Free detail (species id, place name, item id, region id) — only real facts. */
    public String detail;
    public long time;
    public long lastRecalled;
    public int count;
    public double importance;

    public static Memory of(Type type, String who, String detail, long time) {
        Memory m = new Memory();
        m.type = type;
        m.who = who == null ? "" : who;
        m.detail = detail == null ? "" : detail;
        m.time = time;
        m.lastRecalled = time;
        m.count = 1;
        m.importance = type.importance;
        return m;
    }

    /** Retention score: importance, repetition, and how recent the memory is (in in-game days). */
    double weight(long now) {
        double ageDays = Math.max(0, (now - Math.max(time, lastRecalled)) / 24000.0);
        double fade = importance >= 0.7 ? 1.0 / (1 + ageDays * 0.03) : 1.0 / (1 + ageDays * 0.35);
        return (importance + Math.min(0.3, Math.log(count) * 0.08)) * fade;
    }

    NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("t", type.ordinal());
        n.putString("w", who);
        n.putString("d", detail);
        n.putLong("at", time);
        n.putLong("r", lastRecalled);
        n.putInt("c", count);
        n.putDouble("i", importance);
        return n;
    }

    static Memory fromNbt(NbtCompound n) {
        Memory m = new Memory();
        m.type = Type.values()[Math.min(n.getInt("t"), Type.values().length - 1)];
        m.who = n.getString("w");
        m.detail = n.getString("d");
        m.time = n.getLong("at");
        m.lastRecalled = n.getLong("r");
        m.count = Math.max(1, n.getInt("c"));
        m.importance = n.getDouble("i");
        return m;
    }

    /** A bounded memory store with consolidation and importance-based forgetting. */
    public static final class Book {
        public final List<Memory> list = new ArrayList<>();

        /**
         * Records an experience. Similar memories (same type, who and detail) within a few days consolidate.
         * Returns the stored memory (new or consolidated).
         */
        public Memory remember(Type type, String who, String detail, long now, int limit, double personalWeight) {
            for (Memory m : list) {
                if (m.type == type && m.who.equals(who == null ? "" : who) && m.detail.equals(detail == null ? "" : detail)
                        && now - m.time < 24000L * 4) {
                    m.count++;
                    m.lastRecalled = now;
                    m.importance = Math.min(1.0, m.importance + type.importance * 0.08);
                    return m;
                }
            }
            Memory m = of(type, who, detail, now);
            m.importance = Math.max(0.02, Math.min(1.0, type.importance * personalWeight));
            list.add(m);
            if (list.size() > limit) {
                // forget the least weighty memory (never the one just formed)
                Memory worst = null;
                for (Memory o : list) {
                    if (o != m && (worst == null || o.weight(now) < worst.weight(now))) {
                        worst = o;
                    }
                }
                list.remove(worst);
            }
            return m;
        }

        /** Most important memory matching a filter, marking it recalled. */
        @Nullable
        public Memory recall(Type type, @Nullable String who, long now) {
            Memory best = null;
            for (Memory m : list) {
                if (m.type == type && (who == null || m.who.equals(who)) && (best == null || m.weight(now) > best.weight(now))) {
                    best = m;
                }
            }
            if (best != null) {
                best.lastRecalled = now;
            }
            return best;
        }

        public boolean has(Type type, @Nullable String who) {
            for (Memory m : list) {
                if (m.type == type && (who == null || m.who.equals(who))) {
                    return true;
                }
            }
            return false;
        }

        /** Drops faded minor memories (called during daily upkeep). */
        public void fade(long now) {
            list.removeIf(m -> m.importance < 0.25 && now - m.lastRecalled > 24000L * 6);
        }

        NbtList toNbt() {
            NbtList l = new NbtList();
            for (Memory m : list) {
                l.add(m.toNbt());
            }
            return l;
        }

        void fromNbt(NbtList l) {
            list.clear();
            for (NbtElement e : l) {
                list.add(Memory.fromNbt((NbtCompound) e));
            }
        }
    }
}
