"""Stage 11 guardian model refinement: adds anatomical and costume detail to the Stage 10 boss rigs without renaming or
moving any animated part (every pose, sway and stride keeps working; hit timing is server-side and unaffected).

Generic rules read the existing boxes (y up, relative pivots): shoulder guards on arms, feet/paws/hooves under legs,
dorsal plates on body segments, a brow ridge over heads. Species-specific detail is added per guardian below.
Previews of guardians are private (docs/private/previews/).
"""


def first_box(p):
    return p["boxes"][0] if p["boxes"] else None


def add(r, name, parent, pivot, boxes, mat, rot=(0, 0, 0), glow=False):
    if any(p["name"] == name for p in r.parts):
        return
    r.parts.append({"name": name, "parent": parent, "pivot": list(pivot), "rot": list(rot), "boxes": [list(b) for b in boxes], "mat": mat,
                    "glow": glow})


def generic(r, accent):
    names = {p["name"]: p for p in r.parts}
    for p in list(r.parts):
        n, b = p["name"], first_box(p)
        if b is None or p.get("glow") or any(q["name"].startswith(n + "_") for q in r.parts):
            continue
        x, y, z, w, h, d = b[:6]
        if n in ("arm_l", "arm_r"):
            add(r, n + "_guard", n, (0, 0, 0), [(x - 1.2, y + h - 3.5, z - 1.2, w + 2.4, 3.5, d + 2.4)], accent)
        elif n.startswith("leg") and h > 6 and p["parent"] != "root" or n in ("leg_l", "leg_r"):
            add(r, n + "_foot", n, (0, 0, 0), [(x - 1, y - 0.5, z - 2.5, w + 2, 2.2, d + 3.5)], p["mat"] if accent == p["mat"] else "f")
        elif n.startswith("seg") and w >= 4:
            add(r, n + "_plate", n, (0, 0, 0), [(x + w * 0.25, y + h - 0.2, z + d * 0.15, w * 0.5, 1.6, d * 0.7)], accent)
        elif n == "head" and w >= 8 and h >= 6:
            add(r, "head_brow", n, (0, 0, 0), [(x - 0.5, y + h * 0.62, z - 0.6, w + 1, 1.6, 2.2)], p["mat"])
    _ = names


