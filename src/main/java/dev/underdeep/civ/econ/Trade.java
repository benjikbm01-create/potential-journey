package dev.underdeep.civ.econ;

import dev.underdeep.civ.Civilization;
import dev.underdeep.civ.mind.Memory;
import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.npc.SettlementState;
import dev.underdeep.civ.settlement.SettlementCategory;
import dev.underdeep.civ.settlement.SettlementMap;
import dev.underdeep.civ.settlement.SettlementSite;
import dev.underdeep.civ.settlement.Settlements;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.world.region.Danger;
import dev.underdeep.world.terrain.CavernGraph;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.TerrainColumn;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Regional trade (Stage 6): a sparse network (each settlement linked to its nearest partners on the same cavern
 * layer, towns and cities also to distant hubs) with routes through the cavern graph; settlement-initiated imports
 * when reserves fall short (food, construction materials, missing workshop inputs) and exports of real surpluses
 * to partners that need them; recurring agreements; shipments that carry their cargo and escrowed payment and
 * deliver exactly once; route disruptions; civilization diplomacy.
 */
public final class Trade {
    private Trade() {
    }

    // ================================================================== network

    public static void connect(EconomyManager mgr, EconomyState st, Economy e) {
        SettlementMap map = Settlements.of(mgr.world);
        if (map == null) {
            return;
        }
        SettlementSite me = map.byId(e.id);
        if (me == null) {
            return;
        }
        boolean hub = me.category().ordinal() >= SettlementCategory.TOWN.ordinal();
        List<Object[]> cands = new ArrayList<>();
        for (Economy o : st.economies.values()) {
            SettlementSite s = map.byId(o.id);
            if (o == e || s == null || s.layer() != me.layer()) {
                continue;
            }
            double d = Math.hypot(s.x() - me.x(), s.z() - me.z());
            boolean otherHub = s.category().ordinal() >= SettlementCategory.TOWN.ordinal();
            if (d <= 1800 || hub && otherHub && d <= 3500) {
                cands.add(new Object[]{d, o, s});
            }
        }
        cands.sort((a, b) -> Double.compare((Double) a[0], (Double) b[0]));
        int added = 0, existing = st.connectionsOf(e.id).size();
        for (Object[] cd : cands) {
            if (existing + added >= (hub ? 5 : 3)) {
                break;
            }
            Economy o = (Economy) cd[1];
            if (st.connection(e.id, o.id) != null || st.connectionsOf(o.id).size() >= 6) {
                continue;
            }
            SettlementSite s = (SettlementSite) cd[2];
            EconomyState.Connection k = new EconomyState.Connection();
            k.a = e.id;
            k.b = o.id;
            route(map.terrain(), me, s, k);
            k.water = (me.waterNearby() || me.overWater()) && (s.waterNearby() || s.overWater()) && waterRegion(me) && waterRegion(s);
            EconomyState.Relation rel = st.relation(e.civ, o.civ);
            k.trust = rel.trust * 0.5;
            st.connections.add(k);
            st.networkVersion++;
            added++;
        }
    }

    static boolean waterRegion(SettlementSite s) {
        return switch (s.region()) {
            case FLOODED_DEPTHS, SUNKEN_GARDENS, EMERALD_WETLANDS -> true;
            default -> false;
        };
    }

