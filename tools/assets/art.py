"""
Procedural Minecraft-style pixel art for Underdeep (16x16, plus sign sheets).

Every function takes a deterministic seed so re-running the generator reproduces identical PNGs.
Colours are RGB tuples; shade(c, f) scales brightness. The goal is readable silhouettes and a
coherent palette per species, in the spirit of vanilla textures, while being entirely original.
"""
import math
import random

from PIL import Image

T = (0, 0, 0, 0)


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, f):
    return tuple(clamp(x * f) for x in c[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


def rgba(c, a=255):
    return tuple(c[:3]) + (a,)


def new(w=16, h=16, fill=T):
    return Image.new("RGBA", (w, h), fill)


def px(img, x, y, c, a=255):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((int(x), int(y)), rgba(c, a))


def get(img, x, y):
    if 0 <= x < img.width and 0 <= y < img.height:
        return img.getpixel((int(x), int(y)))
    return T


def outline(img, c, alpha_min=1):
    """Darken edge pixels of opaque regions bordering transparency (gives vanilla-like crisp sprites)."""
    w, h = img.size
    src = img.copy()
    for x in range(w):
        for y in range(h):
            p = src.getpixel((x, y))
            if p[3] < alpha_min:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = src.getpixel((x + dx, y + dy)) if 0 <= x + dx < w and 0 <= y + dy < h else T
                if q[3] == 0:
                    img.putpixel((x, y), rgba(mix(p, c, 0.45), p[3]))
                    break
    return img


# ---------------------------------------------------------------------------- surfaces

def noise_fill(img, base, var, rnd, x0=0, y0=0, w=16, h=16):
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            f = 1 + rnd.uniform(-var, var)
            px(img, x, y, shade(base, f))


def stone(base, seed, var=0.08, specks=None, n=0):
    rnd = random.Random(seed)
    img = new()
    noise_fill(img, base, var, rnd)
    # soft blotches
    for _ in range(7):
        cx, cy, r = rnd.randrange(16), rnd.randrange(16), rnd.uniform(1.2, 2.6)
        f = rnd.choice([0.88, 0.93, 1.07])
        for x in range(16):
            for y in range(16):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    p = img.getpixel((x, y))
                    img.putpixel((x, y), rgba(shade(p, f)))
    for _ in range(n):
        c = rnd.choice(specks)
        px(img, rnd.randrange(16), rnd.randrange(16), c)
    return img


def ore(host, mineral, seed, style="cluster", glow=None):
    """Host rock with mineral deposits. Styles: cluster, crystals, veins, flecks, bands, bones, stars."""
    rnd = random.Random(seed)
    img = host.copy()
    dark, light = shade(mineral, 0.65), shade(mineral, 1.3)
    if style == "cluster":
        for _ in range(4 + rnd.randrange(2)):
            cx, cy = rnd.randrange(2, 14), rnd.randrange(2, 14)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1)):
                if rnd.random() < 0.8:
                    px(img, cx + dx, cy + dy, mineral)
            px(img, cx, cy, light)
            px(img, cx + 1, cy + 1, dark)
    elif style == "crystals":
        for _ in range(5):
            cx, cy = rnd.randrange(2, 14), rnd.randrange(3, 15)
            hgt = rnd.randrange(2, 4)
            for i in range(hgt):
                px(img, cx, cy - i, light if i == hgt - 1 else mineral)
            px(img, cx + 1, cy, dark)
    elif style == "veins":
        for _ in range(3):
            x, y = rnd.randrange(16), rnd.randrange(16)
            for _ in range(9):
                px(img, x, y, mineral)
                if rnd.random() < 0.3:
                    px(img, x, y - 1, light)
                x += rnd.choice([-1, 0, 1])
                y += rnd.choice([-1, 1])
    elif style == "flecks":
        for _ in range(26):
            px(img, rnd.randrange(16), rnd.randrange(16), rnd.choice([mineral, light, dark]))
    elif style == "bands":
        off = rnd.randrange(16)
        for x in range(16):
            for y in range(16):
                v = math.sin((x + y * 0.6 + off) * 0.9)
                if v > 0.55 and rnd.random() < 0.9:
                    px(img, x, y, light if v > 0.85 else mineral)
                elif v < -0.75 and rnd.random() < 0.4:
                    px(img, x, y, dark)
    elif style == "bones":
        for _ in range(3):
            x, y = rnd.randrange(2, 12), rnd.randrange(2, 13)
            horiz = rnd.random() < 0.5
            for i in range(4):
                px(img, x + (i if horiz else 0), y + (0 if horiz else i), mineral)
            for e in (0, 3):
                ex, ey = (x + e, y) if horiz else (x, y + e)
                px(img, ex + (0 if horiz else 1), ey + (1 if horiz else 0), light)
    elif style == "stars":
        for _ in range(5):
            cx, cy = rnd.randrange(2, 14), rnd.randrange(2, 14)
            px(img, cx, cy, light)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                px(img, cx + dx, cy + dy, mineral)
    if glow:
        for _ in range(6):
            px(img, rnd.randrange(16), rnd.randrange(16), glow)
    return img


