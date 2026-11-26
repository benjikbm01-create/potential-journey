package dev.underdeep.civ.arch;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Random;

/**
 * Drawing toolkit for {@link Blueprint}s. Local frame: front = north (-z), back = south (+z),
 * left = west (-x), right = east (+x). All writes go to the blueprint buffer; {@link #AIR} entries carve
 * space (interiors are explicitly cleared so terrain never intrudes).
 */
public final class Builder {
    public static final BlockState AIR = Blocks.AIR.getDefaultState();
    public final Blueprint bp;
    public final Palette p;
    public final Random rnd;

    public Builder(Blueprint bp, Palette p, long seed) {
        this.bp = bp;
        this.p = p;
        this.rnd = new Random(seed);
    }

    // ------------------------------------------------------------------ raw

    public void set(int x, int y, int z, BlockState s) {
        if (s == null) {
            return;
        }
        bp.blocks.put(Blueprint.key(x, y, z), s);
        bp.height = Math.max(bp.height, y + 1);
    }

    public void set(int x, int y, int z, Block b) {
        if (b != null) {
            set(x, y, z, b.getDefaultState());
        }
    }

    /** Sets only where nothing has been placed yet (or only air). */
    public void soft(int x, int y, int z, BlockState s) {
        BlockState cur = bp.get(x, y, z);
        if (cur == null || cur.isAir()) {
            set(x, y, z, s);
        }
    }

    public void soft(int x, int y, int z, Block b) {
        if (b != null) {
            soft(x, y, z, b.getDefaultState());
        }
    }

    public BlockState get(int x, int y, int z) {
        return bp.get(x, y, z);
    }

    public boolean empty(int x, int y, int z) {
        BlockState s = bp.get(x, y, z);
        return s == null || s.isAir();
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
        if (b != null) {
            fill(x1, y1, z1, x2, y2, z2, b.getDefaultState());
        }
    }

