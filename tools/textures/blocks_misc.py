"""Stage 11.5 batch 4: workstations, furniture, mechanisms, ground covers and remaining blocks.

Each block is rebuilt on a new material surface (wood, dressed stone, metal plate, clay, cloth, soil/moss) in its
original colours; its symbol or feature layer (glyphs, runes, numerals, openings, lamps, glows) is lifted from the
original texture and redrawn as an inset/inlaid detail with a shadow, so every symbol players rely on is preserved.
Originals are read from git (the pre-11.5 commit), never from our own output.
"""
import colorsys
import io
import math
import os
import subprocess

from PIL import Image

from texlib import hx, ramp, shade, h01, new, put, get, rock, strata, planks, log_side, log_top, leaves, bevel_block, seeds, tdist

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
BASE_COMMIT = "c098626"   # last commit before Stage 11.5


def original(name):
    try:
        raw = subprocess.run(["git", "show", f"{BASE_COMMIT}:src/main/resources/assets/underdeep/textures/block/{name}.png"],
                             cwd=ROOT, capture_output=True, check=True).stdout
        return Image.open(io.BytesIO(raw)).convert("RGBA")
    except Exception:
        return None


def dominant(img):
    px = [c[:3] for c in img.getdata() if c[3] > 128]
    if not px:
        return (128, 128, 128)
    px.sort(key=lambda c: sum(c))
    return px[len(px) // 2]


def dist(a, b):
    return math.sqrt(sum((a[i] - b[i]) ** 2 for i in range(3)))


def features(img, base, thr=58):
    """Pixels that belong to a deliberate feature (symbol, opening, lamp): far from the base colour and part of a
    connected group of >= 2 (or very bright/saturated single texels)."""
    cand = set()
    for y in range(16):
        for x in range(16):
            c = img.getpixel((x, y))
            if c[3] > 128 and dist(c, base) > thr:
                cand.add((x, y))
    keep = {}
    for (x, y) in cand:
        nb = sum(((x + dx, y + dy) in cand) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)))
        c = img.getpixel((x, y))[:3]
        h, s, v = colorsys.rgb_to_hsv(*(q / 255 for q in c))
        if nb >= 2 or (v > 0.85 and s > 0.4):
            keep[(x, y)] = c
    return keep


def inlay(img, feats, depth=True):
    """Draws feature pixels with an inset shadow (lower-right) and a lit rim (upper-left) on the new surface."""
    for (x, y), c in feats.items():
        if depth:
            for dx, dy, f in ((1, 1, 0.62), (1, 0, 0.8), (0, 1, 0.8)):
                q = ((x + dx) % 16, (y + dy) % 16)
                if q not in feats:
                    put(img, q[0], q[1], shade(get(img, *q), f))
            for dx, dy in ((-1, -1),):
                q = ((x + dx) % 16, (y + dy) % 16)
                if q not in feats:
                    put(img, q[0], q[1], shade(get(img, *q), 1.15))
    for (x, y), c in feats.items():
        put(img, x, y, c)
    return img


# ------------------------------------------------------------------------------------------- surfaces

