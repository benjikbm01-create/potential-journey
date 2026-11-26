package dev.underdeep.civ.mind;

import dev.underdeep.civ.mind.Feelings.Emotion;
import dev.underdeep.civ.mind.Feelings.Need;

import java.util.Random;

/**
 * Personal goals and requests for help (Stage 5): generated from the inhabitant's real state (personality,
 * needs, health, relationships, possessions), advanced by what they actually do, completed or abandoned.
 * Pure logic; called from both simulation tiers.
 */
public final class Goals {
    private Goals() {
    }

    /** Daily-scale upkeep: progress, completion, expiry and occasional new goals. {@code hours} of simulated time. */
    public static void update(Mind m, Activity a, double hours, long now, Random rnd, int memLimit) {
        if (a != null && a.cat == Activity.Cat.WORK) {
            m.skill = (int) Math.min(100, m.skill + (rnd.nextDouble() < 0.04 * hours * (0.5 + m.trait(Trait.AMBITION) / 100.0) ? 1 : 0));
        }
        for (Mind.Goal g : m.goals) {
            switch (g.type) {
                case IMPROVE_SKILL -> g.progress += a != null && a.cat == Activity.Cat.WORK ? 0.06 * hours : 0;
                case EXPLORE_NEARBY -> g.progress += a == Activity.EXPLORE_NEARBY ? 0.6 * hours : 0;
                case SPEND_TIME_FAMILY -> g.progress += a == Activity.VISIT_FAMILY ? 0.7 * hours : 0;
                case EAT_FAVORITE -> g.progress += a == Activity.EAT_TAVERN || a == Activity.SHARE_MEAL ? 1 : 0;
                case RECOVER -> g.progress = m.health >= 0.9f ? 1 : g.progress;
                default -> {
                }
            }
        }
        for (java.util.Iterator<Mind.Goal> it = m.goals.iterator(); it.hasNext(); ) {
            Mind.Goal g = it.next();
            if (g.progress >= 1) {
                it.remove();
                complete(m, g, now, memLimit);
            } else if (now > g.expires) {
                it.remove(); // quietly given up
                if (m.trait(Trait.AMBITION) > 70) {
                    m.feel(Emotion.FRUSTRATION, 0.1);
                }
            }
        }
        if (m.request != null && now > m.request.expires) {
            m.request = null; // the need passed or was solved some other way
        }
        if (rnd.nextDouble() < 0.06 * hours) {
            propose(m, now, rnd);
        }
        requests(m, now);
    }

    public static void complete(Mind m, Mind.Goal g, long now, int memLimit) {
        m.memories.remember(Memory.Type.GOAL_DONE, g.target, g.type.name(), now, memLimit, 1);
        m.feel(Emotion.PRIDE, 0.2 + m.t(Trait.AMBITION) * 0.1);
        m.feel(Emotion.HAPPINESS, 0.15);
        m.satisfy(Need.PURPOSE, 0.25);
        if (g.type == Mind.GoalType.IMPROVE_SKILL) {
            m.skill = Math.min(100, m.skill + 3);
        }
    }

    /** Completes a goal of a type (and optional target) if present. */
    public static boolean completeIf(Mind m, Mind.GoalType type, String target, long now, int memLimit) {
        Mind.Goal g = m.goal(type);
        if (g != null && (g.target.isEmpty() || target == null || g.target.equals(target))) {
            m.goals.remove(g);
            complete(m, g, now, memLimit);
            return true;
        }
        return false;
    }

    /** New goals grounded in the current state; personality decides which are attractive. */
    static void propose(Mind m, long now, Random rnd) {
        long day = 24000L;
        double r = rnd.nextDouble();
        if (m.health < 0.6f) {
            m.addGoal(Mind.GoalType.RECOVER, "", now, day * 4);
        } else if (r < 0.2 && m.skill < 85 && m.trait(Trait.AMBITION) > 50) {
            m.addGoal(Mind.GoalType.IMPROVE_SKILL, "", now, day * 6);
        } else if (r < 0.35 && m.trait(Trait.CURIOSITY) + m.trait(Trait.ADVENTUROUSNESS) > 110) {
            m.addGoal(Mind.GoalType.EXPLORE_NEARBY, "", now, day * 3);
        } else if (r < 0.5 && m.trait(Trait.SOCIABILITY) > 55 && friends(m) < 2) {
            m.addGoal(Mind.GoalType.MAKE_FRIEND, "", now, day * 6);
        } else if (r < 0.6 && hasKinElsewhere(m)) {
            m.addGoal(Mind.GoalType.SPEND_TIME_FAMILY, "", now, day * 3);
        } else if (r < 0.75 && !m.likes.isEmpty()) {
            m.addGoal(Mind.GoalType.OBTAIN_ITEM, m.likes.get(rnd.nextInt(m.likes.size())), now, day * 5);
        } else if (r < 0.85) {
            m.addGoal(Mind.GoalType.EAT_FAVORITE, "", now, day * 2);
        } else if (m.trait(Trait.CURIOSITY) > 60) {
            m.addGoal(Mind.GoalType.LEARN_NEWS, "", now, day * 3);
        }
    }

    static int friends(Mind m) {
        int n = 0;
        for (Bond b : m.people.values()) {
            Bond.Label l = b.label();
            n += l == Bond.Label.FRIEND || l == Bond.Label.CLOSE_FRIEND || l == Bond.Label.BEST_FRIEND ? 1 : 0;
        }
        return n;
    }

    static boolean hasKinElsewhere(Mind m) {
        for (Bond b : m.people.values()) {
            if (b.kin != Bond.Kin.NONE && !b.household) {
                return true;
            }
        }
        return false;
    }

    /**
     * Requests for help arise only from real circumstances: being hungry with nothing to eat, being injured, or
     * wanting a particular item for a personal goal. They expire on their own.
     */
    static void requests(Mind m, long now) {
        if (m.request != null) {
            return;
        }
        Mind.Request q = null;
        if (m.health < 0.55f) {
            q = new Mind.Request();
            q.kind = Mind.Request.Kind.MEDICINE;
            q.item = "underdeep:sunleaf_salve";
            q.count = 1;
        } else if (m.need(Need.HUNGER) < 0.2 && m.possessions.stream().noneMatch(s -> s.getComponents().contains(
                net.minecraft.component.DataComponentTypes.FOOD))) {
            q = new Mind.Request();
            q.kind = Mind.Request.Kind.FOOD;
            q.item = "minecraft:bread";
            q.count = 2;
        } else {
            Mind.Goal g = m.goal(Mind.GoalType.OBTAIN_ITEM);
            if (g != null && !g.target.isEmpty() && now - g.created > 6000) {
                q = new Mind.Request();
                q.kind = Mind.Request.Kind.MATERIAL;
                q.item = g.target;
                q.count = 1;
            }
        }
        if (q != null) {
            q.created = now;
            q.expires = now + 24000L * 2;
            m.request = q;
        }
    }
}
