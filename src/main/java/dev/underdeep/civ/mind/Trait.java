package dev.underdeep.civ.mind;

/**
 * Personality dimensions (0–100, 50 = average). Ordinals are persisted (append-only). Every trait feeds at
 * least one decision; the effect is documented here and in docs/NPC_MINDS.md.
 */
public enum Trait {
    /** Utility of gatherings, visiting friends, starting conversations; loneliness grows faster when high. */
    SOCIABILITY,
    /** Flee threshold against predators/hostiles; guards and companions with high courage engage. */
    COURAGE,
    /** Tolerance for interruptions (repeated talking), frustration growth, minimum activity commitment. */
    PATIENCE,
    /** Gift giving to friends, sharing food in encounters, friendlier trade prices for trusted people. */
    GENEROSITY,
    /** Starting trust toward strangers and newcomers; how fast trust grows. */
    TRUSTFULNESS,
    /** Utility of extra work and skill goals; rivalry formation with competitors. */
    AMBITION,
    /** Utility of exploring, observing wildlife, asking about discoveries; interest in news. */
    CURIOSITY,
    /** Companion willingness to stay in danger; resilience of friendships to small slights. */
    LOYALTY,
    /** Emotional baseline (contentment) and recovery speed from sadness. */
    OPTIMISM,
    /** Whether rumours are passed on as rumours or embellished (lower confidence when low). */
    HONESTY,
    /** Willingness to travel as a companion, preference for solitary activities. */
    INDEPENDENCE,
    /** Punctuality: schedule jitter and work commitment. */
    DISCIPLINE,
    /** Joking dialogue variants, play/games in leisure. */
    HUMOR,
    /** Rivalry formation with people of the same profession; competitive games. */
    COMPETITIVENESS,
    /** Comforting sad or injured people, helping in encounters, concern for others. */
    COMPASSION,
    /** Avoiding places with remembered danger; warning others; refusing risky companion orders. */
    CAUTION,
    /** Utility of exploring nearby areas; acceptance of companion invitations. */
    ADVENTUROUSNESS,
    /** Lower commitment (switches activities sooner), blunt dialogue, quick arguments. */
    IMPULSIVENESS,
    /** Offence at insults and refused gifts; boasting dialogue; slower reconciliation. */
    PRIDE,
    /** Resentment decay speed; reconciliation chance. */
    FORGIVENESS,
    /** Emotional decay speed after fear, sadness and stress. */
    RESILIENCE,
    /** Saving goals; how much gifts of valuables are appreciated. */
    FRUGALITY,
    /** Attachment to home: time spent at home, refusal to leave as a companion. */
    HOMEBODY,
    /** Disagreement probability with incompatible personalities; acceptance of outsiders. */
    TOLERANCE;

    public static final int COUNT = values().length;
}
