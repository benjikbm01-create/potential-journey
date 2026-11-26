package dev.underdeep.civ.mind;

import dev.underdeep.civ.mind.Feelings.Need;

/**
 * The activity library (Stage 5). Each activity has a category, a place it happens, a duration range (in-game
 * minutes; 1 in-game hour = 1000 ticks) and hourly effects on needs. Utility scoring lives in {@link Brain}.
 * Ordinals are persisted (append-only).
 */
public enum Activity {
    // personal
    SLEEP(Cat.PERSONAL, Place.BED, 360, 600, Need.REST, 0.18, Need.HUNGER, -0.03),
    NAP(Cat.PERSONAL, Place.BED, 40, 90, Need.REST, 0.15),
    EAT_HOME(Cat.PERSONAL, Place.HOME, 25, 45, Need.HUNGER, 0.9, Need.COMFORT, 0.1),
    EAT_TAVERN(Cat.PERSONAL, Place.TAVERN, 35, 60, Need.HUNGER, 0.85, Need.SOCIAL, 0.2, Need.RECREATION, 0.1),
    TIDY_HOME(Cat.PERSONAL, Place.HOME, 30, 70, Need.COMFORT, 0.25, Need.PURPOSE, 0.05),
    MORNING_ROUTINE(Cat.PERSONAL, Place.HOME, 20, 40, Need.COMFORT, 0.15),
    RECOVER(Cat.PERSONAL, Place.BED, 120, 400, Need.HEALTH, 0.12, Need.REST, 0.1),
    // work
    WORK_STATION(Cat.WORK, Place.WORK, 120, 260, Need.PURPOSE, 0.22, Need.REST, -0.04),
    TEND_FIELDS(Cat.WORK, Place.FARM, 120, 240, Need.PURPOSE, 0.22, Need.REST, -0.05),
    FISH(Cat.WORK, Place.WATER, 90, 220, Need.PURPOSE, 0.2, Need.RECREATION, 0.05),
    SMITHING(Cat.WORK, Place.WORK, 120, 240, Need.PURPOSE, 0.24, Need.REST, -0.06),
    CRAFTING(Cat.WORK, Place.WORK, 120, 240, Need.PURPOSE, 0.22),
    COOKING(Cat.WORK, Place.WORK, 90, 200, Need.PURPOSE, 0.2, Need.HUNGER, 0.1),
    STUDY(Cat.WORK, Place.LIBRARY, 90, 220, Need.PURPOSE, 0.18, Need.CURIOSITY, 0.25),
    SERVE_CUSTOMERS(Cat.WORK, Place.WORK, 150, 300, Need.PURPOSE, 0.2, Need.SOCIAL, 0.08),
    STOCK_SHELVES(Cat.WORK, Place.WORK, 30, 70, Need.PURPOSE, 0.2),
    PATROL(Cat.WORK, Place.PATROL, 120, 260, Need.PURPOSE, 0.2, Need.SAFETY, 0.05),
    WATCH_POST(Cat.WORK, Place.WORK, 90, 200, Need.PURPOSE, 0.18),
    HEAL_PATIENTS(Cat.WORK, Place.WORK, 90, 200, Need.PURPOSE, 0.24),
    ANIMAL_CARE(Cat.WORK, Place.FARM, 60, 150, Need.PURPOSE, 0.2, Need.RECREATION, 0.05),
    MAINTAIN_TOOLS(Cat.WORK, Place.WORK, 30, 60, Need.PURPOSE, 0.15),
    GATHER_HERBS(Cat.WORK, Place.EDGE, 60, 150, Need.PURPOSE, 0.18, Need.CURIOSITY, 0.08),
    RUN_INN(Cat.WORK, Place.TAVERN, 150, 300, Need.PURPOSE, 0.2, Need.SOCIAL, 0.1),
    // social
    CHAT(Cat.SOCIAL, Place.NEAR, 10, 25, Need.SOCIAL, 0.5),
    VISIT_FRIEND(Cat.SOCIAL, Place.FRIEND, 40, 110, Need.SOCIAL, 0.45, Need.BELONGING, 0.12),
    VISIT_FAMILY(Cat.SOCIAL, Place.FRIEND, 40, 110, Need.SOCIAL, 0.35, Need.BELONGING, 0.25),
    SHARE_MEAL(Cat.SOCIAL, Place.TAVERN, 35, 60, Need.HUNGER, 0.8, Need.SOCIAL, 0.35),
    ATTEND_GATHERING(Cat.SOCIAL, Place.PLAZA, 60, 140, Need.SOCIAL, 0.3, Need.BELONGING, 0.2, Need.RECREATION, 0.15),
    HELP_NEIGHBOR(Cat.SOCIAL, Place.FRIEND, 30, 80, Need.PURPOSE, 0.15, Need.BELONGING, 0.12),
    GIVE_GIFT(Cat.SOCIAL, Place.FRIEND, 15, 30, Need.SOCIAL, 0.2, Need.BELONGING, 0.1),
    MAKE_AMENDS(Cat.SOCIAL, Place.FRIEND, 20, 40, Need.SOCIAL, 0.2, Need.BELONGING, 0.15),
    GREET_NEWCOMER(Cat.SOCIAL, Place.PLAZA, 15, 35, Need.SOCIAL, 0.25, Need.CURIOSITY, 0.1),
    TEACH_APPRENTICE(Cat.SOCIAL, Place.WORK, 60, 120, Need.PURPOSE, 0.2, Need.SOCIAL, 0.15),
    // leisure
    STROLL(Cat.LEISURE, Place.WANDER, 30, 80, Need.RECREATION, 0.25),
    SIT_AND_REST(Cat.LEISURE, Place.PLAZA, 30, 70, Need.REST, 0.08, Need.COMFORT, 0.1, Need.RECREATION, 0.1),
    WATCH_WILDLIFE(Cat.LEISURE, Place.EDGE, 40, 100, Need.CURIOSITY, 0.3, Need.RECREATION, 0.25),
    VISIT_GARDEN(Cat.LEISURE, Place.FARM, 30, 80, Need.RECREATION, 0.25, Need.COMFORT, 0.1),
    BROWSE_MARKET(Cat.LEISURE, Place.MARKET, 30, 70, Need.RECREATION, 0.2, Need.SOCIAL, 0.1, Need.CURIOSITY, 0.1),
    TAVERN_EVENING(Cat.LEISURE, Place.TAVERN, 60, 150, Need.SOCIAL, 0.35, Need.RECREATION, 0.35),
    EXPLORE_NEARBY(Cat.LEISURE, Place.EDGE, 60, 150, Need.CURIOSITY, 0.4, Need.RECREATION, 0.25, Need.SAFETY, -0.05),
    FAVORITE_SPOT(Cat.LEISURE, Place.FAVORITE, 40, 100, Need.RECREATION, 0.3, Need.COMFORT, 0.15),
    PLAY_GAME(Cat.LEISURE, Place.PLAZA, 30, 70, Need.RECREATION, 0.45, Need.SOCIAL, 0.25),
    READ(Cat.LEISURE, Place.LIBRARY, 40, 100, Need.CURIOSITY, 0.3, Need.RECREATION, 0.2),
    // emergency
    FLEE(Cat.EMERGENCY, Place.SHELTER, 5, 15, Need.SAFETY, 0.3),
    TAKE_SHELTER(Cat.EMERGENCY, Place.SHELTER, 20, 60, Need.SAFETY, 0.6),
    WARN_OTHERS(Cat.EMERGENCY, Place.PLAZA, 5, 15, Need.PURPOSE, 0.1),
    DEFEND(Cat.EMERGENCY, Place.NEAR, 5, 30, Need.SAFETY, 0.1),
    SEEK_GUARD(Cat.EMERGENCY, Place.GUARD, 5, 20, Need.SAFETY, 0.3),
    HELP_INJURED(Cat.EMERGENCY, Place.FRIEND, 10, 40, Need.PURPOSE, 0.2),
    SEE_HEALER(Cat.EMERGENCY, Place.CLINIC, 30, 120, Need.HEALTH, 0.3),
    // Stage 6 (append-only)
    CONSTRUCT(Cat.WORK, Place.SITE, 120, 260, Need.PURPOSE, 0.22, Need.REST, -0.05),
    TRAVEL(Cat.WORK, Place.NEAR, 60, 600, Need.PURPOSE, 0.1, Need.CURIOSITY, 0.1),
    CELEBRATE(Cat.SOCIAL, Place.PLAZA, 60, 160, Need.SOCIAL, 0.4, Need.BELONGING, 0.3, Need.RECREATION, 0.35);

