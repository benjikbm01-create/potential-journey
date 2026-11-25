package dev.underdeep.world.analysis;

import dev.underdeep.world.region.RegionEnvironment;
import dev.underdeep.world.region.RegionId;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;

/**
 * Everything the world model knows about a position, without loading chunks. This is the primary
 * query contract for future ecosystem, wildlife and civilization systems.
 *
 * @param regionId     stable persistent id of the region instance (see {@link RegionId})
 * @param region       region type at this height
 * @param layer        cavern layer at this height
 * @param floorY       floor height of the cavern containing (or nearest to) this column in that layer
 * @param ceilingY     ceiling height of that cavern
 * @param openHeight   ceiling - floor (&lt;= 0 means solid rock)
 * @param fluidLevel   level of open fluid at this column for this layer (NaN = none reachable)
 * @param lavaFluid    whether that fluid is lava
 * @param borderDistance distance (blocks) to the nearest region border
 * @param environment  static environment profile of the region
 */
public record RegionReport(RegionId regionId, RegionType region, CavernLayer layer, double floorY, double ceilingY,
                           double openHeight, double fluidLevel, boolean lavaFluid, double borderDistance,
                           RegionEnvironment environment) {

    public static RegionReport at(UnderdeepTerrain t, int x, int y, int z) {
        TerrainColumn c = t.column(x, z);
        CavernLayer layer = c.layerAt(y);
        boolean upper = layer == CavernLayer.UPPER;
        var sample = upper ? c.upperRegion : c.deepRegion;
        double floor = upper ? c.upperFloor : c.deepFloor;
        double ceil = upper ? c.upperCeiling : c.deepCeiling;
        double fluid = Double.NaN;
        boolean lava = upper ? c.upperLava : c.deepLava;
        int layerFluid = upper ? UnderdeepTerrain.SEA_LEVEL : CavernLayer.DEEP.fluidLevel;
        if (!Double.isNaN(c.pondLevel)) {
            fluid = c.pondLevel;
            lava = c.pondLava;
        } else if (floor < layerFluid) {
            fluid = layerFluid;
        }
        return new RegionReport(sample.id(), sample.primary, layer, floor, ceil, ceil - floor, fluid, lava,
                sample.borderDistance, sample.primary.environment());
    }
}
