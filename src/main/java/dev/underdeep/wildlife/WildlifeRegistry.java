package dev.underdeep.wildlife;

import dev.underdeep.Underdeep;
import dev.underdeep.wildlife.CreatureSpecies.Activity;
import dev.underdeep.wildlife.CreatureSpecies.Diet;
import dev.underdeep.wildlife.CreatureSpecies.HuntStyle;
import dev.underdeep.wildlife.CreatureSpecies.Locomotion;
import dev.underdeep.wildlife.CreatureSpecies.Mount;
import dev.underdeep.wildlife.CreatureSpecies.Nest;
import dev.underdeep.wildlife.CreatureSpecies.Social;
import dev.underdeep.wildlife.CreatureSpecies.TameMethod;
import dev.underdeep.wildlife.CreatureSpecies.Temperament;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.underdeep.ecology.Species.Rarity.EXCEPTIONAL;
import static dev.underdeep.ecology.Species.Rarity.RARE;
import static dev.underdeep.ecology.Species.Rarity.UNCOMMON;
import static dev.underdeep.world.region.RegionType.*;

/**
 * All Stage 3 wildlife species and their entity types. Every species uses {@link UnderdeepCreature};
 * behaviour differs through the species fields (diet, social structure, temperament, hunting style,
 * activity cycle, locomotion, taming, mounting, nesting). Rare and exceptional species are documented
 * only in docs/private/.
 */
public final class WildlifeRegistry {
    private static final Map<String, CreatureSpecies> BY_ID = new LinkedHashMap<>();
    private static final Map<EntityType<?>, CreatureSpecies> BY_TYPE = new HashMap<>();
    private static final Map<String, EntityType<UnderdeepCreature>> TYPES = new LinkedHashMap<>();

    private WildlifeRegistry() {
    }

    private static CreatureSpecies sp(String id) {
        CreatureSpecies s = new CreatureSpecies(id);
        BY_ID.put(id, s);
        return s;
    }

