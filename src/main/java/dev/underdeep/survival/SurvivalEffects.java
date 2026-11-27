package dev.underdeep.survival;

import dev.underdeep.registry.Reg;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Stage 7 status effects. Protective effects (warmed, cooled, spore ward, clear lungs, grounded) are read by
 * {@link Hazards}; comfort effects (well fed, rested) by the exhaustion mixin; hazard effects are mild and bounded.
 */
public final class SurvivalEffects {
    private SurvivalEffects() {
    }

    static final class Effect extends StatusEffect {
        Effect(StatusEffectCategory c, int color) {
            super(c, color);
        }
    }

    private static final Map<String, RegistryEntry<StatusEffect>> BY_ID = new HashMap<>();
    public static RegistryEntry<StatusEffect> WARMED, COOLED, SPORE_WARD, CLEAR_LUNGS, GROUNDED, WELL_FED, RESTED, HEAT_STRESS, COLD_STRESS,
            SPORE_IRRITATION, CHOKING_FUMES, STATIC_SHOCK, WOUNDED, WEARY;

    public static void init() {
        for (Map.Entry<String, Boolean> e : SurvivalContent.EFFECTS.entrySet()) {
            String id = e.getKey();
            Effect eff = new Effect(e.getValue() ? StatusEffectCategory.BENEFICIAL : StatusEffectCategory.HARMFUL,
                    SurvivalContent.EFFECT_COLORS.getOrDefault(id, 0xFFFFFF));
            Identifier mid = Reg.id("effect." + id);
            switch (id) {
                case "cold_stress" -> eff.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, mid, -0.10, Operation.ADD_MULTIPLIED_TOTAL);
                case "wounded" -> eff.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, mid, -0.08, Operation.ADD_MULTIPLIED_TOTAL);
                case "weary" -> eff.addAttributeModifier(EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, mid, -0.10, Operation.ADD_MULTIPLIED_TOTAL);
                case "static_shock" -> eff.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED, mid, -0.15, Operation.ADD_MULTIPLIED_TOTAL);
                case "spore_irritation" -> eff.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED, mid, -0.10, Operation.ADD_MULTIPLIED_TOTAL);
                case "heat_stress" -> eff.addAttributeModifier(EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, mid, -0.10, Operation.ADD_MULTIPLIED_TOTAL);
                case "rested" -> eff.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, mid, 0.03, Operation.ADD_MULTIPLIED_TOTAL);
                default -> {
                }
            }
            BY_ID.put(id, Registry.registerReference(Registries.STATUS_EFFECT, Reg.id(id), eff));
        }
        WARMED = get("warmed");
        COOLED = get("cooled");
        SPORE_WARD = get("spore_ward");
        CLEAR_LUNGS = get("clear_lungs");
        GROUNDED = get("grounded");
        WELL_FED = get("well_fed");
        RESTED = get("rested");
        HEAT_STRESS = get("heat_stress");
        COLD_STRESS = get("cold_stress");
        SPORE_IRRITATION = get("spore_irritation");
        CHOKING_FUMES = get("choking_fumes");
        STATIC_SHOCK = get("static_shock");
        WOUNDED = get("wounded");
        WEARY = get("weary");
    }

    public static RegistryEntry<StatusEffect> get(String id) {
        RegistryEntry<StatusEffect> e = BY_ID.get(id);
        if (e == null) {
            throw new IllegalStateException("missing effect " + id);
        }
        return e;
    }

    /** Any effect by full id (minecraft or underdeep), or null. */
    public static RegistryEntry<StatusEffect> byId(Identifier id) {
        return Registries.STATUS_EFFECT.getEntry(id).map(r -> (RegistryEntry<StatusEffect>) r).orElse(null);
    }
}
