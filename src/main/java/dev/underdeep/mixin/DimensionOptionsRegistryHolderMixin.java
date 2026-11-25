package dev.underdeep.mixin;

import dev.underdeep.world.gen.UnderdeepChunkGenerator;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Treats an Underdeep overworld as a supported configuration so loading an Underdeep world does not
 * show the "Worlds using Experimental Settings are not supported" warning every time. Only affects
 * dimensions whose generator is the Underdeep generator.
 */
@Mixin(DimensionOptionsRegistryHolder.class)
public abstract class DimensionOptionsRegistryHolderMixin {
    @Inject(method = "isOverworldVanilla", at = @At("HEAD"), cancellable = true)
    private static void underdeep$stableOverworld(DimensionOptions options, CallbackInfoReturnable<Boolean> cir) {
        if (options.chunkGenerator() instanceof UnderdeepChunkGenerator) {
            cir.setReturnValue(true);
        }
    }
}
