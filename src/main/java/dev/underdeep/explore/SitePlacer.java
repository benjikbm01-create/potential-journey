package dev.underdeep.explore;

import dev.underdeep.civ.arch.BlueprintPlacer;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.DecoratedPotBlockEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;

/**
 * Writes the part of each site that falls inside one chunk (called from the chunk generator after settlements).
 * Deterministic: a column's result depends only on the layout and that column's terrain, so a site spanning many
 * chunks is assembled identically in any generation order.
 * <ul>
 *   <li>Carved air becomes water in aquatic sites below the water line (flooded architecture), else air.</li>
 *   <li>Connecting blocks (fences, walls, bars, panes) take their shape from the design's neighbours.</li>
 *   <li>Footings: columns whose lowest design block is a solid floor course get a foundation down to ground.</li>
 *   <li>Fluids next to carved space are sealed with rubble so rooms never flood by accident.</li>
 *   <li>Shared containers receive their site loot table (rolled once, like vanilla structure loot).</li>
 * </ul>
 */
public final class SitePlacer {
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    private static final int MAX_FOOTING = 10;

    private SitePlacer() {
    }

    public static void generate(StructureWorldAccess world, Chunk chunk, ExplorationMap map) {
        ChunkPos cp = chunk.getPos();
        int x0 = cp.getStartX(), z0 = cp.getStartZ(), x1 = x0 + 15, z1 = z0 + 15;
        for (Site site : map.sitesIn(x0, z0, x1, z1, 1)) {
            place(world, map.layout(site), x0, z0, x1, z1);
        }
    }

