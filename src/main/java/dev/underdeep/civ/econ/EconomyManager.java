package dev.underdeep.civ.econ;

import dev.underdeep.Underdeep;
import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.npc.SettlementState;
import dev.underdeep.civ.settlement.SettlementMap;
import dev.underdeep.civ.settlement.SettlementPlan;
import dev.underdeep.civ.settlement.SettlementSite;
import dev.underdeep.civ.settlement.Settlements;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.ecology.RegionEcology;
import dev.underdeep.world.region.RegionSample;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Drives the Stage 6 world simulation for one world: makes sure settlements near players (and spawn) have records
 * and economies, steps economies round-robin within a per-tick budget (hourly near players, coarser far away,
 * bounded catch-up), runs daily processes on day boundaries, moves shipments and caravans, and places construction
 * blocks in loaded chunks. Never loads chunks.
 */
public final class EconomyManager {
    private static final Map<ServerWorld, EconomyManager> MANAGERS = new WeakHashMap<>();
    final ServerWorld world;
    private final Map<Long, Object[]> regionCache = new HashMap<>();
    private final List<Long> order = new ArrayList<>();
    private int cursor;
    public final Random rnd = new Random();
    public long steps, dailyRuns, lastStepNanos, maxStepNanos;
    private long lastDiplomacyDay = -1;

    private EconomyManager(ServerWorld world) {
        this.world = world;
    }

    public static void init() {
        Goods.load();
        Recipes.load();
        Construction.installHooks();
        ServerTickEvents.END_WORLD_TICK.register(w -> {
            EconomyManager m = get(w);
            if (m != null && UnderdeepConfig.get().economyEnabled) {
                m.tick();
            }
        });
        Participation.init();
        Caravans.init();
    }

    @Nullable
    public static EconomyManager get(World w) {
        if (!(w instanceof ServerWorld sw) || w.getRegistryKey() != World.OVERWORLD || Settlements.of(sw) == null) {
            return null;
        }
        synchronized (MANAGERS) {
            return MANAGERS.computeIfAbsent(sw, EconomyManager::new);
        }
    }

    /** The economy of a settlement in any loaded world (used by hooks without a world reference). */
    @Nullable
    public static Economy economy(long settlement) {
        synchronized (MANAGERS) {
            for (EconomyManager m : MANAGERS.values()) {
                Economy e = m.state().economies.get(settlement);
                if (e != null) {
                    return e;
                }
            }
        }
        return null;
    }

    public EconomyState state() {
        return EconomyState.get(world);
    }

    // ================================================================== context

    @Nullable
    public EconomySim.Ctx ctx(Economy e) {
        SettlementMap map = Settlements.of(world);
        SettlementSite site = map == null ? null : map.byId(e.id);
        SettlementRecord rec = SettlementState.get(world).get(e.id);
        if (site == null || rec == null) {
            return null;
        }
        return ctx(e, rec, map.plan(site), site);
    }

    EconomySim.Ctx ctx(@Nullable Economy e, SettlementRecord rec, SettlementPlan plan, SettlementSite site) {
        EconomySim.Ctx c = new EconomySim.Ctx();
        c.world = world;
        c.state = state();
        c.e = e;
        c.rec = rec;
        c.plan = plan;
        c.site = site;
        c.region = site.region();
        Object[] reg = regionCache.computeIfAbsent(site.id(), k -> {
            SettlementMap map = Settlements.of(world);
            RegionSample s = map.terrain().regions(site.layer()).sample(site.x(), site.z(), new RegionSample());
            long id = s.id().asLong();
            return new Object[]{id, RegionEcology.of(site.region(), id)};
        });
        c.regionId = (Long) reg[0];
        c.eco = (RegionEcology) reg[1];
        c.now = world.getTime();
        c.cfg = UnderdeepConfig.get();
        c.rnd = rnd;
        return c;
    }

    // ================================================================== tick

    private void tick() {
        long t = world.getTime();
        UnderdeepConfig cfg = UnderdeepConfig.get();
        EconomyState st = state();
        if (t % 40 == 13) {
            ensureSettlements(cfg, st);
        }
        if (t % 20 == 3) {
            Trade.tickShipments(this, st, 20);
        }
        if (t / 24000 != lastDiplomacyDay) {
            lastDiplomacyDay = t / 24000;
            Trade.diplomacy(this, st, t);
        }
        Caravans.tick(this, st);
        Construction.tickPhysical(this, st);
        if (order.size() != st.economies.size()) {
            order.clear();
            order.addAll(st.economies.keySet());
            cursor = 0;
        }
        int budget = Math.max(1, cfg.economySettlementsPerTick);
        for (int i = 0; i < budget && !order.isEmpty(); i++) {
            cursor = (cursor + 1) % order.size();
            Economy e = st.economies.get(order.get(cursor));
            if (e == null) {
                continue;
            }
            boolean near = nearPlayers(e, 512);
            long interval = near ? cfg.economyStepTicks : cfg.economyDistantStepTicks;
            if (t - e.lastStep >= interval) {
                advance(e, t, cfg);
            }
        }
    }

