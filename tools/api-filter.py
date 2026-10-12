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

for line in sys.stdin:
    line = line.rstrip("\n")
    s = line.strip()
    if s.startswith("===") or "class " in s or "interface " in s or "record " in s or "moved-to" in s or "error" in s.lower():
        print(line)
        continue
    m = re.search(r"([A-Za-z_][A-Za-z0-9_$]*)\s*(\(|;|$)", s.split(" throws ")[0])
    name = None
    if "(" in s:
        m = re.search(r"([A-Za-z_][A-Za-z0-9_$]*)\(", s)
        name = m.group(1) if m else None
        # constructors: keep
        if name and "." in s[: s.find("(")].split()[-1]:
            print(line)
            continue
    elif s.endswith(";"):
        m = re.search(r"([A-Za-z_][A-Za-z0-9_$]*);$", s)
        name = m.group(1) if m else None
    if name and name in tokens:
        print(line)
