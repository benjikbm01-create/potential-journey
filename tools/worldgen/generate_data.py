#!/usr/bin/env python3
"""
Generates Underdeep's worldgen JSON (biomes, configured/placed features, dimension type, world
preset, tags). Run from the repository root:

    python3 tools/worldgen/generate_data.py

Why a generator: Minecraft requires every placed feature that appears in several biomes to keep the
same relative order in each biome's feature list (otherwise world loading fails with a feature
order cycle). Declaring one global ordered list per generation step here guarantees that.

Edit the tables below, re-run, and commit the generated files together with this script.
"""
import json
import os
import shutil

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources")
DATA = os.path.join(ROOT, "data")
NS = "underdeep"


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


def simple_block(name, **props):
    return {"type": "minecraft:simple_block",
            "config": {"to_place": {"type": "minecraft:simple_state_provider", "state": state(name, **props)}}}


def inline(feature, placement=None):
    return {"feature": feature, "placement": placement or []}


AIR_FILTER = {"type": "minecraft:block_predicate_filter",
              "predicate": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}}

# ------------------------------------------------------------------------------------------------
# Configured features (underdeep namespace)

SPRING_ROCKS = ["minecraft:stone", "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
                "minecraft:deepslate", "minecraft:tuff", "minecraft:calcite", "minecraft:dirt",
                "minecraft:blackstone", "minecraft:basalt", "minecraft:smooth_basalt",
                "minecraft:dripstone_block"]

CONFIGURED = {
    "rootwild_trees": {"type": "minecraft:random_selector", "config": {
        "features": [
            {"feature": "minecraft:fancy_oak_checked", "chance": 0.3},
            {"feature": "minecraft:dark_oak_checked", "chance": 0.25},
            {"feature": "minecraft:birch_checked", "chance": 0.15},
            {"feature": inline("minecraft:azalea_tree", [{"type": "minecraft:block_predicate_filter", "predicate": {
                "type": "minecraft:would_survive", "state": state("minecraft:oak_sapling", stage=0)}}]), "chance": 0.12},
        ],
        "default": "minecraft:oak_checked"}},
    "start_trees": {"type": "minecraft:random_selector", "config": {
        "features": [
            {"feature": "minecraft:fancy_oak_checked", "chance": 0.3},
            {"feature": "minecraft:birch_checked", "chance": 0.2},
        ],
        "default": "minecraft:oak_checked"}},
    "huge_mushrooms": {"type": "minecraft:random_selector", "config": {
        "features": [{"feature": inline("minecraft:huge_red_mushroom"), "chance": 0.5}],
        "default": inline("minecraft:huge_brown_mushroom")}},
    "hanging_root": {"type": f"{NS}:hanging_root", "config": {}},
    "crystal_spire": {"type": f"{NS}:crystal_spire", "config": {}},
    "ore_outcrop": {"type": f"{NS}:ore_outcrop", "config": {}},
    "glowcap_patch": {"type": "minecraft:random_patch", "config": {
        "tries": 20, "xz_spread": 5, "y_spread": 2,
        "feature": inline(simple_block(f"{NS}:glowcap"), [AIR_FILTER])}},
    "luminite_cluster_up": simple_block(f"{NS}:luminite_cluster", facing="up"),
    "luminite_cluster_down": simple_block(f"{NS}:luminite_cluster", facing="down"),
    "amethyst_cluster_up": simple_block("minecraft:amethyst_cluster", facing="up"),
    "seagrass": {"type": "minecraft:random_patch", "config": {
        "tries": 24, "xz_spread": 4, "y_spread": 2,
        "feature": inline(simple_block("minecraft:seagrass"), [{"type": "minecraft:block_predicate_filter",
            "predicate": {"type": "minecraft:matching_fluids", "fluids": "minecraft:water"}}])}},
    "kelp": {"type": "minecraft:block_column", "config": {
        "direction": "up",
        "allowed_placement": {"type": "minecraft:matching_fluids", "fluids": "minecraft:water"},
        "prioritize_tip": True,
        "layers": [
            {"height": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 10},
             "provider": {"type": "minecraft:simple_state_provider", "state": state("minecraft:kelp_plant")}},
            {"height": 1, "provider": {"type": "minecraft:simple_state_provider", "state": state("minecraft:kelp", age=20)}},
        ]}},
    "sea_pickle": simple_block("minecraft:sea_pickle", pickles=3, waterlogged=True),
    "lily_pad": simple_block("minecraft:lily_pad"),
    "spring_water": {"type": "minecraft:spring_feature", "config": {
        "state": state("minecraft:water", falling=True), "requires_block_below": True,
        "rock_count": 4, "hole_count": 1, "valid_blocks": SPRING_ROCKS}},
    "spring_lava": {"type": "minecraft:spring_feature", "config": {
        "state": state("minecraft:lava", falling=True), "requires_block_below": True,
        "rock_count": 4, "hole_count": 1, "valid_blocks": SPRING_ROCKS}},
    "ore_copper_upper": {"type": "minecraft:ore", "config": {
        "size": 10, "discard_chance_on_air_exposure": 0.0, "targets": [
            {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"},
             "state": state("minecraft:copper_ore")},
            {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"},
             "state": state("minecraft:deepslate_copper_ore")}]}},
    "ore_gilded_blackstone": {"type": "minecraft:ore", "config": {
        "size": 7, "discard_chance_on_air_exposure": 0.0, "targets": [
            {"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:blackstone"},
             "state": state("minecraft:gilded_blackstone")}]}},
    "ore_magma": {"type": "minecraft:ore", "config": {
        "size": 24, "discard_chance_on_air_exposure": 0.0, "targets": [
            {"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:blackstone"},
             "state": state("minecraft:magma_block")},
            {"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:basalt"},
             "state": state("minecraft:magma_block")}]}},
    "ore_calcite_veins": {"type": "minecraft:ore", "config": {
        "size": 40, "discard_chance_on_air_exposure": 0.0, "targets": [
            {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:base_stone_overworld"},
             "state": state("minecraft:calcite")}]}},
}

