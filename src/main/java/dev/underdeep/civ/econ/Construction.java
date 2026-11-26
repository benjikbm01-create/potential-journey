package dev.underdeep.civ.econ;

import dev.underdeep.civ.arch.Architect;
import dev.underdeep.civ.arch.Blueprint;
import dev.underdeep.civ.arch.BlueprintPlacer;
import dev.underdeep.civ.arch.BuildingKind;
import dev.underdeep.civ.mind.Activity;
import dev.underdeep.civ.mind.Memory;
import dev.underdeep.civ.npc.InhabitantManager;
import dev.underdeep.civ.npc.Places;
import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.npc.Society;
import dev.underdeep.civ.settlement.SettlementPlan;
import dev.underdeep.civ.settlement.SettlementPlanner;
import dev.underdeep.world.terrain.TerrainColumn;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Autonomous construction (Stage 6). Settlements build Stage 4 blueprints of their own civilization for actual
 * needs. Plots are checked against the plan's buildings and roads, other projects, player property and the
 * physical world (any player-made or non-natural block in the volume rejects or pauses the project). Material
 * goods move from the ledger into the project's site stock; builders' hours advance progress; blocks are placed
 * layer by layer only while the plot's chunks are loaded, at a bounded rate. Completed buildings become homes or
 * workplaces of the settlement. Repairs restore missing blocks of public buildings (filling air only).
 */
public final class Construction {
    private Construction() {
    }

    // ================================================================== hooks into the Stage 5 places

    static void installHooks() {
        Society.EXTRAS = (rec, places) -> {
            Economy e = EconomyManager.economy(rec.id);
            if (e == null) {
                return;
            }
            for (Economy.Extra x : e.extras) {
                try {
                    places.addBuilding(Economy.EXTRA_BASE + x.id, BuildingKind.valueOf(x.kind), x.entrance);
                } catch (IllegalArgumentException ignored) {
                    // unknown kind
                }
            }
            places.constructionSite = site(e);
            places.celebrationUntil = e.gatheringUntil;
        };
    }

    @Nullable
    static BlockPos site(Economy e) {
        for (Project p : e.projects) {
            if (p.status == Project.Status.BUILDING) {
                return p.origin.add(1, 0, -1);
            }
        }
        return null;
    }

    static void refreshPlaces(EconomySim.Ctx c) {
        InhabitantManager im = InhabitantManager.get(c.world);
        if (im != null) {
            im.society.invalidate(c.e.id);
        }
    }

    // ================================================================== proposing

    static int nextId(Economy e) {
        int max = 0;
        for (Project p : e.projects) {
            max = Math.max(max, p.id);
        }
        return max + 1;
    }

    /** Starts a project for a real need if the settlement has capacity and a valid plot. */
    public static boolean propose(EconomyManager mgr, EconomySim.Ctx c, Project.Kind kind, String reason, String profession) {
        Economy e = c.e;
        long active = e.projects.stream().filter(Project::active).count();
        if (active >= c.cfg.maxProjectsPerSettlement || kind.building == null) {
            return false;
        }
        int variant = c.rnd.nextInt(Architect.VARIANTS);
        String prof = profession;
        if (kind == Project.Kind.WORKSHOP && prof.isEmpty()) {
            prof = dev.underdeep.civ.settlement.Professions.workshopFor(e.civ, c.rnd.nextInt(5));
        }
        Blueprint bp = Architect.design(e.civ, kind.building, variant, prof);
        Object[] plot = findPlot(c, bp, kind == Project.Kind.DOCK);
        if (plot == null) {
            e.decide(c.now, "wanted to build a " + kind.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " but found no suitable plot");
            return false;
        }
        Project p = new Project();
        p.id = nextId(e);
        p.kind = kind;
        p.reason = reason;
        p.profession = prof;
        p.variant = variant;
        p.origin = (BlockPos) plot[0];
        p.rotation = (BlockRotation) plot[1];
        materials(bp, p.required);
        double scale = c.cfg.constructionBudget;
        p.required.replaceAll((k, v) -> Math.ceil(v * scale));
        p.laborHours = Math.max(8, bp.blockCount() / 22.0) / Math.max(0.1, c.cfg.constructionSpeed);
        p.budget = 0;
        p.status = Project.Status.SITE;
        p.started = c.now;
        e.projects.add(p);
        while (e.projects.size() > 12) {
            e.projects.remove(0);
        }
        Daily.event(c, "CONSTRUCTION_PLANNED", "p:" + p.id, "they are planning a new " + label(kind) + " in " + e.name + " because " + reason,
                24000L * 3);
        return true;
    }

