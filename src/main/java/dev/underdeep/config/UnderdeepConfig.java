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

    // ---- Stage 3: wildlife ----
    /** Master switch for Underdeep wildlife (spawning, regional populations, simulation). */
    public boolean wildlifeEnabled = true;
    /** Overall wildlife density multiplier (regional carrying capacities). */
    public double wildlifeDensity = 1.0;
    /** Optional per-ecosystem density multipliers, e.g. {"the_abyss": 0.5}. */
    public java.util.Map<String, Double> wildlifeDensityByEcosystem = new java.util.HashMap<>();
    /** Predator abundance multiplier (spawning share and abstract predation pressure). */
    public double predatorAbundance = 1.0;
    /** Reproduction rate multiplier (natural breeding and abstract recovery); 0 disables wild breeding. */
    public double wildlifeReproduction = 1.0;
    /** Migration multiplier between regions (0 disables migration). */
    public double wildlifeMigration = 1.0;
    /** Ticks between aggregate simulation passes over unloaded regions. */
    public int wildlifeSimInterval = 1200;
    /** Regions advanced per simulation pass. */
    public int wildlifeSimRegionsPerUpdate = 24;
    /** Maximum wild creatures materialised around one player. */
    public int maxWildlifePerPlayer = 28;
    /** Maximum wild creatures materialised in the whole world. */
    public int maxWildlifeTotal = 160;
    /** Beyond this distance from every player, wildlife runs reduced AI (no searches). */
    public int wildlifeDetailDistance = 48;
    /** Beyond this distance from every player, wild creatures return to their regional population. */
    public int wildlifeDespawnDistance = 96;
    /** How much grazing actually removes vegetation (0 = animals only nibble). Crops are never eaten. */
    public double wildlifeVegetationConsumption = 1.0;
    /** Frequency multiplier for rare and exceptional creatures. */
    public double rareCreatureFrequency = 1.0;
    /** Whether wild creatures can be tamed. */
    public boolean tamingEnabled = true;
    /** Replace vanilla passive animals, fish and bats in Underdeep ecosystems with native wildlife. */
    public boolean vanillaMobReplacement = true;
    /** Share of vanilla hostile spawn attempts kept in Underdeep (natural predators fill the rest). */
    public double vanillaMonsterShare = 0.6;
    /** How strongly player hunting disturbs a region (temporarily lowers its capacity). */
    public double huntingDisturbance = 1.0;

    // ---- Stage 4: civilizations ----
    /** Master switch for settlement generation in new Underdeep worlds. */
    public boolean settlementsEnabled = true;
    /**
     * Also generate settlements in worlds created before Stage 4 (terrain generator v1–v2). Settlements then
     * appear only in newly generated chunks and are cut off at the borders of explored areas.
     */
    public boolean settlementsInExistingWorlds = false;
    /** Inhabitants kept as active entities per settlement while players are near. */
    public int maxActiveInhabitantsPerSettlement = 24;
    /** Active inhabitants in the whole world. */
    public int maxActiveInhabitantsTotal = 96;
    /** Players closer than this to a settlement's edge make its inhabitants appear. */
    public int inhabitantActivationDistance = 64;
    /** Natural hostile spawns are blocked within a settlement's radius plus this margin. */
    public int settlementMonsterExclusion = 24;

    // ---- Stage 5: living inhabitants ----
    /** Memories kept per inhabitant (least important fade first). */
    public int inhabitantMemoryLimit = 48;
    /** Pieces of news/knowledge kept per inhabitant. */
    public int inhabitantKnowledgeLimit = 12;
    /** Frequency multiplier of conversations between inhabitants (0 disables them). */
    public double npcConversationFrequency = 1.0;
    /** Show what inhabitants say to each other as floating speech bubbles. */
    public boolean speechBubbles = true;
    /** How long a speech bubble stays visible, in seconds. */
    public int speechBubbleSeconds = 4;
    /** Inhabitant minds updated per server tick in the abstract tier (bounded work). */
    public int mindUpdatesPerTick = 24;
    /** Abstract update interval (ticks) for settlements near players / far from every player. */
    public int abstractUpdateInterval = 1000;
    public int distantUpdateInterval = 6000;
    /** Settlements farther than this from every player use the distant interval. */
    public int distantSimulationDistance = 512;
    /** Companions: master switch, per-player limit, distance beyond which a companion relocates out of view. */
    public boolean companionsEnabled = true;
    public int maxCompanionsPerPlayer = 1;
    public int companionRelocateDistance = 40;
    /** When false (default) a defeated companion is incapacitated and recovers at home instead of dying. */
    public boolean companionPermanentDeath = false;
    /** Ticks before the same player can give the same inhabitant another gift that counts. */
    public int giftCooldownTicks = 6000;

    // ---- Stage 6: economies, trade and autonomous settlements ----
    /** Master switch for the settlement economy (prices, production, trade, construction, migration). */
    public boolean economyEnabled = true;
    /** Settlements within this distance of a player or the world spawn take part in the simulated economy. */
    public int economyRadius = 1600;
    /** Economy update interval (ticks) near players / far from every player; catch-up is capped in hours. */
    public int economyStepTicks = 1000;
    public int economyDistantStepTicks = 4000;
    public int economyMaxCatchUpHours = 48;
    /** Settlement economies updated per server tick (bounded work). */
    public int economySettlementsPerTick = 2;
    /** Pace multipliers (1 = default). */
    public double productionRate = 1.0;
    public double consumptionRate = 1.0;
    public double wageRate = 1.0;
    public double priceVariation = 1.0;
    public double merchantMargin = 0.18;
    public double tradeFrequency = 1.0;
    public double transportCost = 1.0;
    public double constructionSpeed = 1.0;
    public double constructionBudget = 1.0;
    public double populationGrowth = 1.0;
    public double migrationFrequency = 1.0;
    public double eventFrequency = 1.0;
    /** Target money per inhabitant (emeralds) used by the issuance/upkeep stabiliser. */
    public double moneyPerCapita = 30;
    /** Physical caravans embodied at once, shipments in flight at once. */
    public int maxActiveCaravans = 4;
    public int maxShipments = 40;
    /** Construction: projects at once per settlement, blocks placed per tick at a site. */
    public int maxProjectsPerSettlement = 1;
    public int constructionBlocksPerTick = 16;
    /** Player participation limits. */
    public int maxBusinessesPerPlayer = 2;
    public int maxEmployeesPerBusiness = 1;
    public int maxContractsPerPlayer = 3;
    /** Containers and blocks inside owned property are protected from other players. */
    public boolean propertyProtection = true;

    // ---- Stage 8: equipment, handbook, advancements ----
    /** Give each new player an Underdeep Handbook on first arrival in an Underdeep world (the H key always works). */
    public boolean handbookOnJoin = true;
    /** Recognise accomplishments from earlier versions once (quietly) when a player first joins with Stage 8. */
    public boolean advancementMigration = true;

    // ---- Stage 9: exploration ----
    public Exploration exploration = new Exploration();
    public Caves caves = new Caves();
    public Combat combat = new Combat();

    /** Stage 10 player combat (all server-side). */
    public static final class Combat {
        /** Master switch for stamina, dodge, heavy attacks, parries, counters and stagger (vanilla combat stays). */
        public boolean enabled = true;
        public boolean dodge = true;
        public boolean heavyAttacks = true;
        /** Right-click with a melee weapon (no shield in the off hand) to parry. Shields always parry when raised in time. */
        public boolean weaponParry = true;
        public double dodgeStamina = 22;
        public int dodgeInvulnerableTicks = 7;
        /** Parry window in ticks (20 = 1 s); latency compensation adds up to 4 ticks for high ping. */
        public int parryWindowTicks = 6;
        public boolean latencyCompensation = true;
        public int counterWindowTicks = 30;
        public double staminaRegenPerSecond = 22;
        public double shieldStaminaPerDamage = 4;
        /** Multiplier of heavy-attack wind-up times (1 = designed). */
        public double heavyWindupScale = 1.0;
        /** Combat assistance: wider dodge and parry windows (and longer boss telegraphs). */
        public boolean assist = false;
        /** Creatures fight with telegraphed, archetype-specific attacks (false: vanilla instant melee). */
        public boolean enemyBehaviours = true;
        /** Ordinary mobs can be staggered. */
        public boolean mobStagger = true;
    }


    /** Stage 10 cave network (applies to worlds created with generator v5+; changing it changes future terrain). */
    public static final class Caves {
        /** Density of secondary connecting passages, side branches and intermediate chambers (0 = none, 1 = designed). */
        public double passageFrequency = 1.0;
        /** Share of deep cells with a walkable spiral descent between the layers (0 = none, 1 = designed). */
        public double descentFrequency = 1.0;
    }


    /** Stage 9 settings (nested object in underdeep.json; missing keys keep their defaults). */
    public static final class Exploration {
        /** Master switch: exploration sites generate in new chunks of Underdeep worlds created with generator v4+. */
        public boolean enabled = true;
        /** Also generate sites in new chunks of older worlds (sites at the border of explored areas may be cut). */
        public boolean inOlderWorlds = false;
        /** Share of grid cells holding a site (1.0 = default). Changing it changes future placement: use for fresh worlds. */
        public double density = 1.0;
        /** Relative weight of dungeons among sites (1.0 = default). Same caveat. */
        public double dungeonDensity = 1.0;
        /** Encounter frequency multiplier (0 disables travel encounters). */
        public double encounterFrequency = 1.0;
        /** Trap damage and hazard multiplier (0 = harmless warnings only, 1 = default, 2 = harsh). */
        public double trapSeverity = 1.0;
        /** Chat lines for minor discoveries (significant discoveries always announce themselves to the finder). */
        public boolean minorDiscoveryMessages = true;
        /** Undead guardians wake in guarded rooms. */
        public boolean guardians = true;
    }

    // ---- Stage 7: survival, farming, cooking, medicine and homesteading ----
    public Survival survival = new Survival();

    /** Stage 7 settings (a nested object in underdeep.json; missing keys keep their defaults). */
    public static final class Survival {
        /** Master switch for Stage 7 rules (items and blocks always exist). */
        public boolean enabled = true;
        /** Variety bonus (Well Fed) for eating different food groups. Never a penalty. */
        public boolean nutritionBonus = true;
        /** Environmental hazards in dangerous regions (never in the starting regions). */
        public boolean hazards = true;
        /** Multiplier for how fast hazard exposure builds (1 = default, 0.5 = half as fast). */
        public double hazardRate = 1.0;
        /** Hazard effects may deal small amounts of damage at full exposure. */
        public boolean hazardDamage = true;
        /** Lightweight injuries: a single heavy hit (7+ damage) leaves you Wounded until treated or healed. */
        public boolean injuries = true;
        /** Weariness after several days without rest (sleep, or sitting a while). Mild. Off by default. */
        public boolean weariness = false;
        /** Food spoilage. Not implemented as decay; kept for compatibility, see docs/SURVIVAL.md. Off by default. */
        public boolean spoilage = false;
        /** Regional fishing tables in the Underdeep. */
        public boolean regionalFishing = true;
        /** Bedrolls never move your respawn point unless you sneak while using one. */
        public boolean bedrollSetsSpawnWhenSneaking = true;
        /** Station speed multiplier (1 = default; 2 = twice as fast). */
        public double stationSpeed = 1.0;
        /** Crop growth bonus on rich soil (multiplier on the growth chance). */
        public double richSoilGrowth = 1.5;
        /** Fish trap: ticks between catches, maximum catches caught up after the area was unloaded. */
        public int fishTrapInterval = 6000;
        public int fishTrapMaxCatchUp = 3;
        /** Feeding trough radius and the cap on tamed animals near a trough that may still breed. */
        public int troughRadius = 12;
        public int breedingCap = 24;
    }

    public double densityFor(String ecosystem) {
        return wildlifeDensity * wildlifeDensityByEcosystem.getOrDefault(ecosystem, 1.0);
    }

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
