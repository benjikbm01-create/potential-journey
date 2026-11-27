package dev.underdeep.survival.home;

import dev.underdeep.survival.SurvivalBlocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Invisible, unsaved seat for chairs and benches; removes itself when empty or when the seat block is gone. */
public class SeatEntity extends Entity {
    public SeatEntity(EntityType<?> type, World world) {
        super(type, world);
        noClip = true;
        setNoGravity(true);
        setInvisible(true);
    }

    public SeatEntity(World world, double x, double y, double z) {
        this(SurvivalBlocks.SEAT, world);
        setPosition(x, y, z);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient && (!hasPassengers() || !(getWorld().getBlockState(getBlockPos()).getBlock() instanceof SeatBlock))) {
            removeAllPassengers();
            discard();
        }
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        return new Vec3d(getX(), getBlockPos().getY() + 1.0, getZ());
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public boolean shouldSave() {
        return false;
    }
}
