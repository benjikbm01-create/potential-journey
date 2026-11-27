package dev.underdeep.explore.design;

import dev.underdeep.explore.Canvas;
import dev.underdeep.explore.Site;
import dev.underdeep.explore.Style;
import dev.underdeep.registry.ModBlocks;
import dev.underdeep.registry.ModWood;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * Standalone secret places (Stage 9). Each shows only a small, fair clue outside (a crack, a draught, a glint, a
 * sound) and is discovered only when the player actually enters the hidden room. Their identities are private.
 */
final class Secrets {
    private Secrets() {
    }

    static final Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;

    static void waterfallGrotto(Kit k, Site site) {
        int m = k.mid;
        // a curtain of water falling over the entrance
        for (int x = m - 2; x <= m + 2; x++) {
            for (int y = 1; y <= 6; y++) {
                k.c.set(x, y, m + 2, Blocks.STONE);
            }
            k.c.set(x, 7, m + 3, Blocks.WATER);
            k.c.set(x, 7, m + 2, Blocks.STONE);
        }
        k.air(m - 1, 1, m + 2, m, 3, m + 2);
        k.room(m - 3, 0, m - 4, m + 3, 5, m + 2);
        k.air(m - 1, 1, m + 2, m, 3, m + 2);
        k.c.set(m, 1, m - 3, Blocks.MOSS_BLOCK);
        k.coffer(m, 1, m - 2, S, "underdeep:exploration/secret_stash");
        k.c.set(m - 2, 1, m - 3, dev.underdeep.registry.ModPlants.GLOWFERN);
        k.c.set(m + 2, 4, m - 3, ModBlocks.LUMINITE_CLUSTER);
        k.inscription(m + 3, 2, m - 1, W, Designs.loose(k, site));
        k.c.secret("waterfall_grotto", m - 2, 1, m - 3, m + 2, 4, m + 1);
    }

