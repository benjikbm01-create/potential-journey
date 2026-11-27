package dev.underdeep.equipment;

import com.mojang.serialization.Codec;
import dev.underdeep.Underdeep;
import dev.underdeep.guide.Uses;
import dev.underdeep.registry.Reg;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Accessory slots (Stage 8): two rings, an amulet and a charm, stored per player (kept on death), opened with a
 * keybind (or by using an accessory to wear it). Effects: attribute modifiers re-applied from scratch whenever the
 * slots change (stable ids; removed before being added, so equipping repeatedly never accumulates), hazard
 * protection and behaviour flags read on use. Everything is decided on the server; the client only learns the
 * flags it needs for movement prediction (climbing).
 */
public final class Accessories {
    private Accessories() {
    }

    public static final int SIZE = 4;
    public static final String[] SLOT_KIND = {"ring", "ring", "amulet", "charm"};

    /** Per-player accessory stacks (attachment, copied on death). */
    public static final class Data {
        public final ItemStack[] stacks = new ItemStack[SIZE];

        public Data() {
            java.util.Arrays.fill(stacks, ItemStack.EMPTY);
        }

        static final Codec<Data> CODEC = ItemStack.OPTIONAL_CODEC.listOf().xmap(l -> {
            Data d = new Data();
            for (int i = 0; i < Math.min(SIZE, l.size()); i++) {
                d.stacks[i] = l.get(i);
            }
            return d;
        }, d -> List.of(d.stacks));
    }

    public static AttachmentType<Data> TYPE;
    public static ScreenHandlerType<Handler> SCREEN;
    /** Client copy of the local player's accessory flags. */
    public static final Set<String> CLIENT_FLAGS = new HashSet<>();
    private static final Map<UUID, List<Object[]>> APPLIED = new HashMap<>();
    /** Players currently wearing a trader's token (read by settlement price offers). */
    public static final Set<UUID> TRADERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public record Open() implements CustomPayload {
        public static final Id<Open> ID = new Id<>(Identifier.of(Underdeep.MOD_ID, "open_accessories"));
        public static final PacketCodec<RegistryByteBuf, Open> CODEC = PacketCodec.unit(new Open());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record Flags(List<String> flags) implements CustomPayload {
        public static final Id<Flags> ID = new Id<>(Identifier.of(Underdeep.MOD_ID, "accessory_flags"));
        public static final PacketCodec<RegistryByteBuf, Flags> CODEC = PacketCodec.of((p, b) -> {
            b.writeVarInt(p.flags.size());
            p.flags.forEach(b::writeString);
        }, b -> {
            int n = Math.min(64, b.readVarInt());
            List<String> l = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                l.add(b.readString());
            }
            return new Flags(l);
        });

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    static void init() {
        TYPE = AttachmentRegistry.<Data>builder().persistent(Data.CODEC).copyOnDeath().initializer(Data::new).buildAndRegister(Reg.id("accessories"));
        SCREEN = Registry.register(Registries.SCREEN_HANDLER, Reg.id("accessories"),
                new ScreenHandlerType<>((syncId, inv) -> new Handler(syncId, inv, new SimpleInventory(SIZE)), FeatureFlags.VANILLA_FEATURES));
        PayloadTypeRegistry.playC2S().register(Open.ID, Open.CODEC);
        PayloadTypeRegistry.playS2C().register(Flags.ID, Flags.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Open.ID, (payload, ctx) -> ctx.server().execute(() -> open(ctx.player())));
        ServerPlayConnectionEvents.JOIN.register((h, s, server) -> server.execute(() -> refresh(h.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> APPLIED.remove(h.player.getUuid()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            APPLIED.remove(oldP.getUuid());
            refresh(newP);
        });
        for (EquipmentContent.Accessory a : EquipmentContent.ACCESSORIES.values()) {
            Uses.add(EquipmentItems.get(a.id()), "accessory (" + a.slot() + "): " + a.desc());
        }
    }

    public static Data of(PlayerEntity p) {
        return p.getAttachedOrCreate(TYPE);
    }

    public static boolean fits(int slot, ItemStack s) {
        return s.getItem() instanceof AccessoryItem ai && ai.def.slot().equals(SLOT_KIND[slot]);
    }

    public static void open(ServerPlayerEntity p) {
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, pl) -> new Handler(syncId, inv, new Inv(p)),
                Text.translatable("container.underdeep.accessories")));
    }

