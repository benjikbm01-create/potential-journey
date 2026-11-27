#!/usr/bin/env python3
"""Generates the Stage 6 economy data (goods and production recipes) as JSON resources.

Every good is an actual registered item (vanilla or Underdeep); the Java loader validates the ids and skips
unknown ones. Base values are in emeralds per unit. Run from the repository root:
    python3 tools/economy/generate_economy.py
"""
import json, os

OUT = "src/main/resources/data/underdeep/economy"

# (item id, category, base value in emeralds per unit)
GOODS = [
    # grain and crops
    ("underdeep:glowgrain", "GRAIN", 0.08), ("underdeep:mire_rice", "GRAIN", 0.08), ("minecraft:wheat", "GRAIN", 0.07),
    ("underdeep:sunbean", "PRODUCE", 0.08), ("underdeep:duskroot", "PRODUCE", 0.07), ("underdeep:frost_turnip", "PRODUCE", 0.07),
    ("underdeep:lake_chestnut", "PRODUCE", 0.08), ("underdeep:emberpepper", "PRODUCE", 0.1), ("minecraft:potato", "PRODUCE", 0.05),
    ("minecraft:carrot", "PRODUCE", 0.05), ("underdeep:duskberries", "PRODUCE", 0.06), ("underdeep:cliffberries", "PRODUCE", 0.06),
    ("underdeep:snowberries", "PRODUCE", 0.06), ("underdeep:honeyfruit", "PRODUCE", 0.12), ("underdeep:sea_grapes", "PRODUCE", 0.08),
    ("underdeep:briar_fruit", "PRODUCE", 0.07), ("underdeep:inkcap", "PRODUCE", 0.06), ("underdeep:puffcap", "PRODUCE", 0.06),
    ("underdeep:rimecap", "PRODUCE", 0.08), ("underdeep:cave_truffle", "PRODUCE", 0.25), ("minecraft:brown_mushroom", "PRODUCE", 0.05),
    ("minecraft:red_mushroom", "PRODUCE", 0.05),
    # meat and fish
    ("underdeep:game_meat", "MEAT", 0.15), ("underdeep:shellmeat", "MEAT", 0.14), ("underdeep:fish_fillet", "FISH", 0.12),
    ("minecraft:cod", "FISH", 0.1),
    # prepared food
    ("underdeep:glowgrain_bread", "PREPARED_FOOD", 0.3), ("minecraft:bread", "PREPARED_FOOD", 0.25),
    ("underdeep:baked_duskroot", "PREPARED_FOOD", 0.18), ("underdeep:baked_turnip", "PREPARED_FOOD", 0.18),
    ("underdeep:roasted_chestnut", "PREPARED_FOOD", 0.18), ("underdeep:roasted_inkcap", "PREPARED_FOOD", 0.15),
    ("underdeep:roasted_truffle", "PREPARED_FOOD", 0.5), ("underdeep:cooked_game_meat", "PREPARED_FOOD", 0.3),
    ("underdeep:cooked_fish_fillet", "PREPARED_FOOD", 0.28), ("underdeep:cooked_shellmeat", "PREPARED_FOOD", 0.28),
    ("underdeep:cavern_stew", "PREPARED_FOOD", 0.6), ("underdeep:sunbean_stew", "PREPARED_FOOD", 0.6),
    ("underdeep:rice_bowl", "PREPARED_FOOD", 0.5), ("minecraft:baked_potato", "PREPARED_FOOD", 0.15),
    ("minecraft:cooked_cod", "PREPARED_FOOD", 0.22),
    # timber and lumber
    ("underdeep:duskoak_log", "TIMBER", 0.12), ("underdeep:lumenwood_log", "TIMBER", 0.14), ("underdeep:mirewillow_log", "TIMBER", 0.12),
    ("underdeep:frostfir_log", "TIMBER", 0.12), ("underdeep:goldwood_log", "TIMBER", 0.18), ("underdeep:spirebark_log", "TIMBER", 0.12),
    ("underdeep:bloodthorn_log", "TIMBER", 0.14), ("underdeep:cinderbark_log", "TIMBER", 0.2),
    ("underdeep:duskoak_planks", "LUMBER", 0.04), ("underdeep:lumenwood_planks", "LUMBER", 0.045),
    ("underdeep:mirewillow_planks", "LUMBER", 0.04), ("underdeep:frostfir_planks", "LUMBER", 0.04),
    ("underdeep:goldwood_planks", "LUMBER", 0.055), ("underdeep:spirebark_planks", "LUMBER", 0.04),
    ("underdeep:bloodthorn_planks", "LUMBER", 0.045), ("minecraft:stick", "LUMBER", 0.012),
    # stone and building materials
    ("minecraft:cobblestone", "STONE", 0.02), ("minecraft:cobbled_deepslate", "STONE", 0.02), ("minecraft:basalt", "STONE", 0.025),
    ("minecraft:stone", "STONE", 0.03), ("minecraft:sand", "STONE", 0.02),
    ("minecraft:stone_bricks", "BUILDING", 0.05), ("minecraft:deepslate_bricks", "BUILDING", 0.055),
    ("minecraft:glass", "BUILDING", 0.06), ("minecraft:glass_pane", "BUILDING", 0.025), ("minecraft:torch", "BUILDING", 0.03),
    ("minecraft:lantern", "BUILDING", 0.45), ("minecraft:glass_bottle", "BUILDING", 0.03),
    # ores, metals, fuel
    ("minecraft:raw_iron", "ORE", 0.25), ("minecraft:raw_copper", "ORE", 0.1), ("minecraft:raw_gold", "ORE", 0.5),
    ("underdeep:raw_umbralite", "ORE", 0.7), ("underdeep:raw_starsilver", "ORE", 1.0), ("minecraft:redstone", "ORE", 0.08),
    ("minecraft:iron_ingot", "METAL", 0.4), ("minecraft:copper_ingot", "METAL", 0.15), ("minecraft:gold_ingot", "METAL", 0.8),
    ("underdeep:umbralite_ingot", "METAL", 1.2), ("underdeep:starsilver_ingot", "METAL", 1.6), ("minecraft:iron_nugget", "METAL", 0.045),
    ("minecraft:coal", "FUEL", 0.1), ("minecraft:charcoal", "FUEL", 0.08), ("underdeep:creature_oil", "FUEL", 0.15),
    ("underdeep:sulfur", "FUEL", 0.08), ("underdeep:niter", "FUEL", 0.08),
    # fibres and animal products
    ("minecraft:string", "FIBER", 0.05), ("underdeep:silk_strand", "FIBER", 0.15), ("underdeep:voidroot_fiber", "FIBER", 0.12),
    ("minecraft:white_wool", "FIBER", 0.2), ("underdeep:fur_tuft", "FIBER", 0.1),
    ("underdeep:hide", "ANIMAL_PRODUCT", 0.2), ("minecraft:leather", "ANIMAL_PRODUCT", 0.3), ("underdeep:horn", "ANIMAL_PRODUCT", 0.3),
    ("underdeep:scale", "ANIMAL_PRODUCT", 0.25), ("underdeep:plume", "ANIMAL_PRODUCT", 0.2), ("underdeep:shell_fragment", "ANIMAL_PRODUCT", 0.1),
    ("minecraft:bone", "ANIMAL_PRODUCT", 0.03), ("underdeep:glow_gland", "ANIMAL_PRODUCT", 0.4), ("underdeep:venom_gland", "ANIMAL_PRODUCT", 0.5),
    ("underdeep:snapjaw_fang", "ANIMAL_PRODUCT", 0.3), ("underdeep:creature_egg", "ANIMAL_PRODUCT", 0.2),
    # herbs and medicine
    ("underdeep:mendleaf_sprig", "HERB", 0.15), ("underdeep:sunleaf_blossom", "HERB", 0.2), ("underdeep:moonpetal_petal", "HERB", 0.25),
    ("underdeep:emberwort_root", "HERB", 0.2), ("underdeep:bogwort_leaf", "HERB", 0.12), ("underdeep:siltwort_frond", "HERB", 0.12),
    ("underdeep:umbral_petal", "HERB", 0.35),
    ("underdeep:mendleaf_poultice", "MEDICINE", 0.6), ("underdeep:sunleaf_salve", "MEDICINE", 0.8), ("underdeep:moonpetal_draught", "MEDICINE", 1.0),
    ("underdeep:emberwort_tonic", "MEDICINE", 0.9), ("underdeep:antidote_tincture", "MEDICINE", 1.2),
    # gems, decorative, specialty
    ("underdeep:jasper", "GEM", 0.4), ("underdeep:malachite", "GEM", 0.5), ("underdeep:cinnabar", "GEM", 0.4), ("underdeep:galvanite", "GEM", 0.6),
    ("underdeep:cryolite", "GEM", 0.5), ("underdeep:prismatic_opal", "GEM", 2.0), ("underdeep:glimmer_shard", "GEM", 0.6),
    ("minecraft:amethyst_shard", "GEM", 0.3), ("underdeep:pearl", "GEM", 1.0), ("minecraft:lapis_lazuli", "GEM", 0.12),
    ("underdeep:jasper_block", "DECORATIVE", 4.0), ("underdeep:malachite_block", "DECORATIVE", 5.0), ("underdeep:cryolite_block", "DECORATIVE", 5.0),
    ("underdeep:galvanite_block", "DECORATIVE", 6.0), ("underdeep:fossil_fragment", "DECORATIVE", 0.5), ("minecraft:flower_pot", "DECORATIVE", 0.1),
    ("underdeep:amber_glob", "SPECIALTY", 0.35), ("underdeep:starlily", "SPECIALTY", 0.8), ("underdeep:emberite", "SPECIALTY", 0.35),
    ("underdeep:crystal_husk", "SPECIALTY", 0.4),
    # tools and equipment
    ("minecraft:iron_pickaxe", "TOOLS", 2.0), ("minecraft:iron_axe", "TOOLS", 2.0), ("minecraft:iron_shovel", "TOOLS", 0.9),
    ("minecraft:iron_hoe", "TOOLS", 1.2), ("minecraft:shears", "TOOLS", 1.0), ("minecraft:fishing_rod", "TOOLS", 0.5),
    ("minecraft:bucket", "TOOLS", 1.4), ("minecraft:stone_pickaxe", "TOOLS", 0.2),
    ("minecraft:iron_sword", "EQUIPMENT", 2.0), ("minecraft:shield", "EQUIPMENT", 1.2), ("minecraft:bow", "EQUIPMENT", 1.0),
    ("minecraft:iron_helmet", "EQUIPMENT", 2.2), ("minecraft:iron_chestplate", "EQUIPMENT", 3.5), ("minecraft:leather_chestplate", "EQUIPMENT", 1.5),
    ("minecraft:lead", "EQUIPMENT", 0.3), ("underdeep:harness", "EQUIPMENT", 2.0),
    # luxuries
    ("minecraft:spyglass", "LUXURY", 2.0), ("minecraft:compass", "LUXURY", 1.6), ("minecraft:clock", "LUXURY", 2.6),
    ("minecraft:book", "LUXURY", 0.6), ("minecraft:cake", "LUXURY", 1.0), ("minecraft:golden_apple", "LUXURY", 6.0),
]

