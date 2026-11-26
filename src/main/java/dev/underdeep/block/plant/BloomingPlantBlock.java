package dev.underdeep.block.plant;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * A flower that opens ("blooms") only under its trigger condition and closes otherwise. Blooming
 * flowers emit more light (luminance is state-dependent, so it is real block light), release pollen
 * particles and are the only state in which the species spreads seeds.
 * <p>
 * Voltbloom-style ({@link Trigger#ENERGY}) flowers react immediately to redstone power in the block
 * below, or to charged stormstone nearby.
 */
public class BloomingPlantBlock extends EcoPlantBlock {
    public static final BooleanProperty BLOOMING = BooleanProperty.of("blooming");

    public enum Trigger { BRIGHT, DARK, HEAT, WATER, COLD, ENERGY }

    private final Trigger trigger;

    public BloomingPlantBlock(TagKey<Block> soil, Trigger trigger, Settings settings) {
        super(soil, settings);
        this.trigger = trigger;
        setDefaultState(getDefaultState().with(BLOOMING, false));
    }

    public static int light(BlockState s, int closed, int open) {
        return s.get(BLOOMING) ? open : closed;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(BLOOMING);
    }

    @Override
    protected boolean hasRandomTicks(BlockState state) {
        return true;
    }

    public boolean conditionMet(World world, BlockPos pos) {
        return conditionMet(world, pos, world.getBlockState(pos));
    }

    /**
     * For the DARK trigger an open flower must not be closed by its own glow: while open it judges
     * darkness from the light 4 blocks away, where its own contribution is (open light - 4).
     */
    public boolean conditionMet(World world, BlockPos pos, BlockState state) {
        return switch (trigger) {
            case BRIGHT -> Env.light(world, pos) >= 10;
            case DARK -> {
                if (state.isOf(this) && state.get(BLOOMING)) {
                    int own = Math.max(0, state.getLuminance() - 4);
                    int max = 0;
                    for (var d : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                        max = Math.max(max, world.getLightLevel(net.minecraft.world.LightType.BLOCK, pos.offset(d, 4)));
                    }
                    yield max <= own + 1;
                }
                yield Env.light(world, pos) <= 5;
            }
            case HEAT -> Env.nearHeat(world, pos, 3);
            case WATER -> Env.nearWater(world, pos, 2);
            case COLD -> Env.temperature(world, pos) == Env.Temp.COLD && !Env.nearHeat(world, pos, 3);
            case ENERGY -> world.isReceivingRedstonePower(pos.down()) || world.isReceivingRedstonePower(pos)
                    || Env.any(world, pos, 2, 2, s -> s.isOf(dev.underdeep.registry.ModBlocks.CHARGED_STORMSTONE));
        };
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState s = super.getPlacementState(ctx);
        return s == null ? null : s.with(BLOOMING, conditionMet(ctx.getWorld(), ctx.getBlockPos()));
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        boolean want = conditionMet(world, pos, state);
        if (want != state.get(BLOOMING) && random.nextInt(3) == 0) {
            world.setBlockState(pos, state.with(BLOOMING, want), Block.NOTIFY_LISTENERS);
            return;
        }
        if (state.get(BLOOMING)) {
            super.randomTick(state, world, pos, random);
        }
    }

    @Override
    protected boolean spread(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        BlockState child = getDefaultState().with(BLOOMING, false);
        return Spreading.trySpread(world, pos, child, spreadRadius, spreadCap, random, Spreading.land(child));
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (!world.isClient && trigger == Trigger.ENERGY) {
            boolean want = conditionMet(world, pos);
            if (want != state.get(BLOOMING)) {
                world.setBlockState(pos, state.with(BLOOMING, want), Block.NOTIFY_LISTENERS);
            }
        }
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (state.get(BLOOMING) && random.nextInt(4) == 0 && ParticleGate.allow()) {
            var p = ambient != null ? ambient : ParticleTypes.SPORE_BLOSSOM_AIR;
            world.addParticle(p, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 0.6 + random.nextDouble() * 0.4,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, 0.015, 0);
        }
    }
}
