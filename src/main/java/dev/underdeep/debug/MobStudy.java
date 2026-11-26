package dev.underdeep.debug;

import dev.underdeep.Underdeep;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.spawn.SpawnRules;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Development-only measurement of natural mob populations with a real connected player
 * ({@code -Dunderdeep.mobstudy=<output dir>}, optional {@code -Dunderdeep.mobstudy.sites=<ecosystem_sites.txt>},
 * {@code -Dunderdeep.mobstudy.seconds=60}).
 * <p>
 * Vanilla natural spawning only happens near non-spectator players, so this waits for a client to
 * join (the test harness connects one), then visits the starter cavern and one site per ecosystem.
 * At each site the player hovers in creative mode (it still counts for spawning but is never attacked),
 * existing hostiles nearby are removed, and natural spawning runs for the configured time. Hostile and
 * passive counts within 64 blocks are sampled every 10 seconds. Results go to {@code mobstudy.md}.
 */
public final class MobStudy {
    private record Site(String name, RegionType region, int x, int y, int z) {
    }

    private static final List<Site> SITES = new ArrayList<>();
    private static final StringBuilder OUT = new StringBuilder();
    private static int index = -1;
    private static int ticks;
    private static int waitForPlayer = 20 * 600;
    private static int maxHostile;
    private static final List<Integer> samples = new ArrayList<>();
    private static int perSiteTicks;
    private static Path outDir;

    private MobStudy() {
    }

    public static void init() {
        String out = System.getProperty("underdeep.mobstudy");
        if (out == null) {
            return;
        }
        outDir = Path.of(out);
        perSiteTicks = Integer.getInteger("underdeep.mobstudy.seconds", 60) * 20;
        ServerTickEvents.END_SERVER_TICK.register(MobStudy::tick);
        Underdeep.LOGGER.info("Mob study enabled; waiting for a player to join");
    }

