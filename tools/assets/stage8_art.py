"""
Stage 8 pixel art: weapons and tools are drawn geometrically along the item diagonal (handle bottom-left, business end
top-right, like vanilla), so each family has its own silhouette; materials only change the palette. Armor layer sheets
follow the vanilla 64x32 UV layout. No noise fills: shading is directional (light upper-left, dark lower-right).
"""
import json
import math
import os
import random

import art

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "underdeep")
NS = "underdeep"
SEED = [8000]
WOOD = ((150, 108, 66), (112, 78, 46), (74, 50, 30))
GOLD = ((255, 226, 120), (220, 172, 60), (140, 100, 30))


def nseed():
    SEED[0] += 1
    return SEED[0]


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def save_item(iid, img):
    p = os.path.join(ASSETS, "textures", "item", iid + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def item_model(iid, handheld=False, tex=None):
    wjson(os.path.join(ASSETS, "models", "item", iid + ".json"),
          {"parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated", "textures": {"layer0": tex or f"{NS}:item/{iid}"}})


# ------------------------------------------------------------------------------------------- diagonal drawing
def P(t, k=0):
    """Point t along the diagonal (0 = bottom-left), offset k across it (+ = lower-right side)."""
    return (int(round(t)) + k, 15 - int(round(t)) + k)


def put(img, xy, c):
    x, y = xy
    if 0 <= x < 16 and 0 <= y < 16:
        art.px(img, x, y, c)


def handle(img, t0, t1, wood=WOOD, wrap=None):
    for t in range(t0, t1 + 1):
        x, y = P(t)
        c = wood[1] if not wrap or t % 2 else wrap
        put(img, (x, y), c)
        put(img, (x + 1, y), wood[2])
        put(img, (x, y - 1), wood[0])


def blade(img, t0, t1, col, width=2, tip=True):
    light, mid, dark = col
    for t in range(t0, t1 + 1):
        w = 1 if (tip and t == t1) else width
        x, y = P(t)
        put(img, (x, y), mid)
        if w >= 2:
            put(img, (x - 1, y), light)
            put(img, (x, y - 1), light)
        if w >= 3:
            put(img, (x + 1, y), dark)
            put(img, (x, y + 1), dark)
            put(img, (x - 1, y - 1), light)
        if w >= 2 and t == t1 - 1:
            put(img, (x + 1, y - 1), light)


def guard(img, t, half, col):
    for k in range(-half, half + 1):
        put(img, P(t, k), col[1] if k <= 0 else col[2])
    put(img, P(t, -half), col[0])


def region(img, t0, t1, k0, k1, col, light_k=None):
    """Solid fill of the area t0..t1 along the diagonal and k0..k1 across it (no raster gaps)."""
    for x in range(16):
        for y in range(16):
            t = (x - y + 15) / 2.0
            k = (x + y - 15) / 2.0
            if t0 - 0.5 <= t <= t1 + 0.5 and k0 - 0.5 <= k <= k1 + 0.5:
                c = col[1]
                if t >= t1 - 0.25 or (light_k is not None and abs(k - light_k) < 0.6):
                    c = col[0]
                elif t <= t0 + 0.25 or k >= k1 - 0.25:
                    c = col[2]
                put(img, (x, y), c)


def disc(img, cx, cy, r, col):
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                put(img, (x, y), col[0] if (x - cx) + (y - cy) < -r * 0.6 else col[2] if (x - cx) + (y - cy) > r * 0.6 else col[1])


def finish(img, col):
    return art.outline(img, art.shade(col[2], 0.55))


def weapon(family, col, material=""):
    img = art.new()
    wood = WOOD if material not in ("starsilver", "umbralite", "glimmer") else ((226, 210, 180), (196, 176, 140), (130, 110, 84))
    accent = GOLD if material in ("starsilver", "umbralite") else (art.shade(col[1], 0.8), art.shade(col[2], 0.9), art.shade(col[2], 0.6))
    if family == "sword":
        put(img, P(1), accent[1])
        handle(img, 2, 4, wood)
        guard(img, 5, 2, accent)
        blade(img, 6, 14, col, 2)
    elif family == "dagger":
        put(img, P(3), accent[1])
        handle(img, 4, 6, wood)
        guard(img, 7, 1, accent)
        blade(img, 8, 12, col, 2)
    elif family == "greatsword":
        put(img, P(0), accent[1])
        handle(img, 1, 4, wood, wrap=accent[2])
        guard(img, 5, 3, accent)
        blade(img, 6, 15, col, 3)
    elif family == "spear":
        handle(img, 0, 10, wood)
        put(img, P(10), col[2])
        for t, w in ((11, 2), (12, 3), (13, 3), (14, 2), (15, 1)):
            blade(img, t, t, col, w, tip=False)
    elif family == "battleaxe":
        handle(img, 0, 13, wood)
        region(img, 10, 12.5, 0.5, 3.5, col, light_k=3.5)
        region(img, 9, 10, 0.5, 2, col)
        region(img, 10.5, 11.5, -2, -0.5, col)
    elif family == "warhammer":
        handle(img, 0, 10, wood)
        region(img, 10.5, 13.5, -2.5, 2.5, col)
    elif family == "mace":
        handle(img, 0, 9, wood)
        cx, cy = P(12)
        disc(img, cx, cy, 2.3, col)
        for dx, dy in ((0, -3), (3, 0), (0, 3), (-3, 0)):
            put(img, (cx + dx, cy + dy), col[0])
    return finish(img, col)


def tool(kind, col):
    img = art.new()
    wood = WOOD
    if kind in ("pickaxe", "prospector"):
        handle(img, 0, 11, wood)
        for k in range(-5, 6):
            t = 12 - abs(k) // 2
            if kind == "prospector" and k > 2:
                continue
            put(img, P(t, k), col[0] if k < 0 else col[1])
            put(img, P(t - 1, k), col[2] if abs(k) > 1 else col[1])
        if kind == "prospector":
            put(img, P(12, 2), (250, 236, 150))
            put(img, P(12, 3), (250, 236, 150))
    elif kind == "axe":
        handle(img, 0, 12, wood)
        region(img, 9.5, 12, 0.5, 2.5, col, light_k=2.5)
    elif kind == "shovel":
        handle(img, 0, 10, wood)
        cx, cy = P(12.5)
        for x in range(16):
            for y in range(16):
                if abs(x - cx) + abs(y - cy) <= 2.5:
                    put(img, (x, y), col[0] if x + y < cx + cy else col[1])
    elif kind == "hoe":
        handle(img, 0, 12, wood)
        for k in range(1, 4):
            put(img, P(12, k), col[1])
            put(img, P(13, k - 1), col[0])
    elif kind == "hammer":
        handle(img, 0, 10, wood)
        region(img, 10.5, 14.5, -3, 3, col)
    elif kind == "excavator":
        handle(img, 0, 9, wood)
        cx, cy = P(12)
        for x in range(16):
            for y in range(16):
                if abs(x - cx) + abs(y - cy) <= 3.5:
                    put(img, (x, y), col[0] if x + y < cx + cy else col[1] if x + y == cx + cy else col[2])
    elif kind == "lumber_axe":
        handle(img, 0, 13, wood)
        region(img, 9.5, 12.5, 0.5, 3, col, light_k=3)
        region(img, 9.5, 12.5, -3, -0.5, col, light_k=-3)
    elif kind == "sickle":
        handle(img, 1, 5, wood)
        cx, cy = P(9)
        for a in range(180, 361, 8):
            r = 5
            x = int(round(cx + 1 + r * math.cos(math.radians(a))))
            y = int(round(cy + 1 + r * math.sin(math.radians(a))))
            put(img, (x, y), col[1])
            put(img, (x, y - 1), col[0])
    return finish(img, col)


def arrow(col):
    img = art.new()
    for t in range(1, 12):
        put(img, P(t), (130, 100, 70))
    for t, w in ((12, 2), (13, 2), (14, 1)):
        blade(img, t, t, (art.shade(col, 1.3), col, art.shade(col, 0.6)), w, tip=False)
    for t in (1, 2, 3):
        put(img, P(t, -1), (236, 236, 236))
        put(img, P(t, 1), (210, 210, 210))
    return art.outline(img, (40, 30, 30))


def bow_textures(bid, limb):
    light, mid, dark = art.shade(limb, 1.25), limb, art.shade(limb, 0.65)
    for state, pull in (("", 0), ("_pulling_0", 1), ("_pulling_1", 2), ("_pulling_2", 3)):
        img = art.new()
        pts = []
        for a in range(180, 271, 3):
            x = int(round(14 + 11 * math.cos(math.radians(a))))
            y = int(round(14 + 11 * math.sin(math.radians(a))))
            pts.append((x, y))
        for (x, y) in pts:
            put(img, (x, y), mid)
            put(img, (x + 1, y + 1), dark)
        put(img, pts[len(pts) // 2], light)
        a, b = (3, 14), (14, 3)
        mx, my = 8 + pull, 8 + pull
        art.line(img, a[0], a[1], mx, my, (230, 230, 220))
        art.line(img, mx, my, b[0], b[1], (230, 230, 220))
        if pull:
            for t in range(0, 6 + pull):
                put(img, (mx - t, my - t), (130, 100, 70))
            put(img, (mx - 6 - pull, my - 6 - pull), (200, 200, 210))
        save_item(bid + state, art.outline(img, art.shade(dark, 0.6)))


def bow_models(bid):
    base = {"parent": "minecraft:item/bow", "textures": {"layer0": f"{NS}:item/{bid}"}, "overrides": [
        {"predicate": {"pulling": 1}, "model": f"{NS}:item/{bid}_pulling_0"},
        {"predicate": {"pulling": 1, "pull": 0.65}, "model": f"{NS}:item/{bid}_pulling_1"},
        {"predicate": {"pulling": 1, "pull": 0.9}, "model": f"{NS}:item/{bid}_pulling_2"}]}
    wjson(os.path.join(ASSETS, "models", "item", bid + ".json"), base)
    for i in range(3):
        wjson(os.path.join(ASSETS, "models", "item", f"{bid}_pulling_{i}.json"),
              {"parent": f"{NS}:item/{bid}", "textures": {"layer0": f"{NS}:item/{bid}_pulling_{i}"}})


# ------------------------------------------------------------------------------------------- armor
HELMET = ["................", "................", "....########....", "...##########...", "..############..", "..####....####..",
          "..###......###..", "..##........##..", "................"]
CHEST = ["................", "..###......###..", ".#####....#####.", ".##############.", ".##############.", "..############..",
         "...##########...", "...##########...", "...##########...", "...##########...", "....########....", "................"]
LEGS = ["................", "...##########...", "...##########...", "...####..####...", "...####..####...", "...###....###...",
        "...###....###...", "...###....###...", "...###....###...", "...###....###...", "................"]
BOOTS = ["................", "................", "................", "................", "................", "...###....###...",
         "...###....###...", "..####....####..", ".#####....#####.", ".#####....#####.", "................"]


def armor_icon(piece, col, kind):
    mask = {"helmet": HELMET, "chestplate": CHEST, "leggings": LEGS, "boots": BOOTS}[piece]
    img = art.new()
    off = 2 if piece in ("helmet",) else 1
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            if ch == "#":
                c = col[1]
                if y == 1 + (1 if piece == "helmet" else 0) or x <= 3 and piece != "boots":
                    c = col[0]
                if x >= 12:
                    c = col[2]
                if kind in ("scale", "shell") and (x + y) % 3 == 0:
                    c = art.shade(c, 0.85)
                if kind in ("fur",) and y in (1, 2) and piece == "chestplate":
                    c = (236, 228, 210)
                put(img, (x, y + off), c)
    if kind == "nacre" and piece == "helmet":
        for x in range(6, 10):
            put(img, (x, 6), (120, 190, 220))
    if kind in ("steel", "embersteel", "rimesteel", "umbralite", "galvanic"):
        put(img, (4, 4 + off), art.shade(col[0], 1.1))
        put(img, (11, 4 + off), art.shade(col[0], 1.1))
    return art.outline(img, art.shade(col[2], 0.55))


def _face(img, x0, y0, w, h, col, kind, top=False):
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            c = col[1]
            if y == y0 or x == x0:
                c = col[0]
            if y == y0 + h - 1 or x == x0 + w - 1:
                c = col[2]
            if kind in ("scale",) and (x // 2 + y) % 3 == 0 and not top:
                c = art.shade(col[1], 0.82)
            if kind in ("shell",) and (y - y0) % 4 == 3:
                c = col[2]
            if kind in ("fur",) and (x + y * 3) % 5 == 0:
                c = art.shade(col[0], 1.05)
            if kind in ("silk",) and (x - x0) % 3 == 1 and not top:
                c = art.shade(col[1], 0.92)
            img.putpixel((x, y), art.rgba(c))


def _cube(img, u, v, w, h, d, col, kind):
    """Vanilla box UV: top/bottom at (u+d, v), sides below."""
    _face(img, u + d, v, w, d, col, kind, top=True)
    _face(img, u + d + w, v, w, d, col, kind, top=True)
    _face(img, u, v + d, d, h, col, kind)
    _face(img, u + d, v + d, w, h, col, kind)
    _face(img, u + d + w, v + d, d, h, col, kind)
    _face(img, u + d + w + d, v + d, w, h, col, kind)


def armor_layers(name, col, kind):
    from PIL import Image
    l1 = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    _cube(l1, 0, 0, 8, 8, 8, col, kind)        # head
    _cube(l1, 16, 16, 8, 12, 4, col, kind)     # body
    _cube(l1, 40, 16, 4, 12, 4, col, kind)     # arms
    _cube(l1, 0, 16, 4, 12, 4, col, kind)      # legs (boots)
    if kind == "nacre":
        for x in range(9, 15):
            for y in range(11, 14):
                l1.putpixel((x, y), art.rgba((120, 190, 220), 200))
    else:
        for x in range(10, 14):
            l1.putpixel((x, 12), art.rgba(col[2]))
    l2 = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    _cube(l2, 16, 16, 8, 12, 4, col, kind)
    _cube(l2, 0, 16, 4, 12, 4, col, kind)
    p = os.path.join(ASSETS, "textures", "models", "armor")
    os.makedirs(p, exist_ok=True)
    l1.save(os.path.join(p, f"{name}_layer_1.png"))
    l2.save(os.path.join(p, f"{name}_layer_2.png"))


# ------------------------------------------------------------------------------------------- accessories, kits, components
ACC_COLORS = {"ember": ((255, 160, 80), (210, 80, 30)), "rime": ((200, 230, 255), (150, 140, 230)), "galvanic": ((120, 180, 255), (60, 120, 220)),
              "pearl": ((230, 200, 90), (250, 246, 240)), "opal": ((230, 200, 90), (240, 220, 250)), "jasper": ((180, 180, 190), (190, 80, 60)),
              "malachite": ((210, 140, 90), (60, 170, 110)), "starsilver": ((230, 234, 250), (200, 210, 240)), "umbral": ((70, 60, 90), (190, 30, 50)),
              "copper": ((210, 130, 90), (70, 130, 220)), "horn": ((214, 196, 160), (150, 120, 90)), "spore": ((178, 146, 96), (170, 200, 120)),
              "heartstone": ((230, 200, 90), (220, 60, 80)), "nacre": ((200, 190, 180), (250, 246, 240)), "fossil": ((140, 90, 56), (236, 226, 200)),
              "amber": ((140, 90, 56), (250, 170, 40)), "warden": ((70, 60, 90), (190, 80, 60))}


def accessory(slot, aid):
    key = aid.split("_")[0]
    band, gem = ACC_COLORS.get(key, ((200, 180, 120), (180, 120, 200)))
    img = art.new()
    if slot == "ring":
        for a in range(0, 360, 10):
            x = int(round(8 + 4 * math.cos(math.radians(a))))
            y = int(round(9 + 3.5 * math.sin(math.radians(a))))
            put(img, (x, y), art.shade(band, 1.15 if y < 9 else 0.85))
        art.disc(img, 8, 5, 1.6, gem, art.shade(gem, 0.7))
        put(img, (7, 4), art.shade(gem, 1.4))
    elif slot == "amulet":
        art.line(img, 3, 2, 8, 9, (200, 190, 150))
        art.line(img, 13, 2, 8, 9, (170, 160, 120))
        art.disc(img, 8, 11, 2.6, gem, art.shade(gem, 0.7))
        put(img, (7, 10), art.shade(gem, 1.4))
        put(img, (8, 8), band)
    else:
        charm_icon(img, aid)
    return art.outline(img, (40, 34, 30))


def charm_icon(img, aid):
    if aid == "climbing_claws":
        for i, x in enumerate((5, 8, 11)):
            art.line(img, x, 4, x - 1, 10, (214, 196, 160))
            put(img, (x - 2, 11), (240, 240, 230))
        art.line(img, 3, 12, 12, 12, (140, 90, 56))
    elif aid == "feather_charm":
        art.line(img, 4, 13, 12, 3, (240, 240, 235))
        for t in range(3, 10):
            put(img, (4 + t, 12 - t), (200, 200, 210))
            put(img, (5 + t, 13 - t), (170, 190, 220))
    elif aid == "stride_charm":
        for y in range(5, 12):
            for x in range(5, 9):
                put(img, (x, y), (214, 206, 196))
        for x in range(5, 12):
            put(img, (x, 11), (170, 160, 150))
            put(img, (x, 12), (120, 110, 100))
    elif aid == "miners_lamp":
        for x in range(5, 11):
            for y in range(6, 11):
                put(img, (x, y), (190, 110, 70))
        art.disc(img, 8, 8, 1.6, (255, 240, 160))
        art.line(img, 3, 8, 5, 8, (140, 90, 56))
        art.line(img, 11, 8, 13, 8, (140, 90, 56))
    elif aid == "traders_token":
        art.disc(img, 8, 8, 4, (240, 200, 70), (180, 140, 40))
        art.disc(img, 8, 8, 1.5, (60, 180, 90))
    elif aid == "foragers_charm":
        art.disc(img, 7, 9, 2, (170, 50, 80))
        art.disc(img, 10, 8, 2, (200, 60, 90))
        art.line(img, 8, 4, 9, 6, (80, 140, 60))
    elif aid == "wayfinders_charm":
        art.disc(img, 8, 8, 4, (200, 190, 170), (150, 140, 120))
        art.line(img, 8, 5, 8, 8, (220, 50, 50))
        art.line(img, 8, 8, 8, 11, (240, 240, 240))
    elif aid == "anglers_charm":
        art.line(img, 9, 2, 9, 10, (190, 190, 200))
        art.line(img, 9, 10, 6, 12, (190, 190, 200))
        put(img, (5, 11), (190, 190, 200))
        art.disc(img, 9, 3, 1.3, (250, 246, 240))
    elif aid == "healers_charm":
        for i in range(-3, 4):
            put(img, (8 + i, 8), (90, 170, 80))
            put(img, (8, 8 + i), (90, 170, 80))
        put(img, (8, 8), (240, 240, 230))
    else:  # runners_charm and others: a winged token
        art.disc(img, 8, 9, 2.5, (164, 134, 102))
        art.line(img, 3, 6, 6, 8, (240, 240, 235))
        art.line(img, 13, 6, 10, 8, (240, 240, 235))


KIT_COLORS = {"venom": (120, 200, 80), "ember": (240, 120, 40), "frost": (160, 210, 250), "glimmer": (250, 236, 150), "opal": (240, 220, 250),
              "jasper": (190, 80, 60), "malachite": (60, 170, 110), "cryolite": (190, 180, 240), "emberite": (220, 100, 40), "galvanite": (70, 132, 214),
              "pearl": (250, 246, 240), "starsilver": (220, 226, 240), "amber": (250, 170, 40), "insulated": (164, 134, 102), "cooling": (170, 210, 240),
              "spore": (178, 146, 96), "grounding": (70, 132, 214), "sealed": (66, 150, 132)}


def kit(group, uid):
    c = KIT_COLORS.get(uid.split("_")[0], (170, 170, 180))
    img = art.new()
    if group == "coating":
        for y in range(6, 14):
            for x in range(5, 11):
                put(img, (x, y), c if y > 8 else (200, 215, 220))
        art.line(img, 7, 3, 8, 3, (140, 100, 60))
        art.line(img, 6, 4, 9, 4, (190, 210, 215))
    elif group == "gem":
        return art.gem(c, nseed())
    elif group in ("lining", "string"):
        art.disc(img, 8, 8, 4.5, c, art.shade(c, 0.7))
        art.disc(img, 8, 8, 1.5, art.shade(c, 0.6))
    elif group in ("plating", "head", "brace"):
        steel = (170, 176, 186) if uid not in ("malachite_bracing", "toughened_plating", "lightened_frame") else \
            {"malachite_bracing": (60, 170, 110), "toughened_plating": (70, 60, 90), "lightened_frame": (220, 226, 240)}[uid]
        for x in range(3, 13):
            for y in range(4, 12):
                put(img, (x, y), art.shade(steel, 1.15 if y == 4 else 0.85 if y == 11 else 1.0))
        for x, y in ((4, 5), (11, 5), (4, 10), (11, 10)):
            put(img, (x, y), art.shade(steel, 0.6))
    elif group == "edge":
        for t in range(2, 14):
            put(img, (t, 10 - t // 3), (150, 150, 160))
            put(img, (t, 11 - t // 3), (110, 110, 120))
        put(img, (12, 6), (255, 255, 255))
    elif group in ("grip", "limbs"):
        for t in range(2, 13):
            put(img, P(t), (140, 90, 56) if t % 2 else (200, 180, 150))
            put(img, P(t, 1), (90, 60, 36))
    else:
        art.disc(img, 8, 8, 4, (170, 170, 180))
    return art.outline(img, (40, 34, 30))


def component(kind, c):
    s = nseed()
    if kind == "ingot":
        return art.ingot(c, s)
    if kind == "gem":
        return art.gem(c, s)
    if kind == "gem_round":
        img = art.new()
        art.disc(img, 8, 8, 4, c, art.shade(c, 0.7))
        put(img, (6, 6), art.shade(c, 1.4))
        return art.outline(img, art.shade(c, 0.4))
    if kind == "lens":
        img = art.new()
        art.disc(img, 8, 8, 4.5, (200, 190, 140), (150, 140, 100))
        art.disc(img, 8, 8, 3.2, c, art.shade(c, 0.85))
        put(img, (6, 6), (255, 255, 255))
        return art.outline(img, (90, 80, 60))
    if kind == "pile":
        img = art.new()
        rnd = random.Random(s)
        for y in range(8, 14):
            w = y - 6
            for x in range(8 - w, 8 + w):
                put(img, (x, y), art.shade(c, 1.15 if (x + y) % 3 == 0 else 0.9 if (x * y) % 4 == 0 else 1.0))
        return art.outline(img, art.shade(c, 0.45))
    if kind == "cloth":
        img = art.new()
        for x in range(3, 13):
            for y in range(5, 12):
                put(img, (x, y), art.shade(c, 1.1 if (y - 5) % 3 == 0 else 0.95))
        for y in range(5, 12):
            put(img, (13, y + (1 if y % 2 else 0)), art.shade(c, 0.8))
        return art.outline(img, art.shade(c, 0.5))
    if kind == "plate":
        img = art.new()
        for x in range(3, 13):
            for y in range(4, 12):
                if (x - 8) ** 2 / 30 + (y - 8) ** 2 / 20 <= 1:
                    put(img, (x, y), art.shade(c, 1.15 if y < 7 else 0.85 if y > 9 else 1.0))
        art.line(img, 4, 8, 12, 8, art.shade(c, 0.75))
        return art.outline(img, art.shade(c, 0.45))
    if kind == "grip":
        img = art.new()
        for t in range(3, 12):
            put(img, P(t), c if t % 2 else art.shade(c, 0.7))
            put(img, P(t, 1), art.shade(c, 0.55))
        return art.outline(img, art.shade(c, 0.4))
    if kind == "strap":
        img = art.new()
        for t in range(1, 15):
            put(img, (t, 7 + (1 if t % 4 < 2 else 0)), c)
            put(img, (t, 8 + (1 if t % 4 < 2 else 0)), art.shade(c, 0.7))
        art.disc(img, 4, 8, 1.3, (200, 190, 150))
        return art.outline(img, art.shade(c, 0.45))
    if kind == "rope":
        img = art.new()
        for r in (4.5, 3, 1.5):
            for a in range(0, 360, 12):
                put(img, (int(round(8 + r * math.cos(math.radians(a)))), int(round(8 + r * math.sin(math.radians(a))))),
                    art.shade(c, 1.1 if a < 180 else 0.85))
        return art.outline(img, art.shade(c, 0.45))
    raise ValueError(kind)


def utility(kind):
    img = art.new()
    steel = (170, 176, 186)
    if kind == "grapple":
        art.line(img, 3, 13, 9, 7, (190, 160, 110))
        for (dx, dy) in ((0, -3), (3, 0), (2, -2)):
            art.line(img, 10, 6, 10 + dx, 6 + dy, steel)
        put(img, (10, 6), (110, 110, 120))
    elif kind == "glider":
        for x in range(1, 15):
            top = 6 + abs(x - 8) // 2
            for y in range(top, top + 3):
                put(img, (x, y), (214, 206, 196) if y == top else (186, 176, 166))
        art.line(img, 8, 4, 8, 11, (110, 110, 120))
    elif kind == "gauge":
        art.disc(img, 8, 8, 5, (200, 130, 90), (140, 80, 50))
        art.disc(img, 8, 8, 3.5, (230, 230, 220))
        art.line(img, 8, 8, 10, 5, (200, 40, 40))
        for a in range(200, 341, 35):
            put(img, (int(8 + 3 * math.cos(math.radians(a))), int(8 + 3 * math.sin(math.radians(a)))), (60, 60, 60))
    elif kind == "compass":
        art.disc(img, 8, 8, 5, (70, 132, 214), (34, 62, 122))
        art.disc(img, 8, 8, 3.5, (230, 230, 220))
        art.line(img, 8, 5, 8, 8, (250, 236, 150))
        art.line(img, 8, 8, 8, 11, (90, 90, 100))
    elif kind in ("pack", "pouch", "satchel"):
        c = {"pack": (140, 90, 56), "pouch": (110, 140, 70), "satchel": (120, 100, 90)}[kind]
        h0 = 4 if kind == "pack" else 6
        for x in range(4, 12):
            for y in range(h0, 14):
                put(img, (x, y), art.shade(c, 1.1 if x < 6 else 0.9 if x > 9 else 1.0))
        for x in range(4, 12):
            put(img, (x, h0 + 3), art.shade(c, 0.7))
        art.line(img, 6, h0 - 2, 9, h0 - 2, art.shade(c, 0.8))
        put(img, (8, h0 + 4), (200, 190, 150))
    elif kind == "kit":
        for x in range(3, 13):
            for y in range(6, 13):
                put(img, (x, y), (180, 60, 50) if y > 7 else (150, 50, 40))
        art.line(img, 6, 5, 9, 5, steel)
        for i in range(-1, 2):
            put(img, (8 + i, 10), (240, 240, 230))
            put(img, (8, 10 + i), (240, 240, 230))
    return art.outline(img, (40, 34, 30))


# ------------------------------------------------------------------------------------------- blocks
def tex(name, img):
    p = os.path.join(ASSETS, "textures", "block", name + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def framed(c, border):
    img = art.new()
    for x in range(16):
        for y in range(16):
            cc = c
            if x in (0, 15) or y in (0, 15):
                cc = border
            elif (x + y) % 7 == 0:
                cc = art.shade(c, 0.94)
            art.px(img, x, y, cc)
    return img


def el(frm, to, faces):
    return {"from": frm, "to": to, "faces": {k: {"texture": v} for k, v in faces.items()}}


def all6(t):
    return {f: t for f in ("north", "south", "east", "west", "up", "down")}


def model(name, elements, textures, ao=True):
    obj = {"parent": "block/block", "textures": textures, "elements": elements}
    if not ao:
        obj["ambientocclusion"] = False
    wjson(os.path.join(ASSETS, "models", "block", name + ".json"), obj)


def facing_state(bid):
    wjson(os.path.join(ASSETS, "blockstates", bid + ".json"), {"variants": {
        f"facing={f}": {"model": f"{NS}:block/{bid}", **({"y": r} if r else {})} for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})


def block_assets(bid, kind):
    T = lambda n: f"{NS}:block/{n}"  # noqa: E731
    stone = (118, 112, 108)
    brick = (150, 84, 62)
    steel = (150, 158, 168)
    wood = (150, 108, 66)
    if kind in ("crusher", "alloy", "forge"):
        body = {"crusher": stone, "alloy": brick, "forge": (90, 86, 92)}[kind]
        side = framed(body, art.shade(body, 0.7))
        tex(f"{bid}_side", side)
        front = side.copy()
        if kind == "crusher":
            for x in range(3, 13):
                for y in range(4, 12):
                    art.px(front, x, y, (60, 60, 64) if (x + y) % 2 else (90, 90, 96))
        else:
            for x in range(4, 12):
                for y in range(7, 14):
                    art.px(front, x, y, (40, 26, 20) if y < 11 else (240, 130, 40))
        tex(f"{bid}_front", front)
        top = framed(art.shade(body, 1.1), art.shade(body, 0.7))
        if kind == "forge":
            for x in range(4, 12):
                for y in range(4, 12):
                    art.px(top, x, y, (40, 30, 30) if (x + y) % 3 else (230, 110, 40))
        tex(f"{bid}_top", top)
        model(bid, [el([0, 0, 0], [16, 16, 16], {"north": "#front", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#side"})],
              {"front": T(f"{bid}_front"), "side": T(f"{bid}_side"), "top": T(f"{bid}_top"), "particle": T(f"{bid}_side")})
        facing_state(bid)
    elif kind in ("gems", "leather", "artisan"):
        top_c = {"gems": (150, 150, 160), "leather": (140, 90, 56), "artisan": steel}[kind]
        tex(f"{bid}_top", framed(top_c, art.shade(top_c, 0.7)))
        tex("workbench_leg", art.planks(art.shade(wood, 0.75), nseed()))
        detail = {"gems": (200, 200, 210), "leather": (210, 190, 150), "artisan": (230, 200, 90)}[kind]
        tex(f"{bid}_detail", framed(detail, art.shade(detail, 0.7)))
        els = [el([0, 12, 0], [16, 16, 16], {"north": "#leg", "south": "#leg", "east": "#leg", "west": "#leg", "up": "#top", "down": "#leg"})]
        els += [el([x, 0, z], [x + 2, 12, z + 2], all6("#leg")) for x in (1, 13) for z in (1, 13)]
        if kind == "gems":
            els.append(el([9, 16, 5], [14, 20, 11], all6("#detail")))
        elif kind == "leather":
            els.append(el([2, 16, 3], [10, 17, 12], all6("#detail")))
        else:
            els.append(el([4, 16, 5], [12, 19, 11], all6("#detail")))
        model(bid, els, {"top": T(f"{bid}_top"), "leg": T("workbench_leg"), "detail": T(f"{bid}_detail"), "particle": T(f"{bid}_top")})
        facing_state(bid)
    elif kind == "marker":
        tex("waymarker_post", framed(art.shade(wood, 0.8), art.shade(wood, 0.6)))
        tex("waymarker_flag", framed((214, 206, 196), (160, 150, 140)))
        tex("waymarker_glow", framed((255, 246, 180), (230, 210, 120)))
        model(bid, [el([7, 0, 7], [9, 16, 9], all6("#post")), el([9, 10, 7.5], [15, 15, 8.5], all6("#flag")),
                    el([6.5, 15, 6.5], [9.5, 16, 9.5], all6("#glow"))],
              {"post": T("waymarker_post"), "flag": T("waymarker_flag"), "glow": T("waymarker_glow"), "particle": T("waymarker_post")}, ao=False)
        wjson(os.path.join(ASSETS, "blockstates", bid + ".json"), {"variants": {"": {"model": f"{NS}:block/{bid}"}}})
    elif kind == "lantern":
        tex("glimmer_lantern_body", framed((255, 240, 170), (120, 110, 100)))
        tex("glimmer_lantern_cap", framed((110, 110, 120), (70, 70, 80)))
        model(bid, [el([5, 0, 5], [11, 7, 11], all6("#body")), el([6, 7, 6], [10, 9, 10], all6("#cap"))],
              {"body": T("glimmer_lantern_body"), "cap": T("glimmer_lantern_cap"), "particle": T("glimmer_lantern_body")}, ao=False)
        wjson(os.path.join(ASSETS, "blockstates", bid + ".json"), {"variants": {"": {"model": f"{NS}:block/{bid}"}}})
    elif kind == "ladder":
        img = art.new()
        for y in range(16):
            for x in (2, 3, 12, 13):
                art.px(img, x, y, (190, 160, 110) if y % 3 else (150, 120, 80))
        for y in (2, 7, 12):
            for x in range(3, 13):
                art.px(img, x, y, (170, 140, 95))
        tex("rope_ladder", img)
        wjson(os.path.join(ASSETS, "models", "block", bid + ".json"), {"parent": "minecraft:block/ladder",
                                                                         "textures": {"particle": T("rope_ladder"), "texture": T("rope_ladder")}})
        facing_state(bid)
    if kind != "ladder":
        wjson(os.path.join(ASSETS, "models", "item", bid + ".json"), {"parent": f"{NS}:block/{bid}"})
    else:
        item_model(bid, tex=f"{NS}:block/rope_ladder")
