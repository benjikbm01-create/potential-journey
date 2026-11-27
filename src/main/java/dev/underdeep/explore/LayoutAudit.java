package dev.underdeep.explore;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Static validation of an authored layout (Stage 9, development diagnostics): walkability flood fill from the
 * footprint edge (positions the design never writes are assumed to be open cavern above the floor and rock below
 * it, which the placement fit guarantees), with seals open; then checks that every puzzle element, cache, inscription,
 * hidden room and the heart can be reached, and that each required puzzle's elements are reachable without passing its
 * own seals. Also checks the puzzle data is consistent (element blocks, solutions in range, a light path that works).
 */
public final class LayoutAudit {
    private LayoutAudit() {
    }

    public record Result(List<String> problems, int reachable) {
    }

    public static Result audit(Canvas c) {
        List<String> problems = new ArrayList<>();
        Set<Long> allSeals = new HashSet<>();
        c.puzzles.forEach(p -> allSeals.addAll(p.seals()));
        LongOpenHashSet open = flood(c, Set.of());
        // puzzle data and element reachability
        for (int i = 0; i < c.puzzles.size(); i++) {
            Canvas.PuzzleSpec p = c.puzzles.get(i);
            Set<Long> own = new HashSet<>(p.seals());
            LongOpenHashSet withoutOwn = p.required() ? flood(c, own) : open;
            for (int e = 0; e < p.elements().size(); e++) {
                long el = p.elements().get(e);
                if (p.type().equals("mosaic") && e > 0 || p.type().equals("mirror_beam")) {
                    continue; // mosaic gaps and beam parts are reached from the room around them
                }
                if (!near(withoutOwn, el)) {
                    problems.add(p.type() + "#" + i + " element " + e + " at " + str(el) + " not reachable" + (p.required() ? " before its own seal opens" : ""));
                }
            }
            for (long s : p.seals()) {
                BlockState st = c.blocks().get(s);
                if (st == null || !(st.isOf(ExploreBlocks.ANCIENT_SEAL) || st.isOf(ExploreBlocks.SEAL_BARS) || st.isOf(ExploreBlocks.KEY_LOCK))) {
                    problems.add(p.type() + "#" + i + " seal at " + str(s) + " is " + (st == null ? "unset" : st.getBlock()));
                }
            }
            problems.addAll(data(c, p, i));
        }
        for (int i = 0; i < c.caches.size(); i++) {
            if (!near(open, c.caches.get(i).pos())) {
                problems.add("cache " + i + " (" + c.caches.get(i).policy() + ") at " + str(c.caches.get(i).pos()) + " not reachable");
            }
        }
        for (long ins : c.inscriptions.keySet()) {
            if (!near(open, ins)) {
                problems.add("inscription at " + str(ins) + " not reachable");
            }
        }
        for (Canvas.SecretSpec s : c.secrets) {
            if (!boxReached(open, s.box())) {
                problems.add("secret " + s.id() + " not reachable");
            }
        }
        if (c.heart != null && !boxReached(open, c.heart)) {
            problems.add("heart not reachable");
        }
        for (Canvas.TrapSpec t : c.traps) {
            for (long e : t.effectors()) {
                if (c.blocks().get(e) == null) {
                    problems.add("trap " + t.type() + " effector unset at " + str(e));
                }
            }
        }
        return new Result(problems, open.size());
    }

