package dev.underdeep.world.analysis;

import dev.underdeep.world.terrain.CavernGraph;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;

import java.util.List;
import java.util.Locale;

/**
 * Stage 10 cave connectivity measurement on the terrain model (bounded: one square area, coarse grid).
 * <p>
 * Each layer is sampled on a grid; a sample is standable when the layer is open there and not lava (water counts as
 * swimmable at its surface). Neighbouring samples connect when the height difference is walkable (at most about one
 * block per block). Components are found with union-find; descents and spiral shafts link the two layers at their
 * mouths. Reports the share of open area in the main network, the share of great chambers (graph nodes) whose centre
 * lies in it, and how many neighbouring ecosystems are reached. This is a model-level estimate — it ignores 3D
 * wall noise and formations — so physical routes are additionally verified on generated blocks by the self-test.
 */
public final class CaveNetwork {
    private CaveNetwork() {
    }

    public record Report(int version, int openUpper, int openDeep, int mainUpper, int mainDeep, int mainCombined,
                         int nodes, int nodesInMain, int descents, int shafts, int links, double ms) {
        public String summary() {
            int open = openUpper + openDeep;
            return String.format(Locale.ROOT,
                    "v%d: open samples upper %d / deep %d; largest walkable network: upper %.0f%%, deep %.0f%%, both layers joined %.0f%%; "
                            + "great chambers in the main network %d/%d (%.0f%%); descents %d, spiral shafts %d, layer links %d (%.0f ms)",
                    version, openUpper, openDeep, mainUpper * 100.0 / Math.max(1, openUpper), mainDeep * 100.0 / Math.max(1, openDeep),
                    mainCombined * 100.0 / Math.max(1, open), nodesInMain, nodes, nodesInMain * 100.0 / Math.max(1, nodes),
                    descents, shafts, links, ms);
        }
    }

