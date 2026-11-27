package dev.underdeep.mixin;

import dev.underdeep.equipment.Accessories;
import dev.underdeep.equipment.EquipmentEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stage 8: weapon special damage (server) and climbing claws (both sides, from synced accessory flags). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEquipmentMixin {
    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float underdeep$weaponBonus(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.getWorld().isClient && source.getAttacker() instanceof LivingEntity attacker && source.getSource() == attacker) {
            return dev.underdeep.combat.Combat.outgoing(attacker, self, EquipmentEffects.bonusDamage(attacker, self, amount));
        }
        return amount;
    }

    @Inject(method = "isClimbing", at = @At("RETURN"), cancellable = true)
    private void underdeep$claws(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && (Object) this instanceof PlayerEntity p && p.horizontalCollision && !p.isSpectator()
                && Accessories.flags(p).contains("climb")) {
            cir.setReturnValue(true);
        }
    }
}