    /** Route through the cavern graph (A*, bounded); falls back to a straight line. Also measures danger. */
    static void route(UnderdeepTerrain t, SettlementSite from, SettlementSite to, EconomyState.Connection k) {
        CavernGraph g = t.graph(from.layer());
        CavernGraph.Node start = g.nearestNode(from.x(), from.z()), goal = g.nearestNode(to.x(), to.z());
        List<CavernGraph.Node> path = new ArrayList<>();
        if (start != null && goal != null) {
            Map<Long, Double> cost = new HashMap<>();
            Map<Long, CavernGraph.Node> prev = new HashMap<>();
            Map<Long, CavernGraph.Node> nodes = new HashMap<>();
            PriorityQueue<Object[]> open = new PriorityQueue<>((a, b) -> Double.compare((Double) a[0], (Double) b[0]));
            cost.put(start.key(), 0.0);
            nodes.put(start.key(), start);
            open.add(new Object[]{0.0, start});
            Set<Long> closed = new HashSet<>();
            int expansions = 0;
            while (!open.isEmpty() && expansions++ < 600) {
                CavernGraph.Node n = (CavernGraph.Node) open.poll()[1];
                if (!closed.add(n.key())) {
                    continue;
                }
                if (n.key() == goal.key()) {
                    for (CavernGraph.Node p = n; p != null; p = prev.get(p.key())) {
                        path.add(0, p);
                    }
                    break;
                }
                for (CavernGraph.Edge ed : g.edgesOf(n.cellX(), n.cellZ())) {
                    CavernGraph.Node m = ed.a().key() == n.key() ? ed.b() : ed.a();
                    if (m == null) {
                        continue;
                    }
                    double step = Math.hypot(m.x() - n.x(), m.z() - n.z()) * (1 + m.region().danger().ordinal() * 0.25);
                    double c2 = cost.get(n.key()) + step;
                    if (c2 < cost.getOrDefault(m.key(), Double.MAX_VALUE)) {
                        cost.put(m.key(), c2);
                        prev.put(m.key(), n);
                        nodes.put(m.key(), m);
                        open.add(new Object[]{c2 + Math.hypot(goal.x() - m.x(), goal.z() - m.z()), m});
                    }
                }
            }
        }
        k.route.clear();
        k.route.add(new double[]{from.x(), from.floorY() + 1, from.z()});
        double danger = 0;
        int n = 0;
        for (CavernGraph.Node p : path) {
            k.route.add(new double[]{p.x(), floor(t, from.layer(), (int) p.x(), (int) p.z()), p.z()});
            danger += p.region().danger() == Danger.LOW ? 0 : p.region().danger() == Danger.MEDIUM ? 0.25 : p.region().danger() == Danger.HIGH ? 0.6 : 1;
            n++;
        }
        k.route.add(new double[]{to.x(), to.floorY() + 1, to.z()});
        k.danger = n == 0 ? 0.2 : Math.min(1, danger / n);
        double len = 0;
        for (int i = 0; i + 1 < k.route.size(); i++) {
            len += Math.hypot(k.route.get(i + 1)[0] - k.route.get(i)[0], k.route.get(i + 1)[2] - k.route.get(i)[2]);
        }
        k.distance = len;
    }

    static double floor(UnderdeepTerrain t, CavernLayer layer, int x, int z) {
        TerrainColumn c = t.column(x, z);
        return Math.floor(layer == CavernLayer.UPPER ? c.upperFloor : c.deepFloor) + 1;
    }

    // ================================================================== daily planning

    public static void daily(EconomyManager mgr, EconomySim.Ctx c) {
        Economy e = c.e;
        // inputs that stopped workshops
        Set<String> missing = new HashSet<>();
        for (Economy.Workplace w : e.workplaces.values()) {
            if (w.status == Economy.WorkStatus.IDLE && !w.missing.isEmpty()) {
                missing.add(w.missing);
            }
        }
        for (String m : missing) {
            if (m.equals("@FUEL")) {
                requestImport(mgr, c, Goods.Category.FUEL, 16);
            } else if (m.startsWith("@")) {
                try {
                    requestImport(mgr, c, Goods.Category.valueOf(m.substring(1)), 16);
                } catch (IllegalArgumentException ignored) {
                    // not a category
                }
            } else {
                requestGood(mgr, c, m, 12, false);
            }
        }
        // construction materials
        for (Project p : e.projects) {
            if (p.status == Project.Status.MATERIALS) {
                for (var en : p.required.entrySet()) {
                    double lack = en.getValue() - p.delivered.getOrDefault(en.getKey(), 0.0);
                    Goods.Category cat = Goods.Category.valueOf(en.getKey().toUpperCase(java.util.Locale.ROOT));
                    if (lack > 0 && e.freeIn(cat) < lack) {
                        requestImport(mgr, c, cat, lack - e.freeIn(cat));
                    }
                }
            }
        }
        // medicine and fuel reserves
        if (e.freeIn(Goods.Category.MEDICINE) < 2) {
            requestImport(mgr, c, Goods.Category.MEDICINE, 4);
        }
        if (e.freeIn(Goods.Category.FUEL) < 6) {
            requestImport(mgr, c, Goods.Category.FUEL, 16);
        }
        agreements(mgr, c);
        if (c.now / 24000 % 2 == e.id % 2) {
            planExports(mgr, c);
        }
    }

    /** Asks partners for a category (cheapest suitable good the supplier has spare). */
    public static void requestImport(EconomyManager mgr, EconomySim.Ctx c, Goods.Category cat, double units) {
        Goods.Good best = null;
        double bestPrice = Double.MAX_VALUE;
        for (EconomyState.Connection k : c.state.connectionsOf(c.e.id)) {
            Economy o = c.state.economies.get(k.other(c.e.id));
            if (o == null) {
                continue;
            }
            for (Goods.Good g : Goods.in(cat)) {
                if (spare(o, g) >= Math.min(units, 6) && o.stock(g.id()).price < bestPrice) {
                    bestPrice = o.stock(g.id()).price;
                    best = g;
                }
            }
        }
        if (best != null) {
            requestGood(mgr, c, best.id(), units, false);
        }
    }

