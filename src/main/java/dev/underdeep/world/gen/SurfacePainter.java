package dev.underdeep.world.gen;

import dev.underdeep.world.noise.Hashing;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.Set;

/**
 * Paints cavern floors and ceilings with region-specific ground cover (grass, moss, mycelium,
 * nylium, sand, clay, ash, sculk...). Runs in the SURFACE generation step on a single chunk, so it
 * never touches neighbouring chunks.
 */
public final class SurfacePainter {
    private static final Set<Block> PAINTABLE = Set.of(Blocks.STONE, Blocks.DEEPSLATE, Blocks.ANDESITE, Blocks.GRANITE,
            Blocks.DIORITE, Blocks.TUFF, Blocks.CALCITE, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.SMOOTH_BASALT,
            Blocks.DRIPSTONE_BLOCK);

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
                BlockState prev = chunk.getBlockState(pos.set(x, top + 1, z));
                for (int y = top; y >= bottom; y--) {
                    BlockState s = chunk.getBlockState(pos.set(x, y, z));
                    if (isOpen(s)) {
                        // Block above an opening is a ceiling.
                        if (!isOpen(prev) && PAINTABLE.contains(prev.getBlock()) && s.isAir()) {
                            BlockState ceil = ceiling(seed, c.regionAt(y + 1), x, y + 1, z, pb);
                            if (ceil != null) {
                                chunk.setBlockState(pos.set(x, y + 1, z), ceil, false);
                            }
                        }
                        depth = 0;
                        openWater = s.getFluidState().isOf(net.minecraft.fluid.Fluids.WATER);
                        openLava = s.getFluidState().isOf(net.minecraft.fluid.Fluids.LAVA);
                        prev = s;
                        continue;
                    }
                    if (depth >= 0) {
                        depth++;
                        if (depth <= 4 && PAINTABLE.contains(s.getBlock())) {
                            BlockState f = floor(seed, c, c.regionAt(y), x, y, z, depth, openWater, openLava, pa);
                            if (f != null) {
                                chunk.setBlockState(pos.set(x, y, z), f, false);
                                s = f;
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

    private static double rnd(long seed, int x, int y, int z) {
        return Hashing.unit(Hashing.hash(seed, x, y, z));
    }

    private static BlockState floor(long seed, TerrainColumn c, RegionType region, int x, int y, int z, int depth,
                                    boolean water, boolean lava, double p) {
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
            if (region == RegionType.ASHEN_HOLLOWS || region == RegionType.THE_ABYSS) {
                return Blocks.GRAVEL.getDefaultState();
            }
            if (p < -0.3) {
                return Blocks.CLAY.getDefaultState();
            }
            if (p < 0.05) {
                return Blocks.SAND.getDefaultState();
            }
            if (p < 0.35) {
                return Blocks.GRAVEL.getDefaultState();
            }
            return depth == 1 && region == RegionType.FLOODED_DEPTHS ? Blocks.MUD.getDefaultState() : Blocks.SAND.getDefaultState();
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
            case FUNGAL_EXPANSE -> {
                if (shore) {
                    return depth <= 2 ? Blocks.MUD.getDefaultState() : null;
                }
                boolean nylium = p > 0.0;
                if (depth == 1) {
                    if (r < 0.08) {
                        return Blocks.MOSS_BLOCK.getDefaultState();
                    }
                    if (p < 0) {
                        return Blocks.MYCELIUM.getDefaultState();
                    }
                    return (p < 0.38 ? Blocks.WARPED_NYLIUM : Blocks.CRIMSON_NYLIUM).getDefaultState();
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
            default -> {
                return null;
            }
        }
    }

    private static BlockState ceiling(long seed, RegionType region, int x, int y, int z, double p) {
        double r = rnd(seed, x, y, z ^ 0x5A5A);
        return switch (region) {
            case ROOTWILD -> p > 0.3 ? Blocks.ROOTED_DIRT.getDefaultState()
                    : (p < -0.35 ? Blocks.MOSS_BLOCK.getDefaultState() : null);
            case FUNGAL_EXPANSE -> {
                if (r < 0.015) {
                    yield Blocks.SHROOMLIGHT.getDefaultState();
                }
                yield p > 0.12 ? Blocks.NETHER_WART_BLOCK.getDefaultState()
                        : (p < -0.25 ? Blocks.WARPED_WART_BLOCK.getDefaultState() : null);
            }
            case FLOODED_DEPTHS -> p > 0.2 ? Blocks.DRIPSTONE_BLOCK.getDefaultState() : null;
            case ASHEN_HOLLOWS -> p > 0.0 ? Blocks.BASALT.getDefaultState() : Blocks.BLACKSTONE.getDefaultState();
            case CRYSTAL_DEEP -> r < 0.05 ? Blocks.AMETHYST_BLOCK.getDefaultState()
                    : (p > 0.1 ? Blocks.CALCITE.getDefaultState() : null);
            case THE_ABYSS -> p > 0.35 ? Blocks.SCULK.getDefaultState() : null;
        };
    }
}
