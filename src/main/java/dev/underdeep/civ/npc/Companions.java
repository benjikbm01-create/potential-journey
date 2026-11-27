package dev.underdeep.civ.npc;

import dev.underdeep.civ.mind.Bond;
import dev.underdeep.civ.mind.Brain;
import dev.underdeep.civ.mind.Feelings.Emotion;
import dev.underdeep.civ.mind.Feelings.Need;
import dev.underdeep.civ.mind.Memory;
import dev.underdeep.civ.mind.Mind;
import dev.underdeep.civ.mind.Trait;
import dev.underdeep.config.UnderdeepConfig;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Companions (Stage 5): inhabitants who travel with a player. One authoritative identity (the record): the
 * companion entity is embodied near its owner by {@link InhabitantManager}, never duplicated, never saved; when
 * the owner logs out or leaves the world it waits abstractly and rejoins later. Defeat incapacitates (the
 * companion goes home to recover) unless {@code companionPermanentDeath} is enabled. Only the owner can command
 * a companion or open its pack; companions never attack players.
 */
public final class Companions {
    private Companions() {
    }

    public enum Mode { FOLLOW, WAIT, CLOSE, DISTANCE, HOME, REST }

    public enum Stance { AVOID, SELF, DEFEND_ME }

    public static Mode mode(Mind m) {
        int i = m.companionMode & 0xFF;
        return i < Mode.values().length ? Mode.values()[i] : Mode.FOLLOW;
    }

    public static Stance stance(Mind m) {
        int i = m.companionMode >> 8 & 0xFF;
        return i < Stance.values().length ? Stance.values()[i] : Stance.SELF;
    }

    static void set(Mind m, Mode mode) {
        m.companionMode = m.companionMode & ~0xFF | mode.ordinal();
    }

    static void set(Mind m, Stance s) {
        m.companionMode = m.companionMode & 0xFF | s.ordinal() << 8;
    }

    /** With its owner (not resting at home). */
    public static boolean travelling(Mind m) {
        return m != null && m.companionOf != null && mode(m) != Mode.HOME;
    }

    static int count(ServerWorld w, UUID player) {
        int n = 0;
        for (SettlementRecord rec : SettlementState.get(w).all().values()) {
            for (SettlementRecord.InhabitantRecord r : rec.inhabitants) {
                n += r.alive && r.mind != null && player.equals(r.mind.companionOf) ? 1 : 0;
            }
        }
        return n;
    }

    // ================================================================== dialogue

    static void options(ServerPlayerEntity p, InhabitantEntity e, Topics.Talk t, List<String> ids, List<String> labels) {
        UnderdeepConfig cfg = UnderdeepConfig.get();
        if (!cfg.companionsEnabled) {
            return;
        }
        Mind m = t.m;
        if (p.getUuid().equals(m.companionOf)) {
            Mode mode = mode(m);
            if (mode != Mode.FOLLOW) {
                add(ids, labels, "c_follow", "Follow me");
            }
            if (mode != Mode.WAIT) {
                add(ids, labels, "c_wait", "Wait here");
            }
            add(ids, labels, mode == Mode.CLOSE ? "c_distance" : "c_close", mode == Mode.CLOSE ? "Give me some room" : "Stay close");
            add(ids, labels, "c_stance", switch (stance(m)) {
                case AVOID -> "Defend yourself if attacked";
                case SELF -> "Protect me";
                case DEFEND_ME -> "Avoid fights";
            });
            add(ids, labels, "c_rest", "Take a rest");
            add(ids, labels, "c_pack", "Carry some supplies");
            add(ids, labels, "c_home", "Go home for now");
            add(ids, labels, "c_leave", "We part ways here");
        } else if (m.companionOf == null && t.bond.familiarity >= 20 && !t.wary()) {
            add(ids, labels, "c_join", "Would you travel with me?");
        }
    }

    private static void add(List<String> ids, List<String> labels, String id, String label) {
        if (ids.size() < 15) {
            ids.add(id);
            labels.add(label);
        }
    }

