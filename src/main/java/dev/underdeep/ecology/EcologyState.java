package dev.underdeep.ecology;

import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.analysis.RegionReport;
import dev.underdeep.world.data.UnderdeepWorldState;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Lightweight dynamic ecological data per region, persisted inside {@link UnderdeepWorldState}'s
 * per-region compounds (key {@value #KEY}). Stage 2 records only player harvest pressure per species
 * and the last game time the region was touched. Later stages read this to model depletion and
 * recovery of unloaded regions abstractly (Stage 3 wildlife food supply, Stage 6 trade), instead of
 * simulating plants block by block.
 * <p>
 * Persistent contract (append-only): compound {@code ecology} with int entries {@code h.<speciesId>}
 * and long {@code last}.
 */
public final class EcologyState {
    public static final String KEY = "ecology";

    private EcologyState() {
    }

    /** Records one harvest of a species at a position (no-op outside Underdeep worlds). */
    public static void recordHarvest(ServerWorld world, BlockPos pos, Species species) {
        if (species == null) {
            return;
        }
        UnderdeepTerrain t = UnderdeepWorlds.terrain(world);
        if (t == null) {
            return;
        }
        RegionReport r = RegionReport.at(t, pos.getX(), pos.getY(), pos.getZ());
        UnderdeepWorldState state = UnderdeepWorldState.get(world);
        NbtCompound eco = state.regionData(r.regionId().asLong()).getCompound(KEY);
        String k = "h." + species.id();
        eco.putInt(k, eco.getInt(k) + 1);
        eco.putLong("last", world.getTime());
        state.regionData(r.regionId().asLong()).put(KEY, eco);
        state.markDirty();
    }

    /** Harvest count of a species in a region (0 if never harvested). */
    public static int harvests(ServerWorld world, long regionId, String speciesId) {
        return UnderdeepWorldState.get(world).regionData(regionId).getCompound(KEY).getInt("h." + speciesId);
    }
}
