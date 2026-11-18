#!/usr/bin/env python3
"""Port Create Fly's Fabric Transfer API adapters to NeoForge 26.2's ResourceHandler API.

Usage: python3 -I codemod_transfer_api.py <reference-dir> <output-dir> [file.java ...]

The two APIs are the same design -- immutable resource values, transaction-scoped insert/extract,
indexed storages -- addressed differently. Fabric hands out StorageView objects; NeoForge takes an
int index. `ResourceView` in the transfer package bridges that, which turns most of this port into
a rename.

What this script does NOT do, on purpose:
  * collapse InventoryWrapper's Direct/Slotted split (ResourceHandler is always indexed, so only
    the Slotted behaviour applies) -- that is a structural edit, done by hand
  * do anything about supportsInsertion/supportsExtraction, which have no ResourceHandler
    counterpart -- also by hand
  * convert long to int on insert/extract results; the casts upstream already has are harmless and
    javac points at any that are not

Leaving those out is deliberate: a codemod that guesses at semantics produces code that compiles
and misbehaves, which is worse than a compile error.
"""
import re
import sys
from pathlib import Path

# (pattern, replacement) applied in order. Imports first, then types, then calls.
SUBS: list[tuple[str, str]] = [
    # --- imports -----------------------------------------------------------
    (r"import net\.fabricmc\.fabric\.api\.transfer\.v1\.storage\.Storage;",
     "import net.neoforged.neoforge.transfer.ResourceHandler;"),
    (r"import net\.fabricmc\.fabric\.api\.transfer\.v1\.storage\.SlottedStorage;\n", ""),
    (r"import net\.fabricmc\.fabric\.api\.transfer\.v1\.storage\.StorageView;\n", ""),
    (r"import net\.fabricmc\.fabric\.api\.transfer\.v1\.storage\.base\.SingleSlotStorage;\n", ""),
    (r"import net\.fabricmc\.fabric\.api\.transfer\.v1\.storage\.base\.CombinedStorage;",
     "import net.neoforged.neoforge.transfer.CombinedResourceHandler;"),
    (r"import net\.fabricmc\.fabric\.api\.transfer\.v1\.storage\.StoragePreconditions;",
     "import net.neoforged.neoforge.transfer.TransferPreconditions;"),
    (r"import net\.fabricmc\.fabric\.api\.transfer\.v1\.item\.ItemVariant;",
     "import net.neoforged.neoforge.transfer.item.ItemResource;"),
    (r"import net\.fabricmc\.fabric\.api\.transfer\.v1\.fluid\.FluidVariant;",
     "import net.neoforged.neoforge.transfer.fluid.FluidResource;"),
    (r"import net\.fabricmc\.fabric\.api\.transfer\.v1\.transaction\.Transaction;",
     "import net.neoforged.neoforge.transfer.transaction.Transaction;"),
    (r"import net\.fabricmc\.fabric\.api\.transfer\.v1\.transaction\.TransactionContext;",
     "import net.neoforged.neoforge.transfer.transaction.TransactionContext;"),
    (r"import net\.fabricmc\.fabric\.api\.transfer\.v1\.transaction\.base\.SnapshotParticipant;",
     "import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;"),
    (r"import net\.fabricmc\.fabric\.impl\.transfer\.item\.ItemVariantImpl;\n", ""),
    (r"import net\.fabricmc\.fabric\.impl\.transfer\.DebugMessages;\n", ""),
    (r"import net\.fabricmc\.fabric\.api\.transfer\.v1\.item\.ContainerStorage;",
     "import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;"),
    (r"import net\.fabricmc\.fabric\.api\.transfer\.v1\.item\.base\.SingleStackStorage;\n", ""),
    (r"import net\.fabricmc\.fabric\.api\.transfer\.v1\.context\.ContainerItemContext;",
     "import net.neoforged.neoforge.transfer.access.ItemAccess;"),

    # --- generic types -----------------------------------------------------
    # Storage/SlottedStorage both become the flat indexed handler.
    (r"\bSlottedStorage<", "ResourceHandler<"),
    (r"\bStorage<", "ResourceHandler<"),
    # Both view shapes become the (handler, index) record.
    (r"\bSingleSlotStorage<", "ResourceView<"),
    (r"\bStorageView<", "ResourceView<"),
    # Resource value types.
    (r"\bItemVariant\b", "ItemResource"),
    (r"\bFluidVariant\b", "FluidResource"),
    (r"\bSnapshotParticipant\b", "SnapshotJournal"),
    (r"\bStoragePreconditions\b", "TransferPreconditions"),
    (r"\bCombinedStorage\b", "CombinedResourceHandler"),

    # --- calls -------------------------------------------------------------
    # An ItemResource is built straight from a stack; no need to split item and components.
    (r"ItemResource\.of\(\s*(\w+)\.getItem\(\),\s*\1\.getComponentsPatch\(\)\s*\)",
     r"ItemResource.of(\1)"),
    # Fabric's outer transaction is NeoForge's root transaction.
    (r"Transaction\.openOuter\(\)", "Transaction.openRoot()"),
    # NeoForge rolls back on close, so an explicit abort has no counterpart. Dropping the statement
    # keeps the semantics: the try-with-resources close is the rollback.
    (r"[ \t]*\w+\.abort\(\);\n", ""),
    # Views come from the bridge rather than the storage.
    (r"(\w+)\.nonEmptyViews\(\)", r"ResourceView.nonEmpty(\1)"),
    (r"(\w+)\.getSlots\(\)\b", r"\1.size()"),
    # Fabric exposed the cached stack through an impl class; a resource converts directly.
    (r"\(\(ItemVariantImpl\)\s*(\w+)\)\.getCachedStack\(\)", r"\1.toStack()"),
    (r"ItemResourceImpl", "ItemResource"),
    # Vanilla container adapters are supplied by NeoForge.
    (r"ContainerStorage\.of\(\s*(\w+),\s*null\s*\)", r"VanillaContainerWrapper.of(\1)"),
    (r"ContainerStorage\.of\(", "VanillaContainerWrapper.of("),
]

