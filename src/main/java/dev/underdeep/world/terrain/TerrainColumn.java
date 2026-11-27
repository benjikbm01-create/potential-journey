package dev.underdeep.world.terrain;

import dev.underdeep.world.region.RegionSample;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.region.TerrainProfile;

/**
 * All per-column (2D) terrain values at one block column. Computed once per column by
 * {@link UnderdeepTerrain#column(int, int)} and reused for every block in the column.
 * Treat as read-only after construction.
 */
public final class TerrainColumn {
    public int x;
    public int z;

    public final RegionSample upperRegion = new RegionSample();
    public final RegionSample deepRegion = new RegionSample();
    public final TerrainProfile upperProfile = new TerrainProfile();
    public final TerrainProfile deepProfile = new TerrainProfile();

    /** Upper layer floor / ceiling (open where floor &lt; y &lt; ceiling). */
    public double upperFloor;
    public double upperCeiling;
    /** Smooth upper floor before hills / rivers (useful for analysis). */
    public double upperBaseFloor;
    /** Deep layer floor / ceiling. */
    public double deepFloor;
    public double deepCeiling;
    public double deepBaseFloor;

    /** Below this height blocks belong to the deep layer (biomes, fluids, palettes). */
    public double separation;

    /** 0..1 chamber-ness of each layer (scales hills, pillars). */
    public double upperChamber;
    public double deepChamber;
    public double upperOpenHeight;
    public double deepOpenHeight;

    /** Vertical shaft: open where horizontal distance &lt; radius between bottom and top. */
    public double shaftDist = Double.MAX_VALUE;
    public double shaftRadius;
    public double shaftBottom;
    public double shaftTop;
    public double shaftAngle;
    public boolean shaftSpiral;

    /** Perched pond (NaN level when none). */
    public double pondLevel = Double.NaN;
    public boolean pondLava;
    /** 0..1 suppression of 3D noise around ponds (1 = fully suppressed). */
    public double pondGuard;

    /** Pillars: horizontal distance to nearest pillar axis and its base radius (0 = none). */
    public double upperPillarDist = Double.MAX_VALUE;
    public double upperPillarRadius;
    public double deepPillarDist = Double.MAX_VALUE;
    public double deepPillarRadius;

    /** Natural bridge slab (NaN when none). */
    public double upperBridgeY = Double.NaN;
    public double upperBridgeThickness;
    public double deepBridgeY = Double.NaN;
    public double deepBridgeThickness;

    /** Pillar geometry: axis position, lean per block of height, lobe phase. */
    public double upperPillarX, upperPillarZ, upperPillarLeanX, upperPillarLeanZ, upperPillarPhase;
    public double deepPillarX, deepPillarZ, deepPillarLeanX, deepPillarLeanZ, deepPillarPhase;

    /**
     * Sub-regional variant 0..3 of the ecosystem at this column (0 = open/plain, 1 = typical,
     * 2 = rugged/dense, 3 = lake/wet). Meaning per ecosystem is documented in CONTENT_REGISTRY.md.
     */
    public int variant;

    /** Landmarks whose bounds cover this column (empty array when none). */
    public Landmarks.Landmark[] landmarks = NO_LANDMARKS;
    static final Landmarks.Landmark[] NO_LANDMARKS = new Landmarks.Landmark[0];

    /** River channel strength 0..1 (upper layer). */
    public double river;

    /**
     * Stage 10 descent caves crossing this column: open bands [bandFloor, bandCeil] (count {@link #bandCount}).
     * A band flagged as ledge crosses open space and stands on a natural rock ledge just below its floor.
     */
    public int bandCount;
    public double[] bandFloor = NO_BANDS;
    public double[] bandCeil = NO_BANDS;
    public boolean[] bandLedge;
    static final double[] NO_BANDS = new double[0];

    /** Index of the descent band containing height y, or -1. */
    public int bandAt(double y) {
        for (int i = 0; i < bandCount; i++) {
            if (y >= bandFloor[i] && y <= bandCeil[i]) {
                return i;
            }
        }
        return -1;
    }
    public boolean upperLava;
    public boolean deepLava;

    public RegionType regionAt(double y) {
        return y >= separation ? upperRegion.primary : deepRegion.primary;
    }

    public CavernLayer layerAt(double y) {
        return y >= separation ? CavernLayer.UPPER : CavernLayer.DEEP;
    }

    /** Height of open space in the upper layer at this column (may be negative = none). */
    public double upperGap() {
        return upperCeiling - upperFloor;
    }

    public double deepGap() {
        return deepCeiling - deepFloor;
    }
}
