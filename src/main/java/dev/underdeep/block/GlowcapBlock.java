package dev.underdeep.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Glowcap: a small bioluminescent fungus that grows on any sturdy surface. A natural light source
 * for cavern floors (Stage 1 foundational block; ecological role defined in Stage 2).
 */
public final class GlowcapBlock extends PlantBlock {
    public static final MapCodec<GlowcapBlock> CODEC = createCodec(GlowcapBlock::new);
    private static final VoxelShape SHAPE = PlantBlock.createCuboidShape(4.0, 0.0, 4.0, 12.0, 9.0, 12.0);

    public GlowcapBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends PlantBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
        return floor.isSideSolidFullSquare(world, pos, Direction.UP);
    }
}
