#!/usr/bin/env python3
"""Stage 11.5 texture sample (for owner approval): renders the representative set with the new art direction.

Outputs (nothing in the mod's assets is changed):
  docs/previews/texture_sample/textures/<group>/<id>.png  the new 16x16 textures (and armor layers)
  docs/previews/texture_sample/sheet_native.png           every sample at true 1x scale (plus 2x)
  docs/previews/texture_sample/sheet_enlarged.png         every sample enlarged 8x with labels, old vs new
  docs/previews/texture_sample/walls.png                  stones / ores / building blocks tiled 6x4 (repetition check)
  docs/previews/texture_sample/diorama.png                a small cavern scene built from the sample blocks
"""
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from texlib import (hx, ramp, shade, lerp, h01, new, put, get, rock, strata, ore, NODULE, GRAIN, SHARD, VEIN, BAND,  # noqa: E402
                    planks, log_side, log_top, leaves, sprite, outline, bevel_block)

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
OUT = os.path.join(ROOT, "docs", "previews", "texture_sample")
OLD = os.path.join(ROOT, "src", "main", "resources", "assets", "underdeep", "textures")
T = {}       # id -> (group, image, old path or None, label)


def add(group, tid, img, old=None, label=None):
    T[tid] = (group, img, old, label or tid)


# ================================================================================ palettes (per region identity)
P = {
    "cavern": ramp(hx("7d7a76"), 7),          # warm grey limestone-granite (proposed Underdeep stone)
    "tuff": ramp(hx("6e6a5c"), 7),
    "storm": ramp(hx("4a4c66"), 7, hue_shift=8),
    "star": ramp(hx("9aa3b8"), 7, hue_shift=6),
    "deep": ramp(hx("4b4b55"), 7),
    "basalt": ramp(hx("3c3a3e"), 7),
    "rust": ramp(hx("a0582e"), 5),
    "cinnabar": ramp(hx("c2323a"), 5),
    "malachite": ramp(hx("2f9a62"), 6),
    "starsilver": ramp(hx("c8d8f0"), 5, hi=1.25),
    "ember": ramp(hx("e8742a"), 5, hi=1.2),
    "jasper": ramp(hx("a8463a"), 7),
    "galvanite": ramp(hx("3f9c8c"), 7),
    "opal": ramp(hx("a8c4d8"), 7),
}


# ================================================================================ natural stone
def stones():
    add("stone", "cavern_stone", cavern_stone(), "", "stone (proposed Underdeep look)")
    add("stone", "cavern_tuff", strata(P["tuff"], 5, bands=5, wobble=1.0), "", "tuff (proposed Underdeep look)")
    add("stone", "stormstone", stormstone(False), "block/stormstone")
    add("stone", "charged_stormstone", stormstone(True), "block/charged_stormstone")
    add("stone", "starstone", starstone(), "block/starstone")


def cavern_stone():
    """Natural cave rock for huge walls: broad, low-contrast slabs; a few hairline cracks; mineral flecks. Must not read as
    cobblestone when repeated across a cliff."""
    pal = ramp(hx("7d7a76"), 7, lo=0.62, hi=1.18)
    img = rock(pal, 11, n_cells=6, crack=0.28, min_d=4.6, spread=0.5)
    for x, y in ((4, 2), (12, 7), (7, 13), (1, 10)):
        put(img, x, y, pal[5])
    return img


def stormstone(charged):
    img = rock(P["storm"], 21, n_cells=10, crack=0.65, stretch=(1.6, 0.8), min_d=2.8)
    # slate cleavage: a few long dark diagonal partings; charged stone has conductive veins along them
    vein = ramp(hx("8fb4ff"), 5, hi=1.3)
    # slate cleavage: two short irregular partings; in charged stone they carry a bright conductive thread that
    # branches once, and only one segment glows hot (so a wall reads as scattered sparks, not stripes)
    paths = [[(2, 5), (3, 5), (4, 4), (5, 4), (6, 3), (7, 3)], [(9, 12), (10, 12), (11, 11), (12, 11), (12, 10), (13, 9)]]
    for k, path in enumerate(paths):
        for i, (x, y) in enumerate(path):
            put(img, x, y, (vein[1] if i % 3 else vein[2]) if charged else P["storm"][0])
        if charged:
            hx_, hy = path[2 + k]
            put(img, hx_, hy, vein[4])
            put(img, hx_, hy - 1, vein[3])
    return img


def starstone():
    img = rock(ramp(hx("8a93a8"), 7, lo=0.55, hi=1.15), 31, n_cells=7, crack=0.35, min_d=3.6, spread=0.5)
    # embedded star motes: two small glints of different size (a single bright texel with a faint halo)
    put(img, 4, 5, (238, 242, 255))
    for dx, dy in ((1, 0), (0, 1)):
        put(img, 4 + dx, 5 + dy, (168, 182, 220))
    put(img, 11, 12, (214, 222, 250))
    return img


