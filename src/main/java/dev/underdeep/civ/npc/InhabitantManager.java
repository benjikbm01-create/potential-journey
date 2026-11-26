package dev.underdeep.civ.npc;

import dev.underdeep.Underdeep;
import dev.underdeep.civ.arch.Blueprint;
import dev.underdeep.civ.settlement.SettlementMap;
import dev.underdeep.civ.settlement.SettlementPlan;
import dev.underdeep.civ.settlement.SettlementSite;
import dev.underdeep.civ.settlement.Settlements;
import dev.underdeep.config.UnderdeepConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tiered inhabitant simulation (Stage 4): every registered inhabitant exists as a persistent record; near
 * players, up to a budget per settlement and per world, records are embodied as entities at the place their
 * daily routine puts them. Entities are never saved; when players leave they return to their records.
 * Deaths are permanent (no resurrection by reloading), identities are stable, and trades restock daily.
 */
public final class InhabitantManager {
    public static EntityType<InhabitantEntity> INHABITANT;
    private static final Map<ServerWorld, InhabitantManager> MANAGERS = new WeakHashMap<>();

    private final ServerWorld world;
    /** Active entities: settlement id → record index → entity. */
    final Map<Long, Map<Integer, InhabitantEntity>> active = new HashMap<>();
    public int spawned, returned, deaths;

    private InhabitantManager(ServerWorld world) {
        this.world = world;
    }

