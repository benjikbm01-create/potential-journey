package dev.underdeep.civ.econ;

import net.minecraft.item.ItemStack;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Merchants and the market interface (Stage 6). Each seller owns a shelf bought wholesale from the settlement
 * ledger (till → treasury) and keeps essentials in reserve for households; players see only what is really on the
 * shelf. Trades are vanilla merchant offers built from the shelf at the current bounded price; every completed
 * trade is mirrored exactly on the shelf and till (server side), so stock is finite and persistent.
 */
public final class Market {
    private Market() {
    }

    /** What a profession sells (and buys back). Guards and scholars have no shop. */
    public static Set<Goods.Category> categories(String profession) {
        return switch (profession) {
            case "farmer" -> EnumSet.of(Goods.Category.GRAIN, Goods.Category.PRODUCE);
            case "fisher", "boatwright" -> EnumSet.of(Goods.Category.FISH, Goods.Category.MEAT, Goods.Category.TOOLS);
            case "hunter", "rancher" -> EnumSet.of(Goods.Category.MEAT, Goods.Category.ANIMAL_PRODUCT, Goods.Category.FIBER);
            case "blacksmith", "engineer" -> EnumSet.of(Goods.Category.TOOLS, Goods.Category.EQUIPMENT, Goods.Category.METAL);
            case "smelter", "miner" -> EnumSet.of(Goods.Category.ORE, Goods.Category.METAL, Goods.Category.FUEL, Goods.Category.STONE);
            case "carpenter", "forester" -> EnumSet.of(Goods.Category.TIMBER, Goods.Category.LUMBER, Goods.Category.FUEL);
            case "stonemason" -> EnumSet.of(Goods.Category.STONE, Goods.Category.BUILDING);
            case "cook", "baker", "innkeeper", "brewer" -> EnumSet.of(Goods.Category.PREPARED_FOOD, Goods.Category.PRODUCE);
            case "herbalist", "healer", "alchemist", "mycologist" -> EnumSet.of(Goods.Category.HERB, Goods.Category.MEDICINE, Goods.Category.PRODUCE);
            case "gemcutter", "artisan" -> EnumSet.of(Goods.Category.GEM, Goods.Category.DECORATIVE, Goods.Category.SPECIALTY);
            case "lampwright" -> EnumSet.of(Goods.Category.BUILDING, Goods.Category.DECORATIVE, Goods.Category.METAL);
            case "tailor" -> EnumSet.of(Goods.Category.FIBER, Goods.Category.ANIMAL_PRODUCT, Goods.Category.EQUIPMENT);
            case "cartographer", "navigator", "astronomer" -> EnumSet.of(Goods.Category.LUXURY, Goods.Category.SPECIALTY);
            case "merchant" -> EnumSet.of(Goods.Category.PREPARED_FOOD, Goods.Category.GRAIN, Goods.Category.PRODUCE, Goods.Category.FUEL,
                    Goods.Category.TOOLS, Goods.Category.BUILDING, Goods.Category.MEDICINE, Goods.Category.LUXURY, Goods.Category.METAL,
                    Goods.Category.GEM, Goods.Category.SPECIALTY, Goods.Category.FIBER, Goods.Category.LUMBER);
            default -> EnumSet.noneOf(Goods.Category.class);
        };
    }

    public static boolean sells(String profession) {
        return !categories(profession).isEmpty();
    }

    static int shelfTarget(EconomySim.Ctx c, Goods.Good g, boolean general) {
        Economy.Stock s = c.e.stock(g.id());
        double base = g.value() >= 2 ? 2 : g.value() >= 0.6 ? 4 : g.value() >= 0.2 ? 8 : 16;
        double t = base * (0.6 + c.e.score) * (general ? 1.3 : 1) * Math.min(1.5, (s.free() + 1) / (s.desired + 1));
        return (int) Math.max(0, Math.min(32, Math.round(t)));
    }

    /** Restocks a shelf from the ledger at the local price; sells overflow back. Integer units only. */
    static void restock(EconomySim.Ctx c, Economy.Merchant m, double hours) {
        if (m.index < 0 || m.index >= c.rec.inhabitants.size() || !c.rec.inhabitants.get(m.index).present()) {
            return;
        }
        String prof = c.rec.inhabitants.get(m.index).profession;
        boolean general = prof.equals("merchant");
        Economy e = c.e;
        for (Goods.Category cat : categories(prof)) {
            for (Goods.Good g : Goods.in(cat)) {
                int have = m.shelf.getOrDefault(g.id(), 0);
                int target = shelfTarget(c, g, general);
                Economy.Stock s = e.stock.get(g.id());
                if (have < target && s != null) {
                    double reserve = s.desired * (g.food() ? 0.6 : 0.25);
                    int units = (int) Math.floor(Math.min(target - have, s.free() - reserve));
                    units = Math.min(units, (int) Math.floor(m.till / Math.max(0.01, s.price)));
                    if (units > 0 && Tx.pay(m, e.treasury, units * s.price, c.now, "wholesale")) {
                        e.take(g.id(), units);
                        m.shelf.merge(g.id(), units, Integer::sum);
                    }
                } else if (have > target * 2 + 4 && s != null) {
                    int back = have - target;
                    double pay = back * s.price * 0.9;
                    if (Tx.pay(e.treasury, m, pay, c.now, "sellback")) {
                        e.put(g.id(), back);
                        m.shelf.put(g.id(), have - back);
                    }
                }
            }
        }
        m.shelf.values().removeIf(v -> v <= 0);
        m.lastRestock = c.now;
        // profit beyond working capital goes home
        if (m.till > 40) {
            Economy.Household h = e.households.get(c.rec.inhabitants.get(m.index).home);
            if (h != null) {
                Tx.pay(m, h, (m.till - 40) * 0.25 * Math.min(1, hours / 24.0), c.now, "profit");
            }
        }
    }

