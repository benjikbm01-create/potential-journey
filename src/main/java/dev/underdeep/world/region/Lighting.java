package dev.underdeep.world.region;

/**
 * Intended natural illumination class of a region. Controls the density of light-emitting ground
 * cover, plants and formations placed by world generation.
 */
public enum Lighting {
    /** Comfortable to explore and build: most traversed ground at block light >= 8. */
    BRIGHT,
    /** Pools of light with dimmer areas between them. */
    MODERATE,
    /** Dark with dramatic local light (lava, crystals, veins). */
    DARK,
    /** Almost no natural light. */
    PITCH
}