# ================================================================================ ores
def ores():
    stone = cavern_stone()
    deep = rock(P["deep"], 41, n_cells=10, crack=0.6, min_d=2.8)
    basalt = strata(P["basalt"], 17, bands=6, wobble=0)
    add("ore", "bog_iron_ore", ore(stone, P["rust"], [(3, 3, NODULE), (11, 5, NODULE), (6, 11, NODULE), (13, 13, GRAIN)]), "block/bog_iron_ore")
    add("ore", "cinnabar_ore", ore(stone, P["cinnabar"], [(2, 2, GRAIN), (5, 4, GRAIN), (11, 3, GRAIN), (9, 9, GRAIN), (3, 12, GRAIN),
                                                         (13, 12, GRAIN)]), "block/cinnabar_ore")
    add("ore", "malachite_ore", ore(stone, P["malachite"], [(3, 4, BAND), (10, 9, BAND), (12, 2, GRAIN)]), "block/malachite_ore")
    add("ore", "starsilver_ore", ore(deep, P["starsilver"], [(4, 2, SHARD), (11, 6, SHARD), (6, 10, SHARD)]), "block/starsilver_ore")
    em = ore(basalt, P["ember"], [(2, 5, VEIN), (9, 11, VEIN), (12, 3, GRAIN)])
    add("ore", "emberite_ore", em, "block/emberite_ore")


# ================================================================================ building blocks (mineral storage / decor)
def building():
    # polished jasper bricks: 2 rows of offset bricks with banded stone inside each brick
    j = P["jasper"]
    img = new()
    for y in range(16):
        for x in range(16):
            row = y // 8
            bx = (x + (4 if row else 0)) % 16
            band = (y + (bx // 3)) % 5
            c = j[3] if band < 2 else j[4] if band < 4 else j[2]
            if y % 8 == 0:
                c = j[5]
            if y % 8 == 7:
                c = j[1]
            if bx % 8 == 0:
                c = j[0] if y % 8 else j[1]
            if bx % 8 == 1 and y % 8 not in (0, 7):
                c = j[5]
            put(img, x, y, c)
    add("building", "jasper_block", img, "block/jasper_block", "jasper_block (polished bricks)")
    # malachite: polished slab with concentric banding swirls
    m = P["malachite"]
    img = new()
    import math
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 4.5, y - 11.5) + 0.8 * math.sin(x * 0.7)
            r2 = math.hypot(x - 13.5, y - 2.5)
            v = min(r, r2 + 2)
            c = m[1 + int(v) % 4]
            put(img, x, y, c)
    bevel_block(img, m, light=5, dark=0)
    add("building", "malachite_block", img, "block/malachite_block", "malachite_block (polished swirl)")
    # galvanite: riveted conductive plate with a copper-teal patina and conductor channel
    g = P["galvanite"]
    img = new()
    for y in range(16):
        for x in range(16):
            c = g[3] if (x // 8 + y // 8) % 2 == 0 else g[4]
            put(img, x, y, c)
    for i in range(16):
        put(img, i, 7, g[1]); put(img, i, 8, g[5]); put(img, 7, i, g[1]); put(img, 8, i, g[5])
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13), (5, 5), (10, 10)):
        put(img, x, y, g[6]); put(img, x + 1, y + 1, g[0])
    bevel_block(img, g, light=6, dark=0)
    add("building", "galvanite_block", img, "block/galvanite_block", "galvanite_block (riveted plate)")
    # starsilver: stacked bevelled ingots
    s = ramp(hx("b8c6dc"), 7, hi=1.28)
    img = new()
    for y in range(16):
        for x in range(16):
            row = y // 4
            ox = 4 if row % 2 else 0
            bx = (x + ox) % 8
            ry = y % 4
            c = s[3]
            if ry == 0 or bx == 0:
                c = s[5]
            if ry == 3 or bx == 7:
                c = s[1]
            if ry == 1 and bx in (2, 3):
                c = s[6]
            put(img, x, y, c)
    add("building", "starsilver_block", img, "block/starsilver_block", "starsilver_block (ingot stack)")
    # prismatic opal: cut gem tiles with play-of-colour flecks
    o = P["opal"]
    flecks = [hx("ff9ad5"), hx("8ff0c8"), hx("9ab8ff"), hx("ffe28a")]
    img = new()
    for y in range(16):
        for x in range(16):
            tx, ty = x % 8, y % 8
            c = o[4] if tx + ty < 7 else o[3]
            if tx == 0 or ty == 0:
                c = o[6]
            if tx == 7 or ty == 7:
                c = o[1]
            if 1 < tx < 6 and 1 < ty < 6 and h01(7, x, y) < 0.18:
                c = flecks[int(h01(9, x, y) * 4)]
            put(img, x, y, c)
    add("building", "prismatic_opal_block", img, "block/prismatic_opal_block", "prismatic_opal_block (cut tiles)")


# ================================================================================ wood types
WOODS = {
    "duskoak": dict(wood=hx("6e4a3a"), bark=hx("3e2c26"), leaf=hx("3f6a3c")),
    "goldwood": dict(wood=hx("c89a52"), bark=hx("8a6236"), leaf=hx("d8a832")),
    "frostfir": dict(wood=hx("a8b4b8"), bark=hx("5a6068"), leaf=hx("4f7a78")),
}


def woods():
    for k, (name, w) in enumerate(WOODS.items()):
        wp, bp, lp = ramp(w["wood"], 6), ramp(w["bark"], 6), ramp(w["leaf"], 6, hue_shift=10)
        add("wood", f"{name}_planks", planks(wp, 50 + k), f"block/{name}_planks")
        add("wood", f"{name}_log", log_side(bp, 60 + k), f"block/{name}_log")
        add("wood", f"{name}_log_top", log_top(wp, bp, 70 + k), f"block/{name}_log_top")
        lv = leaves(lp, 80 + k)
        if name == "goldwood":  # golden grove: amber blossoms among the leaves
            for x, y in ((3, 2), (10, 6), (6, 12), (13, 13)):
                put(lv, x, y, hx("ffe6a0")); put(lv, x + 1, y, hx("f2b648"))
        if name == "frostfir":  # needles + rime on the tips
            for x, y in ((2, 3), (9, 1), (12, 9), (5, 13)):
                put(lv, x, y, hx("e8f4ff"))
        add("wood", f"{name}_leaves", lv, f"block/{name}_leaves")


