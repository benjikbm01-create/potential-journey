#!/usr/bin/env python3
"""
Stage 3 creature assets: data-driven models (assets/underdeep/creatures/<id>.json), UV-packed procedural
textures (+ emissive _glow layers), spawn-egg item models, entity loot tables, entity names, journal
lines, resource items (textures, models, recipes) and entity tags.

Each species is built from a body plan (quadruped, hexapod, crab, serpent, fish, ray, flyer, biped,
floater, blob, shelled, strider) plus species decorations (antlers, horns, manes, shells, spikes,
crystals, mushrooms, frills, lures). Parts carry animation roles consumed by the client CreatureModel.
Called from stage2_assets.py main() (or run directly after it).
"""
import json
import math
import os
import random
import sys
import zlib

sys.path.insert(0, os.path.dirname(__file__))
import art  # noqa: E402
import creature_designs  # noqa: E402
import creature_kit  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "underdeep")
DATA = os.path.join(ROOT, "data")
NS = "underdeep"
LANG = {}
TAGS = {}


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=1)
        f.write("\n")


# ============================================================================== model building

class Model:
    def __init__(self, sid, pal):
        self.sid = sid
        self.pal = pal
        self.parts = []
        self.names = set()

    def part(self, name, parent, pivot, boxes, role="decor", rot=None, **kw):
        assert name not in self.names, name
        self.names.add(name)
        p = {"name": name, "parent": parent, "pivot": [round(v, 3) for v in pivot], "role": role,
             "boxes": [dict(b) for b in boxes]}
        if rot:
            p["rot"] = [round(v, 4) for v in rot]
        for k, v in kw.items():
            if k.startswith("_"):
                p[k] = v
            else:
                p[k] = v
        self.parts.append(p)
        return name


def box(fr, size, mat="base", mirror=False):
    return {"from": [round(v, 3) for v in fr], "size": [round(max(v, 0), 3) for v in size], "_mat": mat, "mirror": mirror}


def leg_set(m, parent, xs, zs, top_y, length, w, mat="base", phases=None, hexa=False):
    """Legs hanging from y=top_y (model space, relative to root) down to the ground (y=24)."""
    n = 0
    for zi, z in enumerate(zs):
        for xi, x in enumerate(xs):
            side = 0 if x > 0 else 1
            ph = phases[n] if phases else ((zi + xi) % 2)
            rot = None
            if hexa:
                rot = [0, 0, (0.55 if side == 0 else -0.55)]
            m.part(f"leg{n}", parent, [x, top_y, z], [box([-w / 2, 0, -w / 2], [w, length, w], mat)], "leg",
                   rot=rot, phase=ph, index=0 if zi == 0 else 1, side=side)
            n += 1


def quadruped(m, L, W, H, leg, legw, neck=0.0, neck_up=0.6, head=(5, 5, 5), snout=0, tail=0, tail_w=2, tail_up=-0.5,
              ears=0, jaw=False, leg_mat="base"):
    body_y = 24 - leg - H / 2
    m.part("body", "root", [0, body_y, 0], [box([-W / 2, -H / 2, -L / 2], [W, H, L])], "body")
    lx = W / 2 - legw / 2
    leg_set(m, "root", [lx, -lx], [-L / 2 + legw / 2 + 0.5, L / 2 - legw / 2 - 0.5], 24 - leg, leg, legw, leg_mat)
    hh, hw, hl = head
    if neck > 0:
        nw = max(2, min(W - 2, hw - 1))
        m.part("neck", "body", [0, -H / 2 + nw / 2, -L / 2 + 1], [box([-nw / 2, -nw / 2, -neck], [nw, nw, neck])], "neck",
               rot=[-neck_up, 0, 0])
        hp = ("neck", [0, 0, -neck + 1])
    else:
        hp = ("body", [0, -H / 2 + hh / 2 - 1, -L / 2])
    m.part("head", hp[0], hp[1], [box([-hw / 2, -hh / 2, -hl], [hw, hh, hl], "head")], "head",
           rot=[neck_up * 0.8, 0, 0] if neck > 0 else None)
    if snout:
        sw = max(2, hw - 2)
        m.part("snout", "head", [0, 0, -hl], [box([-sw / 2, -hh / 2 + hh * 0.35, -snout], [sw, hh * 0.55, snout], "head")])
    if jaw:
        sw = max(2, hw - 1)
        m.part("jaw", "head", [0, hh / 2 - 1, -1], [box([-sw / 2, 0, -hl - snout + 1], [sw, 1.5, hl + snout - 1], "belly")], "jaw")
    if ears:
        for s, x in ((0, hw / 2 - 1), (1, -hw / 2 + 1)):
            m.part(f"ear{s}", "head", [x, -hh / 2, -hl + 2], [box([-0.5, -ears, -1], [1, ears, 2], "accent")], "crest",
                   rot=[-0.3, 0, (0.35 if s == 0 else -0.35)])
    if tail:
        m.part("tail0", "body", [0, -H / 2 + 1.5, L / 2], [box([-tail_w / 2, -tail_w / 2, 0], [tail_w, tail_w, tail])], "tail",
               rot=[tail_up, 0, 0], index=0)
    return body_y


def hexapod(m, L, W, H, leg, abdomen=0.0, head=(3, 4, 3), antenna=4, wings=False, glow_abdomen=False, mandible=False):
    body_y = 24 - leg * 0.7 - H / 2
    m.part("body", "root", [0, body_y, 0], [box([-W / 2, -H / 2, -L / 2], [W, H, L])], "body")
    if abdomen:
        aw = W + 1
        m.part("abdomen", "body", [0, -H / 4, L / 2], [box([-aw / 2, -H / 2 - 0.5, 0], [aw, H + 1, abdomen],
                                                         "glow" if glow_abdomen else "pattern")], "tail", rot=[-0.15, 0, 0], index=0)
    hh, hw, hl = head
    m.part("head", "body", [0, -H / 4, -L / 2], [box([-hw / 2, -hh / 2, -hl], [hw, hh, hl], "head")], "head")
    if mandible:
        for s, x in ((0, 1), (1, -1)):
            m.part(f"mand{s}", "head", [x, hh / 2 - 1, -hl], [box([-0.5, 0, -2], [1, 1, 2], "accent")], "jaw")
    if antenna:
        for s, x in ((0, hw / 2 - 1), (1, -hw / 2 + 1)):
            m.part(f"ant{s}", "head", [x, -hh / 2, -hl + 1], [box([-0.5, -antenna, -0.5], [1, antenna, 1], "accent")], "antenna",
                   rot=[-0.6, 0, (0.3 if s == 0 else -0.3)], index=s)
    zs = [-L / 2 + 1, 0, L / 2 - 1]
    leg_set(m, "root", [W / 2, -W / 2], zs, body_y + H / 4, leg, 1, "accent", phases=[0, 1, 1, 0, 0, 1], hexa=True)
    if wings:
        for s, x in ((0, 1), (1, -1)):
            m.part(f"wing{s}", "body", [x * W / 2, -H / 2, -L / 4], [box([0 if s == 0 else -L, 0, 0], [L, 0, L * 0.8], "wing")],
                   "wing", side=s)


def crab(m, W, L, H, leg, claw=3):
    body_y = 24 - leg * 0.6 - H / 2
    m.part("body", "root", [0, body_y, 0], [box([-W / 2, -H / 2, -L / 2], [W, H, L])], "body")
    zs = [-L / 4, 0, L / 4]
    leg_set(m, "root", [W / 2 - 0.5, -W / 2 + 0.5], zs, body_y + H / 4, leg, 1, "accent", phases=[0, 1, 1, 0, 0, 1], hexa=True)
    for s, x in ((0, W / 2), (1, -W / 2)):
        m.part(f"claw{s}", "body", [x * 0.8, 0, -L / 2], [box([-claw / 2, -claw / 2, -claw - 1], [claw, claw, claw + 1], "accent")],
               "arm", rot=[0, (-0.4 if s == 0 else 0.4), 0], phase=s)
    for s, x in ((0, 1.5), (1, -1.5)):
        m.part(f"eye{s}", "body", [x, -H / 2, -L / 2 + 1], [box([-0.5, -2, -0.5], [1, 2, 1], "eye")], "antenna", index=s)


def serpent(m, segs, seg_len, thick, head_len, y_center=None, fins=False, frill=False, glow_spots=False):
    y = y_center if y_center is not None else 24 - thick / 2
    m.part("head", "root", [0, y, -seg_len], [box([-thick / 2 - 0.5, -thick / 2, -head_len], [thick + 1, thick, head_len], "head")], "head")
    m.part("jaw", "head", [0, thick / 2 - 1, -1], [box([-thick / 2, 0, -head_len + 1], [thick, 1, head_len - 1], "belly")], "jaw")
    if frill:
        m.part("frill", "head", [0, -thick / 2, -1], [box([-thick, -3, 0], [thick * 2, 3, 1], "accent")], "crest")
    prev = "root"
    z = -seg_len
    for i in range(segs):
        t = thick * (1 - 0.6 * i / max(1, segs - 1))
        name = f"seg{i}"
        pivot = [0, y, z] if prev == "root" else [0, 0, seg_len]
        m.part(name, prev, pivot, [box([-t / 2, -t / 2, 0], [t, t, seg_len], "glow" if glow_spots and i % 2 == 0 else "base")],
               "segment", index=i)
        if fins and i == segs - 1:
            m.part("tailfin", name, [0, 0, seg_len], [box([0, -3, 0], [0, 6, 4], "fin")], "tailfin")
        prev = name
        z = 0


