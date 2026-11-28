"""Stage 10 content: guardian lairs (site families), heirloom equipment, boss upgrade kits, Sign-stones, guardian
materials, lair blocks, loot tables, damage types, boss models (articulated rigs + textures) and names.

Run through stage2_assets.py (after stage9, which rewrites the shared content files first). Deterministic: every
random choice is seeded. Identities and mechanics behind these names are private (docs/private/BOSSES.md).
"""
import json
import math
import os
import random

from PIL import Image

import art
import stage8_art as S8
import stage9_art as A9

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "underdeep")
DATA = os.path.join(ROOT, "data")
NS = "underdeep"


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def I(x):
    return x if ":" in x else f"{NS}:{x}"


def name(iid):
    small = {"of", "the"}
    return " ".join(w if w in small and i else w.capitalize() for i, w in enumerate(iid.split("_")))


BOSSES = ["mossback", "sporemother", "choirwarden", "rimefang", "galvanic", "cinderwurm", "lamplighter", "resonant", "tidemother",
          "silentwarden", "rootsovereign", "seamwatcher", "somnarch"]
TITLES = {"mossback": "The Mossback Thunderer", "sporemother": "The Spore Mother", "choirwarden": "The Drowned Choirwarden",
          "rimefang": "Rimefang, Lord of the Pack", "galvanic": "The Galvanic Colossus", "cinderwurm": "The Cinder Wurm",
          "lamplighter": "The Last Lamplighter", "resonant": "The Resonant Colossus", "tidemother": "The Tide-Mother",
          "silentwarden": "The Silent Warden", "rootsovereign": "The Root Sovereign", "seamwatcher": "The Seam Watcher", "somnarch": "The Somnarch"}
MATERIAL = {"mossback": "thunderhide", "sporemother": "mycelial_heart", "choirwarden": "choir_nacre", "rimefang": "rimefang_pelt",
            "galvanic": "galvanic_core", "cinderwurm": "cinder_scale", "lamplighter": "everlit_wick", "resonant": "resonant_crystal",
            "tidemother": "abyssal_pearl", "silentwarden": "silent_bellmetal", "rootsovereign": "heartwood_resin", "seamwatcher": "seamglass",
            "somnarch": "somnal_ember"}
SIGN = {"rimefang": "sign_spiral", "galvanic": "sign_eye", "cinderwurm": "sign_flame", "lamplighter": "sign_wave", "resonant": "sign_star",
        "tidemother": "sign_key", "silentwarden": "keystone_silence"}
TIER = {"mossback": 0, "sporemother": 0, "choirwarden": 0, "rimefang": 1, "galvanic": 1, "cinderwurm": 1, "lamplighter": 1, "resonant": 2,
        "tidemother": 2, "silentwarden": 2, "rootsovereign": 3, "seamwatcher": 3, "somnarch": 4}

# ------------------------------------------------------------------------------------------------ lair families
# (id, name shown on discovery, description, culture, regions, layers, radius, height, below, aquatic)
LAIRS = [
    ("mossback", "Thunder Hollow", "Hoof-scarred roots and a smell of wet earth. Something heavy beds down here.", "gardener",
     ["rootwild", "golden_grove"], ["upper"], 22, 14, 1, False),
    ("sporemother", "The Spore Grove", "Pale caps the size of houses, and the air is thick enough to taste.", "gardener",
     ["fungal_expanse"], ["upper"], 22, 16, 1, False),
    ("choirwarden", "The Drowned Choir", "A temple of nacre, half under water. Someone still keeps watch.", "tide",
     ["flooded_depths", "sunken_gardens", "emerald_wetlands"], ["upper"], 20, 12, 2, False),
    ("rimefang", "The Frozen Howl", "A ring of cold braziers around a frozen lake, and tracks everywhere.", "silent",
     ["everfrost"], ["upper"], 24, 14, 1, False),
    ("galvanic", "The Pylon Yard", "Copper pylons stand around a sleeping shape that hums.", "wright",
     ["stormstone_caverns"], ["upper", "deep"], 22, 16, 1, False),
    ("cinderwurm", "The Ash Pit", "Basalt broken from below; the ground is warm and restless.", "ember",
     ["ashen_hollows", "crimson_basin"], ["upper", "deep"], 22, 12, 5, False),
    ("lamplighter", "The Hall of Lanterns", "Old League lantern posts, still lit, though nobody has tended them for an age.", "lantern",
     ["starlight_caverns", "golden_grove", "hollow_peaks"], ["upper"], 22, 14, 1, False),
    ("resonant", "The Singing Vault", "Crystal grows here in chords. Your footsteps ring.", "star",
     ["crystal_deep"], ["deep"], 26, 18, 1, False),
    ("tidemother", "The Island Ring", "Stone islands over black water. The water is too still.", "tide",
     ["flooded_depths", "sunken_gardens", "emerald_wetlands"], ["upper"], 28, 14, 8, True),
    ("silentwarden", "The Silent Cathedral", "Pillars, bells that make no sound, and a hush that presses on your ears.", "silent",
     ["the_abyss"], ["deep"], 24, 16, 1, False),
    ("rootsovereign", "The Heartwood Chamber", "The oldest root you have ever seen, and it is breathing.", "gardener",
     ["rootwild"], ["upper"], 24, 18, 1, False),
    ("seamwatcher", "The Seam Chamber", "High under the roof of the world the stone hums like a struck bell.", "star",
     [], ["upper"], 24, 24, 1, False),
    ("somnarch", "The Heart Engine", "A sealed hall at the bottom of everything. The walls are warm, and they beat.", "wright",
     ["the_abyss"], ["deep"], 32, 26, 2, False),
]


def lair_families():
    out = []
    for bid, nm, desc, culture, regions, layers, radius, height, below, aquatic in LAIRS:
        out.append({"id": "lair_" + bid, "category": "lair", "scale": "exceptional" if bid == "somnarch" else "large", "name": nm, "desc": desc,
                    "culture": culture, "regions": regions, "layers": layers, "radius": radius, "height": height, "below": below, "weight": 0,
                    "aquatic": aquatic, "minSpawn": 250, "secrets": [], "audio": "lair_final" if bid == "somnarch" else "lair"})
    return out


# ------------------------------------------------------------------------------------------------ heirlooms and kits
HEIR_ACC = [
    ("mossback_horn", "charm", "Your charges land harder: +1 attack knockback, +10% knockback resistance.",
     [("generic.attack_knockback", 1.0, "add_value"), ("generic.knockback_resistance", 0.1, "add_value")], {}, []),
    ("sporemother_censer", "amulet", "Spores and fumes trouble you far less.", [], {"spores": 0.5, "fumes": 0.3}, []),
    ("rimefang_mantle", "amulet", "The cold bites less, and you run like the pack: +5% speed.", [("generic.movement_speed", 0.005, "add_value")],
     {"cold": 0.45}, ["runner"]),
    ("galvanic_gauntlet", "ring", "Static cannot find you; +1 attack damage.", [("generic.attack_damage", 1.0, "add_value")], {"static": 0.5}, []),
    ("lamplighter_lantern", "charm", "Carries a little of the last lamplighter's light.", [], {}, ["lamp"]),
    ("silent_veil", "amulet", "You move quietly: +30% sneaking speed.", [("player.sneaking_speed", 0.3, "add_value")], {}, ["keen"]),
    ("sovereign_crown", "amulet", "+4 maximum health.", [("generic.max_health", 4.0, "add_value")], {}, ["healer"]),
    ("watcher_lens", "ring", "You see what others miss; +1 luck.", [("generic.luck", 1.0, "add_value")], {}, ["echo"]),
    ("somnarch_heart", "charm", "Warm as a hearth: +2 armour, heat troubles you less.", [("generic.armor", 2.0, "add_value")], {"heat": 0.35}, []),
]
HEIR_WEAP = [
    ("choirwarden_blade", "sword", "nacre", 7.5, 1.6, 0.0, 0.0, 1100, "aquatic", "The warden's blade. Light in the hand, deadly under water."),
    ("cinder_fang", "dagger", "embersteel", 6.0, 2.4, 0.0, 0.0, 900, "heat", "A fang of the wurm; its cuts keep burning."),
    ("resonant_hammer", "warhammer", "starsilver", 12.0, 0.75, 0.0, 0.6, 1300, "heavy", "Hums before it lands. Breaks guard like nothing else."),
    ("tide_harpoon", "spear", "nacre", 8.5, 1.3, 1.5, 0.0, 1200, "aquatic", "Thrown light, thrust heavy; at home in the deep."),
]
BOSS_UPGRADES = [
    ("thunderhide_lining", ["chestplate", "leggings"], "lining", "+2 armour, +5% knockback resistance.",
     [("generic.armor", 2.0, "add_value"), ("generic.knockback_resistance", 0.05, "add_value")], {}, [], "leather", [("thunderhide", 2), ("leather_strap", 1)]),
    ("mycelial_coating", ["weapon"], "coating", "Hits poison the target.", [], {}, ["venom"], "herbal", [("mycelial_heart", 1), ("creature_oil", 1)]),
    ("nacre_edge", ["weapon"], "edge", "+1.5 attack damage.", [("generic.attack_damage", 1.5, "add_value")], {}, [], "forge",
     [("choir_nacre", 2), ("steel_ingot", 1)]),
    ("rimefang_lining", ["helmet", "boots"], "lining", "Keeps out the cold.", [], {"cold": 0.25}, [], "leather", [("rimefang_pelt", 2)]),
    ("galvanic_coil", ["weapon", "tool"], "gem", "+10% attack speed.", [("generic.attack_speed", 0.1, "add_multiplied_base")], {}, [], "gems",
     [("galvanic_core", 1)]),
    ("cinder_temper", ["weapon", "tool"], "head", "Hits set the target alight.", [], {}, ["ember"], "forge", [("cinder_scale", 2), ("steel_ingot", 1)]),
    ("everlit_lens", ["helmet"], "gem", "A steady light at your brow.", [], {}, ["lamp"], "gems", [("everlit_wick", 1), ("glimmer_lens", 1)]),
    ("resonant_core", ["chestplate"], "plating", "+2 armour toughness.", [("generic.armor_toughness", 2.0, "add_value")], {}, [], "forge",
     [("resonant_crystal", 2), ("steel_ingot", 1)]),
    ("abyssal_grip", ["weapon"], "grip", "+0.5 attack damage and steadier hands under water.",
     [("generic.attack_damage", 0.5, "add_value"), ("player.submerged_mining_speed", 0.5, "add_value")], {}, [], "leather",
     [("abyssal_pearl", 1), ("leather_strap", 1)]),
    ("silent_padding", ["boots"], "lining", "+20% sneaking speed.", [("player.sneaking_speed", 0.2, "add_value")], {}, [], "leather",
     [("silent_bellmetal", 1), ("silk_cloth", 1)]),
]


