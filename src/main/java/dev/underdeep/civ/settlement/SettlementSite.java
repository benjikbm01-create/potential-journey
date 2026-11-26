package dev.underdeep.civ.settlement;

import dev.underdeep.civ.Civilization;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;

/**
 * A settlement chosen by {@link SettlementMap}: a pure function of the world seed and the terrain model.
 *
 * @param id        stable settlement id (persisted): layer, category and cavern-graph cell packed
 * @param floorY    model floor height at the centre
 * @param radius    planned radius (the plan may use less)
 * @param overWater the centre sits on open water (waterfront/floating layouts)
 * @param starter   the welcoming settlement guaranteed near the world spawn
 */
public record SettlementSite(long id, Civilization civ, SettlementCategory category, CavernLayer layer, RegionType region,
                             int x, int floorY, int z, int radius, double headroom, boolean overWater, boolean waterNearby,
                             boolean starter) {

    public static long packId(CavernLayer layer, int cellX, int cellZ) {
        return ((long) layer.ordinal() << 62) | ((long) (cellX & 0x7FFFFFFF) << 31) | (cellZ & 0x7FFFFFFFL);
    }

    public static int cellX(long id) {
        return (int) (id << 2 >> 33);
    }

    public static int cellZ(long id) {
        return (int) (id << 33 >> 33);
    }

    public boolean contains(int bx, int bz, int margin) {
        long dx = bx - x;
        long dz = bz - z;
        long r = radius + margin;
        return dx * dx + dz * dz <= r * r;
    }

    /** Seed for everything derived from this settlement (names, layout, inhabitants). */
    public long seed(long worldSeed) {
        return dev.underdeep.world.noise.Hashing.hash(worldSeed, id, 0x5E77L);
    }
}
