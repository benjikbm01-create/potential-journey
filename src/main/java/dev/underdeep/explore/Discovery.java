package dev.underdeep.explore;

import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.equipment.Accessories;
import dev.underdeep.guide.Advancements;
import dev.underdeep.guide.Handbook;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Discovery (Stage 9), once per second per player, bounded to sites within 72 blocks (pure map lookup, no block
 * scans): entering a site's footprint discovers it for that player; stepping into a hidden room discovers the secret;
 * entering a guarded room wakes it once; reaching a dungeon's heart with its required puzzles solved completes the
 * expedition for every nearby participant who discovered the site. Discovery and completion are personal; physical
 * state (seals, traps, claims) is shared, so a late arrival still discovers and can complete what others opened.
 */
public final class Discovery {
    private Discovery() {
    }

    private static final Map<UUID, Long> CONTEXT = new HashMap<>();

    public static void tick(ServerWorld w, ServerPlayerEntity p, ExplorationMap map) {
        if (p.isSpectator()) {
            return;
        }
        ExplorerData d = ExplorerData.of(p);
        boolean keen = Accessories.flags(p).contains("keen");
        long inside = 0;
        for (Site s : map.sitesNear(p.getBlockX(), p.getBlockZ(), 72)) {
            SiteLayout l = map.layout(s);
            int margin = s.family().category() == ExploreContent.Category.SECRET ? 0 : (keen ? 12 : 6);
            if (s.family().category() != ExploreContent.Category.SECRET && l.inside(p.getX(), p.getY(), p.getZ(), margin) && !d.sites.containsKey(s.id())) {
                discover(w, p, s, d);
            }
            if (!l.inside(p.getX(), p.getY(), p.getZ(), 2)) {
                continue;
            }
            inside = d.sites.containsKey(s.id()) ? s.id() : inside;
            // hidden rooms (standalone secret sites count as a discovery of the site as well)
            for (int i = 0; i < l.secretBoxes.size(); i++) {
                int[] b = l.secretBoxes.get(i);
                String key = s.id() + ":" + l.canvas.secrets.get(i).id();
                if (Canvas.inBox(b, p.getX(), p.getY() + 0.1, p.getZ()) && !d.secrets.contains(key)) {
                    secret(w, p, s, l.canvas.secrets.get(i).id(), key, d);
                }
            }
            ExplorationState st = ExplorationState.get(w.getServer());
            ExplorationState.SiteState ss = st.peek(s.id());
            for (int i = 0; i < l.guardBoxes.size(); i++) {
                if (UnderdeepConfig.get().exploration.guardians && Canvas.inBox(l.guardBoxes.get(i), p.getX(), p.getY(), p.getZ())
                        && (ss == null || !ss.woken.contains(i))) {
                    Mechanisms.Ctx c = new Mechanisms.Ctx(w, map, l, st, st.site(s.id()));
                    Traps.wake(c, i);
                }
            }
            if (l.heartBox != null && Canvas.inBox(l.heartBox, p.getX(), p.getY(), p.getZ()) && !d.completed.contains(s.id()) && requiredSolved(l, ss)) {
                complete(w, s, l, st, p);
            }
            senses(w, p, l, ss);
        }
        Long prev = CONTEXT.put(p.getUuid(), inside);
        if (prev == null || prev != inside) {
            Site s = inside == 0 ? null : map.byId(inside);
            Explorations.sendContext(p, s == null ? "none" : s.family().audio());
        }
    }

    static boolean requiredSolved(SiteLayout l, @Nullable ExplorationState.SiteState ss) {
        for (int i = 0; i < l.canvas.puzzles.size(); i++) {
            if (l.canvas.puzzles.get(i).required() && (ss == null || ss.puzzles.get(i) == null || !ss.puzzles.get(i).solved)) {
                return false;
            }
        }
        return true;
    }

