package dev.underdeep.civ.econ;

import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.npc.SettlementState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The journal's trade notes (Stage 6): settlements the player has visited, prices they saw there (marked with the
 * day they were seen — history, not live prices), their contracts, property and shops. Never shows settlements the
 * player has not discovered.
 */
public final class TradeJournal {
    private TradeJournal() {
    }

    static List<Text> lines(ServerPlayerEntity p) {
        ServerWorld w = p.getServer().getOverworld();
        EconomyState st = EconomyState.get(w);
        List<Text> out = new ArrayList<>();
        long day = w.getTime() / 24000;
        Map<Long, EconomyState.Observation> seen = st.observations.getOrDefault(p.getUuid(), Map.of());
        for (SettlementRecord rec : SettlementState.get(w).all().values()) {
            if (!rec.discoveredBy.contains(p.getUuid())) {
                continue;
            }
            Economy e = st.economies.get(rec.id);
            MutableText line = Text.literal(rec.name).formatted(Formatting.AQUA).append(Text.literal(" (" + rec.civ.id().replace('_', ' ') + ")")
                    .formatted(Formatting.GRAY));
            EconomyState.Observation o = seen.get(rec.id);
            if (o != null && !o.prices.isEmpty()) {
                List<String> ps = new ArrayList<>();
                o.prices.entrySet().stream().limit(4).forEach(en -> {
                    Goods.Good g = Goods.get(en.getKey());
                    if (g != null) {
                        ps.add(g.name() + " " + String.format(Locale.ROOT, "%.2f", en.getValue()));
                    }
                });
                line.append(Text.literal("; prices seen on day " + o.time / 24000 + (o.time / 24000 == day ? " (today)" : "") + ": " + String.join(", ", ps))
                        .formatted(Formatting.WHITE));
            } else if (e != null) {
                line.append(Text.literal("; no prices noted yet").formatted(Formatting.DARK_GRAY));
            }
            out.add(line);
        }
        for (EconomyState.Contract k : st.contracts.values()) {
            if (p.getUuid().equals(k.taker) && k.status == EconomyState.Contract.Status.TAKEN) {
                Economy e = st.economies.get(k.settlement);
                out.add(Text.literal("Contract for " + (e == null ? "?" : e.name) + ": " + Participation.describe(st, k) + ", until day " + k.expires / 24000)
                        .formatted(Formatting.YELLOW));
            }
        }
        for (EconomyState.Property pr : st.properties.values()) {
            if (pr.allowed(p.getUuid())) {
                Economy e = st.economies.get(pr.settlement);
                out.add(Text.literal((p.getUuid().equals(pr.owner) ? "Your " : "Shared ") + pr.type + " in " + (e == null ? "?" : e.name) + " at "
                        + (pr.x0 + pr.x1) / 2 + ", " + (pr.z0 + pr.z1) / 2).formatted(Formatting.GREEN));
            }
        }
        for (EconomyState.Business b : st.businesses.values()) {
            if (b.owner.equals(p.getUuid())) {
                out.add(Text.literal(b.name + ": " + b.sales + " items sold, till " + String.format(Locale.ROOT, "%.1f", b.till)).formatted(Formatting.GREEN));
            }
        }
        return out;
    }

    public static int pages(ServerPlayerEntity p) {
        return Math.max(1, (lines(p).size() + 6) / 7);
    }

    public static List<Text> render(ServerPlayerEntity p, int page) {
        List<Text> all = lines(p);
        List<Text> out = new ArrayList<>();
        out.add(Text.translatable("message.underdeep.journal.trade").formatted(Formatting.GOLD));
        for (int i = page * 7; i < Math.min(all.size(), page * 7 + 7); i++) {
            out.add(all.get(i));
        }
        if (all.isEmpty()) {
            out.add(Text.literal("Nothing noted yet.").formatted(Formatting.GRAY));
        }
        return out;
    }
}