LOGS = ["duskoak", "lumenwood", "mirewillow", "frostfir", "goldwood", "spirebark", "bloodthorn"]


def R(id, kind, profs, inputs, outputs, hours, per_day, skill, workplace=None, habitats=None, regions=None,
      source=None, pool=None, tool=None, fuel=0.0, byproducts=None):
    return {"id": id, "kind": kind, "professions": profs, "workplace": workplace or [], "inputs": inputs, "outputs": outputs,
            "byproducts": byproducts or {}, "hours": hours, "maxPerDay": per_day, "skill": skill, "habitats": habitats or [],
            "regions": regions or [], "source": source or [], "pool": pool or "", "tool": tool or "", "fuel": fuel}


RECIPES = [
    # ---------------- gathering: limited by the region's ecology and the settlement's sustainability pools
    R("farm_staple", "gather", ["farmer"], {}, {"@CIVCROP": 4}, 2, 4, "AGRICULTURE", workplace=["FARM"], pool="crops", tool="@TOOLS"),
    R("farm_glowgrain", "gather", ["farmer"], {}, {"underdeep:glowgrain": 3}, 2, 3, "AGRICULTURE", workplace=["FARM"], pool="crops",
      regions=["STARLIGHT_CAVERNS", "GOLDEN_GROVE", "ROOTWILD"]),
    R("forage_fruit", "gather", ["farmer", "herbalist", "forester", "cook"], {}, {"@SPECIES": 3}, 2, 3, "AGRICULTURE",
      source=["SHRUB", "VINE"], pool="forage"),
    R("forage_fungi", "gather", ["mycologist", "herbalist", "cook", "farmer"], {}, {"underdeep:inkcap": 2, "underdeep:puffcap": 1}, 2, 3,
      "AGRICULTURE", habitats=["FUNGAL_GROVE", "FOREST", "WETLAND", "DARK_DEPTHS"], pool="fungi"),
    R("forage_rimecap", "gather", ["mycologist", "herbalist", "hunter"], {}, {"underdeep:rimecap": 2}, 2, 2, "AGRICULTURE",
      regions=["EVERFROST"], pool="fungi"),
    R("dig_truffles", "gather", ["mycologist", "farmer"], {}, {"underdeep:cave_truffle": 1}, 3, 2, "AGRICULTURE",
      regions=["FUNGAL_EXPANSE", "ROOTWILD"], pool="fungi"),
    R("gather_herbs", "gather", ["herbalist", "healer", "alchemist", "mycologist"], {}, {"@SPECIES": 2}, 2, 3, "MEDICINE",
      source=["HERB", "FLOWER"], pool="herbs"),
    R("fell_trees", "gather", ["forester", "carpenter"], {}, {"@SPECIES": 3}, 3, 3, "FORESTRY", source=["TREE"], pool="wood", tool="@TOOLS"),
    R("tap_resin", "gather", ["forester", "artisan"], {}, {"underdeep:amber_glob": 1}, 3, 2, "FORESTRY", regions=["GOLDEN_GROVE"], pool="wood"),
    R("fish", "gather", ["fisher", "boatwright"], {}, {"underdeep:fish_fillet": 2, "minecraft:cod": 1}, 2, 4, "FISHING",
      habitats=["OPEN_WATER", "SUBMERGED", "WETLAND"], pool="fish", tool="minecraft:fishing_rod"),
    R("gather_shellfish", "gather", ["fisher", "navigator"], {}, {"underdeep:shellmeat": 2, "underdeep:shell_fragment": 1}, 2, 3, "FISHING",
      habitats=["SUBMERGED", "OPEN_WATER"], pool="fish"),
    R("dive_pearls", "gather", ["fisher", "gemcutter"], {}, {"underdeep:pearl": 1}, 4, 1, "FISHING", regions=["SUNKEN_GARDENS", "FLOODED_DEPTHS"],
      pool="minerals"),
    R("hunt_game", "gather", ["hunter"], {}, {"underdeep:game_meat": 2, "underdeep:hide": 1, "minecraft:bone": 1}, 3, 2, "HUNTING", pool="game",
      byproducts={"underdeep:horn": 0.3}),
    R("trap_small_game", "gather", ["hunter", "rancher"], {}, {"underdeep:game_meat": 1, "underdeep:fur_tuft": 1}, 3, 2, "HUNTING", pool="game"),
    R("tend_herd", "gather", ["rancher"], {}, {"underdeep:fur_tuft": 2, "underdeep:creature_egg": 1}, 3, 2, "ANIMAL_CARE",
      workplace=["PEN", "BARN"], pool="ranch", byproducts={"underdeep:plume": 0.3}),
    R("render_oil", "gather", ["hunter", "rancher"], {}, {"underdeep:creature_oil": 1}, 3, 1, "HUNTING", pool="game"),
    R("quarry_stone", "gather", ["stonemason", "miner"], {}, {"minecraft:cobblestone": 8}, 2, 4, "MINING", pool="stone", tool="@TOOLS"),
    R("quarry_basalt", "gather", ["stonemason", "miner"], {}, {"minecraft:basalt": 6}, 2, 3, "MINING", regions=["ASHEN_HOLLOWS", "THE_ABYSS"],
      pool="stone"),
    R("dig_sand", "gather", ["stonemason", "miner", "smelter"], {}, {"minecraft:sand": 6}, 2, 3, "MINING",
      habitats=["OPEN_WATER", "SUBMERGED", "WETLAND"], pool="stone"),
    R("mine_coal", "gather", ["miner"], {}, {"minecraft:coal": 3}, 3, 3, "MINING", pool="ore", tool="@TOOLS"),
    R("mine_iron", "gather", ["miner"], {}, {"minecraft:raw_iron": 2}, 3, 3, "MINING", pool="ore", tool="@TOOLS"),
    R("mine_copper", "gather", ["miner"], {}, {"minecraft:raw_copper": 3, "minecraft:redstone": 1}, 3, 2, "MINING", pool="ore"),
    R("mine_gold", "gather", ["miner"], {}, {"minecraft:raw_gold": 1}, 4, 1, "MINING",
      regions=["GOLDEN_GROVE", "HOLLOW_PEAKS", "ASHEN_HOLLOWS"], pool="ore"),
    R("mine_regional", "gather", ["miner", "gemcutter", "stonemason"], {}, {"@SPECIES": 2}, 4, 2, "MINING", source=["MINERAL"],
      pool="minerals", tool="@TOOLS"),
    R("harvest_crystals", "gather", ["gemcutter", "artisan", "lampwright"], {}, {"@SPECIES": 2, "minecraft:amethyst_shard": 1}, 4, 2, "MINING",
      source=["CRYSTAL"], pool="minerals"),
    R("collect_silk", "gather", ["tailor", "hunter"], {}, {"underdeep:silk_strand": 2}, 3, 2, "HUNTING",
      regions=["CRIMSON_BASIN", "STORMSTONE_CAVERNS", "THE_ABYSS", "ROOTWILD"], pool="game"),
    R("gather_voidroot", "gather", ["tailor", "herbalist"], {}, {"underdeep:voidroot_fiber": 2}, 3, 2, "AGRICULTURE",
      regions=["THE_ABYSS"], pool="herbs"),
    # ---------------- food processing (fuel: any FUEL good)
    R("bake_glowgrain_bread", "process", ["baker", "cook", "innkeeper"], {"underdeep:glowgrain": 3}, {"underdeep:glowgrain_bread": 1}, 0.5, 16,
      "COOKING", fuel=0.1),
    R("bake_bread", "process", ["baker", "cook"], {"minecraft:wheat": 3}, {"minecraft:bread": 1}, 0.5, 16, "COOKING", fuel=0.1),
    R("bake_roots", "process", ["cook", "baker", "innkeeper"], {"underdeep:duskroot": 1}, {"underdeep:baked_duskroot": 1}, 0.3, 20, "COOKING",
      fuel=0.1),
    R("bake_turnips", "process", ["cook", "baker", "innkeeper"], {"underdeep:frost_turnip": 1}, {"underdeep:baked_turnip": 1}, 0.3, 20,
      "COOKING", fuel=0.1),
    R("bake_potatoes", "process", ["cook", "baker"], {"minecraft:potato": 1}, {"minecraft:baked_potato": 1}, 0.3, 20, "COOKING", fuel=0.1),
    R("roast_chestnuts", "process", ["cook", "innkeeper"], {"underdeep:lake_chestnut": 1}, {"underdeep:roasted_chestnut": 1}, 0.3, 20,
      "COOKING", fuel=0.1),
    R("roast_fungi", "process", ["cook", "mycologist", "innkeeper"], {"underdeep:inkcap": 1}, {"underdeep:roasted_inkcap": 1}, 0.3, 20,
      "COOKING", fuel=0.1),
    R("roast_truffles", "process", ["cook", "innkeeper"], {"underdeep:cave_truffle": 1}, {"underdeep:roasted_truffle": 1}, 0.4, 8, "COOKING",
      fuel=0.1),
    R("cook_game", "process", ["cook", "innkeeper", "hunter"], {"underdeep:game_meat": 1}, {"underdeep:cooked_game_meat": 1}, 0.3, 20,
      "COOKING", fuel=0.1),
    R("cook_fish", "process", ["cook", "innkeeper", "fisher"], {"underdeep:fish_fillet": 1}, {"underdeep:cooked_fish_fillet": 1}, 0.3, 20,
      "COOKING", fuel=0.1),
    R("cook_cod", "process", ["cook", "fisher"], {"minecraft:cod": 1}, {"minecraft:cooked_cod": 1}, 0.3, 20, "COOKING", fuel=0.1),
    R("cook_shellmeat", "process", ["cook", "innkeeper"], {"underdeep:shellmeat": 1}, {"underdeep:cooked_shellmeat": 1}, 0.3, 20, "COOKING",
      fuel=0.1),
    R("simmer_cavern_stew", "process", ["cook", "innkeeper"], {"underdeep:inkcap": 1, "underdeep:duskroot": 1, "underdeep:puffcap": 1},
      {"underdeep:cavern_stew": 1}, 0.5, 12, "COOKING", fuel=0.1),
    R("simmer_sunbean_stew", "process", ["cook", "innkeeper"], {"underdeep:sunbean": 2, "underdeep:honeyfruit": 1}, {"underdeep:sunbean_stew": 1},
      0.5, 12, "COOKING", fuel=0.1),
    R("cook_rice_bowl", "process", ["cook", "innkeeper"], {"underdeep:mire_rice": 2, "underdeep:fish_fillet": 1}, {"underdeep:rice_bowl": 1},
      0.5, 12, "COOKING", fuel=0.1),
    R("bake_cake", "process", ["baker"], {"minecraft:wheat": 3, "underdeep:creature_egg": 1, "underdeep:honeyfruit": 2}, {"minecraft:cake": 1},
      1, 2, "COOKING", fuel=0.2),
    # ---------------- wood
    *[R(f"saw_{w}_planks", "process", ["carpenter", "boatwright", "forester"], {f"underdeep:{w}_log": 1}, {f"underdeep:{w}_planks": 4},
        0.25, 32, "CARPENTRY", workplace=["WORKSHOP"]) for w in LOGS],
    R("whittle_sticks", "process", ["carpenter", "boatwright"], {"@LUMBER": 2}, {"minecraft:stick": 4}, 0.1, 32, "CARPENTRY"),
    R("burn_charcoal", "process", ["forester", "smelter"], {"@TIMBER": 1}, {"minecraft:charcoal": 1}, 0.5, 12, "FORESTRY"),
    R("make_flower_pots", "process", ["artisan"], {"minecraft:sand": 3}, {"minecraft:flower_pot": 1}, 0.5, 6, "CRAFTING", fuel=0.1),
    # ---------------- stone and building materials
    R("smelt_stone", "process", ["stonemason", "smelter"], {"minecraft:cobblestone": 1}, {"minecraft:stone": 1}, 0.15, 48, "MASONRY", fuel=0.1),
    R("cut_stone_bricks", "process", ["stonemason"], {"minecraft:stone": 1}, {"minecraft:stone_bricks": 1}, 0.1, 48, "MASONRY"),
    R("cut_deepslate_bricks", "process", ["stonemason"], {"minecraft:cobbled_deepslate": 1}, {"minecraft:deepslate_bricks": 1}, 0.15, 40,
      "MASONRY"),
    R("make_glass", "process", ["smelter", "lampwright", "artisan"], {"minecraft:sand": 1}, {"minecraft:glass": 1}, 0.15, 40, "SMITHING",
      fuel=0.1),
    R("cut_panes", "process", ["lampwright", "artisan"], {"minecraft:glass": 6}, {"minecraft:glass_pane": 16}, 0.3, 12, "CRAFTING"),
    R("blow_bottles", "process", ["lampwright", "alchemist", "brewer"], {"minecraft:glass": 3}, {"minecraft:glass_bottle": 3}, 0.3, 12,
      "CRAFTING"),
    R("make_torches", "process", ["lampwright", "miner"], {"@FUELSOLID": 1, "minecraft:stick": 1}, {"minecraft:torch": 4}, 0.1, 24, "CRAFTING"),
    R("forge_lanterns", "process", ["lampwright"], {"minecraft:iron_nugget": 8, "minecraft:torch": 1}, {"minecraft:lantern": 1}, 0.5, 8,
      "SMITHING"),
    # ---------------- metals
    R("smelt_iron", "process", ["smelter", "blacksmith", "engineer"], {"minecraft:raw_iron": 1}, {"minecraft:iron_ingot": 1}, 0.25, 32,
      "SMITHING", fuel=0.125),
    R("smelt_copper", "process", ["smelter", "engineer"], {"minecraft:raw_copper": 1}, {"minecraft:copper_ingot": 1}, 0.25, 32, "SMITHING",
      fuel=0.125),
    R("smelt_gold", "process", ["smelter", "gemcutter"], {"minecraft:raw_gold": 1}, {"minecraft:gold_ingot": 1}, 0.3, 16, "SMITHING", fuel=0.125),
    R("smelt_umbralite", "process", ["smelter", "blacksmith"], {"underdeep:raw_umbralite": 1}, {"underdeep:umbralite_ingot": 1}, 0.5, 8,
      "SMITHING", fuel=0.25),
    R("smelt_starsilver", "process", ["smelter", "astronomer", "lampwright"], {"underdeep:raw_starsilver": 1}, {"underdeep:starsilver_ingot": 1},
      0.5, 8, "SMITHING", fuel=0.25),
    R("cut_nuggets", "process", ["blacksmith", "lampwright"], {"minecraft:iron_ingot": 1}, {"minecraft:iron_nugget": 9}, 0.1, 16, "SMITHING"),
    # ---------------- tools and equipment
    R("forge_pickaxes", "process", ["blacksmith", "engineer"], {"minecraft:iron_ingot": 3, "minecraft:stick": 2}, {"minecraft:iron_pickaxe": 1},
      1, 4, "SMITHING", workplace=["WORKSHOP"]),
    R("forge_axes", "process", ["blacksmith"], {"minecraft:iron_ingot": 3, "minecraft:stick": 2}, {"minecraft:iron_axe": 1}, 1, 4, "SMITHING",
      workplace=["WORKSHOP"]),
    R("forge_shovels", "process", ["blacksmith"], {"minecraft:iron_ingot": 1, "minecraft:stick": 2}, {"minecraft:iron_shovel": 1}, 0.5, 6,
      "SMITHING"),
    R("forge_hoes", "process", ["blacksmith"], {"minecraft:iron_ingot": 2, "minecraft:stick": 2}, {"minecraft:iron_hoe": 1}, 0.7, 4, "SMITHING"),
    R("forge_shears", "process", ["blacksmith", "tailor"], {"minecraft:iron_ingot": 2}, {"minecraft:shears": 1}, 0.5, 4, "SMITHING"),
    R("forge_buckets", "process", ["blacksmith", "engineer"], {"minecraft:iron_ingot": 3}, {"minecraft:bucket": 1}, 0.6, 4, "SMITHING"),
    R("forge_swords", "process", ["blacksmith"], {"minecraft:iron_ingot": 2, "minecraft:stick": 1}, {"minecraft:iron_sword": 1}, 1, 3,
      "SMITHING", workplace=["WORKSHOP"]),
    R("forge_helmets", "process", ["blacksmith"], {"minecraft:iron_ingot": 5}, {"minecraft:iron_helmet": 1}, 1.5, 2, "SMITHING"),
    R("forge_chestplates", "process", ["blacksmith"], {"minecraft:iron_ingot": 8}, {"minecraft:iron_chestplate": 1}, 2, 1, "SMITHING"),
    R("build_shields", "process", ["carpenter", "blacksmith"], {"@LUMBER": 6, "minecraft:iron_ingot": 1}, {"minecraft:shield": 1}, 1, 3,
      "CARPENTRY"),
    R("craft_bows", "process", ["carpenter", "hunter"], {"minecraft:stick": 3, "minecraft:string": 3}, {"minecraft:bow": 1}, 0.7, 4, "CARPENTRY"),
    R("craft_fishing_rods", "process", ["boatwright", "fisher", "carpenter"], {"minecraft:stick": 3, "minecraft:string": 2},
      {"minecraft:fishing_rod": 1}, 0.4, 6, "CARPENTRY"),
    R("craft_leads", "process", ["tailor", "rancher"], {"minecraft:string": 4}, {"minecraft:lead": 2}, 0.4, 6, "TAILORING"),
    R("knap_stone_picks", "process", ["miner", "stonemason"], {"minecraft:cobblestone": 3, "minecraft:stick": 2}, {"minecraft:stone_pickaxe": 1},
      0.3, 6, "MASONRY"),
    # ---------------- textiles and leather
    R("tan_hides", "process", ["tailor", "hunter"], {"underdeep:hide": 1}, {"minecraft:leather": 1}, 0.5, 12, "TAILORING"),
    R("spin_wool", "process", ["tailor"], {"minecraft:string": 4}, {"minecraft:white_wool": 1}, 0.3, 16, "TAILORING"),
    R("spin_silk", "process", ["tailor"], {"underdeep:silk_strand": 2}, {"minecraft:string": 3}, 0.3, 16, "TAILORING"),
    R("felt_fur", "process", ["tailor"], {"underdeep:fur_tuft": 3}, {"minecraft:white_wool": 1}, 0.4, 12, "TAILORING"),
    R("twist_voidroot", "process", ["tailor"], {"underdeep:voidroot_fiber": 2}, {"minecraft:string": 3}, 0.3, 16, "TAILORING"),
    R("sew_leather_coats", "process", ["tailor"], {"minecraft:leather": 8}, {"minecraft:leather_chestplate": 1}, 1.5, 2, "TAILORING"),
    R("stitch_harnesses", "process", ["tailor", "rancher"], {"minecraft:leather": 4, "minecraft:iron_ingot": 1}, {"underdeep:harness": 1}, 1.5,
      2, "TAILORING"),
    R("bind_books", "process", ["scholar", "cartographer"], {"minecraft:leather": 1, "underdeep:voidroot_fiber": 3}, {"minecraft:book": 1}, 1, 3,
      "RESEARCH"),
    # ---------------- medicine
    R("make_poultices", "process", ["healer", "herbalist", "alchemist"], {"underdeep:mendleaf_sprig": 2}, {"underdeep:mendleaf_poultice": 1},
      0.5, 8, "MEDICINE"),
    R("make_salves", "process", ["healer", "alchemist"], {"underdeep:sunleaf_blossom": 2, "underdeep:creature_oil": 1},
      {"underdeep:sunleaf_salve": 1}, 0.6, 6, "MEDICINE"),
    R("brew_draughts", "process", ["alchemist", "healer", "brewer"], {"underdeep:moonpetal_petal": 2, "minecraft:glass_bottle": 1},
      {"underdeep:moonpetal_draught": 1}, 0.8, 4, "MEDICINE"),
    R("brew_tonics", "process", ["alchemist", "brewer", "healer"], {"underdeep:emberwort_root": 2, "minecraft:glass_bottle": 1},
      {"underdeep:emberwort_tonic": 1}, 0.8, 4, "MEDICINE"),
    R("distil_antidotes", "process", ["alchemist"], {"underdeep:venom_gland": 1, "underdeep:bogwort_leaf": 2, "minecraft:glass_bottle": 1},
      {"underdeep:antidote_tincture": 1}, 1, 3, "MEDICINE"),
    # ---------------- gems, precision craft, research
    R("cut_jasper", "process", ["gemcutter", "stonemason", "artisan"], {"underdeep:jasper": 9}, {"underdeep:jasper_block": 1}, 1, 3, "CRAFTING"),
    R("cut_malachite", "process", ["gemcutter", "artisan"], {"underdeep:malachite": 9}, {"underdeep:malachite_block": 1}, 1, 3, "CRAFTING"),
    R("cut_cryolite", "process", ["gemcutter", "artisan"], {"underdeep:cryolite": 9}, {"underdeep:cryolite_block": 1}, 1, 3, "CRAFTING"),
    R("cut_galvanite", "process", ["gemcutter", "engineer"], {"underdeep:galvanite": 9}, {"underdeep:galvanite_block": 1}, 1, 3, "CRAFTING"),
    R("grind_spyglasses", "process", ["astronomer", "lampwright", "engineer"], {"minecraft:copper_ingot": 2, "minecraft:amethyst_shard": 1},
      {"minecraft:spyglass": 1}, 1.5, 2, "RESEARCH"),
    R("assemble_compasses", "process", ["cartographer", "navigator", "engineer"], {"minecraft:iron_ingot": 4, "minecraft:redstone": 1},
      {"minecraft:compass": 1}, 1.5, 2, "RESEARCH"),
    R("assemble_clocks", "process", ["astronomer", "engineer", "artisan"], {"minecraft:gold_ingot": 4, "minecraft:redstone": 1},
      {"minecraft:clock": 1}, 2, 1, "RESEARCH"),
    R("gild_apples", "process", ["alchemist", "artisan"], {"underdeep:honeyfruit": 1, "minecraft:gold_ingot": 8}, {"minecraft:golden_apple": 1},
      2, 1, "CRAFTING"),
]


