package dev.underdeep.world.region;

/**
 * Natural hazard / hostile-spawn class of a region. Drives natural hostile spawning (see
 * {@code SpawnRules}) and is exposed to future wildlife and civilization systems.
 * Persistence contract: append-only.
 */
public enum Danger {
    LOW, MEDIUM, HIGH, EXTREME;

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
