# Regression matrix 12.7

## Главный принцип

12.7 — диагностическая manual-only версия. До появления подтверждённых правил приложение не должно автоматически блокировать рекламу, media, bootstrap JS, redirects или DOM.

## Проверить

1. versionName = 12.7, versionCode = 70.
2. Service Worker request interception только наблюдает и всегда возвращает null.
3. Search WebView request interception только наблюдает и вызывает super.
4. Player WebView request interception только логирует и никогда не возвращает synthetic/empty response.
5. Нет вызовов automatic `cleanPlayerPage()`.
6. Нет automatic network denylist/allowlist blocking path.
7. Stravers/Kinobox/moviead55 bootstrap загружаются без вмешательства приложения.
8. MP4/M3U8/MPD и неизвестный video CDN загружаются без вмешательства приложения.
9. «Убрать рекламу» скрывает только выбранный DOM элемент.
10. `html/body/video/audio/iframe` и элементы, содержащие video/audio/iframe, нельзя сохранить как manual rule.
11. Manual rule сохраняется и применяется при следующем открытии того же host.
12. «Отменить последнее удаление» удаляет последнее правило и возвращает скрытый элемент.
13. Первый запуск 12.7 очищает manual rules прошлой схемы, чтобы тест начался с чистого состояния.
14. «Это видеореклама» не изменяет страницу и не блокирует request.
15. Нажатие «Это видеореклама» пишет `VIDEO_AD_MARK_BEGIN`, `VIDEO_AD_CONTEXT_BEGIN`, `VIDEO_AD_REQUEST`, `VIDEO_AD_MARK_DOM`, затем повторный context dump и `VIDEO_AD_MARK_END`.
16. Запросы в контексте содержат ageMs, candidate, mainFrame, method, url, Referer и Origin, когда они доступны WebView API.
17. Fullscreen остаётся через WebChromeClient.onShowCustomView().
18. Нет synthetic touch и synthetic media KeyEvent.
19. Button Mapper duplicate source=0x0 по-прежнему подавляется после реального media event.
20. Tab lifecycle: inactive WebView onPause(), background pauseTimers().
