package dev.underdeep.world.region;

import dev.underdeep.world.analysis.SiteType;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The underground ecosystems (15 as of Stage 1.5). Each region has a stable string id (used for biome ids,
 * persistence and future data files), a terrain profile, an environmental description and
 * settlement affinities.
 * <p>
 * IMPORTANT: ids and ordinal order are persistence contracts. Append new regions at the end; never
 * rename or reorder existing entries.
 */
public enum RegionType {
    // ---- Stage 1 regions (ordinals 0-5 are persistence contracts) ----
    ROOTWILD("rootwild", Danger.LOW, Lighting.BRIGHT, 0.0,
            TerrainProfile.of(0, 1.0, 20, 1 / 75.0, 0.35, 0, 1.15, 1.15, 1.1, 0.35, 5,
                    0.45, 1.25, 0.30, 0.25, 1.0, 0.35, 0.10, 0.35, 0),
            new RegionEnvironment(0.5, 0.65, 0.45, 0.8, 0.2, 0.85,
                    Set.of("forest", "moss", "roots", "stream", "temperate"),
                    Set.of("wood", "food_plants", "water", "stone", "common_ores")),
            0x2E4A2A),
    FUNGAL_EXPANSE("fungal_expanse", Danger.LOW, Lighting.BRIGHT, 0.0,
            TerrainProfile.of(6, 0.9, 15, 1 / 45.0, 0.0, 3, 1.0, 0.9, 1.0, 0.5, 7,
                    0.30, 1.0, 0.15, 0.2, 0.5, 0.3, 0.10, 0.25, 0),
            new RegionEnvironment(0.6, 0.85, 0.6, 0.6, 0.3, 0.7,
                    Set.of("fungal", "spores", "humid", "mounds"),
                    Set.of("fungal_wood", "mushrooms", "glow", "common_ores")),
            0x4A2A55),
    FLOODED_DEPTHS("flooded_depths", Danger.MEDIUM, Lighting.MODERATE, 0.22,
            TerrainProfile.of(-46, 1.1, 27, 1 / 80.0, 0.2, 0, 1.3, 1.05, 1.25, 0.7, 5,
                    0.35, 1.1, 0.4, 0.5, 0.3, 0.0, 0.0, 0.0, 0),
            new RegionEnvironment(0.4, 1.0, 0.35, 1.0, 0.35, 0.6,
                    Set.of("lake", "island", "shore", "waterfall", "aquatic"),
                    Set.of("fish", "clay", "sand", "water", "dripstone")),
            0x1F3B55),
    ASHEN_HOLLOWS("ashen_hollows", Danger.HIGH, Lighting.DARK, 0.05,
            TerrainProfile.of(4, 1.2, 30, 1 / 65.0, 0.8, 7, 0.95, 0.95, 0.9, 0.4, 9,
                    0.35, 0.9, 0.35, 0.45, 0.8, 0.35, 0.12, 0.8, 0),
            new RegionEnvironment(0.95, 0.1, 0.4, 0.15, 0.75, 0.15,
                    Set.of("volcanic", "basalt", "lava", "ash", "hot"),
                    Set.of("basalt", "blackstone", "magma", "obsidian", "metal_ores")),
            0x4A2418),
    CRYSTAL_DEEP("crystal_deep", Danger.MEDIUM, Lighting.MODERATE, 0.05,
            TerrainProfile.of(-4, 1.0, 22, 1 / 60.0, 0.6, 0, 1.1, 1.6, 1.0, 0.45, 6,
                    0.45, 0.9, 0.55, 0.8, 0.3, 0.35, 0.12, 0.4, 0),
            new RegionEnvironment(0.25, 0.35, 0.7, 0.5, 0.4, 0.3,
                    Set.of("crystal", "geode", "echoing", "cold", "tall_caverns"),
                    Set.of("crystal", "amethyst", "calcite", "rare_minerals")),
            0x1F4A55),
    THE_ABYSS("the_abyss", Danger.EXTREME, Lighting.PITCH, 0.30,
            TerrainProfile.of(0, 1.2, 26, 1 / 80.0, 0.7, 0, 1.35, 1.5, 1.2, 0.6, 8,
                    0.6, 1.8, 0.5, 0.8, 0.0, 0.2, 0.0, 0.3, 0.6),
            new RegionEnvironment(0.55, 0.3, 0.05, 0.2, 1.0, 0.1,
                    Set.of("abyssal", "dark", "chasm", "sculk", "colossal"),
                    Set.of("deepslate", "sculk", "deep_ores", "lava")),
            0x0E0F17),
    // ---- Stage 1.5 regions (append only) ----
    EVERFROST("everfrost", Danger.MEDIUM, Lighting.MODERATE, 0.08,
            TerrainProfile.of(-24, 1.0, 18, 1 / 70.0, 0.5, 0, 1.05, 1.25, 1.0, 0.4, 6,
                    0.40, 1.0, 0.6, 1.0, 0.4, 0.5, 0.10, 0.2, 0).extras(0, 1.6),
            new RegionEnvironment(0.0, 0.5, 0.45, 0.6, 0.5, 0.15,
                    Set.of("frozen", "ice", "glacier", "snow", "cold"),
                    Set.of("ice", "packed_ice", "snow", "rime_crystal", "common_ores")),
            0x6E9EC0),
    GOLDEN_GROVE("golden_grove", Danger.LOW, Lighting.BRIGHT, 0.04,
            TerrainProfile.of(2, 0.7, 10, 1 / 90.0, 0.0, 0, 1.2, 1.05, 1.1, 0.45, 4,
                    0.25, 1.4, 0.15, 0.15, 0.9, 0.5, 0.08, 0.1, 0).extras(0, 1.6),
            new RegionEnvironment(0.7, 0.5, 0.8, 0.75, 0.1, 0.95,
                    Set.of("forest", "meadow", "warm", "golden", "clearing"),
                    Set.of("wood", "food_plants", "water", "flowers", "common_ores")),
            0xC59A2E),
    HOLLOW_PEAKS("hollow_peaks", Danger.MEDIUM, Lighting.MODERATE, 0.20,
            TerrainProfile.of(6, 1.3, 72, 1 / 115.0, 0.75, 12, 1.35, 2.0, 1.1, 0.6, 8,
                    0.30, 1.3, 0.9, 0.4, 0.2, 0.2, 0.10, 0.6, 0),
            new RegionEnvironment(0.3, 0.3, 0.4, 0.4, 0.5, 0.35,
                    Set.of("mountain", "cliff", "plateau", "pass", "highland"),
                    Set.of("stone", "iron", "coal", "emerald_like", "granite")),
            0x7A6F66),
    EMERALD_WETLANDS("emerald_wetlands", Danger.LOW, Lighting.BRIGHT, 0.06,
            TerrainProfile.of(-31, 0.25, 7, 1 / 40.0, 0.0, 0, 1.25, 0.95, 1.2, 0.6, 3,
                    0.2, 1.0, 0.1, 0.15, 0.6, 0.0, 0.0, 0.0, 0),
            new RegionEnvironment(0.55, 1.0, 0.75, 1.0, 0.15, 0.9,
                    Set.of("marsh", "wetland", "reeds", "shallow_water", "humid"),
                    Set.of("clay", "mud", "reeds", "fish", "food_plants")),
            0x2F8A52),
    PETRIFIED_FOREST("petrified_forest", Danger.MEDIUM, Lighting.MODERATE, 0.0,
            TerrainProfile.of(0, 1.0, 16, 1 / 60.0, 0.5, 4, 1.05, 1.2, 1.0, 0.4, 7,
                    0.9, 0.75, 0.35, 0.35, 0.3, 0.2, 0.10, 0.5, 0),
            new RegionEnvironment(0.45, 0.2, 0.4, 0.3, 0.45, 0.2,
                    Set.of("ancient", "fossil", "mineralized", "quiet", "dry"),
                    Set.of("petrified_wood", "bone", "amber", "fossils")),
            0x8C7B60),
    CRIMSON_BASIN("crimson_basin", Danger.HIGH, Lighting.DARK, 0.0,
            TerrainProfile.of(8, 0.9, 14, 1 / 50.0, 0.3, 0, 1.2, 1.15, 1.0, 0.5, 8,
                    0.3, 1.0, 0.25, 0.4, 0.4, 0.45, 0.10, 0.3, 0).extras(30, 1.2),
            new RegionEnvironment(0.8, 0.6, 0.35, 0.3, 0.75, 0.4,
                    Set.of("alien", "red", "overgrown", "basin", "hot"),
                    Set.of("crimson_growth", "lava", "netherrack", "red_minerals")),
            0x8E1E28),
    SUNKEN_GARDENS("sunken_gardens", Danger.MEDIUM, Lighting.MODERATE, 0.12,
            TerrainProfile.of(-62, 1.0, 30, 1 / 55.0, 0.6, 0, 1.2, 1.1, 1.2, 0.6, 6,
                    0.3, 1.0, 0.4, 0.3, 0.0, 0.0, 0.0, 0.0, 0),
            new RegionEnvironment(0.6, 1.0, 0.5, 1.0, 0.45, 0.6,
                    Set.of("underwater", "coral", "kelp_forest", "submerged", "aquatic"),
                    Set.of("coral", "sea_pickles", "prismarine_like", "fish")),
            0x1E7A80),
    STORMSTONE_CAVERNS("stormstone_caverns", Danger.HIGH, Lighting.DARK, 0.04,
            TerrainProfile.of(2, 1.3, 26, 1 / 45.0, 0.9, 5, 1.0, 1.3, 0.95, 0.45, 13,
                    0.35, 0.8, 0.4, 1.0, 0.0, 0.15, 0.12, 0.9, 0),
            new RegionEnvironment(0.2, 0.2, 0.35, 0.2, 0.75, 0.05,
                    Set.of("mineral", "fractured", "charged", "jagged", "strange"),
                    Set.of("stormstone", "storm_crystal", "metal_ores", "rare_minerals")),
            0x3D3A8C),
    STARLIGHT_CAVERNS("starlight_caverns", Danger.LOW, Lighting.BRIGHT, 0.18,
            TerrainProfile.of(-2, 0.6, 8, 1 / 100.0, 0.1, 0, 1.55, 1.7, 1.25, 0.7, 4,
                    0.2, 1.2, 0.4, 0.2, 0.4, 0.8, 0.08, 0.0, 0).extras(0, 2.6),
            new RegionEnvironment(0.4, 0.55, 0.95, 0.8, 0.05, 0.8,
                    Set.of("meadow", "lake", "starlit", "open", "peaceful"),
                    Set.of("water", "flowers", "starstone", "common_ores")),
            0x6C7BD8);

