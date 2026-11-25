package dev.underdeep.ecology;

import dev.underdeep.Underdeep;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Stage 3 integration API: lets creature AI find food, shelter and nesting sites from the species
 * metadata without new block tags or per-block data. All searches are bounded (radius ≤ 16) and meant
 * to be called rarely (e.g. when a goal starts), never every tick.
 */
public final class Habitats {
    private Habitats() {
    }

    /** Species represented by a block state, or null for non-Underdeep / non-ecological blocks. */
    @Nullable
    public static Species speciesOf(BlockState state) {
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        if (!Underdeep.MOD_ID.equals(id.getNamespace())) {
            return null;
        }
        return EcologyRegistry.byBlock(id.getPath());
    }

    public static boolean provides(BlockState state, Species.Role role) {
        Species s = speciesOf(state);
        return s != null && s.has(role);
    }

    /**
     * Nearest block (squared distance) within a cube of the given radius whose species matches.
     * Cost: (2r+1)^2 * (2v+1) block reads; keep radius small.
     */
    @Nullable
    public static BlockPos findNearest(World world, BlockPos origin, int radius, int vertical, Predicate<Species> filter) {
        int r = Math.min(radius, 16);
        int v = Math.min(vertical, 8);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -v; dy <= v; dy++) {
                    m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!world.isChunkLoaded(m)) {
                        continue;
                    }
                    Species s = speciesOf(world.getBlockState(m));
                    if (s != null && filter.test(s)) {
                        double d = dx * dx + dy * dy + dz * dz;
                        if (d < bestD) {
                            bestD = d;
                            best = m.toImmutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    public static Predicate<Species> role(Species.Role role) {
        return s -> s.has(role);
    }
}
