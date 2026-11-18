#!/usr/bin/env python3
"""Convert Fabric data-pack JSON (load conditions, custom ingredients) to NeoForge's equivalents.

Usage: python3 -I convert_fabric_data.py <resources-dir> [--check]

Datapack syntax does not fail the build: NeoForge simply cannot parse a `fabric:` key and logs
"Couldn't parse data file" at server start, one file at a time. Worse, an unread
`fabric:load_conditions` does not disable anything -- a recipe guarded by "mod X is loaded" is
parsed anyway and fails, or loads when it should not. So every file is converted at once:

  fabric:load_conditions                -> neoforge:conditions
    fabric:all_mods_loaded {values}     -> one neoforge:mod_loaded {modid} per value (all must hold)
    fabric:any_mods_loaded {values}     -> neoforge:or of neoforge:mod_loaded
    fabric:tags_populated {registry?, values} -> neoforge:not(neoforge:tag_empty {registry, tag}) each
    fabric:not / and / or / true        -> neoforge:not / and / or / always
  ingredient {"fabric:type": ...}       -> {"neoforge:ingredient_type": ...}
    fabric:any {ingredients}            -> neoforge:compound {children}
    fabric:all {ingredients}            -> neoforge:intersection {children}
    fabric:difference {base, subtracted}-> neoforge:difference {base, subtracted}

Field names were read from the NeoForge 26.2 codecs (ModLoadedCondition, TagEmptyCondition,
NotCondition, OrCondition, CompoundIngredient, DifferenceIngredient, IntersectionIngredient).
Anything else under a `fabric:` key is reported and left alone -- converting it would be guessing.

With --check nothing is written; the exit status says whether any `fabric:` key remains (CI).
Idempotent: a converted tree converts to itself.
"""
import json
import sys
from pathlib import Path

INGREDIENT_TYPES = {
    "fabric:any": ("neoforge:compound", {"ingredients": "children"}),
    "fabric:all": ("neoforge:intersection", {"ingredients": "children"}),
    "fabric:difference": ("neoforge:difference", {}),
}


class Unsupported(Exception):
    pass


def condition_list(conditions: list) -> list:
    out = []
    for c in conditions:
        out.extend(condition(c))
    return out


def condition(c: dict) -> list:
    """One Fabric condition -> a list of NeoForge conditions that must ALL hold."""
    kind = c.get("condition")
    if kind == "fabric:all_mods_loaded":
        return [{"type": "neoforge:mod_loaded", "modid": m} for m in c["values"]]
    if kind == "fabric:any_mods_loaded":
        return [{"type": "neoforge:or", "values": [{"type": "neoforge:mod_loaded", "modid": m} for m in c["values"]]}]
    if kind == "fabric:tags_populated":
        registry = c.get("registry", "minecraft:item")
        return [{"type": "neoforge:not", "value": {"type": "neoforge:tag_empty", "registry": registry, "tag": t}}
                for t in c["values"]]
    if kind == "fabric:not":
        inner = condition(c["value"])
        value = inner[0] if len(inner) == 1 else {"type": "neoforge:and", "values": inner}
        return [{"type": "neoforge:not", "value": value}]
    if kind in ("fabric:and", "fabric:or"):
        values = condition_list(c["values"])
        return [{"type": "neoforge:" + kind.split(":")[1], "values": values}]
    if kind == "fabric:true":
        return [{"type": "neoforge:always"}]
    raise Unsupported(f"condition {kind!r}")


def convert(node, problems: list, where: str):
    if isinstance(node, list):
        return [convert(v, problems, where) for v in node]
    if not isinstance(node, dict):
        return node
    out = {}
    for key, value in node.items():
        if key == "fabric:load_conditions":
            try:
                out["neoforge:conditions"] = condition_list(value)
            except (Unsupported, KeyError) as e:
                problems.append(f"{where}: unsupported {e}")
                out[key] = value
            continue
        out[key] = convert(value, problems, where)
    kind = out.get("fabric:type")
    if kind is not None:
        if kind in INGREDIENT_TYPES:
            neo, renames = INGREDIENT_TYPES[kind]
            converted = {"neoforge:ingredient_type": neo}
            for k, v in out.items():
                if k != "fabric:type":
                    converted[renames.get(k, k)] = v
            return converted
        problems.append(f"{where}: unsupported ingredient {kind!r}")
    return out


def remaining_fabric_keys(node) -> bool:
    if isinstance(node, dict):
        return any(k.startswith("fabric:") or (k == "condition" and str(v).startswith("fabric:"))
                   or remaining_fabric_keys(v) for k, v in node.items())
    if isinstance(node, list):
        return any(remaining_fabric_keys(v) for v in node)
    return False


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    root, check = Path(sys.argv[1]), "--check" in sys.argv
    changed, problems, leftover = 0, [], []
    for path in sorted(root.rglob("*.json")):
        text = path.read_text(encoding="utf-8")
        if "fabric:" not in text:
            continue
        data = json.loads(text)
        new = convert(data, problems, str(path.relative_to(root)))
        if remaining_fabric_keys(new):
            leftover.append(str(path.relative_to(root)))
        if new != data:
            changed += 1
            if not check:
                path.write_text(json.dumps(new, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    verb = "would convert" if check else "converted"
    print(f"{verb} {changed} file(s)")
    for p in problems:
        print("  " + p)
    if check and (changed or leftover):
        print("fabric: data left in:", *(leftover or ["(convertible files above)"]), sep="\n  ")
        return 1
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
