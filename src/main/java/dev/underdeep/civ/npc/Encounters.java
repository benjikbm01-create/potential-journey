package dev.underdeep.civ.npc;

import dev.underdeep.civ.mind.Bond;
import dev.underdeep.civ.mind.Feelings.Emotion;
import dev.underdeep.civ.mind.Feelings.Need;
import dev.underdeep.civ.mind.Memory;
import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.mind.Trait;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Social encounters between two inhabitants (Stage 5). Each type has eligibility rules (relationship,
 * personality, needs, knowledge, place), a weight, effects on both minds (bonds, memories, emotions, needs,
 * knowledge, possessions — items are moved, never created) and a short exchange of lines that nearby players
 * may overhear. Ordinary small talk is far more common than arguments or dramatic events.
 */
public final class Encounters {
    private Encounters() {
    }

    public enum Type {
        GREETING, SMALL_TALK, WORK_TALK, SHARE_NEWS, SHARE_FOOD, ASK_FOR_HELP, DISAGREEMENT, ARGUMENT, RECONCILE, CONGRATULATE, COMFORT,
        GIFT, INTRODUCTION, LESSON, RIVAL_JAB, JOKE, INVITE_MEAL, FAMILY_TALK, WORRY_ABOUT_SOMEONE, WARN_DANGER, TALK_ABOUT_PLAYER,
        TALK_DISCOVERY, BOAST, COMPLAIN, PLAN_VISIT, MERCHANT_BANTER, GUARD_REPORT, GAME, NOTICE_INJURED, ASK_WHEREABOUTS, COMPLIMENT_WORK,
        TEASE
    }

    public record Result(Type type, String lineA, String lineB) {
    }

    /** Personality compatibility 0–1 (similar temperaments and tolerance get along). */
    public static double compat(Mind a, Mind b) {
        Trait[] ts = {Trait.SOCIABILITY, Trait.HUMOR, Trait.DISCIPLINE, Trait.CURIOSITY, Trait.PRIDE, Trait.OPTIMISM};
        double d = 0;
        for (Trait t : ts) {
            d += Math.abs(a.trait(t) - b.trait(t));
        }
        double base = 1 - d / (ts.length * 100.0) * 1.6;
        double tol = (a.trait(Trait.TOLERANCE) + b.trait(Trait.TOLERANCE)) / 200.0;
        return Math.max(0, Math.min(1, base * 0.7 + tol * 0.3));
    }

    private static String nm(SettlementRecord.InhabitantRecord r) {
        return r.name.substring(0, r.name.indexOf(' ') < 0 ? r.name.length() : r.name.indexOf(' '));
    }

    @Nullable
    private static ItemStack food(Mind m) {
        for (ItemStack s : m.possessions) {
            if (!s.isEmpty() && s.getComponents().contains(net.minecraft.component.DataComponentTypes.FOOD)) {
                return s;
            }
        }
        return null;
    }

