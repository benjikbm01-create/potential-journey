package dev.underdeep.civ.settlement;

import dev.underdeep.civ.Civilization;
import dev.underdeep.world.noise.Hashing;
import dev.underdeep.world.region.RegionSample;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernGraph;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic settlement placement (Stage 4). Settlements sit at cavern-graph chamber centres, which are
 * open and connected by tunnels by construction, so every settlement is reachable through the cave network.
 * <p>
 * Pure function of (seed, terrain model): independent of chunk generation order. Decisions are made per
 * chamber in three steps — a hashed roll for a tentative category, a terrain fit check (downgrading until
 * the category fits the chamber), and a local spacing rule against neighbouring chambers' tentative
 * results — plus two guarantees near the world spawn (a welcoming settlement 140–320 blocks away and a
 * town 600–1100 blocks away). Results are cached in a concurrent map (immutable values).
 */
public final class SettlementMap {
    public static final CavernLayer LAYER = CavernLayer.UPPER;
    private static final int STARTER_MIN = 140, STARTER_MAX = 320, TOWN_MIN = 600, TOWN_MAX = 1100;

    private final UnderdeepTerrain terrain;
    private final CavernGraph graph;
    private final int cell;
    private final long seed;
    private final ConcurrentHashMap<Long, Tentative> tentative = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Optional<SettlementSite>> finals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, SettlementPlan> plans = new ConcurrentHashMap<>();
    private volatile long starterKey = Long.MIN_VALUE, townKey = Long.MIN_VALUE;
    private volatile boolean guaranteesReady;

    /** Tentative result for one chamber before spacing is resolved. */
    private record Tentative(SettlementCategory category, Civilization civ, double priority, int radius, int floorY,
                             double headroom, boolean overWater, boolean water, RegionType region) {
    }

    public SettlementMap(UnderdeepTerrain terrain) {
        this.terrain = terrain;
        this.graph = terrain.graph(LAYER);
        this.cell = graph.cellSize();
        this.seed = Hashing.hash(terrain.seed(), Hashing.salt("settlements"));
    }

    public UnderdeepTerrain terrain() {
        return terrain;
    }

    public long worldSeed() {
        return terrain.seed();
    }

    // ------------------------------------------------------------------ queries

    /**
     * Whether natural decoration must not start at this column: within 12 blocks of a planned building,
     * road or plaza (features reach at most one chunk from their origin).
     */
    public boolean reserved(int x, int z) {
        SettlementSite s = siteAt(x, z, 30);
        if (s == null) {
            return false;
        }
        SettlementPlan plan = plan(s);
        for (SettlementPlan.Plaza p : plan.plazas()) {
            if (Math.hypot(x - p.x(), z - p.z()) <= p.radius() + 12) {
                return true;
            }
        }
        for (SettlementPlan.Building b : plan.buildings()) {
            if (x >= b.box().getMinX() - 12 && x <= b.box().getMaxX() + 12 && z >= b.box().getMinZ() - 12 && z <= b.box().getMaxZ() + 12) {
                return true;
            }
        }
        for (SettlementPlan.Road r : plan.roads()) {
            double lim = r.width() / 2 + 10;
            if (r.project(x, z)[1] <= lim * lim) {
                return true;
            }
        }
        return false;
    }

    /** The (cached) full plan of a settlement. */
    public SettlementPlan plan(SettlementSite site) {
        return plans.computeIfAbsent(site.id(), k -> SettlementPlanner.plan(site, terrain));
    }

    /** Final settlement of a chamber cell, if any. */
    public Optional<SettlementSite> site(int cx, int cz) {
        long key = SettlementSite.packId(LAYER, cx, cz);
        Optional<SettlementSite> s = finals.get(key);
        if (s == null) {
            s = Optional.ofNullable(resolve(cx, cz));
            finals.putIfAbsent(key, s);
        }
        return s;
    }

