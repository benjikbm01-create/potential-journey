package dev.underdeep.debug;

import dev.underdeep.boss.BossDef;
import dev.underdeep.boss.BossEntity;
import dev.underdeep.boss.Bosses;
import dev.underdeep.boss.EncounterState;
import dev.underdeep.boss.Encounters;
import dev.underdeep.boss.Lairs;
import dev.underdeep.boss.Move;
import dev.underdeep.boss.Tier;
import dev.underdeep.combat.Archetype;
import dev.underdeep.combat.Combat;
import dev.underdeep.combat.CombatGoal;
import dev.underdeep.combat.Poise;
import dev.underdeep.explore.ExplorationMap;
import dev.underdeep.explore.ExploreContent;
import dev.underdeep.explore.Explorations;
import dev.underdeep.explore.Site;
import dev.underdeep.explore.SiteLayout;
import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.UnderdeepCreature;
import dev.underdeep.wildlife.WildlifeRegistry;
import dev.underdeep.world.analysis.VoxelWalk;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stage 10 self-test: cave network on generated blocks (survival walk), generation cost v4 vs v5, combat mechanics,
 * enemy archetypes, every guardian (awakened in a test area and fought by test players through accelerated ticking),
 * encounter rules (scaling, downed/revive, reset, persistence, rewards) and the final progression. Fights here prove
 * the mechanics work end to end; they say nothing about balance or fun (that needs real play).
 */
public final class Stage10Checks {
    private Stage10Checks() {
    }

