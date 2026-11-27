package dev.underdeep.client.mixin;

import dev.underdeep.client.combat.CombatClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stage 10: sneak + attack with a melee weapon sends a heavy-attack intent instead of a light attack. */
@Mixin(MinecraftClient.class)
public abstract class HeavyAttackMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void underdeep$heavy(CallbackInfoReturnable<Boolean> cir) {
        if (CombatClient.tryHeavy((MinecraftClient) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