def gem_block(base, seed, style="facets"):
    rnd = random.Random(seed)
    img = new()
    light, dark = shade(base, 1.25), shade(base, 0.7)
    for x in range(16):
        for y in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if style == "facets":
                v = ((x // 4) + (y // 4)) % 2
                c = shade(base, 1.08 if v else 0.94)
                if (x % 4 == 0) or (y % 4 == 0):
                    c = shade(base, 0.85)
            elif style == "metal":
                c = shade(base, 1.0 + 0.12 * math.sin(y * 0.8) - 0.06 * (x % 5 == 0))
            else:
                c = shade(base, 1 + rnd.uniform(-0.06, 0.06))
            px(img, x, y, dark if edge else c)
    for _ in range(8):
        px(img, rnd.randrange(1, 15), rnd.randrange(1, 15), light)
    px(img, 1, 1, shade(base, 1.4))
    px(img, 2, 1, light)
    return img


# ---------------------------------------------------------------------------- wood

def bark(base, seed, groove=0.62, style="furrow", accent=None):
    rnd = random.Random(seed)
    img = new()
    for x in range(16):
        col_f = 1 + rnd.uniform(-0.07, 0.07)
        for y in range(16):
            px(img, x, y, shade(base, col_f + rnd.uniform(-0.04, 0.04)))
    if style == "furrow":
        for x in rnd.sample(range(16), 6):
            y = rnd.randrange(16)
            ln = rnd.randrange(5, 14)
            for i in range(ln):
                px(img, x, (y + i) % 16, shade(base, groove))
                if rnd.random() < 0.25:
                    px(img, x + 1, (y + i) % 16, shade(base, groove + 0.15))
    elif style == "lenticel":  # smooth bark with short horizontal marks (pale trees)
        for _ in range(11):
            x, y = rnd.randrange(16), rnd.randrange(16)
            for i in range(rnd.randrange(2, 4)):
                px(img, x + i, y, shade(base, groove))
    elif style == "scales":  # plated conifer bark
        for y in range(0, 16, 3):
            off = rnd.randrange(4)
            for x in range(16):
                if (x + off) % 4 == 0:
                    px(img, x, y, shade(base, groove))
                    px(img, x, y + 1, shade(base, groove + 0.12))
            for x in range(16):
                if rnd.random() < 0.5:
                    px(img, x, y + 2, shade(base, groove + 0.2))
    elif style == "twisted":  # diagonal ridges
        for k in range(-16, 16, 4):
            for x in range(16):
                y = x + k + rnd.choice([0, 0, 1])
                px(img, x, y % 16, shade(base, groove))
    elif style == "charred":
        for _ in range(18):
            x, y = rnd.randrange(16), rnd.randrange(16)
            px(img, x, y, shade(base, 0.6))
        for _ in range(4):
            x, y = rnd.randrange(16), rnd.randrange(16)
            for i in range(rnd.randrange(2, 5)):
                px(img, x, (y + i) % 16, accent or (255, 120, 40))
                x += rnd.choice([0, 0, 1, -1])
    if accent and style != "charred":
        for _ in range(7):
            px(img, rnd.randrange(16), rnd.randrange(16), accent)
    return img


def log_top(bark_c, wood_c, seed, rings=True):
    rnd = random.Random(seed)
    img = new()
    for x in range(16):
        for y in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge and bark_c is not None:
                px(img, x, y, shade(bark_c, 0.9 + rnd.uniform(-0.08, 0.08)))
                continue
            r = max(abs(x - 7.5), abs(y - 7.5)) + rnd.uniform(-0.3, 0.3)
            band = int(r) % 2 == 0
            c = shade(wood_c, 1.06 if band else 0.9)
            if r < 1.2:
                c = shade(wood_c, 0.8)
            px(img, x, y, c)
    return img


def stripped_side(wood_c, seed):
    rnd = random.Random(seed)
    img = new()
    for x in range(16):
        f = 1 + 0.07 * math.sin(x * 1.7 + seed) + rnd.uniform(-0.03, 0.03)
        for y in range(16):
            px(img, x, y, shade(wood_c, f + rnd.uniform(-0.025, 0.025)))
    for _ in range(5):
        x, y = rnd.randrange(16), rnd.randrange(16)
        for i in range(rnd.randrange(3, 7)):
            px(img, x, (y + i) % 16, shade(wood_c, 0.82))
    return img


def planks(wood_c, seed, grain=0.85):
    rnd = random.Random(seed)
    img = new()
    for row in range(4):
        y0 = row * 4
        seam = (rnd.randrange(3, 13)) if row % 2 else (rnd.randrange(2, 7) + 8) % 16
        for x in range(16):
            for y in range(y0, y0 + 4):
                f = 1 + rnd.uniform(-0.035, 0.035) + (0.04 if y == y0 else 0)
                c = shade(wood_c, f)
                if y == y0 + 3:
                    c = shade(wood_c, 0.72)
                if x == seam:
                    c = shade(wood_c, 0.75)
                px(img, x, y, c)
        for _ in range(3):
            gx = rnd.randrange(16)
            gy = y0 + rnd.randrange(1, 3)
            for i in range(rnd.randrange(2, 5)):
                if gx + i != seam:
                    px(img, gx + i, gy, shade(wood_c, grain))
    return img


def door(wood_c, seed, top, window=True):
    rnd = random.Random(seed)
    img = new()
    base = planks(wood_c, seed + 5)
    for x in range(16):
        for y in range(16):
            frame = x in (0, 1, 14, 15) or (top and y in (0, 1)) or (not top and y in (14, 15)) or y in (7, 8)
            p = base.getpixel((x, y))
            px(img, x, y, shade(wood_c, 0.8) if frame else p)
    if top and window:
        for x in range(4, 12):
            for y in range(3, 6):
                if x not in (7, 8):
                    img.putpixel((x, y), T)
    else:
        for y in range(3, 13):
            if y not in (7, 8):
                px(img, 7, y, shade(wood_c, 0.7))
    px(img, 12, 9 if top else 6, (40, 40, 40))
    return img


def trapdoor(wood_c, seed):
    img = new()
    base = planks(wood_c, seed + 9)
    for x in range(16):
        for y in range(16):
            frame = x in (0, 1, 14, 15) or y in (0, 1, 14, 15) or x in (7, 8) or y in (7, 8)
            if frame:
                px(img, x, y, shade(wood_c, 0.82))
            elif (x + y) % 5 == 0:
                img.putpixel((x, y), T)
            else:
                img.putpixel((x, y), base.getpixel((x, y)))
    return img


def leaves(base, seed, style="round", accent=None, hole=0.14, glow=None):
    rnd = random.Random(seed)
    img = new()
    for x in range(16):
        for y in range(16):
            if rnd.random() < hole:
                continue
            f = rnd.choice([0.78, 0.9, 1.0, 1.0, 1.12])
            px(img, x, y, shade(base, f))
    if style == "needles":
        for _ in range(22):
            x, y = rnd.randrange(16), rnd.randrange(16)
            for i in range(3):
                px(img, x + i, y + i, shade(base, 1.2 if i == 0 else 0.75))
    elif style == "drooping":
        for x in range(0, 16, 2):
            y = rnd.randrange(16)
            for i in range(rnd.randrange(3, 7)):
                px(img, x + rnd.choice([0, 0, 1]), (y + i) % 16, shade(base, 1.18))
    elif style == "round":
        for _ in range(10):
            cx, cy = rnd.randrange(16), rnd.randrange(16)
            px(img, cx, cy, shade(base, 1.25))
            px(img, cx + 1, cy + 1, shade(base, 0.7))
    if accent:
        for _ in range(10):
            px(img, rnd.randrange(16), rnd.randrange(16), accent)
    if glow:
        for _ in range(7):
            x, y = rnd.randrange(16), rnd.randrange(16)
            px(img, x, y, glow)
    return img


# ---------------------------------------------------------------------------- sprites

def line(img, x0, y0, x1, y1, c):
    n = int(max(abs(x1 - x0), abs(y1 - y0), 1))
    for i in range(n + 1):
        t = i / n
        px(img, round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), c)


def disc(img, cx, cy, r, c, shade_c=None):
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            d = (x - cx) ** 2 + (y - cy) ** 2
            if d <= r * r:
                cc = c
                if shade_c is not None and (x - cx) + (y - cy) > r * 0.4:
                    cc = shade_c
                px(img, x, y, cc)


def sapling(trunk, leaf, seed, shape="round", accent=None):
    rnd = random.Random(seed)
    img = new()
    line(img, 8, 15, 8, 8, trunk)
    if shape == "round":
        for cx, cy, r in ((8, 6, 3.2), (5, 8, 2.0), (11, 8, 2.0)):
            disc(img, cx, cy, r, leaf, shade(leaf, 0.75))
    elif shape == "cone":
        for y in range(2, 12):
            w = (y - 1) * 0.45
            for x in range(int(8 - w), int(8 + w) + 1):
                px(img, x, y, shade(leaf, 1.1 if (x + y) % 3 else 0.8))
    elif shape == "umbrella":
        line(img, 8, 9, 9, 6, trunk)
        for x in range(3, 14):
            px(img, x, 5, leaf)
            px(img, x, 4 if 5 <= x <= 11 else 5, shade(leaf, 1.15))
        for x in (3, 6, 10, 13):
            px(img, x, 6, leaf)
    elif shape == "droop":
        disc(img, 8, 6, 3.5, leaf, shade(leaf, 0.8))
        for x in (4, 6, 10, 12):
            for y in range(7, 7 + rnd.randrange(3, 6)):
                px(img, x, y, shade(leaf, 0.9))
    elif shape == "twisted":
        line(img, 8, 15, 6, 10, trunk)
        line(img, 6, 10, 9, 6, trunk)
        line(img, 9, 8, 12, 6, trunk)
        disc(img, 9, 4, 2.2, leaf)
        disc(img, 12, 5, 1.5, leaf)
        disc(img, 5, 8, 1.6, leaf)
    elif shape == "bent":
        line(img, 8, 15, 10, 9, trunk)
        line(img, 10, 9, 13, 6, trunk)
        for x in range(6, 15):
            px(img, x, 5, leaf)
            if 8 <= x <= 13:
                px(img, x, 4, shade(leaf, 1.15))
    if accent:
        for _ in range(4):
            px(img, rnd.randrange(4, 13), rnd.randrange(2, 10), accent)
    return outline(img, (20, 20, 20))


def fern(stem, leaf, seed, tip=None, alpha=255, height=14):
    rnd = random.Random(seed)
    img = new()
    for i, (bx, lean) in enumerate(((8, 0), (4, -1), (12, 1), (6, -1), (10, 1))):
        top = 16 - height + rnd.randrange(0, 4) + (2 if i else 0)
        x = float(bx)
        for y in range(15, top - 1, -1):
            x += lean * 0.18
            px(img, x, y, stem, alpha)
            if (15 - y) % 2 == 0 and y > top + 1:
                px(img, x - 1, y - 1, leaf, alpha)
                px(img, x + 1, y - 1, leaf, alpha)
                if rnd.random() < 0.5:
                    px(img, x - 2, y - 2, shade(leaf, 0.85), alpha)
                    px(img, x + 2, y - 2, shade(leaf, 0.85), alpha)
        if tip:
            px(img, x, top, tip, alpha)
            px(img, x, top - 1, tip, alpha)
    return img


def grass(color, seed, tip=None, n=9, height=12, angular=False, spark=None, alpha=255):
    rnd = random.Random(seed)
    img = new()
    for _ in range(n):
        x = rnd.randrange(1, 15)
        h = rnd.randrange(max(3, height - 6), height + 1)
        lean = rnd.choice([-1, 0, 0, 1])
        for i in range(h):
            xx = x + (lean * i) // (h if not angular else 3)
            c = shade(color, 0.75 + 0.35 * i / h)
            if tip and i >= h - 2:
                c = tip
            px(img, xx, 15 - i, c, alpha)
    if spark:
        for _ in range(4):
            px(img, rnd.randrange(16), rnd.randrange(4, 14), spark)
    return img


def flower(stem, petal, center, seed, kind="daisy", closed=False, leaf=None):
    """kind: daisy, bell, trumpet, star, cluster, crystal, round."""
    rnd = random.Random(seed)
    img = new()
    leaf = leaf or shade(stem, 1.1)
    line(img, 8, 15, 8, 7, stem)
    line(img, 8, 13, 5, 11, leaf)
    line(img, 8, 12, 11, 10, leaf)
    px(img, 4, 11, leaf)
    px(img, 12, 10, leaf)
    if closed:
        for y in range(3, 7):
            w = 1 if y in (3, 6) else 2
            for x in range(8 - w + 1, 8 + w):
                px(img, x, y, shade(petal, 0.75 if x < 8 else 0.95))
        px(img, 8, 2, shade(petal, 1.1))
        return outline(img, (25, 25, 25))
    if kind == "daisy":
        for i in range(8):
            a = i * math.pi / 4
            px(img, 8 + round(math.cos(a) * 2.6), 5 + round(math.sin(a) * 2.6), petal)
            px(img, 8 + round(math.cos(a) * 1.6), 5 + round(math.sin(a) * 1.6), shade(petal, 0.9))
        disc(img, 8, 5, 1.0, center)
    elif kind == "bell":
        for cx in (5, 11, 8):
            cy = 7 if cx != 8 else 4
            line(img, 8, cy - 1, cx, cy - 1, stem)
            for y in range(cy, cy + 3):
                w = 1 + (y - cy) // 1
                for x in range(cx - w + 1, cx + w):
                    px(img, x, y, shade(petal, 1.0 if x >= cx else 0.82))
            px(img, cx, cy + 3, center)
    elif kind == "trumpet":
        for y in range(1, 7):
            w = max(1, (7 - y) // 2 + (1 if y < 3 else 0))
            for x in range(8 - w, 8 + w + 1):
                px(img, x, y, shade(petal, 1.15 if y < 3 else 0.95))
        px(img, 8, 2, center)
        px(img, 7, 1, center)
        px(img, 9, 1, center)
    elif kind == "star":
        for dx, dy in ((0, -3), (-3, -1), (3, -1), (-2, 2), (2, 2)):
            line(img, 8, 5, 8 + dx, 5 + dy, petal)
        px(img, 8, 5, center)
    elif kind == "cluster":
        for _ in range(9):
            disc(img, 8 + rnd.uniform(-3, 3), 4 + rnd.uniform(-2, 2), 0.9, rnd.choice([petal, shade(petal, 1.15), shade(petal, 0.85)]))
        px(img, 8, 4, center)
    elif kind == "crystal":
        for dx, h in ((-2, 4), (0, 6), (2, 4)):
            for i in range(h):
                px(img, 8 + dx, 7 - i, petal if i < h - 1 else center)
                if i < h - 2:
                    px(img, 8 + dx + (1 if dx >= 0 else -1), 7 - i, shade(petal, 0.75))
    elif kind == "round":
        disc(img, 8, 5, 3.0, petal, shade(petal, 0.85))
        disc(img, 8, 5, 1.0, center)
    return outline(img, (25, 25, 25))


def bush(leaf, seed, stage, fruit=None, fruit_n=6, thorns=None, fruit_r=0.8):
    rnd = random.Random(seed)
    img = new()
    size = [3.5, 5.5, 6.5][stage]
    cy = 15 - size * 0.9
    for _ in range(int(14 * size)):
        a = rnd.uniform(0, math.pi * 2)
        r = rnd.uniform(0, size)
        x, y = 8 + math.cos(a) * r, cy + math.sin(a) * r * 0.85
        if y <= 15.5:
            px(img, x, y, shade(leaf, rnd.choice([0.75, 0.9, 1.0, 1.15])))
    if thorns:
        for _ in range(8 + stage * 4):
            px(img, rnd.randrange(2, 14), rnd.randrange(int(cy - size), 16), thorns)
    if fruit and stage == 2:
        for _ in range(fruit_n):
            fx, fy = 8 + rnd.uniform(-size + 1, size - 1), cy + rnd.uniform(-size * 0.7, size * 0.5)
            disc(img, fx, fy, fruit_r, fruit)
            px(img, fx - 0.4, fy - 0.4, shade(fruit, 1.35))
    return outline(img, (20, 30, 20))


def herb(leaf, seed, stage, vein=None, bloom=None, broad=True):
    rnd = random.Random(seed)
    img = new()
    n = [3, 5, 6][stage]
    for i in range(n):
        a = math.pi * (0.15 + 0.7 * i / max(1, n - 1)) + rnd.uniform(-0.1, 0.1)
        ln = [4, 6, 7][stage] + rnd.randrange(2)
        ex, ey = 8 + math.cos(a) * ln, 15 - math.sin(a) * ln
        line(img, 8, 15, ex, ey, shade(leaf, 0.8))
        if broad:
            for t in (0.5, 0.75):
                mx, my = 8 + (ex - 8) * t, 15 + (ey - 15) * t
                disc(img, mx, my, 1.1, leaf)
                if vein:
                    px(img, mx, my, vein)
    if bloom and stage == 2:
        for _ in range(3):
            disc(img, 8 + rnd.uniform(-3, 3), 5 + rnd.uniform(-2, 2), 1.2, bloom)
    return outline(img, (20, 30, 20))


def mushroom(cap, stem, seed, kind="dome", n=1, spots=None, gill=None, scale=1.0, alpha=255):
    """kind: dome, bell, flat, puff, tall, shelf."""
    rnd = random.Random(seed)
    img = new()
    spots_xy = [(8, 0)] if n == 1 else [(5, 1), (11, 0), (8, 3)][:n]
    for i, (cx, yoff) in enumerate(spots_xy):
        sc = scale * (1.0 if i == 0 or n == 1 else 0.75)
        base_y = 15
        sh = int(round((5 if kind != "tall" else 8) * sc))
        top = base_y - sh - yoff
        if kind != "puff":
            line(img, cx, base_y, cx, top + 2, stem)
        w = 3.3 * sc
        if kind == "dome":
            for x in range(int(cx - w), int(cx + w) + 1):
                hh = int(round(math.sqrt(max(0, w * w - (x - cx) ** 2)) * 0.7))
                for y in range(top - hh + 1, top + 2):
                    px(img, x, y, shade(cap, 1.1 if y < top else 0.85), alpha)
            if gill:
                for x in range(int(cx - w + 1), int(cx + w)):
                    px(img, x, top + 2, gill, alpha)
        elif kind in ("bell", "tall"):
            for y in range(top - 2, top + 3):
                ww = 1 + (y - top + 2) * 0.55
                for x in range(int(cx - ww), int(cx + ww) + 1):
                    px(img, x, y, shade(cap, 1.05 if x <= cx else 0.82), alpha)
            if gill:
                px(img, cx - 1, top + 3, gill, alpha)
                px(img, cx + 1, top + 3, gill, alpha)
        elif kind == "flat":
            for x in range(int(cx - w - 0.5), int(cx + w + 0.5) + 1):
                px(img, x, top + 1, cap, alpha)
                if abs(x - cx) < w - 1:
                    px(img, x, top, shade(cap, 1.15), alpha)
            if gill:
                for x in range(int(cx - w + 1), int(cx + w)):
                    px(img, x, top + 2, gill, alpha)
        elif kind == "puff":
            r = 2.6 * sc
            disc(img, cx, base_y - r, r, cap, shade(cap, 0.8))
            px(img, cx - 1, base_y - r - 1, shade(cap, 1.25))
        if spots:
            for _ in range(int(3 * sc) + 1):
                px(img, cx + rnd.randrange(-2, 3), top + rnd.randrange(-1, 1), spots, alpha)
    return outline(img, (25, 20, 25))


def crop(stem, seed, stage, kind="leafy", fruit=None, glow=None):
    """4 stages; kind: leafy (root crops), grain, pepper, bean, rice, nub, rosette."""
    rnd = random.Random(seed)
    img = new()
    h = [3, 6, 9, 12][stage]
    for bx in (3, 7, 11, 14) if kind not in ("rosette", "nub") else (5, 8, 11):
        x = bx + rnd.randrange(-1, 1)
        hh = max(2, h - rnd.randrange(0, 3))
        if kind == "grain" or kind == "rice":
            for i in range(hh):
                px(img, x, 15 - i, shade(stem, 0.8 + 0.3 * i / hh))
            if stage >= 2:
                for i in range(3):
                    hx = x + (1 if kind == "rice" and i > 0 else 0)
                    px(img, hx, 15 - hh - i + (2 if kind == "rice" else 0), fruit if stage == 3 else shade(stem, 1.2))
        elif kind == "leafy":
            line(img, x, 15, x + rnd.choice([-1, 1]), 15 - hh, stem)
            if stage >= 1:
                disc(img, x + rnd.choice([-1, 1]), 15 - hh + 1, 1.0 + stage * 0.3, shade(stem, 1.1))
        elif kind in ("pepper", "bean"):
            line(img, x, 15, x, 15 - hh, shade(stem, 0.8))
            for k in range(1, hh, 3):
                px(img, x - 1, 15 - k, stem)
                px(img, x + 1, 15 - k - 1, stem)
            if stage == 3:
                for k in range(2, hh, 4):
                    if kind == "pepper":
                        px(img, x + 1, 15 - k, fruit)
                        px(img, x + 1, 15 - k + 1, fruit)
                    else:
                        line(img, x - 1, 15 - k, x - 1, 15 - k + 2, fruit)
        elif kind in ("rosette", "nub"):
            if kind == "rosette":
                for i in range(hh // 2 + 1):
                    px(img, x - i // 2, 15 - i, stem)
                    px(img, x + i // 2, 15 - i, shade(stem, 1.15))
            else:
                disc(img, x, 15, 1 + stage * 0.45, fruit or stem, shade(fruit or stem, 0.8))
    if kind == "leafy" and stage == 3 and fruit:
        for x in (4, 8, 12):
            px(img, x, 15, fruit)
            px(img, x + 1, 15, shade(fruit, 0.8))
            px(img, x, 14, shade(fruit, 1.2))
    if kind == "rosette" and stage == 3 and fruit:
        for x in (5, 11):
            disc(img, x, 13, 1.2, fruit)
    if glow and stage == 3:
        for _ in range(4):
            px(img, rnd.randrange(2, 14), rnd.randrange(3, 10), glow)
    return outline(img, (20, 25, 15))


def strands(color, tip, seed, n=4, glow_dots=None, body=False, alpha=255):
    rnd = random.Random(seed)
    img = new()
    for sx in [3, 6, 9, 12][:n]:
        x = sx + rnd.choice([-1, 0, 1])
        for y in range(16):
            if rnd.random() < 0.25:
                x += rnd.choice([-1, 1])
            x = max(1, min(14, x))
            c = tip if (not body and y > 12) else color
            px(img, x, y, c, alpha)
            if glow_dots and rnd.random() < 0.18:
                px(img, x + 1, y, glow_dots, alpha)
    return img


def ribbon(color, seed, tip=False, glow=None):
    rnd = random.Random(seed)
    img = new()
    for k, sx in enumerate((4, 8, 12)):
        phase = rnd.uniform(0, 6)
        top = rnd.randrange(2, 6) if tip else 0
        for y in range(top, 16):
            x = sx + round(math.sin(y * 0.6 + phase) * 1.5)
            px(img, x, y, shade(color, 0.85 + 0.25 * ((y + k) % 3 == 0)))
            px(img, x + 1, y, shade(color, 0.7))
            if glow and rnd.random() < 0.12:
                px(img, x, y, glow)
    return img


def pad(color, seed, flower_c=None, glow=None):
    rnd = random.Random(seed)
    img = new()
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            notch = (x >= 8 and abs(y - 7.5) < 1.2)
            if d < 7.2 and not notch:
                c = shade(color, 0.9 + 0.2 * ((x * 3 + y) % 4 == 0))
                if d > 6.2:
                    c = shade(color, 0.7)
                px(img, x, y, c)
    for i in range(6):
        a = i * math.pi / 3
        line(img, 7.5, 7.5, 7.5 + math.cos(a) * 5, 7.5 + math.sin(a) * 5, shade(color, 0.75))
    if glow:
        for _ in range(7):
            px(img, rnd.randrange(3, 13), rnd.randrange(3, 13), glow)
    if flower_c:
        for dx, dy in ((0, -2), (-2, 0), (2, 0), (0, 2), (-1, -1), (1, 1), (1, -1), (-1, 1)):
            px(img, 7 + dx, 7 + dy, flower_c)
        px(img, 7, 7, (255, 250, 200))
    return img


def crystal_sprite(body, light, seed, small=False):
    rnd = random.Random(seed)
    img = new()
    spikes = [(8, 3, 9 if not small else 5), (5, 2, 6 if not small else 3), (11, 2, 7 if not small else 4)]
    for cx, half, h in spikes:
        cx += rnd.choice([-1, 0, 1])
        for i in range(h):
            w = max(0, half - (i * half) // h)
            for x in range(cx - w, cx + w + 1):
                px(img, x, 15 - i, light if x == cx and i < h - 1 else (body if x <= cx else shade(body, 0.75)))
        px(img, cx, 15 - h, shade(light, 1.1))
    return outline(img, shade(body, 0.4))


def plate(color, seed, rings=True):
    rnd = random.Random(seed)
    img = new()
    for x in range(16):
        for y in range(16):
            r = math.hypot(x - 7.5, y - 16)
            c = shade(color, 0.85 + 0.25 * (int(r) % 3 == 0)) if rings else shade(color, 1 + rnd.uniform(-0.08, 0.08))
            px(img, x, y, c)
    return img


# ---------------------------------------------------------------------------- items

def item_canvas():
    return new()


def berries(c, seed, leaf=(60, 120, 50), n=5):
    rnd = random.Random(seed)
    img = new()
    line(img, 8, 2, 9, 5, leaf)
    px(img, 10, 3, leaf)
    px(img, 11, 3, leaf)
    pos = [(6, 8), (10, 8), (8, 11), (5, 12), (11, 12), (8, 6)][:n]
    for x, y in pos:
        disc(img, x, y, 1.7, c, shade(c, 0.7))
        px(img, x - 1, y - 1, shade(c, 1.45))
    return outline(img, shade(c, 0.35))


def fruit(c, seed, leaf=(70, 130, 50), r=4.5):
    img = new()
    disc(img, 8, 9, r, c, shade(c, 0.72))
    px(img, 6, 7, shade(c, 1.4))
    px(img, 7, 6, shade(c, 1.3))
    line(img, 8, 4, 9, 2, (90, 60, 30))
    px(img, 10, 3, leaf)
    px(img, 11, 2, leaf)
    px(img, 11, 3, leaf)
    return outline(img, shade(c, 0.3))


def root(c, seed, top=(70, 130, 60), baked=False):
    img = new()
    for i in range(9):
        x, y = 4 + i, 11 - i // 2
        disc(img, x, y, 2.3 - abs(i - 4) * 0.22, c, shade(c, 0.75))
    if not baked:
        line(img, 12, 6, 14, 2, top)
        line(img, 12, 6, 11, 2, top)
    else:
        for x, y in ((6, 10), (9, 8), (11, 8)):
            px(img, x, y, shade(c, 0.6))
    px(img, 6, 9, shade(c, 1.3))
    return outline(img, shade(c, 0.35))


def bread(c, seed):
    img = new()
    for x in range(2, 15):
        hh = int(4.5 - abs(x - 8) * 0.35)
        for y in range(10 - hh, 13):
            px(img, x, y, shade(c, 1.1 if y < 9 else 0.85))
    for x in (5, 8, 11):
        px(img, x, 7, shade(c, 0.65))
        px(img, x + 1, 8, shade(c, 0.65))
    return outline(img, shade(c, 0.35))


def bowl(content, seed, bits=None):
    rnd = random.Random(seed)
    img = new()
    wood = (130, 90, 50)
    for x in range(2, 15):
        depth = int(4 - abs(x - 8) * 0.45)
        for y in range(10, 10 + max(1, depth) + 1):
            px(img, x, y, shade(wood, 1.0 if y == 10 else 0.8))
    for x in range(3, 14):
        px(img, x, 9, content)
        if 5 <= x <= 11:
            px(img, x, 8, shade(content, 1.15))
    for c in bits or []:
        px(img, rnd.randrange(4, 12), rnd.randrange(7, 10), c)
    return outline(img, (40, 25, 15))


def seeds(c, seed, n=6):
    rnd = random.Random(seed)
    img = new()
    for _ in range(n):
        x, y = rnd.randrange(4, 12), rnd.randrange(5, 12)
        px(img, x, y, c)
        px(img, x + 1, y, shade(c, 0.75))
        px(img, x, y - 1, shade(c, 1.25))
    return img


def grain_ear(c, seed):
    img = new()
    line(img, 4, 14, 11, 3, (120, 110, 50))
    for i in range(5):
        x, y = 6 + i, 10 - i * 2
        disc(img, x, y, 1.1, c)
        px(img, x + 1, y + 1, shade(c, 0.7))
    return outline(img, shade(c, 0.4))


def pepper(c, seed):
    img = new()
    for i in range(8):
        x, y = 5 + i, 5 + int(i * 0.9) + (1 if i > 5 else 0)
        disc(img, x, y, 1.6 - i * 0.12, c, shade(c, 0.7))
    line(img, 4, 5, 3, 2, (70, 120, 40))
    px(img, 4, 7, shade(c, 1.4))
    return outline(img, shade(c, 0.3))


def bulb(c, seed, top=(80, 140, 70), top2=None):
    img = new()
    disc(img, 8, 10, 4.0, c, shade(c, 0.82))
    if top2:
        for x in range(5, 12):
            px(img, x, 7, top2)
    line(img, 8, 6, 6, 1, top)
    line(img, 8, 6, 10, 1, top)
    line(img, 8, 6, 8, 2, shade(top, 1.15))
    px(img, 6, 9, shade(c, 1.25))
    return outline(img, shade(c, 0.4))


def pod(c, seed):
    img = new()
    for i in range(10):
        x, y = 3 + i, 12 - i
        px(img, x, y, c)
        px(img, x + 1, y, c)
        px(img, x, y - 1, shade(c, 1.2))
    for i in (2, 5, 8):
        px(img, 3 + i, 11 - i, shade(c, 0.7))
    line(img, 13, 3, 14, 1, (80, 130, 50))
    return outline(img, shade(c, 0.4))


def nut(c, seed, spiky=False):
    img = new()
    disc(img, 8, 9, 4.0, c, shade(c, 0.7))
    if spiky:
        for dx, dy in ((-5, 0), (5, 0), (0, -5), (-4, -3), (4, -3), (-3, 4), (3, 4)):
            px(img, 8 + dx, 9 + dy, shade(c, 0.6))
    px(img, 6, 7, shade(c, 1.35))
    return outline(img, shade(c, 0.35))


def truffle(c, seed):
    rnd = random.Random(seed)
    img = new()
    for _ in range(4):
        disc(img, 8 + rnd.uniform(-2, 2), 9 + rnd.uniform(-1.5, 1.5), 2.6, c, shade(c, 0.75))
    for _ in range(6):
        px(img, rnd.randrange(5, 12), rnd.randrange(6, 12), shade(c, 1.3))
    return outline(img, shade(c, 0.35))


def leaf_item(c, seed, vein=None, n=3):
    img = new()
    for i in range(n):
        a = -0.5 + i * 0.5
        ex, ey = 8 + math.sin(a) * 6, 13 - math.cos(a) * 9
        line(img, 8, 14, ex, ey, shade(c, 0.75))
        for t in (0.35, 0.6, 0.85):
            disc(img, 8 + (ex - 8) * t, 14 + (ey - 14) * t, 1.6 - t * 0.6, c)
        if vein:
            line(img, 8, 14, ex, ey, vein)
    return outline(img, shade(c, 0.35))


def blossom(c, center, seed):
    img = new()
    for i in range(6):
        a = i * math.pi / 3
        disc(img, 8 + math.cos(a) * 3, 8 + math.sin(a) * 3, 2.0, c, shade(c, 0.8))
    disc(img, 8, 8, 1.6, center)
    return outline(img, shade(c, 0.35))


def petal(c, seed):
    img = new()
    for y in range(3, 14):
        w = int(3.5 * math.sin((y - 2) / 12 * math.pi))
        for x in range(8 - w, 8 + w + 1):
            px(img, x, y, shade(c, 1.12 if x < 8 else 0.9))
    line(img, 8, 4, 8, 13, shade(c, 0.8))
    return outline(img, shade(c, 0.45))


def fang(c, seed):
    img = new()
    for y in range(2, 15):
        w = max(0, int((15 - y) * 0.28))
        for x in range(7 - w, 8 + w + 1):
            px(img, x + (y // 5), y, shade(c, 1.1 if x <= 7 else 0.85))
    return outline(img, (60, 20, 20))


def fiber(c, seed):
    img = new()
    for k in range(3):
        for t in range(40):
            a = t * 0.35 + k * 2
            x = 8 + math.cos(a) * (2 + t * 0.12)
            y = 8 + math.sin(a) * (2 + t * 0.12) * 0.7
            px(img, x, y, shade(c, 0.85 + 0.15 * k))
    return outline(img, shade(c, 0.4))


def glob(c, seed):
    img = new()
    for y in range(3, 15):
        w = 4.2 * math.sin((y - 2) / 13 * math.pi) ** 0.7 if y > 6 else (y - 2) * 0.7
        for x in range(int(8 - w), int(8 + w) + 1):
            px(img, x, y, shade(c, 1.15 if x < 8 and y < 10 else 0.85))
    px(img, 6, 8, (255, 240, 200))
    return outline(img, shade(c, 0.4))


def bottle(liquid, seed, glow=False):
    img = new()
    glass = (200, 220, 230)
    for y in range(2, 5):
        px(img, 7, y, glass)
        px(img, 8, y, glass)
    px(img, 7, 1, (140, 100, 60))
    px(img, 8, 1, (140, 100, 60))
    for y in range(5, 15):
        w = 1 if y == 5 else 3 if y == 6 else 4 if y < 14 else 3
        for x in range(8 - w, 8 + w):
            c = liquid if y >= 8 else glass
            px(img, x, y, shade(c, 1.15 if x < 7 else 0.9))
    px(img, 6, 9, (255, 255, 255))
    if glow:
        px(img, 9, 11, shade(liquid, 1.5))
    return outline(img, (50, 50, 60))


def poultice(c, seed):
    img = new()
    for x in range(3, 13):
        for y in range(5, 12):
            px(img, x, y, (220, 210, 180) if (x + y) % 4 else (190, 180, 150))
    for x in range(5, 11):
        for y in range(7, 10):
            px(img, x, y, c)
    line(img, 3, 8, 12, 8, (150, 110, 70))
    return outline(img, (60, 50, 40))


def salve(c, seed):
    img = new()
    for x in range(3, 14):
        px(img, x, 6, (180, 180, 190))
        for y in range(7, 12):
            px(img, x, y, (150, 150, 160) if y > 7 else c)
    for x in range(4, 13):
        px(img, x, 7, c)
        px(img, x, 6, shade(c, 1.2))
    return outline(img, (50, 50, 60))


def gem(c, seed, kind="cut"):
    img = new()
    light, dark = shade(c, 1.35), shade(c, 0.65)
    if kind == "cut":
        rows = [(6, 9), (4, 11), (3, 12), (3, 12), (4, 11), (5, 10), (6, 9), (7, 8)]
        for i, (a, b) in enumerate(rows):
            for x in range(a, b + 1):
                px(img, x, 4 + i, light if i < 2 and x < 8 else (c if x < 9 else dark))
    elif kind == "crystal":
        for x0, h in ((5, 7), (8, 10), (11, 6)):
            for i in range(h):
                px(img, x0, 13 - i, light if i == h - 1 else c)
                px(img, x0 + 1, 13 - i, dark if i < h - 1 else c)
    elif kind == "shard":
        for i in range(10):
            for x in range(6 + i // 3, 10 - i // 4):
                px(img, x + i // 3, 13 - i, light if x == 6 + i // 3 else c)
    elif kind == "round":
        disc(img, 8, 8, 4.5, c, dark)
        disc(img, 6.5, 6.5, 1.2, light)
    return outline(img, shade(c, 0.3))


def dust(c, seed):
    rnd = random.Random(seed)
    img = new()
    for y in range(9, 14):
        w = (y - 8) * 1.2
        for x in range(int(8 - w), int(8 + w) + 1):
            if rnd.random() < 0.9:
                px(img, x, y, shade(c, rnd.choice([0.8, 1.0, 1.2])))
    for _ in range(5):
        px(img, rnd.randrange(4, 12), rnd.randrange(5, 9), c)
    return outline(img, shade(c, 0.4))


def ingot(c, seed):
    img = new()
    for y in range(6, 12):
        inset = 11 - y if y < 8 else 0
        for x in range(2 + inset, 14 - (11 - y if y < 8 else 0) // 2):
            px(img, x, y, shade(c, 1.25 if y == 6 else 1.0 if y < 10 else 0.75))
    return outline(img, shade(c, 0.35))


def raw_chunk(c, seed):
    rnd = random.Random(seed)
    img = new()
    for _ in range(5):
        disc(img, 8 + rnd.uniform(-2.5, 2.5), 8 + rnd.uniform(-2.5, 2.5), 2.6, c, shade(c, 0.7))
    for _ in range(6):
        px(img, rnd.randrange(4, 12), rnd.randrange(4, 12), shade(c, 1.35))
    return outline(img, shade(c, 0.3))


def fragment(c, seed):
    img = new()
    line(img, 4, 12, 11, 5, c)
    line(img, 5, 12, 12, 5, c)
    line(img, 4, 11, 11, 4, shade(c, 1.1))
    disc(img, 4, 12, 1.5, c)
    disc(img, 12, 4, 1.5, c)
    px(img, 8, 8, shade(c, 0.7))
    return outline(img, shade(c, 0.4))


def sign_item(wood_c, seed, hanging=False):
    img = new()
    base = planks(wood_c, seed)
    if hanging:
        line(img, 4, 1, 4, 5, (90, 90, 100))
        line(img, 11, 1, 11, 5, (90, 90, 100))
        for x in range(2, 14):
            for y in range(5, 13):
                px(img, x, y, base.getpixel((x, y)))
    else:
        for x in range(1, 15):
            for y in range(2, 10):
                px(img, x, y, base.getpixel((x, y)))
        line(img, 7, 10, 7, 15, shade(wood_c, 0.6))
        line(img, 8, 10, 8, 15, shade(wood_c, 0.75))
    return outline(img, shade(wood_c, 0.4))


def sign_sheet(wood_c, seed, w=64, h=32, post=None):
    """Entity texture for (hanging) signs: tiled planks with a darker post area (UV-agnostic)."""
    tile = planks(wood_c, seed)
    img = new(w, h)
    for x in range(w):
        for y in range(h):
            img.putpixel((x, y), tile.getpixel((x % 16, y % 16)))
    if post is not None:
        for x in range(0, 16):
            for y in range(16, 32):
                img.putpixel((x, y), rgba(shade(post, 0.9 + 0.1 * ((x + y) % 3 == 0))))
    return img
