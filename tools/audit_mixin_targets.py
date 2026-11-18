#!/usr/bin/env python3
"""Check every mixin injector against the BYTECODE of the patched Minecraft jar.

Usage:
  python3 -I audit_mixin_targets.py --src src/main/java --resources src/main/resources \
      --jar <patched-minecraft.jar> [--jar more.jar ...] [--classes build/classes/java/main]

Why this exists
---------------
Create Fly's mixins target vanilla Minecraft; NeoForge runs a patched one. Each mismatch is fatal
only when Mixin applies that class, so a server boot reports them one per launch, in waves, and a
dedicated server never applies the client mixins at all.

The first audit (audit_mixins_vs_patches.py) reads NeoForge's source patches, which can't see two
kinds of change. NeoForge sometimes disables vanilla code by putting `if (false)` in front of it;
javac then drops every call inside from the bytecode, even though the patch still shows those lines
as unchanged context, often beyond the end of the hunk. That is how
LivingEntity.baseTick lost isEyeInFluid(WATER) and MobEffectUtil.hasWaterBreathing. The patches
also don't show recompilation side effects, such as renumbered lambdas and anonymous classes.

This script reads what will actually run: `javap -c -p -s` on each target class in the patched jar
ModDevGradle builds. That covers client and common mixins alike, all at once.

What it checks (per active mixin -- listed in a mixin config or added by its config plugin)
  * the target class exists in the jar
  * each injector's `method` exists there (name, and descriptor when given)
  * INVOKE / INVOKE_ASSIGN / FIELD / NEW @At targets occur in that method, and at least ordinal+1
    times when an ordinal is given (FIELD honours `opcode`); RETURN ordinals and TAIL likewise
  * @Shadow, @Accessor and @Invoker members exist in the target class (by name)

What it cannot see
  @Local / @Share resolution, slices, MixinExtras expressions, injection order between mixins, and
  NeoForge coremods (it reads the jar as patched at build time). A finding is a near-certain boot
  failure; a clean run is strong evidence, not proof. The boot test remains the authority.
"""
import argparse
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path

INJECTORS = ("Inject", "Redirect", "ModifyArg", "ModifyArgs", "ModifyVariable", "ModifyConstant",
             "ModifyExpressionValue", "ModifyReturnValue", "WrapOperation", "WrapWithCondition",
             "WrapMethod", "ModifyReceiver")
IMPORT = re.compile(r"^import\s+([\w.]+);", re.M)
WILDCARD = re.compile(r"^import\s+([\w.]+)\.\*;", re.M)
STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')
CHECKED_AT = {"INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING", "FIELD", "NEW"}
RETURN_INSN = re.compile(r"\s+\d+: [ilfda]?return\b")


# --------------------------------------------------------------------------- source parsing

def balanced(text: str, open_at: int) -> int:
    """Index just past the parenthesis that closes the one at open_at (string-aware)."""
    depth, i, n = 0, open_at, len(text)
    while i < n:
        ch = text[i]
        if ch == '"':
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == "\\" else 1
        elif ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return n


def annotations(text: str, names) -> list[tuple[str, str, int]]:
    """(name, args-without-outer-parens, end index) for each @Name(...) or bare @Name."""
    out = []
    for m in re.finditer(r"@(" + "|".join(names) + r")\b", text):
        j = m.end()
        while j < len(text) and text[j] in " \t":
            j += 1
        if j < len(text) and text[j] == "(":
            end = balanced(text, j)
            out.append((m.group(1), text[j + 1:end - 1], end))
        else:
            out.append((m.group(1), "", j))
    return out


