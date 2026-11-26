package dev.underdeep.civ;

import dev.underdeep.world.region.RegionType;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import static dev.underdeep.world.region.RegionType.*;

/**
 * The eight civilizations of the underground (Stage 4). Ids and ordinals are persistence contracts
 * (stored in settlement records): append-only. Public identity only; histories and cultural secrets are
 * kept in docs/private/.
 */
public enum Civilization {
    ROOTFOLK("rootfolk", 0x6B8E3A, 0xC9A66B, EnumSet.of(ROOTWILD), EnumSet.of(EMERALD_WETLANDS, GOLDEN_GROVE, FUNGAL_EXPANSE)),
    FORGEKIN("forgekin", 0xB5502A, 0x3A3A40, EnumSet.of(ASHEN_HOLLOWS), EnumSet.of(CRIMSON_BASIN, STORMSTONE_CAVERNS, PETRIFIED_FOREST)),
    TIDE_DWELLERS("tide_dwellers", 0x2F7F8F, 0xD8CFA8, EnumSet.of(FLOODED_DEPTHS, SUNKEN_GARDENS), EnumSet.of(EMERALD_WETLANDS)),
    SPOREFOLK("sporefolk", 0x9A5FB0, 0xE0C27A, EnumSet.of(FUNGAL_EXPANSE), EnumSet.of(EMERALD_WETLANDS, ROOTWILD)),
    GLIMMERKIN("glimmerkin", 0x6E7FD8, 0xE6E2F0, EnumSet.of(CRYSTAL_DEEP), EnumSet.of(STORMSTONE_CAVERNS, STARLIGHT_CAVERNS)),
    STONEBORN("stoneborn", 0x7D7468, 0xB0453A, EnumSet.of(HOLLOW_PEAKS), EnumSet.of(EVERFROST, PETRIFIED_FOREST)),
    AMBERFOLK("amberfolk", 0xD99A2B, 0x8A4B2A, EnumSet.of(GOLDEN_GROVE), EnumSet.of(ROOTWILD, EMERALD_WETLANDS)),
    ASTRALS("astrals", 0x9FB8E8, 0xF2F4FA, EnumSet.of(STARLIGHT_CAVERNS), EnumSet.of(CRYSTAL_DEEP));

    private final String id;
    /** Primary and accent colours (clothing tints, banners, map colours). */
    public final int primaryColor, accentColor;
    private final Set<RegionType> home, secondary;

    Civilization(String id, int primaryColor, int accentColor, Set<RegionType> home, Set<RegionType> secondary) {
        this.id = id;
        this.primaryColor = primaryColor;
        this.accentColor = accentColor;
        this.home = home;
        this.secondary = secondary;
    }

    public String id() {
        return id;
    }

    public Set<RegionType> homeRegions() {
        return home;
    }

    public Set<RegionType> secondaryRegions() {
        return secondary;
    }

    public String translationKey() {
        return "civ.underdeep." + id;
    }

    /** Suitability of an ecosystem for this civilization: 1 home, 0.55 secondary, 0 otherwise. */
    public double affinity(RegionType type) {
        return home.contains(type) ? 1.0 : secondary.contains(type) ? 0.55 : 0.0;
    }

    /** Civilizations that are friendly toward newcomers (used to choose the settlement nearest spawn). */
    public boolean welcoming() {
        return this == ROOTFOLK || this == AMBERFOLK || this == SPOREFOLK || this == TIDE_DWELLERS;
    }

    /** Native civilization of an ecosystem (null for the deep abyss, which only hosts outposts of others). */
    public static Civilization nativeOf(RegionType type) {
        for (Civilization c : values()) {
            if (c.home.contains(type)) {
                return c;
            }
        }
        return switch (type) {
            case EMERALD_WETLANDS -> TIDE_DWELLERS;
            case CRIMSON_BASIN, STORMSTONE_CAVERNS -> FORGEKIN;
            case EVERFROST, PETRIFIED_FOREST -> STONEBORN;
            default -> null;
        };
    }

    public static Civilization byId(String id) {
        for (Civilization c : values()) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return id.toUpperCase(Locale.ROOT);
    }
}
