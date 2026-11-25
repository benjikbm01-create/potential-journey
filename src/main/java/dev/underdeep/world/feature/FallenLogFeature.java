package dev.underdeep.world.feature;

import dev.underdeep.registry.ModPlants;
import dev.underdeep.registry.WoodSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * A fallen, decaying trunk lying on the cavern floor: sections of the species' log mixed with
 * rotting log, moss and small fungi on top, bracket fungi on the sides. Rotting logs keep sprouting
 * fungi over time (see {@link dev.underdeep.block.plant.RottingLogBlock}).
 */
public final class FallenLogFeature extends Feature<SpeciesFeatureConfig> {
    public FallenLogFeature() {
        super(SpeciesFeatureConfig.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<SpeciesFeatureConfig> ctx) {
        StructureWorldAccess w = ctx.getWorld();
        BlockPos o = ctx.getOrigin();
        Random r = ctx.getRandom();
        WoodSet set = WoodSet.ALL.stream().filter(ws -> ws.name.equals(ctx.getConfig().species())).findFirst().orElse(null);
        if (set == null) {
            return false;
        }
        Direction dir = r.nextBoolean() ? Direction.EAST : Direction.SOUTH;
        int len = 3 + r.nextInt(5);
        for (int i = 0; i < len; i++) {
            BlockPos p = o.offset(dir, i);
            if (!w.getBlockState(p).isAir() || !w.getBlockState(p.down()).isSolidBlock(w, p.down())) {
                if (i < 3) {
                    return false;
                }
                len = i;
                break;
            }
        }
        for (int i = 0; i < len; i++) {
            BlockPos p = o.offset(dir, i);
            boolean rot = r.nextFloat() < 0.45f;
            Block b = rot ? ModPlants.ROTTING_LOG : set.log;
            w.setBlockState(p, b.getDefaultState().with(PillarBlock.AXIS, dir.getAxis()), Block.NOTIFY_LISTENERS);
            BlockPos up = p.up();
            if (w.getBlockState(up).isAir()) {
                float f = r.nextFloat();
                BlockState top = f < 0.3f ? Blocks.MOSS_CARPET.getDefaultState()
                        : f < 0.42f ? ModPlants.PUFFCAP.getDefaultState()
                        : f < 0.5f ? ModPlants.INKCAP.getDefaultState() : null;
                if (top != null) {
                    w.setBlockState(up, top, Block.NOTIFY_LISTENERS);
                }
            }
            if (rot && r.nextFloat() < 0.5f) {
                Direction side = r.nextBoolean() ? dir.rotateYClockwise() : dir.rotateYCounterclockwise();
                BlockPos sp = p.offset(side);
                if (w.getBlockState(sp).isAir()) {
                    w.setBlockState(sp, ModPlants.BRACKET_FUNGUS.getDefaultState().with(HorizontalFacingBlock.FACING, side), Block.NOTIFY_LISTENERS);
                }
            }
        }
        return true;
    }
}
