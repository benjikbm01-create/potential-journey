package dev.underdeep.debug;

import dev.underdeep.wildlife.CreatureSpecies;
import dev.underdeep.wildlife.UnderdeepCreature;
import dev.underdeep.wildlife.WildlifeRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Development-only "zoo": builds lit test halls far from spawn and places every species (frozen,
 * persistent, labelled) in groups of six, writing a screenshot tour so models and textures can be
 * inspected with the real client. Enabled only from the property-gated self-test.
 */
final class WildlifeZoo {
    private WildlifeZoo() {
    }

    static void build(ServerWorld w, Path dir) throws IOException {
        List<CreatureSpecies> all = WildlifeRegistry.all();
        StringBuilder tour = new StringBuilder("# name x y z yaw pitch [natural]\n");
        int baseY = 200, baseZ = 2400;
        int x = 2400;
        for (int i = 0; i < all.size(); i++) {
            CreatureSpecies s = all.get(i);
            float size = Math.max(s.width(), s.height()) * (s.sizeClass() > 2.5f ? 1.3f : 1.0f);
            int pen = (int) Math.max(10, size * 4 + 6);
            int cx = x + pen / 2;
            hall(w, cx, baseY, baseZ, pen / 2, s.isAquatic());
            UnderdeepCreature c = WildlifeRegistry.type(s.id).create(w);
            if (c != null) {
                double y = baseY + (s.flies() ? 2 : s.isAquatic() ? 3 : 0);
                c.refreshPositionAndAngles(cx + 0.5, y, baseZ + 0.5, 150, 0);
                c.setHeadYaw(150);
                c.setBodyYaw(150);
                c.setAiDisabled(true);
                c.setPersistent();
                c.setCustomName(net.minecraft.text.Text.literal(s.id));
                w.spawnEntity(c);
            }
            double d = 2.5 + size * 2.2;
            double camY = baseY + (s.isAquatic() ? 3.5 : 0.6) + Math.min(6, s.height() * 0.9);
            float pitch = (float) Math.toDegrees(Math.atan2(camY - (baseY + s.height() * 0.5 + (s.flies() ? 2 : s.isAquatic() ? 3 : 0)), d));
            tour.append(String.format(Locale.ROOT, "zoo_%02d_%s %.1f %.1f %.1f 0 %.0f natural\n", i, s.id, cx + 0.5, camY - 1.62,
                    baseZ + 0.5 - d, pitch));
            x += pen + 2;
        }
        Files.writeString(dir.resolve("tour_zoo.txt"), tour.toString());
    }

    private static void hall(ServerWorld w, int cx, int y0, int cz, int half, boolean water) {
        for (int x = cx - half - 16; x <= cx + half + 16; x += 16) {
            for (int z = cz - 32; z <= cz + 32; z += 16) {
                w.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
            }
        }
        BlockPos.Mutable m = new BlockPos.Mutable();
        int f = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
        for (int x = cx - half - 1; x <= cx + half + 1; x++) {
            for (int z = cz - 22; z <= cz + 10; z++) {
                for (int y = y0 - 1; y <= y0 + 16; y++) {
                    m.set(x, y, z);
                    boolean edge = x == cx - half - 1 || x == cx + half + 1 || z == cz - 22 || z == cz + 10 || y == y0 + 16;
                    BlockState s;
                    if (y == y0 - 1) {
                        s = (x + z) % 7 == 0 ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.MOSS_BLOCK.getDefaultState();
                    } else if (edge) {
                        s = y == y0 + 16 && (x % 4 == 0 && z % 4 == 0) ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.SMOOTH_STONE.getDefaultState();
                    } else if (water && y < y0 + 12) {
                        s = Blocks.WATER.getDefaultState();
                    } else {
                        s = Blocks.AIR.getDefaultState();
                    }
                    w.setBlockState(m, s, f);
                }
            }
        }
    }
}
