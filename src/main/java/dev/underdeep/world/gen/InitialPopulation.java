package dev.underdeep.world.gen;

import dev.underdeep.world.region.RegionType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.RandomSeed;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.chunk.Chunk;

import java.util.List;

/**
 * Initial animal herds placed when a chunk is generated.
 * <p>
 * Vanilla's {@code SpawnHelper.populateEntities} picks heights from the surface heightmap, which in
 * a roofed world is the bedrock ceiling, so Underdeep places herds itself on real cavern floors.
 * Stage 1 uses vanilla animals as a food source; Stage 3 replaces this with the ecological wildlife
 * simulation (see docs/ARCHITECTURE.md, "Wildlife population contract").
 */
public final class InitialPopulation {
    private InitialPopulation() {
    }

    private record Herd(EntityType<? extends MobEntity> type, int min, int max) {
    }

    private static final List<Herd> ROOTWILD = List.of(
            new Herd(EntityType.SHEEP, 2, 4), new Herd(EntityType.COW, 2, 3),
            new Herd(EntityType.PIG, 2, 3), new Herd(EntityType.CHICKEN, 2, 4),
            new Herd(EntityType.RABBIT, 2, 3));
    private static final List<Herd> FUNGAL = List.of(new Herd(EntityType.MOOSHROOM, 2, 3), new Herd(EntityType.PIG, 1, 2));
    private static final List<Herd> FLOODED = List.of(new Herd(EntityType.FROG, 2, 3), new Herd(EntityType.CHICKEN, 1, 3));

    public static void populate(ChunkRegion region, UnderdeepChunkGenerator generator) {
        ChunkPos cp = region.getCenterPos();
        ChunkRandom random = new ChunkRandom(new CheckedRandom(RandomSeed.getSeed()));
        random.setPopulationSeed(region.getSeed(), cp.getStartX(), cp.getStartZ());
        boolean nearSpawn = Math.abs(cp.getCenterX()) < 96 && Math.abs(cp.getCenterZ()) < 96;
        if (random.nextFloat() > (nearSpawn ? 0.5f : 0.09f)) {
            return;
        }
        Chunk chunk = region.getChunk(cp.x, cp.z);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int attempt = 0; attempt < 6; attempt++) {
            int x = cp.getStartX() + 2 + random.nextInt(12);
            int z = cp.getStartZ() + 2 + random.nextInt(12);
            int startY = 300 - random.nextInt(60);
            BlockPos floor = findFloor(chunk, pos, x, z, startY);
            if (floor == null) {
                continue;
            }
            BlockState ground = chunk.getBlockState(floor.down());
            RegionType type = null;
            var biome = region.getBiome(floor);
            for (RegionType r : RegionType.values()) {
                if (biome.matchesKey(UnderdeepBiomes.key(r))) {
                    type = r;
                }
            }
            List<Herd> herds = switch (type == null ? RegionType.THE_ABYSS : type) {
                case ROOTWILD -> ground.isOf(Blocks.GRASS_BLOCK) || ground.isOf(Blocks.MOSS_BLOCK) ? ROOTWILD : null;
                case FUNGAL_EXPANSE -> ground.isOf(Blocks.MYCELIUM) ? FUNGAL : null;
                case FLOODED_DEPTHS -> ground.isOf(Blocks.GRASS_BLOCK) || ground.isOf(Blocks.MOSS_BLOCK) ? FLOODED : null;
                default -> null;
            };
            if (herds == null) {
                continue;
            }
            Herd herd = herds.get(random.nextInt(herds.size()));
            int n = herd.min + random.nextInt(herd.max - herd.min + 1);
            for (int i = 0; i < n; i++) {
                int hx = Math.max(cp.getStartX(), Math.min(cp.getEndX(), floor.getX() + random.nextInt(5) - 2));
                int hz = Math.max(cp.getStartZ(), Math.min(cp.getEndZ(), floor.getZ() + random.nextInt(5) - 2));
                BlockPos spot = findFloor(chunk, pos, hx, hz, floor.getY() + 3);
                if (spot == null || Math.abs(spot.getY() - floor.getY()) > 3) {
                    spot = floor;
                }
                MobEntity mob = herd.type.create(region.toServerWorld());
                if (mob == null) {
                    continue;
                }
                mob.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360f, 0f);
                mob.initialize(region, region.getLocalDifficulty(spot), SpawnReason.CHUNK_GENERATION, null);
                mob.setPersistent();
                region.spawnEntityAndPassengers(mob);
            }
            return;
        }
    }

    /** First walkable floor at or below startY (solid below, two air blocks). */
    static BlockPos findFloor(Chunk chunk, BlockPos.Mutable pos, int x, int z, int startY) {
        for (int y = startY; y > -60; y--) {
            BlockState s = chunk.getBlockState(pos.set(x, y, z));
            if (!s.isAir()) {
                continue;
            }
            BlockState below = chunk.getBlockState(pos.set(x, y - 1, z));
            BlockState above = chunk.getBlockState(pos.set(x, y + 1, z));
            if (above.isAir() && below.isOpaqueFullCube(chunk, pos) && below.getFluidState().isEmpty()) {
                return new BlockPos(x, y, z);
            }
        }
        return null;
    }
}
