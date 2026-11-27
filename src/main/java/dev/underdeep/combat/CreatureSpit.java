package dev.underdeep.combat;

import dev.underdeep.Underdeep;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/** A spitter's venom glob: a visible, dodgeable and shield-blockable projectile. */
public class CreatureSpit extends ThrownItemEntity {
    public static EntityType<CreatureSpit> TYPE;

    public static void register() {
        TYPE = Registry.register(Registries.ENTITY_TYPE, Identifier.of(Underdeep.MOD_ID, "creature_spit"),
                EntityType.Builder.<CreatureSpit>create(CreatureSpit::new, SpawnGroup.MISC).dimensions(0.3f, 0.3f).maxTrackingRange(4)
                        .trackingTickInterval(5).build());
    }

    public CreatureSpit(EntityType<? extends CreatureSpit> type, World world) {
        super(type, world);
    }

    public CreatureSpit(World world, LivingEntity owner) {
        super(TYPE, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SLIME_BALL;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld() instanceof ServerWorld w && age % 2 == 0) {
            w.spawnParticles(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0);
        }
        if (age > 80) {
            discard();
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        if (hit.getEntity() instanceof LivingEntity l && getOwner() instanceof LivingEntity owner && l != owner) {
            float dmg = (float) Math.max(2, owner.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE) * 0.8);
            if (l.damage(getDamageSources().mobProjectile(this, owner), dmg)) {
                l.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, 0), owner);
            }
        }
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (!getWorld().isClient) {
            discard();
        }
    }
}
