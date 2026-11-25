package dev.underdeep.block.plant;

import com.mojang.serialization.MapCodec;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.ecology.Habitats;
import dev.underdeep.ecology.Species;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * Configurable small land plant or fungus: soil requirement, growth rule, bounded natural spreading,
 * optional contact effects (toxic or thorny) and ambient particles. Most Stage 2 flora are instances
 * of this class (or a subclass) configured fluently at registration.
 */
public class EcoPlantBlock extends PlantBlock implements Fertilizable {
    protected final TagKey<Block> soil;
    protected GrowthRule rule = GrowthRule.ANY;
    protected float spreadChance;
    protected int spreadRadius = 2;
    protected int spreadCap;
    protected VoxelShape shape = Block.createCuboidShape(2, 0, 2, 14, 13, 14);
    @Nullable
    protected StatusEffectInstance contactEffect;
    protected float thornDamage;
    @Nullable
    protected ParticleEffect ambient;
    protected float ambientChance;

    public EcoPlantBlock(TagKey<Block> soil, Settings settings) {
        super(settings);
        this.soil = soil;
    }

    // ---- fluent configuration (registration time only) ----
    public EcoPlantBlock rule(GrowthRule r) { this.rule = r; return this; }
    public EcoPlantBlock spreads(float chance, int radius, int cap) { this.spreadChance = chance; this.spreadRadius = radius; this.spreadCap = cap; return this; }
    public EcoPlantBlock shape(double w, double h) { double m = (16 - w) / 2; this.shape = Block.createCuboidShape(m, 0, m, 16 - m, h, 16 - m); return this; }
    public EcoPlantBlock contact(StatusEffectInstance effect) { this.contactEffect = effect; return this; }
    public EcoPlantBlock thorns(float damage) { this.thornDamage = damage; return this; }
    public EcoPlantBlock particles(ParticleEffect p, float chance) { this.ambient = p; this.ambientChance = chance; return this; }

    public GrowthRule growthRule() {
        return rule;
    }

    @Override
    protected MapCodec<? extends PlantBlock> getCodec() {
        return MapCodec.unit(this);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        Vec3d off = state.getModelOffset(world, pos);
        return shape.offset(off.x, off.y, off.z);
    }

    @Override
    protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
        return soil == null ? floor.isSideSolidFullSquare(world, pos, Direction.UP) : floor.isIn(soil);
    }

    @Override
    protected boolean hasRandomTicks(BlockState state) {
        return spreadChance > 0;
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (spreadChance > 0 && Env.spreadingEnabled(world)) {
            double f = nativeFactor(world, pos) * rule.factor(world, pos);
            if (f > 0 && Env.roll(random, spreadChance * f)) {
                spread(state, world, pos, random);
            }
        }
    }

    /** Full rate inside the species' native ecosystems, a quarter elsewhere (transplanted gardens). */
    protected double nativeFactor(ServerWorld world, BlockPos pos) {
        Species s = Habitats.speciesOf(getDefaultState());
        if (s == null) {
            return 1;
        }
        var region = Env.region(world, pos);
        return region != null && s.habitats().contains(region) ? 1.0 : 0.25;
    }

    protected boolean spread(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        BlockState child = getDefaultState();
        return Spreading.trySpread(world, pos, child, spreadRadius, spreadCap, random, Spreading.land(child));
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world.isClient || !(entity instanceof LivingEntity living) || !UnderdeepConfig.get().reactivePlants) {
            return;
        }
        if (contactEffect != null && !living.hasStatusEffect(contactEffect.getEffectType())) {
            living.addStatusEffect(new StatusEffectInstance(contactEffect));
        }
        if (thornDamage > 0 && (entity.lastRenderX != entity.getX() || entity.lastRenderZ != entity.getZ())) {
            double dx = Math.abs(entity.getX() - entity.lastRenderX);
            double dz = Math.abs(entity.getZ() - entity.lastRenderZ);
            if (dx >= 0.003 || dz >= 0.003) {
                entity.damage(world.getDamageSources().sweetBerryBush(), thornDamage);
            }
        }
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (ambient != null && random.nextFloat() < ambientChance && ParticleGate.allow()) {
            world.addParticle(ambient, pos.getX() + random.nextDouble(), pos.getY() + 0.3 + random.nextDouble() * 0.7,
                    pos.getZ() + random.nextDouble(), (random.nextDouble() - 0.5) * 0.01, 0.01, (random.nextDouble() - 0.5) * 0.01);
        }
    }

    // ---- bone meal encourages spreading of spreading species ----
    @Override
    public boolean isFertilizable(WorldView world, BlockPos pos, BlockState state) {
        return spreadChance > 0;
    }

    @Override
    public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
        for (int i = 0; i < 6; i++) {
            spread(state, world, pos, random);
        }
    }
}