    /** Picks and applies an encounter between a and b (a starts it). */
    @Nullable
    public static Result run(SettlementRecord rec, SettlementRecord.InhabitantRecord ra, SettlementRecord.InhabitantRecord rb, long now,
                             Random rnd, int memoryLimit) {
        Mind a = ra.mind, b = rb.mind;
        Bond ab = a.bond(rb.index), ba = b.bond(ra.index);
        double compat = compat(a, b);
        List<Type> pool = new ArrayList<>();
        List<Double> w = new ArrayList<>();
        java.util.function.BiConsumer<Type, Double> add = (t, x) -> {
            if (x > 0) {
                pool.add(t);
                w.add(x);
            }
        };
        boolean know = ab.familiarity >= 8;
        boolean friends = ab.label().ordinal() >= Bond.Label.FRIEND.ordinal() && ab.label().ordinal() <= Bond.Label.BEST_FRIEND.ordinal();
        boolean kin = ab.kin != Bond.Kin.NONE;
        add.accept(Type.INTRODUCTION, know ? 0 : 3.0);
        add.accept(Type.GREETING, know ? 2.0 : 0);
        add.accept(Type.SMALL_TALK, know ? 3.0 * (0.5 + a.trait(Trait.SOCIABILITY) / 100.0) : 0);
        add.accept(Type.WORK_TALK, ab.coworker ? 2.0 : 0);
        add.accept(Type.FAMILY_TALK, kin ? 2.5 : 0);
        add.accept(Type.JOKE, know && a.trait(Trait.HUMOR) > 60 ? 1.2 : 0);
        add.accept(Type.TEASE, friends && a.trait(Trait.HUMOR) > 55 ? 0.8 : 0);
        add.accept(Type.COMPLIMENT_WORK, ab.coworker || ab.respect > 25 ? 0.7 : 0);
        if (b.bond(ra.index).mentor) {
            add.accept(Type.LESSON, 1.5); // a teaches b (b considers a its mentor)
        }
        add.accept(Type.INVITE_MEAL, friends && a.need(Need.HUNGER) < 0.6 ? 1.0 : 0);
        add.accept(Type.PLAN_VISIT, friends && a.goal(Mind.GoalType.VISIT_FRIEND) == null ? 0.6 : 0);
        add.accept(Type.GAME, know && a.trait(Trait.COMPETITIVENESS) + a.trait(Trait.HUMOR) > 110 ? 0.6 : 0);
        add.accept(Type.MERCHANT_BANTER, ra.profession.equals("merchant") && rb.profession.equals("merchant") ? 1.2 : 0);
        add.accept(Type.BOAST, a.trait(Trait.PRIDE) > 65 && know ? 0.5 : 0);
        add.accept(Type.COMPLAIN, a.need(Need.HUNGER) < 0.3 || a.need(Need.REST) < 0.25 ? 0.5 : 0);
        add.accept(Type.SHARE_FOOD, b.need(Need.HUNGER) < 0.35 && food(a) != null && (friends || kin || a.trait(Trait.GENEROSITY) > 65) ? 2.0 : 0);
        add.accept(Type.GIFT, (friends || kin) && a.trait(Trait.GENEROSITY) > 60 && !a.possessions.isEmpty() && now - ab.lastGift > 24000L * 3 ? 0.4 : 0);
        add.accept(Type.COMFORT, (b.emotion(Emotion.SADNESS) > 0.35 || b.emotion(Emotion.CONCERN) > 0.4) && a.trait(Trait.COMPASSION) > 45 ? 2.5 : 0);
        add.accept(Type.CONGRATULATE, b.memories.list.stream().anyMatch(m -> m.type == Memory.Type.GOAL_DONE && now - m.time < 24000) && know ? 1.5 : 0);
        add.accept(Type.NOTICE_INJURED, b.health < 0.6 ? 2.0 : 0);
        add.accept(Type.ASK_FOR_HELP, ab.coworker && a.need(Need.PURPOSE) < 0.5 ? 0.8 : 0);
        Mind.Info news = shareable(a, b, now);
        add.accept(Type.SHARE_NEWS, news != null && news.type == Mind.InfoType.DISCOVERY ? 0 : news != null ? 1.4 : 0);
        add.accept(Type.WARN_DANGER, news != null && news.type == Mind.InfoType.PREDATOR_SEEN ? 2.2 : 0);
        add.accept(Type.GUARD_REPORT, news != null && ra.profession.equals("guard") && rb.profession.equals("guard") ? 2.0 : 0);
        add.accept(Type.TALK_ABOUT_PLAYER, news != null && (news.type == Mind.InfoType.PLAYER_HELPED || news.type == Mind.InfoType.PLAYER_ATTACKED
                || news.type == Mind.InfoType.GIFT_FROM_PLAYER) ? 1.6 : 0);
        add.accept(Type.TALK_DISCOVERY, news != null && news.type == Mind.InfoType.DISCOVERY ? 1.6 : 0);
        add.accept(Type.WORRY_ABOUT_SOMEONE, news != null && (news.type == Mind.InfoType.PERSON_LOST || news.type == Mind.InfoType.PERSON_INJURED) ? 2.0 : 0);
        add.accept(Type.ASK_WHEREABOUTS, a.knowledge.stream().anyMatch(i -> i.type == Mind.InfoType.PERSON_LOST) ? 0.6 : 0);
        // friction: incompatible temperaments, pride, impulsiveness; rivalries between ambitious peers
        double friction = (1 - compat) * (a.trait(Trait.PRIDE) + a.trait(Trait.IMPULSIVENESS) + (100 - a.trait(Trait.TOLERANCE))) / 300.0;
        add.accept(Type.DISAGREEMENT, know ? friction * 1.4 : 0);
        add.accept(Type.ARGUMENT, ab.resentment >= 30 || ab.rivalry ? 0.4 + friction : 0);
        add.accept(Type.RIVAL_JAB, ra.profession.equals(rb.profession) && a.trait(Trait.COMPETITIVENESS) > 65 && !friends ? 0.7 : 0);
        add.accept(Type.RECONCILE, ab.resentment >= 15 && a.trait(Trait.FORGIVENESS) > 45 ? 0.8 + a.t(Trait.FORGIVENESS) : 0);
        if (pool.isEmpty()) {
            return null;
        }
        double total = 0;
        for (double x : w) {
            total += x;
        }
        double pick = rnd.nextDouble() * total;
        Type t = pool.get(pool.size() - 1);
        for (int i = 0; i < pool.size(); i++) {
            pick -= w.get(i);
            if (pick <= 0) {
                t = pool.get(i);
                break;
            }
        }
        return apply(t, rec, ra, rb, ab, ba, news, compat, now, rnd, memoryLimit);
    }

