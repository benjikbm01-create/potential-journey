package dev.underdeep.block.plant;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.SaplingGenerator;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Sapling of an Underdeep tree species. Grows under block light (no sunlight needed) on its soil,
 * within its growth rule (e.g. cold species stay dormant near heat; volcanic species need heat).
 * The tree feature only fills air and plants, so a growing tree never replaces player blocks.
 */
public class EcoSaplingBlock extends SaplingBlock {
    private final TagKey<Block> soil;
    private final GrowthRule rule;
    private final int minLight;

    public EcoSaplingBlock(SaplingGenerator generator, TagKey<Block> soil, GrowthRule rule, int minLight, Settings settings) {
        super(generator, settings);
        this.soil = soil;
        this.rule = rule;
        this.minLight = minLight;
    }

    public GrowthRule growthRule() {
        return rule;
    }

    @Override
    public MapCodec<? extends SaplingBlock> getCodec() {
        return MapCodec.unit(this);
    }

    @Override
    protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
        return floor.isIn(soil);
    }

    private boolean conditions(ServerWorld world, BlockPos pos) {
        return world.getLightLevel(pos.up()) >= minLight && rule.factor(world, pos) > 0;
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (conditions(world, pos) && random.nextFloat() < rule.chance()) {
            generate(world, pos, state, random);
        }
    }

    @Override
    public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
        return world instanceof ServerWorld sw ? conditions(sw, pos) && random.nextFloat() < 0.45f : true;
    }
}
