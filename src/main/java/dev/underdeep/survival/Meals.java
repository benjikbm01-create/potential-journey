package dev.underdeep.survival;

import dev.underdeep.config.UnderdeepConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Set;

/**
 * Food rules (Stage 7). Vanilla hunger and saturation stay as they are; on top of them:
 * <ul>
 *   <li><b>Variety</b>: eating from 3 different food groups within a day and a half gives Well Fed I (less hunger
 *   drain); 5 groups gives Well Fed II (also slower hazard exposure). Never a penalty for a plain diet.</li>
 *   <li><b>Meal buffs</b> are bounded: durations never add up (the longer one wins), amplifiers from food are at
 *   most I, durations at most 10 minutes, and opposite protections (warmed/cooled) cancel each other.</li>
 *   <li>Eating a dish teaches the recipe that makes it (recipe discovery).</li>
 * </ul>
 */
public final class Meals {
    private Meals() {
    }

    public static final long VARIETY_WINDOW = 36000;
    public static final int MAX_FOOD_BUFF_SECONDS = 600;

    /** Called (server side) whenever a player eats any food, vanilla or modded, before the stack shrinks. */
    public static void onEat(ServerPlayerEntity p, ItemStack stack) {
        if (!UnderdeepConfig.get().survival.enabled) {
            return;
        }
        SurvivalData d = SurvivalData.of(p);
        long now = p.getServerWorld().getTime();
        Set<SurvivalContent.FoodGroup> groups = SurvivalContent.groupsOf(stack.getItem());
        int before = d.variety(now, VARIETY_WINDOW);
        for (SurvivalContent.FoodGroup g : groups) {
            d.lastAte[g.ordinal()] = now;
        }
        if (UnderdeepConfig.get().survival.nutritionBonus) {
            int v = d.variety(now, VARIETY_WINDOW);
            if (v >= 3) {
                int amp = v >= 5 ? 1 : 0;
                apply(p, SurvivalEffects.WELL_FED, 20 * 60 * 10, amp, true);
                if (v > before && (v == 3 || v == 5)) {
                    p.sendMessage(Text.literal(v == 5 ? "A varied diet: you feel very well fed." : "A varied diet: you feel well fed.")
                            .formatted(Formatting.GOLD), true);
                }
            }
        }
        for (SurvivalContent.Recipe r : SurvivalContent.producing(stack.getItem())) {
            Recipes.learn(p, r, "tasting it");
            break;
        }
    }

    /** Applies a buff with the bounded-stacking rules. */
    public static void apply(LivingEntity e, RegistryEntry<StatusEffect> effect, int ticks, int amp, boolean food) {
        if (effect == null) {
            return;
        }
        if (food) {
            ticks = Math.min(ticks, MAX_FOOD_BUFF_SECONDS * 20);
            amp = Math.min(amp, 1);
        }
        if (effect == SurvivalEffects.WARMED) {
            e.removeStatusEffect(SurvivalEffects.COOLED);
        } else if (effect == SurvivalEffects.COOLED) {
            e.removeStatusEffect(SurvivalEffects.WARMED);
        }
        StatusEffectInstance cur = e.getStatusEffect(effect);
        if (cur != null && cur.getAmplifier() > amp && !cur.isInfinite()) {
            return; // a stronger version is active: keep it
        }
        if (cur != null && cur.getAmplifier() == amp && cur.getDuration() >= ticks) {
            return;
        }
        e.addStatusEffect(new StatusEffectInstance(effect, ticks, amp, false, true, true));
    }

    public static void applyBuffs(LivingEntity e, java.util.List<SurvivalContent.Buff> buffs, boolean food) {
        for (SurvivalContent.Buff b : buffs) {
            apply(e, SurvivalEffects.byId(b.effect()), b.seconds() * 20, b.amplifier(), food);
        }
    }

    /** Exhaustion multiplier from comfort effects (Well Fed, Rested) and wear (Weary). */
    public static float exhaustionFactor(PlayerEntity p) {
        if (SurvivalEffects.WELL_FED == null) {
            return 1f;
        }
        float f = 1f;
        StatusEffectInstance wf = p.getStatusEffect(SurvivalEffects.WELL_FED);
        if (wf != null) {
            f *= wf.getAmplifier() >= 1 ? 0.8f : 0.88f;
        }
        if (p.hasStatusEffect(SurvivalEffects.RESTED)) {
            f *= 0.92f;
        }
        if (p.hasStatusEffect(SurvivalEffects.WEARY)) {
            f *= 1.1f;
        }
        return f;
    }
}
