package dev.underdeep.world.terrain;

import dev.underdeep.civ.Civilization;
import dev.underdeep.civ.settlement.SettlementCategory;
import dev.underdeep.civ.settlement.SettlementMap;
import dev.underdeep.civ.settlement.SettlementSite;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Settlement placement is deterministic, spaced and meets the accessibility targets near spawn. */
class SettlementMapTest {
    private static final long[] SEEDS = {12345L, -987654321L, 42L};

    @Test
    void deterministicAndIndependentOfQueryOrder() {
        SettlementMap a = new SettlementMap(new UnderdeepTerrain(777L));
        SettlementMap b = new SettlementMap(new UnderdeepTerrain(777L));
        List<SettlementSite> la = a.sitesNear(0, 0, 1500);
        // query b in a different order first
        b.sitesNear(1400, -1400, 300);
        b.sitesNear(-900, 700, 400);
        List<SettlementSite> lb = b.sitesNear(0, 0, 1500);
        assertEquals(la, lb);
    }

    @Test
    void distributionAndSpawnAccess() {
        for (long seed : SEEDS) {
            SettlementMap m = new SettlementMap(new UnderdeepTerrain(seed));
            List<SettlementSite> all = m.sitesNear(0, 0, 3000);
            Map<SettlementCategory, Integer> counts = new EnumMap<>(SettlementCategory.class);
            Set<Civilization> civs = EnumSet.noneOf(Civilization.class);
            double nearestWelcoming = Double.MAX_VALUE, nearestTown = Double.MAX_VALUE;
            int within500 = 0;
            for (SettlementSite s : all) {
                counts.merge(s.category(), 1, Integer::sum);
                civs.add(s.civ());
                double d = Math.hypot(s.x(), s.z());
                if (s.civ().welcoming()) {
                    nearestWelcoming = Math.min(nearestWelcoming, d);
                }
                if (s.category().atLeast(SettlementCategory.TOWN)) {
                    nearestTown = Math.min(nearestTown, d);
                }
                within500 += d <= 500 ? 1 : 0;
            }
            // spacing: no overlapping settlement areas
            for (SettlementSite s : all) {
                for (SettlementSite o : all) {
                    if (s != o) {
                        assertTrue(Math.hypot(s.x() - o.x(), s.z() - o.z()) >= s.radius() + o.radius(), s + " overlaps " + o);
                    }
                }
            }
            System.out.printf("seed %d: %d settlements within 3000: %s; civs %d %s; nearest welcoming %.0f; nearest town %.0f; within 500: %d%n",
                    seed, all.size(), counts, civs.size(), civs, nearestWelcoming, nearestTown, within500);
            assertTrue(nearestWelcoming <= 340, "welcoming settlement too far: " + nearestWelcoming);
            assertTrue(nearestTown <= 1250, "town too far: " + nearestTown);
            assertTrue(within500 >= 3, "too few settlements within 500: " + within500);
            assertEquals(8, civs.size(), "civilizations within 3000: " + civs);
        }
    }
}
