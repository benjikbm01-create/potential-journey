package dev.underdeep.world.gen;

import dev.underdeep.world.noise.Hashing;
import dev.underdeep.world.region.RegionType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

import java.util.EnumMap;
import java.util.Map;

/**
 * Region-specific rock palettes. Rock is layered into geological strata: horizontal bands
 * (offset by a per-column warp so they tilt and undulate) of different stones. Using several
 * vanilla stones keeps ore generation working in the common bands while giving each region a
 * distinct look on cavern walls.
 */
public final class BlockPalette {
    private record Weighted(BlockState[] states, double[] cumulative) {
        BlockState pick(double u) {
            for (int i = 0; i < cumulative.length; i++) {
                if (u < cumulative[i]) {
                    return states[i];
                }
            }
            return states[states.length - 1];
        }
    }

    private static Weighted of(Object... pairs) {
        int n = pairs.length / 2;
        BlockState[] states = new BlockState[n];
        double[] cum = new double[n];
        double total = 0;
        for (int i = 0; i < n; i++) {
            total += (Double) pairs[i * 2 + 1];
        }
        double acc = 0;
        for (int i = 0; i < n; i++) {
            states[i] = (BlockState) pairs[i * 2];
            acc += (Double) pairs[i * 2 + 1] / total;
            cum[i] = acc;
        }
        return new Weighted(states, cum);
    }

    private static final Map<RegionType, Weighted> UPPER = new EnumMap<>(RegionType.class);
    private static final Map<RegionType, Weighted> DEEP = new EnumMap<>(RegionType.class);

    static {
        BlockState stone = Blocks.STONE.getDefaultState();
        BlockState deepslate = Blocks.DEEPSLATE.getDefaultState();
        UPPER.put(RegionType.ROOTWILD, of(stone, 0.72, Blocks.ANDESITE.getDefaultState(), 0.14,
                Blocks.TUFF.getDefaultState(), 0.08, Blocks.GRANITE.getDefaultState(), 0.06));
        UPPER.put(RegionType.FUNGAL_EXPANSE, of(stone, 0.66, Blocks.TUFF.getDefaultState(), 0.14,
                Blocks.ANDESITE.getDefaultState(), 0.14, Blocks.DRIPSTONE_BLOCK.getDefaultState(), 0.06));
        UPPER.put(RegionType.FLOODED_DEPTHS, of(stone, 0.68, Blocks.ANDESITE.getDefaultState(), 0.16,
                Blocks.DIORITE.getDefaultState(), 0.06, Blocks.DRIPSTONE_BLOCK.getDefaultState(), 0.10));
        UPPER.put(RegionType.ASHEN_HOLLOWS, of(stone, 0.36, Blocks.BLACKSTONE.getDefaultState(), 0.30,
                Blocks.BASALT.getDefaultState(), 0.20, Blocks.SMOOTH_BASALT.getDefaultState(), 0.08, Blocks.TUFF.getDefaultState(), 0.06));
        UPPER.put(RegionType.CRYSTAL_DEEP, of(stone, 0.46, Blocks.CALCITE.getDefaultState(), 0.20,
                Blocks.TUFF.getDefaultState(), 0.24, Blocks.SMOOTH_BASALT.getDefaultState(), 0.10));
        UPPER.put(RegionType.THE_ABYSS, of(deepslate, 0.7, Blocks.TUFF.getDefaultState(), 0.3));

        DEEP.put(RegionType.THE_ABYSS, of(deepslate, 0.66, Blocks.TUFF.getDefaultState(), 0.14,
                Blocks.BLACKSTONE.getDefaultState(), 0.12, Blocks.SMOOTH_BASALT.getDefaultState(), 0.08));
        DEEP.put(RegionType.CRYSTAL_DEEP, of(deepslate, 0.50, Blocks.CALCITE.getDefaultState(), 0.20,
                Blocks.TUFF.getDefaultState(), 0.20, Blocks.SMOOTH_BASALT.getDefaultState(), 0.10));
        DEEP.put(RegionType.ASHEN_HOLLOWS, of(deepslate, 0.40, Blocks.BLACKSTONE.getDefaultState(), 0.30,
                Blocks.BASALT.getDefaultState(), 0.22, Blocks.SMOOTH_BASALT.getDefaultState(), 0.08));
        for (RegionType t : RegionType.values()) {
            DEEP.putIfAbsent(t, of(deepslate, 0.8, Blocks.TUFF.getDefaultState(), 0.2));
        }
    }

    private BlockPalette() {
    }

    /**
     * Base rock at a position.
     *
     * @param strataOffset per-column vertical offset of strata bands (tilt / warp)
     */
    public static BlockState rock(long seed, RegionType region, boolean deep, int y, double strataOffset) {
        // Thick bands; consecutive bands often repeat the dominant stone so walls read as stone with
        // occasional seams rather than stripes.
        int band = Math.floorDiv((int) Math.floor(y + strataOffset), 11);
        double u = Hashing.unit(Hashing.hash(seed, band, region.ordinal(), deep ? 0x0DEE : 0x0A11));
        return (deep ? DEEP : UPPER).get(region).pick(u);
    }
}
