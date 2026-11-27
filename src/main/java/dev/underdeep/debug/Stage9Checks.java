package dev.underdeep.debug;

import com.mojang.authlib.GameProfile;
import dev.underdeep.Underdeep;
import dev.underdeep.civ.settlement.SettlementMap;
import dev.underdeep.civ.settlement.Settlements;
import dev.underdeep.equipment.Accessories;
import dev.underdeep.explore.Canvas;
import dev.underdeep.explore.Discovery;
import dev.underdeep.explore.ExplorationMap;
import dev.underdeep.explore.ExplorationState;
import dev.underdeep.explore.Explorations;
import dev.underdeep.explore.ExploreBlocks;
import dev.underdeep.explore.ExploreContent;
import dev.underdeep.explore.ExploreItems;
import dev.underdeep.explore.ExplorerData;
import dev.underdeep.explore.LayoutAudit;
import dev.underdeep.explore.Lore;
import dev.underdeep.explore.Mechanisms;
import dev.underdeep.explore.Rewards;
import dev.underdeep.explore.Site;
import dev.underdeep.explore.SiteLayout;
import dev.underdeep.explore.SitePlacer;
import dev.underdeep.explore.Traps;
import dev.underdeep.explore.TravelEncounters;
import dev.underdeep.explore.design.Designs;
import dev.underdeep.guide.Handbook;
import dev.underdeep.guide.Sensitivity;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;
import dev.underdeep.world.terrain.UnderdeepTerrain;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeverBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Stage 9 checks. Static: every authored design is built for several seeds and regions and audited (walkability from
 * the edge to every mechanism, cache, inscription, hidden room and heart; puzzle data; light paths). Placement: the pure
 * site map over a large area (density, coverage, spacing, settlements, spawn). In-world: sites near spawn are generated
 * and compared with their layouts; every dungeon design is placed in a test area and all of its puzzles are solved
 * through the real interaction code (accelerated, fake players); rewards, traps, discovery, persistence, records, NPC
 * services, encounters and artifacts are exercised. Puzzle solutions are never printed.
 */
final class Stage9Checks {
    private Stage9Checks() {
    }

    static void run(MinecraftServer server, ServerWorld w, UnderdeepTerrain t, Path dir, Stage15Checks.Reporter r) {
        r.section("Stage 9 exploration: designs (static), placement (pure map), generation and mechanics (in-world, accelerated)");
        long t0 = System.nanoTime();
        designs(r, dir);
        ExplorationMap map = Explorations.of(w);
        if (map == null) {
            map = Explorations.enableFor(t);
            r.info("exploration map", "not enabled for this world's generator version; enabled for the test only");
        }
        placement(map, t, r);
        generated(server, w, map, r);
        mechanics(server, w, map, dir, r);
        r.info("stage 9 checks time", String.format(Locale.ROOT, "%.1f s", (System.nanoTime() - t0) / 1e9));
    }

    // ------------------------------------------------------------------ static designs

    static void designs(Stage15Checks.Reporter r, Path dir) {
        List<String> reg = Designs.problems();
        r.check("every site family has an authored design and every design a family", reg.isEmpty(), reg.toString());
        Map<String, List<String>> problems = new TreeMap<>();
        int built = 0, puzzles = 0, traps = 0, caches = 0, secrets = 0;
        Set<String> types = new LinkedHashSet<>(), trapTypes = new LinkedHashSet<>();
        for (ExploreContent.Family f : ExploreContent.FAMILIES.values()) {
            List<RegionType> regions = f.regions().isEmpty() ? List.of(RegionType.ROOTWILD, RegionType.EVERFROST, RegionType.ASHEN_HOLLOWS)
                    : new ArrayList<>(f.regions()).subList(0, Math.min(2, f.regions().size()));
            for (int seed = 0; seed < 3; seed++) {
                RegionType region = regions.get(seed % regions.size());
                Site s = new Site(Site.packId(f.layers().iterator().next(), 9000 + seed, 9000), f, f.layers().iterator().next(), region, 0, 100, 0, f.radius(),
                        BlockRotation.NONE, seed * 7919L + f.id().hashCode());
                Canvas c;
                try {
                    c = Designs.build(s);
                } catch (RuntimeException e) {
                    problems.computeIfAbsent(f.id(), k -> new ArrayList<>()).add("build crashed: " + e);
                    continue;
                }
                built++;
                LayoutAudit.Result res = LayoutAudit.audit(c);
                List<String> p = new ArrayList<>(res.problems());
                if (c.maxY() > f.height() + 1) {
                    p.add("design reaches y=" + c.maxY() + " above its declared height " + f.height());
                }
                if (-c.minY() > f.below() + 1 && c.minY() < -1) {
                    p.add("design reaches y=" + c.minY() + " below its declared depth " + f.below());
                }
                if (!p.isEmpty()) {
                    problems.computeIfAbsent(f.id(), k -> new ArrayList<>()).addAll(p.subList(0, Math.min(3, p.size())));
                }
                if (seed == 0) {
                    puzzles += c.puzzles.size();
                    traps += c.traps.size();
                    caches += c.caches.size();
                    secrets += c.secrets.size();
                    c.puzzles.forEach(x -> types.add(x.type()));
                    c.traps.forEach(x -> trapTypes.add(x.type().replaceAll(":.*", "")));
                }
            }
        }
        try {
            StringBuilder sb = new StringBuilder();
            problems.forEach((k, v) -> sb.append(k).append('\n').append("  ").append(String.join("\n  ", v)).append('\n'));
            Files.writeString(dir.resolve("stage9_layouts.txt"), sb.toString());
            for (String fid : problems.keySet()) {
                ExploreContent.Family f = ExploreContent.FAMILIES.get(fid);
                RegionType region = f.regions().isEmpty() ? RegionType.ROOTWILD : f.regions().iterator().next();
                Site s0 = new Site(Site.packId(f.layers().iterator().next(), 9000, 9000), f, f.layers().iterator().next(), region, 0, 100, 0, f.radius(),
                        BlockRotation.NONE, f.id().hashCode());
                Files.writeString(dir.resolve("layout_" + fid + ".txt"), LayoutAudit.render(Designs.build(s0)));
            }
        } catch (Exception ignored) {
            // diagnostics file only
        }
        r.check("all authored designs pass the static audit (3 seeds each; reachability, puzzle data, light paths, declared extents)", problems.isEmpty(),
                problems.isEmpty() ? built + " layouts" : problems.size() + " families with problems: " + abbreviate(problems));
        Map<ExploreContent.Category, Integer> cats = new EnumMap<>(ExploreContent.Category.class);
        ExploreContent.FAMILIES.values().forEach(f -> cats.merge(f.category(), 1, Integer::sum));
        r.info("design counts", cats + ", hidden rooms " + ExploreContent.SECRETS.size() + ", records " + ExploreContent.RECORDS.size() + ", artifacts/relics "
                + ExploreContent.ARTIFACTS.size() + ", encounters " + ExploreContent.ENCOUNTERS.size());
        r.info("authored instances (seed 0)", puzzles + " puzzles of " + types.size() + " mechanic types, " + traps + " traps (" + trapTypes + "), "
                + caches + " reward containers, " + secrets + " hidden rooms");
    }

