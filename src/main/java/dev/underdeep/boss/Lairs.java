package dev.underdeep.boss;

import dev.underdeep.explore.Canvas;
import dev.underdeep.explore.ExplorationMap;
import dev.underdeep.explore.ExploreContent;
import dev.underdeep.explore.Site;
import dev.underdeep.explore.design.Designs;
import dev.underdeep.explore.design.Kit;
import dev.underdeep.world.noise.Hashing;
import dev.underdeep.world.region.RegionType;
import dev.underdeep.world.terrain.CavernLayer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.BlockRotation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Guardian lairs: one per guardian per world, placed deterministically (seed + terrain model) by a bounded search
 * around a seeded bearing within the guardian's distance band and ecosystems; they replace any ordinary site they
 * overlap and never sit in settlements or on descent caves (the normal site fit check). Their arenas are authored
 * below. Placement rules are private in spirit (which guardian lives where); public docs only say lairs exist.
 */
public final class Lairs {
    private Lairs() {
    }

    record Rule(String boss, Set<CavernLayer> layers, Set<RegionType> regions, int min, int max) {
    }

    static final List<Rule> RULES = new ArrayList<>();

    static void rule(String boss, Set<CavernLayer> layers, int min, int max, RegionType... regions) {
        RULES.add(new Rule(boss, layers, regions.length == 0 ? EnumSet.allOf(RegionType.class) : EnumSet.of(regions[0], regions), min, max));
    }

    static {
        Set<CavernLayer> up = EnumSet.of(CavernLayer.UPPER), deep = EnumSet.of(CavernLayer.DEEP), both = EnumSet.allOf(CavernLayer.class);
        rule("mossback", up, 300, 700, RegionType.ROOTWILD, RegionType.GOLDEN_GROVE);
        rule("sporemother", up, 350, 800, RegionType.FUNGAL_EXPANSE);
        rule("choirwarden", up, 400, 900, RegionType.FLOODED_DEPTHS, RegionType.SUNKEN_GARDENS, RegionType.EMERALD_WETLANDS);
        rule("rimefang", up, 700, 1300, RegionType.EVERFROST);
        rule("galvanic", both, 800, 1400, RegionType.STORMSTONE_CAVERNS);
        rule("cinderwurm", both, 800, 1500, RegionType.ASHEN_HOLLOWS, RegionType.CRIMSON_BASIN);
        rule("lamplighter", up, 800, 1500, RegionType.STARLIGHT_CAVERNS, RegionType.GOLDEN_GROVE, RegionType.HOLLOW_PEAKS);
        rule("resonant", deep, 1200, 2000, RegionType.CRYSTAL_DEEP);
        rule("tidemother", up, 1300, 2100, RegionType.FLOODED_DEPTHS, RegionType.SUNKEN_GARDENS, RegionType.EMERALD_WETLANDS);
        rule("silentwarden", deep, 1300, 2200, RegionType.THE_ABYSS);
        rule("rootsovereign", up, 1500, 2400, RegionType.ROOTWILD);
        rule("seamwatcher", up, 1800, 2700);
        rule("somnarch", deep, 1800, 2600, RegionType.THE_ABYSS);
    }

    // ------------------------------------------------------------------------------------------ placement

    private static final Map<ExplorationMap, Map<String, Site>> PLANS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** All lairs of a world (computed once per map; bounded search). */
    public static Map<String, Site> plan(ExplorationMap map) {
        if (map == null) {
            return Map.of();
        }
        Map<String, Site> p = PLANS.get(map);
        if (p != null) {
            return p;
        }
        synchronized (PLANS) {
            p = PLANS.get(map);
            if (p == null) {
                p = compute(map);
                PLANS.put(map, p);
            }
        }
        return p;
    }

    @Nullable
    public static Site site(@Nullable ExplorationMap map, String boss) {
        return map == null ? null : plan(map).get(boss);
    }

    private static Map<String, Site> compute(ExplorationMap map) {
        Map<String, Site> out = new LinkedHashMap<>();
        long seed = Hashing.hash(map.terrain().seed(), Hashing.salt("lairs"));
        for (int i = 0; i < RULES.size(); i++) {
            Rule r = RULES.get(i);
            ExploreContent.Family f = ExploreContent.FAMILIES.get("lair_" + r.boss);
            if (f == null) {
                continue;
            }
            Site s = search(map, r, f, Hashing.hash(seed, i), out, true);
            if (s == null) {
                s = search(map, r, f, Hashing.hash(seed, i, 7), out, false);
            }
            if (s != null) {
                out.put(r.boss, s);
            }
        }
        return out;
    }

