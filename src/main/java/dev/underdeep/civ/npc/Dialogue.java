package dev.underdeep.civ.npc;

import dev.underdeep.civ.mind.Bond;
import dev.underdeep.civ.mind.Feelings.Emotion;
import dev.underdeep.civ.mind.Feelings.Need;
import dev.underdeep.civ.mind.Goals;
import dev.underdeep.civ.mind.Memory;
import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.mind.Preferences;
import dev.underdeep.civ.mind.Trait;
import dev.underdeep.config.UnderdeepConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Server-authoritative conversations with inhabitants (Stage 5). Opening a dialogue and every choice are
 * validated (session, distance, liveness, rate limit); effects on relationships use diminishing returns so
 * talking cannot be ground for reputation. Gifts and help are validated against the player's actual held
 * stack and consume exactly what is handed over.
 */
public final class Dialogue {
    private Dialogue() {
    }

    public static final class Session {
        int entityId;
        long settlement;
        int index;
        String lastTopic = "";
        /** Sub-menu (Stage 6 noticeboard) or empty. */
        public String menu = "";
        long lastChoice;
        int talks;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Random RND = new Random();
    static final double RANGE = 8;

    public static void open(ServerPlayerEntity p, InhabitantEntity e) {
        SettlementRecord rec = SettlementState.get((ServerWorld) e.getWorld()).get(e.settlementId);
        SettlementRecord.InhabitantRecord r = InhabitantManager.record(e);
        if (rec == null || r == null || r.mind == null) {
            return;
        }
        Session s = new Session();
        s.entityId = e.getId();
        s.settlement = rec.id;
        s.index = r.index;
        SESSIONS.put(p.getUuid(), s);
        Topics.Talk t = talk(p, e, rec, r);
        dev.underdeep.guide.Advancements.event(p, "talked", rec.id + ":" + r.index);
        if (t.bond != null && t.bond.label().ordinal() >= dev.underdeep.civ.mind.Bond.Label.FRIEND.ordinal()
                && t.bond.label().ordinal() <= dev.underdeep.civ.mind.Bond.Label.TRUSTED_ASSOCIATE.ordinal()) {
            dev.underdeep.guide.Advancements.event(p, "friend_made", rec.id + ":" + r.index);
        }
        long now = t.now;
        dev.underdeep.civ.econ.EconDialogue.opened(p, rec, r);
        boolean first = t.bond.familiarity == 0 && !r.mind.memories.has(Memory.Type.FIRST_MEETING, "p:" + p.getUuid());
        Topics.Topic greet = Topics.choose(Topics.Group.GREETING, t, null);
        String text = greet == null ? "Hello." : Topics.voice(t, greet.line().apply(t));
        if (first) {
            r.mind.memories.remember(Memory.Type.FIRST_MEETING, "p:" + p.getUuid(), "", now, Society.memLimit(), 1);
            t.bond.adjust(now, 10, 0, 1, 0, 0, 0, false);
        } else {
            t.bond.adjust(now, 2, 0, 0, 0, 0, 0, true);
        }
        r.mind.satisfy(Need.SOCIAL, 0.05);
        e.talkTo(p, 600);
        SettlementState.get((ServerWorld) e.getWorld()).markDirty();
        send(p, e, t, text);
    }

