# 12.0 — правила player-layer

Эти решения нельзя возвращать без нового диагностического доказательства:

1. Не отклонять `onShowCustomView()` немедленным `callback.onCustomViewHidden()`.
2. Не эмулировать fullscreen через JS, resize iframe/video или скрытие соседей DOM.
3. Не размещать поверх Chromium custom view собственные controls/cursor/hover overlays.
4. Не вызывать `requestFocus()`/`requestFocusFromTouch()` из mouse/hover/fullscreen callbacks.
5. Не переопределять `onResolvePointerIcon()` и не использовать `PointerIcon.TYPE_NULL`.
6. Не конвертировать DPAD_CENTER/OK в synthetic touch/mouse без отдельного доказанного устройства, которому это нужно.
7. Не регистрировать MediaSession для Play/Pause на Droidlogic: один ACTION_UP раньше циклически переотправлялся системой.
8. Не устанавливать в player document постоянные MutationObserver/setInterval мосты.
9. Не переписывать SRI JS/HTML Stravers и не подменять playback network responses.
10. Не возвращать автоматический iframe promotion/isolation на legacy WebView.

Допустимые точечные compatibility-меры:
- узкий polyfill конкретного Kinobox `:has()` на Chromium 83;
- диагностика, которая только наблюдает и возвращает `super`;
- network error UI и loading deadline, не изменяющие документ плеера.