# ------------------------------------------------------------------------------------------------
# Placed features. Helper builders.


def cave(surface, min_y, max_y, count, feature, scan=128, extra_before=None, extra_after=None):
    p = []
    if isinstance(count, int):
        if count > 1:
            p.append({"type": "minecraft:count", "count": count})
    else:
        p.append(count)
    p.append({"type": "minecraft:in_square"})
    p += extra_before or []
    p.append({"type": f"{NS}:cave_surface", "surface": surface, "min_y": min_y, "max_y": max_y, "max_scan": scan})
    p += extra_after or []
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
    return cave(surface, 100, 300, count, feature, scan=200,
                extra_before=[{"type": f"{NS}:start_zone", "radius": radius}])


U = (100, 300)    # upper layer band
A = (-60, 300)    # both layers (regions present in both)
D = (-60, 130)    # deep layer band
SURV_OAK = [{"type": "minecraft:block_predicate_filter",
             "predicate": {"type": "minecraft:would_survive", "state": state("minecraft:oak_sapling", stage=0)}}]

PLACED = {
    # --- local modifications
    "amethyst_geode": ranged(1, -50, 260, "minecraft:amethyst_geode", rarity=6),
    # --- ores
    "ore_copper_upper": ranged(12, 100, 260, f"{NS}:ore_copper_upper"),
    "ore_gilded_blackstone": ranged(8, -60, 300, f"{NS}:ore_gilded_blackstone"),
    "ore_magma": ranged(4, -60, 300, f"{NS}:ore_magma"),
    "ore_calcite_veins": ranged(2, -60, 300, f"{NS}:ore_calcite_veins"),
    # --- underground decoration
    "crystal_spires_floor": cave("floor", *A, 5, f"{NS}:crystal_spire"),
    "crystal_spires_ceiling": cave("ceiling", *A, 3, f"{NS}:crystal_spire"),
    "glow_lichen": cave("wall", *A, 14, "minecraft:glow_lichen"),
    "glow_lichen_sparse": cave("wall", *A, 4, "minecraft:glow_lichen"),
    "sculk_vein": cave("floor", *D, 14, "minecraft:sculk_vein"),
    # --- fluid springs (waterfalls and lavafalls)
    "spring_water": ranged(16, 100, 290, f"{NS}:spring_water"),
    "spring_water_many": ranged(34, 90, 290, f"{NS}:spring_water"),
    "spring_water_deep": ranged(6, -40, 120, f"{NS}:spring_water"),
    "spring_lava": ranged(14, -40, 290, f"{NS}:spring_lava"),
    # --- vegetation
    "rootwild_trees": cave("floor", *U, 5, f"{NS}:rootwild_trees", scan=160, extra_after=SURV_OAK),
    "hanging_roots": cave("ceiling", *U, 4, f"{NS}:hanging_root", scan=160),
    "cave_vines": cave("ceiling", *U, 26, "minecraft:cave_vine", scan=160),
    "cave_vines_sparse": cave("ceiling", *A, 8, "minecraft:cave_vine", scan=160),
    "spore_blossom": cave("ceiling", *U, 3, "minecraft:spore_blossom", scan=160),
    "rooted_azalea": cave("ceiling", *U, 1, "minecraft:rooted_azalea_tree", scan=160),
    "moss_patch": cave("floor", *U, 4, "minecraft:moss_patch", scan=160),
    "moss_patch_ceiling": cave("ceiling", *U, 4, "minecraft:moss_patch_ceiling", scan=160),
    "patch_grass": cave("floor", *U, 8, "minecraft:patch_grass", scan=160),
    "patch_taiga_grass": cave("floor", *U, 4, "minecraft:patch_taiga_grass", scan=160),
    "patch_berry_bush": cave("floor", *U, 2, "minecraft:patch_berry_bush", scan=160),
    "flower_default": cave("floor", *U, 3, "minecraft:flower_default", scan=160),
    "patch_sugar_cane": cave("floor", 95, 160, 6, "minecraft:patch_sugar_cane", scan=80),
    "brown_mushrooms": cave("floor", *A, 2, "minecraft:patch_brown_mushroom", scan=160),
    "red_mushrooms": cave("floor", *A, 2, "minecraft:patch_red_mushroom", scan=160),
    "glowcaps": cave("floor", *A, 4, f"{NS}:glowcap_patch", scan=160),
    "glowcaps_many": cave("floor", *A, 7, f"{NS}:glowcap_patch", scan=160),
    "glowcaps_sparse": cave("floor", *A, 1, f"{NS}:glowcap_patch", scan=160),
    "huge_mushrooms": cave("floor", *U, 5, f"{NS}:huge_mushrooms", scan=160),
    "warped_fungi": cave("floor", *U, 4, "minecraft:warped_fungus", scan=160),
    "crimson_fungi": cave("floor", *U, 4, "minecraft:crimson_fungus", scan=160),
    "warped_vegetation": cave("floor", *U, 3, "minecraft:warped_forest_vegetation", scan=160),
    "crimson_vegetation": cave("floor", *U, 3, "minecraft:crimson_forest_vegetation", scan=160),
    "nether_sprouts": cave("floor", *U, 2, "minecraft:nether_sprouts", scan=160),
    "twisting_vines": cave("floor", *U, 2, "minecraft:twisting_vines", scan=160),
    "weeping_vines": cave("ceiling", *U, 8, "minecraft:weeping_vines", scan=160),
    "seagrass": cave("water_floor", 40, 112, 10, f"{NS}:seagrass", scan=80),
    "kelp": cave("water_floor", 40, 112, 6, f"{NS}:kelp", scan=80),
    "sea_pickles": cave("water_floor", 40, 112, 3, f"{NS}:sea_pickle", scan=80),
    "lily_pads": cave("water_surface", 113, 113, 3, f"{NS}:lily_pad", scan=2),
    "pointed_dripstone": cave("ceiling", *A, 10, "minecraft:pointed_dripstone", scan=160),
    "large_dripstone": cave("floor", *A, 2, "minecraft:large_dripstone", scan=160),
    "dripstone_cluster": cave("floor", *A, 2, "minecraft:dripstone_cluster", scan=160),
    "small_basalt_columns": cave("floor", *A, 3, "minecraft:small_basalt_columns", scan=160),
    "large_basalt_columns": cave("floor", *A, {"type": "minecraft:rarity_filter", "chance": 2}, "minecraft:large_basalt_columns", scan=160),
    "basalt_pillars": cave("ceiling", *A, {"type": "minecraft:rarity_filter", "chance": 4}, "minecraft:basalt_pillar", scan=40),
    "lava_deltas": cave("floor", *A, 2, "minecraft:delta", scan=160),
    "glowstone": cave("ceiling", *A, 4, "minecraft:glowstone_extra", scan=160),
    "luminite_clusters_floor": cave("floor", *A, 8, f"{NS}:luminite_cluster_up", scan=160),
    "luminite_clusters_ceiling": cave("ceiling", *A, 6, f"{NS}:luminite_cluster_down", scan=160),
    "luminite_clusters_rare": cave("floor", *D, 1, f"{NS}:luminite_cluster_up", scan=160),
    "amethyst_clusters": cave("floor", *A, 4, f"{NS}:amethyst_cluster_up", scan=160),
    # --- starting cavern guarantees
    "start_trees": start(3, f"{NS}:start_trees"),
    "start_berries": start(3, "minecraft:patch_berry_bush"),
    "start_grass": start(4, "minecraft:patch_grass"),
    "start_mushrooms": start(2, "minecraft:patch_brown_mushroom"),
    "start_ore_outcrops": start(2, f"{NS}:ore_outcrop", radius=64),
    "start_glowcaps": start(8, f"{NS}:glowcap_patch", radius=88),
    "start_cave_vines": start(12, "minecraft:cave_vine", surface="ceiling", radius=88),
    "start_sugar_cane": start(3, "minecraft:patch_sugar_cane", radius=60),
}
# The amethyst geode placement uses its own rarity; vanilla geodes are placed by rarity only.