    static Topics.Talk talk(ServerPlayerEntity p, InhabitantEntity e, SettlementRecord rec, SettlementRecord.InhabitantRecord r) {
        Topics.Talk t = new Topics.Talk();
        t.rec = rec;
        t.r = r;
        t.m = r.mind;
        t.player = p.getUuid();
        t.playerName = p.getName().getString();
        t.bond = r.mind.bond(p.getUuid());
        InhabitantManager mgr = InhabitantManager.get(e.getWorld());
        t.places = mgr == null ? null : mgr.society.places(rec);
        t.now = e.getWorld().getTime();
        t.timeOfDay = e.getWorld().getTimeOfDay();
        t.rnd = RND;
        return t;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Sends the current line and the options the player has right now. */
    static void send(ServerPlayerEntity p, InhabitantEntity e, Topics.Talk t, String text) {
        List<String> ids = new ArrayList<>(), labels = new ArrayList<>();
        boolean hostile = t.bond.trust <= -50 || t.bond.resentment >= 70;
        Session ses = SESSIONS.get(p.getUuid());
        if (ses != null && !ses.menu.isEmpty()) {
            dev.underdeep.civ.econ.EconDialogue.menu(p, e, t, ses.menu, ids, labels);
            opt(ids, labels, "back", "Back");
            opt(ids, labels, "bye", "Goodbye");
            sendRaw(p, e, t, text, ids, labels);
            return;
        }
        if (!hostile) {
            opt(ids, labels, "chat", "Chat");
            opt(ids, labels, "about", "Tell me about yourself");
            opt(ids, labels, "news", "Any news?");
            opt(ids, labels, "town", "About " + t.rec.name);
            opt(ids, labels, "people", "Who's who around here?");
            opt(ids, labels, "feel", "How are you?");
            if (!e.getOffers().isEmpty() && !t.wary()) {
                opt(ids, labels, "trade", "Trade");
            }
        }
        ItemStack held = p.getMainHandStack();
        if (!held.isEmpty()) {
            opt(ids, labels, "gift", "Give " + held.getName().getString());
        }
        Mind.Request q = t.m.request;
        if (q != null && !hostile) {
            opt(ids, labels, "ask_help", "Do you need anything?");
            if (!held.isEmpty() && matches(q, held)) {
                opt(ids, labels, "give_help", "Hand over " + held.getName().getString());
            }
        }
        if (!hostile) {
            dev.underdeep.civ.econ.EconDialogue.options(p, e, t, ids, labels);
            dev.underdeep.survival.SurvivalDialogue.options(p, e, t, ids, labels);
            dev.underdeep.explore.ExploreDialogue.options(p, e, t, ids, labels);
        }
        Companions.options(p, e, t, ids, labels);
        opt(ids, labels, "bye", "Goodbye");
        sendRaw(p, e, t, text, ids, labels);
    }

    static void sendRaw(ServerPlayerEntity p, InhabitantEntity e, Topics.Talk t, String text, List<String> ids, List<String> labels) {
        String subtitle = cap(t.prof()) + " · " + cap(t.civName()) + " · " + t.bond.playerWord()
                + (t.m.age == Mind.Age.YOUNG ? " · young" : t.m.age == Mind.Age.ELDER ? " · elder" : "");
        String name = t.r.name;
        if (ServerPlayNetworking.canSend(p, DialoguePayloads.Open.ID)) {
            ServerPlayNetworking.send(p, new DialoguePayloads.Open(e.getId(), name, subtitle, text, ids, labels));
        } else {
            // a client without the mod's screen: plain chat, then trade
            p.sendMessage(Text.literal("<" + name + "> ").formatted(Formatting.GOLD).append(Text.literal(text).formatted(Formatting.WHITE)), false);
        }
    }

    public static void opt(List<String> ids, List<String> labels, String id, String label) {
        if (ids.size() < 15) {
            ids.add(id);
            labels.add(label);
        }
    }

    /** A choice from the client: validated, applied, answered. */
    static void choose(ServerPlayerEntity p, int entityId, String option) {
        Session s = SESSIONS.get(p.getUuid());
        if (s == null || s.entityId != entityId) {
            return;
        }
        Entity ent = p.getServerWorld().getEntityById(entityId);
        if (!(ent instanceof InhabitantEntity e) || !e.isAlive() || e.squaredDistanceTo(p) > RANGE * RANGE || e.isSleeping()) {
            SESSIONS.remove(p.getUuid());
            return;
        }
        long now = p.getServerWorld().getTime();
        if (now - s.lastChoice < 4 && !option.equals("bye")) {
            return; // rate limit
        }
        s.lastChoice = now;
        SettlementRecord rec = SettlementState.get(p.getServerWorld()).get(s.settlement);
        if (rec == null || s.index >= rec.inhabitants.size()) {
            return;
        }
        SettlementRecord.InhabitantRecord r = rec.inhabitants.get(s.index);
        if (r.mind == null || e.recordIndex != r.index) {
            return;
        }
        Topics.Talk t = talk(p, e, rec, r);
        e.talkTo(p, 600);
        SettlementState.get(p.getServerWorld()).markDirty();
        String text;
        switch (option) {
            case "bye" -> {
                SESSIONS.remove(p.getUuid());
                e.talkUntil = 0;
                InhabitantManager mgr = InhabitantManager.get(e.getWorld());
                if (mgr != null) {
                    mgr.society.say(e, farewell(t), 0);
                }
                return;
            }
            case "chat", "about", "news", "town", "people", "feel" -> text = topic(t, s, option);
            case "trade" -> {
                if (e.getOffers().isEmpty() || t.wary()) {
                    text = "I've nothing to trade right now.";
                } else {
                    SESSIONS.remove(p.getUuid());
                    e.setCustomer(p);
                    e.sendOffers(p, e.getDisplayName(), 1);
                    return;
                }
            }
            case "gift" -> {
                text = gift(p, e, t);
                dev.underdeep.guide.Advancements.event(p, "gift_given", t.r.name);
            }
            case "ask_help" -> text = describeRequest(t);
            case "give_help" -> {
                text = help(p, e, t);
                if (t.m.request == null) {
                    dev.underdeep.guide.Advancements.event(p, "helped", t.r.name);
                }
            }
            case "back" -> {
                s.menu = "";
                text = "Anything else?";
            }
            default -> {
                String econ = dev.underdeep.civ.econ.EconDialogue.choose(p, e, t, s, option);
                if (econ == null) {
                    econ = dev.underdeep.survival.SurvivalDialogue.choose(p, e, t, option);
                }
                if (econ == null) {
                    econ = dev.underdeep.explore.ExploreDialogue.choose(p, e, t, option);
                }
                if (econ != null) {
                    if (econ.isEmpty()) {
                        SESSIONS.remove(p.getUuid());
                        return;
                    }
                    send(p, e, t, econ);
                    return;
                }
                String c = Companions.choose(p, e, t, option);
                if (c == null) {
                    return;
                }
                if (c.isEmpty()) {
                    SESSIONS.remove(p.getUuid());
                    return; // the companion command closed the dialogue (e.g. opened the pack)
                }
                text = c;
            }
        }
        send(p, e, t, text);
    }

    public static void close(ServerPlayerEntity p) {
        SESSIONS.remove(p.getUuid());
    }

    private static String farewell(Topics.Talk t) {
        if (t.wary()) {
            return "...";
        }
        return t.warm() ? t.pick("Take care, " + t.playerName + "!", "Come back soon!", "Mind the dark out there.")
                : t.pick("Goodbye.", "Safe travels.", "Bye now.");
    }

    private static String topic(Topics.Talk t, Session s, String option) {
        Topics.Group g = switch (option) {
            case "about" -> Topics.Group.ABOUT;
            case "news" -> Topics.Group.NEWS;
            case "town" -> Topics.Group.TOWN;
            case "people" -> Topics.Group.PEOPLE;
            case "feel" -> Topics.Group.FEELING;
            default -> Topics.Group.CHAT;
        };
        Topics.Topic topic = Topics.choose(g, t, s.lastTopic);
        if (topic == null) {
            return "Hm. Not much to say about that.";
        }
        s.lastTopic = topic.id();
        s.talks++;
        String line = Topics.voice(t, topic.line().apply(t));
        // a pleasant conversation: familiarity and (with compatible personalities) affection grow, with diminishing returns
        int aff = t.m.trait(Trait.SOCIABILITY) > 40 || t.familiar() ? 1 : 0;
        t.bond.adjust(t.now, 2, 1, aff, 0, t.bond.resentment > 0 ? -1 : 0, t.bond.fear > 0 ? -1 : 0, true);
        t.m.satisfy(Need.SOCIAL, 0.06);
        if (s.talks == 3) {
            t.m.memories.remember(Memory.Type.GOOD_TALK, "p:" + t.player, "", t.now, Society.memLimit(), 0.6);
        }
        if (g == Topics.Group.ABOUT || g == Topics.Group.CHAT && t.familiar()) {
            String rev = reveal(t);
            if (rev != null) {
                line = line + " " + rev;
            }
        }
        // news shared with the player is real knowledge; tell them about predators they saw
        return line;
    }

    /** Sometimes shares a preference (more readily with familiar, trusted people); the journal remembers it. */
    @Nullable
    static String reveal(Topics.Talk t) {
        double chance = 0.2 + t.bond.familiarity / 250.0 + Math.max(0, t.bond.trust) / 250.0 + t.m.t(Trait.SOCIABILITY) * 0.1;
        if (t.rnd.nextDouble() > chance) {
            return null;
        }
        int known = t.m.revealed.getOrDefault(t.player, 0);
        for (int bit = 0; bit < 5; bit++) {
            if ((known & 1 << bit) != 0) {
                continue;
            }
            String s = null;
            if (bit < 3 && bit < t.m.likes.size()) {
                s = t.pick("Between you and me, I love " + Preferences.name(t.m.likes.get(bit)) + ".",
                        "If you ever want to make my day, " + Preferences.name(t.m.likes.get(bit)) + " would do it.");
            } else if (bit == 3 && !t.m.dislike.isEmpty()) {
                s = "And whatever you do, don't bring me " + Preferences.name(t.m.dislike) + ". Can't stand it.";
            } else if (bit == 4 && !t.m.favoritePlace.isEmpty()) {
                s = "My favourite place? " + switch (t.m.favoritePlace) {
                    case "garden" -> "the fields.";
                    case "tavern" -> "the inn, of course.";
                    case "plaza" -> "the square.";
                    case "market" -> "the market.";
                    case "water" -> "the water's edge.";
                    case "workshop" -> "my workbench.";
                    case "landmark" -> "the old landmark.";
                    default -> "home.";
                };
            }
            if (s != null) {
                t.m.revealed.put(t.player, known | 1 << bit);
                return s;
            }
        }
        return null;
    }

    // ================================================================== gifts and help

    static boolean isFood(ItemStack s) {
        return s.getComponents().contains(DataComponentTypes.FOOD);
    }

    static boolean isMedicine(ItemStack s) {
        String id = Registries.ITEM.getId(s.getItem()).toString();
        return s.getItem() instanceof dev.underdeep.item.RemedyItem || id.equals("underdeep:mendleaf_sprig") || id.equals("minecraft:golden_apple");
    }

    static boolean matches(Mind.Request q, ItemStack s) {
        return switch (q.kind) {
            case FOOD -> isFood(s);
            case MEDICINE -> isMedicine(s);
            case MATERIAL -> Registries.ITEM.getId(s.getItem()).toString().equals(q.item);
        };
    }

    private static String gift(ServerPlayerEntity p, InhabitantEntity e, Topics.Talk t) {
        ItemStack held = p.getMainHandStack();
        if (held.isEmpty()) {
            return "You're not holding anything.";
        }
        Mind m = t.m;
        long now = t.now;
        boolean hungry = isFood(held) && m.need(Need.HUNGER) < 0.6;
        boolean injured = isMedicine(held) && m.health < 0.9f;
        if (!hungry && !injured && m.possessions.size() >= 9) {
            return "That's kind, but I've no room for anything else. Keep it.";
        }
        if (t.wary() && t.bond.trust <= -50) {
            return "I don't want anything from you.";
        }
        String id = Registries.ITEM.getId(held.getItem()).toString();
        int value = Preferences.value(m, held);
        ItemStack one = held.copyWithCount(1);
        if (!p.isCreative()) {
            held.decrement(1); // exactly one item changes hands
        }
        if (hungry) {
            m.satisfy(Need.HUNGER, 0.5);
        } else if (injured) {
            m.health = Math.min(1f, m.health + 0.35f);
            e.heal(e.getMaxHealth() * 0.35f);
        } else {
            m.possessions.add(one);
        }
        // a pending request this satisfies counts as help
        if (m.request != null && matches(m.request, one)) {
            return fulfil(p, e, t, one);
        }
        Bond b = t.bond;
        boolean recent = now - b.lastGift < UnderdeepConfig.get().giftCooldownTicks;
        b.lastGift = now;
        Goals.completeIf(m, Mind.GoalType.OBTAIN_ITEM, id, now, Society.memLimit());
        if (recent) {
            b.adjust(now, 1, 0, 1, 0, 0, 0, true);
            return "You're too generous. Really, you don't have to keep bringing me things.";
        }
        m.memories.remember(Memory.Type.GIFT_RECEIVED, "p:" + t.player, id, now, Society.memLimit(), value > 0 ? 1.2 : 0.8);
        m.learn(Mind.InfoType.GIFT_FROM_PLAYER, "p:" + t.player, t.playerName, now, Mind.Source.WITNESSED, 100, Society.knowLimit());
        String name = Preferences.name(id);
        switch (value) {
            case 2 -> {
                b.adjust(now, 4, 6, 12, 1, -5, -5, false);
                m.feel(Emotion.HAPPINESS, 0.35);
                markRevealed(m, t.player, id);
                return t.pick("Oh! " + cap(name) + "! How did you know? Thank you!", "This is perfect. I love " + name + ". Thank you, truly.");
            }
            case 1 -> {
                b.adjust(now, 3, 3, 6, 0, -3, -2, false);
                m.feel(Emotion.HAPPINESS, 0.15);
                return t.pick("That's very kind of you. Thank you.", "Oh, " + name + "? That's useful. Thanks!");
            }
            case -1 -> {
                b.adjust(now, 2, 0, -4, 0, 3, 0, false);
                m.feel(Emotion.EMBARRASSMENT, 0.2);
                markRevealed(m, t.player, id);
                return t.hi(Trait.HONESTY) && t.lo(Trait.PATIENCE) ? "Ugh, " + name + "? I can't stand the stuff. But... thanks, I suppose."
                        : "Oh. " + cap(name) + ". Um. Thank you? It's the thought that counts.";
            }
            default -> {
                b.adjust(now, 2, 1, 3, 0, -1, -1, false);
                return t.pick("For me? Thank you.", "A gift? That's thoughtful.");
            }
        }
    }

    private static void markRevealed(Mind m, UUID player, String id) {
        int bits = m.revealed.getOrDefault(player, 0);
        for (int i = 0; i < m.likes.size() && i < 3; i++) {
            if (m.likes.get(i).equals(id)) {
                bits |= 1 << i;
            }
        }
        if (m.dislike.equals(id)) {
            bits |= 8;
        }
        m.revealed.put(player, bits);
    }

    private static String describeRequest(Topics.Talk t) {
        Mind.Request q = t.m.request;
        if (q == null) {
            return "No, I'm alright. Thank you for asking.";
        }
        if (t.lo(Trait.INDEPENDENCE) || t.bond.trust > 20 || t.m.trait(Trait.PRIDE) < 60) {
            return switch (q.kind) {
                case FOOD -> "I haven't eaten in ages and my stores are empty. Any food at all would help.";
                case MEDICINE -> "I'm hurt and it isn't healing well. A sunleaf salve, or anything medicinal, would help.";
                case MATERIAL -> "I've been hoping to find some " + Preferences.name(q.item) + ". If you come across any...";
            };
        }
        return "I'll manage. ...Though if you had some " + (q.kind == Mind.Request.Kind.FOOD ? "food" : q.kind == Mind.Request.Kind.MEDICINE
                ? "medicine" : Preferences.name(q.item)) + ", I wouldn't say no.";
    }

    private static String help(ServerPlayerEntity p, InhabitantEntity e, Topics.Talk t) {
        ItemStack held = p.getMainHandStack();
        Mind.Request q = t.m.request;
        if (q == null || held.isEmpty() || !matches(q, held)) {
            return "That's not quite what I need, but thank you.";
        }
        ItemStack one = held.copyWithCount(1);
        if (!p.isCreative()) {
            held.decrement(1);
        }
        if (q.kind == Mind.Request.Kind.FOOD) {
            t.m.satisfy(Need.HUNGER, 0.5);
        } else if (q.kind == Mind.Request.Kind.MEDICINE) {
            t.m.health = Math.min(1f, t.m.health + 0.4f);
            e.heal(e.getMaxHealth() * 0.4f);
        } else if (t.m.possessions.size() < 9) {
            t.m.possessions.add(one);
        }
        return fulfil(p, e, t, one);
    }

    private static String fulfil(ServerPlayerEntity p, InhabitantEntity e, Topics.Talk t, ItemStack given) {
        Mind m = t.m;
        Mind.Request q = m.request;
        long now = t.now;
        q.count--;
        if (q.count > 0) {
            t.bond.adjust(now, 2, 3, 3, 0, 0, 0, false);
            return "Thank you! A little more and I'll be set.";
        }
        m.request = null;
        String id = Registries.ITEM.getId(given.getItem()).toString();
        Goals.completeIf(m, Mind.GoalType.OBTAIN_ITEM, id, now, Society.memLimit());
        m.memories.remember(Memory.Type.HELPED_BY, "p:" + t.player, id, now, Society.memLimit(), 1.3);
        m.learn(Mind.InfoType.PLAYER_HELPED, "p:" + t.player, t.playerName, now, Mind.Source.WITNESSED, 100, Society.knowLimit());
        t.bond.adjust(now, 5, 10, 9, 3, -10, -5, false);
        m.feel(Emotion.RELIEF, 0.4);
        m.feel(Emotion.HAPPINESS, 0.2);
        // a thank-you from their own belongings (moved, never created)
        ItemStack reward = ItemStack.EMPTY;
        for (ItemStack s : m.possessions) {
            if (!s.isEmpty() && !isFood(s) && s.getItem() != given.getItem()) {
                reward = s;
                break;
            }
        }
        if (!reward.isEmpty() && m.trait(Trait.GENEROSITY) > 45) {
            ItemStack out = reward.split(1);
            m.possessions.removeIf(ItemStack::isEmpty);
            if (!p.giveItemStack(out)) {
                p.dropItem(out, false);
            }
            return "You've no idea what this means. Here, take this. I insist.";
        }
        return t.pick("Thank you. I won't forget this.", "You're a good soul, " + t.playerName + ". Thank you.");
    }
}
