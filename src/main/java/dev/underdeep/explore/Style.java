package dev.underdeep.explore;

import dev.underdeep.registry.ModBlocks;
import dev.underdeep.registry.ModPlants;
import dev.underdeep.registry.ModWood;
import dev.underdeep.world.region.RegionType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Architectural vocabulary of one historical culture (the old builders of the underground; identities are private
 * design material) plus the overgrowth of the region a ruin stands in. Builders only ask for roles (brick, pillar,
 * trim, light...), so the same authored design reads differently per culture and region.
 */
public final class Style {
    public enum Culture { WRIGHT, LANTERN, TIDE, EMBER, GARDENER, STAR, SILENT }

    public final Culture culture;
    public Block brick, brickStairs, brickSlab, brickWall, cracked, block, chiseled, pillar, floor, floorAlt, trim, trimStairs, trimSlab;
    public Block metal, bars, light, hangingLight, glow, accent, wood, woodSlab, woodStairs, fence, door, trapdoor, glass;
    /** Weathered replacements (brick → cracked/mossy...). */
    public final Map<Block, Block> weathered = new HashMap<>();
    /** Overgrowth for the region (ground cover, wall growth, small plants, hanging plants). */
    public Block moss, mossCarpet, vine, rubble;
    public List<Block> plants = List.of();
    public boolean wet, frozen, hot;

    private Style(Culture c) {
        this.culture = c;
    }

