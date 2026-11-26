package dev.underdeep.world.spawn;

import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.world.gen.UnderdeepBiomes;
import dev.underdeep.world.region.Danger;
import dev.underdeep.world.region.RegionType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Natural hostile-spawn policy for Underdeep worlds.
 * <p>
 * Vanilla spawns monsters wherever block light is 0, which in a world without sun means everywhere.
 * Underdeep instead gates every natural monster spawn attempt by the ecosystem's {@link Danger}:
 * peaceful ecosystems have no monster spawn entries at all (biome JSON), and the remaining ones pass
 * only a configurable fraction of attempts. The starter cavern is a hard no-spawn zone. Spawn costs in
 * the biome JSON additionally cap local density so giant caverns cannot accumulate hordes.
 * <p>
 * This only affects <em>natural</em> spawning (the chunk generator's spawn list). Spawners, commands,
 * spawn eggs, structures and future scripted encounters are unaffected.
 */
public final class SpawnRules {
    private SpawnRules() {
    }

    public static RegionType regionOf(RegistryEntry<Biome> biome) {
        for (RegionType t : RegionType.values()) {
            if (biome.matchesKey(UnderdeepBiomes.key(t))) {
                return t;
            }
        }
        return null;
    }

    /** Whether the position lies in the protected starter cavern. */
    public static boolean inStarterZone(BlockPos pos) {
        int r = UnderdeepConfig.get().starterSafeRadius;
        long dx = pos.getX();
        long dz = pos.getZ();
        return dx * dx + dz * dz <= (long) r * r && pos.getY() > 90;
    }

    private static final ThreadLocal<dev.underdeep.world.region.RegionSample> SAMPLE =
            ThreadLocal.withInitial(dev.underdeep.world.region.RegionSample::new);

    /** Whether a peaceful (low-danger) ecosystem has blend weight at this position. */
    public static boolean nearPeaceful(dev.underdeep.world.terrain.UnderdeepTerrain terrain, BlockPos pos) {
        var col = terrain.column(pos.getX(), pos.getZ());
        var layer = col.layerAt(pos.getY());
        var s = terrain.regions(layer).sample(pos.getX(), pos.getZ(), SAMPLE.get());
        for (RegionType t : RegionType.values()) {
            if (t.danger() == Danger.LOW && s.weights[t.ordinal()] > 0) {
                return true;
            }
        }
        return false;
    }

    public static double monsterChance(Danger danger) {
        UnderdeepConfig c = UnderdeepConfig.get();
        return switch (danger) {
            case LOW -> c.monsterSpawnChanceLow;
            case MEDIUM -> c.monsterSpawnChanceMedium;
            case HIGH -> c.monsterSpawnChanceHigh;
            case EXTREME -> c.monsterSpawnChanceExtreme;
        };
    }

    /** Decides one natural monster spawn attempt. */
    public static boolean allowMonster(dev.underdeep.world.terrain.UnderdeepTerrain terrain, RegistryEntry<Biome> biome, BlockPos pos) {
        if (inStarterZone(pos)) {
            return false;
        }
        RegionType region = regionOf(biome);
        if (region == null) {
            return true;
        }
        if (terrain != null && nearPeaceful(terrain, pos)) {
            // Buffer: no natural hostiles within the ~34-block blend zone of a peaceful ecosystem, so
            // mobs from neighbouring regions do not wander into peaceful ones.
            return false;
        }
        double chance = monsterChance(region.danger());
        var cfg = dev.underdeep.config.UnderdeepConfig.get();
        if (cfg.wildlifeEnabled && cfg.vanillaMobReplacement) {
            chance *= cfg.vanillaMonsterShare; // Stage 3: native predators take part of the role
        }
        return chance >= 1 || ThreadLocalRandom.current().nextDouble() < chance;
    }
}
