#!/bin/bash
set -euo pipefail
echo "**************************************************"
echo "**************************************************"
echo "**************************************************"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
VERSION="13.21"
ADB="${ANDROID_HOME:-$HOME/.android-build/android-sdk}/platform-tools/adb"
APK="${PROJECT_DIR}/sidi-i-smotri-${VERSION}.apk"

if [ $# -ne 1 ]; then
  echo "Использование: ./install-to-tv.sh IP_ПРИСТАВКИ"
  exit 1
fi
[ -f "${APK}" ] || { echo "Сначала собери APK командой ./build.sh"; exit 1; }
[ -x "${ADB}" ] || { echo "ADB не найден: ${ADB}"; exit 1; }

"${ADB}" connect "$1:5555" || true
"${ADB}" -s "$1:5555" wait-for-device
"${ADB}" -s "$1:5555" install -r "${APK}"
"${ADB}" -s "$1:5555" shell am force-stop com.kinotv.app
"${ADB}" -s "$1:5555" shell am start -S -W -n com.kinotv.app/.MainActivity

echo "Установлено и запущено: ${VERSION}"