def attr(args: str, key: str) -> str | None:
    """Raw text of `key = <value>` at the top level of an annotation's arguments."""
    m = re.search(r"(?<![\w.])" + key + r"\s*=\s*", args)
    if not m:
        return None
    i, depth, start = m.end(), 0, m.end()
    while i < len(args):
        ch = args[i]
        if ch == '"':
            i += 1
            while i < len(args) and args[i] != '"':
                i += 2 if args[i] == "\\" else 1
        elif ch in "({":
            depth += 1
        elif ch in ")}":
            if depth == 0:
                break
            depth -= 1
        elif ch == "," and depth == 0:
            break
        i += 1
    return args[start:i].strip()


def at_annotations(args: str) -> list[str]:
    """Arguments of each @At(...) in an injector, excluding those inside a slice."""
    slice_ = attr(args, "slice")
    if slice_:
        args = args.replace(slice_, "")
    return [a for _, a, _ in annotations(args, ["At"])]


def at_value(at_args: str) -> str:
    v = attr(at_args, "value")
    if v is None:
        first = STRING.match(at_args.strip())
        return first.group(1) if first else ""
    s = STRING.search(v)
    return s.group(1) if s else v


def next_member_name(text: str, pos: int) -> str:
    """Name of the field or method declared after the annotations starting at pos."""
    rest = text[pos:]
    # skip further annotations
    while True:
        rest = rest.lstrip()
        if not rest.startswith("@"):
            break
        m = re.match(r"@[\w.]+", rest)
        rest = rest[m.end():]
        if rest.lstrip().startswith("("):
            k = len(rest) - len(rest.lstrip())
            rest = rest[balanced(rest, k):]
    m = re.match(r"[^;{=(]*?([\w$]+)\s*([;(=])", rest, re.S)
    return m.group(1) if m else "?"


def resolve_class(ref: str, imports: dict[str, str], wildcards: list[str], package: str,
                  index: set[str]) -> str | None:
    """Turn `Foo`, `Foo.Bar` or `net.x.Foo.Bar` into an internal name present in the jar."""
    ref = ref.strip()
    head, _, tail = ref.partition(".")
    candidates = []
    if head in imports:
        candidates.append(imports[head] + ("." + tail if tail else ""))
    candidates += [ref, package + "." + ref] + [w + "." + ref for w in wildcards]
    for cand in candidates:
        parts = cand.split(".")
        # try every split point between package and nested classes
        for cut in range(len(parts), 0, -1):
            name = "/".join(parts[:cut]) + "".join("$" + p for p in parts[cut:])
            if name in index:
                return name
    return None


def mixin_targets(text: str, imports, wildcards, package, index) -> tuple[list[str], list[str]]:
    found, missing = [], []
    for _, args, _ in annotations(text, ["Mixin"])[:1]:
        targets_attr = attr(args, "targets")
        if targets_attr:
            for s in STRING.findall(targets_attr):
                name = s.replace(".", "/")
                (found if name in index else missing).append(name)
        value = attr(args, "value")
        if value is None and targets_attr is None:
            value = args
        for ref in re.findall(r"([\w.]+)\.class", value or ""):
            name = resolve_class(ref, imports, wildcards, package, index)
            (found.append(name) if name else missing.append(ref))
    return found, missing


# --------------------------------------------------------------------------- bytecode parsing

class ClassInfo:
    def __init__(self):
        self.methods: list[tuple[str, str, list[str]]] = []  # name, descriptor, code lines
        self.fields: set[str] = set()