    static {
        // ---------------- Rootwild: peaceful inhabited forest ----------------
        sp("mossbuck").in(ROOTWILD).size(0.9f, 1.5f).stats(14, 0.27, 0).eats(Diet.GRAZER).group(Social.HERD, 3, 6)
                .temper(Temperament.SKITTISH).active(Activity.DAY).density(12).voice("goat", 1.25f).product("shed:horn");
        sp("thumperkit").in(ROOTWILD, GOLDEN_GROVE).size(0.5f, 0.5f).stats(4, 0.32, 0).eats(Diet.GRAZER).group(Social.COLONY, 2, 5)
                .temper(Temperament.SKITTISH).active(Activity.TWILIGHT).nest(Nest.BURROW, false).density(10).voice("rabbit", 1.0f);
        sp("rootglider").in(ROOTWILD).size(0.5f, 0.45f).stats(6, 0.28, 0).moves(Locomotion.GLIDER).eats(Diet.OMNIVORE)
                .group(Social.PAIR, 1, 2).temper(Temperament.CURIOUS).active(Activity.NIGHT).tame(TameMethod.FEEDING, "duskberries")
                .nest(Nest.TREE, false).density(4).voice("fox", 1.6f);
        sp("lumenmoth").in(ROOTWILD, EMERALD_WETLANDS).size(0.4f, 0.4f).stats(2, 0.3, 0).moves(Locomotion.HOVER).eats(Diet.NECTAR)
                .group(Social.SWARM, 3, 6).active(Activity.NIGHT).glowing().density(10).voice("bee", 1.4f);
        sp("tuskbriar").in(ROOTWILD).size(1.0f, 0.9f).stats(18, 0.26, 4).eats(Diet.OMNIVORE).group(Social.FAMILY, 2, 4)
                .temper(Temperament.TERRITORIAL).active(Activity.DAY).density(4).voice("hoglin", 1.25f);
        sp("shadecat").in(ROOTWILD, GOLDEN_GROVE).size(0.8f, 0.9f).stats(20, 0.32, 5).eats(Diet.CARNIVORE)
                .hunts(HuntStyle.STALK, "mossbuck", "thumperkit", "snufflet", "goldfleece").temper(Temperament.PREDATOR)
                .active(Activity.NIGHT).tame(TameMethod.RESCUE, "game_meat").density(1.2).voice("cat", 0.75f);
        sp("snufflet").in(ROOTWILD, FUNGAL_EXPANSE).size(0.6f, 0.6f).stats(8, 0.25, 0).eats(Diet.OMNIVORE).group(Social.FAMILY, 2, 3)
                .temper(Temperament.CURIOUS).active(Activity.DAY).tame(TameMethod.FEEDING, "cave_truffle").density(5).voice("pig", 1.5f);

        // ---------------- Fungal Expanse ----------------
        sp("capback").in(FUNGAL_EXPANSE).size(1.6f, 1.7f).stats(30, 0.2, 0).eats(Diet.FUNGIVORE).group(Social.HERD, 2, 4)
                .temper(Temperament.PASSIVE).tame(TameMethod.FEEDING, "puffcap").mount(Mount.PACK).product("milk")
                .density(6).voice("cow", 0.7f);
        sp("sporemite").in(FUNGAL_EXPANSE).size(0.4f, 0.3f).stats(3, 0.3, 0).eats(Diet.SCAVENGER).group(Social.COLONY, 3, 6)
                .active(Activity.BURSTS).density(10).voice("silverfish", 1.2f);
        sp("lanternbeetle").in(FUNGAL_EXPANSE).size(0.6f, 0.4f).stats(6, 0.2, 0).armor(4).eats(Diet.FUNGIVORE)
                .group(Social.PAIR, 1, 2).active(Activity.NIGHT).glowing().density(6).voice("armadillo", 1.3f).product("shed:glow_gland");
        sp("capstalker").in(FUNGAL_EXPANSE).size(1.0f, 1.4f).stats(22, 0.3, 5).eats(Diet.CARNIVORE)
                .hunts(HuntStyle.AMBUSH, "sporemite", "lanternbeetle", "puffling", "snufflet").temper(Temperament.PREDATOR)
                .density(1).voice("frog", 0.6f);
        sp("puffling").in(FUNGAL_EXPANSE).size(0.5f, 0.5f).stats(6, 0.24, 0).eats(Diet.FUNGIVORE).group(Social.COLONY, 2, 4)
                .temper(Temperament.CURIOUS).tame(TameMethod.FEEDING, "lanterncap").density(5).voice("allay", 1.3f);

        // ---------------- Flooded Depths ----------------
        sp("glimmerfin").in(FLOODED_DEPTHS, EMERALD_WETLANDS).size(0.5f, 0.4f).stats(3, 0.9, 0).moves(Locomotion.AQUATIC).water()
                .eats(Diet.FILTER).group(Social.SCHOOL, 4, 8).glowing().density(16).voice("fish", 1.2f);
        sp("newtling").in(FLOODED_DEPTHS, EMERALD_WETLANDS).size(0.5f, 0.3f).stats(5, 0.25, 0).moves(Locomotion.AMPHIBIOUS).water()
                .eats(Diet.OMNIVORE).group(Social.FAMILY, 2, 3).temper(Temperament.SKITTISH).nest(Nest.WATER, true).density(6)
                .voice("frog", 1.4f);
        sp("lakehorn").in(FLOODED_DEPTHS).size(1.8f, 1.2f).stats(40, 0.7, 0).moves(Locomotion.AQUATIC).water()
                .eats(Diet.AQUATIC_GRAZER).group(Social.FAMILY, 1, 3).tame(TameMethod.FEEDING, "lakebloom").mount(Mount.SWIMMER)
                .density(2.5).voice("dolphin", 0.6f);
        sp("ribbon_eel").in(FLOODED_DEPTHS, SUNKEN_GARDENS).size(0.6f, 0.5f).stats(14, 1.0, 4).moves(Locomotion.AQUATIC).water()
                .eats(Diet.CARNIVORE).hunts(HuntStyle.AQUATIC, "glimmerfin", "newtling", "reef_darter").temper(Temperament.PREDATOR)
                .active(Activity.NIGHT).density(2).voice("fish", 0.7f);
        sp("grand_drifter").in(FLOODED_DEPTHS, SUNKEN_GARDENS).size(4.0f, 2.5f).stats(200, 0.5, 0).moves(Locomotion.AQUATIC).water()
                .eats(Diet.FILTER).rarity(EXCEPTIONAL).density(0.04).voice("dolphin", 0.3f).product("shed:scale");

        // ---------------- Ashen Hollows ----------------
        sp("emberskink").in(ASHEN_HOLLOWS).size(0.7f, 0.4f).stats(10, 0.3, 2).lavaSwimmer().eats(Diet.LITHOVORE)
                .group(Social.PAIR, 1, 2).temper(Temperament.NEUTRAL).glowing().density(6).voice("strider", 1.5f);
        sp("ashshell").in(ASHEN_HOLLOWS, PETRIFIED_FOREST).size(1.2f, 0.9f).stats(30, 0.12, 0).armor(12).fireproof()
                .eats(Diet.LITHOVORE).group(Social.FAMILY, 1, 2).density(4).voice("turtle", 0.7f).product("shed:shell_fragment");
        sp("scorchjaw").in(ASHEN_HOLLOWS).size(0.9f, 1.6f).stats(24, 0.33, 6).fireproof().eats(Diet.CARNIVORE)
                .group(Social.PACK, 1, 3).hunts(HuntStyle.PACK, "emberskink", "ashshell").temper(Temperament.AMBUSH)
                .density(1.5).voice("ravager", 1.5f);
        sp("slag_titan").in(ASHEN_HOLLOWS).size(3.2f, 3.0f).stats(150, 0.14, 10).armor(16).fireproof().eats(Diet.LITHOVORE)
                .temper(Temperament.TERRITORIAL).rarity(RARE).density(0.08).voice("ravager", 0.5f).product("shed:shell_fragment");

        // ---------------- Crystal Deep ----------------
        sp("prismhorn").in(CRYSTAL_DEEP).size(0.9f, 1.2f).stats(18, 0.26, 3).armor(4).moves(Locomotion.CLIMBER).eats(Diet.LITHOVORE)
                .group(Social.HERD, 2, 4).temper(Temperament.NEUTRAL).glowing().density(6).voice("goat", 0.9f).product("shed:crystal_husk");
        sp("shardfly").in(CRYSTAL_DEEP, STORMSTONE_CAVERNS).size(0.4f, 0.3f).stats(2, 0.3, 0).moves(Locomotion.HOVER).eats(Diet.NECTAR)
                .group(Social.SWARM, 3, 6).glowing().density(10).voice("allay", 1.8f);
        sp("geode_crab").in(CRYSTAL_DEEP).size(0.8f, 0.5f).stats(12, 0.2, 2).armor(8).eats(Diet.LITHOVORE)
                .group(Social.COLONY, 2, 3).temper(Temperament.NEUTRAL).nest(Nest.CRYSTAL, false).density(5).voice("turtle", 1.4f)
                .product("shed:crystal_husk");
        sp("facet_mantis").in(CRYSTAL_DEEP).size(0.8f, 1.4f).stats(20, 0.3, 5).moves(Locomotion.CLIMBER).eats(Diet.CARNIVORE)
                .hunts(HuntStyle.AMBUSH, "shardfly", "geode_crab").temper(Temperament.TERRITORIAL).glowing().density(1.2)
                .voice("spider", 1.4f);

        // ---------------- The Abyss ----------------
        sp("echo_crawler").in(THE_ABYSS).size(0.9f, 0.5f).stats(12, 0.28, 3).moves(Locomotion.CLIMBER).eats(Diet.SCAVENGER)
                .group(Social.COLONY, 2, 4).temper(Temperament.NEUTRAL).density(6).voice("silverfish", 0.6f);
        sp("lure_angler").in(THE_ABYSS).size(1.2f, 1.0f).stats(26, 0.22, 7).eats(Diet.CARNIVORE)
                .hunts(HuntStyle.AMBUSH, "echo_crawler", "gloomwing").temper(Temperament.AMBUSH).glowing().density(1.5)
                .voice("frog", 0.4f);
        sp("gloomwing").in(THE_ABYSS, CRYSTAL_DEEP).size(0.6f, 0.6f).stats(6, 0.3, 0).moves(Locomotion.FLYER).eats(Diet.OMNIVORE)
                .group(Social.SWARM, 2, 5).temper(Temperament.SKITTISH).nest(Nest.LEDGE, false).density(8).voice("bat", 0.9f);
        sp("abyss_strider").in(THE_ABYSS).size(2.4f, 5.0f).stats(120, 0.18, 0).eats(Diet.SCAVENGER).rarity(RARE).density(0.1)
                .voice("warden", 1.3f);

        // ---------------- Everfrost ----------------
        sp("frostmane").in(EVERFROST).size(1.4f, 1.4f).stats(28, 0.22, 3).eats(Diet.GRAZER).group(Social.HERD, 3, 6)
                .temper(Temperament.NEUTRAL).density(10).voice("cow", 0.6f).product("shear:fur_tuft");
        sp("rimefox").in(EVERFROST).size(0.6f, 0.7f).stats(14, 0.33, 4).eats(Diet.CARNIVORE).group(Social.PACK, 2, 4)
                .hunts(HuntStyle.PACK, "glacier_skimmer", "frostmane").temper(Temperament.PREDATOR).active(Activity.TWILIGHT)
                .nest(Nest.BURROW, false).density(2.5).voice("fox", 0.9f);
        sp("glacier_skimmer").in(EVERFROST).size(0.5f, 0.3f).stats(4, 0.34, 0).eats(Diet.FUNGIVORE).group(Social.COLONY, 2, 4)
                .temper(Temperament.SKITTISH).density(6).voice("silverfish", 1.6f);

        // ---------------- Golden Grove ----------------
        sp("goldfleece").in(GOLDEN_GROVE).size(0.9f, 1.2f).stats(12, 0.24, 0).eats(Diet.GRAZER).group(Social.HERD, 3, 5)
                .density(10).voice("sheep", 1.1f).product("shear:fur_tuft");
        sp("amberwing").in(GOLDEN_GROVE, ROOTWILD).size(0.5f, 0.6f).stats(6, 0.3, 0).moves(Locomotion.FLYER).eats(Diet.BROWSER)
                .group(Social.FAMILY, 2, 4).temper(Temperament.CURIOUS).tame(TameMethod.FEEDING, "honeyfruit").nest(Nest.TREE, true)
                .density(8).voice("parrot", 1.0f).product("shed:plume");
        sp("bumblewisp").in(GOLDEN_GROVE).size(0.4f, 0.4f).stats(3, 0.3, 0).moves(Locomotion.HOVER).eats(Diet.NECTAR)
                .group(Social.SWARM, 3, 5).active(Activity.DAY).glowing().density(8).voice("bee", 1.0f);
        sp("glade_runner").in(GOLDEN_GROVE, STARLIGHT_CAVERNS).size(1.0f, 1.9f).stats(20, 0.35, 0).eats(Diet.BROWSER)
                .group(Social.FAMILY, 1, 3).temper(Temperament.SKITTISH).tame(TameMethod.FEEDING, "sunbean").mount(Mount.RUNNER)
                .nest(Nest.GROUND, true).density(4).voice("chicken", 0.55f);

        // ---------------- Hollow Peaks ----------------
        sp("cliffstrider").in(HOLLOW_PEAKS).size(0.9f, 1.4f).stats(20, 0.27, 2).moves(Locomotion.CLIMBER).eats(Diet.GRAZER)
                .group(Social.HERD, 2, 4).temper(Temperament.SKITTISH).tame(TameMethod.PATIENCE, "cliffberries").mount(Mount.CLIMBER)
                .density(6).voice("goat", 1.0f);
        sp("ledgewing").in(HOLLOW_PEAKS).size(1.2f, 0.8f).stats(16, 0.3, 3).moves(Locomotion.GLIDER).eats(Diet.CARNIVORE)
                .group(Social.PAIR, 1, 2).hunts(HuntStyle.SWOOP, "rockpip", "thumperkit").temper(Temperament.PREDATOR)
                .tame(TameMethod.RESCUE, "game_meat").mount(Mount.GLIDER).nest(Nest.LEDGE, true).density(2).voice("parrot", 0.6f);
        sp("rockpip").in(HOLLOW_PEAKS).size(0.5f, 0.5f).stats(6, 0.3, 0).moves(Locomotion.CLIMBER).eats(Diet.GRAZER)
                .group(Social.COLONY, 3, 5).temper(Temperament.SKITTISH).nest(Nest.BURROW, false).density(8).voice("rabbit", 0.8f);

        // ---------------- Emerald Wetlands ----------------
        sp("bogcroaker").in(EMERALD_WETLANDS).size(0.6f, 0.5f).stats(6, 0.25, 0).moves(Locomotion.AMPHIBIOUS).water()
                .eats(Diet.OMNIVORE).group(Social.FAMILY, 2, 4).nest(Nest.WATER, true).density(8).voice("frog", 0.8f);
        sp("stiltwader").in(EMERALD_WETLANDS, FLOODED_DEPTHS).size(0.6f, 1.6f).stats(10, 0.28, 3).eats(Diet.CARNIVORE)
                .group(Social.PAIR, 1, 2).hunts(HuntStyle.STALK, "glimmerfin", "newtling", "glassdart").temper(Temperament.SKITTISH)
                .nest(Nest.GROUND, true).density(3).voice("parrot", 0.5f).product("shed:plume");
        sp("rillpup").in(EMERALD_WETLANDS, FLOODED_DEPTHS).size(0.6f, 0.5f).stats(10, 0.3, 2).moves(Locomotion.AMPHIBIOUS).water()
                .eats(Diet.CARNIVORE).group(Social.FAMILY, 2, 4).hunts(HuntStyle.AQUATIC, "glimmerfin")
                .temper(Temperament.CURIOUS).tame(TameMethod.FEEDING, "fish_fillet").density(4).voice("fox", 1.3f);
        sp("glassdart").in(EMERALD_WETLANDS).size(0.4f, 0.3f).stats(2, 0.35, 0).moves(Locomotion.HOVER).eats(Diet.NECTAR)
                .group(Social.SWARM, 2, 4).active(Activity.DAY).density(8).voice("bee", 1.8f);

        // ---------------- Petrified Forest ----------------
        sp("stonehide").in(PETRIFIED_FOREST).size(0.7f, 0.5f).stats(14, 0.22, 0).armor(10).eats(Diet.LITHOVORE)
                .group(Social.PAIR, 1, 2).nest(Nest.BURROW, false).density(5).voice("armadillo", 1.0f).product("shed:shell_fragment");
        sp("dustbeak").in(PETRIFIED_FOREST, THE_ABYSS).size(0.8f, 0.9f).stats(12, 0.3, 3).moves(Locomotion.FLYER).eats(Diet.SCAVENGER)
                .group(Social.PAIR, 1, 2).temper(Temperament.NEUTRAL).nest(Nest.LEDGE, true).density(3).voice("parrot", 0.5f);
        sp("petrisloth").in(PETRIFIED_FOREST).size(1.0f, 1.1f).stats(24, 0.12, 0).armor(6).moves(Locomotion.CLIMBER)
                .eats(Diet.LITHOVORE).rarity(UNCOMMON).active(Activity.BURSTS).density(1.5).voice("panda", 0.6f);

        // ---------------- Crimson Basin ----------------
        sp("bloodhorn").in(CRIMSON_BASIN).size(1.0f, 1.3f).stats(22, 0.27, 5).eats(Diet.GRAZER).group(Social.HERD, 2, 4)
                .temper(Temperament.TERRITORIAL).density(6).voice("goat", 0.6f).product("shed:horn");
        sp("thornstalker").in(CRIMSON_BASIN).size(1.0f, 0.8f).stats(24, 0.3, 6).moves(Locomotion.CLIMBER).eats(Diet.CARNIVORE)
                .hunts(HuntStyle.STALK, "toxitoad", "bloodhorn").temper(Temperament.AMBUSH).density(1.5).voice("spider", 0.7f);
        sp("toxitoad").in(CRIMSON_BASIN).size(0.6f, 0.5f).stats(8, 0.24, 0).eats(Diet.BROWSER).group(Social.FAMILY, 1, 3)
                .temper(Temperament.NEUTRAL).density(6).voice("frog", 0.5f).product("shed:venom_gland");

        // ---------------- Sunken Gardens ----------------
        sp("reef_darter").in(SUNKEN_GARDENS).size(0.4f, 0.4f).stats(3, 1.0, 0).moves(Locomotion.AQUATIC).water().eats(Diet.AQUATIC_GRAZER)
                .group(Social.SCHOOL, 5, 9).density(20).voice("fish", 1.4f);
        sp("coral_crab").in(SUNKEN_GARDENS).size(0.7f, 0.4f).stats(10, 0.2, 2).armor(6).moves(Locomotion.AMPHIBIOUS).water()
                .eats(Diet.AQUATIC_GRAZER).group(Social.COLONY, 2, 3).temper(Temperament.NEUTRAL).density(6).voice("turtle", 1.5f);
        sp("veilray").in(SUNKEN_GARDENS, FLOODED_DEPTHS).size(2.2f, 0.5f).stats(30, 0.8, 0).moves(Locomotion.AQUATIC).water()
                .eats(Diet.FILTER).group(Social.PAIR, 1, 2).glowing().density(2.5).voice("dolphin", 0.8f);
        sp("stoneface").in(SUNKEN_GARDENS).size(0.7f, 0.5f).stats(12, 0.6, 4).moves(Locomotion.AQUATIC).water().eats(Diet.CARNIVORE)
                .hunts(HuntStyle.AMBUSH, "reef_darter").temper(Temperament.PREDATOR).density(2).voice("fish", 0.5f).product("shed:venom_gland");

        // ---------------- Stormstone Caverns ----------------
        sp("static_beetle").in(STORMSTONE_CAVERNS).size(0.7f, 0.5f).stats(10, 0.22, 2).armor(6).eats(Diet.LITHOVORE)
                .group(Social.COLONY, 2, 4).temper(Temperament.NEUTRAL).glowing().density(8).voice("armadillo", 1.4f);
        sp("arcback").in(STORMSTONE_CAVERNS).size(1.0f, 0.7f).stats(20, 0.26, 4).moves(Locomotion.CLIMBER).eats(Diet.LITHOVORE)
                .temper(Temperament.TERRITORIAL).glowing().density(3).voice("strider", 0.7f).product("shed:scale");
        sp("conduit_serpent").in(STORMSTONE_CAVERNS).size(0.8f, 0.6f).stats(40, 0.3, 5).eats(Diet.LITHOVORE).temper(Temperament.NEUTRAL)
                .glowing().rarity(RARE).density(0.15).voice("breeze", 0.7f);

        // ---------------- Starlight Caverns ----------------
        sp("starwisp").in(STARLIGHT_CAVERNS).size(0.4f, 0.4f).stats(2, 0.25, 0).moves(Locomotion.HOVER).eats(Diet.NECTAR)
                .group(Social.SWARM, 3, 6).glowing().density(12).voice("allay", 1.6f);
        sp("moonhorn").in(STARLIGHT_CAVERNS).size(0.9f, 1.7f).stats(18, 0.28, 0).eats(Diet.GRAZER).group(Social.HERD, 2, 4)
                .temper(Temperament.SKITTISH).glowing().rarity(UNCOMMON).density(3).voice("camel", 1.6f).product("shed:horn");
        sp("lumen_kit").in(STARLIGHT_CAVERNS, CRYSTAL_DEEP).size(0.6f, 0.6f).stats(10, 0.3, 2).eats(Diet.OMNIVORE).group(Social.FAMILY, 1, 3)
                .temper(Temperament.CURIOUS).tame(TameMethod.OFFERING, "glimmer_shard").glowing().density(3).voice("fox", 1.4f);
        sp("sky_drifter").in(STARLIGHT_CAVERNS).size(4.0f, 2.5f).stats(100, 0.12, 0).moves(Locomotion.HOVER).eats(Diet.NECTAR)
                .glowing().rarity(EXCEPTIONAL).density(0.05).voice("allay", 0.4f);
    }

