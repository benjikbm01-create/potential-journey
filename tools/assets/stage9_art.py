"""
Stage 9 pixel art and models: puzzle mechanisms, seals, traps, reward containers, inscriptions, relic décor, the
warding bell, exploration items and artifacts. Drawn from shapes with directional shading (light upper-left), not
noise; the six signs (spiral, eye, wave, flame, star, key) are distinct silhouettes so no puzzle depends on colour.
"""
import json
import math
import os

import art
import stage8_art as S8

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "underdeep")
NS = "underdeep"
T = lambda n: f"{NS}:block/{n}"  # noqa: E731

TECHNICAL_BLOCKS = ["rune_dial", "glyph_tile", "rune_plate", "heavy_plate", "ancient_brazier", "offering_socket", "resonance_chime", "ancient_lever",
                    "stone_basin", "light_mirror", "light_lens", "light_receiver", "memory_pillar", "memory_stone", "keystone", "mosaic_frame",
                    "ancient_seal", "seal_bars", "dormant_engine", "counterweight", "key_lock", "trap_plate", "dart_vent", "flame_vent", "steam_vent",
                    "spore_vent", "frost_vent", "crumbling_floor", "loose_ceiling", "alarm_gong", "ancient_lamp", "expedition_cache", "relic_pedestal",
                    "key_pedestal", "inscription_stone", "vault_key", "lore_fragment"]

STONE = (84, 84, 94)
STONE_L = (112, 112, 122)
STONE_D = (56, 56, 64)
GOLD = (226, 186, 84)
GLYPH = (130, 214, 232)


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=1)
        f.write("\n")


