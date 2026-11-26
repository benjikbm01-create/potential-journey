package dev.underdeep.civ.econ;

import dev.underdeep.civ.Civilization;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The persistent economy of one settlement (Stage 6): one authoritative ledger of goods, the community treasury,
 * households, merchants (shelves and tills), workplaces, sustainability pools, prosperity, leadership, projects,
 * buildings added by construction, events and money-flow counters. Every tier (embodied or abstract) reads and
 * writes this one copy; physical interactions (player trades, deliveries, construction) are transactions on it.
 */
public final class Economy {
    public static final int SCHEMA = 1;

    // ------------------------------------------------------------------ nested records

    /** Ledger entry for one good. Quantities in item units; rates are smoothed per in-game day. */
    public static final class Stock {
        public double qty, reserved;
        public double produced, consumed, imported, exported;
        public double desired = 4;
        public double price;

        public double free() {
            return Math.max(0, qty - reserved);
        }

        NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            n.putDouble("q", qty);
            n.putDouble("r", reserved);
            n.putDouble("p", produced);
            n.putDouble("c", consumed);
            n.putDouble("i", imported);
            n.putDouble("e", exported);
            n.putDouble("d", desired);
            n.putDouble("$", price);
            return n;
        }

        static Stock fromNbt(NbtCompound n) {
            Stock s = new Stock();
            s.qty = n.getDouble("q");
            s.reserved = n.getDouble("r");
            s.produced = n.getDouble("p");
            s.consumed = n.getDouble("c");
            s.imported = n.getDouble("i");
            s.exported = n.getDouble("e");
            s.desired = n.getDouble("d");
            s.price = n.getDouble("$");
            return s;
        }
    }

    /** A home and the people living in it. */
    public static final class Household implements Tx.Account {
        public int home;
        public double purse;
        /** Smoothed share of the household's food need that was met (0–1). */
        public double foodSecurity = 1;
        public double support;
        /** Belongings left by members who died or moved without them (never deleted). */
        public final List<ItemStack> storage = new ArrayList<>();

        @Override
        public double balance() {
            return purse;
        }

        @Override
        public void add(double v) {
            purse += v;
        }

        @Override
        public String label() {
            return "household " + home;
        }
    }

    /** A seller: a shelf of goods owned by them and a till. */
    public static final class Merchant implements Tx.Account {
        public int index;
        public final Map<String, Integer> shelf = new TreeMap<>();
        public double till;
        public long lastRestock;

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
            return "merchant " + index;
        }
    }

    public enum WorkStatus { OPEN, IDLE, NO_WORKERS }

    /** A workplace: a building with job positions. */
    public static final class Workplace {
        public int building;
        public String kind = "";
        public int positions;
        public final List<Integer> workers = new ArrayList<>();
        public WorkStatus status = WorkStatus.OPEN;
        public double wage = 0.25;
        public double output;
        public double idleHours;
        public String recipe = "";
        public String missing = "";
    }

    /** A renewable resource pool shared by the settlement's gatherers (sustainability). */
    public static final class Pool {
        public double capacity, level, regen, pressure;

        public double yieldFactor() {
            return capacity <= 0 ? 0 : Math.max(0.15, Math.min(1, level / capacity * 1.25));
        }
    }

    /** A building added by construction (identity: {@code EXTRA_BASE + id} as a building index). */
    public static final class Extra {
        public int id;
        public String kind = "";
        public int variant;
        public BlockRotation rotation = BlockRotation.NONE;
        public BlockPos origin = BlockPos.ORIGIN, entrance = BlockPos.ORIGIN;
        public int x0, z0, x1, z1;
        public final List<BlockPos> beds = new ArrayList<>();
        public final List<BlockPos> jobs = new ArrayList<>();
        public final List<String> jobProfessions = new ArrayList<>();
        public String profession = "";
    }

    public static final int EXTRA_BASE = 1000;

    public record Event(String type, long time, String subject, String detail, long until) {
    }

    // ------------------------------------------------------------------ state

    public long id;
    public Civilization civ;
    public String name = "";
    public long created, lastStep = -1;
    public long day = -1;
    public final Map<String, Stock> stock = new TreeMap<>();
    public final Tx.Account treasury = new Tx.Account() {
        @Override
        public double balance() {
            return treasuryBalance;
        }

        @Override
        public void add(double v) {
            treasuryBalance += v;
        }

        @Override
        public String label() {
            return "treasury";
        }
    };
    public double treasuryBalance;
    public final Map<Integer, Household> households = new TreeMap<>();
    public final Map<Integer, Merchant> merchants = new TreeMap<>();
    public final Map<Integer, Workplace> workplaces = new TreeMap<>();
    public final Map<String, Pool> pools = new TreeMap<>();
    public double storageCapacity = 400;
    /** Prosperity components (0–1) and score. */
    public final double[] prosperity = new double[Prosperity.COUNT];
    public double score = 0.5;
    public final List<Integer> leaders = new ArrayList<>();
    public String leadership = "";
    public final List<Project> projects = new ArrayList<>();
    public final List<Extra> extras = new ArrayList<>();
    public int nextExtra;
    public final Deque<String> decisions = new ArrayDeque<>();
    public final Deque<Event> events = new ArrayDeque<>();
    /** Money-flow counters since creation (audit): issued, sunk, player in/out, trade in/out, wages, support. */
    public double issued, sunk, playerIn, playerOut, tradeIn, tradeOut, wagesPaid, supportPaid;
    public double initialMoney;
    public final Map<String, Long> lastImport = new TreeMap<>(), lastExport = new TreeMap<>();
    public long gatheringUntil;
    public String gatheringReason = "";
    public double disruption;
    public int growth;

    // ------------------------------------------------------------------ ledger operations (validated)

    public Stock stock(String good) {
        return stock.computeIfAbsent(good, k -> {
            Stock s = new Stock();
            Goods.Good g = Goods.get(k);
            s.price = g == null ? 0.1 : g.value();
            return s;
        });
    }

    public double free(String good) {
        Stock s = stock.get(good);
        return s == null ? 0 : s.free();
    }

    /** Adds produced/received goods (bounded by storage capacity; overflow is reported back to the caller). */
    public double put(String good, double qty) {
        if (qty <= 0) {
            return 0;
        }
        double room = Math.max(0, storageCapacity * 1.5 - totalUnits());
        double in = Math.min(qty, Math.max(room, 0));
        stock(good).qty += in;
        return in;
    }

    /** Removes up to {@code qty} free units; returns what was actually removed (never negative stock). */
    public double take(String good, double qty) {
        Stock s = stock.get(good);
        if (s == null || qty <= 0) {
            return 0;
        }
        double out = Math.min(qty, s.free());
        s.qty -= out;
        if (s.qty < 1e-9) {
            s.qty = 0;
        }
        return out;
    }

    public double totalUnits() {
        double t = 0;
        for (Stock s : stock.values()) {
            t += s.qty;
        }
        return t;
    }

    /** Free units of all goods in a category. */
    public double freeIn(Goods.Category c) {
        double t = 0;
        for (Goods.Good g : Goods.in(c)) {
            t += free(g.id());
        }
        return t;
    }

    /** Takes units from a category, cheapest first; returns units taken. */
    public double takeFrom(Goods.Category c, double qty, Map<String, Double> taken) {
        double need = qty;
        List<Goods.Good> gs = new ArrayList<>(Goods.in(c));
        gs.sort((a, b) -> Double.compare(a.value(), b.value()));
        for (Goods.Good g : gs) {
            if (need <= 1e-9) {
                break;
            }
            double t = take(g.id(), need);
            if (t > 0 && taken != null) {
                taken.merge(g.id(), t, Double::sum);
            }
            need -= t;
        }
        return qty - need;
    }

    public double money() {
        double m = treasuryBalance;
        for (Household h : households.values()) {
            m += h.purse;
        }
        for (Merchant mm : merchants.values()) {
            m += mm.till;
        }
        return m;
    }

    public void decide(long now, String line) {
        decisions.addFirst("day " + now / 24000 + ": " + line);
        while (decisions.size() > 16) {
            decisions.removeLast();
        }
    }

    public void event(String type, long now, String subject, String detail, long duration) {
        events.addFirst(new Event(type, now, subject, detail, now + duration));
        while (events.size() > 20) {
            events.removeLast();
        }
    }

    public boolean recent(String type, long now) {
        for (Event e : events) {
            if (e.type().equals(type) && e.until() > now) {
                return true;
            }
        }
        return false;
    }

    public double prosperity(Prosperity p) {
        return prosperity[p.ordinal()];
    }

    // ------------------------------------------------------------------ NBT

    public NbtCompound toNbt(RegistryWrapper.WrapperLookup lookup) {
        NbtCompound n = new NbtCompound();
        n.putInt("schema", SCHEMA);
        n.putLong("id", id);
        n.putString("civ", civ.id());
        n.putString("name", name);
        n.putLong("created", created);
        n.putLong("step", lastStep);
        n.putLong("day", day);
        NbtCompound st = new NbtCompound();
        stock.forEach((k, v) -> st.put(k, v.toNbt()));
        n.put("stock", st);
        n.putDouble("treasury", treasuryBalance);
        NbtList hl = new NbtList();
        for (Household h : households.values()) {
            NbtCompound c = new NbtCompound();
            c.putInt("home", h.home);
            c.putDouble("purse", h.purse);
            c.putDouble("food", h.foodSecurity);
            c.putDouble("support", h.support);
            NbtList items = new NbtList();
            for (ItemStack s : h.storage) {
                if (!s.isEmpty()) {
                    items.add(s.encode(lookup));
                }
            }
            c.put("storage", items);
            hl.add(c);
        }
        n.put("households", hl);
        NbtList ml = new NbtList();
        for (Merchant m : merchants.values()) {
            NbtCompound c = new NbtCompound();
            c.putInt("i", m.index);
            c.putDouble("till", m.till);
            c.putLong("restock", m.lastRestock);
            NbtCompound sh = new NbtCompound();
            m.shelf.forEach(sh::putInt);
            c.put("shelf", sh);
            ml.add(c);
        }
        n.put("merchants", ml);
        NbtList wl = new NbtList();
        for (Workplace w : workplaces.values()) {
            NbtCompound c = new NbtCompound();
            c.putInt("b", w.building);
            c.putString("kind", w.kind);
            c.putInt("pos", w.positions);
            c.putIntArray("workers", w.workers.stream().mapToInt(Integer::intValue).toArray());
            c.putInt("status", w.status.ordinal());
            c.putDouble("wage", w.wage);
            c.putDouble("out", w.output);
            c.putDouble("idle", w.idleHours);
            c.putString("recipe", w.recipe);
            c.putString("missing", w.missing);
            wl.add(c);
        }
        n.put("workplaces", wl);
        NbtCompound pl = new NbtCompound();
        pools.forEach((k, p) -> {
            NbtCompound c = new NbtCompound();
            c.putDouble("cap", p.capacity);
            c.putDouble("lvl", p.level);
            c.putDouble("regen", p.regen);
            c.putDouble("press", p.pressure);
            pl.put(k, c);
        });
        n.put("pools", pl);
        n.putDouble("capacity", storageCapacity);
        NbtList pr = new NbtList();
        for (double d : prosperity) {
            pr.add(net.minecraft.nbt.NbtDouble.of(d));
        }
        n.put("prosperity", pr);
        n.putDouble("score", score);
        n.putIntArray("leaders", leaders.stream().mapToInt(Integer::intValue).toArray());
        n.putString("leadership", leadership);
        NbtList pj = new NbtList();
        for (Project p : projects) {
            pj.add(p.toNbt());
        }
        n.put("projects", pj);
        NbtList ex = new NbtList();
        for (Extra e : extras) {
            NbtCompound c = new NbtCompound();
            c.putInt("id", e.id);
            c.putString("kind", e.kind);
            c.putInt("v", e.variant);
            c.putInt("rot", e.rotation.ordinal());
            c.putLong("origin", e.origin.asLong());
            c.putLong("door", e.entrance.asLong());
            c.putIntArray("box", new int[]{e.x0, e.z0, e.x1, e.z1});
            c.putLongArray("beds", e.beds.stream().mapToLong(BlockPos::asLong).toArray());
            c.putLongArray("jobs", e.jobs.stream().mapToLong(BlockPos::asLong).toArray());
            NbtList jp = new NbtList();
            e.jobProfessions.forEach(s -> jp.add(net.minecraft.nbt.NbtString.of(s)));
            c.put("jobProf", jp);
            c.putString("prof", e.profession);
            ex.add(c);
        }
        n.put("extras", ex);
        n.putInt("nextExtra", nextExtra);
        NbtList dl = new NbtList();
        decisions.forEach(s -> dl.add(net.minecraft.nbt.NbtString.of(s)));
        n.put("decisions", dl);
        NbtList el = new NbtList();
        for (Event e : events) {
            NbtCompound c = new NbtCompound();
            c.putString("t", e.type());
            c.putLong("at", e.time());
            c.putString("s", e.subject());
            c.putString("d", e.detail());
            c.putLong("u", e.until());
            el.add(c);
        }
        n.put("events", el);
        NbtList fl = new NbtList();
        for (double d : new double[]{issued, sunk, playerIn, playerOut, tradeIn, tradeOut, wagesPaid, supportPaid, initialMoney, disruption}) {
            fl.add(net.minecraft.nbt.NbtDouble.of(d));
        }
        n.put("flows", fl);
        NbtCompound li = new NbtCompound();
        lastImport.forEach(li::putLong);
        n.put("lastImport", li);
        NbtCompound le = new NbtCompound();
        lastExport.forEach(le::putLong);
        n.put("lastExport", le);
        n.putLong("gathering", gatheringUntil);
        n.putString("gatheringWhy", gatheringReason);
        n.putInt("growth", growth);
        return n;
    }

    public static Economy fromNbt(NbtCompound n, RegistryWrapper.WrapperLookup lookup) {
        Economy e = new Economy();
        e.id = n.getLong("id");
        e.civ = Civilization.byId(n.getString("civ"));
        e.name = n.getString("name");
        e.created = n.getLong("created");
        e.lastStep = n.getLong("step");
        e.day = n.getLong("day");
        NbtCompound st = n.getCompound("stock");
        for (String k : st.getKeys()) {
            e.stock.put(k, Stock.fromNbt(st.getCompound(k)));
        }
        e.treasuryBalance = n.getDouble("treasury");
        for (NbtElement x : n.getList("households", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) x;
            Household h = new Household();
            h.home = c.getInt("home");
            h.purse = c.getDouble("purse");
            h.foodSecurity = c.getDouble("food");
            h.support = c.getDouble("support");
            for (NbtElement s : c.getList("storage", NbtElement.COMPOUND_TYPE)) {
                ItemStack.fromNbt(lookup, s).ifPresent(h.storage::add);
            }
            e.households.put(h.home, h);
        }
        for (NbtElement x : n.getList("merchants", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) x;
            Merchant m = new Merchant();
            m.index = c.getInt("i");
            m.till = c.getDouble("till");
            m.lastRestock = c.getLong("restock");
            NbtCompound sh = c.getCompound("shelf");
            for (String k : sh.getKeys()) {
                m.shelf.put(k, sh.getInt(k));
            }
            e.merchants.put(m.index, m);
        }
        for (NbtElement x : n.getList("workplaces", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) x;
            Workplace w = new Workplace();
            w.building = c.getInt("b");
            w.kind = c.getString("kind");
            w.positions = c.getInt("pos");
            for (int i : c.getIntArray("workers")) {
                w.workers.add(i);
            }
            w.status = WorkStatus.values()[Math.min(c.getInt("status"), WorkStatus.values().length - 1)];
            w.wage = c.getDouble("wage");
            w.output = c.getDouble("out");
            w.idleHours = c.getDouble("idle");
            w.recipe = c.getString("recipe");
            w.missing = c.getString("missing");
            e.workplaces.put(w.building, w);
        }
        NbtCompound pl = n.getCompound("pools");
        for (String k : pl.getKeys()) {
            NbtCompound c = pl.getCompound(k);
            Pool p = new Pool();
            p.capacity = c.getDouble("cap");
            p.level = c.getDouble("lvl");
            p.regen = c.getDouble("regen");
            p.pressure = c.getDouble("press");
            e.pools.put(k, p);
        }
        e.storageCapacity = n.getDouble("capacity");
        NbtList pr = n.getList("prosperity", NbtElement.DOUBLE_TYPE);
        for (int i = 0; i < Math.min(pr.size(), e.prosperity.length); i++) {
            e.prosperity[i] = pr.getDouble(i);
        }
        e.score = n.getDouble("score");
        for (int i : n.getIntArray("leaders")) {
            e.leaders.add(i);
        }
        e.leadership = n.getString("leadership");
        for (NbtElement x : n.getList("projects", NbtElement.COMPOUND_TYPE)) {
            e.projects.add(Project.fromNbt((NbtCompound) x));
        }
        for (NbtElement x : n.getList("extras", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) x;
            Extra ex = new Extra();
            ex.id = c.getInt("id");
            ex.kind = c.getString("kind");
            ex.variant = c.getInt("v");
            ex.rotation = BlockRotation.values()[Math.min(c.getInt("rot"), 3)];
            ex.origin = BlockPos.fromLong(c.getLong("origin"));
            ex.entrance = BlockPos.fromLong(c.getLong("door"));
            int[] box = c.getIntArray("box");
            if (box.length == 4) {
                ex.x0 = box[0];
                ex.z0 = box[1];
                ex.x1 = box[2];
                ex.z1 = box[3];
            }
            for (long l : c.getLongArray("beds")) {
                ex.beds.add(BlockPos.fromLong(l));
            }
            for (long l : c.getLongArray("jobs")) {
                ex.jobs.add(BlockPos.fromLong(l));
            }
            for (NbtElement s : c.getList("jobProf", NbtElement.STRING_TYPE)) {
                ex.jobProfessions.add(s.asString());
            }
            ex.profession = c.getString("prof");
            e.extras.add(ex);
        }
        e.nextExtra = n.getInt("nextExtra");
        for (NbtElement s : n.getList("decisions", NbtElement.STRING_TYPE)) {
            e.decisions.addLast(s.asString());
        }
        for (NbtElement x : n.getList("events", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) x;
            e.events.addLast(new Event(c.getString("t"), c.getLong("at"), c.getString("s"), c.getString("d"), c.getLong("u")));
        }
        NbtList fl = n.getList("flows", NbtElement.DOUBLE_TYPE);
        double[] f = new double[fl.size()];
        for (int i = 0; i < f.length; i++) {
            f[i] = fl.getDouble(i);
        }
        if (f.length >= 10) {
            e.issued = f[0];
            e.sunk = f[1];
            e.playerIn = f[2];
            e.playerOut = f[3];
            e.tradeIn = f[4];
            e.tradeOut = f[5];
            e.wagesPaid = f[6];
            e.supportPaid = f[7];
            e.initialMoney = f[8];
            e.disruption = f[9];
        }
        NbtCompound li = n.getCompound("lastImport");
        for (String k : li.getKeys()) {
            e.lastImport.put(k, li.getLong(k));
        }
        NbtCompound le = n.getCompound("lastExport");
        for (String k : le.getKeys()) {
            e.lastExport.put(k, le.getLong(k));
        }
        e.gatheringUntil = n.getLong("gathering");
        e.gatheringReason = n.getString("gatheringWhy");
        e.growth = n.getInt("growth");
        return e;
    }
}