# Global order per generation step (index = GenerationStep.Feature ordinal).
STEP_ORDER = {
    2: [f"{NS}:amethyst_geode"],
    3: ["minecraft:monster_room", "minecraft:monster_room_deep"],
    6: ["minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_granite_upper", "minecraft:ore_granite_lower",
        "minecraft:ore_diorite_upper", "minecraft:ore_diorite_lower", "minecraft:ore_andesite_upper",
        "minecraft:ore_andesite_lower", "minecraft:ore_tuff", "minecraft:ore_coal_upper", "minecraft:ore_coal_lower",
        "minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_gold",
        "minecraft:ore_gold_lower", "minecraft:ore_redstone", "minecraft:ore_redstone_lower", "minecraft:ore_diamond",
        "minecraft:ore_diamond_medium", "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried",
        "minecraft:ore_lapis", "minecraft:ore_lapis_buried", "minecraft:ore_copper",
        f"{NS}:ore_copper_upper", f"{NS}:ore_gilded_blackstone", f"{NS}:ore_magma", f"{NS}:ore_calcite_veins"],
    7: [f"{NS}:crystal_spires_floor", f"{NS}:crystal_spires_ceiling", f"{NS}:glow_lichen",
        f"{NS}:glow_lichen_sparse", f"{NS}:sculk_vein"],
    8: [f"{NS}:spring_water", f"{NS}:spring_water_many", f"{NS}:spring_water_deep", f"{NS}:spring_lava"],
    9: [f"{NS}:start_ore_outcrops", f"{NS}:start_trees", f"{NS}:rootwild_trees", f"{NS}:huge_mushrooms",
        f"{NS}:warped_fungi", f"{NS}:crimson_fungi", f"{NS}:small_basalt_columns", f"{NS}:large_basalt_columns",
        f"{NS}:basalt_pillars", f"{NS}:lava_deltas", f"{NS}:large_dripstone", f"{NS}:dripstone_cluster",
        f"{NS}:pointed_dripstone", f"{NS}:hanging_roots", f"{NS}:rooted_azalea", f"{NS}:moss_patch",
        f"{NS}:moss_patch_ceiling", f"{NS}:warped_vegetation", f"{NS}:crimson_vegetation", f"{NS}:nether_sprouts",
        f"{NS}:twisting_vines", f"{NS}:weeping_vines", f"{NS}:start_grass", f"{NS}:patch_grass",
        f"{NS}:patch_taiga_grass", f"{NS}:start_berries", f"{NS}:patch_berry_bush", f"{NS}:flower_default",
        f"{NS}:start_sugar_cane", f"{NS}:patch_sugar_cane", f"{NS}:start_mushrooms", f"{NS}:brown_mushrooms",
        f"{NS}:red_mushrooms", f"{NS}:start_glowcaps", f"{NS}:glowcaps", f"{NS}:glowcaps_many",
        f"{NS}:glowcaps_sparse", f"{NS}:start_cave_vines", f"{NS}:cave_vines", f"{NS}:cave_vines_sparse",
        f"{NS}:spore_blossom", f"{NS}:seagrass", f"{NS}:kelp", f"{NS}:sea_pickles", f"{NS}:lily_pads",
        f"{NS}:glowstone", f"{NS}:luminite_clusters_floor", f"{NS}:luminite_clusters_ceiling",
        f"{NS}:luminite_clusters_rare", f"{NS}:amethyst_clusters"],
}

