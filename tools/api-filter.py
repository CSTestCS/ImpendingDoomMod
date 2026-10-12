#!/usr/bin/env python3
"""CI helper: shrinks a javap dump to class headers plus members whose names appear in the mod's sources."""
import os
import re
import sys

tokens = set()
for root, _, files in os.walk("src/main/java"):
    for f in files:
        if f.endswith(".java"):
            tokens.update(re.findall(r"[A-Za-z_][A-Za-z0-9_]*", open(os.path.join(root, f), encoding="utf-8").read()))

def emit_block(block):
    # Small classes are printed in full; large ones (registries of constants) only keep members the mod uses.
    if len(block) <= 400:
        for l in block:
            print(l)
    else:
        for l in block:
            filter_line(l)


def filter_line(line):
    s = line.strip()
    if s.startswith("===") or "class " in s or "interface " in s or "record " in s or "moved-to" in s or s.startswith("~") or "error" in s.lower():
        print(line)
        return
    m = re.search(r"([A-Za-z_][A-Za-z0-9_$]*)\s*(\(|;|$)", s.split(" throws ")[0])
    name = None
    if "(" in s:
        m = re.search(r"([A-Za-z_][A-Za-z0-9_$]*)\(", s)
        name = m.group(1) if m else None
        # constructors: keep
        if name and "." in s[: s.find("(")].split()[-1]:
            print(line)
            return
    elif s.endswith(";"):
        m = re.search(r"([A-Za-z_][A-Za-z0-9_$]*);$", s)
        name = m.group(1) if m else None
    if name and name in tokens:
        print(line)


block = []
for raw in sys.stdin:
    raw = raw.rstrip("\n")
    if raw.startswith("===") and block:
        emit_block(block)
        block = []
    block.append(raw)
emit_block(block)
