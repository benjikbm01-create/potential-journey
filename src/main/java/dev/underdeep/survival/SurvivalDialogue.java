package dev.underdeep.survival;

import dev.underdeep.civ.econ.Economy;
import dev.underdeep.civ.econ.EconomyManager;
import dev.underdeep.civ.econ.Goods;
import dev.underdeep.civ.econ.Tx;
import dev.underdeep.civ.npc.Dialogue;
import dev.underdeep.civ.npc.InhabitantEntity;
import dev.underdeep.civ.npc.Topics;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 7 services from inhabitants: cooks teach their people's dishes, healers treat injuries using the
 * settlement's own medicine stock, and innkeepers serve a hot meal and rent a bed. Payments use physical emeralds
 * into the community treasury (recorded like every other player payment, so the world money audit stays exact);
 * goods used come out of the settlement's ledger.
 */
public final class SurvivalDialogue {
    private SurvivalDialogue() {
    }

    public static final int TREAT_PRICE = 2, MEAL_PRICE = 1, BED_PRICE = 2;
    static final List<String> MEDS = List.of("underdeep:bandage", "underdeep:mendleaf_poultice", "underdeep:field_medkit",
            "underdeep:sunleaf_salve", "underdeep:antidote_tincture");

    static boolean cook(String prof) {
        return prof.equals("cook") || prof.equals("baker") || prof.equals("innkeeper") || prof.equals("brewer");
    }

    static boolean healer(String prof) {
        return prof.equals("healer") || prof.equals("herbalist") || prof.equals("alchemist");
    }

    public static void options(ServerPlayerEntity p, InhabitantEntity ent, Topics.Talk t, List<String> ids, List<String> labels) {
        if (!UnderdeepConfigHolder.enabled()) {
            return;
        }
        String prof = t.r.profession;
        if (cook(prof)) {
            Dialogue.opt(ids, labels, "s_teach", "Could you teach me one of your dishes?");
        }
        if (healer(prof)) {
            Dialogue.opt(ids, labels, "s_treat", "Could you treat my injuries? (" + TREAT_PRICE + " emeralds)");
        }
        net.minecraft.item.ItemStack held = p.getMainHandStack();
        if ((prof.equals("blacksmith") || prof.equals("smelter") || prof.equals("engineer")) && held.isDamageable() && held.getDamage() > 0) {
            Dialogue.opt(ids, labels, "s_repair", "Could you repair this " + held.getName().getString() + "? (" + repairPrice(held) + " emeralds)");
        }
        if (prof.equals("innkeeper")) {
            Dialogue.opt(ids, labels, "s_meal", "A hot meal, please (" + MEAL_PRICE + " emerald)");
            Dialogue.opt(ids, labels, "s_bed", "A bed for the night (" + BED_PRICE + " emeralds)");
        }
    }

