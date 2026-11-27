package dev.underdeep.explore.design;

import dev.underdeep.explore.Canvas;
import dev.underdeep.explore.ExploreBlocks;
import dev.underdeep.explore.Site;
import dev.underdeep.explore.Style;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * Ruins and abandoned places (Stage 9): small roadside remains, regional ruins and significant ruined complexes.
 * Each design has its own former purpose told by layout and objects; region and culture change materials and
 * overgrowth. Spoiler-sensitive meaning lives in docs/private/EXPLORATION.md.
 */
final class Ruins {
    private Ruins() {
    }

    static final Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;

    // ================================================================== minor

    static void wayshrine(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m, 3.5, 0, k.s.floor, k.s.floorAlt);
        k.air(m - 3, 1, m - 3, m + 3, 6, m + 3);
        for (int[] p : new int[][]{{m - 2, m - 2}, {m + 2, m - 2}}) {
            k.pillar(p[0], p[1], 1, 3 + k.ri(2), true);
        }
        k.c.set(m - 1, 4, m - 2, Style.slab(k.s.trimSlab, true));
        k.c.set(m, 4, m - 2, Style.slab(k.s.trimSlab, true));
        k.c.set(m + 1, 4, m - 2, Style.slab(k.s.trimSlab, true));
        k.c.set(m, 1, m - 2, k.s.chiseled);
        k.inscription(m, 2, m - 2, S, Designs.carved(k, site));
        k.pot(m - 1, 1, m - 1, k.chance(0.5) ? "underdeep:exploration/minor_cache" : null);
        k.c.set(m + 1, 1, m - 1, Blocks.CANDLE.getDefaultState().with(Properties.CANDLES, 3).with(Properties.LIT, false));
        k.c.set(m, 1, m + 1, Style.slab(k.s.brickSlab, false));
        k.weather(0.5, false);
    }

    static void lanternPost(Kit k, Site site) {
        // a line of fallen League lantern posts along an old road
        int m = k.mid;
        for (int i = -k.mid + 1; i <= k.mid - 1; i++) {
            k.c.set(m + i, 0, m, (i % 2 == 0 ? k.s.floorAlt : k.s.floor).getDefaultState());
            k.c.set(m + i, 0, m + 1, k.s.floorAlt.getDefaultState());
        }
        for (int i = -k.mid + 2; i <= k.mid - 2; i += 5) {
            boolean standing = k.chance(0.5);
            int x = m + i, z = m - 1;
            if (standing) {
                for (int y = 1; y <= 4; y++) {
                    k.c.set(x, y, z, Style.axis(Blocks.OXIDIZED_COPPER, Direction.Axis.Y).getBlock() == Blocks.OXIDIZED_COPPER ? Blocks.OXIDIZED_CUT_COPPER.getDefaultState()
                            : Blocks.OXIDIZED_COPPER.getDefaultState());
                }
                k.c.set(x, 5, z, Blocks.OXIDIZED_COPPER_GRATE);
                k.c.set(x, 4, z + 1, Blocks.CHAIN.getDefaultState());
                k.c.set(x, 3, z + 1, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
            } else {
                for (int d = 0; d < 4; d++) {
                    k.c.set(x + d - 1, 1, z - 1, Blocks.OXIDIZED_CUT_COPPER_SLAB.getDefaultState());
                }
                k.c.set(x + 3, 1, z - 2, Blocks.OXIDIZED_COPPER_GRATE);
            }
        }
        k.c.set(m, 1, m + 2, Style.facing(ExploreBlocks.INSCRIPTION, N));
        k.inscription(m, 1, m + 2, N, Designs.carved(k, site));
        k.weather(0.3, false);
    }

    static void collapsedHut(Kit k, Site site) {
        int m = k.mid;
        k.shell(m - 3, m - 3, m + 3, m + 2, 0, 3, false, 0.6);
        k.air(m - 1, 1, m - 3, m, 2, m - 3);
        k.c.set(m + 1, 1, m + 1, Blocks.CAMPFIRE.getDefaultState().with(Properties.LIT, false));
        k.bedRemains(m - 2, 1, m + 1, E);
        k.table(m + 2, 1, m - 2);
        k.chair(m + 1, 1, m - 2, W);
        k.pot(m - 2, 1, m - 2, "underdeep:exploration/minor_cache");
        for (int x = m - 3; x <= m + 3; x++) {
            if (k.chance(0.6)) {
                k.c.set(x, 4, m - 3 + k.ri(6), Style.slab(k.s.woodSlab, false));
            }
        }
        k.weather(0.55, true);
    }

    static void minersCamp(Kit k, Site site) {
        int m = k.mid;
        k.floor(m - 4, m - 1, m + 4, m + 1, 0, Blocks.GRAVEL, k.s.rubble);
        for (int x = m - 5; x <= m + 5; x++) {
            if (k.chance(0.8)) {
                k.c.set(x, 1, m, Blocks.RAIL.getDefaultState().with(Properties.RAIL_SHAPE, net.minecraft.block.enums.RailShape.EAST_WEST));
            }
        }
        k.c.set(m - 3, 1, m + 2, Blocks.CAMPFIRE.getDefaultState().with(Properties.LIT, false));
        k.barrelLoot(m + 2, 1, m + 2, "underdeep:exploration/miners");
        k.barrel(m + 3, 1, m + 2);
        k.c.set(m + 3, 2, m + 2, Style.slab(Blocks.SPRUCE_SLAB, false));
        k.c.set(m - 2, 1, m - 2, Blocks.SPRUCE_FENCE);
        k.c.set(m - 2, 2, m - 2, Blocks.SPRUCE_FENCE);
        k.c.set(m - 2, 3, m - 2, Blocks.LANTERN);
        k.bedRemains(m - 4, 1, m + 2, E);
        k.skeleton(m + 4, 1, m - 2);
        k.c.set(m + 1, 1, m - 2, Blocks.IRON_ORE);
        k.weather(0.3, true);
    }

    static void cairnCircle(Kit k, Site site) {
        int m = k.mid;
        for (int i = 0; i < 7; i++) {
            double a = Math.PI * 2 * i / 7;
            int x = (int) Math.round(m + Math.cos(a) * (k.mid - 1.5)), z = (int) Math.round(m + Math.sin(a) * (k.mid - 1.5));
            int h = 2 + k.ri(3);
            for (int y = 1; y <= h; y++) {
                k.c.set(x, y, z, (y == h ? k.s.brickSlab : k.s.rubble).getDefaultState());
            }
        }
        k.c.set(m, 1, m, k.s.chiseled);
        k.c.set(m, 2, m, Style.facing(ExploreBlocks.GLYPH_TILE, N).with(ExploreBlocks.SYMBOL, k.ri(6)));
        k.weather(0.2, false);
    }

    static void brokenBridge(Kit k, Site site) {
        int m = k.mid;
        int span = k.n - 2;
        int gapFrom = m - 1 - k.ri(2), gapTo = m + 1 + k.ri(2);
        for (int x = 1; x < 1 + span; x++) {
            if (x >= gapFrom && x <= gapTo) {
                continue;
            }
            int h = 3;
            k.c.set(x, h, m - 1, k.s.brick.getDefaultState());
            k.c.set(x, h, m, k.s.floor.getDefaultState());
            k.c.set(x, h, m + 1, k.s.brick.getDefaultState());
            k.c.set(x, h + 1, m - 1, k.s.brickWall.getDefaultState());
            k.c.set(x, h + 1, m + 1, k.s.brickWall.getDefaultState());
            k.air(x, h + 1, m, x, h + 3, m);
        }
        for (int x : new int[]{2, k.n - 3}) {
            for (int y = 0; y < 3; y++) {
                k.c.set(x, y, m, Style.axis(k.s.pillar, Direction.Axis.Y));
            }
        }
        for (int x = gapFrom; x <= gapTo; x++) {
            if (k.chance(0.5)) {
                k.c.set(x, 1, m + k.ri(3) - 1, k.s.rubble.getDefaultState());
            }
        }
        k.weather(0.4, false);
    }

    static void fallenStatue(Kit k, Site site) {
        // a colossal head lying on its side, half sunk in the floor
        int m = k.mid;
        for (int x = m - 4; x <= m + 4; x++) {
            for (int y = 0; y <= 6; y++) {
                for (int z = m - 3; z <= m + 3; z++) {
                    double d = Math.pow((x - m) / 4.2, 2) + Math.pow((y - 2) / 4.4, 2) + Math.pow((z - m) / 3.4, 2);
                    if (d <= 1) {
                        k.c.set(x, y, z, (d > 0.82 ? k.s.block : k.s.pillar).getDefaultState());
                    }
                }
            }
        }
        // face: brow, eyes, nose on the upturned side (+z)
        k.c.set(m - 2, 3, m + 3, Blocks.AIR.getDefaultState());
        k.c.set(m + 2, 3, m + 3, Blocks.AIR.getDefaultState());
        k.c.set(m - 2, 3, m + 2, k.s.glow.getDefaultState());
        k.c.set(m + 2, 3, m + 2, k.s.glow.getDefaultState());
        k.c.set(m, 2, m + 4, k.s.block.getDefaultState());
        for (int x = m - 3; x <= m + 3; x++) {
            k.c.set(x, 4, m + 4, Style.slab(k.s.trimSlab, false));
        }
        k.c.set(m, 1, m + 4, Style.stairs(k.s.brickStairs, S, true));
        k.inscription(m + 5, 1, m, E, Designs.carved(k, site));
        k.weather(0.35, false);
    }

    static void wellHead(Kit k, Site site) {
        int m = k.mid;
        k.ring(m, m, 2, 0, 1, k.s.brick);
        k.c.set(m - 2, 2, m, k.s.fence);
        k.c.set(m + 2, 2, m, k.s.fence);
        for (int x = m - 2; x <= m + 2; x++) {
            k.c.set(x, 3, m, Style.slab(k.s.woodSlab, false));
        }
        k.c.set(m, 2, m, Blocks.CHAIN.getDefaultState());
        // the shaft down, with a ladder; at the bottom a forgotten side tunnel (secret)
        int depth = Math.min(14, site.family().below() - 2);
        for (int y = 1; y >= -depth; y--) {
            k.air(m - 1, y, m - 1, m + 1, y, m + 1);
        }
        for (int y = 0; y >= -depth; y--) {
            k.c.set(m, y, m - 2, k.s.brick);
            k.c.set(m, y, m - 1, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, S));
            k.c.lock(m, y, m - 1);
        }
        k.c.set(m, -depth - 1, m, Blocks.WATER);
        k.c.set(m + 1, -depth - 1, m, Blocks.WATER);
        k.room(m + 2, -depth - 1, m - 2, m + 7, -depth + 3, m + 2);
        k.air(m + 1, -depth, m - 1, m + 2, -depth + 1, m);
        k.coffer(m + 6, -depth, m, W, "underdeep:exploration/secret_stash");
        k.inscription(m + 7, -depth + 1, m, W, Designs.loose(k, site));
        k.c.secret("well_bottom", m + 3, -depth, m - 1, m + 6, -depth + 2, m + 1);
        k.weather(0.3, true);
    }

    static void watchNiche(Kit k, Site site) {
        int m = k.mid;
        k.shell(m - 3, m - 2, m + 3, m + 2, 0, 4, true, 0.3);
        k.air(m - 1, 1, m + 2, m + 1, 3, m + 2);
        k.arch(m - 2, m + 2, m + 2, 1, true);
        k.c.set(m, 1, m - 1, Style.stairs(k.s.woodStairs, S, false));
        k.c.set(m - 2, 1, m - 1, k.s.bars);
        k.c.set(m + 2, 1, m - 1, Blocks.CAULDRON);
        k.lantern(m, 4, m);
        k.chest(m + 2, 1, m + 1, W, "underdeep:exploration/watch");
        k.weather(0.35, true);
    }

    static void graveGarden(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m, k.mid - 1, 0, k.s.moss, k.s.floor);
        for (int i = 0; i < 5; i++) {
            int x = m - 4 + i * 2, z = m - 2 + (i % 2) * 3;
            k.c.set(x, 1, z, Style.facing(k.s.brickWall, N));
            k.c.set(x, 2, z, Style.slab(k.s.trimSlab, false));
            if (k.chance(0.7) && !k.s.plants.isEmpty()) {
                k.c.set(x, 1, z + 1, k.pick(k.s.plants).getDefaultState());
            }
        }
        k.c.set(m, 1, m + 3, Blocks.FLOWER_POT);
        k.inscription(m, 1, m - 4, S, Designs.carved(k, site));
        k.c.set(m - 1, 1, m - 4, Blocks.CANDLE.getDefaultState().with(Properties.CANDLES, 2));
        k.weather(0.2, false);
    }

    static void kilnMound(Kit k, Site site) {
        int m = k.mid;
        for (int y = 0; y <= 4; y++) {
            k.ring(m, m, 3 - y * 0.5, y, y, y < 3 ? Blocks.BRICKS : Blocks.MUD_BRICKS);
        }
        k.air(m - 1, 1, m - 1, m + 1, 3, m + 1);
        k.air(m, 1, m - 3, m, 2, m - 2);
        k.c.set(m, 1, m, Blocks.CAMPFIRE.getDefaultState().with(Properties.LIT, false));
        for (int[] p : new int[][]{{m + 3, m + 2}, {m - 3, m + 2}, {m + 4, m - 1}}) {
            k.pot(p[0], 1, p[1], "underdeep:exploration/kiln_pot");
        }
        k.c.set(m - 4, 1, m - 2, Blocks.CLAY);
        k.weather(0.25, false);
    }

    static void waystone(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m, 2.5, 0, k.s.floor, k.s.floorAlt);
        for (int y = 1; y <= 6; y++) {
            k.c.set(m, y, m, (y == 3 ? k.s.chiseled : k.s.block).getDefaultState());
        }
        k.c.set(m, 7, m, Style.slab(k.s.trimSlab, false));
        for (Direction d : Direction.Type.HORIZONTAL) {
            k.c.set(m + d.getOffsetX(), 4, m + d.getOffsetZ(), Style.stairs(k.s.trimStairs, d.getOpposite(), true));
        }
        k.inscription(m, 2, m + 1, S, Designs.waystoneRecord(k, site));
        k.weather(0.15, false);
    }

    // ================================================================== regional

    static void cottageRow(Kit k, Site site) {
        int m = k.mid;
        k.floor(1, m - 1, k.n - 2, m + 1, 0, k.s.floorAlt, k.s.floor);
        int x = 2;
        for (int h = 0; h < 3; h++) {
            int w = 4 + k.ri(2);
            int z1 = h % 2 == 0 ? m - 8 : m + 2;
            k.shell(x, z1, x + w, z1 + 5, 0, 4, h != 1, 0.25 + h * 0.15);
            k.air(x + w / 2, 1, h % 2 == 0 ? z1 + 5 : z1, x + w / 2, 2, h % 2 == 0 ? z1 + 5 : z1);
            k.bedRemains(x + 1, 1, z1 + 2, h % 2 == 0 ? N : S);
            k.table(x + w - 1, 1, z1 + 2);
            k.shelf(x + w - 1, 1, h % 2 == 0 ? z1 + 1 : z1 + 4, false);
            k.c.set(x + 1, 1, h % 2 == 0 ? z1 + 4 : z1 + 1, Blocks.FURNACE.getDefaultState());
            if (h == 1) {
                // the middle house hides a cellar under a loose floor tile (secret)
                k.c.set(x + 2, 0, z1 + 3, Blocks.SPRUCE_TRAPDOOR.getDefaultState().with(Properties.HORIZONTAL_FACING, N));
                k.c.lock(x + 2, 0, z1 + 3);
                k.room(x, -5, z1, x + w, -1, z1 + 5);
                for (int y = -4; y <= -1; y++) {
                    k.c.set(x + 2, y, z1 + 2, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, S));
                    k.c.lock(x + 2, y, z1 + 2);
                }
                k.coffer(x + w - 1, -4, z1 + 4, W, "underdeep:exploration/cellar");
                k.barrelLoot(x + 1, -4, z1 + 4, "underdeep:exploration/ruin_common");
                k.inscription(x + w, -3, z1 + 2, W, Designs.loose(k, site));
                k.c.secret("cellar_beneath", x + 1, -4, z1 + 1, x + w - 1, -2, z1 + 4);
            } else {
                k.chest(x + 1, 1, h % 2 == 0 ? z1 + 1 : z1 + 4, E, "underdeep:exploration/ruin_common");
            }
            x += w + 2;
        }
        k.c.set(m, 1, m, Blocks.CAULDRON);
        k.weather(0.45, true);
    }

    static void lanternWaystation(Kit k, Site site) {
        int m = k.mid;
        k.shell(m - 6, m - 4, m + 6, m + 4, 0, 5, true, 0.2);
        for (int x = m - 5; x <= m + 5; x += 2) {
            k.c.set(x, 3, m - 4, k.s.glass);
            k.c.set(x, 3, m + 4, k.s.glass);
        }
        k.air(m - 1, 1, m + 4, m + 1, 3, m + 4);
        k.arch(m - 2, m + 2, m + 4, 1, true);
        k.c.set(m - 5, 1, m - 3, Blocks.OXIDIZED_COPPER_BULB);
        k.c.set(m + 5, 1, m - 3, Blocks.OXIDIZED_COPPER_GRATE);
        for (int y = 1; y <= 3; y++) {
            k.c.set(m + 4, y, m - 3, Blocks.LIGHTNING_ROD.getDefaultState());
        }
        k.table(m, 1, m - 1);
        k.table(m + 1, 1, m - 1);
        k.chair(m, 1, m, N);
        k.c.set(m - 4, 1, m + 2, Blocks.CARTOGRAPHY_TABLE);
        k.shelf(m - 5, 1, m + 3, true);
        k.chest(m - 5, 1, m - 1, E, "underdeep:exploration/league_cache");
        k.inscription(m + 5, 2, m, W, Designs.carved(k, site));
        for (int x = m - 4; x <= m + 4; x += 4) {
            k.lantern(x, 5, m);
        }
        // the pump outside
        k.c.set(m + 8, 1, m, Blocks.OXIDIZED_COPPER);
        k.c.set(m + 8, 2, m, Blocks.OXIDIZED_COPPER_GRATE);
        k.c.set(m + 8, 3, m, Blocks.LIGHTNING_ROD);
        k.c.set(m + 8, 0, m, Blocks.WATER);
        k.weather(0.3, true);
    }

    static void forgeRuin(Kit k, Site site) {
        int m = k.mid;
        k.shell(m - 6, m - 5, m + 6, m + 5, 0, 6, false, 0.4);
        k.air(m - 1, 1, m + 5, m + 1, 3, m + 5);
        // the great hearth on the back wall
        for (int x = m - 2; x <= m + 2; x++) {
            for (int y = 1; y <= 4; y++) {
                k.c.set(x, y, m - 5, (y == 1 && Math.abs(x - m) <= 1 ? Blocks.MAGMA_BLOCK : k.s.block).getDefaultState());
            }
        }
        k.air(m - 1, 2, m - 4, m + 1, 3, m - 4);
        k.c.set(m, 1, m - 4, Blocks.CAMPFIRE.getDefaultState().with(Properties.LIT, false));
        k.c.set(m - 3, 1, m - 1, Blocks.ANVIL);
        k.c.set(m + 3, 1, m - 1, Blocks.CHIPPED_ANVIL);
        k.c.set(m + 4, 1, m + 3, Blocks.SMITHING_TABLE);
        k.c.set(m - 4, 1, m + 3, Blocks.CAULDRON);
        k.chest(m + 5, 1, m - 3, W, "underdeep:exploration/ruin_forge");
        // under the cold anvil a strongbox was bricked in (secret)
        k.c.set(m - 3, 0, m - 1, k.s.cracked);
        k.room(m - 5, -4, m - 3, m - 1, -1, m + 1);
        k.c.set(m - 3, 0, m - 2, Blocks.AIR.getDefaultState());
        k.c.lock(m - 3, 0, m - 2);
        k.c.set(m - 3, -1, m - 2, Blocks.SCAFFOLDING.getDefaultState());
        k.c.lock(m - 3, -1, m - 2);
        k.coffer(m - 4, -3, m, E, "underdeep:exploration/strongbox");
        k.c.secret("forge_strongbox", m - 4, -3, m - 2, m - 2, -2, m);
        k.inscription(m + 6, 2, m + 2, W, Designs.carved(k, site));
        k.weather(0.4, false);
    }

    static void tideShrine(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m, k.mid - 1, 0, k.s.floor, k.s.brick);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            int x = (int) Math.round(m + Math.cos(a) * (k.mid - 3)), z = (int) Math.round(m + Math.sin(a) * (k.mid - 3));
            k.pillar(x, z, 1, 4 + k.ri(3), true);
        }
        k.disc(m, m, 2.5, 1, k.s.trim, null);
        k.c.set(m, 2, m, k.s.glow);
        k.c.set(m, 3, m, Blocks.CONDUIT.asItem() == null ? k.s.glow : Blocks.SEA_PICKLE.getDefaultState().getBlock());
        k.inscription(m, 2, m + 2, S, Designs.carved(k, site));
        k.chest(m - 2, 2, m, E, "underdeep:exploration/tide");
        // a pearl grotto under the shrine floor, reached through a gap behind the altar (secret)
        k.air(m, 1, m - 3, m, 1, m - 3);
        k.c.set(m, 0, m - 3, Blocks.AIR.getDefaultState());
        k.c.lock(m, 0, m - 3);
        k.room(m - 3, -5, m - 6, m + 3, -1, m - 2);
        k.air(m, -1, m - 3, m, -1, m - 3);
        k.coffer(m + 2, -4, m - 5, W, "underdeep:exploration/pearl");
        k.c.set(m - 2, -4, m - 5, Blocks.SEA_LANTERN);
        k.c.secret("pearl_grotto", m - 2, -4, m - 5, m + 2, -2, m - 3);
        k.weather(0.35, false);
    }

    static void seedTerrace(Kit k, Site site) {
        int m = k.mid;
        for (int t = 0; t < 3; t++) {
            int r = k.mid - 1 - t * 3;
            k.disc(m, m, r, t, k.s.brick, k.s.trim);
            k.disc(m, m, r - 1, t, Blocks.ROOTED_DIRT, null);
            for (int i = 0; i < r * 3; i++) {
                double a = Math.PI * 2 * i / (r * 3);
                int x = (int) Math.round(m + Math.cos(a) * (r - 1.5)), z = (int) Math.round(m + Math.sin(a) * (r - 1.5));
                if (!k.s.plants.isEmpty() && k.chance(0.5)) {
                    k.c.set(x, t + 1, z, k.pick(k.s.plants).getDefaultState());
                }
            }
        }
        k.c.set(m, 3, m, Blocks.COMPOSTER);
        k.c.set(m + 1, 3, m, Blocks.FLOWER_POT);
        k.inscription(m - 1, 3, m, E, Designs.carved(k, site));
        k.pot(m, 3, m + 1, "underdeep:exploration/gardener");
        k.weather(0.25, false);
    }

    static void starDial(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m, k.mid - 1, 0, k.s.floor, k.s.trim);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            int x = (int) Math.round(m + Math.cos(a) * (k.mid - 2)), z = (int) Math.round(m + Math.sin(a) * (k.mid - 2));
            k.c.set(x, 1, z, (i % 3 == 0 ? k.s.glow : k.s.chiseled).getDefaultState());
        }
        for (int y = 1; y <= 7; y++) {
            k.c.set(m + (y > 4 ? 1 : 0), y, m, Style.axis(k.s.pillar, Direction.Axis.Y));
        }
        k.c.set(m + 1, 8, m, Blocks.AMETHYST_CLUSTER);
        k.inscription(m, 1, m + 3, S, Designs.carved(k, site));
        k.chest(m - 3, 1, m, E, "underdeep:exploration/star");
        k.weather(0.15, false);
    }

    static void fungalHall(Kit k, Site site) {
        int m = k.mid;
        k.shell(m - 7, m - 5, m + 7, m + 5, 0, 6, true, 0.5);
        k.air(m - 1, 1, m + 5, m + 1, 3, m + 5);
        // a giant mushroom has grown through the hall
        for (int y = 1; y <= 10; y++) {
            k.c.set(m + 2, y, m - 1, Blocks.MUSHROOM_STEM.getDefaultState());
        }
        k.disc(m + 2, m - 1, 4.5, 10, Blocks.RED_MUSHROOM_BLOCK, null);
        k.disc(m + 2, m - 1, 3.5, 11, Blocks.RED_MUSHROOM_BLOCK, null);
        for (int x = m - 5; x <= m + 5; x += 3) {
            k.table(x, 1, m + 2);
        }
        k.c.set(m - 6, 1, m - 4, Blocks.BREWING_STAND);
        k.shelf(m - 6, 1, m - 3, false);
        k.chest(m - 6, 1, m + 4, E, "underdeep:exploration/fungal");
        k.inscription(m + 7, 2, m + 2, W, Designs.carved(k, site));
        k.weather(0.6, true);
    }

    static void iceHermitage(Kit k, Site site) {
        int m = k.mid;
        k.shell(m - 4, m - 3, m + 4, m + 3, 0, 4, true, 0.1);
        k.air(m, 1, m + 3, m, 2, m + 3);
        // half buried in ice
        for (int x = m - 6; x <= m + 6; x++) {
            for (int z = m - 5; z <= m - 3; z++) {
                for (int y = 1; y <= 5 - Math.abs(x - m) / 2; y++) {
                    k.c.soft(x, y, z, (y % 2 == 0 ? Blocks.PACKED_ICE : Blocks.BLUE_ICE).getDefaultState());
                }
            }
        }
        k.c.set(m - 3, 1, m - 2, Blocks.FURNACE);
        k.bedRemains(m + 2, 1, m - 1, W);
        k.c.set(m + 3, 1, m + 2, Blocks.WHITE_WOOL);
        k.chest(m - 3, 1, m + 2, E, "underdeep:exploration/hermit");
        k.inscription(m + 4, 2, m, W, Designs.loose(k, site));
        k.weather(0.2, false);
    }

    static void stiltVillage(Kit k, Site site) {
        int m = k.mid;
        int deck = 3;
        for (int h = 0; h < 3; h++) {
            int cx = 4 + h * 6, cz = h % 2 == 0 ? m - 3 : m + 3;
            for (int[] p : new int[][]{{cx - 2, cz - 2}, {cx + 2, cz - 2}, {cx - 2, cz + 2}, {cx + 2, cz + 2}}) {
                for (int y = -2; y <= deck; y++) {
                    k.c.set(p[0], y, p[1], Style.axis(k.s.pillar == Blocks.BASALT ? Blocks.SPRUCE_LOG : Blocks.SPRUCE_LOG, Direction.Axis.Y));
                }
            }
            boolean collapsed = h == 2;
            for (int x = cx - 2; x <= cx + 2; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    if (!collapsed || k.chance(0.4)) {
                        k.c.set(x, deck, z, k.s.wood);
                    }
                }
            }
            if (!collapsed) {
                k.c.set(cx - 1, deck + 1, cz, Style.slab(k.s.woodSlab, false));
                k.c.set(cx + 1, deck + 1, cz - 1, Blocks.BARREL);
                k.chest(cx + 1, deck + 1, cz + 1, W, "underdeep:exploration/ruin_common");
                for (int x = cx - 2; x <= cx + 2; x++) {
                    k.c.set(x, deck + 1, cz - 2, k.s.fence);
                }
            }
        }
        for (int x = 6; x <= 16; x++) {
            if (k.chance(0.65)) {
                k.c.set(x, deck, m, k.s.woodSlab);
            }
        }
        k.c.set(10, deck + 1, m, Style.facing(ExploreBlocks.INSCRIPTION, S));
        k.inscription(10, deck + 1, m, S, Designs.carved(k, site));
        k.weather(0.4, false);
    }

    static void stormTower(Kit k, Site site) {
        int m = k.mid;
        int h = 14;
        k.ring(m, m, 3, 0, h, k.s.brick);
        k.disc(m, m, 2.5, 0, k.s.floor, null);
        k.air(m - 2, 1, m - 2, m + 2, h, m + 2);
        k.air(m, 1, m + 3, m, 2, m + 3);
        k.spiral(m, m, 1, h - 2);
        // the broken crown with a copper rod and charged stone
        for (int y = h + 1; y <= h + 4; y++) {
            k.c.set(m, y, m, Blocks.LIGHTNING_ROD);
        }
        k.c.set(m, h, m, Blocks.LIGHTNING_ROD);
        for (int i = 0; i < 6; i++) {
            k.c.set(m - 3 + k.ri(7), 1, m - 6 + k.ri(3), k.s.rubble);
        }
        k.chest(m + 1, h - 1, m + 1, W, "underdeep:exploration/storm");
        k.c.set(m - 1, h - 1, m + 1, dev.underdeep.registry.ModBlocks.CHARGED_STORMSTONE);
        k.inscription(m + 1, 2, m - 2, S, Designs.carved(k, site));
        k.weather(0.35, false);
    }

    static void quarry(Kit k, Site site) {
        int m = k.mid;
        // terraced pit
        for (int t = 0; t < 4; t++) {
            int r = k.mid - 2 - t * 2;
            for (int x = m - r; x <= m + r; x++) {
                for (int z = m - r; z <= m + r; z++) {
                    k.c.set(x, -t, z, Canvas.AIR);
                    k.c.set(x, -t - 1, z, (t == 3 ? k.s.rubble : Blocks.STONE).getDefaultState());
                }
            }
        }
        // wooden crane
        for (int y = 1; y <= 7; y++) {
            k.c.set(m - k.mid + 2, y, m, Blocks.SPRUCE_LOG.getDefaultState());
        }
        for (int x = m - k.mid + 2; x <= m - 2; x++) {
            k.c.set(x, 8, m, Blocks.SPRUCE_PLANKS);
        }
        for (int y = 4; y <= 7; y++) {
            k.c.set(m - 2, y, m, Blocks.CHAIN);
        }
        k.c.set(m - 2, 3, m, k.s.brick);
        k.c.set(m + 2, -3, m + 1, Blocks.STONECUTTER);
        k.barrelLoot(m - 3, -3, m + 2, "underdeep:exploration/miners");
        for (int i = 0; i < 4; i++) {
            k.c.set(m - 2 + i, -3, m - 2, k.s.brick);
        }
        k.inscription(m + k.mid - 2, 1, m, W, Designs.carved(k, site));
        k.weather(0.3, false);
    }

    static void silentRuin(Kit k, Site site) {
        int m = k.mid;
        k.disc(m, m, k.mid - 1, 0, k.s.floor, k.s.floorAlt);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            int x = (int) Math.round(m + Math.cos(a) * (k.mid - 3)), z = (int) Math.round(m + Math.sin(a) * (k.mid - 3));
            k.pillar(x, z, 1, 3 + k.ri(5), false);
            k.c.set(x, 1, z + 1, Blocks.SCULK_VEIN.getDefaultState().with(Properties.DOWN, true));
        }
        k.c.set(m, 1, m, Blocks.SCULK_SENSOR);
        k.c.set(m, 0, m, Blocks.SCULK_CATALYST);
        k.c.set(m + 2, 1, m, Blocks.SOUL_CAMPFIRE.getDefaultState().with(Properties.LIT, false));
        k.inscription(m - 2, 1, m, E, Designs.carved(k, site));
        k.chest(m, 1, m - 3, S, "underdeep:exploration/silent");
        k.weather(0.4, false);
    }

    // ================================================================== significant

    static void vaultGatehouse(Kit k, Site site) {
        int m = k.mid;
        int h = 9;
        // twin towers and a gate hall
        for (int side : new int[]{-1, 1}) {
            int cx = m + side * 9;
            k.shell(cx - 3, m - 4, cx + 3, m + 4, 0, h + 4, true, 0.15);
            k.spiral(cx, m, 1, h + 1);
            k.air(cx - side * 3, 1, m, cx - side * 3, 3, m);
        }
        k.shell(m - 6, m - 5, m + 6, m + 5, 0, h, true, 0.25);
        k.air(m - 1, 1, m + 5, m + 1, 4, m + 5);
        k.arch(m - 2, m + 2, m + 5, 1, true);
        k.air(m - 1, 1, m - 5, m + 1, 4, m - 5);
        for (int x = m - 4; x <= m + 4; x += 4) {
            k.pillar(x, m - 2, 1, h - 1, true);
            k.pillar(x, m + 2, 1, h - 1, true);
        }
        for (int x = m - 5; x <= m + 5; x++) {
            k.c.set(x, 1, m, k.s.trimSlab);
        }
        k.lantern(m, h - 1, m);
        // gate machinery and storytelling
        k.c.set(m - 5, 1, m - 4, Blocks.GRINDSTONE);
        k.c.set(m + 5, 1, m - 4, k.s.bars);
        k.chest(m + 5, 1, m + 3, W, "underdeep:exploration/gatehouse");
        k.inscription(m - 6, 2, m, E, Designs.carved(k, site));
        // the armory behind a false wall in the west tower (a cracked course is the clue)
        int ax = m - 9;
        k.c.set(ax - 3, 1, m + 2, k.s.cracked);
        k.c.set(ax - 3, 2, m + 2, k.s.cracked);
        k.room(ax - 8, 0, m, ax - 3, 4, m + 4);
        k.c.set(ax - 3, 1, m + 2, Canvas.AIR);
        k.c.set(ax - 3, 2, m + 2, k.s.cracked);
        k.c.set(ax - 3, 1, m + 2, k.s.cracked);
        k.c.lock(ax - 3, 1, m + 2);
        k.c.lock(ax - 3, 2, m + 2);
        k.coffer(ax - 7, 1, m + 2, E, "underdeep:exploration/armory");
        k.c.set(ax - 7, 1, m + 3, k.s.bars);
        k.c.secret("gatehouse_armory", ax - 7, 1, m + 1, ax - 4, 3, m + 3);
        k.weather(0.35, true);
    }

    static void aqueduct(Kit k, Site site) {
        int m = k.mid;
        int top = 10;
        for (int x = 1; x < k.n - 1; x++) {
            for (int z = m - 1; z <= m + 1; z++) {
                k.c.set(x, top, z, k.s.brick);
                k.c.set(x, top + 1, z, z == m ? Blocks.WATER : k.s.brick);
            }
            boolean pier = (x - 1) % 6 == 0;
            if (pier) {
                for (int y = 0; y < top; y++) {
                    for (int z = m - 1; z <= m + 1; z++) {
                        k.c.set(x, y, z, Style.axis(k.s.pillar, Direction.Axis.Y));
                    }
                }
            }
        }
        for (int x0 = 1; x0 + 6 < k.n - 1; x0 += 6) {
            for (int z = m - 1; z <= m + 1; z++) {
                k.arch(x0, x0 + 6, z, top - 4, true);
            }
        }
        // collapsed span
        int gap = m - 2 + k.ri(4);
        for (int x = gap; x <= gap + 3; x++) {
            for (int z = m - 1; z <= m + 1; z++) {
                k.c.set(x, top, z, Canvas.AIR);
                k.c.set(x, top + 1, z, Canvas.AIR);
                k.c.set(x + k.ri(3) - 1, 1, z + k.ri(3) - 1, k.s.rubble);
            }
        }
        // the inner maintenance channel of one pier (secret)
        int px = 1 + 6 * 2;
        k.air(px, 1, m, px, top - 1, m);
        for (int y = 1; y < top; y++) {
            k.c.set(px, y, m - 1, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, S));
            k.c.lock(px, y, m - 1);
        }
        k.c.set(px, 1, m + 1, Blocks.AIR.getDefaultState());
        k.c.set(px, 2, m + 1, Blocks.AIR.getDefaultState());
        k.c.lock(px, 1, m + 1);
        k.c.lock(px, 2, m + 1);
        k.coffer(px, top - 1, m, S, "underdeep:exploration/secret_stash");
        k.c.secret("aqueduct_channel", px, top - 3, m, px, top - 1, m);
        k.inscription(px - 1, 2, m + 2, S, Designs.carved(k, site));
        k.weather(0.3, false);
    }

    static void marketSquare(Kit k, Site site) {
        int m = k.mid;
        k.floor(2, 2, k.n - 3, k.n - 3, 0, k.s.floor, k.s.floorAlt);
        k.disc(m, m, 3, 1, k.s.brick, null);
        k.disc(m, m, 2, 1, Blocks.WATER, null);
        k.pillar(m, m, 1, 4, true);
        k.c.set(m, 6, m, k.s.light);
        int stall = 0;
        for (int[] p : new int[][]{{4, 4}, {k.n - 7, 4}, {4, k.n - 7}, {k.n - 7, k.n - 7}, {m - 2, 3}, {m - 2, k.n - 6}}) {
            for (int x = p[0]; x <= p[0] + 3; x++) {
                k.c.set(x, 1, p[1], k.s.woodSlab);
                k.c.set(x, 1, p[1] + 2, k.s.fence);
                k.c.set(x, 4, p[1] + 1, Blocks.WHITE_CARPET.getDefaultState().getBlock() == null ? k.s.woodSlab : (stall % 2 == 0 ? Blocks.RED_WOOL : Blocks.YELLOW_WOOL));
            }
            k.c.set(p[0], 2, p[1] + 2, k.s.fence);
            k.c.set(p[0], 3, p[1] + 2, k.s.fence);
            k.c.set(p[0] + 3, 2, p[1] + 2, k.s.fence);
            k.c.set(p[0] + 3, 3, p[1] + 2, k.s.fence);
            if (stall % 2 == 0) {
                k.barrelLoot(p[0] + 1, 1, p[1] + 1, "underdeep:exploration/market");
            } else {
                k.pot(p[0] + 2, 1, p[1] + 1, "underdeep:exploration/kiln_pot");
            }
            stall++;
        }
        k.c.set(m + 5, 1, m, Blocks.LECTERN);
        k.inscription(m - 5, 1, m, E, Designs.carved(k, site));
        k.c.set(m, 1, m + 6, Blocks.BELL.getDefaultState().with(Properties.ATTACHMENT, net.minecraft.block.enums.Attachment.FLOOR));
        k.weather(0.4, false);
    }

    static void greatLibrary(Kit k, Site site) {
        int m = k.mid;
        int h = 10;
        k.shell(m - 12, m - 9, m + 12, m + 9, 0, h, true, 0.35);
        k.air(m - 1, 1, m + 9, m + 1, 4, m + 9);
        k.arch(m - 2, m + 2, m + 9, 1, true);
        for (int x = m - 10; x <= m + 10; x += 4) {
            for (int z : new int[]{m - 6, m - 3, m + 3, m + 6}) {
                for (int y = 1; y <= 4; y++) {
                    if (k.chance(0.85)) {
                        k.c.set(x, y, z, Blocks.BOOKSHELF);
                        k.c.set(x + 1, y, z, k.chance(0.3) ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF);
                    }
                }
            }
        }
        // gallery
        for (int x = m - 11; x <= m + 11; x++) {
            k.c.set(x, 5, m - 8, k.s.woodSlab);
            k.c.set(x, 5, m + 8, k.s.woodSlab);
            k.c.set(x, 6, m - 7, k.s.fence);
            k.c.set(x, 6, m + 7, k.s.fence);
        }
        k.stairs(m - 11, 1, m + 7, N, 5, 1, k.s.woodStairs, k.s.wood);
        k.c.set(m, 1, m, Blocks.LECTERN);
        k.table(m - 2, 1, m);
        k.table(m + 2, 1, m);
        k.chest(m + 11, 1, m - 8, W, "underdeep:exploration/library");
        k.barrelLoot(m - 11, 1, m - 8, "underdeep:exploration/library");
        k.inscription(m + 12, 2, m, W, Designs.carved(k, site));
        k.inscription(m - 12, 2, m, E, Designs.carved(k, site));
        for (int x = m - 8; x <= m + 8; x += 8) {
            k.lantern(x, h, m);
        }
        // the secret stacks: one bookshelf in the north wall is a door (a lectern faces it)
        k.room(m - 4, 0, m - 15, m + 4, 5, m - 9);
        k.c.set(m, 1, m - 9, Blocks.AIR.getDefaultState());
        k.c.set(m, 2, m - 9, Blocks.AIR.getDefaultState());
        k.c.lock(m, 1, m - 9);
        k.c.lock(m, 2, m - 9);
        k.c.set(m, 1, m - 8, Blocks.LECTERN.getDefaultState().with(Properties.HORIZONTAL_FACING, N));
        for (int x = m - 3; x <= m + 3; x += 2) {
            k.c.set(x, 1, m - 14, Blocks.CHISELED_BOOKSHELF);
            k.c.set(x, 2, m - 14, Blocks.BOOKSHELF);
        }
        k.coffer(m, 1, m - 13, S, "underdeep:exploration/library_secret");
        k.inscription(m + 3, 2, m - 12, W, Designs.loose(k, site));
        k.c.secret("library_stacks", m - 3, 1, m - 14, m + 3, 4, m - 10);
        k.weather(0.35, true);
    }

    static void amphitheatre(Kit k, Site site) {
        int m = k.mid;
        for (int t = 0; t < 6; t++) {
            double r = 5 + t * 2.4;
            for (int i = 0; i < (int) (r * 7); i++) {
                double a = Math.PI * (0.05 + 0.9 * i / (r * 7.0)) + Math.PI;
                int x = (int) Math.round(m + Math.cos(a) * r), z = (int) Math.round(m + 4 + Math.sin(a) * r);
                for (int y = 0; y < t + 1; y++) {
                    k.c.set(x, y, z, k.s.brick);
                }
                k.c.set(x, t + 1, z, Style.slab(k.s.brickSlab, false));
            }
        }
        k.disc(m, m + 4, 4.5, 0, k.s.floor, k.s.trim);
        k.pillar(m - 3, m + 7, 1, 6, true);
        k.pillar(m + 3, m + 7, 1, 6, true);
        for (int x = m - 3; x <= m + 3; x++) {
            k.c.set(x, 7, m + 7, k.s.trim);
        }
        k.inscription(m, 1, m + 7, N, Designs.carved(k, site));
        k.pot(m - 2, 1, m + 6, "underdeep:exploration/kiln_pot");
        k.weather(0.3, false);
    }

    static void foundryComplex(Kit k, Site site) {
        int m = k.mid;
        k.shell(m - 14, m - 8, m + 4, m + 8, 0, 10, true, 0.3);
        k.air(m + 4, 1, m - 1, m + 4, 3, m + 1);
        for (int i = 0; i < 3; i++) {
            int cx = m - 11 + i * 6;
            k.ring(cx, m - 3, 2, 1, 5, Blocks.BRICKS);
            k.c.set(cx, 1, m - 3, Blocks.MAGMA_BLOCK);
            for (int y = 6; y <= 12; y++) {
                k.c.set(cx, y, m - 3, Blocks.BRICKS);
            }
        }
        for (int x = m - 13; x <= m + 10; x++) {
            k.c.set(x, 1, m + 3, Blocks.RAIL.getDefaultState().with(Properties.RAIL_SHAPE, net.minecraft.block.enums.RailShape.EAST_WEST));
        }
        k.c.set(m - 4, 1, m + 6, Blocks.BLAST_FURNACE);
        k.c.set(m - 6, 1, m + 6, Blocks.BLAST_FURNACE);
        k.chest(m - 13, 1, m + 6, E, "underdeep:exploration/ruin_forge");
        k.barrelLoot(m + 2, 1, m + 6, "underdeep:exploration/miners");
        // the yard with ore carts
        k.floor(m + 5, m - 6, m + 13, m + 6, 0, Blocks.GRAVEL, k.s.floor);
        k.c.set(m + 8, 1, m - 3, Blocks.HOPPER);
        k.c.set(m + 8, 2, m - 3, Blocks.HOPPER);
        k.inscription(m - 14, 2, m, E, Designs.carved(k, site));
        for (int x = m - 12; x <= m + 2; x += 7) {
            k.lantern(x, 9, m + 4);
        }
        k.weather(0.35, true);
    }
}
