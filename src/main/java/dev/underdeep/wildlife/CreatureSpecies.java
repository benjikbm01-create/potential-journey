package dev.underdeep.wildlife;

import dev.underdeep.ecology.Species.Rarity;
import dev.underdeep.ecology.Species.Role;
import dev.underdeep.world.region.RegionType;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Definition of one wildlife species (Stage 3). Pure data: behaviour comes from the reusable goals
 * selected by these fields; appearance comes from the generated model/texture
 * ({@code assets/underdeep/creatures/<id>.json}). Species ids are persistent (entity type ids,
 * regional population keys) and append-only.
 */
public final class CreatureSpecies {
    /** How the creature moves. */
    public enum Locomotion { LAND, CLIMBER, AQUATIC, AMPHIBIOUS, FLYER, GLIDER, HOVER }

    /** What it eats. Plant diets map to Stage 2 ecological roles. */
    public enum Diet { GRAZER, BROWSER, FUNGIVORE, LITHOVORE, AQUATIC_GRAZER, NECTAR, OMNIVORE, CARNIVORE, SCAVENGER, FILTER }

    /** Group structure. */
    public enum Social { SOLITARY, PAIR, FAMILY, HERD, PACK, SCHOOL, COLONY, SWARM }

    /** Default stance towards other creatures and players. */
    public enum Temperament {
        PASSIVE,      // ignores players, flees predators
        SKITTISH,     // flees players that come close
        CURIOUS,      // approaches players, flees if hurt
        NEUTRAL,      // retaliates when attacked
        TERRITORIAL,  // warns, then drives intruders out of its territory
        PREDATOR,     // hunts animals; defends itself
        AMBUSH        // dangerous: hunts animals and, in dangerous ecosystems, players who come close
    }

    public enum HuntStyle { NONE, PURSUIT, STALK, AMBUSH, PACK, AQUATIC, SWOOP }

    /** Biological rhythm: when the species is mostly active (world time based). */
    public enum Activity { DAY, NIGHT, TWILIGHT, ALWAYS, BURSTS }

    public enum TameMethod { NONE, FEEDING, PATIENCE, RESCUE, OFFERING }

    public enum Mount { NONE, RUNNER, CLIMBER, SWIMMER, PACK, GLIDER }

    public enum Nest { NONE, GROUND, BURROW, TREE, LEDGE, WATER, CRYSTAL, FUNGAL }

    public final String id;
    Set<RegionType> regions = EnumSet.noneOf(RegionType.class);
    float width = 0.9f, height = 0.9f;
    double health = 10, speed = 0.25, attack = 0, armor = 0, follow = 24;
    Locomotion locomotion = Locomotion.LAND;
    Diet diet = Diet.GRAZER;
    Social social = Social.SOLITARY;
    int groupMin = 1, groupMax = 1;
    Temperament temperament = Temperament.PASSIVE;
    HuntStyle hunt = HuntStyle.NONE;
    Activity activity = Activity.ALWAYS;
    TameMethod tame = TameMethod.NONE;
    String tameFood = "";
    Mount mount = Mount.NONE;
    Nest nest = Nest.NONE;
    boolean laysEggs;
    List<String> prey = List.of();
    /** Expected individuals per region instance at full capacity. */
    double density = 6;
    Rarity rarity = Rarity.COMMON;
    boolean fireImmune, breathesWater, glows, swimsInLava;
    String voice = "generic";
    float pitch = 1.0f;
    float sizeClass = 1;
    /** Renewable product: "milk", "shear:<item>", "shed:<item>" or "". */
    String product = "";
    String juvenileName = "";

    CreatureSpecies(String id) {
        this.id = id;
    }

