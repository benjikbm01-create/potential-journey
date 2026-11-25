package dev.underdeep.world.gen;

import dev.underdeep.registry.ModBlocks;
import dev.underdeep.world.noise.Hashing;
import dev.underdeep.world.region.Lighting;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.Set;

/**
 * Paints cavern floors and ceilings with region-specific ground cover, and places pools of
 * light-emitting ground cover according to each ecosystem's {@link Lighting} class (the main source
 * of ground-level natural light). Runs in the SURFACE step on a single chunk only.
 */
public final class SurfacePainter {
    private static final Set<Block> PAINTABLE = Set.of(Blocks.STONE, Blocks.DEEPSLATE, Blocks.ANDESITE, Blocks.GRANITE,
            Blocks.DIORITE, Blocks.TUFF, Blocks.CALCITE, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.SMOOTH_BASALT,
            Blocks.DRIPSTONE_BLOCK, Blocks.PACKED_ICE, Blocks.SNOW_BLOCK, Blocks.BLUE_ICE, Blocks.SANDSTONE,
            Blocks.TERRACOTTA, Blocks.NETHERRACK, Blocks.RED_TERRACOTTA, Blocks.PRISMARINE, ModBlocks.STORMSTONE);

    private SurfacePainter() {
    }

    private static boolean isOpen(BlockState s) {
        return s.isAir() || !s.getFluidState().isEmpty();
    }

