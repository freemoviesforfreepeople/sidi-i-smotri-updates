#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
MAIN="$ROOT/app/src/main/java/com/kinotv/app/MainActivity.java"
MOVIE="$ROOT/app/src/main/java/com/kinotv/app/MovieRecord.java"
GRADLE="$ROOT/app/build.gradle"
fail(){ echo "SMOKE FAIL: $*" >&2; exit 1; }

grep -q 'versionCode 84' "$GRADLE" || fail 'versionCode'
grep -q "versionName '13.11'" "$GRADLE" || fail 'versionName'
grep -q 'function visualAt(x,y,raw)' "$MAIN" || fail 'visual hit-test missing'
grep -q "pointerEvents==='none'" "$MAIN" || fail 'pointer-events:none handling missing'
grep -q 'PICKER_CANDIDATE' "$MAIN" || fail 'picker candidate diagnostics missing'
grep -q 'PlayerChoiceBridge' "$MAIN" || fail 'player choice persistence missing'
grep -q 'PLAYER_CHOICE_RESTORE' "$MAIN" || fail 'player choice restore missing'
grep -q 'playerIndex' "$MOVIE" || fail 'playerIndex storage missing'
grep -q 'playerLabel' "$MOVIE" || fail 'playerLabel storage missing'
python3 "$ROOT/smoke_visual_picker_13_11.py" "$MAIN"
bash -n "$ROOT/build.sh"
bash -n "$ROOT/install-to-tv.sh"
bash -n "$ROOT/collect-logs.sh"
bash -n "$ROOT/publish-13.11-github-only.sh"
echo 'SMOKE OK: pointer-events:none overlay selected above protected video; player choice persistence present'
