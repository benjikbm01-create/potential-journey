package dev.underdeep.combat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Stagger ("poise") for ordinary mobs. Hits wear a mob's poise down by the attacker's style; at zero the mob is
 * staggered for a short time: its AI pauses (see {@code MobStaggerMixin}) and it takes extra damage — the opening
 * for a counterattack. Poise recovers after a few seconds without hits, and a mob cannot be re-staggered right away,
 * so stagger-locking is impossible. Bosses implement {@link Staggerable} and handle poise themselves.
 * Server thread only; entries vanish with their entities (weak keys).
 */
public final class Poise {
    /** Entities with their own poise and stagger rules (bosses). */
    public interface Staggerable {
        void poiseDamage(double amount, LivingEntity attacker, boolean forceStagger);

        boolean isStaggered();
    }

    private static final class P {
        double poise = -1;
        long lastHit;
        long staggerUntil;
        long immuneUntil;
    }

    private static final Map<LivingEntity, P> MAP = new WeakHashMap<>();
    public static final int STAGGER_TICKS = 30;

    private Poise() {
    }

    static double max(LivingEntity e) {
        return Math.max(5, 3 + e.getMaxHealth() * 0.22);
    }

    public static boolean staggered(LivingEntity e) {
        if (e instanceof Staggerable s) {
            return s.isStaggered();
        }
        P p = MAP.get(e);
        return p != null && p.staggerUntil > e.getWorld().getTime();
    }

    /** Applies stagger damage; returns true when this hit staggered the entity. */
    public static boolean damage(LivingEntity e, double amount, LivingEntity attacker, boolean force) {
        if (e instanceof Staggerable s) {
            boolean was = s.isStaggered();
            s.poiseDamage(amount, attacker, force);
            return !was && s.isStaggered();
        }
        if (!(e instanceof MobEntity) || !e.isAlive()) {
            return false;
        }
        long now = e.getWorld().getTime();
        P p = MAP.computeIfAbsent(e, k -> new P());
        if (p.poise < 0 || now - p.lastHit > 60) {
            p.poise = max(e);
        }
        p.lastHit = now;
        if (p.staggerUntil > now || p.immuneUntil > now && !force) {
            return false;
        }
        p.poise -= amount;
        if (p.poise <= 0 || force) {
            p.staggerUntil = now + STAGGER_TICKS;
            p.immuneUntil = now + STAGGER_TICKS + 50;
            p.poise = max(e);
            if (e.getWorld() instanceof ServerWorld w) {
                w.spawnParticles(ParticleTypes.CRIT, e.getX(), e.getBodyY(1.05), e.getZ(), 12, 0.3, 0.2, 0.3, 0.2);
                w.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1f, 0.6f);
            }
            if (e instanceof MobEntity m) {
                m.getNavigation().stop();
            }
            return true;
        }
        return false;
    }

    /** Called from the mixin while staggered: shows the daze. */
    public static void tickStaggered(MobEntity m) {
        if (m.getWorld() instanceof ServerWorld w && m.age % 6 == 0) {
            w.spawnParticles(ParticleTypes.CRIT, m.getX(), m.getBodyY(1.1), m.getZ(), 2, 0.25, 0.05, 0.25, 0.02);
        }
        m.setVelocity(m.getVelocity().multiply(0.3, 1, 0.3));
    }
}
