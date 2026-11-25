#!/usr/bin/env python3
"""
Generates Underdeep's worldgen JSON (15 biomes, configured/placed features, dimension type, world
preset, tags). Run from the repository root:

    python3 tools/worldgen/generate_data.py

Minecraft requires every placed feature shared by several biomes to keep the same relative order in
each biome's list. Here the global order is simply the declaration order of PLACED (plus the vanilla
features in VANILLA), and every biome list is sorted by it, so cycles cannot occur.

Edit the tables below, re-run, and commit the generated files together with this script.
"""
import json
import os
import shutil

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources")
DATA = os.path.join(ROOT, "data")
NS = "underdeep"

# Generation steps
RAW, LAKES, LOCAL, UNDER_STRUCT, SURF_STRUCT, STRONG, ORES, UNDER_DECO, SPRINGS, VEG, TOP = range(11)


def write(path, obj):
    full = os.path.join(DATA, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def state(name, **props):
    s = {"Name": name}
    if props:
        s["Properties"] = {k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in props.items()}
    return s


def provider(name, **props):
    return {"type": "minecraft:simple_state_provider", "state": state(name, **props)}


def simple_block(name, **props):
    return {"type": "minecraft:simple_block", "config": {"to_place": provider(name, **props)}}


def inline(feature, placement=None):
    return {"feature": feature, "placement": placement or []}


def patch(block_feature, tries=24, xz=5, y=2, filt=None):
    return {"type": "minecraft:random_patch", "config": {
        "tries": tries, "xz_spread": xz, "y_spread": y,
        "feature": inline(block_feature, [filt or AIR_FILTER])}}


AIR_FILTER = {"type": "minecraft:block_predicate_filter",
              "predicate": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}}
WATER_FILTER = {"type": "minecraft:block_predicate_filter",
                "predicate": {"type": "minecraft:matching_fluids", "fluids": "minecraft:water"}}
SURV_OAK = [{"type": "minecraft:block_predicate_filter",
             "predicate": {"type": "minecraft:would_survive", "state": state("minecraft:oak_sapling", stage=0)}}]

SPRING_ROCKS = ["minecraft:stone", "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
                "minecraft:deepslate", "minecraft:tuff", "minecraft:calcite", "minecraft:dirt",
                "minecraft:blackstone", "minecraft:basalt", "minecraft:smooth_basalt", "minecraft:dripstone_block",
                "minecraft:packed_ice", "minecraft:sandstone", "minecraft:netherrack", "underdeep:stormstone"]


def ore(targets, size, discard=0.0):
    return {"type": "minecraft:ore", "config": {"size": size, "discard_chance_on_air_exposure": discard,
                                                "targets": targets}}


def tag_target(tag, block):
    return {"target": {"predicate_type": "minecraft:tag_match", "tag": tag}, "state": state(block)}


def block_target(match, block):
    return {"target": {"predicate_type": "minecraft:block_match", "block": match}, "state": state(block)}


def tree(trunk, leaves, trunk_placer, foliage_placer, size=None, leaf_props=None):
    lp = leaf_props if leaf_props is not None else {"distance": 7, "persistent": False, "waterlogged": False}
    return {"type": "minecraft:tree", "config": {
        "dirt_provider": provider("minecraft:dirt"),
        "trunk_provider": provider(trunk, axis="y"),
        "foliage_provider": provider(leaves, **lp),
        "trunk_placer": trunk_placer, "foliage_placer": foliage_placer,
        "minimum_size": size or {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1},
        "decorators": [], "ignore_vines": True, "force_dirt": False}}


# ------------------------------------------------------------------------------------------------
# Configured features (underdeep namespace)

