package dev.underdeep.ecology;

import dev.underdeep.world.region.RegionType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Ecological metadata of one biological species or geological resource (Stage 2).
 * <p>
 * Pure data, independent of Minecraft classes, so it can be unit-tested and consumed by later
 * systems: Stage 3 wildlife (food, shelter, nesting, habitat choice), Stage 6 civilizations
 * (agricultural productivity, trade goods) and the abstract regional simulation of unloaded areas.
 * Instances are immutable; build them with {@link #of(String, Kind)}.
 */
public final class Species {
    public enum Kind { TREE, SHRUB, HERB, FLOWER, GRASS, FERN, VINE, FUNGUS, GIANT_FUNGUS, CROP, AQUATIC, CORAL, MINERAL, CRYSTAL }

    /** Discovery hierarchy (MASTER_DESIGN §Stage 2). Exceptional species are documented privately. */
    public enum Rarity { COMMON, UNCOMMON, RARE, EXCEPTIONAL }

    public enum Edibility { INEDIBLE, EDIBLE, EDIBLE_COOKED, TOXIC }

    /** Preferred block-light band for growth. DARK = 0-6, DIM = 3-11, BRIGHT = 9-15. */
    public enum LightPref {
        DARK(0, 6), DIM(3, 11), BRIGHT(9, 15), ANY(0, 15);
        public final int min;
        public final int max;

        LightPref(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public boolean accepts(int light) {
            return light >= min && light <= max;
        }
    }

    public enum WaterNeed { NONE, MOIST, SUBMERGED, FLOATING }

    public enum Climate { ANY, COLD, TEMPERATE, HOT }

    /** What the species offers to other organisms (the Stage 3 food web and habitat vocabulary). */
    public enum Role {
        FORAGE,         // leaves/grass eaten by grazing and browsing herbivores
        FRUIT,          // fruit or berries (frugivores, omnivores)
        SEEDS,          // grain or seeds (seed eaters, small rodents, birds)
        NECTAR,         // flowers visited by pollinating insects
        FUNGAL_FOOD,    // fungi eaten by fungivores
        AQUATIC_FORAGE, // underwater grazing
        SHELTER,        // dense cover where small animals hide
        NEST_SITE,      // supports nests (trees, cliffs, colonies)
        LIGHT_SOURCE,   // bioluminescent: attracts light-seeking creatures, used by settlements
        HAZARD,         // harms creatures that touch it
        MINERAL_FOOD    // licked or grazed for minerals by lithic creatures
    }

    public enum Reproduction { SEEDS, SAPLING, SPREADING, SPORES, BUDDING, CUTTINGS, NONE }

    private final String id;
    private final Kind kind;
    private Rarity rarity = Rarity.COMMON;
    private Set<RegionType> habitats = EnumSet.noneOf(RegionType.class);
    private Edibility edibility = Edibility.INEDIBLE;
    private LightPref light = LightPref.ANY;
    private WaterNeed water = WaterNeed.NONE;
    private Climate climate = Climate.ANY;
    private Set<Role> roles = EnumSet.noneOf(Role.class);
    private Reproduction reproduction = Reproduction.NONE;
    private boolean renewable;
    private double abundance = 0.3;
    private List<String> blocks = List.of();
    private String yield = "";

    private Species(String id, Kind kind) {
        this.id = id;
        this.kind = kind;
        this.renewable = kind != Kind.MINERAL;
    }

    public static Species of(String id, Kind kind) {
        return new Species(id, kind);
    }

    // ---- builder-style setters (used only while building the registry) ----
    public Species rarity(Rarity r) { this.rarity = r; return this; }
    public Species in(RegionType... types) { this.habitats = types.length == 0 ? EnumSet.noneOf(RegionType.class) : EnumSet.of(types[0], types); return this; }
    public Species edible(Edibility e) { this.edibility = e; return this; }
    public Species light(LightPref l) { this.light = l; return this; }
    public Species water(WaterNeed w) { this.water = w; return this; }
    public Species climate(Climate c) { this.climate = c; return this; }
    public Species roles(Role... r) { this.roles = r.length == 0 ? EnumSet.noneOf(Role.class) : EnumSet.of(r[0], r); return this; }
    public Species reproduces(Reproduction r) { this.reproduction = r; this.renewable = r != Reproduction.NONE; return this; }
    public Species abundance(double a) { this.abundance = a; return this; }
    public Species blocks(String... ids) { this.blocks = List.of(ids); return this; }
    public Species yields(String itemId) { this.yield = itemId; return this; }

    // ---- accessors ----
    public String id() { return id; }
    public Kind kind() { return kind; }
    public Rarity rarity() { return rarity; }
    public Set<RegionType> habitats() { return Collections.unmodifiableSet(habitats); }
    public Edibility edibility() { return edibility; }
    public LightPref light() { return light; }
    public WaterNeed water() { return water; }
    public Climate climate() { return climate; }
    public Set<Role> roles() { return Collections.unmodifiableSet(roles); }
    public Reproduction reproduction() { return reproduction; }
    public boolean renewable() { return renewable; }
    /** Typical relative abundance in its habitats, 0..1 (cover share for plants, frequency for minerals). */
    public double abundance() { return abundance; }
    /** Block ids (namespace-less, underdeep:) that represent this species in the world. */
    public List<String> blockIds() { return blocks; }
    /** Main harvested item id, or empty. */
    public String yield() { return yield; }

    public boolean isBiological() {
        return kind != Kind.MINERAL && kind != Kind.CRYSTAL;
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }

    @Override
    public String toString() {
        return id;
    }
}
