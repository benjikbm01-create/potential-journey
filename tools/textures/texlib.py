"""Underdeep texture library (Stage 11.5): art-directed 16x16 pixel art.

Principles (see docs/TEXTURE_STANDARDS.md):
- Controlled palettes: every material is a hue-shifted ramp of 5-7 tones. Shadows shift towards blue/violet and gain
  saturation; highlights shift towards warm yellow and lose saturation. No per-pixel random colours.
- Light comes from the top-left. Forms get a lit upper-left rim and a shaded lower-right rim (bevels).
- Structure, not noise: stone is a tileable mosaic of rock chunks with selective cracks; ores are mineral formations
  with outlines and highlights; wood has boards, seams, grain and knots; leaves are drawn leaf clusters; items are
  outlined sprites built from deliberate shapes.
- Every block texture tiles seamlessly (all structure is computed on a torus).
"""
import colorsys
import math

from PIL import Image


# ---------------------------------------------------------------------------------------------- colour

def hx(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def ramp(base, n=6, lo=0.42, hi=1.32, hue_shift=14.0, sat_shift=0.12):
    """Hue-shifted ramp, darkest first. base is the mid tone."""
    r, g, b = (c / 255 for c in base)
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    out = []
    for i in range(n):
        t = i / (n - 1)                       # 0 dark .. 1 light
        k = lo + (hi - lo) * t
        dl = (t - 0.5)
        hh = (h * 360 + (-dl * 2) * hue_shift * (1 if (h * 360 < 60 or h * 360 > 240) else -1)) % 360 / 360
        # shadows towards blue (240), highlights towards yellow (60)
        target_dark, target_light = 250 / 360, 55 / 360
        if dl < 0:
            hh = mix_hue(h, target_dark, min(1, -dl * 2) * hue_shift / 90)
        else:
            hh = mix_hue(h, target_light, min(1, dl * 2) * hue_shift / 90)
        ll = max(0.02, min(0.97, l * k))
        ss = max(0.0, min(1.0, s * (1 + (-dl) * sat_shift * 2)))
        rr, gg, bb = colorsys.hls_to_rgb(hh, ll, ss)
        out.append((round(rr * 255), round(gg * 255), round(bb * 255)))
    return out


def mix_hue(a, b, t):
    d = ((b - a + 0.5) % 1.0) - 0.5
    return (a + d * t) % 1.0


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c[:3])


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ---------------------------------------------------------------------------------------------- hashing (deterministic)