    public static Style of(Culture c, RegionType region) {
        Style s = new Style(c);
        switch (c) {
            case WRIGHT -> {
                s.brick = Blocks.DEEPSLATE_TILES;
                s.brickStairs = Blocks.DEEPSLATE_TILE_STAIRS;
                s.brickSlab = Blocks.DEEPSLATE_TILE_SLAB;
                s.brickWall = Blocks.DEEPSLATE_TILE_WALL;
                s.cracked = Blocks.CRACKED_DEEPSLATE_TILES;
                s.block = Blocks.POLISHED_DEEPSLATE;
                s.chiseled = Blocks.CHISELED_DEEPSLATE;
                s.pillar = Blocks.DEEPSLATE;
                s.floor = Blocks.POLISHED_TUFF;
                s.floorAlt = Blocks.TUFF_BRICKS;
                s.trim = Blocks.TUFF_BRICKS;
                s.trimStairs = Blocks.TUFF_BRICK_STAIRS;
                s.trimSlab = Blocks.TUFF_BRICK_SLAB;
                s.metal = Blocks.CHISELED_TUFF_BRICKS;
                s.bars = Blocks.IRON_BARS;
                s.light = Blocks.SOUL_LANTERN;
                s.hangingLight = Blocks.SOUL_LANTERN;
                s.glow = ModBlocks.LUMINITE_BLOCK;
                s.accent = Blocks.CHISELED_TUFF;
                wood(s, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_TRAPDOOR);
                s.glass = Blocks.TINTED_GLASS;
                s.weathered.put(Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES);
                s.weathered.put(Blocks.POLISHED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE);
                s.weathered.put(Blocks.TUFF_BRICKS, Blocks.TUFF);
            }
            case LANTERN -> {
                s.brick = Blocks.STONE_BRICKS;
                s.brickStairs = Blocks.STONE_BRICK_STAIRS;
                s.brickSlab = Blocks.STONE_BRICK_SLAB;
                s.brickWall = Blocks.STONE_BRICK_WALL;
                s.cracked = Blocks.CRACKED_STONE_BRICKS;
                s.block = Blocks.SMOOTH_STONE;
                s.chiseled = Blocks.CHISELED_STONE_BRICKS;
                s.pillar = Blocks.STONE;
                s.floor = Blocks.WEATHERED_CUT_COPPER;
                s.floorAlt = Blocks.STONE_BRICKS;
                s.trim = Blocks.OXIDIZED_CUT_COPPER;
                s.trimStairs = Blocks.OXIDIZED_CUT_COPPER_STAIRS;
                s.trimSlab = Blocks.OXIDIZED_CUT_COPPER_SLAB;
                s.metal = Blocks.OXIDIZED_COPPER_GRATE;
                s.bars = Blocks.CHAIN;
                s.light = Blocks.LANTERN;
                s.hangingLight = Blocks.LANTERN;
                s.glow = Blocks.OXIDIZED_COPPER_BULB;
                s.accent = Blocks.OXIDIZED_CHISELED_COPPER;
                wood(s, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_FENCE, Blocks.OXIDIZED_COPPER_DOOR, Blocks.OXIDIZED_COPPER_TRAPDOOR);
                s.glass = Blocks.GLASS;
                s.weathered.put(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS);
                s.weathered.put(Blocks.WEATHERED_CUT_COPPER, Blocks.OXIDIZED_CUT_COPPER);
            }
            case TIDE -> {
                s.brick = Blocks.PRISMARINE_BRICKS;
                s.brickStairs = Blocks.PRISMARINE_BRICK_STAIRS;
                s.brickSlab = Blocks.PRISMARINE_BRICK_SLAB;
                s.brickWall = Blocks.PRISMARINE_WALL;
                s.cracked = Blocks.PRISMARINE;
                s.block = Blocks.DARK_PRISMARINE;
                s.chiseled = Blocks.DARK_PRISMARINE;
                s.pillar = Blocks.PRISMARINE;
                s.floor = Blocks.DARK_PRISMARINE;
                s.floorAlt = Blocks.PRISMARINE_BRICKS;
                s.trim = Blocks.QUARTZ_BRICKS;
                s.trimStairs = Blocks.QUARTZ_STAIRS;
                s.trimSlab = Blocks.QUARTZ_SLAB;
                s.metal = Blocks.SEA_LANTERN;
                s.bars = Blocks.IRON_BARS;
                s.light = Blocks.SEA_LANTERN;
                s.hangingLight = Blocks.LANTERN;
                s.glow = Blocks.SEA_LANTERN;
                s.accent = Blocks.QUARTZ_PILLAR;
                wood(s, ModWood.MIREWILLOW.planks, ModWood.MIREWILLOW.slab, ModWood.MIREWILLOW.stairs, ModWood.MIREWILLOW.fence, ModWood.MIREWILLOW.door,
                        ModWood.MIREWILLOW.trapdoor);
                s.glass = Blocks.LIGHT_BLUE_STAINED_GLASS;
                s.weathered.put(Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE);
            }
            case EMBER -> {
                s.brick = Blocks.POLISHED_BLACKSTONE_BRICKS;
                s.brickStairs = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS;
                s.brickSlab = Blocks.POLISHED_BLACKSTONE_BRICK_SLAB;
                s.brickWall = Blocks.POLISHED_BLACKSTONE_BRICK_WALL;
                s.cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS;
                s.block = Blocks.POLISHED_BLACKSTONE;
                s.chiseled = Blocks.CHISELED_POLISHED_BLACKSTONE;
                s.pillar = Blocks.BASALT;
                s.floor = Blocks.POLISHED_BASALT;
                s.floorAlt = Blocks.SMOOTH_BASALT;
                s.trim = Blocks.CUT_COPPER;
                s.trimStairs = Blocks.CUT_COPPER_STAIRS;
                s.trimSlab = Blocks.CUT_COPPER_SLAB;
                s.metal = Blocks.EXPOSED_COPPER_GRATE;
                s.bars = Blocks.IRON_BARS;
                s.light = Blocks.LANTERN;
                s.hangingLight = Blocks.LANTERN;
                s.glow = Blocks.MAGMA_BLOCK;
                s.accent = Blocks.GILDED_BLACKSTONE;
                wood(s, ModWood.BLOODTHORN.planks, ModWood.BLOODTHORN.slab, ModWood.BLOODTHORN.stairs, ModWood.BLOODTHORN.fence, Blocks.IRON_DOOR,
                        Blocks.IRON_TRAPDOOR);
                s.glass = Blocks.ORANGE_STAINED_GLASS;
                s.weathered.put(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);
                s.weathered.put(Blocks.POLISHED_BLACKSTONE, Blocks.BLACKSTONE);
            }
            case GARDENER -> {
                s.brick = Blocks.MUD_BRICKS;
                s.brickStairs = Blocks.MUD_BRICK_STAIRS;
                s.brickSlab = Blocks.MUD_BRICK_SLAB;
                s.brickWall = Blocks.MUD_BRICK_WALL;
                s.cracked = Blocks.PACKED_MUD;
                s.block = Blocks.PACKED_MUD;
                s.chiseled = ModBlocks.AMBER_RESIN;
                s.pillar = ModWood.GOLDWOOD.log;
                s.floor = Blocks.MOSSY_STONE_BRICKS;
                s.floorAlt = Blocks.MOSS_BLOCK;
                s.trim = ModWood.GOLDWOOD.planks;
                s.trimStairs = ModWood.GOLDWOOD.stairs;
                s.trimSlab = ModWood.GOLDWOOD.slab;
                s.metal = Blocks.HONEYCOMB_BLOCK;
                s.bars = ModWood.GOLDWOOD.fence;
                s.light = Blocks.LANTERN;
                s.hangingLight = Blocks.LANTERN;
                s.glow = Blocks.SHROOMLIGHT;
                s.accent = ModBlocks.AMBER_RESIN;
                wood(s, ModWood.GOLDWOOD.planks, ModWood.GOLDWOOD.slab, ModWood.GOLDWOOD.stairs, ModWood.GOLDWOOD.fence, ModWood.GOLDWOOD.door,
                        ModWood.GOLDWOOD.trapdoor);
                s.glass = Blocks.YELLOW_STAINED_GLASS;
                s.weathered.put(Blocks.MUD_BRICKS, Blocks.PACKED_MUD);
                s.weathered.put(Blocks.MOSSY_STONE_BRICKS, Blocks.MOSS_BLOCK);
            }
            case STAR -> {
                s.brick = Blocks.QUARTZ_BRICKS;
                s.brickStairs = Blocks.QUARTZ_STAIRS;
                s.brickSlab = Blocks.QUARTZ_SLAB;
                s.brickWall = Blocks.DIORITE_WALL;
                s.cracked = Blocks.CALCITE;
                s.block = Blocks.CALCITE;
                s.chiseled = Blocks.CHISELED_QUARTZ_BLOCK;
                s.pillar = Blocks.QUARTZ_PILLAR;
                s.floor = Blocks.SMOOTH_QUARTZ;
                s.floorAlt = ModBlocks.STARSTONE;
                s.trim = Blocks.POLISHED_DIORITE;
                s.trimStairs = Blocks.POLISHED_DIORITE_STAIRS;
                s.trimSlab = Blocks.POLISHED_DIORITE_SLAB;
                s.metal = Blocks.GOLD_BLOCK;
                s.bars = Blocks.CHAIN;
                s.light = Blocks.SOUL_LANTERN;
                s.hangingLight = Blocks.LANTERN;
                s.glow = ModBlocks.STARSTONE;
                s.accent = Blocks.AMETHYST_BLOCK;
                wood(s, ModWood.LUMENWOOD.planks, ModWood.LUMENWOOD.slab, ModWood.LUMENWOOD.stairs, ModWood.LUMENWOOD.fence, ModWood.LUMENWOOD.door,
                        ModWood.LUMENWOOD.trapdoor);
                s.glass = Blocks.PURPLE_STAINED_GLASS;
                s.weathered.put(Blocks.QUARTZ_BRICKS, Blocks.CALCITE);
                s.weathered.put(Blocks.SMOOTH_QUARTZ, Blocks.DIORITE);
            }
            case SILENT -> {
                s.brick = Blocks.DEEPSLATE_BRICKS;
                s.brickStairs = Blocks.DEEPSLATE_BRICK_STAIRS;
                s.brickSlab = Blocks.DEEPSLATE_BRICK_SLAB;
                s.brickWall = Blocks.DEEPSLATE_BRICK_WALL;
                s.cracked = Blocks.CRACKED_DEEPSLATE_BRICKS;
                s.block = Blocks.POLISHED_DEEPSLATE;
                s.chiseled = Blocks.CHISELED_DEEPSLATE;
                s.pillar = Blocks.BASALT;
                s.floor = Blocks.DEEPSLATE_TILES;
                s.floorAlt = Blocks.SCULK;
                s.trim = Blocks.POLISHED_BASALT;
                s.trimStairs = Blocks.DEEPSLATE_TILE_STAIRS;
                s.trimSlab = Blocks.DEEPSLATE_TILE_SLAB;
                s.metal = Blocks.SCULK_CATALYST;
                s.bars = Blocks.CHAIN;
                s.light = Blocks.SOUL_LANTERN;
                s.hangingLight = Blocks.SOUL_LANTERN;
                s.glow = Blocks.SOUL_CAMPFIRE;
                s.accent = Blocks.SCULK;
                wood(s, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_TRAPDOOR);
                s.glass = Blocks.TINTED_GLASS;
                s.weathered.put(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS);
                s.weathered.put(Blocks.DEEPSLATE_TILES, Blocks.SCULK);
            }
        }
        flora(s, region);
        return s;
    }