CONFIGURED = {
    "rootwild_trees": {"type": "minecraft:random_selector", "config": {
        "features": [
            {"feature": "minecraft:fancy_oak_checked", "chance": 0.3},
            {"feature": "minecraft:dark_oak_checked", "chance": 0.25},
            {"feature": "minecraft:birch_checked", "chance": 0.15},
            {"feature": inline("minecraft:azalea_tree", SURV_OAK), "chance": 0.12},
        ],
        "default": "minecraft:oak_checked"}},
    "golden_tree": tree("minecraft:stripped_oak_log", "underdeep:golden_leaves",
                        {"type": "minecraft:fancy_trunk_placer", "base_height": 6, "height_rand_a": 5, "height_rand_b": 3},
                        {"type": "minecraft:fancy_foliage_placer", "radius": 2, "offset": 4, "height": 4},
                        {"type": "minecraft:two_layers_feature_size", "limit": 0, "lower_size": 0, "upper_size": 0,
                         "min_clipped_height": 4}),
    "golden_bush": tree("minecraft:oak_log", "underdeep:golden_leaves",
                        {"type": "minecraft:straight_trunk_placer", "base_height": 1, "height_rand_a": 0, "height_rand_b": 0},
                        {"type": "minecraft:bush_foliage_placer", "radius": 2, "offset": 1, "height": 2}),
    "golden_trees": {"type": "minecraft:random_selector", "config": {
        "features": [{"feature": inline(f"{NS}:golden_bush", SURV_OAK), "chance": 0.3},
                     {"feature": "minecraft:birch_checked", "chance": 0.15}],
        "default": inline(f"{NS}:golden_tree", SURV_OAK)}},
    "start_trees": {"type": "minecraft:random_selector", "config": {
        "features": [{"feature": "minecraft:fancy_oak_checked", "chance": 0.3},
                     {"feature": "minecraft:birch_checked", "chance": 0.2}],
        "default": "minecraft:oak_checked"}},
    "frost_trees": {"type": "minecraft:random_selector", "config": {
        "features": [{"feature": "minecraft:pine_checked", "chance": 0.5}],
        "default": "minecraft:spruce_checked"}},
    "peak_trees": {"type": "minecraft:random_selector", "config": {
        "features": [{"feature": "minecraft:spruce_checked", "chance": 0.5}],
        "default": "minecraft:oak_checked"}},
    "huge_mushrooms": {"type": "minecraft:random_selector", "config": {
        "features": [{"feature": inline("minecraft:huge_red_mushroom"), "chance": 0.5}],
        "default": inline("minecraft:huge_brown_mushroom")}},
    "hanging_root": {"type": f"{NS}:hanging_root", "config": {}},
    "crystal_spire": {"type": f"{NS}:crystal_spire", "config": {}},
    "ore_outcrop": {"type": f"{NS}:ore_outcrop", "config": {}},
    "glowcap_patch": patch(simple_block(f"{NS}:glowcap"), 20),
    "starbloom_patch": patch(simple_block(f"{NS}:starbloom"), 24),
    "amberbloom_patch": patch(simple_block(f"{NS}:amberbloom"), 24),
    "emberbloom_patch": patch(simple_block(f"{NS}:emberbloom"), 20),
    "glowreed_patch": patch(simple_block(f"{NS}:glowreed", half="lower"), 28, 6, 2),
    "lumen_seagrass_patch": patch(simple_block(f"{NS}:lumen_seagrass"), 28, 5, 2, WATER_FILTER),
    "luminite_cluster_up": simple_block(f"{NS}:luminite_cluster", facing="up"),
    "luminite_cluster_down": simple_block(f"{NS}:luminite_cluster", facing="down"),
    "storm_crystal_up": simple_block(f"{NS}:storm_crystal", facing="up"),
    "storm_crystal_down": simple_block(f"{NS}:storm_crystal", facing="down"),
    "rime_crystal_up": simple_block(f"{NS}:rime_crystal", facing="up"),
    "rime_crystal_down": simple_block(f"{NS}:rime_crystal", facing="down"),
    "amethyst_cluster_up": simple_block("minecraft:amethyst_cluster", facing="up"),
    "seagrass": patch(simple_block("minecraft:seagrass"), 24, 4, 2, WATER_FILTER),
    "kelp": {"type": "minecraft:block_column", "config": {
        "direction": "up",
        "allowed_placement": {"type": "minecraft:matching_fluids", "fluids": "minecraft:water"},
        "prioritize_tip": True,
        "layers": [
            {"height": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 14},
             "provider": provider("minecraft:kelp_plant")},
            {"height": 1, "provider": provider("minecraft:kelp", age=20)}]}},
    "sea_pickle": simple_block("minecraft:sea_pickle", pickles=4, waterlogged=True),
    "lily_pad": simple_block("minecraft:lily_pad"),
    "spring_water": {"type": "minecraft:spring_feature", "config": {
        "state": state("minecraft:water", falling=True), "requires_block_below": True,
        "rock_count": 4, "hole_count": 1, "valid_blocks": SPRING_ROCKS}},
    "spring_lava": {"type": "minecraft:spring_feature", "config": {
        "state": state("minecraft:lava", falling=True), "requires_block_below": True,
        "rock_count": 4, "hole_count": 1, "valid_blocks": SPRING_ROCKS}},
    "ore_copper_upper": ore([tag_target("minecraft:stone_ore_replaceables", "minecraft:copper_ore"),
                             tag_target("minecraft:deepslate_ore_replaceables", "minecraft:deepslate_copper_ore")], 10),
    "ore_gilded_blackstone": ore([block_target("minecraft:blackstone", "minecraft:gilded_blackstone")], 7),
    "ore_magma": ore([block_target("minecraft:blackstone", "minecraft:magma_block"),
                      block_target("minecraft:basalt", "minecraft:magma_block")], 24),
    "ore_calcite_veins": ore([tag_target("minecraft:base_stone_overworld", "minecraft:calcite")], 40),
    "ore_charged_veins": ore([block_target("underdeep:stormstone", "underdeep:charged_stormstone")], 14),
    "ore_amber": ore([block_target("minecraft:tuff", "underdeep:amber_resin"),
                      block_target("minecraft:sandstone", "underdeep:amber_resin")], 6),
    "ore_quartz_crimson": ore([block_target("minecraft:netherrack", "minecraft:nether_quartz_ore")], 12),
    "ore_gold_crimson": ore([block_target("minecraft:netherrack", "minecraft:nether_gold_ore")], 10),
}

# ------------------------------------------------------------------------------------------------
# Placed features: (step, json). Declaration order = global order.

U = (95, 300)     # upper layer band
A = (-60, 300)    # both layers
D = (-60, 130)    # deep layer band


def cave(surface, min_y, max_y, count, feature, scan=128, before=None, after=None, variants=None):
    p = []
    if isinstance(count, int):
        if count > 1:
            p.append({"type": "minecraft:count", "count": count})
    else:
        p.append(count)
    p.append({"type": "minecraft:in_square"})
    p += before or []
    p.append({"type": f"{NS}:cave_surface", "surface": surface, "min_y": min_y, "max_y": max_y, "max_scan": scan})
    if variants is not None:
        p.append({"type": f"{NS}:variant", "variants": variants})
    p += after or []
    p.append({"type": "minecraft:biome"})
    return {"feature": feature, "placement": p}


def ranged(count, min_y, max_y, feature, rarity=None):
    p = []
    if rarity:
        p.append({"type": "minecraft:rarity_filter", "chance": rarity})
    if count > 1:
        p.append({"type": "minecraft:count", "count": count})
    p += [{"type": "minecraft:in_square"},
          {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                         "min_inclusive": {"absolute": min_y},
                                                         "max_inclusive": {"absolute": max_y}}},
          {"type": "minecraft:biome"}]
    return {"feature": feature, "placement": p}


def start(count, feature, surface="floor", radius=72):
    return cave(surface, 100, 300, count, feature, scan=200, before=[{"type": f"{NS}:start_zone", "radius": radius}])


def rare(chance):
    return {"type": "minecraft:rarity_filter", "chance": chance}


