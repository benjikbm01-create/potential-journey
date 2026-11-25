package dev.underdeep.mixin;

import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.spawn.SpawnFinder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.MiscConfiguredFeatures;
import net.minecraft.world.level.ServerWorldProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla spawn selection (which assumes an open sky) for Underdeep worlds.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "setupSpawn", at = @At("HEAD"), cancellable = true)
    private static void underdeep$setupSpawn(ServerWorld world, ServerWorldProperties worldProperties, boolean bonusChest,
                                            boolean debugWorld, CallbackInfo ci) {
        if (debugWorld || !UnderdeepWorlds.isUnderdeep(world)) {
            return;
        }
        BlockPos spawn = SpawnFinder.findSpawn(world);
        worldProperties.setSpawnPos(spawn, 0.0F);
        if (bonusChest) {
            world.getRegistryManager().getOptional(RegistryKeys.CONFIGURED_FEATURE)
                    .flatMap(r -> r.getEntry(MiscConfiguredFeatures.BONUS_CHEST))
                    .ifPresent(f -> f.value().generate(world, world.getChunkManager().getChunkGenerator(), world.random, spawn.east(2)));
        }
        ci.cancel();
    }
}
