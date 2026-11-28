"""Underdeep Stage 11 creature designs: one hand-authored anatomy per species, built with creature_kit.

Each design names its silhouette idea first (what makes the species recognisable at a glance), then builds it. Units are
model pixels (16 = one block), y up from the ground, head towards -z. Hitbox sizes (blocks) are in WildlifeRegistry; the
design height / 16 * scale should roughly match them.
"""
import math

from creature_kit import Rig, quad, lizard, bug, bird, fish, eel, crab, jelly, tail, leg

DESIGNS = {}


def design(sid, scale=1.0, shadow=0.5, gait="quad", leg_len=6, glows=False):
    def wrap(fn):
        DESIGNS[sid] = dict(build=fn, scale=scale, shadow=shadow, gait=gait, leg=leg_len, glows=glows)
        return fn
    return wrap


# ---------------------------------------------------------------- shared decorations

def chain(r, name, parent, pivot, segs, first_rot, bend, mat="horn", side=0, rel=True, role="decor", zone="plain"):
    """A tapering chain of boxes growing along +y of its own frame (horns, antler beams, stalks, crystals)."""
    prev = parent
    for i, (ln, w) in enumerate(segs):
        pv = pivot if i == 0 else [0, segs[i - 1][0] - 0.3, 0]
        prev = r.part(f"{name}{i}", prev, pv, [r.box(-w / 2, 0, -w / 2, w, ln, w, mat, zone)], role, rot=first_rot if i == 0 else bend,
                      rel=rel or i > 0, side=side)
    return prev


def antlers(r, parent, x, y, z, size, mat="horn", tines=2, moss=None):
    for s, sx in ((0, 1), (1, -1)):
        beam = chain(r, f"antler{s}_", parent, [sx * x, y, z], [(size * 0.45, 1.2), (size * 0.4, 1), (size * 0.35, 0.8)],
                     (-0.35, 0, sx * 0.55), (0.45, 0, sx * 0.1), mat, s)
        for t in range(tines):
            r.part(f"tine{s}_{t}", f"antler{s}_{t}", [0, size * 0.25, 0], [r.box(-0.4, 0, -0.4, 0.8, size * 0.3, 0.8, mat, "plain")], "decor",
                   rot=(0.9, 0, sx * 0.25), rel=True)
        if moss:
            r.part(f"antlermoss{s}", f"antler{s}_0", [0, size * 0.2, 0], [r.box(-1, 0, -1, 2, 1.2, 2, moss, "plain")], "decor", rel=True)
        _ = beam


def horn_pair(r, parent, x, y, z, segs, first, bend, mat="horn"):
    for s, sx in ((0, 1), (1, -1)):
        f = (first[0], first[1] * sx, first[2] * sx)
        b = (bend[0], bend[1] * sx, bend[2] * sx)
        chain(r, f"horn{s}_", parent, [sx * x, y, z], segs, f, b, mat, s, role="crest")


def cap(r, name, parent, pivot, stem_h, stem_w, cap_w, cap_h, rot=(0, 0, 0), mat="cap", stem="stem", rel=True, glow_dots=None):
    r.part(name, parent, pivot, [r.box(-stem_w / 2, 0, -stem_w / 2, stem_w, stem_h, stem_w, stem, "plain"),
                                 r.box(-cap_w / 2, stem_h, -cap_w / 2, cap_w, cap_h, cap_w, mat, "plain"),
                                 r.box(-cap_w / 2 + 1, stem_h + cap_h, -cap_w / 2 + 1, cap_w - 2, 1, cap_w - 2, mat, "plain")] if cap_w > 3 else
           [r.box(-stem_w / 2, 0, -stem_w / 2, stem_w, stem_h, stem_w, stem, "plain"),
            r.box(-cap_w / 2, stem_h, -cap_w / 2, cap_w, cap_h, cap_w, mat, "plain")], "decor", rot=rot, rel=rel)
    if glow_dots:
        r.mark(name, "up", "glowdots", glow_dots, box=1)
        r.mark(name, "north", "glowdots", glow_dots, box=1)


def crystal(r, name, parent, pivot, h, w, rot, mat="crystal", rel=True):
    r.part(name, parent, pivot, [r.box(-w / 2, 0, -w / 2, w, h, w, mat, "plain"),
                                 r.box(-w / 2 + 0.3, h, -w / 2 + 0.3, w - 0.6, min(1.5, h * 0.25), w - 0.6, mat, "plain")], "decor", rot=rot, rel=rel)


def spines(r, parent, y, z0, z1, n, h, mat="spine", w=1.0, lean=0.35, x=0.0, role="crest"):
    for i in range(n):
        z = z0 + (z1 - z0) * i / max(1, n - 1)
        hh = h * (0.7 + 0.3 * math.sin(math.pi * (i + 0.5) / n))
        r.part(f"spine{i}", parent, [x, y, z], [r.box(-w / 2, 0, -w / 2, w, hh, w, mat, "plain")], role, rot=(-lean, 0, 0), rel=True)


def pal(**kw):
    kw.setdefault("hoof", (52, 44, 40))
    kw.setdefault("paw", tuple(max(0, int(c * 0.6)) for c in kw["base"]))
    kw.setdefault("horn", (214, 204, 178))
    kw.setdefault("dark", (34, 26, 24))
    kw.setdefault("eye", (26, 20, 18))
    return kw


# ================================================================ ROOTWILD

@design("mossbuck", leg_len=12, shadow=0.6)
def mossbuck(r: Rig):
    """Slender deer: long legs, raised neck, branching mossy antlers, white-spotted back, short flag tail."""
    r.pal.update(pal(base=(132, 96, 64), belly=(214, 196, 160), back=(104, 74, 50), mark=(226, 212, 170), muzzle=(96, 72, 52),
                     ear=(150, 112, 80), horn=(196, 180, 150), moss=(84, 132, 58), pattern="spots"))
    quad(r, 14, (6, 7), (6, 6.5), 14.5, [(0.5, 2, (0.4, 1.4), 1.5), (0.48, 2, (0.4, 1.4), 1.5)],
         (7, 3, 3.6, 0.95), (4, 4, 4.5, 3, 3, 2.6), tail_spec=(1, [2.5], [2], -0.6), ears=(3, 1.6, 1.0), stance=0.25)
    antlers(r, "head", 1.2, 1.8, -2.6, 8, tines=2, moss="moss")
    r.part("mane", "neck", [0, 1.4, -6], [r.box(-1, 0, 0, 2, 1.2, 6.5, "moss", "plain")], "decor", rel=True)


@design("thumperkit", leg_len=3, shadow=0.3)
def thumperkit(r: Rig):
    """Round hopping burrower: huge folded hind legs, long upright ears, cotton tail, tiny forelegs."""
    r.pal.update(pal(base=(160, 138, 108), belly=(232, 222, 202), back=(132, 112, 88), ear=(214, 170, 160), paw=(120, 100, 78), pattern="fur"))
    body = r.part("body", "root", [0, 5, 0], [r.box(-2.5, -2.5, -3.5, 5, 5, 7, "base", "body"), r.box(-3, -2.8, 0, 6, 5.3, 3.5, "base", "body")], "body",
                  rot=(-0.15, 0, 0))
    r.part("head", body, [0, 7, -3.5], [r.box(-2, -2, -3.5, 4, 4, 4, "base", "head"), r.box(-1.25, -2, -4.3, 2.5, 2, 1, "belly", "head")], "head")
    r.eye("head", "east", 1, 1, 2)
    r.eye("head", "west", -3, 1, 2)
    r.mark("head", "north", "nostrils", box=1)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"ear{s}", "head", [sx * 1.3, 1.8, -0.8], [r.box(-0.6, 0, -0.4, 1.2, 6, 0.8, "ear", "plain")], "ear", rot=(0.3, 0, sx * 0.3), rel=True, side=s)
        r.part(f"leg{s}", body, [sx * 1.5, 3, -2.5], [r.box(-0.6, -3, -0.6, 1.2, 3.2, 1.2, "base", "limb")], "leg_upper", index=0, side=s, phase=0)
        r.part(f"haunch{s}", body, [sx * 2.6, 4.5, 2], [r.box(-1, -2.5, -2.5, 2, 3.5, 4.5, "base", "limb")], "leg_upper", index=1, side=s, phase=0)
        r.part(f"haunch{s}_lower", f"haunch{s}", [0, -2.5, -1.5], [r.box(-0.9, -2, -3.5, 1.8, 1.2, 4.2, "paw", "limb")], "leg_lower", rel=True,
               index=1, side=s)
    r.part("tail0", body, [0, 6, 3.5], [r.box(-1.25, -1, 0, 2.5, 2.5, 2, "belly", "plain")], "tail", chain=0)


@design("rootglider", leg_len=3, shadow=0.3, glows=True)
def rootglider(r: Rig):
    """Flying squirrel: furred gliding membranes between the limbs, huge glowing night eyes, flat feathered tail."""
    r.pal.update(pal(base=(118, 102, 88), belly=(214, 204, 186), back=(92, 80, 70), wing=(140, 118, 100), wingtip=(110, 94, 82),
                     ear=(150, 130, 112), eye=(150, 255, 190), pattern="fur", glow_mats=("eyeglow",), eyeglow=(150, 255, 190)))
    quad(r, 8, (4, 3.6), (4, 3.6), 4, [(0.5, 1.2, (0.3, 0.5)), (0.5, 1.4, (0.3, 0.5))], (0, 0, 0, 0), (4, 3.6, 3, 1.2, 2.2, 1.8),
         tail_spec=(2, [3.5, 3.5], [3, 2.6], 0.05), ears=(1.8, 1.2, 0.6), hooves=False)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"membrane{s}", "body", [sx * 2, 4.5, -3.2], [r.box(0 if s == 0 else -3.5, -0.2, 0, 3.5, 0.4, 6.4, "wing", "wing")], "wing", side=s,
               rot=(0, 0, sx * -0.2))
        r.part(f"eyeglow{s}", "head", [sx * 2.01, 0.3, -2.6], [r.box(-0.3 if s == 0 else -0.2, 0, 0, 0.5, 1.6, 1.6, "eyeglow", "plain")], "decor", rel=True)


@design("lumenmoth", gait="none", leg_len=0, shadow=0.2, glows=True)
def lumenmoth(r: Rig):
    """Moth: furry thorax, plump banded abdomen, two pairs of broad patterned wings with glowing eyespots, feather antennae."""
    r.pal.update(pal(base=(110, 112, 128), belly=(160, 160, 176), head=(130, 130, 146), accent=(80, 80, 96), wing=(150, 196, 190),
                     wingtip=(120, 170, 166), glow=(190, 255, 236), pattern="fur", glow_mats=("glowspot",), glowspot=(190, 255, 236)))
    body = r.part("body", "root", [0, 9, 0], [r.box(-1.25, -1.25, -1.5, 2.5, 2.5, 3, "base", "body")], "body")
    r.part("abdomen", body, [0, 9, 1.5], [r.box(-1, -1, 0, 2, 2, 4.5, "base", "body")], "tail", rot=(-0.15, 0, 0), chain=0)
    r.part("head", body, [0, 9.2, -1.5], [r.box(-1, -1, -1.6, 2, 2, 1.6, "head", "head")], "head")
    r.eye("head", "east", 0, 0, 1)
    r.eye("head", "west", -1, 0, 1)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"antenna{s}", "head", [sx * 0.6, 0.8, -1.4], [r.box(-0.25, 0, -3, 0.5, 0.2, 3, "accent", "plain"),
                                                              r.box(-0.8 if s == 0 else -0.2, 0, -3, 1, 0.2, 2.4, "accent", "plain")], "antenna",
               rot=(-0.6, -sx * 0.3, 0), rel=True, side=s)
        r.part(f"wing{s}", body, [sx * 1.2, 10, -1.2], [r.box(0 if s == 0 else -6, 0, -1, 6, 0.25, 5, "wing", "wing")], "wing", side=s, rot=(0, sx * 0.2, 0))
        r.part(f"wing{s}_outer", body, [sx * 1.2, 9.9, 1], [r.box(0 if s == 0 else -4.5, 0, 0, 4.5, 0.25, 4, "wingtip", "wing")], "wing", side=s,
               rot=(0, -sx * 0.25, 0))
        r.part(f"spot{s}", f"wing{s}", [sx * 3.5, 0.26, 1.5], [r.box(-0.75, 0, -0.75, 1.5, 0.05, 1.5, "glowspot", "plain")], "decor", rel=True)


@design("tuskbriar", leg_len=5, shadow=0.6)
def tuskbriar(r: Rig):
    """Boar: massive shoulders sloping to small hips, low wedge head, upcurved tusks, thorny bristle ridge, thin tufted tail."""
    r.pal.update(pal(base=(88, 64, 52), belly=(128, 100, 84), back=(64, 46, 38), muzzle=(150, 110, 100), horn=(232, 222, 196),
                     spine=(110, 132, 64), ear=(96, 70, 58), pattern="fur"))
    quad(r, 13, (8, 8), (6.5, 6), 9.5, [(0.5, 2.5, (0.3, 1)), (0.5, 2.2, (0.3, 1))], (0, 0, 0, 0), (6, 6, 4, 3.5, 4, 3.4),
         tail_spec=(1, [3], [1], 1.0), ears=(2, 1.6, 1.2), hump=(5, 1.6, 1, 6))
    r.parts[-0]  # noqa
    for s, sx in ((0, 1), (1, -1)):
        chain(r, f"tusk{s}_", "head", [sx * 1.6, -2.2, -6.5], [(2.2, 1), (1.6, 0.8)], (0.4, 0, sx * 0.3), (0.6, 0, 0), "horn", s)
    spines(r, "body", 3.8, -5.5, 3, 6, 2.6, "spine", lean=0.5)


