package dev.underdeep.world.analysis;

/**
 * Kinds of locations that future civilization stages may want to place. Each type declares the
 * minimum physical requirements a candidate site must meet; {@link SiteAnalyzer} scores sites
 * against these requirements.
 *
 * @param minHeadroom     minimum open air above the floor (blocks)
 * @param minRadius       minimum radius of usable floor (blocks)
 * @param maxSlope        maximum average floor slope (blocks rise per block)
 * @param wantsWater      whether nearby water is required (true) or merely a bonus (false)
 * @param wantsLava       whether nearby lava is desirable (industrial heat)
 * @param overWater       site sits on a water surface (floating settlements)
 * @param suspended       site hangs from a ceiling above a large drop (hanging settlements)
 */
public enum SiteType {
    VILLAGE(8, 24, 0.45, false, false, false, false),
    CITY(18, 56, 0.35, true, false, false, false),
    MARKET(10, 20, 0.30, false, false, false, false),
    INDUSTRIAL(12, 24, 0.50, false, true, false, false),
    FLOATING(10, 28, 1.0, true, false, true, false),
    HANGING(48, 20, 9.0, false, false, false, true),
    FARM(6, 16, 0.30, true, false, false, false),
    OUTPOST(5, 8, 0.70, false, false, false, false),
    BRIDGE(12, 6, 9.0, false, false, false, false);

    public final int minHeadroom;
    public final int minRadius;
    public final double maxSlope;
    public final boolean wantsWater;
    public final boolean wantsLava;
    public final boolean overWater;
    public final boolean suspended;

    SiteType(int minHeadroom, int minRadius, double maxSlope, boolean wantsWater, boolean wantsLava,
             boolean overWater, boolean suspended) {
        this.minHeadroom = minHeadroom;
        this.minRadius = minRadius;
        this.maxSlope = maxSlope;
        this.wantsWater = wantsWater;
        this.wantsLava = wantsLava;
        this.overWater = overWater;
        this.suspended = suspended;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
