package dev.underdeep.client.civ;

import dev.underdeep.civ.npc.DialoguePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Conversation window (Stage 5): name, profession/culture/familiarity line, the inhabitant's words and the
 * options the server offered. The client never decides anything: each button sends the option id and the
 * server answers with an updated {@link DialoguePayloads.Open}. Closing (Esc or "Goodbye") ends the talk.
 */
public final class DialogueScreen extends Screen {
    private DialoguePayloads.Open data;
    private boolean silentClose;

    public DialogueScreen(DialoguePayloads.Open data) {
        super(Text.translatable("screen.underdeep.dialogue"));
        this.data = data;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DialoguePayloads.Open.ID, (payload, ctx) -> {
            MinecraftClient mc = ctx.client();
            if (mc.currentScreen instanceof DialogueScreen ds && ds.data.entityId() == payload.entityId()) {
                ds.data = payload;
                ds.clearAndInit();
            } else {
                if (mc.currentScreen instanceof DialogueScreen ds) {
                    ds.silentClose = true;
                }
                mc.setScreen(new DialogueScreen(payload));
            }
        });
    }

    private int panelTop() {
        return height - Math.max(130, height * 2 / 5);
    }

    @Override
    protected void init() {
        int top = panelTop();
        int textLines = textRenderer.wrapLines(Text.literal(data.text()), width - 60).size();
        int y = top + 34 + textLines * 10 + 8;
        int bw = Math.min(200, (width - 70) / 2);
        int col = 0;
        for (int i = 0; i < data.optionIds().size(); i++) {
            String id = data.optionIds().get(i);
            int x = width / 2 - bw - 5 + col * (bw + 10);
            ButtonWidget b = ButtonWidget.builder(Text.literal(data.optionLabels().get(i)), btn -> choose(id))
                    .dimensions(x, y, bw, 18).build();
            addDrawableChild(b);
            col++;
            if (col == 2) {
                col = 0;
                y += 21;
            }
        }
    }

    private void choose(String id) {
        ClientPlayNetworking.send(new DialoguePayloads.Choose(data.entityId(), id));
        if (id.equals("bye")) {
            silentClose = true;
            close();
        }
    }

    @Override
    public void removed() {
        if (!silentClose && client != null && client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(new DialoguePayloads.Choose(data.entityId(), "bye"));
        }
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // keep the world visible above the panel
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int top = panelTop();
        ctx.fill(20, top, width - 20, height - 8, 0xC0101014);
        ctx.fill(20, top, width - 20, top + 1, 0xFF8A6D3B);
        ctx.drawTextWithShadow(textRenderer, Text.literal(data.name()), 30, top + 8, 0xFFE0B050);
        ctx.drawTextWithShadow(textRenderer, Text.literal(data.subtitle()), 30, top + 19, 0xFF9A9A9A);
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(data.text()), width - 60);
        int y = top + 34;
        for (OrderedText l : lines) {
            ctx.drawTextWithShadow(textRenderer, l, 30, y, 0xFFFFFFFF);
            y += 10;
        }
        super.render(ctx, mouseX, mouseY, delta);
    }
}