    // ---------------- fluent builder (used only in WildlifeRegistry) ----------------
    CreatureSpecies in(RegionType... r) { regions = EnumSet.of(r[0], r); return this; }
    CreatureSpecies size(float w, float h) { width = w; height = h; sizeClass = Math.max(w, h); return this; }
    CreatureSpecies stats(double hp, double spd, double atk) { health = hp; speed = spd; attack = atk; return this; }
    CreatureSpecies armor(double a) { armor = a; return this; }
    CreatureSpecies moves(Locomotion l) { locomotion = l; return this; }
    CreatureSpecies eats(Diet d) { diet = d; return this; }
    CreatureSpecies group(Social s, int min, int max) { social = s; groupMin = min; groupMax = max; return this; }
    CreatureSpecies temper(Temperament t) { temperament = t; return this; }
    CreatureSpecies hunts(HuntStyle h, String... preyIds) { hunt = h; prey = List.of(preyIds); return this; }
    CreatureSpecies active(Activity a) { activity = a; return this; }
    CreatureSpecies tame(TameMethod m, String food) { tame = m; tameFood = food; return this; }
    CreatureSpecies mount(Mount m) { mount = m; return this; }
    CreatureSpecies nest(Nest n, boolean eggs) { nest = n; laysEggs = eggs; return this; }
    CreatureSpecies density(double d) { density = d; return this; }
    CreatureSpecies rarity(Rarity r) { rarity = r; return this; }
    CreatureSpecies fireproof() { fireImmune = true; return this; }
    CreatureSpecies lavaSwimmer() { fireImmune = true; swimsInLava = true; return this; }
    CreatureSpecies water() { breathesWater = true; return this; }
    CreatureSpecies glowing() { glows = true; return this; }
    CreatureSpecies voice(String v, float p) { voice = v; pitch = p; return this; }
    CreatureSpecies product(String p) { product = p; return this; }

    // ---------------- accessors ----------------
    public Set<RegionType> regions() { return regions; }
    public float width() { return width; }
    public float height() { return height; }
    public double health() { return health; }
    public double speed() { return speed; }
    public double attack() { return attack; }
    public double armorValue() { return armor; }
    public Locomotion locomotion() { return locomotion; }
    public Diet diet() { return diet; }
    public Social social() { return social; }
    public int groupMin() { return groupMin; }
    public int groupMax() { return groupMax; }
    public Temperament temperament() { return temperament; }
    public HuntStyle huntStyle() { return hunt; }
    public Activity activity() { return activity; }
    public TameMethod tameMethod() { return tame; }
    public String tameFood() { return tameFood; }
    public Mount mountType() { return mount; }
    public Nest nestType() { return nest; }
    public boolean laysEggs() { return laysEggs; }
    public List<String> preyIds() { return prey; }
    public double density() { return density; }
    public Rarity rarity() { return rarity; }
    public boolean fireImmune() { return fireImmune; }
    public boolean breathesWater() { return breathesWater; }
    public boolean swimsInLava() { return swimsInLava; }
    public boolean glows() { return glows; }
    public String voiceName() { return voice; }
    public float voicePitch() { return pitch; }
    public float sizeClass() { return sizeClass; }
    public String product() { return product; }

    public boolean isAquatic() {
        return locomotion == Locomotion.AQUATIC;
    }

    public boolean flies() {
        return locomotion == Locomotion.FLYER || locomotion == Locomotion.HOVER;
    }

    public boolean isPredator() {
        return hunt != HuntStyle.NONE;
    }

    public boolean tamable() {
        return tame != TameMethod.NONE;
    }

    /** Stage 2 ecological roles this species feeds on (empty for carnivores). */
    public Set<Role> foodRoles() {
        return switch (diet) {
            case GRAZER -> EnumSet.of(Role.FORAGE, Role.SEEDS);
            case BROWSER -> EnumSet.of(Role.FORAGE, Role.FRUIT);
            case FUNGIVORE -> EnumSet.of(Role.FUNGAL_FOOD);
            case LITHOVORE -> EnumSet.of(Role.MINERAL_FOOD);
            case AQUATIC_GRAZER, FILTER -> EnumSet.of(Role.AQUATIC_FORAGE);
            case NECTAR -> EnumSet.of(Role.NECTAR);
            case OMNIVORE -> EnumSet.of(Role.FRUIT, Role.FUNGAL_FOOD, Role.SEEDS);
            default -> EnumSet.noneOf(Role.class);
        };
    }

    @Override
    public String toString() {
        return id;
    }
}