def fish(m, L, W, H, tail=4, dorsal=2, pect=2, y=None, glow=False):
    cy = y if y is not None else 24 - H / 2 - 2
    m.part("body", "root", [0, cy, 0], [box([-W / 2, -H / 2, -L / 2], [W, H, L], "glow" if glow else "base")], "body")
    m.part("head", "body", [0, 0, -L / 2], [box([-W / 2 + 0.5, -H / 2 + 0.5, -2], [W - 1, H - 1, 2], "head")], "head")
    m.part("tailfin", "body", [0, 0, L / 2], [box([0, -H / 2 - 1, 0], [0, H + 2, tail], "fin")], "tailfin")
    if dorsal:
        m.part("dorsal", "body", [0, -H / 2, -L / 4], [box([0, -dorsal, 0], [0, dorsal, L / 2], "fin")])
    if pect:
        for s, x in ((0, W / 2), (1, -W / 2)):
            m.part(f"pect{s}", "body", [x, H / 4, -L / 6], [box([0 if s == 0 else -pect, 0, 0], [pect, 0, pect + 1], "fin")], "fin",
                   side=s, phase=s * 1.5)


def ray(m, L, W, H, tail=10):
    cy = 24 - H / 2 - 4
    m.part("body", "root", [0, cy, 0], [box([-W / 4, -H / 2, -L / 2], [W / 2, H, L])], "body")
    for s in (0, 1):
        x0 = W / 4 if s == 0 else -W / 4
        m.part(f"wing{s}", "body", [x0, 0, 0], [box([0 if s == 0 else -W / 4, -0.5, -L / 2 + 1], [W / 4, 1, L - 2], "wing")], "fin",
               side=s, phase=0)
    m.part("tail0", "body", [0, 0, L / 2], [box([-0.5, -0.5, 0], [1, 1, tail], "accent")], "tail", index=0)
    m.part("head", "body", [0, 0, -L / 2], [box([-2, -H / 2, -1], [4, H, 1], "head")], "head")


def flyer(m, L, W, H, wing_span, wing_depth, head=(3, 3, 3), beak=1, tail=3, leg=2, membrane=False):
    body_y = 24 - leg - H / 2
    m.part("body", "root", [0, body_y, 0], [box([-W / 2, -H / 2, -L / 2], [W, H, L])], "body")
    hh, hw, hl = head
    m.part("head", "body", [0, -H / 2, -L / 2 + 1], [box([-hw / 2, -hh, -hl], [hw, hh, hl], "head")], "head")
    if beak:
        m.part("beak", "head", [0, -hh / 2, -hl], [box([-0.5, 0, -beak], [1, 1, beak], "accent")], "jaw")
    for s, x in ((0, W / 2), (1, -W / 2)):
        m.part(f"wing{s}", "body", [x, -H / 2 + 0.5, -L / 4], [box([0 if s == 0 else -wing_span, 0, 0],
                                                                   [wing_span, 0 if membrane else 1, wing_depth], "wing")], "wing", side=s)
    if tail:
        m.part("tail0", "body", [0, -H / 2 + 1, L / 2], [box([-W / 2 + 0.5, 0, 0], [W - 1, 0, tail], "wing")], "tail", rot=[-0.2, 0, 0])
    if leg:
        leg_set(m, "root", [W / 4, -W / 4], [0], 24 - leg, leg, 1, "accent", phases=[0, 1])


def biped(m, L, W, H, leg, legw, neck=6, head=(4, 4, 5), tail=8, arms=2, neck_up=1.0, jaw=True):
    body_y = 24 - leg - H / 2
    m.part("body", "root", [0, body_y, 0], [box([-W / 2, -H / 2, -L / 2], [W, H, L])], "body")
    lx = W / 2 - legw / 2
    for s, x in ((0, lx), (1, -lx)):
        m.part(f"leg{s}", "root", [x, 24 - leg, 0.5], [box([-legw / 2, 0, -legw / 2], [legw, leg, legw])], "leg", phase=s, index=s, side=s)
    nw = max(2, W - 2)
    m.part("neck", "body", [0, -H / 2 + 1, -L / 2 + 1], [box([-nw / 2, -nw / 2, -neck], [nw, nw, neck])], "neck", rot=[-neck_up, 0, 0])
    hh, hw, hl = head
    m.part("head", "neck", [0, 0, -neck + 1], [box([-hw / 2, -hh / 2, -hl], [hw, hh, hl], "head")], "head", rot=[neck_up * 0.9, 0, 0])
    if jaw:
        m.part("jaw", "head", [0, hh / 2 - 1, -1], [box([-hw / 2 + 0.5, 0, -hl + 1], [hw - 1, 1.5, hl - 1], "belly")], "jaw")
    if tail:
        m.part("tail0", "body", [0, -H / 2 + 1.5, L / 2], [box([-1.5, -1.5, 0], [3, 3, tail])], "tail", rot=[0.15, 0, 0], index=0)
    if arms:
        for s, x in ((0, W / 2), (1, -W / 2)):
            m.part(f"arm{s}", "body", [x, 0, -L / 2 + 2], [box([-0.5, 0, -0.5], [1, arms, 1], "accent")], "arm", phase=s, rot=[-0.6, 0, 0])


def floater(m, R, H, tentacles=6, tlen=8, y=12, glow=True):
    m.part("body", "root", [0, y, 0], [box([-R, -H, -R], [2 * R, H, 2 * R], "glow" if glow else "base")], "float", index=0)
    for i in range(tentacles):
        a = i * 2 * math.pi / tentacles
        x, z = math.cos(a) * (R - 1), math.sin(a) * (R - 1)
        m.part(f"tent{i}", "body", [x, 0, z], [box([-0.5, 0, -0.5], [1, tlen, 1], "accent")], "float", index=i + 1)


def blob(m, R, eyes=True, feet=True):
    leg = 1.5 if feet else 0
    y = 24 - leg - R
    m.part("body", "root", [0, y, 0], [box([-R, -R, -R], [2 * R, 2 * R, 2 * R])], "body")
    m.part("head", "body", [0, -R / 2, -R], [box([-R + 1, -1, -0.5], [2 * R - 2, 2, 0.5], "head")], "head")
    if feet:
        leg_set(m, "root", [R / 2, -R / 2], [-R / 2, R / 2], 24 - leg, leg, 2, "accent")


def shelled(m, L, W, H, leg, shellH, head=(3, 3, 3), neck=1):
    body_y = 24 - leg - H / 2
    m.part("body", "root", [0, body_y, 0], [box([-W / 2, -H / 2, -L / 2], [W, H, L], "belly"),
                                             box([-W / 2 - 1, -H / 2 - shellH, -L / 2 - 1], [W + 2, shellH + 1, L + 2], "shell")], "body")
    lx = W / 2 - 1
    leg_set(m, "root", [lx, -lx], [-L / 2 + 1.5, L / 2 - 1.5], 24 - leg, leg, 2)
    hh, hw, hl = head
    m.part("head", "body", [0, 0, -L / 2 - 1], [box([-hw / 2, -hh / 2, -hl - neck], [hw, hh, hl + neck], "head")], "head")


# ---------------------------------------------------------------- decorations

def antlers(m, parent, y, z, size, mat="accent"):
    for s, x in ((0, 1.5), (1, -1.5)):
        n = m.part(f"antler{s}", parent, [x, y, z], [box([-0.5, -size, -0.5], [1, size, 1], mat)], "crest",
                   rot=[-0.3, 0, (0.45 if s == 0 else -0.45)])
        m.part(f"antlerb{s}", n, [0, -size * 0.6, 0], [box([-0.5, -size * 0.5, -0.5], [1, size * 0.5, 1], mat)], rot=[0.6, 0, 0])
        m.part(f"antlerc{s}", n, [0, -size, 0], [box([-0.5, -size * 0.4, -0.5], [1, size * 0.4, 1], mat)],
               rot=[-0.2, 0, (0.5 if s == 0 else -0.5)])


def horns(m, parent, y, z, size, mat="accent", curl=0.9):
    for s, x in ((0, 1.5), (1, -1.5)):
        n = m.part(f"horn{s}", parent, [x, y, z], [box([-0.75, -size, -0.75], [1.5, size, 1.5], mat)], "crest",
                   rot=[0.4, 0, (0.3 if s == 0 else -0.3)])
        m.part(f"hornb{s}", n, [0, -size, 0], [box([-0.5, -size * 0.7, -0.5], [1, size * 0.7, 1], mat)], rot=[curl, 0, 0])


def spikes(m, parent, n, y, z0, z1, h, mat="accent", glow=False):
    for i in range(n):
        z = z0 + (z1 - z0) * (i + 0.5) / n
        m.part(f"spike{i}", parent, [0, y, z], [box([-0.5, -h, -0.5], [1, h, 1], "glow" if glow else mat)], "crest",
               rot=[0.2 * ((i % 2) * 2 - 1), 0, 0])


