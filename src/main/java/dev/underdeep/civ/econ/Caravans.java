package dev.underdeep.civ.econ;

import dev.underdeep.civ.npc.Companions;
import dev.underdeep.civ.npc.InhabitantEntity;
import dev.underdeep.civ.npc.InhabitantManager;
import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.npc.SettlementState;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.wildlife.UnderdeepCreature;
import dev.underdeep.wildlife.WildlifeRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Physical caravans (Stage 6). A moving shipment near players is embodied where its abstract journey has reached:
 * the trader (the actual origin inhabitant), guards and pack animals, spawned out of the nearest player's view
 * distance (40–96 blocks away). They walk the shipment's route; their progress is written back to the same
 * shipment record, and they vanish again (as the abstract journey) when players are far. Cargo never lives on
 * the entities: it is the shipment's. If the trader is killed, the shipment is lost and part of its cargo spills.
 * Boats travel abstractly only (documented).
 */
public final class Caravans {
    public static final String TAG = "underdeep_caravan";
    private static final Map<Long, List<Entity>> ACTIVE = new HashMap<>();
    private static final Map<Long, long[]> STUCK = new HashMap<>();
    static boolean spawning;

    private Caravans() {
    }

    public static boolean embodied(long shipment) {
        return ACTIVE.containsKey(shipment);
    }

    public static int active() {
        return ACTIVE.size();
    }