@design("shadecat", leg_len=7, shadow=0.55, glows=True)
def shadecat(r: Rig):
    """Lean panther: long low body, long curving tail, tall pointed ears, glowing slit eyes, faint stripes."""
    r.pal.update(pal(base=(56, 54, 68), belly=(92, 88, 104), back=(40, 38, 50), mark=(34, 32, 44), muzzle=(70, 68, 82), ear=(46, 44, 56),
                     paw=(42, 40, 52), eye=(200, 255, 120), pattern="stripes", glow_mats=("eyeglow",), eyeglow=(200, 255, 120)))
    quad(r, 14, (5.5, 5.5), (5, 5), 9.5, [(0.5, 1.8, (0.6, 0.8)), (0.45, 2, (0.6, 0.8))], (2, 3, 3, 0.35), (5, 4.5, 4, 1.6, 3, 2.4),
         tail_spec=(4, [3.5, 3.5, 3.5, 3], [1.6, 1.5, 1.4, 1.4], 0.9), ears=(2.4, 1.6, 0.2), hooves=False, stance=0.35)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"eyeglow{s}", "head", [sx * 2.51, 1.4, -3.4], [r.box(-0.25, 0, 0, 0.5, 1, 1.6, "eyeglow", "plain")], "decor", rel=True)
    r.by["tail1"]["rot"] = [0.3, 0, 0]
    r.by["tail2"]["rot"] = [0.35, 0, 0]
    r.by["tail3"]["rot"] = [0.3, 0, 0]


@design("snufflet", leg_len=3, shadow=0.35)
def snufflet(r: Rig):
    """Truffle-hog: barrel body, long flexible snout ending in a pink nose disc, floppy ears, dappled hide, curly tail."""
    r.pal.update(pal(base=(196, 146, 124), belly=(230, 196, 176), back=(168, 120, 102), mark=(146, 104, 88), muzzle=(220, 160, 150),
                     ear=(176, 126, 108), paw=(120, 90, 80), pattern="spots"))
    quad(r, 9, (6, 5.5), (6, 5.5), 5.5, [(0.5, 1.6, (0.2, 0.6)), (0.5, 1.6, (0.2, 0.6))], (0, 0, 0, 0), (5, 4.5, 3.5, 3, 2, 2),
         tail_spec=(2, [1.2, 1.2], [0.8, 0.8], -1.2), ears=(2.2, 2.2, 1.9))
    r.part("nosedisc", "head", [0, -2.25, -6.6], [r.box(-1.25, 0, -0.5, 2.5, 2.2, 0.6, "muzzle", "plain")], "decor", rel=True)
    r.mark("nosedisc", "north", "nostrils")
    r.by["tail1"]["rot"] = [-1.4, 0.4, 0]


# ================================================================ FUNGAL EXPANSE

@design("capback", scale=1.3, shadow=1.0, leg_len=8)
def capback(r: Rig):
    """Gentle giant: broad pale back carrying a garden of glowing-gilled mushrooms, pillar legs, broad low head with a dewlap."""
    r.pal.update(pal(base=(176, 156, 174), belly=(218, 206, 214), back=(150, 130, 152), muzzle=(196, 180, 190), ear=(160, 140, 160),
                     cap=(84, 150, 186), stem=(222, 214, 200), hoof=(90, 80, 96), glow=(220, 250, 255), pattern="fur"))
    quad(r, 16, (10, 9), (10, 8.5), 13, [(0.5, 3.6, (0.4, 1.2)), (0.5, 3.6, (0.4, 1.2))], (3, 6, 6, 0.1), (7, 6.5, 6, 2.5, 5, 3.5),
         tail_spec=(1, [3], [1.6], 1.0), ears=(2.4, 2, 1.4))
    r.part("dewlap", "head", [0, -3.2, -3], [r.box(-1.5, -2, -1, 3, 2.2, 3, "base", "head")], "decor", rel=True)
    for i, (x, z, sh, cw, ch) in enumerate([(0, -3, 4, 6, 2), (3, 2, 3, 4, 1.6), (-3, 3.5, 2.5, 4, 1.4), (-2.5, -5.5, 2, 3, 1.2), (2.5, -6, 1.5, 2.5, 1),
                                            (0, 6, 2, 3, 1.2)]):
        cap(r, f"cap{i}", "body", [x, 4.6, z], sh, 1.2 if cw > 3 else 1, cw, ch, rot=(0.05 * (i % 3 - 1), 0, x * 0.04), glow_dots=(220, 250, 255))


@design("sporemite", gait="hexa", leg_len=2, shadow=0.2)
def sporemite(r: Rig):
    """Mite: oversized round abdomen dusted with spore puffs, tiny head, six short scuttling legs."""
    r.pal.update(pal(base=(156, 124, 156), belly=(194, 174, 194), accent=(96, 74, 96), head=(124, 98, 124), spore=(222, 240, 255), pattern="spots",
                     mark=(214, 230, 250)))
    bug(r, (2.5, 2, 2.5), (4, 3.5, 4.5), (2, 1.8, 1.4), 2.4, 2.6, antenna=1.5)
    for i, (x, z) in enumerate([(1, 2.5), (-1.2, 3.5), (0.3, 4.4)]):
        r.part(f"puff{i}", "abdomen", [x, 1.4, z - 1.5], [r.box(-0.6, 0, -0.6, 1.2, 1, 1.2, "spore", "plain")], "decor", rel=True)


@design("lanternbeetle", gait="hexa", leg_len=3, shadow=0.3, glows=True)
def lanternbeetle(r: Rig):
    """Beetle: domed split wing-cases over a glowing lantern abdomen tip, broad pronotum, clubbed antennae."""
    r.pal.update(pal(base=(42, 62, 96), belly=(70, 90, 124), accent=(26, 36, 56), head=(36, 50, 80), elytra=(52, 78, 120), lantern=(130, 236, 255),
                     pattern="chitin", elytra_pattern="chitin", glow_mats=("lantern",)))
    bug(r, (4, 2.5, 3.5), (4.5, 3, 5.5), (3, 2, 2), 3, 5, antenna=3)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"elytron{s}", "body", [sx * 0.05, 4.4, 0.5], [r.box(0 if s == 0 else -2.5, 0, 0, 2.5, 1.6, 5, "elytra", "plain")], "wing", side=s,
               rot=(-0.12, 0, sx * 0.05))
    r.part("lantern", "abdomen", [0, 0, 4.5], [r.box(-1.5, -1.2, 0, 3, 2.4, 1.6, "lantern", "plain")], "decor", rel=True)


@design("capstalker", leg_len=9, shadow=0.6)
def capstalker(r: Rig):
    """Fungal predator: a red-capped hood over a long fanged jaw, hunched lean body with spore-stalks along the spine."""
    r.pal.update(pal(base=(132, 112, 102), belly=(170, 152, 140), back=(104, 86, 78), muzzle=(118, 98, 90), cap=(178, 62, 58), stem=(230, 220, 206),
                     paw=(80, 64, 58), horn=(236, 230, 214), mark=(150, 50, 46), pattern="saddle", eye=(250, 210, 90)))
    quad(r, 13, (7, 7), (6, 5.5), 11, [(0.5, 2.6, (0.6, 0.8)), (0.45, 2.4, (0.6, 0.8))], (3, 3.5, 4, 0.1), (6, 5.5, 5, 3, 4, 2.6),
         tail_spec=(2, [3, 3], [2, 1.6], 0.8), hooves=False, stance=0.35)
    cap(r, "hood", "head", [0, 1.8, -2.2], 0.3, 2, 6, 1.6, rot=(0.15, 0, 0), glow_dots=None)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"fang{s}", "head", [sx * 1.5, -2.75, -7.5], [r.box(-0.3, -1.4, -0.3, 0.6, 1.4, 0.6, "horn", "plain")], "decor", rel=True)
    for i, z in enumerate((-4, -1, 2)):
        cap(r, f"stalk{i}", "body", [0.5 * (i % 2 * 2 - 1), 3.4, z], 2.5 - i * 0.5, 0.8, 2.2, 1, rot=(0.1, 0, 0.15 * (i % 2 * 2 - 1)))


@design("puffling", leg_len=1.5, shadow=0.3, gait="quad")
def puffling(r: Rig):
    """Living puffball: a round soft body that is its own cap, two tiny feet, big dark eyes, a little spore tuft on top."""
    r.pal.update(pal(base=(226, 208, 234), belly=(244, 236, 248), back=(206, 184, 218), accent=(160, 132, 186), cap=(150, 200, 232),
                     stem=(236, 228, 240), paw=(170, 140, 190), eye=(46, 32, 66), mark=(200, 170, 220), pattern="spots"))
    body = r.part("body", "root", [0, 4.5, 0], [r.box(-3.5, -3, -3.5, 7, 6, 7, "base", "body"), r.box(-3, 3, -3, 6, 1, 6, "base", "body"),
                                                 r.box(-3, -3.6, -3, 6, 0.6, 6, "belly", "plain")], "body")
    r.part("head", body, [0, 4.5, -3.5], [r.box(-2.5, -1, -0.2, 5, 3, 0.4, "belly", "head")], "head")
    r.eye("head", "north", 0, 0, 2)
    r.eye("head", "north", -2, 0, 2)
    r.part("jaw", "head", [0, -1, 0], [r.box(-1, -0.6, -0.25, 2, 0.6, 0.3, "accent", "plain")], "jaw", rel=True)
    cap(r, "tuft", body, [0, 8.4, 0], 1, 1, 2.5, 1.2, rel=False)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"leg{s}", body, [sx * 1.6, 1.5, 0], [r.box(-0.9, -1.5, -1.4, 1.8, 1.7, 2.4, "paw", "limb")], "leg_upper", side=s, phase=s, index=0)


# ================================================================ FLOODED DEPTHS

@design("glimmerfin", gait="fish", leg_len=0, shadow=0.15, glows=True)
def glimmerfin(r: Rig):
    """Slim minnow with a glowing lateral line, forked tail and translucent fins."""
    r.pal.update(pal(base=(84, 146, 166), belly=(206, 232, 232), back=(60, 108, 130), fin=(150, 222, 232), line=(170, 255, 240), pattern="scales",
                     glow_mats=("line",)))
    fish(r, 8, 2, 3, 6, 6, 1.2, 1.5, head_frac=0.28)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"line{s}", "body", [sx * 1.01, 6, -1.8], [r.box(-0.05, 0, 0, 0.1, 0.5, 3.2, "line", "plain")], "decor")


@design("newtling", leg_len=1.5, shadow=0.2)
def newtling(r: Rig):
    """Newt: low sprawling body, splayed elbows, broad flat head, long keeled tail, bright orange skin with dark spots."""
    r.pal.update(pal(base=(206, 116, 60), belly=(244, 196, 120), back=(186, 96, 48), mark=(46, 34, 32), paw=(180, 100, 50), pattern="spots",
                     fin=(220, 130, 70)))
    lizard(r, 6, 3, 2, 2, 1.5, 1, (3, 1.6, 2.4, 1), (3, [2.5, 2.5, 2.2], [2, 1.5, 1], 0.05))
    r.part("keel", "tail1", [0, 0.6, 0], [r.box(-0.2, 0, 0, 0.4, 0.8, 2.5, "fin", "plain")], "decor", rel=True)


@design("lakehorn", gait="fish", scale=1.3, shadow=1.0, leg_len=0)
def lakehorn(r: Rig):
    """Lake sirenian: a heavy rounded body, paddle flippers, a broad horizontal fluke and a single spiral horn on the brow."""
    r.pal.update(pal(base=(104, 124, 134), belly=(184, 194, 194), back=(80, 98, 110), fin=(90, 110, 120), horn=(226, 216, 186), pattern="",
                     mark=(80, 96, 104)))
    body = r.part("body", "root", [0, 9, 0], [r.box(-5, -4.5, -6, 10, 9, 13, "base", "body"), r.box(-4, -4, 7, 8, 7, 3, "base", "body")], "body")
    r.part("head", body, [0, 9, -6], [r.box(-3.5, -3, -5, 7, 6, 5, "base", "head"), r.box(-2.5, -3, -6.5, 5, 3.5, 1.6, "belly", "head")], "head")
    r.part("jaw", "head", [0, -2.8, -2], [r.box(-2, -1, -4.4, 4, 1, 4.4, "belly", "head")], "jaw", rel=True)
    r.eye("head", "east", 1, 2, 1)
    r.eye("head", "west", -2, 2, 1)
    chain(r, "horn", "head", [0, 2.8, -3.5], [(3, 1.4), (2.6, 1.1), (2.2, 0.8)], (0.75, 0, 0), (0.12, 0.6, 0), "horn", role="crest")
    s0 = r.part("seg0", body, [0, 9, 10], [r.box(-3, -2.8, 0, 6, 5.5, 4, "base", "tail")], "segment", chain=0)
    r.part("seg1", s0, [0, 0, 4], [r.box(-2, -1.8, 0, 4, 3.5, 3.5, "base", "tail")], "segment", rel=True, chain=1)
    r.part("tailfin", "seg1", [0, 0, 3.5], [r.box(-5.5, -0.5, 0, 11, 1, 4, "fin", "wing")], "fluke", rel=True)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"pect{s}", body, [sx * 5, 6.5, -3.5], [r.box(0 if s == 0 else -5, -0.5, -1.5, 5, 1, 3.5, "fin", "wing")], "fin", rot=(0, sx * 0.3, sx * 0.5),
               side=s)


@design("ribbon_eel", gait="serpent", leg_len=0, shadow=0.3)
def ribbon_eel(r: Rig):
    """Ribbon eel: a long banded body with one continuous ribbon fin, a narrow jaw and a yellow lip stripe."""
    r.pal.update(pal(base=(60, 94, 72), belly=(168, 186, 140), back=(44, 70, 54), fin=(200, 222, 120), mark=(214, 230, 120), pattern="bands"))
    eel(r, 7, 3.5, 2.6, 5, 4)


