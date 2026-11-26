package dev.underdeep.civ.npc;

import dev.underdeep.civ.Civilization;
import dev.underdeep.civ.mind.Activity;
import dev.underdeep.civ.mind.Bond;
import dev.underdeep.civ.mind.Feelings.Emotion;
import dev.underdeep.civ.mind.Feelings.Need;
import dev.underdeep.civ.mind.Memory;
import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.mind.Preferences;
import dev.underdeep.civ.mind.Trait;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * The offline conversation library (Stage 5): topics with eligibility conditions, weights and line generators.
 * Lines vary with personality, emotion, needs, profession, civilization, familiarity with the player, current
 * activity, relationships and memories. A topic only refers to things that are true for this inhabitant
 * (real family members, real memories, real knowledge, places that exist). No language model is involved:
 * everything is composed from authored fragments.
 */
public final class Topics {
    private Topics() {
    }

    /** Menu groups the player can ask about. */
    public enum Group { GREETING, CHAT, ABOUT, NEWS, TOWN, PEOPLE, FEELING }

    /** Everything a line may draw on. */
    public static final class Talk {
        public SettlementRecord rec;
        public SettlementRecord.InhabitantRecord r;
        public Mind m;
        public UUID player;
        public String playerName = "traveller";
        public Bond bond;
        public Places places;
        public long now, timeOfDay;
        public Random rnd;

        public String pick(String... s) {
            return s[rnd.nextInt(s.length)];
        }

        public String who(int idx) {
            if (idx < 0 || idx >= rec.inhabitants.size()) {
                return "someone";
            }
            String n = rec.inhabitants.get(idx).name;
            int sp = n.indexOf(' ');
            return sp < 0 ? n : n.substring(0, sp);
        }

        public String job(int idx) {
            return idx < 0 || idx >= rec.inhabitants.size() ? "" : rec.inhabitants.get(idx).profession.replace('_', ' ');
        }

        public boolean alive(int idx) {
            return Society.alive(rec, idx);
        }

        public int kin(Bond.Kin k) {
            for (var e : m.people.entrySet()) {
                if (e.getValue().kin == k && alive(e.getKey())) {
                    return e.getKey();
                }
            }
            return -1;
        }

        /** Strongest bond matching a label set, or −1. */
        public int with(Bond.Label... labels) {
            int best = -1;
            double w = -9;
            for (var e : m.people.entrySet()) {
                Bond.Label l = e.getValue().label();
                for (Bond.Label x : labels) {
                    if (l == x && alive(e.getKey()) && e.getValue().warmth() > w) {
                        w = e.getValue().warmth();
                        best = e.getKey();
                    }
                }
            }
            return best;
        }

        public int rival() {
            for (var e : m.people.entrySet()) {
                if (e.getValue().rivalry && alive(e.getKey())) {
                    return e.getKey();
                }
            }
            return -1;
        }

        @Nullable
        public Memory recent(Memory.Type type, long within) {
            Memory best = null;
            for (Memory x : m.memories.list) {
                if (x.type == type && now - x.time < within && (best == null || x.time > best.time)) {
                    best = x;
                }
            }
            return best;
        }

        @Nullable
        public Memory aboutPlayer(Memory.Type type) {
            return m.memories.recall(type, "p:" + player, now);
        }

        @Nullable
        public Mind.Info news(Mind.InfoType type) {
            Mind.Info best = null;
            for (Mind.Info i : m.knowledge) {
                if (i.type == type && now - i.time < 24000L * 5 && (best == null || i.time > best.time)) {
                    best = i;
                }
            }
            return best;
        }

        public boolean hi(Trait t) {
            return m.trait(t) >= 65;
        }

        public boolean lo(Trait t) {
            return m.trait(t) <= 35;
        }

        public Activity activity() {
            Activity a = Activity.byOrdinal(m.activity);
            return a == null ? Activity.STROLL : a;
        }

        public String prof() {
            return r.profession.replace('_', ' ');
        }

        public boolean familiar() {
            return bond.familiarity >= 25;
        }

        public boolean warm() {
            return bond.affection >= 20 && bond.trust >= 10;
        }

        public boolean wary() {
            return bond.trust <= -20 || bond.resentment >= 30;
        }

        public String civName() {
            return rec.civ.id().replace('_', ' ');
        }

        /** Stage 6: this settlement's economy (null when the economy is off). */
        @Nullable
        public dev.underdeep.civ.econ.Economy econ() {
            return dev.underdeep.civ.econ.EconomyManager.economy(rec.id);
        }

        public String settlementName(long id) {
            dev.underdeep.civ.econ.Economy e = dev.underdeep.civ.econ.EconomyManager.economy(id);
            return e == null ? "far away" : e.name;
        }

        public boolean plaza() {
            return !places.plazas.isEmpty();
        }

        public boolean night() {
            long t = Math.floorMod(timeOfDay, 24000L);
            return t >= 13000 && t < 23000;
        }
    }

    public record Topic(String id, Group group, Predicate<Talk> ok, ToDoubleFunction<Talk> weight, Function<Talk, String> line) {
    }

    private static final List<Topic> ALL = new ArrayList<>();

    private static void add(String id, Group g, Predicate<Talk> ok, ToDoubleFunction<Talk> w, Function<Talk, String> line) {
        ALL.add(new Topic(id, g, ok, w, line));
    }

    public static List<Topic> all() {
        return ALL;
    }

