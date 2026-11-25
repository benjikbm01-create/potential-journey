package dev.underdeep.block.plant;

import dev.underdeep.config.UnderdeepConfig;

import java.util.function.BooleanSupplier;

/**
 * Client-side gate for ambient ecological particles. The client entry point installs a supplier that
 * also honours the vanilla "Particles: Minimal" option; common code never touches client classes.
 * randomDisplayTick is only called for blocks near the player, so particle cost is already bounded
 * by distance; each block additionally rolls a small chance.
 */
public final class ParticleGate {
    private static volatile BooleanSupplier clientAllows = () -> true;

    private ParticleGate() {
    }

    public static void install(BooleanSupplier supplier) {
        clientAllows = supplier;
    }

    public static boolean allow() {
        return UnderdeepConfig.get().ambientParticles && clientAllows.getAsBoolean();
    }
}