def equipment():
    p = os.path.join(DATA, NS, "equipment", "content.json")
    eq = json.load(open(p))
    acc = [{"id": a, "slot": s, "desc": d, "attrs": [list(x) for x in at], "protect": pr, "flags": fl} for a, s, d, at, pr, fl in HEIR_ACC]
    weap = [{"id": w[0], "family": w[1], "material": w[2], "damage": w[3], "speed": w[4], "reach": w[5], "knockback": w[6], "durability": w[7],
             "special": w[8]} for w in HEIR_WEAP]
    ups = [{"id": u[0], "applies": u[1], "group": u[2], "desc": u[3], "attrs": [list(x) for x in u[4]], "durability": 0.0, "protect": u[5],
            "flags": u[6], "kit": u[0] + "_kit"} for u in BOSS_UPGRADES]
    ids = {x["id"] for x in acc} | {x["id"] for x in weap}
    eq["accessories"] = [a for a in eq["accessories"] if a["id"] not in ids] + acc
    eq["weapons"] = [w for w in eq["weapons"] if w["id"] not in ids] + weap
    eq["upgrades"] = [u for u in eq["upgrades"] if u["id"] not in {x["id"] for x in ups}] + ups
    eq["artifacts"] = [a for a in eq.get("artifacts", []) if a not in ids] + [a["id"] for a in acc] + [w["id"] for w in weap]
    recs = [r for r in eq["recipes"] if not r["id"].startswith("s10_")]
    for u in BOSS_UPGRADES:
        recs.append({"id": "s10_kit_" + u[0], "station": u[7], "ingredients": [[I(i), n] for i, n in u[8]], "result": I(u[0] + "_kit"), "count": 1,
                     "seconds": 10, "container": "", "basic": False, "civ": ""})
    eq["recipes"] = recs
    wjson(p, eq)
    return acc, weap, ups


# ------------------------------------------------------------------------------------------------ loot
def entry(item, w=1, lo=1, hi=1):
    e = {"type": "minecraft:item", "name": I(item), "weight": w}
    if hi > 1 or lo != 1:
        e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return e


def table(path, pools):
    wjson(os.path.join(DATA, NS, "loot_table", path + ".json"), {"type": "minecraft:chest", "pools": pools})


def loot():
    valuables = [entry("minecraft:diamond", 3, 1, 3), entry("minecraft:emerald", 4, 3, 8), entry("minecraft:gold_ingot", 4, 2, 6),
                 entry("ancient_coin", 4, 2, 6), entry("star_shard", 1)]
    for b in BOSSES:
        t = TIER[b]
        mat = MATERIAL[b]
        pools = [{"rolls": 1, "entries": [entry(mat, 1, 3 + t, 5 + t * 2)]},
                 {"rolls": 2 + t, "entries": valuables},
                 {"rolls": 1, "entries": [entry("minecraft:experience_bottle", 1, 4, 8 + 4 * t)]}]
        table(f"bosses/{b}", pools)
        table(f"bosses/{b}_echo", [{"rolls": 1, "entries": [entry(mat, 1, 1, 2 + t)]}, {"rolls": 1, "entries": valuables[:3]}])


# ------------------------------------------------------------------------------------------------ damage types
def damage_types():
    for d in ("boss_strike", "boss_sweep", "boss_crush", "boss_spell", "boss_aura"):
        wjson(os.path.join(DATA, NS, "damage_type", d + ".json"),
              {"message_id": "underdeep.boss", "exhaustion": 0.1, "scaling": "when_caused_by_living_non_player"})
    wjson(os.path.join(DATA, "minecraft", "tags", "damage_type", "bypasses_shield.json"),
          {"replace": False, "values": ["underdeep:boss_crush", "underdeep:boss_aura"]})
    wjson(os.path.join(DATA, "minecraft", "tags", "damage_type", "no_knockback.json"),
          {"replace": False, "values": ["underdeep:boss_aura"]})


# ------------------------------------------------------------------------------------------------ item and block art
def item_art(lang):
    rnd = random.Random(1010)
    sign_cols = {"sign_spiral": (120, 200, 255), "sign_eye": (110, 160, 255), "sign_wave": (90, 220, 210), "sign_flame": (255, 140, 60),
                 "sign_star": (240, 220, 140), "sign_key": (220, 200, 120), "keystone_silence": (40, 210, 200)}
    for sid, col in sign_cols.items():
        img = art.new()
        base = (70, 74, 88) if sid != "keystone_silence" else (24, 30, 40)
        art.disc(img, 8, 8, 6, base, art.shade(base, 0.7))
        k = list(sign_cols).index(sid)
        A9.glyph(img, k % 6, col) if k < 6 else None
        if sid == "keystone_silence":
            for a in range(0, 360, 30):
                art.px(img, int(8 + 4 * math.cos(math.radians(a))), int(8 + 4 * math.sin(math.radians(a))), col)
            art.px(img, 8, 8, (255, 255, 255))
        S8.save_item(sid, art.outline(img, (20, 20, 26)))
        S8.item_model(sid)
    lang.update({f"item.{NS}.{s}": n for s, n in (("sign_spiral", "Sign of the Spiral"), ("sign_eye", "Sign of the Eye"), ("sign_wave", "Sign of the Wave"),
                                                   ("sign_flame", "Sign of the Flame"), ("sign_star", "Sign of the Star"), ("sign_key", "Sign of the Key"),
                                                   ("keystone_silence", "Keystone of Silence"))})
    mat_cols = {"thunderhide": (100, 120, 70), "mycelial_heart": (200, 120, 110), "choir_nacre": (230, 236, 240), "rimefang_pelt": (220, 236, 250),
                "galvanic_core": (90, 170, 240), "cinder_scale": (60, 50, 50), "everlit_wick": (255, 210, 110), "resonant_crystal": (210, 160, 250),
                "abyssal_pearl": (60, 120, 150), "silent_bellmetal": (70, 90, 110), "heartwood_resin": (230, 150, 50), "seamglass": (200, 220, 255),
                "somnal_ember": (255, 110, 60)}
    descs = {"thunderhide": "Thick, mossy hide. Leatherworkers can line armour with it.", "mycelial_heart": "Still pulses faintly. An herbalist could make a coating from it.",
             "choir_nacre": "Iridescent plates. A smith can edge a blade with them.", "rimefang_pelt": "The warmest fur there is.",
             "galvanic_core": "A crackling heart of copper and glass. A gem cutter can set it.", "cinder_scale": "Hot to the touch, forever.",
             "everlit_wick": "A wick that never burns down.", "resonant_crystal": "It rings when you breathe on it.",
             "abyssal_pearl": "Heavy and cold, from very deep water.", "silent_bellmetal": "Metal that makes no sound when struck.",
             "heartwood_resin": "Amber from the oldest root.", "seamglass": "Glass from the roof of the world, full of tiny stars.",
             "somnal_ember": "An ember of a dream. It is warm, and it never goes out."}
    for m, c in mat_cols.items():
        img = art.new()
        for x in range(3, 13):
            for y in range(3, 13):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 5 + rnd.random() * 0.8:
                    art.px(img, x, y, art.shade(c, 0.75 + 0.5 * rnd.random() - d * 0.03))
        art.px(img, 6, 5, art.shade(c, 1.5))
        S8.save_item(m, art.outline(img, art.shade(c, 0.35)))
        S8.item_model(m)
        lang[f"item.{NS}.{m}"] = name(m)
        lang[f"tooltip.{NS}.{m}"] = descs[m]
    for s in sign_cols:
        lang[f"tooltip.{NS}.{s}"] = "One of the Signs of an old door." if s != "keystone_silence" else "It hums. Hold it up and listen."