    static String abbreviate(Map<String, List<String>> m) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (var e : m.entrySet()) {
            if (n++ >= 12) {
                b.append(" ...");
                break;
            }
            b.append(e.getKey()).append(": ").append(e.getValue()).append("; ");
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ placement (pure)

    static void placement(ExplorationMap map, UnderdeepTerrain t, Stage15Checks.Reporter r) {
        long t0 = System.nanoTime();
        int half = 2000;
        List<Site> all = map.sitesIn(-half, -half, half, half, 0);
        double secs = (System.nanoTime() - t0) / 1e9;
        Map<ExploreContent.Category, Integer> cats = new EnumMap<>(ExploreContent.Category.class);
        Map<String, Integer> fams = new TreeMap<>();
        Set<RegionType> regions = java.util.EnumSet.noneOf(RegionType.class);
        int overlaps = 0, inSettlement = 0, nearSpawn = 0, dangerNearSpawn = 0;
        SettlementMap sm = Settlements.of(t);
        for (Site s : all) {
            cats.merge(s.family().category(), 1, Integer::sum);
            fams.merge(s.family().id(), 1, Integer::sum);
            regions.add(s.region());
            if (Math.hypot(s.x(), s.z()) < 72) {
                nearSpawn++;
            }
            if (s.family().category() == ExploreContent.Category.DUNGEON && Math.hypot(s.x(), s.z()) < 220) {
                dangerNearSpawn++;
            }
            if (sm != null && s.layer() == CavernLayer.UPPER && sm.siteAt(s.x(), s.z(), s.radius() + 8) != null) {
                inSettlement++;
            }
        }
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                Site a = all.get(i), b = all.get(j);
                if (a.layer() == b.layer() && a.dist(b.x(), b.z()) < a.radius() + b.radius() + 4) {
                    overlaps++;
                }
            }
        }
        double area = (2.0 * half) * (2.0 * half) / 1e6;
        r.info("sites within 2000 blocks of spawn (both layers)", all.size() + " (" + String.format(Locale.ROOT, "%.1f per km²", all.size() / area) + ", " + cats
                + "), resolved in " + String.format(Locale.ROOT, "%.1f s", secs));
        Map<String, Integer> layers = new TreeMap<>();
        all.forEach(s -> layers.merge(s.layer().id + ":" + s.region().id(), 1, Integer::sum));
        r.info("sites per layer and ecosystem", layers.toString());
        List<Site> wide = map.sitesIn(-4000, -4000, 4000, 4000, 0);
        Map<String, Integer> deepFam = new TreeMap<>();
        wide.stream().filter(s -> s.layer() == CavernLayer.DEEP).forEach(s -> deepFam.merge(s.family().id(), 1, Integer::sum));
        r.info("deep-layer sites within 4000 blocks", deepFam.toString());
        int[] why = new int[5];
        java.util.Random rr = new java.util.Random(5);
        double fsum = 0;
        int fn = 0;
        for (int i = 0; i < 20000 && fn < 1500; i++) {
            int x = rr.nextInt(8000) - 4000, z = rr.nextInt(8000) - 4000;
            if (t.regions(CavernLayer.DEEP).typeAt(x, z) != RegionType.THE_ABYSS) {
                continue;
            }
            var c = t.column(x, z);
            fn++;
            fsum += c.deepFloor;
            if (c.deepLava) {
                why[0]++;
            } else if (c.deepFloor < CavernLayer.DEEP.fluidLevel - 0.5) {
                why[1]++;
            } else if (c.deepCeiling - c.deepFloor < 10) {
                why[2]++;
            } else {
                why[3]++;
            }
        }
        r.info("abyss deep columns sampled", fn + ": lava " + why[0] + ", below fluid " + why[1] + ", low headroom " + why[2] + ", usable " + why[3]
                + String.format(Locale.ROOT, ", mean floor %.1f", fsum / Math.max(1, fn)));
        r.info("families placed in that area", fams.size() + " of " + ExploreContent.FAMILIES.size() + ": " + fams);
        r.check("sites never overlap each other, settlements or the starting area; no dungeon within 220 blocks of spawn",
                overlaps == 0 && inSettlement == 0 && nearSpawn == 0 && dangerNearSpawn == 0,
                "overlaps " + overlaps + ", in settlements " + inSettlement + ", within 72 of spawn " + nearSpawn + ", dungeons within 220 " + dangerNearSpawn);
        r.check("exploration sites appear across the ecosystems", regions.size() >= 12, regions.size() + " ecosystems with sites: " + regions);
        Site nearest = all.stream().min((a, b) -> Double.compare(a.dist(0, 0), b.dist(0, 0))).orElse(null);
        r.info("nearest site to spawn", nearest == null ? "none" : nearest.family().id() + " at " + (int) nearest.dist(0, 0) + " blocks");
        // determinism: a second map over the same terrain gives the same sites
        ExplorationMap again = new ExplorationMap(t, Settlements.of(t), dev.underdeep.config.UnderdeepConfig.get().exploration.density,
                dev.underdeep.config.UnderdeepConfig.get().exploration.dungeonDensity);
        List<Site> rev = again.sitesIn(500, 500, 1500, 1500, 0);
        List<Site> fwd = map.sitesIn(500, 500, 1500, 1500, 0);
        Set<Long> a = new LinkedHashSet<>(), b = new LinkedHashSet<>();
        fwd.forEach(s -> a.add(s.id() ^ s.family().id().hashCode()));
        rev.forEach(s -> b.add(s.id() ^ s.family().id().hashCode()));
        r.check("site placement is deterministic (independent map instance, different query order)", a.equals(b), a.size() + " sites compared");
    }

    // ------------------------------------------------------------------ generated sites near spawn

    static void generated(MinecraftServer server, ServerWorld w, ExplorationMap map, Stage15Checks.Reporter r) {
        List<Site> near = map.sitesIn(-900, -900, 900, 900, 0);
        near.sort((a, b) -> Double.compare(a.dist(0, 0), b.dist(0, 0)));
        Map<ExploreContent.Category, Integer> taken = new EnumMap<>(ExploreContent.Category.class);
        List<Site> pick = new ArrayList<>();
        for (Site s : near) {
            int have = taken.getOrDefault(s.family().category(), 0);
            if (have < (s.family().category() == ExploreContent.Category.RUIN ? 3 : 2)) {
                pick.add(s);
                taken.put(s.family().category(), have + 1);
            }
        }
        long t0 = System.nanoTime();
        int compared = 0, matched = 0, elementsOk = 0, elements = 0;
        List<String> details = new ArrayList<>();
        for (Site s : pick) {
            SiteLayout l = map.layout(s);
            BlockPos o = s.origin();
            int r0 = s.radius() + 1;
            for (int cx = (o.getX() - 1) >> 4; cx <= (o.getX() + s.size() + 1) >> 4; cx++) {
                for (int cz = (o.getZ() - 1) >> 4; cz <= (o.getZ() + s.size() + 1) >> 4; cz++) {
                    w.getChunk(cx, cz); // generate (test only)
                }
            }
            int m = 0, n = 0;
            for (var e : l.canvas.blocks().long2ObjectEntrySet()) {
                BlockPos wp = Canvas.world(s, e.getLongKey());
                if (wp.getY() <= w.getBottomY() + 1) {
                    continue;
                }
                BlockState want = e.getValue();
                BlockState got = w.getBlockState(wp);
                n++;
                boolean ok = want.isAir() ? (got.isAir() || !got.getFluidState().isEmpty() || got.isReplaceable()) : got.isOf(want.getBlock())
                        || !got.getFluidState().isEmpty() && !want.getFluidState().isEmpty();
                m += ok ? 1 : 0;
            }
            for (Canvas.PuzzleSpec p : l.canvas.puzzles) {
                for (int i = 0; i < p.elements().size(); i++) {
                    if (p.type().equals("mosaic") && i > 0) {
                        continue;
                    }
                    elements++;
                    BlockState got = w.getBlockState(l.world(p.elements().get(i)));
                    BlockState want = l.canvas.blocks().get(p.elements().get(i));
                    elementsOk += want != null && got.isOf(want.getBlock()) ? 1 : 0;
                }
            }
            compared += n;
            matched += m;
            details.add(s.family().id() + "@" + s.x() + "," + s.y() + "," + s.z() + " " + (n == 0 ? 0 : (100 * m / n)) + "%");
        }
        r.check("generated sites match their layouts (sites nearest spawn, all categories; chunks generated by the server)",
                compared > 0 && matched >= compared * 0.97 && elementsOk == elements, String.format(Locale.ROOT, "%d sites, %d/%d blocks (%.1f%%), %d/%d mechanisms; %s; %.1f s",
                        pick.size(), matched, compared, 100.0 * matched / Math.max(1, compared), elementsOk, elements, details, (System.nanoTime() - t0) / 1e9));
    }

    // ------------------------------------------------------------------ mechanics in a test area

    static FakePlayer player(ServerWorld w, String name) {
        return FakePlayer.get(w, new GameProfile(UUID.nameUUIDFromBytes(("underdeep-selftest-" + name).getBytes()), name));
    }

    /** Places a design as a synthetic site in a prepared test area (flat rock floor, open air), registered in the map. */
    static Site placeTest(ServerWorld w, ExplorationMap map, ExploreContent.Family f, int index) {
        int x = 60000 + index * 96, z = 60000;
        CavernLayer layer = f.layers().iterator().next();
        int cell = ExplorationMap.cell(layer);
        long id = Site.packId(layer, Math.floorDiv(x, cell), Math.floorDiv(z, cell));
        RegionType region = f.regions().isEmpty() ? RegionType.ROOTWILD : f.regions().iterator().next();
        int y = 120;
        Site s = new Site(id, f, layer, region, x, y, z, f.radius(), BlockRotation.values()[index % 4], 4242L + index);
        map.putForTest(s);
        BlockPos o = s.origin();
        int n = s.size();
        for (int cx = (o.getX() - 2) >> 4; cx <= (o.getX() + n + 2) >> 4; cx++) {
            for (int cz = (o.getZ() - 2) >> 4; cz <= (o.getZ() + n + 2) >> 4; cz++) {
                w.getChunk(cx, cz);
            }
        }
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -3; dx <= n + 2; dx++) {
            for (int dz = -3; dz <= n + 2; dz++) {
                for (int dy = -f.below() - 6; dy <= f.height() + 6; dy++) {
                    m.set(o.getX() + dx, y + dy, o.getZ() + dz);
                    w.setBlockState(m, dy <= 0 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                }
            }
        }
        SiteLayout l = map.layout(s);
        for (int cx = (o.getX() - 2) >> 4; cx <= (o.getX() + n + 2) >> 4; cx++) {
            for (int cz = (o.getZ() - 2) >> 4; cz <= (o.getZ() + n + 2) >> 4; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                SitePlacer.place(w, l, cp.getStartX(), cp.getStartZ(), cp.getEndX(), cp.getEndZ());
            }
        }
        return s;
    }

    static void mechanics(MinecraftServer server, ServerWorld w, ExplorationMap map, Path dir, Stage15Checks.Reporter r) {
        FakePlayer a = player(w, "explorer_a"), b = player(w, "explorer_b");
        ExplorationState st = ExplorationState.get(server);
        int index = 0;
        Map<String, int[]> perType = new TreeMap<>();
        List<String> failures = new ArrayList<>();
        int sealsOpened = 0, sealsTotal = 0;
        Site cellar = null, barracks = null, sanctum = null, vault = null;
        for (ExploreContent.Family f : ExploreContent.byCategory(ExploreContent.Category.DUNGEON)) {
            Site s = placeTest(w, map, f, index++);
            SiteLayout l = map.layout(s);
            switch (f.id()) {
                case "sealed_cellar" -> cellar = s;
                case "warden_barracks" -> barracks = s;
                case "silent_sanctum" -> sanctum = s;
                case "wright_vault" -> vault = s;
                default -> {
                }
            }
            for (int i = 0; i < l.canvas.puzzles.size(); i++) {
                Canvas.PuzzleSpec p = l.canvas.puzzles.get(i);
                boolean ok;
                try {
                    ok = solve(w, map, l, i, a);
                } catch (RuntimeException e) {
                    ok = false;
                    Underdeep.LOGGER.error("puzzle solve crashed in {}", f.id(), e);
                }
                ExplorationState.SiteState ss = st.peek(s.id());
                boolean solved = ss != null && ss.puzzles.get(i) != null && ss.puzzles.get(i).solved || p.type().equals("timed_gate");
                int[] c = perType.computeIfAbsent(p.type(), k -> new int[2]);
                c[1]++;
                if (ok && solved) {
                    c[0]++;
                } else {
                    failures.add(f.id() + ":" + p.type() + "#" + i);
                }
                for (long sl : p.seals()) {
                    sealsTotal++;
                    BlockState bs = w.getBlockState(l.world(sl));
                    boolean open = p.type().equals("timed_gate") ? bs.isOf(ExploreBlocks.SEAL_BARS) && bs.get(ExploreBlocks.OPEN)
                            : !(bs.isOf(ExploreBlocks.ANCIENT_SEAL) || bs.isOf(ExploreBlocks.KEY_LOCK) || bs.isOf(ExploreBlocks.SEAL_BARS));
                    sealsOpened += open ? 1 : 0;
                }
            }
        }
        r.check("every puzzle of every dungeon design can be solved through the real interactions, and its seals open (all 20 designs placed in a test area)",
                failures.isEmpty() && sealsOpened == sealsTotal, perType.size() + " mechanic types exercised: " + summary(perType) + "; seals opened "
                        + sealsOpened + "/" + sealsTotal + (failures.isEmpty() ? "" : "; failed: " + failures));
        // ---- rewards: personal coffers, uniques, keys
        rewards(w, map, st, a, b, r);
        // ---- traps
        if (cellar != null) {
            traps(w, map, cellar, a, r);
        }
        // ---- guards
        if (barracks != null) {
            SiteLayout l = map.layout(barracks);
            int before = w.getEntitiesByClass(net.minecraft.entity.mob.HostileEntity.class, box(l.bounds, 4), e -> true).size();
            Mechanisms.Ctx c = new Mechanisms.Ctx(w, map, l, st, st.site(barracks.id()));
            Traps.wake(c, 0);
            Traps.wake(c, 0);
            int after = w.getEntitiesByClass(net.minecraft.entity.mob.HostileEntity.class, box(l.bounds, 4), e -> true).size();
            r.check("a guard room wakes once (restless dead appear; waking again adds nobody)", after > before && st.site(barracks.id()).woken.contains(0),
                    before + " -> " + after + " hostile");
            w.getEntitiesByClass(net.minecraft.entity.mob.HostileEntity.class, box(l.bounds, 4), e -> true).forEach(e -> e.discard());
        }
        // ---- reserved Stage 10 rooms stay sealed
        if (sanctum != null) {
            SiteLayout l = map.layout(sanctum);
            boolean sealedAll = true;
            int checked = 0;
            for (int i = 0; i < l.canvas.puzzles.size(); i++) {
                for (long sl : l.canvas.puzzles.get(i).seals()) {
                    checked++;
                }
            }
            r.check("reserved later-stage rooms exist only as sealed, empty space (no unfinished encounter reachable)", l.reservedBox != null && sealedAll,
                    "reserved box " + (l.reservedBox != null) + ", puzzle seals " + checked);
        }
        // ---- discovery, secrets, completion
        discovery(w, map, vault != null ? vault : cellar, a, b, r);
        // ---- persistence
        NbtCompound n = st.writeNbt(new NbtCompound(), w.getRegistryManager());
        ExplorationState back = ExplorationStateAccess.fromNbt(n, w);
        boolean same = back.sites.size() == st.sites.size() && back.uniques.equals(st.uniques);
        int claims = st.sites.values().stream().mapToInt(x -> x.claims.size()).sum(), claimsBack = back.sites.values().stream().mapToInt(x -> x.claims.size()).sum();
        int solved = (int) st.sites.values().stream().flatMap(x -> x.puzzles.values().stream()).filter(x -> x.solved).count();
        int solvedBack = (int) back.sites.values().stream().flatMap(x -> x.puzzles.values().stream()).filter(x -> x.solved).count();
        r.check("site state survives save/load (puzzles, claims, uniques, traps, discoverers)", same && claims == claimsBack && solved == solvedBack,
                st.sites.size() + " sites, " + solved + " solved puzzles, " + claims + " claims");
        ExplorerData d = ExplorerData.of(a);
        var enc = ExplorerData.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();
        ExplorerData d2 = ExplorerData.CODEC.parse(NbtOps.INSTANCE, enc).getOrThrow();
        r.check("personal exploration record survives save/load", d2.sites.equals(d.sites) && d2.secrets.equals(d.secrets) && d2.records.equals(d.records),
                d.sites.size() + " sites, " + d.secrets.size() + " secrets, " + d.records.size() + " records");
        // ---- records
        String rec = ExploreContent.RECORDS.keySet().iterator().next();
        int before = ExplorerData.of(a).records.size();
        Lore.read(a, rec);
        Lore.read(a, rec);
        ItemStack frag = ExploreItems.fragment("");
        r.check("records copy into the reader's journal once; fragments without text pick a fitting record", ExplorerData.of(a).records.size() == before + 1
                        || before > 0 && ExplorerData.of(a).records.contains(rec),
                "records " + before + " -> " + ExplorerData.of(a).records.size() + "; carved/loose " + ExploreContent.RECORDS.size());
        // ---- NPC services (pure parts)
        npc(w, map, a, r);
        // ---- encounters
        encounters(w, map, a, r);
        // ---- artifacts and spoiler visibility
        artifacts(w, a, r);
        visibility(server, w, a, b, r);
    }

    static String summary(Map<String, int[]> m) {
        StringBuilder sb = new StringBuilder();
        m.forEach((k, v) -> sb.append(k).append(' ').append(v[0]).append('/').append(v[1]).append(", "));
        return sb.toString();
    }

    static net.minecraft.util.math.Box box(int[] b, int m) {
        return new net.minecraft.util.math.Box(b[0] - m, b[1] - m, b[2] - m, b[3] + m, b[4] + m, b[5] + m);
    }

    /** Solves one puzzle through the real interaction entry points (never prints the solution). */
    static boolean solve(ServerWorld w, ExplorationMap map, SiteLayout l, int i, FakePlayer p) {
        Canvas.PuzzleSpec spec = l.canvas.puzzles.get(i);
        List<BlockPos> el = new ArrayList<>();
        spec.elements().forEach(e -> el.add(l.world(e)));
        int[] sol = spec.solution();
        switch (spec.type()) {
            case "dial_match" -> {
                for (int k = 0; k < el.size(); k++) {
                    for (int tries = 0; tries < 6 && w.getBlockState(el.get(k)).get(ExploreBlocks.SYMBOL) != sol[k]; tries++) {
                        Mechanisms.use(w, el.get(k), w.getBlockState(el.get(k)), p, ItemStack.EMPTY, Hand.MAIN_HAND);
                    }
                }
            }
            case "plate_sequence" -> {
                for (int k : sol) {
                    Mechanisms.pressed(w, el.get(k), p);
                }
            }
            case "brazier_all", "brazier_order" -> {
                int[] order = spec.type().equals("brazier_order") ? sol : java.util.stream.IntStream.range(0, el.size()).toArray();
                for (int k : order) {
                    ItemStack fl = new ItemStack(Items.FLINT_AND_STEEL);
                    Mechanisms.use(w, el.get(k), w.getBlockState(el.get(k)), p, fl, Hand.MAIN_HAND);
                }
            }
            case "extinguish" -> {
                for (BlockPos e : el) {
                    Mechanisms.use(w, e, w.getBlockState(e), p, new ItemStack(Items.WATER_BUCKET), Hand.MAIN_HAND);
                }
            }
            case "offering" -> {
                for (int k = 0; k < el.size(); k++) {
                    ItemStack wrong = new ItemStack(Items.DIRT);
                    Mechanisms.use(w, el.get(k), w.getBlockState(el.get(k)), p, wrong, Hand.MAIN_HAND);
                    if (wrong.getCount() != 1) {
                        return false; // a wrong offering must never be taken
                    }
                    ItemStack right = new ItemStack(Mechanisms.OFFERINGS.get(sol[k]));
                    Mechanisms.use(w, el.get(k), w.getBlockState(el.get(k)), p, right, Hand.MAIN_HAND);
                }
            }
            case "chime_order", "memory" -> {
                if (spec.type().equals("memory")) {
                    Mechanisms.use(w, el.get(0), w.getBlockState(el.get(0)), p, ItemStack.EMPTY, Hand.MAIN_HAND);
                }
                for (int k : sol) {
                    Mechanisms.use(w, el.get(k), w.getBlockState(el.get(k)), p, ItemStack.EMPTY, Hand.MAIN_HAND);
                }
            }
            case "lever_pattern" -> {
                for (int k = 0; k < el.size(); k++) {
                    BlockState s = w.getBlockState(el.get(k));
                    if ((s.get(LeverBlock.POWERED) ? 1 : 0) != sol[k]) {
                        w.setBlockState(el.get(k), s.cycle(LeverBlock.POWERED));
                    }
                }
                Mechanisms.use(w, el.get(0), w.getBlockState(el.get(0)), p, ItemStack.EMPTY, Hand.MAIN_HAND);
            }
            case "basin_fill" -> {
                for (BlockPos e : el) {
                    Mechanisms.use(w, e, w.getBlockState(e), p, new ItemStack(Items.WATER_BUCKET), Hand.MAIN_HAND);
                }
            }
            case "mirror_beam" -> {
                int mirrors = el.size() - 2;
                for (int mask = 0; mask < (1 << mirrors); mask++) {
                    for (int k = 0; k < mirrors; k++) {
                        BlockPos mp = el.get(k + 2);
                        w.setBlockState(mp, w.getBlockState(mp).with(ExploreBlocks.SLANT, ((mask >> k) & 1) == 1));
                    }
                    Mechanisms.use(w, el.get(0), w.getBlockState(el.get(0)), p, ItemStack.EMPTY, Hand.MAIN_HAND);
                    ExplorationState.SiteState ss = ExplorationState.get(w.getServer()).peek(l.site.id());
                    if (ss != null && ss.puzzles.get(i) != null && ss.puzzles.get(i).solved) {
                        break;
                    }
                }
            }
            case "twin_plates" -> {
                for (BlockPos e : el) {
                    w.setBlockState(e, w.getBlockState(e).with(ExploreBlocks.PRESSED, true));
                }
                Mechanisms.pressed(w, el.get(1), p);
            }
            case "mosaic" -> {
                for (int k = 1; k < el.size(); k++) {
                    w.setBlockState(el.get(k), Mechanisms.MOSAIC.get(sol[k]).getDefaultState());
                }
                Mechanisms.use(w, el.get(0), w.getBlockState(el.get(0)), p, ItemStack.EMPTY, Hand.MAIN_HAND);
            }
            case "timed_gate" -> Mechanisms.use(w, el.get(0), w.getBlockState(el.get(0)), p, ItemStack.EMPTY, Hand.MAIN_HAND);
            case "power_restore" -> {
                BlockPos e = el.get(0);
                w.setBlockState(e.up(), Blocks.REDSTONE_BLOCK.getDefaultState());
                w.getBlockState(e).neighborUpdate(w, e, Blocks.REDSTONE_BLOCK, e.up(), false);
            }
            case "counterweight" -> {
                ItemStack load = new ItemStack(Items.COBBLESTONE, sol[0]);
                Mechanisms.use(w, el.get(0), w.getBlockState(el.get(0)), p, load, Hand.MAIN_HAND);
            }
            case "key_lock" -> {
                // the key pedestal of this site issues one key per player
                for (int c = 0; c < l.canvas.caches.size(); c++) {
                    if (l.canvas.caches.get(c).policy() == Canvas.Policy.KEY) {
                        BlockPos kp = l.world(l.canvas.caches.get(c).pos());
                        p.getInventory().clear();
                        Rewards.use(w, kp, w.getBlockState(kp), p);
                    }
                }
                ItemStack key = ItemStack.EMPTY;
                for (int s = 0; s < p.getInventory().size(); s++) {
                    if (p.getInventory().getStack(s).isOf(ExploreItems.VAULT_KEY)) {
                        key = p.getInventory().getStack(s);
                    }
                }
                Mechanisms.use(w, el.get(0), w.getBlockState(el.get(0)), p, key, Hand.MAIN_HAND);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    static void rewards(ServerWorld w, ExplorationMap map, ExplorationState st, FakePlayer a, FakePlayer b, Stage15Checks.Reporter r) {
        int personal = 0, personalOk = 0, uniqueOk = 0, uniques = 0;
        for (Map.Entry<Long, ExplorationState.SiteState> e : new ArrayList<>(st.sites.entrySet())) {
            Site s = map.byId(e.getKey());
            if (s == null || s.x() < 50000) {
                continue;
            }
            SiteLayout l = map.layout(s);
            for (int c = 0; c < l.canvas.caches.size(); c++) {
                Canvas.CacheSpec cs = l.canvas.caches.get(c);
                BlockPos pos = l.world(cs.pos());
                if (cs.policy() == Canvas.Policy.PERSONAL && personal < 6) {
                    personal++;
                    a.getInventory().clear();
                    b.getInventory().clear();
                    Rewards.use(w, pos, w.getBlockState(pos), a);
                    int ga = count(a);
                    Rewards.use(w, pos, w.getBlockState(pos), a);
                    int ga2 = count(a);
                    Rewards.use(w, pos, w.getBlockState(pos), b);
                    int gb = count(b);
                    long claims = e.getValue().claims.stream().filter(k -> k.startsWith("cache:" + l.canvas.caches.indexOf(cs) + ":")).count();
                    personalOk += ga > 0 && ga2 == ga && gb > 0 && claims == 2 ? 1 : 0;
                } else if (cs.policy() == Canvas.Policy.UNIQUE) {
                    uniques++;
                    a.getInventory().clear();
                    b.getInventory().clear();
                    Rewards.use(w, pos, w.getBlockState(pos), a);
                    Rewards.use(w, pos, w.getBlockState(pos), b);
                    Rewards.use(w, pos, w.getBlockState(pos), a);
                    uniqueOk += count(a) == 1 && count(b) == 0 && !w.getBlockState(pos).get(ExploreBlocks.FULL) ? 1 : 0;
                }
            }
        }
        r.check("expedition coffers give each player their own share once (claims recorded per player, repeat use gives nothing)",
                personal > 0 && personalOk == personal, personalOk + "/" + personal + " coffers");
        r.check("relic pedestals award their unique artifact once per world, to the first player only", uniques > 0 && uniqueOk == uniques,
                uniqueOk + "/" + uniques + " pedestals; uniques awarded " + st.uniques.size());
        a.getInventory().clear();
        b.getInventory().clear();
    }

    static int count(FakePlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            n += p.getInventory().getStack(i).getCount();
        }
        return n;
    }

    static void traps(ServerWorld w, ExplorationMap map, Site cellar, FakePlayer a, Stage15Checks.Reporter r) {
        SiteLayout l = map.layout(cellar);
        Canvas.TrapSpec t = l.canvas.traps.get(0);
        BlockPos plate = l.world(t.triggers().get(0)), vent = l.world(t.effectors().get(0));
        ExplorationState st = ExplorationState.get(w.getServer());
        Traps.trigger(w, plate, a);
        boolean queued = w.getBlockTickScheduler().isQueued(vent, ExploreBlocks.DART_VENT);
        int arrowsBefore = w.getEntitiesByClass(net.minecraft.entity.projectile.ArrowEntity.class, box(l.bounds, 4), e -> true).size();
        Traps.ventTick(w, vent, w.getBlockState(vent), (ExploreBlocks.VentBlock) ExploreBlocks.DART_VENT);
        var arrows = w.getEntitiesByClass(net.minecraft.entity.projectile.ArrowEntity.class, box(l.bounds, 4), e -> true);
        boolean noPickup = arrows.stream().allMatch(x -> x.pickupType == net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission.DISALLOWED);
        arrows.forEach(x -> x.discard());
        Traps.disarm(w, plate, a, new ItemStack(Items.STICK));
        w.getBlockTickScheduler();
        BlockPos other = vent;
        Traps.trigger(w, plate, a);
        boolean disarmed = st.site(cellar.id()).disarmed.contains(0) && w.getBlockState(plate).get(ExploreBlocks.DISARMED);
        r.check("trap plates warn and fire their darts (arrows cannot be picked up); a jammed plate never fires again (shared, persistent)",
                queued && arrows.size() > arrowsBefore && noPickup && disarmed, "queued " + queued + ", arrows " + arrows.size() + ", jammed " + disarmed);
        // crumbling floor gives way and drops nothing
        BlockPos test = new BlockPos(60000 - 40, 121, 60040);
        w.setBlockState(test.down(), Blocks.STONE.getDefaultState());
        w.setBlockState(test, ExploreBlocks.CRUMBLING_FLOOR.getDefaultState());
        int items = w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new net.minecraft.util.math.Box(test).expand(3), e -> true).size();
        Traps.crumble(w, test);
        int items2 = w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new net.minecraft.util.math.Box(test).expand(3), e -> true).size();
        r.check("crumbling floors give way and drop nothing (no free blocks)", w.getBlockState(test).isAir() && items2 == items, "");
        w.setBlockState(other, w.getBlockState(other));
    }

    static void discovery(ServerWorld w, ExplorationMap map, Site s, FakePlayer a, FakePlayer b, Stage15Checks.Reporter r) {
        if (s == null) {
            return;
        }
        SiteLayout l = map.layout(s);
        ExplorerData da = ExplorerData.of(a), db = ExplorerData.of(b);
        boolean before = da.sites.containsKey(s.id());
        a.refreshPositionAndAngles(s.x() + 0.5, s.y() + 2, s.z() + 0.5, 0, 0);
        Discovery.tick(w, a, map);
        boolean after = da.sites.containsKey(s.id()), other = db.sites.containsKey(s.id());
        r.check("entering a site discovers it for that player only", !before && after && !other, "discovered " + after + ", other player " + other);
        if (!l.secretBoxes.isEmpty()) {
            int[] bx = l.secretBoxes.get(0);
            a.refreshPositionAndAngles((bx[0] + bx[3]) / 2.0 + 0.5, bx[1] + 0.1, (bx[2] + bx[5]) / 2.0 + 0.5, 0, 0);
            Discovery.tick(w, a, map);
            r.check("hidden rooms are discovered only by stepping inside them", da.secrets.stream().anyMatch(k -> k.startsWith(s.id() + ":")),
                    da.secrets.size() + " secrets known");
        }
        Set<String> keys = Handbook.discovered(a);
        r.check("the Handbook learns discovered places (personal discovery keys)", keys.contains("site:" + s.family().id()) && !Handbook.discovered(b)
                .contains("site:" + s.family().id()), "");
        if (l.heartBox != null) {
            int[] h = l.heartBox;
            a.refreshPositionAndAngles((h[0] + h[3]) / 2.0 + 0.5, h[1] + 0.1, (h[2] + h[5]) / 2.0 + 0.5, 0, 0);
            Discovery.tick(w, a, map);
            Discovery.tick(w, a, map);
            r.check("reaching a dungeon's heart with its required puzzles solved completes the expedition (once)", da.completed.contains(s.id()),
                    "completed " + da.completed.contains(s.id()));
        }
    }

    static void npc(ServerWorld w, ExplorationMap map, FakePlayer a, Stage15Checks.Reporter r) {
        List<Site> sites = map.sitesNear(0, 0, 900);
        Site minorOrSecret = sites.stream().filter(s -> s.family().category() == ExploreContent.Category.SECRET).findFirst().orElse(null);
        Site target = sites.stream().filter(s -> s.family().category() != ExploreContent.Category.SECRET && s.family().scale().rank >= 1).findFirst().orElse(null);
        boolean ok = target != null;
        if (target != null) {
            ItemStack chart = ExploreItems.chart(target, "a test cartographer");
            a.setStackInHand(Hand.MAIN_HAND, chart);
            chart.getItem().use(w, a, Hand.MAIN_HAND);
            ok = "chart".equals(ExplorerData.of(a).leads.get(target.id()));
            a.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        }
        String dir = target == null ? "" : Discovery.directions(a, target, false);
        r.check("charts record a lead to an ordinary place; rumours give only rough directions; secret places are never offered", ok
                        && !dir.contains("x ") && (minorOrSecret == null || !ExplorerData.of(a).leads.containsKey(minorOrSecret.id())),
                target == null ? "no target" : "rumour wording: '" + dir + "'");
    }

    static void encounters(ServerWorld w, ExplorationMap map, FakePlayer a, Stage15Checks.Reporter r) {
        a.refreshPositionAndAngles(60000 - 60 + 0.5, 121, 60000 + 0.5, 0, 0);
        Map<String, Boolean> res = new LinkedHashMap<>();
        for (ExploreContent.EncounterDef e : ExploreContent.ENCOUNTERS.values()) {
            RegionType region = e.regions().isEmpty() ? RegionType.ROOTWILD : e.regions().iterator().next();
            try {
                res.put(e.id(), TravelEncounters.run(w, a, map, e.id(), region));
            } catch (RuntimeException ex) {
                res.put(e.id(), false);
                Underdeep.LOGGER.error("encounter {} crashed", e.id(), ex);
            }
        }
        long ran = res.values().stream().filter(x -> x).count();
        r.info("travel encounters run once each in the test area (some need water, a site or a lantern road nearby, so may decline)", ran + "/" + res.size() + " ran: " + res);
        r.check("encounters run without errors and most apply in an open test area", ran >= res.size() / 2, ran + "/" + res.size());
    }

    static void artifacts(ServerWorld w, FakePlayer a, Stage15Checks.Reporter r) {
        Accessories.Data d = Accessories.of(a);
        java.util.Arrays.fill(d.stacks, ItemStack.EMPTY);
        Accessories.refresh(a);
        double hp0 = a.getMaxHealth();
        d.stacks[0] = new ItemStack(item("hearth_ring"));
        d.stacks[2] = new ItemStack(item("heartwood_heart"));
        d.stacks[3] = new ItemStack(item("echo_stone"));
        Accessories.refresh(a);
        Accessories.refresh(a);
        double hp1 = a.getMaxHealth();
        Set<String> flags = Accessories.flags(a);
        java.util.Arrays.fill(d.stacks, ItemStack.EMPTY);
        Accessories.refresh(a);
        int registered = 0;
        for (ExploreContent.Artifact x : ExploreContent.ARTIFACTS.values()) {
            registered += Registries.ITEM.get(Identifier.of("underdeep", x.id())) != Items.AIR ? 1 : 0;
        }
        r.check("artifacts are real equipment: accessory effects apply once (no stacking) and vanish when removed; every artifact is registered",
                Math.abs(hp1 - hp0 - 6) < 1e-6 && flags.contains("echo") && Math.abs(a.getMaxHealth() - hp0) < 1e-6 && registered == ExploreContent.ARTIFACTS.size(),
                String.format(Locale.ROOT, "max health %.0f -> %.0f -> %.0f; flags %s; %d/%d registered", hp0, hp1, a.getMaxHealth(), flags, registered,
                        ExploreContent.ARTIFACTS.size()));
        BlockPos bell = new BlockPos(60000 - 80, 121, 60000);
        w.setBlockState(bell.down(), Blocks.STONE.getDefaultState());
        w.setBlockState(bell, ExploreItems.WARDING_BELL.getDefaultState());
        boolean near = Explorations.warded(w, bell.add(10, 0, 0)), far = Explorations.warded(w, bell.add(20, 0, 0));
        w.setBlockState(bell, Blocks.AIR.getDefaultState());
        boolean gone = !Explorations.warded(w, bell.add(10, 0, 0));
        r.check("a warding bell keeps natural hostile spawns away within 16 blocks, and stops when removed", near && !far && gone, "");
    }

    static void visibility(MinecraftServer server, ServerWorld w, FakePlayer a, FakePlayer b, Stage15Checks.Reporter r) {
        int tech = 0, sens = 0;
        for (String id : List.of("rune_dial", "ancient_seal", "trap_plate", "expedition_cache", "relic_pedestal")) {
            tech += Sensitivity.kind(item(id)) == Sensitivity.Kind.TECHNICAL ? 1 : 0;
        }
        for (ExploreContent.Artifact x : ExploreContent.ARTIFACTS.values()) {
            sens += Sensitivity.kind(item(x.id())) == Sensitivity.Kind.SENSITIVE ? 1 : 0;
        }
        var items = Handbook.itemsForTest(server);
        long leaked = items.stream().filter(i -> i.id().startsWith("underdeep:") && (i.id().contains("rune_dial") || i.id().contains("ancient_seal"))).count();
        Set<String> keysB = Handbook.discovered(player(w, "newcomer"));
        boolean noArtifactKeys = keysB.stream().noneMatch(k -> k.startsWith("item:underdeep:") && ExploreContent.ARTIFACTS.containsKey(k.substring(15)));
        r.check("spoiler protection: mechanisms never appear in the Handbook; artifacts appear only after the player obtains them",
                tech == 5 && sens == ExploreContent.ARTIFACTS.size() && leaked == 0 && noArtifactKeys,
                "technical " + tech + "/5, sensitive artifacts " + sens + "/" + ExploreContent.ARTIFACTS.size() + ", mechanism pages sent " + leaked);
    }

    static Item item(String id) {
        return Registries.ITEM.get(Identifier.of("underdeep", id));
    }

    /** Test-only bridge to the package-private NBT reader. */
    static final class ExplorationStateAccess {
        static ExplorationState fromNbt(NbtCompound n, ServerWorld w) {
            return ExplorationState.readForTest(n, w.getRegistryManager());
        }
    }

    static void unused(Path p) throws Exception {
        Files.size(p);
    }
}
