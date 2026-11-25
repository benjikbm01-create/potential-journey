package dev.underdeep.block.plant;

import dev.underdeep.config.UnderdeepConfig;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.function.Supplier;

/**
 * Mineral that slowly grows crystals on its exposed faces (budding block). Growth requires dim light
 * at the face (crystals regrow in the dark). Stages are supplied in order, e.g. bud then cluster.
 * The crystals themselves never spread: only budding blocks regrow them, at a fixed slow rate.
 */
public class CrystalGrowerBlock extends Block {
    private final List<Supplier<Block>> stages;
    private final int oneIn;

    public CrystalGrowerBlock(List<Supplier<Block>> stages, int oneIn, Settings settings) {
        super(settings);
        this.stages = stages;
        this.oneIn = oneIn;
    }

    @Override
    protected boolean hasRandomTicks(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!UnderdeepConfig.get().crystalRegrowth || random.nextInt(oneIn) != 0) {
            return;
        }
        Direction d = Direction.random(random);
        BlockPos t = pos.offset(d);
        BlockState ts = world.getBlockState(t);
        if (world.getLightLevel(t) > 11 && !ts.isOf(stages.get(0).get())) {
            return;
        }
        BlockState next = null;
        if (ts.isAir() || (ts.isOf(Blocks.WATER) && ts.getFluidState().getLevel() == 8)) {
            next = stages.get(0).get().getDefaultState();
        } else {
            for (int i = 0; i < stages.size() - 1; i++) {
                if (ts.isOf(stages.get(i).get()) && ts.get(AmethystClusterBlock.FACING) == d) {
                    next = stages.get(i + 1).get().getDefaultState();
                }
            }
        }
        if (next != null) {
            world.setBlockState(t, next.with(AmethystClusterBlock.FACING, d)
                    .with(Properties.WATERLOGGED, ts.getFluidState().isOf(net.minecraft.fluid.Fluids.WATER)), Block.NOTIFY_ALL);
        }
    }
}