    private final Danger danger;
    private final Lighting lighting;
    private final double sizeBias;
    private final String id;
    private final TerrainProfile profile;
    private final RegionEnvironment environment;
    private final int mapColor;
    private final Map<SiteType, Double> settlementAffinity = new EnumMap<>(SiteType.class);

    RegionType(String id, Danger danger, Lighting lighting, double sizeBias, TerrainProfile profile,
               RegionEnvironment environment, int mapColor) {
        this.danger = danger;
        this.lighting = lighting;
        this.sizeBias = sizeBias;
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

    static {
        EVERFROST.affinity(SiteType.OUTPOST, 0.8).affinity(SiteType.VILLAGE, 0.5).affinity(SiteType.BRIDGE, 0.8)
                .affinity(SiteType.HANGING, 0.5).affinity(SiteType.CITY, 0.4).affinity(SiteType.MARKET, 0.4)
                .affinity(SiteType.FARM, 0.1).affinity(SiteType.INDUSTRIAL, 0.3).affinity(SiteType.FLOATING, 0.2);
        GOLDEN_GROVE.affinity(SiteType.VILLAGE, 1.0).affinity(SiteType.CITY, 1.0).affinity(SiteType.MARKET, 1.0)
                .affinity(SiteType.FARM, 1.0).affinity(SiteType.OUTPOST, 0.5).affinity(SiteType.BRIDGE, 0.5)
                .affinity(SiteType.HANGING, 0.4).affinity(SiteType.INDUSTRIAL, 0.3).affinity(SiteType.FLOATING, 0.3);
        HOLLOW_PEAKS.affinity(SiteType.OUTPOST, 1.0).affinity(SiteType.CITY, 0.6).affinity(SiteType.BRIDGE, 1.0)
                .affinity(SiteType.HANGING, 0.8).affinity(SiteType.VILLAGE, 0.6).affinity(SiteType.MARKET, 0.5)
                .affinity(SiteType.INDUSTRIAL, 0.5).affinity(SiteType.FARM, 0.3).affinity(SiteType.FLOATING, 0.0);
        EMERALD_WETLANDS.affinity(SiteType.FLOATING, 0.9).affinity(SiteType.VILLAGE, 0.8).affinity(SiteType.FARM, 0.9)
                .affinity(SiteType.MARKET, 0.7).affinity(SiteType.BRIDGE, 0.8).affinity(SiteType.CITY, 0.5)
                .affinity(SiteType.OUTPOST, 0.5).affinity(SiteType.HANGING, 0.2).affinity(SiteType.INDUSTRIAL, 0.1);
        PETRIFIED_FOREST.affinity(SiteType.OUTPOST, 0.8).affinity(SiteType.VILLAGE, 0.5).affinity(SiteType.CITY, 0.4)
                .affinity(SiteType.MARKET, 0.4).affinity(SiteType.BRIDGE, 0.6).affinity(SiteType.HANGING, 0.5)
                .affinity(SiteType.INDUSTRIAL, 0.4).affinity(SiteType.FARM, 0.2).affinity(SiteType.FLOATING, 0.0);
        CRIMSON_BASIN.affinity(SiteType.OUTPOST, 0.7).affinity(SiteType.INDUSTRIAL, 0.6).affinity(SiteType.HANGING, 0.6)
                .affinity(SiteType.VILLAGE, 0.3).affinity(SiteType.CITY, 0.3).affinity(SiteType.MARKET, 0.3)
                .affinity(SiteType.BRIDGE, 0.6).affinity(SiteType.FARM, 0.1).affinity(SiteType.FLOATING, 0.0);
        SUNKEN_GARDENS.affinity(SiteType.FLOATING, 1.0).affinity(SiteType.MARKET, 0.6).affinity(SiteType.VILLAGE, 0.4)
                .affinity(SiteType.BRIDGE, 0.7).affinity(SiteType.CITY, 0.4).affinity(SiteType.OUTPOST, 0.5)
                .affinity(SiteType.HANGING, 0.3).affinity(SiteType.FARM, 0.2).affinity(SiteType.INDUSTRIAL, 0.1);
        STORMSTONE_CAVERNS.affinity(SiteType.INDUSTRIAL, 0.9).affinity(SiteType.OUTPOST, 0.9).affinity(SiteType.HANGING, 0.6)
                .affinity(SiteType.BRIDGE, 0.7).affinity(SiteType.CITY, 0.3).affinity(SiteType.VILLAGE, 0.2)
                .affinity(SiteType.MARKET, 0.3).affinity(SiteType.FARM, 0.0).affinity(SiteType.FLOATING, 0.0);
        STARLIGHT_CAVERNS.affinity(SiteType.CITY, 1.0).affinity(SiteType.VILLAGE, 1.0).affinity(SiteType.MARKET, 1.0)
                .affinity(SiteType.FARM, 0.8).affinity(SiteType.FLOATING, 0.6).affinity(SiteType.BRIDGE, 0.6)
                .affinity(SiteType.HANGING, 0.7).affinity(SiteType.OUTPOST, 0.5).affinity(SiteType.INDUSTRIAL, 0.2);
    }

    public Danger danger() {
        return danger;
    }

    public Lighting lighting() {
        return lighting;
    }

    /** Positive values make contiguous regions of this type larger (exceptional-scale biomes). */
    public double sizeBias() {
        return sizeBias;
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
        return this == ASHEN_HOLLOWS || this == THE_ABYSS || this == CRIMSON_BASIN;
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
