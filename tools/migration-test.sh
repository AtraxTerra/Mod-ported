#!/usr/bin/env bash
# World migration test: Create: Adapted (what the survival server runs) -> this Create Fly port.
#
# Two production NeoForge servers, one after the other, on the SAME world:
#   A. Create: Adapted 26.2-1.31 (+ the official create-1.21.1-6.0.10.jar it reads its content from)
#      places every Create block on a grid via RCON, fills the ones that hold things (hoppers feed
#      items in, fluids are merged in), builds a few extras (a bearing contraption, a powered shaft
#      line, a belt with items, a chest of Create items with data components) and records
#      `data get` for each.
#   B. this port's jar replaces it; for every position it checks that the block is still there, that
#      its block entity kept its keys and that the same items and fluids are still inside.
# Both third-party jars are downloaded for the test only and never leave the runner.
#
# Usage: tools/migration-test.sh <create-fly.jar> [neoforge-version] [addon.jar ...]
#
# For an addon (Diesel Generators): its jars for phase B go after the NeoForge version; set
#   MIGRATION_EXTRA_A    space-separated URLs of the jars it replaces, installed for phase A only;
#   MIGRATION_BLOCKSTATES space-separated assets/<namespace>/blockstates dirs whose blocks join the grid.
#   MIGRATION_SKIP       a regex of block ids left out of the grid (ones that crash the phase A server)
set -uo pipefail

FLY_JAR="$(realpath "$1")"
NEO="${2:-26.2.0.93}"
EXTRA_B=()
for jar in "${@:3}"; do EXTRA_B+=("$(realpath "$jar")"); done
EXTRA_A="${MIGRATION_EXTRA_A:-}"
BLOCKSTATE_DIRS="$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/assets/create/blockstates"
for dir in ${MIGRATION_BLOCKSTATES:-}; do BLOCKSTATE_DIRS="$BLOCKSTATE_DIRS $(realpath "$dir")"; done
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
mkdir -p ../phase-a-only
for url in $EXTRA_A; do
    name="adapted-extra-$(basename "${url%%\?*}")"
    curl -fsSL -o "mods/$name" "$url" || { echo "::error::could not download $url"; exit 1; }
done
ls -l mods config/create-adapted/external-assets
echo "::endgroup::"

# Positions: one per blockstate file (same ids on both sides), two apart, around spawn.
# shellcheck disable=SC2086
python3 -I - $BLOCKSTATE_DIRS > grid.tsv <<'EOF'
import math, os, re, sys
# fluid source blocks would flow over the grid (Create's two, Diesel Generators' fuels and cements)
FLUIDS = {"honey", "chocolate", "biodiesel", "crude_oil", "diesel", "ethanol", "gasoline", "plant_oil"}
ids = []
for d in sys.argv[1:]:
    namespace = os.path.basename(os.path.dirname(d.rstrip("/")))
    names = (f[:-5] for f in os.listdir(d) if f.endswith(".json"))
    ids += sorted(f"{namespace}:{n}" for n in names if n not in FLUIDS and not n.endswith("_cement"))
skip = os.environ.get("MIGRATION_SKIP")
if skip:
    ids = [i for i in ids if not re.search(skip, i)]
side = math.ceil(math.sqrt(len(ids) + 1))
i = 0
for block in ids:
    while True:
        x, z = (i % side - side // 2) * 2, (i // side - side // 2) * 2
        i += 1
        if (x, z) != (0, 0):
            break
    print(f"{x}\t-60\t{z}\t{block}")
EOF
half=$(( $(awk -F'\t' '{x=$1<0?-$1:$1; z=$3<0?-$3:$3; m=x>z?x:z; if(m>M)M=m} END{print M+2}' grid.tsv) ))
reach=$(( half + 8 ))  # the extras are built on a row just outside the grid

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
printf 'setworldspawn 0 -60 0\nforceload add %d %d %d %d\n' "-$reach" "-$reach" "$reach" "$reach" | rcon > /dev/null
sleep 10
awk -F'\t' '{print "setblock "$1" "$2" "$3" "$4}' grid.tsv | rcon > placeA.tsv
sleep 20
python3 -I "$TOOLS/migration_fill.py" grid.tsv "$half" fill.cmds unfill.cmds extra.cmds fresh.cmds
rcon < fill.cmds > fillA.tsv
sleep 40
rcon < unfill.cmds > /dev/null
sleep 2
rcon < extra.cmds > extraA.tsv
awk -F'\t' '{print "execute if block "$1" "$2" "$3" "$4}' grid.tsv | rcon > presentA.tsv
awk -F'\t' '{print "data get block "$1" "$2" "$3}' grid.tsv | rcon > dataA.tsv
stop
echo "::endgroup::"

echo "::group::Phase B: Create Fly loads the same world"
mv mods/create-adapted.jar mods/adapted-extra-* ../phase-a-only/ 2>/dev/null
cp "$FLY_JAR" "${EXTRA_B[@]}" mods/
ls mods
if ! boot B; then
    echo "::error::Create Fly server did not start on the migrated world"; status=1
else
    sleep 10
    awk -F'\t' '{print "execute if block "$1" "$2" "$3" "$4}' grid.tsv | rcon > presentB.tsv
    awk -F'\t' '{print "data get block "$1" "$2" "$3}' grid.tsv | rcon > dataB.tsv
    rcon < extra.cmds > extraB.tsv
    rcon < fresh.cmds > /dev/null && sleep 8 && rcon < fresh.cmds.get > freshB.tsv
    stop
fi
echo "::endgroup::"

python3 -I "$TOOLS/migration_report.py" | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"

errors() {  # error lines of a server log, each with the stack trace that follows it
    awk '/\/(ERROR|FATAL)\]|Skipping BlockEntity|Failed to load|Couldn.t load/ && !/Missing translations/ {print; n=40; next}
         n > 0 && /^(\t|\s+at |Caused by|\s*\.\.\. [0-9]+ more|[a-z]+\.[A-Za-z.$]+(Exception|Error))/ {print; n--; next} {n=0}' "$1" 2>/dev/null | head -400
}
echo "::group::Phase B log: errors with stack traces"
errors server-B.log
echo "::endgroup::"
echo "::group::Phase A log: errors with stack traces"
errors server-A.log | head -120
echo "::endgroup::"
echo "::group::Full NBT, both phases"
cat dumps.md 2>/dev/null
echo "::endgroup::"
echo "::group::A contraption built by Create Fly itself (phase B)"
cut -f2 freshB.tsv 2>/dev/null
echo "::endgroup::"
echo "::group::Fill commands and their replies (phase A)"
cat fillA.tsv 2>/dev/null | cut -c1-400
echo "::endgroup::"
echo "::group::Phase A log tail"
tail -40 server-A.log
echo "::endgroup::"
exit "$status"
