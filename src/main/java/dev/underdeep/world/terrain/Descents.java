package dev.underdeep.world.terrain;

import dev.underdeep.world.noise.Hashing;

/**
 * Stage 10 vertical connections: walkable spiral descent caves between the upper and the deep layer.
 * <p>
 * Each deep-layer graph cell may own one descent. Its centre is chosen in solid rock beside the deep chamber, where
 * neither layer is open. A short tunnel leaves the wall of the nearest dry upper cavern at floor level, winds down a
 * helix inside the rock at a walkable gradient (about 1 block down per 3–4 forward), and leaves through a second
 * tunnel into the deep chamber at its floor. Where the helix happens to cross open space it continues as a natural
 * rock ledge, and where it would meet water, lava or a pond it is closed off by rock, so fluids never drain into it.
 * <p>
 * A descent is a pure function of (seed, deep cell) computed from the terrain model without descents, so every chunk
 * agrees on it regardless of generation order. Thread-safe: immutable values and thread-local caches.
 */
final class Descents {
    /** One descent: a polyline of band segments (floor height varies along the line). */
    static final class Descent {
        final double[] x, z, y, w, h;
        final double minX, minZ, maxX, maxZ;
        final int topX, topY, topZ, bottomX, bottomY, bottomZ;
        final double centerX, centerZ, radius;

        Descent(double[] x, double[] z, double[] y, double[] w, double[] h, int[] top, int[] bottom, double cx, double cz, double r) {
            this.x = x;
            this.z = z;
            this.y = y;
            this.w = w;
            this.h = h;
            double a = Double.MAX_VALUE, b = Double.MAX_VALUE, c = -Double.MAX_VALUE, d = -Double.MAX_VALUE;
            for (int i = 0; i < x.length; i++) {
                a = Math.min(a, x[i] - w[i] - 1);
                b = Math.min(b, z[i] - w[i] - 1);
                c = Math.max(c, x[i] + w[i] + 1);
                d = Math.max(d, z[i] + w[i] + 1);
            }
            minX = a;
            minZ = b;
            maxX = c;
            maxZ = d;
            topX = top[0];
            topY = top[1];
            topZ = top[2];
            bottomX = bottom[0];
            bottomY = bottom[1];
            bottomZ = bottom[2];
            centerX = cx;
            centerZ = cz;
            radius = r;
        }
    }

    private record Entry(int cx, int cz, Descent d) {
    }

    static final Descent NONE = null;
    /** Diagnostic counters of placement outcomes (rock, top, bottom, build, ok). */
    static final java.util.concurrent.atomic.AtomicIntegerArray STATS = new java.util.concurrent.atomic.AtomicIntegerArray(6);
    /** Target gradient (blocks down per block along the helix). */
    static final double SLOPE = 0.28;

    private final UnderdeepTerrain terrain;
    private final long seed;
    private final double frequency;
    /** Shared across worker threads (descents are immutable); bounded. */
    private final java.util.concurrent.ConcurrentHashMap<Long, Entry> shared = new java.util.concurrent.ConcurrentHashMap<>();
    private final ThreadLocal<Object[]> cache = ThreadLocal.withInitial(() -> new Object[1024]);

    Descents(UnderdeepTerrain terrain, long seed, double frequency) {
        this.terrain = terrain;
        this.seed = Hashing.hash(seed, Hashing.salt("descents"));
        this.frequency = frequency;
    }

    Descent of(int dcx, int dcz) {
        Object[] c = cache.get();
        long key = ((long) dcx << 32) ^ (dcz & 0xFFFFFFFFL);
        int slot = (int) (Hashing.mix(key ^ 0xD35CL) & 1023);
        if (c[slot] instanceof Entry e && e.cx == dcx && e.cz == dcz) {
            return e.d;
        }
        Entry e = shared.get(key);
        if (e == null) {
            e = new Entry(dcx, dcz, resolve(dcx, dcz));
            if (shared.size() > 16384) {
                shared.clear();
            }
            shared.put(key, e);
        }
        c[slot] = e;
        return e.d;
    }

    private final java.util.concurrent.ConcurrentHashMap<Long, Entry> raw = new java.util.concurrent.ConcurrentHashMap<>();

    /** Candidate before overlap resolution (shared, bounded). */
    private Descent raw(int dcx, int dcz) {
        long key = ((long) dcx << 32) ^ (dcz & 0xFFFFFFFFL);
        Entry e = raw.get(key);
        if (e == null) {
            e = new Entry(dcx, dcz, compute(dcx, dcz));
            if (raw.size() > 16384) {
                raw.clear();
            }
            raw.put(key, e);
        }
        return e.d;
    }

