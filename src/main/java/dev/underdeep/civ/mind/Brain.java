package dev.underdeep.civ.mind;

import dev.underdeep.civ.mind.Feelings.Emotion;
import dev.underdeep.civ.mind.Feelings.Need;

import java.util.Locale;
import java.util.Random;

/**
 * Utility-based activity selection with commitment (Stage 5). The same logic drives embodied inhabitants
 * (every second) and abstract ones (coarse updates), so an individual behaves consistently across tiers.
 * <p>
 * Stability rules: an activity is kept until its minimum duration has passed unless an urgent need or danger
 * interrupts (utility ≥ 3); afterwards a new activity must beat the current one by 25 % plus a margin;
 * past its maximum duration the current activity loses utility steadily.
 */
public final class Brain {
    private Brain() {
    }

    /** What the surrounding world offers this inhabitant right now (filled by the caller). */
    public static final class Context {
        public String profession = "";
        public long now, timeOfDay;
        public boolean hasWork, hasFarm, hasWater, hasLibrary, hasTavern, hasMarket, hasClinic, hasEdge;
        public boolean danger, attacked, guardNearby, othersNear, injuredNeighbor;
        /** Best candidates found by the caller (record indices) or −1. */
        public int friend = -1, family = -1, apprentice = -1, estranged = -1, newcomer = -1;
        public boolean eveningGathering;
        public long personalSeed;
    }

    public record Choice(Activity activity, int target, String reason) {
    }

    /** Hours of in-game time in ticks. */
    public static double hours(long ticks) {
        return ticks / 1000.0;
    }

    /** Advances needs and emotions over elapsed time and applies the current activity's effects. */
    public static void advance(Mind m, long elapsed) {
        double h = Math.min(24, hours(Math.max(0, elapsed)));
        if (h <= 0) {
            return;
        }
        for (Need n : Need.values()) {
            double rate = n.decay;
            if (n == Need.SOCIAL) {
                rate *= 0.6 + 0.8 * (m.trait(Trait.SOCIABILITY) / 100.0);
            } else if (n == Need.CURIOSITY) {
                rate *= 0.5 + m.trait(Trait.CURIOSITY) / 100.0;
            } else if (n == Need.PURPOSE) {
                rate *= 0.6 + 0.8 * (m.trait(Trait.AMBITION) / 100.0);
            }
            m.satisfy(n, -rate * h);
        }
        // safety and health recover by themselves when nothing happens
        m.satisfy(Need.SAFETY, 0.25 * h);
        m.satisfy(Need.HEALTH, 0.05 * h);
        Activity a = Activity.byOrdinal(m.activity);
        if (a != null) {
            a.apply(m, h);
        }
        double res = 0.7 + 0.6 * (m.trait(Trait.RESILIENCE) / 100.0);
        for (Emotion e : Emotion.values()) {
            double keep = Math.pow(e.hourlyRetention(), h * (e.negative() ? res : 1.0));
            m.emotions[e.ordinal()] *= (float) keep;
        }
        // contentment follows needs and optimism; loneliness follows the social need
        double sat = 0;
        for (Need n : Need.values()) {
            sat += m.need(n);
        }
        sat /= Need.COUNT;
        m.emotions[Emotion.CONTENTMENT.ordinal()] = (float) Math.max(0, Math.min(1, sat * 0.8 + m.t(Trait.OPTIMISM) * 0.15));
        if (m.need(Need.SOCIAL) < 0.3) {
            m.feel(Emotion.LONELINESS, 0.05 * h);
        }
        if (m.need(Need.HUNGER) < 0.2 || m.need(Need.REST) < 0.15) {
            m.feel(Emotion.STRESS, 0.04 * h);
        }
    }

    /** Personal schedule offset in ticks (−300..300): undisciplined people drift more. */
    static long offset(Mind m, long seed) {
        double spread = 300 * (1.15 - m.trait(Trait.DISCIPLINE) / 100.0);
        return (long) (((seed >>> 8) % 1000 / 1000.0 - 0.5) * 2 * spread);
    }