def surface(material, col, seed):
    if material == "wood":
        return planks(ramp(col, 6), seed)
    if material == "stone":
        p = ramp(col, 7, lo=0.62, hi=1.18)
        img = rock(p, seed, n_cells=6, crack=0.15, min_d=4.6, spread=0.4)
        return bevel_block(img, p, light=6, dark=1)
    if material == "brick":
        p = ramp(col, 6)
        img = new()
        for y in range(16):
            for x in range(16):
                row = y // 4
                bx = (x + (4 if row % 2 else 0)) % 8
                c = p[3] if h01(seed, row, (x + (4 if row % 2 else 0)) // 8) > 0.45 else p[2]
                if y % 4 == 3 or bx == 7:
                    c = p[0]
                elif y % 4 == 0:
                    c = p[4]
                put(img, x, y, c)
        return img
    if material == "metal":
        p = ramp(col, 6, lo=0.5, hi=1.3)
        img = new()
        for y in range(16):
            for x in range(16):
                c = p[3] if (y // 2) % 2 else p[2]          # brushed rows
                put(img, x, y, c)
        bevel_block(img, p, light=5, dark=0)
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            put(img, x, y, p[5]); put(img, x + 1, y + 1, p[0])
        return img
    if material == "clay":
        p = ramp(col, 6)
        img = strata(p, seed, bands=4, wobble=0.6, crack=0.1)
        return bevel_block(img, p, light=4, dark=1)
    if material == "cloth":
        p = ramp(col, 5)
        img = new()
        for y in range(16):
            for x in range(16):
                put(img, x, y, p[3] if (x + y) % 2 else p[2])
        for i in range(16):
            if i % 2 == 0:
                put(img, i, 1, p[4]); put(img, i, 14, p[4])
        return img
    if material == "glass":
        p = ramp(col, 5, hi=1.25)
        img = new()
        for y in range(16):
            for x in range(16):
                put(img, x, y, p[2], 140)
        for i in range(16):
            put(img, i, 0, p[4]); put(img, 0, i, p[4]); put(img, i, 15, p[1]); put(img, 15, i, p[1])
        for i in range(3, 7):
            put(img, i, 9 - i, p[4], 220)
        return img
    raise ValueError(material)


def ground(kind, col, seed, accent=None):
    """Ground covers: moss carpets, soils, nylium, lichens."""
    p = ramp(col, 6, hue_shift=10)
    img = new()
    if kind == "moss":
        blobs = seeds(seed, 9, min_d=3.6)
        for y in range(16):
            for x in range(16):
                d = min(tdist(x + 0.5, y + 0.5, bx, by) for bx, by in blobs)
                t = 4 if d < 1.2 else 3 if d < 2.4 else 2
                if h01(seed, x, y) < 0.12:
                    t = 1
                put(img, x, y, p[t])
    elif kind == "soil":
        img = rock(ramp(col, 6), seed, n_cells=12, crack=0.0, min_d=2.4, spread=0.7, bevel=0)
        for k in range(5):
            x, y = int(h01(seed, k) * 16), int(h01(seed, k, 1) * 16)
            put(img, x, y, shade(col, 1.35)); put(img, x + 1, y, shade(col, 0.7))
    elif kind == "lichen":
        img = rock(ramp(hx("6a6a6a"), 6), seed, n_cells=7, crack=0.3, min_d=3.4)
        blobs = seeds(seed + 3, 6, min_d=4)
        for y in range(16):
            for x in range(16):
                d = min(tdist(x + 0.5, y + 0.5, bx, by) for bx, by in blobs)
                if d < 2.2:
                    put(img, x, y, p[4] if d < 1.0 else p[3])
    if accent:
        for x, y in ((3, 3), (11, 5), (6, 12), (13, 13), (1, 9)):
            put(img, x, y, accent)
    return img


def mushroom_block(col, seed, spots=True):
    p = ramp(col, 6)
    img = new()
    for y in range(16):
        for x in range(16):
            t = 3 if (x + y // 2) % 4 else 2
            put(img, x, y, p[t])
    bevel_block(img, p, light=4, dark=1)
    if spots:
        for x, y in ((3, 3), (10, 4), (6, 10), (12, 12)):
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                put(img, x + dx, y + dy, hx("f0e8dc") if (dx, dy) != (1, 1) else hx("c8c0b4"))
    return img


# ------------------------------------------------------------------------------------------- classification

WOOD = ("furniture", "kitchen_counter", "cabinet", "pantry", "crate", "trough", "rack_wood", "workbench", "compost_bin", "cutting_board",
        "expedition_cache", "alarm_gong_frame", "awning_pole", "waymarker_post", "planter_side", "fish_trap", "herb_bundle", "artisan_bench",
        "leatherworker_bench", "gem_cutter", "rope_ladder", "twig_nest", "counter_top", "burrow_mound")
METAL = ("brazier", "conductor_pylon", "seal_bars", "kettle", "cooking_pot", "light_", "lantern_head", "key_hook", "silent_bell", "warding_bell",
         "alarm_gong_disc", "engine_coil", "lamplighter_post", "glimmer_lantern", "counterweight", "warding_brazier", "league_astrolabe", "star_globe")
CLAY = ("clay_oven", "crock", "alloy_furnace", "seed_urn", "mortar")
CLOTH = ("awning_canvas", "bedroll", "waymarker_flag")
GROUND = {"amber_moss": ("moss", None), "glowmoss": ("moss", hx("c8ff9a")), "starmoss": ("moss", hx("e8f0ff")), "ember_nylium": ("moss", hx("ffb04a")),
          "rich_soil": ("soil", None), "planter_soil": ("soil", None), "sporeglow_soil": ("soil", hx("8af0ff")), "petrolichen": ("lichen", None),
          "storm_lichen": ("lichen", hx("b8d0ff")), "glowivy": ("moss", hx("c8ff9a")), "glowpad": ("moss", hx("c8ff9a"))}
CAPS = ("bellcap_cap", "bracket_fungus_top", "bracket_fungus_side", "shelfcap_cap", "sporestalk_cap", "glowgill")


def material_of(name):
    if any(k in name for k in CLOTH):
        return "cloth"
    if any(k in name for k in CLAY):
        return "clay"
    if any(k in name for k in METAL):
        return "metal"
    if any(k in name for k in WOOD):
        return "wood"
    if any(k in name for k in ("basin", "budding_glimmer", "chime_crystal", "lanterncoral", "starlily", "amber_resin", "resin_knot")):
        return None   # natural/crystalline: handled as rock-like with features
    return "stone"


SYMBOLIC = ("glyph_tile", "rune_plate", "heavy_plate", "brazier_side", "memory_stone", "rune_dial", "memory_pillar", "inscription",
            "key_lock", "keystone", "offering", "ancient_lamp", "ancient_seal", "guardian_altar", "mosaic", "silent_mask", "wright_bust",
            "gilded_idol", "pedestal", "medicine_cabinet", "storage_cabinet", "expedition_cache", "supply_crate", "pantry", "kitchen_counter")


def legible(feats, base):
    """Symbols must stay readable: low-contrast marks become carved (darker) or inlaid (brighter) versions of themselves."""
    lb = sum(base) / 3
    out = {}
    for xy, c in feats.items():
        l = sum(c) / 3
        if abs(l - lb) < 70:
            c = shade(c, 0.45) if l <= lb else shade(c, 1.6)
        out[xy] = c
    return out


def remaster(old, base):
    """Keeps every pixel position of the original (symbols are preserved 1:1) and re-maps its colours onto the new
    material ramp by luminance; saturated accents (glows, inlays) keep their hue but are re-shaded."""
    p = ramp(base, 7, lo=0.5, hi=1.3)
    lums = [sum(c[:3]) / 3 for c in old.getdata() if c[3] > 128]
    lo, hi = min(lums), max(lums)
    img = new()
    bh, bs, bv = colorsys.rgb_to_hsv(*(q / 255 for q in base))
    for y in range(16):
        for x in range(16):
            c = old.getpixel((x, y))
            if c[3] < 128:
                continue
            h, sat, v = colorsys.rgb_to_hsv(*(q / 255 for q in c[:3]))
            l = (sum(c[:3]) / 3 - lo) / max(1, hi - lo)
            if sat > 0.35 and abs(h - bh) > 0.06 and dist(c, base) > 60:
                put(img, x, y, c)                      # accent / glow / inlay colour kept
            else:
                put(img, x, y, p[min(6, max(0, round(l * 6)))])
    return img


def paint(name, seed):
    old = original(name)
    if old is None or old.size != (16, 16):
        return None
    base = dominant(old)
    if name in GROUND:
        kind, acc = GROUND[name]
        return ground(kind, base, seed, acc)
    if name in CAPS:
        return mushroom_block(base, seed, spots="gill" not in name and "side" not in name)
    if name in ("cragpine_leaves", "golden_leaves"):
        return leaves(ramp(base, 6, hue_shift=10), seed)
    if name in ("petrified_wood", "rotting_log", "stripped_cinderbark_log", "fungal_stem"):
        return log_side(ramp(base, 6), seed, ridges=3 if "stripped" in name else 5)
    if name.endswith("_top") and any(k in name for k in ("petrified_wood", "rotting_log", "stripped_cinderbark_log", "fungal_stem")):
        return log_top(ramp(base, 6), ramp(shade(base, 0.7), 6), seed)
    mat = material_of(name)
    if mat is None:
        p = ramp(base, 7, lo=0.6, hi=1.25)
        surf = rock(p, seed, n_cells=7, crack=0.2, min_d=3.6, spread=0.5)
        if "basin" in name:
            surf = strata(ramp(base, 6, hi=1.3), seed, bands=5, wobble=1.5, crack=0.0)
        return inlay(surf, features(old, base), depth=False)
    if "brick" in name or name.startswith(("ancient_side", "clay_oven", "crumbling")):
        mat = "brick" if mat == "stone" else mat
    surf = surface(mat, base, seed)
    if any(k in name for k in SYMBOLIC):
        return remaster(old, base)
    return inlay(surf, features(old, base))
