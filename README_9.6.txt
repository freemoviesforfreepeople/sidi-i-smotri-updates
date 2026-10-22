СБОРКА

cd ~/Downloads/Sidi-i-smotri-9.6-internal-player
./build.sh

APK:
app/build/outputs/apk/debug/app-debug.apk

УСТАНОВКА ПО ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

Эта версия не запускает внешние браузеры. Плеер остаётся внутри приложения.
