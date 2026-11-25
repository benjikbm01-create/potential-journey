package dev.underdeep.world.terrain;

import dev.underdeep.world.noise.Hashing;
import dev.underdeep.world.region.RegionMap;
import dev.underdeep.world.region.RegionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Large natural landmarks (colossal trees, giant mushrooms and fungi, crystal clusters, stone and root
 * arches, fossil skeletons, volcanic cones) built from signed-distance primitives.
 * <p>
 * Landmarks are part of the pure terrain model (not features), so they can be far larger than the
 * vanilla 3x3-chunk feature window while remaining deterministic and chunk-order independent. They only
 * ever turn <em>open</em> space into solid formation blocks (never carve rock).
 * <p>
 * Each layer is divided into landmark cells; each cell may host one landmark at a jittered position
 * inside a cavern chamber tall enough for it. About one in eight landmarks is a "titan" version roughly
 * twice the size (rare spectacular landmarks).
 */
public final class Landmarks {
    /** Formation material codes (mapped to blocks per region by the chunk generator). */
    public static final int PILLAR = 0, TRUNK = 1, LEAVES = 2, LEAVES_GLOW = 3, CORE = 4, SHELL = 5, BONE = 6,
            STEM = 7, CAP = 8, CAP_GLOW = 9, LAVA = 10, ROCK = 11, ROOTS = 12;

    public enum Kind { COLOSSAL_TREE, GIANT_MUSHROOM, CRYSTAL_CLUSTER, ARCH, FOSSIL, VOLCANO }

    /** Distance-field primitive. type 0 = tapered capsule a->b, 1 = ellipsoid at a (b.x = y scale), 2 = torus. */
    record Prim(int type, double ax, double ay, double az, double bx, double by, double bz, double ra, double rb,
                int code, boolean noisy) {
    }

    /** A placed landmark. */
    public static final class Landmark {
        public final Kind kind;
        public final RegionType region;
        public final boolean titan;
        public final double x;
        public final double baseY;
        public final double z;
        final double minX, maxX, minY, maxY, minZ, maxZ;
        final Prim[] prims;

        Landmark(Kind kind, RegionType region, boolean titan, double x, double baseY, double z, List<Prim> prims) {
            this.kind = kind;
            this.region = region;
            this.titan = titan;
            this.x = x;
            this.baseY = baseY;
            this.z = z;
            this.prims = prims.toArray(new Prim[0]);
            double a = Double.MAX_VALUE, b = -Double.MAX_VALUE, c = Double.MAX_VALUE, d = -Double.MAX_VALUE,
                    e = Double.MAX_VALUE, f = -Double.MAX_VALUE;
            for (Prim p : this.prims) {
                double r = Math.max(p.ra, p.rb) + (p.type == 2 ? p.ra + p.rb : 0) + 3;
                double[] xs = p.type == 0 ? new double[]{p.ax, p.bx} : new double[]{p.ax};
                double[] ys = p.type == 0 ? new double[]{p.ay, p.by} : new double[]{p.ay};
                double[] zs = p.type == 0 ? new double[]{p.az, p.bz} : new double[]{p.az};
                double ry = p.type == 1 ? r * Math.max(1, p.bx) : r;
                for (double v : xs) {
                    a = Math.min(a, v - r);
                    b = Math.max(b, v + r);
                }
                for (double v : ys) {
                    c = Math.min(c, v - ry);
                    d = Math.max(d, v + ry);
                }
                for (double v : zs) {
                    e = Math.min(e, v - r);
                    f = Math.max(f, v + r);
                }
            }
            minX = a;
            maxX = b;
            minY = c;
            maxY = d;
            minZ = e;
            maxZ = f;
        }

        public boolean coversColumn(int px, int pz) {
            return px >= minX && px <= maxX && pz >= minZ && pz <= maxZ;
        }

