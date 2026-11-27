package dev.underdeep.boss;

import dev.underdeep.Underdeep;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.explore.ExplorationMap;
import dev.underdeep.explore.Explorations;
import dev.underdeep.explore.Site;
import dev.underdeep.explore.SiteLayout;
import dev.underdeep.guide.Advancements;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stage 10 boss encounters: awakening, participation, scaling, arena protection, downed players and revival, resets,
 * victory and rewards. One encounter per guardian per world; only active fights are simulated (a sleeping guardian
 * costs nothing). Rules:
 * <ul>
 *   <li>Awakening is a deliberate act at the guardian's altar (use twice; some need an offering).</li>
 *   <li>Participants are players inside the arena; scaling is fixed at awakening by their number and only rises when
 *   someone joins during the first 90 s (it never drops, so leaving and rejoining cannot exploit it).</li>
 *   <li>If nobody is left fighting in the arena for 30 s (all dead, gone or disconnected), the guardian returns to sleep
 *   and the arena is restored — retry any time, no administrator needed.</li>
 *   <li>Rewards go to every credited participant (damage, defensive actions, devices, revives, or staying in the fight);
 *   claims are recorded before anything is given and persist, so reconnecting or restarting cannot duplicate them.</li>
 * </ul>
 */
public final class Encounters {
    private Encounters() {
    }

    public static UnderdeepConfig.Bosses cfg() {
        return UnderdeepConfig.get().bosses;
    }

    /** A running fight. */
    public static final class Active {
        public final BossDef def;
        public final ServerWorld world;
        public final long siteId;
        public final BlockPos center;
        public final int radius;
        public BossEntity boss;
        public boolean ended;
        final long started;
        int count;
        public double healthScale = 1, damageScale = 1;
        final Map<UUID, int[]> participants = new LinkedHashMap<>(); // {actions, presenceTicks}
        final Map<UUID, long[]> downed = new HashMap<>(); // {until, progress, lastDowned}
        final Map<UUID, Long> lastDowned = new HashMap<>();
        long emptySince = -1;
        ServerBossBar bar;
        final Map<String, Object> data = new HashMap<>();
        /** Self-test hook: fake players that count as fighters (never used in play). */
        public final List<ServerPlayerEntity> testFighters = new ArrayList<>();

        Active(BossDef def, ServerWorld world, long siteId, BlockPos center, int radius) {
            this.def = def;
            this.world = world;
            this.siteId = siteId;
            this.center = center;
            this.radius = radius;
            this.started = world.getTime();
        }

        public boolean inArena(BlockPos p) {
            double dx = p.getX() - center.getX(), dz = p.getZ() - center.getZ();
            return dx * dx + dz * dz <= (double) (radius + 2) * (radius + 2) && p.getY() > center.getY() - 12 && p.getY() < center.getY() + 48;
        }

        /** Players fighting: alive, in the arena, not spectating. */
        public List<ServerPlayerEntity> fighters() {
            List<ServerPlayerEntity> l = new ArrayList<>();
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (p.isAlive() && !p.isSpectator() && inArena(p.getBlockPos())) {
                    l.add(p);
                }
            }
            for (ServerPlayerEntity p : testFighters) {
                if (p.isAlive() && inArena(p.getBlockPos()) && !l.contains(p)) {
                    l.add(p);
                }
            }
            return l;
        }

        public void participate(ServerPlayerEntity p, double amount) {
            int[] r = participants.computeIfAbsent(p.getUuid(), k -> new int[2]);
            r[0]++;
        }

        public boolean downed(ServerPlayerEntity p) {
            return downed.containsKey(p.getUuid());
        }

        public void tell(String msg, Formatting f) {
            for (ServerPlayerEntity p : fighters()) {
                p.sendMessage(Text.literal(msg).formatted(f), true);
            }
        }

