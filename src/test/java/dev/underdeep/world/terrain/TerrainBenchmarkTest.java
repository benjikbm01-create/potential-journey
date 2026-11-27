package dev.underdeep.world.terrain;

import dev.underdeep.world.analysis.Accessibility;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Bounded generation-cost and reachability measurements of the terrain model (printed, not asserted, except a
 * loose time bound). Columns are what every generated chunk evaluates; the 3D sample mimics the chunk generator's
 * coarse density grid. Run with {@code ./gradlew test --tests '*TerrainBenchmarkTest*' -i}.
 */
class TerrainBenchmarkTest {
    @Test
    void columnAndDensityCost() {
        long seed = 42L;
        // warm-up (JIT) on a separate area
        UnderdeepTerrain w = new UnderdeepTerrain(seed, Integer.getInteger("bench.version", UnderdeepTerrain.GENERATOR_VERSION));
        for (int x = 4000; x < 4256; x++) {
            for (int z = 0; z < 256; z++) {
                w.column(x, z);
            }
        }
        UnderdeepTerrain t = new UnderdeepTerrain(seed, Integer.getInteger("bench.version", UnderdeepTerrain.GENERATOR_VERSION));
        long t0 = System.nanoTime();
        int cols = 0;
        for (int x = -256; x < 256; x++) {
            for (int z = -256; z < 256; z++) {
                t.column(x, z);
                cols++;
            }
        }
        long t1 = System.nanoTime();
        long acc = 0;
        int samples = 0;
        for (int x = -256; x < 256; x += 4) {
            for (int z = -256; z < 256; z += 4) {
                for (int y = -60; y < 316; y += 8) {
                    acc += t.materialAt(x, y, z).ordinal();
                    samples++;
                }
            }
        }
        long t2 = System.nanoTime();
        double colUs = (t1 - t0) / 1000.0 / cols;
        double sampleUs = (t2 - t1) / 1000.0 / samples;
        System.out.printf("BENCH columns: %.2f us/column (%d), 3D samples: %.2f us/sample (%d) [%d]%n", colUs, cols, sampleUs, samples, acc);
        System.out.printf("BENCH per chunk estimate: %.1f ms columns%n", colUs * 256 / 1000.0);
        org.junit.jupiter.api.Assertions.assertTrue(colUs < 2000, "column cost unreasonable");
    }

    @Test
    void descentsAreWalkable() {
        UnderdeepTerrain t = new UnderdeepTerrain(42L, 5);
        var list = t.descentsIn(-900, -900, 900, 900);
        int ok = 0, back = 0, n = 0;
        for (var d : list) {
            if (n >= (Boolean.getBoolean("underdeep.bench") ? 16 : 5)) {
                break;
            }
            n++;
            int[] min = {(int) Math.min(d.minX(), Math.min(d.topX(), d.bottomX())) - 8, d.bottomY() - 8,
                    (int) Math.min(d.minZ(), Math.min(d.topZ(), d.bottomZ())) - 8};
            int[] max = {(int) Math.max(d.maxX(), Math.max(d.topX(), d.bottomX())) + 8, d.topY() + 12,
                    (int) Math.max(d.maxZ(), Math.max(d.topZ(), d.bottomZ())) + 8};
            dev.underdeep.world.analysis.VoxelWalk.Blocks b = (x, y, z) -> switch (t.materialAt(x, y, z)) {
                case AIR -> dev.underdeep.world.analysis.VoxelWalk.AIR;
                case WATER -> dev.underdeep.world.analysis.VoxelWalk.WATER;
                case LAVA -> dev.underdeep.world.analysis.VoxelWalk.LAVA;
                default -> dev.underdeep.world.analysis.VoxelWalk.SOLID;
            };
            int[] top = {d.topX(), d.topY(), d.topZ()};
            int[] bot = {d.bottomX(), d.bottomY(), d.bottomZ()};
            var down = dev.underdeep.world.analysis.VoxelWalk.walk(b, min, max, top, bot, 3);
            var up = dev.underdeep.world.analysis.VoxelWalk.walk(b, min, max, bot, top, 3);
            ok += down.reached() ? 1 : 0;
            back += up.reached() ? 1 : 0;
            System.out.printf("WALK descent at %.0f,%.0f r%.0f top %d,%d,%d bottom %d,%d,%d: down %s (len %d, closest %s d=%.1f) up %s (closest %s d=%.1f)%n",
                    d.centerX(), d.centerZ(), d.radius(), d.topX(), d.topY(), d.topZ(), d.bottomX(), d.bottomY(), d.bottomZ(),
                    down.reached(), down.pathLength(), java.util.Arrays.toString(down.closest()), down.closestDist(), up.reached(),
                    java.util.Arrays.toString(up.closest()), up.closestDist());
        }
        System.out.println("WALK " + ok + "/" + n + " walkable down, " + back + "/" + n + " walkable back up");
        org.junit.jupiter.api.Assertions.assertTrue(n >= 4, "descents exist near spawn");
        org.junit.jupiter.api.Assertions.assertTrue(ok >= n - 1 && back >= n - 1, "descents must be walkable both ways: " + ok + "/" + back + " of " + n);
    }

