package dev.underdeep.civ.arch;

import dev.underdeep.civ.Civilization;
import dev.underdeep.registry.ModPlants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * Civilization-specific exterior: plinth, wall pattern, windows, entrance, roof and ornament. Interiors are
 * shared ({@link Architect}); the shell is what makes a culture recognizable from its architecture alone.
 */
public final class Shells {
    final Civilization civ;
    final Palette p;

    Shells(Civilization civ, Palette p) {
        this.civ = civ;
        this.p = p;
    }

    /** Storey height in blocks (floor-to-floor). */
    public int floorHeight() {
        return switch (civ) {
            case FORGEKIN, GLIMMERKIN, ASTRALS -> 5;
            default -> 4;
        };
    }

    private boolean rounded() {
        return civ == Civilization.SPOREFOLK;
    }

    private boolean decked() {
        return civ == Civilization.TIDE_DWELLERS;
    }

    // ================================================================== body

    public void body(Builder b, int x1, int z1, int x2, int z2, int floors, int v) {
        body(b, x1, z1, x2, z2, floors, v, floorHeight());
    }

    /** Walls, floors and windows of a rectangular building. */
    public void body(Builder b, int x1, int z1, int x2, int z2, int floors, int v, int fh) {
        Blueprint bp = b.bp;
        if (decked()) {
            bp.stilts = true;
            // a plank deck one block wider than the walls, on log posts, with a railing (gap at the front)
            int mid = (x1 + x2) / 2;
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                for (int z = z1 - 1; z <= z2 + 1; z++) {
                    boolean edge = x == x1 - 1 || x == x2 + 1 || z == z1 - 1 || z == z2 + 1;
                    if (!edge) {
                        continue;
                    }
                    b.set(x, 0, z, p.planks);
                    if (((x - x1 + 1) % 3 == 0 || x == x2 + 1) && ((z - z1 + 1) % 3 == 0 || z == z2 + 1)) {
                        b.set(x, -1, z, b.axis(p.log, Direction.Axis.Y));
                        b.set(x, 0, z, b.axis(p.log, Direction.Axis.Y));
                    }
                    if (!(z == z1 - 1 && Math.abs(x - mid) <= 1)) {
                        b.set(x, 1, z, p.fence);
                    }
                }
            }
            for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
                b.set(c[0], -1, c[1], b.axis(p.log, Direction.Axis.Y));
            }
        }
        int top = floors * fh;
        // floor and footing
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                if (rounded() && corner(x, z, x1, z1, x2, z2)) {
                    continue;
                }
                b.set(x, 0, z, edge && !decked() ? p.foundation : p.floor);
                if (!decked()) {
                    b.set(x, -1, z, p.foundation);
                }
            }
        }
        // interior air
        for (int x = x1 + 1; x < x2; x++) {
            for (int z = z1 + 1; z < z2; z++) {
                for (int y = 1; y < top; y++) {
                    b.soft(x, y, z, Builder.AIR);
                }
                for (int f = 1; f < floors; f++) {
                    b.set(x, f * fh, z, p.floor);
                }
            }
        }
        // walls
        for (int y = 1; y <= top; y++) {
            int level = (y - 1) % fh;
            boolean plate = y % fh == 0;
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    if (!(x == x1 || x == x2 || z == z1 || z == z2)) {
                        continue;
                    }
                    if (rounded() && corner(x, z, x1, z1, x2, z2)) {
                        if (y <= top) {
                            continue;
                        }
                    }
                    b.set(x, y, z, wallAt(b, x, y, z, x1, z1, x2, z2, level, plate, v));
                }
            }
        }
        // windows on every storey
        for (int f = 0; f < floors; f++) {
            int wy = 2 + f * fh;
            int rows = civ == Civilization.ASTRALS ? 3 : fh >= 5 ? 2 : (civ == Civilization.AMBERFOLK || civ == Civilization.TIDE_DWELLERS ? 2 : 1);
            int step = civ == Civilization.FORGEKIN || civ == Civilization.STONEBORN ? 4 : civ == Civilization.GLIMMERKIN || civ == Civilization.ASTRALS ? 2 : 3;
            for (int x = x1 + 2; x <= x2 - 2; x += step) {
                if (Math.abs(x - (x1 + x2) / 2) > 1 || f > 0) {
                    window(b, x, wy, z1, rows, Direction.NORTH);
                }
                window(b, x, wy, z2, rows, Direction.SOUTH);
            }
            for (int z = z1 + 2; z <= z2 - 2; z += step) {
                window(b, x1, wy, z, rows, Direction.WEST);
                window(b, x2, wy, z, rows, Direction.EAST);
            }
        }
        bodyTop = top;
        bx1 = x1;
        bz1 = z1;
        bx2 = x2;
        bz2 = z2;
    }

    private int bodyTop, bx1, bz1, bx2, bz2;

    private static boolean corner(int x, int z, int x1, int z1, int x2, int z2) {
        return (x == x1 || x == x2) && (z == z1 || z == z2);
    }

    /** Wall block by culture: framing rhythm, base courses and top plates. */
    private BlockState wallAt(Builder b, int x, int y, int z, int x1, int z1, int x2, int z2, int level, boolean plate, int v) {
        boolean corner = corner(x, z, x1, z1, x2, z2);
        int along = (z == z1 || z == z2) ? x - x1 : z - z1;
        Direction.Axis wallAxis = (z == z1 || z == z2) ? Direction.Axis.X : Direction.Axis.Z;
        switch (civ) {
            case ROOTFOLK -> {
                if (corner) {
                    return b.axis(p.frame, Direction.Axis.Y);
                }
                if (plate) {
                    return b.axis(p.frame, wallAxis);
                }
                if (level == 0) {
                    return p.wallAlt.getDefaultState();
                }
                return (v & 1) == 1 && along % 4 == 0 ? b.axis(p.frameAxis, Direction.Axis.Y) : p.wall.getDefaultState();
            }
            case FORGEKIN -> {
                if (corner || along % 4 == 0) {
                    return b.axis(p.frameAxis, Direction.Axis.Y);
                }
                if (plate) {
                    return p.trim.getDefaultState();
                }
                return level == 0 ? p.wallAlt.getDefaultState() : p.wall.getDefaultState();
            }
            case TIDE_DWELLERS -> {
                if (corner) {
                    return b.axis(p.frame, Direction.Axis.Y);
                }
                if (plate) {
                    return b.axis(p.frameAxis, wallAxis);
                }
                return (v & 1) == 0 ? p.wall.getDefaultState() : (level % 2 == 0 ? p.wall : p.wallAlt).getDefaultState();
            }
            case SPOREFOLK -> {
                if (plate) {
                    return p.wallAlt.getDefaultState();
                }
                return along % 5 == 2 && level == 0 ? p.floorAccent.getDefaultState() : p.wall.getDefaultState();
            }
            case GLIMMERKIN -> {
                if (corner || along % 3 == 0) {
                    return b.axis(p.frame, Direction.Axis.Y);
                }
                if (plate) {
                    return p.trim.getDefaultState();
                }
                return level == 0 && (v & 1) == 1 ? p.wallAlt.getDefaultState() : p.wall.getDefaultState();
            }
            case STONEBORN -> {
                if (corner) {
                    return (y % 2 == 0 ? p.frame : p.wallAlt).getDefaultState();
                }
                if (plate) {
                    return p.wallAlt.getDefaultState();
                }
                return y <= 2 ? p.wallAlt.getDefaultState() : p.wall.getDefaultState();
            }
            case AMBERFOLK -> {
                if (corner || along % 4 == 0) {
                    return b.axis(p.frame, Direction.Axis.Y);
                }
                if (plate) {
                    return b.axis(p.frame, wallAxis);
                }
                if (level == 0 && y <= floorHeight() && (v & 2) != 0) {
                    return p.wallAlt.getDefaultState();
                }
                return p.wall.getDefaultState();
            }
            case ASTRALS -> {
                if (corner) {
                    return b.axis(p.frameAxis, Direction.Axis.Y);
                }
                if (plate) {
                    return p.trim.getDefaultState();
                }
                return along % 4 == 2 ? b.axis(p.frameAxis, Direction.Axis.Y) : p.wall.getDefaultState();
            }
        }
        return p.wall.getDefaultState();
    }

    private void window(Builder b, int x, int y, int z, int rows, Direction out) {
        BlockState cur = b.get(x, y, z);
        if (cur == null || cur.isOf(p.frame) || cur.isOf(p.frameAxis) && civ != Civilization.ROOTFOLK) {
            return;
        }
        b.window(x, y, z, rows);
        switch (civ) {
            case STONEBORN -> {
                // shutters
                int sx = x + out.getOffsetX(), sz = z + out.getOffsetZ();
                Direction side = out.rotateYClockwise();
                b.soft(sx + side.getOffsetX(), y, sz + side.getOffsetZ(), b.trapdoor(p.trapdoor, side, false, true));
            }
            case AMBERFOLK, ROOTFOLK -> {
                // flower box under the window
                int sx = x + out.getOffsetX(), sz = z + out.getOffsetZ();
                b.soft(sx, y - 1, sz, b.trapdoor(p.trapdoor, out.getOpposite(), true, false));
            }
            case GLIMMERKIN, ASTRALS -> {
                // pointed arch above tall windows
                b.set(x, y + rows, z, b.stairs(p.trimStairs, out.rotateYClockwise(), true));
            }
            default -> {
            }
        }
    }

    // ================================================================== entrance

    private final java.util.List<int[]> doors = new java.util.ArrayList<>();

    public void entrance(Builder b, int x, int z, Direction out) {
        int dz = z;
        doors.add(new int[]{x, z});
        b.clear(x, 1, dz, x, 2, dz);
        b.door(x, 1, dz, out);
        int ox = x + out.getOffsetX(), oz = dz + out.getOffsetZ();
        switch (civ) {
            case FORGEKIN, STONEBORN, GLIMMERKIN, ASTRALS -> {
                // carved arch / lintel above the door
                b.set(x, 3, dz, b.stairs(p.trimStairs, out, true));
                b.set(x - 1, 3, dz, b.stairs(p.trimStairs, out.rotateYClockwise(), true));
                b.set(x + 1, 3, dz, b.stairs(p.trimStairs, out.rotateYCounterclockwise(), true));
            }
            case AMBERFOLK, ROOTFOLK -> {
                b.set(x - 1, 3, oz, b.wallLightAt(p.hangingLight));
            }
            default -> {
            }
        }
        if (!decked()) {
            b.soft(ox, 0, oz, p.foundationSlab == null ? p.path.getDefaultState() : b.slab(p.foundationSlab, true));
        }
        b.marker(Blueprint.MarkerType.ENTRANCE, ox + (decked() ? out.getOffsetX() : 0), 1, oz + (decked() ? out.getOffsetZ() : 0), "");
        b.soft(ox, 1, oz, Builder.AIR);
        b.soft(ox, 2, oz, Builder.AIR);
    }

    /** Ladder from y0 through {@code floors} storeys, cutting holes in the floors above. */
    public void ladder(Builder b, int x, int z, int y0, int floors, Direction facing) {
        int top = (floors - 1) * floorHeight() + 1;
        for (int y = y0; y <= top + 1 && y < floors * floorHeight() + 2; y++) {
            b.set(x, y, z, Architect.ladder(facing));
        }
        for (int f = 1; f < floors; f++) {
            b.set(x, f * floorHeight(), z, Architect.ladder(facing));
        }
    }

    public void awning(Builder b, int x1, int x2, int z) {
        for (int x = x1; x <= x2; x++) {
            b.soft(x, 3, z - 1, b.carpet(x % 2 == 0 ? p.cloth : p.clothAlt));
        }
    }

    /** Hanging banner next to the entrance identifying the trade. */
    public void sign(Builder b, int x, int y, int z, String trade) {
        b.soft(x, y + 1, z, b.banner(trade.hashCode() % 2 == 0 ? p.cloth : p.clothAlt, Direction.NORTH));
    }

    /** Small roofed shed at the back of a plot (farms). */
    public void miniShed(Builder b, int x1, int z1, int x2, int z2) {
        b.fill(x1, 0, z1, x2, 0, z2, p.floor);
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            b.fill(c[0], 1, c[1], c[0], 3, c[1], b.axis(p.log, Direction.Axis.Y));
        }
        b.fill(x1, 1, z2, x2, 2, z2, p.planks);
        b.fill(x1 - 1, 4, z1 - 1, x2 + 1, 4, z2 + 1, b.slab(p.roofSlab != null ? p.roofSlab : p.planksSlab, false));
        b.clear(x1 + 1, 1, z1, x2 - 1, 3, z2 - 1);
        b.hangingLantern((x1 + x2) / 2, 3, (z1 + z2) / 2, p.hangingLight);
    }

    // ================================================================== roof and ornament

    /** Roof and exterior ornament after the interior has been furnished. */
    public void finish(Builder b, int x1, int z1, int x2, int z2, int floors, int v) {
        int top = bodyTop;
        x1 = bx1;
        z1 = bz1;
        x2 = bx2;
        z2 = bz2;
        int w = x2 - x1 + 1, d = z2 - z1 + 1;
        int y = top + 1;
        articulate(b, x1, z1, x2, z2, top);
        switch (civ) {
            case ROOTFOLK -> {
                int ridge = w <= d ? b.gableRoofAlongZ(x1, z1, x2, z2, y, p.roofStairs, p.roofStairs, p.planks, 1)
                        : b.gableRoofAlongX(x1, z1, x2, z2, y, p.roofStairs, p.planks, 1);
                thatch(b, x1 - 1, z1 - 1, x2 + 1, z2 + 1, y, ridge + 1);
                if (v == 3) {
                    trunk(b, (x1 + x2) / 2, (z1 + z2) / 2 + d / 4, 1, ridge + 4);
                }
                eaveLanterns(b, x1, z1, x2, z2, top);
            }
            case FORGEKIN -> {
                b.fill(x1, y, z1, x2, y, z2, p.wallAlt);
                for (int x = x1; x <= x2; x++) {
                    for (int z = z1; z <= z2; z++) {
                        if (x == x1 || x == x2 || z == z1 || z == z2) {
                            b.set(x, y + 1, z, (x + z) % 2 == 0 ? p.foundationWall.getDefaultState() : Builder.AIR);
                        }
                    }
                }
                for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
                    b.set(c[0], y + 1, c[1], p.trim);
                    b.set(c[0], y + 2, c[1], p.trimSlab);
                }
                chimney(b, x2 - 1, z2 - 1, y + 1, 3 + (v & 1) * 2, Blocks.BRICKS);
                if (w >= 11) {
                    chimney(b, x1 + 1, z2 - 1, y + 1, 4, Blocks.BRICKS);
                }
                for (int x = x1 + 2; x <= x2 - 2; x += 4) {
                    b.soft(x, top - 1, z1 - 1, b.wallLightAt(p.hangingLight));
                }
            }
            case TIDE_DWELLERS -> {
                int ridge = b.hipRoof(x1, z1, x2, z2, y, p.roofStairs, 1);
                b.set((x1 + x2) / 2, ridge, (z1 + z2) / 2, p.light);
                eaveLanterns(b, x1, z1, x2, z2, top);
            }
            case SPOREFOLK -> {
                double cx = (x1 + x2) / 2.0, cz = (z1 + z2) / 2.0;
                double r = Math.max(w, d) / 2.0 + 1.5;
                capDome(b, cx, cz, r, y, v);
            }
            case GLIMMERKIN -> {
                int ridge = b.hipRoof(x1, z1, x2, z2, y, p.roofStairs, 0);
                int cx = (x1 + x2) / 2, cz = (z1 + z2) / 2;
                spire(b, cx, cz, ridge, 3 + (v & 1) * 2);
                for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
                    b.set(c[0], y, c[1], p.frame);
                    b.set(c[0], y + 1, c[1], b.facing(ModPlants.GLIMMER_CLUSTER, Direction.UP));
                }
            }
            case STONEBORN -> {
                // cornice, then a gable with deep eaves
                for (int x = x1 - 1; x <= x2 + 1; x++) {
                    b.soft(x, top, z1 - 1, b.stairs(p.foundationStairs, Direction.SOUTH, true));
                    b.soft(x, top, z2 + 1, b.stairs(p.foundationStairs, Direction.NORTH, true));
                }
                int ridge = w >= d ? b.gableRoofAlongX(x1, z1, x2, z2, y, p.roofStairs, p.wallAlt, 1)
                        : b.gableRoofAlongZ(x1, z1, x2, z2, y, p.roofStairs, p.roofStairs, p.wallAlt, 1);
                chimney(b, x2 - 1, (z1 + z2) / 2, y, ridge - y + 2, p.foundation);
            }
            case AMBERFOLK -> {
                int ridge = w >= d ? b.gableRoofAlongX(x1, z1, x2, z2, y, p.roofStairs, p.wall, 1)
                        : b.gableRoofAlongZ(x1, z1, x2, z2, y, p.roofStairs, p.roofStairs, p.wall, 1);
                if ((v & 1) == 1) {
                    chimney(b, x1 + 1, z2 - 1, y, ridge - y + 2, Blocks.BRICKS);
                }
                // dormer-style gable lantern and climbing plants
                b.soft((x1 + x2) / 2, top - 1, z1 - 1, b.wallLightAt(p.hangingLight));
            }
            case ASTRALS -> {
                b.fill(x1, y, z1, x2, y, z2, b.slab(p.foundationSlab, false));
                double cx = (x1 + x2) / 2.0, cz = (z1 + z2) / 2.0;
                double r = Math.min(w, d) / 2.0 + 0.2;
                b.ring(cx, cz, r - 0.5, y + 1, y + 1, p.frameAxis, false, null);
                int domeTop = b.dome(cx, cz, r - 0.3, y + 2, p.roof, p.glow);
                b.set((int) Math.round(cx), domeTop, (int) Math.round(cz), p.ornament);
                for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
                    b.set(c[0], y + 1, c[1], p.frameAxis);
                    b.set(c[0], y + 2, c[1], b.facing(Blocks.END_ROD, Direction.UP));
                }
            }
        }
    }

    private boolean nearDoor(int x, int z) {
        for (int[] d : doors) {
            if (Math.abs(d[0] - x) <= 1 && Math.abs(d[1] - z) <= 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Depth and rhythm on the facades: flared plinth, buttresses / pilasters standing out from the frame
     * posts, cornices and window sills, all in the one-block margin around the footprint.
     */
    private void articulate(Builder b, int x1, int z1, int x2, int z2, int top) {
        int fh = floorHeight();
        for (Direction out : Direction.Type.HORIZONTAL) {
            boolean alongX = out.getAxis() == Direction.Axis.Z;
            int from = alongX ? x1 : z1, to = alongX ? x2 : z2;
            for (int i = from; i <= to; i++) {
                int wx = alongX ? i : (out == Direction.WEST ? x1 : x2);
                int wz = alongX ? (out == Direction.NORTH ? z1 : z2) : i;
                int ox = wx + out.getOffsetX(), oz = wz + out.getOffsetZ();
                if (nearDoor(wx, wz) || nearDoor(ox, oz)) {
                    continue;
                }
                int along = i - from;
                boolean post = i == from || i == to || along % (civ == Civilization.GLIMMERKIN ? 3 : 4) == 0;
                switch (civ) {
                    case FORGEKIN -> {
                        b.soft(ox, 0, oz, b.stairs(p.foundationStairs, out.getOpposite(), false));
                        if (post) {
                            for (int y = 1; y < top - 1; y++) {
                                b.soft(ox, y, oz, p.foundationWall.getDefaultState());
                            }
                            b.soft(ox, top - 1, oz, b.stairs(p.foundationStairs, out.getOpposite(), false));
                        }
                        b.soft(ox, top, oz, b.stairs(p.trimStairs, out.getOpposite(), true));
                    }
                    case STONEBORN -> {
                        b.soft(ox, 0, oz, b.stairs(p.foundationStairs, out.getOpposite(), false));
                        sill(b, wx, wz, ox, oz, out, fh);
                    }
                    case GLIMMERKIN -> {
                        b.soft(ox, 0, oz, b.stairs(p.foundationStairs, out.getOpposite(), false));
                        if (post) {
                            for (int y = 1; y <= top; y++) {
                                b.soft(ox, y, oz, p.foundationWall.getDefaultState());
                            }
                            b.soft(ox, top + 1, oz, b.facing(ModPlants.GLIMMER_BUD, Direction.UP));
                        }
                        sill(b, wx, wz, ox, oz, out, fh);
                    }
                    case ASTRALS -> {
                        b.soft(ox, 0, oz, b.stairs(p.foundationStairs, out.getOpposite(), false));
                        if (post || out == Direction.NORTH && along % 2 == 0) {
                            for (int y = 1; y < top; y++) {
                                b.soft(ox, y, oz, b.axis(p.frameAxis, Direction.Axis.Y));
                            }
                        }
                        b.soft(ox, top, oz, b.stairs(p.trimStairs, out.getOpposite(), true));
                    }
                    case AMBERFOLK -> {
                        b.soft(ox, 0, oz, b.slab(p.foundationSlab, false));
                    }
                    case ROOTFOLK -> {
                        if (post) {
                            b.soft(ox, 0, oz, p.foundation.getDefaultState());
                            b.soft(ox, 1, oz, b.stairs(p.foundationStairs, out.getOpposite(), false));
                        } else if (b.rnd.nextInt(3) == 0) {
                            b.soft(ox, 1, oz, b.rnd.nextBoolean() ? p.plantAlt.getDefaultState() : p.accent);
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    /** Stone sill under windows on the outer face. */
    private void sill(Builder b, int wx, int wz, int ox, int oz, Direction out, int fh) {
        for (int y = 2; y < 40; y += fh) {
            BlockState s = b.get(wx, y, wz);
            if (s != null && s.getBlock() == p.window) {
                b.soft(ox, y - 1, oz, b.stairs(p.trimStairs, out.getOpposite(), true));
            }
        }
    }

    private void eaveLanterns(Builder b, int x1, int z1, int x2, int z2, int top) {
        for (int[] c : new int[][]{{x1 - 1, z1 - 1}, {x2 + 1, z1 - 1}}) {
            b.soft(c[0], top - 1, c[1], b.wallLightAt(p.hangingLight));
        }
    }

    /** Leaf thatch over a stepped roof (Rootfolk): leaves rest on every exposed roof step. */
    private void thatch(Builder b, int x1, int z1, int x2, int z2, int y0, int y1) {
        if (p.leaves == null) {
            return;
        }
        BlockState leaves = p.leaves.getDefaultState();
        if (leaves.contains(Properties.PERSISTENT)) {
            leaves = leaves.with(Properties.PERSISTENT, true);
        }
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int y = y1; y >= y0; y--) {
                    BlockState s = b.get(x, y, z);
                    if (s != null && !s.isAir()) {
                        if (b.empty(x, y + 1, z) && (s.getBlock() == p.roofStairs || s.getBlock() == p.roofSlab)
                                && b.rnd.nextInt(10) < 8) {
                            b.set(x, y + 1, z, leaves);
                        }
                        break;
                    }
                }
            }
        }
    }

    /** A living trunk rising through the building with a leafy crown (Rootfolk tree houses). */
    void trunk(Builder b, int x, int z, int y0, int y1) {
        for (int y = y0; y <= y1; y++) {
            b.set(x, y, z, b.axis(p.log, Direction.Axis.Y));
        }
        if (p.leaves != null) {
            BlockState leaves = p.leaves.getDefaultState();
            if (leaves.contains(Properties.PERSISTENT)) {
                leaves = leaves.with(Properties.PERSISTENT, true);
            }
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        double dd = Math.sqrt(dx * dx + dz * dz + dy * dy * 2.5);
                        if (dd <= 3.2 && b.rnd.nextInt(8) != 0) {
                            b.soft(x + dx, y1 + dy, z + dz, leaves);
                        }
                    }
                }
            }
        }
    }

    private void chimney(Builder b, int x, int z, int y0, int h, Block block) {
        for (int y = y0; y < y0 + h; y++) {
            b.set(x, y, z, block);
        }
        b.set(x, y0 + h, z, Blocks.CAMPFIRE.getDefaultState().with(Properties.LIT, true).with(Properties.SIGNAL_FIRE, false));
    }

    /** Mushroom-cap dome with glowing spots and a glowing gill rim underneath (Sporefolk). */
    private void capDome(Builder b, double cx, double cz, double r, int y, int v) {
        Block cap = (v & 1) == 0 ? p.roof : p.trim;
        int ri = (int) Math.ceil(r) + 1;
        double hScale = 0.55;
        for (int dy = 0; dy <= (int) Math.ceil(r * hScale) + 1; dy++) {
            double rr = Math.sqrt(Math.max(0, r * r - (dy / hScale) * (dy / hScale)));
            for (int x = (int) Math.floor(cx - ri); x <= (int) Math.ceil(cx + ri); x++) {
                for (int z = (int) Math.floor(cz - ri); z <= (int) Math.ceil(cz + ri); z++) {
                    double dd = Math.hypot(x - cx, z - cz);
                    if (dd <= rr + 0.5 && dd > rr - 1.2) {
                        boolean spot = (x * 7 + z * 13 + dy * 5) % 11 == 0;
                        b.set(x, y + dy, z, spot ? p.light : cap);
                    } else if (dd <= rr - 1.2) {
                        b.soft(x, y + dy, z, Builder.AIR);
                    }
                }
            }
        }
        // gill rim under the overhang
        for (int x = (int) Math.floor(cx - ri); x <= (int) Math.ceil(cx + ri); x++) {
            for (int z = (int) Math.floor(cz - ri); z <= (int) Math.ceil(cz + ri); z++) {
                double dd = Math.hypot(x - cx, z - cz);
                if (dd <= r + 0.5 && dd > r - 1.5 && (x + z) % 3 == 0) {
                    b.soft(x, y - 1, z, ModPlants.GLOWGILL.getDefaultState());
                }
            }
        }
    }

    /** Slender crystal spire with a glowing heart and crystal tip (Glimmerkin). */
    private void spire(Builder b, int x, int z, int y0, int h) {
        for (int y = y0; y < y0 + h; y++) {
            b.set(x, y, z, y == y0 + h - 2 ? p.glow : Blocks.AMETHYST_BLOCK);
        }
        b.set(x, y0 + h, z, b.facing(ModPlants.GLIMMER_CLUSTER, Direction.UP));
        for (Direction d : Direction.Type.HORIZONTAL) {
            b.soft(x + d.getOffsetX(), y0, z + d.getOffsetZ(), b.facing(Blocks.AMETHYST_CLUSTER, d));
        }
    }

    /** Tower roofs / lookout caps per culture. */
    public void towerCap(Builder b, int w, int d, int y, int v) {
        double cx = (w - 1) / 2.0, cz = (d - 1) / 2.0;
        switch (civ) {
            case ROOTFOLK -> {
                for (int[] c : new int[][]{{-1, -1}, {w, -1}, {-1, d}, {w, d}}) {
                    b.fill(c[0], y, c[1], c[0], y + 1, c[1], p.fence);
                }
                b.cone(cx, cz, w / 2.0 + 1.5, y + 2, p.leaves != null ? p.leaves : p.roof, 1.0);
            }
            case FORGEKIN -> {
                b.set((int) cx, y - 1, (int) cz, Blocks.CAMPFIRE);
            }
            case TIDE_DWELLERS -> {
                for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) {
                    b.fill(c[0], y, c[1], c[0], y + 2, c[1], p.fence);
                }
                b.set((int) cx, y + 1, (int) cz, p.light);
                b.hipRoof(-1, -1, w, d, y + 3, p.roofStairs, 0);
            }
            case SPOREFOLK -> capDome(b, cx, cz, w / 2.0 + 2, y + 2, v);
            case GLIMMERKIN -> spire(b, (int) cx, (int) cz, y - 1, 6);
            case STONEBORN -> {
                for (int x = -1; x <= w; x += 2) {
                    b.set(x, y, -1, p.foundation);
                    b.set(x, y, d, p.foundation);
                }
                for (int z = -1; z <= d; z += 2) {
                    b.set(-1, y, z, p.foundation);
                    b.set(w, y, z, p.foundation);
                }
            }
            case AMBERFOLK -> {
                for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) {
                    b.fill(c[0], y, c[1], c[0], y + 1, c[1], b.axis(p.frame, Direction.Axis.Y));
                }
                b.hipRoof(-1, -1, w, d, y + 2, p.roofStairs, 0);
            }
            case ASTRALS -> {
                int top = b.dome(cx, cz, w / 2.0, y, p.roof, p.glow);
                b.set((int) cx, top, (int) cz, b.facing(Blocks.END_ROD, Direction.UP));
            }
        }
    }

    /** Hanging lantern state usable on walls and eaves. */
    static BlockState hanging(Block lantern) {
        BlockState s = lantern.getDefaultState();
        return s.contains(LanternBlock.HANGING) ? s.with(LanternBlock.HANGING, true) : s;
    }
}