def stage7():
    """Stage 7 goods (every new food, drink, medicine and material) and settlement production for them, derived
    from the Stage 7 content definitions so the two never disagree. Buckets of water/milk are not modelled for
    settlements (they have wells and dairy animals); bowls and bottles are assumed reused."""
    import sys
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "assets"))
    import stage7 as s7
    have = {g[0] for g in GOODS}
    mat_cat = {"flour": ("GRAIN", 0.1), "dough": ("SPECIALTY", 0.12), "salt": ("SPECIALTY", 0.05), "butter": ("ANIMAL_PRODUCT", 0.15),
               "vinegar": ("SPECIALTY", 0.12), "spice_blend": ("SPECIALTY", 0.2), "compost": ("SPECIALTY", 0.05),
               "grain_feed": ("ANIMAL_PRODUCT", 0.08), "fungal_feed": ("ANIMAL_PRODUCT", 0.08), "fruit_mash": ("ANIMAL_PRODUCT", 0.08),
               "meat_scraps": ("ANIMAL_PRODUCT", 0.08), "mineral_lick": ("ANIMAL_PRODUCT", 0.1), "fishing_bait": ("SPECIALTY", 0.04)}
    for i, d in s7.F.items():
        iid = "underdeep:" + i
        if iid in have:
            continue
        cat = "FISH" if d["groups"] == "FISH" and d["nutrition"] <= 2 else "PREPARED_FOOD"
        val = round(0.1 + 0.035 * d["nutrition"] + 0.06 * len(d["buffs"]) + (0.1 if d["kind"] == "bottle" else 0), 3)
        GOODS.append((iid, cat, val))
        have.add(iid)
    for i, d in s7.M.items():
        GOODS.append(("underdeep:" + i, "MEDICINE", round(min(2.0, 0.5 + 0.006 * d["cooldown"] + 0.1 * len(d["cures"])), 3)))
        have.add("underdeep:" + i)
    for i in s7.MAT:
        c, v = mat_cat.get(i, ("SPECIALTY", 0.1))
        GOODS.append(("underdeep:" + i, c, v))
        have.add("underdeep:" + i)
    groups = {"#fruit": "@PRODUCE", "#vegetable": "@PRODUCE", "#mushroom": "@PRODUCE", "#dried_fruit_src": "@PRODUCE", "#grain": "@GRAIN",
              "#meat": "@MEAT", "#fish": "@FISH", "#herb": "@HERB", "#sweet": "underdeep:honeyfruit", "#cooked_meat": "underdeep:cooked_game_meat",
              "#cooked_fish": "underdeep:cooked_fish_fillet", "#bread": "underdeep:glowgrain_bread", "#fiber": "minecraft:string",
              "#egg": "underdeep:creature_egg"}
    profs = {"pot": ["cook", "innkeeper"], "oven": ["baker", "cook"], "prep": ["cook", "baker", "innkeeper"], "kettle": ["brewer", "cook", "innkeeper"],
             "crock": ["brewer", "cook"], "rack": ["hunter", "cook", "fisher"], "herbal": ["healer", "herbalist", "alchemist"]}
    skip = ("propagate_", "leather_rack", "salt", "warm_milk")
    n = 0
    for r in s7.R:
        if r["id"].startswith(skip) or r["id"] == "salt":
            continue
        ins, ok = {}, True
        for k, c in r["ingredients"]:
            if k in ("minecraft:water_bucket", "minecraft:milk_bucket"):
                continue
            key = groups.get(k, k)
            if not key.startswith("@") and key not in have:
                ok = False
                break
            ins[key] = ins.get(key, 0) + c
        if not ok or not ins or r["result"] not in have:
            continue
        hours = round(min(2.0, max(0.2, r["seconds"] / 40)), 2)
        fuel = 0.1 if r["station"] in ("pot", "oven", "kettle") else 0.0
        skill = "MEDICINE" if r["station"] == "herbal" else "COOKING"
        RECIPES.append(R("s7_" + r["id"], "process", profs[r["station"]], ins, {r["result"]: r["count"]}, hours, 8, skill, fuel=fuel))
        n += 1
    # settlements also make salt (evaporating brine) and propagate nothing
    RECIPES.append(R("s7_boil_salt", "process", ["cook", "brewer"], {}, {"underdeep:salt": 2}, 0.5, 6, "COOKING", fuel=0.1))
    return n


