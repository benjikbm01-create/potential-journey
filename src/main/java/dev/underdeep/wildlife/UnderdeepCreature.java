package dev.underdeep.wildlife;

import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.wildlife.CreatureSpecies.Locomotion;
import dev.underdeep.wildlife.CreatureSpecies.Mount;
import dev.underdeep.wildlife.CreatureSpecies.TameMethod;
import dev.underdeep.wildlife.CreatureSpecies.Temperament;
import dev.underdeep.wildlife.ai.CreatureGoals;
import dev.underdeep.wildlife.population.WildlifeManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.AquaticMoveControl;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.pathing.AmphibiousSwimNavigation;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.ai.pathing.SpiderNavigation;
import net.minecraft.entity.ai.pathing.SwimNavigation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The single entity class for all Underdeep wildlife. Species data selects navigation, goals,
 * diet, social behaviour, taming and mounting.
 * <p>
 * <b>Ownership rules</b> (see ARCHITECTURE §5d): a <i>wild</i> creature (not tamed, not named, not
 * persistent) belongs to the abstract population of its home region. Wild creatures are never saved
 * with chunks ({@link #shouldSave()} is false): when their chunk unloads or no player is near they
 * simply return to the region's abstract count, and the spawner re-materialises members while the
 * count allows. Deaths and births change the count; taming or naming removes a creature from the
 * wild pool and makes it a normal persistent entity owned by the world.
 */
public class UnderdeepCreature extends TameableEntity {
    public static final byte IDLE = 0, EAT = 1, SLEEP = 2, WARN = 3, ATTACK = 4, PLAY = 5, DRINK = 6, ALERT = 7;

    private static final TrackedData<Byte> STATE = DataTracker.registerData(UnderdeepCreature.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Byte> VARIANT = DataTracker.registerData(UnderdeepCreature.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Boolean> HARNESSED = DataTracker.registerData(UnderdeepCreature.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> SHEARED = DataTracker.registerData(UnderdeepCreature.class, TrackedDataHandlerRegistry.BOOLEAN);

    private final CreatureSpecies species;
    /** Needs in [0,1]: 0 = satisfied. */
    public float hunger, tiredness, loneliness;
    public long homeRegion = Long.MIN_VALUE;
    @Nullable
    public BlockPos home;
    public int herdId;
    public int stateTicks;
    public int huntCooldown;
    public int productTimer = 2400;
    public int trust;
    @Nullable
    public UUID trustingPlayer;
    public int patienceTicks;
    public int lastOfferingAge = -100000;
    public boolean lowDetail;
    private int chaseTicks;
    /** Self-test only (set by the property-gated dev self-test): behave as if a player were adjacent. */
    public static boolean testObserver;
    private double nearestPlayerSq = Double.MAX_VALUE;
    private final SimpleInventory pack;
    private final Personality personality;

    public UnderdeepCreature(EntityType<? extends UnderdeepCreature> type, World world) {
        super(type, world);
        this.species = WildlifeRegistry.of(type);
        this.personality = Personality.of(getUuid());
        this.pack = new SimpleInventory(species != null && species.mountType() == Mount.PACK ? 18 : 0);
        if (species != null) {
            switch (species.locomotion()) {
                case AQUATIC -> this.moveControl = new AquaticMoveControl(this, 85, 10, 0.02f, 0.1f, true);
                case FLYER, HOVER -> {
                    this.moveControl = new FlightMoveControl(this, 20, true);
                    this.setNoGravity(true);
                }
                default -> {
                }
            }
            if (species.fireImmune()) {
                setPathfindingPenalty(PathNodeType.LAVA, species.swimsInLava() ? 0 : 8);
                setPathfindingPenalty(PathNodeType.DANGER_FIRE, 0);
                setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, 0);
            } else {
                setPathfindingPenalty(PathNodeType.DANGER_FIRE, 16);
                setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, -1);
            }
            if (species.isAquatic()) {
                setPathfindingPenalty(PathNodeType.WATER, 0);
            } else if (species.locomotion() == Locomotion.AMPHIBIOUS) {
                setPathfindingPenalty(PathNodeType.WATER, 0);
            }
            this.hunger = 0.3f + random.nextFloat() * 0.3f;
            this.tiredness = random.nextFloat() * 0.5f;
        }
    }

    public CreatureSpecies species() {
        return species;
    }

    public Personality personality() {
        return personality;
    }

    // ------------------------------------------------------------------ data tracker

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(STATE, IDLE);
        builder.add(VARIANT, (byte) 0);
        builder.add(HARNESSED, false);
        builder.add(SHEARED, false);
    }

    public byte state() {
        return dataTracker.get(STATE);
    }

    public void setState(byte s) {
        if (state() != s) {
            dataTracker.set(STATE, s);
            stateTicks = 0;
        }
    }

    public int variant() {
        return dataTracker.get(VARIANT);
    }

    public void setHarnessed(boolean harnessed) {
        dataTracker.set(HARNESSED, harnessed);
    }

    public boolean isHarnessed() {
        return dataTracker.get(HARNESSED);
    }

    public boolean isSheared() {
        return dataTracker.get(SHEARED);
    }

    // ------------------------------------------------------------------ ownership

    /** Wild = part of a regional abstract population (not tamed, not named, not otherwise persistent). */
    public boolean isWild() {
        return !isTamed() && !hasCustomName() && !isPersistent();
    }

    @Override
    public boolean shouldSave() {
        return !isWild() && super.shouldSave();
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // wild creatures return to their population via WildlifeManager (no vanilla random despawn)
    }

    // ------------------------------------------------------------------ navigation & movement

    @Override
    protected EntityNavigation createNavigation(World world) {
        CreatureSpecies s = WildlifeRegistry.of(getType());
        if (s == null) {
            return new MobNavigation(this, world);
        }
        return switch (s.locomotion()) {
            case AQUATIC -> new SwimNavigation(this, world);
            case AMPHIBIOUS -> new AmphibiousSwimNavigation(this, world);
            case FLYER, HOVER -> {
                BirdNavigation n = new BirdNavigation(this, world);
                n.setCanPathThroughDoors(false);
                n.setCanSwim(false);
                yield n;
            }
            case CLIMBER -> new SpiderNavigation(this, world);
            default -> {
                MobNavigation n = new MobNavigation(this, world);
                n.setCanSwim(true);
                yield n;
            }
        };
    }

    @Override
    public boolean isClimbing() {
        Locomotion l = species.locomotion();
        return (l == Locomotion.CLIMBER || l == Locomotion.GLIDER) && horizontalCollision && state() != SLEEP || super.isClimbing();
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        Locomotion l = species.locomotion();
        if (l != Locomotion.LAND && l != Locomotion.AMPHIBIOUS && l != Locomotion.AQUATIC) {
            return false;
        }
        // sure-footed: large animals and herd animals take reduced fall damage
        return super.handleFallDamage(fallDistance, damageMultiplier * 0.5f, damageSource);
    }

    @Override
    public boolean canWalkOnFluid(FluidState state) {
        return species.swimsInLava() && state.isIn(FluidTags.LAVA) || super.canWalkOnFluid(state);
    }

    @Override
    public void travel(Vec3d movementInput) {
        if (species.isAquatic() && isLogicalSideForUpdatingMovement() && isTouchingWater()) {
            updateVelocity(getMovementSpeed() * (hasControllingPassenger() ? 1.0f : 0.1f), movementInput);
            move(net.minecraft.entity.MovementType.SELF, getVelocity());
            setVelocity(getVelocity().multiply(0.9));
            if (getTarget() == null && !hasControllingPassenger()) {
                setVelocity(getVelocity().add(0, -0.003, 0));
            }
            return;
        }
        super.travel(movementInput);
    }

    // ------------------------------------------------------------------ goals

    @Override
    protected void initGoals() {
        CreatureSpecies s = WildlifeRegistry.of(getType());
        if (s != null) {
            CreatureGoals.install(this, s, goalSelector, targetSelector);
        }
    }

    public boolean isBreedingItem(ItemStack stack) {
        return CreatureGoals.isFood(species, stack);
    }

    @Nullable
    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity mate) {
        UnderdeepCreature child = WildlifeRegistry.type(species.id).create(world);
        if (child != null && mate instanceof UnderdeepCreature m && isTamed() && m.isTamed() && getOwnerUuid() != null) {
            child.setOwnerUuid(getOwnerUuid());
            child.setTamed(true, true);
        }
        return child;
    }

    @Override
    public void breed(ServerWorld world, AnimalEntity other) {
        if (WildlifeManager.get(world) != null && !WildlifeManager.get(world).allowBirth(this)) {
            setBreedingAge(6000);
            other.setBreedingAge(6000);
            resetLoveTicks();
            other.resetLoveTicks();
            return;
        }
        if (species.laysEggs() && WildlifeManager.layEgg(world, this)) {
            setBreedingAge(6000);
            other.setBreedingAge(6000);
            resetLoveTicks();
            other.resetLoveTicks();
            return;
        }
        super.breed(world, other);
    }

    @Override
    public void breed(ServerWorld world, AnimalEntity other, @Nullable PassiveEntity baby) {
        super.breed(world, other, baby);
        if (baby instanceof UnderdeepCreature b && WildlifeManager.get(world) != null) {
            WildlifeManager.get(world).onBirth(b, this);
        }
    }

    /** Ownership by UUID: works while the owner entity lookup is unavailable (other dimension, fake players). */
    public boolean ownedBy(PlayerEntity player) {
        return isTamed() && player.getUuid().equals(getOwnerUuid());
    }

    /** Diagnostics: running goals, state and position (used by the self-test). */
    public String debugSummary() {
        StringBuilder b = new StringBuilder("state=").append(state()).append(" goals=[");
        goalSelector.getGoals().stream().filter(g -> g.isRunning()).forEach(g -> b.append(g.getGoal().getClass().getSimpleName()).append(' '));
        return b.append("] target=").append(getTarget() == null ? "-" : getTarget().getType().getUntranslatedName())
                .append(String.format(java.util.Locale.ROOT, " pos=%.1f,%.1f,%.1f ground=%s water=%s tired=%.2f hurt=%d nav=%s",
                        getX(), getY(), getZ(), isOnGround(), isTouchingWater(), tiredness, hurtTime,
                        getNavigation().isIdle() ? "idle" : "moving")).toString();
    }

    /** No endless chases: a wild animal that cannot close in on its target within ~12 s gives up. */
    private void limitChase() {
        LivingEntity t = getTarget();
        if (t == null || isTamed()) {
            chaseTicks = 0;
            return;
        }
        if (squaredDistanceTo(t) < 6.25) {
            chaseTicks = 0;
        } else if (++chaseTicks > 240) {
            chaseTicks = 0;
            setTarget(null);
            huntCooldown = Math.max(huntCooldown, 600);
            if (state() == ATTACK || state() == WARN) {
                setState(IDLE);
            }
        }
    }

    @Override
    public boolean onKilledOther(ServerWorld world, LivingEntity other) {
        if (other instanceof UnderdeepCreature prey && species.preyIds().contains(prey.species().id)) {
            hunger = 0.05f; // a successful hunt satiates the predator, which then rests
            tiredness = Math.min(1, tiredness + 0.45f);
            huntCooldown = 2400 + random.nextInt(1200);
        }
        return super.onKilledOther(world, other);
    }

    @Override
    public boolean canBreedWith(AnimalEntity other) {
        return other instanceof UnderdeepCreature o && o.species == species && super.canBreedWith(other);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    protected void mobTick() {
        super.mobTick();
        stateTicks++;
        if (huntCooldown > 0) {
            huntCooldown--;
        }
        limitChase();
        if ((age + getId()) % 20 == 0 && getWorld() instanceof ServerWorld sw) {
            updateNeeds(sw);
        }
    }

    private void updateNeeds(ServerWorld world) {
        UnderdeepConfig c = UnderdeepConfig.get();
        PlayerEntity p = world.getClosestPlayer(this, 160);
        nearestPlayerSq = testObserver ? 0 : p == null ? Double.MAX_VALUE : squaredDistanceTo(p);
        lowDetail = nearestPlayerSq > (double) c.wildlifeDetailDistance * c.wildlifeDetailDistance;
        if (isWild() && nearestPlayerSq > (double) c.wildlifeDespawnDistance * c.wildlifeDespawnDistance && !hasPassengers()) {
            discard(); // returns to the regional population (WildlifeManager counts it on unload)
            return;
        }
        float rate = species.isPredator() ? 0.004f : 0.006f;
        hunger = Math.min(1, hunger + rate);
        boolean activeTime = Rhythms.isActive(species.activity(), world.getTimeOfDay(), getId());
        tiredness = MathHelper.clamp(tiredness + (activeTime ? 0.003f : 0.012f) - (state() == SLEEP ? 0.03f : 0), 0, 1);
        if (species.social() != CreatureSpecies.Social.SOLITARY) {
            loneliness = Math.min(1, loneliness + 0.004f * (0.5f + personality.sociability()));
        }
        // species-specific upkeep
        if (species.isAquatic() && !isTouchingWater()) {
            setAir(getAir() - 20);
            if (getAir() <= -20) {
                setAir(0);
                damage(getDamageSources().dryOut(), 2.0f);
            }
        } else if (species.isAquatic()) {
            setAir(getMaxAir());
        }
        if (hasControllingPassenger() && getControllingPassenger() instanceof PlayerEntity rider && species.mountType() == Mount.SWIMMER) {
            rider.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 220, 0, true, false));
        }
        if (isSheared() && random.nextInt(300) == 0) {
            dataTracker.set(SHEARED, false);
        }
        WildlifeManager wm = WildlifeManager.get(world);
        if (wm != null) {
            wm.tickCreature(this);
        }
        if (!lowDetail) {
            WildlifeManager.tickTaming(this);
            if (species.product().startsWith("shed:") && !isBaby() && --productTimer <= 0) {
                productTimer = 1800 + random.nextInt(2400);
                if (nearestPlayerSq < 48 * 48) {
                    Item item = Registries.ITEM.get(Identifier.of("underdeep", species.product().substring(5)));
                    dropStack(new ItemStack(item));
                }
            }
        }
    }

    /**
     * Fed from a feeding trough (Stage 7, tamed animals only): satisfies hunger and keeps the animal productive.
     * Shedding products come sooner and sheared coats regrow sooner, within the normal product bounds (never more
     * than one product per feeding). Returns false if the animal was not hungry enough to eat.
     */
    public boolean troughFed() {
        if (!isTamed() || hunger < 0.35f) {
            return false;
        }
        hunger = Math.max(0, hunger - 0.6f);
        if (species.product().startsWith("shed:")) {
            productTimer = Math.max(1, productTimer - 600);
        }
        if (isSheared() && random.nextInt(4) == 0) {
            dataTracker.set(SHEARED, false);
        }
        return true;
    }

    public double nearestPlayerDistanceSq() {
        return nearestPlayerSq;
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        if (species.locomotion() == Locomotion.GLIDER && !isOnGround() && !isTouchingWater()) {
            Vec3d v = getVelocity();
            if (v.y < -0.1) {
                Vec3d look = getRotationVector();
                setVelocity(v.x + look.x * 0.02, -0.1, v.z + look.z * 0.02);
                fallDistance = 0;
            }
        }
        if (state() == SLEEP || isSitting()) {
            getNavigation().stop();
        }
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        World w = getWorld();
        // renewable products
        if (species.product().equals("milk") && stack.isOf(Items.BUCKET) && !isBaby()) {
            player.playSound(SoundEvents.ENTITY_COW_MILK, 1.0f, 1.0f);
            ItemStack milk = net.minecraft.item.ItemUsage.exchangeStack(stack, player, Items.MILK_BUCKET.getDefaultStack());
            player.setStackInHand(hand, milk);
            return ActionResult.success(w.isClient);
        }
        if (species.product().startsWith("shear:") && stack.isOf(Items.SHEARS) && !isSheared() && !isBaby()) {
            if (!w.isClient) {
                dataTracker.set(SHEARED, true);
                Item item = Registries.ITEM.get(Identifier.of("underdeep", species.product().substring(6)));
                dropStack(new ItemStack(item, 1 + random.nextInt(3)));
                w.playSoundFromEntity(null, this, SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 1.0f, 1.0f);
                stack.damage(1, player, getSlotForHand(hand));
            }
            return ActionResult.success(w.isClient);
        }
        // harness for mounts
        if (species.mountType() != Mount.NONE && isTamed() && ownedBy(player) && !isBaby() && !isHarnessed()
                && stack.isOf(Registries.ITEM.get(Identifier.of("underdeep", "harness")))) {
            if (!w.isClient) {
                dataTracker.set(HARNESSED, true);
                stack.decrementUnlessCreative(1, player);
                w.playSoundFromEntity(null, this, SoundEvents.ENTITY_HORSE_SADDLE, SoundCategory.NEUTRAL, 1.0f, 1.0f);
            }
            return ActionResult.success(w.isClient);
        }
        if (isTamed() && ownedBy(player)) {
            if (player.isSneaking() && species.mountType() == Mount.PACK && pack.size() > 0) {
                if (!w.isClient) {
                    player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                            (syncId, inv, p) -> new GenericContainerScreenHandler(net.minecraft.screen.ScreenHandlerType.GENERIC_9X2, syncId, inv, pack, 2), getDisplayName()));
                }
                return ActionResult.success(w.isClient);
            }
            if (stack.isEmpty() && isHarnessed() && !player.isSneaking() && !hasPassengers() && !isBaby()) {
                if (!w.isClient) {
                    setSitting(false);
                    player.startRiding(this);
                    if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                        dev.underdeep.guide.Advancements.event(sp, "rode", species.id);
                    }
                }
                return ActionResult.success(w.isClient);
            }
            if (stack.isEmpty() && !isBreedingItem(stack)) {
                if (!w.isClient) {
                    setSitting(!isSitting());
                    player.sendMessage(Text.translatable(isSitting() ? "message.underdeep.creature.stay" : "message.underdeep.creature.follow",
                            getDisplayName()), true);
                }
                return ActionResult.success(w.isClient);
            }
        }
        if (!isTamed() && species.tamable() && UnderdeepConfig.get().tamingEnabled && !w.isClient && WildlifeManager.tryTame(this, player, stack)) {
            return ActionResult.SUCCESS;
        }
        return super.interactMob(player, hand);
    }

    // ------------------------------------------------------------------ riding

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (isHarnessed() && getFirstPassenger() instanceof PlayerEntity p && ownedBy(p)) {
            return p;
        }
        return null;
    }

    @Override
    protected void tickControlled(PlayerEntity rider, Vec3d movementInput) {
        super.tickControlled(rider, movementInput);
        setRotation(rider.getYaw(), rider.getPitch() * 0.5f);
        prevYaw = bodyYaw = headYaw = getYaw();
        if (species.mountType() == Mount.SWIMMER && isTouchingWater() && rider.forwardSpeed > 0) {
            double up = -Math.sin(Math.toRadians(rider.getPitch())) * 0.08;
            setVelocity(getVelocity().add(0, up, 0));
        }
        if (species.mountType() == Mount.GLIDER && !isOnGround() && getVelocity().y < -0.08) {
            Vec3d look = rider.getRotationVector();
            setVelocity(getVelocity().x + look.x * 0.04, -0.08, getVelocity().z + look.z * 0.04);
            fallDistance = 0;
        }
    }

    @Override
    protected Vec3d getControlledMovementInput(PlayerEntity rider, Vec3d movementInput) {
        float side = rider.sidewaysSpeed * 0.5f;
        float fwd = rider.forwardSpeed;
        if (fwd <= 0) {
            fwd *= 0.25f;
        }
        return new Vec3d(side, 0, fwd);
    }

    @Override
    protected float getSaddledSpeed(PlayerEntity rider) {
        float base = (float) getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        return switch (species.mountType()) {
            case RUNNER -> base * 1.25f;
            case PACK -> base * 0.95f;
            case SWIMMER -> isTouchingWater() ? base * 0.5f : base * 0.15f;
            case CLIMBER -> base * 1.0f;
            case GLIDER -> base * 1.05f;
            default -> base;
        };
    }

    @Override
    public boolean isPushedByFluids() {
        return !species.isAquatic();
    }

    // ------------------------------------------------------------------ damage & death

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isTamed() && source.getAttacker() instanceof PlayerEntity p && ownedBy(p) && !p.isSneaking()) {
            return false; // accidental friendly fire from the owner
        }
        if (hasPassengers() && source.getAttacker() == getFirstPassenger()) {
            return false;
        }
        boolean hurt = super.damage(source, amount);
        if (hurt && !getWorld().isClient) {
            if (state() == SLEEP) {
                setState(ALERT);
            }
            if (source.getAttacker() instanceof PlayerEntity p && trustingPlayer != null && trustingPlayer.equals(p.getUuid())) {
                trust = 0;
                trustingPlayer = null;
            }
            WildlifeManager.alarmHerd(this, source.getAttacker());
            if (species.id.equals("static_beetle") && source.getAttacker() instanceof LivingEntity att && squaredDistanceTo(att) < 9) {
                att.damage(getDamageSources().indirectMagic(this, this), 2.0f); // defensive static discharge
                ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.4, getZ(), 12, 0.4, 0.3, 0.4, 0.05);
            }
        }
        return hurt;
    }

    @Override
    public void onDeath(DamageSource source) {
        if (!getWorld().isClient && getWorld() instanceof ServerWorld sw) {
            WildlifeManager wm = WildlifeManager.get(sw);
            if (wm != null) {
                wm.onDeath(this, source);
            }
            if (pack.size() > 0) {
                for (int i = 0; i < pack.size(); i++) {
                    dropStack(pack.getStack(i));
                }
                pack.clear();
            }
        }
        super.onDeath(source);
    }

    @Override
    public void setCustomName(@Nullable Text name) {
        boolean wasWild = isWild();
        super.setCustomName(name);
        if (wasWild && !isWild() && getWorld() instanceof ServerWorld sw && WildlifeManager.get(sw) != null) {
            WildlifeManager.get(sw).onLeaveWild(this);
        }
    }

    public void leaveWildAsTamed(PlayerEntity owner) {
        boolean wasWild = isWild();
        setOwner(owner);
        setTamed(true, true);
        setPersistent();
        if (wasWild && getWorld() instanceof ServerWorld sw && WildlifeManager.get(sw) != null) {
            WildlifeManager.get(sw).onLeaveWild(this);
        }
        getWorld().sendEntityStatus(this, (byte) 7);
        if (owner instanceof ServerPlayerEntity sp) {
            sp.sendMessage(Text.translatable("message.underdeep.creature.tamed", getDisplayName()), true);
        }
    }

    // ------------------------------------------------------------------ sounds

    @Override
    protected SoundEvent getAmbientSound() {
        return state() == SLEEP ? null : Voices.ambient(species);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Voices.hurt(species);
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Voices.death(species);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (!species.isAquatic() && !species.flies() && species.sizeClass() > 0.8f) {
            playSound(Voices.step(species), 0.15f * species.sizeClass(), 1.0f);
        }
    }

    @Override
    public float getSoundPitch() {
        return species.voicePitch() * (isBaby() ? 1.4f : 1.0f) * (0.92f + random.nextFloat() * 0.16f);
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 240 + (int) (species.sizeClass() * 120);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putFloat("UdHunger", hunger);
        nbt.putFloat("UdTired", tiredness);
        nbt.putLong("UdHomeRegion", homeRegion);
        if (home != null) {
            nbt.putLong("UdHome", home.asLong());
        }
        nbt.putBoolean("UdHarness", isHarnessed());
        nbt.putBoolean("UdSheared", isSheared());
        nbt.putByte("UdVariant", (byte) variant());
        if (pack.size() > 0) {
            nbt.put("UdPack", pack.toNbtList(getRegistryManager()));
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        hunger = nbt.getFloat("UdHunger");
        tiredness = nbt.getFloat("UdTired");
        if (nbt.contains("UdHomeRegion")) {
            homeRegion = nbt.getLong("UdHomeRegion");
        }
        if (nbt.contains("UdHome")) {
            home = BlockPos.fromLong(nbt.getLong("UdHome"));
        }
        dataTracker.set(HARNESSED, nbt.getBoolean("UdHarness"));
        dataTracker.set(SHEARED, nbt.getBoolean("UdSheared"));
        dataTracker.set(VARIANT, nbt.getByte("UdVariant"));
        if (nbt.contains("UdPack") && pack.size() > 0) {
            pack.readNbtList(nbt.getList("UdPack", 10), getRegistryManager());
        }
    }

    public void initWild(long region, BlockPos homePos, int herd) {
        this.homeRegion = region;
        this.home = homePos;
        this.herdId = herd;
        dataTracker.set(VARIANT, (byte) random.nextInt(4));
    }

    public boolean isAggressiveTo(LivingEntity target) {
        Temperament t = species.temperament();
        return t == Temperament.AMBUSH || t == Temperament.TERRITORIAL;
    }

    @Override
    public boolean canBeLeashed() {
        return isTamed() || !isWild();
    }
}
