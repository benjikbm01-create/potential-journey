package dev.underdeep.civ.npc;

import dev.underdeep.civ.arch.Blueprint;
import dev.underdeep.civ.arch.BuildingKind;
import dev.underdeep.civ.mind.Activity;
import dev.underdeep.civ.mind.Brain;
import dev.underdeep.civ.settlement.SettlementPlan;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where activities happen in a settlement (Stage 5): abstract {@link Activity.Place}s resolved to positions from
 * the (pure, deterministic) settlement plan. Shared spots (seats, gathering points, market, plaza) are handed
 * out through short reservations so people spread out instead of stacking on one block; reservations expire
 * by time and are released when an inhabitant leaves the active tier or dies.
 */
public final class Places {
    final List<BlockPos> farms = new ArrayList<>(), docks = new ArrayList<>(), libraries = new ArrayList<>(), taverns = new ArrayList<>(),
            plazas = new ArrayList<>(), markets = new ArrayList<>(), guards = new ArrayList<>(), clinics = new ArrayList<>(),
            edges = new ArrayList<>(), seats = new ArrayList<>(), landmarks = new ArrayList<>();
    final Map<Integer, BlockPos> doors = new HashMap<>();
    /** Reserved spot → (record index, expiry). */
    private final Map<Long, long[]> reserved = new HashMap<>();

