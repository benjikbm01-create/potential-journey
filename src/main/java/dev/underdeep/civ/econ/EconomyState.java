package dev.underdeep.civ.econ;

import dev.underdeep.civ.Civilization;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * All persistent Stage 6 state of a world ({@code data/underdeep_economy.dat}): settlement economies, the trade
 * network, shipments (with cargo and escrowed payment), civilization relations, property, player businesses,
 * contracts and players' remembered price observations. One file, so a shipment's cargo and the ledgers it
 * left/enters are always saved together.
 */
public final class EconomyState extends PersistentState {
    public static final String ID = "underdeep_economy";
    public static final int SCHEMA = 1;
    public static final PersistentState.Type<EconomyState> TYPE = new PersistentState.Type<>(EconomyState::new, EconomyState::fromNbt,
            net.minecraft.datafixer.DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    public final Map<Long, Economy> economies = new LinkedHashMap<>();
    public final List<Connection> connections = new ArrayList<>();
    public final Map<Long, Shipment> shipments = new LinkedHashMap<>();
    public final Relation[][] relations = new Relation[Civilization.values().length][Civilization.values().length];
    public final Map<Long, Property> properties = new LinkedHashMap<>();
    public final Map<Long, Business> businesses = new LinkedHashMap<>();
    public final Map<Long, Contract> contracts = new LinkedHashMap<>();
    public final Map<UUID, Map<Long, Observation>> observations = new HashMap<>();
    public final Deque<String> log = new ArrayDeque<>();
    public long nextId = 1;
    public long delivered, lost, networkVersion;

    public static EconomyState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    public EconomyState() {
        int n = Civilization.values().length;
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                relations[a][b] = a <= b ? Relation.baseline(Civilization.values()[a], Civilization.values()[b]) : relations[b][a];
            }
        }
    }

    public long newId() {
        markDirty();
        return nextId++;
    }

    public Relation relation(Civilization a, Civilization b) {
        return relations[a.ordinal()][b.ordinal()];
    }

    public void note(long now, String line) {
        log.addFirst("day " + now / 24000 + ": " + line);
        while (log.size() > 200) {
            log.removeLast();
        }
    }

    @Nullable
    public Connection connection(long a, long b) {
        for (Connection c : connections) {
            if (c.a == a && c.b == b || c.a == b && c.b == a) {
                return c;
            }
        }
        return null;
    }

    public List<Connection> connectionsOf(long id) {
        List<Connection> out = new ArrayList<>();
        for (Connection c : connections) {
            if (c.a == id || c.b == id) {
                out.add(c);
            }
        }
        return out;
    }

    // ================================================================== records

    /** A trade connection between two settlements (sparse regional network). */
    public static final class Connection {
        public long a, b;
        public double distance;
        public boolean water, crossLayer;
        /** 0 (safe) – 1 (very dangerous): region danger and predators along the route. */
        public double danger;
        /** Local relationship between the two settlements (−100..100), reliability of deliveries (0–100). */
        public double trust = 10, reliability = 60;
        public long blockedUntil;
        public int shipments;
        public final List<double[]> route = new ArrayList<>();
        /** Recurring trade agreements on this connection. */
        public final List<Agreement> agreements = new ArrayList<>();

        public long other(long id) {
            return id == a ? b : a;
        }

        NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            n.putLong("a", a);
            n.putLong("b", b);
            n.putDouble("dist", distance);
            n.putBoolean("water", water);
            n.putBoolean("cross", crossLayer);
            n.putDouble("danger", danger);
            n.putDouble("trust", trust);
            n.putDouble("rel", reliability);
            n.putLong("blocked", blockedUntil);
            n.putInt("count", shipments);
            n.put("route", route(route));
            NbtList ag = new NbtList();
            for (Agreement a : agreements) {
                ag.add(a.toNbt());
            }
            n.put("agreements", ag);
            return n;
        }

        static Connection fromNbt(NbtCompound n) {
            Connection c = new Connection();
            c.a = n.getLong("a");
            c.b = n.getLong("b");
            c.distance = n.getDouble("dist");
            c.water = n.getBoolean("water");
            c.crossLayer = n.getBoolean("cross");
            c.danger = n.getDouble("danger");
            c.trust = n.getDouble("trust");
            c.reliability = n.getDouble("rel");
            c.blockedUntil = n.getLong("blocked");
            c.shipments = n.getInt("count");
            readRoute(n.getList("route", NbtElement.LIST_TYPE), c.route);
            for (NbtElement e : n.getList("agreements", NbtElement.COMPOUND_TYPE)) {
                c.agreements.add(Agreement.fromNbt((NbtCompound) e));
            }
            return c;
        }
    }

    /** A recurring delivery: {@code supplier} sends {@code units} of a good to {@code buyer} every {@code interval} days. */
    public static final class Agreement {
        public long supplier, buyer;
        public String good = "";
        public int units, interval = 2, remaining = 4, failures;
        public long nextDay;
        public boolean aid;

        NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            n.putLong("s", supplier);
            n.putLong("b", buyer);
            n.putString("g", good);
            n.putInt("u", units);
            n.putInt("i", interval);
            n.putInt("r", remaining);
            n.putInt("f", failures);
            n.putLong("next", nextDay);
            n.putBoolean("aid", aid);
            return n;
        }

        static Agreement fromNbt(NbtCompound n) {
            Agreement a = new Agreement();
            a.supplier = n.getLong("s");
            a.buyer = n.getLong("b");
            a.good = n.getString("g");
            a.units = n.getInt("u");
            a.interval = Math.max(1, n.getInt("i"));
            a.remaining = n.getInt("r");
            a.failures = n.getInt("f");
            a.nextDay = n.getLong("next");
            a.aid = n.getBoolean("aid");
            return a;
        }
    }

    static NbtList route(List<double[]> r) {
        NbtList l = new NbtList();
        for (double[] p : r) {
            NbtList q = new NbtList();
            for (double d : p) {
                q.add(net.minecraft.nbt.NbtDouble.of(d));
            }
            l.add(q);
        }
        return l;
    }

    static void readRoute(NbtList l, List<double[]> out) {
        for (int i = 0; i < l.size(); i++) {
            NbtList q = l.getList(i);
            out.add(new double[]{q.getDouble(0), q.getDouble(1), q.getDouble(2)});
        }
    }

    /** Relationship between two civilizations (all dimensions 0–100). */
    public static final class Relation {
        public double trust, familiarity, cooperation, tension, competition, reliability, dependence, history;
        public long restrictedUntil;

        static Relation baseline(Civilization a, Civilization b) {
            Relation r = new Relation();
            if (a == b) {
                r.trust = 70;
                r.familiarity = 90;
                r.cooperation = 60;
                r.reliability = 70;
                r.history = 60;
                return r;
            }
            // deterministic, mild historical affinities: neighbours in similar environments know each other better
            long h = (a.ordinal() * 31L + b.ordinal()) * 0x9E3779B97F4A7C15L;
            double u = ((h >>> 11) % 1000) / 1000.0;
            r.history = 30 + u * 40;
            r.trust = 25 + u * 25;
            r.familiarity = 15 + ((h >>> 23) % 30);
            r.cooperation = 20;
            r.tension = 10 + (1 - u) * 15;
            r.reliability = 50;
            return r;
        }

        /** −1..1 attitude used in trade terms. */
        public double attitude() {
            return Math.max(-1, Math.min(1, (trust * 0.5 + cooperation * 0.25 + reliability * 0.25 - tension * 0.8) / 60.0 - 0.2));
        }

        NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            n.putDouble("trust", trust);
            n.putDouble("fam", familiarity);
            n.putDouble("coop", cooperation);
            n.putDouble("tension", tension);
            n.putDouble("comp", competition);
            n.putDouble("rel", reliability);
            n.putDouble("dep", dependence);
            n.putDouble("hist", history);
            n.putLong("restricted", restrictedUntil);
            return n;
        }

        void read(NbtCompound n) {
            trust = n.getDouble("trust");
            familiarity = n.getDouble("fam");
            cooperation = n.getDouble("coop");
            tension = n.getDouble("tension");
            competition = n.getDouble("comp");
            reliability = n.getDouble("rel");
            dependence = n.getDouble("dep");
            history = n.getDouble("hist");
            restrictedUntil = n.getLong("restricted");
        }
    }

    /** A purchasable or rentable property: a settlement building without residents or keeper. */
    public static final class Property {
        public long id, settlement;
        public int building;
        public String type = "home";
        public int x0, y0, z0, x1, y1, z1;
        @Nullable public UUID owner;
        public final Set<UUID> members = new HashSet<>();
        public double price;
        /** 0 = owned outright; otherwise the rent is paid until this world time. */
        public long rentUntil;

        public boolean contains(int x, int y, int z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }

        public boolean allowed(UUID p) {
            return p.equals(owner) || members.contains(p);
        }

        NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            n.putLong("id", id);
            n.putLong("s", settlement);
            n.putInt("b", building);
            n.putString("type", type);
            n.putIntArray("box", new int[]{x0, y0, z0, x1, y1, z1});
            if (owner != null) {
                n.putUuid("owner", owner);
            }
            NbtList m = new NbtList();
            for (UUID u : members) {
                m.add(net.minecraft.nbt.NbtHelper.fromUuid(u));
            }
            n.put("members", m);
            n.putDouble("price", price);
            n.putLong("rent", rentUntil);
            return n;
        }

        static Property fromNbt(NbtCompound n) {
            Property p = new Property();
            p.id = n.getLong("id");
            p.settlement = n.getLong("s");
            p.building = n.getInt("b");
            p.type = n.getString("type");
            int[] b = n.getIntArray("box");
            if (b.length == 6) {
                p.x0 = b[0];
                p.y0 = b[1];
                p.z0 = b[2];
                p.x1 = b[3];
                p.y1 = b[4];
                p.z1 = b[5];
            }
            p.owner = n.containsUuid("owner") ? n.getUuid("owner") : null;
            for (NbtElement e : n.getList("members", NbtElement.INT_ARRAY_TYPE)) {
                p.members.add(net.minecraft.nbt.NbtHelper.toUuid(e));
            }
            p.price = n.getDouble("price");
            p.rentUntil = n.getLong("rent");
            return p;
        }
    }

    /** A player-owned shop: stock (the inventory itself), prices, till, optional hired shopkeeper. */
    public static final class Business implements Tx.Account {
        public long id, property, settlement;
        public UUID owner;
        public String name = "";
        public final List<ItemStack> stock = new ArrayList<>();
        public double till;
        public int priceMode = 1;
        public int employee = -1;
        public double wage = 1.5;
        public long lastPaid;
        public int unpaidDays, sales;
        public double revenue;
        public final Deque<String> history = new ArrayDeque<>();

        public double priceFactor() {
            return priceMode == 0 ? 0.9 : priceMode == 2 ? 1.15 : 1.0;
        }

        @Override
        public double balance() {
            return till;
        }

        @Override
        public void add(double v) {
            till += v;
        }

        @Override
        public String label() {
            return "business " + id;
        }

        NbtCompound toNbt(RegistryWrapper.WrapperLookup lookup) {
            NbtCompound n = new NbtCompound();
            n.putLong("id", id);
            n.putLong("prop", property);
            n.putLong("s", settlement);
            n.putUuid("owner", owner);
            n.putString("name", name);
            NbtList items = new NbtList();
            for (int i = 0; i < stock.size(); i++) {
                ItemStack s = stock.get(i);
                if (!s.isEmpty()) {
                    NbtCompound c = (NbtCompound) s.encode(lookup);
                    c.putInt("Slot", i);
                    items.add(c);
                }
            }
            n.put("stock", items);
            n.putDouble("till", till);
            n.putInt("mode", priceMode);
            n.putInt("employee", employee);
            n.putDouble("wage", wage);
            n.putLong("paid", lastPaid);
            n.putInt("unpaid", unpaidDays);
            n.putInt("sales", sales);
            n.putDouble("revenue", revenue);
            NbtList h = new NbtList();
            history.forEach(s -> h.add(net.minecraft.nbt.NbtString.of(s)));
            n.put("history", h);
            return n;
        }

        static Business fromNbt(NbtCompound n, RegistryWrapper.WrapperLookup lookup) {
            Business b = new Business();
            b.id = n.getLong("id");
            b.property = n.getLong("prop");
            b.settlement = n.getLong("s");
            b.owner = n.getUuid("owner");
            b.name = n.getString("name");
            while (b.stock.size() < 27) {
                b.stock.add(ItemStack.EMPTY);
            }
            for (NbtElement e : n.getList("stock", NbtElement.COMPOUND_TYPE)) {
                NbtCompound c = (NbtCompound) e;
                int slot = c.getInt("Slot");
                ItemStack.fromNbt(lookup, c).ifPresent(s -> {
                    if (slot >= 0 && slot < 27) {
                        b.stock.set(slot, s);
                    }
                });
            }
            b.till = n.getDouble("till");
            b.priceMode = n.getInt("mode");
            b.employee = n.getInt("employee");
            b.wage = n.getDouble("wage");
            b.lastPaid = n.getLong("paid");
            b.unpaidDays = n.getInt("unpaid");
            b.sales = n.getInt("sales");
            b.revenue = n.getDouble("revenue");
            for (NbtElement s : n.getList("history", NbtElement.STRING_TYPE)) {
                b.history.addLast(s.asString());
            }
            return b;
        }
    }

    /** An economic contract offered by a settlement; the reward is escrowed from its treasury when posted. */
    public static final class Contract implements Tx.Account {
        public enum Type { SUPPLY, COURIER, ESCORT, BUILD }

        public enum Status { OPEN, TAKEN, DONE, EXPIRED }

        public long id, settlement;
        public Type type = Type.SUPPLY;
        public Status status = Status.OPEN;
        public String good = "";
        public int qty;
        public double escrow;
        public double reward;
        public double deposit;
        public long posted, expires;
        @Nullable public UUID taker;
        public long target;
        public int project = -1;
        public int issuer = -1;
        public String reason = "";

        @Override
        public double balance() {
            return escrow;
        }

        @Override
        public void add(double v) {
            escrow += v;
        }

        @Override
        public String label() {
            return "contract " + id;
        }

        NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            n.putLong("id", id);
            n.putLong("s", settlement);
            n.putString("type", type.name());
            n.putString("status", status.name());
            n.putString("good", good);
            n.putInt("qty", qty);
            n.putDouble("escrow", escrow);
            n.putDouble("reward", reward);
            n.putDouble("deposit", deposit);
            n.putLong("posted", posted);
            n.putLong("expires", expires);
            if (taker != null) {
                n.putUuid("taker", taker);
            }
            n.putLong("target", target);
            n.putInt("project", project);
            n.putInt("issuer", issuer);
            n.putString("reason", reason);
            return n;
        }

        static Contract fromNbt(NbtCompound n) {
            Contract c = new Contract();
            c.id = n.getLong("id");
            c.settlement = n.getLong("s");
            c.type = Type.valueOf(n.getString("type"));
            c.status = Status.valueOf(n.getString("status"));
            c.good = n.getString("good");
            c.qty = n.getInt("qty");
            c.escrow = n.getDouble("escrow");
            c.reward = n.getDouble("reward");
            c.deposit = n.getDouble("deposit");
            c.posted = n.getLong("posted");
            c.expires = n.getLong("expires");
            c.taker = n.containsUuid("taker") ? n.getUuid("taker") : null;
            c.target = n.getLong("target");
            c.project = n.getInt("project");
            c.issuer = n.getInt("issuer");
            c.reason = n.getString("reason");
            return c;
        }
    }

    /** Prices a player saw at a settlement (historical, never live). */
    public static final class Observation {
        public long time;
        public final Map<String, Double> prices = new TreeMap<>();
    }

    // ================================================================== NBT

    private static EconomyState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        EconomyState s = new EconomyState();
        s.nextId = Math.max(1, nbt.getLong("next"));
        s.delivered = nbt.getLong("delivered");
        s.lost = nbt.getLong("lost");
        NbtCompound ec = nbt.getCompound("economies");
        for (String k : ec.getKeys()) {
            Economy e = Economy.fromNbt(ec.getCompound(k), lookup);
            if (e.civ != null) {
                s.economies.put(e.id, e);
            }
        }
        for (NbtElement e : nbt.getList("connections", NbtElement.COMPOUND_TYPE)) {
            s.connections.add(Connection.fromNbt((NbtCompound) e));
        }
        for (NbtElement e : nbt.getList("shipments", NbtElement.COMPOUND_TYPE)) {
            Shipment sh = Shipment.fromNbt((NbtCompound) e);
            s.shipments.put(sh.id, sh);
        }
        NbtCompound rel = nbt.getCompound("relations");
        for (String k : rel.getKeys()) {
            String[] ab = k.split("\\|");
            Civilization a = Civilization.byId(ab[0]), b = ab.length > 1 ? Civilization.byId(ab[1]) : null;
            if (a != null && b != null) {
                s.relations[a.ordinal()][b.ordinal()].read(rel.getCompound(k));
            }
        }
        for (NbtElement e : nbt.getList("properties", NbtElement.COMPOUND_TYPE)) {
            Property p = Property.fromNbt((NbtCompound) e);
            s.properties.put(p.id, p);
        }
        for (NbtElement e : nbt.getList("businesses", NbtElement.COMPOUND_TYPE)) {
            Business b = Business.fromNbt((NbtCompound) e, lookup);
            s.businesses.put(b.id, b);
        }
        for (NbtElement e : nbt.getList("contracts", NbtElement.COMPOUND_TYPE)) {
            Contract c = Contract.fromNbt((NbtCompound) e);
            s.contracts.put(c.id, c);
        }
        NbtCompound ob = nbt.getCompound("observations");
        for (String k : ob.getKeys()) {
            UUID u = UUID.fromString(k);
            NbtCompound per = ob.getCompound(k);
            Map<Long, Observation> m = new HashMap<>();
            for (String sk : per.getKeys()) {
                NbtCompound c = per.getCompound(sk);
                Observation o = new Observation();
                o.time = c.getLong("t");
                NbtCompound pr = c.getCompound("p");
                for (String g : pr.getKeys()) {
                    o.prices.put(g, pr.getDouble(g));
                }
                m.put(Long.parseLong(sk), o);
            }
            s.observations.put(u, m);
        }
        for (NbtElement e : nbt.getList("log", NbtElement.STRING_TYPE)) {
            s.log.addLast(e.asString());
        }
        Tx.load(nbt.getList("tx", NbtElement.COMPOUND_TYPE));
        return s;
    }

    /** Test access: a copy loaded from saved NBT (as after a restart). */
    public static EconomyState loadForTest(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        return fromNbt(nbt, lookup);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putInt("schema", SCHEMA);
        nbt.putLong("next", nextId);
        nbt.putLong("delivered", delivered);
        nbt.putLong("lost", lost);
        NbtCompound ec = new NbtCompound();
        economies.forEach((k, v) -> ec.put(Long.toString(k), v.toNbt(lookup)));
        nbt.put("economies", ec);
        NbtList cl = new NbtList();
        connections.forEach(c -> cl.add(c.toNbt()));
        nbt.put("connections", cl);
        NbtList sl = new NbtList();
        shipments.values().forEach(s -> sl.add(s.toNbt()));
        nbt.put("shipments", sl);
        NbtCompound rel = new NbtCompound();
        Civilization[] cv = Civilization.values();
        for (int a = 0; a < cv.length; a++) {
            for (int b = a; b < cv.length; b++) {
                rel.put(cv[a].id() + "|" + cv[b].id(), relations[a][b].toNbt());
            }
        }
        nbt.put("relations", rel);
        NbtList pl = new NbtList();
        properties.values().forEach(p -> pl.add(p.toNbt()));
        nbt.put("properties", pl);
        NbtList bl = new NbtList();
        businesses.values().forEach(b -> bl.add(b.toNbt(lookup)));
        nbt.put("businesses", bl);
        NbtList ctl = new NbtList();
        contracts.values().forEach(c -> ctl.add(c.toNbt()));
        nbt.put("contracts", ctl);
        NbtCompound ob = new NbtCompound();
        observations.forEach((u, m) -> {
            NbtCompound per = new NbtCompound();
            m.forEach((sid, o) -> {
                NbtCompound c = new NbtCompound();
                c.putLong("t", o.time);
                NbtCompound pr = new NbtCompound();
                o.prices.forEach(pr::putDouble);
                c.put("p", pr);
                per.put(Long.toString(sid), c);
            });
            ob.put(u.toString(), per);
        });
        nbt.put("observations", ob);
        NbtList lg = new NbtList();
        log.forEach(l -> lg.add(net.minecraft.nbt.NbtString.of(l)));
        nbt.put("log", lg);
        nbt.put("tx", Tx.save());
        return nbt;
    }

    /** Total money in the simulated world (settlements, escrow, shipments, businesses) for audits. */
    public double totalMoney() {
        double t = 0;
        for (Economy e : economies.values()) {
            t += e.money();
        }
        for (Shipment s : shipments.values()) {
            t += s.escrow;
        }
        for (Contract c : contracts.values()) {
            t += c.escrow;
        }
        for (Business b : businesses.values()) {
            t += b.till;
        }
        for (Economy e : economies.values()) {
            for (Project p : e.projects) {
                t += p.budget;
            }
        }
        return t;
    }

    /** World money audit: [actual, expected from explicit sources and sinks]. Trade flows cancel between settlements. */
    public double[] audit() {
        double expected = 0;
        for (Economy e : economies.values()) {
            expected += e.initialMoney + e.issued - e.sunk + e.playerIn - e.playerOut;
        }
        return new double[]{totalMoney(), expected};
    }
}
