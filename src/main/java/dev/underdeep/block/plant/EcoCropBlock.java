package dev.underdeep.block.plant;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.item.ItemConvertible;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

import java.util.function.Supplier;

/**
 * Underground crop (age 0-7). Grows on farmland, and slowly on its wild soil (wild patches), under
 * its {@link GrowthRule} instead of the vanilla "light ≥ 9" rule: some crops prefer darkness, heat,
 * cold or water beside them. Hydrated farmland speeds growth like vanilla moisture does.
 */
public class EcoCropBlock extends CropBlock {
    protected final TagKey<Block> wildSoil;
    protected final GrowthRule rule;
    private final Supplier<ItemConvertible> seed;

    public EcoCropBlock(TagKey<Block> wildSoil, GrowthRule rule, Supplier<ItemConvertible> seed, Settings settings) {
        super(settings);
        this.wildSoil = wildSoil;
        this.rule = rule;
        this.seed = seed;
    }

    public GrowthRule growthRule() {
        return rule;
    }

    @Override
    public MapCodec<? extends CropBlock> getCodec() {
        return MapCodec.unit(this);
    }

    @Override
    protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
        return floor.isOf(Blocks.FARMLAND) || (wildSoil != null && floor.isIn(wildSoil));
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockPos below = pos.down();
        return canPlantOnTop(world.getBlockState(below), world, below);
    }

    @Override
    protected ItemConvertible getSeedsItem() {
        return seed.get();
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        int age = getAge(state);
        if (age >= getMaxAge()) {
            return;
        }
        double f = rule.factor(world, pos);
        if (f <= 0) {
            return;
        }
        boolean farmland = world.getBlockState(pos.down()).isOf(Blocks.FARMLAND);
        float moisture = getAvailableMoisture(this, world, pos);
        boolean rich = dev.underdeep.survival.home.RichSoilBlock.rich(world, pos);
        double chance = rule.chance() * f * (farmland ? 0.4 + 0.6 * Math.min(1.0, moisture / 6.0) : rich ? 1.0 : 0.3)
                * dev.underdeep.survival.home.RichSoilBlock.boost(world, pos);
        if (random.nextDouble() < chance) {
            world.setBlockState(pos, withAge(age + 1), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
        return !(world instanceof ServerWorld sw) || rule.factor(sw, pos) > 0;
    }
}
