package dev.underdeep.world.gen;

import dev.underdeep.registry.ModBlocks;
import dev.underdeep.world.noise.Hashing;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.Landmarks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.util.math.Direction;

import java.util.EnumMap;
import java.util.Map;

/**
 * Region-specific rock and formation palettes.
 * <p>
 * Rock is layered into geological strata: thick horizontal bands (offset by a per-column warp so they
 * tilt and undulate) of different stones. Common bands use stones that vanilla ores can replace.
 * Formations (pillars, bridges, arches and landmarks) use per-region materials: wood trunks in forests,
 * ice in Everfrost, crystal in the Crystal Deep, petrified wood, coral, bone and so on.
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

    private static BlockState d(net.minecraft.block.Block b) {
        return b.getDefaultState();
    }

    static {
        BlockState stone = d(Blocks.STONE);
        BlockState deepslate = d(Blocks.DEEPSLATE);
        UPPER.put(RegionType.ROOTWILD, of(stone, 0.72, d(Blocks.ANDESITE), 0.14, d(Blocks.TUFF), 0.08, d(Blocks.GRANITE), 0.06));
        UPPER.put(RegionType.FUNGAL_EXPANSE, of(stone, 0.66, d(Blocks.TUFF), 0.14, d(Blocks.ANDESITE), 0.14, d(Blocks.DRIPSTONE_BLOCK), 0.06));
        UPPER.put(RegionType.FLOODED_DEPTHS, of(stone, 0.68, d(Blocks.ANDESITE), 0.16, d(Blocks.DIORITE), 0.06, d(Blocks.DRIPSTONE_BLOCK), 0.10));
        UPPER.put(RegionType.ASHEN_HOLLOWS, of(stone, 0.36, d(Blocks.BLACKSTONE), 0.30, d(Blocks.BASALT), 0.20,
                d(Blocks.SMOOTH_BASALT), 0.08, d(Blocks.TUFF), 0.06));
        UPPER.put(RegionType.CRYSTAL_DEEP, of(stone, 0.46, d(Blocks.CALCITE), 0.20, d(Blocks.TUFF), 0.24, d(Blocks.SMOOTH_BASALT), 0.10));
        UPPER.put(RegionType.THE_ABYSS, of(deepslate, 0.7, d(Blocks.TUFF), 0.3));
        UPPER.put(RegionType.EVERFROST, of(stone, 0.38, d(Blocks.PACKED_ICE), 0.30, d(Blocks.SNOW_BLOCK), 0.10,
                d(Blocks.BLUE_ICE), 0.06, d(Blocks.DIORITE), 0.10, d(Blocks.TUFF), 0.06));
        UPPER.put(RegionType.GOLDEN_GROVE, of(stone, 0.62, d(Blocks.GRANITE), 0.18, d(Blocks.ANDESITE), 0.10, d(Blocks.DRIPSTONE_BLOCK), 0.10));
        UPPER.put(RegionType.HOLLOW_PEAKS, of(stone, 0.62, d(Blocks.ANDESITE), 0.15, d(Blocks.GRANITE), 0.08, d(Blocks.TUFF), 0.10,
                d(Blocks.DIORITE), 0.05));
        UPPER.put(RegionType.EMERALD_WETLANDS, of(stone, 0.66, d(Blocks.ANDESITE), 0.14, d(Blocks.TUFF), 0.10, d(Blocks.DRIPSTONE_BLOCK), 0.10));
        UPPER.put(RegionType.PETRIFIED_FOREST, of(stone, 0.46, d(Blocks.TUFF), 0.18, d(Blocks.SANDSTONE), 0.16,
                d(Blocks.TERRACOTTA), 0.10, d(Blocks.CALCITE), 0.10));
        UPPER.put(RegionType.CRIMSON_BASIN, of(stone, 0.40, d(Blocks.NETHERRACK), 0.30, d(Blocks.BLACKSTONE), 0.18, d(Blocks.RED_TERRACOTTA), 0.12));
        UPPER.put(RegionType.SUNKEN_GARDENS, of(stone, 0.52, d(Blocks.ANDESITE), 0.18, d(Blocks.DIORITE), 0.18, d(Blocks.PRISMARINE), 0.12));
        UPPER.put(RegionType.STORMSTONE_CAVERNS, of(d(ModBlocks.STORMSTONE), 0.46, deepslate, 0.18, stone, 0.22, d(Blocks.TUFF), 0.14));
        UPPER.put(RegionType.STARLIGHT_CAVERNS, of(stone, 0.56, d(Blocks.CALCITE), 0.20, d(Blocks.DIORITE), 0.14, d(Blocks.TUFF), 0.10));

        DEEP.put(RegionType.THE_ABYSS, of(deepslate, 0.66, d(Blocks.TUFF), 0.14, d(Blocks.BLACKSTONE), 0.12, d(Blocks.SMOOTH_BASALT), 0.08));
        DEEP.put(RegionType.CRYSTAL_DEEP, of(deepslate, 0.50, d(Blocks.CALCITE), 0.20, d(Blocks.TUFF), 0.20, d(Blocks.SMOOTH_BASALT), 0.10));
        DEEP.put(RegionType.ASHEN_HOLLOWS, of(deepslate, 0.40, d(Blocks.BLACKSTONE), 0.30, d(Blocks.BASALT), 0.22, d(Blocks.SMOOTH_BASALT), 0.08));
        DEEP.put(RegionType.STORMSTONE_CAVERNS, of(d(ModBlocks.STORMSTONE), 0.5, deepslate, 0.4, d(Blocks.TUFF), 0.1));
        for (RegionType t : RegionType.values()) {
            UPPER.putIfAbsent(t, of(stone, 0.8, d(Blocks.ANDESITE), 0.2));
            DEEP.putIfAbsent(t, of(deepslate, 0.8, d(Blocks.TUFF), 0.2));
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
        // Thick bands; the dominant stone repeats often so walls read as stone with occasional seams.
        int band = Math.floorDiv((int) Math.floor(y + strataOffset), 11);
        double u = Hashing.unit(Hashing.hash(seed, band, region.ordinal(), deep ? 0x0DEE : 0x0A11));
        return (deep ? DEEP : UPPER).get(region).pick(u);
    }

    private static final BlockState PERSISTENT_OAK = leaves(Blocks.OAK_LEAVES);
    private static final BlockState PERSISTENT_AZALEA = leaves(Blocks.FLOWERING_AZALEA_LEAVES);
    private static final BlockState PERSISTENT_MANGROVE = leaves(Blocks.MANGROVE_LEAVES);
    private static final BlockState PERSISTENT_GOLDEN = leaves(ModBlocks.GOLDEN_LEAVES);

    private static BlockState leaves(net.minecraft.block.Block b) {
        return b.getDefaultState().with(LeavesBlock.PERSISTENT, true);
    }

    /**
     * Block for a formation (pillar, bridge, landmark) material code in a region.
     *
     * @param h a per-block hash used for material mixing
     */
    public static BlockState formation(RegionType region, int code, long h, long seed, int y, double strataOffset) {
        double u = Hashing.unit(h);
        switch (code) {
            case Landmarks.TRUNK:
                return switch (region) {
                    case GOLDEN_GROVE -> d(Blocks.STRIPPED_OAK_WOOD);
                    case EMERALD_WETLANDS -> d(Blocks.MANGROVE_WOOD);
                    case PETRIFIED_FOREST -> d(ModBlocks.PETRIFIED_WOOD);
                    case CRIMSON_BASIN -> d(Blocks.CRIMSON_HYPHAE);
                    default -> u < 0.03 ? d(ModBlocks.GLOWMOSS) : d(Blocks.DARK_OAK_WOOD);
                };
            case Landmarks.ROOTS:
                return switch (region) {
                    case GOLDEN_GROVE -> u < 0.15 ? d(ModBlocks.AMBER_MOSS) : d(Blocks.OAK_WOOD);
                    case EMERALD_WETLANDS -> u < 0.5 ? d(Blocks.MANGROVE_ROOTS) : d(Blocks.MUDDY_MANGROVE_ROOTS);
                    default -> u < 0.06 ? d(ModBlocks.GLOWMOSS) : (u < 0.35 ? d(Blocks.ROOTED_DIRT) : d(Blocks.DARK_OAK_WOOD));
                };
            case Landmarks.LEAVES:
                // Scattered glowing "fruit" lights canopies from within (natural light in forests).
                if (u < 0.045 && region != RegionType.GOLDEN_GROVE) {
                    return d(Blocks.SHROOMLIGHT);
                }
                return switch (region) {
                    case GOLDEN_GROVE -> PERSISTENT_GOLDEN;
                    case EMERALD_WETLANDS -> PERSISTENT_MANGROVE;
                    case CRIMSON_BASIN -> d(Blocks.NETHER_WART_BLOCK);
                    default -> u < 0.3 ? PERSISTENT_AZALEA : PERSISTENT_OAK;
                };
            case Landmarks.LEAVES_GLOW:
                return switch (region) {
                    case GOLDEN_GROVE -> PERSISTENT_GOLDEN;
                    default -> d(Blocks.SHROOMLIGHT);
                };
            case Landmarks.CORE:
                return switch (region) {
                    case STORMSTONE_CAVERNS -> d(ModBlocks.CHARGED_STORMSTONE);
                    case STARLIGHT_CAVERNS -> d(ModBlocks.STARSTONE);
                    case SUNKEN_GARDENS -> d(Blocks.SEA_LANTERN);
                    case EVERFROST -> u < 0.7 ? d(Blocks.BLUE_ICE) : d(ModBlocks.LUMINITE_BLOCK);
                    default -> d(ModBlocks.LUMINITE_BLOCK);
                };
            case Landmarks.SHELL:
                return switch (region) {
                    case STORMSTONE_CAVERNS -> d(ModBlocks.STORMSTONE);
                    case STARLIGHT_CAVERNS -> u < 0.1 ? d(ModBlocks.STARSTONE) : d(Blocks.CALCITE);
                    case SUNKEN_GARDENS -> u < 0.33 ? d(Blocks.TUBE_CORAL_BLOCK) : u < 0.66 ? d(Blocks.BRAIN_CORAL_BLOCK) : d(Blocks.BUBBLE_CORAL_BLOCK);
                    case EVERFROST -> d(Blocks.PACKED_ICE);
                    default -> d(Blocks.AMETHYST_BLOCK);
                };
            case Landmarks.BONE:
                return Blocks.BONE_BLOCK.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
            case Landmarks.STEM:
                return region == RegionType.CRIMSON_BASIN ? d(Blocks.CRIMSON_HYPHAE) : d(Blocks.MUSHROOM_STEM);
            case Landmarks.CAP:
                if (region == RegionType.CRIMSON_BASIN) {
                    return d(Blocks.NETHER_WART_BLOCK);
                }
                return (h >>> 40 & 1) == 0 ? d(Blocks.RED_MUSHROOM_BLOCK) : d(Blocks.BROWN_MUSHROOM_BLOCK);
            case Landmarks.CAP_GLOW:
                if (region == RegionType.CRIMSON_BASIN) {
                    return u < 0.4 ? d(Blocks.SHROOMLIGHT) : d(ModBlocks.EMBER_NYLIUM);
                }
                return u < 0.45 ? d(ModBlocks.SPOREGLOW_SOIL) : d(Blocks.SHROOMLIGHT);
            case Landmarks.LAVA:
                return d(Blocks.LAVA);
            case Landmarks.ROCK:
                if (region == RegionType.ASHEN_HOLLOWS) {
                    return u < 0.1 ? d(Blocks.MAGMA_BLOCK) : u < 0.55 ? d(Blocks.BASALT) : d(Blocks.BLACKSTONE);
                }
                return rock(seed, region, false, y, strataOffset);
            default:
                return pillar(region, u, seed, y, strataOffset);
        }
    }

    private static BlockState pillar(RegionType region, double u, long seed, int y, double strataOffset) {
        return switch (region) {
            case ROOTWILD -> u < 0.03 ? d(ModBlocks.GLOWMOSS) : d(Blocks.DARK_OAK_WOOD);
            case GOLDEN_GROVE -> u < 0.03 ? d(ModBlocks.AMBER_MOSS) : d(Blocks.STRIPPED_OAK_WOOD);
            case EMERALD_WETLANDS -> u < 0.04 ? d(ModBlocks.GLOWMOSS) : d(Blocks.MANGROVE_WOOD);
            case FUNGAL_EXPANSE -> u < 0.04 ? d(ModBlocks.SPOREGLOW_SOIL) : d(Blocks.MUSHROOM_STEM);
            case CRIMSON_BASIN -> u < 0.05 ? d(Blocks.SHROOMLIGHT) : d(Blocks.CRIMSON_HYPHAE);
            case PETRIFIED_FOREST -> u < 0.02 ? d(ModBlocks.AMBER_RESIN) : d(ModBlocks.PETRIFIED_WOOD);
            case EVERFROST -> u < 0.12 ? d(Blocks.BLUE_ICE) : d(Blocks.PACKED_ICE);
            case CRYSTAL_DEEP -> u < 0.08 ? d(ModBlocks.LUMINITE_BLOCK) : d(Blocks.CALCITE);
            case STORMSTONE_CAVERNS -> u < 0.1 ? d(ModBlocks.CHARGED_STORMSTONE) : d(ModBlocks.STORMSTONE);
            case STARLIGHT_CAVERNS -> u < 0.06 ? d(ModBlocks.STARSTONE) : d(Blocks.CALCITE);
            case SUNKEN_GARDENS -> u < 0.3 ? d(Blocks.DARK_PRISMARINE) : d(Blocks.PRISMARINE);
            case FLOODED_DEPTHS -> u < 0.5 ? d(Blocks.DRIPSTONE_BLOCK) : d(Blocks.STONE);
            case ASHEN_HOLLOWS -> d(Blocks.BASALT);
            case THE_ABYSS -> u < 0.2 ? d(Blocks.COBBLED_DEEPSLATE) : d(Blocks.DEEPSLATE);
            default -> rock(seed, region, false, y, strataOffset);
        };
    }
}
