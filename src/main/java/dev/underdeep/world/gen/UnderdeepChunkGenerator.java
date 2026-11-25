package dev.underdeep.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The Underdeep chunk generator. Terrain comes from {@link UnderdeepTerrain}; surfaces are painted
 * by {@link SurfacePainter}; vegetation, ores and other decoration come from biome features through
 * the standard vanilla feature pipeline; initial wildlife comes from {@link InitialPopulation}.
 */
public final class UnderdeepChunkGenerator extends ChunkGenerator {
    public static final MapCodec<UnderdeepChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Codec.INT.optionalFieldOf("generator_version", UnderdeepTerrain.GENERATOR_VERSION).forGetter(g -> g.generatorVersion)
    ).apply(instance, instance.stable(UnderdeepChunkGenerator::new)));

    private static final int GRID = 4;
    private static final int GRID_Y = UnderdeepTerrain.HEIGHT / GRID + 1;

    private final int generatorVersion;

    public UnderdeepChunkGenerator(BiomeSource biomeSource, int generatorVersion) {
        super(biomeSource);
        this.generatorVersion = generatorVersion;
    }

    public int generatorVersion() {
        return generatorVersion;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Override
    public StructurePlacementCalculator createStructurePlacementCalculator(RegistryWrapper<StructureSet> structureSetRegistry,
                                                                          NoiseConfig noiseConfig, long seed) {
        // Called once when a world's chunk manager is constructed: the only place vanilla hands a
        // generator both the seed and the world's NoiseConfig.
        UnderdeepWorlds.bind(noiseConfig, seed);
        return super.createStructurePlacementCalculator(structureSetRegistry, noiseConfig, seed);
    }

    // ------------------------------------------------------------------------------------------
    // Terrain fill

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        UnderdeepTerrain terrain = UnderdeepWorlds.terrain(noiseConfig);
        return CompletableFuture.supplyAsync(Util.debugSupplier("underdeep_fill", () -> {
            int bottom = chunk.getBottomSectionCoord();
            int count = chunk.countVerticalSections();
            ChunkSection[] sections = new ChunkSection[count];
            for (int i = 0; i < count; i++) {
                sections[i] = chunk.getSection(i);
                sections[i].lock();
            }
            try {
                fill(terrain, chunk, sections, bottom);
            } finally {
                for (ChunkSection s : sections) {
                    s.unlock();
                }
            }
            Heightmap.populateHeightmaps(chunk, EnumSet.of(Heightmap.Type.OCEAN_FLOOR_WG, Heightmap.Type.WORLD_SURFACE_WG));
            return chunk;
        }), Util.getMainWorkerExecutor());
    }

    private static final BlockState BEDROCK = Blocks.BEDROCK.getDefaultState();
    private static final BlockState WATER = Blocks.WATER.getDefaultState();
    private static final BlockState LAVA = Blocks.LAVA.getDefaultState();

    /**
     * Fills one chunk. Column values are exact per block column; 3D noise is sampled on a 4-block
     * grid and trilinearly interpolated.
     */
    public static void fill(UnderdeepTerrain t, Chunk chunk, ChunkSection[] sections, int bottomSectionCoord) {
        ChunkPos cp = chunk.getPos();
        int x0 = cp.getStartX();
        int z0 = cp.getStartZ();
        TerrainColumn[] cols = new TerrainColumn[256];
        double[] strata = new double[256];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                cols[lx * 16 + lz] = t.column(x0 + lx, z0 + lz);
                strata[lx * 16 + lz] = t.strataOffset(x0 + lx, z0 + lz);
            }
        }
        int g = 16 / GRID + 1;
        double[] rough = new double[g * g * GRID_Y];
        double[] tun = new double[g * g * GRID_Y];
        for (int gx = 0; gx < g; gx++) {
            for (int gz = 0; gz < g; gz++) {
                int x = x0 + gx * GRID;
                int z = z0 + gz * GRID;
                for (int gy = 0; gy < GRID_Y; gy++) {
                    int y = UnderdeepTerrain.MIN_Y + gy * GRID;
                    int idx = (gx * g + gz) * GRID_Y + gy;
                    rough[idx] = t.roughness(x, y, z);
                    tun[idx] = t.tunnels(x, y, z);
                }
            }
        }
        long seed = t.seed();
        for (int lx = 0; lx < 16; lx++) {
            int gx = lx / GRID;
            double fx = (lx % GRID) / (double) GRID;
            for (int lz = 0; lz < 16; lz++) {
                int gz = lz / GRID;
                double fz = (lz % GRID) / (double) GRID;
                TerrainColumn c = cols[lx * 16 + lz];
                double strataOff = strata[lx * 16 + lz];
                int i00 = (gx * g + gz) * GRID_Y;
                int i10 = ((gx + 1) * g + gz) * GRID_Y;
                int i01 = (gx * g + gz + 1) * GRID_Y;
                int i11 = ((gx + 1) * g + gz + 1) * GRID_Y;
                for (int y = UnderdeepTerrain.MIN_Y; y < UnderdeepTerrain.TOP_Y; y++) {
                    int gy = (y - UnderdeepTerrain.MIN_Y) / GRID;
                    double fy = ((y - UnderdeepTerrain.MIN_Y) % GRID) / (double) GRID;
                    double base = t.baseDensity(c, y);
                    boolean upper = y >= c.separation;
                    double amp = (upper ? c.upperProfile : c.deepProfile).roughness;
                    double r = 0;
                    if (base > -amp - 1 && base < amp + 1) {
                        r = tri(rough, i00, i10, i01, i11, gy, fx, fy, fz);
                    }
                    double tn = -100;
                    if (t.tunnelsAllowed(c, y)) {
                        tn = tri(tun, i00, i10, i01, i11, gy, fx, fy, fz);
                    }
                    double d = t.density(c, y, r, tn);
                    UnderdeepTerrain.Material m = t.classify(c, y, d);
                    BlockState state;
                    switch (m) {
                        case AIR -> {
                            continue;
                        }
                        case WATER -> state = WATER;
                        case LAVA -> state = LAVA;
                        case BEDROCK -> state = BEDROCK;
                        default -> {
                            boolean deep = y < c.separation - 2 + strataOff * 0.4;
                            RegionType region = deep ? c.deepRegion.primary : c.upperRegion.primary;
                            state = BlockPalette.rock(seed, region, deep, y, strataOff);
                        }
                    }
                    int si = (y >> 4) - bottomSectionCoord;
                    sections[si].setBlockState(lx, y & 15, lz, state, false);
                }
            }
        }
    }

    private static double tri(double[] a, int i00, int i10, int i01, int i11, int gy, double fx, double fy, double fz) {
        int gy1 = Math.min(gy + 1, GRID_Y - 1);
        double c00 = a[i00 + gy] + (a[i00 + gy1] - a[i00 + gy]) * fy;
        double c10 = a[i10 + gy] + (a[i10 + gy1] - a[i10 + gy]) * fy;
        double c01 = a[i01 + gy] + (a[i01 + gy1] - a[i01 + gy]) * fy;
        double c11 = a[i11 + gy] + (a[i11 + gy1] - a[i11 + gy]) * fy;
        double c0 = c00 + (c10 - c00) * fx;
        double c1 = c01 + (c11 - c01) * fx;
        return c0 + (c1 - c0) * fz;
    }

    // ------------------------------------------------------------------------------------------
    // Other generation stages

    @Override
    public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess,
                      StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carverStep) {
        // Caves are part of the terrain model; vanilla carvers are intentionally not used.
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
        SurfacePainter.paint(UnderdeepWorlds.terrain(noiseConfig), chunk);
    }

    @Override
    public void populateEntities(ChunkRegion region) {
        InitialPopulation.populate(region, this);
    }

    @Override
    public int getWorldHeight() {
        return UnderdeepTerrain.HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return UnderdeepTerrain.SEA_LEVEL;
    }

    @Override
    public int getMinimumY() {
        return UnderdeepTerrain.MIN_Y;
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        // The world is sealed by a bedrock roof: the topmost non-air block of every column is the roof.
        UnderdeepTerrain t = UnderdeepWorlds.terrain(noiseConfig);
        BlockState[] states = columnStates(t, x, z);
        for (int i = states.length - 1; i >= 0; i--) {
            if (heightmap.getBlockPredicate().test(states[i])) {
                return UnderdeepTerrain.MIN_Y + i + 1;
            }
        }
        return world.getBottomY();
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        return new VerticalBlockSample(UnderdeepTerrain.MIN_Y, columnStates(UnderdeepWorlds.terrain(noiseConfig), x, z));
    }

    /** Unpainted terrain blocks of a column (exact 3D noise, no interpolation). */
    public static BlockState[] columnStates(UnderdeepTerrain t, int x, int z) {
        TerrainColumn c = t.column(x, z);
        double strataOff = t.strataOffset(x, z);
        BlockState[] states = new BlockState[UnderdeepTerrain.HEIGHT];
        for (int y = UnderdeepTerrain.MIN_Y; y < UnderdeepTerrain.TOP_Y; y++) {
            double d = t.density(c, y, t.roughness(x, y, z), t.tunnelsAllowed(c, y) ? t.tunnels(x, y, z) : -100);
            BlockState s = switch (t.classify(c, y, d)) {
                case AIR -> Blocks.AIR.getDefaultState();
                case WATER -> WATER;
                case LAVA -> LAVA;
                case BEDROCK -> BEDROCK;
                case STONE -> {
                    boolean deep = y < c.separation - 2 + strataOff * 0.4;
                    yield BlockPalette.rock(t.seed(), deep ? c.deepRegion.primary : c.upperRegion.primary, deep, y, strataOff);
                }
            };
            states[y - UnderdeepTerrain.MIN_Y] = s;
        }
        return states;
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        UnderdeepTerrain t = UnderdeepWorlds.terrain(noiseConfig);
        TerrainColumn c = t.column(pos.getX(), pos.getZ());
        CavernLayer layer = c.layerAt(pos.getY());
        text.add(String.format("Underdeep: %s layer, region %s (%s)", layer.id, c.regionAt(pos.getY()).id(),
                (layer == CavernLayer.UPPER ? c.upperRegion : c.deepRegion).id()));
        text.add(String.format("Upper floor %.0f ceil %.0f | Deep floor %.0f ceil %.0f | sep %.0f",
                c.upperFloor, c.upperCeiling, c.deepFloor, c.deepCeiling, c.separation));
    }
}