    static {
        // ------------------------------------------------------------------ greetings (by familiarity, mood, culture)
        add("greet_stranger", Group.GREETING, t -> t.bond.familiarity < 8 && !t.wary(), t -> 1, t -> switch (t.rec.civ) {
            case STONEBORN -> t.pick("...You're not from here. State your business.", "Hm. A stranger. Speak.");
            case GLIMMERKIN -> t.pick("Greetings, traveller. I am " + t.who(t.r.index) + ", " + t.prof() + " of " + t.rec.name + ".",
                    "Welcome to " + t.rec.name + ". Mind the lamps; they are older than you.");
            case AMBERFOLK -> t.pick("Welcome, welcome! A new face in " + t.rec.name + "! I'm " + t.who(t.r.index) + ".",
                    "Oh, a visitor! Come in out of the dark. I'm " + t.who(t.r.index) + ".");
            case SPOREFOLK -> t.pick("Oh! Hello hello. You smell like the deep tunnels. I'm " + t.who(t.r.index) + ".",
                    "A new spore drifts in! I'm " + t.who(t.r.index) + ".");
            case FORGEKIN -> t.pick("Hah. A traveller. " + t.who(t.r.index) + ", " + t.prof() + ". Don't touch anything hot.",
                    "You're new. I'm " + t.who(t.r.index) + ". Need something forged?");
            case TIDE_DWELLERS -> t.pick("Ahoy! Fresh off the dark water? I'm " + t.who(t.r.index) + ".",
                    "Welcome to " + t.rec.name + ", stranger. I'm " + t.who(t.r.index) + ".");
            case ASTRALS -> t.pick("Ah. A visitor. I am " + t.who(t.r.index) + ". The lights told me someone was coming.",
                    "You are new to " + t.rec.name + ". I am " + t.who(t.r.index) + ".");
            default -> t.pick("Hello there. I'm " + t.who(t.r.index) + ". Welcome to " + t.rec.name + ".",
                    "Well met, traveller. " + t.who(t.r.index) + ", " + t.prof() + ".");
        });
        add("greet_shy", Group.GREETING, t -> t.bond.familiarity < 8 && t.lo(Trait.SOCIABILITY) && !t.wary(), t -> 1.5,
                t -> t.pick("Oh. Um. Hello. I'm " + t.who(t.r.index) + ".", "...Hi. " + t.who(t.r.index) + ". Did you need something?"));
        add("greet_acquaintance", Group.GREETING, t -> t.bond.familiarity >= 8 && !t.warm() && !t.wary(), t -> 1,
                t -> t.pick("Oh, it's you again, " + t.playerName + ".", "Hello again.", "Back in " + t.rec.name + ", " + t.playerName + "?"));
        add("greet_friend", Group.GREETING, t -> t.warm() && !t.wary(), t -> 1.2, t -> t.hi(Trait.HUMOR)
                ? t.pick("Look who it is! I was just thinking things were too quiet.", t.playerName + "! Still alive, I see. Good.")
                : t.pick("" + t.playerName + "! It's good to see you.", "There you are, " + t.playerName + ". Sit a moment?", "Ah, a friendly face. Hello!"));
        add("greet_close", Group.GREETING, t -> t.bond.affection >= 50 && t.bond.trust >= 40, t -> 2,
                t -> t.pick("My friend! I hoped you'd come by.", t.playerName + "! I saved you a seat.", "Ah, " + t.playerName + ". Everything's better when you're around."));
        add("greet_wary", Group.GREETING, t -> t.wary(), t -> 3, t -> t.hi(Trait.COURAGE)
                ? t.pick("You. I remember what you did. Keep your hands where I can see them.", "Say what you want and go.")
                : t.pick("P-please, I don't want trouble.", "Stay back. What do you want?"));
        add("greet_tired", Group.GREETING, t -> t.m.need(Need.REST) < 0.3, t -> 1.5,
                t -> t.pick("*yawns* Sorry. Long day.", "Hello... forgive me, I can barely keep my eyes open."));
        add("greet_sad", Group.GREETING, t -> t.m.emotion(Emotion.SADNESS) > 0.4, t -> 2,
                t -> t.pick("Oh... hello.", "Hi. Sorry, I'm not much company today."));
        add("greet_scared", Group.GREETING, t -> t.m.emotion(Emotion.FEAR) > 0.35, t -> 2.5,
                t -> t.pick("Did you see it? Something's been prowling near town.", "Shh! Sorry. I thought you were... something else."));
        add("greet_busy", Group.GREETING, t -> t.activity().cat == Activity.Cat.WORK && t.hi(Trait.DISCIPLINE), t -> 1.2,
                t -> t.pick("I'm working, but I can spare a moment.", "Make it quick, I've got " + t.prof() + "'s work to finish."));
        add("greet_night", Group.GREETING, t -> t.night() && t.activity() != Activity.PATROL && t.activity() != Activity.WATCH_POST, t -> 1.2,
                t -> t.pick("It's late, " + t.playerName + ". Shouldn't you be resting?", "Up at this hour? Me neither, apparently."));

        // ------------------------------------------------------------------ small talk
        add("work_general", Group.CHAT, t -> true, t -> 1, t -> switch (t.r.profession) {
            case "farmer" -> t.pick("The glowgrain is coming in well this cycle.", "Fields need water and patience. Mostly patience.");
            case "fisher" -> t.pick("The fish run deep this time of year.", "Caught a fillet as long as my arm yesterday. Well. Almost.");
            case "blacksmith", "smelter" -> t.pick("The forge never sleeps. Neither do I, some days.", "Good iron sings when you strike it.");
            case "guard" -> t.pick("Quiet watch so far. I like quiet.", "Keep to the lit paths and you'll be fine.");
            case "merchant" -> t.pick("Business is steady. Could always be steadier.", "Everything's for sale, friend. Almost everything.");
            case "healer" -> t.pick("Most of my patients just need rest and a warm meal.", "Mendleaf for cuts, rest for everything else.");
            case "scholar", "astronomer", "cartographer" -> t.pick("I've been reading old records. People here have lived through a lot.",
                    "Every tunnel has a story. I try to write them down.");
            case "innkeeper" -> t.pick("The inn's warm and the stew's hot. That's my whole job.", "You'd be amazed what people tell you over a bowl of stew.");
            case "cook", "baker" -> t.pick("Bread's in the oven. Smell that?", "Nobody fights over a good meal.");
            default -> t.pick("Work keeps my hands busy and my head clear.", "Being a " + t.prof() + " isn't glamorous, but it matters.");
        });
        add("work_proud", Group.CHAT, t -> t.m.skill >= 70 && t.hi(Trait.PRIDE), t -> 1.5,
                t -> t.pick("I'm the best " + t.prof() + " in " + t.rec.name + ". Ask anyone.", "Took me years to get this good. Worth every one."));
        add("work_tired", Group.CHAT, t -> t.m.need(Need.REST) < 0.35 && t.activity().cat == Activity.Cat.WORK, t -> 2,
                t -> t.pick("If I see one more day like this, I'll fall asleep standing.", "My back hurts. Don't get old, " + t.playerName + "."));
        add("work_bored", Group.CHAT, t -> t.m.need(Need.RECREATION) < 0.3, t -> 1.5,
                t -> t.pick("Same thing every day. I need a change of scenery.", "I'd give a lot for something interesting to happen."));
        add("food_hungry", Group.CHAT, t -> t.m.need(Need.HUNGER) < 0.35, t -> 2,
                t -> t.pick("I'm starving. I could eat a whole roast.", "Is that food I smell? No? Pity."));
        add("food_love", Group.CHAT, t -> !t.m.likes.isEmpty(), t -> 0.8, t -> t.pick(
                "Nothing beats a good " + Preferences.name(t.m.likes.get(0)) + ". Nothing.",
                "If you ever find " + Preferences.name(t.m.likes.get(0)) + ", think of me."));
        add("weather_underground", Group.CHAT, t -> true, t -> 0.8, t -> t.pick("The glow is dimmer today. Or maybe that's my eyes.",
                "Hear that dripping? It's been going for a week. Somewhere up there it's raining, they say.",
                "The air feels heavy. Something's shifting in the deep rock.", "Cool down here today. I like it."));
        add("time_morning", Group.CHAT, t -> Math.floorMod(t.timeOfDay, 24000L) < 2000 || Math.floorMod(t.timeOfDay, 24000L) > 23000, t -> 1,
                t -> t.pick("Morning. The lamps aren't even warm yet.", "Early start? Me too."));
        add("time_evening", Group.CHAT, t -> Math.floorMod(t.timeOfDay, 24000L) > 11000 && Math.floorMod(t.timeOfDay, 24000L) < 13000, t -> 1,
                t -> t.plaza() ? t.pick("Evening's my favourite. Everyone comes out to the square.", "Day's done. Time for the good part.")
                        : t.pick("Day's done. Time for the good part.", "Evening already? Where does the time go?"));
        add("joke", Group.CHAT, t -> t.hi(Trait.HUMOR), t -> 1.5, t -> t.pick("Why don't cave spiders play cards? Too many cheaters on the walls.",
                "I told my bed a joke. It didn't get it. It's a bit of a flat character.", "My last apprentice quit. Said the job was boring. I said, that's mining, kid."));
        add("deep_thought", Group.CHAT, t -> t.hi(Trait.CURIOSITY) && t.familiar(), t -> 1.2, t -> t.pick(
                "Do you ever wonder how far down it goes? Really goes?", "Every cavern was empty once. Someone walked into each one first.",
                "I think about the ones who lived here before us. What did they worry about?"));
        add("complaint", Group.CHAT, t -> t.lo(Trait.TOLERANCE) || t.lo(Trait.OPTIMISM), t -> 1, t -> t.pick(
                "Someone keeps leaving their tools on my bench. I know who.", "Nobody here sweeps anything. Ever.", "Prices have gone up again. Typical."));
        add("optimist", Group.CHAT, t -> t.hi(Trait.OPTIMISM) && t.m.emotion(Emotion.SADNESS) < 0.2, t -> 1, t -> t.pick(
                "Good day for it, whatever 'it' is!", "Things are looking up. Literally, if you stand in the shaft."));
        add("homebody", Group.CHAT, t -> t.hi(Trait.HOMEBODY), t -> 1, t -> t.pick("Why would anyone leave? Everything I need is right here.",
                "I haven't been past the edge of town in years. Don't intend to."));
        add("wanderlust", Group.CHAT, t -> t.hi(Trait.ADVENTUROUSNESS), t -> 1.2, t -> t.pick(
                "Someday I'll pack a bag and just go. Follow a tunnel and see.", "You travel? I envy you. Tell me where you've been."));
        add("lonely", Group.CHAT, t -> t.m.emotion(Emotion.LONELINESS) > 0.3 || t.m.need(Need.SOCIAL) < 0.25, t -> 2, t -> t.pick(
                "Thanks for stopping. Not many people do.", "It's been quiet. Too quiet. Stay a while?"));
        add("ask_travels", Group.CHAT, t -> t.hi(Trait.CURIOSITY), t -> 1, t -> t.pick("Where have you come from? Is it true there are glowing forests out there?",
                "Have you seen the frozen caverns? I've only heard stories."));
        add("culture", Group.CHAT, t -> true, t -> 0.9, t -> switch (t.rec.civ) {
            case ROOTFOLK -> t.pick("We plant a seed for every child born. The roots remember them.", "Never cut a living root without asking first. That's our way.");
            case FORGEKIN -> t.pick("A Forgekin's name is in their work. Mine's on half the hinges in town.", "Fire is honest. It shows you what metal really is.");
            case TIDE_DWELLERS -> t.pick("The dark water gives and takes. We thank it for both.", "Every boat here has a name. Never sail a nameless boat.");
            case SPOREFOLK -> t.pick("We share everything. Food, songs, spores. Mostly spores.", "Mushrooms teach patience. They grow when they're ready.");
            case GLIMMERKIN -> t.pick("A crystal remembers every light it has held. So do we.", "We speak softly near the crystals. They hum back.");
            case STONEBORN -> t.pick("Stone endures. So do we.", "We don't waste words. Or water.");
            case AMBERFOLK -> t.pick("A guest is a gift. That's what my grandmother said.", "Amber keeps things safe for a thousand years. We try to do the same for each other.");
            case ASTRALS -> t.pick("We map the lights in the ceiling. They move, if you're patient enough.", "Our elders say the lights above are older than the caverns.");
        });
        add("young_play", Group.CHAT, t -> t.m.age == Mind.Age.YOUNG, t -> 3, t -> t.pick("Wanna see how far I can throw a pebble? Really far!",
                "My parents say I can't go past the last lamp. But I could. If I wanted.", "Are you an adventurer? Do you have a sword? Can I hold it?"));
        add("elder_story", Group.CHAT, t -> t.m.age == Mind.Age.ELDER, t -> 2, t -> t.pick(
                "When I was young, " + t.rec.name + " was half this size. Half! We were lucky to have one lamp.",
                "I've seen a lot of years come and go down here. The caverns change slower than people do.",
                "Sit, sit. Young folk always rushing. The rock isn't going anywhere."));
        add("favorite_place", Group.CHAT, t -> !t.m.favoritePlace.isEmpty(), t -> 0.8, t -> switch (t.m.favoritePlace) {
            case "garden" -> "When I need quiet, I go sit by the fields. Growing things calm me.";
            case "tavern" -> "You'll usually find me at the inn in the evening. Best place in town.";
            case "plaza" -> "I like the square. You see everyone pass through eventually.";
            case "market" -> "I love wandering the market. Even when I'm not buying.";
            case "water" -> "The water's edge. That's where I go to think.";
            case "workshop" -> "Honestly? I'm happiest at my workbench.";
            case "landmark" -> "There's a spot by the old landmark I like. It feels like it's been there forever.";
            default -> "Home. I'm a simple creature.";
        });

        // ------------------------------------------------------------------ profession-specific
        add("prof_guard", Group.CHAT, t -> t.r.profession.equals("guard"), t -> 1.5, t -> t.news(Mind.InfoType.PREDATOR_SEEN) != null
                ? "We've had a " + t.news(Mind.InfoType.PREDATOR_SEEN).detail.toLowerCase(Locale.ROOT) + " sniffing around. I'm keeping watch."
                : t.pick("Nothing gets past me. Well. Nothing big.", "Stay on the lit paths after dark."));
        add("prof_healer", Group.CHAT, t -> t.r.profession.equals("healer") || t.r.profession.equals("herbalist"), t -> 1.5, t -> t.pick(
                "If you're hurt, come see me. Don't be proud about it.", "Sunleaf salve. Never leave home without it."));
        add("prof_merchant", Group.CHAT, t -> t.r.profession.equals("merchant"), t -> 1.5, t -> t.pick(
                "Buy low, sell high, and never trust a smiling customer. Present company excepted.", "Want to see what I've got? Just ask to trade."));
        add("prof_farmer", Group.CHAT, t -> t.r.profession.equals("farmer") || t.r.profession.equals("rancher"), t -> 1.5, t -> t.pick(
                "Crops don't care if you're tired. They need tending anyway.", "If the rats get into the stores again, I'll scream."));
        add("prof_scholar", Group.CHAT, t -> t.r.profession.equals("scholar") || t.r.profession.equals("astronomer") || t.r.profession.equals("cartographer"),
                t -> 1.5, t -> t.pick("Knowledge is like light down here: precious, and it spreads.", "I'm mapping the eastern tunnels. Slowly."));
        add("prof_innkeeper", Group.CHAT, t -> t.r.profession.equals("innkeeper"), t -> 1.5, t -> t.pick(
                "Need a bed? A meal? A shoulder to cry on? I do all three.", "Everyone comes through my door eventually."));
        add("prof_craft", Group.CHAT, t -> t.r.work != null && t.activity().cat == Activity.Cat.WORK, t -> 0.8, t -> t.pick(
                "Hand me that— oh, you're not my apprentice. Never mind.", "Careful there, that's still curing."));

        // ------------------------------------------------------------------ about themselves
        add("about_job", Group.ABOUT, t -> true, t -> 1, t -> t.m.skill > 70
                ? "I'm a " + t.prof() + ". Been at it long enough that my hands know the work better than my head."
                : t.m.skill < 40 ? "I'm a " + t.prof() + ". Still learning, honestly." : "I'm a " + t.prof() + ". It's good, honest work.");
        add("about_family_partner", Group.ABOUT, t -> t.kin(Bond.Kin.PARTNER) >= 0, t -> 2, t -> t.pick(
                "My partner " + t.who(t.kin(Bond.Kin.PARTNER)) + " is the " + t.job(t.kin(Bond.Kin.PARTNER)) + ". Couldn't do without them.",
                "Have you met " + t.who(t.kin(Bond.Kin.PARTNER)) + "? We share a home. And a lot of arguments about whose turn it is to cook."));
        add("about_family_children", Group.ABOUT, t -> t.kin(Bond.Kin.CHILD) >= 0, t -> 2, t -> t.pick(
                "My little " + t.who(t.kin(Bond.Kin.CHILD)) + " is growing so fast.", t.who(t.kin(Bond.Kin.CHILD)) + " wants to be a " + t.prof() + " like me. We'll see."));
        add("about_family_parent", Group.ABOUT, t -> t.kin(Bond.Kin.PARENT) >= 0, t -> 2, t -> t.pick(
                t.who(t.kin(Bond.Kin.PARENT)) + " is my parent. Always telling me what to do.", "Don't tell " + t.who(t.kin(Bond.Kin.PARENT)) + " I said this, but they're usually right."));
        add("about_family_sibling", Group.ABOUT, t -> t.kin(Bond.Kin.SIBLING) >= 0, t -> 2, t -> t.pick(
                "My sibling " + t.who(t.kin(Bond.Kin.SIBLING)) + " and I used to race through the tunnels.", t.who(t.kin(Bond.Kin.SIBLING)) + "? That's my sibling. Annoying. Love them."));
        add("about_alone", Group.ABOUT, t -> t.kin(Bond.Kin.PARTNER) < 0 && t.kin(Bond.Kin.CHILD) < 0 && t.kin(Bond.Kin.PARENT) < 0
                && t.kin(Bond.Kin.SIBLING) < 0, t -> 1, t -> t.hi(Trait.INDEPENDENCE) ? "I live on my own. I like it that way."
                : "No family here. The town is family enough, mostly.");
        add("about_origin", Group.ABOUT, t -> true, t -> 0.7, t -> "I've lived in " + t.rec.name + " all my life. " + (t.hi(Trait.ADVENTUROUSNESS)
                ? "Too long, maybe." : "Wouldn't trade it."));
        add("about_goal_skill", Group.ABOUT, t -> t.m.goal(Mind.GoalType.IMPROVE_SKILL) != null, t -> 2,
                t -> "I'm trying to get better at my craft. Practising every chance I get.");
        add("about_goal_explore", Group.ABOUT, t -> t.m.goal(Mind.GoalType.EXPLORE_NEARBY) != null, t -> 2,
                t -> "I want to see what's past the edge of town. Just a little way. Just to see.");
        add("about_goal_friend", Group.ABOUT, t -> t.m.goal(Mind.GoalType.MAKE_FRIEND) != null, t -> 2,
                t -> "Honestly? I'd like more friends. It's harder than it sounds.");
        add("about_goal_item", Group.ABOUT, t -> t.m.goal(Mind.GoalType.OBTAIN_ITEM) != null, t -> 2,
                t -> "I've been hoping to get my hands on some " + Preferences.name(t.m.goal(Mind.GoalType.OBTAIN_ITEM).target) + ".");
        add("about_goal_done", Group.ABOUT, t -> t.recent(Memory.Type.GOAL_DONE, 24000L * 2) != null, t -> 2.5,
                t -> "I finally did it! Something I've been working toward for days. Feels good.");
        add("about_skill_low", Group.ABOUT, t -> t.m.skill < 35, t -> 1, t -> "I'm not very good yet. Everyone has to start somewhere.");

        // ------------------------------------------------------------------ news (only what they really know)
        add("news_predator", Group.NEWS, t -> t.news(Mind.InfoType.PREDATOR_SEEN) != null, t -> 2, t -> {
            Mind.Info i = t.news(Mind.InfoType.PREDATOR_SEEN);
            String what = i.detail.isEmpty() ? "something dangerous" : "a " + i.detail.toLowerCase(Locale.ROOT);
            return i.source == Mind.Source.WITNESSED ? "I saw " + what + " near town with my own eyes. Be careful out there."
                    : "Someone told me there's " + what + " around. " + (i.confidence < 50 ? "Might just be talk." : "I believe it.");
        });
        add("news_injured", Group.NEWS, t -> { Mind.Info i = t.news(Mind.InfoType.PERSON_INJURED); return i != null && Society.index(i.subject) != t.r.index; },
                t -> 2, t -> t.who(Society.index(t.news(Mind.InfoType.PERSON_INJURED).subject)) + " got hurt recently. I hope they're on the mend.");
        add("news_lost", Group.NEWS, t -> t.news(Mind.InfoType.PERSON_LOST) != null, t -> 2.5, t -> {
            Mind.Info i = t.news(Mind.InfoType.PERSON_LOST);
            return "Did you hear about " + t.who(Society.index(i.subject)) + "? They're gone. " + (t.m.emotion(Emotion.SADNESS) > 0.3
                    ? "I can't stop thinking about it." : "Hard times.");
        });
        add("news_player_good", Group.NEWS, t -> { Mind.Info i = t.news(Mind.InfoType.PLAYER_HELPED); return i != null && !i.subject.equals("p:" + t.player); },
                t -> 1.5, t -> "I hear " + t.news(Mind.InfoType.PLAYER_HELPED).detail + " has been helping people around here. Good sort, apparently.");
        add("news_player_bad", Group.NEWS, t -> { Mind.Info i = t.news(Mind.InfoType.PLAYER_ATTACKED); return i != null && !i.subject.equals("p:" + t.player); },
                t -> 1.5, t -> "Watch out for " + t.news(Mind.InfoType.PLAYER_ATTACKED).detail + ". I heard they hurt someone.");
        add("news_gift", Group.NEWS, t -> { Mind.Info i = t.news(Mind.InfoType.GIFT_FROM_PLAYER); return i != null && !i.subject.equals("p:" + t.player); },
                t -> 1, t -> "Someone told me " + t.news(Mind.InfoType.GIFT_FROM_PLAYER).detail + " brought gifts for folk here. Generous.");
        add("news_discovery", Group.NEWS, t -> t.news(Mind.InfoType.DISCOVERY) != null, t -> 1.5, t -> "People are talking about a discovery out in the caverns. "
                + (t.hi(Trait.CURIOSITY) ? "I'd love to see it!" : "Not my business."));
        add("news_nothing", Group.NEWS, t -> true, t -> 0.2, t -> t.pick("Nothing new, really. Quiet times.", "News? Not that I've heard.",
                "If anything happens, the innkeeper hears first. Ask there."));
        add("news_argument", Group.NEWS, t -> t.recent(Memory.Type.ARGUMENT, 24000L * 2) != null, t -> 1.5, t -> {
            Memory x = t.recent(Memory.Type.ARGUMENT, 24000L * 2);
            int who = Society.index(x.who);
            return t.hi(Trait.FORGIVENESS) ? "I had words with " + t.who(who) + ". I should apologise, really."
                    : "Had a row with " + t.who(who) + ". They know what they did.";
        });

        // ------------------------------------------------------------------ the town
        add("town_name", Group.TOWN, t -> true, t -> 0.8, t -> t.rec.name + " is a " + t.rec.category.id() + " of the " + t.civName() + ". "
                + (t.hi(Trait.PRIDE) ? "Finest in the deep." : "It's home."));
        add("town_inn", Group.TOWN, t -> !t.places.taverns.isEmpty(), t -> 1, t -> "If you're hungry, the inn serves food all day. Evenings are lively.");
        add("town_market", Group.TOWN, t -> !t.places.markets.isEmpty(), t -> 1, t -> "The market's where the traders are. Prices are fair, mostly.");
        add("town_library", Group.TOWN, t -> !t.places.libraries.isEmpty(), t -> 1, t -> "We have a hall of records. The scholars guard it like treasure.");
        add("town_clinic", Group.TOWN, t -> !t.places.clinics.isEmpty(), t -> 1, t -> "If you get hurt, there's a clinic. Go before it gets worse.");
        add("town_fields", Group.TOWN, t -> !t.places.farms.isEmpty(), t -> 0.8, t -> "Our fields feed the whole town. Don't trample the crops.");
        add("town_docks", Group.TOWN, t -> !t.places.docks.isEmpty(), t -> 0.8, t -> "The docks are on the water. Fishers go out early.");
        add("town_guards", Group.TOWN, t -> !t.places.guards.isEmpty(), t -> 0.6, t -> "The guards keep watch at their posts. They're good people. Mostly.");
        add("town_size", Group.TOWN, t -> true, t -> 0.6, t -> "About " + t.rec.alive() + " of us live here now. " + (t.rec.alive() < t.rec.inhabitants.size()
                ? "There used to be more." : "Everyone knows everyone."));

        // ------------------------------------------------------------------ people
        add("people_friend", Group.PEOPLE, t -> t.with(Bond.Label.BEST_FRIEND, Bond.Label.CLOSE_FRIEND, Bond.Label.FRIEND) >= 0, t -> 2, t -> {
            int f = t.with(Bond.Label.BEST_FRIEND, Bond.Label.CLOSE_FRIEND, Bond.Label.FRIEND);
            return t.m.bond(f).label() == Bond.Label.BEST_FRIEND ? t.who(f) + " is my best friend. We'd do anything for each other."
                    : "You should meet " + t.who(f) + ", the " + t.job(f) + ". We get on well.";
        });
        add("people_rival", Group.PEOPLE, t -> t.rival() >= 0, t -> 2, t -> t.hi(Trait.COMPETITIVENESS)
                ? t.who(t.rival()) + " thinks they're better than me. They're wrong."
                : "Me and " + t.who(t.rival()) + "? We don't see eye to eye. Never have.");
        add("people_mentor", Group.PEOPLE, t -> t.with(Bond.Label.MENTOR) >= 0, t -> 1.5,
                t -> "Everything I know, " + t.who(t.with(Bond.Label.MENTOR)) + " taught me. Patient soul.");
        add("people_apprentice", Group.PEOPLE, t -> t.with(Bond.Label.APPRENTICE) >= 0, t -> 1.5, t -> t.hi(Trait.PATIENCE)
                ? t.who(t.with(Bond.Label.APPRENTICE)) + " is learning quickly. I'm proud of them."
                : t.who(t.with(Bond.Label.APPRENTICE)) + " still can't hold a tool properly. Give it time, I keep telling myself.");
        add("people_disliked", Group.PEOPLE, t -> t.with(Bond.Label.DISLIKED, Bond.Label.DISTRUSTED) >= 0 && t.familiar(), t -> 1.2,
                t -> "Between you and me, I don't trust " + t.who(t.with(Bond.Label.DISLIKED, Bond.Label.DISTRUSTED)) + ".");
        add("people_coworker", Group.PEOPLE, t -> t.with(Bond.Label.COWORKER, Bond.Label.TRUSTED_ASSOCIATE) >= 0, t -> 1,
                t -> "I work alongside " + t.who(t.with(Bond.Label.COWORKER, Bond.Label.TRUSTED_ASSOCIATE)) + ". Reliable.");
        add("people_lost", Group.PEOPLE, t -> t.m.memories.recall(Memory.Type.LOST_FAMILY, null, t.now) != null
                || t.m.memories.recall(Memory.Type.LOST_FRIEND, null, t.now) != null, t -> 2, t -> {
            Memory x = t.m.memories.recall(Memory.Type.LOST_FAMILY, null, t.now);
            if (x == null) {
                x = t.m.memories.recall(Memory.Type.LOST_FRIEND, null, t.now);
            }
            return "I still miss " + t.who(Society.index(x.who)) + ". Every day.";
        });
        add("people_reconciled", Group.PEOPLE, t -> t.recent(Memory.Type.RECONCILED, 24000L * 3) != null, t -> 1.5,
                t -> "I made up with " + t.who(Society.index(t.recent(Memory.Type.RECONCILED, 24000L * 3).who)) + ". Feels lighter.");
        add("people_nobody", Group.PEOPLE, t -> true, t -> 0.2, t -> "I keep to myself, mostly.");

        // ------------------------------------------------------------------ feelings and the player's history with them
        add("feel_happy", Group.FEELING, t -> t.m.mood() == Emotion.HAPPINESS || t.m.mood() == Emotion.CONTENTMENT, t -> 1, t -> t.pick(
                "I'm well! Truly.", "Can't complain. Well, I could, but I won't."));
        add("feel_sad", Group.FEELING, t -> t.m.emotion(Emotion.SADNESS) > 0.3, t -> 2, t -> "Not great, honestly. It's been a hard few days.");
        add("feel_afraid", Group.FEELING, t -> t.m.emotion(Emotion.FEAR) > 0.3, t -> 2, t -> "Scared, if I'm honest. Something's out there.");
        add("feel_angry", Group.FEELING, t -> t.m.emotion(Emotion.ANGER) > 0.3 || t.m.emotion(Emotion.FRUSTRATION) > 0.35, t -> 2,
                t -> "Frustrated. Some days nothing goes right.");
        add("feel_hurt", Group.FEELING, t -> t.m.health < 0.6f, t -> 3, t -> t.hi(Trait.PRIDE) ? "It's just a scratch. ...A big scratch."
                : "I'm hurt. Healing slowly. Could use some medicine.");
        add("feel_recovered", Group.FEELING, t -> t.m.health > 0.9f && t.recent(Memory.Type.DANGER, 24000L * 2) != null, t -> 1.5,
                t -> "Better now. That scare a while back shook me, though.");
        add("feel_tired", Group.FEELING, t -> t.m.need(Need.REST) < 0.3, t -> 2, t -> "Exhausted. I need a long sleep.");
        add("player_first", Group.FEELING, t -> t.aboutPlayer(Memory.Type.FIRST_MEETING) != null && t.familiar(), t -> 0.6,
                t -> "I remember when you first walked into " + t.rec.name + ". Seems like a while ago now.");
        add("player_gift", Group.FEELING, t -> t.aboutPlayer(Memory.Type.GIFT_RECEIVED) != null, t -> 1.2,
                t -> "I still have the " + Preferences.name(t.aboutPlayer(Memory.Type.GIFT_RECEIVED).detail) + " you gave me. Thank you again.");
        add("player_helped", Group.FEELING, t -> t.aboutPlayer(Memory.Type.HELPED_BY) != null, t -> 1.5,
                t -> "I haven't forgotten how you helped me. I owe you.");
        add("player_attacked", Group.FEELING, t -> t.aboutPlayer(Memory.Type.ATTACKED_BY) != null, t -> 3,
                t -> t.bond.trust < 0 ? "You hurt me. I haven't forgotten." : "You hurt me once. I'm trying to let it go.");
        add("player_companion", Group.FEELING, t -> t.aboutPlayer(Memory.Type.JOINED_COMPANY) != null, t -> 1.5,
                t -> "Travelling with you was something. I think about it often.");
        add("player_rescued", Group.FEELING, t -> t.aboutPlayer(Memory.Type.DEFENDED_BY) != null || t.aboutPlayer(Memory.Type.RESCUED) != null, t -> 2,
                t -> "You stood between me and danger. I won't forget that.");
    }

