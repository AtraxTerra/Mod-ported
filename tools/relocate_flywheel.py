#!/usr/bin/env python3
"""Give Create Fly's embedded Flywheel its own resource namespace, `create_flywheel` (port-only).

Usage: python3 -I tools/relocate_flywheel.py [ROOT] [--undo]

Why
---
Create Fly carries its own copy of Flywheel (1.0.6, under com.zurrtum.create.client.flywheel) and
upstream keeps its shaders, textures and lang under `assets/flywheel/`. CrankShaft -- the standalone
Flywheel, which NTM NEXT and NTM Space require on the client -- ships its shaders under
`assets/flywheel/` as well. On NeoForge every mod's resources sit in one pack, so the two copies
overwrite each other file by file: 55 shader files share a path, 27 of them differ, and the client
stops at "Warming CrankShaft shaders" (culling/uber18.glsl fails to compile). See PORTING.md, 1f.

What it does
------------
Forward (the default), on a tree in upstream's layout:
  - moves src/main/resources/assets/flywheel/ to src/main/resources/assets/create_flywheel/;
  - rewrites every `flywheel:` resource location in the shaders under assets/ (the `#include`
    lines, and one comment) to `create_flywheel:`;
  - rewrites the five Java strings that name the namespace (Flywheel.MOD_ID first: ResourceUtil.rl,
    the config file name and the loggers follow it).

--undo is the exact reverse. The upstream sync workflow undoes the relocation, applies upstream's
diff to upstream's own layout, then runs this again, so upstream's shader changes keep applying.

Both directions are idempotent: running one twice changes nothing the second time.
"""
import pathlib
import re
import shutil
import sys

OLD = 'flywheel'
NEW = 'create_flywheel'
SHADERS = ('.glsl', '.vert', '.frag', '.comp')

# File -> strings naming the namespace, with {ns} where the namespace goes. Each must be found
# exactly once, in one form or the other, or the script stops: upstream moved something.
JAVA = {
    'src/main/java/com/zurrtum/create/client/flywheel/impl/Flywheel.java':
        ['MOD_ID = "{ns}"'],
    'src/main/java/com/zurrtum/create/client/flywheel/backend/compile/FlwProgramsReloader.java':
        ['super("{ns}")'],
    'src/main/java/com/zurrtum/create/client/mixin/ClientPackSourceMixin.java':
        ['"{ns}",'],
    'src/main/java/com/zurrtum/create/client/flywheel/impl/BackendArgument.java':
        ['"{ns}:off"'],
    'src/main/java/com/zurrtum/create/client/flywheel/backend/engine/MaterialEncoder.java':
        ['"{ns}:flywheel/internal/packed_material.glsl"'],
}


def move_tree(source: pathlib.Path, target: pathlib.Path) -> int:
    """Moves every file of source into target (same relative path), then drops source."""
    if not source.is_dir():
        return 0
    moved = 0
    for path in sorted(p for p in source.rglob('*') if p.is_file()):
        destination = target / path.relative_to(source)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(path), str(destination))
        moved += 1
    shutil.rmtree(source)
    return moved


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    undo = '--undo' in sys.argv[1:]
    root = pathlib.Path(args[0] if args else '.').resolve()
    old, new = (NEW, OLD) if undo else (OLD, NEW)
    assets = root / 'src/main/resources/assets'
    if not assets.is_dir():
        print(f'error: {assets} not found', file=sys.stderr)
        return 1

    moved = move_tree(assets / old, assets / new)

    pattern = re.compile(r'(?<![A-Za-z0-9_])' + re.escape(old) + ':')
    files = refs = 0
    for path in sorted(assets.rglob('*')):
        if path.is_file() and path.suffix in SHADERS:
            text = path.read_text(encoding='utf-8')
            changed, count = pattern.subn(new + ':', text)
            if count:
                path.write_text(changed, encoding='utf-8')
                files += 1
                refs += count

    edited = 0
    for name, templates in JAVA.items():
        path = root / name
        text = path.read_text(encoding='utf-8')
        for template in templates:
            before, after = template.format(ns=old), template.format(ns=new)
            if text.count(before) == 1:
                text = text.replace(before, after)
                edited += 1
            elif text.count(after) != 1:
                print(f'error: {name}: neither {before!r} nor {after!r} found exactly once', file=sys.stderr)
                return 1
        path.write_text(text, encoding='utf-8')

    print(f'{"undo" if undo else "relocate"}: assets/{old} -> assets/{new}: {moved} files moved; '
          f'{refs} shader references rewritten in {files} files; {edited} Java strings changed')
    return 0


if __name__ == '__main__':
    sys.exit(main())
