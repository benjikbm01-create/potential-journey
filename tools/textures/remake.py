#!/usr/bin/env python3
"""Stage 11.5 texture remake: applies the approved art direction (texlib + the sample designs) to the mod's assets.

Runs as the last step of tools/assets/stage2_assets.py so regenerated placeholder art is always replaced; can also be
run on its own. Batches:
  1. stones, ores, mineral blocks, wood sets (+ the bundled "Underdeep Caverns" pack that restyles vanilla cave stone)
Each batch writes a contact sheet to build/texture_sheets/ for visual inspection.
"""
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
import sample as S  # noqa: E402
from texlib import (hx, ramp, shade, lerp, h01, new, put, get, rock, strata, ore, NODULE, GRAIN, SHARD, VEIN, BAND,  # noqa: E402
                    planks, log_side, log_top, leaves, sprite, outline, bevel_block, seeds, tdist)

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "underdeep", "textures")
PACK = os.path.join(ROOT, "src", "main", "resources", "resourcepacks", "underdeep_caverns")
SHEETS = os.path.join(ROOT, "build", "texture_sheets")
WRITTEN = {}   # batch -> list of (label, image)


def write(batch, rel, img, root=TEX):
    path = os.path.join(root, rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    WRITTEN.setdefault(batch, []).append((rel.split("/")[-1], img))


# ================================================================================ batch 1: stone, ores, minerals, wood

FOSSIL = [(0, 0, 3), (1, 0, 2), (2, 0, 2), (3, 0, 1), (3, 1, 0), (0, 1, 1), (-1, 1, 0), (4, -1, 2), (-1, -1, 2)]   # a small bone
CRUST = [(0, 0, 3), (1, 0, 2), (2, 0, 2), (0, 1, 2), (1, 1, 1), (3, 1, 1), (2, 1, 0), (-1, 1, 1), (1, 2, 0)]          # efflorescent crust


def cave_stones():
    """Underdeep's own stones and the vanilla cave stones restyled for the bundled pack."""
    write("stone", "block/stormstone", S.stormstone(False))
    write("stone", "block/charged_stormstone", S.stormstone(True))
    write("stone", "block/starstone", S.starstone())
    v = "assets/minecraft/textures/block/"
    cav = S.cavern_stone()
    write("stone", v + "stone", cav, PACK)
    write("stone", v + "andesite", rock(ramp(hx("858684"), 7, lo=0.6, hi=1.2), 101, n_cells=9, crack=0.2, min_d=3.4, spread=0.7), PACK)
    dio = rock(ramp(hx("bdbab4"), 7, lo=0.62, hi=1.15), 102, n_cells=8, crack=0.15, min_d=3.6, spread=0.5)
    for x, y in ((3, 3), (10, 5), (6, 11), (13, 12), (1, 8)):
        put(dio, x, y, hx("5a5856"))                       # hornblende flecks
    write("stone", v + "diorite", dio, PACK)
    gra = rock(ramp(hx("9a6c58"), 7, lo=0.62, hi=1.15), 103, n_cells=9, crack=0.2, min_d=3.2, spread=0.6)
    for x, y in ((2, 5), (9, 2), (12, 10), (5, 13)):
        put(gra, x, y, hx("e0c8b8")); put(gra, x + 1, y, hx("3a2a28"))   # feldspar + mica grains
    write("stone", v + "granite", gra, PACK)
    write("stone", v + "tuff", strata(ramp(hx("6e6a5c"), 7), 5, bands=5, wobble=1.0), PACK)
    calc = rock(ramp(hx("dcdad4"), 7, lo=0.72, hi=1.1), 104, n_cells=6, crack=0.3, min_d=4.4, spread=0.4)
    write("stone", v + "calcite", calc, PACK)
    deep = rock(ramp(hx("4b4b55"), 7), 41, n_cells=10, crack=0.6, stretch=(1.5, 0.8), min_d=2.8)
    write("stone", v + "deepslate", deep, PACK)
    dtop = rock(ramp(hx("4b4b55"), 7), 42, n_cells=7, crack=0.5, min_d=3.6)
    write("stone", v + "deepslate_top", dtop, PACK)
    write("stone", v + "cobblestone", rock(ramp(hx("7d7a76"), 7), 105, n_cells=11, crack=0.95, min_d=2.6, spread=0.9), PACK)
    write("stone", v + "cobbled_deepslate", rock(ramp(hx("4b4b55"), 7), 106, n_cells=11, crack=0.95, min_d=2.6, spread=0.9), PACK)
    bas = ramp(hx("4a484e"), 7)
    side = new()
    for y in range(16):
        for x in range(16):
            col = x // 4
            t = 3 + (1 if h01(107, col) > 0.5 else 0)
            if x % 4 == 0:
                t = 1
            if x % 4 == 1:
                t += 1
            if h01(108, col, y // 3) < 0.15 and x % 4 == 2:
                t = 2
            put(side, x, y, bas[t])
    write("stone", v + "basalt_side", side, PACK)
    top = rock(bas, 113, n_cells=5, crack=1.0, min_d=5.0, spread=0.5)   # column tops: polygonal joints
    write("stone", v + "basalt_top", top, PACK)
    write("stone", v + "smooth_basalt", rock(ramp(hx("48464c"), 7, lo=0.7, hi=1.1), 109, n_cells=5, crack=0.2, min_d=5, spread=0.4), PACK)
    bs = ramp(hx("2e2a30"), 7)
    blk = rock(bs, 110, n_cells=8, crack=0.5, min_d=3.2)
    for x, y in ((4, 6), (11, 3), (8, 12)):
        put(blk, x, y, hx("8a6a3a"))                       # gilded specks
    write("stone", v + "blackstone", blk, PACK)
    write("stone", v + "blackstone_top", rock(bs, 111, n_cells=7, crack=0.4, min_d=3.6), PACK)
    write("stone", v + "dripstone_block", strata(ramp(hx("8a6e5a"), 7), 112, bands=6, wobble=1.5, crack=0.2), PACK)


def ores_and_minerals():
    stone = S.cavern_stone()
    deep = rock(ramp(hx("4b4b55"), 7), 41, n_cells=10, crack=0.6, stretch=(1.5, 0.8), min_d=2.8)
    basalt = strata(ramp(hx("3c3a3e"), 7), 17, bands=6, wobble=0)
    pal = {
        "bog_iron": ramp(hx("a0582e"), 5), "cinnabar": ramp(hx("c2323a"), 5), "cryolite": ramp(hx("c8e6f4"), 5, hi=1.2),
        "emberite": ramp(hx("e8742a"), 5, hi=1.2), "fossil": ramp(hx("d8cca8"), 5, hi=1.15), "galvanite": ramp(hx("3fae9c"), 5),
        "jasper": ramp(hx("c25a3a"), 6), "malachite": ramp(hx("2f9a62"), 6), "nacre": ramp(hx("d8d0e8"), 5, hi=1.2),
        "niter": ramp(hx("e8e4dc"), 5, hi=1.15), "prismatic_opal": ramp(hx("9ad4e8"), 5, hi=1.25), "starsilver": ramp(hx("c8d8f0"), 5, hi=1.25),
        "sulfur": ramp(hx("e0c43a"), 5, hi=1.2), "umbralite": ramp(hx("7a4ab8"), 5, hi=1.3),
    }
    spec = {
        "bog_iron": (stone, [(3, 3, NODULE), (11, 5, NODULE), (6, 11, NODULE), (13, 13, GRAIN)]),
        "cinnabar": (stone, [(2, 2, GRAIN), (5, 4, GRAIN), (11, 3, GRAIN), (9, 9, GRAIN), (3, 12, GRAIN), (13, 12, GRAIN)]),
        "cryolite": (stone, [(4, 2, SHARD), (10, 7, SHARD), (3, 10, SHARD), (13, 12, GRAIN)]),
        "emberite": (basalt, [(2, 5, VEIN), (9, 11, VEIN), (12, 3, GRAIN)]),
        "fossil": (stone, [(2, 4, FOSSIL), (8, 11, FOSSIL), (11, 3, GRAIN)]),
        "galvanite": (stone, [(1, 4, VEIN), (8, 10, VEIN), (12, 3, GRAIN), (4, 13, GRAIN)]),
        "jasper": (stone, [(3, 3, BAND), (10, 10, BAND)]),
        "malachite": (stone, [(3, 4, BAND), (10, 9, BAND), (12, 2, GRAIN)]),
        "nacre": (stone, [(4, 4, NODULE), (11, 9, NODULE), (5, 12, GRAIN), (12, 3, GRAIN)]),
        "niter": (stone, [(2, 3, CRUST), (9, 10, CRUST), (11, 2, GRAIN)]),
        "prismatic_opal": (deep, [(4, 3, SHARD), (11, 8, SHARD), (5, 11, GRAIN)]),
        "starsilver": (deep, [(4, 2, SHARD), (11, 6, SHARD), (6, 10, SHARD)]),
        "sulfur": (basalt, [(3, 3, CRUST), (10, 9, CRUST), (12, 2, GRAIN), (2, 12, GRAIN)]),
        "umbralite": (deep, [(3, 3, SHARD), (10, 8, SHARD), (5, 12, GRAIN), (13, 13, GRAIN)]),
    }
    for name, (host, cl) in spec.items():
        img = ore(host, pal[name], cl)
        if name == "prismatic_opal":
            for x, y in ((4, 4), (11, 9)):
                put(img, x, y, hx("ff9ad5")); put(img, x, y + 1, hx("8ff0c8"))
        write("ore", f"block/{name}_ore", img)
    # storage / decorative mineral blocks
    S.T.clear()
    S.building()
    for tid in ("jasper_block", "malachite_block", "galvanite_block", "starsilver_block", "prismatic_opal_block"):
        write("mineral", "block/" + tid, S.T[tid][1])
    # cryolite: frosted ice bricks with rime edges
    c = ramp(hx("b8dcec"), 7, hi=1.2)
    img = new()
    for y in range(16):
        for x in range(16):
            row = y // 8
            bx = (x + (8 if row else 0)) % 16
            t = 4 if (x + y) % 7 else 5
            if y % 8 == 0:
                t = 6
            if y % 8 == 7 or bx == 15:
                t = 1
            if bx == 0:
                t = 6
            put(img, x, y, c[t])
    for x, y in ((3, 2), (4, 3), (11, 11), (12, 12), (9, 4)):
        put(img, x, y, (250, 254, 255))
    write("mineral", "block/cryolite_block", img)
    # luminite: a glowing crystal lattice set in a dark frame
    lu = ramp(hx("7ae8d8"), 7, hi=1.25)
    img = new()
    for y in range(16):
        for x in range(16):
            d = (x + y) % 6 == 0 or (x - y) % 6 == 0
            put(img, x, y, lu[6] if d else lu[3] if (x // 3 + y // 3) % 2 else lu[4])
    bevel_block(img, ramp(hx("2a3a40"), 4), light=3, dark=0)
    write("mineral", "block/luminite_block", img)
    # umbralite: dark violet faceted gems
    um = ramp(hx("5a3488"), 7, hi=1.35)
    img = new()
    for y in range(16):
        for x in range(16):
            tx, ty = x % 8, y % 8
            t = 4 if tx > ty else 2
            if tx + ty == 7:
                t = 6
            if tx == 0 or ty == 0:
                t = 5
            if tx == 7 or ty == 7:
                t = 0
            put(img, x, y, um[t])
    write("mineral", "block/umbralite_block", img)


WOODS = {
    "duskoak": dict(wood=hx("6e4a3a"), bark=hx("3e2c26"), leaf=hx("3f6a3c"), extra=None),
    "goldwood": dict(wood=hx("c89a52"), bark=hx("8a6236"), leaf=hx("d8a832"), extra="blossom"),
    "frostfir": dict(wood=hx("a8b4b8"), bark=hx("5a6068"), leaf=hx("4f7a78"), extra="rime"),
    "bloodthorn": dict(wood=hx("8a2e2e"), bark=hx("3e1a1e"), leaf=hx("8a2a3a"), extra="thorn"),
    "lumenwood": dict(wood=hx("6a7ab8"), bark=hx("2e3456"), leaf=hx("4a8ac0"), extra="glow"),
    "mirewillow": dict(wood=hx("7a7a52"), bark=hx("3e3e2a"), leaf=hx("5a7a3a"), extra="drip"),
    "spirebark": dict(wood=hx("9a7a5a"), bark=hx("5a4a3e"), leaf=hx("4a7a4a"), extra=None),
    "cinderbark": dict(wood=hx("5a4a4a"), bark=hx("2a2224"), leaf=hx("8a4a2a"), extra="ember"),
}


def wood_sets():
    for k, (name, w) in enumerate(WOODS.items()):
        wp, bp, lp = ramp(w["wood"], 6), ramp(w["bark"], 6), ramp(w["leaf"], 6, hue_shift=10)
        has = lambda suffix: os.path.exists(os.path.join(TEX, "block", f"{name}_{suffix}.png"))  # noqa: E731
        if has("planks"):
            write("wood", f"block/{name}_planks", planks(wp, 50 + k))
        bark = log_side(bp, 60 + k)
        if w["extra"] == "ember":
            for x, y in ((3, 4), (3, 5), (10, 11), (11, 11)):
                put(bark, x, y, hx("e8742a"))
        write("wood", f"block/{name}_log", bark)
        write("wood", f"block/{name}_log_top", log_top(wp, bp, 70 + k))
        if has("leaves") or name in ("cinderbark",):
            lv = leaves(lp, 80 + k)
            accents = {"blossom": (hx("ffe6a0"), hx("f2b648")), "rime": (hx("e8f4ff"), None), "thorn": (hx("e8d8c0"), None),
                       "glow": (hx("bff4ff"), hx("7ad8f0")), "drip": (hx("8aa860"), None), "ember": (hx("ffb04a"), hx("e8742a"))}.get(w["extra"])
            if accents:
                for x, y in ((3, 2), (10, 6), (6, 12), (13, 13)):
                    put(lv, x, y, accents[0])
                    if accents[1]:
                        put(lv, x + 1, y, accents[1])
            leaf_name = "cinder_leaves" if name == "cinderbark" else f"{name}_leaves"
            write("wood", f"block/{leaf_name}", lv)
        if has("planks"):
            stripped = log_side(wp, 90 + k, ridges=2)
            write("wood", f"block/stripped_{name}_log", stripped)
            write("wood", f"block/stripped_{name}_log_top", log_top(wp, wp, 95 + k))
            door_top, door_bottom, trap = doors(wp, bp, k)
            write("wood", f"block/{name}_door_top", door_top)
            write("wood", f"block/{name}_door_bottom", door_bottom)
            write("wood", f"block/{name}_trapdoor", trap)


def doors(wp, bp, seed):
    """Plank door with a frame, cross brace, iron hinges, and a small window in the top half; trapdoor with a grille."""
    iron = ramp(hx("6a6c74"), 5)
    top, bot, trap = new(), new(), new()
    for img, part in ((top, "top"), (bot, "bottom")):
        for y in range(16):
            for x in range(16):
                t = 3 if (x // 3) % 2 else 2
                if x in (0, 15) or (y == 0 and part == "top") or (y == 15 and part == "bottom"):
                    t = 1
                if x == 1 or (y == 1 and part == "top"):
                    t = 4
                put(img, x, y, wp[t])
        for x in (2, 13):
            for yy in (3, 12):
                put(img, x, yy, iron[3]); put(img, x + 1, yy, iron[1])
    for y in range(3, 9):
        for x in range(4, 12):
            top.putpixel((x, y), (0, 0, 0, 0) if (x not in (7, 8) and y != 6) else (*wp[1], 255))
    for i in range(12):
        put(bot, 2 + i, 13 - i, wp[4]); put(bot, 3 + i, 13 - i, wp[1])
    for y in range(16):
        for x in range(16):
            t = 3 if (y // 4) % 2 else 2
            if x in (0, 15) or y in (0, 15):
                t = 1
            put(trap, x, y, wp[t])
    for y in range(4, 12):
        for x in range(4, 12):
            if (x + y) % 3 == 0:
                trap.putpixel((x, y), (0, 0, 0, 0))
    return top, bot, trap


# ================================================================================ batch 2: plants

def plant_names():
    import glob
    import json
    names = set()
    for f in glob.glob(os.path.join(TEX, "..", "models", "block", "*.json")):
        d = json.load(open(f))
        par = d.get("parent", "")
        if any(k in par for k in ("cross", "crop")):
            for v in d.get("textures", {}).values():
                names.add(v.split("/")[-1])
    return sorted(n for n in names if os.path.exists(os.path.join(TEX, "block", n + ".png")))


def plants_batch():
    import plants
    names = plant_names()
    for n in names:            # sample identities from the originals before anything is overwritten
        plants.identity(n)
    for n in names:
        write("plant", "block/" + n, plants.paint(n))


# ================================================================================ batch 3: weapons, tools, armour

def equipment_batch():
    import equipment as E
    kinds = sorted(E.SIL, key=len, reverse=True)
    for f in sorted(os.listdir(os.path.join(TEX, "item"))):
        n = f[:-4]
        kind = next((k for k in kinds if n.endswith("_" + k)), None)
        if not kind:
            continue
        mat = n[:-len(kind) - 1]
        mat = {"steel_lumber": "steel"}.get(mat, mat)
        if mat not in E.MAT:
            continue        # unique/named weapons keep their own art (later batch)
        write("equipment", "item/" + n, E.weapon(kind, mat))
    for name in E.SETS:
        icons, _ = E.armour_icons(name, S.ARMOUR_ICONS)
        for piece, img in icons.items():
            if os.path.exists(os.path.join(TEX, "item", f"{name}_{piece}.png")):
                write("equipment", f"item/{name}_{piece}", img)

        def painter(style, m, t, st, ac):
            return S.armour_layers(style, m, t, st, ac)
        l1, l2 = E.armour_layers(name, painter)
        write("armour_layer", f"models/armor/{name}_layer_1", l1)
        write("armour_layer", f"models/armor/{name}_layer_2", l2)


# ================================================================================ batch 4: stations, furniture, mechanisms

def misc_blocks_batch():
    import blocks_misc
    done = {rel for b, items in WRITTEN.items() for rel, _ in items}
    S.T.clear()
    S.workstations()
    for tid in ("forge_front", "forge_top", "forge_side", "alloy_furnace_front", "ore_crusher_front"):
        write("misc", "block/" + tid, S.T[tid][1])
    done |= {"forge_front", "forge_top", "forge_side", "alloy_furnace_front", "ore_crusher_front"}
    for f in sorted(os.listdir(os.path.join(TEX, "block"))):
        n = f[:-4]
        if n in done or not f.endswith(".png"):
            continue
        img = blocks_misc.paint(n, sum(map(ord, n)))
        if img is not None:
            write("misc", "block/" + n, img)


# ================================================================================ contact sheets

def sheet(batch):
    items = WRITTEN.get(batch, [])
    if not items:
        return
    cols = 8
    S_ = 96
    rows = (len(items) + cols - 1) // cols
    img = Image.new("RGB", (cols * (S_ + 8), rows * (S_ + 22)), (34, 36, 42))
    d = ImageDraw.Draw(img)
    for k, (label, im) in enumerate(items):
        x, y = (k % cols) * (S_ + 8) + 4, (k // cols) * (S_ + 22) + 16
        e = im.resize((S_, S_) if im.size == (16, 16) else (S_, S_ * im.size[1] // im.size[0]), Image.NEAREST)
        d.rectangle([x, y, x + S_ - 1, y + S_ - 1], fill=(58, 60, 68))
        img.paste(e, (x, y), e)
        d.text((x, y - 13), label[:16], fill=(220, 220, 220))
    os.makedirs(SHEETS, exist_ok=True)
    img.save(os.path.join(SHEETS, f"{batch}.png"))


def pack_meta():
    os.makedirs(PACK, exist_ok=True)
    with open(os.path.join(PACK, "pack.mcmeta"), "w") as f:
        f.write('{\n "pack": {\n  "pack_format": 34,\n  "description": "Underdeep Caverns: cave stone textures in the Underdeep style"\n }\n}\n')


def main():
    pack_meta()
    cave_stones()
    ores_and_minerals()
    wood_sets()
    plants_batch()
    equipment_batch()
    misc_blocks_batch()
    for b in list(WRITTEN):
        sheet(b)
    print("texture remake: " + ", ".join(f"{b} {len(v)}" for b, v in WRITTEN.items()))


if __name__ == "__main__":
    main()
