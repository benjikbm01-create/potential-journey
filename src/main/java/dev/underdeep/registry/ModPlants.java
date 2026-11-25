package dev.underdeep.registry;

import dev.underdeep.block.plant.AquaticCropBlock;
import dev.underdeep.block.plant.AquaticHarvestableBlock;
import dev.underdeep.block.plant.AquaticStemBlock;
import dev.underdeep.block.plant.BloomingPlantBlock;
import dev.underdeep.block.plant.BloomingPlantBlock.Trigger;
import dev.underdeep.block.plant.CrystalGrowerBlock;
import dev.underdeep.block.plant.EcoCropBlock;
import dev.underdeep.block.plant.EcoPlantBlock;
import dev.underdeep.block.plant.EphemeralFungusBlock;
import dev.underdeep.block.plant.FloatingPlantBlock;
import dev.underdeep.block.plant.GrowthRule;
import dev.underdeep.block.plant.GrowthRule.Need;
import dev.underdeep.block.plant.HangingBodyBlock;
import dev.underdeep.block.plant.HangingStemBlock;
import dev.underdeep.block.plant.HarvestablePlantBlock;
import dev.underdeep.block.plant.ResinKnotBlock;
import dev.underdeep.block.plant.RottingLogBlock;
import dev.underdeep.block.plant.ShelfFungusBlock;
import dev.underdeep.block.plant.SnapjawBlock;
import dev.underdeep.block.plant.SporePuffBlock;
import dev.underdeep.block.plant.SubmergedPlantBlock;
import dev.underdeep.ecology.Species.Climate;
import dev.underdeep.ecology.Species.LightPref;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.block.GlowLichenBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.intprovider.UniformIntProvider;

import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import static dev.underdeep.registry.ModTags.AQUATIC_BED;
import static dev.underdeep.registry.ModTags.FROZEN_SOIL;
import static dev.underdeep.registry.ModTags.FUNGAL_SOIL;
import static dev.underdeep.registry.ModTags.MINERAL_SOIL;
import static dev.underdeep.registry.ModTags.PLANT_SOIL;
import static dev.underdeep.registry.ModTags.VOLCANIC_SOIL;
import static dev.underdeep.registry.ModTags.WET_SOIL;

/**
 * Stage 2 flora, fungi, aquatic life, crops and minerals. Ecological metadata for each lives in
 * {@link dev.underdeep.ecology.EcologyRegistry}; generation in tools/worldgen/generate_data.py.
 */
public final class ModPlants {
    // ---------------- settings helpers ----------------
    static AbstractBlock.Settings plant(MapColor c, int light) {
        AbstractBlock.Settings s = AbstractBlock.Settings.create().mapColor(c).noCollision().breakInstantly()
                .sounds(BlockSoundGroup.GRASS).offset(AbstractBlock.OffsetType.XZ).pistonBehavior(PistonBehavior.DESTROY);
        return light > 0 ? s.luminance(st -> light) : s;
    }

    static AbstractBlock.Settings plant(MapColor c, ToIntFunction<net.minecraft.block.BlockState> light) {
        return AbstractBlock.Settings.create().mapColor(c).noCollision().breakInstantly().sounds(BlockSoundGroup.GRASS)
                .offset(AbstractBlock.OffsetType.XZ).pistonBehavior(PistonBehavior.DESTROY).luminance(light);
    }

    static AbstractBlock.Settings grass(MapColor c, int light) {
        return plant(c, light).replaceable().burnable();
    }

    static AbstractBlock.Settings fungus(MapColor c, int light) {
        return plant(c, light).sounds(BlockSoundGroup.FUNGUS);
    }

    static AbstractBlock.Settings bush(MapColor c, ToIntFunction<net.minecraft.block.BlockState> light) {
        return AbstractBlock.Settings.create().mapColor(c).noCollision().breakInstantly().sounds(BlockSoundGroup.SWEET_BERRY_BUSH)
                .pistonBehavior(PistonBehavior.DESTROY).luminance(light);
    }

    static AbstractBlock.Settings water(MapColor c, int light) {
        AbstractBlock.Settings s = AbstractBlock.Settings.create().mapColor(c).noCollision().breakInstantly()
                .sounds(BlockSoundGroup.WET_GRASS).pistonBehavior(PistonBehavior.DESTROY).replaceable();
        return light > 0 ? s.luminance(st -> light) : s;
    }

    static GrowthRule rule(LightPref l, Climate c, Need n, float chance) {
        return GrowthRule.of(l, c, n, chance);
    }

    static <T extends Block> T plantItem(String name, T block) {
        return Reg.cutout(Reg.blockItem(name, block));
    }

    static int byAge(net.minecraft.block.BlockState s, int young, int mature) {
        return s.get(HarvestablePlantBlock.AGE) == 3 ? mature : young;
    }