    private double priority(int dcx, int dcz) {
        return Hashing.unit(Hashing.hash(seed, dcx, dcz, 999));
    }

    /** Drops a candidate whose helix or leads come too close to a neighbour's candidate of higher priority. */
    private Descent resolve(int dcx, int dcz) {
        Descent d = raw(dcx, dcz);
        if (d == null) {
            return null;
        }
        double p = priority(dcx, dcz);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Descent o = raw(dcx + dx, dcz + dz);
                if (o == null || o.maxX + 10 < d.minX || o.minX - 10 > d.maxX || o.maxZ + 10 < d.minZ || o.minZ - 10 > d.maxZ) {
                    continue;
                }
                double po = priority(dcx + dx, dcz + dz);
                if (po > p || po == p && (dx < 0 || dx == 0 && dz < 0)) {
                    return null;
                }
            }
        }
        return d;
    }

    private boolean rock(int x, int z) {
        TerrainColumn b = terrain.baseColumn(x, z);
        // the deep part of the helix must be enclosed in rock; where the upper layer is open the top turn opens as a
        // spiral sinkhole in a dry cavern floor (checked per column when the bands are applied)
        boolean upperOk = b.upperCeiling - b.upperFloor < 1 || b.upperFloor >= UnderdeepTerrain.SEA_LEVEL + 6
                && Double.isNaN(b.pondLevel) && b.pondGuard < 0.05 && !b.upperLava;
        return upperOk && b.deepCeiling - b.deepFloor < 1 && b.shaftDist > b.shaftRadius + 3 && b.landmarks.length == 0
                && b.upperFloor - b.deepFloor > 40;
    }

    private Descent compute(int dcx, int dcz) {
        long h = Hashing.hash(seed, dcx, dcz, 1);
        if (Hashing.unit(h) >= 0.6 * frequency) {
            return null;
        }
        CavernGraph deep = terrain.graph(CavernLayer.DEEP);
        CavernGraph.Node dn = deep.node(dcx, dcz);
        double radius = 12 + 8 * Hashing.unit(Hashing.hash(seed, dcx, dcz, 2));
        double a0 = Hashing.unit(Hashing.hash(seed, dcx, dcz, 3)) * Math.PI * 2;
        for (int k = 0; k < 18; k++) {
            double ang = a0 + (k % 6) * Math.PI / 3 + (k / 6) * 0.5;
            double dist = dn.radius() * Math.max(dn.axis(), 0.6) * 0.95 + radius + 8 + (k / 6) * 26;
            double cx = dn.x() + Math.cos(ang) * dist;
            double cz = dn.z() + Math.sin(ang) * dist;
            if (cx * cx + cz * cz < 280.0 * 280.0) {
                continue; // keep the starting cavern and its village intact
            }
            int icx = (int) Math.floor(cx), icz = (int) Math.floor(cz);
            boolean ok = rock(icx, icz);
            for (int j = 0; ok && j < 6; j++) {
                double ra = j * Math.PI / 3;
                ok = rock((int) Math.floor(cx + Math.cos(ra) * radius), (int) Math.floor(cz + Math.sin(ra) * radius));
            }
            if (!ok) {
                STATS.incrementAndGet(0);
                continue;
            }
            // upper entry: march from the centre towards the nearest upper chamber until the cavern opens
            CavernGraph.Node un = terrain.graph(CavernLayer.UPPER).nearestNode(cx, cz);
            int[] top = entry(cx, cz, un.x(), un.z(), radius, true);
            if (top == null) {
                STATS.incrementAndGet(1);
                continue;
            }
            int[] bottom = entry(cx, cz, dn.x(), dn.z(), radius, false);
            if (bottom == null) {
                STATS.incrementAndGet(2);
                continue;
            }
            Descent d = build(cx, cz, radius, top, bottom, Hashing.hash(seed, dcx, dcz, 40 + k));
            if (d != null) {
                STATS.incrementAndGet(4);
                return d;
            }
            STATS.incrementAndGet(3);
        }
        STATS.incrementAndGet(5);
        return null;
    }

    /** Finds where a lead tunnel from the helix ring meets the open cavern (dry floor), or null. */
    private int[] entry(double cx, double cz, double tx, double tz, double radius, boolean upper) {
        double dx = tx - cx, dz = tz - cz;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1) {
            return null;
        }
        dx /= len;
        dz /= len;
        for (double s = radius + 3; s < 170; s += 3) {
            int x = (int) Math.floor(cx + dx * s), z = (int) Math.floor(cz + dz * s);
            TerrainColumn b = terrain.baseColumn(x, z);
            double gap = upper ? b.upperCeiling - b.upperFloor : b.deepCeiling - b.deepFloor;
            if (gap > 7) {
                // step a little further in so the mouth opens cleanly, then validate the floor
                int ex = (int) Math.floor(cx + dx * (s + 4)), ez = (int) Math.floor(cz + dz * (s + 4));
                TerrainColumn e = terrain.baseColumn(ex, ez);
                double floor = upper ? e.upperFloor : e.deepFloor;
                double g2 = upper ? e.upperCeiling - e.upperFloor : e.deepCeiling - e.deepFloor;
                int fluid = upper ? UnderdeepTerrain.SEA_LEVEL : CavernLayer.DEEP.fluidLevel;
                if (g2 < 7 || floor < fluid + 3 || !Double.isNaN(e.pondLevel) || e.pondGuard > 0.05 || e.shaftDist < e.shaftRadius + 4) {
                    return null;
                }
                return new int[]{ex, (int) Math.floor(floor) + 1, ez};
            }
        }
        return null;
    }

    private Descent build(double cx, double cz, double radius, int[] top, int[] bottom, long h) {
        double drop = top[1] - bottom[1];
        if (drop < 12 || drop > 280) {
            return null;
        }
        double aTop = Math.atan2(top[2] - cz, top[0] - cx);
        double aBot = Math.atan2(bottom[2] - cz, bottom[0] - cx);
        double sign = (h & 1) == 0 ? 1 : -1;
        double circ = Math.PI * 2 * radius;
        double tmin = drop / (circ * SLOPE);
        double frac = ((aBot - aTop) * sign) % (Math.PI * 2);
        if (frac < 0) {
            frac += Math.PI * 2;
        }
        frac /= Math.PI * 2;
        double turns = Math.floor(tmin) + frac;
        if (turns < tmin) {
            turns += 1;
        }
        // rock between stacked turns: pitch per turn must leave headroom plus a solid roof
        if (turns > 0.01 && drop / turns < 10.5) {
            return null;
        }
        int n = Math.max(6, (int) Math.ceil(turns * 26));
        int total = 3 + n + 1 + 3;
        double[] x = new double[total], z = new double[total], y = new double[total], w = new double[total], hh = new double[total];
        int i = 0;
        double topY = top[1];
        double botY = bottom[1];
        // lead-in from the upper cavern to the helix (level)
        double rx0 = cx + Math.cos(aTop) * radius, rz0 = cz + Math.sin(aTop) * radius;
        for (int k = 0; k < 3; k++) {
            double t = k / 3.0;
            x[i] = top[0] + (rx0 - top[0]) * t;
            z[i] = top[2] + (rz0 - top[2]) * t;
            y[i] = topY - 0.6 * t;
            i++;
        }
        for (int k = 0; k <= n; k++) {
            double t = (double) k / n;
            double a = aTop + sign * turns * Math.PI * 2 * t;
            double r = radius * (1 + 0.08 * Math.sin(t * 17 + (h & 7)));
            x[i] = cx + Math.cos(a) * r;
            z[i] = cz + Math.sin(a) * r;
            y[i] = topY - 0.6 - (drop - 1.2) * t;
            i++;
        }
        double rx1 = x[i - 1], rz1 = z[i - 1];
        for (int k = 1; k <= 3; k++) {
            double t = k / 3.0;
            x[i] = rx1 + (bottom[0] - rx1) * t;
            z[i] = rz1 + (bottom[2] - rz1) * t;
            y[i] = botY + 0.6 * (1 - t);
            i++;
        }
        for (int k = 0; k < total; k++) {
            long v = Hashing.mix(h + 31L * k);
            w[k] = 2.6 + 1.3 * Hashing.unit(v);
            hh[k] = 5.4 + 1.8 * Hashing.unit(Hashing.mix(v));
        }
        return new Descent(x, z, y, w, hh, top, bottom, cx, cz, radius);
    }

    /**
     * Adds the descent bands crossing a column to it (called while computing the full column; uses the column's
     * own floors to decide where a band meets open space, water or a pond).
     */
    void apply(TerrainColumn c, int x, int z) {
        int cell = terrain.graph(CavernLayer.DEEP).cellSize();
        int bx = Math.floorDiv(x, cell), bz = Math.floorDiv(z, cell);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Descent d = of(bx + dx, bz + dz);
                if (d == null || x < d.minX || x > d.maxX || z < d.minZ || z > d.maxZ) {
                    continue;
                }
                for (int i = 0; i < d.x.length - 1; i++) {
                    double ax = d.x[i], az = d.z[i], ex = d.x[i + 1], ez = d.z[i + 1];
                    double wm = Math.max(d.w[i], d.w[i + 1]);
                    if (x < Math.min(ax, ex) - wm || x > Math.max(ax, ex) + wm || z < Math.min(az, ez) - wm || z > Math.max(az, ez) + wm) {
                        continue;
                    }
                    double vx = ex - ax, vz = ez - az;
                    double len2 = vx * vx + vz * vz;
                    double t = len2 > 0 ? ((x + 0.5 - ax) * vx + (z + 0.5 - az) * vz) / len2 : 0;
                    t = Math.max(0, Math.min(1, t));
                    double px = ax + vx * t - x - 0.5, pz = az + vz * t - z - 0.5;
                    double w = d.w[i] + (d.w[i + 1] - d.w[i]) * t;
                    double q = Math.sqrt(px * px + pz * pz) / w;
                    if (q >= 1) {
                        continue;
                    }
                    double floor = Math.floor(d.y[i] + (d.y[i + 1] - d.y[i]) * t + 1.3 * q * q);
                    double ceil = floor + (d.h[i] + (d.h[i + 1] - d.h[i]) * t) * (1 - 0.45 * q * q);
                    addBand(c, floor, ceil);
                }
            }
        }
    }

    private static void addBand(TerrainColumn c, double floor, double ceil) {
        boolean ledge = false;
        // upper layer
        double uf = c.upperFloor, uc = c.upperCeiling;
        if (uc - uf > 2 && ceil > uf - 4 && floor < uc) {
            boolean dry = uf >= UnderdeepTerrain.SEA_LEVEL + 2 && Double.isNaN(c.pondLevel) && c.pondGuard < 0.05;
            if (floor >= uf + 1) {
                if (floor <= UnderdeepTerrain.SEA_LEVEL + 1 || !Double.isNaN(c.pondLevel) && floor <= c.pondLevel + 2) {
                    return; // would be flooded
                }
                ledge = true;
            } else if (!dry) {
                ceil = Math.min(ceil, uf - 4);
            }
        }
        // deep layer
        double df = c.deepFloor, dc = c.deepCeiling;
        if (dc - df > 2 && ceil > df - 4 && floor < dc) {
            boolean dry = df >= CavernLayer.DEEP.fluidLevel + 2 && (Double.isNaN(c.pondLevel) || c.pondLevel > c.separation) && c.pondGuard < 0.05;
            if (floor >= df + 1) {
                if (floor <= CavernLayer.DEEP.fluidLevel + 1) {
                    return;
                }
                ledge = true;
            } else if (!dry) {
                ceil = Math.min(ceil, df - 4);
            }
        }
        if (ceil - floor < 2) {
            return;
        }
        if (c.bandFloor == TerrainColumn.NO_BANDS) {
            c.bandFloor = new double[6];
            c.bandCeil = new double[6];
            c.bandLedge = new boolean[6];
        }
        for (int i = 0; i < c.bandCount; i++) {
            if (floor <= c.bandCeil[i] + 1 && ceil >= c.bandFloor[i] - 1) { // merge overlapping bands
                c.bandFloor[i] = Math.min(c.bandFloor[i], floor);
                c.bandCeil[i] = Math.max(c.bandCeil[i], ceil);
                c.bandLedge[i] |= ledge;
                return;
            }
        }
        if (c.bandCount == c.bandFloor.length) {
            c.bandFloor = java.util.Arrays.copyOf(c.bandFloor, c.bandCount * 2);
            c.bandCeil = java.util.Arrays.copyOf(c.bandCeil, c.bandCount * 2);
            c.bandLedge = java.util.Arrays.copyOf(c.bandLedge, c.bandCount * 2);
        }
        {
            c.bandFloor[c.bandCount] = floor;
            c.bandCeil[c.bandCount] = ceil;
            c.bandLedge[c.bandCount] = ledge;
            c.bandCount++;
        }
    }

    /** Descents whose extent intersects a rectangle (diagnostics, site protection). */
    void collect(int minX, int minZ, int maxX, int maxZ, java.util.List<Descent> out) {
        int cell = terrain.graph(CavernLayer.DEEP).cellSize();
        for (int cx = Math.floorDiv(minX, cell) - 1; cx <= Math.floorDiv(maxX, cell) + 1; cx++) {
            for (int cz = Math.floorDiv(minZ, cell) - 1; cz <= Math.floorDiv(maxZ, cell) + 1; cz++) {
                Descent d = of(cx, cz);
                if (d != null && d.maxX >= minX && d.minX <= maxX && d.maxZ >= minZ && d.minZ <= maxZ) {
                    out.add(d);
                }
            }
        }
    }
}