    static int testIndex = 40;
    static final int[] PROBE = new int[1];
    static {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amt) -> {
            if (e instanceof TestPlayer) {
                PROBE[0]++;
            }
            return true;
        });
    }

    public static void run(MinecraftServer server, ServerWorld w, UnderdeepTerrain t, Path dir, Stage15Checks.Reporter r) {
        caves(server, w, t, r);
        combat(w, r);
        archetypes(w, r);
        bosses(server, w, r);
    }

    // ============================================================================================ caves

    static byte kind(ServerWorld w, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        BlockState s = w.getBlockState(p);
        if (s.getFluidState().isOf(net.minecraft.fluid.Fluids.LAVA) || s.getFluidState().isOf(net.minecraft.fluid.Fluids.FLOWING_LAVA)) {
            return VoxelWalk.LAVA;
        }
        if (!s.getFluidState().isEmpty()) {
            return VoxelWalk.WATER;
        }
        return s.getCollisionShape(w, p).isEmpty() ? VoxelWalk.AIR : VoxelWalk.SOLID;
    }

    static void caves(MinecraftServer server, ServerWorld w, UnderdeepTerrain t, Stage15Checks.Reporter r) {
        r.section("Stage 10 cave network (generated blocks)");
        r.info("terrain version", "world terrain v" + t.version() + (t.hasCaveNetwork() ? " (cave network)" : " (no cave network: older world)"));
        if (!t.hasCaveNetwork()) {
            return;
        }
        var report = dev.underdeep.world.analysis.CaveNetwork.analyze(t, 0, 0, 1200, 4);
        r.info("model connectivity (2400 x 2400 blocks around spawn)", report.summary());
        // generation cost of the terrain stage: v4 vs v5 on the same chunks (same JVM, same conditions)
        var biomes = w.getRegistryManager().get(RegistryKeys.BIOME);
        long seed = t.seed();
        for (int warm = 0; warm < 2; warm++) {
            for (int v : new int[]{4, 5}) {
                UnderdeepTerrain tv = new UnderdeepTerrain(seed, v);
                long t0 = System.nanoTime();
                for (int i = 0; i < 64; i++) {
                    GenerationBenchmark.fillOne(w, tv, biomes, new ChunkPos(300 + i % 8, -200 + i / 8));
                }
                double ms = (System.nanoTime() - t0) / 1e6 / 64;
                if (warm == 1) {
                    r.info("terrain-stage fill v" + v, String.format(Locale.ROOT, "%.2f ms per chunk (64 chunks, fresh model, after warm-up)", ms));
                }
            }
        }
        // survival walk through real generated descents, both directions
        List<UnderdeepTerrain.DescentInfo> ds = new ArrayList<>(t.descentsIn(-900, -900, 900, 900));
        ds.sort((a, b) -> Double.compare(Math.hypot(a.centerX(), a.centerZ()), Math.hypot(b.centerX(), b.centerZ())));
        int ok = 0, back = 0, n = 0;
        StringBuilder sb = new StringBuilder();
        long g0 = System.nanoTime();
        for (var d : ds) {
            if (n >= 3) {
                break;
            }
            int minX = (int) Math.min(d.minX(), Math.min(d.topX(), d.bottomX())) - 6, maxX = (int) Math.max(d.maxX(), Math.max(d.topX(), d.bottomX())) + 6;
            int minZ = (int) Math.min(d.minZ(), Math.min(d.topZ(), d.bottomZ())) - 6, maxZ = (int) Math.max(d.maxZ(), Math.max(d.topZ(), d.bottomZ())) + 6;
            if (maxX - minX > 170 || maxZ - minZ > 170) {
                continue;
            }
            n++;
            for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                    w.getChunk(cx, cz);
                }
            }
            int[] min = {minX, d.bottomY() - 8, minZ}, max = {maxX, d.topY() + 12, maxZ};
            VoxelWalk.Blocks b = (x, y, z) -> kind(w, x, y, z);
            int[] top = {d.topX(), d.topY(), d.topZ()}, bot = {d.bottomX(), d.bottomY(), d.bottomZ()};
            var down = VoxelWalk.walk(b, min, max, top, bot, 3);
            var up = VoxelWalk.walk(b, min, max, bot, top, 3);
            ok += down.reached() ? 1 : 0;
            back += up.reached() ? 1 : 0;
            if (!down.reached()) {
                int[] q = down.closest();
                StringBuilder blk = new StringBuilder();
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dy = -2; dy <= 3; dy++) {
                            BlockState bs = w.getBlockState(new BlockPos(q[0] + dx, q[1] + dy, q[2] + dz));
                            if (!bs.isAir() && !bs.isIn(net.minecraft.registry.tag.BlockTags.BASE_STONE_OVERWORLD)) {
                                blk.append(Registries.BLOCK.getId(bs.getBlock()).getPath()).append('@').append(dx).append(',').append(dy).append(',').append(dz).append(' ');
                            }
                        }
                    }
                }
                r.info("descent obstacle near " + java.util.Arrays.toString(q), blk.length() > 600 ? blk.substring(0, 600) : blk.toString());
            }
            sb.append(String.format(Locale.ROOT, "[%d,%d top y%d -> y%d: down %s%s, up %s] ", (int) d.centerX(), (int) d.centerZ(), d.topY(), d.bottomY(),
                    down.reached() ? "ok len " + down.pathLength() : "FAIL at " + java.util.Arrays.toString(down.closest()), "", up.reached() ? "ok" : "FAIL"));
        }
        double secs = (System.nanoTime() - g0) / 1e9;
        r.check("descent caves are walkable on generated blocks (survival movement, both ways)", n > 0 && ok == n && back == n,
                ok + "/" + n + " down, " + back + "/" + n + " up; " + sb + String.format(Locale.ROOT, "(%.1f s incl. chunk generation)", secs));
        // a horizontal connection between neighbouring great chambers, on generated blocks
        var g = t.graph(dev.underdeep.world.terrain.CavernLayer.UPPER);
        var a0 = g.node(0, 0);
        var a1 = g.node(1, 0);
        int ax = (int) a0.x(), az = (int) a0.z(), bx = (int) a1.x(), bz = (int) a1.z();
        if (Math.abs(bx - ax) < 260) {
            int minX = Math.min(ax, bx) - 40, maxX = Math.max(ax, bx) + 40, minZ = Math.min(az, bz) - 50, maxZ = Math.max(az, bz) + 50;
            for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                    w.getChunk(cx, cz);
                }
            }
            var ca = t.column(ax, az);
            var cb = t.column(bx, bz);
            int ay = (int) Math.floor(Math.max(ca.upperFloor, UnderdeepTerrain.SEA_LEVEL)) + 1, by = (int) Math.floor(Math.max(cb.upperFloor, UnderdeepTerrain.SEA_LEVEL)) + 1;
            var res = VoxelWalk.walk((x, y, z) -> kind(w, x, y, z), new int[]{minX, UnderdeepTerrain.SEA_LEVEL - 10, minZ},
                    new int[]{maxX, Math.max(ay, by) + 40, maxZ}, new int[]{ax, ay, az}, new int[]{bx, by, bz}, 8);
            r.info("spawn cavern to its east neighbour (generated blocks, survival walk)", res.reached() ? "walkable, " + res.pathLength() + " steps"
                    : "not walkable within the box; closest " + java.util.Arrays.toString(res.closest()) + String.format(Locale.ROOT, " (%.0f blocks short)", res.closestDist()));
        }
        // lairs never overlap settlements and exist for every guardian
        ExplorationMap map = Explorations.of(w);
        Map<String, Site> lairs = Lairs.plan(map);
        StringBuilder ls = new StringBuilder();
        for (var e : lairs.entrySet()) {
            ls.append(e.getKey()).append(" ").append((int) Math.hypot(e.getValue().x(), e.getValue().z())).append(e.getValue().layer().id.charAt(0)).append("; ");
        }
        r.check("every guardian has a lair in this world (deterministic placement)", lairs.size() == Bosses.DEFS.size(), lairs.size() + "/" + Bosses.DEFS.size()
                + " (distance from spawn, layer — operator diagnostics): " + ls);
    }

    // ============================================================================================ combat

    /** A fake player that can be hurt (Fabric's FakePlayer is invulnerable), ticked by the harness. */
    static final class TestPlayer extends FakePlayer {
        TestPlayer(ServerWorld w, String name) {
            super(w, new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(("underdeep-s10-" + name).getBytes()), name));
            try { // never ticked, so the spawn protection would never wear off
                var f = ServerPlayerEntity.class.getDeclaredField("joinInvulnerabilityTicks");
                f.setAccessible(true);
                f.setInt(this, 0);
            } catch (ReflectiveOperationException e) {
                // production mappings: damage checks then report honestly as failing
            }
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return false;
        }
    }

    static FakePlayer player(ServerWorld w, String name) {
        return new TestPlayer(w, "s10_" + name);
    }

    /** The self-test holds the server thread, so world time does not move by itself: advance it per simulated tick. */
    static void advance(ServerWorld w) {
        var props = (net.minecraft.world.level.ServerWorldProperties) w.getLevelProperties();
        props.setTime(props.getTime() + 1);
    }

    static void combat(ServerWorld w, Stage15Checks.Reporter r) {
        r.section("Stage 10 player combat (fake players, server-authoritative paths)");
        MinecraftServer server = w.getServer();
        FakePlayer p = player(w, "fighter");
        Combat.TEST_PLAYERS.add(p);
        // inside the spawn chunks (always entity-loaded), carved into rock high above the cavern
        BlockPos base = w.getSpawnPos().withY(285).add(6, 0, 6);
        floor(w, base, 10);
        p.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
        p.setOnGround(true);
        // dodge: stamina cost and invulnerability against an attack, not against a fall
        Combat.State st = Combat.of(p);
        st.stamina = 100;
        Combat.dodge(p, 1, 0);
        float afterDodge = st.stamina;
        ZombieEntity z = EntityType.ZOMBIE.create(w);
        z.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 1.5, 180, 0);
        w.spawnEntity(z);
        p.setHealth(20);
        boolean hitDuringDodge = p.damage(w.getDamageSources().mobAttack(z), 4);
        boolean fallDuringDodge = p.damage(w.getDamageSources().fall(), 1);
        r.check("dodge: costs stamina, avoids an attack during its window, not a fall", afterDodge < 100 && !hitDuringDodge && fallDuringDodge,
                "stamina " + afterDodge + ", attack landed " + hitDuringDodge + ", fall landed " + fallDuringDodge);
        // stamina regenerates (ticked like a connected player)
        st.stamina = 40;
        for (int i = 0; i < 80; i++) {
            advance(w);
            Combat.tick(server);
        }
        r.check("stamina regenerates after a short pause (not used by walking/mining)", st.stamina > 70, "40 -> " + String.format(Locale.ROOT, "%.0f", st.stamina) + " in 4 s");
        // weapon parry: right-click with a sword opens a window; a melee blow inside it is parried, attacker staggered, opening granted
        for (int i = 0; i < 40; i++) {
            advance(w);
            Combat.tick(server);
        }
        p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        p.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
        p.setYaw(0);
        p.setHeadYaw(0);
        z.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 1.5, 180, 0); // in front (south = +z at yaw 0)
        Combat.useItem(p, w, Hand.MAIN_HAND);
        p.setHealth(20);
        p.timeUntilRegen = 0;
        int seen = PROBE[0];
        boolean parryHit = p.damage(w.getDamageSources().mobAttack(z), 5);
        r.info("parry probe", "damage hook reached " + (PROBE[0] - seen) + " time(s); zombie " + z.getBlockPos().toShortString() + " alive " + z.isAlive()
                + "; player health " + p.getHealth() + " invulnerable " + p.isInvulnerableTo(w.getDamageSources().mobAttack(z)));
        boolean staggered = Poise.staggered(z);
        boolean opening = Combat.of(p).inOpening(w.getTime());
        r.check("weapon parry: blow negated, attacker staggered, opening for a counter", !parryHit && staggered && opening,
                "blow landed " + parryHit + ", zombie staggered " + staggered + ", opening " + opening);
        // counter damage is boosted and consumes the opening
        z.setHealth(z.getMaxHealth());
        z.timeUntilRegen = 0;
        float before = z.getHealth();
        z.damage(w.getDamageSources().playerAttack(p), 4);
        float counter = before - z.getHealth();
        r.check("counter: the next hit after a parry deals much more and ends the opening", counter > 6 && !Combat.of(p).inOpening(w.getTime()),
                String.format(Locale.ROOT, "4 base -> %.1f dealt", counter));
        // heavy attack: server-timed wind-up, then a hit in the style's shape; recovery afterwards
        for (int i = 0; i < 60; i++) {
            advance(w);
            Combat.tick(server);
        }
        z.discard();
        ZombieEntity z2 = EntityType.ZOMBIE.create(w);
        z2.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 2.2, 180, 0);
        w.spawnEntity(z2);
        p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Registries.ITEM.get(Identifier.of("underdeep", "steel_greatsword"))));
        for (int i = 0; i < 40; i++) {
            advance(w);
            p.age++;
        }
        charge(p);
        z2.timeUntilRegen = 0;
        float l0 = z2.getHealth();
        p.attack(z2); // a light hit in the same state, for comparison
        float light = l0 - z2.getHealth();
        z2.setHealth(z2.getMaxHealth());
        z2.timeUntilRegen = 0;
        charge(p);
        st.stamina = 100;
        float h0 = z2.getHealth();
        Combat.heavy(p);
        boolean winding = Combat.of(p).inWindup();
        int ticks = 0;
        while (Combat.of(p).inWindup() && ticks < 40) {
            advance(w);
            Combat.tick(server);
            ticks++;
        }
        float heavyDealt = h0 - z2.getHealth();
        r.info("heavy attack hit list", Combat.LAST_HEAVY_HITS + " target(s) in the shape; player " + p.getBlockPos().toShortString() + " zombie "
                + z2.getBlockPos().toShortString() + " alive " + z2.isAlive() + " removed " + z2.isRemoved() + ", entities near player "
                + w.getOtherEntities(p, p.getBoundingBox().expand(6)).size());
        r.check("heavy attack: wind-up timed by the server, then a much stronger hit than a light one; stamina spent",
                winding && ticks >= 10 && light > 0 && heavyDealt >= light * 1.8 && st.stamina < 100,
                "wind-up " + ticks + " ticks, light " + String.format(Locale.ROOT, "%.1f", light) + " vs heavy " + String.format(Locale.ROOT, "%.1f", heavyDealt)
                        + ", stamina " + String.format(Locale.ROOT, "%.0f", st.stamina) + " (test player without weapon attributes: compare ratios)");
        z2.discard();
        r.info("weapon styles", java.util.Arrays.stream(dev.underdeep.combat.WeaponStyle.values()).map(s -> s.title).toList().toString());
        Combat.TEST_PLAYERS.remove(p);
    }

    /** Fake players are not ticked: set the attack cooldown to fully charged (dev mappings; self-test only). */
    static void charge(ServerPlayerEntity p) {
        try {
            var f = net.minecraft.entity.LivingEntity.class.getDeclaredField("lastAttackedTicks");
            f.setAccessible(true);
            f.setInt(p, 100);
        } catch (ReflectiveOperationException e) {
            // production mappings: leave it (the check then reports the heavy attack as not started)
        }
    }

    static void floor(ServerWorld w, BlockPos c, int r) {
        for (int cx = (c.getX() - r - 2) >> 4; cx <= (c.getX() + r + 2) >> 4; cx++) {
            for (int cz = (c.getZ() - r - 2) >> 4; cz <= (c.getZ() + r + 2) >> 4; cz++) {
                w.getChunk(cx, cz);
            }
        }
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 6; dy++) {
                    w.setBlockState(c.add(dx, dy, dz), dy < 0 ? net.minecraft.block.Blocks.STONE.getDefaultState() : net.minecraft.block.Blocks.AIR.getDefaultState());
                }
            }
        }
    }

    // ============================================================================================ archetypes

    static void archetypes(ServerWorld w, Stage15Checks.Reporter r) {
        r.section("Stage 10 enemy behaviour archetypes");
        Map<Archetype, List<String>> by = new EnumMap<>(Archetype.class);
        int fighters = 0, peaceful = 0, peacefulWithGoal = 0;
        BlockPos base = w.getSpawnPos().withY(285).add(-8, 0, -8);
        floor(w, base, 6);
        for (CreatureSpecies s : WildlifeRegistry.all()) {
            var type = WildlifeRegistry.type(s.id);
            if (!(type.create(w) instanceof UnderdeepCreature c)) {
                continue;
            }
            c.refreshPositionAndAngles(base.getX(), base.getY(), base.getZ(), 0, 0);
            boolean hasGoal = false;
            for (PrioritizedGoal g : c.goals().getGoals()) {
                if (g.getGoal() instanceof CombatGoal cg) {
                    hasGoal = true;
                    by.computeIfAbsent(cg.archetype(), k -> new ArrayList<>()).add(s.id);
                }
            }
            if (s.attack() > 1) {
                fighters++;
            } else {
                peaceful++;
                peacefulWithGoal += hasGoal ? 1 : 0;
            }
            c.discard();
        }
        StringBuilder sb = new StringBuilder();
        by.forEach((a, l) -> sb.append(a.title).append(" ").append(l.size()).append(", "));
        r.check("fighting creatures use telegraphed archetype behaviour; peaceful ones never fight", peacefulWithGoal == 0 && !by.isEmpty(),
                fighters + " fighting species in " + by.size() + " archetypes (" + sb + "), " + peaceful + " peaceful (" + peacefulWithGoal + " with a fighting goal)");
        // a fighting creature telegraphs (warning state) before its strike lands
        FakePlayer p = player(w, "prey");
        p.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 3.5, 180, 0);
        String species = by.containsKey(Archetype.SKIRMISHER) ? by.get(Archetype.SKIRMISHER).get(0)
                : by.containsKey(Archetype.PACK_FLANKER) ? by.get(Archetype.PACK_FLANKER).get(0) : by.values().iterator().next().get(0);
        UnderdeepCreature c = (UnderdeepCreature) WildlifeRegistry.type(species).create(w);
        c.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
        w.spawnEntity(c);
        c.setTarget(p);
        CombatGoal.STRIKES = 0;
        int warnAt = -1, hitAt = -1;
        p.setHealth(20);
        for (int i = 0; i < 400 && hitAt < 0; i++) {
            advance(w);
            c.setTarget(p);
            c.tick();
            p.setPosition(base.getX() + 0.5, base.getY(), base.getZ() + 2.2);
            if (warnAt < 0 && c.state() == UnderdeepCreature.WARN) {
                warnAt = i;
            }
            if (p.getHealth() < 20 || CombatGoal.STRIKES > 0) {
                hitAt = i;
            }
        }
        StringBuilder running = new StringBuilder();
        c.goals().getGoals().forEach(g -> {
            if (g.isRunning()) {
                running.append(g.getGoal().getClass().getSimpleName()).append(' ');
            }
        });
        r.info("creature probe", species + " target " + (c.getTarget() != null) + " running goals: " + running + " distance " + String.format(Locale.ROOT, "%.1f", c.distanceTo(p)));
        r.check("a creature's attack is telegraphed before it lands (accelerated ticking)", warnAt >= 0 && hitAt > warnAt,
                species + ": warning at tick " + warnAt + ", first hit at tick " + hitAt);
        c.discard();
    }

    // ============================================================================================ guardians

    static void bosses(MinecraftServer server, ServerWorld w, Stage15Checks.Reporter r) {
        r.section("Stage 10 guardians (test area; test players; accelerated ticking — mechanics only, not balance)");
        ExplorationMap map = Explorations.of(w);
        EncounterState st = EncounterState.get(server);
        FakePlayer a = player(w, "hero_a"), b = player(w, "hero_b"), c = player(w, "hero_c");
        // static checks: every move telegraphs; every guardian has an answer to unreachable targets
        StringBuilder weak = new StringBuilder();
        for (BossDef d : Bosses.DEFS.values()) {
            BossEntity probe = (BossEntity) Bosses.type(d.id()).create(w);
            boolean ranged = false;
            for (Move m : probe.brain.moves()) {
                ranged |= isRanged(m);
            }
            if (!ranged) {
                weak.append(d.id()).append(' ');
            }
            probe.discard();
        }
        r.check("every guardian can answer players it cannot reach (ranged moves)", weak.isEmpty(), weak.isEmpty() ? "13/13" : "missing: " + weak);
        Map<String, String> results = new LinkedHashMap<>();
        int defeated = 0;
        Set<String> withPhases = new LinkedHashSet<>();
        long t0 = System.nanoTime();
        for (BossDef d : Bosses.DEFS.values()) {
            ExploreContent.Family f = ExploreContent.FAMILIES.get("lair_" + d.id());
            Site s = Stage9Checks.placeTest(w, map, f, testIndex++);
            String res = fight(server, w, map, s, d, a, st, withPhases);
            results.put(d.id(), res);
            if (res.startsWith("defeated")) {
                defeated++;
            }
        }
        double secs = (System.nanoTime() - t0) / 1e9;
        StringBuilder sb = new StringBuilder();
        results.forEach((k, v) -> sb.append(k).append(": ").append(v).append("; "));
        r.check("every guardian awakens at its altar, attacks, changes phase, and can be defeated (rewards credited)", defeated == Bosses.DEFS.size(),
                defeated + "/" + Bosses.DEFS.size() + String.format(Locale.ROOT, " in %.0f s: ", secs) + sb);
        r.check("multi-phase guardians changed phase during their fight", withPhases.size() >= 12, withPhases.size() + " with a phase change: " + withPhases);
        // scaling, downed / revive, reset
        ExploreContent.Family f = ExploreContent.FAMILIES.get("lair_mossback");
        Site s = Stage9Checks.placeTest(w, map, f, testIndex++);
        SiteLayout l = map.layout(s);
        BlockPos centre = l.marks("center").get(0);
        for (FakePlayer p : List.of(a, b, c)) {
            p.refreshPositionAndAngles(centre.getX() + 4.5, centre.getY(), centre.getZ() + 0.5, 0, 0);
            p.setHealth(20);
        }
        var act = Encounters.awaken(w, s, Bosses.byId("mossback"), List.of(a, b, c));
        double expected = 1 + Encounters.cfg().healthPerExtraPlayer * 2;
        r.check("scaling: fixed by the fighters present at awakening", act != null && Math.abs(act.healthScale - expected) < 1e-6
                && Math.abs(act.boss.effectiveMaxHealth() - Bosses.byId("mossback").health() * expected) < 1, act == null ? "no encounter"
                : String.format(Locale.ROOT, "3 fighters -> health x%.2f (effective %.0f)", act.healthScale, act.boss.effectiveMaxHealth()));
        boolean down = Encounters.tryDown(a, w.getDamageSources().generic(), 100);
        b.setSneaking(true);
        b.refreshPositionAndAngles(a.getX() + 1, a.getY(), a.getZ(), 0, 0);
        for (int i = 0; i < 60; i++) {
            advance(w);
            Encounters.tick(server);
        }
        b.setSneaking(false);
        boolean revived = act != null && !act.downed(a) && a.getHealth() > 1;
        r.check("downed instead of killed while a friend fights; a crouching friend helps them up", down && revived,
                "downed " + down + ", revived " + revived + ", health " + a.getHealth());
        // pylons/devices restored after a reset; nobody left fighting -> sleeps again
        act.testFighters.clear();
        for (int i = 0; i < Encounters.cfg().resetAfterSeconds * 20 + 40; i++) {
            advance(w);
            Encounters.tick(server);
        }
        boolean reset = Encounters.active("mossback") == null && (act.boss == null || act.boss.isRemoved());
        r.check("everyone gone -> the guardian sleeps again after the reset delay (no admin needed)", reset, "active " + (Encounters.active("mossback") != null));
        // persistence of the world record
        var copy = EncounterState.roundTrip(st, w.getRegistryManager());
        r.check("encounter state survives save/load (defeats, credited players, door signs, completion)",
                copy.bosses.keySet().equals(st.bosses.keySet()) && copy.doorSigns.equals(st.doorSigns) && copy.completedAt == st.completedAt
                        && copy.record("mossback").credited.equals(st.record("mossback").credited), copy.bosses.size() + " records, completed " + copy.completed());
        r.check("final victory recorded the world completion and queued the ending", st.completed() && !st.completers.isEmpty(),
                "completed at " + st.completedAt + " by " + st.completers);
        // spoiler visibility: hidden advancements for secret guardians and the full set
        int hidden = 0;
        for (String id : new String[]{"sovereign", "watcher", "all"}) {
            var e = server.getAdvancementLoader().get(Identifier.of("underdeep", "guardians/" + id));
            hidden += e != null && e.value().display().map(dsp -> dsp.isHidden()).orElse(false) ? 1 : 0;
        }
        r.check("secret guardians' advancements are hidden", hidden == 3, hidden + "/3 hidden");
    }

    static boolean isRanged(Move m) {
        try {
            var f = Move.class.getDeclaredField("ranged");
            f.setAccessible(true);
            return f.getBoolean(m);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** One guardian fight in a test area: awaken, let it attack, work its devices, wear it down, check the rewards. */
    static String fight(MinecraftServer server, ServerWorld w, ExplorationMap map, Site s, BossDef d, FakePlayer p, EncounterState st, Set<String> phased) {
        SiteLayout l = map.layout(s);
        BlockPos centre = l.marks("center").isEmpty() ? new BlockPos(s.x(), s.y() + 1, s.z()) : l.marks("center").get(0);
        if (d.tier() == Tier.FINAL) {
            // the final arena is behind the door: set the keystone and three Signs first
            BlockPos door = l.marks("door").get(0);
            for (String sign : new String[]{"keystone_silence", "sign_spiral", "sign_eye", "sign_star"}) {
                ItemStack stack = new ItemStack(Registries.ITEM.get(Identifier.of("underdeep", sign)));
                p.refreshPositionAndAngles(door.getX() + 0.5, door.getY(), door.getZ() - 1.5, 0, 0);
                p.setStackInHand(Hand.MAIN_HAND, stack);
                st.doorOpen = false;
                dev.underdeep.boss.Ending.class.getName();
                try {
                    var m = dev.underdeep.boss.Ending.class.getDeclaredMethod("door", ServerPlayerEntity.class, BlockPos.class, ItemStack.class);
                    m.setAccessible(true);
                    m.invoke(null, p, door, stack);
                } catch (ReflectiveOperationException e) {
                    return "door error " + e;
                }
            }
            boolean open = st.doorOpen && w.getBlockState(door).isAir();
            if (!open) {
                return "door did not open";
            }
        }
        p.refreshPositionAndAngles(centre.getX() + 6.5, centre.getY(), centre.getZ() + 0.5, 0, 0);
        p.setHealth(p.getMaxHealth());
        Encounters.Active a = Encounters.awaken(w, s, d, List.of(p));
        if (a == null) {
            return "did not awaken";
        }
        BossEntity b = a.boss;
        Set<String> used = new LinkedHashSet<>();
        int hitsTaken = 0, phases = 0, ticks = 0;
        float chunk = (float) (b.effectiveMaxHealth() / 45f);
        List<BlockPos> devices = new ArrayList<>();
        for (String k : new String[]{"pylon", "brazier", "lantern", "coil", "bell", "root"}) {
            for (BlockPos q : l.marks(k)) {
                devices.add(q);
            }
        }
        while (!b.isRemoved() && b.isAlive() && ticks < 9000) {
            ticks++;
            advance(w);
            b.tick();
            Encounters.tick(server);
            if (b.stage() == BossEntity.WINDUP) {
                Move m = b.brain.move(b.moveId());
                if (m != null) {
                    used.add(m.name());
                }
            }
            if (p.getHealth() < p.getMaxHealth()) {
                hitsTaken++;
                p.setHealth(p.getMaxHealth());
            }
            if (!p.isAlive()) {
                p.setHealth(p.getMaxHealth());
            }
            // keep the fighter in the arena, next to the guardian
            Vec3d to = b.getPos().subtract(centre.getX(), b.getY(), centre.getZ());
            if (ticks % 20 == 0) {
                Vec3d spot = b.getPos().add(3, 0, 0);
                if (!a.inArena(BlockPos.ofFloored(spot))) {
                    spot = Vec3d.ofBottomCenter(centre).add(4, 0, 0);
                }
                p.refreshPositionAndAngles(spot.x, centre.getY(), spot.z, 0, 0);
            }
            // work every device: ground pylons, douse lanterns, light braziers, relight coils, cut roots, ring bells
            if (ticks > 80 && ticks % 4 == 0) {
                for (BlockPos q : devices) {
                    BlockState bs = w.getBlockState(q);
                    if (bs.getBlock() instanceof dev.underdeep.boss.BossBlocks.DeviceBlock db) {
                        b.brain.device(p, q, db.kind);
                    }
                }
            }
            if (ticks > 200 && ticks % 10 == 0 && !b.defeated()) {
                b.damage(w.getDamageSources().playerAttack(p), chunk);
            }
            phases = Math.max(phases, b.phaseIndex());
        }
        boolean won = st.defeated(d.id()) && st.record(d.id()).credited.contains(p.getUuidAsString());
        boolean loot = p.getInventory().containsAny(x -> x.isOf(Registries.ITEM.get(Identifier.of(d.material()))));
        boolean sign = d.sign() == null || p.getInventory().containsAny(x -> x.isOf(Registries.ITEM.get(Identifier.of(d.sign())))) || st.doorSigns.contains(d.sign().substring(10));
        if (phases > 0) {
            phased.add(d.id());
        }
        if (Encounters.active(d.id()) != null) {
            Encounters.lost(Encounters.active(d.id()), "reset");
        }
        p.getInventory().clear();
        return (won && loot && sign ? "defeated" : "NOT defeated") + " in " + ticks + " ticks, moves " + used.size() + "/" + b.brain.moves().size()
                + ", hits on player " + hitsTaken + ", phases reached " + phases + (loot ? "" : ", NO LOOT") + (sign ? "" : ", NO SIGN");
    }
}
