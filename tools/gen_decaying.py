#!/usr/bin/env python3
"""Generates blockstates, models, loot tables, tool tags and names for the decaying blocks thrown by tornadoes.
Keep the list in sync with com.impendingdoom.block.DecayingBlocks."""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "impendingdoom")
DATA = os.path.join(ROOT, "data")

SAME = ["dirt", "coarse_dirt", "rooted_dirt", "mud", "clay", "sand", "red_sand", "gravel", "moss_block", "stone",
        "granite", "diorite", "andesite", "tuff", "calcite", "terracotta", "netherrack", "end_stone"]
WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"]
TINTED_LEAVES = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove"}

# name -> (top, side, bottom, tint: none|top|all, tool, cutout)
BLOCKS = {}
for n in SAME:
    tool = "shovel" if n in ("dirt", "coarse_dirt", "rooted_dirt", "mud", "clay", "sand", "red_sand", "gravel") else \
        "hoe" if n == "moss_block" else "pickaxe"
    BLOCKS[n] = (n, n, n, "none", tool, False)
BLOCKS["grass_block"] = ("grass_block_top", "grass_block_side", "dirt", "top", "shovel", False)
BLOCKS["podzol"] = ("podzol_top", "podzol_side", "dirt", "none", "shovel", False)
BLOCKS["mycelium"] = ("mycelium_top", "mycelium_side", "dirt", "none", "shovel", False)
BLOCKS["snow_block"] = ("snow", "snow", "snow", "none", "shovel", False)
for n in ("sandstone", "red_sandstone"):
    BLOCKS[n] = (n + "_top", n, n + "_bottom", "none", "pickaxe", False)
for w in WOODS:
    BLOCKS[w + "_log"] = (w + "_log_top", w + "_log", w + "_log_top", "none", "axe", False)
for w in WOODS + ["azalea", "flowering_azalea"]:
    t = w + "_leaves"
    BLOCKS[t] = (t, t, t, "all" if w in TINTED_LEAVES else "none", "hoe", True)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def face(tex, tint, h, side, cull=None):
    f = {"texture": tex}
    if side:
        f["uv"] = [0, 16 - h, 16, 16]
    if tint:
        f["tintindex"] = 0
    if cull:
        f["cullface"] = cull
    return f


def templates():
    for tint in ("none", "top", "all"):
        for layer in range(1, 9):
            h = layer * 2
            sides = tint == "all"
            faces = {
                "down": face("#bottom", tint == "all", h, False, "down"),
                "up": face("#top", tint != "none", h, False, "up" if h == 16 else None),
            }
            for d in ("north", "south", "west", "east"):
                faces[d] = face("#side", sides, h, True, d)
            write(f"{ASSETS}/models/block/decaying_template_{tint}_{h}.json", {
                "parent": "minecraft:block/block",
                "textures": {"particle": "#side"},
                "elements": [{"from": [0, 0, 0], "to": [16, h, 16], "faces": faces}],
            })


def main():
    templates()
    lang_path = f"{ASSETS}/lang/en_us.json"
    lang = json.load(open(lang_path))
    tags = {"shovel": [], "pickaxe": [], "axe": [], "hoe": []}
    for name, (top, side, bottom, tint, tool, cutout) in BLOCKS.items():
        block = "decaying_" + name
        variants = {}
        for layer in range(1, 9):
            model = {"parent": f"impendingdoom:block/decaying_template_{tint}_{layer * 2}",
                     "textures": {"top": f"minecraft:block/{top}", "side": f"minecraft:block/{side}",
                                  "bottom": f"minecraft:block/{bottom}"}}
            if cutout:
                model["render_type"] = "minecraft:cutout_mipped"
            write(f"{ASSETS}/models/block/{block}_{layer * 2}.json", model)
            variants[f"layers={layer}"] = {"model": f"impendingdoom:block/{block}_{layer * 2}"}
        write(f"{ASSETS}/blockstates/{block}.json", {"variants": variants})
        write(f"{DATA}/impendingdoom/loot_table/blocks/{block}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minecraft:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"impendingdoom:blocks/{block}",
        })
        tags[tool].append(f"impendingdoom:{block}")
        lang[f"block.impendingdoom.{block}"] = "Decaying " + name.replace("_", " ").title()
    for tool, values in tags.items():
        path = f"{DATA}/minecraft/tags/block/mineable/{tool}.json"
        existing = json.load(open(path))["values"] if os.path.exists(path) else []
        existing = [v for v in existing if not v.startswith("impendingdoom:decaying_")]
        write(path, {"values": existing + values})
    lang.pop("block.impendingdoom.decaying", None)
    write(lang_path, lang)


if __name__ == "__main__":
    main()