@design("grand_drifter", gait="fish", scale=2.2, shadow=2.0, leg_len=0, glows=True)
def grand_drifter(r: Rig):
    """A lantern whale: vast tapered body with throat grooves, long pectoral sails, a crown of glowing crystals along the back."""
    r.pal.update(pal(base=(52, 74, 106), belly=(164, 184, 204), back=(40, 58, 86), fin=(80, 110, 150), crystal=(156, 224, 255), pattern="",
                     glow_mats=("crystal",), mark=(70, 96, 130)))
    body = r.part("body", "root", [0, 8, 0], [r.box(-6, -5.5, -9, 12, 11, 15, "base", "body"), r.box(-5, -6.2, -8, 10, 0.8, 12, "belly", "plain")], "body")
    r.part("head", body, [0, 8, -9], [r.box(-5, -5, -8, 10, 9, 8, "base", "head"), r.box(-4.5, -5.5, -9, 9, 4, 1.2, "base", "head")], "head")
    r.part("jaw", "head", [0, -4.8, -1], [r.box(-4.5, -1.4, -7.8, 9, 1.4, 7.8, "belly", "head")], "jaw", rel=True)
    r.eye("head", "east", 2, 5, 1)
    r.eye("head", "west", -3, 5, 1)
    s0 = r.part("seg0", body, [0, 8, 6], [r.box(-4.5, -4, 0, 9, 8, 7, "base", "tail")], "segment", chain=0)
    s1 = r.part("seg1", s0, [0, 0, 7], [r.box(-3, -2.5, 0, 6, 5, 6, "base", "tail")], "segment", rel=True, chain=1)
    r.part("seg2", s1, [0, 0, 6], [r.box(-1.5, -1.5, 0, 3, 3, 4, "base", "tail")], "segment", rel=True, chain=2)
    r.part("tailfin", "seg2", [0, 0, 4], [r.box(-8, -0.6, 0, 16, 1.2, 5, "fin", "wing")], "fluke", rel=True)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"pect{s}", body, [sx * 6, 4, -4], [r.box(0 if s == 0 else -12, -0.6, -2, 12, 1.2, 4.5, "fin", "wing")], "fin", rot=(0, sx * 0.35, sx * 0.4),
               side=s)
    for i, (z, h) in enumerate([(-6, 4), (-2, 5), (2, 4.5), (6, 3)]):
        crystal(r, f"crystal{i}", "body", [0.8 * (i % 2 * 2 - 1), 5.4, z], h, 1.8, (0.15, 0, 0.25 * (i % 2 * 2 - 1)))


# ================================================================ ASHEN HOLLOWS

@design("emberskink", leg_len=2, shadow=0.3, glows=True)
def emberskink(r: Rig):
    """Skink: smooth glossy scales, short splayed legs, a fat tail and ember-glow vents along its flanks."""
    r.pal.update(pal(base=(66, 44, 42), belly=(130, 70, 46), back=(48, 32, 32), mark=(255, 150, 60), ember=(255, 150, 60), pattern="scales",
                     glow_mats=("ember",)))
    lizard(r, 8, 4, 2.6, 2.6, 2, 1.2, (3.4, 2.2, 3, 1.2), (3, [3, 3, 2.5], [3, 2, 1.2], 0.05))
    for i, z in enumerate((-2.5, 0, 2.5)):
        for s, sx in ((0, 1), (1, -1)):
            r.part(f"vent{i}{s}", "body", [sx * 2.01, 2.8, z], [r.box(-0.05, 0, -0.6, 0.1, 0.6, 1.2, "ember", "plain")], "decor")


@design("ashshell", leg_len=4, shadow=0.7)
def ashshell(r: Rig):
    """Ash tortoise: a high domed carapace of hexagonal scutes, elephantine legs, a wrinkled beaked head on a long neck."""
    r.pal.update(pal(base=(108, 98, 86), belly=(150, 136, 112), shell=(72, 70, 72), scute=(96, 92, 92), paw=(82, 74, 66), beak=(60, 54, 50),
                     shell_pattern="plates", scute_pattern="plates", pattern="scales"))
    body = r.part("body", "root", [0, 6, 0], [r.box(-5, -2, -6, 10, 3.5, 12, "shell", "plain"), r.box(-4, 1.5, -5, 8, 2.5, 10, "scute", "plain"),
                                               r.box(-2.5, 4, -3, 5, 1, 6, "shell", "plain"), r.box(-4.5, -2.8, -5.5, 9, 0.8, 11, "belly", "plain")], "body")
    r.part("neck", body, [0, 5.5, -6], [r.box(-1.5, -1.5, -3.5, 3, 3, 4, "base", "neck")], "neck", rot=(-0.2, 0, 0))
    r.part("head", "neck", [0, 0.3, -3.5], [r.box(-1.8, -1.5, -3.2, 3.6, 3, 3.2, "base", "head")], "head", rel=True, rot=(0.2, 0, 0))
    r.part("jaw", "head", [0, -1.4, -1], [r.box(-1.5, -0.8, -2.4, 3, 0.8, 2.6, "beak", "head")], "jaw", rel=True)
    r.eye("head", "east", 0, 1, 1)
    r.eye("head", "west", -1, 1, 1)
    for i, (x, z, idx, ph) in enumerate([(4, -4, 0, 0), (-4, -4, 0, 1), (4, 4, 1, 1), (-4, 4, 1, 0)]):
        r.part(f"leg{i}", body, [x, 4, z], [r.box(-1.5, -4, -1.5, 3, 4.2, 3, "base", "limb"), r.box(-1.7, -4, -1.9, 3.4, 0.8, 3.6, "paw", "plain")],
               "leg_upper", side=i % 2, index=idx, phase=ph)
    tail(r, body, 0, 5, 6, 1, [2], [1.4], droop=0.6)


@design("scorchjaw", gait="biped", leg_len=12, glows=True)
def scorchjaw(r: Rig):
    """Raptor: horizontal spine balanced over powerful digitigrade legs, a deep scorched jaw, glowing back spines, stiff tail."""
    r.pal.update(pal(base=(78, 44, 38), belly=(146, 94, 62), back=(54, 30, 28), mark=(40, 24, 22), muzzle=(70, 40, 34), spine=(255, 128, 46),
                     paw=(46, 30, 28), horn=(230, 214, 180), eye=(255, 196, 60), pattern="scales", glow_mats=("spine",)))
    body = r.part("body", "root", [0, 15, 0], [r.box(-3, -3.5, -5, 6, 7, 6, "base", "body"), r.box(-2.5, -3, 1, 5, 6, 4, "base", "body")], "body",
                  rot=(0.08, 0, 0))
    r.part("neck", body, [0, 16.5, -4.5], [r.box(-1.5, -1.5, -5, 3, 3.2, 5.5, "base", "neck")], "neck", rot=(-0.55, 0, 0))
    r.part("head", "neck", [0, 0.3, -5], [r.box(-2.5, -2, -4, 5, 4.2, 4, "base", "head"), r.box(-2, -2, -7.5, 4, 2.8, 3.6, "muzzle", "head")],
           "head", rel=True, rot=(0.5, 0, 0))
    r.part("jaw", "head", [0, -1.8, -2], [r.box(-1.9, -1.6, -5.6, 3.8, 1.6, 5.8, "belly", "head")], "jaw", rel=True)
    r.eye("head", "east", 1, 1, 2, "slit")
    r.eye("head", "west", -3, 1, 2, "slit")
    for i in range(3):
        r.part(f"tooth{i}", "head", [1.6 - i * 1.6, -2, -6.8 + i * 0.4], [r.box(-0.3, -0.9, -0.3, 0.6, 0.9, 0.6, "horn", "plain")], "decor", rel=True)
    tail(r, body, 0, 16, 4.5, 3, [5, 4.5, 4], [4, 2.8, 1.6], droop=0.15, heights=[4, 2.8, 1.6])
    spines(r, "body", 3.5, -4, 3.5, 5, 2.4, "spine", lean=0.6)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"leg{s}", body, [sx * 3, 13, 1.5], [r.box(-1.6, -6, -2, 3.2, 7, 4, "base", "limb")], "leg_upper", rot=(-0.45, 0, 0), side=s, phase=s, index=1)
        r.part(f"leg{s}_lower", f"leg{s}", [0, -6, -0.5], [r.box(-1, -5.5, -1, 2, 5.8, 2, "base", "limb")], "leg_lower", rot=(0.9, 0, 0), rel=True, side=s,
               phase=s, index=1)
        r.part(f"leg{s}_foot", f"leg{s}_lower", [0, -5.3, 0], [r.box(-1.2, -1.2, -3, 2.4, 1.2, 3.6, "paw", "plain")], "foot", rot=(-0.45, 0, 0), rel=True,
               side=s)
        r.part(f"arm{s}", body, [sx * 2.6, 13.5, -4], [r.box(-0.6, -3, -0.6, 1.2, 3.2, 1.2, "base", "limb"), r.box(-0.6, -3.6, -1.2, 1.2, 0.8, 1.4, "horn", "plain")],
               "arm", rot=(-0.6, 0, 0), side=s, phase=s)


@design("slag_titan", scale=2.0, shadow=2.0, leg_len=6, glows=True)
def slag_titan(r: Rig):
    """Magma tortoise colossus: a mountain of slag plates split by glowing seams, basalt crystal spires, stump legs, a low ram head."""
    r.pal.update(pal(base=(66, 58, 56), belly=(100, 78, 64), shell=(52, 46, 46), seam=(255, 130, 40), crystal=(70, 62, 66), paw=(44, 40, 40),
                     horn=(90, 84, 80), shell_pattern="plates", pattern="plates", glow_mats=("seam",), eye=(255, 170, 60)))
    body = r.part("body", "root", [0, 9, 0], [r.box(-8, -3, -10, 16, 5, 20, "shell", "plain"), r.box(-7, 2, -8.5, 14, 4, 17, "shell", "plain"),
                                               r.box(-5, 6, -6, 10, 2, 12, "shell", "plain"), r.box(-7.5, -4, -9, 15, 1, 18, "belly", "plain")], "body")
    for i, (z, w) in enumerate([(-5, 14.2), (0, 14.2), (5, 14.2)]):
        r.part(f"seam{i}", body, [0, 11, z], [r.box(-w / 2, 0, -0.3, w, 4.05, 0.6, "seam", "plain")], "decor")
    r.part("head", body, [0, 9, -10], [r.box(-3.5, -3, -6, 7, 6, 6, "base", "head"), r.box(-4, 2, -6.5, 8, 1.6, 4, "horn", "plain")], "head")
    r.part("jaw", "head", [0, -2.8, -2], [r.box(-3, -1.4, -4, 6, 1.4, 4, "belly", "head")], "jaw", rel=True)
    r.eye("head", "east", 1, 2, 1, "roundglow")
    r.eye("head", "west", -2, 2, 1, "roundglow")
    for i, (x, z, idx, ph) in enumerate([(6.5, -7, 0, 0), (-6.5, -7, 0, 1), (6.5, 7, 1, 1), (-6.5, 7, 1, 0)]):
        r.part(f"leg{i}", body, [x, 6, z], [r.box(-2.5, -6, -2.5, 5, 6.2, 5, "base", "limb"), r.box(-2.8, -6, -3, 5.6, 1.2, 5.8, "paw", "plain")],
               "leg_upper", side=i % 2, index=idx, phase=ph)
    for i, (x, z, h, rx, rz) in enumerate([(-3, -3, 7, 0.1, 0.2), (2.5, -1, 9, -0.05, -0.15), (-1, 4, 6, -0.2, 0.1), (4, 5, 5, -0.1, -0.35),
                                           (-5, 2, 4, 0, 0.5), (0, -6.5, 4, 0.35, 0)]):
        crystal(r, f"spire{i}", "body", [x, 7, z], h, 2.4 if h > 6 else 1.8, (rx, 0, rz))


# ================================================================ CRYSTAL DEEP

@design("prismhorn", leg_len=9, glows=True)
def prismhorn(r: Rig):
    """Crystal goat: compact sure-footed body, long faceted glowing horns sweeping back, crystal growths on the withers, beard."""
    r.pal.update(pal(base=(172, 182, 202), belly=(222, 226, 236), back=(140, 150, 172), muzzle=(150, 160, 180), ear=(160, 170, 190),
                     crystal=(168, 238, 255), hoof=(80, 86, 104), pattern="fur", glow_mats=("crystal",)))
    quad(r, 12, (6.5, 7), (6, 6.5), 12, [(0.5, 2, (0.3, 1.2)), (0.5, 2, (0.3, 1.2))], (4, 3, 3.5, 0.7), (4.5, 4, 4.5, 2, 3, 2.6),
         tail_spec=(1, [2], [1.6], -0.4), ears=(2, 1.4, 1.5))
    horn_pair(r, "head", 1.2, 1.8, -2.5, [(3, 1.6), (3, 1.3), (2.6, 1)], (-0.9, 0, 0.25), (-0.35, 0, 0), "crystal")
    r.part("beard", "jaw", [0, -1.2, -1.5], [r.box(-0.75, -2, -1, 1.5, 2, 1.5, "belly", "plain")], "decor", rel=True)
    for i, (x, z, h) in enumerate([(0.8, -4.5, 3), (-1, -3, 2.5), (0.5, -1.5, 2)]):
        crystal(r, f"crystal{i}", "body", [x, 3.4, z], h, 1.2, (0.3, 0, x * 0.25))


