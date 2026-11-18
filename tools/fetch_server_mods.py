#!/usr/bin/env python3
"""Download, from Modrinth, the exact jars listed in server-mods.txt (the survival server's mods).

Usage: fetch_server_mods.py <server-mods.txt> <dest-dir>

For each file name: search Modrinth with the name's leading words, list the NeoForge versions of the
top hits and keep the one that ships a file of exactly that name. A line may instead give the URL after a
tab. Prints one line per jar (found / not on Modrinth) and exits 0 either way: a mod that is not
public, or only on CurseForge without a URL, is reported, not fatal.
"""
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "AtraxTerra/Mod-ported CI (create-fly neoforge port)"}


def get(url):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                return json.load(r)
        except Exception as e:  # rate limit or transient
            if attempt == 3:
                raise
            time.sleep(2 + attempt * 3)


def queries(filename):
    stem = re.sub(r"\.jar$", "", filename)
    stem = re.sub(r"\s*\(\d+\)$", "", stem)
    words = re.split(r"[-_+ ]", stem)
    head = []
    for w in words:
        if re.match(r"^(v?\d|mc\d|neoforge$|NeoForge$|release$|all$|mod$|core$)", w):
            break
        head.append(w)
    name = " ".join(head) or words[0]
    out = [name, re.sub(r"(?<=[a-z])(?=[A-Z])", " ", name)]
    if name.lower() == "jade":
        out.append("jade 🔍")
    return list(dict.fromkeys(out))


# Modrinth slugs for names the search does not find by their file name.
SLUGS = {
    "kotlinforforge": "kotlin-for-forge", "BiomesOPlenty": "biomes-o-plenty", "appliedenergistics2": "ae2",
    "inventoryessentials": "inventory-essentials", "supermartijn642corelib": "supermartijn642s-core-lib",
    "supermartijn642configlib": "supermartijn642s-config-lib", "elytraslot": "elytra-slot", "eplus": "enchanting-plus",
    "cupboard": "cupboard", "farsight": "farsight", "day-counter": "day-counter", "capturenet": "capture-net",
    "enchantmenttransfer": "enchantment-transfer", "utilitydrawers": "utility-drawers", "tabbylib": "tabbylib",
}


def versions_with(pid, filename):
    versions = get(f"{API}/project/{pid}/version?" + urllib.parse.urlencode({"loaders": json.dumps(["neoforge"])}))
    plain = re.sub(r"\s*\(\d+\)(?=\.jar$)", "", filename)
    for v in versions:
        for f in v["files"]:
            if f["filename"] in (filename, plain):
                return f["url"]
    return None


def find(filename):
    seen = set()
    for key, slug in SLUGS.items():
        if filename.lower().startswith(key.lower()):
            try:
                url = versions_with(slug, filename)
            except Exception:
                url = None
            if url:
                return slug, url
    for q in queries(filename):
        hits = get(f"{API}/search?" + urllib.parse.urlencode({"query": q, "limit": 8,
                                                              "facets": json.dumps([["project_type:mod"]])}))["hits"]
        for hit in hits:
            pid = hit["project_id"]
            if pid in seen:
                continue
            seen.add(pid)
            url = versions_with(pid, filename)
            if url:
                return hit["slug"], url
    return None, None


def main():
    listing, dest = sys.argv[1], sys.argv[2]
    os.makedirs(dest, exist_ok=True)
    found = missing = 0
    for line in open(listing):
        name, _, direct = line.rstrip("\n").partition("\t")
        name = name.strip()
        if not name or name.startswith("#"):
            continue
        try:
            slug, url = ("direct", direct.strip()) if direct.strip() else find(name)
        except Exception as e:
            slug, url = None, None
            print(f"error   {name}: {e}")
        if url:
            urllib.request.urlretrieve(url, os.path.join(dest, name.replace(" ", "_")))
            print(f"found   {name}  (modrinth: {slug})")
            found += 1
        else:
            print(f"MISSING {name}  (not on Modrinth under that file name)")
            missing += 1
    print(f"\n{found} downloaded, {missing} not found")


if __name__ == "__main__":
    main()
