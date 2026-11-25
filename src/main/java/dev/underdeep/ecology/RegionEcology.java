package dev.underdeep.ecology;

import dev.underdeep.ecology.Species.Kind;
import dev.underdeep.ecology.Species.Role;
import dev.underdeep.world.region.Danger;
import dev.underdeep.world.region.RegionEnvironment;
import dev.underdeep.world.region.RegionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic ecological summary of one region instance (Stage 2 contract for Stage 3+).
 * <p>
 * Computed from the region type's species list, its {@link RegionEnvironment} and a small stable
 * per-region variation derived from the packed region id, so the same region always reports the same
 * values without storing anything. Dynamic pressure (harvesting, later hunting and settlements) is
 * kept separately in {@link EcologyState}. Never stores per-block information.
 *
 * @param region          region type
 * @param regionId        packed {@code RegionId} (0 when unknown)
 * @param dominant        up to three most abundant biological species
 * @param diversity       number of biological species native to the region type
 * @param habitats        habitat categories present
 * @param foodAvailability 0..1 forage/fruit/seed/fungal food supply for wildlife
 * @param waterAvailability 0..1
 * @param renewableAbundance 0..1 renewable biological resource supply
 * @param productivity    0..1 biological productivity (fertility, light, food)
 * @param danger          environmental danger class
 * @param wildlife        wildlife guilds the region can support
 * @param minerals        regional mineral resources (ids)
 */
