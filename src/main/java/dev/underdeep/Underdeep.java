package dev.underdeep;

import dev.underdeep.command.UnderdeepCommands;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.debug.SelfTest;
import dev.underdeep.registry.ModBlocks;
import dev.underdeep.registry.ModWorldgen;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.data.UnderdeepWorldState;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point. See CLAUDE.md and docs/ARCHITECTURE.md for the system overview.
 */
public final class Underdeep implements ModInitializer {
    public static final String MOD_ID = "underdeep";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        UnderdeepConfig.load();
        ModBlocks.init();
        dev.underdeep.registry.ModWood.init();
        dev.underdeep.registry.ModPlants.init();
        dev.underdeep.registry.ModItems.init();
        dev.underdeep.wildlife.WildlifeRegistry.init();
        dev.underdeep.wildlife.WildlifeBlocks.init();
        dev.underdeep.wildlife.WildlifeItems.init();
        dev.underdeep.wildlife.population.WildlifeManager.init();
        dev.underdeep.wildlife.WildlifeJournal.init();
        ModWorldgen.init();
        UnderdeepCommands.init();
        SelfTest.init();
        dev.underdeep.debug.MobStudy.init();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerWorld overworld = server.getOverworld();
            if (overworld != null && UnderdeepWorlds.isUnderdeep(overworld)) {
                UnderdeepWorldState state = UnderdeepWorldState.get(overworld);
                state.initializeIfNew(overworld.getTime());
                if (state.createdWithGeneratorVersion() != UnderdeepTerrain.GENERATOR_VERSION) {
                    LOGGER.warn("This world was created with Underdeep terrain generator v{} but v{} is running; "
                                    + "newly generated chunks may not match existing ones at their borders.",
                            state.createdWithGeneratorVersion(), UnderdeepTerrain.GENERATOR_VERSION);
                }
                LOGGER.info("Underdeep world loaded (seed-bound terrain ready, spawn {})", overworld.getSpawnPos().toShortString());
            }
        });
        LOGGER.info("Underdeep initialized (terrain generator v{})", UnderdeepTerrain.GENERATOR_VERSION);
    }
}
