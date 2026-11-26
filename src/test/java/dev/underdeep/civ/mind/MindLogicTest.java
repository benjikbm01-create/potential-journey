package dev.underdeep.civ.mind;

import dev.underdeep.civ.mind.Feelings.Emotion;
import dev.underdeep.civ.mind.Feelings.Need;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure logic of Stage 5 minds: bonds, memory consolidation, needs/emotions, utility decisions with commitment. */
class MindLogicTest {

    private static Mind average() {
        Mind m = new Mind();
        for (int i = 0; i < Trait.COUNT; i++) {
            m.traits[i] = 50;
        }
        for (Need n : Need.values()) {
            m.needs[n.ordinal()] = 0.8f;
        }
        m.needs[Need.SAFETY.ordinal()] = 1f;
        m.health = 1f;
        m.needs[Need.HEALTH.ordinal()] = 1f;
        return m;
    }

    private static Brain.Context ctx(String profession, long timeOfDay) {
        Brain.Context c = new Brain.Context();
        c.profession = profession;
        c.now = 100_000;
        c.timeOfDay = timeOfDay;
        c.hasWork = c.hasFarm = c.hasTavern = c.hasMarket = c.hasEdge = true;
        c.personalSeed = 1; // odd: day shift
        return c;
    }

    @Test
    void trivialInteractionsHaveDiminishingReturns() {
        Bond b = new Bond();
        for (int i = 0; i < 20; i++) {
            b.adjust(24000, 2, 1, 1, 0, 0, 0, true);
        }
        assertTrue(b.affection <= 4, "affection from repeated small talk in one day is capped, got " + b.affection);
        b.adjust(24000, 0, 0, 12, 0, 0, 0, false);
        assertTrue(b.affection >= 12, "significant events are never diminished");
        int before = b.affection;
        b.adjust(48000, 0, 0, 1, 0, 0, 0, true);
        assertEquals(before + 1, b.affection, "a new day resets the diminishing returns");
    }

    @Test
    void relationshipLabelsFollowDimensions() {
        Bond b = new Bond();
        assertEquals(Bond.Label.STRANGER, b.label());
        b.familiarity = 30;
        b.affection = 25;
        assertEquals(Bond.Label.FRIEND, b.label());
        b.familiarity = 75;
        b.affection = 75;
        b.trust = 65;
        assertEquals(Bond.Label.BEST_FRIEND, b.label());
        b.rivalry = true;
        assertEquals(Bond.Label.RIVAL, b.label());
        Bond e = new Bond();
        e.resentment = 80;
        e.affection = -60;
        assertEquals(Bond.Label.ENEMY, e.label());
        Bond k = new Bond();
        k.kin = Bond.Kin.SIBLING;
        assertEquals(Bond.Label.FAMILY, k.label());
        assertTrue(Bond.Label.values().length >= 12);
    }

    @Test
    void memoriesConsolidateAndStayBounded() {
        Memory.Book book = new Memory.Book();
        for (int i = 0; i < 5; i++) {
            book.remember(Memory.Type.GOOD_TALK, "p:x", "", 1000 + i * 100, 10, 1);
        }
        assertEquals(1, book.list.size(), "similar recent memories consolidate");
        assertEquals(5, book.list.get(0).count);
        for (int i = 0; i < 30; i++) {
            book.remember(Memory.Type.WILDLIFE_SEEN, "s:" + i, "", 2000 + i, 10, 1);
        }
        assertEquals(10, book.list.size(), "bounded storage");
        Memory last = book.remember(Memory.Type.ATTACKED_BY, "p:y", "", 5000, 10, 1.2);
        assertTrue(book.list.contains(last), "the newest memory is never the one evicted");
        assertTrue(book.has(Memory.Type.ATTACKED_BY, "p:y"));
        assertTrue(Memory.Type.values().length >= 20);
    }

    @Test
    void needsDecayAndEmotionsFade() {
        Mind m = average();
        m.feel(Emotion.ANGER, 0.8);
        Brain.advance(m, 12_000);
        assertTrue(m.need(Need.HUNGER) < 0.8, "hunger grows over half a day");
        assertTrue(m.emotion(Emotion.ANGER) < 0.4, "anger fades, was 0.8 now " + m.emotion(Emotion.ANGER));
        for (Need n : Need.values()) {
            assertTrue(m.need(n) >= 0 && m.need(n) <= 1, "needs are bounded");
        }
    }

    @Test
    void sleepsAtNightWorksByDayEatsWhenHungry() {
        Random r = new Random(7);
        Mind m = average();
        assertEquals(Activity.SLEEP, Brain.decide(m, ctx("farmer", 18000), r).activity());
        Mind w = average();
        w.needs[Need.PURPOSE.ordinal()] = 0.3f;
        assertEquals(Activity.TEND_FIELDS, Brain.decide(w, ctx("farmer", 3500), r).activity());
        Mind h = average();
        h.needs[Need.HUNGER.ordinal()] = 0.05f;
        Activity a = Brain.decide(h, ctx("farmer", 3500), r).activity();
        assertTrue(a == Activity.EAT_HOME || a == Activity.EAT_TAVERN, "a starving farmer eats first, got " + a);
    }

    @Test
    void dangerInterruptsAndPersonalityMatters() {
        Random r = new Random(3);
        Mind timid = average();
        timid.traits[Trait.COURAGE.ordinal()] = 15;
        timid.activity = Activity.TEND_FIELDS.ordinal();
        timid.activitySince = 99_990; // just started: commitment would normally hold
        Brain.Context c = ctx("farmer", 3500);
        c.danger = true;
        Activity a = Brain.decide(timid, c, r).activity();
        assertTrue(a.emergency(), "danger interrupts work for the timid, got " + a);
        Mind guard = average();
        Brain.Context g = ctx("guard", 3500);
        g.danger = true;
        assertEquals(Activity.DEFEND, Brain.decide(guard, g, r).activity());
    }

    @Test
    void commitmentPreventsFlickering() {
        Mind m = average();
        m.activity = Activity.STROLL.ordinal();
        m.activitySince = 99_990;
        Brain.Context c = ctx("farmer", 12000); // evening
        Brain.Choice ch = Brain.decide(m, c, new Random(1));
        assertEquals(-2, ch.target(), "a just-started activity is kept (no flickering)");
        assertEquals(Activity.STROLL, ch.activity());
    }

    @Test
    void goalsGrowFromStateAndRequestsFromRealNeeds() {
        Mind m = average();
        m.health = 0.3f;
        Goals.requests(m, 1000);
        assertNotNull(m.request, "an injured inhabitant may ask for medicine");
        assertEquals(Mind.Request.Kind.MEDICINE, m.request.kind);
        Mind ok = average();
        Goals.requests(ok, 1000);
        assertFalse(ok.request != null && ok.request.kind != Mind.Request.Kind.MATERIAL, "no invented emergencies");
        assertTrue(Activity.values().length >= 40);
        assertTrue(Trait.COUNT >= 20 && Trait.COUNT <= 30);
        assertTrue(Need.COUNT >= 8 && Need.COUNT <= 12);
    }
}