        /** Updates {@code hit} if this landmark's solid is closer (more negative) at (px, py, pz). */
        void sample(double px, double py, double pz, UnderdeepTerrain.FormationHit hit) {
            if (py < minY || py > maxY) {
                return;
            }
            for (Prim p : prims) {
                double d;
                switch (p.type) {
                    case 0 -> {
                        double vx = p.bx - p.ax, vy = p.by - p.ay, vz = p.bz - p.az;
                        double len2 = vx * vx + vy * vy + vz * vz;
                        double t = len2 > 0 ? ((px - p.ax) * vx + (py - p.ay) * vy + (pz - p.az) * vz) / len2 : 0;
                        t = Math.max(0, Math.min(1, t));
                        double cx = p.ax + vx * t - px, cy = p.ay + vy * t - py, cz = p.az + vz * t - pz;
                        d = Math.sqrt(cx * cx + cy * cy + cz * cz) - (p.ra + (p.rb - p.ra) * t);
                    }
                    case 1 -> {
                        double dx = px - p.ax, dy = (py - p.ay) / p.bx, dz = pz - p.az;
                        d = Math.sqrt(dx * dx + dy * dy + dz * dz) - p.ra;
                    }
                    default -> {
                        // torus in the vertical plane whose normal is (bx, 0, bz)
                        double qx = px - p.ax, qy = py - p.ay, qz = pz - p.az;
                        double dn = qx * p.bx + qz * p.bz;
                        double ix = qx - dn * p.bx, iz = qz - dn * p.bz;
                        double inPlane = Math.sqrt(ix * ix + qy * qy + iz * iz);
                        double ring = inPlane - p.ra;
                        d = Math.sqrt(ring * ring + dn * dn) - p.rb;
                    }
                }
                if (p.noisy && d < 3) {
                    // Cheap hash-based jitter makes canopies and caps leafy rather than smooth spheres.
                    long h = Hashing.hash((long) Math.floor(px), (long) Math.floor(py), (long) Math.floor(pz), 0x1EAFL);
                    d += Hashing.signed(h) * 1.6 + 0.4;
                }
                if (p.code == LAVA) {
                    // The crater pool overrides the cone it sits in.
                    if (d < 0) {
                        hit.density = Math.min(hit.density, d);
                        hit.code = LAVA;
                        hit.region = region;
                        hit.locked = true;
                    }
                    continue;
                }
                if (d < hit.density) {
                    hit.density = d;
                    if (!hit.locked) {
                        hit.code = p.code;
                        hit.region = region;
                    }
                }
            }
        }
    }

    private final long seed;
    private final CavernLayer layer;
    private final RegionMap regions;
    private final CavernGraph graph;
    private final int cell;
    private final ThreadLocal<Object[]> cache = ThreadLocal.withInitial(() -> new Object[256]);
    private final java.util.function.DoubleBinaryOperator baseFloor;

    private record Entry(int cx, int cz, Landmark landmark) {
    }

    Landmarks(long worldSeed, CavernLayer layer, RegionMap regions, CavernGraph graph,
              java.util.function.DoubleBinaryOperator baseFloor) {
        this.seed = Hashing.hash(worldSeed, Hashing.salt("landmarks"), layer.ordinal());
        this.layer = layer;
        this.regions = regions;
        this.graph = graph;
        this.cell = layer == CavernLayer.UPPER ? 230 : 320;
        this.baseFloor = baseFloor;
    }

    public int cellSize() {
        return cell;
    }

    /** The landmark of a cell, or null. */
    public Landmark landmark(int cx, int cz) {
        Object[] c = cache.get();
        int slot = (int) (Hashing.mix(((long) cx << 32) ^ (cz & 0xFFFFFFFFL) ^ 0x3A3AL) & 255);
        if (c[slot] instanceof Entry e && e.cx == cx && e.cz == cz) {
            return e.landmark;
        }
        Landmark l = compute(cx, cz);
        c[slot] = new Entry(cx, cz, l);
        return l;
    }

