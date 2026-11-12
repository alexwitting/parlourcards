#!/usr/bin/env bash
# The on-device upgrade check (docs/TEST_PLAN.md §B): install the previous release, play into a game,
# then install this build over it and check nothing was lost. Keep each release's APK (e.g. as a
# GitHub release asset) so there's always a previous one to start from.
#
#   tools/upgrade_check.sh <previous-release.apk>
#
# The release app ("Parlour Cards", com.cardgames) is uninstalled first — an older build can't be
# installed over a newer one — so its saves, stats and settings on this phone are lost.
set -euo pipefail

PACKAGE=com.cardgames
OLD_APK=${1:?usage: tools/upgrade_check.sh <previous-release.apk>}
ROOT=$(cd "$(dirname "$0")/.." && pwd)

[ -f "$OLD_APK" ] || { echo "No such APK: $OLD_APK"; exit 1; }
adb get-state >/dev/null 2>&1 || { echo "No phone connected (adb devices)."; exit 1; }

echo "This uninstalls Parlour Cards ($PACKAGE) from the phone, losing its saves, stats and settings."
read -r -p "Continue? [y/N] " answer
[ "$answer" = "y" ] || exit 1

adb uninstall "$PACKAGE" >/dev/null 2>&1 || true
adb install "$OLD_APK"
adb shell am start -n "$PACKAGE/com.cardgames.MainActivity" >/dev/null

cat <<'STEPS'

On the phone, with the previous release:
  - play a few moves of Solitaire and leave it mid-game (note the time and moves)
  - start a Gin match and leave it mid-hand (note the hand, scores and opponent)
  - finish or abandon a game of each, so the stats have results
  - change a few settings (theme, card speed, a Gin option)
STEPS
read -r -p "Press Enter when that's done… "

echo "Building and installing this version over it…"
(cd "$ROOT" && ./gradlew --quiet installRelease)
adb shell am start -n "$PACKAGE/com.cardgames.MainActivity" >/dev/null

cat <<'STEPS'

Now check, on the new version:
  - the Solitaire game resumes at the same position, time and moves, and Undo still works
  - the Gin match resumes: same hand, scores, opponent and action message
  - Stats show the same totals, streaks and best times
  - every setting you changed is still set
STEPS
