package dev.underdeep.client.equipment;

import dev.underdeep.equipment.Accessories;
import dev.underdeep.equipment.Artisan;
import dev.underdeep.equipment.EquipmentContent;
import dev.underdeep.equipment.EquipmentItems;
import dev.underdeep.equipment.Upgrades;
import dev.underdeep.equipment.Weapons;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Client side of Stage 8 equipment: screens, keybinds, bow animation, upgrade tooltips, accessory flag sync. */
public final class EquipmentClient {
    private EquipmentClient() {
    }

    public static KeyBinding ACCESSORIES;

    public static void init() {
        HandledScreens.register(Accessories.SCREEN, AccessoryScreen::new);
        HandledScreens.register(Artisan.SCREEN, ArtisanScreen::new);
        ACCESSORIES = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.underdeep.accessories", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K,
                "key.categories.underdeep"));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (ACCESSORIES.wasPressed()) {
                if (mc.player != null && ClientPlayNetworking.canSend(Accessories.Open.ID)) {
                    ClientPlayNetworking.send(new Accessories.Open());
                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Accessories.Flags.ID, (payload, ctx) -> ctx.client().execute(() -> {
            Accessories.CLIENT_FLAGS.clear();
            Accessories.CLIENT_FLAGS.addAll(payload.flags());
        }));
        for (EquipmentContent.Bow b : EquipmentContent.BOWS) {
            Item bow = EquipmentItems.get(b.id());
            ModelPredicateProviderRegistry.register(bow, Identifier.ofVanilla("pull"), (stack, world, entity, seed) ->
                    entity == null || entity.getActiveItem() != stack ? 0f
                            : ((Weapons.EquipBowItem) bow).pull(stack, stack.getMaxUseTime(entity) - entity.getItemUseTimeLeft()));
            ModelPredicateProviderRegistry.register(bow, Identifier.ofVanilla("pulling"), (stack, world, entity, seed) ->
                    entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1f : 0f);
        }
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.contains(EquipmentItems.UPGRADES) || (EquipmentItems.category(stack.getItem()) != null
                    && dev.underdeep.registry.Reg.id("x").getNamespace().equals(net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getNamespace()))) {
                Upgrades.tooltip(stack, lines);
            }
        });
    }

    /** Accessory screen: two rings, an amulet, a charm. */
    public static class AccessoryScreen extends HandledScreen<Accessories.Handler> {
        public AccessoryScreen(Accessories.Handler h, PlayerInventory inv, Text title) {
            super(h, inv, title);
        }

        @Override
        protected void drawBackground(DrawContext c, float delta, int mx, int my) {
            Screens.panel(c, x, y, backgroundWidth, backgroundHeight);
            for (Slot s : handler.slots) {
                Screens.slot(c, x + s.x, y + s.y);
            }
        }

        @Override
        protected void drawForeground(DrawContext c, int mx, int my) {
            c.drawText(textRenderer, title, 8, 6, 0x404040, false);
            String[] labels = {"Ring", "Ring", "Amulet", "Charm"};
            for (int i = 0; i < 4; i++) {
                c.drawText(textRenderer, labels[i], 53 + i * 18 + 8 - textRenderer.getWidth(labels[i]) / 2, 47, 0x606060, false);
            }
            c.drawText(textRenderer, playerInventoryTitle, 8, 72, 0x404040, false);
        }

        @Override
        public void render(DrawContext c, int mx, int my, float d) {
            super.render(c, mx, my, d);
            drawMouseoverTooltip(c, mx, my);
        }
    }

    /** Artisan's bench: equipment + kit/material → preview; Salvage button. */
    public static class ArtisanScreen extends HandledScreen<Artisan.Handler> {
        public ArtisanScreen(Artisan.Handler h, PlayerInventory inv, Text title) {
            super(h, inv, title);
        }

        @Override
        protected void init() {
            super.init();
            addDrawableChild(ButtonWidget.builder(Text.literal("Salvage"), b -> {
                if (client != null && client.interactionManager != null) {
                    client.interactionManager.clickButton(handler.syncId, 0);
                }
            }).dimensions(x + 104, y + 58, 60, 16).build());
        }

        @Override
        protected void drawBackground(DrawContext c, float delta, int mx, int my) {
            Screens.panel(c, x, y, backgroundWidth, backgroundHeight);
            for (Slot s : handler.slots) {
                Screens.slot(c, x + s.x, y + s.y);
            }
            c.drawText(textRenderer, "+", x + 58, y + 39, 0x404040, false);
            c.fill(x + 100, y + 42, x + 124, y + 44, 0xFF606060);
        }

        @Override
        protected void drawForeground(DrawContext c, int mx, int my) {
            c.drawText(textRenderer, title, 8, 6, 0x404040, false);
            ItemStack eq = handler.input.getStack(0), mod = handler.input.getStack(1);
            String why = Artisan.explain(eq, mod);
            if (why != null) {
                var lines = textRenderer.wrapLines(Text.literal(why), 90);
                for (int i = 0; i < Math.min(2, lines.size()); i++) {
                    c.drawText(textRenderer, lines.get(i), 8, 56 + i * 9, 0x404040, false);
                }
            }
            c.drawText(textRenderer, playerInventoryTitle, 8, 72, 0x404040, false);
        }

        @Override
        public void render(DrawContext c, int mx, int my, float d) {
            super.render(c, mx, my, d);
            drawMouseoverTooltip(c, mx, my);
        }
    }

    static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }
}
