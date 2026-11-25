package dev.underdeep.world.analysis;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Measures how well the open space of generated chunks is connected (ground truth, including all
 * features). Open = air or water (lava and solids block). Uses 6-connected flood fill over a bit grid.
 * <p>
 * Intended for diagnostics and automated tests only: memory is (2r*16)^2 * height bits
 * (r = 12 chunks: 384*384*384 bits = 7 MB) and it loads/generates every chunk in the area
 * synchronously.
 */
public final class ConnectivityAnalyzer {
    private ConnectivityAnalyzer() {
    }

    public record Result(int sizeX, int sizeZ, long openBlocks, long largestComponent, int components,
                         double largestFraction, int componentsOver1000, List<Long> topSizes,
                         List<BitSet> mainMasks, int originX, int originZ, int minY, int sizeY,
                         int mainNetworks, double mainFraction) {
        /**
         * Index of the main network (component holding >= 10% of the open volume) containing the
         * position, or -1 if the position is solid or in a minor pocket.
         */
        public int mainIndex(int x, int y, int z) {
            int lx = x - originX;
            int lz = z - originZ;
            int ly = y - minY;
            if (lx < 0 || lz < 0 || ly < 0 || lx >= sizeX || lz >= sizeZ || ly >= sizeY) {
                return -1;
            }
            int i = idx(lx, ly, lz, sizeX, sizeY);
            for (int k = 0; k < mainMasks.size(); k++) {
                if (mainMasks.get(k).get(i)) {
                    return k;
                }
            }
            return -1;
        }

        public String summary() {
            return String.format(java.util.Locale.ROOT, "area %dx%d: open=%d, components=%d (>=1000 blocks: %d), largest=%d (%.1f%% of open), main networks=%d holding %.1f%%",
                    sizeX, sizeZ, openBlocks, components, componentsOver1000, largestComponent, largestFraction * 100,
                    mainNetworks, mainFraction * 100);
        }
    }

    public static Result analyze(ServerWorld world, int centerChunkX, int centerChunkZ, int radiusChunks, int minY, int maxY) {
        int cx0 = centerChunkX - radiusChunks;
        int cz0 = centerChunkZ - radiusChunks;
        int sx = (radiusChunks * 2 + 1) * 16;
        int sz = sx;
        int sy = maxY - minY + 1;
        BitSet open = new BitSet(sx * sy * sz);
        BlockPos.Mutable m = new BlockPos.Mutable();
        long openCount = 0;
        for (int cx = 0; cx < radiusChunks * 2 + 1; cx++) {
            for (int cz = 0; cz < radiusChunks * 2 + 1; cz++) {
                Chunk chunk = world.getChunk(cx0 + cx, cz0 + cz, ChunkStatus.FULL, true);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int x = cx * 16 + lx;
                        int z = cz * 16 + lz;
                        for (int y = minY; y <= maxY; y++) {
                            BlockState s = chunk.getBlockState(m.set((cx0 + cx) * 16 + lx, y, (cz0 + cz) * 16 + lz));
                            boolean o = s.isAir() || (s.getFluidState().isOf(Fluids.WATER) || s.getFluidState().isOf(Fluids.FLOWING_WATER))
                                    || s.getCollisionShape(chunk, m).isEmpty() && s.getFluidState().isEmpty();
                            if (o) {
                                open.set(idx(x, y - minY, z, sx, sy));
                                openCount++;
                            }
                        }
                    }
                }
            }
        }
        BitSet visited = new BitSet(sx * sy * sz);
        int[] stack = new int[1 << 20];
        long largest = 0;
        int largestStart = -1;
        List<Integer> mainStarts = new ArrayList<>();
        List<Long> mainSizes = new ArrayList<>();
        int components = 0;
        int big = 0;
        List<Long> sizes = new ArrayList<>();
        for (int start = open.nextSetBit(0); start >= 0; start = open.nextSetBit(start + 1)) {
            if (visited.get(start)) {
                continue;
            }
            components++;
            long size = 0;
            int sp = 0;
            stack[sp++] = start;
            visited.set(start);
            while (sp > 0) {
                int cur = stack[--sp];
                size++;
                int y = cur % sy;
                int rest = cur / sy;
                int z = rest % sz;
                int x = rest / sz;
                int[] nb = {x > 0 ? cur - sy * sz : -1, x < sx - 1 ? cur + sy * sz : -1,
                        z > 0 ? cur - sy : -1, z < sz - 1 ? cur + sy : -1,
                        y > 0 ? cur - 1 : -1, y < sy - 1 ? cur + 1 : -1};
                for (int n : nb) {
                    if (n >= 0 && open.get(n) && !visited.get(n)) {
                        visited.set(n);
                        if (sp >= stack.length) {
                            stack = java.util.Arrays.copyOf(stack, stack.length * 2);
                        }
                        stack[sp++] = n;
                    }
                }
            }
            if (size >= 1000) {
                big++;
                sizes.add(size);
            }
            if (size > largest) {
                largest = size;
                largestStart = start;
            }
            if (size >= 10000) {
                mainStarts.add(start);
                mainSizes.add(size);
            }
        }
        // Re-flood every main network (>= 10% of open volume) into one mask.
        List<BitSet> masks = new ArrayList<>();
        int mainNetworks = 0;
        long mainVolume = 0;
        for (int i = 0; i < mainStarts.size(); i++) {
            if (mainSizes.get(i) < openCount * 0.10) {
                continue;
            }
            mainNetworks++;
            mainVolume += mainSizes.get(i);
            BitSet mask = new BitSet(sx * sy * sz);
            masks.add(mask);
            int sp = 0;
            int startIdx = mainStarts.get(i);
            stack[sp++] = startIdx;
            mask.set(startIdx);
            while (sp > 0) {
                int cur = stack[--sp];
                int y = cur % sy;
                int rest = cur / sy;
                int z = rest % sz;
                int x = rest / sz;
                int[] nb = {x > 0 ? cur - sy * sz : -1, x < sx - 1 ? cur + sy * sz : -1,
                        z > 0 ? cur - sy : -1, z < sz - 1 ? cur + sy : -1,
                        y > 0 ? cur - 1 : -1, y < sy - 1 ? cur + 1 : -1};
                for (int n : nb) {
                    if (n >= 0 && open.get(n) && !mask.get(n)) {
                        mask.set(n);
                        if (sp >= stack.length) {
                            stack = java.util.Arrays.copyOf(stack, stack.length * 2);
                        }
                        stack[sp++] = n;
                    }
                }
            }
        }
        sizes.sort((a, b) -> Long.compare(b, a));
        if (sizes.size() > 8) {
            sizes = new ArrayList<>(sizes.subList(0, 8));
        }
        return new Result(sx, sz, openCount, largest, components, openCount > 0 ? largest / (double) openCount : 0, big, sizes,
                masks, cx0 * 16, cz0 * 16, minY, sy, mainNetworks, openCount > 0 ? mainVolume / (double) openCount : 0);
    }

    private static int idx(int x, int y, int z, int sx, int sy) {
        return (x * sx + z) * sy + y;
    }
}
