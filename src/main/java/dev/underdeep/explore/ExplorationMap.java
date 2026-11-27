package dev.underdeep.explore;

import dev.underdeep.civ.settlement.SettlementMap;
import dev.underdeep.explore.design.Designs;
import dev.underdeep.world.noise.Hashing;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic placement of exploration sites (Stage 9). A pure function of (seed, terrain model, settlement map,
 * placement settings): independent of chunk generation order, never loads chunks.
 * <p>
 * Each layer is divided into a jittered grid (80 blocks upper, 96 deep). Per cell: a hashed roll decides whether
 * the cell holds a site; a family is drawn by weight among those that fit the region, layer and distance from the
 * world spawn; up to four candidate points are tried against the terrain model (dry floor, gentle relief, headroom,
 * solid rock for parts below the floor, no lava, water only for aquatic designs) and against settlements; finally
 * overlapping candidates in neighbouring cells are resolved by priority (larger and rarer first). Results are
 * cached; layouts (authored geometry) are kept in a small bounded cache and rebuilt on demand.
 */
public final class ExplorationMap {
    public static final int CELL_UPPER = 80, CELL_DEEP = 96;
    private static final int SPAWN_CLEAR = 72, SPAWN_DANGER_CLEAR = 220;

    private final UnderdeepTerrain terrain;
    @Nullable
    private final SettlementMap settlements;
    private final long seed;
    private final double density, dungeonDensity;
    private final ConcurrentHashMap<Long, Optional<Candidate>> tentative = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Optional<Site>> finals = new ConcurrentHashMap<>();
    private final Map<Long, SiteLayout> layouts = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, SiteLayout> e) {
            return size() > 40;
        }
    });

    private record Candidate(Site site, double priority) {
    }

    public ExplorationMap(UnderdeepTerrain terrain, @Nullable SettlementMap settlements, double density, double dungeonDensity) {
        this.terrain = terrain;
        this.settlements = settlements;
        this.seed = Hashing.hash(terrain.seed(), Hashing.salt("exploration"));
        this.density = density;
        this.dungeonDensity = dungeonDensity;
    }

    public UnderdeepTerrain terrain() {
        return terrain;
    }

    public static int cell(CavernLayer l) {
        return l == CavernLayer.UPPER ? CELL_UPPER : CELL_DEEP;
    }

    // ------------------------------------------------------------------ queries

    public Optional<Site> site(CavernLayer layer, int cx, int cz) {
        long key = Site.packId(layer, cx, cz);
        Optional<Site> s = finals.get(key);
        if (s == null) {
            s = Optional.ofNullable(resolve(layer, cx, cz));
            finals.putIfAbsent(key, s);
        }
        return s;
    }

    @Nullable
    public Site byId(long id) {
        return site(Site.layerOf(id), Site.cellX(id), Site.cellZ(id)).orElse(null);
    }

    /** Sites (both layers) whose footprint plus margin touches the rectangle. */
    public List<Site> sitesIn(int minX, int minZ, int maxX, int maxZ, int margin) {
        List<Site> out = new ArrayList<>();
        for (CavernLayer layer : CavernLayer.values()) {
            int cell = cell(layer), reach = 48 + margin;
            for (int cx = Math.floorDiv(minX - reach, cell); cx <= Math.floorDiv(maxX + reach, cell); cx++) {
                for (int cz = Math.floorDiv(minZ - reach, cell); cz <= Math.floorDiv(maxZ + reach, cell); cz++) {
                    site(layer, cx, cz).ifPresent(s -> {
                        int r = s.radius() + margin;
                        if (s.x() + r >= minX && s.x() - r <= maxX && s.z() + r >= minZ && s.z() - r <= maxZ) {
                            out.add(s);
                        }
                    });
                }
            }
        }
        return out;
    }

    /** The site whose built volume contains a position (with a small margin), if any. */
    @Nullable
    public Site siteAt(BlockPos p, int margin) {
        for (Site s : sitesIn(p.getX(), p.getZ(), p.getX(), p.getZ(), margin)) {
            int low = s.y() - s.family().below() - 3 - margin, high = s.y() + s.family().height() + 4 + margin;
            if (p.getY() >= low && p.getY() <= high && s.containsXZ(p.getX(), p.getZ(), margin)) {
                return s;
            }
        }
        return null;
    }

    public List<Site> sitesNear(int x, int z, int radius) {
        List<Site> l = new ArrayList<>(sitesIn(x - radius, z - radius, x + radius, z + radius, 0));
        l.removeIf(s -> s.dist(x, z) > radius);
        l.sort((a, b) -> Double.compare(a.dist(x, z), b.dist(x, z)));
        return l;
    }

    /** The authored layout of a site (bounded cache; rebuilt deterministically when evicted). */
    public SiteLayout layout(Site s) {
        SiteLayout l = layouts.get(s.id());
        if (l == null) {
            l = new SiteLayout(s, Designs.build(s));
            SiteLayout prev = layouts.putIfAbsent(s.id(), l);
            if (prev != null) {
                l = prev;
            }
        }
        return l;
    }

    /** Self-test hook: registers a synthetic site (test areas only; never used in normal play). */
    public void putForTest(Site s) {
        finals.put(s.id(), Optional.of(s));
        layouts.remove(s.id());
    }

    // ------------------------------------------------------------------ placement

    @Nullable
    private Site resolve(CavernLayer layer, int cx, int cz) {
        Candidate mine = tentative(layer, cx, cz);
        if (mine == null) {
            return null;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Candidate o = tentative(layer, cx + dx, cz + dz);
                if (o == null) {
                    continue;
                }
                double gap = mine.site.radius() + o.site.radius() + 14;
                if (mine.site.dist(o.site.x(), o.site.z()) < gap && (o.priority > mine.priority
                        || o.priority == mine.priority && Long.compare(o.site.id(), mine.site.id()) > 0)) {
                    return null;
                }
            }
        }
        return mine.site;
    }

    @Nullable
    private Candidate tentative(CavernLayer layer, int cx, int cz) {
        long key = Site.packId(layer, cx, cz);
        Optional<Candidate> c = tentative.get(key);
        if (c == null) {
            c = Optional.ofNullable(compute(layer, cx, cz, key));
            tentative.putIfAbsent(key, c);
        }
        return c.orElse(null);
    }

    @Nullable
    private Candidate compute(CavernLayer layer, int cx, int cz, long key) {
        int cell = cell(layer);
        long h = Hashing.hash(seed, key);
        double chance = (layer == CavernLayer.UPPER ? 0.46 : 0.40) * density;
        if (Hashing.unit(Hashing.hash(h, 1)) >= chance) {
            return null;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            long ha = Hashing.hash(h, 10 + attempt);
            int x = cx * cell + 10 + (int) (Hashing.unit(Hashing.hash(ha, 1)) * (cell - 20));
            int z = cz * cell + 10 + (int) (Hashing.unit(Hashing.hash(ha, 2)) * (cell - 20));
            double fromSpawn = Math.hypot(x, z);
            if (fromSpawn < SPAWN_CLEAR) {
                continue;
            }
            RegionType region = terrain.regions(layer).typeAt(x, z);
            List<ExploreContent.Family> pool = new ArrayList<>();
            double total = 0;
            for (ExploreContent.Family f : ExploreContent.FAMILIES.values()) {
                if (!f.layers().contains(layer) || !f.fits(region) || fromSpawn < f.minSpawn() || f.category() == ExploreContent.Category.SECRET
                        && f.minSpawn() == 0 && fromSpawn < SPAWN_CLEAR) {
                    continue;
                }
                if (fromSpawn < SPAWN_DANGER_CLEAR && (f.category() == ExploreContent.Category.DUNGEON)) {
                    continue;
                }
                pool.add(f);
                total += weight(f, region);
            }
            if (pool.isEmpty()) {
                continue;
            }
            double pick = Hashing.unit(Hashing.hash(ha, 3)) * total;
            ExploreContent.Family chosen = null;
            for (ExploreContent.Family f : pool) {
                pick -= weight(f, region);
                if (pick <= 0) {
                    chosen = f;
                    break;
                }
            }
            if (chosen == null) {
                chosen = pool.get(pool.size() - 1);
            }
            Integer floor = fit(layer, chosen, x, z);
            if (floor == null) {
                continue;
            }
            BlockRotation rot = BlockRotation.values()[(int) (Hashing.unit(Hashing.hash(ha, 4)) * 4) & 3];
            Site s = new Site(key, chosen, layer, region, x, floor, z, chosen.radius(), rot, Hashing.hash(ha, 5));
            double prio = chosen.scale().rank * 10 + Hashing.unit(Hashing.hash(ha, 6));
            return new Candidate(s, prio);
        }
        return null;
    }

    private double weight(ExploreContent.Family f, RegionType region) {
        double w = f.weight();
        if (f.category() == ExploreContent.Category.DUNGEON) {
            w *= dungeonDensity;
        }
        return f.anyRegion() ? w : w * 2.2;
    }

    /** Terrain fit of a family's footprint: returns the floor level or null. */
    @Nullable
    Integer fit(CavernLayer layer, ExploreContent.Family f, int x, int z) {
        boolean upper = layer == CavernLayer.UPPER;
        TerrainColumn c0 = terrain.column(x, z);
        double f0 = upper ? c0.upperFloor : c0.deepFloor;
        int floorY = (int) Math.floor(f0);
        if (lava(c0, upper) || !f.aquatic() && submerged(c0, upper, f0)) {
            return null;
        }
        int r = f.radius();
        int good = 0, total = 0;
        for (int ring = 0; ring <= r; ring += Math.max(3, r / 4)) {
            int samples = ring == 0 ? 1 : 8;
            for (int i = 0; i < samples; i++) {
                double a = Math.PI * 2 * i / samples + ring * 0.31;
                int sx = (int) Math.round(x + Math.cos(a) * ring), sz = (int) Math.round(z + Math.sin(a) * ring);
                TerrainColumn c = terrain.column(sx, sz);
                double fl = upper ? c.upperFloor : c.deepFloor;
                double ce = upper ? c.upperCeiling : c.deepCeiling;
                boolean ok = !lava(c, upper) && ce - Math.max(fl, floorY) >= f.height() + 2 && Math.abs(fl - f0) <= 3 + ring * 0.22
                        && (f.aquatic() || !submerged(c, upper, fl));
                total++;
                good += ok ? 1 : 0;
            }
        }
        if (good < total * 0.78) {
            return null;
        }
        if (f.below() > 0) {
            // parts below the floor must be cut into solid rock (no caves, water or lava in the way)
            for (int dx = -r + 2; dx <= r - 2; dx += Math.max(3, r / 2)) {
                for (int dz = -r + 2; dz <= r - 2; dz += Math.max(3, r / 2)) {
                    for (int dy = 2; dy <= f.below() + 2; dy += 3) {
                        UnderdeepTerrain.Material m = terrain.materialAt(x + dx, floorY - dy, z + dz);
                        if (m != UnderdeepTerrain.Material.STONE && m != UnderdeepTerrain.Material.FORMATION) {
                            return null;
                        }
                    }
                }
            }
        }
        if (upper && settlements != null && settlements.siteAt(x, z, r + 20) != null) {
            return null;
        }
        return floorY;
    }

    private static boolean lava(TerrainColumn c, boolean upper) {
        return upper ? c.upperLava || c.pondLava && !Double.isNaN(c.pondLevel) && c.upperFloor < c.pondLevel : c.deepLava;
    }

    private static boolean submerged(TerrainColumn c, boolean upper, double floor) {
        if (!upper) {
            return floor < CavernLayer.DEEP.fluidLevel - 0.5;
        }
        return floor < UnderdeepTerrain.SEA_LEVEL - 0.5 || (!Double.isNaN(c.pondLevel) && floor < c.pondLevel) || c.river > 0.3;
    }
}
