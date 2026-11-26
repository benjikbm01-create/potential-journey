package dev.underdeep.civ.npc;

import dev.underdeep.Underdeep;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialogue networking (Stage 5). The server decides everything (lines, options, effects); the client only
 * displays a {@link Open} payload and reports the chosen option id with {@link Choose}. Common code: the
 * dedicated server never loads client classes.
 */
public final class DialoguePayloads {
    private DialoguePayloads() {
    }

    /** Server → client: show (or update) the dialogue with an inhabitant. */
    public record Open(int entityId, String name, String subtitle, String text, List<String> optionIds, List<String> optionLabels)
            implements CustomPayload {
        public static final Id<Open> ID = new Id<>(Identifier.of(Underdeep.MOD_ID, "dialogue_open"));
        public static final PacketCodec<RegistryByteBuf, Open> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeVarInt(p.entityId);
            buf.writeString(p.name);
            buf.writeString(p.subtitle);
            buf.writeString(p.text, 4096);
            buf.writeVarInt(p.optionIds.size());
            for (int i = 0; i < p.optionIds.size(); i++) {
                buf.writeString(p.optionIds.get(i));
                buf.writeString(p.optionLabels.get(i));
            }
        }, buf -> {
            int id = buf.readVarInt();
            String name = buf.readString();
            String sub = buf.readString();
            String text = buf.readString(4096);
            int n = Math.min(16, buf.readVarInt());
            List<String> ids = new ArrayList<>(), labels = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                ids.add(buf.readString());
                labels.add(buf.readString());
            }
            return new Open(id, name, sub, text, ids, labels);
        });

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Client → server: the player picked an option (or closed the dialogue: "bye"). */
    public record Choose(int entityId, String option) implements CustomPayload {
        public static final Id<Choose> ID = new Id<>(Identifier.of(Underdeep.MOD_ID, "dialogue_choose"));
        public static final PacketCodec<RegistryByteBuf, Choose> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeVarInt(p.entityId);
            buf.writeString(p.option, 64);
        }, buf -> new Choose(buf.readVarInt(), buf.readString(64)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Open.ID, Open.CODEC);
        PayloadTypeRegistry.playC2S().register(Choose.ID, Choose.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Choose.ID, (payload, ctx) -> ctx.player().server.execute(
                () -> Dialogue.choose(ctx.player(), payload.entityId(), payload.option())));
    }
}
