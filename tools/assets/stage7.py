#!/usr/bin/env python3
"""
Stage 7 content and assets: foods, drinks, medicines, materials, station recipes, survival blocks
(workstations, furniture, camping, soils) — the data file the Java side loads
(data/underdeep/survival/content.json) plus textures, models, blockstates, loot tables, crafting recipes,
names and tooltips. Called by stage2_assets.py (full pipeline) or run alone:

    python3 tools/assets/stage7.py

Deterministic. Every ingredient must be a registered item: the script validates ids against the
existing language file and its own items; the Java loader validates again at startup.
"""
import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
import art  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "underdeep")
DATA = os.path.join(ROOT, "data")
NS = "underdeep"
LANG = {}
SEED = [7000]


def nseed():
    SEED[0] += 1
    return SEED[0]


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def u(i):
    return i if ":" in i else f"{NS}:{i}"


def name_of(i):
    return " ".join(w.capitalize() for w in i.split("_"))


# =============================================================================================== content
# Ingredient groups (recipe keys "#name"): real items only.
GROUPS = {
    "fruit": ["duskberries", "honeyfruit", "snowberries", "cliffberries", "briar_fruit", "sea_grapes", "minecraft:apple",
              "minecraft:sweet_berries", "minecraft:glow_berries", "minecraft:melon_slice"],
    "vegetable": ["duskroot", "frost_turnip", "lake_chestnut", "emberpepper", "minecraft:carrot", "minecraft:potato",
                  "minecraft:beetroot"],
    "grain": ["glowgrain", "mire_rice", "minecraft:wheat"],
    "mushroom": ["puffcap", "inkcap", "rimecap", "cave_truffle", "minecraft:brown_mushroom", "minecraft:red_mushroom"],
    "meat": ["game_meat", "shellmeat", "minecraft:beef", "minecraft:porkchop", "minecraft:chicken", "minecraft:mutton",
             "minecraft:rabbit"],
    "cooked_meat": ["cooked_game_meat", "cooked_shellmeat", "minecraft:cooked_beef", "minecraft:cooked_porkchop",
                    "minecraft:cooked_chicken", "minecraft:cooked_mutton"],
    "fish": ["fish_fillet", "minecraft:cod", "minecraft:salmon", "glimmerfin", "reef_darter", "mire_eel"],
    "cooked_fish": ["cooked_fish_fillet", "minecraft:cooked_cod", "minecraft:cooked_salmon"],
    "herb": ["mendleaf_sprig", "sunleaf_blossom", "moonpetal_petal", "emberwort_root", "bogwort_leaf", "siltwort_frond"],
    "sweet": ["minecraft:sugar", "minecraft:honey_bottle", "honeyfruit"],
    "egg": ["creature_egg", "minecraft:egg"],
    "fiber": ["minecraft:string", "voidroot_fiber", "silk_strand", "fur_tuft"],
    "bread": ["minecraft:bread", "glowgrain_bread", "flatbread"],
    "dried_fruit_src": ["duskberries", "honeyfruit", "snowberries", "cliffberries", "briar_fruit", "minecraft:apple",
                        "minecraft:sweet_berries"],
    "organic": [],  # filled below: anything compostable
}

# Nutrition groups (for the variety bonus): GRAIN, VEGETABLE, FRUIT, FUNGI, MEAT, FISH, SWEET.
NUTRITION = {
    "duskberries": "FRUIT", "honeyfruit": "FRUIT,SWEET", "snowberries": "FRUIT", "cliffberries": "FRUIT", "briar_fruit": "FRUIT",
    "sea_grapes": "FRUIT,VEGETABLE", "duskroot": "VEGETABLE", "baked_duskroot": "VEGETABLE", "glowgrain_bread": "GRAIN",
    "cave_truffle": "FUNGI", "roasted_truffle": "FUNGI", "rice_bowl": "GRAIN", "lake_chestnut": "VEGETABLE",
    "roasted_chestnut": "VEGETABLE", "emberpepper": "VEGETABLE", "frost_turnip": "VEGETABLE", "baked_turnip": "VEGETABLE",
    "sunbean": "VEGETABLE", "sunbean_stew": "VEGETABLE,GRAIN", "puffcap": "FUNGI", "inkcap": "FUNGI", "roasted_inkcap": "FUNGI",
    "rimecap": "FUNGI", "cavern_stew": "FUNGI,VEGETABLE", "game_meat": "MEAT", "cooked_game_meat": "MEAT", "shellmeat": "FISH",
    "cooked_shellmeat": "FISH", "fish_fillet": "FISH", "cooked_fish_fillet": "FISH",
    "minecraft:bread": "GRAIN", "minecraft:apple": "FRUIT", "minecraft:carrot": "VEGETABLE", "minecraft:potato": "VEGETABLE",
    "minecraft:baked_potato": "VEGETABLE", "minecraft:beetroot": "VEGETABLE", "minecraft:beetroot_soup": "VEGETABLE",
    "minecraft:cooked_beef": "MEAT", "minecraft:cooked_porkchop": "MEAT", "minecraft:cooked_chicken": "MEAT",
    "minecraft:cooked_mutton": "MEAT", "minecraft:cooked_rabbit": "MEAT", "minecraft:cooked_cod": "FISH",
    "minecraft:cooked_salmon": "FISH", "minecraft:mushroom_stew": "FUNGI", "minecraft:sweet_berries": "FRUIT",
    "minecraft:glow_berries": "FRUIT", "minecraft:melon_slice": "FRUIT", "minecraft:cookie": "SWEET,GRAIN", "minecraft:pumpkin_pie": "SWEET",
    "minecraft:cake": "SWEET,GRAIN", "minecraft:honey_bottle": "SWEET", "minecraft:dried_kelp": "VEGETABLE",
    "minecraft:rabbit_stew": "MEAT,VEGETABLE",
}

# Effects: id → (display name, beneficial, colour) — custom status effects registered by the mod.
EFFECTS = {
    "warmed": ("Warmed", True, (240, 130, 50)), "cooled": ("Cooled", True, (110, 200, 240)),
    "spore_ward": ("Spore Ward", True, (170, 210, 120)), "clear_lungs": ("Clear Lungs", True, (200, 230, 230)),
    "grounded": ("Grounded", True, (200, 180, 90)), "well_fed": ("Well Fed", True, (230, 190, 90)),
    "rested": ("Rested", True, (150, 150, 230)), "heat_stress": ("Heat Stress", False, (230, 80, 40)),
    "cold_stress": ("Cold Stress", False, (120, 160, 230)), "spore_irritation": ("Spore Irritation", False, (150, 170, 60)),
    "choking_fumes": ("Choking Fumes", False, (190, 190, 80)), "static_shock": ("Static Shock", False, (120, 140, 255)),
    "wounded": ("Wounded", False, (180, 40, 40)), "weary": ("Weary", False, (120, 110, 100)),
}

# Foods: id → (nutrition, saturation, groups, kind, art, buffs, role)
#   kind: "bowl" (returns a bowl, stacks 16), "bottle" (drink, returns a bottle), "plain"
#   buffs: list of (effect id, seconds, amplifier)
#   role: tooltip line describing the purpose
F = {}


def food(i, n, s, groups, kind, art_spec, buffs=(), role="", name=None, stack=None):
    F[i] = {"nutrition": n, "saturation": s, "groups": groups, "kind": kind, "art": art_spec, "buffs": list(buffs), "role": role,
            "name": name or name_of(i), "stack": stack}


W, C, SW, CL, GL, HA, WB, RG, RS, RE, AB, SP, NV, DG, SA, HB = ("underdeep:warmed", "underdeep:cooled", "underdeep:spore_ward",
                                                                "underdeep:clear_lungs", "underdeep:grounded", "minecraft:haste",
                                                                "minecraft:water_breathing", "minecraft:regeneration",
                                                                "underdeep:rested", "minecraft:resistance", "minecraft:absorption",
                                                                "minecraft:speed", "minecraft:night_vision", "minecraft:dolphins_grace",
                                                                "minecraft:saturation", "minecraft:health_boost")

# ---- regional raw fish (caught by rod or trap; cook like any fish)
food("glimmerfin", 2, 0.1, "FISH", "plain", ("fish", (150, 200, 240)), role="A shimmering fish of the luminous waters. Cook it.")
food("reef_darter", 2, 0.1, "FISH", "plain", ("fish", (240, 140, 90)), role="A quick fish of the drowned gardens. Cook it.")
food("mire_eel", 2, 0.1, "FISH", "plain", ("eel", (90, 110, 70)), role="A slippery wetland eel. Cook it.")

# ---- campfire / furnace roasts (vanilla cooking)
food("roasted_sunbeans", 4, 0.5, "VEGETABLE", "plain", ("seeds_pile", (200, 150, 60)), role="Simple roast")
food("charred_emberpepper", 3, 0.4, "VEGETABLE", "plain", ("pepper", (150, 40, 25)), buffs=[(W, 60, 0)], role="Warming snack")
food("roasted_puffcap", 4, 0.5, "FUNGI", "plain", ("mushroom_item", (210, 190, 120)), role="Simple roast")
food("toasted_rimecap", 4, 0.5, "FUNGI", "plain", ("mushroom_item", (150, 200, 230)), buffs=[(C, 60, 0)], role="Cooling snack")
food("boiled_egg", 4, 0.6, "MEAT", "plain", ("egg", (240, 235, 220)), role="Simple, portable")

# ---- cooking pot (soups and stews)
food("puffcap_soup", 7, 0.7, "FUNGI,VEGETABLE", "bowl", ("bowl", (200, 180, 120), [(230, 220, 170)]), role="Everyday meal")
food("root_stew", 8, 0.75, "VEGETABLE", "bowl", ("bowl", (170, 110, 70), [(200, 140, 90), (230, 200, 140)]), role="Everyday meal")
food("game_stew", 10, 0.9, "MEAT,VEGETABLE", "bowl", ("bowl", (140, 80, 50), [(90, 50, 30), (200, 140, 90)]),
     buffs=[(RG, 8, 0)], role="Recovery meal")
food("fish_chowder", 9, 0.85, "FISH,VEGETABLE", "bowl", ("bowl", (235, 225, 200), [(240, 170, 120)]), buffs=[(RG, 6, 0)],
     role="Recovery meal")
food("shellfish_broth", 7, 0.7, "FISH", "bowl", ("bowl", (210, 190, 150), [(240, 150, 120)]), buffs=[(WB, 60, 0)],
     role="Aquatic meal: short water breathing")
food("ember_chili", 10, 0.8, "MEAT,VEGETABLE", "bowl", ("bowl", (180, 50, 30), [(240, 120, 40), (90, 40, 20)]),
     buffs=[(W, 360, 0)], role="Cold-region meal: keeps you warm")
food("frost_turnip_soup", 8, 0.75, "VEGETABLE", "bowl", ("bowl", (230, 220, 240), [(190, 160, 220)]), buffs=[(W, 240, 0)],
     role="Cold-region meal: keeps you warm")
food("rice_porridge", 7, 0.8, "GRAIN", "bowl", ("bowl", (245, 240, 225), [(230, 200, 140)]), buffs=[(RS, 180, 0)],
     role="Comfort meal")
food("truffle_risotto", 11, 1.0, "GRAIN,FUNGI", "bowl", ("bowl", (225, 210, 170), [(90, 70, 50), (250, 240, 200)]),
     buffs=[(RS, 300, 0)], role="Comfort meal (luxury)")
