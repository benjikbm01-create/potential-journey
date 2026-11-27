package dev.underdeep.mixin;

import dev.underdeep.survival.Meals;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stage 7: records food groups eaten (variety bonus, recipe discovery) and applies comfort exhaustion modifiers. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "eatFood", at = @At("HEAD"))
    private void underdeep$onEat(World world, ItemStack stack, FoodComponent food, CallbackInfoReturnable<ItemStack> cir) {
        if (!world.isClient && (Object) this instanceof ServerPlayerEntity sp) {
            Meals.onEat(sp, stack);
        }
    }

    @ModifyVariable(method = "addExhaustion", at = @At("HEAD"), argsOnly = true)
    private float underdeep$exhaustion(float exhaustion) {
        return exhaustion * Meals.exhaustionFactor((PlayerEntity) (Object) this);
    }
}
