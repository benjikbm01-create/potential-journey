package dev.underdeep.world.feature;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Plans a large organism (tree or giant fungus) block by block, then commits it safely:
 * <ul>
 *   <li>the "core" (lower trunk) must be free, otherwise nothing is placed;</li>
 *   <li>wood and foliage only ever replace air, water, replaceable plants and leaves, so growing
 *       saplings never overwrite player blocks or terrain;</li>
 *   <li>leaf {@code distance} is computed like vanilla (BFS from logs) so leaves decay naturally
 *       when the tree is cut, and leaves farther than 6 from wood are made persistent.</li>
 * </ul>
 */
public final class PlantBuilder {
    private final StructureWorldAccess world;
    private final BlockPos origin;
    private final Map<BlockPos, BlockState> wood = new LinkedHashMap<>();
    private final Map<BlockPos, BlockState> foliage = new LinkedHashMap<>();
    private final Set<BlockPos> core = new LinkedHashSet<>();
    private final int maxRadius;

    public PlantBuilder(StructureWorldAccess world, BlockPos origin, int maxRadius) {
        this.world = world;
        this.origin = origin;
        this.maxRadius = maxRadius;
    }

    private boolean inBounds(BlockPos p) {
        return Math.abs(p.getX() - origin.getX()) <= maxRadius && Math.abs(p.getZ() - origin.getZ()) <= maxRadius;
    }

    public void wood(BlockPos p, BlockState s) {
        if (inBounds(p)) {
            wood.put(p.toImmutable(), s);
            foliage.remove(p);
        }
    }

    public void core(BlockPos p, BlockState s) {
        wood(p, s);
        core.add(p.toImmutable());
    }

    public void leaf(BlockPos p, BlockState s) {
        if (inBounds(p) && !wood.containsKey(p)) {
            foliage.putIfAbsent(p.toImmutable(), s);
        }
    }

