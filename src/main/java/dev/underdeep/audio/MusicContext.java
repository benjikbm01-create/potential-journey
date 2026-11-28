package dev.underdeep.audio;

import dev.underdeep.Underdeep;
import dev.underdeep.civ.settlement.SettlementMap;
import dev.underdeep.civ.settlement.SettlementSite;
import dev.underdeep.civ.settlement.Settlements;
import dev.underdeep.world.UnderdeepWorlds;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stage 11 music context (server side). The client decides most of its music context itself (biome, combat, riding,
 * boss and exploration payloads), but only the server knows where settlements are (pure SettlementMap, no chunk loading)
 * and where a player's home is (their respawn point). Every two seconds each player gets {@code "settlement:<civ>"},
 * {@code "home"} or {@code ""} — sent only when it changes. Cheap: one map lookup per player.
 */
public final class MusicContext {
    private static final Map<UUID, String> LAST = new HashMap<>();

    private MusicContext() {
    }

    public record Payload(String context) implements CustomPayload {
        public static final Id<Payload> ID = new Id<>(Identifier.of(Underdeep.MOD_ID, "music_context"));
        public static final PacketCodec<ByteBuf, Payload> CODEC = PacketCodecs.STRING.xmap(Payload::new, Payload::context);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Payload.ID, Payload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 40 != 7) {
                return;
            }
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (!ServerPlayNetworking.canSend(p, Payload.ID)) {
                    continue;
                }
                String ctx = contextOf(p);
                if (!ctx.equals(LAST.get(p.getUuid()))) {
                    LAST.put(p.getUuid(), ctx);
                    ServerPlayNetworking.send(p, new Payload(ctx));
                }
            }
            if (LAST.size() > server.getPlayerManager().getCurrentPlayerCount() * 2 + 16) {
                LAST.keySet().removeIf(u -> server.getPlayerManager().getPlayer(u) == null);
            }
        });
    }

    /** The context for a player: inside a settlement's footprint (any layer height near its floor), at home, or none. */
    public static String contextOf(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld w) || !UnderdeepWorlds.isUnderdeep(w)) {
            return "";
        }
        BlockPos pos = p.getBlockPos();
        SettlementMap map = Settlements.of(w);
        if (map != null) {
            SettlementSite s = map.siteAt(pos.getX(), pos.getZ(), 6);
            if (s != null && Math.abs(pos.getY() - s.floorY()) < 28) {
                return "settlement:" + s.civ().id();
            }
        }
        BlockPos home = p.getSpawnPointPosition();
        if (home != null && p.getSpawnPointDimension() == w.getRegistryKey() && home.getSquaredDistance(pos) < 28 * 28) {
            return "home";
        }
        return "";
    }
}