    // ================= ROOTWILD =================
    public static final Block DUSKBERRY_BUSH = Reg.cutout(Reg.block("duskberry_bush", new HarvestablePlantBlock(PLANT_SOIL,
            () -> ModItems.DUSKBERRIES, 2, 3, bush(MapColor.DARK_GREEN, s -> byAge(s, 0, 4)))
            .rule(rule(LightPref.ANY, Climate.ANY, Need.NONE, 0.2f))));
    public static final Block GLOWFERN = plantItem("glowfern", new EcoPlantBlock(PLANT_SOIL, grass(MapColor.GREEN, 6))
            .spreads(0.08f, 2, 5).shape(12, 13));
    public static final Block MENDLEAF = plantItem("mendleaf", new HarvestablePlantBlock(PLANT_SOIL, () -> ModItems.MENDLEAF_SPRIG, 1, 2,
            plant(MapColor.GREEN, 0)).rule(rule(LightPref.ANY, Climate.ANY, Need.NONE, 0.15f)).shape(10, 10));
    public static final Block GLOWIVY = plantItem("glowivy", new GlowLichenBlock(AbstractBlock.Settings.copy(Blocks.GLOW_LICHEN)
            .mapColor(MapColor.LIME).luminance(GlowLichenBlock.getLuminanceSupplier(8))));

    // ================= GOLDEN GROVE =================
    public static final Block HONEYFRUIT_BUSH = Reg.cutout(Reg.block("honeyfruit_bush", new HarvestablePlantBlock(PLANT_SOIL,
            () -> ModItems.HONEYFRUIT, 1, 2, bush(MapColor.YELLOW, s -> byAge(s, 2, 7)))
            .rule(rule(LightPref.ANY, Climate.ANY, Need.NONE, 0.15f))));
    public static final Block GOLDGRASS = plantItem("goldgrass", new EcoPlantBlock(PLANT_SOIL, grass(MapColor.GOLD, 0))
            .spreads(0.1f, 2, 6).shape(12, 12).particles(ParticleTypes.SPORE_BLOSSOM_AIR, 0.01f));
    public static final Block SUNLEAF = plantItem("sunleaf", new HarvestablePlantBlock(PLANT_SOIL, () -> ModItems.SUNLEAF_BLOSSOM, 1, 1,
            plant(MapColor.YELLOW, s -> byAge(s, 3, 10))).rule(rule(LightPref.BRIGHT, Climate.ANY, Need.NONE, 0.06f)).shape(10, 12));

    // ================= ASHEN HOLLOWS =================
    public static final Block ASHGRASS = plantItem("ashgrass", new EcoPlantBlock(VOLCANIC_SOIL, grass(MapColor.GRAY, 0))
            .spreads(0.05f, 2, 4).rule(rule(LightPref.ANY, Climate.HOT, Need.NONE, 0.1f)).shape(12, 10)
            .particles(ParticleTypes.WHITE_ASH, 0.05f));
    public static final Block PYRELILY = plantItem("pyrelily", new BloomingPlantBlock(VOLCANIC_SOIL, Trigger.HEAT,
            plant(MapColor.ORANGE, s -> BloomingPlantBlock.light(s, 3, 12))).spreads(0.06f, 3, 3).shape(10, 13)
            .particles(ParticleTypes.SMALL_FLAME, 0.0f));
    public static final Block EMBERWORT = plantItem("emberwort", new HarvestablePlantBlock(VOLCANIC_SOIL, () -> ModItems.EMBERWORT_ROOT, 1, 2,
            plant(MapColor.DARK_RED, s -> byAge(s, 2, 6))).rule(rule(LightPref.ANY, Climate.HOT, Need.HEAT_BOOST, 0.12f)).shape(10, 10));

    // ================= CRYSTAL DEEP =================
    public static final Block SHARDGRASS = translucent(plantItem("shardgrass", new EcoPlantBlock(MINERAL_SOIL,
            grass(MapColor.CYAN, 4).sounds(BlockSoundGroup.SMALL_AMETHYST_BUD)).spreads(0.04f, 2, 4).shape(12, 9)));
    public static final Block PRISM_LILY = translucent(plantItem("prism_lily", new BloomingPlantBlock(MINERAL_SOIL, Trigger.DARK,
            plant(MapColor.PALE_PURPLE, s -> BloomingPlantBlock.light(s, 5, 12)).sounds(BlockSoundGroup.AMETHYST_CLUSTER))
            .spreads(0.02f, 3, 2).shape(10, 14).particles(ParticleTypes.END_ROD, 0f)));
    public static final Block GHOSTFERN = translucent(plantItem("ghostfern", new EcoPlantBlock(MINERAL_SOIL, grass(MapColor.WHITE_GRAY, 3))
            .spreads(0.05f, 2, 4).rule(rule(LightPref.DARK, Climate.ANY, Need.NONE, 0.1f)).shape(12, 13)));

