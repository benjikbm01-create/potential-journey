package dev.underdeep.world.region;

import java.util.Set;

/**
 * Static environmental description of a region type, exposed for future ecosystem, wildlife and
 * civilization systems (Stages 2-6). Values are normalised to [0, 1] unless stated.
 *
 * @param temperature      0 = frigid, 1 = volcanic
 * @param humidity         0 = arid, 1 = saturated
 * @param ambientLight     typical natural light availability from glowing flora/minerals
 * @param waterAvailability how easily fresh water is found
 * @param danger           baseline environmental hazard (lava, drops, darkness)
 * @param fertility        suitability for farming and plant growth
 * @param habitatTags      free-form habitat descriptors consumed by future species definitions
 * @param resourceTags     broad resource categories present (refined in Stage 2)
 */
public record RegionEnvironment(
        double temperature,
        double humidity,
        double ambientLight,
        double waterAvailability,
        double danger,
        double fertility,
        Set<String> habitatTags,
        Set<String> resourceTags) {
}
