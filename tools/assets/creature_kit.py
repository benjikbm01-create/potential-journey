"""Underdeep creature anatomy kit (Stage 11): hand-authored, articulated cuboid creatures and their textures.

Authoring space: y up from the ground (pixels, 16 = one block), head towards -z, x across. Part pivots are absolute in
authoring space unless ``rel=True`` (then relative to the parent pivot, so parent rotations bend chains such as tails).
Output is the creature JSON format read by client/creature/CreatureModel (MC model space: y down, ground at y = 24).

Texture painting is deliberate, not noise: per material a base colour with shading by face orientation, dorsal/ventral
countershading, a darker rim on the lower edges, and authored markings (stripes, spots, saddle, rings, scales, chitin
plates, fur strokes) placed by rules. Eyes, nostrils and mouths are painted where the design puts them.
"""
import math
import random

from PIL import Image


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


class Rig:
    def __init__(self, sid, pal, seed):
        self.sid = sid
        self.pal = dict(pal)
        self.parts = []
        self.by = {}
        self.rnd = random.Random(seed)
        self.eyes = []  # (part, face, u, v, size, style)
        self.features = []  # painted marks: (part, face, kind, args)
        self.extra = {}

    # ------------------------------------------------------------------ parts
    def part(self, name, parent, pivot, boxes, role="decor", rot=(0, 0, 0), rel=False, **meta):
        if parent != "root" and parent not in self.by:
            raise ValueError(f"{self.sid}: parent {parent} of {name} missing")
        if rel and parent != "root":
            pp = self.by[parent]["abs"]
            ab = [pp[0] + pivot[0], pp[1] + pivot[1], pp[2] + pivot[2]]
        else:
            ab = list(pivot)
        p = {"name": name, "parent": parent, "abs": ab, "relpivot": list(pivot) if rel else None, "rot": list(rot), "role": role,
             "boxes": [dict(b) for b in boxes]}
        p.update(meta)
        self.parts.append(p)
        self.by[name] = p
        return name

    def box(self, x, y, z, w, h, d, mat="base", zone="plain", mirror=False, dilate=0.0):
        """Box relative to its part pivot (y up): from (x, y, z), size (w, h, d)."""
        return {"_from": [x, y, z], "size": [w, h, d], "mat": mat, "zone": zone, "mirror": mirror, "dilate": dilate}

    def eye(self, part, face, u, v, size=2, style="round"):
        self.eyes.append((part, face, u, v, size, style))

    def mark(self, part, face, kind, *args, box=0):
        self.features.append((part, face, kind, args, box))

    # ------------------------------------------------------------------ output
    def to_json(self):
        out = []
        for p in self.parts:
            ab = p["abs"]
            if p["parent"] == "root":
                pivot = [ab[0], 24 - ab[1], ab[2]]
            elif p["relpivot"] is not None:
                r = p["relpivot"]
                pivot = [r[0], -r[1], r[2]]
            else:
                pp = self.by[p["parent"]]["abs"]
                pivot = [ab[0] - pp[0], -(ab[1] - pp[1]), ab[2] - pp[2]]
            boxes = []
            for b in p["boxes"]:
                x, y, z = b["_from"]
                w, h, d = b["size"]
                q = {"from": [x, -(y + h), z], "size": [w, h, d], "mirror": b["mirror"], "uv": b["uv"]}
                if b["dilate"]:
                    q["dilate"] = b["dilate"]
                boxes.append(q)
            j = {"name": p["name"], "parent": p["parent"], "pivot": [round(v, 3) for v in pivot], "role": p["role"], "boxes": boxes}
            if any(p["rot"]):
                j["rot"] = [round(v, 4) for v in p["rot"]]
            for k in ("phase", "index", "side", "chain", "amp", "unfold"):
                if k in p:
                    j[k] = p[k]
            out.append(j)
        return out


# ====================================================================== texture packing and painting

def pack(parts, width=64):
    """Shelf-packs every box's UV net; returns texture size (power of two)."""
    items = []
    for p in parts:
        for b in p["boxes"]:
            w, h, d = (int(math.ceil(v)) for v in b["size"])
            items.append((max(1, 2 * d + 2 * w), max(1, d + h), b))
    items.sort(key=lambda t: -t[1])
    W = width
    while True:
        x = y = row = 0
        ok = True
        for uw, uh, b in items:
            if uw > W:
                ok = False
                break
            if x + uw > W:
                x, y, row = 0, y + row, 0
            b["uv"] = [x, y]
            x += uw
            row = max(row, uh)
        H = y + row
        if ok and H <= W * 2:
            break
        W *= 2
    Hp = 16
    while Hp < H:
        Hp *= 2
    return W, Hp


