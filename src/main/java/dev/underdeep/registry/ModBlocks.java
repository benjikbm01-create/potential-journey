package dev.underdeep.registry;

import dev.underdeep.Underdeep;
import dev.underdeep.block.GlowcapBlock;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SeagrassBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * Foundational Underdeep blocks (Stage 1). See docs/CONTENT_REGISTRY.md.
 */
public final class ModBlocks {
    public static final Block LUMINITE_BLOCK = register("luminite_block", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.CYAN).strength(1.5f).sounds(BlockSoundGroup.AMETHYST_BLOCK).requiresTool()
            .luminance(s -> 12).instrument(NoteBlockInstrument.CHIME)));
    public static final Block LUMINITE_CLUSTER = register("luminite_cluster", new AmethystClusterBlock(7.0f, 3.0f,
            AbstractBlock.Settings.create().mapColor(MapColor.CYAN).solid().nonOpaque().ticksRandomly()
                    .sounds(BlockSoundGroup.AMETHYST_CLUSTER).strength(1.5f).luminance(s -> 9)
                    .pistonBehavior(PistonBehavior.DESTROY)));
    public static final Block GLOWCAP = register("glowcap", new GlowcapBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.LIGHT_BLUE).noCollision().breakInstantly().sounds(BlockSoundGroup.FUNGUS)
            .luminance(s -> 10).postProcess((s, w, p) -> true).pistonBehavior(PistonBehavior.DESTROY)));

    // ---- Stage 1.5: natural light and ecosystem foundation blocks ----
    public static final Block GLOWMOSS = register("glowmoss", new Block(AbstractBlock.Settings.copy(Blocks.MOSS_BLOCK)
            .mapColor(MapColor.LIME).luminance(s -> 13)));
    public static final Block AMBER_MOSS = register("amber_moss", new Block(AbstractBlock.Settings.copy(Blocks.MOSS_BLOCK)
            .mapColor(MapColor.GOLD).luminance(s -> 13)));
    public static final Block STARMOSS = register("starmoss", new Block(AbstractBlock.Settings.copy(Blocks.MOSS_BLOCK)
            .mapColor(MapColor.LIGHT_BLUE).luminance(s -> 13)));
    public static final Block SPOREGLOW_SOIL = register("sporeglow_soil", new Block(AbstractBlock.Settings.copy(Blocks.MYCELIUM)
            .mapColor(MapColor.CYAN).luminance(s -> 12)));
    public static final Block EMBER_NYLIUM = register("ember_nylium", new Block(AbstractBlock.Settings.copy(Blocks.NETHERRACK)
            .mapColor(MapColor.DARK_RED).sounds(BlockSoundGroup.NYLIUM).luminance(s -> 9)));
    public static final Block GOLDEN_LEAVES = register("golden_leaves", new LeavesBlock(AbstractBlock.Settings.copy(Blocks.OAK_LEAVES)
            .mapColor(MapColor.GOLD).luminance(s -> 9)));
    public static final Block STARBLOOM = register("starbloom", new GlowcapBlock(plant(MapColor.LIGHT_BLUE, 12)));
    public static final Block EMBERBLOOM = register("emberbloom", new GlowcapBlock(plant(MapColor.RED, 10)));
    public static final Block AMBERBLOOM = register("amberbloom", new GlowcapBlock(plant(MapColor.YELLOW, 11)));
    public static final Block GLOWREED = register("glowreed", new TallPlantBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.LIME).replaceable().noCollision().breakInstantly().sounds(BlockSoundGroup.GRASS)
            .luminance(s -> 11).offset(AbstractBlock.OffsetType.XZ).burnable().pistonBehavior(PistonBehavior.DESTROY)));
    public static final Block LUMEN_SEAGRASS = register("lumen_seagrass", new SeagrassBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.CYAN).replaceable().noCollision().breakInstantly().sounds(BlockSoundGroup.WET_GRASS)
            .luminance(s -> 10).pistonBehavior(PistonBehavior.DESTROY)));
    public static final Block STARSTONE = register("starstone", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.WHITE).strength(1.5f).requiresTool().sounds(BlockSoundGroup.CALCITE).luminance(s -> 15)));
    public static final Block STORMSTONE = register("stormstone", new Block(AbstractBlock.Settings.copy(Blocks.DEEPSLATE)
            .mapColor(MapColor.BLUE)));
    public static final Block CHARGED_STORMSTONE = register("charged_stormstone", new Block(AbstractBlock.Settings.copy(Blocks.DEEPSLATE)
            .mapColor(MapColor.PURPLE).luminance(s -> 8)));
    public static final Block STORM_CRYSTAL = register("storm_crystal", new AmethystClusterBlock(7.0f, 3.0f,
            AbstractBlock.Settings.create().mapColor(MapColor.PURPLE).solid().nonOpaque().sounds(BlockSoundGroup.AMETHYST_CLUSTER)
                    .strength(1.5f).luminance(s -> 10).pistonBehavior(PistonBehavior.DESTROY)));
    public static final Block RIME_CRYSTAL = register("rime_crystal", new AmethystClusterBlock(7.0f, 3.0f,
            AbstractBlock.Settings.create().mapColor(MapColor.PALE_PURPLE).solid().nonOpaque().sounds(BlockSoundGroup.GLASS)
                    .strength(1.0f).luminance(s -> 10).pistonBehavior(PistonBehavior.DESTROY)));
    public static final Block PETRIFIED_WOOD = register("petrified_wood", new PillarBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.TERRACOTTA_BROWN).strength(2.0f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block AMBER_RESIN = register("amber_resin", new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.ORANGE).strength(1.0f).sounds(BlockSoundGroup.HONEY).luminance(s -> 10)));

    private static AbstractBlock.Settings plant(MapColor color, int light) {
        return AbstractBlock.Settings.create().mapColor(color).noCollision().breakInstantly().sounds(BlockSoundGroup.GRASS)
                .luminance(s -> light).offset(AbstractBlock.OffsetType.XZ).pistonBehavior(PistonBehavior.DESTROY);
    }

    private ModBlocks() {
    }

    private static Block register(String name, Block block) {
        Identifier id = Identifier.of(Underdeep.MOD_ID, name);
        Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return block;
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
            entries.add(LUMINITE_BLOCK);
            entries.add(LUMINITE_CLUSTER);
            entries.add(GLOWCAP);
            for (Block b : new Block[]{GLOWMOSS, AMBER_MOSS, STARMOSS, SPOREGLOW_SOIL, EMBER_NYLIUM, GOLDEN_LEAVES, STARBLOOM,
                    EMBERBLOOM, AMBERBLOOM, GLOWREED, LUMEN_SEAGRASS, STARSTONE, STORMSTONE, CHARGED_STORMSTONE, STORM_CRYSTAL,
                    RIME_CRYSTAL, PETRIFIED_WOOD, AMBER_RESIN}) {
                entries.add(b);
            }
        });
    }
}