    static String label(Project.Kind k) {
        return switch (k) {
            case LARGE_HOUSE -> "large house";
            case SMALL_HOUSE -> "cottage";
            case SHARED_HOUSE -> "lodging house";
            case GUARD_POST -> "guard post";
            case REPAIR -> "repair";
            default -> k.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    /** Material units by category (a credible share of the blocks; decoration and plants are not costed). */
    static void materials(Blueprint bp, Map<String, Double> out) {
        bp.forEach((pos, state) -> {
            String cat = category(state);
            if (cat != null) {
                out.merge(cat, 0.6, Double::sum);
            }
        });
        out.replaceAll((k, v) -> Math.ceil(v));
    }

    @Nullable
    static String category(BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty()) {
            return null;
        }
        String id = Registries.BLOCK.getId(s.getBlock()).getPath();
        if (s.isIn(BlockTags.LOGS) || id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem")) {
            return "timber";
        }
        if (s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.FLOWERS) || s.isIn(BlockTags.CROPS) || s.isIn(BlockTags.SAPLINGS) || id.contains("dirt")
                || id.contains("grass") || id.contains("moss") || id.contains("farmland") || id.contains("water") || id.contains("vine")) {
            return null;
        }
        if (id.contains("planks") || s.isIn(BlockTags.WOODEN_STAIRS) || s.isIn(BlockTags.WOODEN_SLABS) || s.isIn(BlockTags.WOODEN_FENCES)
                || s.isIn(BlockTags.WOODEN_DOORS) || s.isIn(BlockTags.WOODEN_TRAPDOORS) || id.contains("ladder")) {
            return "lumber";
        }
        if (id.contains("glass") || id.contains("torch") || id.contains("lantern") || id.contains("brick")) {
            return "building";
        }
        if (id.contains("wool") || id.contains("carpet") || id.endsWith("_bed")) {
            return "fiber";
        }
        if (id.contains("iron") || id.contains("chain") || id.contains("anvil")) {
            return "metal";
        }
        if (id.contains("stone") || id.contains("cobble") || id.contains("deepslate") || id.contains("basalt") || id.contains("tuff")
                || id.contains("andesite") || id.contains("granite") || id.contains("diorite") || id.contains("calcite") || id.contains("blackstone")
                || id.contains("sandstone") || id.contains("jasper") || id.contains("malachite") || id.contains("cryolite") || id.contains("galvanite")) {
            return "stone";
        }
        // any other solid building block (regional stones, crystal, amber, shell...) counts as stone work
        return s.isSolid() && !s.hasBlockEntity() ? "stone" : s.hasBlockEntity() ? "lumber" : null;
    }

    // ================================================================== plots

    /** Finds a valid plot near the settlement: [origin, rotation] or null. */
    @Nullable
    static Object[] findPlot(EconomySim.Ctx c, Blueprint bp, boolean water) {
        int sx = c.site.x(), sz = c.site.z();
        for (double r = c.site.radius() * 0.4; r <= c.site.radius() + 48; r += 5) {
            for (int i = 0; i < 36; i++) {
                double ang = (i + c.e.projects.size() * 7 + c.e.extras.size() * 3) * Math.PI * 2 / 36;
                double cx = sx + Math.cos(ang) * r, cz = sz + Math.sin(ang) * r;
                // face the centre
                double ax = sx - cx, az = sz - cz;
                BlockRotation rot = Math.abs(ax) > Math.abs(az) ? (ax > 0 ? BlockRotation.CLOCKWISE_90 : BlockRotation.COUNTERCLOCKWISE_90)
                        : (az > 0 ? BlockRotation.CLOCKWISE_180 : BlockRotation.NONE);
                int rw = bp.rotatedWidth(rot), rd = bp.rotatedDepth(rot);
                int ox = (int) Math.round(cx - rw / 2.0), oz = (int) Math.round(cz - rd / 2.0);
                if (!clearOfPlan(c, ox, oz, rw, rd)) {
                    continue;
                }
                Integer y0 = terrainFit(c, ox, oz, rw, rd, bp.height(), water);
                if (y0 == null) {
                    continue;
                }
                BlockPos origin = new BlockPos(ox, y0, oz);
                if (!physicallyFree(c.world, origin, rw, rd, bp.height())) {
                    continue;
                }
                return new Object[]{origin, rot};
            }
        }
        return null;
    }

