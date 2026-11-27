package dev.underdeep.boss;

import dev.underdeep.registry.Reg;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 10 lair blocks. All are unbreakable (a guardian's altar, its arena devices and the Heart Engine door must
 * never be destroyed by accident, and they hold no player items): the altar (awaken, reclaim a lost Sign), arena
 * devices used during fights (each with a lit/charged state that fights change temporarily), the engine door and
 * the engine stone enclosing the Heart Engine.
 */
public final class BossBlocks {
    private BossBlocks() {
    }

    public static final BooleanProperty LIT = Properties.LIT;
    public static Block ALTAR, ENGINE_DOOR, ENGINE_STONE;
    public static Block PYLON, BRAZIER, LANTERN, COIL, BELL, ROOT_HEART;
    public static final List<Block> ALL = new ArrayList<>();

    static AbstractBlock.Settings sealed(Block like) {
        return AbstractBlock.Settings.copy(like).strength(-1f, 3600000f).dropsNothing().pistonBehavior(PistonBehavior.BLOCK);
    }

    static <T extends Block> T reg(String id, T b) {
        Reg.blockItem(id, b);
        ALL.add(b);
        return b;
    }

    public static void init() {
        ALTAR = reg("guardian_altar", new AltarBlock(sealed(Blocks.CHISELED_DEEPSLATE).luminance(s -> 6)));
        ENGINE_DOOR = reg("engine_door", new DoorBlock(sealed(Blocks.REINFORCED_DEEPSLATE).luminance(s -> 3)));
        ENGINE_STONE = reg("engine_stone", new Block(sealed(Blocks.REINFORCED_DEEPSLATE)));
        PYLON = reg("conductor_pylon", new DeviceBlock("pylon", sealed(Blocks.OXIDIZED_COPPER).nonOpaque().luminance(s -> s.get(LIT) ? 12 : 2)));
        BRAZIER = reg("warding_brazier", new DeviceBlock("brazier", sealed(Blocks.BLACKSTONE).nonOpaque().luminance(s -> s.get(LIT) ? 15 : 0)));
        LANTERN = reg("lamplighter_post", new DeviceBlock("lantern", sealed(Blocks.COPPER_BLOCK).nonOpaque().luminance(s -> s.get(LIT) ? 15 : 0)));
        COIL = reg("engine_coil", new DeviceBlock("coil", sealed(Blocks.AMETHYST_BLOCK).nonOpaque().luminance(s -> s.get(LIT) ? 14 : 1)));
        BELL = reg("silent_bell", new DeviceBlock("bell", sealed(Blocks.GOLD_BLOCK).nonOpaque()));
        ROOT_HEART = reg("root_heart", new DeviceBlock("root", sealed(Blocks.MANGROVE_ROOTS).nonOpaque().luminance(s -> s.get(LIT) ? 10 : 0)));
    }

    public static class AltarBlock extends Block {
        public AltarBlock(Settings s) {
            super(s);
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (player instanceof ServerPlayerEntity p) {
                Encounters.altar(p, pos);
            }
            return ActionResult.success(world.isClient);
        }

        @Override
        protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                                                 BlockHitResult hit) {
            if (player instanceof ServerPlayerEntity p) {
                Encounters.altar(p, pos);
            }
            return ItemActionResult.success(world.isClient);
        }
    }

    public static class DeviceBlock extends Block {
        public final String kind;

        public DeviceBlock(String kind, Settings s) {
            super(s);
            this.kind = kind;
            setDefaultState(getStateManager().getDefaultState().with(LIT, false));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
            b.add(LIT);
        }

        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            return getDefaultState();
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (player instanceof ServerPlayerEntity p) {
                Encounters.Active a = Encounters.arenaAt(world, pos);
                if (a != null && a.boss != null) {
                    a.boss.brain.device(p, pos, kind);
                } else {
                    p.sendMessage(Text.literal(switch (kind) {
                        case "pylon" -> "The pylon is cold. Copper hums faintly inside.";
                        case "brazier" -> "An old brazier, waiting for fire.";
                        case "lantern" -> "A lamplighter's post. Its wick is old but whole.";
                        case "coil" -> "A great coil of crystal and copper. It feels like a sleeping heartbeat.";
                        case "bell" -> "A bell that makes no sound.";
                        default -> "A knot of living root.";
                    }).formatted(Formatting.GRAY, Formatting.ITALIC), true);
                }
            }
            return ActionResult.success(world.isClient);
        }

        @Override
        protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                                                 BlockHitResult hit) {
            onUse(state, world, pos, player, hit);
            return ItemActionResult.success(world.isClient);
        }
    }

    public static class DoorBlock extends Block {
        public DoorBlock(Settings s) {
            super(s);
        }

        @Override
        protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                                                 BlockHitResult hit) {
            if (player instanceof ServerPlayerEntity p) {
                Ending.door(p, pos, stack);
            }
            return ItemActionResult.success(world.isClient);
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (player instanceof ServerPlayerEntity p) {
                Ending.door(p, pos, ItemStack.EMPTY);
            }
            return ActionResult.success(world.isClient);
        }
    }
}
