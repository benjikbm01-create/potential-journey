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
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Reactive predatory plant. While open it snaps shut on a creature stepping into it (3 damage and a
 * short slowdown), then stays closed for about 6 seconds. Sneaking players can pass carefully.
 * Understandable and avoidable: visible open jaws, a loud snap, and a long reset.
 */
public class SnapjawBlock extends EcoPlantBlock {
    public static final BooleanProperty OPEN = BooleanProperty.of("open");

    public SnapjawBlock(TagKey<Block> soil, Settings settings) {
        super(soil, settings);
        setDefaultState(getDefaultState().with(OPEN, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world.isClient || !state.get(OPEN) || !(entity instanceof LivingEntity living) || !UnderdeepConfig.get().reactivePlants) {
            return;
        }
        if (entity instanceof PlayerEntity p && (p.isSneaking() || p.isCreative() || p.isSpectator())) {
            return;
        }
        living.damage(world.getDamageSources().cactus(), 3.0f);
        living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
        world.setBlockState(pos, state.with(OPEN, false), Block.NOTIFY_ALL);
        world.playSound(null, pos, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.BLOCKS, 0.8f, 1.4f);
        ((ServerWorld) world).spawnParticles(ParticleTypes.CRIMSON_SPORE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
        world.scheduleBlockTick(pos, this, 120);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!state.get(OPEN)) {
            world.setBlockState(pos, state.with(OPEN, true), Block.NOTIFY_ALL);
        }
    }
}
