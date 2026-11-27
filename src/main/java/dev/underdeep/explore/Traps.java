package dev.underdeep.explore;

import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.guide.Advancements;
import dev.underdeep.survival.Hazards;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

/**
 * Stage 9 traps. Every trap gives a readable warning (dust under loose ceilings, ash at dart slits, smoke before
 * flames, hissing before vents, cracks before a floor gives) and a chance to react; none kills outright. Effects:
 * <ul>
 *   <li>trap plates fire the traps listed for them in the site layout (darts, rockfall, alarm gong, darkness, vents);</li>
 *   <li>flame and hazard vents cycle on their own only while a player is within 20 blocks (random ticks restart the
 *   cycle; no world-wide loops); hazard vents feed the Stage 7 hazard meters ({@link Hazards#burst});</li>
 *   <li>crumbling floors give way a second after a step and drop nothing;</li>
 *   <li>alarm gongs wake a guard room once per site (persistent).</li>
 * </ul>
 * Disarming: sneak-use a trap plate with a stick or pickaxe (permanent, shared); block a vent by placing a block on it.
 * Dart arrows cannot be picked up (no free ammunition).
 */
public final class Traps {
    private Traps() {
    }

    static double severity() {
        return Math.max(0, UnderdeepConfig.get().exploration.trapSeverity);
    }

    public static void trigger(ServerWorld w, BlockPos plate, LivingEntity by) {
        Mechanisms.Ctx c = Mechanisms.ctx(w, plate);
        if (c == null) {
            return;
        }
        for (SiteLayout.Ref r : c.layout().at(plate)) {
            if (r.kind() != SiteLayout.Kind.TRIGGER || c.site().disarmed.contains(r.index())) {
                continue;
            }
            Canvas.TrapSpec t = c.layout().canvas.traps.get(r.index());
            w.playSound(null, plate, SoundEvents.BLOCK_TRIPWIRE_CLICK_ON, SoundCategory.BLOCKS, 0.9f, 0.6f);
            fire(c, r.index(), t);
            if (by instanceof ServerPlayerEntity p) {
                Advancements.event(p, "trap_sprung", t.type());
            }
        }
    }

    static void fire(Mechanisms.Ctx c, int index, Canvas.TrapSpec t) {
        ServerWorld w = c.world();
        for (long l : t.effectors()) {
            BlockPos e = c.layout().world(l);
            BlockState s = w.getBlockState(e);
            if (!(s.getBlock() instanceof ExploreBlocks.VentBlock v)) {
                continue;
            }
            switch (v.kind) {
                case "dart", "rockfall" -> w.scheduleBlockTick(e, v, v.kind.equals("dart") ? 8 : 14);
                case "gong" -> {
                    w.playSound(null, e, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 2f, 0.5f);
                    w.playSound(null, e, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.BLOCKS, 1.5f, 0.6f);
                    int gi = t.type().startsWith("alarm:") ? Integer.parseInt(t.type().substring(6)) : 0;
                    wake(c, gi);
                }
                case "lamp" -> {
                    if (s.get(ExploreBlocks.LIT)) {
                        w.setBlockState(e, s.with(ExploreBlocks.LIT, false), Block.NOTIFY_ALL);
                        w.scheduleBlockTick(e, v, 600);
                        w.spawnParticles(ParticleTypes.SMOKE, e.getX() + 0.5, e.getY() + 0.5, e.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.01);
                    }
                }
                default -> w.scheduleBlockTick(e, v, 1);
            }
        }
        if (t.type().equals("darkness")) {
            w.playSound(null, c.layout().world(t.effectors().get(0)), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1f, 0.5f);
        }
    }

