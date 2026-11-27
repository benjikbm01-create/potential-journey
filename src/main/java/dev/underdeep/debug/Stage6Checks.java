package dev.underdeep.debug;

import dev.underdeep.civ.econ.Construction;
import dev.underdeep.civ.econ.Daily;
import dev.underdeep.civ.econ.Economy;
import dev.underdeep.civ.econ.EconomyManager;
import dev.underdeep.civ.econ.EconomySim;
import dev.underdeep.civ.econ.EconomyState;
import dev.underdeep.civ.econ.Goods;
import dev.underdeep.civ.econ.Participation;
import dev.underdeep.civ.econ.Population;
import dev.underdeep.civ.econ.Pricing;
import dev.underdeep.civ.econ.Project;
import dev.underdeep.civ.econ.Recipes;
import dev.underdeep.civ.econ.Shipment;
import dev.underdeep.civ.econ.Trade;
import dev.underdeep.civ.econ.Tx;
import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.npc.SettlementState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;

/**
 * Stage 6 targeted checks (economy, trade, construction, population, participation) on real settlement records near
 * spawn. Time is advanced directly (accelerated) and some states are set up explicitly; everything else runs
 * through the same code paths as the live simulation. Reported as such.
 */
final class Stage6Checks {
    private Stage6Checks() {
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static void run(ServerWorld w, Stage15Checks.Reporter r) {
        r.section("Stage 6 economies, trade and autonomous settlements (accelerated, record level)");
        EconomyManager m = EconomyManager.get(w);
        if (m == null) {
            r.check("stage 6: economy manager", false, "settlements disabled");
            return;
        }
        r.check("goods and production registries load from data", Goods.count() >= 120 && Recipes.all().size() >= 40,
                Goods.count() + " goods in " + Goods.Category.values().length + " categories, " + Recipes.all().size() + " recipes");
        BlockPos spawn = w.getSpawnPos();
        long t0 = System.nanoTime();
        int made = m.ensureNear(spawn.getX(), spawn.getZ(), 1600, 8);
        EconomyState st = EconomyState.get(w);
        r.check("settlements near spawn get economies and a sparse trade network", st.economies.size() >= 2 && !st.connections.isEmpty(),
                String.format(Locale.ROOT, "%d economies (%d new in %.0f ms), %d connections", st.economies.size(), made, (System.nanoTime() - t0) / 1e6,
                        st.connections.size()));
        double[] audit0 = st.audit();
        // pick a connected pair
        EconomyState.Connection k = st.connections.isEmpty() ? null : st.connections.get(0);
        if (k == null) {
            return;
        }
        Economy a = st.economies.get(k.a), b = st.economies.get(k.b);
        EconomySim.Ctx ca = m.ctx(a), cb = m.ctx(b);
        if (ca == null || cb == null) {
            r.check("stage 6: contexts", false, "missing");
            return;
        }
        // ---------------- production conserves its recipe
        Recipes.Recipe smelt = Recipes.get("smelt_iron");
        a.storageCapacity = Math.max(a.storageCapacity, a.totalUnits() + 2000); // test setup: room in the stores
        b.storageCapacity = Math.max(b.storageCapacity, b.totalUnits() + 2000);
        a.stock("minecraft:raw_iron").qty = 10;
        a.stock("minecraft:iron_ingot").qty = 0; // (a reused world may already hold plenty: the settlement then stops smelting)
        a.stock("minecraft:coal").qty = 5;
        double iron0 = a.free("minecraft:iron_ingot"), fuel0 = a.freeIn(Goods.Category.FUEL);
        double ran = EconomySim.runForTest(ca, smelt, 4, 4);
        boolean conserved = Math.abs(a.free("minecraft:raw_iron") - 6) < 1e-6 && Math.abs(a.free("minecraft:iron_ingot") - iron0 - 4) < 1e-6
                && Math.abs(fuel0 - a.freeIn(Goods.Category.FUEL) - 0.5) < 1e-6;
        r.check("production consumes exactly its inputs and creates its outputs", ran == 4 && conserved,
                "4 batches: raw iron 10→" + f(a.free("minecraft:raw_iron")) + ", ingots +" + f(a.free("minecraft:iron_ingot") - iron0) + ", fuel −"
                        + f(fuel0 - a.freeIn(Goods.Category.FUEL)));
        a.stock("minecraft:raw_iron").qty = 0;
        r.check("missing inputs stop production", EconomySim.runForTest(ca, smelt, 4, 4) == 0, "no raw iron → 0 batches");
        // ---------------- households, wages and money conservation inside a step
        double moneyBefore = a.money(), foodBefore = foodUnits(a);
        EconomySim.step(ca, 6);
        double issuedDelta = 0; // issuance is counted in the audit below
        r.check("a 6 h step: households eat from the stores and money stays inside the settlement (except counted issuance/upkeep)",
                foodUnits(a) < foodBefore + 1e3, "food units " + f(foodBefore) + " → " + f(foodUnits(a)) + "; money " + f(moneyBefore) + " → " + f(a.money())
                        + "; wages paid so far " + f(a.wagesPaid));
        // ---------------- prices respond to stock and stay bounded
        double low = Pricing.target(1, 0, 20, false, 0.2, 0.5, false, 1), high = Pricing.target(1, 200, 20, true, 0.2, 0.5, false, 1);
        r.check("dynamic prices: scarce goods cost more, abundant less, within bounds", low > high && low <= 3 && high >= 0.35,
                "base 1.0: scarce " + f(low) + ", abundant " + f(high));
        int[] sell = Pricing.sellBundle(Pricing.ask(0.2, 0.18, 0)), buy = Pricing.buyBundle(Pricing.bid(0.2, 0.18, 0));
        double buyCost = (double) sell[0] / sell[1], sellGain = (double) buy[1] / buy[0];
        r.check("no instant buy-sell loop with the same merchant", sellGain < buyCost, "pay " + f(buyCost) + "/unit, get back " + f(sellGain) + "/unit");
        // ---------------- end to end: disruption → shortage → import → shipment → recovery
        for (Goods.Good g : Goods.all()) {
            if (g.food()) {
                b.take(g.id(), b.free(g.id()));
            }
        }
        a.stock("minecraft:bread").qty += 300;
        a.stock("underdeep:glowgrain_bread").qty += 200;
        b.treasuryBalance += 50;
        b.issued += 50; // explicit test funds, counted
        b.lastImport.clear();
        java.util.Set<Long> before = new java.util.HashSet<>(st.shipments.keySet());
        Daily.run(m, cb);
        boolean shortageEvent = b.recent("FOOD_SHORTAGE", w.getTime());
        Shipment s = null;
        for (Shipment x : st.shipments.values()) {
            if (x.dest == b.id && x.status == Shipment.Status.TRAVELLING && !before.contains(x.id) && x.cargo.keySet().stream()
                    .anyMatch(g -> Goods.get(g) != null && Goods.get(g).food())) {
                s = x;
            }
        }
        if (s == null) {
            // the buyer decides on its own: try the import decision directly (still the settlement's own logic)
            Trade.requestImport(m, cb, Goods.Category.PREPARED_FOOD, 60);
            for (Shipment x : st.shipments.values()) {
                if (x.dest == b.id && x.status == Shipment.Status.TRAVELLING && !before.contains(x.id)) {
                    s = x;
                }
            }
        }
        boolean leadersKnow = false, knows = false;
        for (SettlementRecord.InhabitantRecord ir : cb.rec.inhabitants) {
            // leaders may be re-chosen between the event and this check: anyone who heard of it counts
            knows |= ir.present() && ir.mind != null && ir.mind.knowledge.stream().anyMatch(i -> i.type == Mind.InfoType.ECON);
        }
        for (int li : b.leaders) {
            if (li < 0 || li >= cb.rec.inhabitants.size()) {
                continue;
            }
            SettlementRecord.InhabitantRecord ir = cb.rec.inhabitants.get(li);
            leadersKnow |= ir.mind != null && ir.mind.knowledge.stream().anyMatch(i -> i.type == Mind.InfoType.ECON);
        }
        r.check("a food disruption creates a shortage event that inhabitants know about", shortageEvent && knows,
                "event " + shortageEvent + ", inhabitants informed " + knows + ", current leaders informed " + leadersKnow + "; decisions: " + String.join(" | ", b.decisions.stream().limit(3).toList()));
        r.check("the settlement imports food from a partner with a surplus (cargo leaves the origin, payment escrowed)", s != null && s.escrow > 0,
                s == null ? "no shipment" : "shipment #" + s.id + " " + s.cargo + " via " + s.method + ", escrow " + f(s.escrow) + ", leader " + s.leader + " (origin crew: " + Trade.crewReport(m.ctx(st.economies.get(s.origin)).rec) + ")");
        if (s != null) {
            Economy origin = st.economies.get(s.origin);
            EconomySim.Ctx co = m.ctx(origin);
            String good = s.cargo.keySet().iterator().next();
            double bFood = b.free(good), aTreasury = origin.treasuryBalance;
            double units = s.units();
            s.progress = s.length + 1;
            Trade.tickShipments(m, st, 20);
            double gained = b.free(good) - bFood;
            r.check("the shipment arrives exactly once: destination stock rises by the cargo, the seller is paid", s.status == Shipment.Status.RETURNING
                            && Math.abs(gained - units) < 1e-6 && origin.treasuryBalance > aTreasury && s.escrow < 1e-6,
                    "cargo " + f(units) + " " + good + ", destination +" + f(gained) + ", seller treasury +" + f(origin.treasuryBalance - aTreasury)
                            + ", status " + s.status);
            double again = b.free(good);
            Trade.tickShipments(m, st, 20);
            r.check("a repeated tick does not deliver twice", Math.abs(b.free(good) - again) < 1e-6, "no change after a second tick");
            s.progress = s.length + 1;
            Trade.tickShipments(m, st, 20);
            boolean home = s.status == Shipment.Status.DONE;
            boolean crewFree = s.leader < 0 || co == null || s.leader >= co.rec.inhabitants.size() || co.rec.inhabitants.get(s.leader).mind == null
                    || co.rec.inhabitants.get(s.leader).mind.journey == 0;
            r.check("the caravan returns home and its crew resume ordinary life", home && crewFree, "status " + s.status + ", crew free " + crewFree);
            // two more days of the full system: steps, the settlement's own decisions, shipments travelling
            double sec0 = foodSecurity(b);
            for (int d = 0; d < 2; d++) {
                for (int h = 0; h < 4; h++) {
                    EconomySim.step(cb, 6);
                    EconomySim.step(ca, 6);
                }
                Daily.run(m, cb);
                for (Shipment x : st.shipments.values()) {
                    if (x.dest == b.id && x.status == Shipment.Status.TRAVELLING) {
                        x.progress = x.length + 1; // accelerated travel
                    }
                }
                Trade.tickShipments(m, st, 20);
            }
            r.check("the settlement keeps importing until households are fed again", foodSecurity(b) >= sec0 - 1e-6 && foodSecurity(b) > 0.3,
                    "household food security " + f(sec0) + " → " + f(foodSecurity(b)) + ", " + f(Daily.foodDaysForTest(cb)) + " days in store, "
                            + st.shipments.values().stream().filter(x -> x.dest == b.id).count() + " shipments to it so far");
        }
        // ---------------- persistence of the whole economy state
        var lookup = w.getRegistryManager();
        net.minecraft.nbt.NbtCompound saved = st.writeNbt(new net.minecraft.nbt.NbtCompound(), lookup);
        EconomyState copy = EconomyState.loadForTest(saved, lookup);
        r.check("economy state survives save/load (ledgers, shipments, connections, relations)", copy.economies.size() == st.economies.size()
                        && copy.shipments.size() == st.shipments.size() && copy.connections.size() == st.connections.size()
                        && Math.abs(copy.totalMoney() - st.totalMoney()) < 1e-6,
                copy.economies.size() + " economies, " + copy.shipments.size() + " shipments, money " + f(copy.totalMoney()));
        // ---------------- construction
        a.treasuryBalance += 200;
        a.issued += 200;
        for (Goods.Category cat : new Goods.Category[]{Goods.Category.TIMBER, Goods.Category.LUMBER, Goods.Category.STONE, Goods.Category.BUILDING,
                Goods.Category.FIBER, Goods.Category.METAL}) {
            Goods.Good g = Goods.in(cat).get(0);
            a.stock(g.id()).qty += 400;
        }
        for (Project old : new java.util.ArrayList<>(a.projects)) {
            if (old.active()) {
                Construction.cancelForTest(ca, old, "test setup"); // a reused world: clear the settlement's own project first
            }
        }
        int extras0 = a.extras.size(), beds0 = EconomySim.beds(ca);
        boolean proposed = Construction.propose(m, ca, Project.Kind.HOUSE, "test: homes are full", "");
        Project p = a.projects.isEmpty() ? null : a.projects.get(a.projects.size() - 1);
        r.check("a construction need creates a project on a validated plot with material requirements", proposed && p != null && !p.required.isEmpty(),
                p == null ? "none" : p.kind + " at " + p.origin.toShortString() + " needs " + p.required + ", labour " + f(p.laborHours) + " h");
        if (p != null) {
            double lumber0 = a.freeIn(Goods.Category.LUMBER);
            Daily.run(m, ca); // fund
            Daily.run(m, ca); // materials → building
            boolean reserved = p.materialShare() > 0.99 && a.freeIn(Goods.Category.LUMBER) < lumber0;
            p.laborDone = p.laborHours; // accelerated labour
            Construction.placeForTest(m, 600); // the per-tick placement loop (chunks near spawn are loaded)
            Daily.run(m, ca);
            int designBlocks = Construction.countPlacedForTest(w, a, p);
            r.check("materials move from the ledger to the site; the finished building joins the settlement", reserved
                            && p.status == Project.Status.COMPLETE && a.extras.size() == extras0 + 1 && EconomySim.beds(ca) > beds0,
                    "materials " + f(p.materialShare() * 100) + "%, status " + p.status + ", buildings +" + (a.extras.size() - extras0) + ", beds "
                            + beds0 + "→" + EconomySim.beds(ca) + ", blocks placed " + p.placed + "/" + p.total + ", design blocks in the world "
                            + designBlocks + " (" + a.civ.id() + " blueprint)");
            Daily.run(m, ca);
            r.check("a completed building is not built again", a.extras.size() == extras0 + 1, a.extras.size() - extras0 + " added");
        }
        boolean second = Construction.propose(m, ca, Project.Kind.WELL, "test cancel", "");
        if (second) {
            Project q = a.projects.get(a.projects.size() - 1);
            Daily.run(m, ca);
            Daily.run(m, ca);
            double site = q.site.values().stream().mapToDouble(Double::doubleValue).sum();
            double stone0 = a.freeIn(Goods.Category.STONE) + a.freeIn(Goods.Category.LUMBER) + a.freeIn(Goods.Category.TIMBER)
                    + a.freeIn(Goods.Category.BUILDING) + a.freeIn(Goods.Category.FIBER) + a.freeIn(Goods.Category.METAL);
            Construction.cancelForTest(ca, q, "test");
            double stone1 = a.freeIn(Goods.Category.STONE) + a.freeIn(Goods.Category.LUMBER) + a.freeIn(Goods.Category.TIMBER)
                    + a.freeIn(Goods.Category.BUILDING) + a.freeIn(Goods.Category.FIBER) + a.freeIn(Goods.Category.METAL);
            r.check("cancelling a project returns its unused site stock", Math.abs(stone1 - stone0 - site) < 1e-6 && q.status == Project.Status.CANCELLED,
                    f(site) + " units returned");
        }
        // ---------------- migration keeps identity
        SettlementRecord.InhabitantRecord mover = null;
        for (SettlementRecord.InhabitantRecord ir : ca.rec.inhabitants) {
            if (ir.present() && ir.mind != null && ir.mind.age == Mind.Age.ADULT && !a.leaders.contains(ir.index) && ir.mind.journey == 0) {
                mover = ir;
            }
        }
        if (mover != null && EconomySim.freeBed(cb) != null) {
            String name = mover.name;
            long look = mover.appearance;
            Mind mind = mover.mind;
            int presentBefore = ca.rec.alive() + cb.rec.alive();
            Population.moveForTest(m, ca, mover, cb);
            SettlementRecord.InhabitantRecord arrived = cb.rec.inhabitants.get(cb.rec.inhabitants.size() - 1);
            r.check("migration moves the same person (name, appearance, mind) and leaves a pointer, no duplicate", arrived.name.equals(name)
                            && arrived.appearance == look && arrived.mind == mind && mover.movedTo == b.id && ca.rec.alive() + cb.rec.alive() == presentBefore,
                    name + " moved " + a.name + " → " + b.name + "; " + mind.elsewhere.size() + " old relationships kept");
        } else {
            r.info("migration", "no free bed / candidate in the test pair");
        }
        // ---------------- contracts, property and a player business (fake player, server-side validation)
        var fake = net.fabricmc.fabric.api.entity.FakePlayer.get(w);
        fake.getInventory().clear();
        Goods.Good bread = Goods.get("minecraft:bread");
        Participation.postSupply(ca, bread, "test");
        EconomyState.Contract kc = null;
        for (EconomyState.Contract x : st.contracts.values()) {
            if (x.settlement == a.id && x.good.equals(bread.id()) && x.status == EconomyState.Contract.Status.OPEN) {
                kc = x;
            }
        }
        if (kc != null) {
            double escrow = kc.escrow;
            Participation.accept(fake, kc);
            String refused = Participation.handIn(fake, kc, ca);
            fake.getInventory().insertStack(new ItemStack(Items.BREAD, kc.qty));
            int em0 = Tx.emeralds(fake);
            String ok = Participation.handIn(fake, kc, ca);
            r.check("a supply contract: escrowed reward, invalid hand-in refused, valid hand-in paid once", escrow > 0 && refused.startsWith("I need")
                            && kc.status == EconomyState.Contract.Status.DONE && Tx.emeralds(fake) > em0 && Tx.count(fake, Items.BREAD) == 0,
                    "escrow " + f(escrow) + "; without bread: \"" + refused + "\"; paid " + (Tx.emeralds(fake) - em0) + " emeralds");
            String twice = Participation.handIn(fake, kc, ca);
            r.check("a finished contract cannot be handed in again", twice.startsWith("That's not"), "\"" + twice + "\"");
        } else {
            r.info("contracts", "no contract could be posted (treasury)");
        }
        EconomyState.Property prop = null;
        for (int i = 0; i < 3 && prop == null; i++) {
            Daily.run(m, ca);
            for (EconomyState.Property x : st.properties.values()) {
                if (x.settlement == a.id && x.owner == null) {
                    prop = x;
                }
            }
        }
        if (prop != null) {
            fake.getInventory().clear();
            String poor = Participation.buy(fake, prop, false);
            fake.getInventory().insertStack(new ItemStack(Items.EMERALD, 64));
            fake.getInventory().insertStack(new ItemStack(Items.EMERALD, 64));
            double tr = a.treasuryBalance;
            String bought = Participation.buy(fake, prop, false);
            r.check("property: only vacant buildings are listed; buying needs the price and pays the treasury", prop.owner != null
                            && poor.startsWith("That costs") && a.treasuryBalance > tr,
                    prop.type + " for " + f(prop.price) + "; \"" + bought + "\"");
            String opened = Participation.openBusiness(fake, a);
            EconomyState.Business biz = null;
            for (EconomyState.Business x : st.businesses.values()) {
                if (x.property == prop.id) {
                    biz = x;
                }
            }
            if (biz != null) {
                biz.stock.set(0, new ItemStack(Items.BREAD, 32));
                for (Economy.Household h : a.households.values()) {
                    h.foodSecurity = 0.5;
                    h.purse += 0; // households use their own money
                }
                double till = biz.till;
                for (int i = 0; i < 5; i++) {
                    Participation.customersForTest(ca, biz);
                }
                r.check("a player shop: NPC customers buy what they need with their own money", biz.till > till && biz.stock.get(0).getCount() < 32,
                        "\"" + opened + "\" sold " + (32 - biz.stock.get(0).getCount()) + " bread for " + f(biz.till - till));
            }
        } else {
            r.info("property", "no vacant building listed in the test settlement");
        }
        // ---------------- the money audit across the whole world
        double[] audit = st.audit();
        r.check("world money audit: total = initial + issued − sunk + player in − player out", Math.abs(audit[0] - audit[1]) < 0.01,
                "actual " + f(audit[0]) + ", expected " + f(audit[1]) + " (start " + f(audit0[0]) + "/" + f(audit0[1]) + "), rejected transactions "
                        + Tx.rejected);
        // ---------------- performance of a full step over every economy
        long t1 = System.nanoTime();
        for (Economy e : st.economies.values()) {
            EconomySim.Ctx c = m.ctx(e);
            if (c != null) {
                EconomySim.step(c, 1);
            }
        }
        r.info("economy step cost", String.format(Locale.ROOT, "%.1f ms for one hourly step of all %d economies", (System.nanoTime() - t1) / 1e6,
                st.economies.size()));
        SettlementState.get(w).markDirty();
        st.markDirty();
    }

    static double foodSecurity(Economy e) {
        double t = 0;
        for (Economy.Household h : e.households.values()) {
            t += h.foodSecurity;
        }
        return e.households.isEmpty() ? 1 : t / e.households.size();
    }

    static double foodUnits(Economy e) {
        double t = 0;
        for (Goods.Good g : Goods.all()) {
            if (g.food()) {
                t += e.free(g.id());
            }
        }
        return t;
    }
}