    /** Landmarks whose bounding box covers a column (at most a handful). */
    public void covering(int x, int z, List<Landmark> out) {
        int bx = Math.floorDiv(x, cell);
        int bz = Math.floorDiv(z, cell);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Landmark l = landmark(bx + dx, bz + dz);
                if (l != null && l.coversColumn(x, z)) {
                    out.add(l);
                }
            }
        }
    }

    private Landmark compute(int cx, int cz) {
        long h = Hashing.hash(seed, cx, cz, 1);
        boolean originCell = layer == CavernLayer.UPPER && cx == 0 && cz == 0;
        if (!originCell && Hashing.unit(h) > (layer == CavernLayer.UPPER ? 0.75 : 0.5)) {
            return null;
        }
        double x;
        double z;
        if (originCell) {
            // A colossal tree in view of the starting point, well clear of the spawn itself.
            x = -58;
            z = 42;
        } else {
            x = (cx + 0.25 + 0.5 * Hashing.unit(Hashing.mix(h ^ 2))) * cell;
            z = (cz + 0.25 + 0.5 * Hashing.unit(Hashing.mix(h ^ 3))) * cell;
            if (x * x + z * z < 70 * 70) {
                return null;
            }
        }
        CavernGraph.Openness o = graph.evaluate(x, z, null, new CavernGraph.Openness());
        if (o.height < 32 || o.chamber < 0.12) {
            return null;
        }
        RegionType region = originCell ? RegionType.ROOTWILD : regions.typeAt(x, z);
        boolean titan = !originCell && Hashing.unit(Hashing.mix(h ^ 4)) < 0.13;
        double scale = titan ? 1.8 : 1.0;
        double maxH = Math.min(o.height * 0.82, titan ? 150 : 90);
        double base = baseFloor.applyAsDouble(x, z);
        long r = Hashing.mix(h ^ 5);
        Kind kind = chooseKind(region, Hashing.unit(r));
        if (originCell) {
            kind = Kind.COLOSSAL_TREE;
        }
        List<Prim> prims = new ArrayList<>();
        switch (kind) {
            case COLOSSAL_TREE -> tree(prims, region, x, base, z, maxH, scale, r);
            case GIANT_MUSHROOM -> mushroom(prims, x, base, z, maxH, scale, r);
            case CRYSTAL_CLUSTER -> crystals(prims, x, base, z, maxH, scale, r);
            case ARCH -> arch(prims, region, x, base, z, maxH, scale, r);
            case FOSSIL -> fossil(prims, x, base, z, scale, r);
            case VOLCANO -> volcano(prims, x, base, z, maxH, scale, r);
        }
        if (prims.isEmpty()) {
            return null;
        }
        return new Landmark(kind, region, titan, x, base, z, prims);
    }

    static Kind chooseKind(RegionType region, double u) {
        return switch (region) {
            case ROOTWILD, GOLDEN_GROVE, EMERALD_WETLANDS -> u < 0.72 ? Kind.COLOSSAL_TREE : Kind.ARCH;
            case FUNGAL_EXPANSE -> Kind.GIANT_MUSHROOM;
            case CRIMSON_BASIN -> u < 0.65 ? Kind.GIANT_MUSHROOM : Kind.VOLCANO;
            case PETRIFIED_FOREST -> u < 0.5 ? Kind.COLOSSAL_TREE : Kind.FOSSIL;
            case EVERFROST, CRYSTAL_DEEP, STORMSTONE_CAVERNS, SUNKEN_GARDENS -> u < 0.65 ? Kind.CRYSTAL_CLUSTER : Kind.ARCH;
            case STARLIGHT_CAVERNS -> u < 0.5 ? Kind.CRYSTAL_CLUSTER : Kind.ARCH;
            case FLOODED_DEPTHS -> u < 0.6 ? Kind.ARCH : Kind.FOSSIL;
            case HOLLOW_PEAKS -> u < 0.75 ? Kind.ARCH : Kind.FOSSIL;
            case ASHEN_HOLLOWS -> u < 0.6 ? Kind.VOLCANO : Kind.FOSSIL;
            case THE_ABYSS -> u < 0.5 ? Kind.FOSSIL : Kind.ARCH;
        };
    }

    private static double u(long h, int salt) {
        return Hashing.unit(Hashing.hash(h, salt));
    }

    private static void tree(List<Prim> p, RegionType region, double x, double base, double z, double maxH, double s, long h) {
        boolean petrified = region == RegionType.PETRIFIED_FOREST;
        double height = Math.max(30, maxH * (0.65 + 0.3 * u(h, 1)));
        double r = (4.5 + 2.5 * u(h, 2)) * s;
        double topY = base + height;
        // Trunk (buried 20 blocks so it always meets the ground), slightly leaning.
        double lean = (u(h, 3) - 0.5) * 0.25;
        double ang0 = u(h, 4) * Math.PI * 2;
        double tx = x + Math.cos(ang0) * lean * height;
        double tz = z + Math.sin(ang0) * lean * height;
        p.add(new Prim(0, x, base - 20, z, tx, topY, tz, r * 1.15, r * 0.55, TRUNK, false));
        // Flaring buttress roots.
        int roots = 4 + (int) (u(h, 5) * 3);
        for (int i = 0; i < roots; i++) {
            double a = ang0 + i * Math.PI * 2 / roots + u(h, 10 + i) * 0.6;
            double len = r * (2.2 + u(h, 20 + i) * 1.6);
            p.add(new Prim(0, x, base + r * 1.3, z, x + Math.cos(a) * len, base - 3, z + Math.sin(a) * len,
                    r * 0.45, r * 0.18, petrified ? TRUNK : ROOTS, false));
        }
        // Branches with canopy blobs.
        int branches = 3 + (int) (u(h, 6) * 3);
        for (int i = 0; i < branches; i++) {
            double t = 0.55 + 0.35 * u(h, 30 + i);
            double sx = x + (tx - x) * t;
            double sz = z + (tz - z) * t;
            double sy = base + height * t;
            double a = ang0 + i * Math.PI * 2 / branches + u(h, 40 + i);
            double len = (12 + 12 * u(h, 50 + i)) * s;
            double ex = sx + Math.cos(a) * len;
            double ez = sz + Math.sin(a) * len;
            double ey = Math.min(sy + len * 0.45, topY + 4);
            p.add(new Prim(0, sx, sy, sz, ex, ey, ez, r * 0.42, r * 0.2, TRUNK, false));
            if (!petrified) {
                double cr = (7 + 5 * u(h, 60 + i)) * s;
                p.add(new Prim(1, ex, ey + 1, ez, 0.6, 0, 0, cr, 0, LEAVES, true));
            }
        }
        if (!petrified) {
            p.add(new Prim(1, tx, topY - 2, tz, 0.55, 0, 0, (10 + 5 * u(h, 7)) * s, 0, LEAVES, true));
        }
    }

    private static void mushroom(List<Prim> p, double x, double base, double z, double maxH, double s, long h) {
        double height = Math.max(24, maxH * (0.5 + 0.3 * u(h, 1)));
        double r = (3 + 2 * u(h, 2)) * s;
        double topY = base + height;
        double bend = (u(h, 3) - 0.5) * 0.3 * height;
        double a = u(h, 4) * Math.PI * 2;
        double tx = x + Math.cos(a) * bend;
        double tz = z + Math.sin(a) * bend;
        p.add(new Prim(0, x, base - 15, z, tx, topY, tz, r * 1.3, r * 0.9, STEM, false));
        double capR = (13 + 8 * u(h, 5)) * s;
        // Cap: flattened ellipsoid with a glowing underside.
        p.add(new Prim(1, tx, topY + 2, tz, 0.38, 0, 0, capR, 0, CAP, false));
        p.add(new Prim(1, tx, topY - 1, tz, 0.22, 0, 0, capR * 0.92, 0, CAP_GLOW, false));
        // Satellite mushrooms
        int n = (int) (u(h, 6) * 3);
        for (int i = 0; i < n; i++) {
            double aa = a + 1.5 + i * 2.1;
            double dist = capR * (1.0 + 0.4 * u(h, 10 + i));
            double sx = x + Math.cos(aa) * dist;
            double sz = z + Math.sin(aa) * dist;
            double sh = height * (0.3 + 0.2 * u(h, 20 + i));
            p.add(new Prim(0, sx, base - 10, sz, sx, base + sh, sz, r * 0.6, r * 0.45, STEM, false));
            p.add(new Prim(1, sx, base + sh + 1, sz, 0.4, 0, 0, capR * 0.4, 0, CAP, false));
            p.add(new Prim(1, sx, base + sh - 1, sz, 0.25, 0, 0, capR * 0.36, 0, CAP_GLOW, false));
        }
    }

    private static void crystals(List<Prim> p, double x, double base, double z, double maxH, double s, long h) {
        int n = 4 + (int) (u(h, 1) * 4);
        double mainLen = Math.min(maxH * 0.9, (32 + 26 * u(h, 2)) * s);
        for (int i = 0; i < n; i++) {
            double a = u(h, 10 + i) * Math.PI * 2;
            double tilt = i == 0 ? 0.08 : 0.25 + 0.45 * u(h, 20 + i);
            double len = i == 0 ? mainLen : mainLen * (0.35 + 0.45 * u(h, 30 + i));
            double r = (i == 0 ? 5.5 : 2.5 + 2.5 * u(h, 40 + i)) * s;
            double sx = x + Math.cos(a) * (i == 0 ? 0 : r);
            double sz = z + Math.sin(a) * (i == 0 ? 0 : r);
            double ex = sx + Math.cos(a) * Math.sin(tilt) * len;
            double ez = sz + Math.sin(a) * Math.sin(tilt) * len;
            double ey = base + Math.cos(tilt) * len;
            // Alternate glowing and plain shards so the cluster both shines and has contrast.
            p.add(new Prim(0, sx, base - 8, sz, ex, ey, ez, r, 0.6, i % 2 == 0 ? CORE : SHELL, false));
        }
    }

    private static void arch(List<Prim> p, RegionType region, double x, double base, double z, double maxH, double s, long h) {
        double radius = Math.min(maxH * 0.75, (22 + 18 * u(h, 1)) * s);
        double thick = (3.5 + 3 * u(h, 2)) * Math.sqrt(s);
        double a = u(h, 3) * Math.PI;
        int code = switch (region) {
            case ROOTWILD, GOLDEN_GROVE, EMERALD_WETLANDS -> ROOTS;
            default -> ROCK;
        };
        p.add(new Prim(2, x, base - thick * 0.5, z, Math.cos(a), 0, Math.sin(a), radius, thick, code, false));
        if (u(h, 4) < 0.4) {
            // A second, crossing arch makes a natural "gateway".
            double a2 = a + Math.PI / 2 + (u(h, 5) - 0.5) * 0.6;
            p.add(new Prim(2, x, base - thick * 0.5, z, Math.cos(a2), 0, Math.sin(a2), radius * 0.75, thick * 0.8, code, false));
        }
    }

    private static void fossil(List<Prim> p, double x, double base, double z, double s, long h) {
        double len = (34 + 22 * u(h, 1)) * s;
        double a = u(h, 2) * Math.PI * 2;
        double dx = Math.cos(a);
        double dz = Math.sin(a);
        double y0 = base + 2;
        int segs = 6;
        double px = x - dx * len / 2;
        double pz = z - dz * len / 2;
        double py = y0;
        double curve = (u(h, 3) - 0.5) * 0.6;
        for (int i = 0; i < segs; i++) {
            double ang = a + curve * i / segs;
            double nx = px + Math.cos(ang) * len / segs;
            double nz = pz + Math.sin(ang) * len / segs;
            double ny = y0 + Math.sin(i * Math.PI / segs) * 6 * s;
            p.add(new Prim(0, px, py, pz, nx, ny, nz, 1.6 * s, 1.4 * s, BONE, false));
            if (i > 0 && i < segs - 1) {
                // ribs: torus arcs perpendicular to the spine, half buried
                p.add(new Prim(2, nx, ny - 3 * s, nz, Math.cos(ang), 0, Math.sin(ang), (6 + 2 * Math.sin(i)) * s, 0.9 * s, BONE, false));
            }
            px = nx;
            py = ny;
            pz = nz;
        }
        // skull
        p.add(new Prim(1, px + Math.cos(a) * 4 * s, py + 1, pz + Math.sin(a) * 4 * s, 0.7, 0, 0, 4.5 * s, 0, BONE, false));
    }

    private static void volcano(List<Prim> p, double x, double base, double z, double maxH, double s, long h) {
        double height = Math.min(maxH * 0.6, (24 + 16 * u(h, 1)) * s);
        double r = (20 + 10 * u(h, 2)) * s;
        double top = base + height;
        double topR = r * 0.32;
        p.add(new Prim(0, x, base - 10, z, x, top, z, r, topR, ROCK, false));
        // Crater lava pool sitting just below the rim; overflowing edges become lava cascades.
        p.add(new Prim(1, x, top - topR * 0.45, z, 0.5, 0, 0, topR * 0.62, 0, LAVA, false));
    }

    /** The cell size, for diagnostics. */
    public List<Landmark> landmarksIn(int minX, int minZ, int maxX, int maxZ) {
        List<Landmark> list = new ArrayList<>();
        for (int cx = Math.floorDiv(minX, cell); cx <= Math.floorDiv(maxX, cell); cx++) {
            for (int cz = Math.floorDiv(minZ, cell); cz <= Math.floorDiv(maxZ, cell); cz++) {
                Landmark l = landmark(cx, cz);
                if (l != null) {
                    list.add(l);
                }
            }
        }
        return list;
    }
}
