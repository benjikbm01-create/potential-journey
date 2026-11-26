package dev.underdeep.client.civ;

import dev.underdeep.Underdeep;
import dev.underdeep.civ.Civilization;
import dev.underdeep.civ.npc.InhabitantEntity;
import dev.underdeep.civ.npc.InhabitantManager;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Inhabitant rendering: a player-shaped model with a civilization skin (6 variants each), a profession
 * outfit layer tinted in the culture's colour, held items, and per-culture body proportions.
 */
public final class InhabitantRenderer extends MobEntityRenderer<InhabitantEntity, PlayerEntityModel<InhabitantEntity>> {
    public static final EntityModelLayer OUTFIT = new EntityModelLayer(Identifier.of(Underdeep.MOD_ID, "inhabitant"), "outfit");

    public static void init() {
        EntityModelLayerRegistry.registerModelLayer(OUTFIT,
                () -> TexturedModelData.of(PlayerEntityModel.getTexturedModelData(new Dilation(0.27f), false), 64, 64));
        EntityRendererRegistry.register(InhabitantManager.INHABITANT, InhabitantRenderer::new);
    }

    public InhabitantRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        addFeature(new Outfit(this, new PlayerEntityModel<>(ctx.getPart(OUTFIT), false)));
        addFeature(new HeldItemFeatureRenderer<>(this, ctx.getHeldItemRenderer()));
    }

    @Override
    public Identifier getTexture(InhabitantEntity e) {
        return Identifier.of(Underdeep.MOD_ID, "textures/entity/inhabitant/" + e.civ().id() + "_" + Math.min(5, e.skinVariant()) + ".png");
    }

    @Override
    protected void scale(InhabitantEntity e, MatrixStack matrices, float tickDelta) {
        float w = 0.9375f, h = 0.9375f;
        switch (e.civ()) {
            case STONEBORN -> {
                w *= 1.08f;
                h *= 0.92f;
            }
            case FORGEKIN -> {
                w *= 1.06f;
                h *= 0.95f;
            }
            case ASTRALS -> {
                w *= 0.95f;
                h *= 1.06f;
            }
            case GLIMMERKIN -> {
                w *= 0.95f;
                h *= 1.02f;
            }
            case SPOREFOLK -> h *= 0.97f;
            default -> {
            }
        }
        matrices.scale(w, h, w);
    }

    /** Outfit group of a profession (texture), tinted by the civilization's primary colour. */
    static String outfit(String profession) {
        return switch (profession) {
            case "guard" -> "armor";
            case "scholar", "healer", "alchemist", "astronomer", "cartographer", "navigator", "mycologist" -> "robe";
            case "farmer", "rancher", "herbalist", "forester" -> "farmer";
            case "merchant", "innkeeper", "tailor", "artisan" -> "vest";
            case "fisher", "boatwright", "hunter" -> "coat";
            default -> "apron";
        };
    }

    static final class Outfit extends FeatureRenderer<InhabitantEntity, PlayerEntityModel<InhabitantEntity>> {
        private final PlayerEntityModel<InhabitantEntity> model;

        Outfit(FeatureRendererContext<InhabitantEntity, PlayerEntityModel<InhabitantEntity>> ctx, PlayerEntityModel<InhabitantEntity> model) {
            super(ctx);
            this.model = model;
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, InhabitantEntity e, float limbAngle,
                           float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (e.isInvisible()) {
                return;
            }
            Identifier tex = Identifier.of(Underdeep.MOD_ID, "textures/entity/inhabitant/outfit/" + outfit(e.profession()) + ".png");
            Civilization civ = e.civ();
            int c = outfit(e.profession()).equals("armor") ? 0xFFD0D0D8 : 0xFF000000 | civ.accentColor;
            render(getContextModel(), model, tex, matrices, vertexConsumers, light, e, limbAngle, limbDistance, animationProgress, headYaw,
                    headPitch, tickDelta, c);
        }
    }
}
