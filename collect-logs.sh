#!/bin/bash
set -u
echo "**************************************************"
echo "**************************************************"
echo "**************************************************"
ADB="$HOME/.android-build/android-sdk/platform-tools/adb"
DEVICE="${1:-192.168.0.40:5555}"
TS="$(date +%Y%m%d-%H%M%S)"
OUT="$HOME/Downloads/kinotv-diagnostics-$TS"
ZIP="$HOME/Downloads/kinotv-diagnostics-$TS.zip"
mkdir -p "$OUT"
"$ADB" connect "$DEVICE" || exit 1
STATE="$("$ADB" -s "$DEVICE" get-state 2>/dev/null || true)"
if [ "$STATE" != "device" ]; then
  echo "ADB не подключён"
  "$ADB" devices -l
  exit 1
fi
FINISHED=0
LOGPID=""
finish_log() {
  if [ "$FINISHED" = "1" ]; then return; fi
  FINISHED=1
  echo
  echo "Останавливаю логирование..."
  if [ -n "$LOGPID" ]; then
    kill "$LOGPID" >/dev/null 2>&1 || true
    wait "$LOGPID" >/dev/null 2>&1 || true
  fi
  "$ADB" -s "$DEVICE" exec-out screencap -p > "$OUT/screen.png" 2>/dev/null || true
  grep -Ei 'Kinobox|SidiPlayerChoice|PLAYER_CHOICE|PLAYER_PROVIDER|Stravers|moviead55|VAST|VPAID|VMAP|PLAYER_|VIDEO_AD|AD_|MAIN_MEDIA|chromium|WebView|Console|video_init_error|Failed to fetch|m3u8|mp4|mpd|CORS|Access-Control|iframe|viewport|MANUAL_AD|PLAYER_LAYOUT|PLAYER_IFRAME|onReceivedError|onReceivedHttpError|Choreographer|Skipped [0-9]+ frames|Davey|NativeAlloc|concurrent copying GC|ThermalService|render process gone|FATAL EXCEPTION|ANR|MEDIA_PLAY_PAUSE|POPUP_WINDOW_BLOCKED|DESKTOP_IDENTITY|FRAME_PICKER|PICKER_CANDIDATE|PICKER_REJECTED|PICKER_DEFER|PICKER_INSTALL' "$OUT/logcat-full.txt" > "$OUT/logcat-player-filtered.txt" 2>/dev/null || true
  "$ADB" -s "$DEVICE" shell dumpsys meminfo com.kinotv.app > "$OUT/meminfo.txt" 2>&1 || true
  "$ADB" -s "$DEVICE" shell dumpsys thermalservice > "$OUT/thermal.txt" 2>&1 || true
  "$ADB" -s "$DEVICE" shell 'for z in /sys/class/thermal/thermal_zone*; do echo -n "$z "; cat "$z/type" 2>/dev/null; cat "$z/temp" 2>/dev/null; done' > "$OUT/thermal-zones.txt" 2>&1 || true
  {
    echo "DEVICE=$DEVICE"
    echo "DATE=$(date)"
    "$ADB" -s "$DEVICE" shell dumpsys package com.kinotv.app 2>/dev/null | grep -E 'versionName=|versionCode=' || true
  } > "$OUT/info.txt"
  cd "$HOME/Downloads" || exit 1
  rm -f "$ZIP"
  zip -qr "$(basename "$ZIP")" "$(basename "$OUT")"
  echo
  echo "**************************************************"
  echo "ГОТОВО"
  echo "**************************************************"
  echo "$ZIP"
}
trap 'finish_log; exit 0' INT TERM
"$ADB" -s "$DEVICE" logcat -c >/dev/null 2>&1 || true
echo "Запускаю запись..."
"$ADB" -s "$DEVICE" logcat -v threadtime > "$OUT/logcat-full.txt" 2>&1 &
LOGPID=$!
sleep 1
if ! kill -0 "$LOGPID" 2>/dev/null; then
  echo "logcat не запустился"
  cat "$OUT/logcat-full.txt"
  exit 1
fi
echo
echo "**************************************************"
echo "ЛОГИРОВАНИЕ НАЧАЛОСЬ"
echo "**************************************************"
echo
echo "Тестируй приложение. Когда закончишь — нажми ENTER."
read -r </dev/tty
finish_log
