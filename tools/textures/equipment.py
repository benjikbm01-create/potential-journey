"""Stage 11.5 equipment painter: weapons, tools and armour.

Weapons/tools: one hand-drawn silhouette per type (role letters), coloured by the material: a metal ramp for the head
(1 darkest .. 5 brightest), fittings (g/G), grip (h/H), wood (T), and a material signature on the fuller/edge (f/F):
ember glow, frost edge, spark, star glint, violet glow, patina, pearl sheen...
Armour: four construction families (hide, plate, scale, robe) drawn piece by piece, with set-specific ornaments, plus
matching 64x32 model layers.
"""
from PIL import Image

from texlib import hx, ramp, shade, h01, new, put, sprite

# ------------------------------------------------------------------------------------------- materials

MAT = {
    #           metal         fittings      grip          signature (f, F)                      trait
    "copper": (hx("c8743a"), hx("6a4a2a"), hx("4a3020"), (hx("5aa88a"), hx("8ad4b0")), "patina"),
    "iron": (hx("9a9a9e"), hx("5a4a3a"), hx("3a2a1e"), (hx("7a7a80"), hx("b8b8be")), None),
    "steel": (hx("b4bcc8"), hx("4a4a58"), hx("2a2430"), (hx("8a94a4"), hx("e8eef6")), None),
    "diamond": (hx("6ad8d0"), hx("c8a04a"), hx("3a2a1e"), (hx("b8fff4"), hx("ffffff")), "facet"),
    "embersteel": (hx("5a5262"), hx("8a2a22"), hx("2e1e16"), (hx("ff8a2a"), hx("ffd06a")), "glow"),
    "rimesteel": (hx("8ab4d8"), hx("c8b088"), hx("3a2a1e"), (hx("d8f4ff"), hx("ffffff")), "frost"),
    "galvanic": (hx("c87a3a"), hx("3a6a8a"), hx("2a2a30"), (hx("6ad8ff"), hx("d8f8ff")), "spark"),
    "starsilver": (hx("c8d4ec"), hx("4a5a9a"), hx("2a2a48"), (hx("fff6c8"), hx("ffffff")), "star"),
    "umbralite": (hx("5a3a88"), hx("2a1a3a"), hx("1a1420"), (hx("b87aff"), hx("e8c8ff")), "glow"),
    "malachite": (hx("3a9a6a"), hx("6a5a3a"), hx("3a2a1e"), (hx("8ad8a8"), hx("d8ffe8")), "band"),
    "nacre": (hx("d8d4e4"), hx("4a8a9a"), hx("2a3a44"), (hx("ffc8e8"), hx("c8fff0")), "sheen"),
    "fossil": (hx("d8cca8"), hx("6a4a2a"), hx("3a2a1e"), (hx("a89878"), hx("f4ecd8")), None),
}


def palette(mat):
    metal, fit, grip, (f, F), trait = MAT[mat]
    m = ramp(metal, 5, lo=0.5, hi=1.3)
    fr, gr, wood = ramp(fit, 4), ramp(grip, 3), ramp(hx("8a6a44"), 4)
    return {"o": shade(m[0], 0.45), "1": m[0], "2": m[1], "3": m[2], "4": m[3], "5": m[4], "f": f, "F": F,
            "g": fr[1], "G": fr[3], "h": gr[0], "H": gr[2], "p": fr[2], "T": wood[3], "w": wood[1], "W": wood[2]}


# ------------------------------------------------------------------------------------------- weapon / tool silhouettes

