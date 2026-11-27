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

    /**
     * S2C: the player's personal journal pages (places, leads, records, collection). Each entry is
     * "kind \u0001 id \u0001 title \u0001 icon \u0001 body", body paragraphs separated by '\n'. "{at:x,y,z}" in a body is shown by
     * the client as the live distance and direction to that position.
     */
    public record Journal(java.util.List<String> entries) implements CustomPayload {
        public static final Id<Journal> ID = new Id<>(Identifier.of(Underdeep.MOD_ID, "explore_journal"));
        public static final PacketCodec<ByteBuf, Journal> CODEC = PacketCodecs.STRING.collect(PacketCodecs.toList()).xmap(Journal::new, Journal::entries);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** The eight nearest places the player has discovered (name, position), for the delver's compass. */
    public static java.util.List<net.minecraft.util.Pair<String, BlockPos>> compassTargets(ServerPlayerEntity p) {
        java.util.List<net.minecraft.util.Pair<String, BlockPos>> out = new java.util.ArrayList<>();
        ExplorationMap map = p.getWorld() instanceof ServerWorld w ? of(w) : null;
        if (map == null) {
            return out;
        }
        java.util.List<Site> found = new java.util.ArrayList<>();
        for (long id : ExplorerData.of(p).sites.keySet()) {
            Site s = map.byId(id);
            if (s != null) {
                found.add(s);
            }
        }
        found.sort((a, b) -> Double.compare(a.dist(p.getX(), p.getZ()), b.dist(p.getX(), p.getZ())));
        for (Site s : found.subList(0, Math.min(8, found.size()))) {
            out.add(new net.minecraft.util.Pair<>(s.family().name(), new BlockPos(s.x(), s.y() + 1, s.z())));
        }
        return out;
    }

    public static void sendJournal(ServerPlayerEntity p) {
        if (!ServerPlayNetworking.canSend(p, Journal.ID) || !(p.getWorld() instanceof ServerWorld w)) {
            return;
        }
        ExplorationMap map = of(w);
        ExplorerData d = ExplorerData.of(p);
        java.util.List<String> out = new java.util.ArrayList<>();
        ExplorationState st = ExplorationState.get(w.getServer());
        if (map != null) {
            for (var e : d.sites.entrySet()) {
                Site s = map.byId(e.getKey());
                if (s == null) {
                    continue;
                }
                ExploreContent.Family f = s.family();
                StringBuilder b = new StringBuilder();
                b.append(f.desc()).append('\n');
                b.append(switch (f.category()) {
                    case DUNGEON -> "A sealed complex";
                    case LANDMARK -> "A landmark";
                    case SECRET -> "A hidden place";
                    default -> "An abandoned place";
                }).append(" in the ").append(s.region().displayName()).append(" (day ").append(e.getValue() / 24000 + 1).append(").\n");
                b.append("Where: x ").append(s.x()).append(", y ").append(s.y() + 1).append(", z ").append(s.z()).append(" — {at:").append(s.x()).append(',')
                        .append(s.y() + 1).append(',').append(s.z()).append("}\n");
                if (d.completed.contains(s.id())) {
                    b.append("You reached its heart.\n");
                }
                ExplorationState.SiteState ss = st.peek(s.id());
                if (ss != null && ss.discoverers.size() > 1) {
                    b.append(ss.discoverers.size()).append(" explorers have been here.\n");
                }
                for (String k : d.secrets) {
                    if (k.startsWith(s.id() + ":")) {
                        ExploreContent.SecretDef def = ExploreContent.SECRETS.get(k.substring(k.indexOf(':') + 1));
                        if (def != null) {
                            b.append("Hidden here: ").append(def.name()).append(".\n");
                        }
                    }
                }
                out.add(String.join("\u0001", "place", Long.toString(s.id(), 36), f.name(), "minecraft:recovery_compass", b.toString().trim()));
            }
            for (var e : d.leads.entrySet()) {
                Site s = map.byId(e.getKey());
                if (s == null || d.sites.containsKey(s.id())) {
                    continue;
                }
                boolean chart = e.getValue().equals("chart");
                String from = e.getValue().startsWith("rumour:") ? "Heard from " + e.getValue().substring(7) : chart ? "From a surveyor's chart" : "Lights led this way";
                String where = chart ? "Where: x " + s.x() + ", z " + s.z() + " — {at:" + s.x() + "," + (s.y() + 1) + "," + s.z() + "}"
                        : "Roughly: {near:" + (Math.round(s.x() / 50.0) * 50) + "," + (s.y() + 1) + "," + (Math.round(s.z() / 50.0) * 50) + "}";
                out.add(String.join("\u0001", "lead", Long.toString(s.id(), 36), "Lead: " + s.family().name(), "minecraft:paper", from + ".\n" + where));
            }
        }
        for (String r : d.records) {
            ExploreContent.Record rec = ExploreContent.RECORDS.get(r);
            if (rec != null) {
                out.add(String.join("\u0001", "record", r, rec.title(), "underdeep:lore_fragment", rec.text() + "\n— " + rec.voice()));
            }
        }
        for (String a : d.artifacts) {
            ExploreContent.Artifact art = ExploreContent.ARTIFACTS.get(a);
            if (art != null) {
                out.add(String.join("\u0001", "artifact", a, "", "underdeep:" + a, art.desc() + "\nRarity: " + art.rarity() + "."));
            }
        }
        ServerPlayNetworking.send(p, new Journal(out));
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
        PayloadTypeRegistry.playS2C().register(Journal.ID, Journal.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> server.execute(() -> sendJournal(h.player)));
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
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, reg, env) -> ExploreCommands.register(d));
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
