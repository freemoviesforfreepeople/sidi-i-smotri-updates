#!/bin/bash
set -euo pipefail
echo "**************************************************"
echo "**************************************************"
echo "**************************************************"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
VERSION="13.21"
SDK_DEFAULT="$HOME/.android-build/android-sdk"
GRADLE_VERSION="8.13"
TOOLS_DIR="$HOME/.android-build/gradle"
GRADLE_DIR="$TOOLS_DIR/gradle-$GRADLE_VERSION"
GRADLE_ZIP="$TOOLS_DIR/gradle-$GRADLE_VERSION-bin.zip"

java_major() {
  "$1/bin/java" -version 2>&1 | awk -F'[".]' '/version/ {print ($2=="1"?$3:$2); exit}'
}

find_java17() {
  local candidate=""
  local candidates=()
  [ -n "${JAVA_HOME:-}" ] && candidates+=("${JAVA_HOME}")
  candidates+=(
    "$HOME/.android-build/jdk-17/Contents/Home"
    "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
    "/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home"
    "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
  )
  if [ -x /usr/libexec/java_home ]; then
    candidate="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    [ -n "${candidate}" ] && candidates+=("${candidate}")
  fi
  for candidate in "${candidates[@]}"; do
    if [ -x "${candidate}/bin/java" ] && [ "$(java_major "${candidate}")" = "17" ]; then
      printf '%s\n' "${candidate}"
      return 0
    fi
  done
  return 1
}

JAVA17_HOME="$(find_java17 || true)"
if [ -z "${JAVA17_HOME}" ]; then
  echo "Не найдена Java 17. На macOS установи:"
  echo "  brew install --cask temurin@17"
  exit 1
fi

export JAVA_HOME="${JAVA17_HOME}"
export ANDROID_HOME="${ANDROID_HOME:-$SDK_DEFAULT}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "Java: ${JAVA_HOME}"
java -version

if [ ! -d "${ANDROID_HOME}/platforms/android-35" ]; then
  echo "Не найден Android SDK 35: ${ANDROID_HOME}/platforms/android-35"
  exit 1
fi

"${PROJECT_DIR}/verify-source.sh"

mkdir -p "${TOOLS_DIR}"
if [ ! -x "${GRADLE_DIR}/bin/gradle" ]; then
  echo "Скачиваю Gradle ${GRADLE_VERSION} (один раз)…"
  rm -f "${GRADLE_ZIP}"
  curl -L --fail --retry 5 --retry-all-errors \
    "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
    -o "${GRADLE_ZIP}"
  unzip -q "${GRADLE_ZIP}" -d "${TOOLS_DIR}"
  rm -f "${GRADLE_ZIP}"
fi

cd "${PROJECT_DIR}"
echo "Собираю APK ${VERSION}…"
"${GRADLE_DIR}/bin/gradle" --no-daemon --console=plain :app:assembleDebug

APK="${PROJECT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
[ -f "${APK}" ] || { echo "Сборка завершилась, но APK не найден."; exit 1; }

FINAL_APK="${PROJECT_DIR}/sidi-i-smotri-${VERSION}.apk"
cp "${APK}" "${FINAL_APK}"

echo
echo "ГОТОВО: ${FINAL_APK}"
echo "SHA-256:"
shasum -a 256 "${FINAL_APK}"

echo "Размер APK:"
du -h "${FINAL_APK}"

BUILD_TOOLS="$(find "${ANDROID_HOME}/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1)"
if [ -x "${BUILD_TOOLS}/apksigner" ]; then
  echo "Подпись APK:"
  "${BUILD_TOOLS}/apksigner" verify --print-certs "${FINAL_APK}" | sed -n '1,8p'
fi
