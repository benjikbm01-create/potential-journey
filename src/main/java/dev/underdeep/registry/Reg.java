package dev.underdeep.registry;

import dev.underdeep.Underdeep;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Registration helpers and client render-layer bookkeeping for Stage 2 content. */
public final class Reg {
    /** Blocks rendered with the cutout layer (plants, crops, doors, saplings...). */
    public static final List<Block> CUTOUT = new ArrayList<>();
    /** Blocks rendered with cutout-mipped (leaves). */
    public static final List<Block> CUTOUT_MIPPED = new ArrayList<>();
    /** Blocks rendered translucent (crystalline plants). */
    public static final List<Block> TRANSLUCENT = new ArrayList<>();
    /** Creative tab order. */
    public static final List<ItemConvertible> TAB = new ArrayList<>();

    private Reg() {
    }

    public static Identifier id(String path) {
        return Identifier.of(Underdeep.MOD_ID, path);
    }

    /** Registers a block without an item. */
    public static <T extends Block> T block(String name, T block) {
        return Registry.register(Registries.BLOCK, id(name), block);
    }

    /** Registers a block with a plain block item (added to the Underdeep tab). */
    public static <T extends Block> T blockItem(String name, T block) {
        block(name, block);
        TAB.add(Registry.register(Registries.ITEM, id(name), new BlockItem(block, new Item.Settings())));
        return block;
    }

    public static <T extends Block> T blockItem(String name, T block, Item.Settings settings) {
        block(name, block);
        TAB.add(Registry.register(Registries.ITEM, id(name), new BlockItem(block, settings)));
        return block;
    }

    public static <T extends Item> T item(String name, T item) {
        TAB.add(Registry.register(Registries.ITEM, id(name), item));
        return item;
    }

    public static <T extends Block> T cutout(T block) {
        CUTOUT.add(block);
        return block;
    }
}
