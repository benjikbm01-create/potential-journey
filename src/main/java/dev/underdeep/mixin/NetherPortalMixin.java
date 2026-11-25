package dev.underdeep.mixin;

import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.world.UnderdeepWorlds;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.dimension.NetherPortal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Nether portals cannot be lit in Underdeep worlds unless enabled in config/underdeep.json.
 */
@Mixin(NetherPortal.class)
public abstract class NetherPortalMixin {
    @Inject(method = "getNewPortal", at = @At("HEAD"), cancellable = true)
    private static void underdeep$blockPortals(WorldAccess world, BlockPos pos, Direction.Axis axis,
                                              CallbackInfoReturnable<Optional<NetherPortal>> cir) {
        if (!UnderdeepConfig.get().netherPortalsInUnderdeep && UnderdeepWorlds.isUnderdeep(world)) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