    /** Registers entity types and attributes (common init). */
    public static void init() {
        for (CreatureSpecies s : BY_ID.values()) {
            SpawnGroup group = s.isAquatic() ? SpawnGroup.WATER_CREATURE : s.flies() && s.sizeClass < 1 ? SpawnGroup.AMBIENT : SpawnGroup.CREATURE;
            EntityType.Builder<UnderdeepCreature> b = EntityType.Builder.<UnderdeepCreature>create(UnderdeepCreature::new, group)
                    .dimensions(s.width, s.height).maxTrackingRange(s.sizeClass > 2 ? 12 : 8).trackingTickInterval(3);
            if (s.fireImmune) {
                b = b.makeFireImmune();
            }
            Identifier id = Identifier.of(Underdeep.MOD_ID, s.id);
            EntityType<UnderdeepCreature> type = Registry.register(Registries.ENTITY_TYPE, id, b.build());
            TYPES.put(s.id, type);
            BY_TYPE.put(type, s);
            FabricDefaultAttributeRegistry.register(type, MobEntity.createMobAttributes()
                    .add(EntityAttributes.GENERIC_MAX_HEALTH, s.health)
                    .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, s.speed)
                    .add(EntityAttributes.GENERIC_FLYING_SPEED, s.speed * 1.4)
                    .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, Math.max(1, s.attack))
                    .add(EntityAttributes.GENERIC_ARMOR, s.armor)
                    .add(EntityAttributes.GENERIC_FOLLOW_RANGE, s.follow)
                    .add(EntityAttributes.GENERIC_STEP_HEIGHT, s.sizeClass > 1.5 ? 1.1 : 0.6));
        }
    }

    public static List<CreatureSpecies> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    public static CreatureSpecies byId(String id) {
        return BY_ID.get(id);
    }

    public static CreatureSpecies of(EntityType<?> type) {
        return BY_TYPE.get(type);
    }

    public static EntityType<UnderdeepCreature> type(String id) {
        return TYPES.get(id);
    }

    public static Map<String, EntityType<UnderdeepCreature>> types() {
        return Collections.unmodifiableMap(TYPES);
    }
}