    public Places(SettlementPlan plan) {
        int cx = plan.site().x(), cz = plan.site().z();
        List<SettlementPlan.Building> byDistance = new ArrayList<>(plan.buildings());
        for (SettlementPlan.Building b : plan.buildings()) {
            doors.put(b.index(), b.entrance());
            BuildingKind k = b.kind();
            switch (k) {
                case INN -> taverns.add(b.entrance());
                case LIBRARY, HALL -> libraries.add(b.entrance());
                case CLINIC -> clinics.add(b.entrance());
                case STALL, SHOP, TRADE_HALL -> markets.add(b.entrance());
                case LANDMARK, WELL, TOWER -> landmarks.add(b.entrance());
                default -> {
                }
            }
            for (Blueprint.Marker m : b.blueprint().markers()) {
                BlockPos wp = b.world(m.pos());
                switch (m.type()) {
                    case FARM -> farms.add(wp);
                    case DOCK -> docks.add(wp);
                    case GUARD -> guards.add(wp);
                    case SEAT -> (k == BuildingKind.INN ? taverns : seats).add(wp);
                    case GATHER -> {
                        if ("tavern".equals(m.tag())) {
                            taverns.add(wp);
                        } else if ("market".equals(m.tag())) {
                            markets.add(wp);
                        } else if (!"home".equals(m.tag())) {
                            plazas.add(wp);
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        for (SettlementPlan.Plaza p : plan.plazas()) {
            plazas.add(new BlockPos(p.x() + 2, p.y() + 1, p.z() + 2));
            plazas.add(new BlockPos(p.x() - 2, p.y() + 1, p.z() - 1));
        }
        // the settlement's edge: entrances of the outermost buildings (safe, reachable, at walking level)
        byDistance.sort((a, b) -> Long.compare(d2(b.entrance(), cx, cz), d2(a.entrance(), cx, cz)));
        for (int i = 0; i < Math.min(4, byDistance.size()); i++) {
            edges.add(byDistance.get(i).entrance());
        }
    }

    private static long d2(BlockPos p, int x, int z) {
        long dx = p.getX() - x, dz = p.getZ() - z;
        return dx * dx + dz * dz;
    }

    /** Fills the availability part of a decision context. */
    public void fill(Brain.Context c, SettlementRecord.InhabitantRecord r) {
        c.profession = r.profession;
        c.hasWork = r.work != null;
        c.hasFarm = !farms.isEmpty();
        c.hasWater = !docks.isEmpty();
        c.hasLibrary = !libraries.isEmpty();
        c.hasTavern = !taverns.isEmpty();
        c.hasMarket = !markets.isEmpty();
        c.hasClinic = !clinics.isEmpty();
        c.hasEdge = !edges.isEmpty();
        c.eveningGathering = !plazas.isEmpty();
        c.personalSeed = r.appearance;
    }

    @Nullable
    public BlockPos door(SettlementRecord rec, int index) {
        if (index < 0 || index >= rec.inhabitants.size()) {
            return null;
        }
        SettlementRecord.InhabitantRecord r = rec.inhabitants.get(index);
        BlockPos d = doors.get(r.home);
        return d != null ? d : r.bed;
    }

    private BlockPos pick(List<BlockPos> list, long seed, @Nullable BlockPos fallback, int who, long now, boolean reserve) {
        if (list.isEmpty()) {
            return fallback;
        }
        int start = (int) Math.floorMod(seed, (long) list.size());
        for (int i = 0; i < list.size(); i++) {
            BlockPos p = list.get((start + i) % list.size());
            if (!reserve) {
                return p;
            }
            long[] res = reserved.get(p.asLong());
            if (res == null || res[1] < now || res[0] == who) {
                reserved.put(p.asLong(), new long[]{who, now + 2400});
                return p;
            }
        }
        return list.get(start); // all taken: share (open spaces hold several people)
    }

    /** Releases every reservation of an inhabitant (left the active tier, died or changed activity). */
    public void release(int who) {
        reserved.values().removeIf(v -> v[0] == who);
    }

    /** The position where an activity takes place for this inhabitant (target = record index of a partner, or −1). */
    @Nullable
    public BlockPos resolve(SettlementRecord rec, SettlementRecord.InhabitantRecord r, Activity a, int target, long now) {
        BlockPos home = doors.getOrDefault(r.home, r.bed);
        long s = r.appearance ^ (now / 6000);
        release(r.index);
        return switch (a.place) {
            case BED -> r.bed;
            case HOME -> home;
            case WORK -> {
                if (a == Activity.TEACH_APPRENTICE || r.work == null) {
                    yield r.work != null ? r.work : home;
                }
                yield r.work;
            }
            case FARM -> pick(farms, s, home, r.index, now, false);
            case WATER -> r.work != null && r.profession.equals("fisher") ? r.work : pick(docks, s, home, r.index, now, false);
            case LIBRARY -> pick(libraries, s, home, r.index, now, false);
            case TAVERN -> r.profession.equals("innkeeper") && r.work != null && a == Activity.RUN_INN ? r.work : pick(taverns, s, home, r.index, now, true);
            case PLAZA -> pick(plazas.isEmpty() ? seats : plazas, s, home, r.index, now, true);
            case MARKET -> pick(markets, s, home, r.index, now, false);
            case PATROL -> {
                List<BlockPos> route = new ArrayList<>(guards);
                route.addAll(edges);
                route.addAll(plazas);
                yield route.isEmpty() ? home : route.get((int) Math.floorMod(r.appearance + now / 700, (long) route.size()));
            }
            case EDGE -> pick(edges, s, home, r.index, now, false);
            case FRIEND -> {
                BlockPos d = door(rec, target);
                yield d != null ? d : home;
            }
            case NEAR -> null; // stay where you are
            case WANDER -> home;
            case FAVORITE -> switch (r.mind == null ? "" : r.mind.favoritePlace) {
                case "garden" -> pick(farms, r.appearance, home, r.index, now, false);
                case "plaza" -> pick(plazas, r.appearance, home, r.index, now, true);
                case "tavern" -> pick(taverns, r.appearance, home, r.index, now, true);
                case "market" -> pick(markets, r.appearance, home, r.index, now, false);
                case "landmark" -> pick(landmarks, r.appearance, home, r.index, now, false);
                case "water" -> pick(docks, r.appearance, home, r.index, now, false);
                case "workshop" -> r.work != null ? r.work : home;
                default -> home;
            };
            case SHELTER -> r.bed != null ? r.bed : home;
            case GUARD -> pick(guards, s, home, r.index, now, false);
            case CLINIC -> pick(clinics, s, home, r.index, now, false);
        };
    }

    /** Human-readable place name for dialogue (only places that really exist). */
    public String describe(Activity.Place p) {
        return switch (p) {
            case TAVERN -> taverns.isEmpty() ? "home" : "the inn";
            case PLAZA -> "the square";
            case MARKET -> "the market";
            case LIBRARY -> "the library";
            case FARM -> "the fields";
            case WATER -> "the docks";
            case EDGE -> "the edge of town";
            case CLINIC -> "the clinic";
            default -> "home";
        };
    }
}