PLACED = [
    # --- local modifications
    (LOCAL, "amethyst_geode", ranged(1, -50, 260, "minecraft:amethyst_geode", rarity=6)),
    (LOCAL, "amethyst_geode_common", ranged(1, -50, 260, "minecraft:amethyst_geode", rarity=2)),
    # --- underground structures (fossils embedded in rock)
    (UNDER_STRUCT, "fossils", ranged(1, -40, 260, "minecraft:fossil_coal", rarity=3)),
    # --- ores
    (ORES, "ore_copper_upper", ranged(12, 100, 260, f"{NS}:ore_copper_upper")),
    (ORES, "ore_gilded_blackstone", ranged(8, -60, 300, f"{NS}:ore_gilded_blackstone")),
    (ORES, "ore_magma", ranged(4, -60, 300, f"{NS}:ore_magma")),
    (ORES, "ore_calcite_veins", ranged(2, -60, 300, f"{NS}:ore_calcite_veins")),
    (ORES, "ore_charged_veins", ranged(10, -60, 300, f"{NS}:ore_charged_veins")),
    (ORES, "ore_amber", ranged(6, 60, 300, f"{NS}:ore_amber")),
    (ORES, "ore_quartz_crimson", ranged(10, 60, 300, f"{NS}:ore_quartz_crimson")),
    (ORES, "ore_gold_crimson", ranged(6, 60, 300, f"{NS}:ore_gold_crimson")),
    # --- underground decoration
    (UNDER_DECO, "crystal_spires_floor", cave("floor", *A, 5, f"{NS}:crystal_spire")),
    (UNDER_DECO, "crystal_spires_ceiling", cave("ceiling", *A, 3, f"{NS}:crystal_spire")),
    (UNDER_DECO, "glow_lichen", cave("wall", *A, 14, "minecraft:glow_lichen")),
    (UNDER_DECO, "glow_lichen_sparse", cave("wall", *A, 4, "minecraft:glow_lichen")),
    (UNDER_DECO, "sculk_vein", cave("floor", *D, 14, "minecraft:sculk_vein")),
    # --- fluid springs (waterfalls and lavafalls)
    (SPRINGS, "spring_water", ranged(16, 100, 290, f"{NS}:spring_water")),
    (SPRINGS, "spring_water_many", ranged(34, 90, 290, f"{NS}:spring_water")),
    (SPRINGS, "spring_water_deep", ranged(6, -40, 120, f"{NS}:spring_water")),
    (SPRINGS, "spring_lava", ranged(14, -40, 290, f"{NS}:spring_lava")),
    (SPRINGS, "spring_lava_few", ranged(4, -40, 290, f"{NS}:spring_lava")),
    # --- vegetation & decoration (order matters only for overlap)
    (VEG, "start_ore_outcrops", start(2, f"{NS}:ore_outcrop", radius=64)),
    (VEG, "start_trees", start(3, f"{NS}:start_trees")),
    (VEG, "rootwild_trees", cave("floor", *U, 5, f"{NS}:rootwild_trees", scan=160, after=SURV_OAK, variants=[1, 3])),
    (VEG, "rootwild_trees_dense", cave("floor", *U, 12, f"{NS}:rootwild_trees", scan=160, after=SURV_OAK, variants=[2])),
    (VEG, "golden_trees", cave("floor", *U, 6, f"{NS}:golden_trees", scan=160, after=SURV_OAK, variants=[1, 2, 3])),
    (VEG, "mangroves", cave("floor", *U, 6, "minecraft:mangrove", scan=160, after=[
        {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive",
                                                                    "state": state("minecraft:mangrove_propagule")}}])),
    (VEG, "frost_trees", cave("floor", *U, 2, f"{NS}:frost_trees", scan=160, after=SURV_OAK, variants=[1, 2])),
    (VEG, "peak_trees", cave("floor", *U, 2, f"{NS}:peak_trees", scan=200, after=SURV_OAK, variants=[0, 1])),
    (VEG, "huge_mushrooms", cave("floor", *U, 5, f"{NS}:huge_mushrooms", scan=160)),
    (VEG, "warped_fungi", cave("floor", *U, 3, "minecraft:warped_fungus", scan=160, variants=[2, 3])),
    (VEG, "crimson_fungi", cave("floor", *U, 5, "minecraft:crimson_fungus", scan=160)),
    (VEG, "ice_spikes", cave("floor", *U, rare(2), "minecraft:ice_spike", scan=160, variants=[0, 2])),
    (VEG, "blue_ice", cave("ceiling", *U, 6, "minecraft:blue_ice", scan=160)),
    (VEG, "coral_gardens", cave("water_floor", 40, 112, 14, "minecraft:warm_ocean_vegetation", scan=90)),
    (VEG, "small_basalt_columns", cave("floor", *A, 3, "minecraft:small_basalt_columns", scan=160)),
    (VEG, "large_basalt_columns", cave("floor", *A, rare(2), "minecraft:large_basalt_columns", scan=160)),
    (VEG, "basalt_pillars", cave("ceiling", *A, rare(4), "minecraft:basalt_pillar", scan=40)),
    (VEG, "lava_deltas", cave("floor", *A, 2, "minecraft:delta", scan=160)),
    (VEG, "large_dripstone", cave("floor", *A, 2, "minecraft:large_dripstone", scan=160)),
    (VEG, "dripstone_cluster", cave("floor", *A, 2, "minecraft:dripstone_cluster", scan=160)),
    (VEG, "pointed_dripstone", cave("ceiling", *A, 10, "minecraft:pointed_dripstone", scan=160)),
    (VEG, "hanging_roots", cave("ceiling", *U, 4, f"{NS}:hanging_root", scan=160)),
    (VEG, "rooted_azalea", cave("ceiling", *U, 1, "minecraft:rooted_azalea_tree", scan=160)),
    (VEG, "moss_patch", cave("floor", *U, 4, "minecraft:moss_patch", scan=160)),
    (VEG, "moss_patch_ceiling", cave("ceiling", *U, 4, "minecraft:moss_patch_ceiling", scan=160)),
    (VEG, "warped_vegetation", cave("floor", *U, 3, "minecraft:warped_forest_vegetation", scan=160)),
    (VEG, "crimson_vegetation", cave("floor", *U, 5, "minecraft:crimson_forest_vegetation", scan=160)),
    (VEG, "nether_sprouts", cave("floor", *U, 2, "minecraft:nether_sprouts", scan=160)),
    (VEG, "twisting_vines", cave("floor", *U, 2, "minecraft:twisting_vines", scan=160)),
    (VEG, "weeping_vines", cave("ceiling", *U, 10, "minecraft:weeping_vines", scan=160)),
    (VEG, "start_grass", start(4, "minecraft:patch_grass")),
    (VEG, "patch_grass", cave("floor", *U, 8, "minecraft:patch_grass", scan=160)),
    (VEG, "patch_grass_sparse", cave("floor", *U, 3, "minecraft:patch_grass", scan=160)),
    (VEG, "patch_taiga_grass", cave("floor", *U, 4, "minecraft:patch_taiga_grass", scan=160)),
    (VEG, "dead_bushes", cave("floor", *U, 3, "minecraft:patch_dead_bush", scan=160)),
    (VEG, "start_berries", start(3, "minecraft:patch_berry_bush")),
    (VEG, "patch_berry_bush", cave("floor", *U, 2, "minecraft:patch_berry_bush", scan=160)),
    (VEG, "patch_pumpkin", cave("floor", *U, rare(3), "minecraft:patch_pumpkin", scan=160)),
    (VEG, "flower_default", cave("floor", *U, 3, "minecraft:flower_default", scan=160)),
    (VEG, "flower_meadow", cave("floor", *U, 6, "minecraft:flower_meadow", scan=160, variants=[0])),
    (VEG, "start_sugar_cane", start(3, "minecraft:patch_sugar_cane", radius=60)),
    (VEG, "patch_sugar_cane", cave("floor", 95, 160, 6, "minecraft:patch_sugar_cane", scan=80)),
    (VEG, "start_mushrooms", start(2, "minecraft:patch_brown_mushroom")),
    (VEG, "brown_mushrooms", cave("floor", *A, 2, "minecraft:patch_brown_mushroom", scan=160)),
    (VEG, "red_mushrooms", cave("floor", *A, 2, "minecraft:patch_red_mushroom", scan=160)),
    (VEG, "start_glowcaps", start(8, f"{NS}:glowcap_patch", radius=88)),
    (VEG, "glowcaps", cave("floor", *A, 4, f"{NS}:glowcap_patch", scan=160)),
    (VEG, "glowcaps_many", cave("floor", *A, 8, f"{NS}:glowcap_patch", scan=160)),
    (VEG, "glowcaps_sparse", cave("floor", *A, 1, f"{NS}:glowcap_patch", scan=160)),
    (VEG, "starblooms", cave("floor", *U, 8, f"{NS}:starbloom_patch", scan=160)),
    (VEG, "starblooms_sparse", cave("floor", *U, 2, f"{NS}:starbloom_patch", scan=160)),
    (VEG, "amberblooms", cave("floor", *U, 7, f"{NS}:amberbloom_patch", scan=160)),
    (VEG, "emberblooms", cave("floor", *U, 5, f"{NS}:emberbloom_patch", scan=160)),
    (VEG, "glowreeds", cave("floor", 100, 125, 10, f"{NS}:glowreed_patch", scan=40)),
    (VEG, "glowreeds_shore", cave("floor", 100, 125, 3, f"{NS}:glowreed_patch", scan=40)),
    (VEG, "big_dripleaf", cave("water_floor", 100, 113, 4, "minecraft:dripleaf", scan=20)),
    (VEG, "start_cave_vines", start(12, "minecraft:cave_vine", surface="ceiling", radius=88)),
    (VEG, "cave_vines", cave("ceiling", *U, 26, "minecraft:cave_vine", scan=160)),
    (VEG, "cave_vines_sparse", cave("ceiling", *A, 8, "minecraft:cave_vine", scan=160)),
    (VEG, "spore_blossom", cave("ceiling", *U, 3, "minecraft:spore_blossom", scan=160)),
    (VEG, "seagrass", cave("water_floor", 40, 112, 10, f"{NS}:seagrass", scan=80)),
    (VEG, "lumen_seagrass", cave("water_floor", 40, 112, 8, f"{NS}:lumen_seagrass_patch", scan=80)),
    (VEG, "lumen_seagrass_many", cave("water_floor", 30, 112, 16, f"{NS}:lumen_seagrass_patch", scan=90)),
    (VEG, "kelp", cave("water_floor", 40, 112, 6, f"{NS}:kelp", scan=80)),
    (VEG, "kelp_forest", cave("water_floor", 30, 112, 22, f"{NS}:kelp", scan=90)),
    (VEG, "sea_pickles", cave("water_floor", 40, 112, 3, f"{NS}:sea_pickle", scan=80)),
    (VEG, "sea_pickles_many", cave("water_floor", 30, 112, 10, f"{NS}:sea_pickle", scan=90)),
    (VEG, "lily_pads", cave("water_surface", 113, 113, 3, f"{NS}:lily_pad", scan=2)),
    (VEG, "lily_pads_many", cave("water_surface", 113, 113, 10, f"{NS}:lily_pad", scan=2)),
    (VEG, "glowstone", cave("ceiling", *A, 4, "minecraft:glowstone_extra", scan=160)),
    (VEG, "luminite_clusters_floor", cave("floor", *A, 8, f"{NS}:luminite_cluster_up", scan=160)),
    (VEG, "luminite_clusters_ceiling", cave("ceiling", *A, 6, f"{NS}:luminite_cluster_down", scan=160)),
    (VEG, "luminite_clusters_rare", cave("floor", *D, 1, f"{NS}:luminite_cluster_up", scan=160)),
    (VEG, "storm_crystals_floor", cave("floor", *A, 10, f"{NS}:storm_crystal_up", scan=160)),
    (VEG, "storm_crystals_ceiling", cave("ceiling", *A, 8, f"{NS}:storm_crystal_down", scan=160)),
    (VEG, "rime_crystals_floor", cave("floor", *U, 8, f"{NS}:rime_crystal_up", scan=160)),
    (VEG, "rime_crystals_ceiling", cave("ceiling", *U, 8, f"{NS}:rime_crystal_down", scan=160)),
    (VEG, "amethyst_clusters", cave("floor", *A, 4, f"{NS}:amethyst_cluster_up", scan=160)),
]
PLACED_NAMES = [n for _, n, _ in PLACED]