def h01(*xs):
    v = 2166136261
    for x in xs:
        v = ((v ^ (int(x) & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    v ^= v >> 13
    v = (v * 0x5bd1e995) & 0xFFFFFFFF
    v ^= v >> 15
    return v / 4294967296.0


def new(w=16, h=16):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def put(img, x, y, c, a=255):
    w, h = img.size
    img.putpixel((x % w, y % h), (*c[:3], a))


def get(img, x, y):
    w, h = img.size
    return img.getpixel((x % w, y % h))


# ---------------------------------------------------------------------------------------------- tileable rock mosaic

def seeds(seed, n, size=16, min_d=3.2):
    """Poisson-like seed points on a torus (deterministic)."""
    pts = []
    k = 0
    while len(pts) < n and k < 4000:
        x, y = h01(seed, k, 1) * size, h01(seed, k, 2) * size
        k += 1
        if all(tdist(x, y, px, py, size) >= min_d for px, py in pts):
            pts.append((x, y))
    return pts


def tdist(x0, y0, x1, y1, size=16):
    dx = abs(x0 - x1)
    dy = abs(y0 - y1)
    dx = min(dx, size - dx)
    dy = min(dy, size - dy)
    return math.hypot(dx, dy)


def voronoi(pts, size=16, stretch=(1.0, 1.0)):
    """Per pixel: (nearest index, d1, d2, offset to nearest seed) on a torus; stretch elongates cells (strata)."""
    out = {}
    for y in range(size):
        for x in range(size):
            best = []
            for i, (px, py) in enumerate(pts):
                dx = (x + 0.5) - px
                dy = (y + 0.5) - py
                dx -= size * round(dx / size)
                dy -= size * round(dy / size)
                d = math.hypot(dx / stretch[0], dy / stretch[1])
                best.append((d, i, dx, dy))
            best.sort()
            out[(x, y)] = (best[0][1], best[0][0], best[1][0], best[0][2], best[0][3], best[1][1])
    return out


def rock(pal, seed, n_cells=8, crack=0.55, bevel=1, specks=0.0, stretch=(1.0, 1.0), min_d=3.0, tone_bias=0.0,
         speck_col=None, spread=0.6):
    """Natural stone: chunks of rock with lit/shaded bevels and selective cracks. pal is a ramp (dark..light)."""
    img = new()
    pts = seeds(seed, n_cells, min_d=min_d)
    vo = voronoi(pts, stretch=stretch)
    n = len(pal)
    mid = (n - 1) / 2
    cell_tone = [min(n - 2, max(1, int(round(mid + (h01(seed, i, 7) - 0.5) * (n - 3) * spread + tone_bias)))) for i in range(len(pts))]
    for y in range(16):
        for x in range(16):
            i, d1, d2, dx, dy, j = vo[(x, y)]
            t = cell_tone[i]
            edge = d2 - d1
            pair_cracks = h01(seed, min(i, j), max(i, j), 3) < crack
            if edge < 0.9 and pair_cracks:
                c = pal[0]
            elif edge < 0.9:
                c = pal[max(0, t - 1)]
            else:
                # bevel: pixels on the lit (upper-left) side of a chunk are brighter, lower-right darker
                lit = (-dx - dy) / max(1e-6, math.hypot(dx, dy)) if (dx or dy) else 0
                tt = t
                if bevel and edge < 2.0:
                    tt = t + (1 if lit > 0.35 else -1 if lit < -0.35 else 0)
                c = pal[max(0, min(n - 1, tt))]
            put(img, x, y, c)
    if specks:
        sc = speck_col or pal[-1]
        for k in range(int(specks * 16)):
            x, y = int(h01(seed, k, 11) * 16), int(h01(seed, k, 12) * 16)
            put(img, x, y, sc)
    return img


def strata(pal, seed, bands=4, wobble=1.0, crack=0.4):
    """Layered sedimentary stone (horizontal bands of varied thickness, gently wavy, with sparse cracks)."""
    img = new()
    edges = sorted(int(h01(seed, k) * 16) for k in range(bands))
    for x in range(16):
        off = round(math.sin((x / 16) * 2 * math.pi + seed) * wobble)
        for y in range(16):
            yy = (y + off) % 16
            band = sum(1 for e in edges if yy >= e) % len(edges)
            t = 1 + int(h01(seed, band, 5) * (len(pal) - 3))
            c = pal[t]
            if yy in edges:
                c = pal[max(0, t - 1)]
            elif (yy + 1) % 16 in edges:
                c = pal[min(len(pal) - 1, t + 1)]
            put(img, x, y, c)
    for k in range(3):
        if h01(seed, k, 9) < crack:
            x, y = int(h01(seed, k, 1) * 16), int(h01(seed, k, 2) * 16)
            for s in range(3 + int(h01(seed, k, 3) * 3)):
                put(img, x, y, pal[0])
                x += 1
                y += 1 if h01(seed, k, s) > 0.5 else 0
    return img


# ---------------------------------------------------------------------------------------------- ores

# hand-placed cluster shapes (relative pixels). Each tuple: (dx, dy, role) role: 0 outline/dark, 1 mid, 2 light, 3 highlight
NODULE = [(0, 0, 2), (1, 0, 1), (0, 1, 1), (1, 1, 0), (-1, 0, 1), (0, -1, 3)]
GRAIN = [(0, 0, 3), (1, 0, 1), (0, 1, 1), (1, 1, 0)]
SHARD = [(0, 0, 3), (0, 1, 2), (0, 2, 1), (1, 1, 1), (1, 2, 0), (-1, 2, 0), (0, 3, 0)]
VEIN = [(0, 0, 2), (1, 0, 1), (2, 1, 1), (3, 1, 0), (-1, 0, 1), (-2, -1, 0), (1, -1, 3)]
BAND = [(0, 0, 3), (1, 0, 2), (2, 0, 1), (0, 1, 2), (1, 1, 1), (2, 1, 0), (-1, 0, 1), (-1, 1, 0), (0, -1, 1), (1, -1, 1)]


def ore(host, mineral, clusters, host_rim=True):
    """Embeds mineral formations into a host stone texture. mineral is a ramp (dark..light, >=4 tones).
    clusters: list of (x, y, shape). A dark host rim is drawn around each formation (the cavity it sits in)."""
    img = host.copy()
    occupied = set()
    for cx, cy, shape in clusters:
        for dx, dy, role in shape:
            occupied.add(((cx + dx) % 16, (cy + dy) % 16))
    if host_rim:
        for (x, y) in list(occupied):
            for ox, oy in ((1, 0), (0, 1), (1, 1)):
                q = ((x + ox) % 16, (y + oy) % 16)
                if q not in occupied:
                    c = get(img, *q)
                    put(img, q[0], q[1], shade(c, 0.72))
    roles = {0: mineral[0], 1: mineral[len(mineral) // 2 - 1], 2: mineral[len(mineral) // 2 + 1], 3: mineral[-1]}
    for cx, cy, shape in clusters:
        for dx, dy, role in shape:
            put(img, cx + dx, cy + dy, roles[role])
    return img


# ---------------------------------------------------------------------------------------------- wood

def planks(pal, seed, boards=4, knots=1):
    """Planks: horizontal boards with staggered end seams, grain streaks, lit top edge and shaded bottom edge."""
    img = new()
    bh = 16 // boards
    n = len(pal)
    for b in range(boards):
        seam = int(h01(seed, b, 1) * 16)
        base = 2 + int(h01(seed, b, 2) * (n - 4))
        for y in range(b * bh, b * bh + bh):
            ry = y - b * bh
            for x in range(16):
                t = base
                # grain: long darker streaks with breaks
                if ry in (1, bh - 2) and h01(seed, b, x // 3, ry) < 0.45:
                    t = base - 1
                if ry == 0:
                    t = min(n - 1, base + 1)          # lit top edge
                if ry == bh - 1:
                    t = 0                              # shadow gap between boards
                if x == seam and ry != bh - 1:
                    t = 1 if ry else base             # board end seam
                if x == (seam + 1) % 16 and 0 < ry < bh - 1:
                    t = min(n - 1, base + 1)          # lit edge after the seam
                put(img, x, y, pal[t])
        if knots and h01(seed, b, 7) < 0.4:
            kx = (seam + 4 + int(h01(seed, b, 8) * 6)) % 16
            ky = b * bh + 1
            put(img, kx, ky, pal[1])
            put(img, kx + 1, ky, pal[0])
            put(img, kx + 1, ky + 1, pal[1])
    return img


def log_side(bark, seed, ridges=5):
    """Bark: vertical ridged plates of varied width with dark fissures and broken horizontal checks."""
    img = new()
    n = len(bark)
    xs = sorted(set(int(h01(seed, k) * 16) for k in range(ridges)))
    for x in range(16):
        fiss = x in xs
        lit = (x - 1) % 16 in xs           # the column after a fissure catches light
        for y in range(16):
            t = 2 + (1 if h01(seed, x // 2, y // 5) > 0.6 else 0)
            if lit:
                t = min(n - 1, t + 1)
            if fiss and h01(seed, x, y // 2, 3) > 0.15:
                t = 0
            if h01(seed, x // 3, y, 9) < 0.07:   # horizontal check
                t = 1
            put(img, x, y, bark[t])
    return img


def log_top(wood, bark, seed, rings=3):
    img = new()
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            if r > 6.6 or max(abs(dx), abs(dy)) > 6.9:
                t = 1 + (1 if h01(seed, x, y) > 0.5 else 0)
                c = bark[t]
            else:
                rr = r + 0.25 * math.sin(math.atan2(dy, dx) * 2 + seed)   # gently irregular growth rings
                band = int(rr + 0.5) % 3
                c = wood[len(wood) - 2] if band == 0 else wood[len(wood) - 3]
                if r < 1.0:
                    c = wood[2]
                elif r > 6.0:
                    c = wood[len(wood) - 4]
            put(img, x, y, c)
    return img


def leaves(pal, seed, density=0.82, outline=True):
    """Leaves: overlapping clusters of drawn 5-texel leaves (lit tip, mid body, dark base) over a shaded canopy with a
    few see-through gaps. Clusters are placed on a torus so the block tiles."""
    img = new()
    n = len(pal)
    # canopy: dark interior in soft blobs (not per-pixel noise)
    blobs = seeds(seed + 1, 7, min_d=4.0)
    for y in range(16):
        for x in range(16):
            dmin = min(tdist(x + 0.5, y + 0.5, bx, by) for bx, by in blobs)
            if dmin < 3.2 or h01(seed, x // 2, y // 2) < density - 0.25:
                put(img, x, y, pal[1] if dmin < 2.0 else pal[0])
    leaf_shapes = [
        [(0, 0, 3), (1, 0, 2), (0, 1, 2), (1, 1, 1), (2, 1, 1), (1, 2, 0)],      # leaf pointing up-left
        [(1, 0, 3), (0, 1, 2), (1, 1, 2), (2, 1, 1), (0, 2, 1), (1, 2, 0)],      # pointing up
        [(2, 0, 3), (1, 1, 2), (2, 1, 2), (0, 1, 1), (1, 2, 1), (0, 2, 0)],      # pointing up-right
    ]
    pts = seeds(seed, 18, min_d=2.6)
    for i, (px, py) in enumerate(pts):
        sh = leaf_shapes[int(h01(seed, i, 5) * 3)]
        lift = 1 if h01(seed, i, 6) > 0.5 else 0
        for dx, dy, role in sh:
            t = min(n - 1, 1 + role + lift)
            put(img, int(px) + dx, int(py) + dy, pal[t])
    return img


# ---------------------------------------------------------------------------------------------- sprites

def sprite(rows, palette):
    """Hand-drawn pixel art: rows of characters mapped through palette ('.' = transparent)."""
    h = len(rows)
    w = max(len(r) for r in rows)
    img = new(w, h)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != "." and ch != " ":
                img.putpixel((x, y), (*palette[ch][:3], 255))
    return img


def outline(img, col, diagonal=False):
    """Adds a 1-px outline around the opaque area (vanilla item style)."""
    w, h = img.size
    out = img.copy()
    nb = ((1, 0), (-1, 0), (0, 1), (0, -1)) + (((1, 1), (-1, -1), (1, -1), (-1, 1)) if diagonal else ())
    for y in range(h):
        for x in range(w):
            if img.getpixel((x, y))[3] == 0:
                for dx, dy in nb:
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < w and 0 <= yy < h and img.getpixel((xx, yy))[3] > 0:
                        out.putpixel((x, y), (*col[:3], 255))
                        break
    return out


def bevel_block(img, pal, light=1, dark=0, inset=0):
    """Lit top/left and shaded bottom/right border (polished or crafted blocks)."""
    for i in range(inset, 16 - inset):
        put(img, i, inset, pal[light] if light >= 0 else get(img, i, inset))
        put(img, inset, i, pal[light])
        put(img, i, 15 - inset, pal[dark])
        put(img, 15 - inset, i, pal[dark])
    return img