    public static void paint(UnderdeepTerrain t, Chunk chunk) {
        ChunkPos cp = chunk.getPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        long seed = t.seed();
        int top = UnderdeepTerrain.TOP_Y - 2;
        int bottom = UnderdeepTerrain.MIN_Y + 1;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getStartX() + lx;
                int z = cp.getStartZ() + lz;
                TerrainColumn c = t.column(x, z);
                double pa = t.patchA(x, z);
                double pb = t.patchB(x, z);
                int depth = -1;
                boolean openWater = false;
                boolean openLava = false;
                boolean openAir = false;
                BlockState prev = chunk.getBlockState(pos.set(x, top + 1, z));
                for (int y = top; y >= bottom; y--) {
                    BlockState s = chunk.getBlockState(pos.set(x, y, z));
                    if (isOpen(s)) {
                        if (!isOpen(prev) && PAINTABLE.contains(prev.getBlock()) && s.isAir()
                                && openBelow(chunk, pos, x, y, z, 6)) {
                            BlockState ceil = ceiling(seed, c.regionAt(y + 1), x, y + 1, z, pb);
                            if (ceil != null) {
                                chunk.setBlockState(pos.set(x, y + 1, z), ceil, false);
                            }
                        }
                        depth = 0;
                        openWater = s.getFluidState().isOf(Fluids.WATER);
                        openLava = s.getFluidState().isOf(Fluids.LAVA);
                        openAir = s.isAir();
                        prev = s;
                        continue;
                    }
                    if (depth >= 0) {
                        depth++;
                        if (depth <= 4 && PAINTABLE.contains(s.getBlock())) {
                            RegionType region = c.regionAt(y);
                            BlockState f = floor(seed, c, region, x, y, z, depth, openWater, openLava, pa, pb);
                            if (f != null) {
                                chunk.setBlockState(pos.set(x, y, z), f, false);
                                s = f;
                                if (depth == 1 && openAir && region == RegionType.EVERFROST && f.isOf(Blocks.SNOW_BLOCK)
                                        && rnd(seed, x, y, z ^ 0x51) < 0.7) {
                                    BlockState above = chunk.getBlockState(pos.set(x, y + 1, z));
                                    if (above.isAir()) {
                                        int layers = 1 + (int) (rnd(seed, x, y, z ^ 0x52) * 2);
                                        chunk.setBlockState(pos, Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, layers), false);
                                    }
                                }
                            }
                        }
                        if (depth > 4) {
                            depth = -1;
                        }
                    }
                    prev = s;
                }
            }
        }
    }

    /** True when {@code n} blocks starting at y are all open (a real ceiling, not a wall step). */
    private static boolean openBelow(Chunk chunk, BlockPos.Mutable pos, int x, int y, int z, int n) {
        for (int i = 0; i < n; i++) {
            if (!isOpen(chunk.getBlockState(pos.set(x, y - i, z)))) {
                pos.set(x, y, z);
                return false;
            }
        }
        pos.set(x, y, z);
        return true;
    }

    private static double rnd(long seed, int x, int y, int z) {
        return Hashing.unit(Hashing.hash(seed, x, y, z));
    }

    /** Chance that a dry floor top block becomes the region's light-emitting ground cover. */
    static double glowChance(RegionType region, double pool) {
        return switch (region.lighting()) {
            case BRIGHT -> 0.26 + 0.30 * pool;
            case MODERATE -> 0.02 + 0.10 * pool;
            case DARK -> 0.006 + 0.02 * pool;
            case PITCH -> 0;
        };
    }

    private static BlockState glowBlock(RegionType region) {
        return switch (region) {
            case ROOTWILD, EMERALD_WETLANDS, HOLLOW_PEAKS, FLOODED_DEPTHS -> ModBlocks.GLOWMOSS.getDefaultState();
            case GOLDEN_GROVE -> ModBlocks.AMBER_MOSS.getDefaultState();
            case STARLIGHT_CAVERNS -> ModBlocks.STARMOSS.getDefaultState();
            case FUNGAL_EXPANSE -> ModBlocks.SPOREGLOW_SOIL.getDefaultState();
            case CRIMSON_BASIN -> ModBlocks.EMBER_NYLIUM.getDefaultState();
            case STORMSTONE_CAVERNS -> ModBlocks.CHARGED_STORMSTONE.getDefaultState();
            case PETRIFIED_FOREST -> ModBlocks.AMBER_RESIN.getDefaultState();
            case CRYSTAL_DEEP, EVERFROST, SUNKEN_GARDENS -> ModBlocks.LUMINITE_BLOCK.getDefaultState();
            case ASHEN_HOLLOWS -> Blocks.MAGMA_BLOCK.getDefaultState();
            case THE_ABYSS -> null;
        };
    }

    private static BlockState floor(long seed, TerrainColumn c, RegionType region, int x, int y, int z, int depth,
                                    boolean water, boolean lava, double p, double pb) {
        double r = rnd(seed, x, y, z);
        boolean deep = y < c.separation;
        int fluidLevel = deep ? CavernLayer.DEEP.fluidLevel : UnderdeepTerrain.SEA_LEVEL;
        boolean shore = !water && y <= fluidLevel + 2 && y >= fluidLevel - 1;
        if (lava || (!water && region.lavaFluid() && y <= fluidLevel + 2)) {
            if (depth == 1) {
                return r < 0.5 ? Blocks.MAGMA_BLOCK.getDefaultState() : Blocks.BLACKSTONE.getDefaultState();
            }
            return depth == 2 ? Blocks.BLACKSTONE.getDefaultState() : null;
        }
        if (water) {
            if (depth > 2) {
                return null;
            }
            if (depth == 1 && (region == RegionType.SUNKEN_GARDENS || region == RegionType.FLOODED_DEPTHS
                    || region == RegionType.EMERALD_WETLANDS || region == RegionType.STARLIGHT_CAVERNS)
                    && rnd(seed, x, y ^ 0x77, z) < (region == RegionType.SUNKEN_GARDENS ? 0.06 : 0.025)) {
                return Blocks.SEA_LANTERN.getDefaultState();
            }
            return switch (region) {
                case ASHEN_HOLLOWS, THE_ABYSS, STORMSTONE_CAVERNS -> Blocks.GRAVEL.getDefaultState();
                case EVERFROST -> p < 0 ? Blocks.GRAVEL.getDefaultState() : Blocks.CLAY.getDefaultState();
                case EMERALD_WETLANDS -> p < 0.2 ? Blocks.MUD.getDefaultState() : Blocks.CLAY.getDefaultState();
                case SUNKEN_GARDENS -> p < -0.2 ? Blocks.GRAVEL.getDefaultState() : (p < 0.3 ? Blocks.SAND.getDefaultState()
                        : Blocks.CLAY.getDefaultState());
                case CRIMSON_BASIN -> Blocks.SOUL_SAND.getDefaultState();
                default -> p < -0.3 ? Blocks.CLAY.getDefaultState() : p < 0.05 ? Blocks.SAND.getDefaultState()
                        : p < 0.35 ? Blocks.GRAVEL.getDefaultState()
                        : (region == RegionType.FLOODED_DEPTHS && depth == 1 ? Blocks.MUD.getDefaultState() : Blocks.SAND.getDefaultState());
            };
        }
        // Natural light: pools of glowing ground cover (top block only, dry floors).
        if (depth == 1) {
            double pool = UnderdeepTerrain.smoothstepPublic(-0.25, 0.55, pb);
            BlockState glow = glowBlock(region);
            if (glow != null && rnd(seed, x, y ^ 0x6C, z) < glowChance(region, pool)) {
                return glow;
            }
        }
        switch (region) {
            case ROOTWILD -> {
                if (shore) {
                    return depth <= 2 ? (p > 0.3 ? Blocks.GRAVEL : Blocks.SAND).getDefaultState() : null;
                }
                if (depth == 1) {
                    if (p < -0.28) {
                        return Blocks.MOSS_BLOCK.getDefaultState();
                    }
                    if (p > 0.5) {
                        return (r < 0.5 ? Blocks.PODZOL : Blocks.COARSE_DIRT).getDefaultState();
                    }
                    return r < 0.04 ? Blocks.ROOTED_DIRT.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
                }
                return depth <= 3 || r < 0.5 ? Blocks.DIRT.getDefaultState() : null;
            }
            case GOLDEN_GROVE -> {
                if (shore) {
                    return depth <= 2 ? Blocks.SAND.getDefaultState() : null;
                }
                if (depth == 1) {
                    if (p > 0.45) {
                        return Blocks.COARSE_DIRT.getDefaultState();
                    }
                    return r < 0.05 ? Blocks.ROOTED_DIRT.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
                }
                return depth <= 3 ? Blocks.DIRT.getDefaultState() : null;
            }
            case EMERALD_WETLANDS -> {
                if (depth == 1) {
                    if (y <= fluidLevel + 1) {
                        return p < 0 ? Blocks.MUD.getDefaultState() : Blocks.MUDDY_MANGROVE_ROOTS.getDefaultState();
                    }
                    if (p < -0.2) {
                        return Blocks.MOSS_BLOCK.getDefaultState();
                    }
                    return Blocks.GRASS_BLOCK.getDefaultState();
                }
                return depth <= 3 ? (y <= fluidLevel + 2 ? Blocks.MUD : Blocks.DIRT).getDefaultState() : null;
            }
            case FUNGAL_EXPANSE -> {
                if (shore) {
                    return depth <= 2 ? Blocks.MUD.getDefaultState() : null;
                }
                boolean nylium = p > 0.25;
                if (depth == 1) {
                    if (r < 0.08) {
                        return Blocks.MOSS_BLOCK.getDefaultState();
                    }
                    if (p < 0.25) {
                        return Blocks.MYCELIUM.getDefaultState();
                    }
                    return Blocks.WARPED_NYLIUM.getDefaultState();
                }
                if (depth <= 3) {
                    return nylium ? Blocks.NETHERRACK.getDefaultState() : Blocks.DIRT.getDefaultState();
                }
                return null;
            }
            case FLOODED_DEPTHS -> {
                if (shore || y <= fluidLevel + 4) {
                    return depth <= 3 ? (p > 0.25 ? Blocks.GRAVEL : Blocks.SAND).getDefaultState() : null;
                }
                if (depth == 1) {
                    if (p < -0.2) {
                        return Blocks.MOSS_BLOCK.getDefaultState();
                    }
                    if (p < 0.25) {
                        return Blocks.GRASS_BLOCK.getDefaultState();
                    }
                    return r < 0.5 ? Blocks.DRIPSTONE_BLOCK.getDefaultState() : null;
                }
                return depth <= 2 && p < 0.25 ? Blocks.DIRT.getDefaultState() : null;
            }
            case SUNKEN_GARDENS -> {
                if (depth == 1) {
                    return p < 0 ? Blocks.SAND.getDefaultState() : Blocks.MOSS_BLOCK.getDefaultState();
                }
                return depth <= 2 ? Blocks.SAND.getDefaultState() : null;
            }
            case ASHEN_HOLLOWS -> {
                if (depth == 1) {
                    if (p < -0.35) {
                        return Blocks.GRAVEL.getDefaultState();
                    }
                    if (p > 0.55 && r < 0.4) {
                        return Blocks.MAGMA_BLOCK.getDefaultState();
                    }
                    if (p > 0.2) {
                        return Blocks.SOUL_SOIL.getDefaultState();
                    }
                    return (r < 0.5 ? Blocks.BLACKSTONE : Blocks.BASALT).getDefaultState();
                }
                return depth <= 2 ? Blocks.BLACKSTONE.getDefaultState() : null;
            }
            case CRIMSON_BASIN -> {
                if (depth == 1) {
                    if (p < -0.3) {
                        return Blocks.RED_SAND.getDefaultState();
                    }
                    if (p > 0.45) {
                        return Blocks.NETHER_WART_BLOCK.getDefaultState();
                    }
                    return Blocks.CRIMSON_NYLIUM.getDefaultState();
                }
                return depth <= 3 ? Blocks.NETHERRACK.getDefaultState() : null;
            }
            case CRYSTAL_DEEP -> {
                if (depth == 1) {
                    if (r < 0.025) {
                        return Blocks.AMETHYST_BLOCK.getDefaultState();
                    }
                    if (p < -0.05) {
                        return Blocks.CALCITE.getDefaultState();
                    }
                    if (p < 0.35) {
                        return Blocks.TUFF.getDefaultState();
                    }
                    return Blocks.SMOOTH_BASALT.getDefaultState();
                }
                return depth <= 2 ? Blocks.CALCITE.getDefaultState() : null;
            }
            case STARLIGHT_CAVERNS -> {
                if (shore) {
                    return depth <= 2 ? Blocks.SAND.getDefaultState() : null;
                }
                if (depth == 1) {
                    if (p < -0.4) {
                        return Blocks.CALCITE.getDefaultState();
                    }
                    return p < 0.1 ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.MOSS_BLOCK.getDefaultState();
                }
                return depth <= 3 ? Blocks.DIRT.getDefaultState() : null;
            }
            case EVERFROST -> {
                if (depth == 1) {
                    if (p > 0.5) {
                        return Blocks.PACKED_ICE.getDefaultState();
                    }
                    return r < 0.04 ? Blocks.POWDER_SNOW.getDefaultState() : Blocks.SNOW_BLOCK.getDefaultState();
                }
                return depth <= 2 ? Blocks.SNOW_BLOCK.getDefaultState() : null;
            }
            case HOLLOW_PEAKS -> {
                if (depth == 1) {
                    if (p < -0.3) {
                        return Blocks.GRAVEL.getDefaultState();
                    }
                    if (p < 0.1) {
                        return Blocks.GRASS_BLOCK.getDefaultState();
                    }
                    return p > 0.5 ? Blocks.MOSS_BLOCK.getDefaultState() : null;
                }
                return depth == 2 && p < 0.1 ? Blocks.DIRT.getDefaultState() : null;
            }
            case PETRIFIED_FOREST -> {
                if (depth == 1) {
                    if (p < -0.25) {
                        return Blocks.COARSE_DIRT.getDefaultState();
                    }
                    if (p < 0.2) {
                        return Blocks.GRAVEL.getDefaultState();
                    }
                    return r < 0.6 ? Blocks.PACKED_MUD.getDefaultState() : Blocks.TUFF.getDefaultState();
                }
                return depth <= 2 ? Blocks.COARSE_DIRT.getDefaultState() : null;
            }
            case STORMSTONE_CAVERNS -> {
                if (depth == 1) {
                    if (p > 0.4) {
                        return Blocks.AMETHYST_BLOCK.getDefaultState();
                    }
                    return p < -0.3 ? Blocks.TUFF.getDefaultState() : ModBlocks.STORMSTONE.getDefaultState();
                }
                return null;
            }
            case THE_ABYSS -> {
                if (depth == 1) {
                    if (p > 0.22) {
                        return Blocks.SCULK.getDefaultState();
                    }
                    if (p < -0.35) {
                        return Blocks.COBBLED_DEEPSLATE.getDefaultState();
                    }
                    return r < 0.1 ? Blocks.TUFF.getDefaultState() : Blocks.DEEPSLATE.getDefaultState();
                }
                return null;
            }
        }
        return null;
    }

    private static BlockState ceiling(long seed, RegionType region, int x, int y, int z, double p) {
        double r = rnd(seed, x, y, z ^ 0x5A5A);
        return switch (region) {
            case ROOTWILD -> p > 0.3 ? Blocks.ROOTED_DIRT.getDefaultState()
                    : (p < -0.35 ? Blocks.MOSS_BLOCK.getDefaultState() : null);
            case GOLDEN_GROVE -> r < 0.02 ? ModBlocks.AMBER_MOSS.getDefaultState()
                    : (p > 0.3 ? Blocks.ROOTED_DIRT.getDefaultState() : null);
            case EMERALD_WETLANDS -> p < -0.1 ? Blocks.MOSS_BLOCK.getDefaultState() : null;
            case FUNGAL_EXPANSE -> {
                if (r < 0.015) {
                    yield Blocks.SHROOMLIGHT.getDefaultState();
                }
                yield p > 0.12 ? Blocks.WARPED_WART_BLOCK.getDefaultState()
                        : (p < -0.25 ? Blocks.BROWN_MUSHROOM_BLOCK.getDefaultState() : null);
            }
            case CRIMSON_BASIN -> r < 0.01 ? Blocks.SHROOMLIGHT.getDefaultState()
                    : (p > -0.2 ? Blocks.NETHER_WART_BLOCK.getDefaultState() : Blocks.NETHERRACK.getDefaultState());
            case FLOODED_DEPTHS -> p > 0.2 ? Blocks.DRIPSTONE_BLOCK.getDefaultState() : null;
            case SUNKEN_GARDENS -> p > 0.3 ? Blocks.PRISMARINE.getDefaultState() : null;
            case ASHEN_HOLLOWS -> p > 0.0 ? Blocks.BASALT.getDefaultState() : Blocks.BLACKSTONE.getDefaultState();
            case CRYSTAL_DEEP -> r < 0.05 ? Blocks.AMETHYST_BLOCK.getDefaultState()
                    : (r < 0.065 ? ModBlocks.LUMINITE_BLOCK.getDefaultState() : (p > 0.1 ? Blocks.CALCITE.getDefaultState() : null));
            case STARLIGHT_CAVERNS -> r < 0.035 + 0.03 * Math.max(0, p) ? ModBlocks.STARSTONE.getDefaultState()
                    : (p > 0.2 ? Blocks.CALCITE.getDefaultState() : null);
            case EVERFROST -> p > -0.2 ? Blocks.PACKED_ICE.getDefaultState() : Blocks.SNOW_BLOCK.getDefaultState();
            case HOLLOW_PEAKS -> null;
            case PETRIFIED_FOREST -> r < 0.01 ? ModBlocks.AMBER_RESIN.getDefaultState() : null;
            case STORMSTONE_CAVERNS -> r < 0.03 ? ModBlocks.CHARGED_STORMSTONE.getDefaultState()
                    : (p > 0.1 ? Blocks.AMETHYST_BLOCK.getDefaultState() : null);
            case THE_ABYSS -> p > 0.35 ? Blocks.SCULK.getDefaultState() : null;
        };
    }
}
