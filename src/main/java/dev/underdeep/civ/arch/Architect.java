package dev.underdeep.civ.arch;

import dev.underdeep.civ.Civilization;
import dev.underdeep.registry.ModPlants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Designs buildings: shared massing and furnished interiors per {@link BuildingKind}, dressed in a
 * civilization's shell (base, wall pattern, windows, roof, ornament) from {@link Shells}.
 * <p>
 * Conventions: footprint [0, w) x [0, d); floor blocks at y = 0; ground-floor walls from y = 1; the main door
 * is on the front wall (z = 0) and opens onto the road at y = 1. {@link Blueprint#markers()} record the
 * entrance (outside the door), beds, workstations, storage, merchant spots and seats for inhabitants.
 * Designs are cached per (civilization, kind, variant, profession).
 */
public final class Architect {
    private static final ConcurrentHashMap<String, Blueprint> CACHE = new ConcurrentHashMap<>();
    /** Number of design variants per kind (size/massing/roof/extension choices). */
    public static final int VARIANTS = 4;

    private Architect() {
    }

    public static Blueprint design(Civilization civ, BuildingKind kind, int variant, String profession) {
        String key = civ.id() + "/" + kind.id() + "/" + variant + "/" + profession;
        return CACHE.computeIfAbsent(key, k -> build(civ, kind, Math.floorMod(variant, VARIANTS), profession, k));
    }

    private static Blueprint build(Civilization civ, BuildingKind kind, int v, String prof, String key) {
        long seed = key.hashCode() * 0x9E3779B97F4A7C15L;
        Palette p = Palette.of(civ);
        Shells s = new Shells(civ, p);
        Blueprint bp = switch (kind) {
            case TENT -> tent(civ, p, v, seed);
            case CAMPFIRE -> campfire(p, v, seed);
            case HOUSE_SMALL -> house(s, p, 7 + (v & 1) * 2, 7 + (v >> 1) * 2, 1, v, seed, kind);
            case HOUSE -> house(s, p, 9 + (v & 1) * 2, 9, v >= 2 ? 2 : 1, v, seed, kind);
            case HOUSE_LARGE -> house(s, p, 11 + (v & 1) * 2, 11 + (v >> 1) * 2, 2, v, seed, kind);
            case SHARED_HOUSE -> house(s, p, 15, 11 + (v & 1) * 2, v >= 2 ? 3 : 2, v, seed, kind);
            case STALL -> stall(civ, p, v, seed);
            case SHOP -> shop(s, p, v, seed, prof);
            case INN -> inn(s, p, v, seed);
            case TRADE_HALL -> tradeHall(s, p, v, seed);
            case WORKSHOP -> workshop(s, p, v, seed, prof);
            case KITCHEN -> kitchen(s, p, v, seed);
            case STOREHOUSE -> storehouse(s, p, v, seed);
            case FARM -> farm(civ, s, p, v, seed);
            case BARN -> barn(s, p, v, seed);
            case PEN -> pen(p, v, seed);
            case HALL -> hall(s, p, v, seed);
            case LIBRARY -> library(s, p, v, seed);
            case TOWER -> tower(s, p, v, seed);
            case GUARD_POST -> guardPost(s, p, v, seed);
            case CLINIC -> clinic(s, p, v, seed);
            case LANDMARK -> Landmarks.design(civ, s, p, v, seed);
            case WELL -> well(civ, p, v, seed);
            case GATE -> gate(s, p, v, seed);
            case DOCK -> dock(p, v, seed);
        };
        bp.id = key;
        bp.kind = kind.id();
        if (bp.foundation == null) {
            bp.foundation = p.foundation.getDefaultState();
        }
        return bp;
    }

    // =================================================================== residential

    static Blueprint house(Shells s, Palette p, int w, int d, int floors, int v, long seed, BuildingKind kind) {
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        int fh = s.floorHeight();
        s.body(b, 0, 0, w - 1, d - 1, floors, v);
        int door = w / 2;
        s.entrance(b, door, 0, Direction.NORTH);
        // ground floor: living room and kitchen corner
        int y = 1;
        b.storage(1, y, d - 2, Direction.NORTH, true);
        b.set(w - 2, y, d - 2, b.facing(Blocks.SMOKER, Direction.NORTH));
        b.set(w - 3, y, d - 2, b.facing(Blocks.BARREL, Direction.UP));
        b.table(w / 2, y, d / 2 + 1);
        b.chair(w / 2 - 1, y, d / 2 + 1, Direction.EAST);
        b.chair(w / 2 + 1, y, d / 2 + 1, Direction.WEST);
        b.hangingLantern(w / 2, fh, d / 2, p.hangingLight);
        b.set(1, y, 1, b.pot(p.plantAlt));
        b.set(w / 2, y, d / 2 - 1, b.carpet(p.cloth));
        b.marker(Blueprint.MarkerType.GATHER, w / 2, y, d / 2, "home");
        int beds = kind.residents;
        if (floors == 1) {
            // beds along the side walls
            for (int i = 0; i < beds; i++) {
                int bx = i % 2 == 0 ? 1 : w - 2;
                int bz = 2 + (i / 2) * 2;
                if (bz >= d - 2) {
                    bz = d - 3;
                }
                if (b.empty(bx, y, bz) && b.empty(bx, y, bz + 1)) {
                    b.bed(bx, y, bz, Direction.SOUTH, i == 0 ? p.cloth : p.clothAlt);
                }
            }
            b.shelf(w - 2, y, 1, Direction.SOUTH);
        } else {
            s.ladder(b, 1, 1, 1, floors, Direction.SOUTH);
            for (int f = 1; f < floors; f++) {
                int fy = 1 + f * fh;
                int per = (beds + floors - 2) / Math.max(1, floors - 1);
                for (int i = 0; i < per; i++) {
                    int bx = i % 2 == 0 ? 3 + (i / 2) * 2 : w - 2;
                    int bz = i % 2 == 0 ? d - 3 : 2 + (i / 2) * 2;
                    if (bx < w - 1 && b.empty(bx, fy, bz) && b.empty(bx, fy, bz + (i % 2 == 0 ? 1 : 1))) {
                        b.bed(bx, fy, bz, Direction.SOUTH, i % 2 == 0 ? p.cloth : p.clothAlt);
                    }
                }
                b.shelf(w - 2, fy, d - 2, Direction.NORTH);
                b.storage(w - 2, fy, 1, Direction.WEST, false);
                b.hangingLantern(w / 2, fy + fh - 2, d / 2, p.hangingLight);
                b.set(w / 2, fy, d / 2, b.carpet(p.clothAlt));
            }
        }
        s.finish(b, 0, 0, w - 1, d - 1, floors, v);
        return bp;
    }

    static Blueprint tent(Civilization civ, Palette p, int v, long seed) {
        int w = 5 + (v & 1), d = 6;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        BlockState cloth = b.wool(v >= 2 ? p.clothAlt : p.cloth);
        b.floor(0, 0, w - 1, d - 1, 0, p.planks);
        // A-frame cloth tent along z
        int mid = (w - 1) / 2;
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int h = 3 - Math.abs(x - mid);
                for (int y = 1; y <= 3; y++) {
                    if (y == h + (w % 2 == 0 && x > mid ? 1 : 0)) {
                        if (z > 0 || x != mid) {
                            b.set(x, y, z, cloth);
                        }
                    } else if (y < h) {
                        b.soft(x, y, z, Builder.AIR);
                    }
                }
            }
        }
        b.set(mid, 4, 0, p.fence);
        b.set(mid, 4, d - 1, p.fence);
        b.bed(1, 1, 2, Direction.SOUTH, p.cloth);
        b.storage(w - 2, 1, d - 2, Direction.WEST, false);
        b.marker(Blueprint.MarkerType.ENTRANCE, mid, 1, -1, "");
        b.clear(mid, 1, 0, mid, 2, 0);
        return bp;
    }

    static Blueprint campfire(Palette p, int v, long seed) {
        Blueprint bp = new Blueprint(7, 7);
        Builder b = new Builder(bp, p, seed);
        b.disc(3, 3, 3, 0, p.path.getDefaultState());
        b.set(3, 0, 3, Blocks.COBBLESTONE);
        b.set(3, 1, 3, Blocks.CAMPFIRE);
        b.marker(Blueprint.MarkerType.GATHER, 3, 1, 3, "fire");
        for (Direction dir : Direction.Type.HORIZONTAL) {
            int x = 3 + dir.getOffsetX() * 2, z = 3 + dir.getOffsetZ() * 2;
            b.set(x, 1, z, b.axis(p.log, dir.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X));
            b.marker(Blueprint.MarkerType.SEAT, x, 1, z, "");
        }
        b.set(0, 1, 0, p.light);
        b.marker(Blueprint.MarkerType.ENTRANCE, 3, 1, -1, "");
        return bp;
    }

    // =================================================================== commercial

    static Blueprint stall(Civilization civ, Palette p, int v, long seed) {
        int w = 5, d = 4;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        b.floor(0, 0, w - 1, d - 1, 0, p.plaza);
        for (int x : new int[]{0, w - 1}) {
            for (int z : new int[]{0, d - 1}) {
                b.fill(x, 1, z, x, 2, z, p.fence);
            }
        }
        // striped cloth awning
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                b.set(x, 3, z, b.wool(x % 2 == 0 ? p.cloth : p.clothAlt));
            }
            b.set(x, 3, -1, b.carpet(x % 2 == 0 ? p.cloth : p.clothAlt));
        }
        b.counter(1, 1, w - 2, 1, 1);
        b.set(1, 2, 1, Blocks.DECORATED_POT);
        b.set(w - 2, 1, d - 1, Blocks.BARREL);
        b.marker(Blueprint.MarkerType.MERCHANT, w / 2, 1, 2, "stall");
        b.marker(Blueprint.MarkerType.ENTRANCE, w / 2, 1, -1, "");
        b.marker(Blueprint.MarkerType.STORAGE, w - 2, 1, d - 1, "barrel");
        b.hangingLantern(w / 2, 2, d - 1, p.hangingLight);
        return bp;
    }

    static Blueprint shop(Shells s, Palette p, int v, long seed, String prof) {
        int w = 9 + (v & 1) * 2, d = 9;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        s.body(b, 0, 0, w - 1, d - 1, v >= 2 ? 2 : 1, v);
        s.entrance(b, w / 2, 0, Direction.NORTH);
        s.awning(b, 1, w - 2, 0);
        b.counter(1, 4, w - 2, 4, 1);
        b.clear(w / 2, 1, 4, w / 2, 1, 4);
        b.set(w / 2, 1, 4, b.trapdoor(p.trapdoor, Direction.NORTH, false, true));
        b.marker(Blueprint.MarkerType.MERCHANT, w / 2 - 1, 1, 5, prof.isEmpty() ? "merchant" : prof);
        for (int x = 1; x < w - 1; x++) {
            b.shelf(x, 1, d - 2, Direction.NORTH);
            b.shelf(x, 2, d - 2, Direction.NORTH);
        }
        b.storage(1, 1, 6, Direction.EAST, true);
        b.set(1, 2, 4, Blocks.DECORATED_POT);
        b.set(w - 2, 2, 4, b.pot(p.plantAlt));
        b.hangingLantern(w / 2, s.floorHeight(), 3, p.hangingLight);
        b.hangingLantern(w / 2, s.floorHeight(), 6, p.hangingLight);
        if (v >= 2) {
            int fy = 1 + s.floorHeight();
            s.ladder(b, w - 2, 1, 1, 2, Direction.SOUTH);
            b.bed(1, fy, 2, Direction.SOUTH, p.cloth);
            b.storage(3, fy, d - 2, Direction.NORTH, true);
        }
        s.finish(b, 0, 0, w - 1, d - 1, v >= 2 ? 2 : 1, v);
        return bp;
    }

    static Blueprint inn(Shells s, Palette p, int v, long seed) {
        int w = 15, d = 13 + (v & 1) * 2;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        int fh = s.floorHeight();
        s.body(b, 0, 0, w - 1, d - 1, 2, v);
        s.entrance(b, w / 2, 0, Direction.NORTH);
        // tavern room: bar along the back, tables with chairs
        b.counter(2, d - 4, w - 3, d - 4, 1);
        b.marker(Blueprint.MarkerType.MERCHANT, w / 2, 1, d - 3, "innkeeper");
        for (int x = 2; x < w - 2; x += 2) {
            b.set(x, 1, d - 2, b.facing(Blocks.BARREL, Direction.NORTH));
            b.set(x + 1, 2, d - 2, Blocks.BREWING_STAND);
        }
        b.set(w - 2, 1, d - 2, b.facing(Blocks.SMOKER, Direction.NORTH));
        b.set(1, 1, d - 2, b.facing(Blocks.SMOKER, Direction.NORTH));
        b.marker(Blueprint.MarkerType.WORKSTATION, w - 2, 1, d - 2, "cook");
        for (int tx : new int[]{3, w - 4}) {
            for (int tz : new int[]{3, 6}) {
                b.table(tx, 1, tz);
                b.chair(tx - 1, 1, tz, Direction.EAST);
                b.chair(tx + 1, 1, tz, Direction.WEST);
                b.marker(Blueprint.MarkerType.GATHER, tx, 1, tz + 1, "tavern");
            }
        }
        b.hangingLantern(4, fh, 4, p.hangingLight);
        b.hangingLantern(w - 5, fh, 4, p.hangingLight);
        b.hangingLantern(w / 2, fh, d - 6, p.hangingLight);
        // guest rooms upstairs: corridor along x with rooms on both sides
        s.ladder(b, 1, 1, 1, 2, Direction.SOUTH);
        int fy = 1 + fh;
        for (int i = 0; i < 3; i++) {
            int x = 2 + i * 4;
            b.fill(x + 3, fy, 1, x + 3, fy + fh - 2, d / 2 - 1, p.planks);
            b.bed(x + 1, fy, 1, Direction.SOUTH, p.cloth);
            b.storage(x + 2, fy, 1, Direction.SOUTH, false);
            b.clear(x + 3, fy, d / 2 - 1, x + 3, fy + 1, d / 2 - 1);
        }
        b.fill(1, fy, d / 2, w - 2, fy, d / 2, b.carpet(p.cloth));
        for (int x = 3; x < w - 2; x += 4) {
            b.hangingLantern(x, fy + fh - 2, d / 2, p.hangingLight);
        }
        b.shelf(w - 2, fy, d - 2, Direction.WEST);
        b.storage(w - 3, fy, d - 2, Direction.NORTH, true);
        s.sign(b, w / 2 + 2, 2, -1, "inn");
        s.finish(b, 0, 0, w - 1, d - 1, 2, v);
        return bp;
    }

    static Blueprint tradeHall(Shells s, Palette p, int v, long seed) {
        int w = 17, d = 15 + (v & 1) * 2;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        s.body(b, 0, 0, w - 1, d - 1, 1, v, s.floorHeight() + 3);
        s.entrance(b, w / 2, 0, Direction.NORTH);
        s.entrance(b, w / 2, d - 1, Direction.SOUTH);
        // counters around the hall, merchants behind them, central display
        for (int i = 0; i < 4; i++) {
            int x = i < 2 ? 2 : w - 4;
            int z = 3 + (i % 2) * (d - 8);
            b.counter(x, z, x + 1, z + 2, 1);
            b.marker(Blueprint.MarkerType.MERCHANT, i < 2 ? 1 : w - 2, 1, z + 1, "merchant");
            b.storage(i < 2 ? 1 : w - 2, 1, z + 3 > d - 2 ? z - 1 : z + 3, i < 2 ? Direction.EAST : Direction.WEST, true);
        }
        b.fill(w / 2 - 1, 1, d / 2 - 1, w / 2 + 1, 1, d / 2 + 1, Blocks.BARREL);
        b.set(w / 2, 2, d / 2, p.accent);
        b.marker(Blueprint.MarkerType.GATHER, w / 2, 1, d / 2 - 3, "market");
        for (int x = 3; x < w - 2; x += 5) {
            for (int z = 3; z < d - 2; z += 5) {
                b.hangingLantern(x, s.floorHeight() + 2, z, p.hangingLight);
            }
        }
        s.finish(b, 0, 0, w - 1, d - 1, 1, v);
        return bp;
    }

    // =================================================================== production

    /** Workstation block and supplies for a profession. */
    static Block stationFor(String prof) {
        return switch (prof) {
            case "blacksmith", "smelter" -> Blocks.ANVIL;
            case "engineer" -> Blocks.SMITHING_TABLE;
            case "carpenter", "boatwright", "forester" -> Blocks.CRAFTING_TABLE;
            case "tailor" -> Blocks.LOOM;
            case "stonemason" -> Blocks.STONECUTTER;
            case "gemcutter", "artisan" -> Blocks.GRINDSTONE;
            case "alchemist", "healer" -> Blocks.BREWING_STAND;
            case "herbalist", "mycologist", "farmer" -> Blocks.COMPOSTER;
            case "cook", "baker" -> Blocks.SMOKER;
            case "brewer" -> Blocks.CAULDRON;
            case "fisher" -> Blocks.BARREL;
            case "cartographer", "navigator", "astronomer" -> Blocks.CARTOGRAPHY_TABLE;
            case "scholar" -> Blocks.LECTERN;
            case "hunter" -> Blocks.FLETCHING_TABLE;
            case "miner" -> Blocks.BLAST_FURNACE;
            case "lampwright" -> Blocks.FURNACE;
            default -> Blocks.CRAFTING_TABLE;
        };
    }

    /** Raw materials stacked in a workshop (shows what is made there). */
    static Block supplyFor(String prof, Palette p) {
        return switch (prof) {
            case "blacksmith", "smelter", "engineer", "miner" -> Blocks.RAW_IRON_BLOCK;
            case "carpenter", "boatwright", "forester" -> p.log;
            case "tailor" -> Blocks.WHITE_WOOL;
            case "stonemason" -> Blocks.COBBLESTONE;
            case "gemcutter", "artisan" -> Blocks.AMETHYST_BLOCK;
            case "alchemist", "healer", "herbalist", "mycologist" -> Blocks.MOSS_BLOCK;
            case "cook", "baker", "brewer" -> Blocks.HAY_BLOCK;
            case "fisher" -> Blocks.DRIED_KELP_BLOCK;
            case "lampwright" -> Blocks.GLOWSTONE;
            default -> Blocks.BARREL;
        };
    }

    static Blueprint workshop(Shells s, Palette p, int v, long seed, String prof) {
        int w = 9 + (v & 1) * 2, d = 11;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        s.body(b, 0, 0, w - 1, d - 1, 1, v, s.floorHeight() + 1);
        s.entrance(b, w / 2, 0, Direction.NORTH);
        Block station = stationFor(prof);
        b.workstation(w / 2, 1, d - 3, station, Direction.NORTH, prof);
        b.workstation(2, 1, d / 2, station == Blocks.ANVIL ? Blocks.SMITHING_TABLE : Blocks.CRAFTING_TABLE, Direction.EAST, prof);
        if (station == Blocks.ANVIL || prof.equals("smelter") || prof.equals("miner")) {
            b.set(w - 2, 1, d - 2, b.facing(Blocks.BLAST_FURNACE, Direction.WEST));
            b.set(w - 2, 1, d - 3, b.facing(Blocks.FURNACE, Direction.WEST));
            b.set(w - 2, 2, d - 2, b.facing(Blocks.FURNACE, Direction.WEST));
            b.set(w / 2 + 1, 1, d - 3, Blocks.WATER_CAULDRON.getDefaultState().with(Properties.LEVEL_3, 3));
        }
        Block supply = supplyFor(prof, p);
        b.set(1, 1, d - 2, supply);
        b.set(2, 1, d - 2, supply);
        b.set(1, 2, d - 2, supply);
        b.storage(1, 1, d - 3, Direction.EAST, true);
        b.storage(w - 2, 1, 2, Direction.WEST, false);
        b.shelf(w - 2, 1, 3, Direction.WEST);
        b.shelf(w - 2, 2, 3, Direction.WEST);
        b.hangingLantern(w / 2, s.floorHeight() + 1, d / 2, p.hangingLight);
        b.hangingLantern(w / 2, s.floorHeight() + 1, d - 3, p.hangingLight);
        s.sign(b, w / 2 + 2, 2, -1, prof);
        s.finish(b, 0, 0, w - 1, d - 1, 1, v);
        return bp;
    }

    static Blueprint kitchen(Shells s, Palette p, int v, long seed) {
        int w = 11, d = 9 + (v & 1) * 2;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        s.body(b, 0, 0, w - 1, d - 1, 1, v);
        s.entrance(b, w / 2, 0, Direction.NORTH);
        for (int x = 1; x < w - 1; x++) {
            b.set(x, 1, d - 2, x % 3 == 0 ? b.facing(Blocks.SMOKER, Direction.NORTH) : b.facing(Blocks.FURNACE, Direction.NORTH));
        }
        b.marker(Blueprint.MarkerType.WORKSTATION, w / 2, 1, d - 2, "baker");
        b.marker(Blueprint.MarkerType.WORKSTATION, 2, 1, d - 2, "cook");
        b.counter(2, d - 4, w - 3, d - 4, 1);
        b.set(w / 2, 2, d - 4, Blocks.CAKE);
        b.set(3, 2, d - 4, Blocks.DECORATED_POT);
        b.marker(Blueprint.MarkerType.MERCHANT, w / 2, 1, d - 3, "baker");
        b.set(1, 1, 1, Blocks.HAY_BLOCK);
        b.set(1, 2, 1, Blocks.HAY_BLOCK);
        b.storage(w - 2, 1, 1, Direction.WEST, false);
        b.set(w - 2, 1, 2, Blocks.WATER_CAULDRON.getDefaultState().with(Properties.LEVEL_3, 3));
        b.table(3, 1, 2);
        b.chair(4, 1, 2, Direction.WEST);
        b.hangingLantern(w / 2, s.floorHeight(), d / 2, p.hangingLight);
        s.sign(b, w / 2 + 2, 2, -1, "baker");
        s.finish(b, 0, 0, w - 1, d - 1, 1, v);
        return bp;
    }

    static Blueprint storehouse(Shells s, Palette p, int v, long seed) {
        int w = 11 + (v & 1) * 2, d = 9;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        s.body(b, 0, 0, w - 1, d - 1, 1, v, s.floorHeight() + 1);
        s.entrance(b, w / 2, 0, Direction.NORTH);
        b.clear(w / 2 - 1, 1, 0, w / 2 + 1, 3, 0); // wide loading door
        b.set(w / 2 - 1, 1, 0, Builder.AIR);
        for (int x = 1; x < w - 1; x++) {
            if (Math.abs(x - w / 2) <= 1) {
                continue;
            }
            for (int y = 1; y <= 3; y++) {
                int r = b.rnd.nextInt(4);
                b.set(x, y, d - 2, r == 0 ? Blocks.HAY_BLOCK.getDefaultState() : r == 1 ? Blocks.BARREL.getDefaultState()
                        : b.axis(p.log, Direction.Axis.X));
            }
            b.set(x, 1, d - 3, x % 2 == 0 ? Blocks.BARREL.getDefaultState() : Blocks.CHEST.getDefaultState()
                    .with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        }
        b.storage(1, 1, 1, Direction.EAST, true);
        b.marker(Blueprint.MarkerType.WORKSTATION, 2, 1, 2, "merchant");
        b.hangingLantern(w / 2, s.floorHeight() + 1, d / 2, p.hangingLight);
        s.finish(b, 0, 0, w - 1, d - 1, 1, v);
        return bp;
    }

    // =================================================================== agricultural

    static Block cropFor(Civilization c, int v) {
        return switch (c) {
            case ROOTFOLK -> v % 2 == 0 ? ModPlants.DUSKROOTS : ModPlants.GLOWGRAIN_CROP;
            case FORGEKIN -> ModPlants.EMBERPEPPERS;
            case TIDE_DWELLERS -> ModPlants.MIRE_RICE_CROP;
            case SPOREFOLK -> ModPlants.CAVE_TRUFFLES;
            case GLIMMERKIN -> ModPlants.GLOWGRAIN_CROP;
            case STONEBORN -> ModPlants.FROST_TURNIPS;
            case AMBERFOLK -> v % 2 == 0 ? ModPlants.SUNBEANS : ModPlants.GLOWGRAIN_CROP;
            case ASTRALS -> v % 2 == 0 ? ModPlants.GLOWGRAIN_CROP : ModPlants.SUNBEANS;
        };
    }

    static Blueprint farm(Civilization civ, Shells s, Palette p, int v, long seed) {
        int w = 13, d = 13 + (v & 1) * 2;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        Block crop = cropFor(civ, v);
        // fenced field with irrigation channel and a tool shed at the back
        b.floor(0, 0, w - 1, d - 1, 0, p.pathEdge);
        for (int x = 0; x < w; x++) {
            b.set(x, 1, 0, x == w / 2 ? Builder.AIR : p.fence.getDefaultState());
            b.set(x, 1, d - 1, p.fence);
        }
        for (int z = 0; z < d; z++) {
            b.set(0, 1, z, p.fence);
            b.set(w - 1, 1, z, p.fence);
        }
        b.set(w / 2, 1, 0, b.facing(p.fenceGate, Direction.NORTH));
        for (int x = 1; x < w - 1; x++) {
            for (int z = 2; z < d - 5; z++) {
                if (x == w / 2) {
                    b.set(x, 0, z, Blocks.WATER);
                    b.set(x, 1, z, Builder.AIR);
                    continue;
                }
                b.set(x, 0, z, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
                BlockState cs = crop.getDefaultState();
                if (crop instanceof CropBlock cb) {
                    cs = cb.withAge(Math.min(cb.getMaxAge(), 1 + b.rnd.nextInt(cb.getMaxAge())));
                }
                b.set(x, 1, z, cs);
            }
        }
        b.marker(Blueprint.MarkerType.FARM, w / 2 - 2, 1, d / 2 - 2, "farmer");
        b.marker(Blueprint.MarkerType.FARM, w / 2 + 2, 1, d / 2 - 1, "farmer");
        // shed
        int sz = d - 5;
        s.miniShed(b, 1, sz, w - 2, d - 2);
        b.workstation(2, 1, d - 3, Blocks.COMPOSTER, Direction.NORTH, "farmer");
        b.storage(w - 3, 1, d - 3, Direction.NORTH, true);
        b.set(w / 2, 1, d - 3, Blocks.HAY_BLOCK);
        b.lamp(0, 2, 0);
        b.lamp(w - 1, 2, 0);
        b.marker(Blueprint.MarkerType.ENTRANCE, w / 2, 1, -1, "");
        return bp;
    }

    static Blueprint barn(Shells s, Palette p, int v, long seed) {
        int w = 11 + (v & 1) * 2, d = 13;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        s.body(b, 0, 0, w - 1, d - 1, 1, v, s.floorHeight() + 2);
        s.entrance(b, w / 2, 0, Direction.NORTH);
        b.clear(w / 2 - 1, 1, 0, w / 2 + 1, 3, 0);
        for (int z = 2; z < d - 1; z += 3) {
            b.fill(1, 1, z, 3, 1, z, p.fence);
            b.fill(w - 4, 1, z, w - 2, 1, z, p.fence);
            b.set(1, 1, z + 1, Blocks.HAY_BLOCK);
            b.set(w - 2, 1, z + 1, Blocks.COMPOSTER);
        }
        b.fill(1, 1, d - 2, w - 2, 2, d - 2, Blocks.HAY_BLOCK);
        b.workstation(w / 2, 1, d - 3, Blocks.COMPOSTER, Direction.NORTH, "rancher");
        b.marker(Blueprint.MarkerType.FARM, w / 2, 1, d / 2, "rancher");
        b.bed(w - 2, 1, 1, Direction.SOUTH, p.clothAlt);
        b.hangingLantern(w / 2, s.floorHeight() + 2, d / 2, p.hangingLight);
        s.finish(b, 0, 0, w - 1, d - 1, 1, v);
        return bp;
    }

    static Blueprint pen(Palette p, int v, long seed) {
        int w = 11 + (v & 1) * 2, d = 11;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        b.floor(0, 0, w - 1, d - 1, 0, Blocks.COARSE_DIRT);
        for (int x = 0; x < w; x++) {
            b.set(x, 1, 0, p.fence);
            b.set(x, 1, d - 1, p.fence);
        }
        for (int z = 0; z < d; z++) {
            b.set(0, 1, z, p.fence);
            b.set(w - 1, 1, z, p.fence);
        }
        b.set(w / 2, 1, 0, b.facing(p.fenceGate, Direction.NORTH));
        b.set(2, 1, d - 2, Blocks.HAY_BLOCK);
        b.set(3, 1, d - 2, Blocks.HAY_BLOCK);
        b.set(w - 3, 1, d - 2, Blocks.WATER_CAULDRON.getDefaultState().with(Properties.LEVEL_3, 3));
        b.set(0, 2, 0, p.light);
        b.set(w - 1, 2, 0, p.light);
        b.marker(Blueprint.MarkerType.FARM, w / 2, 1, d / 2, "rancher");
        b.marker(Blueprint.MarkerType.ENTRANCE, w / 2, 1, -1, "");
        return bp;
    }

    // =================================================================== civic

    static Blueprint hall(Shells s, Palette p, int v, long seed) {
        int w = 13 + (v & 1) * 2, d = 19;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        int h = s.floorHeight() + 4;
        s.body(b, 0, 0, w - 1, d - 1, 1, v, h);
        s.entrance(b, w / 2, 0, Direction.NORTH);
        // two long tables, a dais with lectern, banners on the walls
        for (int z = 4; z < d - 5; z++) {
            b.set(w / 2 - 2, 1, z, p.fence);
            b.set(w / 2 - 2, 2, z, b.slab(p.planksSlab, false));
            b.set(w / 2 + 2, 1, z, p.fence);
            b.set(w / 2 + 2, 2, z, b.slab(p.planksSlab, false));
            if (z % 2 == 0) {
                b.chair(w / 2 - 3, 1, z, Direction.EAST);
                b.chair(w / 2 + 3, 1, z, Direction.WEST);
            }
        }
        b.fill(1, 1, d - 4, w - 2, 1, d - 2, b.slab(p.trimSlab, true));
        b.set(w / 2, 2, d - 3, b.facing(Blocks.LECTERN, Direction.NORTH));
        b.marker(Blueprint.MarkerType.WORKSTATION, w / 2, 2, d - 2, "elder");
        b.marker(Blueprint.MarkerType.GATHER, w / 2, 1, d / 2, "hall");
        for (int z = 3; z < d - 2; z += 4) {
            b.set(1, 3, z, b.banner(p.cloth, Direction.EAST));
            b.set(w - 2, 3, z, b.banner(p.cloth, Direction.WEST));
            b.hangingLantern(w / 2, h - 1, z, p.hangingLight);
        }
        s.finish(b, 0, 0, w - 1, d - 1, 1, v);
        return bp;
    }

    static Blueprint library(Shells s, Palette p, int v, long seed) {
        int w = 13, d = 13 + (v & 1) * 2;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        int fh = s.floorHeight();
        s.body(b, 0, 0, w - 1, d - 1, 2, v);
        s.entrance(b, w / 2, 0, Direction.NORTH);
        for (int z = 2; z < d - 1; z++) {
            for (int y = 1; y < fh; y++) {
                if (z != d / 2) {
                    b.set(1, y, z, Blocks.BOOKSHELF);
                    b.set(w - 2, y, z, Blocks.BOOKSHELF);
                }
            }
        }
        for (int x = 3; x <= w - 4; x += 3) {
            b.fill(x, 1, 4, x, 2, d - 4, Blocks.BOOKSHELF);
        }
        b.set(w / 2, 1, d - 2, b.facing(Blocks.LECTERN, Direction.NORTH));
        b.workstation(2, 1, 1, Blocks.LECTERN, Direction.SOUTH, "scholar");
        b.workstation(w - 3, 1, 1, Blocks.CARTOGRAPHY_TABLE, Direction.SOUTH, "cartographer");
        b.table(w / 2, 1, 2);
        b.chair(w / 2 - 1, 1, 2, Direction.EAST);
        s.ladder(b, w - 2, 1, d / 2, 2, Direction.WEST);
        int fy = 1 + fh;
        for (int x = 2; x < w - 2; x += 2) {
            b.set(x, fy, d - 2, Blocks.BOOKSHELF);
            b.set(x, fy + 1, d - 2, Blocks.CHISELED_BOOKSHELF.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        }
        b.table(w / 2, fy, d / 2);
        b.chair(w / 2 + 1, fy, d / 2, Direction.WEST);
        b.hangingLantern(w / 2, fh, d / 2, p.hangingLight);
        b.hangingLantern(w / 2, fy + fh - 2, d / 2 - 2, p.hangingLight);
        s.finish(b, 0, 0, w - 1, d - 1, 2, v);
        return bp;
    }

    static Blueprint tower(Shells s, Palette p, int v, long seed) {
        int w = 7, d = 7;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        int floors = 3 + (v & 1);
        s.body(b, 0, 0, w - 1, d - 1, floors, v);
        s.entrance(b, w / 2, 0, Direction.NORTH);
        s.ladder(b, w / 2, d - 2, 1, floors + 1, Direction.NORTH);
        int top = 1 + floors * s.floorHeight();
        // lookout floor over the shaft (ladder hole), then an open platform with railing
        b.fill(1, top - 1, 1, w - 2, top - 1, d - 2, p.floor);
        b.set(w / 2, top - 1, d - 2, b.facing(Blocks.LADDER, Direction.NORTH));
        b.set(w / 2, top - 2, d - 2, b.facing(Blocks.LADDER, Direction.NORTH));
        for (int x = -1; x <= w; x++) {
            for (int z = -1; z <= d; z++) {
                boolean edge = x == -1 || z == -1 || x == w || z == d;
                if (edge) {
                    b.set(x, top, z, p.foundationWall != null ? p.foundationWall : p.fence);
                    b.set(x, top - 1, z, b.slab(p.trimSlab, true));
                }
            }
        }
        b.lamp(-1, top + 1, -1);
        b.lamp(w, top + 1, d);
        b.marker(Blueprint.MarkerType.GUARD, w / 2, top, d / 2, "lookout");
        s.towerCap(b, w, d, top + 2, v);
        return bp;
    }

    static Blueprint guardPost(Shells s, Palette p, int v, long seed) {
        int w = 9, d = 9;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        s.body(b, 0, 0, w - 1, d - 1, 1, v);
        s.entrance(b, w / 2, 0, Direction.NORTH);
        b.bed(1, 1, d - 3, Direction.SOUTH, p.cloth);
        b.bed(w - 2, 1, d - 3, Direction.SOUTH, p.cloth);
        b.storage(w / 2, 1, d - 2, Direction.NORTH, true);
        b.workstation(1, 1, 2, Blocks.SMITHING_TABLE, Direction.EAST, "guard");
        b.set(w - 2, 1, 2, b.facing(Blocks.LECTERN, Direction.WEST));
        b.set(w - 2, 2, 1, b.banner(p.cloth, Direction.WEST));
        b.marker(Blueprint.MarkerType.GUARD, w / 2, 1, -2, "post");
        b.hangingLantern(w / 2, s.floorHeight(), d / 2, p.hangingLight);
        s.finish(b, 0, 0, w - 1, d - 1, 1, v);
        return bp;
    }

    static Blueprint clinic(Shells s, Palette p, int v, long seed) {
        int w = 11, d = 11;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        s.body(b, 0, 0, w - 1, d - 1, 1, v);
        s.entrance(b, w / 2, 0, Direction.NORTH);
        for (int i = 0; i < 3; i++) {
            b.bed(1 + i * 3, 1, d - 3, Direction.SOUTH, net.minecraft.util.DyeColor.WHITE);
        }
        b.workstation(w - 2, 1, 2, Blocks.BREWING_STAND, Direction.WEST, "healer");
        b.set(w - 2, 1, 3, Blocks.WATER_CAULDRON.getDefaultState().with(Properties.LEVEL_3, 3));
        for (int z = 1; z < 5; z++) {
            b.shelf(1, 2, z, Direction.EAST);
            b.set(1, 1, z, z % 2 == 0 ? b.pot(p.plantAlt) : Blocks.BARREL.getDefaultState());
        }
        b.counter(3, 3, w - 4, 3, 1);
        b.marker(Blueprint.MarkerType.MERCHANT, w / 2, 1, 4, "healer");
        b.hangingLantern(w / 2, s.floorHeight(), d / 2, p.hangingLight);
        s.sign(b, w / 2 + 2, 2, -1, "healer");
        s.finish(b, 0, 0, w - 1, d - 1, 1, v);
        return bp;
    }

    // =================================================================== infrastructure

    static Blueprint well(Civilization civ, Palette p, int v, long seed) {
        Blueprint bp = new Blueprint(7, 7);
        Builder b = new Builder(bp, p, seed);
        b.disc(3, 3, 3.2, 0, p.plaza.getDefaultState());
        b.fill(2, 0, 2, 4, 1, 4, p.foundation);
        b.fill(3, -2, 3, 3, 1, 3, Blocks.WATER);
        b.set(3, 1, 3, Blocks.WATER);
        boolean fountain = civ == Civilization.AMBERFOLK || civ == Civilization.ASTRALS || civ == Civilization.GLIMMERKIN || (v & 1) == 1;
        if (fountain) {
            b.fill(2, 1, 2, 4, 1, 4, b.slab(p.foundationSlab, false));
            b.set(3, 1, 3, Blocks.WATER);
            b.set(3, 2, 3, p.foundationWall);
            b.set(3, 3, 3, p.glow);
        } else {
            for (int[] c : new int[][]{{2, 2}, {4, 2}, {2, 4}, {4, 4}}) {
                b.fill(c[0], 2, c[1], c[0], 3, c[1], p.fence);
            }
            b.fill(2, 4, 2, 4, 4, 4, b.slab(p.roofSlab != null ? p.roofSlab : p.planksSlab, false));
            b.set(3, 3, 3, p.ornament);
            b.hangingLantern(3, 2, 3, p.hangingLight);
            b.set(3, 1, 3, Blocks.WATER);
            for (int[] c : new int[][]{{3, 2}, {3, 4}, {2, 3}, {4, 3}}) {
                b.set(c[0], 1, c[1], b.slab(p.foundationSlab, false));
            }
        }
        b.marker(Blueprint.MarkerType.GATHER, 3, 1, 0, "well");
        b.marker(Blueprint.MarkerType.ENTRANCE, 3, 1, -1, "");
        return bp;
    }

    static Blueprint gate(Shells s, Palette p, int v, long seed) {
        int w = 11, d = 5;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        int h = 7;
        b.floor(0, 0, w - 1, d - 1, 0, p.path);
        for (int x : new int[]{0, 1, w - 2, w - 1}) {
            b.fill(x, 1, 0, x, h, d - 1, p.foundation);
        }
        b.fill(2, h - 2, 0, w - 3, h, d - 1, p.wall);
        b.clear(2, 1, 0, w - 3, h - 3, d - 1);
        for (int z = 0; z < d; z++) {
            b.set(2, h - 3, z, b.stairs(p.foundationStairs, Direction.EAST, true));
            b.set(w - 3, h - 3, z, b.stairs(p.foundationStairs, Direction.WEST, true));
        }
        for (int x = -1; x <= w; x++) {
            b.set(x, h + 1, 0, x % 2 == 0 ? p.foundationWall.getDefaultState() : Builder.AIR);
            b.set(x, h + 1, d - 1, x % 2 == 0 ? p.foundationWall.getDefaultState() : Builder.AIR);
        }
        b.lamp(0, h + 1, 2);
        b.lamp(w - 1, h + 1, 2);
        b.set(w / 2, h - 1, 0, b.banner(p.cloth, Direction.NORTH));
        b.set(w / 2, h - 1, d - 1, b.banner(p.cloth, Direction.SOUTH));
        b.marker(Blueprint.MarkerType.GUARD, 2, 1, -1, "gate");
        b.marker(Blueprint.MarkerType.GUARD, w - 3, 1, d, "gate");
        b.marker(Blueprint.MarkerType.ENTRANCE, w / 2, 1, -1, "");
        return bp;
    }

    static Blueprint dock(Palette p, int v, long seed) {
        int w = 5, d = 15 + (v & 1) * 4;
        Blueprint bp = new Blueprint(w, d);
        Builder b = new Builder(bp, p, seed);
        bp.stilts = true;
        // deck at y = 0, posts every 4 blocks (extended to the bed by the placer), railings, mooring lights
        for (int z = 0; z < d; z++) {
            for (int x = 1; x < w - 1; x++) {
                b.set(x, 0, z, p.planks);
            }
            if (z % 4 == 0) {
                b.set(0, 0, z, p.log);
                b.set(w - 1, 0, z, p.log);
                b.set(0, -1, z, p.log);
                b.set(w - 1, -1, z, p.log);
                b.set(0, 1, z, p.fence);
                b.set(w - 1, 1, z, p.fence);
                b.set(0, 2, z, z % 8 == 0 ? p.light.getDefaultState() : p.fence.getDefaultState());
            } else {
                b.set(0, 0, z, b.slab(p.planksSlab, true));
                b.set(w - 1, 0, z, b.slab(p.planksSlab, true));
            }
        }
        b.set(1, 1, d - 2, Blocks.BARREL);
        b.set(3, 1, d - 2, b.facing(Blocks.BARREL, Direction.UP));
        b.marker(Blueprint.MarkerType.DOCK, w / 2, 1, d - 3, "fisher");
        b.marker(Blueprint.MarkerType.WORKSTATION, 1, 1, d - 3, "fisher");
        b.marker(Blueprint.MarkerType.ENTRANCE, w / 2, 1, -1, "");
        return bp;
    }

    /** Ladder column helper used by shells. */
    static BlockState ladder(Direction facing) {
        return Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, facing);
    }
}
