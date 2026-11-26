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

    public final Society society;

    private InhabitantManager(ServerWorld world) {
        this.world = world;
        this.society = new Society(world, this);
    }

    public boolean isActive(long settlement, int index) {
        Map<Integer, InhabitantEntity> m = active.get(settlement);
        return m != null && m.containsKey(index);
    }

    /** Every active inhabitant thinks now (tests and diagnostics). */
    public void thinkAll() {
        for (Map.Entry<Long, Map<Integer, InhabitantEntity>> en : active.entrySet()) {
            SettlementRecord rec = SettlementState.get(world).get(en.getKey());
            if (rec == null) {
                continue;
            }
            for (InhabitantEntity e : new ArrayList<>(en.getValue().values())) {
                if (e.isAlive() && e.recordIndex >= 0 && e.recordIndex < rec.inhabitants.size()) {
                    society.think(e, rec, rec.inhabitants.get(e.recordIndex));
                }
            }
        }
    }

    static boolean spawningBubble;

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
        DialoguePayloads.init();
        CompanionCommand.init();
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> Dialogue.close(h.player));
        ServerEntityEvents.ENTITY_LOAD.register((e, w) -> {
            // speech bubbles are transient: any left over from a crash are removed when loaded
            if (!spawningBubble && e.getCommandTags().contains(Society.BUBBLE_TAG) && !e.hasVehicle()) {
                e.discard();
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
            SettlementRecord rec = SettlementState.get(world).get(e.settlementId);
            Places pl = rec == null ? null : society.places(rec);
            if (pl != null) {
                pl.release(e.recordIndex);
            }
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
        society.tickBubbles();
        // active tier: each embodied inhabitant thinks once per second, staggered over the second
        long t = world.getTime();
        for (Map.Entry<Long, Map<Integer, InhabitantEntity>> en : active.entrySet()) {
            SettlementRecord rec = null;
            for (InhabitantEntity e : new ArrayList<>(en.getValue().values())) {
                if (!e.isAlive() || (e.getId() + t) % 20 != 0) {
                    continue;
                }
                if (rec == null) {
                    rec = SettlementState.get(world).get(en.getKey());
                    if (rec == null) {
                        break;
                    }
                }
                if (e.recordIndex >= 0 && e.recordIndex < rec.inhabitants.size()) {
                    society.think(e, rec, rec.inhabitants.get(e.recordIndex));
                }
            }
        }
        if (t % 2 == 0) {
            society.abstractStep(UnderdeepConfig.get(), world.getPlayers(p -> !p.isSpectator()));
        }
        if (t % 20 != 7) {
            return;
        }
        SettlementMap map = Settlements.of(world);
        if (map == null) {
            return;
        }
        UnderdeepConfig cfg = UnderdeepConfig.get();
        List<ServerPlayerEntity> players = world.getPlayers(p -> true); // spectators observe too (no discovery)
        Set<Long> wanted = new HashSet<>();
        for (ServerPlayerEntity p : players) {
            for (SettlementSite s : map.sitesNear(p.getBlockX(), p.getBlockZ(), 175 + cfg.inhabitantActivationDistance)) {
                if (s.contains(p.getBlockX(), p.getBlockZ(), cfg.inhabitantActivationDistance)) {
                    wanted.add(s.id());
                }
                if (!p.isSpectator() && s.contains(p.getBlockX(), p.getBlockZ(), 0)) {
                    discover(p, map, s);
                }
            }
        }
        // return inhabitants of settlements nobody is near (companions travelling with a player stay with them;
        // a companion sent home keeps walking until no player is close enough to see it go)
        SettlementState st = SettlementState.get(world);
        for (Long id : new ArrayList<>(active.keySet())) {
            if (!wanted.contains(id)) {
                SettlementRecord rec = st.get(id);
                Map<Integer, InhabitantEntity> mine = active.get(id);
                for (InhabitantEntity e : new ArrayList<>(mine.values())) {
                    SettlementRecord.InhabitantRecord r = rec == null || e.recordIndex >= rec.inhabitants.size() ? null : rec.inhabitants.get(e.recordIndex);
                    if (r != null && r.mind != null && r.mind.companionOf != null
                            && (Companions.travelling(r.mind) || world.getClosestPlayer(e, 32) != null)) {
                        continue;
                    }
                    e.discard();
                    mine.remove(e.recordIndex);
                }
                if (mine.isEmpty()) {
                    active.remove(id);
                }
            }
        }
        companionTick(st);
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
        boolean fresh = !active.containsKey(site.id());
        Map<Integer, InhabitantEntity> mine = active.computeIfAbsent(site.id(), k -> new HashMap<>());
        int before = mine.size();
        int budget = Math.min(cfg.maxActiveInhabitantsPerSettlement, cfg.maxActiveInhabitantsTotal - activeCount() + mine.size());
        if (mine.size() >= budget) {
            return;
        }
        List<BlockPos> gathers = gatherPoints(plan);
        List<Object[]> candidates = new ArrayList<>();
        for (SettlementRecord.InhabitantRecord r : rec.inhabitants) {
            if (!r.present() || mine.containsKey(r.index) || r.bed == null || r.mind != null && (Companions.travelling(r.mind) || r.mind.journey != 0
                    || r.mind.status == dev.underdeep.civ.mind.Mind.Status.AWAY && r.mind.companionOf == null)) {
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
            BlockPos doorstep = r.home >= 0 && r.home < plan.buildings().size() ? plan.buildings().get(r.home).entrance() : null;
            e.bind(rec, r, gather, doorstep);
            // the mind is the authority: bring it up to date, then appear where its current activity takes place
            Places pl = society.places(rec);
            if (pl == null || r.mind == null) {
                continue;
            }
            society.simulate(rec, r, world.getTime());
            dev.underdeep.civ.mind.Activity act = dev.underdeep.civ.mind.Activity.byOrdinal(r.mind.activity);
            if (act == null) {
                act = dev.underdeep.civ.mind.Activity.STROLL;
            }
            BlockPos at = pl.resolve(rec, r, act, r.mind.activityTarget, world.getTime());
            if (at == null) {
                at = doorstep != null ? doorstep : r.bed;
            }
            if (at == null || !world.isChunkLoaded(at.getX() >> 4, at.getZ() >> 4)) {
                continue;
            }
            e.goalPos = at;
            e.goalActivity = act;
            e.setHealth(Math.max(1f, e.getMaxHealth() * r.mind.health));
            e.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360, 0);
            if (world.spawnEntity(e)) {
                mine.put(r.index, e);
                spawned++;
                if ((act == dev.underdeep.civ.mind.Activity.SLEEP || act == dev.underdeep.civ.mind.Activity.NAP
                        || act == dev.underdeep.civ.mind.Activity.RECOVER) && at.equals(r.bed)) {
                    e.sleep(r.bed);
                }
            }
        }
        if (fresh) {
            Underdeep.LOGGER.info("Settlement {} ({}): {} of {} inhabitants present ({} candidates, time {})", rec.name, rec.civ.id(),
                    mine.size() - before, rec.alive(), candidates.size(), world.getTimeOfDay() % 24000);
        }
    }

    /** Embodies travelling companions near their owners; they wait (abstractly) while the owner is away. */
    private void companionTick(SettlementState st) {
        long now = world.getTime();
        for (SettlementRecord rec : st.all().values()) {
            for (SettlementRecord.InhabitantRecord r : rec.inhabitants) {
                if (!r.alive || r.mind == null || r.mind.companionOf == null || !Companions.travelling(r.mind)) {
                    continue;
                }
                dev.underdeep.civ.mind.Mind m = r.mind;
                Map<Integer, InhabitantEntity> mine = active.get(rec.id);
                InhabitantEntity e = mine == null ? null : mine.get(r.index);
                ServerPlayerEntity o = world.getServer().getPlayerManager().getPlayer(m.companionOf);
                boolean present = o != null && o.getWorld() == world && !o.isSpectator() && o.isAlive();
                boolean travelling = m.status == dev.underdeep.civ.mind.Mind.Status.AWAY && now < m.recoverUntil;
                if (!present || travelling && e != null && e.squaredDistanceTo(o) > 40 * 40) {
                    if (e != null) {
                        e.discard();
                        mine.remove(r.index);
                    }
                    continue;
                }
                if (e != null || m.status == dev.underdeep.civ.mind.Mind.Status.RECOVERING || travelling) {
                    continue;
                }
                if (m.status == dev.underdeep.civ.mind.Mind.Status.AWAY) {
                    m.status = dev.underdeep.civ.mind.Mind.Status.ACTIVE;
                }
                BlockPos at = Companions.safeSpot(world, o, true, new java.util.Random());
                InhabitantEntity ne = at == null ? null : INHABITANT.create(world);
                if (ne == null) {
                    continue;
                }
                ne.bind(rec, r, null, null);
                ne.companionOwner = o;
                ne.setHealth(Math.max(1f, ne.getMaxHealth() * m.health));
                ne.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, o.getYaw() + 180, 0);
                if (world.spawnEntity(ne)) {
                    active.computeIfAbsent(rec.id, k -> new HashMap<>()).put(r.index, ne);
                    spawned++;
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
    public static SettlementRecord.InhabitantRecord record(InhabitantEntity e) {
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
        InhabitantManager mgr = get(victim.getWorld());
        if (mgr != null) {
            mgr.society.attacked(victim, attacker);
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
        if (m != null) {
            m.society.died(rec, e, r, source.getAttacker());
        }
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
