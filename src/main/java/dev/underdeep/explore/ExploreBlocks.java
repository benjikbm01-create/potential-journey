package dev.underdeep.explore;

import dev.underdeep.registry.Reg;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 9 blocks: puzzle mechanisms, seals, traps, reward containers, inscriptions and relic décor. Mechanisms carry
 * no block entity: the server finds their puzzle/trap/cache from the position via the site layout, so they only
 * work where a site placed them (a mechanism moved elsewhere is inert). Seals are unbreakable; everything else in
 * a ruin can be mined, and mechanisms drop nothing (no free materials).
 */
public final class ExploreBlocks {
    private ExploreBlocks() {
    }

    public static final IntProperty SYMBOL = IntProperty.of("symbol", 0, 5);
    public static final IntProperty NOTCHES = IntProperty.of("notches", 0, 4);
    public static final IntProperty SIZE = IntProperty.of("size", 0, 2);
    public static final IntProperty TALLY = IntProperty.of("tally", 0, 4);
    public static final IntProperty LEVEL = IntProperty.of("level", 0, 3);
    public static final BooleanProperty SLANT = BooleanProperty.of("slant");
    public static final BooleanProperty PRESSED = Properties.POWERED;
    public static final BooleanProperty LIT = Properties.LIT;
    public static final BooleanProperty OPEN = Properties.OPEN;
    public static final BooleanProperty DISARMED = BooleanProperty.of("disarmed");
    public static final BooleanProperty FULL = BooleanProperty.of("full");
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public static final List<Block> ALL = new ArrayList<>();
    public static Block RUNE_DIAL, GLYPH_TILE, RUNE_PLATE, HEAVY_PLATE, BRAZIER, OFFERING_SOCKET, CHIME, ANCIENT_LEVER, STONE_BASIN, LIGHT_MIRROR,
            LIGHT_LENS, LIGHT_RECEIVER, MEMORY_PILLAR, MEMORY_STONE, KEYSTONE, MOSAIC_SLOT, ANCIENT_SEAL, SEAL_BARS, DORMANT_ENGINE, COUNTERWEIGHT,
            KEY_LOCK, TRAP_PLATE, DART_VENT, FLAME_VENT, STEAM_VENT, SPORE_VENT, FROST_VENT, CRUMBLING_FLOOR, LOOSE_CEILING, ALARM_GONG, ANCIENT_LAMP,
            EXPEDITION_CACHE, RELIC_PEDESTAL, KEY_PEDESTAL, INSCRIPTION;

    static AbstractBlock.Settings stone() {
        return AbstractBlock.Settings.copy(Blocks.STONE_BRICKS).requiresTool().strength(2.5f, 9f);
    }

    static AbstractBlock.Settings sealed() {
        return AbstractBlock.Settings.copy(Blocks.DEEPSLATE_TILES).strength(-1f, 3600000f).dropsNothing().pistonBehavior(PistonBehavior.BLOCK);
    }

    static <T extends Block> T reg(String id, T b) {
        Reg.blockItem(id, b);
        ALL.add(b);
        return b;
    }

