package dev.underdeep.civ.mind;

/** Emotions (short-lived, event driven) and needs (slow, drive activity choice). Ordinals persisted. */
public final class Feelings {
    private Feelings() {
    }

    public enum Emotion {
        HAPPINESS, CONTENTMENT, EXCITEMENT, CURIOSITY, AFFECTION, CONCERN, FEAR, ANGER, FRUSTRATION, SADNESS, LONELINESS,
        EMBARRASSMENT, STRESS, PRIDE, RELIEF;

        public static final int COUNT = values().length;

        /** Fraction remaining after one in-game hour without new stimulus (before resilience). */
        double hourlyRetention() {
            return switch (this) {
                case FEAR, EMBARRASSMENT, RELIEF, EXCITEMENT -> 0.45;
                case ANGER, FRUSTRATION, CURIOSITY -> 0.6;
                case HAPPINESS, CONCERN, STRESS, PRIDE -> 0.75;
                case SADNESS, LONELINESS, AFFECTION -> 0.9;
                case CONTENTMENT -> 0.85;
            };
        }

        public boolean negative() {
            return this == CONCERN || this == FEAR || this == ANGER || this == FRUSTRATION || this == SADNESS || this == LONELINESS
                    || this == EMBARRASSMENT || this == STRESS;
        }
    }

    /** Needs as satisfaction 0–1 (1 = fully satisfied). */
    public enum Need {
        HUNGER(0.09), REST(0.055), SAFETY(0.0), SOCIAL(0.04), RECREATION(0.03), COMFORT(0.02), PURPOSE(0.035), BELONGING(0.01),
        HEALTH(0.0), CURIOSITY(0.02);

        public static final int COUNT = values().length;
        /** Satisfaction lost per in-game hour (before personality). */
        public final double decay;

        Need(double decay) {
            this.decay = decay;
        }
    }
}
