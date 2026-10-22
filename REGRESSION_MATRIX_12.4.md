# 12.4 — правила, которые нельзя откатывать

1. **Плеер**
   - `loadUrl()` стартует только после создания/добавления shield и spinner.
   - loading shield — визуальный, `clickable=false`, `focusable=false`.
   - `onPageCommitVisible()` снимает shield/spinner.
   - не возвращать бесконечный readiness overlay.

2. **Adblock**
   - не возвращать AdblockAndroid, AdGuard/EasyPrivacy runtime subscriptions или WorkManager.
   - player subresources: блокировать только явно известную рекламу.
   - trusted player/media и неизвестные CDN не блокировать.
   - не использовать системный proxy/VPN.

3. **Input**
   - никаких synthetic `KeyEvent`, synthetic touch/mouse и `requestFocus()` для Play/Pause.
   - `repeatCount > 0` подавлять.
   - delayed Button Mapper `source=0` подавлять минимум 1.2 с после реального source!=0 события.

4. **Fullscreen**
   - только Chromium `onShowCustomView`.
   - системный `PointerIcon.TYPE_ARROW` на container/custom view/динамических children.
   - не перехватывать hover/click для показа курсора.

5. **Tabs/lifecycle**
   - неактивный player WebView -> `onPause()`.
   - активный -> `onResume()`.
   - при переходе в Поиск активный player также паузится.

6. **WebView**
   - современный Google/Android System WebView 120+ обязателен.
   - iframe Stravers остаётся cross-origin и не промоутится в top-level.
