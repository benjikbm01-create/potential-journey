package dev.underdeep.civ.arch;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

/**
 * Writes a {@link Blueprint} into a world: rotated, clipped to a box (one chunk during world generation),
 * with foundations extended down to solid ground (or to the bed under water for stilts), terrain
 * cleared out of the building volume, and loot tables on containers.
 * <p>
 * Deterministic: the result for a column depends only on the blueprint, the origin and the terrain blocks
 * of that column, so a building spanning several chunks is assembled identically in any order.
 */
public final class BlueprintPlacer {
    private static final int MAX_FOUNDATION = 28;

    private BlueprintPlacer() {
    }

    /**
     * @param origin world position of the rotated footprint's minimum corner at the floor level (y = 0)
     * @param clip   only positions inside are written (null = everything)
     */
    public static void place(WorldAccess world, Blueprint bp, BlockPos origin, BlockRotation rot, @Nullable BlockBox clip,
                             long lootSeed, @Nullable String lootTable) {
        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
        BlockPos.Mutable m = new BlockPos.Mutable();
        // 1. clear terrain out of the building volume (footprint plus a one-block margin for eaves)
        int rw = bp.rotatedWidth(rot), rd = bp.rotatedDepth(rot);
        int h = bp.height() + 2;
        for (int x = -1; x <= rw; x++) {
            for (int z = -1; z <= rd; z++) {
                int wx = origin.getX() + x, wz = origin.getZ() + z;
                if (clip != null && (wx < clip.getMinX() || wx > clip.getMaxX() || wz < clip.getMinZ() || wz > clip.getMaxZ())) {
                    continue;
                }
                for (int y = 1; y <= h; y++) {
                    m.set(wx, origin.getY() + y, wz);
                    BlockState cur = world.getBlockState(m);
                    if (!cur.isAir() && cur.getFluidState().isEmpty()) {
                        world.setBlockState(m, Blocks.AIR.getDefaultState(), flags);
                    }
                }
            }
        }
        // 2. blocks
        bp.forEach((lp, state) -> {
            BlockPos o = bp.transform(lp.getX(), lp.getY(), lp.getZ(), rot);
            int wx = origin.getX() + o.getX(), wy = origin.getY() + o.getY(), wz = origin.getZ() + o.getZ();
            if (clip != null && (wx < clip.getMinX() || wx > clip.getMaxX() || wz < clip.getMinZ() || wz > clip.getMaxZ())) {
                return;
            }
            BlockPos wp = new BlockPos(wx, wy, wz);
            world.setBlockState(wp, state.rotate(rot), flags);
            if (lootTable != null && state.hasBlockEntity()) {
                BlockEntity be = world.getBlockEntity(wp);
                if (be instanceof LootableContainerBlockEntity lc) {
                    lc.setLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(lootTable)), lootSeed ^ wp.asLong());
                }
            }
        });
        // 3. foundations: extend every footing (lowest block of a column at y <= 0) down to solid ground
        BlockState foundation = bp.foundation();
        for (int x = -1; x <= bp.width; x++) {
            for (int z = -1; z <= bp.depth; z++) {
                int low = Integer.MAX_VALUE;
                BlockState lowState = null;
                for (int y = -2; y <= 0; y++) {
                    BlockState s = bp.get(x, y, z);
                    if (s != null && !s.isAir()) {
                        low = y;
                        lowState = s;
                        break;
                    }
                }
                if (lowState == null) {
                    continue;
                }
                if (bp.stilts && !(lowState.isIn(BlockTags.LOGS) || lowState.isOf(foundation.getBlock()))) {
                    continue; // decks rest on their posts only
                }
                BlockPos o = bp.transform(x, 0, z, rot);
                int wx = origin.getX() + o.getX(), wz = origin.getZ() + o.getZ();
                if (clip != null && (wx < clip.getMinX() || wx > clip.getMaxX() || wz < clip.getMinZ() || wz > clip.getMaxZ())) {
                    continue;
                }
                BlockState fill = bp.stilts ? lowState.rotate(rot) : foundation;
                for (int y = origin.getY() + low - 1, n = 0; n < MAX_FOUNDATION; y--, n++) {
                    m.set(wx, y, wz);
                    BlockState cur = world.getBlockState(m);
                    boolean solid = isGround(world, m, cur);
                    if (solid) {
                        break;
                    }
                    world.setBlockState(m, fill, flags);
                }
            }
        }
    }

    /** Natural ground: solid, not fluid, not vegetation, logs or giant-fungus blocks (those vary with generation order). */
    public static boolean isGround(WorldAccess world, BlockPos p, BlockState st) {
        return !st.isAir() && st.getFluidState().isEmpty() && st.isSolidBlock(world, p) && !st.isIn(BlockTags.LEAVES)
                && !st.isIn(BlockTags.LOGS) && !(st.getBlock() instanceof net.minecraft.block.MushroomBlock)
                && !st.isIn(BlockTags.WART_BLOCKS);
    }

    /** Vanilla loot table that suits a building kind (containers start with fitting supplies). */
    @Nullable
    public static String lootFor(String kind, String profession) {
        return switch (kind) {
            case "house", "house_small", "house_large", "shared_house", "tent" -> "minecraft:chests/village/village_plains_house";
            case "workshop" -> switch (profession) {
                case "blacksmith", "smelter", "engineer", "miner" -> "minecraft:chests/village/village_toolsmith";
                case "tailor" -> "minecraft:chests/village/village_shepherd";
                case "stonemason" -> "minecraft:chests/village/village_mason";
                case "hunter" -> "minecraft:chests/village/village_fletcher";
                case "carpenter", "boatwright", "forester" -> "minecraft:chests/village/village_toolsmith";
                default -> "minecraft:chests/village/village_cartographer";
            };
            case "shop", "trade_hall", "stall", "storehouse" -> "minecraft:chests/village/village_cartographer";
            case "kitchen", "inn" -> "minecraft:chests/village/village_butcher";
            case "barn", "farm", "pen" -> "minecraft:chests/village/village_tannery";
            case "guard_post", "gate", "tower" -> "minecraft:chests/village/village_armorer";
            case "clinic", "library" -> "minecraft:chests/village/village_temple";
            case "dock" -> "minecraft:chests/village/village_fisher";
            default -> null;
        };
    }
}