    static {
        // ------------------------------------------------------------------ Stage 6: economy and community (facts from the ledger and events)
        add("news_econ", Group.NEWS, t -> t.news(Mind.InfoType.ECON) != null, t -> 2.2, t -> {
            Mind.Info i = t.news(Mind.InfoType.ECON);
            return (i.source == Mind.Source.WITNESSED ? t.pick("You know, ", "Well, ", "") : t.pick("Someone told me ", "I heard that ", "Word is "))
                    + i.detail + ".";
        });
        add("news_moved", Group.PEOPLE, t -> t.news(Mind.InfoType.PERSON_MOVED) != null, t -> 2, t -> t.news(Mind.InfoType.PERSON_MOVED).detail + ". "
                + t.pick("I hope they're happy there.", "It's quieter without them.", "Good for them, I suppose."));
        add("town_prosperity", Group.TOWN, t -> t.econ() != null, t -> 1.2, t -> {
            double sc = t.econ().score;
            return sc > 0.7 ? t.pick("Times are good in " + t.rec.name + ". Plenty of work, plenty to eat.", "We're doing well. People keep moving here.")
                    : sc < 0.35 ? t.pick("Things are hard here lately.", "We've seen better days, honestly.")
                    : t.pick("We get by. Some weeks better than others.", "Not rich, not poor. It's a fair place to live.");
        });
        add("town_building", Group.TOWN, t -> t.econ() != null && t.econ().projects.stream().anyMatch(p -> p.active()
                && p.kind != dev.underdeep.civ.econ.Project.Kind.REPAIR), t -> 2.2, t -> {
            dev.underdeep.civ.econ.Project p = t.econ().projects.stream().filter(q -> q.active()
                    && q.kind != dev.underdeep.civ.econ.Project.Kind.REPAIR).findFirst().get();
            String what = p.kind.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            return switch (p.status) {
                case BUILDING -> "They're building a new " + what + " out by the edge of town. Come and see.";
                case MATERIALS -> "There's going to be a new " + what + ", once they've gathered the materials.";
                default -> "The council is talking about a new " + what + ". " + cap(p.reason) + ".";
            };
        });
        add("town_imports", Group.TOWN, t -> t.econ() != null && topImport(t) != null, t -> 1.2, t -> {
            dev.underdeep.civ.econ.Goods.Good g = dev.underdeep.civ.econ.Goods.get(topImport(t));
            return "Most of our " + g.name() + " comes in by caravan. We can't make enough of it ourselves.";
        });
        add("work_idle", Group.CHAT, t -> t.econ() != null && t.econ().workplaces.containsKey(t.r.workplace)
                && t.econ().workplaces.get(t.r.workplace).status == dev.underdeep.civ.econ.Economy.WorkStatus.IDLE
                && !t.econ().workplaces.get(t.r.workplace).missing.isEmpty(), t -> 2.5, t -> {
            String miss = t.econ().workplaces.get(t.r.workplace).missing;
            dev.underdeep.civ.econ.Goods.Good g = miss.startsWith("@") ? null : dev.underdeep.civ.econ.Goods.get(miss);
            String what = g != null ? g.name() : miss.equals("@FUEL") ? "fuel" : miss.substring(1).toLowerCase(Locale.ROOT);
            return t.pick("We've run out of " + what + ". I can't work without it.", "No " + what + " anywhere. The workshop's been idle.");
        });
        add("work_busy", Group.CHAT, t -> t.econ() != null && t.econ().workplaces.containsKey(t.r.workplace)
                && t.econ().workplaces.get(t.r.workplace).output > 1, t -> 1, t -> t.pick("We've been busy. Everything we make sells.",
                "Orders keep coming in. Good for business, hard on my back."));
        add("caravan_trip", Group.CHAT, t -> t.m.journey != 0, t -> 5, t -> t.pick("We're on the road with a load of goods. Long way yet.",
                "Caravan work. Keep your eyes open, the tunnels aren't always friendly.", "Mind the pack beasts, they bite."));
        add("celebration_now", Group.FEELING, t -> t.econ() != null && t.econ().gatheringUntil > t.now, t -> 3, t -> "We're celebrating — "
                + t.econ().gatheringReason + "! Come to the square.");
        add("hard_times", Group.FEELING, t -> t.econ() != null && t.econ().score < 0.35, t -> 2, t -> "Times are hard. Food's dear and there's not much work.");
        add("leader_role", Group.ABOUT, t -> t.econ() != null && t.econ().leaders.contains(t.r.index), t -> 2.5, t -> "I'm one of "
                + t.econ().leadership + ". Someone has to keep the stores counted and the roofs fixed.");
        add("moved_here", Group.ABOUT, t -> !t.r.cameFrom.isEmpty(), t -> 2, t -> t.r.cameFrom.equals("afar") ? "I came here from far away, looking for work."
                : "I moved here from " + t.settlementName(Long.parseLong(t.r.cameFrom.substring(0, t.r.cameFrom.indexOf(':')))) + ". New start.");
        add("friends_elsewhere", Group.PEOPLE, t -> t.m.elsewhere.values().stream().anyMatch(b -> b.affection > 30), t -> 1.5, t -> {
            String key = t.m.elsewhere.entrySet().stream().filter(en -> en.getValue().affection > 30).findFirst().get().getKey();
            return "I still miss my friends back in " + t.settlementName(Long.parseLong(key.substring(0, key.indexOf(':')))) + ".";
        });
    }

