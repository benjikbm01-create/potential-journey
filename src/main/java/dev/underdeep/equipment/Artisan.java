package dev.underdeep.equipment;

import com.mojang.serialization.MapCodec;
import dev.underdeep.registry.Reg;
import dev.underdeep.survival.SurvivalContent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Artisan's bench (Stage 8): put equipment in the first slot and an upgrade kit, a repair kit or the item's repair
 * material in the second; the result slot previews the outcome and taking it consumes the inputs (exactly once, like an
 * anvil). With the second slot empty, the Salvage button breaks the item down into part of its main material
 * (at most half of what it cost, less if it is worn). Enchantments and upgrades are kept by repair and upgrading,
 * and lost by salvaging.
 */
public final class Artisan {
    private Artisan() {
    }

    public static ScreenHandlerType<Handler> SCREEN;
    public static final double REPAIR_PER_UNIT = 0.25, SALVAGE_SHARE = 0.5;

    static void init() {
        SCREEN = Registry.register(Registries.SCREEN_HANDLER, Reg.id("artisan"),
                new ScreenHandlerType<>((syncId, inv) -> new Handler(syncId, inv, ScreenHandlerContext.EMPTY), FeatureFlags.VANILLA_FEATURES));
    }

    public static void repairBy(ItemStack s, double fraction) {
        int max = s.getMaxDamage();
        s.setDamage(Math.max(0, s.getDamage() - (int) Math.ceil(max * fraction)));
    }

    /** The outcome of the bench for these inputs, or EMPTY. */
    public static ItemStack result(ItemStack eq, ItemStack mod) {
        if (eq.isEmpty() || mod.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (mod.getItem() instanceof Upgrades.KitItem kit) {
            return Upgrades.reject(eq, kit.def) == null ? Upgrades.applied(eq, kit.def) : ItemStack.EMPTY;
        }
        if (eq.isDamageable() && eq.getDamage() > 0 && (mod.getItem() instanceof EquipmentTools.RepairKitItem || eq.getItem().canRepair(eq, mod))) {
            ItemStack out = eq.copy();
            repairBy(out, REPAIR_PER_UNIT);
            return out;
        }
        return ItemStack.EMPTY;
    }

    /** Why nothing happens (shown in the screen), or null. */
    @Nullable
    public static String explain(ItemStack eq, ItemStack mod) {
        if (eq.isEmpty()) {
            return "Place equipment on the left.";
        }
        if (mod.isEmpty()) {
            return salvage(eq).isEmpty() ? "Add an upgrade kit, a repair kit or repair material." : "Add a kit or material, or Salvage.";
        }
        if (mod.getItem() instanceof Upgrades.KitItem kit) {
            String r = Upgrades.reject(eq, kit.def);
            return r == null ? "Apply: " + kit.def.desc() : r;
        }
        if (!eq.isDamageable() || eq.getDamage() == 0) {
            return "Nothing to repair.";
        }
        if (mod.getItem() instanceof EquipmentTools.RepairKitItem || eq.getItem().canRepair(eq, mod)) {
            return "Repair a quarter of its durability.";
        }
        return "That material does not repair this.";
    }

    private static Map<Item, Object[]> mainMaterial;

    /** What salvaging returns: up to half the main material, scaled by remaining durability. */
    public static ItemStack salvage(ItemStack eq) {
        if (eq.isEmpty() || EquipmentItems.category(eq.getItem()) == null) {
            return ItemStack.EMPTY;
        }
        Object[] m = main(eq.getItem());
        if (m == null) {
            return ItemStack.EMPTY;
        }
        double cond = eq.isDamageable() ? 1.0 - (double) eq.getDamage() / Math.max(1, eq.getMaxDamage()) : 1.0;
        int n = (int) Math.floor((Integer) m[1] * SALVAGE_SHARE * cond);
        return n <= 0 ? ItemStack.EMPTY : new ItemStack((Item) m[0], n);
    }

    /** [material item, count] used by the item's own recipe (largest ingredient), from forge or crafting recipes. */
    @Nullable
    static Object[] main(Item item) {
        if (mainMaterial == null) {
            mainMaterial = new HashMap<>();
            for (SurvivalContent.Recipe r : SurvivalContent.recipes()) {
                Item out = r.resultItem();
                for (SurvivalContent.Ingredient i : r.ingredients()) {
                    if (!i.group()) {
                        Item it = Registries.ITEM.get(net.minecraft.util.Identifier.of(i.key()));
                        Object[] cur = mainMaterial.get(out);
                        if (cur == null || (Integer) cur[1] < i.count()) {
                            mainMaterial.put(out, new Object[]{it, i.count()});
                        }
                    }
                }
            }
        }
        return mainMaterial.get(item);
    }

    /** Fills the crafting-table part of the salvage table from the server's recipes (called once at server start). */
    public static void learnCraftingRecipes(net.minecraft.server.MinecraftServer server) {
        main(net.minecraft.item.Items.AIR);
        for (RecipeEntry<?> re : server.getRecipeManager().values()) {
            if (!(re.value() instanceof ShapedRecipe) && !(re.value() instanceof ShapelessRecipe)) {
                continue;
            }
            ItemStack out = re.value().getResult(server.getRegistryManager());
            if (out.isEmpty() || EquipmentItems.category(out.getItem()) == null || mainMaterial.containsKey(out.getItem())) {
                continue;
            }
            Map<Item, Integer> counts = new HashMap<>();
            for (Ingredient ing : re.value().getIngredients()) {
                ItemStack[] m = ing.getMatchingStacks();
                if (m.length == 1 && !m[0].isOf(net.minecraft.item.Items.STICK)) {
                    counts.merge(m[0].getItem(), 1, Integer::sum);
                }
            }
            counts.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e -> mainMaterial.put(out.getItem(), new Object[]{e.getKey(), e.getValue()}));
        }
    }

