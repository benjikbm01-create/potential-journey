package dev.underdeep.explore;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.world.UnderdeepWorlds;
import dev.underdeep.world.region.RegionType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Operator diagnostics for Stage 9 ({@code /underdeep explore ...}, permission-gated like every /underdeep command).
 * These reveal site identities and positions: they are for administrators and testing, not normal play.
 */
public final class ExploreCommands {
    private ExploreCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("underdeep").requires(s -> s.hasPermissionLevel(UnderdeepConfig.get().diagnosticsPermissionLevel))
                .then(CommandManager.literal("explore")
                        .then(CommandManager.literal("here").executes(c -> here(c.getSource())))
                        .then(CommandManager.literal("near").executes(c -> near(c.getSource(), 300))
                                .then(CommandManager.argument("radius", IntegerArgumentType.integer(16, 3000)).executes(c ->
                                        near(c.getSource(), IntegerArgumentType.getInteger(c, "radius")))))
                        .then(CommandManager.literal("journal").executes(c -> journal(c.getSource())))
                        .then(CommandManager.literal("me").executes(c -> me(c.getSource())))
                        .then(CommandManager.literal("encounter").then(CommandManager.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    ExploreContent.ENCOUNTERS.keySet().forEach(b::suggest);
                                    return b.buildFuture();
                                }).executes(c -> encounter(c.getSource(), StringArgumentType.getString(c, "id")))))));
    }

    static void send(ServerCommandSource s, String msg) {
        s.sendFeedback(() -> Text.literal(msg), false);
    }

    static int here(ServerCommandSource src) {
        ServerWorld w = src.getWorld();
        ExplorationMap map = Explorations.of(w);
        if (map == null) {
            send(src, "Exploration sites are not enabled in this world (created before generator v" + Explorations.SINCE_VERSION + ").");
            return 0;
        }
        Site s = map.siteAt(BlockPos.ofFloored(src.getPosition()), 4);
        if (s == null) {
            send(src, "No exploration site here.");
            return 0;
        }
        SiteLayout l = map.layout(s);
        ExplorationState.SiteState st = ExplorationState.get(w.getServer()).peek(s.id());
        int solved = st == null ? 0 : (int) st.puzzles.values().stream().filter(p -> p.solved).count();
        send(src, s.family().id() + " (" + s.family().category() + ", " + s.family().scale() + ", " + s.region().id() + ", " + s.layer().id + ") id " + s.key()
                + " at " + s.x() + " " + s.y() + " " + s.z() + " r" + s.radius() + " " + s.rotation());
        send(src, "puzzles " + solved + "/" + l.canvas.puzzles.size() + ", traps " + l.canvas.traps.size() + (st == null ? "" : " (jammed " + st.disarmed.size() + ")")
                + ", caches " + l.canvas.caches.size() + ", claims " + (st == null ? 0 : st.claims.size()) + ", hidden rooms " + l.canvas.secrets.size()
                + ", discoverers " + (st == null ? 0 : st.discoverers.size()) + (st != null && st.completedAt >= 0 ? ", completed" : ""));
        return 1;
    }

    static int near(ServerCommandSource src, int r) {
        ExplorationMap map = Explorations.of(src.getWorld());
        if (map == null) {
            send(src, "Exploration sites are not enabled in this world.");
            return 0;
        }
        BlockPos p = BlockPos.ofFloored(src.getPosition());
        List<Site> l = map.sitesNear(p.getX(), p.getZ(), r);
        send(src, l.size() + " sites within " + r + " blocks (spoilers!):");
        for (Site s : l.subList(0, Math.min(20, l.size()))) {
            send(src, " " + s.family().id() + " " + s.layer().id + " at " + s.x() + " " + (s.y() + 1) + " " + s.z() + " (" + (int) s.dist(p.getX(), p.getZ()) + ")");
        }
        return l.size();
    }

    static int journal(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p != null) {
            Explorations.sendJournal(p);
            send(src, "Journal sent.");
        }
        return 1;
    }

    static int me(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            return 0;
        }
        ExplorerData d = ExplorerData.of(p);
        send(src, "discovered " + d.sites.size() + " places, " + d.secrets.size() + " hidden rooms, completed " + d.completed.size() + ", records "
                + d.records.size() + ", artifacts " + d.artifacts.size() + ", leads " + d.leads.size());
        return 1;
    }

    static int encounter(ServerCommandSource src, String id) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null || !ExploreContent.ENCOUNTERS.containsKey(id)) {
            send(src, "Unknown encounter.");
            return 0;
        }
        RegionType r = UnderdeepWorlds.terrain(src.getWorld()) == null ? RegionType.ROOTWILD
                : UnderdeepWorlds.terrain(src.getWorld()).regionAt(p.getBlockX(), p.getBlockY(), p.getBlockZ());
        boolean ok = TravelEncounters.run(src.getWorld(), p, Explorations.of(src.getWorld()), id, r);
        send(src, ok ? "Encounter started." : "That encounter does not fit here (no suitable spot, water or site nearby).");
        return ok ? 1 : 0;
    }
}
