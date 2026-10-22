# 11.7.16 regression matrix

This file documents hardware-proven constraints for the Droidlogic Android 11 / WebView 83 target.

## Must remain true
- Do not accept Android WebChromeClient native custom-view fullscreen as a separate player container.
- Do not create a PopupWindow/player overlay/cursor overlay above the WebView.
- Do not use PointerIcon.TYPE_NULL or CSS cursor:none from app code.
- Do not call requestFocus()/requestFocusFromTouch() from hover/mouse-move handlers.
- Do not register SidiMediaSession: on this firmware one MEDIA_PLAY_PAUSE ACTION_UP was repeatedly redelivered by MediaSessionService.
- Do not promote the Stravers iframe to a standalone main-frame page.
- Do not skip iframe isolation merely because a playback network resource has already been seen.
- Do not leave the app loading shield indefinitely; playback-resource detection and timeout must both be able to reveal the WebView.
- Do not use CSS :has() in app-injected code targeting WebView 83.

## Fullscreen contract
1. Provider requests fullscreen via onShowCustomView.
2. Keep the original WebView as the input context.
3. Re-find the largest/known player iframe or video and force it to viewport geometry.
4. Hide app header, tab strip and movie toolbar.
5. Reject the provider native custom view after the embedded layout has been applied.
6. Back restores app chrome; no synthetic center-tap fullscreen exit.

## Pointer contract
- Real mouse hover remains real mouse hover.
- Android system arrow is forced by WebView.onResolvePointerIcon() for SOURCE_MOUSE.
- No second drawn cursor.
- Air-mouse OK conversion uses the last real pointer coordinates and a mouse click event.