VANILLA = {
    UNDER_STRUCT: ["minecraft:monster_room", "minecraft:monster_room_deep"],
    ORES: ["minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_granite_upper", "minecraft:ore_granite_lower",
           "minecraft:ore_diorite_upper", "minecraft:ore_diorite_lower", "minecraft:ore_andesite_upper",
           "minecraft:ore_andesite_lower", "minecraft:ore_tuff", "minecraft:ore_coal_upper", "minecraft:ore_coal_lower",
           "minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_gold",
           "minecraft:ore_gold_lower", "minecraft:ore_redstone", "minecraft:ore_redstone_lower", "minecraft:ore_diamond",
           "minecraft:ore_diamond_medium", "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried",
           "minecraft:ore_lapis", "minecraft:ore_lapis_buried", "minecraft:ore_copper", "minecraft:ore_emerald"],
}
COMMON_ORES = [o for o in VANILLA[ORES] if o != "minecraft:ore_emerald"] + ["ore_copper_upper"]


def mob(type_, weight, lo, hi):
    return {"type": type_, "weight": weight, "minCount": lo, "maxCount": hi}


MONSTERS_MEDIUM = [mob("minecraft:spider", 80, 2, 3), mob("minecraft:zombie", 80, 2, 3),
                   mob("minecraft:skeleton", 80, 2, 3), mob("minecraft:creeper", 60, 1, 2),
                   mob("minecraft:slime", 20, 1, 2), mob("minecraft:enderman", 4, 1, 1)]
