package dev.underdeep.world.analysis;

import dev.underdeep.world.region.RegionSample;
import dev.underdeep.world.terrain.CavernGraph;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Settlement placement contract (Stage 1 foundation for Stages 4-6).
 * <p>
 * Works entirely on the deterministic terrain model, so it can evaluate unloaded, ungenerated areas
 * cheaply and identically on every run. It does not account for decoration features (trees,
 * boulders) — placement code in later stages must clear or adapt to those when building.
 * <p>
 * Cost is bounded: {@link #evaluate} samples at most (2r/4+1)^2 columns; {@link #findSites}
 * evaluates only cavern-graph chamber centres within the requested radius (one per graph cell).
 */
public final class SiteAnalyzer {
    private static final int STEP = 4;

    private SiteAnalyzer() {
    }

    /** Evaluates a site centred on (x, z) in a layer for a given type. */
    public static SiteCandidate evaluate(UnderdeepTerrain t, SiteType type, CavernLayer layer, int x, int z, int radius) {
        boolean upper = layer == CavernLayer.UPPER;
        int layerFluid = upper ? UnderdeepTerrain.SEA_LEVEL : CavernLayer.DEEP.fluidLevel;
        TerrainColumn center = t.column(x, z);
        double cFloor = upper ? center.upperFloor : center.deepFloor;
        int n = radius / STEP;
        int size = 2 * n + 1;
        double[] floors = new double[size * size];
        boolean[] usable = new boolean[size * size];
        double[] headroom = new double[size * size];
        boolean water = false;
        boolean lava = false;
        int dryOpen = 0;
        int overWater = 0;
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                int idx = (i + n) * size + (j + n);
                TerrainColumn c = t.column(x + i * STEP, z + j * STEP);
                double f = upper ? c.upperFloor : c.deepFloor;
                double ce = upper ? c.upperCeiling : c.deepCeiling;
                floors[idx] = f;
                double gap = ce - Math.max(f, layerFluid);
                headroom[idx] = gap;
                boolean lavaHere = upper ? c.upperLava : c.deepLava;
                boolean submerged = f < layerFluid - 0.5 || (!Double.isNaN(c.pondLevel) && f < c.pondLevel);
                if (submerged && ce > layerFluid) {
                    if (lavaHere || c.pondLava) {
                        lava = true;
                    } else {
                        water = true;
                        overWater++;
                    }
                }
                if (c.river > 0.3) {
                    water = true;
                }
                if (ce - f > type.minHeadroom && !submerged) {
                    usable[idx] = true;
                    dryOpen++;
                }
            }
        }
        // Contiguous usable radius: grow rings while most of each ring stays usable and level.
        int usableRadius = 0;
        double slopeSum = 0;
        int slopeCount = 0;
        for (int r = 1; r <= n; r++) {
            int ok = 0;
            int total = 0;
            for (int i = -r; i <= r; i++) {
                for (int j = -r; j <= r; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != r) {
                        continue;
                    }
                    total++;
                    int idx = (i + n) * size + (j + n);
                    if (usable[idx] && Math.abs(floors[idx] - cFloor) < 6 + r * STEP * type.maxSlope) {
                        ok++;
                    }
                }
            }
            if (ok < total * 0.7) {
                break;
            }
            usableRadius = r * STEP;
        }
        for (int i = -n; i < n; i++) {
            for (int j = -n; j <= n; j++) {
                int a = (i + n) * size + (j + n);
                int b = (i + 1 + n) * size + (j + n);
                if (usable[a] && usable[b]) {
                    slopeSum += Math.abs(floors[a] - floors[b]) / STEP;
                    slopeCount++;
                }
            }
        }
        double slope = slopeCount > 0 ? slopeSum / slopeCount : 0; // no dry neighbours to compare (e.g. open water)
        double[] hs = Arrays.stream(headroom).filter(h -> h > 0).sorted().toArray();
        double medianHead = hs.length > 0 ? hs[hs.length / 2] : 0;

        CavernGraph graph = t.graph(layer);
        CavernGraph.Node node = graph.nearestNode(x, z);
        int connections = countConnections(graph, node);

        RegionSample rs = new RegionSample();
        t.regions(layer).sample(x, z, rs);
        double affinity = rs.primary.settlementAffinity(type);

        double score;
        if (type.overWater) {
            double frac = overWater / (double) (size * size);
            score = affinity * Math.min(1, frac * 2.0) * Math.min(1, medianHead / Math.max(1, type.minHeadroom));
            usableRadius = (int) (Math.sqrt(overWater * STEP * STEP / Math.PI));
        } else if (type.suspended) {
            score = affinity * Math.min(1, medianHead / type.minHeadroom) * (dryOpen > 0 ? 1 : 0);
            usableRadius = Math.max(usableRadius, radius / 2);
        } else {
            double areaScore = Math.min(1, usableRadius / (double) type.minRadius);
            double slopeScore = Math.max(0, 1 - slope / Math.max(0.05, type.maxSlope * 1.5));
            double headScore = Math.min(1, medianHead / (type.minHeadroom * 1.5));
            double waterScore = type.wantsWater ? (water ? 1 : 0.35) : (water ? 1 : 0.8);
            double lavaScore = type.wantsLava ? (lava ? 1 : 0.4) : (lava ? 0.6 : 1);
            double connScore = Math.min(1, 0.4 + connections * 0.15);
            score = affinity * areaScore * slopeScore * headScore * waterScore * lavaScore * connScore;
            if (usableRadius < type.minRadius) {
                score *= 0.5;
            }
        }
        return new SiteCandidate(type, x, (int) Math.floor(cFloor) + 1, z, layer, rs.id(), rs.primary,
                Math.max(0, Math.min(1, score)), usableRadius, medianHead, slope, water, lava, connections);
    }

    /** Number of tunnels attached to a chamber (deterministic from the graph). */
    public static int countConnections(CavernGraph graph, CavernGraph.Node node) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (CavernGraph.Edge e : graph.edgesOf(node.cellX() + dx, node.cellZ() + dz)) {
                    if (e.a().key() == node.key() || e.b().key() == node.key()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Finds the best sites of a type within {@code searchRadius} blocks of (x, z), best first.
     * Evaluates one candidate per cavern-graph chamber (plus its offsets for large chambers).
     */
    public static List<SiteCandidate> findSites(UnderdeepTerrain t, SiteType type, CavernLayer layer, int x, int z,
                                                int searchRadius, int maxResults) {
        CavernGraph graph = t.graph(layer);
        List<SiteCandidate> out = new ArrayList<>();
        int evalRadius = Math.max(24, Math.min(96, type.minRadius * 2));
        for (CavernGraph.Node node : graph.nodesIn(x - searchRadius, z - searchRadius, x + searchRadius, z + searchRadius)) {
            double dx = node.x() - x;
            double dz = node.z() - z;
            if (dx * dx + dz * dz > (double) searchRadius * searchRadius) {
                continue;
            }
            SiteCandidate c = evaluate(t, type, layer, (int) node.x(), (int) node.z(), evalRadius);
            if (c.score() > 0.01) {
                out.add(c);
            }
        }
        out.sort(Comparator.comparingDouble(SiteCandidate::score).reversed());
        return out.size() > maxResults ? new ArrayList<>(out.subList(0, maxResults)) : out;
    }
}