SIL = {
    "sword": ["..............oo", ".............o5o", "............o54o", "...........o543o", "..........o54F3o", ".........o54f3o.",
              "........o54f3o..", ".......o54f3o...", "..oo..o54f3o....", "..oGooo53o......", "...oGGg3o.......", "....oGgo........",
              "...oHoGGo.......", "..oHho.oGo......", ".opho...oo......", ".ooo............"],
    "greatsword": [".............ooo", "............o55o", "...........o554o", "..........o5543o", ".........o55F43o", "........o55f43o.",
                   ".......o55f43o..", "......o55f43o...", ".....o55f43o....", ".oo.o55f43o.....", ".oGoo5543o......", "..oGGg43o.......",
                   "..oHoGgGo.......", ".oHho.oGGo......", "opho....oo......", "ooo............."],
    "dagger": ["................", "................", "................", "...........ooo..", "..........o5Fo..", ".........o54fo..",
               "........o543o...", ".......o543o....", "...oo.o5f3o.....", "...oGoo43o......", "....oGG3o.......", ".....oGo........",
               "....oHoGGo......", "...oHho.oo......", "..opho..........", "..ooo..........."],
    "spear": ["............ooo.", "...........o55o.", "..........o54Fo.", ".........o54f3o.", ".........o4f3o..", "........oGG2o...",
              ".......oTGgo....", "......oTwo......", ".....oTwo.......", "....oTwo........", "...oTwo.........", "..oTwo..........",
              ".oTwo...........", "oTwo............", "owo.............", ".o.............."],
    "battleaxe": ["........ooo.....", ".......o54Fo....", "......o5443o....", ".....o54433oo...", "......o4433oTo..", ".......o332oTwo.",
                  "........oooTwo..", "......oo2gTo....", ".....o5432o.....", "....o5432o......", "...oTo32o.......", "..oTwoo.........",
                  ".oTwo...........", "oTwo............", "owo.............", ".o.............."],
    "axe": ["................", ".......oo.......", "......o54o......", ".....o5F43oo....", ".....o5443oTo...", "......o43oTwo...",
            ".......ooTwo....", "........oTwo....", ".......oTwo.....", "......oTwo......", ".....oTwo.......", "....oTwo........",
            "...oTwo.........", "..oTwo..........", "..owo...........", "...o............"],
    "pickaxe": ["....ooooo.......", "...o5544Fo......", "..o5oooo43o.....", ".o4o...oTo3o....", ".oo...oTwoo3o...", "......oTwo..o2o..",
                ".....oTwo....oo.", "....oTwo........", "...oTwo.........", "..oTwo..........", ".oTwo...........", ".owo............",
                "..o.............", "................", "................", "................"],
    "shovel": ["..........ooo...", ".........o54Fo..", "........o5443o..", "........o4432o..", ".........o32o...", "........oTGo....",
               ".......oTwo.....", "......oTwo......", ".....oTwo.......", "....oTwo........", "...oTwo.........", "..oTwo..........",
               ".oHHo...........", "oHho............", ".oo.............", "................"],
    "hoe": ["................", "......oooooo....", ".....o5544F3o...", "......ooooTo3o..", ".........oTwoo..", "........oTwo....",
            ".......oTwo.....", "......oTwo......", ".....oTwo.......", "....oTwo........", "...oTwo.........", "..oTwo..........",
            ".oTwo...........", ".owo............", "..o.............", "................"],
    "warhammer": ["........oooo....", ".......o55F4o...", "......o54443o...", ".....o54433o2o..", ".....o4433oo3o..", "......o3oGo2o...",
                  ".......ooGgoo...", "........oGgo....", ".......oTGo.....", "......oTwo......", ".....oTwo.......", "....oTwo........",
                  "...oTwo.........", "..oGgo..........", ".oGgo...........", ".ooo............"],
    "hammer": ["................", "......oooooo....", ".....o5554F4o...", ".....o4444333o..", "......o33322o...", ".......oooTo....",
               "........oTwo....", "........oTwo....", ".......oTwo.....", "......oTwo......", ".....oTwo.......", "....oTwo........",
               "...oHHo.........", "..oHho..........", "..oo............", "................"],
    "mace": [".........o.o....", "........o5o5o...", ".......o54F43o..", "......o5o44o3o..", ".......o4332o...", "........o3o2o...",
             ".......oGo.o....", "......oTGo......", ".....oTwo.......", "....oTwo........", "...oTwo.........", "..oHHo..........",
             ".oHho...........", ".opo............", "..o.............", "................"],
    "sickle": ["......oooo......", ".....o554Fo.....", "....o5oooo4o....", "....o4o...o3o...", "....oo.....o2o..", "...........oo...",
               "..........oTo...", ".........oTwo...", "........oTwo....", ".......oTwo.....", "......oHHo......", ".....oHho.......",
               "....opo.........", ".....o..........", "................", "................"],
}


