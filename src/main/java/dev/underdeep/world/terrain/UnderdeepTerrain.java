package dev.underdeep.world.terrain;

import dev.underdeep.world.noise.FractalNoise;
import dev.underdeep.world.noise.Hashing;
import dev.underdeep.world.region.RegionMap;
import dev.underdeep.world.region.RegionSample;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.region.TerrainProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * The complete, deterministic terrain model of an Underdeep world.
 * <p>
 * The terrain is a pure function of (world seed, x, y, z). It is evaluated in two parts:
 * <ol>
 *     <li>{@link #column(int, int)} computes all 2D values of a block column (layer floors and
 *     ceilings, rivers, ponds, shafts, pillars, bridges, region blend);</li>
 *     <li>{@link #density} combines those with 3D noise ({@link #roughness}, {@link #tunnels}) to decide
 *     whether a block is open (&gt; 0) or solid (&lt;= 0), and {@link #classify} turns that into a
 *     {@link Material}.</li>
 * </ol>
 * Because nothing depends on previously generated chunks, results are identical regardless of chunk
 * generation order, and the same model can be queried for unloaded areas by analysis tools and future
 * simulation systems without generating chunks.
 * <p>
 * Thread-safe: all state is immutable; caches are thread-local.
 */
public final class UnderdeepTerrain {
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;
    /** Exclusive top. */
    public static final int TOP_Y = MIN_Y + HEIGHT;
    public static final int ROOF_BEDROCK = 315;
    public static final int SEA_LEVEL = CavernLayer.UPPER.fluidLevel;
    /** Bumped whenever terrain output changes incompatibly. Stored in world data. */
    public static final int GENERATOR_VERSION = 5;
    /** First generator version with the Stage 10 cave network (secondary passages, descents, spiral shafts). */
    public static final int CAVE_NETWORK_VERSION = 5;
    /**
     * Cave network settings applied to terrain models created afterwards (set from config {@code caves} at start-up;
     * changing them changes future terrain, so they are for fresh worlds). Defaults are the designed values.
     */
    public static volatile double passageFrequency = 1.0;
    public static volatile double descentFrequency = 1.0;
    public static final double SHAFT_SPIRAL_PITCH = 22.0;

    /** Persistence note: append-only. */
    public enum Material { AIR, STONE, WATER, LAVA, BEDROCK, ICE, FORMATION }

    /** Result of evaluating formations (pillars, bridges, landmarks) at a block. */
    public static final class FormationHit {
        /** Signed distance to the nearest formation solid (&lt;= 0 inside). */
        public double density = Double.MAX_VALUE;
        /** {@link Landmarks} material code of that solid. */
        public int code;
        /** Region whose palette the formation uses. */
        public RegionType region;
        boolean locked;

        public FormationHit reset() {
            density = Double.MAX_VALUE;
            code = Landmarks.PILLAR;
            region = null;
            locked = false;
            return this;
        }
    }

    private final long seed;
    private final RegionMap upperRegions;
    private final RegionMap deepRegions;
    private final CavernGraph upperGraph;
    private final CavernGraph deepGraph;
    private final Landmarks upperLandmarks;
    private final Landmarks deepLandmarks;
    private final FractalNoise variantNoise;

    private final FractalNoise upperFloorNoise;
    private final FractalNoise deepFloorNoise;
    private final FractalNoise upperHill;
    private final FractalNoise deepHill;
    private final FractalNoise hillBoost;
    private final FractalNoise openFieldU;
    private final FractalNoise openFieldD;
    private final FractalNoise ceilU;
    private final FractalNoise ceilD;
    private final FractalNoise riverNoise;
    private final FractalNoise riverMask;
    private final FractalNoise ravineNoise;
    private final FractalNoise ravineMask;
    private final FractalNoise ravineDepth;
    private final FractalNoise pitNoise;
    private final FractalNoise bridgeNoiseU;
    private final FractalNoise bridgeNoiseD;
    private final FractalNoise bridgeMask;
    private final FractalNoise bridgeHeight;
    private final FractalNoise ceilRidge;
    private final FractalNoise strata;
    private final FractalNoise patchA;
    private final FractalNoise patchB;
    private final FractalNoise rough1;
    private final FractalNoise rough2;
    private final FractalNoise spagA;
    private final FractalNoise spagB;
    private final FractalNoise spagMask;

    private final ThreadLocal<TerrainColumn[]> columnCache = ThreadLocal.withInitial(() -> new TerrainColumn[512]);
    private final ThreadLocal<Object[]> shaftCache = ThreadLocal.withInitial(() -> new Object[256]);
    private final ThreadLocal<CavernGraph.Openness> openScratch = ThreadLocal.withInitial(CavernGraph.Openness::new);
    private final ThreadLocal<RegionSample> pondRegionScratch = ThreadLocal.withInitial(RegionSample::new);
    private final ThreadLocal<TerrainProfile> pondProfileScratch = ThreadLocal.withInitial(TerrainProfile::new);
    private final ThreadLocal<List<Landmarks.Landmark>> landmarkScratch = ThreadLocal.withInitial(ArrayList::new);
    private final ThreadLocal<List<CavernGraph.Shaft>> shaftScratch = ThreadLocal.withInitial(ArrayList::new);

    private final int version;
    @org.jetbrains.annotations.Nullable
    private final Descents descents;

    public UnderdeepTerrain(long worldSeed) {
        this(worldSeed, GENERATOR_VERSION);
    }

    /**
     * Terrain of a world generated with the given generator version. Worlds created before the Stage 10 cave network
     * keep producing exactly their original terrain, so newly generated chunks of old saves have no seams.
     */
    public UnderdeepTerrain(long worldSeed, int version) {
        this.seed = worldSeed;
        this.version = version;
        boolean caves = version >= CAVE_NETWORK_VERSION;
        this.upperRegions = new RegionMap(worldSeed, CavernLayer.UPPER);
        this.deepRegions = new RegionMap(worldSeed, CavernLayer.DEEP);
        this.upperGraph = new CavernGraph(worldSeed, CavernLayer.UPPER, upperRegions, caves ? passageFrequency : 0);
        this.deepGraph = new CavernGraph(worldSeed, CavernLayer.DEEP, deepRegions, caves ? passageFrequency : 0);
        this.descents = caves && descentFrequency > 0 ? new Descents(this, worldSeed, descentFrequency) : null;
        long s = Hashing.hash(worldSeed, Hashing.salt("terrain"));
        variantNoise = new FractalNoise(Hashing.hash(s, 40), 2, 1 / 120.0);
        upperFloorNoise = new FractalNoise(Hashing.hash(s, 1), 3, 1 / 520.0);
        deepFloorNoise = new FractalNoise(Hashing.hash(s, 2), 3, 1 / 600.0);
        upperHill = new FractalNoise(Hashing.hash(s, 3), 3, 1.0);
        deepHill = new FractalNoise(Hashing.hash(s, 4), 3, 1.0);
        hillBoost = new FractalNoise(Hashing.hash(s, 5), 2, 1 / 300.0);
        openFieldU = new FractalNoise(Hashing.hash(s, 6), 2, 1 / 150.0);
        openFieldD = new FractalNoise(Hashing.hash(s, 7), 2, 1 / 170.0);
        ceilU = new FractalNoise(Hashing.hash(s, 8), 3, 1 / 90.0);
        ceilD = new FractalNoise(Hashing.hash(s, 9), 3, 1 / 90.0);
        riverNoise = new FractalNoise(Hashing.hash(s, 10), 2, 1 / 420.0);
        riverMask = new FractalNoise(Hashing.hash(s, 11), 2, 1 / 900.0);
        ravineNoise = new FractalNoise(Hashing.hash(s, 12), 2, 1 / 520.0);
        ravineMask = new FractalNoise(Hashing.hash(s, 13), 2, 1 / 700.0);
        ravineDepth = new FractalNoise(Hashing.hash(s, 14), 2, 1 / 200.0);
        pitNoise = new FractalNoise(Hashing.hash(s, 15), 3, 1 / 220.0);
        bridgeNoiseU = new FractalNoise(Hashing.hash(s, 16), 2, 1 / 150.0);
        bridgeNoiseD = new FractalNoise(Hashing.hash(s, 17), 2, 1 / 170.0);
        bridgeMask = new FractalNoise(Hashing.hash(s, 18), 2, 1 / 320.0);
        bridgeHeight = new FractalNoise(Hashing.hash(s, 19), 2, 1 / 200.0);
        ceilRidge = new FractalNoise(Hashing.hash(s, 25), 2, 1 / 110.0);
        strata = new FractalNoise(Hashing.hash(s, 26), 2, 1 / 90.0);
        patchA = new FractalNoise(Hashing.hash(s, 27), 2, 1 / 22.0);
        patchB = new FractalNoise(Hashing.hash(s, 28), 2, 1 / 30.0);
        rough1 = new FractalNoise(Hashing.hash(s, 20), 2, 1 / 28.0);
        rough2 = new FractalNoise(Hashing.hash(s, 21), 2, 1 / 9.0);
        spagA = new FractalNoise(Hashing.hash(s, 22), 2, 1 / 90.0);
        spagB = new FractalNoise(Hashing.hash(s, 23), 2, 1 / 90.0);
        spagMask = new FractalNoise(Hashing.hash(s, 24), 1, 1 / 260.0);
        upperLandmarks = new Landmarks(worldSeed, CavernLayer.UPPER, upperRegions, upperGraph, (x, z) -> {
            RegionSample rs = new RegionSample();
            TerrainProfile p = new TerrainProfile();
            upperRegions.sample(x, z, rs).blendProfile(p);
            return upperBaseFloor(x, z, p);
        });
        deepLandmarks = new Landmarks(worldSeed, CavernLayer.DEEP, deepRegions, deepGraph, (x, z) -> {
            RegionSample rs = new RegionSample();
            TerrainProfile p = new TerrainProfile();
            deepRegions.sample(x, z, rs).blendProfile(p);
            return deepBaseFloor(x, z, p);
        });
    }

    public int version() {
        return version;
    }

    /** Whether this terrain has the Stage 10 cave network. */
    public boolean hasCaveNetwork() {
        return version >= CAVE_NETWORK_VERSION;
    }

    /** A descent cave: helix centre and radius, upper mouth and deep mouth (block positions), extent. */
    public record DescentInfo(double centerX, double centerZ, double radius, int topX, int topY, int topZ,
                              int bottomX, int bottomY, int bottomZ, double minX, double minZ, double maxX, double maxZ) {
    }

    /** Descent caves whose extent intersects a block rectangle (pure; for diagnostics and structure protection). */
    public List<DescentInfo> descentsIn(int minX, int minZ, int maxX, int maxZ) {
        List<DescentInfo> out = new ArrayList<>();
        if (descents == null) {
            return out;
        }
        List<Descents.Descent> l = new ArrayList<>();
        descents.collect(minX, minZ, maxX, maxZ, l);
        for (Descents.Descent d : l) {
            out.add(new DescentInfo(d.centerX, d.centerZ, d.radius, d.topX, d.topY, d.topZ, d.bottomX, d.bottomY, d.bottomZ,
                    d.minX, d.minZ, d.maxX, d.maxZ));
        }
        return out;
    }

    /** The column without descent bands (used to place descents themselves; not cached). */
    TerrainColumn baseColumn(int x, int z) {
        return computeColumn(x, z, false);
    }

    public long seed() {
        return seed;
    }

    public RegionMap regions(CavernLayer layer) {
        return layer == CavernLayer.UPPER ? upperRegions : deepRegions;
    }

    public CavernGraph graph(CavernLayer layer) {
        return layer == CavernLayer.UPPER ? upperGraph : deepGraph;
    }

    public Landmarks landmarks(CavernLayer layer) {
        return layer == CavernLayer.UPPER ? upperLandmarks : deepLandmarks;
    }

    /** Sub-regional variant (0..3) at a position; see {@link TerrainColumn#variant}. */
    public int variantAt(double x, double z) {
        double v = variantNoise.sample(x, z);
        return v < -0.32 ? 0 : v < 0.05 ? 1 : v < 0.38 ? 2 : 3;
    }

    private static double variantHillScale(int variant) {
        return switch (variant) {
            case 0 -> 0.35;
            case 2 -> 1.5;
            case 3 -> 0.8;
            default -> 1.0;
        };
    }

    // ------------------------------------------------------------------------------------------
    // Math helpers

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static double smoothstep(double e0, double e1, double x) {
        double t = clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static double smoothstepPublic(double e0, double e1, double x) {
        return smoothstep(e0, e1, x);
    }

    /** Smooth minimum (polynomial) with blend radius k. */
    static double softMin(double a, double b, double k) {
        double h = clamp(0.5 + 0.5 * (b - a) / k, 0, 1);
        return lerp(b, a, h) - k * h * (1 - h);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ------------------------------------------------------------------------------------------
    // Columns

    /** Returns the (cached) terrain column at a block position. */
    public TerrainColumn column(int x, int z) {
        TerrainColumn[] cache = columnCache.get();
        int slot = (int) (Hashing.mix(((long) x << 32) ^ (z & 0xFFFFFFFFL)) & 511);
        TerrainColumn c = cache[slot];
        if (c != null && c.x == x && c.z == z) {
            return c;
        }
        c = computeColumn(x, z);
        cache[slot] = c;
        return c;
    }

    private double hills(FractalNoise noise, double x, double z, TerrainProfile p, double openHeight) {
        double scale = clamp(Math.sqrt(Math.max(openHeight, 1) / 55.0), 0.5, 1.9);
        if (p.hillAmp <= 0.01) {
            return 0;
        }
        double f = p.hillFreq;
        double n = noise.sample(x * f, z * f);
        double rounded = 0.5 + 0.5 * n;
        double ridge = 1 - Math.abs(n);
        ridge *= ridge;
        double s = lerp(rounded, ridge, clamp(p.ridged, 0, 1));
        double boost = 0.7 + 0.9 * Math.max(0, hillBoost.sample(x, z));
        double h = p.hillAmp * s * boost * scale;
        if (p.terrace > 1.5) {
            double step = p.terrace;
            double k = Math.floor(h / step);
            double frac = h / step - k;
            h = (k + smoothstep(0.7, 1.0, frac)) * step;
        }
        return h;
    }

    /** Returns {stalactite length, stalagmite length} at a column. */
    private void spikes(long salt, double x, double z, double gap, double amount, double[] out) {
        out[0] = 0;
        out[1] = 0;
        if (amount <= 0.01 || gap < 16) {
            return;
        }
        final int sp = 15;
        int bx = Math.floorDiv((int) Math.floor(x), sp);
        int bz = Math.floorDiv((int) Math.floor(z), sp);
        double gapScale = Math.min(gap, 120);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = bx + dx;
                int cz = bz + dz;
                long h = Hashing.hash(seed ^ salt, cx, cz, 91);
                double u = Hashing.unit(h);
                if (u > amount * 0.16) {
                    continue;
                }
                double px = (cx + 0.2 + 0.6 * Hashing.unit(Hashing.mix(h ^ 1))) * sp;
                double pz = (cz + 0.2 + 0.6 * Hashing.unit(Hashing.mix(h ^ 2))) * sp;
                double v = Hashing.unit(Hashing.mix(h ^ 3));
                double len = Math.min(34, (0.07 + 0.22 * v * v) * gapScale);
                double rs = 2.6 + len * 0.26;
                double d = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
                if (d < rs) {
                    double k = Math.pow(1 - d / rs, 1.6) * len;
                    boolean down = Hashing.unit(Hashing.mix(h ^ 4)) < 0.7;
                    if (down) {
                        out[0] = Math.max(out[0], k);
                    } else {
                        out[1] = Math.max(out[1], k * 0.6);
                    }
                }
            }
        }
    }

    /** Hanging ridges and vaults on tall ceilings. */
    private double ceilingRelief(double x, double z, double open) {
        if (open < 40) {
            return 0;
        }
        double n = 1 - Math.abs(ceilRidge.sample(x, z));
        return n * n * n * open * 0.28 * smoothstep(40, 80, open);
    }

    /** Shaft owned by a deep graph cell, or null. */
    public CavernGraph.Shaft shaft(int dcx, int dcz) {
        Object[] cache = shaftCache.get();
        int slot = (int) (Hashing.mix(((long) dcx << 32) ^ (dcz & 0xFFFFFFFFL) ^ 0x77L) & 255);
        Object o = cache[slot];
        if (o instanceof ShaftEntry e && e.cx == dcx && e.cz == dcz) {
            return e.shaft;
        }
        CavernGraph.Shaft sh = computeShaft(dcx, dcz);
        cache[slot] = new ShaftEntry(dcx, dcz, sh);
        return sh;
    }

    private record ShaftEntry(int cx, int cz, CavernGraph.Shaft shaft) {
    }

    private CavernGraph.Shaft computeShaft(int dcx, int dcz) {
        CavernGraph.Node n = deepGraph.node(dcx, dcz);
        RegionType upper = upperRegions.typeAt(n.x(), n.z());
        long h = Hashing.hash(seed, dcx, dcz, Hashing.salt("shaft"));
        if (Hashing.unit(h) >= upper.profile().shafts * 3.2) {
            return null;
        }
        // v5+: wider shafts so every spiral ledge is a walkable gradient
        double radius = version >= CAVE_NETWORK_VERSION ? 8 + 5 * Hashing.unit(Hashing.mix(h)) : 5 + 6 * Hashing.unit(Hashing.mix(h));
        CavernGraph.Node top = upperGraph.nearestNode(n.x(), n.z());
        return new CavernGraph.Shaft(n.x(), n.z(), radius, top);
    }

    private List<CavernGraph.Shaft> nearbyShafts(double x, double z, int range) {
        List<CavernGraph.Shaft> list = shaftScratch.get();
        list.clear();
        int cell = deepGraph.cellSize();
        int bx = Math.floorDiv((int) Math.floor(x), cell);
        int bz = Math.floorDiv((int) Math.floor(z), cell);
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                CavernGraph.Shaft s = shaft(bx + dx, bz + dz);
                if (s != null) {
                    list.add(s);
                }
            }
        }
        return list;
    }

    private double upperBaseFloor(double x, double z, TerrainProfile up) {
        return CavernLayer.UPPER.floorBase + upperFloorNoise.sample(x, z) * CavernLayer.UPPER.floorAmp * up.floorAmp
                + up.floorOffset;
    }

    private double deepBaseFloor(double x, double z, TerrainProfile dp) {
        return CavernLayer.DEEP.floorBase + deepFloorNoise.sample(x, z) * CavernLayer.DEEP.floorAmp * dp.floorAmp
                + dp.floorOffset;
    }

    private TerrainColumn computeColumn(int x, int z) {
        return computeColumn(x, z, true);
    }

    private TerrainColumn computeColumn(int x, int z, boolean withDescents) {
        TerrainColumn c = new TerrainColumn();
        c.x = x;
        c.z = z;
        upperRegions.sample(x, z, c.upperRegion);
        deepRegions.sample(x, z, c.deepRegion);
        c.upperRegion.blendProfile(c.upperProfile);
        c.deepRegion.blendProfile(c.deepProfile);
        TerrainProfile up = c.upperProfile;
        TerrainProfile dp = c.deepProfile;
        c.upperLava = c.upperRegion.primary.lavaFluid();
        c.deepLava = c.deepRegion.primary.lavaFluid();
        double[] spikes = new double[2];
        final double wl = SEA_LEVEL;
        c.variant = variantAt(x, z);
        double variantHills = variantHillScale(c.variant);

        // ---------------------------------------------------------------- upper layer
        double f0u = upperBaseFloor(x, z, up);
        c.upperBaseFloor = f0u;
        CavernGraph.Openness ou = upperGraph.evaluate(x, z, nearbyShafts(x, z, 2), openScratch.get());
        double hu = ou.height;
        double chamberU = ou.chamber;
        {
            double of = openFieldU.sample(x, z);
            double thr = 0.36 - up.openField * 0.5;
            double v = clamp((of - thr) / 0.28, 0, 1);
            double gate = clamp((1.9 - ou.nearestChamber) / 0.6, 0, 1);
            hu = Math.max(hu, v * gate * (24 + 30 * up.chamberHeight));
            chamberU = Math.max(chamberU, v * gate * 0.8);
        }
        double hillU = hills(upperHill, x, z, up, hu) * smoothstep(0.02, 0.5, chamberU) * variantHills;
        if (up.basin > 0.5) {
            // Bowl-shaped basins: floors sink towards chamber centres.
            hillU -= up.basin * Math.pow(chamberU, 0.7);
        }
        double distOrigin = Math.sqrt((double) x * x + (double) z * z);
        if (distOrigin < 170) {
            hillU *= 0.3 + 0.7 * smoothstep(45, 170, distOrigin);
        }
        spikes(0x51, x, z, hu, up.spikes, spikes);
        double fu = f0u + hillU + spikes[1];
        double cu = f0u + hu + ceilU.sample(x, z) * (3 + hu * 0.12) - spikes[0] - ceilingRelief(x, z, hu);
        if (hu < 1.5) {
            cu = f0u - 6;
        }

        // rivers: valleys and channels in the lowlands, carving their own tunnels through rock
        if (up.rivers > 0.01) {
            double rn = Math.abs(riverNoise.sample(x, z));
            double lowland = smoothstep(wl + 34, wl + 14, f0u);
            double strength = up.rivers * lowland * smoothstep(-0.2, 0.05, riverMask.sample(x, z));
            if (strength > 0) {
                double valley = smoothstep(0.085, 0.03, rn) * strength;
                double channel = smoothstep(0.034, 0.012, rn) * strength;
                if (valley > 0) {
                    double target = wl + 1.5;
                    if (fu > target) {
                        fu = lerp(fu, target, valley);
                    }
                    if (channel > 0) {
                        fu = Math.min(fu, lerp(fu, wl - 2 - 3 * channel, Math.min(1, channel * 1.6)));
                    }
                    if (valley > 0.25) {
                        double top = wl + 3 + 10 * valley;
                        if (cu < top) {
                            cu = top;
                            hu = Math.max(hu, top - fu);
                        }
                    }
                    c.river = channel;
                }
            }
        }

        // ravines: deep cracks in dry cavern floors
        if (up.ravines > 0.01 && hu > 6 && fu > wl + 10) {
            double rv = Math.abs(ravineNoise.sample(x, z));
            double mask = ravineMask.sample(x, z);
            double w = 0.016;
            if (rv < w && mask > 0.42 - 0.32 * up.ravines) {
                double t = 1 - rv / w;
                double depth = (22 + 40 * (0.5 + 0.5 * ravineDepth.sample(x, z))) * Math.sqrt(t)
                        * smoothstep(wl + 10, wl + 22, fu) * smoothstep(0.42 - 0.32 * up.ravines, 0.5 - 0.32 * up.ravines, mask);
                fu = Math.max(wl + 3, fu - depth);
            }
        }

        // ---------------------------------------------------------------- deep layer
        double f0d = deepBaseFloor(x, z, dp);
        c.deepBaseFloor = f0d;
        CavernGraph.Openness od = deepGraph.evaluate(x, z, null, openScratch.get());
        double hd = od.height;
        double chamberD = od.chamber;
        {
            double of = openFieldD.sample(x, z);
            double thr = 0.36 - dp.openField * 0.5;
            double v = clamp((of - thr) / 0.28, 0, 1);
            double gate = clamp((1.9 - od.nearestChamber) / 0.6, 0, 1);
            hd = Math.max(hd, v * gate * (26 + 32 * dp.chamberHeight));
            chamberD = Math.max(chamberD, v * gate * 0.8);
        }
        double hillD = hills(deepHill, x, z, dp, hd) * smoothstep(0.02, 0.5, chamberD) * variantHills;
        spikes(0x52, x, z, hd, dp.spikes, spikes);
        double fd = f0d + hillD + spikes[1];
        double cd = f0d + hd + ceilD.sample(x, z) * (3 + hd * 0.12) - spikes[0] - ceilingRelief(x + 7000, z, hd);
        if (hd < 1.5) {
            cd = f0d - 6;
        }
        if (dp.pits > 0.01) {
            double p = pitNoise.sample(x, z);
            double lo = 0.40 - dp.pits * 0.3;
            double k = smoothstep(lo, lo + 0.07, p) * smoothstep(0.05, 0.4, chamberD) * Math.min(1, dp.pits * 1.6);
            if (k > 0) {
                fd = lerp(fd, CavernLayer.DEEP.fluidLevel - 12, k);
            }
        }

        // ---------------------------------------------------------------- ponds
        c.pondLevel = Double.NaN;
        fu = applyPonds(c, upperGraph, CavernLayer.UPPER, x, z, fu, cu, wl);
        if (Double.isNaN(c.pondLevel)) {
            fd = applyPonds(c, deepGraph, CavernLayer.DEEP, x, z, fd, cd, CavernLayer.DEEP.fluidLevel);
        }

        // ---------------------------------------------------------------- separation & caps
        c.separation = fu - 8;
        cu = Math.min(softMin(cu, CavernLayer.UPPER.maxCeiling - 6, 18), CavernLayer.UPPER.maxCeiling);
        // Soft cap keeps the crust between layers intact without flattening deep ceilings into a plane.
        cd = softMin(cd, Math.min(c.separation - 6, CavernLayer.DEEP.maxCeiling), 14);
        cd = Math.min(cd, c.separation - 6);

        c.upperFloor = fu;
        c.upperCeiling = cu;
        c.deepFloor = fd;
        c.deepCeiling = cd;
        c.upperChamber = chamberU;
        c.deepChamber = chamberD;
        c.upperOpenHeight = hu;
        c.deepOpenHeight = hd;

        // ---------------------------------------------------------------- shafts
        for (CavernGraph.Shaft sh : nearbyShafts(x, z, 1)) {
            double d = Math.sqrt((x - sh.x()) * (x - sh.x()) + (z - sh.z()) * (z - sh.z()));
            if (d < sh.radius() + 1 && d < c.shaftDist && fu > wl + 10) {
                c.shaftDist = d;
                c.shaftRadius = sh.radius();
                c.shaftBottom = fd + 1;
                c.shaftTop = fu + 3;
                c.shaftAngle = Math.atan2(z - sh.z(), x - sh.x());
                c.shaftSpiral = version >= CAVE_NETWORK_VERSION || Hashing.unit(Hashing.hash(seed, (long) sh.x(), (long) sh.z(), 5)) < 0.65;
            }
        }

        // ---------------------------------------------------------------- pillars & bridges
        pillars(c, true, x, z, up, chamberU, cu - fu);
        pillars(c, false, x, z, dp, chamberD, cd - fd);
        bridges(c, true, x, z, up, f0u, hu);
        bridges(c, false, x, z, dp, f0d, hd);

        // ---------------------------------------------------------------- landmarks
        List<Landmarks.Landmark> lm = landmarkScratch.get();
        lm.clear();
        upperLandmarks.covering(x, z, lm);
        deepLandmarks.covering(x, z, lm);
        if (!lm.isEmpty()) {
            c.landmarks = lm.toArray(new Landmarks.Landmark[0]);
        }
        if (withDescents && descents != null) {
            descents.apply(c, x, z);
        }
        return c;
    }

    private double applyPonds(TerrainColumn c, CavernGraph graph, CavernLayer layer, int x, int z, double floor,
                              double ceiling, double layerFluid) {
        int cell = graph.cellSize();
        double qx = graph.warpedX(x, z);
        double qz = graph.warpedZ(x, z);
        int bx = Math.floorDiv((int) Math.floor(qx), cell);
        int bz = Math.floorDiv((int) Math.floor(qz), cell);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                CavernGraph.Node n = graph.node(bx + dx, bz + dz);
                long h = Hashing.hash(seed, n.cellX(), n.cellZ(), Hashing.salt("pond") + layer.ordinal());
                boolean origin = layer == CavernLayer.UPPER && n.cellX() == 0 && n.cellZ() == 0;
                int nodeVariant = variantAt(n.x(), n.z());
                double pondChance = n.region().profile().ponds + (nodeVariant == 3 ? 0.35 : 0);
                if (!origin && Hashing.unit(h) >= pondChance) {
                    continue;
                }
                if (n.height() < 18) {
                    continue;
                }
                double px;
                double pz;
                double pr;
                if (origin) {
                    px = 26;
                    pz = 18;
                    pr = 9;
                } else {
                    double ang = Hashing.unit(Hashing.mix(h ^ 1)) * Math.PI * 2;
                    double dist = n.radius() * (0.2 + 0.35 * Hashing.unit(Hashing.mix(h ^ 2)));
                    px = n.x() + Math.cos(ang) * dist;
                    pz = n.z() + Math.sin(ang) * dist;
                    pr = (5 + 8 * Hashing.unit(Hashing.mix(h ^ 3))) * n.region().profile().pondScale
                            * (nodeVariant == 3 ? 1.4 : 1.0);
                }
                // Ponds are defined in unwarped space so their rim is a clean basin.
                double d = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
                if (d > pr + 14) {
                    continue;
                }
                // Level must be identical for every column of the pond: use the profile at its centre.
                RegionSample rs = pondRegionScratch.get();
                TerrainProfile cp = pondProfileScratch.get();
                regions(layer).sample(px, pz, rs).blendProfile(cp);
                double baseAtCenter = layer == CavernLayer.UPPER ? upperBaseFloor(px, pz, cp) : deepBaseFloor(px, pz, cp);
                double level = Math.floor(baseAtCenter);
                if (level < layerFluid + 4) {
                    continue;
                }
                c.pondGuard = Math.max(c.pondGuard, 1 - smoothstep(pr + 7, pr + 13, d));
                if (d < pr) {
                    double t = d / pr;
                    floor = Math.min(level - 1 - 3.5 * (1 - t * t), floor);
                    c.pondLevel = level;
                    c.pondLava = n.region().lavaFluid();
                } else if (d < pr + 5) {
                    floor = Math.max(floor, level + 1);
                    c.pondLevel = level;
                    c.pondLava = n.region().lavaFluid();
                } else {
                    // gently level the surroundings towards the pond rim
                    double t = smoothstep(pr + 14, pr + 5, d);
                    floor = lerp(floor, Math.max(floor, level + 1), t);
                }
                return floor;
            }
        }
        return floor;
    }

    private void pillars(TerrainColumn c, boolean upper, int x, int z, TerrainProfile p, double chamber, double gap) {
        if (p.pillarChance <= 0.01 || gap < 20 || chamber < 0.1) {
            return;
        }
        final int sp = upper ? 46 : 64;
        int bx = Math.floorDiv(x, sp);
        int bz = Math.floorDiv(z, sp);
        double bestDist = Double.MAX_VALUE;
        double bestR = 0;
        double bestPX = 0, bestPZ = 0;
        long bestH = 0;
        double gate = smoothstep(0.1, 0.45, chamber) * smoothstep(20, 40, gap);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = bx + dx;
                int cz = bz + dz;
                long h = Hashing.hash(seed, cx, cz, upper ? 301 : 302);
                if (Hashing.unit(h) >= p.pillarChance) {
                    continue;
                }
                double px = (cx + 0.2 + 0.6 * Hashing.unit(Hashing.mix(h ^ 1))) * sp;
                double pz = (cz + 0.2 + 0.6 * Hashing.unit(Hashing.mix(h ^ 2))) * sp;
                if (px * px + pz * pz < 60 * 60) {
                    continue; // keep the starting clearing open
                }
                double r = (2.2 + 5.5 * Hashing.unit(Hashing.mix(h ^ 3))) * p.pillarRadius * gate;
                if (r < 1.2) {
                    continue;
                }
                double d = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
                if (d - r * 2.2 < bestDist - bestR * 2.2) {
                    bestDist = d;
                    bestR = r;
                    bestPX = px;
                    bestPZ = pz;
                    bestH = h;
                }
            }
        }
        if (bestR > 0) {
            // Pillars lean and have lobed cross-sections so they read as natural columns, not cylinders.
            double leanA = Hashing.unit(Hashing.mix(bestH ^ 5)) * Math.PI * 2;
            double lean = Hashing.unit(Hashing.mix(bestH ^ 6)) * 0.22;
            double phase = Hashing.unit(Hashing.mix(bestH ^ 7)) * Math.PI * 2;
            if (upper) {
                c.upperPillarDist = bestDist;
                c.upperPillarRadius = bestR;
                c.upperPillarX = bestPX;
                c.upperPillarZ = bestPZ;
                c.upperPillarLeanX = Math.cos(leanA) * lean;
                c.upperPillarLeanZ = Math.sin(leanA) * lean;
                c.upperPillarPhase = phase;
            } else {
                c.deepPillarDist = bestDist;
                c.deepPillarRadius = bestR;
                c.deepPillarX = bestPX;
                c.deepPillarZ = bestPZ;
                c.deepPillarLeanX = Math.cos(leanA) * lean;
                c.deepPillarLeanZ = Math.sin(leanA) * lean;
                c.deepPillarPhase = phase;
            }
        }
    }

    private void bridges(TerrainColumn c, boolean upper, int x, int z, TerrainProfile p, double base, double open) {
        if (p.bridges <= 0.01 || open < 34) {
            return;
        }
        double bn = Math.abs((upper ? bridgeNoiseU : bridgeNoiseD).sample(x, z));
        double bw = 0.028 + 0.02 * p.bridges;
        if (bn >= bw) {
            return;
        }
        double m = bridgeMask.sample(x + (upper ? 0 : 5000), z);
        double thr = 0.32 - p.bridges * 0.5;
        if (m < thr) {
            return;
        }
        double t = 1 - bn / bw;
        double profile = Math.sqrt(t) * smoothstep(thr, thr + 0.08, m) * smoothstep(34, 48, open);
        double thickness = (1.2 + 3.2 * profile);
        double y = base + open * (0.42 + 0.18 * bridgeHeight.sample(x, z + (upper ? 0 : 3000)));
        if (upper) {
            c.upperBridgeY = y;
            c.upperBridgeThickness = thickness;
        } else {
            c.deepBridgeY = y;
            c.deepBridgeThickness = thickness;
        }
    }

    /** Vertical offset of rock strata bands at a column. */
    public double strataOffset(int x, int z) {
        return strata.sample(x, z) * 9.0;
    }

    /** Surface patch noise A in [-1, 1] (ground cover variety). */
    public double patchA(int x, int z) {
        return patchA.sample(x, z);
    }

    /** Surface patch noise B in [-1, 1] (ceiling cover variety). */
    public double patchB(int x, int z) {
        return patchB.sample(x, z);
    }

    // ------------------------------------------------------------------------------------------
    // 3D noise (sampled on a coarse grid and interpolated by the chunk generator)

    /** Wall roughness noise in [-1, 1]. Anisotropic so that it forms ledges and overhang shelves. */
    public double roughness(double x, double y, double z) {
        return rough1.sample(x, y * 2.1, z) * 0.7 + rough2.sample(x, y, z) * 0.3;
    }

    /** Winding tunnel density (&gt; 0 inside a tunnel), in approximate blocks. */
    public double tunnels(double x, double y, double z) {
        double a = spagA.sample(x, y * 1.5, z);
        double b = spagB.sample(x, y * 1.5, z);
        double r = Math.sqrt(a * a + b * b);
        double m = spagMask.sample(x, y, z);
        double width = 0.05 + 0.035 * clamp(m * 1.5, -1, 1);
        return (width - r) * 80.0;
    }

    // ------------------------------------------------------------------------------------------
    // Density

    /** Density from column values alone (no 3D noise). &gt; 0 is open. */
    public double baseDensity(TerrainColumn c, double y) {
        double du = Math.min(y - c.upperFloor, c.upperCeiling - y);
        double dd = Math.min(y - c.deepFloor, c.deepCeiling - y);
        double d = Math.max(du, dd);
        if (c.shaftDist < c.shaftRadius + 1) {
            double ds = c.shaftRadius - c.shaftDist;
            double vertical = Math.min(y - c.shaftBottom, c.shaftTop - y);
            double s = Math.min(ds, vertical);
            if (c.shaftSpiral && c.shaftDist > c.shaftRadius - 3.4 && vertical > 2) {
                double phase = (y - c.shaftBottom) - (c.shaftAngle / (Math.PI * 2)) * SHAFT_SPIRAL_PITCH;
                phase = phase - Math.floor(phase / SHAFT_SPIRAL_PITCH) * SHAFT_SPIRAL_PITCH;
                if (phase < 2.2) {
                    s = Math.min(s, -0.5);
                }
            }
            d = Math.max(d, s);
        }
        for (int i = 0; i < c.bandCount; i++) {
            d = Math.max(d, Math.min(y - c.bandFloor[i], c.bandCeil[i] - y));
        }
        return d;
    }

    /** Whether winding tunnels may open at this height (keeps fluids contained). */
    public boolean tunnelsAllowed(TerrainColumn c, double y) {
        if (!Double.isNaN(c.pondLevel) || c.pondGuard > 0.05) {
            return false;
        }
        double sep = c.separation;
        if (y > Math.max(sep, SEA_LEVEL) + 3) {
            return y < CavernLayer.UPPER.maxCeiling - 6;
        }
        return y < Math.min(sep, SEA_LEVEL) - 8 && y > MIN_Y + 10;
    }

    /**
     * Density of the open cavern space at a block (before formations). {@code rough} and
     * {@code tunnel} are the (interpolated) values of {@link #roughness} and {@link #tunnels}.
     */
    public double openDensity(TerrainColumn c, double y, double rough, double tunnel) {
        boolean upper = y >= c.separation;
        TerrainProfile p = upper ? c.upperProfile : c.deepProfile;
        double amp = p.roughness;
        if (c.pondGuard > 0) {
            amp *= 1 - c.pondGuard;
        }
        int band = -1;
        for (int i = 0; i < c.bandCount; i++) {
            if (y >= c.bandFloor[i] - 3 && y <= c.bandCeil[i] + 3) {
                band = i;
                break;
            }
        }
        if (band >= 0) {
            // descent caves stay walkable: exact floor, gentle relief on walls and ceiling only
            amp *= y < c.bandFloor[band] + 1.5 ? 0 : 0.35;
        }
        double d = baseDensity(c, y);
        d += amp * rough;
        if (tunnel > d && tunnelsAllowed(c, y) && (band < 0 || y > c.bandFloor[band] + 1)) {
            d = tunnel;
        }
        if (band >= 0 && c.bandLedge[band] && y < c.bandFloor[band] && y >= c.bandFloor[band] - 2.5) {
            d = Math.min(d, -0.5); // natural rock ledge carrying the descent across open space
        }
        return d;
    }

    private static boolean treePillars(RegionType r) {
        return r == RegionType.ROOTWILD || r == RegionType.GOLDEN_GROVE || r == RegionType.EMERALD_WETLANDS;
    }

    /** Evaluates pillars, bridges and landmarks at a block. */
    public FormationHit formation(TerrainColumn c, double y, FormationHit hit) {
        hit.reset();
        boolean upper = y >= c.separation;
        RegionType region = upper ? c.upperRegion.primary : c.deepRegion.primary;
        // Formations never fill ponds (keeps every lake intact, including the starter pond).
        boolean inPond = c.pondGuard > 0.3 && !Double.isNaN(c.pondLevel) && y <= c.pondLevel + 2;
        // pillars
        double pr = upper ? c.upperPillarRadius : c.deepPillarRadius;
        if (pr > 0 && !inPond) {
            double f = upper ? c.upperFloor : c.deepFloor;
            double ce = upper ? c.upperCeiling : c.deepCeiling;
            double gap = ce - f;
            double leanMax = Math.hypot(upper ? c.upperPillarLeanX : c.deepPillarLeanX,
                    upper ? c.upperPillarLeanZ : c.deepPillarLeanZ) * Math.max(0, gap);
            double reach = pr * 2.6 + (treePillars(region) || region == RegionType.FUNGAL_EXPANSE ? 14 + pr * 1.2 : 2);
            if (gap > 4 && (upper ? c.upperPillarDist : c.deepPillarDist) - leanMax < reach) {
                double t = clamp((y - f) / gap, 0, 1);
                double tt = 2 * t - 1;
                double px = (upper ? c.upperPillarX : c.deepPillarX) + (upper ? c.upperPillarLeanX : c.deepPillarLeanX) * (y - f);
                double pz = (upper ? c.upperPillarZ : c.deepPillarZ) + (upper ? c.upperPillarLeanZ : c.deepPillarLeanZ) * (y - f);
                double dx = c.x - px;
                double dz = c.z - pz;
                double dist = Math.sqrt(dx * dx + dz * dz);
                double phase = upper ? c.upperPillarPhase : c.deepPillarPhase;
                double lobe = 1 + 0.22 * Math.sin(Math.atan2(dz, dx) * 3 + phase + y * 0.045);
                double r = pr * (0.8 + 0.8 * tt * tt) * lobe;
                double pd = dist - r;
                hit.density = pd;
                hit.code = Landmarks.PILLAR;
                hit.region = region;
                if (treePillars(region) && t > 0.5 && t < 0.97) {
                    // Foliage ring around the upper trunk: every wooden pillar is a colossal tree.
                    double bump = Math.max(0, 1 - Math.abs(t - 0.78) / 0.2);
                    double canopy = dist - (r + (5 + pr * 1.1) * bump);
                    long hh = Hashing.hash((long) c.x, (long) Math.floor(y), (long) c.z, 0x7A11L);
                    canopy += Hashing.signed(hh) * 1.5;
                    if (canopy < hit.density && bump > 0.05) {
                        hit.density = Math.min(hit.density, canopy);
                        hit.code = pd < 0 ? Landmarks.PILLAR : ((hh & 15) == 0 ? Landmarks.LEAVES_GLOW : Landmarks.LEAVES);
                    }
                } else if (region == RegionType.FUNGAL_EXPANSE && t > 0.62 && t < 0.86) {
                    // Mushroom-cap shelf on fungal stalks.
                    double shelf = dist - (r + 9 * Math.max(0, 1 - Math.abs(t - 0.74) / 0.12));
                    if (shelf < hit.density) {
                        hit.density = Math.min(hit.density, shelf);
                        hit.code = pd < 0 ? Landmarks.PILLAR : (t < 0.72 ? Landmarks.CAP_GLOW : Landmarks.CAP);
                    }
                }
            }
        }
        // bridges and arches
        double by = upper ? c.upperBridgeY : c.deepBridgeY;
        if (!Double.isNaN(by)) {
            double bd = Math.abs(y - by) - (upper ? c.upperBridgeThickness : c.deepBridgeThickness);
            if (bd < hit.density) {
                hit.density = bd;
                hit.code = treePillars(region) ? Landmarks.ROOTS : Landmarks.PILLAR;
                hit.region = region;
            }
        }
        if (!inPond) {
            for (Landmarks.Landmark l : c.landmarks) {
                l.sample(c.x + 0.5, y + 0.5, c.z + 0.5, hit);
            }
        }
        if (hit.region == null) {
            hit.region = region;
        }
        return hit;
    }

    private final ThreadLocal<FormationHit> hitScratch = ThreadLocal.withInitial(FormationHit::new);

    /** Full density at a block (open space minus formations). &gt; 0 is open. */
    public double density(TerrainColumn c, double y, double rough, double tunnel) {
        double open = openDensity(c, y, rough, tunnel);
        if (open <= 0) {
            return open;
        }
        return Math.min(open, formation(c, y, hitScratch.get()).density);
    }

    /** Convenience: full density computing 3D noise directly (slower; for analysis tools). */
    public double densityAt(int x, int y, int z) {
        TerrainColumn c = column(x, z);
        return density(c, y, roughness(x, y, z), tunnels(x, y, z));
    }

    public static int bedrockFloorTop(long seed, int x, int z) {
        return MIN_Y + (int) Long.remainderUnsigned(Hashing.hash(seed, x, z, 7001), 5);
    }

    public static int bedrockRoofBottom(long seed, int x, int z) {
        return ROOF_BEDROCK + (int) Long.remainderUnsigned(Hashing.hash(seed, x, z, 7002), 5);
    }

    /** Classifies a block given its full density (formations are reported as STONE). */
    public Material classify(TerrainColumn c, int y, double density) {
        return classify(c, y, density, Double.MAX_VALUE);
    }

    /**
     * Classifies a block given its open-space density and formation density. Returns
     * {@link Material#FORMATION} where a formation fills otherwise open space.
     */
    public Material classify(TerrainColumn c, int y, double openDensity, double formationDensity) {
        if (y <= bedrockFloorTop(seed, c.x, c.z) || y >= bedrockRoofBottom(seed, c.x, c.z)) {
            return Material.BEDROCK;
        }
        if (y > CavernLayer.UPPER.maxCeiling + 4 || y < MIN_Y + 6) {
            return Material.STONE;
        }
        if (openDensity <= 0) {
            return Material.STONE;
        }
        if (c.bandCount > 0 && y > CavernLayer.DEEP.fluidLevel && c.bandAt(y) >= 0) {
            return Material.AIR; // descent caves are validated dry and cut through formations; never fill them
        }
        if (formationDensity <= 0) {
            return Material.FORMATION;
        }
        boolean frozen = (y >= c.separation ? c.upperRegion.primary : c.deepRegion.primary) == RegionType.EVERFROST;
        if (!Double.isNaN(c.pondLevel) && y <= c.pondLevel && y > c.pondLevel - 10) {
            if (c.pondLava) {
                return Material.LAVA;
            }
            return frozen && y == (int) c.pondLevel ? Material.ICE : Material.WATER;
        }
        if (y >= c.separation) {
            if (y <= SEA_LEVEL) {
                if (c.upperLava) {
                    return Material.LAVA;
                }
                return frozen && y == SEA_LEVEL ? Material.ICE : Material.WATER;
            }
        } else if (y <= CavernLayer.DEEP.fluidLevel) {
            return c.deepLava ? Material.LAVA : Material.WATER;
        }
        return Material.AIR;
    }

    public Material materialAt(int x, int y, int z) {
        TerrainColumn c = column(x, z);
        double open = openDensity(c, y, roughness(x, y, z), tunnels(x, y, z));
        double form = open > 0 ? formation(c, y, hitScratch.get()).density : Double.MAX_VALUE;
        return classify(c, y, open, form);
    }

    /** Region at a block position (upper or deep layer depending on height). */
    public RegionType regionAt(int x, int y, int z) {
        return column(x, z).regionAt(y);
    }
}