def stage8():
    """Stage 8 goods (materials, components, equipment, accessories, kits, gear) and settlement production, derived from
    the Stage 8 content definitions. Smiths make metal gear, tailors leather/cloth gear, gemcutters cut gems and
    jewellery: a civilization's professions decide what it offers (regional specialisation)."""
    import sys
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "assets"))
    import stage8 as s8
    have = {g[0] for g in GOODS}

    def add(i, cat, v):
        if u8(i) not in have:
            GOODS.append((u8(i), cat, round(v, 3)))
            have.add(u8(i))

    def u8(i):
        return i if ":" in i else "underdeep:" + i
    ingot_val = {"steel_ingot": 0.6, "embersteel_ingot": 1.2, "rimesteel_ingot": 1.2, "galvanic_ingot": 0.5}
    for c in s8.COMPONENTS:
        cat = "METAL" if c.endswith("_ingot") else "ORE" if c.startswith("crushed_") else "GEM" if c.startswith(("cut_", "polished_")) or c == "glimmer_lens" \
            else "FIBER" if c in ("fur_lining", "silk_cloth", "rope_coil", "leather_strap") else "ANIMAL_PRODUCT"
        add(c, cat, ingot_val.get(c, 0.3))
    mval = {m: {"copper": 0.15, "steel": 0.6, "embersteel": 1.2, "rimesteel": 1.2, "galvanic": 0.5, "starsilver": 1.6, "umbralite": 1.2, "nacre": 0.5,
                "fossil": 0.15, "malachite": 0.3, "glimmer": 0.4, "iron": 0.4, "diamond": 3.0, "fur": 0.4, "scale": 0.4, "shell": 0.4,
                "silk": 0.5}[m] for m in s8.MAT}
    for w in s8.WEAPONS:
        add(w["id"], "EQUIPMENT", 1 + mval[w["material"]] * 3)
    for t in s8.TOOLS:
        add(t["id"], "TOOLS", 0.8 + mval[t["material"]] * 3)
    for st in s8.ARMOR_SETS:
        for p, n in (("helmet", 5), ("chestplate", 8), ("leggings", 7), ("boots", 4)):
            add(f"{st}_{p}", "EQUIPMENT", 0.5 + mval[st] * n)
    for a in s8.ACCESSORIES:
        add(a["id"], "LUXURY", 3.0)
    for up in s8.UPGRADES:
        add(up["kit"], "SPECIALTY", 1.5)
    for b in s8.BOWS:
        add(b["id"], "EQUIPMENT", 2.0)
    for a in s8.ARROWS:
        add(a["id"], "EQUIPMENT", 0.1)
    for g in s8.UTILITY:
        add(g, "TOOLS", 3.0)
    profs = {"crush": ["smelter", "miner"], "alloy": ["smelter", "blacksmith"], "forge": ["blacksmith", "engineer"], "gems": ["gemcutter", "artisan"],
             "leather": ["tailor", "hunter"], "herbal": ["alchemist", "healer"]}
    skill = {"crush": "MINING", "alloy": "SMITHING", "forge": "SMITHING", "gems": "CRAFTING", "leather": "TAILORING", "herbal": "MEDICINE"}
    n = 0
    for r in s8.R:
        ins, ok = {}, True
        for k, c in r["ingredients"]:
            key = {"#fiber": "minecraft:string"}.get(k, k)
            if key not in have:
                ok = False
                break
            ins[key] = ins.get(key, 0) + c
        if not ok or r["result"] not in have:
            continue
        RECIPES.append(R("s8_" + r["id"], "process", profs[r["station"]], ins, {r["result"]: r["count"]}, round(min(2.0, max(0.3, r["seconds"] / 10)), 2),
                         6, skill[r["station"]], fuel=0.15 if r["station"] in ("alloy", "forge") else 0.0))
        n += 1
    # crafting-table equipment made by smiths and tailors (same material counts as the crafting recipes)
    count = {"sword": 2, "dagger": 1, "greatsword": 5, "battleaxe": 4, "spear": 1, "warhammer": 6, "mace": 3, "pickaxe": 3, "axe": 3, "shovel": 1,
             "hoe": 2, "helmet": 5, "chestplate": 8, "leggings": 7, "boots": 4}
    for w in s8.WEAPONS + [t for t in s8.TOOLS if t["kind"] in count]:
        m = w["material"]
        fam = w.get("family") or w.get("kind")
        ingot = u8(s8.MAT[m]["ingot"])
        if m in s8.FORGED or ingot not in have or u8(w["id"]) not in have:
            continue
        RECIPES.append(R("s8_make_" + w["id"], "process", ["blacksmith"] if m not in ("fossil",) else ["artisan", "hunter"], {ingot: count[fam],
                         "minecraft:stick": 1}, {u8(w["id"]): 1}, 1.0, 2, "SMITHING"))
        n += 1
    for st in s8.ARMOR_SETS:
        ingot = u8(s8.MAT[st]["ingot"])
        if st in s8.FORGED or ingot not in have:
            continue
        for p in ("helmet", "chestplate", "leggings", "boots"):
            RECIPES.append(R(f"s8_make_{st}_{p}", "process", ["tailor"] if st in ("fur", "scale", "shell", "silk") else ["blacksmith"],
                             {ingot: count[p]}, {u8(f"{st}_{p}"): 1}, 1.2, 2, "TAILORING" if st in ("fur", "scale", "shell", "silk") else "SMITHING"))
            n += 1
    return n


