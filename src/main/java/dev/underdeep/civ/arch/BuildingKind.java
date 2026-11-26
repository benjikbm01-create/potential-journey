package dev.underdeep.civ.arch;

import dev.underdeep.civ.settlement.SettlementCategory;

import java.util.Locale;

/**
 * Building functions. Each civilization designs its own version of the kinds it uses (see
 * {@link Architect}). Ordinals are not persisted; ids are (settlement plans store ids). Append-only.
 *
 * @param group      broad category (residential, commercial, production, agricultural, civic, infrastructure)
 * @param residents  beds provided (inhabitants housed)
 * @param workers    jobs provided
 * @param minCat     smallest settlement category that uses this building
 */
public enum BuildingKind {
    TENT("residential", 2, 0, SettlementCategory.ENCAMPMENT),
    CAMPFIRE("civic", 0, 0, SettlementCategory.ENCAMPMENT),
    HOUSE_SMALL("residential", 2, 0, SettlementCategory.OUTPOST),
    HOUSE("residential", 3, 0, SettlementCategory.OUTPOST),
    HOUSE_LARGE("residential", 5, 0, SettlementCategory.VILLAGE),
    SHARED_HOUSE("residential", 8, 0, SettlementCategory.TOWN),
    STALL("commercial", 0, 1, SettlementCategory.ENCAMPMENT),
    SHOP("commercial", 1, 1, SettlementCategory.OUTPOST),
    INN("commercial", 3, 2, SettlementCategory.VILLAGE),
    TRADE_HALL("commercial", 0, 4, SettlementCategory.TOWN),
    WORKSHOP("production", 1, 2, SettlementCategory.OUTPOST),
    KITCHEN("production", 0, 2, SettlementCategory.VILLAGE),
    STOREHOUSE("production", 0, 1, SettlementCategory.OUTPOST),
    FARM("agricultural", 0, 2, SettlementCategory.OUTPOST),
    BARN("agricultural", 1, 1, SettlementCategory.VILLAGE),
    PEN("agricultural", 0, 1, SettlementCategory.VILLAGE),
    HALL("civic", 0, 2, SettlementCategory.VILLAGE),
    LIBRARY("civic", 0, 2, SettlementCategory.TOWN),
    TOWER("civic", 0, 1, SettlementCategory.OUTPOST),
    GUARD_POST("civic", 2, 2, SettlementCategory.VILLAGE),
    CLINIC("civic", 1, 1, SettlementCategory.TOWN),
    LANDMARK("civic", 0, 1, SettlementCategory.TOWN),
    WELL("infrastructure", 0, 0, SettlementCategory.OUTPOST),
    GATE("infrastructure", 0, 2, SettlementCategory.TOWN),
    DOCK("infrastructure", 0, 2, SettlementCategory.ENCAMPMENT);

    public final String group;
    public final int residents, workers;
    public final SettlementCategory minCat;

    BuildingKind(String group, int residents, int workers, SettlementCategory minCat) {
        this.group = group;
        this.residents = residents;
        this.workers = workers;
        this.minCat = minCat;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Counts as a building for the brief's building counts (not stalls, wells, campfires, gates, docks). */
    public boolean countsAsBuilding() {
        return switch (this) {
            case CAMPFIRE, STALL, WELL, GATE, DOCK, PEN -> false;
            default -> true;
        };
    }
}
