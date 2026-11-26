package dev.underdeep.civ.settlement;

import dev.underdeep.civ.Civilization;
import dev.underdeep.civ.Names;
import dev.underdeep.civ.arch.Architect;
import dev.underdeep.civ.arch.Blueprint;
import dev.underdeep.civ.arch.BuildingKind;
import dev.underdeep.world.noise.Hashing;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static dev.underdeep.civ.arch.BuildingKind.*;
import static dev.underdeep.civ.settlement.SettlementCategory.*;

/**
 * Plans a settlement from its site and the terrain model (pure, deterministic, chunk-independent):
 * layout type → central plaza (well or landmark) → main roads and ring roads → plots along the roads in
 * districts (civic/commercial core, residential and craft rings, agricultural outskirts) → building
 * programme per category → terrain fit per plot (relief, headroom, water) → driveways to every entrance →
 * docks, gates and lamps. Plots that do not fit are skipped; a smaller well-integrated settlement is
 * preferred to a forced one.
 */
public final class SettlementPlanner {
    private SettlementPlanner() {
    }

    /** Diagnostics: why plots were rejected (self-test reports these). */
    public static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> REJECTS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean reject(String why) {
        REJECTS.computeIfAbsent(why, k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
        return false;
    }

    private static final int FREE = 0, ROAD = 1, BUILT = 2, PLAZA = 3;

    /** Working state of one planning run. */
    private static final class Ctx {
        final SettlementSite site;
        final UnderdeepTerrain t;
        final Civilization civ;
        final Random rnd;
        final int r0, size;
        final byte[] grid;
        final List<SettlementPlan.Building> buildings = new ArrayList<>();
        final List<SettlementPlan.Road> roads = new ArrayList<>();
        final List<SettlementPlan.Plaza> plazas = new ArrayList<>();
        final List<BlockPos> lamps = new ArrayList<>();
        final Map<BuildingKind, Integer> program = new EnumMap<>(BuildingKind.class);
        int workshopIndex;

        Ctx(SettlementSite site, UnderdeepTerrain t, long seed) {
            this.site = site;
            this.t = t;
            this.civ = site.civ();
            this.rnd = new Random(seed);
            this.r0 = site.radius() + 24;
            this.size = r0 * 2 + 1;
            this.grid = new byte[size * size];
        }

        int idx(int x, int z) {
            int gx = x - site.x() + r0, gz = z - site.z() + r0;
            if (gx < 0 || gz < 0 || gx >= size || gz >= size) {
                return -1;
            }
            return gx * size + gz;
        }

        int at(int x, int z) {
            int i = idx(x, z);
            return i < 0 ? BUILT : grid[i];
        }

        void mark(int x, int z, int v) {
            int i = idx(x, z);
            if (i >= 0 && (grid[i] == FREE || v == BUILT)) {
                grid[i] = (byte) v;
            }
        }
    }

    // ================================================================== terrain helpers

    /** Ground block height (top solid) from the model; water surfaces count as their water level. */
    static int ground(UnderdeepTerrain t, int x, int z) {
        TerrainColumn c = t.column(x, z);
        return (int) Math.floor(c.upperFloor);
    }

    static double waterLevel(UnderdeepTerrain t, int x, int z) {
        TerrainColumn c = t.column(x, z);
        boolean sea = c.upperFloor < UnderdeepTerrain.SEA_LEVEL - 0.5;
        boolean pond = !Double.isNaN(c.pondLevel) && c.upperFloor < c.pondLevel;
        if (pond && c.pondLava || c.upperLava && (sea || pond)) {
            return Double.POSITIVE_INFINITY; // lava: never build
        }
        if (sea || pond) {
            return pond ? c.pondLevel : UnderdeepTerrain.SEA_LEVEL;
        }
        return Double.NaN;
    }

    // ================================================================== entry point

    public static SettlementPlan plan(SettlementSite site, UnderdeepTerrain t) {
        long seed = site.seed(t.seed());
        Ctx c = new Ctx(site, t, seed);
        String name = Names.settlement(site.civ(), seed);
        String layout = chooseLayout(c);
        program(c);
        int plazaR = switch (site.category()) {
            case ENCAMPMENT -> 4;
            case OUTPOST -> 5;
            case VILLAGE -> 7;
            case TOWN -> 13;
            case CITY -> 16;
        };
        int cy = groundOrWater(t, site.x(), site.z()) ;
        c.plazas.add(new SettlementPlan.Plaza(site.x(), site.z(), plazaR, cy));
        markDisc(c, site.x(), site.z(), plazaR + 1, PLAZA);
        // centre piece
        BuildingKind centre = site.category().atLeast(TOWN) ? LANDMARK : site.category() == ENCAMPMENT ? CAMPFIRE : WELL;
        placeCentre(c, centre, cy);
        // roads
        List<double[]> spokes = roads(c, layout, plazaR);
        // plots along roads, inner first
        plots(c, layout, plazaR);
        // gates at spoke ends (towns and cities) and docks on the shore
        if (site.category().atLeast(TOWN)) {
            gates(c, spokes);
        }
        docks(c);
        lamps(c);
        BlockBox bounds = new BlockBox(site.x() - c.r0, cy - 40, site.z() - c.r0, site.x() + c.r0, cy + 60, site.z() + c.r0);
        return new SettlementPlan(site, name, layout, List.copyOf(c.buildings), List.copyOf(c.roads), List.copyOf(c.plazas),
                List.copyOf(c.lamps), bounds);
    }

    private static int groundOrWater(UnderdeepTerrain t, int x, int z) {
        double wl = waterLevel(t, x, z);
        return Double.isNaN(wl) || Double.isInfinite(wl) ? ground(t, x, z) : (int) Math.floor(wl);
    }

    private static String chooseLayout(Ctx c) {
        SettlementSite s = c.site;
        if (s.category() == ENCAMPMENT) {
            return "camp";
        }
        return switch (c.civ) {
            case TIDE_DWELLERS -> s.waterNearby() ? "waterfront" : "linear";
            case STONEBORN -> "terraced";
            case ROOTFOLK, SPOREFOLK -> c.rnd.nextInt(3) == 0 ? "radial" : "cluster";
            case FORGEKIN -> c.rnd.nextBoolean() ? "linear" : "radial";
            default -> c.rnd.nextInt(3) == 0 ? "linear" : "radial";
        };
    }

    // ================================================================== programme

    private static void add(Ctx c, BuildingKind k, int min, int max) {
        int n = min + (max > min ? c.rnd.nextInt(max - min + 1) : 0);
        if (n > 0) {
            c.program.merge(k, n, Integer::sum);
        }
    }

    private static void program(Ctx c) {
        SettlementCategory cat = c.site.category();
        boolean farming = c.civ != Civilization.FORGEKIN && c.civ != Civilization.GLIMMERKIN || c.rnd.nextBoolean();
        switch (cat) {
            case ENCAMPMENT -> {
                add(c, TENT, 2, 4);
                add(c, STALL, 0, 1);
            }
            case OUTPOST -> {
                add(c, HOUSE_SMALL, 2, 4);
                add(c, HOUSE, 1, 2);
                add(c, WORKSHOP, 1, 2);
                add(c, STOREHOUSE, 1, 1);
                add(c, TOWER, 0, 1);
                add(c, STALL, 1, 2);
                if (farming) {
                    add(c, FARM, 1, 1);
                }
            }
            case VILLAGE -> {
                add(c, HALL, 1, 1);
                add(c, INN, 1, 1);
                add(c, SHOP, 1, 2);
                add(c, STALL, 2, 4);
                add(c, WORKSHOP, 2, 3);
                add(c, KITCHEN, 1, 1);
                add(c, STOREHOUSE, 1, 1);
                add(c, GUARD_POST, 1, 1);
                add(c, TOWER, 0, 1);
                if (farming) {
                    add(c, FARM, 2, 3);
                    add(c, BARN, 1, 1);
                    add(c, PEN, 0, 1);
                }
                add(c, HOUSE_LARGE, 1, 2);
                add(c, HOUSE, 4, 7);
                add(c, HOUSE_SMALL, 3, 6);
            }
            case TOWN -> {
                add(c, HALL, 1, 1);
                add(c, INN, 2, 2);
                add(c, TRADE_HALL, 1, 1);
                add(c, LIBRARY, 1, 1);
                add(c, CLINIC, 1, 1);
                add(c, SHOP, 3, 4);
                add(c, STALL, 4, 8);
                add(c, WORKSHOP, 4, 6);
                add(c, KITCHEN, 1, 2);
                add(c, STOREHOUSE, 2, 2);
                add(c, GUARD_POST, 2, 2);
                add(c, TOWER, 1, 2);
                if (farming) {
                    add(c, FARM, 3, 4);
                    add(c, BARN, 1, 2);
                    add(c, PEN, 1, 2);
                }
                add(c, SHARED_HOUSE, 1, 2);
                add(c, HOUSE_LARGE, 3, 5);
                add(c, HOUSE, 8, 12);
                add(c, HOUSE_SMALL, 6, 9);
            }
            case CITY -> {
                add(c, HALL, 1, 2);
                add(c, INN, 3, 4);
                add(c, TRADE_HALL, 2, 3);
                add(c, LIBRARY, 1, 2);
                add(c, CLINIC, 1, 2);
                add(c, SHOP, 6, 8);
                add(c, STALL, 8, 12);
                add(c, WORKSHOP, 8, 11);
                add(c, KITCHEN, 2, 3);
                add(c, STOREHOUSE, 3, 4);
                add(c, GUARD_POST, 3, 4);
                add(c, TOWER, 2, 4);
                if (farming) {
                    add(c, FARM, 4, 6);
                    add(c, BARN, 2, 3);
                    add(c, PEN, 2, 3);
                }
                add(c, SHARED_HOUSE, 4, 6);
                add(c, HOUSE_LARGE, 6, 9);
                add(c, HOUSE, 14, 20);
                add(c, HOUSE_SMALL, 8, 12);
            }
        }
    }

    /** District of a plot by its distance from the centre (fraction of the radius) and sector. */
    private static String district(Ctx c, double f, double angle) {
        if (c.site.category() == ENCAMPMENT) {
            return "camp";
        }
        double industrial = Hashing.unit(Hashing.hash(c.site.id(), 77)) * Math.PI * 2;
        double da = Math.abs(Math.atan2(Math.sin(angle - industrial), Math.cos(angle - industrial)));
        if (f < 0.38) {
            return "core";
        }
        if (f > 0.74) {
            return "outskirts";
        }
        return da < 0.7 ? "craft" : "residential";
    }

    private static final BuildingKind[] CORE = {LANDMARK, HALL, TRADE_HALL, INN, LIBRARY, CLINIC, SHOP, STALL, KITCHEN, HOUSE_LARGE, SHARED_HOUSE, HOUSE};
    private static final BuildingKind[] CRAFT = {WORKSHOP, STOREHOUSE, KITCHEN, SHOP, HOUSE, HOUSE_SMALL, SHARED_HOUSE, INN};
    private static final BuildingKind[] RESIDENTIAL = {SHARED_HOUSE, HOUSE_LARGE, HOUSE, HOUSE_SMALL, SHOP, INN, CLINIC, LIBRARY, WORKSHOP, KITCHEN};
    private static final BuildingKind[] OUTSKIRTS = {FARM, BARN, PEN, GUARD_POST, TOWER, STOREHOUSE, WORKSHOP, HOUSE_SMALL, HOUSE};
    private static final BuildingKind[] CAMP = {TENT, STALL};

    private static BuildingKind nextKind(Ctx c, String district) {
        BuildingKind[] order = switch (district) {
            case "core" -> CORE;
            case "craft" -> CRAFT;
            case "outskirts" -> OUTSKIRTS;
            case "camp" -> CAMP;
            default -> RESIDENTIAL;
        };
        for (BuildingKind k : order) {
            Integer n = c.program.get(k);
            if (n != null && n > 0) {
                return k;
            }
        }
        // anything left at all (keeps the programme moving when a district is exhausted)
        for (Map.Entry<BuildingKind, Integer> e : c.program.entrySet()) {
            if (e.getValue() > 0 && e.getKey() != LANDMARK) {
                return e.getKey();
            }
        }
        return null;
    }

    // ================================================================== centre, roads

    private static void placeCentre(Ctx c, BuildingKind kind, int cy) {
        int v = c.rnd.nextInt(Architect.VARIANTS);
        Blueprint bp = Architect.design(c.civ, kind, v, "");
        int ox = c.site.x() - bp.width / 2, oz = c.site.z() - bp.depth / 2;
        BlockPos origin = new BlockPos(ox, cy + 1 - (kind == CAMPFIRE || kind == WELL ? 1 : 0), oz);
        if (kind == LANDMARK) {
            origin = new BlockPos(ox, cy + 1, oz);
        }
        // centre pieces sit on the plaza surface (plaza block at cy, building floor at cy)
        origin = new BlockPos(ox, cy, oz);
        addBuilding(c, kind, v, kind == LANDMARK ? "elder" : "", "core", bp, BlockRotation.NONE, origin);
        c.program.computeIfPresent(LANDMARK, (k, n) -> n - 1);
    }

    private static List<double[]> roads(Ctx c, String layout, int plazaR) {
        SettlementSite s = c.site;
        int R = s.radius();
        List<double[]> spokes = new ArrayList<>();
        int n = switch (s.category()) {
            case ENCAMPMENT -> 0;
            case OUTPOST -> 2;
            case VILLAGE -> 3 + c.rnd.nextInt(2);
            case TOWN -> 5 + c.rnd.nextInt(2);
            case CITY -> 7 + c.rnd.nextInt(2);
        };
        double mainW = s.category() == CITY ? 5 : s.category() == TOWN ? 4 : 3;
        double a0 = c.rnd.nextDouble() * Math.PI * 2;
        if (layout.equals("linear") || layout.equals("waterfront")) {
            // a main street through the centre plus cross streets
            double ax = Math.cos(a0), az = Math.sin(a0);
            addRoad(c, s.x() - ax * R, s.z() - az * R, s.x() + ax * R, s.z() + az * R, mainW, true);
            spokes.add(new double[]{s.x() + ax * R, s.z() + az * R, a0});
            spokes.add(new double[]{s.x() - ax * R, s.z() - az * R, a0 + Math.PI});
            int cross = Math.max(1, n / 2);
            for (int i = 1; i <= cross; i++) {
                double off = (i % 2 == 0 ? 1 : -1) * (R * 0.25 + (i - 1) / 2 * R * 0.3);
                double px = s.x() + ax * off, pz = s.z() + az * off;
                double len = R * 0.55;
                addRoad(c, px - az * len, pz + ax * len, px + az * len, pz - ax * len, mainW - 1, false);
            }
        } else if (layout.equals("cluster")) {
            // hubs around the centre joined by bent paths
            int hubs = Math.max(2, n);
            for (int i = 0; i < hubs; i++) {
                double a = a0 + i * Math.PI * 2 / hubs + (c.rnd.nextDouble() - 0.5) * 0.5;
                double d = R * (0.5 + c.rnd.nextDouble() * 0.2);
                double hx = s.x() + Math.cos(a) * d, hz = s.z() + Math.sin(a) * d;
                double bend = a + (c.rnd.nextDouble() - 0.5) * 0.9;
                double mx = s.x() + Math.cos(bend) * d * 0.5, mz = s.z() + Math.sin(bend) * d * 0.5;
                addRoad(c, s.x() + Math.cos(a) * plazaR, s.z() + Math.sin(a) * plazaR, mx, mz, mainW, true);
                addRoad(c, mx, mz, hx, hz, mainW, true);
                double ex = s.x() + Math.cos(a) * R, ez = s.z() + Math.sin(a) * R;
                addRoad(c, hx, hz, ex, ez, mainW - 1, false);
                spokes.add(new double[]{ex, ez, a});
                markDisc(c, (int) hx, (int) hz, 3, PLAZA);
                c.plazas.add(new SettlementPlan.Plaza((int) hx, (int) hz, 3, groundOrWater(c.t, (int) hx, (int) hz)));
            }
        } else {
            for (int i = 0; i < n; i++) {
                double a = a0 + i * Math.PI * 2 / n + (c.rnd.nextDouble() - 0.5) * 0.35;
                double ex = s.x() + Math.cos(a) * R, ez = s.z() + Math.sin(a) * R;
                addRoad(c, s.x() + Math.cos(a) * plazaR, s.z() + Math.sin(a) * plazaR, ex, ez, mainW, true);
                spokes.add(new double[]{ex, ez, a});
            }
        }
        // ring roads for towns and cities (polygons approximating circles)
        if (s.category().atLeast(TOWN)) {
            double[] rings = s.category() == CITY ? new double[]{0.42, 0.74} : new double[]{0.58};
            for (double f : rings) {
                int seg = s.category() == CITY ? 14 : 10;
                double rr = R * f;
                for (int i = 0; i < seg; i++) {
                    double a1 = a0 + i * Math.PI * 2 / seg, a2 = a0 + (i + 1) * Math.PI * 2 / seg;
                    addRoad(c, s.x() + Math.cos(a1) * rr, s.z() + Math.sin(a1) * rr, s.x() + Math.cos(a2) * rr,
                            s.z() + Math.sin(a2) * rr, mainW - 1, false);
                }
            }
        }
        return spokes;
    }

    /** Adds a road with a smoothed height profile (cuts and fills, bridges over water and dips). */
    private static void addRoad(Ctx c, double x1, double z1, double x2, double z2, double width, boolean main) {
        double len = Math.hypot(x2 - x1, z2 - z1);
        if (len < 2) {
            return;
        }
        int n = (int) Math.ceil(len / SettlementPlan.Road.STEP) + 1;
        double[] h = new double[n];
        for (int i = 0; i < n; i++) {
            double t = Math.min(1, i * SettlementPlan.Road.STEP / len);
            int x = (int) Math.round(x1 + (x2 - x1) * t), z = (int) Math.round(z1 + (z2 - z1) * t);
            double wl = waterLevel(c.t, x, z);
            h[i] = Double.isNaN(wl) || Double.isInfinite(wl) ? ground(c.t, x, z) : Math.floor(wl) + 1;
        }
        // limit the grade to 1 block per 2 blocks (2 per sample); average forward and backward passes
        double[] f = h.clone(), b = h.clone();
        for (int i = 1; i < n; i++) {
            f[i] = Math.max(f[i], f[i - 1] - 2);
            f[i] = Math.min(f[i], f[i - 1] + 2);
        }
        for (int i = n - 2; i >= 0; i--) {
            b[i] = Math.max(b[i], b[i + 1] - 2);
            b[i] = Math.min(b[i], b[i + 1] + 2);
        }
        for (int i = 0; i < n; i++) {
            h[i] = (f[i] + b[i]) / 2.0;
        }
        SettlementPlan.Road road = new SettlementPlan.Road(x1, z1, x2, z2, width, h, main);
        c.roads.add(road);
        // rasterize into the occupancy grid
        int minX = (int) Math.floor(Math.min(x1, x2) - width), maxX = (int) Math.ceil(Math.max(x1, x2) + width);
        int minZ = (int) Math.floor(Math.min(z1, z2) - width), maxZ = (int) Math.ceil(Math.max(z1, z2) + width);
        double hw = width / 2.0 + 0.5;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double[] p = road.project(x + 0.5, z + 0.5);
                if (p[1] <= hw * hw) {
                    c.mark(x, z, ROAD);
                }
            }
        }
    }

    private static void markDisc(Ctx c, int x0, int z0, int r, int v) {
        for (int x = x0 - r; x <= x0 + r; x++) {
            for (int z = z0 - r; z <= z0 + r; z++) {
                if ((x - x0) * (x - x0) + (z - z0) * (z - z0) <= r * r) {
                    c.mark(x, z, v);
                }
            }
        }
    }

    // ================================================================== plots

    private static void plots(Ctx c, String layout, int plazaR) {
        SettlementSite s = c.site;
        if (s.category() == ENCAMPMENT) {
            // tents in a ring around the fire
            int n = c.program.getOrDefault(TENT, 0) + c.program.getOrDefault(STALL, 0);
            for (int i = 0; i < n * 3 && nextKind(c, "camp") != null; i++) {
                double a = i * 2.4 + c.rnd.nextDouble();
                double d = plazaR + 5 + c.rnd.nextInt(5);
                double px = s.x() + Math.cos(a) * d, pz = s.z() + Math.sin(a) * d;
                tryPlot(c, px, pz, -Math.cos(a), -Math.sin(a), "camp", null);
            }
            return;
        }
        // walk every road; try both sides at regular intervals (main roads and inner positions first)
        List<SettlementPlan.Road> ordered = new ArrayList<>(c.roads);
        ordered.sort((a, b) -> Double.compare(distToCentre(c, a), distToCentre(c, b)));
        for (int pass = 0; pass < 2; pass++) {
            for (SettlementPlan.Road r : ordered) {
                double len = r.length();
                double ux = (r.x2() - r.x1()) / len, uz = (r.z2() - r.z1()) / len;
                double step = pass == 0 ? 15 : 11;
                for (double d = 6 + pass * 5; d < len - 3; d += step) {
                    double px = r.x1() + ux * d, pz = r.z1() + uz * d;
                    for (int side = -1; side <= 1; side += 2) {
                        double nx = -uz * side, nz = ux * side; // outward normal on this side
                        double off = r.width() / 2.0 + 2;
                        double fx = px + nx * off, fz = pz + nz * off;
                        double f = Math.hypot(fx - s.x(), fz - s.z()) / s.radius();
                        if (f > 1.0) {
                            continue;
                        }
                        String district = district(c, f, Math.atan2(fz - s.z(), fx - s.x()));
                        tryPlot(c, fx, fz, nx, nz, district, r);
                    }
                }
            }
        }
    }

    private static double distToCentre(Ctx c, SettlementPlan.Road r) {
        double mx = (r.x1() + r.x2()) / 2 - c.site.x(), mz = (r.z1() + r.z2()) / 2 - c.site.z();
        return Math.hypot(mx, mz) - (r.main() ? 30 : 0);
    }

    /**
     * Tries to put the next building of a district with its front edge at (fx, fz), facing back along
     * (-nx, -nz) toward the road.
     */
    private static void tryPlot(Ctx c, double fx, double fz, double nx, double nz, String district, SettlementPlan.Road road) {
        BuildingKind kind = nextKind(c, district);
        if (kind == null) {
            return;
        }
        String prof = "";
        if (kind == WORKSHOP) {
            prof = Professions.workshopFor(c.civ, c.workshopIndex);
        } else if (kind == SHOP || kind == STALL) {
            prof = "merchant";
        }
        int v = c.rnd.nextInt(Architect.VARIANTS);
        Blueprint bp = Architect.design(c.civ, kind, v, prof);
        // front of the building faces the road: choose the rotation whose door direction is closest to -n
        BlockRotation rot;
        double ax = -nx, az = -nz;
        if (Math.abs(ax) > Math.abs(az)) {
            rot = ax > 0 ? BlockRotation.CLOCKWISE_90 : BlockRotation.COUNTERCLOCKWISE_90;
        } else {
            rot = az > 0 ? BlockRotation.CLOCKWISE_180 : BlockRotation.NONE;
        }
        int rw = bp.rotatedWidth(rot), rd = bp.rotatedDepth(rot);
        double depth = (rot == BlockRotation.NONE || rot == BlockRotation.CLOCKWISE_180) ? rd : rw;
        // slide outward / sideways until the footprint (+1 margin) is clear of roads, plazas and buildings
        double tx = -nz, tz = nx; // along the road
        int ox = 0, oz = 0;
        boolean found = false;
        int setback = 0;
        search:
        for (int sb : new int[]{0, 1, 2, 3, 5, 7}) {
            for (int lat : new int[]{0, 3, -3, 6, -6}) {
                double cx = fx + nx * (depth / 2.0 + sb) + tx * lat, cz = fz + nz * (depth / 2.0 + sb) + tz * lat;
                int px = (int) Math.round(cx - rw / 2.0), pz = (int) Math.round(cz - rd / 2.0);
                if (clear(c, px, pz, rw, rd)) {
                    ox = px;
                    oz = pz;
                    setback = sb;
                    found = true;
                    break search;
                }
            }
        }
        if (!found) {
            reject("occupied");
            return;
        }
        // terrain fit
        boolean water = false;
        int minG = Integer.MAX_VALUE, maxG = Integer.MIN_VALUE;
        double minCeil = Double.MAX_VALUE;
        List<Integer> gs = new ArrayList<>();
        for (int x = ox; x < ox + rw; x += 2) {
            for (int z = oz; z < oz + rd; z += 2) {
                TerrainColumn col = c.t.column(x, z);
                double wl = waterLevel(c.t, x, z);
                if (Double.isInfinite(wl)) {
                    reject("lava");
                    return;
                }
                int g;
                if (!Double.isNaN(wl)) {
                    water = true;
                    if (wl - col.upperFloor > 9) {
                        reject("deep water");
                        return;
                    }
                    g = (int) Math.floor(wl);
                } else {
                    g = (int) Math.floor(col.upperFloor);
                }
                gs.add(g);
                minG = Math.min(minG, g);
                maxG = Math.max(maxG, g);
                minCeil = Math.min(minCeil, col.upperCeiling);
            }
        }
        boolean aquaticOk = bp.stilts || kind == DOCK;
        if (water && !aquaticOk) {
            reject("water");
            return;
        }
        gs.sort(Integer::compare);
        int median = gs.get(gs.size() / 2);
        double roadH = Double.NaN;
        if (road != null) {
            double[] pr = road.project(fx - nx * (road.width() / 2.0 + 1.5), fz - nz * (road.width() / 2.0 + 1.5));
            roadH = road.heightAt(pr[0]);
        }
        // floor from the building's own ground, kept within reach of the road (gentle driveway ramps)
        int y0 = median;
        if (!Double.isNaN(roadH)) {
            int reach = Math.min(3, 1 + setback);
            y0 = (int) Math.max(Math.floor(roadH) - reach, Math.min(Math.floor(roadH) + reach, y0));
        }
        if (water) {
            y0 = Math.max(y0, maxG + 1);
        }
        boolean terraced = c.civ == Civilization.STONEBORN || c.civ == Civilization.GLIMMERKIN;
        int carve = maxG - y0, fill = y0 - minG;
        if (carve > (terraced ? 8 : 5)) {
            reject("carve");
            return;
        }
        if (fill > (terraced ? 12 : 8)) {
            reject("fill");
            return;
        }
        if (minCeil - y0 < bp.height() + 3) {
            reject("headroom");
            return;
        }
        BlockPos origin = new BlockPos(ox, y0, oz);
        addBuilding(c, kind, v, prof, district, bp, rot, origin);
        c.program.computeIfPresent(kind, (k, n) -> n - 1);
        if (kind == WORKSHOP) {
            c.workshopIndex++;
        }
        // driveway from the entrance to the road
        SettlementPlan.Building b = c.buildings.get(c.buildings.size() - 1);
        if (road != null) {
            double[] pr = road.project(b.entrance().getX() + 0.5, b.entrance().getZ() + 0.5);
            double t = pr[0];
            double rx = road.x1() + (road.x2() - road.x1()) * t, rz = road.z1() + (road.z2() - road.z1()) * t;
            double h0 = y0, h1 = road.heightAt(t);
            double len = Math.hypot(rx - (b.entrance().getX() + 0.5), rz - (b.entrance().getZ() + 0.5));
            int n = (int) Math.ceil(len / SettlementPlan.Road.STEP) + 1;
            double[] hs = new double[Math.max(2, n)];
            for (int i = 0; i < hs.length; i++) {
                hs[i] = h0 + (h1 - h0) * i / (hs.length - 1.0);
            }
            c.roads.add(new SettlementPlan.Road(b.entrance().getX() + 0.5, b.entrance().getZ() + 0.5, rx, rz, 1.6, hs, false));
        }
    }

    private static boolean clear(Ctx c, int ox, int oz, int rw, int rd) {
        for (int x = ox - 1; x <= ox + rw; x++) {
            for (int z = oz - 1; z <= oz + rd; z++) {
                if (c.at(x, z) != FREE || Math.hypot(x - c.site.x(), z - c.site.z()) > c.site.radius() + 4) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void addBuilding(Ctx c, BuildingKind kind, int v, String prof, String district, Blueprint bp, BlockRotation rot,
                                    BlockPos origin) {
        int rw = bp.rotatedWidth(rot), rd = bp.rotatedDepth(rot);
        BlockPos entrance = origin;
        for (Blueprint.Marker m : bp.markers()) {
            if (m.type() == Blueprint.MarkerType.ENTRANCE) {
                entrance = origin.add(bp.transform(m.pos().getX(), m.pos().getY(), m.pos().getZ(), rot));
                break;
            }
        }
        BlockBox box = new BlockBox(origin.getX() - 1, origin.getY() - 12, origin.getZ() - 1, origin.getX() + rw, origin.getY() + bp.height() + 2,
                origin.getZ() + rd);
        c.buildings.add(new SettlementPlan.Building(c.buildings.size(), kind, v, prof, district, bp, rot, origin, entrance, box));
        for (int x = origin.getX() - 1; x <= origin.getX() + rw; x++) {
            for (int z = origin.getZ() - 1; z <= origin.getZ() + rd; z++) {
                c.mark(x, z, BUILT);
            }
        }
    }

    // ================================================================== gates, docks, lamps

    private static void gates(Ctx c, List<double[]> spokes) {
        int n = c.program.getOrDefault(GATE, 0) + (c.site.category() == CITY ? 4 : 2);
        for (double[] s : spokes) {
            if (n-- <= 0) {
                break;
            }
            Blueprint bp = Architect.design(c.civ, GATE, 0, "");
            double a = s[2];
            BlockRotation rot = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? BlockRotation.CLOCKWISE_90 : BlockRotation.NONE;
            int rw = bp.rotatedWidth(rot), rd = bp.rotatedDepth(rot);
            double gx = c.site.x() + Math.cos(a) * (c.site.radius() - 6), gz = c.site.z() + Math.sin(a) * (c.site.radius() - 6);
            int ox = (int) Math.round(gx - rw / 2.0), oz = (int) Math.round(gz - rd / 2.0);
            int y = 0;
            boolean ok = true;
            for (SettlementPlan.Road r : c.roads) {
                double[] p = r.project(gx, gz);
                if (p[1] < 9 && r.main()) {
                    y = (int) Math.floor(r.heightAt(p[0]));
                    break;
                }
            }
            if (y == 0) {
                continue;
            }
            TerrainColumn col = c.t.column((int) gx, (int) gz);
            if (col.upperCeiling - y < bp.height() + 3) {
                ok = false;
            }
            for (SettlementPlan.Building b : c.buildings) {
                if (b.box().intersectsXZ(ox - 1, oz - 1, ox + rw, oz + rd)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                addBuilding(c, GATE, 0, "guard", "gate", bp, rot, new BlockPos(ox, y, oz));
            }
        }
    }

    private static void docks(Ctx c) {
        SettlementSite s = c.site;
        if (!s.waterNearby()) {
            return;
        }
        int want = switch (s.category()) {
            case ENCAMPMENT, OUTPOST -> 1;
            case VILLAGE -> c.civ == Civilization.TIDE_DWELLERS ? 3 : 1;
            case TOWN -> c.civ == Civilization.TIDE_DWELLERS ? 5 : 2;
            case CITY -> c.civ == Civilization.TIDE_DWELLERS ? 8 : 3;
        };
        int placed = 0;
        for (int i = 0; i < 48 && placed < want; i++) {
            double a = i * 2.399963;
            double d = s.radius() * (0.35 + (i % 7) / 10.0);
            int x = (int) Math.round(s.x() + Math.cos(a) * d), z = (int) Math.round(s.z() + Math.sin(a) * d);
            if (!Double.isNaN(waterLevel(c.t, x, z))) {
                continue;
            }
            // shore: water 5 blocks outward
            double ux = Math.cos(a), uz = Math.sin(a);
            double wl = waterLevel(c.t, (int) Math.round(x + ux * 5), (int) Math.round(z + uz * 5));
            if (Double.isNaN(wl) || Double.isInfinite(wl)) {
                continue;
            }
            Blueprint bp = Architect.design(c.civ, DOCK, i, "fisher");
            // the dock runs outward: its back (+z local) points into the water
            BlockRotation rot;
            if (Math.abs(ux) > Math.abs(uz)) {
                rot = ux > 0 ? BlockRotation.COUNTERCLOCKWISE_90 : BlockRotation.CLOCKWISE_90;
            } else {
                rot = uz > 0 ? BlockRotation.NONE : BlockRotation.CLOCKWISE_180;
            }
            int rw = bp.rotatedWidth(rot), rd = bp.rotatedDepth(rot);
            int ox = (int) Math.round(x + ux * (bp.depth / 2.0) - rw / 2.0), oz = (int) Math.round(z + uz * (bp.depth / 2.0) - rd / 2.0);
            boolean free = true;
            for (int xx = ox - 1; xx <= ox + rw && free; xx++) {
                for (int zz = oz - 1; zz <= oz + rd && free; zz++) {
                    free = c.at(xx, zz) == FREE;
                }
            }
            if (!free) {
                continue;
            }
            int y = (int) Math.floor(wl) + 1;
            if (Math.abs(ground(c.t, x, z) - y) > 3) {
                continue;
            }
            addBuilding(c, DOCK, i, "fisher", "harbour", bp, rot, new BlockPos(ox, y, oz));
            placed++;
            // connect the dock to the nearest road
            SettlementPlan.Building b = c.buildings.get(c.buildings.size() - 1);
            SettlementPlan.Road best = null;
            double bestD = Double.MAX_VALUE, bestT = 0;
            for (SettlementPlan.Road r : c.roads) {
                double[] p = r.project(b.entrance().getX(), b.entrance().getZ());
                if (p[1] < bestD && r.width() >= 2) {
                    bestD = p[1];
                    best = r;
                    bestT = p[0];
                }
            }
            if (best != null && bestD < 60 * 60) {
                double rx = best.x1() + (best.x2() - best.x1()) * bestT, rz = best.z1() + (best.z2() - best.z1()) * bestT;
                double h1 = best.heightAt(bestT);
                double len = Math.sqrt(bestD);
                int n = (int) Math.ceil(len / SettlementPlan.Road.STEP) + 1;
                double[] hs = new double[Math.max(2, n)];
                for (int k = 0; k < hs.length; k++) {
                    hs[k] = y + (h1 - y) * k / (hs.length - 1.0);
                }
                c.roads.add(new SettlementPlan.Road(b.entrance().getX() + 0.5, b.entrance().getZ() + 0.5, rx, rz, 2.0, hs, false));
            }
        }
    }

    private static void lamps(Ctx c) {
        for (SettlementPlan.Road r : c.roads) {
            if (r.width() < 2.5) {
                continue;
            }
            double len = r.length();
            double ux = (r.x2() - r.x1()) / len, uz = (r.z2() - r.z1()) / len;
            for (double d = 4; d < len; d += 11) {
                double px = r.x1() + ux * d, pz = r.z1() + uz * d;
                int side = ((int) d / 11) % 2 == 0 ? 1 : -1;
                double off = r.width() / 2.0 + 0.6;
                int x = (int) Math.floor(px - uz * side * off), z = (int) Math.floor(pz + ux * side * off);
                if (c.at(x, z) == FREE) {
                    int y = (int) Math.floor(r.heightAt(d / len));
                    c.lamps.add(new BlockPos(x, y, z));
                    c.mark(x, z, BUILT);
                }
            }
        }
        for (SettlementPlan.Plaza p : c.plazas) {
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + Math.PI / 4;
                int x = (int) Math.round(p.x() + Math.cos(a) * (p.radius() + 0.5)), z = (int) Math.round(p.z() + Math.sin(a) * (p.radius() + 0.5));
                c.lamps.add(new BlockPos(x, p.y(), z));
            }
        }
    }
}
