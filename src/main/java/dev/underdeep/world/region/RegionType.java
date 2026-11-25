package dev.underdeep.world.region;

import dev.underdeep.world.analysis.SiteType;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The foundational underground regions. Each region has a stable string id (used for biome ids,
 * persistence and future data files), a terrain profile, an environmental description and
 * settlement affinities.
 * <p>
 * IMPORTANT: ids and ordinal order are persistence contracts. Append new regions at the end; never
 * rename or reorder existing entries.
 */
public enum RegionType {
    ROOTWILD("rootwild",
            TerrainProfile.of(0, 1.0, 20, 1 / 75.0, 0.35, 0, 1.15, 1.15, 1.1, 0.35, 5,
                    0.55, 1.25, 0.30, 0.35, 1.0, 0.35, 0.10, 0.45, 0),
            new RegionEnvironment(0.5, 0.65, 0.45, 0.8, 0.2, 0.85,
                    Set.of("forest", "moss", "roots", "stream", "temperate"),
                    Set.of("wood", "food_plants", "water", "stone", "common_ores")),
            0x2E4A2A),
    FUNGAL_EXPANSE("fungal_expanse",
            TerrainProfile.of(6, 0.9, 15, 1 / 45.0, 0.0, 3, 1.0, 0.9, 1.0, 0.5, 7,
                    0.30, 1.0, 0.15, 0.2, 0.5, 0.3, 0.10, 0.25, 0),
            new RegionEnvironment(0.6, 0.85, 0.6, 0.6, 0.3, 0.7,
                    Set.of("fungal", "spores", "humid", "mounds"),
                    Set.of("fungal_wood", "mushrooms", "glow", "common_ores")),
            0x4A2A55),
    FLOODED_DEPTHS("flooded_depths",
            TerrainProfile.of(-46, 1.1, 27, 1 / 80.0, 0.2, 0, 1.3, 1.05, 1.25, 0.7, 5,
                    0.35, 1.1, 0.4, 0.6, 0.3, 0.0, 0.0, 0.0, 0),
            new RegionEnvironment(0.4, 1.0, 0.35, 1.0, 0.35, 0.6,
                    Set.of("lake", "island", "shore", "waterfall", "aquatic"),
                    Set.of("fish", "clay", "sand", "water", "dripstone")),
            0x1F3B55),
    ASHEN_HOLLOWS("ashen_hollows",
            TerrainProfile.of(4, 1.2, 28, 1 / 65.0, 0.8, 7, 0.95, 0.9, 0.9, 0.4, 9,
                    0.70, 0.55, 0.35, 0.5, 0.8, 0.35, 0.12, 0.8, 0),
            new RegionEnvironment(0.95, 0.1, 0.55, 0.15, 0.75, 0.15,
                    Set.of("volcanic", "basalt", "lava", "ash", "hot"),
                    Set.of("basalt", "blackstone", "magma", "obsidian", "metal_ores")),
            0x4A2418),
    CRYSTAL_DEEP("crystal_deep",
            TerrainProfile.of(-4, 1.0, 22, 1 / 60.0, 0.6, 0, 1.1, 1.6, 1.0, 0.45, 6,
                    0.45, 0.9, 0.5, 1.0, 0.3, 0.25, 0.12, 0.4, 0),
            new RegionEnvironment(0.25, 0.35, 0.8, 0.5, 0.4, 0.3,
                    Set.of("crystal", "geode", "echoing", "cold", "tall_caverns"),
                    Set.of("crystal", "amethyst", "calcite", "rare_minerals")),
            0x1F4A55),
    THE_ABYSS("the_abyss",
            TerrainProfile.of(0, 1.2, 26, 1 / 80.0, 0.7, 0, 1.35, 1.5, 1.2, 0.6, 8,
                    0.6, 1.8, 0.5, 0.8, 0.0, 0.2, 0.0, 0.3, 0.6),
            new RegionEnvironment(0.55, 0.3, 0.05, 0.2, 1.0, 0.1,
                    Set.of("abyssal", "dark", "chasm", "sculk", "colossal"),
                    Set.of("deepslate", "sculk", "deep_ores", "lava")),
            0x0E0F17);

    private final String id;
    private final TerrainProfile profile;
    private final RegionEnvironment environment;
    private final int mapColor;
    private final Map<SiteType, Double> settlementAffinity = new EnumMap<>(SiteType.class);

