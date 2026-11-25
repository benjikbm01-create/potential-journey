package dev.underdeep.world.terrain;

import dev.underdeep.world.analysis.RegionDistribution;
import org.junit.jupiter.api.Test;

/**
 * Prints ecosystem distribution statistics (enable with -Dunderdeep.distribution=true). Used while
 * tuning region sizes; the assertions on distribution live in TerrainModelTest.
 */
class DistributionReportTest {
    @Test
    void report() {
        if (!Boolean.getBoolean("underdeep.distribution")) {
            return;
        }
        for (long seed : TerrainModelTest.SEEDS) {
            UnderdeepTerrain t = new UnderdeepTerrain(seed);
            for (CavernLayer layer : CavernLayer.values()) {
                RegionDistribution.Report r = RegionDistribution.analyze(t, layer, 2600, 8);
                System.out.println("seed " + seed + " " + layer.id + ": " + r.summary());
                System.out.println("   size classes: " + r.sizeClasses());
                System.out.println("   frequencies: " + r.frequencies());
                System.out.println("   nearest meaningful region: " + r.meaningfulFirstDistance());
            }
            var acc = dev.underdeep.world.analysis.Accessibility.analyze(t, CavernLayer.UPPER, 0, 0, 1500, 4);
            System.out.println("   ACCESS upper: " + acc.summary());
            System.out.println("   ACCESS distances: " + acc.distances());
        }
    }
}