food("berry_compote", 6, 0.6, "FRUIT,SWEET", "bowl", ("bowl", (150, 40, 90), [(200, 60, 120), (90, 30, 70)]), role="Sweet dish")
food("herb_broth", 6, 0.7, "VEGETABLE", "bowl", ("bowl", (170, 190, 110), [(90, 150, 70)]), buffs=[(RG, 10, 0)],
     role="Recovery meal")
food("chestnut_mash", 8, 0.75, "VEGETABLE", "bowl", ("bowl", (190, 150, 100), [(150, 100, 60)]), role="Hearty everyday meal")
food("fungal_hotpot", 12, 0.95, "FUNGI,MEAT", "bowl", ("bowl", (120, 90, 110), [(200, 180, 120), (60, 40, 60)]),
     buffs=[(SW, 300, 0)], role="Sporefolk dish: wards off spores")
food("tide_soup", 10, 0.85, "FISH,FRUIT", "bowl", ("bowl", (100, 170, 170), [(120, 200, 120), (240, 170, 120)]),
     buffs=[(DG, 60, 0), (WB, 90, 0)], role="Tide-dweller dish: swimming and breathing")
food("forge_goulash", 13, 1.0, "MEAT,VEGETABLE", "bowl", ("bowl", (160, 60, 30), [(90, 40, 20), (240, 130, 40)]),
     buffs=[(HA, 240, 0)], role="Forgekin worker meal: haste")
food("amber_hearth_stew", 11, 0.9, "VEGETABLE,GRAIN,SWEET", "bowl", ("bowl", (220, 160, 60), [(240, 210, 60), (250, 220, 120)]),
     buffs=[(RS, 240, 0)], role="Amberfolk comfort stew")
food("marrow_broth", 9, 0.9, "MEAT,VEGETABLE", "bowl", ("bowl", (220, 205, 170), [(200, 170, 130)]), buffs=[(RE, 60, 0)],
     role="Stoneborn hardy broth: brief resistance")
food("forest_stew", 11, 0.9, "MEAT,VEGETABLE,FRUIT", "bowl", ("bowl", (110, 80, 60), [(90, 50, 70), (90, 150, 70)]),
     buffs=[(RG, 8, 0)], role="Rootfolk forest stew")
food("glimmer_broth", 7, 0.75, "FUNGI,GRAIN", "bowl", ("bowl", (150, 190, 220), [(210, 240, 255)]), buffs=[(NV, 180, 0)],
     role="Glimmerkin broth: night vision")
food("starlit_porridge", 9, 0.85, "GRAIN", "bowl", ("bowl", (210, 210, 240), [(170, 170, 255), (250, 250, 255)]),
     buffs=[(NV, 120, 0), (RS, 120, 0)], role="Astral porridge")

# ---- oven
food("flatbread", 5, 0.6, "GRAIN", "plain", ("flatbread", (220, 180, 110)), role="Basic baked bread")
food("rice_cake", 5, 0.6, "GRAIN,SWEET", "plain", ("cake_bite", (240, 235, 215)), role="Snack")
food("berry_pie", 8, 0.8, "FRUIT,GRAIN,SWEET", "plain", ("pie", (200, 150, 90), (120, 30, 70)), role="Shareable pie")
food("honeyfruit_tart", 8, 0.8, "FRUIT,GRAIN,SWEET", "plain", ("pie", (210, 160, 90), (240, 190, 50)), buffs=[(RS, 120, 0)],
     role="Amberfolk tart: comfort")
food("meat_pie", 10, 1.0, "MEAT,GRAIN,VEGETABLE", "plain", ("pie", (190, 140, 80), (120, 70, 40)),
     role="Exploration meal: filling and portable", stack=16)
food("fish_pie", 9, 0.95, "FISH,GRAIN", "plain", ("pie", (200, 160, 100), (230, 200, 170)), role="Exploration meal", stack=16)
food("mushroom_pastry", 8, 0.85, "FUNGI,GRAIN", "plain", ("pastry", (210, 170, 100), (140, 110, 90)), role="Portable meal")
food("roast_platter", 14, 1.1, "MEAT,VEGETABLE", "plain", ("platter", (150, 90, 50)), buffs=[(RS, 240, 0)], stack=8,
     role="Feast dish: comfort")
food("baked_shellfish", 9, 0.85, "FISH", "plain", ("shell_dish", (230, 140, 110)), role="Coastal dish")
food("chestnut_bread", 7, 0.75, "GRAIN,VEGETABLE", "plain", ("loaf", (150, 100, 60)), role="Hearty bread")
food("sunbean_loaf", 7, 0.75, "GRAIN,VEGETABLE", "plain", ("loaf", (220, 180, 80)), role="Hearty bread")
food("ember_flatbread", 6, 0.65, "GRAIN,VEGETABLE", "plain", ("flatbread", (210, 120, 60)), buffs=[(W, 120, 0)], role="Warming bread")
food("sweet_roll", 6, 0.7, "GRAIN,SWEET", "plain", ("roll", (230, 190, 120)), buffs=[(RS, 60, 0)], role="Comfort snack")
food("duskberry_cookie", 3, 0.3, "GRAIN,FRUIT,SWEET", "plain", ("cookie", (170, 120, 70), (90, 40, 90)), role="Snack")
food("hardtack", 5, 0.9, "GRAIN", "plain", ("hardtack", (210, 190, 150)), role="Preserved travel bread: very filling for its size")

# ---- preparation table
food("fruit_salad", 7, 0.6, "FRUIT", "bowl", ("bowl", (220, 120, 120), [(240, 210, 60), (120, 60, 160), (150, 220, 120)]),
     role="Fresh dish")
food("marsh_salad", 6, 0.6, "VEGETABLE,FRUIT", "bowl", ("bowl", (120, 170, 100), [(170, 220, 120), (90, 140, 80)]), role="Fresh dish")
food("trail_mix", 4, 0.5, "VEGETABLE,FRUIT", "plain", ("trail_mix", (170, 120, 70)), role="Exploration snack")
food("hunters_sandwich", 11, 1.0, "GRAIN,MEAT,VEGETABLE", "plain", ("sandwich", (220, 180, 110), (120, 70, 40)),
     role="Exploration meal", stack=16)
food("fish_wrap", 10, 0.95, "GRAIN,FISH,FRUIT", "plain", ("wrap", (225, 195, 130), (240, 170, 120)), role="Exploration meal", stack=16)
food("tide_roll", 8, 0.8, "GRAIN,FISH,FRUIT", "plain", ("sushi", (245, 245, 235), (240, 150, 110)), buffs=[(WB, 45, 0)],
     role="Tide-dweller roll")
food("travel_ration", 9, 1.2, "GRAIN,MEAT,FRUIT", "plain", ("ration", (150, 120, 80)),
     role="Expedition ration: compact and very filling", stack=16)
food("ember_ration", 9, 1.2, "GRAIN,MEAT,VEGETABLE", "plain", ("ration", (170, 80, 50)), buffs=[(W, 600, 0)], stack=16,
     role="Expedition ration for cold regions")
food("frost_ration", 9, 1.2, "GRAIN,MEAT,FUNGI", "plain", ("ration", (100, 150, 190)), buffs=[(C, 600, 0)], stack=16,
     role="Expedition ration for hot regions")

# ---- drying rack and crock (preserved foods)
food("jerky", 6, 0.8, "MEAT", "plain", ("jerky", (130, 60, 40)), role="Preserved meat")
food("dried_fish", 5, 0.8, "FISH", "plain", ("dried_fish", (190, 160, 110)), role="Preserved fish")
food("dried_fruit", 4, 0.5, "FRUIT,SWEET", "plain", ("dried_fruit", (170, 70, 90)), role="Preserved fruit")
food("dried_mushrooms", 4, 0.5, "FUNGI", "plain", ("dried_mushroom", (180, 150, 110)), role="Preserved fungi")
food("pickled_turnips", 6, 0.7, "VEGETABLE", "plain", ("jar", (200, 170, 220)), role="Pickled: keeps on long journeys")
food("pickled_peppers", 5, 0.6, "VEGETABLE", "plain", ("jar", (200, 60, 40)), buffs=[(W, 180, 0)], role="Pickled and warming")
food("spore_paste", 4, 0.6, "FUNGI", "plain", ("jar", (150, 130, 90)), buffs=[(SW, 180, 0)], role="Sporefolk ferment: spore ward")
food("capback_cheese", 6, 0.9, "MEAT", "plain", ("cheese", (240, 210, 110)), role="Aged cheese: filling")

# ---- drinks (kettle, prep table presses, crock ferments)
food("mendleaf_tea", 2, 0.3, "", "bottle", ("cup", (130, 170, 90)), buffs=[(RG, 12, 0)], role="Herbal tea: mild recovery")
food("sunleaf_tea", 2, 0.3, "", "bottle", ("cup", (240, 210, 90)), buffs=[(AB, 60, 0)], role="Herbal tea: a little protection")
food("moonpetal_tea", 2, 0.3, "", "bottle", ("cup", (160, 170, 240)), buffs=[(NV, 120, 0)], role="Herbal tea: night vision")
food("emberwort_tea", 2, 0.3, "", "bottle", ("cup", (210, 90, 40)), buffs=[(W, 300, 0)], role="Warm drink: cold protection")
food("rimecap_cooler", 2, 0.3, "", "bottle", ("bottle", (150, 220, 240)), buffs=[(C, 300, 0)], role="Cool drink: heat protection")
food("marsh_tea", 2, 0.3, "", "bottle", ("cup", (110, 150, 90)), buffs=[(SW, 240, 0)], role="Herbal tea: spore ward")
food("sea_grape_juice", 3, 0.3, "FRUIT", "bottle", ("bottle", (120, 200, 120)), buffs=[(WB, 30, 0)], role="Juice")
food("honeyfruit_juice", 3, 0.4, "FRUIT,SWEET", "bottle", ("bottle", (240, 190, 60)), role="Juice: sweet and filling")
food("berry_juice", 3, 0.3, "FRUIT", "bottle", ("bottle", (150, 40, 90)), buffs=[(SP, 30, 0)], role="Juice: a quick spring in your step")
food("warm_milk", 3, 0.5, "", "bottle", ("cup", (245, 240, 230)), buffs=[(RS, 240, 0)], role="Comfort drink")
food("bone_broth", 4, 0.6, "MEAT", "bottle", ("cup", (220, 200, 160)), buffs=[(RG, 15, 0)], role="Restorative broth")
food("inkcap_infusion", 2, 0.3, "FUNGI", "bottle", ("cup", (60, 50, 70)), buffs=[(HA, 180, 0)], role="Worker's infusion: haste")
food("glowgrain_kvass", 3, 0.4, "GRAIN", "bottle", ("bottle", (200, 160, 80)), buffs=[(RS, 120, 0)], role="Fermented grain drink")
food("honeyfruit_fizz", 3, 0.4, "FRUIT,SWEET", "bottle", ("bottle", (250, 210, 100)), buffs=[(SP, 45, 0)], role="Fermented fizz")
food("berry_shrub", 2, 0.3, "FRUIT", "bottle", ("bottle", (130, 30, 60)), buffs=[(C, 120, 0)], role="Tart drink: cooling")
food("spore_kombucha", 2, 0.3, "FUNGI", "bottle", ("bottle", (170, 160, 90)), buffs=[(SW, 300, 0)], role="Fermented: spore ward")
food("cryolite_chill", 1, 0.1, "", "bottle", ("bottle", (200, 240, 255)), buffs=[(C, 480, 0)], role="Mineral infusion: strong cooling")
food("starlily_nectar", 3, 0.4, "SWEET", "bottle", ("bottle", (220, 220, 255)), buffs=[(NV, 240, 0)], role="Luminous nectar")
food("spiced_cider", 3, 0.4, "FRUIT,SWEET", "bottle", ("cup", (190, 110, 50)), buffs=[(W, 240, 0), (RS, 60, 0)],
     role="Warm spiced drink")

