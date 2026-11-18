#!/usr/bin/env python3
"""Check that NeoForge's furnace_fuels data map matches upstream's AllFuelTimes.

Usage: python3 -I check_fuels.py <src/main/java> <src/main/resources>

Upstream adds Create's fuels by wrapping FuelValues.vanillaBurnTimes. On NeoForge that method only
feeds datagen; the FuelValues the game uses are built from the `neoforge:furnace_fuels` data map. So
the port ships the values as data (data/neoforge/data_maps/item/furnace_fuels.json), and this check
keeps that file in step with AllFuelTimes.java when an upstream sync changes it. A drifted entry
does not fail anything at runtime -- the item just stops (or starts) burning.

Only literal `ALL.put(AllItems.X, <int or Integer.MAX_VALUE>)` lines are understood; anything else
in register() is reported so it gets looked at by hand.
"""
import json
import re
import sys
from pathlib import Path


def item_ids(java: Path) -> dict[str, str]:
    ids = {}
    for name in ("AllItemIds.java", "AllBlockItemIds.java"):
        for f in java.rglob(name):
            for const, path in re.findall(r"\b([A-Z0-9_]+)\s*=\s*create\(\"([a-z0-9_/]+)\"\)", f.read_text()):
                ids.setdefault(const, "create:" + path)
    return ids


def main() -> int:
    java, resources = Path(sys.argv[1]), Path(sys.argv[2])
    src = next(java.rglob("AllFuelTimes.java")).read_text()
    body = src[src.index("register()"):]
    body = body[:body.index("}") + 1]
    ids = item_ids(java)
    expected, problems = {}, []
    for line in body.splitlines():
        line = line.strip()
        if not line or line.startswith(("register()", "}", "//")) or line.endswith("{"):
            continue
        m = re.fullmatch(r"ALL\.put\(AllItems\.([A-Z0-9_]+),\s*(\d+|Integer\.MAX_VALUE)\);", line)
        if not m:
            problems.append(f"not understood, check by hand: {line}")
            continue
        const, value = m.groups()
        if const not in ids:
            problems.append(f"no id found for AllItems.{const}")
            continue
        expected[ids[const]] = 2**31 - 1 if value == "Integer.MAX_VALUE" else int(value)
    data = json.loads((resources / "data/neoforge/data_maps/item/furnace_fuels.json").read_text())
    actual = {k: (v["burn_time"] if isinstance(v, dict) else v) for k, v in data["values"].items()}
    for k in sorted(expected.keys() | actual.keys()):
        if expected.get(k) != actual.get(k):
            problems.append(f"{k}: AllFuelTimes says {expected.get(k)}, data map says {actual.get(k)}")
    if problems:
        print("furnace_fuels.json is out of step with AllFuelTimes.java:")
        print("\n".join("  " + p for p in problems))
        return 1
    print(f"furnace_fuels.json matches AllFuelTimes.java ({len(expected)} fuels)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
