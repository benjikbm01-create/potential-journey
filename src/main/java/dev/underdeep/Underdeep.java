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
        UnderdeepTerrain.passageFrequency = Math.max(0, Math.min(3, UnderdeepConfig.get().caves.passageFrequency));
        UnderdeepTerrain.descentFrequency = Math.max(0, Math.min(1.6, UnderdeepConfig.get().caves.descentFrequency));
        ModBlocks.init();
        dev.underdeep.registry.ModWood.init();
        dev.underdeep.registry.ModPlants.init();
        dev.underdeep.registry.ModItems.init();
        dev.underdeep.wildlife.WildlifeRegistry.init();
        dev.underdeep.wildlife.WildlifeBlocks.init();
        dev.underdeep.wildlife.WildlifeItems.init();
        dev.underdeep.wildlife.population.WildlifeManager.init();
        dev.underdeep.wildlife.WildlifeJournal.init();
        dev.underdeep.civ.settlement.Settlements.init();
        dev.underdeep.civ.npc.InhabitantManager.init();
        dev.underdeep.survival.Survival.registerContent();
        dev.underdeep.equipment.EquipmentItems.init();
        dev.underdeep.explore.Explorations.init();
        dev.underdeep.guide.Advancements.init();
        dev.underdeep.civ.econ.EconomyManager.init();
        dev.underdeep.survival.Survival.init();
        dev.underdeep.guide.Guide.init();
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
                    LOGGER.warn("This world was created with Underdeep terrain generator v{} but v{} is running; content added since "
                                    + "(settlements from v3, exploration sites from v4, the connected cave network from v5) only generates in worlds "
                                    + "created with that version; the world keeps generating its original terrain (no seams).",
                            state.createdWithGeneratorVersion(), UnderdeepTerrain.GENERATOR_VERSION);
                }
                LOGGER.info("Underdeep world loaded (seed-bound terrain ready, spawn {})", overworld.getSpawnPos().toShortString());
            }
        });
        LOGGER.info("Underdeep initialized (terrain generator v{})", UnderdeepTerrain.GENERATOR_VERSION);
    }
}
