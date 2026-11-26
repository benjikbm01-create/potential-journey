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
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModBlocks.LUMINITE_CLUSTER, ModBlocks.GLOWCAP,
                ModBlocks.STARBLOOM, ModBlocks.EMBERBLOOM, ModBlocks.AMBERBLOOM, ModBlocks.GLOWREED, ModBlocks.LUMEN_SEAGRASS,
                ModBlocks.STORM_CRYSTAL, ModBlocks.RIME_CRYSTAL);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.GOLDEN_LEAVES, RenderLayer.getCutoutMipped());
        // Stage 2 content
        dev.underdeep.registry.Reg.CUTOUT.forEach(b -> BlockRenderLayerMap.INSTANCE.putBlock(b, RenderLayer.getCutout()));
        dev.underdeep.registry.Reg.CUTOUT_MIPPED.forEach(b -> BlockRenderLayerMap.INSTANCE.putBlock(b, RenderLayer.getCutoutMipped()));
        dev.underdeep.registry.Reg.TRANSLUCENT.forEach(b -> BlockRenderLayerMap.INSTANCE.putBlock(b, RenderLayer.getTranslucent()));
        for (var w : dev.underdeep.registry.WoodSet.ALL) {
            BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), w.sapling, w.pottedSapling);
        }
        dev.underdeep.block.plant.ParticleGate.install(() -> {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            return mc.options == null || mc.options.getParticles().getValue() != net.minecraft.client.option.ParticlesMode.MINIMAL;
        });
        EcologyTooltips.init();
        dev.underdeep.client.creature.CreatureRenderers.init();
        DimensionRenderingRegistry.registerDimensionEffects(Identifier.of(Underdeep.MOD_ID, "underdeep"), new UnderdeepEffects());
        ScreenshotTour.init();
        CreateWorldTest.init();
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