# Medicines: id → (art, effects list, cures list, heal, cooldown s, use "drink"|"apply", role)
M = {}


def med(i, art_spec, effects=(), cures=(), heal=0, cooldown=10, use="apply", role="", name=None):
    M[i] = {"art": art_spec, "effects": list(effects), "cures": list(cures), "heal": heal, "cooldown": cooldown, "use": use,
            "role": role, "name": name or name_of(i)}


ALLBAD = ["minecraft:poison", "minecraft:nausea", "minecraft:weakness", "minecraft:slowness", "minecraft:mining_fatigue",
          "minecraft:blindness", "minecraft:darkness", "minecraft:hunger", "underdeep:heat_stress", "underdeep:cold_stress",
          "underdeep:spore_irritation", "underdeep:choking_fumes", "underdeep:static_shock", "underdeep:wounded", "underdeep:weary"]
med("bandage", ("bandage", (235, 230, 220)), effects=[(RG, 4, 1)], cures=["underdeep:wounded"], heal=2, cooldown=3,
    role="Binds a wound: stops it slowing your recovery")
med("cooling_salve", ("salve", (140, 210, 240)), effects=[(C, 480, 0)], cures=["underdeep:heat_stress"], cooldown=5,
    role="Protects from heat for 8 minutes")
med("warming_salve", ("salve", (230, 120, 50)), effects=[(W, 480, 0)], cures=["underdeep:cold_stress"], cooldown=5,
    role="Protects from cold for 8 minutes")
med("spore_ward_tincture", ("tincture", (160, 200, 110)), effects=[(SW, 600, 0)], cures=["underdeep:spore_irritation"], cooldown=5,
    use="drink", role="Spore ward for 10 minutes")
med("breathing_draught", ("tincture", (90, 180, 200)), effects=[(WB, 180, 0)], cooldown=20, use="drink",
    role="Three minutes of water breathing")
med("grounding_paste", ("tin", (200, 180, 90)), effects=[(GL, 600, 0)], cures=["underdeep:static_shock"], cooldown=5,
    role="Protects from static charge for 10 minutes")
med("clear_air_tincture", ("tincture", (220, 235, 235)), effects=[(CL, 600, 0)], cures=["underdeep:choking_fumes"], cooldown=5,
    use="drink", role="Protects from fumes for 10 minutes")
med("restorative_tonic", ("tincture", (200, 120, 160)), effects=[(RG, 20, 0)], cures=["underdeep:weary"], cooldown=30, use="drink",
    role="Recovery and an end to weariness")
med("fortifying_elixir", ("tincture", (150, 140, 230)), effects=[(RE, 90, 0)], cooldown=120, use="drink",
    role="Brief resistance before a dangerous fight (long cooldown)")
med("vigor_draught", ("tincture", (240, 170, 60)), effects=[(SP, 60, 0), (HA, 60, 0)], cures=["minecraft:slowness"], cooldown=90,
    use="drink", role="A minute of speed and haste (long cooldown)")
med("venom_antiserum", ("tincture", (120, 200, 90)), effects=[], cures=["minecraft:poison", "minecraft:wither"], cooldown=10,
    use="drink", role="Cures poison and withering", name="Venom Antiserum")
med("calming_balm", ("salve", (190, 170, 230)), cures=["minecraft:nausea", "minecraft:darkness", "minecraft:blindness",
                                                   "underdeep:spore_irritation"], cooldown=10, role="Soothes dizziness and clears the eyes")
med("burn_salve", ("salve", (240, 200, 150)), effects=[(RG, 6, 0)], cures=["underdeep:heat_stress", "minecraft:fire"], heal=2,
    cooldown=10, role="Treats burns")
med("smelling_salts", ("tin", (240, 240, 230)), cures=["underdeep:weary", "minecraft:blindness", "minecraft:darkness",
                                                    "minecraft:mining_fatigue"], cooldown=20, role="Sharpens a weary mind")
med("purging_draught", ("tincture", (90, 60, 110)), cures=ALLBAD, cooldown=300, use="drink",
    role="Purges every ailment, but leaves you queasy (very long cooldown)")
med("bloodcap_tonic", ("tincture", (170, 30, 40)), effects=[(HB, 180, 0)], cooldown=180, use="drink",
    role="Three minutes of extra health (long cooldown)")
med("glow_salve", ("salve", (180, 230, 160)), effects=[(NV, 300, 0)], cooldown=5, role="Five minutes of night vision")
med("field_medkit", ("medkit", (200, 50, 50)), effects=[(RG, 8, 1)], cures=["underdeep:wounded", "minecraft:poison"], heal=4,
    cooldown=15, role="Complete field treatment: wounds and poison")

# Materials: id → (art, role, name)
MAT = {
    "flour": (("sack", (235, 230, 215)), "Ground grain for baking"),
    "dough": (("dough", (235, 215, 170)), "Bake in an oven"),
    "salt": (("pile", (240, 240, 245)), "Seasoning and preserving"),
    "butter": (("butter", (245, 220, 110)), "Cooking fat"),
    "vinegar": (("bottle", (210, 180, 120)), "For pickling"),
    "spice_blend": (("pouch", (190, 90, 40)), "Seasoning for regional dishes"),
    "compost": (("pile", (90, 70, 45)), "Fertiliser: use on crops, herbs and bushes; makes rich soil"),
    "grain_feed": (("sack", (220, 200, 120)), "Feed for grazing and browsing animals"),
    "fungal_feed": (("sack", (170, 150, 120)), "Feed for fungus-eating animals"),
    "fruit_mash": (("pile", (190, 80, 110)), "Feed for fruit-eating animals"),
    "meat_scraps": (("pile", (160, 70, 60)), "Feed for carnivores and scavengers"),
    "mineral_lick": (("block_bit", (210, 200, 190)), "Feed for rock-eating animals"),
    "fishing_bait": (("bait", (170, 120, 90)), "Hold in your other hand while fishing, or put in a fish trap"),
}

# Station recipes: (id, station, [(key, count)...], result, count, seconds, container-or-None, basic?, civ-or-None)
R = []


def rec(rid, station, ins, out, count=1, secs=10, cont=None, basic=False, civ=None):
    R.append({"id": rid, "station": station, "ingredients": [[u(k) if not k.startswith("#") else k, n] for k, n in ins],
              "result": u(out), "count": count, "seconds": secs, "container": u(cont) if cont else "", "basic": basic, "civ": civ or ""})


BOWL, BOTTLE = "minecraft:bowl", "minecraft:glass_bottle"
WATER, MILK = "minecraft:water_bucket", "minecraft:milk_bucket"
# cooking pot
rec("salt", "pot", [(WATER, 1)], "salt", 4, 20, basic=True)
rec("puffcap_soup", "pot", [("puffcap", 2), ("#vegetable", 1)], "puffcap_soup", 1, 10, BOWL, basic=True)
rec("root_stew", "pot", [("duskroot", 2), ("frost_turnip", 1)], "root_stew", 1, 10, BOWL, basic=True)
rec("root_stew_carrots", "pot", [("minecraft:carrot", 2), ("minecraft:potato", 1)], "root_stew", 1, 10, BOWL, basic=True)
rec("game_stew", "pot", [("#meat", 1), ("#vegetable", 2)], "game_stew", 1, 15, BOWL, basic=True)
rec("fish_chowder", "pot", [("#fish", 2), (MILK, 1), ("#vegetable", 1)], "fish_chowder", 2, 15, BOWL)
rec("shellfish_broth", "pot", [("shellmeat", 2), ("siltwort_frond", 1)], "shellfish_broth", 1, 12, BOWL)
rec("ember_chili", "pot", [("emberpepper", 2), ("#meat", 1), ("sunbean", 1)], "ember_chili", 1, 15, BOWL)
rec("frost_turnip_soup", "pot", [("frost_turnip", 2), ("butter", 1)], "frost_turnip_soup", 2, 12, BOWL)
rec("rice_porridge", "pot", [("mire_rice", 2), (MILK, 1)], "rice_porridge", 2, 12, BOWL)
rec("truffle_risotto", "pot", [("mire_rice", 2), ("cave_truffle", 1), ("butter", 1)], "truffle_risotto", 1, 20, BOWL)
rec("berry_compote", "pot", [("#fruit", 3), ("#sweet", 1)], "berry_compote", 1, 10, BOWL, basic=True)
rec("herb_broth", "pot", [("mendleaf_sprig", 1), ("#vegetable", 1), ("salt", 1)], "herb_broth", 1, 10, BOWL)
rec("chestnut_mash", "pot", [("lake_chestnut", 3), ("butter", 1)], "chestnut_mash", 1, 12, BOWL)
rec("sunbean_stew_pot", "pot", [("sunbean", 3), ("duskroot", 1)], "sunbean_stew", 1, 12, BOWL, basic=True)
rec("cavern_stew_pot", "pot", [("puffcap", 1), ("inkcap", 1), ("cave_truffle", 1), ("duskroot", 1)], "cavern_stew", 1, 15, BOWL)
rec("rice_bowl_pot", "pot", [("mire_rice", 3)], "rice_bowl", 1, 10, BOWL, basic=True)
rec("fungal_hotpot", "pot", [("inkcap", 1), ("puffcap", 1), ("rimecap", 1), ("#meat", 1)], "fungal_hotpot", 1, 20, BOWL, civ="sporefolk")
rec("tide_soup", "pot", [("#fish", 1), ("sea_grapes", 1), ("siltwort_frond", 1)], "tide_soup", 1, 15, BOWL, civ="tide_dwellers")
rec("forge_goulash", "pot", [("#meat", 2), ("emberpepper", 1), ("frost_turnip", 1)], "forge_goulash", 1, 20, BOWL, civ="forgekin")
rec("amber_hearth_stew", "pot", [("sunbean", 1), ("honeyfruit", 1), ("glowgrain", 1)], "amber_hearth_stew", 1, 15, BOWL, civ="amberfolk")
rec("marrow_broth", "pot", [("minecraft:bone", 2), ("frost_turnip", 1), ("salt", 1)], "marrow_broth", 1, 20, BOWL, civ="stoneborn")
rec("forest_stew", "pot", [("duskroot", 1), ("duskberries", 1), ("mendleaf_sprig", 1), ("#meat", 1)], "forest_stew", 1, 15, BOWL,
    civ="rootfolk")
