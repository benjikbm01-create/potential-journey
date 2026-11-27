package dev.underdeep.explore.design;

import dev.underdeep.explore.Canvas;
import dev.underdeep.explore.ExploreBlocks;
import dev.underdeep.explore.Site;
import dev.underdeep.explore.Style;
import dev.underdeep.registry.ModBlocks;
import dev.underdeep.registry.ModWood;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

import java.util.List;

/**
 * Dungeons (Stage 9). Every dungeon has an entrance at the cavern floor, a main route through its required
 * challenges to a heart chamber (completion), optional rooms, at least one way back out that never closes, and
 * rewards spread along the way (shared chests, per-player coffers at the heart). Guardians are the restless dead of
 * the old crypts (vanilla undead) or creatures fitting the place; they wake once.
 * <p>
 * Levels: the surface at y = 0; first underground level floor at y = -7; second at y = -14.
 */
final class Dungeons {
    private Dungeons() {
    }

    static final Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;

    /**
     * A spiral stair well (5x5) at the surface entrance (x,z) down to feet level toY, with a two-wide exit toward dir
     * at the bottom. Returns the cell just outside the exit (rooms start beyond it). Corner posts and a lintel mark the
     * mouth on the surface; the top is open, so the well is visible.
     */
    static int[] descend(Kit k, int x, int z, int toY, Direction dir) {
        k.spiral(x, z, toY + 1, -1);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                k.c.weak(x + dx, toY - 1, z + dz, k.s.floor.getDefaultState());
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (edge) {
                    k.c.weak(x + dx, toY, z + dz, k.s.brick.getDefaultState());
                } else if (dx != 0 || dz != 0) {
                    k.c.set(x + dx, toY, z + dz, Canvas.AIR);
                }
                // above the cavern floor only corner posts remain: the well's mouth is open on every side
                for (int y = 1; y <= 3; y++) {
                    if (edge && !(Math.abs(dx) == 2 && Math.abs(dz) == 2)) {
                        k.c.force(x + dx, y, z + dz, Canvas.AIR);
                    }
                }
            }
        }
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            k.c.force(x + c[0], 3, z + c[1], k.s.chiseled.getDefaultState());
        }
        k.c.force(x, 1, z, Canvas.AIR);
        k.c.force(x, 2, z, Canvas.AIR);
        k.c.force(x, 3, z, Canvas.AIR);
        k.c.force(x, 0, z, Style.axis(k.s.pillar, Direction.Axis.Y));
        // the exit at the bottom
        Direction side = dir.rotateYClockwise();
        int sx = side.getOffsetX() < 0 ? -1 : 0, sz = side.getOffsetZ() < 0 ? -1 : 0;
        for (int step = 2; step <= 3; step++) {
            for (int w = 0; w <= 1; w++) {
                int px = x + dir.getOffsetX() * step + Math.abs(side.getOffsetX()) * (w + sx), pz = z + dir.getOffsetZ() * step + Math.abs(side.getOffsetZ()) * (w + sz);
                for (int y = toY; y <= toY + 2; y++) {
                    k.c.force(px, y, pz, Canvas.AIR);
                    k.c.lock(px, y, pz);
                }
                k.c.weak(px, toY - 1, pz, k.s.floor.getDefaultState());
            }
        }
        return new int[]{x + dir.getOffsetX() * 3, toY, z + dir.getOffsetZ() * 3};
    }

    /** A small gatehouse portal around a stair mouth at the surface (x,z facing dir). */
    static void portal(Kit k, int x, int z, Direction dir) {
        Direction side = dir.rotateYClockwise();
        for (int sd = -2; sd <= 3; sd++) {
            int px = x - dir.getOffsetX() + side.getOffsetX() * sd, pz = z - dir.getOffsetZ() + side.getOffsetZ() * sd;
            for (int y = 1; y <= 5; y++) {
                boolean opening = sd >= 0 && sd <= 1 && y <= 3;
                k.c.set(px, y, pz, opening ? Canvas.AIR : (y == 5 ? k.s.trim : sd == -2 || sd == 3 ? k.s.pillar : k.s.brick).getDefaultState());
            }
        }
        int lx = x - dir.getOffsetX() * 2 + side.getOffsetX() * -2, lz = z - dir.getOffsetZ() * 2 + side.getOffsetZ() * -2;
        k.standingLight(lx, 1, lz);
        k.standingLight(lx + side.getOffsetX() * 5, 1, lz + side.getOffsetZ() * 5);
    }

    static void heart(Kit k, int x1, int y1, int z1, int x2, int y2, int z2) {
        k.c.heart = new int[]{Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)};
    }

    static void guard(Kit k, String kind, int count, int x1, int y1, int z1, int x2, int y2, int z2) {
        List<Long> spawns = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            spawns.add(k.at(x1 + 1 + k.ri(Math.max(1, x2 - x1 - 1)), y1, z1 + 1 + k.ri(Math.max(1, z2 - z1 - 1))));
        }
        k.c.guard(kind, count, new int[]{Math.min(x1, x2), y1, Math.min(z1, z2), Math.max(x1, x2), y2, Math.max(z1, z2)}, spawns);
    }

    // ================================================================== small

    static void sealedCellar(Kit k, Site site) {
        int m = k.mid;
        k.shell(m - 4, m - 4, m + 4, m + 4, 0, 4, false, 0.55);
        k.air(m - 1, 1, m + 4, m, 2, m + 4);
        // the hearth bears the clue: three signs
        k.c.set(m - 3, 1, m - 3, Blocks.CAMPFIRE.getDefaultState().with(Properties.LIT, false));
        int[][] clue = {{m - 1, 2, m - 4}, {m, 2, m - 4}, {m + 1, 2, m - 4}};
        // trapdoor and ladder to the cellar
        k.c.set(m + 2, 0, m + 2, Blocks.SPRUCE_TRAPDOOR.getDefaultState().with(Properties.HORIZONTAL_FACING, N));
        k.c.lock(m + 2, 0, m + 2);
        for (int y = -6; y <= -1; y++) {
            k.c.set(m + 2, y, m + 2, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, N));
            k.c.lock(m + 2, y, m + 2);
        }
        k.room(m - 3, -7, m - 1, m + 4, -2, m + 4);
        k.air(m + 2, -6, m + 2, m + 2, -1, m + 2);
        k.barrelLoot(m - 2, -6, m + 3, "underdeep:exploration/ruin_common");
        k.shelf(m - 2, -6, m + 2, false);
        // dart trap in the antechamber
        k.trap("darts", new int[][]{{m, -6, m + 1}}, new int[][]{{m - 3, -5, m + 1}});
        k.dartVent(m - 3, -5, m + 1, E);
        // the sealed vault
        long[] seal = k.sealDoor(m, -6, m - 1, true, 2);
        k.dials(new int[][]{{m - 2, -5, m}, {m - 1, -5, m - 1 + 0}, {m + 3, -5, m}}, S, clue, S, seal, true);
        k.c.set(m - 1, -5, m - 1, Style.facing(ExploreBlocks.RUNE_DIAL, S).with(ExploreBlocks.SYMBOL, 0));
        k.room(m - 3, -7, m - 7, m + 4, -2, m - 1);
        k.coffer(m, -6, m - 5, S, "underdeep:exploration/coffer_small");
        k.chest(m + 2, -6, m - 6, S, "underdeep:exploration/dungeon_small");
        k.inscription(m - 3, -5, m - 4, E, Designs.carved(k, site));
        heart(k, m - 2, -6, m - 6, m + 3, -3, m - 2);
        k.weather(0.4, true);
    }

    static void smugglersDen(Kit k, Site site) {
        int m = k.mid;
        // a rough cave mouth
        for (int x = m - 3; x <= m + 3; x++) {
            for (int y = 1; y <= 4; y++) {
                if (Math.abs(x - m) + y > 5) {
                    k.c.set(x, y, m + 5, k.s.rubble);
                }
            }
        }
        int[] b = descend(k, m, m + 4, -6, N);
        k.corridor(b[0], b[2], m, m - 2, -6, 2, 3);
        k.room(m - 8, -7, m - 8, m + 1, -2, m - 1);
        k.room(m + 1, -7, m - 8, m + 8, -2, m - 1);
        k.air(m + 1, -6, m - 5, m + 1, -4, m - 4);
        // bunks, barrels, a table with cards (bones)
        for (int x = m - 7; x <= m - 3; x += 2) {
            k.bedRemains(x, -6, m - 7, S);
        }
        k.table(m - 4, -6, m - 3);
        k.chair(m - 5, -6, m - 3, E);
        k.chest(m - 7, -6, m - 2, E, "underdeep:exploration/smuggler");
        k.barrel(m - 7, -6, m - 4);
        k.barrel(m - 7, -5, m - 4);
        k.trap("rockfall", new int[][]{{m - 2, -6, m - 5}}, new int[][]{{m - 2, -2, m - 5}, {m - 3, -2, m - 5}});
        k.c.fixed(m - 2, -2, m - 5, ExploreBlocks.LOOSE_CEILING.getDefaultState());
        k.c.fixed(m - 3, -2, m - 5, ExploreBlocks.LOOSE_CEILING.getDefaultState());
        // the hoist: a counterweight lifts the stash gate
        long[] seal = k.sealDoor(m + 8, -6, m - 5, false, 2);
        k.counterweight(new int[]{m + 6, -6, m - 7}, seal, true);
        k.room(m + 8, -7, m - 7, m + 12 < k.n ? m + 12 : k.n - 1, -2, m - 2);
        k.coffer(m + 10, -6, m - 4, W, "underdeep:exploration/coffer_small");
        k.chest(m + 10, -6, m - 6, W, "underdeep:exploration/smuggler");
        k.inscription(m + 5, -5, m - 8, S, Designs.loose(k, site));
        guard(k, "spider", 2, m + 1, -6, m - 8, m + 8, -3, m - 1);
        heart(k, m + 9, -6, m - 6, m + 11, -3, m - 3);
        k.weather(0.45, true);
    }

    static void drownedCrypt(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m + 4, 3, 0, k.s.floor, k.s.brick);
        k.pillar(m - 2, m + 6, 1, 4, true);
        k.pillar(m + 2, m + 6, 1, 4, true);
        int[] b = descend(k, m, m + 4, -7, N);
        // flooded crypt: two burial halls and an inner shrine, full of still water with an air pocket
        k.room(m - 7, -8, m - 6, m + 7, -2, b[2] - 1);
        k.air(b[0], -7, b[2] - 1, b[0] + 1, -5, b[2] - 1);
        for (int x = m - 6; x <= m + 6; x++) {
            for (int z = m - 5; z <= b[2] - 2; z++) {
                for (int y = -7; y <= -4; y++) {
                    k.c.set(x, y, z, Blocks.WATER);
                }
            }
        }
        for (int x = m - 5; x <= m + 5; x += 2) {
            k.c.set(x, -7, m - 4, k.s.block);
            k.c.set(x, -7, m + 1, k.s.block);
        }
        long[] seal = k.sealDoor(m - 1, -7, m - 6, true, 2);
        k.offerings(new int[][]{{m - 4, -6, m - 5}, {m + 4, -6, m - 5}, {m, -6, m + 1}}, seal, true);
        k.room(m - 4, -8, m - 12, m + 4, -2, m - 6);
        k.coffer(m, -7, m - 10, S, "underdeep:exploration/coffer_small");
        k.chest(m + 2, -7, m - 10, S, "underdeep:exploration/tide");
        k.inscription(m - 4, -5, m - 9, E, Designs.carved(k, site));
        guard(k, "drowned", 2, m - 6, -7, m - 5, m + 6, -4, b[2] - 2);
        heart(k, m - 3, -7, m - 11, m + 3, -3, m - 7);
        k.weather(0.25, false);
    }

    static void kilnVault(Kit k, Site site) {
        int m = k.mid;
        for (int y = 0; y <= 5; y++) {
            k.ring(m, m + 4, 3.5 - y * 0.4, y, y, Blocks.BRICKS);
        }
        k.air(m - 2, 1, m + 2, m + 2, 4, m + 6);
        int[] b = descend(k, m, m + 3, -7, N);
        k.corridor(b[0], b[2], b[0], m - 3, -7, 2, 3);
        // steam corridor: vents cycle, you pass between bursts
        for (int z = m - 3; z <= b[2] - 1; z += 3) {
            k.vent(ExploreBlocks.STEAM_VENT, b[0], -7, z);
        }
        k.c.trap("steam", List.of(), List.of(k.at(b[0], -7, m - 3)));
        k.room(m - 6, -8, m - 9, m + 6, -2, m - 3);
        k.braziers("brazier_order", new int[][]{{m - 4, -7, m - 7}, {m - 1, -7, m - 8}, {m + 2, -7, m - 8}, {m + 5, -7, m - 7}}, null, false);
        long[] seal = k.sealDoor(m, -7, m - 9, true, 1);
        k.c.puzzles.set(k.c.puzzles.size() - 1, new Canvas.PuzzleSpec("brazier_order", k.c.puzzles.get(k.c.puzzles.size() - 1).elements(),
                k.c.puzzles.get(k.c.puzzles.size() - 1).solution(), Kit.seals(seal), true));
        k.room(m - 3, -8, m - 14, m + 3, -2, m - 9);
        k.coffer(m, -7, m - 12, S, "underdeep:exploration/coffer_small");
        k.chest(m + 2, -7, m - 13, S, "underdeep:exploration/ruin_forge");
        k.inscription(m - 6, -5, m - 6, E, Designs.carved(k, site));
        heart(k, m - 2, -7, m - 13, m + 2, -3, m - 10);
        k.weather(0.3, false);
    }

    static void sporeWarren(Kit k, Site site) {
        int m = k.mid;
        for (int y = 1; y <= 6; y++) {
            k.c.set(m + 3, y, m + 5, Blocks.MUSHROOM_STEM);
        }
        k.disc(m + 3, m + 5, 3, 7, Blocks.BROWN_MUSHROOM_BLOCK, null);
        int[] b = descend(k, m, m + 6, -7, N);
        // winding tunnels with spore vents
        k.corridor(b[0], b[2], b[0], m, -7, 2, 3);
        k.corridor(b[0], m, m - 7, m, -7, 2, 3);
        k.corridor(m - 7, m, m - 7, m - 7, -7, 2, 3);
        k.vent(ExploreBlocks.SPORE_VENT, b[0], -7, m + 2);
        k.vent(ExploreBlocks.SPORE_VENT, m - 4, -7, m);
        k.c.trap("spores", List.of(), List.of(k.at(b[0], -7, m + 2), k.at(m - 4, -7, m)));
        k.room(m - 10, -8, m - 12, m - 3, -2, m - 6);
        k.plates(new int[][]{{m - 9, -7, m - 9}, {m - 7, -7, m - 11}, {m - 5, -7, m - 9}, {m - 7, -7, m - 7}}, k.sealDoor(m - 3, -7, m - 10, false, 2), true);
        k.room(m - 3, -8, m - 12, m + 4, -2, m - 6);
        k.coffer(m + 1, -7, m - 9, W, "underdeep:exploration/coffer_small");
        k.chest(m + 2, -7, m - 11, W, "underdeep:exploration/fungal");
        for (int i = 0; i < 6; i++) {
            k.c.soft(m - 9 + k.ri(5), -7, m - 11 + k.ri(4), dev.underdeep.registry.ModPlants.PUFFCAP.getDefaultState());
        }
        k.inscription(m - 10, -5, m - 9, E, Designs.carved(k, site));
        guard(k, "spider", 2, m - 10, -7, m - 12, m - 3, -4, m - 6);
        heart(k, m - 2, -7, m - 11, m + 3, -3, m - 7);
        k.weather(0.5, true);
    }

    static void frostCistern(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m + 4, 3, 0, Blocks.PACKED_ICE, k.s.brick);
        int[] b = descend(k, m, m + 4, -8, N);
        k.room(m - 7, -9, m - 9, m + 7, -2, b[2] - 1);
        k.air(b[0], -8, b[2] - 1, b[0] + 1, -6, b[2] - 1);
        for (int x = m - 6; x <= m + 6; x++) {
            for (int z = m - 8; z <= b[2] - 2; z++) {
                k.c.set(x, -9, z, (x + z) % 3 == 0 ? Blocks.BLUE_ICE.getDefaultState() : Blocks.PACKED_ICE.getDefaultState());
            }
        }
        // an open pool (water source for the buckets) and the dry basins
        k.disc(m + 4, m, 1.5, -9, Blocks.WATER, null);
        k.vent(ExploreBlocks.FROST_VENT, m - 3, -9, m + 1);
        k.vent(ExploreBlocks.FROST_VENT, m + 1, -9, m - 4);
        k.c.trap("frost", List.of(), List.of(k.at(m - 3, -9, m + 1), k.at(m + 1, -9, m - 4)));
        long[] seal = k.sealDoor(m - 1, -8, m - 9, true, 2);
        k.basins(new int[][]{{m - 5, -8, m - 7}, {m - 1, -8, m - 3}, {m + 5, -8, m - 7}}, seal, true);
        k.chest(m - 6, -8, m - 2, E, "underdeep:exploration/dungeon_small");
        k.c.set(m - 6, -8, m - 1, Blocks.CAULDRON);
        k.room(m - 3, -9, m - 14, m + 3, -3, m - 9);
        k.coffer(m, -8, m - 12, S, "underdeep:exploration/coffer_small");
        k.inscription(m + 3, -6, m - 11, W, Designs.carved(k, site));
        heart(k, m - 2, -8, m - 13, m + 2, -4, m - 10);
        k.weather(0.2, false);
    }

    static void echoVault(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m + 3, 3, 0, k.s.floor, k.s.trim);
        for (int[] p : new int[][]{{m - 3, m + 5}, {m + 3, m + 5}}) {
            k.pillar(p[0], p[1], 1, 5, true);
        }
        int[] b = descend(k, m, m + 3, -7, N);
        k.room(m - 6, -8, m - 8, m + 6, -1, b[2] - 1);
        k.air(b[0], -7, b[2] - 1, b[0] + 1, -5, b[2] - 1);
        long[] seal = k.sealDoor(m - 1, -7, m - 8, true, 2);
        k.chimes(new int[][]{{m - 4, -7, m - 3}, {m, -7, m - 5}, {m + 4, -7, m - 3}}, seal, true);
        k.memory(new int[]{m, -7, m - 1}, new int[][]{{m - 5, -6, m - 6}, {m - 5, -6, m - 4}, {m + 5, -6, m - 6}, {m + 5, -6, m - 2}},
                k.sealDoor(m + 6, -7, m - 4, false, 1), false);
        k.room(m + 6, -8, m - 7, Math.min(k.n - 1, m + 10), -3, m - 2);
        k.chest(m + 8, -7, m - 4, W, "underdeep:exploration/dungeon_small");
        k.room(m - 3, -8, m - 13, m + 3, -2, m - 8);
        k.coffer(m, -7, m - 11, S, "underdeep:exploration/coffer_small");
        k.inscription(m - 3, -5, m - 10, E, Designs.carved(k, site));
        heart(k, m - 2, -7, m - 12, m + 2, -3, m - 9);
        k.weather(0.25, false);
    }

    // ================================================================== medium

    static void lanternWorks(Kit k, Site site) {
        int m = k.mid;
        k.shell(m - 8, m + 2, m + 8, m + 12, 0, 6, true, 0.3);
        k.air(m - 1, 1, m + 12, m + 1, 3, m + 12);
        k.c.set(m - 6, 1, m + 10, Blocks.CARTOGRAPHY_TABLE);
        k.chest(m + 6, 1, m + 10, W, "underdeep:exploration/league_cache");
        int[] b = descend(k, m, m + 6, -8, N);
        // the hall of lamps (darkness trap)
        k.room(m - 9, -9, m - 6, m + 9, -2, b[2] - 1);
        k.air(b[0], -8, b[2] - 1, b[0] + 1, -6, b[2] - 1);
        int[][] lamps = {{m - 7, -4, m - 4}, {m + 7, -4, m - 4}, {m - 7, -4, m + 1}, {m + 7, -4, m + 1}};
        for (int[] l : lamps) {
            k.c.fixed(l[0], l[1], l[2], ExploreBlocks.ANCIENT_LAMP.getDefaultState().with(ExploreBlocks.LIT, true));
        }
        k.trap("darkness", new int[][]{{m, -8, m - 1}}, lamps);
        // the engine restores the lift doors to the works below
        long[] liftSeal = k.sealDoor(m - 1, -8, m - 6, true, 2);
        k.engine(new int[]{m + 6, -8, m - 5}, liftSeal, true);
        for (int x = m + 1; x <= m + 5; x++) {
            k.c.set(x, -9, m - 5, Blocks.OXIDIZED_COPPER_GRATE);
        }
        // mirror room (optional) on the east side holds the lantern vault (secret)
        k.room(m + 9, -9, m - 4, k.n - 2, -2, m + 4);
        k.air(m + 9, -8, m - 1, m + 9, -6, m);
        k.beam(new int[][]{{m + 10, m + 3}, {m + 12, m + 3}, {m + 12, m - 3}}, -7, E, k.sealDoor(k.n - 2, -8, m - 1, false, 1), false);
        k.coffer(k.n - 2, -8, m + 1, W, "underdeep:exploration/lantern_vault");
        k.c.secret("lantern_vault", m + 10, -8, m - 3, k.n - 3, -5, m + 3);
        // the works
        int[] b2 = descend(k, m - 1, m - 6, -15, N);
        k.room(m - 8, -16, m - 16 < 0 ? 0 : m - 14, m + 8, -9, b2[2] - 1);
        k.air(b2[0], -15, b2[2] - 1, b2[0] + 1, -13, b2[2] - 1);
        for (int x = m - 6; x <= m + 6; x += 4) {
            k.c.set(x, -15, m - 11, Blocks.OXIDIZED_COPPER_BULB);
            k.c.set(x, -14, m - 11, Blocks.OXIDIZED_COPPER_GRATE);
        }
        k.coffer(m, -15, m - 13, S, "underdeep:exploration/coffer_medium");
        k.chest(m - 6, -15, m - 13, E, "underdeep:exploration/dungeon_medium");
        k.inscription(m + 8, -13, m - 10, W, Designs.carved(k, site));
        heart(k, m - 4, -15, m - 13, m + 4, -11, m - 9);
        k.weather(0.3, true);
    }

    static void wardenBarracks(Kit k, Site site) {
        int m = k.mid;
        k.shell(m - 10, m - 10, m + 10, m + 10, 0, 6, false, 0.35);
        k.air(m - 1, 1, m + 10, m + 1, 4, m + 10);
        k.arch(m - 2, m + 2, m + 10, 1, true);
        // bunk rooms west, mess hall east
        for (int z = m - 8; z <= m + 6; z += 3) {
            k.bedRemains(m - 9, 1, z, E);
            k.bedRemains(m - 6, 1, z, W);
        }
        for (int x = m + 3; x <= m + 8; x += 2) {
            k.table(x, 1, m);
            k.chair(x, 1, m + 1, N);
        }
        k.c.set(m + 9, 1, m - 8, Blocks.SMOKER);
        // the alarm gong in the yard
        k.c.fixed(m, 1, m - 3, ExploreBlocks.ALARM_GONG.getDefaultState());
        guard(k, "restless", 4, m - 4, 1, m - 9, m + 4, 4, m - 4);
        k.trap("alarm:0", new int[][]{{m, 1, m + 4}, {m - 1, 1, m + 4}}, new int[][]{{m, 1, m - 3}});
        // captain's quarters with the vault key (guarded)
        k.shell(m - 9, m - 9, m - 3, m - 4, 0, 4, true, 0);
        k.air(m - 3, 1, m - 7, m - 3, 2, m - 7);
        k.keyLock(new int[]{m + 4, -6, m - 7}, new int[]{m - 7, 1, m - 7}, k.sealDoor(m + 2, -6, m - 7, true, 2), true);
        k.chest(m - 8, 1, m - 5, E, "underdeep:exploration/gatehouse");
        // the vault below
        int[] b = descend(k, m + 6, m + 4, -7, N);
        k.room(m + 1, -8, m - 9, m + 9, -2, b[2] - 1);
        k.air(b[0], -7, b[2] - 1, b[0] + 1, -5, b[2] - 1);
        k.c.set(m + 4, -6, m - 7, ExploreBlocks.KEY_LOCK);
        k.room(m + 1, -8, m - 14 < 0 ? 0 : m - 13, m + 9, -2, m - 8);
        k.coffer(m + 5, -7, m - 11, S, "underdeep:exploration/coffer_medium");
        k.chest(m + 7, -7, m - 11, S, "underdeep:exploration/armory");
        // a sealed commander's room: reserved for a later age, silent
        k.room(m - 9, -8, m - 13 < 0 ? 0 : m - 12, m, -2, m - 4);
        k.sealDoor(m, -7, m - 9, false, 2);
        k.c.reserved = new int[]{m - 9, -8, m - 12, m, -2, m - 4};
        k.inscription(m + 9, -5, m - 4, W, Designs.carved(k, site));
        heart(k, m + 2, -7, m - 12, m + 8, -3, m - 9);
        k.weather(0.35, true);
    }

    static void tideTemple(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m, k.mid - 2, 0, k.s.floor, k.s.brick);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            k.pillar((int) Math.round(m + Math.cos(a) * 7), (int) Math.round(m + Math.sin(a) * 7), 1, 7, true);
        }
        k.disc(m, m, 4, 8, k.s.brick, k.s.trim);
        int[] b = descend(k, m, m + 2, -8, N);
        // upper gallery (dry) with levers; lower sanctum flooded
        k.room(m - 9, -9, m - 9, m + 9, -2, b[2] - 1);
        k.air(b[0], -8, b[2] - 1, b[0] + 1, -6, b[2] - 1);
        long[] seal = k.sealDoor(m - 1, -8, m - 9, true, 2);
        k.levers(new int[][]{{m - 3, -6, m - 8}, {m - 1, -6, m - 8}, {m + 3, -6, m - 8}, {m + 5, -6, m - 8}}, S, null, true);
        var lp = k.c.puzzles.get(k.c.puzzles.size() - 1);
        k.c.puzzles.set(k.c.puzzles.size() - 1, new Canvas.PuzzleSpec(lp.type(), lp.elements(), lp.solution(), Kit.seals(seal), true));
        k.room(m - 6, -16, m - 15 < 0 ? 0 : m - 14, m + 6, -9, m - 8);
        k.stairs(m - 1, -15, m - 9, S, 7, 2, k.s.brickStairs, k.s.brick);
        for (int x = m - 5; x <= m + 5; x++) {
            for (int z = m - 13; z <= m - 10; z++) {
                for (int y = -15; y <= -13; y++) {
                    k.c.set(x, y, z, Blocks.WATER);
                }
            }
        }
        k.offerings(new int[][]{{m - 4, -15, m - 12}, {m + 4, -15, m - 12}}, k.sealDoor(m - 1, -15, m - 14, true, 2), false);
        k.coffer(m, -14, m - 9, S, "underdeep:exploration/coffer_medium");
        k.chest(m + 2, -15, m - 9, S, "underdeep:exploration/tide");
        guard(k, "drowned", 3, m - 5, -15, m - 13, m + 5, -12, m - 10);
        k.inscription(m - 9, -6, m - 4, E, Designs.carved(k, site));
        heart(k, m - 3, -14, m - 10, m + 3, -11, m - 8);
        k.weather(0.3, false);
    }

    static void seedVault(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m + 2, 5, 0, Blocks.MOSS_BLOCK, k.s.brick);
        for (int y = 1; y <= 3; y++) {
            k.ring(m, m + 2, 2.5, y, y, k.s.brick);
        }
        int[] b = descend(k, m, m + 2, -8, N);
        k.room(m - 8, -9, m - 8, m + 8, -2, b[2] - 1);
        k.air(b[0], -8, b[2] - 1, b[0] + 1, -6, b[2] - 1);
        k.plates(new int[][]{{m - 5, -8, m - 5}, {m - 2, -8, m - 3}, {m + 2, -8, m - 5}, {m + 5, -8, m - 3}}, k.sealDoor(m - 1, -8, m - 8, true, 2), true);
        // planters with dead and living seeds
        for (int x = m - 7; x <= m + 7; x += 2) {
            k.c.set(x, -8, b[2] - 2, Blocks.ROOTED_DIRT);
            if (!k.s.plants.isEmpty()) {
                k.c.set(x, -7, b[2] - 2, k.pick(k.s.plants).getDefaultState());
            }
        }
        // the inner vault: two heavy plates must be pressed at once (together, or weigh one down)
        k.room(m - 7, -9, m - 15 < 0 ? 0 : m - 14, m + 7, -2, m - 8);
        k.twinPlates(new int[]{m - 6, -8, m - 12}, new int[]{m + 6, -8, m - 12}, k.sealDoor(m - 1, -8, m - 14 < 0 ? 0 : m - 14, true, 2), true);
        k.room(m - 3, -9, 0, m + 3, -3, m - 14 < 0 ? 0 : m - 14);
        k.coffer(m, -8, 2, S, "underdeep:exploration/seed_vault");
        k.shelf(m - 2, -8, 1, false);
        k.shelf(m + 2, -8, 1, false);
        k.inscription(m - 7, -6, m - 11, E, Designs.carved(k, site));
        heart(k, m - 2, -8, 1, m + 2, -4, Math.max(2, m - 15));
        k.weather(0.3, false);
    }

    static void starObservatory(Kit k, Site site) {
        int m = k.mid;
        int h = 14;
        k.ring(m, m, 6, 0, h, k.s.brick);
        k.disc(m, m, 5.5, 0, k.s.floor, k.s.floorAlt);
        k.air(m - 5, 1, m - 5, m + 5, h, m + 5);
        k.air(m - 1, 1, m + 6, m + 1, 3, m + 6);
        k.c.set(m, 1, m + 6, Canvas.AIR);
        for (int y = h + 1; y <= h + 5; y++) {
            k.ring(m, m, 6.0 - (y - h) * 1.1, y, y, k.s.glass);
        }
        // the telescope
        for (int i = 0; i < 7; i++) {
            k.c.set(m + i / 2, 4 + i, m, k.s.metal);
        }
        k.c.set(m + 3, 11, m, Blocks.AMETHYST_BLOCK);
        // a light beam from a lens must be guided to the receiver under the telescope
        long[] seal = k.sealDoor(m - 1, 0, m - 2, true, 2);
        k.beam(new int[][]{{m - 4, m + 3}, {m - 4, m - 3}, {m + 3, m - 3}, {m + 3, m + 2}}, 1, N, null, true);
        var bp = k.c.puzzles.get(k.c.puzzles.size() - 1);
        k.c.puzzles.set(k.c.puzzles.size() - 1, new Canvas.PuzzleSpec(bp.type(), bp.elements(), bp.solution(), Kit.seals(seal), true));
        // the seal opens a stair into the crypt below (observatory crypt, also the heart)
        k.c.set(m - 1, 0, m - 2, ExploreBlocks.ANCIENT_SEAL);
        k.c.set(m, 0, m - 2, ExploreBlocks.ANCIENT_SEAL);
        k.room(m - 5, -7, m - 6, m + 5, -1, m + 3);
        for (int y = -6; y <= -1; y++) {
            k.c.set(m - 1, y, m - 2, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, S));
            k.c.set(m, y, m - 2, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, S));
            k.c.set(m - 1, y, m - 3, k.s.brick);
            k.c.set(m, y, m - 3, k.s.brick);
            k.c.lockBox(m - 1, y, m - 2, m, y, m - 2);
        }
        k.coffer(m + 3, -6, m + 1, W, "underdeep:exploration/coffer_medium");
        k.chest(m - 4, -6, m + 1, E, "underdeep:exploration/star");
        k.inscription(m + 5, -4, m - 2, W, Designs.carved(k, site));
        k.c.secret("observatory_crypt", m - 4, -6, m - 1, m + 4, -3, m + 2);
        k.dials(new int[][]{{m - 5, 2, m}, {m - 5, 2, m + 1}}, E, new int[][]{{m + 5, 3, m}, {m + 5, 3, m + 1}}, W,
                k.sealDoor(m - 5, -6, m - 5, true, 1), false);
        heart(k, m - 4, -6, m - 1, m + 4, -3, m + 2);
        k.weather(0.2, false);
    }

    static void stormworks(Kit k, Site site) {
        int m = k.mid;
        k.shell(m - 6, m + 4, m + 6, m + 12, 0, 6, true, 0.3);
        k.air(m - 1, 1, m + 12, m + 1, 3, m + 12);
        int[] b = descend(k, m, m + 6, -8, N);
        // the generator pit, crossed by a crumbling bridge (a water pool breaks the fall; a ladder climbs back)
        k.room(m - 10, -16, m - 10, m + 10, -2, b[2] - 1);
        k.air(b[0], -8, b[2] - 1, b[0] + 1, -6, b[2] - 1);
        for (int x = m - 9; x <= m + 9; x++) {
            for (int z = m - 9; z <= b[2] - 2; z++) {
                for (int y = -15; y <= -9; y++) {
                    k.c.set(x, y, z, Canvas.AIR);
                }
                k.c.set(x, -16, z, (x + z) % 2 == 0 ? ModBlocks.STORMSTONE.getDefaultState() : Blocks.WATER.getDefaultState());
            }
        }
        for (int x = m - 9; x <= m + 9; x++) {
            for (int z = m - 9; z <= b[2] - 2; z++) {
                k.c.set(x, -16, z, Blocks.WATER.getDefaultState());
                k.c.set(x, -17, z, ModBlocks.STORMSTONE.getDefaultState());
            }
        }
        k.crumbling(m - 1, m - 8, m + 1, b[2] - 2, -9);
        for (int y = -15; y <= -9; y++) {
            k.c.set(m + 9, y, m - 2, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, W));
            k.c.lock(m + 9, y, m - 2);
        }
        k.floor(m + 7, m - 3, m + 9, m - 1, -9, k.s.floor, null);
        // the far ledge: restore power to raise the gate
        k.floor(m - 9, m - 9, m + 9, m - 7, -9, k.s.floor, k.s.floorAlt);
        long[] seal = k.sealDoor(m - 1, -8, m - 10, true, 2);
        k.engine(new int[]{m + 5, -8, m - 9}, seal, true);
        for (int x = m - 4; x <= m + 4; x++) {
            k.c.set(x, -8, m - 9, ModBlocks.STORMSTONE);
        }
        k.c.set(m - 6, -8, m - 8, Blocks.REDSTONE_TORCH.getDefaultState().getBlock() == null ? k.s.brick : Blocks.LEVER);
        k.dartVent(m - 9, -7, m - 8, E);
        k.trap("darts", new int[][]{{m - 4, -8, m - 8}}, new int[][]{{m - 9, -7, m - 8}});
        k.room(m - 4, -9, m - 15 < 0 ? 0 : m - 15, m + 4, -2, m - 10);
        k.coffer(m, -8, m - 13, S, "underdeep:exploration/coffer_medium");
        k.chest(m + 2, -8, m - 13, S, "underdeep:exploration/storm");
        k.inscription(m - 4, -6, m - 12, E, Designs.carved(k, site));
        heart(k, m - 3, -8, m - 14, m + 3, -4, m - 11);
        k.weather(0.3, false);
    }

    static void ossuary(Kit k, Site site) {
        int m = k.mid;
        k.shell(m - 4, m + 4, m + 4, m + 10, 0, 5, true, 0.2);
        k.air(m - 1, 1, m + 10, m, 3, m + 10);
        int[] b = descend(k, m, m + 5, -8, N);
        // catacomb corridors lined with bone niches
        k.corridor(b[0], b[2], b[0], m - 2, -8, 2, 3);
        for (int z = m - 2; z <= b[2]; z += 2) {
            k.c.set(b[0] - 2, -6, z, Blocks.BONE_BLOCK);
            k.c.set(b[0] + 3, -6, z, Blocks.SKELETON_SKULL.getDefaultState().getBlock() == null ? Blocks.BONE_BLOCK : Blocks.BONE_BLOCK);
        }
        k.c.fixed(b[0], -4, m + 1, ExploreBlocks.LOOSE_CEILING.getDefaultState());
        k.c.fixed(b[0] + 1, -4, m + 1, ExploreBlocks.LOOSE_CEILING.getDefaultState());
        k.trap("rockfall", new int[][]{{b[0], -7, m + 1}, {b[0] + 1, -7, m + 1}}, new int[][]{{b[0], -4, m + 1}, {b[0] + 1, -4, m + 1}});
        k.room(m - 8, -9, m - 10, m + 8, -2, m - 2);
        k.memory(new int[]{m, -8, m - 4}, new int[][]{{m - 6, -7, m - 9}, {m - 2, -7, m - 9}, {m + 2, -7, m - 9}, {m + 6, -7, m - 9}},
                k.sealDoor(m - 1, -8, m - 10, true, 2), true);
        for (int x = m - 7; x <= m + 7; x += 3) {
            k.skeleton(x, -8, m - 3);
        }
        guard(k, "restless", 3, m - 8, -8, m - 10, m + 8, -4, m - 2);
        k.room(m - 4, -9, m - 15 < 0 ? 0 : m - 15, m + 4, -3, m - 10);
        k.coffer(m, -8, m - 13, S, "underdeep:exploration/coffer_medium");
        k.chest(m + 3, -8, m - 13, W, "underdeep:exploration/silent");
        // the reliquary: behind the bones of the west niche
        k.room(m - 12 < 0 ? 0 : m - 12, -9, m - 9, m - 8, -5, m - 5);
        k.c.breakable(m - 8, -8, m - 7, Blocks.BONE_BLOCK.getDefaultState());
        k.c.breakable(m - 8, -7, m - 7, Blocks.BONE_BLOCK.getDefaultState());
        k.coffer(m - 10, -8, m - 7, E, "underdeep:exploration/secret_stash");
        k.c.secret("ossuary_reliquary", m - 11, -8, m - 8, m - 9, -6, m - 6);
        k.inscription(m - 4, -6, m - 12, E, Designs.carved(k, site));
        heart(k, m - 3, -8, m - 14, m + 3, -4, m - 11);
        k.weather(0.35, true);
    }

    // ================================================================== large

    static void wrightVault(Kit k, Site site) {
        int m = k.mid;
        // the gate court
        k.floor(m - 10, m + 8, m + 10, m + 18, 0, k.s.floor, k.s.floorAlt);
        for (int x : new int[]{m - 9, m + 9}) {
            k.pillar(x, m + 10, 1, 10, true);
            k.pillar(x, m + 16, 1, 10, true);
        }
        k.statue(m - 5, 1, m + 17, S, 4);
        k.statue(m + 5, 1, m + 17, S, 4);
        portal(k, m, m + 12, N);
        int[] b = descend(k, m, m + 12, -10, N);
        // hub
        int hubZ1 = m - 4, hubZ2 = b[2] - 1;
        k.room(m - 7, -11, hubZ1, m + 7, -3, hubZ2);
        k.air(b[0], -10, b[2] - 1, b[0] + 1, -8, b[2] - 1);
        k.lantern(m, -4, (hubZ1 + hubZ2) / 2);
        // three wings: each holds one key mechanism for the great door
        long[] door = k.sealDoor(m - 1, -10, hubZ1, true, 2);
        // west wing: dials (clues are carved on the hub floor)
        k.room(m - 18, -11, hubZ1, m - 7, -4, hubZ2);
        k.air(m - 7, -10, hubZ1 + 2, m - 7, -8, hubZ1 + 3);
        k.dials(new int[][]{{m - 16, -9, hubZ1 + 1}, {m - 13, -9, hubZ1 + 1}, {m - 10, -9, hubZ1 + 1}}, S,
                new int[][]{{m - 3, -11, hubZ2 - 1}, {m, -11, hubZ2 - 1}, {m + 3, -11, hubZ2 - 1}}, N, door, true);
        guard(k, "restless", 3, m - 18, -10, hubZ1, m - 8, -6, hubZ2);
        // east wing: braziers in order
        k.room(m + 7, -11, hubZ1, m + 18, -4, hubZ2);
        k.air(m + 7, -10, hubZ1 + 2, m + 7, -8, hubZ1 + 3);
        k.braziers("brazier_order", new int[][]{{m + 9, -10, hubZ1 + 2}, {m + 12, -10, hubZ1 + 2}, {m + 15, -10, hubZ1 + 2}, {m + 12, -10, hubZ2 - 2}},
                door, true);
        k.chest(m + 17, -10, hubZ2 - 1, W, "underdeep:exploration/dungeon_large");
        // the crumbling causeway on the way to the inner vault
        int innerZ2 = hubZ1 - 1, innerZ1 = Math.max(1, hubZ1 - 18);
        k.room(m - 8, -19, innerZ1, m + 8, -3, hubZ1);
        for (int x = m - 7; x <= m + 7; x++) {
            for (int z = innerZ1 + 1; z <= innerZ2 - 1; z++) {
                for (int y = -18; y <= -11; y++) {
                    k.c.set(x, y, z, Canvas.AIR);
                }
                k.c.set(x, -19, z, Blocks.WATER);
            }
        }
        k.crumbling(m - 1, innerZ1 + 5, m + 1, innerZ2, -11);
        k.floor(m - 3, innerZ1 + 1, m + 3, innerZ1 + 4, -11, k.s.floor, k.s.floorAlt);
        // a fall is not the end: a ladder climbs out of the pool to a ledge on the vault side
        k.floor(m + 4, innerZ1 + 1, m + 7, innerZ1 + 4, -11, k.s.floor, null);
        for (int y = -18; y <= -11; y++) {
            k.c.fixed(m + 7, y, innerZ1 + 5, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, W));
        }
        k.trap("darts", new int[][]{{m, -10, innerZ2 - 3}}, new int[][]{{m - 8, -9, innerZ2 - 3}});
        k.dartVent(m - 8, -9, innerZ2 - 3, E);
        // inner vault
        k.coffer(m - 1, -10, innerZ1 + 2, S, "underdeep:exploration/coffer_large");
        k.coffer(m + 1, -10, innerZ1 + 2, S, "underdeep:exploration/coffer_large");
        k.chest(m + 3, -10, innerZ1 + 1, S, "underdeep:exploration/dungeon_large");
        k.inscription(m - 3, -9, innerZ1 + 1, S, Designs.carved(k, site));
        heart(k, m - 3, -10, innerZ1 + 1, m + 3, -7, innerZ1 + 4);
        // shortcut: barred passage from the vault ledge back to the east wing (a lever on each side)
        k.corridor(m + 8, innerZ1 + 3, m + 13, innerZ1 + 3, -11, 1, 3);
        k.corridor(m + 13, innerZ1 + 3, m + 13, hubZ1 + 1, -11, 1, 3);
        k.timedGate(new int[][]{{m + 6, -10, innerZ1 + 3}, {m + 11, -10, innerZ1 + 3}}, N,
                new int[][]{{m + 8, -10, innerZ1 + 3}, {m + 8, -9, innerZ1 + 3}});
        // the archive (secret) behind the west wing's cracked course
        k.room(m - 18, -11, innerZ1 + 2, m - 9, -6, hubZ1);
        k.c.breakable(m - 15, -10, hubZ1, k.s.cracked.getDefaultState());
        k.c.breakable(m - 15, -9, hubZ1, k.s.cracked.getDefaultState());
        for (int x = m - 17; x <= m - 10; x += 2) {
            k.c.set(x, -10, innerZ1 + 3, Blocks.CHISELED_BOOKSHELF);
        }
        k.coffer(m - 14, -10, innerZ1 + 4, S, "underdeep:exploration/library_secret");
        k.inscription(m - 11, -9, innerZ1 + 3, S, Designs.loose(k, site));
        k.c.secret("vault_archive", m - 17, -10, innerZ1 + 3, m - 10, -7, hubZ1 - 1);
        k.weather(0.25, true);
    }

    static void sunkenArchive(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m + 10, 6, 0, k.s.floor, k.s.brick);
        k.ring(m, m + 10, 6, 1, 2, k.s.brickWall);
        int[] b = descend(k, m, m + 10, -10, N);
        // a long flooded reading hall with an air pocket at its centre
        k.room(m - 10, -11, m - 14 < 0 ? 0 : m - 14, m + 10, -2, b[2] - 1);
        k.air(b[0], -10, b[2] - 1, b[0] + 1, -8, b[2] - 1);
        for (int x = m - 9; x <= m + 9; x++) {
            for (int z = Math.max(1, m - 13); z <= b[2] - 2; z++) {
                for (int y = -10; y <= -5; y++) {
                    boolean pocket = Math.abs(x - m) <= 2 && Math.abs(z - m) <= 2 && y >= -6;
                    k.c.set(x, y, z, pocket ? Canvas.AIR : Blocks.WATER.getDefaultState());
                }
            }
        }
        for (int x = m - 8; x <= m + 8; x += 4) {
            for (int z = Math.max(2, m - 12); z <= b[2] - 3; z += 6) {
                for (int y = -10; y <= -8; y++) {
                    k.c.set(x, y, z, Blocks.BOOKSHELF);
                }
            }
        }
        k.c.set(m, -7, m, Blocks.SEA_LANTERN);
        // the key lies in the reading pocket; the lock seals the archive's heart
        k.keyLock(new int[]{m, -9, Math.max(1, m - 13)}, new int[]{m + 1, -6, m + 1}, k.sealDoor(m - 1, -10, Math.max(1, m - 13), true, 1), true);
        k.levers(new int[][]{{m - 9, -8, m}, {m - 9, -8, m + 2}, {m - 9, -8, m + 4}}, E, k.sealDoor(m + 10, -10, m, false, 2), false);
        k.room(m + 10, -11, m - 2, k.n - 1, -5, m + 4);
        k.chest(k.n - 3, -10, m + 1, W, "underdeep:exploration/library");
        k.room(m - 4, -11, 0, m + 4, -3, Math.max(1, m - 13));
        k.pedestal(m, -10, 2, "underdeep:drowned_crown");
        k.coffer(m - 2, -10, 1, S, "underdeep:exploration/coffer_large");
        k.coffer(m + 2, -10, 1, S, "underdeep:exploration/coffer_large");
        k.inscription(m - 4, -8, 3, E, Designs.carved(k, site));
        guard(k, "drowned", 3, m - 9, -10, Math.max(1, m - 13), m + 9, -6, b[2] - 2);
        heart(k, m - 3, -10, 1, m + 3, -6, Math.max(2, m - 14));
        k.weather(0.25, false);
    }

    static void deepFoundry(Kit k, Site site) {
        int m = k.mid;
        k.shell(m - 9, m + 6, m + 9, m + 16, 0, 8, true, 0.3);
        k.air(m - 1, 1, m + 16, m + 1, 4, m + 16);
        for (int x = m - 6; x <= m + 6; x += 6) {
            for (int y = 9; y <= 18; y++) {
                k.c.set(x, y, m + 13, Blocks.BRICKS);
            }
        }
        int[] b = descend(k, m, m + 10, -10, N);
        // the casting hall with lava channels (enclosed) and flame vents
        k.room(m - 12, -11, m - 6, m + 12, -2, b[2] - 1);
        k.air(b[0], -10, b[2] - 1, b[0] + 1, -8, b[2] - 1);
        for (int z = m - 5; z <= b[2] - 2; z++) {
            k.c.set(m - 6, -11, z, Blocks.LAVA);
            k.c.set(m + 6, -11, z, Blocks.LAVA);
            k.c.set(m - 6, -10, z, Blocks.IRON_BARS);
            k.c.set(m + 6, -10, z, Blocks.IRON_BARS);
        }
        for (int z = m - 4; z <= b[2] - 3; z += 3) {
            k.vent(ExploreBlocks.FLAME_VENT, m, -11, z);
        }
        k.c.trap("flames", List.of(), List.of(k.at(m, -11, m - 4)));
        k.braziers("brazier_all", new int[][]{{m - 10, -10, m - 4}, {m + 10, -10, m - 4}, {m - 10, -10, b[2] - 3}, {m + 10, -10, b[2] - 3}},
                k.sealDoor(m - 1, -10, m - 6, true, 2), true);
        // the lift: a counterweight raises the gate to the furnace stair
        k.room(m - 8, -11, m - 16 < 0 ? 0 : m - 16, m + 8, -3, m - 6);
        k.counterweight(new int[]{m - 6, -10, m - 10}, k.sealDoor(m + 8, -10, m - 12, false, 2), true);
        k.timedGate(new int[][]{{m - 6, -10, m - 8}, {m + 10, -10, m - 9}}, S, new int[][]{{m + 8, -10, m - 9}, {m + 8, -9, m - 9}});
        k.room(m + 8, -11, m - 14 < 0 ? 0 : m - 14, k.n - 1, -4, m - 6);
        k.coffer(m + 11, -10, m - 11, W, "underdeep:exploration/coffer_large");
        k.coffer(m + 11, -10, m - 9, W, "underdeep:exploration/coffer_large");
        k.chest(m - 7, -10, m - 14, E, "underdeep:exploration/ruin_forge");
        k.inscription(m - 8, -8, m - 12, E, Designs.carved(k, site));
        // the furnace heart: sealed, reserved, silent
        k.room(m - 4, -11, 0, m + 4, -3, m - 16 < 0 ? 1 : m - 16);
        k.sealDoor(m - 1, -10, Math.max(1, m - 16), true, 2);
        k.c.reserved = new int[]{m - 4, -11, 0, m + 4, -3, Math.max(1, m - 16)};
        heart(k, m + 9, -10, m - 13, k.n - 2, -6, m - 7);
        k.weather(0.3, false);
    }

    static void singingHall(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m + 10, 6, 0, Blocks.CALCITE, ModBlocks.LUMINITE_BLOCK);
        for (int i = 0; i < 5; i++) {
            int x = m - 6 + i * 3;
            for (int y = 1; y <= 4 + i % 3 * 2; y++) {
                k.c.set(x, y, m + 13, ModBlocks.LUMINITE_BLOCK);
            }
        }
        int[] b = descend(k, m, m + 10, -10, N);
        k.room(m - 12, -11, m - 14 < 0 ? 0 : m - 14, m + 12, -1, b[2] - 1);
        k.air(b[0], -10, b[2] - 1, b[0] + 1, -8, b[2] - 1);
        for (int i = 0; i < 10; i++) {
            int x = m - 10 + k.ri(21), z = Math.max(2, m - 12) + k.ri(Math.max(1, b[2] - m + 9));
            for (int y = -10; y <= -10 + 2 + k.ri(5); y++) {
                k.c.soft(x, y, z, ModBlocks.LUMINITE_BLOCK.getDefaultState());
            }
        }
        long[] inner = k.sealDoor(m - 1, -10, Math.max(1, m - 13), true, 2);
        k.chimes(new int[][]{{m - 6, -10, m}, {m, -10, m - 3}, {m + 6, -10, m}}, k.sealDoor(m + 12, -10, m, false, 2), true);
        k.room(m + 12, -11, m - 3, k.n - 1, -5, m + 4);
        k.memory(new int[]{k.n - 3, -10, m}, new int[][]{{k.n - 4, -9, m - 2}, {k.n - 4, -9, m + 2}, {k.n - 2, -9, m - 2}, {k.n - 2, -9, m + 2}},
                inner, true);
        k.beam(new int[][]{{m - 10, m - 6}, {m - 10, m - 10}, {m - 6, m - 10}}, -9, N, k.sealDoor(m - 12, -10, m - 8, false, 1), false);
        k.chest(m - 11, -10, m - 8, E, "underdeep:exploration/dungeon_large");
        k.room(m - 4, -11, 0, m + 4, -3, Math.max(1, m - 13));
        k.pedestal(m, -10, 2, "underdeep:resonant_core");
        k.coffer(m - 2, -10, 1, S, "underdeep:exploration/coffer_large");
        k.coffer(m + 2, -10, 1, S, "underdeep:exploration/coffer_large");
        k.inscription(m + 4, -8, 3, W, Designs.carved(k, site));
        guard(k, "silverfish", 3, m - 11, -10, m - 6, m + 11, -6, b[2] - 2);
        heart(k, m - 3, -10, 1, m + 3, -6, Math.max(2, m - 14));
        k.weather(0.15, false);
    }

    // ================================================================== exceptional

    static void heartwood(Kit k, Site site) {
        int m = k.mid;
        // the great root descends into the floor
        for (int y = 0; y <= 18; y++) {
            double r = 3 + Math.max(0, 6 - y) * 0.6;
            k.disc(m, m, r, y, ModWood.DUSKOAK.log, null);
        }
        k.air(m, 1, m + 3, m + 1, 4, m + 7);
        int[] b = descend(k, m, m + 6, -12, N);
        // root halls
        k.room(m - 14, -13, m - 6, m + 14, -2, b[2] - 1);
        k.air(b[0], -12, b[2] - 1, b[0] + 1, -10, b[2] - 1);
        for (int i = 0; i < 6; i++) {
            int x = m - 12 + i * 5;
            for (int y = -12; y <= -3; y++) {
                k.c.set(x, y, m - 1 + (i % 2) * 4, Style.axis(ModWood.DUSKOAK.log, Direction.Axis.Y));
            }
        }
        k.c.set(m, -3, m, Blocks.SHROOMLIGHT);
        k.braziers("brazier_all", new int[][]{{m - 12, -12, m + 3}, {m + 12, -12, m + 3}, {m - 12, -12, m - 5}, {m + 12, -12, m - 5}}, null, false);
        var bz = k.c.puzzles.get(k.c.puzzles.size() - 1);
        long[] gate1 = k.sealDoor(m - 1, -12, m - 6, true, 2);
        k.c.puzzles.set(k.c.puzzles.size() - 1, new Canvas.PuzzleSpec(bz.type(), bz.elements(), bz.solution(), Kit.seals(gate1), true));
        // the seed hall: offerings and the twin plates
        k.room(m - 12, -13, m - 14, m + 12, -2, m - 6);
        k.offerings(new int[][]{{m - 10, -12, m - 12}, {m + 10, -12, m - 12}, {m, -12, m - 10}}, null, false);
        var of = k.c.puzzles.get(k.c.puzzles.size() - 1);
        k.twinPlates(new int[]{m - 9, -12, m - 8}, new int[]{m + 9, -12, m - 8}, null, true);
        long[] heartDoor = k.sealDoor(m - 1, -12, m - 14, true, 2);
        // both puzzles open the heart door: each holds half of the seal
        long[] half1 = {heartDoor[0], heartDoor[1], heartDoor[2]}, half2 = {heartDoor[3], heartDoor[4], heartDoor[5]};
        k.c.puzzles.set(k.c.puzzles.size() - 2, new Canvas.PuzzleSpec(of.type(), of.elements(), of.solution(), Kit.seals(half1), true));
        var tw = k.c.puzzles.get(k.c.puzzles.size() - 1);
        k.c.puzzles.set(k.c.puzzles.size() - 1, new Canvas.PuzzleSpec(tw.type(), tw.elements(), tw.solution(), Kit.seals(half2), true));
        // the heart
        k.room(m - 5, -13, 0, m + 5, -3, m - 14);
        k.pedestal(m, -12, 2, "underdeep:heartwood_heart");
        k.coffer(m - 3, -12, 2, S, "underdeep:exploration/coffer_exceptional");
        k.coffer(m + 3, -12, 2, S, "underdeep:exploration/coffer_exceptional");
        k.coffer(m, -12, 4, S, "underdeep:exploration/seed_vault");
        k.inscription(m - 5, -10, 3, E, Designs.carved(k, site));
        k.inscription(m + 5, -10, 3, W, Designs.loose(k, site));
        guard(k, "spider", 3, m - 12, -12, m - 13, m + 12, -8, m - 7);
        heart(k, m - 4, -12, 1, m + 4, -8, 5);
        k.weather(0.3, true);
    }

    static void silentSanctum(Kit k, Site site) {
        int m = k.mid;
        k.floor(m - 8, m + 6, m + 8, m + 18, 0, k.s.floor, k.s.floorAlt);
        for (int x = m - 7; x <= m + 7; x += 7) {
            k.pillar(x, m + 15, 1, 12, false);
        }
        portal(k, m, m + 10, N);
        int[] b = descend(k, m, m + 10, -10, N);
        k.room(m - 12, -11, m - 4, m + 12, -2, b[2] - 1);
        k.air(b[0], -10, b[2] - 1, b[0] + 1, -8, b[2] - 1);
        // silence: put out every flame and the sealed glyphs show themselves
        k.braziers("extinguish", new int[][]{{m - 10, -10, m - 2}, {m + 10, -10, m - 2}, {m - 10, -10, b[2] - 3}, {m + 10, -10, b[2] - 3}},
                k.sealDoor(m - 1, -10, m - 4, true, 2), true);
        for (int x = m - 9; x <= m + 9; x += 3) {
            k.c.set(x, -11, m, Blocks.SCULK);
            k.c.set(x, -10, m + 1, Blocks.SCULK_SENSOR);
        }
        k.room(m - 10, -11, m - 14 < 0 ? 0 : m - 14, m + 10, -2, m - 4);
        k.plates(new int[][]{{m - 8, -10, m - 8}, {m - 3, -10, m - 11}, {m + 3, -10, m - 11}, {m + 8, -10, m - 8}}, k.sealDoor(m + 10, -10, m - 9, false, 2),
                true);
        k.room(m + 10, -11, m - 12, k.n - 1, -4, m - 5);
        k.coffer(m + 13, -10, m - 9, W, "underdeep:exploration/coffer_exceptional");
        k.coffer(m + 13, -10, m - 7, W, "underdeep:exploration/coffer_exceptional");
        k.chest(m + 13, -10, m - 11, W, "underdeep:exploration/silent");
        k.inscription(k.n - 2, -8, m - 9, W, Designs.carved(k, site));
        // the sanctum floor (reserved for a later age): sealed, empty, silent
        k.room(m - 6, -13, 0, m + 6, -2, Math.max(1, m - 14));
        k.sealDoor(m - 1, -10, Math.max(1, m - 14), true, 2);
        k.c.reserved = new int[]{m - 6, -13, 0, m + 6, -2, Math.max(1, m - 14)};
        guard(k, "restless", 4, m - 10, -10, m - 14 < 0 ? 0 : m - 13, m + 10, -6, m - 5);
        heart(k, m + 11, -10, m - 11, k.n - 2, -6, m - 6);
        k.weather(0.3, true);
    }
}
