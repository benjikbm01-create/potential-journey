package dev.underdeep.ecology;

import dev.underdeep.ecology.Species.Climate;
import dev.underdeep.ecology.Species.Edibility;
import dev.underdeep.ecology.Species.Kind;
import dev.underdeep.ecology.Species.LightPref;
import dev.underdeep.ecology.Species.Reproduction;
import dev.underdeep.ecology.Species.Role;
import dev.underdeep.ecology.Species.WaterNeed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.underdeep.ecology.Species.Rarity.COMMON;
import static dev.underdeep.ecology.Species.Rarity.EXCEPTIONAL;
import static dev.underdeep.ecology.Species.Rarity.RARE;
import static dev.underdeep.ecology.Species.Rarity.UNCOMMON;
import static dev.underdeep.world.region.RegionType.*;

/**
 * Every Stage 2 species and regional resource with its ecological metadata. Pure Java (no Minecraft
 * classes): unit-tested, and the single source of habitat/food-web data for Stage 3+.
 * <p>
 * Block ids listed here must exist in the block registry; the dedicated-server self-test verifies it.
 * Persistent contract: species ids are stable identifiers (append-only, never renamed).
 */
public final class EcologyRegistry {
    private static final List<Species> ALL = new ArrayList<>();
    private static final Map<String, Species> BY_ID = new HashMap<>();
    private static final Map<String, Species> BY_BLOCK = new HashMap<>();
    private static final Map<dev.underdeep.world.region.RegionType, List<Species>> BY_REGION =
            new EnumMap<>(dev.underdeep.world.region.RegionType.class);

    static {
        build();
        for (Species s : ALL) {
            if (BY_ID.put(s.id(), s) != null) {
                throw new IllegalStateException("duplicate species " + s.id());
            }
            for (String b : s.blockIds()) {
                BY_BLOCK.put(b, s);
            }
            for (var r : s.habitats()) {
                BY_REGION.computeIfAbsent(r, k -> new ArrayList<>()).add(s);
            }
        }
    }

    private EcologyRegistry() {
    }

    private static Species add(Species s) {
        ALL.add(s);
        return s;
    }

    private static Species tree(String id) {
        return add(Species.of(id, Kind.TREE).reproduces(Reproduction.SAPLING)
                .roles(Role.SHELTER, Role.NEST_SITE, Role.FORAGE));
    }

    private static Species plant(String id, Kind kind) {
        return add(Species.of(id, kind));
    }

    private static Species mineral(String id, double abundance) {
        return add(Species.of(id, Kind.MINERAL).abundance(abundance).reproduces(Reproduction.NONE));
    }

