package dev.underdeep.world.terrain;

import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.WildlifeRegistry;
import dev.underdeep.wildlife.population.RegionWildlife;
import dev.underdeep.world.region.RegionType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tests of the Stage 3 species data and the aggregate regional population model. */
class WildlifeModelTest {
    private static final long REGION = 0x1234_5678_9ABCL;

    @Test
    void speciesCoverEveryEcosystemWithRequiredRoles() {
        Set<RegionType> covered = EnumSet.noneOf(RegionType.class);
        Set<String> ids = new HashSet<>();
        int tamable = 0, mounts = 0, aquatic = 0, fliers = 0;
        for (CreatureSpecies s : WildlifeRegistry.all()) {
            assertTrue(ids.add(s.id), "duplicate " + s.id);
            assertTrue(!s.regions().isEmpty(), s.id + " has no habitat");
            covered.addAll(s.regions());
            tamable += s.tamable() ? 1 : 0;
            mounts += s.mountType() != CreatureSpecies.Mount.NONE ? 1 : 0;
            aquatic += s.isAquatic() || s.locomotion() == CreatureSpecies.Locomotion.AMPHIBIOUS ? 1 : 0;
            fliers += s.flies() || s.locomotion() == CreatureSpecies.Locomotion.GLIDER ? 1 : 0;
            for (String prey : s.preyIds()) {
                assertTrue(WildlifeRegistry.byId(prey) != null, s.id + " hunts unknown " + prey);
            }
        }
        int n = WildlifeRegistry.all().size();
        assertTrue(n >= 40 && n <= 60, n + " species");
        assertEquals(RegionType.values().length, covered.size());
        assertTrue(tamable >= 8 && tamable <= 12, tamable + " tamable");
        assertTrue(mounts >= 4 && mounts <= 6, mounts + " mounts");
        assertTrue(aquatic >= 8, aquatic + " aquatic");
        assertTrue(fliers >= 4, fliers + " fliers");
    }

    @Test
    void capacityIsDeterministicAndHabitatBound() {
        for (CreatureSpecies s : WildlifeRegistry.all()) {
            for (RegionType t : RegionType.values()) {
                int a = RegionWildlife.capacity(s, t, REGION, 1, 1, 0);
                assertEquals(a, RegionWildlife.capacity(s, t, REGION, 1, 1, 0));
                if (!s.regions().contains(t)) {
                    assertEquals(0, a, s.id + " in " + t);
                }
                assertTrue(RegionWildlife.capacity(s, t, REGION, 1, 1, 0.8) <= a, "disturbance must not raise capacity");
            }
        }
    }

    @Test
    void populationsStayBoundedOverLongSimulation() {
        for (RegionType t : RegionType.values()) {
            RegionWildlife r = new RegionWildlife();
            r.initialize(t, REGION + t.ordinal(), 1, 1, 0);
            Random rnd = new Random(t.ordinal());
            for (int day = 0; day < 200; day++) {
                r.step(1, t, REGION + t.ordinal(), 1, 1, 1, 1, rnd);
            }
            for (Map.Entry<String, Integer> e : r.counts.entrySet()) {
                int cap = RegionWildlife.capacity(WildlifeRegistry.byId(e.getKey()), t, REGION + t.ordinal(), 1, 1, 0);
                assertTrue(e.getValue() <= Math.max(2, cap * 1.5), t + " " + e.getKey() + " " + e.getValue() + " cap " + cap);
            }
        }
    }

    @Test
    void depletedPopulationRecoversAndPredationReducesPrey() {
        RegionWildlife r = new RegionWildlife();
        r.initialize(RegionType.ROOTWILD, REGION, 1, 1, 0);
        int start = r.total();
        r.counts.replaceAll((k, v) -> 1);
        int low = r.total();
        Random rnd = new Random(1);
        for (int i = 0; i < 40; i++) {
            r.step(1, RegionType.ROOTWILD, REGION, 1, 1, 1, 1, rnd);
        }
        assertTrue(r.total() > low && r.total() <= start * 2, low + " -> " + r.total());

        RegionWildlife a = new RegionWildlife();
        RegionWildlife b = new RegionWildlife();
        a.counts.put("thumperkit", 10);
        a.counts.put("shadecat", 3);
        b.counts.put("thumperkit", 10);
        Random ra = new Random(5), rb = new Random(5);
        for (int i = 0; i < 6; i++) {
            a.step(1, RegionType.ROOTWILD, REGION, 1, 1, 1, 1, ra);
            b.step(1, RegionType.ROOTWILD, REGION, 1, 1, 1, 1, rb);
        }
        assertTrue(a.count("thumperkit") < b.count("thumperkit"));
        assertTrue(a.predation > 0);
    }

    @Test
    void nbtRoundTrip() {
        RegionWildlife r = new RegionWildlife();
        r.initialize(RegionType.FLOODED_DEPTHS, REGION, 1, 1, 777);
        r.disturbance = 0.4;
        r.playerKills = 3;
        RegionWildlife c = RegionWildlife.fromNbt(r.toNbt());
        assertEquals(r.counts, c.counts);
        assertEquals(r.lastTime, c.lastTime);
        assertEquals(r.disturbance, c.disturbance, 1e-9);
        assertEquals(r.playerKills, c.playerKills);
        assertTrue(c.initialized);
    }
}
