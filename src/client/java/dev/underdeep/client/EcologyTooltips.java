package dev.underdeep.client;

import dev.underdeep.Underdeep;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Discoverable hints for Underdeep plants, foods and remedies: each item with a
 * {@code tooltip.underdeep.<id>} translation shows it in grey (growing conditions, edibility,
 * preparation). Hints describe what a careful observer would learn; they never reveal rarity or
 * hidden discoveries.
 */
final class EcologyTooltips {
    private EcologyTooltips() {
    }

    static void init() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            Identifier id = Registries.ITEM.getId(stack.getItem());
            if (!Underdeep.MOD_ID.equals(id.getNamespace())) {
                return;
            }
            String key = "tooltip.underdeep." + id.getPath();
            if (I18n.hasTranslation(key)) {
                for (String line : I18n.translate(key).split("\\n")) {
                    lines.add(Text.literal(line).formatted(Formatting.GRAY));
                }
            }
        });
    }
}