    boolean nearPlayers(Economy e, int distance) {
        SettlementMap map = Settlements.of(world);
        SettlementSite s = map == null ? null : map.byId(e.id);
        if (s == null) {
            return false;
        }
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (s.contains(p.getBlockX(), p.getBlockZ(), distance)) {
                return true;
            }
        }
        return false;
    }

    /** Brings an economy up to now: steps of at most 6 h, catch-up capped, then daily processes for crossed days. */
    public void advance(Economy e, long now, UnderdeepConfig cfg) {
        EconomySim.Ctx c = ctx(e);
        if (c == null) {
            e.lastStep = now;
            return;
        }
        long t0 = System.nanoTime();
        double hours = Math.min(cfg.economyMaxCatchUpHours, Math.max(0, now - e.lastStep) / 1000.0);
        e.lastStep = now;
        while (hours > 1e-6) {
            double h = Math.min(6, hours);
            EconomySim.step(c, h);
            hours -= h;
        }
        long day = now / 24000;
        if (e.day < 0) {
            e.day = day;
        }
        int days = 0;
        while (e.day < day && days < 2) {
            e.day++;
            days++;
            Daily.run(this, c);
            dailyRuns++;
        }
        e.day = Math.max(e.day, day - 0);
        steps++;
        lastStepNanos = System.nanoTime() - t0;
        maxStepNanos = Math.max(maxStepNanos, lastStepNanos);
        state().markDirty();
    }

    /** Test/diagnostic helper: brings up to {@code n} settlements near a point into the economy now. */
    public int ensureNear(int x, int z, int radius, int n) {
        SettlementMap map = Settlements.of(world);
        if (map == null) {
            return 0;
        }
        EconomyState st = state();
        SettlementState ss = SettlementState.get(world);
        int made = 0;
        for (SettlementSite s : map.sitesNear(x, z, radius)) {
            if (made >= n) {
                break;
            }
            if (st.economies.containsKey(s.id())) {
                continue;
            }
            SettlementRecord rec = ss.getOrCreate(map.plan(s), world.getSeed(), world.getTime());
            EconomySim.Ctx ctx = ctx(null, rec, map.plan(s), s);
            Economy e = EconomySim.create(ctx);
            st.economies.put(e.id, e);
            Daily.leadership(ctx);
            made++;
        }
        for (Economy e : st.economies.values()) {
            if (st.connectionsOf(e.id).isEmpty()) {
                Trade.connect(this, st, e);
            }
        }
        st.markDirty();
        return made;
    }

    /** Background planner: settlement site resolution and town plans are pure functions of the seed and terrain model,
     *  so they are computed off the server thread (Stage 12: these caused 0.3-0.6 s ticks when a settlement came into
     *  range). Daemon, low priority, one at a time. */
    private static final java.util.concurrent.ExecutorService PLANNER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Underdeep settlement planner");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private java.util.concurrent.CompletableFuture<List<SettlementSite>> pendingScan;

    /** Settlements within the economy radius of players (and spawn) get records and economies (one per call). The site
     *  scan and plan run on the planner thread; only record/economy creation happens here, once the plan is cached. */
    private void ensureSettlements(UnderdeepConfig cfg, EconomyState st) {
        SettlementMap map = Settlements.of(world);
        if (map == null) {
            return;
        }
        if (pendingScan == null) {
            List<int[]> centres = new ArrayList<>();
            BlockPos spawn = world.getSpawnPos();
            centres.add(new int[]{spawn.getX(), spawn.getZ()});
            for (ServerPlayerEntity p : world.getPlayers(p -> !p.isSpectator())) {
                centres.add(new int[]{p.getBlockX(), p.getBlockZ()});
            }
            java.util.Set<Long> have = new java.util.HashSet<>(st.economies.keySet());
            int radius = cfg.economyRadius;
            pendingScan = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                List<SettlementSite> ready = new ArrayList<>();
                for (int[] c : centres) {
                    for (SettlementSite s : map.sitesNear(c[0], c[1], radius)) {
                        if (!have.contains(s.id()) && !ready.contains(s)) {
                            map.plan(s); // cached in the map's concurrent plan cache
                            ready.add(s);
                            if (ready.size() >= 2) {
                                return ready;
                            }
                        }
                    }
                }
                return ready;
            }, PLANNER);
            return;
        }
        if (!pendingScan.isDone()) {
            return;
        }
        List<SettlementSite> ready;
        try {
            ready = pendingScan.join();
        } catch (Exception ex) {
            Underdeep.LOGGER.warn("Settlement planning failed: {}", ex.toString());
            ready = List.of();
        }
        pendingScan = null;
        SettlementState ss = SettlementState.get(world);
        for (SettlementSite s : ready) {
            if (st.economies.containsKey(s.id())) {
                continue;
            }
            SettlementRecord rec = ss.get(s.id());
            if (rec == null) {
                rec = ss.getOrCreate(map.plan(s), world.getSeed(), world.getTime());
            }
            EconomySim.Ctx ctx = ctx(null, rec, map.plan(s), s);
            Economy e = EconomySim.create(ctx);
            st.economies.put(e.id, e);
            Daily.leadership(ctx);
            Trade.connect(this, st, e);
            st.markDirty();
            Underdeep.LOGGER.info("Economy: {} ({} {}) joins the regional economy: {} people, treasury {}", e.name, e.civ.id(),
                    s.category().id(), rec.alive(), String.format(java.util.Locale.ROOT, "%.0f", e.treasuryBalance));
            return; // one settlement per call keeps the cost of a call bounded
        }
    }
}
