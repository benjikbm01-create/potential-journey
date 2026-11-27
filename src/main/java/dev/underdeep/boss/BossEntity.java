package dev.underdeep.boss;

import dev.underdeep.combat.Poise;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The common body of every Stage 10 boss. A boss is never saved with chunks: it exists only while its encounter is
 * active (see {@link Encounters}); if it unloads or the server stops, the encounter resets safely and can be begun
 * again. Behaviour is a move state machine — idle/approach → wind-up (telegraph) → active → recovery (punish
 * window) — fed by the boss's {@link Brain}. Poise breaks into a stagger (the big opening), phases change the fight at
 * health thresholds, and a short defeat sequence plays before death. Clients receive move, stage, phase and flags for
 * animation only; every hit is decided here.
 */
public class BossEntity extends HostileEntity implements Poise.Staggerable {
    public static final byte IDLE = 0, WINDUP = 1, ACTIVE = 2, RECOVER = 3, STAGGER = 4, INTRO = 5, DEFEAT = 6, TRANSITION = 7;
    private static final TrackedData<Byte> MOVE = DataTracker.registerData(BossEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Byte> STAGE = DataTracker.registerData(BossEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Byte> PHASE = DataTracker.registerData(BossEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Byte> FLAGS = DataTracker.registerData(BossEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Integer> STAGE_LEN = DataTracker.registerData(BossEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public final BossDef def;
    public final Brain brain;
    @Nullable
    public Encounters.Active enc;
    private int stage = INTRO, stageTick, stageLen = 50;
    private Move current;
    private final Map<Move, Long> cooldowns = new HashMap<>();
    private int phase;
    private LivingEntity target;
    private double poise, poiseMax;
    private long staggerUntil, staggerImmune;
    private int unreachable;
    private int repath;
    private int idleGap = 10;
    private final Map<UUID, Double> threat = new HashMap<>();
    private Vec3d lockedDir;
    /** Health beyond vanilla's 1024 cap is represented by dividing incoming damage (keeps the fight length right). */
    private double healthFactor = 1;
    final List<Entity> minions = new ArrayList<>();
    /** Client-side animation clock of the current stage. */
    public int clientStageAge;
    private byte lastClientStage = -1, lastClientMove = -1;

    public BossEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.def = Bosses.def(type);
        this.brain = def.brain().apply(this);
        this.experiencePoints = def.tier().ordinal() * 40 + 50;
        this.poiseMax = def.poise();
        this.poise = poiseMax;
        if (def.flying()) {
            setNoGravity(true);
        }
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(MOVE, (byte) 0);
        builder.add(STAGE, INTRO);
        builder.add(PHASE, (byte) 0);
        builder.add(FLAGS, (byte) 0);
        builder.add(STAGE_LEN, 50);
    }

    // ----------------------------------------------------------------------------------- persistence rules

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean isDisallowedInPeaceful() {
        return false;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!getWorld().isClient && enc != null && !enc.ended && reason != RemovalReason.KILLED) {
            Encounters.lost(enc, reason == RemovalReason.DISCARDED ? "reset" : "unloaded");
        }
        super.remove(reason);
    }

    // ----------------------------------------------------------------------------------- synced state

    public byte moveId() {
        return dataTracker.get(MOVE);
    }

    public byte stage() {
        return dataTracker.get(STAGE);
    }

    public int phase() {
        return dataTracker.get(PHASE);
    }

    public int stageLength() {
        return dataTracker.get(STAGE_LEN);
    }

    public byte flags() {
        return dataTracker.get(FLAGS);
    }

    public void setFlag(int bit, boolean on) {
        byte f = flags();
        dataTracker.set(FLAGS, (byte) (on ? f | 1 << bit : f & ~(1 << bit)));
    }

    public boolean flag(int bit) {
        return (flags() & 1 << bit) != 0;
    }

    private void setStage(int s, int len) {
        stage = s;
        stageTick = 0;
        stageLen = Math.max(1, len);
        dataTracker.set(STAGE, (byte) s);
        dataTracker.set(STAGE_LEN, stageLen);
        dataTracker.set(MOVE, current == null ? 0 : current.id);
    }

    public int phaseIndex() {
        return phase;
    }

    public int stageTicks() {
        return stageTick;
    }

    @Nullable
    public LivingEntity target() {
        return target;
    }

    // ----------------------------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            byte s = stage(), m = moveId();
            if (s != lastClientStage || m != lastClientMove) {
                clientStageAge = 0;
                lastClientStage = s;
                lastClientMove = m;
            } else {
                clientStageAge++;
            }
        }
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (enc == null || enc.ended) {
            discard();
            return;
        }
        ServerWorld w = (ServerWorld) getWorld();
        stageTick++;
        if (age % 20 == 0 || target == null || !target.isAlive()) {
            target = brain.pickTarget(enc.fighters());
            setTarget(target);
        }
        switch (stage) {
            case INTRO, TRANSITION -> {
                getNavigation().stop();
                if (target != null) {
                    getLookControl().lookAt(target, 20, 20);
                }
                if (stageTick % 5 == 0) {
                    w.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getBodyY(0.5), getZ(), 4, getWidth() * 0.4, getHeight() * 0.3, getWidth() * 0.4, 0.02);
                }
                if (stageTick >= stageLen) {
                    current = null;
                    setStage(IDLE, idleGap);
                }
                return;
            }
            case DEFEAT -> {
                getNavigation().stop();
                setVelocity(getVelocity().multiply(0.2, 1, 0.2));
                if (stageTick % 3 == 0) {
                    w.spawnParticles(ParticleTypes.POOF, getX(), getBodyY(0.5), getZ(), 6, getWidth() * 0.5, getHeight() * 0.4, getWidth() * 0.5, 0.05);
                }
                if (stageTick >= stageLen) {
                    setHealth(0);
                    onDeath(getRecentDamageSource() != null ? getRecentDamageSource() : getDamageSources().generic());
                }
                return;
            }
            default -> {
            }
        }
        brain.tickZones();
        brain.tick();
        if (isStaggered()) {
            getNavigation().stop();
            if (stageTick % 6 == 0) {
                w.spawnParticles(ParticleTypes.CRIT, getX(), getY() + getHeight() + 0.3, getZ(), 4, getWidth() * 0.3, 0.1, getWidth() * 0.3, 0.05);
            }
            return;
        } else if (stage == STAGGER) {
            current = null;
            setStage(IDLE, 6);
        }
        leash(w);
        if (target == null) {
            if (stage == IDLE && enc.center != null && squaredDistanceTo(Vec3d.ofBottomCenter(enc.center)) > 16 && brain.walks()) {
                getNavigation().startMovingTo(enc.center.getX() + 0.5, enc.center.getY(), enc.center.getZ() + 0.5, 1.0);
            }
            return;
        }
        double d = Math.sqrt(squaredDistanceTo(target));
        trackReachability(d);
        switch (stage) {
            case IDLE -> {
                if (brain.walks() && d > 2.5 + getWidth() * 0.5 && --repath <= 0) {
                    repath = 10;
                    getNavigation().startMovingTo(target, def.chaseSpeed());
                }
                getLookControl().lookAt(target, 30, 30);
                if (stageTick >= stageLen) {
                    Move m = choose(d);
                    if (m != null) {
                        current = m;
                        lockedDir = null;
                        getNavigation().stop();
                        setStage(WINDUP, scaledWindup(m));
                    } else {
                        stageLen = stageTick + 5;
                    }
                }
            }
            case WINDUP -> {
                if (current.lockFacing && lockedDir == null && stageTick >= stageLen - 4) {
                    lockedDir = flatDirTo(target);
                }
                if (!current.lockFacing || lockedDir == null) {
                    getLookControl().lookAt(target, 30, 30);
                    faceTowards(target.getPos());
                }
                if (current.onWindup != null) {
                    current.onWindup.run(this, target, stageTick);
                }
                if (stageTick >= stageLen) {
                    if (lockedDir == null) {
                        lockedDir = flatDirTo(target);
                    }
                    setStage(ACTIVE, current.active);
                    if (current.onStart != null) {
                        current.onStart.run(this, target, 0);
                    }
                }
            }
            case ACTIVE -> {
                if (current.onActive != null) {
                    current.onActive.run(this, target, stageTick);
                }
                if (stageTick >= stageLen) {
                    if (current.onEnd != null) {
                        current.onEnd.run(this, target, stageTick);
                    }
                    cooldowns.put(current, getWorld().getTime() + current.cooldown);
                    setStage(RECOVER, current.recovery);
                }
            }
            case RECOVER -> {
                getNavigation().stop();
                if (stageTick >= stageLen) {
                    current = null;
                    setStage(IDLE, idleGap + random.nextInt(8));
                }
            }
            default -> setStage(IDLE, 5);
        }
    }

