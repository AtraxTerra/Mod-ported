#!/usr/bin/env python3
"""Flag mixin injections whose target call site NeoForge's patches remove.

Usage: python3 -I audit_mixins_vs_patches.py <src/main/java> <neoforge-repo>/patches [--all]

Why this exists
---------------
Create Fly's mixins were written against VANILLA Minecraft, because that is what Fabric runs. NeoForge
runs a PATCHED Minecraft, and those patches routinely replace a vanilla call with a NeoForge one --
`state.getSoundType()` becoming `state.getSoundType(level, pos, entity)`, for instance. An injection
aimed at the vanilla call then finds zero targets. With `defaultRequire: 1` that is fatal, and it
surfaces at runtime, one mixin at a time, each failure hiding the next.

The first server boot of this port died on exactly that. This script finds the same shape
statically, across every mixin at once, so the waves can be fixed together instead of rediscovered
one CI run at a time.

What it checks
--------------
For each injector with an `@At(target = "L<owner>;<name>(...)")`, it opens the NeoForge patch for the
mixin's target class and asks: does a hunk REMOVE a line calling `<name>` without ADDING one back?
If so, the call site the injector needs has probably been patched away.

It compares ARITY, not just names. The first version was name-only and missed the second boot
killer: NeoForge turned `getBlockBounciness(Block)` into `getBlockBounciness(BlockPos, BlockState)`
-- same name, removed and re-added, so a name check sees nothing wrong. Now the argument count is
read from the injector's descriptor and compared against the call sites on each side of the patch:
if every removed call matching that arity is gone and only calls of a different arity were added,
the injector is flagged.

What it cannot see
------------------
It does not read bytecode. A call whose parameter TYPES changed but whose argument count did not is
invisible, as is a call that moved into a different method with nothing removed. It only knows about
classes NeoForge patches; an unpatched class is assumed vanilla, which is right. A clean run is
evidence, not proof -- the server boot remains the authority.
"""
import re
import sys
from pathlib import Path

MIXIN_TARGET = re.compile(r"@Mixin\(\s*(?:value\s*=\s*)?\{?([^)}]*)\}?")
CLASS_REF = re.compile(r"([A-Za-z_][\w.]*)\.class")
IMPORT = re.compile(r"^import\s+([\w.]+);", re.M)
# An injector annotation and the method it decorates, roughly: we want each @At target together
# with the `method =` it injects into.
INJECTOR = re.compile(
    r"@(Inject|Redirect|ModifyArg|ModifyArgs|ModifyVariable|ModifyConstant|ModifyExpressionValue|"
    r"ModifyReturnValue|WrapOperation|WrapWithCondition|WrapMethod)\s*\((.*?)\)\s*\n\s*(?:@\w+[^\n]*\n\s*)*"
    r"(?:private|public|protected|static|\s)+[\w<>\[\], ?]+\s+(\w+)\s*\(",
    re.S,
)
AT_TARGET = re.compile(r'target\s*=\s*"L([\w/$]+);(\w+)(\([^"]*\)[^"]*)?')
METHOD = re.compile(r'method\s*=\s*\{?\s*"([\w<>$]+)')


def resolve(simple: str, imports: dict[str, str], package: str) -> str:
    if "." in simple:
        return simple
    return imports.get(simple, f"{package}.{simple}")


def patch_for(patches: Path, fqcn: str) -> Path | None:
    outer = fqcn.split("$")[0]
    path = patches / (outer.replace(".", "/") + ".java.patch")
    return path if path.is_file() else None


def descriptor_arity(descriptor: str) -> int | None:
    """Number of parameters in a JVM method descriptor such as (Lfoo/Bar;IZ[J)V."""
    if not descriptor.startswith("("):
        return None
    body = descriptor[1:descriptor.index(")")]
    count, i = 0, 0
    while i < len(body):
        ch = body[i]
        if ch == "[":
            i += 1
            continue
        if ch == "L":
            i = body.index(";", i) + 1
        else:
            i += 1
        count += 1
    return count


def call_arities(source: str, name: str) -> list[int]:
    """Argument counts of every `name(...)` call in a blob of Java source."""
    arities = []
    for match in re.finditer(r"\b" + re.escape(name) + r"\s*\(", source):
        depth, i, commas, empty = 1, match.end(), 0, True
        while i < len(source) and depth:
            ch = source[i]
            if ch in "([{<":
                depth += ch in "([{"
            elif ch in ")]}":
                depth -= 1
            elif ch == "," and depth == 1:
                commas += 1
            if depth and not ch.isspace() and ch != ")":
                empty = False
            i += 1
        arities.append(0 if empty else commas + 1)
    return arities


def side(patch_text: str, marker: str) -> str:
    return " ".join(l[1:] for l in patch_text.splitlines()
                    if l.startswith(marker) and not l.startswith(marker * 3))


def removed_without_readding(patch_text: str, name: str, arity: int | None) -> bool:
    removed = call_arities(side(patch_text, "-"), name)
    added = call_arities(side(patch_text, "+"), name)
    if arity is None:
        return bool(removed) and not added
    return arity in removed and arity not in added


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    src, patches = Path(sys.argv[1]), Path(sys.argv[2])
    show_all = "--all" in sys.argv

    suspects, patched_classes, checked = [], set(), 0

    for mixin in sorted(src.rglob("*Mixin*.java")):
        text = mixin.read_text(encoding="utf-8")
        target_match = MIXIN_TARGET.search(text)
        if not target_match:
            continue
        package = re.search(r"^package\s+([\w.]+);", text, re.M).group(1)
        imports = {fq.rsplit(".", 1)[1]: fq for fq in IMPORT.findall(text)}
        targets = [resolve(c, imports, package) for c in CLASS_REF.findall(target_match.group(1))]

        for kind, args, handler in INJECTOR.findall(text):
            at = AT_TARGET.search(args)
            if not at:
                continue
            owner, name = at.group(1).replace("/", "."), at.group(2)
            arity = descriptor_arity(at.group(3)) if at.group(3) else None
            injected_into = METHOD.search(args)
            checked += 1
            for target in targets:
                patch = patch_for(patches, target)
                if patch is None:
                    continue
                patched_classes.add(target)
                if removed_without_readding(patch.read_text(encoding="utf-8"), name, arity):
                    suspects.append((mixin.relative_to(src), handler, kind, target,
                                     injected_into.group(1) if injected_into else "?", owner, name))

    print(f"checked {checked} injector(s) with an @At target; "
          f"{len(patched_classes)} of their target classes are patched by NeoForge\n")
    if not suspects:
        print("no injector targets a call that NeoForge patches away (name-level check)")
        return 0
    print(f"{len(suspects)} SUSPECT injector(s): NeoForge removes the targeted call without re-adding it\n")
    for path, handler, kind, target, into, owner, name in suspects:
        print(f"  {path} :: {handler}  (@{kind})")
        print(f"      into   {target.rsplit('.', 1)[1]}.{into}")
        print(f"      target {owner.rsplit('.', 1)[1]}.{name}(...)")
    return 1


if __name__ == "__main__":
    sys.exit(main())