    @Nullable
    private static Site search(ExplorationMap map, Rule r, ExploreContent.Family f, long h, Map<String, Site> placed, boolean strictRegion) {
        double a0 = Hashing.unit(h) * Math.PI * 2;
        int tries = 0;
        for (int d = (r.min + r.max) / 2, step = 0; step < 40 && tries < 900; step++) {
            int dist = d + (step % 2 == 0 ? 1 : -1) * (step / 2) * 32;
            if (dist < r.min || dist > r.max) {
                continue;
            }
            for (int k = 0; k < 24 && tries < 900; k++) {
                double a = a0 + (k % 2 == 0 ? 1 : -1) * (k / 2) * (Math.PI / 12);
                int x = (int) Math.round(Math.cos(a) * dist), z = (int) Math.round(Math.sin(a) * dist);
                for (CavernLayer layer : r.layers) {
                    RegionType region = map.terrain().regions(layer).typeAt(x, z);
                    if (strictRegion && !r.regions.contains(region)) {
                        continue;
                    }
                    if (!f.layers().contains(layer)) {
                        continue;
                    }
                    boolean clash = false;
                    for (Site o : placed.values()) {
                        clash |= o.dist(x, z) < o.radius() + f.radius() + 60;
                    }
                    if (clash) {
                        continue;
                    }
                    tries++;
                    Integer floor = map.fitLair(layer, f, x, z);
                    if (floor == null) {
                        continue;
                    }
                    int cell = ExplorationMap.cell(layer);
                    long id = Site.packId(layer, Math.floorDiv(x, cell), Math.floorDiv(z, cell));
                    BlockRotation rot = BlockRotation.values()[(int) (Hashing.unit(Hashing.hash(h, 3)) * 4) & 3];
                    return new Site(id, f, layer, region, x, floor, z, f.radius(), rot, Hashing.hash(h, 5));
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------ arenas

    public static void registerDesigns() {
        Designs.BUILDERS.put("lair_mossback", Lairs::mossback);
        Designs.BUILDERS.put("lair_sporemother", Lairs::sporemother);
        Designs.BUILDERS.put("lair_choirwarden", Lairs::choirwarden);
        Designs.BUILDERS.put("lair_rimefang", Lairs::rimefang);
        Designs.BUILDERS.put("lair_galvanic", Lairs::galvanic);
        Designs.BUILDERS.put("lair_cinderwurm", Lairs::cinderwurm);
        Designs.BUILDERS.put("lair_lamplighter", Lairs::lamplighter);
        Designs.BUILDERS.put("lair_resonant", Lairs::resonant);
        Designs.BUILDERS.put("lair_tidemother", Lairs::tidemother);
        Designs.BUILDERS.put("lair_silentwarden", Lairs::silentwarden);
        Designs.BUILDERS.put("lair_rootsovereign", Lairs::rootsovereign);
        Designs.BUILDERS.put("lair_seamwatcher", Lairs::seamwatcher);
        Designs.BUILDERS.put("lair_somnarch", Lairs::somnarch);
    }

    static final BlockState AIR = Blocks.AIR.getDefaultState();

    /** Round arena floor, clear headroom, altar and heirloom pedestal at the south edge. */
    static int base(Kit k, Site s, Block a, Block b, int headroom) {
        int m = k.mid, r = s.radius() - 2;
        k.disc(m, m, r, 0, a, b);
        for (int x = m - r; x <= m + r; x++) {
            for (int z = m - r; z <= m + r; z++) {
                if (Math.hypot(x - m, z - m) <= r) {
                    for (int y = 1; y <= headroom; y++) {
                        k.c.set(x, y, z, AIR);
                    }
                }
            }
        }
        k.c.mark("center", m, 1, m);
        k.c.mark("spawn", m, 1, m);
        k.c.fixed(m, 1, m + r - 3, BossBlocks.ALTAR.getDefaultState());
        k.c.mark("altar", m, 1, m + r - 3);
        BossDef d = Bosses.byId(s.family().id().substring(5));
        if (d != null && d.heirloom() != null) {
            k.pedestal(m + 3, 1, m + r - 3, d.heirloom());
        }
        return r;
    }

    static void device(Kit k, String kind, Block b, int x, int z, boolean lit) {
        k.c.fixed(x, 1, z, b.getDefaultState().with(BossBlocks.LIT, lit));
        k.c.mark(kind, x, 1, z);
    }

    static int[] around(Kit k, double radius, double angle) {
        return new int[]{(int) Math.round(k.mid + Math.cos(angle) * radius), (int) Math.round(k.mid + Math.sin(angle) * radius)};
    }

    /** A ruined, broken ring wall (natural gaps every few blocks). */
    static void ruinedRing(Kit k, int r, Block b, int hMin, int hMax) {
        int m = k.mid;
        for (int i = 0; i < 360; i += 3) {
            double a = Math.toRadians(i);
            if (i % 60 < 12) {
                continue; // gaps
            }
            int x = (int) Math.round(m + Math.cos(a) * r), z = (int) Math.round(m + Math.sin(a) * r);
            int h = hMin + k.ri(hMax - hMin + 1);
            for (int y = 1; y <= h; y++) {
                k.c.weak(x, y, z, b.getDefaultState());
            }
        }
    }

    static void column(Kit k, int x, int z, int r, int h, Block b) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= r * r + 1) {
                    for (int y = 1; y <= h; y++) {
                        k.c.force(x + dx, y, z + dz, b.getDefaultState());
                    }
                }
            }
        }
    }