MONSTERS_HIGH = [mob("minecraft:spider", 90, 2, 4), mob("minecraft:zombie", 90, 2, 4),
                 mob("minecraft:skeleton", 90, 2, 4), mob("minecraft:creeper", 80, 2, 3),
                 mob("minecraft:witch", 6, 1, 1), mob("minecraft:enderman", 8, 1, 2)]
MONSTERS_EXTREME = MONSTERS_HIGH + [mob("minecraft:cave_spider", 40, 2, 4), mob("minecraft:zombie_villager", 8, 1, 1)]


def costs(budget):
    # Vanilla spawn-cost density capping: each mob adds "charge" to a potential field; new spawns need
    # the local potential to stay under the energy budget. Prevents hordes accumulating in giant caverns.
    return {m: {"energy_budget": budget, "charge": 0.7} for m in
            ["minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:creeper",
             "minecraft:cave_spider", "minecraft:witch", "minecraft:slime", "minecraft:zombie_villager"]}


DANGER = {"rootwild": "low", "fungal_expanse": "low", "golden_grove": "low", "emerald_wetlands": "low",
          "starlight_caverns": "low", "flooded_depths": "medium", "crystal_deep": "medium", "everfrost": "medium",
          "hollow_peaks": "medium", "petrified_forest": "medium", "sunken_gardens": "medium",
          "ashen_hollows": "high", "crimson_basin": "high", "stormstone_caverns": "high", "the_abyss": "extreme"}


def spawners_for(name, extra):
    danger = DANGER[name]
    sp = {k: [] for k in ["monster", "creature", "ambient", "axolotls", "underground_water_creature",
                          "water_creature", "water_ambient", "misc"]}
    sp["monster"] = {"low": [], "medium": MONSTERS_MEDIUM, "high": MONSTERS_HIGH, "extreme": MONSTERS_EXTREME}[danger]
    sp.update(extra)
    budget = {"low": None, "medium": 0.08, "high": 0.12, "extreme": 0.16}[danger]
    return sp, (costs(budget) if budget else {})


def music(sound):
    return {"sound": sound, "min_delay": 12000, "max_delay": 24000, "replace_current_music": False}


def particle(ptype, prob):
    return {"options": {"type": ptype}, "probability": prob}


LUSH_CREATURES = [mob("minecraft:sheep", 12, 2, 4), mob("minecraft:cow", 8, 2, 3), mob("minecraft:pig", 10, 2, 3),
                  mob("minecraft:chicken", 10, 2, 4), mob("minecraft:rabbit", 4, 2, 3)]