        public <T> T get(String key, T def) {
            @SuppressWarnings("unchecked") T v = (T) data.getOrDefault(key, def);
            return v;
        }

        public void put(String key, Object v) {
            data.put(key, v);
        }

        public SiteLayout layout() {
            ExplorationMap map = Explorations.of(world);
            Site s = map == null ? null : map.byId(siteId);
            return s == null ? null : map.layout(s);
        }

        /** Changes an arena block for the duration of the fight (restored afterwards and after a crash). */
        public void setTemp(BlockPos pos, BlockState state) {
            EncounterState st = EncounterState.get(world.getServer());
            st.temp.putIfAbsent(pos.asLong(), world.getBlockState(pos));
            st.markDirty();
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
        }
    }

    static final Map<String, Active> ACTIVE = new LinkedHashMap<>();
    private static final Map<UUID, long[]> CONFIRM = new HashMap<>(); // player -> {siteId, until}

    // ------------------------------------------------------------------------------------- audio context hook

    /** S2C: boss audio/presentation context (Stage 11 music): prepare, activate, phase, victory, defeat, retreat, end. */
    public record Audio(String event, String boss, int arg) implements CustomPayload {
        public static final Id<Audio> ID = new Id<>(Identifier.of(Underdeep.MOD_ID, "boss_audio"));
        public static final PacketCodec<RegistryByteBuf, Audio> CODEC = PacketCodec.of((p, b) -> {
            b.writeString(p.event);
            b.writeString(p.boss);
            b.writeVarInt(p.arg);
        }, b -> new Audio(b.readString(32), b.readString(64), b.readVarInt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    static void audio(@Nullable Active a, String event, int arg) {
        if (a == null) {
            return;
        }
        for (ServerPlayerEntity p : a.fighters()) {
            if (!a.testFighters.contains(p)) {
                ServerPlayNetworking.send(p, new Audio(event, a.def.id(), arg));
            }
        }
    }

    // ------------------------------------------------------------------------------------- init

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Audio.ID, Audio.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(Encounters::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(Encounters::restoreArenas);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            for (Active a : new ArrayList<>(ACTIVE.values())) {
                end(a, false);
            }
            restoreArenas(s);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amount) -> !(e instanceof ServerPlayerEntity p) || !tryDown(p, src, amount));
        PlayerBlockBreakEvents.BEFORE.register((w, player, pos, state, be) -> {
            Active a = arenaAt(w, pos);
            if (a != null && cfg().arenaProtection) {
                player.sendMessage(Text.literal("The guardian's lair shakes too violently to dig here now.").formatted(Formatting.GRAY), true);
                return false;
            }
            return true;
        });
        UseBlockCallback.EVENT.register((player, w, hand, hit) -> {
            if (w.isClient || !cfg().arenaProtection) {
                return ActionResult.PASS;
            }
            ItemStack st = player.getStackInHand(hand);
            if ((st.getItem() instanceof BlockItem || st.getItem() instanceof BucketItem) && arenaAt(w, hit.getBlockPos().offset(hit.getSide())) != null) {
                player.sendMessage(Text.literal("No time to build — the guardian is awake.").formatted(Formatting.GRAY), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        Combat.init();
    }

    /** Wires boss rules into the combat layer. */
    static final class Combat {
        static void init() {
            var prev = dev.underdeep.combat.Combat.UNPARRYABLE;
            dev.underdeep.combat.Combat.UNPARRYABLE = s -> BossDamage.unparryable(s) || prev.test(s);
            dev.underdeep.combat.Combat.BLOCK_DRAIN = BossDamage::blockDrain;
            var parried = dev.underdeep.combat.Combat.ON_PARRIED;
            dev.underdeep.combat.Combat.ON_PARRIED = (attacker, p) -> {
                if (attacker instanceof BossEntity b) {
                    b.parried(p);
                    if (b.enc != null && p instanceof ServerPlayerEntity sp) {
                        b.enc.participate(sp, 0);
                    }
                } else {
                    parried.accept(attacker, p);
                }
            };
            var undodge = dev.underdeep.combat.Combat.UNDODGEABLE;
            dev.underdeep.combat.Combat.UNDODGEABLE = s -> BossDamage.undodgeable(s) || undodge.test(s);
        }
    }

    @Nullable
    static Active arenaAt(net.minecraft.world.World w, BlockPos pos) {
        for (Active a : ACTIVE.values()) {
            if (a.world == w && !a.ended && a.inArena(pos)) {
                return a;
            }
        }
        return null;
    }

    @Nullable
    public static Active active(String bossId) {
        return ACTIVE.get(bossId);
    }

    // ------------------------------------------------------------------------------------- awakening

    /** The guardian's lair at a position (lair sites carry the boss id in their family id). */
    @Nullable
    public static BossDef lairBoss(Site s) {
        String f = s.family().id();
        return f.startsWith("lair_") ? Bosses.byId(f.substring(5)) : null;
    }

    /** Use of a guardian altar. */
    public static void altar(ServerPlayerEntity p, BlockPos pos) {
        ServerWorld w = p.getServerWorld();
        ExplorationMap map = Explorations.of(w);
        Site site = map == null ? null : map.siteAt(pos, 4);
        BossDef def = site == null ? null : lairBoss(site);
        if (def == null) {
            p.sendMessage(Text.literal("The altar is cold.").formatted(Formatting.GRAY), true);
            return;
        }
        EncounterState st = EncounterState.get(w.getServer());
        if (ACTIVE.containsKey(def.id())) {
            p.sendMessage(Text.literal("It is already awake!").formatted(Formatting.RED), true);
            return;
        }
        // recovery of a lost Sign or keystone
        if (p.isSneaking() && p.getMainHandStack().isEmpty()) {
            reclaim(p, def, st);
            return;
        }
        String need = offering(def);
        if (need != null && !Registries.ITEM.getId(p.getMainHandStack().getItem()).toString().equals(need)) {
            p.sendMessage(Text.literal(offeringHint(def)).formatted(Formatting.DARK_AQUA, Formatting.ITALIC), false);
            return;
        }
        long now = w.getTime();
        long[] c = CONFIRM.get(p.getUuid());
        if (c == null || c[0] != site.id() || c[1] < now) {
            CONFIRM.put(p.getUuid(), new long[]{site.id(), now + 200});
            boolean beaten = st.defeated(def.id());
            p.sendMessage(Text.literal(beaten ? "The guardian sleeps lightly here. Use the altar again to challenge it once more."
                    : "Something vast sleeps here. Use the altar again to wake it — there will be no turning back until it is over.")
                    .formatted(Formatting.GOLD), false);
            w.playSound(null, pos, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 1f, 0.5f);
            Advancements.event(p, "lair_found", def.tier().name().toLowerCase());
            ServerPlayNetworking.send(p, new Audio("prepare", "", def.tier().ordinal()));
            return;
        }
        CONFIRM.remove(p.getUuid());
        if (need != null) {
            p.getMainHandStack().decrement(1);
        }
        awaken(w, site, def);
    }

    @Nullable
    static String offering(BossDef d) {
        return switch (d.id()) {
            case "rootsovereign" -> "underdeep:relic_fruit";
            case "seamwatcher" -> "underdeep:star_shard";
            default -> null;
        };
    }

    static String offeringHint(BossDef d) {
        return switch (d.id()) {
            case "rootsovereign" -> "The great root is hungry for something that grew from an ancient seed.";
            case "seamwatcher" -> "The seam hums. It wants a piece of a fallen star.";
            default -> "";
        };
    }

    public static Active awaken(ServerWorld w, Site site, BossDef def) {
        return awaken(w, site, def, List.of());
    }

    /** Awakening; {@code test} adds self-test fake players as fighters (empty in play). */
    public static Active awaken(ServerWorld w, Site site, BossDef def, List<ServerPlayerEntity> test) {
        SiteLayout l = Explorations.of(w).layout(site);
        List<BlockPos> centre = l.marks("center");
        List<BlockPos> spawn = l.marks("spawn");
        BlockPos c = centre.isEmpty() ? new BlockPos(site.x(), site.y() + 1, site.z()) : centre.get(0);
        Active a = new Active(def, w, site.id(), c, Math.max(10, site.radius() - 2));
        a.testFighters.addAll(test);
        List<ServerPlayerEntity> f = a.fighters();
        a.count = Math.max(1, f.size());
        applyScale(a);
        BossEntity b = (BossEntity) Bosses.type(def.id()).create(w);
        if (b == null) {
            return null;
        }
        BlockPos sp = spawn.isEmpty() ? c : spawn.get(0);
        b.refreshPositionAndAngles(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, w.random.nextFloat() * 360, 0);
        b.enc = a;
        a.boss = b;
        ACTIVE.put(def.id(), a);
        w.spawnEntity(b);
        b.begin(a.healthScale);
        a.bar = new ServerBossBar(Text.literal(def.title()), switch (def.tier()) {
            case EARLY -> BossBar.Color.GREEN;
            case MID -> BossBar.Color.YELLOW;
            case LATE -> BossBar.Color.RED;
            case SECRET -> BossBar.Color.PURPLE;
            case FINAL -> BossBar.Color.WHITE;
        }, def.tier() == Tier.FINAL ? BossBar.Style.NOTCHED_10 : BossBar.Style.PROGRESS);
        for (ServerPlayerEntity p : f) {
            a.participants.computeIfAbsent(p.getUuid(), k -> new int[2]);
            if (a.testFighters.contains(p)) {
                continue;
            }
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(def.title()).formatted(Formatting.BOLD, tierColor(def.tier()))));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(tierLine(def.tier())).formatted(Formatting.GRAY)));
            Advancements.event(p, "boss_awakened", def.id());
        }
        EncounterState st = EncounterState.get(w.getServer());
        st.record(def.id()).attempts++;
        st.markDirty();
        audio(a, "activate", def.tier().ordinal());
        Underdeep.LOGGER.info("Encounter {} awakened by {} player(s) (health x{})", def.id(), a.count, String.format("%.2f", a.healthScale));
        return a;
    }

    static Formatting tierColor(Tier t) {
        return switch (t) {
            case EARLY -> Formatting.GREEN;
            case MID -> Formatting.YELLOW;
            case LATE -> Formatting.RED;
            case SECRET -> Formatting.LIGHT_PURPLE;
            case FINAL -> Formatting.WHITE;
        };
    }

    static String tierLine(Tier t) {
        return switch (t) {
            case EARLY -> "A guardian of the old ways";
            case MID, LATE -> "A keeper of the Signs";
            case SECRET -> "Something that should not be here";
            case FINAL -> "The dream of the deep";
        };
    }

    static void applyScale(Active a) {
        int n = Math.max(1, Math.min(cfg().maxScaledPlayers, a.count));
        a.healthScale = cfg().healthMultiplier * (1 + cfg().healthPerExtraPlayer * (n - 1));
        a.damageScale = cfg().damageMultiplier * (1 + 0.08 * (n - 1));
    }

    // ------------------------------------------------------------------------------------- tick

    public static void tick(MinecraftServer server) {
        Ending.tick(server);
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (Active a : new ArrayList<>(ACTIVE.values())) {
            if (a.ended) {
                ACTIVE.remove(a.def.id());
                continue;
            }
            long now = a.world.getTime();
            List<ServerPlayerEntity> f = a.fighters();
            // participants and late joiners (scaling only rises, and only early in the fight)
            for (ServerPlayerEntity p : f) {
                int[] r = a.participants.get(p.getUuid());
                if (r == null) {
                    r = new int[2];
                    a.participants.put(p.getUuid(), r);
                    if (now - a.started < 1800 && a.participants.size() > a.count) {
                        a.count = a.participants.size();
                        applyScale(a);
                        if (a.boss != null) {
                            a.boss.rescale(a.healthScale);
                        }
                    }
                    if (!a.testFighters.contains(p)) {
                        ServerPlayNetworking.send(p, new Audio("activate", a.def.id(), a.def.tier().ordinal()));
                    }
                }
                r[1]++;
            }
            tickDowned(a, now);
            boolean anyone = f.stream().anyMatch(p -> !a.downed(p));
            if (!anyone) {
                if (a.emptySince < 0) {
                    a.emptySince = now;
                } else if (now - a.emptySince > cfg().resetAfterSeconds * 20L) {
                    lost(a, "retreat");
                    continue;
                }
            } else {
                a.emptySince = -1;
            }
            if (a.bar != null && a.boss != null) {
                a.bar.setPercent(Math.max(0, a.boss.getHealth() / a.boss.getMaxHealth()));
                if (now % 20 == 0) {
                    for (ServerPlayerEntity p : new ArrayList<>(a.bar.getPlayers())) {
                        if (!f.contains(p)) {
                            a.bar.removePlayer(p);
                        }
                    }
                    for (ServerPlayerEntity p : f) {
                        a.bar.addPlayer(p);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------- downed and revival

    public static boolean tryDown(ServerPlayerEntity p, DamageSource src, float amount) {
        if (!cfg().revive || p.getHealth() - amount > 0 || src.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)
                || src.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL)) {
            return false;
        }
        Active a = arenaAt(p.getWorld(), p.getBlockPos());
        if (a == null || a.downed(p)) {
            return false;
        }
        long now = p.getWorld().getTime();
        if (now - a.lastDowned.getOrDefault(p.getUuid(), -100000L) < 1200) {
            return false; // once per minute: dying still matters
        }
        boolean helper = a.fighters().stream().anyMatch(o -> o != p && !a.downed(o));
        if (!helper) {
            return false;
        }
        a.downed.put(p.getUuid(), new long[]{now + 300, 0});
        a.lastDowned.put(p.getUuid(), now);
        p.setHealth(1);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 300, 4, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 300, 9, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 300, 0, false, false));
        a.tell(p.getName().getString() + " is down! Crouch beside them to help them up.", Formatting.RED);
        return true;
    }

    static void tickDowned(Active a, long now) {
        for (var it = a.downed.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayerEntity p = a.world.getServer().getPlayerManager().getPlayer(e.getKey());
            if (p == null) {
                p = a.testFighters.stream().filter(t -> t.getUuid().equals(e.getKey())).findFirst().orElse(null);
            }
            long[] d = e.getValue();
            if (p == null || !p.isAlive()) {
                it.remove();
                continue;
            }
            ServerPlayerEntity helper = null;
            for (ServerPlayerEntity o : a.fighters()) {
                if (o != p && !a.downed(o) && o.isSneaking() && o.squaredDistanceTo(p) < 6.5) {
                    helper = o;
                    break;
                }
            }
            if (helper != null) {
                d[1]++;
                if (d[1] % 10 == 0) {
                    a.world.spawnParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.2, p.getZ(), 1, 0.3, 0.2, 0.3, 0);
                }
                if (d[1] >= 50) {
                    it.remove();
                    p.removeStatusEffect(StatusEffects.SLOWNESS);
                    p.removeStatusEffect(StatusEffects.WEAKNESS);
                    p.removeStatusEffect(StatusEffects.GLOWING);
                    p.setHealth(Math.min(p.getMaxHealth(), 8));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));
                    a.participate(helper, 0);
                    a.tell(helper.getName().getString() + " helped " + p.getName().getString() + " up!", Formatting.GREEN);
                    Advancements.event(helper, "boss_revive", a.def.id());
                }
            } else if (now >= d[0]) {
                it.remove();
                p.damage(p.getDamageSources().outOfWorld(), Float.MAX_VALUE);
            }
        }
    }

    // ------------------------------------------------------------------------------------- end of a fight

    /** The fight ended without a victory: the guardian sleeps again and the arena is restored. */
    public static void lost(Active a, String reason) {
        if (a.ended) {
            return;
        }
        audio(a, reason.equals("retreat") ? "retreat" : "defeat", 0);
        for (ServerPlayerEntity p : a.fighters()) {
            p.sendMessage(Text.literal("The guardian sinks back into its slumber.").formatted(Formatting.GRAY), false);
        }
        end(a, true);
    }

    static void end(Active a, boolean discardBoss) {
        a.ended = true;
        ACTIVE.remove(a.def.id());
        if (a.bar != null) {
            a.bar.clearPlayers();
        }
        if (a.boss != null) {
            a.boss.clearMinions();
            a.boss.brain.onReset();
            if (discardBoss && !a.boss.isRemoved() && a.boss.isAlive()) {
                a.boss.discard();
            }
        }
        for (UUID u : a.downed.keySet()) {
            ServerPlayerEntity p = a.world.getServer().getPlayerManager().getPlayer(u);
            if (p != null) {
                p.removeStatusEffect(StatusEffects.SLOWNESS);
                p.removeStatusEffect(StatusEffects.WEAKNESS);
                p.removeStatusEffect(StatusEffects.GLOWING);
            }
        }
        a.downed.clear();
        restoreArenas(a.world.getServer());
    }

    /** Puts back every arena block changed by fights (also at start-up, after a crash). */
    static void restoreArenas(MinecraftServer server) {
        EncounterState st = EncounterState.get(server);
        if (st.temp.isEmpty()) {
            return;
        }
        ServerWorld w = server.getOverworld();
        for (var e : st.temp.entrySet()) {
            BlockPos pos = BlockPos.fromLong(e.getKey());
            if (w.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4) || true) {
                w.setBlockState(pos, e.getValue(), Block.NOTIFY_ALL);
            }
        }
        st.temp.clear();
        st.markDirty();
    }

    /** Victory: credit participants (once each), give rewards, record the world fact. */
    static void victory(Active a, BossEntity b) {
        if (a.ended) {
            return;
        }
        EncounterState st = EncounterState.get(a.world.getServer());
        EncounterState.BossRecord rec = st.record(a.def.id());
        boolean firstWorld = rec.defeats == 0;
        rec.defeats++;
        if (rec.firstDefeat < 0) {
            rec.firstDefeat = a.world.getTime();
        }
        List<ServerPlayerEntity> credited = new ArrayList<>();
        for (var e : a.participants.entrySet()) {
            ServerPlayerEntity p = a.world.getServer().getPlayerManager().getPlayer(e.getKey());
            if (p == null) {
                p = a.testFighters.stream().filter(t -> t.getUuid().equals(e.getKey())).findFirst().orElse(null);
            }
            int[] r = e.getValue();
            if (p != null && (r[0] > 0 || r[1] >= 600)) {
                credited.add(p);
            }
        }
        // claims first (same tick), then items
        Map<ServerPlayerEntity, Boolean> first = new LinkedHashMap<>();
        for (ServerPlayerEntity p : credited) {
            first.put(p, rec.credited.add(p.getUuidAsString()));
        }
        st.markDirty();
        audio(a, "victory", a.def.tier().ordinal());
        for (ServerPlayerEntity p : credited) {
            boolean f = first.get(p);
            for (ItemStack s : roll(a.world, p, "underdeep:bosses/" + a.def.id() + (f ? "" : "_echo"))) {
                give(p, s);
            }
            if (f && a.def.sign() != null && !st.doorSigns.contains(signKey(a.def.sign()))) {
                give(p, new ItemStack(Registries.ITEM.get(Identifier.of(a.def.sign()))));
            }
            if (!a.testFighters.contains(p)) {
                p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(f ? "Victory" : "Victory again").formatted(Formatting.GOLD, Formatting.BOLD)));
                p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(a.def.title() + " is defeated").formatted(Formatting.GRAY)));
            }
            Advancements.event(p, "boss_defeated", a.def.id());
            Advancements.event(p, "boss_tier_" + a.def.tier().name().toLowerCase(), a.def.id());
            if (credited.size() >= 2) {
                Advancements.event(p, "boss_together", a.def.id());
            }
            if (credited.size() == 1) {
                Advancements.event(p, "boss_solo", a.def.id());
            }
            if (!a.downed.containsKey(p.getUuid()) && p.getHealth() >= p.getMaxHealth() * 0.5 && a.lastDowned.isEmpty()) {
                Advancements.event(p, "boss_clean", a.def.id());
            }
        }
        if (firstWorld) {
            for (ServerPlayerEntity p : a.world.getServer().getPlayerManager().getPlayerList()) {
                if (!credited.contains(p)) {
                    p.sendMessage(Text.literal("Somewhere below, a great guardian has fallen.").formatted(Formatting.DARK_AQUA, Formatting.ITALIC), false);
                }
            }
        }
        if (a.def.tier() == Tier.FINAL) {
            Ending.victory(a, credited, st);
        }
        end(a, false);
    }

    static String signKey(String itemId) {
        return itemId.substring(itemId.indexOf(':') + 1);
    }

    static void reclaim(ServerPlayerEntity p, BossDef def, EncounterState st) {
        if (def.sign() == null) {
            p.sendMessage(Text.literal("The altar has nothing to return to you.").formatted(Formatting.GRAY), true);
            return;
        }
        String key = signKey(def.sign());
        var item = Registries.ITEM.get(Identifier.of(def.sign()));
        if (!st.record(def.id()).credited.contains(p.getUuidAsString())) {
            p.sendMessage(Text.literal("The altar does not know you. Defeat its guardian first.").formatted(Formatting.GRAY), true);
        } else if (st.doorSigns.contains(key)) {
            p.sendMessage(Text.literal("This Sign already rests in the Heart Engine door.").formatted(Formatting.GRAY), true);
        } else if (p.getInventory().containsAny(s -> s.isOf(item))) {
            p.sendMessage(Text.literal("You already carry this Sign.").formatted(Formatting.GRAY), true);
        } else {
            give(p, new ItemStack(item));
            p.sendMessage(Text.literal("The altar remembers you, and the Sign takes shape again in your hands.").formatted(Formatting.GOLD), false);
            Advancements.event(p, "sign_reclaimed", key);
        }
    }

    public static List<ItemStack> roll(ServerWorld w, ServerPlayerEntity p, String table) {
        LootTable t = w.getServer().getReloadableRegistries().getLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(table)));
        LootContextParameterSet params = new LootContextParameterSet.Builder(w).add(LootContextParameters.ORIGIN, p.getPos())
                .add(LootContextParameters.THIS_ENTITY, p).luck(p.getLuck()).build(LootContextTypes.CHEST);
        return t.generateLoot(params, w.random.nextLong());
    }

    static void give(ServerPlayerEntity p, ItemStack s) {
        ItemStack c = s.copy();
        if (!p.getInventory().insertStack(c) && !c.isEmpty()) {
            p.dropItem(c, false);
        }
        dev.underdeep.explore.Rewards.found(p, s);
    }

    /** For tests and the heirloom pedestal: whether a guardian has ever been defeated in this world. */
    public static boolean defeated(MinecraftServer server, String bossId) {
        return EncounterState.get(server).defeated(bossId);
    }

    public static Vec3d centre(Active a) {
        return Vec3d.ofBottomCenter(a.center);
    }
}
