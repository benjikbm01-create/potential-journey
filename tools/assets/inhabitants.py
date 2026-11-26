"""Stage 4 inhabitant assets: 64x64 player-layout skins per civilization (6 variants each), profession outfit
overlays (greyscale, tinted per civilization at render time), and names for civilizations, settlement
categories, professions and messages. Called from stage2_assets.py main(); returns lang entries.

Original procedural pixel art in the Minecraft skin layout.
"""
import os
import random
import zlib

from PIL import Image

import generate_assets as base

OUT = os.path.join(base.ASSETS, "textures", "entity", "inhabitant")

# skin layout rectangles: (x, y, w, h) for faces of each part
# head 8x8x8 at (0,0); body 8x12x4 at (16,16); right arm 4x12x4 at (40,16); right leg at (0,16);
# left leg at (16,48); left arm at (32,48); overlays: hat (32,0), jacket (16,32), sleeves (40,32)/(48,48),
# pants (0,32)/(0,48).


def faces(ox, oy, w, h, d):
    """Returns dict of face -> (x, y, fw, fh) for a box with UV origin (ox, oy)."""
    return {
        "top": (ox + d, oy, w, d), "bottom": (ox + d + w, oy, w, d),
        "right": (ox, oy + d, d, h), "front": (ox + d, oy + d, w, h),
        "left": (ox + d + w, oy + d, d, h), "back": (ox + 2 * d + w, oy + d, w, h),
    }