rec("glimmer_broth", "pot", [("rimecap", 1), ("glowgrain", 1), ("salt", 1)], "glimmer_broth", 1, 12, BOWL, civ="glimmerkin")
rec("starlit_porridge", "pot", [("glowgrain", 2), ("moonpetal_petal", 1), (MILK, 1)], "starlit_porridge", 2, 15, BOWL, civ="astrals")
rec("boiled_eggs", "pot", [("#egg", 2), (WATER, 1)], "boiled_egg", 2, 8, basic=True)
rec("warm_milk", "kettle", [(MILK, 1), ("#sweet", 1)], "warm_milk", 3, 8, BOTTLE, basic=True)
# oven
rec("flatbread", "oven", [("dough", 1)], "flatbread", 2, 10, basic=True)
rec("glowgrain_bread_oven", "oven", [("dough", 1), ("glowgrain", 1)], "glowgrain_bread", 2, 12, basic=True)
rec("bread_oven", "oven", [("dough", 2)], "minecraft:bread", 3, 12, basic=True)
rec("rice_cake", "oven", [("mire_rice", 2), ("#sweet", 1)], "rice_cake", 3, 10)
rec("berry_pie", "oven", [("dough", 1), ("#fruit", 2), ("#sweet", 1)], "berry_pie", 2, 15)
rec("honeyfruit_tart", "oven", [("dough", 1), ("honeyfruit", 2)], "honeyfruit_tart", 2, 15, civ="amberfolk")
rec("meat_pie", "oven", [("dough", 1), ("#meat", 1), ("#vegetable", 1)], "meat_pie", 2, 15)
rec("fish_pie", "oven", [("dough", 1), ("#fish", 1), ("#vegetable", 1)], "fish_pie", 2, 15)
rec("mushroom_pastry", "oven", [("dough", 1), ("puffcap", 1), ("inkcap", 1)], "mushroom_pastry", 2, 12, civ="sporefolk")
rec("roast_platter", "oven", [("#meat", 2), ("duskroot", 1), ("emberpepper", 1)], "roast_platter", 1, 20)
rec("baked_shellfish", "oven", [("shellmeat", 2), ("butter", 1)], "baked_shellfish", 2, 12, civ="tide_dwellers")
rec("chestnut_bread", "oven", [("dough", 1), ("lake_chestnut", 2)], "chestnut_bread", 2, 12)
rec("sunbean_loaf", "oven", [("dough", 1), ("sunbean", 2)], "sunbean_loaf", 2, 12)
rec("ember_flatbread", "oven", [("dough", 1), ("emberpepper", 1)], "ember_flatbread", 2, 10, civ="forgekin")
rec("sweet_roll", "oven", [("dough", 1), ("#sweet", 1), ("butter", 1)], "sweet_roll", 3, 12)
rec("duskberry_cookie", "oven", [("flour", 1), ("#sweet", 1), ("duskberries", 1)], "duskberry_cookie", 6, 8)
rec("hardtack", "oven", [("flour", 2), ("salt", 1)], "hardtack", 4, 15, basic=True)
rec("baked_potato_oven", "oven", [("minecraft:potato", 2)], "minecraft:baked_potato", 2, 8, basic=True)
# preparation table
rec("flour", "prep", [("glowgrain", 2)], "flour", 1, 3, basic=True)
rec("flour_wheat", "prep", [("minecraft:wheat", 2)], "flour", 1, 3, basic=True)
rec("flour_rice", "prep", [("mire_rice", 2)], "flour", 1, 3, basic=True)
rec("dough", "prep", [("flour", 2), (WATER, 1)], "dough", 3, 3, basic=True)
rec("sugar_from_honeyfruit", "prep", [("honeyfruit", 1)], "minecraft:sugar", 2, 3, basic=True)
rec("butter", "prep", [(MILK, 1), ("salt", 1)], "butter", 3, 5)
rec("spice_blend", "prep", [("emberpepper", 1), ("#herb", 1), ("salt", 1)], "spice_blend", 2, 4)
rec("fruit_salad", "prep", [("#fruit", 3)], "fruit_salad", 1, 3, BOWL, basic=True)
rec("marsh_salad", "prep", [("siltwort_frond", 1), ("sea_grapes", 1), ("duskroot", 1)], "marsh_salad", 1, 3, BOWL)
rec("trail_mix", "prep", [("lake_chestnut", 1), ("duskberries", 1), ("cliffberries", 1)], "trail_mix", 3, 3, basic=True)
rec("hunters_sandwich", "prep", [("#bread", 1), ("#cooked_meat", 1), ("#vegetable", 1)], "hunters_sandwich", 2, 4)
rec("fish_wrap", "prep", [("flatbread", 1), ("#cooked_fish", 1), ("sea_grapes", 1)], "fish_wrap", 2, 4)
rec("tide_roll", "prep", [("mire_rice", 2), ("#fish", 1), ("sea_grapes", 1)], "tide_roll", 3, 4, civ="tide_dwellers")
rec("travel_ration", "prep", [("hardtack", 1), ("jerky", 1), ("dried_fruit", 1)], "travel_ration", 2, 5)
rec("ember_ration", "prep", [("travel_ration", 2), ("spice_blend", 1)], "ember_ration", 2, 5)
rec("frost_ration", "prep", [("travel_ration", 2), ("rimecap", 1)], "frost_ration", 2, 5)
rec("sea_grape_juice", "prep", [("sea_grapes", 3)], "sea_grape_juice", 1, 4, BOTTLE, basic=True)
rec("honeyfruit_juice", "prep", [("honeyfruit", 2)], "honeyfruit_juice", 1, 4, BOTTLE, basic=True)
rec("berry_juice", "prep", [("#fruit", 3)], "berry_juice", 1, 4, BOTTLE, basic=True)
rec("grain_feed", "prep", [("#grain", 3)], "grain_feed", 2, 3, basic=True)
rec("fungal_feed", "prep", [("#mushroom", 3)], "fungal_feed", 2, 3, basic=True)
rec("fruit_mash", "prep", [("#fruit", 3)], "fruit_mash", 2, 3, basic=True)
rec("meat_scraps", "prep", [("#meat", 1), ("minecraft:bone", 1)], "meat_scraps", 3, 3, basic=True)
rec("mineral_lick", "prep", [("niter", 1), ("calcicap", 1), ("salt", 1)], "mineral_lick", 3, 3)
rec("fishing_bait", "prep", [("#meat", 1)], "fishing_bait", 4, 2, basic=True)
rec("fishing_bait_inkcap", "prep", [("inkcap", 2), ("minecraft:bone_meal", 1)], "fishing_bait", 4, 2, basic=True)
# kettle (heated drinks)
rec("mendleaf_tea", "kettle", [("mendleaf_sprig", 1)], "mendleaf_tea", 2, 8, BOTTLE, basic=True)
rec("sunleaf_tea", "kettle", [("sunleaf_blossom", 1), ("#sweet", 1)], "sunleaf_tea", 2, 8, BOTTLE)
rec("moonpetal_tea", "kettle", [("moonpetal_petal", 1)], "moonpetal_tea", 2, 8, BOTTLE)
rec("emberwort_tea", "kettle", [("emberwort_root", 1)], "emberwort_tea", 2, 8, BOTTLE, basic=True)
rec("marsh_tea", "kettle", [("bogwort_leaf", 1), ("#sweet", 1)], "marsh_tea", 2, 8, BOTTLE)
rec("bone_broth", "kettle", [("minecraft:bone", 2), ("salt", 1)], "bone_broth", 3, 15, BOTTLE)
rec("inkcap_infusion", "kettle", [("inkcap", 2)], "inkcap_infusion", 2, 8, BOTTLE)
rec("spiced_cider", "kettle", [("minecraft:apple", 2), ("spice_blend", 1)], "spiced_cider", 3, 10, BOTTLE)
rec("spiced_cider_berries", "kettle", [("#fruit", 3), ("spice_blend", 1)], "spiced_cider", 3, 10, BOTTLE)
rec("rimecap_cooler", "kettle", [("rimecap", 1), ("snowberries", 1)], "rimecap_cooler", 2, 6, BOTTLE)
rec("cryolite_chill", "kettle", [("cryolite", 1)], "cryolite_chill", 3, 6, BOTTLE)
rec("starlily_nectar", "kettle", [("starlily", 1), ("#sweet", 1)], "starlily_nectar", 2, 8, BOTTLE, civ="astrals")
# fermentation crock (long, no heat)
rec("vinegar", "crock", [("#fruit", 2)], "vinegar", 2, 150, BOTTLE, basic=True)
rec("pickled_turnips", "crock", [("frost_turnip", 3), ("vinegar", 1)], "pickled_turnips", 3, 180, civ="stoneborn")
rec("pickled_peppers", "crock", [("emberpepper", 3), ("vinegar", 1)], "pickled_peppers", 3, 180, civ="forgekin")
rec("spore_paste", "crock", [("puffcap", 2), ("inkcap", 1), ("salt", 1)], "spore_paste", 3, 180, civ="sporefolk")
rec("capback_cheese", "crock", [(MILK, 1), ("salt", 1)], "capback_cheese", 4, 240)
rec("glowgrain_kvass", "crock", [("#bread", 1), ("#sweet", 1)], "glowgrain_kvass", 3, 180, BOTTLE, civ="rootfolk")
rec("honeyfruit_fizz", "crock", [("honeyfruit", 2), ("#sweet", 1)], "honeyfruit_fizz", 3, 180, BOTTLE, civ="amberfolk")
rec("berry_shrub", "crock", [("#fruit", 2), ("vinegar", 1), ("#sweet", 1)], "berry_shrub", 3, 150, BOTTLE)
rec("spore_kombucha", "crock", [("inkcap", 1), ("#sweet", 1)], "spore_kombucha", 3, 180, BOTTLE, civ="sporefolk")
# drying rack (one item at a time; seconds per item)
rec("jerky", "rack", [("#meat", 1)], "jerky", 1, 120, basic=True)
rec("dried_fish", "rack", [("#fish", 1)], "dried_fish", 1, 120, basic=True)
rec("dried_fruit", "rack", [("#dried_fruit_src", 1)], "dried_fruit", 1, 90, basic=True)
rec("dried_mushrooms", "rack", [("#mushroom", 1)], "dried_mushrooms", 1, 90, basic=True)
rec("dried_kelp_rack", "rack", [("minecraft:kelp", 1)], "minecraft:dried_kelp", 1, 60, basic=True)
rec("leather_rack", "rack", [("hide", 1)], "minecraft:leather", 1, 150, basic=True)
# herbalist's table (medicine and propagation)
rec("bandage", "herbal", [("#fiber", 2), ("mendleaf_sprig", 1)], "bandage", 2, 4, basic=True)
rec("bandage_paper", "herbal", [("minecraft:paper", 2), ("#herb", 1)], "bandage", 2, 4, basic=True)
rec("mendleaf_poultice_table", "herbal", [("mendleaf_sprig", 3), ("minecraft:paper", 1)], "mendleaf_poultice", 1, 4, basic=True)
rec("cooling_salve", "herbal", [("rimecap", 1), ("creature_oil", 1), ("snowberries", 1)], "cooling_salve", 2, 6)
rec("cooling_salve_cryolite", "herbal", [("cryolite", 1), ("creature_oil", 1)], "cooling_salve", 2, 6)
rec("warming_salve", "herbal", [("emberwort_root", 1), ("creature_oil", 1), ("emberpepper", 1)], "warming_salve", 2, 6)
rec("spore_ward_tincture", "herbal", [("bogwort_leaf", 1), ("sagecap", 1)], "spore_ward_tincture", 2, 6, BOTTLE)
rec("breathing_draught", "herbal", [("siltwort_frond", 2), ("tidecap", 1)], "breathing_draught", 2, 6, BOTTLE)
rec("breathing_draught_kelp", "herbal", [("siltwort_frond", 1), ("minecraft:kelp", 2)], "breathing_draught", 1, 6, BOTTLE)
rec("grounding_paste", "herbal", [("galvanite", 1), ("minecraft:clay_ball", 1)], "grounding_paste", 3, 6)
rec("grounding_paste_storm", "herbal", [("storm_lichen", 2), ("creature_oil", 1)], "grounding_paste", 2, 6)
rec("clear_air_tincture", "herbal", [("calcicap", 1), ("mendleaf_sprig", 1)], "clear_air_tincture", 2, 6, BOTTLE)
rec("restorative_tonic", "herbal", [("mendleaf_sprig", 1), ("sunleaf_blossom", 1), ("honeyfruit", 1)], "restorative_tonic", 2, 8, BOTTLE)
rec("fortifying_elixir", "herbal", [("moonpetal_petal", 1), ("glow_gland", 1)], "fortifying_elixir", 1, 10, BOTTLE)
rec("vigor_draught", "herbal", [("emberpepper", 1), ("sunleaf_blossom", 1)], "vigor_draught", 1, 8, BOTTLE)
rec("venom_antiserum", "herbal", [("venom_gland", 1), ("bogwort_leaf", 1)], "venom_antiserum", 2, 8, BOTTLE)
rec("calming_balm", "herbal", [("moonpetal_petal", 1), ("amber_glob", 1)], "calming_balm", 2, 6)
rec("burn_salve", "herbal", [("sea_grapes", 1), ("amber_glob", 1), ("creature_oil", 1)], "burn_salve", 2, 6)
rec("smelling_salts", "herbal", [("niter", 1), ("calcicap", 1)], "smelling_salts", 2, 4)
rec("purging_draught", "herbal", [("deathbell", 1), ("minecraft:charcoal", 1)], "purging_draught", 1, 10, BOTTLE)
rec("bloodcap_tonic", "herbal", [("bloodcap", 1), ("emberwort_root", 1)], "bloodcap_tonic", 1, 10, BOTTLE)
rec("glow_salve", "herbal", [("glow_gland", 1), ("creature_oil", 1)], "glow_salve", 2, 6)
rec("field_medkit", "herbal", [("bandage", 2), ("mendleaf_poultice", 1), ("antidote_tincture", 1)], "field_medkit", 1, 10)
rec("antidote_tincture_table", "herbal", [("bogwort_leaf", 1), ("bracket_fungus", 1)], "antidote_tincture", 1, 6, BOTTLE, basic=True)
rec("sunleaf_salve_table", "herbal", [("sunleaf_blossom", 1), ("honeyfruit", 1), ("amber_glob", 1)], "sunleaf_salve", 1, 6)
rec("emberwort_tonic_table", "herbal", [("emberwort_root", 1), ("emberpepper", 1)], "emberwort_tonic", 1, 6, BOTTLE)
rec("moonpetal_draught_table", "herbal", [("moonpetal_petal", 1), ("gloomcap", 1)], "moonpetal_draught", 1, 6, BOTTLE)
for herb, plant in (("mendleaf_sprig", "mendleaf"), ("sunleaf_blossom", "sunleaf"), ("emberwort_root", "emberwort"),
                    ("bogwort_leaf", "bogwort"), ("moonpetal_petal", "moonpetal"), ("siltwort_frond", "siltwort")):
    rec(f"propagate_{plant}", "herbal", [(herb, 3), ("compost", 1)], plant, 1, 15, basic=True)

