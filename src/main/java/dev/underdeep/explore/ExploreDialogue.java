package dev.underdeep.explore;

import dev.underdeep.civ.econ.Economy;
import dev.underdeep.civ.econ.EconomyManager;
import dev.underdeep.civ.econ.Tx;
import dev.underdeep.civ.npc.Dialogue;
import dev.underdeep.civ.npc.InhabitantEntity;
import dev.underdeep.civ.npc.Topics;
import dev.underdeep.guide.Advancements;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 9 conversation services (all optional, never quests):
 * <ul>
 *   <li>rumours — anyone may mention an old place nearby in their own culture's words; the player's journal records a
 *   vague lead (direction and rough distance). Ordinary people know only ordinary places; scholars who know the player
 *   well may hint at stranger ones. Hidden rooms and secret places are never told;</li>
 *   <li>charts — cartographers and navigators sell a surveyor's chart to a place (exact location; can be shared);</li>
 *   <li>identification — scholars, cartographers and merchants identify an encrusted relic for 3 emeralds;</li>
 *   <li>reports — scholars pay for a report of a significant place the player discovered, and for historical records
 *   the player has read, once per place/record per settlement (paid from the treasury, audited as player payments).</li>
 * </ul>
 */
public final class ExploreDialogue {
    private ExploreDialogue() {
    }

    public static final int CHART_PRICE = 6, IDENTIFY_PRICE = 3;
    private static final Map<String, Long> RUMOUR_COOLDOWN = new HashMap<>();

    public static void init() {
    }

    static boolean scholar(String prof) {
        return prof.equals("scholar") || prof.equals("astronomer");
    }

    static boolean charter(String prof) {
        return prof.equals("cartographer") || prof.equals("navigator");
    }

    public static void options(ServerPlayerEntity p, InhabitantEntity ent, Topics.Talk t, List<String> ids, List<String> labels) {
        if (Explorations.of(p.getServerWorld()) == null) {
            return;
        }
        String prof = t.r.profession;
        Dialogue.opt(ids, labels, "x_rumour", "Heard of any old places around here?");
        if (charter(prof)) {
            Dialogue.opt(ids, labels, "x_chart", "Could you draw me a chart to somewhere worth seeing? (" + CHART_PRICE + " emeralds)");
        }
        ItemStack held = p.getMainHandStack();
        if ((scholar(prof) || charter(prof) || prof.equals("merchant")) && held.isOf(ExploreItems.ENCRUSTED_RELIC)) {
            Dialogue.opt(ids, labels, "x_identify", "Can you tell what this is? (" + IDENTIFY_PRICE + " emeralds)");
        }
        if (scholar(prof)) {
            Dialogue.opt(ids, labels, "x_report", "I've been exploring. Would your people pay for what I found?");
        }
    }

    @Nullable
    public static String choose(ServerPlayerEntity p, InhabitantEntity ent, Topics.Talk t, String option) {
        if (!option.startsWith("x_")) {
            return null;
        }
        ServerWorld w = p.getServerWorld();
        ExplorationMap map = Explorations.of(w);
        if (map == null) {
            return "I wouldn't know.";
        }
        Economy e = EconomyManager.economy(t.rec.id);
        String culture = t.rec.civ == null ? "" : t.rec.civ.id();
        switch (option) {
            case "x_rumour" -> {
                String key = p.getUuid() + ":" + t.rec.id + ":" + t.r.index;
                long day = w.getTimeOfDay() / 24000;
                Long last = RUMOUR_COOLDOWN.get(key);
                if (last != null && last == day) {
                    return "I've told you all I know for now. Ask me another day.";
                }
                RUMOUR_COOLDOWN.put(key, day);
                boolean wise = scholar(t.r.profession) && t.bond != null && t.bond.familiarity >= 10;
                Site s = pickSite(map, p, ent.getBlockX(), ent.getBlockZ(), wise ? 900 : 600, wise, false);
                if (s == null) {
                    return pickRumour(culture, "none", null, p, t);
                }
                Discovery.lead(p, s, "rumour:" + t.r.name);
                Advancements.event(p, "rumour_heard", culture);
                String kind = s.family().scale() == ExploreContent.Scale.EXCEPTIONAL ? "exceptional"
                        : s.family().category().name().toLowerCase();
                return pickRumour(culture, kind, s, p, t);
            }
            case "x_chart" -> {
                Site s = pickSite(map, p, ent.getBlockX(), ent.getBlockZ(), 800, false, true);
                if (s == null) {
                    return "I've charted everything I know of around here. Try a cartographer further afield.";
                }
                if (!pay(p, e, CHART_PRICE)) {
                    return "A good chart is " + CHART_PRICE + " emeralds of work.";
                }
                Rewards.give(p, List.of(ExploreItems.chart(s, t.r.name)));
                Advancements.event(p, "chart_bought", s.family().category().name().toLowerCase());
                return "Here. It's an old " + s.family().category().name().toLowerCase().replace("ruin", "ruin") + " I've drawn before. Mind the way down.";
            }
            case "x_identify" -> {
                ItemStack held = p.getMainHandStack();
                if (!held.isOf(ExploreItems.ENCRUSTED_RELIC)) {
                    return "Show me the thing first.";
                }
                if (!pay(p, e, IDENTIFY_PRICE)) {
                    return "Cleaning one of those takes time. " + IDENTIFY_PRICE + " emeralds.";
                }
                held.decrement(1);
                ItemStack out = identify(p);
                Rewards.give(p, List.of(out));
                Rewards.found(p, out);
                Advancements.event(p, "relic_identified", Registries.ITEM.getId(out.getItem()).getPath());
                return "There. Under the crust it's " + out.getName().getString().toLowerCase() + ". Handle it kindly.";
            }
            case "x_report" -> {
                return report(p, t, e, w);
            }
            default -> {
                return null;
            }
        }
    }

