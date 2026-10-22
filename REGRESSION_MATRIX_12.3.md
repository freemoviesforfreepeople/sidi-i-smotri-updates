# 12.3 — правила, которые нельзя откатывать

1. WebView: рабочий минимум 120+, целевая приставка использует Google Android System WebView 151.
2. Fullscreen: только WebChromeClient.onShowCustomView / Chromium CustomView. Никакого CSS/iframe fullscreen.
3. Mouse: никакого requestFocus/requestFocusFromTouch/synthetic tap. Системная стрелка фиксируется на WebView и fullscreen container.
4. Play/Pause: repeatCount > 0 и второй цикл Button Mapper в debounce-окне поглощаются. MediaSession не используется.
5. Adblock: никакого системного VPN/proxy. Player/media/SRI не переписываются.
6. SSPoisk/Kinobox wrapper может менять TLD. Нельзя whitelist-ить только один bulkikim TLD. Разрешать bulkikim.* и HTTPS same-film redirect с тем же /film/<id>/ или /series/<id>/.
7. Неизвестные main-frame redirect без совпадающего movie id остаются заблокированными.
8. PlayerControlsBridge не опрашивается постоянным foreground ticker.
9. Верхние вкладки сохраняют явные ‹ › индикаторы overflow.
