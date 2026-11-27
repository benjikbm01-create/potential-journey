package dev.underdeep.combat;

import dev.underdeep.Underdeep;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.guide.Advancements;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Stage 10 player combat, layered on vanilla combat (attack cooldown, shields, armour, enchantments, durability and
 * health are untouched). Everything that matters is decided on the server: stamina, dodge invulnerability, parry
 * windows, heavy-attack timing and hit detection, counters and stagger. Clients only send intents (dodge with a
 * movement direction, heavy attack) and receive the HUD state and wind-up animations.
 * <ul>
 *   <li>Stamina (0–100) is spent only by dodging, heavy attacks, weapon parries and blocking hits with a shield;
 *   walking, sprinting, mining and building never use it. It regenerates quickly after a short pause.</li>
 *   <li>Dodge (key, default Left Alt): a short dash in the movement direction with a few ticks of invulnerability
 *   against attacks (not against falls, fire or hazards). Avoiding a blow this way opens a counter.</li>
 *   <li>Heavy attack (sneak + attack with a melee weapon): a committed, style-specific blow with wind-up and recovery.</li>
 *   <li>Parry: raise a shield just before a blow, or right-click with a melee weapon (no shield in the off hand). A
 *   parried attacker is staggered and the next hit is a counter (riposte).</li>
 *   <li>Blocking with a shield costs stamina per blocked damage; running out breaks the guard.</li>
 * </ul>
 */
public final class Combat {
    private Combat() {
    }

    public static final float MAX_STAMINA = 100;
    private static final Identifier WINDUP_SLOW = Identifier.of(Underdeep.MOD_ID, "heavy_windup");

    /** Per-player combat state (transient: nothing here needs to survive a restart). */
    public static final class State {
        public float stamina = MAX_STAMINA;
        long lastSpend = -100;
        long dodgeUntil, dodgeReady;
        long parryUntil, parryReady;
        boolean parryShield;
        long openingUntil;
        int heavyStage; // 0 idle, 1 wind-up, 2 recovery
        long heavyHit, heavyEnd;
        WeaponStyle heavyStyle = WeaponStyle.FIST;
        boolean executingHeavy;
        long shieldUseStart = -1;
        long lastGround;
        float sentStamina = -1;
        int sentFlags = -1;

        public boolean inOpening(long now) {
            return openingUntil > now;
        }

