#!/usr/bin/env python3
"""Make every Create enum codec read the original Create's UPPERCASE enum names too.

Usage: python3 -I codemod_legacy_enums.py <src/main/java>

Rewrites `StringRepresentable.fromEnum(X::values)` to `LegacyEnumCodec.of(X::values)` (same encoding,
lenient decoding; see com.zurrtum.create.platform.LegacyEnumCodec) and adds the import. Idempotent.
Re-run after every upstream sync: a new enum codec upstream would otherwise not read old saves.
"""
import re
import sys
from pathlib import Path

IMPORT = "import com.zurrtum.create.platform.LegacyEnumCodec;"
root = Path(sys.argv[1])
changed = 0
for path in sorted(root.rglob("*.java")):
    if path.name == "LegacyEnumCodec.java":
        continue
    text = path.read_text(encoding="utf-8")
    new = re.sub(r"StringRepresentable\.fromEnum\(", "LegacyEnumCodec.of(", text)
    if new == text:
        continue
    if IMPORT not in new:
        new = re.sub(r"^(package [\w.]+;\n)", r"\1\n" + IMPORT + "\n", new, count=1, flags=re.M)
    path.write_text(new, encoding="utf-8")
    changed += 1
print(f"{changed} file(s) rewritten")