def face_rects(b):
    u, v = b["uv"]
    w, h, d = (int(math.ceil(x)) for x in b["size"])
    return {"up": (u + d, v, w, d), "down": (u + d + w, v, w, d), "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
            "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h)}


def h01(*xs):
    v = 0
    for x in xs:
        v = (v * 1103515245 + int(x) * 12345 + 0x9E3779B9) & 0xFFFFFFFF
    return ((v >> 8) & 0xFFFF) / 65535.0


def paint(rig):
    W, H = pack(rig.parts)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px, gx = img.load(), glow.load()
    pal = rig.pal
    seed = rig.rnd.randrange(1 << 16)

    def put(x, y, c, g=False):
        if 0 <= x < W and 0 <= y < H:
            px[x, y] = (*c[:3], 255)
            if g:
                gx[x, y] = (*c[:3], 255)

    for p in rig.parts:
        for bi, b in enumerate(p["boxes"]):
            mat = b["mat"]
            base = pal.get(mat, pal["base"])
            glowing = mat in pal.get("glow_mats", ())
            belly = pal.get("belly", shade(base, 1.2))
            back = pal.get("back", shade(base, 0.85))
            zone = b["zone"]
            pattern = pal.get("pattern", "") if zone in ("body", "limb", "tail", "head", "neck") else ""
            if mat not in ("base", "back") and zone != "body":
                pattern = pal.get(mat + "_pattern", "")
            rects = face_rects(b)
            bw, bh, bd = b["size"]
            for fname, (fx, fy, fw, fh) in rects.items():
                light = {"up": 1.12, "down": 0.7, "north": 1.0, "south": 0.9, "east": 0.94, "west": 0.94}[fname]
                for i in range(fw):
                    for j in range(fh):
                        c = base
                        # countershading on bodies: belly colour on the lower part of side faces and on the underside
                        if zone in ("body", "neck", "head", "tail") and mat == "base":
                            if fname == "down":
                                c = belly
                            elif fname in ("east", "west", "north", "south") and fh >= 3:
                                t = j / max(1, fh - 1)
                                if t > 0.62:
                                    c = mix(base, belly, min(1, (t - 0.62) / 0.3))
                            if fname == "up" or fname in ("east", "west") and j <= max(0, fh // 5 - 1):
                                c = mix(c, back, 0.6)
                        # authored patterns (deterministic, structured)
                        gxw, gyw = fx + i, fy + j
                        if pattern == "stripes" and fname in ("up", "east", "west") and (i + (3 if fname == "up" else 0)) % 4 == 0:
                            c = pal.get("mark", shade(base, 0.65))
                        elif pattern == "bands" and fname in ("east", "west", "up") and (i // 2) % 3 == 0 and zone != "head":
                            c = pal.get("mark", shade(base, 0.7))
                        elif pattern == "spots" and fname in ("up", "east", "west") and (fname == "up" or j < fh * 0.55):
                            # sparse round dapples: a dot centre every ~3 texels, sometimes a 2-texel blot
                            cx3, cy3 = gxw // 3, gyw // 3
                            if h01(seed, bi, cx3, cy3) > 0.55:
                                ox, oy = int(h01(seed + 1, cx3, cy3) * 2), int(h01(seed + 2, cx3, cy3) * 2)
                                big = h01(seed + 3, cx3, cy3) > 0.6
                                dx, dy = gxw - cx3 * 3 - ox, gyw - cy3 * 3 - oy
                                if (dx, dy) == (0, 0) or big and (dx, dy) in ((1, 0), (0, 1)):
                                    c = pal.get("mark", shade(base, 1.35))
                        elif pattern == "saddle" and fname in ("up",) or pattern == "saddle" and fname in ("east", "west") and j < fh * 0.35:
                            c = mix(c, pal.get("mark", shade(base, 0.6)), 0.75)
                        elif pattern == "scales" and fname != "down":
                            if (j % 2 == 0 and i % 3 == 0) or (j % 2 == 1 and i % 3 == 1):
                                c = shade(c, 0.86)
                            elif (j % 2 == 0 and i % 3 == 1):
                                c = shade(c, 1.08)
                        elif pattern == "plates" and fname != "down":
                            if i % 4 == 3 or j % 3 == 2:
                                c = shade(c, 0.78)
                            elif i % 4 == 0 and j % 3 == 0:
                                c = shade(c, 1.15)
                        elif pattern == "fur" and fname in ("up", "east", "west", "north", "south"):
                            if h01(seed, bi, gxw, gyw // 2) > 0.86:
                                c = shade(c, 0.88)
                            elif h01(seed + 7, bi, gxw // 2, gyw) > 0.92:
                                c = shade(c, 1.08)
                        elif pattern == "rings" and fname != "down" and (i if fname in ("up",) else i) % 3 == 0 and zone == "tail":
                            c = pal.get("mark", shade(base, 0.62))
                        elif pattern == "crystal" and fname != "down":
                            if (i + j) % 4 == 0:
                                c = shade(c, 1.28)
                            elif (i - j) % 5 == 0:
                                c = shade(c, 0.85)
                        elif pattern == "chitin" and fname != "down":
                            if j == 0 and fname in ("east", "west", "north", "south"):
                                c = shade(c, 1.25)
                            elif fname == "up" and i == fw // 2:
                                c = shade(c, 1.18)
                            elif fname == "up" and (j % 3 == 2):
                                c = shade(c, 0.85)
                        elif pattern == "feathers" and fname != "down":
                            if (j % 2 == 0 and (i + j // 2) % 3 == 0):
                                c = shade(c, 0.84)
                            elif j % 2 == 1 and (i + j // 2) % 3 == 1:
                                c = shade(c, 1.1)
                        elif pattern == "membrane" and fname in ("up", "down"):
                            if (i % 4 == 0) or (j == 0):
                                c = shade(pal.get("bone", shade(base, 0.6)), 1.0)
                        elif pattern == "bark":
                            if i % 3 == 0 or h01(seed, i, j // 3) > 0.85:
                                c = shade(c, 0.8)
                        f = light
                        # rim: darken bottom edge of side faces, lighten top edge
                        if fname in ("east", "west", "north", "south"):
                            if j == fh - 1 and fh > 2:
                                f *= 0.82
                            elif j == 0 and fh > 2:
                                f *= 1.08
                        put(fx + i, fy + j, shade(c, f), glowing)
    # features
    for part, face, kind, args, bi in rig.features:
        p = rig.by.get(part)
        if not p:
            continue
        b = p["boxes"][min(bi, len(p["boxes"]) - 1)]
        fx, fy, fw, fh = face_rects(b)[face]
        if kind == "nostrils":
            for dx in (1, fw - 2):
                if fw >= 4:
                    put(fx + dx, fy + max(0, fh // 3), shade(pal.get("dark", (30, 24, 22)), 1))
        elif kind == "mouth":
            yy = fy + fh - 1 - (args[0] if args else 1)
            for i in range(1, fw - 1):
                put(fx + i, yy, shade(pal.get("dark", (30, 24, 22)), 1.2))
        elif kind == "stripe_v":
            col = args[0]
            for j in range(fh):
                put(fx + args[1], fy + j, col)
        elif kind == "glowdots":
            col = args[0]
            for i in range(fw):
                for j in range(fh):
                    if h01(seed, fx + i, fy + j) > 0.82 and (i + j) % 2 == 0:
                        put(fx + i, fy + j, col, True)
        elif kind == "fill":
            col = args[0]
            for i in range(fw):
                for j in range(fh):
                    put(fx + i, fy + j, shade(col, 1 if j else 1.1), len(args) > 1 and args[1])
    # eyes
    for part, face, u, v, size, style in rig.eyes:
        p = rig.by.get(part)
        if not p:
            continue
        fx, fy, fw, fh = face_rects(p["boxes"][0])[face]
        iris = pal.get("eye", (24, 20, 18))
        g = style.endswith("glow")
        cx = fx + (u if u >= 0 else fw + u)
        cy = fy + v
        if size >= 2:
            sclera = pal.get("sclera", None)
            for dx in range(size):
                for dy in range(size):
                    put(cx + dx, cy + dy, iris, g)
            if style.startswith("slit"):
                for dy in range(size):
                    put(cx + size // 2, cy + dy, (10, 8, 8))
            put(cx + (0 if face in ("west", "north") else size - 1), cy, shade(iris, 2.6) if not g else (255, 255, 255), g)
            if sclera:
                put(cx - 1 if face in ("west", "north") else cx + size, cy + size - 1, sclera)
        else:
            put(cx, cy, iris, g)
    return img, glow, (W, H)


# ====================================================================== body-plan builders

def leg(r, name, parent, x, y, z, upper, lower, w, foot=None, side=0, index=0, phase=0, mat="base", foot_mat="hoof", knee=0.0, splay=0.0,
        lower_w=None):
    """Two-segment leg: upper (thigh/shoulder) and lower (shin) hanging from (x, y, z); optional foot/hoof box."""
    lw = lower_w if lower_w is not None else max(1, w - 1)
    r.part(name, parent, [x, y, z], [r.box(-w / 2, -upper, -w / 2, w, upper + 0.5, w, mat, "limb", mirror=side == 1)], "leg_upper",
           rot=(knee, 0, splay * (1 if side == 0 else -1)), phase=phase, index=index, side=side)
    r.part(name + "_lower", name, [0, -upper, 0], [r.box(-lw / 2, -lower, -lw / 2, lw, lower + 0.3, lw, mat, "limb", mirror=side == 1)]
           + ([r.box(-lw / 2 - 0.25, -lower - foot[1], -lw / 2 - foot[0], lw + 0.5, foot[1], lw + foot[0], foot_mat, "plain")] if foot else []),
           "leg_lower", rot=(-knee * 1.6, 0, 0), rel=True, phase=phase, index=index, side=side)


def tail(r, parent, x, y, z, segs, lengths, widths, droop=0.35, curl=0.0, mat="base", zone="tail", heights=None, tip=None):
    prev = parent
    px, py, pz = x, y, z
    for i in range(segs):
        L, Wd = lengths[i], widths[i]
        Hh = heights[i] if heights else Wd
        rel = i > 0
        pv = [0, 0, lengths[i - 1]] if rel else [px, py, pz]
        boxes = [r.box(-Wd / 2, -Hh / 2, 0, Wd, Hh, L + 0.3, mat, zone)]
        if tip and i == segs - 1:
            boxes.append(tip)
        prev = r.part(f"tail{i}", prev, pv, boxes, "tail", rot=(-droop if i == 0 else -curl, 0, 0), rel=rel, chain=i)
    return prev


def quad(r, L, chest, hip, y, legs, neck, head, tail_spec=None, ears=None, hooves=True, stance=0.0, hump=None):
    """Quadruped: chest and hip masses (w, h), overlapping barrel; neck (len, w, h, angle); head (w, h, l) with muzzle and jaw.

    legs: (upper ratio, width, foot (extra depth, height) or None[, lower width]) for front and rear; the legs always reach the
    ground. stance bends the rear knees (digitigrade hocks)."""
    cw, ch = chest
    hw, hh = hip
    body = r.part("body", "root", [0, y, 0], [
        r.box(-cw / 2, -ch / 2, -L / 2, cw, ch, L * 0.55, "base", "body"),                     # chest
        r.box(-hw / 2, -hh / 2 + (ch - hh) * 0.12, -L / 2 + L * 0.5, hw, hh, L * 0.5, "base", "body"),  # hips
        r.box(-(cw + hw) / 4 + 0.25, -min(ch, hh) / 2 - 0.6, -L / 2 + L * 0.25, (cw + hw) / 2 - 0.5, min(ch, hh) * 0.6, L * 0.5, "base", "body"),
    ], "body")
    if hump:
        r.part("hump", body, [0, y + ch / 2 - 0.5, -L / 2 + hump[2]], [r.box(-hump[0] / 2, 0, 0, hump[0], hump[1], hump[3], hump[4] if len(hump) > 4 else "base",
                                                                           "body")], "decor")
    top = y - ch / 2 + 1.5
    for i, (x, z, spec, idx, ph) in enumerate([(cw / 2 - legs[0][1] / 2, -L / 2 + legs[0][1] / 2 + 1, legs[0], 0, 0),
                                               (-cw / 2 + legs[0][1] / 2, -L / 2 + legs[0][1] / 2 + 1, legs[0], 0, 1),
                                               (hw / 2 - legs[1][1] / 2, L / 2 - legs[1][1] / 2 - 1, legs[1], 1, 1),
                                               (-hw / 2 + legs[1][1] / 2, L / 2 - legs[1][1] / 2 - 1, legs[1], 1, 0)]):
        ratio, Wd, F = spec[0], spec[1], spec[2]
        k = stance if idx == 1 else -stance * 0.3
        Hh = top - (F[1] if F else 0)
        X = Hh / (ratio * math.cos(k) + (1 - ratio) * math.cos(0.6 * k))
        leg(r, f"leg{i}", body, x, top, z, X * ratio, X * (1 - ratio), Wd, foot=F, side=i % 2, index=idx, phase=ph,
            foot_mat="hoof" if hooves else "paw", knee=k, lower_w=spec[3] if len(spec) > 3 else None)
    nl, nw, nh, nang = neck
    hx, hy, hz, ml, mw, mh = head
    if nl > 0:
        r.part("neck", body, [0, y + ch * 0.18, -L / 2 + 1], [r.box(-nw / 2, -nh / 2, -nl, nw, nh, nl + 1, "base", "neck")], "neck", rot=(-nang, 0, 0))
        hp, hpv = "neck", [0, 0, -nl]
    else:
        hp, hpv = body, None
    hb = [r.box(-hx / 2, -hy / 2, -hz, hx, hy, hz, "base", "head")]
    if ml > 0:
        hb.append(r.box(-mw / 2, -hy / 2, -hz - ml, mw, mh, ml + 0.2, "muzzle" if "muzzle" in r.pal else "base", "head"))
    if hpv:
        r.part("head", hp, hpv, hb, "head", rot=(nang * 0.85, 0, 0), rel=True)
    else:
        r.part("head", hp, [0, y + ch * 0.15, -L / 2], hb, "head")
    # jaw under the muzzle
    jl = ml + hz * 0.45 if ml else hz * 0.6
    r.part("jaw", "head", [0, -hy / 2 + 0.2, -hz * 0.4], [r.box(-(mw if ml else hx) / 2 + 0.3, -1.2, -jl + hz * 0.4 - 0.2, (mw if ml else hx) - 0.6, 1.2, jl,
                                                         "muzzle" if "muzzle" in r.pal and ml else "base", "head")], "jaw", rel=True)
    r.eye("head", "east", 1 if ml else 1, max(0, int(hy * 0.28)), 2 if hy >= 4 else 1)
    r.eye("head", "west", -3 if hy >= 4 else -2, max(0, int(hy * 0.28)), 2 if hy >= 4 else 1)
    if ml:
        r.mark("head", "north", "nostrils", box=1)
    if ears:
        el, ew, eang = ears
        for s, sx in ((0, 1), (1, -1)):
            r.part(f"ear{s}", "head", [sx * (hx / 2 - ew / 2), hy / 2 - 0.3, -hz * 0.25], [r.box(-ew / 2, 0, -0.5, ew, el, 1, "ear" if "ear" in r.pal else "base", "head")],
                   "ear", rot=(0.2, 0, eang * sx), rel=True, side=s)
    if tail_spec:
        segs, lens, wids, droop = tail_spec
        tail(r, body, 0, y + hh * 0.2, L / 2 - 0.5, segs, lens, wids, droop=droop)
    return body


def lizard(r, L, W, H, y, leg_len, leg_w, head, tail_spec, splay=1.0, frills=False):
    body = r.part("body", "root", [0, y, 0], [r.box(-W / 2, -H / 2, -L / 2, W, H, L, "base", "body"),
                                               r.box(-W / 2 + 0.5, -H / 2 - 0.4, -L / 2 + 1, W - 1, 0.6, L - 2, "base", "body")], "body")
    hx, hy, hz, ml = head
    r.part("head", body, [0, y + 0.3, -L / 2], [r.box(-hx / 2, -hy / 2, -hz, hx, hy, hz, "base", "head"),
                                                 r.box(-hx / 2 + 0.75, -hy / 2, -hz - ml, hx - 1.5, hy - 1, ml + 0.2, "base", "head")], "head")
    r.part("jaw", "head", [0, -hy / 2 + 0.2, -hz * 0.3], [r.box(-hx / 2 + 0.5, -1, -hz * 0.7 - ml, hx - 1, 1, hz * 0.7 + ml, "belly", "head")], "jaw", rel=True)
    r.eye("head", "east", 1, 1, 1 if hy < 3 else 2, "round")
    r.eye("head", "west", -2, 1, 1 if hy < 3 else 2, "round")
    for i, (x, z, idx, ph) in enumerate([(W / 2, -L / 2 + 1.5, 0, 0), (-W / 2, -L / 2 + 1.5, 0, 1), (W / 2, L / 2 - 1.5, 1, 1), (-W / 2, L / 2 - 1.5, 1, 0)]):
        s = i % 2
        sx = 1 if s == 0 else -1
        r.part(f"leg{i}", body, [x, y - H / 2 + 1, z], [r.box(0 if s == 0 else -leg_len, -leg_w / 2, -leg_w / 2, leg_len, leg_w, leg_w, "base", "limb")],
               "leg_upper", rot=(0, 0, 0), phase=ph, index=idx, side=s)
        r.part(f"leg{i}_lower", f"leg{i}", [sx * leg_len, 0, 0], [r.box(-leg_w / 2, -(y - H / 2 + 1) - 0.2, -leg_w / 2, leg_w, y - H / 2 + 1, leg_w, "base", "limb"),
                                                                   r.box(-leg_w / 2 - 0.4, -(y - H / 2 + 1) - 0.2, -leg_w / 2 - 1, leg_w + 0.8, 0.6, leg_w + 1, "paw", "plain")],
               "leg_lower", rel=True, phase=ph, index=idx, side=s)
    segs, lens, wids, droop = tail_spec
    tail(r, body, 0, y, L / 2 - 0.5, segs, lens, wids, droop=droop, heights=[w * 0.8 for w in wids])
    return body


def bug(r, thorax, abdomen, head, y, leg_len, legs=3, antenna=4, mandible=0, wings=None, mat_abd="base"):
    tx, ty, tz = thorax
    ax, ay, az = abdomen
    hx, hy, hz = head
    body = r.part("body", "root", [0, y, 0], [r.box(-tx / 2, -ty / 2, -tz / 2, tx, ty, tz, "base", "body")], "body")
    r.part("abdomen", body, [0, y + 0.5, tz / 2 - 0.5], [r.box(-ax / 2, -ay / 2, 0, ax, ay, az * 0.55, mat_abd, "body"),
                                                         r.box(-ax / 2 + 0.75, -ay / 2 + 0.3, az * 0.55 - 0.2, ax - 1.5, ay - 0.8, az * 0.45, mat_abd, "body")],
           "tail", rot=(-0.12, 0, 0), chain=0)
    r.part("head", body, [0, y + 0.3, -tz / 2], [r.box(-hx / 2, -hy / 2, -hz, hx, hy, hz, "head" if "head" in r.pal else "base", "head")], "head")
    r.eye("head", "east", 0, max(0, int(hy / 2) - 1), 2 if hy >= 3 else 1, "round")
    r.eye("head", "west", -2 if hy >= 3 else -1, max(0, int(hy / 2) - 1), 2 if hy >= 3 else 1, "round")
    if mandible:
        for s, sx in ((0, 1), (1, -1)):
            r.part(f"mandible{s}", "head", [sx * (hx / 2 - 0.5), -hy / 2 + 0.5, -hz], [r.box(-0.5, -0.5, -mandible, 1, 1, mandible, "accent", "plain")],
                   "jaw", rot=(0, -sx * 0.35, 0), rel=True, side=s)
    if antenna:
        for s, sx in ((0, 1), (1, -1)):
            r.part(f"antenna{s}", "head", [sx * (hx / 2 - 0.6), hy / 2 - 0.3, -hz + 0.5], [r.box(-0.25, 0, -antenna, 0.5, 0.5, antenna, "accent", "plain")],
                   "antenna", rot=(-0.55, -sx * 0.25, 0), rel=True, side=s)
    hp = y - ty / 2 + 1
    rise = leg_len * 0.55 * math.sin(0.45)
    lower = (hp + rise) / math.cos(0.45) - 0.2
    for k in range(legs):
        z = -tz / 2 + 0.8 + k * (tz - 1.6) / max(1, legs - 1)
        for s, sx in ((0, 1), (1, -1)):
            nm = f"leg{k * 2 + s}"
            r.part(nm, body, [sx * tx / 2, hp, z], [r.box(0 if s == 0 else -leg_len * 0.55, -0.5, -0.5, leg_len * 0.55, 1, 1, "base", "limb")],
                   "leg_upper", rot=(0, sx * (0.6 - k * 0.6), sx * -0.45), phase=(k + s) % 2, index=k, side=s)
            r.part(nm + "_lower", nm, [sx * leg_len * 0.55, 0, 0], [r.box(-0.5, -lower, -0.5, 1, lower + 0.5, 1, "accent" if "limbdark" not in r.pal else "limbdark", "limb")],
                   "leg_lower", rot=(0, 0, 0), rel=True, phase=(k + s) % 2, index=k, side=s)
    if wings:
        wl, ww, mat = wings
        for s, sx in ((0, 1), (1, -1)):
            r.part(f"wing{s}", body, [sx * (tx / 2 - 0.5), y + ty / 2, -tz / 4], [r.box(0 if s == 0 else -ww, 0, -0.5, ww, 0.25, wl, mat, "wing")], "wing",
                   rot=(-0.05, sx * -0.15, sx * 0.08), side=s)
    return body


def bird(r, body_len, body_w, body_h, y, neck, head, beak, wing, tail_len, legs, crest=None):
    bl, bw, bh = body_len, body_w, body_h
    body = r.part("body", "root", [0, y, 0], [r.box(-bw / 2, -bh / 2, -bl / 2, bw, bh, bl * 0.7, "base", "body"),
                                               r.box(-bw / 2 + 0.75, -bh / 2 + 0.5, -bl / 2 + bl * 0.6, bw - 1.5, bh - 1.2, bl * 0.4, "base", "body")],
                  "body", rot=(-0.15, 0, 0))
    nl, nw = neck
    hx, hy, hz = head
    r.part("neck", body, [0, y + bh / 2 - 1, -bl / 2 + 1], [r.box(-nw / 2, -nw / 2, -1, nw, nl, nw, "base", "neck")], "neck", rot=(-0.25, 0, 0))
    r.part("head", "neck", [0, nl - 0.5, -0.5], [r.box(-hx / 2, -hy / 2, -hz + 1, hx, hy, hz, "base", "head")], "head", rot=(0.25, 0, 0), rel=True)
    bl_, bw_ = beak
    r.part("beak", "head", [0, -0.3, -hz + 1], [r.box(-bw_ / 2, -0.6, -bl_, bw_, 1.2, bl_, "beak", "plain")], "jaw", rel=True)
    r.eye("head", "east", 1, max(0, hy // 3), 1 if hy < 4 else 2)
    r.eye("head", "west", -2 if hy >= 4 else -1, max(0, hy // 3), 1 if hy < 4 else 2)
    span, depth = wing
    for s, sx in ((0, 1), (1, -1)):
        # wings rest folded against the flanks (span pointing back, chord hanging down); "unfold" opens them in flight
        r.part(f"wing{s}", body, [sx * bw / 2, y + bh / 2 - 1, -bl / 2 + 1.5], [r.box(0 if s == 0 else -span * 0.5, -0.5, 0, span * 0.5, 1, depth, "wing", "wing")],
               "wing", side=s, rot=(-1.45, -sx * 1.5, 0), unfold=[1.45, sx * 1.5, 0])
        r.part(f"wing{s}_outer", f"wing{s}", [sx * span * 0.5, 0, 0], [r.box(0 if s == 0 else -span * 0.5, -0.25, 0, span * 0.5, 0.5, depth * 0.85, "wingtip", "wing")],
               "wing_outer", side=s, rel=True)
    r.part("tailfan", body, [0, y + 0.5, bl / 2 - 0.5], [r.box(-bw / 2 + 0.5, -0.25, 0, bw - 1, 0.5, tail_len, "wingtip", "wing")], "tail", rot=(-0.2, 0, 0), chain=0)
    ll, lw = legs
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"leg{s}", body, [sx * bw / 4, y - bh / 2 + 0.5, 0.5], [r.box(-lw / 2, -(y - bh / 2 + 0.5), -lw / 2, lw, y - bh / 2 + 0.5, lw, "beak", "limb"),
                                                                        r.box(-1, -(y - bh / 2 + 0.5), -2, 2, 0.4, 3, "beak", "plain")],
               "leg_upper", phase=s, index=0, side=s)
    if crest:
        r.part("crest", "head", [0, hy / 2, -hz / 2 + 1], [r.box(-0.5, 0, -1, 1, crest[0], crest[1], "accent", "plain")], "crest", rot=(-0.5, 0, 0), rel=True)
    return body


def fish(r, L, W, H, y, tail_len, dorsal, pect, head_frac=0.3, glow=False, belly_fin=True):
    hl = L * head_frac
    body = r.part("body", "root", [0, y, 0], [r.box(-W / 2, -H / 2, -L / 2 + hl, W, H, L * 0.4, "base", "body")], "body")
    r.part("head", body, [0, y, -L / 2 + hl], [r.box(-W / 2 + 0.25, -H / 2 + 0.25, -hl, W - 0.5, H - 0.5, hl + 0.2, "base", "head")], "head")
    r.part("jaw", "head", [0, -H / 2 + 0.6, -hl * 0.4], [r.box(-W / 2 + 0.75, -0.8, -hl * 0.6, W - 1.5, 0.8, hl * 0.6, "belly", "head")], "jaw", rel=True)
    r.eye("head", "east", 1, max(0, int(H / 3)), 2 if H >= 4 else 1)
    r.eye("head", "west", -3 if H >= 4 else -2, max(0, int(H / 3)), 2 if H >= 4 else 1)
    r1 = r.part("seg0", body, [0, y, -L / 2 + hl + L * 0.4], [r.box(-W / 2 + 0.5, -H / 2 + 0.5, 0, W - 1, H - 1, L * 0.3, "base", "body")], "segment", chain=0)
    r.part("seg1", r1, [0, 0, L * 0.3], [r.box(-W / 2 + 1, -H / 4, 0, max(1, W - 2), max(1, H / 2), tail_len * 0.4, "base", "tail")], "segment", rel=True, chain=1)
    r.part("tailfin", "seg1", [0, 0, tail_len * 0.4], [r.box(-0.25, -H * 0.6, 0, 0.5, H * 1.2, tail_len * 0.6, "fin", "wing")], "tailfin", rel=True)
    if dorsal:
        r.part("dorsal", body, [0, y + H / 2, -L / 2 + hl + 1], [r.box(-0.25, 0, 0, 0.5, dorsal, L * 0.35, "fin", "wing")], "fin", rot=(0.2, 0, 0))
    if pect:
        for s, sx in ((0, 1), (1, -1)):
            r.part(f"pect{s}", body, [sx * W / 2, y - H / 4, -L / 2 + hl + 0.5], [r.box(0 if s == 0 else -pect, -0.25, 0, pect, 0.5, pect * 0.8, "fin", "wing")],
                   "fin", rot=(0.2, 0, sx * 0.4), side=s)
    if belly_fin:
        r.part("vent", body, [0, y - H / 2, -L / 2 + hl + L * 0.25], [r.box(-0.25, -max(1, H * 0.3), 0, 0.5, max(1, H * 0.3), L * 0.18, "fin", "wing")], "fin")
    return body


def eel(r, segs, seg_len, thick, y, head_len, fin=True):
    body = r.part("body", "root", [0, y, 0], [r.box(-thick / 2, -thick / 2, -seg_len / 2, thick, thick, seg_len, "base", "body")], "body")
    r.part("head", body, [0, y, -seg_len / 2], [r.box(-thick / 2 - 0.25, -thick / 2, -head_len, thick + 0.5, thick, head_len, "base", "head")], "head")
    r.part("jaw", "head", [0, -thick / 2 + 0.6, -head_len * 0.3], [r.box(-thick / 2, -0.8, -head_len * 0.7, thick, 0.8, head_len * 0.7, "belly", "head")], "jaw", rel=True)
    r.eye("head", "east", 1, 0, 1)
    r.eye("head", "west", -2, 0, 1)
    prev = body
    for i in range(segs):
        t = thick * (1 - 0.6 * (i + 1) / segs)
        pv = [0, 0, seg_len / 2] if i == 0 else [0, 0, seg_len]
        boxes = [r.box(-t / 2, -t / 2, 0, t, t, seg_len + 0.3, "base", "tail")]
        if fin:
            boxes.append(r.box(-0.2, t / 2, 0, 0.4, 1.2, seg_len, "fin", "wing"))
        prev = r.part(f"seg{i}", prev, pv, boxes, "segment", rel=True, chain=i)
    return body


def crab(r, W, L, H, y, leg_len, claw):
    body = r.part("body", "root", [0, y, 0], [r.box(-W / 2, -H / 2, -L / 2, W, H, L, "base", "body"),
                                               r.box(-W / 2 + 1, H / 2 - 0.2, -L / 2 + 1, W - 2, 1, L - 2, "back", "body")], "body")
    for s, sx in ((0, 1), (1, -1)):
        r.part(f"eyestalk{s}", body, [sx * (W / 4), y + H / 2, -L / 2 + 0.5], [r.box(-0.5, 0, -0.5, 1, 2, 1, "accent", "plain"),
                                                                               r.box(-0.75, 2, -0.75, 1.5, 1.5, 1.5, "eyeball", "plain")], "antenna", side=s)
        r.part(f"claw{s}", body, [sx * (W / 2 - 1), y - 0.5, -L / 2], [r.box(-1, -1, -claw, 2, 2, claw, "base", "limb")], "arm", rot=(0, sx * 0.6, 0), side=s)
        r.part(f"pincer{s}", f"claw{s}", [0, 0, -claw], [r.box(-claw * 0.45, -1.25, -claw * 0.9, claw * 0.9, 2.5, claw * 0.9, "accent", "limb")], "jaw", rel=True, side=s)
        hp = y - H / 2 + 1
        rise = leg_len * 0.6 * math.sin(0.35)
        lower = (hp + rise) / math.cos(0.35) - 0.2
        for k in range(3):
            nm = f"leg{k * 2 + s}"
            z = -L / 2 + 1.5 + k * (L - 3) / 2
            r.part(nm, body, [sx * W / 2, hp, z], [r.box(0 if s == 0 else -leg_len * 0.6, -0.5, -0.5, leg_len * 0.6, 1, 1, "base", "limb")],
                   "leg_upper", rot=(0, sx * (0.4 - k * 0.4), sx * -0.35), phase=(k + s) % 2, index=k, side=s)
            r.part(nm + "_lower", nm, [sx * leg_len * 0.6, 0, 0], [r.box(-0.5, -lower, -0.5, 1, lower + 0.5, 1, "accent", "limb")],
                   "leg_lower", rot=(0, 0, 0), rel=True, phase=(k + s) % 2, index=k, side=s)
    for s, sx in ((0, 1), (1, -1)):
        r.eye(f"eyestalk{s}", "north", 0, 0, 1, "round")
    return body


def jelly(r, R, H, y, tentacles, tlen, glow=True):
    body = r.part("body", "root", [0, y, 0], [r.box(-R, 0, -R, 2 * R, H * 0.55, 2 * R, "base", "body"),
                                               r.box(-R + 1, H * 0.55, -R + 1, 2 * R - 2, H * 0.3, 2 * R - 2, "base", "body"),
                                               r.box(-R + 2.5, H * 0.85, -R + 2.5, 2 * R - 5, H * 0.15, 2 * R - 5, "accent", "body"),
                                               r.box(-R - 0.5, -0.8, -R - 0.5, 2 * R + 1, 0.8, 2 * R + 1, "skirt", "plain")], "body")
    for i in range(tentacles):
        a = i * math.pi * 2 / tentacles
        x, z = math.cos(a) * (R - 1.2), math.sin(a) * (R - 1.2)
        prev = r.part(f"tentacle{i}", body, [x, y - 0.5, z], [r.box(-0.4, -tlen * 0.5, -0.4, 0.8, tlen * 0.5, 0.8, "tentacle", "plain")], "tentacle", index=i,
                      chain=0, phase=i % 2)
        r.part(f"tentacle{i}b", prev, [0, -tlen * 0.5, 0], [r.box(-0.3, -tlen * 0.5, -0.3, 0.6, tlen * 0.5, 0.6, "tentacle", "plain")], "tentacle", rel=True,
               index=i, chain=1, phase=i % 2)
    return body