def heirloom_art(lang, acc, weap, ups):
    gems = {"mossback_horn": (210, 200, 160), "sporemother_censer": (200, 110, 90), "rimefang_mantle": (200, 230, 255),
            "galvanic_gauntlet": (90, 170, 240), "lamplighter_lantern": (255, 210, 110), "silent_veil": (40, 210, 200),
            "sovereign_crown": (230, 160, 60), "watcher_lens": (230, 230, 255), "somnarch_heart": (255, 100, 60)}
    for a in acc:
        g = gems[a["id"]]
        img = art.new()
        if a["slot"] == "ring":
            for ang in range(0, 360, 10):
                art.px(img, int(round(8 + 4 * math.cos(math.radians(ang)))), int(round(9 + 3.5 * math.sin(math.radians(ang)))), art.shade(g, 0.8))
            art.disc(img, 8, 5, 2, g, art.shade(g, 0.6))
        elif a["slot"] == "amulet":
            art.line(img, 3, 2, 8, 8, (200, 190, 150))
            art.line(img, 13, 2, 8, 8, (170, 160, 120))
            art.disc(img, 8, 11, 3.2, g, art.shade(g, 0.6))
        else:
            art.disc(img, 8, 8, 5.4, art.shade(g, 0.7), art.shade(g, 0.45))
            art.disc(img, 8, 8, 2.2, art.shade(g, 1.3), None)
        art.px(img, 7, 4 if a["slot"] == "ring" else 10, (255, 255, 255))
        S8.save_item(a["id"], art.outline(img, (30, 26, 24)))
        S8.item_model(a["id"])
        lang[f"item.{NS}.{a['id']}"] = name(a["id"])
        lang[f"tooltip.{NS}.{a['id']}"] = a["desc"]
    cols = {"nacre": ((250, 244, 236), (210, 200, 196), (150, 140, 140)), "embersteel": ((255, 160, 90), (210, 100, 50), (130, 50, 30)),
            "starsilver": ((240, 244, 252), (200, 210, 226), (140, 150, 170))}
    for w in HEIR_WEAP:
        S8.save_item(w[0], S8.weapon(w[1], cols[w[2]], w[2]))
        S8.item_model(w[0], handheld=True)
        lang[f"item.{NS}.{w[0]}"] = name(w[0])
        lang[f"tooltip.{NS}.{w[0]}"] = w[9]
    for u in ups:
        S8.save_item(u["kit"], S8.kit(u["group"], u["id"]))
        S8.item_model(u["kit"])
        lang[f"item.{NS}.{u['kit']}"] = name(u["id"]) + " Kit"
        lang[f"tooltip.{NS}.{u['kit']}"] = u["desc"] + " Apply at the artisan's bench."


def block_art(lang):
    rnd = random.Random(1011)

    def tile(base, var=0.1, seam=True, glow=None, pattern=None):
        img = Image.new("RGBA", (16, 16))
        for x in range(16):
            for y in range(16):
                c = art.shade(base, 1 - var + 2 * var * rnd.random())
                if seam and (x % 8 == 0 or y % 8 == 0):
                    c = art.shade(c, 0.78)
                img.putpixel((x, y), (*c, 255))
        if pattern == "ring" and glow:
            for a in range(0, 360, 12):
                x, y = int(8 + 5 * math.cos(math.radians(a))), int(8 + 5 * math.sin(math.radians(a)))
                img.putpixel((x, y), (*glow, 255))
        if pattern == "core" and glow:
            for x in range(5, 11):
                for y in range(5, 11):
                    img.putpixel((x, y), (*art.shade(glow, 0.8 + 0.3 * rnd.random()), 255))
        if pattern == "veins" and glow:
            for _ in range(5):
                x, y = rnd.randrange(16), rnd.randrange(16)
                for _ in range(6):
                    img.putpixel((x % 16, y % 16), (*glow, 255))
                    x += rnd.choice((-1, 0, 1))
                    y += 1
        return img

    blocks = {
        "guardian_altar": (tile((56, 56, 66), glow=(120, 220, 230), pattern="ring"), None),
        "engine_door": (tile((44, 40, 50), glow=(200, 120, 255), pattern="ring"), None),
        "engine_stone": (tile((36, 34, 42), var=0.06, glow=(140, 60, 40), pattern="veins"), None),
    }
    for b, (img, _) in blocks.items():
        A9.tex(b, img)
        A9.cube(b, b)
        A9.state(b, {"": {"model": f"{NS}:block/{b}"}})
        A9.item_block(b)
    devices = {"conductor_pylon": ((80, 140, 120), (140, 220, 255)), "warding_brazier": ((40, 38, 42), (255, 170, 60)),
               "lamplighter_post": ((150, 110, 70), (255, 220, 120)), "engine_coil": ((110, 80, 140), (230, 170, 255)),
               "silent_bell": ((170, 140, 60), (60, 210, 200)), "root_heart": ((90, 60, 40), (255, 170, 60))}
    for b, (base, glow) in devices.items():
        A9.tex(b + "_off", tile(base, pattern="core", glow=art.shade(base, 0.6)))
        A9.tex(b + "_on", tile(base, pattern="core", glow=glow))
        A9.cube(b + "_off", b + "_off")
        A9.cube(b + "_on", b + "_on")
        A9.state(b, {"lit=false": {"model": f"{NS}:block/{b}_off"}, "lit=true": {"model": f"{NS}:block/{b}_on"}})
        A9.item_block(b, b + "_on")
    names = {"guardian_altar": "Guardian Altar", "engine_door": "Heart Engine Door", "engine_stone": "Engine Stone", "conductor_pylon": "Conductor Pylon",
             "warding_brazier": "Old Brazier", "lamplighter_post": "Lamplighter's Post", "engine_coil": "Engine Coil", "silent_bell": "Silent Bell",
             "root_heart": "Root Heart"}
    lang.update({f"block.{NS}.{k}": v for k, v in names.items()})
    return list(names)


# ------------------------------------------------------------------------------------------------ boss models
class Rig:
    """Authors an articulated cuboid model: named parts with pivots, hierarchy, materials and glow."""

    def __init__(self, bid, palette, scale=1.0):
        self.bid = bid
        self.pal = palette
        self.scale = scale
        self.parts = []
        self.idle = []
        self.walk = []
        self.poses = {}
        self.extra = {}

    def part(self, nm, parent, pivot, boxes, rot=(0, 0, 0), mat="a", glow=False):
        self.parts.append({"name": nm, "parent": parent, "pivot": list(pivot), "rot": list(rot), "boxes": [list(b) for b in boxes], "mat": mat,
                           "glow": glow})
        return nm

    def sway(self, part, axis, amp, speed, phase=0.0):
        self.idle.append({"part": part, "axis": axis, "amp": amp, "speed": speed, "phase": phase})

    def stride(self, part, axis, amp, phase=0.0):
        self.walk.append({"part": part, "axis": axis, "amp": amp, "phase": phase})

    def pose(self, key, **parts):
        self.poses[key] = {k: list(v) for k, v in parts.items()}


