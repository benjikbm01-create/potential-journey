package dev.underdeep.survival;

import dev.underdeep.Underdeep;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Recipe knowledge (Stage 7). Basic recipes are known from the start. Others are learned by cooking them (any
 * valid combination works at a station even when unknown: experimenting is allowed), by eating the dish, or by
 * asking a settlement's cooks, who teach their civilization's regional recipes. Knowledge only affects what the
 * station screens and the journal show; it never blocks cooking.
 */
public final class Recipes {
    private Recipes() {
    }

    /** Client-side copy of the local player's known recipes. */
    public static final Set<String> CLIENT_KNOWN = new LinkedHashSet<>();

    public record Sync(List<String> known) implements CustomPayload {
        public static final Id<Sync> ID = new Id<>(Identifier.of(Underdeep.MOD_ID, "recipes_sync"));
        public static final PacketCodec<RegistryByteBuf, Sync> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeVarInt(p.known.size());
            p.known.forEach(buf::writeString);
        }, buf -> {
            int n = Math.min(4096, buf.readVarInt());
            List<String> l = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                l.add(buf.readString());
            }
            return new Sync(l);
        });

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Sync.ID, Sync.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> sync(handler.player)));
    }

    public static boolean knows(PlayerEntity p, SurvivalContent.Recipe r) {
        return r.basic() || (p.getWorld().isClient ? CLIENT_KNOWN.contains(r.id()) : SurvivalData.of(p).known.contains(r.id()));
    }

    /** Teaches a recipe; returns true if it was new. {@code how} completes "You learned X by ...". */
    public static boolean learn(ServerPlayerEntity p, SurvivalContent.Recipe r, String how) {
        if (r.basic() || !SurvivalData.of(p).known.add(r.id())) {
            return false;
        }
        p.sendMessage(Text.literal("New recipe (" + how + "): " + r.resultItem().getName().getString() + " at the "
                + SurvivalContent.stationName(r.station())).formatted(Formatting.YELLOW), false);
        sync(p);
        dev.underdeep.guide.Advancements.event(p, "recipe_learned", r.id());
        return true;
    }

    public static void sync(ServerPlayerEntity p) {
        if (ServerPlayNetworking.canSend(p, Sync.ID)) {
            ServerPlayNetworking.send(p, new Sync(new ArrayList<>(SurvivalData.of(p).known)));
        }
    }

    public static int knownCount(ServerPlayerEntity p) {
        int n = 0;
        for (SurvivalContent.Recipe r : SurvivalContent.recipes()) {
            if (knows(p, r)) {
                n++;
            }
        }
        return n;
    }
}
