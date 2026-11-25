package dev.underdeep.world.data;

import dev.underdeep.Underdeep;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent per-world Underdeep data, saved as {@code data/underdeep.dat} in the world folder.
 * <p>
 * Stage 1 stores the generator version used to create the world (to detect terrain-incompatible
 * upgrades) and provides the {@link #regionData} map keyed by {@link dev.underdeep.world.region.RegionId#asLong()},
 * reserved for the abstract regional simulation of later stages (wildlife populations, settlement
 * state, trade routes). Keep entries small: one compound per region, never per block.
 */
public final class UnderdeepWorldState extends PersistentState {
    public static final String ID = "underdeep";
    public static final PersistentState.Type<UnderdeepWorldState> TYPE =
            new PersistentState.Type<>(UnderdeepWorldState::new, UnderdeepWorldState::fromNbt,
                    // Vanilla requires a fixer type; command storage has no schema for our keys, so it is a no-op.
                    net.minecraft.datafixer.DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private int createdWithGeneratorVersion = UnderdeepTerrain.GENERATOR_VERSION;
    private long createdAtGameTime = -1;
    private final Map<Long, NbtCompound> regionData = new HashMap<>();

    public static UnderdeepWorldState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    private static UnderdeepWorldState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        UnderdeepWorldState s = new UnderdeepWorldState();
        s.createdWithGeneratorVersion = nbt.getInt("generator_version");
        s.createdAtGameTime = nbt.getLong("created_at");
        NbtCompound regions = nbt.getCompound("regions");
        for (String key : regions.getKeys()) {
            try {
                s.regionData.put(Long.parseLong(key), regions.getCompound(key));
            } catch (NumberFormatException e) {
                Underdeep.LOGGER.warn("Ignoring malformed region key {}", key);
            }
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putInt("generator_version", createdWithGeneratorVersion);
        nbt.putLong("created_at", createdAtGameTime);
        NbtCompound regions = new NbtCompound();
        regionData.forEach((k, v) -> regions.put(Long.toString(k), v));
        nbt.put("regions", regions);
        return nbt;
    }

    public int createdWithGeneratorVersion() {
        return createdWithGeneratorVersion;
    }

    public long createdAtGameTime() {
        return createdAtGameTime;
    }

    public void initializeIfNew(long gameTime) {
        if (createdAtGameTime < 0) {
            createdAtGameTime = gameTime;
            createdWithGeneratorVersion = UnderdeepTerrain.GENERATOR_VERSION;
            markDirty();
        }
    }

    /** Mutable per-region compound (created on demand). Call {@link #markDirty()} after changes. */
    public NbtCompound regionData(long regionId) {
        return regionData.computeIfAbsent(regionId, k -> new NbtCompound());
    }

    public int regionCount() {
        return regionData.size();
    }
}
