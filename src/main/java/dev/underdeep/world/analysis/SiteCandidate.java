package dev.underdeep.world.analysis;

import dev.underdeep.world.region.RegionId;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;

/**
 * A scored location for a future settlement or infrastructure element.
 *
 * @param type          site type evaluated
 * @param x             centre block x
 * @param y             representative floor height (block the settlement would stand on)
 * @param z             centre block z
 * @param layer         cavern layer
 * @param regionId      stable region id (for persistence by later stages)
 * @param region        region type
 * @param score         0..1 overall suitability (0 = unusable)
 * @param usableRadius  radius (blocks) of contiguous usable ground around the centre
 * @param headroom      median open height above usable ground
 * @param slope         mean floor slope across the usable area
 * @param waterNearby   fresh water within the analysed area
 * @param lavaNearby    lava within the analysed area
 * @param connections   number of tunnels leaving the nearest cavern-graph chamber
 */
public record SiteCandidate(SiteType type, int x, int y, int z, CavernLayer layer, RegionId regionId, RegionType region,
                            double score, int usableRadius, double headroom, double slope, boolean waterNearby,
                            boolean lavaNearby, int connections) {
}