    static boolean overlaps(int ax0, int az0, int ax1, int az1, int bx0, int bz0, int bx1, int bz1) {
        return ax0 <= bx1 && ax1 >= bx0 && az0 <= bz1 && az1 >= bz0;
    }

    static boolean clearOfPlan(EconomySim.Ctx c, int ox, int oz, int rw, int rd) {
        int x0 = ox - 2, z0 = oz - 2, x1 = ox + rw + 1, z1 = oz + rd + 1;
        for (SettlementPlan.Building b : c.plan.buildings()) {
            if (overlaps(x0, z0, x1, z1, b.box().getMinX(), b.box().getMinZ(), b.box().getMaxX(), b.box().getMaxZ())) {
                return false;
            }
        }
        for (Economy.Extra x : c.e.extras) {
            if (overlaps(x0, z0, x1, z1, x.x0, x.z0, x.x1, x.z1)) {
                return false;
            }
        }
        for (Project p : c.e.projects) {
            if (p.active() && p.kind != Project.Kind.REPAIR && overlaps(x0, z0, x1, z1, p.origin.getX() - 2, p.origin.getZ() - 2, p.origin.getX() + 24,
                    p.origin.getZ() + 24)) {
                return false;
            }
        }
        for (EconomyState.Property pr : c.state.properties.values()) {
            if (overlaps(x0, z0, x1, z1, pr.x0 - 4, pr.z0 - 4, pr.x1 + 4, pr.z1 + 4)) {
                return false;
            }
        }
        for (SettlementPlan.Road r : c.plan.roads()) {
            for (int px = x0; px <= x1; px += 2) {
                for (int pz = z0; pz <= z1; pz += 2) {
                    double[] pr = r.project(px + 0.5, pz + 0.5);
                    if (pr[0] >= 0 && pr[0] <= 1 && Math.abs(pr[1]) < r.width() / 2 + 1) {
                        return false;
                    }
                }
            }
        }
        for (SettlementPlan.Plaza p : c.plan.plazas()) {
            if (overlaps(x0, z0, x1, z1, p.x() - p.radius(), p.z() - p.radius(), p.x() + p.radius(), p.z() + p.radius())) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    static Integer terrainFit(EconomySim.Ctx c, int ox, int oz, int rw, int rd, int h, boolean water) {
        var t = dev.underdeep.civ.settlement.Settlements.of(c.world).terrain();
        List<Integer> gs = new ArrayList<>();
        int minG = Integer.MAX_VALUE, maxG = Integer.MIN_VALUE;
        double minCeil = Double.MAX_VALUE;
        boolean wet = false;
        for (int x = ox; x < ox + rw; x += 2) {
            for (int z = oz; z < oz + rd; z += 2) {
                TerrainColumn col = t.column(x, z);
                double wl = SettlementPlanner.waterLevelAt(t, x, z);
                if (Double.isInfinite(wl)) {
                    return null;
                }
                if (!Double.isNaN(wl)) {
                    wet = true;
                }
                int g = (int) Math.floor(Double.isNaN(wl) ? col.upperFloor : wl);
                gs.add(g);
                minG = Math.min(minG, g);
                maxG = Math.max(maxG, g);
                minCeil = Math.min(minCeil, col.upperCeiling);
            }
        }
        if (wet && !water || water && !wet) {
            return null;
        }
        gs.sort(Integer::compare);
        int y0 = gs.get(gs.size() / 2);
        if (maxG - y0 > 4 || y0 - minG > 6 || minCeil - y0 < h + 3 || Math.abs(y0 - c.site.floorY()) > 14) {
            return null;
        }
        return y0;
    }

    /** Natural terrain only (rock, soil, plants, fluids are checked elsewhere): anything built rejects the plot. */
    static boolean natural(BlockState s) {
        if (s.isAir() || s.hasBlockEntity()) {
            return s.isAir();
        }
        String id = Registries.BLOCK.getId(s.getBlock()).getPath();
        for (String made : new String[]{"planks", "brick", "glass", "wool", "carpet", "fence", "door", "torch", "lantern", "rail", "_bed", "slab",
                "stairs", "wall", "concrete", "terracotta", "table", "ladder", "scaffolding", "polished", "chiseled", "cut_", "smooth_", "path",
                "farmland", "bars", "chain", "candle", "sign", "button", "lever", "pressure", "redstone_wire", "hay", "bookshelf", "_block"}) {
            if (id.contains(made)) {
                return id.endsWith("_ore") || id.equals("mud") || id.contains("moss_block") || id.contains("_ore_block");
            }
        }
        return true;
    }

    /** When the plot is loaded, its volume must contain only natural blocks (respects player builds). */
    static boolean physicallyFree(ServerWorld w, BlockPos origin, int rw, int rd, int h) {
        if (!loaded(w, origin, rw, rd)) {
            return true; // checked again before any block is placed
        }
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = -1; x <= rw; x++) {
            for (int z = -1; z <= rd; z++) {
                for (int y = -1; y <= h + 2; y++) {
                    m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (!natural(w.getBlockState(m))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static boolean loaded(ServerWorld w, BlockPos origin, int rw, int rd) {
        return w.isChunkLoaded(origin.getX() - 2 >> 4, origin.getZ() - 2 >> 4) && w.isChunkLoaded(origin.getX() + rw + 2 >> 4, origin.getZ() + rd + 2 >> 4)
                && w.isChunkLoaded(origin.getX() - 2 >> 4, origin.getZ() + rd + 2 >> 4) && w.isChunkLoaded(origin.getX() + rw + 2 >> 4, origin.getZ() - 2 >> 4);
    }

    // ================================================================== daily lifecycle

    public static void daily(EconomyManager mgr, EconomySim.Ctx c) {
        Economy e = c.e;
        for (Project p : new ArrayList<>(e.projects)) {
            switch (p.status) {
                case SITE -> {
                    double budget = p.laborHours * 0.12 * c.cfg.wageRate;
                    if (e.treasuryBalance >= budget + c.cfg.moneyPerCapita) {
                        e.treasuryBalance -= budget;
                        p.budget = budget;
                        Tx.log(c.now, "fund", "treasury", "project " + p.id, budget, label(p.kind));
                        p.status = Project.Status.FUNDED;
                    } else if (c.now - p.started > 24000L * 8) {
                        cancel(c, p, "funds never came together");
                    }
                }
                case FUNDED, MATERIALS -> {
                    p.status = Project.Status.MATERIALS;
                    deliver(c, p);
                    if (p.materialsComplete()) {
                        p.status = Project.Status.BUILDING;
                        Daily.event(c, "CONSTRUCTION_STARTED", "p:" + p.id, "building has started on a new " + label(p.kind) + " in " + e.name,
                                24000L * 3);
                        refreshPlaces(c);
                    } else if (c.now - p.started > 24000L * 6 && !Participation.hasBuildContract(c, p)) {
                        Participation.postBuild(c, p);
                    }
                    if (c.now - p.started > 24000L * 16) {
                        cancel(c, p, "the materials could not be found");
                    }
                }
                case BUILDING -> {
                    if (p.progress() >= 1 && p.placed >= p.total && p.total > 0) {
                        complete(mgr, c, p);
                    } else if (p.progress() >= 1 && p.total == 0 && !loadedPlot(c, p)) {
                        complete(mgr, c, p); // far from players: the building is finished abstractly and placed when its chunks load
                    }
                }
                default -> {
                }
            }
        }
    }

    static boolean loadedPlot(EconomySim.Ctx c, Project p) {
        if (p.kind == Project.Kind.REPAIR) {
            return true;
        }
        Blueprint bp = blueprint(c.e, p);
        return loaded(c.world, p.origin, bp.rotatedWidth(p.rotation), bp.rotatedDepth(p.rotation));
    }

    static Blueprint blueprint(Economy e, Project p) {
        return Architect.design(e.civ, p.kind.building, p.variant, p.profession);
    }

    /** Moves materials from the ledger into the project's site stock (the project now owns them). */
    static void deliver(EconomySim.Ctx c, Project p) {
        for (var en : p.required.entrySet()) {
            double lack = en.getValue() - p.delivered.getOrDefault(en.getKey(), 0.0);
            if (lack <= 0) {
                continue;
            }
            Goods.Category cat = Goods.Category.valueOf(en.getKey().toUpperCase(Locale.ROOT));
            Map<String, Double> taken = new HashMap<>();
            double t = c.e.takeFrom(cat, lack, taken);
            taken.forEach((g, v) -> {
                p.site.merge(g, v, Double::sum);
                c.e.stock(g).consumed += v;
            });
            p.delivered.merge(en.getKey(), t, Double::sum);
        }
    }

    /** Cancels a project: unused site stock and budget return; built-in materials are consumed. */
    static void cancel(EconomySim.Ctx c, Project p, String why) {
        if (!p.active()) {
            return;
        }
        double used = p.status == Project.Status.BUILDING ? p.progress() : 0;
        for (var en : p.site.entrySet()) {
            c.e.put(en.getKey(), en.getValue() * (1 - used));
        }
        p.site.clear();
        c.e.treasuryBalance += p.budget;
        Tx.log(c.now, "refund", "project " + p.id, "treasury", p.budget, why);
        p.budget = 0;
        p.status = Project.Status.CANCELLED;
        p.finished = c.now;
        c.e.decide(c.now, "cancelled the " + label(p.kind) + ": " + why);
        refreshPlaces(c);
    }

    /** Test access: places the site's blocks as the per-tick loop would (bounded number of iterations). */
    public static int placeForTest(EconomyManager mgr, int ticks) {
        for (int i = 0; i < ticks; i++) {
            tickPhysical(mgr, mgr.state());
        }
        return ticks;
    }

    /** Test access: how many of the blueprint's blocks are present in the world at the project's site. */
    public static int countPlacedForTest(ServerWorld w, Economy e, Project p) {
        Blueprint bp = blueprint(e, p);
        int[] n = {0};
        bp.forEach((lp, state) -> {
            BlockPos o = bp.transform(lp.getX(), lp.getY(), lp.getZ(), p.rotation);
            if (!state.isAir() && w.getBlockState(p.origin.add(o)).getBlock() == state.getBlock()) {
                n[0]++;
            }
        });
        return n[0];
    }

    public static void cancelForTest(EconomySim.Ctx c, Project p, String why) {
        cancel(c, p, why);
    }

    /** Builders' hours (called by the hourly economy step): progress and their pay from the project budget. */
    static void labor(EconomySim.Ctx c, double hours) {
        Project p = null;
        for (Project q : c.e.projects) {
            if (q.status == Project.Status.BUILDING) {
                p = q;
                break;
            }
        }
        if (p == null || p.progress() >= 1) {
            return;
        }
        double work = 0;
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (!ir.present() || ir.mind == null || ir.mind.journey != 0 || ir.mind.companionOf != null) {
                continue;
            }
            Activity a = Activity.byOrdinal(ir.mind.activity);
            double h = hours <= 1.5 ? (a == Activity.CONSTRUCT ? hours : 0)
                    : (ir.profession.equals("carpenter") || ir.profession.equals("stonemason") || ir.profession.equals("engineer") || ir.work == null
                    ? hours / 3.0 : 0);
            if (h <= 0) {
                continue;
            }
            double skill = 0.6 + (ir.mind.skills[Recipes.Skill.CONSTRUCTION.ordinal()] & 0xFF) / 100.0 * 0.8;
            work += h * skill * c.cfg.constructionSpeed;
            Economy.Household hh = c.e.households.get(ir.home);
            double pay = Math.min(p.budget, 0.12 * h * c.cfg.wageRate);
            if (hh != null && pay > 0) {
                p.budget -= pay;
                hh.purse += pay;
                c.e.wagesPaid += pay;
            }
            if (c.rnd.nextDouble() < 0.03 * h) {
                int si = Recipes.Skill.CONSTRUCTION.ordinal();
                ir.mind.skills[si] = (byte) Math.min(100, (ir.mind.skills[si] & 0xFF) + 1);
            }
        }
        p.laborDone += work;
    }

    /** Registers the finished building in the settlement (homes, workplaces, places). */
    static void complete(EconomyManager mgr, EconomySim.Ctx c, Project p) {
        Economy e = c.e;
        p.status = Project.Status.COMPLETE;
        p.finished = c.now;
        // leftover budget returns to the treasury; site stock was built in
        e.treasuryBalance += p.budget;
        p.budget = 0;
        p.site.clear();
        if (p.kind == Project.Kind.REPAIR) {
            Daily.event(c, "REPAIRS_DONE", "", "repairs in " + e.name + " are finished", 24000L * 2);
            return;
        }
        Blueprint bp = blueprint(e, p);
        Economy.Extra x = new Economy.Extra();
        x.id = e.nextExtra++;
        x.kind = p.kind.building.name();
        x.variant = p.variant;
        x.rotation = p.rotation;
        x.origin = p.origin;
        x.profession = p.profession;
        int rw = bp.rotatedWidth(p.rotation), rd = bp.rotatedDepth(p.rotation);
        x.x0 = p.origin.getX() - 1;
        x.z0 = p.origin.getZ() - 1;
        x.x1 = p.origin.getX() + rw;
        x.z1 = p.origin.getZ() + rd;
        x.entrance = p.origin;
        for (Blueprint.Marker m : bp.markers()) {
            BlockPos wp = p.origin.add(bp.transform(m.pos().getX(), m.pos().getY(), m.pos().getZ(), p.rotation));
            switch (m.type()) {
                case ENTRANCE -> x.entrance = wp;
                case BED -> x.beds.add(wp);
                case WORKSTATION, MERCHANT, DOCK, FARM, GUARD -> {
                    x.jobs.add(wp);
                    x.jobProfessions.add(m.type() == Blueprint.MarkerType.GUARD ? "guard" : m.type() == Blueprint.MarkerType.FARM
                            ? (p.kind == Project.Kind.FARM ? "farmer" : "rancher") : m.type() == Blueprint.MarkerType.DOCK ? "fisher"
                            : dev.underdeep.civ.settlement.Professions.valid(m.tag()) ? m.tag() : p.profession.isEmpty() ? "merchant" : p.profession);
                }
                default -> {
                }
            }
        }
        e.extras.add(x);
        if (!x.jobs.isEmpty()) {
            Economy.Workplace w = new Economy.Workplace();
            w.building = Economy.EXTRA_BASE + x.id;
            w.kind = x.kind;
            w.positions = x.jobs.size();
            e.workplaces.put(w.building, w);
        }
        e.storageCapacity += p.kind == Project.Kind.STOREHOUSE ? 400 : 30;
        e.growth++;
        refreshPlaces(c);
        Daily.event(c, "BUILDING_COMPLETED", "p:" + p.id, "a new " + label(p.kind) + " was finished in " + e.name, 24000L * 4);
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (ir.present() && ir.mind != null && c.rnd.nextInt(4) == 0) {
                ir.mind.memories.remember(Memory.Type.DISCOVERY, "e:" + e.id, "new " + label(p.kind), c.now, c.cfg.inhabitantMemoryLimit, 0.6);
            }
        }
        if (e.score > 0.6 && (p.kind == Project.Kind.HALL || p.kind == Project.Kind.INN || p.kind == Project.Kind.WELL || c.rnd.nextInt(3) == 0)) {
            Daily.celebrate(c, "the new " + label(p.kind) + " is finished");
        }
        Population.settleNewHomes(mgr, c);
        Daily.leadership(c);
    }

    // ================================================================== physical placement (loaded chunks only)

    private static final Map<String, List<Object[]>> PLANS = new HashMap<>();

    /** Ordered placement list: clearing, foundations and lower layers first. */
    static List<Object[]> placementList(Economy e, Project p, Blueprint bp) {
        return PLANS.computeIfAbsent(e.id + ":" + p.id + ":" + p.origin.asLong(), k -> {
            List<Object[]> list = new ArrayList<>();
            int rw = bp.rotatedWidth(p.rotation), rd = bp.rotatedDepth(p.rotation);
            Map<Long, BlockState> design = new HashMap<>();
            bp.forEach((lp, state) -> {
                BlockPos o = bp.transform(lp.getX(), lp.getY(), lp.getZ(), p.rotation);
                design.put(p.origin.add(o).asLong(), state.rotate(p.rotation));
            });
            for (int y = 1; y <= bp.height() + 2; y++) {
                for (int x = -1; x <= rw; x++) {
                    for (int z = -1; z <= rd; z++) {
                        long key = p.origin.add(x, y, z).asLong();
                        if (!design.containsKey(key)) {
                            list.add(new Object[]{key, Blocks.AIR.getDefaultState()});
                        }
                    }
                }
            }
            List<Map.Entry<Long, BlockState>> blocks = new ArrayList<>(design.entrySet());
            blocks.sort((a, b) -> {
                int ya = BlockPos.unpackLongY(a.getKey()), yb = BlockPos.unpackLongY(b.getKey());
                return ya != yb ? Integer.compare(ya, yb) : Long.compare(a.getKey(), b.getKey());
            });
            for (Map.Entry<Long, BlockState> en : blocks) {
                list.add(new Object[]{en.getKey(), en.getValue()});
            }
            return list;
        });
    }

    /** Every tick: active sites with loaded chunks place their next blocks (bounded per tick). */
    static void tickPhysical(EconomyManager mgr, EconomyState st) {
        ServerWorld w = mgr.world;
        int budget = Math.max(1, dev.underdeep.config.UnderdeepConfig.get().constructionBlocksPerTick);
        for (Economy e : st.economies.values()) {
            for (Project p : e.projects) {
                if (p.kind == Project.Kind.REPAIR) {
                    if (p.status == Project.Status.BUILDING || p.status == Project.Status.COMPLETE && p.placed < p.repairs.size()) {
                        budget -= repairBlocks(w, e, p, budget);
                    }
                    continue;
                }
                boolean pending = p.status == Project.Status.BUILDING || p.status == Project.Status.COMPLETE && (p.total == 0 || p.placed < p.total);
                if (!pending || budget <= 0) {
                    continue;
                }
                Blueprint bp = blueprint(e, p);
                int rw = bp.rotatedWidth(p.rotation), rd = bp.rotatedDepth(p.rotation);
                if (!loaded(w, p.origin, rw, rd)) {
                    continue;
                }
                List<Object[]> list = placementList(e, p, bp);
                p.total = list.size();
                int target = p.status == Project.Status.COMPLETE ? p.total : (int) Math.floor(p.progress() * p.total);
                int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
                while (p.placed < target && budget > 0) {
                    Object[] item = list.get(p.placed);
                    BlockPos pos = BlockPos.fromLong((Long) item[0]);
                    BlockState cur = w.getBlockState(pos);
                    if (!natural(cur) && !cur.equals(item[1])) {
                        // someone built here meanwhile: stop and give the site up rather than overwrite their work
                        EconomySim.Ctx c = mgr.ctx(e);
                        if (c != null) {
                            cancel(c, p, "someone else built on the plot");
                        }
                        break;
                    }
                    if (!cur.getFluidState().isEmpty() && ((BlockState) item[1]).isAir()) {
                        p.placed++;
                        continue; // never drain water or lava
                    }
                    BlockState s = (BlockState) item[1];
                    if (BlueprintPlacer.connects(s)) {
                        s = BlueprintPlacer.connect(w, pos, s, java.util.Map.of());
                    }
                    w.setBlockState(pos, s, flags);
                    p.placed++;
                    budget--;
                }
                if (p.placed >= p.total && p.total > 0 && p.status != Project.Status.CANCELLED) {
                    // final pass: foundations and connections exactly as the Stage 4 placer does (no loot: nothing for free)
                    BlueprintPlacer.place(w, bp, p.origin, p.rotation, null, 0, null);
                    PLANS.remove(e.id + ":" + p.id + ":" + p.origin.asLong());
                }
                st.markDirty();
            }
        }
    }

    // ================================================================== repairs

    /** Checks one public building of an active settlement for missing blocks (air where the design has a block). */
    static boolean damaged(EconomyManager mgr, EconomySim.Ctx c) {
        InhabitantManager im = InhabitantManager.get(c.world);
        if (im == null || im.activeIn(c.e.id) == 0 || c.plan.buildings().isEmpty()) {
            return false;
        }
        SettlementPlan.Building b = c.plan.buildings().get(c.rnd.nextInt(c.plan.buildings().size()));
        if (b.kind().group.equals("residential") || Daily.ownedByPlayer(c, b.index())) {
            return false;
        }
        int[] missing = {0};
        List<Long> where = new ArrayList<>();
        b.blueprint().forEach((lp, state) -> {
            if (state.isAir() || lp.getY() < 0) {
                return;
            }
            BlockPos wp = b.world(lp);
            if (c.world.isChunkLoaded(wp.getX() >> 4, wp.getZ() >> 4) && c.world.getBlockState(wp).isAir()) {
                missing[0]++;
                where.add(wp.asLong());
            }
        });
        if (missing[0] >= 6) {
            c.e.decide(c.now, "noticed " + missing[0] + " missing blocks at the " + b.kind().id());
            pendingRepair.put(c.e.id, new Object[]{b.index(), where});
            return true;
        }
        return false;
    }

    private static final Map<Long, Object[]> pendingRepair = new HashMap<>();

    @SuppressWarnings("unchecked")
    static void proposeRepair(EconomyManager mgr, EconomySim.Ctx c) {
        Object[] pr = pendingRepair.remove(c.e.id);
        if (pr == null) {
            return;
        }
        Project p = new Project();
        p.id = nextId(c.e);
        p.kind = Project.Kind.REPAIR;
        p.target = (Integer) pr[0];
        p.repairs.addAll((List<Long>) pr[1]);
        SettlementPlan.Building b = c.plan.buildings().get(p.target);
        p.origin = b.origin();
        p.rotation = b.rotation();
        p.variant = b.variant();
        p.profession = b.profession();
        for (long l : p.repairs) {
            BlockPos wp = BlockPos.fromLong(l);
            BlockState design = designAt(b, wp);
            String cat = design == null ? null : category(design);
            if (cat != null) {
                p.required.merge(cat, 1.0, Double::sum);
            }
        }
        p.laborHours = Math.max(2, p.repairs.size() / 10.0);
        p.status = Project.Status.SITE;
        p.started = c.now;
        p.reason = "damage";
        c.e.projects.add(p);
        Daily.event(c, "REPAIRS_PLANNED", "p:" + p.id, "they are repairing the " + b.kind().id().replace('_', ' ') + " in " + c.e.name, 24000L * 2);
    }

    @Nullable
    static BlockState designAt(SettlementPlan.Building b, BlockPos wp) {
        BlockState[] out = {null};
        b.blueprint().forEach((lp, state) -> {
            if (out[0] == null && b.world(lp).equals(wp)) {
                out[0] = state.rotate(b.rotation());
            }
        });
        return out[0];
    }

    static int repairBlocks(ServerWorld w, Economy e, Project p, int budget) {
        if (p.target < 0) {
            return 0;
        }
        var map = dev.underdeep.civ.settlement.Settlements.of(w);
        var site = map == null ? null : map.byId(e.id);
        if (site == null) {
            return 0;
        }
        SettlementPlan plan = map.plan(site);
        if (p.target >= plan.buildings().size()) {
            return 0;
        }
        SettlementPlan.Building b = plan.buildings().get(p.target);
        int used = 0;
        int target = p.status == Project.Status.COMPLETE ? p.repairs.size() : (int) Math.floor(p.progress() * p.repairs.size());
        while (p.placed < target && used < budget) {
            BlockPos wp = BlockPos.fromLong(p.repairs.get(p.placed));
            p.placed++;
            if (!w.isChunkLoaded(wp.getX() >> 4, wp.getZ() >> 4) || !w.getBlockState(wp).isAir()) {
                continue; // only fill air: whatever a player put there stays
            }
            BlockState s = designAt(b, wp);
            if (s != null) {
                w.setBlockState(wp, s, Block.NOTIFY_LISTENERS);
                used++;
            }
        }
        return used;
    }
}