    @Nullable
    public static String choose(ServerPlayerEntity p, InhabitantEntity ent, Topics.Talk t, String option) {
        if (!option.startsWith("s_")) {
            return null;
        }
        Economy e = EconomyManager.economy(t.rec.id);
        long now = p.getServerWorld().getTime();
        switch (option) {
            case "s_teach" -> {
                if (t.bond == null || t.bond.familiarity < 2 || t.bond.warmth() < 0) {
                    return "Maybe, once we know each other a little better.";
                }
                SurvivalContent.Recipe r = pick(p, t.rec.civ == null ? "" : t.rec.civ.id());
                if (r == null) {
                    return "You already know everything I could show you.";
                }
                Recipes.learn(p, r, "taught by " + t.r.name);
                StringBuilder how = new StringBuilder();
                for (SurvivalContent.Ingredient i : r.ingredients()) {
                    if (!how.isEmpty()) {
                        how.append(", ");
                    }
                    how.append(i.count()).append(" ").append(SurvivalContent.ingredientName(i));
                }
                return "Here's how we make " + r.resultItem().getName().getString() + ": " + how + ", in a "
                        + SurvivalContent.stationName(r.station()).toLowerCase(java.util.Locale.ROOT) + ".";
            }
            case "s_treat" -> {
                boolean hurt = p.getHealth() < p.getMaxHealth() - 1 || p.getStatusEffects().stream()
                        .anyMatch(x -> x.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL);
                if (!hurt) {
                    return "You look well enough to me.";
                }
                String med = e == null ? null : MEDS.stream().filter(g -> e.free(g) >= 1).findFirst().orElse(null);
                if (med == null) {
                    return "I'm out of dressings and remedies, I'm afraid. Bring me some herbs?";
                }
                if (!pay(p, e, TREAT_PRICE)) {
                    return "That's " + TREAT_PRICE + " emeralds for the materials.";
                }
                use(e, med);
                for (StatusEffectInstance x : new ArrayList<>(p.getStatusEffects())) {
                    if (x.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL) {
                        p.removeStatusEffect(x.getEffectType());
                    }
                }
                p.heal(8);
                dev.underdeep.guide.Advancements.event(p, "service", "healer");
                Meals.apply(p, net.minecraft.entity.effect.StatusEffects.REGENERATION, 200, 0, false);
                return "Hold still... there. Keep it clean and you'll mend.";
            }
            case "s_repair" -> {
                net.minecraft.item.ItemStack held = p.getMainHandStack();
                if (!held.isDamageable() || held.getDamage() == 0) {
                    return "That looks fine to me.";
                }
                String metal = e == null ? null : java.util.List.of("underdeep:steel_ingot", "minecraft:iron_ingot").stream()
                        .filter(g -> e.free(g) >= 1).findFirst().orElse(null);
                if (metal == null) {
                    return "I've no metal to spare for repairs right now.";
                }
                int price = repairPrice(held);
                if (!pay(p, e, price)) {
                    return "That'll be " + price + " emeralds.";
                }
                use(e, metal);
                held.setDamage(0); // only damage changes: enchantments and upgrades stay
                dev.underdeep.guide.Advancements.event(p, "repaired", "smith");
                return "Good as new. Look after it.";
            }
            case "s_meal" -> {
                Goods.Good meal = e == null ? null : Goods.in(Goods.Category.PREPARED_FOOD).stream().filter(g -> g.food() && e.free(g.id()) >= 1)
                        .max(java.util.Comparator.comparingInt(Goods.Good::nutrition)).orElse(null);
                if (meal == null) {
                    return "The kitchen's empty today, sorry.";
                }
                if (!pay(p, e, MEAL_PRICE)) {
                    return "One emerald for a meal.";
                }
                use(e, meal.id());
                p.getHungerManager().add(Math.max(6, meal.nutrition()), 0.8f);
                Meals.onEat(p, meal.stack(1));
                dev.underdeep.guide.Advancements.event(p, "service", "inn_meal");
                return "Here - " + meal.name() + ", still hot.";
            }
            case "s_bed" -> {
                if (!pay(p, e, BED_PRICE)) {
                    return "Two emeralds for a bed.";
                }
                SurvivalData.of(p).lastRest = now;
                p.removeStatusEffect(SurvivalEffects.WEARY);
                Meals.apply(p, SurvivalEffects.RESTED, 20 * 60 * 20, 0, false);
                dev.underdeep.guide.Advancements.event(p, "service", "inn_bed");
                return "Your room's upstairs. Sleep well - you'll feel the better for it.";
            }
            default -> {
                return null;
            }
        }
    }

    static int repairPrice(net.minecraft.item.ItemStack s) {
        return 1 + (int) Math.ceil(4.0 * s.getDamage() / Math.max(1, s.getMaxDamage()));
    }

    /** First unknown recipe of this civilization, else any unknown non-basic recipe without a region. */
    @Nullable
    static SurvivalContent.Recipe pick(ServerPlayerEntity p, String civ) {
        for (SurvivalContent.Recipe r : SurvivalContent.regional(civ)) {
            if (!Recipes.knows(p, r)) {
                return r;
            }
        }
        for (SurvivalContent.Recipe r : SurvivalContent.recipes()) {
            if (r.civ().isEmpty() && !Recipes.knows(p, r) && r.station() != SurvivalContent.Station.HERBAL) {
                return r;
            }
        }
        return null;
    }

    static boolean pay(ServerPlayerEntity p, Economy e, int price) {
        if (e == null || !Tx.takeEmeralds(p, price)) {
            return false;
        }
        e.treasuryBalance += price;
        e.playerIn += price;
        return true;
    }

    static void use(Economy e, String good) {
        double got = e.take(good, 1);
        e.stock(good).consumed += got;
    }

    /** Config indirection (keeps this class free of a hard dependency order). */
    static final class UnderdeepConfigHolder {
        static boolean enabled() {
            return dev.underdeep.config.UnderdeepConfig.get().survival.enabled;
        }
    }
}