# ================================================================================ plants (hand-drawn)
def plants():
    cap = {"o": hx("1e3a52"), "a": hx("2e7fa8"), "b": hx("4fc0e8"), "c": hx("a8f0ff"), "s": hx("d8e4dc"), "t": hx("9aa8a0"), "g": hx("2f5a3a")}
    add("plant", "glowcap", sprite([
        "................",
        "................",
        "......oooo......",
        "....oabbbbao....",
        "...obbccbbbbo...",
        "...abcbbbcbba...",
        "..oaabbbbbbaao..",
        "..ooaaaaaaaaoo..",
        "....ootsstoo....",
        "......tss.......",
        "......tsso..oo..",
        "..oo..tss..obao.",
        ".obao.tss..oaao.",
        ".oaao.tss...tt..",
        "..tt.gtssg..tt..",
        ".gggggggggggggg.",
    ], cap), "block/glowcap")
    fl = {"o": hx("3a2a52"), "p": hx("8a6ad8"), "q": hx("c2a8ff"), "w": hx("f4ecff"), "y": hx("ffd86a"), "s": hx("3f7a4a"), "S": hx("62a868"),
          "d": hx("2a4a30")}
    add("plant", "starbloom", sprite([
        "................",
        ".......w........",
        "......oqo.......",
        "....oqpwpqo.....",
        "...wqpyypqw.....",
        "....oqpwpqo.....",
        "......oqo....w..",
        ".......S....oqo.",
        "...o...S...qpyq.",
        "..oqo..S....oqo.",
        "..qyq..S.....S..",
        "..oqo..S....S...",
        "...S..SS...S....",
        "....S.Ss..S.....",
        ".....SSsSS......",
        "....dsddsd......",
    ], fl), "block/starbloom")
    fb = {"o": hx("2a3f5a"), "b": hx("7fb8e0"), "c": hx("c8e8ff"), "w": hx("ffffff"), "s": hx("5a7a6a"), "S": hx("8aa898"), "d": hx("34483e")}
    add("plant", "frostbell", sprite([
        "................",
        "......SSS.......",
        ".....S...S......",
        "....S.....S.....",
        "...obo...obo....",
        "..obccbo.obcbo..",
        "..bcwccb.bcwcb..",
        "..occcco.occco..",
        "...o.o....o.o...",
        "......S.........",
        "......S.....S...",
        "......SS...S....",
        ".......S..S.....",
        ".......SSS......",
        ".......sS.......",
        "......dsd.......",
    ], fb), "block/frostbell")
    gf = {"o": hx("26303a"), "a": hx("6a8a98"), "b": hx("a8c8d4"), "c": hx("e0f2f8"), "s": hx("4a5e66")}
    add("plant", "ghostfern", sprite([
        "................",
        "........c.......",
        ".......cb.......",
        "......cba..c....",
        "..c...ba..cb....",
        "..bc..a..cba....",
        "...ba.a.cba.....",
        "....bba.ba...c..",
        ".c...aaba...cb..",
        ".bc...aa...cba..",
        "..bba.sa..bba...",
        "....bbsabba.....",
        "......sa........",
        "......ss........",
        ".....osso.......",
        "....oosoo.......",
    ], gf), "block/ghostfern")
    em = {"o": hx("3a1a12"), "r": hx("b83a1e"), "e": hx("f07a2a"), "y": hx("ffd25a"), "w": hx("fff2c8"), "s": hx("4a3a2a"), "S": hx("6a5a40")}
    add("plant", "emberbloom", sprite([
        "................",
        "................",
        "......y.........",
        ".....yey..y.....",
        "....oewyeo.y....",
        "...orewweyro....",
        "...oreyyyero....",
        "....orreerro....",
        ".....oorro......",
        "......sS........",
        "..o...sS....o...",
        ".ore..sS...ore..",
        "..oS..sS..oSo...",
        "....SS.S.SS.....",
        ".....SSsSS......",
        "....osssso......",
    ], em), "block/emberbloom")