BIOMES = {
    "rootwild": {
        "temperature": 0.7, "downfall": 0.8,
        "effects": {"fog_color": 0x3B5A3A, "water_color": 0x3F76E4, "water_fog_color": 0x062A3A,
                    "grass_color": 0x6FB04A, "foliage_color": 0x4F9A3A,
                    "particle": particle("minecraft:spore_blossom_air", 0.002),
                    "music": music("minecraft:music.overworld.lush_caves")},
        "spawners": {"creature": LUSH_CREATURES, "ambient": [mob("minecraft:bat", 4, 2, 4)],
                     "water_ambient": [mob("minecraft:salmon", 5, 1, 4)]},
        "features": ["minecraft:monster_room", "fossils", *COMMON_ORES, "glow_lichen_sparse", "spring_water",
                     "start_ore_outcrops", "start_trees", "rootwild_trees", "rootwild_trees_dense", "hanging_roots",
                     "rooted_azalea", "moss_patch", "moss_patch_ceiling", "start_grass", "patch_grass", "patch_taiga_grass",
                     "start_berries", "patch_berry_bush", "patch_pumpkin", "flower_default", "flower_meadow",
                     "start_sugar_cane", "patch_sugar_cane", "start_mushrooms", "brown_mushrooms", "red_mushrooms",
                     "start_glowcaps", "glowcaps", "start_cave_vines", "cave_vines", "spore_blossom", "seagrass",
                     "lumen_seagrass", "lily_pads"]},
    "golden_grove": {
        "temperature": 0.9, "downfall": 0.5,
        "effects": {"fog_color": 0x6E5A2A, "water_color": 0x4A9AD8, "water_fog_color": 0x123040,
                    "grass_color": 0xB8B850, "foliage_color": 0xD0A030,
                    "particle": particle("minecraft:spore_blossom_air", 0.0015),
                    "music": music("minecraft:music.overworld.meadow")},
        "spawners": {"creature": LUSH_CREATURES, "ambient": [mob("minecraft:bat", 2, 2, 3)]},
        "features": ["fossils", *COMMON_ORES, "spring_water", "golden_trees", "moss_patch", "patch_grass",
                     "start_berries", "patch_berry_bush", "patch_pumpkin", "flower_default", "flower_meadow",
                     "patch_sugar_cane", "brown_mushrooms", "amberblooms", "cave_vines_sparse", "spore_blossom",
                     "seagrass", "lily_pads"]},
    "emerald_wetlands": {
        "temperature": 0.8, "downfall": 1.0,
        "effects": {"fog_color": 0x2A5A40, "water_color": 0x3A9A70, "water_fog_color": 0x0E2A20,
                    "grass_color": 0x5AB060, "foliage_color": 0x4AA060,
                    "particle": particle("minecraft:spore_blossom_air", 0.002),
                    "music": music("minecraft:music.overworld.swamp")},
        "spawners": {"creature": [mob("minecraft:frog", 10, 2, 5), mob("minecraft:chicken", 6, 2, 3),
                                  mob("minecraft:sheep", 4, 2, 3)],
                     "axolotls": [mob("minecraft:axolotl", 10, 3, 5)],
                     "water_ambient": [mob("minecraft:tropical_fish", 6, 3, 6), mob("minecraft:salmon", 4, 1, 3)]},
        "features": [*COMMON_ORES, "spring_water", "mangroves", "moss_patch", "patch_grass", "patch_sugar_cane",
                     "glowcaps", "glowreeds", "big_dripleaf", "cave_vines", "spore_blossom", "seagrass", "lumen_seagrass",
                     "lily_pads_many"]},
    "fungal_expanse": {
        "temperature": 0.8, "downfall": 0.9,
        "effects": {"fog_color": 0x45305E, "water_color": 0x5A6BC0, "water_fog_color": 0x1A1030,
                    "grass_color": 0x8AA855, "foliage_color": 0x7A9A45,
                    "particle": particle("minecraft:warped_spore", 0.004),
                    "music": music("minecraft:music.nether.warped_forest")},
        "spawners": {"creature": [mob("minecraft:mooshroom", 8, 2, 3)], "ambient": [mob("minecraft:bat", 3, 2, 3)]},
        "features": ["minecraft:monster_room", *COMMON_ORES, "glow_lichen_sparse", "spring_water", "huge_mushrooms",
                     "warped_fungi", "moss_patch", "warped_vegetation", "nether_sprouts", "twisting_vines",
                     "brown_mushrooms", "red_mushrooms", "glowcaps_many", "cave_vines_sparse", "seagrass"]},
    "starlight_caverns": {
        "temperature": 0.5, "downfall": 0.6,
        "effects": {"fog_color": 0x2A3070, "water_color": 0x5A7AE0, "water_fog_color": 0x101848,
                    "grass_color": 0x6A9AC0, "foliage_color": 0x5A8AB0,
                    "particle": particle("minecraft:end_rod", 0.0012),
                    "music": music("minecraft:music.overworld.grove")},
        "spawners": {"creature": [mob("minecraft:sheep", 10, 2, 4), mob("minecraft:rabbit", 8, 2, 3),
                                  mob("minecraft:cow", 6, 2, 3)],
                     "water_ambient": [mob("minecraft:tropical_fish", 4, 3, 6)]},
        "features": [*COMMON_ORES, "spring_water", "moss_patch", "patch_grass", "flower_default", "flower_meadow",
                     "glowcaps", "starblooms", "cave_vines_sparse", "spore_blossom", "seagrass", "lumen_seagrass",
                     "lily_pads_many", "amethyst_clusters"]},
    "flooded_depths": {
        "temperature": 0.6, "downfall": 1.0,
        "effects": {"fog_color": 0x20405E, "water_color": 0x2E86D0, "water_fog_color": 0x0A2A48,
                    "grass_color": 0x5DA56A, "foliage_color": 0x4A9A6A,
                    "music": music("minecraft:music.overworld.dripstone_caves")},
        "spawners": {"creature": [mob("minecraft:frog", 6, 2, 4)], "ambient": [mob("minecraft:bat", 6, 4, 6)],
                     "water_creature": [mob("minecraft:squid", 4, 1, 3)],
                     "underground_water_creature": [mob("minecraft:glow_squid", 10, 2, 4)],
                     "axolotls": [mob("minecraft:axolotl", 10, 4, 6)],
                     "water_ambient": [mob("minecraft:cod", 10, 3, 6), mob("minecraft:salmon", 8, 1, 5),
                                       mob("minecraft:tropical_fish", 4, 4, 8)]},
        "features": ["minecraft:monster_room", "fossils", *COMMON_ORES, "glow_lichen", "spring_water_many",
                     "large_dripstone", "dripstone_cluster", "pointed_dripstone", "moss_patch", "patch_grass",
                     "patch_sugar_cane", "glowcaps", "glowreeds_shore", "cave_vines_sparse", "seagrass",
                     "lumen_seagrass", "kelp", "sea_pickles", "lily_pads"]},
    "sunken_gardens": {
        "temperature": 0.8, "downfall": 1.0,
        "effects": {"fog_color": 0x1A5A60, "water_color": 0x30C0C0, "water_fog_color": 0x0A3A40,
                    "grass_color": 0x5AB09A, "foliage_color": 0x4AA08A,
                    "particle": particle("minecraft:underwater", 0.004),
                    "music": music("minecraft:music.overworld.lush_caves")},
        "spawners": {"water_creature": [mob("minecraft:squid", 4, 1, 3), mob("minecraft:dolphin", 1, 1, 2)],
                     "underground_water_creature": [mob("minecraft:glow_squid", 12, 2, 4)],
                     "axolotls": [mob("minecraft:axolotl", 8, 3, 5)],
                     "water_ambient": [mob("minecraft:tropical_fish", 14, 4, 8), mob("minecraft:pufferfish", 3, 1, 3)]},
        "features": [*COMMON_ORES, "glow_lichen", "spring_water", "coral_gardens", "seagrass", "lumen_seagrass_many",
                     "kelp_forest", "sea_pickles_many", "glowcaps", "lily_pads"]},
    "crystal_deep": {
        "temperature": 0.3, "downfall": 0.3,
        "effects": {"fog_color": 0x1E4A5C, "water_color": 0x4FB8D8, "water_fog_color": 0x0A3040,
                    "grass_color": 0x6AB0A0, "foliage_color": 0x5AA090,
                    "particle": particle("minecraft:end_rod", 0.0012),
                    "music": music("minecraft:music.overworld.grove")},
        "spawners": {"ambient": [mob("minecraft:bat", 6, 4, 6)],
                     "underground_water_creature": [mob("minecraft:glow_squid", 6, 2, 4)]},
        "features": ["amethyst_geode", "minecraft:monster_room", *COMMON_ORES, "ore_calcite_veins",
                     "crystal_spires_floor", "crystal_spires_ceiling", "glow_lichen", "spring_water", "spring_water_deep",
                     "pointed_dripstone", "glowcaps_sparse", "luminite_clusters_floor", "luminite_clusters_ceiling",
                     "amethyst_clusters", "lumen_seagrass"]},
    "everfrost": {
        "temperature": -0.5, "downfall": 0.6,
        "effects": {"fog_color": 0x6A8AB0, "water_color": 0x3938C9, "water_fog_color": 0x050533,
                    "grass_color": 0x80B497, "foliage_color": 0x60A17B,
                    "particle": particle("minecraft:white_ash", 0.006),
                    "music": music("minecraft:music.overworld.frozen_peaks")},
        "spawners": {"creature": [mob("minecraft:fox", 6, 2, 3), mob("minecraft:rabbit", 6, 2, 3)],
                     "ambient": [mob("minecraft:bat", 4, 2, 4)]},
        "features": ["minecraft:monster_room", *COMMON_ORES, "spring_water", "frost_trees", "ice_spikes", "blue_ice",
                     "glowcaps_sparse", "rime_crystals_floor", "rime_crystals_ceiling"]},
    "hollow_peaks": {
        "temperature": 0.4, "downfall": 0.4,
        "effects": {"fog_color": 0x5A5A66, "water_color": 0x3F76E4, "water_fog_color": 0x050533,
                    "grass_color": 0x8AB06A, "foliage_color": 0x6A9A4A,
                    "music": music("minecraft:music.overworld.stony_peaks")},
        "spawners": {"creature": [mob("minecraft:goat", 8, 2, 4), mob("minecraft:sheep", 6, 2, 3)],
                     "ambient": [mob("minecraft:bat", 4, 2, 4)]},
        "features": ["minecraft:monster_room", *COMMON_ORES, "minecraft:ore_emerald", "glow_lichen", "spring_water_many",
                     "peak_trees", "moss_patch", "patch_grass_sparse", "flower_default", "glowcaps",
                     "cave_vines_sparse"]},
    "petrified_forest": {
        "temperature": 1.0, "downfall": 0.1,
        "effects": {"fog_color": 0x5E5040, "water_color": 0x3F76E4, "water_fog_color": 0x050533,
                    "grass_color": 0xA09A60, "foliage_color": 0x908A50,
                    "particle": particle("minecraft:white_ash", 0.004),
                    "music": music("minecraft:music.overworld.desert")},
        "spawners": {"ambient": [mob("minecraft:bat", 4, 2, 4)]},
        "features": ["minecraft:monster_room", "fossils", *COMMON_ORES, "ore_amber", "glow_lichen_sparse",
                     "spring_water", "dead_bushes", "glowcaps_sparse", "brown_mushrooms"]},
    "crimson_basin": {
        "temperature": 1.5, "downfall": 0.3,
        "effects": {"fog_color": 0x5A1418, "water_color": 0x3F76E4, "water_fog_color": 0x050533,
                    "grass_color": 0x9A4A3A, "foliage_color": 0x8A3A2A,
                    "particle": particle("minecraft:crimson_spore", 0.008),
                    "mood_sound": {"sound": "minecraft:ambient.crimson_forest.mood", "tick_delay": 6000,
                                   "block_search_extent": 8, "offset": 2.0},
                    "music": music("minecraft:music.nether.crimson_forest")},
        "spawners": {},
        "features": [*COMMON_ORES, "ore_quartz_crimson", "ore_gold_crimson", "spring_lava_few", "crimson_fungi",
                     "crimson_vegetation", "weeping_vines", "red_mushrooms", "emberblooms"]},
    "ashen_hollows": {
        "temperature": 2.0, "downfall": 0.0,
        "effects": {"fog_color": 0x5A2A1C, "water_color": 0x3F76E4, "water_fog_color": 0x050533,
                    "grass_color": 0x9A8A50, "foliage_color": 0x8A7A40,
                    "particle": particle("minecraft:ash", 0.02),
                    "mood_sound": {"sound": "minecraft:ambient.basalt_deltas.mood", "tick_delay": 6000,
                                   "block_search_extent": 8, "offset": 2.0},
                    "additions_sound": {"sound": "minecraft:ambient.basalt_deltas.additions", "tick_chance": 0.0111},
                    "music": music("minecraft:music.nether.basalt_deltas")},
        "spawners": {},
        "features": ["minecraft:monster_room", "minecraft:monster_room_deep", "fossils", *COMMON_ORES,
                     "ore_gilded_blackstone", "ore_magma", "spring_lava", "small_basalt_columns", "large_basalt_columns",
                     "basalt_pillars", "lava_deltas", "glowcaps_sparse", "glowstone"]},
    "stormstone_caverns": {
        "temperature": 0.2, "downfall": 0.2,
        "effects": {"fog_color": 0x2A2860, "water_color": 0x5050C0, "water_fog_color": 0x101030,
                    "grass_color": 0x6A6AA0, "foliage_color": 0x5A5A90,
                    "particle": particle("minecraft:electric_spark", 0.004),
                    "music": music("minecraft:music.overworld.deep_dark")},
        "spawners": {"ambient": [mob("minecraft:bat", 4, 2, 4)]},
        "features": ["amethyst_geode_common", "minecraft:monster_room", *COMMON_ORES, "ore_charged_veins",
                     "glow_lichen_sparse", "spring_water", "pointed_dripstone", "storm_crystals_floor",
                     "storm_crystals_ceiling", "amethyst_clusters"]},
    "the_abyss": {
        "temperature": 0.5, "downfall": 0.2,
        "effects": {"fog_color": 0x0C0C14, "water_color": 0x3F4A6A, "water_fog_color": 0x050510,
                    "grass_color": 0x4A5A4A, "foliage_color": 0x3A4A3A,
                    "particle": particle("minecraft:ash", 0.008),
                    "music": music("minecraft:music.overworld.deep_dark")},
        "spawners": {"ambient": [mob("minecraft:bat", 10, 8, 8)]},
        "features": ["minecraft:monster_room", "minecraft:monster_room_deep", "fossils", *COMMON_ORES,
                     "glow_lichen_sparse", "sculk_vein", "spring_lava", "spring_water_deep", "large_dripstone",
                     "pointed_dripstone", "luminite_clusters_rare"]},
}