    /** Returns the reply, "" when the dialogue closes, or null for an unknown option. */
    @Nullable
    static String choose(ServerPlayerEntity p, InhabitantEntity e, Topics.Talk t, String option) {
        Mind m = t.m;
        boolean mine = p.getUuid().equals(m.companionOf);
        if (!option.startsWith("c_") || !UnderdeepConfig.get().companionsEnabled) {
            return null;
        }
        if (option.equals("c_join")) {
            return join(p, e, t);
        }
        if (!mine) {
            return null; // only the owner commands a companion
        }
        switch (option) {
            case "c_follow" -> {
                set(m, Mode.FOLLOW);
                return t.pick("Lead the way.", "Right behind you.");
            }
            case "c_wait" -> {
                set(m, Mode.WAIT);
                e.waitPos = e.getBlockPos();
                return t.pick("I'll wait here.", "Don't be long.");
            }
            case "c_close" -> {
                set(m, Mode.CLOSE);
                return "I'll stick close.";
            }
            case "c_distance" -> {
                set(m, Mode.DISTANCE);
                return "I'll keep back a bit.";
            }
            case "c_stance" -> {
                Stance s = switch (stance(m)) {
                    case AVOID -> Stance.SELF;
                    case SELF -> Stance.DEFEND_ME;
                    case DEFEND_ME -> Stance.AVOID;
                };
                set(m, s);
                return switch (s) {
                    case AVOID -> m.trait(Trait.COURAGE) > 65 ? "Fine. I'll stay out of it. Reluctantly." : "Gladly. I'll keep my head down.";
                    case SELF -> "I'll defend myself if something comes at me.";
                    case DEFEND_ME -> m.trait(Trait.COURAGE) < 35 ? "I'll... try. I'll try." : "Anything that comes for you deals with me.";
                };
            }
            case "c_rest" -> {
                set(m, Mode.REST);
                return m.need(Need.REST) < 0.4 ? "Oh, thank you. My feet are killing me." : "A short break, then.";
            }
            case "c_pack" -> {
                openPack(p, e, m, t.r.name);
                return "";
            }
            case "c_home" -> {
                set(m, Mode.HOME);
                e.companionOwner = null;
                return "I'll head home. Call for me when you need me.";
            }
            case "c_leave" -> {
                return leave(p, e, t);
            }
            default -> {
                return null;
            }
        }
    }

    private static String join(ServerPlayerEntity p, InhabitantEntity e, Topics.Talk t) {
        Mind m = t.m;
        UnderdeepConfig cfg = UnderdeepConfig.get();
        ServerWorld w = p.getServerWorld();
        if (m.companionOf != null) {
            return "I've already promised to travel with someone.";
        }
        if (m.age == Mind.Age.YOUNG) {
            return "Me? Really? ...My family would never let me.";
        }
        if (m.status != Mind.Status.ACTIVE || m.health < 0.6f) {
            return "I'm in no shape to travel right now.";
        }
        if (count(w, p.getUuid()) >= cfg.maxCompanionsPerPlayer) {
            return "You already have someone travelling with you.";
        }
        Bond b = t.bond;
        if (b.affection < 25 || b.trust < 25) {
            return t.hi(Trait.TRUSTFULNESS) && b.affection >= 15 ? "Maybe one day. I'd like to know you a little better first."
                    : "I don't know you well enough for that.";
        }
        double will = m.t(Trait.ADVENTUROUSNESS) * 0.5 + m.t(Trait.LOYALTY) * 0.2 + m.t(Trait.COURAGE) * 0.3 - m.t(Trait.HOMEBODY) * 0.5
                + (b.affection - 25) / 60.0 + (b.trust - 25) / 80.0 - (t.r.profession.equals("guard") ? 0.3 : 0)
                - (t.kin(Bond.Kin.CHILD) >= 0 ? 0.3 : 0);
        // deterministic per player and day: asking again today gives the same answer
        double roll = new java.util.Random(p.getUuid().getLeastSignificantBits() ^ t.r.appearance ^ t.now / 24000).nextDouble() * 0.6 - 0.3;
        if (will + roll < 0) {
            return t.hi(Trait.HOMEBODY) ? "Leave " + t.rec.name + "? No... my place is here. I'm sorry."
                    : t.kin(Bond.Kin.CHILD) >= 0 ? "I can't leave my family. Not now." : "Not today. Ask me again some other time.";
        }
        m.companionOf = p.getUuid();
        dev.underdeep.guide.Advancements.event(p, "companion_recruited", t.r.name);
        m.companionMode = 0;
        set(m, Mode.FOLLOW);
        set(m, Stance.SELF);
        b.companion = true;
        m.memories.remember(Memory.Type.JOINED_COMPANY, "p:" + p.getUuid(), "", t.now, Society.memLimit(), 1.2);
        m.feel(Emotion.EXCITEMENT, 0.4);
        while (m.companionPack.size() < 9) {
            m.companionPack.add(ItemStack.EMPTY);
        }
        InhabitantManager mgr = InhabitantManager.get(w);
        if (mgr != null) {
            Places pl = mgr.society.places(t.rec);
            if (pl != null) {
                pl.release(t.r.index);
            }
        }
        e.companionOwner = p;
        SettlementState.get(w).markDirty();
        return t.pick("Yes! Let me grab my things. Lead the way!", "I'd be glad to. Where are we going?", "Alright. I'm with you.");
    }

