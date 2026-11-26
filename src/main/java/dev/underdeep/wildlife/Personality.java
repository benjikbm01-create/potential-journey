package dev.underdeep.wildlife;

import java.util.UUID;

/**
 * Lightweight individual personality, derived deterministically from the entity UUID (so it is
 * stable for persistent creatures without extra storage). Each trait is in [0,1]; behaviours use the
 * traits as small multipliers (flee distance, curiosity approach chance, warning patience, play
 * frequency), so individuals of a species differ subtly rather than needing separate AI.
 */
public record Personality(float curiosity, float timidity, float aggression, float sociability, float playfulness,
                          float boldness, float loyalty) {
    public static Personality of(UUID id) {
        long h = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17);
        float[] t = new float[7];
        for (int i = 0; i < 7; i++) {
            h ^= h >>> 33;
            h *= 0xff51afd7ed558ccdL;
            h ^= h >>> 29;
            // bias towards the middle so extreme individuals are uncommon
            float a = ((h >>> 40) & 0xFFFF) / 65535f;
            float b = ((h >>> 20) & 0xFFFF) / 65535f;
            t[i] = (a + b) / 2f;
        }
        return new Personality(t[0], t[1], t[2], t[3], t[4], t[5], t[6]);
    }

    /** Distance multiplier for fleeing from players (timid individuals flee earlier). */
    public float fleeFactor() {
        return 0.7f + timidity * 0.8f - boldness * 0.3f;
    }

    public String describe() {
        StringBuilder b = new StringBuilder();
        if (curiosity > 0.65f) b.append("curious ");
        if (timidity > 0.65f) b.append("timid ");
        if (aggression > 0.65f) b.append("aggressive ");
        if (sociability > 0.65f) b.append("sociable ");
        if (playfulness > 0.65f) b.append("playful ");
        if (boldness > 0.65f) b.append("bold ");
        return b.length() == 0 ? "even-tempered" : b.toString().trim();
    }
}