    @Nullable
    public SettlementSite byId(long id) {
        return site(SettlementSite.cellX(id), SettlementSite.cellZ(id)).orElse(null);
    }

    /** Settlements whose planned area (plus margin) touches the rectangle. */
    public List<SettlementSite> sitesIn(int minX, int minZ, int maxX, int maxZ, int margin) {
        List<SettlementSite> out = new ArrayList<>();
        int reach = SettlementCategory.CITY.maxRadius + margin + cell;
        for (int cx = Math.floorDiv(minX - reach, cell); cx <= Math.floorDiv(maxX + reach, cell); cx++) {
            for (int cz = Math.floorDiv(minZ - reach, cell); cz <= Math.floorDiv(maxZ + reach, cell); cz++) {
                site(cx, cz).ifPresent(s -> {
                    int r = s.radius() + margin;
                    if (s.x() + r >= minX && s.x() - r <= maxX && s.z() + r >= minZ && s.z() - r <= maxZ) {
                        out.add(s);
                    }
                });
            }
        }
        return out;
    }

    /** The settlement containing a horizontal position (within its radius + margin), if any. */
    @Nullable
    public SettlementSite siteAt(int x, int z, int margin) {
        for (SettlementSite s : sitesIn(x, z, x, z, margin)) {
            if (s.contains(x, z, margin)) {
                return s;
            }
        }
        return null;
    }

    /** All settlements within a radius of a point, nearest first. */
    public List<SettlementSite> sitesNear(int x, int z, int radius) {
        List<SettlementSite> list = new ArrayList<>(sitesIn(x - radius, z - radius, x + radius, z + radius, 0));
        list.removeIf(s -> (double) (s.x() - x) * (s.x() - x) + (double) (s.z() - z) * (s.z() - z) > (double) radius * radius);
        list.sort((a, b) -> Double.compare(dist2(a, x, z), dist2(b, x, z)));
        return list;
    }

    private static double dist2(SettlementSite s, int x, int z) {
        return (double) (s.x() - x) * (s.x() - x) + (double) (s.z() - z) * (s.z() - z);
    }

    // ------------------------------------------------------------------ decisions

