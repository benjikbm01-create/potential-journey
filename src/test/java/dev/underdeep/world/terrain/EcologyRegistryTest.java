package dev.underdeep.world.terrain;

import dev.underdeep.ecology.EcologyRegistry;
import dev.underdeep.ecology.RegionEcology;
import dev.underdeep.ecology.Species;
import dev.underdeep.world.region.RegionType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tests of the Stage 2 ecology data (no Minecraft bootstrap). */
class EcologyRegistryTest {
    @Test
    void everyEcosystemHasItsOwnBiologyAndResources() {
        for (RegionType t : RegionType.values()) {
            long bio = EcologyRegistry.inRegion(t).stream().filter(Species::isBiological).count();
            long min = EcologyRegistry.inRegion(t).stream().filter(s -> !s.isBiological()).count();
            assertTrue(bio >= 3, t + " has only " + bio + " species");
            // Golden Grove's regional resource is biological (renewable amber resin from goldbough trees)
            assertTrue(min >= 1 || t == RegionType.GOLDEN_GROVE, t + " has no regional mineral");
        }
    }

    @Test
    void speciesAndBlockIdsAreUnique() {
        Set<String> blocks = new HashSet<>();
        for (Species s : EcologyRegistry.all()) {
            for (String b : s.blockIds()) {
                assertTrue(blocks.add(b), "block " + b + " claimed twice");
            }
            assertFalse(s.habitats().isEmpty(), s.id() + " has no habitat");
        }
    }

    @Test
    void contentCountsMatchTheStage2Targets() {
        assertEquals(9, EcologyRegistry.count(Species.Kind.TREE));
        long fungi = EcologyRegistry.count(Species.Kind.FUNGUS, Species.Kind.GIANT_FUNGUS);
        assertTrue(fungi >= 10 && fungi <= 16, "fungi " + fungi);
        long crops = EcologyRegistry.count(Species.Kind.CROP);
        assertTrue(crops >= 8 && crops <= 12, "crops " + crops);
        long aquatic = EcologyRegistry.count(Species.Kind.AQUATIC, Species.Kind.CORAL);
        assertTrue(aquatic >= 8 && aquatic <= 12, "aquatic " + aquatic);
        long minerals = EcologyRegistry.count(Species.Kind.MINERAL, Species.Kind.CRYSTAL);
        assertTrue(minerals >= 10 && minerals <= 16, "minerals " + minerals);
    }

    @Test
    void regionEcologyIsDeterministicAndBounded() {
        for (RegionType t : RegionType.values()) {
            RegionEcology a = RegionEcology.of(t, 12345L);
            RegionEcology b = RegionEcology.of(t, 12345L);
            assertEquals(a, b);
            assertTrue(a.foodAvailability() >= 0 && a.foodAvailability() <= 1);
            assertTrue(a.productivity() >= 0 && a.productivity() <= 1);
            assertFalse(a.wildlife().isEmpty(), t + " supports no wildlife guild");
        }
        // welcoming forests are more productive than the Abyss
        assertTrue(RegionEcology.of(RegionType.ROOTWILD, 1).productivity() > RegionEcology.of(RegionType.THE_ABYSS, 1).productivity());
    }
}