    public static void place(StructureWorldAccess world, SiteLayout layout, int x0, int z0, int x1, int z1) {
        Site site = layout.site;
        Canvas c = layout.canvas;
        BlockRotation rot = site.rotation();
        BlockPos o = site.origin();
        int n = c.n;
        boolean aquatic = site.family().aquatic();
        int waterLine = site.layer() == dev.underdeep.world.terrain.CavernLayer.UPPER ? dev.underdeep.world.terrain.UnderdeepTerrain.SEA_LEVEL
                : dev.underdeep.world.terrain.CavernLayer.DEEP.fluidLevel;
        Long2IntOpenHashMap lowest = new Long2IntOpenHashMap();
        lowest.defaultReturnValue(Integer.MAX_VALUE);
        LongArrayList carved = new LongArrayList();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (Long2ObjectMap.Entry<BlockState> e : c.blocks().long2ObjectEntrySet()) {
            long k = e.getLongKey();
            int lx = BlockPos.unpackLongX(k), ly = BlockPos.unpackLongY(k), lz = BlockPos.unpackLongZ(k);
            BlockPos off = Canvas.transform(n, lx, ly, lz, rot);
            int wx = o.getX() + off.getX(), wz = o.getZ() + off.getZ(), wy = o.getY() + ly;
            if (wx < x0 || wx > x1 || wz < z0 || wz > z1 || wy <= world.getBottomY() + 1 || wy >= world.getTopY() - 1) {
                continue;
            }
            m.set(wx, wy, wz);
            BlockState s = e.getValue().rotate(rot);
            if (s.isAir()) {
                BlockState cur = world.getBlockState(m);
                if (aquatic && (wy <= waterLine || cur.getFluidState().isOf(Fluids.WATER))) {
                    world.setBlockState(m, Blocks.WATER.getDefaultState(), FLAGS);
                } else if (!cur.isAir()) {
                    world.setBlockState(m, s, FLAGS);
                    carved.add(m.asLong());
                } else {
                    carved.add(m.asLong());
                }
                continue;
            }
            if (aquatic && s.contains(Properties.WATERLOGGED) && wy <= waterLine) {
                s = s.with(Properties.WATERLOGGED, true);
            }
            if (connects(s)) {
                s = connect(world, c, site, m.toImmutable(), s, lx, ly, lz);
            }
            world.setBlockState(m, s, FLAGS);
            long col = BlockPos.asLong(wx, 0, wz);
            if (ly < lowest.get(col)) {
                lowest.put(col, ly);
            }
            if (s.hasBlockEntity()) {
                loot(world, layout, m, k);
            }
        }
        // footings under floor courses that sit above lower ground
        Block foot = c.style.rubble == null ? Blocks.COBBLESTONE : c.style.rubble;
        for (var it = lowest.long2IntEntrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getIntValue() < -1 || e.getIntValue() > 0) {
                continue;
            }
            int wx = BlockPos.unpackLongX(e.getLongKey()), wz = BlockPos.unpackLongZ(e.getLongKey());
            for (int y = o.getY() + e.getIntValue() - 1, i = 0; i < MAX_FOOTING; y--, i++) {
                m.set(wx, y, wz);
                BlockState cur = world.getBlockState(m);
                if (BlueprintPlacer.isGround(world, m, cur)) {
                    break;
                }
                world.setBlockState(m, (i == 0 ? c.style.brick : foot).getDefaultState(), FLAGS);
            }
        }
        // keep carved rooms dry: seal fluids touching carved space from outside the design
        if (!aquatic) {
            for (int i = 0; i < carved.size(); i++) {
                BlockPos p = BlockPos.fromLong(carved.getLong(i));
                for (Direction d : Direction.values()) {
                    BlockPos q = p.offset(d);
                    if (q.getX() < x0 || q.getX() > x1 || q.getZ() < z0 || q.getZ() > z1) {
                        continue;
                    }
                    BlockState qs = world.getBlockState(q);
                    if (!qs.getFluidState().isEmpty() && !inDesign(c, site, q)) {
                        world.setBlockState(q, foot.getDefaultState(), FLAGS);
                    }
                }
            }
        }
    }

    private static void loot(StructureWorldAccess world, SiteLayout layout, BlockPos pos, long local) {
        for (Canvas.CacheSpec cs : layout.canvas.caches) {
            if (cs.pos() == local && cs.policy() == Canvas.Policy.SHARED) {
                BlockEntity be = world.getBlockEntity(pos);
                RegistryKey<net.minecraft.loot.LootTable> key = RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(cs.loot()));
                long seed = layout.site.seed() ^ pos.asLong();
                if (be instanceof LootableContainerBlockEntity lc) {
                    lc.setLootTable(key, seed);
                } else if (be instanceof DecoratedPotBlockEntity pot) {
                    pot.setLootTable(key, seed);
                }
            }
        }
    }

    static boolean connects(BlockState s) {
        return s.getBlock() instanceof WallBlock || s.contains(Properties.NORTH) && s.contains(Properties.EAST) && !s.contains(Properties.UP)
                && !(s.getBlock() instanceof net.minecraft.block.VineBlock);
    }

    /** Local position of a world position on a site (inverse rotation). */
    static long local(Site site, BlockPos w) {
        int n = site.size();
        int ox = w.getX() - site.origin().getX(), oz = w.getZ() - site.origin().getZ(), y = w.getY() - site.origin().getY();
        return switch (site.rotation()) {
            case NONE -> Canvas.key(ox, y, oz);
            case CLOCKWISE_90 -> Canvas.key(oz, y, n - 1 - ox);
            case CLOCKWISE_180 -> Canvas.key(n - 1 - ox, y, n - 1 - oz);
            case COUNTERCLOCKWISE_90 -> Canvas.key(n - 1 - oz, y, ox);
        };
    }

    static boolean inDesign(Canvas c, Site site, BlockPos w) {
        long l = local(site, w);
        int x = BlockPos.unpackLongX(l), z = BlockPos.unpackLongZ(l);
        return x >= 0 && z >= 0 && x < c.n && z < c.n && c.blocks().containsKey(l);
    }

    private static BlockState connect(StructureWorldAccess world, Canvas c, Site site, BlockPos pos, BlockState s, int lx, int ly, int lz) {
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos q = pos.offset(d);
            BlockState ns;
            long l = local(site, q);
            BlockState design = c.blocks().get(l);
            if (design != null) {
                ns = design.rotate(site.rotation());
            } else {
                ns = world.getBlockState(q);
            }
            try {
                s = s.getStateForNeighborUpdate(d, ns, world, pos, q);
            } catch (RuntimeException ignored) {
                // some blocks consult the world beyond the neighbour; keep the current shape
            }
        }
        return s;
    }
}