    public static void init() {
        INHABITANT = Registry.register(Registries.ENTITY_TYPE, Identifier.of(Underdeep.MOD_ID, "inhabitant"),
                EntityType.Builder.create(InhabitantEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.95f).maxTrackingRange(10).build());
        FabricDefaultAttributeRegistry.register(INHABITANT, InhabitantEntity.createAttributes());
        ServerTickEvents.END_WORLD_TICK.register(w -> {
            InhabitantManager m = get(w);
            if (m != null) {
                m.tick();
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((e, w) -> {
            if (e instanceof InhabitantEntity ie) {
                InhabitantManager m = get(w);
                if (m != null) {
                    m.forget(ie);
                }
            }
        });
    }

    @Nullable
    public static InhabitantManager get(World w) {
        if (!(w instanceof ServerWorld sw) || w.getRegistryKey() != World.OVERWORLD || Settlements.of(sw) == null) {
            return null;
        }
        synchronized (MANAGERS) {
            return MANAGERS.computeIfAbsent(sw, InhabitantManager::new);
        }
    }

    private void forget(InhabitantEntity e) {
        Map<Integer, InhabitantEntity> m = active.get(e.settlementId);
        if (m != null && m.get(e.recordIndex) == e) {
            m.remove(e.recordIndex);
            if (e.isAlive()) {
                returned++;
            }
        }
    }

    public int activeCount() {
        int n = 0;
        for (Map<Integer, InhabitantEntity> m : active.values()) {
            n += m.size();
        }
        return n;
    }

    public java.util.Collection<InhabitantEntity> activeEntities(long settlement) {
        Map<Integer, InhabitantEntity> m = active.get(settlement);
        return m == null ? java.util.List.of() : m.values();
    }

    public int activeIn(long settlement) {
        Map<Integer, InhabitantEntity> m = active.get(settlement);
        return m == null ? 0 : m.size();
    }

    // ================================================================== tick

    private void tick() {
        if (world.getTime() % 20 != 7) {
            return;
        }
        SettlementMap map = Settlements.of(world);
        if (map == null) {
            return;
        }
        UnderdeepConfig cfg = UnderdeepConfig.get();
        List<ServerPlayerEntity> players = world.getPlayers(p -> !p.isSpectator());
        Set<Long> wanted = new HashSet<>();
        for (ServerPlayerEntity p : players) {
            for (SettlementSite s : map.sitesNear(p.getBlockX(), p.getBlockZ(), 175 + cfg.inhabitantActivationDistance)) {
                if (s.contains(p.getBlockX(), p.getBlockZ(), cfg.inhabitantActivationDistance)) {
                    wanted.add(s.id());
                }
                if (s.contains(p.getBlockX(), p.getBlockZ(), 0)) {
                    discover(p, map, s);
                }
            }
        }
        // return inhabitants of settlements nobody is near
        for (Long id : new ArrayList<>(active.keySet())) {
            if (!wanted.contains(id)) {
                for (InhabitantEntity e : new ArrayList<>(active.get(id).values())) {
                    e.discard();
                }
                active.remove(id);
            }
        }
        for (Long id : wanted) {
            SettlementSite s = map.byId(id);
            if (s != null) {
                populate(map, s, players, cfg);
            }
        }
    }

    /** Embodies the inhabitants nearest to players, within the budgets. */
    public void populate(SettlementMap map, SettlementSite site, List<? extends PlayerEntity> players, UnderdeepConfig cfg) {
        SettlementPlan plan = map.plan(site);
        SettlementState state = SettlementState.get(world);
        SettlementRecord rec = state.getOrCreate(plan, world.getSeed(), world.getTime());
        Map<Integer, InhabitantEntity> mine = active.computeIfAbsent(site.id(), k -> new HashMap<>());
        int budget = Math.min(cfg.maxActiveInhabitantsPerSettlement, cfg.maxActiveInhabitantsTotal - activeCount() + mine.size());
        if (mine.size() >= budget) {
            return;
        }
        List<BlockPos> gathers = gatherPoints(plan);
        List<Object[]> candidates = new ArrayList<>();
        for (SettlementRecord.InhabitantRecord r : rec.inhabitants) {
            if (!r.alive || mine.containsKey(r.index) || r.bed == null) {
                continue;
            }
            BlockPos spot = r.work != null ? r.work : r.bed;
            double best = Double.MAX_VALUE;
            for (PlayerEntity p : players) {
                best = Math.min(best, p.getBlockPos().getSquaredDistance(spot));
            }
            candidates.add(new Object[]{best, r});
        }
        candidates.sort((a, b) -> Double.compare((Double) a[0], (Double) b[0]));
        for (Object[] c : candidates) {
            if (mine.size() >= budget) {
                break;
            }
            SettlementRecord.InhabitantRecord r = (SettlementRecord.InhabitantRecord) c[1];
            InhabitantEntity e = INHABITANT.create(world);
            if (e == null) {
                break;
            }
            BlockPos gather = gathers.isEmpty() ? null : gathers.get((int) Math.floorMod(r.appearance >> 8, (long) gathers.size()));
            e.bind(rec, r, gather);
            BlockPos at = e.spotFor(e.activity());
            if (at == null || !world.isChunkLoaded(at.getX() >> 4, at.getZ() >> 4)) {
                continue;
            }
            e.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360, 0);
            if (world.spawnEntity(e)) {
                mine.put(r.index, e);
                spawned++;
                if (e.activity() == InhabitantEntity.Activity.SLEEP && at.equals(r.bed)) {
                    e.sleep(r.bed);
                }
            }
        }
    }

    private static List<BlockPos> gatherPoints(SettlementPlan plan) {
        List<BlockPos> out = new ArrayList<>();
        for (SettlementPlan.Building b : plan.buildings()) {
            for (Blueprint.Marker m : b.blueprint().markers()) {
                if (m.type() == Blueprint.MarkerType.GATHER) {
                    out.add(b.world(m.pos()));
                }
            }
        }
        for (SettlementPlan.Plaza p : plan.plazas()) {
            out.add(new BlockPos(p.x() + 2, p.y() + 1, p.z() + 2));
        }
        return out;
    }

    // ================================================================== discovery

    public void discover(ServerPlayerEntity p, SettlementMap map, SettlementSite s) {
        SettlementRecord rec = SettlementState.get(world).getOrCreate(map.plan(s), world.getSeed(), world.getTime());
        if (rec.discoveredBy.add(p.getUuid())) {
            SettlementState.get(world).markDirty();
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("message.underdeep.settlement.subtitle",
                    Text.translatable(rec.civ.translationKey()), Text.translatable("settlement.underdeep." + rec.category.id()))
                    .formatted(Formatting.GRAY)));
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(rec.name).formatted(Formatting.GOLD)));
        }
    }

    // ================================================================== events from entities

    @Nullable
    static SettlementRecord.InhabitantRecord record(InhabitantEntity e) {
        if (!(e.getWorld() instanceof ServerWorld sw)) {
            return null;
        }
        SettlementRecord rec = SettlementState.get(sw).get(e.settlementId);
        if (rec == null || e.recordIndex < 0 || e.recordIndex >= rec.inhabitants.size()) {
            return null;
        }
        return rec.inhabitants.get(e.recordIndex);
    }

    static void applyUses(InhabitantEntity e, TradeOfferList offers) {
        SettlementRecord.InhabitantRecord r = record(e);
        if (r == null) {
            return;
        }
        long day = e.getWorld().getTimeOfDay() / 24000;
        if (r.restockDay != day) {
            r.restockDay = day;
            r.tradeUses = new int[offers.size()];
        }
        for (int i = 0; i < offers.size() && i < r.tradeUses.length; i++) {
            for (int k = 0; k < r.tradeUses[i]; k++) {
                offers.get(i).use();
            }
        }
    }

    static void recordTrade(InhabitantEntity e, TradeOffer offer) {
        SettlementRecord.InhabitantRecord r = record(e);
        if (r == null) {
            return;
        }
        int i = e.getOffers().indexOf(offer);
        if (i >= 0) {
            if (r.tradeUses.length <= i) {
                r.tradeUses = java.util.Arrays.copyOf(r.tradeUses, i + 1);
            }
            r.tradeUses[i]++;
            SettlementState.get((ServerWorld) e.getWorld()).markDirty();
        }
    }

    static void onTalk(InhabitantEntity e, PlayerEntity player) {
        // Stage 5 hook: conversations and memory; Stage 4 greets with name and trade
        SettlementRecord.InhabitantRecord r = record(e);
        if (r != null) {
            player.sendMessage(Text.translatable("message.underdeep.inhabitant.greet", r.name,
                    Text.translatable("profession.underdeep." + r.profession)), true);
        }
    }

    /** Guards nearby take on whoever hurt a resident. */
    static void alarm(InhabitantEntity victim, LivingEntity attacker) {
        if (attacker instanceof InhabitantEntity) {
            return;
        }
        for (InhabitantEntity g : victim.getWorld().getEntitiesByClass(InhabitantEntity.class, victim.getBoundingBox().expand(24),
                InhabitantEntity::isGuard)) {
            if (!(attacker instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()))) {
                g.setTarget(attacker);
            }
        }
    }

    static void onDeath(ServerWorld w, InhabitantEntity e, DamageSource source) {
        InhabitantManager m = get(w);
        SettlementRecord rec = SettlementState.get(w).get(e.settlementId);
        if (rec == null || e.recordIndex < 0 || e.recordIndex >= rec.inhabitants.size()) {
            return;
        }
        SettlementRecord.InhabitantRecord r = rec.inhabitants.get(e.recordIndex);
        r.alive = false;
        r.diedAt = w.getTime();
        if (source.getAttacker() instanceof PlayerEntity) {
            rec.deathsByPlayers++;
        }
        SettlementState.get(w).markDirty();
        if (m != null) {
            m.deaths++;
            Map<Integer, InhabitantEntity> mine = m.active.get(e.settlementId);
            if (mine != null) {
                mine.remove(e.recordIndex);
            }
        }
    }
}
