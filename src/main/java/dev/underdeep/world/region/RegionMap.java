package dev.underdeep.world.region;

import dev.underdeep.world.noise.FractalNoise;
import dev.underdeep.world.noise.Hashing;
import dev.underdeep.world.terrain.CavernLayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic ecosystem layout for one {@link CavernLayer} (Stage 1.5 version).
 * <p>
 * The layer is tiled by small, strongly domain-warped Voronoi cells (~112 blocks upper, ~160 deep).
 * Each cell picks an ecosystem from a three-axis "climate" (temperature, moisture, mineral/strangeness)
 * sampled at its centre: low-frequency climate makes neighbouring cells agree, so cells merge into
 * contiguous regions of very different sizes (a single cell ≈ 60–150 blocks; merged clusters up to
 * several hundred blocks), while a mid-frequency climate term and per-cell jitter break up uniformity.
 * Prototype positions in climate space are arranged so neighbouring ecosystems make geographic sense
 * (forest ↔ grove ↔ wetland, flooded ↔ sunken, crystal ↔ stormstone, peaks ↔ frost, ashen ↔ crimson).
 * Exceptional-scale ecosystems get a {@link RegionType#sizeBias()} which widens their climate basin.
 * <p>
 * Guarantees: the cells around the world origin are Rootwild; dangerous ecosystems are suppressed near
 * the origin; every ecosystem of the layer appears within ~2,400 blocks of the origin.
 * Pure function of (seed, x, z); immutable and thread-safe (caches are thread-local).
 */
public final class RegionMap {
    private static final double BLEND = 34.0;
    private static final int GUARANTEE_RANGE = 2300;

    private final CavernLayer layer;
    private final long seed;
    private final int cell;
    private final FractalNoise warpX;
    private final FractalNoise warpZ;
    private final FractalNoise warpX2;
    private final FractalNoise warpZ2;
    private final FractalNoise[] climate = new FractalNoise[3];
    private final FractalNoise[] climateDetail = new FractalNoise[3];
    private final Map<RegionType, double[]> prototypes = new EnumMap<>(RegionType.class);
    private final Map<RegionType, Double> targetShare = new EnumMap<>(RegionType.class);
    private final Map<RegionType, Double> bias = new EnumMap<>(RegionType.class);
    private final double[] sigma = new double[3];
    private final Map<Long, RegionType> overrides = new HashMap<>();

    public RegionMap(long worldSeed, CavernLayer layer) {
        this.layer = layer;
        this.seed = Hashing.hash(worldSeed, Hashing.salt("region_map_v2"), layer.ordinal());
        this.cell = layer.regionCell;
        this.warpX = new FractalNoise(Hashing.hash(seed, 1), 3, 1 / 260.0);
        this.warpZ = new FractalNoise(Hashing.hash(seed, 2), 3, 1 / 260.0);
        this.warpX2 = new FractalNoise(Hashing.hash(seed, 5), 2, 1 / 70.0);
        this.warpZ2 = new FractalNoise(Hashing.hash(seed, 6), 2, 1 / 70.0);
        for (int i = 0; i < 3; i++) {
            climate[i] = new FractalNoise(Hashing.hash(seed, 10 + i), 2, 1 / 950.0);
            climateDetail[i] = new FractalNoise(Hashing.hash(seed, 20 + i), 2, 1 / 330.0);
        }
        if (layer == CavernLayer.UPPER) {
            // temperature, moisture, mineral (each axis normalised to roughly uniform [-1, 1]); target share
            proto(RegionType.ROOTWILD, 0.0, 0.2, -0.4, 0.09);
            proto(RegionType.GOLDEN_GROVE, 0.4, -0.1, -0.55, 0.08);
            proto(RegionType.EMERALD_WETLANDS, 0.15, 0.7, -0.5, 0.07);
            proto(RegionType.FUNGAL_EXPANSE, 0.35, 0.45, 0.05, 0.08);
            proto(RegionType.FLOODED_DEPTHS, -0.1, 0.95, 0.0, 0.09);
            proto(RegionType.SUNKEN_GARDENS, 0.3, 1.0, 0.4, 0.05);
            proto(RegionType.STARLIGHT_CAVERNS, -0.35, 0.15, 0.3, 0.09);
            proto(RegionType.CRYSTAL_DEEP, -0.45, -0.3, 0.6, 0.07);
            proto(RegionType.STORMSTONE_CAVERNS, -0.1, -0.55, 0.9, 0.06);
            proto(RegionType.EVERFROST, -0.95, 0.25, 0.1, 0.06);
            proto(RegionType.HOLLOW_PEAKS, -0.6, -0.45, -0.15, 0.08);
            proto(RegionType.PETRIFIED_FOREST, 0.3, -0.65, 0.2, 0.06);
            proto(RegionType.ASHEN_HOLLOWS, 0.9, -0.6, 0.3, 0.06);
            proto(RegionType.CRIMSON_BASIN, 0.8, 0.15, 0.5, 0.06);
        } else {
            proto(RegionType.THE_ABYSS, 0.0, 0.0, 0.0, 0.55);
            proto(RegionType.STORMSTONE_CAVERNS, -0.5, -0.5, 0.8, 0.15);
            proto(RegionType.ASHEN_HOLLOWS, 0.8, -0.5, 0.3, 0.15);
            proto(RegionType.CRYSTAL_DEEP, -0.6, 0.4, 0.5, 0.15);
        }
        calibrate();
        computeOverrides();
    }

    private void proto(RegionType type, double t, double m, double x, double share) {
        prototypes.put(type, new double[]{t, m, x});
        targetShare.put(type, share);
        bias.put(type, 0.0);
    }

    /** Standard normal CDF (Abramowitz-Stegun erf approximation). */
    private static double phi(double x) {
        double t = 1 / (1 + 0.3275911 * Math.abs(x) / Math.sqrt(2));
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t
                * Math.exp(-x * x / 2);
        return x >= 0 ? 0.5 * (1 + y) : 0.5 * (1 - y);
    }

    /**
     * Normalises the climate axes to an approximately uniform distribution and solves per-ecosystem
     * biases so each ecosystem covers roughly its target share of the layer. Deterministic: uses fixed
     * sample points (seed only affects the measured noise spread).
     */
    private void calibrate() {
        // Measure the spread of the raw climate signal.
        double[] sum2 = new double[3];
        int n = 0;
        for (int i = -24; i <= 24; i++) {
            for (int j = -24; j <= 24; j++) {
                double px = i * 977.0 + 13;
                double pz = j * 977.0 - 29;
                for (int a = 0; a < 3; a++) {
                    double v = climate[a].sample(px, pz) * 1.35 + climateDetail[a].sample(px, pz) * 0.55;
                    sum2[a] += v * v;
                }
                n++;
            }
        }
        for (int a = 0; a < 3; a++) {
            sigma[a] = Math.sqrt(sum2[a] / n) + 1e-6;
        }
        // Solve biases on a fixed uniform sample of the climate cube.
        int samples = 6000;
        double[][] pts = new double[samples][3];
        long h = 0x5EEDL;
        for (int k = 0; k < samples; k++) {
            for (int a = 0; a < 3; a++) {
                h = Hashing.mix(h + 0x9E3779B97F4A7C15L);
                pts[k][a] = Hashing.signed(h);
            }
        }
        for (int iter = 0; iter < 60; iter++) {
            Map<RegionType, Integer> counts = new EnumMap<>(RegionType.class);
            for (double[] p : pts) {
                counts.merge(nearestPrototype(p, 0), 1, Integer::sum);
            }
            for (RegionType type : prototypes.keySet()) {
                double share = counts.getOrDefault(type, 0) / (double) samples;
                double err = targetShare.get(type) - share;
                bias.put(type, bias.get(type) + err * 0.8);
            }
        }
    }

    private RegionType nearestPrototype(double[] c, double distOrigin) {
        RegionType best = layer.regions.get(0);
        double bestD = Double.MAX_VALUE;
        for (RegionType type : prototypes.keySet()) {
            double d = climateDistance(type, c);
            if (layer == CavernLayer.UPPER && distOrigin > 0 && distOrigin < 850) {
                // Keep the surroundings of the starting cavern welcoming: dangerous ecosystems are pushed
                // beyond ~500 blocks, medium ones thinned out near the start.
                double k = 1 - distOrigin / 850;
                d += switch (type.danger()) {
                    case LOW -> -0.1 * k;
                    case MEDIUM -> 0.15 * k;
                    case HIGH, EXTREME -> 2.0 * k;
                };
            }
            if (d < bestD) {
                bestD = d;
                best = type;
            }
        }
        return best;
    }

    public CavernLayer layer() {
        return layer;
    }

    public int cellSize() {
        return cell;
    }

    public double cellCenterX(int cx, int cz) {
        return (cx + 0.15 + 0.7 * Hashing.unit(Hashing.hash(seed, cx, cz, 11))) * cell;
    }

    public double cellCenterZ(int cx, int cz) {
        return (cz + 0.15 + 0.7 * Hashing.unit(Hashing.hash(seed, cx, cz, 12))) * cell;
    }

    /** Climate vector (temperature, moisture, mineral) at a cell centre, including per-cell jitter. */
    public double[] climateAt(int cx, int cz) {
        double px = cellCenterX(cx, cz);
        double pz = cellCenterZ(cx, cz);
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double raw = climate[i].sample(px, pz) * 1.35 + climateDetail[i].sample(px, pz) * 0.55;
            c[i] = 2 * phi(raw / sigma[i]) - 1 + Hashing.signed(Hashing.hash(seed, cx, cz, 30 + i)) * 0.12;
        }
        return c;
    }

    private double climateDistance(RegionType type, double[] c) {
        double[] p = prototypes.get(type);
        double d0 = c[0] - p[0];
        double d1 = c[1] - p[1];
        double d2 = c[2] - p[2];
        return Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2) - bias.get(type);
    }

    private RegionType climateType(int cx, int cz) {
        double px = cellCenterX(cx, cz);
        double pz = cellCenterZ(cx, cz);
        return nearestPrototype(climateAt(cx, cz), Math.max(1e-3, Math.sqrt(px * px + pz * pz)));
    }

    private final ThreadLocal<long[]> typeCacheKeys = ThreadLocal.withInitial(() -> {
        long[] a = new long[512];
        java.util.Arrays.fill(a, Long.MIN_VALUE);
        return a;
    });
    private final ThreadLocal<RegionType[]> typeCacheValues = ThreadLocal.withInitial(() -> new RegionType[512]);

    /** The region type assigned to a cell (after origin and coverage guarantees). */
    public RegionType typeOf(int cx, int cz) {
        long key = pack(cx, cz);
        long[] keys = typeCacheKeys.get();
        RegionType[] values = typeCacheValues.get();
        int slot = (int) (Hashing.mix(key) & 511);
        if (keys[slot] == key && values[slot] != null) {
            return values[slot];
        }
        RegionType t = rawType(cx, cz);
        keys[slot] = key;
        values[slot] = t;
        return t;
    }

    private RegionType rawType(int cx, int cz) {
        RegionType o = overrides.get(pack(cx, cz));
        return o != null ? o : climateType(cx, cz);
    }

    private static long pack(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private double warpedX(double x, double z) {
        return x + warpX.sample(x, z) * 75.0 + warpX2.sample(x, z) * 18.0;
    }

    private double warpedZ(double x, double z) {
        return z + warpZ.sample(x, z) * 75.0 + warpZ2.sample(x, z) * 18.0;
    }

    /** The Voronoi cell containing a block position. */
    public int[] cellAt(double x, double z) {
        double wx = warpedX(x, z);
        double wz = warpedZ(x, z);
        int bx = Math.floorDiv((int) Math.floor(wx), cell);
        int bz = Math.floorDiv((int) Math.floor(wz), cell);
        double best = Double.MAX_VALUE;
        int[] r = new int[2];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = bx + dx;
                int cz = bz + dz;
                double ddx = cellCenterX(cx, cz) - wx;
                double ddz = cellCenterZ(cx, cz) - wz;
                double d = ddx * ddx + ddz * ddz;
                if (d < best) {
                    best = d;
                    r[0] = cx;
                    r[1] = cz;
                }
            }
        }
        return r;
    }

    private void computeOverrides() {
        int[] origin = cellAt(0, 0);
        if (layer == CavernLayer.UPPER) {
            // The starting cavern and its immediate surroundings are Rootwild.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    double px = cellCenterX(origin[0] + dx, origin[1] + dz);
                    double pz = cellCenterZ(origin[0] + dx, origin[1] + dz);
                    if (dx == 0 && dz == 0 || px * px + pz * pz < 95 * 95) {
                        overrides.put(pack(origin[0] + dx, origin[1] + dz), RegionType.ROOTWILD);
                    }
                }
            }
        }
        // Guarantee every ecosystem of this layer within GUARANTEE_RANGE blocks of the origin.
        int r = GUARANTEE_RANGE / cell;
        EnumSet<RegionType> present = EnumSet.noneOf(RegionType.class);
        List<int[]> candidates = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = origin[0] + dx;
                int cz = origin[1] + dz;
                double px = cellCenterX(cx, cz);
                double pz = cellCenterZ(cx, cz);
                double d = Math.sqrt(px * px + pz * pz);
                if (d > GUARANTEE_RANGE) {
                    continue;
                }
                present.add(rawType(cx, cz));
                if (d > 800 && d < GUARANTEE_RANGE - 200) {
                    candidates.add(new int[]{cx, cz});
                }
            }
        }
        for (RegionType wanted : prototypes.keySet()) {
            if (present.contains(wanted)) {
                continue;
            }
            // Pick the candidate cell whose climate is closest to the missing ecosystem, so the forced
            // region still sits in plausible surroundings, then grow it to a small cluster.
            int[] best = null;
            double bestD = Double.MAX_VALUE;
            for (int[] c : candidates) {
                if (overrides.containsKey(pack(c[0], c[1]))) {
                    continue;
                }
                double d = climateDistance(wanted, climateAt(c[0], c[1]));
                if (d < bestD) {
                    bestD = d;
                    best = c;
                }
            }
            if (best == null) {
                continue;
            }
            overrides.put(pack(best[0], best[1]), wanted);
            for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long k = pack(best[0] + n[0], best[1] + n[1]);
                if (!overrides.containsKey(k)) {
                    overrides.put(k, wanted);
                }
            }
            present.add(wanted);
        }
    }

    /** Samples region membership and border blend weights at a block position. */
    public RegionSample sample(double x, double z, RegionSample out) {
        double wx = warpedX(x, z);
        double wz = warpedZ(x, z);
        int bx = Math.floorDiv((int) Math.floor(wx), cell);
        int bz = Math.floorDiv((int) Math.floor(wz), cell);
        double[] dist = new double[9];
        int[] cxs = new int[9];
        int[] czs = new int[9];
        double best = Double.MAX_VALUE;
        double second = Double.MAX_VALUE;
        int bestIdx = 0;
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++, i++) {
                int cx = bx + dx;
                int cz = bz + dz;
                double ddx = cellCenterX(cx, cz) - wx;
                double ddz = cellCenterZ(cx, cz) - wz;
                double d = Math.sqrt(ddx * ddx + ddz * ddz);
                dist[i] = d;
                cxs[i] = cx;
                czs[i] = cz;
                if (d < best) {
                    second = best;
                    best = d;
                    bestIdx = i;
                } else if (d < second) {
                    second = d;
                }
            }
        }
        java.util.Arrays.fill(out.weights, 0);
        RegionType primary = typeOf(cxs[bestIdx], czs[bestIdx]);
        double total = 0;
        double nearestOther = Double.MAX_VALUE;
        for (i = 0; i < 9; i++) {
            double t = (dist[i] - best) * 0.5 / BLEND;
            RegionType type = typeOf(cxs[i], czs[i]);
            if (type != primary) {
                nearestOther = Math.min(nearestOther, (dist[i] - best) * 0.5);
            }
            if (t >= 1) {
                continue;
            }
            double w = (1 - t) * (1 - t) * (1 - t);
            out.weights[type.ordinal()] += w;
            total += w;
        }
        for (int k = 0; k < out.weights.length; k++) {
            out.weights[k] /= total;
        }
        out.layer = layer;
        out.cellX = cxs[bestIdx];
        out.cellZ = czs[bestIdx];
        out.primary = primary;
        // Distance to the nearest border with a *different* ecosystem (cell borders inside one region don't count).
        out.borderDistance = nearestOther == Double.MAX_VALUE ? cell : nearestOther;
        return out;
    }

    public RegionType typeAt(double x, double z) {
        int[] c = cellAt(x, z);
        return typeOf(c[0], c[1]);
    }

    /** Distinct ecosystems of the cells surrounding a cell (approximate Voronoi neighbours). */
    public Set<RegionType> neighbourTypes(int cx, int cz) {
        EnumSet<RegionType> set = EnumSet.noneOf(RegionType.class);
        RegionType self = typeOf(cx, cz);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                RegionType t = typeOf(cx + dx, cz + dz);
                if (t != self) {
                    set.add(t);
                }
            }
        }
        return set;
    }
}