    private static void wood(Style s, Block planks, Block slab, Block stairs, Block fence, Block door, Block trapdoor) {
        s.wood = planks;
        s.woodSlab = slab;
        s.woodStairs = stairs;
        s.fence = fence;
        s.door = door;
        s.trapdoor = trapdoor;
    }

    private static void flora(Style s, RegionType r) {
        s.rubble = Blocks.COBBLESTONE;
        s.moss = Blocks.MOSS_BLOCK;
        s.mossCarpet = Blocks.MOSS_CARPET;
        s.vine = Blocks.VINE;
        switch (r) {
            case ROOTWILD -> {
                s.mossCarpet = Blocks.MOSS_CARPET;
                s.plants = List.of(ModPlants.GLOWFERN, Blocks.FERN, ModPlants.MENDLEAF, Blocks.SHORT_GRASS);
                s.vine = ModPlants.GLOWIVY;
            }
            case FUNGAL_EXPANSE -> {
                s.moss = ModBlocks.SPOREGLOW_SOIL;
                s.mossCarpet = Blocks.MOSS_CARPET;
                s.plants = List.of(ModBlocks.GLOWCAP, ModPlants.PUFFCAP, ModPlants.INKCAP, ModPlants.LANTERNCAP);
                s.vine = Blocks.VINE;
            }
            case FLOODED_DEPTHS, SUNKEN_GARDENS -> {
                s.wet = true;
                s.moss = Blocks.MOSS_BLOCK;
                s.plants = List.of(ModBlocks.GLOWREED, Blocks.FERN, ModPlants.TIDECAP);
                s.vine = Blocks.VINE;
            }
            case ASHEN_HOLLOWS, CRIMSON_BASIN -> {
                s.hot = true;
                s.moss = r == RegionType.CRIMSON_BASIN ? Blocks.CRIMSON_NYLIUM : ModBlocks.EMBER_NYLIUM;
                s.mossCarpet = null;
                s.rubble = Blocks.BLACKSTONE;
                s.plants = r == RegionType.CRIMSON_BASIN ? List.of(Blocks.CRIMSON_ROOTS, ModPlants.THORNBRIAR) : List.of(ModPlants.ASHGRASS, ModPlants.PYRELILY);
                s.vine = Blocks.WEEPING_VINES;
            }
            case CRYSTAL_DEEP -> {
                s.moss = ModBlocks.GLOWMOSS;
                s.mossCarpet = null;
                s.rubble = Blocks.CALCITE;
                s.plants = List.of(ModBlocks.LUMINITE_CLUSTER, ModPlants.SHARDGRASS, ModPlants.PRISM_LILY);
            }
            case THE_ABYSS -> {
                s.moss = Blocks.SCULK;
                s.mossCarpet = Blocks.SCULK_VEIN;
                s.rubble = Blocks.COBBLED_DEEPSLATE;
                s.plants = List.of(ModPlants.GHOSTFERN, ModPlants.VOIDROOT);
                s.vine = Blocks.SCULK_VEIN;
            }
            case EVERFROST -> {
                s.frozen = true;
                s.moss = Blocks.SNOW_BLOCK;
                s.mossCarpet = Blocks.SNOW;
                s.rubble = Blocks.PACKED_ICE;
                s.plants = List.of(ModPlants.FROSTBELL, ModPlants.RIMECAP);
                s.vine = null;
            }
            case GOLDEN_GROVE -> {
                s.moss = ModBlocks.AMBER_MOSS;
                s.plants = List.of(ModPlants.GOLDGRASS, ModPlants.SUNLEAF, ModBlocks.AMBERBLOOM);
                s.vine = ModPlants.GLOWIVY;
            }
            case HOLLOW_PEAKS -> {
                s.rubble = Blocks.GRAVEL;
                s.plants = List.of(ModPlants.PEAKSTAR, ModPlants.STONEROOT);
                s.vine = ModPlants.LEDGEVINE;
            }
            case EMERALD_WETLANDS -> {
                s.wet = true;
                s.moss = Blocks.MUD;
                s.plants = List.of(ModPlants.MARSH_FERN, ModPlants.BOGBLOOM, Blocks.SHORT_GRASS);
                s.vine = Blocks.VINE;
            }
            case PETRIFIED_FOREST -> {
                s.rubble = Blocks.GRAVEL;
                s.moss = Blocks.MOSS_BLOCK;
                s.plants = List.of(ModPlants.PETROLICHEN, Blocks.DEAD_BUSH);
                s.vine = null;
            }
            case STORMSTONE_CAVERNS -> {
                s.rubble = ModBlocks.STORMSTONE;
                s.moss = ModBlocks.STORMSTONE;
                s.mossCarpet = null;
                s.plants = List.of(ModPlants.STATIC_GRASS, ModPlants.STORM_LICHEN);
                s.vine = null;
            }
            case STARLIGHT_CAVERNS -> {
                s.moss = ModBlocks.STARMOSS;
                s.mossCarpet = null;
                s.plants = List.of(ModPlants.SILVERGRASS, ModPlants.MOONPETAL);
            }
            default -> s.plants = List.of(Blocks.FERN);
        }
    }

