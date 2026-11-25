package dev.underdeep.registry;

import dev.underdeep.block.plant.GrowthRule;
import dev.underdeep.block.plant.GrowthRule.Need;
import dev.underdeep.ecology.Species.Climate;
import dev.underdeep.ecology.Species.LightPref;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.PillarBlock;

/**
 * Stage 2 trees: seven complete wood sets and two further species that use their own logs/leaves.
 * Tree shapes live in {@link dev.underdeep.world.feature.UnderdeepTreeFeature}.
 */
public final class ModWood {
    private static GrowthRule sap(LightPref l, Climate c, Need n) {
        return GrowthRule.of(l, c, n, 0.14f);
    }

    public static final WoodSet DUSKOAK = new WoodSet("duskoak", MapColor.TERRACOTTA_BROWN, MapColor.BROWN, true)
            .leaves(MapColor.DARK_GREEN, 0).sapling("duskoak", ModTags.PLANT_SOIL, sap(LightPref.ANY, Climate.ANY, Need.NONE), 7);
    public static final WoodSet SPIREBARK = new WoodSet("spirebark", MapColor.PALE_YELLOW, MapColor.LIGHT_GRAY, true)
            .leaves(MapColor.TEAL, 0).sapling("spirebark", ModTags.MINERAL_SOIL, sap(LightPref.ANY, Climate.ANY, Need.NONE), 7);
    public static final WoodSet GOLDWOOD = new WoodSet("goldwood", MapColor.GOLD, MapColor.TERRACOTTA_YELLOW, true)
            .leaves(ModBlocks.GOLDEN_LEAVES).sapling("goldbough", ModTags.PLANT_SOIL, sap(LightPref.ANY, Climate.ANY, Need.NONE), 10);
    public static final WoodSet MIREWILLOW = new WoodSet("mirewillow", MapColor.TERRACOTTA_GREEN, MapColor.DARK_GREEN, true)
            .leaves(MapColor.GREEN, 0).sapling("mirewillow", ModTags.WET_SOIL, sap(LightPref.ANY, Climate.ANY, Need.NONE), 7);
    public static final WoodSet FROSTFIR = new WoodSet("frostfir", MapColor.WHITE_GRAY, MapColor.LIGHT_BLUE_GRAY, true)
            .leaves(MapColor.CYAN, 0).sapling("frostfir", ModTags.FROZEN_SOIL, sap(LightPref.ANY, Climate.COLD, Need.NO_HEAT), 6);
    public static final WoodSet BLOODTHORN = new WoodSet("bloodthorn", MapColor.DARK_RED, MapColor.TERRACOTTA_RED, false)
            .leaves(MapColor.RED, 4).sapling("bloodthorn", ModTags.VOLCANIC_SOIL, sap(LightPref.ANY, Climate.HOT, Need.NONE), 3);
    public static final WoodSet LUMENWOOD = new WoodSet("lumenwood", MapColor.LIGHT_BLUE, MapColor.WHITE_GRAY, true)
            .leaves(MapColor.LIGHT_BLUE, 10).sapling("lumenwood", ModTags.PLANT_SOIL, sap(LightPref.ANY, Climate.ANY, Need.NONE), 7);

    // ---- species without a full wood set ----
    public static final Block CRAGPINE_LEAVES = mipped(Reg.blockItem("cragpine_leaves", WoodSet.leavesBlock(MapColor.DARK_GREEN, 0)));
    public static final Block CRAGPINE_SAPLING = WoodSet.sapling("cragpine", "cragpine", ModTags.MINERAL_SOIL,
            sap(LightPref.ANY, Climate.ANY, Need.NONE), 7);
    public static final Block POTTED_CRAGPINE_SAPLING = WoodSet.potted("cragpine", CRAGPINE_SAPLING);
    public static final Block CINDERBARK_LOG = Reg.blockItem("cinderbark_log", new PillarBlock(AbstractBlock.Settings.copy(Blocks.CRIMSON_STEM)
            .mapColor(MapColor.BLACK)));
    public static final Block STRIPPED_CINDERBARK_LOG = Reg.blockItem("stripped_cinderbark_log",
            new PillarBlock(AbstractBlock.Settings.copy(Blocks.CRIMSON_STEM).mapColor(MapColor.TERRACOTTA_ORANGE)));
    public static final Block CINDER_LEAVES = mipped(Reg.blockItem("cinder_leaves", WoodSet.leavesBlock(MapColor.ORANGE, 6)));
    public static final Block CINDERBARK_SAPLING = WoodSet.sapling("cinderbark", "cinderbark", ModTags.VOLCANIC_SOIL,
            sap(LightPref.ANY, Climate.HOT, Need.HEAT), 0);
    public static final Block POTTED_CINDERBARK_SAPLING = WoodSet.potted("cinderbark", CINDERBARK_SAPLING);

    private static Block mipped(Block b) {
        Reg.CUTOUT_MIPPED.add(b);
        return b;
    }

    private ModWood() {
    }

    public static void init() {
        StrippableBlockRegistry.register(CINDERBARK_LOG, STRIPPED_CINDERBARK_LOG);
        FlammableBlockRegistry.getDefaultInstance().add(CRAGPINE_LEAVES, 30, 60);
    }
}