    // ------------------------------------------------------------------ screen handler

    public static class Handler extends ScreenHandler {
        public final Inventory input = new SimpleInventory(2) {
            @Override
            public void markDirty() {
                super.markDirty();
                onContentChanged(this);
            }
        };
        final CraftingResultInventory output = new CraftingResultInventory();
        final ScreenHandlerContext ctx;
        final PlayerEntity player;

        public Handler(int syncId, PlayerInventory pinv, ScreenHandlerContext ctx) {
            super(SCREEN, syncId);
            this.ctx = ctx;
            this.player = pinv.player;
            addSlot(new Slot(input, 0, 27, 35));
            addSlot(new Slot(input, 1, 76, 35));
            addSlot(new Slot(output, 0, 134, 35) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return false;
                }

                @Override
                public void onTakeItem(PlayerEntity p, ItemStack stack) {
                    ItemStack mod = input.getStack(1);
                    boolean upgrade = mod.getItem() instanceof Upgrades.KitItem;
                    input.setStack(0, ItemStack.EMPTY);
                    mod.decrement(1);
                    input.setStack(1, mod);
                    ctx.run((w, pos) -> w.playSound(null, pos, SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.BLOCKS, 1f, 1f));
                    if (p instanceof ServerPlayerEntity sp) {
                        dev.underdeep.guide.Advancements.event(sp, upgrade ? "upgraded" : "repaired", Registries.ITEM.getId(stack.getItem()).getPath());
                    }
                    super.onTakeItem(p, stack);
                }
            });
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
        public void onContentChanged(Inventory inventory) {
            if (inventory == input) {
                output.setStack(0, result(input.getStack(0), input.getStack(1)));
                sendContentUpdates();
            }
            super.onContentChanged(inventory);
        }

        /** Button 0: salvage the equipment (second slot must be empty). */
        @Override
        public boolean onButtonClick(PlayerEntity p, int id) {
            if (id != 0 || !input.getStack(1).isEmpty()) {
                return false;
            }
            ItemStack eq = input.getStack(0);
            ItemStack back = salvage(eq);
            if (back.isEmpty()) {
                return false;
            }
            input.setStack(0, ItemStack.EMPTY);
            input.setStack(1, back);
            ctx.run((w, pos) -> w.playSound(null, pos, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 1f, 1f));
            if (p instanceof ServerPlayerEntity sp) {
                dev.underdeep.guide.Advancements.event(sp, "salvaged", "");
            }
            return true;
        }

        @Override
        public ItemStack quickMove(PlayerEntity p, int index) {
            Slot slot = slots.get(index);
            if (!slot.hasStack()) {
                return ItemStack.EMPTY;
            }
            ItemStack s = slot.getStack();
            ItemStack copy = s.copy();
            if (index == 2) {
                if (!insertItem(s, 3, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
                slot.onTakeItem(p, s);
                slot.onQuickTransfer(s, copy);
            } else if (index < 2) {
                if (!insertItem(s, 3, slots.size(), false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                int target = EquipmentItems.category(s.getItem()) != null && input.getStack(0).isEmpty() ? 0 : 1;
                if (!insertItem(s, target, target + 1, false)) {
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
        public void onClosed(PlayerEntity p) {
            super.onClosed(p);
            ctx.run((w, pos) -> dropInventory(p, input));
        }

        @Override
        public boolean canUse(PlayerEntity p) {
            return ctx.get((w, pos) -> w.getBlockState(pos).isOf(EquipmentItems.ARTISAN_BENCH) && p.squaredDistanceTo(pos.toCenterPos()) <= 64, true);
        }
    }

    // ------------------------------------------------------------------ block

    public static class ArtisanBenchBlock extends HorizontalFacingBlock {
        static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 16, 16);

        public ArtisanBenchBlock(Settings s) {
            super(s);
            setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
        }

        @Override
        protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
            return createCodec(ArtisanBenchBlock::new);
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Nullable
        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return SHAPE;
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (!world.isClient) {
                player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, p) ->
                        new Handler(syncId, inv, ScreenHandlerContext.create(world, pos)), Text.translatable("container.underdeep.artisan")));
            }
            return ActionResult.success(world.isClient);
        }
    }
}
