#!/usr/bin/env python3
"""Convert a Fabric access widener / classTweaker file into a NeoForge accesstransformer.cfg.

Usage: python3 -I aw2at.py <input.classtweaker> <output.cfg>

Mapping rules
-------------
  accessible class  O              -> public <O>
  accessible field  O name DESC    -> public <O> name   (+ public <O> for the owner)
  accessible method O name DESC    -> public <O> name(DESC)  (+ public <O> for the owner)
  mutable field     O name DESC    -> public-f <O> name  (strips final)
  extendable class  O              -> public-f <O>       (strips final)
  extendable method O name DESC    -> public-f <O> name(DESC)
  transitive-inject-interface      -> NOT representable in an AT; reported separately.

Two things here are easy to get wrong, and both were, at first:

1. `extendable` must STRIP FINAL, not merely widen visibility. In an access widener it means "I
   intend to subclass this or override this method", which for a final member requires removing
   final -- NeoForge spells that `public-f`. Translating it as plain `public` compiles the access
   widener fine and then fails much later with "cannot override" or "cannot inherit from final",
   far from the cause.

2. An access widener IMPLICITLY makes the owning class accessible when a member is widened.
   A NeoForge access transformer does not, so widening a member of a package-private or nested
   class needs its own line for the owner, or the member is unreachable because its class is.

NeoForge ATs use dot-separated class names, '$' for nested classes, and raw JVM
descriptors for methods. Access widener files are already in internal form
(slash-separated), so only the owner needs rewriting.
"""
import sys
from collections import OrderedDict


def owner_to_at(owner: str) -> str:
    # net/minecraft/Foo$Bar -> net.minecraft.Foo$Bar
    return owner.replace("/", ".")


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    src, dst = sys.argv[1], sys.argv[2]

    with open(src, "r", encoding="utf-8") as fh:
        raw = fh.readlines()

    if not raw or not raw[0].lower().startswith(("accesswidener", "classtweaker")):
        header = repr(raw[0]) if raw else "<empty file>"
        print("warning: %s has no recognised header: %s" % (src, header))

    # entry -> ordered set so a member widened twice emits once
    entries = OrderedDict()
    injected_interfaces = []
    skipped = []

    for lineno, line in enumerate(raw[1:], start=2):
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        parts = line.split()
        access = parts[0]

        if access == "transitive-inject-interface":
            injected_interfaces.append((lineno, line))
            continue

        if len(parts) < 3:
            skipped.append((lineno, line, "too few tokens"))
            continue

        kind = parts[1]

        if kind == "class":
            owner = parts[2]
            # extendable means "I will subclass this", which needs final gone.
            prefix = "public-f" if access == "extendable" else "public"
            entries[f"{prefix} {owner_to_at(owner)}"] = lineno

        elif kind == "field":
            if len(parts) < 5:
                skipped.append((lineno, line, "field needs owner/name/desc"))
                continue
            owner, name = parts[2], parts[3]
            # 'mutable' and 'extendable' must also strip final; 'accessible' only widens visibility.
            prefix = "public-f" if access in ("mutable", "extendable") else "public"
            entries[f"{prefix} {owner_to_at(owner)} {name}"] = lineno
            # The owner itself, which the access widener would have opened implicitly.
            entries.setdefault(f"public {owner_to_at(owner)}", lineno)

        elif kind == "method":
            if len(parts) < 5:
                skipped.append((lineno, line, "method needs owner/name/desc"))
                continue
            owner, name, desc = parts[2], parts[3], parts[4]
            prefix = "public-f" if access == "extendable" else "public"
            entries[f"{prefix} {owner_to_at(owner)} {name}{desc}"] = lineno
            entries.setdefault(f"public {owner_to_at(owner)}", lineno)

        else:
            skipped.append((lineno, line, f"unknown target kind {kind!r}"))

    with open(dst, "w", encoding="utf-8") as fh:
        fh.write("# Generated from %s by tools/aw2at.py -- do not hand-edit.\n" % src.split("/")[-1])
        fh.write("# Regenerate after any upstream change to the access widener.\n")
        fh.write("#\n")
        fh.write("# NeoForge applies these at compile AND runtime via neoforge.mods.toml\n")
        fh.write("# (accessTransformer entry) / the ModDevGradle accessTransformers block.\n\n")
        for entry in entries:
            fh.write(entry + "\n")

    print(f"wrote {len(entries)} AT lines -> {dst}")

    if injected_interfaces:
        print(f"\n{len(injected_interfaces)} interface injection(s) have NO AT equivalent;")
        print("declare them in build.gradle via ModDevGradle interface injection data:")
        for lineno, line in injected_interfaces:
            print(f"  line {lineno}: {line}")

    if skipped:
        print(f"\n{len(skipped)} line(s) skipped:")
        for lineno, line, why in skipped:
            print(f"  line {lineno}: {why}: {line}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
