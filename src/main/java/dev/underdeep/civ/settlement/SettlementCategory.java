package dev.underdeep.civ.settlement;

/**
 * Settlement size classes. Ordinals are persisted (append-only).
 *
 * @param minRadius / maxRadius   planned radius range (blocks; diameter roughly matches the brief)
 * @param minBuildings / maxBuildings target number of real buildings (not counting stalls, lamps, walls)
 * @param minPeople / maxPeople   registered inhabitants
 * @param headroom                minimum open air above the floor for the central area
 */
public enum SettlementCategory {
    ENCAMPMENT(9, 16, 2, 5, 2, 6, 7),
    OUTPOST(16, 32, 4, 10, 5, 15, 8),
    VILLAGE(32, 55, 10, 25, 12, 35, 10),
    TOWN(52, 100, 25, 55, 35, 80, 12),
    CITY(90, 175, 50, 100, 80, 200, 14);

    public final int minRadius, maxRadius, minBuildings, maxBuildings, minPeople, maxPeople, headroom;

    SettlementCategory(int minRadius, int maxRadius, int minBuildings, int maxBuildings, int minPeople, int maxPeople, int headroom) {
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.minBuildings = minBuildings;
        this.maxBuildings = maxBuildings;
        this.minPeople = minPeople;
        this.maxPeople = maxPeople;
        this.headroom = headroom;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public SettlementCategory smaller() {
        return this == ENCAMPMENT ? null : values()[ordinal() - 1];
    }

    public boolean atLeast(SettlementCategory o) {
        return ordinal() >= o.ordinal();
    }
}