    @Nullable
    private SettlementSite resolve(int cx, int cz) {
        ensureGuarantees();
        Tentative t = tentative(cx, cz);
        if (t == null) {
            return null;
        }
        CavernGraph.Node n = graph.node(cx, cz);
        // local spacing: a neighbour with a stronger claim within reach removes this settlement
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Tentative o = tentative(cx + dx, cz + dz);
                if (o == null) {
                    continue;
                }
                CavernGraph.Node on = graph.node(cx + dx, cz + dz);
                double d = Math.hypot(on.x() - n.x(), on.z() - n.z());
                if (d < t.radius + o.radius + 48 && stronger(o, t)) {
                    return null;
                }
            }
        }
        long key = SettlementSite.packId(LAYER, cx, cz);
        return new SettlementSite(key, t.civ, t.category, LAYER, t.region, (int) Math.round(n.x()), t.floorY,
                (int) Math.round(n.z()), t.radius, t.headroom, t.overWater, t.water, key == starterKey);
    }

    private static boolean stronger(Tentative a, Tentative b) {
        if (a.category != b.category) {
            return a.category.ordinal() > b.category.ordinal();
        }
        return a.priority > b.priority;
    }

    @Nullable
    private Tentative tentative(int cx, int cz) {
        long key = SettlementSite.packId(LAYER, cx, cz);
        Tentative t = tentative.get(key);
        if (t == null) {
            t = computeTentative(cx, cz, key);
            tentative.putIfAbsent(key, t == null ? NONE : t);
            return t;
        }
        return t == NONE ? null : t;
    }

    private static final Tentative NONE = new Tentative(SettlementCategory.ENCAMPMENT, null, -1, 0, 0, 0, false, false, null);

    @Nullable
    private Tentative computeTentative(int cx, int cz, long key) {
        CavernGraph.Node n = graph.node(cx, cz);
        double distOrigin = Math.hypot(n.x(), n.z());
        if (distOrigin < 112) {
            return null; // the starting cavern stays wild
        }
        RegionSample rs = new RegionSample();
        terrain.regions(LAYER).sample((int) n.x(), (int) n.z(), rs);
        RegionType region = rs.primary;
        double u = Hashing.unit(Hashing.hash(seed, cx, cz, 1));
        SettlementCategory want;
        boolean forcedStarter = key == starterKey;
        boolean forcedTown = key == townKey;
        if (forcedTown) {
            want = SettlementCategory.TOWN;
        } else if (forcedStarter) {
            want = SettlementCategory.VILLAGE;
        } else if (n.mega()) {
            want = u < 0.16 ? SettlementCategory.CITY : u < 0.42 ? SettlementCategory.TOWN : u < 0.62 ? SettlementCategory.VILLAGE : null;
        } else {
            want = u < 0.035 ? SettlementCategory.TOWN : u < 0.11 ? SettlementCategory.VILLAGE
                    : u < 0.19 ? SettlementCategory.OUTPOST : u < 0.31 ? SettlementCategory.ENCAMPMENT : null;
        }
        if (want == null) {
            return null;
        }
        Civilization civ = civFor(region, cx, cz);
        if (civ == null) {
            return null;
        }
        // remote frontier ecosystems only host small settlements
        if (civ.affinity(region) == 0 && want.atLeast(SettlementCategory.VILLAGE)) {
            want = SettlementCategory.OUTPOST;
        }
        boolean aquatic = civ == Civilization.TIDE_DWELLERS;
        TerrainColumn c0 = terrain.column((int) n.x(), (int) n.z());
        int floorY = (int) Math.floor(c0.upperFloor) + 1;
        Fit fit = null;
        for (SettlementCategory c = want; c != null; c = c.smaller()) {
            int r = Math.min(c.maxRadius, Math.max(c.minRadius, (int) (n.radius() * (c == SettlementCategory.CITY ? 1.0 : 0.85))));
            fit = fit(n.x(), n.z(), floorY, r, c.headroom, aquatic);
            if (fit.ok) {
                // shrink to the usable radius when generous
                int radius = Math.max(c.minRadius, Math.min(r, fit.usable));
                double prio = Hashing.unit(Hashing.hash(seed, cx, cz, 2)) + (forcedStarter || forcedTown ? 10 : 0);
                return new Tentative(c, civ, prio, radius, floorY, fit.headroom, fit.overWater, fit.water, region);
            }
            if (forcedStarter && c == SettlementCategory.OUTPOST) {
                break;
            }
        }
        return null;
    }

    @Nullable
    private Civilization civFor(RegionType region, int cx, int cz) {
        Civilization c = Civilization.nativeOf(region);
        if (c == null) {
            return null;
        }
        // in shared ecosystems, sometimes a neighbouring civilization settles instead (mixed frontiers)
        List<Civilization> others = new ArrayList<>();
        for (Civilization o : Civilization.values()) {
            if (o != c && o.secondaryRegions().contains(region)) {
                others.add(o);
            }
        }
        double u = Hashing.unit(Hashing.hash(seed, cx, cz, 3));
        int pick = (int) (u / 0.16);
        return pick < others.size() ? others.get(pick) : c;
    }

    /** Terrain fit of a circular area: dry buildable floor share, headroom and gentle relief. */
    private record Fit(boolean ok, int usable, double headroom, boolean overWater, boolean water) {
    }

    private Fit fit(double x, double z, int floorY, int radius, int needHead, boolean aquatic) {
        int good = 0, total = 0, wet = 0;
        double headSum = 0;
        int usable = radius;
        boolean water = false;
        for (int ring = 0; ring <= radius; ring += Math.max(4, radius / 8)) {
            int samples = ring == 0 ? 1 : Math.max(8, (int) (ring * 0.5));
            int ringGood = 0;
            for (int i = 0; i < samples; i++) {
                double a = (Math.PI * 2 * i) / samples + ring * 0.37;
                int sx = (int) Math.round(x + Math.cos(a) * ring);
                int sz = (int) Math.round(z + Math.sin(a) * ring);
                TerrainColumn c = terrain.column(sx, sz);
                double f = c.upperFloor;
                double head = c.upperCeiling - Math.max(f, UnderdeepTerrain.SEA_LEVEL);
                boolean submerged = f < UnderdeepTerrain.SEA_LEVEL - 0.5 || (!Double.isNaN(c.pondLevel) && f < c.pondLevel);
                boolean lava = c.upperLava || submerged && c.pondLava;
                boolean ok;
                if (submerged) {
                    water |= !lava;
                    wet++;
                    double depth = (Double.isNaN(c.pondLevel) ? UnderdeepTerrain.SEA_LEVEL : c.pondLevel) - f;
                    ok = aquatic && !lava && depth < 9 && head > needHead;
                } else {
                    ok = !lava && head > needHead && Math.abs(f + 1 - floorY) < 5 + ring * 0.28;
                }
                if (c.river > 0.3) {
                    water = true;
                }
                total++;
                if (ok) {
                    good++;
                    ringGood++;
                    headSum += head;
                }
            }
            if (ring > 0 && ringGood < samples * 0.55 && usable == radius) {
                usable = Math.max(0, ring - 4);
            }
        }
        boolean centreWet = wet > 0 && terrainSubmerged(x, z);
        boolean ok = good >= total * 0.62 && usable >= radius * 0.7 && (!centreWet || aquatic);
        return new Fit(ok, usable, good > 0 ? headSum / good : 0, centreWet, water);
    }

    private boolean terrainSubmerged(double x, double z) {
        TerrainColumn c = terrain.column((int) x, (int) z);
        return c.upperFloor < UnderdeepTerrain.SEA_LEVEL - 0.5 || (!Double.isNaN(c.pondLevel) && c.upperFloor < c.pondLevel);
    }

    // ------------------------------------------------------------------ guarantees near spawn

    private void ensureGuarantees() {
        if (guaranteesReady) {
            return;
        }
        synchronized (this) {
            if (guaranteesReady) {
                return;
            }
            starterKey = bestInRing(STARTER_MIN, STARTER_MAX, true, SettlementCategory.VILLAGE);
            townKey = bestInRing(TOWN_MIN, TOWN_MAX, false, SettlementCategory.TOWN);
            guaranteesReady = true;
        }
    }

    /** Best chamber in a ring around the origin for a forced settlement (welcoming civilizations first). */
    private long bestInRing(int min, int max, boolean welcoming, SettlementCategory category) {
        long best = Long.MIN_VALUE;
        double bestScore = -1;
        for (CavernGraph.Node n : graph.nodesIn(-max, -max, max, max)) {
            double d = Math.hypot(n.x(), n.z());
            if (d < min || d > max) {
                continue;
            }
            RegionSample rs = new RegionSample();
            terrain.regions(LAYER).sample((int) n.x(), (int) n.z(), rs);
            Civilization civ = Civilization.nativeOf(rs.primary);
            if (civ == null || civ.affinity(rs.primary) == 0 || welcoming && !civ.welcoming()) {
                continue;
            }
            TerrainColumn c0 = terrain.column((int) n.x(), (int) n.z());
            int floorY = (int) Math.floor(c0.upperFloor) + 1;
            int r = Math.min(category.maxRadius, Math.max(category.minRadius, (int) (n.radius() * 0.85)));
            Fit f = fit(n.x(), n.z(), floorY, r, category.headroom, civ == Civilization.TIDE_DWELLERS);
            if (!f.ok) {
                continue;
            }
            double score = n.radius() + (welcoming ? (max - d) * 0.2 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = SettlementSite.packId(LAYER, n.cellX(), n.cellZ());
            }
        }
        return best;
    }
}
