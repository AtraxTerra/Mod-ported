#!/usr/bin/env bash
# Boot the CLIENT headlessly and report what a server boot cannot see.
#
# Gates 4 and 5 of the validation stack, as far as CI can take them:
#   * client mixins -- a dedicated server never applies a mixin config's "client" array, and with
#     defaultRequire = 1 a client mixin that misses its target crashes the client the moment the
#     class loads. The bytecode audit checks targets; this checks that they actually apply,
#     @Local captures and all.
#   * the bake gate -- every blockstate, model and texture is resolved during the first resource
#     reload, and failures are logged there ("Missing textures in model", "Unable to load model",
#     ...). A purple or invisible block in game is one of those lines.
#
# The client runs under Xvfb with Mesa's software OpenGL (llvmpipe), so it is slow and nothing
# here judges what it draws. Success means: the first resource reload completes (the block atlas
# is created) and the client is still alive SETTLE seconds later -- on the title screen, with
# every client mixin that loads at startup applied. World rendering and in-game behaviour stay
# gate 8: a person playing.
#
# With a third argument, the client quick-plays into run/saves/<world> (see world-smoke.sh) and
# success means the player joined and the client kept rendering that world for SETTLE seconds.
#
# Usage: tools/client-boot-test.sh [timeout-seconds] [settle-seconds] [world]   (under xvfb-run)
set -uo pipefail

TIMEOUT="${1:-900}"
SETTLE="${2:-45}"
WORLD="${3:-}"
LOG="client-boot-test.log"
RUN_DIR="run"
LOADED='Created: [0-9]+x[0-9]+x[0-9]+ minecraft:(textures/atlas/)?blocks'
[ -n "$WORLD" ] && LOADED='joined the game'
MIXIN='FAILED during APPLY|was not applied|Found a remappable @Shadow|Critical injection failure|InvalidInjectionException|InjectionError|Mixin apply for mod .* failed'
BAKE='Missing textures in model|Unable to resolve texture|Exception loading blockstate|Failed to load (block )?model|Unable to load model|Failed to (load|parse) .*(model|blockstate)|missing model for variant|No model (loaded )?for|Couldn.t (load|parse) .*model|Missing model'

mkdir -p "$RUN_DIR"
# First launch would otherwise open the accessibility onboarding and the narrator; neither matters
# for a load test, both cost time under software rendering.
cat > "$RUN_DIR/options.txt" <<'OPTS'
onboardAccessibility:false
narrator:0
soundCategory_master:0.0
renderDistance:3
simulationDistance:5
maxFps:30
OPTS

echo "::group::Launching the client (timeout ${TIMEOUT}s)"
QUICKPLAY=()
[ -n "$WORLD" ] && QUICKPLAY=("-PquickPlayWorld=$WORLD")
./gradlew runClient "${QUICKPLAY[@]}" --console=plain --stacktrace > "$LOG" 2>&1 &
GRADLE_PID=$!

loaded=0
for _ in $(seq 1 "$TIMEOUT"); do
    if grep -qE "$LOADED" "$LOG" 2>/dev/null; then
        loaded=1
        break
    fi
    kill -0 "$GRADLE_PID" 2>/dev/null || break
    # A loading error does not exit: NeoForge opens its error screen and waits for a click. A crash
    # report on disk means the verdict is in, so stop instead of waiting out the timeout.
    compgen -G "$RUN_DIR/crash-reports/*.txt" > /dev/null && break
    sleep 1
done

survived=0
if [ "$loaded" -eq 1 ]; then
    # Let the post-reload work (title screen, late class loading) happen before judging.
    survived=1
    for _ in $(seq 1 "$SETTLE"); do
        if ! kill -0 "$GRADLE_PID" 2>/dev/null; then survived=0; break; fi
        sleep 1
    done
fi

if kill -0 "$GRADLE_PID" 2>/dev/null; then
    kill -INT "$GRADLE_PID" 2>/dev/null || true
    for _ in $(seq 1 30); do kill -0 "$GRADLE_PID" 2>/dev/null || break; sleep 1; done
    kill -9 "$GRADLE_PID" 2>/dev/null || true
fi
echo "::endgroup::"

GAME_LOG="$RUN_DIR/logs/latest.log"
[ -f "$GAME_LOG" ] || GAME_LOG="$LOG"
shopt -s nullglob
reports=("$RUN_DIR"/crash-reports/*.txt)

count() { grep -cE "$1" "$2" 2>/dev/null || true; }
mixin_failures=$(count "$MIXIN" "$GAME_LOG"); : "${mixin_failures:=0}"
bake_errors=$(count "$BAKE" "$GAME_LOG"); : "${bake_errors:=0}"

status=0
{
    if [ -n "$WORLD" ]; then echo "## Client in world \`$WORLD\` (headless, software GL)"; else echo "## Client boot (headless, software GL)"; fi
    echo
    if [ "$loaded" -eq 1 ] && [ "$survived" -eq 1 ]; then
        if [ -n "$WORLD" ]; then echo ":white_check_mark: Joined the world and kept rendering it for ${SETTLE}s."; else echo ":white_check_mark: First resource reload completed and the client stayed up ${SETTLE}s after it."; fi
    elif [ "$loaded" -eq 1 ]; then
        echo ":x: Resources loaded, then the client died within ${SETTLE}s."
        status=1
    else
        echo ":x: Never reached \`$LOADED\` within ${TIMEOUT}s."
        status=1
    fi
    if [ ${#reports[@]} -gt 0 ]; then
        echo ":x: ${#reports[@]} crash report(s) written."
        status=1
    fi
    echo
    if [ "$mixin_failures" -gt 0 ]; then
        echo ":x: **${mixin_failures} mixin failure line(s)**:"
        echo '```'; grep -E "$MIXIN" "$GAME_LOG" | head -30; echo '```'
        status=1
    else
        echo ":white_check_mark: No mixin failures in the log."
    fi
    echo
    if [ "$bake_errors" -gt 0 ]; then
        echo ":x: **${bake_errors} model/texture error line(s)** (bake gate) -- create: lines first:"
        echo '```'
        { grep -E "$BAKE" "$GAME_LOG" | grep -i "create:"; grep -E "$BAKE" "$GAME_LOG" | grep -vi "create:"; } | head -60
        echo '```'
        status=1
    else
        echo ":white_check_mark: No model/texture errors in the log."
    fi
} | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"

# Keep Create's own frames: they are where a crash explains itself.
strip_frames() { grep -vE "^[[:space:]]+at (java\.|jdk\.|net\.neoforged\.|cpw\.|org\.spongepowered\.|com\.mojang\.|org\.gradle\.)|^[[:space:]]*\.\.\. [0-9]+ more" "$1"; }
echo "::group::Why Gradle failed (if it did)"
sed -n '/^FAILURE:/,/^\* Get more help/p' "$LOG" | head -40
echo "::endgroup::"
echo "::group::Game log, ERROR/WARN lines about create or mixins (first 150)"
grep -E "/(ERROR|FATAL)\]|WARN\].*([Cc]reate|[Mm]ixin|model|texture)" "$GAME_LOG" | head -150
echo "::endgroup::"
echo "::group::Game log, stack frames filtered (last 120 lines)"
strip_frames "$GAME_LOG" | tail -120
echo "::endgroup::"
for report in "${reports[@]}"; do
    echo "::group::Crash report $(basename "$report")"
    head -60 "$report"
    echo "--- Create frames and causes in this report ---"
    grep -E "Caused by|^[[:space:]]+at com\.zurrtum" "$report" | head -40
    echo "::endgroup::"
done

exit "$status"