        public boolean inWindup() {
            return heavyStage == 1;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    public static State of(PlayerEntity p) {
        return STATES.computeIfAbsent(p.getUuid(), k -> new State());
    }

    /** Boss hooks: attacks that cannot be parried (must be dodged) and extra stamina drain of heavy blocked blows. */
    public static Predicate<DamageSource> UNPARRYABLE = s -> false;
    public static ToDoubleFunction<DamageSource> BLOCK_DRAIN = s -> 1.0;
    /** Bosses decide whether a parry staggers them (their own poise rules). */
    public static java.util.function.BiConsumer<LivingEntity, PlayerEntity> ON_PARRIED = (a, p) -> Poise.damage(a, 999, p, true);

    // ---------------------------------------------------------------------------------------------- payloads

    public record Dodge(float forward, float sideways) implements CustomPayload {
        public static final Id<Dodge> ID = new Id<>(Identifier.of(Underdeep.MOD_ID, "combat_dodge"));
        public static final PacketCodec<RegistryByteBuf, Dodge> CODEC = PacketCodec.of((p, b) -> {
            b.writeFloat(p.forward);
            b.writeFloat(p.sideways);
        }, b -> new Dodge(b.readFloat(), b.readFloat()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record Heavy() implements CustomPayload {
        public static final Id<Heavy> ID = new Id<>(Identifier.of(Underdeep.MOD_ID, "combat_heavy"));
        public static final PacketCodec<RegistryByteBuf, Heavy> CODEC = PacketCodec.unit(new Heavy());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** S2C: stamina and state flags (1 opening, 2 wind-up, 4 parry window, 8 recovery). */
    public record Hud(float stamina, int flags) implements CustomPayload {
        public static final Id<Hud> ID = new Id<>(Identifier.of(Underdeep.MOD_ID, "combat_hud"));
        public static final PacketCodec<RegistryByteBuf, Hud> CODEC = PacketCodec.of((p, b) -> {
            b.writeFloat(p.stamina);
            b.writeVarInt(p.flags);
        }, b -> new Hud(b.readFloat(), b.readVarInt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** S2C: an entity began a heavy wind-up of {@code ticks} with a style (animation only; damage is server-side). */
    public record Windup(int entity, int ticks, int style) implements CustomPayload {
        public static final Id<Windup> ID = new Id<>(Identifier.of(Underdeep.MOD_ID, "combat_windup"));
        public static final PacketCodec<RegistryByteBuf, Windup> CODEC = PacketCodec.of((p, b) -> {
            b.writeVarInt(p.entity);
            b.writeVarInt(p.ticks);
            b.writeVarInt(p.style);
        }, b -> new Windup(b.readVarInt(), b.readVarInt(), b.readVarInt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    static UnderdeepConfig.Combat cfg() {
        return UnderdeepConfig.get().combat;
    }

    public static void init() {
        CreatureSpit.register();
        UNPARRYABLE = src -> src.getAttacker() instanceof LivingEntity l && CombatGoal.UNPARRYABLE_NOW.contains(l);
        PayloadTypeRegistry.playC2S().register(Dodge.ID, Dodge.CODEC);
        PayloadTypeRegistry.playC2S().register(Heavy.ID, Heavy.CODEC);
        PayloadTypeRegistry.playS2C().register(Hud.ID, Hud.CODEC);
        PayloadTypeRegistry.playS2C().register(Windup.ID, Windup.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Dodge.ID, (p, ctx) -> ctx.server().execute(() -> dodge(ctx.player(), p.forward(), p.sideways())));
        ServerPlayNetworking.registerGlobalReceiver(Heavy.ID, (p, ctx) -> ctx.server().execute(() -> heavy(ctx.player())));
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> s.execute(() -> STATES.remove(h.player.getUuid())));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(Combat::allowDamage);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> afterDamage(target, source, base, taken, blocked));
        UseItemCallback.EVENT.register(Combat::useItem);
        ServerTickEvents.END_SERVER_TICK.register(Combat::tick);
    }

    // ---------------------------------------------------------------------------------------------- actions

    static boolean canAct(ServerPlayerEntity p) {
        return cfg().enabled && p.isAlive() && !p.isSpectator() && !p.isSleeping() && !p.hasVehicle() && !p.isFallFlying()
                && !p.getAbilities().flying;
    }

    static void spend(State s, float amount, long now) {
        s.stamina = Math.max(0, s.stamina - amount);
        s.lastSpend = now;
    }

    /** Dash direction from yaw and the client's movement input (clamped; backwards when standing still). */
    public static Vec3d dodgeVelocity(float yaw, float forward, float sideways, boolean onGround) {
        float f = MathHelper.clamp(forward, -1, 1), s = MathHelper.clamp(sideways, -1, 1);
        if (Math.abs(f) < 0.1 && Math.abs(s) < 0.1) {
            f = -1;
        }
        double len = Math.sqrt(f * f + s * s);
        f /= len;
        s /= len;
        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad), cos = Math.cos(rad);
        double x = s * cos - f * sin, z = f * cos + s * sin;
        double speed = 0.92;
        return new Vec3d(x * speed, onGround ? 0.16 : 0.02, z * speed);
    }

    static void dodge(ServerPlayerEntity p, float forward, float sideways) {
        if (!canAct(p) || !cfg().dodge) {
            return;
        }
        State s = of(p);
        long now = p.getWorld().getTime();
        boolean grounded = p.isOnGround() || now - s.lastGround <= 3;
        if (s.heavyStage == 1 || now < s.dodgeReady || s.stamina < cfg().dodgeStamina || !grounded || p.isTouchingWater() || p.isInLava()) {
            return;
        }
        spend(s, (float) cfg().dodgeStamina, now);
        s.dodgeUntil = now + cfg().dodgeInvulnerableTicks + (cfg().assist ? 2 : 0);
        s.dodgeReady = now + 14;
        p.setVelocity(dodgeVelocity(p.getYaw(), forward, sideways, p.isOnGround()));
        p.velocityModified = true;
        ServerWorld w = p.getServerWorld();
        w.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.25, 0.02, 0.25, 0.02);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GOAT_LONG_JUMP, SoundCategory.PLAYERS, 0.5f, 1.4f);
    }

    static void heavy(ServerPlayerEntity p) {
        if (!canAct(p) || !cfg().heavyAttacks) {
            return;
        }
        State s = of(p);
        long now = p.getWorld().getTime();
        WeaponStyle st = WeaponStyle.of(p.getMainHandStack());
        if (!st.melee() || s.heavyStage != 0 || s.stamina < st.stamina || p.getAttackCooldownProgress(0.5f) < 0.8f) {
            return;
        }
        spend(s, st.stamina, now);
        s.heavyStage = 1;
        s.heavyStyle = st;
        int windup = Math.max(3, (int) Math.round(st.windup * cfg().heavyWindupScale));
        s.heavyHit = now + windup;
        var speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(WINDUP_SLOW);
            speed.addTemporaryModifier(new EntityAttributeModifier(WINDUP_SLOW, -0.55, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        broadcast(p, new Windup(p.getId(), windup, st.ordinal()));
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_CHAIN.value(), SoundCategory.PLAYERS, 0.7f, 0.6f);
    }

    static void endWindup(ServerPlayerEntity p) {
        var speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(WINDUP_SLOW);
        }
    }

    /** Executes a heavy attack at its hit tick: server-side hit detection in the style's shape. */
    static void executeHeavy(ServerPlayerEntity p, State s, long now) {
        endWindup(p);
        WeaponStyle st = s.heavyStyle;
        s.heavyStage = 2;
        s.heavyEnd = now + st.recovery;
        if (!p.isAlive() || WeaponStyle.of(p.getMainHandStack()) != st) {
            return; // swapped weapons mid swing: the blow is lost
        }
        Vec3d look = p.getRotationVector().multiply(1, 0, 1).normalize();
        if (st.lunge > 0 && p.isOnGround()) {
            p.addVelocity(look.x * st.lunge, 0.05, look.z * st.lunge);
            p.velocityModified = true;
        }
        Vec3d eye = p.getEyePos();
        Entity aimed = aimed(p, st.reach + 0.5);
        List<LivingEntity> hits = new ArrayList<>();
        Box box = p.getBoundingBox().expand(st.reach + 1, 1.5, st.reach + 1);
        for (Entity e : p.getWorld().getOtherEntities(p, box, e -> e instanceof LivingEntity l && l.isAlive() && !(e instanceof PlayerEntity))) {
            LivingEntity l = (LivingEntity) e;
            if (e != aimed && !hostile(l, p)) {
                continue;
            }
            Vec3d to = l.getPos().add(0, l.getHeight() * 0.5, 0).subtract(eye);
            double dist = Math.sqrt(to.x * to.x + to.z * to.z) - l.getWidth() * 0.5;
            if (dist > st.reach || Math.abs(to.y) > st.reach * 0.8 + l.getHeight()) {
                continue;
            }
            Vec3d flat = new Vec3d(to.x, 0, to.z);
            double ang = flat.lengthSquared() < 1e-4 ? 0 : Math.toDegrees(Math.acos(MathHelper.clamp(flat.normalize().dotProduct(look), -1, 1)));
            if (ang > st.arc && st.arc < 360 && e != aimed) {
                continue;
            }
            hits.add(l);
        }
        hits.sort((a, b) -> Double.compare(a.squaredDistanceTo(p), b.squaredDistanceTo(p)));
        ServerWorld w = p.getServerWorld();
        int n = 0;
        for (LivingEntity l : hits) {
            if (n++ >= st.targets) {
                break;
            }
            s.executingHeavy = true;
            try {
                p.attack(l);
            } finally {
                s.executingHeavy = false;
            }
        }
        p.resetLastAttackedTicks();
        p.swingHand(Hand.MAIN_HAND, true);
        Vec3d front = p.getPos().add(look.multiply(Math.min(2, st.reach * 0.5)));
        switch (st) {
            case WARHAMMER -> {
                w.spawnParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.2, p.getZ(), 1, 0, 0, 0, 0);
                w.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX(), p.getY() + 0.1, p.getZ(), 10, st.reach * 0.5, 0.05, st.reach * 0.5, 0.01);
                w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.35f, 1.4f);
            }
            case GREATSWORD, BATTLEAXE -> {
                w.spawnParticles(ParticleTypes.SWEEP_ATTACK, front.x, p.getY() + 1, front.z, 3, 0.8, 0.1, 0.8, 0);
                w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1f, 0.7f);
            }
            default -> w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1f, 0.8f);
        }
        if (n > 0) {
            Advancements.event(p, "combat_heavy", st.name().toLowerCase());
            if (n >= 3) {
                Advancements.event(p, "combat_multi_hit", st.name().toLowerCase());
            }
        }
    }

    /** Whether the heavy attack may hit this entity without it being aimed at (hostiles, bosses, wild predators). */
    static boolean hostile(LivingEntity l, PlayerEntity p) {
        if (l instanceof Monster || l instanceof Poise.Staggerable) {
            return true;
        }
        if (l instanceof net.minecraft.entity.mob.MobEntity m && m.getTarget() == p) {
            return true;
        }
        return false;
    }

    static Entity aimed(PlayerEntity p, double reach) {
        Vec3d eye = p.getEyePos();
        Vec3d end = eye.add(p.getRotationVector().multiply(reach));
        var hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(p, eye, end, p.getBoundingBox().stretch(p.getRotationVector().multiply(reach)).expand(1),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator(), reach * reach);
        return hit == null ? null : hit.getEntity();
    }

    static TypedActionResult<ItemStack> useItem(PlayerEntity player, net.minecraft.world.World world, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (hand != Hand.MAIN_HAND || !cfg().enabled || !cfg().weaponParry) {
            return TypedActionResult.pass(stack);
        }
        WeaponStyle st = WeaponStyle.of(stack);
        if (!st.melee() || stack.getItem() instanceof TridentItem || player.getOffHandStack().getItem() instanceof ShieldItem) {
            return TypedActionResult.pass(stack);
        }
        if (world.isClient) {
            return TypedActionResult.success(stack, false);
        }
        ServerPlayerEntity p = (ServerPlayerEntity) player;
        State s = of(p);
        long now = world.getTime();
        if (!canAct(p) || s.heavyStage != 0 || now < s.parryReady || s.stamina < 10) {
            return TypedActionResult.fail(stack);
        }
        spend(s, 10, now);
        openParry(p, s, now, false, st == WeaponStyle.SWORD ? 2 : 0);
        s.parryReady = now + 18;
        p.swingHand(Hand.MAIN_HAND, true);
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.8f, 1.5f);
        return TypedActionResult.success(stack, false);
    }

