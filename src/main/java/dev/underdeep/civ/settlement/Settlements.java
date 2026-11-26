package dev.underdeep.civ.settlement;

import dev.underdeep.Underdeep;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.data.UnderdeepWorldState;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.StructureWorldAccess;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-world access to the settlement map. Settlements generate only in worlds created with terrain
 * generator v3 or later (Stage 4), so existing Stage 1–3 worlds never receive half-built settlements at the
 * borders of already explored chunks (unless the server enables {@code settlementsInExistingWorlds}).
 */
public final class Settlements {
    public static final int SETTLEMENTS_SINCE_VERSION = 3;
    private static final Map<UnderdeepTerrain, SettlementMap> MAPS = Collections.synchronizedMap(new WeakHashMap<>());

    private Settlements() {
    }

    public static void init() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() != World.OVERWORLD || !UnderdeepWorlds.isUnderdeep(world)) {
                return;
            }
            UnderdeepTerrain t = UnderdeepWorlds.terrain(world);
            if (t == null) {
                return;
            }
            UnderdeepWorldState state = UnderdeepWorldState.get(world);
            boolean fresh = state.createdAtGameTime() < 0;
            int version = fresh ? UnderdeepTerrain.GENERATOR_VERSION : state.createdWithGeneratorVersion();
            boolean enabled = UnderdeepConfig.get().settlementsEnabled
                    && (version >= SETTLEMENTS_SINCE_VERSION || UnderdeepConfig.get().settlementsInExistingWorlds);
            if (enabled) {
                MAPS.put(t, new SettlementMap(t));
            } else {
                MAPS.remove(t);
            }
            Underdeep.LOGGER.info("Underdeep settlements {} for this world (created with generator v{})",
                    enabled ? "enabled" : "disabled", version);
        });
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            UnderdeepTerrain t = UnderdeepWorlds.terrain(world);
            if (t != null) {
                MAPS.remove(t);
            }
        });
    }

    /** Test/diagnostic hook: force a map for a terrain. */
    public static SettlementMap enableFor(UnderdeepTerrain t) {
        return MAPS.computeIfAbsent(t, SettlementMap::new);
    }

    @Nullable
    public static SettlementMap of(@Nullable UnderdeepTerrain t) {
        return t == null ? null : MAPS.get(t);
    }

    @Nullable
    public static SettlementMap of(WorldAccess world) {
        ServerWorld sw = world instanceof ServerWorld s ? s : world instanceof StructureWorldAccess swa ? swa.toServerWorld() : null;
        if (sw == null || !UnderdeepWorlds.isUnderdeep(sw)) {
            return null;
        }
        return of(UnderdeepWorlds.terrain(sw));
    }
}
