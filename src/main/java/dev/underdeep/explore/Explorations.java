package dev.underdeep.explore;

import dev.underdeep.Underdeep;
import dev.underdeep.civ.settlement.Settlements;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.data.UnderdeepWorldState;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.StructureWorldAccess;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Stage 9 entry point. Exploration sites generate only in Underdeep worlds created with terrain generator v4+ (so
 * existing worlds never get half-built sites at the edge of explored chunks), unless {@code exploration.inOlderWorlds}.
 * Runtime systems (discovery, puzzles, traps, rewards, encounters) are cheap per-player checks against the pure map.
 */
public final class Explorations {
    public static final int SINCE_VERSION = 4;
    private static final Map<UnderdeepTerrain, ExplorationMap> MAPS = Collections.synchronizedMap(new WeakHashMap<>());

    private Explorations() {
    }

    /** S2C: the exploration audio context the player is in (only discovered sites; Stage 11 music hooks). */
    public record Context(String context) implements CustomPayload {
        public static final Id<Context> ID = new Id<>(Identifier.of(Underdeep.MOD_ID, "explore_context"));
        public static final PacketCodec<ByteBuf, Context> CODEC = PacketCodecs.STRING.xmap(Context::new, Context::context);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static volatile net.minecraft.server.MinecraftServer SERVER;

    @Nullable
    public static net.minecraft.server.MinecraftServer serverRef() {
        return SERVER;
    }

    public static void init() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(s -> SERVER = s);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> SERVER = null);
        ExploreContent.load();
        ExploreBlocks.init();
        ExploreItems.init();
        ExplorerData.init();
        Mechanisms.init();
        PayloadTypeRegistry.playS2C().register(Context.ID, Context.CODEC);
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() != World.OVERWORLD || !UnderdeepWorlds.isUnderdeep(world)) {
                return;
            }
            UnderdeepTerrain t = UnderdeepWorlds.terrain(world);
            if (t == null) {
                return;
            }
            UnderdeepWorldState state = UnderdeepWorldState.get(world);
            boolean fresh = state.createdAtGameTime() < 0;
            int version = fresh ? UnderdeepTerrain.GENERATOR_VERSION : state.createdWithGeneratorVersion();
            UnderdeepConfig.Exploration cfg = UnderdeepConfig.get().exploration;
            boolean enabled = cfg.enabled && (version >= SINCE_VERSION || cfg.inOlderWorlds);
            if (enabled) {
                MAPS.put(t, new ExplorationMap(t, Settlements.of(t), cfg.density, cfg.dungeonDensity));
            } else {
                MAPS.remove(t);
            }
            Underdeep.LOGGER.info("Underdeep exploration sites {} for this world (created with generator v{})", enabled ? "enabled" : "disabled", version);
        });
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            UnderdeepTerrain t = UnderdeepWorlds.terrain(world);
            if (t != null) {
                MAPS.remove(t);
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(w -> {
            if (w.getTime() % 20 != 7) {
                return;
            }
            ExplorationMap map = of(w);
            if (map == null) {
                return;
            }
            for (ServerPlayerEntity p : w.getPlayers()) {
                try {
                    Discovery.tick(w, p, map);
                    TravelEncounters.tick(w, p, map);
                } catch (RuntimeException e) {
                    Underdeep.LOGGER.warn("exploration tick failed for {}", p.getName().getString(), e);
                }
            }
        });
        ExploreDialogue.init();
    }

    /** Test/diagnostic hook: force a map for a terrain. */
    public static ExplorationMap enableFor(UnderdeepTerrain t) {
        return MAPS.computeIfAbsent(t, k -> new ExplorationMap(k, Settlements.of(k), UnderdeepConfig.get().exploration.density,
                UnderdeepConfig.get().exploration.dungeonDensity));
    }

    @Nullable
    public static ExplorationMap of(@Nullable UnderdeepTerrain t) {
        return t == null ? null : MAPS.get(t);
    }

    @Nullable
    public static ExplorationMap of(WorldAccess world) {
        ServerWorld sw = world instanceof ServerWorld s ? s : world instanceof StructureWorldAccess swa ? swa.toServerWorld() : null;
        if (sw == null || !UnderdeepWorlds.isUnderdeep(sw)) {
            return null;
        }
        return of(UnderdeepWorlds.terrain(sw));
    }

    public static it.unimi.dsi.fastutil.longs.LongOpenHashSet wards(ServerWorld w) {
        return ExplorationState.get(w.getServer()).wards;
    }

    /** Whether a warding bell within 16 blocks prevents natural hostile spawns at this position. */
    public static boolean warded(ServerWorld w, BlockPos pos) {
        var set = ExplorationState.get(w.getServer()).wards;
        if (set.isEmpty()) {
            return false;
        }
        for (long l : set) {
            if (BlockPos.fromLong(l).getSquaredDistance(pos) <= 16 * 16) {
                return true;
            }
        }
        return false;
    }

    public static void sendContext(ServerPlayerEntity p, String context) {
        if (ServerPlayNetworking.canSend(p, Context.ID)) {
            ServerPlayNetworking.send(p, new Context(context));
        }
    }
}
