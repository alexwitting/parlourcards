#!/usr/bin/env bash
# Crash hunt (docs/TEST_PLAN.md §K): random taps, back presses and relaunches at the release build,
# installed as its own app (com.cardgames.benchmark) and uninstalled afterwards — the real Parlour
# Cards and Parlour Cards Dev, and their saves, are never touched.
#
#   tools/monkey_run.sh [seed ...]        (default seeds: 1 2 3; 10,000 events each, ~5 minutes per seed)
#
# Keep the phone unlocked and leave it alone while it runs. No swipes (one from the top edge would open
# the notification shade, and the taps after it could change system settings), and no rotation, volume
# or call keys — so card drags aren't exercised; taps play the same moves.
set -euo pipefail

PACKAGE=com.cardgames.benchmark
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SEEDS=("${@:-1 2 3}")
OUT=$(mktemp -d)

adb get-state >/dev/null 2>&1 || { echo "No phone connected (adb devices)."; exit 1; }

echo "Building the release app as $PACKAGE…"
(cd "$ROOT" && ./gradlew --quiet :app:assembleBenchmarkRelease)
META="$ROOT/app/build/outputs/apk/benchmarkRelease/output-metadata.json"
grep -q "\"applicationId\": \"$PACKAGE\"" "$META" || { echo "The benchmark build isn't $PACKAGE — stopping."; exit 1; }
adb install -r "$ROOT"/app/build/outputs/apk/benchmarkRelease/*.apk >/dev/null

START=$(adb shell 'date "+%m-%d %H:%M:%S.000"' | tr -d '\r')
failed=0
for seed in ${SEEDS[@]}; do
    adb shell input keyevent KEYCODE_WAKEUP
    adb shell monkey -p "$PACKAGE" -s "$seed" --throttle 60 \
        --pct-touch 90 --pct-majornav 8 --pct-appswitch 2 \
        --pct-motion 0 --pct-trackball 0 --pct-nav 0 --pct-syskeys 0 --pct-flip 0 \
        --pct-anyevent 0 --pct-pinchzoom 0 --pct-rotation 0 --pct-permission 0 \
        -v 10000 > "$OUT/seed$seed.txt" 2>&1 || true
    if grep -qE "CRASH|NOT RESPONDING|aborted" "$OUT/seed$seed.txt"; then
        echo "Seed $seed: FAILED — see $OUT/seed$seed.txt (replay with: tools/monkey_run.sh $seed)"
        failed=1
    else
        echo "Seed $seed: $(grep -m1 'Events injected' "$OUT/seed$seed.txt"), no crash"
    fi
done

adb logcat -d -b crash -T "$START" > "$OUT/crash.txt" 2>/dev/null || true
if grep -q "$PACKAGE" "$OUT/crash.txt"; then
    echo "Crash log entries for $PACKAGE: $OUT/crash.txt"
    failed=1
fi

adb uninstall "$PACKAGE" >/dev/null
echo "Logs in $OUT"
exit $failed
