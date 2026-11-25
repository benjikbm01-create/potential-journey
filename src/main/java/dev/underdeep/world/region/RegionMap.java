package dev.underdeep.world.region;

import dev.underdeep.world.noise.FractalNoise;
import dev.underdeep.world.noise.Hashing;
import dev.underdeep.world.terrain.CavernLayer;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Deterministic large-scale region layout for one {@link CavernLayer}.
 * <p>
 * Regions are domain-warped Voronoi cells. Each cell's {@link RegionType} is chosen from a slowly
 * varying "climate" (temperature / moisture noise) so that neighbouring regions follow a plausible
 * geography (volcanic zones border dry crystal or temperate forest more often than lakes). The cell
 * containing the world origin in the upper layer is always {@link RegionType#ROOTWILD}, and every
 * region type of a layer is guaranteed to appear within a few cells of the origin.
 * <p>
 * Pure function of (seed, x, z); immutable and thread-safe.
 */
public final class RegionMap {
    private static final double BLEND = 72.0;
    private static final int GUARANTEE_RADIUS = 3;

    private final CavernLayer layer;
    private final long seed;
    private final int cell;
    private final FractalNoise warpX;
    private final FractalNoise warpZ;
    private final FractalNoise temperature;
    private final FractalNoise moisture;
    private final Map<RegionType, double[]> prototypes = new EnumMap<>(RegionType.class);
    private final Map<Long, RegionType> overrides = new HashMap<>();

    public RegionMap(long worldSeed, CavernLayer layer) {
        this.layer = layer;
        this.seed = Hashing.hash(worldSeed, Hashing.salt("region_map"), layer.ordinal());
        this.cell = layer.regionCell;
        this.warpX = new FractalNoise(Hashing.hash(seed, 1), 3, 1 / 420.0);
        this.warpZ = new FractalNoise(Hashing.hash(seed, 2), 3, 1 / 420.0);
        this.temperature = new FractalNoise(Hashing.hash(seed, 3), 2, 1 / 2600.0);
        this.moisture = new FractalNoise(Hashing.hash(seed, 4), 2, 1 / 2600.0);
        if (layer == CavernLayer.UPPER) {
            prototypes.put(RegionType.ROOTWILD, new double[]{0.0, 0.1, -0.12});
            prototypes.put(RegionType.FUNGAL_EXPANSE, new double[]{0.45, 0.55, 0.0});
            prototypes.put(RegionType.FLOODED_DEPTHS, new double[]{-0.4, 0.6, 0.0});
            prototypes.put(RegionType.ASHEN_HOLLOWS, new double[]{0.6, -0.5, 0.0});
            prototypes.put(RegionType.CRYSTAL_DEEP, new double[]{-0.55, -0.45, 0.0});
        } else {
            prototypes.put(RegionType.THE_ABYSS, new double[]{0.0, 0.0, 0.3});
            prototypes.put(RegionType.CRYSTAL_DEEP, new double[]{-0.6, -0.3, 0.0});
            prototypes.put(RegionType.ASHEN_HOLLOWS, new double[]{0.6, -0.3, 0.0});
        }
        computeOverrides();
    }

    public CavernLayer layer() {
        return layer;
    }

    public int cellSize() {
        return cell;
    }

    /** Centre of a region cell, in block coordinates (in warped space). */
    public double cellCenterX(int cx, int cz) {
        return (cx + 0.15 + 0.7 * Hashing.unit(Hashing.hash(seed, cx, cz, 11))) * cell;
    }

    public double cellCenterZ(int cx, int cz) {
        return (cz + 0.15 + 0.7 * Hashing.unit(Hashing.hash(seed, cx, cz, 12))) * cell;
    }

    private RegionType climateType(int cx, int cz) {
        double px = cellCenterX(cx, cz);
        double pz = cellCenterZ(cx, cz);
        double t = temperature.sample(px, pz) * 1.7 + Hashing.signed(Hashing.hash(seed, cx, cz, 21)) * 0.32;
        double m = moisture.sample(px, pz) * 1.7 + Hashing.signed(Hashing.hash(seed, cx, cz, 22)) * 0.32;
        RegionType best = layer.regions.get(0);
        double bestD = Double.MAX_VALUE;
        for (Map.Entry<RegionType, double[]> e : prototypes.entrySet()) {
            double[] p = e.getValue();
            double d = Math.sqrt((t - p[0]) * (t - p[0]) + (m - p[1]) * (m - p[1])) - p[2];
            if (d < bestD) {
                bestD = d;
                best = e.getKey();
            }
        }
        return best;
    }

    private final ThreadLocal<long[]> typeCacheKeys = ThreadLocal.withInitial(() -> {
        long[] a = new long[128];
        java.util.Arrays.fill(a, Long.MIN_VALUE);
        return a;
    });
    private final ThreadLocal<RegionType[]> typeCacheValues = ThreadLocal.withInitial(() -> new RegionType[128]);

    /** The region type assigned to a cell (after origin guarantees). */
    public RegionType typeOf(int cx, int cz) {
        long key = pack(cx, cz);
        long[] keys = typeCacheKeys.get();
        RegionType[] values = typeCacheValues.get();
        int slot = (int) (Hashing.mix(key) & 127);
        if (keys[slot] == key && values[slot] != null) {
            return values[slot];
        }
        RegionType o = overrides.get(key);
        RegionType t = o != null ? o : climateType(cx, cz);
        keys[slot] = key;
        values[slot] = t;
        return t;
    }

    private static long pack(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private int[] nearestCell(double x, double z) {
        double wx = x + warpX.sample(x, z) * 150.0;
        double wz = z + warpZ.sample(x, z) * 150.0;
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
        int[] origin = nearestCell(0, 0);
        if (layer == CavernLayer.UPPER) {
            overrides.put(pack(origin[0], origin[1]), RegionType.ROOTWILD);
        }
        // Guarantee every layer region appears near the origin so players can reach all of them.
        EnumSet<RegionType> present = EnumSet.noneOf(RegionType.class);
        for (int dx = -GUARANTEE_RADIUS; dx <= GUARANTEE_RADIUS; dx++) {
            for (int dz = -GUARANTEE_RADIUS; dz <= GUARANTEE_RADIUS; dz++) {
                RegionType o = overrides.get(pack(origin[0] + dx, origin[1] + dz));
                present.add(o != null ? o : climateType(origin[0] + dx, origin[1] + dz));
            }
        }
        int attempt = 0;
        for (RegionType wanted : layer.regions) {
            if (present.contains(wanted)) {
                continue;
            }
            // Deterministically pick a ring-2 cell that is not the origin and not yet overridden.
            for (int tries = 0; tries < 64; tries++) {
                long h = Hashing.hash(seed, wanted.ordinal(), attempt++, 77);
                int dx = (int) Long.remainderUnsigned(h, 5) - 2;
                int dz = (int) Long.remainderUnsigned(h >>> 20, 5) - 2;
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) {
                    continue;
                }
                long key = pack(origin[0] + dx, origin[1] + dz);
                if (overrides.containsKey(key)) {
                    continue;
                }
                overrides.put(key, wanted);
                present.add(wanted);
                break;
            }
        }
    }

    /** Samples region membership and border blend weights at a block position. */
    public RegionSample sample(double x, double z, RegionSample out) {
        double wx = x + warpX.sample(x, z) * 150.0;
        double wz = z + warpZ.sample(x, z) * 150.0;
        int bx = Math.floorDiv((int) Math.floor(wx), cell);
        int bz = Math.floorDiv((int) Math.floor(wz), cell);
        double[] dist = new double[9];
        RegionType[] types = new RegionType[9];
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
        double total = 0;
        for (i = 0; i < 9; i++) {
            // Bisector-distance approximation: half the difference of distances.
            double t = (dist[i] - best) * 0.5 / BLEND;
            if (t >= 1) {
                continue;
            }
            double w = (1 - t) * (1 - t) * (1 - t);
            types[i] = typeOf(cxs[i], czs[i]);
            out.weights[types[i].ordinal()] += w;
            total += w;
        }
        for (int k = 0; k < out.weights.length; k++) {
            out.weights[k] /= total;
        }
        out.layer = layer;
        out.cellX = cxs[bestIdx];
        out.cellZ = czs[bestIdx];
        out.primary = types[bestIdx] != null ? types[bestIdx] : typeOf(cxs[bestIdx], czs[bestIdx]);
        out.borderDistance = (second - best) * 0.5;
        return out;
    }

    public RegionType typeAt(double x, double z) {
        int[] c = nearestCell(x, z);
        return typeOf(c[0], c[1]);
    }
}