    // ================= THE ABYSS =================
    public static final Block VOIDROOT = Reg.cutout(Reg.blockItem("voidroot", new HangingStemBlock(() -> ModPlants.VOIDROOT_PLANT,
            AbstractBlock.Settings.copy(Blocks.WEEPING_VINES).mapColor(MapColor.BLACK).luminance(s -> 2))));
    public static final Block VOIDROOT_PLANT = Reg.cutout(Reg.block("voidroot_plant", new HangingBodyBlock(() -> ModPlants.VOIDROOT,
            AbstractBlock.Settings.copy(Blocks.WEEPING_VINES_PLANT).mapColor(MapColor.BLACK))));
    public static final Block UMBRAL_BLOOM = plantItem("umbral_bloom", new BloomingPlantBlock(MINERAL_SOIL, Trigger.DARK,
            plant(MapColor.PURPLE, s -> BloomingPlantBlock.light(s, 1, 5))).spreads(0.01f, 4, 1).shape(10, 12)
            .particles(ParticleTypes.REVERSE_PORTAL, 0f));

    // ================= EVERFROST =================
    public static final Block FROSTBELL = plantItem("frostbell", new BloomingPlantBlock(FROZEN_SOIL, Trigger.COLD,
            plant(MapColor.LIGHT_BLUE, s -> BloomingPlantBlock.light(s, 2, 8))).spreads(0.05f, 2, 4).shape(10, 12)
            .particles(ParticleTypes.SNOWFLAKE, 0f));
    public static final Block SNOWBERRY_BUSH = Reg.cutout(Reg.block("snowberry_bush", new HarvestablePlantBlock(FROZEN_SOIL,
            () -> ModItems.SNOWBERRIES, 2, 3, bush(MapColor.WHITE, s -> 0))
            .rule(rule(LightPref.ANY, Climate.COLD, Need.NO_HEAT, 0.08f))));
    public static final Block STEAMFERN = plantItem("steamfern", new EcoPlantBlock(FROZEN_SOIL, grass(MapColor.DARK_GREEN, 0))
            .spreads(0.1f, 2, 5).rule(rule(LightPref.ANY, Climate.ANY, Need.HEAT, 0.1f)).shape(12, 13)
            .particles(ParticleTypes.CLOUD, 0.02f));

    // ================= HOLLOW PEAKS =================
    public static final Block CLIFFBERRY_BUSH = Reg.cutout(Reg.block("cliffberry_bush", new HarvestablePlantBlock(MINERAL_SOIL,
            () -> ModItems.CLIFFBERRIES, 1, 3, bush(MapColor.DARK_GREEN, s -> 0))
            .rule(rule(LightPref.ANY, Climate.ANY, Need.NONE, 0.12f))));
    public static final Block PEAKSTAR = plantItem("peakstar", new BloomingPlantBlock(MINERAL_SOIL, Trigger.BRIGHT,
            plant(MapColor.WHITE, s -> BloomingPlantBlock.light(s, 0, 6))).spreads(0.015f, 3, 2).shape(8, 10));
    public static final Block LEDGEVINE = Reg.cutout(Reg.blockItem("ledgevine", new HangingStemBlock(() -> ModPlants.LEDGEVINE_PLANT,
            AbstractBlock.Settings.copy(Blocks.WEEPING_VINES).mapColor(MapColor.GREEN).sounds(BlockSoundGroup.CAVE_VINES))));
    public static final Block LEDGEVINE_PLANT = Reg.cutout(Reg.block("ledgevine_plant", new HangingBodyBlock(() -> ModPlants.LEDGEVINE,
            AbstractBlock.Settings.copy(Blocks.WEEPING_VINES_PLANT).mapColor(MapColor.GREEN).sounds(BlockSoundGroup.CAVE_VINES))));

    // ================= EMERALD WETLANDS =================
    public static final Block BOGBLOOM = plantItem("bogbloom", new BloomingPlantBlock(WET_SOIL, Trigger.WATER,
            plant(MapColor.PINK, s -> BloomingPlantBlock.light(s, 2, 9))).spreads(0.06f, 2, 4).shape(10, 12));
    public static final Block MARSH_FERN = plantItem("marsh_fern", new EcoPlantBlock(WET_SOIL, grass(MapColor.DARK_GREEN, 0))
            .spreads(0.08f, 2, 5).shape(14, 13));
    public static final Block BOGWORT = plantItem("bogwort", new HarvestablePlantBlock(WET_SOIL, () -> ModItems.BOGWORT_LEAF, 1, 2,
            plant(MapColor.GREEN, 0)).rule(rule(LightPref.ANY, Climate.ANY, Need.WATER, 0.12f)).shape(10, 10));