def javap(classpath: str, names: list[str]) -> dict[str, ClassInfo]:
    out: dict[str, ClassInfo] = {}
    for chunk in range(0, len(names), 40):
        batch = [n.replace("/", ".") for n in names[chunk:chunk + 40]]
        proc = subprocess.run(["javap", "-c", "-p", "-s", "-cp", classpath, *batch],
                              capture_output=True, text=True)
        current, cls_name, member, pending = None, None, None, None
        for line in proc.stdout.splitlines():
            if not line.startswith(" ") and line.rstrip().endswith("{"):
                m = re.search(r"\b(?:class|interface|enum|record)\s+([\w.$]+)", line)
                cls_name = m.group(1) if m else None
                current = out.setdefault(cls_name.replace(".", "/"), ClassInfo()) if cls_name else None
                member = None
                continue
            if current is None:
                continue
            if line.startswith("  ") and not line.startswith("   "):
                header = line.strip()
                if header == "static {};":
                    pending = ("<clinit>", True)
                elif "(" in header:
                    name = header[:header.index("(")].split()[-1]
                    pending = ("<init>" if name == cls_name else name, True)
                elif header.endswith(";"):
                    pending = (header[:-1].split()[-1], False)
                else:
                    pending = None
                member = None
                continue
            stripped = line.strip()
            if stripped.startswith("descriptor:") and pending:
                name, is_method = pending
                if is_method:
                    member = (name, stripped.split(":", 1)[1].strip(), [])
                    current.methods.append(member)
                else:
                    current.fields.add(name)
                pending = None
                continue
            if member is not None and re.match(r"\s+\d+: ", line):
                member[2].append(line)
    return out


def instruction_matches(line: str, cls: str, kind: str, owner: str | None, name: str | None,
                        desc: str | None, opcode: str | None) -> bool:
    m = re.search(r"\d+:\s+(\w+).*//\s+(InterfaceMethod|Method|Field|class)\s+(\S+)", line)
    if not m:
        return False
    insn, ref_kind, ref = m.group(1), m.group(2), m.group(3)
    if kind == "NEW":
        if desc:  # constructor descriptor given: match the <init> call, not the `new`
            return (insn == "invokespecial" and ref.split(":", 1)[0].replace('"', "") == f"{owner}.<init>"
                    and ref.split(":", 1)[1] == desc)
        return insn == "new" and ref.strip('"') == owner
    if kind == "FIELD":
        if ref_kind != "Field" or (opcode and insn != opcode):
            return False
    elif ref_kind not in ("Method", "InterfaceMethod") or not insn.startswith("invoke"):
        return False
    member, _, ref_desc = ref.partition(":")
    ref_owner, _, ref_name = member.rpartition(".")
    ref_owner = ref_owner or cls  # javap omits the owner for members of the class itself
    ref_name = ref_name.strip('"')
    if owner and ref_owner != owner:
        return False
    if name and ref_name != name:
        return False
    return not desc or ref_desc == desc


def parse_target(kind: str, target: str):
    if kind == "NEW":
        t, ctor = target, None
        if t.startswith("("):
            ctor = t[:t.rindex(")") + 1] + "V"
            t = t[t.rindex(")") + 1:]
        return t.strip("L;").replace(".", "/"), None, ctor
    m = re.match(r"L([\w/$]+);([\w<>$]+)(.*)$", target) or re.match(r"([\w/$.]+)\.([\w<>$]+)(.*)$", target)
    if not m:
        return None, None, None
    owner, name, rest = m.group(1).replace(".", "/"), m.group(2), m.group(3)
    if kind == "FIELD":
        return owner, name, rest[1:] if rest.startswith(":") else None
    return owner, name, rest or None


def method_selectors(args: str) -> list[str]:
    raw = attr(args, "method")
    return STRING.findall(raw) if raw else []


def select_methods(info: ClassInfo, selector: str):
    sel = re.sub(r"^L[\w/$]+;", "", selector)
    if "*" in sel or sel.startswith("/"):
        return None  # wildcard / regex: not verified here
    name, paren, desc = sel.partition("(")
    desc = paren + desc if paren else None
    found = [m for m in info.methods if m[0] == name and (desc is None or m[1] == desc)]
    # Mixin semantics: without a descriptor (and without a `*` quantifier) a selector matches only
    # the FIRST method of that name, in class-file order -- which javap preserves. NeoForge often
    # adds an overload and turns vanilla's into a delegate; a name-only selector then lands on the
    # delegate (ModelManager.discoverModelDependencies).
    return found[:1] if desc is None else found


