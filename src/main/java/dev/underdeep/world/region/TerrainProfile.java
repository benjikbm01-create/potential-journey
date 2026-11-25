package dev.underdeep.world.region;

/**
 * Numeric terrain-shaping parameters for a region type. Profiles of neighbouring regions are
 * blended by weight near region borders so that terrain transitions are continuous.
 * <p>
 * All lengths are in blocks unless noted. Multipliers are relative to the layer defaults in
 * {@link dev.underdeep.world.terrain.CavernLayer}.
 */
public final class TerrainProfile {
    /** Vertical offset added to the layer's base floor height. */
    public double floorOffset;
    /** Multiplier on the layer's large-scale floor undulation. */
    public double floorAmp;
    /** Height of hills / underground mountains inside open caverns. */
    public double hillAmp;
    /** Frequency of hill noise (1/blocks). */
    public double hillFreq;
    /** 0 = rounded hills, 1 = sharp ridged mountains. */
    public double ridged;
    /** Terrace step height (0 = none). Produces stepped cliffs. */
    public double terrace;
    /** Multipliers on cavern-graph chamber radius and height. */
    public double chamberRadius;
    public double chamberHeight;
    /** Multiplier on connecting tunnel width. */
    public double tunnelWidth;
    /** Amount of additional free-form noise caverns (0..1). */
    public double openField;
    /** Amplitude of 3D wall roughness / overhangs. */
    public double roughness;
    /** Probability a pillar site becomes a pillar, and pillar radius multiplier. */
    public double pillarChance;
    public double pillarRadius;
    /** Amount of natural bridges and arches (0..1). */
    public double bridges;
    /** Amount of stalactites / stalagmites (0..1). */
    public double spikes;
    /** Amount of river channels (0..1). */
    public double rivers;
    /** Chance for a chamber to contain a perched pond (0..1). */
    public double ponds;
    /** Chance for a graph node to host a vertical shaft to the layer below. */
    public double shafts;
    /** Amount of ravines (0..1). */
    public double ravines;
    /** Amount of bottomless-looking pits (deep layer). */
    public double pits;

    public TerrainProfile() {
    }

    public TerrainProfile copy() {
        TerrainProfile p = new TerrainProfile();
        p.addWeighted(this, 1.0);
        return p;
    }

    public void clear() {
        floorOffset = floorAmp = hillAmp = hillFreq = ridged = terrace = 0;
        chamberRadius = chamberHeight = tunnelWidth = openField = roughness = 0;
        pillarChance = pillarRadius = bridges = spikes = rivers = ponds = shafts = ravines = pits = 0;
    }

    public void addWeighted(TerrainProfile o, double w) {
        floorOffset += o.floorOffset * w;
        floorAmp += o.floorAmp * w;
        hillAmp += o.hillAmp * w;
        hillFreq += o.hillFreq * w;
        ridged += o.ridged * w;
        terrace += o.terrace * w;
        chamberRadius += o.chamberRadius * w;
        chamberHeight += o.chamberHeight * w;
        tunnelWidth += o.tunnelWidth * w;
        openField += o.openField * w;
        roughness += o.roughness * w;
        pillarChance += o.pillarChance * w;
        pillarRadius += o.pillarRadius * w;
        bridges += o.bridges * w;
        spikes += o.spikes * w;
        rivers += o.rivers * w;
        ponds += o.ponds * w;
        shafts += o.shafts * w;
        ravines += o.ravines * w;
        pits += o.pits * w;
    }

    /** Fluent builder used by {@link RegionType}. */
    static TerrainProfile of(double floorOffset, double floorAmp, double hillAmp, double hillFreq, double ridged,
                             double terrace, double chamberRadius, double chamberHeight, double tunnelWidth,
                             double openField, double roughness, double pillarChance, double pillarRadius,
                             double bridges, double spikes, double rivers, double ponds, double shafts,
                             double ravines, double pits) {
        TerrainProfile p = new TerrainProfile();
        p.floorOffset = floorOffset;
        p.floorAmp = floorAmp;
        p.hillAmp = hillAmp;
        p.hillFreq = hillFreq;
        p.ridged = ridged;
        p.terrace = terrace;
        p.chamberRadius = chamberRadius;
        p.chamberHeight = chamberHeight;
        p.tunnelWidth = tunnelWidth;
        p.openField = openField;
        p.roughness = roughness;
        p.pillarChance = pillarChance;
        p.pillarRadius = pillarRadius;
        p.bridges = bridges;
        p.spikes = spikes;
        p.rivers = rivers;
        p.ponds = ponds;
        p.shafts = shafts;
        p.ravines = ravines;
        p.pits = pits;
        return p;
    }
}