# ================================================================================ weapons (hand-drawn)
def weapons():
    es = {"o": hx("1c1418"), "d": hx("3a3440"), "m": hx("625a6a"), "l": hx("9a92a2"), "w": hx("d0cad6"), "e": hx("ff8a2a"), "E": hx("ffd06a"),
          "g": hx("6a1c1c"), "G": hx("b23a2a"), "h": hx("2e1e16"), "H": hx("5a3a26"), "p": hx("c88a3a")}
    add("weapon", "embersteel_sword", sprite([
        "..............oo",
        ".............owo",
        "............owlo",
        "...........owlmo",
        "..........owlEmo",
        ".........owleмo.".replace("м", "m"),
        "........owlemo..",
        ".......owlemo...",
        "..oo..owlemo....",
        "..oGooowmo......",
        "...oGGdmo.......",
        "....oGgo........",
        "...oHoGGo.......",
        "..oHho.oGo......",
        ".opho...oo......",
        ".ooo............",
    ], es), "item/embersteel_sword")
    rs = {"o": hx("16202e"), "d": hx("3a5a7a"), "m": hx("6a9ac0"), "l": hx("a8d4f0"), "w": hx("eaf8ff"), "b": hx("c8b088"), "s": hx("3a2a1e"),
          "S": hx("6a4e34"), "T": hx("9a7a52")}
    add("weapon", "rimesteel_spear", sprite([
        "............ooo.",
        "...........owwo.",
        "..........owlmo.",
        ".........owlmdo.",
        ".........olmdo..",
        "........obbdo...",
        ".......oTbbo....",
        "......oTSo......",
        ".....oTSo.......",
        "....oTSo........",
        "...oTSo.........",
        "..oTSo..........",
        ".oTSo...........",
        "oTSo............",
        "oSo.............",
        ".o..............",
    ], rs), "item/rimesteel_spear")
    sa = {"o": hx("15161a"), "d": hx("4a4e58"), "m": hx("7c8290"), "l": hx("b4bac6"), "w": hx("e8ecf2"), "s": hx("3a2818"), "S": hx("6a4a2c"),
          "T": hx("94704a"), "b": hx("8a5a2a")}
    add("weapon", "steel_battleaxe", sprite([
        "........ooo.....",
        ".......owlmo....",
        "......owlmmdo...",
        ".....owlmmddoo..",
        "......olmdoSTo..",
        ".......oddoTSo..",
        "........oobTo...",
        "......ooobSo....",
        ".....owlddoo....",
        "....owlmdo......",
        "...oTomdo.......",
        "..oTSoo.........",
        ".oTSo...........",
        "oTSo............",
        "oSo.............",
        ".o..............",
    ], sa), "item/steel_battleaxe")
    gd = {"o": hx("1a1410"), "c": hx("8a4a22"), "C": hx("c87a3a"), "k": hx("f0b070"), "e": hx("6ad8ff"), "E": hx("d8f8ff"), "h": hx("2a2a30"),
          "H": hx("50505a"), "g": hx("3a6a8a")}
    add("weapon", "galvanic_dagger", sprite([
        "................",
        "................",
        "................",
        "...........ooo..",
        "..........okEo..",
        ".........okCeo..",
        "........okCco...",
        ".......okCco....",
        "...oo.okeco.....",
        "...ogooCco......",
        "....oggco.......",
        ".....ogo........",
        "....oHoggo......",
        "...oHho.oo......",
        "..oego..........",
        "..ooo...........",
    ], gd), "item/galvanic_dagger")
    wh = {"o": hx("141416"), "d": hx("3e3a3a"), "m": hx("6e6a66"), "l": hx("a8a29a"), "w": hx("dcd6cc"), "s": hx("3a2818"), "S": hx("6a4a2c"),
          "T": hx("94704a"), "b": hx("8a7a3a"), "B": hx("c8b060")}
    add("weapon", "iron_warhammer", sprite([
        "........oooo....",
        ".......owwllo...",
        "......owllmmdo..",
        ".....owlmmmddo..",
        ".....olmmmddo...",
        "......odmddbo...",
        ".......oddoBo...",
        "........obBo....",
        ".......oTbo.....",
        "......oTSo......",
        ".....oTSo.......",
        "....oTSo........",
        "...oTSo.........",
        "..oBbo..........",
        ".oBbo...........",
        ".ooo............",
    ], wh), "item/iron_warhammer")


# ================================================================================ armour (3 complete sets: icons + layers)
SETS = {
    "fur": dict(main=hx("8a6a4a"), trim=hx("d8c8a8"), strap=hx("4a3220"), accent=hx("c84a3a")),
    "rimesteel": dict(main=hx("7aa4c8"), trim=hx("e4f4ff"), strap=hx("2a3a52"), accent=hx("4ad8f0")),
    "embersteel": dict(main=hx("4a4450"), trim=hx("c8862a"), strap=hx("221a1e"), accent=hx("ff7a24")),
}


def armour():
    for name, s in SETS.items():
        m, t, st, ac = ramp(s["main"], 6), ramp(s["trim"], 5), ramp(s["strap"], 4), ramp(s["accent"], 4)
        pal = {"o": shade(st[0], 0.7), "1": m[1], "2": m[2], "3": m[3], "4": m[4], "5": m[5], "t": t[2], "T": t[4], "s": st[1], "S": st[2],
               "a": ac[1], "A": ac[3]}
        icons = ARMOUR_ICONS[name]
        for piece, rows in icons.items():
            add("armour", f"{name}_{piece}", sprite(rows, pal), f"item/{name}_{piece}")
        l1, l2 = armour_layers(name, m, t, st, ac)
        add("armour_layer", f"{name}_layer_1", l1, f"models/armor/{name}_layer_1")
        add("armour_layer", f"{name}_layer_2", l2, f"models/armor/{name}_layer_2")


