package dev.underdeep.world.analysis;

import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Survival-traversal analysis of a cavern layer on the terrain model.
 * <p>
 * The layer is sampled on a grid. A sample is <em>standable</em> if the layer has open space there;
 * its standing height is the floor, or the water surface where the floor is submerged (swimming).
 * Lava surfaces are impassable. Two neighbouring samples are connected if the standing heights differ
 * by at most {@code maxStep} (default: one block of climb per block of horizontal distance — what a
 * player can jump). Breadth-first search from a start position gives true walking distances.
 * <p>
 * Limitations: ignores decoration (trees, boulders) which players can cut through, ignores ceiling
 * clearance below 3 blocks, and treats each layer separately (shafts are measured elsewhere).
 */
public final class Accessibility {
    private Accessibility() {
    }

    public record Report(int reachableSamples, int openSamples, Map<RegionType, Integer> walkDistance,
                         Map<RegionType, Integer> straightDistance, int borderCrossings, int walkableCrossings) {
        public String summary() {
            return String.format(Locale.ROOT,
                    "%d of %d open samples (%.0f%%) reachable on foot/swimming from the start; "
                            + "%d of %d sampled ecosystem border crossings are walkable (%.0f%%)",
                    reachableSamples, openSamples, reachableSamples * 100.0 / Math.max(1, openSamples),
                    walkableCrossings, borderCrossings, walkableCrossings * 100.0 / Math.max(1, borderCrossings));
        }

        public String distances() {
            StringBuilder sb = new StringBuilder();
            for (RegionType t : RegionType.values()) {
                Integer w = walkDistance.get(t);
                Integer s = straightDistance.get(t);
                if (s == null) {
                    continue;
                }
                sb.append(t.id()).append(": straight ").append(s).append(", walking ")
                        .append(w == null ? "unreached" : w.toString()).append("; ");
            }
            return sb.toString();
        }
    }

    public static Report analyze(UnderdeepTerrain t, CavernLayer layer, int startX, int startZ, int radius, int step) {
        int n = radius * 2 / step + 1;
        double[] h = new double[n * n];
        byte[] type = new byte[n * n];
        boolean upper = layer == CavernLayer.UPPER;
        int fluid = upper ? UnderdeepTerrain.SEA_LEVEL : CavernLayer.DEEP.fluidLevel;
        int open = 0;
        Map<RegionType, Integer> straight = new EnumMap<>(RegionType.class);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int x = startX - radius + i * step;
                int z = startZ - radius + j * step;
                TerrainColumn c = t.column(x, z);
                double f = upper ? c.upperFloor : c.deepFloor;
                double ce = upper ? c.upperCeiling : c.deepCeiling;
                int idx = i * n + j;
                if (ce - f < 3) {
                    h[idx] = Double.NaN;
                    type[idx] = -1;
                    continue;
                }
                double stand = f;
                boolean lava = upper ? c.upperLava : c.deepLava;
                if (!Double.isNaN(c.pondLevel) && f < c.pondLevel) {
                    lava = c.pondLava;
                    stand = c.pondLevel;
                } else if (f < fluid) {
                    stand = fluid;
                } else {
                    lava = false;
                }
                if (lava) {
                    h[idx] = Double.NaN;
                    type[idx] = -1;
                    continue;
                }
                h[idx] = stand;
                RegionType rt = (upper ? c.upperRegion : c.deepRegion).primary;
                type[idx] = (byte) rt.ordinal();
                open++;
                int d = (int) Math.hypot(x - startX, z - startZ);
                straight.merge(rt, d, Math::min);
            }
        }
        double maxStep = step * 1.05;
        int[] dist = new int[n * n];
        java.util.Arrays.fill(dist, -1);
        int s = (n / 2) * n + n / 2;
        // Start at the nearest standable sample to the centre.
        if (Double.isNaN(h[s])) {
            int best = -1;
            double bd = Double.MAX_VALUE;
            for (int k = 0; k < n * n; k++) {
                if (!Double.isNaN(h[k])) {
                    double dd = Math.hypot(k / n - n / 2, k % n - n / 2);
                    if (dd < bd) {
                        bd = dd;
                        best = k;
                    }
                }
            }
            s = best;
        }
        ArrayDeque<Integer> q = new ArrayDeque<>();
        if (s >= 0) {
            dist[s] = 0;
            q.add(s);
        }
        int reached = 0;
        Map<RegionType, Integer> walk = new EnumMap<>(RegionType.class);
        RegionType[] types = RegionType.values();
        while (!q.isEmpty()) {
            int cur = q.poll();
            reached++;
            RegionType rt = types[type[cur]];
            walk.merge(rt, dist[cur] * step, Math::min);
            int ci = cur / n;
            int cj = cur % n;
            int[] nb = {ci > 0 ? cur - n : -1, ci < n - 1 ? cur + n : -1, cj > 0 ? cur - 1 : -1, cj < n - 1 ? cur + 1 : -1};
            for (int nx : nb) {
                if (nx >= 0 && dist[nx] < 0 && !Double.isNaN(h[nx]) && Math.abs(h[nx] - h[cur]) <= maxStep) {
                    dist[nx] = dist[cur] + 1;
                    q.add(nx);
                }
            }
        }
        int crossings = 0;
        int walkable = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n - 1; j++) {
                for (int dir = 0; dir < 2; dir++) {
                    int a = dir == 0 ? i * n + j : j * n + i;
                    int b = dir == 0 ? i * n + j + 1 : (j + 1) * n + i;
                    if (type[a] >= 0 && type[b] >= 0 && type[a] != type[b]) {
                        crossings++;
                        if (Math.abs(h[a] - h[b]) <= maxStep) {
                            walkable++;
                        }
                    }
                }
            }
        }
        return new Report(reached, open, walk, straight, crossings, walkable);
    }
}
