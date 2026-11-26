package dev.underdeep.wildlife;

import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/**
 * Temporary creature voices built from vanilla sound events with species-specific pitch (see
 * {@link CreatureSpecies#voicePitch()}). Original recorded/generated sounds are planned for Stage 11;
 * the mapping is the single place to swap them in.
 */
public final class Voices {
    private Voices() {
    }

    public static SoundEvent ambient(CreatureSpecies s) {
        return switch (s.voiceName()) {
            case "goat" -> SoundEvents.ENTITY_GOAT_AMBIENT;
            case "rabbit" -> SoundEvents.ENTITY_RABBIT_AMBIENT;
            case "fox" -> SoundEvents.ENTITY_FOX_AMBIENT;
            case "bee" -> SoundEvents.ENTITY_BEE_LOOP;
            case "hoglin" -> SoundEvents.ENTITY_HOGLIN_AMBIENT;
            case "cat" -> SoundEvents.ENTITY_CAT_AMBIENT;
            case "pig" -> SoundEvents.ENTITY_PIG_AMBIENT;
            case "cow" -> SoundEvents.ENTITY_COW_AMBIENT;
            case "silverfish" -> SoundEvents.ENTITY_SILVERFISH_AMBIENT;
            case "armadillo" -> SoundEvents.ENTITY_ARMADILLO_AMBIENT;
            case "frog" -> SoundEvents.ENTITY_FROG_AMBIENT;
            case "allay" -> SoundEvents.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM;
            case "fish" -> SoundEvents.ENTITY_COD_FLOP;
            case "dolphin" -> SoundEvents.ENTITY_DOLPHIN_AMBIENT_WATER;
            case "strider" -> SoundEvents.ENTITY_STRIDER_AMBIENT;
            case "turtle" -> SoundEvents.ENTITY_TURTLE_AMBIENT_LAND;
            case "ravager" -> SoundEvents.ENTITY_RAVAGER_AMBIENT;
            case "spider" -> SoundEvents.ENTITY_SPIDER_AMBIENT;
            case "bat" -> SoundEvents.ENTITY_BAT_AMBIENT;
            case "warden" -> SoundEvents.ENTITY_WARDEN_AMBIENT;
            case "sheep" -> SoundEvents.ENTITY_SHEEP_AMBIENT;
            case "parrot" -> SoundEvents.ENTITY_PARROT_AMBIENT;
            case "chicken" -> SoundEvents.ENTITY_CHICKEN_AMBIENT;
            case "panda" -> SoundEvents.ENTITY_PANDA_AMBIENT;
            case "camel" -> SoundEvents.ENTITY_CAMEL_AMBIENT;
            case "breeze" -> SoundEvents.ENTITY_BREEZE_IDLE_GROUND;
            default -> SoundEvents.ENTITY_GENERIC_SMALL_FALL;
        };
    }

    public static SoundEvent hurt(CreatureSpecies s) {
        return switch (s.voiceName()) {
            case "goat" -> SoundEvents.ENTITY_GOAT_HURT;
            case "rabbit" -> SoundEvents.ENTITY_RABBIT_HURT;
            case "fox" -> SoundEvents.ENTITY_FOX_HURT;
            case "bee" -> SoundEvents.ENTITY_BEE_HURT;
            case "hoglin" -> SoundEvents.ENTITY_HOGLIN_HURT;
            case "cat" -> SoundEvents.ENTITY_CAT_HURT;
            case "pig" -> SoundEvents.ENTITY_PIG_HURT;
            case "cow" -> SoundEvents.ENTITY_COW_HURT;
            case "silverfish" -> SoundEvents.ENTITY_SILVERFISH_HURT;
            case "armadillo" -> SoundEvents.ENTITY_ARMADILLO_HURT;
            case "frog" -> SoundEvents.ENTITY_FROG_HURT;
            case "allay" -> SoundEvents.ENTITY_ALLAY_HURT;
            case "fish" -> SoundEvents.ENTITY_COD_HURT;
            case "dolphin" -> SoundEvents.ENTITY_DOLPHIN_HURT;
            case "strider" -> SoundEvents.ENTITY_STRIDER_HURT;
            case "turtle" -> SoundEvents.ENTITY_TURTLE_HURT;
            case "ravager" -> SoundEvents.ENTITY_RAVAGER_HURT;
            case "spider" -> SoundEvents.ENTITY_SPIDER_HURT;
            case "bat" -> SoundEvents.ENTITY_BAT_HURT;
            case "warden" -> SoundEvents.ENTITY_WARDEN_HURT;
            case "sheep" -> SoundEvents.ENTITY_SHEEP_HURT;
            case "parrot" -> SoundEvents.ENTITY_PARROT_HURT;
            case "chicken" -> SoundEvents.ENTITY_CHICKEN_HURT;
            case "panda" -> SoundEvents.ENTITY_PANDA_HURT;
            case "camel" -> SoundEvents.ENTITY_CAMEL_HURT;
            case "breeze" -> SoundEvents.ENTITY_BREEZE_HURT;
            default -> SoundEvents.ENTITY_GENERIC_HURT;
        };
    }

    public static SoundEvent death(CreatureSpecies s) {
        return switch (s.voiceName()) {
            case "goat" -> SoundEvents.ENTITY_GOAT_DEATH;
            case "rabbit" -> SoundEvents.ENTITY_RABBIT_DEATH;
            case "fox" -> SoundEvents.ENTITY_FOX_DEATH;
            case "cat" -> SoundEvents.ENTITY_CAT_DEATH;
            case "cow" -> SoundEvents.ENTITY_COW_DEATH;
            case "frog" -> SoundEvents.ENTITY_FROG_DEATH;
            case "dolphin" -> SoundEvents.ENTITY_DOLPHIN_DEATH;
            case "ravager" -> SoundEvents.ENTITY_RAVAGER_DEATH;
            case "spider" -> SoundEvents.ENTITY_SPIDER_DEATH;
            case "warden" -> SoundEvents.ENTITY_WARDEN_DEATH;
            case "parrot" -> SoundEvents.ENTITY_PARROT_DEATH;
            default -> SoundEvents.ENTITY_GENERIC_DEATH;
        };
    }

    public static SoundEvent step(CreatureSpecies s) {
        return s.sizeClass() > 2 ? SoundEvents.ENTITY_RAVAGER_STEP : SoundEvents.ENTITY_COW_STEP;
    }

    /** Short warning call used by territorial displays and herd alarms. */
    public static SoundEvent warning(CreatureSpecies s) {
        return switch (s.voiceName()) {
            case "goat", "camel" -> SoundEvents.ENTITY_GOAT_SCREAMING_AMBIENT;
            case "hoglin", "ravager" -> SoundEvents.ENTITY_HOGLIN_ANGRY;
            case "cat" -> SoundEvents.ENTITY_CAT_HISS;
            case "fox" -> SoundEvents.ENTITY_FOX_AGGRO;
            case "rabbit" -> SoundEvents.ENTITY_RABBIT_ATTACK;
            case "strider" -> SoundEvents.ENTITY_STRIDER_RETREAT;
            default -> hurt(s);
        };
    }
}
