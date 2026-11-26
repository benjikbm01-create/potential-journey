package dev.underdeep.debug;

import dev.underdeep.civ.Civilization;
import dev.underdeep.civ.arch.Blueprint;
import dev.underdeep.civ.arch.BuildingKind;
import dev.underdeep.civ.settlement.SettlementCategory;
import dev.underdeep.civ.settlement.SettlementMap;
import dev.underdeep.civ.settlement.SettlementPlan;
import dev.underdeep.civ.settlement.SettlementSite;
import dev.underdeep.civ.settlement.Settlements;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Stage 4 settlement checks: distribution, plans, generated buildings, entrances, determinism inputs. */
final class Stage4Checks {
    private Stage4Checks() {
    }

    static SettlementSite starter, town;

    static void run(ServerWorld w, UnderdeepTerrain t, Path dir, Stage15Checks.Reporter r, boolean create) {
        r.section("Stage 4 civilizations: settlement distribution (pure model)");
        SettlementMap map = Settlements.of(t);
        r.check("settlements enabled for a new world (generator v3)", map != null, "map=" + map);
        if (map == null) {
            return;
        }
        List<SettlementSite> near = map.sitesNear(0, 0, 3000);
        Map<SettlementCategory, Integer> counts = new EnumMap<>(SettlementCategory.class);
        Set<Civilization> civs = EnumSet.noneOf(Civilization.class);
        double welcoming = 1e9, townD = 1e9;
        starter = null;
        town = null;
        for (SettlementSite s : near) {
            counts.merge(s.category(), 1, Integer::sum);
            civs.add(s.civ());
            double d = Math.hypot(s.x(), s.z());
            if (s.civ().welcoming() && d < welcoming) {
                welcoming = d;
                starter = s;
            }
            if (s.category().atLeast(SettlementCategory.TOWN) && d < townD) {
                townD = d;
                town = s;
            }
        }
        r.info("settlements within 3000 blocks", near.size() + " " + counts + "; civilizations " + civs.size());
        r.check("a welcoming settlement within ~300 blocks of spawn", welcoming <= 340, String.format(Locale.ROOT, "%.0f blocks (%s %s)",
                welcoming, starter == null ? "-" : starter.civ().id(), starter == null ? "-" : starter.category().id()));
        r.check("a town or city within ~1200 blocks of spawn", townD <= 1250, String.format(Locale.ROOT, "%.0f blocks", townD));
        r.check("all eight civilizations within 3000 blocks", civs.size() == 8, civs.toString());

        r.section("Stage 4 civilizations: settlement plans");
        int plansOk = 0, plans = 0;
        StringBuilder detail = new StringBuilder();
        for (SettlementSite s : near) {
            if (Math.hypot(s.x(), s.z()) > 1500) {
                continue;
            }
            SettlementPlan p = map.plan(s);
            plans++;
            int bld = p.buildingCount();
            boolean ok = bld >= Math.max(1, s.category().minBuildings / 2) || s.category() == SettlementCategory.ENCAMPMENT && bld >= 1;
            plansOk += ok ? 1 : 0;
            if (!ok || detail.length() < 900) {
                detail.append(String.format(Locale.ROOT, "%s %s %s r%d: %d bld, %d beds, %d roads; ", p.name(), s.civ().id(), s.category().id(),
                        s.radius(), bld, p.beds(), p.roads().size()));
            }
        }
        r.info("plot rejections", new java.util.TreeMap<>(dev.underdeep.civ.settlement.SettlementPlanner.REJECTS).toString());
        r.check("settlement plans reach a sensible size (≥ half the category minimum)", plansOk >= plans * 0.85,
                plansOk + "/" + plans + " within 1500 blocks; " + detail);
        if (!create || starter == null) {
            return;
        }
        r.section("Stage 4 civilizations: generated settlements (real world generation)");
        StringBuilder tour = new StringBuilder("# name x y z yaw pitch [natural]\n");
        for (SettlementSite s : town == null ? List.of(starter) : List.of(starter, town)) {
            SettlementPlan p = map.plan(s);
            int rad = s.radius() + 24;
            for (int cx = (s.x() - rad) >> 4; cx <= (s.x() + rad) >> 4; cx++) {
                for (int cz = (s.z() - rad) >> 4; cz <= (s.z() + rad) >> 4; cz++) {
                    w.getChunk(cx, cz, ChunkStatus.FULL, true);
                }
            }
            int matched = 0, total = 0, entrances = 0, entrancesOk = 0;
            StringBuilder bad = new StringBuilder();
            for (SettlementPlan.Building b : p.buildings()) {
                int[] mt = {0, 0};
                b.blueprint().forEach((lp, st) -> {
                    if (st.isAir() || lp.getY() < 0) {
                        return;
                    }
                    BlockPos wp = b.world(lp);
                    mt[1]++;
                    BlockState cur = w.getBlockState(wp);
                    if (cur.getBlock() == st.getBlock()) {
                        mt[0]++;
                    }
                });
                matched += mt[0];
                total += mt[1];
                if (b.kind().countsAsBuilding() || b.kind() == BuildingKind.DOCK) {
                    entrances++;
                    BlockPos e = b.entrance();
                    boolean feet = w.getBlockState(e).getCollisionShape(w, e).isEmpty();
                    boolean head = w.getBlockState(e.up()).getCollisionShape(w, e.up()).isEmpty();
                    BlockState below = w.getBlockState(e.down());
                    boolean ground = !below.getCollisionShape(w, e.down()).isEmpty();
                    if (feet && head && ground) {
                        entrancesOk++;
                    } else if (bad.length() < 300) {
                        bad.append(b.kind().id()).append("@").append(e.toShortString()).append(feet ? "" : " feet").append(head ? "" : " head")
                                .append(ground ? "" : " ground").append("; ");
                    }
                }
            }
            double share = total == 0 ? 0 : matched / (double) total;
            r.check(p.name() + " (" + s.civ().id() + " " + s.category().id() + "): buildings generated as designed",
                    share >= 0.97, String.format(Locale.ROOT, "%.1f %% of %d blueprint blocks present; %d buildings", share * 100, total, p.buildingCount()));
            r.check(p.name() + ": building entrances open onto walkable ground", entrancesOk >= entrances * 0.9,
                    entrancesOk + "/" + entrances + (bad.length() > 0 ? "; blocked: " + bad : ""));
            // tour: an overview from above the plaza edge and street-level views toward several buildings
            SettlementPlan.Plaza c = p.plazas().get(0);
            tour.append(String.format(Locale.ROOT, "settle_%s_overview %d %d %d 0 35 natural\n", s.civ().id(), c.x(), c.y() + 22, c.z() - s.radius() / 2));
            int k = 0;
            for (SettlementPlan.Building b : p.buildings()) {
                if (!b.kind().countsAsBuilding() || k >= 6) {
                    continue;
                }
                BlockPos e = b.entrance();
                double dx = e.getX() - b.origin().getX() - b.blueprint().rotatedWidth(b.rotation()) / 2.0;
                double dz = e.getZ() - b.origin().getZ() - b.blueprint().rotatedDepth(b.rotation()) / 2.0;
                double len = Math.max(0.1, Math.hypot(dx, dz));
                double vx = e.getX() + dx / len * 9, vz = e.getZ() + dz / len * 9;
                float yaw = (float) Math.toDegrees(Math.atan2(-(e.getX() - vx), e.getZ() - vz));
                tour.append(String.format(Locale.ROOT, "settle_%s_%s_%d %.1f %d %.1f %.0f 8 natural\n", s.civ().id(), b.kind().id(), k++, vx,
                        e.getY() + 2, vz, yaw));
            }
        }
        try {
            Files.writeString(dir.resolve("tour_settlements.txt"), tour.toString());
        } catch (Exception e) {
            r.info("tour", "could not write: " + e);
        }
    }
}
