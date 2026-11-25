package dev.underdeep.block.plant;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LilyPadBlock;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Floating water-surface plant (lily-pad family) that slowly spreads across still water. */
public class FloatingPlantBlock extends LilyPadBlock {
    private float spreadChance;
    private int cap = 6;
    private ParticleEffect ambient;

    public FloatingPlantBlock(Settings settings) {
        super(settings);
    }

    public FloatingPlantBlock spreads(float chance, int cap) {
        this.spreadChance = chance;
        this.cap = cap;
        return this;
    }

    public FloatingPlantBlock particles(ParticleEffect p) {
        this.ambient = p;
        return this;
    }

    @Override
    public MapCodec<LilyPadBlock> getCodec() {
        return MapCodec.unit(this);
    }

    @Override
    protected boolean hasRandomTicks(BlockState state) {
        return spreadChance > 0;
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (Env.spreadingEnabled(world) && Env.roll(random, spreadChance)) {
            BlockState child = getDefaultState();
            Spreading.trySpread(world, pos, child, 3, cap, random, (w, p) -> w.getBlockState(p).isAir()
                    && w.getBlockState(p.down()).isOf(Blocks.WATER) && w.getFluidState(p.down()).isStill());
        }
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (ambient != null && random.nextInt(10) == 0 && ParticleGate.allow()) {
            world.addParticle(ambient, pos.getX() + random.nextDouble(), pos.getY() + 0.2, pos.getZ() + random.nextDouble(), 0, 0.01, 0);
        }
    }

    static int flags() {
        return Block.NOTIFY_ALL;
    }
}
