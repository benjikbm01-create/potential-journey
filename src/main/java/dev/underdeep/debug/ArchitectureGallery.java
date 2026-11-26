package dev.underdeep.debug;

import dev.underdeep.civ.Civilization;
import dev.underdeep.civ.arch.Architect;
import dev.underdeep.civ.arch.Blueprint;
import dev.underdeep.civ.arch.BlueprintPlacer;
import dev.underdeep.civ.arch.BlueprintValidator;
import dev.underdeep.civ.arch.BuildingKind;
import dev.underdeep.civ.settlement.Professions;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Development-only architecture gallery: lays every civilization's buildings out in a row on a lit plaza
 * far from spawn (test worlds only; enabled by -Dunderdeep.gallery=true or the operator command with an
 * explicit confirmation) and writes a screenshot tour.
 */
public final class ArchitectureGallery {
    public static final int BASE_X = 6000, BASE_Z = 6000, BASE_Y = 210, ROW = 70;

    private ArchitectureGallery() {
    }

    /** Designs shown per civilization: every kind once, plus house variants. */
    public static List<Object[]> entries(Civilization civ) {
        List<Object[]> list = new ArrayList<>();
        for (BuildingKind k : BuildingKind.values()) {
            list.add(new Object[]{k, 0, Professions.workshopFor(civ, 0)});
        }
        for (int v = 1; v < Architect.VARIANTS; v++) {
            list.add(new Object[]{BuildingKind.HOUSE, v, ""});
            list.add(new Object[]{BuildingKind.HOUSE_SMALL, v, ""});
        }
        list.add(new Object[]{BuildingKind.WORKSHOP, 1, Professions.workshopFor(civ, 1)});
        list.add(new Object[]{BuildingKind.WORKSHOP, 2, Professions.workshopFor(civ, 2)});
        return list;
    }

    /** Builds the gallery; returns validation problems (one line each). */
    public static List<String> build(ServerWorld w, int baseX, int baseY, int baseZ, Path tourDir, List<Civilization> civs) throws IOException {
        List<String> problems = new ArrayList<>();
        StringBuilder tour = new StringBuilder("# name x y z yaw pitch [natural]\n");
        int row = 0;
        for (Civilization civ : civs) {
            int z0 = baseZ + row * ROW;
            int x = baseX;
            List<Object[]> entries = entries(civ);
            int rowEnd = baseX;
            for (Object[] e : entries) {
                Blueprint bp = Architect.design(civ, (BuildingKind) e[0], (Integer) e[1], (String) e[2]);
                rowEnd += bp.width + 6;
            }
            plaza(w, baseX - 8, baseY, z0 - 26, rowEnd + 8, z0 + 40, civ);
            int shot = 0;
            for (Object[] e : entries) {
                BuildingKind kind = (BuildingKind) e[0];
                Blueprint bp = Architect.design(civ, kind, (Integer) e[1], (String) e[2]);
                BlueprintPlacer.place(w, bp, new BlockPos(x, baseY, z0), BlockRotation.NONE, null, 1L,
                        BlueprintPlacer.lootFor(kind.id(), (String) e[2]));
                BlueprintValidator.Result r = BlueprintValidator.validate(bp);
                if (!r.ok()) {
                    problems.add(civ.id() + "/" + kind.id() + "/" + e[1] + ": " + String.join("; ", r.problems()));
                }
                x += bp.width + 6;
            }
            // inhabitant line-up in front of the row (frozen, saved exhibits) and a close view of it
            String[] profs = {"merchant", "guard", "farmer", "scholar", dev.underdeep.civ.settlement.Professions.workshopFor(civ, 0), "fisher"};
            for (int i = 0; i < 6; i++) {
                dev.underdeep.civ.npc.InhabitantEntity e = dev.underdeep.civ.npc.InhabitantManager.INHABITANT.create(w);
                if (e == null) {
                    continue;
                }
                e.exhibit = true;
                e.dress(civ, i, profs[i], dev.underdeep.civ.Names.person(civ, i * 31L + civ.ordinal()));
                e.setAiDisabled(true);
                e.setPersistent();
                e.refreshPositionAndAngles(baseX + 2 + i * 2 + 0.5, baseY, z0 - 8 + 0.5, 180, 0);
                e.setHeadYaw(180);
                e.setBodyYaw(180);
                w.spawnEntity(e);
            }
            tour.append(String.format(Locale.ROOT, "gallery_%s_people %d %d %d 0 12 natural\n", civ.id(), baseX + 7, baseY + 2, z0 - 15));
            // wide views every ~40 blocks along the row, from the front and slightly above
            for (int vx = baseX + 12; vx < rowEnd; vx += 30) {
                tour.append(String.format(Locale.ROOT, "gallery_%s_%02d %d %d %d 0 18 natural\n", civ.id(), shot++, vx, baseY + 6, z0 - 15));
            }
            row++;
        }
        Files.writeString(tourDir.resolve("tour_gallery.txt"), tour.toString());
        return problems;
    }

    private static void plaza(ServerWorld w, int x1, int y, int z1, int x2, int z2, Civilization civ) {
        for (int cx = x1 >> 4; cx <= x2 >> 4; cx++) {
            for (int cz = z1 >> 4; cz <= z2 >> 4; cz++) {
                w.getChunk(cx, cz, ChunkStatus.FULL, true);
            }
        }
        BlockPos.Mutable m = new BlockPos.Mutable();
        int f = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int yy = y - 3; yy <= y + 42; yy++) {
                    m.set(x, yy, z);
                    w.setBlockState(m, yy < y ? ((x * 7 + z * 3) % 23 == 0 ? Blocks.GLOWSTONE : Blocks.SMOOTH_STONE).getDefaultState()
                            : Blocks.AIR.getDefaultState(), f);
                }
                if ((x - x1) % 12 == 0 && (z - z1) % 12 == 0) {
                    m.set(x, y + 42, z);
                    w.setBlockState(m, Blocks.GLOWSTONE.getDefaultState(), f);
                }
            }
        }
    }
}
