package dev.underdeep.survival;

import dev.underdeep.wildlife.UnderdeepCreature;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashSet;
import java.util.Set;

/**
 * Home comfort (Stage 7): a positive-only score for where you rest. Furniture of different kinds, a kitchen station,
 * light, warmth, a proper bed, a shelter and company each add a point. Waking from a comfortable rest gives Rested
 * (longer the more comfortable) and clears weariness. A bare floor simply gives nothing extra.
 */
public final class Comfort {
    private Comfort() {
    }

    public static final int MAX = 10;

    public static int score(ServerWorld w, BlockPos at, PlayerEntity p) {
        Set<Block> kinds = new HashSet<>();
        boolean station = false, bed = false, awning = false;
        for (BlockPos q : BlockPos.iterate(at.add(-6, -2, -6), at.add(6, 3, 6))) {
            BlockState s = w.getBlockState(q);
            Block b = s.getBlock();
            if (SurvivalBlocks.FURNITURE.contains(b)) {
                kinds.add(b);
                awning |= b == SurvivalBlocks.CAMP_AWNING;
            } else if (SurvivalBlocks.STATIONS.contains(b)) {
                station = true;
            }
            bed |= b instanceof BedBlock && q.isWithinDistance(at, 2.5);
        }
        int score = Math.min(4, kinds.size());
        if (station) {
            score++;
        }
        if (bed) {
            score += 2;
        }
        if (awning) {
            score++;
        }
        if (w.getLightLevel(at.up()) >= 8) {
            score++;
        }
        if (Hazards.warmth(w, at)) {
            score++;
        }
        Box near = new Box(at).expand(8);
        boolean company = !w.getEntitiesByClass(PlayerEntity.class, near, o -> o != p).isEmpty()
                || !w.getEntitiesByClass(UnderdeepCreature.class, near, c -> c.isTamed()).isEmpty()
                || !w.getEntitiesByClass(dev.underdeep.civ.npc.InhabitantEntity.class, near, e -> true).isEmpty();
        if (company) {
            score++;
        }
        return Math.min(MAX, score);
    }

    /** Called when a player wakes up (bed or bedroll). */
    public static void onWake(ServerPlayerEntity p, BlockPos bedPos) {
        ServerWorld w = p.getServerWorld();
        int s = score(w, bedPos, p);
        SurvivalData d = SurvivalData.of(p);
        d.lastRest = w.getTime();
        d.lastComfort = s;
        p.removeStatusEffect(SurvivalEffects.WEARY);
        int minutes = Math.min(20, 2 + 2 * s);
        Meals.apply(p, SurvivalEffects.RESTED, minutes * 20 * 60, 0, false);
        String word = s >= 8 ? "very comfortable" : s >= 5 ? "comfortable" : s >= 2 ? "a fair rest" : "a rough night";
        p.sendMessage(Text.translatable("message.underdeep.rested", word + ", comfort " + s + "/" + MAX).formatted(Formatting.AQUA), true);
    }
}
