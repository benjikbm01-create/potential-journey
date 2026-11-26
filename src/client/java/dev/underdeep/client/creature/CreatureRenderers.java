package dev.underdeep.client.creature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.underdeep.Underdeep;
import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.UnderdeepCreature;
import dev.underdeep.wildlife.WildlifeRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Registers model layers and renderers for every wildlife species (client only). */
public final class CreatureRenderers {
    private CreatureRenderers() {
    }

    public static void init() {
        for (CreatureSpecies s : WildlifeRegistry.all()) {
            JsonObject spec = load(s.id);
            if (spec == null) {
                Underdeep.LOGGER.error("Missing creature model spec for {}", s.id);
                continue;
            }
            EntityModelLayer layer = new EntityModelLayer(Identifier.of(Underdeep.MOD_ID, s.id), "main");
            EntityModelLayerRegistry.registerModelLayer(layer, () -> CreatureModel.build(spec));
            EntityRendererRegistry.register(WildlifeRegistry.type(s.id), ctx -> new Renderer(ctx, new CreatureModel(ctx.getPart(layer), spec), spec, s));
        }
    }

    static JsonObject load(String id) {
        try (InputStream in = CreatureRenderers.class.getResourceAsStream("/assets/underdeep/creatures/" + id + ".json")) {
            return in == null ? null : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            Underdeep.LOGGER.error("Bad creature model spec {}", id, e);
            return null;
        }
    }

    static final class Renderer extends MobEntityRenderer<UnderdeepCreature, CreatureModel> {
        private final Identifier texture;
        private final float scale;

        Renderer(EntityRendererFactory.Context ctx, CreatureModel model, JsonObject spec, CreatureSpecies s) {
            super(ctx, model, spec.has("shadow") ? spec.get("shadow").getAsFloat() : 0.5f);
            this.texture = Identifier.of(Underdeep.MOD_ID, "textures/entity/creature/" + s.id + ".png");
            this.scale = spec.has("scale") ? spec.get("scale").getAsFloat() : 1.0f;
            if (spec.has("glow") && spec.get("glow").getAsBoolean()) {
                RenderLayer glow = RenderLayer.getEyes(Identifier.of(Underdeep.MOD_ID, "textures/entity/creature/" + s.id + "_glow.png"));
                addFeature(new EyesFeatureRenderer<>(this) {
                    @Override
                    public RenderLayer getEyesTexture() {
                        return glow;
                    }
                });
            }
        }

        @Override
        public Identifier getTexture(UnderdeepCreature entity) {
            return texture;
        }

        @Override
        protected void scale(UnderdeepCreature entity, MatrixStack matrices, float amount) {
            float v = 0.94f + 0.04f * entity.variant();
            float f = scale * v * (entity.isBaby() ? 0.55f : 1.0f);
            matrices.scale(f, f, f);
        }
    }
}
