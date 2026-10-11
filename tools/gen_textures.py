#!/usr/bin/env python3
"""Generates the mod's textures procedurally (no dependencies). Run from the repository root."""
import os
import random
import struct
import zlib

OUT = "src/main/resources/assets/impendingdoom/textures"


def png(path, w, h, pixels):
    """pixels: list of rows of (r, g, b, a)."""
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)

    def chunk(kind, data):
        c = struct.pack(">I", len(data)) + kind + data
        return c + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def canvas(w, h, color=(0, 0, 0, 0)):
    return [[color for _ in range(w)] for _ in range(h)]


def rect(img, x, y, w, h, color, noise=0, rnd=None):
    for j in range(y, y + h):
        for i in range(x, x + w):
            c = color
            if noise and rnd:
                n = rnd.randint(-noise, noise)
                c = (max(0, min(255, color[0] + n)), max(0, min(255, color[1] + n)), max(0, min(255, color[2] + n)), color[3])
            img[j][i] = c


def herobrine():
    """Classic 64x64 player skin layout: brown hair, cyan shirt, blue pants, glowing white eyes."""
    r = random.Random(1)
    img = canvas(64, 64)
    skin, hair, shirt, pants, shoe = (176, 124, 92, 255), (52, 34, 20, 255), (0, 168, 168, 255), (60, 60, 160, 255), (70, 70, 70, 255)
    # Head (0,0)-(32,16): all faces skin, top & back hair
    rect(img, 0, 0, 32, 16, skin, 6, r)
    rect(img, 8, 0, 8, 8, hair, 6, r)        # top
    rect(img, 24, 8, 8, 8, hair, 6, r)       # back
    rect(img, 0, 8, 8, 3, hair, 6, r)        # right side hair
    rect(img, 16, 8, 8, 3, hair, 6, r)       # left side hair
    rect(img, 8, 8, 8, 2, hair, 6, r)        # fringe
    # Face front (8,8)-(16,16): white glowing eyes, beard shadow
    for x in (9, 10, 13, 14):
        img[12][x] = (255, 255, 255, 255)
    rect(img, 11, 14, 2, 1, (120, 80, 60, 255))
    rect(img, 10, 15, 4, 1, (110, 72, 52, 255))
    # Body (16,16)-(40,32)
    rect(img, 16, 16, 24, 16, shirt, 8, r)
    # Right arm (40,16)-(56,32): sleeve + skin
    rect(img, 40, 16, 16, 16, skin, 6, r)
    rect(img, 40, 20, 16, 4, shirt, 8, r)
    rect(img, 44, 16, 4, 4, shirt, 8, r)
    # Right leg (0,16)-(16,32)
    rect(img, 0, 16, 16, 16, pants, 8, r)
    rect(img, 0, 29, 16, 3, shoe, 6, r)
    # Left leg (16,48)-(32,64) and left arm (32,48)-(48,64) mirrored copies
    for j in range(16):
        for i in range(16):
            img[48 + j][16 + i] = img[16 + j][i]
            img[48 + j][32 + i] = img[16 + j][40 + i]
    png(f"{OUT}/entity/herobrine.png", 64, 64, img)


def heart_walker():
    r = random.Random(2)
    img = canvas(64, 32)
    bark, dark, glow = (92, 62, 44, 255), (55, 36, 25, 255), (255, 140, 40, 255)
    rect(img, 0, 0, 40, 20, bark, 14, r)
    # Bark grain
    for i in range(0, 40, 3):
        for j in range(20):
            if r.random() < 0.5:
                img[j][i] = dark
    # Glowing eye-like heart on every side face (y 10..20)
    for fx in (0, 10, 20, 30):
        rect(img, fx + 3, 13, 4, 3, glow, 20, r)
        img[14][fx + 4] = (255, 230, 150, 255)
        img[14][fx + 5] = (255, 230, 150, 255)
    # Legs
    rect(img, 0, 20, 24, 11, (70, 46, 32, 255), 10, r)
    png(f"{OUT}/entity/heart_walker.png", 64, 32, img)


