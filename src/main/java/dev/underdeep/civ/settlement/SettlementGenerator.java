package dev.underdeep.civ.settlement;

import dev.underdeep.civ.arch.Blueprint;
import dev.underdeep.civ.arch.BlueprintPlacer;
import dev.underdeep.civ.arch.BuildingKind;
import dev.underdeep.civ.arch.Palette;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;

/**
 * Writes the parts of settlement plans that fall inside one chunk (called from the Underdeep chunk
 * generator after decoration). Order: plazas, roads, buildings, lamps, boats. Each step only reads the
 * chunk's own terrain and the pure plan, so results are independent of chunk generation order.
 */
public final class SettlementGenerator {
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    private SettlementGenerator() {
    }

    public static void generate(StructureWorldAccess world, Chunk chunk, SettlementMap map) {
        ChunkPos cp = chunk.getPos();
        int x0 = cp.getStartX(), z0 = cp.getStartZ(), x1 = x0 + 15, z1 = z0 + 15;
        for (SettlementSite site : map.sitesIn(x0, z0, x1, z1, 26)) {
            SettlementPlan plan = map.plan(site);
            BlockBox clip = new BlockBox(x0, world.getBottomY(), z0, x1, world.getTopY() - 1, z1);
            Palette pal = Palette.of(site.civ());
            for (SettlementPlan.Plaza p : plan.plazas()) {
                plaza(world, p, pal, clip);
            }
            for (SettlementPlan.Building b : plan.buildings()) {
                if (b.box().intersectsXZ(x0 - 3, z0 - 3, x1 + 3, z1 + 3) && b.kind() != BuildingKind.DOCK) {
                    yard(world, b, clip, pal);
                }
            }
            roads(world, plan, pal, clip);
            for (SettlementPlan.Building b : plan.buildings()) {
                if (b.box().intersectsXZ(x0, z0, x1, z1)) {
                    BlueprintPlacer.place(world, b.blueprint(), b.origin(), b.rotation(), clip, site.id() * 31 + b.index(),
                            BlueprintPlacer.lootFor(b.kind().id(), b.profession()));
                    if (b.kind() == BuildingKind.DOCK) {
                        boats(world, b, clip, site);
                    }
                }
            }
            for (BlockPos l : plan.lamps()) {
                if (clip.contains(l.getX(), clip.getMinY(), l.getZ())) {
                    lamp(world, l, pal);
                }
            }
        }
    }

    // ------------------------------------------------------------------ plazas and roads

