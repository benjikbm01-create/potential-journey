package dev.underdeep.registry;

import dev.underdeep.Underdeep;
import dev.underdeep.block.GlowcapBlock;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.Block;
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
        });
    }
}
