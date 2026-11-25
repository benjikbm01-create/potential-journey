package dev.underdeep.block.plant;

import dev.underdeep.registry.ModTags;
import dev.underdeep.world.region.RegionType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Glowing fungal soil (sporeglow soil). Inside the Fungal Expanse it slowly creeps into adjacent
 * natural dirt, grass and moss in dim light, mycelium-style. It never converts farmland, paths or
 * anything near player-made blocks, never spreads outside its native ecosystem, and stops where the
 * neighbourhood is already mostly fungal soil, so it cannot overrun bases or other regions.
 */
public class FungalSoilBlock extends Block {
    public FungalSoilBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected boolean hasRandomTicks(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!Env.spreadingEnabled(world) || !Env.roll(random, 0.12)) {
            return;
        }
        if (Env.region(world, pos) != RegionType.FUNGAL_EXPANSE || world.getLightLevel(pos.up()) > 13) {
            return;
        }
        BlockPos t = pos.add(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
        BlockState ts = world.getBlockState(t);
        if (!ts.isIn(ModTags.FUNGAL_CONVERTIBLE) || world.getBlockState(t.up()).isOpaqueFullCube(world, t.up())) {
            return;
        }
        if (Env.count(world, t, 2, 1, s -> s.isOf(this)) > 20 || Env.protectedArea(world, t)) {
            return;
        }
        world.setBlockState(t, getDefaultState(), Block.NOTIFY_ALL);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(40) == 0 && world.getBlockState(pos.up()).isAir() && ParticleGate.allow()) {
            world.addParticle(ParticleTypes.WARPED_SPORE, pos.getX() + random.nextDouble(), pos.getY() + 1.1, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        }
    }
}
