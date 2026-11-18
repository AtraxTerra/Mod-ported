#!/usr/bin/env python3
"""Summarise tools/migration-test.sh: what survived the switch from Create: Adapted to Create Fly.

Run from the migration directory. Reads grid.tsv, fill.tsv and the RCON result files of both
phases, prints a Markdown report on stdout and writes the full NBT of both phases to dumps.md.

Beyond "is the block still there" and "are its NBT keys still there", it compares what the block
entity holds: every item stack (id and count) and every fluid stack (id and amount) found anywhere
in its NBT. Fluid amounts are millibuckets in Create: Adapted and droplets (81 per mB) here.
"""
import collections
import re
import sys

DROPLETS_PER_MB = 81


# --- a small SNBT reader, enough for `data get` output ---------------------------------------
class SNBT:
    NUM = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?[bBsSlLfFdD]?")
    BARE = re.compile(r"[A-Za-z0-9._+\-]+")

    def __init__(self, text):
        self.s, self.i = text, 0

    def ws(self):
        while self.i < len(self.s) and self.s[self.i] in " \t\r\n":
            self.i += 1

    def peek(self):
        self.ws()
        return self.s[self.i] if self.i < len(self.s) else ""

    def expect(self, c):
        if self.peek() != c:
            raise ValueError(f"expected {c!r} at {self.i}: {self.s[self.i:self.i + 40]!r}")
        self.i += 1

    def string(self):
        q = self.s[self.i]
        self.i += 1
        out = []
        while self.s[self.i] != q:
            if self.s[self.i] == "\\":
                self.i += 1
            out.append(self.s[self.i])
            self.i += 1
        self.i += 1
        return "".join(out)

    def key(self):
        if self.peek() in "\"'":
            return self.string()
        m = self.BARE.match(self.s, self.i)
        self.i = m.end()
        return m.group(0)

    def value(self):
        c = self.peek()
        if c == "{":
            self.i += 1
            out = {}
            while self.peek() != "}":
                k = self.key()
                self.expect(":")
                out[k] = self.value()
                if self.peek() == ",":
                    self.i += 1
            self.i += 1
            return out
        if c == "[":
            self.i += 1
            if re.match(r"[BIL]\s*;", self.s[self.i:self.i + 3]):
                self.i = self.s.index(";", self.i) + 1
            out = []
            while self.peek() != "]":
                out.append(self.value())
                if self.peek() == ",":
                    self.i += 1
            self.i += 1
            return out
        if c in "\"'":
            return self.string()
        m = self.NUM.match(self.s, self.i)
        if m and m.end() > self.i:
            self.i = m.end()
            t = m.group(0).rstrip("bBsSlLfFdD")
            return float(t) if any(ch in t for ch in ".eE") else int(t)
        return self.key()  # true / false / bare word


def parse(resp):
    m = re.search(r"has the following (?:block|entity) data: (\{.*\})\s*$", resp)
    if not m:
        return None
    try:
        return SNBT(m.group(1)).value()
    except (ValueError, IndexError, AttributeError):
        return None


def contents(tree):
    """Multiset of item and fluid stacks anywhere in the tree (fluids keyed as 'fluid:<id>')."""
    found = collections.Counter()

    def walk(node, under_components=False):
        if isinstance(node, dict):
            ident = node.get("id")
            if isinstance(ident, str) and ":" in ident and not under_components:
                if isinstance(node.get("count"), int):
                    found[ident] += node["count"]
                elif isinstance(node.get("amount"), int):
                    found["fluid:" + ident] += node["amount"]
            for k, v in node.items():
                walk(v, under_components or k == "components")
        elif isinstance(node, list):
            for v in node:
                walk(v, under_components)

    walk(tree)
    return found


def load(name):
    try:
        return [l.rstrip("\n").split("\t", 1)[1] if "\t" in l else "" for l in open(name)]
    except FileNotFoundError:
        return []


