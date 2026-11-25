package dev.underdeep.world.spawn;

import dev.underdeep.Underdeep;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses and validates the world spawn inside the guaranteed starting cavern.
 * <p>
 * The search spirals outward from the origin over generated chunks (synchronously, only during world
 * creation or on explicit command) and accepts the first position whose 3x3 footprint is solid,
 * dry and lava-free with at least three blocks of headroom. If nothing qualifies, a safe pocket is
 * created at the origin as a last resort (logged as a warning).
 */
public final class SpawnFinder {
    private SpawnFinder() {
    }

    /** Result of validating a spawn position. */
    public record Validation(boolean safe, List<String> problems) {
    }

    public static BlockPos findSpawn(ServerWorld world) {
        UnderdeepTerrain t = UnderdeepWorlds.terrain(world);
        int radius = Math.max(16, UnderdeepConfig.get().spawnSearchRadius);
        for (int r = 0; r <= radius; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    BlockPos p = candidate(world, t, dx, dz);
                    if (p != null) {
                        int lit = lightSpawn(world, p);
                        Underdeep.LOGGER.info("Underdeep spawn selected at {} ({} glowcaps planted around it)", p, lit);
                        return p;
                    }
                }
            }
        }
        Underdeep.LOGGER.warn("No natural safe spawn found within {} blocks; creating a safe pocket at the origin", radius);
        return createPocket(world, t);
    }

    /**
     * Plants glowcaps on the floor around the spawn so the arrival point is lit (hostile mobs only
     * spawn at block light 0). Deterministic for a given world seed.
     */
    public static int lightSpawn(ServerWorld world, BlockPos spawn) {
        net.minecraft.util.math.random.Random random = net.minecraft.util.math.random.Random.create(world.getSeed() ^ 0x5EED5EEDL);
        BlockPos.Mutable m = new BlockPos.Mutable();
        int placed = 0;
        for (int attempt = 0; attempt < 60 && placed < 7; attempt++) {
            double ang = random.nextDouble() * Math.PI * 2;
            double dist = 3 + random.nextDouble() * 6;
            int x = spawn.getX() + (int) Math.round(Math.cos(ang) * dist);
            int z = spawn.getZ() + (int) Math.round(Math.sin(ang) * dist);
            for (int y = spawn.getY() + 4; y >= spawn.getY() - 5; y--) {
                m.set(x, y, z);
                BlockState here = world.getBlockState(m);
                if (!(here.isAir() || here.isReplaceable()) || !here.getFluidState().isEmpty()) {
                    continue;
                }
                BlockState state = dev.underdeep.registry.ModBlocks.GLOWCAP.getDefaultState();
                if (state.canPlaceAt(world, m) && !world.getBlockState(m.down()).isReplaceable()) {
                    world.setBlockState(m, state, net.minecraft.block.Block.NOTIFY_ALL);
                    placed++;
                    break;
                }
            }
        }
        return placed;
    }

    private static BlockPos candidate(ServerWorld world, UnderdeepTerrain t, int x, int z) {
        TerrainColumn c = t != null ? t.column(x, z) : null;
        int top = c != null ? (int) Math.min(c.upperCeiling, 300) : 300;
        int bottom = c != null ? (int) Math.max(c.upperFloor - 6, UnderdeepTerrain.SEA_LEVEL) : 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int y = top; y >= bottom; y--) {
            m.set(x, y, z);
            if (validate(world, m).safe()) {
                return m.toImmutable();
            }
        }
        return null;
    }

    /** Checks that a player can safely stand at {@code feet}. */
    public static Validation validate(ServerWorld world, BlockPos feet) {
        List<String> problems = new ArrayList<>();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                m.set(feet.getX() + dx, feet.getY() - 1, feet.getZ() + dz);
                BlockState floor = world.getBlockState(m);
                if (!floor.isSideSolidFullSquare(world, m, Direction.UP) || floor.isIn(BlockTags.LEAVES)
                        || floor.isOf(Blocks.MAGMA_BLOCK) || !floor.getFluidState().isEmpty()) {
                    problems.add("unsafe floor at " + m.toShortString() + " (" + floor.getBlock().getTranslationKey() + ")");
                }
                for (int dy = 0; dy < 3; dy++) {
                    m.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                    BlockState s = world.getBlockState(m);
                    if (!s.getCollisionShape(world, m).isEmpty()) {
                        problems.add("obstructed at " + m.toShortString());
                    }
                    if (!s.getFluidState().isEmpty()) {
                        problems.add("fluid at " + m.toShortString());
                    }
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    m.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                    BlockState s = world.getBlockState(m);
                    if (s.isOf(Blocks.LAVA) || s.isOf(Blocks.FIRE) || s.isOf(Blocks.MAGMA_BLOCK)) {
                        problems.add("hazard near spawn at " + m.toShortString());
                        dx = dy = dz = 99;
                    }
                }
            }
        }
        // Prefer open ground with a view: no canopy or rock directly overhead.
        for (int dy = 3; dy < 10; dy++) {
            m.set(feet.getX(), feet.getY() + dy, feet.getZ());
            if (!world.getBlockState(m).isAir()) {
                problems.add("covered overhead at " + m.toShortString());
                break;
            }
        }
        if (feet.getY() <= world.getBottomY() + 5 || feet.getY() >= world.getTopY() - 6) {
            problems.add("outside the safe vertical range");
        }
        return new Validation(problems.isEmpty(), problems);
    }

    private static BlockPos createPocket(ServerWorld world, UnderdeepTerrain t) {
        int y = t != null ? (int) Math.max(t.column(0, 0).upperFloor + 1, UnderdeepTerrain.SEA_LEVEL + 4) : 150;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.setBlockState(m.set(dx, y - 1, dz), Blocks.STONE.getDefaultState());
                for (int dy = 0; dy < 4; dy++) {
                    world.setBlockState(m.set(dx, y + dy, dz), Blocks.AIR.getDefaultState());
                }
            }
        }
        return new BlockPos(0, y, 0);
    }
}
