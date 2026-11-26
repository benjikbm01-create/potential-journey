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
    /** Gift giving to friends, sharing food in encounters, giving a thank-you item after being helped. */
    GENEROSITY,
    /** Starting trust toward players they have not met; how gently they turn down a companion invitation. */
    TRUSTFULNESS,
    /** Utility of extra work and skill goals; rivalry formation with competitors. */
    AMBITION,
    /** Utility of exploring, observing wildlife, asking about discoveries; interest in news. */
    CURIOSITY,
    /** Willingness to accept a companion invitation. */
    LOYALTY,
    /** Emotional baseline (contentment) and recovery speed from sadness. */
    OPTIMISM,
    /** Whether rumours are passed on as rumours or embellished (lower confidence when low). */
    HONESTY,
    /** Reluctance to ask for help (requests are phrased indirectly); content living alone. */
    INDEPENDENCE,
    /** Punctuality: schedule jitter and work commitment. */
    DISCIPLINE,
    /** Joking dialogue variants, play/games in leisure. */
    HUMOR,
    /** Rivalry formation with people of the same profession; competitive games. */
    COMPETITIVENESS,
    /** Comforting sad or injured people, helping in encounters, concern for others. */
    COMPASSION,
    /** Shelter preference in danger, loss of safety on hearing of predators, dislike of exploring, weight of danger memories. */
    CAUTION,
    /** Utility of exploring nearby areas; acceptance of companion invitations. */
    ADVENTUROUSNESS,
    /** Lower commitment (switches activities sooner), blunt dialogue, quick arguments. */
    IMPULSIVENESS,
    /** Offence at insults and refused gifts; boasting dialogue; slower reconciliation. */
    PRIDE,
    /** Reconciliation chance and the urge to make amends after an argument. */
    FORGIVENESS,
    /** Emotional decay speed after fear, sadness and stress. */
    RESILIENCE,
    /** Eating at home rather than at the inn; appreciation of valuable gifts. */
    FRUGALITY,
    /** Attachment to home: eating at home, favourite-spot visits, reluctance to explore or to leave as a companion. */
    HOMEBODY,
    /** Disagreement probability with incompatible personalities; acceptance of outsiders. */
    TOLERANCE;

    public static final int COUNT = values().length;
}