def weapon(kind, mat):
    pal = palette(mat)
    img = sprite(SIL[kind], pal)
    trait = MAT[mat][4]
    # material signatures drawn on the head (deliberate single texels, not noise)
    marks = {
        "patina": [(10, 5, pal["f"]), (8, 8, pal["f"])],
        "frost": [(12, 2, pal["F"]), (13, 1, pal["F"])],
        "spark": [(11, 4, pal["F"])],
        "star": [(12, 3, pal["F"])],
        "facet": [(11, 4, pal["F"]), (9, 6, pal["f"])],
        "band": [(10, 6, pal["f"]), (9, 7, pal["2"])],
        "sheen": [(11, 4, pal["f"]), (9, 6, pal["F"])],
    }.get(trait, [])
    for x, y, c in marks:
        if img.getpixel((x, y))[3] and img.getpixel((x, y))[:3] != pal["o"]:
            put(img, x, y, c)
    return img


# ------------------------------------------------------------------------------------------- armour

SETS = {
    #            family    main          trim          strap         accent
    "fur": ("hide", hx("8a6a4a"), hx("d8c8a8"), hx("4a3220"), hx("c84a3a")),
    "rimesteel": ("horned", hx("7aa4c8"), hx("e4f4ff"), hx("2a3a52"), hx("4ad8f0")),
    "embersteel": ("crested", hx("4a4450"), hx("c8862a"), hx("221a1e"), hx("ff7a24")),
    "copper": ("plate", hx("c8743a"), hx("6ac8a0"), hx("4a3020"), hx("e8b070")),
    "steel": ("plate", hx("a8b0bc"), hx("e8eef6"), hx("2a2430"), hx("8a2a2a")),
    "starsilver": ("plate", hx("c4d0ea"), hx("fff6c8"), hx("2a2a48"), hx("6a8aff")),
    "umbralite": ("crested", hx("4a3470"), hx("b87aff"), hx("1a1420"), hx("e0b0ff")),
    "galvanic": ("plate", hx("c87a3a"), hx("6ad8ff"), hx("2a2a30"), hx("d8f8ff")),
    "scale": ("scale", hx("4a8a7a"), hx("c8b088"), hx("2a3a30"), hx("8ad8b8")),
    "nacre": ("scale", hx("a8a0bc"), hx("8ad0e0"), hx("2a3a44"), hx("ffc8e8")),
    "shell": ("scale", hx("b0906a"), hx("e8d8b8"), hx("3a2a1e"), hx("7a5a3a")),
    "silk": ("robe", hx("c8c0d8"), hx("8a6ad0"), hx("4a3a5a"), hx("f0e8ff")),
    "fossil": ("bone", hx("d8cca8"), hx("8a6a48"), hx("3a2a1e"), hx("f4ecd8")),
}

