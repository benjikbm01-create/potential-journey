package dev.underdeep.wildlife.population;

import dev.underdeep.Underdeep;
import dev.underdeep.block.plant.Env;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.CreatureSpecies.Locomotion;
import dev.underdeep.wildlife.CreatureSpecies.Temperament;
import dev.underdeep.wildlife.UnderdeepCreature;
import dev.underdeep.wildlife.WildlifeBlocks;
import dev.underdeep.wildlife.WildlifeJournal;
import dev.underdeep.wildlife.WildlifeRegistry;
import dev.underdeep.wildlife.ai.CreatureGoals;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.analysis.RegionReport;
import dev.underdeep.world.data.UnderdeepWorldState;
import dev.underdeep.world.region.RegionId;
import dev.underdeep.world.region.RegionMap;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Runtime owner of Underdeep wildlife in one world: regional populations (Tier 3 state), the
 * ledger of materialised wild creatures, spawning near players (Tier 1/2), herds/packs, alarms,
 * aggregate simulation and migration of unloaded regions, and lifecycle hooks (birth, death,
 * taming). Runs only in Underdeep worlds; never force-loads chunks.
 */
public final class WildlifeManager {
    private static final Map<ServerWorld, WildlifeManager> MANAGERS = new WeakHashMap<>();

    private final ServerWorld world;
    private final UnderdeepTerrain terrain;
    private final Map<Long, RegionWildlife> regions = new HashMap<>();
    /** region → species → materialised wild entities currently loaded. */
    private final Map<Long, Map<String, Integer>> active = new HashMap<>();
    private final Set<UnderdeepCreature> loaded = new HashSet<>();
    private final Map<Integer, List<UnderdeepCreature>> herds = new HashMap<>();
    private final Map<Integer, Alarm> alarms = new HashMap<>();
    private final Map<Integer, LivingEntity> packTargets = new HashMap<>();
    private final Random rnd = new Random();
    private int nextHerd = 1;
    private int playerCursor;
    // statistics for diagnostics
    public int spawned, returned, deathsByPlayer, deathsByPredator, birthsLoaded, migrationsLoaded, simSteps, simMigrations;
    public long simNanos;

    private record Alarm(LivingEntity source, long until) {
    }

    private WildlifeManager(ServerWorld world, UnderdeepTerrain terrain) {
        this.world = world;
        this.terrain = terrain;
    }

    @Nullable
    public static WildlifeManager get(World world) {
        if (!(world instanceof ServerWorld sw) || !UnderdeepWorlds.isUnderdeep(sw)) {
            return null;
        }
        return MANAGERS.computeIfAbsent(sw, w -> new WildlifeManager(w, UnderdeepWorlds.terrain(w)));
    }