    /** A trade offer and what it means for the ledger. */
    public record Line(String good, boolean merchantSells, int units, int emeralds) {
    }

    /**
     * Builds the offers for a player: up to 12 shelf goods the merchant sells and up to 6 goods the settlement needs
     * (bought at the bid). {@code discount} is the relationship benefit (0–0.12).
     */
    public static TradeOfferList offers(Economy e, Economy.Merchant m, String profession, double margin, double discount, List<Line> lines) {
        TradeOfferList list = new TradeOfferList();
        lines.clear();
        List<String> goods = new ArrayList<>(m.shelf.keySet());
        goods.sort((a, b) -> Integer.compare(m.shelf.get(b), m.shelf.get(a)));
        for (String id : goods) {
            if (list.size() >= 12) {
                break;
            }
            Goods.Good g = Goods.get(id);
            int have = m.shelf.getOrDefault(id, 0);
            if (g == null || have <= 0) {
                continue;
            }
            double ask = Pricing.ask(e.stock(id).price, margin, discount);
            int[] b = Pricing.sellBundle(ask);
            int uses = have / b[1];
            if (uses <= 0) {
                continue;
            }
            list.add(new TradeOffer(new TradedItem(Goods.currency, b[0]), g.stack(b[1]), uses, 0, 0f));
            lines.add(new Line(id, true, b[1], b[0]));
        }
        // what the settlement is short of (never tools/equipment: damaged items would be accepted)
        List<Goods.Good> wants = new ArrayList<>();
        for (Goods.Category cat : categories(profession)) {
            if (cat == Goods.Category.TOOLS || cat == Goods.Category.EQUIPMENT) {
                continue;
            }
            for (Goods.Good g : Goods.in(cat)) {
                Economy.Stock s = e.stock(g.id());
                if (s.free() + m.shelf.getOrDefault(g.id(), 0) < s.desired * 1.5) {
                    wants.add(g);
                }
            }
        }
        wants.sort((a, b) -> Double.compare(e.stock(b.id()).desired / (e.stock(b.id()).free() + 1),
                e.stock(a.id()).desired / (e.stock(a.id()).free() + 1)));
        int buys = 0;
        for (Goods.Good g : wants) {
            if (buys >= 6) {
                break;
            }
            Economy.Stock s = e.stock(g.id());
            double bid = Pricing.bid(s.price, margin, discount * 0.4);
            int[] b = Pricing.buyBundle(bid);
            int wantUnits = (int) Math.max(0, s.desired * 2 - s.free() - m.shelf.getOrDefault(g.id(), 0));
            int uses = Math.min(wantUnits / b[0], (int) Math.floor(m.till / b[1]));
            if (uses <= 0) {
                continue;
            }
            list.add(new TradeOffer(new TradedItem(g.item(), b[0]), new ItemStack(Goods.currency, b[1]), uses, 0, 0f));
            lines.add(new Line(g.id(), false, b[0], b[1]));
            buys++;
        }
        return list;
    }

    /** Mirrors one completed vanilla trade on the shelf, till and settlement counters. */
    public static void apply(Economy e, Economy.Merchant m, Line l, long now) {
        if (l.merchantSells()) {
            int have = m.shelf.getOrDefault(l.good(), 0);
            m.shelf.put(l.good(), Math.max(0, have - l.units()));
            m.till += l.emeralds();
            e.playerIn += l.emeralds();
            Tx.log(now, "sale", "player", m.label(), l.emeralds(), l.good() + " x" + l.units());
        } else {
            m.shelf.merge(l.good(), l.units(), Integer::sum);
            m.till -= l.emeralds();
            e.playerOut += l.emeralds();
            Tx.log(now, "purchase", m.label(), "player", l.emeralds(), l.good() + " x" + l.units());
        }
    }

    /** Spoken availability label for a good. */
    public static String label(Economy e, Goods.Good g) {
        Economy.Stock s = e.stock.get(g.id());
        if (s == null || s.free() < 1) {
            return "out of stock";
        }
        String a = s.free() >= s.desired * 2 ? "plenty" : s.free() < s.desired * 0.5 ? "scarce" : "normal";
        String o = s.rI > s.rP + 0.05 ? "imported" : s.rP > 0.05 ? "local" : "";
        return o.isEmpty() ? a : a + ", " + o;
    }
}