def global_order():
    order = {}
    by_name = {n: st for st, n, _ in PLACED}
    for step in range(11):
        names = list(VANILLA.get(step, []))
        names += [f"{NS}:{n}" for st, n, _ in PLACED if st == step]
        order[step] = names
    return order, by_name


def biome_json(name, spec, order, by_name):
    steps = [[] for _ in range(11)]
    for f in spec["features"]:
        if f.startswith("minecraft:"):
            step = next(st for st, names in VANILLA.items() if f in names)
            full = f
        else:
            if f not in by_name:
                raise SystemExit(f"unknown placed feature {f} in {name}")
            step = by_name[f]
            full = f"{NS}:{f}"
        if full not in steps[step]:
            steps[step].append(full)
    for st in range(11):
        steps[st].sort(key=order[st].index)
    effects = {"mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8,
                              "offset": 2.0}}
    effects.update(spec["effects"])
    effects.setdefault("sky_color", effects["fog_color"])
    spawners, spawn_costs = spawners_for(name, spec["spawners"])
    return {
        "has_precipitation": False,
        "temperature": spec["temperature"],
        "downfall": spec["downfall"],
        "effects": effects,
        "spawners": spawners,
        "spawn_costs": spawn_costs,
        "carvers": {},
        "features": steps,
    }


