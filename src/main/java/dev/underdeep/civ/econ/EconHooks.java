package dev.underdeep.civ.econ;

import dev.underdeep.civ.mind.Bond;
import dev.underdeep.civ.mind.Memory;
import dev.underdeep.civ.mind.Trait;
import dev.underdeep.civ.npc.InhabitantEntity;
import dev.underdeep.civ.npc.InhabitantManager;
import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.npc.SettlementState;
import dev.underdeep.config.UnderdeepConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Connects inhabitants' trade screens to the Stage 6 market: offers come from the seller's real shelf (or, for a
 * hired shopkeeper, the player business's stock) at current prices with relationship discounts; each completed
 * trade is mirrored on the server's ledger exactly once. Prices seen are remembered for the journal (history).
 */
public final class EconHooks {
    private EconHooks() {
    }

    /** Relationship benefit: personal trust/affection (more with generous merchants) plus a little local reputation. */
    static double discount(SettlementRecord rec, SettlementRecord.InhabitantRecord r, UUID player) {
        double d = 0;
        Bond b = r.mind == null ? null : r.mind.bondIfAny(player);
        if (b != null) {
            d += Math.max(0, (b.trust + b.affection) / 200.0) * 0.1 * (0.6 + r.mind.trait(Trait.GENEROSITY) / 100.0);
        }
        d += reputation(rec, player) * 0.03;
        double cap = 0.12;
        if (dev.underdeep.equipment.Accessories.TRADERS.contains(player)) { // Stage 8 trader's token
            d += 0.05;
            cap = 0.17;
        }
        return Math.max(0, Math.min(cap, d));
    }

    /** Settlement reputation (−1..1): how the inhabitants who know a player regard them (never omniscient). */
    public static double reputation(SettlementRecord rec, UUID player) {
        double sum = 0;
        int n = 0;
        for (SettlementRecord.InhabitantRecord ir : rec.inhabitants) {
            Bond b = ir.present() && ir.mind != null ? ir.mind.bondIfAny(player) : null;
            if (b != null) {
                sum += (b.trust + b.affection * 0.5 - b.resentment) / 150.0;
                n++;
            }
        }
        return n == 0 ? 0 : Math.max(-1, Math.min(1, sum / n));
    }

    @Nullable
    public static TradeOfferList offers(InhabitantEntity e, @Nullable PlayerEntity customer) {
        if (!UnderdeepConfig.get().economyEnabled || !(e.getWorld() instanceof ServerWorld w) || e.exhibit) {
            return null;
        }
        SettlementRecord rec = SettlementState.get(w).get(e.settlementId);
        SettlementRecord.InhabitantRecord r = InhabitantManager.record(e);
        Economy econ = EconomyState.get(w).economies.get(e.settlementId);
        if (rec == null || r == null || econ == null) {
            return null;
        }
        UUID who = customer == null ? new UUID(0, 0) : customer.getUuid();
        double margin = UnderdeepConfig.get().merchantMargin;
        if (r.mind != null && r.mind.employer != null) {
            return businessOffers(w, econ, r, e);
        }
        Economy.Merchant m = econ.merchants.get(r.index);
        if (m == null) {
            e.marketLines.clear();
            return new TradeOfferList();
        }
        TradeOfferList list = Market.offers(econ, m, r.profession, margin, discount(rec, r, who), e.marketLines);
        if (customer != null) {
            observe(w, customer.getUuid(), econ, e);
        }
        return list;
    }

    static TradeOfferList businessOffers(ServerWorld w, Economy econ, SettlementRecord.InhabitantRecord r, InhabitantEntity e) {
        EconomyState st = EconomyState.get(w);
        EconomyState.Business b = Participation.businessOf(st, r.mind.employer, econ.id);
        TradeOfferList list = new TradeOfferList();
        e.marketLines.clear();
        if (b == null) {
            return list;
        }
        for (int i = 0; i < b.stock.size() && list.size() < 12; i++) {
            ItemStack s = b.stock.get(i);
            Goods.Good g = Goods.of(s);
            if (g == null || s.isDamaged()) {
                continue;
            }
            double ask = econ.stock(g.id()).price * b.priceFactor();
            int[] bundle = Pricing.sellBundle(ask);
            int uses = s.getCount() / bundle[1];
            if (uses <= 0) {
                continue;
            }
            list.add(new TradeOffer(new TradedItem(Goods.currency, bundle[0]), g.stack(bundle[1]), uses, 0, 0f));
            e.marketLines.add(new Market.Line("biz:" + b.id + ":" + i + ":" + g.id(), true, bundle[1], bundle[0]));
        }
        return list;
    }

    static void observe(ServerWorld w, UUID player, Economy econ, InhabitantEntity e) {
        EconomyState st = EconomyState.get(w);
        EconomyState.Observation o = new EconomyState.Observation();
        o.time = w.getTime();
        for (Market.Line l : e.marketLines) {
            if (!l.good().startsWith("biz:")) {
                o.prices.put(l.good(), econ.stock(l.good()).price);
            }
        }
        EconomyState.Observation prev = st.observations.computeIfAbsent(player, k -> new java.util.HashMap<>()).get(econ.id);
        if (prev != null) {
            prev.prices.forEach(o.prices::putIfAbsent);
        }
        while (o.prices.size() > 24) {
            o.prices.remove(o.prices.keySet().iterator().next());
        }
        st.observations.get(player).put(econ.id, o);
        st.markDirty();
    }

    /** Mirrors a completed trade on the ledger; returns true if it was a Stage 6 trade. */
    public static boolean traded(InhabitantEntity e, TradeOffer offer) {
        if (!(e.getWorld() instanceof ServerWorld w)) {
            return false;
        }
        int idx = e.getOffers().indexOf(offer);
        if (idx < 0 || idx >= e.marketLines.size()) {
            return false;
        }
        EconomyState st = EconomyState.get(w);
        Economy econ = st.economies.get(e.settlementId);
        SettlementRecord.InhabitantRecord r = InhabitantManager.record(e);
        if (econ == null || r == null) {
            return false;
        }
        Market.Line l = e.marketLines.get(idx);
        long now = w.getTime();
        if (l.good().startsWith("biz:")) {
            String[] parts = l.good().split(":", 4);
            EconomyState.Business b = st.businesses.get(Long.parseLong(parts[1]));
            int slot = Integer.parseInt(parts[2]);
            if (b != null && slot < b.stock.size()) {
                b.stock.get(slot).decrement(l.units());
                b.till += l.emeralds();
                b.sales += l.units();
                b.revenue += l.emeralds();
                b.history.addFirst("day " + now / 24000 + ": sold " + l.units() + " " + parts[3] + " to a player");
            }
        } else {
            Economy.Merchant m = econ.merchants.get(r.index);
            if (m == null) {
                return false;
            }
            Market.apply(econ, m, l, now);
        }
        PlayerEntity customer = e.getCustomer();
        if (customer != null && r.mind != null) {
            r.mind.memories.remember(Memory.Type.TRADED, "p:" + customer.getUuid(), l.good(), now, UnderdeepConfig.get().inhabitantMemoryLimit, 0.4);
            r.mind.bond(customer.getUuid()).adjust(now, 1, 1, 0, 0, 0, 0, true);
        }
        st.markDirty();
        SettlementState.get(w).markDirty();
        return true;
    }
}