    private static void build() {
        // ---------------- trees ----------------
        tree("duskoak").in(ROOTWILD).light(LightPref.DIM).abundance(0.45)
                .blocks("duskoak_log", "duskoak_leaves", "duskoak_sapling").yields("duskoak_log");
        tree("spirebark").in(ROOTWILD, HOLLOW_PEAKS).light(LightPref.DIM).abundance(0.25)
                .blocks("spirebark_log", "spirebark_leaves", "spirebark_sapling").yields("spirebark_log");
        tree("goldbough").in(GOLDEN_GROVE).light(LightPref.BRIGHT).abundance(0.4)
                .roles(Role.SHELTER, Role.NEST_SITE, Role.FORAGE, Role.LIGHT_SOURCE)
                .blocks("goldwood_log", "golden_leaves", "goldwood_sapling", "resin_knot").yields("goldwood_log");
        tree("mirewillow").in(EMERALD_WETLANDS, FLOODED_DEPTHS).water(WaterNeed.MOIST).abundance(0.35)
                .blocks("mirewillow_log", "mirewillow_leaves", "mirewillow_sapling").yields("mirewillow_log");
        tree("frostfir").in(EVERFROST).climate(Climate.COLD).abundance(0.3)
                .blocks("frostfir_log", "frostfir_leaves", "frostfir_sapling").yields("frostfir_log");
        tree("bloodthorn").in(CRIMSON_BASIN).climate(Climate.HOT).light(LightPref.DARK).abundance(0.25)
                .roles(Role.SHELTER, Role.HAZARD).blocks("bloodthorn_log", "bloodthorn_leaves", "bloodthorn_sapling")
                .yields("bloodthorn_log");
        tree("lumenwood").in(STARLIGHT_CAVERNS).abundance(0.25)
                .roles(Role.SHELTER, Role.NEST_SITE, Role.LIGHT_SOURCE)
                .blocks("lumenwood_log", "lumenwood_leaves", "lumenwood_sapling").yields("lumenwood_log");
        tree("cragpine").in(HOLLOW_PEAKS).rarity(UNCOMMON).climate(Climate.COLD).abundance(0.15)
                .blocks("cragpine_leaves", "cragpine_sapling").yields("spirebark_log");
        tree("cinderbark").in(ASHEN_HOLLOWS).rarity(UNCOMMON).climate(Climate.HOT).light(LightPref.DARK).abundance(0.08)
                .roles(Role.SHELTER).blocks("cinderbark_log", "cinder_leaves", "cinderbark_sapling").yields("cinderbark_log");

        // ---------------- terrestrial plants ----------------
        plant("duskberry", Kind.SHRUB).in(ROOTWILD).edible(Edibility.EDIBLE).light(LightPref.DIM)
                .roles(Role.FRUIT, Role.SHELTER).reproduces(Reproduction.CUTTINGS).abundance(0.2)
                .blocks("duskberry_bush").yields("duskberries");
        plant("glowfern", Kind.FERN).in(ROOTWILD, EMERALD_WETLANDS).light(LightPref.DIM)
                .roles(Role.FORAGE, Role.SHELTER, Role.LIGHT_SOURCE).reproduces(Reproduction.SPREADING).abundance(0.35)
                .blocks("glowfern");
        plant("mendleaf", Kind.HERB).in(ROOTWILD).rarity(UNCOMMON).reproduces(Reproduction.CUTTINGS).abundance(0.08)
                .roles(Role.FORAGE).blocks("mendleaf").yields("mendleaf_sprig");
        plant("glowivy", Kind.VINE).in(ROOTWILD, GOLDEN_GROVE).roles(Role.SHELTER, Role.LIGHT_SOURCE)
                .reproduces(Reproduction.SPREADING).abundance(0.15).blocks("glowivy");
        plant("honeyfruit", Kind.SHRUB).in(GOLDEN_GROVE).light(LightPref.BRIGHT).edible(Edibility.EDIBLE)
                .roles(Role.FRUIT, Role.NECTAR).reproduces(Reproduction.CUTTINGS).abundance(0.15)
                .blocks("honeyfruit_bush").yields("honeyfruit");
        plant("goldgrass", Kind.GRASS).in(GOLDEN_GROVE).light(LightPref.BRIGHT).roles(Role.FORAGE, Role.SEEDS)
                .reproduces(Reproduction.SPREADING).abundance(0.5).blocks("goldgrass");
        plant("sunleaf", Kind.HERB).in(GOLDEN_GROVE).rarity(RARE).light(LightPref.BRIGHT).roles(Role.NECTAR)
                .reproduces(Reproduction.CUTTINGS).abundance(0.03).blocks("sunleaf").yields("sunleaf_blossom");
        plant("ashgrass", Kind.GRASS).in(ASHEN_HOLLOWS).climate(Climate.HOT).roles(Role.FORAGE)
                .reproduces(Reproduction.SPREADING).abundance(0.2).blocks("ashgrass");
        plant("pyrelily", Kind.FLOWER).in(ASHEN_HOLLOWS).rarity(UNCOMMON).climate(Climate.HOT)
                .roles(Role.NECTAR, Role.LIGHT_SOURCE).reproduces(Reproduction.SEEDS).abundance(0.06).blocks("pyrelily");
        plant("emberwort", Kind.HERB).in(ASHEN_HOLLOWS, CRIMSON_BASIN).rarity(UNCOMMON).climate(Climate.HOT)
                .reproduces(Reproduction.CUTTINGS).abundance(0.05).blocks("emberwort").yields("emberwort_root");
        plant("shardgrass", Kind.GRASS).in(CRYSTAL_DEEP).roles(Role.MINERAL_FOOD, Role.LIGHT_SOURCE)
                .reproduces(Reproduction.SPREADING).abundance(0.25).blocks("shardgrass");
        plant("prism_lily", Kind.FLOWER).in(CRYSTAL_DEEP).rarity(RARE).roles(Role.NECTAR, Role.LIGHT_SOURCE)
                .reproduces(Reproduction.SEEDS).abundance(0.02).blocks("prism_lily");
        plant("ghostfern", Kind.FERN).in(CRYSTAL_DEEP, THE_ABYSS).light(LightPref.DARK).roles(Role.SHELTER)
                .reproduces(Reproduction.SPREADING).abundance(0.15).blocks("ghostfern");
        plant("voidroot", Kind.VINE).in(THE_ABYSS).light(LightPref.DARK).roles(Role.SHELTER, Role.NEST_SITE)
                .reproduces(Reproduction.SPREADING).abundance(0.12).blocks("voidroot", "voidroot_plant").yields("voidroot_fiber");
        plant("umbral_bloom", Kind.FLOWER).in(THE_ABYSS).rarity(EXCEPTIONAL).light(LightPref.DARK)
                .roles(Role.LIGHT_SOURCE).reproduces(Reproduction.SEEDS).abundance(0.005).blocks("umbral_bloom")
                .yields("umbral_petal");
        plant("frostbell", Kind.FLOWER).in(EVERFROST).climate(Climate.COLD).roles(Role.NECTAR, Role.LIGHT_SOURCE)
                .reproduces(Reproduction.SEEDS).abundance(0.15).blocks("frostbell");
        plant("snowberry", Kind.SHRUB).in(EVERFROST).climate(Climate.COLD).edible(Edibility.EDIBLE)
                .roles(Role.FRUIT, Role.SHELTER).reproduces(Reproduction.CUTTINGS).abundance(0.1)
                .blocks("snowberry_bush").yields("snowberries");
        plant("steamfern", Kind.FERN).in(EVERFROST).rarity(UNCOMMON).roles(Role.FORAGE, Role.SHELTER)
                .reproduces(Reproduction.SPREADING).abundance(0.04).blocks("steamfern");
        plant("cliffberry", Kind.SHRUB).in(HOLLOW_PEAKS).edible(Edibility.EDIBLE).roles(Role.FRUIT, Role.NEST_SITE)
                .reproduces(Reproduction.CUTTINGS).abundance(0.1).blocks("cliffberry_bush").yields("cliffberries");
        plant("peakstar", Kind.FLOWER).in(HOLLOW_PEAKS).rarity(RARE).climate(Climate.COLD).roles(Role.NECTAR)
                .reproduces(Reproduction.SEEDS).abundance(0.02).blocks("peakstar");
        plant("ledgevine", Kind.VINE).in(HOLLOW_PEAKS).roles(Role.FORAGE, Role.SHELTER, Role.NEST_SITE)
                .reproduces(Reproduction.SPREADING).abundance(0.12).blocks("ledgevine", "ledgevine_plant");
        plant("bogbloom", Kind.FLOWER).in(EMERALD_WETLANDS).water(WaterNeed.MOIST).roles(Role.NECTAR, Role.LIGHT_SOURCE)
                .reproduces(Reproduction.SEEDS).abundance(0.15).blocks("bogbloom");
        plant("marsh_fern", Kind.FERN).in(EMERALD_WETLANDS, FLOODED_DEPTHS).water(WaterNeed.MOIST)
                .roles(Role.FORAGE, Role.SHELTER).reproduces(Reproduction.SPREADING).abundance(0.35).blocks("marsh_fern");
        plant("bogwort", Kind.HERB).in(EMERALD_WETLANDS).rarity(UNCOMMON).water(WaterNeed.MOIST)
                .reproduces(Reproduction.CUTTINGS).abundance(0.05).blocks("bogwort").yields("bogwort_leaf");
        plant("stoneroot", Kind.VINE).in(PETRIFIED_FOREST).roles(Role.SHELTER, Role.MINERAL_FOOD)
                .reproduces(Reproduction.SPREADING).abundance(0.15).blocks("stoneroot");
        plant("petrolichen", Kind.VINE).in(PETRIFIED_FOREST).roles(Role.FORAGE, Role.MINERAL_FOOD)
                .reproduces(Reproduction.SPREADING).abundance(0.2).blocks("petrolichen");
        plant("thornbriar", Kind.SHRUB).in(CRIMSON_BASIN).climate(Climate.HOT).edible(Edibility.EDIBLE)
                .roles(Role.FRUIT, Role.SHELTER, Role.HAZARD).reproduces(Reproduction.CUTTINGS).abundance(0.15)
                .blocks("thornbriar").yields("briar_fruit");
        plant("plaguebloom", Kind.FLOWER).in(CRIMSON_BASIN).edible(Edibility.TOXIC).roles(Role.HAZARD, Role.NECTAR)
                .reproduces(Reproduction.SEEDS).abundance(0.1).blocks("plaguebloom");
        plant("snapjaw", Kind.FLOWER).in(CRIMSON_BASIN).rarity(UNCOMMON).roles(Role.HAZARD)
                .reproduces(Reproduction.SEEDS).abundance(0.04).blocks("snapjaw").yields("snapjaw_fang");
        plant("static_grass", Kind.GRASS).in(STORMSTONE_CAVERNS).roles(Role.FORAGE, Role.LIGHT_SOURCE)
                .reproduces(Reproduction.SPREADING).abundance(0.2).blocks("static_grass");
        plant("voltbloom", Kind.FLOWER).in(STORMSTONE_CAVERNS).rarity(UNCOMMON).roles(Role.NECTAR, Role.LIGHT_SOURCE)
                .reproduces(Reproduction.SEEDS).abundance(0.05).blocks("voltbloom");
        plant("storm_lichen", Kind.VINE).in(STORMSTONE_CAVERNS).light(LightPref.DARK)
                .roles(Role.MINERAL_FOOD, Role.LIGHT_SOURCE).reproduces(Reproduction.SPREADING).abundance(0.15)
                .blocks("storm_lichen");
        plant("silvergrass", Kind.GRASS).in(STARLIGHT_CAVERNS).light(LightPref.BRIGHT)
                .roles(Role.FORAGE, Role.SEEDS, Role.LIGHT_SOURCE).reproduces(Reproduction.SPREADING).abundance(0.45)
                .blocks("silvergrass");
        plant("moonpetal", Kind.HERB).in(STARLIGHT_CAVERNS).rarity(RARE).roles(Role.NECTAR, Role.LIGHT_SOURCE)
                .reproduces(Reproduction.CUTTINGS).abundance(0.03).blocks("moonpetal").yields("moonpetal_petal");
        plant("starstrand", Kind.VINE).in(STARLIGHT_CAVERNS).roles(Role.LIGHT_SOURCE, Role.NEST_SITE)
                .reproduces(Reproduction.SPREADING).abundance(0.12).blocks("starstrand", "starstrand_plant");

        // ---------------- fungi ----------------
        plant("puffcap", Kind.FUNGUS).in(FUNGAL_EXPANSE, ROOTWILD).light(LightPref.DIM).edible(Edibility.EDIBLE)
                .roles(Role.FUNGAL_FOOD).reproduces(Reproduction.SPORES).abundance(0.3).blocks("puffcap");
        plant("inkcap", Kind.FUNGUS).in(FUNGAL_EXPANSE, ROOTWILD).light(LightPref.DARK).edible(Edibility.EDIBLE_COOKED)
                .roles(Role.FUNGAL_FOOD).reproduces(Reproduction.SPORES).abundance(0.2).blocks("inkcap");
        plant("deathbell", Kind.FUNGUS).in(FUNGAL_EXPANSE).rarity(UNCOMMON).light(LightPref.DARK).edible(Edibility.TOXIC)
                .roles(Role.HAZARD).reproduces(Reproduction.SPORES).abundance(0.08).blocks("deathbell");
        plant("lanterncap", Kind.FUNGUS).in(FUNGAL_EXPANSE).roles(Role.FUNGAL_FOOD, Role.LIGHT_SOURCE)
                .reproduces(Reproduction.SPORES).abundance(0.25).blocks("lanterncap");
        plant("sporepuff", Kind.FUNGUS).in(FUNGAL_EXPANSE).rarity(UNCOMMON).roles(Role.FUNGAL_FOOD)
                .reproduces(Reproduction.SPORES).abundance(0.06).blocks("sporepuff");
        plant("sagecap", Kind.FUNGUS).in(FUNGAL_EXPANSE, ROOTWILD).rarity(RARE).light(LightPref.DARK)
                .roles(Role.FUNGAL_FOOD).reproduces(Reproduction.SPORES).abundance(0.02).blocks("sagecap");
        plant("bracket_fungus", Kind.FUNGUS).in(ROOTWILD, FUNGAL_EXPANSE, EMERALD_WETLANDS, GOLDEN_GROVE)
                .roles(Role.FUNGAL_FOOD, Role.NEST_SITE).reproduces(Reproduction.SPORES).abundance(0.1)
                .blocks("bracket_fungus", "rotting_log");
        plant("embercap", Kind.FUNGUS).in(ASHEN_HOLLOWS).rarity(UNCOMMON).climate(Climate.HOT)
                .roles(Role.FUNGAL_FOOD, Role.LIGHT_SOURCE).reproduces(Reproduction.SPORES).abundance(0.05).blocks("embercap");
        plant("rimecap", Kind.FUNGUS).in(EVERFROST).climate(Climate.COLD).edible(Edibility.EDIBLE)
                .roles(Role.FUNGAL_FOOD).reproduces(Reproduction.SPORES).abundance(0.1).blocks("rimecap");
        plant("gloomcap", Kind.FUNGUS).in(THE_ABYSS).light(LightPref.DARK).rarity(UNCOMMON)
                .roles(Role.FUNGAL_FOOD, Role.LIGHT_SOURCE).reproduces(Reproduction.SPORES).abundance(0.06).blocks("gloomcap");
        plant("calcicap", Kind.FUNGUS).in(PETRIFIED_FOREST, CRYSTAL_DEEP).roles(Role.FUNGAL_FOOD, Role.MINERAL_FOOD)
                .reproduces(Reproduction.SPORES).abundance(0.15).blocks("calcicap");
        plant("bloodcap", Kind.FUNGUS).in(CRIMSON_BASIN).climate(Climate.HOT).edible(Edibility.TOXIC)
                .roles(Role.FUNGAL_FOOD, Role.HAZARD).reproduces(Reproduction.SPORES).abundance(0.12).blocks("bloodcap");
        plant("tidecap", Kind.FUNGUS).in(SUNKEN_GARDENS).water(WaterNeed.SUBMERGED)
                .roles(Role.FUNGAL_FOOD, Role.AQUATIC_FORAGE, Role.LIGHT_SOURCE).reproduces(Reproduction.SPORES)
                .abundance(0.12).blocks("tidecap");
        plant("sporestalk", Kind.GIANT_FUNGUS).in(FUNGAL_EXPANSE).roles(Role.SHELTER, Role.NEST_SITE, Role.LIGHT_SOURCE)
                .reproduces(Reproduction.SPORES).abundance(0.2).blocks("sporestalk_cap", "fungal_stem", "glowgill");
        plant("bellcap", Kind.GIANT_FUNGUS).in(FUNGAL_EXPANSE).roles(Role.SHELTER, Role.FUNGAL_FOOD)
                .reproduces(Reproduction.SPORES).abundance(0.15).blocks("bellcap_cap");
        plant("shelfcap", Kind.GIANT_FUNGUS).in(FUNGAL_EXPANSE).rarity(UNCOMMON).roles(Role.SHELTER, Role.NEST_SITE)
                .reproduces(Reproduction.SPORES).abundance(0.08).blocks("shelfcap_cap");

        // ---------------- aquatic ----------------
        plant("glowpad", Kind.AQUATIC).in(FLOODED_DEPTHS, EMERALD_WETLANDS).water(WaterNeed.FLOATING)
                .roles(Role.AQUATIC_FORAGE, Role.SHELTER, Role.LIGHT_SOURCE).reproduces(Reproduction.SPREADING)
                .abundance(0.25).blocks("glowpad");
        plant("starlily", Kind.AQUATIC).in(STARLIGHT_CAVERNS).water(WaterNeed.FLOATING).roles(Role.NECTAR, Role.LIGHT_SOURCE)
                .reproduces(Reproduction.SPREADING).abundance(0.15).blocks("starlily");
        plant("lakebloom", Kind.AQUATIC).in(FLOODED_DEPTHS).water(WaterNeed.SUBMERGED)
                .roles(Role.AQUATIC_FORAGE, Role.LIGHT_SOURCE).reproduces(Reproduction.SEEDS).abundance(0.15)
                .blocks("lakebloom");
        plant("ribbonweed", Kind.AQUATIC).in(FLOODED_DEPTHS, EMERALD_WETLANDS).water(WaterNeed.SUBMERGED)
                .roles(Role.AQUATIC_FORAGE, Role.SHELTER, Role.NEST_SITE).reproduces(Reproduction.SPREADING)
                .abundance(0.3).blocks("ribbonweed", "ribbonweed_plant");
        plant("glowkelp", Kind.AQUATIC).in(SUNKEN_GARDENS).water(WaterNeed.SUBMERGED)
                .roles(Role.AQUATIC_FORAGE, Role.SHELTER, Role.LIGHT_SOURCE).reproduces(Reproduction.SPREADING)
                .abundance(0.35).blocks("glowkelp", "glowkelp_plant");
        plant("tidebloom", Kind.AQUATIC).in(SUNKEN_GARDENS).water(WaterNeed.SUBMERGED)
                .roles(Role.NECTAR, Role.LIGHT_SOURCE).reproduces(Reproduction.SEEDS).abundance(0.12).blocks("tidebloom");
        plant("lanterncoral", Kind.CORAL).in(SUNKEN_GARDENS).water(WaterNeed.SUBMERGED)
                .roles(Role.SHELTER, Role.NEST_SITE, Role.LIGHT_SOURCE).reproduces(Reproduction.BUDDING).abundance(0.2)
                .blocks("lanterncoral", "lanterncoral_fan");
        plant("spirecoral", Kind.CORAL).in(SUNKEN_GARDENS).water(WaterNeed.SUBMERGED).roles(Role.SHELTER, Role.AQUATIC_FORAGE)
                .reproduces(Reproduction.BUDDING).abundance(0.2).blocks("spirecoral");
        plant("sea_grapes", Kind.AQUATIC).in(SUNKEN_GARDENS, FLOODED_DEPTHS).water(WaterNeed.SUBMERGED)
                .edible(Edibility.EDIBLE).roles(Role.FRUIT, Role.AQUATIC_FORAGE).reproduces(Reproduction.CUTTINGS)
                .abundance(0.1).blocks("sea_grape_bush").yields("sea_grapes");
        plant("siltwort", Kind.AQUATIC).in(FLOODED_DEPTHS).rarity(RARE).water(WaterNeed.SUBMERGED)
                .reproduces(Reproduction.CUTTINGS).abundance(0.03).blocks("siltwort").yields("siltwort_frond");

        // ---------------- crops (wild relatives grow naturally) ----------------
        plant("duskroot", Kind.CROP).in(ROOTWILD).edible(Edibility.EDIBLE).roles(Role.FORAGE)
                .reproduces(Reproduction.SEEDS).abundance(0.05).blocks("duskroots").yields("duskroot");
        plant("glowgrain", Kind.CROP).in(STARLIGHT_CAVERNS, GOLDEN_GROVE).light(LightPref.BRIGHT)
                .roles(Role.SEEDS, Role.FORAGE).reproduces(Reproduction.SEEDS).abundance(0.05).blocks("glowgrain_crop")
                .yields("glowgrain");
        plant("cave_truffle", Kind.CROP).in(ROOTWILD, FUNGAL_EXPANSE).light(LightPref.DARK).edible(Edibility.EDIBLE)
                .roles(Role.FUNGAL_FOOD).reproduces(Reproduction.SEEDS).abundance(0.04).blocks("cave_truffles")
                .yields("cave_truffle");
        plant("mire_rice", Kind.CROP).in(EMERALD_WETLANDS).water(WaterNeed.MOIST).roles(Role.SEEDS)
                .reproduces(Reproduction.SEEDS).abundance(0.06).blocks("mire_rice_crop").yields("mire_rice");
        plant("lake_chestnut", Kind.CROP).in(FLOODED_DEPTHS).water(WaterNeed.SUBMERGED).edible(Edibility.EDIBLE)
                .roles(Role.AQUATIC_FORAGE).reproduces(Reproduction.SEEDS).abundance(0.05).blocks("lake_chestnuts")
                .yields("lake_chestnut");
        plant("emberpepper", Kind.CROP).in(ASHEN_HOLLOWS).climate(Climate.HOT).edible(Edibility.EDIBLE)
                .reproduces(Reproduction.SEEDS).abundance(0.03).blocks("emberpeppers").yields("emberpepper");
        plant("frost_turnip", Kind.CROP).in(EVERFROST).climate(Climate.COLD).edible(Edibility.EDIBLE)
                .roles(Role.FORAGE).reproduces(Reproduction.SEEDS).abundance(0.04).blocks("frost_turnips")
                .yields("frost_turnip");
        plant("sunbean", Kind.CROP).in(GOLDEN_GROVE).light(LightPref.BRIGHT).edible(Edibility.EDIBLE_COOKED)
                .roles(Role.SEEDS).reproduces(Reproduction.SEEDS).abundance(0.04).blocks("sunbeans").yields("sunbean");
        plant("relic_sprout", Kind.CROP).in(PETRIFIED_FOREST).rarity(EXCEPTIONAL).edible(Edibility.EDIBLE)
                .reproduces(Reproduction.SEEDS).abundance(0.0).blocks("relic_sprout").yields("relic_fruit");

        // ---------------- minerals & crystals ----------------
        mineral("cinnabar", 0.25).in(ASHEN_HOLLOWS, CRIMSON_BASIN).blocks("cinnabar_ore").yields("cinnabar");
        mineral("sulfur", 0.35).in(ASHEN_HOLLOWS, CRIMSON_BASIN, THE_ABYSS).blocks("sulfur_ore").yields("sulfur");
        mineral("niter", 0.3).in(FUNGAL_EXPANSE, ROOTWILD, PETRIFIED_FOREST).blocks("niter_ore").yields("niter");
        mineral("emberite", 0.12).in(ASHEN_HOLLOWS).rarity(UNCOMMON).blocks("emberite_ore").yields("emberite");
        mineral("cryolite", 0.3).in(EVERFROST).blocks("cryolite_ore").yields("cryolite");
        mineral("malachite", 0.3).in(HOLLOW_PEAKS).blocks("malachite_ore").yields("malachite");
        mineral("bog_iron", 0.3).in(EMERALD_WETLANDS, FLOODED_DEPTHS).blocks("bog_iron_ore").yields("minecraft:raw_iron");
        mineral("galvanite", 0.25).in(STORMSTONE_CAVERNS).blocks("galvanite_ore").yields("galvanite");
        mineral("prismatic_opal", 0.03).in(CRYSTAL_DEEP).rarity(RARE).blocks("prismatic_opal_ore").yields("prismatic_opal");
        mineral("jasper", 0.25).in(PETRIFIED_FOREST).blocks("jasper_ore").yields("jasper");
        mineral("fossil", 0.2).in(PETRIFIED_FOREST, THE_ABYSS).rarity(UNCOMMON).blocks("fossil_ore").yields("fossil_fragment");
        mineral("umbralite", 0.05).in(THE_ABYSS).rarity(RARE).blocks("umbralite_ore").yields("raw_umbralite");
        mineral("nacre", 0.15).in(SUNKEN_GARDENS, FLOODED_DEPTHS).rarity(UNCOMMON).blocks("nacre_ore").yields("pearl");
        mineral("starsilver", 0.1).in(STARLIGHT_CAVERNS).rarity(UNCOMMON).blocks("starsilver_ore").yields("raw_starsilver");
        add(Species.of("glimmer_quartz", Kind.CRYSTAL).in(CRYSTAL_DEEP).abundance(0.15).reproduces(Reproduction.BUDDING)
                .roles(Role.LIGHT_SOURCE, Role.MINERAL_FOOD)
                .blocks("budding_glimmer", "glimmer_bud", "glimmer_cluster").yields("glimmer_shard"));
        add(Species.of("storm_crystal", Kind.CRYSTAL).in(STORMSTONE_CAVERNS).abundance(0.2).reproduces(Reproduction.BUDDING)
                .roles(Role.LIGHT_SOURCE, Role.MINERAL_FOOD).blocks("storm_crystal", "charged_stormstone"));
    }

    public static List<Species> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Species byId(String id) {
        return BY_ID.get(id);
    }

    /** Species represented by an underdeep block path (e.g. "duskoak_leaves"), or null. */
    public static Species byBlock(String blockPath) {
        return BY_BLOCK.get(blockPath);
    }

    public static List<Species> inRegion(dev.underdeep.world.region.RegionType type) {
        return BY_REGION.getOrDefault(type, List.of());
    }

    public static long count(Kind... kinds) {
        java.util.Set<Kind> set = java.util.EnumSet.noneOf(Kind.class);
        Collections.addAll(set, kinds);
        return ALL.stream().filter(s -> set.contains(s.kind())).count();
    }
}
