package dev.underdeep.client.guide;

import dev.underdeep.guide.Handbook;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Client side of the handbook: H opens it; server data (acquisition, uses, discoveries) is cached here. */
public final class HandbookClient {
    private HandbookClient() {
    }

    public static KeyBinding KEY;

    public static void init() {
        KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.underdeep.handbook", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H,
                "key.categories.underdeep"));
        Handbook.openClient = () -> MinecraftClient.getInstance().setScreen(new HandbookScreen());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (KEY.wasPressed()) {
                if (mc.player != null && mc.currentScreen == null) {
                    mc.setScreen(new HandbookScreen());
                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Handbook.Data.ID, (p, ctx) -> ctx.client().execute(() -> {
            Handbook.CLIENT_ITEMS.clear();
            p.items().forEach(i -> Handbook.CLIENT_ITEMS.put(i.id(), i));
            Handbook.CLIENT_DISCOVERED.clear();
            Handbook.CLIENT_DISCOVERED.addAll(p.discovered());
        }));
        ClientPlayNetworking.registerGlobalReceiver(Handbook.Discover.ID, (p, ctx) -> ctx.client().execute(() ->
                Handbook.CLIENT_DISCOVERED.addAll(p.keys())));
    }
}
