package dev.underdeep.block.plant;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractPlantBlock;
import net.minecraft.block.AbstractPlantStemBlock;
import net.minecraft.block.Block;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

import java.util.function.Supplier;

/** Body segment of a downward-hanging vine/root. */
public class HangingBodyBlock extends AbstractPlantBlock {
    private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 16, 15);
    private final Supplier<Block> stem;

    public HangingBodyBlock(Supplier<Block> stem, Settings settings) {
        super(settings, Direction.DOWN, SHAPE, false);
        this.stem = stem;
    }

    @Override
    protected MapCodec<? extends AbstractPlantBlock> getCodec() {
        return MapCodec.unit(this);
    }

    @Override
    protected AbstractPlantStemBlock getStem() {
        return (AbstractPlantStemBlock) stem.get();
    }
}
