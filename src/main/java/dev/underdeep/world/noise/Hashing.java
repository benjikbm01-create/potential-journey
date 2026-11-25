package dev.underdeep.world.noise;

/**
 * Deterministic, allocation-free hashing helpers used throughout Underdeep world generation.
 * <p>
 * Every random decision that must be independent of chunk generation order is derived from
 * these pure functions of (seed, coordinates, salt). Never use shared {@code java.util.Random}
 * instances for terrain decisions.
 */
public final class Hashing {
    private Hashing() {
    }

    /** SplitMix64 finalizer. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long hash(long seed, long a) {
        return mix(seed ^ mix(a + 0x9E3779B97F4A7C15L));
    }

    public static long hash(long seed, long a, long b) {
        return mix(hash(seed, a) ^ mix(b * 0xC2B2AE3D27D4EB4FL + 0x165667B19E3779F9L));
    }

    public static long hash(long seed, long a, long b, long c) {
        return mix(hash(seed, a, b) ^ mix(c * 0x27D4EB2F165667C5L + 0x85EBCA77C2B2AE63L));
    }

    /** Uniform double in [0, 1) from a hash value. */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Uniform double in [-1, 1) from a hash value. */
    public static double signed(long h) {
        return unit(h) * 2.0 - 1.0;
    }

    public static long salt(String name) {
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < name.length(); i++) {
            h ^= name.charAt(i);
            h *= 0x100000001B3L;
        }
        return mix(h);
    }
}