@design("shardfly", gait="none", leg_len=0, shadow=0.15, glows=True)
def shardfly(r: Rig):
    """Crystal dragonfly: a long segmented glassy abdomen, big faceted eyes, two pairs of narrow glittering wings."""
    r.pal.update(pal(base=(150, 220, 240), belly=(200, 240, 250), head=(130, 200, 230), accent=(100, 160, 190), wing=(214, 250, 255),
                     wingtip=(190, 240, 255), eye=(200, 255, 255), pattern="crystal", glow_mats=("eyeglow",), eyeglow=(200, 255, 255)))
    body = r.part("body", "root", [0, 9, 0], [r.box(-1, -1, -1.5, 2, 2, 3, "base", "body")], "body")
    a = r.part("abdomen", body, [0, 9, 1.5], [r.box(-0.6, -0.6, 0, 1.2, 1.2, 3.5, "base", "tail")], "tail", chain=0)
    r.part("abdomen2", a, [0, 0, 3.5], [r.box(-0.5, -0.5, 0, 1, 1, 3, "base", "tail")], "tail", rel=True, chain=1)
    r.part("head", body, [0, 9.2, -1.5], [r.box(-1.25, -1, -1.5, 2.5, 2, 1.5, "head", "head")], "head")
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"eyeglow{s}", "head", [sx * 0.9, -0.6, -1.4], [r.box(-0.6, 0, 0, 1.2, 1.4, 1.2, "eyeglow", "plain")], "decor", rel=True)
        r.part(f"wing{s}", body, [sx * 0.8, 10, -1], [r.box(0 if s == 0 else -6, 0, -0.6, 6, 0.2, 1.6, "wing", "wing")], "wing", side=s)
        r.part(f"wing{s}_outer", body, [sx * 0.8, 10, 0.6], [r.box(0 if s == 0 else -5.5, 0, -0.6, 5.5, 0.2, 1.4, "wingtip", "wing")], "wing", side=s)


@design("geode_crab", gait="crab", leg_len=4, shadow=0.5, glows=True)
def geode_crab(r: Rig):
    """Geode crab: a cracked stone shell bursting with violet crystal clusters, heavy mismatched claws, stalked eyes."""
    r.pal.update(pal(base=(112, 102, 118), belly=(170, 160, 170), back=(90, 82, 98), accent=(80, 72, 90), crystal=(214, 166, 255), eyeball=(40, 30, 50),
                     pattern="plates", glow_mats=("crystal",)))
    crab(r, 9, 7, 3.5, 4, 5, 3.4)
    for i, (x, z, h) in enumerate([(-1.5, -0.5, 3), (1, 1, 2.5), (-0.2, 2, 2), (2.2, -1.5, 1.6), (-2.5, 1.6, 1.6)]):
        crystal(r, f"geode{i}", "body", [x, 2.2, z], h, 1.3, (0.2 * (i % 2 * 2 - 1), 0, x * 0.15))


@design("facet_mantis", gait="hexa", leg_len=8, glows=True)
def facet_mantis(r: Rig):
    """Mantis: an upright thorax, triangular head with big eyes, folded serrated raptorial arms of glowing crystal, long abdomen."""
    r.pal.update(pal(base=(150, 200, 190), belly=(200, 230, 220), head=(160, 210, 200), accent=(96, 146, 136), crystal=(180, 255, 240),
                     wing=(190, 230, 220), eye=(40, 70, 70), pattern="chitin", glow_mats=("crystal",)))
    body = r.part("body", "root", [0, 9, 0], [r.box(-1.25, -1.25, -1.5, 2.5, 2.5, 4, "base", "body")], "body")
    r.part("prothorax", body, [0, 9.5, -1.5], [r.box(-0.75, 0, -1, 1.5, 6, 1.5, "base", "body")], "neck", rot=(-0.4, 0, 0))
    r.part("head", "prothorax", [0, 6, 0], [r.box(-1.75, -0.5, -1.5, 3.5, 2, 1.8, "head", "head")], "head", rel=True, rot=(0.4, 0, 0))
    r.eye("head", "north", 0, 0, 1)
    r.eye("head", "north", -1, 0, 1)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"antenna{s}", "head", [sx * 0.5, 1.5, -1], [r.box(-0.2, 0, -4, 0.4, 0.4, 4, "accent", "plain")], "antenna", rot=(-0.7, -sx * 0.3, 0),
               rel=True, side=s)
        r.part(f"rapt{s}", "prothorax", [sx * 1, 4.5, -0.5], [r.box(-0.5, -4, -0.6, 1, 4.5, 1.2, "crystal", "plain")], "arm", rot=(-0.9, 0, 0),
               rel=True, side=s, phase=s)
        r.part(f"rapt{s}_blade", f"rapt{s}", [0, -4, 0], [r.box(-0.4, 0, -0.5, 0.8, 4, 1, "crystal", "plain"),
                                                       r.box(-0.3, 1, -0.9, 0.6, 2.5, 0.4, "accent", "plain")], "jaw", rot=(-2.4, 0, 0), rel=True, side=s)
        r.part(f"wing{s}", body, [sx * 0.6, 10.3, -0.5], [r.box(0 if s == 0 else -1.6, 0, 0, 1.6, 0.3, 7.5, "wing", "wing")], "wing", side=s,
               rot=(-0.15, 0, sx * 0.1))
        for k in range(2):
            nm = f"leg{k * 2 + s}"
            r.part(nm, body, [sx * 1.25, 8.5, 0.5 + k * 1.8], [r.box(0 if s == 0 else -4, -0.4, -0.4, 4, 0.8, 0.8, "base", "limb")], "leg_upper",
                   rot=(0, sx * (0.2 - k * 0.9), sx * -0.6), side=s, phase=(k + s) % 2, index=k)
            r.part(nm + "_lower", nm, [sx * 4, 0, 0], [r.box(-0.4, -7.2, -0.4, 0.8, 7.5, 0.8, "accent", "limb")], "leg_lower", rot=(0, 0, sx * 0.62),
                   rel=True, side=s, phase=(k + s) % 2, index=k)
    r.part("abdomen", body, [0, 9, 2.5], [r.box(-1.5, -1.5, 0, 3, 3, 4, "base", "body"), r.box(-1.1, -1.2, 4, 2.2, 2.2, 3, "base", "body")], "tail",
           rot=(0.2, 0, 0), chain=0)


# ================================================================ THE ABYSS

@design("echo_crawler", gait="hexa", leg_len=4)
def echo_crawler(r: Rig):
    """Blind cave isopod-centipede: overlapping flat armour bands, long sweeping sensory antennae, no eyes, many short legs."""
    r.pal.update(pal(base=(66, 60, 76), belly=(96, 90, 106), back=(50, 46, 60), accent=(110, 104, 130), head=(56, 52, 66), pattern="plates",
                     band=(78, 72, 90), band_pattern="plates"))
    body = r.part("body", "root", [0, 4, 0], [r.box(-3.5, -1.5, -3, 7, 2.6, 6, "base", "body")], "body")
    r.part("head", body, [0, 4, -3], [r.box(-2.5, -1.2, -2.5, 5, 2, 2.5, "head", "head")], "head")
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"antenna{s}", "head", [sx * 1.6, 0.5, -2.4], [r.box(-0.2, 0, -9, 0.4, 0.4, 9, "accent", "plain")], "antenna", rot=(-0.15, -sx * 0.4, 0),
               rel=True, side=s)
        r.part(f"feeler{s}", "head", [sx * 2.4, 0.2, -1.5], [r.box(-0.2, -0.2, -3, 0.4, 0.4, 3, "accent", "plain")], "antenna", rot=(0.1, -sx * 1.1, 0),
               rel=True, side=s)
    prev = body
    for i in range(4):
        pv = [0, 4, 3] if i == 0 else [0, 0, 2.6]
        w = 7 - i * 1.2
        prev = r.part(f"band{i}", prev, pv, [r.box(-w / 2, -1.3, 0, w, 2.4, 2.8, "band", "body")], "segment", rel=i > 0, chain=i)
    for k in range(3):
        for s, sx in ((0, 1), (1, -1)):
            nm = f"leg{k * 2 + s}"
            r.part(nm, body, [sx * 3.4, 3.2, -2 + k * 2], [r.box(0 if s == 0 else -2.4, -0.4, -0.4, 2.4, 0.8, 0.8, "base", "limb")], "leg_upper",
                   rot=(0, sx * (0.5 - k * 0.5), sx * -0.4), side=s, phase=(k + s) % 2, index=k)
            r.part(nm + "_lower", nm, [sx * 2.4, 0, 0], [r.box(-0.4, -3.8, -0.4, 0.8, 4, 0.8, "accent", "limb")], "leg_lower", rot=(0, 0, sx * 0.55), rel=True,
                   side=s, phase=(k + s) % 2, index=k)
    r.part("cerci", "band3", [0, 0, 2.6], [r.box(-1.5, -0.2, 0, 0.4, 0.4, 3, "accent", "plain"), r.box(1.1, -0.2, 0, 0.4, 0.4, 3, "accent", "plain")],
           "decor", rel=True)


@design("lure_angler", leg_len=3, glows=True)
def lure_angler(r: Rig):
    """Land anglerfish: an enormous head that is mostly jaw, needle teeth, a glowing lure on a bending rod, stubby crawling fins."""
    r.pal.update(pal(base=(32, 34, 44), belly=(56, 56, 68), back=(24, 26, 34), horn=(214, 220, 214), muzzle=(40, 42, 52), lure=(140, 255, 220),
                     accent=(50, 52, 64), paw=(28, 30, 38), pattern="spots", mark=(60, 62, 80), glow_mats=("lure",), eye=(180, 220, 200)))
    body = r.part("body", "root", [0, 6, 0], [r.box(-4.5, -3, -2, 9, 6.5, 6, "base", "body"), r.box(-3, -2.4, 4, 6, 4.5, 3, "base", "body")], "body")
    r.part("head", body, [0, 6.5, -2], [r.box(-5, -1, -8, 10, 6, 8, "base", "head")], "head")
    r.part("jaw", "head", [0, -0.5, -0.5], [r.box(-5.2, -3.5, -8, 10.4, 3.5, 8, "belly", "head")], "jaw", rot=(0.12, 0, 0), rel=True)
    for i in range(5):
        x = -4 + i * 2
        r.part(f"tooth{i}", "head", [x, -1, -7.5], [r.box(-0.3, -1.8, -0.3, 0.6, 1.8, 0.6, "horn", "plain")], "decor", rel=True)
        r.part(f"ltooth{i}", "jaw", [x + 1, 0, -7.6], [r.box(-0.3, 0, -0.3, 0.6, 1.4, 0.6, "horn", "plain")], "decor", rel=True)
    r.eye("head", "east", 3, 1, 1)
    r.eye("head", "west", -4, 1, 1)
    rod = chain(r, "rod", "head", [0, 5, -5], [(3, 0.6), (3, 0.5), (2.5, 0.4)], (0.35, 0, 0), (0.55, 0, 0), "accent", role="antenna")
    r.part("lure", rod, [0, 2.2, 0], [r.box(-1, -1, -1, 2, 2, 2, "lure", "plain")], "float", rel=True)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"leg{s}", body, [sx * 4.5, 3, -0.5], [r.box(-1 if s == 0 else -2, -3, -1.5, 3, 3.2, 3, "base", "limb")], "leg_upper", side=s, phase=s,
               index=0, rot=(0, 0, sx * -0.3))
        r.part(f"leg{s + 2}", body, [sx * 3, 3, 4.5], [r.box(-1, -3, -1, 2, 3.2, 2, "base", "limb")], "leg_upper", side=s, phase=1 - s, index=1)
    tail(r, body, 0, 6, 7, 2, [3, 3], [3, 2], droop=0.25)
    r.part("tailfan", "tail1", [0, 0, 3], [r.box(-0.25, -2.5, 0, 0.5, 5, 3, "accent", "wing")], "tailfin", rel=True)


@design("gloomwing", gait="none", leg_len=1, shadow=0.2, glows=True)
def gloomwing(r: Rig):
    """Cave bat: a furry body, huge ears, glowing violet eyes and broad finger-ribbed membrane wings in two hinged sections."""
    r.pal.update(pal(base=(54, 48, 64), belly=(84, 74, 96), back=(40, 36, 50), ear=(70, 58, 80), wing=(70, 58, 82), wingtip=(62, 52, 74),
                     bone=(40, 34, 48), eyeglow=(210, 130, 255), pattern="fur", wing_pattern="membrane", wingtip_pattern="membrane",
                     glow_mats=("eyeglow",), muzzle=(64, 56, 74)))
    body = r.part("body", "root", [0, 8, 0], [r.box(-1.75, -1.75, -2, 3.5, 3.5, 4.5, "base", "body")], "body")
    r.part("head", body, [0, 9, -2], [r.box(-1.6, -1.5, -2.6, 3.2, 3, 2.6, "base", "head"), r.box(-0.9, -1.5, -3.4, 1.8, 1.6, 1, "muzzle", "head")],
           "head")
    r.part("jaw", "head", [0, -1.4, -1.5], [r.box(-0.8, -0.6, -1.8, 1.6, 0.6, 1.8, "muzzle", "head")], "jaw", rel=True)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"ear{s}", "head", [sx * 1, 1.4, -1], [r.box(-0.8, 0, -0.3, 1.6, 3, 0.6, "ear", "plain")], "ear", rot=(-0.1, 0, -sx * 0.35), rel=True, side=s)
        r.part(f"eyeglow{s}", "head", [sx * 1.61, 0.2, -2.4], [r.box(-0.2, 0, 0, 0.4, 0.8, 0.8, "eyeglow", "plain")], "decor", rel=True)
        r.part(f"wing{s}", body, [sx * 1.75, 9.5, -1.5], [r.box(0 if s == 0 else -4.5, -0.2, 0, 4.5, 0.4, 4.5, "wing", "wing")], "wing", side=s)
        r.part(f"wing{s}_outer", f"wing{s}", [sx * 4.5, 0, 0], [r.box(0 if s == 0 else -4, -0.15, 0, 4, 0.3, 3.5, "wingtip", "wing")], "wing_outer",
               side=s, rel=True)
        r.part(f"leg{s}", body, [sx * 0.8, 6.5, 2], [r.box(-0.3, -1.2, -0.3, 0.6, 1.2, 0.6, "bone", "limb")], "leg_upper", side=s, index=1)