    static List<String> data(Canvas c, Canvas.PuzzleSpec p, int i) {
        List<String> out = new ArrayList<>();
        int[] sol = p.solution();
        switch (p.type()) {
            case "dial_match" -> {
                for (int v : sol) {
                    if (v < 0 || v > 5) {
                        out.add("dial solution out of range");
                    }
                }
            }
            case "plate_sequence", "chime_order", "brazier_order" -> {
                Set<Integer> seen = new HashSet<>();
                for (int v : sol) {
                    if (v < 0 || v >= p.elements().size() || !seen.add(v)) {
                        out.add(p.type() + "#" + i + " bad order " + java.util.Arrays.toString(sol));
                    }
                }
                if (sol.length != p.elements().size()) {
                    out.add(p.type() + "#" + i + " order length " + sol.length + " for " + p.elements().size() + " elements");
                }
            }
            case "memory" -> {
                for (int v : sol) {
                    if (v < 1 || v >= p.elements().size()) {
                        out.add("memory solution refers to element " + v);
                    }
                }
            }
            case "lever_pattern" -> {
                if (sol.length != p.elements().size()) {
                    out.add("lever pattern length mismatch");
                }
            }
            case "mirror_beam" -> {
                if (!beamWorks(c, p)) {
                    out.add("mirror_beam#" + i + ": the beam does not reach the receiver even with the right mirror angles");
                }
            }
            case "counterweight" -> {
                if (sol.length != 1 || sol[0] <= 0) {
                    out.add("counterweight without a weight");
                }
            }
            default -> {
            }
        }
        for (int e = 0; e < p.elements().size(); e++) {
            BlockState st = c.blocks().get(p.elements().get(e));
            boolean slot = p.type().equals("mosaic") && e > 0;
            if (!slot && (st == null || st.isAir())) {
                out.add(p.type() + "#" + i + " element " + e + " has no block");
            }
        }
        return out;
    }

    /** Simulates the beam in the canvas assuming every mirror can be set right; true when the receiver is reached. */
    static boolean beamWorks(Canvas c, Canvas.PuzzleSpec p) {
        long lens = p.elements().get(0);
        int x = BlockPos.unpackLongX(lens), y = BlockPos.unpackLongY(lens), z = BlockPos.unpackLongZ(lens);
        Direction d = Direction.fromHorizontal(p.solution()[0]);
        Set<Long> mirrors = new HashSet<>(p.elements().subList(2, p.elements().size()));
        long receiver = p.elements().get(1);
        // with free choice of each mirror, a beam can turn left or right at a mirror: search
        return search(c, x, y, z, d, mirrors, receiver, 0, new HashSet<>());
    }