    // ------------------------------------------------------------------ state helpers

    public static BlockState stairs(Block b, Direction facing, boolean top) {
        BlockState s = b.getDefaultState();
        if (s.contains(StairsBlock.FACING)) {
            s = s.with(StairsBlock.FACING, facing).with(StairsBlock.HALF, top ? BlockHalf.TOP : BlockHalf.BOTTOM);
        }
        return s;
    }

    public static BlockState slab(Block b, boolean top) {
        BlockState s = b.getDefaultState();
        return s.contains(SlabBlock.TYPE) ? s.with(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM) : s;
    }

    public static BlockState axis(Block b, Direction.Axis a) {
        BlockState s = b.getDefaultState();
        return s.contains(PillarBlock.AXIS) ? s.with(PillarBlock.AXIS, a) : s;
    }

    public static BlockState facing(Block b, Direction d) {
        BlockState s = b.getDefaultState();
        if (s.contains(Properties.HORIZONTAL_FACING) && d.getAxis().isHorizontal()) {
            return s.with(Properties.HORIZONTAL_FACING, d);
        }
        if (s.contains(Properties.FACING)) {
            return s.with(Properties.FACING, d);
        }
        return s;
    }

    public static BlockState hanging(Block lantern) {
        BlockState s = lantern.getDefaultState();
        return s.contains(Properties.HANGING) ? s.with(Properties.HANGING, true) : s;
    }

    public static BlockState lit(Block b, boolean lit) {
        BlockState s = b.getDefaultState();
        return s.contains(Properties.LIT) ? s.with(Properties.LIT, lit) : s;
    }
}