    @Test
    void neighbourChambersAreWalkable() {
        Assumptions.assumeTrue(Boolean.getBoolean("underdeep.bench"), "slow measurement; run with -Dunderdeep.bench=true");
        for (int v : new int[]{4, 5}) {
            UnderdeepTerrain t = new UnderdeepTerrain(42L, v);
            CavernGraph g = t.graph(CavernLayer.UPPER);
            int ok = 0, n = 0;
            for (int cx = -3; cx <= 2; cx++) {
                for (int cz = -2; cz <= 1; cz += 2) {
                    CavernGraph.Node a = g.node(cx, cz), b = g.node(cx + 1, cz);
                    int ax = (int) a.x(), az = (int) a.z(), bx = (int) b.x(), bz = (int) b.z();
                    TerrainColumn ca = t.column(ax, az), cb = t.column(bx, bz);
                    if (ca.upperCeiling - ca.upperFloor < 4 || cb.upperCeiling - cb.upperFloor < 4) {
                        continue;
                    }
                    int ay = (int) Math.floor(Math.max(ca.upperFloor, UnderdeepTerrain.SEA_LEVEL)) + 1;
                    int by = (int) Math.floor(Math.max(cb.upperFloor, UnderdeepTerrain.SEA_LEVEL)) + 1;
                    int[] min = {Math.min(ax, bx) - 70, Math.max(UnderdeepTerrain.SEA_LEVEL - 12, Math.min(ay, by) - 45), Math.min(az, bz) - 70};
                    int[] max = {Math.max(ax, bx) + 70, Math.max(ay, by) + 45, Math.max(az, bz) + 70};
                    dev.underdeep.world.analysis.VoxelWalk.Blocks blk = (x, y, z) -> switch (t.materialAt(x, y, z)) {
                        case AIR -> dev.underdeep.world.analysis.VoxelWalk.AIR;
                        case WATER -> dev.underdeep.world.analysis.VoxelWalk.WATER;
                        case LAVA -> dev.underdeep.world.analysis.VoxelWalk.LAVA;
                        default -> dev.underdeep.world.analysis.VoxelWalk.SOLID;
                    };
                    var r = dev.underdeep.world.analysis.VoxelWalk.walk(blk, min, max, new int[]{ax, ay, az}, new int[]{bx, by, bz}, 8);
                    n++;
                    ok += r.reached() ? 1 : 0;
                    if (!r.reached()) {
                        var back = dev.underdeep.world.analysis.VoxelWalk.walk(blk, min, max, new int[]{bx, by, bz}, new int[]{ax, ay, az}, 8);
                        System.out.printf("CHFAIL v%d %d,%d,%d (%s%s) -> %d,%d,%d (%s%s): closest %s d=%.0f visited %d; reverse %s closest %s%n", v, ax, ay, az,
                                a.region().id(), a.mega() ? " mega" : "", bx, by, bz, b.region().id(), b.mega() ? " mega" : "",
                                java.util.Arrays.toString(r.closest()), r.closestDist(), r.visited(), back.reached(), java.util.Arrays.toString(back.closest()));
                    }
                }
            }
            System.out.println("CHAMBERS v" + v + ": " + ok + "/" + n + " neighbouring great chambers physically walkable (terrain model voxels)");
        }
    }

    @Test
    void stridedColumnCost() {
        Assumptions.assumeTrue(Boolean.getBoolean("underdeep.bench"), "slow measurement; run with -Dunderdeep.bench=true");
        for (int v : new int[]{4, 5}) {
            UnderdeepTerrain t = new UnderdeepTerrain(42L, v);
            long t0 = System.nanoTime();
            int n = 0;
            for (int x = -1200; x <= 1200; x += 4) {
                for (int z = -1200; z <= 1200; z += 4) {
                    t.column(x, z);
                    n++;
                }
            }
            System.out.printf("STRIDE v%d: %.2f us/column%n", v, (System.nanoTime() - t0) / 1000.0 / n);
        }
    }

    @Test
    void caveNetworkV4VersusV5() {
        Assumptions.assumeTrue(Boolean.getBoolean("underdeep.bench"), "slow measurement; run with -Dunderdeep.bench=true");
        for (long seed : new long[]{42L, 12345L, 2024L}) {
            for (int v : new int[]{4, 5}) {
                UnderdeepTerrain t = new UnderdeepTerrain(seed, v);
                System.out.println("NET seed " + seed + " " + dev.underdeep.world.analysis.CaveNetwork.analyze(t, 0, 0, 1200, 4).summary());
            }
        }
    }

    @Test
    void reachability() {
        Assumptions.assumeTrue(Boolean.getBoolean("underdeep.bench"), "slow measurement; run with -Dunderdeep.bench=true");
        for (long seed : new long[]{42L, 12345L}) {
            UnderdeepTerrain t = new UnderdeepTerrain(seed);
            Accessibility.Report up = Accessibility.analyze(t, CavernLayer.UPPER, 0, 0, 1200, 4);
            Accessibility.Report dp = Accessibility.analyze(t, CavernLayer.DEEP, 0, 0, 1200, 4);
            System.out.println("REACH seed " + seed + " upper: " + up.summary());
            System.out.println("REACH seed " + seed + " deep: " + dp.summary());
        }
    }
}
