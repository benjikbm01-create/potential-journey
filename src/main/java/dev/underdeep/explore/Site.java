package dev.underdeep.explore;

import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

/**
 * One exploration site: a deterministic placement of a {@link ExploreContent.Family} (pure function of the world seed
 * and the terrain model). {@link #id} is stable and packs the layer and grid cell (persistence key, append-only
 * layout: bits 0–27 cell x, 28–55 cell z, 56–59 layer ordinal).
 *
 * @param x      footprint centre x
 * @param z      footprint centre z
 * @param y      floor level (blueprint y = 0 is written at this height)
 * @param radius half extent of the footprint (the canvas is (2r+1)²)
 */
public record Site(long id, ExploreContent.Family family, CavernLayer layer, RegionType region, int x, int y, int z, int radius,
                   BlockRotation rotation, long seed) {

    public static long packId(CavernLayer layer, int cx, int cz) {
        return ((long) layer.ordinal() << 56) | ((long) (cz & 0xFFFFFFF) << 28) | (cx & 0xFFFFFFF);
    }

    public static int cellX(long id) {
        return (int) (id << 36 >> 36);
    }

    public static int cellZ(long id) {
        return (int) ((id >>> 28) << 36 >> 36);
    }

    public static CavernLayer layerOf(long id) {
        return CavernLayer.values()[(int) (id >>> 56) & 0xF];
    }

    /** Minimum corner of the (unrotated, square) footprint at floor level. */
    public BlockPos origin() {
        return new BlockPos(x - radius, y, z - radius);
    }

    public int size() {
        return radius * 2 + 1;
    }

    public boolean containsXZ(int px, int pz, int margin) {
        return Math.abs(px - x) <= radius + margin && Math.abs(pz - z) <= radius + margin;
    }

    public double dist(double px, double pz) {
        return Math.hypot(px - x, pz - z);
    }

    public String key() {
        return Long.toString(id, 36);
    }
}