def crystals(m, parent, n, y, spread_x, spread_z, h, seed):
    rnd = random.Random(seed)
    for i in range(n):
        x = rnd.uniform(-spread_x, spread_x)
        z = rnd.uniform(-spread_z, spread_z)
        m.part(f"crystal{i}", parent, [x, y, z], [box([-0.75, -h * rnd.uniform(0.6, 1.0), -0.75], [1.5, h, 1.5], "glow")], "decor",
               rot=[rnd.uniform(-0.4, 0.4), rnd.uniform(0, 1), rnd.uniform(-0.4, 0.4)])


def mushrooms(m, parent, n, y, spread_x, spread_z, seed, cap=3):
    rnd = random.Random(seed)
    for i in range(n):
        x = rnd.uniform(-spread_x, spread_x)
        z = rnd.uniform(-spread_z, spread_z)
        c = cap * rnd.uniform(0.7, 1.2)
        st = m.part(f"stem_{parent}{i}", parent, [x, y, z], [box([-0.5, -2, -0.5], [1, 2, 1], "belly")], "decor", rot=[rnd.uniform(-0.2, 0.2), 0, rnd.uniform(-0.2, 0.2)])
        m.part(f"cap_{parent}{i}", st, [0, -2, 0], [box([-c / 2, -1.5, -c / 2], [c, 1.5, c], "pattern")], "decor")


def mane(m, parent, y, z, w, h, l):
    m.part("mane", parent, [0, y, z], [box([-w / 2, -h, 0], [w, h, l], "accent")], "crest")


def lure(m, parent, y, z, length):
    st = m.part("lure", parent, [0, y, z], [box([-0.5, -length, -0.5], [1, length, 1], "accent")], "antenna", rot=[-0.8, 0, 0], index=3)
    m.part("lurebulb", st, [0, -length, 0], [box([-1, -2, -1], [2, 2, 2], "glow")], "decor")


# ============================================================================== texture painting

def pack(parts):
    """Assign UV origins to all boxes (shelf packing); returns texture (w, h)."""
    boxes = [b for p in parts for b in p["boxes"]]
    sizes = []
    for b in boxes:
        w, h, d = [max(0, math.ceil(v)) for v in b["size"]]
        sizes.append((2 * (d + w), d + h, b))
    sizes.sort(key=lambda s: -s[1])
    for tw in (64, 128, 256):
        x = y = rowh = 0
        ok = True
        for bw, bh, b in sizes:
            if bw > tw:
                ok = False
                break
            if x + bw > tw:
                x = 0
                y += rowh
                rowh = 0
            b["uv"] = [x, y]
            x += bw
            rowh = max(rowh, bh)
        th = y + rowh
        if ok and th <= tw:
            return tw, max(32, 1 << max(5, (th - 1).bit_length()))
    raise SystemExit("texture too large")


def faces(b):
    u, v = b["uv"]
    w, h, d = [max(0, math.ceil(x)) for x in b["size"]]
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "east": (u, v + d, d, h),
            "north": (u + d, v + d, w, h), "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h)}


