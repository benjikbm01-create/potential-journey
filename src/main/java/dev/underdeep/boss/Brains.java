package dev.underdeep.boss;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The thirteen guardian brains (private design: docs/private/BOSSES.md). Every damaging move is telegraphed during
 * its wind-up (posture on the client, sound, ground markings here), and every guardian has recovery windows, a
 * stagger and at least one ranged answer to players it cannot reach.
 */
public final class Brains {
    private Brains() {
    }

    static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1f, 0.12f, 0.08f), 1.6f);
    static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1f, 0.8f, 0.2f), 1.4f);

    /** Shared charge: straight run in the locked direction; a wall or pillar dazes the charger. */
    static void charge(BossEntity b, int tick, double speed, double mult, Set<UUID> hit) {
        Vec3d d = b.lockedDirection();
        b.setVelocity(d.x * speed, b.getVelocity().y, d.z * speed);
        b.velocityModified = true;
        b.particles(ParticleTypes.CLOUD, b.getPos(), 2, b.getWidth() * 0.4);
        for (LivingEntity e : b.victims(b.getPos().add(d.multiply(b.getWidth() * 0.6)), b.getWidth() * 0.7 + 0.8)) {
            if (hit.add(e.getUuid())) {
                b.strike(e, mult, BossDamage.CRUSH, 1.4);
            }
        }
        if (tick > 3 && b.horizontalCollision) {
            b.roar(SoundEvents.ENTITY_RAVAGER_STUNNED, 2f, 0.8f);
            b.particles(ParticleTypes.EXPLOSION, b.getPos().add(d.multiply(b.getWidth() * 0.6)), 3, 0.6);
            b.stagger(80);
        }
    }

    // ============================================================================= EARLY

    /** Charger: telegraphed charges that daze it against pillars; tail sweep; stomp ring. */
    static final class Mossback extends Brain {
        final Set<UUID> hit = new HashSet<>();
        boolean called;

        Mossback(BossEntity b) {
            super(b);
            add(Move.of("charge", 24, 26, 26).range(5, 24).cooldown(70).weight(3).lock()
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markLine(x.getPos(), x.flatDirTo(t), 18, ParticleTypes.CLOUD);
                            x.particles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x.getPos(), 3, 0.8);
                        }
                    }).start((x, t, k) -> {
                        hit.clear();
                        x.roar(SoundEvents.ENTITY_RAVAGER_ROAR, 1.5f, 1.1f);
                    }).tick((x, t, k) -> charge(x, k, 0.75, 1.6, hit)));
            add(Move.of("gore", 12, 2, 16).range(0, 4.5).cooldown(20).weight(3)
                    .start((x, t, k) -> x.cone(4.5, 50, 1.0, BossDamage.STRIKE, 0.6)));
            add(Move.of("tail", 14, 2, 18).range(0, 5).cooldown(60).weight(1.5)
                    .windupTick((x, t, k) -> x.particles(ParticleTypes.CRIT, x.getPos().subtract(x.lockedDirection().multiply(2)), 2, 1))
                    .start((x, t, k) -> x.line(x.getPos(), x.lockedDirection().multiply(-1), 5.5, 2.4, 1.1, BossDamage.SWEEP, 0.9)));
            add(Move.of("stomp", 18, 1, 22).range(0, 6).cooldown(80).weight(2)
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markCircle(x.getPos(), 5.5, ParticleTypes.SMOKE);
                        }
                    }).start((x, t, k) -> {
                        x.particles(ParticleTypes.EXPLOSION, x.getPos(), 2, 1);
                        x.wave(x.getPos(), 0, 6, 1.2, 0.8);
                    }));
            add(Move.of("double_charge", 26, 52, 30).range(6, 26).cooldown(110).weight(3).phases(0b10).lock()
                    .start((x, t, k) -> hit.clear())
                    .tick((x, t, k) -> {
                        if (k == 26 && t != null) {
                            hit.clear();
                            x.faceTowards(t.getPos());
                            x.roar(SoundEvents.ENTITY_RAVAGER_ROAR, 1.2f, 1.3f);
                        }
                        charge(x, k % 26, 0.8, 1.5, hit);
                    }));
            add(Move.of("hurl", 16, 1, 20).range(0, 40).cooldown(80).weight(0.5).ranged()
                    .start((x, t, k) -> x.shoot(BossProjectile.Kind.ROCK, x.getEyePos(), t.getEyePos(), 1.1, 1.0)));
        }

        @Override
        public void onPhase(int phase) {
            if (phase == 1 && !called) {
                called = true;
                b.summon("tuskbriar", 2, 2, 5);
            }
        }
    }

    /** Rooted fungal matriarch: ground spore rings, tendril slams, exploding puffs; her heart opens after the Great Exhale. */
    static final class SporeMother extends Brain {
        long heartUntil;
        BlockPos rootGoal;
        int moved;

        SporeMother(BossEntity b) {
            super(b);
            add(Move.of("bursts", 16, 1, 18).range(0, 30).cooldown(50).weight(3).ranged()
                    .start((x, t, k) -> {
                        int n = x.phaseIndex() == 0 ? 3 : 5;
                        for (ServerPlayerEntity p : fighters()) {
                            zone(at(p), x.phaseIndex() == 0 ? 3.2 : 4.0, 24, 1.1, BossDamage.AURA, ParticleTypes.SPORE_BLOSSOM_AIR, 0.2);
                            if (--n <= 0) {
                                break;
                            }
                        }
                        for (int i = 0; i < n; i++) {
                            Vec3d c = centre().add(x.getRandom().nextGaussian() * 6, 0, x.getRandom().nextGaussian() * 6);
                            zone(c, 3, 28, 1.0, BossDamage.AURA, ParticleTypes.SPORE_BLOSSOM_AIR, 0.2);
                        }
                    }));
            add(Move.of("tendril", 14, 2, 18).range(0, 9).cooldown(30).weight(3).lock()
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markLine(x.getPos(), x.flatDirTo(t), 9, ParticleTypes.MYCELIUM);
                        }
                    }).start((x, t, k) -> x.line(x.getPos(), x.lockedDirection(), 9, 1.6, 1.2, BossDamage.STRIKE, 0.8)));
            add(Move.of("volley", 16, 8, 18).range(4, 40).cooldown(70).weight(1.5).ranged()
                    .tick((x, t, k) -> {
                        if (k % 2 == 0) {
                            x.shoot(BossProjectile.Kind.SPORE, x.getEyePos(), t.getEyePos().add(x.getRandom().nextGaussian(), 0, x.getRandom().nextGaussian()), 0.7, 0.6);
                        }
                    }));
            add(Move.of("puffs", 20, 1, 16).range(0, 40).cooldown(300).weight(1)
                    .start((x, t, k) -> x.summon("puffling", 3, 4, 4)));
            add(Move.of("exhale", 34, 8, 120).range(0, 40).cooldown(420).weight(4)
                    .windupTick((x, t, k) -> {
                        x.particles(ParticleTypes.SPORE_BLOSSOM_AIR, x.getPos().add(0, x.getHeight(), 0), 6, 1.5);
                        if (k % 4 == 0) {
                            x.markCircle(x.getPos(), 8, ParticleTypes.CRIMSON_SPORE);
                        }
                    }).start((x, t, k) -> {
                        x.roar(SoundEvents.ENTITY_SNIFFER_SNIFFING, 2f, 0.5f);
                        x.circle(x.getPos(), 8, 3, 0.9, BossDamage.AURA, 0.8);
                        heartUntil = x.getWorld().getTime() + 130;
                        x.setFlag(0, true);
                        if (x.enc != null) {
                            x.enc.tell("Her heart-cap opens!", Formatting.YELLOW);
                        }
                    }));
        }

        @Override
        public boolean walks() {
            return rootGoal != null;
        }

        @Override
        public void tick() {
            long now = b.getWorld().getTime();
            if (heartUntil > 0 && now > heartUntil) {
                heartUntil = 0;
                b.setFlag(0, false);
            }
            // puffs burst into spore rings when they reach someone
            for (var e : new ArrayList<>(b.minions)) {
                if (e.isAlive() && e.age > 40) {
                    for (ServerPlayerEntity p : fighters()) {
                        if (p.squaredDistanceTo(e) < 4) {
                            zone(e.getPos(), 2.5, 14, 0.8, BossDamage.AURA, ParticleTypes.SPORE_BLOSSOM_AIR, 0.1);
                            e.discard();
                            break;
                        }
                    }
                }
            }
            if (rootGoal != null) {
                b.getNavigation().startMovingTo(rootGoal.getX() + 0.5, rootGoal.getY(), rootGoal.getZ() + 0.5, 1.0);
                if (b.getBlockPos().getSquaredDistance(rootGoal) < 6 || ++moved > 240) {
                    rootGoal = null;
                    b.getNavigation().stop();
                    b.roar(SoundEvents.BLOCK_ROOTED_DIRT_PLACE, 2f, 0.5f);
                }
            }
        }

        @Override
        public void onPhase(int phase) {
            if (phase == 1) {
                List<BlockPos> roots = devices("root_spot");
                rootGoal = roots.isEmpty() ? BlockPos.ofFloored(centre().add(6, 0, -6)) : roots.get(b.getRandom().nextInt(roots.size()));
                moved = 0;
                b.roar(SoundEvents.BLOCK_ROOTED_DIRT_BREAK, 2f, 0.4f);
            }
        }

        @Override
        public double damageTaken(DamageSource src, float amount, LivingEntity attacker) {
            return b.flag(0) ? 2.0 : 0.7;
        }
    }

    /** Knight of nacre: parryable combos, a red-glowing unparryable thrust, shield bash; sings geysers later. */
    static final class Choirwarden extends Brain {
        Choirwarden(BossEntity b) {
            super(b);
            add(Move.of("combo", 10, 24, 16).range(0, 4).cooldown(30).weight(4)
                    .tick((x, t, k) -> {
                        if (k == 2 || k == 10 || k == 18) {
                            if (t != null) {
                                x.faceTowards(t.getPos());
                                x.addVelocity(x.flatDirTo(t).x * 0.25, 0, x.flatDirTo(t).z * 0.25);
                            }
                            x.cone(3.8, 60, 0.8, BossDamage.STRIKE, 0.3);
                            x.roar(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
                        }
                    }));
            add(Move.of("thrust", 18, 6, 22).range(2, 8).cooldown(60).weight(2).lock()
                    .windupTick((x, t, k) -> x.particles(RED, x.getEyePos(), 3, 0.3))
                    .start((x, t, k) -> {
                        Vec3d d = x.lockedDirection();
                        x.setVelocity(d.x * 1.2, 0.05, d.z * 1.2);
                        x.velocityModified = true;
                    }).tick((x, t, k) -> {
                        if (k == 3) {
                            x.line(x.getPos(), x.lockedDirection(), 6, 1.2, 1.6, BossDamage.CRUSH, 0.8);
                        }
                    }));
            add(Move.of("bash", 12, 2, 16).range(0, 3.5).cooldown(50).weight(1.5)
                    .start((x, t, k) -> x.cone(3.2, 45, 0.9, BossDamage.SWEEP, 1.3)));
            add(Move.of("javelin", 18, 1, 20).range(6, 40).cooldown(60).weight(1).ranged()
                    .start((x, t, k) -> x.shoot(BossProjectile.Kind.SHARD, x.getEyePos(), t.getEyePos(), 1.3, 1.0)));
            add(Move.of("song", 24, 40, 24).range(0, 30).cooldown(160).weight(2).phases(0b10)
                    .windupTick((x, t, k) -> x.particles(ParticleTypes.NOTE, x.getEyePos().add(0, 0.5, 0), 1, 0.4))
                    .tick((x, t, k) -> {
                        if (k % 10 == 0) {
                            for (ServerPlayerEntity p : fighters()) {
                                zone(at(p), 2.5, 20, 1.0, BossDamage.AURA, ParticleTypes.SPLASH, 0.9);
                            }
                        }
                    }));
        }
    }

    // ============================================================================= MID

    /** Pack alpha: pounces, frost breath, howls; a blizzard later that only lit braziers keep back. */
    static final class Rimefang extends Brain {
        boolean packCalled, packReturned;
        final Set<UUID> hit = new HashSet<>();

        Rimefang(BossEntity b) {
            super(b);
            add(Move.of("pounce", 14, 10, 18).range(3, 11).cooldown(40).weight(3).lock()
                    .start((x, t, k) -> {
                        hit.clear();
                        Vec3d d = x.lockedDirection();
                        x.setVelocity(d.x * 1.1, 0.5, d.z * 1.1);
                        x.velocityModified = true;
                    }).tick((x, t, k) -> {
                        for (LivingEntity e : x.victims(x.getPos(), 2.2)) {
                            if (hit.add(e.getUuid())) {
                                x.strike(e, 1.3, BossDamage.STRIKE, 0.6);
                            }
                        }
                    }));
            add(Move.of("bite", 8, 2, 12).range(0, 3).cooldown(16).weight(3)
                    .start((x, t, k) -> x.cone(3, 45, 1.0, BossDamage.STRIKE, 0.3)));
            add(Move.of("breath", 18, 20, 20).range(0, 9).cooldown(90).weight(2).lock().ranged()
                    .windupTick((x, t, k) -> {
                        if (k % 4 == 0) {
                            x.markCone(9, 28, ParticleTypes.SNOWFLAKE);
                        }
                    }).tick((x, t, k) -> {
                        x.particles(ParticleTypes.SNOWFLAKE, x.getEyePos().add(x.lockedDirection().multiply(3)), 8, 1.2);
                        if (k % 4 == 0) {
                            for (LivingEntity e : x.victims(x.getPos(), 10)) {
                                e.setFrozenTicks(Math.min(e.getMinFreezeDamageTicks() + 30, e.getFrozenTicks() + 20));
                            }
                            x.cone(9, 28, 0.35, BossDamage.SPELL, 0);
                        }
                    }));
            add(Move.of("howl", 20, 1, 16).range(0, 40).cooldown(400).weight(1.5)
                    .windupTick((x, t, k) -> x.particles(ParticleTypes.SNOWFLAKE, x.getEyePos(), 2, 0.5))
                    .start((x, t, k) -> {
                        x.roar(SoundEvents.ENTITY_WOLF_HOWL, 2.5f, 0.6f);
                        for (var m : x.minions) {
                            if (m instanceof LivingEntity l && l.isAlive()) {
                                l.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 160, 1));
                                l.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 160, 0));
                            }
                        }
                    }));
            add(Move.of("ambush", 30, 10, 22).range(6, 18).cooldown(160).weight(3).phases(0b10).lock()
                    .windupTick((x, t, k) -> {
                        if (k < 16 && t != null) { // slinks away into the snow first
                            Vec3d away = x.getPos().subtract(t.getPos()).multiply(1, 0, 1).normalize();
                            x.setVelocity(away.x * 0.35 - away.z * 0.2, x.getVelocity().y, away.z * 0.35 + away.x * 0.2);
                        }
                    }).start((x, t, k) -> {
                        hit.clear();
                        if (t != null) {
                            x.faceTowards(t.getPos());
                        }
                        Vec3d d = x.flatDirTo(t);
                        x.setVelocity(d.x * 1.3, 0.55, d.z * 1.3);
                        x.velocityModified = true;
                    }).tick((x, t, k) -> {
                        for (LivingEntity e : x.victims(x.getPos(), 2.4)) {
                            if (hit.add(e.getUuid())) {
                                x.strike(e, 1.5, BossDamage.CRUSH, 0.8);
                            }
                        }
                    }));
            add(Move.of("shards", 16, 1, 18).range(8, 40).cooldown(70).weight(1).ranged()
                    .start((x, t, k) -> {
                        for (int i = -1; i <= 1; i++) {
                            x.shoot(BossProjectile.Kind.FROST, x.getEyePos(), t.getEyePos().add(i * 1.5, 0, 0), 1.0, 0.7);
                        }
                    }));
        }

        @Override
        public void onPhase(int phase) {
            if (phase == 0 && !packCalled) {
                packCalled = true;
                b.summon("rimefox", 3, 3, 5);
            }
            if (phase == 1) {
                if (!packReturned) {
                    packReturned = true;
                    b.summon("rimefox", 3, 3, 6);
                }
                if (b.enc != null) {
                    b.enc.tell("A blizzard rises! Stay near a lit brazier.", Formatting.AQUA);
                }
            }
        }

        @Override
        public void tick() {
            if (b.phaseIndex() >= 1 && b.age % 100 == 0) {
                List<BlockPos> lit = new ArrayList<>();
                for (BlockPos p : devices("brazier")) {
                    if (lit(p)) {
                        lit.add(p);
                    }
                }
                for (ServerPlayerEntity p : fighters()) {
                    boolean warm = lit.stream().anyMatch(l -> l.getSquaredDistance(p.getPos()) < 36);
                    if (!warm) {
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
                        p.setFrozenTicks(Math.min(p.getMinFreezeDamageTicks() + 20, p.getFrozenTicks() + 40));
                    }
                }
                b.particles(ParticleTypes.SNOWFLAKE, centre().add(0, 4, 0), 120, 12);
            }
        }

        @Override
        public void device(ServerPlayerEntity p, BlockPos pos, String kind) {
            if (kind.equals("brazier") && !lit(pos) && channel(p, pos, 4)) {
                setLit(pos, true);
                b.roar(SoundEvents.ITEM_FIRECHARGE_USE, 1f, 1f);
            }
        }
    }

    /** Shielded construct: ground its three conductor pylons to drop the shield; lightning, slams, overloads. */
    static final class Galvanic extends Brain {
        long shieldDownUntil;
        long nextRecharge;

        Galvanic(BossEntity b) {
            super(b);
            add(Move.of("lightning", 20, 1, 20).range(0, 40).cooldown(60).weight(3).ranged()
                    .start((x, t, k) -> {
                        for (ServerPlayerEntity p : fighters()) {
                            boolean copper = false;
                            for (ItemStack s : p.getArmorItems()) {
                                copper |= s.getItem() instanceof ArmorItem a && a.getMaterial().getIdAsString().contains("copper");
                            }
                            zone(at(p), copper ? 3.2 : 2.2, 22, copper ? 1.8 : 1.3, BossDamage.SPELL, ParticleTypes.ELECTRIC_SPARK, 0.3);
                        }
                    }));
            add(Move.of("slam", 22, 1, 24).range(0, 7).cooldown(70).weight(2)
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markCircle(x.getPos(), 7, ParticleTypes.ELECTRIC_SPARK);
                        }
                    }).start((x, t, k) -> {
                        x.particles(ParticleTypes.EXPLOSION, x.getPos(), 3, 1.5);
                        x.wave(x.getPos(), 0, 7.5, 1.3, 0.9);
                    }));
            add(Move.of("sweep", 14, 2, 18).range(0, 6).cooldown(30).weight(3)
                    .start((x, t, k) -> x.cone(6, 100, 1.2, BossDamage.SWEEP, 1.0)));
            add(Move.of("overload", 40, 1, 40).range(0, 40).cooldown(600).weight(5).phases(0b10)
                    .windupTick((x, t, k) -> {
                        if (k % 4 == 0) {
                            x.markCircle(x.getPos(), 12, ParticleTypes.ELECTRIC_SPARK);
                            x.roar(SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.5f, 2f);
                        }
                    }).start((x, t, k) -> {
                        x.roar(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 1f);
                        x.circle(x.getPos(), 12, 4, 1.6, BossDamage.AURA, 0.6);
                    }));
            add(Move.of("bolt", 14, 1, 16).range(5, 40).cooldown(40).weight(1).ranged()
                    .start((x, t, k) -> x.shoot(BossProjectile.Kind.SHARD, x.getEyePos(), t.getEyePos(), 1.4, 1.0)));
        }

        boolean shielded() {
            if (b.getWorld().getTime() < shieldDownUntil) {
                return false;
            }
            for (BlockPos p : devices("pylon")) {
                if (lit(p)) {
                    return true;
                }
            }
            return !devices("pylon").isEmpty();
        }

        @Override
        public void onPhase(int phase) {
            if (phase == 0) {
                for (BlockPos p : devices("pylon")) {
                    setLit(p, true);
                }
            }
        }

        @Override
        public void tick() {
            long now = b.getWorld().getTime();
            boolean sh = shielded();
            b.setFlag(0, sh);
            if (sh && b.age % 10 == 0) {
                b.particles(ParticleTypes.ELECTRIC_SPARK, b.getPos().add(0, b.getHeight() * 0.5, 0), 6, b.getWidth() * 0.6);
            }
            boolean anyLit = devices("pylon").stream().anyMatch(this::lit);
            if (!anyLit && shieldDownUntil < now && !devices("pylon").isEmpty()) {
                shieldDownUntil = now + 300;
                nextRecharge = now + 300;
                b.stagger(40);
                if (b.enc != null) {
                    b.enc.tell("The colossus's shield collapses!", Formatting.YELLOW);
                }
            }
            if (nextRecharge > 0 && now >= nextRecharge) {
                for (BlockPos p : devices("pylon")) {
                    if (!lit(p)) {
                        setLit(p, true);
                        b.roar(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.6f);
                        b.particles(ParticleTypes.ELECTRIC_SPARK, Vec3d.ofCenter(p), 30, 1);
                        break;
                    }
                }
                nextRecharge = devices("pylon").stream().allMatch(this::lit) ? 0 : now + (b.phaseIndex() >= 1 ? 200 : 300);
            }
        }

        @Override
        public double damageTaken(DamageSource src, float amount, LivingEntity attacker) {
            return shielded() ? 0 : 1.1;
        }

        @Override
        public void device(ServerPlayerEntity p, BlockPos pos, String kind) {
            if (kind.equals("pylon") && lit(pos) && channel(p, pos, 8)) {
                setLit(pos, false);
                b.roar(SoundEvents.BLOCK_COPPER_BULB_TURN_OFF, 1.5f, 0.6f);
                if (nextRecharge == 0) {
                    nextRecharge = b.getWorld().getTime() + 600;
                }
            }
        }
    }

    /** Burrower: travels under the floor, erupts beneath players, surfaces to breathe fire; magma cracks later. */
    static final class CinderWurm extends Brain {
        long surfacedUntil;
        boolean burrowed;
        int surfacedMoves;
        Vec3d eruptAt;

        CinderWurm(BossEntity b) {
            super(b);
            add(Move.of("erupt", 22, 2, 16).range(0, 40).cooldown(30).weight(4).ranged()
                    .windupTick((x, t, k) -> {
                        if (k == 1 && t != null) {
                            eruptAt = at(t);
                        }
                        if (eruptAt != null && k % 3 == 0) {
                            x.markCircle(eruptAt, 2.8, ParticleTypes.LAVA);
                            x.glideTowards(new Vec3d(eruptAt.x, centre().y - 2.5, eruptAt.z), 0.6);
                        }
                    }).start((x, t, k) -> {
                        Vec3d c = eruptAt != null ? eruptAt : at(t);
                        surface(c);
                        x.circle(c, 2.8, 3, 1.6, BossDamage.CRUSH, 0.9);
                        x.particles(ParticleTypes.LAVA, c, 30, 1.2);
                        eruptAt = null;
                    }));
            add(Move.of("breath", 18, 24, 22).range(0, 10).cooldown(70).weight(3).lock()
                    .windupTick((x, t, k) -> {
                        if (k % 4 == 0) {
                            x.markCone(9, 28, ParticleTypes.FLAME);
                        }
                    }).tick((x, t, k) -> {
                        x.particles(ParticleTypes.FLAME, x.getEyePos().add(x.lockedDirection().multiply(3)), 10, 1.2);
                        if (k % 4 == 0) {
                            x.cone(9, 28, 0.35, BossDamage.SPELL, 0);
                            for (LivingEntity e : x.victims(x.getPos(), 10)) {
                                if (e.squaredDistanceTo(x) < 81) {
                                    e.setOnFireFor(2);
                                }
                            }
                        }
                    }).end((x, t, k) -> surfacedMoves++));
            add(Move.of("lash", 12, 2, 16).range(0, 5).cooldown(30).weight(2)
                    .start((x, t, k) -> x.circle(x.getPos(), 4.5, 2, 1.1, BossDamage.SWEEP, 1.0))
                    .end((x, t, k) -> surfacedMoves++));
            add(Move.of("spit", 14, 6, 16).range(6, 40).cooldown(60).weight(1.5).ranged()
                    .tick((x, t, k) -> {
                        if (k % 2 == 0) {
                            x.shoot(BossProjectile.Kind.EMBER, x.getEyePos(), t.getEyePos(), 1.0, 0.7);
                        }
                    }).end((x, t, k) -> surfacedMoves++));
        }

        void surface(Vec3d c) {
            burrowed = false;
            b.noClip = false;
            b.setNoGravity(false);
            b.setFlag(0, false);
            b.refreshPositionAndAngles(c.x, centre().y + 0.2, c.z, b.getYaw(), 0);
            surfacedUntil = b.getWorld().getTime() + 160;
            surfacedMoves = 0;
            b.roar(SoundEvents.ENTITY_RAVAGER_ROAR, 1.6f, 1.4f);
        }

        void burrow() {
            burrowed = true;
            b.setFlag(0, true);
            b.noClip = true;
            b.setNoGravity(true);
            b.getNavigation().stop();
            b.refreshPositionAndAngles(b.getX(), centre().y - 2.5, b.getZ(), b.getYaw(), 0);
            b.particles(ParticleTypes.CAMPFIRE_COSY_SMOKE, b.getPos().add(0, 2.5, 0), 20, 1.2);
        }

        @Override
        public boolean walks() {
            return !burrowed;
        }

        @Override
        public void onPhase(int phase) {
            if (phase == 0) {
                surfacedUntil = b.getWorld().getTime() + 120;
            }
        }

        @Override
        public void tick() {
            long now = b.getWorld().getTime();
            if (burrowed) {
                b.setVelocity(b.getVelocity().multiply(0.6, 0, 0.6));
                if (b.age % 2 == 0) {
                    b.particles(ParticleTypes.ASH, new Vec3d(b.getX(), centre().y + 0.3, b.getZ()), 3, 0.6);
                }
                LivingEntity t = b.target();
                if (t != null && b.stage() == BossEntity.IDLE) {
                    b.glideTowards(new Vec3d(t.getX(), centre().y - 2.5, t.getZ()), 0.35);
                }
            } else if (now > surfacedUntil || surfacedMoves >= 2) {
                if (b.stage() == BossEntity.IDLE) {
                    burrow();
                }
            }
            if (b.phaseIndex() >= 1 && b.age % 140 == 0) {
                for (ServerPlayerEntity p : fighters()) {
                    Vec3d c = at(p).add(b.getRandom().nextGaussian() * 3, 0, b.getRandom().nextGaussian() * 3);
                    zone(c, 2, 30, 0.6, BossDamage.AURA, ParticleTypes.LAVA, 0.5);
                    BlockPos fl = BlockPos.ofFloored(c).down();
                    if (b.enc != null && b.getWorld().getBlockState(fl).isOpaqueFullCube(b.getWorld(), fl)) {
                        b.enc.setTemp(fl, net.minecraft.block.Blocks.MAGMA_BLOCK.getDefaultState());
                    }
                }
            }
        }

        @Override
        public double damageTaken(DamageSource src, float amount, LivingEntity attacker) {
            return burrowed ? 0 : 1.15;
        }

        @Override
        public void onReset() {
            b.noClip = false;
        }
    }

    /** Ghost of the lamplighters: walks the lantern light; douse its lanterns to drag it into the open. */
    static final class Lamplighter extends Brain {
        BlockPos post;
        long relightAt;
        float relightDamage;

        Lamplighter(BossEntity b) {
            super(b);
            add(Move.of("orbs", 16, 9, 18).range(0, 40).cooldown(50).weight(3).ranged()
                    .tick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.shoot(BossProjectile.Kind.ORB, x.getEyePos(), t.getEyePos(), 0.6, 0.9);
                        }
                    }));
            add(Move.of("snuff", 24, 1, 20).range(0, 7).cooldown(200).weight(2)
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markCircle(new Vec3d(x.getX(), centre().y, x.getZ()), 6, ParticleTypes.SMOKE);
                        }
                    }).start((x, t, k) -> {
                        for (LivingEntity e : x.victims(x.getPos(), 7)) {
                            e.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0));
                        }
                        x.circle(new Vec3d(x.getX(), centre().y, x.getZ()), 6, 8, 0.8, BossDamage.AURA, 0.4);
                    }));
            add(Move.of("beam", 18, 10, 20).range(3, 16).cooldown(80).weight(2).lock()
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markLine(x.getPos(), x.flatDirTo(t), 15, ParticleTypes.END_ROD);
                        }
                    }).tick((x, t, k) -> {
                        if (k % 2 == 0) {
                            x.line(x.getEyePos().subtract(0, 1.5, 0), x.lockedDirection(), 15, 1.3, 0.4, BossDamage.SPELL, 0);
                            x.markLine(x.getEyePos().subtract(0, 1.5, 0), x.lockedDirection(), 15, ParticleTypes.FLAME);
                        }
                    }));
            add(Move.of("scythe", 12, 2, 16).range(0, 3.8).cooldown(20).weight(3)
                    .start((x, t, k) -> x.cone(3.8, 70, 1.1, BossDamage.STRIKE, 0.5)));
            add(Move.of("wisps", 20, 1, 16).range(0, 40).cooldown(400).weight(1).phases(0b10)
                    .start((x, t, k) -> {
                        if (x.getWorld() instanceof ServerWorld w) {
                            for (int i = 0; i < 2 && x.minions.size() < 3; i++) {
                                VexEntity v = EntityType.VEX.create(w);
                                if (v != null) {
                                    v.refreshPositionAndAngles(x.getX(), x.getY() + 1, x.getZ(), 0, 0);
                                    v.setLifeTicks(500);
                                    v.setTarget(t);
                                    w.spawnEntity(v);
                                    x.minions.add(v);
                                }
                            }
                        }
                    }));
        }

        List<BlockPos> litPosts() {
            List<BlockPos> l = new ArrayList<>();
            for (BlockPos p : devices("lantern")) {
                if (lit(p)) {
                    l.add(p);
                }
            }
            return l;
        }

        @Override
        public boolean walks() {
            return false;
        }

        @Override
        public void onPhase(int phase) {
            if (phase == 0) {
                for (BlockPos p : devices("lantern")) {
                    setLit(p, true);
                }
            }
        }

        @Override
        public void tick() {
            List<BlockPos> lit = litPosts();
            LivingEntity t = b.target();
            boolean grounded = lit.isEmpty();
            b.setFlag(0, grounded);
            if (grounded) {
                Vec3d goal = t != null ? t.getPos().add(b.flatDirTo(t).multiply(-2.2)) : centre();
                b.glideTowards(new Vec3d(goal.x, centre().y + 0.4, goal.z), 0.22);
            } else {
                if (post == null || !lit.contains(post) || b.age % 160 == 0) {
                    BlockPos best = lit.get(0);
                    for (BlockPos p : lit) {
                        if (t != null && p.getSquaredDistance(t.getPos()) < best.getSquaredDistance(t.getPos())) {
                            best = p;
                        }
                    }
                    post = best;
                }
                Vec3d goal = Vec3d.ofCenter(post).add(0, 2.5, 0);
                b.glideTowards(goal, 0.45);
                if (b.age % 3 == 0 && b.squaredDistanceTo(goal) > 4) {
                    b.particles(ParticleTypes.SOUL_FIRE_FLAME, b.getPos().add(0, 1, 0), 2, 0.3);
                }
            }
            // phase 2: it relights a dark lantern unless hurt enough while doing so
            long now = b.getWorld().getTime();
            if (b.phaseIndex() >= 1 && !grounded && relightAt == 0 && b.age % 300 == 0) {
                for (BlockPos p : devices("lantern")) {
                    if (!lit(p)) {
                        relightAt = now + 60;
                        relightDamage = b.getHealth();
                        post = p;
                        if (b.enc != null) {
                            b.enc.tell("The Lamplighter reaches for a dark lantern — stop it!", Formatting.GOLD);
                        }
                        break;
                    }
                }
            }
            if (relightAt > 0 && now >= relightAt) {
                if (relightDamage - b.getHealth() < b.getMaxHealth() * 0.04 && post != null) {
                    setLit(post, true);
                    b.roar(SoundEvents.BLOCK_COPPER_BULB_TURN_ON, 1.5f, 0.7f);
                }
                relightAt = 0;
            }
        }

        @Override
        public double damageTaken(DamageSource src, float amount, LivingEntity attacker) {
            return b.flag(0) ? 1.6 : 0.45;
        }

        @Override
        public void device(ServerPlayerEntity p, BlockPos pos, String kind) {
            if (kind.equals("lantern") && lit(pos) && channel(p, pos, 6)) {
                setLit(pos, false);
                b.roar(SoundEvents.BLOCK_COPPER_BULB_TURN_OFF, 1.5f, 0.5f);
                if (litPosts().isEmpty() && b.enc != null) {
                    b.enc.tell("The last lantern dies — the Lamplighter is exposed!", Formatting.YELLOW);
                }
            }
        }
    }

    // ============================================================================= LATE

    /** Crystal titan: hit its glowing resonance nodes; sonic waves to jump, crystal rain, slams. */
    static final class Resonant extends Brain {
        int broken;
        long glowUntil;
        double nodeDamage;

        Resonant(BossEntity b) {
            super(b);
            add(Move.of("wave", 24, 30, 26).range(0, 22).cooldown(90).weight(3)
                    .windupTick((x, t, k) -> {
                        if (k % 4 == 0) {
                            x.roar(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 2f, 0.6f + k * 0.02f);
                            x.particles(ParticleTypes.END_ROD, x.getPos().add(0, x.getHeight() * 0.6, 0), 6, 1.2);
                        }
                    }).tick((x, t, k) -> {
                        double r = 2 + k * 0.65;
                        x.markCircle(x.getPos(), r, ParticleTypes.END_ROD);
                        x.wave(x.getPos(), r - 1.4, r, 1.2, 0.6);
                    }).end((x, t, k) -> expose()));
            add(Move.of("rain", 18, 1, 18).range(0, 40).cooldown(60).weight(3).ranged()
                    .start((x, t, k) -> {
                        for (ServerPlayerEntity p : fighters()) {
                            zone(at(p), 2.2, 24, 1.0, BossDamage.SPELL, ParticleTypes.END_ROD, 0);
                            zone(at(p).add(x.getRandom().nextGaussian() * 3, 0, x.getRandom().nextGaussian() * 3), 2, 30, 1.0, BossDamage.SPELL,
                                    ParticleTypes.END_ROD, 0);
                        }
                    }));
            add(Move.of("slam", 26, 1, 30).range(0, 7).cooldown(80).weight(2)
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markCircle(x.getPos(), 7, ParticleTypes.CRIT);
                        }
                    }).start((x, t, k) -> {
                        x.particles(ParticleTypes.EXPLOSION, x.getPos(), 4, 2);
                        x.circle(x.getPos(), 7, 3, 1.6, BossDamage.CRUSH, 1.0);
                    }).end((x, t, k) -> expose()));
            add(Move.of("sweep", 16, 2, 20).range(0, 7).cooldown(30).weight(3)
                    .start((x, t, k) -> x.cone(7, 110, 1.3, BossDamage.SWEEP, 1.0)));
            add(Move.of("shards", 16, 1, 18).range(6, 40).cooldown(50).weight(1.5).ranged()
                    .start((x, t, k) -> {
                        for (int i = -2; i <= 2; i++) {
                            Vec3d d = x.flatDirTo(t).rotateY((float) Math.toRadians(i * 10));
                            x.shoot(BossProjectile.Kind.SHARD, x.getEyePos(), x.getEyePos().add(d.multiply(10)).add(0, t.getEyeY() - x.getEyeY(), 0), 1.2, 0.8);
                        }
                    }));
        }

        void expose() {
            if (broken < 4) {
                glowUntil = b.getWorld().getTime() + 110;
                b.setFlag(4, true);
                if (b.enc != null) {
                    b.enc.tell("A resonance node glows!", Formatting.LIGHT_PURPLE);
                }
            }
        }

        @Override
        public double[] phaseThresholds() {
            return new double[]{0.66, 0.33};
        }

        @Override
        public void tick() {
            if (glowUntil > 0 && b.getWorld().getTime() > glowUntil) {
                glowUntil = 0;
                b.setFlag(4, false);
            }
        }

        @Override
        public double damageTaken(DamageSource src, float amount, LivingEntity attacker) {
            if (b.flag(4)) {
                nodeDamage += amount * 2.5;
                if (nodeDamage >= b.getMaxHealth() * 0.1) {
                    nodeDamage = 0;
                    b.setFlag(broken, true);
                    broken++;
                    glowUntil = 0;
                    b.setFlag(4, false);
                    b.roar(SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 2.5f, 0.5f);
                    b.particles(ParticleTypes.END_ROD, b.getPos().add(0, b.getHeight() * 0.7, 0), 60, 1.5);
                    b.stagger(60);
                }
                return 2.5;
            }
            return broken >= 4 || b.phaseIndex() >= 2 ? 1.0 : 0.5;
        }
    }

    /** Aquatic matriarch: hunts between islands; beached after a lunge; wave slams; a whirlpool later. */
    static final class TideMother extends Brain {
        long beachedUntil;

        TideMother(BossEntity b) {
            super(b);
            add(Move.of("lunge", 24, 12, 16).range(4, 22).cooldown(60).weight(4).lock()
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markLine(x.getPos(), x.flatDirTo(t), Math.sqrt(x.squaredDistanceTo(t)) + 2, ParticleTypes.SPLASH);
                            x.particles(ParticleTypes.BUBBLE_POP, x.getPos(), 8, 1.2);
                        }
                    }).start((x, t, k) -> {
                        Vec3d d = x.lockedDirection();
                        double dist = Math.min(20, Math.sqrt(x.squaredDistanceTo(t)));
                        x.setVelocity(d.x * dist * 0.11, 0.6, d.z * dist * 0.11);
                        x.velocityModified = true;
                        x.setNoGravity(false);
                    }).end((x, t, k) -> {
                        x.circle(x.getPos(), 3.5, 3, 1.4, BossDamage.CRUSH, 1.0);
                        if (!x.isTouchingWater()) {
                            beachedUntil = x.getWorld().getTime() + 100;
                            x.setFlag(1, true);
                            if (x.enc != null) {
                                x.enc.tell("She is stranded on the stones!", Formatting.YELLOW);
                            }
                        }
                    }));
            add(Move.of("slam", 20, 1, 22).range(0, 7).cooldown(60).weight(2)
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markCircle(x.getPos(), 7, ParticleTypes.SPLASH);
                        }
                    }).start((x, t, k) -> x.wave(x.getPos(), 0, 7.5, 1.1, 1.5)));
            add(Move.of("orbs", 16, 8, 16).range(5, 40).cooldown(50).weight(2).ranged()
                    .tick((x, t, k) -> {
                        if (k % 2 == 0) {
                            x.shoot(BossProjectile.Kind.TIDE, x.getEyePos(), t.getEyePos(), 0.8, 0.8);
                        }
                    }));
            add(Move.of("grab", 16, 2, 18).range(0, 4.5).cooldown(30).weight(3)
                    .windupTick((x, t, k) -> x.particles(RED, x.getEyePos(), 2, 0.4))
                    .start((x, t, k) -> {
                        if (x.cone(4.5, 50, 1.5, BossDamage.CRUSH, 0) > 0 && t.squaredDistanceTo(x) < 30) {
                            t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2));
                        }
                    }));
            add(Move.of("whirlpool", 30, 100, 30).range(0, 40).cooldown(500).weight(3).phases(0b10)
                    .windupTick((x, t, k) -> x.particles(ParticleTypes.BUBBLE_COLUMN_UP, x.getPos(), 10, 3))
                    .tick((x, t, k) -> {
                        for (ServerPlayerEntity p : fighters()) {
                            if (p.isTouchingWater()) {
                                Vec3d pull = x.getPos().subtract(p.getPos()).normalize().multiply(0.06);
                                p.addVelocity(pull.x, 0, pull.z);
                                p.velocityModified = true;
                            }
                        }
                        if (k % 10 == 0) {
                            x.circle(x.getPos(), 3.5, 3, 0.5, BossDamage.AURA, 0);
                        }
                    }));
        }

        @Override
        public boolean walks() {
            return !b.isTouchingWater() && b.getWorld().getTime() > beachedUntil;
        }

        @Override
        public void tick() {
            long now = b.getWorld().getTime();
            boolean wet = b.isTouchingWater();
            b.setFlag(0, wet);
            if (b.flag(1) && now > beachedUntil) {
                b.setFlag(1, false);
            }
            if (b.flag(1)) {
                b.getNavigation().stop();
                b.setVelocity(b.getVelocity().multiply(0.2, 1, 0.2));
                return;
            }
            LivingEntity t = b.target();
            if (wet && t != null && b.stage() == BossEntity.IDLE) {
                Vec3d goal = t.getPos().add(b.flatDirTo(t).multiply(-6));
                b.glideTowards(new Vec3d(goal.x, Math.min(b.getY(), centre().y - 1), goal.z), 0.3);
            } else if (!wet && !b.flag(1) && b.stage() == BossEntity.IDLE && b.age % 40 == 0) {
                // slide back into the water
                Vec3d c = centre();
                b.getNavigation().startMovingTo(c.x, c.y, c.z, 1.2);
            }
        }

        @Override
        public double damageTaken(DamageSource src, float amount, LivingEntity attacker) {
            return b.flag(1) ? 1.6 : b.flag(0) ? 0.35 : 1.0;
        }
    }

    /** Blind sentinel hunting by sound: sneak to hide, ring the bells to bait it, strike its back. */
    static final class SilentWarden extends Brain {
        final Map<UUID, Double> noise = new HashMap<>();
        BlockPos bait;
        long baitUntil;

        SilentWarden(BossEntity b) {
            super(b);
            add(Move.of("boom", 30, 1, 26).range(4, 22).cooldown(80).weight(3).lock().ranged()
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markLine(x.getEyePos().subtract(0, 2, 0), x.flatDirTo(t), 20, ParticleTypes.SCULK_SOUL);
                        }
                        if (k == 2) {
                            x.roar(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 2f, 1f);
                        }
                    }).start((x, t, k) -> {
                        x.roar(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 3f, 1f);
                        Vec3d from = x.getPos().add(0, 1, 0);
                        for (double s = 1; s < 20; s += 1.5) {
                            x.particles(ParticleTypes.SONIC_BOOM, from.add(x.lockedDirection().multiply(s)), 1, 0);
                        }
                        x.line(x.getPos(), x.lockedDirection(), 20, 1.5, 2.0, BossDamage.CRUSH, 1.2);
                    }));
            add(Move.of("grab", 14, 2, 18).range(0, 3.8).cooldown(30).weight(3)
                    .windupTick((x, t, k) -> x.particles(RED, x.getEyePos(), 2, 0.4))
                    .start((x, t, k) -> x.cone(3.8, 45, 1.6, BossDamage.CRUSH, 0.6)));
            add(Move.of("stomp", 18, 1, 20).range(0, 6).cooldown(60).weight(2)
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markCircle(x.getPos(), 6, ParticleTypes.SCULK_CHARGE_POP);
                        }
                    }).start((x, t, k) -> x.wave(x.getPos(), 0, 6.5, 1.2, 0.8)));
            add(Move.of("swipe", 10, 2, 14).range(0, 4).cooldown(16).weight(3)
                    .start((x, t, k) -> x.cone(4, 70, 1.0, BossDamage.STRIKE, 0.4)));
        }

        @Override
        public void tick() {
            for (ServerPlayerEntity p : fighters()) {
                double n = noise.getOrDefault(p.getUuid(), 0.0) * 0.93;
                double speed = p.getVelocity().horizontalLength();
                n += (p.isSneaking() ? 0.15 : 1.0) * (0.2 + speed * 6) + (p.isSprinting() ? 1.5 : 0) + (p.handSwinging ? 2 : 0);
                noise.put(p.getUuid(), n);
            }
            long now = b.getWorld().getTime();
            if (bait != null && now > baitUntil) {
                bait = null;
            }
            if (bait != null && b.stage() == BossEntity.IDLE) {
                b.getNavigation().startMovingTo(bait.getX() + 0.5, bait.getY(), bait.getZ() + 0.5, 1.4);
            }
            if (b.phaseIndex() >= 1 && b.age % 160 == 0) {
                for (ServerPlayerEntity p : fighters()) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0));
                }
                b.particles(ParticleTypes.SCULK_SOUL, b.getPos(), 30, 4);
            }
        }

        @Override
        public LivingEntity pickTarget(List<ServerPlayerEntity> players) {
            if (bait != null) {
                return null;
            }
            ServerPlayerEntity best = null;
            double bestN = 1.2;
            for (ServerPlayerEntity p : players) {
                if (p.isCreative() || b.enc.downed(p)) {
                    continue;
                }
                double n = noise.getOrDefault(p.getUuid(), 0.0) / (1 + Math.sqrt(b.squaredDistanceTo(p)) * 0.08);
                if (n > bestN) {
                    bestN = n;
                    best = p;
                }
            }
            return best;
        }

        @Override
        public boolean walks() {
            return bait == null;
        }

        @Override
        public double damageTaken(DamageSource src, float amount, LivingEntity attacker) {
            if (attacker != null) {
                noise.merge(attacker.getUuid(), 6.0, Double::sum);
                Vec3d facing = b.getRotationVector().multiply(1, 0, 1);
                Vec3d from = attacker.getPos().subtract(b.getPos()).multiply(1, 0, 1);
                if (facing.lengthSquared() > 0 && from.lengthSquared() > 0 && facing.normalize().dotProduct(from.normalize()) < -0.3) {
                    return bait != null ? 1.8 : 1.5;
                }
            }
            return 1.0;
        }

        @Override
        public void device(ServerPlayerEntity p, BlockPos pos, String kind) {
            if (kind.equals("bell")) {
                b.roar(SoundEvents.BLOCK_BELL_USE, 3f, 0.7f);
                b.getWorld().playSound(null, pos, SoundEvents.BLOCK_BELL_RESONATE, net.minecraft.sound.SoundCategory.BLOCKS, 2f, 0.6f);
                bait = pos;
                baitUntil = b.getWorld().getTime() + 100;
                b.enc.participate(p, 0);
            }
        }
    }

    // ============================================================================= SECRET

    /** Heartwood treant: root eruption lines, thorn volleys, root spirits; regrows until its root hearts are cut. */
    static final class RootSovereign extends Brain {
        RootSovereign(BossEntity b) {
            super(b);
            add(Move.of("roots", 20, 18, 20).range(0, 18).cooldown(60).weight(3).lock()
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markLine(x.getPos(), x.flatDirTo(t), 17, ParticleTypes.COMPOSTER);
                        }
                    }).tick((x, t, k) -> {
                        Vec3d c = x.getPos().add(x.lockedDirection().multiply(2 + k));
                        x.particles(ParticleTypes.COMPOSTER, c, 6, 0.6);
                        x.circle(c, 1.7, 2, 1.3, BossDamage.CRUSH, 0);
                        for (LivingEntity e : x.victims(c, 1.7)) {
                            e.addVelocity(0, 0.6, 0);
                            e.velocityModified = true;
                        }
                    }));
            add(Move.of("thorns", 16, 1, 18).range(4, 40).cooldown(50).weight(2).ranged()
                    .start((x, t, k) -> {
                        for (int i = -3; i <= 3; i++) {
                            Vec3d d = x.flatDirTo(t).rotateY((float) Math.toRadians(i * 8));
                            x.shoot(BossProjectile.Kind.THORN, x.getEyePos(), x.getEyePos().add(d.multiply(10)).add(0, t.getEyeY() - x.getEyeY(), 0), 1.2, 0.7);
                        }
                    }));
            add(Move.of("slam", 22, 1, 24).range(0, 6.5).cooldown(60).weight(2)
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markCircle(x.getPos(), 6.5, ParticleTypes.COMPOSTER);
                        }
                    }).start((x, t, k) -> x.circle(x.getPos(), 6.5, 3, 1.5, BossDamage.CRUSH, 1.0)));
            add(Move.of("swat", 14, 2, 16).range(0, 6).cooldown(20).weight(3)
                    .start((x, t, k) -> x.cone(6, 80, 1.2, BossDamage.SWEEP, 1.0)));
            add(Move.of("spirits", 20, 1, 16).range(0, 40).cooldown(400).weight(1)
                    .start((x, t, k) -> x.summon("shadecat", 2, 3, 6)));
        }

        @Override
        public double[] phaseThresholds() {
            return new double[]{0.6, 0.3};
        }

        @Override
        public void onPhase(int phase) {
            if (phase == 0) {
                for (BlockPos p : devices("root")) {
                    setLit(p, true);
                }
            }
            if (phase == 1 && b.enc != null) {
                b.enc.tell("Its wounds close over! Something in the chamber feeds it.", Formatting.GREEN);
            }
        }

        @Override
        public void tick() {
            if (b.phaseIndex() >= 1 && b.age % 20 == 0 && devices("root").stream().anyMatch(this::lit)) {
                b.heal(b.getMaxHealth() * 0.006f);
                b.particles(ParticleTypes.HAPPY_VILLAGER, b.getPos().add(0, b.getHeight() * 0.5, 0), 4, 1);
            }
        }

        @Override
        public void device(ServerPlayerEntity p, BlockPos pos, String kind) {
            if (kind.equals("root") && lit(pos) && channel(p, pos, 10)) {
                setLit(pos, false);
                b.roar(SoundEvents.BLOCK_ROOTED_DIRT_BREAK, 2f, 0.4f);
                b.stagger(30);
            }
        }
    }

    /** Starlit thing from the seam: sweeping gaze beams, star-falls, gravity; it tires and sinks to the ground. */
    static final class SeamWatcher extends Brain {
        int movesSinceRest;
        long restUntil;

        SeamWatcher(BossEntity b) {
            super(b);
            add(Move.of("gaze", 24, 30, 22).range(0, 26).cooldown(80).weight(3).lock()
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markLine(new Vec3d(x.getX(), centre().y, x.getZ()), x.flatDirTo(t), 24, ParticleTypes.END_ROD);
                        }
                    }).tick((x, t, k) -> {
                        double sweep = Math.toRadians((k - 15) * (x.phaseIndex() >= 1 ? 3.2 : 2.6));
                        Vec3d d = x.lockedDirection().rotateY((float) sweep);
                        Vec3d from = new Vec3d(x.getX(), centre().y + 0.5, x.getZ());
                        if (k % 3 == 0) {
                            x.markLine(from, d, 24, ParticleTypes.FIREWORK);
                            x.line(from, d, 24, 1.2, 0.45, BossDamage.SPELL, 0);
                        }
                    }).end((x, t, k) -> movesSinceRest++));
            add(Move.of("starfall", 18, 1, 18).range(0, 40).cooldown(60).weight(3).ranged()
                    .start((x, t, k) -> {
                        for (ServerPlayerEntity p : fighters()) {
                            for (int i = 0; i < 2; i++) {
                                zone(at(p).add(x.getRandom().nextGaussian() * 2.5, 0, x.getRandom().nextGaussian() * 2.5), 2.5, 26, 1.2, BossDamage.SPELL,
                                        ParticleTypes.FIREWORK, 0);
                            }
                        }
                    }).end((x, t, k) -> movesSinceRest++));
            add(Move.of("gravity", 20, 40, 20).range(0, 40).cooldown(240).weight(2)
                    .tick((x, t, k) -> {
                        Vec3d c = new Vec3d(x.getX(), centre().y, x.getZ());
                        boolean push = x.phaseIndex() >= 1;
                        for (ServerPlayerEntity p : fighters()) {
                            Vec3d v = c.subtract(p.getPos()).multiply(1, 0, 1).normalize().multiply(push ? -0.07 : 0.06);
                            p.addVelocity(v.x, 0, v.z);
                            p.velocityModified = true;
                        }
                        if (k % 5 == 0) {
                            x.markCircle(c, 3, ParticleTypes.REVERSE_PORTAL);
                            x.circle(c, 3, 3, 0.4, BossDamage.AURA, 0);
                        }
                    }).end((x, t, k) -> movesSinceRest++));
            add(Move.of("orbs", 14, 6, 16).range(0, 40).cooldown(50).weight(2).ranged()
                    .tick((x, t, k) -> {
                        if (k % 2 == 0) {
                            x.shoot(BossProjectile.Kind.STAR, x.getEyePos(), t.getEyePos(), 0.7, 0.7);
                        }
                    }).end((x, t, k) -> movesSinceRest++));
            add(Move.of("claw", 12, 2, 16).range(0, 4).cooldown(20).weight(2)
                    .start((x, t, k) -> x.cone(4, 70, 1.1, BossDamage.STRIKE, 0.5)));
        }

        @Override
        public boolean walks() {
            return false;
        }

        @Override
        public void tick() {
            long now = b.getWorld().getTime();
            if (movesSinceRest >= 3 && restUntil == 0 && b.stage() == BossEntity.IDLE) {
                restUntil = now + 110;
                movesSinceRest = 0;
                b.setFlag(0, true);
                if (b.enc != null) {
                    b.enc.tell("The Watcher sinks, its light guttering!", Formatting.YELLOW);
                }
            }
            if (restUntil > 0) {
                b.glideTowards(new Vec3d(b.getX(), centre().y + 0.3, b.getZ()), 0.2);
                if (now > restUntil) {
                    restUntil = 0;
                    b.setFlag(0, false);
                }
                return;
            }
            LivingEntity t = b.target();
            double ang = b.age * 0.01;
            Vec3d c = centre();
            Vec3d goal = new Vec3d(c.x + Math.cos(ang) * 8, c.y + 9, c.z + Math.sin(ang) * 8);
            if (t != null && b.stage() == BossEntity.IDLE) {
                b.faceTowards(t.getPos());
            }
            b.glideTowards(goal, 0.18);
        }

        @Override
        public double damageTaken(DamageSource src, float amount, LivingEntity attacker) {
            return b.flag(0) ? 1.8 : 0.8;
        }
    }

    // ============================================================================= FINAL

    /** The dream of the deep: three phases — dream echoes, the engine coils, the waking. */
    static final class Somnarch extends Brain {
        final Map<UUID, Double> noise = new HashMap<>();
        final Set<UUID> hit = new HashSet<>();
        long vulnerableUntil;
        boolean engineDone;

        Somnarch(BossEntity b) {
            super(b);
            add(Move.of("swipe", 12, 2, 16).range(0, 5).cooldown(18).weight(3)
                    .start((x, t, k) -> x.cone(5, 80, 1.1, BossDamage.STRIKE, 0.6)));
            add(Move.of("echo_charge", 24, 24, 24).range(6, 24).cooldown(90).weight(2).lock()
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markLine(x.getPos(), x.flatDirTo(t), 18, ParticleTypes.SCULK_SOUL);
                        }
                    }).start((x, t, k) -> hit.clear()).tick((x, t, k) -> charge(x, k, 0.8, 1.5, hit)));
            add(Move.of("echo_spores", 16, 1, 16).range(0, 40).cooldown(70).weight(2).ranged()
                    .start((x, t, k) -> {
                        for (ServerPlayerEntity p : fighters()) {
                            zone(at(p), 3.2, 24, 1.1, BossDamage.AURA, ParticleTypes.SCULK_SOUL, 0.2);
                        }
                    }));
            add(Move.of("echo_boom", 30, 1, 24).range(4, 24).cooldown(110).weight(2).lock().ranged()
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markLine(x.getPos(), x.flatDirTo(t), 22, ParticleTypes.SONIC_BOOM);
                        }
                        if (k == 2) {
                            x.roar(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 2f, 0.8f);
                        }
                    }).start((x, t, k) -> {
                        x.roar(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 3f, 0.8f);
                        x.line(x.getPos(), x.lockedDirection(), 22, 1.6, 2.0, BossDamage.CRUSH, 1.2);
                    }));
            add(Move.of("dream_wave", 22, 26, 22).range(0, 22).cooldown(90).weight(2)
                    .tick((x, t, k) -> {
                        double r = 2 + k * 0.7;
                        x.markCircle(x.getPos(), r, ParticleTypes.SCULK_CHARGE_POP);
                        x.wave(x.getPos(), r - 1.4, r, 1.1, 0.6);
                    }));
            add(Move.of("head_slam", 26, 1, 30).range(0, 7).cooldown(70).weight(3).phases(0b100)
                    .windupTick((x, t, k) -> {
                        if (k % 3 == 0) {
                            x.markCircle(x.getPos().add(x.lockedDirection().multiply(3)), 5, ParticleTypes.LAVA);
                        }
                    }).start((x, t, k) -> x.circle(x.getPos().add(x.lockedDirection().multiply(3)), 5, 3, 1.7, BossDamage.CRUSH, 1.0)));
            add(Move.of("wake_roar", 30, 1, 80).range(0, 40).cooldown(320).weight(4).phases(0b100)
                    .windupTick((x, t, k) -> {
                        if (k % 4 == 0) {
                            x.markCircle(x.getPos(), 10, ParticleTypes.LAVA);
                        }
                    }).start((x, t, k) -> {
                        x.roar(SoundEvents.ENTITY_WARDEN_ROAR, 3f, 0.5f);
                        x.circle(x.getPos(), 10, 5, 0.8, BossDamage.AURA, 1.4);
                        vulnerableUntil = x.getWorld().getTime() + 80;
                        x.setFlag(1, true);
                        if (x.enc != null) {
                            x.enc.tell("It rears up, its heart exposed!", Formatting.YELLOW);
                        }
                    }));
            add(Move.of("motes", 14, 8, 16).range(6, 40).cooldown(50).weight(1).ranged()
                    .tick((x, t, k) -> {
                        if (k % 2 == 0) {
                            x.shoot(BossProjectile.Kind.ORB, x.getEyePos(), t.getEyePos(), 0.7, 0.8);
                        }
                    }));
        }

        @Override
        public double[] phaseThresholds() {
            return new double[]{0.66, 0.33};
        }

        @Override
        public void onPhase(int phase) {
            if (phase == 0) {
                for (BlockPos p : devices("coil")) {
                    setLit(p, true);
                }
            }
            if (phase == 1) {
                for (BlockPos p : devices("coil")) {
                    setLit(p, false);
                }
                if (b.enc != null) {
                    b.enc.tell("The engine coils go dark — the dream feeds on the cold. Relight them!", Formatting.AQUA);
                }
            }
            if (phase == 2 && b.enc != null) {
                b.enc.tell("The Sleeper's true face rises from the fire!", Formatting.RED);
            }
        }

        boolean coilsDark() {
            return devices("coil").stream().anyMatch(p -> !lit(p));
        }

        @Override
        public void tick() {
            for (ServerPlayerEntity p : fighters()) {
                double n = noise.getOrDefault(p.getUuid(), 0.0) * 0.94;
                n += (p.isSneaking() ? 0.2 : 1.0) * (0.3 + p.getVelocity().horizontalLength() * 6) + (p.isSprinting() ? 1.2 : 0);
                noise.put(p.getUuid(), n);
            }
            long now = b.getWorld().getTime();
            if (vulnerableUntil > 0 && now > vulnerableUntil) {
                vulnerableUntil = 0;
                b.setFlag(1, false);
            }
            if (b.phaseIndex() == 1) {
                if (coilsDark()) {
                    if (b.age % 20 == 0) {
                        b.heal(b.getMaxHealth() * 0.004f);
                    }
                } else if (!engineDone) {
                    engineDone = true;
                    b.roar(SoundEvents.BLOCK_BEACON_ACTIVATE, 3f, 0.5f);
                    b.stagger(120);
                    if (b.enc != null) {
                        b.enc.tell("The Heart Engine roars back to life!", Formatting.GOLD);
                    }
                }
            }
            if (b.phaseIndex() >= 2 && b.age % 60 == 0) {
                for (ServerPlayerEntity p : fighters()) {
                    zone(at(p).add(b.getRandom().nextGaussian() * 2, 0, b.getRandom().nextGaussian() * 2), 2.2, 26, 1.0, BossDamage.SPELL,
                            ParticleTypes.END_ROD, 0);
                }
            }
        }

        @Override
        public LivingEntity pickTarget(List<ServerPlayerEntity> players) {
            if (b.phaseIndex() > 0) {
                return b.threatTarget(players);
            }
            ServerPlayerEntity best = null;
            double bestN = 0.8;
            for (ServerPlayerEntity p : players) {
                if (p.isCreative() || b.enc.downed(p)) {
                    continue;
                }
                double n = noise.getOrDefault(p.getUuid(), 0.0) / (1 + Math.sqrt(b.squaredDistanceTo(p)) * 0.06);
                if (n > bestN) {
                    bestN = n;
                    best = p;
                }
            }
            return best != null ? best : b.threatTarget(players);
        }

        @Override
        public double damageTaken(DamageSource src, float amount, LivingEntity attacker) {
            if (attacker != null) {
                noise.merge(attacker.getUuid(), 5.0, Double::sum);
            }
            if (b.phaseIndex() == 1 && coilsDark()) {
                return 0.6;
            }
            return b.flag(1) ? 1.5 : 1.0;
        }

        @Override
        public void device(ServerPlayerEntity p, BlockPos pos, String kind) {
            if (kind.equals("coil") && b.phaseIndex() == 1 && !lit(pos) && channel(p, pos, 12)) {
                setLit(pos, true);
                b.roar(SoundEvents.BLOCK_BEACON_POWER_SELECT, 2f, 0.6f);
                Advancements_.device(p);
            }
        }
    }

    /** Small indirection so brains can report device use without importing the guide package everywhere. */
    static final class Advancements_ {
        static void device(ServerPlayerEntity p) {
            dev.underdeep.guide.Advancements.event(p, "boss_device", "coil");
        }
    }
}
