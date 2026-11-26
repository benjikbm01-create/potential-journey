package dev.underdeep.wildlife;

/**
 * Biological activity cycles. Underdeep has no sun, but world time still advances; each species
 * keys its rhythm to it (or to its own internal cycle) so caverns are busier at some times than others.
 */
public final class Rhythms {
    private Rhythms() {
    }

    /** @param timeOfDay world time of day (0..24000 repeating) */
    public static boolean isActive(CreatureSpecies.Activity a, long timeOfDay, int entityId) {
        long t = Math.floorMod(timeOfDay, 24000L);
        return switch (a) {
            case DAY -> t < 12500;
            case NIGHT -> t >= 12500;
            case TWILIGHT -> (t > 10500 && t < 14500) || t > 21500 || t < 1500;
            case ALWAYS -> true;
            // short bursts of activity with long pauses, each individual on its own phase
            case BURSTS -> Math.floorMod(t + entityId * 977L, 6000L) < 2400;
        };
    }
}