    public void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, AIR);
    }

    public void marker(Blueprint.MarkerType type, int x, int y, int z, String tag) {
        bp.markers.add(new Blueprint.Marker(type, new BlockPos(x, y, z), tag));
    }

    public static Block block(String id) {
        return Registries.BLOCK.get(Identifier.of(id));
    }

    // ------------------------------------------------------------------ shaped blocks

    public BlockState stairs(Block b, Direction facing, boolean top) {
        if (b == null) {
            return null;
        }
        BlockState s = b.getDefaultState();
        if (s.contains(StairsBlock.FACING)) {
            s = s.with(StairsBlock.FACING, facing).with(StairsBlock.HALF, top ? BlockHalf.TOP : BlockHalf.BOTTOM);
        }
        return s;
    }

    public BlockState slab(Block b, boolean top) {
        if (b == null) {
            return null;
        }
        BlockState s = b.getDefaultState();
        if (s.contains(SlabBlock.TYPE)) {
            s = s.with(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
        }
        return s;
    }

    public BlockState axis(Block b, Direction.Axis axis) {
        BlockState s = b.getDefaultState();
        return s.contains(PillarBlock.AXIS) ? s.with(PillarBlock.AXIS, axis) : s;
    }

    public BlockState facing(Block b, Direction d) {
        BlockState s = b.getDefaultState();
        if (s.contains(Properties.HORIZONTAL_FACING) && d.getAxis().isHorizontal()) {
            return s.with(Properties.HORIZONTAL_FACING, d);
        }
        if (s.contains(Properties.FACING)) {
            return s.with(Properties.FACING, d);
        }
        return s;
    }

    public BlockState trapdoor(Block b, Direction facing, boolean top, boolean open) {
        BlockState s = b.getDefaultState();
        if (s.contains(TrapdoorBlock.FACING)) {
            s = s.with(TrapdoorBlock.FACING, facing).with(TrapdoorBlock.HALF, top ? BlockHalf.TOP : BlockHalf.BOTTOM)
                    .with(TrapdoorBlock.OPEN, open);
        }
        return s;
    }

    /** Door whose outside is in direction {@code outward}. */
    public void door(int x, int y, int z, Direction outward) {
        Block d = p.door;
        BlockState lower = d.getDefaultState().with(DoorBlock.FACING, outward.getOpposite())
                .with(DoorBlock.HALF, DoubleBlockHalf.LOWER).with(DoorBlock.HINGE, DoorHinge.LEFT);
        set(x, y, z, lower);
        set(x, y + 1, z, lower.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    public void bed(int x, int y, int z, Direction headDir, DyeColor color) {
        Block b = block("minecraft:" + color.getName() + "_bed");
        BlockState foot = b.getDefaultState().with(BedBlock.FACING, headDir).with(BedBlock.PART, BedPart.FOOT);
        set(x, y, z, foot);
        set(x + headDir.getOffsetX(), y, z + headDir.getOffsetZ(), foot.with(BedBlock.PART, BedPart.HEAD));
        marker(Blueprint.MarkerType.BED, x + headDir.getOffsetX(), y, z + headDir.getOffsetZ(), "");
    }

    public void hangingLantern(int x, int y, int z, Block lantern) {
        BlockState s = lantern.getDefaultState();
        if (s.contains(LanternBlock.HANGING)) {
            s = s.with(LanternBlock.HANGING, true);
        }
        set(x, y, z, s);
        marker(Blueprint.MarkerType.LIGHT, x, y, z, "");
    }

    /** Lantern state hanging from the block above (eaves, ceilings). */
    public BlockState wallLightAt(Block lantern) {
        BlockState s = lantern.getDefaultState();
        return s.contains(LanternBlock.HANGING) ? s.with(LanternBlock.HANGING, true) : s;
    }

    public void lamp(int x, int y, int z) {
        set(x, y, z, p.light);
        marker(Blueprint.MarkerType.LIGHT, x, y, z, "");
    }

    public BlockState carpet(DyeColor c) {
        return block("minecraft:" + c.getName() + "_carpet").getDefaultState();
    }

    public BlockState wool(DyeColor c) {
        return block("minecraft:" + c.getName() + "_wool").getDefaultState();
    }

    public BlockState banner(DyeColor c, Direction facing) {
        Block wall = block("minecraft:" + c.getName() + "_wall_banner");
        return wall.getDefaultState().with(HorizontalFacingBlock.FACING, facing);
    }

    public BlockState pot(Block plant) {
        Block potted = block("minecraft:potted_fern");
        if (plant == Blocks.RED_MUSHROOM || plant == Blocks.BROWN_MUSHROOM) {
            potted = plant == Blocks.RED_MUSHROOM ? Blocks.POTTED_RED_MUSHROOM : Blocks.POTTED_BROWN_MUSHROOM;
        }
        return potted.getDefaultState();
    }

    // ------------------------------------------------------------------ furniture

    /** Chair (stairs) facing a direction (the sitter faces {@code facing}). */
    public void chair(int x, int y, int z, Direction facing) {
        set(x, y, z, stairs(p.planksStairs, facing.getOpposite(), false));
        marker(Blueprint.MarkerType.SEAT, x, y, z, "");
    }

    /** Small table: fence post with pressure plate or slab top. */
    public void table(int x, int y, int z) {
        set(x, y, z, p.fence);
        set(x, y + 1, z, Blocks.AIR);
        BlockState top = p.planksSlab != null ? slab(p.planksSlab, false) : Blocks.OAK_PRESSURE_PLATE.getDefaultState();
        set(x, y + 1, z, top);
    }

    /** Counter top (top slab) used by shops and kitchens. */
    public void counter(int x1, int z1, int x2, int z2, int y) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                set(x, y, z, slab(p.trimSlab != null ? p.trimSlab : p.planksSlab, true));
            }
        }
    }

    public void shelf(int x, int y, int z, Direction facing) {
        int r = rnd.nextInt(5);
        BlockState s = switch (r) {
            case 0 -> Blocks.BOOKSHELF.getDefaultState();
            case 1 -> Blocks.CHISELED_BOOKSHELF.getDefaultState().with(Properties.HORIZONTAL_FACING, facing);
            case 2 -> Blocks.BARREL.getDefaultState().with(Properties.FACING, facing);
            case 3 -> trapdoor(p.trapdoor, facing.getOpposite(), true, false);
            default -> Blocks.DECORATED_POT.getDefaultState().with(Properties.HORIZONTAL_FACING, facing);
        };
        set(x, y, z, s);
    }

    public void storage(int x, int y, int z, Direction facing, boolean chest) {
        set(x, y, z, chest ? Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, facing)
                : Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        marker(Blueprint.MarkerType.STORAGE, x, y, z, chest ? "chest" : "barrel");
    }

    public void workstation(int x, int y, int z, Block station, Direction facing, String profession) {
        BlockState s = station.getDefaultState();
        if (s.contains(Properties.HORIZONTAL_FACING)) {
            s = s.with(Properties.HORIZONTAL_FACING, facing);
        }
        set(x, y, z, s);
        marker(Blueprint.MarkerType.WORKSTATION, x, y, z, profession);
    }

    // ------------------------------------------------------------------ structural helpers

    /** Floor slab spanning a rectangle at y, foundation below handled by the placer. */
    public void floor(int x1, int z1, int x2, int z2, int y, Block b) {
        fill(x1, y, z1, x2, y, z2, b);
    }

    /** Rectangular wall ring from y1 to y2 (inclusive), corners with pillar block when given. */
    public void walls(int x1, int z1, int x2, int z2, int y1, int y2, Block wall, Block corner) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                set(x, y, z1, wall);
                set(x, y, z2, wall);
            }
            for (int z = z1; z <= z2; z++) {
                set(x1, y, z, wall);
                set(x2, y, z, wall);
            }
            if (corner != null) {
                set(x1, y, z1, axis(corner, Direction.Axis.Y));
                set(x2, y, z1, axis(corner, Direction.Axis.Y));
                set(x1, y, z2, axis(corner, Direction.Axis.Y));
                set(x2, y, z2, axis(corner, Direction.Axis.Y));
            }
        }
    }

    /** Circular wall ring (radius r, centre cx/cz) from y1 to y2. */
    public void ring(double cx, double cz, double r, int y1, int y2, Block wall, boolean fillInside, BlockState inside) {
        int ri = (int) Math.ceil(r) + 1;
        for (int x = (int) Math.floor(cx - ri); x <= (int) Math.ceil(cx + ri); x++) {
            for (int z = (int) Math.floor(cz - ri); z <= (int) Math.ceil(cz + ri); z++) {
                double d = Math.hypot(x - cx, z - cz);
                for (int y = y1; y <= y2; y++) {
                    if (d <= r + 0.5 && d > r - 0.5) {
                        set(x, y, z, wall);
                    } else if (d <= r - 0.5 && fillInside) {
                        set(x, y, z, inside);
                    }
                }
            }
        }
    }

    /** Disc (filled circle) at y. */
    public void disc(double cx, double cz, double r, int y, BlockState s) {
        int ri = (int) Math.ceil(r) + 1;
        for (int x = (int) Math.floor(cx - ri); x <= (int) Math.ceil(cx + ri); x++) {
            for (int z = (int) Math.floor(cz - ri); z <= (int) Math.ceil(cz + ri); z++) {
                if (Math.hypot(x - cx, z - cz) <= r + 0.5) {
                    set(x, y, z, s);
                }
            }
        }
    }

    /**
     * Gable roof over [x1, x2] x [z1, z2] with the ridge running along z (gable ends at front/back), starting
     * at y. Stairs step inward each layer; overhang extends one block beyond the walls.
     */
    public int gableRoofAlongZ(int x1, int z1, int x2, int z2, int y, Block stairs, Block fullBlock, Block gableFill, int overhang) {
        int layer = 0;
        int lx = x1 - overhang, rx = x2 + overhang;
        while (lx <= rx) {
            int yy = y + layer;
            for (int z = z1 - overhang; z <= z2 + overhang; z++) {
                if (lx == rx) {
                    set(lx, yy, z, slab(p.roofSlab, false));
                } else {
                    set(lx, yy, z, stairs(stairs, Direction.EAST, false));
                    set(rx, yy, z, stairs(stairs, Direction.WEST, false));
                    if (rx - lx == 1) {
                        // ridge cap
                        set(lx, yy + 1, z, slab(p.roofSlab, false));
                        set(rx, yy + 1, z, slab(p.roofSlab, false));
                    }
                }
            }
            // gable end walls
            for (int x = lx + 1; x <= rx - 1; x++) {
                if (x >= x1 && x <= x2) {
                    set(x, yy, z1, gableFill);
                    set(x, yy, z2, gableFill);
                    for (int z = z1 + 1; z < z2; z++) {
                        soft(x, yy, z, AIR);
                    }
                }
            }
            lx++;
            rx--;
            layer++;
        }
        return y + layer;
    }

    /** Gable roof with the ridge running along x (gables on the sides). */
    public int gableRoofAlongX(int x1, int z1, int x2, int z2, int y, Block stairs, Block gableFill, int overhang) {
        int layer = 0;
        int fz = z1 - overhang, bz = z2 + overhang;
        while (fz <= bz) {
            int yy = y + layer;
            for (int x = x1 - overhang; x <= x2 + overhang; x++) {
                if (fz == bz) {
                    set(x, yy, fz, slab(p.roofSlab, false));
                } else {
                    set(x, yy, fz, stairs(stairs, Direction.SOUTH, false));
                    set(x, yy, bz, stairs(stairs, Direction.NORTH, false));
                    if (bz - fz == 1) {
                        set(x, yy + 1, fz, slab(p.roofSlab, false));
                        set(x, yy + 1, bz, slab(p.roofSlab, false));
                    }
                }
            }
            for (int z = fz + 1; z <= bz - 1; z++) {
                if (z >= z1 && z <= z2) {
                    set(x1, yy, z, gableFill);
                    set(x2, yy, z, gableFill);
                    for (int x = x1 + 1; x < x2; x++) {
                        soft(x, yy, z, AIR);
                    }
                }
            }
            fz++;
            bz--;
            layer++;
        }
        return y + layer;
    }

    /** Hip roof (all four sides slope) — pyramid of stairs rings, flat slab top when the ring closes. */
    public int hipRoof(int x1, int z1, int x2, int z2, int y, Block stairs, int overhang) {
        int lx = x1 - overhang, rx = x2 + overhang, fz = z1 - overhang, bz = z2 + overhang;
        int yy = y;
        while (lx <= rx && fz <= bz) {
            if (lx == rx || fz == bz) {
                for (int x = lx; x <= rx; x++) {
                    for (int z = fz; z <= bz; z++) {
                        set(x, yy, z, slab(p.roofSlab, false));
                    }
                }
                return yy + 1;
            }
            for (int x = lx; x <= rx; x++) {
                set(x, yy, fz, stairs(stairs, Direction.SOUTH, false));
                set(x, yy, bz, stairs(stairs, Direction.NORTH, false));
            }
            for (int z = fz; z <= bz; z++) {
                set(lx, yy, z, stairs(stairs, Direction.EAST, false));
                set(rx, yy, z, stairs(stairs, Direction.WEST, false));
            }
            for (int x = lx + 1; x < rx; x++) {
                for (int z = fz + 1; z < bz; z++) {
                    soft(x, yy, z, AIR);
                }
            }
            lx++;
            rx--;
            fz++;
            bz--;
            yy++;
        }
        return yy;
    }

    /** Dome over a circle: shells of the given block, optional glowing cap. */
    public int dome(double cx, double cz, double r, int y, Block shell, Block cap) {
        int top = y;
        for (int dy = 0; dy <= (int) Math.ceil(r); dy++) {
            double rr = Math.sqrt(Math.max(0, r * r - dy * dy * 1.3));
            if (rr < 0.6) {
                set((int) Math.round(cx), y + dy, (int) Math.round(cz), cap != null ? cap : shell);
                top = y + dy;
                break;
            }
            int ri = (int) Math.ceil(rr) + 1;
            for (int x = (int) Math.floor(cx - ri); x <= (int) Math.ceil(cx + ri); x++) {
                for (int z = (int) Math.floor(cz - ri); z <= (int) Math.ceil(cz + ri); z++) {
                    double d = Math.hypot(x - cx, z - cz);
                    if (d <= rr + 0.5 && d > rr - 1.0) {
                        set(x, y + dy, z, shell);
                    } else if (d <= rr - 1.0) {
                        soft(x, y + dy, z, AIR);
                    }
                }
            }
            top = y + dy;
        }
        return top + 1;
    }

    /** Cone (spire) roof over a circle or square, used for towers. */
    public int cone(double cx, double cz, double r, int y, Block block, double steep) {
        int dy = 0;
        while (true) {
            double rr = r - dy / steep;
            if (rr < 0.5) {
                set((int) Math.round(cx), y + dy, (int) Math.round(cz), block);
                return y + dy + 1;
            }
            disc(cx, cz, rr, y + dy, block.getDefaultState());
            if (rr > 1.5) {
                disc(cx, cz, rr - 1.2, y + dy, AIR);
            }
            dy++;
        }
    }

    /** Window opening: pane (or bars) set into a wall at (x, y, z) across 1-2 rows. */
    public void window(int x, int y, int z, int rows) {
        for (int i = 0; i < rows; i++) {
            set(x, y + i, z, p.window);
        }
    }

    /** Wall sconce: wall light set into a wall position. */
    public void sconce(int x, int y, int z) {
        set(x, y, z, p.wallLight);
        marker(Blueprint.MarkerType.LIGHT, x, y, z, "");
    }

    /** Fills a column downward with foundation (to be extended to the ground by the placer). */
    public void footing(int x, int z, Block b) {
        set(x, -1, z, b);
    }

    public Direction left(Direction d) {
        return d.rotateYCounterclockwise();
    }

    public Direction right(Direction d) {
        return d.rotateYClockwise();
    }
}
