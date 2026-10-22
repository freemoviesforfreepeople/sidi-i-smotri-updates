# Regression matrix 12.5

1. Film/series main frame may follow SSPoisk/Kinobox mirrors when same content id is preserved.
2. Known ad hosts and explicit VAST/VPAID/VMAP/preroll endpoints are blocked before any media whitelist.
3. Media from an ad host returns 204; XML/VAST returns an empty VAST; ad JS returns an empty script.
4. Unknown media/CDN without an ad signature remains allowed to protect the actual film stream.
5. DOM cleaner may remove banner/promo/Telegram/floating widgets but must never remove video or trusted player iframe.
6. Loading shield is non-clickable/non-focusable and is removed on commit, finish, or deadline.
7. Play/Pause repeat and delayed Button Mapper source=0 duplicates remain suppressed.
8. No synthetic KeyEvent, synthetic MotionEvent, player requestFocus, MediaSession, Accessibility polling.
9. Native onShowCustomView fullscreen + system TYPE_ARROW cursor remain enabled.
