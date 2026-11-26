package dev.underdeep.civ.econ;

import dev.underdeep.civ.arch.BuildingKind;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A construction project (Stage 6). Lifecycle: PLANNED → SITE (plot validated) → FUNDED (labour budget moved from
 * the treasury into the project) → MATERIALS (goods moved from the ledger into the project's site stock, which the
 * project owns) → BUILDING (builders' hours advance progress; blocks are placed physically, layer by layer, when
 * the plot's chunks are loaded) → COMPLETE (registered as a settlement building). Cancelling returns unused site
 * stock and budget; materials already built in are consumed.
 */
public final class Project {
    public enum Kind {
        HOUSE(BuildingKind.HOUSE), LARGE_HOUSE(BuildingKind.HOUSE_LARGE), SMALL_HOUSE(BuildingKind.HOUSE_SMALL), SHARED_HOUSE(BuildingKind.SHARED_HOUSE),
        WORKSHOP(BuildingKind.WORKSHOP), FARM(BuildingKind.FARM), PEN(BuildingKind.PEN), STOREHOUSE(BuildingKind.STOREHOUSE),
        STALL(BuildingKind.STALL), SHOP(BuildingKind.SHOP), INN(BuildingKind.INN), KITCHEN(BuildingKind.KITCHEN),
        GUARD_POST(BuildingKind.GUARD_POST), DOCK(BuildingKind.DOCK), HALL(BuildingKind.HALL), LIBRARY(BuildingKind.LIBRARY),
        CLINIC(BuildingKind.CLINIC), WELL(BuildingKind.WELL), TOWER(BuildingKind.TOWER), BARN(BuildingKind.BARN), REPAIR(null);

        public final BuildingKind building;

        Kind(BuildingKind b) {
            this.building = b;
        }
    }

    public enum Status { PLANNED, SITE, FUNDED, MATERIALS, BUILDING, COMPLETE, CANCELLED }

    public int id;
    public Kind kind = Kind.HOUSE;
    public Status status = Status.PLANNED;
    public String reason = "";
    public String profession = "";
    public int variant;
    public BlockRotation rotation = BlockRotation.NONE;
    public BlockPos origin = BlockPos.ORIGIN;
    /** Required material units by category and units already delivered to the site (owned by the project). */
    public final Map<String, Double> required = new TreeMap<>(), delivered = new TreeMap<>();
    /** Exactly which goods sit in the site stock (for refunds). */
    public final Map<String, Double> site = new TreeMap<>();
    public double budget, laborHours, laborDone;
    public long started, finished;
    public int placed, total;
    public boolean cleared;
    /** For repairs: the damaged building and how many blocks were missing. */
    public int target = -1;
    public final List<Long> repairs = new ArrayList<>();

    public double progress() {
        return laborHours <= 0 ? 0 : Math.min(1, laborDone / laborHours);
    }

    public boolean materialsComplete() {
        for (var e : required.entrySet()) {
            if (delivered.getOrDefault(e.getKey(), 0.0) + 1e-6 < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    public double materialShare() {
        double need = 0, have = 0;
        for (var e : required.entrySet()) {
            need += e.getValue();
            have += Math.min(e.getValue(), delivered.getOrDefault(e.getKey(), 0.0));
        }
        return need <= 0 ? 1 : have / need;
    }

    public boolean active() {
        return status != Status.COMPLETE && status != Status.CANCELLED;
    }

    private static NbtCompound map(Map<String, Double> m) {
        NbtCompound c = new NbtCompound();
        m.forEach(c::putDouble);
        return c;
    }

    private static void read(NbtCompound c, Map<String, Double> m) {
        for (String k : c.getKeys()) {
            m.put(k, c.getDouble(k));
        }
    }

    NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("id", id);
        n.putString("kind", kind.name());
        n.putString("status", status.name());
        n.putString("reason", reason);
        n.putString("prof", profession);
        n.putInt("v", variant);
        n.putInt("rot", rotation.ordinal());
        n.putLong("origin", origin.asLong());
        n.put("required", map(required));
        n.put("delivered", map(delivered));
        n.put("site", map(site));
        n.putDouble("budget", budget);
        n.putDouble("labor", laborHours);
        n.putDouble("done", laborDone);
        n.putLong("started", started);
        n.putLong("finished", finished);
        n.putInt("placed", placed);
        n.putInt("total", total);
        n.putBoolean("cleared", cleared);
        n.putInt("target", target);
        n.putLongArray("repairs", repairs.stream().mapToLong(Long::longValue).toArray());
        return n;
    }

    static Project fromNbt(NbtCompound n) {
        Project p = new Project();
        p.id = n.getInt("id");
        try {
            p.kind = Kind.valueOf(n.getString("kind"));
            p.status = Status.valueOf(n.getString("status"));
        } catch (IllegalArgumentException e) {
            p.status = Status.CANCELLED;
        }
        p.reason = n.getString("reason");
        p.profession = n.getString("prof");
        p.variant = n.getInt("v");
        p.rotation = BlockRotation.values()[Math.min(n.getInt("rot"), 3)];
        p.origin = BlockPos.fromLong(n.getLong("origin"));
        read(n.getCompound("required"), p.required);
        read(n.getCompound("delivered"), p.delivered);
        read(n.getCompound("site"), p.site);
        p.budget = n.getDouble("budget");
        p.laborHours = n.getDouble("labor");
        p.laborDone = n.getDouble("done");
        p.started = n.getLong("started");
        p.finished = n.getLong("finished");
        p.placed = n.getInt("placed");
        p.total = n.getInt("total");
        p.cleared = n.getBoolean("cleared");
        p.target = n.getInt("target");
        for (long l : n.getLongArray("repairs")) {
            p.repairs.add(l);
        }
        return p;
    }
}
