package dev.underdeep.civ.arch;

import dev.underdeep.civ.Civilization;
import dev.underdeep.registry.ModBlocks;
import dev.underdeep.registry.ModPlants;
import dev.underdeep.registry.ModWood;
import dev.underdeep.registry.WoodSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.DyeColor;

/**
 * Materials of one architectural style. Every civilization has its own palette; roles are used by the
 * building grammar so a design reads differently per culture (D-040).
 */
public final class Palette {
    public Block foundation, foundationStairs, foundationSlab, foundationWall;
    public Block floor, floorAccent;
    public Block wall, wallAlt, frame, frameAxis;
    public Block roof, roofStairs, roofSlab;
    public Block trim, trimStairs, trimSlab;
    public Block window, door, trapdoor, fence, fenceGate;
    public Block light, hangingLight, wallLight;
    public Block path, pathEdge, plaza, plazaAccent;
    public Block planks, planksStairs, planksSlab, log;
    public Block ornament, glow, leaves, plant, plantAlt, water;
    public DyeColor cloth, clothAlt;
    public BlockState accent;

    private Palette() {
    }

    public static Palette of(Civilization civ) {
        return switch (civ) {
            case ROOTFOLK -> rootfolk();
            case FORGEKIN -> forgekin();
            case TIDE_DWELLERS -> tide();
            case SPOREFOLK -> sporefolk();
            case GLIMMERKIN -> glimmerkin();
            case STONEBORN -> stoneborn();
            case AMBERFOLK -> amberfolk();
            case ASTRALS -> astrals();
        };
    }

    private static void wood(Palette p, WoodSet w) {
        p.planks = w.planks;
        p.planksStairs = w.stairs;
        p.planksSlab = w.slab;
        p.log = w.log;
        p.door = w.door;
        p.trapdoor = w.trapdoor;
        p.fence = w.fence;
        p.fenceGate = w.fenceGate;
    }

    private static Palette rootfolk() {
        Palette p = new Palette();
        wood(p, ModWood.DUSKOAK);
        p.foundation = Blocks.MOSSY_COBBLESTONE;
        p.foundationStairs = Blocks.MOSSY_COBBLESTONE_STAIRS;
        p.foundationSlab = Blocks.MOSSY_COBBLESTONE_SLAB;
        p.foundationWall = Blocks.MOSSY_COBBLESTONE_WALL;
        p.floor = ModWood.DUSKOAK.planks;
        p.floorAccent = Blocks.SPRUCE_PLANKS;
        p.wall = ModWood.DUSKOAK.planks;
        p.wallAlt = Blocks.MUD_BRICKS;
        p.frame = ModWood.DUSKOAK.log;
        p.frameAxis = ModWood.DUSKOAK.strippedLog;
        p.roof = ModWood.DUSKOAK.leaves;
        p.roofStairs = Blocks.SPRUCE_STAIRS;
        p.roofSlab = Blocks.SPRUCE_SLAB;
        p.trim = Blocks.SPRUCE_PLANKS;
        p.trimStairs = Blocks.SPRUCE_STAIRS;
        p.trimSlab = Blocks.SPRUCE_SLAB;
        p.window = Blocks.GLASS_PANE;
        p.light = Blocks.LANTERN;
        p.hangingLight = Blocks.LANTERN;
        p.wallLight = Blocks.LANTERN;
        p.path = Blocks.DIRT_PATH;
        p.pathEdge = Blocks.COARSE_DIRT;
        p.plaza = Blocks.MOSSY_STONE_BRICKS;
        p.plazaAccent = Blocks.MOSS_BLOCK;
        p.ornament = Blocks.CHAIN;
        p.glow = ModBlocks.GLOWCAP;
        p.leaves = ModWood.DUSKOAK.leaves;
        p.plant = ModPlants.GLOWFERN;
        p.plantAlt = Blocks.FERN;
        p.water = Blocks.WATER;
        p.cloth = DyeColor.GREEN;
        p.clothAlt = DyeColor.BROWN;
        p.accent = Blocks.MOSS_CARPET.getDefaultState();
        return p;
    }

