package dev.underdeep.client.boss;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.underdeep.Underdeep;
import dev.underdeep.boss.BossDef;
import dev.underdeep.boss.BossEntity;
import dev.underdeep.boss.BossProjectile;
import dev.underdeep.boss.Bosses;
import dev.underdeep.boss.Encounters;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Client registration of the Stage 10 boss renderers, projectiles and the boss audio-context hook. */
public final class BossRenderers {
    private BossRenderers() {
    }

    /** Latest boss audio context (Stage 11 soundtrack reads this): event, boss id, argument, client time. */
    public static String audioEvent = "none", audioBoss = "";
    public static int audioArg;

    public static void init() {
        for (BossDef d : Bosses.DEFS.values()) {
            JsonObject spec = load(d.id());
            if (spec == null) {
                Underdeep.LOGGER.warn("No boss model for {}", d.id());
                continue;
            }
            EntityModelLayer layer = new EntityModelLayer(Identifier.of(Underdeep.MOD_ID, "boss_" + d.id()), "main");
            EntityModelLayerRegistry.registerModelLayer(layer, () -> BossModel.data(spec));
            EntityRendererRegistry.register(Bosses.type(d.id()), ctx -> new Renderer(ctx, new BossModel(ctx.getPart(layer), spec), spec, d));
        }
        EntityRendererRegistry.register(BossProjectile.TYPE, ctx -> new FlyingItemEntityRenderer<>(ctx, 1.6f, true));
        ClientPlayNetworking.registerGlobalReceiver(Encounters.Audio.ID, (p, ctx) -> ctx.client().execute(() -> {
            audioEvent = p.event();
            audioBoss = p.boss();
            audioArg = p.arg();
        }));
    }

    static JsonObject load(String id) {
        var in = BossRenderers.class.getResourceAsStream("/assets/underdeep/bosses/" + id + ".json");
        if (in == null) {
            return null;
        }
        try (var r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        } catch (Exception e) {
            Underdeep.LOGGER.warn("Bad boss model {}: {}", id, e.toString());
            return null;
        }
    }

    static final class Renderer extends MobEntityRenderer<BossEntity, BossModel> {
        private final Identifier texture, glow;
        private final float scale;

        Renderer(EntityRendererFactory.Context ctx, BossModel model, JsonObject spec, BossDef d) {
            super(ctx, model, d.width() * 0.45f);
            this.texture = Identifier.of(Underdeep.MOD_ID, "textures/entity/boss/" + d.id() + ".png");
            this.glow = Identifier.of(Underdeep.MOD_ID, "textures/entity/boss/" + d.id() + "_glow.png");
            this.scale = spec.has("scale") ? spec.get("scale").getAsFloat() : 1f;
            addFeature(new Glow(this, glow, model));
        }

        @Override
        public Identifier getTexture(BossEntity e) {
            return texture;
        }

        @Override
        protected void scale(BossEntity e, MatrixStack m, float tickDelta) {
            m.scale(scale, scale, scale);
        }

        @Override
        public void render(BossEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider v, int light) {
            if (getModel().hideFlag >= 0 && e.flag(getModel().hideFlag)) {
                return; // burrowed: only its trail of dust and embers is visible
            }
            super.render(e, yaw, tickDelta, m, v, light);
        }

        @Override
        protected float getLyingAngle(BossEntity e) {
            return 0; // the defeat pose is authored; no vanilla tip-over
        }
    }

    /** Emissive layer: eyes, cores, lanterns, crystals; flag-driven parts glow only while their flag is set. */
    static final class Glow extends FeatureRenderer<BossEntity, BossModel> {
        private final Identifier tex;
        private final BossModel model;
        private final Map<ModelPart, Boolean> saved = new HashMap<>();

        Glow(FeatureRendererContext<BossEntity, BossModel> ctx, Identifier tex, BossModel model) {
            super(ctx);
            this.tex = tex;
            this.model = model;
        }

        @Override
        public void render(MatrixStack m, VertexConsumerProvider v, int light, BossEntity e, float limbAngle, float limbDistance, float tickDelta,
                           float age, float headYaw, float headPitch) {
            saved.clear();
            for (var en : model.flagGlow.entrySet()) {
                for (String n : en.getValue()) {
                    ModelPart p = model.part(n);
                    if (p != null) {
                        saved.put(p, p.visible);
                        p.visible = p.visible && e.flag(en.getKey());
                    }
                }
            }
            model.render(m, v.getBuffer(RenderLayer.getEyes(tex)), 0xF000F0, OverlayTexture.DEFAULT_UV, e.isStaggered() ? 0x88FFFFFF : -1);
            saved.forEach((p, vis) -> p.visible = vis);
        }
    }
}