def refine(r):
    pal = r.pal
    pal.setdefault("f", tuple(max(0, int(c * 0.55)) for c in pal["a"]))      # feet / hooves / dark keratin
    pal.setdefault("w", (236, 230, 214))                                     # teeth / claws
    accent = "c" if "c" in pal else "b"
    bid = r.bid
    if bid == "mossback":
        pal["n"] = (60, 46, 40)
        add(r, "snout_disc", "head", (0, -4, -17), [(-4, -3, -1.2, 8, 6, 1.4)], "n")
        add(r, "jaw", "head", (0, -8, -6), [(-6, -2.5, -10.5, 12, 2.5, 10.5)], "a")
        add(r, "ear_l", "head", (-7, 4, -2), [(-1.5, 0, -1, 3, 4.5, 2)], "a", rot=(0.2, 0, 0.5))
        add(r, "ear_r", "head", (7, 4, -2), [(-1.5, 0, -1, 3, 4.5, 2)], "a", rot=(0.2, 0, -0.5))
        add(r, "mane", "body", (0, 6, -14), [(-12, -7, -3, 24, 11, 8)], "b")
        add(r, "fringe", "body", (0, -9, 0), [(-13.5, -3, -17, 27, 3.2, 34)], "b")
        add(r, "pauldron_l", "body", (-13, 4, -10), [(-1.5, -6, -6, 2.5, 10, 12)], "c")
        add(r, "pauldron_r", "body", (13, 4, -10), [(-1, -6, -6, 2.5, 10, 12)], "c")
        add(r, "tail_tuft", "tail", (0, 0, 11), [(-2.5, -2.5, 0, 5, 5, 4)], "b")
        for i in range(4):
            add(r, f"leg{i}_hoof", f"leg{i}", (0, -15, 0), [(-4, -0.5, -4.5, 8, 2.5, 8.5)], "f")
            add(r, f"leg{i}_fetlock", f"leg{i}", (0, -12, 0), [(-4, 0, -4, 8, 3, 8)], "b")
    elif bid == "rimefang":
        pal["n"] = (40, 44, 54)
        add(r, "nose", "head", (0, -1, -15), [(-1.5, -1, -0.8, 3, 2, 1)], "n")
        add(r, "brow", "head", (0, 3, -9), [(-6.5, 0, -1, 13, 1.5, 2.2)], "a")
        add(r, "cheek_l", "head", (-6, -2, -4), [(-1.5, -3, -3, 2, 6, 6)], "a")
        add(r, "cheek_r", "head", (6, -2, -4), [(-0.5, -3, -3, 2, 6, 6)], "a")
        add(r, "fang_l", "jaw", (-2, 0, -5.5), [(-0.4, 0, -0.4, 0.8, 2.2, 0.8)], "w")
        add(r, "fang_r", "jaw", (2, 0, -5.5), [(-0.4, 0, -0.4, 0.8, 2.2, 0.8)], "w")
        add(r, "shoulder_l", "body", (-8, -2, -10), [(-2, -5, -4, 4, 9, 8)], "a")
        add(r, "shoulder_r", "body", (8, -2, -10), [(-2, -5, -4, 4, 9, 8)], "a")
        add(r, "haunch_l", "body", (-8, -1, 10), [(-2, -6, -5, 4, 10, 10)], "a")
        add(r, "haunch_r", "body", (8, -1, 10), [(-2, -6, -5, 4, 10, 10)], "a")
        add(r, "tail_brush", "tail", (0, 0, 6), [(-3.5, -3.5, 0, 7, 7, 10)], "a")
        add(r, "belly_fur", "body", (0, -7, 0), [(-7, -2.5, -12, 14, 2.6, 22)], "a")
        for i in range(4):
            add(r, f"leg{i}_paw", f"leg{i}", (0, -14, 0), [(-3.5, -0.5, -4.5, 7, 2.2, 7.5)], "b")
    elif bid == "cinderwurm":
        for i in range(6):
            add(r, f"seg{i}_spike", f"seg{i}", (0, 0, 0), [(-0.8, 5.5, 2, 1.6, 3.5, 2)], "c", rot=(-0.3, 0, 0), glow=False)
        add(r, "fang_l", "jaw_l", (0, 0, -6), [(-0.5, -0.5, -0.5, 1, 3, 1)], "w")
        add(r, "fang_r", "jaw_r", (0, 0, -6), [(-0.5, -0.5, -0.5, 1, 3, 1)], "w")
    elif bid == "tidemother":
        for i in range(0, 6, 2):
            add(r, f"seg{i}_fin_l", f"seg{i}", (-5, 0, 2), [(-5, -0.4, 0, 5, 0.8, 5)], "b", rot=(0, 0.4, -0.3))
            add(r, f"seg{i}_fin_r", f"seg{i}", (5, 0, 2), [(0, -0.4, 0, 5, 0.8, 5)], "b", rot=(0, -0.4, 0.3))
        add(r, "whisker_l", "head", (-5, -3, -10), [(-6, -0.3, -0.3, 6, 0.6, 0.6)], "c", rot=(0, 0.3, 0.3))
        add(r, "whisker_r", "head", (5, -3, -10), [(0, -0.3, -0.3, 6, 0.6, 0.6)], "c", rot=(0, -0.3, -0.3))
    elif bid == "choirwarden":
        add(r, "plume", "head", (0, 8, 0), [(-0.8, 0, -3, 1.6, 4, 7)], "b", rot=(-0.3, 0, 0))
        add(r, "tabard", "torso", (0, -4, -4), [(-4, -10, -0.6, 8, 12, 1.2)], "b")
        add(r, "belt", "torso", (0, -3, 0), [(-6, -1, -4, 12, 2, 8)], "c")
    elif bid == "lamplighter":
        add(r, "hem", "robe", (0, 0, 0), [(-8, -1, -8, 16, 2, 16)], "b")
        add(r, "mantle", "robe", (0, 22, 0), [(-8, -4, -5, 16, 5, 10)], "b")
    elif bid == "silentwarden":
        add(r, "spine_ridge", "torso", (0, 6, 4), [(-1, -10, 0, 2, 14, 2)], "c", glow=True)
    generic(r, accent)
    return r