    static void hollowPillar(Kit k, Site site) {
        int m = k.mid;
        int h = 16;
        for (int y = 0; y <= h; y++) {
            k.disc(m, m, 2.6, y, Blocks.STONE, null);
        }
        // a single cracked stone at the foot is the clue; behind it a ladder climbs inside
        k.c.set(m, 1, m + 2, Blocks.CRACKED_STONE_BRICKS);
        k.c.lock(m, 1, m + 2);
        for (int y = 1; y <= h - 2; y++) {
            k.c.set(m, y, m, Canvas.AIR);
            k.c.set(m, y, m + 1, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, N));
            k.c.lock(m, y, m + 1);
        }
        k.c.set(m, 2, m + 2, Blocks.STONE);
        k.air(m - 1, h - 3, m - 1, m + 1, h - 1, m + 1);
        k.c.set(m, h - 3, m, Blocks.SPRUCE_PLANKS);
        k.coffer(m - 1, h - 2, m - 1, S, "underdeep:exploration/secret_stash");
        k.c.set(m + 1, h - 1, m - 1, Blocks.LANTERN);
        k.c.secret("hollow_pillar", m - 1, h - 3, m - 1, m + 1, h - 1, m + 1);
    }

    static void buriedReliquary(Kit k, Site site) {
        int m = k.mid;
        // a cairn whose top stone is a lever
        for (int y = 1; y <= 3; y++) {
            k.disc(m, m, 2.2 - y * 0.5, y, k.s.rubble, null);
        }
        k.levers(new int[][]{{m, 2, m + 2}}, S, k.sealDoor(m + 2, -3, m - 1, false, 2), false);
        var lp = k.c.puzzles.get(k.c.puzzles.size() - 1);
        k.c.puzzles.set(k.c.puzzles.size() - 1, new Canvas.PuzzleSpec(lp.type(), lp.elements(), new int[]{1}, lp.seals(), false));
        k.c.set(m, 4, m + 1, Style.slab(k.s.trimSlab, true));
        k.room(m - 2, -4, m - 3, m + 2, 0, m + 2);
        k.room(m + 2, -4, m - 2, m + 5, 0, m + 1);
        k.stairs(m + 4, -3, m - 1, S, 3, 1, k.s.brickStairs, k.s.brick);
        k.air(m + 4, -1, m + 2, m + 4, 1, m + 3);
        k.c.lockBox(m + 4, -1, m + 2, m + 4, 1, m + 3);
        k.coffer(m - 1, -3, m - 2, E, "underdeep:exploration/secret_stash");
        k.inscription(m - 2, -2, m, E, Designs.carved(k, site));
        k.c.secret("buried_reliquary", m - 1, -3, m - 2, m + 1, -1, m + 1);
    }

    static void hermitCell(Kit k, Site site) {
        int m = k.mid;
        // a door-sized patch of mossy stone in a rock face; the cell behind
        for (int x = m - 4; x <= m + 4; x++) {
            for (int y = 1; y <= 6; y++) {
                k.c.set(x, y, m + 3, Blocks.STONE);
                k.c.set(x, y, m + 4, Blocks.STONE);
            }
        }
        k.c.set(m, 1, m + 3, Blocks.MOSSY_COBBLESTONE);
        k.c.set(m, 2, m + 3, Blocks.MOSSY_COBBLESTONE);
        k.c.set(m, 1, m + 4, Blocks.AIR.getDefaultState());
        k.c.set(m, 2, m + 4, Blocks.AIR.getDefaultState());
        k.c.lockBox(m, 1, m + 4, m, 2, m + 4);
        k.room(m - 3, 0, m - 2, m + 3, 4, m + 3);
        k.c.set(m, 1, m + 3, Blocks.MOSSY_COBBLESTONE);
        k.bedRemains(m - 2, 1, m - 1, S);
        k.table(m + 2, 1, m);
        k.c.set(m + 2, 1, m - 1, Blocks.LECTERN);
        k.coffer(m - 2, 1, m + 2, E, "underdeep:exploration/hermit");
        k.inscription(m + 3, 2, m + 1, W, Designs.loose(k, site));
        k.c.set(m, 3, m, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
        k.c.secret("hermit_cell", m - 2, 1, m - 1, m + 2, 3, m + 2);
    }

    static void fungalHeart(Kit k, Site site) {
        int m = k.mid;
        for (int y = 1; y <= 9; y++) {
            k.disc(m, m, 1.6, y, Blocks.MUSHROOM_STEM, null);
        }
        k.disc(m, m, 4.6, 10, Blocks.RED_MUSHROOM_BLOCK, null);
        k.disc(m, m, 3.2, 11, Blocks.RED_MUSHROOM_BLOCK, null);
        // spores puff from a gap at the stem's foot; a hollow below
        k.c.set(m + 1, 1, m, Canvas.AIR);
        k.c.set(m + 1, 0, m, Canvas.AIR);
        k.c.lock(m + 1, 1, m);
        k.c.lock(m + 1, 0, m);
        k.room(m - 3, -6, m - 3, m + 3, -1, m + 3);
        for (int y = -5; y <= 0; y++) {
            k.c.set(m + 1, y, m, Blocks.SCAFFOLDING);
            k.c.lock(m + 1, y, m);
        }
        k.c.set(m, -5, m, ModBlocks.SPOREGLOW_SOIL);
        k.c.set(m, -4, m, dev.underdeep.registry.ModPlants.SAGECAP);
        k.coffer(m - 2, -5, m - 2, E, "underdeep:exploration/fungal");
        k.c.set(m + 2, -2, m + 2, Blocks.SHROOMLIGHT);
        k.c.secret("fungal_heart", m - 2, -5, m - 2, m + 2, -3, m + 2);
    }

    static void emberShrine(Kit k, Site site) {
        int m = k.mid;
        // a basalt wall with glowing seams; one seam is a passage
        for (int x = m - 4; x <= m + 4; x++) {
            for (int y = 1; y <= 6; y++) {
                k.c.set(x, y, m + 2, (y % 3 == 0 ? Blocks.MAGMA_BLOCK : Blocks.BASALT).getDefaultState());
            }
        }
        k.c.set(m, 1, m + 2, Canvas.AIR);
        k.c.set(m, 2, m + 2, Canvas.AIR);
        k.c.lockBox(m, 1, m + 2, m, 2, m + 2);
        k.room(m - 3, 0, m - 4, m + 3, 5, m + 2);
        k.c.set(m, 1, m + 2, Canvas.AIR);
        k.c.set(m, 2, m + 2, Canvas.AIR);
        k.c.set(m, 1, m - 3, Blocks.GILDED_BLACKSTONE);
        k.c.set(m, 2, m - 3, Blocks.LANTERN);
        k.coffer(m - 2, 1, m - 2, E, "underdeep:exploration/ruin_forge");
        k.inscription(m + 3, 2, m - 1, W, Designs.carved(k, site));
        k.c.secret("ember_shrine", m - 2, 1, m - 3, m + 2, 4, m + 1);
    }

    static void starWindow(Kit k, Site site) {
        int m = k.mid;
        k.room(m - 3, -6, m - 3, m + 3, 0, m + 3);
        // its ceiling is a window of starstone set in the cavern floor: a pattern of lights underfoot
        for (int x = m - 2; x <= m + 2; x++) {
            for (int z = m - 2; z <= m + 2; z++) {
                k.c.set(x, 0, z, ((x + z) % 2 == 0 ? ModBlocks.STARSTONE : Blocks.TINTED_GLASS).getDefaultState());
            }
        }
        k.c.set(m + 3, 0, m, Canvas.AIR);
        k.c.lock(m + 3, 0, m);
        for (int y = -5; y <= 0; y++) {
            k.c.set(m + 3, y, m, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, W));
            k.c.set(m + 4, y, m, Blocks.STONE);
            k.c.lock(m + 3, y, m);
        }
        k.coffer(m - 2, -5, m, E, "underdeep:exploration/star");
        k.inscription(m, -4, m - 3, S, Designs.loose(k, site));
        k.c.secret("star_window", m - 2, -5, m - 2, m + 2, -2, m + 2);
    }

    static void drownedBell(Kit k, Site site) {
        int m = k.mid;
        k.room(m - 3, -6, m - 3, m + 3, 0, m + 3);
        for (int x = m - 2; x <= m + 2; x++) {
            for (int z = m - 2; z <= m + 2; z++) {
                for (int y = -5; y <= -1; y++) {
                    k.c.set(x, y, z, Blocks.WATER);
                }
            }
        }
        k.c.set(m, 0, m, Canvas.AIR);
        k.c.set(m, 0, m, Blocks.WATER);
        k.c.lock(m, 0, m);
        k.c.set(m, -1, m, Blocks.BELL.getDefaultState().with(Properties.ATTACHMENT, net.minecraft.block.enums.Attachment.CEILING));
        k.c.set(m, -5, m - 2, Blocks.SEA_LANTERN);
        k.coffer(m + 2, -5, m + 2, W, "underdeep:exploration/tide");
        k.c.secret("drowned_bell", m - 2, -5, m - 2, m + 2, -2, m + 2);
    }

    static void rootHollow(Kit k, Site site) {
        int m = k.mid;
        for (int y = 0; y <= 10; y++) {
            double r = 3.2 - y * 0.12;
            k.ring(m, m, r, y, y, ModWood.DUSKOAK.log);
            if (y >= 1 && y <= 4) {
                k.air(m - 1, y, m - 1, m + 1, y, m + 1);
            }
        }
        k.c.set(m, 1, m + 3, ModWood.DUSKOAK.log);
        k.c.set(m - 3, 1, m, Canvas.AIR);
        k.c.set(m - 3, 2, m, Canvas.AIR);
        k.c.lockBox(m - 3, 1, m, m - 3, 2, m);
        k.c.set(m - 4, 1, m, dev.underdeep.registry.ModPlants.GLOWFERN);
        k.coffer(m, 1, m + 1, N, "underdeep:exploration/gardener");
        k.c.set(m + 1, 3, m - 1, Blocks.SHROOMLIGHT);
        k.c.secret("root_hollow", m - 1, 1, m - 1, m + 1, 3, m + 1);
    }

    static void crystalGeode(Kit k, Site site) {
        int m = k.mid;
        for (int x = 0; x < k.n; x++) {
            for (int y = -1; y <= 9; y++) {
                for (int z = 0; z < k.n; z++) {
                    double d = Math.sqrt((x - m) * (x - m) + (y - 4) * (y - 4) * 1.2 + (z - m) * (z - m));
                    if (d <= 5.5 && d > 4.5) {
                        k.c.set(x, y, z, (d > 5 ? Blocks.CALCITE : Blocks.AMETHYST_BLOCK).getDefaultState());
                    } else if (d <= 4.5) {
                        k.c.set(x, y, z, Canvas.AIR);
                    }
                }
            }
        }
        for (int i = 0; i < 14; i++) {
            int x = m - 3 + k.ri(7), z = m - 3 + k.ri(7);
            k.c.soft(x, 1, z, ModBlocks.LUMINITE_CLUSTER.getDefaultState());
        }
        // a glinting crack in the shell
        k.c.set(m + 5, 2, m, Canvas.AIR);
        k.c.set(m + 5, 3, m, Canvas.AIR);
        k.c.set(m + 4, 2, m, Canvas.AIR);
        k.c.set(m + 4, 3, m, Canvas.AIR);
        k.c.set(m + 6, 2, m, Canvas.AIR);
        k.c.set(m + 6, 3, m, Canvas.AIR);
        k.c.lockBox(m + 4, 2, m, m + 6, 3, m);
        k.c.set(m, 0, m, Blocks.CALCITE);
        k.coffer(m, 1, m, S, "underdeep:exploration/crystal");
        k.c.secret("crystal_geode", m - 3, 1, m - 3, m + 3, 6, m + 3);
    }
}