    /** What a supplier can spare of a good: food keeps 1.5× desired, everything else 1.3×. */
    static double spare(Economy o, Goods.Good g) {
        Economy.Stock s = o.stock.get(g.id());
        if (s == null) {
            return 0;
        }
        double keep = s.desired * (g.food() ? 1.5 : 1.3) + (g.food() ? 6 : 2);
        return Math.max(0, s.free() - keep);
    }

    static boolean strategic(Goods.Good g) {
        return g.category() == Goods.Category.METAL || g.category() == Goods.Category.EQUIPMENT || g.category() == Goods.Category.TOOLS
                || g.category() == Goods.Category.GEM;
    }

    /** The buyer chooses a supplier among its partners and, if it can pay, a shipment departs. */
    public static boolean requestGood(EconomyManager mgr, EconomySim.Ctx c, String good, double units, boolean aid) {
        Economy buyer = c.e;
        Goods.Good g = Goods.get(good);
        if (g == null || units < 1) {
            return false;
        }
        Long last = buyer.lastImport.get(good);
        Long exported = buyer.lastExport.get(good);
        if (last != null && c.now - last < 24000 || exported != null && c.now - exported < 24000L * 3) {
            return false; // no back-and-forth, no repeated orders
        }
        if (inFlight(c.state, buyer.id, good)) {
            return false;
        }
        EconomyState.Connection bestK = null;
        Economy bestO = null;
        double bestScore = Double.MAX_VALUE;
        for (EconomyState.Connection k : c.state.connectionsOf(buyer.id)) {
            if (k.blockedUntil > c.now) {
                continue;
            }
            Economy o = c.state.economies.get(k.other(buyer.id));
            if (o == null || spare(o, g) < Math.min(units, 4)) {
                continue;
            }
            EconomyState.Relation rel = c.state.relation(buyer.civ, o.civ);
            if (strategic(g) && rel.restrictedUntil > c.now) {
                continue;
            }
            Long oi = o.lastImport.get(good);
            if (oi != null && c.now - oi < 24000L * 3) {
                continue;
            }
            double unit = o.stock(good).price * (1.05 - rel.attitude() * 0.08) + fee(k, 1);
            double score = unit * (1 + k.danger * 0.5) * (1.2 - k.reliability / 250.0);
            if (score < bestScore) {
                bestScore = score;
                bestK = k;
                bestO = o;
            }
        }
        if (bestK == null) {
            return false;
        }
        return ship(mgr, c.state, bestO, buyer, bestK, good, Math.min(units, spare(bestO, g)), aid, c.now, "import");
    }

    static double fee(EconomyState.Connection k, double units) {
        return units * 0.0035 * k.distance / 100.0 * UnderdeepConfig.get().transportCost * (k.water ? 0.6 : 1) * (1 + k.danger);
    }