    public static void init() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof UnderdeepCreature c && get(world) instanceof WildlifeManager m) {
                m.onLoad(c);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof UnderdeepCreature c && get(world) instanceof WildlifeManager m) {
                m.onUnload(c);
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(w -> {
            WildlifeManager m = get(w);
            if (m != null) {
                m.tick();
            }
        });
    }

    // ================================================================= regional state

    public RegionWildlife region(long id) {
        RegionWildlife r = regions.get(id);
        if (r == null) {
            NbtLoad:
            {
                var tag = UnderdeepWorldState.get(world).regionData(id);
                if (tag.contains(RegionWildlife.KEY)) {
                    r = RegionWildlife.fromNbt(tag.getCompound(RegionWildlife.KEY));
                    break NbtLoad;
                }
                r = new RegionWildlife();
            }
            if (!r.initialized) {
                UnderdeepConfig c = UnderdeepConfig.get();
                r.initialize(typeOf(id), id, c.wildlifeDensity, c.rareCreatureFrequency, world.getTime());
            }
            regions.put(id, r);
            save(id, r);
        }
        return r;
    }

    private void save(long id, RegionWildlife r) {
        UnderdeepWorldState st = UnderdeepWorldState.get(world);
        st.regionData(id).put(RegionWildlife.KEY, r.toNbt());
        st.markDirty();
    }

    public RegionType typeOf(long id) {
        if (id == Long.MIN_VALUE) {
            return null; // creature without a home region yet (not wild, or not loaded by the manager)
        }
        RegionId rid = RegionId.fromLong(id);
        return terrain.regions(rid.layer()).typeOf(rid.cellX(), rid.cellZ());
    }

    public Map<Long, RegionWildlife> knownRegions() {
        return regions;
    }

    public int activeCount(long region, String species) {
        return active.getOrDefault(region, Map.of()).getOrDefault(species, 0);
    }

    public int activeTotal() {
        return loaded.size();
    }

    private void activeAdd(long region, String species, int d) {
        Map<String, Integer> m = active.computeIfAbsent(region, k -> new HashMap<>());
        int v = Math.max(0, m.getOrDefault(species, 0) + d);
        if (v == 0) {
            m.remove(species);
        } else {
            m.put(species, v);
        }
    }

    // ================================================================= entity lifecycle

    private void onLoad(UnderdeepCreature c) {
        if (!c.isWild()) {
            return;
        }
        if (c.homeRegion == Long.MIN_VALUE) {
            // created outside the spawner (spawn egg, command, breeding fallback): joins the local population
            RegionReport rr = RegionReport.at(terrain, c.getBlockX(), c.getBlockY(), c.getBlockZ());
            c.initWild(rr.regionId().asLong(), c.getBlockPos(), 0);
            region(c.homeRegion).add(c.species().id, 1);
            save(c.homeRegion, region(c.homeRegion));
        }
        if (loaded.add(c)) {
            activeAdd(c.homeRegion, c.species().id, 1);
            if (c.herdId != 0) {
                herds.computeIfAbsent(c.herdId, k -> new ArrayList<>()).add(c);
            }
        }
    }

    private void onUnload(UnderdeepCreature c) {
        if (loaded.remove(c)) {
            activeAdd(c.homeRegion, c.species().id, -1);
            if (c.getRemovalReason() != net.minecraft.entity.Entity.RemovalReason.KILLED) {
                returned++; // unloaded or despawned: back in the regional pool
            }
            List<UnderdeepCreature> h = herds.get(c.herdId);
            if (h != null) {
                h.remove(c);
                if (h.isEmpty()) {
                    herds.remove(c.herdId);
                    alarms.remove(c.herdId);
                    packTargets.remove(c.herdId);
                }
            }
        }
    }

    public void onDeath(UnderdeepCreature c, DamageSource source) {
        if (!c.isWild() || c.homeRegion == Long.MIN_VALUE) {
            return;
        }
        RegionWildlife r = region(c.homeRegion);
        r.add(c.species().id, -1);
        r.deaths++;
        Entity attacker = source.getAttacker();
        if (attacker instanceof PlayerEntity p) {
            r.playerKills++;
            r.disturbance = Math.min(1.0, r.disturbance + 0.08 * UnderdeepConfig.get().huntingDisturbance);
            deathsByPlayer++;
            if (p instanceof ServerPlayerEntity sp) {
                WildlifeJournal.learn(sp, c.species(), WildlifeJournal.Fact.RESOURCES);
            }
        } else if (attacker instanceof UnderdeepCreature pred) {
            r.predation++;
            deathsByPredator++;
            killSite(c.getPos());
            for (ServerPlayerEntity sp : world.getPlayers(pl -> pl.squaredDistanceTo(c) < 32 * 32)) {
                WildlifeJournal.learn(sp, pred.species(), WildlifeJournal.Fact.FOOD);
            }
        }
        save(c.homeRegion, r);
    }

    /** Named or tamed creatures leave the wild population (they become ordinary persistent entities). */
    public void onLeaveWild(UnderdeepCreature c) {
        if (c.homeRegion != Long.MIN_VALUE && loaded.remove(c)) {
            activeAdd(c.homeRegion, c.species().id, -1);
            RegionWildlife r = region(c.homeRegion);
            r.add(c.species().id, -1);
            save(c.homeRegion, r);
        }
    }

    /** Whether a wild pair may produce offspring now (population and local density caps). */
    public boolean allowBirth(UnderdeepCreature parent) {
        if (!parent.isWild()) {
            return true; // livestock and companions breed like vanilla animals (player-managed)
        }
        UnderdeepConfig cfg = UnderdeepConfig.get();
        RegionWildlife r = region(parent.homeRegion);
        int cap = RegionWildlife.capacity(parent.species(), typeOf(parent.homeRegion), parent.homeRegion, cfg.wildlifeDensity,
                cfg.rareCreatureFrequency, r.disturbance);
        int local = world.getEntitiesByClass(UnderdeepCreature.class, parent.getBoundingBox().expand(16), e -> e.getType() == parent.getType()).size();
        return r.count(parent.species().id) < cap && local < parent.species().groupMax() + 3 && loaded.size() < cfg.maxWildlifeTotal;
    }

    /** Registers a newborn (live birth or hatched egg) in its parents' regional population. */
    public void onBirth(UnderdeepCreature child, UnderdeepCreature parent) {
        if (!parent.isWild()) {
            return;
        }
        child.initWild(parent.homeRegion, parent.home, parent.herdId);
        RegionWildlife r = region(parent.homeRegion);
        r.add(child.species().id, 1);
        r.births++;
        birthsLoaded++;
        save(parent.homeRegion, r);
    }

    // ================================================================= per-creature upkeep (every 20 ticks)

    public void tickCreature(UnderdeepCreature c) {
        if (!c.isWild() || c.isBaby() || c.lowDetail) {
            return;
        }
        // natural breeding when well fed and the population has room
        if (UnderdeepConfig.get().wildlifeReproduction > 0 && c.getBreedingAge() == 0 && !c.isInLove() && c.hunger < 0.35f
                && rnd.nextDouble() < 0.004 * UnderdeepConfig.get().wildlifeReproduction && allowBirth(c)) {
            boolean mate = !world.getEntitiesByClass(UnderdeepCreature.class, c.getBoundingBox().expand(8),
                    e -> e != c && e.getType() == c.getType() && !e.isBaby() && e.getBreedingAge() == 0).isEmpty();
            if (mate) {
                c.setLoveTicks(600);
            }
        }
        // home-region transfer when a creature has clearly settled elsewhere (natural migration on foot)
        if (rnd.nextInt(30) == 0) {
            RegionReport rr = RegionReport.at(terrain, c.getBlockX(), c.getBlockY(), c.getBlockZ());
            long here = rr.regionId().asLong();
            if (here != c.homeRegion && rr.borderDistance() > 16 && c.species().regions().contains(rr.region())) {
                transferLoaded(c, here);
            }
        }
    }

    private void transferLoaded(UnderdeepCreature c, long to) {
        RegionWildlife from = region(c.homeRegion);
        RegionWildlife dst = region(to);
        from.add(c.species().id, -1);
        from.migOut++;
        dst.add(c.species().id, 1);
        dst.migIn++;
        activeAdd(c.homeRegion, c.species().id, -1);
        activeAdd(to, c.species().id, 1);
        save(c.homeRegion, from);
        save(to, dst);
        c.homeRegion = to;
        c.home = c.getBlockPos();
        migrationsLoaded++;
    }

    // ================================================================= herds, alarms, packs

    @Nullable
    public static UnderdeepCreature leaderOf(UnderdeepCreature c) {
        WildlifeManager m = get(c.getWorld());
        if (m == null) {
            return null;
        }
        List<UnderdeepCreature> h = m.herds.get(c.herdId);
        if (h == null) {
            return null;
        }
        UnderdeepCreature best = null;
        for (UnderdeepCreature e : h) {
            if (e.isAlive() && !e.isBaby() && (best == null || e.getId() < best.getId())) {
                best = e;
            }
        }
        return best;
    }

    public static void alarmHerd(UnderdeepCreature c, @Nullable Entity source) {
        WildlifeManager m = get(c.getWorld());
        if (m == null || !(source instanceof LivingEntity le) || c.herdId == 0) {
            return;
        }
        m.alarms.put(c.herdId, new Alarm(le, c.getWorld().getTime() + 100));
    }

    @Nullable
    public static LivingEntity alarmSource(UnderdeepCreature c) {
        WildlifeManager m = get(c.getWorld());
        if (m == null || c.herdId == 0 || c.species().attack() > 1 && !c.isBaby()) {
            return null;
        }
        Alarm a = m.alarms.get(c.herdId);
        if (a == null || a.until < c.getWorld().getTime() || !a.source.isAlive() || a.source.squaredDistanceTo(c) > 24 * 24) {
            return null;
        }
        return a.source;
    }

    @Nullable
    public static LivingEntity packTarget(UnderdeepCreature c) {
        WildlifeManager m = get(c.getWorld());
        if (m == null || c.herdId == 0) {
            return null;
        }
        LivingEntity t = m.packTargets.get(c.herdId);
        return t != null && t.isAlive() && t.squaredDistanceTo(c) < 24 * 24 ? t : null;
    }

    public static void sharePackTarget(UnderdeepCreature c, LivingEntity prey) {
        WildlifeManager m = get(c.getWorld());
        if (m != null && c.herdId != 0 && c.species().huntStyle() == CreatureSpecies.HuntStyle.PACK) {
            m.packTargets.put(c.herdId, prey);
        }
    }

    // ================================================================= feeding records, journal

    public static void recordFeeding(UnderdeepCreature c, BlockState food) {
        if (c.getWorld() instanceof ServerWorld sw) {
            for (ServerPlayerEntity p : sw.getPlayers(pl -> pl.squaredDistanceTo(c) < 20 * 20)) {
                WildlifeJournal.learn(p, c.species(), WildlifeJournal.Fact.FOOD);
            }
        }
    }

    public static void recordScavenge(UnderdeepCreature c) {
        recordFeeding(c, null);
    }

    private final List<Vec3d> killSites = new ArrayList<>();

    private void killSite(Vec3d pos) {
        killSites.add(pos);
        if (killSites.size() > 16) {
            killSites.remove(0);
        }
    }

    // ================================================================= nests & eggs

    public static void maintainNest(ServerWorld w, UnderdeepCreature c) {
        if (c.home != null && WildlifeBlocks.isNest(w.getBlockState(c.home))) {
            return;
        }
        BlockPos o = c.home != null ? c.home : c.getBlockPos();
        for (int i = 0; i < 12; i++) {
            BlockPos p = o.add(c.getRandom().nextInt(9) - 4, c.getRandom().nextInt(7) - 3, c.getRandom().nextInt(9) - 4);
            if (!w.isChunkLoaded(p) || !w.getBlockState(p).isAir() || Env.protectedArea(w, p)) {
                continue;
            }
            BlockState below = w.getBlockState(p.down());
            boolean ok = switch (c.species().nestType()) {
                case TREE -> below.isIn(BlockTags.LEAVES) || below.isIn(BlockTags.LOGS);
                case LEDGE, CRYSTAL -> below.isSideSolidFullSquare(w, p.down(), net.minecraft.util.math.Direction.UP) && w.isAir(p.up())
                        && !w.isAir(p.down(2)) && w.isAir(p.add(1, -1, 0)) | w.isAir(p.add(-1, -1, 0));
                case WATER -> w.getFluidState(p.down()).isIn(FluidTags.WATER) || below.isOf(net.minecraft.block.Blocks.MUD);
                default -> below.isSideSolidFullSquare(w, p.down(), net.minecraft.util.math.Direction.UP);
            };
            if (ok) {
                BlockState nest = c.species().nestType() == CreatureSpecies.Nest.BURROW ? WildlifeBlocks.BURROW.getDefaultState()
                        : WildlifeBlocks.NEST.getDefaultState();
                if (nest.canPlaceAt(w, p)) {
                    w.setBlockState(p, nest, Block.NOTIFY_ALL);
                    c.home = p;
                    return;
                }
            }
        }
    }

    /** Egg-laying species lay one egg on/next to their nest; the egg hatches into a juvenile. */
    public static boolean layEgg(ServerWorld w, UnderdeepCreature parent) {
        BlockPos base = parent.home != null && WildlifeBlocks.isNest(w.getBlockState(parent.home)) ? parent.home : parent.getBlockPos();
        for (BlockPos p : new BlockPos[]{base, base.north(), base.south(), base.east(), base.west()}) {
            BlockState s = w.getBlockState(p);
            if ((s.isAir() || WildlifeBlocks.isNest(s)) && WildlifeBlocks.EGG.getDefaultState().canPlaceAt(w, p) && !Env.protectedArea(w, p)) {
                w.setBlockState(p, WildlifeBlocks.EGG.getDefaultState(), Block.NOTIFY_ALL);
                if (w.getBlockEntity(p) instanceof WildlifeBlocks.EggBlockEntity egg) {
                    egg.setup(parent.species().id, parent.homeRegion, parent.isWild(), w.getTime() + 6000 + w.random.nextInt(6000),
                            parent.getOwnerUuid());
                }
                return true;
            }
        }
        return false;
    }

    // ================================================================= taming

    /** Called every 20 ticks for nearby creatures: patience-based trust builds while a player waits quietly. */
    public static void tickTaming(UnderdeepCreature c) {
        if (c.isTamed() || c.species().tameMethod() != CreatureSpecies.TameMethod.PATIENCE) {
            return;
        }
        PlayerEntity p = c.getWorld().getClosestPlayer(c, 6);
        if (p != null && p.isSneaking() && CreatureGoals.isFood(c.species(), p.getMainHandStack())) {
            if (c.trustingPlayer == null || !c.trustingPlayer.equals(p.getUuid())) {
                c.trustingPlayer = p.getUuid();
                c.patienceTicks = 0;
            }
            c.patienceTicks += 20;
            if (c.patienceTicks % 200 == 0 && c.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.HEART, c.getX(), c.getY() + c.getHeight(), c.getZ(), 1, 0.2, 0.2, 0.2, 0);
            }
        }
    }

    /**
     * Taming interactions. FEEDING: feed its favourite food several times. PATIENCE: wait sneaking
     * nearby holding its food until it relaxes (hearts), then feed it. RESCUE: it only accepts food
     * from you while injured. OFFERING: present a gift, at most once every 30 seconds, three times.
     */
    public static boolean tryTame(UnderdeepCreature c, PlayerEntity player, ItemStack stack) {
        CreatureSpecies s = c.species();
        if (!CreatureGoals.isFood(s, stack) || !net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath().equals(s.tameFood())) {
            return false;
        }
        if (c.isBaby() && s.tameMethod() != CreatureSpecies.TameMethod.FEEDING) {
            return false;
        }
        int needed = 3 + (int) (c.personality().timidity() * 3);
        boolean accepted = switch (s.tameMethod()) {
            case FEEDING -> true;
            case PATIENCE -> c.trustingPlayer != null && c.trustingPlayer.equals(player.getUuid()) && c.patienceTicks >= 1200;
            case RESCUE -> c.getHealth() < c.getMaxHealth() * 0.6f;
            case OFFERING -> c.age - c.lastOfferingAge > 600;
            default -> false;
        };
        if (!accepted) {
            if (player instanceof ServerPlayerEntity sp) {
                WildlifeJournal.learn(sp, s, WildlifeJournal.Fact.TAMING_HINT);
            }
            return false;
        }
        stack.decrementUnlessCreative(1, player);
        c.heal(4);
        c.hunger = 0;
        c.lastOfferingAge = c.age;
        if (c.trustingPlayer == null || !c.trustingPlayer.equals(player.getUuid())) {
            c.trustingPlayer = player.getUuid();
            c.trust = 0;
        }
        c.trust += s.tameMethod() == CreatureSpecies.TameMethod.PATIENCE ? needed : 1 + (c.personality().curiosity() > 0.6f ? 1 : 0);
        ServerWorld sw = (ServerWorld) c.getWorld();
        if (c.trust >= (s.tameMethod() == CreatureSpecies.TameMethod.FEEDING ? needed : 3)) {
            c.leaveWildAsTamed(player);
            c.getNavigation().stop();
            c.setTarget(null);
            c.setSitting(true);
            sw.spawnParticles(ParticleTypes.HEART, c.getX(), c.getY() + c.getHeight(), c.getZ(), 7, 0.4, 0.3, 0.4, 0);
            if (player instanceof ServerPlayerEntity sp) {
                WildlifeJournal.learn(sp, s, WildlifeJournal.Fact.TAMING);
            }
        } else {
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, c.getX(), c.getY() + c.getHeight(), c.getZ(), 4, 0.3, 0.3, 0.3, 0);
        }
        return true;
    }

    // ================================================================= world tick: spawner, companions, simulation

    private void tick() {
        long t = world.getTime();
        UnderdeepConfig cfg = UnderdeepConfig.get();
        if (!cfg.wildlifeEnabled) {
            return;
        }
        if (t % 20 == 7) {
            List<ServerPlayerEntity> players = world.getPlayers(p -> !p.isSpectator());
            if (!players.isEmpty()) {
                ServerPlayerEntity p = players.get(playerCursor++ % players.size());
                try {
                    spawnNear(p);
                } catch (RuntimeException e) {
                    Underdeep.LOGGER.warn("wildlife spawn attempt failed", e);
                }
            }
        }
        companions();
        if (t % Math.max(200, cfg.wildlifeSimInterval) == 31) {
            long s0 = System.nanoTime();
            simulate();
            simNanos += System.nanoTime() - s0;
        }
        if (t % 6000 == 0) {
            alarms.entrySet().removeIf(e -> e.getValue().until < t);
        }
    }

    /** Materialises wild creatures from the regional pool near a player (bounded, staggered). */
    public void spawnNear(ServerPlayerEntity player) {
        UnderdeepConfig cfg = UnderdeepConfig.get();
        if (loaded.size() >= cfg.maxWildlifeTotal) {
            return;
        }
        int nearby = world.getEntitiesByClass(UnderdeepCreature.class, player.getBoundingBox().expand(cfg.wildlifeDespawnDistance * 0.75),
                UnderdeepCreature::isWild).size();
        if (nearby >= cfg.maxWildlifePerPlayer) {
            return;
        }
        double ang = rnd.nextDouble() * Math.PI * 2;
        double dist = 24 + rnd.nextDouble() * 32;
        int x = (int) (player.getX() + Math.cos(ang) * dist);
        int z = (int) (player.getZ() + Math.sin(ang) * dist);
        if (!world.isChunkLoaded(new BlockPos(x, 0, z))) {
            return;
        }
        TerrainColumn col = terrain.column(x, z);
        CavernLayer layer = col.layerAt(player.getY());
        boolean up = layer == CavernLayer.UPPER;
        var sample = up ? col.upperRegion : col.deepRegion;
        long regionId = sample.id().asLong();
        RegionType type = sample.primary;
        RegionWildlife pool = region(regionId);
        // choose a species with unmaterialised members, weighted by how many are "missing" here
        List<CreatureSpecies> options = new ArrayList<>();
        int totalW = 0;
        for (Map.Entry<String, Integer> e : pool.counts.entrySet()) {
            CreatureSpecies s = WildlifeRegistry.byId(e.getKey());
            if (s == null || !s.regions().contains(type)) {
                continue;
            }
            int free = e.getValue() - activeCount(regionId, s.id);
            if (free > 0) {
                options.add(s);
                totalW += Math.min(free, 6);
            }
        }
        if (options.isEmpty()) {
            return;
        }
        int pick = rnd.nextInt(totalW);
        CreatureSpecies s = options.get(0);
        for (CreatureSpecies o : options) {
            int free = Math.min(6, pool.count(o.id) - activeCount(regionId, o.id));
            if (pick < free) {
                s = o;
                break;
            }
            pick -= free;
        }
        if (s.isPredator() && rnd.nextDouble() > cfg.predatorAbundance) {
            return;
        }
        boolean dangerous = s.temperament() == Temperament.AMBUSH || s.temperament() == Temperament.TERRITORIAL;
        if (dangerous && Math.hypot(x, z) < cfg.starterSafeRadius) {
            return;
        }
        double floor = up ? col.upperFloor : col.deepFloor;
        double ceil = up ? col.upperCeiling : col.deepCeiling;
        BlockPos spot = findSpot(s, x, z, floor, ceil, col, up);
        if (spot == null) {
            return;
        }
        RegionReport rr = RegionReport.at(terrain, spot.getX(), spot.getY(), spot.getZ());
        if (rr.regionId().asLong() != regionId) {
            return;
        }
        int free = pool.count(s.id) - activeCount(regionId, s.id);
        int n = Math.min(free, s.groupMin() + (s.groupMax() > s.groupMin() ? rnd.nextInt(s.groupMax() - s.groupMin() + 1) : 0));
        int herd = s.social() == CreatureSpecies.Social.SOLITARY ? 0 : nextHerd++;
        for (int i = 0; i < n; i++) {
            UnderdeepCreature c = WildlifeRegistry.type(s.id).create(world);
            if (c == null) {
                return;
            }
            double ox = i == 0 ? 0 : (rnd.nextDouble() - 0.5) * 4;
            double oz = i == 0 ? 0 : (rnd.nextDouble() - 0.5) * 4;
            c.refreshPositionAndAngles(spot.getX() + 0.5 + ox, spot.getY(), spot.getZ() + 0.5 + oz, rnd.nextFloat() * 360, 0);
            if (!world.isSpaceEmpty(c)) {
                c.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, rnd.nextFloat() * 360, 0);
            }
            c.initWild(regionId, spot, herd);
            if (i > 0 && (s.social() == CreatureSpecies.Social.HERD || s.social() == CreatureSpecies.Social.FAMILY) && rnd.nextInt(4) == 0) {
                c.setBaby(true);
            }
            c.initialize(world, world.getLocalDifficulty(spot), SpawnReason.NATURAL, null);
            world.spawnEntity(c);
            spawned++;
        }
    }

    @Nullable
    private BlockPos findSpot(CreatureSpecies s, int x, int z, double floor, double ceil, TerrainColumn col, boolean up) {
        int top = (int) Math.min(ceil - 1, floor + 40);
        int bottom = (int) floor - 8;
        BlockPos.Mutable m = new BlockPos.Mutable(x, top, z);
        if (s.isAquatic()) {
            for (int y = top; y > bottom; y--) {
                m.setY(y);
                if (world.getFluidState(m).isIn(FluidTags.WATER) && world.getFluidState(m.down()).isIn(FluidTags.WATER)
                        && world.getFluidState(m.up()).isIn(FluidTags.WATER)) {
                    return m.toImmutable();
                }
            }
            return null;
        }
        for (int y = top; y > bottom; y--) {
            m.setY(y);
            BlockState here = world.getBlockState(m);
            BlockState below = world.getBlockState(m.down());
            boolean waterOk = s.locomotion() == Locomotion.AMPHIBIOUS && here.getFluidState().isIn(FluidTags.WATER);
            if ((here.isAir() || here.isReplaceable() && here.getFluidState().isEmpty() || waterOk)
                    && below.isSideSolidFullSquare(world, m.down(), net.minecraft.util.math.Direction.UP)
                    && !below.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK) && !below.getFluidState().isIn(FluidTags.LAVA)) {
                int need = (int) Math.ceil(s.height());
                boolean clear = true;
                for (int k = 1; k < need && clear; k++) {
                    BlockState a = world.getBlockState(m.up(k));
                    clear = a.isAir() || a.isReplaceable() || waterOk;
                }
                if (clear) {
                    return s.flies() ? m.up(2).toImmutable() : m.toImmutable();
                }
            }
        }
        return null;
    }

    /** Companion abilities: predator warnings, resource sensing, gentle healing company (every 10 s, owners only). */
    private void companions() {
        if (world.getTime() % 200 != 13) {
            return;
        }
        for (ServerPlayerEntity owner : world.getPlayers()) {
            for (UnderdeepCreature c : world.getEntitiesByClass(UnderdeepCreature.class, owner.getBoundingBox().expand(24),
                    e -> e.isTamed() && owner.getUuid().equals(e.getOwnerUuid()))) {
                Companions.act(world, c, owner);
            }
        }
    }

    // ================================================================= aggregate simulation

    /** Advances unloaded known regions; moves emigrants to suitable neighbouring regions (conserving counts). */
    private void simulate() {
        UnderdeepConfig cfg = UnderdeepConfig.get();
        long now = world.getTime();
        int budget = cfg.wildlifeSimRegionsPerUpdate;
        List<Long> ids = new ArrayList<>(regions.keySet());
        java.util.Collections.shuffle(ids, rnd);
        for (long id : ids) {
            if (budget-- <= 0) {
                break;
            }
            Map<String, Integer> act = active.get(id);
            if (act != null && !act.isEmpty()) {
                regions.get(id).lastTime = now; // loaded regions are simulated by their entities
                continue;
            }
            RegionWildlife r = regions.get(id);
            double days = Math.min(3.0, (now - r.lastTime) / 24000.0);
            if (days < 0.05) {
                continue;
            }
            r.lastTime = now;
            RegionType type = typeOf(id);
            Map<String, Integer> out = r.step(days, type, id, cfg.wildlifeDensity, cfg.rareCreatureFrequency, cfg.wildlifeReproduction,
                    cfg.predatorAbundance, rnd);
            simSteps++;
            if (cfg.wildlifeMigration > 0) {
                for (Map.Entry<String, Integer> e : out.entrySet()) {
                    migrate(id, e.getKey(), probRound(e.getValue() * cfg.wildlifeMigration));
                }
            }
            save(id, r);
        }
    }

    private int probRound(double v) {
        return RegionWildlife.probRound(v, rnd);
    }

    private void migrate(long from, String species, int n) {
        if (n <= 0) {
            return;
        }
        CreatureSpecies s = WildlifeRegistry.byId(species);
        RegionId rid = RegionId.fromLong(from);
        RegionMap map = terrain.regions(rid.layer());
        UnderdeepConfig cfg = UnderdeepConfig.get();
        List<Long> targets = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                RegionType t = map.typeOf(rid.cellX() + dx, rid.cellZ() + dz);
                if (s.regions().contains(t)) {
                    targets.add(new RegionId(rid.layer(), rid.cellX() + dx, rid.cellZ() + dz).asLong());
                }
            }
        }
        java.util.Collections.shuffle(targets, rnd);
        RegionWildlife src = regions.get(from);
        for (long to : targets) {
            RegionWildlife dst = region(to);
            int cap = RegionWildlife.capacity(s, typeOf(to), to, cfg.wildlifeDensity, cfg.rareCreatureFrequency, dst.disturbance);
            int room = cap - dst.count(species);
            int move = Math.min(n, Math.min(room, src.count(species)));
            if (move > 0) {
                src.add(species, -move);
                src.migOut += move;
                dst.add(species, move);
                dst.migIn += move;
                dst.lastTime = Math.max(dst.lastTime, src.lastTime);
                save(to, dst);
                simMigrations += move;
                n -= move;
            }
            if (n <= 0) {
                break;
            }
        }
    }

    /** Runs the aggregate step immediately for a region (diagnostics and accelerated tests). */
    public Map<String, Integer> simulateRegionForTest(long id, double days) {
        UnderdeepConfig cfg = UnderdeepConfig.get();
        RegionWildlife r = region(id);
        Map<String, Integer> out = r.step(days, typeOf(id), id, cfg.wildlifeDensity, cfg.rareCreatureFrequency, cfg.wildlifeReproduction,
                cfg.predatorAbundance, rnd);
        for (Map.Entry<String, Integer> e : out.entrySet()) {
            migrate(id, e.getKey(), e.getValue());
        }
        save(id, r);
        return out;
    }

    public List<UnderdeepCreature> loadedCreatures() {
        return new ArrayList<>(loaded);
    }

    @SuppressWarnings("unused")
    private static String name(ItemStack s) {
        return Registries.ITEM.getId(s.getItem()).toString();
    }
}
