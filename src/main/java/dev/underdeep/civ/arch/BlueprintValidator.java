package dev.underdeep.civ.arch;

import net.minecraft.block.BlockState;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Structural checks of a design without a world: an entrance exists and opens to the interior, and every
 * bed, workstation, merchant and storage marker can be reached on foot from the entrance (two-block-high
 * walkable space, doors/gates/ladders passable, one-block steps allowed).
 */
public final class BlueprintValidator {
    public record Result(boolean entrance, int markers, int reachable, List<String> problems) {
        public boolean ok() {
            return entrance && problems.isEmpty();
        }
    }

    private BlueprintValidator() {
    }

    static boolean passable(BlockState s) {
        if (s == null || s.isAir()) {
            return true;
        }
        return s.getBlock() instanceof DoorBlock || s.getBlock() instanceof FenceGateBlock || s.getBlock() instanceof CarpetBlock
                || s.getBlock() instanceof PressurePlateBlock || s.getBlock() instanceof LadderBlock
                || s.getBlock() instanceof TrapdoorBlock && s.get(TrapdoorBlock.OPEN)
                || !s.getFluidState().isEmpty() && s.getBlock() == net.minecraft.block.Blocks.WATER
                || s.getCollisionShape(net.minecraft.world.EmptyBlockView.INSTANCE, BlockPos.ORIGIN).isEmpty();
    }

    static boolean standable(BlockState below) {
        if (below == null || below.isAir()) {
            return false;
        }
        if (below.getBlock() instanceof SlabBlock) {
            return true;
        }
        return !below.getCollisionShape(net.minecraft.world.EmptyBlockView.INSTANCE, BlockPos.ORIGIN).isEmpty()
                || below.getBlock() instanceof LadderBlock;
    }

    public static Result validate(Blueprint bp) {
        List<String> problems = new ArrayList<>();
        Blueprint.Marker entrance = null;
        for (Blueprint.Marker m : bp.markers()) {
            if (m.type() == Blueprint.MarkerType.ENTRANCE) {
                entrance = m;
                break;
            }
        }
        if (entrance == null) {
            problems.add("no entrance marker");
            return new Result(false, 0, 0, problems);
        }
        // flood fill walkable cells from the entrance (outside space counts as walkable ground)
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(entrance.pos());
        seen.add(entrance.pos().asLong());
        int limit = 20000;
        while (!q.isEmpty() && limit-- > 0) {
            BlockPos c = q.poll();
            for (Direction d : Direction.Type.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos n = c.add(d.getOffsetX(), dy, d.getOffsetZ());
                    if (n.getX() < -2 || n.getZ() < -2 || n.getX() > bp.width + 1 || n.getZ() > bp.depth + 1 || n.getY() < 0
                            || n.getY() > bp.height() + 1 || seen.contains(n.asLong())) {
                        continue;
                    }
                    if (walkable(bp, n, dy > 0 ? c : null)) {
                        seen.add(n.asLong());
                        q.add(n);
                    }
                }
            }
            // ladders lead up and down
            for (int dy = -1; dy <= 1; dy += 2) {
                BlockPos n = c.up(dy);
                BlockState s = bp.get(n.getX(), n.getY(), n.getZ());
                BlockState here = bp.get(c.getX(), c.getY(), c.getZ());
                if ((s != null && s.getBlock() instanceof LadderBlock || here != null && here.getBlock() instanceof LadderBlock)
                        && passable(s) && passable(bp.get(n.getX(), n.getY() + 1, n.getZ())) && seen.add(n.asLong())) {
                    q.add(n);
                }
            }
        }
        int markers = 0, reachable = 0;
        for (Blueprint.Marker m : bp.markers()) {
            if (m.type() == Blueprint.MarkerType.ENTRANCE || m.type() == Blueprint.MarkerType.LIGHT || m.type() == Blueprint.MarkerType.SEAT) {
                continue;
            }
            markers++;
            boolean ok = false;
            BlockPos p = m.pos();
            for (int dx = -1; dx <= 1 && !ok; dx++) {
                for (int dz = -1; dz <= 1 && !ok; dz++) {
                    for (int dy = -1; dy <= 1 && !ok; dy++) {
                        ok = seen.contains(p.add(dx, dy, dz).asLong());
                    }
                }
            }
            if (ok) {
                reachable++;
            } else {
                problems.add(m.type().name().toLowerCase() + " " + m.tag() + " at " + p.toShortString() + " unreachable");
            }
        }
        return new Result(true, markers, reachable, problems);
    }

    private static boolean walkable(Blueprint bp, BlockPos p, BlockPos stepFrom) {
        BlockState feet = bp.get(p.getX(), p.getY(), p.getZ());
        BlockState head = bp.get(p.getX(), p.getY() + 1, p.getZ());
        BlockState below = bp.get(p.getX(), p.getY() - 1, p.getZ());
        boolean outside = p.getX() < 0 || p.getZ() < 0 || p.getX() >= bp.width || p.getZ() >= bp.depth;
        if (!passable(feet) || !passable(head)) {
            // a bottom slab or stair can be stepped onto
            return false;
        }
        if (stepFrom != null) {
            BlockState above = bp.get(stepFrom.getX(), stepFrom.getY() + 2, stepFrom.getZ());
            if (!passable(above)) {
                return false;
            }
        }
        if (feet != null && feet.getBlock() instanceof LadderBlock) {
            return true;
        }
        if (outside && p.getY() == 1 && (below == null || below.isAir())) {
            return true; // ground around the building
        }
        return standable(below) || below != null && below.getBlock() instanceof SlabBlock && below.get(SlabBlock.TYPE) != SlabType.BOTTOM;
    }
}
