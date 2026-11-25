package dev.underdeep.block.plant;

import dev.underdeep.config.UnderdeepConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Supplier;

/**
 * Puffball that bursts in a visible spore cloud when touched or (rarely) on its own. A burst seeds
 * one new fungus nearby (bounded spreading) and, if enabled, briefly weakens creatures right next to
 * it. After bursting it is "spent" and refills slowly, so releases are occasional, not constant.
 */
public class SporePuffBlock extends EcoPlantBlock {
    public static final BooleanProperty SPENT = BooleanProperty.of("spent");
    private final List<Supplier<Block>> offspring;

    public SporePuffBlock(TagKey<Block> soil, List<Supplier<Block>> offspring, Settings settings) {
        super(soil, settings);
        this.offspring = offspring;
        setDefaultState(getDefaultState().with(SPENT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(SPENT);
    }

    @Override
    protected boolean hasRandomTicks(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (state.get(SPENT)) {
            if (random.nextInt(4) == 0) {
                world.setBlockState(pos, state.with(SPENT, false), Block.NOTIFY_LISTENERS);
            }
        } else if (random.nextInt(10) == 0) {
            burst(state, world, pos, random);
        }
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!world.isClient && !state.get(SPENT) && entity instanceof LivingEntity) {
            burst(state, (ServerWorld) world, pos, world.random);
        }
    }

    public void burst(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        UnderdeepConfig c = UnderdeepConfig.get();
        if (!c.sporeReleases) {
            return;
        }
        world.setBlockState(pos, state.with(SPENT, true), Block.NOTIFY_LISTENERS);
        world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                40, 1.2, 0.8, 1.2, 0.01);
        world.spawnParticles(ParticleTypes.WARPED_SPORE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                25, 1.5, 1.0, 1.5, 0.0);
        world.playSound(null, pos, SoundEvents.BLOCK_FUNGUS_BREAK, SoundCategory.BLOCKS, 0.8f, 0.6f);
        if (Env.spreadingEnabled(world) && !offspring.isEmpty()) {
            BlockState child = offspring.get(random.nextInt(offspring.size())).get().getDefaultState();
            Spreading.trySpread(world, pos, child, 3, 0, random, Spreading.land(child));
        }
        if (c.sporeEffects) {
            for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, new Box(pos).expand(1.5), x -> true)) {
                if (!(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()))) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0));
                }
            }
        }
    }
}