    public static void discover(ServerWorld w, ServerPlayerEntity p, Site s, ExplorerData d) {
        d.sites.put(s.id(), w.getTime());
        String lead = d.leads.remove(s.id());
        ExplorationState st = ExplorationState.get(w.getServer());
        ExplorationState.SiteState ss = st.site(s.id());
        boolean first = ss.discoverers.isEmpty();
        ss.discoverers.add(p.getUuid());
        if (ss.firstDiscovered < 0) {
            ss.firstDiscovered = w.getTime();
        }
        st.markDirty();
        ExploreContent.Family f = s.family();
        boolean big = f.scale().rank >= 2 || f.category() == ExploreContent.Category.DUNGEON || f.category() == ExploreContent.Category.LANDMARK;
        if (big || UnderdeepConfig.get().exploration.minorDiscoveryMessages) {
            Formatting col = switch (f.category()) {
                case LANDMARK -> Formatting.AQUA;
                case DUNGEON -> Formatting.RED;
                default -> Formatting.GOLD;
            };
            p.sendMessage(Text.literal("Discovered: " + f.name()).formatted(col, Formatting.BOLD), !big);
            if (big) {
                p.sendMessage(Text.literal(f.desc()).formatted(Formatting.GRAY, Formatting.ITALIC), false);
                if (lead != null) {
                    p.sendMessage(Text.literal(lead.startsWith("rumour") ? "So the stories were true." : "Just where the chart said.")
                            .formatted(Formatting.DARK_AQUA), false);
                }
            }
        }
        p.playSoundToPlayer(big ? SoundEvents.UI_TOAST_IN : SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, big ? 0.8f : 0.5f,
                big ? 0.8f : 1.2f);
        Advancements.event(p, "site_discovered", f.id());
        Advancements.event(p, f.category().name().toLowerCase() + "_discovered", f.id());
        Advancements.event(p, "explored_region", s.region().id());
        if (first) {
            Advancements.event(p, "first_discoverer", f.id());
        }
        Handbook.push(p, List.of("site:" + f.id()));
        companionRemark(w, p, s);
    }

    static void secret(ServerWorld w, ServerPlayerEntity p, Site s, String secretId, String key, ExplorerData d) {
        d.secrets.add(key);
        ExploreContent.SecretDef def = ExploreContent.SECRETS.get(secretId);
        String name = def == null ? "A hidden place" : def.name();
        p.sendMessage(Text.literal("Secret found: " + name).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        if (def != null) {
            p.sendMessage(Text.literal(def.desc()).formatted(Formatting.GRAY, Formatting.ITALIC), false);
        }
        p.playSoundToPlayer(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1f, 0.8f);
        Advancements.event(p, "secret_found", secretId);
        Handbook.push(p, List.of("secret:" + secretId));
        if (s.family().category() == ExploreContent.Category.SECRET && !d.sites.containsKey(s.id())) {
            d.sites.put(s.id(), w.getTime());
            ExplorationState st = ExplorationState.get(w.getServer());
            st.site(s.id()).discoverers.add(p.getUuid());
            st.markDirty();
            Advancements.event(p, "site_discovered", s.family().id());
            Handbook.push(p, List.of("site:" + s.family().id()));
        }
    }

    /** Completion: every nearby participant who has discovered the site gets the credit once. */
    static void complete(ServerWorld w, Site s, SiteLayout l, ExplorationState st, ServerPlayerEntity trigger) {
        ExplorationState.SiteState ss = st.site(s.id());
        if (ss.completedAt < 0) {
            ss.completedAt = w.getTime();
        }
        BlockPos c = BlockPos.ofFloored((l.heartBox[0] + l.heartBox[3]) / 2.0, l.heartBox[1], (l.heartBox[2] + l.heartBox[5]) / 2.0);
        List<ServerPlayerEntity> party = new java.util.ArrayList<>(w.getPlayers(pl -> pl.squaredDistanceTo(c.toCenterPos()) < 40 * 40 && !pl.isSpectator()));
        if (!party.contains(trigger)) {
            party.add(trigger);
        }
        for (ServerPlayerEntity p : party) {
            ExplorerData d = ExplorerData.of(p);
            if (!d.sites.containsKey(s.id()) || !d.completed.add(s.id())) {
                continue;
            }
            ss.completedBy.add(p.getUuid());
            p.sendMessage(Text.literal("Expedition complete: " + s.family().name()).formatted(Formatting.GREEN, Formatting.BOLD), false);
            p.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1f);
            Advancements.event(p, "expedition_completed", s.family().id());
            Advancements.event(p, "expedition_" + s.family().scale().name().toLowerCase(), s.family().id());
            if (party.size() >= 2) {
                Advancements.event(p, "expedition_together", s.family().id());
            }
        }
        st.markDirty();
    }

