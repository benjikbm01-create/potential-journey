package dev.underdeep.client;

import dev.underdeep.Underdeep;
import dev.underdeep.registry.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class UnderdeepClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModBlocks.LUMINITE_CLUSTER, ModBlocks.GLOWCAP);
        DimensionRenderingRegistry.registerDimensionEffects(Identifier.of(Underdeep.MOD_ID, "underdeep"), new UnderdeepEffects());
        ScreenshotTour.init();
    }

    /**
     * Underground dimension rendering: no sky, no clouds, no sunrise tint, and fog colour taken
     * directly from the biome (so each region has its own atmospheric haze regardless of time of day).
     */
    static final class UnderdeepEffects extends DimensionEffects {
        UnderdeepEffects() {
            super(Float.NaN, false, SkyType.NONE, false, false);
        }

        @Override
        public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
            return color;
        }

        @Override
        public boolean useThickFog(int camX, int camY) {
            return false;
        }

        @Override
        public float[] getFogColorOverride(float skyAngle, float tickDelta) {
            return null;
        }
    }
}