    enum Phase { NIGHT, MORNING, WORK, MIDDAY, EVENING }

    static Phase phase(Mind m, Context c) {
        long t = Math.floorMod(c.timeOfDay + offset(m, c.personalSeed), 24000L);
        // night shift workers (guards, innkeepers) swap night and work
        boolean nightShift = (c.profession.equals("guard") || c.profession.equals("innkeeper")) && (c.personalSeed & 1) == 0;
        Phase p;
        if (t >= 13000 && t < 23000) {
            p = Phase.NIGHT;
        } else if (t >= 23000 || t < 1000) {
            p = Phase.MORNING;
        } else if (t >= 5500 && t < 6500) {
            p = Phase.MIDDAY;
        } else if (t >= 11000) {
            p = Phase.EVENING;
        } else {
            p = Phase.WORK;
        }
        if (nightShift) {
            p = p == Phase.NIGHT ? Phase.WORK : p == Phase.WORK ? Phase.NIGHT : p;
        }
        return p;
    }

    /** Chooses the next activity (or keeps the current one). */
    public static Choice decide(Mind m, Context c, Random rnd) {
        Activity cur = Activity.byOrdinal(m.activity);
        long elapsedMin = cur == null ? 9999 : (c.now - m.activitySince) * 60 / 1000;
        Phase ph = phase(m, c);
        Activity best = null;
        double bestU = -1;
        int bestTarget = -1;
        String bestWhy = "";
        for (Activity a : Activity.values()) {
            double[] why = new double[1];
            int[] target = {-1};
            double u = utility(a, m, c, ph, target, why);
            if (u <= 0) {
                continue;
            }
            u *= 0.92 + rnd.nextDouble() * 0.16; // small individual variation
            if (u > bestU) {
                bestU = u;
                best = a;
                bestTarget = target[0];
                bestWhy = describe(a, ph, m);
            }
        }
        if (best == null) {
            best = Activity.STROLL;
            bestWhy = "nothing better to do";
        }
        if (cur != null && cur != best) {
            double curU = utility(cur, m, c, ph, new int[1], new double[1]);
            boolean urgent = bestU >= 3.0 && best.emergency() || bestU >= 3.0 && !cur.emergency() && curU < 2;
            int minimum = (int) (cur.minMinutes * (1.0 - 0.4 * m.t(Trait.IMPULSIVENESS)) * (0.8 + 0.4 * m.trait(Trait.PATIENCE) / 100.0));
            if (!urgent) {
                if (elapsedMin < minimum) {
                    return new Choice(cur, -2, m.reason);
                }
                if (elapsedMin < cur.maxMinutes && bestU < curU * 1.25 + 0.15) {
                    return new Choice(cur, -2, m.reason);
                }
            }
        }
        if (cur == best && elapsedMin < best.maxMinutes) {
            return new Choice(cur, -2, m.reason);
        }
        return new Choice(best, bestTarget, bestWhy + String.format(Locale.ROOT, " (utility %.2f)", bestU));
    }

    /** Starts a chosen activity (records time; target kept by the caller). */
    public static void begin(Mind m, Choice ch, long now) {
        if (ch.target() == -2) {
            return;
        }
        m.activity = ch.activity().ordinal();
        m.activitySince = now;
        m.reason = ch.reason();
    }

    private static String describe(Activity a, Phase ph, Mind m) {
        Need low = Need.HUNGER;
        for (Need n : Need.values()) {
            if (m.need(n) < m.need(low)) {
                low = n;
            }
        }
        return a.id() + " — " + ph.name().toLowerCase(Locale.ROOT) + ", lowest need " + low.name().toLowerCase(Locale.ROOT)
                + String.format(Locale.ROOT, " %.2f, mood %s", m.need(low), m.mood().name().toLowerCase(Locale.ROOT));
    }

    private static double lack(Mind m, Need n) {
        return 1.0 - m.need(n);
    }