    static void mossback(Kit k, Site s) {
        int r = base(k, s, Blocks.MOSS_BLOCK, Blocks.ROOTED_DIRT, 14);
        for (int i = 0; i < 6; i++) { // root pillars: charge into them
            int[] p = around(k, r * 0.6, i * Math.PI / 3 + 0.3);
            column(k, p[0], p[1], 1, 12, Blocks.STRIPPED_DARK_OAK_LOG);
            k.c.set(p[0], 13, p[1], Blocks.MANGROVE_ROOTS.getDefaultState());
        }
        ruinedRing(k, r, Blocks.MUD_BRICKS, 1, 4);
        k.weather(0.15, false);
    }

    static void sporemother(Kit k, Site s) {
        int r = base(k, s, Blocks.MYCELIUM, Blocks.MOSS_BLOCK, 16);
        for (int i = 0; i < 5; i++) {
            int[] p = around(k, r * 0.75, i * Math.PI * 2 / 5);
            column(k, p[0], p[1], 0, 6 + k.ri(4), Blocks.MUSHROOM_STEM);
            k.disc(p[0], p[1], 2.5, 7 + k.ri(3), Blocks.RED_MUSHROOM_BLOCK, Blocks.BROWN_MUSHROOM_BLOCK);
        }
        for (int i = 0; i < 3; i++) {
            int[] p = around(k, r * 0.45, i * Math.PI * 2 / 3 + 1);
            k.c.mark("root_spot", p[0], 1, p[1]);
            k.c.set(p[0], 0, p[1], Blocks.PODZOL.getDefaultState());
        }
        k.weather(0.1, true);
    }