def build_texture(rig, rnd):
    """Packs every box's UV net, paints it from the rig's palette (shaded faces, edge darkening, material patterns)."""
    W = 256
    x = y = row = 0
    layout = []
    for p in rig.parts:
        for b in p["boxes"]:
            w, h, d = int(math.ceil(b[3])), int(math.ceil(b[4])), int(math.ceil(b[5]))
            uw, uh = 2 * d + 2 * w, d + h
            if x + uw > W:
                x, y, row = 0, y + row, 0
            layout.append((p, b, x, y, w, h, d))
            b.extend([x, y])
            x += uw + 1
            row = max(row, uh + 1)
    H = 1
    while H < y + row:
        H *= 2
    H = max(H, 64)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    # Stage 11: structured painting instead of per-texel noise — face lighting, top highlight / bottom rim, low-frequency
    # tonal variation and material patterns drawn as shapes (moss clumps, scale rows, bands, facets, crack lines, spots).
    def hsh(*xs):
        v = 2166136261
        for x_ in xs:
            v = ((v ^ (int(x_) & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
        return (v >> 8) / float(1 << 24)

    seed = sum(map(ord, rig.bid))
    for bi, (p, b, u, v, w, h, d) in enumerate(layout):
        c = rig.pal[p["mat"]]
        pat = rig.pal.get(p["mat"] + "_pattern", "")
        crack = rig.pal.get("crack", (255, 140, 40))
        faces = [("up", u + d, v, w, d, 1.16), ("down", u + d + w, v, w, d, 0.64), ("e", u, v + d, d, h, 0.86), ("n", u + d, v + d, w, h, 1.0),
                 ("w", u + d + w, v + d, d, h, 0.86), ("s", u + 2 * d + w, v + d, w, h, 0.94)]
        for fname, fx, fy, fw, fh, light in faces:
            side = fname not in ("up", "down")
            # crack lines: a few random walks per side face
            crack_px = set()
            if pat == "cracks" and fname != "down":
                for k in range(max(1, (fw * fh) // 90)):
                    cx, cy = int(hsh(seed, bi, fx, k) * fw), int(hsh(seed, bi, fy, k, 7) * fh)
                    for step in range(max(3, (fw + fh) // 3)):
                        crack_px.add((cx, cy))
                        r_ = hsh(seed, bi, k, step)
                        cx += 1 if r_ < 0.4 else -1 if r_ < 0.6 else 0
                        cy += 1 if r_ > 0.3 else 0
            for i in range(fw):
                for j in range(fh):
                    col = c
                    tone = 0.96 + 0.08 * hsh(seed, bi, fx + i // 3, fy + j // 3)
                    if pat == "moss":
                        clump = hsh(seed, fx + i // 3, fy + j // 2) > (0.55 if fname == "up" else 0.75 if j < fh * 0.4 else 0.95)
                        if clump and hsh(seed + 1, fx + i, fy + j) > 0.25:
                            col = art.shade(rig.pal.get("moss", (90, 140, 60)), 0.9 + 0.2 * hsh(seed + 2, fx + i, fy + j))
                    elif pat == "stripes" and side and j % 4 == 0 and hsh(seed, bi, fx + i // 4, j) > 0.3:
                        col = art.shade(c, 0.88)
                    elif pat == "scales" and fname != "down":
                        row, off = j // 2, (j // 2) % 2
                        if (i + off * 2) % 4 == 0 or j % 2 == 1 and (i + off * 2) % 4 == 3:
                            col = art.shade(c, 0.8)
                        elif j % 2 == 0 and (i + off * 2) % 4 == 2:
                            col = art.shade(c, 1.12)
                    elif pat == "facets" and fname != "down":
                        if (i + j) % 5 == 0:
                            col = art.shade(c, 1.3)
                        elif (i - j) % 7 == 0:
                            col = art.shade(c, 0.85)
                    elif pat == "cracks" and (i, j) in crack_px:
                        col = crack
                    elif pat == "spots" and fname != "down":
                        gx_, gy_ = (fx + i) // 4, (fy + j) // 4
                        ox, oy = int(hsh(seed, gx_, gy_) * 3), int(hsh(seed, gy_, gx_) * 3)
                        if (fx + i) % 4 in (ox, ox + 1) and (fy + j) % 4 in (oy, oy + 1) and hsh(seed, gx_, gy_, 3) > 0.5:
                            col = rig.pal.get("spot", (240, 240, 230))
                    elif pat == "stars":
                        gx_, gy_ = (fx + i) // 5, (fy + j) // 5
                        if (fx + i) % 5 == int(hsh(seed, gx_, gy_) * 5) and (fy + j) % 5 == int(hsh(seed, gy_, gx_) * 5) and hsh(gx_, gy_) > 0.55:
                            col = (255, 255, 240)
                    shade = light * tone
                    if side and fh > 2:
                        if j == fh - 1:
                            shade *= 0.74
                        elif j == 0:
                            shade *= 1.1
                    elif not side and (i == 0 or i == fw - 1):
                        shade *= 0.9
                    if 0 <= fx + i < W and 0 <= fy + j < H:
                        img.putpixel((fx + i, fy + j), (*art.shade(col, shade), 255))
                        if p["glow"] or (pat == "cracks" and col == crack):
                            gc = rig.pal.get("glow", (255, 220, 120)) if p["glow"] else col
                            glow.putpixel((fx + i, fy + j), (*gc, 255))
    return img, glow, W, H


def save_rig(rig):
    rnd = random.Random(sum(map(ord, rig.bid)))
    img, glow, W, H = build_texture(rig, rnd)
    tex_dir = os.path.join(ASSETS, "textures", "entity", "boss")
    os.makedirs(tex_dir, exist_ok=True)
    img.save(os.path.join(tex_dir, rig.bid + ".png"))
    glow.save(os.path.join(tex_dir, rig.bid + "_glow.png"))
    obj = {"texture": [W, H], "scale": rig.scale, "parts": rig.parts, "idle": rig.idle, "walk": rig.walk, "poses": rig.poses}
    obj.update(rig.extra)
    wjson(os.path.join(ASSETS, "bosses", rig.bid + ".json"), obj)
    return len(rig.parts)


def quad_legs(r, body, pts, size, length, mat="a"):
    names = []
    for i, (lx, ly, lz) in enumerate(pts):
        n = r.part(f"leg{i}", body, (lx, ly, lz), [(-size / 2, -length, -size / 2, size, length, size)], mat=mat)
        names.append(n)
        r.stride(n, 0, 0.7, 0 if i in (0, 3) else math.pi)
    return names


def rigs():
    out = []
    # --- mossback: armoured boar-beast with tusks, horn plates and a mossy hump
    r = Rig("mossback", {"a": (92, 70, 50), "a_pattern": "moss", "moss": (88, 130, 52), "b": (110, 140, 70), "b_pattern": "moss",
                         "c": (226, 214, 180), "glow": (255, 190, 80)})
    body = r.part("body", "root", (0, 24, 0), [(-13, -9, -18, 26, 18, 36)])
    r.part("hump", body, (0, 9, -4), [(-10, 0, -12, 20, 7, 22), (-6, 7, -8, 12, 4, 14)], mat="b")
    head = r.part("head", body, (0, 3, -18), [(-8, -8, -11, 16, 14, 11), (-5, -8, -17, 10, 8, 6)])
    r.part("tusk_l", head, (-6, -5, -16), [(-1, -1, -3, 2, 9, 2)], rot=(-0.4, 0, 0.2), mat="c")
    r.part("tusk_r", head, (6, -5, -16), [(-1, -1, -3, 2, 9, 2)], rot=(-0.4, 0, -0.2), mat="c")
    r.part("horn_l", head, (-7, 5, -7), [(-2, 0, -2, 4, 7, 5)], rot=(0.5, 0, -0.4), mat="c")
    r.part("horn_r", head, (7, 5, -7), [(-2, 0, -2, 4, 7, 5)], rot=(0.5, 0, 0.4), mat="c")
    r.part("eye_l", head, (-8, 1, -9), [(-0.5, 0, 0, 1, 2, 2)], mat="c", glow=True)
    r.part("eye_r", head, (8, 1, -9), [(-0.5, 0, 0, 1, 2, 2)], mat="c", glow=True)
    tail = r.part("tail", body, (0, 4, 18), [(-2, -2, 0, 4, 4, 11)], rot=(0.5, 0, 0))
    quad_legs(r, body, [(-8, -8, -12), (8, -8, -12), (-8, -8, 12), (8, -8, 12)], 7, 15)
    r.sway(head, 0, 0.05, 0.08)
    r.sway(tail, 1, 0.3, 0.12)
    r.pose("charge:windup", head=(0.45, 0, 0), body=(0.08, 0, 0))
    r.pose("charge:active", head=(0.6, 0, 0), body=(0.12, 0, 0))
    r.pose("double_charge:windup", head=(0.45, 0, 0))
    r.pose("double_charge:active", head=(0.6, 0, 0), body=(0.12, 0, 0))
    r.pose("gore:windup", head=(-0.5, 0, 0))
    r.pose("gore:active", head=(0.7, 0, 0))
    r.pose("tail:windup", body=(0, 0.35, 0), tail=(0, -0.6, 0))
    r.pose("tail:active", body=(0, -0.5, 0), tail=(0, 1.3, 0))
    r.pose("stomp:windup", body=(-0.45, 0, 0), leg0=(-1.1, 0, 0), leg1=(-1.1, 0, 0))
    r.pose("stomp:active", body=(0.15, 0, 0), leg0=(0.3, 0, 0), leg1=(0.3, 0, 0))
    r.pose("hurl:windup", head=(-0.6, 0, 0))
    r.pose("hurl:active", head=(0.5, 0, 0))
    r.pose("stagger", head=(0.8, 0, 0.3), body=(0, 0, 0.18))
    r.pose("defeat", body=(0, 0, 1.45), head=(0.4, 0, 0))
    r.pose("intro", head=(-0.6, 0, 0), body=(-0.15, 0, 0))
    out.append(r)

    # --- sporemother: towering stalk, a vast frilled cap, hanging tendrils, a heart-cap that opens
    r = Rig("sporemother", {"a": (214, 200, 170), "a_pattern": "stripes", "b": (150, 60, 48), "b_pattern": "spots", "spot": (240, 230, 210),
                            "c": (120, 90, 80), "d": (255, 160, 140), "glow": (190, 255, 200)}, scale=1.0)
    stalk = r.part("stalk", "root", (0, 0, 0), [(-7, 0, -7, 14, 44, 14), (-10, 0, -10, 20, 6, 20)])
    cap = r.part("cap", stalk, (0, 44, 0), [(-26, 0, -26, 52, 10, 52), (-20, 10, -20, 40, 8, 40), (-12, 18, -12, 24, 5, 24)], mat="b")
    r.part("gills", cap, (0, 0, 0), [(-24, -3, -24, 48, 3, 48)], mat="c")
    heart = r.part("heart", stalk, (0, 30, -7), [(-4, -4, -2, 8, 8, 3)], mat="d", glow=True)
    for i in range(8):
        a = i * math.pi / 4
        t = r.part(f"tendril{i}", cap, (math.cos(a) * 20, -2, math.sin(a) * 20), [(-1.5, -22, -1.5, 3, 22, 3)], mat="a")
        r.sway(t, 0 if i % 2 else 2, 0.15, 0.06, i)
    r.part("arm_l", stalk, (-7, 26, 0), [(-16, -3, -3, 16, 6, 6), (-20, -12, -3, 5, 12, 6)], rot=(0, 0, 0.3), mat="a")
    r.part("arm_r", stalk, (7, 26, 0), [(0, -3, -3, 16, 6, 6), (15, -12, -3, 5, 12, 6)], rot=(0, 0, -0.3), mat="a")
    r.sway(cap, 2, 0.03, 0.04)
    r.sway(stalk, 0, 0.02, 0.05)
    r.pose("tendril:windup", arm_r=(0, 0, -1.6))
    r.pose("tendril:active", arm_r=(0, 0, 0.6), stalk=(0.08, 0, 0))
    r.pose("bursts:windup", cap=(-0.12, 0, 0))
    r.pose("bursts:active", cap=(0.1, 0, 0))
    r.pose("exhale:windup", cap=(-0.25, 0, 0), arm_l=(0, 0, 1.0), arm_r=(0, 0, -1.0))
    r.pose("exhale:active", cap=(0.2, 0, 0), stalk=(0.1, 0, 0))
    r.pose("exhale:recover", cap=(0.25, 0, 0), heart=(0, 0, 0))
    r.pose("volley:windup", stalk=(-0.08, 0, 0))
    r.pose("stagger", cap=(0.3, 0, 0.2), stalk=(0, 0, 0.1))
    r.pose("defeat", stalk=(0, 0, 1.3))
    r.pose("intro", cap=(-0.3, 0, 0))
    r.extra["flagGlow"] = {"0": ["heart"]}
    out.append(r)

    # --- choirwarden: tall knight of nacre with a tower shield and a long blade
    r = Rig("choirwarden", {"a": (220, 230, 232), "a_pattern": "scales", "b": (40, 90, 96), "c": (150, 210, 200), "glow": (90, 255, 230)})
    torso = r.part("torso", "root", (0, 30, 0), [(-8, 0, -5, 16, 18, 10), (-6, -6, -4, 12, 6, 8)])
    r.part("cloak", torso, (0, 16, 5), [(-9, -26, 0, 18, 28, 2)], mat="b")
    head = r.part("head", torso, (0, 18, 0), [(-5, 0, -5, 10, 11, 10), (-1, 11, -6, 2, 7, 12)])
    r.part("visor", head, (0, 5, -5), [(-4, 0, -0.6, 8, 2, 1)], mat="c", glow=True)
    arm_r = r.part("arm_r", torso, (-10, 15, 0), [(-3, -16, -3, 5, 18, 6)])
    r.part("blade", arm_r, (-0.5, -15, 0), [(-1, -2, -1, 2, 4, 2), (-0.5, -3, -30, 1, 3, 28)], rot=(0.0, 0, 0), mat="c")
    arm_l = r.part("arm_l", torso, (10, 15, 0), [(-2, -16, -3, 5, 18, 6)])
    r.part("shield", arm_l, (3, -10, -4), [(0, -12, -8, 2, 24, 16)], mat="b")
    r.part("leg_l", "root", (4, 30, 0), [(-3, -30, -3, 6, 30, 6)])
    r.part("leg_r", "root", (-4, 30, 0), [(-3, -30, -3, 6, 30, 6)])
    r.stride("leg_l", 0, 0.6, 0)
    r.stride("leg_r", 0, 0.6, math.pi)
    r.stride(arm_r, 0, 0.3, math.pi)
    r.sway(head, 1, 0.08, 0.05)
    r.sway("cloak", 0, 0.06, 0.07)
    r.pose("combo:windup", arm_r=(-2.0, 0, 0.2))
    r.pose("combo:active", arm_r=(-0.2, -0.6, 0), torso=(0, -0.4, 0))
    r.pose("thrust:windup", arm_r=(-1.5, 0.3, 0), torso=(0, 0.4, 0))
    r.pose("thrust:active", arm_r=(-1.57, 0, 0), torso=(0.15, -0.2, 0))
    r.pose("bash:windup", arm_l=(-0.4, 0.6, 0))
    r.pose("bash:active", arm_l=(-1.3, -0.3, 0), torso=(0.1, 0.3, 0))
    r.pose("javelin:windup", arm_r=(-2.8, 0, 0))
    r.pose("javelin:active", arm_r=(-0.9, 0, 0))
    r.pose("song:windup", head=(-0.4, 0, 0), arm_r=(-0.5, 0, 0.6), arm_l=(-0.5, 0, -0.6))
    r.pose("song:active", head=(-0.5, 0, 0), arm_r=(-0.8, 0, 0.9), arm_l=(-0.8, 0, -0.9))
    r.pose("stagger", torso=(0.3, 0, 0.15), head=(0.4, 0, 0))
    r.pose("defeat", torso=(1.2, 0, 0), head=(0.5, 0, 0))
    r.pose("intro", head=(-0.3, 0, 0), arm_r=(-2.4, 0, 0))
    out.append(r)

    # --- rimefang: huge frost wolf with an ice-spined back
    r = Rig("rimefang", {"a": (220, 228, 236), "a_pattern": "stripes", "b": (60, 70, 86), "c": (170, 230, 255), "glow": (150, 240, 255)})
    body = r.part("body", "root", (0, 20, 0), [(-8, -7, -15, 16, 14, 30)])
    r.part("ruff", body, (0, 2, -12), [(-10, -6, -4, 20, 14, 8)])
    head = r.part("head", body, (0, 4, -16), [(-6, -5, -9, 12, 10, 9), (-3, -5, -15, 6, 5, 6)])
    r.part("jaw", head, (0, -5, -9), [(-3, -2, -6, 6, 2, 6)], mat="b")
    r.part("ear_l", head, (-4, 5, -4), [(-1.5, 0, -1, 3, 5, 2)])
    r.part("ear_r", head, (4, 5, -4), [(-1.5, 0, -1, 3, 5, 2)])
    r.part("eyes", head, (0, 1, -9), [(-4, 0, -0.5, 8, 1, 1)], mat="c", glow=True)
    for i in range(5):
        r.part(f"spine{i}", body, (0, 7, -10 + i * 5), [(-1, 0, -1, 2, 5 - abs(i - 2), 2)], rot=(-0.4, 0, 0), mat="c", glow=True)
    tail = r.part("tail", body, (0, 4, 15), [(-2, -2, 0, 4, 4, 14)], rot=(0.6, 0, 0))
    quad_legs(r, body, [(-5, -6, -10), (5, -6, -10), (-5, -6, 10), (5, -6, 10)], 5, 14)
    r.sway(tail, 1, 0.35, 0.15)
    r.sway(head, 0, 0.05, 0.1)
    r.pose("pounce:windup", body=(0.25, 0, 0), head=(0.2, 0, 0), leg2=(0.6, 0, 0), leg3=(0.6, 0, 0))
    r.pose("pounce:active", body=(-0.35, 0, 0), leg0=(-1.2, 0, 0), leg1=(-1.2, 0, 0), jaw=(0.6, 0, 0))
    r.pose("bite:windup", head=(-0.3, 0, 0), jaw=(0.7, 0, 0))
    r.pose("bite:active", head=(0.35, 0, 0), jaw=(0, 0, 0))
    r.pose("breath:windup", head=(-0.4, 0, 0), jaw=(0.3, 0, 0))
    r.pose("breath:active", head=(0.1, 0, 0), jaw=(0.9, 0, 0))
    r.pose("howl:windup", head=(-0.9, 0, 0))
    r.pose("howl:active", head=(-1.1, 0, 0), jaw=(0.6, 0, 0))
    r.pose("ambush:windup", body=(0.2, 0, 0), head=(0.3, 0, 0))
    r.pose("ambush:active", body=(-0.4, 0, 0), jaw=(0.7, 0, 0))
    r.pose("shards:windup", head=(-0.3, 0, 0))
    r.pose("stagger", head=(0.5, 0, 0.4), body=(0, 0, 0.2))
    r.pose("defeat", body=(0, 0, 1.5))
    r.pose("intro", head=(-1.0, 0, 0), jaw=(0.5, 0, 0))
    out.append(r)

    # --- galvanic: towering copper construct with pylon shoulders and a blue core
    r = Rig("galvanic", {"a": (80, 150, 128), "a_pattern": "scales", "b": (110, 104, 100), "c": (200, 130, 80), "glow": (120, 200, 255)}, scale=0.5)
    pelvis = r.part("pelvis", "root", (0, 70, 0), [(-16, 0, -10, 32, 14, 20)], mat="b")
    torso = r.part("torso", pelvis, (0, 14, 0), [(-24, 0, -14, 48, 40, 28), (-14, 40, -10, 28, 8, 20)])
    r.part("core", torso, (0, 18, -14), [(-7, 0, -2, 14, 14, 3)], mat="c", glow=True)
    head = r.part("head", torso, (0, 48, -2), [(-9, 0, -9, 18, 16, 18)], mat="b")
    r.part("eyes", head, (0, 8, -9), [(-6, 0, -1, 12, 3, 1)], mat="c", glow=True)
    for side, sx in (("l", 1), ("r", -1)):
        sh = r.part(f"shoulder_{side}", torso, (sx * 26, 32, 0), [(-8, 0, -8, 16, 14, 16), (-2, 14, -2, 4, 14, 4)], mat="c")
        arm = r.part(f"arm_{side}", torso, (sx * 30, 30, 0), [(-7, -44, -7, 14, 44, 14)])
        r.part(f"fist_{side}", arm, (0, -44, 0), [(-9, -14, -9, 18, 14, 18)], mat="b")
        r.stride(arm, 0, 0.25, 0 if sx > 0 else math.pi)
        leg = r.part(f"leg_{side}", pelvis, (sx * 10, 0, 0), [(-8, -70, -8, 16, 70, 16)])
        r.stride(leg, 0, 0.35, math.pi if sx > 0 else 0)
    r.sway(head, 1, 0.05, 0.03)
    r.pose("slam:windup", arm_l=(-2.6, 0, 0), arm_r=(-2.6, 0, 0), torso=(-0.15, 0, 0))
    r.pose("slam:active", arm_l=(-0.3, 0, 0), arm_r=(-0.3, 0, 0), torso=(0.35, 0, 0))
    r.pose("sweep:windup", torso=(0, 0.7, 0), arm_l=(-1.2, 0, -0.5))
    r.pose("sweep:active", torso=(0, -0.8, 0), arm_l=(-1.4, 0, 0.6))
    r.pose("lightning:windup", arm_r=(-3.0, 0, 0), head=(-0.4, 0, 0))
    r.pose("lightning:active", arm_r=(-3.1, 0, 0.2))
    r.pose("overload:windup", torso=(-0.2, 0, 0), arm_l=(0, 0, -1.2), arm_r=(0, 0, 1.2))
    r.pose("overload:active", torso=(0.1, 0, 0), arm_l=(0, 0, -1.5), arm_r=(0, 0, 1.5))
    r.pose("bolt:windup", arm_r=(-1.6, 0.4, 0))
    r.pose("stagger", torso=(0.35, 0, 0.12), head=(0.4, 0, 0))
    r.pose("defeat", torso=(1.2, 0, 0))
    r.pose("intro", head=(-0.3, 0, 0), arm_l=(-1.8, 0, 0), arm_r=(-1.8, 0, 0))
    r.extra["flagGlow"] = {"0": ["shoulder_l", "shoulder_r"]}
    out.append(r)

    # --- cinderwurm: segmented basalt wurm with a magma-lit maw
    r = Rig("cinderwurm", {"a": (52, 46, 48), "a_pattern": "cracks", "crack": (255, 130, 40), "b": (90, 60, 50), "c": (255, 150, 60), "glow": (255, 170, 60)})
    head = r.part("head", "root", (0, 18, -12), [(-11, -9, -16, 22, 18, 16)])
    r.part("jaw_l", head, (-8, -6, -16), [(-2, -3, -10, 5, 8, 10)], rot=(0, 0.3, 0), mat="b")
    r.part("jaw_r", head, (8, -6, -16), [(-3, -3, -10, 5, 8, 10)], rot=(0, -0.3, 0), mat="b")
    r.part("maw", head, (0, -2, -16), [(-6, -4, -1, 12, 8, 1)], mat="c", glow=True)
    r.part("crest", head, (0, 9, -6), [(-3, 0, -6, 6, 6, 12)], mat="b")
    prev = head
    for i in range(6):
        size = 20 - i * 2
        seg = r.part(f"seg{i}", prev, (0, 0, 0 if i == 0 else 14 - i), [(-size / 2, -size / 2 + 1, 0, size, size - 2, 14 - i)], mat="a")
        r.sway(seg, 1, 0.12, 0.1, i * 0.8)
        prev = seg
    r.sway(head, 1, 0.06, 0.1)
    r.pose("breath:windup", head=(-0.5, 0, 0), jaw_l=(0, 0.6, 0), jaw_r=(0, -0.6, 0))
    r.pose("breath:active", head=(0.1, 0, 0), jaw_l=(0, 0.8, 0), jaw_r=(0, -0.8, 0))
    r.pose("lash:windup", head=(0, 0.6, 0), seg2=(0, -0.6, 0))
    r.pose("lash:active", head=(0, -0.6, 0), seg2=(0, 0.8, 0), seg4=(0, 0.8, 0))
    r.pose("spit:windup", head=(-0.6, 0, 0))
    r.pose("spit:active", head=(0.3, 0, 0), jaw_l=(0, 0.5, 0), jaw_r=(0, -0.5, 0))
    r.pose("erupt:active", head=(-1.2, 0, 0), jaw_l=(0, 0.8, 0), jaw_r=(0, -0.8, 0))
    r.pose("stagger", head=(0.5, 0, 0.5))
    r.pose("defeat", head=(0.6, 0, 1.2))
    r.pose("intro", head=(-1.0, 0, 0))
    r.extra["hideFlag"] = 0
    out.append(r)

    # --- lamplighter: tall robed figure, hollow hood, a lantern on a crook
    r = Rig("lamplighter", {"a": (54, 58, 74), "a_pattern": "stripes", "b": (30, 30, 40), "c": (180, 140, 80), "d": (255, 220, 120), "glow": (255, 220, 120)})
    robe = r.part("robe", "root", (0, 2, 0), [(-8, 0, -6, 16, 26, 12), (-6, 26, -5, 12, 10, 10)])
    hood = r.part("hood", robe, (0, 36, 0), [(-6, 0, -6, 12, 11, 12)], mat="a")
    r.part("face", hood, (0, 4, -6), [(-3, 0, -0.5, 6, 4, 1)], mat="d", glow=True)
    crook = r.part("arm_r", robe, (-9, 32, 0), [(-2, -14, -2, 4, 15, 4)], rot=(-0.3, 0, 0))
    staff = r.part("staff", crook, (0, -12, -2), [(-1, -18, -1, 2, 40, 2), (-1, 22, -1, 2, 2, 8)], mat="c")
    r.part("lantern", staff, (0, 20, 6), [(-2.5, -6, -2.5, 5, 6, 5)], mat="d", glow=True)
    r.part("arm_l", robe, (9, 32, 0), [(-2, -14, -2, 4, 15, 4)])
    for i in range(4):
        t = r.part(f"tatter{i}", robe, (-6 + i * 4, 0, 6), [(-1.5, -6, 0, 3, 6, 1)], mat="b")
        r.sway(t, 0, 0.25, 0.12, i)
    r.sway(robe, 0, 0.04, 0.06)
    r.sway(hood, 1, 0.1, 0.05)
    r.pose("orbs:windup", arm_r=(-1.3, 0, 0))
    r.pose("orbs:active", arm_r=(-1.6, 0.2, 0))
    r.pose("snuff:windup", arm_l=(-2.5, 0, 0), arm_r=(-2.5, 0, 0))
    r.pose("snuff:active", arm_l=(-0.3, 0, -0.8), arm_r=(-0.3, 0, 0.8))
    r.pose("beam:windup", arm_r=(-1.5, 0, 0), robe=(-0.1, 0, 0))
    r.pose("beam:active", arm_r=(-1.6, 0, 0))
    r.pose("scythe:windup", arm_r=(-2.4, 0.6, 0))
    r.pose("scythe:active", arm_r=(-0.4, -0.8, 0))
    r.pose("wisps:windup", hood=(-0.4, 0, 0))
    r.pose("stagger", robe=(0.2, 0, 0.2))
    r.pose("defeat", robe=(1.0, 0, 0))
    r.pose("intro", hood=(-0.3, 0, 0), arm_r=(-2.0, 0, 0))
    out.append(r)

    # --- resonant: crystal titan, calcite body, amethyst growths, four resonance nodes
    r = Rig("resonant", {"a": (230, 226, 220), "a_pattern": "facets", "b": (170, 120, 220), "b_pattern": "facets", "c": (255, 150, 220),
                         "glow": (255, 160, 230)}, scale=0.5)
    pelvis = r.part("pelvis", "root", (0, 80, 0), [(-20, 0, -12, 40, 16, 24)])
    torso = r.part("torso", pelvis, (0, 16, 0), [(-28, 0, -16, 56, 44, 32)])
    head = r.part("head", torso, (0, 44, -4), [(-10, 0, -10, 20, 18, 20)], mat="a")
    r.part("crown", head, (0, 18, 0), [(-8, 0, -2, 4, 14, 4), (-2, 0, -4, 4, 20, 4), (4, 0, -2, 4, 12, 4)], mat="b")
    for i, (x, y, z) in enumerate(((-24, 40, 0), (24, 40, 0), (0, 30, 16), (0, 18, -16))):
        r.part(f"node{i}", torso, (x, y, z), [(-5, 0, -5, 10, 10, 10)], mat="c", glow=True)
    for side, sx in (("l", 1), ("r", -1)):
        arm = r.part(f"arm_{side}", torso, (sx * 32, 38, 0), [(-9, -50, -9, 18, 52, 18)])
        r.part(f"shard_{side}", arm, (0, -50, 0), [(-6, -16, -6, 12, 16, 12)], mat="b")
        r.stride(arm, 0, 0.2, 0 if sx > 0 else math.pi)
        leg = r.part(f"leg_{side}", pelvis, (sx * 12, 0, 0), [(-10, -80, -10, 20, 80, 20)])
        r.stride(leg, 0, 0.3, math.pi if sx > 0 else 0)
    r.sway(head, 1, 0.04, 0.03)
    r.pose("wave:windup", torso=(-0.2, 0, 0), arm_l=(0, 0, -1.0), arm_r=(0, 0, 1.0))
    r.pose("wave:active", torso=(0.1, 0, 0), arm_l=(0, 0, -1.4), arm_r=(0, 0, 1.4))
    r.pose("slam:windup", arm_l=(-2.8, 0, 0), arm_r=(-2.8, 0, 0))
    r.pose("slam:active", arm_l=(-0.2, 0, 0), arm_r=(-0.2, 0, 0), torso=(0.4, 0, 0))
    r.pose("sweep:windup", torso=(0, 0.7, 0))
    r.pose("sweep:active", torso=(0, -0.8, 0), arm_l=(-1.3, 0, 0))
    r.pose("rain:windup", head=(-0.6, 0, 0), arm_l=(-3.0, 0, 0))
    r.pose("shards:windup", arm_r=(-1.6, 0, 0))
    r.pose("stagger", torso=(0.3, 0, 0.12))
    r.pose("defeat", torso=(1.1, 0, 0))
    r.pose("intro", head=(-0.4, 0, 0))
    r.extra["brokenParts"] = ["node0", "node1", "node2", "node3"]
    r.extra["flagGlow"] = {"4": ["node0", "node1", "node2", "node3"]}
    out.append(r)

    # --- tidemother: serpentine sea-matriarch: crested head, fins, long scaled body
    r = Rig("tidemother", {"a": (40, 110, 120), "a_pattern": "scales", "b": (200, 220, 200), "c": (250, 120, 110), "glow": (120, 255, 240)})
    head = r.part("head", "root", (0, 20, -16), [(-10, -7, -18, 20, 14, 18), (-6, -7, -24, 12, 8, 6)])
    r.part("crest", head, (0, 7, -6), [(-1, 0, -10, 2, 10, 16)], mat="c")
    r.part("fin_l", head, (-10, 0, -8), [(-12, -1, -4, 12, 2, 10)], rot=(0, 0, 0.3), mat="c")
    r.part("fin_r", head, (10, 0, -8), [(0, -1, -4, 12, 2, 10)], rot=(0, 0, -0.3), mat="c")
    r.part("eyes", head, (0, 2, -18), [(-8, 0, -0.5, 16, 2, 1)], mat="b", glow=True)
    r.part("jaw", head, (0, -7, -14), [(-6, -3, -10, 12, 3, 12)], mat="b")
    prev = head
    for i in range(6):
        size = 18 - i * 2
        seg = r.part(f"seg{i}", prev, (0, 0, 0 if i == 0 else 12), [(-size / 2, -size / 2, 0, size, size, 12)], mat="a")
        r.sway(seg, 1, 0.15, 0.08, i)
        prev = seg
    r.part("tail_fin", prev, (0, 0, 12), [(-8, -1, 0, 16, 2, 10)], mat="c")
    r.pose("lunge:windup", head=(-0.6, 0, 0), jaw=(0.5, 0, 0))
    r.pose("lunge:active", head=(0.3, 0, 0), jaw=(0.9, 0, 0))
    r.pose("slam:windup", head=(-0.8, 0, 0), seg0=(-0.3, 0, 0))
    r.pose("slam:active", head=(0.6, 0, 0), seg0=(0.3, 0, 0))
    r.pose("grab:windup", jaw=(0.9, 0, 0))
    r.pose("grab:active", jaw=(0, 0, 0), head=(0.4, 0, 0))
    r.pose("orbs:windup", head=(-0.4, 0, 0))
    r.pose("whirlpool:active", head=(0, 0.6, 0), seg1=(0, -0.4, 0), seg3=(0, 0.4, 0))
    r.pose("stagger", head=(0.4, 0, 0.6))
    r.pose("defeat", head=(0.5, 0, 1.4))
    r.pose("intro", head=(-0.9, 0, 0), jaw=(0.6, 0, 0))
    out.append(r)

    # --- silentwarden: gaunt, very tall, antler-like sensors, sculk ribs, long arms
    r = Rig("silentwarden", {"a": (24, 34, 46), "a_pattern": "cracks", "crack": (40, 210, 200), "b": (14, 18, 24), "c": (60, 230, 220),
                             "glow": (60, 240, 230)})
    torso = r.part("torso", "root", (0, 38, 0), [(-10, 0, -6, 20, 24, 12)])
    r.part("ribs", torso, (0, 4, -6), [(-8, 0, -1, 16, 16, 1)], mat="c", glow=True)
    head = r.part("head", torso, (0, 24, -2), [(-6, 0, -6, 12, 10, 10)])
    for side, sx in (("l", 1), ("r", -1)):
        r.part(f"antler_{side}", head, (sx * 5, 9, 0), [(-1, 0, -1, 2, 14, 2), (sx * 1 - 1, 9, -1, 6 * sx if sx > 0 else 6, 2, 2)],
               rot=(0, 0, -0.5 * sx), mat="c", glow=True)
        arm = r.part(f"arm_{side}", torso, (sx * 12, 22, 0), [(-2.5, -34, -2.5, 5, 36, 5)])
        r.part(f"claw_{side}", arm, (0, -34, 0), [(-3, -8, -3, 6, 8, 6)], mat="b")
        r.stride(arm, 0, 0.3, 0 if sx > 0 else math.pi)
        leg = r.part(f"leg_{side}", "root", (sx * 5, 38, 0), [(-3, -38, -3, 6, 38, 6)])
        r.stride(leg, 0, 0.5, math.pi if sx > 0 else 0)
    r.sway(head, 1, 0.15, 0.04)
    r.sway("antler_l", 2, 0.05, 0.2)
    r.sway("antler_r", 2, 0.05, 0.2, 1)
    r.pose("boom:windup", head=(-0.3, 0, 0), torso=(-0.2, 0, 0), arm_l=(0, 0, 0.6), arm_r=(0, 0, -0.6))
    r.pose("boom:active", head=(0.3, 0, 0), torso=(0.3, 0, 0))
    r.pose("grab:windup", arm_l=(-2.2, 0, 0), arm_r=(-2.2, 0, 0))
    r.pose("grab:active", arm_l=(-1.0, 0, 0), arm_r=(-1.0, 0, 0), torso=(0.3, 0, 0))
    r.pose("stomp:windup", torso=(-0.15, 0, 0), leg_l=(-1.2, 0, 0))
    r.pose("stomp:active", leg_l=(0.2, 0, 0))
    r.pose("swipe:windup", arm_r=(-2.0, 0.6, 0))
    r.pose("swipe:active", arm_r=(-0.4, -0.8, 0))
    r.pose("stagger", torso=(0.35, 0, 0.2), head=(0.5, 0, 0))
    r.pose("defeat", torso=(1.3, 0, 0))
    r.pose("intro", head=(-0.6, 0, 0), arm_l=(-0.8, 0, 0.5), arm_r=(-0.8, 0, -0.5))
    out.append(r)

    # --- rootsovereign: walking treant of the heartwood, root legs, branch arms, a glowing heart knot
    r = Rig("rootsovereign", {"a": (90, 64, 44), "a_pattern": "stripes", "b": (80, 130, 60), "b_pattern": "moss", "moss": (110, 170, 70),
                              "c": (255, 170, 60), "glow": (255, 180, 80)}, scale=0.5)
    trunk = r.part("trunk", "root", (0, 40, 0), [(-20, 0, -18, 40, 70, 36)])
    r.part("heart", trunk, (0, 40, -18), [(-6, 0, -2, 12, 12, 3)], mat="c", glow=True)
    crown = r.part("crown", trunk, (0, 70, 0), [(-34, 0, -30, 68, 26, 60), (-24, 26, -20, 48, 14, 40)], mat="b")
    face = r.part("face", trunk, (0, 58, -18), [(-10, 0, -2, 20, 4, 2)], mat="c", glow=True)
    for side, sx in (("l", 1), ("r", -1)):
        arm = r.part(f"arm_{side}", trunk, (sx * 22, 60, 0), [(-6, -48, -6, 12, 50, 12)], rot=(0, 0, -0.25 * sx))
        r.part(f"twig_{side}", arm, (0, -48, 0), [(-10, -14, -3, 20, 14, 6)], mat="a")
        r.stride(arm, 0, 0.2, 0 if sx > 0 else math.pi)
        for j in range(2):
            leg = r.part(f"root_{side}{j}", trunk, (sx * (10 + j * 6), 2, -8 + j * 16), [(-6, -42, -6, 12, 44, 12)], rot=(0.2 * (1 - 2 * j), 0, 0.2 * sx))
            r.stride(leg, 0, 0.3, j * math.pi + (0 if sx > 0 else math.pi / 2))
    r.sway(crown, 2, 0.03, 0.03)
    r.pose("roots:windup", arm_l=(-2.0, 0, 0), arm_r=(-2.0, 0, 0))
    r.pose("roots:active", arm_l=(-0.2, 0, 0), arm_r=(-0.2, 0, 0), trunk=(0.25, 0, 0))
    r.pose("slam:windup", arm_r=(-2.8, 0, 0))
    r.pose("slam:active", arm_r=(-0.1, 0, 0), trunk=(0.3, 0, 0))
    r.pose("swat:windup", trunk=(0, 0.6, 0), arm_l=(-1.0, 0, -0.6))
    r.pose("swat:active", trunk=(0, -0.7, 0), arm_l=(-1.2, 0, 0.6))
    r.pose("thorns:windup", crown=(-0.2, 0, 0))
    r.pose("thorns:active", crown=(0.15, 0, 0))
    r.pose("stagger", trunk=(0.2, 0, 0.15))
    r.pose("defeat", trunk=(1.2, 0, 0))
    r.pose("intro", crown=(-0.2, 0, 0), arm_l=(-1.5, 0, 0), arm_r=(-1.5, 0, 0))
    out.append(r)

    # --- seamwatcher: a great starlit eye in a shell of plates, trailing star-tendrils
    r = Rig("seamwatcher", {"a": (30, 28, 50), "a_pattern": "stars", "b": (60, 56, 90), "c": (255, 240, 200), "d": (255, 200, 90), "glow": (255, 240, 200)})
    core = r.part("core", "root", (0, 18, 0), [(-16, -16, -16, 32, 32, 32)])
    r.part("eye", core, (0, 0, -16), [(-10, -10, -2, 20, 20, 2)], mat="c", glow=True)
    r.part("pupil", core, (0, 0, -18), [(-4, -4, -1, 8, 8, 1)], mat="d", glow=True)
    for i in range(6):
        a = i * math.pi / 3
        pl = r.part(f"plate{i}", core, (math.cos(a) * 18, math.sin(a) * 18, 0), [(-6, -6, -8, 12, 12, 16)], rot=(0, 0, a), mat="b")
        r.sway(pl, 2, 0.08, 0.05, i)
    for i in range(5):
        t = r.part(f"tendril{i}", core, (-12 + i * 6, -16, 6), [(-1.5, -26, -1.5, 3, 26, 3)], mat="a")
        r.sway(t, 0, 0.25, 0.08, i)
    r.sway(core, 1, 0.05, 0.02)
    r.pose("gaze:windup", core=(0.2, 0, 0))
    r.pose("gaze:active", core=(0.25, 0, 0))
    r.pose("starfall:windup", core=(-0.5, 0, 0))
    r.pose("gravity:active", core=(0, 0, 0.3))
    r.pose("orbs:windup", core=(-0.2, 0, 0))
    r.pose("claw:windup", tendril2=(-1.5, 0, 0))
    r.pose("claw:active", tendril2=(0.6, 0, 0))
    r.pose("stagger", core=(0.5, 0, 0.5))
    r.pose("defeat", core=(1.5, 0, 0.5))
    r.pose("intro", core=(-0.4, 0, 0))
    out.append(r)

    # --- somnarch: colossus rising from a molten mass, sculk-veined body, crystal crown, vast arms
    r = Rig("somnarch", {"a": (22, 30, 36), "a_pattern": "cracks", "crack": (255, 120, 50), "b": (40, 200, 200), "c": (200, 140, 255),
                         "d": (255, 120, 60), "glow": (255, 140, 70)}, scale=0.5)
    mass = r.part("mass", "root", (0, 0, 0), [(-30, 0, -26, 60, 24, 52), (-24, 24, -20, 48, 16, 40)])
    torso = r.part("torso", mass, (0, 40, 0), [(-26, 0, -16, 52, 52, 32)])
    r.part("ribs", torso, (0, 10, -16), [(-20, 0, -2, 40, 30, 2)], mat="b", glow=True)
    r.part("heartglow", torso, (0, 26, -18), [(-6, 0, -1, 12, 12, 1)], mat="d", glow=True)
    head = r.part("head", torso, (0, 52, -6), [(-14, 0, -14, 28, 24, 24), (-10, 0, -22, 20, 12, 8)])
    r.part("eyes", head, (0, 14, -14), [(-10, 0, -1, 20, 3, 1)], mat="d", glow=True)
    for i, (x, h) in enumerate(((-10, 22), (-4, 30), (4, 30), (10, 22))):
        r.part(f"crown{i}", head, (x, 24, 0), [(-2, 0, -2, 4, h, 4)], rot=(0, 0, -x * 0.03), mat="c", glow=True)
    for side, sx in (("l", 1), ("r", -1)):
        arm = r.part(f"arm_{side}", torso, (sx * 30, 46, 0), [(-10, -60, -10, 20, 62, 20)])
        r.part(f"hand_{side}", arm, (0, -60, 0), [(-12, -16, -12, 24, 16, 24)], mat="a")
        r.stride(arm, 0, 0.15, 0 if sx > 0 else math.pi)
    r.sway(torso, 0, 0.03, 0.025)
    r.sway(head, 1, 0.06, 0.02)
    r.pose("swipe:windup", torso=(0, 0.6, 0), arm_l=(-1.4, 0, -0.5))
    r.pose("swipe:active", torso=(0, -0.7, 0), arm_l=(-1.2, 0, 0.6))
    r.pose("echo_charge:windup", torso=(0.3, 0, 0), head=(0.3, 0, 0))
    r.pose("echo_charge:active", torso=(0.45, 0, 0), head=(0.4, 0, 0))
    r.pose("echo_spores:windup", head=(-0.5, 0, 0))
    r.pose("echo_boom:windup", head=(-0.3, 0, 0), torso=(-0.25, 0, 0))
    r.pose("echo_boom:active", head=(0.35, 0, 0), torso=(0.3, 0, 0))
    r.pose("dream_wave:windup", arm_l=(0, 0, -1.2), arm_r=(0, 0, 1.2))
    r.pose("dream_wave:active", arm_l=(0, 0, -1.5), arm_r=(0, 0, 1.5))
    r.pose("head_slam:windup", torso=(-0.35, 0, 0), head=(-0.5, 0, 0))
    r.pose("head_slam:active", torso=(0.6, 0, 0), head=(0.5, 0, 0))
    r.pose("wake_roar:windup", torso=(-0.4, 0, 0), head=(-0.8, 0, 0), arm_l=(-2.5, 0, 0), arm_r=(-2.5, 0, 0))
    r.pose("wake_roar:active", torso=(-0.5, 0, 0), head=(-1.0, 0, 0), arm_l=(-2.8, 0, -0.4), arm_r=(-2.8, 0, 0.4))
    r.pose("wake_roar:recover", torso=(-0.3, 0, 0), head=(-0.6, 0, 0))
    r.pose("motes:windup", arm_r=(-1.6, 0, 0))
    r.pose("stagger", torso=(0.4, 0, 0.15), head=(0.5, 0, 0))
    r.pose("transition", torso=(-0.5, 0, 0), head=(-0.9, 0, 0), arm_l=(-2.0, 0, -0.5), arm_r=(-2.0, 0, 0.5))
    r.pose("defeat", torso=(0.9, 0, 0), head=(0.8, 0, 0))
    r.pose("intro", torso=(-0.3, 0, 0), head=(-0.7, 0, 0))
    out.append(r)
    return out


def main(lang_in=None):
    lang = {}
    # site families (appended after stage 9 wrote the file)
    p = os.path.join(DATA, NS, "exploration", "content.json")
    c = json.load(open(p))
    c["families"] = [f for f in c["families"] if not f["id"].startswith("lair_")] + lair_families()
    wjson(p, c)
    acc, weap, ups = equipment()
    loot()
    damage_types()
    item_art(lang)
    heirloom_art(lang, acc, weap, ups)
    blocks = block_art(lang)
    parts = 0
    import boss_refine
    for rig in rigs():
        boss_refine.refine(rig)  # Stage 11 detail pass (no animated part changes)
        parts += save_rig(rig)
    # visibility: lair blocks are technical; boss rewards and signs appear after being obtained
    vp = os.path.join(DATA, NS, "guide", "visibility.json")
    v = json.load(open(vp))
    v["technical"] = sorted(set(v["technical"]) | {I(b) for b in blocks})
    sens = {I(s) for s in SIGN.values()} | {I(m) for m in MATERIAL.values()} | {I(a["id"]) for a in acc} | {I(w[0]) for w in HEIR_WEAP} \
        | {I(u["kit"]) for u in ups}
    v["sensitive"] = sorted(set(v["sensitive"]) | sens)
    wjson(vp, v)
    for b in BOSSES:
        lang[f"entity.{NS}.boss_{b}"] = TITLES[b]
    lang[f"entity.{NS}.boss_projectile"] = "Guardian Projectile"
    lang[f"entity.{NS}.creature_spit"] = "Venom Spit"
    lang["death.attack.underdeep.boss"] = "%1$s was slain by %2$s"
    lang["death.attack.underdeep.boss.player"] = "%1$s was slain by %2$s while fighting %3$s"
    lang["key.underdeep.dodge"] = "Dodge"
    print(f"stage 10: {len(LAIRS)} lair families, {len(acc)} heirloom accessories, {len(weap)} heirloom weapons, {len(ups)} guardian upgrade kits, "
          f"{len(BOSSES)} boss rigs ({parts} parts), {len(blocks)} lair blocks")
    return lang


if __name__ == "__main__":
    main()
