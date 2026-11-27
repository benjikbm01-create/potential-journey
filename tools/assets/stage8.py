#!/usr/bin/env python3
"""
Stage 8 content: equipment materials and processing, weapons, armor, tools, accessories, upgrades, exploration gear,
workstations, and underground alternatives for vanilla materials that normally come from the surface or the Nether.
Writes data/underdeep/equipment/content.json (loaded by the mod), textures (stage8_art.py), models, recipes, tags and
names. Called by stage2_assets.py after stage7.py, or alone:  PYTHONHASHSEED=0 python3 tools/assets/stage8.py

Design rules (docs/EQUIPMENT.md): every material has a role, not just bigger numbers; recipes use only obtainable
items; basic equipment uses the crafting table, advanced equipment the forge; stats are bounded.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import stage8_art as A  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "underdeep")
DATA = os.path.join(ROOT, "data")
NS = "underdeep"
LANG = {}
TAGS = {}


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def u(i):
    return i if ":" in i or i.startswith("#") else f"{NS}:{i}"


def ing(k):
    return {"tag": k[1:]} if k.startswith("#") else {"item": u(k)}


def recipe(name, obj):
    wjson(os.path.join(DATA, NS, "recipe", name + ".json"), obj)


def shapeless(name, out, n, ins, cat="misc"):
    recipe(name, {"type": "minecraft:crafting_shapeless", "category": cat, "ingredients": [ing(i) for i in ins],
                  "result": {"id": u(out), "count": n}})


def shaped(name, out, n, pattern, keys, cat="misc"):
    used = set("".join(pattern)) - {" "}
    recipe(name, {"type": "minecraft:crafting_shaped", "category": cat, "pattern": pattern,
                  "key": {k: ing(v) for k, v in keys.items() if k in used}, "result": {"id": u(out), "count": n}})


def smelt(name, src, out, xp=0.5, t=200, blast=True):
    recipe(name + "_smelting", {"type": "minecraft:smelting", "category": "misc", "ingredient": ing(src), "result": {"id": u(out)},
                                "experience": xp, "cookingtime": t})
    if blast:
        recipe(name + "_blasting", {"type": "minecraft:blasting", "category": "misc", "ingredient": ing(src), "result": {"id": u(out)},
                                    "experience": xp, "cookingtime": t // 2})


def tag(path, *vals):
    TAGS.setdefault(path, [])
    for v in vals:
        if u(v) not in TAGS[path]:
            TAGS[path].append(u(v))


def name_of(i):
    return " ".join(w.capitalize() for w in i.split("_"))


# ============================================================================== underground alternatives (8A)
def alternatives():
    """Nether portals are disabled and there is no surface: underground routes to the vanilla materials that need them."""
    shapeless("blaze_rod_from_emberite", "minecraft:blaze_rod", 1, ["emberite", "emberite", "sulfur", "minecraft:stick"])
    shapeless("nether_wart_from_bloodcap", "minecraft:nether_wart", 2, ["bloodcap", "sulfur"])
    shapeless("name_tag_from_paper", "minecraft:name_tag", 1, ["minecraft:paper", "minecraft:string", "minecraft:iron_nugget"])
    shapeless("honey_bottle_from_honeyfruit", "minecraft:honey_bottle", 1, ["honeyfruit", "honeyfruit", "minecraft:glass_bottle"])
    # the netherite upgrade template normally comes from bastions (Nether): forge one from umbralite and diamonds
    shaped("netherite_template_from_umbralite", "minecraft:netherite_upgrade_smithing_template", 1, ["DUD", "DOD", "DDD"],
           {"D": "minecraft:diamond", "U": "umbralite_ingot", "O": "minecraft:obsidian"})


# ============================================================================== materials
# colors: (light, mid, dark); tool: (mining level, durability, speed, damage bonus, enchantability, repair)
# armor: (protection h/c/l/b, toughness, knockback resistance, durability multiplier, enchantability, repair,
#         per-piece protection {hazard: fraction}, per-piece attributes [(attr, amount, op)], piece-specific attributes)
MAT = {}


def material(mid, name, colors, tool=None, armor=None, special="", role="", ingot=None):
    MAT[mid] = {"name": name, "colors": colors, "tool": tool, "armor": armor, "special": special, "role": role, "ingot": ingot or f"{mid}_ingot"}


material("copper", "Copper", ((236, 160, 120), (192, 108, 74), (124, 62, 40)),
         tool=("stone", 210, 5.0, 1.5, 15, "minecraft:copper_ingot"),
         armor=((2, 4, 3, 1), 0, 0, 11, 14, "minecraft:copper_ingot", {}, [], {}),
         special="", role="Cheap early metal between stone and iron. Conducts static: copper armour makes static charge worse.",
         ingot="minecraft:copper_ingot")
material("steel", "Steel", ((214, 220, 226), (150, 158, 168), (82, 88, 98)),
         tool=("iron", 520, 6.5, 2.5, 10, "steel_ingot"),
         armor=((2, 6, 5, 2), 1.0, 0, 20, 9, "steel_ingot", {}, [], {}),
         role="Iron refined with coal in the alloy furnace: tougher and longer-lasting than iron. The structural metal.")
material("embersteel", "Embersteel", ((255, 178, 96), (204, 88, 40), (112, 38, 22)),
         tool=("diamond", 760, 7.0, 3.0, 12, "embersteel_ingot"),
         armor=((3, 6, 5, 2), 1.0, 0, 26, 12, "embersteel_ingot", {"heat": 0.2}, [("generic.burning_time", -0.15, "add_multiplied_base")], {}),
         special="heat", role="Steel forged with emberite from the Ashen Hollows: weapons set targets alight, armour shields from heat and fire.")
material("rimesteel", "Rimesteel", ((214, 236, 255), (124, 176, 220), (58, 94, 140)),
         tool=("diamond", 700, 7.0, 3.0, 12, "rimesteel_ingot"),
         armor=((3, 6, 5, 2), 1.0, 0, 26, 12, "rimesteel_ingot", {"cold": 0.2}, [], {}),
         special="frost", role="Steel tempered with Everfrost cryolite: weapons slow what they hit, armour keeps the cold out.")
material("galvanic", "Galvanic", ((156, 214, 255), (70, 132, 214), (34, 62, 122)),
         tool=("iron", 400, 8.5, 2.0, 16, "galvanic_ingot"),
         armor=((2, 5, 4, 2), 0.5, 0, 18, 16, "galvanic_ingot", {"static": 0.25}, [], {}),
         special="static", role="Copper bronzed with Stormstone galvanite: very fast to mine and swing, grounds static, shocks wet targets.")
material("starsilver", "Starsilver", ((246, 248, 255), (196, 206, 230), (118, 128, 160)),
         tool=("diamond", 540, 9.0, 2.5, 22, "starsilver_ingot"),
         armor=((2, 5, 4, 2), 0, 0, 22, 25, "starsilver_ingot", {}, [("generic.movement_speed", 0.02, "add_multiplied_base")], {}),
         special="radiant", role="Light Starlight Caverns metal: fast tools, the best enchanting, light armour; strikes the undead harder.")
material("umbralite", "Umbralite", ((110, 96, 140), (58, 48, 78), (24, 18, 34)),
         tool=("netherite", 2100, 8.5, 4.5, 12, "umbralite_ingot"),
         armor=((3, 8, 6, 3), 3.0, 0.1, 40, 12, "umbralite_ingot", {}, [], {}),
         special="heavy", role="The Abyss' dense black metal: the strongest general equipment; heavy blows pierce armour.")
material("nacre", "Nacre", ((250, 244, 236), (210, 200, 196), (140, 126, 130)),
         tool=("iron", 420, 6.0, 2.5, 18, "pearl"),
         armor=((2, 5, 4, 2), 0.5, 0, 18, 18, "pearl", {"pressure": 0.25}, [],
                {"helmet": [("generic.oxygen_bonus", 2.0, "add_value"), ("player.submerged_mining_speed", 0.6, "add_value")],
                 "leggings": [("generic.water_movement_efficiency", 0.25, "add_value")],
                 "boots": [("generic.water_movement_efficiency", 0.25, "add_value")]}),
         special="aquatic", role="Pearl and shell from the flooded regions: diving gear (long breath, fast swimming, underwater mining) and"
                               " weapons that hit hard in water.", ingot="pearl")
material("fossil", "Fossil", ((236, 226, 200), (196, 180, 150), (122, 104, 82)),
         tool=("stone", 300, 4.5, 2.5, 8, "fossil_fragment"),
         armor=((2, 5, 4, 2), 0, 0.1, 15, 8, "fossil_fragment", {}, [], {}),
         special="", role="Petrified bone and jasper: cheap, heavy blunt weapons and sturdy armour that resists knockback.",
         ingot="fossil_fragment")
material("malachite", "Malachite", ((150, 230, 170), (60, 160, 100), (24, 92, 56)),
         tool=("iron", 950, 5.5, 2.0, 9, "malachite"),
         role="Hollow Peaks stone that holds an edge forever: very durable tools for long mining trips.", ingot="malachite")
material("fur", "Furlined", ((222, 206, 180), (164, 134, 102), (96, 70, 52)),
         armor=((1, 3, 2, 1), 0, 0, 9, 15, "fur_lining", {"cold": 0.25}, [], {}),
         role="Warm clothing from shed fur: light protection that keeps out the Everfrost cold.", ingot="fur_lining")
material("scale", "Scale", ((140, 210, 190), (66, 150, 132), (30, 84, 76)),
         armor=((2, 4, 3, 1), 0, 0, 15, 13, "scale_plate", {"pressure": 0.1}, [("generic.water_movement_efficiency", 0.08, "add_value")], {}),
         role="Overlapping shed scales: light armour that swims well.", ingot="scale_plate")
material("shell", "Shellplate", ((226, 206, 160), (178, 146, 96), (104, 80, 48)),
         armor=((2, 5, 4, 2), 0.5, 0, 17, 10, "shell_plate", {"spores": 0.2, "fumes": 0.2}, [], {}),
         role="Sealed shell plates with silk seams: keeps spores and fumes out.", ingot="shell_plate")
material("silk", "Silkweave", ((246, 242, 236), (214, 206, 196), (150, 140, 130)),
         armor=((1, 3, 2, 1), 0, 0, 12, 20, "silk_cloth", {}, [("generic.movement_speed", 0.03, "add_multiplied_base")],
                {"boots": [("generic.safe_fall_distance", 3.0, "add_value")]}),
         role="Light woven silk: the explorer's clothing, quick on the feet and soft landings.", ingot="silk_cloth")
material("glimmer", "Glimmer", ((255, 250, 200), (236, 220, 130), (160, 140, 70)),
         tool=("stone", 260, 6.0, 2.0, 20, "glimmer_shard"),
         special="glimmer", role="Crystal Deep glimmer: luminous lenses, lanterns and a stiletto that marks its targets.", ingot="glimmer_shard")
material("iron", "Iron", ((236, 236, 236), (190, 190, 190), (110, 110, 110)),
         tool=("iron", 250, 6.0, 2.0, 14, "minecraft:iron_ingot"), ingot="minecraft:iron_ingot", role="Vanilla iron.")
material("diamond", "Diamond", ((190, 255, 250), (80, 220, 210), (30, 120, 120)),
         tool=("diamond", 1561, 8.0, 3.0, 10, "minecraft:diamond"), ingot="minecraft:diamond", role="Vanilla diamond.")

# ============================================================================== weapons
FAMILY = {  # base damage add, attack speed (total), reach add, knockback add, durability mult
    "sword": (3.0, 1.6, 0.0, 0.0, 1.0), "dagger": (1.5, 2.4, -0.25, 0.0, 0.8), "greatsword": (5.0, 0.9, 0.5, 0.3, 1.2),
    "battleaxe": (5.5, 0.9, 0.0, 0.2, 1.1), "spear": (3.5, 1.3, 1.0, 0.0, 0.9), "warhammer": (6.0, 0.75, 0.25, 1.0, 1.3),
    "mace": (4.5, 1.1, 0.0, 0.5, 1.2)}
FAMILY_ROLE = {"sword": "Reliable all-rounder; sweeping strikes.", "dagger": "Fast, light, short; many quick hits.",
               "greatsword": "Slow, heavy, long; big sweeping blows.", "battleaxe": "Slow heavy chops that disable shields; also fells trees.",
               "spear": "Strikes from a step further away.", "warhammer": "Very slow crushing blows with strong knockback.",
               "mace": "Steady blunt blows with knockback."}
SPECIAL_NAMES = {"nacre_sword": "nacre_cutlass", "nacre_spear": "nacre_harpoon", "fossil_mace": "fossil_club", "starsilver_spear": "starsilver_glaive",
                 "glimmer_dagger": "glimmer_stiletto", "malachite_mace": "malachite_maul"}
WEAPON_SETS = {"copper": ["sword", "dagger", "spear"], "steel": ["sword", "dagger", "greatsword", "battleaxe", "spear", "warhammer", "mace"],
               "embersteel": ["sword", "greatsword", "warhammer"], "rimesteel": ["sword", "spear", "dagger"],
               "galvanic": ["sword", "dagger", "spear"], "starsilver": ["sword", "dagger", "spear"],
               "umbralite": ["sword", "greatsword", "battleaxe", "warhammer"], "nacre": ["sword", "spear"], "fossil": ["mace", "battleaxe"],
               "malachite": ["mace"], "glimmer": ["dagger"], "iron": ["dagger", "greatsword", "spear", "warhammer", "battleaxe", "mace"],
               "diamond": ["greatsword", "spear", "warhammer"]}
FORGED = {"embersteel", "rimesteel", "galvanic", "starsilver", "umbralite", "nacre", "glimmer"}
WEAPONS = []
for m, fams in WEAPON_SETS.items():
    for f in fams:
        wid = SPECIAL_NAMES.get(f"{m}_{f}", f"{m}_{f}")
        d, s, r, kb, dm = FAMILY[f]
        t = MAT[m]["tool"]
        WEAPONS.append({"id": wid, "family": f, "material": m, "damage": round(d + t[3] * (1.2 if f in ("greatsword", "warhammer") else 1.0), 2),
                        "speed": s + (0.1 if m in ("galvanic", "starsilver") and f != "warhammer" else 0), "reach": r, "knockback": kb,
                        "durability": int(t[1] * dm), "special": MAT[m]["special"]})

BOWS = [  # id, name, draw ticks, power multiplier, durability, recipe
    {"id": "lumenwood_shortbow", "draw": 12, "power": 0.85, "durability": 300},
    {"id": "spirebark_longbow", "draw": 30, "power": 1.35, "durability": 420},
    {"id": "horn_recurve_bow", "draw": 18, "power": 1.1, "durability": 520},
]
ARROWS = [  # id, damage (vanilla 2), effect, fire seconds
    {"id": "heavy_arrow", "damage": 3.0, "effect": None, "fire": 0},
    {"id": "frost_arrow", "damage": 2.0, "effect": ["minecraft:slowness", 4, 1], "fire": 0},
    {"id": "venom_arrow", "damage": 2.0, "effect": ["minecraft:poison", 4, 0], "fire": 0},
    {"id": "ember_arrow", "damage": 2.0, "effect": None, "fire": 4},
]

# ============================================================================== tools
TOOL_SETS = {"copper": ["pickaxe", "axe", "shovel", "hoe"], "steel": ["pickaxe", "axe", "shovel", "hoe"],
             "malachite": ["pickaxe", "axe", "shovel"], "starsilver": ["pickaxe", "axe"], "galvanic": ["pickaxe"], "embersteel": ["pickaxe"],
             "nacre": ["pickaxe"], "umbralite": ["pickaxe", "axe", "shovel", "hoe"]}
TOOLS = []
for m, kinds in TOOL_SETS.items():
    for k in kinds:
        TOOLS.append({"id": f"{m}_{k}", "kind": k, "material": m, "special": "smelting" if (m == "embersteel" and k == "pickaxe") else
                      ("aquatic" if m == "nacre" else "")})
SPECIAL_TOOLS = [  # specialized tools (forge)
    {"id": "steel_hammer", "kind": "hammer", "material": "steel", "special": "area"},
    {"id": "umbralite_hammer", "kind": "hammer", "material": "umbralite", "special": "area"},
    {"id": "steel_excavator", "kind": "excavator", "material": "steel", "special": "area"},
    {"id": "steel_lumber_axe", "kind": "lumber_axe", "material": "steel", "special": "fell"},
    {"id": "steel_sickle", "kind": "sickle", "material": "steel", "special": "reap"},
    {"id": "prospectors_pick", "kind": "prospector", "material": "galvanic", "special": "prospect"},
]
TOOLS += SPECIAL_TOOLS
TOOL_ROLE = {"hammer": "Mines a 3x3 area of stone (sneak to mine one block). Slow.", "excavator": "Digs a 3x3 area of soft ground (sneak for one).",
             "lumber_axe": "Fells a whole small tree at once (up to 48 logs, only logs that touch leaves).",
             "sickle": "Harvests and replants mature crops in a 3x3 area; cuts plants.",
             "prospector": "Right-click: senses which ores lie within 8 blocks and roughly which way (cooldown).",
             "smelting": "Smelts the ores it mines into ingots.", "aquatic": "Mines at full speed underwater."}

# ============================================================================== armor sets
ARMOR_SETS = ["copper", "steel", "embersteel", "rimesteel", "galvanic", "starsilver", "umbralite", "nacre", "fossil", "fur", "scale", "shell",
              "silk"]
PIECES = ["helmet", "chestplate", "leggings", "boots"]
PIECE_NAMES = {"nacre": {"helmet": "Diving Helmet", "chestplate": "Diving Harness", "leggings": "Diving Leggings", "boots": "Swim Fins"},
               "shell": {"helmet": "Filter Mask"}, "silk": {"boots": "Springstep Boots"}, "fur": {"chestplate": "Fur Coat"}}

# ============================================================================== accessories
ACCESSORIES = []


def acc(aid, slot, desc, attrs=(), protect=None, flags=()):
    ACCESSORIES.append({"id": aid, "slot": slot, "desc": desc, "attrs": [list(a) for a in attrs], "protect": protect or {}, "flags": list(flags)})


acc("ember_ring", "ring", "Heat and fire exposure builds 35% slower.", protect={"heat": 0.35})
acc("rime_ring", "ring", "Cold exposure builds 35% slower.", protect={"cold": 0.35})
acc("galvanic_ring", "ring", "Static charge builds 35% slower.", protect={"static": 0.35})
acc("pearl_ring", "ring", "Hold your breath longer.", attrs=[("generic.oxygen_bonus", 1.0, "add_value")])
acc("opal_ring", "ring", "+1 Luck (better fishing and loot).", attrs=[("generic.luck", 1.0, "add_value")])
acc("jasper_ring", "ring", "+1 armour.", attrs=[("generic.armor", 1.0, "add_value")])
acc("malachite_ring", "ring", "Mine faster.", attrs=[("player.mining_efficiency", 3.0, "add_value")])
acc("starsilver_ring", "ring", "Move 4% faster.", attrs=[("generic.movement_speed", 0.04, "add_multiplied_base")])
acc("umbral_ring", "ring", "+1.5 attack damage but 2 less maximum health.",
    attrs=[("generic.attack_damage", 1.5, "add_value"), ("generic.max_health", -2.0, "add_value")])
acc("copper_ring", "ring", "Swing 6% faster.", attrs=[("generic.attack_speed", 0.06, "add_multiplied_base")])
acc("horn_ring", "ring", "Your blows knock back harder.", attrs=[("generic.attack_knockback", 0.5, "add_value")])
acc("spore_filter_amulet", "amulet", "Spores and fumes build 35% slower.", protect={"spores": 0.35, "fumes": 0.35})
acc("heartstone_amulet", "amulet", "+2 maximum health.", attrs=[("generic.max_health", 2.0, "add_value")])
acc("nacre_amulet", "amulet", "Swim faster.", attrs=[("generic.water_movement_efficiency", 0.25, "add_value")])
acc("fossil_amulet", "amulet", "Resist knockback.", attrs=[("generic.knockback_resistance", 0.25, "add_value")])
acc("amber_amulet", "amulet", "Burn for half as long.", attrs=[("generic.burning_time", -0.5, "add_multiplied_base")])
acc("warden_amulet", "amulet", "+1 armour toughness.", attrs=[("generic.armor_toughness", 1.0, "add_value")])
acc("climbing_claws", "charm", "Climb walls by pressing against them (sneak to hold on).", flags=["climb"])
acc("feather_charm", "charm", "Fall 4 blocks further without harm.", attrs=[("generic.safe_fall_distance", 4.0, "add_value")])
acc("stride_charm", "charm", "Step up full blocks without jumping.", attrs=[("generic.step_height", 0.5, "add_value")])
acc("miners_lamp", "charm", "Lets you see in darkness (night vision while it is dark around you).", flags=["lamp"])
acc("runners_charm", "charm", "Sprinting tires you 30% less.", flags=["runner"])
acc("healers_charm", "charm", "Medicine cooldowns are 25% shorter.", flags=["healer"])
acc("traders_token", "charm", "Merchants give you slightly better prices (5%).", flags=["trader"])
acc("foragers_charm", "charm", "Sometimes picks an extra handful when you harvest bushes and herbs.", flags=["forager"])
acc("wayfinders_charm", "charm", "Shows your depth, region and heading while you hold a compass or gauge... and always, faintly.", flags=["wayfinder"])
acc("anglers_charm", "charm", "+1 Luck while fishing, and bait lasts twice as long.", flags=["angler"])

# ============================================================================== upgrades
UPGRADES = []


def upg(uid, applies, group, desc, attrs=(), durability=0.0, protect=None, flags=(), kit=None):
    UPGRADES.append({"id": uid, "applies": list(applies), "group": group, "desc": desc, "attrs": [list(a) for a in attrs],
                     "durability": durability, "protect": protect or {}, "flags": list(flags), "kit": kit or (uid + "_kit")})


W, T, H, C, L, B = "weapon", "tool", "helmet", "chestplate", "leggings", "boots"
ARM = [H, C, L, B]
upg("honed_edge", [W], "edge", "+1 attack damage.", attrs=[("generic.attack_damage", 1.0, "add_value")])
upg("balanced_grip", [W], "grip", "Swing 10% faster.", attrs=[("generic.attack_speed", 0.1, "add_multiplied_base")])
upg("weighted_pommel", [W], "grip", "+0.5 damage and knockback, 5% slower swings.",
    attrs=[("generic.attack_damage", 0.5, "add_value"), ("generic.attack_knockback", 0.5, "add_value"), ("generic.attack_speed", -0.05, "add_multiplied_base")])
upg("long_haft", [W], "grip", "Reach half a block further, 5% slower swings.",
    attrs=[("player.entity_interaction_range", 0.5, "add_value"), ("generic.attack_speed", -0.05, "add_multiplied_base")])
upg("venom_coating", [W], "coating", "Hits poison for 3 seconds.", flags=["venom"])
upg("ember_coating", [W], "coating", "Hits set the target alight for 3 seconds.", flags=["ember"])
upg("frost_coating", [W], "coating", "Hits slow the target for 2 seconds.", flags=["frost"])
upg("glimmer_coating", [W], "coating", "Hits make the target glow for 6 seconds.", flags=["glimmer"])
upg("reinforced_head", [T], "head", "+30% durability.", durability=0.3)
upg("honed_bit", [T], "head", "Mines faster.", attrs=[("player.mining_efficiency", 4.0, "add_value")])
upg("aquatic_grip", [T], "grip", "Mine faster underwater.", attrs=[("player.submerged_mining_speed", 0.5, "add_value")])
upg("malachite_bracing", [T, W], "brace", "+60% durability, but mines and swings 5% slower.", durability=0.6,
    attrs=[("player.mining_efficiency", -1.0, "add_value"), ("generic.attack_speed", -0.05, "add_multiplied_base")])
upg("insulated_lining", ARM, "lining", "Cold exposure builds 20% slower.", protect={"cold": 0.2})
upg("cooling_lining", ARM, "lining", "Heat exposure builds 20% slower.", protect={"heat": 0.2})
upg("spore_filter", [H, C], "lining", "Spores and fumes build 20% slower.", protect={"spores": 0.2, "fumes": 0.2})
upg("grounding_mesh", ARM, "lining", "Static charge builds 20% slower.", protect={"static": 0.2})
upg("sealed_seams", [H, C], "lining", "Hold your breath longer.", attrs=[("generic.oxygen_bonus", 1.0, "add_value")])
upg("reinforced_plating", ARM, "plating", "+1 armour.", attrs=[("generic.armor", 1.0, "add_value")])
upg("toughened_plating", ARM, "plating", "+1 armour toughness, 2% slower movement.",
    attrs=[("generic.armor_toughness", 1.0, "add_value"), ("generic.movement_speed", -0.02, "add_multiplied_base")])
upg("lightened_frame", ARM, "plating", "2% faster movement.", attrs=[("generic.movement_speed", 0.02, "add_multiplied_base")])
upg("gripped_soles", [B], "plating", "Fall 2 blocks further without harm.", attrs=[("generic.safe_fall_distance", 2.0, "add_value")])
upg("lamp_mount", [H], "lining", "A small lamp: see in darkness while it is dark around you.", flags=["lamp"])
upg("opal_inlay", [W, T] + ARM, "gem", "+0.5 Luck.", attrs=[("generic.luck", 0.5, "add_value")])
upg("jasper_inlay", [W] + ARM, "gem", "+0.5 attack damage (weapons) or +0.5 armour.",
    attrs=[("generic.attack_damage", 0.5, "add_value", "weapon"), ("generic.armor", 0.5, "add_value", "armor")])
upg("malachite_inlay", [W, T] + ARM, "gem", "+15% durability.", durability=0.15)
upg("cryolite_inlay", ARM, "gem", "Cold exposure builds 10% slower.", protect={"cold": 0.1})
upg("emberite_inlay", ARM, "gem", "Heat exposure builds 10% slower.", protect={"heat": 0.1})
upg("galvanite_inlay", [W, T] + ARM, "gem", "Swing 4% faster; static builds 10% slower (armour).",
    attrs=[("generic.attack_speed", 0.04, "add_multiplied_base", "weapon")], protect={"static": 0.1})
upg("pearl_inlay", [H, C], "gem", "Hold your breath a little longer.", attrs=[("generic.oxygen_bonus", 0.5, "add_value")])
upg("starsilver_inlay", [W, T] + ARM, "gem", "1% faster movement.", attrs=[("generic.movement_speed", 0.01, "add_multiplied_base")])
upg("glimmer_inlay", [W], "gem", "Hits make the target glow for 3 seconds.", flags=["glimmer_short"])
upg("amber_inlay", ARM, "gem", "Burn 15% shorter.", attrs=[("generic.burning_time", -0.15, "add_multiplied_base")])
upg("bow_string_silk", ["bow"], "string", "Draw 15% faster.", flags=["draw_fast"])
upg("bow_limbs_horn", ["bow"], "limbs", "Arrows fly 10% harder.", flags=["power"])

# ============================================================================== components and processing
COMPONENTS = {  # id: (art kind, color, role)
    "steel_ingot": ("ingot", MAT["steel"]["colors"][1], "Alloy furnace: iron and coal. Steel equipment, forge, upgrades."),
    "embersteel_ingot": ("ingot", MAT["embersteel"]["colors"][1], "Alloy furnace: steel and emberite. Heat-forged equipment."),
    "rimesteel_ingot": ("ingot", MAT["rimesteel"]["colors"][1], "Alloy furnace: steel and cryolite. Cold-tempered equipment."),
    "galvanic_ingot": ("ingot", MAT["galvanic"]["colors"][1], "Alloy furnace: copper and galvanite. Fast, conductive equipment."),
    "crushed_iron": ("pile", (200, 160, 140), "Ore crusher: smelts to iron (crushing gives half again as much metal)."),
    "crushed_copper": ("pile", (210, 130, 90), "Ore crusher: smelts to copper."),
    "crushed_gold": ("pile", (240, 210, 90), "Ore crusher: smelts to gold."),
    "crushed_starsilver": ("pile", (220, 226, 240), "Ore crusher: smelts to starsilver."),
    "crushed_umbralite": ("pile", (80, 66, 104), "Ore crusher: smelts to umbralite."),
    "cut_opal": ("gem", (240, 230, 250), "Gem cutter: for rings, amulets and inlays."),
    "polished_jasper": ("gem_round", (190, 80, 60), "Gem cutter: for rings and inlays."),
    "polished_malachite": ("gem_round", (60, 170, 110), "Gem cutter: for rings and inlays."),
    "cut_cryolite": ("gem", (190, 180, 240), "Gem cutter: cold-resistant inlays and rings."),
    "polished_pearl": ("gem_round", (250, 246, 240), "Gem cutter: for rings, amulets and inlays."),
    "glimmer_lens": ("lens", (250, 236, 150), "Gem cutter: lamps, lanterns and luminous coatings."),
    "fur_lining": ("cloth", MAT["fur"]["colors"][1], "Leatherworker's bench: shed fur, for warm clothing."),
    "scale_plate": ("plate", MAT["scale"]["colors"][1], "Leatherworker's bench: shed scales, for swimming armour."),
    "shell_plate": ("plate", MAT["shell"]["colors"][1], "Leatherworker's bench: shell fragments, for sealed armour."),
    "silk_cloth": ("cloth", MAT["silk"]["colors"][1], "Leatherworker's bench: silk strands, for light clothing, gliders and packs."),
    "horn_grip": ("grip", (200, 180, 150), "Leatherworker's bench: horn handles for fine weapons and bows."),
    "leather_strap": ("strap", (140, 90, 56), "Leatherworker's bench: straps for accessories, packs and gear."),
    "rope_coil": ("rope", (190, 160, 110), "Climbing rope. Place on a ledge to hang a rope ladder down (up to 24 blocks)."),
}
STATIONS8 = {"crush": "Ore Crusher", "alloy": "Alloy Furnace", "forge": "Forge", "gems": "Gem Cutter", "leather": "Leatherworker's Bench"}
R = []


def rec(rid, station, ins, out, count=1, secs=6, cont=None, basic=True):
    R.append({"id": rid, "station": station, "ingredients": [[u(k), n] for k, n in ins], "result": u(out), "count": count, "seconds": secs,
              "container": u(cont) if cont else "", "basic": basic, "civ": ""})


def processing():
    # crusher: 2 raw ore -> 3 crushed ore (half again as much metal), plus a few utility grinds
    for raw, crushed in (("minecraft:raw_iron", "crushed_iron"), ("minecraft:raw_copper", "crushed_copper"), ("minecraft:raw_gold", "crushed_gold"),
                         ("raw_starsilver", "crushed_starsilver"), ("raw_umbralite", "crushed_umbralite")):
        rec(f"crush_{crushed}", "crush", [(raw, 2)], crushed, 3, 6)
    rec("crush_gravel", "crush", [("minecraft:cobblestone", 1)], "minecraft:gravel", 1, 2)
    rec("crush_sand", "crush", [("minecraft:gravel", 1)], "minecraft:sand", 1, 2)
    rec("crush_bone_meal", "crush", [("minecraft:bone", 1)], "minecraft:bone_meal", 5, 2)
    rec("crush_fossil", "crush", [("fossil_fragment", 1)], "minecraft:bone_meal", 3, 2)
    rec("crush_flint", "crush", [("minecraft:gravel", 2)], "minecraft:flint", 1, 3)
    # alloy furnace (fuel)
    rec("alloy_steel", "alloy", [("minecraft:iron_ingot", 1), ("minecraft:coal", 2)], "steel_ingot", 1, 10)
    rec("alloy_steel_charcoal", "alloy", [("minecraft:iron_ingot", 1), ("minecraft:charcoal", 2)], "steel_ingot", 1, 10)
    rec("alloy_embersteel", "alloy", [("steel_ingot", 2), ("emberite", 2)], "embersteel_ingot", 2, 14)
    rec("alloy_rimesteel", "alloy", [("steel_ingot", 2), ("cryolite", 2)], "rimesteel_ingot", 2, 14)
    rec("alloy_galvanic", "alloy", [("minecraft:copper_ingot", 3), ("galvanite", 1)], "galvanic_ingot", 3, 12)
    rec("alloy_netherite_scrap", "alloy", [("raw_umbralite", 2), ("minecraft:gold_ingot", 1)], "minecraft:netherite_scrap", 1, 20)
    rec("alloy_glass", "alloy", [("minecraft:sand", 2)], "minecraft:glass", 2, 4)
    # gem cutter
    for raw, cut, n in (("prismatic_opal", "cut_opal", 1), ("jasper", "polished_jasper", 1), ("malachite", "polished_malachite", 1),
                        ("cryolite", "cut_cryolite", 1), ("pearl", "polished_pearl", 1), ("glimmer_shard", "glimmer_lens", 1)):
        rec(f"cut_{cut}", "gems", [(raw, 2)], cut, n, 6)
    # leatherworker's bench
    rec("bench_leather", "leather", [("hide", 1)], "minecraft:leather", 1, 4)
    rec("bench_fur_lining", "leather", [("fur_tuft", 3)], "fur_lining", 1, 4)
    rec("bench_scale_plate", "leather", [("scale", 3)], "scale_plate", 1, 4)
    rec("bench_shell_plate", "leather", [("shell_fragment", 3), ("minecraft:string", 1)], "shell_plate", 1, 4)
    rec("bench_silk_cloth", "leather", [("silk_strand", 4)], "silk_cloth", 1, 4)
    rec("bench_silk_cloth_string", "leather", [("minecraft:string", 6)], "silk_cloth", 1, 4)
    rec("bench_horn_grip", "leather", [("horn", 2)], "horn_grip", 1, 4)
    rec("bench_strap", "leather", [("minecraft:leather", 2), ("minecraft:string", 1)], "leather_strap", 3, 4)
    rec("bench_rope", "leather", [("#fiber", 3)], "rope_coil", 2, 3)
    rec("bench_feather", "leather", [("plume", 1)], "minecraft:feather", 3, 2)
    rec("bench_book", "leather", [("minecraft:paper", 3), ("minecraft:leather", 1)], "minecraft:book", 1, 3)


# ============================================================================== utility and exploration gear
UTILITY = {  # id: (kind, role)
    "grappling_hook": ("grapple", "Right-click a block up to 16 blocks away to pull yourself to it. No fall damage on the swing. Cooldown."),
    "cave_glider": ("glider", "Wear in the chest slot and jump from a height to glide, like an elytra, but it cannot be boosted."),
    "depth_gauge": ("gauge", "Shows your depth, the region you are in and the nearest settlement you know."),
    "delvers_compass": ("compass", "Points to one of your waymarkers, your bed or a settlement you know. Right-click to change target."),
    "backpack": ("pack", "Carry 18 extra stacks. Right-click to open. Cannot hold other packs or shulker boxes."),
    "herb_pouch": ("pouch", "9 slots for herbs, seeds and plants."),
    "ore_satchel": ("satchel", "9 slots for ores, raw metals, gems and stone."),
    "repair_kit": ("kit", "Restores a quarter of an item's durability at the artisan's bench, or use on held gear. Keeps upgrades and enchantments."),
}
BLOCKS8 = {  # id: (name, kind, tooltip)
    "ore_crusher": ("Ore Crusher", "crusher", "Crushes raw ore into half again as much metal, stone into gravel and sand, bones into meal. Needs no fuel."),
    "alloy_furnace": ("Alloy Furnace", "alloy", "Alloys metals (steel, embersteel, rimesteel, galvanic bronze). Burns fuel."),
    "forge": ("Forge", "forge", "Forges advanced weapons, armour, tools and upgrade parts. Burns fuel."),
    "gem_cutter": ("Gem Cutter", "gems", "Cuts and polishes gems; makes rings, amulets and gem inlays."),
    "leatherworker_bench": ("Leatherworker's Bench", "leather", "Works hides, fur, scales, shells, silk and horn; makes charms, packs and linings."),
    "artisan_bench": ("Artisan's Bench", "artisan", "Apply upgrades to equipment, repair it, or salvage what you no longer need."),
    "waymarker": ("Waymarker", "marker", "Mark a place: it is added to your journal's places and your delver's compass can point to it. Gives light."),
    "glimmer_lantern": ("Glimmer Lantern", "lantern", "A bright, cool lantern of glimmer crystal."),
    "rope_ladder": ("Rope Ladder", "ladder", "Climbable rope. Placed by using a rope coil on the top of a ledge."),
}


def equipment_recipes():
    S = "minecraft:stick"
    # stations
    shaped("ore_crusher", "ore_crusher", 1, ["III", "CPC", "CCC"], {"I": "minecraft:iron_ingot", "P": "minecraft:piston", "C": "minecraft:cobblestone"})
    shaped("alloy_furnace", "alloy_furnace", 1, ["III", "IFI", "BBB"], {"I": "minecraft:iron_ingot", "F": "minecraft:furnace", "B": "minecraft:bricks"})
    shaped("forge", "forge", 1, ["SSS", "SFS", "BBB"], {"S": "steel_ingot", "F": "minecraft:blast_furnace", "B": "minecraft:bricks"})
    shaped("gem_cutter", "gem_cutter", 1, ["CGC", "PPP"], {"C": "minecraft:copper_ingot", "G": "minecraft:grindstone", "P": "#minecraft:planks"})
    shaped("leatherworker_bench", "leatherworker_bench", 1, ["LSL", "PPP"], {"L": "minecraft:leather", "S": "minecraft:shears",
                                                                             "P": "#minecraft:planks"})
    shaped("artisan_bench", "artisan_bench", 1, ["SAS", "PCP"], {"S": "steel_ingot", "A": "minecraft:smithing_table", "P": "#minecraft:planks",
                                                                 "C": "minecraft:crafting_table"})
    shaped("waymarker", "waymarker", 2, ["G", "C", "S"], {"G": "glimmer_shard", "C": "silk_cloth", "S": S})
    shaped("waymarker_cloth", "waymarker", 1, ["G", "W", "S"], {"G": "glimmer_shard", "W": "#minecraft:wool", "S": S})
    shaped("glimmer_lantern", "glimmer_lantern", 1, ["NNN", "NGN", "NNN"], {"N": "minecraft:iron_nugget", "G": "glimmer_lens"})
    # crushed ore smelting
    for c, out in (("crushed_iron", "minecraft:iron_ingot"), ("crushed_copper", "minecraft:copper_ingot"), ("crushed_gold", "minecraft:gold_ingot"),
                   ("crushed_starsilver", "starsilver_ingot"), ("crushed_umbralite", "umbralite_ingot")):
        smelt(c, c, out, 0.4, 200)
    # crafting-table weapons/tools/armor for basic materials; forge recipes for advanced ones
    pat = {"sword": [" M ", " M ", " S "], "dagger": ["M", "S"], "greatsword": [" M ", "MMM", " S "], "battleaxe": ["MMM", "MS ", " S "],
           "spear": ["  M", " S ", "S  "], "warhammer": ["MMM", "MMM", " S "], "mace": [" MM", " SM", "S  "],
           "pickaxe": ["MMM", " S ", " S "], "axe": ["MM", "MS", " S"], "shovel": ["M", "S", "S"], "hoe": ["MM", " S", " S"],
           "helmet": ["MMM", "M M"], "chestplate": ["M M", "MMM", "MMM"], "leggings": ["MMM", "M M", "M M"], "boots": ["M M", "M M"]}
    count = {"sword": 2, "dagger": 1, "greatsword": 5, "battleaxe": 4, "spear": 1, "warhammer": 6, "mace": 3, "pickaxe": 3, "axe": 3, "shovel": 1,
             "hoe": 2, "helmet": 5, "chestplate": 8, "leggings": 7, "boots": 4, "hammer": 6, "excavator": 3, "lumber_axe": 5, "sickle": 3,
             "prospector": 3}
    sticks = {"sword": 1, "dagger": 1, "greatsword": 1, "battleaxe": 2, "spear": 2, "warhammer": 1, "mace": 2, "pickaxe": 2, "axe": 2, "shovel": 2,
              "hoe": 2, "hammer": 2, "excavator": 2, "lumber_axe": 2, "sickle": 1, "prospector": 2}
    for w in WEAPONS:
        m, f = w["material"], w["family"]
        if m in FORGED:
            handle = "horn_grip" if m in ("starsilver", "umbralite", "glimmer") else S
            rec(f"forge_{w['id']}", "forge", [(MAT[m]["ingot"], count[f]), (handle, 1 if handle != S else sticks[f])], w["id"], 1, 12)
        else:
            shaped(w["id"], w["id"], 1, pat[f], {"M": MAT[m]["ingot"], "S": S}, "equipment")
    for t in TOOLS:
        m, k = t["material"], t["kind"]
        if m in FORGED or k in ("hammer", "excavator", "lumber_axe", "sickle", "prospector"):
            ins = [(MAT[m]["ingot"], count[k]), (S, sticks[k])]
            if k == "prospector":
                ins.append(("glimmer_lens", 1))
            rec(f"forge_{t['id']}", "forge", ins, t["id"], 1, 12)
        else:
            shaped(t["id"], t["id"], 1, pat[k], {"M": MAT[m]["ingot"], "S": S}, "equipment")
    for s in ARMOR_SETS:
        for p in PIECES:
            pid = f"{s}_{p}"
            if s in FORGED:
                rec(f"forge_{pid}", "forge", [(MAT[s]["ingot"], count[p])] + ([("leather_strap", 1)] if p in ("chestplate", "leggings") else []),
                    pid, 1, 14)
            else:
                shaped(pid, pid, 1, pat[p], {"M": MAT[s]["ingot"]}, "equipment")
    # bows and arrows
    shaped("lumenwood_shortbow", "lumenwood_shortbow", 1, [" PS", "P S", " PS"], {"P": "lumenwood_planks", "S": "minecraft:string"})
    shaped("spirebark_longbow", "spirebark_longbow", 1, [" LS", "L S", " LS"], {"L": "spirebark_log", "S": "silk_cloth"})
    shaped("horn_recurve_bow", "horn_recurve_bow", 1, [" HS", "G S", " HS"], {"H": "horn", "G": "horn_grip", "S": "minecraft:string"})
    shapeless("heavy_arrow", "heavy_arrow", 4, ["minecraft:iron_nugget", "minecraft:arrow", "minecraft:arrow", "minecraft:arrow", "minecraft:arrow"])
    shapeless("frost_arrow", "frost_arrow", 4, ["cryolite", "minecraft:arrow", "minecraft:arrow", "minecraft:arrow", "minecraft:arrow"])
    shapeless("venom_arrow", "venom_arrow", 4, ["venom_gland", "minecraft:arrow", "minecraft:arrow", "minecraft:arrow", "minecraft:arrow"])
    shapeless("ember_arrow", "ember_arrow", 4, ["emberite", "minecraft:arrow", "minecraft:arrow", "minecraft:arrow", "minecraft:arrow"])
    # accessories (gem cutter for jewellery, leatherworker's bench for charms)
    band = {"ember_ring": [("emberite", 2), ("minecraft:gold_ingot", 1)], "rime_ring": [("cut_cryolite", 1), ("minecraft:gold_ingot", 1)],
            "galvanic_ring": [("galvanic_ingot", 2)], "pearl_ring": [("polished_pearl", 1), ("minecraft:gold_ingot", 1)],
            "opal_ring": [("cut_opal", 1), ("minecraft:gold_ingot", 1)], "jasper_ring": [("polished_jasper", 1), ("steel_ingot", 1)],
            "malachite_ring": [("polished_malachite", 1), ("minecraft:copper_ingot", 1)], "starsilver_ring": [("starsilver_ingot", 2)],
            "umbral_ring": [("umbralite_ingot", 1), ("bloodcap", 1)], "copper_ring": [("minecraft:copper_ingot", 2), ("galvanite", 1)],
            "horn_ring": [("horn", 2), ("minecraft:copper_ingot", 1)],
            "spore_filter_amulet": [("shell_plate", 1), ("silk_cloth", 1), ("calcicap", 1)],
            "heartstone_amulet": [("cut_opal", 1), ("bloodcap", 2), ("minecraft:gold_ingot", 1)],
            "nacre_amulet": [("polished_pearl", 2), ("leather_strap", 1)], "fossil_amulet": [("fossil_fragment", 3), ("leather_strap", 1)],
            "amber_amulet": [("amber_glob", 3), ("leather_strap", 1)], "warden_amulet": [("polished_jasper", 1), ("umbralite_ingot", 1),
                                                                                       ("leather_strap", 1)]}
    for a, ins in band.items():
        rec(f"jewel_{a}", "gems", ins, a, 1, 10)
    charms = {"climbing_claws": [("horn", 2), ("leather_strap", 2), ("steel_ingot", 1)], "feather_charm": [("plume", 3), ("leather_strap", 1)],
              "stride_charm": [("silk_cloth", 1), ("horn_grip", 1)], "miners_lamp": [("glimmer_lens", 1), ("leather_strap", 1), ("minecraft:copper_ingot", 1)],
              "runners_charm": [("fur_lining", 1), ("plume", 1), ("leather_strap", 1)], "healers_charm": [("mendleaf_sprig", 3), ("silk_cloth", 1)],
              "traders_token": [("minecraft:emerald", 3), ("minecraft:gold_ingot", 1)], "foragers_charm": [("briar_fruit", 2), ("leather_strap", 1)],
              "wayfinders_charm": [("minecraft:compass", 1), ("leather_strap", 1)], "anglers_charm": [("fishing_bait", 4), ("polished_pearl", 1)]}
    for a, ins in charms.items():
        rec(f"charm_{a}", "leather", ins, a, 1, 8)
    # upgrade kits: edges/plates/heads at the forge, linings/grips at the leatherworker's bench, inlays at the gem cutter,
    # coatings at the herbalist's table (Stage 7)
    kit_recipes = {
        "honed_edge": ("forge", [("steel_ingot", 1), ("minecraft:flint", 2)]), "balanced_grip": ("leather", [("horn_grip", 1), ("leather_strap", 1)]),
        "weighted_pommel": ("forge", [("steel_ingot", 2)]), "long_haft": ("leather", [("minecraft:stick", 4), ("leather_strap", 1)]),
        "venom_coating": ("herbal", [("venom_gland", 1), ("creature_oil", 1)]), "ember_coating": ("herbal", [("emberite", 1), ("creature_oil", 1)]),
        "frost_coating": ("herbal", [("cryolite", 1), ("creature_oil", 1)]), "glimmer_coating": ("herbal", [("glimmer_shard", 1), ("creature_oil", 1)]),
        "reinforced_head": ("forge", [("steel_ingot", 2)]), "honed_bit": ("forge", [("steel_ingot", 1), ("minecraft:diamond", 1)]),
        "aquatic_grip": ("leather", [("scale_plate", 1), ("leather_strap", 1)]), "malachite_bracing": ("forge", [("malachite", 3)]),
        "insulated_lining": ("leather", [("fur_lining", 2)]), "cooling_lining": ("leather", [("silk_cloth", 1), ("cryolite", 1)]),
        "spore_filter": ("leather", [("shell_plate", 1), ("calcicap", 1)]), "grounding_mesh": ("forge", [("galvanic_ingot", 2)]),
        "sealed_seams": ("leather", [("scale_plate", 1), ("amber_glob", 1)]), "reinforced_plating": ("forge", [("steel_ingot", 3)]),
        "toughened_plating": ("forge", [("umbralite_ingot", 1), ("steel_ingot", 1)]), "lightened_frame": ("forge", [("starsilver_ingot", 2)]),
        "gripped_soles": ("leather", [("leather_strap", 2), ("minecraft:slime_ball", 1)]), "lamp_mount": ("gems", [("glimmer_lens", 1), ("steel_ingot", 1)]),
        "opal_inlay": ("gems", [("cut_opal", 1)]), "jasper_inlay": ("gems", [("polished_jasper", 1)]), "malachite_inlay": ("gems", [("polished_malachite", 1)]),
        "cryolite_inlay": ("gems", [("cut_cryolite", 1)]), "emberite_inlay": ("gems", [("emberite", 2)]), "galvanite_inlay": ("gems", [("galvanite", 2)]),
        "pearl_inlay": ("gems", [("polished_pearl", 1)]), "starsilver_inlay": ("gems", [("starsilver_ingot", 1)]),
        "glimmer_inlay": ("gems", [("glimmer_lens", 1)]), "amber_inlay": ("gems", [("amber_glob", 2)]),
        "bow_string_silk": ("leather", [("silk_cloth", 1), ("minecraft:string", 2)]), "bow_limbs_horn": ("leather", [("horn", 2), ("horn_grip", 1)]),
    }
    for up in UPGRADES:
        st, ins = kit_recipes[up["id"]]
        rec(f"kit_{up['id']}", st, ins, up["kit"], 1, 8)
    # utility
    rec("forge_grappling_hook", "forge", [("steel_ingot", 3), ("rope_coil", 2)], "grappling_hook", 1, 10)
    rec("bench_cave_glider", "leather", [("silk_cloth", 4), ("steel_ingot", 2), ("horn_grip", 1)], "cave_glider", 1, 12)
    rec("bench_backpack", "leather", [("minecraft:leather", 4), ("silk_cloth", 2), ("leather_strap", 2)], "backpack", 1, 10)
    rec("bench_herb_pouch", "leather", [("minecraft:leather", 2), ("leather_strap", 1)], "herb_pouch", 1, 6)
    rec("bench_ore_satchel", "leather", [("minecraft:leather", 2), ("leather_strap", 1), ("minecraft:iron_nugget", 2)], "ore_satchel", 1, 6)
    shaped("depth_gauge", "depth_gauge", 1, [" C ", "CRC", " G "], {"C": "minecraft:copper_ingot", "R": "minecraft:redstone", "G": "minecraft:glass_pane"})
    shaped("delvers_compass", "delvers_compass", 1, [" G ", "GCG", " S "], {"G": "galvanite", "C": "minecraft:compass", "S": "starsilver_ingot"})
    shaped("delvers_compass_glimmer", "delvers_compass", 1, [" G ", "GCG", " G "], {"G": "glimmer_shard", "C": "minecraft:compass"})
    rec("forge_repair_kit", "forge", [("steel_ingot", 1), ("leather_strap", 1), ("amber_glob", 1)], "repair_kit", 2, 6)
    rec("bench_repair_kit", "leather", [("minecraft:iron_ingot", 1), ("leather_strap", 1), ("minecraft:string", 2)], "repair_kit", 1, 6)


# ============================================================================== output
def main(extra=None):
    alternatives()
    processing()
    equipment_recipes()
    A.SEED[0] = 8000
    # ---- items, models, names
    for cid, (kind, color, role) in COMPONENTS.items():
        A.save_item(cid, A.component(kind, color))
        A.item_model(cid)
        LANG[f"item.{NS}.{cid}"] = name_of(cid)
        LANG[f"tooltip.{NS}.{cid}"] = role
    for w in WEAPONS:
        c = MAT[w["material"]]["colors"]
        A.save_item(w["id"], A.weapon(w["family"], c, w["material"]))
        A.item_model(w["id"], handheld=True)
        mname = MAT[w["material"]]["name"]
        base = w["id"].split("_", 1)[1] if w["id"] in SPECIAL_NAMES.values() else w["family"]
        LANG[f"item.{NS}.{w['id']}"] = f"{mname} {name_of(base)}"
        LANG[f"tooltip.{NS}.{w['id']}"] = FAMILY_ROLE[w["family"]] + (" " + special_text(w["special"]) if w["special"] else "")
        tag("minecraft:tags/item/swords" if w["family"] in ("sword", "dagger", "greatsword") else "underdeep:tags/item/weapons", w["id"])
    for t in TOOLS:
        c = MAT[t["material"]]["colors"]
        A.save_item(t["id"], A.tool(t["kind"], c))
        A.item_model(t["id"], handheld=True)
        LANG[f"item.{NS}.{t['id']}"] = {"prospector": "Prospector's Pick"}.get(t["kind"], f"{MAT[t['material']]['name']} {name_of(t['kind'])}")
        role = TOOL_ROLE.get(t["kind"], "") or TOOL_ROLE.get(t["special"], "")
        if t["material"] in ("malachite",):
            role = (role + " Very durable.").strip()
        if t["material"] in ("starsilver", "galvanic") and t["kind"] in ("pickaxe", "axe"):
            role = (role + " Very fast.").strip()
        if role:
            LANG[f"tooltip.{NS}.{t['id']}"] = role
    for s in ARMOR_SETS:
        c = MAT[s]["colors"]
        A.armor_layers(s, c, kind=s)
        for p in PIECES:
            pid = f"{s}_{p}"
            A.save_item(pid, A.armor_icon(p, c, s))
            A.item_model(pid)
            LANG[f"item.{NS}.{pid}"] = PIECE_NAMES.get(s, {}).get(p, f"{MAT[s]['name']} {name_of(p)}")
            LANG[f"tooltip.{NS}.{pid}"] = MAT[s]["role"]
    for b in BOWS:
        A.bow_textures(b["id"], {"lumenwood_shortbow": (150, 210, 200), "spirebark_longbow": (120, 90, 70), "horn_recurve_bow": (214, 196, 160)}[b["id"]])
        A.bow_models(b["id"])
        LANG[f"item.{NS}.{b['id']}"] = name_of(b["id"])
        LANG[f"tooltip.{NS}.{b['id']}"] = {"lumenwood_shortbow": "Draws quickly; weaker shots.", "spirebark_longbow": "Slow to draw; powerful shots.",
                                           "horn_recurve_bow": "Balanced and very durable."}[b["id"]]
        tag("minecraft:tags/item/enchantable/bow", b["id"])
        tag("minecraft:tags/item/enchantable/durability", b["id"])
    for a in ARROWS:
        A.save_item(a["id"], A.arrow({"heavy_arrow": (150, 150, 160), "frost_arrow": (160, 210, 250), "venom_arrow": (120, 200, 80),
                                      "ember_arrow": (240, 120, 40)}[a["id"]]))
        A.item_model(a["id"])
        LANG[f"item.{NS}.{a['id']}"] = name_of(a["id"])
        LANG[f"tooltip.{NS}.{a['id']}"] = {"heavy_arrow": "Hits harder.", "frost_arrow": "Slows what it hits.", "venom_arrow": "Poisons what it hits.",
                                           "ember_arrow": "Sets what it hits alight."}[a["id"]]
        tag("minecraft:tags/item/arrows", a["id"])
    for a in ACCESSORIES:
        A.save_item(a["id"], A.accessory(a["slot"], a["id"]))
        A.item_model(a["id"])
        LANG[f"item.{NS}.{a['id']}"] = {"traders_token": "Trader's Token", "miners_lamp": "Miner's Lamp", "runners_charm": "Runner's Charm",
                                        "healers_charm": "Healer's Charm", "foragers_charm": "Forager's Charm", "wayfinders_charm": "Wayfinder's Charm",
                                        "anglers_charm": "Angler's Charm"}.get(a["id"], name_of(a["id"]))
        LANG[f"tooltip.{NS}.{a['id']}"] = f"{a['slot'].capitalize()} accessory. {a['desc']}"
    for up in UPGRADES:
        A.save_item(up["kit"], A.kit(up["group"], up["id"]))
        A.item_model(up["kit"])
        LANG[f"item.{NS}.{up['kit']}"] = name_of(up["id"]) + " Kit"
        LANG[f"tooltip.{NS}.{up['kit']}"] = f"Upgrade for {', '.join(up['applies'])} (artisan's bench). {up['desc']}"
        LANG[f"upgrade.{NS}.{up['id']}"] = name_of(up["id"])
        LANG[f"upgrade.{NS}.{up['id']}.desc"] = up["desc"]
    for uid, (kind, role) in UTILITY.items():
        A.save_item(uid, A.utility(kind))
        A.item_model(uid)
        LANG[f"item.{NS}.{uid}"] = {"delvers_compass": "Delver's Compass"}.get(uid, name_of(uid))
        LANG[f"tooltip.{NS}.{uid}"] = role
    for bid, (nm, kind, tip) in BLOCKS8.items():
        A.block_assets(bid, kind)
        LANG[f"block.{NS}.{bid}"] = nm
        LANG[f"tooltip.{NS}.{bid}"] = tip
        wjson(os.path.join(DATA, NS, "loot_table", "blocks", bid + ".json"), {"type": "minecraft:block", "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": u(bid if bid != "rope_ladder" else "rope_coil")}],
             "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for st, nm in STATIONS8.items():
        LANG[f"station.{NS}.{st}"] = nm
    LANG["key.underdeep.handbook"] = "Open Underdeep Handbook"
    LANG["key.underdeep.accessories"] = "Open Accessories"
    LANG["key.categories.underdeep"] = "Underdeep"
    LANG["container.underdeep.accessories"] = "Accessories"
    LANG["container.underdeep.artisan"] = "Artisan's Bench"
    # ---- tags (mining, enchanting, repair)
    for w in WEAPONS:
        tag("minecraft:tags/item/enchantable/weapon", w["id"])
        tag("minecraft:tags/item/enchantable/durability", w["id"])
        tag("minecraft:tags/item/enchantable/sharp_weapon", w["id"])
        tag("minecraft:tags/item/enchantable/fire_aspect", w["id"])
        if w["family"] in ("sword", "dagger", "greatsword"):
            tag("minecraft:tags/item/enchantable/sword", w["id"])
        if w["family"] == "battleaxe":
            tag("minecraft:tags/item/axes", w["id"])
            tag("minecraft:tags/item/enchantable/mining", w["id"])
    for t in TOOLS:
        k = t["kind"]
        tagname = {"pickaxe": "pickaxes", "axe": "axes", "shovel": "shovels", "hoe": "hoes", "hammer": "pickaxes", "excavator": "shovels",
                   "lumber_axe": "axes", "sickle": "hoes", "prospector": "pickaxes"}[k]
        tag(f"minecraft:tags/item/{tagname}", t["id"])
        tag("minecraft:tags/item/enchantable/mining", t["id"])
        tag("minecraft:tags/item/enchantable/mining_loot", t["id"])
        tag("minecraft:tags/item/enchantable/durability", t["id"])
        if k in ("axe", "lumber_axe"):
            tag("minecraft:tags/item/enchantable/weapon", t["id"])
    for s in ARMOR_SETS:
        for p in PIECES:
            pid = f"{s}_{p}"
            tag("minecraft:tags/item/enchantable/armor", pid)
            tag("minecraft:tags/item/enchantable/durability", pid)
            tag(f"minecraft:tags/item/enchantable/{ {'helmet': 'head', 'chestplate': 'chest', 'leggings': 'leg', 'boots': 'foot'}[p]}_armor", pid)
            tag("minecraft:tags/item/trimmable_armor", pid)
    tag("minecraft:tags/item/enchantable/equippable", *[f"{s}_{p}" for s in ARMOR_SETS for p in PIECES])
    tag("minecraft:tags/block/mineable/pickaxe", "ore_crusher", "alloy_furnace", "forge", "glimmer_lantern")
    tag("minecraft:tags/block/mineable/axe", "gem_cutter", "leatherworker_bench", "artisan_bench", "waymarker", "rope_ladder")
    tag("minecraft:tags/block/climbable", "rope_ladder")
    for path, vals in TAGS.items():
        ns, rest = path.split(":", 1)
        full = os.path.join(DATA, ns, rest + ".json")
        cur = json.load(open(full)).get("values", []) if os.path.exists(full) else []
        wjson(full, {"replace": False, "values": cur + [v for v in vals if v not in cur]})
    # ---- the content file
    content = {
        "materials": {k: {"name": v["name"], "colors": [list(c) for c in v["colors"]], "special": v["special"], "role": v["role"],
                          "ingot": u(v["ingot"]),
                          "tool": None if not v["tool"] else dict(zip(["level", "durability", "speed", "damage", "enchant", "repair"],
                                                                      [v["tool"][0], v["tool"][1], v["tool"][2], v["tool"][3], v["tool"][4],
                                                                       u(v["tool"][5])])),
                          "armor": None if not v["armor"] else {"protection": list(v["armor"][0]), "toughness": v["armor"][1],
                                                                "knockback": v["armor"][2], "durability": v["armor"][3], "enchant": v["armor"][4],
                                                                "repair": u(v["armor"][5]), "protect": v["armor"][6],
                                                                "attrs": [list(a) for a in v["armor"][7]],
                                                                "pieceAttrs": {p: [list(a) for a in l] for p, l in v["armor"][8].items()}}}
                      for k, v in MAT.items()},
        "weapons": WEAPONS, "tools": TOOLS, "bows": BOWS, "arrows": ARROWS, "armorSets": ARMOR_SETS, "accessories": ACCESSORIES,
        "upgrades": UPGRADES, "components": list(COMPONENTS), "utility": {k: v[0] for k, v in UTILITY.items()}, "blocks": {k: v[1] for k, v in BLOCKS8.items()},
        "stations": STATIONS8, "recipes": R,
    }
    wjson(os.path.join(DATA, NS, "equipment", "content.json"), content)
    print(f"stage 8: {len(MAT)} materials, {len(WEAPONS)} weapons + {len(BOWS)} bows, {len(ARMOR_SETS)} armor sets, {len(TOOLS)} tools, "
          f"{len(ACCESSORIES)} accessories, {len(UPGRADES)} upgrades, {len(UTILITY)} utility items, {len(BLOCKS8)} blocks, {len(R)} station recipes")
    return LANG


def special_text(s):
    return {"heat": "Sets targets alight.", "frost": "Slows targets.", "static": "Shocks wet targets (+3 damage in water or rain).",
            "radiant": "+3 damage against the undead.", "heavy": "Pierces armour (bonus damage against armoured targets).",
            "aquatic": "+3 damage when you or the target are in water.", "glimmer": "Makes targets glow."}.get(s, "")


if __name__ == "__main__":
    lang = main()
    lang_path = os.path.join(ASSETS, "lang", "en_us.json")
    cur = json.load(open(lang_path))
    cur.update(lang)
    wjson(lang_path, dict(sorted(cur.items())))