@design("abyss_strider", scale=2.6, shadow=1.0, leg_len=28, glows=True)
def abyss_strider(r: Rig):
    """Harvestman titan: a small armoured body slung impossibly high between four stilt legs with high knees, a lantern lure below."""
    r.pal.update(pal(base=(44, 46, 56), belly=(74, 76, 86), back=(34, 34, 42), accent=(30, 30, 38), lure=(160, 200, 255), horn=(190, 200, 214),
                     pattern="plates", glow_mats=("lure",), eye=(160, 200, 255)))
    y = 30
    body = r.part("body", "root", [0, y, 0], [r.box(-3.5, -2.5, -4, 7, 5, 8, "base", "body"), r.box(-2.5, 2.5, -3, 5, 1, 6, "back", "body")], "body")
    r.part("head", body, [0, y - 0.5, -4], [r.box(-2.5, -2, -3, 5, 4, 3, "base", "head")], "head")
    r.part("jaw", "head", [0, -1.8, -1.5], [r.box(-1.5, -1.5, -1.5, 3, 1.5, 1.5, "accent", "plain")], "jaw", rel=True)
    r.eye("head", "north", 1, 1, 1, "roundglow")
    r.eye("head", "north", -2, 1, 1, "roundglow")
    stalk = chain(r, "stalk", body, [0, y - 2.5, -1], [(3, 0.4), (3, 0.4)], (3.14, 0, 0), (0.1, 0, 0), "accent", rel=False, role="antenna")
    r.part("lure", stalk, [0, 2.6, 0], [r.box(-1, 0, -1, 2, 2, 2, "lure", "plain")], "float", rel=True)
    for i, (sx, sz, idx, ph) in enumerate([(1, -1, 0, 0), (-1, -1, 0, 1), (1, 1, 1, 1), (-1, 1, 1, 0)]):
        s = 0 if sx > 0 else 1
        # thigh rises out and up to a high knee, shin falls to the ground
        r.part(f"leg{i}", body, [sx * 3, y, sz * 2.5], [r.box(-0.75, 0, -0.75, 1.5, 12, 1.5, "base", "limb")], "leg_upper",
               rot=(sz * 0.55, 0, -sx * 0.75), side=s, index=idx, phase=ph)
        r.part(f"leg{i}_lower", f"leg{i}", [0, 11.5, 0], [r.box(-0.6, -38, -0.6, 1.2, 38, 1.2, "accent", "limb")], "leg_lower",
               rot=(-sz * 0.98, 0, sx * 1.05), rel=True, side=s, index=idx, phase=ph)


# ================================================================ EVERFROST

@design("frostmane", scale=1.15, shadow=0.9, leg_len=7)
def frostmane(r: Rig):
    """Musk ox: a curtain of long frosted fur hanging almost to the hooves, a high shoulder hump, boss horns curving down and out."""
    r.pal.update(pal(base=(176, 180, 190), belly=(214, 218, 224), back=(150, 154, 166), muzzle=(110, 108, 116), skirt=(204, 210, 220),
                     horn=(226, 216, 190), hoof=(70, 66, 70), pattern="fur", skirt_pattern="fur"))
    quad(r, 15, (10, 9), (9, 8), 12, [(0.5, 3, (0.2, 1)), (0.5, 3, (0.2, 1))], (0, 0, 0, 0), (6, 5.5, 4, 2.5, 4, 3.5),
         tail_spec=(1, [2], [1.6], 1.1), hump=(8, 2.6, 1, 7, "skirt"))
    r.part("skirt", "body", [0, 12, 0], [r.box(-5.4, -7.5, -7, 10.8, 7.2, 13.5, "skirt", "plain")], "decor")
    r.part("boss", "head", [0, 2.5, -1.5], [r.box(-3.5, 0, -2.5, 7, 1.4, 3, "horn", "plain")], "decor", rel=True)
    horn_pair(r, "head", 3.2, 3, -2.5, [(3, 1.6), (2.5, 1.3), (2, 1)], (0, 0, 1.9), (0, 0, -0.9), "horn")
    r.part("beardfur", "head", [0, -2.75, -3], [r.box(-2, -3, -1, 4, 3, 3, "skirt", "plain")], "decor", rel=True)


@design("rimefox", leg_len=6)
def rimefox(r: Rig):
    """Arctic fox: dainty legs, a pointed muzzle, big triangular ears and a huge plumed tail as long as its body, frost-tipped."""
    r.pal.update(pal(base=(228, 234, 242), belly=(248, 250, 252), back=(206, 216, 230), muzzle=(236, 240, 246), ear=(200, 210, 226),
                     paw=(180, 190, 210), mark=(150, 180, 214), tip=(160, 196, 230), pattern="fur", eye=(40, 70, 110)))
    quad(r, 10, (4.5, 4.5), (4.5, 4.5), 8.5, [(0.5, 1.5, (0.5, 0.6)), (0.45, 1.6, (0.5, 0.6))], (2, 2.6, 2.6, 0.5), (4.5, 4, 3, 3, 2, 2),
         tail_spec=(3, [3, 3.5, 3.5], [3, 3.6, 3.2], 0.7), ears=(3, 2, 0.35), hooves=False, stance=0.3)
    r.part("tailtip", "tail2", [0, 0, 3.5], [r.box(-1.4, -1.4, 0, 2.8, 2.8, 2, "tip", "plain")], "decor", rel=True)
    r.by["tail1"]["rot"] = [0.35, 0, 0]
    r.by["tail2"]["rot"] = [0.3, 0, 0]
    r.part("ruff", "neck", [0, -0.5, -1.5], [r.box(-2.25, -1.6, -1, 4.5, 3.5, 2.5, "belly", "plain")], "decor", rel=True)


@design("glacier_skimmer", gait="hexa", leg_len=3, shadow=0.2)
def glacier_skimmer(r: Rig):
    """Ice strider: a tiny slender body held level on six very long hair-thin legs, front pair short and grasping."""
    r.pal.update(pal(base=(150, 200, 230), belly=(220, 240, 250), accent=(90, 130, 170), head=(130, 180, 214), pattern="chitin"))
    body = r.part("body", "root", [0, 3.5, 0], [r.box(-1, -0.75, -2.5, 2, 1.5, 5, "base", "body"), r.box(-0.75, -0.6, 2.5, 1.5, 1.2, 2, "base", "body")],
                  "body")
    r.part("head", body, [0, 3.7, -2.5], [r.box(-0.9, -0.7, -1.5, 1.8, 1.4, 1.5, "head", "head")], "head")
    r.eye("head", "east", 0, 0, 1)
    r.eye("head", "west", -1, 0, 1)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"antenna{s}", "head", [sx * 0.5, 0.5, -1.4], [r.box(-0.15, 0, -2.5, 0.3, 0.3, 2.5, "accent", "plain")], "antenna", rot=(-0.25, -sx * 0.3, 0),
               rel=True, side=s)
        for k, (z, ln, yaw) in enumerate([(-1.8, 2.5, 0.9), (-0.5, 7, 0.25), (0.8, 7, -0.55)]):
            nm = f"leg{k * 2 + s}"
            r.part(nm, body, [sx * 1, 3.3, z], [r.box(0 if s == 0 else -ln * 0.5, -0.25, -0.25, ln * 0.5, 0.5, 0.5, "base", "limb")], "leg_upper",
                   rot=(0, sx * yaw, sx * -0.35), side=s, phase=(k + s) % 2, index=k)
            r.part(nm + "_lower", nm, [sx * ln * 0.5, 0, 0], [r.box(0 if s == 0 else -ln * 0.6, -0.2, -0.2, ln * 0.6, 0.4, 0.4, "accent", "limb")],
                   "leg_lower", rot=(0, 0, sx * 0.55), rel=True, side=s, phase=(k + s) % 2, index=k)


# ================================================================ GOLDEN GROVE

@design("goldfleece", leg_len=6)
def goldfleece(r: Rig):
    """Sheep: a deep cloud of curly golden fleece, a dark slender face and thin legs below it, drooping ears."""
    r.pal.update(pal(base=(70, 56, 44), belly=(110, 90, 70), fleece=(236, 196, 92), muzzle=(56, 44, 36), ear=(70, 56, 44), hoof=(40, 32, 28),
                     fleece_pattern="fur", pattern="", eye=(30, 24, 20)))
    quad(r, 11, (6, 6), (6, 6), 9.5, [(0.5, 1.4, (0.2, 0.8)), (0.5, 1.4, (0.2, 0.8))], (1.5, 3, 3, 0.4), (3.5, 3.6, 3, 2, 2.6, 2.6),
         tail_spec=(1, [1.5], [1.6], 1.0), ears=(2, 1.2, 2.2))
    r.part("fleece", "body", [0, 9.5, 0], [r.box(-5, -4.6, -6.6, 10, 9.2, 13.4, "fleece", "plain"), r.box(-4, 4.5, -5, 8, 1.4, 10, "fleece", "plain"),
                                           r.box(-4.4, -5.5, -5.6, 8.8, 1, 11, "fleece", "plain")], "decor")
    r.part("poll", "head", [0, 1.7, -1.5], [r.box(-2.25, 0, -1.5, 4.5, 1.6, 3, "fleece", "plain")], "decor", rel=True)


@design("amberwing", gait="biped", leg_len=2, shadow=0.25)
def amberwing(r: Rig):
    """Songbird: a round amber breast, dark mask, short thick seed beak, long forked tail and pale wing bars."""
    r.pal.update(pal(base=(234, 148, 54), belly=(255, 210, 126), back=(176, 96, 40), wing=(200, 92, 58), wingtip=(110, 60, 40), beak=(60, 48, 40),
                     accent=(80, 60, 40), pattern="feathers", wing_pattern="feathers", wingtip_pattern="feathers"))
    bird(r, 6, 3.5, 3.5, 4.5, (1.5, 2.6), (3, 3, 3), (1.6, 1.4), (5, 3.5), 4.5, (0.5, 0.5), crest=(2, 2))
    r.part("mask", "head", [0, -0.2, -1.95], [r.box(-1.55, 0, 0, 3.1, 1, 2, "accent", "plain")], "decor", rel=True)


@design("bumblewisp", gait="none", leg_len=0, shadow=0.2)
def bumblewisp(r: Rig):
    """Bumblebee: a very fuzzy round body in gold and black bands, a tiny head, stubby glassy wings and dangling legs."""
    r.pal.update(pal(base=(250, 200, 60), belly=(255, 226, 120), mark=(54, 38, 22), head=(54, 38, 22), accent=(40, 28, 18), wing=(226, 238, 250),
                     wingtip=(210, 226, 244), pattern="bands"))
    body = r.part("body", "root", [0, 8, 0], [r.box(-2.25, -2, -2, 4.5, 4.2, 5, "base", "body"), r.box(-1.75, -1.5, 3, 3.5, 3, 1.5, "mark", "body")], "body")
    r.part("head", body, [0, 8.4, -2], [r.box(-1.4, -1.25, -1.8, 2.8, 2.5, 1.8, "head", "head")], "head")
    r.eye("head", "east", 0, 0, 1)
    r.eye("head", "west", -1, 0, 1)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"antenna{s}", "head", [sx * 0.6, 1, -1.6], [r.box(-0.2, 0, -0.2, 0.4, 2, 0.4, "accent", "plain")], "antenna", rot=(-0.6, 0, sx * 0.3),
               rel=True, side=s)
        r.part(f"wing{s}", body, [sx * 1.2, 10.2, -1], [r.box(0 if s == 0 else -3.5, 0, -0.5, 3.5, 0.2, 2.6, "wing", "wing")], "wing", side=s,
               rot=(0, sx * 0.3, 0))
        for k in range(2):
            r.part(f"leg{k * 2 + s}", body, [sx * 1.2, 6.2, -1 + k * 2], [r.box(-0.25, -1.6, -0.25, 0.5, 1.6, 0.5, "accent", "limb")], "leg_upper",
                   rot=(0.3, 0, sx * -0.2), side=s, index=k, phase=k)


@design("glade_runner", gait="biped", leg_len=14, shadow=0.6)
def glade_runner(r: Rig):
    """Running bird: a long S-necked flightless sprinter with a plumed body, tiny wings, thick thighs and a crimson head crest."""
    r.pal.update(pal(base=(202, 162, 92), belly=(240, 222, 172), back=(168, 130, 70), beak=(120, 100, 70), wing=(176, 136, 76), wingtip=(140, 104, 60),
                     accent=(222, 92, 60), skin=(110, 96, 86), pattern="feathers"))
    body = r.part("body", "root", [0, 17, 0], [r.box(-3.5, -3, -4.5, 7, 6.5, 9, "base", "body"), r.box(-3, -2, 4.5, 6, 4, 2, "base", "body")], "body",
                  rot=(-0.1, 0, 0))
    r.part("neck", body, [0, 19, -4], [r.box(-1, -1, -1, 2, 8, 2, "base", "neck")], "neck", rot=(-0.35, 0, 0))
    r.part("neck2", "neck", [0, 7.5, 0], [r.box(-0.9, 0, -0.9, 1.8, 4, 1.8, "base", "neck")], "neck", rel=True, rot=(0.5, 0, 0))
    r.part("head", "neck2", [0, 4, -0.2], [r.box(-1.4, -0.8, -2.6, 2.8, 2.6, 3.2, "base", "head")], "head", rel=True, rot=(-0.15, 0, 0))
    r.part("beak", "head", [0, -0.2, -2.6], [r.box(-0.8, -0.5, -2.4, 1.6, 1.1, 2.4, "beak", "plain")], "jaw", rel=True)
    r.eye("head", "east", 1, 0, 1)
    r.eye("head", "west", -2, 0, 1)
    r.part("crest", "head", [0, 1.8, -1], [r.box(-0.4, 0, -1, 0.8, 3, 3, "accent", "plain")], "crest", rot=(0.4, 0, 0), rel=True)
    r.part("plume", body, [0, 19, 5], [r.box(-2.5, -1, 0, 5, 3, 4, "wingtip", "wing")], "tail", rot=(0.5, 0, 0), chain=0)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"wing{s}", body, [sx * 3.5, 18.5, -2], [r.box(0 if s == 0 else -1, -2.5, 0, 1, 3, 5, "wing", "wing")], "wing", side=s,
               rot=(0.2, 0, sx * 0.1))
        r.part(f"leg{s}", body, [sx * 2, 15, 0.5], [r.box(-1.5, -6, -1.6, 3, 7, 3.4, "base", "limb")], "leg_upper", rot=(-0.3, 0, 0), side=s, phase=s,
               index=1)
        r.part(f"leg{s}_lower", f"leg{s}", [0, -5.6, 0], [r.box(-0.5, -9, -0.5, 1, 9.3, 1, "skin", "limb")], "leg_lower", rot=(0.42, 0, 0), rel=True,
               side=s, phase=s, index=1)
        r.part(f"leg{s}_foot", f"leg{s}_lower", [0, -8.8, 0], [r.box(-1.2, -0.6, -2.8, 2.4, 0.8, 3.6, "skin", "plain")], "foot", rot=(-0.12, 0, 0), rel=True,
               side=s)