    static void choirwarden(Kit k, Site s) {
        int r = base(k, s, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, 12);
        int m = k.mid;
        for (int x = m - r; x <= m + r; x++) { // two shallow pools
            for (int z = m - r; z <= m + r; z++) {
                double d = Math.hypot(x - m, z - m);
                if (d < r - 2 && d > 4 && (x - m) * (z - m) > 0 && Math.abs(x - m) > 2) {
                    k.c.set(x, 0, z, Blocks.WATER.getDefaultState());
                    k.c.set(x, -1, z, Blocks.PRISMARINE.getDefaultState());
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            int[] p = around(k, r - 1, i * Math.PI / 4);
            column(k, p[0], p[1], 0, 8, Blocks.PRISMARINE_BRICKS);
            k.c.set(p[0], 9, p[1], Blocks.SEA_LANTERN.getDefaultState());
        }
    }

    static void rimefang(Kit k, Site s) {
        int r = base(k, s, Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, 14);
        k.disc(k.mid, k.mid, r * 0.45, 0, Blocks.BLUE_ICE, Blocks.PACKED_ICE);
        for (int i = 0; i < 4; i++) {
            int[] p = around(k, r * 0.62, i * Math.PI / 2 + 0.78);
            device(k, "brazier", BossBlocks.BRAZIER, p[0], p[1], false);
        }
        for (int i = 0; i < 7; i++) {
            int[] p = around(k, r * (0.3 + 0.5 * k.r()), k.r() * Math.PI * 2);
            k.c.weak(p[0], 1, p[1], Blocks.PACKED_ICE.getDefaultState());
        }
        ruinedRing(k, r, Blocks.PACKED_ICE, 1, 3);
    }

    static void galvanic(Kit k, Site s) {
        int r = base(k, s, Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.TUFF_BRICKS, 16);
        for (int i = 0; i < 3; i++) {
            int[] p = around(k, r * 0.66, i * Math.PI * 2 / 3);
            device(k, "pylon", BossBlocks.PYLON, p[0], p[1], true);
            for (int y = 2; y <= 4; y++) {
                k.c.fixed(p[0], y, p[1], Blocks.WAXED_COPPER_BLOCK.getDefaultState());
            }
            k.c.fixed(p[0], 5, p[1], Blocks.LIGHTNING_ROD.getDefaultState());
        }
        ruinedRing(k, r, Blocks.TUFF_BRICKS, 2, 5);
    }

    static void cinderwurm(Kit k, Site s) {
        int r = base(k, s, Blocks.BASALT, Blocks.BLACKSTONE, 12);
        int m = k.mid;
        for (int i = 0; i < 40; i++) {
            int[] p = around(k, r * k.r(), k.r() * Math.PI * 2);
            k.c.set(p[0], 0, p[1], Blocks.MAGMA_BLOCK.getDefaultState());
        }
        for (int x = m - r; x <= m + r; x++) {
            for (int z = m - r; z <= m + r; z++) {
                if (Math.hypot(x - m, z - m) <= r) {
                    for (int y = -4; y < 0; y++) {
                        k.c.set(x, y, z, Blocks.BASALT.getDefaultState());
                    }
                }
            }
        }
        ruinedRing(k, r, Blocks.POLISHED_BLACKSTONE_BRICKS, 1, 4);
    }

    static void lamplighter(Kit k, Site s) {
        int r = base(k, s, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES, 14);
        for (int i = 0; i < 5; i++) {
            int[] p = around(k, r * 0.6, i * Math.PI * 2 / 5 + 0.4);
            k.c.fixed(p[0], 1, p[1], Blocks.WAXED_EXPOSED_COPPER.getDefaultState());
            k.c.fixed(p[0], 2, p[1], Blocks.WAXED_EXPOSED_COPPER.getDefaultState());
            k.c.fixed(p[0], 3, p[1], BossBlocks.LANTERN.getDefaultState().with(BossBlocks.LIT, true));
            k.c.mark("lantern", p[0], 3, p[1]);
        }
        for (int i = 0; i < 6; i++) {
            int[] p = around(k, r * 0.88, i * Math.PI / 3);
            k.pillar(p[0], p[1], 1, 7, true);
        }
        k.weather(0.2, true);
    }

    static void resonant(Kit k, Site s) {
        int r = base(k, s, Blocks.CALCITE, Blocks.AMETHYST_BLOCK, 18);
        for (int i = 0; i < 9; i++) {
            int[] p = around(k, r * (0.75 + 0.2 * k.r()), i * Math.PI * 2 / 9);
            column(k, p[0], p[1], 0, 3 + k.ri(5), Blocks.AMETHYST_BLOCK);
            k.c.set(p[0], 9, p[1], Blocks.AMETHYST_CLUSTER.getDefaultState());
        }
        ruinedRing(k, r, Blocks.CALCITE, 1, 3);
    }

    static void tidemother(Kit k, Site s) {
        int m = k.mid, r = s.radius() - 2;
        base(k, s, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, 14);
        for (int x = m - r; x <= m + r; x++) { // deep basin
            for (int z = m - r; z <= m + r; z++) {
                double d = Math.hypot(x - m, z - m);
                if (d <= r - 1) {
                    for (int y = -6; y <= 0; y++) {
                        k.c.set(x, y, z, Blocks.WATER.getDefaultState());
                    }
                    k.c.set(x, -7, z, Blocks.GRAVEL.getDefaultState());
                }
            }
        }
        List<int[]> islands = new ArrayList<>();
        islands.add(new int[]{m, m + r - 3});
        for (int i = 0; i < 5; i++) {
            islands.add(around(k, r * 0.55, i * Math.PI * 2 / 5 + Math.PI / 2));
        }
        for (int[] p : islands) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx * dx + dz * dz <= 9) {
                        for (int y = -7; y <= 0; y++) {
                            k.c.set(p[0] + dx, y, p[1] + dz, (y == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE).getDefaultState());
                        }
                    }
                }
            }
        }
        k.c.fixed(m, 1, m + r - 3, BossBlocks.ALTAR.getDefaultState());
        k.c.marks.remove("spawn");
        k.c.mark("spawn", m, -3, m);
    }

    static void silentwarden(Kit k, Site s) {
        int r = base(k, s, Blocks.DEEPSLATE_TILES, Blocks.SCULK, 16);
        for (int i = 0; i < 8; i++) { // cover pillars
            int[] p = around(k, r * 0.55, i * Math.PI / 4 + 0.2);
            column(k, p[0], p[1], 1, 9, Blocks.DEEPSLATE_BRICKS);
        }
        for (int i = 0; i < 4; i++) {
            int[] p = around(k, r * 0.85, i * Math.PI / 2);
            device(k, "bell", BossBlocks.BELL, p[0], p[1], false);
        }
        ruinedRing(k, r, Blocks.DEEPSLATE_BRICKS, 3, 6);
    }

    static void rootsovereign(Kit k, Site s) {
        int r = base(k, s, Blocks.ROOTED_DIRT, Blocks.MOSS_BLOCK, 18);
        for (int i = 0; i < 3; i++) {
            int[] p = around(k, r * 0.7, i * Math.PI * 2 / 3 + 0.5);
            device(k, "root", BossBlocks.ROOT_HEART, p[0], p[1], true);
            column(k, p[0], p[1] + 2, 1, 14, Blocks.MANGROVE_ROOTS);
        }
        for (int i = 0; i < 10; i++) {
            int[] p = around(k, r * (0.4 + 0.5 * k.r()), k.r() * Math.PI * 2);
            k.c.weak(p[0], 1, p[1], Blocks.MANGROVE_ROOTS.getDefaultState());
        }
        k.weather(0.2, true);
    }

    static void seamwatcher(Kit k, Site s) {
        int r = base(k, s, Blocks.CALCITE, Blocks.SMOOTH_QUARTZ, 24);
        for (int i = 0; i < 6; i++) {
            int[] p = around(k, r * 0.8, i * Math.PI / 3);
            column(k, p[0], p[1], 0, 6 + k.ri(6), Blocks.QUARTZ_PILLAR);
            k.c.set(p[0], 13, p[1], Blocks.END_ROD.getDefaultState());
        }
        ruinedRing(k, r, Blocks.CALCITE, 1, 3);
    }

    static void somnarch(Kit k, Site s) {
        int m = k.mid, r = base(k, s, Blocks.POLISHED_DEEPSLATE, Blocks.MAGMA_BLOCK, 24);
        // enclosed Heart Engine: unbreakable walls and dome, one door
        for (int x = m - r - 1; x <= m + r + 1; x++) {
            for (int z = m - r - 1; z <= m + r + 1; z++) {
                double d = Math.hypot(x - m, z - m);
                if (d > r && d <= r + 1.5) {
                    for (int y = 0; y <= 25; y++) {
                        k.c.fixed(x, y, z, BossBlocks.ENGINE_STONE.getDefaultState());
                    }
                }
                if (d <= r + 1.5) {
                    k.c.fixed(x, 25, z, BossBlocks.ENGINE_STONE.getDefaultState());
                    k.c.fixed(x, -1, z, BossBlocks.ENGINE_STONE.getDefaultState());
                }
            }
        }
        // the door (north), 5 wide, 6 high
        for (int x = m - 2; x <= m + 2; x++) {
            for (int y = 1; y <= 6; y++) {
                int z = m - r - 1;
                k.c.fixed(x, y, z, BossBlocks.ENGINE_DOOR.getDefaultState());
                k.c.fixed(x, y, z + 1, BossBlocks.ENGINE_DOOR.getDefaultState());
                k.c.mark("door", x, y, z);
                k.c.mark("door", x, y, z + 1);
                k.c.breakable.add(Canvas.key(x, y, z)); // opens with the Signs: passable for the layout audit
                k.c.breakable.add(Canvas.key(x, y, z + 1));
            }
            for (int z = Math.max(0, m - r - 6); z < m - r - 1; z++) { // approach
                k.c.set(x, 0, z, Blocks.POLISHED_DEEPSLATE.getDefaultState());
                for (int y = 1; y <= 6; y++) {
                    k.c.set(x, y, z, AIR);
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            int[] p = around(k, r * 0.62, i * Math.PI * 2 / 3 + Math.PI / 2);
            k.c.fixed(p[0], 1, p[1], Blocks.CHISELED_DEEPSLATE.getDefaultState());
            k.c.fixed(p[0], 2, p[1], BossBlocks.COIL.getDefaultState().with(BossBlocks.LIT, true));
            k.c.mark("coil", p[0], 2, p[1]);
        }
        k.c.marks.remove("altar");
        k.c.fixed(m, 1, m + r - 3, BossBlocks.ALTAR.getDefaultState());
        k.c.mark("altar", m, 1, m + r - 3);
        for (int i = 0; i < 12; i++) {
            int[] p = around(k, r - 1, i * Math.PI / 6);
            k.c.fixed(p[0], 1, p[1], Blocks.CRYING_OBSIDIAN.getDefaultState());
        }
    }
}