COMMON_ORES = ["minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_granite_upper",
               "minecraft:ore_granite_lower", "minecraft:ore_diorite_upper", "minecraft:ore_diorite_lower",
               "minecraft:ore_andesite_upper", "minecraft:ore_andesite_lower", "minecraft:ore_tuff",
               "minecraft:ore_coal_upper", "minecraft:ore_coal_lower", "minecraft:ore_iron_upper",
               "minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_gold",
               "minecraft:ore_gold_lower", "minecraft:ore_redstone", "minecraft:ore_redstone_lower",
               "minecraft:ore_diamond", "minecraft:ore_diamond_medium", "minecraft:ore_diamond_large",
               "minecraft:ore_diamond_buried", "minecraft:ore_lapis", "minecraft:ore_lapis_buried",
               "minecraft:ore_copper", f"{NS}:ore_copper_upper"]


def mob(type_, weight, lo, hi):
    return {"type": type_, "weight": weight, "minCount": lo, "maxCount": hi}


MONSTERS = [mob("minecraft:spider", 80, 4, 4), mob("minecraft:zombie", 80, 4, 4),
            mob("minecraft:skeleton", 80, 4, 4), mob("minecraft:creeper", 70, 4, 4),
            mob("minecraft:slime", 40, 4, 4), mob("minecraft:enderman", 6, 1, 4),
            mob("minecraft:witch", 4, 1, 1), mob("minecraft:zombie_villager", 4, 1, 1)]

