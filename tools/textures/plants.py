"""Stage 11.5 plant painter: every plant/crop/fungus/crystal sprite drawn from a botanical archetype.

Each species keeps its identity colours (sampled once from its previous texture: the dominant saturated accent and its
foliage green), but the drawing is new: deliberate stems, leaves with lit tips and dark bases, petals with a darker rim
and a bright centre, caps with gill shadows and spots, crops that visibly grow through their stages.
"""
import colorsys
import math
import os

from PIL import Image

from texlib import hx, ramp, shade, h01, new, put

TEX = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "underdeep", "textures", "block")


# ------------------------------------------------------------------------------------------- identity colours

CACHE = os.path.join(os.path.dirname(__file__), "plant_identity.json")
_IDS = None


def identity(name):
    """(accent, foliage) of the species, sampled once from its original (pre-11.5) texture and cached in
    plant_identity.json so re-running never samples our own output."""
    global _IDS
    import json
    if _IDS is None:
        _IDS = json.load(open(CACHE)) if os.path.exists(CACHE) else {}
    if name in _IDS:
        a, f = _IDS[name]
        return (tuple(a) if a else None), (tuple(f) if f else None)
    r = _sample(name)
    _IDS[name] = [list(r[0]) if r[0] else None, list(r[1]) if r[1] else None]
    with open(CACHE, "w") as fh:
        json.dump(_IDS, fh, indent=0, sort_keys=True)
    return r


