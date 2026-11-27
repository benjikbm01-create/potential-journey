package dev.underdeep.boss;

import dev.underdeep.Underdeep;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Boss projectiles: slow and visible (a shield blocks them, a raised shield in time deflects them, dodging avoids
 * them). Kinds differ in look, speed, homing and side effect. Bounded: each lives at most a few seconds.
 */
public class BossProjectile extends ThrownItemEntity {
    public enum Kind {
        ORB(Items.ENDER_EYE, ParticleTypes.END_ROD, 0.04, 0),
        SPORE(Items.BROWN_MUSHROOM, ParticleTypes.SPORE_BLOSSOM_AIR, 0, 1),
        SHARD(Items.AMETHYST_SHARD, ParticleTypes.ELECTRIC_SPARK, 0, 0),
        EMBER(Items.FIRE_CHARGE, ParticleTypes.FLAME, 0.01, 2),
        TIDE(Items.HEART_OF_THE_SEA, ParticleTypes.BUBBLE_POP, 0.02, 3),
        THORN(Items.STICK, ParticleTypes.COMPOSTER, 0, 1),
        STAR(Items.NETHER_STAR, ParticleTypes.FIREWORK, 0.03, 4),
        ROCK(Items.COBBLESTONE, ParticleTypes.CAMPFIRE_COSY_SMOKE, 0, 0),
        FROST(Items.SNOWBALL, ParticleTypes.SNOWFLAKE, 0, 5);

        final Item item;
        final ParticleEffect trail;
        final double homing;
        final int effect;

        Kind(Item item, ParticleEffect trail, double homing, int effect) {
            this.item = item;
            this.trail = trail;
            this.homing = homing;
            this.effect = effect;
        }
    }

    public static EntityType<BossProjectile> TYPE;
    private static final TrackedData<Byte> KIND = DataTracker.registerData(BossProjectile.class, TrackedDataHandlerRegistry.BYTE);
    private double mult = 1;
    private LivingEntity homingTarget;

    public static void register() {
        TYPE = Registry.register(Registries.ENTITY_TYPE, Identifier.of(Underdeep.MOD_ID, "boss_projectile"),
                EntityType.Builder.<BossProjectile>create(BossProjectile::new, SpawnGroup.MISC).dimensions(0.5f, 0.5f).maxTrackingRange(8)
                        .trackingTickInterval(2).build());
    }

    public BossProjectile(EntityType<? extends BossProjectile> type, World world) {
        super(type, world);
    }

    public BossProjectile(World world, BossEntity owner, Kind kind, double mult) {
        super(TYPE, owner, world);
        this.mult = mult;
        dataTracker.set(KIND, (byte) kind.ordinal());
        setItem(new ItemStack(kind.item));
        setNoGravity(true);
        homingTarget = owner.target();
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(KIND, (byte) 0);
    }

    public Kind kind() {
        return Kind.values()[Math.min(Kind.values().length - 1, dataTracker.get(KIND))];
    }

    @Override
    protected Item getDefaultItem() {
        return Items.ENDER_EYE;
    }

    @Override
    protected double getGravity() {
        return 0;
    }

    @Override
    public void tick() {
        super.tick();
        Kind k = kind();
        if (getWorld() instanceof ServerWorld w) {
            w.spawnParticles(k.trail, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0);
            if (k.homing > 0 && homingTarget != null && homingTarget.isAlive() && age < 60) {
                Vec3d to = homingTarget.getEyePos().subtract(getPos()).normalize();
                double sp = getVelocity().length();
                setVelocity(getVelocity().normalize().multiply(1 - k.homing).add(to.multiply(k.homing)).normalize().multiply(sp));
                velocityModified = true;
            }
            if (age > 120) {
                discard();
            }
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        if (!(getOwner() instanceof BossEntity b) || !(hit.getEntity() instanceof LivingEntity l) || l instanceof BossEntity) {
            return;
        }
        if (l.damage(getDamageSources().mobProjectile(this, b), (float) (b.damage() * mult))) {
            switch (kind().effect) {
                case 1 -> l.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0), b);
                case 2 -> l.setOnFireFor(3);
                case 3 -> l.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 1), b);
                case 4 -> l.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 20, 0), b);
                case 5 -> l.setFrozenTicks(Math.min(l.getMinFreezeDamageTicks() + 40, l.getFrozenTicks() + 80));
                default -> {
                }
            }
        }
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (!getWorld().isClient) {
            if (getWorld() instanceof ServerWorld w) {
                w.spawnParticles(kind().trail, getX(), getY(), getZ(), 8, 0.2, 0.2, 0.2, 0.05);
            }
            discard();
        }
    }
}