# ================================================================ HOLLOW PEAKS

@design("cliffstrider", leg_len=10)
def cliffstrider(r: Rig):
    """Ibex: a stocky mountain climber with enormous ridged horns arcing back over its shoulders, a beard and a dark dorsal stripe."""
    r.pal.update(pal(base=(136, 118, 98), belly=(206, 194, 176), back=(110, 94, 78), mark=(74, 62, 52), muzzle=(150, 132, 112), ear=(130, 112, 94),
                     horn=(160, 140, 110), hoof=(54, 46, 40), pattern="saddle"))
    quad(r, 13, (6.5, 7), (6, 6.5), 12.5, [(0.5, 2, (0.3, 1.2)), (0.5, 2, (0.3, 1.2))], (4, 3, 3.5, 0.75), (4.5, 4, 4.5, 2, 3, 2.6),
         tail_spec=(1, [2], [1.4], -0.3), ears=(2, 1.3, 1.6))
    for s, sx in ((0, 1), (1, -1)):
        prev = "head"
        segs = [(3, 2), (3, 1.8), (3, 1.6), (2.6, 1.3), (2.2, 1)]
        for i, (ln, w) in enumerate(segs):
            pv = [sx * 1.2, 1.7, -3] if i == 0 else [0, segs[i - 1][0] - 0.3, 0]
            boxes = [r.box(-w / 2, 0, -w / 2, w, ln, w, "horn", "plain")]
            boxes.append(r.box(-w / 2 - 0.15, ln * 0.4, -w / 2 - 0.15, w + 0.3, 0.5, w + 0.3, "horn", "plain"))
            prev = r.part(f"horn{s}_{i}", prev, pv, boxes, "crest", rot=(-0.55, 0, sx * 0.12) if i == 0 else (-0.5, 0, sx * 0.05), rel=True, side=s)
    r.part("beard", "jaw", [0, -1.2, -2], [r.box(-0.75, -2.5, -1, 1.5, 2.5, 1.5, "back", "plain")], "decor", rel=True)


@design("ledgewing", gait="biped", leg_len=3, shadow=0.5)
def ledgewing(r: Rig):
    """Pterosaur: a long toothless beak counterbalanced by a swept-back bony crest, membrane wings on a long finger, short body."""
    r.pal.update(pal(base=(114, 94, 82), belly=(176, 156, 136), back=(90, 72, 62), wing=(156, 106, 84), wingtip=(140, 92, 72), bone=(100, 74, 60),
                     beak=(214, 190, 150), accent=(190, 70, 50), wing_pattern="membrane", wingtip_pattern="membrane", pattern="scales"))
    body = r.part("body", "root", [0, 6, 0], [r.box(-2, -2, -3, 4, 4, 6, "base", "body")], "body", rot=(-0.3, 0, 0))
    r.part("neck", body, [0, 8, -2.5], [r.box(-1, -1, -1, 2, 5, 2, "base", "neck")], "neck", rot=(-0.4, 0, 0))
    r.part("head", "neck", [0, 4.6, -0.3], [r.box(-1.25, -1.2, -3, 2.5, 2.6, 3.5, "base", "head"), r.box(-0.7, -1, -9, 1.4, 1.6, 6.2, "beak", "head")],
           "head", rel=True, rot=(0.6, 0, 0))
    r.part("jaw", "head", [0, -1, -2.5], [r.box(-0.6, -0.6, -6.3, 1.2, 0.6, 6.4, "beak", "plain")], "jaw", rel=True)
    r.part("crest", "head", [0, 1, 0], [r.box(-0.3, 0, 0, 0.6, 2.5, 5, "accent", "plain")], "crest", rot=(-0.3, 0, 0), rel=True)
    r.eye("head", "east", 1, 0, 1)
    r.eye("head", "west", -2, 0, 1)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"wing{s}", body, [sx * 2, 7.5, -2.5], [r.box(0 if s == 0 else -6, -0.4, 0, 6, 0.8, 6, "wing", "wing")], "wing", side=s, rot=(0, 0, sx * 0.9), unfold=[0, 0, -sx * 0.9])
        r.part(f"wing{s}_outer", f"wing{s}", [sx * 6, 0, 0], [r.box(0 if s == 0 else -8, -0.25, 0, 8, 0.5, 4, "wingtip", "wing"),
                                                          r.box(0 if s == 0 else -8, -0.45, -0.3, 8, 0.9, 0.9, "bone", "plain")], "wing_outer", side=s, rel=True,
               rot=(0, 0, sx * -2.6), unfold=[0, 0, sx * 2.6])
        r.part(f"leg{s}", body, [sx * 1.3, 4.5, 1.5], [r.box(-0.5, -4.5, -0.5, 1, 4.5, 1, "bone", "limb"), r.box(-0.8, -4.5, -1.5, 1.6, 0.5, 2, "bone", "plain")],
               "leg_upper", side=s, phase=s, index=1, rot=(0.3, 0, 0))
    tail(r, body, 0, 6, 3, 2, [3, 3], [0.8, 0.6], droop=0.2)


@design("rockpip", leg_len=2, shadow=0.3)
def rockpip(r: Rig):
    """Pika: a round tailless ball of fur with big round ears, tiny feet and a twitching nose."""
    r.pal.update(pal(base=(146, 126, 104), belly=(206, 192, 168), back=(120, 102, 84), ear=(120, 102, 84), muzzle=(176, 156, 134), paw=(96, 80, 66),
                     pattern="fur", mark=(230, 220, 200)))
    body = r.part("body", "root", [0, 3.5, 0], [r.box(-2.75, -2.5, -3, 5.5, 5, 6, "base", "body")], "body")
    r.part("head", body, [0, 5, -3], [r.box(-2.25, -2, -3, 4.5, 4, 3.4, "base", "head"), r.box(-1, -1.8, -3.6, 2, 1.6, 0.8, "muzzle", "head")], "head")
    r.mark("head", "north", "nostrils", box=1)
    r.eye("head", "east", 1, 1, 1)
    r.eye("head", "west", -2, 1, 1)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"ear{s}", "head", [sx * 1.4, 1.8, -1.2], [r.box(-1.25, 0, -0.4, 2.5, 2.4, 0.8, "ear", "plain"), r.box(-0.9, 0.4, -0.5, 1.8, 1.6, 0.2, "mark", "plain")],
               "ear", rot=(0, 0, -sx * 0.4), rel=True, side=s)
        for k, z in enumerate((-2, 2)):
            r.part(f"leg{k * 2 + s}", body, [sx * 1.6, 1.2, z], [r.box(-0.8, -1.2, -1, 1.6, 1.4, 1.8, "paw", "limb")], "leg_upper", side=s, index=k,
                   phase=(k + s) % 2)


# ================================================================ EMERALD WETLANDS

@design("bogcroaker", leg_len=2, shadow=0.35)
def bogcroaker(r: Rig):
    """Frog: a squat wide body, a huge mouth line, bulging eyes on top, long folded jumping legs and a throat sac."""
    r.pal.update(pal(base=(84, 124, 62), belly=(206, 214, 146), back=(64, 100, 48), mark=(44, 64, 32), eyeball=(206, 190, 90), paw=(74, 110, 54),
                     sac=(220, 214, 150), pattern="spots", eye=(30, 24, 10)))
    body = r.part("body", "root", [0, 3.8, 0], [r.box(-3.5, -2, -3, 7, 3.8, 6.5, "base", "body")], "body", rot=(-0.12, 0, 0))
    r.part("head", body, [0, 4.5, -3], [r.box(-3.5, -1, -3.5, 7, 2.6, 3.6, "base", "head")], "head")
    r.part("jaw", "head", [0, -1, -0.2], [r.box(-3.4, -1.2, -3.4, 6.8, 1.2, 3.6, "belly", "head")], "jaw", rel=True)
    r.part("sac", "jaw", [0, -1.2, -1.5], [r.box(-1.5, -1, -1, 3, 1, 2.2, "sac", "plain")], "decor", rel=True)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"eyebump{s}", "head", [sx * 2.2, 1.4, -2.2], [r.box(-1, 0, -1, 2, 1.6, 2, "eyeball", "plain")], "decor", rel=True)
        r.eye(f"eyebump{s}", "north", 0, 0, 2)
        r.part(f"leg{s}", body, [sx * 2.5, 2.2, -2], [r.box(-0.6, -2.2, -0.6, 1.2, 2.4, 1.2, "base", "limb"), r.box(-1, -2.2, -1.6, 2, 0.4, 2, "paw", "plain")],
               "leg_upper", side=s, index=0, phase=0)
        r.part(f"haunch{s}", body, [sx * 3.2, 2.6, 2.5], [r.box(-1 if s == 0 else -1, -1.2, -3.5, 2, 2.2, 4.5, "base", "limb")], "leg_upper", side=s, index=1,
               rot=(0, sx * 0.25, 0))
        r.part(f"haunch{s}_lower", f"haunch{s}", [0, -1, -3.2], [r.box(-0.8, -1.3, 0, 1.6, 1.3, 4.8, "base", "limb"), r.box(-1.4, -1.4, 4.4, 2.8, 0.4, 2.4, "paw", "plain")],
               "leg_lower", rel=True, side=s, index=1)


@design("stiltwader", gait="biped", leg_len=14, shadow=0.4)
def stiltwader(r: Rig):
    """Heron: stilt legs with backward knees, a slim body, an S-curved neck held coiled, a dagger beak and trailing head plumes."""
    r.pal.update(pal(base=(204, 212, 222), belly=(240, 244, 250), back=(150, 164, 184), beak=(222, 180, 80), skin=(196, 160, 90),
                     wing=(170, 182, 200), wingtip=(64, 74, 94), accent=(60, 70, 90), pattern="feathers"))
    body = r.part("body", "root", [0, 15, 0], [r.box(-2.25, -2.5, -3.5, 4.5, 5, 7, "base", "body"), r.box(-1.75, -1.5, 3.5, 3.5, 3, 2.5, "wing", "body")],
                  "body", rot=(-0.25, 0, 0))
    r.part("neck", body, [0, 16.5, -3], [r.box(-0.8, -0.8, -0.8, 1.6, 5, 1.6, "base", "neck")], "neck", rot=(0.55, 0, 0))
    r.part("neck2", "neck", [0, 4.6, 0], [r.box(-0.8, 0, -0.8, 1.6, 4, 1.6, "base", "neck")], "neck", rel=True, rot=(-1.1, 0, 0))
    r.part("head", "neck2", [0, 3.8, 0], [r.box(-1.1, -0.9, -2.4, 2.2, 2.2, 3, "base", "head")], "head", rel=True, rot=(0.5, 0, 0))
    r.part("beak", "head", [0, -0.3, -2.4], [r.box(-0.5, -0.4, -5, 1, 0.9, 5, "beak", "plain")], "jaw", rel=True)
    r.part("plumes", "head", [0, 0.8, 0.5], [r.box(-0.2, 0, 0, 0.4, 0.4, 4, "accent", "plain")], "crest", rot=(-0.4, 0, 0), rel=True)
    r.eye("head", "east", 0, 0, 1)
    r.eye("head", "west", -1, 0, 1)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"wing{s}", body, [sx * 2.25, 16.5, -2.5], [r.box(0 if s == 0 else -0.8, -3, 0, 0.8, 4, 7, "wing", "wing")], "wing", side=s,
               rot=(0, 0, sx * 0.05))
        r.part(f"leg{s}", body, [sx * 1.2, 13, 0], [r.box(-0.5, -6, -0.5, 1, 6.3, 1, "skin", "limb")], "leg_upper", rot=(-0.25, 0, 0), side=s, phase=s, index=1)
        r.part(f"leg{s}_lower", f"leg{s}", [0, -6, 0], [r.box(-0.4, -7.4, -0.4, 0.8, 7.6, 0.8, "skin", "limb")], "leg_lower", rot=(0.28, 0, 0), rel=True,
               side=s, phase=s, index=1)
        r.part(f"leg{s}_foot", f"leg{s}_lower", [0, -7.3, 0], [r.box(-1.3, -0.4, -3, 2.6, 0.4, 4, "skin", "plain")], "foot", rot=(-0.03, 0, 0), rel=True, side=s)


@design("rillpup", leg_len=2, shadow=0.3)
def rillpup(r: Rig):
    """Otter: a long low supple body, a broad whiskered head with small round ears, webbed paws and a thick tapering rudder tail."""
    r.pal.update(pal(base=(104, 78, 56), belly=(178, 152, 124), back=(84, 62, 44), muzzle=(190, 166, 140), ear=(90, 68, 50), paw=(70, 52, 40),
                     pattern="fur", whisker=(230, 220, 200)))
    quad(r, 12, (5, 4.2), (5, 4.4), 4.5, [(0.5, 1.6, (0.8, 0.5)), (0.5, 1.8, (0.8, 0.5))], (2, 3.6, 3.4, 0.3), (4.5, 3.6, 3, 2, 3.2, 2.2),
         tail_spec=(3, [3, 3, 3], [3, 2.2, 1.2], 0.15), ears=(1, 1.2, 1.4), hooves=False)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"whisker{s}", "head", [sx * 1.6, -1, -4.3], [r.box(0 if s == 0 else -2, 0, 0, 2, 0.1, 0.1, "whisker", "plain")], "decor", rel=True)