    public static void init() {
        RUNE_DIAL = reg("rune_dial", new RuneDialBlock(stone()));
        GLYPH_TILE = reg("glyph_tile", new GlyphBlock(stone().dropsNothing()));
        RUNE_PLATE = reg("rune_plate", new RunePlateBlock(stone().noCollision(), false));
        HEAVY_PLATE = reg("heavy_plate", new RunePlateBlock(stone().noCollision(), true));
        BRAZIER = reg("ancient_brazier", new BrazierBlock(stone().nonOpaque().luminance(s -> s.get(LIT) ? 15 : 0)));
        OFFERING_SOCKET = reg("offering_socket", new SocketBlock(stone()));
        CHIME = reg("resonance_chime", new ChimeBlock(AbstractBlock.Settings.copy(Blocks.AMETHYST_BLOCK).nonOpaque().strength(1.5f)));
        ANCIENT_LEVER = reg("ancient_lever", new AncientLeverBlock(AbstractBlock.Settings.copy(Blocks.LEVER)));
        STONE_BASIN = reg("stone_basin", new BasinBlock(stone().nonOpaque()));
        LIGHT_MIRROR = reg("light_mirror", new MirrorBlock(stone().nonOpaque()));
        LIGHT_LENS = reg("light_lens", new Mechanism(stone().luminance(s -> 12)));
        LIGHT_RECEIVER = reg("light_receiver", new LitMechanism(stone().luminance(s -> s.get(LIT) ? 15 : 2)));
        MEMORY_PILLAR = reg("memory_pillar", new MemoryPillarBlock(stone()));
        MEMORY_STONE = reg("memory_stone", new MemoryStoneBlock(stone().luminance(s -> s.get(LIT) ? 10 : 0)));
        KEYSTONE = reg("keystone", new KeystoneBlock(stone()));
        MOSAIC_SLOT = reg("mosaic_frame", new Block(stone().dropsNothing()));
        ANCIENT_SEAL = reg("ancient_seal", new SealBlock(sealed()));
        SEAL_BARS = reg("seal_bars", new SealBarsBlock(sealed().nonOpaque()));
        DORMANT_ENGINE = reg("dormant_engine", new EngineBlock(stone().luminance(s -> s.get(LIT) ? 13 : 0)));
        COUNTERWEIGHT = reg("counterweight", new CounterweightBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).strength(2f)));
        KEY_LOCK = reg("key_lock", new KeyLockBlock(sealed()));
        TRAP_PLATE = reg("trap_plate", new TrapPlateBlock(stone().noCollision()));
        DART_VENT = reg("dart_vent", new VentBlock(stone(), "dart"));
        FLAME_VENT = reg("flame_vent", new VentBlock(stone().luminance(s -> s.get(LIT) ? 12 : 0).ticksRandomly(), "flame"));
        STEAM_VENT = reg("steam_vent", new VentBlock(stone().ticksRandomly(), "steam"));
        SPORE_VENT = reg("spore_vent", new VentBlock(stone().ticksRandomly(), "spore"));
        FROST_VENT = reg("frost_vent", new VentBlock(stone().ticksRandomly(), "frost"));
        CRUMBLING_FLOOR = reg("crumbling_floor", new CrumblingBlock(AbstractBlock.Settings.copy(Blocks.CRACKED_STONE_BRICKS).strength(0.8f).dropsNothing()));
        LOOSE_CEILING = reg("loose_ceiling", new VentBlock(AbstractBlock.Settings.copy(Blocks.GRAVEL).strength(0.8f).dropsNothing(), "rockfall"));
        ALARM_GONG = reg("alarm_gong", new VentBlock(AbstractBlock.Settings.copy(Blocks.BELL).nonOpaque(), "gong"));
        ANCIENT_LAMP = reg("ancient_lamp", new VentBlock(stone().luminance(s -> s.get(LIT) ? 15 : 0), "lamp"));
        EXPEDITION_CACHE = reg("expedition_cache", new CacheBlock(sealed().nonOpaque(), "cache"));
        RELIC_PEDESTAL = reg("relic_pedestal", new CacheBlock(sealed().nonOpaque().luminance(s -> s.get(FULL) ? 9 : 0), "pedestal"));
        KEY_PEDESTAL = reg("key_pedestal", new CacheBlock(sealed().nonOpaque(), "key"));
        INSCRIPTION = reg("inscription_stone", new InscriptionBlock(stone().dropsNothing()));
        for (Block b : List.of(BRAZIER, CHIME, STONE_BASIN, LIGHT_MIRROR, SEAL_BARS, ANCIENT_LEVER, EXPEDITION_CACHE, RELIC_PEDESTAL, KEY_PEDESTAL,
                RUNE_PLATE, HEAVY_PLATE, TRAP_PLATE, COUNTERWEIGHT, ALARM_GONG)) {
            Reg.CUTOUT.add(b);
        }
    }

    static ServerPlayerEntity sp(PlayerEntity p) {
        return p instanceof ServerPlayerEntity s ? s : null;
    }

    // ------------------------------------------------------------------ base mechanism

    /** A puzzle element: interactions are routed to {@link Mechanisms}. Drops nothing. */
    public static class Mechanism extends Block {
        public Mechanism(Settings s) {
            super(s.dropsNothing());
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity p) {
                Mechanisms.use(sw, pos, state, p, ItemStack.EMPTY, Hand.MAIN_HAND);
            }
            return ActionResult.success(world.isClient);
        }
    }

    public static class LitMechanism extends Mechanism {
        public LitMechanism(Settings s) {
            super(s);
            setDefaultState(getDefaultState().with(LIT, false));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(LIT);
        }
    }

    public static class FacingMechanism extends Mechanism {
        public FacingMechanism(Settings s) {
            super(s);
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
        }
    }

    public static class RuneDialBlock extends FacingMechanism {
        public RuneDialBlock(Settings s) {
            super(s);
            setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(SYMBOL, 0));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(FACING, SYMBOL);
        }
    }

    /** Decorative clue tile showing one of the six signs (no interaction). */
    public static class GlyphBlock extends Block {
        public GlyphBlock(Settings s) {
            super(s);
            setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(SYMBOL, 0));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(FACING, SYMBOL);
        }

        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
        }
    }

    public static class RunePlateBlock extends Mechanism {
        static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 1, 15), DOWN = Block.createCuboidShape(1, 0, 1, 15, 0.5, 15);
        final boolean heavy;

        public RunePlateBlock(Settings s, boolean heavy) {
            super(s);
            this.heavy = heavy;
            setDefaultState(getDefaultState().with(PRESSED, false).with(NOTCHES, 0));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(PRESSED, NOTCHES);
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
            return state.get(PRESSED) ? DOWN : SHAPE;
        }

        @Override
        protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
            if (!(world instanceof ServerWorld sw) || state.get(PRESSED)) {
                return;
            }
            if (heavy || entity instanceof PlayerEntity || entity instanceof LivingEntity) {
                sw.setBlockState(pos, state.with(PRESSED, true), Block.NOTIFY_ALL);
                sw.playSound(null, pos, SoundEvents.BLOCK_STONE_PRESSURE_PLATE_CLICK_ON, SoundCategory.BLOCKS, 0.6f, heavy ? 0.6f : 0.9f);
                sw.scheduleBlockTick(pos, this, 10);
                Mechanisms.pressed(sw, pos, entity);
            }
        }

        @Override
        protected void scheduledTick(BlockState state, ServerWorld w, BlockPos pos, Random random) {
            if (!state.get(PRESSED)) {
                return;
            }
            boolean still = !w.getEntitiesByClass(Entity.class, new Box(pos).shrink(0.06, 0, 0.06).stretch(0, 0.25, 0),
                    e -> !e.isSpectator() && (heavy || e instanceof LivingEntity)).isEmpty();
            if (heavy) {
                // a heavy plate also takes a dropped stack of at least eight items (solo solution)
                still |= w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new Box(pos).stretch(0, 0.25, 0), e -> e.getStack().getCount() >= 8)
                        .size() > 0;
            }
            if (still) {
                w.scheduleBlockTick(pos, this, 10);
            } else {
                w.setBlockState(pos, state.with(PRESSED, false), Block.NOTIFY_ALL);
                w.playSound(null, pos, SoundEvents.BLOCK_STONE_PRESSURE_PLATE_CLICK_OFF, SoundCategory.BLOCKS, 0.5f, 0.8f);
                Mechanisms.released(w, pos);
            }
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            return ActionResult.PASS;
        }
    }

    public static class BrazierBlock extends Mechanism {
        static final VoxelShape SHAPE = VoxelShapes.union(Block.createCuboidShape(3, 0, 3, 13, 9, 13), Block.createCuboidShape(1, 9, 1, 15, 12, 15));

        public BrazierBlock(Settings s) {
            super(s);
            setDefaultState(getDefaultState().with(LIT, false).with(TALLY, 0));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(LIT, TALLY);
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
            return SHAPE;
        }

        @Override
        protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                                                 BlockHitResult hit) {
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity p) {
                return Mechanisms.use(sw, pos, state, p, stack, hand) ? ItemActionResult.SUCCESS : ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            return ItemActionResult.SUCCESS;
        }

        @Override
        public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
            if (state.get(LIT)) {
                world.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + random.nextGaussian() * 0.15, pos.getY() + 0.85, pos.getZ() + 0.5, 0, 0.02, 0);
                if (random.nextInt(3) == 0) {
                    world.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0, 0.04, 0);
                }
            }
        }
    }

    public static class SocketBlock extends Mechanism {
        public SocketBlock(Settings s) {
            super(s);
            setDefaultState(getDefaultState().with(FULL, false));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(FULL);
        }

        @Override
        protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                                                 BlockHitResult hit) {
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity p) {
                Mechanisms.use(sw, pos, state, p, stack, hand);
            }
            return ItemActionResult.SUCCESS;
        }
    }

    public static class ChimeBlock extends Mechanism {
        static final VoxelShape[] SHAPES = {Block.createCuboidShape(5, 0, 5, 11, 6, 11), Block.createCuboidShape(5, 0, 5, 11, 11, 11),
                Block.createCuboidShape(5, 0, 5, 11, 16, 11)};

        public ChimeBlock(Settings s) {
            super(s);
            setDefaultState(getDefaultState().with(SIZE, 0));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(SIZE);
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
            return SHAPES[state.get(SIZE)];
        }
    }

    public static class AncientLeverBlock extends LeverBlock {
        public AncientLeverBlock(Settings s) {
            super(s.dropsNothing());
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            ActionResult r = super.onUse(state, world, pos, player, hit);
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity p) {
                Mechanisms.use(sw, pos, sw.getBlockState(pos), p, ItemStack.EMPTY, Hand.MAIN_HAND);
            }
            return r;
        }
    }

    public static class BasinBlock extends Mechanism {
        static final VoxelShape SHAPE = VoxelShapes.combineAndSimplify(VoxelShapes.fullCube(), Block.createCuboidShape(2, 4, 2, 14, 16, 14),
                net.minecraft.util.function.BooleanBiFunction.ONLY_FIRST);

        public BasinBlock(Settings s) {
            super(s);
            setDefaultState(getDefaultState().with(LEVEL, 0));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(LEVEL);
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
            return SHAPE;
        }

        @Override
        protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                                                 BlockHitResult hit) {
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity p) {
                return Mechanisms.use(sw, pos, state, p, stack, hand) ? ItemActionResult.SUCCESS : ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            return ItemActionResult.SUCCESS;
        }
    }

    public static class MirrorBlock extends Mechanism {
        public MirrorBlock(Settings s) {
            super(s);
            setDefaultState(getDefaultState().with(SLANT, false));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(SLANT);
        }
    }

    public static class MemoryPillarBlock extends Mechanism {
        public MemoryPillarBlock(Settings s) {
            super(s);
        }

        @Override
        protected void scheduledTick(BlockState state, ServerWorld w, BlockPos pos, Random random) {
            Mechanisms.replayTick(w, pos);
        }
    }

    public static class MemoryStoneBlock extends Mechanism {
        public MemoryStoneBlock(Settings s) {
            super(s);
            setDefaultState(getDefaultState().with(SYMBOL, 0).with(LIT, false));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(SYMBOL, LIT);
        }

        @Override
        protected void scheduledTick(BlockState state, ServerWorld w, BlockPos pos, Random random) {
            if (state.get(LIT)) {
                w.setBlockState(pos, state.with(LIT, false), Block.NOTIFY_ALL);
            }
        }
    }

    public static class KeystoneBlock extends Mechanism {
        public KeystoneBlock(Settings s) {
            super(s);
        }
    }

    /** An ancient seal: unbreakable; opened only by its puzzle. Using it explains that much. */
    public static class SealBlock extends Block {
        public SealBlock(Settings s) {
            super(s);
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity p) {
                Mechanisms.sealHint(sw, pos, p);
            }
            return ActionResult.success(world.isClient);
        }
    }

    /** Bars of a timed gate: open (no collision) for a while after their lever is pulled. */
    public static class SealBarsBlock extends SealBlock {
        public SealBarsBlock(Settings s) {
            super(s);
            setDefaultState(getDefaultState().with(OPEN, false));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(OPEN);
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
            return state.get(OPEN) ? VoxelShapes.empty() : VoxelShapes.fullCube();
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
            return state.get(OPEN) ? Block.createCuboidShape(0, 14, 0, 16, 16, 16) : VoxelShapes.fullCube();
        }

        @Override
        protected void scheduledTick(BlockState state, ServerWorld w, BlockPos pos, Random random) {
            if (!state.get(OPEN)) {
                return;
            }
            if (!w.getEntitiesByClass(LivingEntity.class, new Box(pos), e -> true).isEmpty()) {
                w.scheduleBlockTick(pos, this, 10); // never close on someone
                return;
            }
            w.setBlockState(pos, state.with(OPEN, false), Block.NOTIFY_ALL);
            w.playSound(null, pos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 0.8f, 0.6f);
        }
    }

    public static class EngineBlock extends LitMechanism {
        public EngineBlock(Settings s) {
            super(s);
        }

        @Override
        protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block source, BlockPos sourcePos, boolean notify) {
            if (world instanceof ServerWorld sw && !state.get(LIT) && sw.isReceivingRedstonePower(pos)) {
                sw.setBlockState(pos, state.with(LIT, true), Block.NOTIFY_ALL);
                sw.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.8f, 0.7f);
                Mechanisms.powered(sw, pos);
            }
        }
    }

    public static class CounterweightBlock extends Mechanism {
        public CounterweightBlock(Settings s) {
            super(s);
        }

        @Override
        protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                                                 BlockHitResult hit) {
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity p) {
                Mechanisms.use(sw, pos, state, p, stack, hand);
            }
            return ItemActionResult.SUCCESS;
        }
    }

    public static class KeyLockBlock extends SealBlock {
        public KeyLockBlock(Settings s) {
            super(s);
        }

        @Override
        protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                                                 BlockHitResult hit) {
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity p) {
                Mechanisms.use(sw, pos, state, p, stack, hand);
            }
            return ItemActionResult.SUCCESS;
        }
    }

    // ------------------------------------------------------------------ traps

    /** A worn floor tile that triggers nearby traps. Sneak-use with a stick or a pickaxe to jam it for good. */
    public static class TrapPlateBlock extends Block {
        static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 1, 16);

        public TrapPlateBlock(Settings s) {
            super(s.dropsNothing());
            setDefaultState(getDefaultState().with(DISARMED, false).with(PRESSED, false));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(DISARMED, PRESSED);
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
            return SHAPE;
        }

        @Override
        protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
            if (world instanceof ServerWorld sw && !state.get(DISARMED) && !state.get(PRESSED) && entity instanceof LivingEntity le && !le.isSneaking()) {
                sw.setBlockState(pos, state.with(PRESSED, true), Block.NOTIFY_ALL);
                sw.scheduleBlockTick(pos, this, 30);
                Traps.trigger(sw, pos, le);
            }
        }

        @Override
        protected void scheduledTick(BlockState state, ServerWorld w, BlockPos pos, Random random) {
            if (state.get(PRESSED)) {
                w.setBlockState(pos, state.with(PRESSED, false), Block.NOTIFY_ALL);
            }
        }

        @Override
        protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                                                 BlockHitResult hit) {
            if (!player.isSneaking() || state.get(DISARMED) || !(stack.isOf(Items.STICK) || stack.isIn(net.minecraft.registry.tag.ItemTags.PICKAXES))) {
                return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity p) {
                Traps.disarm(sw, pos, p, stack);
            }
            return ItemActionResult.SUCCESS;
        }
    }

    /** Dart, flame, hazard vents, loose ceilings, alarm gongs and ancient lamps: effects in {@link Traps}. */
    public static class VentBlock extends Block {
        public final String kind;

        public VentBlock(Settings s, String kind) {
            super(s.dropsNothing());
            this.kind = kind;
            setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(LIT, kind.equals("lamp")));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(FACING, LIT);
        }

        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
        }

        @Override
        protected void scheduledTick(BlockState state, ServerWorld w, BlockPos pos, Random random) {
            Traps.ventTick(w, pos, state, this);
        }

        @Override
        protected void randomTick(BlockState state, ServerWorld w, BlockPos pos, Random random) {
            Traps.ventRandom(w, pos, state, this);
        }

        @Override
        public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
            if (kind.equals("rockfall") && random.nextInt(4) == 0) {
                world.addParticle(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.getDefaultState()),
                        pos.getX() + random.nextDouble(), pos.getY() - 0.05, pos.getZ() + random.nextDouble(), 0, 0, 0);
            } else if (kind.equals("dart") && random.nextInt(6) == 0) {
                Direction d = state.get(FACING);
                world.addParticle(ParticleTypes.WHITE_ASH, pos.getX() + 0.5 + d.getOffsetX() * 0.55, pos.getY() + 0.5, pos.getZ() + 0.5 + d.getOffsetZ() * 0.55,
                        0, 0, 0);
            } else if ((kind.equals("steam") || kind.equals("spore") || kind.equals("frost")) && random.nextInt(5) == 0) {
                world.addParticle(kind.equals("spore") ? ParticleTypes.SPORE_BLOSSOM_AIR : kind.equals("frost") ? ParticleTypes.SNOWFLAKE : ParticleTypes.CLOUD,
                        pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.02, 0);
            } else if (kind.equals("flame") && state.get(LIT)) {
                for (int i = 0; i < 3; i++) {
                    world.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + random.nextGaussian() * 0.12, pos.getY() + 1.1 + i * 0.4,
                            pos.getZ() + 0.5 + random.nextGaussian() * 0.12, 0, 0.05, 0);
                }
            }
        }

        @Override
        protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
            if (kind.equals("flame") && state.get(LIT) && !world.isClient && entity instanceof LivingEntity le && !le.isFireImmune()) {
                le.setOnFireFor(3);
            }
        }
    }

    public static class CrumblingBlock extends Block {
        public CrumblingBlock(Settings s) {
            super(s);
            setDefaultState(getDefaultState().with(PRESSED, false));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(PRESSED);
        }

        @Override
        public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
            if (world instanceof ServerWorld sw && !state.get(PRESSED) && entity instanceof LivingEntity) {
                sw.setBlockState(pos, state.with(PRESSED, true), Block.NOTIFY_LISTENERS);
                sw.playSound(null, pos, SoundEvents.BLOCK_DRIPSTONE_BLOCK_BREAK, SoundCategory.BLOCKS, 1f, 0.6f);
                sw.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 1,
                        pos.getZ() + 0.5, 12, 0.3, 0.05, 0.3, 0.05);
                sw.scheduleBlockTick(pos, this, 20);
            }
            super.onSteppedOn(world, pos, state, entity);
        }

        @Override
        protected void scheduledTick(BlockState state, ServerWorld w, BlockPos pos, Random random) {
            Traps.crumble(w, pos);
        }
    }

    // ------------------------------------------------------------------ rewards and records

    public static class CacheBlock extends Block {
        final String kind;

        public CacheBlock(Settings s, String kind) {
            super(s);
            this.kind = kind;
            setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(FULL, true));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(FACING, FULL);
        }

        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
            return kind.equals("cache") ? Block.createCuboidShape(1, 0, 2, 15, 12, 14) : Block.createCuboidShape(2, 0, 2, 14, 13, 14);
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity p) {
                Rewards.use(sw, pos, state, p);
            }
            return ActionResult.success(world.isClient);
        }
    }

    public static class InscriptionBlock extends Block {
        public InscriptionBlock(Settings s) {
            super(s);
            setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity p) {
                if (!Lore.readAt(sw, pos, p)) {
                    p.sendMessage(Text.literal("The carving is worn beyond reading.").formatted(Formatting.GRAY), true);
                }
            }
            return ActionResult.success(world.isClient);
        }
    }

    static void unused(BlockSoundGroup g) {
    }
}
