# Regression matrix 12.8

1. versionName = 12.8, versionCode = 71.
2. `code.moviead55.ru/player_js` не блокируется.
3. `player.moviead55.ru/...mstream2inline.js` не блокируется.
4. Stravers iframe/resources не блокируются.
5. Неизвестные `.mp4`, `.m3u8`, `.mpd`, HLS/DASH/media CDN не блокируются.
6. `code.moviead55.ru/vast2` получает валидный пустой VAST 200 вместо сетевого обрыва.
7. Downstream VAST descriptors из диагностики нейтрализуются тем же способом.
8. JS/CSS/VPAID bootstrap не режется blanket-правилом.
9. Баннеры не блокируются автоматически; DOM picker остаётся ручным.
10. «Удалить рекламу» не может выбрать `<video>`, `<audio>`, iframe плеера или контейнер с ними.
11. «Отменить удаление» восстанавливает последнее ручное правило.
12. «Это видеореклама» ничего не блокирует и пишет диагностический контекст.
13. Service Worker перехват использует только explicit VAST descriptor neutralizer.
14. WebView player intercept использует только explicit VAST descriptor neutralizer.
15. Старый Adblock Plus/VPN/localhost proxy/WorkManager/MediaSession не возвращены.
16. Native Chromium fullscreen, system cursor, air-mouse и Button Mapper logic не изменены.
17. Shell scripts проходят `bash -n`.
18. Полный Android Gradle build выполняется publish/build script на Mac с Android SDK 35 и Java 17.
