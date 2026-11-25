package dev.underdeep.block.plant;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * Temporary fungal bloom (lanterncap): natural colonies sprout (age 0), brighten as they mature,
 * spread young caps while in their prime and finally fade away, so glowing blooms wander slowly
 * through the Fungal Expanse. Player-placed caps are {@code persistent} and never fade.
 */
public class EphemeralFungusBlock extends EcoPlantBlock {
    public static final IntProperty AGE = Properties.AGE_3;
    public static final BooleanProperty PERSISTENT = Properties.PERSISTENT;

    public EphemeralFungusBlock(TagKey<Block> soil, Settings settings) {
        super(soil, settings);
        setDefaultState(getDefaultState().with(AGE, 0).with(PERSISTENT, false));
    }

    /** Luminance by age: sprout 6, young 10, prime 13, fading 8. */
    public static int light(BlockState s) {
        return switch (s.get(AGE)) {
            case 0 -> 6;
            case 1 -> 10;
            case 2 -> 13;
            default -> 8;
        };
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AGE, PERSISTENT);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState s = super.getPlacementState(ctx);
        return s == null ? null : s.with(AGE, 2).with(PERSISTENT, true);
    }

    @Override
    protected boolean hasRandomTicks(BlockState state) {
        return !state.get(PERSISTENT);
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!Env.spreadingEnabled(world)) {
            return;
        }
        int age = state.get(AGE);
        if (age < 3) {
            if (random.nextInt(age == 2 ? 6 : 3) == 0) {
                world.setBlockState(pos, state.with(AGE, age + 1), Block.NOTIFY_LISTENERS);
            } else if (age >= 1) {
                double f = rule.factor(world, pos) * nativeFactor(world, pos);
                if (f > 0 && Env.roll(random, spreadChance * f)) {
                    spread(state, world, pos, random);
                }
            }
        } else if (random.nextInt(5) == 0) {
            world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.0);
            world.removeBlock(pos, false);
        }
    }
}
