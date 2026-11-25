package dev.underdeep.block.plant;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.function.Supplier;

/**
 * Decaying fallen wood. Over time it sprouts bracket fungi on its sides and small mushrooms on top
 * (at most three fungi around one log block): fallen organic matter feeding fungal growth.
 */
public class RottingLogBlock extends PillarBlock {
    private final Supplier<Block> shelf;
    private final List<Supplier<Block>> toppers;

    public RottingLogBlock(Supplier<Block> shelf, List<Supplier<Block>> toppers, Settings settings) {
        super(settings);
        this.shelf = shelf;
        this.toppers = toppers;
    }

    @Override
    protected boolean hasRandomTicks(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!Env.spreadingEnabled(world) || !Env.roll(random, 0.08) || world.getLightLevel(pos.up()) > 12) {
            return;
        }
        int fungi = 0;
        for (Direction d : Direction.values()) {
            Block b = world.getBlockState(pos.offset(d)).getBlock();
            if (b == shelf.get() || toppers.stream().anyMatch(t -> t.get() == b)) {
                fungi++;
            }
        }
        if (fungi >= 3) {
            return;
        }
        Direction d = Direction.random(random);
        BlockPos t = pos.offset(d);
        if (!world.getBlockState(t).isAir()) {
            return;
        }
        BlockState place = null;
        if (d.getAxis().isHorizontal()) {
            place = shelf.get().getDefaultState().with(HorizontalFacingBlock.FACING, d);
        } else if (d == Direction.UP && !toppers.isEmpty()) {
            place = toppers.get(random.nextInt(toppers.size())).get().getDefaultState();
        }
        if (place != null && place.canPlaceAt(world, t)) {
            world.setBlockState(t, place, Block.NOTIFY_ALL);
        }
    }
}
