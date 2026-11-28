#!/usr/bin/env python3
"""
Stage 2 asset and data generator: textures (procedural pixel art, see art.py), blockstates, block and
item models, loot tables, recipes, tags, English names and ecological tooltip hints for every Stage 2
block and item. Runs the Stage 1.5 generator (generate_assets.py) first, then merges into its lang
and tag files. Run from the repository root:

    python3 tools/assets/stage2_assets.py

Deterministic: re-running produces identical files. Ids must match the Java registrations in
dev.underdeep.registry (ModWood, ModPlants, ModItems); the dedicated-server self-test checks that
every registered block and item has its model, texture, name and (for blocks) loot table.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import art  # noqa: E402
import generate_assets as base  # noqa: E402

ASSETS = base.ASSETS
DATA = base.DATA
TEXB = os.path.join(ASSETS, "textures", "block")
TEXI = os.path.join(ASSETS, "textures", "item")
NS = "underdeep"
LANG = {}
TOOLTIPS = {}
TAGS = {}
SEED = [1000]


def wjson(path, obj):
    base.wjson(path, obj)


def nseed():
    SEED[0] += 1
    return SEED[0]


def save_block(name, img):
    os.makedirs(TEXB, exist_ok=True)
    img.save(os.path.join(TEXB, name + ".png"))


def save_item(name, img):
    os.makedirs(TEXI, exist_ok=True)
    img.save(os.path.join(TEXI, name + ".png"))


def tag(path, *values):
    TAGS.setdefault(path, [])
    for v in values:
        if v not in TAGS[path]:
            TAGS[path].append(v)


def u(i):
    return i if ":" in i else f"{NS}:{i}"


def bs(bid, obj):
    wjson(os.path.join(ASSETS, "blockstates", bid + ".json"), obj)


def bm(name, obj):
    wjson(os.path.join(ASSETS, "models", "block", name + ".json"), obj)


def im(name, obj):
    wjson(os.path.join(ASSETS, "models", "item", name + ".json"), obj)


def item_gen(name, tex):
    im(name, {"parent": "minecraft:item/generated", "textures": {"layer0": tex}})


def item_block(name, model=None):
    im(name, {"parent": f"{NS}:block/{model or name}"})


def name_of(i):
    return " ".join(w.capitalize() for w in i.split("_"))


def lang_block(bid, name=None):
    LANG[f"block.{NS}.{bid}"] = name or name_of(bid)


def lang_item(iid, name=None):
    LANG[f"item.{NS}.{iid}"] = name or name_of(iid)


def tip(iid, text):
    TOOLTIPS[f"tooltip.{NS}.{iid}"] = text


# ------------------------------------------------------------------------------------------- loot

SHEARS = {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}
SILK = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
SHEARS_OR_SILK = {"condition": "minecraft:any_of", "terms": [SHEARS, SILK]}
NO_SHEARS_OR_SILK = {"condition": "minecraft:inverted", "term": SHEARS_OR_SILK}
NO_SILK = {"condition": "minecraft:inverted", "term": SILK}
SURVIVES = {"condition": "minecraft:survives_explosion"}


def entry(item, conditions=None, functions=None):
    e = {"type": "minecraft:item", "name": u(item)}
    if conditions:
        e["conditions"] = conditions
    if functions:
        e["functions"] = functions
    return e


def pool(entries, conditions=None, rolls=1):
    p = {"rolls": rolls, "bonus_rolls": 0, "entries": entries}
    if conditions:
        p["conditions"] = conditions
    return p


def count(lo, hi):
    return {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def state_is(bid, **props):
    return {"condition": "minecraft:block_state_property", "block": u(bid),
            "properties": {k: str(v).lower() for k, v in props.items()}}


def loot(bid, pools):
    wjson(os.path.join(DATA, NS, "loot_table", "blocks", bid + ".json"),
          {"type": "minecraft:block", "pools": pools, "random_sequence": f"{NS}:blocks/{bid}"})


def loot_self(bid, item=None):
    loot(bid, [pool([entry(item or bid)], [SURVIVES])])


def loot_shears(bid, item=None, otherwise=None):
    entries = [entry(item or bid, [SHEARS_OR_SILK])]
    if otherwise:
        entries.append(otherwise)
    loot(bid, [pool([{"type": "minecraft:alternatives", "children": entries}])])


def loot_ore(bid, item, lo=1, hi=1):
    loot(bid, [pool([{"type": "minecraft:alternatives", "children": [
        entry(bid, [SILK]),
        entry(item, None, [count(lo, hi) if hi > 1 else {"function": "minecraft:set_count", "count": lo},
                           {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                            "formula": "minecraft:ore_drops"},
                           {"function": "minecraft:explosion_decay"}])]}])])


def loot_leaves(bid, sapling):
    loot(bid, [pool([{"type": "minecraft:alternatives", "children": [
        entry(bid, [SHEARS_OR_SILK]),
        entry(sapling, [SURVIVES, {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                                   "chances": [0.05, 0.0625, 0.083333336, 0.1]}])]}]),
        pool([entry("minecraft:stick", [{"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                                          "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1]}],
                    [count(1, 2), {"function": "minecraft:explosion_decay"}])], [NO_SHEARS_OR_SILK])])


# ------------------------------------------------------------------------------------------- recipes

def recipe(name, obj):
    wjson(os.path.join(DATA, NS, "recipe", name + ".json"), obj)


def ing(x):
    return {"tag": x[1:]} if x.startswith("#") else {"item": u(x)}


def shaped(name, pattern, key, result, n=1, category="misc"):
    recipe(name, {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
                  "key": {k: ing(v) for k, v in key.items()}, "result": {"id": u(result), "count": n}})


def shapeless(name, ings, result, n=1, category="misc", components=None):
    r = {"type": "minecraft:crafting_shapeless", "category": category, "ingredients": [ing(i) for i in ings],
         "result": {"id": u(result), "count": n}}
    if components:
        r["result"]["components"] = components
    recipe(name, r)


def cook(name, src, out, xp=0.35, food=True, blast=False):
    cat = "food" if food else "misc"
    recipe(name + "_smelting", {"type": "minecraft:smelting", "category": cat, "ingredient": ing(src),
                                "result": {"id": u(out)}, "experience": xp, "cookingtime": 200})
    if food:
        recipe(name + "_smoking", {"type": "minecraft:smoking", "category": "food", "ingredient": ing(src),
                                   "result": {"id": u(out)}, "experience": xp, "cookingtime": 100})
        recipe(name + "_campfire", {"type": "minecraft:campfire_cooking", "category": "food", "ingredient": ing(src),
                                    "result": {"id": u(out)}, "experience": xp, "cookingtime": 600})
    if blast:
        recipe(name + "_blasting", {"type": "minecraft:blasting", "category": "misc", "ingredient": ing(src),
                                    "result": {"id": u(out)}, "experience": xp, "cookingtime": 100})


# ------------------------------------------------------------------------------------------- wood sets

WOODS = {
    # name: bark, wood, bark style, bark accent, leaves colour, leaf style, leaf glow, sapling shape, burnable, window
    "duskoak": dict(bark=(78, 56, 52), wood=(140, 102, 84), style="furrow", accent=None, leaf=(46, 92, 70),
                    lstyle="round", glow=None, sap="round", burn=True, window=True,
                    note="Broad branching forest tree. Grows from saplings on living soil."),
    "spirebark": dict(bark=(196, 196, 182), wood=(214, 196, 150), style="lenticel", accent=(120, 120, 110),
                      leaf=(56, 120, 116), lstyle="needles", glow=None, sap="cone", burn=True, window=True,
                      note="Very tall narrow tree. Tolerates stony ground."),
    "goldwood": dict(bark=(150, 104, 40), wood=(222, 170, 72), style="furrow", accent=(250, 200, 90), leaf=None,
                     lstyle=None, glow=None, sap="round", burn=True, window=True,
                     note="Golden broad-crowned tree. Needs bright light to grow."),
    "mirewillow": dict(bark=(84, 92, 56), wood=(150, 150, 100), style="furrow", accent=(70, 120, 50),
                       leaf=(92, 150, 72), lstyle="drooping", glow=None, sap="droop", burn=True, window=False,
                       note="Weeping wetland tree. Grows on mud, clay and wet soil."),
    "frostfir": dict(bark=(150, 160, 172), wood=(206, 214, 222), style="scales", accent=(240, 248, 255),
                     leaf=(70, 120, 120), lstyle="needles", glow=None, sap="cone", burn=True, window=True,
                     note="Cold-adapted conifer. Grows on snow and ice; dormant near heat."),
    "bloodthorn": dict(bark=(92, 24, 30), wood=(150, 46, 50), style="twisted", accent=(200, 60, 50),
                       leaf=(150, 30, 40), lstyle="round", glow=(255, 110, 70), sap="twisted", burn=False, window=False,
                       note="Twisted heat-loving tree. Grows on volcanic ground; fireproof wood."),
    "lumenwood": dict(bark=(176, 190, 206), wood=(170, 196, 226), style="lenticel", accent=(120, 200, 255),
                      leaf=(90, 150, 220), lstyle="round", glow=(200, 240, 255), sap="umbrella", burn=True, window=True,
                      note="Luminous umbrella tree with glowing foliage."),
}


def stairs_state(bid):
    v = {}
    base_y = {"east": 0, "south": 90, "west": 180, "north": 270}
    for f, b in base_y.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = f"{NS}:block/{bid}" + ("" if shape == "straight" else "_inner" if "inner" in shape else "_outer")
                if half == "bottom":
                    y = b - 90 if shape.endswith("left") else b
                    x = 0
                else:
                    y = b + 90 if shape.endswith("right") else b
                    x = 180
                y %= 360
                d = {"model": model}
                if x:
                    d["x"] = x
                if y:
                    d["y"] = y
                if x or y:
                    d["uvlock"] = True
                v[f"facing={f},half={half},shape={shape}"] = d
    return {"variants": v}


def door_state(bid):
    v = {}
    base_y = {"east": 0, "south": 90, "west": 180, "north": 270}
    for f, b in base_y.items():
        for half, part in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for op in (False, True):
                    model = f"{NS}:block/{bid}_{part}_{hinge}" + ("_open" if op else "")
                    y = (b + (90 if hinge == "left" else 270) if op else b) % 360
                    d = {"model": model}
                    if y:
                        d["y"] = y
                    v[f"facing={f},half={half},hinge={hinge},open={str(op).lower()}"] = d
    return {"variants": v}


def trapdoor_state(bid):
    m = f"{NS}:block/{bid}"
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    top_open = {"north": 180, "east": 270, "south": 0, "west": 90}
    v = {}
    for f, y in rot.items():
        v[f"facing={f},half=bottom,open=false"] = {"model": m + "_bottom", **({"y": y} if y else {})}
        v[f"facing={f},half=top,open=false"] = {"model": m + "_top", **({"y": y} if y else {})}
        v[f"facing={f},half=bottom,open=true"] = {"model": m + "_open", **({"y": y} if y else {})}
        v[f"facing={f},half=top,open=true"] = {"model": m + "_open", "x": 180, **({"y": top_open[f]} if top_open[f] else {})}
    return {"variants": v}


def gate_state(bid):
    m = f"{NS}:block/{bid}"
    rot = {"south": 0, "west": 90, "north": 180, "east": 270}
    v = {}
    for f, y in rot.items():
        for wall in (False, True):
            for op in (False, True):
                model = m + ("_wall" if wall else "") + ("_open" if op else "")
                d = {"model": model, "uvlock": True}
                if y:
                    d["y"] = y
                v[f"facing={f},in_wall={str(wall).lower()},open={str(op).lower()}"] = d
    return {"variants": v}


def button_state(bid):
    m = f"{NS}:block/{bid}"
    v = {}
    floor = {"north": 0, "east": 90, "south": 180, "west": 270}
    ceil = {"north": 180, "east": 270, "south": 0, "west": 90}
    for f in floor:
        for p in (False, True):
            model = m + ("_pressed" if p else "")
            v[f"face=floor,facing={f},powered={str(p).lower()}"] = {"model": model, **({"y": floor[f]} if floor[f] else {})}
            v[f"face=wall,facing={f},powered={str(p).lower()}"] = {"model": model, "x": 90, "uvlock": True,
                                                                   **({"y": floor[f]} if floor[f] else {})}
            v[f"face=ceiling,facing={f},powered={str(p).lower()}"] = {"model": model, "x": 180,
                                                                      **({"y": ceil[f]} if ceil[f] else {})}
    return {"variants": v}


def pillar_state(bid, model=None, horizontal=None):
    m = f"{NS}:block/{model or bid}"
    h = f"{NS}:block/{horizontal}" if horizontal else m
    return {"variants": {"axis=y": {"model": m}, "axis=z": {"model": h, "x": 90},
                         "axis=x": {"model": h, "x": 90, "y": 90}}}


def wood_set(name, w):
    sd = nseed() * 7
    log, wood, stripped_log, stripped_wood = f"{name}_log", f"{name}_wood", f"stripped_{name}_log", f"stripped_{name}_wood"
    planks_id = f"{name}_planks"
    # textures
    save_block(log, art.bark(w["bark"], sd, style=w["style"], accent=w["accent"]))
    save_block(log + "_top", art.log_top(w["bark"], w["wood"], sd + 1))
    save_block(stripped_log, art.stripped_side(w["wood"], sd + 2))
    save_block(stripped_log + "_top", art.log_top(art.shade(w["wood"], 0.85), w["wood"], sd + 3))
    save_block(planks_id, art.planks(w["wood"], sd + 4))
    save_block(f"{name}_door_top", art.door(w["wood"], sd + 5, True, w["window"]))
    save_block(f"{name}_door_bottom", art.door(w["wood"], sd + 6, False))
    save_block(f"{name}_trapdoor", art.trapdoor(w["wood"], sd + 7))
    save_item(f"{name}_door", door_item(w["wood"], sd + 8, w["window"]))
    save_item(f"{name}_sign", art.sign_item(w["wood"], sd + 9))
    save_item(f"{name}_hanging_sign", art.sign_item(w["wood"], sd + 10, hanging=True))
    if w["leaf"]:
        save_block(f"{name}_leaves", art.leaves(w["leaf"], sd + 11, w["lstyle"], glow=w["glow"]))
    leaf_c = w["leaf"] or (214, 164, 46)
    save_block(f"{name}_sapling", art.sapling(art.shade(w["bark"], 0.9), leaf_c, sd + 12, w["sap"], accent=w["glow"]))
    ent = os.path.join(ASSETS, "textures", "entity", "signs")
    os.makedirs(os.path.join(ent, "hanging"), exist_ok=True)
    art.sign_sheet(w["wood"], sd + 13, post=w["bark"]).save(os.path.join(ent, name + ".png"))
    art.sign_sheet(art.shade(w["wood"], 0.92), sd + 14, post=w["bark"]).save(os.path.join(ent, "hanging", name + ".png"))
    gui = os.path.join(ASSETS, "textures", "gui", "hanging_signs")
    os.makedirs(gui, exist_ok=True)
    art.planks(w["wood"], sd + 15).save(os.path.join(gui, name + ".png"))

    t = lambda x: f"{NS}:block/{x}"
    # logs & wood
    for bid, side, end in ((log, log, log + "_top"), (stripped_log, stripped_log, stripped_log + "_top"),
                           (wood, log, log), (stripped_wood, stripped_log, stripped_log)):
        bm(bid, {"parent": "minecraft:block/cube_column", "textures": {"end": t(end), "side": t(side)}})
        bm(bid + "_horizontal", {"parent": "minecraft:block/cube_column_horizontal", "textures": {"end": t(end), "side": t(side)}})
        bs(bid, pillar_state(bid, horizontal=bid + "_horizontal" if bid in (log, stripped_log) else None))
        item_block(bid)
        loot_self(bid)
    bm(planks_id, {"parent": "minecraft:block/cube_all", "textures": {"all": t(planks_id)}})
    bs(planks_id, {"variants": {"": {"model": t(planks_id)}}})
    item_block(planks_id)
    loot_self(planks_id)
    # stairs / slab
    st = f"{name}_stairs"
    tex3 = {"bottom": t(planks_id), "top": t(planks_id), "side": t(planks_id)}
    bm(st, {"parent": "minecraft:block/stairs", "textures": tex3})
    bm(st + "_inner", {"parent": "minecraft:block/inner_stairs", "textures": tex3})
    bm(st + "_outer", {"parent": "minecraft:block/outer_stairs", "textures": tex3})
    bs(st, stairs_state(st))
    item_block(st)
    loot_self(st)
    sl = f"{name}_slab"
    bm(sl, {"parent": "minecraft:block/slab", "textures": tex3})
    bm(sl + "_top", {"parent": "minecraft:block/slab_top", "textures": tex3})
    bs(sl, {"variants": {"type=bottom": {"model": t(sl)}, "type=top": {"model": t(sl + "_top")},
                         "type=double": {"model": t(planks_id)}}})
    item_block(sl)
    loot(sl, [pool([entry(sl, None, [{"function": "minecraft:set_count", "count": 2, "add": False,
                                       "conditions": [state_is(sl, type="double")]},
                                      {"function": "minecraft:explosion_decay"}])])])
    # fence & gate
    fe = f"{name}_fence"
    bm(fe + "_post", {"parent": "minecraft:block/fence_post", "textures": {"texture": t(planks_id)}})
    bm(fe + "_side", {"parent": "minecraft:block/fence_side", "textures": {"texture": t(planks_id)}})
    bm(fe + "_inventory", {"parent": "minecraft:block/fence_inventory", "textures": {"texture": t(planks_id)}})
    bs(fe, {"multipart": [{"apply": {"model": t(fe + "_post")}}] + [
        {"when": {d: "true"}, "apply": {"model": t(fe + "_side"), "uvlock": True, **({"y": y} if y else {})}}
        for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))]})
    item_block(fe, fe + "_inventory")
    loot_self(fe)
    ga = f"{name}_fence_gate"
    for suf, par in (("", "template_fence_gate"), ("_open", "template_fence_gate_open"),
                     ("_wall", "template_fence_gate_wall"), ("_wall_open", "template_fence_gate_wall_open")):
        bm(ga + suf, {"parent": f"minecraft:block/{par}", "textures": {"texture": t(planks_id)}})
    bs(ga, gate_state(ga))
    item_block(ga)
    loot_self(ga)
    # door
    do = f"{name}_door"
    for part in ("bottom", "top"):
        for hinge in ("left", "right"):
            for op in ("", "_open"):
                bm(f"{do}_{part}_{hinge}{op}", {"parent": f"minecraft:block/door_{part}_{hinge}{op}",
                                                "textures": {"bottom": t(do + "_bottom"), "top": t(do + "_top")}})
    bs(do, door_state(do))
    item_gen(do, f"{NS}:item/{do}")
    loot(do, [pool([entry(do, [state_is(do, half="lower")])], [SURVIVES])])
    # trapdoor
    tr = f"{name}_trapdoor"
    for suf in ("bottom", "top", "open"):
        bm(f"{tr}_{suf}", {"parent": f"minecraft:block/template_trapdoor_{suf}", "textures": {"texture": t(tr)}})
    bs(tr, trapdoor_state(tr))
    item_block(tr, tr + "_bottom")
    loot_self(tr)
    # button & plate
    bu = f"{name}_button"
    bm(bu, {"parent": "minecraft:block/button", "textures": {"texture": t(planks_id)}})
    bm(bu + "_pressed", {"parent": "minecraft:block/button_pressed", "textures": {"texture": t(planks_id)}})
    bm(bu + "_inventory", {"parent": "minecraft:block/button_inventory", "textures": {"texture": t(planks_id)}})
    bs(bu, button_state(bu))
    item_block(bu, bu + "_inventory")
    loot_self(bu)
    pp = f"{name}_pressure_plate"
    bm(pp, {"parent": "minecraft:block/pressure_plate_up", "textures": {"texture": t(planks_id)}})
    bm(pp + "_down", {"parent": "minecraft:block/pressure_plate_down", "textures": {"texture": t(planks_id)}})
    bs(pp, {"variants": {"powered=false": {"model": t(pp)}, "powered=true": {"model": t(pp + "_down")}}})
    item_block(pp)
    loot_self(pp)
    # signs
    for sid, particle, item_id in ((f"{name}_sign", planks_id, f"{name}_sign"), (f"{name}_wall_sign", planks_id, f"{name}_sign"),
                                   (f"{name}_hanging_sign", stripped_log, f"{name}_hanging_sign"),
                                   (f"{name}_wall_hanging_sign", stripped_log, f"{name}_hanging_sign")):
        bm(sid, {"textures": {"particle": t(particle)}})
        bs(sid, {"variants": {"": {"model": t(sid)}}})
        loot_self(sid, item_id)
    item_gen(f"{name}_sign", f"{NS}:item/{name}_sign")
    item_gen(f"{name}_hanging_sign", f"{NS}:item/{name}_hanging_sign")
    # leaves & sapling
    leaves_id = f"{name}_leaves" if w["leaf"] else "golden_leaves"
    if w["leaf"]:
        bm(leaves_id, {"parent": "minecraft:block/leaves", "textures": {"all": t(leaves_id)}})
        bs(leaves_id, {"variants": {"": {"model": t(leaves_id)}}})
        item_block(leaves_id)
    loot_leaves(leaves_id, f"{name}_sapling")
    sapling_blocks(f"{name}_sapling", f"{name}")

    # names
    title = name_of(name)
    for bid in (log, wood, stripped_log, stripped_wood, planks_id, st, sl, fe, ga, tr, bu, pp):
        lang_block(bid)
    lang_block(do)
    lang_block(f"{name}_sign")
    lang_block(f"{name}_wall_sign", f"{title} Wall Sign")
    lang_block(f"{name}_hanging_sign")
    lang_block(f"{name}_wall_hanging_sign", f"{title} Wall Hanging Sign")
    if w["leaf"]:
        lang_block(leaves_id)
    lang_block(f"{name}_sapling")
    tip(f"{name}_sapling", w["note"])
    # tags
    logs_tag = f"{NS}:{name}_logs"
    tag(f"{NS}/tags/block/{name}_logs.json", u(log), u(wood), u(stripped_log), u(stripped_wood))
    tag(f"{NS}/tags/item/{name}_logs.json", u(log), u(wood), u(stripped_log), u(stripped_wood))
    for kind in ("block", "item"):
        tag(f"minecraft/tags/{kind}/logs{'_that_burn' if w['burn'] else ''}.json", "#" + logs_tag)
        tag(f"minecraft/tags/{kind}/planks.json", u(planks_id))
        tag(f"minecraft/tags/{kind}/wooden_stairs.json", u(st))
        tag(f"minecraft/tags/{kind}/wooden_slabs.json", u(sl))
        tag(f"minecraft/tags/{kind}/wooden_fences.json", u(fe))
        tag(f"minecraft/tags/{kind}/fence_gates.json", u(ga))
        tag(f"minecraft/tags/{kind}/wooden_doors.json", u(do))
        tag(f"minecraft/tags/{kind}/wooden_trapdoors.json", u(tr))
        tag(f"minecraft/tags/{kind}/wooden_buttons.json", u(bu))
        tag(f"minecraft/tags/{kind}/wooden_pressure_plates.json", u(pp))
        tag(f"minecraft/tags/{kind}/saplings.json", u(f"{name}_sapling"))
        if w["leaf"]:
            tag(f"minecraft/tags/{kind}/leaves.json", u(leaves_id))
    if not w["burn"]:
        tag("minecraft/tags/block/logs.json", "#" + logs_tag)
        tag("minecraft/tags/item/logs.json", "#" + logs_tag)
        tag("minecraft/tags/item/non_flammable_wood.json", u(log), u(wood), u(stripped_log), u(stripped_wood), u(planks_id),
            u(st), u(sl), u(fe), u(ga), u(do), u(tr), u(bu), u(pp), u(f"{name}_sign"), u(f"{name}_hanging_sign"))
    tag("minecraft/tags/block/standing_signs.json", u(f"{name}_sign"))
    tag("minecraft/tags/block/wall_signs.json", u(f"{name}_wall_sign"))
    tag("minecraft/tags/block/ceiling_hanging_signs.json", u(f"{name}_hanging_sign"))
    tag("minecraft/tags/block/wall_hanging_signs.json", u(f"{name}_wall_hanging_sign"))
    tag("minecraft/tags/item/signs.json", u(f"{name}_sign"))
    tag("minecraft/tags/item/hanging_signs.json", u(f"{name}_hanging_sign"))
    for bid in (log, wood, stripped_log, stripped_wood, planks_id, st, sl, fe, ga, do, tr, bu, pp, f"{name}_sign",
                f"{name}_wall_sign", f"{name}_hanging_sign", f"{name}_wall_hanging_sign"):
        tag("minecraft/tags/block/mineable/axe.json", u(bid))
    if w["leaf"]:
        tag("minecraft/tags/block/mineable/hoe.json", u(leaves_id))
    # recipes
    shapeless(f"{planks_id}", ["#" + logs_tag], planks_id, 4, "building")
    shaped(wood, ["##", "##"], {"#": log}, wood, 3, "building")
    shaped(stripped_wood, ["##", "##"], {"#": stripped_log}, stripped_wood, 3, "building")
    shaped(st, ["#  ", "## ", "###"], {"#": planks_id}, st, 4, "building")
    shaped(sl, ["###"], {"#": planks_id}, sl, 6, "building")
    shaped(fe, ["W#W", "W#W"], {"W": planks_id, "#": "minecraft:stick"}, fe, 3, "misc")
    shaped(ga, ["#W#", "#W#"], {"W": planks_id, "#": "minecraft:stick"}, ga, 1, "redstone")
    shaped(do, ["##", "##", "##"], {"#": planks_id}, do, 3, "redstone")
    shaped(tr, ["###", "###"], {"#": planks_id}, tr, 2, "redstone")
    shapeless(bu, [planks_id], bu, 1, "redstone")
    shaped(pp, ["##"], {"#": planks_id}, pp, 1, "redstone")
    shaped(f"{name}_sign", ["###", "###", " X "], {"#": planks_id, "X": "minecraft:stick"}, f"{name}_sign", 3)
    shaped(f"{name}_hanging_sign", ["X X", "###", "###"], {"#": stripped_log, "X": "minecraft:chain"},
           f"{name}_hanging_sign", 6)


def door_item(wood_c, seed, window):
    img = art.new()
    top = art.door(wood_c, seed, True, window)
    bot = art.door(wood_c, seed + 1, False)
    for x in range(16):
        for y in range(8):
            img.putpixel(((x // 2) + 4, y), top.getpixel((x, y * 2)))
            img.putpixel(((x // 2) + 4, y + 8), bot.getpixel((x, y * 2)))
    return art.outline(img, art.shade(wood_c, 0.4))


def sapling_blocks(sid, species):
    t = f"{NS}:block/{sid}"
    bm(sid, {"parent": "minecraft:block/cross", "textures": {"cross": t}})
    bs(sid, {"variants": {"": {"model": t}}})
    item_gen(sid, t)
    loot_self(sid)
    pot = f"potted_{sid}"
    bm(pot, {"parent": "minecraft:block/flower_pot_cross", "textures": {"plant": t}})
    bs(pot, {"variants": {"": {"model": f"{NS}:block/{pot}"}}})
    loot(pot, [pool([entry("minecraft:flower_pot")], [SURVIVES]), pool([entry(sid)], [SURVIVES])])
    lang_block(pot, f"Potted {name_of(sid)}")
    tag("minecraft/tags/block/flower_pots.json", u(pot))


def extra_trees():
    sd = 7700
    # cragpine: spirebark logs, own leaves & sapling
    save_block("cragpine_leaves", art.leaves((40, 90, 60), sd, "needles"))
    save_block("cragpine_sapling", art.sapling((120, 110, 90), (40, 90, 60), sd + 1, "bent"))
    bm("cragpine_leaves", {"parent": "minecraft:block/leaves", "textures": {"all": f"{NS}:block/cragpine_leaves"}})
    bs("cragpine_leaves", {"variants": {"": {"model": f"{NS}:block/cragpine_leaves"}}})
    item_block("cragpine_leaves")
    loot_leaves("cragpine_leaves", "cragpine_sapling")
    sapling_blocks("cragpine_sapling", "cragpine")
    lang_block("cragpine_leaves")
    lang_block("cragpine_sapling")
    tip("cragpine_sapling", "Wind-bent mountain tree. Grows on stony ground.")
    # cinderbark
    save_block("cinderbark_log", art.bark((40, 34, 34), sd + 2, style="charred", accent=(255, 120, 40)))
    save_block("cinderbark_log_top", art.log_top((40, 34, 34), (120, 60, 40), sd + 3))
    save_block("stripped_cinderbark_log", art.stripped_side((150, 80, 50), sd + 4))
    save_block("stripped_cinderbark_log_top", art.log_top((110, 60, 40), (150, 80, 50), sd + 5))
    save_block("cinder_leaves", art.leaves((70, 60, 56), sd + 6, "round", glow=(255, 140, 50), hole=0.25))
    save_block("cinderbark_sapling", art.sapling((50, 40, 40), (90, 70, 60), sd + 7, "twisted", accent=(255, 130, 50)))
    for bid in ("cinderbark_log", "stripped_cinderbark_log"):
        tx = f"{NS}:block/{bid}"
        bm(bid, {"parent": "minecraft:block/cube_column", "textures": {"end": tx + "_top", "side": tx}})
        bm(bid + "_horizontal", {"parent": "minecraft:block/cube_column_horizontal", "textures": {"end": tx + "_top", "side": tx}})
        bs(bid, pillar_state(bid, horizontal=bid + "_horizontal"))
        item_block(bid)
        loot_self(bid)
        lang_block(bid)
        tag("minecraft/tags/block/mineable/axe.json", u(bid))
        for k in ("block", "item"):
            tag(f"minecraft/tags/{k}/logs.json", u(bid))
        tag("minecraft/tags/item/non_flammable_wood.json", u(bid))
    bm("cinder_leaves", {"parent": "minecraft:block/leaves", "textures": {"all": f"{NS}:block/cinder_leaves"}})
    bs("cinder_leaves", {"variants": {"": {"model": f"{NS}:block/cinder_leaves"}}})
    item_block("cinder_leaves")
    loot_leaves("cinder_leaves", "cinderbark_sapling")
    sapling_blocks("cinderbark_sapling", "cinderbark")
    lang_block("cinder_leaves")
    lang_block("cinderbark_sapling")
    tip("cinderbark_sapling", "Gnarled volcanic tree. Only grows on volcanic ground near heat.")
    for k in ("block", "item"):
        tag(f"minecraft/tags/{k}/leaves.json", u("cragpine_leaves"), u("cinder_leaves"))
        tag(f"minecraft/tags/{k}/saplings.json", u("cragpine_sapling"), u("cinderbark_sapling"))
    tag("minecraft/tags/block/mineable/hoe.json", u("cragpine_leaves"), u("cinder_leaves"))
    shapeless("charcoal_from_cinderbark", ["cinderbark_log"], "minecraft:charcoal", 2)
    # golden leaves now drop goldwood saplings
    loot_leaves("golden_leaves", "goldwood_sapling")


# ------------------------------------------------------------------------------------------- block kinds

def tb(x):
    return f"{NS}:block/{x}"


def k_cross(bid, img, item=True, item_tex=None):
    save_block(bid, img)
    bm(bid, {"parent": "minecraft:block/cross", "textures": {"cross": tb(bid)}})
    bs(bid, {"variants": {"": {"model": tb(bid)}}})
    if item:
        item_gen(bid, item_tex or tb(bid))


def k_states(bid, prop, mapping, item_tex, item=True):
    """mapping: {property value: (texture name, image)}"""
    variants = {}
    for val, (tex, img) in mapping.items():
        if img is not None:
            save_block(tex, img)
            bm(tex, {"parent": "minecraft:block/cross", "textures": {"cross": tb(tex)}})
        variants[f"{prop}={val}"] = {"model": tb(tex)}
    bs(bid, {"variants": variants})
    if item:
        item_gen(bid, tb(item_tex))


def k_harvest(bid, imgs, item=True):
    names = [f"{bid}_stage{i}" for i in range(3)]
    for n, img in zip(names, imgs):
        save_block(n, img)
        bm(n, {"parent": "minecraft:block/cross", "textures": {"cross": tb(n)}})
    bs(bid, {"variants": {"age=0": {"model": tb(names[0])}, "age=1": {"model": tb(names[1])},
                          "age=2": {"model": tb(names[1])}, "age=3": {"model": tb(names[2])}}})
    if item:
        item_gen(bid, tb(names[2]))


def k_crop(bid, imgs):
    names = [f"{bid}_stage{i}" for i in range(4)]
    for n, img in zip(names, imgs):
        save_block(n, img)
        bm(n, {"parent": "minecraft:block/crop", "textures": {"crop": tb(n)}})
    stage = [0, 0, 1, 1, 2, 2, 2, 3]
    bs(bid, {"variants": {f"age={a}": {"model": tb(names[stage[a]])} for a in range(8)}})


def k_cube(bid, img, item=True):
    save_block(bid, img)
    bm(bid, {"parent": "minecraft:block/cube_all", "textures": {"all": tb(bid)}})
    bs(bid, {"variants": {"": {"model": tb(bid)}}})
    if item:
        item_block(bid)


def k_pillar(bid, side, top):
    save_block(bid, side)
    save_block(bid + "_top", top)
    bm(bid, {"parent": "minecraft:block/cube_column", "textures": {"end": tb(bid + "_top"), "side": tb(bid)}})
    bm(bid + "_horizontal", {"parent": "minecraft:block/cube_column_horizontal", "textures": {"end": tb(bid + "_top"), "side": tb(bid)}})
    bs(bid, pillar_state(bid, horizontal=bid + "_horizontal"))
    item_block(bid)


def k_cluster(bid, img):
    save_block(bid, img)
    m = tb(bid)
    bs(bid, {"variants": {
        "facing=up": {"model": m}, "facing=down": {"model": m, "x": 180},
        "facing=north": {"model": m, "x": 90}, "facing=south": {"model": m, "x": 90, "y": 180},
        "facing=east": {"model": m, "x": 90, "y": 90}, "facing=west": {"model": m, "x": 90, "y": 270}}})
    bm(bid, {"parent": "minecraft:block/cross", "textures": {"cross": m}})
    item_gen(bid, m)


def k_hanging(tip_id, body_id, tip_img, body_img, item=True):
    k_cross(tip_id, tip_img, item=item)
    k_cross(body_id, body_img, item=False)


def k_pad(bid, img):
    save_block(bid, img)
    bm(bid, {"parent": "minecraft:block/block", "ambientocclusion": False,
             "textures": {"particle": tb(bid), "texture": tb(bid)},
             "elements": [{"from": [0, 0.25, 0], "to": [16, 0.25, 16],
                           "faces": {"down": {"uv": [16, 16, 0, 0], "texture": "#texture"},
                                     "up": {"uv": [16, 0, 0, 16], "texture": "#texture"}}}]})
    bs(bid, {"variants": {"": [{"model": tb(bid)}, {"model": tb(bid), "y": 90}, {"model": tb(bid), "y": 180},
                               {"model": tb(bid), "y": 270}]}})
    item_gen(bid, tb(bid))


def k_multiface(bid, img):
    save_block(bid, img)
    bm(bid, {"parent": "minecraft:block/block", "ambientocclusion": False,
             "textures": {"particle": tb(bid), "texture": tb(bid)},
             "elements": [{"from": [0, 0, 0.1], "to": [16, 16, 0.1],
                           "faces": {"north": {"uv": [16, 0, 0, 16], "texture": "#texture"},
                                     "south": {"uv": [0, 0, 16, 16], "texture": "#texture"}}}]})
    parts = []
    for d, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}),
                   ("up", {"x": 270}), ("down", {"x": 90})):
        parts.append({"when": {d: "true"}, "apply": {"model": tb(bid), "uvlock": True, **rot}})
    bs(bid, {"multipart": parts})
    item_gen(bid, tb(bid))


def facing_state(bid, models):
    """models: list of (state suffix, model) e.g. [("", "x")] or [(",age=0", ...)]."""
    v = {}
    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for suf, model in models:
            v[f"facing={f}{suf}"] = {"model": tb(model), **({"y": y} if y else {})}
    return {"variants": v}


def k_shelf(bid, top_img, side_img, item_img):
    save_block(bid + "_top", top_img)
    save_block(bid + "_side", side_img)
    save_item(bid, item_img)
    bm(bid, {"parent": "minecraft:block/block", "textures": {"particle": tb(bid + "_side"), "top": tb(bid + "_top"),
                                                             "side": tb(bid + "_side")},
             "elements": [{"from": [2, 6, 9], "to": [14, 10, 16], "faces": {
                 "up": {"uv": [2, 9, 14, 16], "texture": "#top"}, "down": {"uv": [2, 9, 14, 16], "texture": "#top"},
                 "north": {"uv": [2, 6, 14, 10], "texture": "#side"}, "east": {"uv": [0, 6, 7, 10], "texture": "#side"},
                 "west": {"uv": [9, 6, 16, 10], "texture": "#side"}}}]})
    bs(bid, facing_state(bid, [("", bid)]))
    item_gen(bid, f"{NS}:item/{bid}")


def k_knot(bid, imgs):
    models = []
    for a, img in enumerate(imgs):
        n = f"{bid}_{a}"
        save_block(n, img)
        bm(n, {"parent": "minecraft:block/block", "textures": {"particle": tb(n), "all": tb(n)},
               "elements": [{"from": [5, 4, 12], "to": [11, 12, 16], "faces": {
                   f: {"uv": [5, 4, 11, 12], "texture": "#all"} for f in ("north", "up", "down", "east", "west")}}]})
        models.append((f",age={a}", n))
    bs(bid, facing_state(bid, models))
    item_gen(bid, tb(f"{bid}_2"))


# ------------------------------------------------------------------------------------------- content

def fan(color, seed):
    import random
    rnd = random.Random(seed)
    img = art.new()
    for i in range(7):
        a = 3.14159 * (0.15 + 0.7 * i / 6)
        for r in range(2, 9):
            import math
            x = 8 + math.cos(a) * r * 0.9
            y = 15 - math.sin(a) * r
            art.px(img, x, y, art.shade(color, 0.8 + 0.3 * (r % 3 == 0)))
            if rnd.random() < 0.3:
                art.px(img, x + 1, y, art.shade(color, 1.25))
    return art.outline(img, art.shade(color, 0.4))


def snapjaw_open(seed):
    img = art.new()
    for i in range(6):
        art.line(img, 3 + i, 9 - i // 2, 3 + i, 9, (170, 30, 40))
        art.line(img, 13 - i, 9 - i // 2, 13 - i, 9, (170, 30, 40))
    for x in (4, 6, 10, 12):
        art.px(img, x, 6, (250, 245, 230))
    art.line(img, 8, 15, 8, 9, (80, 110, 40))
    art.line(img, 8, 13, 5, 12, (80, 110, 40))
    art.line(img, 8, 13, 11, 12, (80, 110, 40))
    return art.outline(img, (40, 10, 10))


def glowgill_tex(seed):
    img = art.new()
    for x in range(16):
        for y in range(16):
            c = (170, 230, 255) if x % 3 else (90, 160, 210)
            art.px(img, x, y, art.shade(c, 1.0 + 0.1 * ((y * 7 + x) % 5 == 0)))
    return img


def plants():
    s = nseed
    P = art
    # ---- Rootwild
    k_harvest("duskberry_bush", [P.bush((50, 90, 60), 11, i, (80, 45, 120)) for i in range(3)], item=False)
    lang_block("duskberry_bush")
    loot("duskberry_bush", [pool([entry("duskberries")], [SURVIVES]),
                            pool([entry("duskberries", None, [count(1, 2)])], [state_is("duskberry_bush", age=3)])])
    k_cross("glowfern", P.fern((40, 100, 50), (70, 150, 70), 12, tip=(140, 255, 230)))
    loot_shears("glowfern")
    tip("glowfern", "A softly glowing forest fern.\nSpreads slowly in dim light.")
    k_harvest("mendleaf", [P.herb((70, 140, 70), 13, i, vein=(170, 230, 150)) for i in range(3)])
    harvest_loot("mendleaf", "mendleaf_sprig", 1, 2)
    tip("mendleaf", "Medicinal herb of forest floors.\nPick the leaves once it is full grown.")
    k_multiface("glowivy", P.leaves((90, 170, 60), 14, "round", hole=0.55, glow=(200, 255, 150)))
    loot_shears("glowivy")
    tip("glowivy", "Glowing climbing ivy. Grows on walls and ceilings.")
    # ---- Golden Grove
    k_harvest("honeyfruit_bush", [P.bush((150, 140, 50), 15, i, (240, 180, 40), fruit_n=4, fruit_r=1.3) for i in range(3)], item=False)
    lang_block("honeyfruit_bush")
    loot("honeyfruit_bush", [pool([entry("honeyfruit")], [SURVIVES]),
                             pool([entry("honeyfruit")], [state_is("honeyfruit_bush", age=3)])])
    k_cross("goldgrass", P.grass((200, 170, 60), 16, tip=(250, 220, 120)))
    loot_shears("goldgrass", otherwise=entry("glowgrain_seeds", [{"condition": "minecraft:random_chance", "chance": 0.1}]))
    tip("goldgrass", "Golden meadow grass. Its seed heads sometimes hold wild glowgrain.")
    k_harvest("sunleaf", [P.herb((150, 170, 60), 17, i, bloom=(255, 220, 80)) for i in range(3)])
    harvest_loot("sunleaf", "sunleaf_blossom", 1, 1)
    tip("sunleaf", "A rare sun-coloured herb. Needs bright light.")
    # ---- Ashen Hollows
    k_cross("ashgrass", P.grass((100, 100, 100), 18, tip=(220, 220, 215), height=9))
    loot_shears("ashgrass")
    tip("ashgrass", "Ash-dusted grass of volcanic ground.")
    k_states("pyrelily", "blooming", {"false": ("pyrelily", P.flower((90, 40, 30), (230, 110, 40), (255, 220, 100), 19, "trumpet", closed=True)),
                                      "true": ("pyrelily_bloom", P.flower((90, 40, 30), (240, 120, 40), (255, 230, 120), 19, "trumpet"))},
             "pyrelily_bloom")
    loot_self("pyrelily")
    tip("pyrelily", "A volcanic lily. Opens and glows only near heat.")
    k_harvest("emberwort", [P.herb((120, 40, 30), 20, i, vein=(255, 120, 40)) for i in range(3)])
    harvest_loot("emberwort", "emberwort_root", 1, 2)
    tip("emberwort", "Heat-loving medicinal herb. Grows faster near lava or fire.")
    # ---- Crystal Deep
    k_cross("shardgrass", P.grass((90, 200, 210), 21, tip=(200, 255, 255), angular=True, alpha=215, height=9))
    loot_shears("shardgrass")
    tip("shardgrass", "Brittle crystalline grass that grows on mineral ground.")
    k_states("prism_lily", "blooming", {"false": ("prism_lily", P.flower((120, 150, 170), (200, 170, 255), (255, 255, 255), 22, "crystal", closed=True)),
                                        "true": ("prism_lily_bloom", P.flower((120, 150, 170), (200, 170, 255), (255, 255, 255), 22, "crystal"))},
             "prism_lily_bloom")
    loot_self("prism_lily")
    tip("prism_lily", "A translucent crystal flower that opens in darkness.")
    k_cross("ghostfern", P.fern((190, 200, 210), (220, 230, 240), 23, tip=(255, 255, 255), alpha=190))
    loot_shears("ghostfern")
    tip("ghostfern", "A pale translucent fern of dark places.")
    # ---- Abyss
    k_hanging("voidroot", "voidroot_plant", P.strands((30, 20, 40), (120, 60, 160), 24),
              P.strands((30, 20, 40), (30, 20, 40), 25, body=True))
    for b in ("voidroot", "voidroot_plant"):
        loot(b, [pool([entry("voidroot_fiber")], [SURVIVES]), pool([entry("voidroot")], [SHEARS_OR_SILK])])
    lang_block("voidroot_plant", "Voidroot")
    tip("voidroot", "Black roots hanging in the deep dark. Their fibres are tough.")
    k_states("umbral_bloom", "blooming", {"false": ("umbral_bloom", P.flower((40, 30, 50), (110, 50, 160), (200, 160, 255), 26, "round", closed=True)),
                                          "true": ("umbral_bloom_bloom", P.flower((40, 30, 50), (110, 50, 160), (220, 190, 255), 26, "round"))},
             "umbral_bloom_bloom")
    loot("umbral_bloom", [pool([entry("umbral_bloom")], [SURVIVES]),
                          pool([entry("umbral_petal")], [state_is("umbral_bloom", blooming=True)])])
    tip("umbral_bloom", "A flower of the deepest darkness.")
    # ---- Everfrost
    k_states("frostbell", "blooming", {"false": ("frostbell", P.flower((70, 110, 120), (180, 220, 255), (255, 255, 255), 27, "bell", closed=True)),
                                       "true": ("frostbell_bloom", P.flower((70, 110, 120), (180, 220, 255), (255, 255, 255), 27, "bell"))},
             "frostbell_bloom")
    loot_self("frostbell")
    tip("frostbell", "Frozen bell flower. Blooms only in the cold, away from heat.")
    k_harvest("snowberry_bush", [P.bush((80, 120, 110), 28, i, (240, 245, 255)) for i in range(3)], item=False)
    lang_block("snowberry_bush")
    loot("snowberry_bush", [pool([entry("snowberries")], [SURVIVES]),
                            pool([entry("snowberries", None, [count(1, 2)])], [state_is("snowberry_bush", age=3)])])
    k_cross("steamfern", P.fern((50, 90, 60), (80, 130, 90), 29, tip=(220, 230, 230)))
    loot_shears("steamfern")
    tip("steamfern", "A fern that only spreads near warmth in frozen caverns.")
    # ---- Hollow Peaks
    k_harvest("cliffberry_bush", [P.bush((50, 80, 50), 30, i, (200, 40, 50)) for i in range(3)], item=False)
    lang_block("cliffberry_bush")
    loot("cliffberry_bush", [pool([entry("cliffberries")], [SURVIVES]),
                             pool([entry("cliffberries", None, [count(1, 2)])], [state_is("cliffberry_bush", age=3)])])
    k_states("peakstar", "blooming", {"false": ("peakstar", P.flower((80, 110, 70), (250, 250, 255), (250, 220, 90), 31, "star", closed=True)),
                                      "true": ("peakstar_bloom", P.flower((80, 110, 70), (250, 250, 255), (250, 220, 90), 31, "star"))},
             "peakstar_bloom")
    loot_self("peakstar")
    tip("peakstar", "A small mountain star flower. Opens in bright light.")
    k_hanging("ledgevine", "ledgevine_plant", P.strands((60, 120, 50), (110, 170, 80), 32),
              P.strands((60, 120, 50), (60, 120, 50), 33, body=True))
    for b in ("ledgevine", "ledgevine_plant"):
        loot(b, [pool([entry("ledgevine")], [SHEARS_OR_SILK])])
    lang_block("ledgevine_plant", "Ledgevine")
    tip("ledgevine", "Leafy vine hanging from mountain ledges.")
    # ---- Emerald Wetlands
    k_states("bogbloom", "blooming", {"false": ("bogbloom", P.flower((60, 110, 60), (240, 120, 170), (255, 230, 120), 34, "cluster", closed=True)),
                                      "true": ("bogbloom_bloom", P.flower((60, 110, 60), (240, 120, 170), (255, 230, 120), 34, "cluster"))},
             "bogbloom_bloom")
    loot_self("bogbloom")
    tip("bogbloom", "Marsh flower. Blooms when water is close.")
    k_cross("marsh_fern", P.fern((40, 90, 50), (60, 120, 60), 35))
    loot_shears("marsh_fern", otherwise=entry("mire_rice", [{"condition": "minecraft:random_chance", "chance": 0.08}]))
    tip("marsh_fern", "Dense wetland fern. Wild mire rice hides among it.")
    k_harvest("bogwort", [P.herb((80, 130, 80), 36, i, vein=(140, 190, 120)) for i in range(3)])
    harvest_loot("bogwort", "bogwort_leaf", 1, 2)
    tip("bogwort", "Medicinal marsh herb. Needs water right beside it to grow.")
    # ---- Petrified Forest
    k_cross("stoneroot", P.strands((130, 115, 95), (165, 150, 130), 37, n=4))
    loot_self("stoneroot")
    tip("stoneroot", "Roots slowly turning to stone.")
    k_multiface("petrolichen", P.leaves((170, 150, 90), 38, "round", hole=0.5, accent=(120, 100, 60)))
    loot_shears("petrolichen")
    tip("petrolichen", "A mineral-eating crust that grows on stone and petrified wood.")
    # ---- Crimson Basin
    k_harvest("thornbriar", [P.bush((120, 30, 30), 39, i, (255, 140, 60), thorns=(240, 220, 200), fruit_n=4) for i in range(3)], item=False)
    lang_block("thornbriar")
    loot("thornbriar", [pool([entry("briar_fruit")], [SURVIVES]),
                        pool([entry("briar_fruit")], [state_is("thornbriar", age=3)])])
    k_cross("plaguebloom", P.flower((80, 90, 40), (170, 220, 60), (120, 40, 140), 40, "cluster"))
    loot_self("plaguebloom")
    tip("plaguebloom", "Toxic flower. Touching it poisons.")
    k_states("snapjaw", "open", {"true": ("snapjaw", snapjaw_open(41)),
                                 "false": ("snapjaw_closed", P.flower((80, 110, 40), (170, 30, 40), (250, 240, 230), 41, "trumpet", closed=True))},
             "snapjaw")
    loot("snapjaw", [pool([entry("snapjaw_fang")], [SURVIVES]), pool([entry("snapjaw")], [SHEARS_OR_SILK])])
    tip("snapjaw", "Snaps at anything that steps into it. Sneak past carefully.\nShears collect the whole plant.")
    # ---- Stormstone
    k_cross("static_grass", P.grass((60, 80, 190), 42, tip=(150, 200, 255), spark=(230, 240, 255)))
    loot_shears("static_grass")
    tip("static_grass", "Crackling blue grass of the stormstone caverns.")
    k_states("voltbloom", "blooming", {"false": ("voltbloom", P.flower((70, 70, 120), (170, 110, 255), (220, 240, 255), 43, "crystal", closed=True)),
                                       "true": ("voltbloom_bloom", P.flower((70, 70, 120), (170, 110, 255), (230, 245, 255), 43, "crystal"))},
             "voltbloom_bloom")
    loot_self("voltbloom")
    tip("voltbloom", "A mineral flower that blazes when energy flows nearby.")
    k_multiface("storm_lichen", P.leaves((90, 70, 170), 48, "round", hole=0.55, glow=(150, 210, 255)))
    loot_shears("storm_lichen")
    tip("storm_lichen", "A crackling lichen that feeds on charged stone.")
    # ---- Starlight
    k_cross("silvergrass", P.grass((150, 170, 200), 44, tip=(230, 240, 255)))
    loot_shears("silvergrass", otherwise=entry("glowgrain_seeds", [{"condition": "minecraft:random_chance", "chance": 0.1}]))
    tip("silvergrass", "Luminous silver grass. Its seed heads sometimes hold wild glowgrain.")
    k_harvest("moonpetal", [P.herb((150, 160, 180), 45, i, bloom=(250, 250, 255)) for i in range(3)])
    harvest_loot("moonpetal", "moonpetal_petal", 1, 2)
    tip("moonpetal", "A rare silver herb. Needs bright light.")
    k_hanging("starstrand", "starstrand_plant", P.strands((110, 170, 240), (220, 240, 255), 46, glow_dots=(255, 255, 255)),
              P.strands((110, 170, 240), (110, 170, 240), 47, glow_dots=(255, 255, 255), body=True))
    for b in ("starstrand", "starstrand_plant"):
        loot(b, [pool([entry("starstrand")], [SHEARS_OR_SILK])])
    lang_block("starstrand_plant", "Starstrand")
    tip("starstrand", "Glowing strands hanging from starlit ceilings.")
    for b in ("glowfern", "mendleaf", "glowivy", "goldgrass", "sunleaf", "ashgrass", "pyrelily", "emberwort", "shardgrass",
              "prism_lily", "ghostfern", "voidroot", "umbral_bloom", "frostbell", "steamfern", "peakstar", "ledgevine",
              "bogbloom", "marsh_fern", "bogwort", "stoneroot", "petrolichen", "plaguebloom", "snapjaw", "static_grass",
              "voltbloom", "silvergrass", "moonpetal", "starstrand", "storm_lichen"):
        lang_block(b)


def harvest_loot(bid, produce, lo, hi):
    loot(bid, [pool([entry(bid)], [SURVIVES]),
               pool([entry(produce, None, [count(lo, hi)] if hi > lo else None)], [state_is(bid, age=3)])])


def fungi():
    P = art
    k_cross("puffcap", P.mushroom((230, 215, 180), (220, 210, 190), 50, "puff", n=3))
    loot_self("puffcap")
    k_cross("inkcap", P.mushroom((50, 48, 60), (200, 200, 200), 51, "tall", n=2, gill=(20, 20, 30)))
    loot_self("inkcap")
    k_cross("deathbell", P.mushroom((140, 60, 170), (200, 190, 200), 52, "bell", spots=(240, 240, 240)))
    loot_self("deathbell")
    tip("deathbell", "Deadly poisonous. Not food.")
    save_block("lanterncap_0", P.mushroom((110, 200, 240), (200, 220, 230), 53, "dome", scale=0.6, gill=(220, 255, 255)))
    save_block("lanterncap", P.mushroom((120, 210, 255), (200, 220, 230), 53, "dome", n=3, gill=(230, 255, 255)))
    save_block("lanterncap_fading", P.mushroom((120, 140, 150), (170, 170, 170), 53, "dome", n=2))
    for n in ("lanterncap_0", "lanterncap", "lanterncap_fading"):
        bm(n, {"parent": "minecraft:block/cross", "textures": {"cross": tb(n)}})
    bs("lanterncap", {"variants": {"age=0": {"model": tb("lanterncap_0")}, "age=1": {"model": tb("lanterncap")},
                                   "age=2": {"model": tb("lanterncap")}, "age=3": {"model": tb("lanterncap_fading")}}})
    item_gen("lanterncap", tb("lanterncap"))
    loot_self("lanterncap")
    tip("lanterncap", "Glowing fungal colonies that bloom and fade.\nPlaced caps keep glowing.")
    k_states("sporepuff", "spent", {"false": ("sporepuff", P.mushroom((225, 212, 185), (220, 210, 190), 54, "puff", scale=1.35)),
                                    "true": ("sporepuff_spent", P.mushroom((170, 155, 130), (200, 190, 170), 54, "puff", scale=0.9))},
             "sporepuff")
    loot_self("sporepuff")
    tip("sporepuff", "Bursts into a spore cloud when touched.\nThe spores seed new fungi.")
    k_cross("sagecap", P.mushroom((150, 165, 140), (210, 210, 200), 55, "flat", n=2))
    loot_self("sagecap")
    tip("sagecap", "A rare grey-green fungus valued by healers.")
    k_cross("embercap", P.mushroom((230, 110, 40), (200, 170, 150), 56, "dome", gill=(255, 220, 90)))
    loot_self("embercap")
    tip("embercap", "Glowing volcanic fungus. Only spreads near heat.")
    k_cross("rimecap", P.mushroom((170, 220, 240), (220, 230, 240), 57, "dome", n=2, spots=(255, 255, 255)))
    loot_self("rimecap")
    k_cross("gloomcap", P.mushroom((30, 70, 80), (90, 110, 120), 58, "bell", spots=(90, 220, 220)))
    loot_self("gloomcap")
    tip("gloomcap", "Faintly glowing fungus that grows only in darkness.")
    k_cross("calcicap", P.mushroom((235, 235, 225), (210, 210, 200), 59, "flat", n=3))
    loot_self("calcicap")
    tip("calcicap", "Chalky mineral fungus of stony ground. Grinds into bone meal.")
    k_cross("bloodcap", P.mushroom((140, 20, 30), (180, 120, 110), 60, "dome", spots=(230, 90, 90)))
    loot_self("bloodcap")
    tip("bloodcap", "A blood-red fungus of the crimson basins. Toxic.")
    k_cross("tidecap", P.mushroom((60, 200, 210), (170, 220, 220), 61, "dome", n=2, gill=(200, 255, 255)))
    loot_self("tidecap")
    tip("tidecap", "A glowing fungus that grows underwater.")
    k_shelf("bracket_fungus", P.plate((190, 120, 60), 62), P.plate((160, 95, 50), 63, rings=False),
            P.mushroom((200, 125, 60), (170, 120, 80), 64, "flat"))
    loot_self("bracket_fungus")
    tip("bracket_fungus", "Shelf fungus that grows on the sides of logs and rotting wood.")
    k_pillar("rotting_log", P.bark((95, 70, 50), 65, style="furrow", accent=(80, 120, 50)),
             P.log_top((90, 70, 50), (120, 90, 60), 66))
    loot_self("rotting_log")
    tip("rotting_log", "Decaying wood. Fungi sprout from it over time.")
    k_pillar("fungal_stem", P.stripped_side((225, 225, 215), 67), P.log_top((205, 205, 195), (235, 235, 225), 68))
    loot_self("fungal_stem")
    k_cube("sporestalk_cap", P.stone((60, 170, 190), 69, specks=[(200, 255, 255), (230, 255, 255)], n=16))
    k_cube("bellcap_cap", P.stone((120, 70, 160), 70, specks=[(200, 160, 240)], n=14))
    k_cube("shelfcap_cap", P.plate((200, 120, 60), 71))
    k_cube("glowgill", glowgill_tex(72))
    for b in ("sporestalk_cap", "bellcap_cap", "shelfcap_cap", "glowgill"):
        loot_self(b)
    for b in ("puffcap", "inkcap", "deathbell", "lanterncap", "sporepuff", "sagecap", "embercap", "rimecap", "gloomcap",
              "calcicap", "bloodcap", "tidecap", "bracket_fungus", "rotting_log", "fungal_stem", "sporestalk_cap",
              "bellcap_cap", "shelfcap_cap", "glowgill"):
        lang_block(b)


def aquatic():
    P = art
    k_pad("glowpad", P.pad((70, 150, 60), 80, glow=(190, 255, 150)))
    loot_self("glowpad")
    tip("glowpad", "Glowing floating pad. Slowly spreads across still water.")
    k_pad("starlily", P.pad((50, 90, 130), 81, flower_c=(220, 235, 255)))
    loot_self("starlily")
    tip("starlily", "A floating star flower of starlit lakes.")
    k_cross("lakebloom", P.flower((40, 100, 60), (240, 130, 180), (255, 230, 240), 82, "cluster"))
    loot_self("lakebloom")
    tip("lakebloom", "Flowering lake plant. Grows underwater.")
    k_hanging("ribbonweed", "ribbonweed_plant", P.ribbon((50, 120, 60), 83, tip=True), P.ribbon((50, 120, 60), 84))
    k_hanging("glowkelp", "glowkelp_plant", P.ribbon((40, 150, 150), 85, tip=True, glow=(170, 255, 240)),
              P.ribbon((40, 150, 150), 86, glow=(170, 255, 240)))
    for t, b in (("ribbonweed", "ribbonweed_plant"), ("glowkelp", "glowkelp_plant")):
        loot_self(t)
        loot_self(b, t)
        lang_block(b, name_of(t) + " Plant")
    tip("ribbonweed", "Long underwater ribbons. Fish shelter in them.")
    tip("glowkelp", "Luminous kelp that forms underwater forests.")
    k_cross("tidebloom", P.flower((40, 110, 90), (230, 80, 200), (255, 220, 250), 87, "star"))
    loot_self("tidebloom")
    tip("tidebloom", "A glowing underwater flower.")
    k_cube("lanterncoral", P.stone((210, 110, 50), 88, specks=[(255, 220, 120), (255, 200, 90)], n=20))
    loot_self("lanterncoral")
    tip("lanterncoral", "Glowing reef coral.")
    k_cross("lanterncoral_fan", fan((220, 120, 60), 89))
    loot_self("lanterncoral_fan")
    k_cross("spirecoral", P.crystal_sprite((150, 80, 200), (220, 170, 255), 90))
    loot_self("spirecoral")
    k_harvest("sea_grape_bush", [P.bush((40, 120, 70), 91, i, (120, 220, 120), fruit_n=8, fruit_r=0.9) for i in range(3)], item=False)
    lang_block("sea_grape_bush")
    loot("sea_grape_bush", [pool([entry("sea_grapes")], [SURVIVES]),
                            pool([entry("sea_grapes", None, [count(1, 2)])], [state_is("sea_grape_bush", age=3)])])
    k_harvest("siltwort", [P.herb((100, 110, 70), 92, i, vein=(150, 160, 110)) for i in range(3)])
    harvest_loot("siltwort", "siltwort_frond", 1, 1)
    tip("siltwort", "A rare submerged medicinal herb of lake beds.")
    for b in ("glowpad", "starlily", "lakebloom", "ribbonweed", "glowkelp", "tidebloom", "lanterncoral",
              "lanterncoral_fan", "spirecoral", "siltwort"):
        lang_block(b)


CROPS = {
    # block id: (stem colour, kind, fruit colour, glow)
    "duskroots": ((60, 110, 60), "leafy", (110, 60, 140), None),
    "glowgrain_crop": ((150, 160, 60), "grain", (255, 230, 120), (255, 250, 180)),
    "cave_truffles": ((110, 80, 50), "nub", (110, 80, 50), None),
    "mire_rice_crop": ((120, 170, 90), "rice", (230, 220, 170), None),
    "lake_chestnuts": ((40, 110, 60), "rosette", (110, 70, 40), None),
    "emberpeppers": ((70, 110, 40), "pepper", (230, 50, 30), None),
    "frost_turnips": ((90, 150, 130), "leafy", (235, 235, 240), None),
    "sunbeans": ((90, 140, 50), "bean", (240, 210, 60), None),
    "relic_sprout": ((60, 150, 150), "rosette", (130, 255, 230), (200, 255, 240)),
}
CROP_NAMES = {"duskroots": "Duskroots", "glowgrain_crop": "Glowgrain", "cave_truffles": "Cave Truffles",
              "mire_rice_crop": "Mire Rice", "lake_chestnuts": "Lake Chestnuts", "emberpeppers": "Emberpeppers",
              "frost_turnips": "Frost Turnips", "sunbeans": "Sunbeans", "relic_sprout": "Relic Sprout"}
# crop -> (produce, seed, produce count min/max when mature, extra seeds when mature)
CROP_LOOT = {"duskroots": ("duskroot", "duskroot", 2, 4), "glowgrain_crop": ("glowgrain", "glowgrain_seeds", 1, 1),
             "cave_truffles": ("cave_truffle", "cave_truffle", 1, 3), "mire_rice_crop": ("mire_rice", "mire_rice", 2, 4),
             "lake_chestnuts": ("lake_chestnut", "lake_chestnut", 2, 3), "emberpeppers": ("emberpepper", "emberpepper", 1, 3),
             "frost_turnips": ("frost_turnip", "frost_turnip", 2, 3), "sunbeans": ("sunbean", "sunbean", 2, 4),
             "relic_sprout": ("relic_fruit", "ancient_seed", 1, 1)}


def crops():
    for i, (bid, (stem, kind, fruit_c, glow)) in enumerate(CROPS.items()):
        k_crop(bid, [art.crop(stem, 100 + i, st, kind, fruit_c, glow) for st in range(4)])
        lang_block(bid, CROP_NAMES[bid])
        produce, seed, lo, hi = CROP_LOOT[bid]
        mature = state_is(bid, age=7)
        not_mature = {"condition": "minecraft:inverted", "term": mature}
        pools = [pool([entry(seed)], [not_mature, SURVIVES]) if produce != seed else pool([entry(seed)], [not_mature, SURVIVES])]
        if produce == seed:
            pools.append(pool([entry(produce, None, [count(lo, hi), {"function": "minecraft:apply_bonus",
                                                                     "enchantment": "minecraft:fortune",
                                                                     "formula": "minecraft:binomial_with_bonus_count",
                                                                     "parameters": {"extra": 2, "probability": 0.5714286}},
                                                    {"function": "minecraft:explosion_decay"}])], [mature]))
        else:
            pools.append(pool([entry(produce, None, [count(lo, hi)])], [mature]))
            pools.append(pool([entry(seed, None, [{"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                                                   "formula": "minecraft:binomial_with_bonus_count",
                                                   "parameters": {"extra": 2 if bid != "relic_sprout" else 0,
                                                                  "probability": 0.5714286}},
                                                  {"function": "minecraft:explosion_decay"}])], [mature]))
        loot(bid, pools)
        tag("minecraft/tags/block/crops.json", u(bid))
        tag("minecraft/tags/block/maintains_farmland.json", u(bid))


ORES = {
    # id: (host, mineral colour, style, glow, item, min, max)
    "cinnabar_ore": ("basalt", (200, 30, 40), "cluster", None, "cinnabar", 1, 2),
    "sulfur_ore": ("blackstone", (240, 220, 60), "crystals", None, "sulfur", 1, 3),
    "niter_ore": ("stone", (245, 245, 235), "flecks", None, "niter", 1, 3),
    "emberite_ore": ("basalt", (255, 140, 40), "veins", (255, 220, 120), "emberite", 1, 1),
    "cryolite_ore": ("packed_ice", (200, 170, 255), "crystals", None, "cryolite", 1, 2),
    "malachite_ore": ("granite", (40, 170, 90), "bands", None, "malachite", 1, 2),
    "bog_iron_ore": ("mud", (190, 100, 50), "cluster", None, "minecraft:raw_iron", 1, 2),
    "galvanite_ore": ("stormstone", (90, 170, 255), "veins", (220, 240, 255), "galvanite", 1, 2),
    "prismatic_opal_ore": ("calcite", (180, 240, 230), "cluster", (255, 160, 210), "prismatic_opal", 1, 1),
    "jasper_ore": ("tuff", (170, 60, 40), "bands", None, "jasper", 1, 2),
    "fossil_ore": ("sandstone", (240, 235, 220), "bones", None, "fossil_fragment", 1, 2),
    "umbralite_ore": ("deepslate", (70, 30, 100), "crystals", (180, 120, 255), "raw_umbralite", 1, 1),
    "nacre_ore": ("seabed", (240, 235, 250), "cluster", (230, 200, 255), "pearl", 1, 1),
    "starsilver_ore": ("darkstone", (220, 225, 240), "stars", None, "raw_starsilver", 1, 1),
}
HOSTS = {"basalt": (72, 72, 78), "blackstone": (42, 38, 44), "stone": (125, 125, 125), "packed_ice": (150, 180, 230),
         "granite": (150, 105, 85), "mud": (72, 58, 48), "stormstone": (44, 46, 86), "calcite": (220, 222, 218),
         "tuff": (105, 105, 95), "sandstone": (210, 195, 150), "deepslate": (62, 62, 68), "seabed": (110, 125, 120),
         "darkstone": (78, 82, 98)}
ORE_TIER = {"cinnabar_ore": "stone", "cryolite_ore": "stone", "malachite_ore": "stone", "jasper_ore": "stone",
            "nacre_ore": "stone", "emberite_ore": "iron", "galvanite_ore": "iron", "prismatic_opal_ore": "iron",
            "starsilver_ore": "iron", "umbralite_ore": "diamond"}
STORAGE = {"prismatic_opal_block": ((190, 240, 230), "facets", "prismatic_opal"), "jasper_block": ((170, 70, 50), "facets", "jasper"),
           "malachite_block": ((50, 170, 100), "plain", "malachite"), "cryolite_block": ((200, 180, 250), "facets", "cryolite"),
           "galvanite_block": ((90, 160, 240), "metal", "galvanite"), "starsilver_block": ((215, 220, 235), "metal", "starsilver_ingot"),
           "umbralite_block": ((70, 40, 100), "metal", "umbralite_ingot")}


def minerals():
    for i, (bid, (host, col, style, glow, item, lo, hi)) in enumerate(ORES.items()):
        h = art.stone(HOSTS[host], 200 + i)
        k_cube(bid, art.ore(h, col, 300 + i, style, glow))
        loot_ore(bid, item, lo, hi)
        if bid == "fossil_ore":
            r = json.load(open(os.path.join(DATA, NS, "loot_table", "blocks", bid + ".json")))
            r["pools"].append(pool([entry("ancient_seed")], [NO_SILK, SURVIVES,
                                                             {"condition": "minecraft:random_chance", "chance": 0.03}]))
            loot(bid, r["pools"])
        lang_block(bid)
        tag("minecraft/tags/block/mineable/pickaxe.json", u(bid))
        tier = ORE_TIER.get(bid)
        if tier:
            tag(f"minecraft/tags/block/needs_{tier}_tool.json", u(bid))
    for i, (bid, (col, style, item)) in enumerate(STORAGE.items()):
        k_cube(bid, art.gem_block(col, 400 + i, style))
        loot_self(bid)
        lang_block(bid, name_of(bid).replace("Prismatic Opal Block", "Block of Prismatic Opal"))
        tag("minecraft/tags/block/mineable/pickaxe.json", u(bid))
        tag("minecraft/tags/block/needs_stone_tool.json", u(bid))
        shaped(bid, ["###", "###", "###"], {"#": item}, bid, 1, "building")
        shapeless(f"{item}_from_{bid}", [bid], item, 9)
    k_cluster("glimmer_bud", art.crystal_sprite((240, 230, 150), (255, 255, 220), 500, small=True))
    k_cluster("glimmer_cluster", art.crystal_sprite((240, 230, 150), (255, 255, 220), 501))
    k_cube("budding_glimmer", art.stone((225, 215, 170), 502, specks=[(255, 250, 200), (250, 240, 170)], n=18))
    loot("glimmer_bud", [pool([entry("glimmer_bud", [SILK])])])
    loot("glimmer_cluster", [pool([{"type": "minecraft:alternatives", "children": [
        entry("glimmer_cluster", [SILK]),
        entry("glimmer_shard", [{"condition": "minecraft:match_tool", "predicate": {"items": "#minecraft:cluster_max_harvestables"}}],
              [{"function": "minecraft:set_count", "count": 4},
               {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"}]),
        entry("glimmer_shard", None, [{"function": "minecraft:set_count", "count": 2}, {"function": "minecraft:explosion_decay"}])]}])])
    loot("budding_glimmer", [pool([entry("budding_glimmer", [SILK])])])
    for b in ("glimmer_bud", "glimmer_cluster", "budding_glimmer"):
        lang_block(b)
        tag("minecraft/tags/block/mineable/pickaxe.json", u(b))
    tip("budding_glimmer", "Grows glimmer crystals on its open faces in darkness.\nSilk touch moves it.")
    # resin knot
    k_knot("resin_knot", [art.stone((150, 104, 40), 503 + a, specks=[(250, 200, 90)], n=[2, 8, 24][a]) for a in range(3)])
    loot_self("resin_knot")
    lang_block("resin_knot")
    tip("resin_knot", "Slowly fills with amber resin. Collect it when it glows.")
    for b in ("rotting_log", "fungal_stem", "sporestalk_cap", "bellcap_cap", "shelfcap_cap", "bracket_fungus", "resin_knot"):
        tag("minecraft/tags/block/mineable/axe.json", u(b))
    tag("minecraft/tags/block/mineable/hoe.json", u("glowgill"))
    tag("minecraft/tags/block/mineable/pickaxe.json", u("lanterncoral"))


# ------------------------------------------------------------------------------------------- items

ITEMS = {
    # id: (image factory, name or None, tooltip or None)
    "duskberries": (lambda: art.berries((80, 45, 120), 600), None, "Edible. Plant them to grow a duskberry bush."),
    "honeyfruit": (lambda: art.fruit((240, 175, 50), 601), None, "Sweet and restorative. Plant it to grow a bush."),
    "snowberries": (lambda: art.berries((235, 240, 250), 602), None, "Edible. Bushes only grow in the cold."),
    "cliffberries": (lambda: art.berries((200, 40, 50), 603), None, "Edible mountain berries."),
    "briar_fruit": (lambda: art.fruit((250, 110, 50), 604, leaf=(120, 30, 30), r=3.8), None,
                    "Filling, but thorny bushes guard it. Occasionally mildly toxic."),
    "sea_grapes": (lambda: art.berries((120, 220, 110), 605, leaf=(40, 120, 70), n=6), None,
                   "Edible underwater fruit. Plant underwater."),
    "duskroot": (lambda: art.root((110, 60, 140), 606), None, "A staple root. Better baked. Plant on farmland."),
    "baked_duskroot": (lambda: art.root((150, 90, 60), 607, baked=True), None, None),
    "glowgrain_seeds": (lambda: art.seeds((230, 210, 110), 608), None, "Grows in bright light."),
    "glowgrain": (lambda: art.grain_ear((240, 215, 110), 609), None, "Bake three into bread."),
    "glowgrain_bread": (lambda: art.bread((215, 170, 90), 610), None, None),
    "cave_truffle": (lambda: art.truffle((110, 80, 55), 611), None, "Grows in darkness on farmland or soil. Better roasted."),
    "roasted_truffle": (lambda: art.truffle((80, 55, 35), 612), None, None),
    "mire_rice": (lambda: art.seeds((235, 230, 200), 613, n=9), None, "Needs water right beside it to grow."),
    "rice_bowl": (lambda: art.bowl((245, 245, 235), 614), None, None),
    "lake_chestnut": (lambda: art.nut((110, 75, 45), 615, spiky=True), None, "Plant it on a lake bed. Better roasted."),
    "roasted_chestnut": (lambda: art.nut((90, 55, 30), 616), None, None),
    "emberpepper": (lambda: art.pepper((230, 50, 30), 617), None, "Fiery. Grows much faster near heat."),
    "frost_turnip": (lambda: art.bulb((235, 235, 240), 618, top2=(170, 120, 200)), None,
                     "Hardy and slow. Grows only in the cold, away from heat."),
    "baked_turnip": (lambda: art.bulb((220, 180, 120), 619, top=(120, 100, 60)), None, None),
    "sunbean": (lambda: art.pod((240, 210, 60), 620), None, "Upsets the stomach raw; cook in a stew. Needs bright light."),
    "sunbean_stew": (lambda: art.bowl((210, 170, 60), 621, bits=[(240, 210, 60)]), None, None),
    "ancient_seed": (lambda: ancient_seed_img(622), None, "A seed sealed in stone for ages."),
    "relic_fruit": (lambda: art.fruit((120, 250, 220), 623, leaf=(60, 150, 150)), None, None),
    "roasted_inkcap": (lambda: art.mushroom((90, 70, 60), (200, 180, 160), 624, "tall"), None, None),
    "cavern_stew": (lambda: art.bowl((150, 110, 80), 625, bits=[(230, 215, 180), (60, 40, 40)]), None, None),
    "mendleaf_sprig": (lambda: art.leaf_item((80, 160, 80), 626, vein=(170, 230, 150)), None, "A medicinal leaf."),
    "sunleaf_blossom": (lambda: art.blossom((255, 210, 70), (255, 250, 200), 627), None, "A rare medicinal blossom."),
    "emberwort_root": (lambda: art.root((150, 50, 30), 628, top=(90, 40, 30)), None, "A medicinal root that holds heat."),
    "bogwort_leaf": (lambda: art.leaf_item((90, 140, 90), 629), None, "A medicinal marsh leaf."),
    "siltwort_frond": (lambda: art.leaf_item((110, 120, 80), 630, n=2), None, "A rare medicinal frond."),
    "moonpetal_petal": (lambda: art.petal((240, 240, 255), 631), None, "Said to sharpen the eyes in darkness."),
    "snapjaw_fang": (lambda: art.fang((240, 230, 210), 632), None, "A fang from a predatory plant."),
    "voidroot_fiber": (lambda: art.fiber((70, 50, 90), 633), None, "Tough fibre. Three twist into string."),
    "umbral_petal": (lambda: art.petal((110, 50, 160), 634), None, "A strange petal. Its uses are unknown."),
    "amber_glob": (lambda: art.glob((230, 140, 40), 635), None, "Sticky resin. Burns well."),
    "mendleaf_poultice": (lambda: art.poultice((90, 170, 80), 636), None, "Apply to slowly heal wounds."),
    "emberwort_tonic": (lambda: art.bottle((255, 120, 40), 637), None, "Protects from fire and lava for a while."),
    "antidote_tincture": (lambda: art.bottle((120, 220, 160), 638), None, "Cures poison and other harmful effects."),
    "moonpetal_draught": (lambda: art.bottle((180, 200, 255), 639, glow=True), None, "Lets you see in the dark for a while."),
    "sunleaf_salve": (lambda: art.salve((250, 200, 70), 640), None, "Hardens the body against harm for a while."),
    "cinnabar": (lambda: art.dust((200, 30, 40), 641), None, "A red mineral. Ground into dye; valued by alchemists."),
    "sulfur": (lambda: art.dust((240, 220, 60), 642), None, "Volcanic mineral. An ingredient of gunpowder."),
    "niter": (lambda: art.dust((245, 245, 235), 643), None, "Forms where organic matter decays. An ingredient of gunpowder."),
    "emberite": (lambda: art.gem((255, 140, 40), 644, "crystal"), None, "Burns for a very long time."),
    "cryolite": (lambda: art.gem((200, 170, 255), 645, "crystal"), None, "Always cold to the touch. Freezes water."),
    "malachite": (lambda: art.gem((40, 170, 90), 646, "round"), None, "A copper mineral. Smelts into copper."),
    "galvanite": (lambda: art.gem((90, 170, 255), 647, "shard"), None, "A conductive mineral that hums faintly."),
    "prismatic_opal": (lambda: art.gem((190, 240, 230), 648, "cut"), None, "A rare shimmering gemstone."),
    "jasper": (lambda: art.gem((170, 60, 40), 649, "cut"), None, "A banded stone from fossil forests."),
    "fossil_fragment": (lambda: art.fragment((235, 230, 210), 650), None, "Ancient bone. Grinds into bone meal."),
    "raw_umbralite": (lambda: art.raw_chunk((70, 30, 100), 651), None, "A rare metal of the deep dark."),
    "umbralite_ingot": (lambda: art.ingot((90, 50, 130), 652), None, "A heavy dark metal."),
    "pearl": (lambda: art.gem((240, 235, 250), 653, "round"), None, "From nacre beds under water."),
    "raw_starsilver": (lambda: art.raw_chunk((210, 215, 230), 654), None, "A bright metal from starlit caverns."),
    "starsilver_ingot": (lambda: art.ingot((220, 225, 240), 655), None, "A light, bright metal."),
    "glimmer_shard": (lambda: art.gem((245, 235, 160), 656, "shard"), None, "A glowing crystal shard."),
}
TOOLTIP_BLOCK_ITEMS = {
    "puffcap": "Edible raw.", "inkcap": "Toxic raw. Safe once roasted.", "rimecap": "Edible, but numbing.",
    "glowpad": None, "starlily": None,
}


def ancient_seed_img(seed):
    img = art.nut((95, 110, 100), seed)
    for x, y in ((7, 8), (9, 9), (8, 10), (6, 10)):
        art.px(img, x, y, (130, 255, 230))
    return img


def items():
    for iid, (factory, name, tooltip) in ITEMS.items():
        save_item(iid, factory())
        item_gen(iid, f"{NS}:item/{iid}")
        lang_item(iid, name)
        if tooltip:
            tip(iid, tooltip)
    for iid, text in TOOLTIP_BLOCK_ITEMS.items():
        if text:
            tip(iid, text)
    LANG["item.underdeep.puffcap"] = "Puffcap"
    LANG["item.underdeep.inkcap"] = "Inkcap"
    LANG["item.underdeep.rimecap"] = "Rimecap"
    LANG["item.underdeep.glowpad"] = "Glowpad"
    LANG["item.underdeep.starlily"] = "Starlily"
    for h in ("mendleaf_sprig", "sunleaf_blossom", "emberwort_root", "bogwort_leaf", "siltwort_frond", "moonpetal_petal"):
        tag(f"{NS}/tags/item/herbs.json", u(h))


def recipes():
    for src, out in (("duskroot", "baked_duskroot"), ("cave_truffle", "roasted_truffle"), ("lake_chestnut", "roasted_chestnut"),
                     ("frost_turnip", "baked_turnip"), ("inkcap", "roasted_inkcap")):
        cook(out, src, out)
    shaped("glowgrain_bread", ["###"], {"#": "glowgrain"}, "glowgrain_bread", 1, "misc")
    shapeless("rice_bowl", ["minecraft:bowl", "mire_rice", "mire_rice", "mire_rice"], "rice_bowl")
    shapeless("sunbean_stew", ["minecraft:bowl", "sunbean", "sunbean", "sunbean", "duskroot"], "sunbean_stew")
    shapeless("cavern_stew", ["minecraft:bowl", "puffcap", "roasted_inkcap", "cave_truffle", "duskroot"], "cavern_stew")
    shapeless("mendleaf_poultice", ["mendleaf_sprig", "mendleaf_sprig", "mendleaf_sprig", "minecraft:paper"], "mendleaf_poultice")
    shapeless("emberwort_tonic", ["minecraft:glass_bottle", "emberwort_root", "emberpepper"], "emberwort_tonic")
    shapeless("antidote_tincture", ["minecraft:glass_bottle", "bogwort_leaf", "bracket_fungus"], "antidote_tincture")
    shapeless("antidote_tincture_siltwort", ["minecraft:glass_bottle", "siltwort_frond"], "antidote_tincture", 2)
    shapeless("moonpetal_draught", ["minecraft:glass_bottle", "moonpetal_petal", "gloomcap"], "moonpetal_draught")
    shapeless("sunleaf_salve", ["sunleaf_blossom", "honeyfruit", "amber_glob"], "sunleaf_salve")
    shapeless("gunpowder_from_sulfur", ["sulfur", "niter", "#minecraft:coals"], "minecraft:gunpowder", 3)
    shapeless("blaze_powder_from_emberite", ["emberite", "sulfur"], "minecraft:blaze_powder", 2)
    shapeless("red_dye_from_cinnabar", ["cinnabar"], "minecraft:red_dye", 2)
    shapeless("green_dye_from_malachite", ["malachite"], "minecraft:green_dye", 2)
    shapeless("white_dye_from_pearl", ["pearl"], "minecraft:white_dye", 1)
    shapeless("bone_meal_from_fossil", ["fossil_fragment"], "minecraft:bone_meal", 3)
    shapeless("bone_meal_from_calcicap", ["calcicap"], "minecraft:bone_meal", 2)
    shapeless("redstone_from_galvanite", ["galvanite"], "minecraft:redstone", 3)
    shapeless("ice_from_cryolite", ["cryolite", "minecraft:water_bucket"], "minecraft:ice", 4)
    shapeless("string_from_voidroot", ["voidroot_fiber", "voidroot_fiber", "voidroot_fiber"], "minecraft:string", 1)
    shapeless("sticky_piston_from_amber", ["minecraft:piston", "amber_glob"], "minecraft:sticky_piston", 1, "redstone")
    shaped("amber_resin", ["##", "##"], {"#": "amber_glob"}, "amber_resin", 1, "building")
    shaped("spyglass_from_glimmer", [" G ", " C ", " C "], {"G": "glimmer_shard", "C": "minecraft:copper_ingot"}, "minecraft:spyglass")
    shaped("poison_arrows_from_deathbell", ["AAA", "ADA", "AAA"], {"A": "minecraft:arrow", "D": "deathbell"}, "minecraft:tipped_arrow", 8)
    # tipped arrow needs its potion component
    p = os.path.join(DATA, NS, "recipe", "poison_arrows_from_deathbell.json")
    r = json.load(open(p))
    r["result"]["components"] = {"minecraft:potion_contents": {"potion": "minecraft:poison"}}
    wjson(p, r)
    cook("copper_from_malachite", "malachite", "minecraft:copper_ingot", 0.7, food=False, blast=True)
    cook("umbralite_ingot", "raw_umbralite", "umbralite_ingot", 1.0, food=False, blast=True)
    cook("starsilver_ingot", "raw_starsilver", "starsilver_ingot", 0.8, food=False, blast=True)
    cook("iron_from_bog_iron", "bog_iron_ore", "minecraft:iron_ingot", 0.7, food=False, blast=True)
    cook("charcoal_from_rotting_log", "rotting_log", "minecraft:charcoal", 0.15, food=False)


SOIL_TAGS = {
    "plant_soil": ["#minecraft:dirt", "underdeep:glowmoss", "underdeep:amber_moss", "underdeep:starmoss", "underdeep:sporeglow_soil"],
    "fungal_soil": ["#underdeep:plant_soil", "underdeep:rotting_log", "minecraft:crimson_nylium", "minecraft:warped_nylium",
                    "minecraft:soul_soil", "underdeep:ember_nylium"],
    "volcanic_soil": ["minecraft:basalt", "minecraft:smooth_basalt", "minecraft:blackstone", "minecraft:magma_block",
                      "minecraft:netherrack", "minecraft:soul_soil", "minecraft:soul_sand", "minecraft:crimson_nylium",
                      "minecraft:warped_nylium", "underdeep:ember_nylium", "minecraft:gilded_blackstone",
                      "minecraft:red_sand", "minecraft:nether_wart_block", "#underdeep:plant_soil"],
    "frozen_soil": ["minecraft:snow_block", "minecraft:packed_ice", "minecraft:blue_ice", "minecraft:ice", "#underdeep:plant_soil"],
    "mineral_soil": ["minecraft:stone", "minecraft:deepslate", "minecraft:cobbled_deepslate", "minecraft:tuff", "minecraft:calcite",
                     "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:gravel",
                     "minecraft:dripstone_block", "minecraft:amethyst_block", "underdeep:luminite_block", "underdeep:stormstone",
                     "underdeep:charged_stormstone", "underdeep:petrified_wood", "minecraft:sandstone", "minecraft:terracotta",
                     "minecraft:red_terracotta", "minecraft:smooth_basalt", "minecraft:sculk", "minecraft:cobblestone",
                     "minecraft:packed_ice", "minecraft:snow_block", "underdeep:starstone", "#underdeep:plant_soil"],
    "wet_soil": ["minecraft:mud", "minecraft:clay", "minecraft:muddy_mangrove_roots", "minecraft:packed_mud", "#underdeep:plant_soil"],
    "aquatic_bed": ["minecraft:sand", "minecraft:red_sand", "minecraft:gravel", "minecraft:clay", "minecraft:mud", "#minecraft:dirt",
                    "minecraft:prismarine", "minecraft:dark_prismarine", "minecraft:stone", "minecraft:deepslate", "minecraft:calcite",
                    "minecraft:tuff", "minecraft:sandstone", "underdeep:lanterncoral", "minecraft:tube_coral_block",
                    "minecraft:brain_coral_block", "minecraft:bubble_coral_block", "minecraft:fire_coral_block",
                    "minecraft:horn_coral_block", "minecraft:dripstone_block", "minecraft:andesite", "minecraft:granite",
                    "minecraft:diorite", "underdeep:glowmoss", "underdeep:starmoss", "minecraft:moss_block"],
    "fungal_convertible": ["minecraft:dirt", "minecraft:grass_block", "minecraft:coarse_dirt", "minecraft:moss_block",
                           "minecraft:rooted_dirt", "minecraft:podzol"],
    "shelf_fungus_hosts": ["#minecraft:logs", "underdeep:rotting_log", "underdeep:fungal_stem", "underdeep:petrified_wood"],
    "growth_blockers": ["#minecraft:planks", "#minecraft:wooden_slabs", "#minecraft:wooden_stairs", "#minecraft:doors",
                        "#minecraft:trapdoors", "#minecraft:fences", "#minecraft:fence_gates", "#minecraft:beds",
                        "#minecraft:rails", "#minecraft:wool", "#minecraft:wool_carpets", "#minecraft:all_signs",
                        "#minecraft:candles", "#minecraft:slabs", "#minecraft:stairs", "#minecraft:walls",
                        "#minecraft:shulker_boxes", "#minecraft:anvil", "#minecraft:campfires", "#minecraft:buttons",
                        "#minecraft:pressure_plates", "#minecraft:flower_pots",
                        "minecraft:glass", "minecraft:glass_pane", "minecraft:chest", "minecraft:trapped_chest",
                        "minecraft:barrel", "minecraft:crafting_table", "minecraft:furnace", "minecraft:blast_furnace",
                        "minecraft:smoker", "minecraft:farmland", "minecraft:dirt_path", "minecraft:torch",
                        "minecraft:wall_torch", "minecraft:soul_torch", "minecraft:soul_wall_torch", "minecraft:lantern",
                        "minecraft:soul_lantern", "minecraft:redstone_wire", "minecraft:repeater", "minecraft:comparator",
                        "minecraft:lever", "minecraft:hopper", "minecraft:dropper", "minecraft:dispenser", "minecraft:observer",
                        "minecraft:piston", "minecraft:sticky_piston", "minecraft:redstone_torch", "minecraft:redstone_wall_torch",
                        "minecraft:bricks", "minecraft:stone_bricks", "minecraft:ladder", "minecraft:composter",
                        "minecraft:bookshelf", "minecraft:lectern", "minecraft:cartography_table", "minecraft:fletching_table",
                        "minecraft:smithing_table", "minecraft:loom", "minecraft:stonecutter", "minecraft:grindstone",
                        "minecraft:brewing_stand", "minecraft:cauldron", "minecraft:enchanting_table", "minecraft:jukebox",
                        "minecraft:note_block", "minecraft:target", "minecraft:hay_block", "minecraft:scaffolding",
                        "minecraft:iron_bars", "minecraft:chain", "minecraft:polished_andesite", "minecraft:polished_diorite",
                        "minecraft:polished_granite", "minecraft:smooth_stone", "minecraft:white_concrete",
                        "minecraft:terracotta", "minecraft:beehive", "minecraft:bee_nest"],
}


def misc_tags():
    for name, values in SOIL_TAGS.items():
        tag(f"{NS}/tags/block/{name}.json", *values)
    tag(f"{NS}/tags/block/ore_hosts.json", "#minecraft:base_stone_overworld", "minecraft:basalt", "minecraft:blackstone",
        "minecraft:calcite", "minecraft:packed_ice", "minecraft:mud", "minecraft:sandstone", "minecraft:netherrack",
        "minecraft:smooth_basalt", "minecraft:clay", "minecraft:prismarine", "minecraft:sand", "minecraft:gravel",
        "minecraft:dripstone_block", "underdeep:petrified_wood")
    plants_all = ["glowfern", "mendleaf", "goldgrass", "sunleaf", "ashgrass", "pyrelily", "emberwort", "shardgrass", "prism_lily",
                  "ghostfern", "umbral_bloom", "frostbell", "steamfern", "peakstar", "bogbloom", "marsh_fern", "bogwort", "stoneroot",
                  "plaguebloom", "snapjaw", "static_grass", "voltbloom", "silvergrass", "moonpetal", "puffcap", "inkcap", "deathbell",
                  "lanterncap", "sporepuff", "sagecap", "embercap", "rimecap", "gloomcap", "calcicap", "bloodcap",
                  "duskberry_bush", "honeyfruit_bush", "snowberry_bush", "cliffberry_bush", "thornbriar"]
    for p in plants_all:
        tag("minecraft/tags/block/sword_efficient.json", u(p))
    for p in ("glowfern", "goldgrass", "ashgrass", "shardgrass", "ghostfern", "steamfern", "marsh_fern", "static_grass", "silvergrass"):
        tag("minecraft/tags/block/replaceable_by_trees.json", u(p))
    for p in ("pyrelily", "prism_lily", "frostbell", "peakstar", "bogbloom", "voltbloom", "plaguebloom", "moonpetal", "sunleaf"):
        tag("minecraft/tags/block/flowers.json", u(p))
    for p in ("glowivy", "petrolichen", "storm_lichen"):
        tag("minecraft/tags/block/mineable/axe.json", u(p))
    for p in ("voidroot", "voidroot_plant", "ledgevine", "ledgevine_plant", "starstrand", "starstrand_plant"):
        tag("minecraft/tags/block/climbable.json", u(p))


def merge_outputs():
    lang_path = os.path.join(ASSETS, "lang", "en_us.json")
    lang = json.load(open(lang_path))
    lang.update(LANG)
    lang.update(TOOLTIPS)
    lang["itemGroup.underdeep"] = "Underdeep"
    wjson(lang_path, dict(sorted(lang.items())))
    for path, values in TAGS.items():
        full = os.path.join(DATA, path)
        existing = []
        if os.path.exists(full):
            existing = json.load(open(full)).get("values", [])
        merged = existing + [v for v in values if v not in existing]
        wjson(full, {"replace": False, "values": merged})


def main():
    base.main()
    shutil_dirs = [os.path.join(DATA, NS, "recipe")]
    import shutil
    for d in shutil_dirs:
        shutil.rmtree(d, ignore_errors=True)
    for name, w in WOODS.items():
        wood_set(name, w)
    extra_trees()
    plants()
    fungi()
    aquatic()
    crops()
    minerals()
    items()
    recipes()
    misc_tags()
    import creatures
    clang, ctags = creatures.main()
    LANG.update(clang)
    import inhabitants
    LANG.update(inhabitants.main())
    import stage7
    LANG.update(stage7.main(LANG))
    import stage8
    LANG.update(stage8.main(LANG))
    import stage9
    LANG.update(stage9.main(LANG))
    import stage10
    LANG.update(stage10.main(LANG))
    import stage11
    LANG.update(stage11.main(LANG))
    for path, vals in ctags.items():
        tag(path, *vals)
    merge_outputs()
    # Stage 11.5: the art-directed texture remake replaces generated placeholder art (must run last)
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "textures"))
    import remake
    remake.main()
    print(f"stage 2 assets: {len(LANG)} names, {len(TOOLTIPS)} hints, {len(TAGS)} tag files")


if __name__ == "__main__":
    main()