# hand-drawn piece silhouettes per family; role letters: o outline, 1-5 main ramp, t/T trim, s/S strap, a/A accent
ICON = {
    "plate": {
        "helmet": ["................", ".....oooooo.....", "....o455554o....", "...o45555554o...", "...o4TTTTTT4o...", "...o4oooooo4o...",
                   "...o4oAooAo4o...", "...o3oooooo3o...", "...o34o..o43o...", "...o3To..oT3o...", "....oo....oo....", "................",
                   "................", "................", "................", "................"],
        "chestplate": ["................", "..ooo......ooo..", ".oT5Toooooo T5To", ".o4555T55T5554o.", ".o3445544554 3o.", "..o34455544433o.",
                       "...o34aAA4433o..", "...o344aA4443o..", "...oTTTTTTTTo...", "...o34444443o...", "...o34444443o...", "...o23333332o...",
                       "...oTTTTTTTTo...", "...oooooooooo...", "................", "................"],
        "leggings": ["................", "...oooooooooo...", "...oTTTAATTTo...", "...o45544554o...", "...o45o..o54o...", "...o34o..o43o...",
                     "...oTTo..oTTo...", "...o45o..o54o...", "...o34o..o43o...", "...o34o..o43o...", "...o23o..o32o...", "...oTTo..oTTo...",
                     "...oooo..oooo...", "................", "................", "................"],
        "boots": ["................", "................", "................", "................", "................", "................",
                  "...oTTo..oTTo...", "...o54o..o45o...", "...o43o..o34o...", "..o4334oo4334o..", "..o54433o44345o.", ".oT33333oT33333o",
                  ".ooooooo.ooooooo", "................", "................", "................"],
    },
    "scale": {
        "helmet": ["................", ".....oooooo.....", "....o545454o....", "...o45454545o...", "...o54545454o...", "...oTTTTTTTTo...",
                   "...o4o3443o4o...", "...o5o....o5o...", "...o4o....o4o...", "...o54o..o45o...", "....oo....oo....", "................",
                   "................", "................", "................", "................"],
        "chestplate": ["................", "..oTTo....oTTo..", ".oT45Toooo T54To".replace(" ", "o")[:16], ".o4545454545454o", ".o5454545454545o",
                       "..o45454545454o.", "...o454aA4545o..", "...o5454545454o.", "...oTTTTTTTTo...", "...o45454545o...", "...o54545454o...",
                       "...o45454545o...", "...oTTTTTTTTo...", "...oooooooooo...", "................", "................"],
        "leggings": ["................", "...oooooooooo...", "...oSSSSSSSSo...", "...o45454545o...", "...o54o..o45o...", "...o45o..o54o...",
                     "...o54o..o45o...", "...o45o..o54o...", "...o54o..o45o...", "...o45o..o54o...", "...o34o..o43o...", "...oTTo..oTTo...",
                     "...oooo..oooo...", "................", "................", "................"],
        "boots": ["................", "................", "................", "................", "................", "................",
                  "...oTTo..oTTo...", "...o54o..o45o...", "...o45o..o54o...", "..o4545oo5454o..", "..o54545o54545o.", "..oSSSSSoSSSSSo.",
                  "..ooooooooooooo.", "................", "................", "................"],
    },
    "robe": {
        "helmet": ["................", "......oooo......", ".....o4554o.....", "....o455554o....", "...o45555554o...", "...o4TTTTTT4o...",
                   "..o45o....o54o..", "..o44o....o44o..", "..o34o....o43o..", "..o3TTo..oTT3o..", "...ooo....ooo...", "................",
                   "................", "................", "................", "................"],
        "chestplate": ["................", "..ooo......ooo..", ".o455oooooo554o.", ".o4555TTTT5554o.", ".o3455aAAa5543o.", "..o34555555543o.",
                       "...o4555555544o.", "...o3455555543o.", "...oSSSSSSSSSo..", "...o445555544o..", "...o3455555543o.", "...o3445555443o.",
                       "...oTTTTTTTTTTo.", "...oooooooooooo.", "................", "................"],
        "leggings": ["................", "...oooooooooo...", "...oSSSSSSSSo...", "...o45555554o...", "...o455oo554o...", "...o45o..o54o...",
                     "...o45o..o54o...", "...o45o..o54o...", "...o45o..o54o...", "...o34o..o43o...", "...o34o..o43o...", "...oTTo..oTTo...",
                     "...oooo..oooo...", "................", "................", "................"],
        "boots": ["................", "................", "................", "................", "................", "................",
                  "................", "...oooo..oooo...", "...o45o..o54o...", "..o4554oo4554o..", "..o45555o55554o.", "..oSSSSSoSSSSSo.",
                  "..ooooooooooooo.", "................", "................", "................"],
    },
    "bone": {
        "helmet": ["................", "...o........o...", "..oTo......oTo..", "..o5To....oT5o..", "...o5oooooo5o...", "...o45555554o...",
                   "...o5o5oo5o5o...", "...o4oooooo4o...", "...o45o..o54o...", "...oTTo..oTTo...", "....oo....oo....", "................",
                   "................", "................", "................", "................"],
        "chestplate": ["................", "..ooo......ooo..", ".o5To.oooo.oT5o.", ".o45ooT55Too54o.", ".o5555o55o5555o.", "..ooooo55ooooo..",
                       "..o5555o55o5555o", "..ooooo55ooooo..", "..o5555o55o5555o", "...oooo55oooo...", "....oTT55TTo....", "....o445544o....",
                       "....oooooooo....", "................", "................", "................"],
        "leggings": ["................", "...oooooooooo...", "...oSSSSSSSSo...", "...o55o..o55o...", "...oTTo..oTTo...", "...o55o..o55o...",
                     "...o44o..o44o...", "...oTTo..oTTo...", "...o55o..o55o...", "...o44o..o44o...", "...oTTo..oTTo...", "...o55o..o55o...",
                     "...oooo..oooo...", "................", "................", "................"],
        "boots": ["................", "................", "................", "................", "................", "................",
                  "...oTTo..oTTo...", "...o55o..o55o...", "...oSSo..oSSo...", "..o4555oo5554o..", "..o55555o55555o.", "..oT5T5ToT5T5To.",
                  "..ooooooooooooo.", "................", "................", "................"],
    },
}