    /** Utility of an activity; 0 means not possible now. */
    static double utility(Activity a, Mind m, Context c, Phase ph, int[] target, double[] why) {
        boolean work = ph == Phase.WORK;
        boolean night = ph == Phase.NIGHT;
        boolean free = ph == Phase.EVENING || ph == Phase.MIDDAY || ph == Phase.MORNING;
        double soc = m.trait(Trait.SOCIABILITY) / 100.0;
        double fear = m.emotion(Emotion.FEAR);
        String p = c.profession;
        boolean recovering = m.status == Mind.Status.RECOVERING || m.health < 0.45;
        switch (a) {
            // ---------------- emergency
            case FLEE:
                return c.danger && !p.equals("guard") && m.trait(Trait.COURAGE) < 55 ? 4 + fear : 0;
            case TAKE_SHELTER:
                return c.danger && !p.equals("guard") ? 3.2 + m.t(Trait.CAUTION) * 0.5 : 0;
            case DEFEND:
                return c.danger && (p.equals("guard") || m.trait(Trait.COURAGE) > 75 && p.equals("hunter")) ? 4.2 : 0;
            case WARN_OTHERS:
                return c.danger && c.othersNear && m.trait(Trait.COMPASSION) + m.trait(Trait.COURAGE) > 110 ? 3.4 : 0;
            case SEEK_GUARD:
                return c.attacked && c.guardNearby && !p.equals("guard") ? 3.6 : 0;
            case HELP_INJURED:
                if (c.injuredNeighbor && c.friend >= 0 && !c.danger) {
                    target[0] = c.friend;
                    return 2.2 + m.t(Trait.COMPASSION);
                }
                return 0;
            case SEE_HEALER:
                return recovering && c.hasClinic && !p.equals("healer") ? 3.1 : 0;
            case RECOVER:
                return recovering ? 3.0 : 0;
            // ---------------- personal
            case SLEEP:
                return night ? 2.2 + lack(m, Need.REST) * 2 - (p.equals("guard") ? 0 : 0) : lack(m, Need.REST) > 0.85 ? 2.5 : 0;
            case NAP:
                return !night && lack(m, Need.REST) > 0.55 ? lack(m, Need.REST) * 2.2 : 0;
            case EAT_HOME:
                return lack(m, Need.HUNGER) > 0.35 ? lack(m, Need.HUNGER) * 2.6 + m.t(Trait.HOMEBODY) * 0.3 + m.t(Trait.FRUGALITY) * 0.2 : 0;
            case EAT_TAVERN:
                return c.hasTavern && lack(m, Need.HUNGER) > 0.35 && !night ? lack(m, Need.HUNGER) * 2.4 + soc * 0.5 - m.t(Trait.FRUGALITY) * 0.2 : 0;
            case TIDY_HOME:
                return free && lack(m, Need.COMFORT) > 0.3 ? lack(m, Need.COMFORT) * 1.4 + m.t(Trait.DISCIPLINE) * 0.2 : 0;
            case MORNING_ROUTINE:
                return ph == Phase.MORNING ? 1.4 + m.t(Trait.DISCIPLINE) * 0.3 : 0;
            // ---------------- work (only the profession's own)
            case WORK_STATION:
                return work && c.hasWork && genericWork(p) ? workU(m) : 0;
            case TEND_FIELDS:
                return work && c.hasFarm && p.equals("farmer") ? workU(m) + 0.1 : 0;
            case ANIMAL_CARE:
                return work && c.hasFarm && p.equals("rancher") ? workU(m) + 0.1 : 0;
            case FISH:
                return work && c.hasWater && (p.equals("fisher") || p.equals("boatwright") && m.trait(Trait.ADVENTUROUSNESS) > 60) ? workU(m) + 0.1 : 0;
            case SMITHING:
                return work && c.hasWork && (p.equals("blacksmith") || p.equals("smelter") || p.equals("engineer")) ? workU(m) + 0.1 : 0;
            case CRAFTING:
                return work && c.hasWork && (p.equals("carpenter") || p.equals("tailor") || p.equals("gemcutter") || p.equals("artisan")
                        || p.equals("stonemason") || p.equals("lampwright") || p.equals("boatwright")) ? workU(m) + 0.1 : 0;
            case COOKING:
                return work && c.hasWork && (p.equals("cook") || p.equals("baker") || p.equals("brewer")) ? workU(m) + 0.1 : 0;
            case STUDY:
                return work && (p.equals("scholar") || p.equals("astronomer") || p.equals("cartographer") || p.equals("alchemist"))
                        ? workU(m) + 0.1 + m.t(Trait.CURIOSITY) * 0.2 : 0;
            case SERVE_CUSTOMERS:
                return work && c.hasWork && p.equals("merchant") ? workU(m) + 0.15 : 0;
            case STOCK_SHELVES:
                return work && c.hasWork && p.equals("merchant") && ((c.personalSeed + c.now / 1000) % 5 == 0) ? workU(m) + 0.2 : 0;
            case PATROL:
                return work && p.equals("guard") && ((c.personalSeed >> 3) & 1) == 0 ? workU(m) + 0.2 : 0;
            case WATCH_POST:
                return work && p.equals("guard") ? workU(m) + 0.15 : 0;
            case HEAL_PATIENTS:
                return work && c.hasWork && p.equals("healer") ? workU(m) + 0.15 : 0;
            case MAINTAIN_TOOLS:
                return work && c.hasWork && m.need(Need.PURPOSE) > 0.7 && m.t(Trait.DISCIPLINE) > 0.1 ? 0.9 : 0;
            case GATHER_HERBS:
                return work && c.hasEdge && (p.equals("herbalist") || p.equals("mycologist") || p.equals("forester")) ? workU(m) + 0.1 : 0;
            case RUN_INN:
                return work && c.hasTavern && p.equals("innkeeper") ? workU(m) + 0.2 : 0;
            // ---------------- social
            case CHAT:
                return c.othersNear && !night ? lack(m, Need.SOCIAL) * 1.6 * (0.5 + soc) : 0;
            case VISIT_FRIEND:
                if (free && c.friend >= 0) {
                    target[0] = c.friend;
                    return lack(m, Need.SOCIAL) * 1.8 * (0.5 + soc) + (m.goal(Mind.GoalType.VISIT_FRIEND) != null ? 0.6 : 0);
                }
                return 0;
            case VISIT_FAMILY:
                if (free && c.family >= 0) {
                    target[0] = c.family;
                    return lack(m, Need.BELONGING) * 1.8 + 0.3 + (m.goal(Mind.GoalType.SPEND_TIME_FAMILY) != null ? 0.6 : 0);
                }
                return 0;
            case SHARE_MEAL:
                if (c.hasTavern && c.friend >= 0 && lack(m, Need.HUNGER) > 0.3 && (ph == Phase.MIDDAY || ph == Phase.EVENING)) {
                    target[0] = c.friend;
                    return lack(m, Need.HUNGER) * 2 + soc * 0.8;
                }
                return 0;
            case ATTEND_GATHERING:
                return c.eveningGathering && ph == Phase.EVENING ? 1.2 + soc * 1.2 + lack(m, Need.BELONGING) : 0;
            case HELP_NEIGHBOR:
                return 0; // handled by HELP_INJURED with real context
            case GIVE_GIFT:
                if (free && c.friend >= 0 && !m.possessions.isEmpty() && m.trait(Trait.GENEROSITY) > 60) {
                    target[0] = c.friend;
                    return 0.6 + m.t(Trait.GENEROSITY);
                }
                return 0;
            case MAKE_AMENDS:
                if (free && c.estranged >= 0) {
                    target[0] = c.estranged;
                    return 0.5 + m.t(Trait.FORGIVENESS) - m.t(Trait.PRIDE) * 0.5 + (m.goal(Mind.GoalType.RECONCILE) != null ? 0.7 : 0);
                }
                return 0;
            case GREET_NEWCOMER:
                if (c.newcomer >= 0 && !night) {
                    target[0] = c.newcomer;
                    return 0.8 + soc + m.t(Trait.CURIOSITY) * 0.3;
                }
                return 0;
            case TEACH_APPRENTICE:
                if (work && c.apprentice >= 0) {
                    target[0] = c.apprentice;
                    return workU(m) + m.t(Trait.PATIENCE) * 0.3;
                }
                return 0;
            // ---------------- leisure
            case STROLL:
                return free ? 0.6 + lack(m, Need.RECREATION) * 0.8 : 0.2;
            case SIT_AND_REST:
                return free ? 0.5 + lack(m, Need.REST) * 0.8 + (1 - soc) * 0.2 : 0;
            case WATCH_WILDLIFE:
                return free && c.hasEdge && fear < 0.3 ? lack(m, Need.CURIOSITY) * 1.2 + m.t(Trait.CURIOSITY) * 0.5 - m.t(Trait.CAUTION) * 0.2 : 0;
            case VISIT_GARDEN:
                return free && c.hasFarm ? 0.5 + lack(m, Need.RECREATION) * 0.8 + ("garden".equals(m.favoritePlace) ? 0.5 : 0) : 0;
            case BROWSE_MARKET:
                return free && c.hasMarket && !night ? 0.5 + lack(m, Need.RECREATION) * 0.7 + soc * 0.3 + ("market".equals(m.favoritePlace) ? 0.5 : 0) : 0;
            case TAVERN_EVENING:
                return ph == Phase.EVENING && c.hasTavern ? 0.6 + soc * 1.2 + lack(m, Need.RECREATION) * 0.8 + m.t(Trait.HUMOR) * 0.2
                        + ("tavern".equals(m.favoritePlace) ? 0.5 : 0) : 0;
            case EXPLORE_NEARBY:
                return free && c.hasEdge && fear < 0.2 ? lack(m, Need.CURIOSITY) * 1.1 + m.t(Trait.ADVENTUROUSNESS) * 0.7 - m.t(Trait.CAUTION) * 0.4
                        - m.t(Trait.HOMEBODY) * 0.3 + (m.goal(Mind.GoalType.EXPLORE_NEARBY) != null ? 0.6 : 0) : 0;
            case FAVORITE_SPOT:
                return free ? 0.7 + lack(m, Need.COMFORT) * 0.6 + m.t(Trait.HOMEBODY) * 0.2 : 0;
            case PLAY_GAME:
                return free && c.othersNear && !night ? 0.4 + m.t(Trait.HUMOR) * 0.5 + m.t(Trait.COMPETITIVENESS) * 0.4 + lack(m, Need.RECREATION) : 0;
            case READ:
                return free && (c.hasLibrary || m.trait(Trait.CURIOSITY) > 60) ? 0.3 + m.t(Trait.CURIOSITY) * 0.8 + lack(m, Need.CURIOSITY) * 0.8 : 0;
            default:
                return 0;
        }
    }

    private static boolean genericWork(String p) {
        return switch (p) {
            case "farmer", "rancher", "fisher", "blacksmith", "smelter", "engineer", "carpenter", "tailor", "gemcutter", "artisan",
                 "stonemason", "lampwright", "boatwright", "cook", "baker", "brewer", "merchant", "guard", "healer", "innkeeper" -> false;
            default -> true;
        };
    }

    /** Work utility: purpose, ambition and discipline make people keen; exhaustion and hunger pull them away. */
    private static double workU(Mind m) {
        return 1.6 + (1 - m.need(Need.PURPOSE)) * 0.8 + m.t(Trait.AMBITION) * 0.3 + m.t(Trait.DISCIPLINE) * 0.3
                - Math.max(0, 0.3 - m.need(Need.REST)) * 3 - Math.max(0, 0.25 - m.need(Need.HUNGER)) * 4;
    }
}
