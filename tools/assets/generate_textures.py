#!/usr/bin/env python3
"""Procedurally generates Underdeep's Stage 1 placeholder-quality textures (16x16) and mod icon.
Run from the repository root: python3 tools/assets/generate_textures.py"""
import os, random
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "underdeep")
TEX = os.path.join(OUT, "textures", "block")
os.makedirs(TEX, exist_ok=True)


def clamp(v):
    return max(0, min(255, int(v)))


def luminite_block():
    rnd = random.Random(1)
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            # faceted diagonal bands
            band = ((x + y) // 3 + (x - y) // 5) % 4
            base = [(30, 120, 140), (50, 170, 180), (90, 220, 220), (40, 150, 165)][band]
            n = rnd.randint(-12, 12)
            img.putpixel((x, y), (clamp(base[0] + n), clamp(base[1] + n), clamp(base[2] + n), 255))
    for (x, y) in [(3, 4), (11, 2), (7, 10), (13, 12), (2, 13)]:
        img.putpixel((x, y), (220, 255, 255, 255))
    img.save(os.path.join(TEX, "luminite_block.png"))


def luminite_cluster():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    crystals = [(4, 15, 2, 6), (8, 15, 3, 1), (12, 15, 2, 5), (6, 15, 1, 9), (10, 15, 1, 8)]
    for cx, base, half, top in crystals:
        d.polygon([(cx - half, base), (cx + half, base), (cx, top)], fill=(60, 190, 200, 255))
        d.line([(cx, base), (cx, top)], fill=(200, 255, 255, 255))
    img.save(os.path.join(TEX, "luminite_cluster.png"))


def glowcap():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([7, 8, 8, 15], fill=(200, 210, 190, 255))
    d.ellipse([3, 3, 12, 10], fill=(70, 200, 230, 255))
    d.ellipse([5, 4, 10, 7], fill=(170, 250, 255, 255))
    for p in [(4, 7), (11, 6), (8, 9)]:
        img.putpixel(p, (230, 255, 255, 255))
    img.save(os.path.join(TEX, "glowcap.png"))


def icon():
    rnd = random.Random(7)
    img = Image.new("RGBA", (128, 128), (12, 12, 20, 255))
    d = ImageDraw.Draw(img)
    # cavern silhouette with glowing crystals
    d.ellipse([10, 20, 118, 110], fill=(28, 46, 40, 255))
    d.polygon([(10, 128), (40, 90), (64, 105), (90, 80), (118, 128)], fill=(18, 24, 22, 255))
    for _ in range(14):
        x = rnd.randint(20, 108); y = rnd.randint(30, 60)
        d.polygon([(x - 3, 20 + rnd.randint(0, 8)), (x + 3, 20 + rnd.randint(0, 8)), (x, y)], fill=(36, 52, 46, 255))
    for x, y in [(40, 92), (64, 104), (88, 84)]:
        d.polygon([(x - 6, y + 4), (x + 6, y + 4), (x, y - 18)], fill=(70, 210, 220, 255))
    img.save(os.path.join(OUT, "icon.png"))


luminite_block(); luminite_cluster(); glowcap(); icon()
print("textures written")