def armour_palette(main, trim, strap, accent):
    m, t, st, ac = ramp(main, 6), ramp(trim, 5), ramp(strap, 4), ramp(accent, 4)
    return {"o": shade(st[0], 0.7), "1": m[1], "2": m[2], "3": m[3], "4": m[4], "5": m[5], "t": t[2], "T": t[4], "s": st[1], "S": st[2],
            "a": ac[1], "A": ac[3]}, (m, t, st, ac)


def armour_icons(name, sample_icons):
    fam, main, trim, strap, accent = SETS[name]
    pal, ramps_ = armour_palette(main, trim, strap, accent)
    if fam in ("hide", "horned", "crested"):
        base = {"hide": "fur", "horned": "rimesteel", "crested": "embersteel"}[fam]
        icons = sample_icons[base]
    else:
        icons = ICON[fam]
    out = {}
    for piece, rows in icons.items():
        rows = [(r + "................")[:16] for r in rows]
        img = sprite(rows, pal)
        # set ornaments: one deliberate detail per set so plate sets differ beyond colour
        orn = {"copper": [(7, 3, "A"), (8, 3, "A")], "starsilver": [(7, 2, "A"), (8, 2, "T")], "galvanic": [(5, 4, "A"), (10, 4, "A")],
               "steel": [(7, 6, "A")], "nacre": [(7, 5, "A"), (8, 6, "A")], "shell": [(6, 4, "A"), (9, 4, "A")], "umbralite": [(7, 2, "A")]}.get(name, [])
        for x, y, k in orn:
            if img.getpixel((x, y))[3]:
                put(img, x, y, pal[k])
        out[piece] = img
    return out, ramps_


def armour_layers(name, painter):
    fam, main, trim, strap, accent = SETS[name]
    _, (m, t, st, ac) = armour_palette(main, trim, strap, accent)
    style = {"hide": "fur", "horned": "rimesteel", "crested": "embersteel", "plate": "rimesteel", "scale": "scale", "robe": "fur",
             "bone": "bone"}[fam]
    if style in ("fur", "rimesteel", "embersteel"):
        return painter(style, m, t, st, ac)
    l1, l2 = Image.new("RGBA", (64, 32), (0, 0, 0, 0)), Image.new("RGBA", (64, 32), (0, 0, 0, 0))

    def box(img, u, v, w, h, d, kind):
        faces = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
                 "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}
        for f, (x0, y0, fw, fh) in faces.items():
            for j in range(fh):
                for i in range(fw):
                    if style == "scale":
                        c = m[4] if (i + (j // 2) % 2) % 2 == 0 else m[3]
                        if j % 2 == 1:
                            c = m[2] if (i + (j // 2) % 2) % 2 == 0 else c
                    else:  # bone: ribs / plates of bone on dark leather
                        c = st[2] if j % 3 == 2 else (m[4] if i % 4 != 3 else m[2])
                    if j == 0:
                        c = t[3]
                    if j == fh - 1:
                        c = t[1]
                    if kind == "body" and f == "front" and abs(i - fw / 2 + 0.5) < 1 and 2 <= j <= 4:
                        c = ac[3]
                    img.putpixel((x0 + i, y0 + j), (*c[:3], 255))

    box(l1, 0, 0, 8, 8, 8, "head")
    box(l1, 16, 16, 8, 12, 4, "body")
    box(l1, 40, 16, 4, 12, 4, "arm")
    box(l1, 0, 16, 4, 12, 4, "boot")
    box(l2, 0, 16, 4, 12, 4, "leg")
    box(l2, 16, 16, 8, 12, 4, "waist")
    return l1, l2
