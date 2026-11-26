package dev.underdeep.civ.npc;

import dev.underdeep.civ.Civilization;
import dev.underdeep.civ.mind.Activity;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LongDoorInteractGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtCustomerGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.StopFollowingCustomerGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A registered inhabitant of a settlement (Stage 4). The entity is a temporary embodiment of a persistent
 * {@link SettlementRecord.InhabitantRecord}: it is never saved with the chunk; {@link InhabitantManager}
 * materialises it near players and removes it when they leave, so identities, deaths and trades live in
 * the record (no duplicates, no resurrection).
 */
public class InhabitantEntity extends MerchantEntity {
    private static final TrackedData<Integer> LOOK = DataTracker.registerData(InhabitantEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<String> PROFESSION = DataTracker.registerData(InhabitantEntity.class, TrackedDataHandlerRegistry.STRING);

    public long settlementId;
    public int recordIndex = -1;
    @Nullable public BlockPos bed, work, gather, home;
    long lookSeed;
    private int idleTicks;

    public InhabitantEntity(EntityType<? extends MerchantEntity> type, World world) {
        super(type, world);
        if (getNavigation() instanceof MobNavigation nav) {
            nav.setCanPathThroughDoors(true);
            nav.setCanEnterOpenDoors(true);
        }
        setCanPickUpLoot(false);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 20).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(LOOK, 0);
        builder.add(PROFESSION, "merchant");
    }

    // ------------------------------------------------------------------ identity

    /** Binds this entity to its record (called by the manager before spawning). */
    public void bind(SettlementRecord settlement, SettlementRecord.InhabitantRecord r, @Nullable BlockPos gatherPoint, @Nullable BlockPos doorstep) {
        settlementId = settlement.id;
        recordIndex = r.index;
        bed = r.bed;
        work = r.work;
        home = doorstep != null ? doorstep : r.bed;
        gather = gatherPoint;
        lookSeed = r.appearance;
        int variant = (int) Math.floorMod(r.appearance, 6L);
        dataTracker.set(LOOK, settlement.civ.ordinal() | variant << 4);
        dataTracker.set(PROFESSION, r.profession);
        setCustomName(Text.literal(r.name));
        setCustomNameVisible(false);
        if (isGuard()) {
            equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            setEquipmentDropChance(net.minecraft.entity.EquipmentSlot.MAINHAND, 0);
        }
    }

    public Civilization civ() {
        int c = dataTracker.get(LOOK) & 15;
        return Civilization.values()[Math.min(c, Civilization.values().length - 1)];
    }

    public int skinVariant() {
        return dataTracker.get(LOOK) >> 4 & 15;
    }

    public String profession() {
        return dataTracker.get(PROFESSION);
    }

    public boolean isGuard() {
        return profession().equals("guard");
    }

    /** Development exhibits (architecture gallery line-ups) are saved; real inhabitants never are. */
    public boolean exhibit;

    /** Appearance without a settlement record (gallery exhibits). */
    public void dress(Civilization civ, int variant, String profession, String name) {
        dataTracker.set(LOOK, civ.ordinal() | (variant & 15) << 4);
        dataTracker.set(PROFESSION, profession);
        setCustomName(Text.literal(name));
        if (profession.equals("guard")) {
            equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        }
    }

    @Override
    public boolean shouldSave() {
        return exhibit && super.shouldSave(); // record-backed otherwise; see InhabitantManager
    }

