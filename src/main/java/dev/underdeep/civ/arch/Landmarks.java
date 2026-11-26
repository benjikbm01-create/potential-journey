package dev.underdeep.civ.arch;

import dev.underdeep.civ.Civilization;
import dev.underdeep.registry.ModPlants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * One culturally significant landmark per civilization (towns and cities). Public description only:
 * the meaning of each landmark is private design material.
 */
final class Landmarks {
    private Landmarks() {
    }

    static Blueprint design(Civilization civ, Shells s, Palette p, int v, long seed) {
        return switch (civ) {
            case ROOTFOLK -> elderTree(s, p, v, seed);
            case FORGEKIN -> greatFurnace(s, p, v, seed);
            case TIDE_DWELLERS -> lighthouse(s, p, v, seed);
            case SPOREFOLK -> greatCap(s, p, v, seed);
            case GLIMMERKIN -> crystalSpire(s, p, v, seed);
            case STONEBORN -> stepHall(s, p, v, seed);
            case AMBERFOLK -> fountainCourt(s, p, v, seed);
            case ASTRALS -> observatory(s, p, v, seed);
        };
    }

    private static BlockState leaves(Palette p) {
        BlockState l = (p.leaves != null ? p.leaves : Blocks.OAK_LEAVES).getDefaultState();
        return l.contains(Properties.PERSISTENT) ? l.with(Properties.PERSISTENT, true) : l;
    }

