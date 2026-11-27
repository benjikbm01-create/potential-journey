package dev.underdeep.client.mixin;

import dev.underdeep.client.combat.CombatClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stage 10: a player winding up a heavy attack raises the weapon arm (wind-up), then the normal swing plays at the
 * hit. Timing comes from the server; this is presentation only (Stage 11 replaces it with full animations).
 */
@Mixin(BipedEntityModel.class)
public abstract class WindupPoseMixin<T extends LivingEntity> {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void underdeep$windup(T entity, float limbAngle, float limbDistance, float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity)) {
            return;
        }
        float t = CombatClient.windupProgress(entity.getId(), age - (int) age);
        if (t < 0 || t > 1) {
            return;
        }
        BipedEntityModel<?> m = (BipedEntityModel<?>) (Object) this;
        float k = Math.min(1, t * 1.3f);
        m.rightArm.pitch = m.rightArm.pitch * (1 - k) + (-2.7f) * k;
        m.rightArm.yaw = -0.35f * k;
        m.leftArm.pitch = m.leftArm.pitch * (1 - k) + (-0.6f) * k;
        m.body.yaw = 0.25f * k;
    }
}
