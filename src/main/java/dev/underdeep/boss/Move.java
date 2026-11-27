package dev.underdeep.boss;

import net.minecraft.entity.LivingEntity;

/**
 * One boss attack or action: telegraph (wind-up), active ticks, recovery (the punish window), cooldown and when it
 * may be chosen. Behaviour is given as callbacks so each boss keeps its moves next to its brain.
 */
public final class Move {
    public interface Tick {
        void run(BossEntity b, LivingEntity target, int tick);
    }

    final String name;
    byte id;
    int windup = 12, active = 1, recovery = 16, cooldown = 40;
    double weight = 1, minRange = 0, maxRange = 6;
    int phases = ~0;
    boolean ranged;
    Tick onWindup, onStart, onActive, onEnd;
    boolean faceTarget = true;
    boolean lockFacing;

    public Move(String name) {
        this.name = name;
    }

    public static Move of(String name, int windup, int active, int recovery) {
        Move m = new Move(name);
        m.windup = windup;
        m.active = Math.max(1, active);
        m.recovery = recovery;
        return m;
    }

    public Move range(double min, double max) {
        minRange = min;
        maxRange = max;
        return this;
    }

    public Move cooldown(int t) {
        cooldown = t;
        return this;
    }

    public Move weight(double w) {
        weight = w;
        return this;
    }

    /** Bit mask of phases (bit 0 = first phase) this move is used in. */
    public Move phases(int mask) {
        phases = mask;
        return this;
    }

    /** A move that reaches targets the boss cannot walk to (anti-cheese: pillaring, ledges, water). */
    public Move ranged() {
        ranged = true;
        return this;
    }

    /** The boss stops turning when the move starts (charges, beams): it commits to a direction. */
    public Move lock() {
        lockFacing = true;
        return this;
    }

    public Move windupTick(Tick t) {
        onWindup = t;
        return this;
    }

    public Move start(Tick t) {
        onStart = t;
        return this;
    }

    public Move tick(Tick t) {
        onActive = t;
        return this;
    }

    public Move end(Tick t) {
        onEnd = t;
        return this;
    }

    public String name() {
        return name;
    }

    public byte id() {
        return id;
    }
}