ARMOUR_ICONS = {
    "fur": {  # a hooded pelt with ears, a fur-collared jerkin, wrapped leggings, fur-topped boots
        "helmet": ["................", "...oo......oo...", "..oTTo....oTTo..", "..oT3oooooo3To..", "..o3344444433o..", ".o334455554433o.",
                   ".o34TTTTTTTT43o.", ".o3To......oT3o.", ".o3o........o3o.", ".o3o........o3o.", ".o2o........o2o.", "..oTo......oTo..",
                   "...o........o...", "................", "................", "................"],
        "chestplate": ["................", "..oTTo....oTTo..", ".oTttTooooTttTo.", ".oT44TTTTTT44To.", ".o344s3333s443o.", ".o334s3333s433o.",
                       "..o34s3443s43o..", "...o4s3443s4o...", "...o3sSSSSs3o...", "...o3s3443s3o...", "...o3s3333s3o...", "...o2s3333s2o...",
                       "...oTTTTTTTTo...", "...oooooooooo...", "................", "................"],
        "leggings": ["................", "...oooooooooo...", "...oSSSSSSSSo...", "...o34433443o...", "...o34o..o43o...", "...os4o..o4so...",
                     "...o3so..os3o...", "...os4o..o4so...", "...o3so..os3o...", "...os4o..o4so...", "...o3so..os3o...", "...oTTo..oTTo...",
                     "...oooo..oooo...", "................", "................", "................"],
        "boots": ["................", "................", "................", "................", "................", "................",
                  "...oTTo..oTTo...", "..oTttTooTttTo..", "..o4334oo4334o..", "..o3443oo3443o..", "..o34433o34433o.", "..os3333os3333o.",
                  "..ossssooossssso"[:16], "..oooooo.oooooo.", "................", "................"],
    },
    "rimesteel": {  # a winged frost helm with visor, layered plate cuirass with a frost-crystal heart, greaves, sabatons
        "helmet": ["................", "..T..........T..", "..tT..oooo..Tt..", "...tToT55ToTt...", "....o455544o....", "...o35555554o...",
                   "...o4oooooo4o...", "...o4aAaaAa4o...", "...o3oooooo3o...", "...o34o..o43o...", "....oo....oo....", "................",
                   "................", "................", "................", "................"],
        "chestplate": ["................", "..ooo......ooo..", ".oT5Toooooo T5To".replace(" ", "o")[:16], ".o455T5555T554o.", ".o34445555444 3o".replace(" ", "4")[:16],
                       "..o34445544433o.", "...o444aA4444o..", "...o344AA4443o..", "...oTTTTTTTTo...", "...o34444443o...", "...o3oooooo3o...",
                       "...o34444443o...", "...oTTTTTTTTo...", "...oooooooooo...", "................", "................"],
        "leggings": ["................", "...oooooooooo...", "...oTTTTTTTTo...", "...o45544554o...", "...o45o..o54o...", "...o34o..o43o...",
                     "...oTTo..oTTo...", "...o45o..o54o...", "...o34o..o43o...", "...o34o..o43o...", "...o23o..o32o...", "...oTTo..oTTo...",
                     "...oooo..oooo...", "................", "................", "................"],
        "boots": ["................", "................", "................", "................", "................", "................",
                  "...oTTo..oTTo...", "...o54o..o45o...", "...o43o..o34o...", "..o4334oo4334o..", "..o54433o44345o.", ".oT33333oT33333o",
                  ".ooooooo.ooooooo", "................", "................", "................"],
    },
    "embersteel": {  # a crested close helm with an ember slit, dark plate with glowing seams, flared greaves, spurred boots
        "helmet": ["................", ".......aA.......", "......oaAo......", ".....oTaATo.....", "....o445544o....", "...o34455443o...",
                   "...o34455443o...", "...oaAAAAAAao...", "...o3oooooo3o...", "...o34433443o...", "...o3o.TT.o3o...", "....o..oo..o....",
                   "................", "................", "................", "................"],
        "chestplate": ["................", "..oTTo....oTTo..", ".oT44Toooo T44To".replace(" ", "o")[:16], ".oT443444434 4To".replace(" ", "4")[:16],
                       ".o3443aAAa3443o.", "..o333aAAa333o..", "...o33aAAa33o...", "...o344aa443o...", "...oTTTTTTTTo...", "...o3a3443a3o...",
                       "...o3a3443a3o...", "...o2a3333a2o...", "...oTTTTTTTTo...", "...oooooooooo...", "................", "................"],
        "leggings": ["................", "...oooooooooo...", "...oTTaAAaTTo...", "...o34433443o...", "...o3ao..oa3o...", "...o34o..o43o...",
                     "...o3ao..oa3o...", "..oT34o..o43To..", "..oT3ao..oa3To..", "...o34o..o43o...", "...o23o..o32o...", "...oTTo..oTTo...",
                     "...oooo..oooo...", "................", "................", "................"],
        "boots": ["................", "................", "................", "................", "................", "................",
                  "...oTTo..oTTo...", "...o3ao..oa3o...", "...o34o..o43o...", "..o34433o34433o.", "..o3aaa3o3aaa3o.", ".oT3333ToT3333To",
                  ".ooooooooooooooo", ".aA........aA...", "................", "................"],
    },
}


