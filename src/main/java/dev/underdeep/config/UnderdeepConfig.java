package dev.underdeep.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.underdeep.Underdeep;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side configuration, stored in {@code config/underdeep.json}. Missing fields keep defaults;
 * the file is rewritten with all fields on load so new options become visible to server owners.
 */
public final class UnderdeepConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static UnderdeepConfig instance = new UnderdeepConfig();

    /** Allow Nether portals to be lit in Underdeep worlds (default: disabled). */
    public boolean netherPortalsInUnderdeep = false;
    /**
     * Allow End access in Underdeep worlds. Underdeep worlds contain no strongholds, so the End is
     * unreachable by default; this flag is reserved for a future alternative access route.
     */
    public boolean endAccessInUnderdeep = false;
    /** Permission level required for /underdeep diagnostic commands. */
    public int diagnosticsPermissionLevel = 2;
    /** Horizontal search radius (blocks) for a safe world spawn around the origin. */
    public int spawnSearchRadius = 96;
    /** Radius (blocks) around the origin, above y=90, where no natural hostile mobs spawn. */
    public int starterSafeRadius = 150;
    /**
     * Fraction of natural hostile spawn attempts allowed per ecosystem danger class. Low-danger
     * ecosystems additionally have no hostile spawn entries in their biome data.
     */
    public double monsterSpawnChanceLow = 0.0;
    public double monsterSpawnChanceMedium = 0.12;
    public double monsterSpawnChanceHigh = 0.45;
    public double monsterSpawnChanceExtreme = 1.0;

    // ---- Stage 2: ecology ----
    /** Natural spreading of plants, fungi, fungal soil and aquatic vegetation (Underdeep worlds only). */
    public boolean naturalSpreading = true;
    /** Multiplier for all natural spreading chances (0 disables spreading, 2 doubles it). */
    public double spreadRateMultiplier = 1.0;
    /** Maximum number of the same species within a spreading plant's neighbourhood before it stops spreading. */
    public int spreadDensityLimit = 5;
    /** Puffball spore releases (particles and new fungi nearby). */
    public boolean sporeReleases = true;
    /** Whether spore releases briefly weaken players standing right next to them. */
    public boolean sporeEffects = true;
    /** Slow regrowth of crystal buds on budding blocks. */
    public boolean crystalRegrowth = true;
    /** Thorny, toxic and snapping plants affect creatures that touch them. */
    public boolean reactivePlants = true;
    /** Ambient ecological particles (pollen, spores, falling leaves). Client-side setting. */
    public boolean ambientParticles = true;

    public static UnderdeepConfig get() {
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("underdeep.json");
        UnderdeepConfig loaded = new UnderdeepConfig();
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                UnderdeepConfig c = GSON.fromJson(r, UnderdeepConfig.class);
                if (c != null) {
                    loaded = c;
                }
            } catch (Exception e) {
                Underdeep.LOGGER.error("Failed to read {}, using defaults", path, e);
            }
        }
        instance = loaded;
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, w);
            }
        } catch (IOException e) {
            Underdeep.LOGGER.warn("Could not write {}", path, e);
        }
    }
}