def main():
    grid = [l.rstrip("\n").split("\t") for l in open("grid.tsv")]
    placeA, presentA, dataA = load("placeA.tsv"), load("presentA.tsv"), load("dataA.tsv")
    presentB, dataB = load("presentB.tsv"), load("dataB.tsv")
    extraA, extraB = load("extraA.tsv"), load("extraB.tsv")
    extra_cmds = [l.rstrip("\n").split("\t", 1)[0] for l in open("extraA.tsv")] if extraA else []

    with open("dumps.md", "w") as d:
        for i, (x, y, z, block) in enumerate(grid):
            d.write(f"{block} @ {x} {y} {z}\n  A: {dataA[i] if i < len(dataA) else ''}\n  B: {dataB[i] if i < len(dataB) else ''}\n")
        for i, cmd in enumerate(extra_cmds):
            d.write(f"{cmd}\n  A: {extraA[i]}\n  B: {extraB[i] if i < len(extraB) else ''}\n")

    placed = sum("Changed the block" in r for r in placeA)
    print("## World migration: Create: Adapted -> Create Fly\n")
    print(f"Phase A (Create: Adapted): placed **{placed}** of {len(grid)} blocks.")
    if not presentB:
        print("\n:x: Phase B produced no results (server did not start or RCON failed).")
        return 1

    def keys(tree):
        out = set()
        def walk(node):
            if isinstance(node, dict):
                for k, v in node.items():
                    if k != "components":
                        out.add(k); walk(v)
            elif isinstance(node, list):
                for v in node: walk(v)
        walk(tree)
        return out - {"id", "x", "y", "z", "HasAttachedComputer"}

    lost_blocks, lost_be, lost_keys, unstable, lost_content, kept_content = [], [], [], [], [], []
    for i, (x, y, z, block) in enumerate(grid):
        if i >= len(placeA) or "Changed the block" not in placeA[i]:
            continue
        if i < len(presentA) and "Test passed" not in presentA[i]:
            unstable.append(block); continue
        if i < len(presentB) and "Test passed" not in presentB[i]:
            lost_blocks.append(block); continue
        a = parse(dataA[i]) if i < len(dataA) else None
        b = parse(dataB[i]) if i < len(dataB) else None
        if a is not None and b is None:
            lost_be.append(block); continue
        if a is None:
            continue
        missing = keys(a) - keys(b)
        if missing:
            lost_keys.append((block, sorted(missing)))
        ca, cb = contents(a), contents(b)
        ca = collections.Counter({k: v * (DROPLETS_PER_MB if k.startswith("fluid:") else 1) for k, v in ca.items()})
        if ca:
            (kept_content if ca == cb else lost_content).append((block, dict(ca), dict(cb)))

    print(f"\n- blocks that removed themselves before the switch (no support; not a migration issue): {len(unstable)} -- {', '.join(b.split(':')[1] for b in unstable)}")
    print(f"- blocks gone after the switch: **{len(lost_blocks)}**")
    for b in lost_blocks[:30]: print(f"  - `{b}`")
    print(f"- block entities gone (block kept, no data): **{len(lost_be)}**")
    for b in lost_be[:30]: print(f"  - `{b}`")
    print(f"- block entities holding items or fluids: {len(kept_content) + len(lost_content)}, contents changed: **{len(lost_content)}**")
    for b, ca, cb in lost_content: print(f"  - `{b}`: Adapted {ca} -> Fly {cb}")
    for b, ca, _ in kept_content: print(f"  - kept `{b}`: {ca}")
    print(f"- block entities that lost NBT keys: **{len(lost_keys)}**")
    for b, k in lost_keys[:120]: print(f"  - `{b}`: {', '.join(k)}")
    if extra_cmds:
        print("\n### Contraptions, item components and kinetics\n")
        for i, cmd in enumerate(extra_cmds):
            ra, rb = extraA[i], extraB[i] if i < len(extraB) else ""
            ta, tb = parse(ra), parse(rb)
            verdict = ""
            if ta is not None:
                ca, cb = contents(ta), contents(tb) if tb is not None else None
                verdict = ": same contents" if ca == cb else f": Adapted {dict(ca)} -> Fly {dict(cb) if cb is not None else 'no data'}"
            print(f"- `{cmd}`{verdict}\n  - Adapted: `{ra[:900]}`\n  - Fly: `{rb[:900]}`")
    return 0


if __name__ == "__main__":
    sys.exit(main())