    private static Palette forgekin() {
        Palette p = new Palette();
        wood(p, ModWood.BLOODTHORN);
        p.foundation = Blocks.POLISHED_BLACKSTONE_BRICKS;
        p.foundationStairs = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS;
        p.foundationSlab = Blocks.POLISHED_BLACKSTONE_BRICK_SLAB;
        p.foundationWall = Blocks.POLISHED_BLACKSTONE_BRICK_WALL;
        p.floor = Blocks.POLISHED_BASALT;
        p.floorAccent = Blocks.SMOOTH_BASALT;
        p.wall = Blocks.BLACKSTONE;
        p.wallAlt = Blocks.POLISHED_BLACKSTONE;
        p.frame = Blocks.CUT_COPPER;
        p.frameAxis = Blocks.BASALT;
        p.roof = Blocks.DEEPSLATE_TILES;
        p.roofStairs = Blocks.DEEPSLATE_TILE_STAIRS;
        p.roofSlab = Blocks.DEEPSLATE_TILE_SLAB;
        p.trim = Blocks.WAXED_CUT_COPPER;
        p.trimStairs = Blocks.WAXED_CUT_COPPER_STAIRS;
        p.trimSlab = Blocks.WAXED_CUT_COPPER_SLAB;
        p.window = Blocks.IRON_BARS;
        p.light = Blocks.LANTERN;
        p.hangingLight = Blocks.LANTERN;
        p.wallLight = Blocks.REDSTONE_LAMP;
        p.path = Blocks.POLISHED_BLACKSTONE_BRICKS;
        p.pathEdge = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS;
        p.plaza = Blocks.POLISHED_BLACKSTONE;
        p.plazaAccent = Blocks.WAXED_COPPER_BLOCK;
        p.ornament = Blocks.CHAIN;
        p.glow = Blocks.MAGMA_BLOCK;
        p.leaves = null;
        p.plant = ModPlants.PYRELILY;
        p.plantAlt = ModPlants.EMBERCAP;
        p.water = Blocks.WATER;
        p.cloth = DyeColor.ORANGE;
        p.clothAlt = DyeColor.GRAY;
        p.accent = Blocks.WAXED_COPPER_BLOCK.getDefaultState();
        return p;
    }

    private static Palette tide() {
        Palette p = new Palette();
        wood(p, ModWood.MIREWILLOW);
        p.foundation = Blocks.PRISMARINE_BRICKS;
        p.foundationStairs = Blocks.PRISMARINE_BRICK_STAIRS;
        p.foundationSlab = Blocks.PRISMARINE_BRICK_SLAB;
        p.foundationWall = Blocks.PRISMARINE_WALL;
        p.floor = ModWood.MIREWILLOW.planks;
        p.floorAccent = Blocks.BIRCH_PLANKS;
        p.wall = Blocks.BIRCH_PLANKS;
        p.wallAlt = ModWood.MIREWILLOW.planks;
        p.frame = ModWood.MIREWILLOW.log;
        p.frameAxis = ModWood.MIREWILLOW.strippedLog;
        p.roof = Blocks.DARK_PRISMARINE;
        p.roofStairs = Blocks.DARK_PRISMARINE_STAIRS;
        p.roofSlab = Blocks.DARK_PRISMARINE_SLAB;
        p.trim = ModWood.MIREWILLOW.planks;
        p.trimStairs = ModWood.MIREWILLOW.stairs;
        p.trimSlab = ModWood.MIREWILLOW.slab;
        p.window = Blocks.LIGHT_BLUE_STAINED_GLASS_PANE;
        p.light = Blocks.SEA_LANTERN;
        p.hangingLight = Blocks.LANTERN;
        p.wallLight = Blocks.SEA_LANTERN;
        p.path = ModWood.MIREWILLOW.planks;
        p.pathEdge = ModWood.MIREWILLOW.slab;
        p.plaza = Blocks.PRISMARINE_BRICKS;
        p.plazaAccent = Blocks.SEA_LANTERN;
        p.ornament = Blocks.CHAIN;
        p.glow = Blocks.SEA_LANTERN;
        p.leaves = ModWood.MIREWILLOW.leaves;
        p.plant = ModPlants.LAKEBLOOM;
        p.plantAlt = Blocks.SEAGRASS;
        p.water = Blocks.WATER;
        p.cloth = DyeColor.CYAN;
        p.clothAlt = DyeColor.WHITE;
        p.accent = Blocks.TUBE_CORAL_BLOCK.getDefaultState();
        return p;
    }

