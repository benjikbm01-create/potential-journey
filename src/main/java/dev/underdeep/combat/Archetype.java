package dev.underdeep.combat;

import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.UnderdeepCreature;

/**
 * Stage 10 enemy behaviour archetypes for creatures that fight. Each archetype is a parameter set for the one
 * reusable {@link CombatGoal}: how the creature approaches, how long its attack is telegraphed, how far it reaches
 * and lunges, how long it recovers (the punish window), whether it retreats or circles, and whether its big attack
 * can be parried. Assignment comes from the species' existing ecology data, so behaviour matches the animal.
 *
 * @param windup     telegraph ticks before the strike (doubled for the heavy variant)
 * @param recovery   ticks after a strike during which it does not attack (punish window)
 * @param reach      strike reach beyond its own body
 * @param lunge      forward impulse at the strike
 * @param retreat    ticks it backs off after a hit (hit-and-run), 0 = stays
 * @param circle     ticks it circles before committing (stalkers, flankers), 0 = none
 * @param heavyEvery every n-th attack is a heavy one (bigger telegraph, more damage, cannot be parried), 0 = never
 * @param ranged     preferred distance for a ranged attack (0 = melee only)
 */
public enum Archetype {
    BRAWLER("brawler", 9, 14, 1.0, 0.15, 0, 0, 4, 0),
    SKIRMISHER("skirmisher", 6, 10, 1.0, 0.45, 30, 0, 0, 0),
    STALKER("stalker", 11, 16, 1.3, 0.85, 0, 50, 3, 0),
    AMBUSHER("ambusher", 4, 18, 1.1, 0.6, 0, 0, 3, 0),
    PACK_FLANKER("pack flanker", 7, 12, 1.0, 0.4, 16, 25, 0, 0),
    PACK_LEADER("pack leader", 10, 14, 1.2, 0.35, 0, 0, 3, 0),
    CHARGER("charger", 22, 26, 1.0, 0, 0, 0, 1, 0),
    BRUISER("bruiser", 18, 30, 1.8, 0, 0, 0, 2, 0),
    WARNER("territorial", 12, 16, 1.0, 0.25, 0, 0, 0, 0),
    SPITTER("spitter", 14, 20, 1.0, 0, 20, 0, 0, 9),
    SWOOPER("swooper", 14, 22, 1.2, 0, 0, 30, 0, 0),
    AQUATIC("aquatic dasher", 8, 16, 1.1, 0.8, 20, 20, 0, 0),
    BURROWER("burrower", 16, 22, 1.3, 0, 0, 0, 2, 0),
    DEFENDER("defender", 12, 18, 1.1, 0.2, 0, 0, 3, 0),
    SWARM("swarm", 4, 8, 0.8, 0.3, 14, 0, 0, 0);

    public final String title;
    public final int windup, recovery, retreat, circle, heavyEvery;
    public final double reach, lunge, ranged;

    Archetype(String title, int windup, int recovery, double reach, double lunge, int retreat, int circle, int heavyEvery, double ranged) {
        this.title = title;
        this.windup = windup;
        this.recovery = recovery;
        this.reach = reach;
        this.lunge = lunge;
        this.retreat = retreat;
        this.circle = circle;
        this.heavyEvery = heavyEvery;
        this.ranged = ranged;
    }

    /** Archetype of a creature (species data plus a stable per-individual role for packs). */
    public static Archetype of(UnderdeepCreature c) {
        return of(c, c.species());
    }

    /** As {@link #of(UnderdeepCreature)}, with the species given (goals are installed before the entity is fully built). */
    public static Archetype of(UnderdeepCreature c, CreatureSpecies s) {
        if (s == null) {
            return BRAWLER;
        }
        if (s.social() == CreatureSpecies.Social.SWARM && s.sizeClass() <= 1) {
            return SWARM;
        }
        switch (s.huntStyle()) {
            case PACK -> {
                return (c.getUuid().getLeastSignificantBits() & 3) == 0 ? PACK_LEADER : PACK_FLANKER;
            }
            case PURSUIT -> {
                return SKIRMISHER;
            }
            case STALK -> {
                return STALKER;
            }
            case AMBUSH -> {
                return s.product().contains("venom") ? SPITTER : AMBUSHER;
            }
            case AQUATIC -> {
                return AQUATIC;
            }
            case SWOOP -> {
                return SWOOPER;
            }
            default -> {
            }
        }
        if (s.diet() == CreatureSpecies.Diet.LITHOVORE) {
            return BURROWER;
        }
        if (s.rarity().ordinal() >= dev.underdeep.ecology.Species.Rarity.RARE.ordinal() && s.attack() >= 6 || s.health() >= 60) {
            return BRUISER;
        }
        if (s.product().contains("shell") || s.armorValue() >= 6) {
            return DEFENDER;
        }
        if (s.temperament() == CreatureSpecies.Temperament.TERRITORIAL) {
            return s.width() >= 1.0f ? CHARGER : WARNER;
        }
        if (s.product().contains("venom")) {
            return SPITTER;
        }
        return BRAWLER;
    }
}