def candidates(info: ClassInfo, selector: str) -> str:
    """What IS there, so a missing method can be retargeted without another round trip."""
    name = re.sub(r"^L[\w/$]+;", "", selector).partition("(")[0]
    m = re.fullmatch(r"(lambda\$[\w$]+?\$)\d+", name)
    if m:  # renumbered or re-captured lambdas: show the whole family
        found = [f"{n}{d}" for n, d, _ in info.methods if n.startswith(m.group(1))]
    else:
        found = [f"{n}{d}" for n, d, _ in info.methods if n == name]
    if not found:
        return " (no method of that name)"
    return "".join("\n          candidate: " + f for f in found[:8])


# --------------------------------------------------------------------------- driver

def active_mixins(src: Path, resources: Path) -> dict[str, Path]:
    """Mixin class name -> source file, for every mixin a config or its plugin activates."""
    active = {}
    for cfg in sorted(resources.glob("*.mixins.json")):
        data = json.loads(cfg.read_text(encoding="utf-8"))
        pkg = data.get("package", "")
        names = [n for key in ("mixins", "client", "server") for n in data.get(key, [])]
        plugin = data.get("plugin")
        if plugin:
            plugin_src = src / (plugin.replace(".", "/") + ".java")
            if plugin_src.exists():
                names += [s for s in STRING.findall(plugin_src.read_text(encoding="utf-8"))
                          if re.fullmatch(r"[\w.$]*Mixin", s)]
        for n in names:
            path = src / (pkg + "." + n).replace(".", "/")
            active[pkg + "." + n] = path.with_suffix(".java")
    return active


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--src", required=True, type=Path)
    ap.add_argument("--resources", required=True, type=Path)
    ap.add_argument("--jar", required=True, action="append", type=Path)
    ap.add_argument("--classes", type=Path, help="compiled classes dir: skip mixins that were not compiled")
    opts = ap.parse_args()

    index: set[str] = set()
    for jar in opts.jar:
        with zipfile.ZipFile(jar) as z:
            index.update(n[:-6] for n in z.namelist() if n.endswith(".class"))
    classpath = ":".join(str(j) for j in opts.jar)

    mixins = active_mixins(opts.src, opts.resources)
    problems, notes, todo = [], [], []
    for fq, path in sorted(mixins.items()):
        if not path.exists():
            problems.append((fq, "config", "listed in a mixin config but the source file does not exist"))
            continue
        if opts.classes and not (opts.classes / (fq.replace(".", "/") + ".class")).exists():
            problems.append((fq, "config", "listed in a mixin config but not compiled (excluded?)"))
            continue
        text = path.read_text(encoding="utf-8")
        package = re.search(r"^package\s+([\w.]+);", text, re.M).group(1)
        imports = {f.rsplit(".", 1)[1]: f for f in IMPORT.findall(text)}
        wildcards = WILDCARD.findall(text)
        targets, missing = mixin_targets(text, imports, wildcards, package, index)
        for m in missing:
            (notes if "@Pseudo" in text else problems).append(
                (fq, "@Mixin", f"target class {m} is not in the patched jar"))
        if targets:
            todo.append((fq, text, targets))

    infos = javap(classpath, sorted({t for _, _, ts in todo for t in ts}))
    checked = 0
    for fq, text, targets in todo:
        short = fq.rsplit(".", 1)[1]
        for kind, args, end in annotations(text, INJECTORS):
            handler = next_member_name(text, end)
            optional = re.search(r"\brequire\s*=\s*0\b", args) is not None
            sink = notes if optional else problems
            for selector in method_selectors(args):
                for target in targets:
                    info = infos.get(target)
                    if info is None:
                        problems.append((fq, handler, f"javap could not read {target}"))
                        continue
                    methods = select_methods(info, selector)
                    checked += 1
                    if methods is None:
                        notes.append((fq, handler, f"selector {selector!r} not verified (wildcard/regex)"))
                        continue
                    if not methods:
                        sink.append((fq, handler, f"@{kind}: no method {selector} in {target}"
                                     + candidates(info, selector)))
                        continue
                    for at in at_annotations(args):
                        value = at_value(at)
                        if value in ("RETURN", "TAIL"):
                            # NeoForge patches add and remove early returns; an ordinal past the
                            # last one matches nothing (KeyboardHandler.keyPress, RETURN ordinal 4).
                            ordinal = attr(at, "ordinal")
                            need = int(ordinal) + 1 if value == "RETURN" and ordinal and ordinal.isdigit() else 1
                            counts = [sum(bool(RETURN_INSN.match(l)) for l in m[2]) for m in methods]
                            if max(counts, default=0) < need:
                                sink.append((fq, handler, f"@{kind} @At(\"{value}\""
                                             + (f", ordinal = {ordinal}" if ordinal else "")
                                             + f"): {target.rsplit('/', 1)[-1]}.{selector} has {max(counts, default=0)} return(s)"))
                            continue
                        if value not in CHECKED_AT:
                            continue
                        raw = attr(at, "target")
                        tgt = STRING.search(raw).group(1) if raw and STRING.search(raw) else None
                        if not tgt:
                            continue
                        owner, name, desc = parse_target("NEW" if value == "NEW" else
                                                         "FIELD" if value == "FIELD" else "INVOKE", tgt)
                        if owner is None:
                            notes.append((fq, handler, f"could not parse target {tgt!r}"))
                            continue
                        op = attr(at, "opcode")
                        opcode = op.rsplit(".", 1)[-1].lower() if op else None
                        ordinal = attr(at, "ordinal")
                        need = int(ordinal) + 1 if ordinal and ordinal.isdigit() else 1
                        count = sum(instruction_matches(line, target, value if value in ("NEW", "FIELD") else "INVOKE",
                                                        owner, name, desc, opcode)
                                    for m in methods for line in m[2])
                        if count < need:
                            what = f"{owner.rsplit('/', 1)[-1]}.{name or '<new>'}{desc or ''}"
                            where = f"{target.rsplit('/', 1)[-1]}.{selector}"
                            sink.append((fq, handler, f"@{kind} @At(\"{value}\") {what}: found {count} in {where}"
                                         + (f", ordinal {ordinal} needs {need}" if ordinal else "")))
        for kind, args, end in annotations(text, ["Shadow", "Accessor", "Invoker"]):
            member = next_member_name(text, end)
            explicit = STRING.search(args)
            name = explicit.group(1) if explicit else member
            if kind == "Shadow":
                prefix = attr(args, "prefix")
                if prefix and STRING.search(prefix) and name.startswith(STRING.search(prefix).group(1)):
                    name = name[len(STRING.search(prefix).group(1)):]
            elif not explicit:
                m = re.match(r"(?:get|set|is|call|invoke)([A-Z]\w*)", member)
                name = m.group(1)[0].lower() + m.group(1)[1:] if m else member
            for target in targets:
                info = infos.get(target)
                if info and name not in info.fields and all(m[0] != name for m in info.methods):
                    problems.append((fq, member, f"@{kind} {name} not found in {target}"))

    print(f"{len(mixins)} active mixin(s), {checked} injector/method pair(s) checked against "
          f"{len(infos)} class(es) from {', '.join(j.name for j in opts.jar)}\n")
    for title, rows in (("PROBLEMS (each one fails Mixin's apply step)", problems),
                        ("notes (optional injectors, @Pseudo, unverified selectors)", notes)):
        if not rows:
            continue
        print(f"{len(rows)} {title}")
        last = None
        for fq, handler, msg in rows:
            if fq != last:
                print(f"  {fq.rsplit('.', 1)[1]}")
                last = fq
            print(f"      {handler}: {msg}")
        print()
    if not problems:
        print("no problems: every checked target exists in the patched bytecode")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