    private static Palette sporefolk() {
        Palette p = new Palette();
        wood(p, ModWood.DUSKOAK);
        p.planks = Blocks.MUSHROOM_STEM;
        p.foundation = ModPlants.FUNGAL_STEM;
        p.foundationStairs = Blocks.MUD_BRICK_STAIRS;
        p.foundationSlab = Blocks.MUD_BRICK_SLAB;
        p.foundationWall = Blocks.MUD_BRICK_WALL;
        p.floor = Blocks.PACKED_MUD;
        p.floorAccent = ModBlocks.GLOWMOSS;
        p.wall = Blocks.MUSHROOM_STEM;
        p.wallAlt = ModPlants.FUNGAL_STEM;
        p.frame = ModPlants.FUNGAL_STEM;
        p.frameAxis = ModPlants.FUNGAL_STEM;
        p.roof = ModPlants.SPORESTALK_CAP;
        p.roofStairs = Blocks.MUD_BRICK_STAIRS;
        p.roofSlab = Blocks.MUD_BRICK_SLAB;
        p.trim = ModPlants.BELLCAP_CAP;
        p.trimStairs = Blocks.MUD_BRICK_STAIRS;
        p.trimSlab = Blocks.MUD_BRICK_SLAB;
        p.window = Blocks.PURPLE_STAINED_GLASS_PANE;
        p.light = Blocks.SHROOMLIGHT;
        p.hangingLight = Blocks.SOUL_LANTERN;
        p.wallLight = Blocks.SHROOMLIGHT;
        p.path = ModBlocks.SPOREGLOW_SOIL;
        p.pathEdge = Blocks.MYCELIUM;
        p.plaza = Blocks.MUD_BRICKS;
        p.plazaAccent = ModPlants.GLOWGILL;
        p.ornament = Blocks.CHAIN;
        p.glow = Blocks.SHROOMLIGHT;
        p.leaves = null;
        p.plant = ModPlants.PUFFCAP;
        p.plantAlt = ModPlants.LANTERNCAP;
        p.water = Blocks.WATER;
        p.cloth = DyeColor.PURPLE;
        p.clothAlt = DyeColor.MAGENTA;
        p.accent = ModPlants.SHELFCAP_CAP.getDefaultState();
        return p;
    }

