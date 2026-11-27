package dev.underdeep.client.survival;

import dev.underdeep.survival.Recipes;
import dev.underdeep.survival.SurvivalBlocks;
import dev.underdeep.survival.SurvivalContent;
import dev.underdeep.survival.SurvivalItems;
import dev.underdeep.survival.station.DryingRackBlockEntity;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

import java.util.Set;

/** Client side of Stage 7: station screen, drying-rack item rendering, seats, recipe sync, food/medicine tooltips. */
public final class SurvivalClient {
    private SurvivalClient() {
    }

    public static void init() {
        HandledScreens.register(SurvivalBlocks.STATION_SCREEN, StationScreen::new);
        BlockEntityRendererFactories.register(SurvivalBlocks.RACK_BE, ctx -> new RackRenderer());
        EntityRendererRegistry.register(SurvivalBlocks.SEAT, EmptyEntityRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(Recipes.Sync.ID, (payload, ctx) -> ctx.client().execute(() -> {
            Recipes.CLIENT_KNOWN.clear();
            Recipes.CLIENT_KNOWN.addAll(payload.known());
        }));
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.getItem() instanceof SurvivalItems.MealItem m) {
                Set<SurvivalContent.FoodGroup> g = m.def.groups();
                if (!g.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (SurvivalContent.FoodGroup fg : g) {
                        if (!sb.isEmpty()) {
                            sb.append(", ");
                        }
                        sb.append(fg.id());
                    }
                    lines.add(Text.literal("Food groups: " + sb).formatted(Formatting.DARK_GREEN));
                }
                for (SurvivalContent.Buff b : m.def.buffs()) {
                    lines.add(Text.literal(I18n.translate("effect." + b.effect().getNamespace() + "." + b.effect().getPath()) + " ("
                            + b.seconds() / 60 + ":" + String.format("%02d", b.seconds() % 60) + ")").formatted(Formatting.BLUE));
                }
            } else if (stack.getItem() instanceof SurvivalItems.MedicineItem med) {
                lines.add(Text.literal((med.def.drink() ? "Drink" : "Apply; or use on someone else") + " - cooldown "
                        + med.def.cooldownSeconds() + "s").formatted(Formatting.DARK_AQUA));
            } else if (!SurvivalContent.groupsOf(stack.getItem()).isEmpty() && !"underdeep".equals(net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getNamespace())) {
                // vanilla foods: show their group for the variety bonus
                StringBuilder sb = new StringBuilder();
                for (SurvivalContent.FoodGroup fg : SurvivalContent.groupsOf(stack.getItem())) {
                    if (!sb.isEmpty()) {
                        sb.append(", ");
                    }
                    sb.append(fg.id());
                }
                lines.add(Text.literal("Food groups: " + sb).formatted(Formatting.DARK_GREEN));
            }
        });
    }

    /** Draws the items hanging on a drying rack. */
    static final class RackRenderer implements BlockEntityRenderer<DryingRackBlockEntity> {
        @Override
        public void render(DryingRackBlockEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider vc, int light, int overlay) {
            Direction f = be.getCachedState().contains(HorizontalFacingBlock.FACING) ? be.getCachedState().get(HorizontalFacingBlock.FACING)
                    : Direction.NORTH;
            for (int i = 0; i < DryingRackBlockEntity.SIZE; i++) {
                ItemStack s = be.items().get(i);
                if (s.isEmpty()) {
                    continue;
                }
                m.push();
                m.translate(0.5, 0, 0.5);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-f.asRotation()));
                m.translate(i % 2 == 0 ? -0.27 : 0.27, i < 2 ? 0.72 : 0.42, 0);
                m.scale(0.4f, 0.4f, 0.4f);
                MinecraftClient.getInstance().getItemRenderer().renderItem(s, ModelTransformationMode.FIXED, light, overlay, m, vc, be.getWorld(),
                        (int) be.getPos().asLong() + i);
                m.pop();
            }
        }
    }
}