    int scaledWindup(Move m) {
        double s = Encounters.cfg().telegraphScale * (dev.underdeep.config.UnderdeepConfig.get().combat.assist ? 1.3 : 1);
        return Math.max(4, (int) Math.round(m.windup * s));
    }

    private void leash(ServerWorld w) {
        if (enc.center == null) {
            return;
        }
        double dist = Math.sqrt(squaredDistanceTo(Vec3d.ofBottomCenter(enc.center)));
        if (dist > enc.radius + 28 || getY() < enc.center.getY() - 30) {
            // fell out or was lured far away: the guardian returns to its lair (never moves players)
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getBodyY(0.5), getZ(), 20, 1, 1, 1, 0.05);
            refreshPositionAndAngles(enc.center.getX() + 0.5, enc.center.getY() + 1, enc.center.getZ() + 0.5, getYaw(), 0);
            getNavigation().stop();
        }
    }

    private void trackReachability(double d) {
        boolean far = target.getY() - getY() > 4.5 && d < 18 || getNavigation().isIdle() && d > 8 && stage == IDLE && brain.walks();
        unreachable = far ? unreachable + 1 : Math.max(0, unreachable - 2);
    }

    /** True when the target has been out of reach for a while (pillaring, ledges): ranged moves get priority. */
    public boolean targetUnreachable() {
        return unreachable > 60;
    }

    @Nullable
    private Move choose(double d) {
        long now = getWorld().getTime();
        List<Move> pool = new ArrayList<>();
        double total = 0;
        boolean stuck = targetUnreachable();
        for (Move m : brain.moves) {
            if ((m.phases & 1 << phase) == 0 || cooldowns.getOrDefault(m, 0L) > now) {
                continue;
            }
            boolean inRange = d >= m.minRange && d <= m.maxRange + getWidth() * 0.5;
            if (stuck ? m.ranged : inRange) {
                pool.add(m);
                total += m.weight;
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        double r = random.nextDouble() * total;
        for (Move m : pool) {
            r -= m.weight;
            if (r <= 0) {
                return m;
            }
        }
        return pool.get(pool.size() - 1);
    }

    // ----------------------------------------------------------------------------------- damage, poise, phases

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient || enc == null) {
            return false;
        }
        if (stage == INTRO || stage == DEFEAT || stage == TRANSITION || source.isOf(net.minecraft.entity.damage.DamageTypes.IN_WALL)
                || source.isOf(net.minecraft.entity.damage.DamageTypes.FALL) || source.isOf(net.minecraft.entity.damage.DamageTypes.DROWN)) {
            return false;
        }
        LivingEntity attacker = source.getAttacker() instanceof LivingEntity l ? l : null;
        if (attacker instanceof ServerPlayerEntity p) {
            if (!enc.inArena(p.getBlockPos())) {
                return false; // the guardian cannot be harmed from outside its lair
            }
            enc.participate(p, amount);
            threat.merge(p.getUuid(), (double) amount, Double::sum);
        } else if (attacker instanceof BossEntity || attacker != null && minions.contains(attacker)) {
            return false;
        }
        double mult = brain.damageTaken(source, amount, attacker);
        if (mult <= 0) {
            if (attacker instanceof PlayerEntity p && age % 10 == 0) {
                p.sendMessage(net.minecraft.text.Text.literal("Your blow glances off.").formatted(net.minecraft.util.Formatting.GRAY), true);
            }
            playSound(SoundEvents.ITEM_SHIELD_BLOCK, 0.8f, 0.6f);
            return false;
        }
        if (isStaggered()) {
            mult *= 1.35;
        }
        float dealt = (float) (amount * mult / healthFactor);
        if (getHealth() - dealt <= 1) {
            // defeat sequence instead of an instant death (the final blow is still credited)
            super.damage(source, Math.max(0, getHealth() - 1));
            beginDefeat();
            return true;
        }
        boolean hurt = super.damage(source, dealt);
        if (hurt) {
            checkPhase();
        }
        return hurt;
    }

    @Override
    public void takeKnockback(double strength, double x, double z) {
        // bosses are not knocked about by ordinary blows
    }

    private void checkPhase() {
        double[] th = brain.phaseThresholds();
        if (phase < th.length && getHealth() / getMaxHealth() <= th[phase]) {
            phase++;
            dataTracker.set(PHASE, (byte) phase);
            current = null;
            setStage(TRANSITION, 40);
            roar(def.roar(), 1.4f, 0.7f);
            brain.onPhase(phase);
            Encounters.audio(enc, "phase", phase);
        }
    }

    private void beginDefeat() {
        current = null;
        setStage(DEFEAT, def.tier() == Tier.FINAL ? 100 : 50);
        getNavigation().stop();
        roar(def.roar(), 2f, 0.5f);
    }

    public boolean defeated() {
        return stage == DEFEAT;
    }

    @Override
    public void onDeath(DamageSource source) {
        if (!getWorld().isClient && enc != null && !enc.ended && !isRemoved()) {
            brain.onDeath();
            Encounters.victory(enc, this);
        }
        super.onDeath(source);
    }

    /** Starts a new fight at full health (called by the encounter on awakening). */
    void begin(double healthScale) {
        var h = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        double want = def.health() * healthScale;
        healthFactor = Math.max(1, want / 1000);
        if (h != null) {
            h.setBaseValue(want / healthFactor);
        }
        setHealth(getMaxHealth());
        poiseMax = def.poise() * (0.8 + 0.2 * healthScale);
        poise = poiseMax;
        phase = 0;
        dataTracker.set(PHASE, (byte) 0);
        setStage(INTRO, 60);
        brain.onPhase(0);
        roar(def.roar(), 2f, 0.8f);
    }

    /** Rescales health when more players join a running fight (never down; health fraction is kept). */
    void rescale(double healthScale) {
        var h = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (h == null) {
            return;
        }
        double frac = getHealth() / getMaxHealth();
        double want = def.health() * healthScale;
        if (want > h.getBaseValue() * healthFactor) {
            healthFactor = Math.max(1, want / 1000);
            h.setBaseValue(want / healthFactor);
            setHealth((float) (getMaxHealth() * frac));
        }
    }

    @Override
    public void poiseDamage(double amount, LivingEntity attacker, boolean force) {
        long now = getWorld().getTime();
        if (isStaggered() || now < staggerImmune || stage == INTRO || stage == DEFEAT || stage == TRANSITION) {
            return;
        }
        poise -= force ? poiseMax * 0.5 : amount;
        if (poise <= 0) {
            stagger(def.tier() == Tier.FINAL ? 50 : 70);
        }
    }

    /** Puts the boss into a stagger (its main opening): interrupts the current move. */
    public void stagger(int ticks) {
        long now = getWorld().getTime();
        staggerUntil = now + ticks;
        staggerImmune = now + ticks + 180;
        poise = poiseMax;
        current = null;
        setStage(STAGGER, ticks);
        getNavigation().stop();
        if (getWorld() instanceof ServerWorld w) {
            w.playSound(null, getBlockPos(), SoundEvents.ENTITY_RAVAGER_STUNNED, SoundCategory.HOSTILE, 1.5f, 0.7f);
            w.spawnParticles(ParticleTypes.CRIT, getX(), getY() + getHeight(), getZ(), 30, getWidth() * 0.5, 0.3, getWidth() * 0.5, 0.2);
        }
        if (enc != null) {
            enc.tell("The guardian staggers — strike now!", net.minecraft.util.Formatting.YELLOW);
        }
    }

    @Override
    public boolean isStaggered() {
        return staggerUntil > getWorld().getTime();
    }

    /** A player parried one of its blows: heavy poise damage, and the blow is cut short. */
    public void parried(PlayerEntity p) {
        poiseDamage(poiseMax * 0.34, p, false);
        if (stage == ACTIVE && current != null && current.maxRange < 7 && !current.ranged) {
            setStage(RECOVER, current.recovery + 10);
        }
    }

    // ----------------------------------------------------------------------------------- targeting

    LivingEntity threatTarget(List<ServerPlayerEntity> players) {
        ServerPlayerEntity best = null;
        double bestScore = -1;
        for (ServerPlayerEntity p : players) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative() || enc.downed(p)) {
                continue;
            }
            double dist = Math.sqrt(squaredDistanceTo(p));
            double score = threat.getOrDefault(p.getUuid(), 0.0) * 0.6 + 30 / (dist + 3) + (p == target ? 6 : 0) + random.nextDouble() * 3;
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        threat.replaceAll((k, v) -> v * 0.97);
        return best;
    }

    // ----------------------------------------------------------------------------------- attack helpers

    public double damage() {
        return def.damage() * (enc == null ? 1 : enc.damageScale);
    }

    /** Hits one entity with a share of the boss's damage. Returns true when it landed. */
    public boolean strike(LivingEntity e, double mult, BossDamage kind, double knock) {
        if (e == this || !e.isAlive() || e instanceof BossEntity || minions.contains(e)) {
            return false;
        }
        if (e instanceof ServerPlayerEntity p && (enc == null || enc.downed(p))) {
            return false;
        }
        boolean hit = e.damage(kind.source(getWorld(), this), (float) (damage() * mult));
        if (hit && knock > 0) {
            Vec3d away = e.getPos().subtract(getPos()).multiply(1, 0, 1);
            if (away.lengthSquared() > 1e-4) {
                away = away.normalize();
                e.addVelocity(away.x * knock, Math.min(0.5, knock * 0.4), away.z * knock);
                e.velocityModified = true;
            }
        }
        return hit;
    }

    /** All living targets (players and their creatures) in the arena near a point. */
    public List<LivingEntity> victims(Vec3d c, double r) {
        net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(c, c).expand(r, r * 0.75 + 3, r);
        List<LivingEntity> out = new ArrayList<>(getWorld().getEntitiesByClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !(e instanceof BossEntity) && !minions.contains(e)
                        && (e instanceof PlayerEntity p && !p.isSpectator() && !p.isCreative() || e instanceof MobEntity m && m.getTarget() == this)));
        if (enc != null) {
            for (ServerPlayerEntity p : enc.fighters()) {
                if (!out.contains(p) && box.intersects(p.getBoundingBox()) && !p.isCreative()) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** Cone in front of the boss (or its locked direction). */
    public int cone(double range, double halfAngle, double mult, BossDamage kind, double knock) {
        Vec3d dir = lockedDir != null ? lockedDir : getRotationVector().multiply(1, 0, 1).normalize();
        int n = 0;
        for (LivingEntity e : victims(getPos(), range + getWidth())) {
            Vec3d to = e.getPos().subtract(getPos()).multiply(1, 0, 1);
            double dist = to.length() - getWidth() * 0.5 - e.getWidth() * 0.5;
            if (dist > range || Math.abs(e.getY() - getY()) > getHeight() + 2) {
                continue;
            }
            if (to.lengthSquared() > 0.25 && Math.toDegrees(Math.acos(MathHelper.clamp(to.normalize().dotProduct(dir), -1, 1))) > halfAngle) {
                continue;
            }
            n += strike(e, mult, kind, knock) ? 1 : 0;
        }
        return n;
    }

    /** Circle on the ground around a point (players above it by more than {@code height} are missed). */
    public int circle(Vec3d c, double r, double height, double mult, BossDamage kind, double knock) {
        int n = 0;
        for (LivingEntity e : victims(c, r)) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            if (dx * dx + dz * dz <= r * r && e.getY() - c.y <= height && c.y - e.getY() <= 3) {
                n += strike(e, mult, kind, knock) ? 1 : 0;
            }
        }
        return n;
    }

    /** Ground ring between two radii: jump over it (only grounded targets are hit). */
    public int wave(Vec3d c, double inner, double outer, double mult, double knock) {
        int n = 0;
        for (LivingEntity e : victims(c, outer)) {
            double dist = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
            if (dist >= inner && dist <= outer && e.isOnGround() && Math.abs(e.getY() - c.y) < 2.5) {
                n += strike(e, mult, BossDamage.CRUSH, knock) ? 1 : 0;
            }
        }
        return n;
    }

    /** A straight band from the boss in a direction. */
    public int line(Vec3d from, Vec3d dir, double length, double width, double mult, BossDamage kind, double knock) {
        Vec3d d = dir.normalize();
        int n = 0;
        for (LivingEntity e : victims(from.add(d.multiply(length * 0.5)), length * 0.5 + width)) {
            Vec3d to = e.getPos().subtract(from);
            double t = to.dotProduct(d);
            if (t < 0 || t > length) {
                continue;
            }
            Vec3d perp = to.subtract(d.multiply(t));
            if (Math.hypot(perp.x, perp.z) <= width + e.getWidth() * 0.5 && Math.abs(perp.y) < 3) {
                n += strike(e, mult, kind, knock) ? 1 : 0;
            }
        }
        return n;
    }

    public BossProjectile shoot(BossProjectile.Kind kind, Vec3d from, Vec3d to, double speed, double mult) {
        BossProjectile p = new BossProjectile(getWorld(), this, kind, mult);
        p.setPosition(from);
        Vec3d v = to.subtract(from).normalize().multiply(speed);
        p.setVelocity(v);
        getWorld().spawnEntity(p);
        return p;
    }

    // ----------------------------------------------------------------------------------- telegraphs and effects

    public void markCircle(Vec3d c, double r, ParticleEffect particle) {
        if (getWorld() instanceof ServerWorld w) {
            int n = (int) Math.max(8, r * 5);
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n;
                w.spawnParticles(particle, c.x + Math.cos(a) * r, c.y + 0.15, c.z + Math.sin(a) * r, 1, 0, 0.02, 0, 0);
            }
        }
    }

    public void markLine(Vec3d from, Vec3d dir, double length, ParticleEffect particle) {
        if (getWorld() instanceof ServerWorld w) {
            Vec3d d = dir.normalize();
            for (double t = 1; t < length; t += 1.2) {
                w.spawnParticles(particle, from.x + d.x * t, from.y + 0.2, from.z + d.z * t, 1, 0.1, 0.02, 0.1, 0);
            }
        }
    }

    public void markCone(double range, double halfAngle, ParticleEffect particle) {
        if (getWorld() instanceof ServerWorld w) {
            Vec3d dir = lockedDir != null ? lockedDir : getRotationVector().multiply(1, 0, 1).normalize();
            double base = Math.atan2(dir.z, dir.x);
            for (int i = -3; i <= 3; i++) {
                double a = base + Math.toRadians(halfAngle) * i / 3;
                for (double t = 2; t <= range; t += 2) {
                    w.spawnParticles(particle, getX() + Math.cos(a) * t, getY() + 0.2, getZ() + Math.sin(a) * t, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    public void particles(ParticleEffect p, Vec3d at, int count, double spread) {
        if (getWorld() instanceof ServerWorld w) {
            w.spawnParticles(p, at.x, at.y, at.z, count, spread, spread * 0.5, spread, 0.02);
        }
    }

    public void roar(SoundEvent s, float volume, float pitch) {
        getWorld().playSound(null, getBlockPos(), s, SoundCategory.HOSTILE, volume, pitch);
    }

    public Vec3d flatDirTo(Entity e) {
        Vec3d d = e.getPos().subtract(getPos()).multiply(1, 0, 1);
        return d.lengthSquared() < 1e-4 ? getRotationVector().multiply(1, 0, 1).normalize() : d.normalize();
    }

    public Vec3d lockedDirection() {
        return lockedDir != null ? lockedDir : getRotationVector().multiply(1, 0, 1).normalize();
    }

    public void faceTowards(Vec3d p) {
        double dx = p.x - getX(), dz = p.z - getZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * 180 / Math.PI) - 90;
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
    }

    /** Moves without pathfinding (flying, swimming, burrowing bosses). */
    public void glideTowards(Vec3d goal, double speed) {
        Vec3d to = goal.subtract(getPos());
        double len = to.length();
        if (len < 0.3) {
            setVelocity(getVelocity().multiply(0.5));
            return;
        }
        Vec3d v = to.multiply(Math.min(speed, len) / len);
        setVelocity(getVelocity().multiply(0.6).add(v.multiply(0.4)));
        velocityModified = true;
    }

    /** Summons helpers (bounded; they are removed with the encounter). */
    public void summon(String species, int count, int cap, double spread) {
        minions.removeIf(e -> !e.isAlive());
        var type = dev.underdeep.wildlife.WildlifeRegistry.type(species);
        if (type == null || !(getWorld() instanceof ServerWorld w)) {
            return;
        }
        for (int i = 0; i < count && minions.size() < cap; i++) {
            Entity e = type.create(w);
            if (e == null) {
                return;
            }
            double a = random.nextDouble() * Math.PI * 2;
            BlockPos at = BlockPos.ofFloored(getX() + Math.cos(a) * spread, getY() + 1, getZ() + Math.sin(a) * spread);
            e.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360, 0);
            if (e instanceof MobEntity m && target != null) {
                m.setTarget(target);
            }
            w.spawnEntity(e);
            w.spawnParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
            minions.add(e);
        }
    }

    void clearMinions() {
        for (Entity e : minions) {
            if (e.isAlive()) {
                e.discard();
            }
        }
        minions.clear();
    }

    /** Effective health including the part beyond the vanilla cap (diagnostics). */
    public double effectiveMaxHealth() {
        return getMaxHealth() * healthFactor;
    }

    public Encounters.Active encounter() {
        return enc;
    }
}
