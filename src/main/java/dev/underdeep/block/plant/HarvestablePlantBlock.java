package dev.underdeep.block.plant;

import dev.underdeep.ecology.EcologyState;
import dev.underdeep.ecology.Habitats;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.event.GameEvent;

import java.util.function.Supplier;

/**
 * Bushes and herbs that grow through four stages (age 0-3) and can be harvested repeatedly when
 * mature: right-click yields produce and resets the plant to age 1, so it regrows (renewable food and
 * medicine). Growth follows the {@link GrowthRule}; outside its conditions the plant is dormant.
 * Breaking a plant always returns the plant itself (transplanting), plus produce when mature (loot table).
 */
public class HarvestablePlantBlock extends EcoPlantBlock {
    public static final IntProperty AGE = Properties.AGE_3;
    private final Supplier<Item> produce;
    private final int minCount;
    private final int maxCount;

    public HarvestablePlantBlock(TagKey<Block> soil, Supplier<Item> produce, int minCount, int maxCount, Settings settings) {
        super(soil, settings);
        this.produce = produce;
        this.minCount = minCount;
        this.maxCount = maxCount;
        setDefaultState(getDefaultState().with(AGE, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected boolean hasRandomTicks(BlockState state) {
        return state.get(AGE) < 3 || spreadChance > 0;
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        int age = state.get(AGE);
        if (age < 3) {
            double f = rule.factor(world, pos);
            if (f > 0 && random.nextFloat() < rule.chance() * f * dev.underdeep.survival.home.RichSoilBlock.boost(world, pos)) {
                world.setBlockState(pos, state.with(AGE, age + 1), Block.NOTIFY_LISTENERS);
                world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(state));
            }
        } else {
            super.randomTick(state, world, pos, random);
        }
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (state.get(AGE) < 3) {
            return ActionResult.PASS;
        }
        if (!world.isClient) {
            int n = minCount + world.random.nextInt(maxCount - minCount + 1);
            if (dev.underdeep.equipment.Accessories.flags(player).contains("forager") && world.random.nextFloat() < 0.3f) {
                n++;
            }
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                dev.underdeep.guide.Advancements.event(sp, "foraged", net.minecraft.registry.Registries.BLOCK.getId(this).getPath());
            }
            Block.dropStack(world, pos, new ItemStack(produce.get(), n));
            world.playSound(null, pos, SoundEvents.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, SoundCategory.BLOCKS, 1.0f,
                    0.8f + world.random.nextFloat() * 0.4f);
            world.setBlockState(pos, state.with(AGE, 1), Block.NOTIFY_LISTENERS);
            world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(player, state));
            EcologyState.recordHarvest((ServerWorld) world, pos, Habitats.speciesOf(state));
        }
        return ActionResult.success(world.isClient);
    }

    public Item produce() {
        return produce.get();
    }

    // bone meal advances one stage (only when conditions allow growth at all)
    @Override
    public boolean isFertilizable(WorldView world, BlockPos pos, BlockState state) {
        return state.get(AGE) < 3;
    }

    @Override
    public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
        return !(world instanceof ServerWorld sw) || rule.factor(sw, pos) > 0;
    }

    @Override
    public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state.with(AGE, Math.min(3, state.get(AGE) + 1)), Block.NOTIFY_LISTENERS);
    }
}
