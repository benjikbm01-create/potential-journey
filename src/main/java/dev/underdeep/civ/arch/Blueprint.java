package dev.underdeep.civ.arch;

import net.minecraft.block.BlockState;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A building design in local coordinates: x across the front, z from front (z = 0) to back, y up from the
 * ground floor (y = 0 is the floor level the entrance opens onto; negative y is foundation). The entrance
 * faces north (-z) in local space; {@link #transform} rotates designs to face their road.
 * <p>
 * Blueprints are immutable once built and are shared between threads by the settlement generator.
 */
public final class Blueprint {
    /** Points of interest used by inhabitants and validation. */
    public enum MarkerType { ENTRANCE, BED, WORKSTATION, STORAGE, MERCHANT, SEAT, LIGHT, GUARD, FARM, DOCK, GATHER }

    public record Marker(MarkerType type, BlockPos pos, String tag) {
    }

    final Map<Long, BlockState> blocks = new HashMap<>();
    final List<Marker> markers = new ArrayList<>();
    /** Footprint size and height (excluding foundation). */
    public final int width, depth;
    int height;
    /** Local foundation style: how deep supports may go below y = 0, and whether supports are pillars only. */
    public boolean stilts;
    public String id = "";
    public String kind = "";
    /** Blocks under the footprint that must be solid ground or foundation (foundation material). */
    BlockState foundation;

    Blueprint(int width, int depth) {
        this.width = width;
        this.depth = depth;
    }

    static long key(int x, int y, int z) {
        return ((long) (x + 512) << 42) | ((long) (y + 512) << 21) | (z + 512);
    }

    static int kx(long k) {
        return (int) (k >>> 42) - 512;
    }

    static int ky(long k) {
        return (int) ((k >>> 21) & 0x1FFFFF) - 512;
    }

    static int kz(long k) {
        return (int) (k & 0x1FFFFF) - 512;
    }

    public int height() {
        return height;
    }

    public int blockCount() {
        return blocks.size();
    }

    public BlockState foundation() {
        return foundation;
    }

    public List<Marker> markers() {
        return Collections.unmodifiableList(markers);
    }

    public BlockState get(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    /** Visits every block in local coordinates. */
    public void forEach(BiConsumer<BlockPos, BlockState> visitor) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (Map.Entry<Long, BlockState> e : blocks.entrySet()) {
            long k = e.getKey();
            p.set(kx(k), ky(k), kz(k));
            visitor.accept(p, e.getValue());
        }
    }

    /** Rotated footprint width along world x. */
    public int rotatedWidth(BlockRotation rot) {
        return rot == BlockRotation.CLOCKWISE_90 || rot == BlockRotation.COUNTERCLOCKWISE_90 ? depth : width;
    }

    public int rotatedDepth(BlockRotation rot) {
        return rot == BlockRotation.CLOCKWISE_90 || rot == BlockRotation.COUNTERCLOCKWISE_90 ? width : depth;
    }

    /**
     * Local → world offset (relative to the footprint's minimum corner in world space) for a rotation.
     * The rotated footprint always occupies [0, rotatedWidth) x [0, rotatedDepth).
     */
    public BlockPos transform(int x, int y, int z, BlockRotation rot) {
        return switch (rot) {
            case NONE -> new BlockPos(x, y, z);
            case CLOCKWISE_90 -> new BlockPos(depth - 1 - z, y, x);
            case CLOCKWISE_180 -> new BlockPos(width - 1 - x, y, depth - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, width - 1 - x);
        };
    }
}