    /** A place for a rumour or chart: not discovered by the player, never a secret; ordinary places first. */
    @Nullable
    static Site pickSite(ExplorationMap map, ServerPlayerEntity p, int x, int z, int radius, boolean exceptional, boolean charted) {
        ExplorerData d = ExplorerData.of(p);
        List<Site> pool = new ArrayList<>();
        for (Site s : map.sitesNear(x, z, radius)) {
            ExploreContent.Family f = s.family();
            if (f.category() == ExploreContent.Category.SECRET || d.sites.containsKey(s.id()) || charted && "chart".equals(d.leads.get(s.id()))) {
                continue;
            }
            if (f.scale() == ExploreContent.Scale.EXCEPTIONAL && !exceptional) {
                continue;
            }
            if (f.scale() == ExploreContent.Scale.MINOR) {
                continue;
            }
            pool.add(s);
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(p.getRandom().nextInt(Math.min(4, pool.size())));
    }

    static String pickRumour(String culture, String kind, @Nullable Site s, ServerPlayerEntity p, Topics.Talk t) {
        List<ExploreContent.Rumor> pool = new ArrayList<>();
        for (ExploreContent.Rumor r : ExploreContent.RUMORS) {
            if (r.kind().equals(kind) && (r.culture().equals(culture) || r.culture().equals("any"))) {
                pool.add(r);
            }
        }
        if (pool.isEmpty()) {
            for (ExploreContent.Rumor r : ExploreContent.RUMORS) {
                if (r.kind().equals(kind)) {
                    pool.add(r);
                }
            }
        }
        String text = pool.isEmpty() ? "Nothing worth telling, I'm afraid." : pool.get(t.rnd.nextInt(pool.size())).text();
        if (s != null) {
            text = text.replace("{name}", s.family().name().toLowerCase()).replace("{where}", Discovery.directions(p, s, false));
        }
        return text;
    }

    static ItemStack identify(ServerPlayerEntity p) {
        ExplorationState st = ExplorationState.get(p.getServer());
        List<ExploreContent.Artifact> pool = new ArrayList<>();
        int total = 0;
        for (ExploreContent.Artifact a : ExploreContent.ARTIFACTS.values()) {
            if (!a.unique() && a.weight() > 0) {
                pool.add(a);
                total += a.weight();
            }
        }
        int pick = p.getRandom().nextInt(Math.max(1, total));
        for (ExploreContent.Artifact a : pool) {
            pick -= a.weight();
            if (pick < 0) {
                return new ItemStack(Registries.ITEM.get(Identifier.of("underdeep", a.id())));
            }
        }
        return new ItemStack(ExploreItems.ANCIENT_COIN, 3);
    }

    static String report(ServerPlayerEntity p, Topics.Talk t, @Nullable Economy e, ServerWorld w) {
        if (e == null) {
            return "We've no treasury to speak of, but thank you.";
        }
        ExplorerData d = ExplorerData.of(p);
        ExplorationState st = ExplorationState.get(w.getServer());
        java.util.Set<String> told = st.reported.computeIfAbsent(t.rec.id, k -> new java.util.LinkedHashSet<>());
        ExplorationMap map = Explorations.of(w);
        int owed = 0, places = 0, records = 0;
        List<String> newKeys = new ArrayList<>();
        for (long id : d.sites.keySet()) {
            Site s = map == null ? null : map.byId(id);
            if (s == null || s.family().scale().rank < 1 || s.family().category() == ExploreContent.Category.SECRET) {
                continue;
            }
            String key = "site:" + id;
            if (!told.contains(key) && places < 5) {
                newKeys.add(key);
                owed += s.family().scale().rank >= 2 ? 2 : 1;
                places++;
            }
        }
        for (String r : d.records) {
            String key = "record:" + r;
            if (!told.contains(key) && records < 6) {
                newKeys.add(key);
                owed += 1;
                records++;
            }
        }
        if (owed == 0) {
            return "You've already told me everything you've found. Come back when you've seen something new.";
        }
        int pay = (int) Math.min(owed, Math.floor(e.treasuryBalance));
        if (pay <= 0) {
            return "I'd pay for this gladly, but the treasury is empty just now.";
        }
        e.treasuryBalance -= pay;
        e.playerOut += pay;
        Tx.giveEmeralds(p, pay);
        Tx.log(w.getTime(), "exploration report", t.rec.name, p.getName().getString(), pay, places + " places, " + records + " records");
        told.addAll(newKeys);
        st.markDirty();
        Advancements.event(p, "report_paid", t.rec.civ == null ? "" : t.rec.civ.id());
        return "Fascinating. " + (places > 0 ? places + " place" + (places > 1 ? "s" : "") : "") + (places > 0 && records > 0 ? " and " : "")
                + (records > 0 ? records + " old writing" + (records > 1 ? "s" : "") : "") + " I hadn't heard of. Here's " + pay + " for your trouble.";
    }

    static boolean pay(ServerPlayerEntity p, @Nullable Economy e, int price) {
        if (e == null || !Tx.takeEmeralds(p, price)) {
            return false;
        }
        e.treasuryBalance += price;
        e.playerIn += price;
        return true;
    }
}