    private static Palette glimmerkin() {
        Palette p = new Palette();
        wood(p, ModWood.SPIREBARK);
        p.foundation = Blocks.POLISHED_DEEPSLATE;
        p.foundationStairs = Blocks.POLISHED_DEEPSLATE_STAIRS;
        p.foundationSlab = Blocks.POLISHED_DEEPSLATE_SLAB;
        p.foundationWall = Blocks.POLISHED_DEEPSLATE_WALL;
        p.floor = Blocks.POLISHED_DIORITE;
        p.floorAccent = ModPlants.OPAL_BLOCK;
        p.wall = Blocks.CALCITE;
        p.wallAlt = Blocks.SMOOTH_QUARTZ;
        p.frame = Blocks.POLISHED_DEEPSLATE;
        p.frameAxis = Blocks.QUARTZ_PILLAR;
        p.roof = Blocks.PURPUR_BLOCK;
        p.roofStairs = Blocks.PURPUR_STAIRS;
        p.roofSlab = Blocks.PURPUR_SLAB;
        p.trim = Blocks.SMOOTH_QUARTZ;
        p.trimStairs = Blocks.SMOOTH_QUARTZ_STAIRS;
        p.trimSlab = Blocks.SMOOTH_QUARTZ_SLAB;
        p.window = Blocks.LIGHT_BLUE_STAINED_GLASS_PANE;
        p.light = ModBlocks.LUMINITE_BLOCK;
        p.hangingLight = Blocks.SOUL_LANTERN;
        p.wallLight = ModBlocks.LUMINITE_BLOCK;
        p.path = Blocks.POLISHED_DEEPSLATE;
        p.pathEdge = Blocks.DEEPSLATE_TILES;
        p.plaza = Blocks.POLISHED_DIORITE;
        p.plazaAccent = Blocks.AMETHYST_BLOCK;
        p.ornament = ModPlants.GLIMMER_CLUSTER;
        p.glow = ModBlocks.LUMINITE_BLOCK;
        p.leaves = ModWood.SPIREBARK.leaves;
        p.plant = ModPlants.PRISM_LILY;
        p.plantAlt = ModPlants.CALCICAP;
        p.water = Blocks.WATER;
        p.cloth = DyeColor.BLUE;
        p.clothAlt = DyeColor.PURPLE;
        p.accent = Blocks.AMETHYST_BLOCK.getDefaultState();
        return p;
    }

    private static Palette stoneborn() {
        Palette p = new Palette();
        wood(p, ModWood.FROSTFIR);
        p.foundation = Blocks.STONE_BRICKS;
        p.foundationStairs = Blocks.STONE_BRICK_STAIRS;
        p.foundationSlab = Blocks.STONE_BRICK_SLAB;
        p.foundationWall = Blocks.STONE_BRICK_WALL;
        p.floor = ModWood.FROSTFIR.planks;
        p.floorAccent = Blocks.POLISHED_ANDESITE;
        p.wall = Blocks.COBBLED_DEEPSLATE;
        p.wallAlt = Blocks.STONE_BRICKS;
        p.frame = Blocks.POLISHED_ANDESITE;
        p.frameAxis = ModWood.FROSTFIR.log;
        p.roof = Blocks.DEEPSLATE_TILES;
        p.roofStairs = Blocks.DEEPSLATE_TILE_STAIRS;
        p.roofSlab = Blocks.DEEPSLATE_TILE_SLAB;
        p.trim = Blocks.POLISHED_ANDESITE;
        p.trimStairs = Blocks.POLISHED_ANDESITE_STAIRS;
        p.trimSlab = Blocks.POLISHED_ANDESITE_SLAB;
        p.window = Blocks.GLASS_PANE;
        p.light = Blocks.LANTERN;
        p.hangingLight = Blocks.LANTERN;
        p.wallLight = Blocks.LANTERN;
        p.path = Blocks.STONE_BRICKS;
        p.pathEdge = Blocks.CRACKED_STONE_BRICKS;
        p.plaza = Blocks.POLISHED_ANDESITE;
        p.plazaAccent = Blocks.CHISELED_STONE_BRICKS;
        p.ornament = Blocks.CHAIN;
        p.glow = Blocks.LANTERN;
        p.leaves = ModWood.FROSTFIR.leaves;
        p.plant = ModPlants.FROSTBELL;
        p.plantAlt = Blocks.SHORT_GRASS;
        p.water = Blocks.WATER;
        p.cloth = DyeColor.RED;
        p.clothAlt = DyeColor.GRAY;
        p.accent = Blocks.CHISELED_STONE_BRICKS.getDefaultState();
        return p;
    }

