package dev.underdeep.survival;

import dev.underdeep.config.UnderdeepConfig;
import dev.underdeep.survival.home.BedrollBlock;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Stage 7 entry point: survival, farming, cooking, medicine and homesteading. See docs/SURVIVAL.md.
 */
public final class Survival {
    private Survival() {
    }

    /** Registers items, blocks, effects and data types. Runs before the economy loads its goods (which include them). */
    public static void registerContent() {
        SurvivalContent.load();
        SurvivalEffects.init();
        SurvivalItems.init();
        SurvivalBlocks.init();
        SurvivalData.init();
        Recipes.init();
        SurvivalItems.postInit();
    }

    /** Registers events. Runs after the earlier stages so their protection callbacks see interactions first. */
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(s -> SurvivalContent.resolve());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) {
                return;
            }
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                Hazards.tick(p);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> Hazards.forget(h.player.getUuid()));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (entity instanceof ServerPlayerEntity p && !blocked) {
                Hazards.afterDamage(p, taken);
            }
        });
        // bedrolls: a valid place to sleep that never moves the respawn point by itself
        EntitySleepEvents.ALLOW_BED.register((entity, pos, state, vanilla) ->
                state.getBlock() instanceof BedrollBlock ? ActionResult.SUCCESS : ActionResult.PASS);
        EntitySleepEvents.ALLOW_SETTING_SPAWN.register((player, pos) -> !(player.getWorld().getBlockState(pos).getBlock() instanceof BedrollBlock));
        EntitySleepEvents.MODIFY_SLEEPING_DIRECTION.register((entity, pos, dir) -> {
            BlockState s = entity.getWorld().getBlockState(pos);
            return s.getBlock() instanceof BedrollBlock ? s.get(HorizontalFacingBlock.FACING) : dir;
        });
        EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
            if (entity instanceof ServerPlayerEntity p && UnderdeepConfig.get().survival.enabled) {
                Comfort.onWake(p, pos);
            }
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != Hand.MAIN_HAND || !player.getStackInHand(hand).isEmpty() || player.isSneaking()) {
                return ActionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            BlockState s = world.getBlockState(pos);
            if (s.getBlock() instanceof CropBlock crop && crop.isMature(s)
                    && !net.minecraft.registry.Registries.BLOCK.getId(s.getBlock()).getPath().equals("relic_sprout")) {
                if (!world.isClient && harvest((ServerWorld) world, pos, s, crop, player) == null) {
                    return ActionResult.PASS;
                }
                return ActionResult.success(world.isClient);
            }
            return ActionResult.PASS;
        });
    }

    /**
     * Right-click harvest: drops the crop's loot, keeps one seed back to replant. Returns the drops given, or null
     * (nothing changed) when the loot holds no seed to replant with.
     */
    public static List<ItemStack> harvest(ServerWorld world, BlockPos pos, BlockState s, CropBlock crop, net.minecraft.entity.player.PlayerEntity player) {
        List<ItemStack> drops = Block.getDroppedStacks(s, world, pos, null, player, ItemStack.EMPTY);
        Item seed = crop.asItem();
        boolean replant = false;
        for (ItemStack d : drops) {
            if (d.isOf(seed)) {
                d.decrement(1);
                replant = true;
                break;
            }
        }
        if (!replant) {
            return null;
        }
        world.setBlockState(pos, crop.withAge(0), Block.NOTIFY_ALL);
        if (player instanceof ServerPlayerEntity sp) {
            dev.underdeep.guide.Advancements.event(sp, "harvested", net.minecraft.registry.Registries.BLOCK.getId(crop).getPath());
        }
        for (ItemStack d : drops) {
            if (!d.isEmpty()) {
                Block.dropStack(world, pos, d);
            }
        }
        world.playSound(null, pos, SoundEvents.BLOCK_CROP_BREAK, SoundCategory.BLOCKS, 1f, 1f);
        return drops;
    }

    /** Whether the world has the Underdeep survival rules active. */
    public static boolean active(World w) {
        return UnderdeepConfig.get().survival.enabled;
    }
}
