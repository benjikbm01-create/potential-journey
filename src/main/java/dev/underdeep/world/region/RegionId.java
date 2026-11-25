package dev.underdeep.world.region;

import dev.underdeep.world.terrain.CavernLayer;

/**
 * Stable identifier of one region instance (one Voronoi cell of one layer's region map).
 * <p>
 * The packed {@link #asLong()} form is a persistence contract for future regional simulation
 * (wildlife populations, settlements, rumours): it depends only on the layer and cell indices,
 * which are a pure function of world coordinates and never change for a given world.
 * Layout: [4 bits layer][30 bits cellX][30 bits cellZ] (cells are signed 30-bit).
 */
public record RegionId(CavernLayer layer, int cellX, int cellZ) {
    private static final long MASK30 = (1L << 30) - 1;

    public long asLong() {
        return ((long) layer.ordinal() << 60) | ((cellX & MASK30) << 30) | (cellZ & MASK30);
    }

    public static RegionId fromLong(long packed) {
        CavernLayer layer = CavernLayer.values()[(int) (packed >>> 60)];
        int cx = (int) ((packed >>> 30) & MASK30);
        int cz = (int) (packed & MASK30);
        // sign-extend 30-bit values
        cx = (cx << 2) >> 2;
        cz = (cz << 2) >> 2;
        return new RegionId(layer, cx, cz);
    }

    @Override
    public String toString() {
        return layer.id + ":" + cellX + "," + cellZ;
    }
}