    private static Palette amberfolk() {
        Palette p = new Palette();
        wood(p, ModWood.GOLDWOOD);
        p.foundation = Blocks.SMOOTH_SANDSTONE;
        p.foundationStairs = Blocks.SMOOTH_SANDSTONE_STAIRS;
        p.foundationSlab = Blocks.SMOOTH_SANDSTONE_SLAB;
        p.foundationWall = Blocks.SANDSTONE_WALL;
        p.floor = ModWood.GOLDWOOD.planks;
        p.floorAccent = Blocks.TERRACOTTA;
        p.wall = Blocks.WHITE_TERRACOTTA;
        p.wallAlt = Blocks.CUT_SANDSTONE;
        p.frame = ModWood.GOLDWOOD.log;
        p.frameAxis = ModWood.GOLDWOOD.strippedLog;
        p.roof = Blocks.BRICKS;
        p.roofStairs = Blocks.BRICK_STAIRS;
        p.roofSlab = Blocks.BRICK_SLAB;
        p.trim = ModWood.GOLDWOOD.planks;
        p.trimStairs = ModWood.GOLDWOOD.stairs;
        p.trimSlab = ModWood.GOLDWOOD.slab;
        p.window = Blocks.GLASS_PANE;
        p.light = Blocks.LANTERN;
        p.hangingLight = Blocks.LANTERN;
        p.wallLight = Blocks.LANTERN;
        p.path = Blocks.SMOOTH_SANDSTONE;
        p.pathEdge = Blocks.CUT_SANDSTONE;
        p.plaza = Blocks.CUT_SANDSTONE;
        p.plazaAccent = Blocks.CHISELED_SANDSTONE;
        p.ornament = Blocks.CHAIN;
        p.glow = ModBlocks.AMBER_RESIN;
        p.leaves = ModBlocks.GOLDEN_LEAVES;
        p.plant = ModBlocks.AMBERBLOOM;
        p.plantAlt = Blocks.FERN;
        p.water = Blocks.WATER;
        p.cloth = DyeColor.YELLOW;
        p.clothAlt = DyeColor.ORANGE;
        p.accent = ModBlocks.AMBER_RESIN.getDefaultState();
        return p;
    }

    private static Palette astrals() {
        Palette p = new Palette();
        wood(p, ModWood.LUMENWOOD);
        p.foundation = Blocks.QUARTZ_BRICKS;
        p.foundationStairs = Blocks.QUARTZ_STAIRS;
        p.foundationSlab = Blocks.QUARTZ_SLAB;
        p.foundationWall = Blocks.DIORITE_WALL;
        p.floor = Blocks.SMOOTH_QUARTZ;
        p.floorAccent = ModBlocks.STARSTONE;
        p.wall = Blocks.CALCITE;
        p.wallAlt = Blocks.QUARTZ_BRICKS;
        p.frame = ModPlants.STARSILVER_BLOCK;
        p.frameAxis = Blocks.QUARTZ_PILLAR;
        p.roof = Blocks.LIGHT_BLUE_TERRACOTTA;
        p.roofStairs = Blocks.QUARTZ_STAIRS;
        p.roofSlab = Blocks.QUARTZ_SLAB;
        p.trim = ModPlants.STARSILVER_BLOCK;
        p.trimStairs = Blocks.QUARTZ_STAIRS;
        p.trimSlab = Blocks.QUARTZ_SLAB;
        p.window = Blocks.LIGHT_BLUE_STAINED_GLASS_PANE;
        p.light = ModBlocks.STARSTONE;
        p.hangingLight = Blocks.SOUL_LANTERN;
        p.wallLight = ModBlocks.STARSTONE;
        p.path = Blocks.SMOOTH_QUARTZ;
        p.pathEdge = Blocks.QUARTZ_BRICKS;
        p.plaza = Blocks.SMOOTH_QUARTZ;
        p.plazaAccent = ModBlocks.STARSTONE;
        p.ornament = Blocks.END_ROD;
        p.glow = ModBlocks.STARSTONE;
        p.leaves = ModWood.LUMENWOOD.leaves;
        p.plant = ModPlants.SILVERGRASS;
        p.plantAlt = ModBlocks.STARBLOOM;
        p.water = Blocks.WATER;
        p.cloth = DyeColor.LIGHT_BLUE;
        p.clothAlt = DyeColor.WHITE;
        p.accent = ModBlocks.STARSTONE.getDefaultState();
        return p;
    }
}
