package dev.underdeep.wildlife;

import com.mojang.serialization.MapCodec;
import dev.underdeep.registry.Reg;
import dev.underdeep.wildlife.population.RegionWildlife;
import dev.underdeep.wildlife.population.WildlifeManager;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.MapColor;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Wildlife structures: woven nests and burrow mounds (placed by nesting species only into free space
 * on natural support, never near player builds) and eggs that hatch into juveniles. Eggs carry a
 * small block entity (species, home region, hatch time); their number is bounded by breeding caps.
 */
public final class WildlifeBlocks {
    private static final VoxelShape FLAT = Block.createCuboidShape(1, 0, 1, 15, 3, 15);
    private static final VoxelShape EGG_SHAPE = Block.createCuboidShape(5, 0, 5, 11, 7, 11);

    public static final Block NEST = Reg.blockItem("twig_nest", new FlatBlock(AbstractBlock.Settings.create().mapColor(MapColor.BROWN)
            .strength(0.3f).sounds(BlockSoundGroup.GRASS).nonOpaque().burnable().pistonBehavior(PistonBehavior.DESTROY)));
    public static final Block BURROW = Reg.blockItem("burrow_mound", new FlatBlock(AbstractBlock.Settings.create().mapColor(MapColor.DIRT_BROWN)
            .strength(0.4f).sounds(BlockSoundGroup.ROOTED_DIRT).nonOpaque().pistonBehavior(PistonBehavior.DESTROY)));
    public static final Block EGG = Reg.block("creature_egg_block", new EggBlock(AbstractBlock.Settings.create().mapColor(MapColor.PALE_YELLOW)
            .strength(0.5f).sounds(BlockSoundGroup.METAL).nonOpaque().ticksRandomly().pistonBehavior(PistonBehavior.DESTROY)));
    public static final BlockEntityType<EggBlockEntity> EGG_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, Reg.id("creature_egg"),
            BlockEntityType.Builder.create(EggBlockEntity::new, EGG).build(null));

    static {
        Reg.CUTOUT.add(NEST);
        Reg.CUTOUT.add(BURROW);
        Reg.CUTOUT.add(EGG);
    }

    private WildlifeBlocks() {
    }

    public static void init() {
    }

    public static boolean isNest(BlockState s) {
        return s.isOf(NEST) || s.isOf(BURROW);
    }

    public static class FlatBlock extends Block {
        public FlatBlock(Settings s) {
            super(s);
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return FLAT;
        }

        @Override
        protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
            BlockState b = world.getBlockState(pos.down());
            return b.isSideSolidFullSquare(world, pos.down(), Direction.UP) || b.isIn(BlockTags.LEAVES);
        }

        @Override
        protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world,
                                                       BlockPos pos, BlockPos neighborPos) {
            return direction == Direction.DOWN && !canPlaceAt(state, world, pos) ? net.minecraft.block.Blocks.AIR.getDefaultState() : state;
        }
    }

    public static class EggBlock extends BlockWithEntity {
        public EggBlock(Settings s) {
            super(s);
        }

        @Override
        protected MapCodec<? extends BlockWithEntity> getCodec() {
            return MapCodec.unit(this);
        }

        @Override
        protected BlockRenderType getRenderType(BlockState state) {
            return BlockRenderType.MODEL;
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return EGG_SHAPE;
        }

        @Override
        protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
            BlockState b = world.getBlockState(pos.down());
            return b.isSideSolidFullSquare(world, pos.down(), Direction.UP) || b.isIn(BlockTags.LEAVES) || isNest(b);
        }

        @Nullable
        @Override
        public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
            return new EggBlockEntity(pos, state);
        }

        @Override
        protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
            if (world.getBlockEntity(pos) instanceof EggBlockEntity egg && egg.species != null && world.getTime() >= egg.hatchAt) {
                hatch(world, pos, egg);
            }
        }

        static void hatch(ServerWorld world, BlockPos pos, EggBlockEntity egg) {
            var type = WildlifeRegistry.type(egg.species);
            world.removeBlock(pos, false);
            if (type == null) {
                return;
            }
            UnderdeepCreature baby = type.create(world);
            if (baby == null) {
                return;
            }
            baby.setBaby(true);
            baby.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random(world), 0);
            if (egg.owner != null) {
                baby.setOwnerUuid(egg.owner);
                baby.setTamed(true, true);
                baby.setPersistent();
            } else if (egg.wild && WildlifeManager.get(world) != null) {
                baby.initWild(egg.region, pos, 0);
                RegionWildlife r = WildlifeManager.get(world).region(egg.region);
                r.add(egg.species, 1);
                r.births++;
            }
            world.spawnEntity(baby);
            world.playSound(null, pos, SoundEvents.ENTITY_TURTLE_EGG_HATCH, SoundCategory.BLOCKS, 0.7f, 1.0f);
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 5, 0.2, 0.2, 0.2, 0);
        }

        private static float random(ServerWorld w) {
            return w.random.nextFloat() * 360;
        }
    }

    public static class EggBlockEntity extends BlockEntity {
        String species;
        long region;
        boolean wild;
        long hatchAt;
        @Nullable
        UUID owner;

        public EggBlockEntity(BlockPos pos, BlockState state) {
            super(EGG_ENTITY, pos, state);
        }

        public void setup(String species, long region, boolean wild, long hatchAt, @Nullable UUID owner) {
            this.species = species;
            this.region = region;
            this.wild = wild;
            this.hatchAt = hatchAt;
            this.owner = wild ? null : owner;
            markDirty();
        }

        public String species() {
            return species;
        }

        @Override
        protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            super.writeNbt(nbt, lookup);
            if (species != null) {
                nbt.putString("Species", species);
            }
            nbt.putLong("Region", region);
            nbt.putBoolean("Wild", wild);
            nbt.putLong("HatchAt", hatchAt);
            if (owner != null) {
                nbt.putUuid("Owner", owner);
            }
        }

        @Override
        protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            super.readNbt(nbt, lookup);
            species = nbt.contains("Species") ? nbt.getString("Species") : null;
            region = nbt.getLong("Region");
            wild = nbt.getBoolean("Wild");
            hatchAt = nbt.getLong("HatchAt");
            owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        }
    }
}