    private static void prepare(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        UnderdeepTerrain t = UnderdeepWorlds.terrain(world);
        BlockPos spawn = world.getSpawnPos();
        SITES.add(new Site("starter_cavern", RegionType.ROOTWILD, spawn.getX() + 20, spawn.getY() + 2, spawn.getZ() + 20));
        String sitesFile = System.getProperty("underdeep.mobstudy.sites");
        if (sitesFile != null && t != null) {
            try {
                for (String line : Files.readAllLines(Path.of(sitesFile))) {
                    String[] p = line.trim().split("\\s+");
                    if (p.length < 4) {
                        continue;
                    }
                    RegionType type = RegionType.byId(p[0]);
                    CavernLayer layer = CavernLayer.values()[Integer.parseInt(p[2])];
                    int x = Integer.parseInt(p[1]);
                    int z = Integer.parseInt(p[3]);
                    // nearest open column of that ecosystem
                    found:
                    for (int rad = 0; rad <= 96; rad += 4) {
                        for (int dx = -rad; dx <= rad; dx += 4) {
                            for (int dz = -rad; dz <= rad; dz += 4) {
                                if (Math.max(Math.abs(dx), Math.abs(dz)) != rad) {
                                    continue;
                                }
                                TerrainColumn c = t.column(x + dx, z + dz);
                                boolean upper = layer == CavernLayer.UPPER;
                                double f = upper ? c.upperFloor : c.deepFloor;
                                double ce = upper ? c.upperCeiling : c.deepCeiling;
                                if (ce - f > 12 && (upper ? c.upperRegion : c.deepRegion).primary == type) {
                                    double fluid = upper ? UnderdeepTerrain.SEA_LEVEL : CavernLayer.DEEP.fluidLevel;
                                    SITES.add(new Site(type.id(), type, x + dx, (int) Math.max(f, fluid) + 4, z + dz));
                                    break found;
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                Underdeep.LOGGER.error("Mob study: cannot read sites", e);
            }
        }
        OUT.append("# Natural mob population study\n\n");
        OUT.append(String.format(Locale.ROOT, "- %d seconds per site, counts within 64 blocks horizontally / 40 vertically of the player\n", perSiteTicks / 20));
        OUT.append("- doMobSpawning=").append(world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING))
                .append(", difficulty=").append(world.getDifficulty()).append("\n\n");
        OUT.append("| site | ecosystem | danger | starter zone | hostile samples (every 10 s) | max hostile | passive at end | wildlife at end (species: count) | all native | avg tick ms |\n");
        OUT.append("|---|---|---|---|---|---|---|---|---|---|\n");
    }

    private static void tick(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (index < 0) {
            if (players.isEmpty()) {
                if (--waitForPlayer <= 0) {
                    Underdeep.LOGGER.error("Mob study: no player joined; stopping");
                    finish(server);
                }
                return;
            }
            prepare(server);
            index = 0;
            begin(server, players.get(0));
            return;
        }
        if (index >= SITES.size() || players.isEmpty()) {
            return;
        }
        ServerPlayerEntity p = players.get(0);
        Site site = SITES.get(index);
        ticks++;
        if (ticks % 200 == 0) {
            int hostile = count(p, true);
            samples.add(hostile);
            maxHostile = Math.max(maxHostile, hostile);
        }
        if (ticks >= perSiteTicks) {
            int passive = count(p, false);
            java.util.Map<String, Integer> wild = new java.util.TreeMap<>();
            boolean native_ = true;
            var wm = dev.underdeep.wildlife.population.WildlifeManager.get(p.getServerWorld());
            for (var c : p.getServerWorld().getEntitiesByClass(dev.underdeep.wildlife.UnderdeepCreature.class,
                    p.getBoundingBox().expand(64, 40, 64), e -> true)) {
                wild.merge(c.species().id, 1, Integer::sum);
                if (wm != null && c.isWild()) {
                    native_ &= c.species().regions().contains(wm.typeOf(c.homeRegion));
                }
            }
            OUT.append(String.format(Locale.ROOT, "| %d %d %d | %s | %s | %s | %s | %d | %d | %s | %s | %.2f |\n", site.x, site.y, site.z,
                    site.region.id(), site.region.danger().id(), SpawnRules.inStarterZone(new BlockPos(site.x, site.y, site.z)),
                    samples, maxHostile, passive, wild, native_, server.getAverageNanosPerTick() / 1e6));
            Underdeep.LOGGER.info("Mob study {}: hostile samples {} (max {}), passive {}", site.name, samples, maxHostile, passive);
            index++;
            if (index >= SITES.size()) {
                finish(server);
                return;
            }
            begin(server, p);
        }
    }

    private static void begin(MinecraftServer server, ServerPlayerEntity p) {
        Site site = SITES.get(index);
        ServerWorld world = server.getOverworld();
        p.changeGameMode(GameMode.CREATIVE);
        p.getAbilities().flying = true;
        p.sendAbilitiesUpdate();
        p.teleport(world, site.x + 0.5, site.y, site.z + 0.5, 0, 0);
        // Clear hostiles anywhere near the new site so each measurement starts from zero.
        Box box = new Box(site.x - 160, site.y - 120, site.z - 160, site.x + 160, site.y + 120, site.z + 160);
        for (Entity e : world.getOtherEntities(p, box, e -> e instanceof Monster)) {
            e.discard();
        }
        ticks = 0;
        maxHostile = 0;
        samples.clear();
    }

    private static int count(ServerPlayerEntity p, boolean hostile) {
        Box box = p.getBoundingBox().expand(64, 40, 64);
        return p.getServerWorld().getOtherEntities(p, box, e -> hostile ? e instanceof Monster : e instanceof PassiveEntity).size();
    }

    private static void finish(MinecraftServer server) {
        try {
            Files.createDirectories(outDir);
            Files.writeString(outDir.resolve("mobstudy.md"), OUT.toString());
        } catch (IOException e) {
            Underdeep.LOGGER.error("Mob study: cannot write report", e);
        }
        Underdeep.LOGGER.info("UNDERDEEP MOBSTUDY FINISHED");
        index = Integer.MAX_VALUE;
        server.stop(false);
    }
}
