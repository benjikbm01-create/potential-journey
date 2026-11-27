package dev.underdeep.equipment;

import com.mojang.serialization.Codec;
import dev.underdeep.Underdeep;
import dev.underdeep.registry.Reg;
import dev.underdeep.survival.Survival;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.FabricElytraItem;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stage 8 tools and exploration gear. Multi-block actions break blocks through the player's own interaction manager
 * ({@code tryBreakBlock}), so property protection, tool durability, drops and statistics behave exactly as if the
 * player had broken each block; a recursion guard prevents chains.
 */
public final class EquipmentTools {
    private EquipmentTools() {
    }

    private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);
    public static final int FELL_LIMIT = 48;

    static void init() {
        PlaceData.init();
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (BUSY.get() || !(player instanceof ServerPlayerEntity sp) || player.isSneaking()) {
                return;
            }
            ItemStack tool = player.getMainHandStack();
            EquipmentContent.Tool def = EquipmentItems.TOOL_DEF.get(tool.getItem());
            if (def == null) {
                return;
            }
            BUSY.set(true);
            try {
                if (def.special().equals("area")) {
                    area(sp, (ServerWorld) world, pos, state, tool);
                } else if (def.special().equals("fell") && state.isIn(BlockTags.LOGS)) {
                    fell(sp, (ServerWorld) world, pos, state);
                }
            } finally {
                BUSY.set(false);
            }
        });
    }

    // ------------------------------------------------------------------ tool items

    static Item create(EquipmentContent.Tool t, EquipmentItems.Mat m) {
        float dmg = switch (t.kind()) {
            case "axe", "lumber_axe" -> 5f;
            case "hammer" -> 4f;
            case "shovel", "excavator" -> 1.5f;
            case "hoe", "sickle" -> 0f;
            default -> 1f;
        };
        float speed = switch (t.kind()) {
            case "axe", "lumber_axe" -> -3.1f;
            case "hammer" -> -3.3f;
            case "excavator" -> -3.2f;
            case "hoe", "sickle" -> -1f;
            case "shovel" -> -3f;
            default -> -2.8f;
        };
        AttributeModifiersComponent attrs = MiningToolItem.createAttributeModifiers(m, dmg, speed);
        if (t.special().equals("aquatic")) {
            AttributeModifiersComponent.Builder b = AttributeModifiersComponent.builder();
            attrs.modifiers().forEach(e -> b.add(e.attribute(), e.modifier(), e.slot()));
            b.add(EntityAttributes.PLAYER_SUBMERGED_MINING_SPEED, new EntityAttributeModifier(Reg.id("tool_aquatic"), 0.8,
                    EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
            attrs = b.build();
        }
        Item.Settings s = new Item.Settings().attributeModifiers(attrs);
        if (t.material().equals("umbralite")) {
            s.fireproof();
        }
        return switch (t.kind()) {
            case "pickaxe", "hammer" -> new PickaxeItem(m, s);
            case "prospector" -> new ProspectorItem(m, s);
            case "axe", "lumber_axe" -> new AxeItem(m, s);
            case "shovel", "excavator" -> new ShovelItem(m, s);
            case "sickle" -> new SickleItem(m, s);
            default -> new HoeItem(m, s);
        };
    }

    /** Hammer / excavator: break the 3x3 plane around the mined block that faces the player. */
    static void area(ServerPlayerEntity p, ServerWorld w, BlockPos center, BlockState centerState, ItemStack tool) {
        float hardness = centerState.getHardness(w, center);
        Direction.Axis axis = Math.abs(p.getPitch()) > 50 ? Direction.Axis.Y : p.getHorizontalFacing().getAxis();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                BlockPos q = switch (axis) {
                    case Y -> center.add(a, 0, b);
                    case X -> center.add(0, a, b);
                    default -> center.add(a, b, 0);
                };
                BlockState s = w.getBlockState(q);
                if (s.isAir() || s.hasBlockEntity() || !tool.isSuitableFor(s) || s.getHardness(w, q) < 0 || s.getHardness(w, q) > hardness + 1.5f) {
                    continue;
                }
                if (p.getMainHandStack() != tool || tool.isEmpty()) {
                    return;
                }
                p.interactionManager.tryBreakBlock(q);
            }
        }
    }

    /** Lumber axe: fell a small natural tree (connected logs of the same kind touching leaves, at most 48). */
    static void fell(ServerPlayerEntity p, ServerWorld w, BlockPos origin, BlockState state) {
        Block log = state.getBlock();
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> logs = new ArrayList<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(origin);
        seen.add(origin);
        boolean leaves = false;
        while (!q.isEmpty()) {
            BlockPos c = q.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = c.add(dx, dy, dz);
                        if (!seen.add(n)) {
                            continue;
                        }
                        BlockState s = w.getBlockState(n);
                        if (s.isIn(BlockTags.LEAVES)) {
                            leaves = true;
                        } else if (s.isOf(log)) {
                            logs.add(n);
                            if (logs.size() > FELL_LIMIT) {
                                return; // too big: a landmark tree, leave it to be cut by hand
                            }
                            q.add(n);
                        }
                    }
                }
            }
        }
        if (!leaves) {
            return; // not a natural tree (a build)
        }
        logs.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        for (BlockPos l : logs) {
            if (p.getMainHandStack().isEmpty()) {
                return;
            }
            p.interactionManager.tryBreakBlock(l);
        }
    }

    /** Sickle: harvests and replants mature crops in a 3x3 area; otherwise behaves like a hoe. */
    public static class SickleItem extends HoeItem {
        public SickleItem(EquipmentItems.Mat m, Settings s) {
            super(m, s);
        }

        @Override
        public ActionResult useOnBlock(ItemUsageContext ctx) {
            World w = ctx.getWorld();
            BlockState st = w.getBlockState(ctx.getBlockPos());
            if (!(st.getBlock() instanceof CropBlock)) {
                return super.useOnBlock(ctx);
            }
            if (w instanceof ServerWorld sw && ctx.getPlayer() != null) {
                int n = 0;
                for (BlockPos q : BlockPos.iterate(ctx.getBlockPos().add(-1, 0, -1), ctx.getBlockPos().add(1, 0, 1))) {
                    BlockState s = sw.getBlockState(q);
                    if (s.getBlock() instanceof CropBlock crop && crop.isMature(s) && !Registries.BLOCK.getId(s.getBlock()).getPath().startsWith("relic")
                            && sw.canPlayerModifyAt(ctx.getPlayer(), q) && Survival.harvest(sw, q.toImmutable(), s, crop, ctx.getPlayer()) != null) {
                        n++;
                    }
                }
                if (n > 0) {
                    ctx.getStack().damage(n, ctx.getPlayer(), LivingEntity.getSlotForHand(ctx.getHand()));
                }
            }
            return ActionResult.success(w.isClient);
        }
    }

    /** Prospector's pick: senses which ores are within 8 blocks and roughly where (cooldown, durability). */
    public static class ProspectorItem extends PickaxeItem {
        public ProspectorItem(EquipmentItems.Mat m, Settings s) {
            super(m, s);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack s = user.getStackInHand(hand);
            if (!(world instanceof ServerWorld sw)) {
                return TypedActionResult.success(s, true);
            }
            Map<String, int[]> found = new LinkedHashMap<>();
            BlockPos c = user.getBlockPos();
            for (BlockPos q : BlockPos.iterate(c.add(-8, -8, -8), c.add(8, 8, 8))) {
                BlockState b = sw.getBlockState(q);
                String id = Registries.BLOCK.getId(b.getBlock()).getPath();
                if (id.endsWith("_ore") || id.equals("ancient_debris") || id.startsWith("budding_")) {
                    String name = b.getBlock().getName().getString();
                    int[] a = found.computeIfAbsent(name, k -> new int[4]);
                    a[0]++;
                    a[1] += q.getX() - c.getX();
                    a[2] += q.getY() - c.getY();
                    a[3] += q.getZ() - c.getZ();
                }
            }
            StringBuilder sb = new StringBuilder();
            if (found.isEmpty()) {
                sb.append("You sense no ore within eight blocks.");
            } else {
                sb.append("You sense: ");
                found.entrySet().stream().sorted((x, y) -> y.getValue()[0] - x.getValue()[0]).limit(5).forEach(e -> {
                    int[] a = e.getValue();
                    sb.append(e.getKey()).append(" (").append(a[0] > 12 ? "plenty" : a[0] > 4 ? "some" : "a little").append(", ")
                            .append(direction(a[1], a[2], a[3])).append(") ");
                });
            }
            user.sendMessage(Text.literal(sb.toString().trim()).formatted(Formatting.GOLD), false);
            user.getItemCooldownManager().set(this, 200);
            s.damage(1, user, LivingEntity.getSlotForHand(hand));
            world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1f, 0.8f);
            dev.underdeep.guide.Advancements.event((ServerPlayerEntity) user, "prospected", "");
            return TypedActionResult.success(s);
        }
    }

    static String direction(int dx, int dy, int dz) {
        int ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        if (ay >= ax && ay >= az) {
            return dy < 0 ? "below" : "above";
        }
        if (ax >= az) {
            return dx < 0 ? "west" : "east";
        }
        return dz < 0 ? "north" : "south";
    }

    // ------------------------------------------------------------------ utility items

    static Item utility(String id, String kind) {
        return switch (kind) {
            case "grapple" -> new GrappleItem(new Item.Settings().maxDamage(160));
            case "glider" -> new GliderItem(new Item.Settings().maxDamage(240));
            case "gauge" -> new GaugeItem(new Item.Settings().maxCount(1));
            case "compass" -> new CompassItem(new Item.Settings().maxCount(1));
            case "pack" -> new PackItem(new Item.Settings().maxCount(1), 2, "any");
            case "pouch" -> new PackItem(new Item.Settings().maxCount(1), 1, "herbs");
            case "satchel" -> new PackItem(new Item.Settings().maxCount(1), 1, "ores");
            case "kit" -> new RepairKitItem(new Item.Settings().maxCount(16));
            default -> new Item(new Item.Settings());
        };
    }

    /** Grappling hook: pull yourself toward a block up to 16 blocks away; slow fall afterwards. */
    public static class GrappleItem extends Item {
        public GrappleItem(Settings s) {
            super(s);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack s = user.getStackInHand(hand);
            HitResult hit = user.raycast(16, 0, false);
            if (hit.getType() != HitResult.Type.BLOCK) {
                return TypedActionResult.fail(s);
            }
            if (!world.isClient) {
                Vec3d to = hit.getPos().subtract(user.getEyePos());
                double dist = to.length();
                Vec3d v = to.normalize().multiply(Math.min(2.2, 0.6 + dist * 0.12)).add(0, 0.25, 0);
                user.setVelocity(v);
                user.velocityModified = true;
                user.fallDistance = 0;
                user.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 50, 0, false, false, true));
                user.getItemCooldownManager().set(this, 30);
                s.damage(1, user, LivingEntity.getSlotForHand(hand));
                world.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 1f, 0.7f);
                if (user instanceof ServerPlayerEntity sp) {
                    dev.underdeep.guide.Advancements.event(sp, "grappled", "");
                }
            }
            return TypedActionResult.success(s, world.isClient);
        }
    }

    /** Cave glider: an elytra-like chest item for gliding down caverns. */
    public static class GliderItem extends ElytraItem implements FabricElytraItem {
        public GliderItem(Settings s) {
            super(s);
        }

        @Override
        public boolean canRepair(ItemStack stack, ItemStack ingredient) {
            return ingredient.isOf(EquipmentItems.get("silk_cloth"));
        }
    }

    /** Depth gauge: depth, region and your nearest waymarker. */
    public static class GaugeItem extends Item {
        public GaugeItem(Settings s) {
            super(s);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            if (!world.isClient) {
                StringBuilder sb = new StringBuilder(EquipmentEffects.locate(user));
                PlaceData.Place near = PlaceData.nearest(user);
                if (near != null) {
                    sb.append(". Nearest waymarker: ").append(near.name()).append(" ").append(PlaceData.describe(user, near.pos()));
                }
                user.sendMessage(Text.literal(sb.toString()).formatted(Formatting.AQUA), false);
            }
            return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
        }
    }

    /** Delver's compass: sneak-use to choose a target (your waymarkers, your bed, world spawn); use to read the way. */
    public static class CompassItem extends Item {
        public CompassItem(Settings s) {
            super(s);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack s = user.getStackInHand(hand);
            if (world.isClient || !(user instanceof ServerPlayerEntity sp)) {
                return TypedActionResult.success(s, true);
            }
            List<PlaceData.Place> targets = PlaceData.targets(sp);
            NbtCompound tag = s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
            int i = tag.getInt("target");
            if (user.isSneaking()) {
                i = (i + 1) % Math.max(1, targets.size());
                tag.putInt("target", i);
                s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
            }
            if (targets.isEmpty()) {
                sp.sendMessage(Text.literal("No places to point to yet: set up a waymarker.").formatted(Formatting.GRAY), true);
            } else {
                PlaceData.Place p = targets.get(Math.min(i, targets.size() - 1));
                sp.sendMessage(Text.literal(p.name() + ": " + PlaceData.describe(sp, p.pos())).formatted(Formatting.AQUA), true);
            }
            return TypedActionResult.success(s);
        }
    }

    /** Repair kit: hold it in your main hand and the damaged item in your other hand, then use. */
    public static class RepairKitItem extends Item {
        public RepairKitItem(Settings s) {
            super(s);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack kit = user.getStackInHand(hand);
            ItemStack target = user.getStackInHand(hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);
            if (!target.isDamageable() || target.getDamage() == 0) {
                if (!world.isClient) {
                    user.sendMessage(Text.literal("Hold damaged equipment in your other hand.").formatted(Formatting.GRAY), true);
                }
                return TypedActionResult.fail(kit);
            }
            if (!world.isClient) {
                Artisan.repairBy(target, 0.25);
                kit.decrementUnlessCreative(1, user);
                world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.PLAYERS, 0.8f, 1.2f);
                if (user instanceof ServerPlayerEntity sp) {
                    dev.underdeep.guide.Advancements.event(sp, "repaired", "kit");
                }
            }
            return TypedActionResult.success(kit, world.isClient);
        }
    }

    // ------------------------------------------------------------------ packs

    /** Backpack, herb pouch, ore satchel: a container stored on the item (no nesting). */
    public static class PackItem extends Item {
        public final int rows;
        public final String filter;

        public PackItem(Settings s, int rows, String filter) {
            super(s.component(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT));
            this.rows = rows;
            this.filter = filter;
        }

        public boolean accepts(ItemStack s) {
            if (s.getItem() instanceof PackItem || (s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock)
                    || s.isOf(Items.BUNDLE) || s.contains(DataComponentTypes.CONTAINER) || s.contains(DataComponentTypes.BUNDLE_CONTENTS)) {
                return false;
            }
            String id = Registries.ITEM.getId(s.getItem()).getPath();
            return switch (filter) {
                case "herbs" -> s.getItem() instanceof BlockItem bi2 && (bi2.getBlock() instanceof net.minecraft.block.PlantBlock)
                        || id.contains("seed") || id.contains("sprig") || id.contains("leaf") || id.contains("petal") || id.contains("root")
                        || id.contains("blossom") || id.contains("frond") || id.contains("cap") || id.contains("berr") || id.contains("fung");
                case "ores" -> id.contains("ore") || id.startsWith("raw_") || id.startsWith("crushed_") || id.contains("gem")
                        || List.of("cinnabar", "sulfur", "niter", "emberite", "cryolite", "malachite", "galvanite", "prismatic_opal", "jasper",
                        "fossil_fragment", "pearl", "glimmer_shard", "coal", "diamond", "emerald", "lapis_lazuli", "redstone", "amethyst_shard",
                        "quartz", "flint", "cobblestone", "cobbled_deepslate", "gravel", "sand", "clay_ball").contains(id);
                default -> true;
            };
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack s = user.getStackInHand(hand);
            if (!world.isClient && user instanceof ServerPlayerEntity sp && hand == Hand.MAIN_HAND) {
                int slot = sp.getInventory().selectedSlot;
                PackInventory inv = new PackInventory(sp, s, slot, this);
                sp.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, pinv, pl) -> new PackHandler(syncId, pinv, inv, rows, slot), s.getName()));
            }
            return TypedActionResult.success(s, world.isClient);
        }
    }

    /** The pack's contents, written back to the item on every change; only usable while the same stack is still held. */
    static final class PackInventory extends SimpleInventory {
        final ServerPlayerEntity p;
        final ItemStack pack;
        final int slot;
        final PackItem kind;

        PackInventory(ServerPlayerEntity p, ItemStack pack, int slot, PackItem kind) {
            super(kind.rows * 9);
            this.p = p;
            this.pack = pack;
            this.slot = slot;
            this.kind = kind;
            DefaultedList<ItemStack> l = DefaultedList.ofSize(size(), ItemStack.EMPTY);
            pack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).copyTo(l);
            for (int i = 0; i < l.size(); i++) {
                super.setStack(i, l.get(i));
            }
        }

        @Override
        public void markDirty() {
            super.markDirty();
            if (canPlayerUse(p)) {
                List<ItemStack> l = new ArrayList<>();
                for (int i = 0; i < size(); i++) {
                    l.add(getStack(i));
                }
                pack.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(l));
            }
        }

        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return player == p && p.getInventory().getStack(slot) == pack && p.isAlive();
        }

        @Override
        public boolean isValid(int s, ItemStack stack) {
            return kind.accepts(stack);
        }
    }

    /** Chest-style screen over a pack: the pack's own slot is locked while it is open (no moving, swapping or dropping it). */
    static final class PackHandler extends GenericContainerScreenHandler {
        final int lockedHotbar;

        PackHandler(int syncId, PlayerInventory pinv, PackInventory inv, int rows, int hotbar) {
            super(rows == 1 ? ScreenHandlerType.GENERIC_9X1 : ScreenHandlerType.GENERIC_9X2, syncId, pinv, inv, rows);
            this.lockedHotbar = hotbar;
            for (int i = 0; i < slots.size(); i++) {
                Slot old = slots.get(i);
                Slot ns;
                if (i < rows * 9) {
                    ns = new Slot(inv, old.getIndex(), old.x, old.y) {
                        @Override
                        public boolean canInsert(ItemStack stack) {
                            return inv.isValid(getIndex(), stack);
                        }
                    };
                } else if (old.inventory == pinv && old.getIndex() == hotbar) {
                    ns = new Slot(pinv, old.getIndex(), old.x, old.y) {
                        @Override
                        public boolean canTakeItems(PlayerEntity playerEntity) {
                            return false;
                        }

                        @Override
                        public boolean canInsert(ItemStack stack) {
                            return false;
                        }
                    };
                } else {
                    continue;
                }
                ns.id = old.id;
                slots.set(i, ns);
            }
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (actionType == SlotActionType.SWAP && button == lockedHotbar) {
                return;
            }
            super.onSlotClick(slotIndex, button, actionType, player);
        }
    }

    // ------------------------------------------------------------------ rope and markers

    /** Rope coil: use on the side of a block to hang a rope ladder down that face (up to 8 rungs per coil). */
    public static class RopeCoilItem extends Item {
        public RopeCoilItem(Settings s) {
            super(s);
        }

        @Override
        public ActionResult useOnBlock(ItemUsageContext ctx) {
            Direction face = ctx.getSide();
            World w = ctx.getWorld();
            BlockPos start = w.getBlockState(ctx.getBlockPos()).isOf(EquipmentItems.ROPE_LADDER) ? bottom(w, ctx.getBlockPos()) : ctx.getBlockPos().offset(face);
            BlockState ladder = w.getBlockState(ctx.getBlockPos()).isOf(EquipmentItems.ROPE_LADDER) ? w.getBlockState(ctx.getBlockPos())
                    : face.getAxis().isHorizontal() ? EquipmentItems.ROPE_LADDER.getDefaultState().with(LadderBlock.FACING, face) : null;
            if (ladder == null) {
                return ActionResult.PASS;
            }
            if (w.isClient) {
                return ActionResult.SUCCESS;
            }
            int placed = 0;
            BlockPos p = start;
            while (placed < 8 && p.getY() > w.getBottomY() && (w.getBlockState(p).isAir() || w.getBlockState(p).isReplaceable())
                    && ladder.canPlaceAt(w, p) && (ctx.getPlayer() == null || w.canPlayerModifyAt(ctx.getPlayer(), p))) {
                w.setBlockState(p, ladder.with(LadderBlock.WATERLOGGED, w.getFluidState(p).isStill()), Block.NOTIFY_ALL);
                placed++;
                p = p.down();
            }
            if (placed > 0) {
                ctx.getStack().decrementUnlessCreative(1, ctx.getPlayer());
                w.playSound(null, start, SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.BLOCKS, 1f, 0.9f);
                return ActionResult.SUCCESS;
            }
            return ActionResult.FAIL;
        }

        static BlockPos bottom(World w, BlockPos p) {
            while (w.getBlockState(p).isOf(EquipmentItems.ROPE_LADDER)) {
                p = p.down();
            }
            return p;
        }
    }

    /** Waymarker: a light that records the place for whoever sets it up. */
    public static class WaymarkerBlock extends Block {
        static final VoxelShape SHAPE = Block.createCuboidShape(6, 0, 6, 10, 16, 10);

        public WaymarkerBlock(Settings s) {
            super(s);
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return SHAPE;
        }

        @Override
        public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            if (!world.isClient && placer instanceof ServerPlayerEntity sp) {
                String name = stack.contains(DataComponentTypes.CUSTOM_NAME) ? stack.getName().getString()
                        : "Marker " + (PlaceData.of(sp).places.size() + 1) + " (" + EquipmentEffects.locate(sp).replaceAll("^Depth y=-?\\d+, ", "")
                        .replaceAll(", facing.*$", "") + ")";
                PlaceData.add(sp, name, pos);
                sp.sendMessage(Text.literal("Waymarker set: " + name).formatted(Formatting.AQUA), true);
                dev.underdeep.guide.Advancements.event(sp, "waymarker", "");
            }
        }

        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            return getDefaultState();
        }
    }

    /** Per-player places (waymarkers): persistent attachment (kept on death). */
    public static final class PlaceData {
        public record Place(String name, BlockPos pos, String dim) {
        }

        public final List<Place> places = new ArrayList<>();
        static AttachmentType<PlaceData> TYPE;
        static final Codec<PlaceData> CODEC = NbtCompound.CODEC.xmap(n -> {
            PlaceData d = new PlaceData();
            for (NbtElement e : n.getList("places", NbtElement.COMPOUND_TYPE)) {
                NbtCompound c = (NbtCompound) e;
                d.places.add(new Place(c.getString("name"), BlockPos.fromLong(c.getLong("pos")), c.getString("dim")));
            }
            return d;
        }, d -> {
            NbtCompound n = new NbtCompound();
            NbtList l = new NbtList();
            for (Place p : d.places) {
                NbtCompound c = new NbtCompound();
                c.putString("name", p.name());
                c.putLong("pos", p.pos().asLong());
                c.putString("dim", p.dim());
                l.add(c);
            }
            n.put("places", l);
            return n;
        });

        static void init() {
            TYPE = AttachmentRegistry.<PlaceData>builder().persistent(CODEC).copyOnDeath().initializer(PlaceData::new).buildAndRegister(Reg.id("places"));
        }

        public static PlaceData of(PlayerEntity p) {
            return p.getAttachedOrCreate(TYPE);
        }

        static void add(ServerPlayerEntity p, String name, BlockPos pos) {
            PlaceData d = of(p);
            d.places.removeIf(x -> x.pos().equals(pos));
            d.places.add(new Place(name, pos.toImmutable(), p.getWorld().getRegistryKey().getValue().toString()));
            while (d.places.size() > 32) {
                d.places.remove(0);
            }
        }

        static Place nearest(PlayerEntity p) {
            Place best = null;
            double bd = Double.MAX_VALUE;
            String dim = p.getWorld().getRegistryKey().getValue().toString();
            for (Place pl : of(p).places) {
                double d = pl.pos().getSquaredDistance(p.getPos());
                if (pl.dim().equals(dim) && d < bd) {
                    bd = d;
                    best = pl;
                }
            }
            return best;
        }

        /** Compass targets: live waymarkers, the respawn point, the world spawn. */
        static List<Place> targets(ServerPlayerEntity p) {
            List<Place> out = new ArrayList<>();
            String dim = p.getWorld().getRegistryKey().getValue().toString();
            PlaceData d = of(p);
            d.places.removeIf(pl -> pl.dim().equals(dim) && p.getServerWorld().isChunkLoaded(pl.pos()) && !p.getServerWorld().getBlockState(pl.pos())
                    .isOf(EquipmentItems.WAYMARKER));
            for (Place pl : d.places) {
                if (pl.dim().equals(dim)) {
                    out.add(pl);
                }
            }
            if (p.getSpawnPointPosition() != null) {
                out.add(new Place("Your bed", p.getSpawnPointPosition(), dim));
            }
            out.add(new Place("World spawn", p.getServerWorld().getSpawnPos(), dim));
            return out;
        }

        static String describe(PlayerEntity p, BlockPos target) {
            int dx = target.getX() - p.getBlockX(), dy = target.getY() - p.getBlockY(), dz = target.getZ() - p.getBlockZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            String h = dist < 3 ? "here" : compass(dx, dz);
            return String.format(Locale.ROOT, "%d blocks %s, %s", dist, h, dy > 3 ? dy + " above" : dy < -3 ? -dy + " below" : "same level");
        }

        static String compass(int dx, int dz) {
            double a = Math.toDegrees(Math.atan2(dx, -dz));
            String[] n = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
            return n[(int) Math.round(((a + 360) % 360) / 45.0) % 8];
        }
    }

    public static Identifier id(String s) {
        return Identifier.of(Underdeep.MOD_ID, s);
    }

    static boolean waterlogged(BlockState s) {
        return s.contains(Properties.WATERLOGGED) && s.get(Properties.WATERLOGGED);
    }
}