    private static String leave(ServerPlayerEntity p, InhabitantEntity e, Topics.Talk t) {
        Mind m = t.m;
        returnPack(p, m);
        m.companionOf = null;
        m.companionMode = 0;
        t.bond.companion = false;
        e.companionOwner = null;
        dev.underdeep.equipment.CompanionGear.clear(e);
        m.memories.remember(Memory.Type.GOAL_DONE, "p:" + p.getUuid(), "JOURNEY", t.now, Society.memLimit(), 0.8);
        SettlementState.get(p.getServerWorld()).markDirty();
        return t.bond.affection > 50 ? "I'll miss this. Come and find me, alright?" : "It was good travelling with you. Take care.";
    }

    /** The pack's contents go back to the player (inventory, or dropped at their feet): nothing vanishes. */
    static void returnPack(ServerPlayerEntity p, Mind m) {
        for (ItemStack s : m.companionPack) {
            if (!s.isEmpty() && !p.giveItemStack(s)) {
                p.dropItem(s, false);
            }
        }
        m.companionPack.clear();
    }

    // ================================================================== pack (no duplication: the inventory IS the record's list)

    static final class PackInventory implements Inventory {
        final List<ItemStack> list;
        final InhabitantEntity e;
        final Mind m;
        final UUID owner;

        PackInventory(InhabitantEntity e, Mind m, UUID owner) {
            this.e = e;
            this.m = m;
            this.owner = owner;
            this.list = m.companionPack;
            while (list.size() < 9) {
                list.add(ItemStack.EMPTY);
            }
        }

        @Override
        public int size() {
            return 9;
        }

        @Override
        public boolean isEmpty() {
            return list.stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getStack(int slot) {
            return list.get(slot);
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            ItemStack s = Inventories.splitStack(list, slot, amount);
            markDirty();
            return s;
        }

        @Override
        public ItemStack removeStack(int slot) {
            ItemStack s = Inventories.removeStack(list, slot);
            markDirty();
            return s;
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            list.set(slot, stack);
            markDirty();
        }

        @Override
        public void markDirty() {
            if (e.getWorld() instanceof ServerWorld sw) {
                SettlementState.get(sw).markDirty();
            }
        }

        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return player.getUuid().equals(owner) && owner.equals(m.companionOf) && e.isAlive() && !e.isRemoved() && e.squaredDistanceTo(player) < 64;
        }

        @Override
        public void clear() {
            for (int i = 0; i < 9; i++) {
                list.set(i, ItemStack.EMPTY);
            }
        }
    }

