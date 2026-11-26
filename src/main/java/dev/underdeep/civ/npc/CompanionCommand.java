package dev.underdeep.civ.npc;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.settlement.SettlementMap;
import dev.underdeep.civ.settlement.SettlementSite;
import dev.underdeep.civ.settlement.Settlements;
import dev.underdeep.config.UnderdeepConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * {@code /companion <command>} — lets a player command their own companion from a distance (no permission
 * needed; it only ever affects the caller's companion). Commands: follow, wait, close, distance, rest,
 * avoid, defend_self, defend_me, home, regroup, leave, status.
 */
public final class CompanionCommand {
    private CompanionCommand() {
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((d, access, env) -> d.register(CommandManager.literal("companion")
                .then(CommandManager.argument("command", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (String s : new String[]{"follow", "wait", "close", "distance", "rest", "avoid", "defend_self", "defend_me", "home",
                                    "regroup", "leave", "status"}) {
                                b.suggest(s);
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> run(c.getSource(), StringArgumentType.getString(c, "command"))))
                .executes(c -> run(c.getSource(), "status"))));
    }

    private static int run(ServerCommandSource src, String cmd) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null || !UnderdeepConfig.get().companionsEnabled) {
            return 0;
        }
        ServerWorld w = p.getServer().getOverworld();
        SettlementRecord found = null;
        SettlementRecord.InhabitantRecord fr = null;
        for (SettlementRecord rec : SettlementState.get(w).all().values()) {
            for (SettlementRecord.InhabitantRecord r : rec.inhabitants) {
                if (r.alive && r.mind != null && p.getUuid().equals(r.mind.companionOf)) {
                    found = rec;
                    fr = r;
                }
            }
        }
        if (fr == null) {
            src.sendFeedback(() -> Text.translatable("message.underdeep.companion.none").formatted(Formatting.GRAY), false);
            return 0;
        }
        Mind m = fr.mind;
        String name = fr.name;
        InhabitantManager mgr = InhabitantManager.get(w);
        InhabitantEntity e = mgr == null ? null : mgr.active.getOrDefault(found.id, java.util.Map.of()).get(fr.index);
        boolean near = e != null && e.getWorld() == p.getWorld() && e.squaredDistanceTo(p) < 48 * 48;
        String reply;
        switch (cmd) {
            case "follow", "close", "distance", "wait", "rest" -> {
                if (!Companions.travelling(m)) {
                    reply = name + " is at home. Use /companion regroup.";
                    break;
                }
                Companions.set(m, switch (cmd) {
                    case "close" -> Companions.Mode.CLOSE;
                    case "distance" -> Companions.Mode.DISTANCE;
                    case "wait" -> Companions.Mode.WAIT;
                    case "rest" -> Companions.Mode.REST;
                    default -> Companions.Mode.FOLLOW;
                });
                if (cmd.equals("wait") && e != null) {
                    e.waitPos = e.getBlockPos();
                }
                reply = near ? name + ": \"Understood.\"" : name + " is too far away to hear you, but will do so when you meet again.";
            }
            case "avoid", "defend_self", "defend_me" -> {
                Companions.set(m, cmd.equals("avoid") ? Companions.Stance.AVOID : cmd.equals("defend_me") ? Companions.Stance.DEFEND_ME : Companions.Stance.SELF);
                reply = name + ": \"Alright.\"";
            }
            case "home" -> {
                Companions.set(m, Companions.Mode.HOME);
                if (e != null) {
                    e.companionOwner = null;
                }
                reply = name + " heads home to " + found.name + ".";
            }
            case "regroup" -> {
                if (m.status == Mind.Status.RECOVERING) {
                    reply = name + " is still recovering at home.";
                } else if (Companions.travelling(m) && near) {
                    Companions.set(m, Companions.Mode.FOLLOW);
                    reply = name + " is right here.";
                } else {
                    Companions.set(m, Companions.Mode.FOLLOW);
                    // travel time from home (abstract): about 5 blocks per second, between 10 s and 2 min
                    SettlementMap map = Settlements.of(w);
                    SettlementSite site = map == null ? null : map.byId(found.id);
                    double dist = site == null ? 200 : Math.sqrt(p.squaredDistanceTo(site.x(), p.getY(), site.z()));
                    m.status = Mind.Status.AWAY;
                    m.recoverUntil = w.getTime() + Math.max(200, Math.min(2400, (long) (dist / 5 * 20)));
                    reply = name + " sets out to meet you.";
                }
            }
            case "leave" -> {
                Companions.returnPack(p, m);
                m.companionOf = null;
                m.companionMode = 0;
                m.bond(p.getUuid()).companion = false;
                if (e != null) {
                    e.companionOwner = null;
                }
                reply = name + " is no longer travelling with you.";
            }
            default -> {
                String where = Companions.travelling(m) ? (e != null ? "with you" : m.status == Mind.Status.AWAY ? "on the way to you" : "waiting for you")
                        : m.status == Mind.Status.RECOVERING ? "recovering at home" : "at home in " + found.name;
                reply = name + " (" + fr.profession.replace('_', ' ') + "): " + where + "; " + Companions.mode(m).name().toLowerCase()
                        + ", " + Companions.stance(m).name().toLowerCase().replace('_', ' ') + "; health " + Math.round(m.health * 100) + "%";
            }
        }
        SettlementState.get(w).markDirty();
        String fr2 = reply;
        src.sendFeedback(() -> Text.literal(fr2), false);
        return 1;
    }
}
