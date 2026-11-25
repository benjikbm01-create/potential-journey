package dev.underdeep.block.plant;

import dev.underdeep.ecology.Species;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Environmental requirements of a growing organism. {@link #factor} returns a growth multiplier:
 * 0 means dormant (no growth), 1 is ideal. Deliberately small: light band, climate and at most one
 * special need, so players can work out requirements by experimenting (tooltips give hints).
 *
 * @param light   accepted block-light band at the plant
 * @param climate preferred temperature class
 * @param need    special requirement
 * @param chance  base chance per random tick to advance one growth step under ideal conditions
 */
public record GrowthRule(Species.LightPref light, Species.Climate climate, Need need, float chance) {
    public enum Need {
        NONE,
        /** Water within 1 block horizontally (moisture-dependent crops). */
        WATER,
        /** Lava, fire or magma within 3 blocks; dormant otherwise. */
        HEAT,
        /** Grows anywhere but three times faster near heat. */
        HEAT_BOOST,
        /** Dormant when heat is within 3 blocks (cold-adapted species). */
        NO_HEAT
    }

    public static final GrowthRule ANY = new GrowthRule(Species.LightPref.ANY, Species.Climate.ANY, Need.NONE, 0.2f);

    public static GrowthRule of(Species.LightPref light, Species.Climate climate, Need need, float chance) {
        return new GrowthRule(light, climate, need, chance);
    }

    public GrowthRule withChance(float c) {
        return new GrowthRule(light, climate, need, c);
    }

    public double factor(ServerWorld world, BlockPos pos) {
        if (!light.accepts(Env.light(world, pos))) {
            return 0;
        }
        double f = Env.climateFactor(climate, world, pos);
        if (f <= 0) {
            return 0;
        }
        switch (need) {
            case WATER -> {
                if (!Env.nearWater(world, pos, 1)) {
                    return 0;
                }
            }
            case HEAT -> {
                if (!Env.nearHeat(world, pos, 3)) {
                    return 0;
                }
            }
            case HEAT_BOOST -> f *= Env.nearHeat(world, pos, 3) ? 1.0 : 0.33;
            case NO_HEAT -> {
                if (Env.nearHeat(world, pos, 3)) {
                    return 0;
                }
            }
            default -> {
            }
        }
        return f;
    }

    /** Short English hint used for tooltips when no translation exists. */
    public String hint() {
        StringBuilder b = new StringBuilder();
        b.append(switch (light) {
            case DARK -> "darkness";
            case DIM -> "dim light";
            case BRIGHT -> "bright light";
            case ANY -> "any light";
        });
        if (climate != Species.Climate.ANY) {
            b.append(", ").append(climate.name().toLowerCase(java.util.Locale.ROOT)).append(" climate");
        }
        switch (need) {
            case WATER -> b.append(", water beside it");
            case HEAT -> b.append(", nearby heat");
            case HEAT_BOOST -> b.append(", faster near heat");
            case NO_HEAT -> b.append(", away from heat");
            default -> {
            }
        }
        return b.toString();
    }
}
