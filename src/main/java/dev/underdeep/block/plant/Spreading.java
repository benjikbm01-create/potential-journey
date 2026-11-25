package dev.underdeep.block.plant;

import dev.underdeep.config.UnderdeepConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.function.BiPredicate;

/**
 * Bounded natural spreading shared by all spreading organisms.
 * <ul>
 *   <li>Only places into a free target (air, or still water for aquatic species) chosen by the caller's predicate;
 *       never replaces existing blocks.</li>
 *   <li>Stops when the neighbourhood already holds {@code UnderdeepConfig#spreadDensityLimit} (or the species' own cap)
 *       blocks of the same species: growth is linear and density-capped, never exponential.</li>
 *   <li>Never places within 2 blocks of player-made blocks ({@code underdeep:growth_blockers}).</li>
 *   <li>Only targets loaded positions inside the same or adjacent chunks (radius ≤ 4).</li>
 * </ul>
 */
public final class Spreading {
    private Spreading() {
    }

    /**
     * @param cap    maximum same-block count within the (2*radius+1)^2 x 3 box; 0 uses the config limit
     * @param target predicate on (world, candidate) deciding whether the new state may stand there
     * @return true if a block was placed
     */
    public static boolean trySpread(ServerWorld world, BlockPos origin, BlockState newState, int radius, int cap,
                                    Random random, BiPredicate<ServerWorld, BlockPos> target) {
        int r = Math.min(4, Math.max(1, radius));
        int limit = cap > 0 ? Math.min(cap, UnderdeepConfig.get().spreadDensityLimit + cap / 2)
                : UnderdeepConfig.get().spreadDensityLimit;
        BlockPos p = origin.add(random.nextInt(2 * r + 1) - r, random.nextInt(3) - 1, random.nextInt(2 * r + 1) - r);
        if (p.equals(origin) || !world.isChunkLoaded(p) || !target.test(world, p)) {
            return false;
        }
        Block block = newState.getBlock();
        if (Env.count(world, origin, r, 1, s -> s.isOf(block)) >= limit) {
            return false;
        }
        if (Env.protectedArea(world, p)) {
            return false;
        }
        world.setBlockState(p, newState, Block.NOTIFY_ALL);
        return true;
    }

    /** Standard land target: air with the state able to stand there. */
    public static BiPredicate<ServerWorld, BlockPos> land(BlockState state) {
        return (w, p) -> w.getBlockState(p).isAir() && state.canPlaceAt(w, p);
    }

    /** Submerged target: still water source with the state able to stand there. */
    public static BiPredicate<ServerWorld, BlockPos> water(BlockState state) {
        return (w, p) -> {
            BlockState s = w.getBlockState(p);
            return s.isOf(net.minecraft.block.Blocks.WATER) && s.getFluidState().isStill() && state.canPlaceAt(w, p);
        };
    }
}
