"""Stage 11.5 batch 5: item icons (foods, drinks, salves, ingots, gems, jewellery, kits, arrows, everything else).

Common item kinds are redrawn from hand-drawn archetype sprites coloured with the item's identity colour (sampled from
its pre-11.5 icon). Unique/named items keep their exact silhouette and are remastered (re-shaded on a ramp of their own
colours, with a clean dark outline), so nothing recognisable is lost.
"""
import colorsys
import io
import os
import subprocess

from PIL import Image

from texlib import hx, ramp, shade, h01, new, put, sprite, outline

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
BASE_COMMIT = "c098626"


def original(name):
    try:
        raw = subprocess.run(["git", "show", f"{BASE_COMMIT}:src/main/resources/assets/underdeep/textures/item/{name}.png"],
                             cwd=ROOT, capture_output=True, check=True).stdout
        return Image.open(io.BytesIO(raw)).convert("RGBA")
    except Exception:
        return None


def identity(img, exclude=None):
    """The item's characteristic colour: the most saturated common colour (ignoring outline/near-greys, and colours the
    exclude(h, s, v) predicate rejects, e.g. the wooden bowl or the gold band)."""
    best, score = (150, 150, 150), -1
    counts = {}
    for c in img.getdata():
        if c[3] < 128:
            continue
        if exclude and exclude(*colorsys.rgb_to_hsv(*(q / 255 for q in c[:3]))):
            continue
        k = (c[0] // 24, c[1] // 24, c[2] // 24)
        counts.setdefault(k, []).append(c[:3])
    for k, cs in counts.items():
        c = cs[len(cs) // 2]
        h, s, v = colorsys.rgb_to_hsv(*(q / 255 for q in c))
        sc = len(cs) * (0.3 + s) * (0.3 + v)
        if v > 0.2 and sc > score:
            best, score = c, sc
    return best


# --------------------------------------------------------------------------------------------- archetype sprites
# roles: o outline; 1-5 identity ramp (dark..light); a-e container ramp (dark..light); w white highlight; s steam/sheen

ART = {
    "bowl": ["................", "................", "................", "................", "......s..s......", ".......s..s.....",
             "................", "..oooooooooooo..", ".o334455544333o.", ".o255555555442o.", ".oeoooooooooodo.", "..odddddddddco..",
             "...occcccccbo...", "....obbbbbbo....", ".....oooooo.....", "................"],
    "flask": ["................", "......oooo......", "......oeeo......", "......odco......", ".......oo.......", "......oddo......",
              ".....o4wwdo.....", "....o45w53do....", "...o4555433do...", "...o5555443do...", "...o4554332co...", "...o4443322co...",
              "....o33222co....", ".....oooooo.....", "................", "................"],
    "bottle": ["................", ".......oo.......", "......odeo......", "......odco......", "......oddo......", ".....oddddo.....",
               ".....o5wd3o.....", ".....o54w3o.....", ".....o5443o.....", ".....o5433o.....", ".....o4432o.....", ".....o4332o.....",
               ".....o3322o.....", ".....oooooo.....", "................", "................"],
    "mug": ["................", ".....s.s........", "......s.s.......", "................", "...ooooooooo....", "...oeeeeeeeooo..",
            "...o4455544o.do.", "...odddddddo..o.", "...odcddddcoo.o.", "...odddddddo.do.", "...ocddddcdooo..", "...occcccccco...",
            "....ooooooooo...", "................", "................", "................"],
    "jar": ["................", "................", ".....oooooo.....", "....oeeeeeeo....", "....odddddco....", "....oooooooo....",
            "...o54444433o...", "...o5wwww433o...", "...o44444332o...", "...o44444332o...", "...o43333322o...", "...o33333221o...",
            "....oooooooo....", "................", "................", "................"],
    "loaf": ["................", "................", "................", "................", "................", ".....oooooo.....",
             "...oo545w545oo..", "..o45545545454o.", ".o4553553553533o", ".o4444444444443o", ".o3443344334433o", ".o2333333333332o",
             "..o2222222222o..", "...oooooooooo...", "................", "................"],
    "pie": ["................", "................", "................", "................", "................", "....oooooooo....",
            "..oocc5cc5ccoo..", ".ocddc5cc5cddco.", "oddd5dd5dd5dddco", "odcd55dddd55dcco", "obddddddddddddbo", "ocbbbbbbbbbbbbco",
            ".oooooooooooooo.", "................", "................", "................"],
    "cookie": ["................", "................", "................", "................", ".....oooooo.....", "....o544544o....",
               "...o54415445o...", "...o45444414o...", "...o44144444o...", "...o34443143o...", "...o33333333o...", "....o222222o....",
               ".....oooooo.....", "................", "................", "................"],
    "ingot": ["................", "................", "................", "................", "................", "......oooooooo..",
              ".....o55555554o.", "....o5555555543o", "...ooooooooo433o", "...o444444443o3o", "...o5ww5444433oo", "...o444444443o..",
              "...o333333332o..", "...oooooooooo...", "................", "................"],
    "raw": ["................", "................", "................", "................", "......ooo.......", "....oo545oo.....",
            "...o5545444o....", "..o54455w444o...", "..o4544w44433o..", "..o44444443322o.", "..o3443433322o..", "...o33322221o...",
            "....oo2221oo....", "......ooo.......", "................", "................"],
    "gem": ["................", "................", "................", "....oooooooo....", "...o5w555544o...", "..o5w55555443o..",
            "..oooooooooooo..", "..o5544444333o..", "...o54444433o...", "....o444433o....", ".....o4433o.....", "......o33o......",
            ".......oo.......", "................", "................", "................"],
    "ring": ["................", "................", "................", "......o55o......", ".....o5ww4o.....", "......o44o......",
             "....oocccdoo....", "...oec....cdo...", "..oec......cdo..", "..oe........do..", "..oe........co..", "..odc......cbo..",
             "...odc....cbo...", "....oodddboo....", "......oooo......", "................"],
    "amulet": ["....oo....oo....", "...oe......co...", "..oe........co..", "..oe........co..", "...oe......co...", "....oe....co....",
               ".....oeooco.....", "......oddo......", ".....oo55oo.....", "....o55w544o....", "....o5w5443o....", "....o544433o....",
               ".....o4433o.....", "......oooo......", "................", "................"],
    "kit": ["................", "................", "......oooo......", ".....oe..co.....", "..oooooooooooo..", ".oddddddddddddo.",
            ".odeeeeeeeeeedo.", ".odd5555555ddcdo".replace("cd", "dd"), ".odd5w44443dddo.", ".odddddddddddco.", ".occcddddddccco.",
            ".obbbbbbbbbbbbo.", "..oooooooooooo..", "................", "................", "................"],
    "arrow": ["................", "............oo..", "...........o5wo.", "..........o544o.", ".........oc43o..", "........oco3o...",
              ".......oco.o....", "......oco.......", ".....oco........", "....oco.........", "..ooco..........", ".o3oo...........",
              ".o23o...........", "..ooo...........", "................", "................"],
    "pouch": ["................", "................", "......oooo......", ".....o3443o.....", "......oeeo......", "....oo4444oo....",
              "...o44555444o...", "..o4455w54443o..", "..o4455554443o..", "..o4444444333o..", "..o3444444332o..", "...o33333322o...",
              "....oooooooo....", "................", "................", "................"],
}

CONTAINER = {"bowl": hx("8a5a3a"), "flask": hx("c8d8e0"), "bottle": hx("a8c0c8"), "mug": hx("8a6a4a"), "jar": hx("c8a878"), "pie": hx("c89a5a"),
             "ring": hx("d8b060"), "amulet": hx("b89a5a"), "kit": hx("6a4a30"), "arrow": hx("8a6a44")}

KEYWORDS = [
    ("bowl", ("stew", "soup", "broth", "chowder", "porridge", "risotto", "salad", "rice_bowl", "hotpot", "goulash", "mash", "compote", "fruit_salad",
              "trail_mix", "roast_platter", "spore_paste")),
    ("mug", ("_tea", "warm_milk", "kvass", "cider", "kombucha", "fizz", "cooler")),
    ("flask", ("tonic", "draught", "tincture", "elixir", "antiserum", "infusion", "smelling_salts", "vinegar", "creature_oil")),
    ("bottle", ("juice", "nectar")),
    ("jar", ("salve", "balm", "poultice", "paste", "butter", "pickled", "grounding_paste", "mineral_lick", "compost", "fungal_feed", "grain_feed")),
    ("pie", ("_pie", "tart")),
    ("loaf", ("bread", "loaf", "flatbread", "hardtack", "dough", "rice_cake", "sweet_roll", "pastry", "sandwich", "fish_wrap", "tide_roll",
              "ration", "capback_cheese")),
    ("cookie", ("cookie",)),
    ("ingot", ("_ingot",)),
    ("raw", ("raw_", "crushed_", "cinnabar", "niter", "sulfur", "emberite", "galvanite", "cryolite", "jasper", "malachite", "salt", "spice_blend",
             "flour", "fossil_fragment", "shell_fragment", "amber_glob", "resin")),
    ("gem", ("cut_", "polished_", "prismatic_opal", "star_shard", "glimmer_shard", "crystal_husk", "resonant_crystal", "seamglass", "pearl")),
    ("ring", ("_ring", "_band", "torc", "oath_band")),
    ("amulet", ("amulet", "pendant", "charm", "locket", "talisman")),
    ("arrow", ("_arrow",)),
    ("pouch", ("herb_pouch", "ore_satchel", "backpack")),
]

# never redrawn from an archetype: unique, named, progression or spoiler-sensitive items keep their exact silhouette
UNIQUE = ("sign_", "keystone", "vault_key", "warden", "heart", "crown", "lens", "mantle", "censer", "veil", "horn", "gauntlet", "blade", "fang",
          "harpoon", "trident", "hammer", "lantern", "relic", "lore_fragment", "ancient_", "somnal", "wright_", "drowned", "mossback", "rimefang",
          "silent", "seamglass", "everlit", "handbook", "journal", "chart", "compass", "astrolabe", "idol")


def kind_of(name):
    if any(k in name for k in UNIQUE):
        return None
    for kind, keys in KEYWORDS:
        if any(k in name for k in keys):
            return kind
    return None


def archetype(kind, col, seed):
    ident = ramp(col, 5, lo=0.45, hi=1.35)
    cont = ramp(CONTAINER.get(kind, col), 5, lo=0.5, hi=1.25)
    pal = {"o": shade(ident[0], 0.4), "1": ident[0], "2": ident[1], "3": ident[2], "4": ident[3], "5": ident[4], "w": (250, 250, 244),
           "s": (220, 220, 220), "a": cont[0], "b": cont[1], "c": cont[2], "d": cont[3], "e": cont[4]}
    if kind in ("ingot", "raw", "gem", "cookie", "loaf"):
        pal["o"] = shade(ident[0], 0.35)
    rows = [(r + "................")[:16] for r in ART[kind]]
    img = sprite(rows, pal)
    if kind in ("ring", "amulet"):     # the gem takes the identity colour; the metal is the container ramp
        pass
    return img


def remaster(old):
    """Keep the exact silhouette; re-shade every opaque texel onto a ramp of its own hue (top-left lit), then outline."""
    img = new()
    w, h = old.size
    if (w, h) != (16, 16):
        return None
    lums = [sum(c[:3]) / 3 for c in old.getdata() if c[3] > 128]
    if not lums:
        return None
    for y in range(16):
        for x in range(16):
            c = old.getpixel((x, y))
            if c[3] < 128:
                continue
            light = 1.0 + 0.08 * ((8 - x) + (8 - y)) / 8          # gentle top-left lighting
            # an exposed upper-left edge gets a highlight, a lower-right edge a shadow
            up = y == 0 or old.getpixel((x, y - 1))[3] < 128
            lf = x == 0 or old.getpixel((x - 1, y))[3] < 128
            dn = y == 15 or old.getpixel((x, y + 1))[3] < 128
            rt = x == 15 or old.getpixel((x + 1, y))[3] < 128
            if up or lf:
                light *= 1.15
            if dn or rt:
                light *= 0.8
            put(img, x, y, shade(c, light))
    # only outline sprites that do not already have a dark rim
    dark_rim = sum(1 for c in old.getdata() if c[3] > 128 and sum(c[:3]) < 120)
    return outline(img, shade(identity(old), 0.3)) if dark_rim < 6 else img


def paint(name):
    old = original(name)
    if old is None or old.size != (16, 16):
        return None
    kind = kind_of(name)
    if kind == "bowl":
        brown = lambda h, s_, v: 0.03 < h < 0.13 and s_ < 0.75 and v < 0.62       # noqa: E731
        food = identity(old, brown)
        img = archetype(kind, food, 0)
        second = identity(old, lambda h, s_, v: brown(h, s_, v) or abs(h - colorsys.rgb_to_hsv(*(q / 255 for q in food))[0]) < 0.06)
        seed = sum(map(ord, name))
        for k, x in enumerate((3, 6, 9, 11)):           # ingredient pieces floating in the dish
            if h01(seed, k) < 0.75:
                put(img, x, 8, shade(second, 1.1)); put(img, x + 1, 9, shade(second, 0.8))
        return img
    if kind == "ring":
        gold = lambda h, s_, v: 0.08 < h < 0.18 and s_ > 0.35       # noqa: E731
        band = next((c for k, c in (("copper", hx("c8743a")), ("galvanic", hx("c8743a")), ("starsilver", hx("c8d4ec")), ("opal", hx("c8ccd8")),
                                      ("rime", hx("a8d0e8")), ("frost", hx("a8d0e8")), ("umbral", hx("6a4a98")), ("ember", hx("5a5262")),
                                      ("storm", hx("8a94b8"))) if k in name), hx("d8b060"))
        CONTAINER["ring"] = band
        img = archetype(kind, identity(old, gold), 0)
        CONTAINER["ring"] = hx("d8b060")
        return img
    if kind:
        return archetype(kind, identity(old), sum(map(ord, name)))
    return remaster(old)
