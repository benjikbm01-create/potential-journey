package dev.underdeep.explore.design;

import dev.underdeep.explore.Canvas;
import dev.underdeep.explore.ExploreBlocks;
import dev.underdeep.explore.Site;
import dev.underdeep.explore.Style;
import dev.underdeep.registry.ModBlocks;
import dev.underdeep.registry.ModWood;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * Landmarks (Stage 9): large, recognisable places that make the underground feel vast. Most hold no loot; some hide a
 * secret room. Compositions are authored for a reveal from a distance (silhouette, light, scale).
 */
final class Landmarks {
    private Landmarks() {
    }

    static final Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;

    /** A seated colossus carved from the rock, hands on knees, with a chamber in its chest. */
    static void colossus(Kit k, Site site) {
        int m = k.mid;
        Block body = k.s.pillar, skin = k.s.block;
        // throne base
        k.floor(m - 9, m - 8, m + 9, m + 6, 0, k.s.floor, k.s.floorAlt);
        for (int x = m - 8; x <= m + 8; x++) {
            for (int z = m - 7; z <= m - 2; z++) {
                for (int y = 1; y <= 8; y++) {
                    k.c.set(x, y, z, (y == 8 ? k.s.trim : k.s.brick).getDefaultState());
                }
            }
        }
        // legs and knees
        for (int side : new int[]{-1, 1}) {
            int lx = m + side * 4;
            for (int x = lx - 2; x <= lx + 2; x++) {
                for (int z = m - 2; z <= m + 4; z++) {
                    for (int y = 1; y <= 7; y++) {
                        boolean shin = z >= m + 2 && y <= 7;
                        boolean thigh = y >= 6 && z <= m + 4;
                        if (shin || thigh) {
                            k.c.set(x, y, z, skin.getDefaultState());
                        }
                    }
                }
            }
            // hands on knees
            for (int x = lx - 2; x <= lx + 2; x++) {
                k.c.set(x, 8, m + 3, k.s.trim.getDefaultState());
                k.c.set(x, 8, m + 4, k.s.trim.getDefaultState());
            }
        }
        // torso
        for (int x = m - 6; x <= m + 6; x++) {
            for (int z = m - 6; z <= m - 1; z++) {
                for (int y = 9; y <= 22; y++) {
                    double wx = Math.abs(x - m) / (6.5 - (y > 19 ? (y - 19) * 1.2 : 0));
                    if (wx <= 1) {
                        k.c.set(x, y, z, body.getDefaultState());
                    }
                }
            }
        }
        // arms
        for (int side : new int[]{-1, 1}) {
            int ax = m + side * 7;
            for (int y = 9; y <= 20; y++) {
                k.c.set(ax, y, m - 3, skin.getDefaultState());
                k.c.set(ax + side, y, m - 3, skin.getDefaultState());
                k.c.set(ax, y, m - 2, skin.getDefaultState());
            }
            for (int z = m - 2; z <= m + 2; z++) {
                k.c.set(ax, 9, z, skin.getDefaultState());
            }
        }
        // head
        for (int x = m - 3; x <= m + 3; x++) {
            for (int z = m - 6; z <= m - 1; z++) {
                for (int y = 23; y <= 29; y++) {
                    double d = Math.pow((x - m) / 3.4, 2) + Math.pow((y - 26) / 3.6, 2) + Math.pow((z - m + 3.5) / 3.0, 2);
                    if (d <= 1) {
                        k.c.set(x, y, z, skin.getDefaultState());
                    }
                }
            }
        }
        k.c.set(m - 1, 26, m - 1, k.s.glow);
        k.c.set(m + 1, 26, m - 1, k.s.glow);
        k.c.set(m, 25, m, skin);
        for (int x = m - 3; x <= m + 3; x++) {
            k.c.set(x, 30, m - 4, Style.slab(k.s.trimSlab, false));
        }
        // the heart chamber: reached by a narrow stair cut into the throne's back (secret)
        k.room(m - 3, 11, m - 6, m + 3, 16, m - 2);
        k.c.set(m, 13, m - 2, k.s.glass);
        for (int y = 1; y <= 12; y++) {
            k.c.fixed(m, y, m - 8, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, N));
            k.c.force(m, y, m - 9, Canvas.AIR);
        }
        k.c.force(m, 11, m - 7, k.s.block.getDefaultState());
        k.c.force(m, 12, m - 7, Canvas.AIR);
        k.c.force(m, 13, m - 7, Canvas.AIR);
        k.c.lock(m, 13, m - 7);
        k.c.set(m, 12, m - 6, Canvas.AIR);
        k.c.set(m, 13, m - 6, Canvas.AIR);
        k.c.lock(m, 12, m - 6);
        k.c.lock(m, 13, m - 6);
        k.coffer(m, 12, m - 4, S, "underdeep:exploration/colossus");
        k.inscription(m + 2, 13, m - 5, W, Designs.carved(k, site));
        k.c.secret("colossus_heart", m - 2, 12, m - 5, m + 2, 15, m - 3);
        k.inscription(m, 1, m + 5, S, Designs.carved(k, site));
        k.weather(0.15, false);
    }

    /** A monumental staircase climbing to a high landing with a sealed door (secret landing). */
    static void greatStair(Kit k, Site site) {
        int m = k.mid;
        int steps = Math.min(k.n - 16, 22);
        int z0 = k.n - 3;
        for (int i = 0; i < steps; i++) {
            for (int x = m - 3; x <= m + 3; x++) {
                k.c.set(x, i + 1, z0 - i, Style.stairs(k.s.brickStairs, N, false));
                for (int y = 0; y <= i; y++) {
                    k.c.set(x, y, z0 - i, (x == m - 3 || x == m + 3 ? k.s.trim : k.s.brick).getDefaultState());
                }
                k.c.lock(x, i + 1, z0 - i);
            }
            if (i % 5 == 4) {
                k.standingLight(m - 4, i + 1, z0 - i);
                k.standingLight(m + 4, i + 1, z0 - i);
            }
        }
        int top = steps, lz = z0 - steps;
        k.floor(m - 5, lz - 5, m + 5, lz, top, k.s.floor, k.s.floorAlt);
        k.air(m - 5, top + 1, lz - 5, m + 5, top + 8, lz);
        for (int x = m - 5; x <= m + 5; x++) {
            for (int y = 0; y < top; y++) {
                for (int z = lz - 11; z <= lz; z++) {
                    k.c.soft(x, y, z, k.s.brick.getDefaultState());
                }
            }
        }
        k.pillar(m - 4, lz - 4, top + 1, top + 7, true);
        k.pillar(m + 4, lz - 4, top + 1, top + 7, true);
        for (int x = m - 4; x <= m + 4; x++) {
            k.c.set(x, top + 8, lz - 4, k.s.trim);
        }
        // the landing door: a dial seal with its clue mural carved on the landing floor's edge
        long[] seal = k.sealDoor(m - 1, top + 1, lz - 5, true, 3);
        k.room(m - 3, top, lz - 11, m + 3, top + 5, lz - 5);
        k.dials(new int[][]{{m - 3, top + 2, lz - 4}, {m + 3, top + 2, lz - 4}, {m, top + 3, lz - 3}}, S,
                new int[][]{{m - 2, top, lz}, {m, top, lz}, {m + 2, top, lz}}, N, seal, false);
        k.coffer(m, top + 1, lz - 9, S, "underdeep:exploration/stair");
        k.inscription(m + 2, top + 2, lz - 10, W, Designs.carved(k, site));
        k.c.secret("stair_landing", m - 2, top + 1, lz - 10, m + 2, top + 4, lz - 6);
        k.weather(0.12, false);
    }

    /** A slender arched bridge leaping between two pillars high above the floor. */
    static void skyBridge(Kit k, Site site) {
        int m = k.mid;
        int h = 16;
        int x1 = 3, x2 = k.n - 4;
        for (int x : new int[]{x1, x2}) {
            for (int y = 0; y <= h; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        k.c.set(x + dx, y, m + dz, (dx == 0 && dz == 0 ? k.s.pillar : k.s.brick).getDefaultState());
                    }
                }
            }
            k.c.set(x, h + 1, m, k.s.light);
        }
        double span = x2 - x1;
        for (int x = x1; x <= x2; x++) {
            double t = (x - x1) / span;
            int y = h - 1 + (int) Math.round(Math.sin(t * Math.PI) * 4);
            for (int z = m - 1; z <= m + 1; z++) {
                k.c.set(x, y, z, (z == m ? k.s.floor : k.s.brick).getDefaultState());
            }
            k.c.set(x, y + 1, m - 1, k.s.brickWall);
            k.c.set(x, y + 1, m + 1, k.s.brickWall);
            k.air(x, y + 1, m, x, y + 3, m);
            k.c.lock(x, y, m);
            if (x % 6 == 0) {
                k.c.set(x, y - 1, m, Style.hanging(k.s.hangingLight));
            }
        }
        // spiral stairs inside one pillar
        for (int y = 1; y < h; y++) {
            k.c.set(x1, y, m, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, E));
            k.c.set(x1 + 1, y, m, k.s.brick);
            k.c.lock(x1, y, m);
        }
        k.c.set(x1 - 1, 1, m, Canvas.AIR);
        k.c.set(x1 - 1, 2, m, Canvas.AIR);
        k.c.lock(x1 - 1, 1, m);
        k.c.lock(x1 - 1, 2, m);
        k.inscription(x2, 1, m + 2, S, Designs.carved(k, site));
        k.weather(0.1, false);
    }

    /** A tall copper lantern tower of the League, still glowing: a navigation beacon. */
    static void lanternSpire(Kit k, Site site) {
        int m = k.mid;
        int h = 24;
        k.disc(m, m, 5, 0, k.s.floor, k.s.trim);
        for (int y = 1; y <= h; y++) {
            double r = y < 4 ? 3.5 : y < h - 4 ? 2.2 : 3;
            k.ring(m, m, r, y, y, y % 6 == 0 ? Blocks.OXIDIZED_CUT_COPPER : k.s.brick);
        }
        k.air(m - 1, 1, m - 1, m + 1, h, m + 1);
        k.air(m, 1, m + 2, m, 2, m + 4);
        for (int y = 1; y < h; y++) {
            k.c.fixed(m, y, m - 1, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, S));
        }
        // lantern room
        for (int y = h + 1; y <= h + 5; y++) {
            for (int[] p : new int[][]{{m - 3, m - 3}, {m + 3, m - 3}, {m - 3, m + 3}, {m + 3, m + 3}}) {
                k.c.set(p[0], y, p[1], Blocks.OXIDIZED_COPPER);
            }
        }
        k.disc(m, m, 3.6, h, Blocks.OXIDIZED_CUT_COPPER, null);
        k.c.set(m, h, m, Blocks.AIR.getDefaultState());
        k.c.set(m, h, m - 1, Blocks.AIR.getDefaultState());
        k.c.set(m, h + 1, m, Blocks.OXIDIZED_COPPER_BULB.getDefaultState().with(Properties.LIT, true));
        k.c.set(m, h + 2, m, Blocks.SEA_LANTERN);
        k.c.set(m, h + 3, m, Blocks.OXIDIZED_COPPER_BULB.getDefaultState().with(Properties.LIT, true));
        k.disc(m, m, 3.6, h + 6, Blocks.OXIDIZED_CUT_COPPER_SLAB, null);
        for (int y = h + 7; y <= h + 9; y++) {
            k.c.set(m, y, m, Blocks.LIGHTNING_ROD);
        }
        k.inscription(m + 2, 1, m - 2, W, Designs.waystoneRecord(k, site));
        k.chest(m - 2, h + 1, m + 2, E, "underdeep:exploration/league_cache");
        k.weather(0.1, false);
    }

    /** A colossal living root that arches out of the floor and back into it. */
    static void rootArch(Kit k, Site site) {
        int m = k.mid;
        double r = k.mid - 2.5;
        Block log = ModWood.DUSKOAK.log;
        for (double a = 0; a <= Math.PI; a += 0.02) {
            double x = m + Math.cos(a) * r, y = Math.sin(a) * (r * 0.9);
            double th = 2.4 - Math.sin(a) * 0.7;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= th * th) {
                            k.c.set((int) Math.round(x) + dx, (int) Math.round(y) + dy, m + dz, Style.axis(log, Direction.Axis.Y));
                        }
                    }
                }
            }
        }
        // hanging glow and moss beneath
        for (int x = m - 6; x <= m + 6; x += 3) {
            int top = (int) (Math.sqrt(Math.max(0, r * r - (x - m) * (x - m))) * 0.9) - 3;
            for (int y = top; y > top - 3 - k.ri(3) && y > 1; y--) {
                k.c.soft(x, y, m, dev.underdeep.registry.ModPlants.GLOWIVY.getDefaultState());
            }
        }
        k.disc(m, m, 4, 0, Blocks.MOSS_BLOCK, null);
        k.c.set(m, 1, m, k.s.chiseled);
        k.inscription(m, 2, m, S, Designs.carved(k, site));
        k.weather(0.1, false);
    }

    /** A cathedral of crystal: luminite spires around a nave of calcite, singing faintly. */
    static void crystalCathedral(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m, k.mid - 1, 0, Blocks.CALCITE, Blocks.SMOOTH_BASALT);
        for (int i = 0; i < 9; i++) {
            double a = Math.PI * 2 * i / 9;
            int x = (int) Math.round(m + Math.cos(a) * (k.mid - 4)), z = (int) Math.round(m + Math.sin(a) * (k.mid - 4));
            int h = 10 + k.ri(10);
            for (int y = 1; y <= h; y++) {
                double rr = 1.6 * (1 - (double) y / (h + 2));
                k.disc(x, z, Math.max(0.4, rr), y, y > h - 3 ? ModBlocks.LUMINITE_CLUSTER : ModBlocks.LUMINITE_BLOCK, null);
            }
            k.c.set(x, h + 1, z, Blocks.AMETHYST_CLUSTER);
        }
        for (int y = 1; y <= 14; y++) {
            k.disc(m, m, 1.2, y, y % 4 == 0 ? Blocks.AMETHYST_BLOCK : ModBlocks.LUMINITE_BLOCK, null);
        }
        k.c.set(m, 15, m, ModBlocks.LUMINITE_CLUSTER);
        k.c.set(m + 3, 1, m, Blocks.AMETHYST_CLUSTER);
        k.inscription(m, 1, m + 3, S, Designs.carved(k, site));
        k.weather(0.05, false);
    }

    /** A frozen waterfall falling from a ledge into a shrine pool. */
    static void frozenFalls(Kit k, Site site) {
        int m = k.mid;
        int h = 18;
        for (int x = m - 6; x <= m + 6; x++) {
            for (int y = 0; y <= h; y++) {
                for (int z = 1; z <= 3; z++) {
                    k.c.set(x, y, z, Blocks.PACKED_ICE.getDefaultState());
                }
            }
        }
        for (int x = m - 3; x <= m + 3; x++) {
            for (int y = 1; y <= h; y++) {
                int bulge = (int) (Math.sin(y * 0.7 + x) * 0.8 + 1);
                for (int z = 4; z <= 4 + bulge; z++) {
                    k.c.set(x, y, z, (y % 3 == 0 ? Blocks.BLUE_ICE : Blocks.ICE).getDefaultState());
                }
            }
        }
        k.disc(m, m + 4, 5, 0, Blocks.BLUE_ICE, Blocks.SNOW_BLOCK);
        k.disc(m, m + 4, 3.5, 0, Blocks.ICE, null);
        for (int[] p : new int[][]{{m - 5, m + 8}, {m + 5, m + 8}}) {
            k.pillar(p[0], p[1], 1, 4, true);
            k.c.set(p[0], 6, p[1], Blocks.SOUL_LANTERN);
        }
        k.inscription(m, 1, m + 9, N, Designs.carved(k, site));
        // a tomb inside the ice behind the fall (secret)
        k.room(m - 2, 0, 0, m + 2, 4, 3);
        k.c.set(m, 1, 3, Canvas.AIR);
        k.c.set(m, 2, 3, Canvas.AIR);
        k.c.set(m, 1, 4, Canvas.AIR);
        k.c.set(m, 2, 4, Canvas.AIR);
        k.c.lockBox(m, 1, 3, m, 2, 4);
        k.coffer(m, 1, 1, S, "underdeep:exploration/ice");
        k.c.secret("ice_tomb", m - 1, 1, 1, m + 1, 3, 2);
        k.weather(0.05, false);
    }

    /** A ring of black monoliths around a glowing magma pool. */
    static void emberCaldera(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m, k.mid - 1, 0, Blocks.BASALT, Blocks.BLACKSTONE);
        k.disc(m, m, 4, 0, Blocks.MAGMA_BLOCK, Blocks.OBSIDIAN);
        k.disc(m, m, 2.5, 0, Blocks.LAVA, null);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            int x = (int) Math.round(m + Math.cos(a) * (k.mid - 4)), z = (int) Math.round(m + Math.sin(a) * (k.mid - 4));
            int h = 8 + k.ri(8);
            for (int y = 1; y <= h; y++) {
                k.c.set(x, y, z, (y % 5 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).getDefaultState());
                if (y < h - 2) {
                    k.c.set(x + 1, y, z, Blocks.OBSIDIAN);
                }
            }
            k.c.set(x, h + 1, z, Blocks.FIRE.getDefaultState().getBlock() == null ? Blocks.MAGMA_BLOCK : Blocks.MAGMA_BLOCK);
        }
        k.inscription(m + 6, 1, m, W, Designs.carved(k, site));
        k.weather(0.05, false);
    }

    /** A half-sunken glass dome on a drowned plinth. */
    static void sunkenDome(Kit k, Site site) {
        int m = k.mid;
        double r = k.mid - 2;
        for (int x = 1; x < k.n - 1; x++) {
            for (int z = 1; z < k.n - 1; z++) {
                for (int y = -3; y <= r; y++) {
                    double d = Math.sqrt((x - m) * (x - m) + (y + 3) * (y + 3) * 1.1 + (z - m) * (z - m));
                    if (d <= r && d > r - 1) {
                        k.c.set(x, y, z, ((x + z + y) % 4 == 0 ? Blocks.PRISMARINE_BRICKS : Blocks.LIGHT_BLUE_STAINED_GLASS).getDefaultState());
                    } else if (d <= r - 1) {
                        k.c.set(x, y, z, Canvas.AIR);
                    }
                }
            }
        }
        k.disc(m, m, r - 1, -3, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS);
        for (int y = -2; y <= 3; y++) {
            k.c.set(m, y, m, Blocks.QUARTZ_PILLAR);
        }
        k.c.set(m, 4, m, Blocks.SEA_LANTERN);
        k.air(m, -2, m + (int) r - 1, m, 0, m + (int) r);
        k.inscription(m + 2, -2, m, W, Designs.carved(k, site));
        k.chest(m - 2, -2, m, E, "underdeep:exploration/tide");
        k.weather(0.15, false);
    }

    /** A fairy ring of giant mushrooms around a mossy lawn. */
    static void mushroomRing(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m, k.mid - 1, 0, Blocks.MOSS_BLOCK, ModBlocks.SPOREGLOW_SOIL);
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10;
            int x = (int) Math.round(m + Math.cos(a) * (k.mid - 4)), z = (int) Math.round(m + Math.sin(a) * (k.mid - 4));
            int h = 6 + k.ri(7);
            for (int y = 1; y <= h; y++) {
                k.c.set(x, y, z, Blocks.MUSHROOM_STEM);
            }
            Block cap = i % 2 == 0 ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK;
            k.disc(x, z, 2.6, h + 1, cap, null);
            k.disc(x, z, 1.5, h + 2, cap, null);
            k.c.set(x, h, z + 1, Blocks.SHROOMLIGHT);
        }
        for (int i = 0; i < 14; i++) {
            k.c.set(m - 4 + k.ri(9), 1, m - 4 + k.ri(9), dev.underdeep.registry.ModPlants.LANTERNCAP);
        }
        k.inscription(m, 1, m, S, Designs.carved(k, site));
        k.weather(0.05, false);
    }

    /** An enormous petrified tree with a hollow trunk (secret hollow at the top). */
    static void petrifiedTitan(Kit k, Site site) {
        int m = k.mid;
        int h = 26;
        Block wood = ModBlocks.PETRIFIED_WOOD;
        for (int y = 0; y <= h; y++) {
            double r = 4.2 - y * 0.06;
            k.ring(m, m, r, y, y, wood);
            if (y < h - 3) {
                k.air(m - 2, y + 1, m - 2, m + 2, y + 1, m + 2);
            }
        }
        for (Direction d : Direction.Type.HORIZONTAL) {
            for (int i = 4; i <= 9; i++) {
                int x = m + d.getOffsetX() * i, z = m + d.getOffsetZ() * i;
                for (int y = 0; y <= Math.max(0, 4 - (i - 4)); y++) {
                    k.c.set(x, y, z, wood);
                }
            }
        }
        for (int b = 0; b < 5; b++) {
            double a = Math.PI * 2 * b / 5 + 0.3;
            for (int i = 0; i < 9; i++) {
                int x = (int) Math.round(m + Math.cos(a) * i), z = (int) Math.round(m + Math.sin(a) * i);
                k.c.set(x, h - 4 + i / 2, z, wood);
            }
        }
        // the way in is a split between two roots
        k.air(m + 3, 1, m + 3, m + 3, 3, m + 3);
        k.air(m + 2, 1, m + 3, m + 2, 3, m + 3);
        k.c.lockBox(m + 2, 1, m + 3, m + 3, 3, m + 3);
        for (int y = 1; y < h - 4; y++) {
            k.c.set(m, y, m - 2, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, S));
            k.c.lock(m, y, m - 2);
        }
        k.air(m - 2, h - 3, m - 2, m + 2, h - 1, m + 2);
        k.floor(m - 2, m - 2, m + 2, m + 2, h - 4, Blocks.SPRUCE_PLANKS, null);
        k.c.fixed(m, h - 4, m - 2, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, S));
        k.coffer(m + 1, h - 3, m + 1, W, "underdeep:exploration/secret_stash");
        k.inscription(m - 2, h - 2, m, E, Designs.loose(k, site));
        k.c.secret("titan_hollow", m - 2, h - 3, m - 2, m + 2, h - 1, m + 2);
        k.weather(0.1, false);
    }

    /** A great armillary: nested rings of quartz and gold around a starstone heart (secret core). */
    static void starOrrery(Kit k, Site site) {
        int m = k.mid;
        int cy = 12;
        k.disc(m, m, 6, 0, k.s.floor, k.s.trim);
        for (int y = 1; y < cy - 1; y++) {
            k.c.set(m, y, m, Blocks.QUARTZ_PILLAR);
            k.c.fixed(m, y, m + 1, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, S));
        }
        for (int ring = 0; ring < 3; ring++) {
            double r = 6 + ring * 3, tilt = 0.4 + ring * 0.5;
            for (double a = 0; a < Math.PI * 2; a += 0.03) {
                double x = Math.cos(a) * r, y = Math.sin(a) * r * Math.cos(tilt), z = Math.sin(a) * r * Math.sin(tilt);
                if (ring == 1) {
                    double t = x;
                    x = z;
                    z = t;
                }
                k.c.set(m + (int) Math.round(x), cy + (int) Math.round(y), m + (int) Math.round(z), (ring == 1 ? Blocks.GOLD_BLOCK : Blocks.QUARTZ_BLOCK)
                        .getDefaultState());
            }
        }
        k.disc(m, m, 2.2, cy - 1, ModBlocks.STARSTONE, null);
        k.disc(m, m, 2.2, cy + 2, ModBlocks.STARSTONE, null);
        k.ring(m, m, 2.2, cy, cy + 1, ModBlocks.STARSTONE);
        k.c.fixed(m, cy - 1, m + 1, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, S));
        for (int x = m - 1; x <= m + 1; x++) {
            for (int z = m - 1; z <= m + 1; z++) {
                k.c.force(x, cy, z, Canvas.AIR);
                k.c.force(x, cy + 1, z, Canvas.AIR);
            }
        }
        k.c.lockBox(m - 1, cy, m - 1, m + 1, cy + 1, m + 1);
        k.coffer(m, cy, m - 1, S, "underdeep:exploration/star");
        k.c.secret("orrery_core", m - 1, cy, m - 1, m + 1, cy + 1, m + 1);
        k.inscription(m + 3, 1, m, W, Designs.carved(k, site));
        k.weather(0.05, false);
    }

    /** Tall stormstone pylons joined by copper cables, crackling. */
    static void stormPylons(Kit k, Site site) {
        int m = k.mid;
        int[][] pos = {{m - 8, m - 6}, {m + 8, m - 6}, {m, m + 8}};
        for (int[] p : pos) {
            for (int y = 0; y <= 20; y++) {
                k.disc(p[0], p[1], y < 3 ? 2 : 1.2, y, y % 4 == 0 ? ModBlocks.CHARGED_STORMSTONE : ModBlocks.STORMSTONE, null);
            }
            for (int y = 21; y <= 24; y++) {
                k.c.set(p[0], y, p[1], Blocks.LIGHTNING_ROD);
            }
            k.c.set(p[0], 20, p[1], ModBlocks.STORM_CRYSTAL);
        }
        for (int i = 0; i < 3; i++) {
            int[] a = pos[i], b = pos[(i + 1) % 3];
            for (int t = 0; t <= 40; t++) {
                double u = t / 40.0;
                int x = (int) Math.round(a[0] + (b[0] - a[0]) * u), z = (int) Math.round(a[1] + (b[1] - a[1]) * u);
                int y = 18 - (int) Math.round(Math.sin(u * Math.PI) * 3);
                k.c.set(x, y, z, Blocks.CHAIN.getDefaultState().with(Properties.AXIS, Math.abs(b[0] - a[0]) > Math.abs(b[1] - a[1]) ? Direction.Axis.X
                        : Direction.Axis.Z));
            }
        }
        k.inscription(m, 1, m, S, Designs.carved(k, site));
        k.weather(0.05, false);
    }

    /** Tiered gardens with falling water. */
    static void hangingGardens(Kit k, Site site) {
        int m = k.mid;
        for (int t = 0; t < 4; t++) {
            int r = k.mid - 2 - t * 3;
            int y = t * 4;
            for (int x = m - r; x <= m + r; x++) {
                for (int z = m - r; z <= m + r; z++) {
                    for (int yy = Math.max(0, y - 3); yy <= y; yy++) {
                        k.c.set(x, yy, z, k.s.brick.getDefaultState());
                    }
                    boolean edge = Math.abs(x - m) == r || Math.abs(z - m) == r;
                    k.c.set(x, y + 1, z, edge ? Blocks.MOSS_CARPET.getDefaultState() : (k.chance(0.5) && !k.s.plants.isEmpty() ? k.pick(k.s.plants)
                            .getDefaultState() : Canvas.AIR));
                    if (!edge) {
                        k.c.set(x, y, z, Blocks.MOSS_BLOCK);
                    }
                }
            }
            k.c.set(m + r, y + 1, m, Blocks.WATER);
            k.c.set(m - r, y + 1, m, Blocks.WATER);
            for (int x = m - r; x <= m + r; x += 3) {
                k.c.set(x, y + 1, m - r, Blocks.FLOWERING_AZALEA);
            }
        }
        for (int t = 0; t < 3; t++) {
            int r = k.mid - 2 - t * 3;
            k.stairs(m, t * 4 + 1, m + r - 1, N, 4, 1, k.s.brickStairs, k.s.brick);
        }
        k.c.set(m, 13, m, Blocks.LANTERN);
        k.inscription(m, 1, m + k.mid - 2, S, Designs.carved(k, site));
        k.pot(m + 1, 13, m, "underdeep:exploration/gardener");
        k.weather(0.05, false);
    }

    /** A field of leaning obelisks carved with the six signs. */
    static void obeliskField(Kit k, Site site) {
        int m = k.mid;
        for (int i = 0; i < 9; i++) {
            int x = 3 + k.ri(k.n - 6), z = 3 + k.ri(k.n - 6);
            int h = 6 + k.ri(9);
            int lean = k.ri(3) - 1;
            for (int y = 0; y <= h; y++) {
                int ox = lean * y / 6;
                k.c.set(x + ox, y, z, (y == h ? k.s.chiseled : k.s.block).getDefaultState());
                if (y < h / 2) {
                    k.c.set(x + ox + 1, y, z, k.s.block.getDefaultState());
                }
                if (y == 3) {
                    k.c.set(x + ox, y, z + 1, Style.facing(ExploreBlocks.GLYPH_TILE, S).with(ExploreBlocks.SYMBOL, (i + y) % 6));
                }
            }
        }
        k.inscription(m, 1, m, S, Designs.carved(k, site));
        k.weather(0.1, false);
    }

    /** A colossal bronze bell hanging in the dark above an altar (secret bell chamber beneath). */
    static void silentBell(Kit k, Site site) {
        int m = k.mid;
        int top = 26;
        for (int y = 1; y <= top; y++) {
            k.c.set(m, y, m, y > 18 ? Blocks.CHAIN.getDefaultState() : Canvas.AIR);
        }
        for (int y = 10; y <= 18; y++) {
            double r = 1.5 + (18 - y) * 0.45;
            k.ring(m, m, r, y, y, y % 3 == 0 ? Blocks.WAXED_EXPOSED_COPPER : Blocks.WAXED_WEATHERED_COPPER);
        }
        k.c.set(m, 9, m, Blocks.WAXED_OXIDIZED_COPPER);
        for (int y = 1; y <= top; y++) {
            for (int[] p : new int[][]{{m - 8, m}, {m + 8, m}}) {
                k.c.set(p[0], y, p[1], Style.axis(k.s.pillar, Direction.Axis.Y));
            }
        }
        for (int x = m - 8; x <= m + 8; x++) {
            k.c.set(x, top, m, k.s.trim);
        }
        k.disc(m, m, 4, 0, k.s.floor, k.s.floorAlt);
        k.c.set(m, 1, m + 3, k.s.chiseled);
        k.inscription(m, 2, m + 3, S, Designs.carved(k, site));
        // a chamber beneath the altar (secret)
        k.room(m - 3, -6, m - 3, m + 3, -1, m + 3);
        // the way down: a narrow stair rising south from the chamber to a gap in the floor beyond the altar
        k.air(m, -5, m + 3, m, -4, m + 3);
        k.c.lockBox(m, -5, m + 3, m, -4, m + 3);
        k.stairs(m, -5, m + 4, S, 6, 1, k.s.brickStairs, k.s.brick);
        k.coffer(m, -5, m - 2, S, "underdeep:exploration/silent");
        k.c.set(m - 2, -5, m - 2, Blocks.SCULK_SHRIEKER.getDefaultState().getBlock() == null ? Blocks.SCULK : Blocks.SCULK_SENSOR);
        k.c.secret("bell_chamber", m - 2, -5, m - 2, m + 2, -3, m + 2);
        k.weather(0.1, false);
    }
}