    RegionType(String id, TerrainProfile profile, RegionEnvironment environment, int mapColor) {
        this.id = id;
        this.profile = profile;
        this.environment = environment;
        this.mapColor = mapColor;
    }

    static {
        ROOTWILD.affinity(SiteType.VILLAGE, 1.0).affinity(SiteType.CITY, 0.8).affinity(SiteType.FARM, 1.0)
                .affinity(SiteType.MARKET, 0.9).affinity(SiteType.OUTPOST, 0.6).affinity(SiteType.BRIDGE, 0.6)
                .affinity(SiteType.HANGING, 0.5).affinity(SiteType.INDUSTRIAL, 0.3).affinity(SiteType.FLOATING, 0.3);
        FUNGAL_EXPANSE.affinity(SiteType.VILLAGE, 0.9).affinity(SiteType.HANGING, 0.8).affinity(SiteType.FARM, 0.8)
                .affinity(SiteType.MARKET, 0.7).affinity(SiteType.CITY, 0.5).affinity(SiteType.OUTPOST, 0.6)
                .affinity(SiteType.BRIDGE, 0.5).affinity(SiteType.INDUSTRIAL, 0.3).affinity(SiteType.FLOATING, 0.2);
        FLOODED_DEPTHS.affinity(SiteType.FLOATING, 1.0).affinity(SiteType.VILLAGE, 0.6).affinity(SiteType.MARKET, 0.8)
                .affinity(SiteType.BRIDGE, 0.9).affinity(SiteType.FARM, 0.4).affinity(SiteType.CITY, 0.5)
                .affinity(SiteType.OUTPOST, 0.5).affinity(SiteType.HANGING, 0.4).affinity(SiteType.INDUSTRIAL, 0.2);
        ASHEN_HOLLOWS.affinity(SiteType.INDUSTRIAL, 1.0).affinity(SiteType.OUTPOST, 0.8).affinity(SiteType.CITY, 0.4)
                .affinity(SiteType.BRIDGE, 0.8).affinity(SiteType.VILLAGE, 0.3).affinity(SiteType.MARKET, 0.4)
                .affinity(SiteType.HANGING, 0.5).affinity(SiteType.FARM, 0.05).affinity(SiteType.FLOATING, 0.0);
        CRYSTAL_DEEP.affinity(SiteType.CITY, 0.9).affinity(SiteType.HANGING, 1.0).affinity(SiteType.MARKET, 0.6)
                .affinity(SiteType.VILLAGE, 0.6).affinity(SiteType.OUTPOST, 0.7).affinity(SiteType.BRIDGE, 0.8)
                .affinity(SiteType.INDUSTRIAL, 0.5).affinity(SiteType.FARM, 0.2).affinity(SiteType.FLOATING, 0.3);
        THE_ABYSS.affinity(SiteType.OUTPOST, 1.0).affinity(SiteType.HANGING, 0.9).affinity(SiteType.BRIDGE, 1.0)
                .affinity(SiteType.INDUSTRIAL, 0.6).affinity(SiteType.CITY, 0.2).affinity(SiteType.VILLAGE, 0.2)
                .affinity(SiteType.MARKET, 0.1).affinity(SiteType.FARM, 0.0).affinity(SiteType.FLOATING, 0.0);
    }

    private RegionType affinity(SiteType type, double value) {
        settlementAffinity.put(type, value);
        return this;
    }

    public String id() {
        return id;
    }

    public TerrainProfile profile() {
        return profile;
    }

    public RegionEnvironment environment() {
        return environment;
    }

    /** RGB colour used by diagnostic map renders. */
    public int mapColor() {
        return mapColor;
    }

    /** 0..1 cultural/physical preference of this region for hosting the given settlement type. */
    public double settlementAffinity(SiteType type) {
        return settlementAffinity.getOrDefault(type, 0.0);
    }

    /** Whether this region's open fluid bodies are lava instead of water. */
    public boolean lavaFluid() {
        return this == ASHEN_HOLLOWS || this == THE_ABYSS;
    }

    public String displayName() {
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public static RegionType byId(String id) {
        for (RegionType t : values()) {
            if (t.id.equals(id.toLowerCase(Locale.ROOT))) {
                return t;
            }
        }
        return null;
    }
}
