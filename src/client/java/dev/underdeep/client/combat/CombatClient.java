package dev.underdeep.client.combat;

import dev.underdeep.combat.Combat;
import dev.underdeep.combat.WeaponStyle;
import dev.underdeep.config.UnderdeepConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side of Stage 10 combat: the dodge key, heavy attacks on sneak + attack, the stamina bar and the wind-up
 * pose of players preparing a heavy blow. The client only sends intents; the server validates and times everything.
 */
public final class CombatClient {
    private CombatClient() {
    }

    public static KeyBinding DODGE;
    public static float stamina = Combat.MAX_STAMINA;
    public static int flags;
    private static long localDodgeReady;
    /** entity id → {start tick, length} of a heavy wind-up (animation only). */
    public static final Map<Integer, long[]> WINDUPS = new HashMap<>();
    private static long clientTicks;

    public static void init() {
        DODGE = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.underdeep.dodge", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT,
                "key.categories.underdeep"));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            clientTicks++;
            while (DODGE.wasPressed()) {
                tryDodge(mc);
            }
            WINDUPS.values().removeIf(w -> clientTicks > w[0] + w[1] + 6);
        });
        ClientPlayNetworking.registerGlobalReceiver(Combat.Hud.ID, (p, ctx) -> ctx.client().execute(() -> {
            stamina = p.stamina();
            flags = p.flags();
        }));
        ClientPlayNetworking.registerGlobalReceiver(Combat.Windup.ID, (p, ctx) -> ctx.client().execute(() ->
                WINDUPS.put(p.entity(), new long[]{clientTicks, p.ticks()})));
        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden || mc.player.isSpectator() || mc.player.isCreative() && stamina >= Combat.MAX_STAMINA) {
                return;
            }
            if (stamina >= Combat.MAX_STAMINA && (flags & 1) == 0) {
                return;
            }
            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
            int x = w / 2 + 10, y = h - 49 - (mc.player.isSubmergedInWater() || mc.player.getAir() < mc.player.getMaxAir() ? 10 : 0);
            int len = 81;
            ctx.fill(x, y, x + len, y + 3, 0x90000000);
            int fill = (int) (len * stamina / Combat.MAX_STAMINA);
            int col = stamina < 25 ? 0xFFD04A2A : (flags & 1) != 0 ? 0xFFFFE070 : 0xFFE8B84A;
            ctx.fill(x, y, x + fill, y + 3, col);
            if ((flags & 1) != 0) {
                ctx.drawText(mc.textRenderer, "Opening", x + len - mc.textRenderer.getWidth("Opening"), y - 9, 0xFFFFE070, true);
            }
        });
    }

    public static float windupProgress(int entityId, float tickDelta) {
        long[] w = WINDUPS.get(entityId);
        if (w == null) {
            return -1;
        }
        float t = (clientTicks - w[0] + tickDelta) / Math.max(1, w[1]);
        return t > 1.25f ? -1 : t;
    }

    static void tryDodge(MinecraftClient mc) {
        net.minecraft.client.network.ClientPlayerEntity p = mc.player;
        if (p == null || !ClientPlayNetworking.canSend(Combat.Dodge.ID) || (flags & 2) != 0 || p.isSpectator() || p.hasVehicle()
                || p.getAbilities().flying || p.isTouchingWater()) {
            return;
        }
        if (stamina < UnderdeepConfig.get().combat.dodgeStamina || clientTicks < localDodgeReady || !p.isOnGround()) {
            return;
        }
        localDodgeReady = clientTicks + 14;
        float f = p.input.movementForward, s = p.input.movementSideways;
        p.setVelocity(Combat.dodgeVelocity(p.getYaw(), f, s, true));
        ClientPlayNetworking.send(new Combat.Dodge(f, s));
    }

    /** From the attack mixin: sneak + attack with a melee weapon at full charge becomes a heavy attack. */
    public static boolean tryHeavy(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        if (p == null || !p.isSneaking() || !ClientPlayNetworking.canSend(Combat.Heavy.ID) || !UnderdeepConfig.get().combat.heavyAttacks) {
            return false;
        }
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            return false; // sneak-mining stays mining
        }
        WeaponStyle st = WeaponStyle.of(p.getMainHandStack());
        if (!st.melee() || p.getAttackCooldownProgress(0.5f) < 0.8f || (flags & 10) != 0 || stamina < st.stamina) {
            return false;
        }
        ClientPlayNetworking.send(new Combat.Heavy());
        return true;
    }
}
