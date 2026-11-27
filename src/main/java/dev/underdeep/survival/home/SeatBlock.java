package dev.underdeep.survival.home;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/** Chairs and benches: right-click with an empty hand to sit (an invisible seat entity carries the player). */
public class SeatBlock extends DecorBlock {
    private final double seatHeight;

    public SeatBlock(Settings settings, VoxelShape shape, double seatHeight) {
        super(settings, shape, shape);
        this.seatHeight = seatHeight;
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return createCodec(s -> new SeatBlock(s, net.minecraft.util.shape.VoxelShapes.fullCube(), seatHeight));
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (player.isSneaking() || player.hasVehicle() || !player.getMainHandStack().isEmpty()) {
            return ActionResult.PASS;
        }
        if (!world.isClient) {
            if (!world.getEntitiesByClass(SeatEntity.class, new Box(pos), e -> true).isEmpty()) {
                return ActionResult.CONSUME;
            }
            SeatEntity seat = new SeatEntity(world, pos.getX() + 0.5, pos.getY() + seatHeight, pos.getZ() + 0.5);
            world.spawnEntity(seat);
            player.startRiding(seat);
        }
        return ActionResult.success(world.isClient);
    }
}
