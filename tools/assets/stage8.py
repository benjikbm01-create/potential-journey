#!/usr/bin/env python3
"""
Stage 8 content: equipment materials, processing, weapons, armor, tools, accessories, upgrades, exploration gear,
and underground alternatives for vanilla materials that normally come from the surface or the Nether.
Writes data/underdeep/equipment/content.json (loaded by the mod), textures, models, recipes, tags and names.
Called by stage2_assets.py after stage7.py, or alone:  PYTHONHASHSEED=0 python3 tools/assets/stage8.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "underdeep")
DATA = os.path.join(ROOT, "data")
NS = "underdeep"
LANG = {}


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
    recipe(name, {"type": "minecraft:crafting_shaped", "category": cat, "pattern": pattern,
                  "key": {k: ing(v) for k, v in keys.items()}, "result": {"id": u(out), "count": n}})


def smelt(name, src, out, xp=0.5, t=200, blast=True):
    recipe(name + "_smelting", {"type": "minecraft:smelting", "category": "misc", "ingredient": ing(src), "result": {"id": u(out)},
                                "experience": xp, "cookingtime": t})
    if blast:
        recipe(name + "_blasting", {"type": "minecraft:blasting", "category": "misc", "ingredient": ing(src), "result": {"id": u(out)},
                                    "experience": xp, "cookingtime": t // 2})


# ============================================================================== underground alternatives (8A)
# Nether portals are disabled in the Underdeep and there is no surface: these give the vanilla materials an
# underground route. Documented in docs/RESOURCES.md.
def alternatives():
    shapeless("blaze_rod_from_emberite", "minecraft:blaze_rod", 1, ["emberite", "emberite", "sulfur", "minecraft:stick"])
    shapeless("nether_wart_from_bloodcap", "minecraft:nether_wart", 2, ["bloodcap", "sulfur"])
    shapeless("name_tag_from_paper", "minecraft:name_tag", 1, ["minecraft:paper", "minecraft:string", "minecraft:iron_nugget"])
    shapeless("honey_bottle_from_honeyfruit", "minecraft:honey_bottle", 1, ["honeyfruit", "honeyfruit", "minecraft:glass_bottle"])


def main(extra=None):
    alternatives()
    print("stage 8: alternatives")
    return LANG


if __name__ == "__main__":
    main()