def paint(img, glow, m, seed):
    pal = m.pal
    rnd = random.Random(seed)
    for p in m.parts:
        for b in p["boxes"]:
            mat = b["_mat"]
            base = {"base": pal["base"], "belly": pal["belly"], "head": pal.get("head", pal["base"]), "accent": pal["accent"],
                    "pattern": pal.get("pattern_c", pal["accent"]), "shell": pal.get("shell", pal["accent"]),
                    "wing": pal.get("wing", pal["base"]), "fin": pal.get("fin", pal["accent"]), "glow": pal.get("glow", pal["accent"]),
                    "eye": (20, 20, 20)}[mat]
            for fname, (fx, fy, fw, fh) in faces(b).items():
                for x in range(fw):
                    for y in range(fh):
                        c = base
                        t = y / max(1, fh - 1)
                        if fname == "top":
                            c = art.shade(c, 0.92)
                        elif fname == "bottom":
                            c = pal["belly"] if mat in ("base", "head") else art.shade(c, 0.8)
                        else:
                            c = art.shade(c, 1.06 - 0.16 * t)
                            if mat in ("base", "head") and t > 0.72:
                                c = art.mix(c, pal["belly"], 0.6)
                        c = art.shade(c, 1 + rnd.uniform(-0.05, 0.05))
                        pat = pal.get("pattern", "")
                        if mat in ("base", "pattern", "shell") and fname != "bottom":
                            if pat == "spots" and rnd.random() < 0.08:
                                c = pal.get("spot", art.shade(c, 1.35))
                            elif pat == "stripes" and (x + (fx * 0)) % 4 == 0:
                                c = art.shade(c, 0.72)
                            elif pat == "plates" and (x % 3 == 0 or y % 3 == 0):
                                c = art.shade(c, 0.8)
                            elif pat == "scales" and (x + y) % 2 == 0:
                                c = art.shade(c, 0.9)
                            elif pat == "fur" and rnd.random() < 0.25:
                                c = art.shade(c, rnd.choice([0.85, 1.12]))
                            elif pat == "dots_glow" and rnd.random() < 0.07:
                                c = pal.get("glow", c)
                                art.px(glow, fx + x, fy + y, c)
                        if mat == "shell" and (x % 4 == 0 or y % 4 == 0) and fname != "bottom":
                            c = art.shade(c, 0.78)
                        if mat == "wing" and pal.get("membrane"):
                            if x % 3 == 0:
                                c = art.shade(c, 0.7)
                        if mat == "glow":
                            c = pal.get("glow", c)
                            if rnd.random() < 0.85:
                                art.px(glow, fx + x, fy + y, art.shade(c, 1.1))
                        art.px(img, fx + x, fy + y, c)
            if p["role"] == "head" and b is p["boxes"][0]:
                fx, fy, fw, fh = faces(b)["north"]
                ey = fy + max(0, fh // 3)
                eye = pal.get("eye", (25, 25, 30))
                for ex in ([fx + 1, fx + fw - 2] if fw >= 4 else [fx + fw // 2]):
                    art.px(img, ex, ey, eye)
                    if fw >= 6:
                        art.px(img, ex, ey + 1, art.shade(eye, 0.6))
                    if pal.get("glow_eyes"):
                        art.px(glow, ex, ey, pal.get("glow_eye_c", (230, 250, 255)))
                # side eyes for wide-set eyes
                for fname in ("east", "west"):
                    sx, sy, sw, sh = faces(b)[fname]
                    if sw >= 3 and pal.get("side_eyes"):
                        art.px(img, sx + sw // 2, sy + max(0, sh // 3), eye)


def build_assets(sid, m, glows, egg):
    tw, th = pack(m.parts)
    img = art.new(tw, th)
    glow = art.new(tw, th)
    paint(img, glow, m, zlib.crc32(sid.encode()) & 0xFFFF)
    tex_dir = os.path.join(ASSETS, "textures", "entity", "creature")
    os.makedirs(tex_dir, exist_ok=True)
    img.save(os.path.join(tex_dir, sid + ".png"))
    has_glow = glows and glow.getbbox() is not None
    if has_glow:
        glow.save(os.path.join(tex_dir, sid + "_glow.png"))
    elif os.path.exists(os.path.join(tex_dir, sid + "_glow.png")):
        os.remove(os.path.join(tex_dir, sid + "_glow.png"))
    parts = []
    for p in m.parts:
        q = {k: v for k, v in p.items()}
        q["boxes"] = [{k: v for k, v in b.items() if not k.startswith("_")} for b in p["boxes"]]
        parts.append(q)
    return {"texture": [tw, th], "parts": parts, "glow": has_glow, "egg": egg}


def build_design(sid, egg):
    """Stage 11: hand-authored anatomy (creature_designs) -> creature JSON + painted texture (+ glow layer)."""
    d = creature_designs.DESIGNS[sid]
    r = creature_kit.Rig(sid, {"base": (128, 128, 128)}, zlib.crc32(sid.encode()) & 0xFFFF)
    d["build"](r)
    img, glow, (tw, th) = creature_kit.paint(r)
    tex_dir = os.path.join(ASSETS, "textures", "entity", "creature")
    os.makedirs(tex_dir, exist_ok=True)
    img.save(os.path.join(tex_dir, sid + ".png"))
    has_glow = d["glows"] and glow.getbbox() is not None
    if has_glow:
        glow.save(os.path.join(tex_dir, sid + "_glow.png"))
    elif os.path.exists(os.path.join(tex_dir, sid + "_glow.png")):
        os.remove(os.path.join(tex_dir, sid + "_glow.png"))
    egg = [rgb_int(r.pal["base"]), rgb_int(r.pal.get("mark", r.pal.get("belly", r.pal["base"])))]
    return {"texture": [tw, th], "parts": r.to_json(), "glow": has_glow, "egg": egg, "scale": d["scale"], "shadow": d["shadow"],
            "gait": d["gait"], "legLength": d["leg"], "rig": 2}


def rgb_int(c):
    return (c[0] << 16) | (c[1] << 8) | c[2]


# ============================================================================== species visuals

def S(**kw):
    return kw


# palettes: base, belly, accent (+ pattern, spot, glow, head, shell, wing, fin, eye)
SPECIES = {}


def define(sid, name, plan, pal, scale=1.0, shadow=0.5, gait="quad", leg=6, drops=(), glows=False, extra=None, rare=False):
    SPECIES[sid] = dict(name=name, plan=plan, pal=pal, scale=scale, shadow=shadow, gait=gait, leg=leg, drops=drops, glows=glows,
                        extra=extra, rare=rare)


def plans():
    # ---------------- Rootwild
    define("mossbuck", "Mossbuck", lambda m: (quadruped(m, 14, 7, 7, 11, 2, neck=6, head=(5, 4, 6), snout=2, tail=3, ears=3),
                                              antlers(m, "head", -2.5, -3, 6, "pattern"), mane(m, "neck", -1, -5, 3, 2, 5)),
           S(base=(120, 90, 62), belly=(196, 176, 140), accent=(70, 110, 50), pattern_c=(84, 132, 60), pattern="spots",
             spot=(214, 196, 150)), leg=11, drops=[("game_meat", 1, 2), ("hide", 0, 1)])
    define("thumperkit", "Thumperkit", lambda m: quadruped(m, 6, 5, 5, 3, 2, head=(4, 4, 4), ears=4, tail=1, tail_w=2),
           S(base=(150, 130, 105), belly=(220, 210, 190), accent=(120, 95, 75), pattern="fur"), leg=3, shadow=0.3,
           drops=[("game_meat", 0, 1), ("fur_tuft", 0, 1)])
    define("rootglider", "Rootglider", lambda m: (quadruped(m, 7, 4, 4, 3, 1, head=(3, 4, 3), ears=2, tail=7, tail_w=2, tail_up=-0.1),
                                                  m.part("membrane0", "body", [2, 0, 0], [box([0, 0, -3], [3, 0, 6], "wing")], "wing", side=0),
                                                  m.part("membrane1", "body", [-2, 0, 0], [box([-3, 0, -3], [3, 0, 6], "wing")], "wing", side=1)),
           S(base=(110, 96, 84), belly=(200, 190, 170), accent=(70, 60, 54), wing=(130, 110, 96), pattern="fur", eye=(10, 10, 10),
             glow_eyes=True, glow_eye_c=(180, 255, 200)), leg=3, shadow=0.3, glows=True, drops=[("fur_tuft", 0, 1)])
    define("lumenmoth", "Lumenmoth", lambda m: flyer(m, 4, 2, 2, 5, 4, head=(2, 2, 1), beak=0, tail=0, leg=0, membrane=True),
           S(base=(90, 100, 120), belly=(150, 160, 180), accent=(60, 60, 70), wing=(140, 210, 200), glow=(180, 255, 230),
             pattern="dots_glow", membrane=True), gait="none", leg=0, shadow=0.2, glows=True, drops=[("glow_gland", 0, 1)])
    define("tuskbriar", "Tuskbriar", lambda m: (quadruped(m, 12, 8, 7, 5, 3, head=(6, 6, 5), snout=3, tail=3, ears=2),
                                                horns(m, "head", 2, -6, 3, "belly", curl=-1.2), spikes(m, "body", 4, -3.5, -5, 5, 2)),
           S(base=(80, 60, 50), belly=(120, 95, 80), accent=(60, 40, 30), pattern="fur"), leg=5, drops=[("game_meat", 1, 3), ("hide", 1, 1), ("horn", 0, 1)])
    define("shadecat", "Shadecat", lambda m: quadruped(m, 13, 6, 6, 7, 2, head=(5, 5, 5), snout=1, tail=10, tail_w=2, tail_up=-0.2, ears=3, jaw=True),
           S(base=(52, 50, 62), belly=(90, 86, 100), accent=(30, 28, 36), pattern="stripes", eye=(10, 10, 10), glow_eyes=True,
             glow_eye_c=(200, 255, 120)), leg=7, glows=True, drops=[("hide", 1, 1), ("game_meat", 0, 1)])
    define("snufflet", "Snufflet", lambda m: quadruped(m, 8, 6, 5, 3, 2, head=(5, 5, 4), snout=2, tail=1, ears=2),
           S(base=(190, 140, 120), belly=(220, 180, 160), accent=(150, 100, 90), pattern="spots", spot=(140, 100, 80)), leg=3, shadow=0.35,
           drops=[("game_meat", 1, 2)])
    # ---------------- Fungal Expanse
    define("capback", "Capback", lambda m: (quadruped(m, 16, 11, 10, 8, 4, neck=3, head=(7, 7, 7), snout=2, tail=3, ears=2),
                                            mushrooms(m, "body", 5, -5, 4, 6, 11, cap=5)),
           S(base=(170, 150, 170), belly=(210, 200, 210), accent=(120, 100, 130), pattern_c=(70, 160, 190), pattern="spots",
             spot=(220, 250, 255)), scale=1.3, shadow=0.9, leg=8, drops=[("game_meat", 2, 3), ("hide", 1, 2)])
    define("sporemite", "Sporemite", lambda m: hexapod(m, 4, 3, 2, 3, abdomen=3, head=(2, 3, 2), antenna=2),
           S(base=(150, 120, 150), belly=(190, 170, 190), accent=(90, 70, 90), pattern_c=(160, 130, 170), pattern="spots",
             spot=(220, 240, 255)), gait="hexa", leg=3, shadow=0.2, drops=[("shell_fragment", 0, 1)])
    define("lanternbeetle", "Lanternbeetle", lambda m: (hexapod(m, 6, 5, 3, 3, abdomen=4, head=(3, 4, 2), antenna=3, glow_abdomen=True)),
           S(base=(40, 60, 90), belly=(70, 90, 120), accent=(30, 40, 60), glow=(120, 230, 255), pattern="plates"), gait="hexa", leg=3,
           shadow=0.3, glows=True, drops=[("glow_gland", 1, 1), ("shell_fragment", 0, 1)])
    define("capstalker", "Capstalker", lambda m: (quadruped(m, 12, 8, 7, 9, 3, head=(6, 7, 6), jaw=True, tail=4),
                                                  mushrooms(m, "body", 6, -4, 3, 5, 21, cap=6), mushrooms(m, "head", 1, -3, 0, 0, 22, cap=6)),
           S(base=(130, 110, 100), belly=(170, 150, 140), accent=(100, 80, 70), pattern_c=(170, 60, 60), pattern="spots",
             spot=(240, 230, 220)), leg=9, drops=[("hide", 1, 1), ("venom_gland", 0, 1)])
    define("puffling", "Puffling", lambda m: (blob(m, 4), mushrooms(m, "body", 1, -4, 0, 0, 31, cap=5)),
           S(base=(220, 200, 230), belly=(240, 230, 245), accent=(170, 140, 190), pattern_c=(140, 200, 230), eye=(40, 30, 60)),
           shadow=0.3, leg=1.5, drops=[("silk_strand", 0, 1)])
    # ---------------- Flooded Depths
    define("glimmerfin", "Glimmerfin", lambda m: fish(m, 7, 2, 3, tail=3, dorsal=1, pect=1, glow=False),
           S(base=(80, 140, 160), belly=(200, 230, 230), accent=(60, 100, 120), fin=(140, 220, 230), glow=(160, 255, 240),
             pattern="dots_glow"), gait="fish", leg=0, shadow=0.15, glows=True, drops=[("fish_fillet", 1, 1)])
    define("newtling", "Newtling", lambda m: quadruped(m, 6, 3, 2, 1.5, 1, head=(2, 3, 3), tail=5, tail_w=1.5, tail_up=0.1),
           S(base=(200, 110, 60), belly=(240, 190, 120), accent=(60, 40, 30), pattern="spots", spot=(40, 30, 30)), leg=1.5, shadow=0.2,
           drops=[("creature_oil", 0, 1)])
    define("lakehorn", "Lakehorn", lambda m: (fish(m, 22, 10, 9, tail=7, dorsal=0, pect=5, y=14),
                                              m.part("horn", "head", [0, -2, -2], [box([-1, -1, -5], [2, 2, 5], "accent")], "crest")),
           S(base=(100, 120, 130), belly=(180, 190, 190), accent=(220, 210, 180), fin=(90, 110, 120), pattern="scales"), gait="fish",
           scale=1.3, shadow=1.0, leg=0, drops=[("game_meat", 2, 4), ("creature_oil", 1, 2), ("horn", 1, 1)])
    define("ribbon_eel", "Ribbon Eel", lambda m: serpent(m, 6, 4, 3, 4, y_center=18, fins=True),
           S(base=(60, 90, 70), belly=(160, 180, 140), accent=(200, 220, 120), fin=(120, 160, 90), pattern="stripes"), gait="serpent",
           leg=0, shadow=0.3, drops=[("fish_fillet", 1, 2), ("scale", 0, 1)])
    define("grand_drifter", "Grand Drifter", lambda m: (fish(m, 30, 14, 12, tail=10, dorsal=4, pect=8, y=10),
                                                        crystals(m, "body", 4, -6, 5, 10, 4, 41)),
           S(base=(50, 70, 100), belly=(160, 180, 200), accent=(120, 150, 180), fin=(80, 110, 150), glow=(150, 220, 255),
             pattern="dots_glow"), gait="fish", scale=2.2, shadow=2.0, leg=0, glows=True, rare=True,
           drops=[("scale", 3, 5), ("creature_oil", 3, 5)])
    # ---------------- Ashen Hollows
    define("emberskink", "Emberskink", lambda m: (quadruped(m, 9, 4, 3, 2, 1.5, head=(3, 4, 4), tail=7, tail_w=2, tail_up=0.1),
                                                  spikes(m, "body", 3, -1.5, -3, 3, 1, glow=True)),
           S(base=(60, 40, 40), belly=(120, 60, 40), accent=(40, 30, 30), glow=(255, 140, 50), pattern="dots_glow"), leg=2, shadow=0.3,
           glows=True, drops=[("scale", 0, 1), ("creature_oil", 0, 1)])
    define("ashshell", "Ashshell", lambda m: shelled(m, 14, 10, 6, 4, 6, head=(4, 4, 4)),
           S(base=(80, 75, 70), belly=(110, 100, 90), accent=(60, 55, 50), shell=(60, 58, 60), pattern="plates"), leg=4, shadow=0.7,
           drops=[("shell_fragment", 1, 3), ("shellmeat", 1, 1)])
    define("scorchjaw", "Scorchjaw", lambda m: (biped(m, 10, 6, 7, 12, 3, neck=5, head=(6, 5, 8), tail=12, arms=3),
                                                spikes(m, "body", 4, -3.5, -4, 4, 2, glow=True)),
           S(base=(70, 40, 35), belly=(140, 90, 60), accent=(40, 25, 25), glow=(255, 120, 40), pattern="scales", eye=(255, 200, 60),
             glow_eyes=True, glow_eye_c=(255, 190, 60)), gait="biped", leg=12, glows=True,
           drops=[("hide", 1, 1), ("scale", 1, 2), ("game_meat", 1, 2)])
    define("slag_titan", "Slag Titan", lambda m: (shelled(m, 20, 16, 10, 8, 8, head=(8, 8, 6)), crystals(m, "body", 6, -12, 8, 9, 5, 51)),
           S(base=(50, 45, 45), belly=(90, 70, 60), accent=(40, 35, 35), shell=(45, 40, 42), glow=(255, 130, 40), pattern="plates"),
           scale=2.0, shadow=2.0, leg=8, glows=True, rare=True, drops=[("shell_fragment", 4, 8), ("horn", 1, 2)])
    # ---------------- Crystal Deep
    define("prismhorn", "Prismhorn", lambda m: (quadruped(m, 12, 6, 7, 9, 2, neck=4, head=(5, 4, 6), snout=1, tail=2, ears=2),
                                                horns(m, "head", -2.5, -3, 4, "glow"), crystals(m, "body", 4, -3.5, 2, 5, 3, 61)),
           S(base=(170, 180, 200), belly=(220, 225, 235), accent=(120, 130, 150), glow=(170, 240, 255), pattern="plates"), leg=9,
           glows=True, drops=[("crystal_husk", 1, 2), ("horn", 0, 1), ("game_meat", 1, 1)])
    define("shardfly", "Shardfly", lambda m: flyer(m, 3, 2, 2, 4, 3, head=(2, 2, 1), beak=0, tail=0, leg=0, membrane=True),
           S(base=(150, 220, 240), belly=(200, 240, 250), accent=(100, 160, 190), wing=(210, 250, 255), glow=(200, 255, 255),
             pattern="dots_glow", membrane=True), gait="none", leg=0, shadow=0.15, glows=True, drops=[("crystal_husk", 0, 1)])
    define("geode_crab", "Geode Crab", lambda m: (crab(m, 9, 7, 4, 4, claw=3), crystals(m, "body", 5, -2, 3, 2, 3, 71)),
           S(base=(110, 100, 120), belly=(170, 160, 170), accent=(90, 80, 100), glow=(220, 170, 255), pattern="plates"), gait="crab",
           leg=4, shadow=0.5, glows=True, drops=[("crystal_husk", 1, 1), ("shellmeat", 1, 1)])
    define("facet_mantis", "Facet Mantis", lambda m: (hexapod(m, 7, 4, 4, 9, abdomen=7, head=(4, 4, 3), antenna=4, mandible=True),
                                                      m.part("rapt0", "body", [2, 0, -3], [box([-0.5, 0, -6], [1, 1, 6], "glow")], "arm", rot=[0.7, 0, 0], phase=0),
                                                      m.part("rapt1", "body", [-2, 0, -3], [box([-0.5, 0, -6], [1, 1, 6], "glow")], "arm", rot=[0.7, 0, 0], phase=1)),
           S(base=(150, 200, 190), belly=(200, 230, 220), accent=(90, 140, 130), glow=(180, 255, 240), pattern="plates", eye=(30, 60, 60)),
           gait="hexa", leg=9, glows=True, drops=[("crystal_husk", 1, 2), ("venom_gland", 0, 1)])
    # ---------------- The Abyss
    define("echo_crawler", "Echo Crawler", lambda m: (hexapod(m, 10, 6, 3, 5, abdomen=4, head=(3, 6, 3), antenna=6),
                                                      spikes(m, "body", 4, -1.5, -4, 4, 1)),
           S(base=(60, 55, 70), belly=(90, 85, 100), accent=(40, 38, 50), pattern="plates", eye=(60, 60, 70)), gait="hexa", leg=5,
           drops=[("shell_fragment", 1, 1), ("silk_strand", 0, 1)])
    define("lure_angler", "Lure Angler", lambda m: (quadruped(m, 12, 10, 8, 3, 3, head=(8, 10, 7), jaw=True, tail=6, tail_w=3),
                                                    lure(m, "head", -4, -3, 7)),
           S(base=(30, 32, 40), belly=(50, 50, 60), accent=(20, 22, 28), glow=(140, 255, 220), pattern="dots_glow", side_eyes=True,
             eye=(10, 10, 12)), leg=3, glows=True, drops=[("glow_gland", 1, 2), ("game_meat", 1, 1)])
    define("gloomwing", "Gloomwing", lambda m: (flyer(m, 4, 3, 3, 7, 4, head=(3, 3, 2), beak=0, tail=0, leg=1, membrane=True),
                                                m.part("ear0", "head", [1, -3, -1], [box([-0.5, -2, -0.5], [1, 2, 1], "accent")], "crest"),
                                                m.part("ear1", "head", [-1, -3, -1], [box([-0.5, -2, -0.5], [1, 2, 1], "accent")], "crest")),
           S(base=(50, 45, 60), belly=(80, 70, 90), accent=(35, 30, 40), wing=(70, 60, 80), membrane=True, eye=(10, 10, 10),
             glow_eyes=True, glow_eye_c=(200, 120, 255)), gait="none", leg=1, shadow=0.2, glows=True, drops=[("hide", 0, 1)])
    define("abyss_strider", "Abyss Strider", lambda m: (quadruped(m, 10, 8, 6, 30, 1.5, neck=0, head=(5, 6, 5), jaw=True, tail=0),
                                                        lure(m, "head", -3, -2, 4)),
           S(base=(40, 42, 50), belly=(70, 72, 80), accent=(30, 30, 36), glow=(160, 200, 255), pattern="plates", side_eyes=True),
           scale=2.6, shadow=1.0, leg=30, glows=True, rare=True, drops=[("silk_strand", 3, 5), ("hide", 2, 2)])
    # ---------------- Everfrost
    define("frostmane", "Frostmane", lambda m: (quadruped(m, 16, 11, 10, 7, 4, neck=2, head=(7, 7, 6), snout=1, tail=2),
                                                mane(m, "body", -5, -8, 12, 3, 14), horns(m, "head", -3, -3, 3, "belly", curl=-1.4)),
           S(base=(170, 175, 185), belly=(220, 225, 230), accent=(240, 245, 250), pattern="fur"), scale=1.15, shadow=0.9, leg=7,
           drops=[("game_meat", 2, 3), ("hide", 1, 1), ("fur_tuft", 1, 3)])
    define("rimefox", "Rimefox", lambda m: quadruped(m, 10, 5, 5, 6, 2, head=(5, 5, 4), snout=3, tail=8, tail_w=3, tail_up=-0.3, ears=3),
           S(base=(225, 232, 240), belly=(245, 248, 252), accent=(150, 180, 210), pattern="fur", eye=(40, 70, 110)), leg=6,
           drops=[("fur_tuft", 1, 2), ("hide", 0, 1)])
    define("glacier_skimmer", "Glacier Skimmer", lambda m: hexapod(m, 4, 3, 2, 4, abdomen=2, head=(2, 3, 2), antenna=3),
           S(base=(150, 200, 230), belly=(220, 240, 250), accent=(90, 130, 170), pattern="plates"), gait="hexa", leg=4, shadow=0.2,
           drops=[("shell_fragment", 0, 1)])
    # ---------------- Golden Grove
    define("goldfleece", "Goldfleece", lambda m: (quadruped(m, 12, 8, 8, 6, 2, head=(5, 4, 5), snout=1, tail=1, ears=2),
                                                  m.part("fleece", "body", [0, 0, 0], [box([-5, -5, -7], [10, 10, 14], "pattern")], "decor")),
           S(base=(150, 120, 80), belly=(220, 200, 150), accent=(110, 90, 60), pattern_c=(235, 195, 90), pattern="fur"), leg=6,
           drops=[("game_meat", 1, 2), ("fur_tuft", 1, 2)])
    define("amberwing", "Amberwing", lambda m: flyer(m, 5, 3, 3, 6, 4, head=(3, 3, 3), beak=2, tail=5, leg=2),
           S(base=(240, 150, 50), belly=(255, 210, 120), accent=(80, 60, 40), wing=(220, 90, 60), pattern="spots",
             spot=(255, 230, 150)), gait="biped", leg=2, shadow=0.25, drops=[("plume", 1, 2), ("game_meat", 0, 1)])
    define("bumblewisp", "Bumblewisp", lambda m: (blob(m, 2.5, feet=False),
                                                  m.part("wing0", "body", [2, -2, 0], [box([0, 0, -1], [3, 0, 3], "wing")], "wing", side=0),
                                                  m.part("wing1", "body", [-2, -2, 0], [box([-3, 0, -1], [3, 0, 3], "wing")], "wing", side=1)),
           S(base=(250, 200, 60), belly=(255, 230, 120), accent=(60, 40, 20), wing=(230, 240, 250), pattern="stripes", glow=(255, 240, 150),
             membrane=True), gait="none", leg=0, shadow=0.2, glows=False, drops=[("glow_gland", 0, 1)])
    define("glade_runner", "Glade Runner", lambda m: (biped(m, 10, 7, 7, 14, 2, neck=9, head=(3, 3, 5), tail=4, arms=0, jaw=False),
                                                      m.part("crest", "head", [0, -1.5, -1], [box([-0.5, -3, 0], [1, 3, 4], "accent")], "crest")),
           S(base=(200, 160, 90), belly=(240, 220, 170), accent=(220, 90, 60), pattern="fur"), gait="biped", leg=14, shadow=0.6,
           drops=[("game_meat", 2, 2), ("plume", 1, 2)])
    # ---------------- Hollow Peaks
    define("cliffstrider", "Cliffstrider", lambda m: (quadruped(m, 13, 7, 7, 10, 2, neck=4, head=(5, 4, 6), snout=1, tail=2, ears=2),
                                                      horns(m, "head", -2.5, -3, 4, "belly", curl=-1.6)),
           S(base=(130, 115, 100), belly=(200, 190, 175), accent=(80, 70, 60), pattern="fur"), leg=10,
           drops=[("game_meat", 1, 2), ("hide", 1, 1), ("horn", 0, 1)])
    define("ledgewing", "Ledgewing", lambda m: (flyer(m, 10, 4, 4, 12, 7, head=(3, 4, 6), beak=0, tail=10, leg=3, membrane=True),
                                                m.part("crest", "head", [0, -3, -2], [box([-0.5, -2, 0], [1, 2, 5], "accent")], "crest")),
           S(base=(110, 90, 80), belly=(170, 150, 130), accent=(180, 70, 50), wing=(150, 100, 80), membrane=True, pattern="scales"),
           gait="biped", leg=3, shadow=0.5, drops=[("hide", 1, 1), ("scale", 1, 1)])
    define("rockpip", "Rockpip", lambda m: quadruped(m, 7, 5, 5, 2, 2, head=(4, 4, 3), ears=1, tail=2, tail_w=2),
           S(base=(140, 120, 100), belly=(200, 185, 160), accent=(100, 85, 70), pattern="fur"), leg=2, shadow=0.3,
           drops=[("fur_tuft", 0, 1), ("game_meat", 0, 1)])
    # ---------------- Emerald Wetlands
    define("bogcroaker", "Bogcroaker", lambda m: quadruped(m, 7, 7, 4, 2, 2, head=(4, 7, 4), jaw=True, tail=0),
           S(base=(80, 120, 60), belly=(200, 210, 140), accent=(50, 80, 40), pattern="spots", spot=(40, 60, 30), side_eyes=True), leg=2,
           shadow=0.35, drops=[("game_meat", 0, 1), ("creature_oil", 0, 1)])
    define("stiltwader", "Stiltwader", lambda m: biped(m, 7, 5, 5, 14, 1, neck=8, head=(3, 3, 7), tail=3, arms=0, jaw=False, neck_up=1.2),
           S(base=(200, 210, 220), belly=(240, 245, 250), accent=(60, 70, 90), pattern=""), gait="biped", leg=14, shadow=0.4,
           drops=[("plume", 1, 2), ("game_meat", 0, 1)])
    define("rillpup", "Rillpup", lambda m: quadruped(m, 10, 5, 4, 2, 2, head=(4, 4, 4), snout=1, tail=6, tail_w=2, tail_up=0.1, ears=1),
           S(base=(100, 75, 55), belly=(170, 145, 120), accent=(70, 50, 40), pattern="fur"), leg=2, shadow=0.3,
           drops=[("fur_tuft", 1, 1), ("hide", 0, 1)])
    define("glassdart", "Glassdart", lambda m: (flyer(m, 7, 1, 1, 5, 2, head=(2, 2, 1), beak=0, tail=0, leg=0, membrane=True)),
           S(base=(60, 160, 150), belly=(120, 200, 190), accent=(40, 100, 90), wing=(210, 240, 250), membrane=True), gait="none", leg=0,
           shadow=0.15, drops=[("silk_strand", 0, 1)])
    # ---------------- Petrified Forest
    define("stonehide", "Stonehide", lambda m: shelled(m, 9, 6, 4, 2, 4, head=(3, 3, 3)),
           S(base=(150, 130, 110), belly=(190, 170, 150), accent=(120, 100, 85), shell=(140, 125, 105), pattern="plates"), leg=2,
           shadow=0.4, drops=[("shell_fragment", 1, 2), ("shellmeat", 1, 1)])
    define("dustbeak", "Dustbeak", lambda m: flyer(m, 7, 4, 5, 9, 5, head=(3, 3, 3), beak=3, tail=4, leg=3),
           S(base=(90, 75, 65), belly=(150, 130, 110), accent=(200, 170, 120), wing=(70, 60, 55)), gait="biped", leg=3, shadow=0.4,
           drops=[("plume", 1, 2)])
    define("petrisloth", "Petrisloth", lambda m: (quadruped(m, 12, 8, 8, 7, 3, head=(5, 5, 4), tail=0),
                                                  crystals(m, "body", 3, -4, 3, 4, 2, 81)),
           S(base=(130, 120, 105), belly=(170, 160, 145), accent=(100, 90, 80), glow=(230, 180, 90), pattern="plates"), leg=7, glows=True,
           drops=[("shell_fragment", 1, 2), ("hide", 1, 1)])
    # ---------------- Crimson Basin
    define("bloodhorn", "Bloodhorn", lambda m: (quadruped(m, 13, 8, 8, 8, 3, neck=2, head=(6, 5, 6), snout=1, tail=2),
                                                horns(m, "head", -2.5, -3, 4, "belly", curl=-1.8)),
           S(base=(120, 30, 35), belly=(170, 70, 60), accent=(60, 20, 20), pattern="fur"), leg=8,
           drops=[("horn", 1, 1), ("game_meat", 1, 2), ("hide", 1, 1)])
    define("thornstalker", "Thornstalker", lambda m: (quadruped(m, 14, 7, 5, 5, 2, head=(4, 6, 5), jaw=True, tail=9, tail_w=2),
                                                      spikes(m, "body", 5, -2.5, -6, 6, 2), spikes_tail(m)),
           S(base=(110, 25, 30), belly=(150, 50, 50), accent=(220, 200, 180), pattern="stripes", eye=(240, 220, 80)), leg=5,
           drops=[("hide", 1, 1), ("venom_gland", 0, 1)])
    define("toxitoad", "Toxitoad", lambda m: quadruped(m, 7, 7, 5, 2, 2, head=(4, 6, 4), jaw=True, tail=0),
           S(base=(150, 200, 60), belly=(220, 230, 140), accent=(120, 40, 140), pattern="spots", spot=(130, 40, 150), side_eyes=True),
           leg=2, shadow=0.35, drops=[("venom_gland", 1, 1), ("creature_oil", 0, 1)])
    # ---------------- Sunken Gardens
    define("reef_darter", "Reef Darter", lambda m: fish(m, 5, 2, 4, tail=3, dorsal=2, pect=1),
           S(base=(250, 120, 60), belly=(255, 220, 160), accent=(60, 60, 200), fin=(80, 120, 255), pattern="stripes"), gait="fish",
           leg=0, shadow=0.15, drops=[("fish_fillet", 1, 1)])
    define("coral_crab", "Coral Crab", lambda m: crab(m, 8, 6, 3, 3, claw=3),
           S(base=(230, 100, 90), belly=(250, 180, 160), accent=(200, 70, 60), pattern="spots", spot=(255, 220, 200)), gait="crab", leg=3,
           shadow=0.4, drops=[("shellmeat", 1, 1), ("shell_fragment", 0, 1)])
    define("veilray", "Veilray", lambda m: ray(m, 14, 36, 2, tail=14),
           S(base=(50, 60, 110), belly=(200, 210, 230), accent=(30, 40, 80), wing=(70, 80, 140), glow=(150, 200, 255),
             pattern="dots_glow"), gait="fish", leg=0, shadow=1.0, glows=True, drops=[("fish_fillet", 2, 2), ("creature_oil", 1, 2)])
    define("stoneface", "Stoneface", lambda m: (fish(m, 8, 5, 5, tail=3, dorsal=3, pect=3), spikes(m, "body", 4, -2.5, -3, 3, 2)),
           S(base=(120, 110, 100), belly=(160, 150, 140), accent=(170, 60, 50), fin=(140, 90, 80), pattern="spots", spot=(90, 80, 70)),
           gait="fish", leg=0, shadow=0.3, drops=[("fish_fillet", 1, 1), ("venom_gland", 0, 1)])
    # ---------------- Stormstone Caverns
    define("static_beetle", "Static Beetle", lambda m: hexapod(m, 7, 6, 4, 3, abdomen=3, head=(3, 4, 2), antenna=4, glow_abdomen=True),
           S(base=(40, 40, 90), belly=(70, 70, 120), accent=(30, 30, 60), glow=(170, 140, 255), pattern="plates"), gait="hexa", leg=3,
           shadow=0.4, glows=True, drops=[("shell_fragment", 1, 1), ("glow_gland", 0, 1)])
    define("arcback", "Arcback", lambda m: (quadruped(m, 14, 7, 5, 4, 2, head=(4, 5, 5), jaw=True, tail=10, tail_w=2, tail_up=0.1),
                                            spikes(m, "body", 5, -2.5, -6, 6, 3, glow=True)),
           S(base=(60, 60, 100), belly=(100, 100, 140), accent=(40, 40, 70), glow=(160, 200, 255), pattern="scales"), leg=4, glows=True,
           drops=[("scale", 1, 2), ("crystal_husk", 0, 1)])
    define("conduit_serpent", "Conduit Serpent", lambda m: (serpent(m, 8, 5, 4, 5, frill=True, glow_spots=True)),
           S(base=(50, 50, 110), belly=(120, 120, 180), accent=(170, 140, 255), glow=(180, 220, 255)), gait="serpent", scale=1.3,
           leg=0, shadow=0.5, glows=True, rare=True, drops=[("scale", 2, 4), ("glow_gland", 1, 2)])
    # ---------------- Starlight Caverns
    define("starwisp", "Starwisp", lambda m: floater(m, 2, 3, tentacles=4, tlen=3, y=14),
           S(base=(200, 220, 255), belly=(230, 240, 255), accent=(150, 180, 255), glow=(220, 235, 255)), gait="none", leg=0,
           shadow=0.1, glows=True, drops=[("glow_gland", 0, 1)])
    define("moonhorn", "Moonhorn", lambda m: (quadruped(m, 13, 6, 7, 12, 2, neck=7, head=(5, 4, 6), snout=2, tail=4, ears=3),
                                              m.part("unihorn", "head", [0, -2, -4], [box([-0.5, -7, -0.5], [1, 7, 1], "glow")], "crest", rot=[-0.5, 0, 0]),
                                              mane(m, "neck", -1, -6, 2, 2, 6)),
           S(base=(200, 210, 235), belly=(235, 240, 250), accent=(150, 170, 220), glow=(200, 230, 255), pattern="dots_glow"), leg=12,
           glows=True, drops=[("horn", 1, 1), ("hide", 1, 1), ("game_meat", 1, 1)])
    define("lumen_kit", "Lumen Kit", lambda m: quadruped(m, 8, 5, 5, 5, 2, head=(5, 5, 4), snout=2, tail=8, tail_w=3, tail_up=-0.4, ears=4),
           S(base=(110, 150, 210), belly=(200, 220, 245), accent=(170, 230, 255), glow=(180, 240, 255), pattern="dots_glow",
             glow_eyes=True, glow_eye_c=(180, 255, 255)), leg=5, glows=True, drops=[("fur_tuft", 1, 1)])
    define("sky_drifter", "Sky Drifter", lambda m: floater(m, 10, 8, tentacles=10, tlen=14, y=-4),
           S(base=(120, 140, 220), belly=(200, 210, 250), accent=(160, 190, 255), glow=(200, 230, 255)), gait="none", scale=2.0,
           leg=0, shadow=1.5, glows=True, rare=True, drops=[("glow_gland", 4, 6), ("silk_strand", 2, 4)])


def spikes_tail(m):
    m.part("tailspike", "tail0", [0, -1, 8], [box([-0.5, -2, -0.5], [1, 2, 1], "accent")], "decor")


RESOURCES = {
    # id: (name, image factory, tooltip)
    "game_meat": ("Game Meat", lambda: art.raw_chunk((190, 70, 70), 900), "Cook it."),
    "cooked_game_meat": ("Cooked Game Meat", lambda: art.raw_chunk((140, 80, 50), 901), None),
    "fish_fillet": ("Fish Fillet", lambda: art.petal((240, 170, 150), 902), "Cook it."),
    "cooked_fish_fillet": ("Cooked Fish Fillet", lambda: art.petal((200, 140, 90), 903), None),
    "shellmeat": ("Shellmeat", lambda: art.truffle((230, 160, 150), 904), "Unwise to eat raw."),
    "cooked_shellmeat": ("Cooked Shellmeat", lambda: art.truffle((210, 130, 90), 905), None),
    "creature_egg": ("Creature Egg", lambda: art.nut((235, 225, 200), 906), "Can stand in for a bird's egg."),
    "hide": ("Hide", lambda: art.poultice((150, 110, 80), 907), "Tans into leather in a furnace."),
    "fur_tuft": ("Fur Tuft", lambda: art.fiber((230, 225, 215), 908), "Four make a block of wool."),
    "plume": ("Plume", lambda: art.leaf_item((240, 180, 80), 909, n=1), "Works as a feather."),
    "shell_fragment": ("Shell Fragment", lambda: art.gem((150, 140, 120), 910, "shard"), "Grinds into bone meal."),
    "horn": ("Horn", lambda: art.fang((230, 220, 190), 911), "Shed naturally by some grazers."),
    "scale": ("Scale", lambda: art.gem((90, 140, 150), 912, "round"), "Tough; saved for future gear."),
    "silk_strand": ("Silk Strand", lambda: art.fiber((240, 240, 250), 913), "Spins into string."),
    "venom_gland": ("Venom Gland", lambda: art.glob((140, 200, 60), 914), "Tips arrows with poison."),
    "creature_oil": ("Creature Oil", lambda: art.bottle((220, 190, 90), 915), "Burns brightly. Makes torches."),
    "glow_gland": ("Glow Gland", lambda: art.glob((170, 255, 230), 916), "Still glows faintly."),
    "crystal_husk": ("Crystal Husk", lambda: art.gem((200, 230, 255), 917, "crystal"), "Shed by crystal creatures."),
    "harness": ("Riding Harness", lambda: art.poultice((110, 80, 60), 918), "Lets you ride a trusting mount."),
    "field_journal": ("Field Journal", lambda: art.poultice((90, 110, 70), 919), "Records the wildlife you discover."),
}

TAMING_TEXT = {
    "rootglider": " Accepts duskberries from patient hands.",
    "shadecat": " Only trusts someone who feeds it while it is hurt.",
    "snufflet": " Loves cave truffles.",
    "capback": " Can be won over with puffcaps.",
    "puffling": " Follows anyone who shares lanterncaps.",
    "lakehorn": " Accepts lakeblooms.",
    "amberwing": " Comes to those who offer honeyfruit.",
    "glade_runner": " Fond of sunbeans.",
    "cliffstrider": " Wait quietly, crouched, with cliffberries, before feeding it.",
    "ledgewing": " Only trusts someone who feeds it while it is hurt.",
    "rillpup": " Bribed with fish fillets.",
    "lumen_kit": " Accepts gifts of glimmer shards, now and then.",
}


def tag(path, *vals):
    TAGS.setdefault(path, [])
    for v in vals:
        if v not in TAGS[path]:
            TAGS[path].append(v)


def loot_entity(sid, drops):
    pools = []
    for item, lo, hi in drops:
        iid = item if ":" in item else f"{NS}:{item}"
        fns = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}},
               {"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                "count": {"type": "minecraft:uniform", "min": 0, "max": 1}}]
        if item in ("game_meat", "fish_fillet", "shellmeat"):
            fns.append({"function": "minecraft:furnace_smelt", "conditions": [
                {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"flags": {"is_on_fire": True}}}]})
        pools.append({"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": iid, "functions": fns}]})
    wjson(os.path.join(DATA, NS, "loot_table", "entities", sid + ".json"),
          {"type": "minecraft:entity", "pools": pools, "random_sequence": f"{NS}:entities/{sid}"})