def omen():
    r = random.Random(3)
    img = canvas(16, 16)
    paper, edge, ink = (226, 214, 180, 255), (150, 120, 80, 255), (120, 10, 20, 255)
    rect(img, 3, 2, 10, 12, paper, 10, r)
    for y in range(2, 14):
        img[y][3] = edge
        img[y][12] = edge
    rect(img, 2, 1, 12, 2, edge, 8, r)
    rect(img, 2, 13, 12, 2, edge, 8, r)
    # A red eye sigil
    for (x, y) in [(6, 6), (7, 5), (8, 5), (9, 6), (6, 8), (7, 9), (8, 9), (9, 8), (5, 7), (10, 7)]:
        img[y][x] = ink
    img[7][7] = (20, 0, 0, 255)
    img[7][8] = (20, 0, 0, 255)
    png(f"{OUT}/item/doom_omen.png", 16, 16, img)


def netherflame():
    r = random.Random(4)
    img = canvas(16, 16)
    glass = (200, 220, 230, 255)
    rect(img, 7, 2, 2, 3, glass)
    rect(img, 6, 1, 4, 1, (120, 80, 50, 255))
    for y in range(5, 15):
        for x in range(3, 13):
            if (x - 7.5) ** 2 + (y - 10) ** 2 <= 22:
                img[y][x] = (230, 60 + r.randint(0, 60), 10, 255)
    for y in range(5, 15):
        for x in range(3, 13):
            d = (x - 7.5) ** 2 + (y - 10) ** 2
            if 22 < d <= 30:
                img[y][x] = glass
    img[8][6] = (255, 220, 120, 255)
    png(f"{OUT}/item/netherflame_bottle.png", 16, 16, img)


def burnt_torch(name, tip):
    img = canvas(16, 16)
    rect(img, 7, 8, 2, 8, (110, 80, 45, 255))
    rect(img, 7, 6, 2, 2, tip)
    img[6][7] = (30, 30, 30, 255)
    png(f"{OUT}/block/{name}.png", 16, 16, img)


def burnt_lantern(name, glass):
    r = random.Random(5)
    img = canvas(16, 16)
    rect(img, 0, 0, 16, 16, (60, 60, 68, 255), 8, r)
    rect(img, 1, 3, 4, 5, glass, 6, r)
    rect(img, 7, 3, 4, 5, glass, 6, r)
    png(f"{OUT}/block/{name}.png", 16, 16, img)


def effect(name, color, symbol):
    img = canvas(18, 18)
    for y in range(18):
        for x in range(18):
            if (x - 8.5) ** 2 + (y - 8.5) ** 2 <= 64:
                img[y][x] = (color[0] // 3, color[1] // 3, color[2] // 3, 255)
            if (x - 8.5) ** 2 + (y - 8.5) ** 2 <= 49:
                img[y][x] = color + (255,)
    for (x, y) in symbol:
        img[y][x] = (255, 255, 255, 255)
    png(f"{OUT}/mob_effect/{name}.png", 18, 18, img)


if __name__ == "__main__":
    herobrine()
    heart_walker()
    omen()
    netherflame()
    burnt_torch("burnt_torch", (40, 32, 28, 255))
    burnt_torch("burnt_soul_torch", (30, 40, 44, 255))
    burnt_lantern("burnt_lantern", (40, 34, 26, 255))
    burnt_lantern("burnt_soul_lantern", (24, 36, 40, 255))
    bolt = [(9, 3), (8, 4), (8, 5), (7, 6), (7, 7), (8, 8), (9, 8), (10, 8), (9, 9), (9, 10), (8, 11), (8, 12), (7, 13)]
    effect("stunned", (230, 210, 40), bolt)
    drop = [(8, 4), (8, 5), (7, 6), (9, 6), (6, 7), (10, 7), (6, 8), (10, 8), (6, 9), (10, 9), (7, 10), (9, 10), (8, 11)]
    effect("bloodlust_haze", (150, 10, 10), drop)
    flake = [(8, 3), (8, 4), (8, 5), (8, 6), (8, 7), (8, 8), (8, 9), (8, 10), (8, 11), (8, 12), (8, 13), (4, 8), (5, 8), (6, 8), (7, 8),
             (9, 8), (10, 8), (11, 8), (12, 8), (5, 5), (6, 6), (11, 5), (10, 6), (5, 11), (6, 10), (11, 11), (10, 10)]
    effect("freezing", (90, 180, 230), flake)
    print("textures written to", OUT)
