package dev.underdeep.civ.econ;

/**
 * Prosperity components (0–1). The weighted score drives decisions: construction funding, merchant stock size,
 * migration attraction, gatherings, wage levels and emergency measures (see {@code Decisions}).
 */
public enum Prosperity {
    FOOD(0.22), HOUSING(0.1), EMPLOYMENT(0.12), RESERVES(0.1), TRADE(0.08), PRODUCTION(0.1), CONNECTIVITY(0.05), HEALTH(0.08),
    SAFETY(0.05), INFRASTRUCTURE(0.04), MONEY(0.06);

    public static final int COUNT = values().length;
    public final double weight;

    Prosperity(double weight) {
        this.weight = weight;
    }

    public static double score(double[] v, double disruption) {
        double s = 0, w = 0;
        for (Prosperity p : values()) {
            s += v[p.ordinal()] * p.weight;
            w += p.weight;
        }
        return Math.max(0, Math.min(1, s / w - disruption * 0.15));
    }
}
