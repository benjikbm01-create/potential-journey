package dev.underdeep.explore.design;

import dev.underdeep.explore.Canvas;
import dev.underdeep.explore.ExploreBlocks;
import dev.underdeep.explore.Style;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Building vocabulary for authored exploration sites: eroded walls, arches, pillars, stairs, rooms and corridors,
 * furniture, decay and overgrowth, and helpers that place puzzle mechanisms with their clues. All randomness comes
 * from the canvas' seeded random, so layouts are deterministic per site.
 */
public final class Kit {
    public final Canvas c;
    public final Style s;
    public final int n, mid;

    public Kit(Canvas c) {
        this.c = c;
        this.s = c.style;
        this.n = c.n;
        this.mid = c.n / 2;
    }

    public double r() {
        return c.rnd.nextDouble();
    }

    public int ri(int bound) {
        return c.rnd.nextInt(Math.max(1, bound));
    }

    public boolean chance(double p) {
        return c.rnd.nextDouble() < p;
    }

    public <T> T pick(List<T> l) {
        return l.get(ri(l.size()));
    }

    // ------------------------------------------------------------------ primitives

    public void floor(int x1, int z1, int x2, int z2, int y, Block a, Block b) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                c.set(x, y, z, ((x + z) % 3 == 0 && b != null ? b : a).getDefaultState());
            }
        }
    }

    public void disc(double cx, double cz, double r, int y, Block a, Block b) {
        for (int x = (int) (cx - r - 1); x <= cx + r + 1; x++) {
            for (int z = (int) (cz - r - 1); z <= cz + r + 1; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d <= r + 0.3) {
                    c.set(x, y, z, (b != null && d > r - 1.2 ? b : a).getDefaultState());
                }
            }
        }
    }

    public void ring(double cx, double cz, double r, int y1, int y2, Block b) {
        for (int x = (int) (cx - r - 1); x <= cx + r + 1; x++) {
            for (int z = (int) (cz - r - 1); z <= cz + r + 1; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d <= r + 0.5 && d > r - 0.6) {
                    for (int y = y1; y <= y2; y++) {
                        c.set(x, y, z, b.getDefaultState());
                    }
                }
            }
        }
    }

    /** Clears air above a floor area (interiors). */
    public void air(int x1, int y1, int z1, int x2, int y2, int z2) {
        c.clear(x1, y1, z1, x2, y2, z2);
    }

    /** A wall run with a ruined top: height varies between min and max along it. */
    public void wall(int x1, int z1, int x2, int z2, int y0, int hMin, int hMax, Block main, Block alt) {
        int len = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        int h = hMax;
        for (int i = 0; i <= len; i++) {
            int x = x1 + Integer.signum(x2 - x1) * i, z = z1 + Integer.signum(z2 - z1) * i;
            if (i % 3 == 0) {
                h = Math.max(hMin, Math.min(hMax, h + ri(3) - 1));
            }
            for (int y = y0; y < y0 + h; y++) {
                c.set(x, y, z, (alt != null && chance(0.18) ? alt : main).getDefaultState());
            }
        }
    }

    /** A rectangular building shell: walls, floor, optional flat ceiling; interior cleared. */
    public void shell(int x1, int z1, int x2, int z2, int y0, int h, boolean roof, double ruin) {
        floor(x1, z1, x2, z2, y0, s.floor, s.floorAlt);
        air(x1 + 1, y0 + 1, z1 + 1, x2 - 1, y0 + h, z2 - 1);
        int lo = ruin > 0 ? Math.max(1, (int) (h * (1 - ruin))) : h;
        wall(x1, z1, x2, z1, y0 + 1, lo, h, s.brick, s.cracked);
        wall(x2, z1, x2, z2, y0 + 1, lo, h, s.brick, s.cracked);
        wall(x2, z2, x1, z2, y0 + 1, lo, h, s.brick, s.cracked);
        wall(x1, z2, x1, z1, y0 + 1, lo, h, s.brick, s.cracked);
        for (int[] k : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            for (int y = y0 + 1; y <= y0 + h; y++) {
                c.set(k[0], y, k[1], Style.axis(s.pillar, Direction.Axis.Y));
            }
        }
        if (roof) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    if (ruin == 0 || !chance(ruin * 0.8)) {
                        c.set(x, y0 + h + 1, z, (x == x1 || x == x2 || z == z1 || z == z2 ? s.trim : s.brick).getDefaultState());
                    }
                }
            }
        }
    }

    /** A carved room below or above ground: solid shell of the style, cleared interior. */
    public void room(int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    boolean edge = x == x1 || x == x2 || y == y1 || y == y2 || z == z1 || z == z2;
                    if (edge) {
                        c.soft(x, y, z, (y == y1 ? (((x + z) & 1) == 0 ? s.floor : s.floorAlt) : y == y2 ? s.block : chance(0.15) ? s.cracked : s.brick)
                                .getDefaultState());
                    } else {
                        c.set(x, y, z, Canvas.AIR);
                    }
                }
            }
        }
    }

    /** A straight corridor (along x or z), width w, height h, floor at y. Sets interior air and walls softly. */
    public void corridor(int x1, int z1, int x2, int z2, int y, int w, int h) {
        boolean alongX = Math.abs(x2 - x1) >= Math.abs(z2 - z1);
        int len = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        for (int i = 0; i <= len; i++) {
            int x = x1 + (alongX ? Integer.signum(x2 - x1) * i : 0), z = z1 + (alongX ? 0 : Integer.signum(z2 - z1) * i);
            for (int k = -(w / 2) - 1; k <= w - w / 2; k++) {
                int px = alongX ? x : x + k, pz = alongX ? z + k : z;
                boolean side = k == -(w / 2) - 1 || k == w - w / 2;
                for (int dy = 0; dy <= h + 1; dy++) {
                    if (side || dy == 0 || dy == h + 1) {
                        c.soft(px, y + dy, pz, (dy == 0 ? s.floor : dy == h + 1 ? s.block : s.brick).getDefaultState());
                    } else {
                        c.set(px, y + dy, pz, Canvas.AIR);
                    }
                }
                if (!side && i % 8 == 4 && k == 0 && h >= 3) {
                    c.set(px, y + h, pz, Style.hanging(s.hangingLight));
                }
            }
        }
    }

    /** Straight stairs climbing (dy per step) along a direction, carving headroom; width w. */
    public void stairs(int x, int y, int z, Direction dir, int steps, int w, Block stair, Block support) {
        Direction side = dir.rotateYClockwise();
        for (int i = 0; i < steps; i++) {
            for (int k = 0; k < w; k++) {
                int px = x + dir.getOffsetX() * i + side.getOffsetX() * k, pz = z + dir.getOffsetZ() * i + side.getOffsetZ() * k;
                int py = y + i;
                c.set(px, py, pz, Style.stairs(stair, dir, false));
                c.set(px, py - 1, pz, support.getDefaultState());
                c.lock(px, py, pz);
                for (int h = 1; h <= 3; h++) {
                    c.set(px, py + h, pz, Canvas.AIR);
                }
            }
        }
    }

    /** A spiral staircase in a 5x5 well (centre cx,cz) from y1 up to y2. */
    public void spiral(int cx, int cz, int y1, int y2) {
        int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        for (int y = y1; y <= y2 + 3; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    if (edge) {
                        c.soft(cx + dx, y, cz + dz, s.brick.getDefaultState());
                    } else if (dx != 0 || dz != 0) {
                        c.set(cx + dx, y, cz + dz, Canvas.AIR);
                    }
                }
            }
            c.set(cx, y, cz, Style.axis(s.pillar, Direction.Axis.Y));
        }
        for (int y = y1, i = 0; y <= y2; y++, i++) {
            int[] r = ring[i % 8];
            c.set(cx + r[0], y, cz + r[1], s.block.getDefaultState());
            c.lock(cx + r[0], y, cz + r[1]);
            int[] r2 = ring[(i + 1) % 8];
            c.set(cx + r2[0], y, cz + r2[1], Style.slab(s.brickSlab, false));
            c.lock(cx + r2[0], y, cz + r2[1]);
        }
    }

    public void pillar(int x, int z, int y1, int y2, boolean capital) {
        for (int y = y1; y <= y2; y++) {
            c.set(x, y, z, Style.axis(s.pillar, Direction.Axis.Y));
        }
        if (capital) {
            c.set(x, y2 + 1, z, s.chiseled.getDefaultState());
        }
    }

    /** A round arch over an opening between x1..x2 at z (along x) springing at y. */
    public void arch(int x1, int x2, int z, int y, boolean alongX) {
        int w = x2 - x1;
        double r = w / 2.0;
        for (int i = 0; i <= w; i++) {
            double dx = i - r;
            int top = y + (int) Math.round(Math.sqrt(Math.max(0, r * r - dx * dx)));
            int px = alongX ? x1 + i : z, pz = alongX ? z : x1 + i;
            c.set(px, top, pz, s.trim.getDefaultState());
            c.set(px, top + 1, pz, s.brick.getDefaultState());
            for (int yy = y; yy < top; yy++) {
                if (i == 0 || i == w) {
                    c.set(px, yy, pz, s.brick.getDefaultState());
                } else {
                    c.set(px, yy, pz, Canvas.AIR);
                }
            }
        }
    }

    public void lantern(int x, int y, int z) {
        c.set(x, y, z, Style.hanging(s.hangingLight));
        c.set(x, y + 1, z, Blocks.CHAIN.getDefaultState());
    }

    public void standingLight(int x, int y, int z) {
        c.set(x, y, z, s.brickWall.getDefaultState());
        c.set(x, y + 1, z, s.light.getDefaultState());
    }

    // ------------------------------------------------------------------ furniture and storytelling

    public void table(int x, int y, int z) {
        c.set(x, y, z, s.fence.getDefaultState());
        c.set(x, y + 1, z, Style.slab(s.woodSlab, false));
    }

    public void chair(int x, int y, int z, Direction facing) {
        c.set(x, y, z, Style.stairs(s.woodStairs, facing.getOpposite(), false));
    }

    public void bedRemains(int x, int y, int z, Direction head) {
        c.set(x, y, z, Style.slab(s.woodSlab, false));
        c.set(x + head.getOffsetX(), y, z + head.getOffsetZ(), Blocks.BROWN_CARPET.getDefaultState());
    }

    public void shelf(int x, int y, int z, boolean books) {
        c.set(x, y, z, books ? (chance(0.5) ? Blocks.BOOKSHELF : Blocks.CHISELED_BOOKSHELF).getDefaultState() : Blocks.BARREL.getDefaultState());
    }

    public void barrel(int x, int y, int z) {
        c.set(x, y, z, Style.facing(Blocks.BARREL, Direction.UP));
    }

    /** A shared container with a site loot table (rolled once). */
    public void chest(int x, int y, int z, Direction facing, String loot) {
        c.set(x, y, z, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing));
        c.cache(x, y, z, Canvas.Policy.SHARED, loot);
    }

    public void barrelLoot(int x, int y, int z, String loot) {
        c.set(x, y, z, Style.facing(Blocks.BARREL, Direction.UP));
        c.cache(x, y, z, Canvas.Policy.SHARED, loot);
    }

    public void pot(int x, int y, int z, String loot) {
        c.set(x, y, z, Blocks.DECORATED_POT.getDefaultState());
        if (loot != null) {
            c.cache(x, y, z, Canvas.Policy.SHARED, loot);
        }
    }

    /** Per-player coffer (fair group rewards). */
    public void coffer(int x, int y, int z, Direction facing, String loot) {
        c.set(x, y, z, Style.facing(ExploreBlocks.EXPEDITION_CACHE, facing));
        c.cache(x, y, z, Canvas.Policy.PERSONAL, loot);
    }

    public void pedestal(int x, int y, int z, String artifact) {
        c.set(x, y, z, ExploreBlocks.RELIC_PEDESTAL.getDefaultState());
        c.cache(x, y, z, Canvas.Policy.UNIQUE, "artifact:" + artifact);
    }

    public void keyPedestal(int x, int y, int z) {
        c.set(x, y, z, ExploreBlocks.KEY_PEDESTAL.getDefaultState());
        c.cache(x, y, z, Canvas.Policy.KEY, "");
    }

    public void inscription(int x, int y, int z, Direction facing, String record) {
        c.set(x, y, z, Style.facing(ExploreBlocks.INSCRIPTION, facing));
        c.inscription(x, y, z, record);
    }

    /** A simple standing figure (statue) of style stone, facing a direction. */
    public void statue(int x, int y, int z, Direction facing, int scale) {
        for (int k = 0; k < scale; k++) {
            c.set(x, y + k, z, s.block.getDefaultState());
        }
        int sh = y + scale;
        Direction l = facing.rotateYCounterclockwise(), r = facing.rotateYClockwise();
        c.set(x + l.getOffsetX(), sh, z + l.getOffsetZ(), Style.stairs(s.brickStairs, r, true));
        c.set(x + r.getOffsetX(), sh, z + r.getOffsetZ(), Style.stairs(s.brickStairs, l, true));
        c.set(x, sh, z, s.block.getDefaultState());
        c.set(x, sh + 1, z, s.chiseled.getDefaultState());
    }

    public void skeleton(int x, int y, int z) {
        c.set(x, y, z, Blocks.SKELETON_SKULL.getDefaultState().with(Properties.ROTATION, ri(16)));
        if (chance(0.5)) {
            c.soft(x + 1, y, z, Blocks.BONE_BLOCK.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
        }
    }

    public void door(int x, int y, int z, Direction facing) {
        if (s.door == null) {
            return;
        }
        BlockState d = s.door.getDefaultState().with(DoorBlock.FACING, facing);
        c.set(x, y, z, d.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        c.set(x, y + 1, z, d.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    // ------------------------------------------------------------------ decay and overgrowth

    /**
     * Weathers the design: cracks and replaces bricks, drops some top courses (never locked positions or floors at
     * y <= 0), carpets floors with moss or snow, hangs vines, scatters rubble and the region's plants.
     */
    public void weather(double amount, boolean webs) {
        List<long[]> edits = new ArrayList<>();
        for (var e : c.blocks().long2ObjectEntrySet()) {
            long k = e.getLongKey();
            int x = net.minecraft.util.math.BlockPos.unpackLongX(k), y = net.minecraft.util.math.BlockPos.unpackLongY(k),
                    z = net.minecraft.util.math.BlockPos.unpackLongZ(k);
            if (c.locked(x, y, z)) {
                continue;
            }
            BlockState st = e.getValue();
            Block b = st.getBlock();
            Block w = s.weathered.get(b);
            double u = r();
            if (w != null && u < amount * 0.45) {
                edits.add(new long[]{k, 1});
            } else if (y > 1 && !st.isAir() && c.get(x, y + 1, z) == null && st.getFluidState().isEmpty() && u < amount * 0.22
                    && !(b instanceof net.minecraft.block.DoorBlock)) {
                edits.add(new long[]{k, 2});
            }
        }
        for (long[] ed : edits) {
            long k = ed[0];
            int x = net.minecraft.util.math.BlockPos.unpackLongX(k), y = net.minecraft.util.math.BlockPos.unpackLongY(k),
                    z = net.minecraft.util.math.BlockPos.unpackLongZ(k);
            BlockState st = c.get(x, y, z);
            if (st == null) {
                continue;
            }
            if (ed[1] == 1) {
                c.set(x, y, z, s.weathered.get(st.getBlock()).getDefaultState());
            } else {
                c.set(x, y, z, Canvas.AIR);
                if (s.rubble != null && chance(0.35)) {
                    // fallen stone lands at the foot of the wall
                    int fx = x + ri(3) - 1, fz = z + ri(3) - 1;
                    for (int fy = y - 1; fy >= 0; fy--) {
                        if (c.solid(fx, fy, fz) && c.get(fx, fy + 1, fz) != null && c.get(fx, fy + 1, fz).isAir() && !c.locked(fx, fy + 1, fz)) {
                            c.set(fx, fy + 1, fz, s.rubble.getDefaultState());
                            break;
                        }
                    }
                }
            }
        }
        overgrow(amount, webs);
    }

    void overgrow(double amount, boolean webs) {
        List<long[]> spots = new ArrayList<>();
        for (var e : c.blocks().long2ObjectEntrySet()) {
            long k = e.getLongKey();
            BlockState st = e.getValue();
            if (!st.isAir()) {
                continue;
            }
            int x = net.minecraft.util.math.BlockPos.unpackLongX(k), y = net.minecraft.util.math.BlockPos.unpackLongY(k),
                    z = net.minecraft.util.math.BlockPos.unpackLongZ(k);
            if (c.locked(x, y, z)) {
                continue;
            }
            spots.add(new long[]{k});
        }
        for (long[] sp : spots) {
            long k = sp[0];
            int x = net.minecraft.util.math.BlockPos.unpackLongX(k), y = net.minecraft.util.math.BlockPos.unpackLongY(k),
                    z = net.minecraft.util.math.BlockPos.unpackLongZ(k);
            boolean onFloor = c.solid(x, y - 1, z) && !c.locked(x, y - 1, z);
            double u = r();
            if (onFloor && u < amount * 0.25) {
                if (s.mossCarpet != null && chance(0.6)) {
                    c.set(x, y, z, s.mossCarpet.getDefaultState());
                } else if (!s.plants.isEmpty()) {
                    Block p = pick(s.plants);
                    c.set(x, y - 1, z, s.moss.getDefaultState());
                    c.set(x, y, z, p.getDefaultState());
                }
            } else if (webs && u < 0.012 && c.solid(x, y + 1, z)) {
                c.set(x, y, z, Blocks.COBWEB.getDefaultState());
            } else if (s.vine == Blocks.VINE && u < amount * 0.06) {
                for (Direction d : Direction.Type.HORIZONTAL) {
                    if (c.solid(x + d.getOffsetX(), y, z + d.getOffsetZ())) {
                        c.set(x, y, z, Blocks.VINE.getDefaultState().with(net.minecraft.block.VineBlock.getFacingProperty(d), true));
                        break;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ puzzles

    public long at(int x, int y, int z) {
        return c.at(x, y, z);
    }

    /** Dials facing a direction, with a clue mural of glyph tiles elsewhere showing the answer in the same order. */
    public void dials(int[][] dials, Direction facing, int[][] clues, Direction clueFacing, long[] seals, boolean required) {
        int[] sol = new int[dials.length];
        List<Long> el = new ArrayList<>();
        for (int i = 0; i < dials.length; i++) {
            sol[i] = ri(6);
            int start = (sol[i] + 1 + ri(5)) % 6;
            c.fixed(dials[i][0], dials[i][1], dials[i][2], Style.facing(ExploreBlocks.RUNE_DIAL, facing).with(ExploreBlocks.SYMBOL, start));
            el.add(at(dials[i][0], dials[i][1], dials[i][2]));
            c.fixed(clues[i][0], clues[i][1], clues[i][2], Style.facing(ExploreBlocks.GLYPH_TILE, clueFacing).with(ExploreBlocks.SYMBOL, sol[i]));
        }
        c.puzzle("dial_match", el, sol, seals(seals), required);
    }

    /** Rune plates with 1..k notches, stepped in notch order. */
    public void plates(int[][] pos, long[] seals, boolean required) {
        int k = pos.length;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            order.add(i);
        }
        java.util.Collections.shuffle(order, c.rnd);
        int[] sol = new int[k];
        List<Long> el = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            int notches = order.indexOf(i) + 1; // element i carries this many notches
            sol[notches - 1] = i;
            c.fixed(pos[i][0], pos[i][1], pos[i][2], ExploreBlocks.RUNE_PLATE.getDefaultState().with(ExploreBlocks.NOTCHES, notches));
            el.add(at(pos[i][0], pos[i][1], pos[i][2]));
        }
        c.puzzle("plate_sequence", el, sol, seals(seals), required);
    }

    public void braziers(String type, int[][] pos, long[] seals, boolean required) {
        int k = pos.length;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            order.add(i);
        }
        java.util.Collections.shuffle(order, c.rnd);
        int[] sol = new int[k];
        List<Long> el = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            int tally = type.equals("brazier_order") ? order.indexOf(i) + 1 : 0;
            if (tally > 0) {
                sol[tally - 1] = i;
            }
            boolean lit = type.equals("extinguish");
            c.fixed(pos[i][0], pos[i][1], pos[i][2], ExploreBlocks.BRAZIER.getDefaultState().with(ExploreBlocks.LIT, lit)
                    .with(ExploreBlocks.TALLY, Math.min(4, tally)));
            el.add(at(pos[i][0], pos[i][1], pos[i][2]));
        }
        c.puzzle(type, el, sol, seals(seals), required);
    }

    public void offerings(int[][] pos, long[] seals, boolean required) {
        int[] sol = new int[pos.length];
        List<Long> el = new ArrayList<>();
        List<Integer> kinds = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
        java.util.Collections.shuffle(kinds, c.rnd);
        for (int i = 0; i < pos.length; i++) {
            sol[i] = kinds.get(i % 6);
            c.fixed(pos[i][0], pos[i][1], pos[i][2], ExploreBlocks.OFFERING_SOCKET.getDefaultState());
            el.add(at(pos[i][0], pos[i][1], pos[i][2]));
        }
        c.puzzle("offering", el, sol, seals(seals), required);
    }

    /** Three chimes of distinct sizes, struck shortest to tallest. */
    public void chimes(int[][] pos, long[] seals, boolean required) {
        List<Integer> sizes = new ArrayList<>(List.of(0, 1, 2));
        java.util.Collections.shuffle(sizes, c.rnd);
        int[] sol = new int[3];
        List<Long> el = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            c.fixed(pos[i][0], pos[i][1], pos[i][2], ExploreBlocks.CHIME.getDefaultState().with(ExploreBlocks.SIZE, sizes.get(i)));
            sol[sizes.get(i)] = i;
            el.add(at(pos[i][0], pos[i][1], pos[i][2]));
        }
        c.puzzle("chime_order", el, sol, seals(seals), required);
    }

    /** Wall levers with a clue row of slabs above (top slab = up, bottom slab = down). */
    public void levers(int[][] pos, Direction facing, long[] seals, boolean required) {
        int[] sol = new int[pos.length];
        List<Long> el = new ArrayList<>();
        boolean any = false;
        for (int i = 0; i < pos.length; i++) {
            sol[i] = ri(2);
            any |= sol[i] == 1;
        }
        if (!any) {
            sol[0] = 1;
        }
        for (int i = 0; i < pos.length; i++) {
            c.fixed(pos[i][0], pos[i][1], pos[i][2], ExploreBlocks.ANCIENT_LEVER.getDefaultState().with(LeverBlock.FACE, BlockFace.WALL)
                    .with(LeverBlock.FACING, facing).with(LeverBlock.POWERED, false));
            int bx = pos[i][0] - facing.getOffsetX(), bz = pos[i][2] - facing.getOffsetZ();
            c.fixed(bx, pos[i][1] + 2, bz, Style.slab(s.trimSlab, sol[i] == 1));
            el.add(at(pos[i][0], pos[i][1], pos[i][2]));
        }
        c.puzzle("lever_pattern", el, sol, seals(seals), required);
    }

    public void basins(int[][] pos, long[] seals, boolean required) {
        List<Long> el = new ArrayList<>();
        for (int[] p : pos) {
            c.fixed(p[0], p[1], p[2], ExploreBlocks.STONE_BASIN.getDefaultState());
            el.add(at(p[0], p[1], p[2]));
        }
        c.puzzle("basin_fill", el, new int[0], seals(seals), required);
    }

    /**
     * A light-beam puzzle along a path of turns: lens at path[0] shooting in dir, mirrors at path[1..n-2] (their correct
     * slant is derived from the turn), receiver at the last point. Mirrors start wrong at random.
     */
    public void beam(int[][] path, int y, Direction dir, long[] seals, boolean required) {
        List<Long> el = new ArrayList<>();
        c.fixed(path[0][0], y, path[0][1], ExploreBlocks.LIGHT_LENS.getDefaultState());
        el.add(at(path[0][0], y, path[0][1]));
        int last = path.length - 1;
        c.fixed(path[last][0], y, path[last][1], ExploreBlocks.LIGHT_RECEIVER.getDefaultState());
        el.add(at(path[last][0], y, path[last][1]));
        for (int i = 1; i < last; i++) {
            int inX = Integer.signum(path[i][0] - path[i - 1][0]), inZ = Integer.signum(path[i][1] - path[i - 1][1]);
            int outX = Integer.signum(path[i + 1][0] - path[i][0]), outZ = Integer.signum(path[i + 1][1] - path[i][1]);
            boolean slantTrue = outX == inZ && outZ == inX; // "\": (dx,dz) -> (dz,dx)
            boolean start = chance(0.75) != slantTrue;
            c.fixed(path[i][0], y, path[i][1], ExploreBlocks.LIGHT_MIRROR.getDefaultState().with(ExploreBlocks.SLANT, start));
            el.add(at(path[i][0], y, path[i][1]));
            // clear the beam's path between turns
        }
        for (int i = 0; i < last; i++) {
            int x0 = path[i][0], z0 = path[i][1], x1 = path[i + 1][0], z1 = path[i + 1][1];
            int len = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
            for (int t = 1; t < len; t++) {
                int x = x0 + Integer.signum(x1 - x0) * t, z = z0 + Integer.signum(z1 - z0) * t;
                if (!c.locked(x, y, z)) {
                    c.set(x, y, z, Canvas.AIR);
                    c.lock(x, y, z);
                }
            }
        }
        c.puzzle("mirror_beam", el, new int[]{dir.getHorizontal()}, seals(seals), required);
    }

    public void twinPlates(int[] a, int[] b, long[] seals, boolean required) {
        c.fixed(a[0], a[1], a[2], ExploreBlocks.HEAVY_PLATE.getDefaultState());
        c.fixed(b[0], b[1], b[2], ExploreBlocks.HEAVY_PLATE.getDefaultState());
        c.puzzle("twin_plates", List.of(at(a[0], a[1], a[2]), at(b[0], b[1], b[2])), new int[0], seals(seals), required);
    }

    public void memory(int[] pillar, int[][] stones, long[] seals, boolean required) {
        List<Long> el = new ArrayList<>();
        c.fixed(pillar[0], pillar[1], pillar[2], ExploreBlocks.MEMORY_PILLAR.getDefaultState());
        el.add(at(pillar[0], pillar[1], pillar[2]));
        List<Integer> syms = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
        java.util.Collections.shuffle(syms, c.rnd);
        for (int i = 0; i < stones.length; i++) {
            c.fixed(stones[i][0], stones[i][1], stones[i][2], ExploreBlocks.MEMORY_STONE.getDefaultState().with(ExploreBlocks.SYMBOL, syms.get(i)));
            el.add(at(stones[i][0], stones[i][1], stones[i][2]));
        }
        int[] sol = new int[4];
        for (int i = 0; i < 4; i++) {
            sol[i] = 1 + ri(stones.length);
            if (i > 0 && sol[i] == sol[i - 1]) {
                sol[i] = 1 + (sol[i] % stones.length);
            }
        }
        c.puzzle("memory", el, sol, seals(seals), required);
    }

    /**
     * A mosaic wall (along x at z, facing south) with a repeating two-colour pattern and gaps; a keystone checks it.
     * Spare tiles lie in a nearby barrel (and the pattern's own tiles can be prised loose).
     */
    public void mosaic(int x0, int y0, int z, int w, int h, int[] keystone, int[] spareBarrel, long[] seals, boolean required) {
        int a = ri(6), b = (a + 1 + ri(5)) % 6;
        List<Long> el = new ArrayList<>();
        c.fixed(keystone[0], keystone[1], keystone[2], ExploreBlocks.KEYSTONE.getDefaultState());
        el.add(at(keystone[0], keystone[1], keystone[2]));
        List<Integer> sol = new ArrayList<>();
        sol.add(0);
        int gaps = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int col = ((x + y) & 1) == 0 ? a : b;
                boolean gap = gaps < 3 && (x * 7 + y * 3 + ri(5)) % 9 == 0 && x > 0;
                if (gap) {
                    c.set(x0 + x, y0 + y, z, Canvas.AIR);
                    c.lock(x0 + x, y0 + y, z);
                    c.set(x0 + x, y0 + y, z - 1, ExploreBlocks.MOSAIC_SLOT.getDefaultState());
                    el.add(at(x0 + x, y0 + y, z));
                    sol.add(col);
                    gaps++;
                } else {
                    c.fixed(x0 + x, y0 + y, z, dev.underdeep.explore.Mechanisms.MOSAIC.get(col).getDefaultState());
                }
            }
        }
        if (gaps == 0) {
            c.set(x0 + 1, y0, z, Canvas.AIR);
            c.lock(x0 + 1, y0, z);
            c.set(x0 + 1, y0, z - 1, ExploreBlocks.MOSAIC_SLOT.getDefaultState());
            el.add(at(x0 + 1, y0, z));
            sol.add(((1 + y0 * 0) & 1) == 0 ? a : b);
        }
        barrelLoot(spareBarrel[0], spareBarrel[1], spareBarrel[2], "underdeep:exploration/mosaic_" + a + "_" + b);
        c.puzzle("mosaic", el, sol.stream().mapToInt(Integer::intValue).toArray(), seals(seals), required);
    }

    public void timedGate(int[][] levers, Direction facing, int[][] bars) {
        List<Long> el = new ArrayList<>(), sl = new ArrayList<>();
        for (int[] l : levers) {
            c.fixed(l[0], l[1], l[2], ExploreBlocks.ANCIENT_LEVER.getDefaultState().with(LeverBlock.FACE, BlockFace.FLOOR).with(LeverBlock.FACING, facing));
            el.add(at(l[0], l[1], l[2]));
        }
        for (int[] b : bars) {
            c.fixed(b[0], b[1], b[2], ExploreBlocks.SEAL_BARS.getDefaultState());
            sl.add(at(b[0], b[1], b[2]));
        }
        c.puzzle("timed_gate", el, new int[0], sl, false);
    }

    public void engine(int[] pos, long[] seals, boolean required) {
        c.fixed(pos[0], pos[1], pos[2], ExploreBlocks.DORMANT_ENGINE.getDefaultState());
        c.puzzle("power_restore", List.of(at(pos[0], pos[1], pos[2])), new int[0], seals(seals), required);
    }

    public void counterweight(int[] pos, long[] seals, boolean required) {
        int[] options = {4, 8, 16};
        c.fixed(pos[0], pos[1], pos[2], ExploreBlocks.COUNTERWEIGHT.getDefaultState());
        c.puzzle("counterweight", List.of(at(pos[0], pos[1], pos[2])), new int[]{options[ri(3)]}, seals(seals), required);
    }

    public void keyLock(int[] lock, int[] pedestal, long[] seals, boolean required) {
        c.fixed(lock[0], lock[1], lock[2], ExploreBlocks.KEY_LOCK.getDefaultState());
        List<Long> sl = new ArrayList<>(seals(seals));
        sl.add(at(lock[0], lock[1], lock[2]));
        c.puzzle("key_lock", List.of(at(lock[0], lock[1], lock[2])), new int[0], sl, required);
        keyPedestal(pedestal[0], pedestal[1], pedestal[2]);
    }

    /** A sealed doorway (2 wide x 3 high) in a wall along x (alongX) or z; returns the seal positions. */
    public long[] sealDoor(int x, int y, int z, boolean alongX, int width) {
        long[] out = new long[width * 3];
        int i = 0;
        for (int k = 0; k < width; k++) {
            for (int h = 0; h < 3; h++) {
                int px = alongX ? x + k : x, pz = alongX ? z : z + k;
                c.fixed(px, y + h, pz, ExploreBlocks.ANCIENT_SEAL.getDefaultState());
                out[i++] = at(px, y + h, pz);
            }
        }
        return out;
    }

    static List<Long> seals(long[] a) {
        List<Long> l = new ArrayList<>();
        if (a != null) {
            for (long v : a) {
                l.add(v);
            }
        }
        return l;
    }

    // ------------------------------------------------------------------ traps

    /** A trap plate wired to effectors. */
    public void trap(String type, int[][] plates, int[][] effectors) {
        List<Long> tr = new ArrayList<>(), ef = new ArrayList<>();
        for (int[] p : plates) {
            c.fixed(p[0], p[1], p[2], ExploreBlocks.TRAP_PLATE.getDefaultState());
            tr.add(at(p[0], p[1], p[2]));
        }
        for (int[] e : effectors) {
            ef.add(at(e[0], e[1], e[2]));
        }
        c.trap(type, tr, ef);
    }

    public void dartVent(int x, int y, int z, Direction facing) {
        c.fixed(x, y, z, Style.facing(ExploreBlocks.DART_VENT, facing));
    }

    public void vent(Block kind, int x, int y, int z) {
        c.fixed(x, y, z, kind.getDefaultState());
        c.set(x, y + 1, z, Canvas.AIR);
    }

    public void crumbling(int x1, int z1, int x2, int z2, int y) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                c.fixed(x, y, z, ExploreBlocks.CRUMBLING_FLOOR.getDefaultState());
            }
        }
    }
}
