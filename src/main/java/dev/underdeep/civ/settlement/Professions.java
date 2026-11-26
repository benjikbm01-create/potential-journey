package dev.underdeep.civ.settlement;

import dev.underdeep.civ.Civilization;

import java.util.List;
import java.util.Map;

/**
 * Foundational professions (Stage 4). Ids are persisted in inhabitant records (append-only). Each
 * civilization has workshop specialties; general roles (farmer, guard, merchant, cook ...) exist everywhere.
 */
public final class Professions {
    /** All profession ids (30). */
    public static final List<String> ALL = List.of(
            "farmer", "herbalist", "forester", "carpenter", "cook", "baker", "brewer", "blacksmith", "miner", "smelter",
            "engineer", "fisher", "boatwright", "navigator", "alchemist", "mycologist", "healer", "gemcutter", "scholar",
            "cartographer", "stonemason", "hunter", "guard", "merchant", "tailor", "innkeeper", "rancher", "artisan",
            "astronomer", "lampwright");

    /** Professions without a workshop of their own: they work at farms, markets, posts, docks or inns. */
    public static final List<String> ROAMING = List.of("farmer", "guard", "merchant", "innkeeper", "rancher", "fisher", "hunter", "cook");

    private static final Map<Civilization, List<String>> SPECIALTIES = Map.of(
            Civilization.ROOTFOLK, List.of("carpenter", "herbalist", "forester", "brewer", "tailor"),
            Civilization.FORGEKIN, List.of("blacksmith", "smelter", "engineer", "miner", "stonemason"),
            Civilization.TIDE_DWELLERS, List.of("boatwright", "navigator", "tailor", "carpenter", "brewer"),
            Civilization.SPOREFOLK, List.of("alchemist", "mycologist", "herbalist", "healer", "brewer"),
            Civilization.GLIMMERKIN, List.of("gemcutter", "artisan", "lampwright", "scholar", "smelter"),
            Civilization.STONEBORN, List.of("stonemason", "blacksmith", "hunter", "miner", "tailor"),
            Civilization.AMBERFOLK, List.of("tailor", "carpenter", "brewer", "herbalist", "artisan"),
            Civilization.ASTRALS, List.of("astronomer", "scholar", "lampwright", "cartographer", "artisan"));

    private Professions() {
    }

    public static List<String> specialties(Civilization civ) {
        return SPECIALTIES.get(civ);
    }

    /** The i-th workshop profession for a civilization (cycles through its specialties). */
    public static String workshopFor(Civilization civ, int i) {
        List<String> s = SPECIALTIES.get(civ);
        return s.get(Math.floorMod(i, s.size()));
    }

    public static boolean valid(String id) {
        return ALL.contains(id);
    }
}