    public enum Cat { PERSONAL, WORK, SOCIAL, LEISURE, EMERGENCY }

    /** Abstract places resolved per inhabitant from the settlement plan ({@code npc.Places}). */
    public enum Place { BED, HOME, WORK, FARM, WATER, LIBRARY, TAVERN, PLAZA, MARKET, PATROL, EDGE, FRIEND, NEAR, WANDER, FAVORITE, SHELTER, GUARD, CLINIC, SITE }

    public final Cat cat;
    public final Place place;
    public final int minMinutes, maxMinutes;
    final Need[] needs;
    final double[] effects;

    Activity(Cat cat, Place place, int minMinutes, int maxMinutes, Object... effects) {
        this.cat = cat;
        this.place = place;
        this.minMinutes = minMinutes;
        this.maxMinutes = maxMinutes;
        this.needs = new Need[effects.length / 2];
        this.effects = new double[effects.length / 2];
        for (int i = 0; i + 1 < effects.length; i += 2) {
            needs[i / 2] = (Need) effects[i];
            this.effects[i / 2] = ((Number) effects[i + 1]).doubleValue();
        }
    }

    /** Applies this activity's hourly effects for a number of in-game hours. */
    public void apply(Mind m, double hours) {
        for (int i = 0; i < needs.length; i++) {
            double e = effects[i];
            if (needs[i] == Need.HUNGER && e > 0) {
                e *= 0.3 + 0.7 * m.food; // meals depend on the household's actual food (Stage 6 economy)
            }
            m.satisfy(needs[i], e * hours);
        }
    }

    public boolean emergency() {
        return cat == Cat.EMERGENCY;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Activity byOrdinal(int i) {
        return i < 0 || i >= values().length ? null : values()[i];
    }
}