STATIONS = {"pot": "Cooking Pot", "oven": "Clay Oven", "prep": "Preparation Table", "kettle": "Kettle", "crock": "Fermentation Crock",
            "rack": "Drying Rack", "herbal": "Herbalist's Table"}

# Blocks: id → (name, template, recipe(shaped pattern, keys), tooltip)
BLOCKS = {
    "cooking_pot": ("Cooking Pot", "pot", (["I I", "I I", "III"], {"I": "minecraft:iron_ingot"}),
                    "Soups and stews. Needs a fire, campfire or lava below. Cooks up to 4 servings at once."),
    "clay_oven": ("Clay Oven", "oven", (["BBB", "BFB", "BBB"], {"B": "minecraft:cobblestone", "F": "minecraft:furnace"}),
                  "Breads, pies and roasts. Burns fuel."),
    "preparation_table": ("Preparation Table", "table_tool", (["SPS", "L L"], {"S": "minecraft:stone_slab", "P": "#minecraft:planks",
                                                                               "L": "#minecraft:logs"}),
                          "Grinding, mixing, salads, rations, juices and animal feed."),
    "kettle": ("Kettle", "kettle", ([" N ", "C C", "CCC"], {"N": "minecraft:iron_nugget", "C": "minecraft:copper_ingot"}),
               "Teas, broths and infusions. Needs heat below."),
    "fermentation_crock": ("Fermentation Crock", "crock", (["P P", "C C", "CCC"], {"P": "#minecraft:planks", "C": "minecraft:cobblestone"}),
                           "Pickles, cheese, vinegar and fermented drinks. Slow, needs no heat."),
    "drying_rack": ("Drying Rack", "rack", (["SSS", "T T", "S S"], {"S": "minecraft:stick", "T": "minecraft:string"}),
                    "Hang meat, fish, fruit, fungi or hides to dry. Right-click to hang, again to take."),
    "herbalist_table": ("Herbalist's Table", "table_herb", (["MBM", "PPP", "L L"], {"M": "#underdeep:herb", "B": "minecraft:bowl",
                                                                                     "P": "#minecraft:planks", "L": "#minecraft:logs"}),
                        "Medicine, salves, tinctures and herb cuttings."),
    "compost_bin": ("Compost Bin", "compost", (["P P", "P P", "PPP"], {"P": "#minecraft:wooden_slabs"}),
                    "Turns plant and food scraps into compost."),
    "fish_trap": ("Fish Trap", "trap", (["STS", "TBT", "STS"], {"S": "minecraft:stick", "T": "minecraft:string", "B": "fishing_bait"}),
                  "Place in water with bait inside. Catches what lives in these waters, slowly."),
    "feeding_trough": ("Feeding Trough", "trough", (["P P", "PPP"], {"P": "#minecraft:planks"}),
                       "Fill with animal feed: tamed animals nearby eat when hungry and stay productive."),
    "rich_soil": ("Rich Soil", "soil", None, "Crops grow faster on it and it never dries out. Plant crops, herbs or bushes."),
    "bedroll": ("Bedroll", "bedroll", (["WWW", "F F"], {"W": "#minecraft:wool", "F": "fur_tuft"}),
                "Rest on expeditions. Does not move your respawn point unless you sneak while lying down."),
    "camp_awning": ("Camp Awning", "awning", (["WWW", "S S"], {"W": "#minecraft:wool", "S": "minecraft:stick"}),
                    "Shelter: standing near it protects you a little from cold and heat, and makes rest more comfortable."),
    "wooden_chair": ("Wooden Chair", "chair", (["P  ", "PPP", "S S"], {"P": "#minecraft:planks", "S": "minecraft:stick"}), "Sit and rest."),
    "wooden_bench": ("Wooden Bench", "bench", (["PPP", "S S"], {"P": "#minecraft:wooden_slabs", "S": "minecraft:stick"}), "Sit and rest."),
    "wooden_table": ("Wooden Table", "table", (["PPP", "S S", "S S"], {"P": "#minecraft:planks", "S": "minecraft:stick"}),
                     "Furniture. Counts toward a comfortable home."),
    "storage_cabinet": ("Storage Cabinet", "cabinet", (["PPP", "PCP", "PPP"], {"P": "#minecraft:planks", "C": "minecraft:chest"}),
                        "27 slots of storage."),
    "pantry": ("Pantry", "pantry", (["SPS", "PCP", "SPS"], {"S": "minecraft:stick", "P": "#minecraft:planks", "C": "minecraft:chest"}),
               "Food storage: 27 slots for food, drinks and ingredients."),
    "medicine_cabinet": ("Medicine Cabinet", "medcab", (["PGP", "PCP", "PPP"], {"P": "#minecraft:planks", "G": "minecraft:glass_pane",
                                                                                "C": "minecraft:chest"}),
                         "18 slots for medicine and herbs."),
    "supply_crate": ("Supply Crate", "crate", (["SSS", "PCP", "SSS"], {"S": "minecraft:stick", "P": "#minecraft:planks", "C": "minecraft:chest"}),
                     "18 slots of storage. Keeps its contents when broken."),
    "kitchen_counter": ("Kitchen Counter", "counter", (["SSS", "PCP", "PPP"], {"S": "minecraft:smooth_stone_slab", "P": "#minecraft:planks",
                                                                               "C": "minecraft:chest"}),
                        "9 slots of storage. Counts as a kitchen for a comfortable home."),
    "planter_box": ("Planter Box", "planter", (["P P", "PDP"], {"P": "#minecraft:planks", "D": "minecraft:dirt"}),
                    "A rich-soil planter for herbs, flowers, fungi and crops indoors."),
}
PLAIN_RECIPES = [  # shapeless (result, count, ingredients)
    ("rich_soil", 4, ["minecraft:dirt", "minecraft:dirt", "minecraft:dirt", "minecraft:dirt", "compost"]),
    ("sporeglow_soil", 2, ["minecraft:dirt", "minecraft:dirt", "puffcap", "compost"]),
    ("compost", 1, ["minecraft:rotten_flesh", "minecraft:rotten_flesh", "minecraft:rotten_flesh"]),
    ("fishing_bait", 2, ["#underdeep:raw_meats"]),
]
ROASTS = [("sunbean", "roasted_sunbeans"), ("emberpepper", "charred_emberpepper"), ("puffcap", "roasted_puffcap"),
          ("rimecap", "toasted_rimecap"), ("glimmerfin", "cooked_fish_fillet"), ("reef_darter", "cooked_fish_fillet"),
          ("mire_eel", "cooked_fish_fillet")]

COMPOSTABLE = {"duskberries": 0.3, "honeyfruit": 0.5, "snowberries": 0.3, "cliffberries": 0.3, "briar_fruit": 0.3, "sea_grapes": 0.3,
               "duskroot": 0.65, "glowgrain": 0.65, "glowgrain_seeds": 0.3, "mire_rice": 0.65, "lake_chestnut": 0.65,
               "emberpepper": 0.65, "frost_turnip": 0.65, "sunbean": 0.65, "puffcap": 0.65, "inkcap": 0.65, "rimecap": 0.65,
               "cave_truffle": 0.65, "mendleaf_sprig": 0.3, "bogwort_leaf": 0.3, "siltwort_frond": 0.3, "glowfern": 0.3,
               "marsh_fern": 0.3, "goldgrass": 0.3, "ashgrass": 0.3, "silvergrass": 0.3, "static_grass": 0.3, "shardgrass": 0.3,
               "bracket_fungus": 0.65, "minecraft:rotten_flesh": 0.5, "flour": 0.3, "fruit_mash": 0.85}


