package dev.underdeep.block.plant;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractPlantStemBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.VineLogic;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;

import java.util.function.Supplier;

/** Tip of a downward-hanging vine/root (weeping-vine family). Length is capped by the vanilla max age. */
public class HangingStemBlock extends AbstractPlantStemBlock {
    private static final VoxelShape SHAPE = Block.createCuboidShape(4, 9, 4, 12, 16, 12);
    private final Supplier<Block> body;

    public HangingStemBlock(Supplier<Block> body, Settings settings) {
        super(settings, Direction.DOWN, SHAPE, false, 0.06);
        this.body = body;
    }

    @Override
    protected MapCodec<? extends AbstractPlantStemBlock> getCodec() {
        return MapCodec.unit(this);
    }

    @Override
    protected int getGrowthLength(Random random) {
        return VineLogic.getGrowthLength(random);
    }

    @Override
    protected Block getPlant() {
        return body.get();
    }

    @Override
    protected boolean chooseStemState(BlockState state) {
        return VineLogic.isValidForWeepingStem(state);
    }
}
