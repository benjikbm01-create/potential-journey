#!/usr/bin/env python3
"""Block-level diff of the same chunks in two saved worlds (Anvil format, MC 1.21).
Usage: python3 tools/analysis/world_diff.py <worldA> <worldB> <cx0> <cz0> <cx1> <cz1>
Requires: pip install nbtlib"""
import io, math, os, struct, sys, zlib, collections
import nbtlib


def read_chunk(world, cx, cz):
    path = os.path.join(world, "region", f"r.{cx >> 5}.{cz >> 5}.mca")
    with open(path, "rb") as f:
        data = f.read()
    idx = 4 * ((cx & 31) + (cz & 31) * 32)
    off = int.from_bytes(data[idx:idx + 3], "big") * 4096
    if off == 0:
        return None
    length = struct.unpack(">I", data[off:off + 4])[0]
    comp = data[off + 4]
    raw = data[off + 5:off + 4 + length]
    raw = zlib.decompress(raw) if comp == 2 else raw
    return nbtlib.File.parse(io.BytesIO(raw))


def blocks(chunk):
    out = {}
    for sec in chunk["sections"]:
        y = int(sec["Y"])
        bs = sec.get("block_states")
        if bs is None:
            continue
        palette = [p["Name"] + str(dict(p.get("Properties", {}))) for p in bs["palette"]]
        if "data" not in bs:
            arr = [0] * 4096
        else:
            bits = max(4, math.ceil(math.log2(len(palette))))
            per = 64 // bits
            mask = (1 << bits) - 1
            arr = []
            for v in bs["data"]:
                v = int(v) & 0xFFFFFFFFFFFFFFFF
                for i in range(per):
                    arr.append((v >> (i * bits)) & mask)
            arr = arr[:4096]
        out[y] = [palette[i] for i in arr]
    return out


ROCK = {"minecraft:" + n for n in ["stone", "deepslate", "andesite", "granite", "diorite", "tuff", "calcite",
        "blackstone", "basalt", "smooth_basalt", "dripstone_block", "bedrock"]}


def terrain_class(name):
    if name in ROCK:
        return "rock"
    if name in ("minecraft:air", "minecraft:cave_air"):
        return "air"
    if name in ("minecraft:water", "minecraft:lava"):
        return name
    return None  # decoration / surface / ore: ignored at terrain level


def main():
    a, b = sys.argv[1], sys.argv[2]
    cx0, cz0, cx1, cz1 = map(int, sys.argv[3:7])
    total = diff = 0
    terrain_total = [0]
    kinds = collections.Counter()
    terrain_kinds = collections.Counter()
    for cx in range(cx0, cx1 + 1):
        for cz in range(cz0, cz1 + 1):
            ca, cb = read_chunk(a, cx, cz), read_chunk(b, cx, cz)
            if ca is None or cb is None:
                print("missing chunk", cx, cz)
                continue
            ba, bb = blocks(ca), blocks(cb)
            for y in ba:
                for i, (x1, x2) in enumerate(zip(ba[y], bb.get(y, ba[y]))):
                    total += 1
                    t1, t2 = terrain_class(x1.split("{")[0]), terrain_class(x2.split("{")[0])
                    if t1 is not None and t2 is not None:
                        terrain_total[0] += 1
                        if t1 != t2:
                            terrain_kinds[f"{t1} <-> {t2}"] += 1
                    if x1 != x2:
                        diff += 1
                        n1, n2 = x1.split("{")[0], x2.split("{")[0]
                        if n1 == n2:
                            kinds["(same block, different state) " + n1] += 1
                        else:
                            kinds[f"{n1} <-> {n2}"] += 1
    print(f"blocks compared: {total}, differing: {diff} ({diff * 100.0 / max(1, total):.4f}%)")
    for k, n in kinds.most_common(int(os.environ.get("TOP", "15"))):
        print(f"  {n:7d}  {k}")
    td = sum(terrain_kinds.values())
    print(f"terrain-level comparison (rock/air/water/lava where both sides are terrain blocks): "
          f"{terrain_total[0]} compared, {td} differ")
    for k, n in terrain_kinds.most_common(8):
        print(f"  {n:7d}  {k}")


main()