    /** Something a knows that b does not (and that may be shared). */
    @Nullable
    static Mind.Info shareable(Mind a, Mind b, long now) {
        Mind.Info best = null;
        for (Mind.Info i : a.knowledge) {
            if (now - i.time > 24000L * 4 || i.confidence < 25) {
                continue;
            }
            boolean bKnows = b.knowledge.stream().anyMatch(o -> o.type == i.type && o.subject.equals(i.subject));
            if (!bKnows && (best == null || i.time > best.time)) {
                best = i;
            }
        }
        return best;
    }

    private static String who(Mind.Info i, SettlementRecord rec) {
        if (i.subject.startsWith("i:")) {
            try {
                int idx = Integer.parseInt(i.subject.substring(2));
                if (idx >= 0 && idx < rec.inhabitants.size()) {
                    return nm(rec.inhabitants.get(idx));
                }
            } catch (NumberFormatException ignored) {
                // not an index
            }
        }
        return i.detail.isEmpty() ? "someone" : i.detail;
    }

    private static Result apply(Type t, SettlementRecord rec, SettlementRecord.InhabitantRecord ra, SettlementRecord.InhabitantRecord rb,
                                Bond ab, Bond ba, @Nullable Mind.Info news, double compat, long now, Random rnd, int memLimit) {
        Mind a = ra.mind, b = rb.mind;
        String an = nm(ra), bn = nm(rb);
        String ia = "i:" + ra.index, ib = "i:" + rb.index;
        a.satisfy(Need.SOCIAL, 0.12);
        b.satisfy(Need.SOCIAL, 0.12);
        double warm = (compat - 0.45) * 8;
        Bond.Label before = ab.label();
        Result res;
        switch (t) {
            case INTRODUCTION -> {
                ab.adjust(now, 10, 3, 2, 2, 0, 0, false);
                ba.adjust(now, 10, 3, 2, 2, 0, 0, false);
                a.memories.remember(Memory.Type.FIRST_MEETING, ib, "", now, memLimit, 1);
                b.memories.remember(Memory.Type.FIRST_MEETING, ia, "", now, memLimit, 1);
                res = new Result(t, pick(rnd, "I don't think we've met. I'm " + an + ".", "Hello there. " + an + ", " + ra.profession + "."),
                        pick(rnd, bn + ". Good to meet you.", "Oh! I'm " + bn + ". Welcome, welcome."));
            }
            case GREETING -> {
                ab.adjust(now, 2, 0, (int) Math.signum(warm), 0, 0, 0, true);
                ba.adjust(now, 2, 0, (int) Math.signum(warm), 0, 0, 0, true);
                res = new Result(t, pick(rnd, "Morning, " + bn + ".", "Ah, " + bn + "!", "There you are."), pick(rnd, "Hey.", "Hello, " + an + ".", "Busy day?"));
            }
            case SMALL_TALK -> {
                ab.adjust(now, 3, 1, (int) Math.round(warm), 0, 0, 0, true);
                ba.adjust(now, 3, 1, (int) Math.round(warm), 0, 0, 0, true);
                if (compat > 0.6) {
                    a.feel(Emotion.HAPPINESS, 0.1);
                    b.feel(Emotion.HAPPINESS, 0.1);
                }
                res = new Result(t, pick(rnd, "Did you hear the water dripping all night?", "The lamps look brighter today.",
                        "I keep meaning to fix my door.", "Have you tried the stew at the inn lately?"),
                        pick(rnd, "Can't say I noticed.", "Ha, I know what you mean.", "You always notice the little things.", "Maybe tomorrow."));
            }
            case WORK_TALK -> {
                ab.adjust(now, 3, 2, 1, 2, 0, 0, true);
                ba.adjust(now, 3, 2, 1, 2, 0, 0, true);
                a.memories.remember(Memory.Type.WORKED_TOGETHER, ib, "", now, memLimit, 1);
                res = new Result(t, pick(rnd, "We're short on supplies again.", "Did you finish the last order?", "My hands are sore from this morning."),
                        pick(rnd, "I'll manage. Always do.", "Almost. Give me until the bell.", "Same here. Good work though."));
            }
            case FAMILY_TALK -> {
                ab.adjust(now, 1, 2, 2, 0, 0, 0, true);
                ba.adjust(now, 1, 2, 2, 0, 0, 0, true);
                a.satisfy(Need.BELONGING, 0.2);
                b.satisfy(Need.BELONGING, 0.2);
                res = new Result(t, pick(rnd, "Did you eat today?", "Don't stay out too late.", "I saved you a seat at dinner."),
                        pick(rnd, "Yes, yes.", "I won't.", "You worry too much."));
            }
            case JOKE -> {
                ab.adjust(now, 2, 0, compat > 0.4 ? 3 : -1, 0, 0, 0, true);
                ba.adjust(now, 2, 0, compat > 0.4 ? 3 : -1, 0, 0, 0, true);
                b.feel(Emotion.HAPPINESS, compat > 0.4 ? 0.15 : 0);
                res = new Result(t, pick(rnd, "Why did the miner bring a ladder? Higher wages.", "I'd tell you a cave joke, but it's too deep.",
                        "My cooking is so good the rats leave thank-you notes."), compat > 0.4 ? pick(rnd, "Ha! Terrible.", "*laughs*", "That's awful. Tell me another.")
                        : pick(rnd, "...right.", "Was that a joke?"));
            }
            case TEASE -> {
                boolean offended = b.trait(Trait.PRIDE) > 70 && ab.affection < 50;
                ab.adjust(now, 1, 0, offended ? -3 : 2, 0, 0, 0, true);
                ba.adjust(now, 1, 0, offended ? -4 : 2, 0, offended ? 4 : 0, 0, true);
                res = new Result(t, pick(rnd, "Still tripping over your own boots?", "Is that a new hat or did something land on you?"),
                        offended ? "Not funny." : pick(rnd, "Very funny.", "Says you!"));
            }
            case COMPLIMENT_WORK -> {
                ba.adjust(now, 1, 1, 2, 4, 0, 0, true);
                b.feel(Emotion.PRIDE, 0.15);
                res = new Result(t, pick(rnd, "Your work is getting really good.", "Whatever you did yesterday, it was well done."),
                        pick(rnd, "That means a lot.", "Thanks. Took me a while."));
            }
            case LESSON -> {
                b.skill = Math.min(100, b.skill + 1);
                ba.adjust(now, 2, 2, 2, 4, 0, 0, true);
                ab.adjust(now, 2, 2, 2, 1, 0, 0, true);
                a.satisfy(Need.PURPOSE, 0.15);
                res = new Result(t, pick(rnd, "Slower. Let the tool do the work.", "Watch the grain, not your hands."),
                        pick(rnd, "Like this?", "I think I've got it."));
            }
            case INVITE_MEAL -> {
                a.addGoal(Mind.GoalType.VISIT_FRIEND, ib, now, 6000);
                b.addGoal(Mind.GoalType.VISIT_FRIEND, ia, now, 6000);
                res = new Result(t, pick(rnd, "Eat with me later?", "Supper at the inn tonight?"), pick(rnd, "Gladly.", "I'll be there."));
            }
            case PLAN_VISIT -> {
                a.addGoal(Mind.GoalType.VISIT_FRIEND, ib, now, 12000);
                res = new Result(t, pick(rnd, "I'll come by your place later.", "Mind if I visit this evening?"), pick(rnd, "Any time.", "Bring something to eat!"));
            }
            case GAME -> {
                boolean aWins = rnd.nextDouble() < 0.5 + (a.trait(Trait.COMPETITIVENESS) - b.trait(Trait.COMPETITIVENESS)) / 400.0;
                a.satisfy(Need.RECREATION, 0.3);
                b.satisfy(Need.RECREATION, 0.3);
                (aWins ? a : b).feel(Emotion.PRIDE, 0.15);
                (aWins ? b : a).feel(Emotion.FRUSTRATION, (aWins ? b : a).trait(Trait.COMPETITIVENESS) > 65 ? 0.15 : 0.03);
                ab.adjust(now, 2, 0, 2, 0, 0, 0, true);
                ba.adjust(now, 2, 0, 2, 0, 0, 0, true);
                res = new Result(t, aWins ? "Ha! My game." : "Rematch. Now.", aWins ? "Lucky stones, that's all." : "You'll never beat me.");
            }
            case MERCHANT_BANTER -> {
                boolean rival = a.trait(Trait.COMPETITIVENESS) > 60 && b.trait(Trait.COMPETITIVENESS) > 60;
                ab.adjust(now, 2, rival ? -1 : 1, rival ? -1 : 1, 2, rival ? 3 : 0, 0, true);
                ba.adjust(now, 2, rival ? -1 : 1, rival ? -1 : 1, 2, rival ? 3 : 0, 0, true);
                if (rival && ab.resentment > 20) {
                    ab.rivalry = true;
                    ba.rivalry = true;
                }
                res = new Result(t, rival ? "Selling to my customers again, I see." : "Business good on your side?",
                        rival ? "They come to me for a reason." : "Steady. Yours?");
            }
            case BOAST -> {
                ba.adjust(now, 1, 0, b.trait(Trait.PRIDE) > 60 ? -2 : 0, a.skill > b.skill ? 2 : -1, 0, 0, true);
                res = new Result(t, pick(rnd, "Nobody in town does it better than me.", "Did you see what I made? Best one yet."),
                        pick(rnd, "If you say so.", "Not bad, I'll admit."));
            }
            case COMPLAIN -> {
                boolean hungry = a.need(Need.HUNGER) < 0.3;
                b.feel(Emotion.CONCERN, b.trait(Trait.COMPASSION) / 400.0);
                res = new Result(t, hungry ? "I haven't eaten since this morning." : "I'm so tired I could sleep standing up.",
                        hungry ? "Go eat something, then." : "Go home and rest.");
            }
            case SHARE_FOOD -> {
                ItemStack f = food(a);
                if (f != null) {
                    ItemStack one = f.split(1);
                    a.possessions.removeIf(ItemStack::isEmpty);
                    b.satisfy(Need.HUNGER, 0.5);
                    one.decrement(1); // eaten: moved and consumed, never created
                    ba.adjust(now, 3, 6, 8, 0, 0, 0, false);
                    ab.adjust(now, 2, 2, 3, 0, 0, 0, false);
                    b.memories.remember(Memory.Type.HELPED_BY, ia, "food", now, memLimit, 1);
                    a.memories.remember(Memory.Type.HELPED_SOMEONE, ib, "food", now, memLimit, 1);
                    b.feel(Emotion.HAPPINESS, 0.2);
                }
                res = new Result(t, pick(rnd, "Here, have some of mine.", "You look starved. Take this."), pick(rnd, "You're a lifesaver.", "Thank you, truly."));
            }
            case GIFT -> {
                ItemStack g = a.possessions.isEmpty() ? ItemStack.EMPTY : a.possessions.get(rnd.nextInt(a.possessions.size()));
                if (!g.isEmpty() && b.possessions.size() < 9) {
                    b.possessions.add(g.split(1));
                    a.possessions.removeIf(ItemStack::isEmpty);
                    ab.lastGift = now;
                    ba.adjust(now, 3, 4, 10, 0, 0, 0, false);
                    b.memories.remember(Memory.Type.GIFT_RECEIVED, ia, net.minecraft.registry.Registries.ITEM.getId(b.possessions.get(b.possessions.size() - 1)
                            .getItem()).toString(), now, memLimit, 1);
                    a.memories.remember(Memory.Type.GIFT_GIVEN, ib, "", now, memLimit, 1);
                    b.feel(Emotion.AFFECTION, 0.2);
                }
                res = new Result(t, pick(rnd, "I found this and thought of you.", "For you. No reason."), pick(rnd, "For me? You shouldn't have.", "That's lovely."));
            }
            case COMFORT -> {
                b.feel(Emotion.SADNESS, -0.2);
                b.feel(Emotion.CONCERN, -0.15);
                b.feel(Emotion.RELIEF, 0.15);
                ba.adjust(now, 3, 5, 7, 0, 0, 0, false);
                b.memories.remember(Memory.Type.HELPED_BY, ia, "comfort", now, memLimit, 1);
                res = new Result(t, pick(rnd, "Hey. It's going to be alright.", "Want to talk about it?"), pick(rnd, "Thanks for being here.", "Maybe later. But thank you."));
            }
            case CONGRATULATE -> {
                ba.adjust(now, 2, 2, 4, 0, 0, 0, false);
                b.feel(Emotion.PRIDE, 0.15);
                res = new Result(t, pick(rnd, "I heard what you did. Well done!", "Congratulations, really."), pick(rnd, "Thank you!", "It took long enough."));
            }
            case NOTICE_INJURED -> {
                a.feel(Emotion.CONCERN, 0.25);
                a.learn(Mind.InfoType.PERSON_INJURED, ib, "", now, Mind.Source.WITNESSED, 90, 12);
                ba.adjust(now, 2, 3, 3, 0, 0, 0, false);
                res = new Result(t, "You're hurt! Go see a healer.", pick(rnd, "It's nothing. Mostly.", "I will, I will."));
            }
            case ASK_FOR_HELP -> {
                ab.adjust(now, 2, 2, 2, 1, 0, 0, true);
                ba.adjust(now, 2, 1, 1, 2, 0, 0, true);
                a.satisfy(Need.PURPOSE, 0.15);
                a.memories.remember(Memory.Type.WORKED_TOGETHER, ib, "", now, memLimit, 1);
                res = new Result(t, pick(rnd, "Can you give me a hand with this?", "Hold that end for me?"), pick(rnd, "Sure.", "Fine, but you owe me."));
            }
            case SHARE_NEWS, WARN_DANGER, GUARD_REPORT, TALK_ABOUT_PLAYER, TALK_DISCOVERY, WORRY_ABOUT_SOMEONE -> {
                if (news == null) {
                    return null;
                }
                int conf = Math.max(10, news.confidence - 15 - (int) (Math.max(0, 50 - a.trait(Trait.HONESTY)) / 5));
                boolean learned = b.learn(news.type, news.subject, news.detail, now, Mind.Source.TOLD, conf, 12);
                news.retold++;
                ab.adjust(now, 2, 1, 1, 0, 0, 0, true);
                String subj = who(news, rec);
                switch (news.type) {
                    case PREDATOR_SEEN -> {
                        b.feel(Emotion.CONCERN, 0.15 + b.t(Trait.CAUTION) * 0.1);
                        res = new Result(t, "Careful near the edge of town. I saw a " + subj + " out there.", learned ? "A " + subj + "? I'll keep my eyes open." : "I heard.");
                    }
                    case PLAYER_HELPED, GIFT_FROM_PLAYER -> {
                        res = new Result(t, "That traveller " + subj + " helped me out, you know.", "Really? Good to hear.");
                        applyHearsay(b, news, +1, now);
                    }
                    case PLAYER_ATTACKED -> {
                        res = new Result(t, "Watch out for " + subj + ". I saw them attack someone.", "That's worrying.");
                        applyHearsay(b, news, -1, now);
                    }
                    case PERSON_INJURED -> {
                        b.feel(Emotion.CONCERN, 0.15);
                        res = new Result(t, subj + " got hurt. Have you seen them?", "No... I hope they're alright.");
                    }
                    case PERSON_LOST -> {
                        b.feel(Emotion.SADNESS, 0.15);
                        res = new Result(t, "Have you heard about " + subj + "?", "I heard. I still can't believe it.");
                    }
                    case DISCOVERY -> {
                        b.feel(Emotion.CURIOSITY, 0.2);
                        res = new Result(t, "Someone found something remarkable out in the caverns.", "Tell me everything!");
                    }
                    default -> res = new Result(t, "Did you hear?", "Hm?");
                }
            }
            case ASK_WHEREABOUTS -> {
                Mind.Info lost = a.knowledge.stream().filter(i -> i.type == Mind.InfoType.PERSON_LOST).findFirst().orElse(null);
                res = new Result(t, "Have you seen " + (lost == null ? "anyone" : who(lost, rec)) + " lately?", "Not since... no.");
            }
            case DISAGREEMENT -> {
                ab.adjust(now, 1, -2, -4, 0, 5, 0, false);
                ba.adjust(now, 1, -2, -4, 0, 5, 0, false);
                a.feel(Emotion.FRUSTRATION, 0.2);
                b.feel(Emotion.FRUSTRATION, 0.2);
                if (ab.resentment > 25) {
                    a.memories.remember(Memory.Type.BAD_TALK, ib, "", now, memLimit, 1);
                }
                res = new Result(t, pick(rnd, "That's not how it's done.", "You're wrong about that.", "Why would anyone do it that way?"),
                        pick(rnd, "It works for me.", "Nobody asked you.", "We'll have to disagree."));
            }
            case ARGUMENT -> {
                ab.adjust(now, 1, -6, -8, -2, 10, 0, false);
                ba.adjust(now, 1, -6, -8, -2, 10, 0, false);
                a.feel(Emotion.ANGER, 0.3);
                b.feel(Emotion.ANGER, 0.3);
                a.memories.remember(Memory.Type.ARGUMENT, ib, "", now, memLimit, 1);
                b.memories.remember(Memory.Type.ARGUMENT, ia, "", now, memLimit, 1);
                if (b.trait(Trait.FORGIVENESS) > 55) {
                    b.addGoal(Mind.GoalType.RECONCILE, ia, now, 24000L * 3);
                }
                res = new Result(t, pick(rnd, "I've had enough of you!", "You always do this!"), pick(rnd, "Then leave me alone!", "Me? You started it!"));
            }
            case RIVAL_JAB -> {
                ab.adjust(now, 1, -1, -2, 1, 4, 0, false);
                ba.adjust(now, 1, -1, -2, 1, 4, 0, false);
                if (ab.resentment > 25) {
                    ab.rivalry = true;
                    ba.rivalry = true;
                }
                res = new Result(t, pick(rnd, "Mine sold out before noon. Yours?", "I hear your last batch came back."), pick(rnd, "Enjoy it while it lasts.", "Mind your own stall."));
            }
            case RECONCILE -> {
                ab.adjust(now, 2, 4, 6, 2, -20, 0, false);
                ba.adjust(now, 2, 4, 6, 2, -20, 0, false);
                ab.rivalry = ab.resentment > 30 && ab.rivalry;
                ba.rivalry = ab.rivalry;
                a.memories.remember(Memory.Type.RECONCILED, ib, "", now, memLimit, 1);
                b.memories.remember(Memory.Type.RECONCILED, ia, "", now, memLimit, 1);
                a.feel(Emotion.RELIEF, 0.25);
                b.feel(Emotion.RELIEF, 0.25);
                a.goals.removeIf(g -> g.type == Mind.GoalType.RECONCILE && g.target.equals(ib));
                b.goals.removeIf(g -> g.type == Mind.GoalType.RECONCILE && g.target.equals(ia));
                res = new Result(t, pick(rnd, "About the other day... I'm sorry.", "Can we put that behind us?"), pick(rnd, "Me too. Let's forget it.", "Alright. Truce."));
            }
            default -> res = null;
        }
        // friendship milestones become memories
        Bond.Label after = ab.label();
        if (after != before && (after == Bond.Label.FRIEND || after == Bond.Label.CLOSE_FRIEND || after == Bond.Label.BEST_FRIEND)) {
            a.memories.remember(Memory.Type.BECAME_FRIENDS, ib, after.name(), now, memLimit, 1);
            a.feel(Emotion.HAPPINESS, 0.2);
        }
        a.lastSocial = now;
        b.lastSocial = now;
        return res;
    }

    /** Hearsay about a player shifts the listener's view a little (never as much as a first-hand experience). */
    private static void applyHearsay(Mind b, Mind.Info i, int sign, long now) {
        if (!i.subject.startsWith("p:")) {
            return;
        }
        try {
            java.util.UUID u = java.util.UUID.fromString(i.subject.substring(2));
            int conf = i.confidence;
            b.bond(u).adjust(now, 0, sign * conf / 20, sign * conf / 30, 0, sign < 0 ? conf / 25 : 0, 0, false);
        } catch (IllegalArgumentException ignored) {
            // not a player subject
        }
    }

    private static String pick(Random r, String... options) {
        return options[r.nextInt(options.length)];
    }
}