    private static void plaza(StructureWorldAccess w, SettlementPlan.Plaza p, Palette pal, BlockBox clip) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = Math.max(clip.getMinX(), p.x() - p.radius()); x <= Math.min(clip.getMaxX(), p.x() + p.radius()); x++) {
            for (int z = Math.max(clip.getMinZ(), p.z() - p.radius()); z <= Math.min(clip.getMaxZ(), p.z() + p.radius()); z++) {
                double d = Math.hypot(x - p.x(), z - p.z());
                if (d > p.radius() + 0.5) {
                    continue;
                }
                Block surface = d > p.radius() - 0.7 ? pal.pathEdge : ((x + z) % 5 == 0 && d > 2 ? pal.plazaAccent : pal.plaza);
                column(w, m, x, z, p.y(), surface.getDefaultState(), false, pal);
            }
        }
    }

    private static void roads(StructureWorldAccess w, SettlementPlan plan, Palette pal, BlockBox clip) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        java.util.List<BlockPos> railings = new java.util.ArrayList<>();
        for (int x = clip.getMinX(); x <= clip.getMaxX(); x++) {
            for (int z = clip.getMinZ(); z <= clip.getMaxZ(); z++) {
                SettlementPlan.Road best = null;
                double bestT = 0, bestEdge = 0;
                for (SettlementPlan.Road r : plan.roads()) {
                    double hw = r.width() / 2.0;
                    double[] p = r.project(x + 0.5, z + 0.5);
                    if (p[1] <= hw * hw && (best == null || r.width() > best.width())) {
                        best = r;
                        bestT = p[0];
                        bestEdge = hw - Math.sqrt(p[1]);
                    }
                }
                if (best == null) {
                    continue;
                }
                double h = best.heightAt(bestT);
                int y = (int) Math.floor(h);
                boolean edge = best.width() >= 3 && bestEdge < 0.9;
                Block surface = edge ? pal.pathEdge : pal.path;
                boolean water = column(w, m, x, z, y, surface.getDefaultState(), true, pal);
                if (h - y >= 0.5 && pal.foundationSlab != null) {
                    m.set(x, y + 1, z);
                    BlockState slab = pal.foundationSlab.getDefaultState();
                    if (slab.contains(SlabBlock.TYPE)) {
                        slab = slab.with(SlabBlock.TYPE, SlabType.BOTTOM);
                    }
                    w.setBlockState(m, slab, FLAGS);
                }
                if (water && edge) {
                    m.set(x, y + 1, z);
                    w.setBlockState(m, pal.fence.getDefaultState(), FLAGS);
                    railings.add(m.toImmutable());
                }
            }
        }
        connectAll(w, railings);
    }

    /** Connects bridge railings to each other (within the chunk). */
    private static void connectAll(StructureWorldAccess w, java.util.List<BlockPos> list) {
        for (BlockPos p : list) {
            BlockState s = w.getBlockState(p);
            if (BlueprintPlacer.connects(s)) {
                w.setBlockState(p, BlueprintPlacer.connect(w, p, s, java.util.Map.of()), FLAGS);
            }
        }
    }

    /**
     * Sets a walking surface at y: fills below with foundation down to the ground (bridge pillars only over
     * water), clears five blocks above. Returns true if the column is over water (bridge).
     */
    private static boolean column(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int y, BlockState surface, boolean bridges,
                                  Palette pal) {
        m.set(x, y - 1, z);
        boolean water = !w.getBlockState(m).getFluidState().isEmpty();
        m.set(x, y, z);
        BlockState top = water && bridges ? pal.planks.getDefaultState() : surface;
        if (top.isOf(Blocks.DIRT_PATH)) {
            top = Blocks.DIRT_PATH.getDefaultState();
        }
        w.setBlockState(m, top, FLAGS);
        boolean pillar = !water || Math.floorMod(x * 3 + z * 7, 6) == 0;
        if (pillar) {
            for (int yy = y - 1, n = 0; n < 14; yy--, n++) {
                m.set(x, yy, z);
                BlockState s = w.getBlockState(m);
                if (ground(s, w, m)) {
                    break;
                }
                w.setBlockState(m, (water ? pal.log : pal.foundation).getDefaultState(), FLAGS);
            }
        }
        for (int yy = y + 1; yy <= y + 5; yy++) {
            m.set(x, yy, z);
            BlockState s = w.getBlockState(m);
            if (!s.isAir() && s.getFluidState().isEmpty()) {
                w.setBlockState(m, Blocks.AIR.getDefaultState(), FLAGS);
            }
        }
        return water;
    }

    /**
     * Levels a two-block yard around a building to its floor level: higher ground is cut back (keeping the
     * natural top block), small hollows are filled; deeper drops are left as they are (the building's
     * foundation shows there).
     */
    private static void yard(StructureWorldAccess w, SettlementPlan.Building b, BlockBox clip, Palette pal) {
        BlockPos o = b.origin();
        int rw = b.blueprint().rotatedWidth(b.rotation()), rd = b.blueprint().rotatedDepth(b.rotation());
        int y0 = o.getY();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = o.getX() - 3; x <= o.getX() + rw + 2; x++) {
            for (int z = o.getZ() - 3; z <= o.getZ() + rd + 2; z++) {
                if (x < clip.getMinX() || x > clip.getMaxX() || z < clip.getMinZ() || z > clip.getMaxZ()) {
                    continue;
                }
                boolean inside = x >= o.getX() && x < o.getX() + rw && z >= o.getZ() && z < o.getZ() + rd;
                if (inside) {
                    continue;
                }
                int ring = Math.max(Math.max(o.getX() - x, x - (o.getX() + rw - 1)), Math.max(o.getZ() - z, z - (o.getZ() + rd - 1)));
                int top = y0;
                // find the current ground in this column near the floor level
                int g = Integer.MIN_VALUE;
                for (int y = y0 + 8; y >= y0 - 6; y--) {
                    m.set(x, y, z);
                    BlockState st = w.getBlockState(m);
                    if (ground(st, w, m)) {
                        g = y;
                        break;
                    }
                    if (!st.getFluidState().isEmpty()) {
                        break;
                    }
                }
                if (g == Integer.MIN_VALUE) {
                    continue;
                }
                m.set(x, g, z);
                BlockState surface = w.getBlockState(m);
                if (g > top) {
                    for (int y = top + 1; y <= g + 2; y++) {
                        m.set(x, y, z);
                        BlockState st = w.getBlockState(m);
                        if (st.getFluidState().isEmpty()) {
                            w.setBlockState(m, Blocks.AIR.getDefaultState(), FLAGS);
                        }
                    }
                    m.set(x, top, z);
                    w.setBlockState(m, surface, FLAGS);
                } else if (g < top && top - g <= 3) {
                    for (int y = g + 1; y < top; y++) {
                        m.set(x, y, z);
                        w.setBlockState(m, Blocks.DIRT.getDefaultState(), FLAGS);
                    }
                    m.set(x, top, z);
                    w.setBlockState(m, surface.isOf(Blocks.STONE) || surface.isOf(Blocks.DEEPSLATE) ? pal.pathEdge.getDefaultState() : surface, FLAGS);
                }
            }
        }
    }

    /** Natural ground (not vegetation, logs or giant-fungus blocks, which vary with generation order). */
    private static boolean ground(BlockState st, StructureWorldAccess w, BlockPos p) {
        if (st.isAir() || !st.getFluidState().isEmpty() || !st.isSolidBlock(w, p)) {
            return false;
        }
        return !st.isIn(net.minecraft.registry.tag.BlockTags.LEAVES) && !st.isIn(net.minecraft.registry.tag.BlockTags.LOGS)
                && !(st.getBlock() instanceof net.minecraft.block.MushroomBlock) && !st.isIn(net.minecraft.registry.tag.BlockTags.WART_BLOCKS);
    }

    // ------------------------------------------------------------------ lamps and boats

    private static void lamp(StructureWorldAccess w, BlockPos base, Palette pal) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        Block post = pal.foundationWall != null ? pal.foundationWall : pal.fence;
        // footing down to the ground, post, light
        for (int yy = base.getY(), n = 0; n < 8; yy--, n++) {
            m.set(base.getX(), yy, base.getZ());
            BlockState s = w.getBlockState(m);
            if (ground(s, w, m)) {
                break;
            }
            w.setBlockState(m, pal.foundation.getDefaultState(), FLAGS);
        }
        m.set(base.getX(), base.getY() + 1, base.getZ());
        w.setBlockState(m, post.getDefaultState(), FLAGS);
        m.set(base.getX(), base.getY() + 2, base.getZ());
        w.setBlockState(m, post.getDefaultState(), FLAGS);
        m.set(base.getX(), base.getY() + 3, base.getZ());
        BlockState light = pal.light.getDefaultState();
        if (light.contains(LanternBlock.HANGING)) {
            light = light.with(LanternBlock.HANGING, false);
        }
        w.setBlockState(m, light, FLAGS);
    }

    private static void boats(StructureWorldAccess w, SettlementPlan.Building b, BlockBox clip, SettlementSite site) {
        for (Blueprint.Marker mk : b.blueprint().markers()) {
            if (mk.type() != Blueprint.MarkerType.DOCK) {
                continue;
            }
            BlockPos p = b.world(mk.pos());
            // moor the boat beside the pier, on the water
            BlockPos side = p.add(b.rotation() == net.minecraft.util.BlockRotation.NONE || b.rotation() == net.minecraft.util.BlockRotation.CLOCKWISE_180 ? 3 : 0,
                    -1, b.rotation() == net.minecraft.util.BlockRotation.NONE || b.rotation() == net.minecraft.util.BlockRotation.CLOCKWISE_180 ? 0 : 3);
            if (!clip.contains(side) || w.getBlockState(side).getFluidState().isEmpty()) {
                continue;
            }
            BoatEntity boat = EntityType.BOAT.create(w.toServerWorld());
            if (boat != null) {
                boat.refreshPositionAndAngles(side.getX() + 0.5, side.getY() + 0.9, side.getZ() + 0.5, (float) (site.id() % 360), 0);
                w.spawnEntity(boat);
            }
        }
    }
}