    /** A giant living tree with a spiral stair, a ring platform with lanterns and a meeting floor at its roots. */
    private static Blueprint elderTree(Shells s, Palette p, int v, long seed) {
        int w = 21, d = 21;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        double cx = 10, cz = 10;
        b.disc(cx, cz, 10, 0, p.plaza.getDefaultState());
        b.disc(cx, cz, 9, 0, p.plazaAccent.getDefaultState());
        int trunkTop = 30;
        for (int y = -1; y <= trunkTop; y++) {
            double r = y < 3 ? 3.2 - y * 0.25 : 2.4;
            b.disc(cx, cz, r, y, b.axis(p.log, Direction.Axis.Y));
        }
        // roots flaring out
        for (Direction dir : Direction.Type.HORIZONTAL) {
            for (int i = 3; i <= 6; i++) {
                int x = (int) cx + dir.getOffsetX() * i, z = (int) cz + dir.getOffsetZ() * i;
                b.set(x, 1, z, b.axis(p.log, dir.getAxis()));
                if (i < 5) {
                    b.set(x, 2, z, b.axis(p.log, dir.getAxis()));
                }
            }
        }
        // spiral stair around the trunk
        for (int i = 0; i < 64; i++) {
            double a = i * 0.42;
            int y = 1 + i / 3;
            if (y > 19) {
                break;
            }
            int x = (int) Math.round(cx + Math.cos(a) * 4), z = (int) Math.round(cz + Math.sin(a) * 4);
            b.set(x, y - 1, z, p.planks);
            b.soft(x, y, z, Builder.AIR);
            b.soft(x, y + 1, z, Builder.AIR);
            if (i % 6 == 0) {
                b.set((int) Math.round(cx + Math.cos(a) * 5), y, (int) Math.round(cz + Math.sin(a) * 5), p.fence);
            }
        }
        // ring platform with railing at y = 20
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                double dd = Math.hypot(x - cx, z - cz);
                if (dd <= 7.5 && dd > 2.4) {
                    b.set(x, 19, z, p.planks);
                }
                if (dd <= 7.5 && dd > 6.5) {
                    b.set(x, 20, z, p.fence);
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int x = (int) Math.round(cx + Math.cos(a) * 7), z = (int) Math.round(cz + Math.sin(a) * 7);
            b.hangingLantern(x, 18, z, p.hangingLight);
            b.set(x, 21, z, p.light);
        }
        // crown
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int dy = 22; dy <= 34; dy++) {
                    double e = (dx * dx + dz * dz) / 90.0 + (dy - 28) * (dy - 28) / 30.0;
                    if (e <= 1 && b.rnd.nextInt(9) != 0) {
                        b.soft((int) cx + dx, dy, (int) cz + dz, leaves(p));
                    }
                }
            }
        }
        // meeting floor: benches around the roots
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            int x = (int) Math.round(cx + Math.cos(a) * 8), z = (int) Math.round(cz + Math.sin(a) * 8);
            b.set(x, 1, z, b.slab(p.planksSlab, false));
            b.marker(Blueprint.MarkerType.SEAT, x, 1, z, "");
        }
        b.marker(Blueprint.MarkerType.GATHER, 10, 1, 4, "landmark");
        b.marker(Blueprint.MarkerType.WORKSTATION, 10, 1, 6, "elder");
        b.marker(Blueprint.MarkerType.ENTRANCE, 10, 1, -1, "");
        return bp;
    }

    /** Ceremonial furnace hall: heavy buttressed hall around a sealed molten core behind glass. */
    private static Blueprint greatFurnace(Shells s, Palette p, int v, long seed) {
        int w = 19, d = 19;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        s.body(b, 0, 0, w - 1, d - 1, 1, v, 9);
        s.entrance(b, w / 2, 0, Direction.NORTH);
        // molten core sealed by glass (safe), with anvils and smithing stations around
        int c = w / 2;
        b.fill(c - 2, 0, c - 1, c + 2, 0, c + 3, p.foundation);
        b.fill(c - 1, 1, c, c + 1, 6, c + 2, Blocks.TINTED_GLASS);
        b.fill(c, 1, c + 1, c, 6, c + 1, Blocks.MAGMA_BLOCK);
        b.set(c, 1, c + 1, Blocks.LAVA);
        b.fill(c - 1, 7, c, c + 1, 9, c + 2, Blocks.BRICKS);
        b.fill(c, 7, c + 1, c, 16, c + 1, Blocks.BRICKS);
        b.set(c, 17, c + 1, Blocks.CAMPFIRE);
        b.set(c, 10, c + 1, Blocks.BRICKS);
        for (int i = -1; i <= 1; i += 2) {
            b.workstation(c + i * 4, 1, c + 1, Blocks.ANVIL, Direction.NORTH, "blacksmith");
            b.workstation(c + i * 4, 1, c + 4, Blocks.BLAST_FURNACE, Direction.NORTH, "smelter");
            b.set(c + i * 6, 1, 3, Blocks.RAW_IRON_BLOCK);
            b.set(c + i * 6, 1, 4, Blocks.RAW_COPPER_BLOCK);
            b.set(c + i * 6, 2, 3, Blocks.RAW_GOLD_BLOCK);
        }
        for (int x = 2; x < w - 2; x += 4) {
            b.hangingLantern(x, 8, 4, p.hangingLight);
            b.hangingLantern(x, 8, d - 5, p.hangingLight);
        }
        b.marker(Blueprint.MarkerType.GATHER, c, 1, 4, "landmark");
        b.marker(Blueprint.MarkerType.WORKSTATION, c, 1, 5, "elder");
        s.finish(b, 0, 0, w - 1, d - 1, 1, v);
        return bp;
    }

    /** Navigation lighthouse on a stone base with a keeper's room. */
    private static Blueprint lighthouse(Shells s, Palette p, int v, long seed) {
        int w = 11, d = 11;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        bp.stilts = true;
        double cx = 5, cz = 5;
        b.disc(cx, cz, 5, 0, p.foundation.getDefaultState());
        b.disc(cx, cz, 5, -1, p.foundation.getDefaultState());
        for (int y = 1; y <= 22; y++) {
            double r = 3.6 - y * 0.05;
            b.ring(cx, cz, r, y, y, y % 6 < 3 ? p.wall : p.foundation, false, null);
            b.disc(cx, cz, r - 1, y, Builder.AIR);
            if (y % 5 == 2 && y > 4) {
                b.set((int) cx, y, (int) Math.round(cz - r), p.window);
            }
        }
        for (int y = 1; y <= 22; y++) {
            b.set((int) cx, y, (int) cz + 2, Architect.ladder(Direction.NORTH));
        }
        b.clear((int) cx, 1, 1, (int) cx, 2, 1);
        b.door((int) cx, 1, 1, Direction.NORTH);
        b.disc(cx, cz, 4.5, 23, p.planks.getDefaultState());
        b.set((int) cx, 23, (int) cz + 2, Architect.ladder(Direction.NORTH));
        b.ring(cx, cz, 4.5, 24, 24, p.fence, false, null);
        b.fill((int) cx - 1, 24, (int) cz - 1, (int) cx + 1, 26, (int) cz + 1, Blocks.GLASS);
        b.set((int) cx, 25, (int) cz, Blocks.SEA_LANTERN);
        b.set((int) cx, 24, (int) cz, Blocks.SEA_LANTERN);
        b.hipRoof((int) cx - 2, (int) cz - 2, (int) cx + 2, (int) cz + 2, 27, p.roofStairs, 0);
        b.bed((int) cx - 2, 1, (int) cz - 1, Direction.SOUTH, p.cloth);
        b.marker(Blueprint.MarkerType.WORKSTATION, (int) cx, 24, (int) cz - 2, "navigator");
        b.marker(Blueprint.MarkerType.GATHER, (int) cx, 1, -1, "landmark");
        b.marker(Blueprint.MarkerType.ENTRANCE, (int) cx, 1, -1, "");
        return bp;
    }

    /** A grown giant mushroom whose cap shelters a round gathering hall. */
    private static Blueprint greatCap(Shells s, Palette p, int v, long seed) {
        int w = 21, d = 21;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        double cx = 10, cz = 10;
        b.disc(cx, cz, 9, 0, p.floor.getDefaultState());
        b.disc(cx, cz, 4, 0, p.floorAccent.getDefaultState());
        b.ring(cx, cz, 8.5, 1, 6, p.wall, false, null);
        for (int y = 1; y <= 6; y++) {
            b.disc(cx, cz, 7.6, y, Builder.AIR);
        }
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            int x = (int) Math.round(cx + Math.cos(a) * 8.5), z = (int) Math.round(cz + Math.sin(a) * 8.5);
            b.set(x, 3, z, p.window);
        }
        // central stem rising to a broad cap
        for (int y = 1; y <= 18; y++) {
            b.disc(cx, cz, y < 8 ? 1.6 : 2.2, y, p.frame.getDefaultState());
        }
        b.disc(cx, cz, 8.5, 7, p.wallAlt.getDefaultState());
        for (int dy = 0; dy <= 7; dy++) {
            double rr = Math.sqrt(Math.max(0, 110 - dy * dy * 2.6));
            for (int x = -12; x <= 32; x++) {
                for (int z = -12; z <= 32; z++) {
                    double dd = Math.hypot(x - cx, z - cz);
                    if (dd <= rr + 0.5 && dd > rr - 1.4) {
                        b.set(x, 16 + dy, z, (x * 5 + z * 3 + dy) % 9 == 0 ? p.light : p.roof);
                    }
                }
            }
        }
        for (int x = -12; x <= 32; x++) {
            for (int z = -12; z <= 32; z++) {
                double dd = Math.hypot(x - cx, z - cz);
                if (dd <= 10 && dd > 2.4 && (x + z) % 2 == 0) {
                    b.soft(x, 15, z, ModPlants.GLOWGILL.getDefaultState());
                }
            }
        }
        b.clear(10, 1, 1, 10, 2, 2);
        b.door(10, 1, 2, Direction.NORTH);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + 0.3;
            int x = (int) Math.round(cx + Math.cos(a) * 5.5), z = (int) Math.round(cz + Math.sin(a) * 5.5);
            b.chair(x, 1, z, Direction.NORTH);
        }
        b.workstation(10, 1, 15, Blocks.BREWING_STAND, Direction.NORTH, "alchemist");
        b.set(12, 1, 15, Blocks.WATER_CAULDRON.getDefaultState().with(Properties.LEVEL_3, 3));
        b.hangingLantern(7, 6, 10, p.hangingLight);
        b.hangingLantern(13, 6, 10, p.hangingLight);
        b.marker(Blueprint.MarkerType.GATHER, 10, 1, 7, "landmark");
        b.marker(Blueprint.MarkerType.WORKSTATION, 10, 1, 13, "elder");
        b.marker(Blueprint.MarkerType.ENTRANCE, 10, 1, 0, "");
        return bp;
    }

    /** A slender crystal spire with an observation gallery, built around a glowing core. */
    private static Blueprint crystalSpire(Shells s, Palette p, int v, long seed) {
        int w = 13, d = 13;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        double cx = 6, cz = 6;
        b.disc(cx, cz, 6.2, 0, p.floor.getDefaultState());
        b.disc(cx, cz, 6.2, -1, p.foundation.getDefaultState());
        for (int y = 1; y <= 26; y++) {
            double r = Math.max(1.6, 4.6 - y * 0.11);
            b.ring(cx, cz, r, y, y, y % 7 == 0 ? p.trim : p.wall, false, null);
            if (r > 2.2) {
                b.disc(cx, cz, r - 1, y, Builder.AIR);
            }
            if (y % 4 == 0) {
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    b.set((int) Math.round(cx + dir.getOffsetX() * r), y, (int) Math.round(cz + dir.getOffsetZ() * r), p.window);
                }
            }
        }
        for (int y = 1; y <= 16; y++) {
            b.set((int) cx, y, (int) cz + 2, Architect.ladder(Direction.NORTH));
        }
        b.disc(cx, cz, 5.5, 17, p.trim.getDefaultState());
        b.ring(cx, cz, 5.5, 18, 18, p.foundationWall, false, null);
        b.set((int) cx, 17, (int) cz + 2, Architect.ladder(Direction.NORTH));
        for (int y = 27; y <= 34; y++) {
            b.set((int) cx, y, (int) cz, y == 31 ? p.glow : Blocks.AMETHYST_BLOCK);
        }
        b.set((int) cx, 35, (int) cz, b.facing(ModPlants.GLIMMER_CLUSTER, Direction.UP));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            b.set((int) Math.round(cx + Math.cos(a) * 6), 1, (int) Math.round(cz + Math.sin(a) * 6), b.facing(ModPlants.GLIMMER_CLUSTER, Direction.UP));
            b.set((int) Math.round(cx + Math.cos(a) * 5.5), 19, (int) Math.round(cz + Math.sin(a) * 5.5), p.light);
        }
        b.clear(6, 1, 1, 6, 2, 2);
        b.door(6, 1, 2, Direction.NORTH);
        b.workstation(6, 1, 4, Blocks.GRINDSTONE, Direction.NORTH, "gemcutter");
        b.marker(Blueprint.MarkerType.WORKSTATION, 6, 17, 4, "astronomer");
        b.marker(Blueprint.MarkerType.GATHER, 6, 1, -1, "landmark");
        b.marker(Blueprint.MarkerType.ENTRANCE, 6, 1, 0, "");
        return bp;
    }

    /** A stepped mountain hall: three terraces joined by a wide central stair, a council hall on top. */
    private static Blueprint stepHall(Shells s, Palette p, int v, long seed) {
        int w = 21, d = 21;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        for (int t = 0; t < 3; t++) {
            int inset = t * 3;
            int y = t * 3;
            b.fill(inset, y, inset, w - 1 - inset, y + 2, d - 1 - inset, p.foundation);
            for (int x = inset; x < w - inset; x += 2) {
                b.set(x, y + 3, inset, p.foundationWall);
            }
            b.lamp(inset, y + 3, inset);
            b.lamp(w - 1 - inset, y + 3, inset);
        }
        b.fill(0, -1, 0, w - 1, -1, d - 1, p.foundation);
        // grand stair up the front
        for (int i = 0; i < 9; i++) {
            for (int x = w / 2 - 2; x <= w / 2 + 2; x++) {
                b.set(x, i, i, b.stairs(p.foundationStairs, Direction.SOUTH, false));
                b.fill(x, i + 1, i, x, i + 3, i, Builder.AIR);
            }
        }
        // council hall on the top terrace
        int hx1 = 7, hz1 = 9, hx2 = 13, hz2 = 15, hy = 9;
        b.walls(hx1, hz1, hx2, hz2, hy, hy + 4, p.wall, p.frame);
        b.clear(hx1 + 1, hy, hz1 + 1, hx2 - 1, hy + 4, hz2 - 1);
        b.clear(10, hy, hz1, 10, hy + 1, hz1);
        b.door(10, hy, hz1, Direction.NORTH);
        b.gableRoofAlongZ(hx1, hz1, hx2, hz2, hy + 5, p.roofStairs, p.roofStairs, p.wallAlt, 1);
        b.set(10, hy, hz2 - 1, b.facing(Blocks.LECTERN, Direction.NORTH));
        b.workstation(hx1 + 1, hy, hz2 - 1, Blocks.STONECUTTER, Direction.NORTH, "stonemason");
        b.hangingLantern(10, hy + 4, 12, p.hangingLight);
        b.marker(Blueprint.MarkerType.WORKSTATION, 10, hy, hz2 - 2, "elder");
        b.marker(Blueprint.MarkerType.GATHER, 10, 9, 8, "landmark");
        b.marker(Blueprint.MarkerType.ENTRANCE, 10, 0, -1, "");
        return bp;
    }

    /** A market fountain court ringed by an arcade of timber columns and flower beds. */
    private static Blueprint fountainCourt(Shells s, Palette p, int v, long seed) {
        int w = 19, d = 19;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        double cx = 9, cz = 9;
        b.fill(0, 0, 0, w - 1, 0, d - 1, p.plaza);
        b.disc(cx, cz, 5, 0, p.plazaAccent.getDefaultState());
        b.ring(cx, cz, 4, 1, 1, p.foundationWall, false, null);
        b.disc(cx, cz, 3, 1, Blocks.WATER.getDefaultState());
        b.disc(cx, cz, 3, 0, p.foundation.getDefaultState());
        for (int y = 1; y <= 5; y++) {
            b.set((int) cx, y, (int) cz, y == 5 ? p.glow.getDefaultState() : p.foundationWall.getDefaultState());
        }
        b.set((int) cx, 6, (int) cz, Blocks.WATER);
        // arcade
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (!edge) {
                    continue;
                }
                if ((x % 3 == 0) && (z % 3 == 0) || (x % 3 == 0 && (z == 0 || z == d - 1)) || (z % 3 == 0 && (x == 0 || x == w - 1))) {
                    b.fill(x, 1, z, x, 3, z, b.axis(p.frame, Direction.Axis.Y));
                }
                b.set(x, 4, z, b.slab(p.roofSlab, false));
                if (x == w / 2 && (z == 0 || z == d - 1) || z == d / 2 && (x == 0 || x == w - 1)) {
                    b.fill(x, 1, z, x, 3, z, Builder.AIR);
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int x = (int) Math.round(cx + Math.cos(a) * 6.5), z = (int) Math.round(cz + Math.sin(a) * 6.5);
            b.set(x, 0, z, Blocks.GRASS_BLOCK);
            b.set(x, 1, z, i % 2 == 0 ? p.plant : p.plantAlt);
            b.set((int) Math.round(cx + Math.cos(a + 0.4) * 6.5), 1, (int) Math.round(cz + Math.sin(a + 0.4) * 6.5), p.light);
        }
        b.marker(Blueprint.MarkerType.GATHER, 9, 1, 3, "landmark");
        b.marker(Blueprint.MarkerType.MERCHANT, 3, 1, 3, "merchant");
        b.marker(Blueprint.MarkerType.MERCHANT, w - 4, 1, 3, "merchant");
        b.marker(Blueprint.MarkerType.ENTRANCE, 9, 1, -1, "");
        return bp;
    }

    /** A domed observatory hall with a colonnade and a glowing instrument at its centre. */
    private static Blueprint observatory(Shells s, Palette p, int v, long seed) {
        int w = 19, d = 19;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        double cx = 9, cz = 9;
        b.disc(cx, cz, 9.2, 0, p.floor.getDefaultState());
        b.disc(cx, cz, 9.2, -1, p.foundation.getDefaultState());
        b.disc(cx, cz, 3, 0, p.floorAccent.getDefaultState());
        b.ring(cx, cz, 7.5, 1, 7, p.wall, false, null);
        for (int y = 1; y <= 7; y++) {
            b.disc(cx, cz, 6.6, y, Builder.AIR);
        }
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            int x = (int) Math.round(cx + Math.cos(a) * 9), z = (int) Math.round(cz + Math.sin(a) * 9);
            b.fill(x, 1, z, x, 6, z, p.frameAxis);
            b.set(x, 7, z, p.trim);
            int wx = (int) Math.round(cx + Math.cos(a) * 7.5), wz = (int) Math.round(cz + Math.sin(a) * 7.5);
            if (i % 2 == 0) {
                b.fill(wx, 2, wz, wx, 5, wz, p.window);
            }
        }
        b.disc(cx, cz, 9.2, 8, b.slab(p.foundationSlab, false));
        int top = b.dome(cx, cz, 7.5, 8, p.roof, p.glow);
        b.set((int) cx, top, (int) cz, b.facing(Blocks.END_ROD, Direction.UP));
        b.set((int) cx, top + 1, (int) cz, b.facing(Blocks.END_ROD, Direction.UP));
        // central instrument
        b.set((int) cx, 1, (int) cz, p.foundation);
        b.set((int) cx, 2, (int) cz, p.frameAxis);
        b.set((int) cx, 3, (int) cz, p.glow);
        b.set((int) cx, 4, (int) cz, b.facing(Blocks.END_ROD, Direction.UP));
        b.workstation((int) cx, 1, (int) cz + 2, Blocks.CARTOGRAPHY_TABLE, Direction.NORTH, "astronomer");
        b.workstation((int) cx + 3, 1, (int) cz + 3, Blocks.LECTERN, Direction.NORTH, "scholar");
        b.clear(9, 1, 1, 9, 2, 1);
        b.clear(9, 1, 2, 9, 3, 2);
        b.door(9, 1, 2, Direction.NORTH);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            b.hangingLantern((int) Math.round(cx + Math.cos(a) * 5), 7, (int) Math.round(cz + Math.sin(a) * 5), p.hangingLight);
        }
        b.marker(Blueprint.MarkerType.GATHER, 9, 1, 6, "landmark");
        b.marker(Blueprint.MarkerType.ENTRANCE, 9, 1, -1, "");
        return bp;
    }
}
