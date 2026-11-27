package dev.underdeep.mixin;

import dev.underdeep.combat.Poise;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stage 10: a staggered mob skips its AI (goals, navigation, targeting) for the stagger duration. */
@Mixin(MobEntity.class)
public abstract class MobStaggerMixin {
    @Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
    private void underdeep$stagger(CallbackInfo ci) {
        MobEntity self = (MobEntity) (Object) this;
        if (!self.getWorld().isClient && Poise.staggered(self)) {
            Poise.tickStaggered(self);
            ci.cancel();
        }
    }
}
