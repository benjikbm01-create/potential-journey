package dev.underdeep.mixin;

import net.minecraft.block.Block;
import net.minecraft.item.AxeItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Stage 8 audit: which logs an axe strips into what. */
@Mixin(AxeItem.class)
public interface AxeItemAccessor {
    @Accessor("STRIPPED_BLOCKS")
    static Map<Block, Block> underdeep$stripped() {
        throw new AssertionError();
    }
}
