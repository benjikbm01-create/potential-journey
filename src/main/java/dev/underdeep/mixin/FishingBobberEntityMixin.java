package dev.underdeep.mixin;

import dev.underdeep.survival.Fishing;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Stage 7: regional fishing catches in the Underdeep (vanilla loot elsewhere and part of the time). */
@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityMixin {
    @Redirect(method = "use", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/loot/LootTable;generateLoot(Lnet/minecraft/loot/context/LootContextParameterSet;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
    private ObjectArrayList<ItemStack> underdeep$regionalLoot(LootTable table, LootContextParameterSet params) {
        ObjectArrayList<ItemStack> vanilla = table.generateLoot(params);
        FishingBobberEntity self = (FishingBobberEntity) (Object) this;
        if (self.getWorld() instanceof ServerWorld sw && self.getPlayerOwner() != null) {
            return Fishing.rodLoot(sw, self.getBlockPos(), self.getPlayerOwner(), vanilla);
        }
        return vanilla;
    }
}