def tex(name, img):
    p = os.path.join(ASSETS, "textures", "block", name + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def item_tex(name, img):
    p = os.path.join(ASSETS, "textures", "item", name + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def model(name, obj):
    wjson(os.path.join(ASSETS, "models", "block", name + ".json"), obj)


def state(name, variants):
    wjson(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})


def item_block(name, model_name=None):
    wjson(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": f"{NS}:block/{model_name or name}"})


def item_flat(name, texture=None, handheld=False):
    wjson(os.path.join(ASSETS, "models", "item", name + ".json"),
          {"parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated", "textures": {"layer0": texture or f"{NS}:item/{name}"}})


def cube(name, side, top=None, bottom=None, front=None):
    tx = {"particle": side, "north": front or side, "south": side, "east": side, "west": side, "up": top or side, "down": bottom or top or side}
    model(name, {"parent": "minecraft:block/cube", "textures": tx})


def el(frm, to, faces, rot=None):
    e = {"from": frm, "to": to, "faces": {k: {"texture": v} for k, v in faces.items()}}
    if rot:
        e["rotation"] = rot
    return e


def all6(t):
    return {f: t for f in ("north", "south", "east", "west", "up", "down")}


def custom(name, elements, textures, ao=True):
    obj = {"parent": "minecraft:block/block", "textures": textures, "elements": elements}
    if not ao:
        obj["ambientocclusion"] = False
    model(name, obj)


FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


# ============================================================================================ base textures
def stone_tile(base=STONE, seams=True, k=0):
    img = art.new()
    for x in range(16):
        for y in range(16):
            c = base
            if seams and (y in (0, 8) or (x == 0 and y < 8) or (x == 8 and y >= 8)):
                c = art.shade(base, 0.72)
            elif (x + y + k) % 11 == 0:
                c = art.shade(base, 0.9)
            elif x in (1, 9) and y not in (0, 8) or y in (1, 9):
                c = art.shade(base, 1.12)
            art.px(img, x, y, c)
    return img


def frame(base, border):
    img = art.new()
    for x in range(16):
        for y in range(16):
            c = base
            if x in (0, 15) or y in (0, 15):
                c = border
            elif x in (1, 14) or y in (1, 14):
                c = art.shade(border, 1.25)
            art.px(img, x, y, c)
    return img


def glyph(img, sym, col, ox=0, oy=0):
    """Draws one of the six signs in an 8x8 box at (4+ox, 4+oy). Shapes differ, not just colours."""
    def p(x, y):
        art.px(img, 4 + ox + x, 4 + oy + y, col)
    if sym == 0:  # spiral
        for (x, y) in [(3, 3), (4, 3), (4, 4), (3, 5), (2, 5), (1, 4), (1, 3), (1, 2), (2, 1), (3, 1), (4, 1), (5, 1), (6, 2), (6, 3), (6, 4), (6, 5), (5, 6),
                       (4, 7), (3, 7), (2, 7), (1, 7), (0, 6)]:
            p(x, y)
    elif sym == 1:  # eye
        for x in range(1, 7):
            p(x, 2 if 2 <= x <= 5 else 3)
            p(x, 6 if 2 <= x <= 5 else 5)
        p(0, 4)
        p(7, 4)
        for (x, y) in [(3, 3), (4, 3), (3, 4), (4, 4), (3, 5), (4, 5)]:
            p(x, y)
    elif sym == 2:  # wave
        for x in range(8):
            p(x, 2 + int(round(math.sin(x * 0.9) * 1.5)) + 1)
            p(x, 5 + int(round(math.sin(x * 0.9) * 1.5)) + 1)
    elif sym == 3:  # flame
        for (x, y) in [(3, 0), (3, 1), (4, 1), (2, 2), (3, 2), (4, 2), (2, 3), (4, 3), (5, 3), (1, 4), (2, 4), (5, 4), (1, 5), (5, 5), (6, 5), (1, 6), (2, 6),
                       (5, 6), (2, 7), (3, 7), (4, 7), (5, 7), (3, 4), (3, 5), (4, 5), (3, 6), (4, 6)]:
            p(x, y)
    elif sym == 4:  # star
        for i in range(8):
            p(i, 4)
            p(3, i)
            p(4, i)
        for (x, y) in [(1, 1), (6, 1), (1, 7), (6, 7), (2, 2), (5, 2), (2, 6), (5, 6)]:
            p(x, y)
    else:  # key
        for (x, y) in [(1, 1), (2, 1), (3, 1), (1, 2), (3, 2), (1, 3), (2, 3), (3, 3)]:
            p(x, y)
        for i in range(2, 8):
            p(i, 2) if i > 3 else None
        for (x, y) in [(4, 2), (5, 2), (6, 2), (7, 2), (6, 3), (6, 4), (7, 4)]:
            p(x, y)


def notches(img, n, col):
    for i in range(n):
        x = 4 + i * 2 + (4 - n)
        for y in range(6, 10):
            art.px(img, x, y, col)


# ============================================================================================ block assets
def mechanisms():
    tex("ancient_side", stone_tile())
    tex("ancient_top", stone_tile(art.shade(STONE, 1.08), True, 3))
    side = T("ancient_side")
    # dials (front shows the sign) and glyph tiles (clue mural)
    for s in range(6):
        img = frame(art.shade(STONE, 0.9), STONE_D)
        art.disc(img, 8, 8, 6.2, art.shade(STONE, 1.15), art.shade(STONE, 0.8))
        glyph(img, s, GLYPH)
        tex(f"rune_dial_{s}", img)
        cube(f"rune_dial_{s}", side, T("ancient_top"), front=T(f"rune_dial_{s}"))
        img = frame((150, 134, 104), (98, 84, 60))
        glyph(img, s, (64, 40, 24))
        tex(f"glyph_tile_{s}", img)
        cube(f"glyph_tile_{s}", T("ancient_side"), T("ancient_top"), front=T(f"glyph_tile_{s}"))
        img = frame(art.shade(STONE, 0.95), STONE_D)
        glyph(img, s, (200, 200, 210))
        tex(f"memory_stone_{s}", img)
        img2 = frame((70, 90, 110), (40, 60, 80))
        glyph(img2, s, (250, 250, 200))
        tex(f"memory_stone_{s}_lit", img2)
        cube(f"memory_stone_{s}", T(f"memory_stone_{s}"))
        cube(f"memory_stone_{s}_lit", T(f"memory_stone_{s}_lit"))
    state("rune_dial", {f"facing={f},symbol={s}": {"model": f"{NS}:block/rune_dial_{s}", **({"y": r} if r else {})} for f, r in FACINGS for s in range(6)})
    state("glyph_tile", {f"facing={f},symbol={s}": {"model": f"{NS}:block/glyph_tile_{s}", **({"y": r} if r else {})} for f, r in FACINGS for s in range(6)})
    state("memory_stone", {f"lit={l},symbol={s}": {"model": f"{NS}:block/memory_stone_{s}{'_lit' if l == 'true' else ''}"} for l in ("false", "true")
                           for s in range(6)})
    item_block("rune_dial", "rune_dial_0")
    item_block("glyph_tile", "glyph_tile_0")
    item_block("memory_stone", "memory_stone_0")
    # plates
    for kind, base in (("rune_plate", (120, 116, 108)), ("heavy_plate", (96, 92, 100))):
        for n in range(5):
            img = frame(base, art.shade(base, 0.7))
            if kind == "heavy_plate":
                for x in range(3, 13):
                    art.px(img, x, 3, art.shade(base, 0.6))
                    art.px(img, x, 12, art.shade(base, 0.6))
            notches(img, n, art.shade(base, 0.45))
            tex(f"{kind}_{n}", img)
            for pressed in (False, True):
                h = 0.5 if pressed else 1
                custom(f"{kind}_{n}{'_down' if pressed else ''}", [el([1, 0, 1], [15, h, 15], all6("#t"))], {"t": T(f"{kind}_{n}"), "particle": T(f"{kind}_{n}")})
        state(kind, {f"notches={n},powered={p}": {"model": f"{NS}:block/{kind}_{n}{'_down' if p == 'true' else ''}"} for n in range(5) for p in ("false", "true")})
        item_block(kind, f"{kind}_{1 if kind == 'rune_plate' else 0}")
    # trap plate: a worn tile with fine cracks (readable once you know); disarmed shows a wedge
    for d in (False, True):
        img = stone_tile((104, 100, 96), True, 1)
        for (x, y) in [(3, 4), (4, 5), (5, 5), (6, 6), (11, 10), (10, 11), (12, 9)]:
            art.px(img, x, y, (72, 68, 64))
        if d:
            for x in range(5, 11):
                art.px(img, x, 8, (150, 108, 66))
                art.px(img, x, 9, (112, 78, 46))
        tex(f"trap_plate{'_jammed' if d else ''}", img)
        custom(f"trap_plate{'_jammed' if d else ''}", [el([0, 0, 0], [16, 1, 16], all6("#t"))], {"t": T(f"trap_plate{'_jammed' if d else ''}"),
                                                                                                "particle": T(f"trap_plate{'_jammed' if d else ''}")})
    state("trap_plate", {f"disarmed={d},powered={p}": {"model": f"{NS}:block/trap_plate{'_jammed' if d == 'true' else ''}"} for d in ("false", "true")
                         for p in ("false", "true")})
    item_block("trap_plate")
    # brazier
    tex("brazier_bowl", frame((70, 66, 70), (44, 42, 46)))
    coals = art.new()
    for x in range(16):
        for y in range(16):
            art.px(coals, x, y, (38, 34, 34) if (x * 3 + y * 5) % 7 else (70, 54, 40))
    tex("brazier_coals", coals)
    fire = art.new()
    for x in range(16):
        for y in range(16):
            art.px(fire, x, y, (255, 200, 60) if (x + y * 3) % 5 else (255, 120, 30))
    tex("brazier_fire", fire)
    for t in range(5):
        img = frame((70, 66, 70), (44, 42, 46))
        notches(img, t, GOLD)
        tex(f"brazier_side_{t}", img)
        for lit in (False, True):
            name = f"ancient_brazier_{t}{'_lit' if lit else ''}"
            els = [el([3, 0, 3], [13, 9, 13], {"north": "#side", "south": "#side", "east": "#side", "west": "#side", "down": "#bowl", "up": "#bowl"}),
                   el([1, 9, 1], [15, 12, 15], {"north": "#bowl", "south": "#bowl", "east": "#bowl", "west": "#bowl", "down": "#bowl", "up": "#coals"})]
            if lit:
                els.append(el([4, 12, 4], [12, 15, 12], all6("#fire")))
            custom(name, els, {"side": T(f"brazier_side_{t}"), "bowl": T("brazier_bowl"), "coals": T("brazier_coals"), "fire": T("brazier_fire"),
                               "particle": T("brazier_bowl")}, ao=False)
    state("ancient_brazier", {f"lit={l},tally={t}": {"model": f"{NS}:block/ancient_brazier_{t}{'_lit' if l == 'true' else ''}"} for l in ("false", "true")
                              for t in range(5)})
    item_block("ancient_brazier", "ancient_brazier_0")
    # offering socket
    for full in (False, True):
        img = frame(art.shade(STONE, 1.05), STONE_D)
        art.disc(img, 8, 8, 4.5, (40, 36, 40) if not full else (230, 200, 110), (24, 22, 26) if not full else (170, 140, 60))
        tex(f"offering_socket{'_full' if full else ''}", img)
        cube(f"offering_socket{'_full' if full else ''}", side, T(f"offering_socket{'_full' if full else ''}"))
    state("offering_socket", {f"full={f}": {"model": f"{NS}:block/offering_socket{'_full' if f == 'true' else ''}"} for f in ("false", "true")})
    item_block("offering_socket")
    # chimes
    crys = art.new()
    for x in range(16):
        for y in range(16):
            art.px(crys, x, y, (200, 170, 240) if (x + y) % 4 else (240, 220, 255))
    tex("chime_crystal", crys)
    for s in range(3):
        h = (6, 11, 16)[s]
        custom(f"resonance_chime_{s}", [el([4, 0, 4], [12, 1, 12], all6("#base")), el([6, 1, 6], [10, h, 10], all6("#c"))],
               {"c": T("chime_crystal"), "base": T("ancient_top"), "particle": T("chime_crystal")}, ao=False)
    state("resonance_chime", {f"size={s}": {"model": f"{NS}:block/resonance_chime_{s}"} for s in range(3)})
    item_block("resonance_chime", "resonance_chime_1")
    # lever (vanilla geometry, ancient base)
    model("ancient_lever", {"parent": "minecraft:block/lever", "textures": {"particle": T("ancient_top"), "base": T("ancient_top"), "lever": "minecraft:block/lever"}})
    model("ancient_lever_on", {"parent": "minecraft:block/lever_on", "textures": {"particle": T("ancient_top"), "base": T("ancient_top"),
                                                                                    "lever": "minecraft:block/lever"}})
    # same geometry and rotations as the vanilla lever blockstate (powered=false uses the "_on" model there too)
    yfloor = {"north": 0, "east": 90, "south": 180, "west": 270}
    yceil = {"north": 180, "east": 270, "south": 0, "west": 90}
    v = {}
    for face, xr in (("floor", 0), ("wall", 90), ("ceiling", 180)):
        for f in yfloor:
            for p in ("false", "true"):
                yr = (yceil if face == "ceiling" else yfloor)[f]
                v[f"face={face},facing={f},powered={p}"] = {"model": f"{NS}:block/ancient_lever{'_on' if p == 'false' else ''}",
                                                            **({"x": xr} if xr else {}), **({"y": yr} if yr else {})}
    state("ancient_lever", v)
    item_flat("ancient_lever", "minecraft:block/lever")
    # basin
    water = art.new()
    for x in range(16):
        for y in range(16):
            art.px(water, x, y, (54, 96, 200) if (x + y) % 3 else (80, 130, 230))
    tex("basin_water", water)
    for lvl in range(4):
        els = [el([0, 0, 0], [16, 4, 16], all6("#s")), el([0, 4, 0], [2, 16, 16], all6("#s")), el([14, 4, 0], [16, 16, 16], all6("#s")),
               el([2, 4, 0], [14, 16, 2], all6("#s")), el([2, 4, 14], [14, 16, 16], all6("#s"))]
        if lvl:
            els.append(el([2, 4, 2], [14, 4 + lvl * 3.5, 14], {"up": "#w"}))
        custom(f"stone_basin_{lvl}", els, {"s": side, "w": T("basin_water"), "particle": side})
    state("stone_basin", {f"level={l}": {"model": f"{NS}:block/stone_basin_{l}"} for l in range(4)})
    item_block("stone_basin", "stone_basin_0")
    # mirror
    for slant in (False, True):
        img = frame((60, 56, 60), (36, 34, 38))
        for i in range(2, 14):
            x = i if not slant else 15 - i
            art.px(img, x, 15 - i, (230, 236, 250))
            art.px(img, min(14, x + 1), 15 - i, (170, 180, 200))
        tex(f"light_mirror{'_b' if slant else ''}", img)
        cube(f"light_mirror{'_b' if slant else ''}", T(f"light_mirror{'_b' if slant else ''}"))
    state("light_mirror", {f"slant={s}": {"model": f"{NS}:block/light_mirror{'_b' if s == 'true' else ''}"} for s in ("false", "true")})
    item_block("light_mirror")
    img = frame((70, 60, 40), GOLD)
    art.disc(img, 8, 8, 4, (255, 240, 170), (220, 190, 100))
    tex("light_lens", img)
    cube("light_lens", T("light_lens"))
    state("light_lens", {"": {"model": f"{NS}:block/light_lens"}})
    item_block("light_lens")
    for lit in (False, True):
        img = frame((50, 50, 60), (30, 30, 36))
        art.disc(img, 8, 8, 3.5, (255, 250, 200) if lit else (90, 90, 110), (220, 200, 120) if lit else (60, 60, 80))
        tex(f"light_receiver{'_lit' if lit else ''}", img)
        cube(f"light_receiver{'_lit' if lit else ''}", T(f"light_receiver{'_lit' if lit else ''}"))
    state("light_receiver", {f"lit={l}": {"model": f"{NS}:block/light_receiver{'_lit' if l == 'true' else ''}"} for l in ("false", "true")})
    item_block("light_receiver")
    # memory pillar, keystone, mosaic frame
    img = frame(art.shade(STONE, 1.1), STONE_D)
    for y in (3, 6, 9, 12):
        art.px(img, 7, y, GLYPH)
        art.px(img, 8, y, GLYPH)
    tex("memory_pillar", img)
    cube("memory_pillar", T("memory_pillar"), T("ancient_top"))
    state("memory_pillar", {"": {"model": f"{NS}:block/memory_pillar"}})
    item_block("memory_pillar")
    img = frame((120, 110, 90), (80, 70, 50))
    glyph(img, 5, GOLD)
    tex("keystone", img)
    cube("keystone", T("keystone"))
    state("keystone", {"": {"model": f"{NS}:block/keystone"}})
    item_block("keystone")
    img = frame((40, 36, 40), (90, 80, 70))
    tex("mosaic_frame", img)
    cube("mosaic_frame", T("mosaic_frame"))
    state("mosaic_frame", {"": {"model": f"{NS}:block/mosaic_frame"}})
    item_block("mosaic_frame")
    # seals
    img = frame((48, 46, 54), (30, 28, 34))
    art.disc(img, 8, 8, 6, (62, 58, 70), (40, 38, 46))
    for a in range(0, 360, 30):
        art.px(img, int(round(8 + math.cos(math.radians(a)) * 5)), int(round(8 + math.sin(math.radians(a)) * 5)), GOLD)
    art.disc(img, 8, 8, 1.5, GOLD)
    tex("ancient_seal", img)
    cube("ancient_seal", T("ancient_seal"))
    state("ancient_seal", {"": {"model": f"{NS}:block/ancient_seal"}})
    item_block("ancient_seal")
    lock = img.copy()
    for (x, y) in [(7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 10), (7, 10)]:
        art.px(lock, x, y, (16, 14, 18))
    tex("key_lock", lock)
    cube("key_lock", T("key_lock"))
    state("key_lock", {"": {"model": f"{NS}:block/key_lock"}})
    item_block("key_lock")
    bars = art.new()
    for x in range(16):
        for y in range(16):
            if x % 4 in (1, 2) or y in (0, 1, 14, 15):
                art.px(bars, x, y, (90, 90, 100) if x % 4 == 1 else (60, 60, 70))
    tex("seal_bars", bars)
    custom("seal_bars", [el([0, 0, 7], [16, 16, 9], all6("#b"))], {"b": T("seal_bars"), "particle": T("seal_bars")}, ao=False)
    custom("seal_bars_open", [el([0, 14, 7], [16, 16, 9], all6("#b"))], {"b": T("seal_bars"), "particle": T("seal_bars")}, ao=False)
    state("seal_bars", {f"open={o}": {"model": f"{NS}:block/seal_bars{'_open' if o == 'true' else ''}"} for o in ("false", "true")})
    item_block("seal_bars")
    # engine, counterweight
    for lit in (False, True):
        img = frame((150, 104, 70) if not lit else (220, 150, 90), (90, 60, 40))
        for x in range(4, 12):
            art.px(img, x, 8, (70, 140, 120) if not lit else (255, 230, 140))
        for y in range(4, 12):
            art.px(img, 8, y, (70, 140, 120) if not lit else (255, 230, 140))
        tex(f"dormant_engine{'_lit' if lit else ''}", img)
        cube(f"dormant_engine{'_lit' if lit else ''}", T(f"dormant_engine{'_lit' if lit else ''}"))
    state("dormant_engine", {f"lit={l}": {"model": f"{NS}:block/dormant_engine{'_lit' if l == 'true' else ''}"} for l in ("false", "true")})
    item_block("dormant_engine")
    img = frame((150, 108, 66), (100, 70, 40))
    for x in range(3, 13):
        art.px(img, x, 4, (180, 180, 190))
    for y in range(4, 12):
        art.px(img, 3, y, (180, 180, 190))
        art.px(img, 12, y, (180, 180, 190))
    tex("counterweight", img)
    cube("counterweight", T("counterweight"))
    state("counterweight", {"": {"model": f"{NS}:block/counterweight"}})
    item_block("counterweight")


def traps():
    side = T("ancient_side")
    for kind, col in (("dart_vent", (40, 36, 40)), ("flame_vent", (230, 110, 40)), ("steam_vent", (230, 230, 230)), ("spore_vent", (150, 190, 90)),
                      ("frost_vent", (150, 210, 250)), ("loose_ceiling", (130, 120, 110)), ("ancient_lamp", (255, 220, 140))):
        for lit in (False, True):
            img = stone_tile(STONE, True, 2)
            if kind == "dart_vent":
                for x in (4, 8, 12):
                    art.px(img, x, 7, col)
                    art.px(img, x, 8, col)
            elif kind == "loose_ceiling":
                for x in range(16):
                    for y in range(16):
                        if (x * 7 + y * 3) % 5 == 0:
                            art.px(img, x, y, art.shade(col, 0.8))
            elif kind == "ancient_lamp":
                img = frame((60, 56, 60), (36, 34, 38))
                art.disc(img, 8, 8, 4.5, col if lit else (70, 64, 56), art.shade(col, 0.8) if lit else (50, 46, 40))
            else:
                for x in range(3, 13):
                    for y in range(3, 13):
                        if x % 3 == 0 or y % 3 == 0:
                            art.px(img, x, y, (34, 32, 36))
                        elif lit:
                            art.px(img, x, y, col)
                        else:
                            art.px(img, x, y, art.shade(col, 0.45))
            name = f"{kind}{'_lit' if lit else ''}"
            tex(name, img)
            if kind == "dart_vent":
                cube(name, side, T("ancient_top"), front=T(name))
            elif kind in ("loose_ceiling", "ancient_lamp"):
                cube(name, T(name))
            else:
                cube(name, side, T(name))
        state(kind, {f"facing={f},lit={l}": {"model": f"{NS}:block/{kind}{'_lit' if l == 'true' else ''}", **({"y": r} if r and kind == 'dart_vent' else {})}
                     for f, r in FACINGS for l in ("false", "true")})
        item_block(kind)
    for p in (False, True):
        img = stone_tile((120, 112, 104), True, 4)
        cracks = [(2, 3), (3, 4), (4, 4), (5, 5), (6, 6), (6, 7), (10, 2), (10, 3), (11, 4), (12, 9), (13, 10), (8, 12), (9, 13)]
        if p:
            cracks += [(7, 7), (8, 8), (9, 9), (10, 10), (4, 11), (5, 10), (11, 6), (12, 5), (2, 12)]
        for (x, y) in cracks:
            art.px(img, x, y, (54, 50, 46))
        tex(f"crumbling_floor{'_cracked' if p else ''}", img)
        cube(f"crumbling_floor{'_cracked' if p else ''}", T(f"crumbling_floor{'_cracked' if p else ''}"))
    state("crumbling_floor", {f"powered={p}": {"model": f"{NS}:block/crumbling_floor{'_cracked' if p == 'true' else ''}"} for p in ("false", "true")})
    item_block("crumbling_floor")
    # alarm gong: a frame and a bronze disc
    gong = art.new()
    art.disc(gong, 8, 8, 7, (196, 140, 64), (140, 96, 40))
    art.disc(gong, 8, 8, 2, (230, 190, 100))
    tex("alarm_gong_disc", gong)
    wood = art.planks((110, 80, 50), 9001)
    tex("alarm_gong_frame", wood)
    custom("alarm_gong", [el([1, 0, 6], [3, 16, 10], all6("#f")), el([13, 0, 6], [15, 16, 10], all6("#f")), el([1, 14, 6], [15, 16, 10], all6("#f")),
                          el([3, 2, 7.5], [13, 13, 8.5], all6("#d"))], {"f": T("alarm_gong_frame"), "d": T("alarm_gong_disc"), "particle": T("alarm_gong_disc")},
           ao=False)
    state("alarm_gong", {f"facing={f},lit={l}": {"model": f"{NS}:block/alarm_gong", **({"y": r} if r else {})} for f, r in FACINGS for l in ("false", "true")})
    item_block("alarm_gong")


def rewards():
    bronze = (170, 120, 64)
    img = frame((80, 76, 84), bronze)
    for x in range(3, 13):
        art.px(img, x, 7, bronze)
    art.disc(img, 8, 9, 1.4, GOLD)
    tex("expedition_cache_front", img)
    tex("expedition_cache_side", frame((80, 76, 84), bronze))
    top = frame((92, 88, 96), bronze)
    glyph(top, 4, GOLD)
    tex("expedition_cache_top", top)
    for full in ("false", "true"):
        pass
    custom("expedition_cache", [el([1, 0, 2], [15, 12, 14], {"north": "#front", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#side"})],
           {"front": T("expedition_cache_front"), "side": T("expedition_cache_side"), "top": T("expedition_cache_top"), "particle": T("expedition_cache_side")})
    state("expedition_cache", {f"facing={f},full={x}": {"model": f"{NS}:block/expedition_cache", **({"y": r} if r else {})} for f, r in FACINGS
                               for x in ("false", "true")})
    item_block("expedition_cache")
    ped = frame(art.shade(STONE, 1.1), STONE_D)
    tex("pedestal", ped)
    glow = art.new()
    for x in range(16):
        for y in range(16):
            art.px(glow, x, y, (250, 230, 255) if (x + y) % 3 else (200, 160, 250))
    tex("pedestal_relic", glow)
    base = [el([2, 0, 2], [14, 3, 14], all6("#p")), el([4, 3, 4], [12, 10, 12], all6("#p")), el([3, 10, 3], [13, 12, 13], all6("#p"))]
    custom("relic_pedestal", base, {"p": T("pedestal"), "particle": T("pedestal")})
    custom("relic_pedestal_full", base + [el([6, 12, 6], [10, 16, 10], all6("#r"))], {"p": T("pedestal"), "r": T("pedestal_relic"), "particle": T("pedestal")},
           ao=False)
    state("relic_pedestal", {f"facing={f},full={x}": {"model": f"{NS}:block/relic_pedestal{'_full' if x == 'true' else ''}"} for f, r in FACINGS
                             for x in ("false", "true")})
    item_block("relic_pedestal", "relic_pedestal_full")
    keytex = art.new()
    for x in range(16):
        for y in range(16):
            art.px(keytex, x, y, GOLD if (x + y) % 2 else (190, 150, 60))
    tex("key_hook", keytex)
    custom("key_pedestal", base + [el([7, 12, 7.5], [9, 16, 8.5], all6("#k")), el([5, 14, 7.5], [11, 15, 8.5], all6("#k"))],
           {"p": T("pedestal"), "k": T("key_hook"), "particle": T("pedestal")}, ao=False)
    state("key_pedestal", {f"facing={f},full={x}": {"model": f"{NS}:block/key_pedestal", **({"y": r} if r else {})} for f, r in FACINGS
                           for x in ("false", "true")})
    item_block("key_pedestal")
    insc = frame((132, 128, 118), (92, 88, 80))
    for y in (4, 6, 8, 10, 12):
        for x in range(3, 13):
            if (x * 5 + y * 3) % 7 not in (0, 3):
                art.px(insc, x, y, (64, 60, 54))
    tex("inscription_stone", insc)
    cube("inscription_stone", T("ancient_side"), T("ancient_top"), front=T("inscription_stone"))
    state("inscription_stone", {f"facing={f}": {"model": f"{NS}:block/inscription_stone", **({"y": r} if r else {})} for f, r in FACINGS})
    item_block("inscription_stone")
    # warding bell
    bell = art.new()
    for x in range(16):
        for y in range(16):
            art.px(bell, x, y, (226, 200, 120) if (x + 2 * y) % 5 else (180, 150, 80))
    tex("warding_bell", bell)
    custom("warding_bell", [el([3, 0, 3], [13, 2, 13], all6("#s")), el([7, 2, 7], [9, 4, 9], all6("#s")), el([5, 4, 5], [11, 11, 11], all6("#b")),
                            el([6, 11, 6], [10, 13, 10], all6("#b"))], {"s": T("ancient_top"), "b": T("warding_bell"), "particle": T("warding_bell")}, ao=False)
    state("warding_bell", {"": {"model": f"{NS}:block/warding_bell"}})
    item_block("warding_bell")


RELIC_SHAPES = {
    "wright_bust": ((96, 96, 106), [([4, 0, 4], [12, 4, 12]), ([5, 4, 5], [11, 11, 11]), ([6, 11, 6], [10, 13, 10])]),
    "lantern_head": ((120, 170, 150), [([5, 0, 5], [11, 2, 11]), ([4, 2, 4], [12, 10, 12]), ([6, 10, 6], [10, 12, 10])]),
    "choir_conch": ((236, 210, 200), [([4, 0, 5], [12, 5, 11]), ([5, 5, 6], [10, 9, 10]), ([6, 9, 7], [8, 11, 9])]),
    "ember_crown": ((60, 56, 60), [([4, 0, 4], [12, 2, 12]), ([4, 2, 4], [6, 5, 6]), ([10, 2, 4], [12, 5, 6]), ([4, 2, 10], [6, 5, 12]), ([10, 2, 10], [12, 5, 12])]),
    "seed_urn": ((170, 120, 70), [([5, 0, 5], [11, 2, 11]), ([4, 2, 4], [12, 9, 12]), ([6, 9, 6], [10, 12, 10])]),
    "star_globe": ((70, 60, 140), [([6, 0, 6], [10, 2, 10]), ([4, 2, 4], [12, 10, 12])]),
    "silent_mask": ((40, 56, 64), [([7, 0, 7], [9, 4, 9]), ([4, 4, 7], [12, 14, 9])]),
    "gilded_idol": ((226, 186, 84), [([5, 0, 5], [11, 2, 11]), ([6, 2, 6], [10, 9, 10]), ([6, 9, 6], [10, 13, 10])]),
    "league_astrolabe": ((150, 200, 170), [([6, 0, 6], [10, 2, 10]), ([3, 2, 7], [13, 12, 9]), ([7, 2, 3], [9, 12, 13])]),
    "petrified_effigy": ((140, 120, 96), [([5, 0, 5], [11, 2, 11]), ([6, 2, 6], [10, 11, 10]), ([4, 7, 7], [12, 9, 9])]),
}


def relics(relic_list):
    lang = {}
    for rid, desc in relic_list:
        col, boxes = RELIC_SHAPES[rid]
        img = art.new()
        for x in range(16):
            for y in range(16):
                c = col
                if (x + y) % 5 == 0:
                    c = art.shade(col, 1.18)
                elif (x * 3 + y) % 7 == 0:
                    c = art.shade(col, 0.82)
                art.px(img, x, y, c)
        tex(rid, img)
        custom(rid, [el(a, b, all6("#t")) for a, b in boxes], {"t": T(rid), "particle": T(rid)}, ao=False)
        state(rid, {f"facing={f}": {"model": f"{NS}:block/{rid}", **({"y": r} if r else {})} for f, r in FACINGS})
        item_block(rid)
        lang[f"block.{NS}.{rid}"] = name(rid)
        lang[f"tooltip.{NS}.{rid}"] = desc
    return lang


# ============================================================================================ items
def items(artifacts, acc9, weap9):
    lang = {}
    # key
    img = art.new()
    art.disc(img, 4, 5, 2.6, GOLD, (180, 140, 50))
    art.px(img, 4, 5, (0, 0, 0, 0)) if False else None
    for t in range(6, 14):
        art.px(img, t, t - 1, GOLD)
        art.px(img, t, t, (180, 140, 50))
    for (x, y) in [(11, 12), (12, 11), (13, 14), (14, 13)]:
        art.px(img, x, y, GOLD)
    item_tex("vault_key", art.outline(img, (60, 40, 20)))
    # fragment, chart, relic lump, coin, shard
    img = art.new()
    for x in range(3, 13):
        for y in range(2, 14):
            if not (x + y > 22 or (x < 5 and y > 11)):
                art.px(img, x, y, (226, 212, 176))
    for y in (4, 6, 8, 10):
        for x in range(4, 12):
            if (x * 3 + y) % 5:
                art.px(img, x, y, (120, 100, 70))
    item_tex("lore_fragment", art.outline(img, (90, 70, 40)))
    img = art.new()
    for x in range(2, 14):
        for y in range(3, 13):
            art.px(img, x, y, (214, 200, 160) if (x // 4 + y // 4) % 2 else (196, 182, 140))
    for t in range(3, 12):
        art.px(img, t, 12 - t // 2, (160, 40, 40))
    art.px(img, 11, 6, (200, 40, 40))
    item_tex("surveyors_chart", art.outline(img, (90, 70, 40)))
    img = art.new()
    art.disc(img, 8, 9, 4.6, (120, 110, 96), (84, 76, 66))
    for (x, y) in [(6, 7), (9, 8), (8, 11), (10, 10), (5, 10)]:
        art.px(img, x, y, (170, 150, 110))
    art.px(img, 7, 9, (120, 200, 220))
    item_tex("encrusted_relic", art.outline(img, (50, 44, 40)))
    img = art.new()
    art.disc(img, 8, 8, 5, (220, 180, 80), (160, 120, 40))
    glyph(img, 0, (150, 110, 40), 0, 0)
    item_tex("ancient_coin", art.outline(img, (90, 64, 24)))
    img = art.new()
    for t in range(4, 13):
        art.px(img, t, 16 - t, (230, 230, 255))
        art.px(img, t + 1, 16 - t, (180, 190, 250))
        if t % 3 == 0:
            art.px(img, t - 1, 16 - t, (255, 255, 255))
    item_tex("star_shard", art.outline(img, (90, 90, 160)))
    # utility artifacts
    img = art.new()
    for y in range(2, 10):
        art.px(img, 5, y, (200, 200, 220))
        art.px(img, 10, y, (200, 200, 220))
    for x in range(5, 11):
        art.px(img, x, 10, (180, 180, 200))
    for y in range(11, 15):
        art.px(img, 7, y, (150, 110, 70))
        art.px(img, 8, y, (150, 110, 70))
    item_tex("tuning_fork", art.outline(img, (60, 60, 80)))
    img = art.new()
    for i in range(6):
        art.disc(img, 8 - i * 0.4, 9 - i * 0.6, 5 - i * 0.7, art.shade((240, 200, 190), 1 - i * 0.06))
    item_tex("breath_shell", art.outline(img, (120, 90, 80)))
    img = art.new()
    art.disc(img, 8, 9, 5, (120, 120, 130), (80, 80, 90))
    glyph(img, 1, (120, 230, 250), 0, 1)
    item_tex("homing_stone", art.outline(img, (40, 40, 50)))
    img = art.new()
    for t in range(2, 13):
        r = 1 + t / 5
        art.disc(img, t, 13 - t * 0.7, r, (226, 210, 170), (180, 160, 120))
    item_tex("sounding_horn", art.outline(img, (100, 80, 50)))
    for iid in ("vault_key", "lore_fragment", "surveyors_chart", "encrusted_relic", "ancient_coin", "star_shard", "tuning_fork", "breath_shell",
                "homing_stone", "sounding_horn"):
        item_flat(iid)
    # artifact accessories: a distinct gem colour per piece, slot silhouette from Stage 8
    gems = {"wright_signet": (130, 214, 232), "lamplighters_ring": (255, 220, 120), "choir_pendant": (90, 210, 200), "oath_band": (240, 110, 40),
            "gardeners_locket": (110, 200, 90), "readers_lens": (200, 170, 250), "hush_charm": (60, 70, 90), "compass_rose": (220, 60, 60),
            "warden_seal": (150, 150, 170), "echo_stone": (180, 120, 250), "hearth_ring": (230, 90, 80), "stormcaller_band": (80, 140, 230),
            "frostbound_torc": (190, 230, 255), "ashwalker_anklet": (250, 160, 60), "sporeward_mask": (160, 140, 90), "drowned_crown": (40, 200, 220),
            "resonant_core": (240, 200, 255), "heartwood_heart": (200, 60, 70)}
    for a in acc9:
        gem = gems.get(a["id"], (200, 200, 200))
        img = art.new()
        if a["slot"] == "ring":
            for ang in range(0, 360, 10):
                x = int(round(8 + 4 * math.cos(math.radians(ang))))
                y = int(round(9 + 3.5 * math.sin(math.radians(ang))))
                art.px(img, x, y, art.shade(GOLD, 1.15 if y < 9 else 0.85))
            art.disc(img, 8, 5, 1.8, gem, art.shade(gem, 0.7))
            art.px(img, 7, 4, art.shade(gem, 1.4))
        elif a["slot"] == "amulet":
            art.line(img, 3, 2, 8, 8, (200, 190, 150))
            art.line(img, 13, 2, 8, 8, (170, 160, 120))
            art.disc(img, 8, 11, 3, gem, art.shade(gem, 0.65))
            glyph(img, sum(map(ord, a["id"])) % 6, art.shade(gem, 1.5), 0, 3) if a["id"] in ("drowned_crown", "heartwood_heart") else None
            art.px(img, 7, 10, art.shade(gem, 1.4))
        else:
            art.disc(img, 8, 8, 5, art.shade(gem, 0.6), art.shade(gem, 0.4))
            glyph(img, sum(map(ord, a["id"])) % 6, art.shade(gem, 1.5))
        item_tex(a["id"], art.outline(img, (40, 34, 30)))
        item_flat(a["id"])
        lang[f"item.{NS}.{a['id']}"] = name(a["id"])
        lang[f"tooltip.{NS}.{a['id']}"] = a["desc"]
    cols = {"starsilver": ((240, 244, 252), (200, 210, 226), (140, 150, 170)), "galvanic": ((120, 180, 250), (70, 132, 214), (40, 80, 150)),
            "embersteel": ((255, 160, 90), (210, 100, 50), (130, 50, 30)), "nacre": ((250, 244, 236), (210, 200, 196), (150, 140, 140)),
            "umbralite": ((90, 80, 110), (60, 52, 80), (34, 30, 48))}
    for w in weap9:
        item_tex(w["id"], S8.weapon(w["family"], cols[w["material"]], w["material"]))
        item_flat(w["id"], handheld=True)
        lang[f"item.{NS}.{w['id']}"] = name(w["id"])
    for a in artifacts:
        if a["kind"] in ("weapon", "utility"):
            lang[f"tooltip.{NS}.{a['id']}"] = a["desc"]
    for iid, n in (("vault_key", "Vault Key"), ("lore_fragment", "Old Writing"), ("surveyors_chart", "Surveyor's Chart"), ("encrusted_relic", "Encrusted Relic"),
                   ("ancient_coin", "Ancient Coin"), ("star_shard", "Star Shard"), ("tuning_fork", "Resonant Tuning Fork"), ("breath_shell", "Breath Shell"),
                   ("homing_stone", "Homing Stone"), ("sounding_horn", "Sounding Horn")):
        lang[f"item.{NS}.{iid}"] = n
    lang[f"tooltip.{NS}.encrusted_relic"] = "Something is under the crust. A scholar or cartographer could clean and identify it."
    lang[f"tooltip.{NS}.ancient_coin"] = "Old coinage. Scholars and merchants pay for it."
    lang[f"tooltip.{NS}.star_shard"] = "A sliver of starstone that fell from the ceiling. Scholars prize it."
    return lang


def name(i):
    special = {"lamplighters_ring": "Lamplighter's Ring", "gardeners_locket": "Gardener's Locket", "readers_lens": "Star-reader's Lens",
               "wright_glaive": "Vaultwright Glaive", "lamplighter_hook": "Lamplighter's Hook", "oathkeeper_hammer": "Oathkeeper's Hammer",
               "league_astrolabe": "League Astrolabe", "wright_bust": "Vaultwright Bust", "seed_urn": "Sealed Seed Urn"}
    return special.get(i, " ".join(w.capitalize() for w in i.split("_")))


BLOCK_NAMES = {"rune_dial": "Rune Dial", "glyph_tile": "Glyph Tile", "rune_plate": "Rune Plate", "heavy_plate": "Heavy Plate", "ancient_brazier": "Ancient Brazier",
               "offering_socket": "Offering Socket", "resonance_chime": "Resonance Chime", "ancient_lever": "Ancient Lever", "stone_basin": "Stone Basin",
               "light_mirror": "Light Mirror", "light_lens": "Light Lens", "light_receiver": "Light Receiver", "memory_pillar": "Memory Pillar",
               "memory_stone": "Memory Stone", "keystone": "Keystone", "mosaic_frame": "Mosaic Frame", "ancient_seal": "Ancient Seal", "seal_bars": "Seal Bars",
               "dormant_engine": "Dormant Engine", "counterweight": "Counterweight Basket", "key_lock": "Vault Lock", "trap_plate": "Worn Floor Tile",
               "dart_vent": "Dart Slit", "flame_vent": "Flame Vent", "steam_vent": "Steam Vent", "spore_vent": "Spore Vent", "frost_vent": "Frost Vent",
               "crumbling_floor": "Crumbling Floor", "loose_ceiling": "Loose Ceiling", "alarm_gong": "Alarm Gong", "ancient_lamp": "Ancient Lamp",
               "expedition_cache": "Expedition Coffer", "relic_pedestal": "Relic Pedestal", "key_pedestal": "Key Pedestal", "inscription_stone": "Inscription",
               "warding_bell": "Warding Bell"}


def assets(artifacts, acc9, weap9, relic_list):
    S8.SEED[0] = 9000
    mechanisms()
    traps()
    rewards()
    lang = {f"block.{NS}.{k}": v for k, v in BLOCK_NAMES.items()}
    lang.update(relics(relic_list))
    lang.update(items(artifacts, acc9, weap9))
    lang[f"tooltip.{NS}.warding_bell"] = "Hostile creatures do not spawn naturally within 16 blocks of it."
    return lang