def _sample(name):
    p = os.path.join(TEX, name + ".png")
    acc, fol = None, None
    if os.path.exists(p):
        im = Image.open(p).convert("RGBA")
        greens, others = [], []
        for c in im.getdata():
            if c[3] < 128:
                continue
            h, s, v = colorsys.rgb_to_hsv(*(x / 255 for x in c[:3]))
            if s < 0.25 or v < 0.25:
                continue
            (greens if 0.18 < h < 0.48 else others).append(c[:3])
        if others:
            others.sort(key=lambda c: sum(c))
            acc = others[len(others) * 2 // 3]
        if greens:
            greens.sort(key=lambda c: sum(c))
            fol = greens[len(greens) // 2]
    return acc, fol


def ramps(name, accent_default=hx("c8a0e8"), foliage_default=hx("4a8a46")):
    acc, fol = identity(name)
    return ramp(acc or accent_default, 6, hi=1.3), ramp(fol or foliage_default, 6, hue_shift=10)


# ------------------------------------------------------------------------------------------- primitives

def line(img, pts, col):
    for (x, y) in pts:
        if 0 <= x < 16 and 0 <= y < 16:
            put(img, x, y, col)


def stem(img, g, x, y0, y1, sway=0.0, seed=0):
    pts = []
    for y in range(y1, y0 - 1, -1):
        xx = x + round(sway * math.sin((y1 - y) / 4 + seed))
        pts.append((xx, y))
        put(img, xx, y, g[2])
        if h01(seed, y) < 0.35:
            put(img, xx + 1, y, g[1])
    return pts


def leaf(img, g, x, y, d=1, size=3):
    """A leaf growing from (x, y) to the side d (+1 right / -1 left), tip lit."""
    for i in range(size):
        put(img, x + d * (i + 1), y - (i // 2), g[3] if i < size - 1 else g[5])
        if i < size - 1:
            put(img, x + d * (i + 1), y - (i // 2) + 1, g[1])


def tuft(img, g, seed, n=6, h=(6, 13), base=15):
    for k in range(n):
        x = 2 + int(h01(seed, k) * 12)
        ht = h[0] + int(h01(seed, k, 1) * (h[1] - h[0]))
        lean = (h01(seed, k, 2) - 0.5) * 0.5
        for i in range(ht):
            xx = round(x + lean * i)
            col = g[5] if i == ht - 1 else g[4] if i > ht * 0.6 else g[3] if i > ht * 0.3 else g[2]
            put(img, xx, base - i, col)


# ------------------------------------------------------------------------------------------- archetypes

def mushroom(name, a, g, style="dome", glow=False, cluster=True, seed=0):
    img = new()
    stalk = ramp(hx("ddd6c8"), 5)
    def cap(cx, top, w, h, small=False):
        for i in range(h):
            half = (w // 2) if style != "cone" else max(1, round((w // 2) * (i + 1) / h))
            if style == "dome":
                half = max(1, round((w / 2) * math.sqrt(max(0.0, 1 - ((h - 1 - i) / h) ** 2)) + 0.3))
            if style == "bell":
                half = max(1, round((w / 2) * (0.55 + 0.45 * i / max(1, h - 1))))
            for x in range(cx - half, cx + half + (0 if w % 2 == 0 else 1)):
                t = 4 if x < cx else 3
                if i == 0:
                    t = 5 if x < cx else 4
                if x in (cx - half, cx + half - (1 if w % 2 == 0 else 0)):
                    t = 1
                put(img, x, top + i, a[t])
        gill = top + h
        for x in range(cx - w // 2 + 1, cx + w // 2 - (1 if w % 2 == 0 else 0)):
            put(img, x, gill, a[0] if not glow else a[5])
        # spots
        if not small and style in ("dome", "flat"):
            for k in range(3):
                sx = cx - w // 2 + 2 + int(h01(seed, k) * (w - 3))
                sy = top + int(h01(seed, k, 1) * max(1, h - 1))
                put(img, sx, sy, hx("f2ecdc"))
        return gill
    def stalk_at(cx, y0, y1, w=2):
        for y in range(y0, y1 + 1):
            for dx in range(w):
                put(img, cx - w // 2 + dx, y, stalk[3] if dx == 0 else stalk[1])
    big_w = {"flat": 12, "dome": 10, "cone": 8, "bell": 8}[style]
    big_h = {"flat": 3, "dome": 5, "cone": 6, "bell": 6}[style]
    top = 3 if style != "flat" else 5
    gill = cap(7 if big_w % 2 == 0 else 7, top, big_w, big_h)
    stalk_at(7, gill + 1, 15)
    if cluster:
        gl = cap(13, 10, 4, 2, small=True)
        stalk_at(13, gl + 1, 15, 1)
        gl2 = cap(2, 11, 3, 2, small=True)
        stalk_at(2, gl2 + 1, 15, 1)
    for x in range(1, 15):
        if h01(seed, x, 9) < 0.5:
            put(img, x, 15, g[1])
    return img


HEADS = {
    "star": ["...3...", ".3.4.3.", "..343..", "34ccc43", "..232..", ".2.2.2.", "...2..."],
    "bell": ["..141..", ".34442.", ".34432.", "3444432", "34c4c32", "1.1.1.1"],
    "cup": ["3..3..3", "34.4.32", "3443432", "3444332", ".34332.", "..222.."],
    "trumpet": ["3.....3", ".34.43.", "..4c4..", "..343..", "..232..", "...2..."],
    "ball": [".2343.", "234443", "344c43", "234432", ".2332."],
}


def head_at(img, a, cx, cy, kind, centre, small=False):
    rows = HEADS[kind]
    if small:
        rows = [r[1:-1] for r in rows[1:-1]] if len(rows) > 4 else rows
    h, w = len(rows), len(rows[0])
    for j, r in enumerate(rows):
        for i, ch in enumerate(r):
            if ch == ".":
                continue
            col = centre if ch == "c" else a[{"1": 1, "2": 2, "3": 3, "4": 5}[ch]]
            put(img, cx - w // 2 + i, cy - h // 2 + j, col)


def flower(name, a, g, head="star", open_=True, seed=0):
    img = new()
    centre = hx("ffe08a") if a[3][2] > a[3][0] else hx("fff4d8")
    # main stem (2 px: lit left, shaded right), three leaves, a side stem with a second smaller bloom
    hy = 4
    pts = []
    for y in range(15, hy, -1):
        x = 7 + round(0.6 * math.sin((15 - y) / 4 + seed))
        pts.append((x, y))
        put(img, x, y, g[3]); put(img, x + 1, y, g[1])
    for (i, d, sz) in ((2, -1, 4), (5, 1, 4), (8, -1, 3)):
        lx, ly = pts[i]
        leaf(img, g, lx if d < 0 else lx + 1, ly, d, sz)
    sx = 3 if h01(seed, 3) > 0.5 else 12
    for y in range(15, 8, -1):
        put(img, sx + (1 if y < 11 and sx < 7 else -1 if y < 11 else 0), y, g[2])
    top = pts[-1]
    if not open_:
        for dx, dy, t in ((0, -2, 5), (0, -1, 4), (-1, 0, 3), (0, 0, 4), (1, 0, 2), (0, 1, 2)):
            put(img, top[0] + dx, top[1] - 1 + dy, a[t])
        put(img, top[0] - 1, top[1], g[3]); put(img, top[0] + 1, top[1], g[2])
        put(img, sx + (1 if sx < 7 else -1), 8, a[3]); put(img, sx + (1 if sx < 7 else -1), 7, a[4])
        return img
    head_at(img, a, top[0], top[1] - 2, head, centre)
    head_at(img, a, sx + (1 if sx < 7 else -1), 7, head, centre, small=True)
    return img


def grass(name, a, g, seed=0, tinted=None):
    img = new()
    tuft(img, g, seed, n=8, h=(5, 13))
    if tinted:   # mineral / ash / crystal grasses carry their accent at the tips
        for k in range(4):
            x = 2 + int(h01(seed, k) * 12)
            y = 3 + int(h01(seed, k, 5) * 6)
            put(img, x, y, a[4])
    return img


def fern(name, a, g, seed=0, ghost=False):
    img = new()
    for k, (bx, lean, ht) in enumerate(((7, 0.0, 13), (4, -0.45, 10), (10, 0.45, 10), (6, -0.2, 7), (9, 0.25, 7))):
        for i in range(ht):
            x = round(bx + lean * i)
            y = 15 - i
            put(img, x, y, g[2] if i < ht - 1 else g[4])
            if i > 1 and i % 2 == 0:
                put(img, x - 1, y, g[4] if i < ht - 2 else g[5])
                put(img, x + 1, y - 1, g[3])
    if ghost:
        for x in range(16):
            for y in range(16):
                c = img.getpixel((x, y))
                if c[3]:
                    l = sum(c[:3]) / 765
                    img.putpixel((x, y), (*shade(a[3], 0.6 + l), 255))
    return img


def crop(name, stage, a, g, seed=0, kind="leafy"):
    """Growth stages: 0 sprout, 1 young plant, 2 tall plant (budding), 3 mature with produce in the accent colour."""
    img = new()
    rows = (2, 5) if stage == 0 else (4, 8) if stage == 1 else (7, 12) if stage == 2 else (9, 14)
    for k, bx in enumerate((2, 6, 9, 13) if stage else (5, 10)):
        ht = rows[0] + int(h01(seed, k, stage) * (rows[1] - rows[0]))
        for i in range(ht):
            put(img, bx + (1 if i > ht * 0.6 and k == 2 else 0), 15 - i, g[2] if i < ht * 0.5 else g[3])
        for n_, i in enumerate(range(2 + k % 2, ht - 1, 3)):
            leaf(img, g, bx, 15 - i, -1 if (n_ + k) % 2 else 1, 2)
        if stage == 3:
            ty = 15 - ht
            if kind == "grain":
                for i in range(4):
                    put(img, bx, ty + i, a[4] if i % 2 else a[3]); put(img, bx + 1, ty + i + 1, a[2])
            elif kind == "root":
                for dx in (-1, 0, 1):
                    put(img, bx + dx, 15, a[3]); put(img, bx + dx, 14, a[4] if dx == 0 else a[2])
            else:
                for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                    put(img, bx + dx, ty + 3 + dy, a[4] if (dx, dy) == (0, 0) else a[2])
        elif stage == 2 and kind != "root":
            put(img, bx, 15 - ht, a[2])
    return img


def bush(name, stage, a, g, seed=0):
    img = new()
    r = (3, 5, 6)[stage]
    cx, cy = 7.5, 15 - r
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - cx) / 1.15, y - cy)
            if d <= r and y <= 15:
                t = 4 if (x < cx and y < cy) else 3 if d < r - 1 else 1
                if h01(seed, x // 2, y // 2) < 0.2:
                    t = 2
                put(img, x, y, g[t])
    for x in (6, 8, 9):
        put(img, x, 15, hx("5a4030"))
    if stage == 2:
        for k in range(6):
            x = int(cx - r + 2 + h01(seed, k) * (2 * r - 3))
            y = int(cy - r + 2 + h01(seed, k, 1) * (2 * r - 3))
            put(img, x, y, a[4]); put(img, x + 1, y, a[2]); put(img, x, y - 1, a[5])
    elif stage == 1:
        for k in range(3):
            put(img, int(cx - 3 + h01(seed, k) * 6), int(cy - 2 + h01(seed, k, 1) * 4), hx("f2ecdc"))
    return img


def sapling(name, g, seed=0):
    img = new()
    bark = ramp(hx("5a4030"), 4)
    for y in range(8, 16):
        put(img, 7, y, bark[2]); put(img, 8, y, bark[1])
    for dy in range(-4, 3):
        for dx in range(-4, 5):
            if dx * dx / 16 + dy * dy / 9 <= 1 and h01(seed, dx, dy) < 0.85:
                t = 4 if dx < 0 and dy < 0 else 3 if dx * dx + dy * dy < 6 else 2
                put(img, 7 + dx, 6 + dy, g[t])
    leaf(img, g, 7, 10, 1, 3)
    return img


def strands(name, a, g, seed=0, beads=False, hanging=False, column=False):
    img = new()
    for k, bx in enumerate((3, 7, 11, 13)):
        ph = h01(seed, k) * 6
        top = 0 if column or hanging else int(h01(seed, k, 1) * 6)
        for y in range(top, 16):
            x = round(bx + 1.2 * math.sin(y / 3 + ph))
            put(img, x, y, g[3] if (y + k) % 4 else g[4])
            if (y + k) % 5 == 0:
                put(img, x + 1, y, g[2])
            if beads and (y + k) % 6 == 2:
                put(img, x - 1, y, a[4]); put(img, x - 1, y + 1, a[2])
    return img


def crystals(name, a, seed=0, size=1.0):
    img = new()
    for k, (bx, h, w, lean) in enumerate(((7, 12, 3, 0), (3, 7, 2, -1), (11, 8, 2, 1), (13, 4, 1, 1), (1, 4, 1, -1))):
        h = max(2, int(h * size))
        for i in range(h):
            x0 = bx + round(lean * i / 4)
            ww = w if i < h - 2 else max(1, w - 1)
            for dx in range(ww):
                t = 5 if dx == 0 and i > 1 else 3 if dx < ww - 1 else 1
                if i == h - 1:
                    t = 5
                put(img, x0 + dx, 15 - i, a[t])
    return img


def coral(name, a, seed=0, fan=False):
    img = new()
    if fan:
        for y in range(4, 16):
            for x in range(16):
                d = math.hypot(x - 7.5, (y - 15) * 1.2)
                if 6 < d < 11 and abs(math.atan2(y - 15, x - 7.5)) > 0.4:
                    t = 4 if (int(d) % 2 == 0) else 2
                    if (x + y) % 3 == 0:
                        t = 1
                    put(img, x, y, a[t])
        for y in range(10, 16):
            put(img, 7, y, a[1])
        return img
    def branch(x, y, ln, ang, depth):
        for i in range(ln):
            xx, yy = round(x + math.sin(ang) * i), round(y - math.cos(ang) * i)
            put(img, xx, yy, a[3] if i < ln - 1 else a[5])
            put(img, xx + 1, yy, a[1])
        if depth:
            ex, ey = x + math.sin(ang) * ln, y - math.cos(ang) * ln
            branch(ex, ey, ln - 1, ang - 0.6, depth - 1)
            branch(ex, ey, ln - 1, ang + 0.5, depth - 1)
    branch(7, 15, 5, 0, 2)
    return img


def puffball(name, a, g, spent=False, seed=0):
    img = new()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, (y - 10.5) * 1.15)
            if d <= 5:
                t = 4 if (x < 7 and y < 10) else 3 if d < 4 else 1
                if spent:
                    t = max(0, t - 2)
                put(img, x, y, a[t])
    if spent:
        for x, y in ((7, 6), (8, 6), (7, 7)):
            put(img, x, y, a[0])
    else:
        for x, y in ((5, 8), (9, 9), (7, 12)):
            put(img, x, y, a[5])
    return img


def flytrap(name, a, g, closed=False, seed=0):
    img = new()
    stem(img, g, 7, 9, 15)
    leaf(img, g, 7, 13, -1, 3)
    leaf(img, g, 7, 12, 1, 3)
    if closed:
        for dx in range(-2, 3):
            for dy in range(3):
                put(img, 7 + dx, 5 + dy, a[3] if dy else a[4])
        for dx in range(-2, 3, 2):
            put(img, 7 + dx, 4, hx("f0ead8"))
    else:
        for i in range(4):
            put(img, 7 - 1 - i, 7 - i, a[4]); put(img, 7 - 1 - i, 8 - i, a[2])
            put(img, 8 + i, 7 - i, a[3]); put(img, 8 + i, 8 - i, a[1])
            if i % 2 == 0:
                put(img, 6 - i, 6 - i, hx("f0ead8")); put(img, 9 + i, 6 - i, hx("f0ead8"))
    return img


def reed(name, a, g, top=True, seed=0):
    img = new()
    for k, bx in enumerate((4, 8, 11)):
        for y in range(16 if not top else 4 + k * 2, 16):
            put(img, bx, y if not top else y, g[3] if (y + k) % 3 else g[2])
        if top:
            hy = 4 + k * 2
            for dy in range(4):
                put(img, bx, hy - dy, a[3] if dy < 3 else a[5]); put(img, bx + 1, hy - dy, a[1])
    return img


# ------------------------------------------------------------------------------------------- dispatch

def paint(name):
    seed = sum(map(ord, name))
    a, g = ramps(name)
    base = name.replace("_bloom", "") if name.endswith("_bloom") and not name.startswith("umbral_bloom_") else name
    if "_stage" in name:
        species, st = name.rsplit("_stage", 1)
        st = int(st)
        if "bush" in species:
            return bush(name, st, a, g, seed)
        maxst = 3 if os.path.exists(os.path.join(TEX, f"{species}_stage3.png")) else 2
        st3 = round(st * 3 / maxst)
        kind = "grain" if any(k in species for k in ("grain", "rice", "wort", "sunbeans")) else \
            "root" if any(k in species for k in ("truffle", "turnip", "roots", "chestnut")) else "leafy"
        return crop(name, st3, a, g, seed, kind)
    if name.endswith("_sapling"):
        _, lg = ramps(name.replace("_sapling", "_leaves")) if os.path.exists(os.path.join(TEX, name.replace("_sapling", "_leaves") + ".png")) else (a, g)
        return sapling(name, lg, seed)
    if name in ("sporepuff", "sporepuff_spent"):
        return puffball(name, a, g, name.endswith("spent"), seed)
    if name in ("snapjaw", "snapjaw_closed"):
        return flytrap(name, a, g, name.endswith("closed"), seed)
    if name in ("glowreed_top", "glowreed_bottom"):
        return reed(name, a, g, name.endswith("top"), seed)
    if any(k in name for k in ("crystal", "glimmer_bud", "glimmer_cluster", "luminite_cluster")):
        return crystals(name, a, seed, size=0.6 if "bud" in name else 1.0)
    if "coral" in name:
        return coral(name, a, seed, fan="fan" in name)
    if any(k in name for k in ("kelp", "seagrass", "ribbonweed")):
        return strands(name, a, g, seed, beads="glow" in name or "lumen" in name, column=name.endswith("_plant"))
    if any(k in name for k in ("ledgevine", "starstrand", "voidroot")):
        return strands(name, a, g, seed, beads=True, hanging=True, column=name.endswith("_plant"))
    if "cap" in name or name in ("glowgill", "stoneroot") or name.endswith("truffle"):
        style = "cone" if name in ("inkcap", "sagecap") else "bell" if name in ("lanterncap", "lanterncap_0", "lanterncap_fading", "deathbell") \
            else "flat" if name in ("tidecap", "calcicap") else "dome"
        return mushroom(name, a, g, style, glow="glow" in name or "lantern" in name, seed=seed)
    if "fern" in name:
        return fern(name, ramp(hx("b8d4e0"), 6, hi=1.25) if "ghost" in name else a, g, seed, ghost="ghost" in name)
    if "grass" in name:
        return grass(name, a, g, seed, tinted=name in ("ashgrass", "shardgrass", "static_grass", "silvergrass"))
    # flowers
    head = "bell" if "bell" in name else "trumpet" if "lily" in name else "cup" if name in ("tidebloom", "lakebloom", "bogbloom") \
        else "ball" if name in ("plaguebloom", "peakstar") else "star"
    is_bud_pair = os.path.exists(os.path.join(TEX, name + "_bloom.png"))
    return flower(name, a, g, head, open_=not is_bud_pair, seed=seed)
