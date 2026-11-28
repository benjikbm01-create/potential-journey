package dev.underdeep.client.mixin;

import dev.underdeep.client.audio.MusicDirector;
import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stage 11: vanilla's music tracker stays silent while the Underdeep soundtrack owns the music (Underdeep worlds). */
@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void underdeep$suppress(CallbackInfo ci) {
        if (MusicDirector.suppressVanilla()) {
            ci.cancel();
        }
    }
}