# Anything matching these still needs a human; reported per file.
NEEDS_HAND = [
    (r"supportsInsertion|supportsExtraction",
     "no ResourceHandler counterpart -- decide whether the guard is still needed"),
    (r"\bDebugMessages\b",
     "Fabric-internal debug helper -- remove the CALL BY HAND. A regex that matches up to the\n      first closing paren mangles a nested call and javac then aborts early on the syntax error,\n      printing a fake low error count"),
    (r"net\.fabricmc", "still references Fabric"),
    (r"\bexceptionHandler\b|\bTransactionContext\.Result\b", "check the transaction callback shape"),
]


def port(text: str) -> str:
    for pattern, replacement in SUBS:
        text = re.sub(pattern, replacement, text)
    return text


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    src_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    names = sys.argv[3:]
    if not src_dir.is_dir():
        print(f"not a directory: {src_dir}")
        return 2
    out_dir.mkdir(parents=True, exist_ok=True)

    files = [src_dir / n for n in names] if names else sorted(src_dir.glob("*.java"))
    report: dict[str, list[str]] = {}

    for path in files:
        if not path.is_file():
            print(f"missing: {path}")
            continue
        ported = port(path.read_text(encoding="utf-8"))
        target = out_dir / path.name
        target.write_text(ported, encoding="utf-8")

        notes = []
        for pattern, why in NEEDS_HAND:
            hits = len(re.findall(pattern, ported))
            if hits:
                notes.append(f"{hits}x {why}")
        if notes:
            report[path.name] = notes
        print(f"ported {path.name} -> {target}")

    if report:
        print("\nstill needs a hand:")
        for name, notes in report.items():
            print(f"  {name}")
            for note in notes:
                print(f"      {note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
