package dev.underdeep.explore;

import dev.underdeep.world.region.RegionType;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The authored layout of one site in local coordinates: a square footprint {@code [0,n)²} (so every rotation keeps
 * the same footprint), y = 0 is the floor course, negative y lies below the cavern floor (cellars, crypts, vaults).
 * Builders write blocks and register gameplay features (puzzles, traps, caches, inscriptions, secret rooms, guardian
 * rooms, completion box). Layouts are pure functions of the site (seed, family, region), so every chunk rebuilds the
 * same layout independently and the server finds a mechanism's puzzle from its position alone.
 * <p>
 * {@link #AIR} entries carve space (interiors are explicitly cleared); positions never written keep the terrain.
 */
public final class Canvas {
    public static final BlockState AIR = Blocks.AIR.getDefaultState();

    public final int n;
    public final RegionType region;
    public final Random rnd;
    public final Style style;
    final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
    /** Positions decay and overgrowth must never touch (mechanisms, seals, required floors, caches). */
    final LongOpenHashSet locked = new LongOpenHashSet();
    int minY, maxY;

    public final List<PuzzleSpec> puzzles = new ArrayList<>();
    public final List<TrapSpec> traps = new ArrayList<>();
    public final List<CacheSpec> caches = new ArrayList<>();
    public final Map<Long, String> inscriptions = new LinkedHashMap<>();
    public final List<SecretSpec> secrets = new ArrayList<>();
    public final List<GuardSpec> guards = new ArrayList<>();
    /** Stage 10 named markers (boss spawn, arena devices, altar, door, arena centre), in local coordinates. */
    public final Map<String, List<Long>> marks = new LinkedHashMap<>();

    public void mark(String kind, int x, int y, int z) {
        marks.computeIfAbsent(kind, k -> new ArrayList<>()).add(key(x, y, z));
    }
    /** Completion zone of dungeons (reaching it with every required puzzle solved completes the expedition). */
    public int[] heart;
    /** Optional Stage 10 reservation box (kept sealed, never populated in Stage 9). */
    public int[] reserved;

    public record PuzzleSpec(String type, List<Long> elements, int[] solution, List<Long> seals, boolean required) {
    }

    public record TrapSpec(String type, List<Long> triggers, List<Long> effectors) {
    }

    public enum Policy { SHARED, PERSONAL, UNIQUE, KEY }

    public record CacheSpec(long pos, Policy policy, String loot) {
    }

    public record SecretSpec(String id, int[] box) {
    }

    public record GuardSpec(String kind, int count, int[] box, List<Long> spawns) {
    }

    public Canvas(int n, RegionType region, Style style, long seed) {
        this.n = n;
        this.region = region;
        this.style = style;
        this.rnd = new Random(seed);
    }

    public static long key(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    // ------------------------------------------------------------------ raw writes

    /** Writes a block unless the position is locked (fixtures placed earlier win over later carving). */
    public void set(int x, int y, int z, BlockState s) {
        if (locked.contains(key(x, y, z))) {
            return;
        }
        force(x, y, z, s);
    }

    /** Writes a block even over a locked position. */
    public void force(int x, int y, int z, BlockState s) {
        if (s == null || x < 0 || z < 0 || x >= n || z >= n) {
            return;
        }
        blocks.put(key(x, y, z), s);
        minY = Math.min(minY, y);
        maxY = Math.max(maxY, y);
    }

    public void set(int x, int y, int z, Block b) {
        if (b != null) {
            set(x, y, z, b.getDefaultState());
        }
    }

    /** Writes only where nothing (or air) was written. */
    public void soft(int x, int y, int z, BlockState s) {
        BlockState c = get(x, y, z);
        if (c == null || c.isAir()) {
            set(x, y, z, s);
        }
    }

    /** Writes only where nothing at all was written (never closes carved passages). */
    public void weak(int x, int y, int z, BlockState s) {
        if (get(x, y, z) == null) {
            set(x, y, z, s);
        }
    }

    public void soft(int x, int y, int z, Block b) {
        soft(x, y, z, b.getDefaultState());
    }

    public BlockState get(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    public boolean solid(int x, int y, int z) {
        BlockState s = get(x, y, z);
        return s != null && !s.isAir() && s.getFluidState().isEmpty();
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, s);
                }
            }
        }
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        fill(x1, y1, z1, x2, y2, z2, b.getDefaultState());
    }

    public void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, AIR);
    }

    /** Blocks meant to be broken by players to enter a hidden place (a fair, visible clue: cracked, mossy, bones). */
    public final LongOpenHashSet breakable = new LongOpenHashSet();

    public void breakable(int x, int y, int z, BlockState s) {
        force(x, y, z, s);
        lock(x, y, z);
        breakable.add(key(x, y, z));
    }

    /** Marks positions so weathering, rubble and plants never change them. */
    public void lock(int x, int y, int z) {
        locked.add(key(x, y, z));
    }

    public void lockBox(int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    lock(x, y, z);
                }
            }
        }
    }

    public boolean locked(int x, int y, int z) {
        return locked.contains(key(x, y, z));
    }

    /** Sets and locks a block (mechanisms, seals, caches). */
    public void fixed(int x, int y, int z, BlockState s) {
        force(x, y, z, s);
        lock(x, y, z);
    }

    // ------------------------------------------------------------------ features

    public long at(int x, int y, int z) {
        return key(x, y, z);
    }

    public void puzzle(String type, List<Long> elements, int[] solution, List<Long> seals, boolean required) {
        puzzles.add(new PuzzleSpec(type, List.copyOf(elements), solution, List.copyOf(seals), required));
    }

    public void trap(String type, List<Long> triggers, List<Long> effectors) {
        traps.add(new TrapSpec(type, List.copyOf(triggers), List.copyOf(effectors)));
    }

    public void cache(int x, int y, int z, Policy policy, String loot) {
        caches.add(new CacheSpec(key(x, y, z), policy, loot));
        lock(x, y, z);
    }

    public void inscription(int x, int y, int z, String record) {
        inscriptions.put(key(x, y, z), record);
        lock(x, y, z);
    }

    public void secret(String id, int x1, int y1, int z1, int x2, int y2, int z2) {
        secrets.add(new SecretSpec(id, new int[]{Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2),
                Math.max(z1, z2)}));
    }

    public void guard(String kind, int count, int[] box, List<Long> spawns) {
        guards.add(new GuardSpec(kind, count, box, List.copyOf(spawns)));
    }

    public int blockCount() {
        return blocks.size();
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public Long2ObjectMap<BlockState> blocks() {
        return blocks;
    }

    // ------------------------------------------------------------------ rotation

    /** Local → offset from the footprint's minimum corner for a rotation (square footprint). */
    public static BlockPos transform(int n, int x, int y, int z, BlockRotation rot) {
        return switch (rot) {
            case NONE -> new BlockPos(x, y, z);
            case CLOCKWISE_90 -> new BlockPos(n - 1 - z, y, x);
            case CLOCKWISE_180 -> new BlockPos(n - 1 - x, y, n - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, n - 1 - x);
        };
    }

    /** World position of a packed local position on a site. */
    public static BlockPos world(Site s, long local) {
        BlockPos o = transform(s.size(), BlockPos.unpackLongX(local), BlockPos.unpackLongY(local), BlockPos.unpackLongZ(local), s.rotation());
        return s.origin().add(o);
    }

    /** World-space axis-aligned box of a local box. */
    public static int[] worldBox(Site s, int[] b) {
        BlockPos a = world(s, key(b[0], b[1], b[2])), c = world(s, key(b[3], b[4], b[5]));
        return new int[]{Math.min(a.getX(), c.getX()), Math.min(a.getY(), c.getY()), Math.min(a.getZ(), c.getZ()), Math.max(a.getX(), c.getX()),
                Math.max(a.getY(), c.getY()), Math.max(a.getZ(), c.getZ())};
    }

    public static boolean inBox(int[] b, double x, double y, double z) {
        return x >= b[0] && x < b[3] + 1 && y >= b[1] && y < b[4] + 1 && z >= b[2] && z < b[5] + 1;
    }
}
