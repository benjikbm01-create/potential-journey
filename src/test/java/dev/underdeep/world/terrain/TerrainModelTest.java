package dev.underdeep.world.terrain;

import dev.underdeep.world.debug.TerrainPreview;
import dev.underdeep.world.region.RegionId;
import dev.underdeep.world.region.RegionType;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-Java tests of the terrain model. These do not need Minecraft and run in milliseconds to
 * seconds. Set system property {@code underdeep.previews=true} to write PNG previews to
 * {@code build/previews}.
 */
class TerrainModelTest {
    static final long[] SEEDS = {12345L, -987654321L, 42L};

    @Test
    void deterministicAcrossInstancesAndQueryOrder() {
        for (long seed : SEEDS) {
            UnderdeepTerrain a = new UnderdeepTerrain(seed);
            UnderdeepTerrain b = new UnderdeepTerrain(seed);
            // Query b in reverse order to make sure caches never leak state between positions.
            int n = 0;
            long hashA = 0;
            long hashB = 0;
            for (int x = -400; x <= 400; x += 37) {
                for (int z = -400; z <= 400; z += 41) {
                    for (int y = -60; y < 316; y += 7) {
                        hashA = hashA * 31 + a.materialAt(x, y, z).ordinal();
                        n++;
                    }
                }
            }
            for (int x = 400 - ((800) % 37); x >= -400; x -= 37) {
                // reverse traversal touched first, discarded: exercises caches
                b.column(x, 999);
            }
            for (int x = -400; x <= 400; x += 37) {
                for (int z = -400; z <= 400; z += 41) {
                    for (int y = -60; y < 316; y += 7) {
                        hashB = hashB * 31 + b.materialAt(x, y, z).ordinal();
                    }
                }
            }
            assertEquals(hashA, hashB, "terrain must be deterministic for seed " + seed);
            assertTrue(n > 1000);
        }
    }

    @Test
    void differentSeedsDiffer() {
        UnderdeepTerrain a = new UnderdeepTerrain(1);
        UnderdeepTerrain b = new UnderdeepTerrain(2);
        int diff = 0;
        for (int x = -500; x <= 500; x += 25) {
            for (int y = 0; y < 300; y += 10) {
                if (a.materialAt(x, y, 77) != b.materialAt(x, y, 77)) {
                    diff++;
                }
            }
        }
        assertTrue(diff > 50, "seeds should produce different terrain, diff=" + diff);
    }

    @Test
    void boundariesAreSealed() {
        for (long seed : SEEDS) {
            UnderdeepTerrain t = new UnderdeepTerrain(seed);
            for (int x = -2000; x <= 2000; x += 61) {
                for (int z = -2000; z <= 2000; z += 67) {
                    assertEquals(UnderdeepTerrain.Material.BEDROCK, t.materialAt(x, UnderdeepTerrain.MIN_Y, z));
                    assertEquals(UnderdeepTerrain.Material.BEDROCK, t.materialAt(x, UnderdeepTerrain.TOP_Y - 1, z));
                    for (int y = CavernLayer.UPPER.maxCeiling + 5; y < UnderdeepTerrain.TOP_Y; y++) {
                        UnderdeepTerrain.Material m = t.materialAt(x, y, z);
                        assertTrue(m == UnderdeepTerrain.Material.STONE || m == UnderdeepTerrain.Material.BEDROCK,
                                "roof must be solid at " + x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void allRegionsNearOrigin() {
        for (long seed : SEEDS) {
            UnderdeepTerrain t = new UnderdeepTerrain(seed);
            Set<RegionType> found = EnumSet.noneOf(RegionType.class);
            for (CavernLayer layer : CavernLayer.values()) {
                for (int x = -2600; x <= 2600; x += 64) {
                    for (int z = -2600; z <= 2600; z += 64) {
                        found.add(t.regions(layer).typeAt(x, z));
                    }
                }
            }
            assertEquals(EnumSet.allOf(RegionType.class), found, "seed " + seed + " regions: " + found);
            assertEquals(RegionType.ROOTWILD, t.regions(CavernLayer.UPPER).typeAt(0, 0), "spawn must be Rootwild");
        }
    }

    @Test
    void terrainContinuousAcrossRegionBorders() {
        // Region profiles are blended near borders, so the smooth base floor must not jump where the
        // primary region changes (biome borders are crisp, terrain is not).
        for (long seed : SEEDS) {
            UnderdeepTerrain t = new UnderdeepTerrain(seed);
            int borders = 0;
            double worst = 0;
            for (int z = -2000; z <= 2000; z += 250) {
                TerrainColumn prev = null;
                for (int x = -2000; x <= 2000; x++) {
                    TerrainColumn c = t.column(x, z);
                    if (prev != null && prev.upperRegion.primary != c.upperRegion.primary) {
                        borders++;
                        worst = Math.max(worst, Math.abs(prev.upperBaseFloor - c.upperBaseFloor));
                        worst = Math.max(worst, Math.abs(prev.deepBaseFloor - c.deepBaseFloor));
                    }
                    prev = c;
                }
            }
            assertTrue(borders > 5, "expected region borders along the sample lines, seed " + seed);
            assertTrue(worst < 2.0, "base floor jumps " + worst + " blocks at a region border, seed " + seed);
        }
    }

    @Test
    void regionIdRoundTrip() {
        RegionId id = new RegionId(CavernLayer.DEEP, -12345, 6789);
        assertEquals(id, RegionId.fromLong(id.asLong()));
        RegionId id2 = new RegionId(CavernLayer.UPPER, 5, -1);
        assertEquals(id2, RegionId.fromLong(id2.asLong()));
    }

    @Test
    void startCavernIsOpenAndDry() {
        for (long seed : SEEDS) {
            UnderdeepTerrain t = new UnderdeepTerrain(seed);
            TerrainColumn c = t.column(0, 0);
            assertTrue(c.upperCeiling - c.upperFloor > 30, "start cavern must be tall, seed " + seed
                    + " floor=" + c.upperFloor + " ceil=" + c.upperCeiling);
            assertTrue(c.upperFloor > UnderdeepTerrain.SEA_LEVEL + 2, "start must be above water, seed " + seed);
        }
    }

    @Test
    void writePreviews() throws Exception {
        if (!Boolean.getBoolean("underdeep.previews")) {
            return;
        }
        for (long seed : SEEDS) {
            UnderdeepTerrain t = new UnderdeepTerrain(seed);
            File dir = new File("build/previews/seed_" + seed);
            TerrainPreview.write(TerrainPreview.regionMap(t, CavernLayer.UPPER, 0, 0, 512, 12), new File(dir, "regions_upper.png"));
            TerrainPreview.write(TerrainPreview.regionMap(t, CavernLayer.DEEP, 0, 0, 512, 12), new File(dir, "regions_deep.png"));
            TerrainPreview.write(TerrainPreview.layerMap(t, CavernLayer.UPPER, 0, 0, 768, 2), new File(dir, "upper_layer.png"));
            TerrainPreview.write(TerrainPreview.layerMap(t, CavernLayer.DEEP, 0, 0, 768, 2), new File(dir, "deep_layer.png"));
            TerrainPreview.write(TerrainPreview.crossSection(t, 0, -400, 800, 1), new File(dir, "section_z0.png"));
            TerrainPreview.write(TerrainPreview.crossSection(t, 300, -400, 800, 1), new File(dir, "section_z300.png"));
            TerrainPreview.write(TerrainPreview.slice(t, 140, 0, 0, 768, 1), new File(dir, "slice_y140.png"));
        }
    }
}
