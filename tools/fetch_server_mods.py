#!/usr/bin/env python3
"""Download, from Modrinth, the exact jars listed in server-mods.txt (the survival server's mods).

Usage: fetch_server_mods.py <server-mods.txt> <dest-dir>

For each file name: search Modrinth with the name's leading words, list the NeoForge versions of the
top hits and keep the one that ships a file of exactly that name. Prints one line per jar (found /
not on Modrinth) and exits 0 either way: a CurseForge-only mod is reported, not fatal.
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


def find(filename):
    seen = set()
    for q in queries(filename):
        hits = get(f"{API}/search?" + urllib.parse.urlencode({"query": q, "limit": 8,
                                                              "facets": json.dumps([["project_type:mod"]])}))["hits"]
        for hit in hits:
            pid = hit["project_id"]
            if pid in seen:
                continue
            seen.add(pid)
            versions = get(f"{API}/project/{pid}/version?" + urllib.parse.urlencode({"loaders": json.dumps(["neoforge"])}))
            for v in versions:
                for f in v["files"]:
                    if f["filename"] == filename or f["filename"] == re.sub(r"\s*\(\d+\)(?=\.jar$)", "", filename):
                        return hit["slug"], f["url"]
    return None, None


def main():
    listing, dest = sys.argv[1], sys.argv[2]
    os.makedirs(dest, exist_ok=True)
    found = missing = 0
    for line in open(listing):
        name = line.strip()
        if not name or name.startswith("#"):
            continue
        try:
            slug, url = find(name)
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