BIOMES = {
    "rootwild": {
        "temperature": 0.7, "downfall": 0.8,
        "effects": {"fog_color": 0x3B5A3A, "sky_color": 0x3B5A3A, "water_color": 0x3F76E4, "water_fog_color": 0x062A3A,
                    "grass_color": 0x6FB04A, "foliage_color": 0x4F9A3A,
                    "particle": {"options": {"type": "minecraft:spore_blossom_air"}, "probability": 0.0025},
                    "music": {"sound": "minecraft:music.overworld.lush_caves", "min_delay": 12000, "max_delay": 24000,
                              "replace_current_music": False}},
        "spawners": {"monster": MONSTERS, "creature": [mob("minecraft:sheep", 12, 2, 4), mob("minecraft:cow", 8, 2, 3),
                                                     mob("minecraft:pig", 10, 2, 3), mob("minecraft:chicken", 10, 2, 4),
                                                     mob("minecraft:rabbit", 4, 2, 3)],
                     "ambient": [mob("minecraft:bat", 10, 8, 8)],
                     "water_ambient": [mob("minecraft:salmon", 5, 1, 4)]},
        "features": {
            2: [], 3: ["minecraft:monster_room", "minecraft:monster_room_deep"],
            6: COMMON_ORES,
            7: [f"{NS}:glow_lichen_sparse"],
            8: [f"{NS}:spring_water"],
            9: [f"{NS}:start_ore_outcrops", f"{NS}:start_trees", f"{NS}:rootwild_trees", f"{NS}:hanging_roots",
                f"{NS}:rooted_azalea", f"{NS}:moss_patch", f"{NS}:moss_patch_ceiling", f"{NS}:start_grass",
                f"{NS}:patch_grass", f"{NS}:patch_taiga_grass", f"{NS}:start_berries", f"{NS}:patch_berry_bush",
                f"{NS}:flower_default", f"{NS}:start_sugar_cane", f"{NS}:patch_sugar_cane", f"{NS}:start_mushrooms",
                f"{NS}:brown_mushrooms", f"{NS}:red_mushrooms", f"{NS}:start_glowcaps", f"{NS}:glowcaps",
                f"{NS}:start_cave_vines", f"{NS}:cave_vines", f"{NS}:spore_blossom", f"{NS}:seagrass", f"{NS}:lily_pads"],
        }},
    "fungal_expanse": {
        "temperature": 0.8, "downfall": 0.9,
        "effects": {"fog_color": 0x45305E, "sky_color": 0x45305E, "water_color": 0x5A6BC0, "water_fog_color": 0x1A1030,
                    "grass_color": 0x8AA855, "foliage_color": 0x7A9A45,
                    "particle": {"options": {"type": "minecraft:warped_spore"}, "probability": 0.006},
                    "music": {"sound": "minecraft:music.nether.warped_forest", "min_delay": 12000, "max_delay": 24000,
                              "replace_current_music": False}},
        "spawners": {"monster": MONSTERS, "creature": [mob("minecraft:mooshroom", 8, 2, 3)],
                     "ambient": [mob("minecraft:bat", 10, 8, 8)]},
        "features": {
            3: ["minecraft:monster_room"], 6: COMMON_ORES, 7: [f"{NS}:glow_lichen_sparse"], 8: [f"{NS}:spring_water"],
            9: [f"{NS}:huge_mushrooms", f"{NS}:warped_fungi", f"{NS}:crimson_fungi", f"{NS}:moss_patch",
                f"{NS}:warped_vegetation", f"{NS}:crimson_vegetation", f"{NS}:nether_sprouts", f"{NS}:twisting_vines",
                f"{NS}:weeping_vines", f"{NS}:brown_mushrooms", f"{NS}:red_mushrooms", f"{NS}:glowcaps_many",
                f"{NS}:cave_vines_sparse", f"{NS}:seagrass"],
        }},
    "flooded_depths": {
        "temperature": 0.6, "downfall": 1.0,
        "effects": {"fog_color": 0x20405E, "sky_color": 0x20405E, "water_color": 0x2E86D0, "water_fog_color": 0x0A2A48,
                    "grass_color": 0x5DA56A, "foliage_color": 0x4A9A6A,
                    "music": {"sound": "minecraft:music.overworld.dripstone_caves", "min_delay": 12000, "max_delay": 24000,
                              "replace_current_music": False}},
        "spawners": {"monster": MONSTERS, "creature": [mob("minecraft:frog", 6, 2, 4)],
                     "ambient": [mob("minecraft:bat", 10, 8, 8)],
                     "water_creature": [mob("minecraft:squid", 4, 1, 3)],
                     "underground_water_creature": [mob("minecraft:glow_squid", 10, 2, 4)],
                     "axolotls": [mob("minecraft:axolotl", 10, 4, 6)],
                     "water_ambient": [mob("minecraft:cod", 10, 3, 6), mob("minecraft:salmon", 8, 1, 5),
                                       mob("minecraft:tropical_fish", 4, 4, 8)]},
        "features": {
            3: ["minecraft:monster_room"], 6: COMMON_ORES, 7: [f"{NS}:glow_lichen"], 8: [f"{NS}:spring_water_many"],
            9: [f"{NS}:large_dripstone", f"{NS}:dripstone_cluster", f"{NS}:pointed_dripstone", f"{NS}:moss_patch",
                f"{NS}:patch_grass", f"{NS}:patch_sugar_cane", f"{NS}:glowcaps", f"{NS}:cave_vines_sparse",
                f"{NS}:seagrass", f"{NS}:kelp", f"{NS}:sea_pickles", f"{NS}:lily_pads"],
        }},
    "ashen_hollows": {
        "temperature": 2.0, "downfall": 0.0,
        "effects": {"fog_color": 0x5A2A1C, "sky_color": 0x5A2A1C, "water_color": 0x3F76E4, "water_fog_color": 0x050533,
                    "grass_color": 0x9A8A50, "foliage_color": 0x8A7A40,
                    "particle": {"options": {"type": "minecraft:ash"}, "probability": 0.03},
                    "mood_sound": {"sound": "minecraft:ambient.basalt_deltas.mood", "tick_delay": 6000,
                                   "block_search_extent": 8, "offset": 2.0},
                    "additions_sound": {"sound": "minecraft:ambient.basalt_deltas.additions", "tick_chance": 0.0111},
                    "music": {"sound": "minecraft:music.nether.basalt_deltas", "min_delay": 12000, "max_delay": 24000,
                              "replace_current_music": False}},
        "spawners": {"monster": MONSTERS + [mob("minecraft:magma_cube", 30, 2, 4)], "ambient": []},
        "features": {
            3: ["minecraft:monster_room", "minecraft:monster_room_deep"],
            6: COMMON_ORES + [f"{NS}:ore_gilded_blackstone", f"{NS}:ore_magma"],
            8: [f"{NS}:spring_lava"],
            9: [f"{NS}:small_basalt_columns", f"{NS}:large_basalt_columns", f"{NS}:basalt_pillars",
                f"{NS}:lava_deltas", f"{NS}:glowcaps_sparse", f"{NS}:glowstone"],
        }},
    "crystal_deep": {
        "temperature": 0.3, "downfall": 0.3,
        "effects": {"fog_color": 0x1E4A5C, "sky_color": 0x1E4A5C, "water_color": 0x4FB8D8, "water_fog_color": 0x0A3040,
                    "grass_color": 0x6AB0A0, "foliage_color": 0x5AA090,
                    "particle": {"options": {"type": "minecraft:end_rod"}, "probability": 0.0015},
                    "music": {"sound": "minecraft:music.overworld.grove", "min_delay": 12000, "max_delay": 24000,
                              "replace_current_music": False}},
        "spawners": {"monster": MONSTERS, "ambient": [mob("minecraft:bat", 6, 8, 8)],
                     "underground_water_creature": [mob("minecraft:glow_squid", 6, 2, 4)]},
        "features": {
            2: [f"{NS}:amethyst_geode"], 3: ["minecraft:monster_room"], 6: COMMON_ORES + [f"{NS}:ore_calcite_veins"],
            7: [f"{NS}:crystal_spires_floor", f"{NS}:crystal_spires_ceiling", f"{NS}:glow_lichen"],
            8: [f"{NS}:spring_water", f"{NS}:spring_water_deep"],
            9: [f"{NS}:pointed_dripstone", f"{NS}:glowcaps_sparse", f"{NS}:luminite_clusters_floor",
                f"{NS}:luminite_clusters_ceiling", f"{NS}:amethyst_clusters"],
        }},
    "the_abyss": {
        "temperature": 0.5, "downfall": 0.2,
        "effects": {"fog_color": 0x0C0C14, "sky_color": 0x0C0C14, "water_color": 0x3F4A6A, "water_fog_color": 0x050510,
                    "grass_color": 0x4A5A4A, "foliage_color": 0x3A4A3A,
                    "particle": {"options": {"type": "minecraft:ash"}, "probability": 0.01},
                    "music": {"sound": "minecraft:music.overworld.deep_dark", "min_delay": 12000, "max_delay": 24000,
                              "replace_current_music": False}},
        "spawners": {"monster": MONSTERS + [mob("minecraft:cave_spider", 20, 2, 4)],
                     "ambient": [mob("minecraft:bat", 10, 8, 8)]},
        "features": {
            3: ["minecraft:monster_room", "minecraft:monster_room_deep"], 6: COMMON_ORES,
            7: [f"{NS}:glow_lichen_sparse", f"{NS}:sculk_vein"], 8: [f"{NS}:spring_lava", f"{NS}:spring_water_deep"],
            9: [f"{NS}:large_dripstone", f"{NS}:pointed_dripstone", f"{NS}:luminite_clusters_rare"],
        }},
}