    static void openPack(ServerPlayerEntity p, InhabitantEntity e, Mind m, String name) {
        PackInventory inv = new PackInventory(e, m, p.getUuid());
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, pinv, pl) -> new GenericContainerScreenHandler(net.minecraft.screen.ScreenHandlerType.GENERIC_9X1, syncId, pinv, inv, 1),
                Text.literal(name.substring(0, name.indexOf(' ') < 0 ? name.length() : name.indexOf(' ')) + "'s pack")));
    }

    // ================================================================== behaviour

    /** Companion thought (replaces settlement routines while travelling). */
    static void think(Society soc, InhabitantEntity e, SettlementRecord rec, SettlementRecord.InhabitantRecord r) {
        Mind m = r.mind;
        ServerWorld w = soc.world;
        long now = w.getTime();
        long elapsed = m.lastUpdate < 0 ? 20 : Math.min(1000, now - m.lastUpdate);
        Brain.advance(m, elapsed);
        m.lastUpdate = now;
        m.health = Math.max(0.05f, e.getHealth() / e.getMaxHealth());
        ServerPlayerEntity owner = w.getServer().getPlayerManager().getPlayer(m.companionOf);
        e.companionOwner = owner != null && owner.getWorld() == w ? owner : null;
        if (e.companionOwner == null) {
            return;
        }
        Mode mode = mode(m);
        // rest restores; travelling tires
        m.activity = mode == Mode.REST ? dev.underdeep.civ.mind.Activity.SIT_AND_REST.ordinal() : dev.underdeep.civ.mind.Activity.EXPLORE_NEARBY.ordinal();
        m.satisfy(Need.SOCIAL, 0.004);
        m.satisfy(Need.CURIOSITY, 0.002);
        if (mode == Mode.REST && m.need(Need.REST) > 0.9) {
            set(m, Mode.FOLLOW);
            soc.say(e, "I'm rested. Let's go.", 0);
        }
        // Stage 8: wear and wield the best gear from the shared pack
        if (now % 40 < 20) {
            dev.underdeep.equipment.CompanionGear.apply(e, m);
        }
        // eat from the pack, then from their own belongings
        if (m.need(Need.HUNGER) < 0.35) {
            if (!eat(m.companionPack, m) && !eat(m.possessions, m) && soc.rnd.nextInt(90) == 0) {
                soc.say(e, "I'm getting really hungry...", 0);
            }
        }
        if (m.need(Need.REST) < 0.2 && mode != Mode.REST && soc.rnd.nextInt(120) == 0) {
            soc.say(e, "Can we rest soon? I'm exhausted.", 0);
        }
        // combat stance and retreat
        Stance st = stance(m);
        boolean hurt = m.health < 0.35f;
        e.defending = !hurt && st != Stance.AVOID;
        if (hurt && e.getTarget() != null) {
            e.setTarget(null);
            if (soc.rnd.nextInt(4) == 0) {
                soc.say(e, "I'm hurt! Falling back!", 0);
            }
        }
        if (!hurt && st == Stance.DEFEND_ME && e.getTarget() == null) {
            LivingEntity threat = owner.getAttacker() != null && !(owner.getAttacker() instanceof PlayerEntity) ? owner.getAttacker()
                    : owner.getAttacking() != null && !(owner.getAttacking() instanceof PlayerEntity) ? owner.getAttacking() : null;
            if (threat != null && threat.isAlive() && threat.squaredDistanceTo(e) < 16 * 16 && !(threat instanceof InhabitantEntity)) {
                e.setTarget(threat);
            }
        }
        if (e.getTarget() instanceof PlayerEntity) {
            e.setTarget(null); // never fight players
        }
        // shared experiences
        if (now % 600 < 20) {
            String biome = w.getBiome(e.getBlockPos()).getKey().map(k -> k.getValue().getPath()).orElse("");
            if (!biome.isEmpty() && !m.memories.has(Memory.Type.NEW_PLACE, biome)) {
                m.memories.remember(Memory.Type.NEW_PLACE, biome, "p:" + owner.getUuid(), now, Society.memLimit(), 0.7 + m.t(Trait.CURIOSITY) * 0.3);
                m.feel(Emotion.CURIOSITY, 0.2);
                soc.say(e, soc.rnd.nextBoolean() ? "I've never been this far from home." : "Look at this place!", 0);
            }
            m.bond(owner.getUuid()).adjust(now, 1, 1, 1, 0, 0, 0, true);
        }
        LivingEntity a = e.getAttacker();
        if (a != null && !a.isAlive() && owner.getAttacking() == a && e.age - e.getLastAttackedTime() < 200) {
            m.memories.remember(Memory.Type.DEFENDED_BY, "p:" + owner.getUuid(), "", now, Society.memLimit(), 1.2);
            m.bond(owner.getUuid()).adjust(now, 2, 5, 4, 3, 0, 0, false);
            e.setAttacker(null);
        }
    }

    private static boolean eat(List<ItemStack> from, Mind m) {
        for (ItemStack s : from) {
            if (!s.isEmpty() && Dialogue.isFood(s)) {
                s.decrement(1);
                m.satisfy(Need.HUNGER, 0.5);
                return true;
            }
        }
        return false;
    }

    /** Incapacitated instead of killed: the companion goes home to recover (default). */
    static void incapacitate(InhabitantEntity e) {
        if (!(e.getWorld() instanceof ServerWorld w)) {
            return;
        }
        SettlementRecord.InhabitantRecord r = InhabitantManager.record(e);
        if (r == null || r.mind == null) {
            return;
        }
        Mind m = r.mind;
        m.health = 0.15f;
        m.status = Mind.Status.RECOVERING;
        set(m, Mode.HOME);
        m.memories.remember(Memory.Type.DANGER, "", "defeated", w.getTime(), Society.memLimit(), 1.3);
        m.feel(Emotion.FEAR, 0.5);
        ServerPlayerEntity owner = m.companionOf == null ? null : w.getServer().getPlayerManager().getPlayer(m.companionOf);
        if (owner != null) {
            owner.sendMessage(Text.translatable("message.underdeep.companion.incapacitated", r.name).formatted(Formatting.RED), false);
        }
        w.spawnParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.8, e.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        SettlementState.get(w).markDirty();
        e.discard();
    }

    /** A safe standing spot near the player, preferably out of their view; null if none. */
    @Nullable
    static BlockPos safeSpot(ServerWorld w, PlayerEntity p, boolean preferHidden, java.util.Random rnd) {
        Vec3d look = p.getRotationVec(1f);
        BlockPos fallback = null;
        for (int tries = 0; tries < 40; tries++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double dist = 2 + rnd.nextDouble() * 4;
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos pos = new BlockPos(x, p.getBlockY() + dy, z);
                if (!safe(w, pos)) {
                    continue;
                }
                Vec3d dir = Vec3d.ofBottomCenter(pos).subtract(p.getPos()).normalize();
                boolean hidden = dir.dotProduct(look) < 0.1;
                if (hidden || !preferHidden) {
                    return pos;
                }
                if (fallback == null) {
                    fallback = pos;
                }
                break;
            }
        }
        return fallback;
    }

    public static boolean safe(ServerWorld w, BlockPos pos) {
        BlockState below = w.getBlockState(pos.down());
        BlockState feet = w.getBlockState(pos), head = w.getBlockState(pos.up());
        return below.isSideSolidFullSquare(w, pos.down(), net.minecraft.util.math.Direction.UP) && feet.getCollisionShape(w, pos).isEmpty()
                && head.getCollisionShape(w, pos.up()).isEmpty() && w.getFluidState(pos).isEmpty() && !w.getFluidState(pos.down()).isIn(FluidTags.LAVA)
                && !feet.isOf(net.minecraft.block.Blocks.FIRE) && !feet.isOf(net.minecraft.block.Blocks.LAVA);
    }

    /** Moves a companion that cannot keep up to a safe spot near its owner, out of view when possible. */
    static boolean relocate(InhabitantEntity e, PlayerEntity owner) {
        if (!(e.getWorld() instanceof ServerWorld w)) {
            return false;
        }
        BlockPos p = safeSpot(w, owner, true, new java.util.Random());
        if (p == null) {
            return false;
        }
        e.getNavigation().stop();
        e.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, e.getYaw(), 0);
        e.fallDistance = 0;
        return true;
    }
}
