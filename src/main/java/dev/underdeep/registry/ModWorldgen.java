package dev.underdeep.registry;

import dev.underdeep.Underdeep;
import dev.underdeep.world.feature.CaveSurfacePlacement;
import dev.underdeep.world.feature.CrystalSpireFeature;
import dev.underdeep.world.feature.HangingRootFeature;
import dev.underdeep.world.feature.OreOutcropFeature;
import dev.underdeep.world.feature.StartZonePlacement;
import dev.underdeep.world.gen.UnderdeepBiomeSource;
import dev.underdeep.world.gen.UnderdeepChunkGenerator;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.placementmodifier.PlacementModifierType;

/** Registration of Underdeep world generation types. */
public final class ModWorldgen {
    public static final PlacementModifierType<CaveSurfacePlacement> CAVE_SURFACE =
            Registry.register(Registries.PLACEMENT_MODIFIER_TYPE, id("cave_surface"), () -> CaveSurfacePlacement.CODEC);
    public static final PlacementModifierType<dev.underdeep.world.feature.VariantPlacement> VARIANT =
            Registry.register(Registries.PLACEMENT_MODIFIER_TYPE, id("variant"), () -> dev.underdeep.world.feature.VariantPlacement.CODEC);
    public static final PlacementModifierType<StartZonePlacement> START_ZONE =
            Registry.register(Registries.PLACEMENT_MODIFIER_TYPE, id("start_zone"), () -> StartZonePlacement.CODEC);

    public static final Feature<DefaultFeatureConfig> CRYSTAL_SPIRE =
            Registry.register(Registries.FEATURE, id("crystal_spire"), new CrystalSpireFeature(DefaultFeatureConfig.CODEC));
    public static final Feature<DefaultFeatureConfig> HANGING_ROOT =
            Registry.register(Registries.FEATURE, id("hanging_root"), new HangingRootFeature(DefaultFeatureConfig.CODEC));
    public static final Feature<DefaultFeatureConfig> ORE_OUTCROP =
            Registry.register(Registries.FEATURE, id("ore_outcrop"), new OreOutcropFeature(DefaultFeatureConfig.CODEC));

    // ---- Stage 2 ----
    public static final Feature<dev.underdeep.world.feature.SpeciesFeatureConfig> TREE =
            Registry.register(Registries.FEATURE, id("tree"), new dev.underdeep.world.feature.UnderdeepTreeFeature());
    public static final Feature<dev.underdeep.world.feature.SpeciesFeatureConfig> GIANT_FUNGUS =
            Registry.register(Registries.FEATURE, id("giant_fungus"), new dev.underdeep.world.feature.GiantFungusFeature());
    public static final Feature<dev.underdeep.world.feature.SpeciesFeatureConfig> FALLEN_LOG =
            Registry.register(Registries.FEATURE, id("fallen_log"), new dev.underdeep.world.feature.FallenLogFeature());
    public static final Feature<DefaultFeatureConfig> CORAL_CLUSTER =
            Registry.register(Registries.FEATURE, id("coral_cluster"), new dev.underdeep.world.feature.CoralClusterFeature());

    private ModWorldgen() {
    }

    static Identifier id(String path) {
        return Identifier.of(Underdeep.MOD_ID, path);
    }

    public static void init() {
        Registry.register(Registries.CHUNK_GENERATOR, id("underdeep"), UnderdeepChunkGenerator.CODEC);
        Registry.register(Registries.BIOME_SOURCE, id("underdeep"), UnderdeepBiomeSource.CODEC);
    }
}