    /** Wakes a guard room once per site: the restless dead of old crypts (vanilla undead) rise at their spawn points. */
    public static void wake(Mechanisms.Ctx c, int guardIndex) {
        if (guardIndex >= c.layout().canvas.guards.size() || !c.site().woken.add(guardIndex)) {
            return;
        }
        c.state().markDirty();
        Canvas.GuardSpec g = c.layout().canvas.guards.get(guardIndex);
        ServerWorld w = c.world();
        int count = (int) Math.max(1, Math.round(g.count() * Math.max(0.5, severity())));
        for (int i = 0; i < count && !g.spawns().isEmpty(); i++) {
            BlockPos at = c.layout().world(g.spawns().get(i % g.spawns().size()));
            HostileEntity mob = switch (g.kind()) {
                case "archer" -> EntityType.SKELETON.create(w);
                case "drowned" -> EntityType.DROWNED.create(w);
                case "spider" -> EntityType.CAVE_SPIDER.create(w);
                case "silverfish" -> EntityType.SILVERFISH.create(w);
                default -> i % 3 == 2 ? EntityType.SKELETON.create(w) : EntityType.ZOMBIE.create(w);
            };
            if (mob == null) {
                continue;
            }
            mob.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, w.random.nextFloat() * 360, 0);
            mob.initialize(w, w.getLocalDifficulty(at), net.minecraft.entity.SpawnReason.EVENT, null);
            if (mob.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isEmpty()) {
                mob.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            }
            w.spawnEntity(mob);
            w.spawnParticles(ParticleTypes.SOUL, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.5, 0.3, 0.02);
        }
        BlockPos center = c.layout().world(g.spawns().isEmpty() ? 0 : g.spawns().get(0));
        w.playSound(null, center, SoundEvents.ENTITY_WARDEN_AGITATED, SoundCategory.HOSTILE, 0.6f, 1.6f);
        for (ServerPlayerEntity p : w.getPlayers(pl -> pl.squaredDistanceTo(center.toCenterPos()) < 24 * 24)) {
            p.sendMessage(Text.literal("Something stirs in the dark.").formatted(Formatting.RED), true);
        }
    }

    // ------------------------------------------------------------------ vents

    public static void ventTick(ServerWorld w, BlockPos pos, BlockState s, ExploreBlocks.VentBlock v) {
        switch (v.kind) {
            case "dart" -> {
                Direction d = s.get(ExploreBlocks.FACING);
                w.playSound(null, pos, SoundEvents.BLOCK_DISPENSER_LAUNCH, SoundCategory.BLOCKS, 1f, 1.2f);
                for (int i = 0; i < 2; i++) {
                    ArrowEntity a = new ArrowEntity(w, pos.getX() + 0.5 + d.getOffsetX() * 0.7, pos.getY() + 0.5, pos.getZ() + 0.5 + d.getOffsetZ() * 0.7,
                            new ItemStack(Items.ARROW), null);
                    a.setVelocity(d.getOffsetX(), 0.05 + i * 0.03, d.getOffsetZ(), 1.5f, 4f);
                    a.setDamage(1.5 * severity());
                    a.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
                    w.spawnEntity(a);
                }
            }
            case "rockfall" -> {
                w.playSound(null, pos, SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 1.2f, 0.6f);
                BlockPos below = pos.down();
                if (w.getBlockState(below).isAir()) {
                    FallingBlockEntity f = FallingBlockEntity.spawnFromBlock(w, pos, Blocks.GRAVEL.getDefaultState());
                    f.setHurtEntities(1f * (float) severity(), 6);
                    f.dropItem = false;
                }
            }
            case "lamp" -> {
                if (!s.get(ExploreBlocks.LIT)) {
                    w.setBlockState(pos, s.with(ExploreBlocks.LIT, true), Block.NOTIFY_ALL);
                }
            }
            case "flame", "steam", "spore", "frost" -> cycle(w, pos, s, v);
            default -> {
            }
        }
    }

    public static void ventRandom(ServerWorld w, BlockPos pos, BlockState s, ExploreBlocks.VentBlock v) {
        if ((v.kind.equals("flame") || v.kind.equals("steam") || v.kind.equals("spore") || v.kind.equals("frost")) && playerNear(w, pos, 20)
                && !w.getBlockTickScheduler().isQueued(pos, v)) {
            w.scheduleBlockTick(pos, v, 5);
        }
    }

    /** Cyclic vents: period 100 ticks — 20 warning, 40 active, 40 quiet (phase offset per position). */
    static void cycle(ServerWorld w, BlockPos pos, BlockState s, ExploreBlocks.VentBlock v) {
        if (!playerNear(w, pos, 20) || !w.getBlockState(pos.up()).isAir() && w.getBlockState(pos.up()).isOpaqueFullCube(w, pos.up())) {
            if (s.get(ExploreBlocks.LIT)) {
                w.setBlockState(pos, s.with(ExploreBlocks.LIT, false), Block.NOTIFY_ALL);
            }
            return; // dormant (no one near, or blocked by a placed block); random ticks wake it later
        }
        long phase = Math.floorMod(w.getTime() + MathHelper.hashCode(pos), 100);
        boolean active = phase >= 20 && phase < 60;
        if (s.get(ExploreBlocks.LIT) != active) {
            w.setBlockState(pos, s.with(ExploreBlocks.LIT, active), Block.NOTIFY_ALL);
        }
        double x = pos.getX() + 0.5, y = pos.getY() + 1.05, z = pos.getZ() + 0.5;
        if (!active && phase < 20) {
            w.spawnParticles(ParticleTypes.SMOKE, x, y, z, 2, 0.15, 0.05, 0.15, 0.01);
            if (phase < 10) {
                w.playSound(null, pos, v.kind.equals("flame") ? SoundEvents.BLOCK_FIRE_AMBIENT : SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS,
                        0.5f, 1.4f);
            }
        }
        if (active) {
            Box zone = new Box(pos.up()).expand(v.kind.equals("flame") ? 0.1 : 1.2, 1.5, v.kind.equals("flame") ? 0.1 : 1.2).offset(0, 0.5, 0);
            switch (v.kind) {
                case "flame" -> {
                    w.spawnParticles(ParticleTypes.FLAME, x, y + 0.6, z, 6, 0.12, 0.6, 0.12, 0.02);
                    for (LivingEntity le : w.getEntitiesByClass(LivingEntity.class, zone, e -> !e.isFireImmune())) {
                        le.setOnFireFor((float) (3 * severity()));
                    }
                }
                case "steam" -> {
                    w.spawnParticles(ParticleTypes.CLOUD, x, y + 0.5, z, 5, 0.3, 0.6, 0.3, 0.02);
                    burst(w, zone, Hazards.Hazard.GEOTHERMAL);
                }
                case "spore" -> {
                    w.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y + 0.5, z, 10, 0.8, 0.6, 0.8, 0.01);
                    burst(w, zone, Hazards.Hazard.SPORE_CLOUDS);
                }
                case "frost" -> {
                    w.spawnParticles(ParticleTypes.SNOWFLAKE, x, y + 0.5, z, 8, 0.5, 0.6, 0.5, 0.02);
                    burst(w, zone, Hazards.Hazard.EXTREME_COLD);
                }
                default -> {
                }
            }
        }
        w.scheduleBlockTick(pos, v, 10);
    }

    private static void burst(ServerWorld w, Box zone, Hazards.Hazard h) {
        for (ServerPlayerEntity p : w.getEntitiesByClass(ServerPlayerEntity.class, zone, e -> true)) {
            Hazards.burst(p, h, (float) (10 * severity()));
        }
    }

    static boolean playerNear(ServerWorld w, BlockPos pos, double r) {
        return w.getClosestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, r, e -> !e.isSpectator()) != null;
    }

    public static void crumble(ServerWorld w, BlockPos pos) {
        BlockState s = w.getBlockState(pos);
        if (!s.isOf(ExploreBlocks.CRUMBLING_FLOOR)) {
            return;
        }
        w.breakBlock(pos, false);
        w.playSound(null, pos, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.BLOCKS, 1f, 0.5f);
        // neighbours of a collapsing span follow shortly
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos q = pos.offset(d);
            BlockState qs = w.getBlockState(q);
            if (qs.isOf(ExploreBlocks.CRUMBLING_FLOOR) && !qs.get(ExploreBlocks.PRESSED) && w.random.nextInt(3) == 0) {
                w.setBlockState(q, qs.with(ExploreBlocks.PRESSED, true), Block.NOTIFY_LISTENERS);
                w.scheduleBlockTick(q, qs.getBlock(), 12 + w.random.nextInt(10));
            }
        }
    }

    public static void disarm(ServerWorld w, BlockPos plate, ServerPlayerEntity p, ItemStack tool) {
        Mechanisms.Ctx c = Mechanisms.ctx(w, plate);
        w.setBlockState(plate, w.getBlockState(plate).with(ExploreBlocks.DISARMED, true), Block.NOTIFY_ALL);
        w.playSound(null, plate, SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.BLOCKS, 1f, 0.7f);
        if (tool.isOf(Items.STICK) && !p.isCreative()) {
            tool.decrement(1);
        } else if (tool.isDamageable()) {
            tool.damage(2, p, net.minecraft.entity.EquipmentSlot.MAINHAND);
        }
        if (c != null) {
            for (SiteLayout.Ref r : c.layout().at(plate)) {
                if (r.kind() == SiteLayout.Kind.TRIGGER) {
                    c.site().disarmed.add(r.index());
                }
            }
            c.state().markDirty();
        }
        p.sendMessage(Text.literal("You wedge the plate. It will not trigger anything again.").formatted(Formatting.GREEN), true);
        Advancements.event(p, "trap_disarmed", "plate");
    }
}