    /** Re-applies every accessory modifier from scratch (call after any change, join, respawn). */
    public static void refresh(ServerPlayerEntity p) {
        List<Object[]> old = APPLIED.remove(p.getUuid());
        if (old != null) {
            for (Object[] o : old) {
                @SuppressWarnings("unchecked") EntityAttributeInstance inst = p.getAttributeInstance((RegistryEntry<EntityAttribute>) o[0]);
                if (inst != null) {
                    inst.removeModifier((Identifier) o[1]);
                }
            }
        }
        // also clear any stale accessory modifiers left by an older session (ids are ours, prefix "accessory.")
        for (var attr : Registries.ATTRIBUTE.streamEntries().toList()) {
            EntityAttributeInstance inst = p.getAttributeInstance(attr);
            if (inst != null) {
                for (EntityAttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                    if (m.id().getNamespace().equals(Underdeep.MOD_ID) && m.id().getPath().startsWith("accessory.")) {
                        inst.removeModifier(m.id());
                    }
                }
            }
        }
        List<Object[]> now = new ArrayList<>();
        Data d = of(p);
        for (int i = 0; i < SIZE; i++) {
            if (!(d.stacks[i].getItem() instanceof AccessoryItem ai)) {
                continue;
            }
            int n = 0;
            for (EquipmentContent.Attr a : ai.def.attrs()) {
                var entry = a.entry();
                EntityAttributeInstance inst = entry == null ? null : p.getAttributeInstance(entry);
                if (inst == null) {
                    continue;
                }
                Identifier id = Reg.id("accessory." + i + "." + n++);
                inst.removeModifier(id);
                inst.addTemporaryModifier(new EntityAttributeModifier(id, a.amount(), a.op()));
                now.add(new Object[]{entry, id});
            }
        }
        APPLIED.put(p.getUuid(), now);
        if (flags(p).contains("trader")) {
            TRADERS.add(p.getUuid());
        } else {
            TRADERS.remove(p.getUuid());
        }
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
        if (ServerPlayNetworking.canSend(p, Flags.ID)) {
            ServerPlayNetworking.send(p, new Flags(new ArrayList<>(flags(p))));
        }
    }

    public static Set<String> flags(PlayerEntity p) {
        if (p.getWorld().isClient) {
            return CLIENT_FLAGS;
        }
        Set<String> f = new LinkedHashSet<>();
        for (ItemStack s : of(p).stacks) {
            if (s.getItem() instanceof AccessoryItem ai) {
                f.addAll(ai.def.flags());
            }
        }
        return f;
    }

    public static double protection(PlayerEntity p, String hazard) {
        double v = 0;
        for (ItemStack s : of(p).stacks) {
            if (s.getItem() instanceof AccessoryItem ai) {
                v += ai.def.protect().getOrDefault(hazard, 0.0);
            }
        }
        return v;
    }

    /** Inventory view over a player's accessory data; refreshes effects on every change. */
    static final class Inv extends SimpleInventory {
        final ServerPlayerEntity p;

        Inv(ServerPlayerEntity p) {
            super(SIZE);
            this.p = p;
            Data d = of(p);
            for (int i = 0; i < SIZE; i++) {
                super.setStack(i, d.stacks[i]);
            }
        }

        @Override
        public void markDirty() {
            super.markDirty();
            Data d = of(p);
            for (int i = 0; i < SIZE; i++) {
                d.stacks[i] = getStack(i);
            }
            refresh(p);
        }

        @Override
        public boolean isValid(int slot, ItemStack stack) {
            return fits(slot, stack);
        }

        @Override
        public int getMaxCountPerStack() {
            return 1;
        }
    }

    /** Accessory screen: 4 accessory slots above the player inventory. */
    public static class Handler extends ScreenHandler {
        public final SimpleInventory inv;

        public Handler(int syncId, PlayerInventory pinv, SimpleInventory inv) {
            super(SCREEN, syncId);
            this.inv = inv;
            for (int i = 0; i < SIZE; i++) {
                int slot = i;
                addSlot(new Slot(inv, i, 53 + i * 18, 26) {
                    @Override
                    public boolean canInsert(ItemStack stack) {
                        return fits(slot, stack);
                    }

                    @Override
                    public int getMaxItemCount() {
                        return 1;
                    }
                });
            }
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    addSlot(new Slot(pinv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(pinv, c, 8 + c * 18, 142));
            }
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int index) {
            Slot slot = slots.get(index);
            if (!slot.hasStack()) {
                return ItemStack.EMPTY;
            }
            ItemStack s = slot.getStack();
            ItemStack copy = s.copy();
            if (index < SIZE) {
                if (!insertItem(s, SIZE, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                boolean moved = false;
                for (int i = 0; i < SIZE && !moved; i++) {
                    if (!slots.get(i).hasStack() && fits(i, s)) {
                        moved = insertItem(s, i, i + 1, false);
                    }
                }
                if (!moved) {
                    return ItemStack.EMPTY;
                }
            }
            if (s.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
            return copy;
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return true;
        }
    }

    /** An accessory: use it to wear it in a free matching slot. */
    public static class AccessoryItem extends Item {
        public final EquipmentContent.Accessory def;

        public AccessoryItem(EquipmentContent.Accessory def, Settings settings) {
            super(settings);
            this.def = def;
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack s = user.getStackInHand(hand);
            if (world.isClient || !(user instanceof ServerPlayerEntity sp)) {
                return TypedActionResult.success(s, world.isClient);
            }
            Data d = of(sp);
            for (int i = 0; i < SIZE; i++) {
                if (d.stacks[i].isEmpty() && fits(i, s)) {
                    d.stacks[i] = s.copyWithCount(1);
                    s.decrement(1);
                    refresh(sp);
                    world.playSound(null, sp.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_GOLD.value(), SoundCategory.PLAYERS, 1f, 1.2f);
                    sp.sendMessage(Text.literal("You wear the " + getName().getString() + ".").formatted(Formatting.GRAY), true);
                    dev.underdeep.guide.Advancements.event(sp, "accessory_equipped", Registries.ITEM.getId(this).getPath());
                    return TypedActionResult.success(s);
                }
            }
            sp.sendMessage(Text.literal("No free " + def.slot() + " slot: open your accessories to swap.").formatted(Formatting.GRAY), true);
            return TypedActionResult.fail(s);
        }
    }
}