def armour_layers(name, m, t, st, ac):
    """Vanilla armour layout (64x32). Each set paints the same regions with its own construction:
    fur = layered pelts with tufted edges and leather lacing; rimesteel = overlapping plates with frost trims and rivets;
    embersteel = dark plate with glowing ember seams and brass trims."""
    l1, l2 = Image.new("RGBA", (64, 32), (0, 0, 0, 0)), Image.new("RGBA", (64, 32), (0, 0, 0, 0))

    def box(img, u, v, w, h, d, painter):
        faces = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
                 "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}
        for f, (x0, y0, fw, fh) in faces.items():
            for j in range(fh):
                for i in range(fw):
                    c = painter(f, i, j, fw, fh)
                    if c is not None:
                        img.putpixel((x0 + i, y0 + j), (*c[:3], 255))

    def paint(kind):
        def p(face, i, j, fw, fh):
            edge_top, edge_bot = j == 0, j == fh - 1
            if name == "fur":
                c = m[3] if (i + j // 2) % 3 else m[2]
                if edge_top or edge_bot:
                    c = t[3] if (i % 2 == 0) else t[2]            # tufted fur trim
                if face in ("front", "back") and kind == "body" and i in (fw // 2 - 1, fw // 2) and j % 3 == 1:
                    c = st[2]                                    # lacing
                if face == "top" and kind == "head":
                    c = m[4] if (i + j) % 2 else m[3]
                return c
            if name == "rimesteel":
                c = m[4] if j % 4 else m[2]                      # overlapping plate rows
                if i == 0 or i == fw - 1:
                    c = m[2]
                if edge_top:
                    c = t[4]
                if edge_bot:
                    c = t[2]
                if j % 4 == 1 and i % 4 == 2:
                    c = t[4]                                     # rivets
                if kind == "body" and face == "front" and abs(i - fw / 2 + 0.5) < 1 and 2 <= j <= 5:
                    c = ac[3]                                    # frost crystal heart
                if kind == "head" and face == "front" and j == 4:
                    c = st[0] if 1 < i < fw - 2 else c           # visor slit
                return c
            # embersteel
            c = m[3] if (i // 2 + j // 3) % 2 else m[2]
            if edge_top or edge_bot:
                c = t[3]
            if (i == fw // 2 and face in ("front", "back")) or (j == fh // 2 and face in ("left", "right")):
                c = ac[3] if j % 2 else ac[2]                    # glowing seams
            if kind == "head" and face == "front" and j == 4 and 1 < i < fw - 2:
                c = ac[3]                                        # ember eye slit
            return c
        return p

    box(l1, 0, 0, 8, 8, 8, paint("head"))       # helmet
    box(l1, 16, 16, 8, 12, 4, paint("body"))     # chestplate body
    box(l1, 40, 16, 4, 12, 4, paint("arm"))      # arms
    box(l1, 0, 16, 4, 12, 4, paint("boot"))      # boots (legs region of layer 1)
    box(l2, 0, 16, 4, 12, 4, paint("leg"))       # leggings legs
    box(l2, 16, 16, 8, 12, 4, paint("waist"))    # leggings waist
    return l1, l2


# ================================================================================ workstations (3)
def workstations():
    brick = ramp(hx("8a5a4a"), 6)
    stone = P["cavern"]
    iron = ramp(hx("6a6c74"), 6)
    wood = ramp(hx("7a5236"), 6)
    fire = [hx("6a1a0a"), hx("c8401a"), hx("f08a2a"), hx("ffd06a"), hx("fff4c8")]

    def bricks(pal, seed=0):
        img = new()
        for y in range(16):
            for x in range(16):
                row = y // 4
                bx = (x + (4 if row % 2 else 0)) % 8
                c = pal[3] if h01(seed, row, (x + (4 if row % 2 else 0)) // 8) > 0.4 else pal[2]
                if y % 4 == 3 or bx == 7:
                    c = pal[0]
                if y % 4 == 0 and bx != 7:
                    c = pal[4]
                put(img, x, y, c)
        return img

    # FORGE: brick hearth, arched firebox with layered coals, iron lintel; top: coal bed in an iron grate; side: tool rack
    f = bricks(brick, 3)
    for y in range(6, 15):
        for x in range(3, 13):
            arch = (y > 7) or (4 <= x <= 11 and y == 7) or (5 <= x <= 10 and y == 6)
            if arch:
                put(f, x, y, fire[0])
    for x in range(3, 13):
        put(f, x, 5, iron[4]); put(f, x, 4, iron[2])
    for x, y, k in ((4, 13, 2), (5, 12, 3), (6, 13, 1), (7, 12, 2), (8, 11, 3), (9, 12, 2), (10, 13, 3), (11, 12, 1), (6, 11, 4), (9, 10, 2),
                    (7, 13, 2), (8, 13, 3), (5, 13, 1), (10, 12, 2)):
        put(f, x, y, fire[k])
    for x in range(2, 14):
        put(f, x, 14, iron[1]); put(f, x, 15, iron[0])
    add("station", "forge_front", f, "block/forge_front", "forge (front)")
    ft = new()
    for y in range(16):
        for x in range(16):
            c = iron[2] if (x in (0, 15) or y in (0, 15)) else (iron[1] if (x % 3 == 0 or y % 3 == 0) else fire[1 + int(h01(5, x, y) * 3)])
            put(ft, x, y, c)
    add("station", "forge_top", ft, "block/forge_top", "forge (top)")
    fs = bricks(brick, 4)
    for x, (h, col) in zip((3, 7, 11), ((7, iron), (6, iron), (8, wood))):
        for y in range(3, 3 + h):
            put(fs, x, y, col[3]); put(fs, x + 1, y, col[1])
        put(fs, x - 1, 3, col[4]); put(fs, x + 2, 3, col[4])
    for x in range(1, 15):
        put(fs, x, 2, wood[2]); put(fs, x, 1, wood[4])
    add("station", "forge_side", fs, "block/forge_side", "forge (side)")

    # ALLOY FURNACE: clay dome with a crucible window showing molten alloy and two vents
    clay = ramp(hx("a8745a"), 6)
    a = new()
    for y in range(16):
        for x in range(16):
            c = clay[3] if (x + y // 2) % 5 else clay[2]
            if y % 5 == 0:
                c = clay[4]
            put(a, x, y, c)
    for y in range(5, 12):
        for x in range(4, 12):
            put(a, x, y, iron[1] if x in (4, 11) or y in (5, 11) else (fire[2] if y > 8 else fire[0]))
    for x in range(5, 11):
        put(a, x, 9, fire[3]); put(a, x, 10, fire[2])
    put(a, 6, 9, fire[4]); put(a, 9, 9, fire[4])
    for x in range(3, 13):
        put(a, x, 4, iron[4]); put(a, x, 12, iron[2])
    for vx in (2, 12):
        for vy in (1, 2):
            put(a, vx, vy, clay[0]); put(a, vx + 1, vy, clay[0])
    bevel_block(a, clay, light=5, dark=0)
    add("station", "alloy_furnace_front", a, "block/alloy_furnace_front", "alloy furnace (front)")

    # ORE CRUSHER: timber frame, twin toothed iron rollers, chute of crushed ore at the bottom
    o = new()
    for y in range(16):
        for x in range(16):
            frame = x in (0, 1, 14, 15) or y in (0, 1)
            c = wood[3] if frame else stone[1]
            if frame and (x in (1, 14) or y == 1):
                c = wood[1]
            if frame and y == 0:
                c = wood[4]
            put(o, x, y, c)
    for cx in (5, 10):
        for y in range(3, 11):
            for x in range(cx - 2, cx + 2):
                tooth = (y + (0 if cx == 5 else 1)) % 2 == 0 and x in (cx - 2, cx + 1)
                put(o, x, y, iron[4] if tooth else (iron[3] if x < cx else iron[1]))
        put(o, cx - 1, 2, iron[5]); put(o, cx, 2, iron[2])
    for x in range(3, 13):
        put(o, x, 12, wood[2]); put(o, x, 15, wood[1])
    for x, y in ((4, 13), (6, 14), (7, 13), (9, 14), (10, 13), (12, 14), (5, 14), (11, 13)):
        put(o, x, y, P["rust"][3] if h01(x, y) > 0.5 else stone[4])
    add("station", "ore_crusher_front", o, "block/ore_crusher_front", "ore crusher (front)")


# ================================================================================ rendering
def old_image(path):
    if not path:
        return None
    p = os.path.join(OLD, path + ".png")
    return Image.open(p).convert("RGBA") if os.path.exists(p) else None


def save_textures():
    for tid, (group, img, old, label) in T.items():
        d = os.path.join(OUT, "textures", group)
        os.makedirs(d, exist_ok=True)
        img.save(os.path.join(d, tid + ".png"))


def sheet_enlarged():
    order = ["stone", "ore", "building", "wood", "plant", "weapon", "armour", "station"]
    S = 96
    cols = 6
    items = [(g, tid) for g in order for tid, v in T.items() if v[0] == g]
    rows = (len(items) + cols - 1) // cols
    W, H = cols * (S * 2 + 24), rows * (S + 30)
    img = Image.new("RGB", (W, H), (34, 36, 42))
    d = ImageDraw.Draw(img)
    for k, (g, tid) in enumerate(items):
        grp, im, old, label = T[tid]
        x, y = (k % cols) * (S * 2 + 24) + 6, (k // cols) * (S + 30) + 20
        d.text((x, y - 16), label[:34], fill=(220, 220, 220))
        ob = old_image(old)
        bg = (58, 60, 68)
        d.rectangle([x, y, x + S - 1, y + S - 1], fill=bg)
        d.rectangle([x + S + 4, y, x + 2 * S + 3, y + S - 1], fill=bg)
        if ob is not None and ob.size == (16, 16):
            e = ob.resize((S, S), Image.NEAREST)
            img.paste(e, (x, y), e)
        else:
            d.text((x + 8, y + S // 2 - 6), "old: n/a" if not old else "old: other", fill=(140, 140, 140))
        n = im.resize((S, S) if im.size == (16, 16) else (S * 2 if im.size[0] == 64 else S, S), Image.NEAREST)
        img.paste(n, (x + S + 4, y), n)
    d.text((6, 2), "LEFT: current texture   RIGHT: new sample (8x, nearest neighbour)", fill=(255, 230, 150))
    img.save(os.path.join(OUT, "sheet_enlarged.png"))


def sheet_native():
    items = [tid for tid, v in T.items() if v[1].size == (16, 16)]
    img = Image.new("RGB", (len(items) * 18 + 4, 60), (34, 36, 42))
    for k, tid in enumerate(items):
        im = T[tid][1]
        img.paste(im, (4 + k * 18, 4), im)
        im2 = im.resize((32, 32), Image.NEAREST)
    img2 = Image.new("RGB", (len(items) * 36 + 4, 40), (34, 36, 42))
    for k, tid in enumerate(items):
        im = T[tid][1].resize((32, 32), Image.NEAREST)
        img2.paste(im, (4 + k * 36, 4), im)
    out = Image.new("RGB", (max(img.width, img2.width), 24 + 40 + 8), (34, 36, 42))
    out.paste(img.crop((0, 0, img.width, 24)), (0, 0))
    out.paste(img2, (0, 26))
    out.save(os.path.join(OUT, "sheet_native.png"))


def walls():
    groups = [("stone", 6, 4), ("ore", 6, 4), ("building", 6, 4)]
    tiles = [tid for g, _, _ in groups for tid, v in T.items() if v[0] == g]
    tw = 6 * 16 * 2
    th = 4 * 16 * 2
    cols = 5
    rows = (len(tiles) + cols - 1) // cols
    img = Image.new("RGB", (cols * (tw + 10), rows * (th + 22)), (24, 24, 28))
    d = ImageDraw.Draw(img)
    for k, tid in enumerate(tiles):
        t = T[tid][1]
        x0, y0 = (k % cols) * (tw + 10), (k // cols) * (th + 22) + 16
        d.text((x0 + 2, y0 - 14), tid, fill=(220, 220, 220))
        for ty in range(4):
            for tx in range(6):
                img.paste(t.resize((32, 32), Image.NEAREST), (x0 + tx * 32, y0 + ty * 32))
    img.save(os.path.join(OUT, "walls.png"))


def diorama():
    """Isometric cavern scene: stone walls and floor with ore seams, a timber-and-jasper hut corner, plants, a forge."""
    import math
    S = 46  # projected block edge in pixels (about 3 px per texel)
    W, H = 1240, 900
    img = Image.new("RGB", (W, H), (20, 22, 28))
    d = ImageDraw.Draw(img)
    # simple painter's isometric: draw blocks back-to-front, top + left + right faces with face light
    blocks = {}
    for x in range(12):
        for z in range(10):
            blocks[(x, 0, z)] = "cavern_stone" if (x * 7 + z * 3) % 11 else "cavern_tuff"
    for z in range(10):
        for y in range(1, 6):
            blocks[(0, y, z)] = "cavern_stone" if (y * 5 + z) % 7 else ("bog_iron_ore" if z % 3 == 0 else "cavern_tuff")
    for x in range(1, 12):
        for y in range(1, 6):
            blocks[(x, y, 0)] = "stormstone" if x > 7 else ("cavern_stone" if (x + y) % 5 else "malachite_ore")
    blocks[(0, 3, 4)] = "cinnabar_ore"
    blocks[(9, 2, 0)] = "starsilver_ore"
    blocks[(10, 4, 0)] = "charged_stormstone"
    for x in range(6, 10):
        blocks[(x, 1, 5)] = "duskoak_planks"
        blocks[(x, 2, 5)] = "jasper_block" if x in (6, 9) else "duskoak_planks"
    for y in range(1, 4):
        blocks[(5, y, 5)] = "duskoak_log"
    blocks[(5, 4, 5)] = "duskoak_log"
    for x in range(5, 10):
        blocks[(x, 4, 5)] = "duskoak_leaves" if x != 5 else "duskoak_log"
    blocks[(3, 1, 7)] = "forge_front"
    blocks[(2, 1, 3)] = "starstone"
    plants_at = {(4, 1, 3): "glowcap", (7, 1, 7): "starbloom", (8, 1, 8): "frostbell", (2, 1, 6): "ghostfern", (10, 1, 6): "emberbloom",
                 (6, 1, 8): "glowcap"}

    def proj(x, y, z):
        return (W / 2 + (x - z) * S * 0.87, 300 + (x + z) * S * 0.5 - y * S)

    def face(tex, quad, light):
        t = tex.convert("RGBA")
        # draw each texel as a projected quad
        (ax, ay), (bx, by), (cx, cy), (dx, dy) = quad  # a=top-left, b=top-right, c=bottom-right, d=bottom-left
        for j in range(16):
            for i in range(16):
                p = t.getpixel((i, j))
                if p[3] == 0:
                    continue
                def at(u, v):
                    tx = ax + (bx - ax) * u + (dx - ax) * v
                    ty = ay + (by - ay) * u + (dy - ay) * v
                    return (tx, ty)
                poly = [at(i / 16, j / 16), at((i + 1) / 16, j / 16), at((i + 1) / 16, (j + 1) / 16), at(i / 16, (j + 1) / 16)]
                d.polygon(poly, fill=tuple(min(255, int(c * light)) for c in p[:3]))

    def top_tex(tid):
        return T.get(tid.replace("_log", "_log_top") if tid.endswith("_log") else ("forge_top" if tid == "forge_front" else tid), T[tid])[1]

    keys = sorted(set(blocks) | set(plants_at), key=lambda b: (b[0] + b[2], b[1]))
    for b in keys:
        x, y, z = b
        if b in plants_at:
            t = T[plants_at[b]][1]
            # a single billboard plane facing the viewer
            a = proj(x + 0.15, y + 1, z + 0.85)
            bb = proj(x + 0.85, y + 1, z + 0.15)
            c = proj(x + 0.85, y, z + 0.15)
            dd = proj(x + 0.15, y, z + 0.85)
            face(t, (a, bb, c, dd), 1.0)
            continue
        tid = blocks[b]
        side = T[tid][1]
        top = top_tex(tid)
        if (x, y + 1, z) not in blocks:
            face(top, (proj(x, y + 1, z), proj(x + 1, y + 1, z), proj(x + 1, y + 1, z + 1), proj(x, y + 1, z + 1)), 1.05)
        if (x, y, z + 1) not in blocks:   # south face (left-lower in view)
            face(side, (proj(x, y + 1, z + 1), proj(x + 1, y + 1, z + 1), proj(x + 1, y, z + 1), proj(x, y, z + 1)), 0.78)
        if (x + 1, y, z) not in blocks:   # east face (right-lower in view)
            face(side, (proj(x + 1, y + 1, z + 1), proj(x + 1, y + 1, z), proj(x + 1, y, z), proj(x + 1, y, z + 1)), 0.9)
    d.text((8, 6), "Sample diorama: proposed cavern stone + tuff, ores, stormstone, jasper/duskoak hut, plants, forge (isometric)",
           fill=(230, 230, 230))
    img.save(os.path.join(OUT, "diorama.png"))


def main():
    stones()
    ores()
    building()
    woods()
    plants()
    weapons()
    armour()
    workstations()
    save_textures()
    sheet_enlarged()
    sheet_native()
    walls()
    diorama()
    print(f"sample: {len(T)} textures -> {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
