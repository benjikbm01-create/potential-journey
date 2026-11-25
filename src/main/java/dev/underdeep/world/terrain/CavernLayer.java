package dev.underdeep.world.terrain;

import dev.underdeep.world.region.RegionType;

import java.util.List;

/**
 * Vertical cavern strata. The world is built from stacked layers of interconnected caverns
 * separated by a rock crust and joined by shafts, ravines and winding tunnels. Each layer has its
 * own region map and cavern graph.
 * <p>
 * Persistence contract: ordinal is packed into {@link dev.underdeep.world.region.RegionId}; append
 * new layers only at the end.
 */
public enum CavernLayer {
    UPPER("upper", 142, 22, 112, 298, 176, 36, 92, 44, 118, 112,
            List.of(RegionType.ROOTWILD, RegionType.FUNGAL_EXPANSE, RegionType.FLOODED_DEPTHS,
                    RegionType.ASHEN_HOLLOWS, RegionType.CRYSTAL_DEEP, RegionType.EVERFROST, RegionType.GOLDEN_GROVE,
                    RegionType.HOLLOW_PEAKS, RegionType.EMERALD_WETLANDS, RegionType.PETRIFIED_FOREST,
                    RegionType.CRIMSON_BASIN, RegionType.SUNKEN_GARDENS, RegionType.STORMSTONE_CAVERNS,
                    RegionType.STARLIGHT_CAVERNS)),
    DEEP("deep", -8, 16, -44, 118, 224, 46, 110, 40, 96, 160,
            List.of(RegionType.THE_ABYSS, RegionType.CRYSTAL_DEEP, RegionType.ASHEN_HOLLOWS,
                    RegionType.STORMSTONE_CAVERNS));

    public final String id;
    /** Base floor height of the layer's caverns. */
    public final int floorBase;
    /** Amplitude of large-scale floor undulation. */
    public final int floorAmp;
    /** Level of the layer's open fluid bodies (lakes / lava seas). */
    public final int fluidLevel;
    /** Absolute maximum ceiling height for this layer. */
    public final int maxCeiling;
    /** Cavern graph cell size (blocks). */
    public final int graphCell;
    public final int chamberRadiusMin;
    public final int chamberRadiusMax;
    public final int chamberHeightMin;
    public final int chamberHeightMax;
    /** Region (Voronoi) cell size in blocks. */
    public final int regionCell;
    /** Region types that can appear in this layer. */
    public final List<RegionType> regions;

    CavernLayer(String id, int floorBase, int floorAmp, int fluidLevel, int maxCeiling, int graphCell,
                int chamberRadiusMin, int chamberRadiusMax, int chamberHeightMin, int chamberHeightMax,
                int regionCell, List<RegionType> regions) {
        this.id = id;
        this.floorBase = floorBase;
        this.floorAmp = floorAmp;
        this.fluidLevel = fluidLevel;
        this.maxCeiling = maxCeiling;
        this.graphCell = graphCell;
        this.chamberRadiusMin = chamberRadiusMin;
        this.chamberRadiusMax = chamberRadiusMax;
        this.chamberHeightMin = chamberHeightMin;
        this.chamberHeightMax = chamberHeightMax;
        this.regionCell = regionCell;
        this.regions = regions;
    }
}
