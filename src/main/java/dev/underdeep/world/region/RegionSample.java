package dev.underdeep.world.region;

import dev.underdeep.world.terrain.CavernLayer;

/**
 * Result of sampling a {@link RegionMap} at a horizontal position. Mutable for reuse in hot loops;
 * copy values out if they need to be retained.
 */
public final class RegionSample {
    public CavernLayer layer;
    public RegionType primary;
    public int cellX;
    public int cellZ;
    /** Distance between the nearest and second-nearest region centres (0 on a border). */
    public double borderDistance;
    /** Blend weight of each {@link RegionType} (indexed by ordinal); sums to 1. */
    public final double[] weights = new double[RegionType.values().length];

    public RegionId id() {
        return new RegionId(layer, cellX, cellZ);
    }

    public double weight(RegionType type) {
        return weights[type.ordinal()];
    }

    /** Writes the weighted blend of region terrain profiles into {@code out}. */
    public TerrainProfile blendProfile(TerrainProfile out) {
        out.clear();
        RegionType[] types = RegionType.values();
        for (int i = 0; i < types.length; i++) {
            if (weights[i] > 0) {
                out.addWeighted(types[i].profile(), weights[i]);
            }
        }
        return out;
    }
}
