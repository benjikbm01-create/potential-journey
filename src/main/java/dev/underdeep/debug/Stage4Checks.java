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
        if (!create) {
            persistence(w, map, dir, r);
            return;
        }
        if (starter == null) {
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
        inhabitants(w, map, dir, r);
        try {
            Files.writeString(dir.resolve("tour_settlements.txt"), tour.toString());
        } catch (Exception e) {
            r.info("tour", "could not write: " + e);
        }
    }

    // ------------------------------------------------------------------ inhabitants

    private static void inhabitants(ServerWorld w, SettlementMap map, Path dir, Stage15Checks.Reporter r) {
        r.section("Stage 4 civilizations: inhabitants, routines, trading (fake player; entities ticked directly = accelerated)");
        dev.underdeep.civ.npc.InhabitantManager m = dev.underdeep.civ.npc.InhabitantManager.get(w);
        if (m == null) {
            r.check("inhabitant manager active", false, "none");
            return;
        }
        SettlementSite s = town != null ? town : starter;
        SettlementPlan plan = map.plan(s);
        var state = dev.underdeep.civ.npc.SettlementState.get(w);
        var rec = state.getOrCreate(plan, w.getSeed(), w.getTime());
        java.util.Set<String> profs = new java.util.TreeSet<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        int withBed = 0, withWork = 0;
        for (var ir : rec.inhabitants) {
            profs.add(ir.profession);
            names.add(ir.name);
            withBed += ir.bed != null ? 1 : 0;
            withWork += ir.work != null ? 1 : 0;
        }
        r.check(rec.name + ": registered inhabitants with homes and identities",
                rec.inhabitants.size() >= Math.min(plan.beds(), s.category().minPeople) && withBed == rec.inhabitants.size(),
                rec.inhabitants.size() + " inhabitants (" + plan.beds() + " beds), " + withWork + " with a workplace, " + names.size()
                        + " distinct names; professions " + profs);
        net.fabricmc.fabric.api.entity.FakePlayer fake = net.fabricmc.fabric.api.entity.FakePlayer.get(w);
        SettlementPlan.Plaza c = plan.plazas().get(0);
        fake.refreshPositionAndAngles(c.x() + 0.5, c.y() + 1, c.z() + 0.5, 0, 0);
        long day = w.getTimeOfDay();
        w.setTimeOfDay(day - day % 24000 + 6000); // midday: work
        var cfg = dev.underdeep.config.UnderdeepConfig.get();
        m.populate(map, s, List.of(fake), cfg);
        int active = m.activeIn(s.id());
        m.populate(map, s, List.of(fake), cfg);
        int again = m.activeIn(s.id());
        java.util.Set<Integer> idx = new java.util.HashSet<>();
        boolean dup = false;
        for (var e : m.activeEntities(s.id())) {
            dup |= !idx.add(e.recordIndex);
        }
        r.check("inhabitants appear near a player within the per-settlement budget, without duplicates", active > 0 && active <= cfg.maxActiveInhabitantsPerSettlement
                && again == active && !dup, active + " active (budget " + cfg.maxActiveInhabitantsPerSettlement + "), repeated population added "
                + (again - active));
        r.check("inhabitants are never written to chunk storage", m.activeEntities(s.id()).stream().noneMatch(net.minecraft.entity.Entity::shouldSave),
                "shouldSave=false for all");
        // routine: at midday workers head for their workplace; at night everyone with a bed sleeps there
        var list = new java.util.ArrayList<>(m.activeEntities(s.id()));
        int working = 0;
        for (var e : list) {
            working += e.activity() == dev.underdeep.civ.npc.InhabitantEntity.Activity.WORK
                    || e.activity() == dev.underdeep.civ.npc.InhabitantEntity.Activity.PATROL ? 1 : 0;
        }
        w.setTimeOfDay(day - day % 24000 + 18000);
        int sleepers = 0, inBed = 0;
        for (int i = 0; i < 1200; i++) {
            if (w.getLevelProperties() instanceof net.minecraft.world.level.ServerWorldProperties props) {
                props.setTime(w.getTime() + 1);
            }
            for (var e : list) {
                if (e.isAlive()) {
                    e.age++;
                    e.tick();
                }
            }
        }
        for (var e : list) {
            if (e.activity() == dev.underdeep.civ.npc.InhabitantEntity.Activity.SLEEP) {
                sleepers++;
                inBed += e.isSleeping() ? 1 : 0;
            }
        }
        r.check("daily routine: work by day, sleep in their own bed at night (60 s accelerated)", working > 0 && sleepers > 0 && inBed >= sleepers * 0.6,
                working + " at work/patrol at midday; at night " + inBed + "/" + sleepers + " asleep in bed");
        // trading
        dev.underdeep.civ.npc.InhabitantEntity trader = null;
        for (var e : list) {
            if (!e.getOffers().isEmpty()) {
                trader = e;
                break;
            }
        }
        if (trader != null) {
            var offer = trader.getOffers().get(0);
            int before = offer.getUses();
            offer.use();
            trader.trade(offer);
            var ir = rec.inhabitants.get(trader.recordIndex);
            r.check("trading: civilization goods offered; trades recorded for daily restock", trader.getOffers().size() >= 3 && ir.tradeUses.length > 0
                    && ir.tradeUses[0] >= 1, trader.getName().getString() + " (" + trader.profession() + ") offers " + trader.getOffers().size()
                    + ": sells " + offer.getSellItem().getCount() + "x " + offer.getSellItem().getItem() + "; uses " + before + " -> " + offer.getUses());
        } else {
            r.check("trading: an inhabitant offers trades", false, "no trader among " + list.size());
        }
        // death is permanent; prepare a restart check
        var victim = list.get(list.size() - 1);
        int vIdx = victim.recordIndex;
        victim.damage(w.getDamageSources().playerAttack(fake), 1000);
        m.populate(map, s, List.of(fake), cfg);
        boolean resurrected = m.activeEntities(s.id()).stream().anyMatch(e -> e.recordIndex == vIdx);
        r.check("killed inhabitants stay dead (record updated, not re-embodied)", !rec.inhabitants.get(vIdx).alive && !resurrected,
                "alive=" + rec.inhabitants.get(vIdx).alive + ", re-embodied=" + resurrected);
        // discovery
        m.discover(fake, map, s);
        r.check("settlement discovery recorded per player", rec.discoveredBy.contains(fake.getUuid()), rec.name);
        // hostile exclusion
        boolean blocked = !dev.underdeep.world.spawn.SpawnRules.allowMonster(map.terrain(), w.getBiome(new BlockPos(s.x(), c.y() + 1, s.z())),
                new BlockPos(s.x(), c.y() + 1, s.z()));
        r.check("no natural hostile spawns inside settlements", blocked, "allowMonster at the centre = " + !blocked);
        // inhabitants leave with the player
        for (var e : new java.util.ArrayList<>(m.activeEntities(s.id()))) {
            e.discard();
        }
        r.check("inhabitants return to their records when removed", m.activeIn(s.id()) == 0, m.activeIn(s.id()) + " left active");
        w.setTimeOfDay(day);
        try {
            Files.writeString(dir.resolve("stage4_reload.txt"), s.id() + " " + vIdx + " " + fake.getUuid() + " " + rec.inhabitants.size() + "\n");
        } catch (Exception ex) {
            r.info("stage 4 reload data", ex.toString());
        }
    }

    private static void persistence(ServerWorld w, SettlementMap map, Path dir, Stage15Checks.Reporter r) {
        r.section("Stage 4 civilizations: persistence after save and restart");
        Path f = dir.resolve("stage4_reload.txt");
        if (!Files.exists(f)) {
            r.info("stage 4 persistence", "no create-phase data");
            return;
        }
        try {
            String[] p = Files.readString(f).trim().split(" ");
            long id = Long.parseLong(p[0]);
            int victim = Integer.parseInt(p[1]);
            java.util.UUID player = java.util.UUID.fromString(p[2]);
            int people = Integer.parseInt(p[3]);
            var rec = dev.underdeep.civ.npc.SettlementState.get(w).get(id);
            r.check("settlement record persisted with the same inhabitants", rec != null && rec.inhabitants.size() == people,
                    rec == null ? "missing" : rec.name + ": " + rec.inhabitants.size() + " inhabitants");
            if (rec != null) {
                r.check("dead inhabitant still dead after restart", !rec.inhabitants.get(victim).alive, rec.inhabitants.get(victim).name);
                r.check("discovery persisted", rec.discoveredBy.contains(player), rec.discoveredBy.size() + " discoverers");
            }
            SettlementSite s = map.byId(id);
            r.check("settlement plan identical after restart (pure function of the seed)", s != null && map.plan(s).name().equals(rec.name),
                    s == null ? "missing" : map.plan(s).name());
        } catch (Exception e) {
            r.check("stage 4 persistence", false, e.toString());
        }
    }
}
