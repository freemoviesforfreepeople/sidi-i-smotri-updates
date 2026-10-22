# Regression matrix 13.2

1. versionName 13.2 / versionCode 75.
2. Desktop viewport remains width=1366.
3. Player iframe target height is clamped to 330–440 CSS px; it must not consume the whole WebView height.
4. Player bottom controls and Kinobox source selector must remain reachable/visible without clipping from forced full-height iframe.
5. Auto-scroll runs only when player is substantially below the visible viewport.
6. Manual picker overlay consumes the click and must not navigate the banner.
7. Clicking a close button or child ad iframe selects its containing floating/banner wrapper when safe.
8. Known player iframe/container cannot be removed manually.
9. Saved rule hides the selected wrapper immediately and persists after reload.
10. VAST no-fill/CORS behavior from 12.9 remains unchanged.
