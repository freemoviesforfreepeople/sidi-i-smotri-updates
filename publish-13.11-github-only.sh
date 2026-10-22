#!/bin/bash
set -euo pipefail
echo "**************************************************"
echo "**************************************************"
echo "**************************************************"
VERSION="13.11"
VERSION_CODE="84"
TAG="v13.11"
REPO="freemoviesforfreepeople/sidi-i-smotri-updates"
EXPECTED_LOGIN="freemoviesforfreepeople"
EXPECTED_SIGNER="e79f40929fe66e2136424cd32d36c4266c6e890ecd81077258a2c8c66ffa3916"
ZIP="${1:-}"
if [ -z "$ZIP" ]; then
  for CANDIDATE in "$PWD/Sidi-i-smotri-13.11-source.zip" "$HOME/Downloads/Sidi-i-smotri-13.11-source.zip"; do
    if [ -f "$CANDIDATE" ]; then ZIP="$CANDIDATE"; break; fi
  done
fi
[ -n "$ZIP" ] && [ -f "$ZIP" ] || { echo "Не найден Sidi-i-smotri-13.11-source.zip"; exit 1; }
ZIP="$(cd "$(dirname "$ZIP")" && pwd)/$(basename "$ZIP")"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/sidi-13.11.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
unzip -q "$ZIP" -d "$WORK/src"
SETTINGS="$(find "$WORK/src" -maxdepth 3 -type f -name settings.gradle -print -quit)"
[ -n "$SETTINGS" ] || { echo "В ZIP не найден settings.gradle"; exit 1; }
PROJECT_DIR="$(dirname "$SETTINGS")"
cd "$PROJECT_DIR"
bash ./verify-source.sh
JAVA_CANDIDATES=("${JAVA_HOME:-}" "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home" "$HOME/.android-build/jdk-17/Contents/Home")
JAVA17=""
for J in "${JAVA_CANDIDATES[@]}"; do
  if [ -n "$J" ] && [ -x "$J/bin/java" ]; then
    MAJOR="$($J/bin/java -version 2>&1 | awk -F'[\".]' '/version/ {print ($2=="1"?$3:$2); exit}')"
    if [ "$MAJOR" = "17" ]; then JAVA17="$J"; break; fi
  fi
done
if [ -z "$JAVA17" ] && [ -x /usr/libexec/java_home ]; then
  JAVA17="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
fi
[ -n "$JAVA17" ] && [ -x "$JAVA17/bin/java" ] || { echo "Не найдена Java 17"; exit 1; }
export JAVA_HOME="$JAVA17"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/.android-build/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
[ -d "$ANDROID_HOME/platforms/android-35" ] || { echo "Не найден Android SDK android-35 в $ANDROID_HOME"; exit 1; }
[ -f "$HOME/.android/debug.keystore" ] || { echo "Не найден ~/.android/debug.keystore"; exit 1; }
GRADLE_VERSION="8.13"
GRADLE_HOME="$HOME/.android-build/gradle/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$HOME/.android-build/gradle"
  GZIP="$WORK/gradle-$GRADLE_VERSION-bin.zip"
  curl -L --fail --retry 5 --retry-all-errors "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$GZIP"
  unzip -q "$GZIP" -d "$HOME/.android-build/gradle"
fi
"$GRADLE_HOME/bin/gradle" --no-daemon --console=plain :app:assembleDebug
APK_SRC="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
[ -s "$APK_SRC" ] || { echo "APK не найден после сборки"; exit 1; }
APK="$PROJECT_DIR/sidi-i-smotri-$VERSION.apk"
cp "$APK_SRC" "$APK"
BUILD_TOOLS="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1)"
APKSIGNER="$BUILD_TOOLS/apksigner"
[ -x "$APKSIGNER" ] || { echo "Не найден apksigner"; exit 1; }
SIGNER="$($APKSIGNER verify --print-certs "$APK" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1 | tr -d ':' | tr '[:upper:]' '[:lower:]')"
[ -n "$SIGNER" ] || { echo "Не удалось прочитать signer APK"; exit 1; }
[ "$SIGNER" = "$EXPECTED_SIGNER" ] || { echo "ОШИБКА: signer mismatch"; echo "Ожидался: $EXPECTED_SIGNER"; echo "Получен:  $SIGNER"; exit 1; }
SHA256="$(shasum -a 256 "$APK" | awk '{print $1}')"
command -v gh >/dev/null || { echo "Не установлен gh"; exit 1; }
LOGIN="$(gh api user --jq '.login')"
[ "$LOGIN" = "$EXPECTED_LOGIN" ] || { echo "gh авторизован как $LOGIN, нужен $EXPECTED_LOGIN"; exit 1; }
NOTES="13.11: ручное удаление рекламы стало многократным и устойчивым к пересозданию DOM; клики передаются внутрь Stravers frame-picker; выбранный Kinobox-плеер запоминается и восстанавливается для фильма/сериала."
if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  gh release upload "$TAG" "$APK" "$ZIP" --repo "$REPO" --clobber
  gh release edit "$TAG" --repo "$REPO" --title "Сиди и смотри $VERSION" --notes "$NOTES"
else
  gh release create "$TAG" "$APK" "$ZIP" --repo "$REPO" --title "Сиди и смотри $VERSION" --notes "$NOTES"
fi
REPO_DIR="$WORK/repo"
gh repo clone "$REPO" "$REPO_DIR"
cd "$REPO_DIR"
DEFAULT_BRANCH="$(git remote show origin | sed -n '/HEAD branch/s/.*: //p')"
[ -n "$DEFAULT_BRANCH" ] || DEFAULT_BRANCH="main"
cat > update.json <<JSON
{
  "versionCode": $VERSION_CODE,
  "versionName": "$VERSION",
  "apkUrl": "https://github.com/$REPO/releases/download/$TAG/sidi-i-smotri-$VERSION.apk",
  "sha256": "$SHA256",
  "notes": "$NOTES"
}
JSON
python3 -m json.tool update.json >/dev/null
git config user.name "$EXPECTED_LOGIN"
git config user.email "$EXPECTED_LOGIN@users.noreply.github.com"
git add update.json
if ! git diff --cached --quiet; then
  git commit -m "Обновление до версии $VERSION"
  git push origin "$DEFAULT_BRANCH"
fi
gh release view "$TAG" --repo "$REPO" --json tagName,name,url,assets
cat update.json
echo "ГОТОВО: $TAG опубликован. ADB не использовался."
echo "APK SHA-256: $SHA256"
echo "Signer SHA-256: $SIGNER"
