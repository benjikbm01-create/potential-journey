package dev.underdeep.survival;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;

/** Operator diagnostics for Stage 7: {@code /underdeep survival [content|me|hazards|learnall|forget]}. */
public final class SurvivalCommands {
    private SurvivalCommands() {
    }

    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return CommandManager.literal("survival").executes(SurvivalCommands::content)
                .then(CommandManager.literal("content").executes(SurvivalCommands::content))
                .then(CommandManager.literal("me").executes(SurvivalCommands::me))
                .then(CommandManager.literal("hazards").executes(SurvivalCommands::hazards))
                .then(CommandManager.literal("learnall").executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    int n = 0;
                    for (SurvivalContent.Recipe r : SurvivalContent.recipes()) {
                        if (!r.basic() && SurvivalData.of(p).known.add(r.id())) {
                            n++;
                        }
                    }
                    Recipes.sync(p);
                    int fn = n;
                    c.getSource().sendFeedback(() -> Text.literal("Learned " + fn + " recipes."), false);
                    return n;
                }))
                .then(CommandManager.literal("forget").executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    SurvivalData.of(p).known.clear();
                    Recipes.sync(p);
                    c.getSource().sendFeedback(() -> Text.literal("Forgot all learned recipes."), false);
                    return 1;
                }));
    }

    static int content(CommandContext<ServerCommandSource> c) {
        StringBuilder sb = new StringBuilder("Stage 7 content: ").append(SurvivalContent.FOODS.size()).append(" foods/drinks, ")
                .append(SurvivalContent.MEDICINES.size()).append(" medicines, ").append(SurvivalContent.recipes().size()).append(" station recipes");
        for (SurvivalContent.Station s : SurvivalContent.Station.values()) {
            sb.append("\n  ").append(SurvivalContent.stationName(s)).append(": ").append(SurvivalContent.recipes(s).size());
        }
        c.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
        return 1;
    }

    static int me(CommandContext<ServerCommandSource> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        SurvivalData d = SurvivalData.of(p);
        long now = p.getServerWorld().getTime();
        String s = String.format(Locale.ROOT, "variety %d groups, %d recipes known, comfort %d, exhaustion x%.2f, hazards seen %s",
                d.variety(now, Meals.VARIETY_WINDOW), Recipes.knownCount(p), d.lastComfort, Meals.exhaustionFactor(p), d.hazardsSeen);
        c.getSource().sendFeedback(() -> Text.literal(s), false);
        return 1;
    }

    static int hazards(CommandContext<ServerCommandSource> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        StringBuilder sb = new StringBuilder("Exposure:");
        for (Hazards.Hazard h : Hazards.Hazard.values()) {
            float v = Hazards.exposure(p, h);
            if (v > 0) {
                sb.append(String.format(Locale.ROOT, "\n  %s %.0f%%", h.title, v));
            }
        }
        c.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
        return 1;
    }
}
