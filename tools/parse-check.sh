#!/usr/bin/env bash
# Syntax-check every Java file the build compiles, without needing its classpath.
#
# Usage: tools/parse-check.sh
#
# Why: the port is edited by scripts that remove or rewrite whole methods, and one of them once left
# a method's parameter list and body behind after deleting its head. The braces still balanced --
# the orphaned fragment was itself balanced -- so a brace count passed and only CI caught it, after a
# full Gradle run. This stops javac right after parsing, which needs no Minecraft or NeoForge jars:
# it cannot resolve a single symbol, but it does not have to, because a syntax error is found before
# symbols are looked at.
#
# It honours the exclude patterns in build.gradle, so it checks exactly what the build compiles.
set -uo pipefail

ROOT="src/main/java"
mapfile -t patterns < <(grep -oE "exclude '[^']+'" build.gradle | sed -E "s/exclude '([^']+)'/\1/")

is_excluded() {
    local rel="$1" pat
    for pat in "${patterns[@]}"; do
        if [[ "$pat" == *'/**' ]]; then
            [[ "$rel" == "${pat%/**}"/* ]] && return 0
        elif [[ "$rel" == "$pat" ]]; then
            return 0
        fi
    done
    return 1
}

files=()
while IFS= read -r -d '' f; do
    rel="${f#"$ROOT"/}"
    is_excluded "$rel" || files+=("$f")
done < <(find "$ROOT" -name '*.java' -print0)

out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT

# The project targets Java 25, and its source uses unnamed variables (`_ -> ...`), final since
# Java 22. A local JDK older than that reports every one of them as a syntax error, so on such a JDK
# they are switched on as a preview feature -- the parse is otherwise identical.
jdk=$(javac -version 2>&1 | grep -oE 'javac [0-9]+' | grep -oE '[0-9]+')
extra=()
if [ "${jdk:-0}" -lt 22 ]; then
    extra=(--enable-preview --release "$jdk")
fi

# should-stop at PARSE: report syntax errors and stop before attribution, which is the phase that
# would need the classpath.
errors=$(javac -XDshould-stop.ifNoError=PARSE -XDshould-stop.ifError=PARSE -proc:none \
    "${extra[@]}" -encoding UTF-8 -d "$out" "${files[@]}" 2>&1 \
    | grep -v -e '^Picked up' -e '^Note:' || true)

if [ -n "$errors" ]; then
    echo "$errors"
    echo
    echo "syntax errors found in ${#files[@]} compiled files"
    exit 1
fi
echo "parsed ${#files[@]} compiled files: no syntax errors"
