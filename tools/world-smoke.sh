#!/usr/bin/env bash
# Server half of the in-world smoke test, and gate 7 (the RCON phantom scan).
#
# Boots the dedicated server with RCON, places every Create block on a grid around spawn with
# /setblock, lets the server tick them, saves, stops, and moves the world into run/saves/<name> so
# the client can quick-play straight into it (client-boot-test.sh <timeout> <settle> <name>).
#
#   * gate 7: `setblock` answers "Unknown block type" for an id that is not registered, so every
#     blockstate file that has no block behind it shows up here, by name.
#   * server ticks: each placed block entity ticks for TICK_SECONDS on its own -- a block entity that
#     cannot survive that crashes the server, which this reports.
#   * the client then renders all of them: block entity renderers, Flywheel visuals, models.
#
# Blocks are two apart so kinetic and fluid blocks do not connect; Create's fluid source blocks are
# skipped so they do not flood the grid.
#
# Usage: tools/world-smoke.sh [world-name] [timeout-seconds]
set -uo pipefail

WORLD="${1:-ci}"
TIMEOUT="${2:-600}"
TICK_SECONDS=20
RUN_DIR="run"
LOG="world-smoke.log"
PASSWORD="ci-$$-$RANDOM"
SKIP='^(honey|chocolate)$'

mkdir -p "$RUN_DIR"
printf 'eula=true\n' > "$RUN_DIR/eula.txt"
cat > "$RUN_DIR/server.properties" <<PROPS
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

# One setblock per blockstate file, on a grid centred on spawn, leaving the spawn column free.
ids=$(ls src/main/resources/assets/create/blockstates | sed 's/\.json$//' | grep -vE "$SKIP" | sort)
total=$(echo "$ids" | wc -l)
side=$(python3 -I -c "import math; print(math.ceil(math.sqrt($total + 1)))")
# A dedicated server with nobody online keeps no chunks loaded in 26.x, and setblock answers
# "That position is not loaded". The grid is force-loaded first, in a separate batch, with a pause
# for the chunks to arrive.
half=$(( (side / 2 + 1) * 2 ))
printf 'forceload add %d %d %d %d\n' "-$half" "-$half" "$half" "$half" > world-smoke-forceload.txt
{
    echo "setworldspawn 0 -60 0"
    i=0
    for id in $ids; do
        gx=$((i % side)); gz=$((i / side))
        x=$(( (gx - side / 2) * 2 )); z=$(( (gz - side / 2) * 2 ))
        if [ "$x" -eq 0 ] && [ "$z" -eq 0 ]; then i=$((i + 1)); gx=$((i % side)); gz=$((i / side)); x=$(( (gx - side / 2) * 2 )); z=$(( (gz - side / 2) * 2 )); fi
        echo "setblock $x -60 $z create:$id"
        i=$((i + 1))
    done
} > world-smoke-commands.txt

echo "::group::Booting the server with RCON"
./gradlew runServer --console=plain --stacktrace > "$LOG" 2>&1 &
GRADLE_PID=$!
up=0
for _ in $(seq 1 "$TIMEOUT"); do
    if grep -q 'Done ([0-9.]*s)' "$LOG" 2>/dev/null; then up=1; break; fi
    kill -0 "$GRADLE_PID" 2>/dev/null || break
    sleep 1
done
echo "::endgroup::"

status=0
if [ "$up" -eq 1 ]; then
    python3 -I tools/rcon.py --password "$PASSWORD" < world-smoke-forceload.txt > world-smoke-results.tsv
    sleep 10
    python3 -I tools/rcon.py --password "$PASSWORD" < world-smoke-commands.txt >> world-smoke-results.tsv
    sleep "$TICK_SECONDS"
    printf 'save-all flush\nstop\n' | python3 -I tools/rcon.py --password "$PASSWORD" >> world-smoke-results.tsv 2>/dev/null || true
    for _ in $(seq 1 120); do kill -0 "$GRADLE_PID" 2>/dev/null || break; sleep 1; done
fi
kill -0 "$GRADLE_PID" 2>/dev/null && { kill -INT "$GRADLE_PID"; sleep 10; kill -9 "$GRADLE_PID" 2>/dev/null; }

placed=$(grep -c $'\tChanged the block' world-smoke-results.tsv 2>/dev/null || true)
unknown=$(grep -c 'Unknown block type' world-smoke-results.tsv 2>/dev/null || true)
other=$(grep '^setblock' world-smoke-results.tsv 2>/dev/null | grep -v $'\tChanged the block' | grep -vc 'Unknown block type' || true)
shopt -s nullglob


{
    echo "## In-world smoke test: server half (gate 7)"
    echo
    if [ "$up" -ne 1 ]; then
        echo ":x: Server never reached \`Done\`."; status=1
    else
        echo "Placed **${placed:-0}** of ${total} Create blocks; ${TICK_SECONDS}s of ticking."
        echo
        if [ "${unknown:-0}" -gt 0 ]; then
            echo ":x: **${unknown}** blockstate file(s) with no registered block (phantoms):"
            echo '```'; grep 'Unknown block type' world-smoke-results.tsv | cut -f1 | head -40; echo '```'
            status=1
        else
            echo ":white_check_mark: Every blockstate file has a registered block."
        fi
        if [ "${other:-0}" -gt 0 ]; then
            echo
            echo "${other} setblock(s) answered something else:"
            echo '```'; grep '^setblock' world-smoke-results.tsv | grep -v $'\tChanged the block' | grep -v 'Unknown block type' | head -20; echo '```'
        fi
    fi
    if grep -qE 'Exception ticking|Encountered an unexpected exception|/FATAL\]' "$LOG"; then
        echo; echo ":x: The server crashed or threw while ticking:"
        echo '```'; grep -E 'Exception ticking|Encountered an unexpected exception|/FATAL\]|Caused by|at com\.zurrtum' "$LOG" | head -30; echo '```'
        status=1
    fi
} | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"

if [ -d "$RUN_DIR/world" ]; then
    mkdir -p "$RUN_DIR/saves"
    rm -rf "$RUN_DIR/saves/$WORLD"
    mv "$RUN_DIR/world" "$RUN_DIR/saves/$WORLD"
    echo "world moved to $RUN_DIR/saves/$WORLD"
else
    echo "(no world was written)"; status=1
fi
exit "$status"
