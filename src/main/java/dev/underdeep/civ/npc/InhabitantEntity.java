package dev.underdeep.civ.npc;

import dev.underdeep.civ.Civilization;
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
        goalSelector.add(1, new FleeEntityGoal<>(this, HostileEntity.class, 10, 0.6, 0.75, e -> !isGuard()));
        goalSelector.add(2, new MeleeAttackGoal(this, 0.8, true) {
            @Override
            public boolean canStart() {
                return isGuard() && super.canStart();
            }
        });
        goalSelector.add(2, new LookAtCustomerGoal(this));
        goalSelector.add(3, new LongDoorInteractGoal(this, true));
        goalSelector.add(4, new ScheduleGoal(this));
        goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 6));
        goalSelector.add(9, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this) {
            @Override
            public boolean canStart() {
                return isGuard() && super.canStart();
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

    /** Daily routine: sleep in their bed at night, work by day, gather in the evening; guards patrol. */
    static final class ScheduleGoal extends Goal {
        private final InhabitantEntity e;
        private BlockPos target;
        private int repath, stuck, workTimer;

        ScheduleGoal(InhabitantEntity e) {
            this.e = e;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return !e.exhibit && e.getCustomer() == null && e.getTarget() == null;
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void stop() {
            if (e.isSleeping() && e.activity() != Activity.SLEEP) {
                e.wakeUp();
            }
        }

        @Override
        public void tick() {
            Activity a = e.activity();
            if (e.isSleeping() && a != Activity.SLEEP) {
                e.wakeUp();
            }
            if (e.isSleeping()) {
                return;
            }
            BlockPos want = e.spotFor(a);
            if (want == null) {
                return;
            }
            if (!want.equals(target)) {
                target = want;
                repath = 0;
                stuck = 0;
            }
            double d2 = e.squaredDistanceTo(Vec3d.ofBottomCenter(target));
            if (d2 > 2.6 * 2.6) {
                if (--repath <= 0 || e.getNavigation().isIdle()) {
                    repath = 40;
                    boolean ok = e.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.55);
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
            switch (a) {
                case SLEEP -> {
                    BlockState s = e.getWorld().getBlockState(target);
                    if (s.getBlock() instanceof BedBlock && !s.get(BedBlock.OCCUPIED)) {
                        e.getNavigation().stop();
                        e.sleep(target);
                    }
                }
                case WORK -> {
                    e.getNavigation().stop();
                    e.getLookControl().lookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
                    if (++workTimer % 50 == 0) {
                        e.swingHand(Hand.MAIN_HAND);
                        e.workEffect(target);
                    }
                }
                default -> {
                    if (e.getNavigation().isIdle() && e.getRandom().nextInt(80) == 0) {
                        BlockPos p = target.add(e.getRandom().nextInt(7) - 3, 0, e.getRandom().nextInt(7) - 3);
                        e.getNavigation().startMovingTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.45);
                    }
                }
            }
        }
    }

    public enum Activity { SLEEP, WORK, GATHER, WANDER, PATROL }

    public Activity activity() {
        long t = getWorld().getTimeOfDay() % 24000;
        boolean nightShift = (lookSeed & 1) == 0;
        if (isGuard()) {
            boolean night = t >= 12800 && t < 23500;
            return night == nightShift ? Activity.PATROL : Activity.SLEEP;
        }
        if (t >= 1000 && t < 11500) {
            return Activity.WORK;
        }
        if (t >= 11500 && t < 12800) {
            return Activity.GATHER;
        }
        if (t >= 12800 && t < 23500) {
            return Activity.SLEEP;
        }
        return Activity.WANDER;
    }

    /** Where the current activity takes place. */
    @Nullable
    BlockPos spotFor(Activity a) {
        return switch (a) {
            case SLEEP -> bed;
            case WORK -> work != null ? work : gather != null ? gather : home;
            case GATHER -> gather != null ? gather : home;
            case PATROL -> {
                long slot = getWorld().getTime() / 600 + lookSeed;
                yield (slot & 1) == 0 && work != null ? work : gather != null ? gather : home;
            }
            default -> home;
        };
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
        if (!isAlive() || hasCustomer() || isSleeping() || player.isSneaking()) {
            return super.interactMob(player, hand);
        }
        if (!getWorld().isClient) {
            InhabitantManager.onTalk(this, player);
            TradeOfferList offers = getOffers();
            if (offers.isEmpty()) {
                player.sendMessage(Text.translatable("message.underdeep.inhabitant.busy", getName()), true);
            } else {
                setCustomer(player);
                sendOffers(player, getDisplayName(), 1);
            }
        }
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    protected void fillRecipes() {
        TradeOfferList list = TradeTables.offers(civ(), profession(), lookSeed);
        InhabitantManager.applyUses(this, list);
        this.offers = list;
    }

    @Override
    protected void afterUsing(TradeOffer offer) {
        InhabitantManager.recordTrade(this, offer);
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
        if (hurt && !getWorld().isClient && source.getAttacker() instanceof LivingEntity attacker) {
            InhabitantManager.alarm(this, attacker);
        }
        return hurt;
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
