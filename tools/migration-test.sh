#!/usr/bin/env bash
# World migration test: Create: Adapted (what the survival server runs) -> this Create Fly port.
#
# Two production NeoForge servers, one after the other, on the SAME world:
#   A. Create: Adapted 26.2-1.31 (+ the official create-1.21.1-6.0.10.jar it reads its content from)
#      places every Create block on a grid via RCON and records `data get block` for each.
#   B. this port's jar replaces it; for every position it checks that the block is still there and
#      that its block entity still carries the keys it had.
# Both third-party jars are downloaded for the test only and never leave the runner.
#
# Usage: tools/migration-test.sh <create-fly.jar> [neoforge-version]
set -uo pipefail

FLY_JAR="$(realpath "$1")"
NEO="${2:-26.2.0.93}"
TOOLS="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(pwd)/migration"
ADAPTED_URL="https://mediafilez.forgecdn.net/files/9075/359/create-adapted-26.2-1.31-public-external-assets.jar"
ADAPTED_FALLBACK="https://www.cursemaven.com/curse/maven/create-adapted-1651484/9075359/create-adapted-1651484-9075359.jar"
CREATE_ASSETS_URL="https://api.modrinth.com/maven/maven/modrinth/LNytGWDc/UjX6dr61/LNytGWDc-UjX6dr61.jar"
PASSWORD="mig-$$-$RANDOM"
status=0

mkdir -p "$ROOT" && cd "$ROOT"
echo "::group::Installing NeoForge $NEO server"
curl -fsSL -o installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO/neoforge-$NEO-installer.jar"
java -jar installer.jar --installServer . > installer.log 2>&1 || { tail -20 installer.log; exit 1; }
printf 'eula=true\n' > eula.txt
cat > server.properties <<PROPS
level-type=minecraft\:flat
online-mode=false
max-players=1
view-distance=4
simulation-distance=4
spawn-protection=0
enable-rcon=true
rcon.port=25575
rcon.password=${PASSWORD}
PROPS
echo "-Xmx3G" > user_jvm_args.txt
echo "::endgroup::"

echo "::group::Fetching Create: Adapted and its asset source"
mkdir -p mods config/create-adapted/external-assets
curl -fsSL -o mods/create-adapted.jar "$ADAPTED_URL" || curl -fsSL -o mods/create-adapted.jar "$ADAPTED_FALLBACK" || { echo "::error::could not download Create: Adapted"; exit 1; }
curl -fsSL -o config/create-adapted/external-assets/create-1.21.1-6.0.10.jar "$CREATE_ASSETS_URL" || { echo "::error::could not download Create 6.0.10"; exit 1; }
ls -l mods config/create-adapted/external-assets
echo "::endgroup::"