    // ================= PETRIFIED FOREST =================
    public static final Block STONEROOT = plantItem("stoneroot", new EcoPlantBlock(MINERAL_SOIL, plant(MapColor.TERRACOTTA_BROWN, 0)
            .sounds(BlockSoundGroup.ROOTS)).spreads(0.03f, 2, 3).shape(14, 10));
    public static final Block PETROLICHEN = plantItem("petrolichen", new GlowLichenBlock(AbstractBlock.Settings.copy(Blocks.GLOW_LICHEN)
            .mapColor(MapColor.TERRACOTTA_YELLOW).luminance(s -> 0)));

    // ================= CRIMSON BASIN =================
    public static final Block THORNBRIAR = Reg.cutout(Reg.block("thornbriar", new HarvestablePlantBlock(VOLCANIC_SOIL,
            () -> ModItems.BRIAR_FRUIT, 1, 2, bush(MapColor.DARK_RED, s -> byAge(s, 0, 3)))
            .rule(rule(LightPref.ANY, Climate.HOT, Need.NONE, 0.12f)).thorns(1.5f)));
    public static final Block PLAGUEBLOOM = plantItem("plaguebloom", new EcoPlantBlock(VOLCANIC_SOIL, plant(MapColor.LIME, 5))
            .contact(new StatusEffectInstance(StatusEffects.POISON, 60, 0)).spreads(0.04f, 2, 3).shape(10, 12)
            .particles(ParticleTypes.CRIMSON_SPORE, 0.08f));
    public static final Block SNAPJAW = plantItem("snapjaw", new SnapjawBlock(VOLCANIC_SOIL, plant(MapColor.RED, 3)).shape(12, 10));

    // ================= STORMSTONE =================
    public static final Block STATIC_GRASS = plantItem("static_grass", new EcoPlantBlock(MINERAL_SOIL, grass(MapColor.BLUE, 3))
            .spreads(0.05f, 2, 5).shape(12, 11).particles(ParticleTypes.ELECTRIC_SPARK, 0.015f));
    public static final Block VOLTBLOOM = translucent(plantItem("voltbloom", new BloomingPlantBlock(MINERAL_SOIL, Trigger.ENERGY,
            plant(MapColor.PURPLE, s -> BloomingPlantBlock.light(s, 4, 13)).sounds(BlockSoundGroup.AMETHYST_CLUSTER))
            .spreads(0.03f, 3, 3).shape(10, 13).particles(ParticleTypes.ELECTRIC_SPARK, 0f)));

    // ================= STARLIGHT =================
    public static final Block SILVERGRASS = plantItem("silvergrass", new EcoPlantBlock(PLANT_SOIL, grass(MapColor.LIGHT_BLUE_GRAY, 5))
            .spreads(0.1f, 2, 6).shape(12, 12));
    public static final Block MOONPETAL = plantItem("moonpetal", new HarvestablePlantBlock(PLANT_SOIL, () -> ModItems.MOONPETAL_PETAL, 1, 2,
            plant(MapColor.WHITE, s -> byAge(s, 4, 11))).rule(rule(LightPref.BRIGHT, Climate.ANY, Need.NONE, 0.06f)).shape(10, 12)
            .particles(ParticleTypes.END_ROD, 0.02f));
    public static final Block STARSTRAND = Reg.cutout(Reg.blockItem("starstrand", new HangingStemBlock(() -> ModPlants.STARSTRAND_PLANT,
            AbstractBlock.Settings.copy(Blocks.WEEPING_VINES).mapColor(MapColor.LIGHT_BLUE).luminance(s -> 12))));
    public static final Block STARSTRAND_PLANT = Reg.cutout(Reg.block("starstrand_plant", new HangingBodyBlock(() -> ModPlants.STARSTRAND,
            AbstractBlock.Settings.copy(Blocks.WEEPING_VINES_PLANT).mapColor(MapColor.LIGHT_BLUE).luminance(s -> 6))));