def biome_json(name, spec):
    steps = [[] for _ in range(11)]
    for step, feats in spec["features"].items():
        order = STEP_ORDER.get(step)
        if order is None:
            raise SystemExit(f"step {step} has no global order")
        for f in feats:
            if f not in order:
                raise SystemExit(f"{f} missing from global order of step {step}")
        steps[step] = sorted(feats, key=order.index)
    effects = {"mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8,
                              "offset": 2.0}}
    effects.update(spec["effects"])
    spawners = {k: [] for k in ["monster", "creature", "ambient", "axolotls", "underground_water_creature",
                                "water_creature", "water_ambient", "misc"]}
    spawners.update(spec["spawners"])
    return {
        "has_precipitation": False,
        "temperature": spec["temperature"],
        "downfall": spec["downfall"],
        "effects": effects,
        "spawners": spawners,
        "spawn_costs": {},
        "carvers": {},
        "features": steps,
    }


def main():
    # Clean generated folders so removed entries do not linger.
    for sub in ["worldgen/biome", "worldgen/configured_feature", "worldgen/placed_feature"]:
        shutil.rmtree(os.path.join(DATA, NS, sub), ignore_errors=True)
    for name, cf in CONFIGURED.items():
        write(f"{NS}/worldgen/configured_feature/{name}.json", cf)
    for name, pf in PLACED.items():
        write(f"{NS}/worldgen/placed_feature/{name}.json", pf)
    for name, spec in BIOMES.items():
        write(f"{NS}/worldgen/biome/{name}.json", biome_json(name, spec))

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
    # Biome tags for future systems
    write(f"{NS}/tags/worldgen/biome/is_underdeep.json", {"replace": False,
                                                           "values": [f"{NS}:{b}" for b in BIOMES]})
    print(f"wrote {len(CONFIGURED)} configured, {len(PLACED)} placed, {len(BIOMES)} biomes")


if __name__ == "__main__":
    main()