@design("glassdart", gait="none", leg_len=0, shadow=0.15)
def glassdart(r: Rig):
    """Damselfly: a needle-thin jewel-green abdomen, wide-set eyes, four long glass wings held swept back."""
    r.pal.update(pal(base=(60, 166, 150), belly=(120, 206, 190), head=(50, 140, 130), accent=(40, 100, 90), wing=(214, 242, 250), wingtip=(196, 232, 244),
                     eyeball=(30, 80, 120), pattern="chitin"))
    body = r.part("body", "root", [0, 9, 0], [r.box(-0.75, -0.75, -1, 1.5, 1.5, 2.5, "base", "body")], "body")
    a = r.part("abdomen", body, [0, 9, 1.5], [r.box(-0.4, -0.4, 0, 0.8, 0.8, 4, "base", "tail")], "tail", chain=0)
    r.part("abdomen2", a, [0, 0, 4], [r.box(-0.35, -0.35, 0, 0.7, 0.7, 3, "accent", "tail")], "tail", rel=True, chain=1)
    r.part("head", body, [0, 9.2, -1], [r.box(-1.5, -0.6, -1, 3, 1.2, 1, "head", "head")], "head")
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"eyeball{s}", "head", [sx * 1.2, 0, -0.5], [r.box(-0.6, -0.6, -0.6, 1.2, 1.2, 1.2, "eyeball", "plain")], "decor", rel=True)
        r.part(f"wing{s}", body, [sx * 0.6, 9.9, -0.5], [r.box(0 if s == 0 else -5, 0, -0.4, 5, 0.15, 1.1, "wing", "wing")], "wing", side=s,
               rot=(0, -sx * 0.35, 0))
        r.part(f"wing{s}_outer", body, [sx * 0.6, 9.9, 0.6], [r.box(0 if s == 0 else -4.6, 0, -0.4, 4.6, 0.15, 1, "wingtip", "wing")], "wing", side=s,
               rot=(0, -sx * 0.6, 0))


# ================================================================ PETRIFIED FOREST

@design("stonehide", leg_len=2, shadow=0.4)
def stonehide(r: Rig):
    """Armadillo: banded stone armour arched over a low body, a long pointed snout, small ears and a ringed armoured tail."""
    r.pal.update(pal(base=(156, 136, 116), belly=(196, 176, 156), shell=(140, 124, 104), band=(118, 104, 88), paw=(110, 96, 84), ear=(150, 130, 110),
                     muzzle=(170, 150, 130), shell_pattern="plates", band_pattern="plates", pattern="scales", mark=(120, 104, 88)))
    body = r.part("body", "root", [0, 3.8, 0], [r.box(-3, -2, -4, 6, 4, 8, "belly", "plain"), r.box(-3.4, -0.6, -4.4, 6.8, 3.6, 3, "shell", "plain"),
                                                 r.box(-3.5, -0.4, -1.4, 7, 4, 2.8, "band", "plain"), r.box(-3.4, -0.6, 1.4, 6.8, 3.6, 3, "shell", "plain")], "body")
    r.part("head", body, [0, 4, -4], [r.box(-1.6, -1.4, -2.6, 3.2, 2.8, 2.6, "shell", "head"), r.box(-0.8, -1.4, -5, 1.6, 1.6, 2.6, "muzzle", "head")],
           "head")
    r.eye("head", "east", 1, 1, 1)
    r.eye("head", "west", -2, 1, 1)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"ear{s}", "head", [sx * 1, 1.2, -0.6], [r.box(-0.5, 0, -0.3, 1, 1.5, 0.6, "ear", "plain")], "ear", rot=(0.3, 0, -sx * 0.4), rel=True, side=s)
        for k, z in enumerate((-2.5, 2.5)):
            r.part(f"leg{k * 2 + s}", body, [sx * 2.2, 2, z], [r.box(-0.8, -2, -0.8, 1.6, 2.2, 1.6, "paw", "limb")], "leg_upper", side=s, index=k,
                   phase=(k + s) % 2)
    tail(r, body, 0, 3, 4, 2, [2.5, 2.5], [1.6, 1], droop=0.35, mat="band")


@design("dustbeak", gait="biped", leg_len=3, shadow=0.4)
def dustbeak(r: Rig):
    """Vulture: a hunched dark body, a bare pale head on a thin neck rising from a ruff, a hooked beak and broad fingered wings."""
    r.pal.update(pal(base=(92, 76, 66), belly=(130, 112, 98), back=(70, 58, 50), wing=(76, 64, 58), wingtip=(56, 48, 44), beak=(214, 190, 140),
                     skin=(200, 150, 130), ruff=(220, 210, 196), pattern="feathers"))
    bird(r, 7, 5, 5, 7, (3, 1.4), (2.4, 2.4, 3), (2, 1.2), (8, 5), 3.5, (0.8, 0.8))
    r.by["head"]["boxes"][0]["mat"] = "skin"
    r.by["neck"]["boxes"][0]["mat"] = "skin"
    r.part("ruff", "body", [0, 9, -3], [r.box(-2.75, -1.2, -1, 5.5, 2.4, 2.6, "ruff", "plain")], "decor")
    r.part("hook", "beak", [0, -0.6, -2], [r.box(-0.5, -0.9, -0.5, 1, 1, 0.8, "beak", "plain")], "decor", rel=True)


@design("petrisloth", leg_len=7, glows=True)
def petrisloth(r: Rig):
    """Stone sloth: a shaggy hunched body, long arms ending in hook claws, a small round face mask, amber crystal moss on the back."""
    r.pal.update(pal(base=(134, 124, 108), belly=(170, 160, 146), back=(110, 100, 88), muzzle=(196, 186, 166), mark=(80, 70, 60), horn=(220, 210, 190),
                     crystal=(232, 182, 92), paw=(96, 86, 76), pattern="fur", glow_mats=("crystal",)))
    body = r.part("body", "root", [0, 9, 0], [r.box(-4, -4, -4, 8, 8.5, 9, "base", "body"), r.box(-3.5, 4, -3, 7, 1.5, 6, "base", "body")], "body",
                  rot=(-0.2, 0, 0))
    r.part("head", body, [0, 13, -4], [r.box(-2.5, -2.3, -3.5, 5, 4.6, 4, "base", "head"), r.box(-1.75, -1.6, -3.8, 3.5, 2.8, 0.5, "muzzle", "head")], "head")
    r.mark("head", "north", "stripe_v", (70, 60, 52), 1, box=1)
    r.mark("head", "north", "stripe_v", (70, 60, 52), 2, box=1)
    r.eye("head", "north", 1, 1, 1)
    r.eye("head", "north", -2, 1, 1)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"arm{s}", body, [sx * 4, 11, -2.5], [r.box(-1.2, -6.5, -1.2, 2.4, 7, 2.4, "base", "limb")], "leg_upper", rot=(-0.2, 0, sx * -0.1), side=s,
               index=0, phase=s)
        r.part(f"arm{s}_lower", f"arm{s}", [0, -6.3, 0], [r.box(-1, -4.5, -1, 2, 4.8, 2, "base", "limb"), r.box(-1, -5.2, -2, 2, 0.8, 2.5, "horn", "plain")],
               "leg_lower", rot=(0.15, 0, 0), rel=True, side=s, index=0, phase=s)
        r.part(f"leg{s}", body, [sx * 3, 6, 3.5], [r.box(-1.3, -6, -1.3, 2.6, 6.3, 2.6, "base", "limb"), r.box(-1.3, -6, -2.4, 2.6, 0.8, 3, "paw", "plain")],
               "leg_upper", side=s, index=1, phase=1 - s)
    for i, (x, z, h) in enumerate([(-1.5, -1, 2), (1.5, 1.5, 1.6), (0, 3.5, 1.2)]):
        crystal(r, f"moss{i}", "body", [x, 5, z], h, 1.4, (0.1, 0, x * 0.2))


# ================================================================ CRIMSON BASIN

@design("bloodhorn", leg_len=8)
def bloodhorn(r: Rig):
    """Bison-like grazer: a towering shoulder hump of dark mane, a low broad head with short forward-hooking horns, slim hindquarters."""
    r.pal.update(pal(base=(124, 34, 38), belly=(170, 72, 62), back=(92, 24, 28), mane=(64, 18, 22), muzzle=(80, 26, 28), horn=(226, 206, 170),
                     hoof=(40, 20, 20), pattern="fur", mane_pattern="fur"))
    quad(r, 13, (8, 9), (6, 7), 12, [(0.5, 2.6, (0.3, 1)), (0.5, 2.2, (0.3, 1))], (1, 4, 4, -0.2), (6, 5.5, 4.5, 2, 4.5, 3.5),
         tail_spec=(2, [2.5, 2], [1, 1.4], 1.0), hump=(7, 3, 0.5, 6, "mane"))
    r.part("forelock", "head", [0, 2.5, -2], [r.box(-3.25, 0, -2.6, 6.5, 1.5, 4, "mane", "plain")], "decor", rel=True)
    r.part("beard", "head", [0, -2.75, -2], [r.box(-1.5, -2.5, -1, 3, 2.5, 2.5, "mane", "plain")], "decor", rel=True)
    horn_pair(r, "head", 3, 1.5, -2, [(2.4, 1.6), (2, 1.2), (1.6, 0.9)], (0, 0, 1.4), (0.7, 0, -0.5), "horn")


@design("thornstalker", leg_len=5)
def thornstalker(r: Rig):
    """Thorn cat-lizard: a long low crouching hunter covered in backward thorns, a wedge head, a spiked whip tail held high."""
    r.pal.update(pal(base=(114, 28, 34), belly=(156, 56, 54), back=(84, 20, 26), mark=(66, 16, 20), muzzle=(100, 24, 30), spine=(226, 206, 184),
                     paw=(70, 18, 22), horn=(226, 206, 184), eye=(240, 220, 80), pattern="stripes"))
    quad(r, 14, (6, 5), (5.5, 4.6), 6.5, [(0.5, 2, (0.6, 0.6)), (0.45, 2, (0.6, 0.6))], (2, 3, 3, 0.15), (4.5, 4, 4, 2.5, 3.5, 2),
         tail_spec=(4, [3, 3, 3, 3], [2, 1.6, 1.3, 1], -0.25), hooves=False, stance=0.45)
    r.eye("head", "east", 1, 1, 2, "slit")
    r.eye("head", "west", -3, 1, 2, "slit")
    spines(r, "body", 2.6, -6, 6, 7, 2.4, "spine", w=0.8, lean=0.9)
    for i in range(1, 4):
        r.part(f"tailthorn{i}", f"tail{i}", [0, 0.5, 1], [r.box(-0.3, 0, -0.3, 0.6, 1.6, 0.6, "spine", "plain")], "decor", rot=(0.8, 0, 0), rel=True)
    r.part("stinger", "tail3", [0, 0, 3], [r.box(-0.6, -0.6, 0, 1.2, 1.2, 1.6, "spine", "plain")], "decor", rel=True)
    r.by["tail1"]["rot"] = [0.35, 0, 0]
    r.by["tail2"]["rot"] = [0.45, 0, 0]
    r.by["tail3"]["rot"] = [0.5, 0, 0]


@design("toxitoad", leg_len=2, shadow=0.35)
def toxitoad(r: Rig):
    """Poison toad: a lumpy, warty acid-green body with violet warning blotches, swollen poison glands behind the eyes, a wide mouth."""
    r.pal.update(pal(base=(150, 200, 64), belly=(222, 230, 144), back=(122, 170, 52), mark=(130, 44, 150), gland=(176, 70, 170), eyeball=(240, 180, 40),
                     paw=(120, 160, 50), pattern="spots", eye=(30, 20, 10)))
    body = r.part("body", "root", [0, 3.6, 0], [r.box(-3.5, -2.2, -3, 7, 4.6, 6.5, "base", "body"), r.box(-2.5, 2.2, -2, 5, 1, 4, "base", "body")], "body",
                  rot=(-0.15, 0, 0))
    r.part("head", body, [0, 4.5, -3], [r.box(-3, -1.2, -3, 6, 2.8, 3.2, "base", "head")], "head")
    r.part("jaw", "head", [0, -1.2, -0.2], [r.box(-2.9, -1, -2.8, 5.8, 1, 3, "belly", "head")], "jaw", rel=True)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"eyebump{s}", "head", [sx * 1.9, 1.4, -2], [r.box(-0.9, 0, -0.9, 1.8, 1.3, 1.8, "eyeball", "plain")], "decor", rel=True)
        r.eye(f"eyebump{s}", "north", 0, 0, 1)
        r.part(f"gland{s}", body, [sx * 2.6, 6, -2.4], [r.box(-0.9, 0, -0.5, 1.8, 1.2, 2.5, "gland", "plain")], "decor")
        r.part(f"leg{s}", body, [sx * 2.5, 2.2, -2], [r.box(-0.6, -2.2, -0.6, 1.2, 2.4, 1.2, "base", "limb"), r.box(-1, -2.2, -1.4, 2, 0.4, 1.8, "paw", "plain")],
               "leg_upper", side=s, index=0)
        r.part(f"haunch{s}", body, [sx * 3.2, 2.3, 2], [r.box(-1.2, -1.4, -2.5, 2.4, 2.6, 4, "base", "limb"), r.box(-1.2, -2.3, -3.6, 2.4, 0.6, 2.4, "paw", "plain")],
               "leg_upper", side=s, index=1)


# ================================================================ SUNKEN GARDENS

@design("reef_darter", gait="fish", leg_len=0, shadow=0.15)
def reef_darter(r: Rig):
    """Reef fish: a tall laterally flat disc of a body with bold vertical bands, a tall sail dorsal fin and a small pouting mouth."""
    r.pal.update(pal(base=(250, 124, 62), belly=(255, 220, 160), back=(230, 100, 50), fin=(80, 124, 255), band=(246, 246, 250), pattern="", mark=(60, 60, 200)))
    fish(r, 6, 1.6, 5, 5.5, 2.5, 3, 1.2, head_frac=0.3)
    for i, z in enumerate((-1.3, 0.8)):
        r.part(f"band{i}", "body", [0, 5.5, z], [r.box(-0.85, -2.6, 0, 1.7, 5.2, 0.7, "band", "plain")], "decor")


