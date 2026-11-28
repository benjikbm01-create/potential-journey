"""Offscreen preview renderer for Underdeep cuboid entity models (creatures, bosses).

Renders the real geometry and UV-mapped textures of a model JSON (creature format: boxes with from/size/uv; boss rig
format: boxes as [x, y, z, w, h, d, u, v] in y-up pixels) from several views with a player-sized reference, so models
can be inspected and compared without the game. Every texel becomes a projected quad drawn back to front (painter's
algorithm) with simple directional face lighting: crisp, pixel-art faithful.

Usage:
  python3 tools/preview/render_models.py creature <id> [out.png]
  python3 tools/preview/render_models.py sheet creatures <out.png> [ids...]
  python3 tools/preview/render_models.py boss <id> <out.png>        (private: write under docs/private/)
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "underdeep")


def rot_matrix(rx, ry, rz):
    """MC ModelPart rotation: rotationZYX(roll, yaw, pitch) => R = Rz * Ry * Rx."""
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mul(Rz, mul(Ry, Rx))


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return [m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2], m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
            m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]]


class Box:
    def __init__(self, frm, size, uv, mirror, world):
        self.frm, self.size, self.uv, self.mirror, self.world = frm, size, uv, mirror, world


def creature_boxes(spec, pose=None):
    """Creature JSON (MC model space, y down) -> boxes with a world transform function."""
    parts = {p["name"]: p for p in spec["parts"]}
    out = []

    def chain(name):
        p = parts[name]
        pv = p["pivot"]
        r = list(p.get("rot", [0, 0, 0]))
        if pose and name in pose:
            r = [r[i] + pose[name][i] for i in range(3)]
        m = rot_matrix(*r)
        parent = p["parent"]
        if parent == "root" or parent not in parts:
            return lambda v: [x + y for x, y in zip(apply(m, v), pv)]
        up = chain(parent)
        return lambda v: up([x + y for x, y in zip(apply(m, v), pv)])

    for p in spec["parts"]:
        f = chain(p["name"])
        for b in p["boxes"]:
            out.append(Box(b["from"], b["size"], b["uv"], b.get("mirror", False), f))
    return out


def boss_boxes(spec, pose=None):
    """Boss rig JSON (y up, pivots relative) -> boxes in MC model space (y down, root at 24)."""
    parts = {p["name"]: p for p in spec["parts"]}
    out = []

    def chain(name):
        p = parts[name]
        px, py, pz = p["pivot"]
        y = 24 - py if p["parent"] == "root" else -py
        r = list(p["rot"])
        if pose and name in pose:
            r = [r[i] + pose[name][i] for i in range(3)]
        m = rot_matrix(*r)
        if p["parent"] == "root" or p["parent"] not in parts:
            return lambda v: [a + b for a, b in zip(apply(m, v), (px, y, pz))]
        up = chain(p["parent"])
        return lambda v: up([a + b for a, b in zip(apply(m, v), (px, y, pz))])

    for p in spec["parts"]:
        f = chain(p["name"])
        for b in p["boxes"]:
            x, y, z, w, h, d, u, v = b[:8]
            out.append(Box([x, -(y + h), z], [w, h, d], [u, v], False, f))
    return out


def faces(box):
    """MC cuboid faces: (name, corner function over (s, t) in [0,1]^2, uv rect (u0, v0, w, h), normal)."""
    x0, y0, z0 = box.frm
    w, h, d = box.size
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    u, v = box.uv
    W, H, D = int(math.ceil(w)), int(math.ceil(h)), int(math.ceil(d))
    # (uv rect, 3D origin, s-axis, t-axis): texture u grows along s, v grows along t
    fs = [
        ("north", (u + D, v + D, W, H), (x1, y0, z0), (-w, 0, 0), (0, h, 0), (0, 0, -1)),
        ("south", (u + D + W + D, v + D, W, H), (x0, y0, z1), (w, 0, 0), (0, h, 0), (0, 0, 1)),
        ("west", (u + D + W, v + D, D, H), (x1, y0, z1), (0, 0, -d), (0, h, 0), (1, 0, 0)),
        ("east", (u, v + D, D, H), (x0, y0, z0), (0, 0, d), (0, h, 0), (-1, 0, 0)),
        ("up", (u + D, v, W, D), (x1, y0, z1), (-w, 0, 0), (0, 0, -d), (0, -1, 0)),
        ("down", (u + D + W, v, W, D), (x1, y1, z0), (-w, 0, 0), (0, 0, d), (0, 1, 0)),
    ]
    if box.mirror:
        fs = [(n, (r[0] + r[2], r[1], -r[2], r[3]), o, sa, ta, nn) for n, r, o, sa, ta, nn in fs]
    return fs


def render(boxes, tex, yaw, pitch=0.42, scale=6.0, size=(360, 360), ground=24.0, reference=True, bg=(38, 40, 48)):
    img = Image.new("RGB", size, bg)
    dr = ImageDraw.Draw(img)
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    light = [0.35, -0.85, -0.4]
    ln = math.sqrt(sum(c * c for c in light))
    light = [c / ln for c in light]

    def view(p):
        # model space: x right, y down, z forward; camera orbits around y
        x, y, z = p[0], p[1] - ground, p[2]
        x2 = x * cy - z * sy
        z2 = x * sy + z * cy
        y2 = y * cp - z2 * sp
        z3 = y * sp + z2 * cp
        return x2, y2, z3

    quads = []
    tw, th = tex.size
    px = tex.load()
    for b in boxes:
        for name, (u0, v0, uw, vh), o, sa, ta, nrm in faces(b):
            n_world = b.world([nrm[0], nrm[1], nrm[2]])
            origin = b.world([0, 0, 0])
            n_world = [n_world[i] - origin[i] for i in range(3)]
            vn = view([n_world[0], n_world[1] + ground, n_world[2]])
            nz = vn[2]
            if nz > 0.05:  # back-facing (camera looks towards +z)
                continue
            shade = 0.55 + 0.45 * max(0, -(n_world[0] * light[0] + n_world[1] * light[1] + n_world[2] * light[2]))
            cols, rows = int(abs(uw)), int(vh)
            if cols == 0 or rows == 0:
                continue
            for i in range(cols):
                for j in range(rows):
                    tu = int(u0 + (i if uw > 0 else -i - 1))
                    tv = int(v0 + j)
                    if not (0 <= tu < tw and 0 <= tv < th):
                        continue
                    c = px[tu, tv]
                    if len(c) == 4 and c[3] < 8:
                        continue
                    pts = []
                    for (a, bb) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                        s_, t_ = a / cols, bb / rows
                        p = [o[k] + sa[k] * s_ + ta[k] * t_ for k in range(3)]
                        pts.append(view(b.world(p)))
                    depth = sum(q[2] for q in pts) / 4
                    col = tuple(int(min(255, c[k] * shade)) for k in range(3))
                    quads.append((depth, [(size[0] / 2 + q[0] * scale, size[1] * 0.62 + q[1] * scale) for q in pts], col))
    if reference:
        # a player-height (32 px = 2 blocks) reference post beside the model
        rx = size[0] * 0.08
        top = size[1] * 0.62 - 32 * scale * cp
        dr.rectangle([rx, top, rx + 4 * scale * 0.5, size[1] * 0.62], fill=(90, 96, 110))
    # ground line
    dr.line([(0, size[1] * 0.62), (size[0], size[1] * 0.62)], fill=(60, 64, 74))
    quads.sort(key=lambda q: -q[0])
    for _, poly, col in quads:
        dr.polygon(poly, fill=col)
    return img


VIEWS = [("front", 0.0), ("3/4", math.pi * 0.25), ("side", math.pi / 2), ("rear", math.pi)]


def auto_scale(boxes, target=150.0):
    ext = 1.0
    for b in boxes:
        for corner in ((0, 0, 0), (1, 1, 1)):
            p = b.world([b.frm[k] + b.size[k] * corner[k] for k in range(3)])
            ext = max(ext, abs(p[0]), abs(p[2]), abs(p[1] - 24) * 0.8)
    return max(1.2, min(9.0, target / ext))


def views(boxes, tex, label="", cell=300, scale=None):
    scale = scale or auto_scale(boxes)
    sheet = Image.new("RGB", (cell * len(VIEWS), cell + 18), (28, 30, 36))
    for i, (nm, yaw) in enumerate(VIEWS):
        sheet.paste(render(boxes, tex, yaw, scale=scale, size=(cell, cell)), (i * cell, 18))
    d = ImageDraw.Draw(sheet)
    d.text((4, 3), label, fill=(230, 230, 230))
    return sheet


def creature(cid):
    spec = json.load(open(os.path.join(ROOT, "creatures", cid + ".json")))
    tex = Image.open(os.path.join(ROOT, "textures", "entity", "creature", cid + ".png")).convert("RGBA")
    return spec, tex


def main():
    mode = sys.argv[1]
    if mode == "creature":
        spec, tex = creature(sys.argv[2])
        views(creature_boxes(spec), tex, sys.argv[2]).save(sys.argv[3] if len(sys.argv) > 3 else f"/tmp/{sys.argv[2]}.png")
    elif mode == "sheet":
        out = sys.argv[3]
        ids = sys.argv[4:] or sorted(f[:-5] for f in os.listdir(os.path.join(ROOT, "creatures")))
        cell = int(os.environ.get("CELL", "170"))
        cols = int(os.environ.get("COLS", "6"))
        rows = (len(ids) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * cell * 2, rows * (cell + 16)), (28, 30, 36))
        for k, cid in enumerate(ids):
            spec, tex = creature(cid)
            bx = creature_boxes(spec)
            sc = auto_scale(bx, 60)
            for j, yaw in enumerate((math.pi * 0.25, math.pi / 2)):
                im = render(bx, tex, yaw, scale=sc, size=(cell, cell))
                sheet.paste(im, ((k % cols) * cell * 2 + j * cell, (k // cols) * (cell + 16) + 16))
            ImageDraw.Draw(sheet).text(((k % cols) * cell * 2 + 4, (k // cols) * (cell + 16) + 2), cid, fill=(220, 220, 220))
        sheet.save(out)
    elif mode == "views":
        # several creatures, each as a row of front / 3/4 / side / rear views
        out = sys.argv[2]
        rows = []
        for cid in sys.argv[3:]:
            spec, tex = creature(cid)
            rows.append(views(creature_boxes(spec), tex, cid, cell=240))
        sheet = Image.new("RGB", (rows[0].width, sum(r.height for r in rows)), (28, 30, 36))
        y = 0
        for r in rows:
            sheet.paste(r, (0, y))
            y += r.height
        sheet.save(out)
    elif mode == "boss":
        bid = sys.argv[2]
        spec = json.load(open(os.path.join(ROOT, "bosses", bid + ".json")))
        tex = Image.open(os.path.join(ROOT, "textures", "entity", "boss", bid + ".png")).convert("RGBA")
        bx = boss_boxes(spec)
        sc = spec.get("scale", 1.0)
        for b in bx:  # apply render scale around the ground pivot
            f = b.world
            b.world = (lambda f: lambda v: [q * sc if i != 1 else 24 + (q - 24) * sc for i, q in enumerate(f(v))])(f)
        views(bx, tex, bid).save(sys.argv[3])


if __name__ == "__main__":
    main()
