package dev.underdeep.combat;

import dev.underdeep.wildlife.UnderdeepCreature;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The reusable Stage 10 fighting behaviour of creatures (replaces vanilla's instant melee for species that attack).
 * Every attack goes through approach → telegraph (posture, sound, particles) → strike (server hit check at that
 * moment: a dodged or out-of-reach target is simply missed) → recovery (the creature's punish window). Archetype
 * parameters make the same machinery read very differently: skirmishers bite and back off, stalkers circle and
 * pounce, chargers warn and rush in a straight line (and daze themselves on walls), bruisers telegraph slow ground
 * slams, swoopers dive from above, spitters keep their distance, defenders turn their armoured front to you, pack
 * flankers work around your sides while the leader holds your attention.
 * <p>
 * Bounded cost: path updates every 10 ticks at most, pack queries every 40 ticks, nothing scans the world.
 */
public final class CombatGoal extends Goal {
    /** Mobs whose current strike cannot be parried (heavy or charge): read by {@link Combat#UNPARRYABLE}. */
    static final Set<LivingEntity> UNPARRYABLE_NOW = Collections.newSetFromMap(new WeakHashMap<>());

    private final UnderdeepCreature c;
    private final Archetype a;
    private final double speed;
    private int phase; // 0 approach, 1 circle, 2 windup, 3 strike/charge, 4 recover, 5 retreat
    private int timer;
    private int attacks;
    private boolean heavy;
    private int repath;
    private double side;
    private Vec3d chargeDir;
    private int howlCooldown;

    public CombatGoal(UnderdeepCreature c, Archetype a, double speed) {
        this.c = c;
        this.a = a;
        this.speed = speed;
        this.side = (c.getUuid().getMostSignificantBits() & 1) == 0 ? 1 : -1;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    public Archetype archetype() {
        return a;
    }

    @Override
    public boolean canStart() {
        LivingEntity t = c.getTarget();
        return t != null && t.isAlive() && !c.isSitting() && !(t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    public boolean shouldContinue() {
        return canStart() && c.squaredDistanceTo(c.getTarget()) < 40 * 40;
    }

    @Override
    public void start() {
        phase = a.circle > 0 ? 1 : 0;
        timer = a.circle > 0 ? a.circle + c.getRandom().nextInt(Math.max(1, a.circle)) : 0;
        repath = 0;
        c.setAttacking(true);
    }

    @Override
    public void stop() {
        c.setAttacking(false);
        c.setState(UnderdeepCreature.IDLE);
        UNPARRYABLE_NOW.remove(c);
        c.getNavigation().stop();
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    double reach(LivingEntity t) {
        return c.getWidth() * 0.5 + t.getWidth() * 0.5 + 1.1 + a.reach;
    }

    @Override
    public void tick() {
        LivingEntity t = c.getTarget();
        if (t == null) {
            return;
        }
        double d = Math.sqrt(c.squaredDistanceTo(t));
        if (phase != 3 || a != Archetype.CHARGER) {
            c.getLookControl().lookAt(t, 30, 30);
        }
        timer--;
        switch (phase) {
            case 0 -> { // approach (or keep range)
                if (a.ranged > 0 && d < a.ranged * 0.6) {
                    retreatFrom(t);
                } else if (--repath <= 0) {
                    repath = 10;
                    if (a == Archetype.PACK_FLANKER) {
                        moveFlank(t);
                    } else {
                        c.getNavigation().startMovingTo(t, speed);
                    }
                }
                if (a == Archetype.PACK_LEADER && --howlCooldown <= 0) {
                    howl();
                }
                boolean inRange = a.ranged > 0 ? d < a.ranged + 2 && c.canSee(t) : d <= reach(t) + (a.lunge > 0.5 ? 2.5 : 0.4)
                        || a == Archetype.CHARGER && d < 14 && d > 4 && c.canSee(t) || a == Archetype.SWOOPER && d < 10;
                if (inRange && timer <= 0) {
                    beginWindup(t);
                }
            }
            case 1 -> { // circle at a distance before committing
                if (--repath <= 0) {
                    repath = 10;
                    Vec3d to = c.getPos().subtract(t.getPos()).multiply(1, 0, 1);
                    if (to.lengthSquared() < 0.01) {
                        to = new Vec3d(1, 0, 0);
                    }
                    Vec3d dir = to.normalize();
                    Vec3d tangent = new Vec3d(-dir.z * side, 0, dir.x * side);
                    Vec3d goal = t.getPos().add(dir.multiply(6)).add(tangent.multiply(4));
                    c.getNavigation().startMovingTo(goal.x, goal.y, goal.z, speed * 0.85);
                }
                if (timer <= 0) {
                    phase = 0;
                    timer = 0;
                }
            }
            case 2 -> { // telegraph
                c.getNavigation().stop();
                if (c.getWorld() instanceof ServerWorld w && timer % 4 == 0) {
                    w.spawnParticles(heavy ? ParticleTypes.ANGRY_VILLAGER : ParticleTypes.CRIT, c.getX(), c.getBodyY(0.9), c.getZ(), heavy ? 1 : 2,
                            c.getWidth() * 0.4, 0.2, c.getWidth() * 0.4, 0.01);
                    if (a == Archetype.BRUISER || a == Archetype.BURROWER) {
                        ring(w, t.getPos(), a == Archetype.BRUISER ? 3.2 : 1.6);
                    }
                }
                if (timer <= 0) {
                    strike(t, d);
                }
            }
            case 3 -> { // charging
                if (chargeDir != null) {
                    c.setVelocity(chargeDir.x * 0.55, c.getVelocity().y, chargeDir.z * 0.55);
                    c.velocityModified = true;
                    if (d < reach(t)) {
                        hit(t, 1.6, 0.9);
                        endStrike();
                    } else if (c.horizontalCollision) {
                        Poise.damage(c, 999, null, true); // runs into the wall: dazed
                        if (c.getWorld() instanceof ServerWorld w) {
                            w.playSound(null, c.getBlockPos(), SoundEvents.ENTITY_RAVAGER_STUNNED, SoundCategory.HOSTILE, 0.8f, 1.2f);
                        }
                        endStrike();
                    } else if (timer <= 0) {
                        endStrike();
                    }
                }
            }
            case 4 -> { // recovery: the opening
                if (timer <= 0) {
                    phase = a.retreat > 0 ? 5 : 0;
                    timer = a.retreat;
                }
            }
            case 5 -> {
                retreatFrom(t);
                if (timer <= 0) {
                    phase = a.circle > 0 ? 1 : 0;
                    timer = a.circle > 0 ? a.circle : 0;
                }
            }
            default -> phase = 0;
        }
    }

    private void beginWindup(LivingEntity t) {
        attacks++;
        heavy = a.heavyEvery > 0 && attacks % a.heavyEvery == 0;
        phase = 2;
        int base = a.windup * (heavy ? 2 : 1);
        if (Combat.cfg().assist) {
            base += 4;
        }
        timer = base;
        c.setState(UnderdeepCreature.WARN);
        c.getNavigation().stop();
        if (heavy || a == Archetype.CHARGER || a == Archetype.BURROWER) {
            UNPARRYABLE_NOW.add(c);
        }
        c.playAmbientSound();
        if (c.getWorld() instanceof ServerWorld w && (heavy || a == Archetype.CHARGER)) {
            w.playSound(null, c.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 0.35f, 1.6f);
        }
    }

    private void strike(LivingEntity t, double d) {
        c.setState(UnderdeepCreature.ATTACK);
        Vec3d to = t.getPos().subtract(c.getPos());
        Vec3d flat = new Vec3d(to.x, 0, to.z);
        Vec3d dir = flat.lengthSquared() < 1e-4 ? c.getRotationVector() : flat.normalize();
        switch (a) {
            case CHARGER -> {
                chargeDir = dir;
                phase = 3;
                timer = 30;
                return;
            }
            case SPITTER -> {
                spit(t);
                endStrike();
                return;
            }
            case SWOOPER -> {
                c.setVelocity(to.normalize().multiply(0.9));
                c.velocityModified = true;
            }
            case BRUISER, BURROWER -> {
                if (c.getWorld() instanceof ServerWorld w) {
                    w.spawnParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 0.1, t.getZ(), 1, 0, 0, 0, 0);
                    w.playSound(null, t.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 0.3f, 0.6f);
                }
                double radius = a == Archetype.BRUISER ? 3.2 : 1.8;
                for (LivingEntity e : c.getWorld().getEntitiesByClass(LivingEntity.class, t.getBoundingBox().expand(radius), e -> e != c && e.isAlive()
                        && (e == t || e instanceof PlayerEntity))) {
                    if (e.squaredDistanceTo(t.getX(), e.getY(), t.getZ()) <= radius * radius && Math.abs(e.getY() - t.getY()) < 2.5) {
                        hit(e, heavy ? 1.8 : 1.3, 0.6);
                        e.addVelocity(0, a == Archetype.BURROWER ? 0.5 : 0.25, 0);
                        e.velocityModified = true;
                    }
                }
                endStrike();
                return;
            }
            default -> {
                if (a.lunge > 0 && c.isOnGround()) {
                    c.addVelocity(dir.x * a.lunge, a == Archetype.STALKER ? 0.35 : 0.08, dir.z * a.lunge);
                    c.velocityModified = true;
                }
            }
        }
        // the strike lands only if the target is still in reach and in front now (dodging or stepping back works)
        boolean front = dir.dotProduct(c.getRotationVector().multiply(1, 0, 1).normalize()) > 0.2 || a.lunge > 0;
        if (Math.sqrt(c.squaredDistanceTo(t)) <= reach(t) + (a.lunge > 0.5 ? 1.5 : 0.3) && front) {
            hit(t, heavy ? 1.7 : 1.0, heavy ? 0.7 : 0.3);
        }
        endStrike();
    }

    private void hit(LivingEntity e, double mult, double knock) {
        float dmg = (float) (c.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE) * mult);
        if (e.damage(c.getDamageSources().mobAttack(c), dmg)) {
            e.takeKnockback(knock, c.getX() - e.getX(), c.getZ() - e.getZ());
            if (c.species().product().contains("venom")) {
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0), c);
            }
        }
    }

    private void endStrike() {
        UNPARRYABLE_NOW.remove(c);
        chargeDir = null;
        phase = 4;
        timer = a.recovery + (heavy ? 10 : 0);
    }

    private void spit(LivingEntity t) {
        CreatureSpit s = new CreatureSpit(c.getWorld(), c);
        Vec3d to = t.getEyePos().subtract(c.getEyePos());
        s.setVelocity(to.x, to.y + to.length() * 0.08, to.z, 1.1f, 2f);
        c.getWorld().spawnEntity(s);
        c.getWorld().playSound(null, c.getBlockPos(), SoundEvents.ENTITY_LLAMA_SPIT, SoundCategory.HOSTILE, 1f, 0.8f);
    }

    private void retreatFrom(LivingEntity t) {
        if (--repath > 0) {
            return;
        }
        repath = 10;
        Vec3d away = c.getPos().subtract(t.getPos()).multiply(1, 0, 1);
        if (away.lengthSquared() < 0.01) {
            away = new Vec3d(side, 0, 0);
        }
        Vec3d goal = c.getPos().add(away.normalize().multiply(6));
        c.getNavigation().startMovingTo(goal.x, goal.y, goal.z, speed);
    }

    private void moveFlank(LivingEntity t) {
        float yaw = t.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double ang = yaw + Math.PI / 2 + side * (Math.PI * 0.55);
        Vec3d goal = t.getPos().add(-Math.sin(ang) * 2.5, 0, Math.cos(ang) * 2.5);
        c.getNavigation().startMovingTo(goal.x, goal.y, goal.z, speed);
    }

    private void howl() {
        howlCooldown = 300;
        if (!(c.getWorld() instanceof ServerWorld w)) {
            return;
        }
        w.playSound(null, c.getBlockPos(), SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.HOSTILE, 1f, 0.8f);
        for (UnderdeepCreature o : w.getEntitiesByClass(UnderdeepCreature.class, c.getBoundingBox().expand(16), o -> o.species() == c.species() && o != c)) {
            o.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 120, 0), c);
            if (o.getTarget() == null && c.getTarget() != null) {
                o.setTarget(c.getTarget());
            }
        }
    }

    private static void ring(ServerWorld w, Vec3d at, double r) {
        for (int i = 0; i < 12; i++) {
            double ang = i * Math.PI / 6;
            w.spawnParticles(ParticleTypes.SMOKE, at.x + Math.cos(ang) * r, at.y + 0.1, at.z + Math.sin(ang) * r, 1, 0, 0, 0, 0);
        }
    }
}
