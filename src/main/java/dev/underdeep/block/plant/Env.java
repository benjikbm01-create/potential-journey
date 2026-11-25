package dev.underdeep.block.plant;

import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.ecology.Species;
import dev.underdeep.registry.ModTags;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.region.RegionType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Environmental queries used by plant growth and natural processes. Every scan is a small bounded
 * box and is only evaluated after a random-tick chance roll, so the cost per loaded chunk is tiny.
 */
public final class Env {
    public enum Temp { COLD, TEMPERATE, HOT }

    private static final Map<String, RegionType> BY_ID = new HashMap<>();

    static {
        for (RegionType t : RegionType.values()) {
            BY_ID.put(t.id(), t);
        }
    }

    private Env() {
    }

    public static boolean isUnderdeep(World world) {
        return world instanceof ServerWorld sw && UnderdeepWorlds.isUnderdeep(sw);
    }

    /** Ecosystem at a position (from the biome), or null outside Underdeep biomes. */
    @Nullable
    public static RegionType region(WorldView world, BlockPos pos) {
        return world.getBiome(pos).getKey()
                .filter(k -> k.getValue().getNamespace().equals("underdeep"))
                .map(k -> BY_ID.get(k.getValue().getPath()))
                .orElse(null);
    }

    public static Temp temperature(WorldView world, BlockPos pos) {
        RegionType r = region(world, pos);
        double t;
        if (r != null) {
            t = r.environment().temperature();
            return t < 0.2 ? Temp.COLD : t > 0.75 ? Temp.HOT : Temp.TEMPERATE;
        }
        float bt = world.getBiome(pos).value().getTemperature();
        return bt < 0.15f ? Temp.COLD : bt > 1.0f ? Temp.HOT : Temp.TEMPERATE;
    }

    /** Growth multiplier for a climate preference at a position: 1 native, 0.35 tolerated, 0 dormant. */
    public static double climateFactor(Species.Climate pref, WorldView world, BlockPos pos) {
        if (pref == Species.Climate.ANY) {
            return 1.0;
        }
        Temp t = temperature(world, pos);
        int want = switch (pref) {
            case COLD -> 0;
            case TEMPERATE -> 1;
            case HOT -> 2;
            default -> 1;
        };
        int d = Math.abs(want - t.ordinal());
        return d == 0 ? 1.0 : d == 1 ? 0.35 : 0.0;
    }

    public static int light(WorldView world, BlockPos pos) {
        return world.getLightLevel(pos);
    }

    public static boolean any(WorldView world, BlockPos c, int r, int dy, Predicate<BlockState> p) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -dy; y <= dy; y++) {
                    m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                    if (p.test(world.getBlockState(m))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static int count(WorldView world, BlockPos c, int r, int dy, Predicate<BlockState> p) {
        int n = 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -dy; y <= dy; y++) {
                    m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                    if (p.test(world.getBlockState(m))) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    public static boolean isHeat(BlockState s) {
        return s.getFluidState().isIn(FluidTags.LAVA) || s.isOf(Blocks.MAGMA_BLOCK) || s.isIn(BlockTags.FIRE)
                || (s.getBlock() instanceof CampfireBlock && s.get(CampfireBlock.LIT)) || s.isOf(Blocks.LAVA_CAULDRON);
    }

    public static boolean nearHeat(WorldView world, BlockPos pos, int r) {
        return any(world, pos, r, 2, Env::isHeat);
    }

    public static boolean nearWater(WorldView world, BlockPos pos, int r) {
        return any(world, pos, r, 1, s -> s.getFluidState().isIn(FluidTags.WATER));
    }

    /** True when player-made blocks (tag {@code underdeep:growth_blockers}) are within 2 blocks. */
    public static boolean protectedArea(WorldView world, BlockPos pos) {
        return any(world, pos, 2, 1, s -> s.isIn(ModTags.GROWTH_BLOCKERS));
    }

    /** Whether natural spreading may run in this world now. */
    public static boolean spreadingEnabled(World world) {
        UnderdeepConfig c = UnderdeepConfig.get();
        return c.naturalSpreading && c.spreadRateMultiplier > 0 && isUnderdeep(world);
    }

    public static boolean roll(Random random, double chance) {
        return random.nextDouble() < chance * UnderdeepConfig.get().spreadRateMultiplier;
    }
}
