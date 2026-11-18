#!/usr/bin/env bash
# Boot the dedicated server and fail unless it reaches "Done".
#
# This is gate 2 of the validation stack, and the first one that says anything about runtime. A
# green compile proves nothing about mixins: they are applied by name at class load, so a mixin
# targeting a member that moved compiles perfectly and fails when the class is first touched.
#
# The dedicated server is the authoritative check for COMMON mixins specifically. It never loads
# the "client" array of a mixin config, so a server that boots clean says nothing about the 66
# client mixins -- those need a real client, which is gate 5.
#
# Mixin faults also surface in WAVES: a registration failure hides the datapack-load ones, which
# hide the world-load ones. So this greps the log even when the boot succeeds, and fails on any
# APPLY failure found, rather than trusting "Done" on its own.
#
# Usage: tools/boot-test.sh [timeout-seconds]
set -uo pipefail

TIMEOUT="${1:-600}"
LOG="boot-test.log"
RUN_DIR="run"

echo "::group::Preparing the run directory"
mkdir -p "$RUN_DIR"
# The server refuses to start without this. Accepting it here is accepting it for a throwaway CI
# container, not on anyone's behalf.
printf 'eula=true\n' > "$RUN_DIR/eula.txt"
# Keep the boot cheap: no world generation beyond spawn, no network listener worth scanning.
cat > "$RUN_DIR/server.properties" <<'PROPS'
level-type=minecraft\:flat
online-mode=false
max-players=1
view-distance=4
simulation-distance=4
spawn-protection=0
sync-chunk-writes=false
PROPS
echo "::endgroup::"

echo "::group::Available run tasks"
# If the run task is not called runServer in this ModDevGradle version, say so plainly rather
# than failing with an empty log.
./gradlew tasks --all --console=plain 2>&1 | grep -iE "^run[A-Za-z]*" || echo "(no run* tasks listed)"
echo "::endgroup::"

echo "::group::Booting the server (timeout ${TIMEOUT}s)"
./gradlew runServer --console=plain --stacktrace > "$LOG" 2>&1 &
GRADLE_PID=$!

reached_done=0
for _ in $(seq 1 "$TIMEOUT"); do
    if grep -q 'Done ([0-9.]*s)' "$LOG" 2>/dev/null; then
        reached_done=1
        break
    fi
    # If Gradle exits before "Done", the boot failed and waiting out the timeout is pointless.
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
        break
    fi
    sleep 1
done

# Ask the server to stop cleanly so the log is flushed, then make sure it is gone.
if kill -0 "$GRADLE_PID" 2>/dev/null; then
    kill -INT "$GRADLE_PID" 2>/dev/null || true
    for _ in $(seq 1 30); do
        kill -0 "$GRADLE_PID" 2>/dev/null || break
        sleep 1
    done
    kill -9 "$GRADLE_PID" 2>/dev/null || true
fi
echo "::endgroup::"

# ---------------------------------------------------------------------------
# Verdict
# ---------------------------------------------------------------------------
status=0

mixin_failures=$(grep -cE 'FAILED during APPLY|was not applied|Found a remappable @Shadow' "$LOG" 2>/dev/null || true)
: "${mixin_failures:=0}"

{
    echo "## Server boot"
    echo
    if [ "$reached_done" -eq 1 ]; then
        echo ":white_check_mark: Reached \`Done\`."
    else
        echo ":x: Never reached \`Done\` within ${TIMEOUT}s."
        status=1
    fi
    echo

    if [ "$mixin_failures" -gt 0 ]; then
        echo ":x: **$mixin_failures mixin failure(s)** in the log. A boot that reaches \`Done\` with"
        echo "these present is not a pass: mixin faults surface in waves, and each one hides the next."
        echo
        echo '```'
        grep -E 'FAILED during APPLY|was not applied|Found a remappable @Shadow' "$LOG" | head -40
        echo '```'
        status=1
    else
        echo ":white_check_mark: No mixin APPLY failures in the log."
    fi
    echo

    if [ "$status" -ne 0 ]; then
        echo "### Last 60 lines"
        echo
        echo '```'
        tail -60 "$LOG"
        echo '```'
        echo
        echo "A clean server boot still says nothing about the 66 client mixins -- a dedicated"
        echo "server never loads a mixin config's \`client\` array. That needs a real client."
    fi
} | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"

# The verdict above goes to the run summary; the log tail goes to stdout unconditionally, because
# the summary is not readable from the job log and a failing gate is useless if its reason is not.
# A tail is nearly useless on its own: a Gradle failure ends in eighty lines of its own stack
# frames and the cause sits far above them. So each source of truth is printed separately, with
# stack frames filtered, because the first three runs of this gate each failed with nothing to go
# on for a different reason.
strip_frames() {
    grep -vE "^[[:space:]]+at |^[[:space:]]*\.\.\. [0-9]+ more" "$1"
}

if [ -s "$LOG" ]; then
    echo "::group::Why Gradle failed"
    sed -n '/^FAILURE:/,/^\* Get more help/p' "$LOG" | head -40
    echo "::endgroup::"

    echo "::group::Console, stack frames filtered (last 120 lines)"
    strip_frames "$LOG" | tail -120
    echo "::endgroup::"
else
    echo "(no console log -- Gradle never started)"
fi

# The game keeps its own log and writes crash reports to files, and that is where a startup death
# explains itself. The console showed the server exiting with code 1 and nothing else.
if [ -f "$RUN_DIR/logs/latest.log" ]; then
    echo "::group::run/logs/latest.log, stack frames filtered (last 100 lines)"
    strip_frames "$RUN_DIR/logs/latest.log" | tail -100
    echo "::endgroup::"
else
    echo "(no run/logs/latest.log -- the game died before opening its own log)"
fi

shopt -s nullglob
reports=("$RUN_DIR"/crash-reports/*.txt)
if [ ${#reports[@]} -gt 0 ]; then
    for report in "${reports[@]}"; do
        echo "::group::Crash report $(basename "$report")"
        head -80 "$report"
        echo "::endgroup::"
    done
else
    echo "(no crash report written)"
fi

exit "$status"