    static void openParry(ServerPlayerEntity p, State s, long now, boolean shield, int bonus) {
        int lag = cfg().latencyCompensation ? Math.min(4, p.networkHandler.getLatency() / 50) : 0;
        s.parryUntil = now + cfg().parryWindowTicks + bonus + lag + (cfg().assist ? 3 : 0);
        s.parryShield = shield;
    }

    // ---------------------------------------------------------------------------------------------- damage

    static boolean dodgeable(DamageSource src) {
        return src.getAttacker() != null || src.isIn(DamageTypeTags.IS_PROJECTILE) || src.isIn(DamageTypeTags.IS_EXPLOSION);
    }

    static boolean inFront(PlayerEntity p, Entity from) {
        Vec3d to = from.getPos().subtract(p.getPos()).multiply(1, 0, 1);
        if (to.lengthSquared() < 0.01) {
            return true;
        }
        return to.normalize().dotProduct(p.getRotationVector().multiply(1, 0, 1).normalize()) > 0.15;
    }

    static boolean allowDamage(LivingEntity target, DamageSource src, float amount) {
        if (!(target instanceof ServerPlayerEntity p) || !cfg().enabled) {
            return true;
        }
        State s = of(p);
        long now = p.getWorld().getTime();
        ServerWorld w = p.getServerWorld();
        if (s.dodgeUntil >= now && dodgeable(src)) {
            Entity a = src.getAttacker();
            if (a != null && a.squaredDistanceTo(p) < 64 && s.openingUntil < now) {
                s.openingUntil = now + 20;
                Advancements.event(p, "combat_perfect_dodge", a.getType().getUntranslatedName());
            }
            w.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 4, 0.3, 0.3, 0.3, 0.01);
            return false;
        }
        Entity direct = src.getSource();
        LivingEntity attacker = src.getAttacker() instanceof LivingEntity l ? l : null;
        if (s.parryUntil >= now && direct != null && !UNPARRYABLE.test(src) && inFront(p, direct)) {
            boolean melee = attacker != null && direct == attacker && attacker.squaredDistanceTo(p) < 49;
            boolean projectile = s.parryShield && direct instanceof ProjectileEntity;
            if (melee || projectile) {
                s.parryUntil = 0;
                s.openingUntil = now + cfg().counterWindowTicks + (WeaponStyle.of(p.getMainHandStack()) == WeaponStyle.SWORD ? 10 : 0);
                s.stamina = Math.min(MAX_STAMINA, s.stamina + 8);
                if (melee) {
                    ON_PARRIED.accept(attacker, p);
                    attacker.takeKnockback(0.5, p.getX() - attacker.getX(), p.getZ() - attacker.getZ());
                }
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getEyeY() - 0.3, p.getZ(), 14, 0.35, 0.25, 0.35, 0.15);
                w.playSound(null, p.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1f, 1.6f);
                w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.25f, 1.9f);
                p.sendMessage(Text.literal("Parried!").formatted(Formatting.GOLD), true);
                Advancements.event(p, "combat_parry", attacker == null ? "projectile" : attacker.getType().getUntranslatedName());
                return false;
            }
        }
        if (s.heavyStage == 1 && amount >= 4 && attacker != null) {
            endWindup(p);
            s.heavyStage = 2;
            s.heavyEnd = now + 8;
            p.sendMessage(Text.literal("Interrupted!").formatted(Formatting.RED), true);
        }
        return true;
    }

    /** Outgoing melee damage of a player (called from the damage mixin after Stage 8 bonuses). */
    public static float outgoing(LivingEntity attacker, LivingEntity target, float amount) {
        if (!(attacker instanceof ServerPlayerEntity p) || !cfg().enabled) {
            return amount;
        }
        State s = of(p);
        long now = p.getWorld().getTime();
        WeaponStyle st = WeaponStyle.of(p.getMainHandStack());
        double mult = 1;
        if (s.executingHeavy) {
            mult *= s.heavyStyle.mult;
        }
        boolean staggered = Poise.staggered(target);
        if (s.inOpening(now)) {
            mult *= st == WeaponStyle.SWORD ? 1.9 : 1.6;
            s.openingUntil = 0;
            pendingRiposte = target;
            if (p.getWorld() instanceof ServerWorld w) {
                w.spawnParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getBodyY(0.6), target.getZ(), 16, 0.3, 0.4, 0.3, 0.3);
                w.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1f, 0.7f);
            }
            Advancements.event(p, "combat_counter", st.name().toLowerCase());
        } else if (staggered) {
            mult *= 1.3;
        }
        if (st == WeaponStyle.DAGGER) {
            Vec3d facing = target.getRotationVector().multiply(1, 0, 1);
            Vec3d from = p.getPos().subtract(target.getPos()).multiply(1, 0, 1);
            boolean behind = facing.lengthSquared() > 0 && from.lengthSquared() > 0 && facing.normalize().dotProduct(from.normalize()) < -0.35;
            if (behind || staggered) {
                mult *= 1.5;
                if (behind) {
                    Advancements.event(p, "combat_backstab", "dagger");
                }
            }
        }
        if (target instanceof dev.underdeep.wildlife.UnderdeepCreature uc && !staggered && Archetype.of(uc) == Archetype.DEFENDER) {
            Vec3d facing = uc.getRotationVector().multiply(1, 0, 1);
            Vec3d from = p.getPos().subtract(uc.getPos()).multiply(1, 0, 1);
            if (facing.lengthSquared() > 0 && from.lengthSquared() > 0 && facing.normalize().dotProduct(from.normalize()) > 0.3) {
                mult *= 0.5; // armoured front: flank it, or break its guard
                if (p.getWorld() instanceof ServerWorld w) {
                    w.playSound(null, uc.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 0.6f, 0.7f);
                }
            }
        }
        int armor = target.getArmor();
        if (st == WeaponStyle.MACE) {
            mult *= 1 + Math.min(0.6, armor * 0.04);
        } else if (st == WeaponStyle.BATTLEAXE) {
            mult *= 1 + Math.min(0.4, armor * 0.03);
        }
        return (float) (amount * mult);
    }

    private static LivingEntity pendingRiposte;

    static void afterDamage(LivingEntity target, DamageSource src, float base, float taken, boolean blocked) {
        if (!cfg().enabled) {
            return;
        }
        if (target instanceof ServerPlayerEntity p && blocked) {
            State s = of(p);
            long now = p.getWorld().getTime();
            float drain = (float) (Math.max(4, base * cfg().shieldStaminaPerDamage) * BLOCK_DRAIN.applyAsDouble(src));
            if (s.stamina < drain) {
                s.stamina = 0;
                p.disableShield();
                p.sendMessage(Text.literal("Guard broken!").formatted(Formatting.RED), true);
            } else {
                spend(s, drain, now);
            }
            return;
        }
        ServerPlayerEntity p;
        WeaponStyle st = WeaponStyle.FIST;
        double poise;
        if (src.getAttacker() instanceof ServerPlayerEntity sp && src.getSource() == sp) {
            p = sp;
            st = WeaponStyle.of(sp.getMainHandStack());
            poise = st.poise * (of(sp).executingHeavy ? 2.5 : 1) * (sp.fallDistance > 0 && !sp.isOnGround() ? 1.3 : 1);
        } else if (src.getAttacker() instanceof ServerPlayerEntity sp && src.getSource() instanceof PersistentProjectileEntity arrow) {
            p = sp;
            WeaponStyle ws = arrow.getWeaponStack() == null ? WeaponStyle.BOW : WeaponStyle.of(arrow.getWeaponStack());
            poise = ws == WeaponStyle.CROSSBOW ? WeaponStyle.CROSSBOW.poise : arrow.isCritical() ? WeaponStyle.BOW.poise : 0.4;
        } else {
            return;
        }
        boolean force = pendingRiposte == target;
        pendingRiposte = null;
        if (cfg().mobStagger && target.isAlive() && Poise.damage(target, poise, p, force)) {
            Advancements.event(p, "combat_stagger", st.name().toLowerCase());
        }
        if (st == WeaponStyle.BATTLEAXE && target instanceof PlayerEntity tp && tp.isBlocking()) {
            tp.disableShield();
        }
    }

    // ---------------------------------------------------------------------------------------------- tick

    static void tick(MinecraftServer server) {
        if (!cfg().enabled) {
            return;
        }
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            State s = of(p);
            long now = p.getWorld().getTime();
            if (p.isOnGround()) {
                s.lastGround = now;
            }
            if (s.heavyStage == 1 && now >= s.heavyHit) {
                executeHeavy(p, s, now);
            } else if (s.heavyStage == 2 && now >= s.heavyEnd) {
                s.heavyStage = 0;
            }
            // shield parry: the first ticks of raising a shield
            boolean shield = p.isUsingItem() && p.getActiveItem().getItem() instanceof ShieldItem;
            if (shield && s.shieldUseStart < 0) {
                s.shieldUseStart = now;
                if (now >= s.parryReady && s.heavyStage == 0) {
                    openParry(p, s, now, true, 0);
                    s.parryReady = now + 14;
                }
            } else if (!shield) {
                s.shieldUseStart = -1;
            }
            if (now - s.lastSpend > 12 && s.stamina < MAX_STAMINA && !shield) {
                double regen = cfg().staminaRegenPerSecond / 20.0;
                var hunger = p.getHungerManager();
                if (hunger.getSaturationLevel() >= 6) {
                    regen *= 1.25; // well fed
                } else if (hunger.getFoodLevel() <= 6) {
                    regen *= 0.5;
                }
                if (p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.HASTE) || p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SPEED)) {
                    regen *= 1.15;
                }
                s.stamina = (float) Math.min(MAX_STAMINA, s.stamina + regen);
            }
            int flags = (s.inOpening(now) ? 1 : 0) | (s.heavyStage == 1 ? 2 : 0) | (s.parryUntil >= now ? 4 : 0) | (s.heavyStage == 2 ? 8 : 0);
            if ((Math.abs(s.stamina - s.sentStamina) >= 1 || s.stamina == MAX_STAMINA && s.sentStamina != MAX_STAMINA || flags != s.sentFlags)
                    && (now % 2 == 0 || flags != s.sentFlags)) {
                s.sentStamina = s.stamina;
                s.sentFlags = flags;
                ServerPlayNetworking.send(p, new Hud(s.stamina, flags));
            }
        }
    }

    static void broadcast(ServerPlayerEntity p, CustomPayload payload) {
        ServerPlayNetworking.send(p, payload);
        for (ServerPlayerEntity o : PlayerLookup.tracking(p)) {
            if (o != p) {
                ServerPlayNetworking.send(o, payload);
            }
        }
    }

    /** For tests and companions: whether an entity is inside its dodge invulnerability. */
    public static boolean dodging(PlayerEntity p) {
        return of(p).dodgeUntil >= p.getWorld().getTime();
    }
}