def stage10():
    """Stage 10 guardian materials: rare goods merchants and scholars buy (never sold back cheaper than bought)."""
    have = {g[0] for g in GOODS}
    for i, v in (("thunderhide", 3.0), ("mycelial_heart", 3.0), ("choir_nacre", 3.0), ("rimefang_pelt", 4.5), ("galvanic_core", 4.5),
                 ("cinder_scale", 4.5), ("everlit_wick", 4.5), ("resonant_crystal", 6.0), ("abyssal_pearl", 6.0), ("silent_bellmetal", 6.0),
                 ("heartwood_resin", 9.0), ("seamglass", 9.0), ("somnal_ember", 12.0)):
        if "underdeep:" + i not in have:
            GOODS.append(("underdeep:" + i, "LUXURY", v))


def stage9():
    """Stage 9 exploration valuables: relics, ancient coins, star shards and encrusted relics are goods that merchants and
    scholars buy (real stock; resale is always dearer than purchase, so no loops). Artifacts are not goods."""
    import sys
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "assets"))
    import stage9 as s9
    have = {g[0] for g in GOODS}
    for rid, _ in s9.RELICS:
        if "underdeep:" + rid not in have:
            GOODS.append(("underdeep:" + rid, "DECORATIVE", 4.0))
    for i, cat, v in (("ancient_coin", "LUXURY", 0.6), ("star_shard", "GEM", 1.5), ("encrusted_relic", "SPECIALTY", 2.0)):
        if "underdeep:" + i not in have:
            GOODS.append(("underdeep:" + i, cat, v))


def main():
    stage9()
    stage10()
    n8 = stage8()
    print("stage 8:", n8, "settlement recipes")
    n7 = stage7()
    print("stage 7:", n7, "settlement recipes")
    os.makedirs(OUT, exist_ok=True)
    ids = [g[0] for g in GOODS]
    assert len(ids) == len(set(ids)), "duplicate goods"
    goods = [{"item": i, "category": c, "value": v} for i, c, v in GOODS]
    rids = [r["id"] for r in RECIPES]
    assert len(rids) == len(set(rids)), "duplicate recipes"
    for r in RECIPES:
        for k in list(r["inputs"]) + list(r["outputs"]) + list(r["byproducts"]):
            assert k.startswith("@") or k in ids, (r["id"], k)
    with open(f"{OUT}/goods.json", "w") as f:
        json.dump({"currency": "minecraft:emerald", "goods": goods}, f, indent=1)
    with open(f"{OUT}/production.json", "w") as f:
        json.dump({"recipes": RECIPES}, f, indent=1)
    print(len(goods), "goods,", len(RECIPES), "recipes")


if __name__ == "__main__":
    main()