    /** Wood line from a to b (inclusive) with the axis of the dominant direction. */
    public void line(BlockPos a, BlockPos b, BlockState log, boolean isCore) {
        int dx = b.getX() - a.getX(), dy = b.getY() - a.getY(), dz = b.getZ() - a.getZ();
        int n = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        Direction.Axis axis = Math.abs(dy) >= Math.max(Math.abs(dx), Math.abs(dz)) ? Direction.Axis.Y
                : Math.abs(dx) >= Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z;
        BlockState s = log.contains(PillarBlock.AXIS) ? log.with(PillarBlock.AXIS, axis) : log;
        BlockPos prev = null;
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : (double) i / n;
            BlockPos p = BlockPos.ofFloored(a.getX() + dx * t + 0.5, a.getY() + dy * t + 0.5, a.getZ() + dz * t + 0.5);
            // keep diagonal steps face-connected
            if (prev != null && prev.getManhattanDistance(p) > 1) {
                BlockPos bridge = new BlockPos(p.getX(), prev.getY(), prev.getZ());
                if (isCore) core(bridge, s); else wood(bridge, s);
            }
            if (isCore) core(p, s); else wood(p, s);
            prev = p;
        }
    }

    /** Ellipsoid of foliage with ragged edges. */
    public void blob(BlockPos c, double rx, double ry, double rz, double density, BlockState leaves, Random r) {
        int ix = (int) Math.ceil(rx), iy = (int) Math.ceil(ry), iz = (int) Math.ceil(rz);
        for (int x = -ix; x <= ix; x++) {
            for (int y = -iy; y <= iy; y++) {
                for (int z = -iz; z <= iz; z++) {
                    double d = (x * x) / (rx * rx) + (y * y) / (ry * ry) + (z * z) / (rz * rz);
                    if (d <= 1.0 && (d < 0.55 || r.nextDouble() < density)) {
                        leaf(c.add(x, y, z), leaves);
                    }
                }
            }
        }
    }

    /** Flat disc of foliage (one layer). */
    public void disc(BlockPos c, double radius, double density, BlockState leaves, Random r) {
        int ir = (int) Math.ceil(radius);
        for (int x = -ir; x <= ir; x++) {
            for (int z = -ir; z <= ir; z++) {
                double d = Math.sqrt(x * x + z * z) / radius;
                if (d <= 1.0 && (d < 0.7 || r.nextDouble() < density)) {
                    leaf(c.add(x, 0, z), leaves);
                }
            }
        }
    }

    /** A hanging column of foliage under p (curtains), length up to len, stopping at obstacles. */
    public void hang(BlockPos p, int len, BlockState leaves) {
        for (int i = 1; i <= len; i++) {
            leaf(p.down(i), leaves);
        }
    }

    public Set<BlockPos> foliagePositions() {
        return foliage.keySet();
    }

    private static boolean replaceable(BlockState s) {
        return s.isAir() || s.isReplaceable() || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.REPLACEABLE_BY_TREES)
                || s.isIn(BlockTags.SAPLINGS) || s.isOf(Blocks.SNOW) || s.isOf(Blocks.VINE);
    }

    /** @return false (and places nothing) when the core is obstructed. */
    public boolean commit() {
        for (BlockPos p : core) {
            BlockState s = world.getBlockState(p);
            if (!replaceable(s) || s.getFluidState().isIn(FluidTags.LAVA)) {
                return false;
            }
        }
        Set<BlockPos> placedWood = new LinkedHashSet<>();
        for (Map.Entry<BlockPos, BlockState> e : wood.entrySet()) {
            BlockState existing = world.getBlockState(e.getKey());
            if (replaceable(existing) && !existing.getFluidState().isIn(FluidTags.LAVA)) {
                world.setBlockState(e.getKey(), e.getValue(), Block.NOTIFY_ALL | Block.FORCE_STATE);
                placedWood.add(e.getKey());
            }
        }
        Map<BlockPos, BlockState> placedLeaves = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : foliage.entrySet()) {
            BlockState existing = world.getBlockState(e.getKey());
            if ((existing.isAir() || existing.isReplaceable() || existing.isOf(Blocks.WATER)) && !existing.isIn(BlockTags.LEAVES)
                    && !existing.getFluidState().isIn(FluidTags.LAVA)) {
                BlockState s = e.getValue();
                if (s.contains(Properties.WATERLOGGED)) {
                    s = s.with(Properties.WATERLOGGED, existing.getFluidState().isIn(FluidTags.WATER) && existing.getFluidState().isStill());
                }
                placedLeaves.put(e.getKey(), s);
            }
        }
        // leaf distances (BFS from wood through planned leaves)
        Map<BlockPos, Integer> dist = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos w : placedWood) {
            for (Direction d : Direction.values()) {
                BlockPos n = w.offset(d);
                if (placedLeaves.containsKey(n) && !dist.containsKey(n)) {
                    dist.put(n, 1);
                    queue.add(n);
                }
            }
        }
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            int dp = dist.get(p);
            if (dp >= 6) {
                continue;
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.offset(d);
                if (placedLeaves.containsKey(n) && !dist.containsKey(n)) {
                    dist.put(n, dp + 1);
                    queue.add(n);
                }
            }
        }
        for (Map.Entry<BlockPos, BlockState> e : placedLeaves.entrySet()) {
            BlockState s = e.getValue();
            if (s.getBlock() instanceof LeavesBlock) {
                Integer d = dist.get(e.getKey());
                s = d == null ? s.with(LeavesBlock.PERSISTENT, true).with(LeavesBlock.DISTANCE, 7)
                        : s.with(LeavesBlock.DISTANCE, d).with(LeavesBlock.PERSISTENT, false);
            }
            world.setBlockState(e.getKey(), s, Block.NOTIFY_ALL | Block.FORCE_STATE);
        }
        return true;
    }
}
