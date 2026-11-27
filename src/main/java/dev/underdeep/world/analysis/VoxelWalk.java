package dev.underdeep.world.analysis;

import java.util.ArrayDeque;

/**
 * Bounded survival-movement reachability over real blocks (or the terrain model): a player-sized body (2 blocks tall)
 * standing on solid ground or swimming, stepping up at most 1 block, dropping at most {@link #MAX_DROP} blocks
 * (no fall damage worth mentioning), never through lava. Used to verify that cave routes are physically
 * traversable — not just connected in a graph.
 */
public final class VoxelWalk {
    /** Block kinds as seen by the walker. */
    public static final byte SOLID = 0, AIR = 1, WATER = 2, LAVA = 3;
    public static final int MAX_DROP = 3;

    /** Supplies block kinds in absolute coordinates. */
    public interface Blocks {
        byte kind(int x, int y, int z);
    }

    public record Result(boolean reached, int visited, int pathLength, int[] closest, double closestDist) {
    }

    private VoxelWalk() {
    }

    /**
     * Breadth-first search from {@code start} (feet position) to within {@code tolerance} blocks of {@code goal},
     * inside the box [min, max]. Returns whether the goal was reached and the closest position found.
     */
    public static Result walk(Blocks b, int[] min, int[] max, int[] start, int[] goal, int tolerance) {
        int sx = max[0] - min[0] + 1, sy = max[1] - min[1] + 1, sz = max[2] - min[2] + 1;
        byte[] k = new byte[sx * sy * sz];
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                for (int y = 0; y < sy; y++) {
                    k[(x * sz + z) * sy + y] = b.kind(min[0] + x, min[1] + y, min[2] + z);
                }
            }
        }
        int[] dist = new int[k.length];
        java.util.Arrays.fill(dist, -1);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        int[] s = settle(k, sx, sy, sz, start[0] - min[0], start[1] - min[1], start[2] - min[2]);
        if (s == null) {
            return new Result(false, 0, -1, start, Double.MAX_VALUE);
        }
        int si = (s[0] * sz + s[2]) * sy + s[1];
        dist[si] = 0;
        q.add(si);
        int gx = goal[0] - min[0], gy = goal[1] - min[1], gz = goal[2] - min[2];
        double best = Double.MAX_VALUE;
        int bestI = si;
        int visited = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int cur = q.poll();
            visited++;
            int y = cur % sy, xz = cur / sy, z = xz % sz, x = xz / sz;
            double d = Math.sqrt((double) (x - gx) * (x - gx) + (double) (y - gy) * (y - gy) + (double) (z - gz) * (z - gz));
            if (d < best) {
                best = d;
                bestI = cur;
            }
            if (d <= tolerance) {
                return new Result(true, visited, dist[cur], new int[]{x + min[0], y + min[1], z + min[2]}, d);
            }
            boolean swimming = at(k, sx, sy, sz, x, y, z) == WATER;
            for (int[] dd : dirs) {
                int nx = x + dd[0], nz = z + dd[1];
                if (nx < 0 || nz < 0 || nx >= sx || nz >= sz) {
                    continue;
                }
                for (int dy = 1; dy >= -MAX_DROP; dy--) {
                    int ny = y + dy;
                    if (ny < 1 || ny + 1 >= sy) {
                        continue;
                    }
                    if (!open(at(k, sx, sy, sz, nx, ny, nz)) || !open(at(k, sx, sy, sz, nx, ny + 1, nz))) {
                        continue;
                    }
                    if (dy == 1 && !open(at(k, sx, sy, sz, x, y + 2, z))) {
                        continue; // head room to step up
                    }
                    if (dy < 0) { // the column we fall through must be open
                        boolean clear = true;
                        for (int yy = ny + 2; yy <= y + 1 && clear; yy++) {
                            clear = open(at(k, sx, sy, sz, nx, yy, nz));
                        }
                        if (!clear) {
                            continue;
                        }
                    }
                    byte below = at(k, sx, sy, sz, nx, ny - 1, nz);
                    boolean stand = below == SOLID || below == WATER || at(k, sx, sy, sz, nx, ny, nz) == WATER;
                    if (!stand) {
                        continue;
                    }
                    int ni = (nx * sz + nz) * sy + ny;
                    if (dist[ni] < 0) {
                        dist[ni] = dist[cur] + 1;
                        q.add(ni);
                    }
                    break;
                }
            }
            if (swimming) { // swim up and down
                for (int dy = -1; dy <= 1; dy += 2) {
                    int ny = y + dy;
                    if (ny >= 1 && ny + 1 < sy && open(at(k, sx, sy, sz, x, ny, z)) && open(at(k, sx, sy, sz, x, ny + 1, z))) {
                        int ni = (x * sz + z) * sy + ny;
                        if (dist[ni] < 0) {
                            dist[ni] = dist[cur] + 1;
                            q.add(ni);
                        }
                    }
                }
            }
        }
        int y = bestI % sy, xz = bestI / sy, z = xz % sz, x = xz / sz;
        return new Result(false, visited, -1, new int[]{x + min[0], y + min[1], z + min[2]}, best);
    }

    private static boolean open(byte b) {
        return b == AIR || b == WATER;
    }

    private static byte at(byte[] k, int sx, int sy, int sz, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
            return SOLID;
        }
        return k[(x * sz + z) * sy + y];
    }

    /** Moves a start position down to the ground (or up out of rock) within a few blocks. */
    private static int[] settle(byte[] k, int sx, int sy, int sz, int x, int y, int z) {
        for (int dy = 0; dy < 12; dy++) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                int yy = y + sgn * dy;
                if (yy >= 1 && yy + 1 < sy && open(at(k, sx, sy, sz, x, yy, z)) && open(at(k, sx, sy, sz, x, yy + 1, z))
                        && at(k, sx, sy, sz, x, yy - 1, z) != AIR) {
                    return new int[]{x, yy, z};
                }
            }
        }
        return null;
    }
}
