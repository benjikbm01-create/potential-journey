package dev.underdeep.wildlife.ai;

import dev.underdeep.block.plant.Env;
import dev.underdeep.block.plant.HarvestablePlantBlock;
import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.ecology.Habitats;
import dev.underdeep.ecology.Species;
import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.CreatureSpecies.Diet;
import dev.underdeep.wildlife.CreatureSpecies.HuntStyle;
import dev.underdeep.wildlife.CreatureSpecies.Locomotion;
import dev.underdeep.wildlife.CreatureSpecies.Social;
import dev.underdeep.wildlife.CreatureSpecies.Temperament;
import dev.underdeep.wildlife.Rhythms;
import dev.underdeep.wildlife.UnderdeepCreature;
import dev.underdeep.wildlife.Voices;
import dev.underdeep.wildlife.WildlifeRegistry;
import dev.underdeep.wildlife.population.WildlifeManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.AnimalMateGoal;
import net.minecraft.entity.ai.goal.AttackWithOwnerGoal;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.FollowParentGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.ai.goal.SwimAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.TrackOwnerAttackerGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Reusable behaviour components for all wildlife. {@link #install} composes a species' goal set from
 * its data; each goal is small, cooldown-limited, and does bounded searches only when it might start.
 * Creatures far from players ({@code lowDetail}) skip the searching goals and simply wander.
 * <p>
 * Behaviours: wander, swim/school, fly/hover/perch, glide, forage (graze/browse/fungi/minerals/nectar),
 * drink, hunt (pursuit, stalk, ambush, pack, aquatic, swoop), flee (predators, players, herd alarms),
 * rest/sleep with shelter seeking, territorial warning and charge, herd/pack following, socialising,
 * play, curiosity, scavenging and food stealing, nest building, protecting young, breeding (vanilla
 * mate goal driven by needs), following parents, taming companions (sit/follow/defend), revenge.
 */
public final class CreatureGoals {
    private CreatureGoals() {
    }

    public static void install(UnderdeepCreature c, CreatureSpecies s, GoalSelector goals, GoalSelector targets) {
        Locomotion l = s.locomotion();
        if (l != Locomotion.AQUATIC && l != Locomotion.FLYER && l != Locomotion.HOVER) {
            goals.add(0, new SwimGoal(c));
        }
        goals.add(1, new SitGoal(c));
        goals.add(1, new FleeGoal(c));
        if (s.attack() <= 1) {
            goals.add(1, new EscapeDangerGoal(c, 1.5));
        }
        if (s.temperament() == Temperament.TERRITORIAL) {
            goals.add(2, new TerritoryGoal(c));
        }
        if (s.attack() > 1) {
            goals.add(3, new MeleeAttackGoal(c, s.isPredator() ? 1.7 : 1.35, true)); // predators sprint when they charge
        }
        if (s.isPredator()) {
            goals.add(3, new HuntGoal(c));
        }
        if (s.temperament() == Temperament.AMBUSH) {
            goals.add(3, new AmbushPlayerGoal(c));
        }
        if (s.tamable()) {
            goals.add(4, new FollowOwnerAnyGoal(c));
        }
        goals.add(4, new AnimalMateGoal(c, 1.0));
        goals.add(5, new FollowParentGoal(c, 1.15));
        goals.add(5, new TemptGoal(c, 1.1, st -> isFood(s, st), false));
        goals.add(5, new ForageGoal(c));
        goals.add(5, new RestGoal(c));
        if (s.social() != Social.SOLITARY) {
            goals.add(6, new GroupFollowGoal(c));
            goals.add(7, new SocialGoal(c));
        }
        if (s.nestType() != CreatureSpecies.Nest.NONE) {
            goals.add(7, new NestGoal(c));
        }
        if (s.diet() == Diet.SCAVENGER || s.diet() == Diet.OMNIVORE || s.temperament() == Temperament.CURIOUS) {
            goals.add(7, new ScavengeGoal(c));
        }
        if (s.temperament() == Temperament.CURIOUS || s.temperament() == Temperament.PASSIVE || s.temperament() == Temperament.NEUTRAL) {
            goals.add(8, new CuriosityGoal(c));
        }
        goals.add(8, new PlayGoal(c));
        if (!s.isAquatic() && !s.flies()) {
            goals.add(8, new DrinkGoal(c));
        }
        switch (l) {
            case AQUATIC -> goals.add(9, new SwimAroundGoal(c, 1.0, 30));
            case FLYER, HOVER -> goals.add(9, new FlyWanderGoal(c));
            default -> goals.add(9, new WanderAroundFarGoal(c, 0.9, 0.002f));
        }
        goals.add(10, new LookAtEntityGoal(c, PlayerEntity.class, 6.0f));
        goals.add(11, new LookAroundGoal(c));

        if (s.temperament() != Temperament.PASSIVE && s.temperament() != Temperament.SKITTISH && s.attack() > 1) {
            targets.add(1, new RevengeGoal(c).setGroupRevenge());
        }
        if (s.tamable() && s.attack() > 1) {
            targets.add(2, new TrackOwnerAttackerGoal(c));
            targets.add(3, new AttackWithOwnerGoal(c));
        }
    }

    /** Breeding / taming / tempting food for a species. */
    public static boolean isFood(CreatureSpecies s, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (!s.tameFood().isEmpty() && id.getPath().equals(s.tameFood())) {
            return true;
        }
        String p = id.getPath();
        return switch (s.diet()) {
            case GRAZER, BROWSER -> p.equals("glowgrain") || p.equals("wheat") || p.equals("duskberries") || p.equals("honeyfruit")
                    || p.equals("cliffberries") || p.equals("snowberries") || p.equals("frost_turnip");
            case FUNGIVORE -> p.equals("puffcap") || p.equals("lanterncap") || p.equals("cave_truffle") || p.equals("rimecap");
            case LITHOVORE -> p.equals("calcicap") || p.equals("shardgrass") || p.equals("glimmer_shard") || p.equals("sulfur");
            case AQUATIC_GRAZER, FILTER -> p.equals("lakebloom") || p.equals("sea_grapes") || p.equals("ribbonweed");
            case NECTAR -> p.equals("honeyfruit") || p.equals("sunleaf_blossom");
            case OMNIVORE -> p.equals("duskberries") || p.equals("cave_truffle") || p.equals("game_meat") || p.equals("honeyfruit");
            case CARNIVORE, SCAVENGER -> p.equals("game_meat") || p.equals("fish_fillet") || p.equals("shellmeat");
        };
    }

    static boolean busy(UnderdeepCreature c) {
        return c.isSitting() || c.hasPassengers() || c.state() == UnderdeepCreature.SLEEP;
    }

    // =============================================================== foraging

    /** Seeks Stage 2 food plants matching the diet and eats them (bounded consumption, never crops). */
    static final class ForageGoal extends Goal {
        private final UnderdeepCreature c;
        private BlockPos target;
        private int cooldown;
        private int eatTicks;

        ForageGoal(UnderdeepCreature c) {
            this.c = c;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (--cooldown > 0 || busy(c) || c.lowDetail || c.getTarget() != null || c.hunger < 0.45f) {
                return false;
            }
            CreatureSpecies s = c.species();
            if (s.diet() == Diet.CARNIVORE || s.diet() == Diet.SCAVENGER) {
                return false;
            }
            cooldown = 60 + c.getRandom().nextInt(60);
            target = findFood();
            if (target == null && (s.diet() == Diet.GRAZER || s.diet() == Diet.LITHOVORE || s.diet() == Diet.FILTER)) {
                target = c.getBlockPos(); // graze/filter what is underfoot when nothing better is around
            }
            return target != null;
        }

        @Nullable
        private BlockPos findFood() {
            World w = c.getWorld();
            BlockPos o = c.getBlockPos();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < 28; i++) {
                BlockPos p = o.add(c.getRandom().nextInt(21) - 10, c.getRandom().nextInt(7) - 3, c.getRandom().nextInt(21) - 10);
                if (!w.isChunkLoaded(p)) {
                    continue;
                }
                if (edible(c.species(), w.getBlockState(p))) {
                    double d = p.getSquaredDistance(o);
                    if (d < bestD) {
                        best = p;
                        bestD = d;
                    }
                }
            }
            return best;
        }

        @Override
        public void start() {
            eatTicks = 0;
            c.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.9);
        }

        @Override
        public boolean shouldContinue() {
            return target != null && eatTicks < 50 && !busy(c) && c.getTarget() == null && c.hunger > 0.1f;
        }

        @Override
        public void tick() {
            c.getLookControl().lookAt(target.getX() + 0.5, target.getY() + 0.3, target.getZ() + 0.5);
            if (c.getBlockPos().getSquaredDistance(target) <= 4.5 || target.equals(c.getBlockPos())) {
                c.getNavigation().stop();
                c.setState(UnderdeepCreature.EAT);
                if (++eatTicks == 40) {
                    consume();
                }
            } else if (c.getNavigation().isIdle()) {
                if (++eatTicks > 30) {
                    target = null;
                }
            }
        }

        private void consume() {
            World w = c.getWorld();
            BlockState s = w.getBlockState(target);
            c.hunger = Math.max(0, c.hunger - 0.55f);
            WildlifeManager.recordFeeding(c, s);
            if (!(w instanceof ServerWorld sw) || UnderdeepConfig.get().wildlifeVegetationConsumption <= 0) {
                return;
            }
            if (s.getBlock() instanceof HarvestablePlantBlock && s.get(HarvestablePlantBlock.AGE) == 3) {
                sw.setBlockState(target, s.with(HarvestablePlantBlock.AGE, 1)); // browse fruit; the plant regrows
            } else if (c.species().diet() == Diet.NECTAR) {
                // pollination: occasionally helps a nearby harvestable plant mature (ecological service)
                if (s.getBlock() instanceof HarvestablePlantBlock && s.get(HarvestablePlantBlock.AGE) < 3 && c.getRandom().nextInt(4) == 0) {
                    sw.setBlockState(target, s.with(HarvestablePlantBlock.AGE, s.get(HarvestablePlantBlock.AGE) + 1));
                }
            } else if (s.isReplaceable() && !s.isAir() && !(s.getBlock() instanceof CropBlock) && s.getFluidState().isEmpty()
                    && c.getRandom().nextDouble() < 0.2 * UnderdeepConfig.get().wildlifeVegetationConsumption
                    && !Env.protectedArea(sw, target)) {
                sw.breakBlock(target, false); // grazed grass/fern; spreading species regrow it
            }
            if (!c.species().flies()) {
                sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, c.getX(), c.getY() + c.getHeight(), c.getZ(), 2, 0.2, 0.2, 0.2, 0);
            }
        }

        @Override
        public void stop() {
            if (c.state() == UnderdeepCreature.EAT) {
                c.setState(UnderdeepCreature.IDLE);
            }
            target = null;
        }
    }

    /** Whether a block is food for the species (Stage 2 ecological roles plus vanilla equivalents). */
    public static boolean edible(CreatureSpecies s, BlockState st) {
        if (st.isAir() || st.getBlock() instanceof CropBlock) {
            return false;
        }
        Species sp = Habitats.speciesOf(st);
        if (sp != null && sp.isBiological() || sp != null && s.diet() == Diet.LITHOVORE) {
            for (Species.Role r : s.foodRoles()) {
                if (sp.has(r)) {
                    return !(st.getBlock() instanceof HarvestablePlantBlock) || st.get(HarvestablePlantBlock.AGE) >= 2
                            || s.diet() == Diet.NECTAR;
                }
            }
        }
        return switch (s.diet()) {
            case GRAZER -> st.isOf(Blocks.SHORT_GRASS) || st.isOf(Blocks.TALL_GRASS) || st.isOf(Blocks.FERN);
            case BROWSER -> st.isOf(Blocks.SWEET_BERRY_BUSH) || st.isIn(BlockTags.LEAVES) && st.isOf(Blocks.AZALEA_LEAVES);
            case FUNGIVORE -> st.isOf(Blocks.BROWN_MUSHROOM) || st.isOf(Blocks.RED_MUSHROOM);
            case AQUATIC_GRAZER, FILTER -> st.isOf(Blocks.SEAGRASS) || st.isOf(Blocks.KELP_PLANT);
            case NECTAR -> st.isIn(BlockTags.FLOWERS);
            case LITHOVORE -> st.isOf(Blocks.AMETHYST_CLUSTER) || st.isOf(Blocks.GLOW_LICHEN);
            default -> false;
        };
    }

    // =============================================================== drinking

    static final class DrinkGoal extends Goal {
        private final UnderdeepCreature c;
        private BlockPos water;
        private int ticks;
        private int cooldown = 600;

        DrinkGoal(UnderdeepCreature c) {
            this.c = c;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (--cooldown > 0 || busy(c) || c.lowDetail || c.getTarget() != null) {
                return false;
            }
            cooldown = 1800 + c.getRandom().nextInt(1800);
            BlockPos o = c.getBlockPos();
            for (int i = 0; i < 24; i++) {
                BlockPos p = o.add(c.getRandom().nextInt(17) - 8, c.getRandom().nextInt(5) - 3, c.getRandom().nextInt(17) - 8);
                if (c.getWorld().getFluidState(p).isIn(FluidTags.WATER) && c.getWorld().getBlockState(p.up()).isAir()) {
                    water = p;
                    return true;
                }
            }
            return false;
        }

        @Override
        public void start() {
            ticks = 0;
            c.getNavigation().startMovingTo(water.getX() + 0.5, water.getY() + 1, water.getZ() + 0.5, 0.9);
        }

        @Override
        public boolean shouldContinue() {
            return ticks < 140 && !busy(c) && c.getTarget() == null;
        }

        @Override
        public void tick() {
            ticks++;
            c.getLookControl().lookAt(water.getX() + 0.5, water.getY(), water.getZ() + 0.5);
            if (c.squaredDistanceTo(Vec3d.ofCenter(water)) < 5) {
                c.getNavigation().stop();
                c.setState(UnderdeepCreature.DRINK);
            }
        }

        @Override
        public void stop() {
            if (c.state() == UnderdeepCreature.DRINK) {
                c.setState(UnderdeepCreature.IDLE);
            }
        }
    }

    // =============================================================== resting

    /** Sleeps when tired or outside its active period; seeks a sheltered spot first; wakes when disturbed. */
    static final class RestGoal extends Goal {
        private final UnderdeepCreature c;
        private BlockPos spot;
        private int cooldown;

        RestGoal(UnderdeepCreature c) {
            this.c = c;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
        }

        @Override
        public boolean canStart() {
            if (--cooldown > 0 || c.isSitting() || c.hasPassengers() || c.getTarget() != null || c.isTouchingWater() && !c.species().isAquatic()) {
                return false;
            }
            cooldown = 40;
            if (c.hunger > 0.8f && c.tiredness < 0.95f) {
                return false; // hunger overrides rest: starving animals go looking for food
            }
            boolean active = Rhythms.isActive(c.species().activity(), c.getWorld().getTimeOfDay(), c.getId());
            if (!(c.tiredness > 0.75f || !active && c.tiredness > 0.35f)) {
                return false;
            }
            spot = shelter();
            return true;
        }

        private BlockPos shelter() {
            World w = c.getWorld();
            BlockPos o = c.getBlockPos();
            if (c.home != null && o.isWithinDistance(c.home, 16)) {
                return c.home;
            }
            for (int i = 0; i < 16; i++) {
                BlockPos p = o.add(c.getRandom().nextInt(13) - 6, 0, c.getRandom().nextInt(13) - 6);
                for (int dy = 1; dy <= 4; dy++) {
                    BlockState above = w.getBlockState(p.up(dy));
                    if (above.isOpaqueFullCube(w, p.up(dy)) || above.isIn(BlockTags.LEAVES)) {
                        return p;
                    }
                }
            }
            return o;
        }

        @Override
        public void start() {
            c.getNavigation().startMovingTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.8);
        }

        @Override
        public boolean shouldContinue() {
            return c.tiredness > 0.08f && c.hunger < 0.9f && c.getTarget() == null && c.state() != UnderdeepCreature.ALERT
                    && c.getAttacker() == null;
        }

        @Override
        public void tick() {
            if (c.state() != UnderdeepCreature.SLEEP && (c.getNavigation().isIdle() || c.getBlockPos().isWithinDistance(spot, 2))) {
                c.setState(UnderdeepCreature.SLEEP);
                if (c.species().flies()) {
                    c.setNoGravity(false);
                }
            }
        }

        @Override
        public void stop() {
            if (c.state() == UnderdeepCreature.SLEEP) {
                c.setState(UnderdeepCreature.IDLE);
            }
            if (c.species().flies()) {
                c.setNoGravity(true);
            }
        }
    }

    // =============================================================== fleeing

    /** Flees predators that hunt this species, players (by temperament/personality) and herd alarms. */
    static final class FleeGoal extends Goal {
        private final UnderdeepCreature c;
        private LivingEntity threat;
        private int check;
        private int fleeTicks;

        FleeGoal(UnderdeepCreature c) {
            this.c = c;
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            if (c.isSitting() || c.hasPassengers() || c.isTamed() && c.species().attack() > 1) {
                return false;
            }
            LivingEntity alarm = WildlifeManager.alarmSource(c);
            if (alarm != null) {
                threat = alarm;
                return true;
            }
            if (--check > 0 || c.lowDetail) {
                return false;
            }
            check = 10 + c.getRandom().nextInt(6);
            threat = findThreat();
            return threat != null;
        }

        @Nullable
        private LivingEntity findThreat() {
            float ff = c.personality().fleeFactor();
            double r = 10 * ff;
            List<LivingEntity> near = c.getWorld().getEntitiesByClass(LivingEntity.class, c.getBoundingBox().expand(r, 4, r), e -> e != c && e.isAlive());
            Temperament t = c.species().temperament();
            for (LivingEntity e : near) {
                if (e instanceof UnderdeepCreature o && o.species().preyIds().contains(c.species().id) && !o.isTamed()) {
                    // stalkers and ambushers are only noticed close up until they charge
                    boolean stealthy = (o.species().huntStyle() == HuntStyle.STALK || o.species().huntStyle() == HuntStyle.AMBUSH)
                            && o.getTarget() == null;
                    if (!stealthy || c.squaredDistanceTo(o) < 16 * ff * ff) {
                        return e;
                    }
                }
                if (e instanceof PlayerEntity p && !p.isCreative() && !p.isSpectator() && !c.isTamed()) {
                    double d = c.squaredDistanceTo(p);
                    boolean scary = switch (t) {
                        case SKITTISH -> d < 36 * ff * ff && !(p.isSneaking() && d > 9);
                        case PASSIVE -> p.isSprinting() && d < 16;
                        case CURIOUS -> p.isSprinting() && d < 9 && c.personality().timidity() > 0.5f;
                        default -> false;
                    };
                    if (scary) {
                        return p;
                    }
                }
            }
            return null;
        }

        @Override
        public void start() {
            fleeTicks = 0;
            c.setState(UnderdeepCreature.ALERT);
            WildlifeManager.alarmHerd(c, threat);
            run();
        }

        private void run() {
            Vec3d away = c.species().isAquatic() || c.species().flies()
                    ? c.getPos().add(c.getPos().subtract(threat.getPos()).normalize().multiply(10))
                    : NoPenaltyTargeting.findFrom(c, 16, 6, threat.getPos());
            if (away != null) {
                c.getNavigation().startMovingTo(away.x, away.y, away.z, fleeTicks < 60 ? 1.5 : 1.2); // a burst, then they tire
            }
        }

        @Override
        public boolean shouldContinue() {
            return threat != null && threat.isAlive() && fleeTicks < 160 && c.squaredDistanceTo(threat) < 22 * 22;
        }

        @Override
        public void tick() {
            if (++fleeTicks % 20 == 0 || c.getNavigation().isIdle()) {
                run();
            }
        }

        @Override
        public void stop() {
            threat = null;
            if (c.state() == UnderdeepCreature.ALERT) {
                c.setState(UnderdeepCreature.IDLE);
            }
        }
    }

    // =============================================================== hunting

    /** Species-specific hunting: pursuit, stalking, ambush, pack, aquatic and swoop strategies. */
    static final class HuntGoal extends Goal {
        private final UnderdeepCreature c;
        private LivingEntity prey;
        private int ticks;
        private int search;

        HuntGoal(UnderdeepCreature c) {
            this.c = c;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (c.isTamed() || busy(c) || c.lowDetail || c.huntCooldown > 0 || c.hunger < 0.5f || c.getTarget() != null) {
                return false;
            }
            if (--search > 0) {
                return false;
            }
            search = 40 + c.getRandom().nextInt(40);
            boolean active = Rhythms.isActive(c.species().activity(), c.getWorld().getTimeOfDay(), c.getId());
            if (!active && c.hunger < 0.85f) {
                return false;
            }
            prey = WildlifeManager.packTarget(c);
            if (prey == null) {
                double r = c.species().huntStyle() == HuntStyle.AMBUSH ? 6 : 18;
                List<UnderdeepCreature> list = c.getWorld().getEntitiesByClass(UnderdeepCreature.class, c.getBoundingBox().expand(r, 6, r),
                        e -> e.isAlive() && c.species().preyIds().contains(e.species().id) && !e.isTamed());
                prey = list.stream().min((a, b) -> Double.compare(c.squaredDistanceTo(a), c.squaredDistanceTo(b))).orElse(null);
            }
            return prey != null;
        }

        @Override
        public void start() {
            ticks = 0;
            WildlifeManager.sharePackTarget(c, prey);
        }

        @Override
        public boolean shouldContinue() {
            // once the predator commits (target set) the melee goal takes over the attack
            return prey != null && prey.isAlive() && c.getTarget() != prey && ticks < 300 && c.squaredDistanceTo(prey) < 26 * 26 && !busy(c);
        }

        @Override
        public void tick() {
            ticks++;
            double d = c.squaredDistanceTo(prey);
            c.getLookControl().lookAt(prey, 30, 30);
            switch (c.species().huntStyle()) {
                case AMBUSH -> {
                    if (d < 25) {
                        c.setTarget(prey); // lunge: melee goal takes over
                    } else {
                        c.getNavigation().stop();
                    }
                }
                case STALK -> {
                    if (d > 49) {
                        c.getNavigation().startMovingTo(prey, 0.55);
                    } else {
                        c.setTarget(prey);
                    }
                }
                default -> c.setTarget(prey);
            }
        }

        @Override
        public void stop() {
            if (prey != null && prey.isAlive() && c.getTarget() == prey) {
                c.huntCooldown = 200; // handed over to the attack; UnderdeepCreature limits the chase and feeds on a kill
            } else if (prey != null && !prey.isAlive()) {
                c.huntCooldown = Math.max(c.huntCooldown, 400);
            } else {
                c.huntCooldown = 400;
            }
            prey = null;
        }
    }

    /** Dangerous ambushers attack players who come very close, only in dangerous ecosystems and never in Peaceful. */
    static final class AmbushPlayerGoal extends Goal {
        private final UnderdeepCreature c;
        private int check;

        AmbushPlayerGoal(UnderdeepCreature c) {
            this.c = c;
        }

        @Override
        public boolean canStart() {
            if (c.isTamed() || busy(c) || --check > 0 || c.getTarget() != null || c.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
                return false;
            }
            check = 20;
            PlayerEntity p = c.getWorld().getClosestPlayer(c, 5);
            if (p == null || p.isCreative() || p.isSpectator() || p.isSneaking() && c.squaredDistanceTo(p) > 4) {
                return false;
            }
            c.setTarget(p);
            c.setState(UnderdeepCreature.ATTACK);
            return false;
        }
    }

    // =============================================================== territory

    /** Warns intruders (posture + call), charges only if they press closer, never chases far from home. */
    static final class TerritoryGoal extends Goal {
        private final UnderdeepCreature c;
        private LivingEntity intruder;
        private int ticks;
        private int check;

        TerritoryGoal(UnderdeepCreature c) {
            this.c = c;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (c.isTamed() || busy(c) || c.lowDetail || --check > 0 || c.home == null) {
                return false;
            }
            check = 15;
            double r = 7 + c.personality().aggression() * 4;
            if (!c.getBlockPos().isWithinDistance(c.home, r * 1.5)) {
                return false;
            }
            for (LivingEntity e : c.getWorld().getEntitiesByClass(LivingEntity.class, c.getBoundingBox().expand(r, 3, r), e -> e != c && e.isAlive())) {
                if (e instanceof PlayerEntity p && !p.isCreative() && !p.isSpectator() && c.getWorld().getDifficulty() != Difficulty.PEACEFUL) {
                    intruder = p;
                    return true;
                }
                if (e instanceof UnderdeepCreature o && !o.species().id.equals(c.species().id) && o.species().sizeClass() >= 0.8f
                        && !c.species().preyIds().contains(o.species().id)) {
                    intruder = o;
                    return true;
                }
            }
            return false;
        }

        @Override
        public void start() {
            ticks = 0;
            c.setState(UnderdeepCreature.WARN);
            c.playSound(Voices.warning(c.species()), 1.0f, c.getSoundPitch());
        }

        @Override
        public boolean shouldContinue() {
            return intruder != null && intruder.isAlive() && ticks < 160 && c.home != null && c.getBlockPos().isWithinDistance(c.home, 18)
                    && c.squaredDistanceTo(intruder) < 15 * 15;
        }

        @Override
        public void tick() {
            ticks++;
            c.getLookControl().lookAt(intruder, 30, 30);
            double d = c.squaredDistanceTo(intruder);
            if (ticks < 50) {
                c.getNavigation().stop();
                if (ticks % 12 == 0 && c.getWorld() instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.POOF, c.getX(), c.getY() + 0.1, c.getZ(), 4, 0.3, 0.05, 0.3, 0.01); // stomping
                }
            } else if (d < 16 + c.personality().aggression() * 16) {
                c.setTarget(intruder); // short warning charge
            } else if (ticks > 100) {
                intruder = null;
            }
        }

        @Override
        public void stop() {
            if (c.getTarget() == intruder) {
                c.setTarget(null);
            }
            intruder = null;
            c.setState(UnderdeepCreature.IDLE);
            if (c.home != null) {
                c.getNavigation().startMovingTo(c.home.getX(), c.home.getY(), c.home.getZ(), 0.9);
            }
        }
    }

    // =============================================================== groups

    /** Herd/pack/school/colony members keep near their group leader (shared destination via the leader). */
    static final class GroupFollowGoal extends Goal {
        private final UnderdeepCreature c;
        private UnderdeepCreature leader;
        private int check;

        GroupFollowGoal(UnderdeepCreature c) {
            this.c = c;
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            if (busy(c) || c.isTamed() || c.herdId == 0 || --check > 0) {
                return false;
            }
            check = 30 + c.getRandom().nextInt(20);
            leader = WildlifeManager.leaderOf(c);
            double keep = c.species().social() == Social.SCHOOL || c.species().social() == Social.SWARM ? 4 : 7;
            return leader != null && leader != c && c.squaredDistanceTo(leader) > keep * keep;
        }

        @Override
        public void start() {
            Vec3d to = leader.getPos().add((c.getRandom().nextDouble() - 0.5) * 4, 0, (c.getRandom().nextDouble() - 0.5) * 4);
            c.getNavigation().startMovingTo(to.x, to.y, to.z, 1.05);
        }

        @Override
        public boolean shouldContinue() {
            return !c.getNavigation().isIdle() && leader != null && leader.isAlive() && c.squaredDistanceTo(leader) > 9;
        }
    }

    /** Lonely social creatures approach a group member and spend a moment together. */
    static final class SocialGoal extends Goal {
        private final UnderdeepCreature c;
        private UnderdeepCreature mate;
        private int ticks;

        SocialGoal(UnderdeepCreature c) {
            this.c = c;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (busy(c) || c.lowDetail || c.loneliness < 0.65f || c.getRandom().nextInt(40) != 0) {
                return false;
            }
            mate = c.getWorld().getEntitiesByClass(UnderdeepCreature.class, c.getBoundingBox().expand(10, 4, 10),
                    e -> e != c && e.getType() == c.getType()).stream().findFirst().orElse(null);
            return mate != null;
        }

        @Override
        public void start() {
            ticks = 0;
            c.getNavigation().startMovingTo(mate, 0.9);
        }

        @Override
        public boolean shouldContinue() {
            return mate.isAlive() && ticks < 120;
        }

        @Override
        public void tick() {
            ticks++;
            c.getLookControl().lookAt(mate, 30, 30);
            if (c.squaredDistanceTo(mate) < 4) {
                c.getNavigation().stop();
                c.setState(UnderdeepCreature.PLAY);
                if (ticks > 60) {
                    c.loneliness = 0;
                    mate.loneliness = 0;
                }
            }
        }

        @Override
        public void stop() {
            c.setState(UnderdeepCreature.IDLE);
        }
    }

    /** Young and playful individuals chase each other in short bursts. */
    static final class PlayGoal extends Goal {
        private final UnderdeepCreature c;
        private UnderdeepCreature friend;
        private int ticks;

        PlayGoal(UnderdeepCreature c) {
            this.c = c;
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            if (busy(c) || c.lowDetail || !(c.isBaby() || c.personality().playfulness() > 0.72f) || c.getRandom().nextInt(200) != 0
                    || c.hunger > 0.6f) {
                return false;
            }
            friend = c.getWorld().getEntitiesByClass(UnderdeepCreature.class, c.getBoundingBox().expand(8, 3, 8),
                    e -> e != c && e.getType() == c.getType() && (e.isBaby() || e.personality().playfulness() > 0.5f)).stream().findAny().orElse(null);
            return friend != null;
        }

        @Override
        public void start() {
            ticks = 0;
            c.setState(UnderdeepCreature.PLAY);
        }

        @Override
        public boolean shouldContinue() {
            return friend.isAlive() && ticks < 100;
        }

        @Override
        public void tick() {
            if (++ticks % 20 == 0) {
                c.getNavigation().startMovingTo(friend, 1.25);
                friend.getNavigation().startMovingTo(c.getX() + (c.getRandom().nextDouble() - 0.5) * 8, c.getY(),
                        c.getZ() + (c.getRandom().nextDouble() - 0.5) * 8, 1.25);
            }
        }

        @Override
        public void stop() {
            c.setState(UnderdeepCreature.IDLE);
        }
    }

    /** Curious individuals approach players to look at them, then go back to their business. */
    static final class CuriosityGoal extends Goal {
        private final UnderdeepCreature c;
        private PlayerEntity player;
        private int ticks;
        private int cooldown = 400;

        CuriosityGoal(UnderdeepCreature c) {
            this.c = c;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (--cooldown > 0 || busy(c) || c.lowDetail || c.isTamed()) {
                return false;
            }
            cooldown = 600 + c.getRandom().nextInt(900);
            float chance = c.species().temperament() == Temperament.CURIOUS ? 0.6f : 0.15f;
            if (c.getRandom().nextFloat() > chance * (0.5f + c.personality().curiosity())) {
                return false;
            }
            player = c.getWorld().getClosestPlayer(c, 14);
            return player != null && !player.isSprinting() && !player.isSpectator();
        }

        @Override
        public void start() {
            ticks = 0;
        }

        @Override
        public boolean shouldContinue() {
            return player.isAlive() && ticks < 120 && !player.isSprinting();
        }

        @Override
        public void tick() {
            ticks++;
            c.getLookControl().lookAt(player, 30, 30);
            if (c.squaredDistanceTo(player) > 9) {
                c.getNavigation().startMovingTo(player, 0.7);
            } else {
                c.getNavigation().stop();
            }
        }
    }

    /** Scavengers and curious omnivores investigate unattended dropped food (never other items). */
    static final class ScavengeGoal extends Goal {
        private final UnderdeepCreature c;
        private ItemEntity item;
        private int cooldown = 200;

        ScavengeGoal(UnderdeepCreature c) {
            this.c = c;
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            if (--cooldown > 0 || busy(c) || c.lowDetail || c.isTamed()) {
                return false;
            }
            cooldown = 100;
            if (c.hunger < 0.3f && c.species().diet() != Diet.SCAVENGER) {
                return false;
            }
            item = c.getWorld().getEntitiesByClass(ItemEntity.class, c.getBoundingBox().expand(10, 3, 10),
                    e -> e.getItemAge() > 100 && e.getStack().contains(DataComponentTypes.FOOD) && !e.getStack().contains(DataComponentTypes.CUSTOM_NAME)
                            && c.getWorld().getClosestPlayer(e, 4) == null).stream().findFirst().orElse(null);
            return item != null;
        }

        @Override
        public void start() {
            c.getNavigation().startMovingTo(item, 1.0);
        }

        @Override
        public boolean shouldContinue() {
            return item.isAlive() && !c.getNavigation().isIdle();
        }

        @Override
        public void tick() {
            if (c.squaredDistanceTo(item) < 2.5 && c.getWorld() instanceof ServerWorld sw) {
                ItemStack st = item.getStack();
                st.decrement(1);
                if (st.isEmpty()) {
                    item.discard();
                }
                c.hunger = Math.max(0, c.hunger - 0.4f);
                c.setState(UnderdeepCreature.EAT);
                cooldown = 600;
                WildlifeManager.recordScavenge(c);
                c.getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            if (c.state() == UnderdeepCreature.EAT) {
                c.setState(UnderdeepCreature.IDLE);
            }
        }
    }

    /** Nesting species keep a nest near home (placed only into free space, never near builds). */
    static final class NestGoal extends Goal {
        private final UnderdeepCreature c;
        private int cooldown = 600;

        NestGoal(UnderdeepCreature c) {
            this.c = c;
        }

        @Override
        public boolean canStart() {
            if (--cooldown > 0 || c.lowDetail || c.isBaby() || busy(c)) {
                return false;
            }
            cooldown = 2400 + c.getRandom().nextInt(2400);
            if (c.getWorld() instanceof ServerWorld sw) {
                WildlifeManager.maintainNest(sw, c);
            }
            return false;
        }
    }

    /** Follow-owner that works with every navigation type (land, climbing, swimming, flying). */
    static final class FollowOwnerAnyGoal extends Goal {
        private final UnderdeepCreature c;
        private int recalc;

        FollowOwnerAnyGoal(UnderdeepCreature c) {
            this.c = c;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            LivingEntity o = c.getOwner();
            return c.isTamed() && !c.isSitting() && !c.hasPassengers() && o != null && !o.isSpectator() && c.squaredDistanceTo(o) > 49;
        }

        @Override
        public boolean shouldContinue() {
            LivingEntity o = c.getOwner();
            return o != null && !c.isSitting() && c.squaredDistanceTo(o) > 9;
        }

        @Override
        public void tick() {
            LivingEntity o = c.getOwner();
            c.getLookControl().lookAt(o, 10, c.getMaxLookPitchChange());
            if (--recalc <= 0) {
                recalc = 10;
                if (c.squaredDistanceTo(o) > 400) {
                    c.tryTeleportToOwner();
                } else {
                    c.getNavigation().startMovingTo(o, 1.15);
                }
            }
        }

        @Override
        public void stop() {
            c.getNavigation().stop();
        }
    }

    // =============================================================== flying

    /** Bounded flight inside caverns: picks nearby open air below the ceiling, prefers perches near leaves/ledges. */
    static final class FlyWanderGoal extends Goal {
        private final UnderdeepCreature c;

        FlyWanderGoal(UnderdeepCreature c) {
            this.c = c;
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            return !busy(c) && c.getNavigation().isIdle() && c.getRandom().nextInt(c.lowDetail ? 60 : 15) == 0;
        }

        @Override
        public void start() {
            World w = c.getWorld();
            for (int i = 0; i < 10; i++) {
                BlockPos p = c.getBlockPos().add(c.getRandom().nextInt(21) - 10, c.getRandom().nextInt(9) - 4, c.getRandom().nextInt(21) - 10);
                if (!w.isChunkLoaded(p) || !w.isAir(p) || !w.isAir(p.up())) {
                    continue;
                }
                // stay below the ceiling and not too far above the ground
                boolean ground = false;
                for (int dy = 1; dy <= 10 && !ground; dy++) {
                    ground = !w.isAir(p.down(dy));
                }
                if (ground) {
                    c.getNavigation().startMovingTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1.0);
                    return;
                }
            }
        }

        @Override
        public boolean shouldContinue() {
            return !c.getNavigation().isIdle();
        }
    }

    static boolean isLand(Direction d) {
        return d.getAxis().isHorizontal();
    }

    @SuppressWarnings("unused")
    private static Box around(UnderdeepCreature c, double r) {
        return c.getBoundingBox().expand(r);
    }
}