    /** Accessory senses: "echo" shows unsolved seals and hidden rooms nearby; "trapsense" warns of armed trap plates. */
    static void senses(ServerWorld w, ServerPlayerEntity p, SiteLayout l, @Nullable ExplorationState.SiteState ss) {
        var flags = Accessories.flags(p);
        if (flags.contains("trapsense")) {
            for (int i = 0; i < l.canvas.traps.size(); i++) {
                if (ss != null && ss.disarmed.contains(i)) {
                    continue;
                }
                for (long t : l.canvas.traps.get(i).triggers()) {
                    BlockPos b = l.world(t);
                    if (b.getSquaredDistance(p.getPos()) < 6 * 6 && w.getBlockState(b).isOf(ExploreBlocks.TRAP_PLATE)
                            && !w.getBlockState(b).get(ExploreBlocks.DISARMED)) {
                        w.spawnParticles(p, ParticleTypes.CRIT, true, b.getX() + 0.5, b.getY() + 0.2, b.getZ() + 0.5, 3, 0.25, 0.02, 0.25, 0);
                        p.sendMessage(Text.literal("Careful: something underfoot is not as it seems.").formatted(Formatting.RED), true);
                    }
                }
            }
        }
        if (flags.contains("echo") && w.getTime() % 40 < 20) {
            for (BlockPos h : hollows(w, l, ss)) {
                if (h.getSquaredDistance(p.getPos()) < 10 * 10) {
                    w.spawnParticles(p, ParticleTypes.ENCHANT, true, h.getX() + 0.5, h.getY() + 0.5, h.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.2);
                }
            }
        }
    }

    /** Unsolved seals and hidden-room centres of one site. */
    static List<BlockPos> hollows(ServerWorld w, SiteLayout l, @Nullable ExplorationState.SiteState ss) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < l.canvas.puzzles.size(); i++) {
            if (ss != null && ss.puzzles.get(i) != null && ss.puzzles.get(i).solved) {
                continue;
            }
            for (long s : l.canvas.puzzles.get(i).seals()) {
                out.add(l.world(s));
            }
        }
        for (int[] b : l.secretBoxes) {
            out.add(new BlockPos((b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2));
        }
        return out;
    }

    public static List<BlockPos> hollowsNear(ServerWorld w, BlockPos pos, int r) {
        ExplorationMap map = Explorations.of(w);
        List<BlockPos> out = new ArrayList<>();
        if (map == null) {
            return out;
        }
        ExplorationState st = ExplorationState.get(w.getServer());
        for (Site s : map.sitesNear(pos.getX(), pos.getZ(), r + 40)) {
            SiteLayout l = map.layout(s);
            for (BlockPos h : hollows(w, l, st.peek(s.id()))) {
                if (h.getSquaredDistance(pos) <= (double) r * r) {
                    out.add(h);
                }
            }
        }
        out.sort((a, b) -> Double.compare(a.getSquaredDistance(pos), b.getSquaredDistance(pos)));
        return out;
    }

    // ------------------------------------------------------------------ leads and directions

    /** Records a lead (rumour, chart, lights) to a site the player has not discovered. */
    public static void lead(ServerPlayerEntity p, Site s, String source) {
        ExplorerData d = ExplorerData.of(p);
        if (d.sites.containsKey(s.id())) {
            return;
        }
        if (!d.leads.containsKey(s.id()) || source.equals("chart")) {
            d.leads.put(s.id(), source);
            p.sendMessage(Text.literal("Noted in your journal: " + s.family().name()).formatted(Formatting.DARK_AQUA), true);
            Advancements.event(p, "lead_noted", source.split(":")[0]);
        }
    }

    public static String directions(ServerPlayerEntity p, Site s, boolean precise) {
        double dx = s.x() - p.getX(), dz = s.z() - p.getZ();
        double dist = Math.hypot(dx, dz);
        int shown = precise ? (int) Math.round(dist) : (int) (Math.round(dist / 50.0) * 50);
        String dir = compass(dx, dz);
        int dy = s.y() - p.getBlockY();
        String v = Math.abs(dy) < 12 ? "" : dy > 0 ? ", higher up" : ", further down";
        return (precise ? "" : "roughly ") + Math.max(precise ? 1 : 50, shown) + " blocks " + dir + v
                + (precise ? " (x " + s.x() + ", z " + s.z() + ")" : "");
    }

    public static String bearing(ServerPlayerEntity p, BlockPos to) {
        double dx = to.getX() - p.getX(), dz = to.getZ() - p.getZ();
        int dy = to.getY() - p.getBlockY();
        return (int) Math.hypot(dx, dz) + " blocks " + compass(dx, dz) + (Math.abs(dy) < 4 ? "" : dy > 0 ? ", " + dy + " up" : ", " + -dy + " down");
    }

    static String compass(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        String[] names = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
        return names[(int) Math.floorMod(Math.round(a / 45.0), 8)];
    }

    static void companionRemark(ServerWorld w, ServerPlayerEntity p, Site s) {
        try {
            dev.underdeep.civ.npc.Companions.remark(p, switch (s.family().category()) {
                case DUNGEON -> "I don't like the feel of this place. Let's keep together.";
                case LANDMARK -> "Would you look at that. Nobody back home will believe us.";
                default -> s.family().scale().rank >= 2 ? "People lived here once. A lot of them." : "Someone was here before us.";
            });
        } catch (RuntimeException ignored) {
            // companions are optional
        }
    }
}