    static boolean inFlight(EconomyState st, long buyer, String good) {
        for (Shipment s : st.shipments.values()) {
            if (s.dest == buyer && s.status == Shipment.Status.TRAVELLING && s.cargo.containsKey(good)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates and dispatches a shipment: cargo leaves the supplier's ledger now, the buyer's payment moves into the
     * shipment's escrow now, and both are settled exactly once on arrival (or refunded/returned if it fails).
     */
    static boolean ship(EconomyManager mgr, EconomyState st, Economy from, Economy to, EconomyState.Connection k, String good, double units,
                        boolean aid, long now, String reason) {
        UnderdeepConfig cfg = UnderdeepConfig.get();
        long flying = st.shipments.values().stream().filter(Shipment::moving).count();
        if (flying >= cfg.maxShipments) {
            return false;
        }
        Shipment.Method method = k.water ? Shipment.Method.BOAT : units > 24 ? Shipment.Method.PACK_TRAIN : Shipment.Method.PORTER;
        int u = (int) Math.floor(Math.min(units, method.capacity));
        if (u < 1) {
            return false;
        }
        double unitPrice = from.stock(good).price * (aid ? 0.5 : 1.0) * (1.05 - st.relation(from.civ, to.civ).attitude() * 0.08);
        double price = unitPrice * u, transport = fee(k, u);
        if (to.treasuryBalance < price + transport) {
            u = (int) Math.floor(to.treasuryBalance / Math.max(0.01, unitPrice + transport / u));
            if (u < 1) {
                return false;
            }
            price = unitPrice * u;
            transport = fee(k, u);
        }
        double taken = from.take(good, u);
        if (taken < u - 1e-6) {
            from.put(good, taken);
            return false;
        }
        Shipment s = new Shipment();
        s.id = st.newId();
        if (!Tx.pay(to.treasury, s, price + transport, now, "trade escrow")) {
            from.put(good, taken);
            return false;
        }
        to.tradeOut += price + transport;
        from.stock(good).exported += u;
        from.lastExport.put(good, now);
        to.lastImport.put(good, now);
        s.origin = from.id;
        s.dest = to.id;
        s.method = method;
        s.status = Shipment.Status.TRAVELLING;
        s.cargo.put(good, (double) u);
        s.price = price;
        s.fee = transport;
        s.aid = aid;
        s.reason = reason;
        s.route.addAll(from.id == k.a ? k.route : reversed(k.route));
        s.length = k.distance;
        s.created = s.departed = s.lastUpdate = now;
        s.animals = method == Shipment.Method.PACK_TRAIN ? 1 + u / 48 : 0;
        crew(mgr, from, s, k.danger);
        st.shipments.put(s.id, s);
        if (k.danger > 0.3 && !k.water) {
            EconomySim.Ctx oc0 = mgr.ctx(from);
            if (oc0 != null) {
                Participation.postEscort(oc0, s, to);
            }
        }
        k.shipments++;
        Goods.Good g = Goods.get(good);
        EconomySim.Ctx oc = mgr.ctx(from);
        if (oc != null) {
            Daily.event(oc, "CARAVAN_DEPARTED", "s:" + s.id, (s.method == Shipment.Method.BOAT ? "a boat" : "a caravan") + " left " + from.name + " for "
                    + to.name + " with " + u + " " + (g == null ? good : g.name()), 24000);
        }
        st.markDirty();
        return true;
    }

    static List<double[]> reversed(List<double[]> r) {
        List<double[]> out = new ArrayList<>(r);
        java.util.Collections.reverse(out);
        return out;
    }

    /** A leader (a trader, traveller or merchant of the origin) and guards for dangerous routes. */
    static void crew(EconomyManager mgr, Economy from, Shipment s, double danger) {
        SettlementRecord rec = SettlementState.get(mgr.world).get(from.id);
        if (rec == null) {
            return;
        }
        String[] leaders = {"merchant", "navigator", "hunter", "cartographer", "fisher", "rancher"};
        for (String prof : leaders) {
            for (SettlementRecord.InhabitantRecord ir : rec.inhabitants) {
                if (s.leader < 0 && ir.present() && ir.mind != null && ir.profession.equals(prof) && free(ir)) {
                    s.leader = ir.index;
                    ir.mind.journey = s.id;
                }
            }
        }
        int wanted = danger > 0.5 ? 2 : danger > 0.2 ? 1 : 0;
        for (SettlementRecord.InhabitantRecord ir : rec.inhabitants) {
            if (s.guards.size() >= wanted) {
                break;
            }
            if (ir.present() && ir.mind != null && ir.profession.equals("guard") && free(ir)) {
                s.guards.add(ir.index);
                ir.mind.journey = s.id;
            }
        }
    }

    static boolean free(SettlementRecord.InhabitantRecord ir) {
        return ir.mind.journey == 0 && ir.mind.companionOf == null && ir.mind.employer == null && ir.mind.age == Mind.Age.ADULT
                && ir.mind.health > 0.6f && ir.mind.status == Mind.Status.ACTIVE;
    }

    public static double surplusValue(EconomySim.Ctx c) {
        double v = 0;
        for (Goods.Good g : Goods.all()) {
            v += spare(c.e, g) > 8 ? spare(c.e, g) * g.value() : 0;
        }
        return v;
    }

    /** Sells real surpluses to partners that are short (the partner pays). */
    public static void planExports(EconomyManager mgr, EconomySim.Ctx c) {
        for (EconomyState.Connection k : c.state.connectionsOf(c.e.id)) {
            Economy o = c.state.economies.get(k.other(c.e.id));
            if (o == null || k.blockedUntil > c.now) {
                continue;
            }
            for (Goods.Good g : Goods.all()) {
                double sp = spare(c.e, g);
                if (sp < 8) {
                    continue;
                }
                Economy.Stock os = o.stock(g.id());
                if (os.free() < os.desired * 0.6 && !inFlight(c.state, o.id, g.id())) {
                    Long last = o.lastImport.get(g.id());
                    if (last == null || c.now - last >= 24000) {
                        ship(mgr, c.state, c.e, o, k, g.id(), Math.min(sp, os.desired * 2 - os.free()), false, c.now, "export");
                        return; // one export decision per day
                    }
                }
            }
        }
    }

    /** The most needed good (for contracts and dialogue), or null. */
    @Nullable
    public static Goods.Good shortage(EconomySim.Ctx c) {
        Goods.Good best = null;
        double worst = 0.5;
        for (Goods.Good g : Goods.all()) {
            Economy.Stock s = c.e.stock.get(g.id());
            if (s == null || s.desired < 3) {
                continue;
            }
            double r = s.free() / s.desired;
            if (r < worst && (g.food() || s.rC > 0.5 || g.category() == Goods.Category.FUEL)) {
                worst = r;
                best = g;
            }
        }
        return best;
    }

    /** Repeated deliveries become agreements; due agreements dispatch (or count a failure). */
    static void agreements(EconomyManager mgr, EconomySim.Ctx c) {
        long day = c.now / 24000;
        for (EconomyState.Connection k : c.state.connectionsOf(c.e.id)) {
            for (var it = k.agreements.iterator(); it.hasNext(); ) {
                EconomyState.Agreement a = it.next();
                if (a.buyer != c.e.id || day < a.nextDay) {
                    continue;
                }
                Economy sup = c.state.economies.get(a.supplier);
                Goods.Good g = Goods.get(a.good);
                a.nextDay = day + a.interval;
                if (sup == null || g == null || spare(sup, g) < a.units * 0.5 || !ship(mgr, c.state, sup, c.e, k, a.good, Math.min(a.units, spare(sup, g)),
                        a.aid, c.now, "agreement")) {
                    a.failures++;
                    k.reliability = Math.max(0, k.reliability - 6);
                    c.state.relation(c.e.civ, sup == null ? c.e.civ : sup.civ).reliability -= 2;
                } else {
                    a.remaining--;
                }
                if (a.remaining <= 0 || a.failures >= 3) {
                    it.remove();
                    Daily.event(c, "AGREEMENT_ENDED", a.good, "the " + (g == null ? a.good : g.name()) + " agreement with "
                            + (sup == null ? "a partner" : sup.name) + " has ended", 24000);
                }
            }
        }
    }

    static void maybeAgreement(EconomyState st, EconomyState.Connection k, Shipment s, long now) {
        if (s.cargo.isEmpty()) {
            return;
        }
        String good = s.cargo.keySet().iterator().next();
        for (EconomyState.Agreement a : k.agreements) {
            if (a.buyer == s.dest && a.good.equals(good)) {
                return;
            }
        }
        int same = 0;
        for (Shipment o : st.shipments.values()) {
            if (o != s && o.origin == s.origin && o.dest == s.dest && o.cargo.containsKey(good) && now - o.created < 24000L * 6) {
                same++;
            }
        }
        if (same >= 1 || k.shipments >= 3 && k.reliability > 60) {
            EconomyState.Agreement a = new EconomyState.Agreement();
            a.supplier = s.origin;
            a.buyer = s.dest;
            a.good = good;
            a.units = (int) Math.max(4, s.units());
            a.interval = 2;
            a.remaining = 4;
            a.nextDay = now / 24000 + 2;
            a.aid = s.aid;
            k.agreements.add(a);
            Economy b = st.economies.get(s.dest), o = st.economies.get(s.origin);
            if (b != null && o != null) {
                st.relation(b.civ, o.civ).cooperation = Math.min(100, st.relation(b.civ, o.civ).cooperation + 3);
                st.note(now, b.name + " and " + o.name + " agreed on regular deliveries of " + Goods.get(good).name());
                b.event("TRADE_AGREEMENT", now, good, b.name + " agreed with " + o.name + " on regular deliveries of " + Goods.get(good).name(), 24000L * 3);
            }
        }
    }

    // ================================================================== shipments

    /** Abstract travel of every moving shipment not currently embodied as a caravan (every second). */
    public static void tickShipments(EconomyManager mgr, EconomyState st, int ticks) {
        long now = mgr.world.getTime();
        long tod = Math.floorMod(mgr.world.getTimeOfDay(), 24000L);
        boolean night = tod >= 13000 && tod < 23000;
        for (Shipment s : new ArrayList<>(st.shipments.values())) {
            if (!s.moving()) {
                if (s.status == Shipment.Status.DONE || s.status == Shipment.Status.LOST) {
                    if (now - s.lastUpdate > 24000L * 2) {
                        st.shipments.remove(s.id);
                    }
                }
                continue;
            }
            if (Caravans.embodied(s.id)) {
                s.lastUpdate = now; // the physical caravan drives progress
            } else if (now >= s.delayedUntil) {
                double speed = s.method.speed * (night && s.method != Shipment.Method.BOAT ? 0.35 : 1) * Math.max(0.3, s.condition);
                s.progress += speed * ticks / 1000.0;
                s.lastUpdate = now;
            }
            // route conditions: once per travelled in-game hour on average
            EconomyState.Connection k = st.connection(s.origin, s.dest);
            if (k != null && s.status == Shipment.Status.TRAVELLING && mgr.rnd.nextDouble() < ticks / 1000.0 * k.danger * 0.05
                    * UnderdeepConfig.get().eventFrequency) {
                incident(mgr, st, s, k, now);
            }
            if (s.progress >= s.length) {
                if (s.status == Shipment.Status.TRAVELLING) {
                    deliver(mgr, st, s, now);
                } else {
                    home(mgr, st, s, now);
                }
            }
            st.markDirty();
        }
    }

    static void incident(EconomyManager mgr, EconomyState st, Shipment s, EconomyState.Connection k, long now) {
        double r = mgr.rnd.nextDouble();
        Economy o = st.economies.get(s.origin), d = st.economies.get(s.dest);
        EconomySim.Ctx dc = d == null ? null : mgr.ctx(d);
        if (r < 0.5) {
            s.delayedUntil = now + 2000 + mgr.rnd.nextInt(3000);
            s.incident = "delayed: a passage had to be cleared";
            if (dc != null) {
                Daily.event(dc, "SHIPMENT_DELAYED", "s:" + s.id, "the caravan from " + (o == null ? "afar" : o.name) + " is running late", 12000);
            }
        } else if (r < 0.85) {
            double strength = 1 + s.guards.size() * 2;
            double threat = k.danger * 4 * mgr.rnd.nextDouble();
            SettlementRecord orec = SettlementState.get(mgr.world).get(s.origin);
            if (threat > strength) {
                double lossShare = 0.1 + mgr.rnd.nextDouble() * 0.3;
                s.cargo.replaceAll((g, v) -> Math.floor(v * (1 - lossShare)));
                s.condition = Math.max(0.3, s.condition - 0.3);
                hurt(orec, s.leader, 0.3);
                s.incident = "attacked by wildlife; some cargo lost";
                k.reliability = Math.max(0, k.reliability - 4);
                if (dc != null) {
                    Daily.event(dc, "CARAVAN_ATTACKED", "s:" + s.id, "wild creatures attacked the caravan from " + (o == null ? "afar" : o.name), 24000);
                }
                if (s.units() < 1) {
                    lose(mgr, st, s, now, "the whole cargo was lost");
                }
            } else {
                s.incident = "fought off wildlife";
                for (int gi : s.guards) {
                    remember(orec, gi, Memory.Type.DANGER, "caravan", now);
                }
            }
        } else {
            // the passage is blocked: the caravan turns back, the cargo returns home and the buyer is refunded
            k.blockedUntil = now + 24000L * 2;
            s.incident = "the route was blocked";
            if (dc != null) {
                Daily.event(dc, "ROUTE_BLOCKED", "", "the way to " + (o == null ? "a neighbour" : o.name) + " is blocked for now", 24000L * 2);
            }
            turnBack(mgr, st, s, now);
        }
    }

    static void hurt(@Nullable SettlementRecord rec, int idx, double amount) {
        if (rec != null && idx >= 0 && idx < rec.inhabitants.size() && rec.inhabitants.get(idx).mind != null) {
            Mind m = rec.inhabitants.get(idx).mind;
            m.health = (float) Math.max(0.3, m.health - amount);
        }
    }

    static void remember(@Nullable SettlementRecord rec, int idx, Memory.Type t, String detail, long now) {
        if (rec != null && idx >= 0 && idx < rec.inhabitants.size() && rec.inhabitants.get(idx).mind != null) {
            rec.inhabitants.get(idx).mind.memories.remember(t, "", detail, now, UnderdeepConfig.get().inhabitantMemoryLimit, 1);
        }
    }

    /** Delivery: cargo enters the destination ledger and escrow is released to the seller (and the carrier) — exactly once. */
    static void deliver(EconomyManager mgr, EconomyState st, Shipment s, long now) {
        if (s.status != Shipment.Status.TRAVELLING) {
            return;
        }
        s.status = Shipment.Status.ARRIVED;
        s.arrived = now;
        Economy to = st.economies.get(s.dest), from = st.economies.get(s.origin);
        if (to == null || from == null) {
            lose(mgr, st, s, now, "destination gone");
            return;
        }
        double delivered = 0;
        for (var en : s.cargo.entrySet()) {
            // storage capacity is soft for deliveries: nothing that arrives is ever deleted
            Economy.Stock st0 = to.stock(en.getKey());
            st0.qty += en.getValue();
            st0.imported += en.getValue();
            delivered += en.getValue();
        }
        double share = s.units() <= 0 ? 0 : Math.min(1, delivered / s.units());
        // pay the seller for what arrived, the carrier for the journey, refund the rest
        double sellerPart = s.price * share;
        Tx.pay(s, from.treasury, Math.min(sellerPart, s.escrow), now, "trade settle");
        from.tradeIn += sellerPart;
        SettlementRecord orec = SettlementState.get(mgr.world).get(s.origin);
        Economy.Household carrier = orec != null && s.leader >= 0 && s.leader < orec.inhabitants.size()
                ? from.households.get(orec.inhabitants.get(s.leader).home) : null;
        double feePart = Math.min(s.fee, s.escrow);
        Tx.pay(s, carrier != null ? carrier : from.treasury, feePart, now, "carriage");
        from.tradeIn += feePart;
        double refund = s.escrow;
        if (refund > 1e-9) {
            Tx.pay(s, to.treasury, refund, now, "trade refund");
            to.tradeIn += refund;
        }
        s.cargo.clear();
        EconomyState.Connection k = st.connection(s.origin, s.dest);
        if (k != null) {
            k.reliability = Math.min(100, k.reliability + 3);
            k.trust = Math.min(100, k.trust + 1.5);
            maybeAgreement(st, k, s, now);
        }
        EconomyState.Relation rel = st.relation(from.civ, to.civ);
        rel.trust = Math.min(100, rel.trust + 0.6);
        rel.dependence = Math.min(100, rel.dependence + 0.8);
        rel.familiarity = Math.min(100, rel.familiarity + 0.5);
        if (s.aid) {
            rel.cooperation = Math.min(100, rel.cooperation + 4);
        }
        st.delivered++;
        // news travels with the traders
        SettlementRecord drec = SettlementState.get(mgr.world).get(s.dest);
        shareNews(orec, s, drec, now, mgr);
        EconomySim.Ctx dc = mgr.ctx(to);
        if (dc != null) {
            Daily.event(dc, s.aid ? "EMERGENCY_AID" : "SHIPMENT_ARRIVED", "s:" + s.id, (s.aid ? "help arrived from " : "a caravan arrived from ")
                    + from.name + (s.method == Shipment.Method.BOAT ? " by boat" : ""), 24000);
        }
        Participation.escortsDone(mgr, st, s, now);
        s.status = Shipment.Status.RETURNING;
        s.progress = 0;
    }

    /** Traders tell people at the destination what they know (bounded); this is how news crosses regions. */
    static void shareNews(@Nullable SettlementRecord orec, Shipment s, @Nullable SettlementRecord drec, long now, EconomyManager mgr) {
        if (orec == null || drec == null || s.leader < 0 || s.leader >= orec.inhabitants.size()) {
            return;
        }
        Mind lm = orec.inhabitants.get(s.leader).mind;
        if (lm == null || drec.inhabitants.isEmpty()) {
            return;
        }
        int told = 0;
        for (Mind.Info i : lm.knowledge) {
            if (told >= 3 || now - i.time > 24000L * 4) {
                continue;
            }
            SettlementRecord.InhabitantRecord listener = drec.inhabitants.get(mgr.rnd.nextInt(drec.inhabitants.size()));
            if (listener.present() && listener.mind != null) {
                listener.mind.learn(i.type, i.subject, i.detail, now, Mind.Source.TOLD, Math.max(10, i.confidence - 25),
                        UnderdeepConfig.get().inhabitantKnowledgeLimit);
                told++;
            }
        }
        lm.memories.remember(Memory.Type.NEW_PLACE, "s:" + s.dest, drec.name, now, UnderdeepConfig.get().inhabitantMemoryLimit, 0.6);
    }

    /** The cargo goes back to the origin (exactly once) and the buyer is refunded in full. */
    static void turnBack(EconomyManager mgr, EconomyState st, Shipment s, long now) {
        if (s.status != Shipment.Status.TRAVELLING) {
            return;
        }
        Economy from = st.economies.get(s.origin), to = st.economies.get(s.dest);
        if (from != null) {
            for (var en : s.cargo.entrySet()) {
                from.put(en.getKey(), en.getValue());
                from.stock(en.getKey()).exported -= en.getValue();
            }
        }
        if (to != null) {
            double r = s.escrow;
            Tx.pay(s, to.treasury, r, now, "trade refund");
            to.tradeIn += r;
        }
        s.cargo.clear();
        s.status = Shipment.Status.RETURNING;
        s.progress = Math.max(0, s.length - s.progress);
    }

    /** Cargo destroyed (counted as lost goods), buyer refunded. */
    static void lose(EconomyManager mgr, EconomyState st, Shipment s, long now, String why) {
        if (s.status != Shipment.Status.TRAVELLING && s.status != Shipment.Status.ARRIVED) {
            return;
        }
        Economy to = st.economies.get(s.dest);
        if (to != null && s.escrow > 0) {
            double r = s.escrow;
            Tx.pay(s, to.treasury, r, now, "trade refund");
            to.tradeIn += r;
        }
        s.cargo.clear();
        s.incident = why;
        s.status = Shipment.Status.LOST;
        st.lost++;
        release(mgr, s, now);
        EconomyState.Connection k = st.connection(s.origin, s.dest);
        if (k != null) {
            k.reliability = Math.max(0, k.reliability - 10);
        }
    }

    static void home(EconomyManager mgr, EconomyState st, Shipment s, long now) {
        s.status = Shipment.Status.DONE;
        s.lastUpdate = now;
        release(mgr, s, now);
    }

    /** Caravan members are home again: their journey ends (the record resumes ordinary life). */
    static void release(EconomyManager mgr, Shipment s, long now) {
        SettlementRecord rec = SettlementState.get(mgr.world).get(s.origin);
        if (rec == null) {
            return;
        }
        List<Integer> crew = new ArrayList<>(s.guards);
        crew.add(s.leader);
        for (int i : crew) {
            if (i >= 0 && i < rec.inhabitants.size() && rec.inhabitants.get(i).mind != null && rec.inhabitants.get(i).mind.journey == s.id) {
                Mind m = rec.inhabitants.get(i).mind;
                m.journey = 0;
                m.lastUpdate = now;
                m.activity = -1;
            }
        }
    }

    // ================================================================== diplomacy

    /** Once per world day: relations drift back toward history, competition raises tension, disputes and reconciliations. */
    public static void diplomacy(EconomyManager mgr, EconomyState st, long now) {
        Civilization[] cv = Civilization.values();
        Map<String, Set<Civilization>> exporters = new HashMap<>();
        for (Shipment s : st.shipments.values()) {
            Economy o = st.economies.get(s.origin);
            if (o != null && now - s.created < 24000L * 3) {
                for (String g : s.cargo.keySet()) {
                    exporters.computeIfAbsent(Goods.get(g) == null ? g : Goods.get(g).category().id(), x -> new HashSet<>()).add(o.civ);
                }
            }
        }
        for (int a = 0; a < cv.length; a++) {
            for (int b = a + 1; b < cv.length; b++) {
                EconomyState.Relation r = st.relations[a][b];
                double competition = 0;
                for (Set<Civilization> set : exporters.values()) {
                    competition += set.contains(cv[a]) && set.contains(cv[b]) ? 1 : 0;
                }
                r.competition = r.competition * 0.9 + competition * 3;
                r.tension = Math.max(0, Math.min(100, r.tension * 0.97 + r.competition * 0.05 + (r.reliability < 30 ? 1 : 0)
                        - r.cooperation * 0.01 + (mgr.rnd.nextDouble() - 0.5) * UnderdeepConfig.get().eventFrequency));
                r.trust += (r.history * 0.6 - r.trust) * 0.02;
                r.reliability += (55 - r.reliability) * 0.03;
                if (r.tension > 60 && r.restrictedUntil < now && r.familiarity > 20) {
                    r.restrictedUntil = now + 24000L * 3;
                    st.note(now, cv[a].id() + " and " + cv[b].id() + " restrict trade in tools, metals and gems after a dispute");
                    announce(mgr, st, cv[a], cv[b], "TRADE_RESTRICTION", "traders say " + civName(cv[b]) + " and our people are quarrelling over trade", now);
                } else if (r.restrictedUntil > 0 && r.restrictedUntil < now && r.tension < 45) {
                    r.restrictedUntil = 0;
                    r.trust = Math.min(100, r.trust + 5);
                    st.note(now, cv[a].id() + " and " + cv[b].id() + " settle their trade dispute");
                    announce(mgr, st, cv[a], cv[b], "RECONCILIATION", "our people and the " + civName(cv[b]) + " have settled their dispute", now);
                }
            }
        }
        st.markDirty();
    }

    static String civName(Civilization c) {
        return c.id().replace('_', ' ');
    }

    static void announce(EconomyManager mgr, EconomyState st, Civilization a, Civilization b, String type, String sentence, long now) {
        for (Economy e : st.economies.values()) {
            if (e.civ == a || e.civ == b) {
                EconomySim.Ctx c = mgr.ctx(e);
                if (c != null) {
                    Daily.event(c, type, b.id(), sentence.replace(civName(b), civName(e.civ == a ? b : a)), 24000L * 3);
                }
            }
        }
    }
}