def recipe(name, obj):
    wjson(os.path.join(DATA, NS, "recipe", name + ".json"), obj)


def u(i):
    return i if ":" in i else f"{NS}:{i}"


def resources():
    tex = os.path.join(ASSETS, "textures", "item")
    for iid, (name, fac, tipt) in RESOURCES.items():
        fac().save(os.path.join(tex, iid + ".png"))
        wjson(os.path.join(ASSETS, "models", "item", iid + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{iid}"}})
        LANG[f"item.{NS}.{iid}"] = name
        if tipt:
            LANG[f"tooltip.{NS}.{iid}"] = tipt
    for raw, cooked in (("game_meat", "cooked_game_meat"), ("fish_fillet", "cooked_fish_fillet"), ("shellmeat", "cooked_shellmeat")):
        for typ, t in (("smelting", 200), ("smoking", 100), ("campfire_cooking", 600)):
            recipe(f"{cooked}_{typ}", {"type": f"minecraft:{typ}", "category": "food", "ingredient": {"item": u(raw)},
                                       "result": {"id": u(cooked)}, "experience": 0.35, "cookingtime": t})
    recipe("leather_from_hide", {"type": "minecraft:smelting", "category": "misc", "ingredient": {"item": u("hide")},
                                 "result": {"id": "minecraft:leather"}, "experience": 0.2, "cookingtime": 200})

    def shapeless(name, ings, res, n=1, comps=None):
        r = {"type": "minecraft:crafting_shapeless", "category": "misc",
             "ingredients": [{"tag": i[1:]} if i.startswith("#") else {"item": u(i)} for i in ings], "result": {"id": u(res), "count": n}}
        if comps:
            r["result"]["components"] = comps
        recipe(name, r)

    shapeless("white_wool_from_fur", ["fur_tuft"] * 4, "minecraft:white_wool")
    shapeless("feather_from_plume", ["plume"], "minecraft:feather")
    shapeless("string_from_silk", ["silk_strand"], "minecraft:string")
    shapeless("bone_meal_from_shell", ["shell_fragment"], "minecraft:bone_meal", 2)
    shapeless("bone_meal_from_horn", ["horn"], "minecraft:bone_meal", 3)
    shapeless("glowstone_dust_from_gland", ["glow_gland"], "minecraft:glowstone_dust", 2)
    shapeless("egg_from_creature_egg", ["creature_egg"], "minecraft:egg")
    shapeless("glimmer_from_husk", ["crystal_husk"], "glimmer_shard", 2)
    shapeless("torches_from_oil", ["creature_oil", "minecraft:stick", "minecraft:stick"], "minecraft:torch", 6)
    shapeless("field_journal", ["minecraft:book", "plume"], "field_journal")
    shapeless("poison_arrows_from_venom", ["venom_gland"] + ["minecraft:arrow"] * 8, "minecraft:tipped_arrow", 8,
              {"minecraft:potion_contents": {"potion": "minecraft:poison"}})
    recipe("harness", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["HHH", "S S"],
                       "key": {"H": {"item": u("hide")}, "S": {"item": "minecraft:string"}}, "result": {"id": u("harness"), "count": 1}})
    tag("minecraft/tags/item/meat.json", u("game_meat"), u("cooked_game_meat"))
    tag("minecraft/tags/item/fishes.json", u("fish_fillet"), u("cooked_fish_fillet"))


def wildlife_blocks():
    tb = os.path.join(ASSETS, "textures", "block")
    art.leaves((120, 90, 50), 950, "drooping", hole=0.2, accent=(90, 70, 40)).save(os.path.join(tb, "twig_nest.png"))
    art.stone((110, 85, 60), 951, specks=[(80, 60, 40), (140, 110, 80)], n=20).save(os.path.join(tb, "burrow_mound.png"))
    art.stone((230, 220, 195), 952, specks=[(180, 160, 120), (140, 120, 90)], n=14).save(os.path.join(tb, "creature_egg_block.png"))
    for bid, h in (("twig_nest", 3), ("burrow_mound", 3)):
        t = f"{NS}:block/{bid}"
        wjson(os.path.join(ASSETS, "models", "block", bid + ".json"), {"parent": "minecraft:block/block", "textures": {"particle": t, "all": t},
              "elements": [{"from": [1, 0, 1], "to": [15, h, 15], "faces": {f: {"texture": "#all"} for f in
                            ("north", "south", "east", "west", "up", "down")}}]})
        wjson(os.path.join(ASSETS, "blockstates", bid + ".json"), {"variants": {"": {"model": t}}})
        wjson(os.path.join(ASSETS, "models", "item", bid + ".json"), {"parent": t})
        wjson(os.path.join(DATA, NS, "loot_table", "blocks", bid + ".json"), {"type": "minecraft:block", "pools": [
            {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": u(bid)}],
             "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    t = f"{NS}:block/creature_egg_block"
    wjson(os.path.join(ASSETS, "models", "block", "creature_egg_block.json"), {"parent": "minecraft:block/block",
          "textures": {"particle": t, "all": t}, "elements": [{"from": [5, 0, 5], "to": [11, 7, 11], "faces": {f: {"texture": "#all"} for f in
                                                                                                             ("north", "south", "east", "west", "up", "down")}}]})
    wjson(os.path.join(ASSETS, "blockstates", "creature_egg_block.json"), {"variants": {"": {"model": t}}})
    wjson(os.path.join(DATA, NS, "loot_table", "blocks", "creature_egg_block.json"), {"type": "minecraft:block", "pools": []})
    tag("minecraft/tags/block/mineable/axe.json", u("twig_nest"))
    tag("minecraft/tags/block/mineable/shovel.json", u("burrow_mound"))


def main():
    plans()
    wildlife_blocks()
    cdir = os.path.join(ASSETS, "creatures")
    os.makedirs(cdir, exist_ok=True)
    for sid, d in SPECIES.items():
        egg = [rgb_int(d["pal"]["base"]), rgb_int(d["pal"].get("glow", d["pal"]["accent"]))]
        if sid in creature_designs.DESIGNS:
            spec = build_design(sid, egg)
        else:
            m = Model(sid, d["pal"])
            d["plan"](m)
            spec = build_assets(sid, m, d["glows"], egg)
            spec.update({"scale": d["scale"], "shadow": d["shadow"], "gait": d["gait"], "legLength": d["leg"]})
        wjson(os.path.join(cdir, sid + ".json"), spec)
        LANG[f"entity.{NS}.{sid}"] = d["name"]
        LANG[f"item.{NS}.{sid}_spawn_egg"] = d["name"] + " Spawn Egg"
        wjson(os.path.join(ASSETS, "models", "item", sid + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
        loot_entity(sid, d["drops"])
        res = ", ".join(sorted({i.replace("_", " ") for i, _, _ in d["drops"]}))
        LANG[f"journal.{NS}.resources.{sid}"] = f"; yields {res}" if res else ""
        LANG[f"journal.{NS}.taming.{sid}"] = TAMING_TEXT.get(sid, "")
    resources()
    aquatic = ["glimmerfin", "newtling", "lakehorn", "ribbon_eel", "grand_drifter", "bogcroaker", "rillpup", "reef_darter", "coral_crab",
               "veilray", "stoneface"]
    tag("minecraft/tags/entity_type/can_breathe_under_water.json", *[u(x) for x in aquatic])
    tag("minecraft/tags/entity_type/aquatic.json", *[u(x) for x in aquatic if x not in ("newtling", "bogcroaker", "rillpup")])
    tag("minecraft/tags/entity_type/fall_damage_immune.json", *[u(x) for x in (
        "rootglider", "lumenmoth", "prismhorn", "shardfly", "facet_mantis", "echo_crawler", "gloomwing", "amberwing", "bumblewisp",
        "cliffstrider", "ledgewing", "rockpip", "glassdart", "dustbeak", "petrisloth", "thornstalker", "arcback", "starwisp", "sky_drifter")])
    tag("minecraft/tags/entity_type/arthropod.json", *[u(x) for x in (
        "sporemite", "lanternbeetle", "geode_crab", "facet_mantis", "echo_crawler", "glacier_skimmer", "static_beetle", "coral_crab",
        "lumenmoth", "shardfly", "glassdart")])
    tag("minecraft/tags/entity_type/freeze_immune_entity_types.json", *[u(x) for x in ("frostmane", "rimefox", "glacier_skimmer")])
    LANG.update({
        "message.underdeep.creature.stay": "%s will stay here",
        "message.underdeep.creature.follow": "%s will follow you",
        "message.underdeep.creature.tamed": "%s trusts you now",
        "message.underdeep.companion.warn": "%s senses danger: %s",
        "message.underdeep.companion.sniff": "%s has caught a scent",
        "message.underdeep.companion.fish": "%s brought you a fish",
        "message.underdeep.journal.new": "New creature recorded: %s",
        "message.underdeep.journal.header": "Field journal — %s of %s creatures recorded",
        "message.underdeep.journal.more": "Sneak and use the journal for page %s",
        "block.underdeep.twig_nest": "Twig Nest",
        "block.underdeep.burrow_mound": "Burrow Mound",
        "block.underdeep.creature_egg_block": "Creature Egg",
    })
    print(f"creatures: {len(SPECIES)} species")
    return LANG, TAGS


if __name__ == "__main__":
    main()
