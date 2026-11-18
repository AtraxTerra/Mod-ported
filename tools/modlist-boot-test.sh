#!/usr/bin/env bash
# Boot a production NeoForge server with the survival server's whole mod list, this port and its
# addons in place of Create: Adapted, and report what goes wrong: missing dependencies, mixin
# failures, duplicate ids, crashes, errors -- then place a few Create blocks and tick them.
#
# Usage: tools/modlist-boot-test.sh <mods-dir> <jar> [jar ...]
#   <mods-dir>  the other mods, as fetched by fetch_server_mods.py
#   <jar>       Create Fly and its addons (Diesel Generators)
set -uo pipefail

MODS="$(realpath "$1")"; shift
NEO="${MODLIST_NEOFORGE:-26.2.0.93}"
TOOLS="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(pwd)/modlist-server"
PASSWORD="ml-$$-$RANDOM"
status=0

rm -rf "$ROOT" && mkdir -p "$ROOT/mods" && cp "$MODS"/*.jar "$ROOT/mods/" && cp "$@" "$ROOT/mods/"
cd "$ROOT"
echo "::group::Installing NeoForge $NEO server ($(ls mods | wc -l) mods)"
curl -fsSL -o installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO/neoforge-$NEO-installer.jar"
java -jar installer.jar --installServer . > installer.log 2>&1 || { tail -20 installer.log; exit 1; }
printf 'eula=true\n' > eula.txt
printf 'level-type=minecraft\\:flat\nonline-mode=false\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\nenable-rcon=true\nrcon.port=25575\nrcon.password=%s\n' "$PASSWORD" > server.properties
echo "-Xmx4G" > user_jvm_args.txt
ls mods
echo "::endgroup::"

./run.sh nogui > server.log 2>&1 &
PID=$!
done=0
for _ in $(seq 1 900); do
    grep -q 'Done ([0-9.]*s)' server.log && { done=1; break; }
    kill -0 "$PID" 2>/dev/null || break
    sleep 1
done
if [ "$done" = 1 ]; then
    rcon() { python3 -I "$TOOLS/rcon.py" --password "$PASSWORD"; }
    printf 'forceload add -16 -16 16 16\n' | rcon > /dev/null
    sleep 5
    i=0
    for b in create:creative_motor create:shaft create:mechanical_press create:basin create:fluid_tank create:item_vault \
             create:mechanical_crafter create:deployer create:chute create:depot create:millstone \
             createdieselgenerators:diesel_engine createdieselgenerators:oil_barrel createdieselgenerators:burner; do
        echo "setblock $((i * 2 - 12)) -60 4 $b"; i=$((i + 1))
    done | rcon > placed.tsv
    sleep 20
    printf 'save-all flush\nstop\n' | rcon > /dev/null 2>&1
    for _ in $(seq 1 120); do kill -0 "$PID" 2>/dev/null || break; sleep 1; done
fi
kill -9 "$PID" 2>/dev/null

{
    echo "## Survival mod list + this port: server boot"
    echo
    if [ "$done" = 1 ]; then echo ":white_check_mark: reached \`Done\` with $(ls mods | wc -l) mods"; else echo ":x: did not reach \`Done\`"; status=1; fi
    placed=$(grep -c "Changed the block" placed.tsv 2>/dev/null || echo 0)
    echo "- Create / Diesel Generators blocks placed and ticked: $placed"
    for pattern in "Missing or unsupported mandatory dependencies" "Mixin apply failed\|FAILED during APPLY\|was not applied\|InvalidMixinException\|MixinTransformerError" \
                   "Duplicate mods\|duplicate mod" "has thrown an exception trying to write state\|Failed to load data for block entity"; do
        n=$(grep -c "$pattern" server.log 2>/dev/null); n=${n:-0}
        echo "- \`${pattern%%\\*}\`...: $n"
    done
    if compgen -G "crash-reports/*.txt" > /dev/null; then
        echo "- :x: crash report: $(ls crash-reports | head -1)"; status=1
        echo '```'; grep -m1 -A25 "^Description\|^---- Minecraft Crash" crash-reports/*.txt | head -40; echo '```'
    fi
} | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"
[ "$done" = 1 ] || status=1   # (the block above runs in a subshell)
compgen -G "crash-reports/*.txt" > /dev/null && status=1
echo "::group::Errors and warnings"
grep -E "/(ERROR|FATAL|WARN)\]" server.log | grep -viE "Missing translations|No key layers|Can't keep up" | head -250
echo "::endgroup::"
echo "::group::Server log tail"
tail -80 server.log
echo "::endgroup::"
exit "$status"
