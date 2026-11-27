package dev.underdeep.explore;

import dev.underdeep.guide.Advancements;
import dev.underdeep.registry.Reg;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 9 items: vault keys (bound to one site), lore fragments, surveyor's charts, encrusted relics (identified by
 * scholars), ancient coins and star shards (valuables), the utility artifacts and the relic décor blocks.
 * Accessory and weapon artifacts are Stage 8 equipment (EquipmentContent.ARTIFACTS).
 */
public final class ExploreItems {
    private ExploreItems() {
    }

    public static Item VAULT_KEY, LORE_FRAGMENT, SURVEYORS_CHART, ENCRUSTED_RELIC, ANCIENT_COIN, STAR_SHARD, TUNING_FORK, BREATH_SHELL, HOMING_STONE,
            SOUNDING_HORN;
    public static Block WARDING_BELL;
    public static final Map<String, Block> RELIC_BLOCKS = new LinkedHashMap<>();
    public static final List<String> RELICS = List.of("wright_bust", "lantern_head", "choir_conch", "ember_crown", "seed_urn", "star_globe", "silent_mask",
            "gilded_idol", "league_astrolabe", "petrified_effigy");

    public static void init() {
        VAULT_KEY = Reg.item("vault_key", new KeyItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));
        LORE_FRAGMENT = Reg.item("lore_fragment", new FragmentItem(new Item.Settings().maxCount(1)));
        SURVEYORS_CHART = Reg.item("surveyors_chart", new ChartItem(new Item.Settings().maxCount(1)));
        ENCRUSTED_RELIC = Reg.item("encrusted_relic", new Item(new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON)));
        ANCIENT_COIN = Reg.item("ancient_coin", new Item(new Item.Settings()));
        STAR_SHARD = Reg.item("star_shard", new Item(new Item.Settings().rarity(Rarity.UNCOMMON)));
        TUNING_FORK = Reg.item("tuning_fork", new ArtifactTool(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof(), "fork"));
        BREATH_SHELL = Reg.item("breath_shell", new ArtifactTool(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof(), "breath"));
        HOMING_STONE = Reg.item("homing_stone", new ArtifactTool(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof(), "homing"));
        SOUNDING_HORN = Reg.item("sounding_horn", new ArtifactTool(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof(), "horn"));
        WARDING_BELL = Reg.block("warding_bell", new WardBlock(AbstractBlock.Settings.copy(Blocks.BELL).nonOpaque().strength(3f)));
        Reg.item("warding_bell", new BlockItem(WARDING_BELL, new Item.Settings().rarity(Rarity.EPIC).fireproof()));
        Reg.CUTOUT.add(WARDING_BELL);
        for (String r : RELICS) {
            Block b = Reg.block(r, new RelicBlock(AbstractBlock.Settings.copy(Blocks.DECORATED_POT).nonOpaque().strength(1f)));
            Reg.item(r, new BlockItem(b, new Item.Settings().maxCount(16).rarity(Rarity.RARE)));
            Reg.CUTOUT.add(b);
            RELIC_BLOCKS.put(r, b);
        }
        dev.underdeep.guide.Uses.add(ANCIENT_COIN, "valuable: scholars, cartographers and merchants buy ancient coins");
        dev.underdeep.guide.Uses.add(ENCRUSTED_RELIC, "a scholar or cartographer can identify it (3 emeralds)");
        dev.underdeep.guide.Uses.add(VAULT_KEY, "opens the lock of the vault it was found in");
        dev.underdeep.guide.Uses.add(LORE_FRAGMENT, "read it (use) to record it in your journal");
        dev.underdeep.guide.Uses.add(SURVEYORS_CHART, "use it to mark the charted place in your journal; can be shared");
        for (Block b : RELIC_BLOCKS.values()) {
            dev.underdeep.guide.Uses.add(b.asItem(), "relic: display it, or sell it to a scholar");
        }
    }

    // ------------------------------------------------------------------ keys

    public static ItemStack key(Site s) {
        ItemStack k = new ItemStack(VAULT_KEY);
        NbtCompound n = new NbtCompound();
        n.putLong("site", s.id());
        k.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        return k;
    }

    public static long keySite(ItemStack s) {
        NbtComponent c = s.get(DataComponentTypes.CUSTOM_DATA);
        return c == null ? Long.MIN_VALUE : c.copyNbt().getLong("site");
    }

    public static class KeyItem extends Item {
        public KeyItem(Settings s) {
            super(s);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext ctx, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.literal("Cut for one particular lock.").formatted(Formatting.GRAY));
        }
    }

    // ------------------------------------------------------------------ lore fragments

    public static ItemStack fragment(String record) {
        ItemStack k = new ItemStack(LORE_FRAGMENT);
        NbtCompound n = new NbtCompound();
        n.putString("record", record);
        k.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        return k;
    }

    static String record(ItemStack s) {
        NbtComponent c = s.get(DataComponentTypes.CUSTOM_DATA);
        return c == null ? "" : c.copyNbt().getString("record");
    }

    public static class FragmentItem extends Item {
        public FragmentItem(Settings s) {
            super(s);
        }

        @Override
        public Text getName(ItemStack stack) {
            ExploreContent.Record r = ExploreContent.RECORDS.get(record(stack));
            return r == null ? super.getName(stack) : Text.literal(r.title());
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack s = user.getStackInHand(hand);
            if (user instanceof ServerPlayerEntity p) {
                String id = record(s);
                if (id.isEmpty()) {
                    // an unwritten fragment from loot: it becomes a record suited to where it was found
                    id = Lore.pickFor(p);
                    s.set(DataComponentTypes.CUSTOM_DATA, fragment(id).get(DataComponentTypes.CUSTOM_DATA));
                }
                Lore.read(p, id);
            }
            return TypedActionResult.success(s, world.isClient);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext ctx, List<Text> tooltip, TooltipType type) {
            ExploreContent.Record r = ExploreContent.RECORDS.get(record(stack));
            tooltip.add(Text.literal(r == null ? "Faded writing. Use to read." : "Written by " + r.voice() + ". Use to read.").formatted(Formatting.GRAY));
        }
    }

    // ------------------------------------------------------------------ charts

    public static ItemStack chart(Site s, String by) {
        ItemStack k = new ItemStack(SURVEYORS_CHART);
        NbtCompound n = new NbtCompound();
        n.putLong("site", s.id());
        n.putString("name", s.family().name());
        n.putString("by", by);
        k.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        return k;
    }

    public static class ChartItem extends Item {
        public ChartItem(Settings s) {
            super(s);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack s = user.getStackInHand(hand);
            if (user instanceof ServerPlayerEntity p && p.getWorld() instanceof ServerWorld w) {
                NbtComponent c = s.get(DataComponentTypes.CUSTOM_DATA);
                ExplorationMap map = Explorations.of(w);
                Site site = c == null || map == null ? null : map.byId(c.copyNbt().getLong("site"));
                if (site == null) {
                    p.sendMessage(Text.literal("The chart shows passages you cannot match to anything here.").formatted(Formatting.GRAY), true);
                } else {
                    Discovery.lead(p, site, "chart");
                    p.sendMessage(Text.literal(site.family().name() + ": " + Discovery.directions(p, site, true)).formatted(Formatting.AQUA), false);
                }
            }
            return TypedActionResult.success(s, world.isClient);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext ctx, List<Text> tooltip, TooltipType type) {
            NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (c != null && c.copyNbt().contains("name")) {
                tooltip.add(Text.literal("Charts the way to: " + c.copyNbt().getString("name")).formatted(Formatting.AQUA));
                tooltip.add(Text.literal("Drawn by " + c.copyNbt().getString("by") + ". Use to copy it into your journal.").formatted(Formatting.GRAY));
            }
        }
    }

    // ------------------------------------------------------------------ utility artifacts

    public static class ArtifactTool extends Item {
        final String kind;

        public ArtifactTool(Settings s, String kind) {
            super(s);
            this.kind = kind;
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack s = user.getStackInHand(hand);
            if (!(user instanceof ServerPlayerEntity p) || !(world instanceof ServerWorld w)) {
                return TypedActionResult.success(s, world.isClient);
            }
            int cooldown = 20 * 20;
            switch (kind) {
                case "fork" -> {
                    // resonates with hollow places nearby: sealed doors and hidden rooms within 24 blocks
                    List<BlockPos> hits = Discovery.hollowsNear(w, p.getBlockPos(), 24);
                    w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1f, hits.isEmpty() ? 0.6f : 1.4f);
                    if (hits.isEmpty()) {
                        p.sendMessage(Text.literal("The fork hums evenly. Nothing hollow answers nearby.").formatted(Formatting.GRAY), true);
                    } else {
                        BlockPos h = hits.get(0);
                        Vec3d from = p.getEyePos(), to = h.toCenterPos();
                        for (int i = 1; i <= 12; i++) {
                            Vec3d v = from.lerp(to, i / 12.0);
                            w.spawnParticles(p, ParticleTypes.ENCHANT, true, v.x, v.y, v.z, 3, 0.05, 0.05, 0.05, 0);
                        }
                        p.sendMessage(Text.literal("The fork rings sharply toward something hollow, " + (int) Math.sqrt(h.getSquaredDistance(p.getPos()))
                                + " blocks away.").formatted(Formatting.LIGHT_PURPLE), true);
                    }
                    cooldown = 20 * 30;
                }
                case "breath" -> {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 20 * 90, 0));
                    w.playSound(null, p.getBlockPos(), SoundEvents.ITEM_BUCKET_FILL, SoundCategory.PLAYERS, 0.8f, 1.3f);
                    cooldown = 20 * 180;
                }
                case "homing" -> {
                    NbtComponent c = s.get(DataComponentTypes.CUSTOM_DATA);
                    if (p.isSneaking() || c == null) {
                        NbtCompound n = new NbtCompound();
                        n.putLong("pos", p.getBlockPos().asLong());
                        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
                        p.sendMessage(Text.literal("The stone warms in your hand. It will remember this place.").formatted(Formatting.AQUA), true);
                    } else {
                        BlockPos home = BlockPos.fromLong(c.copyNbt().getLong("pos"));
                        p.sendMessage(Text.literal(Discovery.bearing(p, home)).formatted(Formatting.AQUA), true);
                    }
                    cooldown = 20;
                }
                case "horn" -> {
                    w.playSound(null, p.getBlockPos(), SoundEvents.GOAT_HORN_SOUNDS.get(0).value(), SoundCategory.PLAYERS, 2f, 0.7f);
                    int n = 0;
                    for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(24), e -> e instanceof HostileEntity)) {
                        e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 10, 0));
                        n++;
                    }
                    p.sendMessage(Text.literal(n == 0 ? "The horn's echo returns clean." : "The echo stumbles over " + n + " lurking shapes.")
                            .formatted(Formatting.YELLOW), true);
                    cooldown = 20 * 60;
                }
                default -> {
                }
            }
            p.getItemCooldownManager().set(this, cooldown);
            Advancements.event(p, "artifact_used", kind);
            return TypedActionResult.success(s);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext ctx, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.translatable("tooltip.underdeep." + switch (kind) {
                case "fork" -> "tuning_fork";
                case "breath" -> "breath_shell";
                case "homing" -> "homing_stone";
                default -> "sounding_horn";
            }).formatted(Formatting.GRAY));
        }
    }

    /** A warding bell: hostile creatures do not spawn naturally within 16 blocks of it (a safe expedition camp). */
    public static class WardBlock extends Block {
        static final VoxelShape SHAPE = Block.createCuboidShape(4, 0, 4, 12, 13, 12);

        public WardBlock(Settings s) {
            super(s);
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
            return SHAPE;
        }

        @Override
        protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState old, boolean notify) {
            if (world instanceof ServerWorld w && !old.isOf(this)) {
                Explorations.wards(w).add(pos.asLong());
                ExplorationState.get(w.getServer()).markDirty();
            }
        }

        @Override
        protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
            if (world instanceof ServerWorld w && !next.isOf(this)) {
                Explorations.wards(w).remove(pos.asLong());
                ExplorationState.get(w.getServer()).markDirty();
            }
            super.onStateReplaced(state, world, pos, next, moved);
        }
    }

    public static class RelicBlock extends Block {
        static final VoxelShape SHAPE = Block.createCuboidShape(3, 0, 3, 13, 13, 13);

        public RelicBlock(Settings s) {
            super(s);
            setDefaultState(getDefaultState().with(ExploreBlocks.FACING, net.minecraft.util.math.Direction.NORTH));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(ExploreBlocks.FACING);
        }

        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            return getDefaultState().with(ExploreBlocks.FACING, ctx.getHorizontalPlayerFacing().getOpposite());
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
            return SHAPE;
        }
    }

    static List<String> ids() {
        return new ArrayList<>(RELICS);
    }
}
