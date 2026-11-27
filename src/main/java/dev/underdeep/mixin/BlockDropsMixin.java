package dev.underdeep.mixin;

import dev.underdeep.equipment.EquipmentItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Stage 8: the embersteel pickaxe smelts the ores it mines (same count, furnace result). */
@Mixin(Block.class)
public abstract class BlockDropsMixin {
    @Inject(method = "getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private static void underdeep$smelt(BlockState state, ServerWorld world, BlockPos pos, BlockEntity be, Entity entity, ItemStack tool,
                                        CallbackInfoReturnable<List<ItemStack>> cir) {
        var def = EquipmentItems.TOOL_DEF.get(tool.getItem());
        if (def == null || !def.special().equals("smelting") || !state.isToolRequired()) {
            return;
        }
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : cir.getReturnValue()) {
            var m = world.getRecipeManager().getFirstMatch(RecipeType.SMELTING, new SingleStackRecipeInput(s), world);
            if (m.isPresent() && !(s.getItem() instanceof net.minecraft.item.BlockItem)) {
                ItemStack r = m.get().value().getResult(world.getRegistryManager()).copyWithCount(s.getCount());
                out.add(r);
            } else {
                out.add(s);
            }
        }
        cir.setReturnValue(out);
    }
}