# =============================================================================================== art
def spec_img(spec, seed):
    kind = spec[0]
    c = spec[1]
    rnd = random.Random(seed)
    if kind == "bowl":
        return art.bowl(c, seed, bits=spec[2] if len(spec) > 2 else None)
    if kind == "bottle":
        return art.bottle(c, seed)
    if kind == "salve":
        return art.salve(c, seed)
    if kind == "pepper":
        return art.pepper(c, seed)
    if kind == "seeds_pile":
        return art.seeds(c, seed, 9)
    if kind == "mushroom_item":
        img = art.new()
        art.disc(img, 8, 7, 4, c, art.shade(c, 0.7))
        for y in range(8, 13):
            art.px(img, 7, y, (230, 220, 200))
            art.px(img, 8, y, (210, 200, 180))
        for x in (6, 9):
            art.px(img, x, 5, art.shade(c, 0.55))
        return art.outline(img, art.shade(c, 0.35))
    img = art.new()
    if kind == "egg":
        for y in range(4, 14):
            w = [2, 3, 4, 4, 5, 5, 5, 4, 4, 3][y - 4]
            for x in range(8 - w, 8 + w):
                art.px(img, x, y, art.shade(c, 1.1 if x < 7 and y < 9 else 0.95))
        art.px(img, 6, 6, (255, 255, 255))
        return art.outline(img, art.shade(c, 0.5))
    if kind == "cup":
        clay = (170, 110, 80)
        for y in range(6, 14):
            for x in range(4, 12):
                art.px(img, x, y, art.shade(clay, 1.1 if x < 6 else 0.9 if x > 9 else 1.0))
        for x in range(5, 11):
            art.px(img, x, 6, c)
            art.px(img, x, 7, art.shade(c, 0.85))
        for y in range(8, 12):
            art.px(img, 12, y, art.shade(clay, 0.8))
        art.px(img, 13, 9, art.shade(clay, 0.8))
        art.px(img, 13, 10, art.shade(clay, 0.8))
        for x, y in ((6, 4), (8, 3), (7, 2), (9, 4)):
            art.px(img, x, y, (230, 230, 235), 150)
        return art.outline(img, (60, 35, 25))
    if kind == "flatbread":
        for x in range(2, 14):
            for y in range(7, 12):
                if (x - 8) ** 2 / 36 + (y - 9.5) ** 2 / 7 <= 1:
                    art.px(img, x, y, art.shade(c, 1.0 + rnd.uniform(-0.08, 0.08)))
        for x, y in ((5, 9), (8, 8), (10, 10), (7, 10)):
            art.px(img, x, y, art.shade(c, 0.7))
        return art.outline(img, art.shade(c, 0.4))
    if kind in ("pie", "pastry"):
        fill = spec[2]
        for x in range(2, 15):
            for y in range(8, 13):
                art.px(img, x, y, art.shade(c, 0.95 if y > 10 else 1.05))
        for x in range(3, 14):
            art.px(img, x, 8, fill if kind == "pie" else art.shade(c, 1.15))
            art.px(img, x, 7, art.shade(c, 1.15))
        if kind == "pie":
            for x in range(4, 13, 3):
                art.line(img, x, 7, x + 1, 9, art.shade(c, 1.25))
        else:
            art.px(img, 7, 9, fill)
            art.px(img, 10, 10, fill)
        return art.outline(img, art.shade(c, 0.4))
    if kind == "cake_bite":
        for x in range(4, 12):
            for y in range(7, 12):
                art.px(img, x, y, art.shade(c, 1.05 if y == 7 else 0.95))
        return art.outline(img, art.shade(c, 0.6))
    if kind == "platter":
        for x in range(1, 15):
            art.px(img, x, 12, (200, 200, 205))
            art.px(img, x, 13, (160, 160, 170))
        art.disc(img, 8, 9, 4, c, art.shade(c, 0.7))
        art.px(img, 4, 11, (90, 150, 70))
        art.px(img, 12, 11, (230, 90, 40))
        return art.outline(img, (60, 50, 50))
    if kind == "shell_dish":
        for x in range(3, 13):
            for y in range(8, 12):
                if (x - 8) ** 2 / 25 + (y - 10) ** 2 / 4 <= 1.1:
                    art.px(img, x, y, art.shade(c, 1.0 if (x + y) % 3 else 0.85))
        art.line(img, 4, 8, 12, 8, art.shade(c, 1.2))
        return art.outline(img, art.shade(c, 0.4))
    if kind == "loaf":
        return art.bread(c, seed)
    if kind == "roll":
        art.disc(img, 8, 9, 4, c, art.shade(c, 0.75))
        art.line(img, 5, 8, 10, 10, art.shade(c, 1.25))
        return art.outline(img, art.shade(c, 0.4))
    if kind == "cookie":
        art.disc(img, 8, 8, 4, c, art.shade(c, 0.8))
        for x, y in ((6, 7), (9, 6), (8, 9), (10, 9)):
            art.px(img, x, y, spec[2])
        return art.outline(img, art.shade(c, 0.4))
    if kind == "hardtack":
        for x in range(3, 13):
            for y in range(4, 13):
                art.px(img, x, y, art.shade(c, 1.0 + rnd.uniform(-0.06, 0.06)))
        for x in range(5, 12, 2):
            for y in range(6, 12, 2):
                art.px(img, x, y, art.shade(c, 0.7))
        return art.outline(img, art.shade(c, 0.45))
    if kind == "trail_mix":
        for _ in range(16):
            x, y = rnd.randrange(3, 13), rnd.randrange(7, 13)
            art.px(img, x, y, rnd.choice([c, (120, 40, 80), (180, 60, 50), (210, 170, 110)]))
        return art.outline(img, (60, 40, 30))
    if kind in ("sandwich", "wrap", "sushi"):
        fill = spec[2]
        if kind == "sandwich":
            for x in range(2, 14):
                art.px(img, x, 7, art.shade(c, 1.1))
                art.px(img, x, 8, (100, 170, 70))
                art.px(img, x, 9, fill)
                art.px(img, x, 10, art.shade(c, 1.0))
                art.px(img, x, 11, art.shade(c, 0.85))
        elif kind == "wrap":
            for x in range(3, 13):
                for y in range(6, 12):
                    art.px(img, x, y, art.shade(c, 1.0 if y < 10 else 0.85))
            art.disc(img, 12, 8, 2, fill)
        else:
            for x in (4, 10):
                art.disc(img, x, 9, 3, (40, 60, 40))
                art.disc(img, x, 9, 2, c)
                art.px(img, x, 9, fill)
        return art.outline(img, art.shade(c, 0.4))
    if kind == "ration":
        paper = (200, 180, 140)
        for x in range(3, 13):
            for y in range(5, 13):
                art.px(img, x, y, art.shade(paper, 1.0 if (x + y) % 5 else 0.9))
        art.line(img, 3, 9, 12, 9, c)
        art.line(img, 8, 5, 8, 12, c)
        return art.outline(img, (70, 55, 40))
    if kind == "jerky":
        for i in range(10):
            art.px(img, 3 + i, 12 - i, art.shade(c, 1.0))
            art.px(img, 4 + i, 12 - i, art.shade(c, 0.8))
            art.px(img, 3 + i, 11 - i, art.shade(c, 1.2))
        return art.outline(img, art.shade(c, 0.45))
    if kind in ("dried_fish", "fish", "eel"):
        if kind == "eel":
            for i in range(12):
                y = 8 + int(2 * __import__("math").sin(i / 2))
                art.px(img, 2 + i, y, c)
                art.px(img, 2 + i, y + 1, art.shade(c, 0.75))
            art.px(img, 13, 8, (20, 20, 20))
        else:
            for x in range(3, 12):
                h = 2 if x in (3, 11) else 3
                for y in range(8 - h // 2 - 1, 8 + h // 2 + 1):
                    art.px(img, x, y, art.shade(c, 1.15 if y < 8 else 0.9))
            for y in (6, 7, 8, 9, 10):
                art.px(img, 12 + (abs(y - 8) > 1), y, art.shade(c, 0.8))
            art.px(img, 4, 7, (20, 20, 30))
        return art.outline(img, art.shade(c, 0.4))
    if kind in ("dried_fruit", "dried_mushroom"):
        for _ in range(5):
            x, y = rnd.randrange(4, 11), rnd.randrange(5, 11)
            art.disc(img, x, y, 1.6, art.shade(c, rnd.uniform(0.8, 1.05)))
        return art.outline(img, art.shade(c, 0.45))
    if kind == "jar":
        glass = (190, 210, 215)
        for y in range(5, 14):
            for x in range(4, 12):
                art.px(img, x, y, c if y > 6 else glass)
        for x in range(4, 12):
            art.px(img, x, 4, (140, 100, 60))
        art.px(img, 5, 8, (255, 255, 255))
        return art.outline(img, (50, 50, 60))
    if kind == "cheese":
        for x in range(3, 13):
            top = 7 + max(0, x - 9)
            for y in range(top, 12):
                art.px(img, x, y, art.shade(c, 1.1 if y == top else 0.95))
        for x, y in ((5, 9), (8, 10), (7, 8)):
            art.px(img, x, y, art.shade(c, 0.75))
        return art.outline(img, art.shade(c, 0.45))
    if kind == "tincture":
        return art.bottle(c, seed, glow=True)
    if kind == "bandage":
        art.disc(img, 8, 8, 5, c, art.shade(c, 0.85))
        art.disc(img, 8, 8, 2, art.shade(c, 0.7))
        art.line(img, 11, 11, 14, 13, c)
        return art.outline(img, (120, 110, 100))
    if kind == "tin":
        for x in range(3, 13):
            for y in range(7, 12):
                art.px(img, x, y, (160, 160, 170) if y > 8 else c)
        art.line(img, 3, 7, 12, 7, art.shade(c, 1.2))
        return art.outline(img, (50, 50, 60))
    if kind == "medkit":
        for x in range(3, 13):
            for y in range(5, 13):
                art.px(img, x, y, art.shade(c, 1.05 if y < 7 else 0.95))
        for i in range(-2, 3):
            art.px(img, 8 + i, 9, (245, 245, 245))
            art.px(img, 8, 9 + i, (245, 245, 245))
        art.line(img, 6, 4, 10, 4, (90, 60, 50))
        return art.outline(img, (70, 20, 20))
    if kind == "sack":
        for y in range(6, 14):
            w = 4 if y > 7 else 3
            for x in range(8 - w, 8 + w):
                art.px(img, x, y, art.shade(c, 1.05 if x < 7 else 0.9))
        art.line(img, 6, 5, 9, 5, (150, 110, 70))
        return art.outline(img, art.shade(c, 0.5))
    if kind == "dough":
        art.disc(img, 8, 9, 4.5, c, art.shade(c, 0.85))
        return art.outline(img, art.shade(c, 0.55))
    if kind == "pile":
        for y in range(8, 13):
            w = (y - 7) + 1
            for x in range(8 - w, 8 + w):
                art.px(img, x, y, art.shade(c, rnd.uniform(0.85, 1.1)))
        return art.outline(img, art.shade(c, 0.5))
    if kind == "butter":
        for x in range(4, 13):
            for y in range(8, 12):
                art.px(img, x, y, art.shade(c, 1.1 if y == 8 else 0.95))
        art.line(img, 3, 12, 13, 12, (220, 220, 225))
        return art.outline(img, art.shade(c, 0.55))
    if kind == "pouch":
        art.disc(img, 8, 10, 4, (170, 130, 90), (130, 95, 60))
        art.line(img, 6, 5, 10, 5, (120, 80, 50))
        art.px(img, 8, 6, c)
        art.px(img, 7, 7, c)
        art.px(img, 9, 7, c)
        return art.outline(img, (60, 40, 25))
    if kind == "block_bit":
        for x in range(4, 12):
            for y in range(6, 13):
                art.px(img, x, y, art.shade(c, rnd.uniform(0.85, 1.1)))
        return art.outline(img, art.shade(c, 0.5))
    if kind == "bait":
        for i in range(8):
            art.px(img, 4 + i, 9 + (i % 2), c)
            art.px(img, 4 + i, 10 + (i % 2), art.shade(c, 0.75))
        art.line(img, 11, 4, 11, 9, (180, 180, 190))
        art.px(img, 10, 9, (180, 180, 190))
        return art.outline(img, art.shade(c, 0.45))
    raise ValueError(kind)


def effect_icon(c, beneficial):
    img = art.new(18, 18)
    for x in range(3, 15):
        for y in range(3, 15):
            if (x - 8.5) ** 2 + (y - 8.5) ** 2 <= 30:
                art.px(img, x, y, art.shade(c, 1.15 if (x + y) < 15 else 0.85))
    mark = (255, 255, 255) if beneficial else (40, 20, 20)
    if beneficial:
        for i in range(-2, 3):
            art.px(img, 8 + i, 9, mark)
            art.px(img, 8, 9 + i, mark)
    else:
        for i in range(-2, 3):
            art.px(img, 8 + i, 8 + i, mark)
            art.px(img, 8 + i, 8 - i, mark)
    return art.outline(img, art.shade(c, 0.4))


# ---------------------------------------------------------------------------------- block art and models
WOOD = (150, 105, 65)
DARKWOOD = (100, 70, 45)
IRON = (160, 160, 168)
STONE = (125, 125, 128)
CANVAS = (210, 195, 160)


def tex(name, img):
    p = os.path.join(ASSETS, "textures", "block", name + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def flat(c, seed, var=0.06):
    img = art.new()
    rnd = random.Random(seed)
    for x in range(16):
        for y in range(16):
            art.px(img, x, y, art.shade(c, 1 + rnd.uniform(-var, var)))
    return img


def framed(c, seed, border):
    img = flat(c, seed)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            art.px(img, p[0], p[1], border)
    return img


def door_face(c, seed, glass=False):
    img = art.planks(c, seed)
    for i in range(16):
        art.px(img, i, 0, art.shade(c, 0.6))
        art.px(img, i, 15, art.shade(c, 0.6))
        art.px(img, 0, i, art.shade(c, 0.6))
        art.px(img, 15, i, art.shade(c, 0.6))
        art.px(img, 7, i, art.shade(c, 0.6))
        art.px(img, 8, i, art.shade(c, 0.7))
    for (x, y) in ((6, 8), (9, 8)):
        art.px(img, x, y, (200, 180, 90))
    if glass:
        for x in range(2, 6):
            for y in range(2, 7):
                art.px(img, x, y, (190, 220, 230))
                art.px(img, x + 8, y, (190, 220, 230))
    return img


def el(frm, to, faces, rot=None):
    e = {"from": frm, "to": to, "faces": {k: {"texture": v} for k, v in faces.items()}}
    if rot:
        e["rotation"] = rot
    return e


def all6(t):
    return {f: t for f in ("north", "south", "east", "west", "up", "down")}


def model(name, elements, textures, parent="block/block", ao=True):
    obj = {"parent": parent, "textures": textures, "elements": elements}
    if not ao:
        obj["ambientocclusion"] = False
    wjson(os.path.join(ASSETS, "models", "block", name + ".json"), obj)


def facing_state(bid, mdl=None):
    mdl = mdl or bid
    wjson(os.path.join(ASSETS, "blockstates", bid + ".json"), {"variants": {
        f"facing={f}": {"model": f"{NS}:block/{mdl}", **({"y": r} if r else {})} for f, r in
        (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})


def simple_state(bid, mdl=None):
    wjson(os.path.join(ASSETS, "blockstates", bid + ".json"), {"variants": {"": {"model": f"{NS}:block/{mdl or bid}"}}})


def legs(t, h=12, inset=1, size=2):
    a, b = inset, 16 - inset - size
    return [el([x, 0, z], [x + size, h, z + size], all6(t)) for x in (a, b) for z in (a, b)]


def block_assets(bid, kind):
    s = nseed()
    T = lambda n: f"{NS}:block/{n}"  # noqa: E731
    if kind == "pot":
        tex("cooking_pot_side", framed(IRON, s, art.shade(IRON, 0.7)))
        top = flat((70, 60, 50), s + 1)
        for x in range(3, 13):
            for y in range(3, 13):
                art.px(top, x, y, (170, 120, 70))
        tex("cooking_pot_top", top)
        model(bid, [el([3, 0, 3], [13, 8, 13], {"north": "#side", "south": "#side", "east": "#side", "west": "#side", "up": "#top",
                                                 "down": "#side"}),
                    el([2, 7, 2], [14, 9, 14], {"north": "#side", "south": "#side", "east": "#side", "west": "#side", "down": "#side"}),
                    el([0, 6, 7], [3, 7, 9], all6("#side")), el([13, 6, 7], [16, 7, 9], all6("#side"))],
              {"side": T("cooking_pot_side"), "top": T("cooking_pot_top"), "particle": T("cooking_pot_side")})
        simple_state(bid)
    elif kind == "kettle":
        copper = (190, 110, 70)
        tex("kettle_side", framed(copper, s, art.shade(copper, 0.7)))
        model(bid, [el([4, 0, 4], [12, 7, 12], all6("#side")), el([6, 7, 6], [10, 8, 10], all6("#side")),
                    el([7, 8, 7], [9, 9, 9], all6("#side")), el([12, 3, 7], [15, 5, 9], all6("#side")),
                    el([2, 4, 7], [4, 7, 9], all6("#side"))], {"side": T("kettle_side"), "particle": T("kettle_side")})
        simple_state(bid)
    elif kind == "oven":
        brick = (170, 110, 80)
        side = art.stone(brick, s, 0.1)
        tex("clay_oven_side", side)
        front = side.copy()
        for x in range(4, 12):
            for y in range(6, 13):
                if (x - 8) ** 2 / 16 + (y - 12) ** 2 / 36 <= 1:
                    art.px(front, x, y, (40, 25, 20) if y < 11 else (230, 120, 40))
        tex("clay_oven_front", front)
        model(bid, [el([0, 0, 0], [16, 16, 16], {"north": "#front", "south": "#side", "east": "#side", "west": "#side", "up": "#side",
                                                  "down": "#side"}),
                    el([10, 16, 10], [14, 22, 14], all6("#side"))],
              {"front": T("clay_oven_front"), "side": T("clay_oven_side"), "particle": T("clay_oven_side")})
        facing_state(bid)
    elif kind in ("table_tool", "table_herb", "table"):
        tex("furniture_wood", art.planks(WOOD, s))
        tex("furniture_dark", art.planks(DARKWOOD, s + 1))
        els = [el([0, 12, 0], [16, 15, 16], all6("#top"))] + legs("#leg", 12)
        texs = {"top": T("furniture_wood"), "leg": T("furniture_dark"), "particle": T("furniture_wood")}
        if kind == "table_tool":
            tex("cutting_board", flat((200, 170, 120), s + 2))
            els.append(el([3, 15, 4], [11, 16, 11], all6("#board")))
            els.append(el([12, 15, 3], [14, 18, 5], all6("#leg")))
            texs["board"] = T("cutting_board")
        if kind == "table_herb":
            tex("mortar", flat(STONE, s + 3))
            tex("herb_bundle", flat((90, 140, 70), s + 4, 0.15))
            els.append(el([9, 15, 9], [13, 18, 13], all6("#mortar")))
            els.append(el([3, 15, 3], [7, 16, 9], all6("#herb")))
            texs.update({"mortar": T("mortar"), "herb": T("herb_bundle")})
        model(bid, els, texs)
        facing_state(bid)
    elif kind == "crock":
        clay = (160, 110, 80)
        tex("crock_side", framed(clay, s, art.shade(clay, 0.75)))
        tex("crock_lid", flat(art.shade(WOOD, 0.9), s + 1))
        model(bid, [el([4, 0, 4], [12, 11, 12], all6("#side")), el([5, 11, 5], [11, 13, 11], all6("#lid")),
                    el([7, 13, 7], [9, 14, 9], all6("#lid"))], {"side": T("crock_side"), "lid": T("crock_lid"), "particle": T("crock_side")})
        simple_state(bid)
    elif kind == "rack":
        tex("rack_wood", flat(DARKWOOD, s))
        model(bid, [el([1, 0, 7], [3, 16, 9], all6("#w")), el([13, 0, 7], [15, 16, 9], all6("#w")),
                    el([1, 14, 7], [15, 15, 9], all6("#w")), el([1, 9, 7], [15, 10, 9], all6("#w"))],
              {"w": T("rack_wood"), "particle": T("rack_wood")}, ao=False)
        facing_state(bid)
    elif kind == "trap":
        tex("fish_trap", framed((140, 110, 70), s, (90, 70, 45)))
        img = art.new()
        for i in range(16):
            for j in range(16):
                if i % 4 == 0 or j % 4 == 0:
                    art.px(img, i, j, (140, 110, 70))
        tex("fish_trap_mesh", img)
        model(bid, [el([2, 0, 2], [14, 10, 14], all6("#mesh")), el([2, 0, 2], [14, 1, 14], all6("#frame"))],
              {"mesh": T("fish_trap_mesh"), "frame": T("fish_trap"), "particle": T("fish_trap")}, ao=False)
        simple_state(bid)
    elif kind == "trough":
        tex("trough_wood", art.planks(WOOD, s))
        tex("trough_feed", flat((210, 190, 120), s + 1, 0.12))
        model(bid, [el([0, 0, 2], [16, 6, 14], {"north": "#w", "south": "#w", "east": "#w", "west": "#w", "up": "#feed", "down": "#w"})],
              {"w": T("trough_wood"), "feed": T("trough_feed"), "particle": T("trough_wood")})
        facing_state(bid)
    elif kind == "soil":
        img = art.stone((80, 55, 35), s, 0.12)
        for _ in range(10):
            art.px(img, random.Random(s).randrange(16), random.Random(s + _).randrange(16), (120, 150, 70))
        tex(bid, img)
        model(bid, [el([0, 0, 0], [16, 16, 16], all6("#all"))], {"all": T(bid), "particle": T(bid)})
        simple_state(bid)
    elif kind == "bedroll":
        tex("bedroll_cloth", framed((150, 60, 50), s, (110, 40, 35)))
        tex("bedroll_pillow", flat((230, 225, 210), s + 1))
        model(bid, [el([1, 0, 1], [15, 2, 15], all6("#cloth")), el([3, 2, 1], [13, 4, 5], all6("#pillow"))],
              {"cloth": T("bedroll_cloth"), "pillow": T("bedroll_pillow"), "particle": T("bedroll_cloth")})
        facing_state(bid)
    elif kind == "awning":
        tex("awning_canvas", framed(CANVAS, s, art.shade(CANVAS, 0.8)))
        tex("awning_pole", flat(DARKWOOD, s + 1))
        model(bid, [el([0, 0, 14], [2, 13, 16], all6("#pole")), el([14, 0, 14], [16, 13, 16], all6("#pole")),
                    el([0, 12, 0], [16, 13, 16], all6("#canvas"), {"origin": [8, 12, 16], "axis": "x", "angle": 22.5})],
              {"canvas": T("awning_canvas"), "pole": T("awning_pole"), "particle": T("awning_canvas")}, ao=False)
        facing_state(bid)
    elif kind == "chair":
        tex("furniture_wood", art.planks(WOOD, s))
        model(bid, [el([2, 7, 2], [14, 9, 14], all6("#w")), el([2, 9, 12], [14, 20, 14], all6("#w"))] + legs("#w", 7, 2),
              {"w": T("furniture_wood"), "particle": T("furniture_wood")})
        facing_state(bid)
    elif kind == "bench":
        tex("furniture_wood", art.planks(WOOD, s))
        model(bid, [el([0, 6, 3], [16, 8, 13], all6("#w")), el([1, 0, 4], [3, 6, 12], all6("#w")), el([13, 0, 4], [15, 6, 12], all6("#w"))],
              {"w": T("furniture_wood"), "particle": T("furniture_wood")})
        facing_state(bid)
    elif kind in ("cabinet", "pantry", "medcab", "counter", "crate"):
        tex(f"{bid}_side", art.planks(art.shade(WOOD, 0.9 if kind != "crate" else 1.05), s))
        if kind == "crate":
            img = art.planks(art.shade(WOOD, 1.05), s + 1)
            for i in range(16):
                art.px(img, i, i, DARKWOOD)
                art.px(img, 15 - i, i, DARKWOOD)
            tex(f"{bid}_front", img)
        else:
            tex(f"{bid}_front", door_face(art.shade(WOOD, 0.9) if kind != "medcab" else (230, 230, 225), s + 2, glass=kind == "medcab"))
        top = T(f"{bid}_side")
        if kind == "counter":
            tex("counter_top", art.stone((190, 190, 185), s + 3, 0.05))
            top = T("counter_top")
        model(bid, [el([0, 0, 0], [16, 16, 16], {"north": "#front", "south": "#side", "east": "#side", "west": "#side", "up": "#top",
                                                  "down": "#side"})],
              {"front": T(f"{bid}_front"), "side": T(f"{bid}_side"), "top": top, "particle": T(f"{bid}_side")})
        facing_state(bid)
    elif kind == "planter":
        tex("planter_side", art.planks(DARKWOOD, s))
        tex("planter_soil", art.stone((80, 55, 35), s + 1, 0.12))
        model(bid, [el([0, 0, 0], [16, 16, 16], {"north": "#side", "south": "#side", "east": "#side", "west": "#side", "up": "#soil",
                                                  "down": "#side"})], {"side": T("planter_side"), "soil": T("planter_soil"),
                                                                      "particle": T("planter_side")})
        simple_state(bid)
    elif kind == "compost":
        # vanilla composter geometry with our textures, plus fill levels like the vanilla composter
        tex("compost_bin_side", art.planks((120, 90, 55), s))
        tex("compost_bin_top", framed((120, 90, 55), s + 1, (80, 60, 35)))
        tex("compost_bin_compost", flat((80, 60, 40), s + 2, 0.15))
        wjson(os.path.join(ASSETS, "models", "block", bid + ".json"), {"parent": "minecraft:block/composter", "textures": {
            "top": T("compost_bin_top"), "bottom": T("compost_bin_side"), "side": T("compost_bin_side"), "particle": T("compost_bin_side")}})
        parts = [{"apply": {"model": f"{NS}:block/{bid}"}}]
        for lvl in range(1, 9):
            m = f"{bid}_contents{lvl}" if lvl < 8 else f"{bid}_contents_ready"
            src = f"minecraft:block/composter_contents{lvl}" if lvl < 8 else "minecraft:block/composter_contents_ready"
            wjson(os.path.join(ASSETS, "models", "block", m + ".json"), {"parent": src, "textures": {
                "content": T("compost_bin_compost"), "inside": T("compost_bin_side")}})
            parts.append({"when": {"level": str(lvl)}, "apply": {"model": f"{NS}:block/{m}"}})
        wjson(os.path.join(ASSETS, "blockstates", bid + ".json"), {"multipart": parts})
    else:
        raise ValueError(kind)


# =============================================================================================== output
def item_model(iid, tex_path=None):
    wjson(os.path.join(ASSETS, "models", "item", iid + ".json"), {"parent": "minecraft:item/generated",
                                                                  "textures": {"layer0": tex_path or f"{NS}:item/{iid}"}})


def save_item(iid, img):
    p = os.path.join(ASSETS, "textures", "item", iid + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def recipe(name, obj):
    wjson(os.path.join(DATA, NS, "recipe", name + ".json"), obj)


def ing(k):
    return {"tag": k[1:]} if k.startswith("#") else {"item": u(k)}


def main(extra=None):
    known = set()
    lang_path = os.path.join(ASSETS, "lang", "en_us.json")
    existing = json.load(open(lang_path)) if os.path.exists(lang_path) else {}
    existing = dict(existing, **(extra or {}))
    for k in existing:
        for p in ("item.underdeep.", "block.underdeep."):
            if k.startswith(p):
                known.add("underdeep:" + k[len(p):])
    new_items = set(u(i) for i in list(F) + list(M) + list(MAT) + list(BLOCKS))
    known |= new_items

    def check(i):
        if i.startswith("#") or i.startswith("minecraft:"):
            return
        assert u(i) in known, f"unknown item {i}"

    for g, items in GROUPS.items():
        for i in items:
            check(i)
    for r in R:
        for k, _ in r["ingredients"]:
            check(k)
        check(r["result"])
        assert r["station"] in STATIONS
    # items
    for iid, d in F.items():
        img = spec_img(d["art"], nseed())
        save_item(iid, img)
        item_model(iid)
        LANG[f"item.{NS}.{iid}"] = d["name"]
        if d["role"]:
            LANG[f"tooltip.{NS}.{iid}"] = d["role"]
    for iid, d in M.items():
        save_item(iid, spec_img(d["art"], nseed()))
        item_model(iid)
        LANG[f"item.{NS}.{iid}"] = d["name"]
        LANG[f"tooltip.{NS}.{iid}"] = d["role"]
    for iid, (spec, role) in MAT.items():
        save_item(iid, spec_img(spec, nseed()))
        item_model(iid)
        LANG[f"item.{NS}.{iid}"] = name_of(iid)
        LANG[f"tooltip.{NS}.{iid}"] = role
    for eid, (nm, good, c) in EFFECTS.items():
        p = os.path.join(ASSETS, "textures", "mob_effect", eid + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        effect_icon(c, good).save(p)
        LANG[f"effect.{NS}.{eid}"] = nm
    # blocks
    for bid, (nm, kind, rcp, tip) in BLOCKS.items():
        block_assets(bid, kind)
        wjson(os.path.join(ASSETS, "models", "item", bid + ".json"), {"parent": f"{NS}:block/{bid}"})
        LANG[f"block.{NS}.{bid}"] = nm
        LANG[f"tooltip.{NS}.{bid}"] = tip
        entry = {"type": "minecraft:item", "name": u(bid)}
        if bid == "supply_crate":
            entry["functions"] = [{"function": "minecraft:copy_components", "source": "block_entity", "include": ["minecraft:container"]}]
        wjson(os.path.join(DATA, NS, "loot_table", "blocks", bid + ".json"), {"type": "minecraft:block", "pools": [
            {"rolls": 1, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        if rcp:
            pattern, keys = rcp
            recipe(bid, {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
                         "key": {k: ing(v) for k, v in keys.items()}, "result": {"id": u(bid), "count": 1}})
    for out, n, ins in PLAIN_RECIPES:
        recipe(f"{out}_from_{'_'.join(i.split(':')[-1] for i in ins[:2])}", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                                              "ingredients": [ing(i) for i in ins],
                                                                              "result": {"id": u(out), "count": n}})
    for src, out in ROASTS:
        for kind, t, suffix in (("minecraft:smelting", 200, "smelting"), ("minecraft:smoking", 100, "smoking"),
                                ("minecraft:campfire_cooking", 600, "campfire")):
            recipe(f"{out}_from_{src}_{suffix}", {"type": kind, "category": "food", "ingredient": ing(src),
                                                    "result": {"id": u(out)}, "experience": 0.35, "cookingtime": t})
    for st, nm in STATIONS.items():
        LANG[f"station.{NS}.{st}"] = nm
    LANG["container.underdeep.station"] = "%s"
    LANG["message.underdeep.station.heat"] = "Needs heat below (fire, campfire, lava or magma)."
    LANG["message.underdeep.station.fuel"] = "Needs fuel."
    LANG["message.underdeep.station.idle"] = "Add ingredients for a recipe."
    LANG["message.underdeep.recipe.learned"] = "New recipe: %s"
    LANG["message.underdeep.hazard.warn"] = "%s"
    LANG["message.underdeep.rested"] = "You feel rested (%s)."
    LANG["message.underdeep.bedroll.spawn"] = "Your respawn point is now at this bedroll."
    LANG["entity.underdeep.seat"] = "Seat"
    # tags
    tags = {f"{NS}/tags/item/raw_meats.json": [u(i) for i in GROUPS["meat"]]}
    for g, items in GROUPS.items():
        if items:
            tags[f"{NS}/tags/item/{g}.json"] = [u(i) for i in items]
    tags["minecraft/tags/block/mineable/axe.json"] = [u(b) for b, v in BLOCKS.items() if v[1] in (
        "table_tool", "table_herb", "table", "rack", "trap", "trough", "chair", "bench", "cabinet", "pantry", "medcab", "counter", "crate",
        "planter", "compost")]
    tags["minecraft/tags/block/mineable/pickaxe.json"] = [u(b) for b, v in BLOCKS.items() if v[1] in ("pot", "kettle", "oven", "crock")]
    tags["minecraft/tags/block/mineable/shovel.json"] = [u("rich_soil")]
    tags[f"{NS}/tags/block/heat_sources.json"] = ["minecraft:magma_block", "minecraft:lava", "minecraft:fire", "minecraft:soul_fire"]
    for soil in ("plant_soil", "fungal_soil", "wet_soil", "frozen_soil", "volcanic_soil", "mineral_soil"):
        tags[f"{NS}/tags/block/{soil}.json"] = [u("rich_soil"), u("planter_box")]
    for path, vals in tags.items():
        full = os.path.join(DATA, path)
        cur = json.load(open(full)).get("values", []) if os.path.exists(full) else []
        wjson(full, {"replace": False, "values": cur + [v for v in vals if v not in cur]})
    # the content file the mod loads
    content = {
        "groups": {g: [u(i) for i in items] for g, items in GROUPS.items() if items},
        "nutrition": {u(k): v.split(",") for k, v in NUTRITION.items()} | {u(k): [g for g in d["groups"].split(",") if g]
                                                                             for k, d in F.items()},
        "foods": {u(k): {kk: vv for kk, vv in d.items() if kk not in ("art", "name")} for k, d in F.items()},
        "medicines": {u(k): {kk: vv for kk, vv in d.items() if kk not in ("art", "name")} for k, d in M.items()},
        "materials": [u(k) for k in MAT],
        "effects": {k: {"beneficial": v[1], "color": (v[2][0] << 16) | (v[2][1] << 8) | v[2][2]} for k, v in EFFECTS.items()},
        "recipes": R,
        "stations": STATIONS,
        "compostable": {u(k): v for k, v in COMPOSTABLE.items()},
    }
    wjson(os.path.join(DATA, NS, "survival", "content.json"), content)
    print(f"stage 7: {len(F)} foods/drinks, {len(M)} medicines, {len(MAT)} materials, {len(R)} station recipes, {len(BLOCKS)} blocks")
    return LANG


if __name__ == "__main__":
    lang = main()
    lang_path = os.path.join(ASSETS, "lang", "en_us.json")
    cur = json.load(open(lang_path))
    cur.update(lang)
    wjson(lang_path, dict(sorted(cur.items())))