    @Override
    public void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (exhibit) {
            nbt.putBoolean("UdExhibit", true);
            nbt.putInt("UdLook", dataTracker.get(LOOK));
            nbt.putString("UdProfession", dataTracker.get(PROFESSION));
        }
    }

    @Override
    public void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        exhibit = nbt.getBoolean("UdExhibit");
        if (exhibit) {
            dataTracker.set(LOOK, nbt.getInt("UdLook"));
            dataTracker.set(PROFESSION, nbt.getString("UdProfession"));
        }
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    // ------------------------------------------------------------------ AI

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new StopFollowingCustomerGoal(this));
        goalSelector.add(1, new FleeEntityGoal<>(this, HostileEntity.class, 10, 0.6, 0.75, e -> !isGuard() && !defending));
        goalSelector.add(1, new FleeEntityGoal<>(this, dev.underdeep.wildlife.UnderdeepCreature.class, 9, 0.6, 0.75,
                e -> !isGuard() && !defending && e instanceof dev.underdeep.wildlife.UnderdeepCreature c && !c.isTamed()
                        && c.species().temperament() == dev.underdeep.wildlife.CreatureSpecies.Temperament.AMBUSH));
        goalSelector.add(2, new MeleeAttackGoal(this, 0.8, true) {
            @Override
            public boolean canStart() {
                return (isGuard() || defending) && super.canStart();
            }
        });
        goalSelector.add(2, new LookAtCustomerGoal(this));
        goalSelector.add(3, new LongDoorInteractGoal(this, true));
        goalSelector.add(3, new CompanionGoal(this));
        goalSelector.add(3, new CaravanGoal(this));
        goalSelector.add(4, new MindGoal(this));
        goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 6));
        goalSelector.add(9, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this) {
            @Override
            public boolean canStart() {
                return (isGuard() || defending) && super.canStart();
            }
        });
        targetSelector.add(2, new ActiveTargetGoal<>(this, HostileEntity.class, 10, true, false, e -> isGuard()) {
            @Override
            public boolean canStart() {
                return isGuard() && super.canStart();
            }
        });
    }

    @Override
    public boolean tryAttack(net.minecraft.entity.Entity target) {
        swingHand(Hand.MAIN_HAND);
        return super.tryAttack(target);
    }

    // ---- Stage 5: state set by the society simulation each "thought" (once per second)
    /** Where the current activity happens and what it is. */
    @Nullable public BlockPos goalPos;
    public Activity goalActivity = Activity.STROLL;
    @Nullable public LivingEntity danger;
    public boolean defending;
    /** Stage 6: shipment this body travels with as a caravan member (0 = none) and the current market offers. */
    public long caravan;
    public final java.util.List<dev.underdeep.civ.econ.Market.Line> marketLines = new java.util.ArrayList<>();
    /** Companion state (set each thought): the owner while travelling, and where to wait. */
    @Nullable public PlayerEntity companionOwner;
    @Nullable public BlockPos waitPos;
    /** Conversation: face the partner and pause until this age. */
    int talkUntil;
    @Nullable net.minecraft.entity.Entity talkPartner;

    public void talkTo(net.minecraft.entity.Entity other, int ticks) {
        talkPartner = other;
        talkUntil = age + ticks;
        getNavigation().stop();
    }

    /** The current activity (from the inhabitant's mind). */
    public Activity current() {
        return goalActivity;
    }

    private static boolean sleeps(Activity a) {
        return a == Activity.SLEEP || a == Activity.NAP || a == Activity.RECOVER;
    }

    /** Caravan members walk the shipment's route; guards and the rest follow the trader. */
    static final class CaravanGoal extends Goal {
        private final InhabitantEntity e;
        private int repath;

        CaravanGoal(InhabitantEntity e) {
            this.e = e;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return e.caravan != 0 && e.getTarget() == null && e.getCustomer() == null;
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void tick() {
            if (e.talkUntil > e.age && e.talkPartner != null) {
                e.getNavigation().stop();
                e.getLookControl().lookAt(e.talkPartner, 30, 30);
                return;
            }
            if (--repath > 0 && !e.getNavigation().isIdle()) {
                return;
            }
            repath = 20;
            BlockPos to = dev.underdeep.civ.econ.Caravans.waypoint(e);
            if (to != null) {
                e.getNavigation().startMovingTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0.55);
            }
        }
    }

    /** Companion movement: follow / stay close / keep distance / wait / rest, with recovery when stuck. */
    static final class CompanionGoal extends Goal {
        private final InhabitantEntity e;
        private int repath, stuck;

        CompanionGoal(InhabitantEntity e) {
            this.e = e;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        private Companions.Mode mode() {
            SettlementRecord.InhabitantRecord r = InhabitantManager.record(e);
            return r == null || r.mind == null ? Companions.Mode.HOME : Companions.mode(r.mind);
        }

        @Override
        public boolean canStart() {
            return e.companionOwner != null && e.companionOwner.isAlive() && e.getTarget() == null && mode() != Companions.Mode.HOME;
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void tick() {
            PlayerEntity o = e.companionOwner;
            Companions.Mode mode = mode();
            if (e.isSleeping()) {
                e.wakeUp();
            }
            if (mode == Companions.Mode.REST || mode == Companions.Mode.WAIT) {
                e.getNavigation().stop();
                if (mode == Companions.Mode.WAIT && e.waitPos != null && e.squaredDistanceTo(Vec3d.ofBottomCenter(e.waitPos)) > 9) {
                    e.getNavigation().startMovingTo(e.waitPos.getX() + 0.5, e.waitPos.getY(), e.waitPos.getZ() + 0.5, 0.55);
                }
                if (e.squaredDistanceTo(o) < 100) {
                    e.getLookControl().lookAt(o, 20, 20);
                }
                return;
            }
            double want = mode == Companions.Mode.CLOSE ? 2.5 : mode == Companions.Mode.DISTANCE ? 7 : 4;
            double d = e.distanceTo(o);
            double dy = Math.abs(e.getY() - o.getY());
            if (d > dev.underdeep.config.UnderdeepConfig.get().companionRelocateDistance || (stuck > 60 && d > 10) || (stuck > 40 && dy > 6)) {
                if (Companions.relocate(e, o)) {
                    stuck = 0;
                    return;
                }
            }
            if (d > want + 1) {
                if (--repath <= 0 || e.getNavigation().isIdle()) {
                    repath = 10;
                    boolean ok = e.getNavigation().startMovingTo(o, d > 12 ? 0.75 : 0.62);
                    if (!ok) {
                        stuck += 10;
                    }
                }
                if (e.getNavigation().isIdle() || e.getVelocity().horizontalLengthSquared() < 1.0E-4) {
                    stuck++;
                } else {
                    stuck = Math.max(0, stuck - 1);
                }
            } else {
                stuck = 0;
                if (d < want - 1.5 && mode == Companions.Mode.DISTANCE) {
                    Vec3d away = e.getPos().subtract(o.getPos()).normalize().multiply(3);
                    e.getNavigation().startMovingTo(e.getX() + away.x, e.getY(), e.getZ() + away.z, 0.5);
                } else {
                    e.getNavigation().stop();
                }
                e.getLookControl().lookAt(o, 20, 20);
            }
        }
    }

    /** Walks to where the mind's chosen activity happens and performs it there. */
    static final class MindGoal extends Goal {
        private final InhabitantEntity e;
        private BlockPos target;
        private int repath, stuck, workTimer;

        MindGoal(InhabitantEntity e) {
            this.e = e;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return !e.exhibit && e.getCustomer() == null && e.getTarget() == null && e.companionOwner == null && e.caravan == 0;
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void stop() {
            if (e.isSleeping() && !sleeps(e.goalActivity)) {
                e.wakeUp();
            }
        }

        @Override
        public void tick() {
            Activity a = e.goalActivity;
            if (e.isSleeping() && !sleeps(a)) {
                e.wakeUp();
            }
            if (e.isSleeping()) {
                return;
            }
            if (e.talkUntil > e.age && e.talkPartner != null && e.talkPartner.isAlive()) {
                e.getNavigation().stop();
                e.getLookControl().lookAt(e.talkPartner, 30, 30);
                return;
            }
            BlockPos want = e.goalPos;
            if (want == null) {
                return;
            }
            if (!want.equals(target)) {
                target = want;
                repath = 0;
                stuck = 0;
            }
            double speed = a.emergency() ? 0.75 : a == Activity.STROLL || a.cat == Activity.Cat.LEISURE ? 0.45 : 0.55;
            double d2 = e.squaredDistanceTo(Vec3d.ofBottomCenter(target));
            double near = a.place == Activity.Place.PLAZA || a.place == Activity.Place.MARKET || a.place == Activity.Place.TAVERN ? 3.5 : 2.6;
            if (d2 > near * near) {
                if (--repath <= 0 || e.getNavigation().isIdle()) {
                    repath = 40;
                    boolean ok = e.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
                    if (!ok) {
                        stuck += 40;
                    }
                }
                if (++stuck > 900 && e.getWorld().getClosestPlayer(e, 24) == null) {
                    // unobserved and stuck: walk "around the corner" (avoids permanently trapped residents)
                    e.refreshPositionAndAngles(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, e.getYaw(), 0);
                    stuck = 0;
                }
                return;
            }
            stuck = 0;
            if (sleeps(a)) {
                BlockState s = e.getWorld().getBlockState(target);
                if (s.getBlock() instanceof BedBlock && !s.get(BedBlock.OCCUPIED)) {
                    e.getNavigation().stop();
                    e.sleep(target);
                }
                return;
            }
            if (a.cat == Activity.Cat.WORK && a != Activity.PATROL && a != Activity.GATHER_HERBS || a == Activity.TEACH_APPRENTICE) {
                e.getNavigation().stop();
                e.getLookControl().lookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
                if (++workTimer % 50 == 0) {
                    e.swingHand(Hand.MAIN_HAND);
                    e.workEffect(target);
                }
                return;
            }
            if ((a == Activity.EAT_HOME || a == Activity.EAT_TAVERN || a == Activity.SHARE_MEAL) && ++workTimer % 60 == 0) {
                e.playSound(SoundEvents.ENTITY_GENERIC_EAT, 0.5f, 1.0f);
            }
            if (a == Activity.WATCH_WILDLIFE && e.danger == null) {
                net.minecraft.entity.Entity w = e.getWorld().getClosestEntity(dev.underdeep.wildlife.UnderdeepCreature.class,
                        net.minecraft.entity.ai.TargetPredicate.DEFAULT, e, e.getX(), e.getY(), e.getZ(), e.getBoundingBox().expand(16));
                if (w != null) {
                    e.getLookControl().lookAt(w, 20, 20);
                }
            }
            if (a == Activity.TAKE_SHELTER || a == Activity.READ || a == Activity.SIT_AND_REST || a == Activity.STUDY) {
                e.getNavigation().stop();
                return;
            }
            int range = a == Activity.STROLL || a == Activity.EXPLORE_NEARBY || a == Activity.PATROL ? 8 : 3;
            if (e.getNavigation().isIdle() && e.getRandom().nextInt(a == Activity.STROLL ? 30 : 80) == 0) {
                BlockPos p = target.add(e.getRandom().nextInt(range * 2 + 1) - range, 0, e.getRandom().nextInt(range * 2 + 1) - range);
                e.getNavigation().startMovingTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.45);
            }
        }
    }

    /** Visible and audible work at a station; farmers tend crops around their field marker. */
    void workEffect(BlockPos at) {
        if (!(getWorld() instanceof ServerWorld sw)) {
            return;
        }
        String p = profession();
        if (p.equals("farmer")) {
            for (BlockPos q : BlockPos.iterate(at.add(-4, -1, -4), at.add(4, 1, 4))) {
                BlockState s = sw.getBlockState(q);
                if (s.getBlock() instanceof CropBlock crop && crop.isMature(s) && getRandom().nextInt(4) == 0) {
                    sw.setBlockState(q, crop.withAge(0)); // harvest and replant (the produce goes to the settlement)
                    sw.playSound(null, q, SoundEvents.ITEM_CROP_PLANT, getSoundCategory(), 0.6f, 1.0f);
                    return;
                }
            }
            return;
        }
        SoundEvent sound = switch (p) {
            case "blacksmith", "smelter", "engineer" -> SoundEvents.BLOCK_ANVIL_USE;
            case "carpenter", "boatwright", "forester" -> SoundEvents.UI_STONECUTTER_TAKE_RESULT;
            case "tailor" -> SoundEvents.UI_LOOM_TAKE_RESULT;
            case "scholar", "cartographer", "navigator", "astronomer" -> SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT;
            case "alchemist", "healer", "brewer" -> SoundEvents.BLOCK_BREWING_STAND_BREW;
            case "stonemason", "gemcutter", "artisan" -> SoundEvents.UI_STONECUTTER_TAKE_RESULT;
            case "cook", "baker", "innkeeper" -> SoundEvents.BLOCK_SMOKER_SMOKE;
            default -> null;
        };
        if (sound != null && getRandom().nextInt(3) == 0) {
            sw.playSound(null, at, sound, getSoundCategory(), 0.35f, 0.9f + getRandom().nextFloat() * 0.2f);
        }
    }

    // ------------------------------------------------------------------ trading

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!isAlive() || hasCustomer() || isSleeping() || exhibit) {
            return super.interactMob(player, hand);
        }
        if (!getWorld().isClient && player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
            if (player.isSneaking()) {
                // quick trade (sneak + use) without conversation
                InhabitantManager.onTalk(this, player);
                TradeOfferList offers = getOffers();
                if (offers.isEmpty()) {
                    player.sendMessage(Text.translatable("message.underdeep.inhabitant.busy", getName()), true);
                } else {
                    setCustomer(player);
                    sendOffers(player, getDisplayName(), 1);
                }
            } else {
                Dialogue.open(sp, this);
            }
        }
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    protected void fillRecipes() {
        TradeOfferList econ = getWorld().isClient ? null : dev.underdeep.civ.econ.EconHooks.offers(this, getCustomer());
        if (econ != null) {
            this.offers = econ; // Stage 6: real shelf stock at current prices
            return;
        }
        TradeOfferList list = TradeTables.offers(civ(), profession(), lookSeed);
        InhabitantManager.applyUses(this, list);
        this.offers = list;
    }

    @Override
    public void setCustomer(@Nullable PlayerEntity customer) {
        if (customer != null && !getWorld().isClient) {
            this.offers = null; // rebuild offers for each customer from the current shelf and prices
        }
        super.setCustomer(customer);
    }

    @Override
    protected void afterUsing(TradeOffer offer) {
        if (!dev.underdeep.civ.econ.EconHooks.traded(this, offer)) {
            InhabitantManager.recordTrade(this, offer);
        }
    }

    @Override
    public boolean isLeveledMerchant() {
        return false;
    }

    @Nullable
    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    // ------------------------------------------------------------------ life

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean hurt = super.damage(source, amount);
        if (hurt && !getWorld().isClient && source.getAttacker() instanceof LivingEntity attacker && !exhibit) {
            InhabitantManager.alarm(this, attacker);
        }
        return hurt;
    }

    @Override
    protected void applyDamage(DamageSource source, float amount) {
        super.applyDamage(source, amount);
        if (getHealth() <= 0 && !getWorld().isClient && !dev.underdeep.config.UnderdeepConfig.get().companionPermanentDeath) {
            SettlementRecord.InhabitantRecord r = InhabitantManager.record(this);
            if (r != null && r.mind != null && r.mind.companionOf != null) {
                setHealth(1f); // defeated, not killed: the companion withdraws to recover
                Companions.incapacitate(this);
            }
        }
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (getWorld() instanceof ServerWorld sw) {
            InhabitantManager.onDeath(sw, this, source);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isSleeping() ? null : SoundEvents.ENTITY_VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_VILLAGER_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return switch (civ()) {
            case STONEBORN, FORGEKIN -> 0.8f;
            case ASTRALS, GLIMMERKIN -> 1.15f;
            case SPOREFOLK -> 1.05f;
            default -> 1.0f;
        } + (getRandom().nextFloat() - 0.5f) * 0.1f;
    }
}