    @Nullable
    static String topImport(Talk t) {
        dev.underdeep.civ.econ.Economy e = t.econ();
        String best = null;
        double bi = 0.3;
        for (var en : e.stock.entrySet()) {
            if (en.getValue().rI > bi && en.getValue().rI > en.getValue().rP && dev.underdeep.civ.econ.Goods.get(en.getKey()) != null) {
                bi = en.getValue().rI;
                best = en.getKey();
            }
        }
        return best;
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Picks a topic of a group for this conversation (never the one just used, if there is any alternative). */
    @Nullable
    public static Topic choose(Group g, Talk t, @Nullable String last) {
        List<Topic> ok = new ArrayList<>();
        List<Double> w = new ArrayList<>();
        double total = 0;
        for (Topic x : ALL) {
            if (x.group != g || x.id.equals(last)) {
                continue;
            }
            boolean eligible;
            try {
                eligible = x.ok.test(t);
            } catch (RuntimeException e) {
                eligible = false;
            }
            if (eligible) {
                double wt = Math.max(0.01, x.weight.applyAsDouble(t));
                ok.add(x);
                w.add(wt);
                total += wt;
            }
        }
        if (ok.isEmpty()) {
            return null;
        }
        double p = t.rnd.nextDouble() * total;
        for (int i = 0; i < ok.size(); i++) {
            p -= w.get(i);
            if (p <= 0) {
                return ok.get(i);
            }
        }
        return ok.get(ok.size() - 1);
    }

    /** Applies cultural and personal voice to a line. */
    public static String voice(Talk t, String line) {
        String s = line;
        if (t.rec.civ == Civilization.STONEBORN) {
            s = s.replace("!", ".");
        }
        if (t.m.emotion(Emotion.STRESS) > 0.4 && t.rnd.nextInt(3) == 0) {
            s = "Sorry, I'm on edge. " + s;
        } else if (t.m.emotion(Emotion.SADNESS) > 0.45 && t.rnd.nextInt(3) == 0) {
            s = "*sighs* " + s;
        } else if (t.hi(Trait.HUMOR) && t.m.emotion(Emotion.HAPPINESS) > 0.3 && t.rnd.nextInt(4) == 0) {
            s = s + " Ha!";
        } else if (t.lo(Trait.SOCIABILITY) && !t.familiar() && s.length() > 90 && s.indexOf(". ") > 0) {
            s = s.substring(0, s.indexOf(". ") + 1); // the reserved say less to strangers
        }
        return s;
    }
}