def main():
    for sub in ["worldgen/biome", "worldgen/configured_feature", "worldgen/placed_feature"]:
        shutil.rmtree(os.path.join(DATA, NS, sub), ignore_errors=True)
    for name, cf in CONFIGURED.items():
        write(f"{NS}/worldgen/configured_feature/{name}.json", cf)
    for _, name, pf in PLACED:
        write(f"{NS}/worldgen/placed_feature/{name}.json", pf)
    order, by_name = global_order()
    for name, spec in BIOMES.items():
        write(f"{NS}/worldgen/biome/{name}.json", biome_json(name, spec, order, by_name))

    write(f"{NS}/dimension_type/underdeep.json", {
        "ultrawarm": False, "natural": True, "coordinate_scale": 1.0,
        "has_skylight": False, "has_ceiling": True, "ambient_light": 0.1,
        "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7},
        "monster_spawn_block_light_limit": 0,
        "piglin_safe": False, "bed_works": True, "respawn_anchor_works": False, "has_raids": True,
        "logical_height": 384, "min_y": -64, "height": 384,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "effects": f"{NS}:underdeep",
    })
    write(f"{NS}/worldgen/world_preset/underdeep.json", {"dimensions": {
        "minecraft:overworld": {"type": f"{NS}:underdeep", "generator": {
            "type": f"{NS}:underdeep", "biome_source": {"type": f"{NS}:underdeep"}}},
        "minecraft:the_nether": {"type": "minecraft:the_nether", "generator": {
            "type": "minecraft:noise", "settings": "minecraft:nether",
            "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:nether"}}},
        "minecraft:the_end": {"type": "minecraft:the_end", "generator": {
            "type": "minecraft:noise", "settings": "minecraft:end", "biome_source": {"type": "minecraft:the_end"}}},
    }})
    write("minecraft/tags/worldgen/world_preset/normal.json", {"replace": False, "values": [f"{NS}:underdeep"]})
    write(f"{NS}/tags/worldgen/biome/is_underdeep.json", {"replace": False, "values": [f"{NS}:{b}" for b in BIOMES]})
    for danger in ["low", "medium", "high", "extreme"]:
        write(f"{NS}/tags/worldgen/biome/danger_{danger}.json",
              {"replace": False, "values": [f"{NS}:{b}" for b in BIOMES if DANGER[b] == danger]})
    print(f"wrote {len(CONFIGURED)} configured, {len(PLACED)} placed, {len(BIOMES)} biomes")


if __name__ == "__main__":
    main()