    public static Report analyze(UnderdeepTerrain t, int cx, int cz, int radius, int step) {
        long t0 = System.nanoTime();
        int n = radius * 2 / step + 1;
        int total = n * n;
        double[] hu = new double[total];
        double[] hd = new double[total];
        int[] parent = new int[total * 2];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        int openU = 0, openD = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                TerrainColumn c = t.column(cx - radius + i * step, cz - radius + j * step);
                hu[i * n + j] = stand(c, true);
                hd[i * n + j] = stand(c, false);
                if (!Double.isNaN(hu[i * n + j])) {
                    openU++;
                }
                if (!Double.isNaN(hd[i * n + j])) {
                    openD++;
                }
            }
        }
        double maxStep = step * 1.05;
        for (int layer = 0; layer < 2; layer++) {
            double[] h = layer == 0 ? hu : hd;
            int off = layer * total;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    int a = i * n + j;
                    if (Double.isNaN(h[a])) {
                        continue;
                    }
                    if (i + 1 < n && !Double.isNaN(h[a + n]) && Math.abs(h[a + n] - h[a]) <= maxStep) {
                        union(parent, off + a, off + a + n);
                    }
                    if (j + 1 < n && !Double.isNaN(h[a + 1]) && Math.abs(h[a + 1] - h[a]) <= maxStep) {
                        union(parent, off + a, off + a + 1);
                    }
                }
            }
        }
        int mainU = largest(parent, hu, 0, total);
        int mainD = largest(parent, hd, total, total);
        // vertical links: descents (mouth to mouth) and spiral shafts (top chamber to shaft bottom)
        int links = 0;
        List<UnderdeepTerrain.DescentInfo> ds = t.descentsIn(cx - radius, cz - radius, cx + radius, cz + radius);
        for (UnderdeepTerrain.DescentInfo d : ds) {
            int a = nearest(hu, n, cx, cz, radius, step, d.topX(), d.topZ(), 3);
            int b = nearest(hd, n, cx, cz, radius, step, d.bottomX(), d.bottomZ(), 3);
            if (a >= 0 && b >= 0) {
                union(parent, a, total + b);
                links++;
            }
        }
        int shafts = 0;
        CavernGraph deep = t.graph(CavernLayer.DEEP);
        int cell = deep.cellSize();
        for (int gx = Math.floorDiv(cx - radius, cell); gx <= Math.floorDiv(cx + radius, cell); gx++) {
            for (int gz = Math.floorDiv(cz - radius, cell); gz <= Math.floorDiv(cz + radius, cell); gz++) {
                CavernGraph.Shaft s = t.shaft(gx, gz);
                if (s == null) {
                    continue;
                }
                TerrainColumn c = t.column((int) s.x(), (int) s.z());
                if (!c.shaftSpiral || c.shaftDist > c.shaftRadius) {
                    continue;
                }
                shafts++;
                int a = nearest(hu, n, cx, cz, radius, step, (int) s.x(), (int) s.z(), (int) (s.radius() + 16) / step + 1);
                int b = nearest(hd, n, cx, cz, radius, step, (int) s.x(), (int) s.z(), (int) (s.radius() + 8) / step + 1);
                if (a >= 0 && b >= 0) {
                    union(parent, a, total + b);
                    links++;
                }
            }
        }
        // combined main component
        int[] count = new int[total * 2];
        int best = -1, bestN = 0;
        for (int k = 0; k < total * 2; k++) {
            double v = k < total ? hu[k] : hd[k - total];
            if (!Double.isNaN(v)) {
                int r = find(parent, k);
                if (++count[r] > bestN) {
                    bestN = count[r];
                    best = r;
                }
            }
        }
        // great chambers whose centre is in the main network
        int nodes = 0, inMain = 0;
        for (CavernLayer layer : CavernLayer.values()) {
            CavernGraph g = t.graph(layer);
            double[] h = layer == CavernLayer.UPPER ? hu : hd;
            int off = layer == CavernLayer.UPPER ? 0 : total;
            for (CavernGraph.Node nd : g.nodesIn(cx - radius + 40, cz - radius + 40, cx + radius - 40, cz + radius - 40)) {
                if (Math.abs(nd.x() - cx) > radius - 40 || Math.abs(nd.z() - cz) > radius - 40) {
                    continue;
                }
                int range = (int) (nd.radius() * 0.6) / step + 1;
                int i0 = Math.round((float) (nd.x() - (cx - radius)) / step), j0 = Math.round((float) (nd.z() - (cz - radius)) / step);
                boolean any = false, reached = false;
                for (int di = -range; di <= range && !reached; di++) {
                    for (int dj = -range; dj <= range; dj++) {
                        int i = i0 + di, j = j0 + dj;
                        if (i < 0 || j < 0 || i >= n || j >= n || Double.isNaN(h[i * n + j]) || di * di + dj * dj > range * range) {
                            continue;
                        }
                        any = true;
                        if (find(parent, off + i * n + j) == best) {
                            reached = true;
                            break;
                        }
                    }
                }
                if (!any) {
                    continue; // chamber fully filled by lava/formations: nothing to reach
                }
                nodes++;
                if (reached) {
                    inMain++;
                }
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        return new Report(t.version(), openU, openD, mainU, mainD, bestN, nodes, inMain, ds.size(), shafts, links, ms);
    }

    private static double stand(TerrainColumn c, boolean upper) {
        double f = upper ? c.upperFloor : c.deepFloor;
        double ce = upper ? c.upperCeiling : c.deepCeiling;
        if (ce - f < 3) {
            return Double.NaN;
        }
        int fluid = upper ? UnderdeepTerrain.SEA_LEVEL : CavernLayer.DEEP.fluidLevel;
        boolean lava = upper ? c.upperLava : c.deepLava;
        if (!Double.isNaN(c.pondLevel) && f < c.pondLevel) {
            return c.pondLava ? Double.NaN : c.pondLevel;
        }
        if (f < fluid) {
            return lava ? Double.NaN : fluid;
        }
        return f;
    }

    private static int nearest(double[] h, int n, int cx, int cz, int radius, int step, int x, int z, int range) {
        int i0 = Math.round((x - (cx - radius)) / (float) step);
        int j0 = Math.round((z - (cz - radius)) / (float) step);
        int best = -1;
        int bd = Integer.MAX_VALUE;
        for (int di = -range; di <= range; di++) {
            for (int dj = -range; dj <= range; dj++) {
                int i = i0 + di, j = j0 + dj;
                if (i < 0 || j < 0 || i >= n || j >= n || Double.isNaN(h[i * n + j])) {
                    continue;
                }
                int d = di * di + dj * dj;
                if (d < bd) {
                    bd = d;
                    best = i * n + j;
                }
            }
        }
        return best;
    }

    private static int largest(int[] parent, double[] h, int off, int total) {
        int[] count = new int[parent.length];
        int best = 0;
        for (int k = 0; k < total; k++) {
            if (!Double.isNaN(h[k])) {
                best = Math.max(best, ++count[find(parent, off + k)]);
            }
        }
        return best;
    }

    private static int find(int[] p, int a) {
        while (p[a] != a) {
            p[a] = p[p[a]];
            a = p[a];
        }
        return a;
    }

    private static void union(int[] p, int a, int b) {
        int ra = find(p, a), rb = find(p, b);
        if (ra != rb) {
            p[ra] = rb;
        }
    }
}