public record RegionEcology(RegionType region, long regionId, List<Species> dominant, int diversity,
                            Set<Habitat> habitats, double foodAvailability, double waterAvailability,
                            double renewableAbundance, double productivity, Danger danger,
                            Set<WildlifeGuild> wildlife, List<String> minerals) {

    public enum Habitat { FOREST, MEADOW, FUNGAL_GROVE, WETLAND, OPEN_WATER, SUBMERGED, VOLCANIC, CRYSTALLINE, FROZEN, CLIFF, BARREN, DARK_DEPTHS }

    /** Broad animal guilds for Stage 3 population models. */
    public enum WildlifeGuild { GRAZERS, BROWSERS, FRUGIVORES, SEED_EATERS, POLLINATORS, FUNGIVORES, AQUATIC_GRAZERS, AMPHIBIANS, NESTERS, LITHOVORES, PREDATORS, AMBUSH_PREDATORS, SCAVENGERS, DARK_ADAPTED }

    public static RegionEcology of(RegionType type, long regionId) {
        List<Species> native_ = EcologyRegistry.inRegion(type);
        double variation = 0.85 + 0.3 * unit(regionId * 0x9E3779B97F4A7C15L + type.ordinal());
        RegionEnvironment env = type.environment();

        double food = 0, renewable = 0, nectar = 0, fungal = 0, aquatic = 0, nest = 0, mineralFood = 0, grass = 0, shrubs = 0;
        int diversity = 0;
        List<String> minerals = new ArrayList<>();
        for (Species s : native_) {
            if (!s.isBiological()) {
                minerals.add(s.id());
                if (s.has(Role.MINERAL_FOOD)) {
                    mineralFood += s.abundance();
                }
                continue;
            }
            diversity++;
            double a = s.abundance();
            if (s.has(Role.FORAGE) || s.has(Role.FRUIT) || s.has(Role.SEEDS) || s.has(Role.FUNGAL_FOOD) || s.has(Role.AQUATIC_FORAGE)) {
                food += a;
            }
            if (s.renewable()) {
                renewable += a;
            }
            if (s.has(Role.NECTAR)) nectar += a;
            if (s.has(Role.FUNGAL_FOOD)) fungal += a;
            if (s.has(Role.AQUATIC_FORAGE)) aquatic += a;
            if (s.has(Role.NEST_SITE)) nest += a;
            if (s.has(Role.MINERAL_FOOD)) mineralFood += a;
            if (s.kind() == Kind.GRASS || s.kind() == Kind.FERN) grass += a;
            if (s.kind() == Kind.SHRUB || s.kind() == Kind.TREE) shrubs += a;
        }
        double foodN = clamp(food / 2.5 * variation);
        double renewN = clamp(renewable / 3.0 * variation);
        double lightFactor = switch (type.lighting()) {
            case BRIGHT -> 1.0;
            case MODERATE -> 0.75;
            case DARK -> 0.5;
            case PITCH -> 0.25;
        };
        double productivity = clamp((0.45 * env.fertility() + 0.35 * foodN + 0.2 * env.waterAvailability()) * lightFactor * variation);

        Set<Habitat> habitats = EnumSet.noneOf(Habitat.class);
        Set<String> tags = env.habitatTags();
        if (native_.stream().anyMatch(s -> s.kind() == Kind.TREE)) habitats.add(Habitat.FOREST);
        if (grass > 0.3) habitats.add(Habitat.MEADOW);
        if (fungal > 0.5 || tags.contains("fungal")) habitats.add(Habitat.FUNGAL_GROVE);
        if (tags.contains("marsh") || tags.contains("wetland") || tags.contains("shore")) habitats.add(Habitat.WETLAND);
        if (tags.contains("lake") || env.waterAvailability() >= 0.95) habitats.add(Habitat.OPEN_WATER);
        if (aquatic > 0.3) habitats.add(Habitat.SUBMERGED);
        if (tags.contains("volcanic") || env.temperature() > 0.75) habitats.add(Habitat.VOLCANIC);
        if (tags.contains("crystal") || type == RegionType.STORMSTONE_CAVERNS) habitats.add(Habitat.CRYSTALLINE);
        if (tags.contains("frozen") || env.temperature() < 0.1) habitats.add(Habitat.FROZEN);
        if (tags.contains("cliff") || tags.contains("mountain")) habitats.add(Habitat.CLIFF);
        if (foodN < 0.2) habitats.add(Habitat.BARREN);
        if (type.lighting() == dev.underdeep.world.region.Lighting.PITCH) habitats.add(Habitat.DARK_DEPTHS);

        Set<WildlifeGuild> guilds = EnumSet.noneOf(WildlifeGuild.class);
        if (grass > 0.3) guilds.add(WildlifeGuild.GRAZERS);
        if (shrubs > 0.4) guilds.add(WildlifeGuild.BROWSERS);
        if (native_.stream().anyMatch(s -> s.has(Role.FRUIT))) guilds.add(WildlifeGuild.FRUGIVORES);
        if (native_.stream().anyMatch(s -> s.has(Role.SEEDS))) guilds.add(WildlifeGuild.SEED_EATERS);
        if (nectar > 0.1) guilds.add(WildlifeGuild.POLLINATORS);
        if (fungal > 0.2) guilds.add(WildlifeGuild.FUNGIVORES);
        if (aquatic > 0.2) guilds.add(WildlifeGuild.AQUATIC_GRAZERS);
        if (habitats.contains(Habitat.WETLAND) || (habitats.contains(Habitat.OPEN_WATER) && grass > 0.2)) guilds.add(WildlifeGuild.AMPHIBIANS);
        if (nest > 0.3) guilds.add(WildlifeGuild.NESTERS);
        if (mineralFood > 0.2) guilds.add(WildlifeGuild.LITHOVORES);
        int preyGuilds = 0;
        for (WildlifeGuild g : guilds) {
            if (g != WildlifeGuild.LITHOVORES) preyGuilds++;
        }
        if (preyGuilds >= 3) guilds.add(WildlifeGuild.PREDATORS);
        if (type.danger().ordinal() >= Danger.HIGH.ordinal() || native_.stream().anyMatch(s -> s.has(Role.HAZARD))) guilds.add(WildlifeGuild.AMBUSH_PREDATORS);
        if (type.danger().ordinal() >= Danger.HIGH.ordinal()) guilds.add(WildlifeGuild.SCAVENGERS);
        if (type.lighting().ordinal() >= dev.underdeep.world.region.Lighting.DARK.ordinal()) guilds.add(WildlifeGuild.DARK_ADAPTED);

        List<Species> dominant = native_.stream().filter(Species::isBiological)
                .sorted(Comparator.comparingDouble((Species x) -> -x.abundance()).thenComparing(Species::id))
                .limit(3).toList();
        return new RegionEcology(type, regionId, dominant, diversity, habitats, foodN, env.waterAvailability(),
                renewN, productivity, type.danger(), guilds, minerals);
    }

    public String describe() {
        return String.format(Locale.ROOT,
                "%s: dominant %s; %d species; food %.2f, water %.2f, renewable %.2f, productivity %.2f; danger %s; habitats %s; wildlife %s; minerals %s",
                region.id(), dominant, diversity, foodAvailability, waterAvailability, renewableAbundance, productivity,
                danger, habitats, wildlife, minerals);
    }

    private static double unit(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
