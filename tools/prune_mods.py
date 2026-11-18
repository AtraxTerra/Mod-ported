#!/usr/bin/env python3
"""Move aside the jars whose required dependencies are not in the folder, until all are met.

Usage: prune_mods.py <mods-dir> [<extra-jar-or-dir> ...]   (extras count as present, never moved)

The mod list used for a compatibility boot is partial (some mods are not public), and FML refuses to
start at all on one missing dependency. Reads META-INF/neoforge.mods.toml of every jar, and of the
jars nested in it (jar-in-jar), so a library shipped inside another mod counts as present. Version
ranges are not checked. Pruned jars go to <mods-dir>/../pruned-mods/ and are listed.
"""
import io
import os
import shutil
import sys
import tomllib
import zipfile

BUILTIN = {"minecraft", "neoforge", "java", "forge", "fml"}


def mods_toml(data):
    """(provided ids, required ids) of a jar given as bytes, nested jars included."""
    provided, required = set(), set()
    try:
        z = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile:
        return provided, required
    with z:
        names = z.namelist()
        if "META-INF/neoforge.mods.toml" in names:
            try:
                toml = tomllib.loads(z.read("META-INF/neoforge.mods.toml").decode("utf-8", "replace"))
            except tomllib.TOMLDecodeError:
                toml = {}
            for mod in toml.get("mods", []):
                provided.add(mod.get("modId"))
                for p in mod.get("provides", []) or []:
                    provided.add(p)
            deps = toml.get("dependencies", {})
            for owner, entries in (deps.items() if isinstance(deps, dict) else []):
                for dep in entries if isinstance(entries, list) else []:
                    kind = str(dep.get("type", "required" if dep.get("mandatory", True) else "optional")).lower()
                    if kind == "required":
                        required.add(dep.get("modId"))
        for n in names:
            if n.startswith("META-INF/jarjar/") and n.endswith(".jar"):
                p, _ = mods_toml(z.read(n))  # nested jars' own deps are FML's business
                provided |= p
    return provided, required


def main():
    mods = sys.argv[1]
    extras = []
    for e in sys.argv[2:]:
        extras += [os.path.join(e, f) for f in os.listdir(e)] if os.path.isdir(e) else [e]
    info = {}
    for f in sorted(os.listdir(mods)):
        if f.endswith(".jar"):
            info[f] = mods_toml(open(os.path.join(mods, f), "rb").read())
    always = set()
    for e in extras:
        if e.endswith(".jar") and os.path.exists(e):
            always |= mods_toml(open(e, "rb").read())[0]
    pruned = os.path.join(os.path.dirname(os.path.abspath(mods)), "pruned-mods")
    while True:
        present = always | set().union(*(p for p, _ in info.values())) | BUILTIN
        bad = {f: r - present for f, (_, r) in info.items() if r - present}
        if not bad:
            break
        os.makedirs(pruned, exist_ok=True)
        for f, missing in bad.items():
            print(f"pruned  {f}: needs {', '.join(sorted(m for m in missing if m))}")
            shutil.move(os.path.join(mods, f), os.path.join(pruned, f))
            del info[f]
    print(f"{len(info)} mods kept")


if __name__ == "__main__":
    main()