# Positions: one per Create blockstate file (same ids in both mods), two apart, around spawn.
python3 -I - "$TOOLS/../src/main/resources/assets/create/blockstates" > grid.tsv <<'EOF'
import math, os, sys
ids = sorted(f[:-5] for f in os.listdir(sys.argv[1]) if f.endswith(".json") and f[:-5] not in ("honey", "chocolate"))
side = math.ceil(math.sqrt(len(ids) + 1))
i = 0
for block in ids:
    while True:
        x, z = (i % side - side // 2) * 2, (i // side - side // 2) * 2
        i += 1
        if (x, z) != (0, 0):
            break
    print(f"{x}\t-60\t{z}\tcreate:{block}")
EOF
half=$(( $(awk -F'\t' '{x=$1<0?-$1:$1; z=$3<0?-$3:$3; m=x>z?x:z; if(m>M)M=m} END{print M+2}' grid.tsv) ))

boot() {  # $1 = phase name; leaves the server running, PID in SERVER_PID
    ./run.sh nogui > "server-$1.log" 2>&1 &
    SERVER_PID=$!
    for _ in $(seq 1 600); do
        grep -q 'Done ([0-9.]*s)' "server-$1.log" && return 0
        kill -0 "$SERVER_PID" 2>/dev/null || return 1
        sleep 1
    done
    return 1
}
rcon() { python3 -I "$TOOLS/rcon.py" --password "$PASSWORD"; }
stop() {
    printf 'save-all flush\nstop\n' | rcon > /dev/null 2>&1 || true
    for _ in $(seq 1 120); do kill -0 "$SERVER_PID" 2>/dev/null || return 0; sleep 1; done
    kill -9 "$SERVER_PID" 2>/dev/null || true
}

echo "::group::Phase A: Create: Adapted places every block"
if ! boot A; then
    echo "::error::Create: Adapted server did not start"; tail -60 server-A.log; exit 1
fi
printf 'setworldspawn 0 -60 0\nforceload add %d %d %d %d\n' "-$half" "-$half" "$half" "$half" | rcon > /dev/null
sleep 10
awk -F'\t' '{print "setblock "$1" "$2" "$3" "$4}' grid.tsv | rcon > placeA.tsv
sleep 20
awk -F'\t' '{print "data get block "$1" "$2" "$3}' grid.tsv | rcon > dataA.tsv
stop
echo "::endgroup::"

echo "::group::Phase B: Create Fly loads the same world"
mv mods/create-adapted.jar ../create-adapted.jar.bak
cp "$FLY_JAR" mods/
if ! boot B; then
    echo "::error::Create Fly server did not start on the migrated world"; status=1
else
    sleep 10
    awk -F'\t' '{print "execute if block "$1" "$2" "$3" "$4}' grid.tsv | rcon > presentB.tsv
    awk -F'\t' '{print "data get block "$1" "$2" "$3}' grid.tsv | rcon > dataB.tsv
    stop
fi
echo "::endgroup::"

python3 -I - <<'EOF' | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"
import re
grid = [l.rstrip("\n").split("\t") for l in open("grid.tsv")]
def load(name):
    try:
        return [l.rstrip("\n").split("\t", 1)[1] if "\t" in l else "" for l in open(name)]
    except FileNotFoundError:
        return []
placeA, dataA, presentB, dataB = load("placeA.tsv"), load("dataA.tsv"), load("presentB.tsv"), load("dataB.tsv")
def keys(resp):
    m = re.search(r"has the following block data: (\{.*\})", resp)
    return set(re.findall(r"[{,]\s*\"?([A-Za-z_][\w]*)\"?\s*:", m.group(1))) if m else None
placed = sum("Changed the block" in r for r in placeA)
print("## World migration: Create: Adapted -> Create Fly\n")
print(f"Phase A (Create: Adapted): placed **{placed}** of {len(grid)} blocks.")
if not presentB:
    print("\n:x: Phase B produced no results (server did not start or RCON failed)."); raise SystemExit
lost_blocks, lost_be, lost_keys = [], [], []
for i, (x, y, z, block) in enumerate(grid):
    if i >= len(placeA) or "Changed the block" not in placeA[i]:
        continue
    if i < len(presentB) and "Test passed" not in presentB[i]:
        lost_blocks.append(block); continue
    a, b = keys(dataA[i]) if i < len(dataA) else None, keys(dataB[i]) if i < len(dataB) else None
    if a is not None and b is None:
        lost_be.append(block)
    elif a and b is not None and (a - b) - {"id", "x", "y", "z"}:
        lost_keys.append((block, sorted((a - b) - {"id", "x", "y", "z"})))
print(f"\n- blocks gone after the switch: **{len(lost_blocks)}**")
for b in lost_blocks[:30]: print(f"  - `{b}`")
print(f"- block entities gone (block kept, no data): **{len(lost_be)}**")
for b in lost_be[:30]: print(f"  - `{b}`")
print(f"- block entities that kept their block but lost NBT keys: **{len(lost_keys)}**")
for b, k in lost_keys[:40]: print(f"  - `{b}`: {', '.join(k)}")
EOF

echo "::group::Phase B log: errors and Create warnings"
grep -E "/(ERROR|FATAL)\]|Skipping BlockEntity|Failed to load|Unknown|Couldn't load" server-B.log 2>/dev/null | grep -v "Missing translations" | head -80
echo "::endgroup::"
echo "::group::Phase A log tail"
tail -40 server-A.log
echo "::endgroup::"
exit "$status"
