#!/usr/bin/env python3
"""Rewrite the mechanical Fabric -> NeoForge symbol substitutions across a source tree.

Usage: python3 -I codemod_fabric_imports.py <src/main/java> [--dry-run]

Only substitutions that are genuinely one-for-one live here. Anything needing judgement
(Transfer API, renderer API, custom ingredients, networking context) is deliberately NOT in this
table -- it is handled by hand and tracked in PORTING.md. Keeping the two apart is the point: a
codemod that silently "ports" a semantic difference is worse than one that leaves a compile error.

Re-runnable and idempotent: applying it twice changes nothing the second time.
"""
import re
import sys
from pathlib import Path

# (fabric fully-qualified name, neoforge replacement, simple-name stays the same?)
IMPORT_MAP = {
    # TriState: NeoForge ships its own with the same TRUE/FALSE/DEFAULT shape.
    "net.fabricmc.fabric.api.util.TriState":
        "net.neoforged.neoforge.common.util.TriState",
    # FakePlayer: both loaders have one; the NeoForge type also extends ServerPlayer.
    "net.fabricmc.fabric.api.entity.FakePlayer":
        "net.neoforged.neoforge.common.util.FakePlayer",
    # Dist markers.
    "net.fabricmc.api.Environment": "net.neoforged.api.distmarker.OnlyIn",
    "net.fabricmc.api.EnvType": "net.neoforged.api.distmarker.Dist",
}

# Symbol-level rewrites applied to the body, paired with the import changes above.
BODY_SUBS = [
    (re.compile(r"@Environment\(\s*EnvType\.CLIENT\s*\)"), "@OnlyIn(Dist.CLIENT)"),
    (re.compile(r"@Environment\(\s*EnvType\.SERVER\s*\)"), "@OnlyIn(Dist.DEDICATED_SERVER)"),
    (re.compile(r"\bEnvType\.CLIENT\b"), "Dist.CLIENT"),
    (re.compile(r"\bEnvType\.SERVER\b"), "Dist.DEDICATED_SERVER"),
]

# FabricLoader.getInstance().X(...) -> Platform.X(...)
FABRIC_LOADER_CALL = re.compile(r"FabricLoader\s*\.\s*getInstance\s*\(\s*\)\s*\.\s*")
PLATFORM_IMPORT = "import com.zurrtum.create.platform.Platform;"


def port_file(path: Path) -> tuple[bool, list[str]]:
    original = path.read_text(encoding="utf-8")
    text = original
    notes = []

    for fabric_fqn, neo_fqn in IMPORT_MAP.items():
        if fabric_fqn in text:
            text = text.replace(f"import {fabric_fqn};", f"import {neo_fqn};")
            text = text.replace(fabric_fqn, neo_fqn)

    for pattern, replacement in BODY_SUBS:
        text = pattern.sub(replacement, text)

    if FABRIC_LOADER_CALL.search(text):
        text = FABRIC_LOADER_CALL.sub("Platform.", text)
        text = re.sub(r"^import net\.fabricmc\.loader\.api\.FabricLoader;\n", "", text, flags=re.M)
        if PLATFORM_IMPORT not in text:
            # Insert the import next to the other com.zurrtum imports so the file stays tidy.
            match = list(re.finditer(r"^import .*;$", text, flags=re.M))
            if match:
                anchor = match[0].start()
                text = text[:anchor] + PLATFORM_IMPORT + "\n" + text[anchor:]
            else:
                notes.append("no import block found; Platform import NOT added")

    # Anything still referring to the loader needs a human.
    leftover = sorted(set(re.findall(r"net\.fabricmc\.[A-Za-z0-9_.]+", text)))
    if leftover:
        notes.extend(f"still Fabric: {name}" for name in leftover)

    changed = text != original
    if changed:
        path.write_text(text, encoding="utf-8")
    return changed, notes


def main() -> int:
    if not 2 <= len(sys.argv) <= 3:
        print(__doc__)
        return 2
    root = Path(sys.argv[1])
    dry = "--dry-run" in sys.argv
    if not root.is_dir():
        print(f"not a directory: {root}")
        return 2

    changed_files = 0
    remaining: dict[str, list[str]] = {}

    for path in sorted(root.rglob("*.java")):
        before = path.read_text(encoding="utf-8")
        if "net.fabricmc" not in before and "EnvType" not in before:
            continue
        if dry:
            print(f"would touch {path.relative_to(root)}")
            continue
        changed, notes = port_file(path)
        if changed:
            changed_files += 1
        if notes:
            remaining[str(path.relative_to(root))] = notes

    if dry:
        return 0

    print(f"rewrote {changed_files} file(s)")
    if remaining:
        print(f"\n{len(remaining)} file(s) still need hand porting:")
        for name, notes in remaining.items():
            print(f"  {name}")
            for note in notes:
                print(f"      {note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