    private static boolean search(Canvas c, int x, int y, int z, Direction d, Set<Long> mirrors, long receiver, int depth, Set<Long> used) {
        if (depth > 8) {
            return false;
        }
        for (int i = 0; i < 48; i++) {
            x += d.getOffsetX();
            z += d.getOffsetZ();
            long k = Canvas.key(x, y, z);
            if (k == receiver) {
                return true;
            }
            if (mirrors.contains(k) && !used.contains(k)) {
                used.add(k);
                for (Direction nd : new Direction[]{d.rotateYClockwise(), d.rotateYCounterclockwise()}) {
                    if (search(c, x, y, z, nd, mirrors, receiver, depth + 1, used)) {
                        return true;
                    }
                }
                used.remove(k);
                return false;
            }
            BlockState s = c.blocks().get(k);
            if (s != null && !s.isAir() && s.isOpaque()) {
                return false;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ walkability

    static BlockState at(Canvas c, int x, int y, int z) {
        BlockState s = c.blocks().get(Canvas.key(x, y, z));
        if (s != null) {
            return s;
        }
        return y >= 1 ? Blocks.AIR.getDefaultState() : Blocks.STONE.getDefaultState();
    }

    static final ThreadLocal<Canvas> CURRENT = new ThreadLocal<>();

    static boolean passable(BlockState s, boolean sealsOpen, long k, Set<Long> closed) {
        Canvas cur = CURRENT.get();
        if (cur != null && cur.breakable.contains(k)) {
            return true;
        }
        if (s.isOf(ExploreBlocks.ANCIENT_SEAL) || s.isOf(ExploreBlocks.SEAL_BARS) || s.isOf(ExploreBlocks.KEY_LOCK)) {
            return sealsOpen && !closed.contains(k);
        }
        return s.isAir() || !s.getFluidState().isEmpty() || s.getBlock() instanceof LadderBlock || s.getBlock() instanceof VineBlock
                || s.getBlock() instanceof ScaffoldingBlock || s.isIn(BlockTags.DOORS) || s.isIn(BlockTags.TRAPDOORS) || s.isIn(BlockTags.REPLACEABLE)
                || s.isIn(BlockTags.FLOWERS) || s.isIn(BlockTags.SAPLINGS) || s.isIn(BlockTags.WOOL_CARPETS) || s.isOf(Blocks.MOSS_CARPET)
                || s.isOf(Blocks.SNOW) || s.isIn(BlockTags.RAILS) || s.isOf(Blocks.COBWEB) || s.isOf(Blocks.CHAIN) || s.isOf(Blocks.LADDER)
                || s.isOf(ExploreBlocks.RUNE_PLATE) || s.isOf(ExploreBlocks.HEAVY_PLATE) || s.isOf(ExploreBlocks.TRAP_PLATE) || s.isOf(Blocks.LEVER)
                || s.isOf(ExploreBlocks.ANCIENT_LEVER) || s.getBlock() instanceof net.minecraft.block.PlantBlock || s.isOf(Blocks.SCULK_VEIN)
                || s.isOf(Blocks.GLOW_LICHEN) || s.isIn(BlockTags.CANDLES) || s.getBlock() instanceof net.minecraft.block.MushroomPlantBlock
                || s.isOf(Blocks.SKELETON_SKULL) || s.isOf(Blocks.TORCH) || s.isOf(Blocks.LANTERN) || s.isOf(Blocks.SOUL_LANTERN)
                || s.getBlock() instanceof net.minecraft.block.AbstractPlantPartBlock;
    }

    static boolean climb(BlockState s) {
        return s.getBlock() instanceof LadderBlock || s.getBlock() instanceof VineBlock || s.getBlock() instanceof ScaffoldingBlock || !s.getFluidState().isEmpty();
    }

    static boolean stand(Canvas c, int x, int y, int z, Set<Long> closed) {
        BlockState feet = at(c, x, y, z), head = at(c, x, y + 1, z), below = at(c, x, y - 1, z);
        if (!passable(feet, true, Canvas.key(x, y, z), closed) || !passable(head, true, Canvas.key(x, y + 1, z), closed)) {
            return false;
        }
        return climb(feet) || !passable(below, true, Canvas.key(x, y - 1, z), closed) || climb(below);
    }

    /** Flood fill of standing positions from the footprint edge (y in [1, 4]) with the given seals kept closed. */
    static LongOpenHashSet flood(Canvas c, Set<Long> closed) {
        CURRENT.set(c);
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue q = new LongArrayFIFOQueue();
        int n = c.n;
        for (int i = 0; i < n; i++) {
            for (int y = 1; y <= 4; y++) {
                for (int[] p : new int[][]{{i, 0}, {i, n - 1}, {0, i}, {n - 1, i}}) {
                    if (stand(c, p[0], y, p[1], closed) && seen.add(Canvas.key(p[0], y, p[1]))) {
                        q.enqueue(Canvas.key(p[0], y, p[1]));
                    }
                }
            }
        }
        int minY = c.minY() - 1, maxY = c.maxY() + 2;
        while (!q.isEmpty()) {
            long k = q.dequeueLong();
            int x = BlockPos.unpackLongX(k), y = BlockPos.unpackLongY(k), z = BlockPos.unpackLongZ(k);
            boolean climbing = climb(at(c, x, y, z));
            for (Direction d : Direction.Type.HORIZONTAL) {
                int nx = x + d.getOffsetX(), nz = z + d.getOffsetZ();
                if (nx < 0 || nz < 0 || nx >= n || nz >= n) {
                    continue;
                }
                // walk level, step up one, or drop up to four
                for (int dy : new int[]{0, 1, -1, -2, -3, -4}) {
                    int ny = y + dy;
                    if (ny < minY || ny > maxY) {
                        continue;
                    }
                    if (dy == 1 && !passable(at(c, x, y + 2, z), true, Canvas.key(x, y + 2, z), closed)) {
                        continue;
                    }
                    if (dy < 0) {
                        boolean clearFall = true;
                        for (int f = 0; f > dy; f--) {
                            if (!passable(at(c, nx, y + f, nz), true, Canvas.key(nx, y + f, nz), closed)) {
                                clearFall = false;
                                break;
                            }
                        }
                        if (!clearFall) {
                            continue;
                        }
                    }
                    if (stand(c, nx, ny, nz, closed)) {
                        long nk = Canvas.key(nx, ny, nz);
                        if (seen.add(nk)) {
                            q.enqueue(nk);
                        }
                        break;
                    }
                }
            }
            // ladders, vines, water: vertical movement
            for (int dy : new int[]{1, -1}) {
                int ny = y + dy;
                if (ny < minY || ny > maxY) {
                    continue;
                }
                boolean can = dy == 1 ? climbing && passable(at(c, x, y + 2, z), true, Canvas.key(x, y + 2, z), closed)
                        : passable(at(c, x, y - 1, z), true, Canvas.key(x, y - 1, z), closed);
                if (can && stand(c, x, ny, z, closed)) {
                    long nk = Canvas.key(x, ny, z);
                    if (seen.add(nk)) {
                        q.enqueue(nk);
                    }
                } else if (can && dy == -1) {
                    // falling straight down through an opening (up to four blocks)
                    for (int f = 2; f <= 5 && y - f >= minY; f++) {
                        if (!passable(at(c, x, y - f + 1, z), true, Canvas.key(x, y - f + 1, z), closed)) {
                            break;
                        }
                        if (stand(c, x, y - f, z, closed)) {
                            long nk = Canvas.key(x, y - f, z);
                            if (seen.add(nk)) {
                                q.enqueue(nk);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return seen;
    }

    /** A block is usable when a standing position is within reach (adjacent cell, feet or head height). */
    static boolean near(LongOpenHashSet open, long k) {
        int x = BlockPos.unpackLongX(k), y = BlockPos.unpackLongY(k), z = BlockPos.unpackLongZ(k);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) <= 3 && open.contains(Canvas.key(x + dx, y + dy, z + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean boxReached(LongOpenHashSet open, int[] b) {
        for (int x = b[0]; x <= b[3]; x++) {
            for (int y = b[1] - 1; y <= b[4]; y++) {
                for (int z = b[2]; z <= b[5]; z++) {
                    if (open.contains(Canvas.key(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** ASCII slices of a layout (development diagnostics): o = reachable standing spot. */
    public static String render(Canvas c) {
        LongOpenHashSet open = flood(c, Set.of());
        StringBuilder b = new StringBuilder();
        for (int y = c.maxY(); y >= c.minY(); y--) {
            b.append("y=").append(y).append('\n');
            for (int z = 0; z < c.n; z++) {
                for (int x = 0; x < c.n; x++) {
                    long k = Canvas.key(x, y, z);
                    BlockState s = c.blocks().get(k);
                    char ch;
                    if (open.contains(k)) {
                        ch = 'o';
                    } else if (s == null) {
                        ch = y >= 1 ? ' ' : ',';
                    } else if (s.isAir()) {
                        ch = '.';
                    } else if (s.isOf(ExploreBlocks.ANCIENT_SEAL) || s.isOf(ExploreBlocks.SEAL_BARS) || s.isOf(ExploreBlocks.KEY_LOCK)) {
                        ch = 'S';
                    } else if (!s.getFluidState().isEmpty()) {
                        ch = '~';
                    } else if (s.getBlock() instanceof LadderBlock) {
                        ch = 'H';
                    } else if (s.getBlock() instanceof net.minecraft.block.StairsBlock) {
                        ch = '=';
                    } else if (s.getBlock() instanceof ExploreBlocks.Mechanism || s.getBlock() instanceof ExploreBlocks.CacheBlock) {
                        ch = 'M';
                    } else if (passable(s, true, k, Set.of())) {
                        ch = '_';
                    } else {
                        ch = '#';
                    }
                    b.append(ch);
                }
                b.append('\n');
            }
        }
        return b.toString();
    }

    static String str(long k) {
        return BlockPos.unpackLongX(k) + "," + BlockPos.unpackLongY(k) + "," + BlockPos.unpackLongZ(k);
    }
}
