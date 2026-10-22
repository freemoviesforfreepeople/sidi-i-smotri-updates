# Regression matrix 13.1

1. Build: versionName 13.1 / versionCode 74.
2. Player WebView uses desktop UA, wide viewport, overview mode and automatic initial scale.
3. Player page injects viewport width=1366 and logs PLAYER_DESKTOP_LAYOUT_131.
4. Largest non-ad media frame is expanded and page auto-scrolls to it once.
5. Known ad frames are excluded from player sizing; known player iframes remain protected from manual removal.
6. Source selector area must remain visible with the expanded wrapper.
7. Manual picker uses a top-level capture overlay; clicks must not navigate the banner.
8. Cross-origin ad iframe can be selected by its outer iframe node; Stravers/Alloha/Kodik/etc. cannot be removed.
9. Confirmed manual removal hides the exact tokenized element immediately.
10. Saved rules re-apply after navigation/reload and undo still restores by rule id.
11. Old manual rules reset via schema 131.
12. VAST no-fill/CORS, main media allow, fullscreen, input and tab lifecycle remain unchanged.
