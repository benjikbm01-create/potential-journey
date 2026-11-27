package dev.underdeep.survival.home;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import dev.underdeep.config.UnderdeepConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Unit;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * A single-block bedroll for expeditions. Sleeping works like a bed (night only, no nearby monsters), but it never
 * moves your respawn point; sneak-use sets it deliberately (configurable).
 */
public class BedrollBlock extends DecorBlock {
    static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 3, 15);

    public BedrollBlock(Settings settings) {
        super(settings, SHAPE, SHAPE);
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return createCodec(BedrollBlock::new);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.CONSUME;
        }
        ServerPlayerEntity sp = (ServerPlayerEntity) player;
        if (player.isSneaking() && UnderdeepConfig.get().survival.bedrollSetsSpawnWhenSneaking) {
            sp.setSpawnPoint(world.getRegistryKey(), pos, player.getYaw(), true, false);
            player.sendMessage(Text.translatable("message.underdeep.bedroll.spawn").formatted(Formatting.YELLOW), true);
            return ActionResult.SUCCESS;
        }
        if (!world.getDimension().bedWorks()) {
            player.sendMessage(Text.literal("You cannot rest here."), true);
            return ActionResult.SUCCESS;
        }
        Either<PlayerEntity.SleepFailureReason, Unit> r = sp.trySleep(pos);
        r.ifLeft(reason -> {
            if (reason.getMessage() != null) {
                player.sendMessage(reason.getMessage(), true);
            }
        });
        return ActionResult.SUCCESS;
    }
}