HEAD = faces(0, 0, 8, 8, 8)
HAT = faces(32, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
JACKET = faces(16, 32, 8, 12, 4)
RARM = faces(40, 16, 4, 12, 4)
LARM = faces(32, 48, 4, 12, 4)
RLEG = faces(0, 16, 4, 12, 4)
LLEG = faces(16, 48, 4, 12, 4)
RSLEEVE = faces(40, 32, 4, 12, 4)
LSLEEVE = faces(48, 48, 4, 12, 4)


def fill(img, rect, color, rnd=None, var=0.06):
    x, y, w, h = rect
    for i in range(w):
        for j in range(h):
            c = color
            if rnd is not None and var > 0:
                f = 1 + (rnd.random() - 0.5) * var * 2
                c = tuple(max(0, min(255, int(v * f))) for v in color[:3]) + (color[3] if len(color) > 3 else 255,)
            img.putpixel((x + i, y + j), c if len(c) == 4 else c + (255,))


def part(img, fs, color, rnd, var=0.06, only=None):
    for name, r in fs.items():
        if only is None or name in only:
            fill(img, r, color, rnd, var)


def rows(img, rect, y0, y1, color, rnd=None, var=0.05):
    x, y, w, h = rect
    for j in range(max(0, y0), min(h, y1)):
        for i in range(w):
            c = color
            if rnd is not None:
                f = 1 + (rnd.random() - 0.5) * var * 2
                c = tuple(max(0, min(255, int(v * f))) for v in color[:3])
            img.putpixel((x + i, y + j), c + (255,))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


SKIN_TONES = [(239, 200, 170), (224, 172, 135), (198, 140, 100), (160, 105, 72), (122, 80, 55), (90, 60, 42)]

CIVS = {
    "rootfolk": dict(skin=[0, 1, 2, 3, 1, 2], hair=[(92, 60, 30), (60, 90, 40), (140, 90, 40), (40, 30, 20), (110, 130, 60), (170, 120, 60)],
                     shirt=(70, 120, 50), shirt2=(110, 80, 45), pants=(95, 70, 45), boots=(60, 45, 30), trim=(150, 120, 60)),
    "forgekin": dict(skin=[2, 3, 1, 4, 3, 2], hair=[(40, 30, 25), (90, 40, 25), (25, 25, 25), (120, 60, 30), (60, 50, 45), (150, 90, 50)],
                     shirt=(60, 58, 62), shirt2=(150, 75, 35), pants=(45, 42, 45), boots=(30, 28, 28), trim=(185, 110, 60), beard=True),
    "tide_dwellers": dict(skin=[1, 2, 0, 3, 2, 1], hair=[(30, 30, 40), (200, 180, 120), (90, 60, 40), (60, 40, 30), (150, 150, 160), (40, 60, 80)],
                          shirt=(230, 230, 220), shirt2=(40, 120, 140), pants=(40, 55, 90), boots=(70, 55, 40), trim=(40, 120, 140), stripes=True),
    "sporefolk": dict(skin=[(214, 196, 220), (200, 180, 205), (190, 170, 200), (220, 205, 215), (180, 160, 190), (205, 190, 210)],
                      hair=[(120, 60, 140), (80, 50, 100), (160, 90, 170), (60, 40, 80), (190, 120, 190), (100, 70, 120)],
                      shirt=(110, 60, 130), shirt2=(200, 160, 90), pants=(80, 50, 90), boots=(60, 45, 55), trim=(220, 190, 110), cap=True),
    "glimmerkin": dict(skin=[(222, 215, 236), (205, 198, 225), (190, 185, 215), (230, 225, 240), (180, 170, 205), (210, 205, 230)],
                       hair=[(230, 230, 245), (150, 160, 220), (90, 100, 170), (200, 170, 230), (60, 70, 120), (240, 220, 250)],
                       shirt=(60, 80, 170), shirt2=(200, 200, 230), pants=(50, 55, 100), boots=(40, 40, 70), trim=(170, 120, 220), sparkle=True),
    "stoneborn": dict(skin=[1, 2, 0, 3, 2, 4], hair=[(110, 80, 50), (70, 60, 55), (170, 120, 70), (40, 35, 30), (140, 140, 140), (90, 50, 30)],
                      shirt=(140, 50, 40), shirt2=(120, 115, 110), pants=(80, 75, 70), boots=(50, 45, 40), trim=(200, 190, 170), beard=True, fur=True),
    "amberfolk": dict(skin=[0, 1, 2, 1, 3, 0], hair=[(200, 150, 60), (150, 90, 40), (90, 60, 30), (230, 190, 110), (60, 40, 25), (180, 110, 50)],
                      shirt=(240, 235, 220), shirt2=(220, 150, 40), pants=(120, 80, 45), boots=(85, 55, 30), trim=(200, 110, 40)),
    "astrals": dict(skin=[(225, 228, 240), (210, 215, 232), (235, 235, 245), (200, 205, 225), (220, 222, 238), (190, 198, 220)],
                    hair=[(235, 240, 250), (190, 205, 235), (170, 180, 210), (240, 230, 250), (210, 220, 240), (150, 170, 210)],
                    shirt=(150, 180, 230), shirt2=(240, 242, 250), pants=(120, 140, 190), boots=(90, 100, 140), trim=(200, 210, 235), robe=True, stars=True),
}


def skin_color(spec, i):
    s = spec["skin"][i % len(spec["skin"])]
    return SKIN_TONES[s] if isinstance(s, int) else s


def paint_skin(civ, spec, variant):
    rnd = random.Random(zlib.crc32(f"{civ}/{variant}".encode()))
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = skin_color(spec, variant)
    hair = spec["hair"][variant % len(spec["hair"])]
    shirt = spec["shirt"] if variant % 2 == 0 else spec["shirt2"]
    accent = spec["shirt2"] if variant % 2 == 0 else spec["shirt"]
    # head
    part(img, HEAD, skin, rnd, 0.04)
    part(img, HEAD, hair, rnd, 0.08, only=("top", "back"))
    for face in ("right", "left"):
        rows(img, HEAD[face], 0, 3 + variant % 2, hair, rnd, 0.08)
    rows(img, HEAD["front"], 0, 2, hair, rnd, 0.08)
    fx, fy, _, _ = HEAD["front"]
    eye = (60, 50, 40) if civ not in ("glimmerkin", "astrals") else (90, 110, 200)
    if civ == "sporefolk":
        eye = (120, 50, 130)
    for ex in (1, 5):
        img.putpixel((fx + ex, fy + 4), (250, 250, 250, 255))
        img.putpixel((fx + ex + 1, fy + 4), eye + (255,))
    img.putpixel((fx + 3, fy + 6), shade(skin, 0.8) + (255,))
    img.putpixel((fx + 4, fy + 6), shade(skin, 0.8) + (255,))
    if spec.get("beard") and variant % 3 != 2:
        rows(img, HEAD["front"], 5, 8, hair, rnd, 0.1)
        img.putpixel((fx + 3, fy + 6), shade(hair, 0.7) + (255,))
        img.putpixel((fx + 4, fy + 6), shade(hair, 0.7) + (255,))
    if spec.get("cap"):
        # mushroom cap (hat layer): coloured top with pale spots, rim around the sides
        capc = [(170, 60, 160), (90, 160, 190), (200, 110, 60), (130, 80, 170), (60, 150, 140), (190, 70, 90)][variant]
        part(img, HAT, capc, rnd, 0.06, only=("top",))
        for face in ("right", "left", "front", "back"):
            rows(img, HAT[face], 0, 2, capc, rnd, 0.06)
        x, y, w, h = HAT["top"]
        for _ in range(6):
            img.putpixel((x + rnd.randrange(w), y + rnd.randrange(h)), (240, 235, 220, 255))
    if spec.get("robe"):
        hood = shade(spec["shirt"], 0.9)
        for face in ("top", "back", "right", "left"):
            part(img, HAT, hood, rnd, 0.05, only=(face,))
        rows(img, HAT["front"], 0, 1, hood, rnd)
    # body and arms
    part(img, BODY, shirt, rnd, 0.06)
    part(img, RARM, shirt, rnd, 0.06)
    part(img, LARM, shirt, rnd, 0.06)
    for arm in (RARM, LARM):
        for face in ("front", "back", "right", "left"):
            rows(img, arm[face], 9, 12, skin, rnd, 0.04)  # hands
        fill(img, arm["bottom"], skin)
    if spec.get("stripes"):
        for face in ("front", "back"):
            x, y, w, h = BODY[face]
            for j in range(0, h, 2):
                for i in range(w):
                    img.putpixel((x + i, y + j), spec["shirt2"] + (255,))
    rows(img, BODY["front"], 8, 9, spec["trim"])  # belt
    rows(img, BODY["back"], 8, 9, spec["trim"])
    if spec.get("fur"):
        rows(img, BODY["front"], 0, 2, (200, 190, 170), rnd, 0.1)
        for face in ("right", "left", "back"):
            rows(img, BODY[face], 0, 2, (200, 190, 170), rnd, 0.1)
    if spec.get("sparkle") or spec.get("stars"):
        for f in (BODY, RARM, LARM):
            x, y, w, h = f["front"]
            for _ in range(3):
                img.putpixel((x + rnd.randrange(w), y + rnd.randrange(8)), (250, 250, 255, 255))
    # legs
    for leg in (RLEG, LLEG):
        part(img, leg, spec["pants"], rnd, 0.06)
        for face in ("front", "back", "right", "left"):
            rows(img, leg[face], 9, 12, spec["boots"], rnd, 0.05)
        fill(img, leg["bottom"], spec["boots"])
    if spec.get("robe"):
        for leg in (RLEG, LLEG):
            for face in ("front", "back", "right", "left"):
                rows(img, leg[face], 0, 9, spec["shirt"], rnd, 0.05)
    # accent sash / collar
    x, y, w, h = BODY["front"]
    for j in range(h):
        i = (j * 2 // 3) % w
        if j < 8:
            img.putpixel((x + i, y + j), accent + (255,))
    return img


OUTFITS = {
    "apron": "craft", "robe": "learned", "armor": "guard", "farmer": "farming", "vest": "trade", "coat": "water",
}


def paint_outfit(kind):
    rnd = random.Random(zlib.crc32(kind.encode()))
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    light, mid, dark = (235, 235, 235), (185, 185, 185), (120, 120, 120)
    if kind == "apron":
        x, y, w, h = BODY["front"]
        for j in range(3, h):
            for i in range(1, w - 1):
                img.putpixel((x + i, y + j), (mid if j > 3 else dark) + (255,))
        rows(img, BODY["front"], 8, 9, dark)
        x, y, w, h = BODY["front"]
        img.putpixel((x + 2, y + 10), (90, 90, 90, 255))
        img.putpixel((x + 5, y + 10), (90, 90, 90, 255))
        for leg in (RLEG, LLEG):
            rows(img, leg["front"], 0, 6, mid)
    elif kind == "robe":
        for f in (BODY, RARM, LARM):
            part(img, f, light, rnd, 0.04)
        for leg in (RLEG, LLEG):
            for face in ("front", "back", "right", "left"):
                rows(img, leg[face], 0, 10, light, rnd)
        rows(img, BODY["front"], 8, 9, dark)
        x, y, w, h = BODY["front"]
        for j in range(h):
            img.putpixel((x + 3, y + j), mid + (255,))
            img.putpixel((x + 4, y + j), mid + (255,))
    elif kind == "armor":
        for f in (BODY,):
            part(img, f, light, rnd, 0.08)
        for f in (RARM, LARM):
            for face in ("front", "back", "right", "left", "top"):
                rows(img, f[face], 0, 4, light, rnd)
        for face in ("top", "right", "left", "back"):
            part(img, HEAD, mid, rnd, 0.08, only=(face,))
        rows(img, HEAD["front"], 0, 2, mid)
        rows(img, BODY["front"], 8, 9, dark)
    elif kind == "farmer":
        # straw hat brim and crown, overall straps
        straw = (230, 220, 160)
        part(img, HEAD, straw, rnd, 0.1, only=("top",))
        for face in ("right", "left", "front", "back"):
            rows(img, HEAD[face], 0, 2, straw, rnd, 0.1)
        x, y, w, h = BODY["front"]
        for j in range(h):
            img.putpixel((x + 1, y + j), mid + (255,))
            img.putpixel((x + 6, y + j), mid + (255,))
        rows(img, BODY["front"], 6, 12, mid)
        for leg in (RLEG, LLEG):
            for face in ("front", "back", "right", "left"):
                rows(img, leg[face], 0, 9, mid, rnd)
    elif kind == "vest":
        x, y, w, h = BODY["front"]
        for j in range(0, 9):
            for i in list(range(0, 3)) + list(range(5, 8)):
                img.putpixel((x + i, y + j), mid + (255,))
        part(img, BODY, mid, rnd, 0.05, only=("back", "right", "left"))
        rows(img, BODY["front"], 8, 10, light)
    elif kind == "coat":
        for f in (BODY, RARM, LARM):
            part(img, f, mid, rnd, 0.06)
        part(img, HEAD, light, rnd, 0.06, only=("top",))
        for face in ("right", "left", "back"):
            rows(img, HEAD[face], 0, 3, light, rnd)
        rows(img, HEAD["front"], 0, 1, light)
        for f in (RARM, LARM):
            for face in ("front", "back", "right", "left"):
                rows(img, f[face], 10, 12, (0, 0, 0))
    # clear hands so skin shows (transparent where black placeholder)
    for x in range(64):
        for y in range(64):
            if img.getpixel((x, y))[:3] == (0, 0, 0) and img.getpixel((x, y))[3] == 255:
                img.putpixel((x, y), (0, 0, 0, 0))
    return img


PROFESSION_NAMES = {
    "farmer": "Farmer", "herbalist": "Herbalist", "forester": "Forester", "carpenter": "Carpenter", "cook": "Cook", "baker": "Baker",
    "brewer": "Brewer", "blacksmith": "Blacksmith", "miner": "Miner", "smelter": "Smelter", "engineer": "Engineer", "fisher": "Fisher",
    "boatwright": "Boatwright", "navigator": "Navigator", "alchemist": "Alchemist", "mycologist": "Mycologist", "healer": "Healer",
    "gemcutter": "Gemcutter", "scholar": "Scholar", "cartographer": "Cartographer", "stonemason": "Stonemason", "hunter": "Hunter",
    "guard": "Guard", "merchant": "Merchant", "tailor": "Tailor", "innkeeper": "Innkeeper", "rancher": "Rancher", "artisan": "Artisan",
    "astronomer": "Astronomer", "lampwright": "Lampwright",
}

CIV_NAMES = {
    "rootfolk": "Rootfolk", "forgekin": "Forgekin", "tide_dwellers": "Tide Dwellers", "sporefolk": "Sporefolk",
    "glimmerkin": "Glimmerkin", "stoneborn": "Stoneborn", "amberfolk": "Amberfolk", "astrals": "Astrals",
}

CATEGORY_NAMES = {"encampment": "Encampment", "outpost": "Outpost", "village": "Village", "town": "Town", "city": "City"}


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(os.path.join(OUT, "outfit"), exist_ok=True)
    for civ, spec in CIVS.items():
        for v in range(6):
            paint_skin(civ, spec, v).save(os.path.join(OUT, f"{civ}_{v}.png"))
    for kind in OUTFITS:
        paint_outfit(kind).save(os.path.join(OUT, "outfit", f"{kind}.png"))
    lang = {}
    for k, v in CIV_NAMES.items():
        lang[f"civ.underdeep.{k}"] = v
    for k, v in CATEGORY_NAMES.items():
        lang[f"settlement.underdeep.{k}"] = v
    for k, v in PROFESSION_NAMES.items():
        lang[f"profession.underdeep.{k}"] = v
    lang["entity.underdeep.inhabitant"] = "Inhabitant"
    lang["message.underdeep.settlement.subtitle"] = "%s %s"
    lang["message.underdeep.inhabitant.greet"] = "%s, %s"
    lang["message.underdeep.inhabitant.busy"] = "%s has nothing to trade right now."
    return lang


if __name__ == "__main__":
    print(len(main()))
