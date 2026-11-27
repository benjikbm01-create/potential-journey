package dev.underdeep.survival;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Journal pages (after the trade notes): hazards you have met with their counterplay, recipes known, last rest. */
public final class SurvivalJournal {
    private SurvivalJournal() {
    }

    static List<Text> lines(ServerPlayerEntity p) {
        SurvivalData d = SurvivalData.of(p);
        List<Text> out = new ArrayList<>();
        out.add(Text.literal("Recipes known: " + Recipes.knownCount(p) + " of " + SurvivalContent.recipes().size()).formatted(Formatting.YELLOW));
        long now = p.getServerWorld().getTime();
        out.add(Text.literal("Food groups eaten lately: " + d.variety(now, Meals.VARIETY_WINDOW) + " of "
                + SurvivalContent.FoodGroup.values().length).formatted(Formatting.YELLOW));
        if (d.lastRest >= 0) {
            out.add(Text.literal("Last rest: comfort " + d.lastComfort + "/" + Comfort.MAX).formatted(Formatting.YELLOW));
        }
        for (Hazards.Hazard h : Hazards.Hazard.values()) {
            if (d.hazardsSeen.contains(h.id())) {
                out.add(Text.literal(h.title + ": ").formatted(Formatting.RED).append(Text.literal(h.advice).formatted(Formatting.GRAY)));
            }
        }
        return out;
    }

    public static int pages(ServerPlayerEntity p) {
        return Math.max(1, (lines(p).size() + 5) / 6);
    }

    public static List<Text> render(ServerPlayerEntity p, int page) {
        List<Text> all = lines(p);
        List<Text> out = new ArrayList<>();
        out.add(Text.literal("Survival notes").formatted(Formatting.GOLD));
        for (int i = page * 6; i < Math.min(all.size(), page * 6 + 6); i++) {
            out.add(all.get(i));
        }
        return out;
    }
}