    // ================= FUNGI =================
    public static final Block PUFFCAP = Reg.cutout(Reg.block("puffcap", new EcoPlantBlock(FUNGAL_SOIL, fungus(MapColor.PALE_YELLOW, 0))
            .spreads(0.06f, 2, 5).rule(rule(LightPref.DIM, Climate.ANY, Need.NONE, 0.1f)).shape(10, 7)));
    public static final Block INKCAP = Reg.cutout(Reg.block("inkcap", new EcoPlantBlock(FUNGAL_SOIL, fungus(MapColor.BLACK, 0))
            .spreads(0.05f, 2, 4).rule(rule(LightPref.DARK, Climate.ANY, Need.NONE, 0.1f)).shape(8, 12)));
    public static final Block DEATHBELL = plantItem("deathbell", new EcoPlantBlock(FUNGAL_SOIL, fungus(MapColor.PURPLE, 2))
            .spreads(0.03f, 2, 3).rule(rule(LightPref.DARK, Climate.ANY, Need.NONE, 0.1f)).shape(8, 12)
            .contact(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0)));
    public static final Block LANTERNCAP = plantItem("lanterncap", new EphemeralFungusBlock(FUNGAL_SOIL,
            plant(MapColor.LIGHT_BLUE, EphemeralFungusBlock::light).sounds(BlockSoundGroup.FUNGUS)).spreads(0.25f, 3, 4).shape(10, 9)
            .particles(ParticleTypes.GLOW, 0.03f));
    public static final Block SPOREPUFF = plantItem("sporepuff", new SporePuffBlock(FUNGAL_SOIL,
            List.of(() -> ModPlants.PUFFCAP, () -> ModPlants.INKCAP, () -> ModPlants.LANTERNCAP), fungus(MapColor.TERRACOTTA_WHITE, 0)).shape(10, 8));
    public static final Block SAGECAP = plantItem("sagecap", new EcoPlantBlock(FUNGAL_SOIL, fungus(MapColor.TERRACOTTA_LIGHT_GRAY, 0))
            .spreads(0.01f, 3, 1).rule(rule(LightPref.DARK, Climate.ANY, Need.NONE, 0.05f)).shape(8, 8));
    public static final Block EMBERCAP = plantItem("embercap", new EcoPlantBlock(VOLCANIC_SOIL, fungus(MapColor.ORANGE, 8))
            .spreads(0.03f, 2, 3).rule(rule(LightPref.ANY, Climate.HOT, Need.HEAT, 0.1f)).shape(8, 9)
            .particles(ParticleTypes.SMALL_FLAME, 0.02f));
    public static final Block RIMECAP = Reg.cutout(Reg.block("rimecap", new EcoPlantBlock(FROZEN_SOIL, fungus(MapColor.LIGHT_BLUE, 3))
            .spreads(0.03f, 2, 4).rule(rule(LightPref.ANY, Climate.COLD, Need.NO_HEAT, 0.08f)).shape(8, 8)));
    public static final Block GLOOMCAP = plantItem("gloomcap", new EcoPlantBlock(MINERAL_SOIL, fungus(MapColor.DARK_AQUA, 4))
            .spreads(0.02f, 3, 3).rule(rule(LightPref.DARK, Climate.ANY, Need.NONE, 0.08f)).shape(8, 10));
    public static final Block CALCICAP = plantItem("calcicap", new EcoPlantBlock(MINERAL_SOIL, fungus(MapColor.WHITE, 0)
            .sounds(BlockSoundGroup.CALCITE)).spreads(0.04f, 2, 4).rule(rule(LightPref.DIM, Climate.ANY, Need.NONE, 0.08f)).shape(10, 8));
    public static final Block BLOODCAP = plantItem("bloodcap", new EcoPlantBlock(VOLCANIC_SOIL, fungus(MapColor.DARK_RED, 2))
            .spreads(0.05f, 2, 4).rule(rule(LightPref.ANY, Climate.HOT, Need.NONE, 0.1f)).shape(10, 10)
            .particles(ParticleTypes.CRIMSON_SPORE, 0.04f));
    public static final Block TIDECAP = plantItem("tidecap", new SubmergedPlantBlock(AQUATIC_BED, water(MapColor.CYAN, 10)
            .sounds(BlockSoundGroup.FUNGUS)).spreads(0.04f, 2, 4).shape(10, 9));
    public static final Block BRACKET_FUNGUS = plantItem("bracket_fungus", new ShelfFungusBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.TERRACOTTA_ORANGE).noCollision().strength(0.2f).sounds(BlockSoundGroup.FUNGUS).pistonBehavior(PistonBehavior.DESTROY)));
    public static final Block ROTTING_LOG = Reg.blockItem("rotting_log", new RottingLogBlock(() -> ModPlants.BRACKET_FUNGUS,
            List.of(() -> ModPlants.PUFFCAP, () -> ModPlants.INKCAP, () -> ModPlants.SAGECAP),
            AbstractBlock.Settings.copy(Blocks.OAK_LOG).mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0f).sounds(BlockSoundGroup.NETHER_STEM)));
    // giant fungus parts
    public static final Block FUNGAL_STEM = Reg.blockItem("fungal_stem", new PillarBlock(AbstractBlock.Settings.copy(Blocks.MUSHROOM_STEM).mapColor(MapColor.WHITE_GRAY)));
    public static final Block SPORESTALK_CAP = Reg.blockItem("sporestalk_cap", new Block(AbstractBlock.Settings.copy(Blocks.BROWN_MUSHROOM_BLOCK).mapColor(MapColor.CYAN)));
    public static final Block BELLCAP_CAP = Reg.blockItem("bellcap_cap", new Block(AbstractBlock.Settings.copy(Blocks.BROWN_MUSHROOM_BLOCK).mapColor(MapColor.PURPLE)));
    public static final Block SHELFCAP_CAP = Reg.blockItem("shelfcap_cap", new Block(AbstractBlock.Settings.copy(Blocks.BROWN_MUSHROOM_BLOCK).mapColor(MapColor.ORANGE)));
    public static final Block GLOWGILL = Reg.blockItem("glowgill", new Block(AbstractBlock.Settings.copy(Blocks.SHROOMLIGHT).mapColor(MapColor.LIGHT_BLUE).luminance(s -> 13)));

    // ================= AQUATIC =================
    public static final Block GLOWPAD = Reg.cutout(Reg.block("glowpad", new FloatingPlantBlock(AbstractBlock.Settings.copy(Blocks.LILY_PAD)
            .mapColor(MapColor.LIME).luminance(s -> 9)).spreads(0.06f, 6)));
    public static final Block STARLILY = Reg.cutout(Reg.block("starlily", new FloatingPlantBlock(AbstractBlock.Settings.copy(Blocks.LILY_PAD)
            .mapColor(MapColor.LIGHT_BLUE).luminance(s -> 12)).spreads(0.03f, 4).particles(ParticleTypes.END_ROD)));
    public static final Block LAKEBLOOM = plantItem("lakebloom", new SubmergedPlantBlock(AQUATIC_BED, water(MapColor.PINK, 8))
            .spreads(0.04f, 2, 4).shape(12, 12));
    public static final Block RIBBONWEED = Reg.cutout(Reg.blockItem("ribbonweed", new AquaticStemBlock.Tip(() -> ModPlants.RIBBONWEED_PLANT,
            AbstractBlock.Settings.copy(Blocks.KELP).mapColor(MapColor.DARK_GREEN))));
    public static final Block RIBBONWEED_PLANT = Reg.cutout(Reg.block("ribbonweed_plant", new AquaticStemBlock.Body(() -> ModPlants.RIBBONWEED,
            AbstractBlock.Settings.copy(Blocks.KELP_PLANT).mapColor(MapColor.DARK_GREEN))));
    public static final Block GLOWKELP = Reg.cutout(Reg.blockItem("glowkelp", new AquaticStemBlock.Tip(() -> ModPlants.GLOWKELP_PLANT,
            AbstractBlock.Settings.copy(Blocks.KELP).mapColor(MapColor.CYAN).luminance(s -> 11))));
    public static final Block GLOWKELP_PLANT = Reg.cutout(Reg.block("glowkelp_plant", new AquaticStemBlock.Body(() -> ModPlants.GLOWKELP,
            AbstractBlock.Settings.copy(Blocks.KELP_PLANT).mapColor(MapColor.CYAN).luminance(s -> 6))));
    public static final Block TIDEBLOOM = plantItem("tidebloom", new SubmergedPlantBlock(AQUATIC_BED, water(MapColor.MAGENTA, 12))
            .spreads(0.03f, 2, 3).shape(12, 13));
    public static final Block LANTERNCORAL = Reg.blockItem("lanterncoral", new Block(AbstractBlock.Settings.copy(Blocks.BRAIN_CORAL_BLOCK)
            .mapColor(MapColor.ORANGE).luminance(s -> 10)));
    public static final Block LANTERNCORAL_FAN = plantItem("lanterncoral_fan", new SubmergedPlantBlock(AQUATIC_BED, water(MapColor.ORANGE, 7)
            .sounds(BlockSoundGroup.CORAL)).spreads(0.03f, 2, 4).shape(14, 8));
    public static final Block SPIRECORAL = plantItem("spirecoral", new SubmergedPlantBlock(AQUATIC_BED, water(MapColor.PURPLE, 4)
            .sounds(BlockSoundGroup.CORAL)).spreads(0.03f, 2, 4).shape(10, 15));
    public static final Block SEA_GRAPE_BUSH = Reg.cutout(Reg.block("sea_grape_bush", new AquaticHarvestableBlock(AQUATIC_BED,
            () -> ModItems.SEA_GRAPES, 1, 3, water(MapColor.GREEN, 0).luminance(s -> byAge(s, 0, 5)))
            .rule(rule(LightPref.ANY, Climate.ANY, Need.NONE, 0.12f))));
    public static final Block SILTWORT = plantItem("siltwort", new AquaticHarvestableBlock(AQUATIC_BED, () -> ModItems.SILTWORT_FROND, 1, 1,
            water(MapColor.TERRACOTTA_GREEN, 0)).rule(rule(LightPref.ANY, Climate.ANY, Need.NONE, 0.06f)));

    // ================= CROPS =================
    static AbstractBlock.Settings crop(MapColor c) {
        return AbstractBlock.Settings.copy(Blocks.WHEAT).mapColor(c);
    }

    static Block cropBlock(String name, Block b) {
        return Reg.cutout(Reg.block(name, b));
    }

    public static final Block DUSKROOTS = cropBlock("duskroots", new EcoCropBlock(PLANT_SOIL,
            rule(LightPref.ANY, Climate.ANY, Need.NONE, 0.25f), () -> ModItems.DUSKROOT, crop(MapColor.PURPLE)));
    public static final Block GLOWGRAIN_CROP = cropBlock("glowgrain_crop", new EcoCropBlock(PLANT_SOIL,
            rule(LightPref.BRIGHT, Climate.ANY, Need.NONE, 0.25f), () -> ModItems.GLOWGRAIN_SEEDS,
            crop(MapColor.YELLOW).luminance(s -> s.get(EcoCropBlock.AGE) == 7 ? 7 : 0)));
    public static final Block CAVE_TRUFFLES = cropBlock("cave_truffles", new EcoCropBlock(FUNGAL_SOIL,
            rule(LightPref.DARK, Climate.ANY, Need.NONE, 0.22f), () -> ModItems.CAVE_TRUFFLE, crop(MapColor.BROWN)));
    public static final Block MIRE_RICE_CROP = cropBlock("mire_rice_crop", new EcoCropBlock(WET_SOIL,
            rule(LightPref.ANY, Climate.ANY, Need.WATER, 0.3f), () -> ModItems.MIRE_RICE, crop(MapColor.PALE_GREEN)));
    public static final Block LAKE_CHESTNUTS = cropBlock("lake_chestnuts", new AquaticCropBlock(AQUATIC_BED,
            rule(LightPref.ANY, Climate.ANY, Need.NONE, 0.22f), () -> ModItems.LAKE_CHESTNUT,
            crop(MapColor.DARK_GREEN).sounds(BlockSoundGroup.WET_GRASS)));
    public static final Block EMBERPEPPERS = cropBlock("emberpeppers", new EcoCropBlock(VOLCANIC_SOIL,
            rule(LightPref.ANY, Climate.ANY, Need.HEAT_BOOST, 0.3f), () -> ModItems.EMBERPEPPER,
            crop(MapColor.RED).luminance(s -> s.get(EcoCropBlock.AGE) == 7 ? 5 : 0)));
    public static final Block FROST_TURNIPS = cropBlock("frost_turnips", new EcoCropBlock(FROZEN_SOIL,
            rule(LightPref.ANY, Climate.COLD, Need.NO_HEAT, 0.15f), () -> ModItems.FROST_TURNIP, crop(MapColor.WHITE)));
    public static final Block SUNBEANS = cropBlock("sunbeans", new EcoCropBlock(PLANT_SOIL,
            rule(LightPref.BRIGHT, Climate.ANY, Need.NONE, 0.28f), () -> ModItems.SUNBEAN, crop(MapColor.YELLOW)));
    public static final Block RELIC_SPROUT = cropBlock("relic_sprout", new EcoCropBlock(MINERAL_SOIL,
            rule(LightPref.ANY, Climate.ANY, Need.NONE, 0.04f), () -> ModItems.ANCIENT_SEED,
            crop(MapColor.TERRACOTTA_CYAN).luminance(s -> s.get(EcoCropBlock.AGE) >= 6 ? 9 : 2)));

    // ================= MINERALS =================
    static Block ore(String name, MapColor c, float hardness, int xpMin, int xpMax, int light, BlockSoundGroup sound) {
        AbstractBlock.Settings s = AbstractBlock.Settings.create().mapColor(c).strength(hardness, 3.0f).requiresTool().sounds(sound);
        if (light > 0) {
            s = s.luminance(st -> light);
        }
        return Reg.blockItem(name, new ExperienceDroppingBlock(UniformIntProvider.create(xpMin, xpMax), s));
    }

    static Block storage(String name, MapColor c, int light, BlockSoundGroup sound) {
        AbstractBlock.Settings s = AbstractBlock.Settings.create().mapColor(c).strength(4.0f, 6.0f).requiresTool().sounds(sound);
        if (light > 0) {
            s = s.luminance(st -> light);
        }
        return Reg.blockItem(name, new Block(s));
    }

    public static final Block CINNABAR_ORE = ore("cinnabar_ore", MapColor.DARK_RED, 3.0f, 1, 3, 0, BlockSoundGroup.BASALT);
    public static final Block SULFUR_ORE = ore("sulfur_ore", MapColor.YELLOW, 2.5f, 0, 2, 0, BlockSoundGroup.NETHERRACK);
    public static final Block NITER_ORE = ore("niter_ore", MapColor.WHITE_GRAY, 2.0f, 0, 2, 0, BlockSoundGroup.STONE);
    public static final Block EMBERITE_ORE = ore("emberite_ore", MapColor.ORANGE, 3.5f, 2, 5, 7, BlockSoundGroup.BASALT);
    public static final Block CRYOLITE_ORE = ore("cryolite_ore", MapColor.PALE_PURPLE, 3.0f, 1, 3, 0, BlockSoundGroup.GLASS);
    public static final Block MALACHITE_ORE = ore("malachite_ore", MapColor.EMERALD_GREEN, 3.0f, 1, 3, 0, BlockSoundGroup.STONE);
    public static final Block BOG_IRON_ORE = ore("bog_iron_ore", MapColor.BROWN, 2.0f, 0, 1, 0, BlockSoundGroup.MUD);
    public static final Block GALVANITE_ORE = ore("galvanite_ore", MapColor.BLUE, 3.5f, 2, 4, 5, BlockSoundGroup.DEEPSLATE);
    public static final Block PRISMATIC_OPAL_ORE = ore("prismatic_opal_ore", MapColor.WHITE, 4.0f, 4, 8, 6, BlockSoundGroup.CALCITE);
    public static final Block JASPER_ORE = ore("jasper_ore", MapColor.TERRACOTTA_RED, 3.0f, 1, 3, 0, BlockSoundGroup.STONE);
    public static final Block FOSSIL_ORE = ore("fossil_ore", MapColor.TERRACOTTA_WHITE, 2.0f, 0, 2, 0, BlockSoundGroup.BONE);
    public static final Block UMBRALITE_ORE = ore("umbralite_ore", MapColor.BLACK, 5.0f, 3, 7, 3, BlockSoundGroup.DEEPSLATE);
    public static final Block NACRE_ORE = ore("nacre_ore", MapColor.TERRACOTTA_WHITE, 2.5f, 1, 3, 0, BlockSoundGroup.CALCITE);
    public static final Block STARSILVER_ORE = ore("starsilver_ore", MapColor.WHITE_GRAY, 3.5f, 2, 4, 6, BlockSoundGroup.CALCITE);
    public static final Block GLIMMER_BUD = Reg.cutout(Reg.blockItem("glimmer_bud", new AmethystClusterBlock(3.0f, 4.0f,
            AbstractBlock.Settings.copy(Blocks.SMALL_AMETHYST_BUD).mapColor(MapColor.PALE_YELLOW).luminance(s -> 4))));
    public static final Block GLIMMER_CLUSTER = Reg.cutout(Reg.blockItem("glimmer_cluster", new AmethystClusterBlock(7.0f, 3.0f,
            AbstractBlock.Settings.copy(Blocks.AMETHYST_CLUSTER).mapColor(MapColor.PALE_YELLOW).luminance(s -> 9))));
    public static final Block BUDDING_GLIMMER = Reg.blockItem("budding_glimmer", new CrystalGrowerBlock(
            List.of(() -> ModPlants.GLIMMER_BUD, () -> ModPlants.GLIMMER_CLUSTER), 5,
            AbstractBlock.Settings.copy(Blocks.BUDDING_AMETHYST).mapColor(MapColor.PALE_YELLOW).luminance(s -> 3)));

    public static final Block OPAL_BLOCK = storage("prismatic_opal_block", MapColor.WHITE, 8, BlockSoundGroup.AMETHYST_BLOCK);
    public static final Block JASPER_BLOCK = storage("jasper_block", MapColor.TERRACOTTA_RED, 0, BlockSoundGroup.STONE);
    public static final Block MALACHITE_BLOCK = storage("malachite_block", MapColor.EMERALD_GREEN, 0, BlockSoundGroup.STONE);
    public static final Block CRYOLITE_BLOCK = storage("cryolite_block", MapColor.PALE_PURPLE, 0, BlockSoundGroup.GLASS);
    public static final Block GALVANITE_BLOCK = storage("galvanite_block", MapColor.BLUE, 4, BlockSoundGroup.METAL);
    public static final Block STARSILVER_BLOCK = storage("starsilver_block", MapColor.WHITE_GRAY, 0, BlockSoundGroup.METAL);
    public static final Block UMBRALITE_BLOCK = storage("umbralite_block", MapColor.BLACK, 0, BlockSoundGroup.NETHERITE);

    // ================= tree extras =================
    public static final Block RESIN_KNOT = Reg.cutout(Reg.blockItem("resin_knot", new ResinKnotBlock(() -> ModItems.AMBER_GLOB,
            AbstractBlock.Settings.create().mapColor(MapColor.ORANGE).strength(0.5f).sounds(BlockSoundGroup.HONEY)
                    .luminance(s -> s.get(ResinKnotBlock.AGE) == 2 ? 6 : 0).noCollision().pistonBehavior(PistonBehavior.DESTROY))));

    private static Block translucent(Block b) {
        Reg.CUTOUT.remove(b);
        Reg.TRANSLUCENT.add(b);
        return b;
    }

    private ModPlants() {
    }

    public static void init() {
        net.fabricmc.fabric.api.registry.FlammableBlockRegistry f = net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance();
        for (Block b : new Block[]{GLOWFERN, GOLDGRASS, MARSH_FERN, SILVERGRASS, DUSKBERRY_BUSH, HONEYFRUIT_BUSH, MENDLEAF, SUNLEAF, BOGBLOOM}) {
            f.add(b, 60, 100);
        }
        f.add(ROTTING_LOG, 5, 5);
    }
}