    static void init() {
        ServerEntityEvents.ENTITY_LOAD.register((e, w) -> {
            if (!spawning && e.getCommandTags().contains(TAG)) {
                e.discard(); // caravans are never restored from chunks: the shipment re-embodies them
            }
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> entity.getCommandTags().contains(TAG)
                && !(entity instanceof InhabitantEntity) ? ActionResult.FAIL : ActionResult.PASS);
    }

    @Nullable
    static Shipment shipmentOf(InhabitantEntity e) {
        if (e.caravan == 0 || !(e.getWorld() instanceof ServerWorld sw)) {
            return null;
        }
        return EconomyState.get(sw).shipments.get(e.caravan);
    }

    /** Where a caravan member should walk next: 20 blocks further along the route. */
    @Nullable
    public static BlockPos waypoint(InhabitantEntity e) {
        Shipment s = shipmentOf(e);
        if (s == null) {
            return null;
        }
        double ahead = Math.min(s.length, s.progress + 20);
        double[] p = s.status == Shipment.Status.RETURNING ? s.positionAt(s.length - ahead) : s.positionAt(ahead);
        return BlockPos.ofFloored(p[0], p[1], p[2]);
    }

    static void tick(EconomyManager mgr, EconomyState st) {
        ServerWorld w = mgr.world;
        if (w.getTime() % 20 != 9) {
            return;
        }
        UnderdeepConfig cfg = UnderdeepConfig.get();
        // keep or retire embodied caravans
        for (Long id : new ArrayList<>(ACTIVE.keySet())) {
            Shipment s = st.shipments.get(id);
            List<Entity> ents = ACTIVE.get(id);
            Entity leader = ents.isEmpty() ? null : ents.get(0);
            if (s == null || !s.moving() || leader == null || leader.isRemoved() && !(leader instanceof InhabitantEntity ie && ie.isDead())) {
                retire(id);
                continue;
            }
            if (leader instanceof InhabitantEntity ie && (ie.isDead() || !ie.isAlive())) {
                spill(mgr, st, s, ie);
                retire(id);
                continue;
            }
            if (w.getClosestPlayer(leader, 128) == null || s.progress >= s.length) {
                retire(id);
                continue;
            }
            double prog = project(s, leader.getX(), leader.getZ());
            long[] stuck = STUCK.computeIfAbsent(id, k -> new long[]{w.getTime(), (long) prog});
            if (prog > s.progress + 0.5) {
                s.progress = Math.min(s.length, prog);
                stuck[0] = w.getTime();
                stuck[1] = (long) prog;
            } else if (w.getTime() - stuck[0] > 600 && w.getClosestPlayer(leader, 32) == null) {
                // stuck and unobserved: move the caravan a little further along its route (safe spot)
                double[] p = s.status == Shipment.Status.RETURNING ? s.positionAt(s.length - s.progress - 14) : s.positionAt(s.progress + 14);
                BlockPos spot = ground(w, BlockPos.ofFloored(p[0], p[1], p[2]));
                if (spot != null) {
                    for (Entity e : ents) {
                        e.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, e.getYaw(), 0);
                    }
                    s.progress = Math.min(s.length, s.progress + 14);
                }
                stuck[0] = w.getTime();
            }
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (!p.isSpectator() && p.squaredDistanceTo(leader) < 24 * 24) {
                    s.escort.merge(p.getUuid(), 20, Integer::sum);
                }
            }
            st.markDirty();
        }
        // embody shipments near players
        for (Shipment s : st.shipments.values()) {
            if (ACTIVE.size() >= cfg.maxActiveCaravans) {
                break;
            }
            if (!s.moving() || s.method == Shipment.Method.BOAT || ACTIVE.containsKey(s.id) || s.progress < 24 || s.progress > s.length - 24) {
                continue;
            }
            double[] pos = s.position();
            if (!w.isChunkLoaded((int) Math.floor(pos[0]) >> 4, (int) Math.floor(pos[2]) >> 4)) {
                continue;
            }
            double nearest = Double.MAX_VALUE;
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (!p.isSpectator()) {
                    nearest = Math.min(nearest, Math.hypot(p.getX() - pos[0], p.getZ() - pos[2]));
                }
            }
            if (nearest < 40 || nearest > 96) {
                continue;
            }
            spawn(mgr, st, s, pos);
        }
    }

    /** Distance along the route of the point nearest to (x, z), in the travel direction. */
    static double project(Shipment s, double x, double z) {
        double best = Double.MAX_VALUE, at = 0, acc = 0;
        for (int i = 0; i + 1 < s.route.size(); i++) {
            double[] a = s.route.get(i), b = s.route.get(i + 1);
            double dx = b[0] - a[0], dz = b[2] - a[2], len = Math.hypot(dx, dz);
            double t = len <= 0 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (z - a[2]) * dz) / (len * len)));
            double px = a[0] + dx * t, pz = a[2] + dz * t;
            double d = Math.hypot(x - px, z - pz);
            if (d < best) {
                best = d;
                at = acc + t * len;
            }
            acc += len;
        }
        return s.status == Shipment.Status.RETURNING ? s.length - at : at;
    }

    @Nullable
    static BlockPos ground(ServerWorld w, BlockPos around) {
        for (int r = 0; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = 6; dy >= -10; dy--) {
                        BlockPos p = around.add(dx * 2, dy, dz * 2);
                        if (w.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4) && Companions.safe(w, p)) {
                            return p;
                        }
                    }
                }
            }
        }
        return null;
    }

    static void spawn(EconomyManager mgr, EconomyState st, Shipment s, double[] pos) {
        ServerWorld w = mgr.world;
        BlockPos at = ground(w, BlockPos.ofFloored(pos[0], pos[1], pos[2]));
        SettlementRecord rec = SettlementState.get(w).get(s.origin);
        if (at == null || rec == null) {
            return;
        }
        List<Entity> ents = new ArrayList<>();
        List<Integer> crew = new ArrayList<>();
        crew.add(s.leader);
        crew.addAll(s.guards);
        spawning = true;
        try {
            for (int idx : crew) {
                InhabitantEntity e = InhabitantManager.INHABITANT.create(w);
                if (e == null) {
                    continue;
                }
                if (idx >= 0 && idx < rec.inhabitants.size() && rec.inhabitants.get(idx).mind != null) {
                    e.bind(rec, rec.inhabitants.get(idx), null, null);
                } else {
                    e.dress(rec.civ, (int) (s.id % 6), "merchant", "Caravan trader");
                }
                e.caravan = s.id;
                e.goalActivity = dev.underdeep.civ.mind.Activity.TRAVEL;
                e.addCommandTag(TAG);
                BlockPos p = ents.isEmpty() ? at : at.add(w.random.nextInt(3) - 1, 0, w.random.nextInt(3) - 1);
                e.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, w.random.nextFloat() * 360, 0);
                if (w.spawnEntity(e)) {
                    ents.add(e);
                }
            }
            if (ents.isEmpty()) {
                return;
            }
            var type = WildlifeRegistry.type("capback");
            for (int i = 0; i < s.animals && type != null; i++) {
                UnderdeepCreature a = type.create(w);
                if (a == null) {
                    continue;
                }
                a.setPersistent();
                a.addCommandTag(TAG);
                a.refreshPositionAndAngles(at.getX() + 0.5 - 2 - i, at.getY(), at.getZ() + 0.5, 0, 0);
                if (w.spawnEntity(a)) {
                    a.attachLeash(ents.get(0), true);
                    ents.add(a);
                }
            }
        } finally {
            spawning = false;
        }
        ACTIVE.put(s.id, ents);
        STUCK.put(s.id, new long[]{w.getTime(), (long) s.progress});
    }

    static void retire(long id) {
        List<Entity> ents = ACTIVE.remove(id);
        STUCK.remove(id);
        if (ents != null) {
            for (Entity e : ents) {
                if (e instanceof MobEntity m) {
                    m.detachLeash(true, false);
                }
                if (!e.isRemoved()) {
                    e.discard();
                }
            }
        }
    }

    /** The trader was killed: the shipment is lost and part of the cargo spills where they fell (exactly once). */
    static void spill(EconomyManager mgr, EconomyState st, Shipment s, InhabitantEntity leader) {
        if (s.status != Shipment.Status.TRAVELLING) {
            return;
        }
        ServerWorld w = mgr.world;
        for (var en : s.cargo.entrySet()) {
            Goods.Good g = Goods.get(en.getKey());
            int n = (int) Math.floor(en.getValue() * 0.5);
            while (g != null && n > 0) {
                int k = Math.min(64, n);
                w.spawnEntity(new ItemEntity(w, leader.getX(), leader.getY() + 0.5, leader.getZ(), g.stack(k)));
                n -= k;
            }
        }
        Trade.lose(mgr, st, s, w.getTime(), "the caravan was attacked and its trader killed");
        Economy d = st.economies.get(s.dest);
        EconomySim.Ctx dc = d == null ? null : mgr.ctx(d);
        if (dc != null) {
            Daily.event(dc, "CARAVAN_LOST", "s:" + s.id, "the caravan bound for " + d.name + " never arrived", 24000L * 3);
        }
    }

    public static boolean isCaravan(Entity e) {
        return e.getCommandTags().contains(TAG);
    }

    static boolean unused(PlayerEntity p) {
        return p == null;
    }
}
