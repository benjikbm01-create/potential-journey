#!/usr/bin/env python3
"""
Generates every asset for Underdeep's custom blocks: procedural 16x16 textures, blockstates, block
and item models, loot tables, block tags and English names. Run from the repository root:

    python3 tools/assets/generate_assets.py

Textures are deterministic (seeded) so re-running produces identical files.
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "underdeep")
DATA = os.path.join(ROOT, "data")
TEX = os.path.join(ASSETS, "textures", "block")


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def clamp(v):
    return max(0, min(255, int(v)))


def noisy(name, base, var, specks=(), speck_count=0, seed=1, alpha=255, holes=0.0):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            n = rnd.uniform(-var, var)
            a = 0 if rnd.random() < holes else alpha
            img.putpixel((x, y), (clamp(base[0] + n), clamp(base[1] + n), clamp(base[2] + n), a))
    for _ in range(speck_count):
        c = rnd.choice(specks)
        x, y = rnd.randrange(16), rnd.randrange(16)
        img.putpixel((x, y), c + (255,))
        if rnd.random() < 0.4:
            img.putpixel(((x + 1) % 16, y), tuple(clamp(v * 0.8) for v in c) + (255,))
    img.save(os.path.join(TEX, name + ".png"))


def plant(name, stem, petal, center, seed):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.line([(8, 15), (8, 7)], fill=stem)
    d.line([(8, 12), (5, 10)], fill=stem)
    d.line([(8, 11), (11, 9)], fill=stem)
    for i in range(6):
        a = i * math.pi / 3 + rnd.random() * 0.2
        x = 8 + math.cos(a) * 3
        y = 5 + math.sin(a) * 2.5
        d.ellipse([x - 1.4, y - 1.4, x + 1.4, y + 1.4], fill=petal)
    d.ellipse([6.8, 3.8, 9.2, 6.2], fill=center)
    img.save(os.path.join(TEX, name + ".png"))


def crystals(name, body, highlight, seed):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for cx, half, top in [(4, 2, 6), (8, 3, 1), (12, 2, 5), (6, 1, 9), (10, 1, 8)]:
        cx += rnd.choice([-1, 0, 0, 1])
        d.polygon([(cx - half, 15), (cx + half, 15), (cx, top)], fill=body)
        d.line([(cx, 15), (cx, top)], fill=highlight)
    img.save(os.path.join(TEX, name + ".png"))


def strands(name, color, tip, seed, tall=False):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for sx in [3, 6, 9, 12]:
        x = sx + rnd.choice([-1, 0, 1])
        top = rnd.randint(0, 5)
        for y in range(15, top - 1, -1):
            if rnd.random() < 0.25:
                x += rnd.choice([-1, 1])
            x = max(0, min(15, x))
            c = tip if (y - top) < 2 else color
            img.putpixel((x, y), c + (255,))
    img.save(os.path.join(TEX, name + ".png"))


def textures():
    os.makedirs(TEX, exist_ok=True)
    noisy("glowmoss", (70, 122, 40), 18, [(190, 255, 120), (150, 240, 90), (230, 255, 170)], 26, 11)
    noisy("amber_moss", (150, 104, 36), 18, [(255, 214, 110), (255, 190, 70), (255, 240, 170)], 26, 12)
    noisy("starmoss", (40, 52, 110), 14, [(220, 235, 255), (150, 190, 255), (255, 255, 255)], 22, 13)
    noisy("sporeglow_soil", (96, 78, 104), 14, [(90, 230, 230), (140, 255, 240), (230, 140, 255)], 24, 14)
    noisy("ember_nylium", (110, 22, 26), 16, [(255, 110, 60), (255, 70, 40), (255, 160, 90)], 22, 15)
    noisy("golden_leaves", (214, 164, 46), 30, [(255, 232, 130), (250, 200, 80)], 18, 16, holes=0.16)
    plant("starbloom", (60, 120, 90, 255), (170, 210, 255, 255), (255, 255, 255, 255), 17)
    plant("emberbloom", (90, 40, 30, 255), (230, 50, 40, 255), (255, 170, 80, 255), 18)
    plant("amberbloom", (80, 120, 40, 255), (255, 190, 60, 255), (255, 245, 180, 255), 19)
    strands("glowreed_bottom", (60, 140, 70), (60, 140, 70), 20)
    strands("glowreed_top", (70, 160, 80), (200, 255, 150), 21)
    strands("lumen_seagrass", (40, 150, 150), (150, 255, 240), 22)
    noisy("starstone", (58, 60, 82), 10, [(255, 255, 255), (210, 225, 255), (255, 245, 210)], 14, 23)
    noisy("stormstone", (44, 46, 86), 14, [(70, 60, 120), (30, 32, 60)], 30, 24)
    noisy("charged_stormstone", (48, 46, 92), 12, [(170, 120, 255), (120, 220, 255), (210, 170, 255)], 28, 25)
    crystals("storm_crystal", (130, 90, 230, 255), (200, 220, 255, 255), 26)
    crystals("rime_crystal", (190, 230, 250, 255), (255, 255, 255, 255), 27)
    noisy("petrified_wood", (118, 104, 88), 16, [(90, 80, 70), (140, 128, 110)], 30, 28)
    # petrified wood end: concentric rings
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            v = 130 + 25 * math.sin(r * 1.6)
            img.putpixel((x, y), (clamp(v), clamp(v * 0.9), clamp(v * 0.78), 255))
    img.save(os.path.join(TEX, "petrified_wood_top.png"))
    noisy("amber_resin", (220, 130, 30), 20, [(255, 200, 90), (150, 80, 20)], 20, 29)


# (id, kind, display name, loot)
BLOCKS = [
    ("luminite_block", "cube", "Luminite Block", "self"),
    ("luminite_cluster", "cluster", "Luminite Cluster", "self"),
    ("glowcap", "cross", "Glowcap", "self"),
    ("glowmoss", "cube", "Glowmoss", "self"),
    ("amber_moss", "cube", "Amber Moss", "self"),
    ("starmoss", "cube", "Starmoss", "self"),
    ("sporeglow_soil", "cube", "Sporeglow Soil", "self"),
    ("ember_nylium", "cube", "Ember Nylium", "self"),
    ("golden_leaves", "leaves", "Golden Leaves", "leaves"),
    ("starbloom", "cross", "Starbloom", "self"),
    ("emberbloom", "cross", "Emberbloom", "self"),
    ("amberbloom", "cross", "Amberbloom", "self"),
    ("glowreed", "tall", "Glowreed", "tall"),
    ("lumen_seagrass", "cross", "Lumen Seagrass", "shears"),
    ("starstone", "cube", "Starstone", "self"),
    ("stormstone", "cube", "Stormstone", "self"),
    ("charged_stormstone", "cube", "Charged Stormstone", "self"),
    ("storm_crystal", "cluster", "Storm Crystal", "self"),
    ("rime_crystal", "cluster", "Rime Crystal", "self"),
    ("petrified_wood", "pillar", "Petrified Wood", "self"),
    ("amber_resin", "cube", "Amber Resin", "self"),
]


def models():
    lang = {}
    for bid, kind, name, loot in BLOCKS:
        bs = os.path.join(ASSETS, "blockstates", bid + ".json")
        bm = os.path.join(ASSETS, "models", "block", bid + ".json")
        im = os.path.join(ASSETS, "models", "item", bid + ".json")
        tex = f"underdeep:block/{bid}"
        if kind == "cube":
            wjson(bs, {"variants": {"": {"model": f"underdeep:block/{bid}"}}})
            wjson(bm, {"parent": "minecraft:block/cube_all", "textures": {"all": tex}})
            wjson(im, {"parent": f"underdeep:block/{bid}"})
        elif kind == "leaves":
            wjson(bs, {"variants": {"": {"model": f"underdeep:block/{bid}"}}})
            # parent "leaves" applies a tint index; with no colour provider the texture shows unchanged
            wjson(bm, {"parent": "minecraft:block/leaves", "textures": {"all": tex}})
            wjson(im, {"parent": f"underdeep:block/{bid}"})
        elif kind == "cross":
            wjson(bs, {"variants": {"": {"model": f"underdeep:block/{bid}"}}})
            wjson(bm, {"parent": "minecraft:block/cross", "textures": {"cross": tex}})
            wjson(im, {"parent": "minecraft:item/generated", "textures": {"layer0": tex}})
        elif kind == "tall":
            wjson(bs, {"variants": {"half=lower": {"model": f"underdeep:block/{bid}_bottom"},
                                    "half=upper": {"model": f"underdeep:block/{bid}_top"}}})
            wjson(os.path.join(ASSETS, "models", "block", bid + "_bottom.json"),
                  {"parent": "minecraft:block/cross", "textures": {"cross": tex + "_bottom"}})
            wjson(os.path.join(ASSETS, "models", "block", bid + "_top.json"),
                  {"parent": "minecraft:block/cross", "textures": {"cross": tex + "_top"}})
            wjson(im, {"parent": "minecraft:item/generated", "textures": {"layer0": tex + "_top"}})
        elif kind == "cluster":
            m = f"underdeep:block/{bid}"
            wjson(bs, {"variants": {
                "facing=up": {"model": m}, "facing=down": {"model": m, "x": 180},
                "facing=north": {"model": m, "x": 90}, "facing=south": {"model": m, "x": 90, "y": 180},
                "facing=east": {"model": m, "x": 90, "y": 90}, "facing=west": {"model": m, "x": 90, "y": 270}}})
            wjson(bm, {"parent": "minecraft:block/cross", "textures": {"cross": tex}})
            wjson(im, {"parent": "minecraft:item/generated", "textures": {"layer0": tex}})
        elif kind == "pillar":
            m = f"underdeep:block/{bid}"
            wjson(bs, {"variants": {"axis=y": {"model": m}, "axis=z": {"model": m, "x": 90},
                                    "axis=x": {"model": m, "x": 90, "y": 90}}})
            wjson(bm, {"parent": "minecraft:block/cube_column", "textures": {"end": tex + "_top", "side": tex}})
            wjson(im, {"parent": f"underdeep:block/{bid}"})
        lang[f"block.underdeep.{bid}"] = name
        wjson(os.path.join(DATA, "underdeep", "loot_table", "blocks", bid + ".json"), loot_table(bid, loot))
    return lang


def loot_table(bid, kind):
    item = f"underdeep:{bid}"
    shears_or_silk = {"condition": "minecraft:any_of", "terms": [
        {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
        {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
            {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]}
    if kind == "self":
        pools = [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": item}],
                  "conditions": [{"condition": "minecraft:survives_explosion"}]}]
    elif kind == "shears":
        pools = [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": item}],
                  "conditions": [{"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}]}]
    elif kind == "tall":
        pools = [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": item}],
                  "conditions": [{"condition": "minecraft:block_state_property", "block": item,
                                  "properties": {"half": "lower"}},
                                 {"condition": "minecraft:survives_explosion"}]}]
    else:  # leaves: leaves with shears/silk touch, otherwise occasional sticks
        pools = [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": item, "conditions": [shears_or_silk]},
            {"type": "minecraft:item", "name": "minecraft:stick", "conditions": [
                {"condition": "minecraft:survives_explosion"},
                {"condition": "minecraft:random_chance", "chance": 0.04}],
             "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}}]}]}]}]
    return {"type": "minecraft:block", "pools": pools, "random_sequence": f"underdeep:blocks/{bid}"}


def tags():
    def tag(path, values):
        wjson(os.path.join(DATA, path), {"replace": False, "values": values})
    u = lambda *ids: [f"underdeep:{i}" for i in ids]
    tag("minecraft/tags/block/mineable/pickaxe.json", u("luminite_block", "luminite_cluster", "starstone", "stormstone",
                                                       "charged_stormstone", "storm_crystal", "rime_crystal",
                                                       "petrified_wood", "ember_nylium"))
    tag("minecraft/tags/block/mineable/hoe.json", u("glowmoss", "amber_moss", "starmoss", "golden_leaves"))
    tag("minecraft/tags/block/mineable/shovel.json", u("sporeglow_soil"))
    tag("minecraft/tags/block/leaves.json", u("golden_leaves"))
    tag("minecraft/tags/item/leaves.json", u("golden_leaves"))
    # Glowing ground covers are deliberately NOT in #minecraft:dirt: vanilla moss patches replace every
    # #dirt block, which erased the light pools. Passive animals may still spawn on the mosses.
    tag("minecraft/tags/block/animals_spawnable_on.json", u("glowmoss", "amber_moss", "starmoss"))
    tag("minecraft/tags/block/stone_ore_replaceables.json", u("stormstone"))
    tag("minecraft/tags/block/base_stone_overworld.json", u("stormstone"))
    tag("minecraft/tags/block/mushroom_grow_block.json", u("sporeglow_soil"))
    tag("minecraft/tags/block/crystal_sound_blocks.json", u("starstone"))


def main():
    textures()
    lang = models()
    lang.update({
        "generator.underdeep.underdeep": "Underdeep",
    })
    names = {"rootwild": "Rootwild", "fungal_expanse": "Fungal Expanse", "flooded_depths": "Flooded Depths",
             "ashen_hollows": "Ashen Hollows", "crystal_deep": "Crystal Deep", "the_abyss": "The Abyss",
             "everfrost": "Everfrost", "golden_grove": "Golden Grove", "hollow_peaks": "Hollow Peaks",
             "emerald_wetlands": "Emerald Wetlands", "petrified_forest": "Petrified Forest",
             "crimson_basin": "Crimson Basin", "sunken_gardens": "Sunken Gardens",
             "stormstone_caverns": "Stormstone Caverns", "starlight_caverns": "Starlight Caverns"}
    for k, v in names.items():
        lang[f"biome.underdeep.{k}"] = v
    wjson(os.path.join(ASSETS, "lang", "en_us.json"), dict(sorted(lang.items())))
    tags()
    print(f"assets written for {len(BLOCKS)} blocks")


if __name__ == "__main__":
    main()
