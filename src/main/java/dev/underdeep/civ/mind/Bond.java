package dev.underdeep.civ.mind;

import net.minecraft.nbt.NbtCompound;

/**
 * A directed relationship (sparse: only created through real contact). Dimensions are bounded; the label
 * shown to players and used by decisions is derived from them plus social context (family, household,
 * work, mentorship, companionship).
 */
public final class Bond {
    /** Family kinds (ordinals persisted). */
    public enum Kin { NONE, PARTNER, SIBLING, PARENT, CHILD, COUSIN }

    /** Derived labels (most specific first). */
    public enum Label {
        STRANGER, ACQUAINTANCE, NEIGHBOR, COWORKER, FRIEND, CLOSE_FRIEND, BEST_FRIEND, FAMILY, HOUSEMATE, MENTOR, APPRENTICE,
        TRUSTED_ASSOCIATE, COMPANION, PROFESSIONAL_RIVAL, RIVAL, DISLIKED, DISTRUSTED, ENEMY
    }

    /** 0–100. */
    public int familiarity;
    /** −100..100. */
    public int trust, affection, respect;
    /** 0–100. */
    public int resentment, fear;
    public Kin kin = Kin.NONE;
    public boolean household, coworker, mentor, apprentice, companion, rivalry;
    public long lastContact;
    public long lastGift;
    /** Interactions counted today (diminishing returns), and the day they were counted. */
    public int today;
    public long day;

    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Applies a change scaled by diminishing returns for repeated trivial interactions on the same day.
     * Significant events (|delta| ≥ 10) are never diminished.
     */
    public void adjust(long now, int dFam, int dTrust, int dAff, int dResp, int dRes, int dFear, boolean trivial) {
        long d = now / 24000;
        if (d != day) {
            day = d;
            today = 0;
        }
        double scale = 1.0;
        if (trivial) {
            today++;
            scale = today <= 2 ? 1.0 : today <= 5 ? 0.35 : 0.0;
        }
        familiarity = clamp(familiarity + (int) Math.round(dFam * Math.max(scale, 0.25)), 0, 100);
        trust = clamp(trust + (int) Math.round(dTrust * scale), -100, 100);
        affection = clamp(affection + (int) Math.round(dAff * scale), -100, 100);
        respect = clamp(respect + (int) Math.round(dResp * scale), -100, 100);
        resentment = clamp(resentment + dRes, 0, 100);
        fear = clamp(fear + dFear, 0, 100);
        lastContact = now;
    }

    /** Warmth used by decisions: affection and trust minus resentment. */
    public double warmth() {
        return (affection * 0.6 + trust * 0.4 - resentment * 0.7) / 100.0;
    }

    public Label label() {
        if (companion) {
            return Label.COMPANION;
        }
        if (resentment >= 70 && affection < -40) {
            return Label.ENEMY;
        }
        if (kin != Kin.NONE) {
            return Label.FAMILY;
        }
        if (trust <= -40) {
            return Label.DISTRUSTED;
        }
        if (rivalry) {
            return coworker || respect > 20 ? Label.PROFESSIONAL_RIVAL : Label.RIVAL;
        }
        if (affection <= -30 || resentment >= 45) {
            return Label.DISLIKED;
        }
        if (mentor) {
            return Label.MENTOR;
        }
        if (apprentice) {
            return Label.APPRENTICE;
        }
        if (affection >= 70 && trust >= 60 && familiarity >= 70) {
            return Label.BEST_FRIEND;
        }
        if (affection >= 45 && trust >= 35 && familiarity >= 45) {
            return Label.CLOSE_FRIEND;
        }
        if (affection >= 22 && familiarity >= 25) {
            return Label.FRIEND;
        }
        if (trust >= 40 && respect >= 30) {
            return Label.TRUSTED_ASSOCIATE;
        }
        if (household) {
            return Label.HOUSEMATE;
        }
        if (coworker) {
            return Label.COWORKER;
        }
        if (familiarity >= 35) {
            return Label.NEIGHBOR;
        }
        if (familiarity >= 8) {
            return Label.ACQUAINTANCE;
        }
        return Label.STRANGER;
    }

    /** Player-facing familiarity word (no numbers). */
    public String playerWord() {
        Label l = label();
        return switch (l) {
            case STRANGER -> "unfamiliar";
            case ACQUAINTANCE, NEIGHBOR, COWORKER, HOUSEMATE -> "familiar";
            case FRIEND -> "friendly";
            case TRUSTED_ASSOCIATE, MENTOR, APPRENTICE -> "trusted";
            case CLOSE_FRIEND, BEST_FRIEND, COMPANION, FAMILY -> "close friend";
            case PROFESSIONAL_RIVAL, RIVAL -> "wary";
            case DISLIKED, DISTRUSTED -> "distrustful";
            case ENEMY -> "hostile";
        };
    }

    NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putByteArray("v", new byte[]{(byte) familiarity, (byte) trust, (byte) affection, (byte) respect, (byte) resentment, (byte) fear});
        n.putInt("k", kin.ordinal());
        int flags = (household ? 1 : 0) | (coworker ? 2 : 0) | (mentor ? 4 : 0) | (apprentice ? 8 : 0) | (companion ? 16 : 0) | (rivalry ? 32 : 0);
        n.putInt("f", flags);
        n.putLong("lc", lastContact);
        n.putLong("lg", lastGift);
        return n;
    }

    static Bond fromNbt(NbtCompound n) {
        Bond b = new Bond();
        byte[] v = n.getByteArray("v");
        if (v.length >= 6) {
            b.familiarity = v[0];
            b.trust = v[1];
            b.affection = v[2];
            b.respect = v[3];
            b.resentment = v[4];
            b.fear = v[5];
        }
        b.kin = Kin.values()[Math.min(n.getInt("k"), Kin.values().length - 1)];
        int f = n.getInt("f");
        b.household = (f & 1) != 0;
        b.coworker = (f & 2) != 0;
        b.mentor = (f & 4) != 0;
        b.apprentice = (f & 8) != 0;
        b.companion = (f & 16) != 0;
        b.rivalry = (f & 32) != 0;
        b.lastContact = n.getLong("lc");
        b.lastGift = n.getLong("lg");
        return b;
    }
}
