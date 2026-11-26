package dev.underdeep.civ.econ;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One physical shipment of goods between two settlements (Stage 6). Lifecycle:
 * AGREED → TRAVELLING (cargo has left the origin ledger and belongs to the shipment; the buyer's payment is
 * escrowed in the shipment) → ARRIVED (cargo enters the destination ledger exactly once, escrow is released to
 * the seller) → RETURNING (the caravan walks home) → DONE. LOST: cargo destroyed or abandoned, escrow refunded.
 * The same record drives the abstract journey and the physical caravan near players.
 */
public final class Shipment implements Tx.Account {
    public enum Status { AGREED, TRAVELLING, ARRIVED, RETURNING, DONE, LOST }

    public enum Method {
        PORTER(170, 24), PACK_TRAIN(140, 96), BOAT(320, 160);

        /** Blocks per in-game hour and cargo capacity in units. */
        public final double speed;
        public final int capacity;

        Method(double speed, int capacity) {
            this.speed = speed;
            this.capacity = capacity;
        }
    }

    public long id, origin, dest;
    public Method method = Method.PACK_TRAIN;
    public Status status = Status.AGREED;
    public final Map<String, Double> cargo = new TreeMap<>();
    public double escrow, price, fee;
    public boolean aid;
    public String reason = "";
    public final List<double[]> route = new ArrayList<>();
    public double length, progress;
    public long created, departed, arrived, delayedUntil, lastUpdate;
    public int leader = -1;
    public final List<Integer> guards = new ArrayList<>();
    public int animals;
    public double condition = 1;
    public String incident = "";
    public final Map<UUID, Integer> escort = new HashMap<>();

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
        return "shipment " + id;
    }

    public double units() {
        double t = 0;
        for (double v : cargo.values()) {
            t += v;
        }
        return t;
    }

    /** Position along the route at a distance travelled (from the destination end while returning). */
    public double[] positionAt(double d) {
        if (route.isEmpty()) {
            return new double[]{0, 0, 0};
        }
        double t = Math.max(0, Math.min(length, d));
        for (int i = 0; i + 1 < route.size(); i++) {
            double[] a = route.get(i), b = route.get(i + 1);
            double seg = Math.hypot(b[0] - a[0], b[2] - a[2]);
            if (t <= seg || i + 2 == route.size()) {
                double f = seg <= 0 ? 0 : Math.min(1, t / seg);
                return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
            }
            t -= seg;
        }
        return route.get(route.size() - 1);
    }

    /** Where the caravan is now (returning caravans walk the route backwards). */
    public double[] position() {
        return status == Status.RETURNING ? positionAt(length - progress) : positionAt(progress);
    }

    public boolean moving() {
        return status == Status.TRAVELLING || status == Status.RETURNING;
    }

    NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putLong("id", id);
        n.putLong("o", origin);
        n.putLong("d", dest);
        n.putString("method", method.name());
        n.putString("status", status.name());
        NbtCompound c = new NbtCompound();
        cargo.forEach(c::putDouble);
        n.put("cargo", c);
        n.putDouble("escrow", escrow);
        n.putDouble("price", price);
        n.putDouble("fee", fee);
        n.putBoolean("aid", aid);
        n.putString("reason", reason);
        n.put("route", EconomyState.route(route));
        n.putDouble("len", length);
        n.putDouble("prog", progress);
        n.putLong("created", created);
        n.putLong("departed", departed);
        n.putLong("arrived", arrived);
        n.putLong("delay", delayedUntil);
        n.putLong("upd", lastUpdate);
        n.putInt("leader", leader);
        n.putIntArray("guards", guards.stream().mapToInt(Integer::intValue).toArray());
        n.putInt("animals", animals);
        n.putDouble("cond", condition);
        n.putString("incident", incident);
        NbtCompound es = new NbtCompound();
        escort.forEach((u, t) -> es.putInt(u.toString(), t));
        n.put("escort", es);
        return n;
    }

    static Shipment fromNbt(NbtCompound n) {
        Shipment s = new Shipment();
        s.id = n.getLong("id");
        s.origin = n.getLong("o");
        s.dest = n.getLong("d");
        try {
            s.method = Method.valueOf(n.getString("method"));
            s.status = Status.valueOf(n.getString("status"));
        } catch (IllegalArgumentException e) {
            s.status = Status.LOST;
        }
        NbtCompound c = n.getCompound("cargo");
        for (String k : c.getKeys()) {
            s.cargo.put(k, c.getDouble(k));
        }
        s.escrow = n.getDouble("escrow");
        s.price = n.getDouble("price");
        s.fee = n.getDouble("fee");
        s.aid = n.getBoolean("aid");
        s.reason = n.getString("reason");
        EconomyState.readRoute(n.getList("route", NbtElement.LIST_TYPE), s.route);
        s.length = n.getDouble("len");
        s.progress = n.getDouble("prog");
        s.created = n.getLong("created");
        s.departed = n.getLong("departed");
        s.arrived = n.getLong("arrived");
        s.delayedUntil = n.getLong("delay");
        s.lastUpdate = n.getLong("upd");
        s.leader = n.getInt("leader");
        for (int g : n.getIntArray("guards")) {
            s.guards.add(g);
        }
        s.animals = n.getInt("animals");
        s.condition = n.getDouble("cond");
        s.incident = n.getString("incident");
        NbtCompound es = n.getCompound("escort");
        for (String k : es.getKeys()) {
            s.escort.put(UUID.fromString(k), es.getInt(k));
        }
        return s;
    }
}
