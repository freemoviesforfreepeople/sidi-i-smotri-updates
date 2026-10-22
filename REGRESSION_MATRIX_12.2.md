# 12.2 — правила, которые нельзя откатывать

1. WebView: рабочий минимум 120+, на целевой приставке — Google Android System WebView 151.
2. Fullscreen: только WebChromeClient.onShowCustomView / Chromium CustomView. Никакого CSS/iframe fullscreen.
3. Mouse: никакого requestFocus/requestFocusFromTouch/synthetic tap. Системная стрелка фиксируется на WebView и fullscreen container.
4. Play/Pause: repeatCount > 0 поглощается; второй цикл Button Mapper в окне 650 мс поглощается. MediaSession не используется.
5. Adblock: никакого системного VPN/proxy. В player path блокируются только известные внешние ad-subresources; trusted player/media/SRI не переписываются.
6. Main-frame player navigation: разрешены known player wrappers (sspoisk, bulkikim, stravers и др.), остальные автоматические redirect блокируются.
7. PlayerControlsBridge не опрашивается постоянным foreground ticker.
8. Верхние вкладки обязаны сохранять явные ‹ › индикаторы overflow.