@design("coral_crab", gait="crab", leg_len=3, shadow=0.4)
def coral_crab(r: Rig):
    """Decorator crab: a flat salmon shell overgrown with branching coral and anemone tufts, slender long claws."""
    r.pal.update(pal(base=(232, 104, 92), belly=(250, 180, 160), back=(210, 84, 74), accent=(200, 70, 60), coral=(255, 150, 190), polyp=(255, 226, 120),
                     eyeball=(40, 20, 20), pattern="spots", mark=(255, 214, 200)))
    crab(r, 8, 6, 2.6, 3, 4, 3)
    for i, (x, z) in enumerate([(-1.5, -0.5), (1.6, 0.8), (0, 1.8)]):
        b = chain(r, f"coral{i}_", "body", [x, 1.8, z], [(1.6, 0.7), (1.2, 0.6)], (0.2 * (i - 1), 0, x * 0.25), (0.4, 0, 0.5), "coral")
        r.part(f"polyp{i}", b, [0, 1, 0], [r.box(-0.5, 0, -0.5, 1, 0.8, 1, "polyp", "plain")], "decor", rel=True)


@design("veilray", gait="fish", leg_len=0, shadow=1.0, glows=True)
def veilray(r: Rig):
    """Manta: a broad flat diamond of a body with wide flapping wing-fins, head lobes, pale belly, glowing spot rows and a whip tail."""
    r.pal.update(pal(base=(52, 62, 112), belly=(206, 214, 232), back=(40, 48, 92), wing=(62, 72, 126), wingtip=(52, 60, 108), glowspot=(150, 200, 255),
                     pattern="", glow_mats=("glowspot",)))
    body = r.part("body", "root", [0, 6, 0], [r.box(-4, -1.2, -6, 8, 2.4, 12, "base", "body"), r.box(-3, 1.2, -4, 6, 0.6, 8, "back", "body")], "body")
    r.part("head", body, [0, 6, -6], [r.box(-3, -1, -2, 6, 2, 2.2, "base", "head")], "head")
    r.eye("head", "east", 0, 0, 1)
    r.eye("head", "west", -1, 0, 1)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"lobe{s}", "head", [sx * 2.2, -0.3, -2], [r.box(-0.5, -0.5, -2.5, 1, 1, 2.6, "base", "plain")], "antenna", rot=(0.3, 0, 0), rel=True, side=s)
        r.part(f"wing{s}", body, [sx * 4, 6, -3], [r.box(0 if s == 0 else -8, -0.6, 0, 8, 1.2, 8, "wing", "wing")], "fin", side=s)
        r.part(f"wing{s}_outer", f"wing{s}", [sx * 8, 0, 1.5], [r.box(0 if s == 0 else -6, -0.4, 0, 6, 0.8, 4.5, "wingtip", "wing")], "fin_outer", side=s,
               rel=True, rot=(0, -sx * 0.35, 0))
        for k in range(3):
            r.part(f"spot{s}{k}", f"wing{s}", [sx * (2 + k * 2), 0.61, 3 + k * 0.6], [r.box(-0.5, 0, -0.5, 1, 0.05, 1, "glowspot", "plain")], "decor",
                   rel=True)
    s0 = r.part("seg0", body, [0, 6, 6], [r.box(-0.5, -0.4, 0, 1, 0.8, 6, "base", "tail")], "segment", chain=0)
    r.part("seg1", s0, [0, 0, 6], [r.box(-0.3, -0.3, 0, 0.6, 0.6, 7, "base", "tail")], "segment", rel=True, chain=1)


@design("stoneface", gait="fish", leg_len=0, shadow=0.3)
def stoneface(r: Rig):
    """Stonefish: a lumpy, warty camouflaged body with a grim upturned mouth, venomous spines raised along the back, fan pectorals."""
    r.pal.update(pal(base=(124, 114, 102), belly=(164, 154, 140), back=(100, 92, 82), mark=(90, 82, 72), fin=(150, 96, 84), spine=(176, 64, 52),
                     wart=(140, 128, 112), pattern="spots"))
    fish(r, 9, 5, 5, 5, 3, 0, 3.5, head_frac=0.4)
    spines(r, "body", 2.4, -1.5, 1.5, 5, 2.4, "spine", w=0.6, lean=0.25)
    for i, (x, y, z) in enumerate([(2.5, 2, -2), (-2.5, 1.5, -0.5), (2.5, 0.5, 0.5), (-2.4, -1, -2.5), (0, 2.5, -3.5)]):
        r.part(f"wart{i}", "head" if z < -2.4 else "body", [x * 0.95, 5 + y, z], [r.box(-0.6, -0.6, -0.6, 1.2, 1.2, 1.2, "wart", "plain")], "decor")


# ================================================================ STORMSTONE CAVERNS

@design("static_beetle", gait="hexa", leg_len=3, shadow=0.4, glows=True)
def static_beetle(r: Rig):
    """Stag beetle: huge forked mandible-antlers, a glossy indigo shell with crackling glowing seams, sturdy legs."""
    r.pal.update(pal(base=(44, 44, 96), belly=(74, 74, 126), accent=(30, 30, 64), head=(36, 36, 80), elytra=(52, 52, 112), seam=(176, 150, 255),
                     pattern="chitin", elytra_pattern="chitin", glow_mats=("seam",)))
    bug(r, (4.5, 2.6, 3.5), (5, 3, 5), (3.5, 2, 2.4), 3, 5, antenna=2)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"elytron{s}", "body", [sx * 0.05, 4.5, 0.5], [r.box(0 if s == 0 else -2.6, 0, 0, 2.6, 1.8, 5.2, "elytra", "plain")], "wing", side=s,
               rot=(-0.1, 0, sx * 0.04))
        r.part(f"seam{s}", f"elytron{s}", [sx * 1.3, 1.81, 0.5], [r.box(-0.2, 0, 0, 0.4, 0.05, 4.2, "seam", "plain")], "decor", rel=True)
        m = r.part(f"mandible{s}", "head", [sx * 1.2, 0, -2.4], [r.box(-0.5, -0.5, -3, 1, 1, 3, "accent", "plain")], "jaw", rot=(-0.25, -sx * 0.4, 0),
                   rel=True, side=s)
        r.part(f"mandible{s}_tip", m, [0, 0, -3], [r.box(-0.4, -0.4, -2, 0.8, 0.8, 2, "accent", "plain"), r.box(-0.3, 0, -1.2, 0.6, 1.2, 0.6, "accent", "plain")],
               "decor", rot=(0, sx * 0.9, 0), rel=True)


@design("arcback", leg_len=4, glows=True)
def arcback(r: Rig):
    """Spark lizard: a monitor-like body with a tall sail of glowing conductor spines, a forked tongue and a long balancing tail."""
    r.pal.update(pal(base=(62, 62, 104), belly=(102, 102, 144), back=(44, 44, 80), mark=(80, 84, 140), spine=(166, 206, 255), pattern="scales",
                     glow_mats=("spine",), tongue=(220, 90, 120)))
    lizard(r, 12, 5, 3.6, 4.2, 2.6, 1.6, (3.6, 3, 3.6, 2), (4, [3.5, 3.5, 3.5, 3], [3, 2.4, 1.8, 1.2], 0.05))
    spines(r, "body", 1.8, -5, 5, 7, 4, "spine", w=0.6, lean=0.15)
    r.part("tongue", "jaw", [0, 0, -5.6], [r.box(-0.2, -0.1, -2, 0.4, 0.2, 2, "tongue", "plain")], "decor", rel=True)


@design("conduit_serpent", gait="serpent", scale=1.3, leg_len=0, shadow=0.5, glows=True)
def conduit_serpent(r: Rig):
    """Thunder serpent: a thick snake with glowing conductor rings, a fanned neck frill on ribs, and a heavy wedge head."""
    r.pal.update(pal(base=(52, 52, 112), belly=(122, 122, 182), back=(40, 40, 90), fin=(170, 140, 255), ring=(180, 220, 255), pattern="scales",
                     glow_mats=("ring",), eye=(220, 240, 255)))
    eel(r, 8, 4.5, 4, 3, 5, fin=False)
    for i in (1, 3, 5, 7):
        t = 4 * (1 - 0.6 * (i + 1) / 8)
        r.part(f"ring{i}", f"seg{i}", [0, 0, 1.5], [r.box(-t / 2 - 0.1, -t / 2 - 0.1, 0, t + 0.2, t + 0.2, 0.8, "ring", "plain")], "decor", rel=True)
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"frill{s}", "head", [sx * 2, 0.5, -0.5], [r.box(0 if s == 0 else -3.5, -2, 0, 3.5, 4.5, 0.4, "fin", "wing")], "crest", rot=(0, -sx * 0.5, 0),
               rel=True, side=s)


# ================================================================ STARLIGHT CAVERNS

@design("starwisp", gait="none", leg_len=0, shadow=0.1, glows=True)
def starwisp(r: Rig):
    """Star jelly: a tiny luminous bell with a bright core and four trailing ribbon arms."""
    r.pal.update(pal(base=(200, 222, 255), belly=(230, 240, 255), accent=(250, 252, 255), skirt=(170, 196, 255), tentacle=(180, 206, 255),
                     glow_mats=("accent", "tentacle", "base"), pattern=""))
    jelly(r, 2.5, 3.6, 12, 4, 4)


@design("moonhorn", leg_len=13, glows=True)
def moonhorn(r: Rig):
    """Moon stag: tall, fine-boned and pale, with a single long spiral glowing horn, a flowing silver mane and a plumed tail."""
    r.pal.update(pal(base=(204, 212, 236), belly=(236, 240, 250), back=(176, 186, 218), muzzle=(220, 226, 242), ear=(190, 200, 226), mane=(240, 244, 255),
                     horn=(210, 236, 255), hoof=(120, 130, 160), pattern="", glow_mats=("horn",), mark=(170, 186, 230)))
    quad(r, 13, (5.5, 6.5), (5.5, 6), 15, [(0.5, 1.8, (0.3, 1.2), 1.3), (0.48, 1.8, (0.3, 1.2), 1.3)], (7, 2.8, 3.4, 1.0),
         (4, 4, 4.5, 3, 3, 2.6), tail_spec=(2, [3, 3], [1.6, 2], 0.8), ears=(2.6, 1.4, 0.9), stance=0.25)
    chain(r, "unihorn", "head", [0, 2, -3], [(3.5, 1.2), (3.5, 1), (3, 0.7)], (0.7, 0, 0), (0.12, 0.8, 0), "horn", role="crest")
    r.part("mane", "neck", [0, 1.4, -7], [r.box(-0.6, 0, 0, 1.2, 2.4, 7.5, "mane", "plain")], "decor", rel=True)
    r.part("tailplume", "tail1", [0, 0, 1], [r.box(-1.2, -1.5, 0, 2.4, 3, 4, "mane", "plain")], "decor", rel=True)


@design("lumen_kit", leg_len=5, glows=True)
def lumen_kit(r: Rig):
    """Fennec kit: a small body, enormous glowing-lined ears, big dark eyes, and a fluffy tail ending in a glowing tuft."""
    r.pal.update(pal(base=(112, 152, 212), belly=(204, 222, 246), back=(90, 128, 190), muzzle=(196, 214, 240), ear=(120, 160, 220), paw=(80, 110, 160),
                     glowtip=(180, 244, 255), inner=(180, 244, 255), pattern="fur", glow_mats=("glowtip", "inner"), eye=(20, 30, 50)))
    quad(r, 8, (4.5, 4.5), (4.5, 4.5), 7, [(0.5, 1.5, (0.4, 0.6)), (0.48, 1.6, (0.4, 0.6))], (1.5, 2.6, 2.6, 0.5), (5, 4.4, 3.6, 2, 2.2, 2),
         tail_spec=(3, [2.5, 3, 3], [2.6, 3.2, 3], 0.8), ears=(4.5, 3, 0.45), hooves=False)
    for s in (0, 1):
        r.part(f"earinner{s}", f"ear{s}", [0, 0.4, -0.55], [r.box(-1, 0, 0, 2, 3.4, 0.1, "inner", "plain")], "decor", rel=True)
    r.part("tailtip", "tail2", [0, 0, 3], [r.box(-1.6, -1.6, 0, 3.2, 3.2, 2.4, "glowtip", "plain")], "decor", rel=True)
    r.by["tail1"]["rot"] = [0.4, 0, 0]
    r.by["tail2"]["rot"] = [0.5, 0, 0]


@design("sky_drifter", gait="none", scale=2.0, shadow=1.5, leg_len=0, glows=True)
def sky_drifter(r: Rig):
    """Sky jelly: a vast glowing domed bell with a frilled rim, ringed with long trailing tentacles and four frilled oral arms."""
    r.pal.update(pal(base=(126, 146, 224), belly=(200, 210, 250), accent=(200, 230, 255), skirt=(160, 190, 255), tentacle=(170, 196, 255),
                     arm=(210, 220, 255), glow_mats=("accent", "tentacle", "arm"), pattern=""))
    jelly(r, 9, 9, 17, 10, 16)
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        prev = r.part(f"oral{i}", "body", [math.cos(a) * 1.5, 16.5, math.sin(a) * 1.5], [r.box(-0.9, -6, -0.3, 1.8, 6, 0.6, "arm", "plain")], "tentacle",
                      index=i, chain=0, phase=i % 2, rot=(0, a, 0))
        r.part(f"oral{i}b", prev, [0, -6, 0], [r.box(-0.7, -6, -0.25, 1.4, 6, 0.5, "arm", "plain")], "tentacle", rel=True, index=i, chain=1, phase=i % 2)
