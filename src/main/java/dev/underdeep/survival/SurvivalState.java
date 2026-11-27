package dev.underdeep.survival;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;

/**
 * World-level Stage 7 state: fishing pressure per water area (sustainability). Keys are append-only.
 */
public final class SurvivalState extends PersistentState {
    public static final String KEY = "underdeep_survival";
    /** Fishing pressure per area key ("region:cellX:cellZ"); decays daily. */
    public final Map<String, Float> pressure = new HashMap<>();
    public long lastDecayDay = -1;

    public static SurvivalState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                new PersistentState.Type<>(SurvivalState::new, SurvivalState::fromNbt, null), KEY);
    }

    static SurvivalState fromNbt(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
        SurvivalState s = new SurvivalState();
        NbtCompound p = n.getCompound("pressure");
        for (String k : p.getKeys()) {
            s.pressure.put(k, p.getFloat(k));
        }
        s.lastDecayDay = n.getLong("decayDay");
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
        NbtCompound p = new NbtCompound();
        pressure.forEach(p::putFloat);
        n.put("pressure", p);
        n.putLong("decayDay", lastDecayDay);
        n.putInt("schema", 1);
        return n;
    }

    /** Daily recovery of fished waters. */
    public void decay(long day) {
        if (lastDecayDay < 0) {
            lastDecayDay = day;
            return;
        }
        long d = Math.min(30, day - lastDecayDay);
        if (d <= 0) {
            return;
        }
        lastDecayDay = day;
        pressure.replaceAll((k, v) -> Math.max(0, v - 10f * d));
        pressure.values().removeIf(v -> v <= 0);
        markDirty();
    }
}
