package dev.underdeep.civ.econ;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.underdeep.civ.arch.Blueprint;
import dev.underdeep.civ.arch.BuildingKind;
import dev.underdeep.civ.mind.Memory;
import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.npc.SettlementRecord;
import dev.underdeep.civ.npc.SettlementState;
import dev.underdeep.civ.settlement.SettlementPlan;
import dev.underdeep.config.UnderdeepConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Player participation (Stage 6): contracts posted by settlements for real needs (rewards escrowed from their
 * treasury when posted, so nothing is created from nothing), property that is actually vacant, player shops whose
 * NPC customers are the settlement's households spending their own money, hired shopkeepers, and the permission
 * model (owner + members, UUID based) protecting containers and blocks inside owned property.
 */
public final class Participation {
    private Participation() {
    }

    public static void init() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !UnderdeepConfig.get().propertyProtection || !(world instanceof ServerWorld sw)) {
                return ActionResult.PASS;
            }
            BlockPos p = hit.getBlockPos();
            EconomyState.Property pr = propertyAt(sw, p);
            if (pr != null && pr.owner != null && !pr.allowed(player.getUuid()) && !player.isCreative()
                    && (sw.getBlockEntity(p) instanceof Inventory || sw.getBlockState(p).getBlock() instanceof net.minecraft.block.DoorBlock
                    || sw.getBlockState(p).getBlock() instanceof net.minecraft.block.BedBlock)) {
                player.sendMessage(Text.translatable("message.underdeep.property.private").formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!(world instanceof ServerWorld sw) || !UnderdeepConfig.get().propertyProtection || player.isCreative()) {
                return true;
            }
            EconomyState.Property pr = propertyAt(sw, pos);
            if (pr != null && pr.owner != null && !pr.allowed(player.getUuid())) {
                player.sendMessage(Text.translatable("message.underdeep.property.private").formatted(Formatting.RED), true);
                return false;
            }
            return true;
        });
        CommandRegistrationCallback.EVENT.register((d, access, env) -> {
            d.register(CommandManager.literal("property")
                    .executes(c -> property(c.getSource(), "list", ""))
                    .then(CommandManager.argument("action", StringArgumentType.word())
                            .suggests((c, b) -> {
                                for (String s : new String[]{"list", "share", "unshare", "abandon"}) {
                                    b.suggest(s);
                                }
                                return b.buildFuture();
                            })
                            .executes(c -> property(c.getSource(), StringArgumentType.getString(c, "action"), ""))
                            .then(CommandManager.argument("player", StringArgumentType.word())
                                    .executes(c -> property(c.getSource(), StringArgumentType.getString(c, "action"),
                                            StringArgumentType.getString(c, "player"))))));
            d.register(CommandManager.literal("business")
                    .executes(c -> business(c.getSource(), "status", ""))
                    .then(CommandManager.argument("action", StringArgumentType.word())
                            .suggests((c, b) -> {
                                for (String s : new String[]{"status", "stock", "collect", "price"}) {
                                    b.suggest(s);
                                }
                                return b.buildFuture();
                            })
                            .executes(c -> business(c.getSource(), StringArgumentType.getString(c, "action"), ""))
                            .then(CommandManager.argument("value", StringArgumentType.word())
                                    .executes(c -> business(c.getSource(), StringArgumentType.getString(c, "action"),
                                            StringArgumentType.getString(c, "value"))))));
        });
    }

    @Nullable
    static EconomyState.Property propertyAt(ServerWorld w, BlockPos p) {
        if (w.getRegistryKey() != net.minecraft.world.World.OVERWORLD) {
            return null;
        }
        for (EconomyState.Property pr : EconomyState.get(w).properties.values()) {
            if (pr.contains(p.getX(), p.getY(), p.getZ())) {
                return pr;
            }
        }
        return null;
    }

    // ================================================================== daily

    public static void daily(EconomyManager mgr, EconomySim.Ctx c) {
        EconomyState st = c.state;
        for (EconomyState.Contract k : new ArrayList<>(st.contracts.values())) {
            if (k.settlement != c.e.id) {
                continue;
            }
            if ((k.status == EconomyState.Contract.Status.OPEN || k.status == EconomyState.Contract.Status.TAKEN) && c.now > k.expires) {
                expire(c, k);
            }
            if ((k.status == EconomyState.Contract.Status.DONE || k.status == EconomyState.Contract.Status.EXPIRED) && k.escrow <= 0
                    && c.now - k.expires > 24000L * 3) {
                st.contracts.remove(k.id);
            }
        }
        listProperties(c);
        for (EconomyState.Property p : st.properties.values()) {
            if (p.settlement == c.e.id && p.owner != null && p.rentUntil > 0 && p.rentUntil < c.now) {
                p.owner = null;
                p.members.clear();
                p.rentUntil = 0;
                for (EconomyState.Business b : st.businesses.values()) {
                    if (b.property == p.id) {
                        b.history.addFirst("day " + c.now / 24000 + ": the rent ran out; the shop is closed");
                    }
                }
            }
        }
        for (EconomyState.Business b : st.businesses.values()) {
            if (b.settlement == c.e.id) {
                customers(c, b);
                payEmployee(c, b);
            }
        }
    }

    static void expire(EconomySim.Ctx c, EconomyState.Contract k) {
        Economy payer = c.state.economies.get(k.type == EconomyState.Contract.Type.COURIER ? k.target : k.settlement);
        if (payer != null && k.escrow > 0) {
            Tx.pay(k, payer.treasury, k.escrow, c.now, "contract expired");
        }
        if (k.deposit > 0) {
            // a courier who never delivered forfeits the deposit to the settlement whose goods they took
            c.e.treasuryBalance += k.deposit;
            c.e.playerIn += k.deposit;
            k.deposit = 0;
        }
        k.status = EconomyState.Contract.Status.EXPIRED;
    }

    static long openContracts(EconomySim.Ctx c) {
        return c.state.contracts.values().stream().filter(k -> k.settlement == c.e.id && k.status == EconomyState.Contract.Status.OPEN).count();
    }

    static int issuer(EconomySim.Ctx c) {
        return c.e.leaders.isEmpty() ? -1 : c.e.leaders.get(0);
    }

    /** A supply contract for a real shortage; the reward (a premium over the local price) is escrowed now. */
    public static void postSupply(EconomySim.Ctx c, Goods.Good g, String reason) {
        if (openContracts(c) >= 3) {
            return;
        }
        for (EconomyState.Contract k : c.state.contracts.values()) {
            if (k.settlement == c.e.id && k.good.equals(g.id()) && k.status != EconomyState.Contract.Status.EXPIRED
                    && k.status != EconomyState.Contract.Status.DONE) {
                return;
            }
        }
        Economy.Stock s = c.e.stock(g.id());
        int qty = (int) Math.max(4, Math.min(32, s.desired * 1.5 - s.free()));
        double reward = Math.ceil(qty * s.price * 1.25);
        EconomyState.Contract k = new EconomyState.Contract();
        k.id = c.state.newId();
        if (!Tx.pay(c.e.treasury, k, reward, c.now, "contract escrow")) {
            return;
        }
        k.settlement = c.e.id;
        k.type = EconomyState.Contract.Type.SUPPLY;
        k.good = g.id();
        k.qty = qty;
        k.reward = reward;
        k.posted = c.now;
        k.expires = c.now + 24000L * 3;
        k.issuer = issuer(c);
        k.reason = reason;
        c.state.contracts.put(k.id, k);
        Daily.event(c, "CONTRACT_POSTED", "c:" + k.id, c.e.name + " is paying for " + qty + " " + g.name(), 24000L * 3);
    }

    public static boolean hasBuildContract(EconomySim.Ctx c, Project p) {
        for (EconomyState.Contract k : c.state.contracts.values()) {
            if (k.settlement == c.e.id && k.project == p.id && k.status != EconomyState.Contract.Status.EXPIRED
                    && k.status != EconomyState.Contract.Status.DONE) {
                return true;
            }
        }
        return false;
    }

    /** Construction materials the settlement cannot find: a contract for the most lacking category. */
    public static void postBuild(EconomySim.Ctx c, Project p) {
        String cat = null;
        double lack = 0;
        for (var en : p.required.entrySet()) {
            double l = en.getValue() - p.delivered.getOrDefault(en.getKey(), 0.0);
            if (l > lack) {
                lack = l;
                cat = en.getKey();
            }
        }
        if (cat == null || openContracts(c) >= 3) {
            return;
        }
        List<Goods.Good> gs = new ArrayList<>(Goods.in(Goods.Category.valueOf(cat.toUpperCase(Locale.ROOT))));
        if (gs.isEmpty()) {
            return;
        }
        gs.sort((a, b) -> Double.compare(a.value(), b.value()));
        Goods.Good g = gs.get(0);
        int qty = (int) Math.min(64, Math.ceil(lack));
        double reward = Math.ceil(qty * c.e.stock(g.id()).price * 1.3);
        EconomyState.Contract k = new EconomyState.Contract();
        k.id = c.state.newId();
        if (!Tx.pay(c.e.treasury, k, reward, c.now, "contract escrow")) {
            return;
        }
        k.settlement = c.e.id;
        k.type = EconomyState.Contract.Type.BUILD;
        k.good = g.id();
        k.qty = qty;
        k.reward = reward;
        k.posted = c.now;
        k.expires = c.now + 24000L * 4;
        k.project = p.id;
        k.issuer = issuer(c);
        k.reason = "materials for the new " + Construction.label(p.kind);
        c.state.contracts.put(k.id, k);
        Daily.event(c, "CONTRACT_POSTED", "c:" + k.id, c.e.name + " needs " + qty + " " + g.name() + " for the new " + Construction.label(p.kind),
                24000L * 3);
    }

    /** An escort contract for a caravan on a dangerous route (reward escrowed from the origin's treasury). */
    static void postEscort(EconomySim.Ctx c, Shipment s, Economy dest) {
        if (openContracts(c) >= 3) {
            return;
        }
        double reward = Math.ceil(3 + s.length / 300.0);
        EconomyState.Contract k = new EconomyState.Contract();
        k.id = c.state.newId();
        if (!Tx.pay(c.e.treasury, k, reward, c.now, "contract escrow")) {
            return;
        }
        k.settlement = c.e.id;
        k.type = EconomyState.Contract.Type.ESCORT;
        k.qty = 0;
        k.reward = reward;
        k.posted = c.now;
        k.expires = c.now + 24000L * 2;
        k.target = s.id;
        k.issuer = issuer(c);
        k.reason = "the caravan to " + dest.name + " takes a dangerous route";
        c.state.contracts.put(k.id, k);
    }

    /** When a shipment arrives, escorts who travelled with it are paid (if online; otherwise when they next visit). */
    static void escortsDone(EconomyManager mgr, EconomyState st, Shipment s, long now) {
        for (EconomyState.Contract k : st.contracts.values()) {
            if (k.type == EconomyState.Contract.Type.ESCORT && k.target == s.id && k.status == EconomyState.Contract.Status.TAKEN && k.taker != null) {
                int ticks = s.escort.getOrDefault(k.taker, 0);
                if (ticks >= 600) {
                    k.status = EconomyState.Contract.Status.DONE;
                    ServerPlayerEntity p = mgr.world.getServer().getPlayerManager().getPlayer(k.taker);
                    if (p != null) {
                        payout(p, k, now);
                    }
                } else {
                    k.expires = now; // did not really travel with it
                }
            }
        }
    }

    /** Pays a completed contract's escrow to the player as emeralds (whole emeralds; the remainder returns). */
    static void payout(ServerPlayerEntity p, EconomyState.Contract k, long now) {
        int n = (int) Math.floor(k.escrow + 1e-9);
        if (n > 0) {
            k.escrow -= n;
            Tx.giveEmeralds(p, n);
            Tx.log(now, "contract reward", k.label(), p.getName().getString(), n, k.type.name());
        }
        Economy home = EconomyState.get(p.getServerWorld()).economies.get(k.settlement);
        if (home != null) {
            home.playerOut += n;
            if (k.escrow > 0) {
                home.treasuryBalance += k.escrow;
                k.escrow = 0;
            }
        }
        p.sendMessage(Text.translatable("message.underdeep.contract.paid", n).formatted(Formatting.GREEN), false);
    }

    // ================================================================== property

    /** Lists actually vacant buildings (no present resident or keeper) for sale; at most three per settlement. */
    static void listProperties(EconomySim.Ctx c) {
        long listed = c.state.properties.values().stream().filter(p -> p.settlement == c.e.id).count();
        if (listed >= 3) {
            return;
        }
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (SettlementRecord.InhabitantRecord ir : c.rec.inhabitants) {
            if (ir.present()) {
                used.add(ir.home);
                used.add(ir.workplace);
            }
        }
        for (EconomyState.Property p : c.state.properties.values()) {
            if (p.settlement == c.e.id) {
                used.add(p.building);
            }
        }
        for (SettlementPlan.Building b : c.plan.buildings()) {
            if (used.contains(b.index())) {
                continue;
            }
            String type = switch (b.kind()) {
                case HOUSE, HOUSE_SMALL, HOUSE_LARGE, TENT -> "home";
                case SHOP, STALL, WORKSHOP -> "shop";
                default -> null;
            };
            if (type == null) {
                continue;
            }
            boolean hasBeds = false;
            for (Blueprint.Marker m : b.blueprint().markers()) {
                hasBeds |= m.type() == Blueprint.MarkerType.BED;
            }
            if (type.equals("home") && !hasBeds) {
                continue;
            }
            EconomyState.Property p = new EconomyState.Property();
            p.id = c.state.newId();
            p.settlement = c.e.id;
            p.building = b.index();
            p.type = type;
            p.x0 = b.box().getMinX();
            p.y0 = b.origin().getY() - 2;
            p.z0 = b.box().getMinZ();
            p.x1 = b.box().getMaxX();
            p.y1 = b.box().getMaxY();
            p.z1 = b.box().getMaxZ();
            double base = switch (b.kind()) {
                case HOUSE_LARGE -> 80;
                case HOUSE -> 50;
                case WORKSHOP, SHOP -> 60;
                case STALL -> 25;
                default -> 35;
            };
            p.price = Math.round(base * (0.8 + c.e.score * 0.5));
            c.state.properties.put(p.id, p);
            Daily.event(c, "PROPERTY_FOR_SALE", "pr:" + p.id, "a " + b.kind().id().replace('_', ' ') + " in " + c.e.name + " is for sale", 24000L * 3);
            return;
        }
    }

    static void propertyOptions(EconomySim.Ctx c, List<Daily.Option> opts) {
        // listing is handled daily; nothing for the settlement to decide beyond it
    }

    /** Buys (or rents for seven days at a fifth of the price) a listed property; emeralds go to the treasury. */
    public static String buy(ServerPlayerEntity p, EconomyState.Property pr, boolean rent) {
        EconomyState st = EconomyState.get(p.getServerWorld());
        if (pr.owner != null) {
            return "That has already been taken.";
        }
        int cost = (int) Math.ceil(rent ? pr.price / 5.0 : pr.price);
        if (!Tx.takeEmeralds(p, cost)) {
            return "That costs " + cost + " emeralds. You don't have enough.";
        }
        Economy e = st.economies.get(pr.settlement);
        if (e != null) {
            e.treasuryBalance += cost;
            e.playerIn += cost;
        }
        pr.owner = p.getUuid();
        pr.rentUntil = rent ? p.getServerWorld().getTime() + 24000L * 7 : 0;
        st.markDirty();
        Tx.log(p.getServerWorld().getTime(), rent ? "rent" : "property", p.getName().getString(), "treasury", cost, "property " + pr.id);
        return rent ? "It's yours for the next seven days. Use /property share to let friends in." : "It's yours. Use /property share to let friends in.";
    }

    static int property(ServerCommandSource src, String action, String arg) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            return 0;
        }
        EconomyState st = EconomyState.get(p.getServer().getOverworld());
        List<EconomyState.Property> mine = new ArrayList<>();
        for (EconomyState.Property pr : st.properties.values()) {
            if (p.getUuid().equals(pr.owner)) {
                mine.add(pr);
            }
        }
        if (action.equals("list")) {
            if (mine.isEmpty()) {
                src.sendFeedback(() -> Text.translatable("message.underdeep.property.none").formatted(Formatting.GRAY), false);
            }
            for (EconomyState.Property pr : mine) {
                Economy e = st.economies.get(pr.settlement);
                String line = pr.type + " in " + (e == null ? "?" : e.name) + " at " + (pr.x0 + pr.x1) / 2 + ", " + (pr.z0 + pr.z1) / 2
                        + (pr.rentUntil > 0 ? " (rented, " + Math.max(0, (pr.rentUntil - p.getServerWorld().getTime()) / 24000) + " days left)" : " (owned)")
                        + (pr.members.isEmpty() ? "" : ", shared with " + pr.members.size());
                src.sendFeedback(() -> Text.literal(line), false);
            }
            return 1;
        }
        EconomyState.Property here = propertyAt(p.getServerWorld(), p.getBlockPos());
        if (here == null || !p.getUuid().equals(here.owner)) {
            src.sendError(Text.translatable("message.underdeep.property.stand"));
            return 0;
        }
        switch (action) {
            case "share", "unshare" -> {
                ServerPlayerEntity other = p.getServer().getPlayerManager().getPlayer(arg);
                if (other == null) {
                    src.sendError(Text.literal("No player named " + arg + " is online."));
                    return 0;
                }
                if (action.equals("share")) {
                    here.members.add(other.getUuid());
                } else {
                    here.members.remove(other.getUuid());
                }
                src.sendFeedback(() -> Text.literal((action.equals("share") ? "Shared with " : "No longer shared with ") + other.getName().getString()), false);
            }
            case "abandon" -> {
                here.owner = null;
                here.members.clear();
                here.rentUntil = 0;
                src.sendFeedback(() -> Text.literal("You gave up the property. (No refund.)"), false);
            }
            default -> {
                return 0;
            }
        }
        st.markDirty();
        return 1;
    }

    // ================================================================== businesses

    @Nullable
    static EconomyState.Business businessOf(EconomyState st, UUID owner, long settlement) {
        for (EconomyState.Business b : st.businesses.values()) {
            if (b.owner.equals(owner) && b.settlement == settlement) {
                return b;
            }
        }
        return null;
    }

    /** Opens a shop in a property the player owns in this settlement. */
    public static String openBusiness(ServerPlayerEntity p, Economy e) {
        EconomyState st = EconomyState.get(p.getServerWorld());
        long count = st.businesses.values().stream().filter(b -> b.owner.equals(p.getUuid())).count();
        if (count >= UnderdeepConfig.get().maxBusinessesPerPlayer) {
            return "You already run as many shops as you can manage.";
        }
        EconomyState.Property prop = null;
        for (EconomyState.Property pr : st.properties.values()) {
            if (pr.settlement == e.id && p.getUuid().equals(pr.owner)) {
                boolean used = st.businesses.values().stream().anyMatch(b -> b.property == pr.id);
                if (!used) {
                    prop = pr;
                    break;
                }
            }
        }
        if (prop == null) {
            return "You'll need a property of your own in " + e.name + " first.";
        }
        EconomyState.Business b = new EconomyState.Business();
        b.id = st.newId();
        b.property = prop.id;
        b.settlement = e.id;
        b.owner = p.getUuid();
        b.name = p.getName().getString() + "'s shop";
        while (b.stock.size() < 27) {
            b.stock.add(ItemStack.EMPTY);
        }
        b.lastPaid = p.getServerWorld().getTime();
        st.businesses.put(b.id, b);
        st.markDirty();
        e.event("BUSINESS_OPENED", p.getServerWorld().getTime(), p.getUuid().toString(), p.getName().getString() + " opened a shop in " + e.name,
                24000L * 3);
        return "Your shop is registered. Stock it with /business stock while standing in your property; people here will buy what they need.";
    }

    /** NPC customers: households buy what they need from the shop with their own money, if the price is fair. */
    static void customers(EconomySim.Ctx c, EconomyState.Business b) {
        EconomyState.Property pr = c.state.properties.get(b.property);
        if (pr == null || !b.owner.equals(pr.owner)) {
            return;
        }
        double capture = b.employee >= 0 ? 0.5 : 0.3;
        for (Economy.Household h : c.e.households.values()) {
            if (c.rnd.nextDouble() > capture) {
                continue;
            }
            for (int i = 0; i < b.stock.size(); i++) {
                ItemStack s = b.stock.get(i);
                Goods.Good g = Goods.of(s);
                if (g == null || s.isDamaged()) {
                    continue;
                }
                boolean needed = g.food() && h.foodSecurity < 0.98 || g.category() == Goods.Category.FUEL && c.e.freeIn(Goods.Category.FUEL) < 8
                        || g.category() == Goods.Category.MEDICINE || g.category() == Goods.Category.TOOLS && c.e.freeIn(Goods.Category.TOOLS) < 1
                        || c.e.free(g.id()) < c.e.stock(g.id()).desired * 0.5;
                if (!needed) {
                    continue;
                }
                double local = c.e.stock(g.id()).price;
                double price = local * b.priceFactor();
                if (price > local * 1.12 && c.e.free(g.id()) > 1) {
                    continue; // cheaper at the market
                }
                int units = Math.min(s.getCount(), g.food() ? 3 : 1);
                units = Math.min(units, (int) Math.floor(h.purse / Math.max(0.01, price)));
                if (units <= 0) {
                    continue;
                }
                double pay = units * price;
                if (Tx.pay(h, b, pay, c.now, "shop sale")) {
                    s.decrement(units);
                    b.sales += units;
                    b.revenue += pay;
                    c.e.playerIn += pay;
                    if (g.food()) {
                        h.foodSecurity = Math.min(1, h.foodSecurity + units * g.nutrition() / 40.0);
                    }
                    b.history.addFirst("day " + c.now / 24000 + ": sold " + units + " " + g.name() + String.format(Locale.ROOT, " for %.1f", pay));
                    while (b.history.size() > 12) {
                        b.history.removeLast();
                    }
                }
                break;
            }
        }
    }

    static void payEmployee(EconomySim.Ctx c, EconomyState.Business b) {
        if (b.employee < 0 || b.employee >= c.rec.inhabitants.size()) {
            return;
        }
        SettlementRecord.InhabitantRecord ir = c.rec.inhabitants.get(b.employee);
        if (!ir.present() || ir.mind == null || !b.owner.equals(ir.mind.employer)) {
            b.employee = -1;
            return;
        }
        Economy.Household h = c.e.households.get(ir.home);
        if (h != null && Tx.pay(b, h, b.wage, c.now, "shop wage")) {
            b.unpaidDays = 0;
            c.e.playerOut -= b.wage; // stays inside the settlement economy
        } else if (++b.unpaidDays >= 2) {
            ir.mind.employer = null;
            ir.mind.memories.remember(Memory.Type.TRUST_BROKEN, "p:" + b.owner, "unpaid", c.now, c.cfg.inhabitantMemoryLimit, 1.2);
            ir.mind.bond(b.owner).adjust(c.now, 0, -12, -6, -5, 10, 0, false);
            b.employee = -1;
            b.history.addFirst("day " + c.now / 24000 + ": " + ir.name + " quit (unpaid)");
        }
    }

    /** Hiring: an adult who knows and likes the player, and is not tied to a workplace, may agree to keep the shop. */
    public static String hire(ServerPlayerEntity p, SettlementRecord rec, SettlementRecord.InhabitantRecord ir) {
        EconomyState st = EconomyState.get(p.getServerWorld());
        EconomyState.Business b = businessOf(st, p.getUuid(), rec.id);
        if (b == null) {
            return "You don't have a shop here.";
        }
        if (b.employee >= 0) {
            return "You already have someone keeping your shop.";
        }
        Mind m = ir.mind;
        if (m == null || m.age != Mind.Age.ADULT || m.companionOf != null || m.journey != 0 || m.employer != null) {
            return "I can't take that on right now.";
        }
        var bond = m.bondIfAny(p.getUuid());
        if (bond == null || bond.trust < 15 || bond.affection < 10) {
            return "I don't know you well enough to work for you.";
        }
        if (ir.workplace >= 0 && m.skill > 55 && !ir.profession.equals("merchant")) {
            return "I'm needed at my own work, sorry.";
        }
        m.employer = p.getUuid();
        b.employee = ir.index;
        b.lastPaid = p.getServerWorld().getTime();
        st.markDirty();
        return "Alright, I'll keep your shop. " + String.format(Locale.ROOT, "%.1f", b.wage) + " emeralds a day, from the till. Don't let it run dry.";
    }

    static int business(ServerCommandSource src, String action, String arg) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            return 0;
        }
        EconomyState st = EconomyState.get(p.getServer().getOverworld());
        EconomyState.Property here = propertyAt(p.getServerWorld(), p.getBlockPos());
        EconomyState.Business b = null;
        for (EconomyState.Business x : st.businesses.values()) {
            if (x.owner.equals(p.getUuid()) && (here == null || x.property == here.id)) {
                b = x;
                if (here != null) {
                    break;
                }
            }
        }
        if (b == null) {
            src.sendError(Text.translatable("message.underdeep.business.none"));
            return 0;
        }
        EconomyState.Business biz = b;
        switch (action) {
            case "stock" -> {
                if (here == null || here.id != biz.property) {
                    src.sendError(Text.translatable("message.underdeep.business.stand"));
                    return 0;
                }
                openStock(p, biz, here);
            }
            case "collect" -> {
                int n = (int) Math.floor(biz.till);
                if (n > 0) {
                    biz.till -= n;
                    Tx.giveEmeralds(p, n);
                    Economy e = st.economies.get(biz.settlement);
                    if (e != null) {
                        e.playerOut += n;
                    }
                }
                src.sendFeedback(() -> Text.literal("Collected " + n + " emeralds."), false);
            }
            case "price" -> {
                biz.priceMode = arg.equals("cheap") ? 0 : arg.equals("premium") ? 2 : 1;
                src.sendFeedback(() -> Text.literal("Prices set to " + (biz.priceMode == 0 ? "cheap (−10%)" : biz.priceMode == 2 ? "premium (+15%)"
                        : "fair (local market price)")), false);
            }
            default -> {
                int items = biz.stock.stream().mapToInt(ItemStack::getCount).sum();
                src.sendFeedback(() -> Text.literal(biz.name + ": " + items + " items in stock, till " + String.format(Locale.ROOT, "%.1f", biz.till)
                        + " emeralds, " + biz.sales + " items sold, keeper " + (biz.employee >= 0 ? "#" + biz.employee : "none")), false);
                for (String h : biz.history.stream().limit(5).toList()) {
                    src.sendFeedback(() -> Text.literal(" " + h).formatted(Formatting.GRAY), false);
                }
            }
        }
        st.markDirty();
        return 1;
    }

    /** The shop's stock inventory (the record's own list: no copy, no duplication), owner/member only, in the property. */
    static void openStock(ServerPlayerEntity p, EconomyState.Business b, EconomyState.Property pr) {
        while (b.stock.size() < 27) {
            b.stock.add(ItemStack.EMPTY);
        }
        Inventory inv = new Inventory() {
            @Override
            public int size() {
                return 27;
            }

            @Override
            public boolean isEmpty() {
                return b.stock.stream().allMatch(ItemStack::isEmpty);
            }

            @Override
            public ItemStack getStack(int slot) {
                return b.stock.get(slot);
            }

            @Override
            public ItemStack removeStack(int slot, int amount) {
                return Inventories.splitStack(b.stock, slot, amount);
            }

            @Override
            public ItemStack removeStack(int slot) {
                return Inventories.removeStack(b.stock, slot);
            }

            @Override
            public void setStack(int slot, ItemStack stack) {
                b.stock.set(slot, stack);
            }

            @Override
            public void markDirty() {
                EconomyState.get(p.getServerWorld()).markDirty();
            }

            @Override
            public boolean canPlayerUse(PlayerEntity player) {
                return pr.allowed(player.getUuid()) && pr.contains(player.getBlockX(), player.getBlockY(), player.getBlockZ());
            }

            @Override
            public void clear() {
                for (int i = 0; i < 27; i++) {
                    b.stock.set(i, ItemStack.EMPTY);
                }
            }
        };
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, pinv, pl) -> new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, id,
                pinv, inv, 3), Text.literal(b.name)));
    }

    // ================================================================== contracts with players

    public static List<EconomyState.Contract> open(EconomyState st, long settlement) {
        List<EconomyState.Contract> out = new ArrayList<>();
        for (EconomyState.Contract k : st.contracts.values()) {
            if (k.settlement == settlement && k.status == EconomyState.Contract.Status.OPEN) {
                out.add(k);
            }
        }
        return out;
    }

    public static String describe(EconomyState st, EconomyState.Contract k) {
        Goods.Good g = Goods.get(k.good);
        return switch (k.type) {
            case SUPPLY, BUILD -> "Bring " + k.qty + " " + (g == null ? k.good : g.name()) + " (" + (int) Math.floor(k.reward) + " emeralds)";
            case ESCORT -> "Escort the caravan (" + (int) Math.floor(k.reward) + " emeralds)";
            case COURIER -> "Carry goods to " + (st.economies.get(k.target) == null ? "a neighbour" : st.economies.get(k.target).name);
        };
    }

    public static String accept(ServerPlayerEntity p, EconomyState.Contract k) {
        EconomyState st = EconomyState.get(p.getServerWorld());
        long mine = st.contracts.values().stream().filter(x -> p.getUuid().equals(x.taker) && x.status == EconomyState.Contract.Status.TAKEN).count();
        if (mine >= UnderdeepConfig.get().maxContractsPerPlayer) {
            return "You've taken on enough work for now.";
        }
        if (k.status != EconomyState.Contract.Status.OPEN) {
            return "Someone else is already doing that.";
        }
        k.status = EconomyState.Contract.Status.TAKEN;
        k.taker = p.getUuid();
        st.markDirty();
        return switch (k.type) {
            case ESCORT -> "The caravan has already set out. Catch up and stay near it until it arrives.";
            default -> "Thank you. Bring it to me when you have it. " + k.reason.substring(0, 1).toUpperCase(Locale.ROOT) + k.reason.substring(1) + ".";
        };
    }

    /** Hands in goods for a taken supply/build contract: validated against the inventory, exactly once. */
    public static String handIn(ServerPlayerEntity p, EconomyState.Contract k, EconomySim.Ctx c) {
        Goods.Good g = Goods.get(k.good);
        if (g == null || k.status != EconomyState.Contract.Status.TAKEN || !p.getUuid().equals(k.taker)) {
            return "That's not a job I gave you.";
        }
        if (!Tx.takeItems(p, g.item(), k.qty)) {
            return "I need " + k.qty + " " + g.name() + ". You have " + Tx.count(p, g.item()) + ".";
        }
        k.status = EconomyState.Contract.Status.DONE;
        if (k.type == EconomyState.Contract.Type.BUILD) {
            Project pr = null;
            for (Project q : c.e.projects) {
                if (q.id == k.project) {
                    pr = q;
                }
            }
            if (pr != null && pr.active()) {
                String cat = g.category().id();
                pr.site.merge(g.id(), (double) k.qty, Double::sum);
                pr.delivered.merge(cat, (double) k.qty, Double::sum);
            } else {
                c.e.put(g.id(), k.qty);
            }
        } else {
            c.e.put(g.id(), k.qty);
            c.e.stock(g.id()).imported += k.qty;
        }
        payout(p, k, c.now);
        // the community remembers
        if (k.issuer >= 0 && k.issuer < c.rec.inhabitants.size() && c.rec.inhabitants.get(k.issuer).mind != null) {
            Mind m = c.rec.inhabitants.get(k.issuer).mind;
            m.memories.remember(Memory.Type.HELPED_BY, "p:" + p.getUuid(), g.id(), c.now, c.cfg.inhabitantMemoryLimit, 1.1);
            m.bond(p.getUuid()).adjust(c.now, 4, 6, 4, 4, -5, 0, false);
            m.learn(Mind.InfoType.PLAYER_HELPED, "p:" + p.getUuid(), p.getName().getString(), c.now, Mind.Source.WITNESSED, 100,
                    c.cfg.inhabitantKnowledgeLimit);
        }
        Daily.event(c, "CONTRACT_COMPLETED", "p:" + p.getUuid(), p.getName().getString() + " brought " + k.qty + " " + g.name() + " to " + c.e.name,
                24000L * 2);
        SettlementState.get(c.world).markDirty();
        return "That's exactly what we needed. Here's your pay.";
    }

    /** Pays any finished contract still waiting for its taker. */
    public static void pending(ServerPlayerEntity p, long settlement) {
        for (EconomyState.Contract k : EconomyState.get(p.getServerWorld()).contracts.values()) {
            if (k.settlement == settlement && k.status == EconomyState.Contract.Status.DONE && k.escrow >= 1 && p.getUuid().equals(k.taker)) {
                payout(p, k, p.getServerWorld().getTime());
            }
        }
    }

    static boolean commercial(BuildingKind k) {
        return k == BuildingKind.SHOP || k == BuildingKind.STALL || k == BuildingKind.WORKSHOP;
    }
}
